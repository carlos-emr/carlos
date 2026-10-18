#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * The BC WCB form: opened from the bill form, filled in, saved, opened again, and billed (#4439 item 5).
 * billing-bc-two-window-isolation bills WCB with a form it seeds; nothing opened the WCB form page itself.
 * User path: the master record's Create Invoice (the BC bill form, billing ▸ billing/CA/BC/billingSetup) ▸ the
 * owned billing physician ▸ Billing Type WCB (the patient's WCB forms, billing/CA/BC/ViewWcbForms, load into the
 * form) ▸ New WCB Form (a popup: billing/CA/BC/viewformwcb ▸ formwcb.jsp) ▸ Save and Close (POST
 * billing/CA/BC/formwcb ▸ wcbClose.jsp, which reloads the bill form's list with the new form chosen and closes).
 * Asserted:
 *   - New WCB Form opens the form for this patient and the chosen billing physician: the patient's name, PHN and
 *     birth date, no postal code (the patient has none), the physician's practitioner and payee numbers, the
 *     patient's number in the bill's hidden field, a first report and Form Needed ticked;
 *   - Save and Close stores the form as filled in (the report type chosen, Form Needed, the employer, the
 *     diagnosis), and the bill form's list then offers it, chosen;
 *   - the list's date link opens the saved form again with those values;
 *   - Continue as a WCB bill with a code that needs a complete WCB report (19937) and that form reaches the bill
 *     review (warnings ignored, as the WCB codes are not in a bare install's fee list);
 *   - LAST: with an incomplete WCB form instead, Continue returns to the bill form saying what the form lacks,
 *     once (a reload shows it no more).
 * Fixtures: the owned FAKE- patient made a BC patient with a street address and city and no postal code, one
 * owned billing physician (bc.createBillingPhysician), and one incomplete WCB form for the patient (no employer,
 * no clinical details). Cleanup deletes the patient's wcb rows and any BC billing rows, then the physician, and
 * asserts each is gone. No bill is saved: the check stops at the review.
 */
const h = require('./lib/playwright-harness');
const bc = require('./lib/bc-billing-fixture');
const { runWorkflow } = require('./lib/workflow-session');
const { masterRecordLink } = require('./lib/master-record-link');

const TIMEOUT = 30000;
/** A WCB service code that needs a complete WCB report (WCBCodes' default list). */
const WCB_FORM_CODE = '19937';
const DX = '780';

/** The step labels; one place, so the script and the manifest agree. */
const STEP = {
  open: 'Bill WCB ▸ New WCB Form opens the patient\'s WCB form, prefilled for the patient and the billing physician',
  save: 'Save and Close saves the form as filled in, and the bill form\'s WCB list offers it, chosen',
  reopen: 'the list\'s date link opens the saved form again with its values',
  bill: 'Continue as a WCB bill with a code that needs the report and the saved form reaches the review',
  refused: 'a WCB Continue with an incomplete form returns to the bill form saying what the form lacks, once',
};

async function preflight({ sql }) {
  bc.requireBcBillingSchema(sql);
}

/** The BC bill form for the owned patient, its billing physician chosen and Billing Type WCB (the list loaded). */
async function openWcbBill(s, physician) {
  const link = masterRecordLink(s.master, '/billing?billRegion=', 'appointment_no=0&').first();
  h.assert(await link.count() === 1, 'The master record has no Create Invoice link');
  const form = await s.popup(s.master, link, 'bc-bill-form');
  await form.waitForLoadState('domcontentloaded');
  await h.assertNotErrorPage(form, 'BC bill form');
  await bc.changeBillFormSelect(form, '#xml_provider', physician.providerNo, 2, 'choosing the billing physician');
  const [listed] = await Promise.all([
    form.waitForResponse(r => new URL(r.url()).pathname.endsWith('/billing/CA/BC/ViewWcbForms'), { timeout: TIMEOUT }),
    form.locator('#xml_billtype').selectOption('WCB'),
  ]);
  h.assert(listed.status() === 200, `The patient's WCB form list answered HTTP ${listed.status()}`);
  await form.waitForFunction(() => !window.jQuery || window.jQuery.active === 0, null, { timeout: TIMEOUT });
  return form;
}

/** Continue on the bill form with one code; answers the Continue's POST response. */
async function continueBill(form, { code, ignoreWarnings }) {
  await form.locator('input[name="xml_other1"]').fill(code);
  await form.locator('input[name="xml_other1_unit"]').fill('1');
  // The form's own validation needs a diagnostic code that exists (it checks it remotely) before it posts.
  await form.locator('input[name="xml_diagnostic_detail1"]').fill(DX);
  await form.locator('#ignoreWarn').setChecked(ignoreWarnings);
  const [posted] = await Promise.all([
    form.waitForResponse(r => r.request().method() === 'POST'
      && new URL(r.url()).pathname.endsWith('/billing/CA/BC/CreateBilling'), { timeout: TIMEOUT }),
    form.locator('#buttonRow input[type="submit"][value="Continue"]').click(),
  ]);
  return posted;
}

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const q = h.sqlString;
  const wcbRows = () => sql.rows(`SELECT ID, w_reporttype, formNeeded, w_empname, w_icd9, w_feeitem, w_diagnosis, status,
      w_pracno, w_payeeno, w_lname FROM wcb WHERE demographic_no=${patient} ORDER BY ID`);
  s.cleanup(() => {
    sql.execute(`DELETE FROM wcb WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM wcb WHERE demographic_no=${patient}`) === '0', 'The owned WCB forms were not removed');
  });
  const physician = bc.createBillingPhysician(s);
  const phn = bc.makeBcPatient(s);
  bc.registerOwnedBcBillCleanup(s);
  sql.execute(`UPDATE demographic SET address='1 FAKE Street', city='Vancouver', postal=NULL
    WHERE demographic_no=${patient} AND last_name=${q(marker)}`);
  const incomplete = h.insertId(sql, `INSERT INTO wcb (demographic_no, provider_no, formCreated, formEdited, w_reporttype,
      w_fname, w_lname, w_dob, w_city, w_phn, w_servicedate, w_doi, status, w_feeitem, w_extrafeeitem, w_duration, formNeeded)
    VALUES (${patient}, ${q(provider)}, NOW(), NOW(), 'F', 'Wcb', ${q(marker.slice(0, 18))}, '1975-03-04', 'Vancouver',
      ${q(phn)}, CURDATE(), CURDATE(), 'O', ${q(WCB_FORM_CODE)}, '', 1, 1)`, 'the incomplete WCB form');

  let form;
  let wcb;
  await s.step(STEP.open, async () => {
    // The form page's first open compiles a large JSP, which on a busy server outlasts the popup's wait; a plain
    // request for the same page first, so the popup's wait measures the page rather than the compile.
    await s.context.request.get(h.appUrl(s.config.baseUrl,
      `/billing/CA/BC/viewformwcb?demographic_no=${patient}&formId=0&provNo=${physician.providerNo}&hideToBill=true`),
      { timeout: 120000 });
    form = await openWcbBill(s, physician);
    wcb = await s.popup(form, form.locator('#wcbForms a', { hasText: 'New WCB Form' }), 'wcb-form');
    await wcb.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(wcb, 'WCB form');
    const url = new URL(wcb.url());
    h.assert(url.pathname.endsWith('/billing/CA/BC/viewformwcb'), `New WCB Form opened ${url.pathname}`);
    h.assert(url.searchParams.get('provNo') === physician.providerNo,
      `New WCB Form named provider ${url.searchParams.get('provNo')}, not the chosen billing physician`);
    h.assert(await wcb.evaluate(() => !!document.WCBForm && document.WCBForm.tagName === 'FORM'),
      'The WCB form page has no form named WCBForm, which its scripts use');
    const value = name => wcb.locator(`[name="${name}"]`).first().inputValue();
    h.assert(await value('w_lname') === marker, `The form's last name is "${await value('w_lname')}"`);
    h.assert(await value('w_phn') === phn, 'The form does not carry the patient\'s PHN');
    h.assert(/^\d{4}-\d{2}-\d{2}$/.test(await value('w_dob')), `The form's birth date is "${await value('w_dob')}"`);
    h.assert(await value('w_postal') === '', `The form's postal code is "${await value('w_postal')}"`);
    h.assert(await value('w_pracno') === physician.ohipNo, 'The form does not carry the physician\'s practitioner number');
    h.assert(await value('w_payeeno') === physician.payeeNo, 'The form does not carry the physician\'s payee number');
    h.assert(await value('demographic_no') === String(patient), 'The form does not carry the patient\'s number');
    h.assert(await wcb.locator('input[name="w_reporttype"][value="F"]').isChecked(), 'A new form is not a first report');
    h.assert(await wcb.locator('input[name="formNeeded"]').isChecked(), 'A new form does not have Form Needed ticked');
  });

  let saved;
  await s.step(STEP.save, async () => {
    await wcb.locator('input[name="w_reporttype"][value="C"]').check();
    for (const [name, text] of [['w_empname', 'FAKE-PW Employer'], ['w_opaddress', '1 FAKE Way'], ['w_opcity', 'Vancouver'],
      ['w_emparea', '604'], ['w_empphone', '5550199'], ['w_diagnosis', 'FAKE diagnosis'], ['w_icd9', DX],
      ['w_feeitem', WCB_FORM_CODE]]) {
      await wcb.locator(`input[name="${name}"]`).fill(text);
    }
    await wcb.locator('#w_problem').fill('FAKE prior problem');
    await wcb.locator('#w_clinicinfo').fill('FAKE clinical information');
    await wcb.locator('#w_doi').evaluate((input) => { input.value = new Date().toISOString().slice(0, 10); });
    const before = wcbRows().length;
    const [replaced, posted] = await Promise.all([
      form.waitForResponse(r => new URL(r.url()).pathname.endsWith('/billing/CA/BC/ViewWcbForms')
        && new URL(r.url()).searchParams.has('wcbid'), { timeout: TIMEOUT }),
      wcb.waitForResponse(r => r.request().method() === 'POST'
        && new URL(r.url()).pathname.endsWith('/billing/CA/BC/formwcb'), { timeout: TIMEOUT }),
      wcb.waitForEvent('close', { timeout: TIMEOUT }),
      wcb.locator('input[name="saveAndClose"]').click({ noWaitAfter: true }),
    ]);
    h.assert(posted.status() === 200, `Save and Close answered HTTP ${posted.status()}`);
    h.assert(replaced.status() === 200, `The bill form's WCB list reload answered HTTP ${replaced.status()}`);
    const rows = wcbRows();
    h.assert(rows.length === before + 1, `Save and Close left ${rows.length - before} new WCB forms, not 1`);
    saved = rows.find(row => row[0] !== String(incomplete));
    const [id, reportType, formNeeded, employer, icd9, feeItem, diagnosis, status, pracNo, payeeNo] = saved;
    h.assert(reportType === 'C', `The saved form's report type is "${reportType}", not the C chosen`);
    h.assert(formNeeded === '1', `The saved form's Form Needed is ${formNeeded}, not 1 (ticked)`);
    h.assert(employer === 'FAKE-PW Employer' && icd9 === DX && feeItem === WCB_FORM_CODE && diagnosis === 'FAKE diagnosis',
      `The saved form does not hold the employer, ICD9, fee item and diagnosis entered: ${JSON.stringify(saved)}`);
    h.assert(status === 'W', `The saved form's status is "${status}"`);
    h.assert(pracNo === physician.ohipNo && payeeNo === physician.payeeNo, 'The saved form does not hold the physician\'s numbers');
    await form.waitForFunction(() => !window.jQuery || window.jQuery.active === 0, null, { timeout: TIMEOUT });
    const choice = form.locator(`#wcbForms input[name="WCBid"][value="${id}"]`);
    h.assert(await choice.count() === 1, 'The bill form\'s WCB list does not offer the saved form');
    h.assert(await choice.isChecked(), 'The bill form\'s WCB list does not have the saved form chosen');
  });

  await s.step(STEP.reopen, async () => {
    const view = await s.popup(form, form.locator(`#wcbForms a[onclick*="formId=${saved[0]}&"]`), 'wcb-form-saved');
    await view.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(view, 'saved WCB form');
    h.assert(await view.locator('input[name="w_empname"]').inputValue() === 'FAKE-PW Employer', 'The saved form opens without its employer');
    h.assert(await view.locator('input[name="w_reporttype"][value="C"]').isChecked(), 'The saved form opens without its report type');
    h.assert(await view.locator('input[name="formNeeded"]').isChecked(), 'The saved form opens with Form Needed clear');
    h.assert(await view.locator('#w_problem').inputValue() === 'FAKE prior problem', 'The saved form opens without its problem text');
    await view.close();
  });

  await s.step(STEP.bill, async () => {
    const posted = await continueBill(form, { code: WCB_FORM_CODE, ignoreWarnings: true });
    h.assert(posted.status() === 200, `Continue with the saved form answered HTTP ${posted.status()}`);
    // The review replaces the bill form in place; wait for it rather than for a load state the old page may still hold.
    await form.locator('form[action$="/billing/CA/BC/SaveBilling"]').waitFor({ state: 'attached', timeout: TIMEOUT })
      .catch(() => {});
    await h.assertNotErrorPage(form, 'BC bill review');
    h.assert(await form.locator('form[action$="/billing/CA/BC/SaveBilling"]').count() === 1,
      'Continue with the saved WCB form did not reach the bill review');
    h.assert((await form.locator('body').innerText()).includes(WCB_FORM_CODE), `The review does not list ${WCB_FORM_CODE}`);
    await form.close();
  });

  await s.step(STEP.refused, async () => {
    form = await openWcbBill(s, physician);
    await form.locator(`#wcbForms input[name="WCBid"][value="${incomplete}"]`).check();
    const posted = await continueBill(form, { code: WCB_FORM_CODE, ignoreWarnings: true });
    h.assert(posted.status() === 302, `Continue with the incomplete form answered HTTP ${posted.status()}, not a refusal`);
    await form.waitForURL(url => url.pathname.endsWith('/billing') && url.searchParams.has('billKey'), { timeout: TIMEOUT });
    await form.waitForLoadState('load', { timeout: TIMEOUT });
    const needs = () => form.locator('div', { hasText: /^\s*WCB Form needs:/ }).last().locator('li').allInnerTexts();
    const listed = (await needs()).map(text => text.trim());
    h.assert(listed.some(text => /employer name/i.test(text)) && listed.some(text => /clinic info/i.test(text)),
      `The bill form does not say what the WCB form lacks: ${JSON.stringify(listed)}`);
    await form.reload({ waitUntil: 'load' });
    // An error page would show no list either, so the reload must first be the bill form.
    await h.assertNotErrorPage(form, 'bill form after a reload');
    h.assert(await form.locator('#bcBillingForm').count() === 1, 'The reload did not show the bill form');
    h.assert((await needs()).length === 0, 'The bill form says what the WCB form lacked again after a reload');
    await form.close();
  });
}

if (require.main === module) runWorkflow('billing-bc-wcb-form', workflow, { openPatient: true, preflight });
module.exports = { workflow, preflight, STEP };
