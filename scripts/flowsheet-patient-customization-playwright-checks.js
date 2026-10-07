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
const { authzReadFixture } = require('./lib/authz-read-fixture');
const { signIn, probe, forbiddenByApp } = require('./lib/authz-read-probe');

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
  let printUrl;
  const rows = (where) => `SELECT COUNT(*) FROM flowsheet_customization WHERE flowsheet='tracker'
    AND demographic_no=${patient} AND provider_no=${h.sqlString(provider)} ${where}`;
  const addRow = (type) => `AND action='add' AND measurement IS NULL
    AND payload LIKE ${h.sqlString(`%measurement_type="${type}"%`)} AND payload LIKE ${h.sqlString(`%${items[type]}%`)}`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM flowsheet_customization WHERE flowsheet='tracker' AND demographic_no=${patient};
      DELETE FROM measurements WHERE demographicNo=${patient};
      DELETE FROM FlowSheetUserCreated WHERE scope='patient' AND scopeDemographicNo=${patient} AND displayName=${h.sqlString(marker)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM flowsheet_customization WHERE demographic_no=${patient}`) === '0',
      'Owned flowsheet customization rows were not removed');
    h.assert(sql.value(`SELECT COUNT(*) FROM FlowSheetUserCreated WHERE scope='patient' AND scopeDemographicNo=${patient} AND displayName=${h.sqlString(marker)}`) === '0',
      'Owned scoped definition was not removed');
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
    printUrl = page.url();
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
  await s.step('a missing deletion target in a scoped definition does not discard patient additions', async () => {
    const scopedXml = `<flowsheet name="tracker" display_name="${marker}" ds_rules="tracker.drl" top_HTML="" warning_colour="#E00000" recommendation_colour="yellow">`
      + `<indicator key="HIGH" colour="orange"/><item measurement_type="A1C" display_name="${marker} Scoped A1C" graphable="yes" value_name="A1C" guideline=""/></flowsheet>`;
    h.assert(sql.value(`SELECT COUNT(*) FROM FlowSheetUserCreated WHERE scope='patient' AND scopeDemographicNo=${patient}`) === '0',
      'The owned patient already has a scoped definition');
    sql.execute(`INSERT INTO FlowSheetUserCreated
      (name,displayName,archived,createdDate,createdBy,scope,scopeDemographicNo,template,xmlContent)
      VALUES ('PWFS',${h.sqlString(marker)},0,CURDATE(),${h.sqlString(provider)},'patient',${patient},'tracker',${h.sqlString(scopedXml)});
      INSERT INTO flowsheet_customization (flowsheet,action,measurement,payload,provider_no,demographic_no,create_date,archived)
      VALUES ('tracker','delete','BP',NULL,${h.sqlString(provider)},${h.sqlString(String(patient))},NOW(),0)`);
    await page.goto(printUrl, { waitUntil: 'networkidle' });
    await h.assertNotErrorPage(page, 'scoped print with absent deletion target');
    h.assert(await page.locator('#printHPA1C').count() === 1, 'Print lost the resolved scoped definition');
    h.assert(await page.getByText(`${marker} Scoped A1C`, { exact: true }).count() === 1, 'Print lost the scoped item label');
    h.assert(await page.locator('#printHPWT').count() === 1, 'An absent deletion target discarded the patient weight item');
    h.assert(await page.locator('#printHPBP').count() === 0, 'Print introduced an item absent from the scoped definition');
  });

  await s.step('an absent addition anchor appends the item and an absent update target stays absent', async () => {
    sql.execute(`DELETE FROM flowsheet_customization WHERE flowsheet='tracker' AND demographic_no=${patient}
      AND action='delete' AND measurement='BP';
      UPDATE flowsheet_customization SET measurement='BP' WHERE flowsheet='tracker' AND demographic_no=${patient}
      AND action='add' AND archived=0 AND payload LIKE ${h.sqlString('%measurement_type="WT"%')};
      INSERT INTO flowsheet_customization (flowsheet,action,measurement,payload,provider_no,demographic_no,create_date,archived)
      VALUES ('tracker','update','BP',${h.sqlString('<item measurement_type="BP" display_name="Absent BP"/>')},
        ${h.sqlString(provider)},${h.sqlString(String(patient))},NOW(),0)`);
    await page.goto(printUrl, { waitUntil: 'networkidle' });
    await h.assertNotErrorPage(page, 'scoped print with absent addition anchor');
    h.assert(await page.locator('#printHPA1C').count() === 1, 'Print lost the scoped definition after adding an item');
    const weight = page.locator('.preventionSection').filter({ has: page.locator('#printHPWT') });
    h.assert(await weight.count() === 1, 'Print discarded the item whose anchor is absent');
    await weight.locator('.preventionProcedure p').filter({ hasText: '120' }).waitFor({ state: 'visible' });
    h.assert(await page.locator('#printHPBP').count() === 0, 'An update reintroduced an absent item');
  });

  await s.step('patient locks refuse both print selection and preview without disclosing custom readings', async () => {
    const fixture = authzReadFixture({ sql, marker, provider, testUser: s.config.testUser });
    s.cleanup(() => fixture.cleanup());
    for (const object of ['_demographic', '_eChart']) {
      const login = fixture.addLogin('doctor');
      const restricted = await signIn(s, login);
      try {
        const selection = await restricted.page.goto(printUrl, { waitUntil: 'networkidle' });
        h.assert(selection.status() === 200, `Unlocked doctor could not open print before applying ${object}`);
        await restricted.page.locator('label[for="printHPWT"]').click();
        const preview = restricted.page.waitForResponse(r => r.request().method() === 'POST'
          && new URL(r.url()).pathname.endsWith('/ViewTemplateFlowSheetPrint'));
        await navigate(restricted.page, restricted.page.locator('button.preview'));
        h.assert((await preview).status() === 200, `Unlocked doctor could not preview before applying ${object}`);
        await restricted.page.locator('.preventionSection').filter({ hasText: items.WT })
          .locator('.preventionProcedure p').filter({ hasText: '120' }).waitFor({ state: 'visible' });
        fixture.lockPatient(login, patient, [object]);
        for (const method of ['GET', 'HEAD', 'POST']) {
          const url = new URL(printUrl);
          if (method === 'POST') {
            url.searchParams.set('printView', 'true');
            url.searchParams.set('printHP', 'WT');
          }
          const answer = await probe(restricted.context, url.href, { method, needles: [marker, items.WT] });
          h.assert(forbiddenByApp(answer), `${object} patient lock did not refuse ${method} print in CARLOS (HTTP ${answer.status})`);
          h.assert(answer.found.length === 0, 'A locked patient print request disclosed the custom reading label');
        }
      } finally {
        await restricted.context.close();
      }
    }
  });

}

if (require.main === module) runWorkflow('flowsheet-patient-customization', workflow);
module.exports = { workflow };
