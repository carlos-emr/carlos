#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * BC billing code administration: the service/diagnostic code associations and the MSP billing code list, the code
 * part of #4439 task 9 (billing-bc-codes-admin). Manage Private Bill is billing-bc-private-codes, the procedure/fee
 * (tray) associations billing-bc-associations, and Manage Referral Doctors billing-bc-referral-doctors.
 *
 * User path: Schedule ▸ Administration ▸ Billing ▸ Manage Service/Diagnostic Code Associations (showServiceCodeAssocs,
 * in the #dynamic-content iframe) ▸ the list; the master record's Create Invoice (the BC bill form) ▸ the fee-code
 * search; Manage Billing Codes (ViewBillingCodeAdjust, opened by its address in a tab: its menu item shows only with
 * BC_BILLING_CODE_MANAGEMENT=yes, which no default configuration sets) ▸ Billing Code search ▸ the code's Edit
 * (ViewBillingEditCode) ▸ the fee row's edit, Price, save (POST billingEditCode?method=ajaxCodeUpdate); back in the
 * associations ▸ the owned association's Edit (editServiceCodeAssocAction) ▸ another diagnosis ▸ Save (POST
 * saveAssocAction) ▸ Remove (POST deleteServiceCodeAssoc).
 * Asserted: the list shows the owned association; picking the owned fee code in the bill form's fee-code search fills
 * in its associated diagnosis; the MSP list's search finds the owned code and its Edit saves a new fee (stored, and the
 * row returns to view); the association's Edit shows its service code and diagnosis; saving another diagnosis
 * replaces the association's diagnosis (and the fee-code search then fills that one); Remove deletes it. A last step
 * collects three known defects so one run reports each: Manage Billing Codes ▸ Add Code has no text box for the
 * service code (#4842), Create New Association links back to the list (#4439 D14), and the editor saves a duplicate
 * association it reports as an error (#4439 D13).
 *
 * Fixtures: one MSP fee code (billingservice 9xxxx with an end date, so its fee row's save does not depend on #4842),
 * two diagnostic codes (diagnosticcode Zxxxx), all described by the run marker, and one association of the fee code
 * with the first diagnosis (ctl_servicecodes_dxcodes), for the workflow's own FAKE patient made a BC patient. Cleanup
 * deletes the association rows of the owned fee code and any written during the run for the owned diagnoses (the
 * editor can store one with no service code), then the codes, and asserts each is gone. The codes and the
 * association are visible clinic-wide while the check runs, so it must not run beside other checks.
 *
 * Expected (see the manifest notes): on 2026.08 the first two steps pass and Manage Billing Codes answers 404 until
 * #4778 (issue #4767) merges. With #4778 and #4844 (issue #4842) the MSP steps pass, and the association's Edit fails
 * on #4941: the editor opens with every box empty and a service-code box that cannot be typed in, so its Save stores
 * the diagnosis with no service code. With #4943 (its fix) and #4892 (the editor's stylesheet address) the Edit, Save
 * and Remove steps pass too, and the last step stays red on D14; D13 (fixed by #4943) is reached only through
 * Create New, so it is checked once D14 is fixed.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { masterRecordLink } = require('./lib/master-record-link');
const bc = require('./lib/bc-billing-fixture');

const TIMEOUT = 30000;

/** The step labels the manifest's notes name; one place, so the script and the manifest agree. */
const STEP = {
  list: 'Administration ▸ Billing ▸ Manage Service/Diagnostic Code Associations lists the owned association',
  suggest: 'the bill form\'s fee-code search fills in the owned code\'s associated diagnosis',
  msp: 'Manage Billing Codes finds the owned MSP code by its code',
  fee: 'the MSP code\'s Edit saves a new fee on its fee row',
  edit: 'the association\'s Edit opens the editor with its service code and diagnosis',
  save: 'saving the editor with another diagnosis replaces the association\'s diagnosis',
  remove: 'the association\'s Remove deletes it',
  known: 'Add Code takes a typed service code, Create New Association opens an empty editor, and a duplicate association is refused',
};

/** One MSP fee code, two diagnostic codes and the fee code's association with the first. */
function createCodes(s) {
  const { sql, marker } = s;
  const q = h.sqlString;
  const codes = { dx: [] };
  const assocMark = Number(sql.value('SELECT COALESCE(MAX(id),0) FROM ctl_servicecodes_dxcodes'));
  s.cleanup(() => {
    const dxList = codes.dx.length ? codes.dx.map(q).join(',') : "''";
    if (codes.fee) {
      sql.execute(`DELETE FROM ctl_servicecodes_dxcodes WHERE service_code=${q(codes.fee)} OR (id>${assocMark} AND dxcode IN (${dxList}))`);
      sql.execute(`DELETE FROM billingservice WHERE service_code=${q(codes.fee)} AND description=${q(`${marker} fee`)}`);
    }
    if (codes.dx.length) sql.execute(`DELETE FROM diagnosticcode WHERE diagnostic_code IN (${dxList}) AND description=${q(`${marker} diagnosis`)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM ctl_servicecodes_dxcodes WHERE service_code=${q(codes.fee || '')} OR dxcode IN (${dxList}))
      + (SELECT COUNT(*) FROM billingservice WHERE description LIKE ${q(`${marker}%`)})
      + (SELECT COUNT(*) FROM diagnosticcode WHERE description LIKE ${q(`${marker}%`)})`) === '0', 'The owned codes or associations were not removed');
  });
  // Five digits, the most the list's search box takes; no other code starts with it, so the search finds only ours.
  codes.fee = bc.unusedNumber('9', 5, c => sql.value(`SELECT (SELECT COUNT(*) FROM billingservice WHERE service_code LIKE ${q(`${c}%`)})
    + (SELECT COUNT(*) FROM ctl_servicecodes_dxcodes WHERE service_code=${q(c)})`) !== '0');
  sql.execute(`INSERT INTO billingservice (service_code,description,value,percentage,billingservice_date,termination_date,specialty,region,anaesthesia)
    VALUES (${q(codes.fee)},${q(`${marker} fee`)},'12.34','',DATE_SUB(CURDATE(),INTERVAL 1 YEAR),'9999-12-31','','BC','00')`);
  codes.feeId = sql.value(`SELECT billingservice_no FROM billingservice WHERE service_code=${q(codes.fee)} AND description=${q(`${marker} fee`)}`);
  h.assert(/^[1-9]\d*$/.test(codes.feeId), 'The owned fee code was not created');
  while (codes.dx.length < 2) {
    const dx = bc.unusedNumber('Z', 5, c => codes.dx.includes(c)
      || sql.value(`SELECT (SELECT COUNT(*) FROM diagnosticcode WHERE diagnostic_code=${q(c)})
        + (SELECT COUNT(*) FROM ctl_servicecodes_dxcodes WHERE dxcode=${q(c)})`) !== '0');
    codes.dx.push(dx);
    sql.execute(`INSERT INTO diagnosticcode (diagnostic_code,description,status,region) VALUES (${q(dx)},${q(`${marker} diagnosis`)},'A','BC')`);
  }
  sql.execute(`INSERT INTO ctl_servicecodes_dxcodes (service_code,dxcode) VALUES (${q(codes.fee)},${q(codes.dx[0])})`);
  return codes;
}

async function workflow(s) {
  const { sql, marker, context } = s;
  const q = h.sqlString;
  bc.requireBcBillingSchema(sql);
  const codes = createCodes(s);
  bc.makeBcPatient(s);
  const associations = () => sql.rows(`SELECT dxcode FROM ctl_servicecodes_dxcodes WHERE service_code=${q(codes.fee)} ORDER BY id`).map(([dx]) => dx);
  const codeless = () => sql.value(`SELECT COUNT(*) FROM ctl_servicecodes_dxcodes WHERE (service_code IS NULL OR service_code='')
    AND dxcode IN (${codes.dx.map(q).join(',')})`);
  const base = `${s.config.baseUrl.href.replace(/\/$/, '')}/`;

  let admin;
  let frame;
  const ownedRow = () => frame.locator('tr.assocRow').filter({ has: frame.locator('td', { hasText: new RegExp(`^\\s*${codes.fee}\\s*$`) }) });
  const showList = async () => {
    await frame.goto(new URL('showServiceCodeAssocs', frame.url()).href, { waitUntil: 'domcontentloaded', timeout: TIMEOUT });
  };
  await s.step(STEP.list, async () => {
    ({ page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
      { context, recorder: s.recorder, label: 'bc-codes-admin', timeout: TIMEOUT }));
    const link = admin.locator('a.xlink', { hasText: 'Manage Service/Diagnostic Code Associations' });
    h.assert(await link.count() === 1, 'Administration ▸ Billing has no Manage Service/Diagnostic Code Associations item');
    const panel = link.locator('xpath=ancestor::div[contains(@class,"accordion-collapse")][1]');
    if (!await panel.isVisible()) await admin.locator(`[data-bs-target="#${await panel.getAttribute('id')}"]`).click();
    const loaded = admin.waitForResponse(r => new URL(r.url()).pathname.endsWith('/billing/CA/BC/showServiceCodeAssocs'), { timeout: TIMEOUT });
    await link.click();
    h.assert((await loaded).status() === 200, 'The association list did not answer HTTP 200');
    frame = await (await admin.locator('#dynamic-content iframe').elementHandle()).contentFrame();
    h.assert(frame, 'The association list did not load in the Administration panel');
    await frame.locator('tr.assocRow, a', { hasText: 'Create' }).first().waitFor({ timeout: TIMEOUT });
    h.assert(await ownedRow().count() === 1, 'The owned association is not listed');
    h.assert((await ownedRow().innerText()).includes(codes.dx[0]), 'The listed association does not show its diagnosis');
  });

  let form;
  /** The bill form's fee-code search for the owned code: one match, so the popup fills the form and closes. */
  const searchFee = async () => {
    const input = form.locator('form[action$="/billing/CA/BC/CreateBilling"] input[name="xml_other1"]');
    await input.fill(`${marker} fee`);
    let search = null;
    try {
      search = await s.popup(form, form.locator(`button[onclick="OtherScriptAttach('xml_other1')"]`), 'bc-fee-code-search');
    } catch (error) {
      if (!/closed/i.test(String(error && error.message))) throw error;
    }
    if (search && !search.isClosed()) await search.waitForEvent('close', { timeout: TIMEOUT });
    h.assert(await input.inputValue() === codes.fee, `The fee-code search filled "${await input.inputValue()}", not ${codes.fee}`);
    return form.locator('#jsonDxSearchInput-1').inputValue();
  };
  await s.step(STEP.suggest, async () => {
    const link = masterRecordLink(s.master, '/billing?billRegion=', 'appointment_no=0&').first();
    h.assert(await link.count() === 1, 'The master record has no Create Invoice link');
    form = await s.popup(s.master, link, 'bc-bill-form');
    await form.waitForLoadState('domcontentloaded');
    await h.assertNotErrorPage(form, 'BC bill form');
    h.assert(await form.locator('form[action$="/billing/CA/BC/CreateBilling"]').count() === 1, 'Create Invoice did not open the BC bill form');
    const dx = await searchFee();
    h.assert(dx === codes.dx[0], `The fee-code search filled diagnosis "${dx}", not the associated ${codes.dx[0]}`);
  });

  let codesPage;
  const mspRow = () => codesPage.locator('tr').filter({ hasText: `${marker} fee` }).last();
  await s.step(STEP.msp, async () => {
    codesPage = await context.newPage();
    const response = await codesPage.goto(new URL('billing/CA/BC/ViewBillingCodeAdjust', base).href, { waitUntil: 'domcontentloaded', timeout: TIMEOUT });
    h.assert(response.status() === 200, `Manage Billing Codes answered HTTP ${response.status()}`);
    await h.assertNotErrorPage(codesPage, 'Manage Billing Codes');
    await codesPage.locator('input[name="bCode"]').fill(codes.fee);
    await Promise.all([codesPage.waitForNavigation({ waitUntil: 'domcontentloaded' }),
      codesPage.locator('input[type="submit"][name="Search"]').click()]);
    h.assert(await mspRow().count() === 1, 'The search does not list the owned MSP code');
    const text = await mspRow().innerText();
    h.assert(text.includes(codes.fee) && text.includes('12.34'), `The listed code shows "${text.replace(/\s+/g, ' ')}"`);
  });

  await s.step(STEP.fee, async () => {
    await Promise.all([codesPage.waitForNavigation({ waitUntil: 'domcontentloaded' }),
      mspRow().getByRole('link', { name: 'Edit', exact: true }).click()]);
    // The page nests its fee table in a layout table, so take the row that holds the code's own hidden id.
    const fee = codesPage.locator(`xpath=//input[@type="hidden" and @value="${codes.feeId}"]/ancestor::tr[1]`);
    h.assert(await fee.count() === 1, 'Edit does not list the code\'s fee row');
    await fee.getByRole('link', { name: 'edit', exact: true }).click();
    await fee.locator('input[name="value"]').fill('23.45');
    const saved = codesPage.waitForResponse(r => r.url().includes('method=ajaxCodeUpdate') && r.request().method() === 'POST', { timeout: TIMEOUT });
    await fee.getByRole('link', { name: 'save', exact: true }).click();
    h.assert((await saved).status() === 200, 'The fee row save did not answer HTTP 200');
    await expectValue(sql, `SELECT value FROM billingservice WHERE billingservice_no=${Number(codes.feeId)}`, '23.45', 'The fee row save did not store the new fee');
    const back = await fee.getByRole('link', { name: 'edit', exact: true }).waitFor({ timeout: TIMEOUT }).then(() => true, () => false);
    h.assert(back, 'The fee row stays in edit mode after its save');
    h.assert((await fee.innerText()).includes('23.45'), 'The fee row does not show the saved fee');
  });

  await s.step(STEP.edit, async () => {
    await showList();
    await Promise.all([frame.waitForNavigation({ waitUntil: 'domcontentloaded' }),
      ownedRow().getByRole('link', { name: 'Edit', exact: true }).click()]);
    await frame.locator('form[action$="/billing/CA/BC/saveAssocAction"]').waitFor({ timeout: TIMEOUT });
    const service = frame.locator('input[name="xml_other1"]');
    const shown = { service: await service.inputValue(), dx: await frame.locator('input[name="xml_diagnostic_detail1"]').inputValue() };
    h.assert(shown.service === codes.fee && shown.dx === codes.dx[0],
      `The editor shows service code "${shown.service}" and diagnosis "${shown.dx}", not ${codes.fee} and ${codes.dx[0]}`);
  });

  await s.step(STEP.save, async () => {
    await frame.locator('input[name="xml_diagnostic_detail1"]').fill(codes.dx[1]);
    await Promise.all([frame.waitForNavigation({ waitUntil: 'domcontentloaded' }),
      frame.locator('input[type="submit"][value="Save"]').click()]);
    await expectValue(sql, `SELECT GROUP_CONCAT(dxcode ORDER BY id) FROM ctl_servicecodes_dxcodes WHERE service_code=${q(codes.fee)}`,
      codes.dx[1], 'The association does not hold just the new diagnosis');
    h.assert(codeless() === '0', 'Save stored a diagnosis with no service code');
    await showList();
    h.assert((await ownedRow().innerText()).includes(codes.dx[1]), 'The list does not show the association\'s new diagnosis');
    h.assert(await searchFee() === codes.dx[1], 'The fee-code search does not fill in the new diagnosis');
  });

  await s.step(STEP.remove, async () => {
    await showList();
    await Promise.all([frame.waitForNavigation({ waitUntil: 'domcontentloaded' }),
      ownedRow().getByRole('link', { name: 'Remove', exact: true }).click()]);
    await expectValue(sql, `SELECT COUNT(*) FROM ctl_servicecodes_dxcodes WHERE service_code=${q(codes.fee)}`, '0', 'Remove did not delete the association');
    await showList();
    h.assert(await ownedRow().count() === 0, 'The removed association is still listed');
  });

  await s.step(STEP.known, async () => {
    const failures = [];
    // #4842: the Add Code form's service code was a checkbox, so no code could be typed or sent.
    const add = await context.newPage();
    const addResponse = await add.goto(new URL('billing/CA/BC/ViewBillingAddCode?addNew=true', base).href, { waitUntil: 'domcontentloaded', timeout: TIMEOUT });
    if (addResponse.status() !== 200) failures.push(`Add Code answered HTTP ${addResponse.status()}`);
    else if (await add.locator('form input[name="code"]').getAttribute('type') !== 'text') failures.push('Add Code has no text box for the service code (#4842)');
    await add.close();
    // D14: Create New Association must open an empty editor; D13: that editor must refuse a code already associated.
    sql.execute(`INSERT INTO ctl_servicecodes_dxcodes (service_code,dxcode) VALUES (${q(codes.fee)},${q(codes.dx[0])})`);
    await showList();
    await Promise.all([frame.waitForNavigation({ waitUntil: 'domcontentloaded' }),
      frame.locator('a', { hasText: 'Create New Association' }).click()]);
    const editor = frame.locator('form[action$="/billing/CA/BC/saveAssocAction"]');
    const service = frame.locator('input[name="xml_other1"]');
    if (!await editor.count() || await service.evaluate(e => e.readOnly)) {
      failures.push('Create New Association does not open an editor whose service code can be typed (#4439 D14); the duplicate check (D13) cannot be reached');
    } else {
      await service.fill(codes.fee);
      await frame.locator('input[name="xml_diagnostic_detail1"]').fill(codes.dx[1]);
      await Promise.all([frame.waitForNavigation({ waitUntil: 'domcontentloaded' }),
        frame.locator('input[type="submit"][value="Save"]').click()]);
      if (associations().join(',') !== codes.dx[0]) failures.push(`A duplicate association was saved: ${codes.fee} now holds ${associations().join(',')} (#4439 D13)`);
    }
    h.assert(!failures.length, failures.join('; '));
  });
}

if (require.main === module) runWorkflow('billing-bc-codes-admin', workflow, { openPatient: true, openMaster: true });
module.exports = { workflow, STEP };
