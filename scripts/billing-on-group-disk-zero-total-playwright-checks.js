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
 * Journey (the operator's path): Billing > Generate OHIP diskette
 * (billing/CA/ON/ViewBillingONMRI) -> "All Providers", a service-date window,
 * "Create Report" -> the page re-renders with the new disk; the OHIP file is
 * fetched through the page's own download link.
 *
 * Fixture (seeded here, removed in cleanup): a throwaway billing group of three
 * providers cloned from an existing billable provider --
 *   ZERO  two claim items at $0.00 (one claim)
 *   PAID  one claim item at a real fee
 *   EMPTY no claims in the window
 * all inside an isolated historical service-date window that must hold no other
 * unbilled (O/W/I) claim, so "All Providers" bills nothing but the fixture.
 * "All Providers" also writes an (empty) solo disk for every solo provider;
 * cleanup removes every disk row this run created. It cannot remove the files
 * those disks wrote unless OHIP_DISK_DIR names HOME_DIR as this process sees it.
 *
 * Environment (docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN, CHROME_PATH,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE
 * Optional:
 *   GROUP_DISK_DEMOGRAPHIC_NO   patient the fixture claims bill (default: lowest
 *                               demographic with a 10-digit Ontario HIN)
 *   GROUP_DISK_TEMPLATE_PROVIDER billable provider the fixture providers are
 *                               cloned from (default 999998)
 *   GROUP_DISK_SERVICE_DATE     YYYY-MM-DD inside the isolated window (default 2003-02-03)
 *   GROUP_DISK_PAID_CODE        OHIP code billed by PAID (default A007A)
 *   OHIP_DISK_DIR               HOME_DIR, to delete the disk files this run wrote
 */

const fs = require('node:fs');
const path = require('node:path');
const {
  SkipCheck, assert, assertNotErrorPage, assertStrictPage, createRecorder, createSqlRunner,
  gotoApp, launchBrowser, login, newContext, readConfig, runCheck, sqlString, wireStrictPage,
} = require('./lib/playwright-harness');

const MEMBERS = ['ZERO', 'PAID', 'EMPTY'];
const BILLING_STATUS_NEW = ['O', 'W', 'I'];

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

function createFixture(db, options) {
  const state = {
    providers: {}, headerIds: [], diskBaseline: 0, groupNo: '', window: options.window,
  };
  const template = options.templateProvider;
  const templateRow = db.rows(`SELECT provider_no, ohip_no, status FROM provider WHERE provider_no=${sqlString(template)}`);
  if (!templateRow.length) {
    throw new SkipCheck(`template provider ${template} does not exist; set GROUP_DISK_TEMPLATE_PROVIDER`);
  }

  const pending = db.value(`SELECT COUNT(*) FROM billing_on_cheader1 WHERE billing_date BETWEEN`
    + ` ${sqlString(options.window.start)} AND ${sqlString(options.window.end)}`
    + ` AND status IN (${BILLING_STATUS_NEW.map(sqlString).join(',')})`);
  if (pending !== '0') {
    throw new SkipCheck(`the service-date window ${options.window.start}..${options.window.end} already holds`
      + ` ${pending} unbilled claim(s); "All Providers" would bill them. Set GROUP_DISK_SERVICE_DATE to an empty window`);
  }

  state.diskBaseline = Number(db.value('SELECT COALESCE(MAX(id),0) FROM billing_on_diskname'));
  state.groupNo = unusedNumber(db, 4, '8', (candidate) => db.value(
    `SELECT COUNT(*) FROM provider WHERE comments LIKE ${sqlString(`%<xml_p_billinggroup_no>${candidate}<%`)}`) !== '0');

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
    overrides.first_name = sqlString('Group');
    overrides.comments = sqlString(`<xml_p_billinggroup_no>${state.groupNo}</xml_p_billinggroup_no>`
      + '<xml_p_specialty_code>00</xml_p_specialty_code>');
    const select = providerColumns.map((column) => (column in overrides ? overrides[column] : `\`${column}\``));
    db.execute(`INSERT INTO provider (${providerColumns.map((c) => `\`${c}\``).join(',')})`
      + ` SELECT ${select.join(',')} FROM provider WHERE provider_no=${sqlString(template)}`);
    state.providers[member] = { providerNo, ohipNo };
    // Put the provider in the operator's sites. With _site_access_privacy the
    // diskette page lists only providers who share a site with the operator,
    // which is how a clinic's own providers are always set up.
    db.execute(`INSERT IGNORE INTO providersite (provider_no, site_id) SELECT DISTINCT ${sqlString(providerNo)}, s.site_id`
      + ` FROM providersite s WHERE s.provider_no=${sqlString(template)} OR s.provider_no IN`
      + ` (SELECT provider_no FROM security WHERE user_name=${sqlString(options.testUser)})`);
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

  const claims = [
    { member: 'ZERO', items: [{ code: options.paidCode, fee: '0.00' }, { code: options.paidCode, fee: '0.00' }] },
    { member: 'PAID', items: [{ code: options.paidCode, fee: Number(paidFee).toFixed(2) }] },
  ];
  for (const claim of claims) {
    const { providerNo, ohipNo } = state.providers[claim.member];
    const total = claim.items.reduce((sum, item) => sum + Number(item.fee), 0).toFixed(2);
    // Optional claim fields are stored as '' (never NULL), exactly as the
    // bill-entry save writes them: the claim-file writer dereferences them.
    db.execute('INSERT INTO billing_on_cheader1 (header_id, transc_id, rec_id, hin, ver, dob, pay_program, payee,'
      + ' ref_num, facilty_num, admission_date, ref_lab_num, man_review, location, demographic_no, provider_no, appointment_no, demographic_name, sex, province, billing_date,'
      + ' billing_time, total, paid, status, comment1, visittype, provider_ohip_no, provider_rma_no, apptProvider_no,'
      + ' creator, clinic)'
      + ` VALUES (0, 'HE', 'H', ${sqlString(hin)}, ${sqlString(ver || '')}, ${sqlString(dob8)}, 'HCP', 'P',`
      + ` '', '', '', '', '', '0000', ${Number(demographicNo)}, ${sqlString(providerNo)}, 0, 'PW3942,Fixture', ${sqlString(sex === 'F' ? '2' : '1')},`
      + ` 'ON', ${sqlString(options.window.serviceDate)}, '09:00:00', ${total}, 0.00, 'O',`
      + ` ${sqlString(`PW3942 ${claim.member}`)}, '00', ${sqlString(ohipNo)}, '', ${sqlString(providerNo)},`
      + ` ${sqlString(providerNo)}, '')`);
    const headerId = db.value(`SELECT MAX(id) FROM billing_on_cheader1 WHERE provider_no=${sqlString(providerNo)}`
      + ` AND comment1=${sqlString(`PW3942 ${claim.member}`)}`);
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

function removeFixture(db, state, diskDir) {
  if (!state) {
    return;
  }
  const failures = [];
  const attempt = (label, fn) => {
    try {
      fn();
    } catch (error) {
      failures.push(`${label}: ${error.message}`);
    }
  };
  const newDisks = db.rows(`SELECT id, ohipfilename FROM billing_on_diskname WHERE id > ${Number(state.diskBaseline)}`);
  for (const [diskId, ohipFile] of newDisks) {
    const htmlFiles = db.rows(`SELECT htmlfilename FROM billing_on_filename WHERE disk_id=${Number(diskId)}`).map((r) => r[0]);
    if (diskDir) {
      for (const name of [ohipFile, ...htmlFiles]) {
        if (name && /^[A-Za-z0-9._-]+$/.test(name)) {
          attempt(`remove disk file ${name}`, () => fs.rmSync(path.join(diskDir, name), { force: true }));
        }
      }
    }
    attempt(`disk ${diskId}`, () => {
      db.execute(`DELETE FROM billing_on_header WHERE disk_id=${Number(diskId)}`);
      db.execute(`DELETE FROM billing_on_filename WHERE disk_id=${Number(diskId)}`);
      db.execute(`DELETE FROM billing_on_diskname WHERE id=${Number(diskId)}`);
    });
  }
  for (const headerId of state.headerIds) {
    attempt(`claim ${headerId}`, () => {
      db.execute(`DELETE FROM billing_on_item WHERE ch1_id=${Number(headerId)}`);
      db.execute(`DELETE FROM billing_on_cheader1 WHERE id=${Number(headerId)}`);
    });
  }
  for (const { providerNo } of Object.values(state.providers)) {
    attempt(`provider ${providerNo}`, () => {
      db.execute(`DELETE FROM providersite WHERE provider_no=${sqlString(providerNo)}`);
      db.execute(`DELETE FROM provider WHERE provider_no=${sqlString(providerNo)}`);
    });
  }
  if (failures.length) {
    throw new Error(`fixture cleanup incomplete: ${failures.join('; ')}`);
  }
}

async function generateAllProvidersDisk(context, config, recorder, window) {
  const page = await context.newPage();
  wireStrictPage(page, 'ohip-disk', recorder);
  await gotoApp(page, config.baseUrl, '/billing/CA/ON/ViewBillingONMRI');
  await assertNotErrorPage(page, 'Generate OHIP diskette page');
  const form = page.locator('form[name="form1"]');
  await form.waitFor({ state: 'visible', timeout: 30000 });
  await form.locator('select[name="providers"]').selectOption('all');
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
  const window = { serviceDate, start: shiftDays(serviceDate, -2), end: shiftDays(serviceDate, 2) };
  const paidCode = (process.env.GROUP_DISK_PAID_CODE || 'A007A').toUpperCase();
  assert(/^[A-Z]\d{3}[A-Z]$/.test(paidCode), `GROUP_DISK_PAID_CODE must look like A007A, got ${paidCode}`);
  const templateProvider = process.env.GROUP_DISK_TEMPLATE_PROVIDER || '999998';
  assert(/^\d{1,6}$/.test(templateProvider), 'GROUP_DISK_TEMPLATE_PROVIDER must be a provider number');
  const demographicNo = process.env.GROUP_DISK_DEMOGRAPHIC_NO || '';
  assert(!demographicNo || /^\d+$/.test(demographicNo), 'GROUP_DISK_DEMOGRAPHIC_NO must be numeric');
  const diskDir = process.env.OHIP_DISK_DIR || '';

  const db = createSqlRunner(config.mysql);
  let state = null;
  const recorder = createRecorder();
  let browser = null;

  const run = async () => {
    state = createFixture(db, {
      templateProvider, window, paidCode, demographicNo, testUser: config.testUser,
    });
    const { ZERO, PAID, EMPTY } = state.providers;

    browser = await launchBrowser(config);
    const context = await newContext(browser, config);
    await login(context, config, recorder);
    const page = await generateAllProvidersDisk(context, config, recorder, window);

    const disks = db.rows(`SELECT id, ohipfilename FROM billing_on_diskname WHERE id > ${state.diskBaseline}`
      + ` AND groupno=${sqlString(state.groupNo)}`);
    assert(disks.length === 1, `expected one new disk for fixture group ${state.groupNo}, found ${disks.length}`);
    const [diskId, ohipFile] = disks[0];

    // The OHIP file, fetched the way the operator downloads it: the link the page renders.
    const link = page.locator(`a[href*="filename=${encodeURIComponent(ohipFile)}"]`).first();
    if (!(await link.count())) {
      // File names and statuses only -- never row content.
      const listed = await page.locator('a[href*="homepath=ohipdownload"]').evaluateAll(
        (anchors) => anchors.map((a) => new URL(a.href).searchParams.get('filename')));
      const statuses = db.rows(`SELECT d.status, f.status FROM billing_on_diskname d JOIN billing_on_filename f`
        + ` ON f.disk_id=d.id WHERE d.id=${Number(diskId)}`).map((r) => r.join('/'));
      assert(false, `the diskette page lists no download link for the new group file ${ohipFile}`
        + ` (page ${new URL(page.url()).pathname}, ${await page.locator('table tbody tr').count()} rows, links: ${listed.slice(0, 10).join(', ') || 'none'}; disk/filename status: ${statuses.join(', ')})`);
    }
    const href = new URL(await link.getAttribute('href'), page.url()).toString();
    const download = await context.request.get(href, { maxRedirects: 0 });
    assert(download.status() === 200, `downloading ${ohipFile} answered HTTP ${download.status()}`);
    const claimFile = (await download.body()).toString('latin1');
    // Records end in CR and usually start with LF, but a group file concatenates
    // member batches directly, so one member's trailer (HEE...\r) runs straight
    // into the next member's HEB: split on either character.
    const records = claimFile.split(/[\r\n]+/).filter(Boolean);
    const batchHeaders = records.filter((line) => line.startsWith('HEB'));
    const claimHeaders = records.filter((line) => line.startsWith('HEH'));
    const claimItems = records.filter((line) => line.startsWith('HET'));

    // Assertions quote record COUNTS and fixture OHIP numbers only: the file
    // carries the fixture patient's HIN and DOB, which never belong in a log.
    assert(batchHeaders.some((line) => line.includes(ZERO.ohipNo)),
      `issue #3942: the $0-total member's batch (OHIP ${ZERO.ohipNo}) is missing from the group disk`);
    assert(batchHeaders.some((line) => line.includes(PAID.ohipNo)),
      `the paid member's batch (OHIP ${PAID.ohipNo}) is missing from the group disk`);
    assert(!batchHeaders.some((line) => line.includes(EMPTY.ohipNo)),
      `the member with no claims (OHIP ${EMPTY.ohipNo}) left an empty batch header in the group disk`);
    assert(batchHeaders.length === 2, `expected 2 batch headers in the group disk, found ${batchHeaders.length}`);
    assert(claimHeaders.length === 2, `expected 2 claim headers in the group disk, found ${claimHeaders.length}`);
    assert(claimItems.length === ZERO.itemCount + PAID.itemCount,
      `expected ${ZERO.itemCount + PAID.itemCount} claim items in the group disk, found ${claimItems.length}`);

    const batchIds = db.rows(`SELECT id FROM billing_on_header WHERE disk_id=${Number(diskId)}`).map((r) => r[0]);
    for (const member of [ZERO, PAID]) {
      const [status, headerId] = db.rows(`SELECT status, header_id FROM billing_on_cheader1 WHERE id=${Number(member.headerId)}`)[0];
      assert(status === 'B', `claim for OHIP ${member.ohipNo} stayed status ${status}; it was not submitted (issue #3942)`);
      assert(batchIds.includes(headerId), `claim for OHIP ${member.ohipNo} was not linked to a batch of disk ${diskId}`);
      const [claimRecord, total] = db.rows(`SELECT claimrecord, total FROM billing_on_filename WHERE disk_id=${Number(diskId)}`
        + ` AND providerno=${sqlString(member.providerNo)}`)[0] || [];
      assert(claimRecord && claimRecord.endsWith(`/${member.itemCount}`),
        `disk summary for OHIP ${member.ohipNo} recorded ${claimRecord}, expected .../${member.itemCount} records`);
      assert(Number(total) === Number(member.total), `disk summary total for OHIP ${member.ohipNo} was ${total}, expected ${member.total}`);
    }
    const emptySummary = db.rows(`SELECT claimrecord FROM billing_on_filename WHERE disk_id=${Number(diskId)}`
      + ` AND providerno=${sqlString(EMPTY.providerNo)}`);
    assert(!emptySummary.length || !emptySummary[0][0],
      `the member with no claims was finalized onto the disk (${emptySummary[0] && emptySummary[0][0]})`);

    assertStrictPage(recorder);
    console.log(`  group ${state.groupNo}: $0 member and paid member submitted, empty member omitted`
      + ` (${batchHeaders.length} batches, ${claimItems.length} items)`);
    return { diskId: Number(diskId) };
  };

  const cleanup = async () => {
    if (browser) {
      await browser.close().catch(() => {});
    }
    removeFixture(db, state, diskDir);
  };

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

module.exports = { shiftDays, isoDate };
