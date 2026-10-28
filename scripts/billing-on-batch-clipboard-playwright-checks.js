#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

/*
 * Ontario batch billing and the RA billing clipboard, driven through their real openers.
 * User paths: Schedule ▸ Administration ▸ Billing ▸ Batch Billing (BatchBill, #myFrame) ▸ provider ▸
 * tick ▸ Generate Batch Invoices; tick ▸ Remove (confirm); … ▸ Billing Reconciliation (ViewGenRA) ▸
 * Report (genRADesc) ▸ Clipboard ▸ Print Preview (ViewPrintBillingClipboard).
 * Asserted: the batch list shows exactly the owned queue rows; Submit writes one claim (header +
 * item: provider, HCP, schedule fee, dx, clinic, date, creator) for the ticked patient only and
 * stamps that queue row; Remove confirms once and deletes only the ticked row; GET on both
 * mutators is 405 and writes nothing; the clipboard preview echoes both notes encoded, the long
 * one wrapped at 80 columns. The last step currently fails: the RA Report link posts a runtime
 * form without a CSRF token (403), so the Clipboard is unreachable.
 * Fixtures: runWorkflow FAKE- patient + a second FAKE- patient, a FAKE- billable provider (with the
 * operator's site memberships), two batch_billing rows, a raheader/radetail pair and its empty RA
 * file in DOCUMENT_DIR; all removed and re-checked in cleanup. Implements coverage-plan
 * billing-on-batch-clipboard.
 */

const fs = require('node:fs');
const path = require('node:path');
const { randomInt } = require('node:crypto');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const CODE = 'A007A';
const DX = '250';
const REMOVE_CONFIRM = 'Do you wish to remove your selected demographics from batch billing?';
const owned = id => /^[1-9]\d*$/.test(String(id));

function unusedProviderNo(sql) {
  for (let attempt = 0; attempt < 20; attempt++) {
    const candidate = String(randomInt(800000, 899999));
    if (sql.value(`SELECT (SELECT COUNT(*) FROM provider WHERE provider_no=${h.sqlString(candidate)})
      + (SELECT COUNT(*) FROM batch_billing WHERE billing_provider_no=${h.sqlString(candidate)})`) === '0') return candidate;
  }
  throw new Error('No unused provider number was found');
}

/** Administration ▸ <left-nav item> loaded into the #myFrame iframe. */
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

/** Run `action` and wait for `frame` to finish its next navigation. */
async function frameNavigation(admin, frame, action) {
  const navigated = admin.waitForEvent('framenavigated', { predicate: f => f === frame, timeout: 30000 });
  navigated.catch(() => {});
  await action();
  await navigated;
  await frame.waitForLoadState('domcontentloaded');
  await h.assertNotErrorPage(frame, 'administration frame');
}

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const q = h.sqlString;
  const ids = { other: '', providerNo: '', ohip: String(randomInt(100000, 999999)), ra: '', raFile: '' };
  const docDir = process.env.DOCUMENT_DIR;
  if (!docDir || !fs.existsSync(docDir)) throw new h.SkipCheck('DOCUMENT_DIR (the install\'s RA document folder) is not set');
  const fee = sql.value(`SELECT value FROM billingservice WHERE service_code=${q(CODE)} AND region='ON'
    AND billingservice_date<=CURDATE() ORDER BY billingservice_date DESC LIMIT 1`);
  if (!/^\d+\.\d{2}$/.test(fee) || Number(fee) <= 0) throw new h.SkipCheck(`${CODE} has no positive Ontario fee`);
  const patients = () => [patient, ids.other].filter(owned).join(',');

  s.cleanup(() => {
    const demos = patients();
    const headers = sql.rows(`SELECT id FROM billing_on_cheader1 WHERE demographic_no IN (${demos})`).map(r => r[0]);
    const hIds = headers.length ? headers.map(Number).join(',') : '0';
    sql.execute(`DELETE FROM billing_on_ext WHERE billing_no IN (${hIds});
      DELETE FROM billing_on_item WHERE ch1_id IN (${hIds});
      DELETE FROM billing_on_cheader1 WHERE id IN (${hIds}) AND demographic_no IN (${demos});
      DELETE FROM batch_billing WHERE demographic_no IN (${demos})`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM billing_on_cheader1 WHERE demographic_no IN (${demos}))
      + (SELECT COUNT(*) FROM billing_on_item WHERE ch1_id IN (${hIds}))
      + (SELECT COUNT(*) FROM batch_billing WHERE demographic_no IN (${demos}))`) === '0',
    'Owned claims or batch queue rows were not removed');
    if (owned(ids.ra)) {
      sql.execute(`DELETE FROM billing_on_premium WHERE raheader_no=${ids.ra};
        DELETE FROM radetail WHERE raheader_no=${ids.ra} AND providerohip_no=${q(ids.ohip)};
        DELETE FROM raheader WHERE raheader_no=${ids.ra} AND payable=${q(marker)}`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM raheader WHERE raheader_no=${ids.ra})
        + (SELECT COUNT(*) FROM radetail WHERE raheader_no=${ids.ra})`) === '0', 'The owned RA header was not removed');
    }
    if (ids.raFile) {
      fs.rmSync(ids.raFile, { force: true });
      h.assert(!fs.existsSync(ids.raFile), 'The owned RA file was not removed');
    }
    if (ids.providerNo) {
      const p = q(ids.providerNo);
      sql.execute(`DELETE FROM providersite WHERE provider_no=${p};
        DELETE FROM provider WHERE provider_no=${p} AND last_name=${q(marker)}`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM provider WHERE provider_no=${p})
        + (SELECT COUNT(*) FROM providersite WHERE provider_no=${p})`) === '0', 'The owned provider was not removed');
    }
    if (owned(ids.other)) {
      sql.execute(`DELETE FROM demographicArchive WHERE demographic_no=${ids.other};
        DELETE FROM demographic WHERE demographic_no=${ids.other} AND last_name=${q(`${marker}-B`)}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${ids.other}`) === '0',
        'The second owned patient was not removed');
    }
  });

  ids.providerNo = unusedProviderNo(sql);
  const p = q(ids.providerNo);
  sql.execute(`INSERT INTO provider (provider_no,last_name,first_name,provider_type,specialty,sex,ohip_no,rma_no,status,
      lastUpdateUser,lastUpdateDate) VALUES (${p},${q(marker)},'Batch','doctor','','F',${q(ids.ohip)},'','1',
      ${q(provider)},NOW())`);
  ids.other = sql.value(`INSERT INTO demographic (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,
      patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES (${q(`${marker}-B`)},'Second','1975','03','04','M','AC',${q(provider)},'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
  h.assert(owned(ids.other), 'The second patient fixture was not created');
  for (const demo of [patient, ids.other]) {
    sql.execute(`INSERT INTO batch_billing (demographic_no,billing_provider_no,service_code,dxcode,create_date,creator)
      VALUES (${demo},${p},${q(CODE)},${q(DX)},NOW(),${q(provider)})`);
  }
  h.assert(sql.value(`SELECT COUNT(*) FROM batch_billing WHERE billing_provider_no=${p}`) === '2',
    'The batch queue fixtures were not created');
  const claims = demo => sql.value(`SELECT COUNT(*) FROM billing_on_cheader1 WHERE demographic_no=${demo}`);
  const queueRow = demo => sql.value(`SELECT CONCAT_WS('|', IFNULL(billing_amount,'-'), IFNULL(lastbilled_date,'-'))
    FROM batch_billing WHERE demographic_no=${demo} AND billing_provider_no=${p}`);

  const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'administration', timeout: 20000 });
  let batch;

  await s.step('Batch Billing lists exactly the owned provider\'s queue rows', async () => {
    batch = await openAdminFrame(admin, '/billing/CA/ON/BatchBill?service_code=all', 'select[name="providers"]');
    const options = await batch.locator('select[name="providers"] option').evaluateAll(o => o.map(x => x.value));
    h.assert(options.includes(ids.providerNo), 'The batch billing provider list does not offer the owned billable provider');
    await frameNavigation(admin, batch, () => batch.locator('select[name="providers"]').selectOption(ids.providerNo));
    h.assert(new URL(batch.url()).searchParams.get('provider_no') === ids.providerNo,
      'Choosing a provider did not reload the batch list for that provider');
    const boxes = await batch.locator('input[type="checkbox"][name="bill"]').evaluateAll(b => b.map(x => x.value));
    h.assert(boxes.length === 2 && boxes.includes(`${CODE};${DX};${patient};${ids.providerNo}`)
      && boxes.includes(`${CODE};${DX};${ids.other};${ids.providerNo}`),
    'The batch list for the owned provider does not show exactly its two queue rows');
    const row = batch.locator('tr').filter({ has: batch.locator(`input[name="bill"][value*=";${patient};"]`) });
    const text = (await row.innerText()).replace(/\s+/g, ' ');
    h.assert(text.includes(marker) && text.includes(CODE) && text.includes(DX) && /N\/A.*N\/A/.test(text),
      'The queue row does not show the patient, code, dx and the not-yet-billed amount/date');
  });

  let billDate;
  await s.step('Submit bills only the ticked patient and stamps that queue row billed', async () => {
    billDate = await batch.locator('#BillDate').inputValue();
    h.assert(/^\d{4}-\d{2}-\d{2}$/.test(billDate), 'The batch service date is not prefilled as YYYY-MM-DD');
    const clinic = await batch.locator('select[name="clinic_view"]').inputValue();
    await batch.locator(`input[name="bill"][value*=";${patient};"]`).check();
    await frameNavigation(admin, batch, () => batch.locator('input[type="button"].btn-primary').click());
    await expectValue(sql, `SELECT COUNT(*) FROM billing_on_cheader1 WHERE demographic_no=${patient}`, '1',
      'Submitting the batch did not create exactly one claim for the ticked patient');
    h.assert(sql.value(`SELECT CONCAT_WS('|', provider_no, pay_program, total, status, billing_date, creator, facilty_num,
        appointment_no) FROM billing_on_cheader1 WHERE demographic_no=${patient}`)
      === `${ids.providerNo}|HCP|${fee}|O|${billDate}|${provider}|${clinic}|0`,
    'The batch claim header does not carry the provider, OHIP program, fee total, date, creator and clinic');
    h.assert(sql.value(`SELECT CONCAT_WS('|', i.service_code, i.fee, i.ser_num, i.dx, i.status, i.service_date)
        FROM billing_on_item i JOIN billing_on_cheader1 c ON c.id=i.ch1_id WHERE c.demographic_no=${patient}`)
      === `${CODE}|${fee}|1|${DX}|O|${billDate}`, 'The batch claim item does not carry the code, fee, unit, dx and date');
    h.assert(queueRow(patient) === `${fee}|${billDate}`, 'The billed queue row was not stamped with its amount and date');
    h.assert(claims(ids.other) === '0' && queueRow(ids.other) === '-|-', 'The unticked patient was billed or stamped');
    const row = batch.locator('tr').filter({ has: batch.locator(`input[name="bill"][value*=";${patient};"]`) });
    const text = (await row.innerText()).replace(/\s+/g, ' ');
    h.assert(text.includes(fee) && text.includes(billDate), 'The reloaded list does not show the billed amount and date');
  });

  await s.step('Remove asks once and deletes only the ticked queue row', async () => {
    await batch.locator(`input[name="bill"][value*=";${ids.other};"]`).check();
    let dialogs;
    await frameNavigation(admin, batch, async () => {
      dialogs = await h.withExpectedDialogs(admin, () => batch.locator('input[type="button"].btn-secondary').click());
    });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm' && dialogs[0].text === REMOVE_CONFIRM,
      'Remove did not ask for confirmation exactly once');
    await expectValue(sql, `SELECT COUNT(*) FROM batch_billing WHERE demographic_no=${ids.other}`, '0',
      'Remove did not delete the ticked queue row');
    h.assert(queueRow(patient) === `${fee}|${billDate}`, 'Remove changed the other queue row');
    h.assert(claims(ids.other) === '0' && claims(patient) === '1', 'Remove created or removed a claim');
    const boxes = await batch.locator('input[name="bill"]').evaluateAll(b => b.map(x => x.value));
    h.assert(boxes.length === 1 && boxes[0].includes(`;${patient};`), 'The reloaded list still shows the removed row');
  });

  await s.step('GET against the batch submit and remove mutators is refused and writes nothing', async () => {
    const value = `${CODE};${DX};${patient};${ids.providerNo}`;
    for (const method of ['doBatchBill', 'remove']) {
      const response = await s.context.request.get(h.appUrl(s.config.baseUrl, '/billing/CA/ON/BatchBill'), {
        params: { method, bill: value, providers: ids.providerNo, service_code: 'all' }, maxRedirects: 0,
      });
      h.assert(response.status() === 405 && response.headers().allow === 'POST', `GET ${method} was not refused with 405`);
    }
    h.assert(claims(patient) === '1' && queueRow(patient) === `${fee}|${billDate}`, 'A refused GET changed the batch state');
  });

  // RA header + its (empty) RA file: the Billing Reconciliation list's Report window reads
  // the file from DOCUMENT_DIR and is the only page that offers the Clipboard.
  ids.raFile = path.join(docDir, `${marker}.txt`);
  fs.writeFileSync(ids.raFile, '\n', { mode: 0o644 });
  ids.ra = sql.value(`INSERT INTO raheader (filename,paymentdate,payable,totalamount,records,claims,status,readdate,content)
    VALUES (${q(`${marker}.txt`)},'20260115',${q(marker)},'0.00','0','0','N',DATE_FORMAT(NOW(),'%Y-%m-%d'),'');
    SELECT LAST_INSERT_ID()`);
  h.assert(owned(ids.ra), 'The RA header fixture was not created');
  // With _site_access_privacy the list shows only RAs carrying a detail line for a provider
  // who shares a site with the operator: one detail line for the owned provider, whose site
  // memberships mirror the operator's.
  sql.execute(`INSERT INTO radetail (raheader_no,providerohip_no,billing_no,service_code,service_count,hin,amountclaim,
      amountpay,service_date,error_code,billtype,claim_no) VALUES (${ids.ra},${q(ids.ohip)},0,${q(CODE)},'01','','0','0',
      '20260110','','','');
    INSERT INTO providersite (provider_no,site_id) SELECT ${q(ids.providerNo)},site_id FROM providersite
      WHERE provider_no=${q(provider)}`);

  await s.step('Billing Reconciliation ▸ Report ▸ Clipboard print preview echoes both notes as text', async () => {
    const ra = await openAdminFrame(admin, '/billing/CA/ON/ViewGenRA', 'table');
    const row = ra.locator('tr').filter({ hasText: marker });
    h.assert(await row.count() === 1, 'The reconciliation list does not show the owned RA row');
    const [report, answer] = await Promise.all([
      s.context.waitForEvent('page', { timeout: 20000 }),
      s.context.waitForEvent('response', { timeout: 20000, predicate: r => r.request().method() === 'POST'
        && new URL(r.url()).pathname.endsWith('/billing/CA/ON/ViewGenRADesc') }),
      row.locator('a', { hasText: 'Report' }).click(),
    ]);
    h.wireStrictPage(report, 'ra-report', s.recorder);
    await report.waitForLoadState('domcontentloaded');
    h.assert(answer.status() === 200, `Billing Reconciliation ▸ Report answered HTTP ${answer.status()}`);
    await h.assertNotErrorPage(report, 'RA report');
    const clipboard = await s.popup(report, report.locator('input[type="button"][value="Clipboard"]'), 'billing-clipboard');
    await clipboard.locator('textarea[name="textfield"]').waitFor();
    const top = `${marker} <b>bold</b> & "quoted"`;
    const long = `${marker}-${'0123456789'.repeat(9)}`;
    await clipboard.locator('textarea[name="textfield"]').fill(top);
    await clipboard.locator('textarea[name="textfield1"]').fill(long);
    const [response] = await Promise.all([
      clipboard.waitForResponse(r => r.request().method() === 'POST' && /\/ViewPrintBillingClipboard$/.test(new URL(r.url()).pathname)),
      clipboard.locator('input[type="submit"][value="Print Preview"]').click(),
    ]);
    h.assert(response.status() === 200, `Print Preview answered HTTP ${response.status()}`);
    await clipboard.waitForLoadState('domcontentloaded');
    const blocks = await clipboard.locator('pre').allInnerTexts();
    h.assert(blocks.length === 2 && blocks[0].trim() === top, 'The first clipboard note was not echoed as text');
    h.assert(await clipboard.locator('pre b').count() === 0, 'Clipboard markup was rendered instead of encoded');
    const lines = blocks[1].split('\n').map(line => line.trim()).filter(Boolean);
    h.assert(lines.join('') === long && lines[0].length === 80 && lines.every(line => line.length <= 80),
      'The long clipboard note was not wrapped at 80 columns without losing text');
  });
}

if (require.main === module) runWorkflow('billing-on-batch-clipboard', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
