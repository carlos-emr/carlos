#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Gap check (clinical): flowsheet customization LAYERS -- clinic ("All Patients") and provider ("Your
 * Patients") scope -- on a flowsheet the run creates.
 *
 * User path: Schedule ▸ Administration ▸ Create New Flowsheet (name, dx trigger, colours ▸ Create) ▸ the
 * flowsheet editor ("Your Patients" scope) ▸ Add ▸ measurement, display name, guideline ▸ Save; "All
 * Patients" ▸ Add ▸ a second measurement ▸ Save; Master Record ▸ E-Chart ▸ Measurements ▸ the flowsheet
 * link (ViewTemplateFlowSheet); editor ▸ Your Patients ▸ "Hide this measurement" / "Show this measurement".
 * flowsheet-patient-customization covers the PATIENT scope on the built-in tracker and flowsheet-admin
 * creates a flowsheet and toggles one built-in measurement by URL; no check adds items at the clinic or
 * provider scope or proves how the layers combine. A clinic-scope change reaches every patient, so a
 * wrong scope on a write is a clinic-wide clinical-content error.
 *
 * Asserts: Create stores the FlowSheetUserCreated row (trigger, colours); a provider-scope Add
 * writes one flowsheet_customization row carrying the provider and demographic 0, and an All Patients Add
 * one with an EMPTY provider and demographic 0; the owned patient (dx trigger seeded) sees the flowsheet in
 * the chart and BOTH items, with their display names and the patient's reading, on it; hiding the clinic
 * item at the provider scope writes a provider-scoped delete, removes it from the patient's flowsheet and
 * leaves the clinic row and the All Patients list intact; Show restores it.
 *
 * Fixtures: the owned FAKE-PW patient with one WT reading and one dxresearch row (a rarely used ICD-9 code
 * chosen at run time), and ONE flowsheet created through the UI whose display name is the run marker.
 * Cleanup deletes the flowsheet's customization rows and FlowSheetUserCreated row, the dx and the readings
 * and asserts it. Note the application keeps a deleted flowsheet's trigger registered in memory until it
 * reloads its configuration, so the check finds ITS flowsheet by name, never by trigger.
 * Coverage plan §2.5 flowsheet-admin (gap-clinical).
 */
const h = require('./lib/playwright-harness');
const {clickOpensPopupOrNavigates} = require('./lib/playwright-ui');
const {revealAuditLink} = require('./lib/playwright-link-audit');
const {runWorkflow, expectValue} = require('./lib/workflow-session');

async function workflow(s) {
  const {sql, patient, provider, marker} = s;
  const q = h.sqlString;
  const code = sql.value(`SELECT icd9 FROM icd9 WHERE icd9 REGEXP '^[0-9]{3,4}$'
    AND icd9 NOT IN (SELECT dxresearch_code FROM dxresearch WHERE coding_system='icd9') ORDER BY RAND() LIMIT 1`);
  h.assert(/^\d{3,4}$/.test(code), 'No unused ICD-9 code is available for the flowsheet trigger');
  const created = () => sql.rows(`SELECT id, name, dxcodeTriggers, warningColour, recommendationColour, createdBy, archived
    FROM FlowSheetUserCreated WHERE displayName=${q(marker)} ORDER BY id`);
  const sheet = () => created()[0] && created()[0][1];
  s.cleanup(() => {
    for (const row of created()) sql.execute(`DELETE FROM flowsheet_customization WHERE flowsheet=${q(row[1])}`);
    sql.execute(`DELETE FROM FlowSheetUserCreated WHERE displayName=${q(marker)};
      DELETE FROM dxresearch WHERE demographic_no=${patient};
      DELETE FROM measurements WHERE demographicNo=${patient}`);
    h.assert(created().length === 0 && sql.value(`SELECT (SELECT COUNT(*) FROM dxresearch WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient})`) === '0', 'Owned flowsheet rows were not removed');
  });
  const custom = (where = '') => sql.rows(`SELECT action, measurement, provider_no, demographic_no, archived FROM flowsheet_customization
    WHERE flowsheet=${q(sheet() || '')} ${where} ORDER BY id`).map(row => row.map(v => (v === null ? '' : v)));
  const names = {WT: `${marker} Weight`, HT: `${marker} Height`};

  const {page: admin} = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    {context: s.context, recorder: s.recorder, label: 'flowsheet-administration', timeout: 20000});
  const link = admin.getByRole('link', {name: 'Create New Flowsheet', includeHidden: true}).first();
  await revealAuditLink(admin, link, 20000);
  await link.click();
  const iframe = admin.locator('#dynamic-content iframe').first();
  await iframe.waitFor();
  const frame = await (await iframe.elementHandle()).contentFrame();
  h.assert(frame, 'Create New Flowsheet did not load in the administration frame');
  const go = async action => {
    await Promise.all([frame.waitForNavigation({timeout: 20000}), action()]);
    await frame.waitForLoadState('load');
    await h.assertNotErrorPage(await iframe.elementHandle().then(handle => handle.contentFrame()).then(() => admin), 'flowsheet editor');
  };
  const editorRow = display => frame.locator('#myTab').locator('xpath=..').locator('tbody tr', {hasText: display});

  await s.step('Create New Flowsheet stores the flowsheet and opens its editor', async () => {
    await frame.locator('#displayName').fill(marker);
    await frame.locator('#dxcodeTriggers').fill(`icd9:${code}`);
    await frame.locator('#warningColour').fill('red');
    await frame.locator('#recommendationColour').fill('yellow');
    await go(() => frame.locator('input[type="submit"][value="Create"]').click());
    const rows = created();
    h.assert(rows.length === 1, `Create stored ${rows.length} flowsheet rows, expected one`);
    const [, name, trigger, warning, recommendation, , archived] = rows[0];
    h.assert(trigger === `icd9:${code}` && warning === 'red' && recommendation === 'yellow' && archived === '0',
      'The created flowsheet row lost the trigger or the colours');
    h.assert((await frame.locator('body').innerText()).includes(`(${name})`), 'Create did not open the editor of the new flowsheet');
  });

  await s.step('a Your Patients add writes a provider-scope row; an All Patients add writes a clinic-scope row', async () => {
    const add = async (type, scopeLink) => {
      if (scopeLink) await go(() => frame.getByRole('link', {name: scopeLink, exact: true}).click());
      await frame.locator('#myTab a[href="#add"]').click();
      await frame.locator('#add select[name="measurement"]').selectOption(type);
      await frame.locator('#display_name').fill(names[type]);
      await frame.locator('#add input[name="guideline"]').fill(`${marker} guideline ${type}`);
      await go(() => frame.locator('#FlowSheetCustomActionForm input[type="submit"][value="Save"]').click());
    };
    await add('WT', null);
    h.assert(JSON.stringify(custom()) === JSON.stringify([['add', '', provider, '0', '0']]),
      `The Your Patients add should store one add row for the provider with demographic 0, found ${JSON.stringify(custom())}`);
    await add('HT', 'All Patients');
    const rows = custom();
    h.assert(rows.length === 2 && rows[1][0] === 'add' && rows[1][2] === '' && rows[1][3] === '0',
      `The All Patients add should store a clinic-scope row (no provider, demographic 0), found ${JSON.stringify(rows)}`);
    h.assert(await frame.locator('tbody tr', {hasText: names.HT}).count() === 1, 'The All Patients list does not show the clinic item');
    h.assert(await frame.locator('tbody tr', {hasText: names.WT}).count() === 0, 'The All Patients list shows the provider-only item');
  });

  sql.execute(`INSERT INTO dxresearch (demographic_no,start_date,update_date,status,dxresearch_code,coding_system,association)
      VALUES (${patient},CURDATE(),NOW(),'A',${q(code)},'icd9',0);
    INSERT INTO measurements(type,demographicNo,providerNo,dataField,measuringInstruction,comments,dateObserved,dateEntered)
      VALUES ('WT',${patient},${q(provider)},'80','',${q(marker)},'2026-09-01 00:00:00',NOW())`);
  const chart = await s.chart();
  let page;
  const openFlowsheet = async label => {
    if (page && !page.isClosed()) await page.close();
    const entry = chart.locator('#leftNavBar a').filter({hasText: marker.slice(0, 14)}).first();
    await entry.waitFor({state: 'attached', timeout: 40000})
      .catch(() => { throw new Error(`The chart's Measurements module does not list the created flowsheet for a patient carrying its dx trigger (icd9:${code})`); });
    page = await s.popup(chart, entry, label);
    await page.waitForLoadState('networkidle').catch(() => {});
    h.assert(new URL(page.url()).searchParams.get('template') === sheet(), 'The chart entry opened a different flowsheet');
    return page;
  };

  await s.step('the chart lists the flowsheet for the dx-triggered patient and shows both layers\' items and the reading', async () => {
    await openFlowsheet('flowsheet');
    const text = await page.locator('body').innerText();
    h.assert(text.includes(names.HT) && text.includes(names.WT), 'The flowsheet is missing the clinic-scope or the provider-scope item');
    h.assert(/WT: 80/.test(text), 'The flowsheet does not show the patient\'s weight reading under the provider-scope item');
  });

  await s.step('hiding the clinic item at the provider scope drops it for the patient and leaves the clinic layer intact', async () => {
    await go(() => frame.getByRole('link', {name: 'Your Patients', exact: true}).click());
    const row = frame.locator('tbody tr', {hasText: names.HT});
    h.assert(await row.count() === 1, 'The Your Patients list does not show the inherited clinic item');
    await go(() => row.getByTitle('Hide this measurement').click());
    await expectValue(sql, `SELECT COUNT(*) FROM flowsheet_customization WHERE flowsheet=${q(sheet())} AND action='delete'
      AND measurement='HT' AND provider_no=${q(provider)} AND demographic_no=0 AND archived='0'`, '1',
    'Hide stored no provider-scope delete for the clinic item');
    h.assert(sql.value(`SELECT COUNT(*) FROM flowsheet_customization WHERE flowsheet=${q(sheet())} AND action='add'
      AND measurement IS NULL AND provider_no='' AND archived='0'`) === '1', 'Hiding at the provider scope changed the clinic row');
    await openFlowsheet('flowsheet-hidden');
    const text = await page.locator('body').innerText();
    h.assert(!text.includes(names.HT), 'The hidden clinic item is still on the patient\'s flowsheet');
    h.assert(text.includes(names.WT), 'Hiding the clinic item also removed the provider item');
    await go(() => frame.getByRole('link', {name: 'All Patients', exact: true}).click());
    h.assert(await frame.locator('tbody tr', {hasText: names.HT}).count() === 1, 'The clinic list lost the item another provider\'s patients still see');
  });

  await s.step('Show this measurement restores the clinic item for the patient', async () => {
    await go(() => frame.getByRole('link', {name: 'Your Patients', exact: true}).click());
    await go(() => frame.locator('tbody tr', {hasText: names.HT}).getByTitle('Show this measurement').click());
    await expectValue(sql, `SELECT COUNT(*) FROM flowsheet_customization WHERE flowsheet=${q(sheet())} AND action='delete'
      AND measurement='HT' AND provider_no=${q(provider)} AND archived='0'`, '0', 'Show left the provider-scope delete active');
    await openFlowsheet('flowsheet-restored');
    h.assert((await page.locator('body').innerText()).includes(names.HT), 'The restored clinic item is not back on the patient\'s flowsheet');
  });
}

if (require.main === module) runWorkflow('gap-clinical-flowsheet-scopes', workflow, {openPatient: true});
module.exports = {workflow};
