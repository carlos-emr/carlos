#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * BC Adjust Bill's own fee item and diagnosis searches (billing/CA/BC/reprocessBill, adjustBill.jsp).
 * billing-bc-reprocess-bill opens the page and settles a claim; billing-bc-bill-form-popups covers the bill form's
 * searches, not these.
 * User path: Master record ▸ Billing ▸ Invoice List ▸ Edit (the adjust bill page) ▸ the service code's Search/Update
 * (billing/CA/BC/support/BillingFeeItem, a popup listing the BC fee items) ▸ a code; Dx 1 (billing/CA/BC/
 * ViewBillingDigNewSearch, a popup listing the diagnostic codes) ▸ tick one ▸ Confirm; then Reprocess Bill.
 * Asserted:
 *   - the owned claim's Adjust Bill page opens from the invoice list;
 *   - the fee item search lists the code typed into the service code, and picking it fills the service code and
 *     the line's fee with that code's current BC fee;
 *   - the diagnosis search lists the codes for a word typed into Dx 1, and Confirm fills Dx 1 with the ticked code;
 *   - LAST: Reprocess Bill stores the picked service code, its fee and the diagnosis on the claim.
 * Neither search's own "update" is pressed: the diagnosis search's per-code update edits the clinic's code list.
 * Expected to FAIL on release/2026.08 at STEP.fee until #4995 (#4990's release fix) merges: the fee item popup's pick
 * ends in an uncaught "TypeError: opener.document.focus is not a function" (notes only, no pin).
 * Fixtures: one open BC claim (billing and billingmaster) for the owned FAKE patient; the fee code and the
 * diagnosis are existing BC reference rows picked by SQL, so nothing clinic-wide is written. Cleanup deletes the
 * patient's billingmaster, billing_history, billingnote and billing rows and asserts them gone. BC installs only.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { expectValue, runWorkflow } = require('./lib/workflow-session');
const { masterRecordLink } = require('./lib/master-record-link');

const TIMEOUT = 30000;
/** The fixture claim's own code and diagnosis; the searches must pick something else. */
const FIXTURE_CODE = '00100';
const FIXTURE_DX = '250';

/** The step labels; one place, so the script and the manifest agree. */
const STEP = {
  open: 'the owned claim\'s Adjust Bill page opens from the invoice list',
  fee: 'the fee item search lists the typed code, and picking it fills the service code and its fee',
  dx: 'the diagnosis search lists the codes for a typed word, and Confirm fills Dx 1 with the ticked code',
  save: 'Reprocess Bill stores the picked service code, its fee and the diagnosis on the claim',
};

/** A BC fee code other than the fixture's, with one positive current fee row, and that fee as the search shows it. */
function feeTarget(sql) {
  const row = sql.rows(`SELECT b.service_code, b.value FROM billingservice b
    WHERE b.region='BC' AND b.service_code REGEXP '^[0-9]{5}$' AND b.service_code<>${h.sqlString(FIXTURE_CODE)}
      AND b.value REGEXP '^[0-9]+(\\\\.[0-9]+)?$' AND b.value+0 > 0
      AND b.billingservice_date = (SELECT MAX(b2.billingservice_date) FROM billingservice b2
        WHERE b2.region='BC' AND b2.service_code=b.service_code AND b2.billingservice_date <= CURDATE())
      AND (SELECT COUNT(*) FROM billingservice b3 WHERE b3.region='BC' AND b3.service_code=b.service_code
        AND b3.billingservice_date=b.billingservice_date) = 1
    ORDER BY b.service_code LIMIT 1`)[0];
  return row && { code: row[0], fee: row[1] };
}

/** A diagnostic code other than the fixture's, listed once, and a word of its description to search by. */
function dxTarget(sql) {
  for (const [code, description] of sql.rows(`SELECT d.diagnostic_code, d.description FROM diagnosticcode d
    WHERE d.diagnostic_code REGEXP '^[0-9]{3,5}$' AND d.diagnostic_code<>${h.sqlString(FIXTURE_DX)}
      AND (SELECT COUNT(*) FROM diagnosticcode d2 WHERE d2.diagnostic_code=d.diagnostic_code) = 1
    ORDER BY d.diagnostic_code LIMIT 200`)) {
    const word = (description.match(/[A-Za-z]{6,}/) || [])[0];
    if (word) return { code, word };
  }
  return null;
}

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const q = h.sqlString;
  let billingmasterNo;

  s.cleanup(() => {
    // Keyed by the owned patient: the cleanup must still find the rows when an insert succeeded and the id read
    // that followed it was lost.
    const owned = sql.rows(`SELECT billingmaster_no FROM billingmaster WHERE demographic_no=${patient}`)
      .map(([id]) => id).filter((id) => /^\d+$/.test(id));
    if (owned.length) {
      const list = owned.join(',');
      sql.execute([
        `DELETE FROM billing_history WHERE billingmaster_no IN (${list})`,
        `DELETE FROM billingnote WHERE billingmaster_no IN (${list})`,
        `DELETE FROM billingmaster WHERE billingmaster_no IN (${list}) AND demographic_no=${patient}`,
      ].join(';'));
    }
    sql.execute(`DELETE FROM billing WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM billing WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM billingmaster WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM billingnote WHERE billingmaster_no IN (${owned.length ? owned.join(',') : 0}))
      + (SELECT COUNT(*) FROM billing_history WHERE billingmaster_no IN (${owned.length ? owned.join(',') : 0}))`) === '0',
    'Owned BC bill rows were not removed');
  });

  const fee = feeTarget(sql);
  if (!fee) throw new h.SkipCheck('No BC fee code with a current positive fee to pick');
  const dx = dxTarget(sql);
  if (!dx) throw new h.SkipCheck('No diagnostic code with a searchable description to pick');

  // An open BC claim for the owned patient, shaped like one the BC billing form writes.
  sql.execute(`UPDATE demographic SET hc_type='BC', hin='9${String(patient).padStart(9, '0').slice(-9)}', ver=''
    WHERE demographic_no=${patient} AND last_name=${q(marker)}`);
  const ohip = sql.value(`SELECT IFNULL(NULLIF(ohip_no,''),'00000') FROM provider WHERE provider_no=${q(provider)}`);
  const billingNo = sql.value(`INSERT INTO billing (clinic_no,demographic_no,provider_no,appointment_no,demographic_name,
      billing_date,billing_time,update_date,update_time,status,provider_ohip_no,billingtype,creator,total)
    VALUES (0,${patient},${q(provider)},0,${q(`${marker},Workflow`)},CURDATE(),CURTIME(),CURDATE(),CURTIME(),
      'O',${q(ohip)},'MSP',${q(provider)},'23.00'); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(billingNo), 'The BC billing fixture was not created');
  billingmasterNo = sql.value(`INSERT INTO billingmaster (billing_no,createdate,billingstatus,demographic_no,appointment_no,
      claimcode,datacenter,payee_no,practitioner_no,phn,name_verify,dependent_num,billing_unit,clarification_code,
      anatomical_area,after_hour,new_program,billing_code,bill_amount,payment_mode,service_date,service_to_day,
      submission_code,extended_submission_code,dx_code1,service_location,referral_flag1,referral_flag2,birth_date,
      correspondence_code,claim_comment,mva_claim_code,paymentMethod)
    VALUES (${billingNo},NOW(),'O',${patient},0,'C02','00000',${q(ohip.slice(0, 5))},${q(ohip.slice(0, 5))},
      (SELECT hin FROM demographic WHERE demographic_no=${patient}),'WOR','00','1','00','00','0','00',
      ${q(FIXTURE_CODE)},'23.00','0',DATE_FORMAT(CURDATE(),'%Y%m%d'),'00','0',' ',${q(FIXTURE_DX)},'A','0','0',
      '19800102','0','','N',6);
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(billingmasterNo), 'The BC billingmaster fixture was not created');
  const form = adjust => adjust.locator('form[name="reprocessBilling"]');

  let adjust;
  await s.step(STEP.open, async () => {
    const link = masterRecordLink(s.master, '/billing/CA/BC/reprocessBill?').first();
    h.assert(await link.count() === 1, 'The BC master record has no Invoice List link');
    // Click the visible words: on BC the inline eligibility menu widens the navigation cell under the content pane,
    // so the link's centre is covered (app-findings-log.md finding 146).
    const billStatus = await ui.clickOpensPopup(s.master, link, { context: s.context, recorder: s.recorder,
      label: 'bc-bill-status', timeout: 20000, position: { x: 8, y: 8 } });
    await billStatus.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(billStatus, 'BC Bill Status');
    const edit = billStatus.locator(`a[href*="billingmaster_no=${billingmasterNo}&"]`);
    h.assert(await edit.count() === 1, 'Bill Status did not list the owned claim with its Edit link');
    adjust = await s.popup(billStatus, edit, 'bc-adjust-bill');
    await adjust.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(adjust, 'BC adjust bill page');
    h.assert(await form(adjust).locator(`input[name="billingmasterNo"][value="${billingmasterNo}"]`).count() === 1,
      'The adjust page is not for the owned claim');
    h.assert(await form(adjust).locator('input[name="service_code"]').inputValue() === FIXTURE_CODE,
      'The adjust page does not show the claim\'s service code');
  });

  await s.step(STEP.fee, async () => {
    await form(adjust).locator('input[name="service_code"]').fill(fee.code);
    const search = form(adjust).locator('input[type="button"][onclick*="popFeeItemList(\'reprocessBilling\',\'service_code\')"]');
    h.assert(await search.count() === 1, 'The service code has no Search/Update button');
    const list = await s.popup(adjust, search, 'bc-fee-items');
    await list.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(list, 'BC fee item list');
    // nosemgrep: javascript.lang.security.audit.detect-non-literal-regexp.detect-non-literal-regexp -- feeTarget selects only a five-digit service code (REGEXP '^[0-9]{5}$')
    const pick = list.locator('a[onclick^="updateFeeCodeValues("]').filter({ hasText: new RegExp(`^\\s*${fee.code}\\s*$`) });
    h.assert(await pick.count() === 1, `The fee item list does not list ${fee.code} once`);
    await Promise.all([list.waitForEvent('close', { timeout: TIMEOUT }), pick.click()]);
    h.assert(await form(adjust).locator('input[name="service_code"]').inputValue() === fee.code,
      'Picking the fee item did not fill the service code');
    h.assert(await form(adjust).locator('input[name="billingAmount"]').inputValue() === fee.fee,
      `Picking the fee item filled the fee with ${await form(adjust).locator('input[name="billingAmount"]').inputValue()}, not ${fee.fee}`);
  });

  await s.step(STEP.dx, async () => {
    await form(adjust).locator('input[name="dx1"]').fill(dx.word);
    const open = form(adjust).locator('a[href="javascript:ScriptAttach(\'dx1\')"]');
    h.assert(await open.count() === 1, 'Dx 1 has no search link');
    const search = await s.popup(adjust, open, 'bc-dx-search');
    await search.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(search, 'BC diagnosis search');
    const box = search.locator(`form#servicecode input[type="checkbox"][name="code_${dx.code}"]`);
    h.assert(await box.count() === 1, `The diagnosis search for "${dx.word}" does not list ${dx.code}`);
    h.assert(await search.locator('form#servicecode input[type="checkbox"]:checked').count() === 0,
      'The diagnosis search ticked a code before any choice');
    await box.check();
    await Promise.all([
      search.waitForEvent('close', { timeout: TIMEOUT }),
      search.locator('form#servicecode input[type="submit"][name="update"][value="Confirm"]').click(),
    ]);
    h.assert(await form(adjust).locator('input[name="dx1"]').inputValue() === dx.code,
      `Confirm filled Dx 1 with "${await form(adjust).locator('input[name="dx1"]').inputValue()}", not ${dx.code}`);
  });

  await s.step(STEP.save, async () => {
    const reprocess = form(adjust).locator('button[name="submit"][value="Reprocess Bill"]');
    h.assert(await reprocess.count() === 1, 'The adjust page has no Reprocess Bill button');
    const posted = adjust.waitForRequest(request => request.method() === 'POST'
      && new URL(request.url()).pathname.endsWith('/billing/CA/BC/reprocessBill'), { timeout: TIMEOUT });
    await ui.clickAndAwaitReload(adjust, reprocess, { label: 'Reprocess Bill', timeout: TIMEOUT });
    await posted;
    await h.assertNotErrorPage(adjust, 'BC reprocess save result');
    await expectValue(sql, `SELECT CONCAT_WS('|', billing_code, bill_amount, dx_code1) FROM billingmaster
      WHERE billingmaster_no=${billingmasterNo}`, `${fee.code}|${Number(fee.fee).toFixed(2)}|${dx.code}`,
    'Reprocess Bill did not store the picked code, fee and diagnosis');
  });
}

async function preflight({ sql }) {
  if (sql.value("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='billingmaster'") !== '1') {
    throw new h.SkipCheck('BC billing schema is required (billregion=BC)');
  }
}

if (require.main === module) runWorkflow('billing-bc-adjust-bill-code-search', workflow, { openPatient: true, preflight });
module.exports = { workflow, STEP };
