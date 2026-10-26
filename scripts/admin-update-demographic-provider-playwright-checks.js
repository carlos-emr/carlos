#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Bulk patient-provider reassignment: coverage plan §2.2 admin-misc (Update Patient Provider).
 * User path: Schedule ▸ Administration ▸ Data Management ▸ Update Patient Provider
 * (admin/UpdateDemographicProvider in the #dynamic-content iframe) ▸ MRP / Resident ▸ Update.
 * Asserted: both owned providers are offered; an excluding last-name range moves nobody; F..F moves
 * exactly the three owned patients (provider_no, one demographicArchive copy of the old MRP each,
 * lastUpdateUser) and reports "3 record(s)"; Resident rewrites only the owned extensions and archives
 * the old value; the reverse Update restores the MRP; every demographic/demographicExt row the run
 * does not own is unchanged (run with EXCLUSIVE=1); a GET with the update parameters is refused.
 * Fixtures: two throwaway providers (last name = run marker) and three FAKE- patients whose MRP and
 * resident are the FROM provider; cleanup deletes only those rows and asserts they are gone.
 */
const h = require('./lib/playwright-harness');
const { authzReadFixture } = require('./lib/authz-read-fixture');
const { signIn } = require('./lib/authz-read-probe');
const { sessionToken } = require('./lib/mutation-replay');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow } = require('./lib/workflow-session');
const { randomInt } = require('node:crypto');

const TIMEOUT = 20000;
const RECORDS = /(\d+)\s*record\(s\) have been updated\./;

function pickUnusedProviderNo(sql, taken = []) {
  for (let attempt = 0; attempt < 50; attempt++) {
    const candidate = String(randomInt(700000, 999000));
    const quoted = h.sqlString(candidate);
    if (taken.includes(candidate)) continue;
    const used = sql.value(`SELECT (SELECT COUNT(*) FROM provider WHERE provider_no=${quoted})
      + (SELECT COUNT(*) FROM demographic WHERE provider_no=${quoted})
      + (SELECT COUNT(*) FROM demographicExt WHERE value=${quoted} AND key_val IN ('resident','nurse','midwife'))`);
    if (used === '0') return candidate;
  }
  h.assert(false, 'No unused provider number was found in the fixture range');
  return null;
}

async function workflow(s) {
  const roleFixture = authzReadFixture({ sql: s.sql, marker: s.marker, provider: s.provider, testUser: s.config.testUser });
  s.cleanup(() => roleFixture.cleanup());

  const { sql, context, config, recorder, marker } = s;
  const from = pickUnusedProviderNo(sql);
  const to = pickUnusedProviderNo(sql, [from]);
  const providers = [from, to].map(h.sqlString).join(',');
  const patients = [];
  const ids = () => patients.join(',') || '0';
  s.cleanup(() => {
    if (patients.length) {
      h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no IN (${ids()}) AND last_name<>${h.sqlString(marker)}`) === '0',
        'Patient fixture ownership changed; refusing to clean up');
      sql.execute(['demographicExtArchive', 'demographicExt', 'demographicArchive']
        .map(table => `DELETE FROM ${table} WHERE demographic_no IN (${ids()})`).join(';'));
      sql.execute(`DELETE FROM demographic WHERE demographic_no IN (${ids()}) AND last_name=${h.sqlString(marker)}`);
    }
    sql.execute(`DELETE FROM provider WHERE provider_no IN (${providers}) AND last_name=${h.sqlString(marker)}`);
    const remaining = ['demographicExtArchive', 'demographicExt', 'demographicArchive', 'demographic']
      .map(table => `(SELECT COUNT(*) FROM ${table} WHERE demographic_no IN (${ids()}))`).join('+');
    h.assert(sql.value(`SELECT ${remaining}+(SELECT COUNT(*) FROM provider WHERE provider_no IN (${providers}))`) === '0',
      'Owned patient-provider fixtures were not removed');
  });
  for (const [number, first] of [[from, 'From'], [to, 'To']]) {
    sql.execute(`INSERT INTO provider (provider_no,last_name,first_name,provider_type,specialty,sex,status,lastUpdateUser,lastUpdateDate)
      VALUES (${h.sqlString(number)},${h.sqlString(marker)},${h.sqlString(first)},'doctor','GP','F','1',${h.sqlString(s.provider)},NOW())`);
  }
  for (const first of ['Alpha', 'Bravo', 'Charlie']) {
    const id = sql.value(`INSERT INTO demographic (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
        provider_no,hc_type,province,roster_status,lastUpdateDate)
      VALUES (${h.sqlString(marker)},${h.sqlString(first)},'1980','01','02','F','AC',${h.sqlString(from)},'ON','ON','NR',NOW());
      SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), 'A synthetic patient fixture was not created');
    patients.push(id);
  }
  // Two of the three also carry the FROM provider as their resident.
  for (const id of patients.slice(0, 2)) {
    sql.execute(`INSERT INTO demographicExt (demographic_no,provider_no,key_val,value,date_time,hidden)
      VALUES (${id},${h.sqlString(s.provider)},'resident',${h.sqlString(from)},NOW(),'0')`);
  }
  const mrp = () => sql.rows(`SELECT provider_no FROM demographic WHERE demographic_no IN (${ids()}) ORDER BY demographic_no`).map(r => r[0]);
  const residents = () => sql.rows(`SELECT value FROM demographicExt WHERE demographic_no IN (${ids()}) AND key_val='resident'
    ORDER BY demographic_no`).map(r => r[0]);
  // Every row this run does not own, fingerprinted over EVERY column (QUOTE keeps NULL distinct
  // from text: it yields the string 'NULL', so CONCAT_WS never skips a column), so any change to a
  // non-owned row fails the invariant; NULL demographic_no rows (demographicExt allows them) are
  // included explicitly because NOT IN drops them. EXCLUSIVE=1 keeps other checks from changing
  // them meanwhile.
  const fingerprint = table => {
    const columns = sql.rows(`SELECT column_name FROM information_schema.columns
      WHERE table_schema=DATABASE() AND table_name=${h.sqlString(table)} ORDER BY ordinal_position`).map(r => r[0]);
    h.assert(columns.length && columns.every(column => /^\w+$/.test(column)), `Unexpected ${table} column names`);
    const row = `SHA2(CONCAT_WS(':',${columns.map(column => `QUOTE(\`${column}\`)`).join(',')}),256)`;
    const key = table === 'demographic' ? 'demographic_no' : 'id';
    return `(SELECT SHA2(COALESCE(GROUP_CONCAT(${row} ORDER BY ${key}),''),256) FROM ${table}
      WHERE demographic_no IS NULL OR demographic_no NOT IN (${ids()}))`;
  };
  const others = () => sql.value(`SET SESSION group_concat_max_len=67108864;
    SELECT CONCAT(${fingerprint('demographic')},'|',${fingerprint('demographicExt')})`);
  const othersBefore = others();

  const { page: admin } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    { context, recorder, label: 'update-patient-provider', timeout: TIMEOUT });
  let frame;
  async function openPage() {
    const link = admin.locator('#adminNav a[rel$="/admin/UpdateDemographicProvider"]').first();
    await revealAuditLink(admin, link, TIMEOUT);
    await link.click();
    const iframe = admin.locator('#dynamic-content iframe').first();
    await iframe.waitFor();
    frame = await (await iframe.elementHandle()).contentFrame();
    h.assert(frame, 'The Update Patient Provider iframe did not load');
    await frame.waitForURL(/\/admin\/UpdateDemographicProvider/, { timeout: TIMEOUT, waitUntil: 'load' });
    await frame.locator('select[name="oldcust5"]').waitFor();
  }
  // Each section's controls sit in their own .card (the <form> is foster-parented out of its <table>).
  const section = select => frame.locator('.card').filter({ has: frame.locator(`select[name="${select}"]`) });
  async function reassign(oldName, newName, oldNo, newNo, fromLetter, toLetter) {
    const card = section(oldName);
    await card.locator(`select[name="${oldName}"]`).selectOption(oldNo);
    await card.locator(`select[name="${newName}"]`).selectOption(newNo);
    await card.locator('select[name="last_name_from"]').selectOption(fromLetter);
    await card.locator('select[name="last_name_to"]').selectOption(toLetter);
    const navigated = admin.waitForEvent('framenavigated', { predicate: f => f === frame, timeout: TIMEOUT });
    navigated.catch(() => {});
    await card.locator('input[type="submit"]').click();
    await navigated;
    await frame.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
    const match = RECORDS.exec((await frame.locator('body').innerText()).replace(/\s+/g, ' '));
    h.assert(match, 'The update did not report how many records changed');
    return Number(match[1]);
  }

  await s.step('Update Patient Provider offers both owned providers in the MRP replace and With lists', async () => {
    await openPage();
    for (const select of ['oldcust5', 'newcust5']) {
      const labels = await frame.locator(`select[name="${select}"] option`).evaluateAll((options, wanted) =>
        options.filter(option => wanted.includes(option.value)).map(option => `${option.value}=${option.textContent.trim()}`), [from, to]);
      h.assert(labels.length === 2 && labels.includes(`${from}=${marker}, From`) && labels.includes(`${to}=${marker}, To`),
        `The ${select} list does not offer both owned providers by name`);
    }
  });
  await s.step('a last-name range that excludes the owned patients reassigns none of them', async () => {
    const count = await reassign('oldcust5', 'newcust5', from, to, 'A', 'E');
    h.assert(count === 0, `The A..E range reported ${count} record(s), expected 0`);
    h.assert(mrp().every(no => no === from), 'An owned patient outside the range was reassigned');
  });
  await s.step('the F..F range moves exactly the three owned patients to the new MRP and archives the old one', async () => {
    const count = await reassign('oldcust5', 'newcust5', from, to, 'F', 'F');
    h.assert(count === 3, `The MRP update reported ${count} record(s), expected 3`);
    h.assert(mrp().every(no => no === to), 'Not every owned patient carries the new MRP');
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no IN (${ids()})
      AND lastUpdateUser=${h.sqlString(s.provider)}`) === '3', 'The reassignment did not stamp the acting admin');
    h.assert(sql.value(`SELECT COUNT(*) FROM demographicArchive WHERE demographic_no IN (${ids()}) AND provider_no=${h.sqlString(from)}`) === '3'
      && sql.value(`SELECT COUNT(DISTINCT demographic_no) FROM demographicArchive WHERE demographic_no IN (${ids()})`) === '3',
    'The reassignment did not archive each patient\'s previous MRP');
    h.assert(residents().every(no => no === from), 'The MRP update changed the resident extension');
  });
  await s.step('the Resident section rewrites only the owned resident extensions and archives their previous value', async () => {
    const count = await reassign('oldcust2', 'newcust2', from, to, 'F', 'F');
    h.assert(count === 2, `The resident update reported ${count} record(s), expected 2`);
    h.assert(JSON.stringify(residents()) === JSON.stringify([to, to]), 'The owned resident extensions were not reassigned');
    h.assert(mrp().every(no => no === to), 'The resident update changed the MRP');
    h.assert(sql.value(`SELECT COUNT(*) FROM demographicExtArchive WHERE demographic_no IN (${ids()})
      AND key_val='resident' AND value=${h.sqlString(from)}`) === '2', 'The previous resident value was not archived');
  });
  await s.step('Update in reverse restores the original MRP and no row outside the fixture changed', async () => {
    const count = await reassign('oldcust5', 'newcust5', to, from, 'F', 'F');
    h.assert(count === 3, `The reverse MRP update reported ${count} record(s), expected 3`);
    h.assert(mrp().every(no => no === from), 'The reverse update did not restore the original MRP');
    h.assert(others() === othersBefore, 'A demographic or demographicExt row this run does not own changed');
  });
  await s.step('GET and HEAD carrying MRP update parameters are refused without reassigning anyone', async () => {
    for (const method of ['GET', 'HEAD']) {
      const response = await context.request.fetch(h.appUrl(config.baseUrl, '/admin/UpdateDemographicProvider'), {
        method, params: { update: 'UpdateMrp', oldcust5: from, newcust5: to, last_name_from: 'F', last_name_to: 'F' }, maxRedirects: 0 });
      const reassigned = mrp().filter(no => no === to).length;
      h.assert(response.status() === 405 && reassigned === 0,
        `${method} answered HTTP ${response.status()} and reassigned ${reassigned} owned patient(s); expected 405 and none`);
    }
  });
  await s.step('HEAD mutations and tokenless POSTs are refused before reassignment', async () => {
    const endpoint = h.appUrl(config.baseUrl, '/admin/UpdateDemographicProvider');
    const form = { update: 'UpdateMrp', oldcust5: from, newcust5: to, last_name_from: 'F', last_name_to: 'F' };
    const head = await context.request.head(endpoint, { params: form, maxRedirects: 0 });
    h.assert(head.status() === 405, `HEAD reassignment answered HTTP ${head.status()}`);
    const post = await context.request.post(endpoint, { form, maxRedirects: 0 });
    h.assert(post.status() === 403 && Object.hasOwn(post.headers(), 'x-permitted-cross-domain-policies'),
      'A tokenless reassignment POST was not refused by the application');
    h.assert(mrp().every(no => no === from), 'A refused request changed the owned MRP');
  });
  await s.step('an administrator with read-only misc permission cannot reassign with a valid CSRF token', async () => {
    const role = roleFixture.addRole({ _appointment: 'w', _admin: 'r', '_admin.misc': 'r', _demographic: 'u' });
    const restricted = await signIn(s, roleFixture.addLogin(role));
    const token = await sessionToken(restricted.page, config.baseUrl);
    const response = await restricted.context.request.post(h.appUrl(config.baseUrl, '/admin/UpdateDemographicProvider'), {
      form: { update: 'UpdateMrp', oldcust5: from, newcust5: to, last_name_from: 'F', last_name_to: 'F', 'CSRF-TOKEN': token },
      maxRedirects: 0,
    });
    h.assert(response.status() === 403 && Object.hasOwn(response.headers(), 'x-permitted-cross-domain-policies'),
      'The read-only administrator was not refused by the application');
    h.assert(mrp().every(no => no === from), 'The read-only administrator changed the owned MRP');
    await restricted.context.close();
  });

  await s.step('No Provider clears owned MRP and resident assignments through the existing forms', async () => {
    h.assert(await reassign('oldcust5', 'newcust5', from, '', 'F', 'F') === 3, 'Clearing the MRP did not update three patients');
    h.assert(mrp().every(no => no === ''), 'No Provider did not clear every owned MRP');
    h.assert(await reassign('oldcust2', 'newcust2', to, '', 'F', 'F') === 2, 'Clearing residents did not update two extensions');
    h.assert(residents().every(no => no === ''), 'No Provider did not clear every owned resident');
    h.assert(others() === othersBefore, 'Clearing an assignment changed a non-owned row');
  });

}
if (require.main === module) runWorkflow('admin-update-demographic-provider', workflow, { openPatient: false });
module.exports = { workflow };
