#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { waitForNavbars } = require('./echart-navbar-modules-playwright-checks');

async function workflow(s) {
  let definition = s.sql.rows("SELECT form_name,hidden FROM encounterForm WHERE form_table='formRhImmuneGlobulin'");
  if (!definition.length) {
    const value = '../form/formRhImmuneGlobulin.jsp?demographic_no=';
    const name = s.marker + ' RH';
    h.assert(s.sql.value(`SELECT COUNT(*) FROM encounterForm WHERE form_value=${h.sqlString(value)}`) === '0', 'RH registration path already belongs to another form');
    const order = s.sql.value('SELECT COALESCE(MAX(hidden),0)+1 FROM encounterForm');
    h.assert(/^[1-9]\d*$/.test(order), 'Invalid form display order');
    s.sql.execute(`INSERT INTO encounterForm (form_value,form_name,form_table,hidden)
      VALUES (${h.sqlString(value)},${h.sqlString(name)},'formRhImmuneGlobulin',${order})`);
    s.cleanup(() => {
      h.assert(s.sql.value(`SELECT COUNT(*) FROM encounterForm WHERE form_value=${h.sqlString(value)} AND form_name=${h.sqlString(name)} AND hidden=${order}`) === '1', 'Owned RH form registration changed; retain it for recovery');
      s.sql.execute(`DELETE FROM encounterForm WHERE form_value=${h.sqlString(value)} AND form_name=${h.sqlString(name)} AND hidden=${order}`);
    });
    definition = [[name, order]];
  }
  h.assert(definition.length === 1, 'RH form registration is ambiguous');
  if (Number(definition[0][1]) <= 0) throw new h.SkipCheck('Enable the RH Form in Administration > Select Forms');
  const formName = definition[0][0];
  const formCount = `SELECT COUNT(*) FROM formRhImmuneGlobulin WHERE demographic_no=${s.patient}`;
  s.cleanup(() => {
    s.sql.execute(`DELETE FROM formRhImmuneGlobulin WHERE demographic_no=${s.patient} AND provider_no=${h.sqlString(s.provider)};
      DELETE FROM workflow WHERE demographic_no=${h.sqlString(s.patient)} AND provider_no=${h.sqlString(s.provider)} AND workflow_type='RH'`);
    h.assert(s.sql.value(formCount) === '0', 'Unexpected RH form rows remain on the owned patient');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM workflow WHERE demographic_no=${h.sqlString(s.patient)}`) === '0', 'Unexpected workflow rows remain on the owned patient');
  });
  let chart = await s.chart();
  await chart.locator('#menuTitle1 a').hover();
  let page = await s.popup(chart, chart.getByRole('link', { name: formName, exact: true }), 'rh-new-form');
  let workflowId;
  const latest = () => s.sql.rows(`SELECT ID,workflowId,comments FROM formRhImmuneGlobulin WHERE demographic_no=${s.patient} ORDER BY ID DESC LIMIT 1`)[0];
  async function save() {
    const response = page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/form/RHPrevention'));
    await Promise.all([page.waitForNavigation({ waitUntil: 'domcontentloaded' }), page.getByRole('button', { name: 'Save', exact: true }).click()]);
    h.assert((await response).status() === 200, 'RH form save did not return a rendered success page');
  }
  await s.step('save a new RH form with its new workflow and reload exact stored values', async () => {
    await page.locator('[name="edd"]').fill('2027-01-01');
    await page.locator('[name="comments"]').fill(s.marker);
    await save();
    const row = latest();
    h.assert(row && /^[1-9]\d*$/.test(row[1]), 'Saved RH form has no valid workflow');
    workflowId = row[1];
    h.assert(row[2] === s.marker && await page.locator('[name="comments"]').inputValue() === s.marker, 'Saved comments were lost in persistence or rendering');
    h.assert(await page.locator('[name="formId"]').inputValue() === row[0], 'Success page reopened a different form record');
  });
  await s.step('reopen through the chart and update the existing pregnancy', async () => {
    await page.close();
    // A fresh chart page rather than chart.reload(): leaving the encounter fires its
    // unload beacon to CaseManagementEntry, which the browser reports as an aborted
    // "ping" request and the strict page recorder counts as a failure.
    const refreshed = await s.context.newPage();
    await refreshed.goto(chart.url(), { waitUntil: 'domcontentloaded' });
    await waitForNavbars(refreshed, 20000);
    chart = refreshed;
    // One navbar ENTRY, not one anchor: LeftNavBarDisplay.jsp renders each saved form as a
    // title link plus a "...date" suffix link with the same target, and the suffix overlays
    // the end of a long title, so count the entries and click the title's visible left edge.
    const entries = chart.locator('#leftNavBar li, #rightNavBar li')
      .filter({ has: chart.locator('a[onclick*="/form/forwardshortcutname"]') });
    // The navbars fill their sections asynchronously after waitForNavbars returns; wait for the
    // forms section to render its entry before counting, or a slow load reads as zero.
    await entries.first().waitFor({ state: 'attached', timeout: 20000 }).catch(() => {});
    h.assert(await entries.count() === 1, 'Owned patient should have exactly one saved form entry');
    page = await ui.clickOpensPopup(chart, entries.first().locator('a[onclick*="/form/forwardshortcutname"]').first(),
      { context: s.context, recorder: s.recorder, label: 'rh-reopen', timeout: 20000, position: { x: 8, y: 9 } });
    h.assert(await page.locator('[name="comments"]').inputValue() === s.marker, 'Reopened RH form lost comments');
    await page.locator('[name="state"]').selectOption('2');
    await page.locator('[name="comments"]').fill(s.marker + ' edited'); await save();
    h.assert(latest()[1] === workflowId && latest()[2] === s.marker + ' edited', 'Edit did not preserve workflow ownership and comments');
    h.assert(s.sql.value(`SELECT current_state FROM workflow WHERE ID=${workflowId} AND demographic_no=${h.sqlString(s.patient)}`) === '2', 'Workflow state did not persist');
  });
  await s.step('reject GET and an unrelated workflow ID without writes', async () => {
    const count = s.sql.value(formCount);
    const token = await page.locator('input[name="CSRF-TOKEN"]').first().inputValue();
    const endpoint = new URL('form/RHPrevention', s.config.baseUrl.href + '/').href;
    const get = await s.context.request.get(endpoint, { params: { demographic_no: s.patient, workflowId, state: '3' } });
    h.assert(get.status() === 405, 'GET unexpectedly mutates the RH form');
    const missingId = s.sql.value('SELECT COALESCE(MAX(ID),0)+10000 FROM workflow');
    const post = await s.context.request.post(endpoint, { form: { 'CSRF-TOKEN': token, demographic_no: s.patient,
      workflowId: missingId, state: '3', edd: '2027-01-01', form_class: 'RhImmuneGlobulin' } });
    h.assert(post.status() === 400, 'An unrelated RH workflow ID was accepted');
    h.assert(s.sql.value(formCount) === count, 'Rejected request added an RH form');
    h.assert(s.sql.value(`SELECT current_state FROM workflow WHERE ID=${workflowId}`) === '2', 'Rejected request changed the workflow');
  });
  // The probe needs MariaDB to REJECT an overlong value. The packaged install deliberately
  // runs sql_mode='' (the carlos-emr drop-in clears the distribution's STRICT_TRANS_TABLES,
  // which the legacy schema cannot run under), where the value is silently truncated and
  // nothing rolls back, so the probe cannot measure anything there. Report it as skipped, not
  // as a pass, and keep the three steps above as the check's verdict.
  if (!/STRICT_(?:TRANS|ALL)_TABLES/.test(s.sql.value('SELECT @@GLOBAL.sql_mode'))) {
    console.log('  SKIP rh-form-workflow: roll back workflow and form together (needs a STRICT sql_mode; this deployment runs without one)');
    return;
  }
  await s.step('roll back workflow and form together when the database rejects a form value', async () => {
    const count = s.sql.value(formCount);
    const token = await page.locator('input[name="CSRF-TOKEN"]').first().inputValue();
    const response = await s.context.request.post(new URL('form/RHPrevention', s.config.baseUrl.href + '/').href, {
      form: { 'CSRF-TOKEN': token, demographic_no: s.patient, workflowId, state: '3', edd: '2027-02-01',
        form_class: 'RhImmuneGlobulin', motherSurname: 'Synthetic'.repeat(20), comments: s.marker },
    });
    h.assert(response.status() === 500, 'Rejected form data did not report a visible server failure');
    h.assert(s.sql.value(formCount) === count, 'Failed save inserted an RH form');
    h.assert(s.sql.value(`SELECT CONCAT(current_state,':',DATE(completion_date)) FROM workflow WHERE ID=${workflowId}`) === '2:2027-01-01',
      'Workflow mutation was committed despite the failed form save');
  });
}
if (require.main === module) runWorkflow('rh-form-workflow', workflow);
module.exports = { workflow };
