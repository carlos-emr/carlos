#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * BC MSP bill entry: the bill form, its diagnosis and fee-code searches, the review, and Save Bill.
 *
 * This is the daily BC billing path (billing ▸ billing/CA/BC/billingSetup ▸ billingBC.jsp ▸
 * CreateBilling ▸ billingCreated.jsp ▸ SaveBilling), and no browser check drove it: the BC checks
 * covered code associations, the Teleplan simulation report and the adjust-bill page only.
 *
 * User path: Master record ▸ Create Invoice (the BC bill form, popup) ▸ billing physician, bill type
 * MSP ▸ diagnosis search (type, magnifier, pick the result) ▸ fee-code search (type, Search code;
 * the popup fills the code) ▸ units ▸ Continue (the review) ▸ Save Bill (the popup closes).
 *
 * Asserted: the form opens for the owned patient and lists the owned billing physician; the
 * diagnosis search finds the owned diagnostic code by its description and fills the bare code; the
 * fee-code search finds the owned fee code by its description and fills the code; Continue shows
 * the review with that code and its fee; a GET to Save Bill is refused with 405 and writes nothing;
 * Save Bill writes one MSP bill (billing header, status O) and one billingmaster line with the code,
 * units, fee, diagnosis, PHN, the physician's practitioner number, today's service date and status O.
 *
 * Finding 281 (#4716): billingBC.jsp read a session form nothing sets any more and called it
 * unguarded, so the BC bill form answered HTTP 500 on every open (confirmed live on develop
 * 2026-10-15). It is fixed on release/2026.08 by #4723, so no step is expected to fail there.
 *
 * Fixtures: one billing physician (a clone of the test login's provider with a FAKE practitioner
 * and payee number, last name = the run marker), one BC fee code (billingservice, 9xxxx, 12.34) and
 * one diagnostic code (diagnosticcode, Zxxxx), both described by the run marker, for the workflow's
 * own FAKE patient made a BC patient. Cleanup deletes every BC billing row of that patient, then the
 * codes and the physician, and asserts each is gone. BC installs only (billregion=BC).
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { expectValue, runWorkflow } = require('./lib/workflow-session');
const bc = require('./lib/bc-billing-fixture');
const { masterRecordLink } = require('./lib/master-record-link');

const TIMEOUT = 30000;
const FEE = '12.34';

/** One BC fee code and one diagnostic code, each described by the run marker so a search finds only it. */
function createCodes(s) {
  const { sql, marker } = s;
  const q = h.sqlString;
  const codes = {};
  s.cleanup(() => {
    if (codes.fee) sql.execute(`DELETE FROM billingservice WHERE service_code=${q(codes.fee)} AND description=${q(`${marker} fee`)}`);
    if (codes.dx) sql.execute(`DELETE FROM diagnosticcode WHERE diagnostic_code=${q(codes.dx)} AND description=${q(`${marker} diagnosis`)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM billingservice WHERE description LIKE ${q(`${marker}%`)})
      + (SELECT COUNT(*) FROM diagnosticcode WHERE description LIKE ${q(`${marker}%`)})`) === '0', 'The owned fee or diagnostic code was not removed');
  });
  codes.fee = bc.unusedNumber('9', 5, c => sql.value(`SELECT COUNT(*) FROM billingservice WHERE service_code=${q(c)}`) !== '0');
  sql.execute(`INSERT INTO billingservice (service_code,description,value,percentage,billingservice_date,specialty,region,anaesthesia)
    VALUES (${q(codes.fee)},${q(`${marker} fee`)},${q(FEE)},'',DATE_SUB(CURDATE(),INTERVAL 1 YEAR),'','BC','00')`);
  codes.dx = bc.unusedNumber('Z', 5, c => sql.value(`SELECT COUNT(*) FROM diagnosticcode WHERE diagnostic_code=${q(c)}`) !== '0');
  sql.execute(`INSERT INTO diagnosticcode (diagnostic_code,description,status,region)
    VALUES (${q(codes.dx)},${q(`${marker} diagnosis`)},'A','BC')`);
  return codes;
}

async function workflow(s) {
  const { sql, marker, patient } = s;
  const q = h.sqlString;
  const physician = bc.createBillingPhysician(s);
  const codes = createCodes(s);
  const phn = bc.makeBcPatient(s);
  bc.registerOwnedBcBillCleanup(s);
  const billRows = () => sql.value(`SELECT COUNT(*) FROM billing WHERE demographic_no=${patient}`);

  let form;
  await s.step('Create Invoice opens the BC bill form for the owned patient with the owned billing physician', async () => {
    const link = masterRecordLink(s.master, '/billing?billRegion=', 'appointment_no=0&').first();
    h.assert(await link.count() === 1, 'The master record has no Create Invoice link');
    form = await s.popup(s.master, link, 'bc-bill-form');
    await form.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(form, 'BC bill form');
    h.assert(await form.locator('form[action$="/billing/CA/BC/CreateBilling"]').count() === 1, 'Create Invoice did not open the BC bill form');
    h.assert(await form.locator(`#xml_provider option[value="${physician.providerNo}"]`).count() === 1,
      'The bill form does not list the owned billing physician');
    // Each change reloads parts of the form in place; wait for them, or Continue abandons one.
    await bc.changeBillFormSelect(form, '#xml_provider', physician.providerNo, 2, 'choosing the billing physician');
    if (await form.locator('#xml_billtype').inputValue() !== 'MSP') {
      await bc.changeBillFormSelect(form, '#xml_billtype', 'MSP', 1, 'choosing the MSP bill type');
    }
  });

  // Fee code first, then the diagnosis, as the form is used: picking a fee code writes that code's
  // associated diagnoses into Dx 1-3 (billingCodeNewSearch.jsp CodeAttach), blanks when it has none.
  await s.step('the fee-code search finds the owned code by its description and fills it in', async () => {
    const input = form.locator('form[action$="/billing/CA/BC/CreateBilling"] input[name="xml_other1"]');
    await input.fill(`${marker} fee`);
    // One match: the popup fills the form and closes itself, at times before the popup helper has
    // finished waiting on it. Several: pick ours and Confirm.
    let search = null;
    try {
      search = await s.popup(form, form.locator(`button[onclick="OtherScriptAttach('xml_other1')"]`), 'bc-fee-code-search');
    } catch (error) {
      if (!/closed/i.test(String(error && error.message))) throw error;
    }
    if (search && !search.isClosed()) {
      await Promise.race([search.waitForEvent('close', { timeout: TIMEOUT }), search.waitForLoadState('load', { timeout: TIMEOUT })]);
    }
    if (search && !search.isClosed()) {
      const pick = search.locator(`input[name="code_${codes.fee}"]`);
      if (await pick.count() === 1) {
        await pick.check();
        await Promise.all([search.waitForEvent('close', { timeout: TIMEOUT }),
          search.locator('input[type="button"][value="Confirm"], input[type="submit"][value="Confirm"]').first().click({ noWaitAfter: true })]);
      } else {
        await search.waitForEvent('close', { timeout: TIMEOUT });
      }
    }
    h.assert(await input.inputValue() === codes.fee, `The fee-code search filled "${await input.inputValue()}", not ${codes.fee}`);
    await form.locator('#xml_other1_unit').fill('1');
  });

  await s.step('the diagnosis search finds the owned code by its description and fills the bare code', async () => {
    const input = form.locator('#jsonDxSearchInput-1');
    await input.fill(`${marker} diag`);
    // Typing starts the autocomplete's own lookup; let it answer before the search button starts
    // another, which would abort it (a cancelled request the strict page check would report).
    await form.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
    await form.locator('.jsonDxSearchButton[value="jsonDxSearchInput-1"]').click();
    const item = form.locator('ul.ui-autocomplete li').filter({ hasText: `${codes.dx}:` });
    await item.first().waitFor({ state: 'visible', timeout: TIMEOUT });
    h.assert(await item.count() === 1, 'The diagnosis search did not list the owned code exactly once');
    await item.first().click();
    h.assert(await input.inputValue() === codes.dx, `The diagnosis search filled "${await input.inputValue()}", not ${codes.dx}`);
  });

  await s.step('Continue shows the review with the owned fee code and its fee', async () => {
    const posted = form.waitForRequest(request => request.method() === 'POST'
      && new URL(request.url()).pathname.endsWith('/billing/CA/BC/CreateBilling'), { timeout: TIMEOUT }).catch(() => null);
    await ui.clickAndAwaitReload(form, form.locator('input[type="submit"][value="Continue"]'), { label: 'Continue', timeout: TIMEOUT });
    h.assert(await posted, 'Continue did not post the bill (the form may have reported a validation error)');
    await h.assertNotErrorPage(form, 'BC bill review');
    h.assert(await form.locator('form[action$="/billing/CA/BC/SaveBilling"]').count() === 1,
      'Continue did not reach the review (the form may have reported a validation error)');
    const text = await form.locator('body').innerText();
    h.assert(text.includes(codes.fee), 'The review does not list the owned fee code');
    h.assert(await form.locator(`input[name="dispPrice+${codes.fee}"]`).inputValue() === FEE, `The review fee is not ${FEE}`);
  });

  await s.step('a GET to Save Bill is refused and writes nothing', async () => {
    const response = await s.context.request.get(h.appUrl(s.config.baseUrl, '/billing/CA/BC/SaveBilling?submit=Save%20Bill'),
      { maxRedirects: 0 });
    h.assert(response.status() === 405, `A GET Save Bill answered ${response.status()}, not 405`);
    h.assert(billRows() === '0', 'A refused GET wrote a bill');
  });

  await s.step('Save Bill writes one MSP bill and its billingmaster line for the owned patient', async () => {
    await Promise.all([form.waitForEvent('close', { timeout: TIMEOUT }),
      form.locator('form[action$="/billing/CA/BC/SaveBilling"] input[type="submit"][value="Save Bill"]').click({ noWaitAfter: true })]);
    await expectValue(sql, `SELECT COUNT(*) FROM billing WHERE demographic_no=${patient}`, '1', 'Save Bill did not write exactly one bill');
    const header = sql.value(`SELECT CONCAT_WS('|',billingtype,status,provider_no) FROM billing WHERE demographic_no=${patient}`);
    h.assert(header === `MSP|O|${physician.providerNo}`, `The bill header is ${header}, not MSP|O|${physician.providerNo}`);
    // billing_unit is text; the save writes "1.0", so it is compared as a number.
    const line = sql.value(`SELECT CONCAT_WS('|',billing_code,CAST(billing_unit AS DECIMAL(5,1)),CAST(bill_amount AS DECIMAL(9,2)),dx_code1,phn,practitioner_no,
        service_date,billingstatus) FROM billingmaster WHERE demographic_no=${patient}`);
    const today = sql.value("SELECT DATE_FORMAT(CURDATE(),'%Y%m%d')");
    const expected = [codes.fee, '1.0', FEE, codes.dx, phn, physician.ohipNo, today, 'O'].join('|');
    h.assert(line === expected, `The billingmaster line is ${line}, not ${expected}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM billing WHERE demographic_no=${patient} AND demographic_name LIKE ${q(`${marker}%`)}`) === '1',
      'The bill is not named for the owned patient');
  });
}

async function preflight({ sql }) {
  bc.requireBcBillingSchema(sql);
}

if (require.main === module) runWorkflow('billing-bc-bill-create-save', workflow, { openPatient: true, preflight });
module.exports = { workflow };
