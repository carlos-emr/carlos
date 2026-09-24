#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const h = require('./lib/playwright-harness');
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
  const rollbackTriggers = [];
  s.cleanup(() => rollbackTriggers.forEach(name => s.sql.execute(`DROP TRIGGER IF EXISTS ${name}`)));
  const formCount = `SELECT COUNT(*) FROM formRhImmuneGlobulin WHERE demographic_no=${s.patient}`;
  s.cleanup(() => {
    s.sql.execute(`DELETE FROM formRhImmuneGlobulin WHERE demographic_no=${s.patient} AND provider_no=${h.sqlString(s.provider)};
      DELETE FROM workflow WHERE demographic_no=${h.sqlString(s.patient)} AND provider_no=${h.sqlString(s.provider)} AND workflow_type='RH'`);
    h.assert(s.sql.value(formCount) === '0', 'Unexpected RH form rows remain on the owned patient');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM workflow WHERE demographic_no=${h.sqlString(s.patient)}`) === '0', 'Unexpected workflow rows remain on the owned patient');
  });
  const chart = await s.chart();
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
  await s.step('reopen through the chart saved forms list and update the existing pregnancy', async () => {
    await page.close();
    // The Forms module heading is the chart's own control for the saved forms list;
    // the per-form shortcut anchors are not rendered for a freshly saved record.
    const list = await s.popup(chart, chart.locator('h3[onclick*="/encounter/ViewFormlist"]').first(), 'rh-form-list');
    const savedLinks = list.locator('a').filter({ hasText: formName });
    h.assert(await savedLinks.count() === 1, 'Patient saved forms list did not show exactly one owned RH form');
    page = await s.popup(list, savedLinks.first(), 'rh-reopen');
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
  await s.step('roll back workflow and form together when the database rejects a form value', async () => {
    const suffix = s.marker.replace(/[^A-Za-z0-9_]/g, '_');
    for (const operation of ['INSERT', 'UPDATE']) {
      const name = `rh_rollback_${operation.toLowerCase()}_${suffix}`;
      rollbackTriggers.push(name);
      // Scoped to the owned patient so a concurrent save, or a trigger stranded by an
      // interrupted run, never blocks RH writes for anyone else.
      s.sql.execute(`DELIMITER //
        CREATE TRIGGER ${name} BEFORE ${operation} ON formRhImmuneGlobulin
        FOR EACH ROW IF NEW.demographic_no=${s.patient} THEN
          SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='RH rollback probe'; END IF//`);
    }
    const count = s.sql.value(formCount);
    const token = await page.locator('input[name="CSRF-TOKEN"]').first().inputValue();
    try {
      const response = await s.context.request.post(new URL('form/RHPrevention', s.config.baseUrl.href + '/').href, {
        form: { 'CSRF-TOKEN': token, demographic_no: s.patient, workflowId, state: '3', edd: '2027-02-01',
          form_class: 'RhImmuneGlobulin', motherSurname: 'Synthetic', comments: s.marker },
      });
      h.assert(response.status() === 500, 'Rejected form data did not report a visible server failure');
      h.assert(s.sql.value(formCount) === count, 'Failed save inserted an RH form');
      h.assert(s.sql.value(`SELECT CONCAT(current_state,':',DATE(completion_date)) FROM workflow WHERE ID=${workflowId}`) === '2:2027-01-01',
        'Workflow mutation was committed despite the failed form save');
    } finally {
      for (const name of rollbackTriggers) s.sql.execute(`DROP TRIGGER IF EXISTS ${name}`);
    }
  });
}
if (require.main === module) runWorkflow('rh-form-workflow', workflow);
module.exports = { workflow };
