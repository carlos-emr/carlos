#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Coverage plan §2.5 decision-support-alerts. User path: Schedule ▸ Search ▸ Master
// Record ▸ E-Chart ▸ right nav "Decision Support Alerts" (heading opens the guideline
// list, each warning opens the guideline detail) and Chart ▸ Dx Registry ▸ Resolve.
// Asserts the warning renders only while the owned patient matches the guideline, that
// the list and detail evaluate that patient (per-condition expected/actual/result) and
// that resolving the diagnosis removes the alert and flips the evaluation.
// Fixtures: CARLOS ships no guideline and has no UI to add one, so the run seeds one
// marker-titled Drools guideline (dxcodes icd9:2700 AND sex F), maps it to the test
// provider, and seeds one active dxresearch row on the owned synthetic patient. Cleanup
// deletes only the marker guideline, its provider mapping and the owned patient's rows.
const h = require('./lib/playwright-harness');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

// An ICD-9 code nobody in the demo data carries, so the owned guideline can only fire
// on the owned patient while other checks open charts for the same provider.
const DX_CODE = '2700';

async function conditionRows(page) {
  const rows = page.locator('table.dsTable tr').filter({ has: page.locator('td') });
  return rows.evaluateAll(trs => trs.map(tr => [...tr.cells].map(td => td.innerText.trim())));
}

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const uuid = h.sqlString(marker);
  const title = `${marker} guideline`;
  const warning = `${marker} review amino-acid transport plan`;
  const xml = `<guideline title="${title}"><conditions>`
    + `<condition type="dxcodes" any="icd9:${DX_CODE}"/><condition type="sex" any="F"/>`
    + `</conditions><consequence><warning strength="warning">${warning}</warning></consequence></guideline>`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM dsGuidelineProviderMap WHERE guideline_uuid=${uuid} AND provider_no=${h.sqlString(provider)};
      DELETE FROM dsGuidelines WHERE uuid=${uuid} AND title=${h.sqlString(title)};
      DELETE FROM dxresearch WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM dsGuidelineProviderMap WHERE guideline_uuid=${uuid})
      + (SELECT COUNT(*) FROM dsGuidelines WHERE uuid=${uuid})
      + (SELECT COUNT(*) FROM dxresearch WHERE demographic_no=${patient})`) === '0', 'Owned guideline fixtures were not removed');
  });
  const guideline = sql.value(`INSERT INTO dsGuidelines (uuid,title,version,author,xml,source,engine,dateStart,status)
    VALUES (${uuid},${h.sqlString(title)},1,'FAKE-PW check',${h.sqlString(xml)},'local','drools',NOW(),'A');
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(guideline), 'The owned guideline was not created');
  sql.execute(`INSERT INTO dsGuidelineProviderMap (provider_no,guideline_uuid) VALUES (${h.sqlString(provider)},${uuid})`);
  const dx = sql.value(`INSERT INTO dxresearch (demographic_no,start_date,update_date,status,dxresearch_code,coding_system,association,providerNo)
    VALUES (${patient},CURDATE(),NOW(),'A','${DX_CODE}','icd9',0,${h.sqlString(provider)}); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(dx), 'The owned diagnosis was not created');

  let chart = await s.chart();
  const module = () => chart.locator('#Guidelines');
  const alert = () => module().locator(`a.links[onclick*="method=detail&guidelineId=${guideline}&"]`);
  const heading = () => module().locator('.nav-menu-title a');
  const listRow = page => page.locator('table.dsTable tr').filter({ hasText: title });

  await s.step('chart nav renders the owned guideline warning for the matching patient', async () => {
    await heading().filter({ hasText: 'Decision Support Alerts' }).waitFor({ state: 'visible' });
    h.assert(await alert().count() === 1, 'The matching patient\'s chart does not show the guideline warning');
    h.assert((await alert().innerText()).trim() === title && await alert().getAttribute('title') === title,
      'The guideline warning does not carry the guideline title');
    h.assert(/demographic_no=(\d+)&/.exec(await alert().getAttribute('onclick'))[1] === patient,
      'The guideline warning links to another patient');
  });

  let list;
  await s.step('heading opens the guideline list evaluating the owned guideline as passed', async () => {
    list = await s.popup(chart, heading(), 'guideline-list');
    h.assert(new URL(list.url()).searchParams.get('demographic_no') === patient, 'Guideline list opened for another patient');
    await listRow(list).waitFor({ state: 'visible' });
    const cells = (await listRow(list).locator('td').allInnerTexts()).map(text => text.trim());
    h.assert(cells[0] === '1' && cells[1] === title && cells[2] === 'FAKE-PW check' && cells[4] === 'Active',
      'Guideline list does not show the owned guideline\'s version, author and active status');
    h.assert(/^Passed\b/.test(cells[5]), 'Guideline list does not evaluate the matching patient as passed');
  });

  await s.step('More Info evaluates each condition against the owned patient', async () => {
    await clickAndAwaitReload(list, listRow(list).getByRole('link', { name: 'More Info' }));
    const params = new URL(list.url()).searchParams;
    h.assert(params.get('method') === 'detail' && params.get('guidelineId') === guideline
      && params.get('demographic_no') === patient, 'More Info opened another guideline or patient');
    h.assert((await list.locator('body').innerText()).includes(`GUIDELINE PASSED: ${warning}`),
      'Guideline detail does not report the passed guideline consequence');
    h.assert((await list.locator('div').first().innerText()).includes(`Workflow ${marker}`),
      'Guideline detail is not headed with the owned patient');
    const rows = await conditionRows(list);
    h.assert(JSON.stringify(rows.map(row => [row[0], row[1], row[3], row[4]])) === JSON.stringify([
      ['dxcodes', 'any', `icd9:${DX_CODE}`, 'Passed'], ['sex', 'any', 'F', 'Passed'],
    ]), 'Guideline detail did not evaluate both conditions as passed with the patient\'s actual values');
  });

  await s.step('List Guidelines returns to the patient list and the warning opens the same detail', async () => {
    await clickAndAwaitReload(list, list.getByRole('button', { name: 'List Guidelines' }));
    h.assert(new URL(list.url()).searchParams.get('demographic_no') === patient, 'List Guidelines lost the patient');
    await listRow(list).waitFor({ state: 'visible' });
    await list.close();
    const detail = await s.popup(chart, alert(), 'guideline-warning-detail');
    h.assert(new URL(detail.url()).searchParams.get('guidelineId') === guideline, 'Warning opened another guideline');
    h.assert((await detail.locator('body').innerText()).includes(`GUIDELINE PASSED: ${warning}`),
      'Warning detail does not report the passed guideline');
    await detail.close();
  });

  await s.step('resolving the diagnosis in the Dx Registry persists the change', async () => {
    const registry = await s.popup(chart, chart.locator('a[onclick*="setupDxResearch"]').first(), 'diagnosis-registry');
    const row = registry.locator(`#startdate1st${dx}`).locator('xpath=ancestor::tr[1]');
    await clickAndAwaitReload(registry, row.getByRole('link', { name: 'Resolve', exact: true }));
    await expectValue(sql, `SELECT status FROM dxresearch WHERE dxresearch_no=${dx} AND demographic_no=${patient}`, 'C',
      'Resolving the diagnosis did not persist');
    await registry.close();
  });

  await s.step('reopened chart no longer shows the warning and the list evaluates Failed', async () => {
    await chart.close();
    chart = await s.chart();
    await heading().filter({ hasText: 'Decision Support Alerts' }).waitFor({ state: 'visible' });
    h.assert(await alert().count() === 0, 'The guideline warning is still shown after the diagnosis was resolved');
    list = await s.popup(chart, heading(), 'guideline-list-after');
    await listRow(list).waitFor({ state: 'visible' });
    const cells = (await listRow(list).locator('td').allInnerTexts()).map(text => text.trim());
    h.assert(/^Failed\b/.test(cells[5]), 'Guideline list still evaluates the resolved patient as passed');
  });

  await s.step('detail fails only the diagnosis condition and keeps the matching sex condition', async () => {
    await clickAndAwaitReload(list, listRow(list).getByRole('link', { name: 'More Info' }));
    h.assert(!(await list.locator('body').innerText()).includes('GUIDELINE PASSED'),
      'Guideline detail still reports the guideline as passed');
    const rows = await conditionRows(list);
    h.assert(rows.length === 2 && rows[0][0] === 'dxcodes' && rows[0][4] === 'Fail',
      'Guideline detail does not fail the resolved diagnosis condition');
    // Each condition is evaluated separately and must be judged on its own rule.
    h.assert(rows[1][0] === 'sex' && rows[1][3] === 'F' && rows[1][4] === 'Passed',
      'Guideline detail fails the sex condition the patient still matches');
  });
}
if (require.main === module) runWorkflow('decision-support-guidelines', workflow);
module.exports = { workflow };
