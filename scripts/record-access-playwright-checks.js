#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Authenticated ownership checks use only synthetic rows owned by this run.
const {assert, sqlString} = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');

async function workflow(s) {
  const {sql, marker, patient, provider, context} = s;
  let otherPatient;
  const scratchIds = [];
  s.cleanup(() => {
    for (const id of scratchIds) sql.execute(`DELETE FROM scratch_pad WHERE id=${id} AND scratch_text=${sqlString(marker)}`);
    sql.execute(`DELETE FROM Episode WHERE demographicNo=${patient} AND description=${sqlString(marker)}`);
    if (otherPatient) {
      assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${otherPatient} AND last_name=${sqlString(marker + '-OTHER')}`) === '1', 'Other patient fixture ownership changed');
      sql.execute(`DELETE FROM Episode WHERE demographicNo=${otherPatient} AND description=${sqlString(marker)}`);
      sql.execute(`DELETE FROM demographic WHERE demographic_no=${otherPatient} AND last_name=${sqlString(marker + '-OTHER')}`);
    }
  });
  otherPatient = sql.value(`INSERT INTO demographic (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES (${sqlString(marker + '-OTHER')},'Access','1980','01','02','F','AC',${sqlString(provider)},'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
  assert(/^[1-9]\d*$/.test(otherPatient), 'Other patient fixture was not created');
  const episode = sql.value(`INSERT INTO Episode (demographicNo,description,startDate,status,lastUpdateUser,lastUpdateTime)
    VALUES (${otherPatient},${sqlString(marker)},'2026-01-02','Current',${sqlString(provider)},NOW()); SELECT LAST_INSERT_ID()`);
  assert(/^[1-9]\d*$/.test(episode), 'Episode fixture was not created');
  const chart = await s.chart();
  const editor = await s.popup(chart, chart.locator('#menuTitleepisode a').first(), 'episode-access-editor');
  await editor.waitForFunction(() => document.querySelector('input[name="CSRF-TOKEN"]')?.value);
  const token = await editor.locator('input[name="CSRF-TOKEN"]').first().inputValue();
  const endpoint = new URL(editor.url());
  endpoint.search = '';
  await s.step('episode from another patient cannot be read through this chart', async () => {
    const url = new URL(endpoint);
    url.search = new URLSearchParams({method: 'edit', demographicNo: patient, 'episode.id': episode}).toString();
    const response = await context.request.get(url.href);
    assert(response.status() === 404, 'Cross-patient episode read was not rejected');
    assert(!(await response.text()).includes(marker), 'Cross-patient episode content was exposed');
  });
  await s.step('episode cannot be reassigned by posting another patient identifier', async () => {
    const response = await context.request.post(endpoint.href, {headers: {'CSRF-TOKEN': token}, form: {
      method: 'save', 'episode.id': episode, 'episode.demographicNo': patient,
      'episode.description': marker + '-CHANGED', 'episode.startDateStr': '2026-01-02', 'episode.status': 'Current',
    }});
    assert(response.status() === 404, 'Cross-patient episode update was not rejected');
    assert(sql.value(`SELECT CONCAT(demographicNo,'|',description) FROM Episode WHERE id=${episode}`) === `${otherPatient}|${marker}`,
      'Rejected episode update changed its owner or description');
  });
  await editor.close();
  const otherProvider = sql.value(`SELECT provider_no FROM provider WHERE provider_no<>${sqlString(provider)} ORDER BY provider_no LIMIT 1`);
  assert(otherProvider, 'A second provider is required for the scratchpad ownership check');
  for (const owner of [provider, otherProvider]) {
    const id = sql.value(`INSERT INTO scratch_pad (provider_no,date_time,scratch_text,status)
      VALUES (${sqlString(owner)},'2000-01-01',${sqlString(marker)},1); SELECT LAST_INSERT_ID()`);
    assert(/^[1-9]\d*$/.test(id), 'Scratchpad fixture was not created');
    scratchIds.push(id);
  }
  const scratch = await s.popup(s.schedule, s.schedule.getByTitle(/Scratch\s*Pad/i).first(), 'scratch-access');
  await scratch.waitForFunction(() => document.querySelector('input[name="CSRF-TOKEN"]')?.value);
  const scratchToken = await scratch.locator('input[name="CSRF-TOKEN"]').first().inputValue();
  const scratchEndpoint = await scratch.locator('form#scratch').evaluate(form => form.action);
  await s.step('another provider scratch version cannot be read or deleted', async () => {
    const url = new URL(scratchEndpoint);
    url.search = new URLSearchParams({method: 'showVersion', id: scratchIds[1]}).toString();
    const response = await context.request.get(url.href);
    assert(response.status() === 404 && !(await response.text()).includes(marker), 'Foreign scratch version was exposed');
    const deleted = await context.request.post(scratchEndpoint, {headers: {'CSRF-TOKEN': scratchToken},
      form: {method: 'delete', id: scratchIds[1], providerNo: otherProvider}});
    assert(deleted.status() === 404 && (await deleted.json()).success === false, 'Foreign scratch deletion was not rejected');
    assert(sql.value(`SELECT status FROM scratch_pad WHERE id=${scratchIds[1]}`) === '1', 'Foreign scratch version changed');
  });
  await s.step('the owner can read and delete the same kind of scratch version', async () => {
    const url = new URL(scratchEndpoint);
    url.search = new URLSearchParams({method: 'showVersion', id: scratchIds[0]}).toString();
    const response = await context.request.get(url.href);
    assert(response.status() === 200 && (await response.text()).includes(marker), 'Owned scratch version did not open');
    const deleted = await context.request.post(scratchEndpoint, {headers: {'CSRF-TOKEN': scratchToken},
      form: {method: 'delete', id: scratchIds[0]}});
    assert(deleted.status() === 200 && (await deleted.json()).success === true, 'Owned scratch deletion failed');
    assert(sql.value(`SELECT status FROM scratch_pad WHERE id=${scratchIds[0]}`) === '0', 'Owned scratch deletion did not persist');
  });
}
if (require.main === module) runWorkflow('record-access', workflow);
module.exports = {workflow};
