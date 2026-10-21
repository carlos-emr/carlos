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
 * Fixtures: the owned FAKE- patient; cleanup removes the owned form_boolean_value children before its
 * formRourke2017 parents and verifies both are gone.
 */
const h = require('./lib/playwright-harness');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { documentChain, openerState, consumeClosingAbort } = require('./lib/popup-opener-helpers');

// A started form is listed as an item reopening the latest copy (forwardshortcutname, formId=latest);
// the module's menu entry for a NEW form is a different link (formrourke2017complete, formId=0).
const STARTED = 'a[onclick*="forwardshortcutname?formname=Rourke2017"]';

// Keep this local: form booleans have no demographic key or cascading SQL delete.
// Capture pre-existing IDs before opening the form, so a reused orphan key is retained.
function installOwnedFormCleanup(s) {
  const { sql, context, marker } = s;
  const patient = positiveId(s.patient, 'patient');
  h.assert(/^FAKE-PW[0-9a-f]{16}$/.test(marker), 'The fixture marker is invalid');
  const quotedMarker = h.sqlString(marker);
  const owner = () => h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient}
    AND last_name=${quotedMarker}`) === '1', 'Patient fixture ownership changed');
  const parentCount = () => sql.value(`SELECT COUNT(*) FROM formRourke2017 WHERE demographic_no=${patient}`);
  owner();
  h.assert(parentCount() === '0', 'A new fixture already has Rourke2017 forms');
  const previousIds = new Set(sql.rows(`SELECT DISTINCT form_id FROM form_boolean_value
    WHERE form_name='formRourke2017'`).map(row => {
    h.assert(Array.isArray(row) && row.length === 1 && /^-?\d+$/.test(String(row[0])),
      'Existing Rourke boolean identity evidence is malformed');
    return String(row[0]);
  }));
  s.cleanup(() => {
    const browser = context.browser();
    h.assert(browser && !browser.isConnected(), 'Rourke fixtures retained: browser closure is unconfirmed');
    owner();
    const ids = sql.rows(`SELECT ID FROM formRourke2017 WHERE demographic_no=${patient} ORDER BY ID`)
      .map(row => {
        h.assert(Array.isArray(row) && row.length === 1, 'Owned Rourke form evidence is malformed');
        return positiveId(row[0], 'form');
      });
    h.assert(new Set(ids).size === ids.length, 'Owned Rourke form IDs are duplicated');
    h.assert(!ids.some(id => previousIds.has(id)), 'Pre-existing Rourke boolean rows must be retained');
    if (!ids.length) {
      h.assert(parentCount() === '0', 'Owned Rourke forms appeared during cleanup');
      return;
    }
    const list = ids.join(',');
    const children = () => sql.value(`SELECT COUNT(*) FROM form_boolean_value
      WHERE form_name='formRourke2017' AND form_id IN (${list})`);
    sql.execute(`DELETE b FROM form_boolean_value b
      INNER JOIN formRourke2017 f ON f.ID=b.form_id AND b.form_name='formRourke2017'
      INNER JOIN demographic d ON d.demographic_no=f.demographic_no
      WHERE f.demographic_no=${patient} AND f.ID IN (${list}) AND d.last_name=${quotedMarker}`);
    h.assert(children() === '0', 'Owned Rourke boolean rows were not removed');
    owner();
    sql.execute(`DELETE f FROM formRourke2017 f
      INNER JOIN demographic d ON d.demographic_no=f.demographic_no
      WHERE f.demographic_no=${patient} AND f.ID IN (${list}) AND d.last_name=${quotedMarker}`);
    owner();
    h.assert(parentCount() === '0' && children() === '0',
      'Owned Rourke forms or boolean rows remain after cleanup');
  });
}

function positiveId(value, label) {
  const text = String(value);
  h.assert(/^[1-9]\d*$/.test(text) && text.length <= 10 && BigInt(text) <= 2147483647n,
    `The owned ${label} identity is invalid`);
  return text;
}

async function workflow(s) {
  const { sql, patient } = s;
  const chain = documentChain(s.context);
  const rows = () => sql.value(`SELECT COUNT(*) FROM formRourke2017 WHERE demographic_no=${patient}`);
  installOwnedFormCleanup(s);
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
}

if (require.main === module) runWorkflow('popup-opener-echart-forms', workflow, { openPatient: true });
module.exports = { workflow, installOwnedFormCleanup };
