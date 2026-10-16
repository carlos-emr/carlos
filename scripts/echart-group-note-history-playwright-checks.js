/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
// Native group-note Rev plus adversarial requests. Every patient, login, facility and link is owned.
const {randomBytes} = require('node:crypto');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const {runWorkflow} = require('./lib/workflow-session');
const {authzReadFixture, cleanupAll} = require('./lib/authz-read-fixture');
const {signIn} = require('./lib/authz-read-probe');
const {openMasterRecord} = require('./master-record-tabs-playwright-checks');
const {openChart, waitForNavbars} = require('./echart-navbar-modules-playwright-checks');
const {readPatientState} = require('./echart-two-patient-isolation-playwright-checks');

function assertSharedHistoryText(text, {shared, privateText, future, cumulative}) {
  h.assert(text.includes(shared), 'Linked group history omitted the explicitly shared revision');
  h.assert(![privateText, future, cumulative].some(value => text.includes(value)),
    'Linked group history disclosed an unshared revision or cumulative prose');
}

function assertDeniedHistory(status, text, clinicalTexts) {
  h.assert(status === 403, 'An unauthorized history request was not deliberately refused');
  h.assert(clinicalTexts.every(value => !text.includes(value)), 'A refused history request disclosed clinical text');
}

async function workflow(s) {
  const {sql, patient, provider, marker, config} = s;
  const q = h.sqlString;
  const fixture = authzReadFixture({sql, marker, provider, testUser: config.testUser});
  const texts = {shared: `${marker} SHARED`, privateText: `${marker} PRIVATE`, future: `${marker} FUTURE`, cumulative: `${marker} CUMULATIVE`};
  let owner;
  let sharedId;
  let privateId;
  let futureId;
  let groupLink;
  let childrenRemoved = false;
  // The recipient search must not also load the unrelated owner through a shared surname prefix.
  const ownerName = `FAKE-GROUP${randomBytes(8).toString('hex')}`;
  h.assert(!ownerName.includes(marker), 'The owner fixture must not match the recipient search term');
  s.cleanup(() => cleanupAll(
    () => {
      if (!owner) { childrenRemoved = true; return; }
      h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${owner} AND last_name=${q(ownerName)}`) === '1',
        'Group-note owner fixture changed before cleanup');
      const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no IN (${patient},${owner})`;
      sql.execute(`DELETE FROM GroupNoteLink WHERE demographicNo IN (${patient},${owner});
        DELETE FROM hash_audit WHERE type='enc' AND id IN (${notes});
        DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes});
        DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes});
        DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
        DELETE FROM casemgmt_note WHERE demographic_no IN (${patient},${owner});
        DELETE FROM casemgmt_issue WHERE demographic_no IN (${patient},${owner});
        DELETE FROM casemgmt_note_lock WHERE demographic_no IN (${patient},${owner});
        DELETE FROM casemgmt_tmpsave WHERE demographic_no IN (${patient},${owner});
        DELETE FROM eChart WHERE demographicNo IN (${patient},${owner})`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no IN (${patient},${owner}))
        +(SELECT COUNT(*) FROM GroupNoteLink WHERE demographicNo IN (${patient},${owner}))
        +(SELECT COUNT(*) FROM casemgmt_issue WHERE demographic_no IN (${patient},${owner}))`) === '0', 'Group fixtures were not removed');
      childrenRemoved = true;
    },
    () => fixture.cleanup(),
    () => {
      if (!owner) return;
      h.assert(childrenRemoved, 'Retaining the owner because child cleanup failed');
      sql.execute(`DELETE FROM demographic WHERE demographic_no=${owner} AND last_name=${q(ownerName)}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${owner}`) === '0', 'The owned group-note patient remains');
    },
  ));
  const sessions = [];
  s.cleanup(async () => { for (const session of sessions) await session.context.close(); });
  async function loginAs(login) {
    const session = await signIn(s, login);
    sessions.push(session);
    return session;
  }

  let reader;
  let denied;
  let roleDenied;
  let crossRoleReader;
  let chart;
  let readerSession;
  const recorder = h.createRecorder();
  await s.step('owned group facility and reciprocal chart permissions are isolated from other staff', async () => {
    owner = sql.value(`INSERT INTO demographic
      (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate)
      VALUES (${q(ownerName)},'GroupOwner','1980','01','02','F','AC',${q(provider)},'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(owner), 'The owned group-note patient was not created');
    const facility = fixture.addFacility('GRP');
    sql.execute(`UPDATE Facility SET enableGroupNotes=1 WHERE id=${facility.id} AND name=${q(facility.name)} AND description=${q(marker)}`);
    reader = fixture.addLogin('doctor');
    denied = fixture.addLogin('doctor');
    const chartReadRights = {_demographic: 'r', _eChart: 'r', _appointment: 'r'};
    const noteReadRole = fixture.addRole({...chartReadRights, '_casemgmt.notes': 'r'});
    const noNoteReadRole = fixture.addRole({...chartReadRights, '_casemgmt.notes': 'o'});
    crossRoleReader = fixture.addLogin(noteReadRole);
    roleDenied = fixture.addLogin(noNoteReadRole);
    sql.execute(`INSERT INTO secUserRole (provider_no,role_name,orgcd,activeyn,lastUpdateDate)
      VALUES (${q(roleDenied.providerNo)},'doctor','R0000001',0,NOW())`);
    h.assert(sql.value(`SELECT COUNT(*) FROM secUserRole WHERE provider_no=${q(roleDenied.providerNo)}
      AND ((role_name='doctor' AND activeyn=0) OR (role_name=${q(noNoteReadRole)} AND activeyn=1))`) === '2',
      'The denied login must have no active note-read grant despite its inactive doctor assignment');
    for (const login of [reader, denied, crossRoleReader, roleDenied]) {
      fixture.joinFacility(login, facility);
      fixture.joinTestLoginPrograms(login);
    }
    // The sharing grant is scoped to the recipient, even though the owner chart is unavailable.
    fixture.lockPatient(reader, owner, ['_eChart']);
    fixture.lockPatient(denied, patient, ['_eChart']);
    const role = sql.value("SELECT role_no FROM secRole WHERE role_name='doctor'");
    h.assert(/^[1-9]\d*$/.test(role), 'The doctor reporter role is missing');
    function seed(text, uuid, history) {
      const id = sql.value(`INSERT INTO casemgmt_note
        (demographic_no,provider_no,signing_provider_no,note,history,uuid,signed,locked,archived,include_issue_innote,position,observation_date,update_date,program_no,reporter_caisi_role,appointmentNo)
        VALUES (${owner},${q(reader.providerNo)},${q(reader.providerNo)},${q(text)},${q(history)},${q(uuid)},1,0,0,1,0,NOW(),NOW(),'0',${q(role)},0);
        SELECT LAST_INSERT_ID()`);
      h.assert(/^[1-9]\d*$/.test(id), 'The owned note revision was not created');
      return id;
    }
    privateId = seed(texts.privateText, `${marker}-chain`, texts.privateText);
    sharedId = seed(texts.shared, `${marker}-chain`, texts.cumulative);
    futureId = seed(texts.future, `${marker}-chain`, texts.cumulative);
    groupLink = sql.value(`INSERT INTO GroupNoteLink (noteId,demographicNo,anonymous,active,created)
      VALUES (${sharedId},${patient},0,1,NOW()); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(groupLink), 'The exact-revision group link was not created');
    const ownerBeforeChart = JSON.stringify(readPatientState(sql, owner));
    readerSession = await loginAs(reader);
    readerSession.context.on('page', page => h.wireStrictPage(page, 'group-history', recorder));
    const {masterPage, searchPage} = await openMasterRecord(readerSession.context, readerSession.page, recorder,
      {searchTerm: marker, preferredDemographicNo: patient, timeout: 30000});
    h.assert(await searchPage.locator('a[title="Master Demographic File"]').count() === 1,
      'The recipient search must return exactly one patient');
    h.assert(new URL(masterPage.url()).searchParams.get('demographic_no') === String(patient),
      'The single search result must be the recipient');
    chart = await openChart(readerSession.context, masterPage, recorder, 30000);
    await waitForNavbars(chart, 30000);
    h.assert(JSON.stringify(readPatientState(sql, owner)) === ownerBeforeChart,
      'Opening the recipient chart changed the group-note owner clinical rows');
  });

  const snapshot = () => JSON.stringify({recipient: readPatientState(sql, patient), owner: readPatientState(sql, owner),
    links: sql.rows(`SELECT * FROM GroupNoteLink WHERE demographicNo IN (${patient},${owner}) ORDER BY id`)});
  const address = (id, target = patient) => h.appUrl(config.baseUrl,
    `/CaseManagementEntry?method=notehistory&demographicNo=${target}&noteId=${id}`);
  const clinicalTexts = Object.values(texts);
  async function refused(context, id, target = patient) {
    const before = snapshot();
    const response = await context.request.get(address(id, target), {failOnStatusCode: false, maxRedirects: 0});
    assertDeniedHistory(response.status(), await response.text(), clinicalTexts);
    h.assert(snapshot() === before, 'A refused history request changed either patient or the links');
  }

  await s.step('native group-note Rev displays the exact shared revision without private siblings or cumulative prose', async () => {
    const before = snapshot();
    const rev = chart.locator(`a[onclick^="return showHistory('${sharedId}'"]`);
    await rev.waitFor({state: 'visible', timeout: 30000});
    h.assert(await rev.count() === 1, 'The recipient chart does not expose the linked group-note Rev control');
    const popup = await ui.clickOpensPopup(chart, rev, {context: readerSession.context, recorder, label: 'shared-history', timeout: 30000});
    const query = new URL(popup.url()).searchParams;
    h.assert(query.get('noteId') === sharedId && query.get('demographicNo') === patient, 'Native Rev did not retain the recipient identity');
    await popup.locator('h3').first().waitFor();
    assertSharedHistoryText(await popup.locator('body').innerText(), texts);
    h.assert(await popup.locator('.note-text-history').count() === 0, 'Shared history exposes legacy cumulative text');
    h.assert(snapshot() === before, 'Reading linked history changed a patient record');
    await popup.close();
    h.assertStrictPage(recorder);
  });
  await s.step('the sharing grant does not give the recipient access to the owner chart or future revisions', async () => {
    await refused(readerSession.context, sharedId, owner);
    await refused(readerSession.context, privateId);
    await refused(readerSession.context, futureId);
    await refused(readerSession.context, '2147483647');
  });
  await s.step('an authorized recipient reader can inspect shared history without holding the reporter role', async () => {
    const before = snapshot();
    const session = await loginAs(crossRoleReader);
    const response = await session.context.request.get(address(sharedId), {failOnStatusCode: false, maxRedirects: 0});
    h.assert(response.status() === 200, 'The modern chart reader cannot inspect its explicitly shared revision');
    assertSharedHistoryText(await response.text(), texts);
    h.assert(snapshot() === before, 'Reading shared history with another role changed a patient record');
    await refused(session.context, privateId);
    await refused(session.context, futureId);
    await session.context.close();
  });
  await s.step('a restricted recipient and a login without an active note-read grant cannot expand linked history', async () => {
    for (const login of [denied, roleDenied]) {
      const session = await loginAs(login);
      await refused(session.context, sharedId);
      await session.context.close();
    }
  });
  await s.step('revoking the exact group link immediately removes history access without changing clinical rows', async () => {
    sql.execute(`UPDATE GroupNoteLink SET active=0 WHERE id=${groupLink} AND noteId=${sharedId} AND demographicNo=${patient}`);
    await refused(readerSession.context, sharedId);
  });
}

module.exports = {workflow, assertSharedHistoryText, assertDeniedHistory};
if (require.main === module) runWorkflow('echart-group-note-history', workflow, {openPatient: true});
