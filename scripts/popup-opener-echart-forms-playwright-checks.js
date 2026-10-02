#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * E-Chart form popup -> chart Forms module refresh (risk sweep "lost popup openers";
 * coverage plan §2.5 encounter forms, opener contracts).
 *
 * User path: Schedule ▸ Search ▸ Master Record ▸ E-Chart ▸ Forms module menu ▸
 * Rourke2017 (form/formrourke2017complete, ViewForm2Action) ▸ Save and Exit
 * (form/formname?submit=exit -> encounter/close.jsp).
 * The chart refreshes a module after a tracked popup closes: popupPage() keeps the
 * window in openWindows[name] and a 1 s timer reloads the module once that window
 * reports .closed. Two things defeat that for a form started from the menu: (1) the
 * form route forwards to its JSP inside the action (ViewForm2Action), so it is served
 * WITHOUT Cross-Origin-Opener-Policy while the chart carries `same-origin`, the form
 * opens in a new browsing-context group, window.opener is null and the chart's handle
 * reads .closed at once, before anything is saved; (2) LeftNavBarDisplay.jsp registers
 * no reloadWindows entry for popup-MENU items at all (a control run with the COOP
 * header restored kept the opener but still never refreshed the module).
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
  });

  // Last: the chart-side refresh the severed opener breaks.
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
}

if (require.main === module) runWorkflow('popup-opener-echart-forms', workflow, { openPatient: true });
module.exports = { workflow };
