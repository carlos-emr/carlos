#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Coverage plan §2.5 flowsheet-patient-customization: the per-patient flowsheet editor.
//
// User path: Schedule ▸ Master Record ▸ E-Chart ▸ Measurements ▸ Health Tracker ▸ Add Measurements
// (Edit Flowsheet, patient scope) ▸ Save twice; ▸ pencil (Update Flowsheet) ▸ warning rule + target
// colour ▸ Update; Health Tracker ▸ Edit ▸ Revert, and ▸ Custom ▸ trash; Health Tracker ▸ Print.
// Asserts the flowsheet_customization rows each control writes (add / update, scoped to this patient
// and provider, archived on removal), that the tracker renders the added items, that the warning rule
// and the target colour fire on a seeded value and stop after Revert, that the trash removes only its
// item, and that custom print lists the patient's customised item and reading and previews it.
// health-tracker seeds its customization rows with SQL; flowsheet-admin covers clinic-wide editing.
// Fixtures: the owned FAKE-PW patient (runWorkflow) and one seeded WT and HT reading carrying the
// marker. Cleanup deletes this patient's tracker customization rows and readings, asserting both.
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

async function navigate(page, locator) {
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 20000 }),
    locator.click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
  await h.assertNotErrorPage(page, new URL(page.url()).pathname);
}

async function workflow(s, { editorOnly = false } = {}) {
  const { sql, patient, provider, marker } = s;
  const items = { WT: `${marker} Weight`, HT: `${marker} Height` };
  const warning = `${marker} weight above 100`;
  const rows = (where) => `SELECT COUNT(*) FROM flowsheet_customization WHERE flowsheet='tracker'
    AND demographic_no=${patient} AND provider_no=${h.sqlString(provider)} ${where}`;
  const addRow = (type) => `AND action='add' AND measurement IS NULL
    AND payload LIKE ${h.sqlString(`%measurement_type="${type}"%`)} AND payload LIKE ${h.sqlString(`%${items[type]}%`)}`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM flowsheet_customization WHERE flowsheet='tracker' AND demographic_no=${patient};
      DELETE FROM measurements WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM flowsheet_customization WHERE demographic_no=${patient}`) === '0',
      'Owned flowsheet customization rows were not removed');
    h.assert(sql.value(`SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient}`) === '0',
      'Owned readings were not removed');
  });

  const chart = await s.chart();
  const trackerLink = chart.locator('#measurementslist li a[onclick*="ViewHealthTracker"]').first();
  await trackerLink.waitFor({ state: 'visible' });
  let page = await s.popup(chart, trackerLink, 'health-tracker');
  await page.waitForLoadState('networkidle').catch(() => {});
  const openTracker = async (label) => {
    await page.close();
    page = await s.popup(chart, trackerLink, label);
    await page.waitForLoadState('networkidle').catch(() => {});
  };
  const editorRow = (type) => page.locator('#measurementTbl tbody tr').filter({ hasText: items[type] });

  await s.step('Add Measurements saves one patient-scoped add customization per item', async () => {
    await navigate(page, page.getByRole('link', { name: 'Add Measurements' }));
    h.assert(new URL(page.url()).searchParams.get('demographic') === patient,
      'Add Measurements did not open the editor for this patient');
    for (const type of Object.keys(items)) {
      if (!await page.locator('#add').isVisible()) await page.locator('#myTab a[href="#add"]').click();
      await page.locator('#add select[name="measurement"]').selectOption(type);
      await page.locator('#display_name').fill(items[type]);
      await page.locator('#add input[name="guideline"]').fill(marker);
      await navigate(page, page.locator('#FlowSheetCustomActionForm input[type="submit"][value="Save"]'));
      await expectValue(sql, rows(`${addRow(type)} AND archived='0'`), '1',
        `The editor did not store one patient-scoped add customization for ${type}`);
      await editorRow(type).waitFor({ state: 'visible' });
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM flowsheet_customization WHERE demographic_no=${patient}`) === '2',
      'Two Saves stored other than two customizations');
  });

  await s.step('Update Flowsheet stores a warning rule and a target colour for the item', async () => {
    // Supply the parent tracker container in this standalone popup, so the served JSP's
    // height assignment is exercised (JSP EL must not consume the JavaScript height).
    await page.addInitScript(() => {
      document.addEventListener('DOMContentLoaded', () => {
        const tracker = document.createElement('div');
        tracker.id = 'trackerSlim';
        tracker.hidden = true;
        document.body.appendChild(tracker);
      }, { once: true });
    });
    await navigate(page, editorRow('WT').locator('a[title="Edit"]'));
    h.assert(/\/ViewUpdateFlowsheet$/.test(new URL(page.url()).pathname), 'The pencil did not open Update Flowsheet');
    await page.waitForFunction(() => parseFloat(document.getElementById('trackerSlim').style.height) > 0);
    await page.locator('select[name="strength1"]').selectOption('warning');
    await page.locator('input[name="text1"]').fill(warning);
    await page.locator('select[name="type1c1"]').selectOption('lastValueAsInt');
    await page.locator('input[name="value1c1"]').fill('>100');
    await page.locator('select[name="targettype1c1"]').selectOption('getDataAsDouble');
    await page.locator('input[name="targetvalue1c1"]').fill('>100');
    await page.locator('input[name="col1"][value="HIGH"]').check();
    await navigate(page, page.locator('input[type="submit"][value="Update"]'));
    await expectValue(sql, rows(`AND action='update' AND archived='0' AND measurement='WT'`), '1',
      'Update Flowsheet did not store one patient-scoped update customization');
    const payload = sql.value(`SELECT payload FROM flowsheet_customization WHERE flowsheet='tracker'
      AND demographic_no=${patient} AND action='update' AND archived='0'`);
    h.assert(payload.includes(`<recommendation strength="warning" message="${warning}">`)
      && payload.includes('<condition type="lastValueAsInt" param="" value="&gt;100" />'),
    'The stored update lost the warning rule entered on Update Flowsheet');
    h.assert(payload.includes('<rule indicationColor="HIGH">')
      && payload.includes('<condition type="getDataAsDouble" param="" value="&gt;100" />'),
    'The stored update lost the target colour entered on Update Flowsheet');
  });

  // The focused editor check stops here; the default workflow still tests tracker and print.
  if (editorOnly) return;

  const seed = (type, value) => sql.value(`INSERT INTO measurements(type,demographicNo,providerNo,dataField,
    measuringInstruction,comments,dateObserved,dateEntered) VALUES(${h.sqlString(type)},${patient},
    ${h.sqlString(provider)},${h.sqlString(value)},'',${h.sqlString(marker)},'2026-09-01 00:00:00',NOW());
    SELECT LAST_INSERT_ID()`);
  const reading = { WT: seed('WT', '120'), HT: seed('HT', '170') };
  h.assert(Object.values(reading).every(id => /^[1-9]\d*$/.test(id)), 'The seeded readings were not created');
  const card = (type) => page.locator(`#wrap-${type}`);
  const value = (type) => card(type).locator(`.history-value[data-measurement-id="${reading[type]}"]`);

  await s.step('the tracker renders both items, the warning and the target colour on a seeded value', async () => {
    await openTracker('health-tracker-customised');
    for (const type of Object.keys(items)) {
      h.assert((await card(type).locator('.measurement-label').innerText()).trim() === items[type],
        `The ${type} tracker card does not carry the display name saved in the editor`);
      await value(type).waitFor({ state: 'visible' });
    }
    h.assert((await card('WT').locator('.alert-danger').innerText()).includes(warning),
      'The warning rule did not fire on a value above its threshold');
    h.assert((await value('WT').locator('.indicator-label').innerText()).trim() === 'HIGH',
      'The target colour rule did not mark the seeded value HIGH');
    h.assert(/orange/.test(await value('WT').locator('.indicator').getAttribute('style') || ''),
      'The seeded value is not drawn in the HIGH indicator colour');
    h.assert(await card('HT').locator('.alert-danger, .indicator').count() === 0,
      'The WT rules leaked onto the HT item');
  });

  await s.step('Revert archives the update and the tracker stops warning and colouring', async () => {
    await navigate(page, page.locator('a[title="Edit Flowsheet"]'));
    const revert = editorRow('WT').locator('a[title="Revert to settings from higher scope"]');
    const dialogs = await h.withExpectedDialogs(page, () => navigate(page, revert));
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Revert did not ask for confirmation');
    await expectValue(sql, rows(`AND action='update' AND measurement='WT' AND archived='1' AND archived_date IS NOT NULL`),
      '1', 'Revert did not archive the update customization');
    h.assert(sql.value(rows(`AND archived='0'`)) === '2', 'Revert changed the add customizations');
    await navigate(page, page.locator('a[title^="go back to"]'));
    h.assert(/\/ViewHealthTracker$/.test(new URL(page.url()).pathname), 'The editor did not return to the tracker');
    await value('WT').waitFor({ state: 'visible' });
    h.assert(await card('WT').locator('.alert-danger, .indicator').count() === 0,
      'The reverted warning or target colour still renders');
  });

  await s.step('the Custom list trash archives only its item and the tracker drops that card', async () => {
    await navigate(page, page.locator('a[title="Edit Flowsheet"]'));
    await page.locator('#myTab a[href="#custom"]').click();
    const entry = page.locator('#custom tbody tr').filter({ hasText: /\bHT\b/ });
    await entry.waitFor({ state: 'visible' });
    await navigate(page, entry.locator('a:has(.fa-trash)'));
    await expectValue(sql, rows(`${addRow('HT')} AND archived='1' AND archived_date IS NOT NULL`), '1',
      'The trash did not archive the HT add customization');
    h.assert(sql.value(rows(`${addRow('WT')} AND archived='0'`)) === '1', 'The trash archived the other item too');
    await navigate(page, page.locator('a[title^="go back to"]'));
    await card('WT').waitFor({ state: 'visible' });
    h.assert(await card('HT').count() === 0, 'The removed HT item still renders on the tracker');
  });

  await s.step('custom print lists the customised item and its reading', async () => {
    await navigate(page, page.locator('a[title="Print this flowsheet"]'));
    h.assert(/\/ViewTemplateFlowSheetPrint$/.test(new URL(page.url()).pathname), 'Print did not open the custom print page');
    const section = page.locator('.preventionSection').filter({ has: page.locator('#printHPWT') });
    h.assert(await section.count() === 1,
      'Custom print from the Health Tracker does not list the patient\'s customised WT item');
    h.assert(await page.locator('#printHPHT').count() === 0,
      'Custom print brought back the removed HT item');
    h.assert((await section.locator('.headPrevention p span').first().innerText()).trim() === items.WT,
      'Custom print does not show the customised display name');
    await section.locator('.preventionProcedure p').filter({ hasText: '120' }).waitFor({ state: 'visible' });
  });

  await s.step('Preview renders the selected item for printing', async () => {
    await page.locator('label[for="printHPWT"]').click();
    const response = page.waitForResponse(r => r.request().method() === 'POST'
      && new URL(r.url()).pathname.endsWith('/ViewTemplateFlowSheetPrint'));
    await navigate(page, page.locator('button.preview'));
    h.assert((await response).status() === 200, 'Custom print Preview was refused');
    const section = page.locator('.preventionSection').filter({ hasText: items.WT });
    await section.locator('.preventionProcedure p').filter({ hasText: '120' }).waitFor({ state: 'visible' });
    await page.getByRole('button', { name: 'Print' }).waitFor({ state: 'visible' });
  });
}

if (require.main === module) runWorkflow('flowsheet-patient-customization', workflow);
module.exports = { workflow };
