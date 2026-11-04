/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const { randomUUID, createHash } = require('node:crypto');
const fs = require('node:fs');
const h = require('./playwright-harness');
const { waitForNavbars } = require('../echart-navbar-modules-playwright-checks');
const { readPatientState, assertPatientStateUnchanged } = require('../echart-two-patient-isolation-playwright-checks');
const q = h.sqlString;
const positive = value => /^[1-9]\d*$/.test(String(value));
const LEGACY_OWNER_FIELDS = Object.freeze(['phone','phone2','chart_no','family_doctor','alias','pronoun','gender',
  'patient_status','roster_status','province','year_of_birth','month_of_birth','date_of_birth']);

function groupPatientLegacyValues(kind) {
  h.assert(['owner','recipient'].includes(kind), 'Unknown group fixture patient kind');
  return kind === 'owner' ? [null,null,null,null,null,null,null,null,null,' on ','','','']
    : [null,null,null,null,null,null,null,'AC','NR','ON','1980','01','02'];
}

/** A raw SQL assertion prevents Java getter defaults from masking this read invariant. */
function assertOwnedGroupLegacySeed(rows) {
  h.assert(rows.length === 1 && rows[0].length === 1
    && JSON.stringify(JSON.parse(rows[0][0])) === JSON.stringify([null,null,null,null,null,null,null,null,null,' on ','','','']),
  'The group owner must retain all thirteen raw legacy values before any browser request');
}

function assertNativeGroupRequest(request, { baseUrl, patient, noteId, token }) {
  const actual = new URL(request.url());
  const target = new URL(h.appUrl(baseUrl, '/CaseManagementView'));
  const body = new URLSearchParams(request.postData() || '');
  h.assert(request.method() === 'POST' && actual.origin === target.origin && actual.pathname === target.pathname,
    'Group expansion did not use the native application POST');
  for (const [key, expected] of Object.entries({ method: 'viewNote', raw: 'false', noteId, demographicNo: patient })) {
    h.assert(JSON.stringify(body.getAll(key)) === JSON.stringify([String(expected)]),
      `Native group expansion changed its ${key} identity`);
  }
  const contexts = [actual.searchParams, body].flatMap(parameters => parameters.getAll('chartContext'));
  const patients = [actual.searchParams, body].flatMap(parameters => [
    ...parameters.getAll('demographicNo'), ...parameters.getAll('demographic_no'),
  ]);
  h.assert(contexts.length > 0 && contexts.every(value => value === token)
    && patients.every(value => value === String(patient)), 'Native group expansion mixed patient or chart contexts');
}

function assertGroupHtmlEnvelope({ status, headers, html }) {
  h.assert(status === 200 && Object.hasOwn(headers, h.APPLICATION_HEADER)
    && /text\/html/i.test(headers['content-type'] || '') && !h.isWafPage(status, html),
  'The group expansion did not return application HTML');
  // displayNote.jsp emits only text plus literal <br> for stored line breaks.
  h.assert(!/<(?!br>)/i.test(html), 'Group expansion returned executable or unencoded markup');
}

function assertGroupExpansion(result, expected, forbidden) {
  assertGroupHtmlEnvelope(result);
  const { html, text } = result;
  h.assert(text.trim() === expected.trim(), 'Group expansion changed or truncated the exact shared text');
  h.assert(forbidden.every(value => !html.includes(value) && !text.includes(value)),
    'Group expansion disclosed an unshared note or cumulative prose');
}

function ownedGroupLockIds(rows, { provider, patient, note, sessionCookie }) {
  h.assert(typeof sessionCookie === 'string' && sessionCookie.split('.')[0], 'The owned group session cookie is missing');
  // A routing suffix may be transport-only; retain exact full-ID compatibility too.
  const sessions = new Set([sessionCookie, sessionCookie.split('.')[0]]);
  return new Set(rows.filter(row => row[1] === provider && row[2] === patient
    && row[3] === note && sessions.has(row[4])).map(row => row[0]));
}

/** A separate owned fixture exercises the native fullView caller and exact group sharing policy. */
async function checkGroupNoteFullView(s, { fixture }) {
  const { sql, provider, marker, config, recorder } = s;
  const patients = ['recipient', 'owner'].map((kind, index) => ({
    kind, name: `${marker}-GV${index}`, first: 'GroupExpand', id: null,
  }));
  const notes = [];
  const logins = [];
  const sessions = [];
  const programName = `${marker}-group-full-view`;
  let program;
  let facility;
  let disabledFacility;
  let linkId;
  let linkClaimed = false;
  let reader;
  let chart;
  let chartToken;
  const role = sql.rows("SELECT role_no,role_name FROM secRole WHERE role_name='doctor' ORDER BY role_no");
  h.assert(role.length === 1 && positive(role[0][0]), 'Group expansion needs the existing cached doctor reporter role');
  const reporterRole = role[0][0];
  const policy = () => ({
    role: sql.rows(`SELECT * FROM secRole WHERE role_no=${reporterRole}`),
    defaults: sql.rows('SELECT * FROM default_role_access ORDER BY id'),
    mainDefault: sql.rows(`SELECT * FROM provider_default_program WHERE provider_no=${q(provider)} ORDER BY program_id`),
  });
  const originalPolicy = JSON.stringify(policy());
  h.assert(patients.every(patient => patient.name.length <= 30)
    && sql.value(`SELECT COUNT(*) FROM demographic WHERE last_name IN (${patients.map(patient => q(patient.name)).join(',')})`) === '0'
    && sql.value(`SELECT COUNT(*) FROM program WHERE name=${q(programName)}`) === '0',
  'An intended group expansion fixture already exists');

  // Registered before any INSERT. Resolve durable names/UUIDs after a lost ID,
  // then validate the entire relationship graph before deleting any child.
  s.cleanup(() => {
    for (const patient of patients) {
      if (!patient.id) {
        const rows = sql.rows(`SELECT demographic_no FROM demographic WHERE last_name=${q(patient.name)}
          AND first_name=${q(patient.first)} AND provider_no=${q(provider)}`);
        h.assert(rows.length <= 1, 'Group expansion patient recovery is ambiguous');
        patient.id = rows[0]?.[0];
      }
      if (patient.id) h.assert(positive(patient.id) && sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient.id}
        AND last_name=${q(patient.name)} AND first_name=${q(patient.first)} AND provider_no=${q(provider)}`) === '1',
      'Group expansion patient ownership changed; retaining children');
    }
    if (!program && facility) {
      const rows = sql.rows(`SELECT id FROM program WHERE name=${q(programName)} AND facilityId=${facility.id}`);
      h.assert(rows.length <= 1, 'Group expansion program recovery is ambiguous');
      program = rows[0]?.[0];
    }
    h.assert(!program || positive(program), 'Group expansion program identity is invalid');
    const patientIds = patients.filter(patient => patient.id).map(patient => patient.id);
    const patientSet = patientIds.length ? patientIds.join(',') : 'SELECT demographic_no FROM demographic WHERE 1=0';
    for (const note of notes) {
      const rows = sql.rows(`SELECT note_id,demographic_no,note,uuid,provider_no FROM casemgmt_note WHERE uuid=${q(note.uuid)}`);
      h.assert(rows.length <= 1 && rows.every(row => row[1] === note.patient.id && row[2] === note.body
        && row[4] === note.author && (!note.id || row[0] === note.id)),
      'Group expansion note ownership changed; retaining relationships');
      if (!note.id) note.id = rows[0]?.[0];
    }
    const noteIds = notes.filter(note => note.id).map(note => note.id);
    h.assert(noteIds.every(positive), 'Group expansion note cleanup identity is invalid');
    const noteSet = noteIds.length ? noteIds.join(',') : 'SELECT note_id FROM casemgmt_note WHERE 1=0';
    const storedNotes = sql.rows(`SELECT note_id,demographic_no,note,uuid FROM casemgmt_note
      WHERE demographic_no IN (${patientSet}) OR note_id IN (${noteSet})`);
    h.assert(storedNotes.every(row => notes.some(note => note.id === row[0] && note.patient.id === row[1]
      && note.body === row[2] && note.uuid === row[3])), 'Unexpected group fixture note; retaining clinical rows');
    const links = sql.rows(`SELECT id,noteId,demographicNo FROM GroupNoteLink
      WHERE demographicNo IN (${patientSet}) OR noteId IN (${noteSet})`);
    const shared = notes.find(note => note.kind === 'shared');
    h.assert(links.length <= 1 && links.every(row => linkClaimed && row[1] === shared?.id
      && row[2] === patients[0].id && (!linkId || row[0] === linkId)),
    'A group fixture acquired an unowned link; retaining its notes and patients');
    if (!linkId) linkId = links[0]?.[0];
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_issue_notes WHERE note_id IN (${noteSet}))
      +(SELECT COUNT(*) FROM casemgmt_note_ext WHERE note_id IN (${noteSet}))
      +(SELECT COUNT(*) FROM casemgmt_note_link WHERE note_id IN (${noteSet}))
      +(SELECT COUNT(*) FROM hash_audit WHERE type='enc' AND id IN (${noteSet}))`) === '0',
    'A group fixture note acquired unrelated clinical children');
    for (const patient of patients.filter(patient => patient.id)) {
      const state = readPatientState(sql, patient.id);
      const allowed = new Set(['demographic', 'demographicExtensions', 'notes', 'echart', 'drafts', 'cpp']);
      for (const [name, rows] of Object.entries(state)) h.assert(allowed.has(name) || rows.length === 0,
        `Unexpected ${name} on the group fixture; retaining all children`);
    }
    if (program) {
      h.assert(sql.value(`SELECT COUNT(*) FROM program WHERE id=${program} AND name=${q(programName)}
        AND facilityId=${facility.id}`) === '1', 'Group expansion program ownership changed');
      const providers = logins.map(login => q(login.providerNo)).join(',') || "''";
      h.assert(sql.value(`SELECT COUNT(*) FROM program_provider WHERE program_id=${program}
        AND (provider_no IS NULL OR provider_no NOT IN (${providers}))`) === '0'
        && sql.value(`SELECT COUNT(*) FROM provider_default_program WHERE program_id=${program}
        AND (provider_no IS NULL OR provider_no NOT IN (${providers}))`) === '0'
        && sql.value(`SELECT COUNT(*) FROM admission WHERE program_id=${program}
        AND (client_id IS NULL OR client_id NOT IN (${patientSet}))`) === '0'
        && sql.value(`SELECT COUNT(*) FROM admission WHERE client_id IN (${patientSet})
        AND (program_id IS NULL OR program_id<>${program})`) === '0'
        && sql.value(`SELECT COUNT(*) FROM program_access WHERE program_id=${program}`) === '0',
      'The group expansion program acquired foreign members or policy');
    }
    const ownedProviders = logins.map(login => q(login.providerNo)).join(',') || "''";
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no IN (${patientSet})
      AND (provider_no IS NULL OR provider_no NOT IN (${ownedProviders}))`) === '0'
      && sql.value(`SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no IN (${patientSet})
      AND (provider_no IS NULL OR provider_no NOT IN (${ownedProviders}))`) === '0',
    'Another provider owns a group fixture draft or lock; retaining it');
    for (const login of logins) h.assert(sql.value(`SELECT COUNT(*) FROM provider_default_program
      WHERE provider_no=${q(login.providerNo)}${program ? ` AND (program_id IS NULL OR program_id<>${program})` : ''}`) === '0',
    'An owned group reader default changed outside the fixture');
    h.assert(JSON.stringify(policy()) === originalPolicy, 'Shared group reporter policy changed');
    // Every ownership check above precedes these mutations.
    if (linkId) sql.execute(`DELETE FROM GroupNoteLink WHERE id=${linkId}`);
    sql.execute(`DELETE FROM hash_audit WHERE type='enc' AND id IN (${noteSet});
      DELETE FROM casemgmt_note WHERE note_id IN (${noteSet});
      DELETE FROM casemgmt_note_lock WHERE demographic_no IN (${patientSet});
      DELETE FROM casemgmt_tmpsave WHERE demographic_no IN (${patientSet});
      DELETE FROM casemgmt_cpp WHERE demographic_no IN (${patientSet});
      DELETE FROM eChart WHERE demographicNo IN (${patientSet});
      DELETE FROM admission WHERE client_id IN (${patientSet});
      DELETE FROM demographicExt WHERE demographic_no IN (${patientSet});
      DELETE FROM demographicArchive WHERE demographic_no IN (${patientSet});
      DELETE FROM log WHERE demographic_no IN (${patientSet})`);
    for (const patient of patients.filter(patient => patient.id)) {
      sql.execute(`DELETE FROM demographic WHERE demographic_no=${patient.id} AND last_name=${q(patient.name)}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient.id}`) === '0',
        'The group expansion patient survived cleanup');
    }
    if (program) for (const login of logins) sql.execute(`DELETE FROM provider_default_program WHERE provider_no=${q(login.providerNo)}
      AND program_id=${program}`);
    if (program) {
      sql.execute(`DELETE FROM program_provider WHERE program_id=${program};
        DELETE FROM program WHERE id=${program} AND name=${q(programName)}`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM program WHERE id=${program})
        +(SELECT COUNT(*) FROM program_provider WHERE program_id=${program})
        +(SELECT COUNT(*) FROM admission WHERE program_id=${program})`) === '0', 'Group program residue remains');
    }
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_note WHERE note_id IN (${noteSet})
        OR demographic_no IN (${patientSet}))
      +(SELECT COUNT(*) FROM GroupNoteLink WHERE noteId IN (${noteSet}) OR demographicNo IN (${patientSet}))
      +(SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no IN (${patientSet}))
      +(SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no IN (${patientSet}))
      +(SELECT COUNT(*) FROM casemgmt_cpp WHERE demographic_no IN (${patientSet}))
      +(SELECT COUNT(*) FROM eChart WHERE demographicNo IN (${patientSet}))
      +(SELECT COUNT(*) FROM admission WHERE client_id IN (${patientSet}))`) === '0', 'Group expansion clinical child residue remains');
  });

  facility = fixture.addFacility('GV1');
  disabledFacility = fixture.addFacility('GV0');
  for (const [owned, enabled] of [[facility, 1], [disabledFacility, 0]]) sql.execute(`UPDATE Facility
    SET enableGroupNotes=${enabled} WHERE id=${owned.id} AND name=${q(owned.name)} AND description=${q(marker)}`);
  for (const patient of patients) patient.id = h.insertId(sql, `INSERT INTO demographic
    (last_name,first_name,sex,provider_no,hc_type,lastUpdateDate,${LEGACY_OWNER_FIELDS.join(',')})
    VALUES (${q(patient.name)},${q(patient.first)},'F',${q(provider)},'ON',NOW(),
      ${groupPatientLegacyValues(patient.kind).map(value => value === null ? 'NULL' : q(value)).join(',')})`, 'group expansion patient');
  const owner = patients.find(patient => patient.kind === 'owner');
  assertOwnedGroupLegacySeed(sql.rows(`SELECT JSON_ARRAY(${LEGACY_OWNER_FIELDS.join(',')}) FROM demographic
    WHERE demographic_no=${owner.id} AND BINARY last_name=${q(owner.name)} AND first_name=${q(owner.first)}
      AND provider_no=${q(provider)}`));
  const moduleRole = fixture.addRole({ _demographic: 'r', _eChart: 'r', _appointment: 'r', '_casemgmt.notes': 'r' });
  const makeLogin = (roleName, ownedFacility) => {
    const login = fixture.addLogin(roleName);
    logins.push(login);
    fixture.joinFacility(login, ownedFacility);
    return login;
  };
  const allowed = makeLogin('doctor', facility);
  const recordDenied = makeLogin('doctor', facility);
  const reporterDenied = makeLogin(moduleRole, facility);
  const facilityDenied = makeLogin('doctor', disabledFacility);
  fixture.lockPatient(allowed, patients[1].id, ['_eChart']);
  // The read route's module gates are _eChart and notes. A demographic exclusion
  // exercises the independent central record boundary while those grants remain.
  fixture.lockPatient(recordDenied, patients[0].id, ['_demographic']);
  program = h.insertId(sql, `INSERT INTO program (facilityId,name,type,maxAllowed,programStatus,transgender,firstNation,
    alcohol,physicalHealth,mentalHealth,housing,exclusiveView,ageMin,ageMax,enableEncounterTime,
    enableEncounterTransportationTime,enableOCAN) VALUES (${facility.id},${q(programName)},'service',1,'active',
    0,0,0,0,0,0,'none',0,150,0,0,0)`, 'group expansion program');
  for (const login of logins) {
    h.assert(sql.value(`SELECT COUNT(*) FROM provider_default_program WHERE provider_no=${q(login.providerNo)}`) === '0',
      'The unused group reader has a preexisting program default');
    sql.execute(`INSERT INTO program_provider(program_id,provider_no,role_id,team_id)
      VALUES (${program},${q(login.providerNo)},${reporterRole},0);
      INSERT INTO provider_default_program(provider_no,program_id) VALUES (${q(login.providerNo)},${program})`);
  }
  sql.execute(`INSERT INTO admission(client_id,program_id,provider_no,admission_date,admission_from_transfer,
    discharge_from_transfer,admission_status,lastUpdateDate) VALUES (${patients[0].id},${program},
    ${q(allowed.providerNo)},NOW(),0,0,'current',NOW()),(${patients[1].id},${program},
    ${q(allowed.providerNo)},NOW(),0,0,'current',NOW())`);
  for (const [kind, patient] of [['shared', patients[1]], ['unlinked', patients[1]], ['control', patients[0]]]) {
    const note = { kind, patient, author: allowed.providerNo, uuid: randomUUID(),
      body: `${marker}-GROUP-${kind} <b>literal & café 中文</b>\n${'retained shared text '.repeat(8)}${marker}-${kind}-END` };
    notes.push(note);
    note.id = h.insertId(sql, `INSERT INTO casemgmt_note (update_date,observation_date,demographic_no,provider_no,note,
      history,uuid,signed,signing_provider_no,locked,archived,include_issue_innote,position,program_no,
      reporter_caisi_role,reporter_program_team,appointmentNo,encounter_type) VALUES (NOW(),'2000-01-02',${patient.id},
      ${q(note.author)},${q(note.body)},${q(`${marker}-${kind}-CUMULATIVE`)},${q(note.uuid)},1,${q(note.author)},0,0,0,0,
      ${q(kind === 'control' ? program : '0')},${q(reporterRole)},'0',0,'')`, 'group expansion note');
  }
  const shared = notes.find(note => note.kind === 'shared');
  h.assert(sql.value(`SELECT COUNT(*) FROM GroupNoteLink WHERE noteId IN (${notes.map(note => note.id).join(',')})
    OR demographicNo IN (${patients.map(patient => patient.id).join(',')})`) === '0', 'An allocated group fixture inherited a link');
  linkClaimed = true;
  linkId = h.insertId(sql, `INSERT INTO GroupNoteLink(noteId,demographicNo,anonymous,active,created)
    VALUES (${shared.id},${patients[0].id},0,1,NOW())`, 'group expansion link');

  const loginAs = async login => {
    const context = await h.newContext(s.context.browser(), config);
    const session = { context, login };
    sessions.push(session);
    s.cleanup(() => context.close());
    context.on('page', page => h.wireStrictPage(page, 'group-full-view', recorder, { baseline: [] }));
    session.page = await h.login(context, { ...config, testUser: login.username }, recorder, { baseline: [] });
    session.csrf = await session.page.evaluate(() => window.CarlosAjax?.getCsrfToken()
      || document.querySelector('input[name="CSRF-TOKEN"]')?.value);
    h.assert(session.csrf, 'The owned group reader has no rendered CSRF token');
    return session;
  };
  const snapshot = () => ({
    patients: patients.map(patient => ({ ...readPatientState(sql, patient.id),
      locks: sql.rows(`SELECT * FROM casemgmt_note_lock WHERE demographic_no=${patient.id} ORDER BY id`) })),
    links: sql.rows(`SELECT * FROM GroupNoteLink WHERE id=${linkId}`),
    program: sql.rows(`SELECT * FROM program WHERE id=${program}`),
    memberships: sql.rows(`SELECT * FROM program_provider WHERE program_id=${program} ORDER BY id`),
    admissions: sql.rows(`SELECT * FROM admission WHERE program_id=${program} ORDER BY am_id`),
    defaults: sql.rows(`SELECT * FROM provider_default_program WHERE provider_no IN (${logins.map(login => q(login.providerNo)).join(',')}) ORDER BY provider_no`),
    facilities: sql.rows(`SELECT * FROM Facility WHERE id IN (${facility.id},${disabledFacility.id}) ORDER BY id`),
    policy: policy(),
  });
  const apiRead = async (session, note, { raw = 'false', target = patients[0].id, token = '' } = {}) => {
    const before = snapshot();
    const response = await session.context.request.post(h.appUrl(config.baseUrl, '/CaseManagementView'), {
      form: { method: 'viewNote', raw, noteId: note.id, demographicNo: target, 'CSRF-TOKEN': session.csrf,
        ...(token ? { chartContext: token } : {}) }, maxRedirects: 0,
    });
    const result = { status: response.status(), headers: response.headers(), html: await response.text() };
    await response.dispose();
    h.assert(JSON.stringify(snapshot()) === JSON.stringify(before), 'A group-note read changed clinical, draft, lock or policy state');
    return result;
  };
  const denied = result => h.assert(result.status === 403 && Object.hasOwn(result.headers, h.APPLICATION_HEADER)
    && !h.isWafPage(result.status, result.html)
    && notes.every(note => !result.html.includes(`${marker}-GROUP-${note.kind}`)
      && !result.html.includes(`${marker}-${note.kind}-CUMULATIVE`)), 'A refused group expansion leaked clinical text or was not an application 403');
  const controlRead = async (session, note, options = {}) => {
    const result = await apiRead(session, note, options);
    h.assert(result.status === 200 && Object.hasOwn(result.headers, h.APPLICATION_HEADER)
      && result.html.includes(`${marker}-GROUP-${note.kind}`) && result.html.includes(`${marker}-${note.kind}-END`),
    'The group refusal control lacks ordinary note access or a valid CSRF session');
  };
  let failure;
  try {
    await s.step('a native group fullView click expands exactly its shared recipient text without changing the owner', async () => {
      reader = await loginAs(allowed);
      chart = await reader.context.newPage();
      const ownerBeforeChart = readPatientState(sql, patients[1].id);
      await h.gotoApp(chart, config.baseUrl, `/encounter/IncomingEncounter?${new URLSearchParams({
        demographicNo: patients[0].id, providerNo: allowed.providerNo, programId: program,
      })}`);
      await waitForNavbars(chart, 90000);
      const text = chart.locator(`#txt${shared.id}`);
      const trigger = chart.locator(`#quitImg${shared.id}[name="fullViewTrigger"]`);
      await trigger.waitFor({ state: 'visible' });
      assertPatientStateUnchanged(ownerBeforeChart, readPatientState(sql, patients[1].id), 'Opening the recipient group chart');
      h.assert(await trigger.count() === 1 && !(await text.innerText()).includes(`${marker}-shared-END`)
        && await chart.locator(`#caseNote_note${shared.id}`).count() === 0, 'The native shared note was not shortened and read-only');
      await chart.waitForFunction(() => !window.autoSaveXhr);
      await chart.evaluate(() => { if (typeof clearAutoSaveTimer === 'function') clearAutoSaveTimer(); });
      chartToken = await chart.locator('#caseManagementEntryForm input[name="chartContext"]').inputValue();
      reader.csrf = await chart.evaluate(() => window.CarlosAjax?.getCsrfToken());
      h.assert(reader.csrf, 'The native group chart has no current CSRF token');
      const before = snapshot();
      const responsePromise = chart.waitForResponse(response => response.request().method() === 'POST'
        && new URL(response.url()).pathname.endsWith('/CaseManagementView')
        && new URLSearchParams(response.request().postData() || '').get('method') === 'viewNote');
      responsePromise.catch(() => {});
      await trigger.click();
      const response = await responsePromise;
      await response.finished();
      assertNativeGroupRequest(response.request(), { baseUrl: config.baseUrl, patient: patients[0].id,
        noteId: shared.id, token: chartToken });
      const html = await response.text();
      const evidence = fs.mkdtempSync('/tmp/carlos-group-full-view-');
      fs.writeFileSync(`${evidence}/response.html`, html, { mode: 0o600 });
      fs.writeFileSync(`${evidence}/proof.json`, JSON.stringify({ patient: patients[0].id, noteId: shared.id,
        program, status: response.status(), fromApplication: Object.hasOwn(response.headers(), h.APPLICATION_HEADER),
        responseSHA256: createHash('sha256').update(html).digest('hex'), bytes: Buffer.byteLength(html),
        nativeRequestIdentityVerified: true }, null, 2) + '\n', { mode: 0o600 });
      console.log(`  Group native expansion evidence ${evidence}/proof.json`);
      const result = { status: response.status(), headers: response.headers(), html };
      assertGroupHtmlEnvelope(result);
      await chart.waitForFunction(({ id, tail }) => document.getElementById(id)?.textContent.includes(tail),
        { id: `txt${shared.id}`, tail: `${marker}-shared-END` });
      const decoded = await text.evaluate(node => {
        const copy = node.cloneNode(true);
        for (const lineBreak of copy.querySelectorAll('br')) lineBreak.replaceWith(document.createTextNode('\n'));
        return copy.textContent;
      });
      assertGroupExpansion({ ...result, text: decoded }, shared.body,
        notes.filter(note => note !== shared).map(note => `${marker}-GROUP-${note.kind}`)
          .concat(notes.map(note => `${marker}-${note.kind}-CUMULATIVE`)));
      h.assert((await text.innerText()).includes('<b>literal & café 中文</b>')
        && await chart.locator(`#caseNote_note${shared.id}`).count() === 0, 'Native expansion lost literal text or enabled the group editor');
      h.assert(JSON.stringify(snapshot()) === JSON.stringify(before), 'Native expansion changed a source row, UUID, clinical draft, lock or policy');
    });
    await s.step('a shared read does not authorize the raw editor, an unlinked note or the owner chart', async () => {
      const control = notes.find(note => note.kind === 'control');
      await controlRead(reader, control, { token: chartToken });
      denied(await apiRead(reader, shared, { raw: 'true', token: chartToken }));
      denied(await apiRead(reader, notes.find(note => note.kind === 'unlinked'), { token: chartToken }));
      denied(await apiRead(reader, shared, { target: patients[1].id, token: chartToken }));
      await controlRead(reader, control, { token: chartToken });
    });
    await s.step('recipient record, global reporter role and current facility remain independent group-read boundaries', async () => {
      for (const login of [recordDenied, reporterDenied, facilityDenied]) {
        const session = await loginAs(login);
        const control = notes.find(note => note.kind === (login === recordDenied ? 'shared' : 'control'));
        const target = login === recordDenied ? patients[1].id : patients[0].id;
        await controlRead(session, control, { target });
        denied(await apiRead(session, shared));
        await controlRead(session, control, { target });
      }
    });
    await s.step('revoking the exact active link refuses expansion and restoring it changes no clinical state', async () => {
      const control = notes.find(note => note.kind === 'control');
      await controlRead(reader, control, { token: chartToken });
      const before = snapshot();
      const linkBefore = sql.rows(`SELECT id,noteId,demographicNo,anonymous,active,created FROM GroupNoteLink WHERE id=${linkId}`);
      h.assert(linkBefore.length === 1 && linkBefore[0][4] === '1', 'The owned group link is not active');
      const inactive = linkBefore.map(row => [...row]); inactive[0][4] = '0';
      sql.execute(`UPDATE GroupNoteLink SET active=0 WHERE id=${linkId} AND noteId=${shared.id}
        AND demographicNo=${patients[0].id} AND active=1`);
      try { denied(await apiRead(reader, shared, { token: chartToken })); }
      finally {
        h.assert(JSON.stringify(sql.rows(`SELECT id,noteId,demographicNo,anonymous,active,created FROM GroupNoteLink WHERE id=${linkId}`))
          === JSON.stringify(inactive), 'Group link drifted; refusing to overwrite it during restoration');
        sql.execute(`UPDATE GroupNoteLink SET active=1 WHERE id=${linkId} AND noteId=${shared.id}
          AND demographicNo=${patients[0].id} AND active=0`);
      }
      h.assert(JSON.stringify(snapshot()) === JSON.stringify(before), 'Group-link restoration changed unrelated state');
      await controlRead(reader, control, { token: chartToken });
    });
  } catch (error) { failure = error; throw error; }
  finally {
    if (chart && !chart.isClosed()) {
      try {
        const form = await chart.evaluate(() => {
          const editor = document.forms.caseManagementEntryForm;
          return editor && { patient: String(window.demographicNo), provider: String(window.providerNo),
            note: String(editor.elements.noteId?.value), token: String(editor.elements.chartContext?.value || ''),
            csrf: window.CarlosAjax?.getCsrfToken() };
        });
        h.assert(form && form.patient === patients[0].id && form.provider === allowed.providerNo && form.csrf
          && (!chartToken || form.token === chartToken)
          && Buffer.from(form.token.split('.')[0], 'base64url').toString('utf8') === `${patients[0].id}:${program}`
          && /^(?:0|-1|[1-9]\d*)$/.test(form.note), 'Owned group chart retirement identity changed');
        if (!['0', '-1'].includes(form.note)) h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note
          WHERE note_id=${form.note} AND demographic_no=${form.patient}`) === '1', 'Retirement note belongs to another patient');
        const cookies = (await reader.context.cookies(config.baseUrl.href)).filter(cookie => cookie.name === 'JSESSIONID');
        h.assert(cookies.length === 1, 'Owned group chart has no unique session');
        const before = snapshot();
        const oldLocks = before.patients[0].locks;
        const columns = sql.rows(`SELECT id,provider_no,demographic_no,note_id,session_id FROM casemgmt_note_lock
          WHERE demographic_no=${patients[0].id} ORDER BY id`);
        const ownedIds = ownedGroupLockIds(columns, { provider: allowed.providerNo, patient: form.patient,
          note: form.note, sessionCookie: cookies[0].value });
        const response = await reader.context.request.post(h.appUrl(config.baseUrl, '/CaseManagementEntry'), {
          form: { method: 'releaseNoteLock', demographicNo: form.patient, providerNo: form.provider,
            noteId: form.note, chartContext: form.token, 'CSRF-TOKEN': form.csrf }, maxRedirects: 0,
        });
        try { h.assert(response.status() === 200 && Object.hasOwn(response.headers(), h.APPLICATION_HEADER),
          'Owned group chart lock release was not acknowledged by the application'); }
        finally { await response.dispose(); }
        const after = snapshot();
        h.assert(JSON.stringify(after.patients[0].locks) === JSON.stringify(oldLocks.filter(row => !ownedIds.has(row[0]))),
          'Group chart retirement changed another editor lock');
        before.patients[0].locks = []; after.patients[0].locks = [];
        h.assert(JSON.stringify(after) === JSON.stringify(before), 'Group chart retirement changed clinical, draft or policy state');
        h.assert(await chart.evaluate(expected => {
          const editor = document.forms.caseManagementEntryForm;
          if (!editor || String(window.demographicNo) !== expected.patient || String(window.providerNo) !== expected.provider
              || String(editor.elements.noteId?.value) !== expected.note || String(editor.elements.chartContext?.value || '') !== expected.token) return false;
          window.needToReleaseLock = false;
          return true;
        }, form), 'Owned group chart changed after its release acknowledgment');
        await chart.close();
      } catch (error) {
        if (failure) { failure.message += `; group chart retirement: ${error.message}`; }
        else throw error;
      }
    }
  }
}

module.exports = { checkGroupNoteFullView, assertNativeGroupRequest, assertGroupExpansion, ownedGroupLockIds,
  groupPatientLegacyValues, assertOwnedGroupLegacySeed };
