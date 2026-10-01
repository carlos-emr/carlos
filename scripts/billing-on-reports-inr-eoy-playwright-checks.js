#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

/*
 * Ontario billing reports: billed report, INR batch billing, end-of-year statement, MOH L report.
 * User paths: Schedule ▸ Report ▸ Billing Report ▸ Billed ▸ Create Report (ViewBillingReportCenter,
 * ViewBillingONReport); Schedule ▸ Administration ▸ Billing ▸ INR Batch Billing ▸ tick ▸ Generate
 * (ViewInrOnGenINRbilling) ▸ patient name (InrUpdateINRbilling); … ▸ End Year Statement ▸ search ▸
 * pick (demosearch) ▸ Create Statement ▸ Print PDF; … ▸ Upload MOH File ▸ L file (billingLreport).
 * Asserted (DB + page): one billed-report row per owned open claim in range; INR generation writes
 * one claim (header + item) for the ticked row and stamps it billed; GET on the generator is 405
 * and writes nothing; the statement lists exactly the owned PAT invoices in the window with items
 * and totals. The last four steps assert behaviour the app lacks today: billed-report cells/headers
 * (forEach var "header" renders the request headers, Cookie included), the statement PDF (HTTP 500),
 * the L report render (ES.xsl 404) and the INR update form (405 to its own GET opener).
 * Fixtures: runWorkflow FAKE- patient, a FAKE- billable provider + reportprovider row, a billinginr
 * row, four seeded claims, the uploaded L file (DOCUMENT_DIR + ONEDT_INBOX); all removed and
 * re-checked in cleanup. Implements coverage-plan billing-on-reports-inr-eoy.
 */

const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const { randomInt } = require('node:crypto');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const INR_CODE = 'G271A';
const INR_DX = '286';
const WINDOW = { from: '2025-01-01', to: '2025-12-31' };
const owned = id => /^[1-9]\d*$/.test(String(id));
const money = text => Number(String(text).replace(/[$,\s]/g, ''));
/** Calendar day of a rendered date, whether it is ISO or Java's Date.toString() form. */
const isoDay = text => {
  const parsed = new Date(/^\d{4}-\d{2}-\d{2}$/.test(text) ? `${text}T12:00:00Z` : text);
  return Number.isNaN(parsed.getTime()) ? '' : parsed.toISOString().slice(0, 10);
};

function unusedProviderNo(sql) {
  for (let attempt = 0; attempt < 20; attempt++) {
    const candidate = String(randomInt(800000, 899999));
    if (sql.value(`SELECT (SELECT COUNT(*) FROM provider WHERE provider_no=${h.sqlString(candidate)})
      + (SELECT COUNT(*) FROM billinginr WHERE provider_no=${h.sqlString(candidate)})`) === '0') return candidate;
  }
  throw new Error('No unused provider number was found');
}

async function openAdminFrame(admin, rel, ready) {
  const link = admin.locator(`a.xlink[rel$="${rel}"]`).first();
  await link.waitFor({ state: 'attached' });
  await revealAuditLink(admin, link, 20000);
  await link.click();
  const iframe = admin.locator('#dynamic-content iframe#myFrame');
  await iframe.waitFor();
  const frame = await (await iframe.elementHandle()).contentFrame();
  h.assert(frame, `${rel} did not load into the administration frame`);
  await frame.locator(ready).first().waitFor();
  return frame;
}

async function frameNavigation(page, frame, action) {
  const navigated = page.waitForEvent('framenavigated', { predicate: f => f === frame, timeout: 30000 });
  navigated.catch(() => {});
  await action();
  await navigated;
  await frame.waitForLoadState('domcontentloaded');
  await h.assertNotErrorPage(frame, 'billing frame');
}

/** Type an ISO date into a flatpickr (allowInput) field and close its calendar. */
async function fillDate(frame, selector, value) {
  await frame.locator(selector).fill(value);
  await frame.locator('h3').first().click();
  h.assert(await frame.locator(selector).inputValue() === value, `${selector} did not keep ${value}`);
}

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const q = h.sqlString;
  const ids = { providerNo: '', ohip: String(randomInt(100000, 999999)), inr: '', report: '', bills: [] };
  const today = sql.value('SELECT CURDATE()');
  const fee = code => sql.value(`SELECT value FROM billingservice WHERE service_code=${q(code)} AND region='ON'
    AND billingservice_date<=CURDATE() ORDER BY billingservice_date DESC LIMIT 1`);
  const inrFee = fee(INR_CODE);
  if (!/^\d+\.\d{2}$/.test(inrFee)) throw new h.SkipCheck(`${INR_CODE} has no Ontario fee`);
  try { execFileSync('pdftotext', ['-v'], { stdio: 'pipe', timeout: 5000 }); } catch {
    throw new h.SkipCheck('The year-end statement PDF check requires Poppler pdftotext');
  }

  const inbox = process.env.ONEDT_INBOX
    || (process.env.DOCUMENT_DIR ? path.join(path.dirname(path.resolve(process.env.DOCUMENT_DIR)), 'onEDTDocs', 'inbox') : '');
  if (!inbox || !fs.existsSync(inbox)) throw new h.SkipCheck('ONEDT_INBOX (the MOH inbox folder) is not set');
  const mohName = `L${marker.replace(/^FAKE-PW/, '')}.xml`;

  s.cleanup(() => {
    // The upload stores the report in DOCUMENT_DIR and copies it into the MOH inbox.
    const copies = [path.join(inbox, mohName), path.join(process.env.DOCUMENT_DIR || inbox, mohName)];
    for (const copy of copies) fs.rmSync(copy, { force: true });
    h.assert(copies.every(copy => !fs.existsSync(copy)), 'The owned MOH report files were not removed');
    const headers = sql.rows(`SELECT id FROM billing_on_cheader1 WHERE demographic_no=${patient}`).map(r => r[0]);
    const hIds = headers.length ? headers.map(Number).join(',') : '0';
    sql.execute(`DELETE FROM billing_on_ext WHERE billing_no IN (${hIds});
      DELETE FROM billing_on_item WHERE ch1_id IN (${hIds});
      DELETE FROM billing_on_cheader1 WHERE id IN (${hIds}) AND demographic_no=${patient};
      DELETE FROM billinginr WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM billing_on_cheader1 WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM billing_on_item WHERE ch1_id IN (${hIds}))
      + (SELECT COUNT(*) FROM billinginr WHERE demographic_no=${patient})`) === '0',
    'Owned claims or INR rows were not removed');
    if (owned(ids.report)) {
      sql.execute(`DELETE FROM reportprovider WHERE id=${ids.report} AND provider_no=${q(ids.providerNo)}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM reportprovider WHERE id=${ids.report}`) === '0',
        'The owned report-provider row was not removed');
    }
    if (ids.providerNo) {
      const p = q(ids.providerNo);
      sql.execute(`DELETE FROM provider WHERE provider_no=${p} AND last_name=${q(marker)}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${p}`) === '0', 'The owned provider was not removed');
    }
  });

  ids.providerNo = unusedProviderNo(sql);
  const p = q(ids.providerNo);
  sql.execute(`INSERT INTO provider (provider_no,last_name,first_name,provider_type,specialty,sex,ohip_no,rma_no,status,
      lastUpdateUser,lastUpdateDate) VALUES (${p},${q(marker)},'Reports','doctor','','F',${q(ids.ohip)},'','1',${q(provider)},NOW())`);
  ids.report = sql.value(`INSERT INTO reportprovider (provider_no,team,action,status)
    VALUES (${p},'',${q('billingreport')},'A'); SELECT LAST_INSERT_ID()`);
  h.assert(owned(ids.report), 'The report-provider fixture was not created');
  const demoName = `${marker},Workflow`;
  // [pay program, billing date, code, total, paid]: two PAT invoices in the window, one OHIP
  // claim in the window and one PAT invoice outside it.
  const seeds = [['PAT', '2025-03-10', 'A007A', '25.00'], ['PAT', '2025-06-20', 'K030A', null],
    ['HCP', '2025-04-02', 'A007A', '0.00'], ['PAT', '2024-12-15', 'A007A', '0.00']];
  for (const [program, date, code, paid] of seeds) {
    const total = fee(code);
    h.assert(/^\d+\.\d{2}$/.test(total), `${code} has no Ontario fee`);
    const id = sql.value(`INSERT INTO billing_on_cheader1 (header_id,transc_id,rec_id,hin,ver,dob,pay_program,payee,ref_num,
        facilty_num,admission_date,ref_lab_num,man_review,location,demographic_no,provider_no,appointment_no,demographic_name,
        sex,province,billing_date,billing_time,total,paid,status,comment1,visittype,provider_ohip_no,provider_rma_no,
        apptProvider_no,asstProvider_no,creator,clinic)
      VALUES (0,'HE','H','','','19800102',${q(program)},'P','','','','','','',${patient},${p},0,${q(demoName)},'2','ON',
        ${q(date)},'10:00:00',${total},${paid === null ? total : paid},'O',${q(marker)},'00',${q(ids.ohip)},'','','',
        ${q(provider)},NULL); SELECT LAST_INSERT_ID()`);
    h.assert(owned(id), 'A claim fixture was not created');
    sql.execute(`INSERT INTO billing_on_item (ch1_id,transc_id,rec_id,service_code,fee,ser_num,service_date,dx,dx1,dx2,status)
      VALUES (${id},'HE','T',${q(code)},${q(total)},'1',${q(date)},'250','','','O')`);
    ids.bills.push({ id, program, date, code, total, paid: paid === null ? total : paid });
  }
  ids.inr = sql.value(`INSERT INTO billinginr (demographic_no,demographic_name,hin,dob,provider_no,provider_ohip_no,provider_rma_no,
      creator,diagnostic_code,service_code,service_desc,billing_amount,billing_unit,createdatetime,status)
    VALUES (${patient},${q(demoName)},'','19800102',${p},${q(ids.ohip)},'',${q(provider)},${q(INR_DX)},${q(INR_CODE)},
      'INR management',${q(inrFee)},'1',NOW(),'N'); SELECT LAST_INSERT_ID()`);
  h.assert(owned(ids.inr), 'The INR billing fixture was not created');
  const statement = ids.bills.filter(b => b.program === 'PAT' && b.date >= WINDOW.from && b.date <= WINDOW.to);

  let report;
  let index;
  const inRange = ids.bills.filter(b => b.date >= WINDOW.from && b.date <= today);
  await s.step('Report ▸ Billing Report ▸ Billed returns one row per owned open claim in the range', async () => {
    const link = s.schedule.locator("a[onclick*='/report/ViewReportindex'], a[href*='/report/ViewReportindex']").first();
    ({ page: index } = await ui.clickOpensPopupOrNavigates(s.schedule, link,
      { context: s.context, recorder: s.recorder, label: 'report-index', timeout: 20000 }));
    report = await s.popup(index, index.locator('a[href*="/billing/CA/ON/ViewBillingReportCenter"]').first(), 'billing-report');
    await report.locator('form[name="serviceform"]').waitFor();
    await h.assertNotErrorPage(report, 'billing report');
    const offered = await report.locator('select[name="providerview"] option').evaluateAll(o => o.map(x => x.value));
    h.assert(offered.includes(ids.providerNo), 'The billing report does not offer the owned report provider');
    await report.locator('input[name="reportAction"][value="billed"]').check();
    await report.locator('select[name="providerview"]').selectOption(ids.providerNo);
    await report.locator('#xml_vdate').fill(WINDOW.from);
    await report.locator('#xml_appointment_date').fill(today);
    await Promise.all([
      report.waitForURL(/\/billing\/CA\/ON\/ViewBillingONReport/, { timeout: 30000 }),
      report.locator('input[type="submit"][value="Create Report"]').click(),
    ]);
    await report.locator('#reportTbl').waitFor();
    await h.assertNotErrorPage(report, 'billed report');
    h.assert(await report.locator('input[name="reportAction"][value="billed"]').isChecked()
      && await report.locator('select[name="providerview"]').inputValue() === ids.providerNo,
    'The billed report did not keep its report type and provider');
    h.assert(await report.locator('#reportTbl tbody tr').count() === inRange.length,
      'The billed report does not return exactly one row per owned open claim in the range');
  });

  const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'administration', timeout: 20000 });

  let inr;
  await s.step('INR Batch Billing lists the owned INR row with its code, amount, dx and unbilled state', async () => {
    inr = await openAdminFrame(admin, '/billing/CA/ON/ViewInrReportINR?provider_no=all', 'select[name="provider"]');
    // The provider dropdown comes from a five-minute provider cache that an SQL-seeded provider
    // does not evict, so the row is reached through the "all providers" list the menu opens.
    const row = inr.locator('tr').filter({ has: inr.locator(`input[name="inrbilling${ids.inr}"]`) });
    h.assert(await row.count() === 1, 'The INR report does not list the owned INR row exactly once');
    const text = (await row.innerText()).replace(/\s+/g, ' ');
    h.assert(text.includes(marker) && text.includes(INR_CODE) && text.includes(inrFee) && text.includes(INR_DX)
      && text.includes('Not Available'), 'The INR row does not show the patient, code, amount, dx and unbilled state');
  });

  await s.step('Generate INR Batch Billing writes one claim for the ticked row and stamps it billed', async () => {
    const location = await inr.locator('select[name="xml_location"]').inputValue();
    await inr.locator(`input[name="inrbilling${ids.inr}"]`).check();
    await inr.locator('input[name="xml_appointment_date"]').fill(today);
    const before = new Set(sql.rows(`SELECT id FROM billing_on_cheader1 WHERE demographic_no=${patient}`).map(r => r[0]));
    const [response] = await Promise.all([
      s.context.waitForEvent('response', { timeout: 30000, predicate: r => r.request().method() === 'POST'
        && new URL(r.url()).pathname.endsWith('/billing/CA/ON/ViewInrOnGenINRbilling') }),
      inr.locator('input[type="submit"][value="Generate INR Batch Billing"]').click(),
    ]);
    h.assert(response.status() === 302, `INR generation answered HTTP ${response.status()}`);
    await inr.waitForURL(/\/billing\/CA\/ON\/ViewInrReportINR\?provider_no=all/, { timeout: 30000 });
    await h.assertNotErrorPage(inr, 'INR report');
    await expectValue(sql, `SELECT CONCAT_WS('|', status, DATE(createdatetime)) FROM billinginr WHERE billinginr_no=${ids.inr}`,
      `A|${today}`, 'The INR row was not stamped billed with the service date');
    const created = sql.rows(`SELECT id FROM billing_on_cheader1 WHERE demographic_no=${patient}`).map(r => r[0])
      .filter(id => !before.has(id));
    h.assert(created.length === 1, 'INR generation did not create exactly one claim');
    ids.bills.push({ id: created[0], program: 'HCP', date: today, code: INR_CODE, total: inrFee, paid: '0.00' });
    h.assert(sql.value(`SELECT CONCAT_WS('|', provider_no, pay_program, total, status, billing_date, creator, facilty_num,
        demographic_name, provider_ohip_no) FROM billing_on_cheader1 WHERE id=${created[0]}`)
      === `${ids.providerNo}|HCP|${inrFee}|O|${today}|${provider}|${location}|${demoName}|${ids.ohip}`,
    'The INR claim header does not carry the provider, program, amount, date, creator, clinic and patient');
    h.assert(sql.value(`SELECT CONCAT_WS('|', service_code, fee, ser_num, dx, status, service_date) FROM billing_on_item
        WHERE ch1_id=${created[0]}`) === `${INR_CODE}|${inrFee}|1|${INR_DX}|O|${today}`,
    'The INR claim item does not carry the code, fee, unit, dx and date');
    const row = inr.locator('tr').filter({ has: inr.locator(`input[name="inrbilling${ids.inr}"]`) });
    h.assert((await row.innerText()).includes(today), 'The INR report does not show the new last bill date');
  });

  await s.step('GET against the INR generator is refused and writes nothing', async () => {
    const count = sql.value(`SELECT COUNT(*) FROM billing_on_cheader1 WHERE demographic_no=${patient}`);
    const response = await s.context.request.get(h.appUrl(s.config.baseUrl, '/billing/CA/ON/ViewInrOnGenINRbilling'), {
      params: { [`inrbilling${ids.inr}`]: 'on', xml_appointment_date: today, curUser: provider }, maxRedirects: 0,
    });
    h.assert(response.status() === 405 && response.headers().allow === 'POST', 'GET INR generation was not refused with 405');
    h.assert(sql.value(`SELECT COUNT(*) FROM billing_on_cheader1 WHERE demographic_no=${patient}`) === count,
      'A refused GET created a claim');
  });

  let eoy;
  await s.step('End Year Statement ▸ patient search picks the owned patient (demosearch)', async () => {
    eoy = await openAdminFrame(admin, '/billing/CA/ON/endYearStatement', '#nameForlooksOnly');
    await eoy.locator('#nameForlooksOnly').fill(marker);
    await frameNavigation(admin, eoy, () => eoy.locator('button[onclick*="demographicSearch"]').click());
    const pick = eoy.locator(`input[name="pick_demographic"][value="${patient}"]`);
    h.assert(await pick.count() === 1, 'The patient search did not list the owned patient exactly once');
    await frameNavigation(admin, eoy, () => pick.click());
    h.assert(new URL(eoy.url()).pathname.endsWith('/billing/CA/ON/endYearStatement/demosearch'),
      'Picking the patient did not return to the statement through demosearch');
    h.assert(await eoy.locator('#demographicNoParam').inputValue() === patient,
      'The statement form did not keep the picked patient');
    h.assert((await eoy.locator('#nameForlooksOnly').inputValue()).includes(marker),
      'The statement form does not show the picked patient');
  });

  await s.step('Create Statement lists exactly the owned PAT invoices in the window with their totals', async () => {
    await fillDate(eoy, '#fromDateParam', WINDOW.from);
    await fillDate(eoy, '#toDateParam', WINDOW.to);
    await frameNavigation(admin, eoy, () => eoy.locator('input[type="submit"][value="Create Statement"]').click());
    const invoiceRows = eoy.locator('tr[bgcolor="#CEF6CE"]');
    const listed = (await invoiceRows.evaluateAll(rows => rows.map(r => [...r.cells].map(c => c.innerText.trim()))));
    h.assert(listed.length === statement.length, 'The statement does not list exactly the owned PAT invoices in the window');
    statement.forEach((bill, i) => {
      const [no, date, , invoiced, paid] = listed[i];
      h.assert(no === bill.id && isoDay(date) === bill.date && money(invoiced) === Number(bill.total)
        && money(paid) === Number(bill.paid), `Statement invoice row ${i + 1} does not match the owned invoice`);
    });
    const services = await eoy.locator('tr[bgcolor="ivory"], tr[bgcolor="#EEEEFF"]').allInnerTexts();
    h.assert(statement.every(b => services.some(t => t.includes(b.code) && t.includes(b.total))),
      'The statement does not list each invoice\'s service code and fee');
    const totals = (await eoy.locator('tr[bgcolor="#99FF66"]').innerText()).replace(/\s+/g, ' ');
    const invoiced = statement.reduce((sum, b) => sum + Number(b.total), 0).toFixed(2);
    const paid = statement.reduce((sum, b) => sum + Number(b.paid), 0).toFixed(2);
    h.assert(new RegExp(`Count: ${statement.length}\\b`).test(totals) && totals.includes(invoiced) && totals.includes(paid),
      'The statement count and invoiced/paid totals are wrong');
    h.assert(await eoy.locator('input[type="submit"][value="Print PDF"]').isEnabled(), 'Print PDF is not offered');
  });

  await s.step('Billed report rows show each claim\'s date, patient and account, under the report\'s own headers', async () => {
    const headers = (await report.locator('#reportTbl thead th').allTextContents()).map(t => t.trim());
    h.assert(JSON.stringify(headers) === JSON.stringify(['SERVICE DATE', 'TIME', 'PATIENT', 'DESCRIPTION', 'ACCOUNT']),
      'The billed report column headers are not the report\'s column names (they render the request headers instead)');
    const rows = await report.locator('#reportTbl tbody tr').evaluateAll(trs => trs.map(tr => [...tr.cells]
      .map(c => c.textContent.replace(/\s+/g, ' ').trim())));
    for (const bill of inRange) {
      const row = rows.find(cells => cells[4] === bill.id);
      h.assert(row && row[0] === bill.date && row[2] === demoName, 'A billed-report row does not show the claim\'s date, patient and account');
    }
  });

  await s.step('Print PDF downloads a PDF carrying the same invoices and totals', async () => {
    const downloaded = admin.waitForEvent('download', { timeout: 30000 });
    downloaded.catch(() => {});
    const [answer] = await Promise.all([
      s.context.waitForEvent('response', { timeout: 30000, predicate: r => r.request().method() === 'POST'
        && new URL(r.url()).pathname.endsWith('/billing/CA/ON/endYearStatement/pdf') }),
      eoy.locator('input[type="submit"][value="Print PDF"]').click(),
    ]);
    h.assert(answer.status() === 200 && /^application\/pdf/.test(answer.headers()['content-type'] || ''),
      `End Year Statement ▸ Print PDF answered HTTP ${answer.status()} instead of a PDF`);
    const download = await downloaded;
    const file = await download.path();
    h.assert(file, 'The statement PDF download was not saved');
    const bytes = fs.readFileSync(file);
    h.assert(bytes.subarray(0, 4).toString('latin1') === '%PDF', 'The statement download is not a PDF');
    h.assert(/%%EOF\s*$/.test(bytes.subarray(-64).toString('latin1')), 'The statement PDF is followed by trailing bytes');
    const text = execFileSync('pdftotext', ['-layout', file, '-'], { encoding: 'utf8', timeout: 15000 });
    const invoiced = statement.reduce((sum, b) => sum + Number(b.total), 0).toFixed(2);
    const paid = statement.reduce((sum, b) => sum + Number(b.paid), 0).toFixed(2);
    h.assert(text.includes('End Year Statement') && text.includes(marker), 'The PDF does not name the statement and patient');
    h.assert(statement.every(b => new RegExp(`\\b${b.id}\\b`).test(text) && text.includes(b.code)),
      'The PDF does not list every owned PAT invoice and its service code');
    h.assert(ids.bills.filter(b => !statement.includes(b)).every(b => !new RegExp(`\\b${b.id}\\b`).test(text)),
      'The PDF lists an invoice outside the PAT/date selection');
    h.assert(text.includes(invoiced) && text.includes(paid), 'The PDF does not carry the invoiced and paid totals');
  });

  await s.step('Upload MOH File ▸ an L (outside use) report opens in billingLreport through its XSL', async () => {
    const upload = await openAdminFrame(admin, '/billing/CA/ON/BillingONUpload', 'input[type="file"][name="file1"]');
    await upload.locator('input[name="file1"]').setInputFiles({ name: mohName, mimeType: 'text/xml', buffer: Buffer.from(
      `<?xml version="1.0"?><REPORT><REPORT-DTL><REPORT-NAME>${marker} EDT REPORT</REPORT-NAME>`
      + `<REPORT-ID>${ids.ohip}</REPORT-ID><REPORT-DATE>${today}</REPORT-DATE></REPORT-DTL></REPORT>\n`) });
    await frameNavigation(admin, upload, () => upload.locator('input[type="submit"][value="Create Report"]').click());
    h.assert(fs.existsSync(path.join(inbox, mohName)), 'The uploaded L report was not copied into the MOH inbox');
    const rendered = await upload.locator('#MOHreport').getByText(`${marker} EDT REPORT`)
      .waitFor({ timeout: 15000 }).then(() => true, () => false);
    h.assert(rendered, 'billingLreport did not render the uploaded L report (its XSL transform produced nothing)');
    h.assert((await upload.locator('#MOHreport').innerText()).includes(ids.ohip), 'The rendered L report does not show its report id');
  });

  await s.step('INR row ▸ patient name opens the INR update form for that row', async () => {
    inr = await openAdminFrame(admin, '/billing/CA/ON/ViewInrReportINR?provider_no=all', 'select[name="provider"]');
    const row = inr.locator('tr').filter({ has: inr.locator(`input[name="inrbilling${ids.inr}"]`) });
    const [popup, answer] = await Promise.all([
      s.context.waitForEvent('page', { timeout: 20000 }),
      s.context.waitForEvent('response', { timeout: 20000, predicate: r =>
        new URL(r.url()).pathname.endsWith('/billing/CA/ON/InrUpdateINRbilling') }),
      row.locator('a', { hasText: marker }).click(),
    ]);
    await popup.waitForLoadState('domcontentloaded');
    h.assert(answer.status() === 200, `The INR update form answered HTTP ${answer.status()} to its opener's ${answer.request().method()}`);
    h.assert(await popup.locator('input[name="billinginr_no"]').inputValue() === ids.inr
      && await popup.locator('input[name="diag_code"]').inputValue() === INR_DX, 'The INR update form did not load the row');
  });
}

if (require.main === module) runWorkflow('billing-on-reports-inr-eoy', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
