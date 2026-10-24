#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Coverage plan §2.5 decision-support-alerts. User path: Schedule ▸ Search ▸ Master
// Record ▸ E-Chart ▸ right nav "Decision Support Alerts" (heading opens the guideline
// list, each warning opens the guideline detail) and Chart ▸ Dx Registry ▸ Resolve.
// Asserts the warning renders only while the owned patient matches the guideline, that
// the list and detail evaluate that patient (per-condition expected/actual/result), that a
// control patient without the diagnosis gets no warning and a failed evaluation, and that
// resolving the diagnosis removes the alert and flips the evaluation.
// Fixtures: CARLOS ships no guideline and has no UI to add one, so the run seeds one
// marker-titled Drools guideline (dxcodes icd9:2700 AND sex F) and seeds one active
// dxresearch row on the owned synthetic patient. The guideline reaches a provider only
// through dsGuidelineProviderMap, so it is mapped to a throwaway login and to nobody else:
// the chart is opened as that login, and the test login's own chart is checked to list
// nothing from it. A second owned patient is the control. Cleanup deletes only the marker
// guideline, its mapping, the owned patients' rows and the control patient; the throwaway
// login and its rows go with its fixture.
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { clickAndAwaitReload } = ui;
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { openChart, waitForNavbars } = require('./echart-navbar-modules-playwright-checks');
const { openSecondSession } = require('./lib/concurrency-support');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');
const { createOwnedPatient, removeOwnedPatient, newOwnedMarker } = require('./lib/owned-patient');

// An ICD-9 code nobody in the demo data carries, so the owned guideline can only fire
// on the owned patient.
const DX_CODE = '2700';

async function conditionRows(page) {
  const rows = page.locator('table.dsTable tr').filter({ has: page.locator('td') });
  return rows.evaluateAll(trs => trs.map(tr => [...tr.cells].map(td => td.innerText.trim())));
}

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const q = h.sqlString;
  // The throwaway login the guideline is mapped to, with the test provider's roles and programs.
  const fixture = throwawayLoginFixture({ sql, marker, provider, testUser: s.config.testUser });
  s.cleanup(() => fixture.cleanup());
  fixture.create();
  s.cleanup(() => {
    sql.execute(`DELETE FROM provider_default_program WHERE provider_no=${q(fixture.providerNo)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM provider_default_program WHERE provider_no=${q(fixture.providerNo)}`) === '0',
      'The throwaway default program was not removed');
  });
  sql.execute(`INSERT INTO program_provider (program_id,provider_no,role_id,team_id)
    SELECT program_id,${q(fixture.providerNo)},role_id,team_id FROM program_provider WHERE provider_no=${q(provider)}`);
  sql.execute(`INSERT INTO provider_default_program (provider_no,program_id,signnote)
    SELECT ${q(fixture.providerNo)},program_id,signnote FROM provider_default_program WHERE provider_no=${q(provider)}`);
  // The control patient: an owned patient without the diagnosis.
  const controlMarker = newOwnedMarker();
  const control = createOwnedPatient(sql, { marker: controlMarker, provider });
  s.cleanup(() => removeOwnedPatient(sql, control, controlMarker));
  const uuid = h.sqlString(marker);
  const title = `${marker} guideline "review"`;
  const author = 'FAKE-PW <b>check</b>';
  const warning = `${marker} review amino-acid transport plan`;
  const xml = `<guideline title="${marker} guideline &quot;review&quot;"><conditions>`
    + `<condition type="dxcodes" any="icd9:${DX_CODE}"/><condition type="sex" any="F"/>`
    + `</conditions><consequence><warning strength="warning">${warning}</warning></consequence></guideline>`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM dsGuidelineProviderMap WHERE guideline_uuid=${uuid} AND provider_no=${q(fixture.providerNo)};
      DELETE FROM dsGuidelines WHERE uuid=${uuid} AND title=${h.sqlString(title)};
      DELETE FROM dxresearch WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM dsGuidelineProviderMap WHERE guideline_uuid=${uuid})
      + (SELECT COUNT(*) FROM dsGuidelines WHERE uuid=${uuid})
      + (SELECT COUNT(*) FROM dxresearch WHERE demographic_no=${patient})`) === '0', 'Owned guideline fixtures were not removed');
  });
  const guideline = sql.value(`INSERT INTO dsGuidelines (uuid,title,version,author,xml,source,engine,dateStart,status)
    VALUES (${uuid},${h.sqlString(title)},1,${h.sqlString(author)},${h.sqlString(xml)},'local','drools',NOW(),'A');
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(guideline), 'The owned guideline was not created');
  sql.execute(`INSERT INTO dsGuidelineProviderMap (provider_no,guideline_uuid) VALUES (${q(fixture.providerNo)},${uuid})`);
  const dx = sql.value(`INSERT INTO dxresearch (demographic_no,start_date,update_date,status,dxresearch_code,coding_system,association,providerNo)
    VALUES (${patient},CURDATE(),NOW(),'A','${DX_CODE}','icd9',0,${h.sqlString(provider)}); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(dx), 'The owned diagnosis was not created');

  // The chart, its pop-ups and the control patient's chart are all the throwaway login's.
  const session = await openSecondSession(s, { label: 'guideline-provider', config: { ...s.config, testUser: fixture.username } });
  const popup = (page, locator, label) => ui.clickOpensPopup(page, locator,
    { context: session.context, recorder: s.recorder, label, timeout: 20000 });
  const throwawayChart = async (master) => {
    const page = await openChart(session.context, master, s.recorder, 20000);
    await waitForNavbars(page, 20000);
    return page;
  };
  let chart = await throwawayChart(session.master);
  const moduleIn = page => page.locator('#Guidelines');
  const alertIn = page => moduleIn(page).locator(`a.links[onclick*="method=detail&guidelineId=${guideline}&"]`);
  const headingIn = page => moduleIn(page).locator('.nav-menu-title a');
  const alert = () => alertIn(chart);
  const heading = () => headingIn(chart);

  await s.step("chart nav renders the owned guideline warning for the matching patient, and only for the guideline's provider", async () => {
    await heading().filter({ hasText: 'Decision Support Alerts' }).waitFor({ state: 'visible' });
    h.assert(await alert().count() === 1, 'The matching patient\'s chart does not show the guideline warning');
    h.assert((await alert().innerText()).trim() === title && await alert().getAttribute('title') === title,
      'The guideline warning does not carry the guideline title');
    h.assert(/demographic_no=(\d+)&/.exec(await alert().getAttribute('onclick'))[1] === patient,
      'The guideline warning links to another patient');
    // The guideline is mapped to the throwaway only: the test login's chart of the same patient shows nothing from it.
    const own = await s.chart();
    await headingIn(own).filter({ hasText: 'Decision Support Alerts' }).waitFor({ state: 'visible' });
    h.assert(await alertIn(own).count() === 0, "The test login's chart shows a guideline mapped to another provider");
  });

  let detail;
  await s.step('warning opens the guideline detail evaluating each condition for the owned patient', async () => {
    detail = await popup(chart, alert(), 'guideline-detail');
    const params = new URL(detail.url()).searchParams;
    h.assert(params.get('method') === 'detail' && params.get('guidelineId') === guideline
      && params.get('demographic_no') === patient, 'The warning opened another guideline or patient');
    h.assert((await detail.locator('body').innerText()).includes(`GUIDELINE PASSED: ${warning}`),
      'Guideline detail does not report the passed guideline consequence');
    h.assert((await detail.locator('div').first().innerText()).includes(`Workflow ${marker}`),
      'Guideline detail is not headed with the owned patient');
    const rows = await conditionRows(detail);
    // Columns: type, operator, Expected (the guideline's configured values, rendered as a list
    // of quoted values such as [icd9:'2700']), Actual (what the owned patient carries: its only
    // diagnosis is the seeded one), result.
    const expected = row => row[2].replace(/[[\]']/g, '');
    h.assert(JSON.stringify(rows.map(row => [row[0], row[1], expected(row)])) === JSON.stringify([
      ['dxcodes', 'any', `icd9:${DX_CODE}`], ['sex', 'any', 'F'],
    ]), `Guideline detail does not list the configured condition values as expected: ${JSON.stringify(rows.map(row => row[2]))}`);
    h.assert(JSON.stringify(rows.map(row => [row[3], row[4]])) === JSON.stringify([
      [`icd9:${DX_CODE}`, 'Passed'], ['F', 'Passed'],
    ]), 'Guideline detail did not evaluate both conditions as passed with the patient\'s actual values');
  });

  await s.step('the heading list evaluates the matching patient, renders stored labels as text, and More Info opens the assessment', async () => {
    const list = await popup(chart, heading(), 'guideline-list-matching');
    const row = list.locator('table.dsTable tr').filter({ hasText: title });
    await row.waitFor({ state: 'visible' });
    const cells = (await row.locator('td').allInnerTexts()).map(text => text.trim());
    h.assert(cells[1] === title && cells[2] === author && cells[4] === 'Active' && /^Passed\b/.test(cells[5]),
      'Guideline list does not show the matching patient as passed with literal stored labels');
    h.assert(await row.locator('b').count() === 0, 'The stored author was rendered as HTML');
    const url = new URL(await row.locator('a').getAttribute('href'), list.url());
    h.assert(url.searchParams.get('guidelineId') === guideline && url.searchParams.get('demographic_no') === patient,
      'The list detail link changed the guideline or patient');
    await Promise.all([list.waitForNavigation({ waitUntil: 'load' }), row.getByRole('link', { name: 'More Info', exact: true }).click()]);
    const assessment = await list.locator('body').innerText();
    h.assert(assessment.includes(`Workflow ${marker}`) && assessment.includes(`GUIDELINE PASSED: ${warning}`),
      "More Info does not open this patient's assessment of the guideline");
    await list.close();
  });

  await s.step('a control patient without the diagnosis gets no warning and a failed evaluation', async () => {
    // The schedule's patient search reuses the window it opened for the first patient, so the control's Master
    // Record is opened at the same address with the control's number.
    const address = new URL(session.master.url());
    address.searchParams.set('demographic_no', control);
    const master = await session.context.newPage();
    await master.goto(address.href, { waitUntil: 'domcontentloaded' });
    h.assert(new URL(master.url()).searchParams.get('demographic_no') === control, 'The control patient did not open');
    const controlChart = await throwawayChart(master);
    await headingIn(controlChart).filter({ hasText: 'Decision Support Alerts' }).waitFor({ state: 'visible' });
    h.assert(await alertIn(controlChart).count() === 0, 'The control patient shows the guideline warning');
    const list = await popup(controlChart, headingIn(controlChart), 'guideline-list-control');
    const row = list.locator('table.dsTable tr').filter({ hasText: title });
    await row.waitFor({ state: 'visible' });
    const cells = (await row.locator('td').allInnerTexts()).map(text => text.trim());
    h.assert(/^Failed\b/.test(cells[5]), 'Guideline list does not show the control patient as failed');
    await list.close();
    await controlChart.close();
    await master.close();
  });

  await s.step('resolving the diagnosis in the Dx Registry persists the change', async () => {
    const registry = await popup(chart, chart.locator('a[onclick*="setupDxResearch"]').first(), 'diagnosis-registry');
    const row = registry.locator(`#startdate1st${dx}`).locator('xpath=ancestor::tr[1]');
    await clickAndAwaitReload(registry, row.getByRole('link', { name: 'Resolve', exact: true }));
    await expectValue(sql, `SELECT status FROM dxresearch WHERE dxresearch_no=${dx} AND demographic_no=${patient}`, 'C',
      'Resolving the diagnosis did not persist');
    await registry.close();
  });

  await s.step('reopened chart no longer shows the warning for the resolved patient', async () => {
    await chart.close();
    chart = await throwawayChart(session.master);
    // The heading proves the module itself loaded, so a missing warning is not a missing module.
    await heading().filter({ hasText: 'Decision Support Alerts' }).waitFor({ state: 'visible' });
    h.assert(await alert().count() === 0, 'The guideline warning is still shown after the diagnosis was resolved');
  });

  await s.step('refreshed detail and the heading\'s guideline list evaluate the resolved patient', async () => {
    await detail.reload({ waitUntil: 'domcontentloaded' });
    h.assert(!(await detail.locator('body').innerText()).includes('GUIDELINE PASSED'),
      'Refreshed guideline detail still reports the guideline as passed');
    const rows = await conditionRows(detail);
    h.assert(rows.length === 2 && rows[0][0] === 'dxcodes' && rows[0][4] === 'Fail',
      'Refreshed guideline detail does not fail the resolved diagnosis condition');
    // Both remaining surfaces are checked before failing so one run reports each defect.
    const defects = [];
    // Each condition is evaluated on its own; the patient still matches the sex condition.
    if (!(rows[1][0] === 'sex' && rows[1][3] === 'F' && rows[1][4] === 'Passed')) {
      defects.push('guideline detail fails the sex condition the patient still matches');
    }
    try {
      const list = await popup(chart, heading(), 'guideline-list');
      h.assert(new URL(list.url()).searchParams.get('demographic_no') === patient, 'Guideline list opened for another patient');
      const row = list.locator('table.dsTable tr').filter({ hasText: title });
      await row.waitFor({ state: 'visible' });
      const cells = (await row.locator('td').allInnerTexts()).map(text => text.trim());
      h.assert(cells[0] === '1' && cells[2] === author && cells[4] === 'Active' && /^Failed\b/.test(cells[5]),
        'Guideline list does not show the owned guideline as active and failed for the resolved patient');
    } catch (error) {
      defects.push(`Decision Support Alerts heading: ${error.message}`);
    }
    h.assert(!defects.length, defects.join('; '));
  });
}
if (require.main === module) runWorkflow('decision-support-guidelines', workflow);
module.exports = { workflow };
