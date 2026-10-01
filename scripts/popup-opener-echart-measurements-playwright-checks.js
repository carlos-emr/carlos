#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * E-Chart measurement group popup -> window.opener.postMessage -> encounter note
 * (risk sweep "lost popup openers"; coverage plan §2.5 encounter, opener contracts).
 *
 * User path: Schedule ▸ Search ▸ Master Record ▸ E-Chart ▸ Measurements module menu ▸
 * Vitals (encounter/oscarMeasurements/SetupMeasurements, Measurements.jsp) ▸ BP and
 * Heart Rate ▸ Submit. Measurements.jsp saves through encounter/Measurements?ajax=true
 * and hands the returned encounter text to window.opener.postMessage(); the chart's
 * receiveMessage() accepts it only from a window it registered in popupPage() and
 * appends it to the open note editor.
 * Asserts the popup opens for the owned patient, Submit writes exactly the two
 * measurements rows, the popup closes, and the chart's note editor gains the BP text
 * WITHOUT reloading (markOpener sentinel). echart-vitals-bmi opens the popup by URL
 * (no opener), so this callback was not covered.
 * Fixtures: the owned FAKE- patient; cleanup deletes its measurements rows (and their
 * measurementsExt rows) and asserts they are gone.
 */
const h = require('./lib/playwright-harness');
const { markOpener } = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { documentChain, openerState } = require('./lib/popup-opener-helpers');

async function workflow(s) {
  const { sql, patient } = s;
  const chain = documentChain(s.context);
  s.cleanup(() => {
    const ids = sql.rows(`SELECT id FROM measurements WHERE demographicNo=${patient}`).map(row => row[0]);
    h.assert(ids.every(id => /^[1-9]\d*$/.test(id)), 'Owned measurement id is invalid');
    if (ids.length) {
      sql.execute(`DELETE FROM measurementsExt WHERE measurement_id IN (${ids.join(',')});
        DELETE FROM measurements WHERE demographicNo=${patient} AND id IN (${ids.join(',')})`);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient}`) === '0',
      'Owned measurement rows were not removed');
  });
  const chart = await s.chart();
  let vitals;

  await s.step('Measurements ▸ Vitals opens the group popup for the owned patient from the chart', async () => {
    const link = chart.locator('[id^="menu"] a[onclick*="SetupMeasurements"][onclick*="groupName=Vitals"]').first();
    await revealAuditLink(chart, link, 20000);
    vitals = await s.popup(chart, link, 'vitals-group');
    h.assert(new URL(vitals.url()).searchParams.get('demographicNo') === patient, 'The Vitals popup opened for another patient');
    await vitals.locator('#row-BP td:nth-child(3) input').waitFor({ state: 'visible' });
  });

  // Last: the opener callback into the chart's note.
  await s.step('Submit saves BP and HR, closes the popup and writes them into the open note without reloading the chart', async () => {
    const sentinel = await markOpener(chart);
    const noteText = () => chart.locator('textarea[id^="caseNote_note"]').evaluateAll(areas => areas.map(a => a.value).join('\n'));
    h.assert(!(await noteText()).includes('120/80'), 'The note already carries the reading before Submit');
    await vitals.locator('#row-BP td:nth-child(3) input').fill('120/80');
    await vitals.locator('#row-HR td:nth-child(3) input').fill('72');
    const state = await openerState(vitals);
    const closed = vitals.waitForEvent('close', { timeout: 20000 }).then(() => true, () => false);
    const [saved] = await Promise.all([
      vitals.waitForResponse(r => r.request().method() === 'POST' && /\/encounter\/Measurements\?ajax=true/.test(r.url())),
      vitals.locator('input[name="Button"][value="Submit"]').click(),
    ]);
    h.assert(saved.ok(), `The measurement save answered HTTP ${saved.status()}`);
    await expectValue(sql, `SELECT GROUP_CONCAT(CONCAT(type,'=',dataField) ORDER BY type SEPARATOR '|')
      FROM measurements WHERE demographicNo=${patient}`, 'BP=120/80|HR=72', 'The Vitals Submit did not store exactly BP and HR');
    h.assert(await closed, `The Vitals popup saved but did not close (window.opener ${state})`);
    const deadline = Date.now() + 10000;
    while (!(await noteText()).includes('120/80') && Date.now() < deadline) await new Promise(r => setTimeout(r, 250));
    h.assert((await noteText()).includes('120/80'),
      `The saved reading never reached the chart's note editor (window.opener ${state} at save; popup documents: ${chain.describe(vitals)})`);
    h.assert(await chart.evaluate(name => window[name], sentinel.marker) === sentinel.token,
      'The chart reloaded instead of receiving the reading in place');
  });
}

if (require.main === module) runWorkflow('popup-opener-echart-measurements', workflow, { openPatient: true });
module.exports = { workflow };
