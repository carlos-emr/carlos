#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Provider record administration: coverage plan §2.2 provider-record-admin.
 *
 * User path: Schedule ▸ Administration ▸ User Management ▸ Add a Provider Record
 * (admin/ViewProviderAddARecordHtm in #myFrame ▸ admin/ProviderAddARecord), then
 * Search/Edit Provider Records (admin/ViewProviderSearchRecordsHtm ▸
 * admin/ViewProviderSearchResults ▸ ID link ▸ admin/ViewProviderUpdateProvider ▸
 * Update Record ▸ admin/ProviderUpdate). The provider edit page offers no delete
 * control, so the delete leg has no UI entry and is not checked.
 *
 * Asserted: the add form creates the owned row with the chosen number; adding the
 * same number again is refused ("Provider No already in use") without touching the
 * row; the search lists the owned provider by name and by number; the edit page
 * reflects the row; Update Record writes exactly the edited columns, one archive
 * copy of the pre-edit row and the official-name property; the post-edit listing
 * shows the new values; ProviderUpdate and ProviderAddARecord refuse GET without
 * writing. scripts/add-login-account-playwright-checks.js already covers the add
 * form's role in login creation, so the add is a fixture here, not the subject.
 *
 * Fixtures: one provider row (last name = run marker, number chosen unused) created
 * through the UI, plus the providerbillcenter/providerArchive/property rows the
 * pages write for it. Cleanup deletes only rows carrying the owned number and
 * asserts they are gone.
 */
const h = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates, dataTableRows } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow } = require('./lib/workflow-session');
const { randomInt } = require('node:crypto');

const TIMEOUT = 20000;
const ADD_LINK = 'Add a Provider Record';
const SEARCH_LINK = 'Search/Edit Provider Records';
const CHILD_TABLES = ['providerbillcenter', 'providersite', 'providerArchive', 'property', 'secUserRole', 'security'];

// provider_no is varchar(6); choose a high value no row holds in provider or in any table
// cleanup deletes from by provider number, so pre-existing orphan rows are never swept up.
function pickUnusedProviderNo(sql) {
  for (let attempt = 0; attempt < 50; attempt++) {
    const candidate = String(randomInt(700000, 999000));
    const quoted = h.sqlString(candidate);
    const used = sql.value(`SELECT ${['provider', ...CHILD_TABLES]
      .map(table => `(SELECT COUNT(*) FROM ${table} WHERE provider_no=${quoted})`).join(' + ')}`);
    if (used === '0') return candidate;
  }
  h.assert(false, 'No unused provider number was found in the fixture range');
  return null;
}

// Open a User Management section through the Administration left nav and hand back
// the frame the shell injected for it (.xlink replaces #myFrame on every click).
async function openSection(admin, name, selector) {
  const link = admin.locator('#adminNav').getByRole('link', { name, exact: true, includeHidden: true });
  await revealAuditLink(admin, link, TIMEOUT);
  await link.click();
  const iframe = admin.locator('#dynamic-content iframe').first();
  await iframe.waitFor();
  const frame = await (await iframe.elementHandle()).contentFrame();
  h.assert(frame, `The ${name} iframe did not load`);
  await frame.locator(selector).waitFor();
  return frame;
}

async function navigateFrame(admin, frame, locator) {
  const navigated = admin.waitForEvent('framenavigated', { predicate: f => f === frame, timeout: TIMEOUT });
  navigated.catch(() => {});
  await locator.click();
  await navigated;
  await frame.waitForLoadState('networkidle', { timeout: TIMEOUT });
}

async function submitAddForm(admin, frame, providerNo, lastName) {
  const form = frame.locator('form[name="searchprovider"]');
  const number = form.locator('input[name="provider_no"]');
  if ((await number.getAttribute('readonly')) !== null) {
    throw new h.SkipCheck('AUTO_GENERATE_PROVIDER_NO is on: the add form assigns numbers, so a chosen number cannot be exercised');
  }
  await number.fill(providerNo);
  await form.locator('input[name="last_name"]').fill(lastName);
  await form.locator('input[name="first_name"]').fill('Record');
  await form.locator('select[name="provider_type"]').selectOption('doctor');
  await form.locator('input[name="specialty"]').fill('GP');
  await form.locator('select[name="sex"]').selectOption('M');
  await navigateFrame(admin, frame, form.locator('input[type="submit"]'));
  h.assert(new URL(frame.url()).pathname.endsWith('/admin/ProviderAddARecord'), 'The add form did not post to ProviderAddARecord');
  return (await frame.locator('h1').first().innerText()).trim();
}

async function searchProviders(admin, frame, mode, keyword, status) {
  await frame.locator(`input[name="search_mode"][value="${mode}"]`).check();
  if (status) await frame.locator(`input[name="search_status"][value="${status}"]`).check();
  await frame.locator('input[name="keyword"]').fill(keyword);
  // providersearchrecordshtm.jsp nests the form inside a <table> (foster-parented),
  // so the submit is located by name rather than as a form descendant.
  await navigateFrame(admin, frame, frame.locator('input[name="button"], button[name="button"]').first());
  const { rows } = await dataTableRows(frame, '#tblResults', { timeout: TIMEOUT });
  return rows;
}

async function workflow(s) {
  const { sql, context, config, recorder } = s;
  const providerNo = pickUnusedProviderNo(sql);
  const no = h.sqlString(providerNo);
  const team = 'PW' + s.marker.slice(-8);
  const officialFirst = 'Official' + s.marker.slice(-6);
  // Provider number the GET ProviderAddARecord probe names. Owned only if a (wrongly
  // accepted) GET created it under the probe's own last name.
  let extra = null;
  const getLastName = h.sqlString(s.marker + '-GET');
  s.cleanup(() => {
    const owned = sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${no} AND last_name=${h.sqlString(s.marker)}`);
    h.assert(owned === '1' || sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${no}`) === '0',
      'Provider fixture ownership changed; refusing to clean up');
    for (const table of CHILD_TABLES) sql.execute(`DELETE FROM ${table} WHERE provider_no=${no}`);
    sql.execute(`DELETE FROM provider WHERE provider_no=${no} AND last_name=${h.sqlString(s.marker)}`);
    const ownedNos = [no];
    if (extra) {
      const x = h.sqlString(extra);
      if (sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${x} AND last_name=${getLastName}`) !== '0') {
        for (const table of CHILD_TABLES) sql.execute(`DELETE FROM ${table} WHERE provider_no=${x}`);
        sql.execute(`DELETE FROM provider WHERE provider_no=${x} AND last_name=${getLastName}`);
        ownedNos.push(x);
      }
    }
    const remaining = ownedNos.flatMap(p => CHILD_TABLES.map(table => `(SELECT COUNT(*) FROM ${table} WHERE provider_no=${p})`)
      .concat(`(SELECT COUNT(*) FROM provider WHERE provider_no=${p})`)).join('+');
    h.assert(sql.value(`SELECT ${remaining}+(SELECT COUNT(*) FROM provider WHERE last_name=${getLastName})`) === '0',
      'Owned provider fixtures were not removed');
  });
  // Every column the edit page can write, minus comments (rebuilt from the form's
  // xml_p_* inputs) and the audit timestamp; supervisor is coalesced because the
  // add stores '' and the update stores NULL for "none".
  const snapshotQuery = `SELECT provider_no,last_name,first_name,provider_type,COALESCE(supervisor,''),specialty,COALESCE(team,'NULL'),sex,
    COALESCE(dob,'NULL'),COALESCE(address,'NULL'),COALESCE(phone,'NULL'),COALESCE(work_phone,'NULL'),COALESCE(ohip_no,'NULL'),
    COALESCE(rma_no,'NULL'),COALESCE(billing_no,'NULL'),COALESCE(hso_no,'NULL'),COALESCE(status,'NULL'),COALESCE(provider_activity,'NULL'),
    COALESCE(practitionerNo,'NULL'),COALESCE(init,'NULL'),COALESCE(job_title,'NULL'),COALESCE(email,'NULL'),COALESCE(title,'NULL'),
    COALESCE(lastUpdateUser,'NULL'),COALESCE(signed_confidentiality,'NULL'),COALESCE(practitionerNoType,'NULL')
    FROM provider WHERE provider_no=${no}`;
  const snapshot = () => sql.rows(snapshotQuery)[0];
  const archives = () => sql.value(`SELECT COUNT(*) FROM providerArchive WHERE provider_no=${no}`);

  const { page: admin } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    { context, recorder, label: 'provider-admin', timeout: TIMEOUT });
  let frame;
  let created;
  await s.step('Add a Provider Record creates the owned provider under the chosen number', async () => {
    h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${no}`) === '0', 'The chosen provider number is already in use');
    frame = await openSection(admin, ADD_LINK, 'form[name="searchprovider"] input[name="last_name"]');
    const heading = await submitAddForm(admin, frame, providerNo, s.marker);
    h.assert(heading === 'Successful Addition of a Provider Record.', `The add reported "${heading}"`);
    created = snapshot();
    h.assert(created && created[1] === s.marker && created[2] === 'Record' && created[3] === 'doctor' && created[5] === 'GP' && created[7] === 'M',
      'The provider row was not created as submitted');
  });
  await s.step('adding the same provider number again is refused and leaves the row untouched', async () => {
    frame = await openSection(admin, ADD_LINK, 'form[name="searchprovider"] input[name="last_name"]');
    const heading = await submitAddForm(admin, frame, providerNo, s.marker + '-DUP');
    h.assert(heading === 'Sorry, addition has failed.' && /Provider No already in use/.test(await frame.locator('h2').first().innerText()),
      'The duplicate provider number was not refused');
    h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${no} OR last_name=${h.sqlString(s.marker + '-DUP')}`) === '1'
      && JSON.stringify(snapshot()) === JSON.stringify(created), 'The refused duplicate changed or duplicated the provider');
  });
  await s.step('Search/Edit Provider Records lists the owned provider by last name', async () => {
    frame = await openSection(admin, SEARCH_LINK, 'input[name="keyword"]');
    const rows = await searchProviders(admin, frame, 'search_name', s.marker);
    const row = rows.filter({ has: frame.getByRole('link', { name: providerNo, exact: true }) });
    h.assert(await rows.count() === 1 && await row.count() === 1, 'The name search did not list exactly the owned provider');
    h.assert((await row.locator('td').nth(1).innerText()).trim() === `${s.marker}, Record`, 'The listing shows the wrong provider name');
    h.assert(JSON.stringify(snapshot()) === JSON.stringify(created), 'Searching modified the provider');
  });
  await s.step('the edit page opens the owned provider and reflects its stored values', async () => {
    await navigateFrame(admin, frame, frame.getByRole('link', { name: providerNo, exact: true }));
    h.assert(new URL(frame.url()).searchParams.get('keyword') === providerNo, 'The edit page opened a provider other than the owned one');
    const form = frame.locator('form[name="updatearecord"]');
    h.assert(await form.locator('input[name="provider_no"]').inputValue() === providerNo, 'The edit form carries the wrong provider number');
    h.assert(await form.locator('input[name="last_name"]').inputValue() === s.marker
      && await form.locator('input[name="first_name"]').inputValue() === 'Record'
      && await form.locator('input[name="specialty"]').inputValue() === 'GP'
      && await form.locator('select[name="provider_type"]').inputValue() === 'doctor', 'The edit form does not reflect the stored row');
  });
  await s.step('Update Record writes exactly the edited columns, one archive copy and the official name', async () => {
    const form = frame.locator('form[name="updatearecord"]');
    await form.locator('input[name="specialty"]').fill('Playwright QA');
    await form.locator('input[name="team"]').fill(team);
    await form.locator('input[name="phone"]').fill('555-0100');
    await form.locator('input[name="email"]').fill('fake-pw@example.invalid');
    await form.locator('input[name="officialFirstName"]').fill(officialFirst);
    await form.locator('#statusActive').check();
    await navigateFrame(admin, frame, form.locator('input[name="subbutton"]'));
    h.assert(new URL(frame.url()).pathname.endsWith('/admin/ProviderUpdate')
      && /Update a Provider Record Successfully/.test(await frame.locator('h2').first().innerText())
      && await frame.locator(`h2 a[href*="ViewProviderUpdateProvider?keyword=${providerNo}"]`).count() === 1, 'The update did not report success');
    const expected = [...created];
    expected[5] = 'Playwright QA'; expected[6] = team; expected[10] = '555-0100'; expected[21] = 'fake-pw@example.invalid'; expected[16] = '1';
    h.assert(JSON.stringify(snapshot()) === JSON.stringify(expected), 'The provider row did not change exactly as submitted');
    h.assert(sql.value(`SELECT lastUpdateDate >= NOW() - INTERVAL 5 MINUTE FROM provider WHERE provider_no=${no}`) === '1',
      'The update did not stamp lastUpdateDate');
    h.assert(archives() === '1' && sql.value(`SELECT CONCAT(last_name,'|',specialty) FROM providerArchive WHERE provider_no=${no}`) === `${s.marker}|GP`,
      'The update did not archive exactly the pre-edit row');
    h.assert(sql.value(`SELECT value FROM property WHERE provider_no=${no} AND name='official_first_name'`) === officialFirst,
      'The official first name property was not saved');
  });
  await s.step('the post-edit listing by number shows the new specialty and active status', async () => {
    frame = await openSection(admin, SEARCH_LINK, 'input[name="keyword"]');
    const rows = await searchProviders(admin, frame, 'search_providerno', providerNo, '1');
    const row = rows.filter({ has: frame.getByRole('link', { name: providerNo, exact: true }) });
    h.assert(await row.count() === 1, 'The number search with the Active filter did not list the owned provider');
    const cells = await row.locator('td').allInnerTexts();
    h.assert(cells[3].trim() === 'Playwright QA' && cells[4].trim() === team && cells[7].trim() === 'Active',
      'The listing does not show the edited values');
  });
  await s.step('ProviderUpdate and ProviderAddARecord refuse GET without writing', async () => {
    const after = snapshot();
    const update = await context.request.get(h.appUrl(config.baseUrl, '/admin/ProviderUpdate'), {
      params: { provider_no: providerNo, last_name: s.marker, first_name: 'Record', provider_type: 'doctor', specialty: 'MUST NOT SAVE',
        team, sex: 'M', status: '1', officialFirstName: 'MUST NOT SAVE' }, maxRedirects: 0 });
    h.assert(update.status() === 405, `ProviderUpdate answered GET with HTTP ${update.status()}, expected 405`);
    extra = pickUnusedProviderNo(sql);
    const add = await context.request.get(h.appUrl(config.baseUrl, '/admin/ProviderAddARecord'), {
      params: { provider_no: extra, last_name: s.marker + '-GET', first_name: 'Record', provider_type: 'doctor', sex: 'M' }, maxRedirects: 0 });
    h.assert(add.status() === 405, `ProviderAddARecord answered GET with HTTP ${add.status()}, expected 405`);
    h.assert(JSON.stringify(snapshot()) === JSON.stringify(after) && archives() === '1'
      && sql.value(`SELECT value FROM property WHERE provider_no=${no} AND name='official_first_name'`) === officialFirst
      && sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${h.sqlString(extra)} OR last_name=${h.sqlString(s.marker + '-GET')}`) === '0',
    'A rejected GET wrote provider data');
  });
}
if (require.main === module) runWorkflow('provider-record-admin', workflow, { openPatient: false });
module.exports = { workflow };
