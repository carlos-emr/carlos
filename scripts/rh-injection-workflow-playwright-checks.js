#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// E-Chart > RH Form > Save > Add Injection > Save Injection.
// Asserts the prevention record and the RH form's live injection panel, then Delete.
// Owns the patient, forms, workflow and prevention; restores optional form registration.
const h = require('./lib/playwright-harness');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { prepareRhWorkflow } = require('./lib/rh-ui-workflow');

async function workflow(s) {
  const privacy = process.env.RH_INJECTION_ONLY === 'privacy';
  const existing = process.env.RH_INJECTION_ONLY === 'existing' || privacy;
  h.assert(!process.env.RH_INJECTION_ONLY || existing, 'RH_INJECTION_ONLY must be existing or privacy when set');
  if (privacy && !process.env.CARLOS_LOG_JOURNAL_UNIT) throw new h.SkipCheck('Set CARLOS_LOG_JOURNAL_UNIT to inspect the installed server log');
  let rh;
  let injection;
  let id;
  if (!existing) await s.step('Add Injection opens for the saved RH form and the same patient and workflow', async () => {
    rh = await prepareRhWorkflow(s);
    injection = await s.popup(rh.page, rh.page.getByRole('button', { name: 'Add Injection', exact: true }), 'rh-add-injection');
    h.assert(new URL(injection.url()).pathname === `${s.config.baseUrl.pathname}/form/addRhInjection`,
      `Add Injection navigated outside its form route: ${h.pathOnly(injection.url())}`);
    await injection.locator('#injectForm').waitFor();
    h.assert(await injection.locator('[name="demographic_no"]').inputValue() === s.patient
      && await injection.locator('[name="workflowId"]').inputValue() === rh.workflowId
      && await injection.locator('[name="formId"]').inputValue() === rh.formId,
    'Add Injection lost its patient, form or workflow');
  });
  if (!existing) await s.step('Save Injection stores one RH prevention and refreshes the form panel without a script error', async () => {
    await injection.locator('#prevDate').fill(s.sql.value('SELECT CURDATE()'));
    await injection.locator('[name="provider"]').selectOption(s.provider);
    for (const [name, value] of Object.entries({location: 'Test clinic', route: 'IM', lot: s.marker,
      product: 'WinRho', manufacture: 'Test manufacturer', dosage: '300'})) {
      await injection.locator(`[name="${name}"]`).fill(value);
    }
    const [response] = await Promise.all([
      injection.waitForResponse(r => r.request().method() === 'POST' && h.pathOnly(r.url()).endsWith('/prevention/AddPrevention')),
      injection.getByRole('button', { name: /Save Injection/i }).click(),
    ]);
    h.assert(response.status() === 200, `Save Injection answered HTTP ${response.status()}`);
    const rows = s.sql.rows(`SELECT id,refused,deleted FROM preventions WHERE demographic_no=${s.patient} AND prevention_type='RH'`);
    h.assert(rows.length === 1 && rows[0][1] === '0' && rows[0][2] === '0', 'Save Injection did not store one completed RH prevention');
    id = rows[0][0];
    h.assert(s.sql.value(`SELECT val FROM preventionsExt WHERE prevention_id=${id} AND keyval='lot'`) === s.marker,
      'The injection lot was not stored exactly');
    h.assert(s.sql.value(`SELECT val FROM preventionsExt WHERE prevention_id=${id} AND keyval='workflowId'`) === rh.workflowId
      && s.sql.value(`SELECT val FROM preventionsExt WHERE prevention_id=${id} AND keyval='formId'`) === rh.formId,
    'The injection was not linked to its pregnancy and saved form');
    await rh.page.locator('#injectionInfo').filter({ hasText: s.marker }).waitFor();
    h.assert((await rh.page.locator('#injectionInfo').innerText()).includes('300'), 'The refreshed panel lost the dose');
  });
  async function openExistingInjection() {
    rh = await prepareRhWorkflow(s);
    id = s.sql.value(`INSERT INTO preventions(demographic_no,prevention_type,prevention_date,creation_date,lastUpdateDate,provider_no,creator,refused,deleted)
      VALUES(${s.patient},'RH',CURDATE(),NOW(),NOW(),${h.sqlString(s.provider)},${h.sqlString(s.provider)},0,0); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), 'Existing injection fixture was not created');
    for (const [key, value] of Object.entries({workflowId: rh.workflowId, formId: rh.formId, lot: s.marker,
      product: 'WinRho', dosage: '300', location: 'Test clinic', reason: 'Antepartum'})) {
      s.sql.execute(`INSERT INTO preventionsExt(prevention_id,keyval,val) VALUES(${id},${h.sqlString(key)},${h.sqlString(value)})`);
    }
    await rh.page.close();
    const requested = s.context.waitForEvent('response', { predicate: r => h.pathOnly(r.url()).endsWith('/form/RhInjectionDisplay')
      && new URL(r.url()).searchParams.get('id') === rh.workflowId });
    rh.page = await rh.openNew();
    h.assert((await requested).status() === 200, 'The RH injection panel request did not reach its success response');
  }
  if (privacy) {
    await s.step('opening the RH form requests the existing pregnancy injection panel', openExistingInjection);
    let scan;
    await s.step('the RH injection privacy check reads the server journal', async () => {
      // Use the boot/run window and a unique fixture needle. This still proves
      // the journal was read when a fixed application emits no event on open.
      await new Promise(resolve => setTimeout(resolve, 2500));
      scan = spawnSync('bash', [path.join(__dirname, 'deb-server-log-phi-scan.sh'), '--since',
        process.env.CARLOS_LOG_AUDIT_SINCE || 'today', '--unit', process.env.CARLOS_LOG_JOURNAL_UNIT,
        '--only-given', '--marker', s.marker], { encoding: 'utf8', timeout: 60000 });
      h.assert(scan.status === 0 || scan.status === 1, 'The server log scan could not run');
      h.assert(/journal [1-9]\d* line\(s\)/.test(scan.stdout), 'The server log scan read no journal lines');
    });
    await s.step('the RH injection panel writes no clinical fixture data to the server log', async () => {
      h.assert(scan.status === 0, `The server log contains the injection fixture's clinical data:\n${scan.stdout.trim()}`);
    });
    return;
  }
  if (existing) await s.step('a saved RH form displays an existing injection with its lot, product, dose and reason', async () => {
    await openExistingInjection();
    await rh.page.locator('#injectionInfo').filter({hasText: s.marker}).waitFor();
    const panel = await rh.page.locator('#injectionInfo').innerText();
    h.assert(['WinRho', '300', 'Test clinic', 'Antepartum'].every(value => panel.includes(value)),
      'The RH injection panel lost stored details');
  });
  await s.step('Delete Injection archives only that injection and removes it from the form panel', async () => {
    const remove = rh.page.locator('#injectionInfo [onclick*="deleteInjection"]');
    h.assert(await remove.count() === 1, 'The owned pregnancy has an unexpected number of injection Delete links');
    await remove.click();
    await expectValue(s.sql, `SELECT deleted FROM preventions WHERE id=${id}`, '1', 'Delete Injection did not archive the record');
    await rh.page.locator('#injectionInfo').filter({ hasText: s.marker }).waitFor({state: 'hidden'});
    h.assert(s.sql.value(`SELECT COUNT(*) FROM workflow WHERE ID=${rh.workflowId}`) === '1', 'Deleting the injection removed the pregnancy');
  });
}
if (require.main === module) runWorkflow(process.env.RH_INJECTION_ONLY === 'privacy' ? 'rh-injection-log-privacy'
  : process.env.RH_INJECTION_ONLY === 'existing' ? 'rh-injection-existing' : 'rh-injection-workflow', workflow);
module.exports = { workflow };
