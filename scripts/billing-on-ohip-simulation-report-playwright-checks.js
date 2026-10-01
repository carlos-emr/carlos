#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

/*
 * Ontario OHIP simulation and claim-file generation, entered from Administration.
 *
 * User path: Schedule ▸ Administration ▸ Billing ▸ Simulation OHIP Diskette
 * (ViewBillingOHIPsimulation, AJAX panel) ▸ provider + window ▸ Create Report,
 * then Summary View; Administration ▸ Billing ▸ Generate OHIP Diskette
 * (ViewBillingOHIPreport → ViewBillingONMRI in the #dynamic-content iframe) ▸
 * provider + window ▸ Create Report (ViewOngenreport) ▸ download the OHIP file.
 *
 * Asserts: the simulation lists exactly the owned in-window open claims (codes,
 * fees, record count, total, Pass) and writes nothing; GET against the generator
 * is refused with 405; generation bills only the in-window claims, records one
 * disk for the owned provider, and the downloaded file parses into HEB/HEH/HET/HEE
 * records whose fields equal the seeded claims and the persisted summary; the
 * simulation re-run then lists none of the billed claims.
 *
 * Fixtures: a throwaway provider (last name = run marker, own OHIP and group
 * numbers, in the operator's sites) cloned from the test login, the owned FAKE-
 * patient given a synthetic 10-digit HIN, and four claims (two in-window open,
 * one deleted, one outside the window). Cleanup removes the disk rows, output
 * files, claims, provider-site rows and the provider, and asserts they are gone.
 * No file is uploaded to MOH/MCEDT. View MOH files (moveMOHFiles) is not reachable
 * on an install with moh_file_management_enabled=false, so it is not covered.
 *
 * Implements docs/ui-tests/playwright-coverage-plan-2026.08.md §2.7
 * billing-on-ohip-file-cycle (simulation and generation half).
 * Optional OHIP_DISK_DIR: the local HOME_DIR (default: the packaged install's).
 */

const fs = require('node:fs');
const path = require('node:path');
const { randomBytes } = require('node:crypto');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow } = require('./lib/workflow-session');
const { checkedDiskDirectory, removeOwnedFiles } = require('./billing-on-group-disk-zero-total-playwright-checks');

const DEFAULT_DISK_DIR = '/var/lib/carlos-emr/CarlosDocument/carlos/billing/download';
const SERVICE_DATE = '2004-03-10';
const WINDOW = { start: '2004-03-08', end: '2004-03-12' };
const OUTSIDE_DATE = '2004-04-20';

function columnsOf(sql, table) {
  return sql.rows(`SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name=${h.sqlString(table)} ORDER BY ordinal_position`).map(row => row[0]);
}

function unusedNumber(prefix, digits, taken) {
  for (let attempt = 0; attempt < 50; attempt += 1) {
    const tail = String(randomBytes(4).readUInt32BE(0) % (10 ** (digits - prefix.length)))
      .padStart(digits - prefix.length, '0');
    if (!taken(prefix + tail)) return prefix + tail;
  }
  throw new Error(`no unused ${digits}-digit number with prefix ${prefix}`);
}

function scheduleFee(sql, code) {
  const fee = sql.value(`SELECT value FROM billingservice WHERE service_code=${h.sqlString(code)}
    ORDER BY billingservice_date DESC LIMIT 1`);
  if (!/^\d+(\.\d+)?$/.test(fee) || Number(fee) <= 0) throw new h.SkipCheck(`service code ${code} has no positive fee`);
  return Number(fee).toFixed(2);
}

/**
 * A billable provider and patient owned by this run. The provider is a clone of
 * the test login with its own OHIP/group numbers, placed in the operator's sites
 * (the billing pages list only site-sharing providers under _site_access_privacy).
 * Cleanup is registered before the first INSERT and removes the provider last.
 */
function createBillingFixture(s) {
  const { sql, marker, patient, provider } = s;
  const owned = { providerNo: '', ohipNo: '', groupNo: '', headerIds: [] };
  s.cleanup(() => {
    if (!owned.providerNo) return;
    const quoted = h.sqlString(owned.providerNo);
    if (sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${quoted} AND last_name=${h.sqlString(marker)}`) !== '1') return;
    h.assert(sql.value(`SELECT COUNT(*) FROM billing_on_cheader1 WHERE provider_no=${quoted}`) === '0',
      'The owned provider still has claims; provider cleanup stopped');
    sql.execute(`DELETE FROM providersite WHERE provider_no=${quoted};
      DELETE FROM provider WHERE provider_no=${quoted} AND last_name=${h.sqlString(marker)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM provider WHERE provider_no=${quoted})
      + (SELECT COUNT(*) FROM providersite WHERE provider_no=${quoted})`) === '0', 'The owned provider was not removed');
  });
  s.cleanup(() => {
    if (!owned.providerNo) return;
    const ids = new Set(sql.rows(`SELECT id FROM billing_on_cheader1 WHERE provider_no=${h.sqlString(owned.providerNo)}
      AND comment1 LIKE ${h.sqlString(`${marker}%`)}`).map(row => row[0]));
    for (const id of owned.headerIds) ids.add(id);
    for (const id of ids) {
      h.assert(/^[1-9]\d*$/.test(id), 'Owned claim id is invalid');
      const items = sql.rows(`SELECT id FROM billing_on_item WHERE ch1_id=${id}`).map(row => Number(row[0]));
      const itemIds = items.length ? items.join(',') : '0';
      sql.execute(`DELETE FROM billing_on_repo WHERE (category='billing_on_item' AND h_id IN (${itemIds}))
          OR (category='billing_on_cheader1' AND h_id=${id});
        DELETE FROM billing_on_proc WHERE object=${h.sqlString(id)};
        DELETE FROM billing_on_ext WHERE billing_no=${id};
        DELETE FROM billing_on_item WHERE ch1_id=${id};
        DELETE FROM billing_on_cheader1 WHERE id=${id} AND provider_no=${h.sqlString(owned.providerNo)}`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM billing_on_cheader1 WHERE id=${id})
        + (SELECT COUNT(*) FROM billing_on_item WHERE ch1_id=${id})`) === '0', 'Owned claim rows were not removed');
    }
  });

  owned.groupNo = unusedNumber('8', 4, candidate => sql.value(`SELECT
    (SELECT COUNT(*) FROM provider WHERE comments LIKE ${h.sqlString(`%<xml_p_billinggroup_no>${candidate}<%`)})
    + (SELECT COUNT(*) FROM billing_on_diskname WHERE groupno=${h.sqlString(candidate)})`) !== '0');
  owned.ohipNo = unusedNumber('96', 6, candidate => sql.value(`SELECT
    (SELECT COUNT(*) FROM provider WHERE ohip_no=${h.sqlString(candidate)})
    + (SELECT COUNT(*) FROM radetail WHERE providerohip_no=${h.sqlString(candidate)})`) !== '0');
  const providerNo = unusedNumber('97', 6, candidate =>
    sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${h.sqlString(candidate)}`) !== '0');
  const overrides = {
    provider_no: h.sqlString(providerNo), ohip_no: h.sqlString(owned.ohipNo), last_name: h.sqlString(marker),
    first_name: "'Billing'", status: "'1'",
    comments: h.sqlString(`<xml_p_billinggroup_no>${owned.groupNo}</xml_p_billinggroup_no>`
      + '<xml_p_specialty_code>00</xml_p_specialty_code>'),
  };
  const columns = columnsOf(sql, 'provider');
  owned.providerNo = providerNo; // Record intent before the INSERT can fail.
  sql.execute(`INSERT INTO provider (${columns.map(c => `\`${c}\``).join(',')})
    SELECT ${columns.map(c => overrides[c] || `\`${c}\``).join(',')} FROM provider WHERE provider_no=${h.sqlString(provider)};
    INSERT IGNORE INTO providersite (provider_no, site_id) SELECT ${h.sqlString(providerNo)}, site_id
      FROM providersite WHERE provider_no=${h.sqlString(provider)}`);
  h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${h.sqlString(providerNo)}`) === '1',
    'The owned billing provider was not created');

  // A synthetic HIN on the owned patient: the claim file reads it from the chart.
  owned.hin = `9${String(randomBytes(4).readUInt32BE(0) % 1e9).padStart(9, '0')}`;
  sql.execute(`UPDATE demographic SET hin=${h.sqlString(owned.hin)}, ver='ZZ', hc_type='ON'
    WHERE demographic_no=${patient} AND last_name=${h.sqlString(marker)}`);
  h.assert(sql.value(`SELECT hin FROM demographic WHERE demographic_no=${patient}`) === owned.hin,
    'The owned patient did not take the synthetic HIN');

  /** Seed one claim header and its items; returns the header id. */
  owned.addClaim = ({ tag, date, status, items }) => {
    const total = items.reduce((sum, item) => sum + Number(item.fee), 0).toFixed(2);
    const id = sql.value(`INSERT INTO billing_on_cheader1 (header_id, transc_id, rec_id, hin, ver, dob, pay_program,
        payee, ref_num, facilty_num, admission_date, ref_lab_num, man_review, location, demographic_no, provider_no,
        appointment_no, demographic_name, sex, province, billing_date, billing_time, total, paid, status, comment1,
        visittype, provider_ohip_no, provider_rma_no, apptProvider_no, asstProvider_no, creator, clinic)
      VALUES (0, 'HE', 'H', ${h.sqlString(owned.hin)}, 'ZZ', '19800102', 'HCP', 'P', '', '', '', '', '', '', ${patient},
        ${h.sqlString(providerNo)}, 0, ${h.sqlString(`${marker},Workflow`)}, '2', 'ON', ${h.sqlString(date)}, '09:00:00',
        ${total}, 0.00, ${h.sqlString(status)}, ${h.sqlString(`${marker} ${tag}`)}, '00', ${h.sqlString(owned.ohipNo)},
        '', '', '', ${h.sqlString(provider)}, NULL); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), `The ${tag} claim was not created`);
    owned.headerIds.push(id);
    sql.execute(items.map(item => `INSERT INTO billing_on_item (ch1_id, transc_id, rec_id, service_code, fee, ser_num,
      service_date, dx, dx1, dx2, status) VALUES (${id}, 'HE', 'T', ${h.sqlString(item.code)}, ${h.sqlString(item.fee)},
      '1', ${h.sqlString(date)}, '250', '', '', ${h.sqlString(status)})`).join(';'));
    return { id, total, items, date };
  };
  return owned;
}

/** Schedule ▸ Administration: the shell that hosts every billing admin page. */
async function openAdministration(s) {
  const { page } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'billing-administration', timeout: 20000 });
  return page;
}

/** Administration ▸ <xlink>: the page the menu hosts in the #dynamic-content iframe. */
async function openAdminFrame(admin, route, ready) {
  const link = admin.locator(`a.xlink[rel$="${route}"]`).first();
  await link.waitFor({ state: 'attached', timeout: 20000 });
  await revealAuditLink(admin, link, 20000);
  await link.click();
  const iframe = admin.locator('#dynamic-content iframe').first();
  await iframe.waitFor({ timeout: 20000 });
  const frame = await (await iframe.elementHandle()).contentFrame();
  h.assert(frame, `${route} did not load in the administration frame`);
  await frame.locator(ready).first().waitFor({ state: 'visible', timeout: 20000 });
  return frame;
}

/** Type an ISO date into a flatpickr (allowInput) field and close its calendar. */
async function fillDate(scope, page, selector, value) {
  await scope.locator(selector).fill(value);
  // Click the page heading, as an operator would, so the open calendar closes.
  await scope.locator('xpath=ancestor-or-self::*[.//h3][1]').locator('h3').first().click();
  await page.locator('.flatpickr-calendar.open').first().waitFor({ state: 'hidden', timeout: 10000 }).catch(() => {});
  for (const frame of page.frames()) {
    await frame.locator('.flatpickr-calendar.open').first().waitFor({ state: 'hidden', timeout: 10000 }).catch(() => {});
  }
  h.assert(await scope.locator(selector).inputValue() === value, `${selector} did not keep ${value}`);
}

/** Simulation panel ▸ Create Report: the AJAX submit that repaints #dynamic-content. */
async function runSimulation(s, admin, owned, { summary = false } = {}) {
  const link = admin.locator('a.contentLink[href*="/billing/CA/ON/ViewBillingOHIPsimulation"]').first();
  await link.waitFor({ state: 'attached', timeout: 20000 });
  await revealAuditLink(admin, link, 20000);
  const panel = await ui.clickInjectsPanel(admin, link, { marker: '#dynamic-content form#serviceform' });
  const form = panel.locator('form#serviceform');
  const offered = await form.locator('select[name="providers"] option').evaluateAll(options => options.map(o => o.value));
  h.assert(offered.includes(owned.providerNo), 'The simulation page does not offer the owned billing provider');
  await form.locator('select[name="providers"]').selectOption(owned.providerNo);
  await fillDate(form, admin, '#xml_vdate', WINDOW.start);
  await fillDate(form, admin, '#xml_appointment_date', WINDOW.end);
  if (summary) await form.locator('#summaryView').check();
  const [response] = await Promise.all([
    admin.waitForResponse(r => new URL(r.url()).pathname.endsWith('/billing/CA/ON/ViewBillingOHIPsimulation')
      && new URL(r.url()).searchParams.get('submit') === 'Create Report', { timeout: 60000 }),
    form.locator('button[type="submit"][value="Create Report"]').click(),
  ]);
  h.assert(response.status() === 200, `The simulation answered HTTP ${response.status()}`);
  await admin.locator('#dynamic-content td', { hasText: 'RECORDS PROCESSED' }).first().waitFor({ timeout: 30000 });
  return admin.locator('#dynamic-content');
}

function billLink(scope, id) {
  return scope.locator(`a[onclick*="BillingONCorrection?billing_no=${id}'"], a[onclick*="BillingONCorrection?billing_no=${id}\\""]`);
}

/** Split a downloaded OHIP claim file into its fixed-width records. */
function parseClaimFile(text) {
  h.assert(/\r\n/.test(text), 'The OHIP file does not use CR LF record separators');
  const lines = text.split(/\r?\n/).map(line => line.replace(/\r$/, '')).filter(Boolean);
  for (const line of lines) h.assert(line.length === 79, `An OHIP record is ${line.length} characters, not 79`);
  return lines.map(line => {
    const type = line.slice(0, 3);
    if (type === 'HEB') {
      return { type, spec: line.slice(3, 6), mohOffice: line.slice(6, 7), batchId: line.slice(7, 19),
        groupNo: line.slice(25, 29), ohipNo: line.slice(29, 35), specialty: line.slice(35, 37) };
    }
    if (type === 'HEH') {
      return { type, hin: line.slice(3, 13), ver: line.slice(13, 15), dob: line.slice(15, 23),
        account: line.slice(23, 31), payProgram: line.slice(31, 34), payee: line.slice(34, 35) };
    }
    if (type === 'HET') {
      return { type, code: line.slice(3, 8), feeCents: line.slice(10, 16), units: line.slice(16, 18),
        date: line.slice(18, 26), dx: line.slice(26, 30).trim() };
    }
    if (type === 'HEE') {
      return { type, claims: Number(line.slice(3, 7)), reciprocal: Number(line.slice(7, 11)), items: Number(line.slice(11, 16)) };
    }
    return { type };
  });
}

function ownedDisks(sql, owned) {
  if (!owned.providerNo || !owned.groupNo) return [];
  const p = h.sqlString(owned.providerNo);
  return sql.rows(`SELECT d.id, d.ohipfilename FROM billing_on_diskname d WHERE d.groupno=${h.sqlString(owned.groupNo)}
    AND EXISTS (SELECT 1 FROM billing_on_filename f WHERE f.disk_id=d.id AND f.providerno=${p})
    AND NOT EXISTS (SELECT 1 FROM billing_on_filename f WHERE f.disk_id=d.id AND (f.providerno IS NULL OR f.providerno<>${p}))`);
}

async function workflow(s) {
  const { sql } = s;
  const diskDir = checkedDiskDirectory(process.env.OHIP_DISK_DIR || DEFAULT_DISK_DIR);
  const owned = createBillingFixture(s);
  // Registered after the claim cleanup, so it runs first: disk rows reference the claims.
  s.cleanup(() => {
    for (const [diskId, ohipFile] of ownedDisks(sql, owned)) {
      const htmlFiles = sql.rows(`SELECT htmlfilename FROM billing_on_filename WHERE disk_id=${Number(diskId)}`).map(r => r[0]);
      removeOwnedFiles(diskDir, [ohipFile, ...htmlFiles].filter(Boolean));
      sql.execute(`START TRANSACTION;
        DELETE r FROM billing_on_repo r JOIN billing_on_header b ON b.id=r.h_id
          WHERE r.category='billing_on_header' AND b.disk_id=${Number(diskId)};
        DELETE FROM billing_on_header WHERE disk_id=${Number(diskId)};
        DELETE FROM billing_on_filename WHERE disk_id=${Number(diskId)};
        DELETE FROM billing_on_diskname WHERE id=${Number(diskId)}; COMMIT`);
      h.assert(sql.value(`SELECT COUNT(*) FROM billing_on_diskname WHERE id=${Number(diskId)}`) === '0', 'Owned disk was not removed');
      h.assert(!fs.existsSync(path.join(diskDir, ohipFile)), 'The owned OHIP file was not removed');
    }
  });

  const fees = { A007A: scheduleFee(sql, 'A007A'), A001A: scheduleFee(sql, 'A001A') };
  const first = owned.addClaim({ tag: 'FIRST', date: SERVICE_DATE, status: 'O',
    items: [{ code: 'A007A', fee: fees.A007A }, { code: 'A001A', fee: fees.A001A }] });
  const second = owned.addClaim({ tag: 'SECOND', date: SERVICE_DATE, status: 'O', items: [{ code: 'A001A', fee: fees.A001A }] });
  const deleted = owned.addClaim({ tag: 'DELETED', date: SERVICE_DATE, status: 'D', items: [{ code: 'A007A', fee: fees.A007A }] });
  const outside = owned.addClaim({ tag: 'OUTSIDE', date: OUTSIDE_DATE, status: 'O', items: [{ code: 'A007A', fee: fees.A007A }] });
  const inWindow = [first, second];
  const itemCount = inWindow.reduce((n, claim) => n + claim.items.length, 0);
  const total = inWindow.reduce((sum, claim) => sum + Number(claim.total), 0).toFixed(2);
  const statuses = () => [first, second, deleted, outside]
    .map(claim => sql.value(`SELECT status FROM billing_on_cheader1 WHERE id=${claim.id}`)).join('|');
  const disks = () => sql.value(`SELECT COUNT(*) FROM billing_on_diskname WHERE groupno=${h.sqlString(owned.groupNo)}`);

  const admin = await openAdministration(s);

  await s.step('Simulation OHIP Diskette previews exactly the owned in-window claims and writes nothing', async () => {
    const panel = await runSimulation(s, admin, owned);
    for (const claim of inWindow) {
      h.assert(await billLink(panel, claim.id).count() === 1, 'The simulation did not list an owned in-window claim');
    }
    h.assert(await billLink(panel, deleted.id).count() === 0, 'The simulation listed a deleted claim');
    h.assert(await billLink(panel, outside.id).count() === 0, 'The simulation listed a claim outside the window');
    const text = (await panel.innerText()).replace(/\s+/g, ' ');
    h.assert(text.includes(`${itemCount} RECORDS PROCESSED, 0 ERROR`), 'The simulation footer does not count the owned items with no errors');
    h.assert(text.includes(`TOTAL: ${total}`), 'The simulation total is not the sum of the owned claims');
    h.assert(/\bPass\b/.test(text), 'The simulation did not report Pass for valid claims');
    const firstRow = panel.locator('tr').filter({ has: billLink(admin, first.id) });
    const cells = (await firstRow.locator('td').allInnerTexts()).map(cell => cell.trim());
    h.assert(cells[0] === owned.ohipNo && cells.includes('A007A') && cells.includes(fees.A007A) && cells.includes('250'),
      'The first claim row does not show the provider OHIP no, code, fee and dx');
    h.assert(statuses() === 'O|O|D|O' && disks() === '0', 'The simulation billed a claim or allocated a disk');
  });

  await s.step('Summary View collapses the owned provider to one row with its item count and total', async () => {
    const panel = await runSimulation(s, admin, owned, { summary: true });
    const row = panel.locator('tr').filter({ has: panel.locator('button[id^="recordShowButton"]') });
    h.assert(await row.count() === 1, 'Summary View did not render exactly one provider row');
    const cells = (await row.locator('td').allInnerTexts()).map(cell => cell.trim());
    h.assert(cells[0] === owned.ohipNo && cells[1] === String(itemCount) && Number(cells[2]) === Number(total),
      'The summary row does not carry the provider OHIP no, item count and total');
    h.assert(!(await billLink(panel, first.id).first().isVisible()), 'Summary View shows record details before they are requested');
    h.assert(statuses() === 'O|O|D|O' && disks() === '0', 'The summary simulation billed a claim or allocated a disk');
  });

  let frame;
  await s.step('GET against the OHIP generator is refused with 405 and bills nothing', async () => {
    frame = await openAdminFrame(admin, '/billing/CA/ON/ViewBillingOHIPreport', 'form[name="form1"]');
    const probe = await s.context.request.get(h.appUrl(s.config.baseUrl, '/billing/CA/ON/ViewOngenreport'), {
      params: { providers: owned.providerNo, xml_vdate: WINDOW.start, xml_appointment_date: WINDOW.end, Submit: 'Create Report' },
      maxRedirects: 0,
    });
    h.assert(probe.status() === 405 && probe.headers().allow === 'POST', 'ViewOngenreport must reject GET with Allow: POST');
    h.assert(statuses() === 'O|O|D|O' && disks() === '0', 'The refused GET billed a claim or allocated a disk');
  });

  let ohipFile;
  await s.step('Generate OHIP Diskette bills only the in-window claims and records one disk for the provider', async () => {
    const form = frame.locator('form[name="form1"]');
    const offered = await form.locator('select[name="providers"] option').evaluateAll(options => options.map(o => o.value));
    h.assert(offered.includes(owned.providerNo), 'The diskette page does not offer the owned billing provider');
    for (let attempt = 0; ; attempt += 1) {
      await form.locator('select[name="providers"]').selectOption(owned.providerNo);
      await fillDate(form, admin, '#xml_vdate', WINDOW.start);
      await fillDate(form, admin, '#xml_appointment_date', WINDOW.end);
      if (await form.locator('#useProviderMOH').isChecked()) await form.locator('#useProviderMOH').uncheck();
      const [response] = await Promise.all([
        admin.waitForResponse(r => r.request().method() === 'POST'
          && new URL(r.url()).pathname.endsWith('/billing/CA/ON/ViewOngenreport'), { timeout: 120000 }),
        form.locator('input[type="submit"][name="Submit"]').click(),
      ]);
      h.assert(response.status() === 200, `Create Report answered HTTP ${response.status()}`);
      await frame.waitForLoadState('domcontentloaded');
      // Another check's export may hold the application's disk lock for a moment.
      if (attempt === 0 && (await frame.locator('body').innerText()).includes('already in progress')) {
        await frame.waitForTimeout(5000);
        frame = await openAdminFrame(admin, '/billing/CA/ON/ViewBillingOHIPreport', 'form[name="form1"]');
        continue;
      }
      break;
    }
    await frame.locator('form[name="form1"]').waitFor({ state: 'visible', timeout: 30000 });
    h.assert(statuses() === 'B|B|D|O', 'Generation did not bill exactly the in-window open claims');
    const rows = ownedDisks(sql, owned);
    h.assert(rows.length === 1, 'Generation did not record exactly one disk for the owned provider');
    const [diskId] = rows[0];
    ohipFile = rows[0][1];
    const [claimRecord, fileTotal] = sql.rows(`SELECT claimrecord, total FROM billing_on_filename WHERE disk_id=${Number(diskId)}
      AND providerno=${h.sqlString(owned.providerNo)}`)[0];
    h.assert(claimRecord === `${inWindow.length}/${itemCount}` && Number(fileTotal) === Number(total),
      'The persisted disk summary does not equal the billed claims and items');
    for (const claim of inWindow) {
      h.assert(sql.value(`SELECT COUNT(*) FROM billing_on_header b JOIN billing_on_cheader1 c ON c.header_id=b.id
        WHERE c.id=${claim.id} AND b.disk_id=${Number(diskId)}`) === '1', 'A billed claim is not linked to the new disk batch');
    }
    h.assert(await frame.locator(`a[href*="filename=${encodeURIComponent(ohipFile)}"]`).count() >= 1,
      'The diskette page does not list the generated OHIP file');
  });

  await s.step('the downloaded OHIP file parses into HEB/HEH/HET/HEE records equal to the owned claims', async () => {
    const outcome = await ui.clickDownloadsOrOpens(frame, frame.locator(`a[href*="filename=${encodeURIComponent(ohipFile)}"]`).first(),
      { context: s.context, recorder: s.recorder, label: 'ohip-file', timeout: 30000 });
    let bytes;
    if (outcome.kind === 'download') bytes = fs.readFileSync(await outcome.download.path());
    else {
      const response = await s.context.request.get(outcome.url, { maxRedirects: 0 });
      h.assert(response.status() === 200, `The OHIP download answered HTTP ${response.status()}`);
      bytes = await response.body();
      await outcome.page.close();
    }
    h.assert(bytes.equals(fs.readFileSync(path.join(diskDir, ohipFile))), 'The download differs from the file the generator wrote');
    const records = parseClaimFile(bytes.toString('latin1'));
    const billCenter = await frame.locator('#billcenter').inputValue();
    h.assert(records[0].type === 'HEB' && records.filter(r => r.type === 'HEB').length === 1, 'The file does not start with one HEB batch header');
    h.assert(records[0].spec === 'V03' && records[0].mohOffice === billCenter && records[0].groupNo === owned.groupNo
      && records[0].ohipNo === owned.ohipNo && records[0].specialty === '00',
    'The HEB batch header does not carry V03, the bill centre, group, OHIP number and specialty');
    h.assert(records.at(-1).type === 'HEE' && records.at(-1).claims === inWindow.length
      && records.at(-1).reciprocal === 0 && records.at(-1).items === itemCount, 'The HEE trailer counts are wrong');
    const claims = [];
    for (const record of records.slice(1, -1)) {
      if (record.type === 'HEH') claims.push({ header: record, items: [] });
      else if (record.type === 'HET') claims.at(-1).items.push(record);
      else h.assert(false, `Unexpected ${record.type} record between the batch header and trailer`);
    }
    h.assert(claims.length === inWindow.length, 'The file does not hold exactly the in-window claims');
    for (const claim of inWindow) {
      const found = claims.find(c => c.header.account === claim.id.padStart(8, '0'));
      h.assert(found, 'An owned claim is missing from the OHIP file');
      h.assert(found.header.hin === owned.hin && found.header.ver === 'ZZ' && found.header.dob === '19800102'
        && found.header.payProgram === 'HCP' && found.header.payee === 'P', 'An HEH record does not carry the patient and payment fields');
      h.assert(found.items.length === claim.items.length, 'An HEH record has the wrong number of HET items');
      claim.items.forEach((item, i) => {
        const het = found.items[i];
        h.assert(het.code === item.code && Number(het.feeCents) === Math.round(Number(item.fee) * 100) && het.units === '01'
          && het.date === SERVICE_DATE.replace(/-/g, '') && het.dx === '250', 'An HET record does not equal its seeded item');
      });
    }
  });

  await s.step('a re-run simulation no longer lists the billed claims', async () => {
    const panel = await runSimulation(s, admin, owned);
    for (const claim of [first, second, deleted, outside]) {
      h.assert(await billLink(panel, claim.id).count() === 0, 'The simulation still lists a claim that is billed or out of scope');
    }
    h.assert((await panel.innerText()).replace(/\s+/g, ' ').includes('0 RECORDS PROCESSED'), 'The re-run simulation still counts records');
    h.assert(statuses() === 'B|B|D|O' && ownedDisks(sql, owned).length === 1, 'The re-run simulation changed the billed state');
  });
}

if (require.main === module) runWorkflow('billing-on-ohip-simulation-report', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow, createBillingFixture, openAdministration, openAdminFrame, unusedNumber };
