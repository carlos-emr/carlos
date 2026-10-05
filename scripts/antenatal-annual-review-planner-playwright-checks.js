#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Coverage plan §2.5 decision planners. User path: Schedule ▸ Search ▸ Master Record ▸
// E-Chart ▸ Forms menu ▸ Annual (female) ▸ "Annual Review Planner" ▸ Save / Print / Save and Exit /
// Edit Risk / Edit CheckList. Asserts the planner opens for the owned patient, that Save
// and Save and Exit each persist a desannualreviewplan row carrying exactly the ticked
// risk and checklist boxes, that reopening restores the latest plan, that Print renders
// the saved plan for that patient, that a GET carrying the save intent is refused without
// a write, and that the shared risk/checklist editors render the deployed lists (opened
// read-only: their Save rewrites a clinic-wide file, so the check never submits them).
// The antenatal planner, its OB risk/checklist editors, SaveAntenatalRiskConfig and
// provider/ViewObar* have no UI entry on an Ontario install, so they get no check here.
// Fixtures: the owned synthetic patient and a marker-named Forms-menu registration of the
// Annual form (a clinic-wide menu entry, so run EXCLUSIVE=1); cleanup deletes both.
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const RISK = 'input[type="checkbox"][name^="risk_"]';
const CHECKLIST = 'input[type="checkbox"][name^="checklist_"]';

function tickedTags(content) {
  return [...String(content).matchAll(/<([A-Za-z0-9_-]+)>checked<\/\1>/g)].map(match => match[1]).sort();
}

async function ticked(page) {
  return page.locator(`${RISK}:checked, ${CHECKLIST}:checked`).evaluateAll(inputs => inputs.map(input => input.name).sort());
}

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const plans = `SELECT des_no,provider_no,risk_content,checklist_content FROM desannualreviewplan
    WHERE demographic_no=${patient} AND form_no=0 ORDER BY des_no`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM desannualreviewplan WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM desannualreviewplan WHERE demographic_no=${patient}`) === '0',
      'Owned annual review plans were not removed');
  });
  // The shipped Annual registration is hidden from the chart's Forms menu, and enabling it
  // would edit a demo row, so the run registers its own marker-named entry for the same view.
  const formName = `${marker} Annual`;
  const formValue = `../form/formannual.jsp?fixture=${marker}&demographic_no=`;
  const formRow = `form_value=${h.sqlString(formValue)} AND form_name=${h.sqlString(formName)}`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM encounterForm WHERE ${formRow}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM encounterForm WHERE form_value=${h.sqlString(formValue)}`) === '0',
      'The owned Annual form registration was not removed');
  });
  sql.execute(`INSERT INTO encounterForm (form_value,form_name,form_table,hidden)
    SELECT ${h.sqlString(formValue)},${h.sqlString(formName)},'formAnnual',COALESCE(MAX(hidden),0)+1 FROM encounterForm`);
  const chart = await s.chart();
  await chart.locator('#menuTitle1 a').hover();
  const annual = await s.popup(chart, chart.getByRole('link', { name: formName, exact: true }), 'annual-form');
  const openPlanner = () => s.popup(annual, annual.getByRole('link', { name: 'Annual Review Planner' }).first(), 'annual-review-planner');
  let planner;

  await s.step('Annual form opens the review planner for the owned patient with nothing ticked', async () => {
    planner = await openPlanner();
    const params = new URL(planner.url()).searchParams;
    h.assert(params.get('demographic_no') === patient && params.get('formId') === '0',
      'The planner opened for another patient or form');
    h.assert(await planner.title() === 'ANNUAL HEALTH REVIEW PLANNER', 'The annual review planner did not render');
    h.assert(await planner.locator(RISK).count() > 0 && await planner.locator(CHECKLIST).count() > 0,
      'The planner rendered no risk or checklist boxes');
    h.assert((await ticked(planner)).length === 0, 'A new plan opened with boxes already ticked');
  });

  const first = ['checklist_0200701_na', 'checklist_0299901_done', 'risk_Fall'];
  await s.step('Save persists exactly the ticked risk and checklist boxes', async () => {
    for (const name of first) await planner.locator(`input[name="${name}"]`).check();
    await ui.clickAndAwaitReload(planner, planner.getByRole('button', { name: 'Save', exact: true }).first());
    await expectValue(sql, `SELECT COUNT(*) FROM desannualreviewplan WHERE demographic_no=${patient}`, '1',
      'Save did not persist exactly one annual review plan');
    const [[, owner, risks, checklist]] = sql.rows(plans);
    h.assert(owner === provider, 'The saved plan is not attributed to the signed-in provider');
    h.assert(JSON.stringify(tickedTags(risks + checklist)) === JSON.stringify(first),
      'The saved plan does not hold exactly the ticked boxes');
    h.assert(JSON.stringify(await ticked(planner)) === JSON.stringify(first), 'The saved planner did not redisplay the ticked boxes');
  });

  await s.step('Print renders the saved plan for the owned patient', async () => {
    const print = await s.popup(planner, planner.getByRole('button', { name: 'Print' }).first(), 'annual-review-print');
    const params = new URL(print.url()).searchParams;
    h.assert(params.get('demographic_no') === patient && params.get('formId') === '0', 'Print opened another patient or form');
    h.assert((await print.locator('table').first().innerText()).includes(`${marker}, Workflow F`),
      'Print is not headed with the owned patient');
    h.assert(JSON.stringify(await print.locator(`${CHECKLIST}:checked`).evaluateAll(inputs => inputs.map(input => input.name).sort()))
      === JSON.stringify(first.filter(name => name.startsWith('checklist_'))), 'Print does not show the saved checklist items');
    await print.close();
  });

  const second = ['checklist_0299901_done', 'risk_Fall', 'risk_OA'];
  await s.step('Save and Exit appends the revised plan and closes the planner', async () => {
    // Plans are ordered by their second-resolution save time; keep the revision distinct.
    await expectValue(sql, `SELECT TIMESTAMPDIFF(SECOND, CONCAT(des_date,' ',des_time), NOW()) > 0
      FROM desannualreviewplan WHERE demographic_no=${patient}`, '1', 'The database clock did not advance');
    await planner.locator('input[name="checklist_0200701_na"]').uncheck();
    await planner.locator('input[name="risk_OA"]').check();
    const closed = planner.waitForEvent('close', { timeout: 20000 });
    await planner.getByRole('button', { name: 'Save and Exit' }).first().click();
    await closed;
    await expectValue(sql, `SELECT COUNT(*) FROM desannualreviewplan WHERE demographic_no=${patient}`, '2',
      'Save and Exit did not append a revised plan');
    const latest = sql.rows(plans)[1];
    h.assert(latest[1] === provider && JSON.stringify(tickedTags(latest[2] + latest[3])) === JSON.stringify(second),
      'The revised plan does not hold exactly the ticked boxes');
  });

  await s.step('reopening the planner restores the latest plan', async () => {
    planner = await openPlanner();
    h.assert(JSON.stringify(await ticked(planner)) === JSON.stringify(second), 'The reopened planner did not restore the latest plan');
  });

  await s.step('a GET carrying the save intent is refused without a write', async () => {
    const endpoint = new URL('decision/annualreview/annualreviewplanner', s.config.baseUrl.href + '/').href;
    const response = await s.context.request.get(endpoint, { params: {
      demographic_no: patient, formId: '0', submit: ' Save ', risk_TIA: 'checked' } });
    h.assert(response.status() === 405, 'A GET with the save intent was not refused');
    h.assert(sql.value(`SELECT COUNT(*) FROM desannualreviewplan WHERE demographic_no=${patient}`) === '2',
      'A refused GET wrote an annual review plan');
  });

  await s.step('Edit Risk and Edit CheckList render the deployed shared lists', async () => {
    for (const [name, label, expected] of [['Edit Risk', 'annual-risk-editor', /<risk\s+name="Fall"/],
      ['Edit CheckList', 'annual-checklist-editor', /<risk\s+riskname="Fall"/]]) {
      const { page: editor, isPopup } = await ui.clickOpensPopupOrNavigates(planner,
        planner.getByRole('link', { name, exact: true }).first(), { context: s.context, recorder: s.recorder, label, timeout: 20000 });
      h.assert(isPopup, `${name} did not open its editor window`);
      h.assert(expected.test(await editor.locator('textarea[name="checklist"]').inputValue()),
        `${name} does not show the deployed list`);
      h.assertStrictPage(s.recorder);
      // Both editors share one named window; close it so the next link opens afresh.
      await editor.close();
    }
  });
}
if (require.main === module) runWorkflow('antenatal-annual-review-planner', workflow);
module.exports = { workflow };
