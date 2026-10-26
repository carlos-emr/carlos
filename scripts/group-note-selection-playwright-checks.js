/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const { randomUUID, createHash } = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { authzReadFixture } = require('./lib/authz-read-fixture');
const { waitForNavbars } = require('./echart-navbar-modules-playwright-checks');
const { readPatientState } = require('./echart-two-patient-isolation-playwright-checks');
const q = h.sqlString;
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
const positive = value => /^[1-9]\d*$/.test(String(value));
const LEGACY_PROGRAM_FIELDS = Object.freeze(['holdingTank','allowBatchAdmission','allowBatchDischarge','hic',
  'userDefined','defaultServiceRestrictionDays']);

function groupProgramLegacyValues() { return [null,null,null,null,null,null]; }

/** Check SQL JSON types before the owned programs are first read by the application. */
function assertOwnedGroupProgramLegacySeed(rows, programs) {
  h.assert(programs.length === 2 && programs.every(positive) && new Set(programs).size === 2
    && rows.length === 2 && rows.every((row, index) => row.length === 2 && row[0] === programs[index]
      && same(JSON.parse(row[1]), [null,null,null,null,null,null])),
  'Both owned group programs must retain all six raw SQL NULL values before their first native request');
}

function assertSelectionPopupResponse(response, { baseUrl }) {
  const expected = new URL(h.appUrl(baseUrl, '/casemgmt/ViewGroupNoteSelect'));
  h.assert(response, 'Native Group popup did not produce an observed document response');
  const actual = new URL(response.url());
  h.assert(actual.origin === expected.origin && actual.pathname === expected.pathname && response.request().method() === 'GET',
    `Native Group popup opened an unexpected document route (HTTP ${response.status()})`);
  h.assert(response.status() === 200 && Object.hasOwn(response.headers(), h.APPLICATION_HEADER),
    `Native Group popup GET returned HTTP ${response.status()} without its expected application view`);
}

async function openSelectionPopup(context, chart, config) {
  // Observe the context before clicking: a popup's initial response can arrive
  // before Playwright emits its page event.
  const responses = [];
  const observe = answer => { if (answer.request().isNavigationRequest()) responses.push(answer); };
  context.on('response', observe);
  const opened = context.waitForEvent('page');
  try {
    await chart.locator('#groupNoteImg').click();
    const page = await opened;
    await page.waitForLoadState('networkidle');
    assertSelectionPopupResponse(responses.filter(answer => answer.frame() === page.mainFrame()).at(-1), { baseUrl: config.baseUrl });
    return page;
  } finally { context.off('response', observe); }
}

function groupSelectionProbeOptions(baseUrl, base, probe) {
  const csrf = base['CSRF-TOKEN'];
  const parameters = { ...base, ...probe.extra };
  h.assert(typeof csrf === 'string' && csrf !== '' && parameters['CSRF-TOKEN'] === csrf,
    'Group method probe must retain its genuine captured CSRF token');
  const address = new URL(h.appUrl(baseUrl, '/casemgmt/SaveGroupNoteSelection'));
  const options = { method: probe.method, maxRedirects: 0, failOnStatusCode: false,
    // Servlet parameter parsing does not read a PUT form body. Reach the method
    // guard with the same authenticated AJAX token carrier the application uses.
    headers: { 'CSRF-TOKEN': csrf, 'X-Requested-With': 'XMLHttpRequest' } };
  if (['GET', 'HEAD'].includes(probe.method)) address.search = new URLSearchParams(parameters).toString();
  else options.form = parameters;
  return { address, options };
}

/** Persist only the exact newly owned policy graph, before its first native read. */
function writeStagedGroupPolicy(directory, policy, columns, scope) {
  const names = ['programs', 'memberships', 'admissions'];
  h.assert(same(Object.keys(policy), names) && same(Object.keys(columns), names)
    && /^FAKE-PW[0-9a-f]{16}$/.test(scope.marker) && positive(scope.provider) && positive(scope.facility)
    && scope.programs.length === 2 && scope.patients.length === 4
    && [...scope.programs, ...scope.patients].every(positive)
    && new Set(scope.programs).size === 2 && new Set(scope.patients).size === 4,
  'Invalid owned group policy evidence scope');
  const cell = (table, row, name) => {
    const index = columns[table].indexOf(name);
    h.assert(index >= 0 && row.length === columns[table].length, 'Incomplete owned policy schema');
    return row[index];
  };
  for (const name of names) h.assert(Array.isArray(columns[name]) && new Set(columns[name]).size === columns[name].length
    && columns[name].every(column => /^[A-Za-z][A-Za-z0-9_]*$/.test(column)) && Array.isArray(policy[name]),
  'Unexpected owned policy evidence table');
  h.assert(policy.programs.length === 2 && policy.programs.every((row, index) => cell('programs', row, 'id') === scope.programs[index]
    && cell('programs', row, 'facilityId') === scope.facility
    && cell('programs', row, 'name') === `${scope.marker}-group-select-${['A', 'B'][index]}`),
  'Group policy evidence contains an unowned program');
  h.assert(policy.memberships.length === 2 && policy.memberships.every(row => scope.programs.includes(cell('memberships', row, 'program_id'))
    && cell('memberships', row, 'provider_no') === scope.provider)
    && new Set(policy.memberships.map(row => cell('memberships', row, 'program_id'))).size === 2,
  'Group policy evidence contains an unowned membership');
  const pairs = policy.admissions.map(row => {
    h.assert(cell('admissions', row, 'provider_no') === scope.provider, 'Group policy evidence contains an unowned admission provider');
    return [cell('admissions', row, 'client_id'), cell('admissions', row, 'program_id')].join(':');
  }).sort();
  const [owner, first, other, second] = scope.patients;
  const [programA, programB] = scope.programs;
  h.assert(same(pairs, [[owner, programA], [owner, programB], [first, programA], [other, programB], [second, programA]]
    .map(pair => pair.join(':')).sort()), 'Group policy evidence contains a foreign, missing or duplicate admission');
  const stat = fs.lstatSync(directory);
  h.assert(stat.isDirectory() && !stat.isSymbolicLink() && (stat.mode & 0o777) === 0o700, 'Group evidence needs its private owned directory');
  const file = path.join(directory, 'staged-policy.json');
  const fd = fs.openSync(file, fs.constants.O_WRONLY | fs.constants.O_CREAT | fs.constants.O_EXCL | fs.constants.O_NOFOLLOW, 0o600);
  try {
    fs.fchmodSync(fd, 0o600);
    fs.writeFileSync(fd, JSON.stringify({ scope, columns, rows: policy }, null, 2) + '\n');
    fs.fsyncSync(fd);
  } finally { fs.closeSync(fd); }
  return file;
}

function assertSelectionRequest(request, { baseUrl, patient, program, note, token, recipient }) {
  const address = new URL(request.url());
  const expected = new URL(h.appUrl(baseUrl, '/casemgmt/SaveGroupNoteSelection'));
  const body = new URLSearchParams(request.postData() || '');
  h.assert(request.method() === 'POST' && address.origin === expected.origin && address.pathname === expected.pathname,
    'Native group selection did not submit its mapped POST action');
  for (const [name, value] of Object.entries({ demographicNo: patient, programId: program, noteId: note,
    chartContext: token, num_anonymous: '0' })) h.assert(same(body.getAll(name), [String(value)]),
  `Native group selection changed or repeated its ${name}`);
  h.assert(same(body.getAll('group_client_id'), recipient ? [recipient] : [])
    && body.getAll('CSRF-TOKEN').length > 0 && body.getAll('CSRF-TOKEN').every(value => value && value === body.get('CSRF-TOKEN')),
  'Native group selection changed its recipients or omitted the genuine CSRF authority');
}

function assertSelectionRedirect(response, { baseUrl, patient, program, token }) {
  const address = new URL(response.headers().location || '', baseUrl);
  const target = new URL(h.appUrl(baseUrl, '/casemgmt/ViewGroupNoteSelectAction'));
  h.assert(response.status() === 303 && Object.hasOwn(response.headers(), h.APPLICATION_HEADER)
    && address.origin === target.origin && address.pathname === target.pathname,
  'Group selection did not redirect its accepted POST to the mapped GET close view');
  for (const [name, value] of Object.entries({ demographicNo: patient, programId: program, chartContext: token })) {
    h.assert(same(address.searchParams.getAll(name), [String(value)]), 'The group redirect lost its originating window identity');
  }
  return address;
}

/** Compare complete sharing rows; only ORM's retirement timestamp may accompany the intended active flag change. */
function linkIndex(rows, columns) {
  const required = ['id', 'noteId', 'demographicNo', 'anonymous', 'active', 'created'];
  h.assert(new Set(columns).size === columns.length && required.every(name => columns.includes(name)), 'Group row schema is incomplete');
  const fields = Object.fromEntries(required.map(name => [name, columns.indexOf(name)]));
  h.assert(rows.every(row => row.length === columns.length && positive(row[fields.id])), 'Group row shape or identity changed');
  const indexed = new Map(rows.map(row => [row[fields.id], row]));
  h.assert(indexed.size === rows.length, 'Group row identities are duplicated');
  return { fields, indexed };
}

function assertParallelSelectionDelta(before, after, { columns, note, recipients }) {
  const { fields: f, indexed: original } = linkIndex(before, columns);
  const { indexed: current } = linkIndex(after, columns);
  const wanted = new Set(recipients.map(String));
  h.assert(wanted.size === 2 && [...wanted].every(positive)
    && before.filter(row => row[f.noteId] === note).every(row => row[f.active] === '0'),
  'Parallel native selection requires two owned recipients and an initially empty group');
  for (const [id, row] of original) h.assert(same(current.get(id), row), 'Parallel group replacement changed a preexisting or bystander row');
  const added = after.filter(row => !original.has(row[f.id]));
  const floor = Math.max(0, ...before.map(row => Number(row[f.id])));
  h.assert(added.length === 2 && current.size === original.size + 2
    && added.every(row => Number(row[f.id]) > floor && row[f.noteId] === note && wanted.has(row[f.demographicNo])
      && row[f.anonymous] === '0' && ['0', '1'].includes(row[f.active])
      && typeof row[f.created] === 'string' && row[f.created] !== '' && row[f.created] !== 'NULL' && row[f.created] !== '\\N')
    && new Set(added.map(row => row[f.demographicNo])).size === 2,
  'Parallel native posts did not each add exactly their own recipient to the same source note');
  const active = after.filter(row => row[f.noteId] === note && row[f.active] === '1');
  h.assert(active.length === 1 && wanted.has(active[0][f.demographicNo]), 'Parallel selection left a union or lost both complete replacement sets');
  return active.map(row => row[f.demographicNo]);
}

function assertClearedSelectionDelta(before, after, { columns, note }) {
  const { fields: f, indexed: original } = linkIndex(before, columns);
  const { indexed: current } = linkIndex(after, columns);
  h.assert(current.size === original.size && [...original.keys()].every(id => current.has(id))
    && before.filter(row => row[f.noteId] === note && row[f.active] === '1').length === 1,
  'Native clearing changed sharing row identities or lacks its one active source link');
  for (const [id, row] of original) {
    const expected = row.slice();
    if (row[f.noteId] === note && row[f.active] === '1') {
      expected[f.active] = '0';
      // GroupNoteLink's @PreUpdate refreshes created when the active link is retired.
      const created = current.get(id)[f.created];
      h.assert(typeof created === 'string' && created !== '' && created !== 'NULL' && created !== '\\N',
        'Native clearing lost its retirement timestamp');
      expected[f.created] = created;
    }
    h.assert(same(current.get(id), expected), 'Native clearing changed a bystander or unexpected sharing field');
  }
}

async function workflow(s) {
  const { sql, provider, marker, config, recorder } = s;
  const fixture = authzReadFixture({ sql, provider, marker, testUser: config.testUser });
  let childrenRemoved=false;
  s.cleanup(() => {h.assert(childrenRemoved,'Retaining group authentication/facility because child cleanup failed');fixture.cleanup();});
  const patients = ['Owner', 'Recipient <b>患者</b>', 'OtherProgram', 'SecondRecipient'].map((first, index) => ({ first, name: `${marker}-GS${index}` }));
  const programs = ['A', 'B'].map(label => ({ name: `${marker}-group-select-${label}` }));
  const notes = [];
  const pages = [];
  let login, facility, context, defaultRows, stagedPolicy, defaultIntended=false;
  const doctor = sql.rows("SELECT role_no FROM secRole WHERE role_name='doctor'");
  h.assert(doctor.length === 1 && positive(doctor[0][0]), 'Group selection needs the existing cached doctor role');
  const shared = () => ({ role: sql.rows(`SELECT * FROM secRole WHERE role_no=${doctor[0][0]}`),
    defaults: sql.rows('SELECT * FROM default_role_access ORDER BY id'),
    mainDefault: sql.rows(`SELECT * FROM provider_default_program WHERE provider_no=${q(provider)} ORDER BY id`) });
  const sharedBefore = shared();
  const evidenceDirectory = fs.mkdtempSync('/tmp/carlos-group-selection-');
  fs.chmodSync(evidenceDirectory, 0o700);
  const evidence = { nativeFlow: [], refusals: [], cleanup: [] };
  const saveEvidence = () => fs.writeFileSync(`${evidenceDirectory}/proof.json`, JSON.stringify(evidence, null, 2) + '\n', { mode: 0o600 });
  saveEvidence(); console.log(`  Group selection evidence ${evidenceDirectory}/proof.json`);
  const ids = () => patients.filter(patient => patient.id).map(patient => patient.id);
  const programIds = () => programs.filter(program => program.id).map(program => program.id);
  const set = values => values.join(',') || '0';
  // Durable ownership intents precede every INSERT; every graph check precedes deletion.
  s.cleanup(() => {
    h.assert(!context||!context.browser()?.isConnected(),'Group fixture retained because browser shutdown is unconfirmed');
    for (const patient of patients) {
      const rows = sql.rows(`SELECT demographic_no FROM demographic WHERE last_name=${q(patient.name)}
        AND first_name=${q(patient.first)} AND provider_no=${q(provider)}`);
      h.assert(rows.length <= 1 && (!patient.id || rows.length === 1 && rows[0][0] === patient.id), 'Group selection patient ownership changed');
      patient.id ||= rows[0]?.[0];
    }
    for (const program of programs) if (facility) {
      const rows = sql.rows(`SELECT id FROM program WHERE name=${q(program.name)} AND facilityId=${facility.id}`);
      h.assert(rows.length <= 1 && (!program.id || rows.length === 1 && rows[0][0] === program.id), 'Group selection program ownership changed');
      program.id ||= rows[0]?.[0];
    }
    for (const note of notes) {
      const rows = sql.rows(`SELECT note_id,demographic_no,provider_no,note,program_no FROM casemgmt_note WHERE uuid=${q(note.uuid)}`);
      h.assert(rows.length <= 1 && rows.every(row => row[1] === patients[0].id && row[2] === login.providerNo
        && row[3] === note.body && row[4] === note.program.id && (!note.id || row[0] === note.id)),
      'Group selection tracked note moved or changed; retaining clinical rows');
      note.id ||= rows[0]?.[0];
    }
    const noteIds = notes.filter(note => note.id).map(note => note.id);
    const currentNotes = sql.rows(`SELECT note_id FROM casemgmt_note WHERE demographic_no IN (${set(ids())})`);
    h.assert(currentNotes.every(([id]) => noteIds.includes(id)), 'A group selection patient acquired an unowned note');
    const links = sql.rows(`SELECT id,noteId,demographicNo,anonymous FROM GroupNoteLink
      WHERE noteId IN (${set(noteIds)}) OR demographicNo IN (${set(ids())})`);
    h.assert(links.every(row => row[1] === notes[0]?.id && [patients[1].id, patients[3].id].includes(row[2]) && row[3] === '0'),
      'Group selection acquired a foreign sharing relationship; retaining all rows');
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no IN (${set(ids())})
      AND (provider_no IS NULL OR provider_no<>${q(login?.providerNo || '')} OR note_id NOT IN (0,${set(noteIds)})))
      +(SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no IN (${set(ids())})
      AND (provider_no IS NULL OR provider_no<>${q(login?.providerNo || '')}))`) === '0',
      'Another actor or note owns a group fixture draft/lock; retaining its rows');
    if (stagedPolicy) h.assert(same(stagedPolicy, {
      programs: sql.rows(`SELECT * FROM program WHERE id IN (${set(programIds())}) ORDER BY id`),
      memberships: sql.rows(`SELECT * FROM program_provider WHERE program_id IN (${set(programIds())}) ORDER BY id`),
      admissions: sql.rows(`SELECT * FROM admission WHERE client_id IN (${set(ids())}) ORDER BY am_id`),
    }), 'The exact staged group program/admission rows changed; retaining fixture');
    for (const patient of patients.filter(patient => patient.id)) {
      const state = readPatientState(sql, patient.id);
      const allowed = new Set(['demographic', 'demographicExtensions', 'notes', 'cpp', 'drafts', 'echart']);
      for (const [name, rows] of Object.entries(state)) h.assert(allowed.has(name) || rows.length === 0,
        `Unexpected ${name} in group selection fixture; retaining children`);
    }
    if (login) {
      const currentDefaults=sql.rows(`SELECT * FROM provider_default_program WHERE provider_no=${q(login.providerNo)} ORDER BY id`);
      h.assert(defaultIntended ? defaultRows && same(currentDefaults,defaultRows) : currentDefaults.length===0,
        'The exact staged group-selection default changed; retaining fixture');
      h.assert(sql.value(`SELECT COUNT(*) FROM program_provider WHERE program_id IN (${set(programIds())})
        AND (provider_no IS NULL OR provider_no<>${q(login.providerNo)})`) === '0', 'Group program acquired a foreign provider');
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM admission WHERE program_id IN (${set(programIds())})
      AND (client_id IS NULL OR client_id NOT IN (${set(ids())}))`) === '0'
      && sql.value(`SELECT COUNT(*) FROM admission WHERE client_id IN (${set(ids())})
      AND (program_id IS NULL OR program_id NOT IN (${set(programIds())}))`) === '0', 'Group selection acquired a foreign admission');
    h.assert(sql.value(`SELECT COUNT(*) FROM provider_default_program WHERE program_id IN (${set(programIds())})
      AND (provider_no IS NULL OR provider_no<>${q(login?.providerNo || "")})`) === '0', 'Group program acquired a foreign default');
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_issue_notes WHERE note_id IN (${set(noteIds)}))
      +(SELECT COUNT(*) FROM casemgmt_note_ext WHERE note_id IN (${set(noteIds)}))
      +(SELECT COUNT(*) FROM casemgmt_note_link WHERE note_id IN (${set(noteIds)}))`) === '0',
      'Group selection note acquired an unrelated issue relationship');
    h.assert(sql.value(`SELECT COUNT(*) FROM program_access WHERE program_id IN (${set(programIds())})`) === '0'
      && same(shared(), sharedBefore), 'Group selection changed shared access policy');
    sql.execute(`DELETE FROM GroupNoteLink WHERE id IN (${set(links.map(row => row[0]))});
      DELETE FROM hash_audit WHERE type='enc' AND id IN (${set(noteIds)});
      DELETE FROM casemgmt_issue_notes WHERE note_id IN (${set(noteIds)});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${set(noteIds)});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${set(noteIds)});
      DELETE FROM casemgmt_note WHERE note_id IN (${set(noteIds)});
      DELETE FROM casemgmt_note_lock WHERE demographic_no IN (${set(ids())});
      DELETE FROM casemgmt_tmpsave WHERE demographic_no IN (${set(ids())});
      DELETE FROM casemgmt_cpp WHERE demographic_no IN (${set(ids())});
      DELETE FROM eChart WHERE demographicNo IN (${set(ids())});
      DELETE FROM admission WHERE client_id IN (${set(ids())});
      DELETE FROM demographicExt WHERE demographic_no IN (${set(ids())});
      DELETE FROM demographicArchive WHERE demographic_no IN (${set(ids())});
      DELETE FROM log WHERE demographic_no IN (${set(ids())});
      ${login ? `DELETE FROM provider_default_program WHERE provider_no=${q(login.providerNo)};` : ''}
      DELETE FROM program_provider WHERE program_id IN (${set(programIds())});
      DELETE FROM program WHERE id IN (${set(programIds())})`);
    for (const patient of patients.filter(patient => patient.id)) {
      sql.execute(`DELETE FROM demographic WHERE demographic_no=${patient.id} AND last_name=${q(patient.name)} AND first_name=${q(patient.first)}`);
    }
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM demographic WHERE demographic_no IN (${set(ids())}))
      +(SELECT COUNT(*) FROM casemgmt_note WHERE note_id IN (${set(noteIds)}))
      +(SELECT COUNT(*) FROM GroupNoteLink WHERE noteId IN (${set(noteIds)}) OR demographicNo IN (${set(ids())}))
      +(SELECT COUNT(*) FROM admission WHERE client_id IN (${set(ids())}) OR program_id IN (${set(programIds())}))
      +(SELECT COUNT(*) FROM program WHERE id IN (${set(programIds())}))
      +(SELECT COUNT(*) FROM program_provider WHERE program_id IN (${set(programIds())}))
      +(SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no IN (${set(ids())}))
      +(SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no IN (${set(ids())}))
      +(SELECT COUNT(*) FROM casemgmt_cpp WHERE demographic_no IN (${set(ids())}))
      +(SELECT COUNT(*) FROM eChart WHERE demographicNo IN (${set(ids())}))
      +(SELECT COUNT(*) FROM casemgmt_note_ext WHERE note_id IN (${set(noteIds)}))
      +(SELECT COUNT(*) FROM casemgmt_note_link WHERE note_id IN (${set(noteIds)}))
      ${login ? `+(SELECT COUNT(*) FROM provider_default_program WHERE provider_no=${q(login.providerNo)})` : ''}`) === '0', 'Group selection fixture residue remains');
    childrenRemoved=true;
  });
  facility = fixture.addFacility('GS');
  sql.execute(`UPDATE Facility SET enableGroupNotes=1 WHERE id=${facility.id} AND name=${q(facility.name)} AND description=${q(marker)}`);
  login = fixture.addLogin('doctor'); fixture.joinFacility(login, facility);
  for (const patient of patients) {
    h.assert(patient.name.length <= 30 && sql.value(`SELECT COUNT(*) FROM demographic WHERE last_name=${q(patient.name)}`) === '0', 'Group patient marker collision');
    patient.id = h.insertId(sql, `INSERT INTO demographic(last_name,first_name,year_of_birth,month_of_birth,date_of_birth,
      sex,patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate) VALUES (${q(patient.name)},${q(patient.first)},
      '1980','01','02','F','AC',${q(provider)},'ON','ON','NR',NOW())`, 'group selection patient');
    const initialized = await s.context.request.get(h.appUrl(config.baseUrl, `/demographic/DemographicEdit?demographic_no=${patient.id}`));
    try { h.assert(initialized.status() === 200 && Object.hasOwn(initialized.headers(), h.APPLICATION_HEADER), 'Group patient initialization failed'); }
    finally { await initialized.dispose(); }
  }
  for (const program of programs) {
    h.assert(sql.value(`SELECT COUNT(*) FROM program WHERE name=${q(program.name)}`) === '0', 'Group program marker collision');
    // Valid nullable legacy values must survive the native reads and sharing writes.
    program.id = h.insertId(sql, `INSERT INTO program(facilityId,name,type,maxAllowed,programStatus,${LEGACY_PROGRAM_FIELDS.join(',')},transgender,firstNation,
      alcohol,physicalHealth,mentalHealth,housing,exclusiveView,ageMin,ageMax,enableEncounterTime,enableEncounterTransportationTime,
      enableOCAN) VALUES (${facility.id},${q(program.name)},'service',10,'active',${groupProgramLegacyValues().map(value => value === null ? 'NULL' : q(value)).join(',')},0,0,0,0,0,0,'none',0,150,0,0,0)`, 'group selection program');
    sql.execute(`INSERT INTO program_provider(program_id,provider_no,role_id,team_id) VALUES (${program.id},${q(login.providerNo)},${doctor[0][0]},0)`);
  }
  const rawProgramSeed = sql.rows(`SELECT id,JSON_ARRAY(${LEGACY_PROGRAM_FIELDS.join(',')}) FROM program
    WHERE id IN (${set(programIds())}) ORDER BY id`);
  assertOwnedGroupProgramLegacySeed(rawProgramSeed, programIds());
  evidence.rawProgramSeed = { columns: LEGACY_PROGRAM_FIELDS,
    programs: rawProgramSeed.map(([id, values]) => ({ id, values: JSON.parse(values) })) };
  h.assert(sql.value(`SELECT COUNT(*) FROM provider_default_program WHERE provider_no=${q(login.providerNo)}`) === '0', 'New group writer already has a default');
  defaultIntended=true;
  sql.execute(`INSERT INTO provider_default_program(provider_no,program_id) VALUES (${q(login.providerNo)},${programs[0].id})`);
  defaultRows = sql.rows(`SELECT * FROM provider_default_program WHERE provider_no=${q(login.providerNo)} ORDER BY id`);
  for (const [patient, program] of [[patients[0], programs[0]], [patients[0], programs[1]], [patients[1], programs[0]], [patients[2], programs[1]], [patients[3], programs[0]]]) {
    sql.execute(`INSERT INTO admission(client_id,program_id,provider_no,admission_date,admission_from_transfer,discharge_from_transfer,
      admission_status,lastUpdateDate) VALUES (${patient.id},${program.id},${q(login.providerNo)},NOW(),0,0,'current',NOW())`);
  }
  for (const program of programs) {
    const note = { program, uuid: randomUUID(), body: `${marker}-GROUP-SELECT-${program.id} café 中文\nexact source prose` };
    notes.push(note);
    note.id = h.insertId(sql, `INSERT INTO casemgmt_note(update_date,observation_date,demographic_no,provider_no,note,history,uuid,signed,
      signing_provider_no,locked,archived,include_issue_innote,position,program_no,reporter_caisi_role,reporter_program_team,appointmentNo,
      encounter_type) VALUES (NOW(),NOW(),${patients[0].id},${q(login.providerNo)},${q(note.body)},${q(note.body)},${q(note.uuid)},0,
      ${q(login.providerNo)},0,0,0,0,${q(program.id)},${q(doctor[0][0])},'0',0,'')`, 'group selection note');
  }
  stagedPolicy = {
    programs: sql.rows(`SELECT * FROM program WHERE id IN (${set(programIds())}) ORDER BY id`),
    memberships: sql.rows(`SELECT * FROM program_provider WHERE program_id IN (${set(programIds())}) ORDER BY id`),
    admissions: sql.rows(`SELECT * FROM admission WHERE client_id IN (${set(ids())}) ORDER BY am_id`),
  };
  evidence.stagedPolicy = writeStagedGroupPolicy(evidenceDirectory, stagedPolicy, {
    programs: sql.rows('SHOW COLUMNS FROM program').map(row => row[0]),
    memberships: sql.rows('SHOW COLUMNS FROM program_provider').map(row => row[0]),
    admissions: sql.rows('SHOW COLUMNS FROM admission').map(row => row[0]),
  }, { marker, provider: login.providerNo, facility: facility.id, programs: programIds(), patients: ids() });
  saveEvidence();
  context = await h.newContext(s.context.browser(), config);
  s.cleanup(() => context.close());
  context.on('page', page => h.wireStrictPage(page, 'group-note-selection', recorder, { baseline: [] }));
  await h.login(context, { ...config, testUser: login.username }, recorder, { baseline: [] });
  const clinical = () => ({ patients: patients.map(patient => readPatientState(sql, patient.id)),
    locks: sql.rows('SELECT id,session_id,ip_address,provider_no,note_id,demographic_no,lock_acquired FROM casemgmt_note_lock ORDER BY id'),
    programs: sql.rows(`SELECT * FROM program WHERE id IN (${set(programIds())}) ORDER BY id`),
    members: sql.rows(`SELECT * FROM program_provider WHERE program_id IN (${set(programIds())}) ORDER BY id`),
    admissions: sql.rows(`SELECT * FROM admission WHERE client_id IN (${set(ids())}) ORDER BY am_id`),
    defaults: sql.rows(`SELECT * FROM provider_default_program WHERE provider_no=${q(login.providerNo)} ORDER BY id`), shared: shared() });
  const linkColumns = sql.rows('SHOW COLUMNS FROM GroupNoteLink').map(row => row[0]);
  const linkRows = () => sql.rows(`SELECT * FROM GroupNoteLink WHERE noteId IN (${notes.map(note => note.id).join(',')}) ORDER BY id`);
  const open = async note => {
    const page = await context.newPage();pages.push(page);
    await h.gotoApp(page, config.baseUrl, `/encounter/IncomingEncounter?${new URLSearchParams({ demographicNo: patients[0].id,
      providerNo: login.providerNo, programId: note.program.id })}`);
    await waitForNavbars(page, 90000);
    const editor = page.locator(`#caseNote_note${note.id}`);
    if (!(await editor.isVisible())) {
      const answer = page.waitForResponse(response => response.request().method() === 'POST'
        && new URL(response.url()).pathname.endsWith('/CaseManagementEntry')
        && new URLSearchParams(response.request().postData() || '').get('method') === 'edit');
      await page.locator(`#edit${note.id}`).click();
      const response = await answer;await response.finished();
      h.assert(response.status() === 200 && Object.hasOwn(response.headers(),h.APPLICATION_HEADER), 'Native group note Edit failed');
    }
    await editor.waitFor({state:'visible'});
    h.assert(await page.locator('#caseManagementEntryForm input[name="noteId"]').inputValue() === note.id, 'Native group editor selected another note');
    await page.waitForFunction(() => !window.autoSaveXhr);
    await page.evaluate(() => { if(typeof clearAutoSaveTimer === 'function')clearAutoSaveTimer(); });
    return page;
  };
  const popup = chart => openSelectionPopup(context, chart, config);
  let primary;
  let token;
  let csrf;
  let primaryFailure;
  try {
    await s.step('the native unsaved group control retains its save-first guidance without persistence',async()=>{
      primary=await open(notes[0]);
      const edit=primary.waitForResponse(response=>response.request().method()==='POST'&&new URL(response.url()).pathname.endsWith('/CaseManagementEntry')
        &&new URLSearchParams(response.request().postData()||'').get('method')==='edit');
      await primary.locator('#newNoteImg').click();const response=await edit;await response.finished();
      h.assert(response.status()===200&&Object.hasOwn(response.headers(),h.APPLICATION_HEADER),'Native New Note did not open its unsaved editor');
      await primary.waitForFunction(()=>document.forms.caseManagementEntryForm.elements.noteId.value==='0'&&!window.autoSaveXhr);
      await primary.evaluate(()=>clearAutoSaveTimer());
      const before=clinical(),links=linkRows();const selected=await popup(primary);
      h.assert((await selected.locator('body').innerText()).includes('you must first save this note')&&await selected.locator('form').count()===0,
        'The unsaved Group control lost its native save-first guidance');
      await selected.locator('input[value="Close Window"]').click();
      h.assert(same(clinical(),before)&&same(linkRows(),links),'The unsaved group popup mutated clinical, draft, lock or sharing state');
      const reopen=primary.waitForResponse(answer=>answer.request().method()==='POST'&&new URL(answer.url()).pathname.endsWith('/CaseManagementEntry')
        &&new URLSearchParams(answer.request().postData()||'').get('method')==='edit');
      await primary.locator(`#edit${notes[0].id}`).click();const reopened=await reopen;await reopened.finished();
      h.assert(reopened.status()===200&&Object.hasOwn(reopened.headers(),h.APPLICATION_HEADER),'The saved group note could not be reopened');
      await primary.locator(`#caseNote_note${notes[0].id}`).waitFor({state:'visible'});
      await primary.waitForFunction(()=>!window.autoSaveXhr);await primary.evaluate(()=>clearAutoSaveTimer());
      evidence.nativeFlow.push({unsavedGuidance:true,applicationStatus:200});saveEvidence();
    });
    await s.step('native group selection retains A while the same patient program B was opened last', async () => {
      await open(notes[1]);
      token = await primary.locator('#caseManagementEntryForm input[name="chartContext"]').inputValue();
      csrf = await primary.evaluate(() => CarlosAjax.getCsrfToken());
      const before = clinical();const beforeLinks = linkRows();
      const selected = await popup(primary);
      fs.writeFileSync(`${evidenceDirectory}/selection.html`,await selected.content(),{mode:0o600});
      const form = selected.locator('form[action$="/casemgmt/SaveGroupNoteSelection"]');
      h.assert(await form.locator('input[name="chartContext"]').inputValue() === token
        && await form.locator('input[name="programId"]').inputValue() === programs[0].id
        && await form.locator('input[name="noteId"]').inputValue() === notes[0].id,
      'Native selection rendered another program, note or signed context');
      h.assert(same((await selected.locator('input[name="group_client_id"]').evaluateAll(nodes => nodes.map(node=>node.value))).sort(),[patients[1].id,patients[3].id].sort())
        && (await selected.locator('body').innerText()).includes(patients[1].first) && await selected.locator('b').count() === 0,
      'Selection exposed another program recipient or failed to encode a literal patient name');
      await selected.locator(`input[name="group_client_id"][value="${patients[1].id}"]`).check();
      const save = selected.waitForResponse(response => response.request().method() === 'POST'
        && new URL(response.url()).pathname.endsWith('/casemgmt/SaveGroupNoteSelection'));
      const redirected = selected.waitForResponse(response => response.request().method() === 'GET'
        && new URL(response.url()).pathname.endsWith('/casemgmt/ViewGroupNoteSelectAction'));
      const closed = selected.waitForEvent('close');
      await selected.locator('input[type="submit"]').click();
      const result = await save;assertSelectionRequest(result.request(), {baseUrl:config.baseUrl,patient:patients[0].id,
        program:programs[0].id,note:notes[0].id,token,recipient:patients[1].id});
      assertSelectionRedirect(result,{baseUrl:config.baseUrl,patient:patients[0].id,program:programs[0].id,token});
      const target = await redirected;h.assert(target.status()===200&&Object.hasOwn(target.headers(),h.APPLICATION_HEADER), 'Redirected group close GET failed');
      await closed;
      h.assert(same(clinical(),before) && beforeLinks.length===0, 'Group selection changed clinical, draft, lock or policy state');
      h.assert(sql.value(`SELECT COUNT(*) FROM GroupNoteLink WHERE noteId=${notes[0].id} AND demographicNo=${patients[1].id} AND active=1 AND anonymous=0`)==='1'
        && sql.value(`SELECT COUNT(*) FROM GroupNoteLink WHERE noteId=${notes[1].id}`)==='0','Native group selection linked another note or patient');
      evidence.nativeFlow.push({saved:true,post:303,redirectGet:200,patient:patients[0].id,note:notes[0].id,program:programs[0].id});saveEvidence();
      h.assertStrictPage(recorder);
    });
    await s.step('invalid recipients, contexts and non-POST saves preserve all clinical and group state', async () => {
      const base={demographicNo:patients[0].id,programId:programs[0].id,noteId:notes[0].id,chartContext:token,num_anonymous:'0','CSRF-TOKEN':csrf};
      for(const probe of [{method:'GET',status:405},{method:'HEAD',status:405},{method:'PUT',status:405},
        {method:'POST',status:403,extra:{group_client_id:patients[2].id}},
        {method:'POST',status:403,extra:{demographicNo:patients[1].id}},
        {method:'POST',status:400,extra:{num_anonymous:'-1'}}]) {
        const before=clinical(), links=linkRows();
        const { address, options } = groupSelectionProbeOptions(config.baseUrl, base, probe);
        const response=await context.request.fetch(address.href,options);
        try {const bytes=await response.body(),body=bytes.toString('utf8');
          const bodyFile=`refusal-${probe.method}-${evidence.refusals.length+1}.body`;
          fs.writeFileSync(path.join(evidenceDirectory,bodyFile),bytes,{flag:'wx',mode:0o600});
          evidence.refusals.push({method:probe.method,expectedStatus:probe.status,status:response.status(),
            applicationOrigin:Object.hasOwn(response.headers(),h.APPLICATION_HEADER),wafResponse:h.isWafPage(response.status(),body),
            allow:response.headers().allow||null,contentType:response.headers()['content-type']||null,
            bodyFile,bodyBytes:bytes.length,bodySha256:createHash('sha256').update(bytes).digest('hex')});saveEvidence();
          h.assert(response.status()===probe.status&&Object.hasOwn(response.headers(),h.APPLICATION_HEADER)
          && !h.isWafPage(response.status(),body)&&notes.every(note=>!body.includes(note.body)),
          `The group ${probe.method} refusal returned HTTP ${response.status()}, expected application ${probe.status}`);
          if(probe.status===405)h.assert(response.headers().allow==='POST','Group save did not retain its POST-only contract');}
        finally {await response.dispose();}
        h.assert(same(clinical(),before)&&same(linkRows(),links),'A group refusal mutated clinical, draft, lock, policy or sharing state');
      }
    });
    await s.step('an empty native group selection clears only its previous sharing link', async () => {
      const before=clinical();const selected=await popup(primary);
      const beforeLinks=linkRows();h.assert(beforeLinks.length===1,'The owned group link set changed before clearing');
      // Existing strict dialog policy accepts this native confirmation; assert its exact count/content afterwards.
      const messages=await h.withExpectedDialogs(selected,async()=>{
      const save=selected.waitForResponse(response=>response.request().method()==='POST'&&new URL(response.url()).pathname.endsWith('/casemgmt/SaveGroupNoteSelection'));
      const target=selected.waitForResponse(response=>response.request().method()==='GET'&&new URL(response.url()).pathname.endsWith('/casemgmt/ViewGroupNoteSelectAction'));
      const close=selected.waitForEvent('close');
      await selected.locator('input[type="submit"]').click();const result=await save;
      assertSelectionRequest(result.request(),{baseUrl:config.baseUrl,patient:patients[0].id,program:programs[0].id,note:notes[0].id,token});
      assertSelectionRedirect(result,{baseUrl:config.baseUrl,patient:patients[0].id,program:programs[0].id,token});
      h.assert((await target).status()===200,'The group clear close GET failed');await close;
      });
      h.assert(messages.length===1&&messages[0].type==='confirm'&&messages[0].text.startsWith('This will cause all previously associated clients'),'Group clearing lost its native confirmation');
      h.assert(same(clinical(),before)&&sql.value(`SELECT COUNT(*) FROM GroupNoteLink WHERE noteId=${notes[0].id} AND active=1`)==='0'
        &&sql.value(`SELECT COUNT(*) FROM GroupNoteLink WHERE noteId=${notes[1].id}`)==='0','Clearing group selection changed another note or clinical state');
      assertClearedSelectionDelta(beforeLinks,linkRows(),{columns:linkColumns,note:notes[0].id});
      evidence.nativeFlow.push({cleared:true,post:303,redirectGet:200});saveEvidence();
    });
    await s.step('two native group popups in one context each replace the complete recipient set, then clear natively', async () => {
      const before = clinical(), beforeLinks = linkRows();
      const first = await popup(primary);
      const topology = await first.evaluate(name => {
        const form = document.querySelector('form');
        const before = { url: location.href, fields: [...new FormData(form)] };
        const originalName = window.name;
        // Fixture topology only: the native control otherwise reuses its fixed "group" window.
        window.name = name;
        return { originalName, renamed: window.name === name,
          unchanged: JSON.stringify(before) === JSON.stringify({ url: location.href, fields: [...new FormData(form)] }) };
      }, `${marker}-parallel-group-first`);
      h.assert(topology.renamed && topology.unchanged, 'Renaming the first fixture popup changed its URL or business form');
      const second = await popup(primary);
      h.assert(first !== second && await first.opener() === primary && await second.opener() === primary,
        'The parallel native Group clicks did not create two distinct owned popup windows');
      const choices = [patients[1].id, patients[3].id];
      for (const [index, page] of [first, second].entries()) {
        h.assert(same((await page.locator('input[name="group_client_id"]').evaluateAll(nodes => nodes.map(node => node.value))).sort(), [...choices].sort()),
          'A parallel popup exposed a recipient outside the captured program');
        await page.locator(`input[name="group_client_id"][value="${choices[index]}"]`).check();
        fs.writeFileSync(`${evidenceDirectory}/parallel-${index}.html`, await page.content(), { mode: 0o600 });
      }
      const submit = async (page, recipient) => {
        let proof;
        const dialogs = await h.withExpectedDialogs(page, async () => {
          const save = page.waitForResponse(response => response.request().method() === 'POST'
            && new URL(response.url()).pathname.endsWith('/casemgmt/SaveGroupNoteSelection'));
          const target = page.waitForResponse(response => response.request().method() === 'GET'
            && new URL(response.url()).pathname.endsWith('/casemgmt/ViewGroupNoteSelectAction'));
          const closed = page.waitForEvent('close');
          await page.locator('input[type="submit"]').click();
          const response = await save;
          assertSelectionRequest(response.request(), { baseUrl: config.baseUrl, patient: patients[0].id,
            program: programs[0].id, note: notes[0].id, token, recipient });
          assertSelectionRedirect(response, { baseUrl: config.baseUrl, patient: patients[0].id, program: programs[0].id, token });
          const get = await target;
          h.assert(get.status() === 200 && Object.hasOwn(get.headers(), h.APPLICATION_HEADER), 'A parallel group close GET failed');
          await closed; proof = { recipient, actualPost: response.status(), closeGet: get.status() };
        });
        h.assert(dialogs.length === 0, 'An initially empty parallel popup unexpectedly requested confirmation');
        return proof;
      };
      // Parallel submission is an end-to-end check, not a claim of deterministic server overlap.
      const posts = await Promise.all([submit(first, choices[0]), submit(second, choices[1])]);
      const afterLinks = linkRows();
      const active = assertParallelSelectionDelta(beforeLinks, afterLinks, { columns: linkColumns, note: notes[0].id, recipients: choices });
      h.assert(same(clinical(), before), 'Parallel group posts changed source/bystander clinical, draft, lock or policy rows');
      evidence.nativeFlow.push({ parallelNativePosts: posts, fixtureWindowNameOnly: topology, serverOverlapClaimed: false,
        beforeLinks, afterLinks, activeRecipients: active }); saveEvidence();
      const clear = await popup(primary);
      const beforeClear = clinical(), linksBeforeClear = linkRows();
      const dialogs = await h.withExpectedDialogs(clear, async () => {
        const save = clear.waitForResponse(response => response.request().method() === 'POST'
          && new URL(response.url()).pathname.endsWith('/casemgmt/SaveGroupNoteSelection'));
        const target = clear.waitForResponse(response => response.request().method() === 'GET'
          && new URL(response.url()).pathname.endsWith('/casemgmt/ViewGroupNoteSelectAction'));
        const closed = clear.waitForEvent('close');
        await clear.locator('input[type="submit"]').click(); const response = await save;
        assertSelectionRequest(response.request(), { baseUrl: config.baseUrl, patient: patients[0].id, program: programs[0].id, note: notes[0].id, token });
        assertSelectionRedirect(response, { baseUrl: config.baseUrl, patient: patients[0].id, program: programs[0].id, token });
        const get = await target; h.assert(get.status() === 200 && Object.hasOwn(get.headers(), h.APPLICATION_HEADER), 'Parallel group cleanup close GET failed');
        await closed;
      });
      h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm'
        && dialogs[0].text.startsWith('This will cause all previously associated clients'), 'Parallel group clearing lost its native confirmation');
      assertClearedSelectionDelta(linksBeforeClear, linkRows(), { columns: linkColumns, note: notes[0].id });
      h.assert(same(clinical(), beforeClear), 'Clearing the parallel result changed clinical/draft/lock/policy state');
      evidence.nativeFlow.push({ parallelResultClearedNatively: true }); saveEvidence(); h.assertStrictPage(recorder);
    });
  } catch(error) {primaryFailure=error;} finally {
    const cleanupFailures=[];
    let cookies=[],sessions=new Set();
    try {cookies=(await context.cookies(config.baseUrl.href)).filter(cookie=>cookie.name==='JSESSIONID');
      h.assert(cookies.length===1&&cookies[0].value,'Group cleanup lost its unique authenticated session');
      sessions=new Set([cookies[0].value,cookies[0].value.split('.')[0]]);
    } catch(error){cleanupFailures.push(error);}
    for(const page of pages)if(!page.isClosed()&&sessions.size) {
      try {
      const form=await page.evaluate(()=>({patient:String(demographicNo),provider:String(providerNo),note:String(document.forms.caseManagementEntryForm.elements.noteId.value),
        token:document.forms.caseManagementEntryForm.elements.chartContext.value,csrf:CarlosAjax.getCsrfToken()}));
      h.assert(form.patient===patients[0].id&&form.provider===login.providerNo&&(form.note==='0'||notes.some(note=>note.id===form.note))&&form.csrf,'Group cleanup lost its owned form identity');
      const before=clinical();
      const own=before.locks.filter(row=>row[3]===form.provider&&row[4]===form.note&&row[5]===form.patient&&sessions.has(row[1]));
      h.assert(own.length<=1&&!before.locks.some(row=>row[3]===form.provider&&row[4]===form.note&&row[5]===form.patient
        &&row[1].startsWith(cookies[0].value.split('.')[0]+'.')&&!sessions.has(row[1])),'Group cleanup has ambiguous or foreign-session lock ownership');
      const response=await context.request.post(h.appUrl(config.baseUrl,'/CaseManagementEntry'),{form:{method:'releaseNoteLock',
        demographicNo:form.patient,providerNo:form.provider,noteId:form.note,chartContext:form.token,'CSRF-TOKEN':form.csrf},maxRedirects:0});
      try {h.assert(response.status()===200&&Object.hasOwn(response.headers(),h.APPLICATION_HEADER),'Group cleanup lock release was not acknowledged');}finally{await response.dispose();}
      const after=clinical();h.assert(same({...before,locks:[]},{...after,locks:[]})&&same(after.locks,before.locks.filter(row=>!own.includes(row))), 'Group cleanup changed clinical or added locks');
      await page.evaluate(expected=>{const form=document.forms.caseManagementEntryForm;if(String(demographicNo)!==expected.patient||String(providerNo)!==expected.provider||String(form.elements.noteId.value)!==expected.note||form.elements.chartContext.value!==expected.token)throw Error('Group cleanup form changed');needToReleaseLock=false;},form);
      await page.close();evidence.cleanup.push({patient:form.patient,note:form.note,status:200,removedLockIds:own.map(row=>row[0])});saveEvidence();
      } catch(error) {cleanupFailures.push(error);}
    }
    if(cleanupFailures.length){if(primaryFailure)primaryFailure.message+='; owned group cleanup: '+cleanupFailures.map(error=>error.message).join('; ');
      else primaryFailure=cleanupFailures[0];}
  }
  if(primaryFailure)throw primaryFailure;
  h.assertStrictPage(recorder);
}
module.exports={workflow,assertSelectionRequest,assertSelectionRedirect,assertParallelSelectionDelta,assertClearedSelectionDelta,
  assertSelectionPopupResponse,openSelectionPopup,writeStagedGroupPolicy,groupSelectionProbeOptions,
  groupProgramLegacyValues,assertOwnedGroupProgramLegacySeed};
if(require.main===module)runWorkflow('group-note-selection',workflow,{openPatient:false,openMaster:false});
