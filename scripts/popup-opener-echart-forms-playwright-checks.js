#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * E-Chart form popup -> chart Forms module refresh (risk sweep "lost popup openers";
 * coverage plan §2.5 encounter forms, opener contracts).
 *
 * User path: Schedule ▸ Search ▸ Master Record ▸ E-Chart ▸ Forms module menu ▸
 * Rourke2017 (form/formrourke2017complete, ViewForm2Action) ▸ Save and Exit
 * (form/formname?submit=exit -> encounter/close.jsp).
 * The chart refreshes a module after a tracked popup closes. Previously form/*
 * forwarded without COOP, severing the opener, and menu items registered no
 * reloadWindows entry. The common response filter and Forms menu tracking must
 * keep the opener live, register the popup and refresh after a successful save.
 * Asserts Save refreshes the chart while preserving its unsaved draft, Save and Exit
 * appends a historical version and closes, and reopening the template offers the
 * latest saved version, explicit blank creation, or Cancel. A second open chart
 * must not borrow the first patient's saved form.
 * Fixtures: two owned FAKE patients; cleanup removes their support rows and the
 * Rourke2017 form/checkbox rows, and asserts removal.
 */
const h = require('./lib/playwright-harness');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { createOwnedPatient, newOwnedMarker, removeOwnedPatient } = require('./lib/owned-patient');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');
const { openChart, waitForNavbars } = require('./echart-navbar-modules-playwright-checks');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { documentChain, openerState, consumeClosingAbort } = require('./lib/popup-opener-helpers');

// A started form is listed as an item reopening the latest copy (forwardshortcutname, formId=latest);
// the module's menu entry for a NEW form is a different link (formrourke2017complete, formId=0).
const STARTED = 'a[onclick*="forwardshortcutname?formname=Rourke2017"]';

async function workflow(s) {
  const { sql, patient } = s;
  const chain = documentChain(s.context);
  const rows = () => sql.value(`SELECT COUNT(*) FROM formRourke2017 WHERE demographic_no=${patient}`);
  s.cleanup(() => {
    sql.execute(`DELETE FROM form_boolean_value WHERE form_name='formRourke2017' AND form_id IN (SELECT ID FROM formRourke2017 WHERE demographic_no=${patient})`);
    sql.execute(`DELETE FROM formRourke2017 WHERE demographic_no=${patient}`);
    h.assert(rows() === '0', 'Owned formRourke2017 rows were not removed');
  });
  const chart = await s.chart();
  const module = chart.locator('#forms');
  let form;
  let state;
  let firstSavedCompletion;

  await s.step('Forms ▸ Rourke2017 opens the form for the owned patient while the Forms module lists none', async () => {
    h.assert(await module.locator(STARTED).count() === 0,
      'The Forms module already lists a saved Rourke form for a new patient');
    const link = chart.locator('[id^="menu"] a[onclick*="/form/formrourke2017complete"]').first();
    await revealAuditLink(chart, link, 20000);
    form = await s.popup(chart, link, 'rourke2017');
    h.assert(new URL(form.url()).searchParams.get('demographic_no') === patient, 'The form opened for another patient');
    await form.locator('#frmP1').waitFor();
    state = await openerState(form);
    h.assert(state === 'live', 'The new form must retain its chart opener before saving');
    const windowName = await form.evaluate(() => window.name);
    h.assert(await chart.evaluate(name => Boolean(reloadWindows[name]) && reloadWindows[name + 'div'] === 'forms', windowName),
      'The Forms menu did not register the popup for module refresh');
  });

  await s.step('Save refreshes Forms while the form stays open and leaves the encounter draft untouched', async () => {
    await form.locator('a[href="#tab-cp4"]').click();
    const draft = chart.locator('textarea[name="caseNote_note"]');
    await draft.fill(s.marker + ' unsaved encounter draft');
    await h.withExpectedDialogs(form, async () => {
      await form.locator('#tab-cp4 input[type="button"][onclick*="onSave();"]').first().click();
      await form.waitForURL(url => url.pathname.endsWith('/form/forwardname') && !url.searchParams.has('saved'));
      await form.locator('#frmP1').waitFor();
    }, {accept: true});
    await module.locator(STARTED).first().waitFor({state: 'attached', timeout: 15000});
    h.assert(rows() === '1', 'Save did not write exactly one initial version');
    h.assert(!form.isClosed(), 'Ordinary Save closed the form');
    h.assert(await draft.inputValue() === s.marker + ' unsaved encounter draft', 'Save changed the encounter draft');
    await chart.waitForFunction(() => autoCompList.some(key =>
      (autoCompleted[key] || '').includes('forwardshortcutname?formname=Rourke2017')));
    firstSavedCompletion = await chart.evaluate(() => autoCompList.find(key =>
      (autoCompleted[key] || '').includes('forwardshortcutname?formname=Rourke2017')));
  });

  // Saving must refresh the chart through its tracked popup handle.
  await s.step('Save and Exit stores the form, closes it, and the chart Forms module lists it without a reload', async () => {
    // A later edit second produces a different dated completion key.
    await expectValue(sql, `SELECT NOW()>MAX(formEdited) FROM formRourke2017 WHERE demographic_no=${patient}`, '1',
      'The second save must have a later edit time to exercise replacement of dated suggestions');
    const closed = form.waitForEvent('close', { timeout: 30000 }).then(() => true, () => false);
    const failuresBefore = s.recorder.requestFailures.length;
    await h.withExpectedDialogs(form, async () => {
      // The Save / Save and Exit bar sits on the last page tab (Page IV).
      await form.locator('a[href="#tab-cp4"]').click();
      await form.locator('#tab-cp4 input[type="button"][onclick*="onSaveExit"]').first().click({ noWaitAfter: true });
      await expectValue(sql, `SELECT COUNT(*) FROM formRourke2017 WHERE demographic_no=${patient}`, '2',
        'Save and Exit did not append exactly one Rourke 2017 version');
    }, { accept: true });
    h.assert(await closed, `The form saved but did not close (documents: ${chain.describe(form)})`);
    consumeClosingAbort(s.recorder, failuresBefore, 'rourke2017');
    const saved = module.locator(STARTED);
    await saved.first().waitFor({ state: 'attached', timeout: 15000 }).catch(() => {});
    h.assert(await saved.count() > 0,
      `The chart's Forms module never listed the saved form (the form popup's window.opener was ${state}; `
      + `form documents: ${chain.describe(form)})`);
    await chart.waitForFunction(oldKey => autoCompList.some(key => key !== oldKey &&
      (autoCompleted[key] || '').includes('forwardshortcutname?formname=Rourke2017')), firstSavedCompletion);
    const completionState = await chart.evaluate(oldKey => ({
      current: autoCompList.filter(key => (autoCompleted[key] || '').includes('forwardshortcutname?formname=Rourke2017')),
      stale: autoCompList.includes(oldKey) || Object.hasOwn(autoCompleted, oldKey) || Object.hasOwn(itemColours, oldKey),
    }), firstSavedCompletion);
    h.assert(completionState.current.length === 1 && !completionState.stale,
      'Forms refresh left an obsolete dated autocomplete choice, action or colour');
  });
  await s.step('A second open chart with no saved form opens blank without borrowing the first chart record', async () => {
    const marker = newOwnedMarker();
    const otherPatient = createOwnedPatient(sql, {marker, provider: s.provider});
    s.cleanup(() => removeOwnedPatient(sql, otherPatient, marker));
    const secondSchedule = await s.context.newPage();
    await secondSchedule.goto(s.schedule.url(), {waitUntil: 'domcontentloaded'});
    const {masterPage} = await openMasterRecord(s.context, secondSchedule, s.recorder, {
      searchTerm: marker, preferredDemographicNo: otherPatient, timeout: 20000,
    });
    const otherChart = await openChart(s.context, masterPage, s.recorder, 20000);
    await waitForNavbars(otherChart, 20000);
    const link = otherChart.locator('#forms .menu a[data-encounter-form-name="Rourke2017"]');
    await revealAuditLink(otherChart, link, 20000);
    const blank = await s.popup(otherChart, link, 'other-chart-rourke');
    h.assert(new URL(blank.url()).searchParams.get('demographic_no') === otherPatient, 'The second chart opened the first patient');
    h.assert(await blank.locator('input[name="formId"]').first().inputValue() === '0', 'The second chart borrowed a saved form');
    await blank.close();
    await chart.bringToFront();
  });

  await s.step('Reopening the template warns before starting blank, and Cancel creates no record', async () => {
    const link = chart.locator('#forms .menu a[data-encounter-form-name="Rourke2017"]');
    await revealAuditLink(chart, link, 20000);
    await link.click();
    const dialog = chart.getByRole('dialog', {name: 'Saved form exists'});
    await dialog.waitFor();
    await dialog.getByRole('button', {name: 'Cancel', exact: true}).click();
    h.assert(rows() === '2', 'Cancelling the new form changed saved versions');
  });

  await s.step('Open the existing version reuses the latest saved form instead of a blank instance', async () => {
    const link = chart.locator('#forms .menu a[data-encounter-form-name="Rourke2017"]');
    await revealAuditLink(chart, link, 20000);
    await link.click();
    const dialog = chart.getByRole('dialog', {name: 'Saved form exists'});
    await dialog.waitFor();
    const existing = await s.popup(chart, dialog.getByRole('button', {name: 'Open the existing version', exact: true}), 'rourke-existing');
    const id = sql.value(`SELECT ID FROM formRourke2017 WHERE demographic_no=${patient} ORDER BY ID DESC LIMIT 1`);
    h.assert(await existing.locator('input[name="formId"]').first().inputValue() === id,
      'The existing-version choice opened a blank or different form');
    h.assert(await existing.evaluate(() => window.name.endsWith('started')), 'The existing-version choice did not reuse the started-form window name');
    await existing.close();
    h.assert(rows() === '2', 'Opening the saved form changed its versions');
  });

  await s.step('Form autocomplete dispatches the same saved-form guard and Cancel preserves history', async () => {
    const search = chart.locator('#enTemplate');
    await search.fill('');
    await search.pressSequentially('Rourke2017', {delay: 40});
    const suggestion = chart.locator('#enTemplate_list li').filter({hasText: 'Rourke2017 (new)'});
    await suggestion.waitFor({state: 'visible'});
    h.assert(await suggestion.count() === 1, 'Forms refresh duplicated the autocomplete suggestion');
    await suggestion.click();
    const dialog = chart.getByRole('dialog', {name: 'Saved form exists'});
    await dialog.waitFor();
    await dialog.getByRole('button', {name: 'Cancel', exact: true}).click();
    h.assert(rows() === '2', 'Cancelling autocomplete changed saved versions');
  });

  await s.step('The packaged form check accepts the signed-int maximum while WAF attack controls remain blocked', async () => {
    const endpoint = new URL(s.config.baseUrl + '/encounter/formExists');
    endpoint.searchParams.set('demographicNo', patient);
    endpoint.searchParams.set('appointmentNo', '2147483647');
    endpoint.searchParams.set('formname', 'Rourke2017');
    const valid = await s.context.request.get(endpoint.href);
    h.assert(valid.status() === 200, 'The front door blocked a valid maximum appointment ID');
    const result = await valid.json();
    h.assert(result.exists && new URL(result.url, chart.url()).searchParams.get('appointmentNo') === '2147483647',
      'The form check truncated the maximum appointment ID');

    // Missing formname rejects before a patient lookup: test the WAF's patient-ID
    // handling without reading an unowned chart or advancing demographic AUTO_INCREMENT.
    const maxPatient = new URL(endpoint);
    maxPatient.searchParams.set('demographicNo', '2147483647');
    maxPatient.searchParams.delete('formname');
    h.assert((await s.context.request.get(maxPatient.href)).status() === 400,
      'The front door blocked the maximum patient ID before application validation');

    for (const [key, value] of [['appointmentNo', '2147483648'], ['probe', '2147483647'], ['appointmentNo', "1' OR '1'='1"]]) {
      const attack = new URL(endpoint);
      attack.searchParams.set(key, value);
      h.assert((await s.context.request.get(attack.href)).status() === 403,
        `The WAF failed to block its ${key} attack control`);
    }
    const otherRoute = new URL(endpoint);
    otherRoute.pathname += 'Unregistered';
    h.assert((await s.context.request.get(otherRoute.href)).status() === 403,
      'The maximum-ID exception leaked to another route');
  });

  await s.step('Create a new blank version remains available only after an explicit choice', async () => {
    const link = chart.locator('#forms .menu a[data-encounter-form-name="Rourke2017"]');
    await revealAuditLink(chart, link, 20000);
    await link.click();
    const dialog = chart.getByRole('dialog', {name: 'Saved form exists'});
    await dialog.waitFor();
    const blank = await s.popup(chart, dialog.getByRole('button', {name: 'Create a new blank version', exact: true}), 'rourke-blank');
    h.assert(await blank.locator('input[name="formId"]').first().inputValue() === '0',
      'The explicit blank choice did not start a new form');
    await blank.close();
    h.assert(rows() === '2', 'Opening a blank form unexpectedly saved a version');
  });

}

if (require.main === module) runWorkflow('popup-opener-echart-forms', workflow, { openPatient: true });
module.exports = { workflow };
