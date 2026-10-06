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
  const title = `${marker} guideline "review"`;
  const author = 'FAKE-PW <b>check</b>';
  const warning = `${marker} review amino-acid transport plan`;
  const xml = `<guideline title="${title.replaceAll('"', '&quot;')}"><conditions>`
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
    VALUES (${uuid},${h.sqlString(title)},1,${h.sqlString(author)},${h.sqlString(xml)},'local','drools',NOW(),'A');
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

  await s.step('chart nav renders the owned guideline warning for the matching patient', async () => {
    await heading().filter({ hasText: 'Decision Support Alerts' }).waitFor({ state: 'visible' });
    h.assert(await alert().count() === 1, 'The matching patient\'s chart does not show the guideline warning');
    h.assert((await alert().innerText()).trim() === title && await alert().getAttribute('title') === title,
      'The guideline warning does not carry the guideline title');
    h.assert(/demographic_no=(\d+)&/.exec(await alert().getAttribute('onclick'))[1] === patient,
      'The guideline warning links to another patient');
  });

  let detail;
  await s.step('warning opens the guideline detail evaluating each condition for the owned patient', async () => {
    detail = await s.popup(chart, alert(), 'guideline-detail');
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

  await s.step('the heading list evaluates the matching patient and renders stored labels as text', async () => {
    const list = await s.popup(chart, heading(), 'guideline-list-matching');
    const row = list.locator('table.dsTable tr').filter({ hasText: title });
    await row.waitFor({ state: 'visible' });
    const cells = (await row.locator('td').allInnerTexts()).map(text => text.trim());
    h.assert(cells[1] === title && cells[2] === author && cells[4] === 'Active' && /^Passed\b/.test(cells[5]),
      'Guideline list does not show the matching patient as passed with literal stored labels');
    h.assert(await row.locator('b').count() === 0, 'The stored author was rendered as HTML');
    const url = new URL(await row.locator('a').getAttribute('href'), list.url());
    h.assert(url.searchParams.get('guidelineId') === guideline && url.searchParams.get('demographic_no') === patient,
      'The list detail link changed the guideline or patient');
    await list.close();
  });

  await s.step('resolving the diagnosis in the Dx Registry persists the change', async () => {
    const registry = await s.popup(chart, chart.locator('a[onclick*="setupDxResearch"]').first(), 'diagnosis-registry');
    const row = registry.locator(`#startdate1st${dx}`).locator('xpath=ancestor::tr[1]');
    await clickAndAwaitReload(registry, row.getByRole('link', { name: 'Resolve', exact: true }));
    await expectValue(sql, `SELECT status FROM dxresearch WHERE dxresearch_no=${dx} AND demographic_no=${patient}`, 'C',
      'Resolving the diagnosis did not persist');
    await registry.close();
  });

  await s.step('reopened chart no longer shows the warning for the resolved patient', async () => {
    await chart.close();
    chart = await s.chart();
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
      const list = await s.popup(chart, heading(), 'guideline-list');
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
