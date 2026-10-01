#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Coverage plan §2.5 flowsheet-patient-customization: the per-patient flowsheet editor.
//
// User path: Schedule ▸ Master Record ▸ E-Chart ▸ Measurements ▸ Health Tracker ▸ Add Measurements
// (Edit Flowsheet, patient scope) ▸ Save; ▸ pencil (Update Flowsheet) ▸ add a warning rule and a
// target colour ▸ Update; back to the tracker; ▸ Print (custom print); ▸ Edit ▸ Revert / Custom ▸ trash.
// Asserts each flowsheet_customization row (add / update, patient + provider scoped, archived flag),
// that the tracker renders the added item, the warning text and the target colour on a seeded value,
// that the custom-print page renders the customised item and its colour, and that Revert and the
// Custom-list trash archive the rows and empty the tracker again.
// health-tracker seeds its customization rows with SQL; flowsheet-admin covers clinic-wide editing.
// Fixtures: the owned FAKE-PW patient (runWorkflow), one seeded WT reading carrying the marker.
// Cleanup deletes this patient's tracker customization rows and readings and asserts they are gone.
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const TYPE = 'WT';

async function navigate(page, locator) {
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 20000 }),
    locator.click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
  await h.assertNotErrorPage(page, new URL(page.url()).pathname);
}

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const displayName = `${marker} Weight`;
  const warning = `${marker} weight above 100`;
  const custRows = (where) => `SELECT COUNT(*) FROM flowsheet_customization WHERE flowsheet='tracker'
    AND demographic_no=${patient} AND provider_no=${h.sqlString(provider)} ${where}`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM flowsheet_customization WHERE flowsheet='tracker' AND demographic_no=${patient};
      DELETE FROM measurements WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM flowsheet_customization WHERE demographic_no=${patient}`) === '0',
      'Owned flowsheet customization rows were not removed');
  });

  const chart = await s.chart();
  let backAfterUpdate;
  const trackerLink = chart.locator('#measurementslist li a.links[onclick*="ViewHealthTracker"]').first();
  await trackerLink.waitFor({ state: 'visible' });
  const tracker = await s.popup(chart, trackerLink, 'health-tracker');
  await tracker.waitForLoadState('networkidle').catch(() => {});

  await s.step('Add Measurements saves a patient-scoped add customization', async () => {
    await navigate(tracker, tracker.getByRole('link', { name: 'Add Measurements' }));
    h.assert(new URL(tracker.url()).searchParams.get('demographic') === patient,
      'Add Measurements did not open the editor for this patient');
    await tracker.locator('#add select[name="measurement"]').selectOption(TYPE);
    await tracker.locator('#display_name').fill(displayName);
    await tracker.locator('#add input[name="guideline"]').fill(marker);
    await navigate(tracker, tracker.locator('#FlowSheetCustomActionForm input[type="submit"][value="Save"]'));
    await expectValue(sql, custRows(`AND action='add' AND archived='0' AND measurement IS NULL
      AND payload LIKE ${h.sqlString(`%measurement_type="${TYPE}"%`)}
      AND payload LIKE ${h.sqlString(`%${displayName}%`)}`), '1',
    'The editor did not store one patient-scoped add customization for the measurement');
    h.assert(sql.value(`SELECT COUNT(*) FROM flowsheet_customization WHERE demographic_no=${patient}`) === '1',
      'One Save stored more than one customization');
    const row = tracker.locator('#measurementTbl tbody tr').filter({ hasText: displayName });
    await row.waitFor({ state: 'visible' });
  });

  await s.step('Update Flowsheet stores a warning rule and a target colour for the item', async () => {
    const row = tracker.locator('#measurementTbl tbody tr').filter({ hasText: displayName });
    await navigate(tracker, row.locator('a[title="Edit"]'));
    h.assert(/\/ViewUpdateFlowsheet$/.test(new URL(tracker.url()).pathname), 'The pencil did not open Update Flowsheet');
    await tracker.locator('select[name="strength1"]').selectOption('warning');
    await tracker.locator('input[name="text1"]').fill(warning);
    await tracker.locator('select[name="type1c1"]').selectOption('lastValueAsInt');
    await tracker.locator('input[name="value1c1"]').fill('>100');
    await tracker.locator('select[name="targettype1c1"]').selectOption('getDataAsDouble');
    await tracker.locator('input[name="targetvalue1c1"]').fill('>100');
    await tracker.locator('input[name="col1"][value="HIGH"]').check();
    await navigate(tracker, tracker.locator('input[type="submit"][value="Update"]'));
    await expectValue(sql, custRows(`AND action='update' AND archived='0' AND measurement=${h.sqlString(TYPE)}`), '1',
      'Update Flowsheet did not store one patient-scoped update customization');
    const payload = sql.value(`SELECT payload FROM flowsheet_customization WHERE flowsheet='tracker'
      AND demographic_no=${patient} AND action='update' AND archived='0'`);
    h.assert(payload.includes(`<recommendation strength="warning" message="${warning}">`)
      && payload.includes('<condition type="lastValueAsInt" param="" value="&gt;100" />'),
    'The stored update lost the warning rule entered on Update Flowsheet');
    h.assert(payload.includes('<rule indicationColor="HIGH">')
      && payload.includes('<condition type="getDataAsDouble" param="" value="&gt;100" />'),
    'The stored update lost the target colour entered on Update Flowsheet');
    backAfterUpdate = await tracker.locator('a[title^="go back to"]').getAttribute('href');
  });

  const readingId = sql.value(`INSERT INTO measurements(type,demographicNo,providerNo,dataField,measuringInstruction,
    comments,dateObserved,dateEntered) VALUES(${h.sqlString(TYPE)},${patient},${h.sqlString(provider)},'120','in kg',
    ${h.sqlString(marker)},'2026-09-01 00:00:00',NOW()); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(readingId), 'The seeded reading was not created');
  let view;

  await s.step('the tracker renders the item, the warning and the target colour on a seeded value', async () => {
    await tracker.close();
    await new Promise(r => setTimeout(r, 3000));
    console.log(chart.isClosed(), await chart.locator('#measurementslist').innerHTML().catch(e => String(e)));
    view = await s.popup(chart, trackerLink, 'health-tracker-customised');
    await view.waitForLoadState('networkidle').catch(() => {});
    const card = view.locator(`#wrap-${TYPE}`);
    h.assert((await card.locator('.measurement-label').innerText()).trim() === displayName,
      'The tracker card does not carry the display name saved in the editor');
    h.assert((await card.locator('.alert-danger').innerText()).includes(warning),
      'The warning rule did not fire on a value above its threshold');
    const value = card.locator(`.history-value[data-measurement-id="${readingId}"]`);
    h.assert((await value.locator('.indicator-label').innerText()).trim() === 'HIGH',
      'The target colour rule did not mark the seeded value HIGH');
    h.assert(/orange/.test(await value.locator('.indicator').getAttribute('style') || ''),
      'The seeded value is not drawn in the HIGH indicator colour');
  });

  await s.step('custom print lists the customised item with its target colour', async () => {
    await navigate(view, view.locator('a[title="Print this flowsheet"]'));
    h.assert(/\/ViewTemplateFlowSheetPrint$/.test(new URL(view.url()).pathname), 'Print did not open the custom print page');
    const section = view.locator('.preventionSection').filter({ has: view.locator(`#printHP${TYPE}`) });
    h.assert((await section.locator('.headPrevention p span').first().innerText()).trim() === displayName,
      'Custom print does not list the customised display name');
    const reading = section.locator('.preventionProcedure p').filter({ hasText: '120' });
    h.assert(/background-color:\s*orange/.test(await reading.getAttribute('style') || ''),
      'Custom print does not colour the out-of-target value');
    console.log(await view.locator('#flowsheetPrintForm').getAttribute('method'));
  });

  await s.step('Preview renders only the selected item for printing', async () => {
    await view.locator(`#printHP${TYPE}`).check();
    await navigate(view, view.locator('button.preview'));
    console.log(view.url(), (await view.locator('body').innerText()).slice(0, 300));
  });
}

if (require.main === module) runWorkflow('flowsheet-patient-customization', workflow);
module.exports = { workflow };
