#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * BC MSP remittance: upload a remittance file, reconcile it, and read its Detail and Summary
 * (NEW_BC_TELEPLAN=no, where Upload Remittance Files is offered; under the packaged default the
 * remittance arrives only through ManageTeleplan's Get Remittance, which is live MSP and never used
 * here). billing-bc-settle-paid-claims covers Settle Paid Claims; nothing drove the upload.
 * User path: Schedule ▸ Administration ▸ Billing ▸ Upload Remittance Files (billing/CA/BC/ViewBillingTA,
 * in the #dynamic-content iframe) ▸ choose the file ▸ Submit (the upload servlet stores it in
 * DOCUMENT_DIR, genTA.jsp parses it, and the reconciliation list opens) ▸ the remittance's Detail
 * (ViewGenTAS00) and Summary (ViewGenTAS22). Then Administration ▸ Billing ▸ MSP Reconciliation
 * Reports (ProcessRemittance), which every BC install offers.
 * The FAKE remittance: an S21 header for the owned physician's payee number, an S01 paid line for the
 * first owned claim, an S03 refusal (explanation AA) for the second, and an S22 practitioner summary,
 * each fixed width as genTA.jsp reads it; the file name carries the run marker.
 * Asserted: Submit opens the reconciliation list, which lists the remittance; the list shows its payee and its
 * billed, paid, balance-forward, cheque and new-balance amounts in dollars; the header, both claim
 * lines and the summary are stored once; the paid claim is settled (S); Detail lists the refused claim's
 * line with its explanation and nothing paid; Summary lists the practitioner's billed and paid totals;
 * the refused claim is marked refused (F); MSP Reconciliation Reports opens from the menu; LAST, that list's
 * Billed PDF names the owned claims (patient number and both fee codes) and its Summary PDF the payee and
 * practitioner numbers.
 * Known failures, recorded in the manifest notes: on 2026.08 Submit stores the remittance and then
 * answers HTTP 500 (#4806; #4808 is its release fix), so the upload step fails; MSP Reconciliation
 * Reports from the menu fails on every BC install (#4439, D6); the list's PDF links answer HTTP 404 until
 * #5063 is fixed (they resolve under the page's base to /<context>/createBillingReportAction), the last step.
 * Needs DOCUMENT_DIR, the server's document directory reachable from this runner (the uploaded copy
 * is removed from it), a NEW_BC_TELEPLAN=no install, and Poppler's pdftotext for the PDF text (SKIP otherwise).
 * Fixtures: one billing physician (bc.createBillingPhysician), the workflow's own FAKE patient made a
 * BC patient, and two submitted MSP claims (status B) for it. Cleanup deletes the remittance's
 * teleplanS21, teleplanS00 and teleplanS22 rows (by the file name, which carries the marker), the
 * uploaded copy in DOCUMENT_DIR and the local file, every BC billing row of the patient and the
 * physician, and asserts each is gone. BC installs only (billregion=BC).
 */
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const bc = require('./lib/bc-billing-fixture');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow } = require('./lib/workflow-session');
const { requirePoppler, pdfTextBuffer, squash } = require('./lib/export-content-helpers');

const TIMEOUT = 60000;
const UPLOAD_ROUTE = '/billing/CA/BC/ViewBillingTA';
const PAID = { code: '00100', amount: '23.00' };
const REFUSED = { code: '00110', amount: '31.50' };

/** The step labels; one place, so the script and the manifest agree. */
const STEP = {
  open: 'Administration ▸ Billing ▸ Upload Remittance Files opens the upload form',
  upload: 'Submit stores the FAKE remittance and opens the reconciliation list, which lists it',
  money: "the reconciliation list shows the remittance's payee and its billed, paid, forward, cheque and new balance amounts in dollars",
  stored: 'the remittance is stored once and settles the paid claim (S)',
  detail: "the remittance's Detail lists the refused claim's line with its explanation and nothing paid",
  summary: "the remittance's Summary lists the practitioner's billed and paid totals",
  refused: 'the claim MSP refused is marked refused (F)',
  menu: 'Administration ▸ Billing ▸ MSP Reconciliation Reports opens the reconciliation list',
  pdfs: "the list's Billed PDF and Summary PDF are PDFs that name the remittance's claims, payee and practitioner",
};

const text = (value, width) => String(value).padEnd(width, ' ').slice(0, width);
const digits = (value, width) => String(value).padStart(width, '0').slice(-width);
const cents = (amount, width) => digits(amount.replace('.', ''), width);

/**
 * The FAKE remittance's four records, fixed width as genTA.jsp reads them (S21, S01 and S22 are
 * 165 characters, S03 267).
 */
function remittanceLines({ marker, payee, practitioner, phn, paid, refused, today }) {
  const head = (type, seq) => type + text('T9999', 5) + digits(seq, 7) + today + '1' + text(payee, 5) + digits(999999, 6);
  const billed = Number(paid.amount) + Number(refused.amount);
  const s21 = head('S21', 1) + text(marker, 25) + cents(billed.toFixed(2), 9) + cents(paid.amount, 9) + digits(0, 9)
    + cents(paid.amount, 9) + digits(0, 9) + text('', 60);
  const s01 = head('S01', 2) + text(practitioner, 5) + '  0000000'.repeat(7) + digits(paid.master, 7) + cents(paid.amount, 7)
    + today + '00' + digits(0, 8) + '  ' + text('', 28);
  const s03 = head('S03', 3) + text(practitioner, 5) + today + 'W ' + text(marker, 18) + text(phn, 10) + '00' + today + '00'
    + '001' + '00' + text(refused.code, 5) + cents(refused.amount, 7) + '000' + '00' + text(refused.code, 5) + digits(0, 7)
    + digits(refused.master, 7) + 'AA' + '  '.repeat(6) + '  0000000'.repeat(7) + digits(0, 10) + ' ' + digits(0, 8) + '  '
    + digits(0, 8) + text('', 30);
  const s22 = head('S22', 4) + text(practitioner, 5) + text(marker, 25) + cents(billed.toFixed(2), 9) + cents(paid.amount, 9)
    + text('', 82);
  for (const [name, line, width] of [['S21', s21, 165], ['S01', s01, 165], ['S03', s03, 267], ['S22', s22, 165]]) {
    h.assert(line.length === width, `The FAKE ${name} record is ${line.length} characters, not ${width}`);
  }
  return [s21, s01, s03, s22];
}

function documentDir() {
  return process.env.DOCUMENT_DIR ? path.resolve(process.env.DOCUMENT_DIR) : '';
}

/** The remittance's stored rows and both copies of its file; registered before the upload. */
function ownRemittance(s, local) {
  const { sql, marker } = s;
  const q = h.sqlString;
  const like = q(`%${marker}%`);
  const dir = documentDir();
  s.cleanup(() => {
    sql.execute(['teleplanS22', 'teleplanS00', 'teleplanS21'].map(table => `DELETE FROM ${table} WHERE filename LIKE ${like}`).join(';'));
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM teleplanS21 WHERE filename LIKE ${like})
      + (SELECT COUNT(*) FROM teleplanS00 WHERE filename LIKE ${like})
      + (SELECT COUNT(*) FROM teleplanS22 WHERE filename LIKE ${like})`) === '0', 'The FAKE remittance rows were not removed');
    for (const name of fs.readdirSync(dir)) {
      if (name.includes(marker) && name === path.basename(name)) fs.unlinkSync(path.join(dir, name));
    }
    h.assert(!fs.readdirSync(dir).some(name => name.includes(marker)), 'The uploaded remittance copy was not removed');
    if (fs.existsSync(local)) fs.unlinkSync(local);
    fs.rmdirSync(path.dirname(local));
  });
}

async function preflight({ sql }) {
  bc.requireBcBillingSchema(sql);
  requirePoppler('pdftotext');
  const dir = documentDir();
  if (!dir || !fs.existsSync(dir) || !fs.statSync(dir).isDirectory()) {
    throw new h.SkipCheck('DOCUMENT_DIR (the server\'s document directory, reachable from this runner) is required');
  }
}

async function workflow(s) {
  const { sql, marker, patient } = s;
  const q = h.sqlString;
  const physician = bc.createBillingPhysician(s);
  const phn = bc.makeBcPatient(s);
  bc.registerOwnedBcBillCleanup(s);
  const seed = claim => ({ ...claim, master: bc.seedOwnedBcBill({ ...s, provider: physician.providerNo },
    { status: 'B', code: claim.code, amount: claim.amount }).billingmasterNo });
  const paid = seed(PAID);
  const refused = seed(REFUSED);
  const local = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'bc-remittance-')), `${marker}.TXT`);
  ownRemittance(s, local);
  const today = sql.value("SELECT DATE_FORMAT(CURDATE(),'%Y%m%d')");
  fs.writeFileSync(local, `${remittanceLines({ marker, payee: physician.payeeNo, practitioner: physician.ohipNo, phn, paid, refused, today })
    .join('\r\n')}\r\n`, 'latin1');
  const like = q(`%${marker}%`);
  const statuses = () => sql.value(`SELECT GROUP_CONCAT(billingstatus ORDER BY billingmaster_no) FROM billingmaster
    WHERE billingmaster_no IN (${paid.master},${refused.master})`);
  h.assert(statuses() === 'B,B', `The owned claims did not start submitted: ${statuses()}`);

  const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'bc-remittance-admin' });
  const menuLink = route => admin.locator(`a.xlink[rel$="${route}"]`).first();
  const contentFrame = async () => {
    const iframe = admin.locator('#dynamic-content iframe#myFrame');
    await iframe.waitFor({ timeout: TIMEOUT });
    const frame = await (await iframe.elementHandle()).contentFrame();
    h.assert(frame, 'The page did not load into the Administration panel');
    return frame;
  };
  /** Open a BC billing menu item into the Administration panel; null when the install does not offer it. */
  const openMenu = async (route, label) => {
    await menuLink('/billing/CA/BC/ViewBillingAccountReports').waitFor({ state: 'attached', timeout: TIMEOUT });
    const link = menuLink(route);
    if (await link.count() === 0) return null;
    await revealAuditLink(admin, link, 20000);
    const loaded = admin.waitForResponse(r => new URL(r.url()).pathname.endsWith(route), { timeout: TIMEOUT });
    await link.click();
    const response = await loaded;
    h.assert(response.status() === 200, `${label} answered HTTP ${response.status()}`);
    const frame = await contentFrame();
    await frame.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(frame, label);
    return frame;
  };
  let raNo;
  let list;

  await s.step(STEP.open, async () => {
    const frame = await openMenu(UPLOAD_ROUTE, 'Upload Remittance Files');
    if (!frame) throw new h.SkipCheck('Upload Remittance Files is offered only with NEW_BC_TELEPLAN=no');
    h.assert(await frame.locator('form[name="form1"] input[type="file"][name="file1"]').count() === 1, 'The upload form has no file field');
  });

  await s.step(STEP.upload, async () => {
    const frame = await contentFrame();
    await frame.locator('form[name="form1"] input[type="file"][name="file1"]').setInputFiles(local);
    const navigated = admin.waitForEvent('framenavigated', { predicate: f => f === frame, timeout: TIMEOUT });
    navigated.catch(() => {});
    const [response] = await Promise.all([
      admin.waitForResponse(r => r.request().method() === 'POST' && /DocumentTeleplanReportUploadServlet/.test(new URL(r.url()).pathname),
        { timeout: TIMEOUT }),
      frame.locator('form[name="form1"] input[type="submit"][name="Submit"]').click(),
    ]);
    h.assert(response.status() === 200, `Submit answered HTTP ${response.status()}`);
    await navigated;
    await frame.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(frame, 'the reconciliation list after the upload');
    raNo = sql.value(`SELECT s21_id FROM teleplanS21 WHERE filename LIKE ${like}`);
    h.assert(/^[1-9]\d*$/.test(raNo), 'The FAKE remittance header was not stored');
    h.assert(await frame.locator(`a[href*="ViewGenTAS00?rano=${raNo}&"]`).count() === 1,
      'The reconciliation list after the upload does not list the FAKE remittance');
    list = frame;
  });

  // The remittance's row of the reconciliation list: its Detail link's nearest row (the list sits in layout tables).
  const listRow = () => list.locator(`a[href*="ViewGenTAS00?rano=${raNo}&"]`).locator('xpath=ancestor::tr[1]');

  await s.step(STEP.money, async () => {
    const cells = (await listRow().locator('xpath=./td').allInnerTexts()).map(cell => cell.replace(/\u00a0/g, ' ').trim());
    const billed = (Number(PAID.amount) + Number(REFUSED.amount)).toFixed(2);
    // The S21 header's fields, as remittanceLines() writes them: payee name, then billed, paid, balance forward, cheque
    // and new balance, each in cents; the list prints them in dollars.
    const expected = [marker, billed, PAID.amount, '0.00', PAID.amount, '0.00'];
    h.assert(JSON.stringify(cells.slice(1, 7)) === JSON.stringify(expected),
      `The reconciliation list shows ${JSON.stringify(cells.slice(1, 7))} for the remittance, not ${JSON.stringify(expected)}`);
  });

  await s.step(STEP.stored, async () => {
    const stored = sql.value(`SELECT CONCAT_WS('|',
        (SELECT COUNT(*) FROM teleplanS21 WHERE filename LIKE ${like}),
        (SELECT GROUP_CONCAT(t_s00type ORDER BY t_s00type) FROM teleplanS00 WHERE filename LIKE ${like} AND s21_id=${raNo}),
        (SELECT COUNT(*) FROM teleplanS22 WHERE filename LIKE ${like} AND s21_id=${raNo}))`);
    h.assert(stored === '1|S01,S03|1', `The stored remittance is ${stored}, not 1|S01,S03|1`);
    h.assert(sql.value(`SELECT billingstatus FROM billingmaster WHERE billingmaster_no=${paid.master}`) === 'S',
      `After the upload the paid claim is ${statuses()}, not settled (S)`);
  });

  await s.step(STEP.detail, async () => {
    const detail = await s.context.newPage();
    await h.gotoApp(detail, s.config.baseUrl, `/billing/CA/BC/ViewGenTAS00?rano=${raNo}&proNo=`);
    await h.assertNotErrorPage(detail, 'the remittance Detail');
    // Detail lists a remittance's lines other than the S01 paid ones (TeleplanS00Dao.search_taS00), so the
    // refusal. The report sits in layout tables, so the line's row is its link's nearest row.
    const link = detail.locator(`a[href*="billingmaster_no=${refused.master.padStart(7, '0')}"]`);
    h.assert(await link.count() === 1, `Detail lists the refused claim ${refused.master} ${await link.count()} times, not once`);
    const rowText = await link.locator('xpath=ancestor::tr[1]').innerText();
    h.assert(rowText.includes('0.00') && rowText.includes('AA') && rowText.includes('S03'),
      `Detail's line for the refused claim does not show S03, nothing paid and explanation AA: ${JSON.stringify(rowText)}`);
    await detail.close();
  });

  await s.step(STEP.summary, async () => {
    const summary = await s.context.newPage();
    await h.gotoApp(summary, s.config.baseUrl, `/billing/CA/BC/ViewGenTAS22?rano=${raNo}&proNo=`);
    await h.assertNotErrorPage(summary, 'the remittance Summary');
    // Innermost rows only: the summary sits in layout tables whose rows hold every inner row's text.
    const row = summary.locator(`xpath=//tr[not(.//tr)][contains(., "${marker}")][contains(., "${physician.ohipNo}")]`);
    h.assert(await row.count() === 1, "Summary does not list the practitioner's summary once");
    const rowText = await row.innerText();
    const billed = (Number(PAID.amount) + Number(REFUSED.amount)).toFixed(2);
    h.assert(rowText.includes(billed) && rowText.includes(PAID.amount), `Summary row does not show billed ${billed} and paid ${PAID.amount}`);
    await summary.close();
  });

  await s.step(STEP.refused, async () => {
    const status = sql.value(`SELECT billingstatus FROM billingmaster WHERE billingmaster_no=${refused.master}`);
    h.assert(status === 'F', `After the upload the refused claim's status is ${JSON.stringify(status)}, not refused (F)`);
  });

  await s.step(STEP.menu, async () => {
    // Found by its label: on 2026.08 the item opens ProcessRemittance, the upload's own processor; its
    // fix (#4778) points it at a read-only reconciliation page instead.
    await menuLink('/billing/CA/BC/ViewBillingAccountReports').waitFor({ state: 'attached', timeout: TIMEOUT });
    // The English label is spelled "Reconcilliation" (admin.admin.reconciliationReports); either spelling matches.
    const link = admin.locator('a.xlink').filter({ hasText: /^\s*MSP Reconcil+iation Reports\s*$/i }).first();
    h.assert(await link.count() === 1, 'Administration ▸ Billing has no MSP Reconciliation Reports item');
    await revealAuditLink(admin, link, 20000);
    const route = await link.getAttribute('rel');
    const loaded = admin.waitForResponse(r => new URL(r.url()).pathname.endsWith(new URL(route, 'https://x').pathname), { timeout: TIMEOUT });
    await link.click();
    const response = await loaded;
    h.assert(response.status() === 200, `MSP Reconciliation Reports answered HTTP ${response.status()}`);
    const frame = await contentFrame();
    await frame.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(frame, 'MSP Reconciliation Reports');
    h.assert(await frame.locator(`a[href*="ViewGenTAS00?rano=${raNo}&"]`).count() === 1,
      'MSP Reconciliation Reports does not list the FAKE remittance');
    list = frame;
  });

  await s.step(STEP.pdfs, async () => {
    // Numbers, not names: the reports' columns can cut a long name.
    for (const [type, label, wanted] of [['REP_MSPREM', 'Billed PDF', [String(patient), PAID.code, REFUSED.code]],
      ['REP_MSPREMSUM', 'Summary PDF', [physician.payeeNo, physician.ohipNo]]]) {
      // Each report has a PDF and a CSV link; these are the PDF ones.
      const link = listRow().locator(`a[href*="docFormat=pdf&repType=${type}&"]`);
      h.assert(await link.count() === 1, `The remittance's row has no ${label} link`);
      const href = await link.evaluate(a => a.href);
      const response = await s.context.request.get(href, { timeout: TIMEOUT });
      h.assert(response.ok(), `The ${label} answered HTTP ${response.status()}`);
      const text = squash(pdfTextBuffer(await response.body()));
      const missing = wanted.filter(value => !text.includes(squash(value)));
      h.assert(missing.length === 0, `The ${label} does not name ${JSON.stringify(missing)}: ${text.slice(0, 300)}`);
    }
  });
  await admin.close();
}

if (require.main === module) runWorkflow('billing-bc-remittance-upload', workflow, { openMaster: false, preflight });
module.exports = { workflow, preflight, STEP };
