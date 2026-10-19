#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Flowsheet editor: which Custom List rows are locked by a HIGHER scope (issue #3962).
 *
 * User path: Master Record > E-Chart > Measurements > Health Tracker > Add Measurements (the patient
 * editor of the built-in `tracker` flowsheet) > Your Patients > All Patients.
 *
 * EditFlowsheet.jsp used to carry its own copy of the clinic/provider/patient classification and now asks
 * FlowSheetCustomizationService.getScopeLevelName, the one definition the server-side archive guard also
 * uses. This check proves the page still draws the right lock on each layer through that service call:
 * a clinic row (no provider, demographic 0) is locked from the patient and provider editors, a provider
 * row is locked from the patient editor only, a patient row is never locked, and nothing is locked from
 * the All Patients editor. A locked row shows a lock whose title names the level that created it and no
 * delete link; an unlocked row shows the delete link.
 *
 * The null-field cases of the service itself (a NULL provider or demographic is "no provider" / "no
 * patient") are covered by FlowSheetCustomizationServiceUnitTest: the DAO queries only ever return rows
 * with provider '' and demographic '0' for the clinic layer, so a row with NULL columns cannot be put on
 * this page to look at.
 *
 * Fixtures: the owned FAKE-PW patient and three flowsheet_customization rows on `tracker` written by SQL
 * (clinic, provider, patient), each carrying the run marker in its display name. Cleanup deletes the rows
 * by marker and asserts they are gone.
 */
const h = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');

async function navigate(page, locator) {
  await Promise.all([
    page.waitForNavigation({waitUntil: 'domcontentloaded', timeout: 20000}),
    locator.click(),
  ]);
  await page.waitForLoadState('networkidle', {timeout: 20000}).catch(() => {});
  await h.assertNotErrorPage(page, new URL(page.url()).pathname);
}

async function workflow(s) {
  const {sql, patient, provider, marker} = s;
  const q = h.sqlString;
  const items = {
    clinic: {type: 'HT', display: `${marker} Clinic`},
    provider: {type: 'WT', display: `${marker} Provider`},
    patient: {type: 'BP', display: `${marker} Patient`},
  };
  const leftover = () => sql.value(`SELECT COUNT(*) FROM flowsheet_customization WHERE flowsheet='tracker'
    AND payload LIKE ${q(`%${marker}%`)}`);
  s.cleanup(() => {
    sql.execute(`DELETE FROM flowsheet_customization WHERE flowsheet='tracker' AND payload LIKE ${q(`%${marker}%`)}`);
    h.assert(leftover() === '0', 'The scope-lock customization rows were not removed');
  });

  const seed = (level, owner, demographic) => sql.execute(`INSERT INTO flowsheet_customization
    (flowsheet,action,measurement,payload,provider_no,demographic_no,create_date,archived)
    VALUES ('tracker','add',NULL,${q(`<item measurement_type="${items[level].type}" display_name="${items[level].display}" graphable="no" value_name="${items[level].type}" guideline=""/>`)},
      ${q(owner)},${demographic},NOW(),0)`);
  seed('clinic', '', 0);
  seed('provider', provider, 0);
  seed('patient', provider, patient);
  h.assert(leftover() === '3', 'The three scope-lock customization rows were not created');

  const chart = await s.chart();
  const trackerLink = chart.locator('#measurementslist li a[onclick*="ViewHealthTracker"]').first();
  await trackerLink.waitFor({state: 'visible'});
  const page = await s.popup(chart, trackerLink, 'health-tracker');
  await page.waitForLoadState('networkidle').catch(() => {});

  const row = level => page.locator('table.table-sm tbody tr', {hasText: items[level].display});
  const expectLocked = async (level, createdAt, where) => {
    const r = row(level);
    h.assert(await r.count() === 1, `${where}: the Custom List does not show the ${level} item exactly once`);
    const lock = r.locator('i.fa-lock');
    h.assert(await lock.count() === 1, `${where}: the ${level} item is not locked`);
    const title = await lock.getAttribute('title');
    h.assert(title === `Cannot remove - created at ${createdAt} level`,
      `${where}: the ${level} item's lock says "${title}", expected it created at ${createdAt} level`);
    h.assert(await r.locator('a[onclick*="archiveMod"]').count() === 0, `${where}: the locked ${level} item still has a delete link`);
  };
  const expectRemovable = async (level, where) => {
    const r = row(level);
    h.assert(await r.count() === 1, `${where}: the Custom List does not show the ${level} item exactly once`);
    h.assert(await r.locator('i.fa-lock').count() === 0, `${where}: the ${level} item is locked`);
    h.assert(await r.locator('a[onclick*="archiveMod"]').count() === 1, `${where}: the ${level} item has no delete link`);
  };

  await s.step('the patient editor locks the clinic and provider items and leaves the patient item removable', async () => {
    await navigate(page, page.getByRole('link', {name: 'Add Measurements'}));
    h.assert(new URL(page.url()).searchParams.get('demographic') === String(patient),
      'Add Measurements did not open the editor for this patient');
    await expectLocked('clinic', 'clinic', 'patient editor');
    await expectLocked('provider', 'provider', 'patient editor');
    await expectRemovable('patient', 'patient editor');
  });

  await s.step('Your Patients locks the clinic item and leaves the provider item removable', async () => {
    await navigate(page, page.getByRole('link', {name: 'Your Patients', exact: true}));
    await expectLocked('clinic', 'clinic', 'Your Patients editor');
    await expectRemovable('provider', 'Your Patients editor');
    h.assert(await row('patient').count() === 0, 'The Your Patients list shows a single patient\'s item');
  });

  await s.step('All Patients locks nothing', async () => {
    await navigate(page, page.getByRole('link', {name: 'All Patients', exact: true}));
    await expectRemovable('clinic', 'All Patients editor');
    h.assert(await row('provider').count() === 0, 'The All Patients list shows a provider\'s item');
    h.assert(await page.locator('i.fa-lock').count() === 0, 'The All Patients editor shows a locked item');
  });
}

if (require.main === module) runWorkflow('flowsheet-scope-lock', workflow, {openPatient: true});
module.exports = {workflow};
