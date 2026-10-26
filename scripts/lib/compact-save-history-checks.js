/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const { randomUUID, createHash } = require('node:crypto');
const fs = require('node:fs');
const h = require('./playwright-harness');
const { signIn } = require('./authz-read-probe');
const { waitForNavbars } = require('../echart-navbar-modules-playwright-checks');
const { readPatientState } = require('../echart-two-patient-isolation-playwright-checks');
const { waitUntil } = require('./concurrency-support');
const q = h.sqlString;
const positive = value => /^[1-9]\d*$/.test(String(value));
const NOTE = '#caseManagementEntryForm textarea[name="caseNote_note"]';
const LOCK_COLUMNS = 'id,provider_no,demographic_no,note_id,session_id,lock_acquired,ip_address';

function assertCompactIdentity(request, { baseUrl, patient, provider, note, token }) {
  const actual = new URL(request.url());
  const expected = new URL(h.appUrl(baseUrl, '/CaseManagementEntry'));
  const body = new URLSearchParams(request.postData() || '');
  h.assert(request.method() === 'POST' && actual.origin === expected.origin && actual.pathname === expected.pathname,
    'The compact-save witness is not the native application POST');
  for (const [key, value] of Object.entries({ method: 'ajaxsave', nId: note, demographicNo: patient, providerNo: provider })) {
    h.assert(body.getAll(key).length > 0 && body.getAll(key).every(item => item === String(value)),
      `The native compact save changed its ${key}`);
  }
  const contexts = [actual.searchParams, body].flatMap(parameters => parameters.getAll('chartContext'));
  h.assert(contexts.length > 0 && contexts.every(value => value === token) && body.get('noteTxt'),
    'The native compact save lost its text or originating chart context');
  return body;
}

/** Exactly one persisted lock advances; the second editor's complete row stays unchanged. */
function assertSelectedLockAdvance(before, after, { selectedId, otherId, revision }) {
  const selected = before.find(row => row[0] === selectedId);
  const other = before.find(row => row[0] === otherId);
  h.assert(selected && other && selectedId !== otherId && positive(revision), 'Two distinct owned editor locks are required');
  const expected = before.map(row => row[0] === selectedId ? row.map((value, index) => index === 3 ? String(revision) : value) : row);
  h.assert(JSON.stringify(after) === JSON.stringify(expected), 'Compact save advanced another editor lock or changed its metadata');
}

function assertNativeRelease(request, { baseUrl, patient, provider, note, token }) {
  const url = new URL(request.url());
  const target = new URL(h.appUrl(baseUrl, '/CaseManagementEntry'));
  const body = new URLSearchParams(request.postData() || '');
  h.assert(request.method() === 'POST' && url.origin === target.origin && url.pathname === target.pathname,
    'Native lock retirement is not the application POST');
  for (const [key, value] of Object.entries({ method: 'releaseNoteLock', demographicNo: patient, providerNo: provider, noteId: note })) {
    h.assert(body.getAll(key).length > 0 && body.getAll(key).every(item => item === String(value)),
      `Native lock retirement changed its ${key}`);
  }
  const contexts = [url.searchParams, body].flatMap(parameters => parameters.getAll('chartContext'));
  h.assert(contexts.length > 0 && contexts.every(context => context === token), 'Native lock retirement changed its chart context');
}

function assertIssueHistory(result, { status, allowed = [], forbidden = [] }) {
  h.assert(result.status === status && Object.hasOwn(result.headers, h.APPLICATION_HEADER)
    && !h.isWafPage(result.status, result.html), 'Issue history did not return its expected application response');
  h.assert(allowed.every(text => result.text.includes(text))
    && forbidden.every(text => !result.html.includes(text) && !result.text.includes(text)),
  'Issue history omitted readable text or exposed a forbidden note');
  if (status === 200) h.assert(/text\/html/i.test(result.headers['content-type'] || ''), 'Issue history is not HTML');
}

/** Saves may add their own revisions/draft cleanup/legacy chart text, never another chart domain. */
function assertProtectedEditorState(before, after) {
  const protectedState = snapshot => {
    h.assert(snapshot.patients?.length === 2, 'The two-patient clinical snapshot is incomplete');
    const { notes, drafts, echart, ...active } = snapshot.patients[0];
    const { locks, patients, ...metadata } = snapshot;
    return { patients: [active, snapshot.patients[1]], ...metadata };
  };
  h.assert(JSON.stringify(protectedState(after)) === JSON.stringify(protectedState(before)),
    'An editor save changed another clinical domain, patient, draft or program policy');
}

/** Independent owned charts exercise the installed compact-lock and issue-history corrections. */
async function checkCompactSaveAndIssueHistory(s, { fixture }) {
  const { sql, marker, provider, config } = s;
  const patients = ['Editor', 'RecordControl'].map((kind, index) => ({ kind, name: `${marker}-CH${index}`, id: null }));
  const programs = [];
  const notes = [];
  const issues = [];
  const logins = [];
  const charts = [];
  let facility;
  let writer;
  let recordLogin;
  let reader;
  let fixtureFailure;
  const names = ['A', 'B'].map(label => `${marker}-compact-history-${label}`);
  const rolePair = sql.rows(`SELECT r.role_no,h.role_no FROM secRole r CROSS JOIN secRole h
    WHERE r.role_no<>h.role_no AND r.role_name NOT LIKE 'FAKE%' AND h.role_name NOT LIKE 'FAKE%'
      AND r.role_name=TRIM(r.role_name) AND h.role_name=TRIM(h.role_name)
      AND r.role_name<>'' AND h.role_name<>''
      AND NOT EXISTS (SELECT 1 FROM default_role_access d JOIN access_type a ON a.access_id=d.access_id
        WHERE d.role_id=r.role_no AND a.name=CONCAT('read ',LOWER(h.role_name),' notes'))
    ORDER BY r.role_no,h.role_no LIMIT 1`)[0];
  h.assert(rolePair && rolePair.every(positive), 'Issue history needs two existing cached roles with a real read distinction');
  const policy = () => ({ roles: sql.rows(`SELECT * FROM secRole WHERE role_no IN (${rolePair.join(',')}) ORDER BY role_no`),
    defaults: sql.rows('SELECT * FROM default_role_access ORDER BY id'),
    mainDefault: sql.rows(`SELECT * FROM provider_default_program WHERE provider_no=${q(provider)} ORDER BY program_id`) });
  const sharedPolicy = JSON.stringify(policy());
  const artifacts = fs.mkdtempSync('/tmp/carlos-compact-history-');
  let artifactIndex = 0;
  const preserve = (label, status, body, metadata = {}) => {
    const name = `${artifacts}/${++artifactIndex}`;
    fs.writeFileSync(`${name}.html`, body, { mode: 0o600 });
    fs.writeFileSync(`${name}.json`, JSON.stringify({ label, status, bytes: Buffer.byteLength(body),
      sha256: createHash('sha256').update(body).digest('hex'), ...metadata }, null, 2) + '\n', { mode: 0o600 });
    console.log(`  Compact/history evidence ${name}.json`);
  };
  const patientIds = () => patients.filter(patient => patient.id).map(patient => patient.id);
  const lockRows = () => sql.rows(`SELECT ${LOCK_COLUMNS} FROM casemgmt_note_lock
    WHERE demographic_no IN (${patientIds().join(',')}) ORDER BY id`);
  const state = () => ({ patients: patients.map(patient => readPatientState(sql, patient.id)), locks: lockRows(),
    programs: sql.rows(`SELECT * FROM program WHERE id IN (${programs.join(',')}) ORDER BY id`),
    memberships: sql.rows(`SELECT * FROM program_provider WHERE program_id IN (${programs.join(',')}) ORDER BY id`),
    admissions: sql.rows(`SELECT * FROM admission WHERE client_id IN (${patientIds().join(',')}) ORDER BY am_id`),
    defaults: sql.rows(`SELECT * FROM provider_default_program WHERE provider_no IN (${logins.map(login => q(login.providerNo)).join(',')}) ORDER BY provider_no`),
    policy: policy() });
  const unchanged = (before, label) => h.assert(JSON.stringify(state()) === before, `${label}: clinical, draft, lock or policy state changed`);

  // Recover lost IDs by unique names/UUIDs; validate every relationship before deletion.
  s.cleanup(() => {
    for (const patient of patients) {
      const rows = sql.rows(`SELECT demographic_no FROM demographic WHERE last_name=${q(patient.name)}
        AND first_name=${q(patient.kind)} AND provider_no=${q(provider)}`);
      h.assert(rows.length <= 1 && (!patient.id || rows.length === 1 && rows[0][0] === patient.id),
        'Compact/history patient ownership changed; retaining fixtures');
      patient.id ||= rows[0]?.[0];
    }
    if (facility) {
      const rows = sql.rows(`SELECT id,name FROM program WHERE name IN (${names.map(q).join(',')}) AND facilityId=${facility.id}`);
      h.assert(new Set(rows.map(row => row[1])).size === rows.length, 'Compact/history program recovery is ambiguous');
      for (const [id] of rows) if (!programs.includes(id)) programs.push(id);
    }
    const ids = patientIds();
    const patientsSql = ids.join(',') || 'SELECT demographic_no FROM demographic WHERE 1=0';
    const stored = sql.rows(`SELECT note_id,demographic_no,provider_no,uuid,note,program_no FROM casemgmt_note
      WHERE demographic_no IN (${patientsSql})`);
    h.assert(stored.every(row => notes.some(note => note.uuid === row[3] && note.patient.id === row[1]
      && note.author === row[2] && note.texts.has(row[4]) && note.program === row[5])),
    'Unexpected compact/history note ownership or text; retaining clinical fixtures');
    for (const note of notes) h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE uuid=${q(note.uuid)}
      AND (demographic_no<>${note.patient.id} OR provider_no<>${q(note.author)})`) === '0',
    'A tracked compact/history UUID moved to another owner');
    const noteIds = stored.map(row => row[0]);
    const noteSet = noteIds.join(',') || 'SELECT note_id FROM casemgmt_note WHERE 1=0';
    const caseRows = sql.rows(`SELECT id,demographic_no,issue_id FROM casemgmt_issue WHERE demographic_no IN (${patientsSql})`);
    h.assert(caseRows.every(row => issues.some(issue => issue.patient.id === row[1] && issue.catalog === row[2]
      && (!issue.id || issue.id === row[0]))), 'Unexpected compact/history case issue; retaining its relationships');
    const issueSet = caseRows.map(row => row[0]).join(',') || 'SELECT id FROM casemgmt_issue WHERE 1=0';
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_issue_notes WHERE id IN (${issueSet}) AND note_id NOT IN (${noteSet}))
      +(SELECT COUNT(*) FROM casemgmt_issue_notes WHERE note_id IN (${noteSet}) AND id NOT IN (${issueSet}))
      +(SELECT COUNT(*) FROM GroupNoteLink WHERE noteId IN (${noteSet}) OR demographicNo IN (${patientsSql}))
      +(SELECT COUNT(*) FROM casemgmt_note_ext WHERE note_id IN (${noteSet}))
      +(SELECT COUNT(*) FROM casemgmt_note_link WHERE note_id IN (${noteSet}))`) === '0',
    'Compact/history fixtures acquired foreign or unexpected clinical relationships');
    const providers = logins.map(login => q(login.providerNo)).join(',') || "''";
    for (const program of programs) h.assert(facility && sql.value(`SELECT COUNT(*) FROM program WHERE id=${program}
      AND name IN (${names.map(q).join(',')}) AND facilityId=${facility.id}`) === '1'
      && sql.value(`SELECT (SELECT COUNT(*) FROM program_provider WHERE program_id=${program} AND (provider_no IS NULL OR provider_no NOT IN (${providers})))
        +(SELECT COUNT(*) FROM provider_default_program WHERE program_id=${program} AND (provider_no IS NULL OR provider_no NOT IN (${providers})))
        +(SELECT COUNT(*) FROM admission WHERE program_id=${program} AND (client_id IS NULL OR client_id NOT IN (${patientsSql})))
        +(SELECT COUNT(*) FROM program_access WHERE program_id=${program})`) === '0',
    'Compact/history program acquired foreign membership or policy');
    for (const patient of patients.filter(patient => patient.id)) {
      const clinical = readPatientState(sql, patient.id);
      const allowed = new Set(['demographic', 'demographicExtensions', 'notes', 'noteIssues', 'issues', 'signatures', 'echart', 'drafts']);
      for (const [domain, rows] of Object.entries(clinical)) h.assert(allowed.has(domain) || rows.length === 0,
        `Unexpected ${domain} on compact/history patient; retaining children`);
    }
    for (const login of logins) h.assert(sql.value(`SELECT COUNT(*) FROM provider_default_program WHERE provider_no=${q(login.providerNo)}
      AND (program_id IS NULL OR program_id NOT IN (${programs.join(',') || '0'}))`) === '0', 'Compact/history reader default changed');
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no IN (${patientsSql}) AND (provider_no IS NULL OR provider_no NOT IN (${providers})))
      +(SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no IN (${patientsSql}) AND (provider_no IS NULL OR provider_no NOT IN (${providers})))
      +(SELECT COUNT(*) FROM admission WHERE client_id IN (${patientsSql}) AND (program_id IS NULL OR program_id NOT IN (${programs.join(',') || '0'})))`) === '0'
      && JSON.stringify(policy()) === sharedPolicy, 'Compact/history foreign draft/lock/admission or policy drift');
    sql.execute(`DELETE FROM casemgmt_issue_notes WHERE id IN (${issueSet});
      DELETE FROM hash_audit WHERE type='enc' AND id IN (${noteSet});
      DELETE FROM casemgmt_note WHERE demographic_no IN (${patientsSql});
      DELETE FROM casemgmt_issue WHERE demographic_no IN (${patientsSql});
      DELETE FROM casemgmt_tmpsave WHERE demographic_no IN (${patientsSql});
      DELETE FROM casemgmt_note_lock WHERE demographic_no IN (${patientsSql});
      DELETE FROM eChart WHERE demographicNo IN (${patientsSql});
      DELETE FROM admission WHERE client_id IN (${patientsSql});
      DELETE FROM log WHERE demographic_no IN (${patientsSql});
      DELETE FROM demographicExt WHERE demographic_no IN (${patientsSql});
      DELETE FROM demographicArchive WHERE demographic_no IN (${patientsSql})`);
    for (const patient of patients.filter(patient => patient.id)) sql.execute(`DELETE FROM demographic WHERE demographic_no=${patient.id}
      AND last_name=${q(patient.name)} AND provider_no=${q(provider)}`);
    for (const login of logins) {
      sql.execute(`DELETE FROM provider_default_program WHERE provider_no=${q(login.providerNo)} AND program_id IN (${programs.join(',') || '0'})`);
    }
    for (const program of programs) sql.execute(`DELETE FROM program_provider WHERE program_id=${program};
      DELETE FROM program WHERE id=${program} AND name IN (${names.map(q).join(',')})`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM demographic WHERE demographic_no IN (${patientsSql}))
      +(SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no IN (${patientsSql}))
      +(SELECT COUNT(*) FROM casemgmt_issue WHERE demographic_no IN (${patientsSql}))
      +(SELECT COUNT(*) FROM casemgmt_issue_notes WHERE note_id IN (${noteSet}) OR id IN (${issueSet}))
      +(SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no IN (${patientsSql}))
      +(SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no IN (${patientsSql}))
      +(SELECT COUNT(*) FROM program WHERE id IN (${programs.join(',') || '0'}))`) === '0', 'Compact/history fixture residue remains');
  });
  facility = fixture.addFacility('CH');
  for (const patient of patients) patient.id = h.insertId(sql, `INSERT INTO demographic
    (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES (${q(patient.name)},${q(patient.kind)},'1980','01','02','F','AC',${q(provider)},'ON','ON','NR',NOW())`, 'compact/history patient');
  writer = fixture.addLogin('doctor'); logins.push(writer); fixture.joinFacility(writer, facility);
  recordLogin = fixture.addLogin('doctor'); logins.push(recordLogin); fixture.joinFacility(recordLogin, facility);
  for (let index = 0; index < 2; index++) programs.push(h.insertId(sql, `INSERT INTO program
    (facilityId,name,type,maxAllowed,programStatus,transgender,firstNation,alcohol,physicalHealth,mentalHealth,housing,
      exclusiveView,ageMin,ageMax,enableEncounterTime,enableEncounterTransportationTime,enableOCAN)
    VALUES (${facility.id},${q(names[index])},'service',1,'active',0,0,0,0,0,0,'none',0,150,0,0,0)`, 'compact/history program'));
  for (const login of logins) {
    h.assert(sql.value(`SELECT COUNT(*) FROM provider_default_program WHERE provider_no=${q(login.providerNo)}`) === '0',
      'The unused compact/history login has a default');
    for (let index = 0; index < 2; index++) sql.execute(`INSERT INTO program_provider(program_id,provider_no,role_id,team_id)
      VALUES (${programs[index]},${q(login.providerNo)},${rolePair[index]},0)`);
    sql.execute(`INSERT INTO provider_default_program(provider_no,program_id,signnote) VALUES (${q(login.providerNo)},${programs[0]},0)`);
  }
  for (const patient of patients) {
    for (const program of programs) sql.execute(`INSERT INTO admission(client_id,program_id,provider_no,admission_date,admission_from_transfer,
      discharge_from_transfer,admission_status,lastUpdateDate) VALUES (${patient.id},${program},${q(writer.providerNo)},NOW(),0,0,'current',NOW())`);
    const normalized = await s.context.request.get(h.appUrl(config.baseUrl, `/demographic/DemographicEdit?demographic_no=${patient.id}`));
    try { h.assert(normalized.status() === 200 && Object.hasOwn(normalized.headers(), h.APPLICATION_HEADER), 'Cannot initialize owned compact/history patient'); }
    finally { await normalized.dispose(); }
  }
  fixture.lockPatient(recordLogin, patients[0].id, ['_eChart']);
  const seedNote = (kind, patient, index, signed = true, role = rolePair[index]) => {
    const note = { kind, patient, program: programs[index], author: writer.providerNo, uuid: randomUUID(), texts: new Set() };
    note.body = `${marker}-CH-${kind} H&P + café 中文`;
    note.texts.add(note.body); notes.push(note);
    note.id = h.insertId(sql, `INSERT INTO casemgmt_note(update_date,observation_date,demographic_no,provider_no,note,history,uuid,
      signed,signing_provider_no,locked,archived,include_issue_innote,position,program_no,reporter_caisi_role,
      reporter_program_team,appointmentNo,encounter_type)
      VALUES (NOW(),NOW(),${patient.id},${q(note.author)},${q(note.body)},${q(`${marker}-${kind}-history`)},${q(note.uuid)},
        ${signed ? 1 : 0},${q(signed ? note.author : '')},0,0,0,0,${q(note.program)},${q(role)},'0',0,'')`, 'compact/history note');
    return note;
  };
  const a = seedNote('editor-A', patients[0], 0, false);
  const b = seedNote('editor-B', patients[0], 1, false);
  const cross = seedNote('readable-cross-program', patients[0], 1, true, rolePair[0]);
  const hidden = seedNote('hidden-history', patients[0], 1);
  const counter = seedNote('record-positive', patients[1], 0);
  const catalogs = sql.rows(`SELECT issue_id,COALESCE(NULLIF(role,''),'system') FROM issue WHERE code IN ('401','250') ORDER BY issue_id`);
  h.assert(catalogs.length >= 2 && catalogs.slice(0, 2).every(row => positive(row[0])), 'Issue history needs existing non-CPP diagnoses');
  const issue = (patient, catalog, selected) => {
    const owned = { patient, catalog: catalog[0], id: null }; issues.push(owned);
    owned.id = h.insertId(sql, `INSERT INTO casemgmt_issue(demographic_no,issue_id,acute,certain,major,resolved,program_id,type,update_date)
      VALUES (${patient.id},${catalog[0]},0,0,0,0,${programs[0]},${q(catalog[1])},NOW())`, 'compact/history case issue');
    sql.execute(`INSERT INTO casemgmt_issue_notes(id,note_id) VALUES ${selected.map(note => `(${owned.id},${note.id})`).join(',')}`);
    return owned;
  };
  const visibleIssue = issue(patients[0], catalogs[0], [cross]);
  const hiddenIssue = issue(patients[0], catalogs[1], [hidden]);
  issue(patients[1], catalogs[0], [counter]);
  reader = await signIn(s, writer); s.cleanup(() => reader.context.close());
  reader.context.on('page', page => h.wireStrictPage(page, 'compact-history', s.recorder, { baseline: [] }));
  const tokens = [];
  try {
    for (const program of programs) {
      const page = await reader.context.newPage(); charts.push(page);
      await h.gotoApp(page, config.baseUrl, `/encounter/IncomingEncounter?${new URLSearchParams({ demographicNo: patients[0].id,
        providerNo: writer.providerNo, programId: program })}`);
      await waitForNavbars(page, 90000);
      await page.waitForFunction(() => !window.autoSaveXhr && !window.autoSaveOperation);
      await page.evaluate(() => clearAutoSaveTimer());
      tokens.push(await page.locator('#caseManagementEntryForm input[name="chartContext"]').inputValue());
    }
    const historyUrl = (patient, issueIds, token) => h.appUrl(config.baseUrl, `/CaseManagementEntry?${new URLSearchParams({
      method: 'issuehistory', demographicNo: patient, issueIds, ...(token ? { chartContext: token } : {}),
    })}`);
    const apiHistory = async (context, patient, issueIds, token) => {
      const before = JSON.stringify(state());
      const response = await context.request.get(historyUrl(patient, issueIds, token), { maxRedirects: 0 });
      try {
        const html = await response.text();
        const text = await charts[0].evaluate(value => new DOMParser().parseFromString(value, 'text/html').body.textContent, html);
        return { status: response.status(), headers: response.headers(), html, text };
      } finally { await response.dispose(); unchanged(before, 'Issue-history read'); }
    };
    const nativeHistory = async (index, catalog, allowed, forbidden) => {
      const before = JSON.stringify(state());
      const responsePromise = reader.context.waitForEvent('response', { predicate: response => {
        const url = new URL(response.url());
        return url.pathname.endsWith('/CaseManagementEntry') && url.searchParams.get('method') === 'issuehistory'
          && url.searchParams.get('issueIds') === catalog && url.searchParams.get('chartContext') === tokens[index];
      }, timeout: 30000 }); responsePromise.catch(() => {});
      const [page] = await Promise.all([reader.context.waitForEvent('page', { timeout: 30000 }),
        charts[index].evaluate(({ patient, catalog }) => showIssueHistory(patient, catalog), { patient: patients[0].id, catalog })]);
      try {
        const response = await responsePromise; await response.finished();
        await page.waitForLoadState('domcontentloaded');
        const url = new URL(page.url());
        h.assert(url.searchParams.get('demographicNo') === patients[0].id && url.searchParams.get('chartContext') === tokens[index],
          'The native issue-history popup lost its patient or originating program');
        const html = await response.text(); preserve('native issue history', response.status(), html, { program: programs[index], catalog });
        assertIssueHistory({ status: response.status(), headers: response.headers(), html,
          text: await page.locator('body').innerText() }, { status: 200, allowed, forbidden });
      } finally { await page.close(); unchanged(before, 'Native issue-history popup'); }
    };
    await s.step('the chart issue-history function opens readable cross-program history and refuses hidden or mixed reporter roles', async () => {
      await nativeHistory(0, visibleIssue.catalog, [cross.body], [hidden.body, a.body, b.body]);
      for (const selected of [hiddenIssue.catalog, `${visibleIssue.catalog},${hiddenIssue.catalog}`]) {
        assertIssueHistory(await apiHistory(reader.context, patients[0].id, selected, tokens[0]),
          { status: 403, forbidden: notes.map(note => note.body) });
      }
      await nativeHistory(1, hiddenIssue.catalog, [hidden.body], [cross.body, a.body, b.body]);
      await nativeHistory(0, visibleIssue.catalog, [cross.body], [hidden.body]);
      const record = await signIn(s, recordLogin);
      try {
        assertIssueHistory(await apiHistory(record.context, patients[1].id, visibleIssue.catalog),
          { status: 200, allowed: [counter.body], forbidden: [cross.body, hidden.body] });
        assertIssueHistory(await apiHistory(record.context, patients[0].id, visibleIssue.catalog),
          { status: 403, forbidden: notes.map(note => note.body) });
      } finally { await record.context.close(); }
    });
    await s.step('a native compact save advances its selected lock while a second same-patient editor remains saveable', async () => {
      for (const [index, note] of [a, b].entries()) {
        // Native entry can already select this provider's last unsigned note.
        if (await charts[index].locator('#caseManagementEntryForm [name="noteId"]').inputValue() !== note.id) {
          const response = charts[index].waitForResponse(response => response.request().method() === 'POST'
            && new URL(response.url()).pathname.endsWith('/CaseManagementEntry')
            && new URLSearchParams(response.request().postData() || '').get('method') === 'edit');
          const dialogs = await h.withExpectedDialogs(charts[index], async () => {
            await charts[index].locator(`#edit${note.id}`).click();
            h.assert((await response).status() === 200, 'The owned note did not open in its native editor');
          }, { accept: true });
          h.assert(dialogs.length <= 1 && dialogs.every(dialog => dialog.type === 'confirm'
            && dialog.text === 'You have started to edit this note in another window.\nDo you wish to continue?'),
          'Native Edit raised an unexpected dialog');
        }
        await charts[index].waitForFunction(id => document.forms.caseManagementEntryForm.elements.noteId.value === id, note.id);
        await charts[index].waitForFunction(() => !window.autoSaveXhr && !window.autoSaveOperation && window.issueUpdatesPending === 0);
        await charts[index].evaluate(() => clearAutoSaveTimer());
      }
      const locksBefore = lockRows();
      const sessions = (await reader.context.cookies(config.baseUrl.href)).filter(cookie => cookie.name === 'JSESSIONID');
      h.assert(sessions.length === 1 && sessions[0].value, 'The two editors have no unique authenticated session');
      const sessionsOwned = new Set([sessions[0].value, sessions[0].value.split('.')[0]]);
      const ownLock = note => locksBefore.filter(row => row[1] === writer.providerNo && row[2] === patients[0].id
        && row[3] === note.id && sessionsOwned.has(row[4]));
      h.assert(ownLock(a).length === 1 && ownLock(b).length === 1, 'Native Edit did not establish two distinct persisted owned locks');
      const lockA = ownLock(a)[0]; const lockB = ownLock(b)[0];
      const originalA = JSON.stringify(sql.rows(`SELECT * FROM casemgmt_note WHERE note_id=${a.id}`));
      const originalB = JSON.stringify(sql.rows(`SELECT * FROM casemgmt_note WHERE note_id=${b.id}`));
      // Model real per-program backups after both editors are loaded; A must consume
      // only A's backup, and the second patient's protected backup never participates.
      for (const note of [a, b, counter]) h.insertId(sql, `INSERT INTO casemgmt_tmpsave
        (demographic_no,provider_no,program_id,note,note_id,update_date)
        VALUES (${note.patient.id},${q(writer.providerNo)},${note.program},${q(`${note.body} protected backup`)},${note.id},NOW())`,
      'owned compact/history backup');
      const otherDrafts = () => sql.rows(`SELECT * FROM casemgmt_tmpsave WHERE demographic_no=${patients[0].id}
        AND provider_no=${q(writer.providerNo)} AND program_id=${programs[1]} ORDER BY id`);
      const bDraftBefore = JSON.stringify(otherDrafts());
      const beforeWrites = state();
      const originalNotes = JSON.stringify(sql.rows(`SELECT * FROM casemgmt_note WHERE demographic_no=${patients[0].id} ORDER BY note_id`));
      const addedNotes = [];
      const text = `${await charts[0].locator(NOTE).inputValue()}\n${marker}-compact-owned-A H&P + = & 中文`;
      a.texts.add(text.trim()); await charts[0].locator(NOTE).fill(text);
      await charts[0].evaluate(() => clearAutoSaveTimer());
      let held;
      const endpoint = new URL(h.appUrl(config.baseUrl, '/CaseManagementEntry'));
      const isEndpoint = url => url.origin === endpoint.origin && url.pathname === endpoint.pathname;
      const intercept = route => {
        const request = route.request();
        if (request.frame().page() !== charts[0] || new URLSearchParams(request.postData() || '').get('method') !== 'ajaxsave') return route.fallback();
        h.assert(!held, 'The first editor submitted duplicate compact saves'); held = route;
      };
      let completed = false;
      const switchWarning = await charts[0].evaluate(() => unsavedNoteWarning);
      h.assert(typeof switchWarning === 'string' && switchWarning, 'The rendered save-before-switch warning is missing');
      await reader.context.route(isEndpoint, intercept);
      try {
        const dialogs = await h.withExpectedDialogs(charts[0], async () => {
          const pending = charts[0].waitForResponse(response => response.request().method() === 'POST'
            && new URL(response.url()).pathname === endpoint.pathname
            && new URLSearchParams(response.request().postData() || '').get('method') === 'ajaxsave');
          pending.catch(() => {});
          await charts[0].locator('#newNoteImg').click();
          await waitUntil(() => held, 'the actual A compact-save request', 30000);
          const body = assertCompactIdentity(held.request(), { baseUrl: config.baseUrl, patient: patients[0].id,
            provider: writer.providerNo, note: a.id, token: tokens[0] });
          h.assert(body.get('noteTxt') === text, 'Native compact save changed the exact editor text');
          const foreign = `owned-other-session-${marker}`;
          sql.execute(`UPDATE casemgmt_note_lock SET session_id=${q(foreign)} WHERE id=${lockA[0]}
            AND provider_no=${q(writer.providerNo)} AND demographic_no=${patients[0].id} AND note_id=${a.id}
            AND session_id=${q(lockA[4])}`);
          const mutated = lockRows();
          h.assert(JSON.stringify(mutated) === JSON.stringify(locksBefore.map(row => row[0] === lockA[0]
            ? row.map((value, index) => index === 4 ? foreign : value) : row)), 'The owned foreign-session lock probe was not staged exactly');
          const before = JSON.stringify(state());
          try {
            const headers = Object.fromEntries(Object.entries(held.request().headers()).filter(([name]) =>
              ['content-type', 'csrf-token', 'x-requested-with', 'origin', 'referer'].includes(name)));
            const refused = await reader.context.request.post(held.request().url(), { data: held.request().postData(), headers, maxRedirects: 0 });
            try {
              const html = await refused.text(); preserve('foreign-session selected lock', refused.status(), html,
                { selectedLock: lockA[0], otherLock: lockB[0], selectedNote: a.id });
              h.assert(refused.status() === 409 && Object.hasOwn(refused.headers(), h.APPLICATION_HEADER)
                && !html.includes(marker), 'A compact save without its selected session lock was not refused before output');
            }
            finally { await refused.dispose(); }
            unchanged(before, 'Foreign-session selected-lock refusal');
          } finally {
            h.assert(JSON.stringify(lockRows()) === JSON.stringify(mutated), 'Selected-lock probe drifted; refusing to overwrite it');
            sql.execute(`UPDATE casemgmt_note_lock SET session_id=${q(lockA[4])} WHERE id=${lockA[0]}
              AND provider_no=${q(writer.providerNo)} AND demographic_no=${patients[0].id} AND note_id=${a.id} AND session_id=${q(foreign)}`);
            h.assert(JSON.stringify(lockRows()) === JSON.stringify(locksBefore), 'The exact selected lock was not restored');
          }
          // Commit the actual captured request once, but hold its unchanged server
          // response before the browser can retire the now-advanced selected lock.
          const upstream = await held.fetch({ maxRedirects: 0 });
          h.assert(upstream.status() === 200 && Object.hasOwn(upstream.headers(), h.APPLICATION_HEADER),
            'Native compact save did not return application200');
          const html = await upstream.text(); preserve('native compact save', upstream.status(), html,
            { selectedLock: lockA[0], otherLock: lockB[0], selectedNote: a.id });
          const id = await charts[0].evaluate(value => new DOMParser().parseFromString(value, 'text/html')
            .getElementById('noteIssues')?.getAttribute('data-saved-note-id'), html);
          h.assert(positive(id) && id !== a.id && sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE note_id=${id}
            AND uuid=${q(a.uuid)} AND demographic_no=${patients[0].id} AND program_no=${q(a.program)} AND note=${q(text.trim())}`) === '1',
          'Native compact save did not persist the exact A revision under its original UUID/program');
          addedNotes.push(id);
          assertSelectedLockAdvance(locksBefore, lockRows(),
            { selectedId: lockA[0], otherId: lockB[0], revision: id });
          h.assert(JSON.stringify(sql.rows(`SELECT * FROM casemgmt_note WHERE note_id=${a.id}`)) === originalA
            && JSON.stringify(sql.rows(`SELECT * FROM casemgmt_note WHERE note_id=${b.id}`)) === originalB,
          'Compact save changed a historical A row or the other editor note');
          const retired = charts[0].waitForResponse(response => response.request().method() === 'POST'
            && new URL(response.url()).pathname === endpoint.pathname
            && new URLSearchParams(response.request().postData() || '').get('method') === 'releaseNoteLock');
          retired.catch(() => {});
          await held.fulfill({ response: upstream }); completed = true;
          const saved = await pending; await saved.finished();
          h.assert(saved.status() === upstream.status() && await saved.text() === html,
            'The browser did not receive the actual compact server response unchanged');
          const released = await retired; await released.finished();
          assertNativeRelease(released.request(), { baseUrl: config.baseUrl, patient: patients[0].id,
            provider: writer.providerNo, note: id, token: tokens[0] });
          h.assert(released.status() === 200 && Object.hasOwn(released.headers(), h.APPLICATION_HEADER),
            'The native retired revision lock was not acknowledged by the application');
          await charts[0].waitForFunction(() => document.forms.caseManagementEntryForm.elements.noteId.value === '0'
            && !window.noteSwitchSavePending && window.issueUpdatesPending === 0);
          await charts[0].evaluate(() => clearAutoSaveTimer());
          const afterRetirement = lockRows(); const priorIds = new Set(locksBefore.map(row => row[0]));
          h.assert(!afterRetirement.some(row => row[0] === lockA[0])
            && JSON.stringify(afterRetirement.filter(row => priorIds.has(row[0])))
              === JSON.stringify(locksBefore.filter(row => row[0] !== lockA[0])),
          'Native retirement did not remove only its selected revision lock');
          const newLocks = afterRetirement.filter(row => !priorIds.has(row[0]));
          h.assert(newLocks.length <= 1 && newLocks.every(row => row[1] === writer.providerNo && row[2] === patients[0].id
            && row[3] === '0' && sessionsOwned.has(row[4])), 'Native New Note added an unrelated editor lock');
          h.assert(JSON.stringify(otherDrafts()) === bDraftBefore
            && sql.value(`SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no=${patients[0].id}
              AND provider_no=${q(writer.providerNo)} AND program_id=${programs[0]}`) === '0'
            && JSON.stringify(sql.rows(`SELECT * FROM casemgmt_note WHERE demographic_no=${patients[0].id}
              AND note_id<>${id} ORDER BY note_id`)) === originalNotes,
          'A compact save consumed B backup or changed another note/history row');
          assertProtectedEditorState(beforeWrites, state());
        }, { accept: true });
        h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm' && dialogs[0].text === switchWarning,
          'Native compact save did not retain its exact rendered save-before-switch confirmation');
      } finally {
        if (held && !completed) await held.abort().catch(() => {});
        await reader.context.unroute(isEndpoint, intercept);
      }
      const bText = `${await charts[1].locator(NOTE).inputValue()}\n${marker}-full-owned-B H&P + retained`;
      b.texts.add(bText); await charts[1].locator(NOTE).fill(bText);
      await charts[1].evaluate(() => clearAutoSaveTimer());
      const [saved] = await Promise.all([charts[1].waitForResponse(response => response.request().method() === 'POST'
        && new URL(response.url()).pathname.endsWith('/CaseManagementEntry')
        && new URLSearchParams(response.request().postData() || '').get('method') === 'save'), charts[1].locator('#saveImg').first().click()]);
      await saved.finished();
      h.assert(saved.status() === 200 && Object.hasOwn(saved.headers(), h.APPLICATION_HEADER), 'The untouched B lock no longer allows its native full Save');
      await charts[1].waitForFunction(previous => {
        const id = document.forms.caseManagementEntryForm.elements.noteId.value;
        return /^[1-9]\d*$/.test(id) && id !== previous && !window.autoSaveXhr;
      }, b.id);
      const bRevision = await charts[1].locator('#caseManagementEntryForm [name="noteId"]').inputValue();
      addedNotes.push(bRevision);
      h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE note_id=${bRevision} AND uuid=${q(b.uuid)}
        AND demographic_no=${patients[0].id} AND program_no=${q(b.program)} AND note=${q(bText)}`) === '1'
        && JSON.stringify(sql.rows(`SELECT * FROM casemgmt_note WHERE note_id=${b.id}`)) === originalB,
      'B full Save lost its own text/program/history after A compact saved');
      h.assert(otherDrafts().length === 0 && JSON.stringify(sql.rows(`SELECT * FROM casemgmt_note
        WHERE demographic_no=${patients[0].id} AND note_id NOT IN (${addedNotes.join(',')}) ORDER BY note_id`)) === originalNotes,
      'B full Save retained its consumed backup or changed another historical note');
      assertProtectedEditorState(beforeWrites, state());
      await charts[1].evaluate(() => clearAutoSaveTimer());
    });
  } catch (error) { fixtureFailure = error; throw error; }
  finally {
    if (reader) {
      try {
        const cookies = (await reader.context.cookies(config.baseUrl.href)).filter(cookie => cookie.name === 'JSESSIONID');
        h.assert(cookies.length === 1 && cookies[0].value, 'Compact/history cleanup has no unique owned session');
        const sessions = new Set([cookies[0].value, cookies[0].value.split('.')[0]]);
        const before = state();
        h.assert(before.locks.every(row => row[1] === writer.providerNo && row[2] === patients[0].id && sessions.has(row[4])
          && (row[3] === '0' || sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE note_id=${row[3]}
            AND demographic_no=${patients[0].id} AND provider_no=${q(writer.providerNo)}`) === '1')), 'Compact/history cleanup acquired an unrelated lock');
        for (const row of before.locks) {
          const response = await reader.context.request.post(h.appUrl(config.baseUrl, '/CaseManagementEntry'), {
            form: { method: 'releaseNoteLock', demographicNo: patients[0].id, providerNo: writer.providerNo,
              noteId: row[3], chartContext: tokens[0], 'CSRF-TOKEN': await charts[0].evaluate(() => window.CarlosAjax?.getCsrfToken()) }, maxRedirects: 0 });
          try { h.assert(response.status() === 200 && Object.hasOwn(response.headers(), h.APPLICATION_HEADER), 'Owned compact/history lock release was not acknowledged'); }
          finally { await response.dispose(); }
        }
        const after = state(); h.assert(after.locks.length === 0, 'An owned compact/history lock survived API cleanup');
        before.locks = []; after.locks = [];
        h.assert(JSON.stringify(before) === JSON.stringify(after), 'Compact/history lock cleanup changed clinical, draft or policy data');
        for (const [index, page] of charts.entries()) if (!page.isClosed()) {
          h.assert(await page.evaluate(expected => {
            const form = document.forms.caseManagementEntryForm;
            if (!form || String(window.demographicNo) !== expected.patient || String(window.providerNo) !== expected.provider
                || form.elements.chartContext.value !== expected.token) return false;
            window.needToReleaseLock = false; return true;
          }, { patient: patients[0].id, provider: writer.providerNo, token: tokens[index] }), 'Owned chart identity changed during retirement');
          await page.close();
        }
      } catch (error) { if (fixtureFailure) fixtureFailure.message += `; Compact/history retirement: ${error.message}`; else throw error; }
    }
  }
}

module.exports = { checkCompactSaveAndIssueHistory, assertCompactIdentity, assertSelectedLockAdvance, assertNativeRelease, assertIssueHistory,
  assertProtectedEditorState };
