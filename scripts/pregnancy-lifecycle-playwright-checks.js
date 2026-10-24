#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
// #4557: native heading, four Add choices, creation, current episode, completion and history.
// SQL owns only synthetic patients/episodes and reversible provider-specific module grants.
// Security probes use the authenticated context's request API and exact status assertions;
// normal UI requests retain the harness's unmodified strict recorder.
const {assert, sqlString} = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');
const {csrfTokenPresent} = require('./lib/playwright-ui');

const TYPES = [['72892002', 'Normal'], ['47200007', 'High Risk'], ['16356006', 'Multiple'], ['34801009', 'Ectopic']];
const GATES = ['_pregnancy', '_newCasemgmt.pregnancy'];

function grantPregnancyModule({sql, provider, cleanup}) {
  const owner = sqlString(provider);
  for (const object of GATES) {
    const where = `roleUserGroup=${owner} AND objectName=${sqlString(object)}`;
    const snapshot = () => sql.rows(`SELECT privilege,priority,provider_no FROM secObjPrivilege WHERE ${where}`);
    const before = snapshot();
    assert(before.length <= 1, 'Pregnancy module fixture found duplicate privilege rows');
    if (before[0]?.[0] === 'x') continue;
    const held = before.length ? [['x', before[0][1], before[0][2]]] : [['x', '0', String(provider)]];
    cleanup(() => {
      const now = snapshot();
      if (JSON.stringify(now) === JSON.stringify(before)) return;
      assert(JSON.stringify(now) === JSON.stringify(held), 'Pregnancy privilege ownership changed; refusing to overwrite it');
      if (before.length) sql.execute(`UPDATE secObjPrivilege SET privilege=${sqlString(before[0][0])} WHERE ${where} AND privilege='x'`);
      else sql.execute(`DELETE FROM secObjPrivilege WHERE ${where} AND privilege='x' AND provider_no=${owner}`);
      assert(JSON.stringify(snapshot()) === JSON.stringify(before), 'Pregnancy module fixture did not restore its exact privilege row');
    });
    if (before.length) sql.execute(`UPDATE secObjPrivilege SET privilege='x' WHERE ${where} AND privilege=${sqlString(before[0][0])}`);
    else sql.execute(`INSERT INTO secObjPrivilege (roleUserGroup,objectName,privilege,priority,provider_no)
      VALUES (${owner},${sqlString(object)},'x',0,${owner})`);
    assert(JSON.stringify(snapshot()) === JSON.stringify(held), 'Pregnancy module fixture did not acquire its provider grant');
  }
}

function isPregnancyPost(response, target, patient, operation) {
  const request = response.request();
  if (request.method() !== 'POST' || new URL(response.url()).pathname !== new URL(target).pathname) return false;
  const body = new URLSearchParams(request.postData() || '');
  return body.get('demographicNo') === String(patient) && body.get('method') === operation;
}

function assertPregnancyRedirect(response, target, patient) {
  assert(response.status() === 302, `Pregnancy POST returned ${response.status()}, expected 302; a save is not established`);
  const location = response.headers().location;
  assert(location, 'Pregnancy POST has no Location header');
  const redirect = new URL(location, target);
  const route = new URL(target);
  assert(redirect.origin === route.origin && redirect.pathname === route.pathname
    && redirect.searchParams.get('demographicNo') === String(patient) && redirect.searchParams.get('saved') === 'true'
    && !redirect.searchParams.has('method'), 'Pregnancy redirect is not the own-patient read-only history');
}

async function withPatientPrivilege(s, object, privilege, operation) {
  assert(/^_(?:pregnancy|demographic)\$[1-9]\d*$/.test(object) && ['r', 'o'].includes(privilege),
    'Patient privilege fixture is outside its owned access probes');
  const where = `roleUserGroup=${sqlString(s.provider)} AND objectName=${sqlString(object)}`;
  const snapshot = () => s.sql.rows(`SELECT privilege,priority,provider_no FROM secObjPrivilege WHERE ${where}`);
  assert(snapshot().length === 0, 'The owned patient already has an individual privilege override');
  const held = [[privilege, '1', String(s.provider)]];
  const restore = () => {
    const now = snapshot();
    if (!now.length) return;
    assert(JSON.stringify(now) === JSON.stringify(held), 'Patient privilege fixture ownership changed');
    s.sql.execute(`DELETE FROM secObjPrivilege WHERE ${where} AND privilege=${sqlString(privilege)} AND priority=1 AND provider_no=${sqlString(s.provider)}`);
    assert(snapshot().length === 0, 'Patient privilege fixture was not removed');
  };
  s.cleanup(restore);
  try {
    s.sql.execute(`INSERT INTO secObjPrivilege (roleUserGroup,objectName,privilege,priority,provider_no)
      VALUES (${sqlString(s.provider)},${sqlString(object)},${sqlString(privilege)},1,${sqlString(s.provider)})`);
    assert(JSON.stringify(snapshot()) === JSON.stringify(held), 'Patient privilege fixture was not acquired');
    return await operation();
  } finally { restore(); }
}

async function workflow(s) {
  const {sql, patient, provider, marker} = s;
  const own = () => sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient} AND last_name=${sqlString(marker)}`) === '1';
  s.cleanup(() => {
    assert(own(), 'Pregnancy cleanup lost its owned patient');
    sql.execute(`DELETE FROM Episode WHERE demographicNo=${patient}`);
    assert(sql.value(`SELECT COUNT(*) FROM Episode WHERE demographicNo=${patient}`) === '0', 'Owned pregnancy rows survived cleanup');
  });
  const snapshot = () => sql.rows(`SELECT id,demographicNo,code,codingSystem,description,status,startDate,endDate,lastUpdateUser,lastUpdateTime,notes
    FROM Episode WHERE demographicNo=${patient} ORDER BY id`);
  const otherMarker = `${marker}-OTHER`;
  let other;
  let foreignEpisode;
  let patientsBefore;
  const patientRows = () => sql.rows(`SELECT * FROM demographic WHERE demographic_no IN (${patient},${other}) ORDER BY demographic_no`);
  const patientsUnchanged = () => assert(JSON.stringify(patientRows()) === JSON.stringify(patientsBefore),
    'Pregnancy operation changed a complete owned demographic row');
  const otherRows = () => sql.rows(`SELECT id,demographicNo,code,codingSystem,description,status,startDate,endDate,lastUpdateUser,lastUpdateTime,notes
    FROM Episode WHERE demographicNo=${other} ORDER BY id`);
  s.cleanup(() => {
    // Lookup by the unique marker also covers an INSERT whose response failed before its ID was captured.
    const owned = sql.rows(`SELECT demographic_no FROM demographic WHERE last_name=${sqlString(otherMarker)} AND provider_no=${sqlString(provider)}`);
    assert(owned.length <= 1, 'Other pregnancy patient ownership is ambiguous');
    if (!owned.length) return;
    const ownedId = owned[0][0];
    assert(!other || ownedId === other, 'Other pregnancy patient ownership changed');
    sql.execute(`DELETE FROM Episode WHERE demographicNo=${ownedId}`);
    sql.execute(`DELETE FROM demographic WHERE demographic_no=${ownedId} AND last_name=${sqlString(otherMarker)} AND provider_no=${sqlString(provider)}`);
    assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${ownedId}`) === '0', 'Other pregnancy patient survived cleanup');
  });
  let chart;
  let popup;
  let id;
  let target;
  let csrf;
  let createSubmission;
  const literalNotes = `${marker}\nLiteral <script>not executable</script> & ' " notes`;
  const wireNotes = literalNotes.replace(/\n/g, '\r\n');
  async function captureToken() {
    await csrfTokenPresent(popup, {timeout: 20000});
    csrf = await popup.locator('input[type="hidden"]').evaluateAll(fields => Object.fromEntries(fields
      .filter(field => /csrf/i.test(field.name)).map(field => [field.name, field.value])));
    assert(Object.keys(csrf).length === 1 && Object.values(csrf)[0], 'Pregnancy form has no usable CSRF token');
    target = await popup.locator('form').getAttribute('action');
    target = new URL(target, popup.url()).href;
  }
  async function save(operation, label) {
    await captureToken();
    const waiting = popup.waitForResponse(response => isPregnancyPost(response, target, patient, operation));
    const [response] = await Promise.all([waiting, popup.getByRole('button', {name: label, exact: true}).click()]);
    await response.finished();
    assertPregnancyRedirect(response, target, patient);
    const submitted = new URLSearchParams(response.request().postData() || '');
    if (operation === 'create') {
      assert(submitted.get('carlosFormSubmission'), 'Pregnancy create form has no submission guard');
      createSubmission = Object.fromEntries(submitted);
    }
    assert(submitted.get('notes') === wireNotes, 'Pregnancy native POST changed literal notes');
    await popup.getByRole('status').filter({hasText: 'Pregnancy saved.'}).waitFor();
    assert(new URL(popup.url()).searchParams.get('demographicNo') === patient, 'Pregnancy redirect lost patient identity');
  }
  await s.step('pregnancy heading lists the owned patient without a GET write', async () => {
    other = sql.value(`INSERT INTO demographic (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
      provider_no,hc_type,province,roster_status,lastUpdateDate)
      VALUES (${sqlString(otherMarker)},'Pregnancy access','1980','01','02','F','AC',${sqlString(provider)},'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
    assert(/^[1-9]\d*$/.test(other), 'Second owned pregnancy patient was not created');
    foreignEpisode = sql.value(`INSERT INTO Episode (demographicNo,code,codingSystem,description,status,startDate,lastUpdateUser,lastUpdateTime,notes)
      VALUES (${other},'47200007','SnomedCore','High Risk','Current','2026-01-02',${sqlString(provider)},NOW(),${sqlString(otherMarker)}); SELECT LAST_INSERT_ID()`);
    assert(/^[1-9]\d*$/.test(foreignEpisode), 'Second owned pregnancy episode was not created');
    grantPregnancyModule(s);
    chart = await s.chart();
    patientsBefore = patientRows();
    const before = snapshot();
    popup = await s.popup(chart, chart.locator('#pregnancy h3 a[onclick*="method=list"]').first(), 'pregnancy-list');
    await popup.getByRole('heading', {name: 'Pregnancy history', exact: true}).waitFor();
    await popup.reload();
    assert(JSON.stringify(snapshot()) === JSON.stringify(before), 'Pregnancy history GET wrote an episode');
    await popup.close();
    patientsUnchanged();
  });
  await s.step('all four native Add choices open read-only forms and reload safely', async () => {
    const before = snapshot();
    for (const [code, label] of TYPES) {
      await chart.locator('#pregnancy #menuTitle5 a').hover();
      popup = await s.popup(chart, chart.locator('#pregnancy #menu5').getByRole('link', {name: label, exact: true}), `pregnancy-new-${code}`);
      await popup.getByRole('heading', {name: 'Add pregnancy', exact: true}).waitFor();
      assert(await popup.locator('input[name="code"]').inputValue() === code, 'Pregnancy Add chose the wrong type');
      assert(await popup.locator('input[name="demographicNo"]').inputValue() === patient, 'Pregnancy Add chose the wrong patient');
      await captureToken();
      await popup.reload();
      assert(JSON.stringify(snapshot()) === JSON.stringify(before), 'Following or reloading Add wrote an episode');
      await popup.close();
    }
  });
  await s.step('native Create persists literal notes and refreshes the own chart', async () => {
    await chart.locator('#pregnancy #menuTitle5 a').hover();
    popup = await s.popup(chart, chart.locator('#pregnancy #menu5').getByRole('link', {name: 'Normal', exact: true}), 'pregnancy-create');
    await popup.getByLabel('Start date', {exact: true}).fill('2026-01-02');
    await popup.getByLabel('Notes', {exact: true}).fill(literalNotes);
    await save('create', 'Create pregnancy');
    const rows = snapshot();
    assert(rows.length === 1, 'Create did not persist exactly one pregnancy');
    id = rows[0][0];
    assert(rows[0][2] === '72892002' && rows[0][3] === 'SnomedCore' && rows[0][5] === 'Current'
      && rows[0][6].startsWith('2026-01-02') && rows[0][8] === String(provider) && rows[0][10] === wireNotes,
    'Create lost pregnancy type, start date, provider or literal notes');
    await chart.locator(`#pregnancy a[onclick*="episodeId=${id}"]`).first().waitFor();
    assert(await chart.locator(`#pregnancy a[onclick*="episodeId=${id}"]`).first().getAttribute('title') === literalNotes,
      'Pregnancy chart tooltip did not preserve literal notes safely');
    const before = snapshot();
    await popup.reload();
    assert(JSON.stringify(snapshot()) === JSON.stringify(before), 'Reload after create duplicated pregnancy');
    await popup.close();
    patientsUnchanged();
  });
  await s.step('repeated create submissions redirect without changing the current episode', async () => {
    const before = snapshot();
    const replies = await Promise.all([1, 2].map(() => s.context.request.post(target, {form: createSubmission, maxRedirects: 0})));
    for (const reply of replies) {
      assertPregnancyRedirect(reply, target, patient);
      await reply.body();
    }
    assert(JSON.stringify(snapshot()) === JSON.stringify(before), 'Repeated create changed the saved pregnancy');
    patientsUnchanged();
  });
  await s.step('current episode reopens safely and native Complete persists history', async () => {
    const before = snapshot();
    popup = await s.popup(chart, chart.locator(`#pregnancy a[onclick*="episodeId=${id}"]`).first(), 'pregnancy-current');
    assert(await popup.getByLabel('Notes', {exact: true}).inputValue() === literalNotes, 'Current pregnancy lost literal notes');
    await popup.reload();
    assert(JSON.stringify(snapshot()) === JSON.stringify(before), 'Opening current pregnancy changed its fields');
    await popup.getByLabel('Completion date', {exact: true}).fill('2026-02-03');
    await save('complete', 'Complete pregnancy');
    const rows = snapshot();
    assert(rows.length === 1 && rows[0][0] === id && rows[0][5] === 'Complete' && rows[0][7].startsWith('2026-02-03')
      && rows[0][10] === wireNotes, 'Completion lost episode identity, date or notes');
    await chart.locator('#pregnancy a').filter({hasText: 'Completed: Normal'}).first().waitFor();
    await popup.locator(`tr[data-episode-id="${id}"]`).getByRole('link', {name: 'Normal', exact: true}).click();
    assert(await popup.getByLabel('Notes', {exact: true}).inputValue() === literalNotes, 'Completed history lost literal notes');
    assert(await popup.getByLabel('Notes', {exact: true}).getAttribute('readonly') !== null, 'Completed history remains editable');
    assert(await popup.getByRole('button', {name: 'Complete pregnancy', exact: true}).count() === 0, 'Completed history offers another completion');
    patientsUnchanged();
  });
  await s.step('old create form cannot start another pregnancy after completion', async () => {
    const before = snapshot();
    const reply = await s.context.request.post(target, {form: createSubmission, maxRedirects: 0});
    assertPregnancyRedirect(reply, target, patient);
    await reply.body();
    assert(JSON.stringify(snapshot()) === JSON.stringify(before), 'Old create form started another pregnancy after completion');
    patientsUnchanged();
  });
  await s.step('GET mutations, malformed dates, legacy handlers and stale completion are rejected without writes', async () => {
    const before = snapshot();
    const probe = async (method, data, status, verb = 'post') => {
      const payload = {...csrf, demographicNo: patient, method, ...data};
      const response = verb === 'get'
        ? await s.context.request.get(target, {params: payload})
        : await s.context.request.post(target, {form: payload});
      assert(response.status() === status, `Pregnancy ${method} probe did not return ${status}`);
      await response.body();
      assert(JSON.stringify(snapshot()) === JSON.stringify(before), 'Rejected pregnancy request changed clinical rows');
    };
    await probe('create', {code: '47200007', startDate: '2026-01-02'}, 405, 'get');
    await probe('complete', {episodeId: id, endDate: '2026-02-03'}, 405, 'get');
    await probe('create', {code: '47200007', startDate: '2026-02-30'}, 400);
    await probe('complete', {episodeId: id, endDate: '2026-02-03', notes: 'Overwritten'}, 409);
    await probe('saveMeasurementAjax', {}, 400);
    await probe('list', {demographicNo: 'bad'}, 400, 'get');
    await probe('edit', {episodeId: id, demographic_no: other}, 400, 'get');
    patientsUnchanged();
  });
  await s.step('another owned patient episode cannot be read or completed through this chart', async () => {
    const before = snapshot();
    const otherBefore = otherRows();
    const read = await s.context.request.get(target, {params: {method: 'edit', demographicNo: patient, episodeId: foreignEpisode}});
    assert(read.status() === 404 && !(await read.text()).includes(otherMarker), 'Foreign pregnancy episode was exposed');
    const write = await s.context.request.post(target, {form: {...csrf, method: 'complete', demographicNo: patient,
      episodeId: foreignEpisode, endDate: '2026-02-03', notes: 'Forged completion'}});
    assert(write.status() === 404, 'Foreign pregnancy completion was not refused');
    await write.body();
    assert(JSON.stringify(snapshot()) === JSON.stringify(before) && JSON.stringify(otherRows()) === JSON.stringify(otherBefore),
      'Rejected cross-patient operation changed an owned patient episode');
    patientsUnchanged();
  });
  await s.step('read-only pregnancy access permits history but rejects both writes with valid CSRF', async () => {
    const before = snapshot();
    const otherBefore = otherRows();
    await withPatientPrivilege(s, `_pregnancy$${patient}`, 'r', async () => {
      const read = await s.context.request.get(target, {params: {method: 'list', demographicNo: patient}});
      assert(read.status() === 200 && (await read.text()).includes('Pregnancy history'), 'Read-only pregnancy history is unavailable');
      for (const method of ['create', 'complete']) {
        const response = await s.context.request.post(target, {form: {...csrf, method, demographicNo: patient,
          episodeId: id, code: '47200007', startDate: '2026-01-02', endDate: '2026-02-03', notes: 'Denied write'}});
        assert(response.status() === 403 && (await response.text()).includes('insufficient privileges'),
          'Read-only pregnancy write was not denied by the routed authorization check');
      }
    });
    assert(JSON.stringify(snapshot()) === JSON.stringify(before) && JSON.stringify(otherRows()) === JSON.stringify(otherBefore),
      'Read-only requests changed clinical rows');
    patientsUnchanged();
  });
  await s.step('individual chart restriction rejects pregnancy reads and writes with valid CSRF', async () => {
    const before = snapshot();
    const otherBefore = otherRows();
    await withPatientPrivilege(s, `_demographic$${patient}`, 'o', async () => {
      const read = await s.context.request.get(target, {params: {method: 'edit', demographicNo: patient, episodeId: id}});
      assert(read.status() === 403 && !(await read.text()).includes(literalNotes), 'Restricted pregnancy episode was exposed');
      const write = await s.context.request.post(target, {form: {...csrf, method: 'create', demographicNo: patient,
        code: '47200007', startDate: '2026-01-02', notes: 'Denied write'}});
      assert(write.status() === 403 && (await write.text()).includes('insufficient privileges'),
        'Individual chart restriction did not deny the routed pregnancy write');
    });
    assert(JSON.stringify(snapshot()) === JSON.stringify(before) && JSON.stringify(otherRows()) === JSON.stringify(otherBefore),
      'Restricted requests changed clinical rows');
    patientsUnchanged();
  });
}

if (require.main === module) runWorkflow('pregnancy-lifecycle', workflow);
module.exports = {workflow, grantPregnancyModule, isPregnancyPost, assertPregnancyRedirect, withPatientPrivilege};
