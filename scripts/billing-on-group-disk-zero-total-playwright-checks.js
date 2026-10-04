#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Browser check for issue #3942: the Ontario group OHIP disk must include a
 * group member whose claims total exactly $0, and must leave out a member who
 * has no claims at all.
 *
 * The defect: BillingOnDiskService skipped a group member when its batch TOTAL
 * was $0, so $0-value claims (tracking codes, items netting to zero) were never
 * submitted and stayed unbilled (status O) forever. The fix, ported from
 * openo-beta/Open-O PR #2510 by Sebastian Ibanez, decides membership by claim
 * ITEM count instead. On a build without the fix this check fails at the
 * "$0 member" assertions.
 *
 * Journey: generate separate disks for the owned ZERO and PAID providers using a
 * single-day service window, then use the rendered R button to regenerate ZERO
 * with legacy empty-member metadata.
 * EMPTY never contributes an output batch. The combined all-provider group case
 * is covered in BillingOnDiskServiceGroupDiskUnitTest without billing unrelated
 * providers in a shared validation database.
 *
 * Fixture: three providers in a unique group, two zero-fee items, one paid item,
 * and NULL optional claim fields. Cleanup proves ownership using the unique run
 * marker, provider IDs and disk membership; it also removes regeneration backups.
 *
 * Environment (docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN, CHROME_PATH,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE, OHIP_DISK_DIR (local HOME_DIR)
 * Optional:
 *   GROUP_DISK_DEMOGRAPHIC_NO   patient the fixture claims bill (default: lowest
 *                               demographic with a 10-digit Ontario HIN)
 *   GROUP_DISK_TEMPLATE_PROVIDER billable provider the fixture providers are
 *                               cloned from (default 999998)
 *   GROUP_DISK_SERVICE_DATE     YYYY-MM-DD for both bounds of the isolated window (default 2003-02-03)
 *   GROUP_DISK_PAID_CODE        OHIP code billed by PAID (default A007A)
 */

const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { randomBytes } = require('node:crypto');
const { withOhipTestLock } = require('./lib/ohip-test-lock');
const {
  SkipCheck, appUrl, assert, assertNotErrorPage, assertStrictPage, createRecorder, createSqlRunner,
  gotoApp, launchBrowser, login, newContext, readConfig, runCheck, sqlString, wireStrictPage, withExpectedDialogs,
} = require('./lib/playwright-harness');

const MEMBERS = ['ZERO', 'PAID', 'EMPTY'];

function isoDate(name, raw, fallback) {
  const value = raw || fallback;
  assert(/^\d{4}-\d{2}-\d{2}$/.test(value), `${name} must be YYYY-MM-DD, got ${value}`);
  return value;
}

function shiftDays(date, days) {
  const d = new Date(`${date}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

/** A column list for INSERT ... SELECT cloning, read from the live schema. */
function columnsOf(db, table) {
  return db.rows(`SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE()`
    + ` AND table_name=${sqlString(table)} ORDER BY ordinal_position`).map((row) => row[0]);
}

function unusedNumber(db, digits, prefix, taken) {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    const tail = String(Math.floor(Math.random() * (10 ** (digits - prefix.length)))).padStart(digits - prefix.length, '0');
    const candidate = `${prefix}${tail}`;
    if (!taken(candidate)) {
      return candidate;
    }
  }
  throw new Error(`could not find an unused ${digits}-digit number with prefix ${prefix}`);
}

function createFixture(db, options, state) {
  const template = options.templateProvider;
  const templateRow = db.rows(`SELECT provider_no, ohip_no, status FROM provider WHERE provider_no=${sqlString(template)}`);
  if (!templateRow.length) {
    throw new SkipCheck(`template provider ${template} does not exist; set GROUP_DISK_TEMPLATE_PROVIDER`);
  }

  const demo = options.demographicNo
    ? db.rows(`SELECT demographic_no, hin, ver, year_of_birth, month_of_birth, date_of_birth, sex FROM demographic`
      + ` WHERE demographic_no=${Number(options.demographicNo)}`)
    : db.rows('SELECT demographic_no, hin, ver, year_of_birth, month_of_birth, date_of_birth, sex FROM demographic'
      + " WHERE hin REGEXP '^[0-9]{10}$' AND hc_type='ON' ORDER BY demographic_no LIMIT 1");
  if (!demo.length || !/^\d{10}$/.test(demo[0][1] || '')) {
    throw new SkipCheck('no Ontario patient with a 10-digit HIN to bill; set GROUP_DISK_DEMOGRAPHIC_NO');
  }
  const [demographicNo, hin, ver, yob, mob, dob, sex] = demo[0];
  const dob8 = `${yob}${String(mob).padStart(2, '0')}${String(dob).padStart(2, '0')}`;

  const paidFee = db.value(`SELECT value FROM billingservice WHERE service_code=${sqlString(options.paidCode)}`
    + ` ORDER BY billingservice_date DESC LIMIT 1`);
  if (!paidFee || Number(paidFee) <= 0) {
    throw new SkipCheck(`service code ${options.paidCode} has no positive schedule fee; set GROUP_DISK_PAID_CODE`);
  }

  state.groupNo = unusedNumber(db, 4, '8', (candidate) => db.value(
    `SELECT (SELECT COUNT(*) FROM provider WHERE comments LIKE ${sqlString(`%<xml_p_billinggroup_no>${candidate}<%`)})`
    + ` + (SELECT COUNT(*) FROM billing_on_diskname WHERE groupno=${sqlString(candidate)})`) !== '0');

  const providerColumns = columnsOf(db, 'provider');
  const overrides = {
    first_name: null, last_name: null, provider_no: null, ohip_no: null, comments: null, status: "'1'",
  };
  for (const member of MEMBERS) {
    const providerNo = unusedNumber(db, 6, '97', (candidate) => db.value(
      `SELECT COUNT(*) FROM provider WHERE provider_no=${sqlString(candidate)}`) !== '0');
    const ohipNo = unusedNumber(db, 6, '96', (candidate) => db.value(
      `SELECT COUNT(*) FROM provider WHERE ohip_no=${sqlString(candidate)}`) !== '0');
    overrides.provider_no = sqlString(providerNo);
    overrides.ohip_no = sqlString(ohipNo);
    overrides.last_name = sqlString(`PW3942-${member}`);
    overrides.first_name = sqlString(state.marker);
    overrides.comments = sqlString(`<xml_p_billinggroup_no>${state.groupNo}</xml_p_billinggroup_no>`
      + '<xml_p_specialty_code>00</xml_p_specialty_code>');
    const select = providerColumns.map((column) => (column in overrides ? overrides[column] : `\`${column}\``));
    state.providers[member] = { providerNo, ohipNo }; // Record intent before the INSERT can fail.
    db.execute(`INSERT INTO provider (${providerColumns.map((c) => `\`${c}\``).join(',')})`
      + ` SELECT ${select.join(',')} FROM provider WHERE provider_no=${sqlString(template)}`);
    // Put the provider in the operator's sites. With _site_access_privacy the
    // diskette page lists only providers who share a site with the operator,
    // which is how a clinic's own providers are always set up.
    db.execute(`INSERT IGNORE INTO providersite (provider_no, site_id) SELECT DISTINCT ${sqlString(providerNo)}, s.site_id`
      + ` FROM providersite s WHERE s.provider_no=${sqlString(template)} OR s.provider_no IN`
      + ` (SELECT provider_no FROM security WHERE user_name=${sqlString(options.testUser)})`);
  }

  const claims = [
    { member: 'ZERO', items: [{ code: options.paidCode, fee: '0.00' }, { code: options.paidCode, fee: '0.00' }] },
    { member: 'PAID', items: [{ code: options.paidCode, fee: Number(paidFee).toFixed(2) }] },
  ];
  for (const claim of claims) {
    const { providerNo, ohipNo } = state.providers[claim.member];
    const total = claim.items.reduce((sum, item) => sum + Number(item.fee), 0).toFixed(2);
    // Legacy claims can have NULL optional fields; exercise the production exporter.
    db.execute('INSERT INTO billing_on_cheader1 (header_id, transc_id, rec_id, hin, ver, dob, pay_program, payee,'
      + ' ref_num, facilty_num, admission_date, ref_lab_num, man_review, location, demographic_no, provider_no, appointment_no, demographic_name, sex, province, billing_date,'
      + ' billing_time, total, paid, status, comment1, visittype, provider_ohip_no, provider_rma_no, apptProvider_no,'
      + ' creator, clinic)'
      + ` VALUES (0, 'HE', 'H', ${sqlString(hin)}, ${sqlString(ver || '')}, ${sqlString(dob8)}, 'HCP', 'P',`
      + ` NULL, NULL, '', NULL, NULL, NULL, ${Number(demographicNo)}, ${sqlString(providerNo)}, 0, 'PW3942,Fixture', ${sqlString(sex === 'F' ? '2' : '1')},`
      + ` 'ON', ${sqlString(options.window.serviceDate)}, '09:00:00', ${total}, 0.00, 'O',`
      + ` ${sqlString(`${state.marker} ${claim.member}`)}, '00', ${sqlString(ohipNo)}, '', ${sqlString(providerNo)},`
      + ` ${sqlString(providerNo)}, '')`);
    const headerId = db.value(`SELECT MAX(id) FROM billing_on_cheader1 WHERE provider_no=${sqlString(providerNo)}`
      + ` AND comment1=${sqlString(`${state.marker} ${claim.member}`)}`);
    assert(/^\d+$/.test(headerId), `could not seed the ${claim.member} claim header`);
    state.headerIds.push(headerId);
    state.providers[claim.member].headerId = headerId;
    for (const item of claim.items) {
      db.execute('INSERT INTO billing_on_item (ch1_id, transc_id, rec_id, service_code, fee, ser_num, service_date, dx, status)'
        + ` VALUES (${Number(headerId)}, 'HE', 'T', ${sqlString(item.code)}, ${sqlString(item.fee)}, '1',`
        + ` ${sqlString(options.window.serviceDate)}, '250', 'O')`);
    }
    state.providers[claim.member].itemCount = claim.items.length;
    state.providers[claim.member].total = total;
  }
  return state;
}

function checkedDiskDirectory(directory) {
  assert(directory && path.isAbsolute(directory), 'OHIP_DISK_DIR must name the local HOME_DIR');
  const root = fs.realpathSync(directory);
  assert(![path.parse(root).root, os.homedir(), process.cwd()].includes(root),
    'OHIP_DISK_DIR must be a dedicated output directory');
  assert(fs.statSync(root).isDirectory(), 'OHIP_DISK_DIR is not a directory');
  fs.accessSync(root, fs.constants.R_OK | fs.constants.W_OK);
  return root;
}

function removeOwnedFiles(directory, names) {
  for (const name of names) {
    assert(/^[A-Za-z0-9][A-Za-z0-9._-]*$/.test(name), 'Refusing an invalid billing output filename');
  }
  const owned = fs.readdirSync(directory).filter((entry) => names.some((name) =>
    entry === name || (entry.startsWith(`${name}.`) && /^\d+$/.test(entry.slice(name.length + 1)))
    || (entry.startsWith(`.ohip-preview-${name}-`)
      && /^\d+\.bak$/.test(entry.slice(`.ohip-preview-${name}-`.length)))));
  // Validate the entire set before removing any of it; never follow symlinks.
  for (const entry of owned) {
    const stat = fs.lstatSync(path.join(directory, entry));
    assert(stat.isFile() && !stat.isSymbolicLink(), 'Refusing a non-regular billing output');
  }
  for (const entry of owned) fs.unlinkSync(path.join(directory, entry));
}

function ownedProviderIds(db, state) {
  return Object.values(state.providers).filter(({ providerNo }) => db.value(
    `SELECT COUNT(*) FROM provider WHERE provider_no=${sqlString(providerNo)}`
    + ` AND first_name=${sqlString(state.marker)}`) === '1').map(({ providerNo }) => providerNo);
}

function ownedDisks(db, state, providers) {
  if (!providers.length || !state.groupNo) return [];
  const ids = providers.map(sqlString).join(',');
  return db.rows(`SELECT d.id, d.ohipfilename FROM billing_on_diskname d WHERE d.groupno=${sqlString(state.groupNo)}`
    + ` AND EXISTS (SELECT 1 FROM billing_on_filename f WHERE f.disk_id=d.id AND f.providerno IN (${ids}))`
    + ` AND NOT EXISTS (SELECT 1 FROM billing_on_filename f WHERE f.disk_id=d.id AND (f.providerno IS NULL OR f.providerno NOT IN (${ids})))`);
}

function removeFixture(db, state, diskDir) {
  if (!state) return;
  const providers = ownedProviderIds(db, state);
  for (const [diskId, ohipFile] of ownedDisks(db, state, providers)) {
    const htmlFiles = db.rows(`SELECT htmlfilename FROM billing_on_filename WHERE disk_id=${Number(diskId)}`).map((r) => r[0]);
    removeOwnedFiles(diskDir, [ohipFile, ...htmlFiles]);
    // One mysql invocation owns the complete transaction (separate calls use separate connections).
    db.execute('START TRANSACTION;'
      + ` DELETE r FROM billing_on_repo r JOIN billing_on_header h ON h.id=r.h_id`
      + ` WHERE r.category='billing_on_header' AND h.disk_id=${Number(diskId)};`
      + ` DELETE FROM billing_on_header WHERE disk_id=${Number(diskId)};`
      + ` DELETE FROM billing_on_filename WHERE disk_id=${Number(diskId)};`
      + ` DELETE FROM billing_on_diskname WHERE id=${Number(diskId)}; COMMIT;`);
  }
  for (const providerNo of providers) {
    // Recover rows even if INSERT succeeded but its result/next query failed.
    const claims = db.rows(`SELECT id FROM billing_on_cheader1 WHERE provider_no=${sqlString(providerNo)}`
      + ` AND comment1 IN (${MEMBERS.map((member) => sqlString(`${state.marker} ${member}`)).join(',')})`);
    for (const [headerId] of claims) {
      db.execute('START TRANSACTION;'
        + ` DELETE FROM billing_on_item WHERE ch1_id=${Number(headerId)};`
        + ` DELETE FROM billing_on_cheader1 WHERE id=${Number(headerId)}; COMMIT;`);
    }
    // If an unexpected association remains, retain the provider and report it for reconciliation.
    assert(db.value(`SELECT COUNT(*) FROM billing_on_filename WHERE providerno=${sqlString(providerNo)}`) === '0',
      'Owned fixture provider still has disk associations; cleanup stopped');
    assert(db.value(`SELECT COUNT(*) FROM billing_on_cheader1 WHERE provider_no=${sqlString(providerNo)}`) === '0',
      'Owned fixture provider still has unexpected claims; cleanup stopped');
    db.execute('START TRANSACTION;'
      + ` DELETE FROM providersite WHERE provider_no=${sqlString(providerNo)};`
      + ` DELETE FROM provider WHERE provider_no=${sqlString(providerNo)} AND first_name=${sqlString(state.marker)}; COMMIT;`);
  }
}

async function cleanupResources(browser, db, state, diskDir) {
  const failures = [];
  try { if (browser) await browser.close(); } catch (error) { failures.push(error); }
  try { removeFixture(db, state, diskDir); } catch (error) { failures.push(error); }
  finally { try { db.dispose(); } catch (error) { failures.push(error); } }
  if (failures.length) throw new AggregateError(failures, 'OHIP browser/fixture cleanup incomplete');
}

async function generateProviderDisk(context, config, recorder, window, providerNo, expectBusy = false) {
  const page = await context.newPage();
  wireStrictPage(page, 'ohip-disk', recorder);
  await gotoApp(page, config.baseUrl, '/billing/CA/ON/ViewBillingONMRI');
  await assertNotErrorPage(page, 'Generate OHIP diskette page');
  const form = page.locator('form[name="form1"]');
  await form.waitFor({ state: 'visible', timeout: 30000 });
  await form.locator('select[name="providers"]').selectOption(providerNo);
  // Both date inputs carry flatpickr (allowInput): typing sets the value, and
  // the open calendar then covers the submit button until the user clicks
  // away -- so click the page heading, as an operator would, and wait for it.
  for (const [selector, value] of [['#xml_vdate', window.start], ['#xml_appointment_date', window.end]]) {
    await form.locator(selector).fill(value);
    await page.locator('h3').first().click();
    await page.locator('.flatpickr-calendar.open').waitFor({ state: 'detached', timeout: 10000 })
      .catch(() => page.locator('.flatpickr-calendar.open').first().waitFor({ state: 'hidden', timeout: 10000 }));
    assert(await form.locator(selector).inputValue() === value, `the ${selector} date did not keep ${value}`);
  }
  const useProviderMoh = form.locator('#useProviderMOH');
  if (await useProviderMoh.isChecked()) {
    await useProviderMoh.uncheck();
  }
  const [response] = await Promise.all([
    page.waitForResponse((r) => r.request().method() === 'POST'
      && new URL(r.url()).pathname.endsWith('/billing/CA/ON/ViewOngenreport'), { timeout: 120000 }),
    form.locator('input[type="submit"][name="Submit"]').click(),
  ]);
  assert(response.status() === 200, `Create Report answered HTTP ${response.status()}`);
  await page.waitForLoadState('domcontentloaded', { timeout: 60000 });
  if (expectBusy) {
    assert((await page.locator('body').innerText()).includes('already in progress'), 'Overlapping export did not show the busy error');
    assert((await page.locator('body').innerText()).includes('Incident reference:'), 'Missing busy-operation incident reference');
    return page;
  }
  await assertNotErrorPage(page, 'OHIP diskette page after Create Report');
  // A failed generation is mapped to an operator error page that still
  // answers 200; only the diskette page itself carries the Create Report form.
  assert(await page.locator('form[name="form1"]').count() === 1,
    'Create Report did not return to the diskette page; disk generation failed (see the server log incident id)');
  return page;
}

async function main() {
  const config = readConfig();
  const serviceDate = isoDate('GROUP_DISK_SERVICE_DATE', process.env.GROUP_DISK_SERVICE_DATE, '2003-02-03');
  // A single-day export must include claims exactly on Service Date Start (#4164).
  const window = { serviceDate, start: serviceDate, end: serviceDate };
  const paidCode = (process.env.GROUP_DISK_PAID_CODE || 'A007A').toUpperCase();
  assert(/^[A-Z]\d{3}[A-Z]$/.test(paidCode), `GROUP_DISK_PAID_CODE must look like A007A, got ${paidCode}`);
  const templateProvider = process.env.GROUP_DISK_TEMPLATE_PROVIDER || '999998';
  assert(/^\d{1,6}$/.test(templateProvider), 'GROUP_DISK_TEMPLATE_PROVIDER must be a provider number');
  const demographicNo = process.env.GROUP_DISK_DEMOGRAPHIC_NO || '';
  assert(!demographicNo || /^\d+$/.test(demographicNo), 'GROUP_DISK_DEMOGRAPHIC_NO must be numeric');
  const diskDir = checkedDiskDirectory(process.env.OHIP_DISK_DIR || '');

  const state = { providers: {}, headerIds: [], groupNo: '', marker: `PW3942-${randomBytes(8).toString('hex')}` };
  const recorder = createRecorder();
  let browser = null;
  const db = createSqlRunner(config.mysql);

  const run = async () => {
    createFixture(db, {
      templateProvider, window, paidCode, demographicNo, testUser: config.testUser,
    }, state);
    const { ZERO, PAID, EMPTY } = state.providers;

    browser = await launchBrowser(config);
    const context = await newContext(browser, config);
    await login(context, config, recorder);
    // Let the application create its own lock inode before the external-lock checks.
    const emptyPage = await generateProviderDisk(context, config, recorder, window, EMPTY.providerNo);
    const tokenInput = emptyPage.locator('form[name="form1"] input[name="CSRF-TOKEN"]');
    await tokenInput.waitFor({ state: 'attached' });
    const csrfToken = await tokenInput.inputValue();
    const privateInput = `PRIVATE_PATIENT_${randomBytes(8).toString('hex')}`;
    const diagnosesBefore = db.value('SELECT COUNT(*) FROM dxresearch');
    const rejected = await context.request.post(appUrl(config.baseUrl, '/billing/CA/ON/ViewBillingONReview'), {
      headers: { 'CSRF-TOKEN': csrfToken },
      form: { 'CSRF-TOKEN': csrfToken, addToPatientDx: 'yes', demographic_no: privateInput, dxCode: '401' },
      maxRedirects: 0,
    });
    assert(rejected.status() === 200, `Mapped billing rejection answered HTTP ${rejected.status()}`);
    const rejectionHtml = await rejected.text();
    assert(rejectionHtml.includes('Billing information could not be validated.'), 'Missing public validation guidance');
    assert(rejectionHtml.includes('Incident reference:'), 'Missing validation incident reference');
    assert(!rejectionHtml.includes(privateInput), 'Billing rejection disclosed the submitted private value');
    assert(!rejectionHtml.includes('NumberFormatException'), 'Billing rejection disclosed its internal cause');
    assert(db.value('SELECT COUNT(*) FROM dxresearch') === diagnosesBefore, 'Rejected billing input wrote a diagnosis');
    console.log('  Rejected billing input: fixed public guidance, incident reference, no private value or diagnosis write');
    await emptyPage.close();
    await withOhipTestLock(diskDir, async () => {
      const blocked = await generateProviderDisk(context, config, recorder, window, ZERO.providerNo, true);
      await blocked.close();
    });
    assert(db.value(`SELECT COUNT(*) FROM billing_on_diskname d JOIN billing_on_filename f ON f.disk_id=d.id`
      + ` WHERE d.groupno=${sqlString(state.groupNo)} AND f.providerno=${sqlString(ZERO.providerNo)}`) === '0',
      'Blocked export allocated a disk');
    assert(db.value(`SELECT status FROM billing_on_cheader1 WHERE id=${Number(ZERO.headerId)}`) === 'O',
      'Blocked export billed the claim');
    for (const member of [ZERO, PAID]) {
      const page = await generateProviderDisk(context, config, recorder, window, member.providerNo);
      const disks = db.rows(`SELECT d.id, d.ohipfilename FROM billing_on_diskname d JOIN billing_on_filename f ON f.disk_id=d.id`
        + ` WHERE d.groupno=${sqlString(state.groupNo)} AND f.providerno=${sqlString(member.providerNo)}`);
      assert(disks.length === 1, 'Expected exactly one disk for the selected fixture provider');
      const [diskId, ohipFile] = disks[0];
      member.diskId = Number(diskId);
      const checkDownload = async () => {
        assert(fs.existsSync(path.join(diskDir, ohipFile)),
          `Single-day export produced no claim file; claim status is ${db.value(
            `SELECT status FROM billing_on_cheader1 WHERE id=${Number(member.headerId)}`)}`);
        const link = page.locator(`a[href*="filename=${encodeURIComponent(ohipFile)}"]`).first();
        assert(await link.count() === 1, 'Diskette page is missing the generated download link');
        const response = await context.request.get(new URL(await link.getAttribute('href'), page.url()).toString(), { maxRedirects: 0 });
        assert(response.status() === 200, 'OHIP download failed');
        const records = (await response.body()).toString('latin1').split(/[\r\n]+/).filter(Boolean);
        await response.dispose();
        const batches = records.filter((line) => line.startsWith('HEB'));
        assert(batches.length === 1 && batches[0].includes(member.ohipNo),
          'Selected member batch missing, or unrelated/empty member was emitted');
        assert(records.filter((line) => line.startsWith('HEH')).length === 1, 'Expected one claim header');
        const items = records.filter((line) => line.startsWith('HET'));
        assert(items.length === member.itemCount, 'Incorrect claim item count');
        assert(items.every(line => line.slice(18, 26) === serviceDate.replaceAll('-', '')),
          'The single-day claim file contains a different service date');
        const [status, headerId] = db.rows(`SELECT status, header_id FROM billing_on_cheader1 WHERE id=${Number(member.headerId)}`)[0];
        assert(status === 'B', 'Emitted claim was not marked billed');
        assert(db.value(`SELECT COUNT(*) FROM billing_on_header WHERE id=${Number(headerId)} AND disk_id=${Number(diskId)}`) === '1',
          'Claim does not belong to its disk batch');
        const [count, total] = db.rows(`SELECT claimrecord, total FROM billing_on_filename WHERE disk_id=${Number(diskId)}`
          + ` AND providerno=${sqlString(member.providerNo)}`)[0];
        assert(count.endsWith(`/${member.itemCount}`) && Number(total) === Number(member.total), 'Incorrect persisted disk summary');
      };
      await checkDownload();
      if (member === ZERO) {
        assert(db.value(`SELECT status FROM billing_on_cheader1 WHERE id=${Number(PAID.headerId)}`) === 'O',
          'Generating ZERO also billed the unselected PAID member');
        // Historical all-provider disks can contain empty member metadata. Clone only
        // this run's own rows to reproduce that shape before the actual R-button flow.
        for (const [table, overrides] of [
          ['billing_on_filename', { providerno: sqlString(EMPTY.providerNo), providerohipno: sqlString(EMPTY.ohipNo),
            htmlfilename: sqlString(`${state.marker}-empty.html`), claimrecord: "''", total: "''" }],
          ['billing_on_header', { provider_reg_num: sqlString(EMPTY.ohipNo) }],
        ]) {
          const columns = columnsOf(db, table).filter((column) => column !== 'id');
          db.execute(`INSERT INTO ${table} (${columns.map((c) => `\`${c}\``).join(',')}) SELECT `
            + columns.map((c) => overrides[c] || `\`${c}\``).join(',')
            + ` FROM ${table} WHERE disk_id=${Number(diskId)} LIMIT 1`);
        }
        const regenerate = async (expectBusy = false) => {
          const row = page.locator('tr').filter({ has: page.locator(`a[href*="filename=${encodeURIComponent(ohipFile)}"]`) }).first();
          const [response] = await Promise.all([
            page.waitForResponse((r) => r.request().method() === 'POST'
              && new URL(r.url()).pathname.endsWith('/billing/CA/ON/ViewOnregenreport'), { timeout: 120000 }),
            withExpectedDialogs(page, () => row.locator('input[value="R"]').click()).then((dialogs) => {
              assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Regeneration must ask for confirmation');
            }),
          ]);
          assert(response.status() === 200, `Regenerate request failed: HTTP ${response.status()}`);
          await page.waitForLoadState('domcontentloaded');
          if (expectBusy) {
            assert((await page.locator('body').innerText()).includes('already in progress'), 'Overlapping regeneration did not show the busy error');
            assert((await page.locator('body').innerText()).includes('Incident reference:'), 'Missing busy-operation incident reference');
          } else {
            await assertNotErrorPage(page, 'Regenerated OHIP disk');
            assert(await page.locator('form[name="form1"]').count() === 1, 'Regeneration returned the failure page');
          }
        };
        const beforeOutput = fs.readFileSync(path.join(diskDir, ohipFile));
        const beforeHeaders = JSON.stringify(db.rows(`SELECT * FROM billing_on_header WHERE disk_id=${Number(diskId)} ORDER BY id`));
        await withOhipTestLock(diskDir, () => regenerate(true));
        assert(fs.readFileSync(path.join(diskDir, ohipFile)).equals(beforeOutput), 'Blocked regeneration changed the existing download');
        assert(JSON.stringify(db.rows(`SELECT * FROM billing_on_header WHERE disk_id=${Number(diskId)} ORDER BY id`)) === beforeHeaders,
          'Blocked regeneration changed batch metadata');
        await gotoApp(page, config.baseUrl, '/billing/CA/ON/ViewBillingONMRI');
        await regenerate();
        await checkDownload();
        assert(db.value(`SELECT claimrecord FROM billing_on_filename WHERE disk_id=${Number(diskId)}`
          + ` AND providerno=${sqlString(EMPTY.providerNo)}`) === '', 'Empty member was finalized during regeneration');
      }
      await page.close();
    }
    const emptyDisks = db.rows(`SELECT d.ohipfilename FROM billing_on_diskname d JOIN billing_on_filename f ON f.disk_id=d.id`
      + ` WHERE d.groupno=${sqlString(state.groupNo)} AND f.providerno=${sqlString(EMPTY.providerNo)}`
      + ` AND d.id<>${ZERO.diskId}`);
    assert(emptyDisks.length === 1, 'Expected one empty-provider allocation');
    assert(!fs.existsSync(path.join(diskDir, emptyDisks[0][0])), 'Empty provider produced an OHIP file');

    assertStrictPage(recorder);
    console.log('  Overlapping export/regeneration refused without mutation; ZERO and PAID exported separately; NULL fields accepted; ZERO regenerated; EMPTY omitted');
    return { diskId: ZERO.diskId };
  };

  const cleanup = () => cleanupResources(browser, db, state, diskDir);

  return { run, cleanup };
}

if (require.main === module) {
  (async () => {
    let handles = null;
    await runCheck({
      name: 'billing-on-group-disk-zero-total',
      run: async () => {
        handles = await main();
        return handles.run();
      },
      cleanup: async () => handles && handles.cleanup(),
    });
  })();
}

module.exports = { shiftDays, isoDate, createFixture, removeFixture, removeOwnedFiles, checkedDiskDirectory, cleanupResources };
