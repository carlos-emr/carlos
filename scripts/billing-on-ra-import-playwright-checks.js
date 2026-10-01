#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

/*
 * Ontario Remittance Advice import, reconciliation reports, settlement and the
 * claims error report upload.
 *
 * User path: Schedule ▸ Administration ▸ Billing ▸ Upload MOH files
 * (BillingONUpload in the #dynamic-content iframe) ▸ choose a synthetic RA file
 * (P…) ▸ Create Report (DocumentUploadServlet → ViewGenRA → ViewOnGenRA);
 * Administration ▸ Billing ▸ Billing Reconciliation (ViewGenRA) ▸ Report
 * (ViewGenRADesc popup), Summary (ViewOnGenRASummary popup ▸ provider ▸ Generate),
 * Error (ViewOnGenRAError popup ▸ provider ▸ Generate), Settle (confirm ▸
 * ViewOnGenRAsettle); Upload MOH files ▸ synthetic claims error report (E…) ▸
 * Create Report (oscarBilling/DocumentErrorReportUpload).
 *
 * Asserts: the import writes one raheader (payable, cheque total, counts, status N)
 * and one radetail per RA item for the owned claims; the reconciliation list,
 * description, summary and error reports show the owned claims and amounts; GET
 * against the settle mutator is refused; the error report writes billing_on_eareport
 * rows for the rejected claim; Settle flips the paid claim to S, leaves the rejected
 * claim billed (B) and marks the RA settled, without a script error.
 *
 * Fixtures: the billing provider/patient fixture of
 * billing-on-ohip-simulation-report plus two submitted (B) claims; the RA payable
 * field and the uploaded file names carry the run marker. Cleanup removes the
 * raheader/radetail/billing_on_premium/billing_on_eareport rows and the uploaded
 * files (DOCUMENT_DIR and the EDT inbox), then the claims and provider. Nothing is
 * sent to MOH/MCEDT.
 *
 * Implements docs/ui-tests/playwright-coverage-plan-2026.08.md §2.7
 * billing-on-ohip-file-cycle (RA and error-report half).
 * Optional RA_DOCUMENT_DIR / RA_EDT_INBOX: the install's DOCUMENT_DIR and ONEDT_INBOX.
 */

const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { createBillingFixture, openAdministration, openAdminFrame } = require('./billing-on-ohip-simulation-report-playwright-checks');

const DEFAULT_DOCUMENT_DIR = '/var/lib/carlos-emr/CarlosDocument/carlos/document';
const DEFAULT_EDT_INBOX = '/var/lib/carlos-emr/CarlosDocument/carlos/onEDTDocs/inbox';
const SERVICE_DATE = '20040512';
const PAYMENT_DATE = '20040615';
const PROCESS_DATE = '20040520';
const RECONCILE_CONFIRM = 'You are about to reconcile the file, are you sure?';

/** Left-justify `value` in a fixed-width field. */
function field(value, width, fill = ' ') {
  const text = String(value);
  h.assert(text.length <= width, `RA field value is wider than ${width}`);
  return text + fill.repeat(width - text.length);
}

function cents(amount, width) {
  return String(Math.round(Number(amount) * 100)).padStart(width, '0');
}

/** A record padded to MOH's 79-character fixed width. */
function record(text) {
  h.assert(text.length <= 79, 'An RA record is longer than 79 characters');
  return field(text, 79);
}

/**
 * A Remittance Advice in the layout BillingOnRaService.importRAFile reads: HR1
 * file header, HR4 claim header + HR5 item per claim, HR6 balance forward, HR7
 * accounting transaction, HR8 message.
 */
function buildRemittance({ owned, marker, claims }) {
  const cheque = claims.reduce((sum, claim) => sum + Number(claim.paid), 0);
  const header = record(`HR1V030${owned.groupNo}${owned.ohipNo}00  ${PAYMENT_DATE}${field(marker, 30)}${cents(cheque, 9)} `);
  h.assert(header.slice(21, 29) === PAYMENT_DATE && header.slice(29, 59).trim() === marker,
    'The synthetic RA header does not match the parser layout');
  const lines = [header];
  claims.forEach((claim, i) => {
    const claimNo = `PW${String(i + 1).padStart(9, '0')}`;
    lines.push(record(`HR4${claimNo}1${owned.ohipNo}00${claim.id.padStart(8, '0')}${field('FAKEPW', 14)}${field('WORK', 5)}ON`
      + `${field(owned.hin, 12)}ZZHCP`));
    lines.push(record(`HR5${claimNo}1${SERVICE_DATE}01${claim.code} ${cents(claim.fee, 6)}${cents(claim.paid, 6)} ${field(claim.error, 2)}`));
  });
  lines.push(record(`HR6${'000000000 '.repeat(4)}`));
  lines.push(record(`HR710C${PAYMENT_DATE}00001000 ${field(`${marker} advance`, 50)}`));
  lines.push(record(`HR8${field(`${marker} remittance message`, 70)}`));
  return { text: `${lines.join('\r\n')}\r\n`, cheque: cheque.toFixed(2) };
}

/** A Claims Error Report (E…) rejecting one owned claim item. */
function buildErrorReport({ owned, claim }) {
  const lines = [
    record(`HX1V03G${' '.repeat(10)}000000${owned.groupNo}${owned.ohipNo}00000${PROCESS_DATE}`),
    record(`HXH${owned.hin}ZZ19800102${claim.id.padStart(8, '0')}HCPP${' '.repeat(29)}VH9${' '.repeat(12)}`),
    record(`HXT${claim.code}  ${cents(claim.fee, 6)}01${SERVICE_DATE}250 ${' '.repeat(34)}A3F${' '.repeat(12)}`),
    record(`HX8A3${field('SYNTHETIC SERVICE CODE REJECTION', 55)}`),
    record(`HX9${'0000001'.repeat(4)}`),
  ];
  // Header indexes are fixed by the parser; prove the builder honours them.
  h.assert(lines[1].slice(23, 31) === claim.id.padStart(8, '0') && lines[1].slice(64, 67) === 'VH9'
    && lines[2].slice(64, 67) === 'A3F', 'The synthetic error report does not match the parser layout');
  return `${lines.join('\r\n')}\r\n`;
}

function directory(env, fallback) {
  const dir = process.env[env] || fallback;
  h.assert(path.isAbsolute(dir) && fs.statSync(dir).isDirectory(), `${env} must name an existing directory`);
  return fs.realpathSync(dir);
}

/** Administration ▸ Billing ▸ Upload MOH files ▸ choose ▸ Create Report. */
async function uploadMohFile(s, admin, name, text, route) {
  const frame = await openAdminFrame(admin, '/billing/CA/ON/BillingONUpload', 'form#form1');
  await frame.locator('input[name="file1"]').setInputFiles({ name, mimeType: 'text/plain', buffer: Buffer.from(text, 'latin1') });
  const [response] = await Promise.all([
    admin.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith(route), { timeout: 60000 }),
    frame.locator('input[type="submit"][value="Create Report"]').click(),
  ]);
  await frame.waitForLoadState('domcontentloaded');
  return { frame, status: response.status() };
}

/** The reconciliation row of the owned RA (its Payable column carries the marker). */
function raRow(frame, marker) {
  return frame.locator('tbody tr').filter({ hasText: marker });
}

async function workflow(s) {
  const { sql, marker } = s;
  const documentDir = directory('RA_DOCUMENT_DIR', DEFAULT_DOCUMENT_DIR);
  const edtInbox = directory('RA_EDT_INBOX', DEFAULT_EDT_INBOX);
  const hex = marker.slice('FAKE-PW'.length);
  const raName = `PPW${hex}.001`;
  const errorName = `EPW${hex.slice(0, 12)}.001`;
  const owned = createBillingFixture(s);
  const raIds = () => sql.rows(`SELECT raheader_no FROM raheader WHERE filename=${h.sqlString(raName)}`).map(row => Number(row[0]));

  // Runs before the claim/provider cleanups registered by the fixture.
  s.cleanup(() => {
    for (const id of raIds()) {
      sql.execute(`DELETE FROM billing_on_premium WHERE raheader_no=${id};
        DELETE FROM radetail WHERE raheader_no=${id};
        DELETE FROM raheader WHERE raheader_no=${id} AND filename=${h.sqlString(raName)}`);
    }
    const claimIds = owned.headerIds.length ? owned.headerIds.map(Number).join(',') : '0';
    sql.execute(`DELETE FROM billing_on_eareport WHERE report_name=${h.sqlString(errorName)} AND billing_no IN (${claimIds})`);
    h.assert(raIds().length === 0 && sql.value(`SELECT COUNT(*) FROM billing_on_eareport
      WHERE report_name=${h.sqlString(errorName)}`) === '0', 'Owned RA or error-report rows were not removed');
    for (const dir of [documentDir, edtInbox]) {
      for (const name of [raName, errorName]) {
        const file = path.join(dir, name);
        if (fs.existsSync(file)) {
          h.assert(fs.lstatSync(file).isFile(), 'Refusing to remove a non-regular uploaded file');
          fs.unlinkSync(file);
        }
        h.assert(!fs.existsSync(file), 'An uploaded MOH file was not removed');
      }
    }
  });

  const fee = { A007A: '34.70', A001A: '21.70' };
  for (const code of Object.keys(fee)) {
    const value = sql.value(`SELECT value FROM billingservice WHERE service_code=${h.sqlString(code)}
      ORDER BY billingservice_date DESC LIMIT 1`);
    if (!/^\d+(\.\d+)?$/.test(value)) throw new h.SkipCheck(`service code ${code} has no fee`);
    fee[code] = Number(value).toFixed(2);
  }
  const isoDate = `${SERVICE_DATE.slice(0, 4)}-${SERVICE_DATE.slice(4, 6)}-${SERVICE_DATE.slice(6)}`;
  const paidClaim = owned.addClaim({ tag: 'PAID', date: isoDate, status: 'B', items: [{ code: 'A007A', fee: fee.A007A }] });
  const rejectedClaim = owned.addClaim({ tag: 'REJECTED', date: isoDate, status: 'B', items: [{ code: 'A001A', fee: fee.A001A }] });
  const raClaims = [
    { id: paidClaim.id, code: 'A007A', fee: fee.A007A, paid: fee.A007A, error: '' },
    { id: rejectedClaim.id, code: 'A001A', fee: fee.A001A, paid: '0.00', error: 'AD' },
  ];
  const remittance = buildRemittance({ owned, marker, claims: raClaims });
  const statuses = () => [paidClaim, rejectedClaim]
    .map(claim => sql.value(`SELECT status FROM billing_on_cheader1 WHERE id=${claim.id}`)).join('|');

  const admin = await openAdministration(s);
  let raNo;

  await s.step('Upload MOH files imports the synthetic RA into one raheader and a radetail per owned item', async () => {
    const { frame, status } = await uploadMohFile(s, admin, raName, remittance.text,
      '/servlet/io.github.carlos_emr.DocumentUploadServlet');
    h.assert(status === 200, `The RA upload answered HTTP ${status}`);
    await raRow(frame, marker).first().waitFor({ state: 'visible', timeout: 30000 });
    h.assert(await frame.locator('.alert-danger', { hasText: 'RA import failed' }).count() === 0, 'The page reports a failed RA import');
    const ids = raIds();
    h.assert(ids.length === 1, 'The upload did not create exactly one raheader for the owned file');
    raNo = ids[0];
    h.assert(sql.value(`SELECT CONCAT_WS('|', TRIM(payable), paymentdate, totalamount, records, claims, status)
      FROM raheader WHERE raheader_no=${raNo}`) === `${marker}|${PAYMENT_DATE}|${remittance.cheque}|2|2|N`,
    'The raheader does not carry the payable, payment date, cheque total, counts and status N');
    const details = sql.rows(`SELECT billing_no, providerohip_no, service_code, service_count, amountclaim, amountpay,
      service_date, error_code, billtype FROM radetail WHERE raheader_no=${raNo} ORDER BY billing_no`);
    h.assert(details.length === 2, 'The import did not write one radetail per RA item');
    for (const claim of raClaims) {
      const row = details.find(r => r[0] === claim.id);
      h.assert(row && row.slice(1).join('|') === [owned.ohipNo, claim.code, '01', claim.fee, claim.paid, SERVICE_DATE,
        claim.error, 'HCP'].join('|'), 'A radetail row does not equal its RA item');
    }
    h.assert(fs.existsSync(path.join(documentDir, raName)), 'The RA file was not stored in DOCUMENT_DIR');
    const cells = (await raRow(frame, marker).first().locator('td').allInnerTexts()).map(cell => cell.trim());
    h.assert(cells[1] === PAYMENT_DATE && cells[3] === '2/2' && cells[4] === remittance.cheque,
      'The reconciliation row does not show the payment date, counts and total');
    h.assert(statuses() === 'B|B', 'Importing the RA changed the claim statuses');
  });

  let frame;
  await s.step('Billing Reconciliation ▸ Error lists only the rejected owned claim with its explanatory code', async () => {
    frame = await openAdminFrame(admin, '/billing/CA/ON/ViewGenRA', 'table');
    const row = raRow(frame, marker);
    h.assert(await row.count() === 1, 'Billing Reconciliation does not list the owned RA exactly once');
    h.assert(await row.locator('a', { hasText: 'Settle' }).count() === 1, 'A new RA does not offer Settle');
    const errors = await s.popup(frame.page(), row.locator('a', { hasText: 'Error' }), 'ra-error');
    await errors.locator('select[name="proNo"]').selectOption(owned.ohipNo);
    await ui.clickAndAwaitReload(errors, errors.locator('input[type="submit"][value="Generate"]'));
    const rows = errors.locator('table.myIvory tr').filter({ hasNot: errors.locator('th') });
    h.assert(await rows.count() === 1, 'The error report does not list exactly the rejected claim');
    const cells = (await rows.first().locator('td').allInnerTexts()).map(cell => cell.trim());
    h.assert(cells[0] === rejectedClaim.id && cells[3] === 'A001A' && cells[7] === 'AD',
      'The error row does not show the rejected claim, its code and the AD explanation');
    await errors.close();
  });

  await s.step('GET against the settle mutator is refused and settles nothing', async () => {
    const probe = await s.context.request.get(h.appUrl(s.config.baseUrl, '/billing/CA/ON/ViewOnGenRAsettle'),
      { params: { rano: String(raNo) }, maxRedirects: 0 });
    h.assert(probe.status() === 405, 'ViewOnGenRAsettle must reject GET with 405');
    h.assert(statuses() === 'B|B' && sql.value(`SELECT status FROM raheader WHERE raheader_no=${raNo}`) === 'N',
      'The refused GET settled a claim or the RA');
  });

  await s.step('Upload MOH files imports the claims error report for the rejected claim and shows it', async () => {
    const { frame: page, status } = await uploadMohFile(s, admin, errorName, buildErrorReport({ owned, claim: raClaims[1] }),
      '/oscarBilling/DocumentErrorReportUpload');
    // The import commits before the report page renders; prove the rows first.
    const rows = sql.rows(`SELECT billing_no, providerohip_no, group_no, code, unit, fee, TRIM(code_error), TRIM(claim_error),
      status FROM billing_on_eareport WHERE report_name=${h.sqlString(errorName)}`);
    h.assert(rows.length === 1, 'The error report did not write exactly one billing_on_eareport row');
    const [billingNo, ohipNo, groupNo, code, unit, rowFee, codeError, claimError, rowStatus] = rows[0];
    h.assert(billingNo === rejectedClaim.id && ohipNo === owned.ohipNo && groupNo === owned.groupNo && code === 'A001A'
      && unit === '01' && Number(rowFee) === Number(fee.A001A) && codeError.startsWith('A3F') && claimError.startsWith('VH9')
      && rowStatus === 'N', 'The billing_on_eareport row does not equal the uploaded report');
    h.assert(fs.existsSync(path.join(documentDir, errorName)), 'The error report was not stored in DOCUMENT_DIR');
    h.assert(statuses() === 'B|B', 'Importing the error report changed the claim statuses');
    h.assert(status === 200, `The claims error report page answered HTTP ${status}`);
    const text = (await page.locator('body').innerText()).replace(/\s+/g, ' ');
    h.assert(text.includes('Claims Error Report') && text.includes(`Provider #: ${owned.ohipNo}`),
      'The error report page does not show the report and the owned provider');
    h.assert(text.includes(rejectedClaim.id.padStart(8, '0')) && text.includes('A001A'),
      'The error report page does not show the rejected invoice and its code');
  });

  await s.step('Billing Reconciliation ▸ Report shows the cheque, balance forward, transaction and message', async () => {
    frame = await openAdminFrame(admin, '/billing/CA/ON/ViewGenRA', 'table');
    const row = raRow(frame, marker);
    const report = await s.popup(frame.page(), row.locator('a', { hasText: 'Report' }), 'ra-description');
    await report.locator('body').waitFor();
    const text = (await report.locator('body').innerText()).replace(/\s+/g, ' ');
    h.assert(text.includes(`Cheque amount: ${remittance.cheque}`), 'The RA description does not show the cheque amount');
    h.assert(text.includes('Accounting Transaction Record') && text.includes(`${marker} advance`) && text.includes('10.00'),
      'The RA description does not show the accounting transaction');
    h.assert(text.includes(`${marker} remittance message`), 'The RA description does not show the HR8 message');
    h.assert(sql.value(`SELECT CONCAT_WS('|', totalamount, records, claims, status) FROM raheader WHERE raheader_no=${raNo}`)
      === `${remittance.cheque}|2|2|N`, 'Viewing the description changed the RA totals or status');
    h.assert(sql.value(`SELECT COUNT(*) FROM billing_on_premium WHERE raheader_no=${raNo}`) === '0',
      'A remittance without premium messages recorded premium payments');
    await report.close();
  });

  await s.step('Summary lists both owned claims with invoiced and paid amounts and stores the totals', async () => {
    const row = raRow(frame, marker);
    const summary = await s.popup(frame.page(), row.locator('a', { hasText: 'Summary' }), 'ra-summary');
    await summary.locator('select[name="proNo"]').waitFor();
    await summary.locator('select[name="proNo"]').selectOption(owned.ohipNo);
    await ui.clickAndAwaitReload(summary, summary.locator('input[type="submit"][value="Generate"]'));
    await summary.locator('#ra_table').waitFor();
    for (const claim of raClaims) {
      const cells = (await summary.locator('#ra_table tbody tr').filter({ hasText: claim.code }).first()
        .locator('td').allInnerTexts()).map(cell => cell.trim());
      h.assert(cells[0] === claim.id && cells[6] === claim.code && Number(cells[7]) === Number(claim.fee)
        && Number(cells[8]) === Number(claim.paid) && cells[12] === claim.error,
      'A summary row does not show the claim, code, invoiced and paid amounts and error');
    }
    h.assert((await summary.locator('#amountPay').innerText()).trim() === remittance.cheque, 'The summary paid total is wrong');
    h.assert(sql.value(`SELECT content FROM raheader WHERE raheader_no=${raNo}`).includes(`<xml_total>${remittance.cheque}</xml_total>`),
      'The summary did not store the paid total on the raheader');
    await summary.close();
  });

  await s.step('Settle reconciles the RA: the paid claim settles, the rejected claim stays billed', async () => {
    frame = await openAdminFrame(admin, '/billing/CA/ON/ViewGenRA', 'table');
    const row = raRow(frame, marker);
    let response;
    const dialogs = await h.withExpectedDialogs(frame.page(), async () => {
      [response] = await Promise.all([
        admin.waitForResponse(r => r.request().method() === 'POST'
          && new URL(r.url()).pathname.endsWith('/billing/CA/ON/ViewOnGenRAsettle'), { timeout: 30000 }),
        row.locator('a', { hasText: 'Settle' }).click(),
      ]);
    });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm' && dialogs[0].text === RECONCILE_CONFIRM,
      'Settle must ask the reconcile confirmation exactly once');
    h.assert(response.status() === 200, `Settle answered HTTP ${response.status()}`);
    await frame.waitForLoadState('load').catch(() => {});
    h.assert(statuses() === 'S|B', 'Settle did not settle exactly the paid claim');
    h.assert(sql.value(`SELECT status FROM raheader WHERE raheader_no=${raNo}`) === 'S', 'Settle did not mark the RA settled');
    frame = await openAdminFrame(admin, '/billing/CA/ON/ViewGenRA', 'table');
    const settled = raRow(frame, marker);
    h.assert(await settled.locator('a', { hasText: 'Settle' }).count() === 0
      && await settled.locator('a', { hasText: 'S35' }).count() === 1, 'The settled RA still offers Settle');
  });
}

if (require.main === module) runWorkflow('billing-on-ra-import', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow, buildRemittance, buildErrorReport };
