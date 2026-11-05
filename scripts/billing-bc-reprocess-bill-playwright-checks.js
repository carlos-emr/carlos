#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * BC Bill Status and the adjust bill page (#4373, #4346).
 *
 * Before #4373 every way into billing/CA/BC/reprocessBill was broken: the method guard refused the
 * GET that every menu and Edit link sends, a save hit a NullPointerException, and the billing
 * history archive behind each save never found its bill. #4346 fixed the adjust page's Billing
 * Notes box, which showed scriptlet source instead of the stored note and would have saved it.
 *
 * User path: Master record ▸ Billing ▸ Invoice List (Bill Status filtered to the patient) ▸ Edit
 * (the adjust bill page) ▸ Settle Bill. Asserted: the owned bill is listed; the adjust page opens
 * for it and its Billing Notes box holds exactly the stored note; Settle posts, marks the bill
 * settled and writes one billing_history row; the save keeps the note exactly, apostrophe
 * included; a GET carrying a save parameter is refused with 405 and nothing changes. Direct
 * requests are negative probes only.
 *
 * Expected to FAIL while app-findings-log.md finding 145 stands: the save writes the note back as
 * "patient\'s", one more backslash per save. (Finding 147, the adjust page's TypeError on load,
 * was fixed with #4551.)
 *
 * BC_REPROCESS_PIN=inactive runs the bill of a physician who has left instead (#4422): the same
 * Invoice List and Edit, then Reprocess Bill after a diagnostic code is fixed. The adjust page lists
 * only active physicians, so it posts an empty physician, and the save must still keep the bill's
 * provider, payee and practitioner numbers. Expected to FAIL while finding 153 stands: the save
 * writes '' to all four.
 *
 * Fixtures: one billing + billingmaster + billingnote row for the workflow's own FAKE patient,
 * stamped with the run marker; cleanup deletes exactly those rows (and the history and notes the
 * save wrote for that bill) and asserts they are gone. With the inactive pin, also one owned
 * inactive billing physician (lib/bc-billing-fixture.js), removed and asserted gone. BC installs
 * only (billregion=BC).
 */
const { authenticatedProbe } = require('./lib/authenticated-probe');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { expectValue, runWorkflow } = require('./lib/workflow-session');
const { masterRecordLink } = require('./lib/master-record-link');
const { createBillingPhysician } = require('./lib/bc-billing-fixture');

const TIMEOUT = 30000;

/** The step labels the manifest's expectedFailure entries name; one place, so the script and the manifest agree. */
const STEP = {
  note: 'the save keeps the billing note exactly, apostrophe included',
  inactive: 'Reprocess Bill on the bill of a physician who has left keeps its provider, payee and practitioner numbers',
};

const PINS = ['inactive'];
/**
 * BC_REPROCESS_PIN selects the run: unset is the Settle path (finding 145), inactive the bill of a
 * physician who has left (finding 153). Read when the check runs, never when the module is required.
 */
function validatePin(value = process.env.BC_REPROCESS_PIN) {
  const pin = (value || '').trim();
  if (!['', ...PINS].includes(pin)) throw new Error(`BC_REPROCESS_PIN must be unset or ${PINS.join(', ')}, not ${pin}`);
  return pin;
}

async function workflow(s) {
  const pin = validatePin();
  const { sql, marker, patient, provider } = s;
  const q = h.sqlString;
  // An apostrophe, as in most real notes: every save re-adds the note through
  // UtilMisc.mysqlEscape, which stores "'" as "\'" over a parameterized write.
  const note = `${marker} adjust note: patient's card re-read`;
  let billingNo;
  let billingmasterNo;

  s.cleanup(() => {
    // Keyed by the owned patient, not by the ids captured above: the cleanup must still recover the rows when the
    // INSERT succeeded and the id read that followed it was lost.
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

  // A BC claim for the owned patient, shaped like one the BC billing form writes.
  sql.execute(`UPDATE demographic SET hc_type='BC', hin='9${String(patient).padStart(9, '0').slice(-9)}', ver=''
    WHERE demographic_no=${patient} AND last_name=${q(marker)}`);
  const ohip = sql.value(`SELECT IFNULL(NULLIF(ohip_no,''),'00000') FROM provider WHERE provider_no=${q(provider)}`);
  // Whose bill it is: the test login's, or with the inactive pin a physician who has left.
  let billedBy = { providerNo: provider, ohipNo: ohip, practitionerNo: ohip.slice(0, 5), payeeNo: ohip.slice(0, 5) };
  if (pin === 'inactive') {
    const leaver = createBillingPhysician(s, { status: '0' });
    billedBy = { providerNo: leaver.providerNo, ohipNo: leaver.ohipNo, practitionerNo: leaver.ohipNo, payeeNo: leaver.payeeNo };
  }
  billingNo = sql.value(`INSERT INTO billing (clinic_no,demographic_no,provider_no,appointment_no,demographic_name,
      billing_date,billing_time,update_date,update_time,status,provider_ohip_no,billingtype,creator,total)
    VALUES (0,${patient},${q(billedBy.providerNo)},0,${q(`${marker},Workflow`)},CURDATE(),CURTIME(),CURDATE(),CURTIME(),
      'O',${q(billedBy.ohipNo)},'MSP',${q(provider)},'23.00'); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(billingNo), 'The BC billing fixture was not created');
  billingmasterNo = sql.value(`INSERT INTO billingmaster (billing_no,createdate,billingstatus,demographic_no,appointment_no,
      claimcode,datacenter,payee_no,practitioner_no,phn,name_verify,dependent_num,billing_unit,clarification_code,
      anatomical_area,after_hour,new_program,billing_code,bill_amount,payment_mode,service_date,service_to_day,
      submission_code,extended_submission_code,dx_code1,service_location,referral_flag1,referral_flag2,birth_date,
      correspondence_code,claim_comment,mva_claim_code,paymentMethod)
    VALUES (${billingNo},NOW(),'O',${patient},0,'C02','00000',${q(billedBy.payeeNo)},${q(billedBy.practitionerNo)},
      (SELECT hin FROM demographic WHERE demographic_no=${patient}),'WOR','00','1','00','00','0','00','00100','23.00',
      '0',DATE_FORMAT(CURDATE(),'%Y%m%d'),'00','0',' ','250','A','0','0','19800102','0','','N',6);
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(billingmasterNo), 'The BC billingmaster fixture was not created');
  sql.execute(`INSERT INTO billingnote (billingmaster_no,createdate,provider_no,note,note_type)
    VALUES (${billingmasterNo},NOW(),${q(provider)},${q(note)},2)`);
  const historyRows = () => sql.value(`SELECT COUNT(*) FROM billing_history WHERE billingmaster_no=${billingmasterNo}`);

  let billStatus;
  await s.step('Invoice List on the master record lists the owned bill', async () => {
    const link = masterRecordLink(s.master, '/billing/CA/BC/reprocessBill?').first();
    h.assert(await link.count() === 1, 'The BC master record has no Invoice List link');
    // Click the visible words: on BC the inline eligibility menu widens the navigation cell to
    // 350px under the content pane, so the link's centre is covered (app-findings-log.md finding 146).
    billStatus = await ui.clickOpensPopup(s.master, link, { context: s.context, recorder: s.recorder,
      label: 'bc-bill-status', timeout: 20000, position: { x: 8, y: 8 } });
    await billStatus.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(billStatus, 'BC Bill Status');
    h.assert(new URL(billStatus.url()).pathname.endsWith('/billing/CA/BC/reprocessBill'), 'Invoice List did not open Bill Status');
    const edit = billStatus.locator(`a[href*="billingmaster_no=${billingmasterNo}&"]`);
    h.assert(await edit.count() === 1, 'Bill Status did not list the owned bill with its Edit link');
  });

  let adjust;
  await s.step('Edit opens the adjust bill page with the stored billing note', async () => {
    const edit = billStatus.locator(`a[href*="billingmaster_no=${billingmasterNo}&"]`);
    adjust = await s.popup(billStatus, edit, 'bc-adjust-bill');
    await adjust.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(adjust, 'BC adjust bill page');
    h.assert(await adjust.locator(`form[name="reprocessBilling"] input[name="billingmasterNo"][value="${billingmasterNo}"]`).count() === 1,
      'The adjust page is not for the owned bill');
    // #4346: the box held the scriptlet's source text, which a save would have stored.
    h.assert(await adjust.locator('#messageNotes').inputValue() === note, 'Billing Notes does not hold exactly the stored note');
  });

  if (pin === 'inactive') {
    const stored = () => sql.rows(`SELECT b.provider_no, b.provider_ohip_no, m.payee_no, m.practitioner_no
      FROM billing b JOIN billingmaster m ON m.billing_no=b.billing_no WHERE m.billingmaster_no=${billingmasterNo}`)[0];
    let offered;
    // Everything up to the save, unpinned: a failure here is not finding 153.
    await s.step('Reprocess Bill posts the claim with a fixed diagnostic code', async () => {
      h.assert(stored().join('|') === [billedBy.providerNo, billedBy.ohipNo, billedBy.payeeNo,
        billedBy.practitionerNo].join('|'), 'The owned bill does not hold the physician who has left');
      const form = adjust.locator('form[name="reprocessBilling"]');
      // What the physician select offers for this bill: today nothing, as it lists active physicians only.
      offered = await form.locator('#providerNo').inputValue();
      // The path in #4422: a rejected claim whose diagnostic code is fixed, then Reprocess Bill.
      const dx = sql.value(`SELECT diagnostic_code FROM diagnosticcode WHERE region='BC' AND diagnostic_code<>'250'
        AND diagnostic_code REGEXP '^[0-9]{3}$' ORDER BY diagnostic_code LIMIT 1`);
      h.assert(/^\d{3}$/.test(dx), 'No other three-digit BC diagnostic code to fix the claim with');
      await form.locator('input[name="dx1"]').fill(dx);
      const reprocess = form.locator('button[name="submit"][value="Reprocess Bill"]');
      h.assert(await reprocess.count() === 1, 'The adjust page has no Reprocess Bill button');
      const historyBefore = Number(historyRows());
      h.assert(Number.isSafeInteger(historyBefore), 'The owned bill history count is not an integer');
      const posted = adjust.waitForResponse(response => response.request().method() === 'POST'
        && new URL(response.url()).pathname.endsWith('/billing/CA/BC/reprocessBill'), { timeout: TIMEOUT });
      await ui.clickAndAwaitReload(adjust, reprocess, { label: 'Reprocess Bill', timeout: TIMEOUT });
      h.assert((await posted).status() === 200, 'Reprocess Bill did not answer 200');
      await h.assertNotErrorPage(adjust, 'BC reprocess save result');
      await expectValue(sql, `SELECT dx_code1 FROM billingmaster WHERE billingmaster_no=${billingmasterNo}`, dx,
        'Reprocess Bill did not store the chosen diagnostic code');
      h.assert(Number(historyRows()) === historyBefore + 1,
        'Reprocess Bill did not add exactly one owned billing_history row');
    });
    await s.step(STEP.inactive, async () => {
      const [providerNo, providerOhipNo, payeeNo, practitionerNo] = stored();
      h.assert(providerNo === billedBy.providerNo && providerOhipNo === billedBy.ohipNo
        && payeeNo === billedBy.payeeNo && practitionerNo === billedBy.practitionerNo,
      `Reprocess Bill (physician select ${JSON.stringify(offered)}) left provider_no ${JSON.stringify(providerNo)}, `
        + `provider_ohip_no ${JSON.stringify(providerOhipNo)}, payee_no ${JSON.stringify(payeeNo)} and practitioner_no `
        + `${JSON.stringify(practitionerNo)}, not the bill's physician (${billedBy.providerNo}, ${billedBy.ohipNo}, `
        + `${billedBy.payeeNo}, ${billedBy.practitionerNo})`);
    });
    return;
  }

  await s.step('a GET that carries a save parameter is refused and changes nothing', async () => {
    const response = await authenticatedProbe(s, h.appUrl(s.config.baseUrl,
      `/billing/CA/BC/reprocessBill?billingmasterNo=${billingmasterNo}&submit=Settle%20Bill`), { maxRedirects: 0 });
    h.assert(response.status() === 405, `A GET save answered ${response.status()}, not 405`);
    h.assert(sql.value(`SELECT billingstatus FROM billingmaster WHERE billingmaster_no=${billingmasterNo}`) === 'O',
      'A refused GET changed the bill');
    h.assert(historyRows() === '0', 'A refused GET wrote billing history');
  });

  await s.step('Settle Bill posts, settles the bill and archives its history', async () => {
    const settle = adjust.locator('form[name="reprocessBilling"] button[name="submit"][value="Settle Bill"]');
    h.assert(await settle.count() === 1, 'The adjust page has no Settle Bill button');
    const posted = adjust.waitForRequest(request => request.method() === 'POST'
      && new URL(request.url()).pathname.endsWith('/billing/CA/BC/reprocessBill'), { timeout: TIMEOUT });
    await ui.clickAndAwaitReload(adjust, settle, { label: 'Settle Bill', timeout: TIMEOUT });
    await posted;
    await h.assertNotErrorPage(adjust, 'BC reprocess save result');
    await expectValue(sql, `SELECT billingstatus FROM billingmaster WHERE billingmaster_no=${billingmasterNo}`, 'S',
      'Settle Bill did not settle the bill');
    // #4373: the archive's query never found its bill, so every save failed here.
    h.assert(historyRows() === '1', 'Settle Bill did not write exactly one billing_history row');
  });

  await s.step(STEP.note, async () => {
    const saved = sql.value(`SELECT note FROM billingnote WHERE billingmaster_no=${billingmasterNo} AND note_type=2
      ORDER BY billingnote_no DESC LIMIT 1`);
    h.assert(saved === note, `The save changed the billing note: ${JSON.stringify(saved.slice(marker.length))} `
      + `instead of ${JSON.stringify(note.slice(marker.length))}`);
  });
}

async function preflight({ sql }) {
  if (sql.value("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='billingmaster'") !== '1') {
    throw new h.SkipCheck('BC billing schema is required (billregion=BC)');
  }
}

if (require.main === module) runWorkflow('billing-bc-reprocess-bill', workflow, { openPatient: true, preflight });
module.exports = { workflow, validatePin, STEP };
