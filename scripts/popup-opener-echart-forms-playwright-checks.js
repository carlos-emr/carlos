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
 * Asserts the form opens for the owned patient, Save and Exit writes one
 * formRourke2017 row and closes the popup, and the chart's Forms module then lists
 * the saved form without a manual reload.
 * Fixtures: the owned FAKE- patient; cleanup deletes its formRourke2017 rows and
 * asserts they are gone.
 */
const h = require('./lib/playwright-harness');
const { revealAuditLink } = require('./lib/playwright-link-audit');
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

  // Saving must refresh the chart through its tracked popup handle.
  await s.step('Save and Exit stores the form, closes it, and the chart Forms module lists it without a reload', async () => {
    const closed = form.waitForEvent('close', { timeout: 30000 }).then(() => true, () => false);
    const failuresBefore = s.recorder.requestFailures.length;
    await h.withExpectedDialogs(form, async () => {
      // The Save / Save and Exit bar sits on the last page tab (Page IV).
      await form.locator('a[href="#tab-cp4"]').click();
      await form.locator('#tab-cp4 input[type="button"][onclick*="onSaveExit"]').first().click({ noWaitAfter: true });
      await expectValue(sql, `SELECT COUNT(*) FROM formRourke2017 WHERE demographic_no=${patient}`, '1',
        'Save and Exit did not store exactly one Rourke 2017 form');
    }, { accept: true });
    h.assert(await closed, `The form saved but did not close (documents: ${chain.describe(form)})`);
    consumeClosingAbort(s.recorder, failuresBefore, 'rourke2017');
    const saved = module.locator(STARTED);
    await saved.first().waitFor({ state: 'attached', timeout: 15000 }).catch(() => {});
    h.assert(await saved.count() > 0,
      `The chart's Forms module never listed the saved form (the form popup's window.opener was ${state}; `
      + `form documents: ${chain.describe(form)})`);
  });
  await s.step('Reopening the template warns before starting blank, and Cancel creates no record', async () => {
    const link = chart.locator('#forms .menu a[data-encounter-form-name="Rourke2017"]');
    await revealAuditLink(chart, link, 20000);
    await link.click();
    const dialog = chart.getByRole('dialog', {name: 'Saved form exists'});
    await dialog.waitFor();
    await dialog.getByRole('button', {name: 'Cancel', exact: true}).click();
    h.assert(rows() === '1', 'Cancelling the new form changed saved versions');
  });

  await s.step('Open the existing version reuses the latest saved form instead of a blank instance', async () => {
    const link = chart.locator('#forms .menu a[data-encounter-form-name="Rourke2017"]');
    await revealAuditLink(chart, link, 20000);
    await link.click();
    const dialog = chart.getByRole('dialog', {name: 'Saved form exists'});
    await dialog.waitFor();
    const existing = await s.popup(chart, dialog.getByRole('button', {name: 'Open the existing version', exact: true}), 'rourke-existing');
    const id = sql.value(`SELECT ID FROM formRourke2017 WHERE demographic_no=${patient}`);
    h.assert(await existing.locator('input[name="formId"]').first().inputValue() === id,
      'The existing-version choice opened a blank or different form');
    h.assert(await existing.evaluate(() => window.name.endsWith('started')), 'The existing-version choice did not reuse the started-form window name');
    await existing.close();
    h.assert(rows() === '1', 'Opening the saved form changed its versions');
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
    h.assert(rows() === '1', 'Opening a blank form unexpectedly saved a version');
  });

}

if (require.main === module) runWorkflow('popup-opener-echart-forms', workflow, { openPatient: true });
module.exports = { workflow };
