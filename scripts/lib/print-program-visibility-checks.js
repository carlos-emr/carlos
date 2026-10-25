/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const { createHash } = require('node:crypto');
const h = require('./playwright-harness');
const { signIn } = require('./authz-read-probe');
const { waitForNavbars } = require('../echart-navbar-modules-playwright-checks');
const { readPatientState, assertPatientStateUnchanged } = require('../echart-two-patient-isolation-playwright-checks');
const q = h.sqlString;

/** Actual cached reporter-role policy must agree across the native chart and every PDF scope. */
async function checkPrintProgramVisibility(s, { fixture }) {
  const { sql, provider, marker, config } = s;
  const owner = `${marker}-PV`;
  const programs = [];
  const notes = [];
  const issues = [];
  const bodies = {};
  let patient;
  let login;
  let facility;
  let ownsDefaults = false;
  let ownsPrograms = false;
  const programNames = ['origin', 'shared'].map(label => `${marker}-pdf-program-${label}`);
  const sharedDefaults = JSON.stringify(sql.rows(`SELECT * FROM provider_default_program
    WHERE provider_no=${q(provider)} ORDER BY program_id`));
  // These rows predate this login and are already in RoleCache. Newly created global
  // login roles are deliberately excluded; RoleCache reloads only at app startup.
  const pair = sql.rows(`SELECT r.role_no,r.role_name,h.role_no,h.role_name
    FROM secRole r CROSS JOIN secRole h
    WHERE r.role_no<>h.role_no AND r.role_name NOT LIKE 'FAKE%' AND h.role_name NOT LIKE 'FAKE%'
      AND r.role_name=TRIM(r.role_name) AND h.role_name=TRIM(h.role_name)
      AND r.role_name<>'' AND h.role_name<>''
      AND NOT EXISTS (SELECT 1 FROM default_role_access d JOIN access_type a ON a.access_id=d.access_id
        WHERE d.role_id=r.role_no AND a.name=CONCAT('read ',LOWER(h.role_name),' notes'))
    ORDER BY r.role_no,h.role_no LIMIT 1`)[0];
  h.assert(pair && /^[1-9]\d*$/.test(pair[0]) && /^[1-9]\d*$/.test(pair[2]),
    'No preexisting cached reporter-role pair can isolate a real denial without policy changes');
  const [readerRole, , hiddenRole] = pair;
  const policy = () => ({
    roles: sql.rows(`SELECT * FROM secRole WHERE role_no IN (${readerRole},${hiddenRole}) ORDER BY role_no`),
    defaults: sql.rows('SELECT * FROM default_role_access ORDER BY id'),
  });
  const originalPolicy = JSON.stringify(policy());
  h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE last_name=${q(owner)}
    AND first_name='PdfPolicy'`) === '0', 'An intended PDF policy patient already exists');
  s.cleanup(() => {
    if (ownsPrograms) {
      const recovered = sql.rows(`SELECT id,name,facilityId FROM program
        WHERE name IN (${programNames.map(q).join(',')}) ORDER BY id`);
      h.assert(new Set(recovered.map(row => row[1])).size === recovered.length
        && recovered.every(row => row[2] === facility), 'Owned PDF program recovery is ambiguous');
      for (const row of recovered) if (!programs.includes(row[0])) programs.push(row[0]);
    }
    if (!patient) {
      const recovered = sql.rows(`SELECT demographic_no FROM demographic WHERE last_name=${q(owner)}
        AND first_name='PdfPolicy' AND provider_no=${q(provider)}`);
      h.assert(recovered.length <= 1, 'Owned PDF patient recovery is ambiguous');
      patient = recovered[0]?.[0];
    }
    for (const program of programs) {
      h.assert(patient && login && sql.value(`SELECT COUNT(*) FROM program_provider
        WHERE program_id=${program} AND (provider_no IS NULL OR provider_no<>${q(login.providerNo)})`) === '0',
      'Another provider joined the owned PDF program; retaining its fixture');
      h.assert(sql.value(`SELECT COUNT(*) FROM admission WHERE program_id=${program}
        AND (client_id IS NULL OR client_id<>${patient})`) === '0',
      'Another patient joined the owned PDF program; retaining its fixture');
    }
    if (patient) {
      h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient}
        AND last_name=${q(owner)}`) === '1', 'PDF visibility patient ownership changed');
      const ids = sql.rows(`SELECT note_id,note FROM casemgmt_note WHERE demographic_no=${patient}`);
      h.assert(ids.every(row => Object.values(bodies).includes(row[1])),
        'Unexpected note retained on PDF visibility patient; refusing parent deletion');
      if (notes.length) {
        const tracked = sql.rows(`SELECT note_id,demographic_no,note FROM casemgmt_note
          WHERE note_id IN (${notes.join(',')})`);
        h.assert(tracked.every(row => row[1] === patient && Object.values(bodies).includes(row[2])),
          'A tracked PDF note moved to another patient or changed ownership; retaining its children');
      }
      if (issues.length) {
        const tracked = sql.rows(`SELECT id,demographic_no FROM casemgmt_issue WHERE id IN (${issues.join(',')})`);
        h.assert(tracked.every(row => row[1] === patient),
          'A tracked PDF issue moved to another patient; retaining its relationships');
      }
      const clinical = readPatientState(sql, patient);
      const known = new Set(['demographic', 'demographicExtensions', 'notes', 'noteExtensions', 'noteLinks',
        'noteIssues', 'issues', 'signatures', 'cpp', 'echart', 'drafts']);
      for (const [name, rows] of Object.entries(clinical)) h.assert(known.has(name) || rows.length === 0,
        `Unexpected ${name} retained on PDF visibility patient; refusing parent deletion`);
      h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_issue_notes n JOIN casemgmt_issue i ON i.id=n.id
        WHERE i.demographic_no=${patient} AND n.note_id NOT IN
          (SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient})`) === '0',
      'A PDF policy issue acquired a foreign note relationship; retaining the fixture');
      const ownedNoteIds = [...new Set([...notes, ...ids.map(row => row[0])])];
      h.assert(ownedNoteIds.every(id => /^[1-9]\d*$/.test(id)), 'Owned PDF note cleanup identities changed');
      const noteSet = ownedNoteIds.length ? ownedNoteIds.join(',') : 'SELECT note_id FROM casemgmt_note WHERE 1=0';
      h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_issue_notes n LEFT JOIN casemgmt_issue i ON i.id=n.id
        WHERE n.note_id IN (${noteSet}) AND (i.id IS NULL OR i.demographic_no IS NULL
          OR i.demographic_no<>${patient})`) === '0',
      'A PDF note acquired a foreign or missing issue parent; retaining its relationships');
      sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${noteSet});
        DELETE FROM casemgmt_note_ext WHERE note_id IN (${noteSet});
        DELETE FROM casemgmt_note_link WHERE note_id IN (${noteSet});
        DELETE FROM hash_audit WHERE type='enc' AND id IN (${noteSet});
        DELETE FROM casemgmt_note WHERE demographic_no=${patient};
        DELETE FROM casemgmt_issue WHERE demographic_no=${patient};
        DELETE FROM casemgmt_cpp WHERE demographic_no=${patient};
        DELETE FROM casemgmt_note_lock WHERE demographic_no=${patient};
        DELETE FROM casemgmt_tmpsave WHERE demographic_no=${patient};
        DELETE FROM eChart WHERE demographicNo=${patient};
        DELETE FROM admission WHERE client_id=${patient};
        DELETE FROM log WHERE demographic_no=${patient};
        DELETE FROM demographicExt WHERE demographic_no=${patient};
        DELETE FROM demographicArchive WHERE demographic_no=${patient};
        DELETE FROM demographic WHERE demographic_no=${patient} AND last_name=${q(owner)}`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient})
        + (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})
        + (SELECT COUNT(*) FROM casemgmt_issue WHERE demographic_no=${patient})
        + (SELECT COUNT(*) FROM casemgmt_cpp WHERE demographic_no=${patient})
        + (SELECT COUNT(*) FROM eChart WHERE demographicNo=${patient})
        + (SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${patient})
        + (SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no=${patient})
        + (SELECT COUNT(*) FROM admission WHERE client_id=${patient})
        + (SELECT COUNT(*) FROM casemgmt_note_ext WHERE note_id IN (${noteSet}))
        + (SELECT COUNT(*) FROM casemgmt_note_link WHERE note_id IN (${noteSet}))
        + (SELECT COUNT(*) FROM casemgmt_issue_notes WHERE note_id IN (${noteSet}))
        + (SELECT COUNT(*) FROM hash_audit WHERE type='enc' AND id IN (${noteSet}))`) === '0',
      'PDF visibility patient or clinical child residue');
    }
    if (ownsDefaults) {
      sql.execute(`DELETE FROM provider_default_program WHERE provider_no=${q(login.providerNo)}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM provider_default_program WHERE provider_no=${q(login.providerNo)}`) === '0',
        'The owned PDF reader default survived cleanup');
    }
    for (const program of programs) {
      h.assert(sql.value(`SELECT COUNT(*) FROM program WHERE id=${program}
        AND name LIKE ${q(`${marker}-pdf-program-%`)}`) === '1', 'PDF visibility program ownership changed');
      h.assert(sql.value(`SELECT COUNT(*) FROM program_provider WHERE program_id=${program}
        AND provider_no<>${q(login.providerNo)}`) === '0', 'Another provider joined the owned PDF program');
      h.assert(sql.value(`SELECT COUNT(*) FROM program_access WHERE program_id=${program}`) === '0',
        'An unexpected access policy was attached to the owned PDF program');
      sql.execute(`DELETE FROM program_provider WHERE program_id=${program} AND provider_no=${q(login.providerNo)};
        DELETE FROM program WHERE id=${program} AND name LIKE ${q(`${marker}-pdf-program-%`)}`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM program WHERE id=${program})
        + (SELECT COUNT(*) FROM program_provider WHERE program_id=${program})
        + (SELECT COUNT(*) FROM admission WHERE program_id=${program})`) === '0',
      'The owned PDF program or its relationships survived cleanup');
    }
    h.assert(JSON.stringify(policy()) === originalPolicy, 'Shared reporter-role policy changed');
    h.assert(JSON.stringify(sql.rows(`SELECT * FROM provider_default_program WHERE provider_no=${q(provider)}
      ORDER BY program_id`)) === sharedDefaults, 'The main provider default changed');
  });
  patient = h.insertId(sql, `INSERT INTO demographic
    (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
     provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES (${q(owner)},'PdfPolicy','1980','01','02','F','AC',${q(provider)},'ON','ON','NR',NOW())`,
  'role-filtered PDF patient');
  login = fixture.addLogin('doctor');
  h.assert(sql.value(`SELECT COUNT(*) FROM provider_default_program WHERE provider_no=${q(login.providerNo)}`) === '0',
    'The unused PDF reader has a preexisting default-program row');
  ownsDefaults = true;
  facility = sql.value(`SELECT f.id FROM Facility f JOIN provider_facility p ON p.facility_id=f.id
    WHERE p.provider_no=${q(provider)} AND f.disabled=0 ORDER BY f.id LIMIT 1`);
  h.assert(/^[1-9]\d*$/.test(facility), 'The main authenticated provider has no enabled Facility');
  sql.execute(`INSERT INTO provider_facility(provider_no,facility_id) VALUES (${q(login.providerNo)},${facility})`);
  h.assert(sql.value(`SELECT COUNT(*) FROM provider_facility WHERE provider_no=${q(login.providerNo)}`) === '1',
    'The PDF reader does not have exactly one selectable Facility');
  h.assert(sql.value(`SELECT COUNT(*) FROM program WHERE name IN (${programNames.map(q).join(',')})`) === '0',
    'An intended PDF program name already exists');
  ownsPrograms = true;
  for (const label of ['origin', 'shared']) {
    const program = h.insertId(sql, `INSERT INTO program
      (facilityId,name,type,maxAllowed,programStatus,transgender,firstNation,alcohol,
       physicalHealth,mentalHealth,housing,exclusiveView,ageMin,ageMax,
       enableEncounterTime,enableEncounterTransportationTime,enableOCAN)
      VALUES (${facility},${q(`${marker}-pdf-program-${label}`)},'service',1,'active',0,0,0,0,0,0,
        'none',0,150,0,0,0)`, 'owned PDF policy program');
    programs.push(program);
  }
  sql.execute(`INSERT INTO program_provider(program_id,provider_no,role_id,team_id)
    VALUES (${programs[0]},${q(login.providerNo)},${readerRole},0),
      (${programs[1]},${q(login.providerNo)},${hiddenRole},0);
    INSERT INTO admission(client_id,program_id,provider_no,admission_date,
      admission_from_transfer,discharge_from_transfer,admission_status,lastUpdateDate)
    VALUES (${patient},${programs[0]},${q(login.providerNo)},NOW(),0,0,'current',NOW()),
      (${patient},${programs[1]},${q(login.providerNo)},NOW(),0,0,'current',NOW())`);
  h.assert(sql.value(`SELECT COUNT(*) FROM program_access WHERE program_id IN (${programs.join(',')})`) === '0',
    'Owned programs unexpectedly have a cached policy override');
  const makeNote = (key, role, program) => {
    const body = `${marker}-ROLE-${key}-END café 中文`;
    bodies[key] = body;
    const id = h.insertId(sql, `INSERT INTO casemgmt_note
      (update_date,observation_date,demographic_no,provider_no,note,signed,signing_provider_no,
       include_issue_innote,encounter_type,program_no,reporter_caisi_role,reporter_program_team,
       history,uuid,locked,archived,position,appointmentNo)
      VALUES (NOW(),'2026-02-03 10:00:00',${patient},${q(provider)},${q(body)},1,${q(provider)},
        0,'',${q(program)},${q(role)},'0',${q(body)},UUID(),'0',0,0,0)`, 'role-filtered note');
    notes.push(id);
    return id;
  };
  const local = makeNote('LOCAL', readerRole, programs[0]);
  const shared = makeNote('SHARED', readerRole, programs[1]);
  const hidden = makeNote('HIDDEN', hiddenRole, programs[0]);
  const cppVisible = makeNote('CPP', readerRole, programs[1]);
  const cppHidden = makeNote('CPP-HIDDEN', hiddenRole, programs[0]);
  const rxVisible = makeNote('OMEDS', readerRole, programs[1]);
  const rxHidden = makeNote('OMEDS-HIDDEN', hiddenRole, programs[0]);
  for (const [code, selected] of [['MedHistory', [cppVisible, cppHidden]], ['OMeds', [rxVisible, rxHidden]]]) {
    const catalog = sql.rows(`SELECT issue_id,COALESCE(NULLIF(role,''),'system') FROM issue
      WHERE code=${q(code)} ORDER BY issue_id LIMIT 1`)[0];
    h.assert(catalog && /^[1-9]\d*$/.test(catalog[0]), 'The existing CPP catalog is missing');
    const issue = h.insertId(sql, `INSERT INTO casemgmt_issue
      (demographic_no,issue_id,acute,certain,major,resolved,program_id,type,update_date)
      VALUES (${patient},${catalog[0]},0,0,0,0,${programs[0]},${q(catalog[1] || 'system')},NOW())`,
    'owned PDF CPP issue');
    issues.push(issue);
    sql.execute(`INSERT INTO casemgmt_issue_notes(id,note_id) VALUES ${selected.map(id => `(${issue},${id})`).join(',')}`);
  }
  const reader = await signIn(s, login);
  s.cleanup(() => reader.context.close());
  reader.context.on('page', page => h.wireStrictPage(page, 'print-program-role-policy', s.recorder));
  const chart = await reader.context.newPage();
  const nativeCharts = [{ page: chart, program: programs[0] }];
  let token;
  let otherToken;
  const clinicalToken = text => text.split(' café')[0];
  const hiddenTokens = [bodies.HIDDEN, bodies['CPP-HIDDEN'], bodies['OMEDS-HIDDEN']].map(clinicalToken);
  const visibleKeys = ['LOCAL', 'SHARED', 'CPP', 'OMEDS'];
  const snapshot = () => ({ ...readPatientState(sql, patient),
    locks: sql.rows(`SELECT * FROM casemgmt_note_lock WHERE demographic_no=${patient} ORDER BY id`),
    programRows: sql.rows(`SELECT * FROM program WHERE id IN (${programs.join(',')}) ORDER BY id`),
    memberships: sql.rows(`SELECT * FROM program_provider WHERE provider_no=${q(login.providerNo)} ORDER BY id`),
    admissions: sql.rows(`SELECT * FROM admission WHERE client_id=${patient} ORDER BY am_id`),
    defaults: sql.rows(`SELECT * FROM provider_default_program WHERE provider_no=${q(login.providerNo)} ORDER BY program_id`),
  });
  let workflowFailure;
  try {
  await s.step('the native chart actually shows allowed shared-program notes and hides the other reporter role', async () => {
    await h.gotoApp(chart, config.baseUrl, `/encounter/IncomingEncounter?${new URLSearchParams({
      demographicNo: patient, providerNo: login.providerNo, programId: programs[0],
    })}`);
    await waitForNavbars(chart, 90000);
    await chart.waitForFunction(() => !window.autoSaveXhr);
    await chart.evaluate(() => { if (typeof clearAutoSaveTimer === 'function') clearAutoSaveTimer(); });
    token = await chart.locator('#caseManagementEntryForm input[name="chartContext"]').inputValue();
    h.assert(token, 'The native chart has no configured program context');
    const visibleText = await chart.locator('body').innerText();
    const html = await chart.content();
    h.assert(visibleText.includes(clinicalToken(bodies.LOCAL)) && visibleText.includes(clinicalToken(bodies.SHARED))
      && !hiddenTokens.some(text => visibleText.includes(text) || html.includes(text)),
    'The native chart does not establish the intended real reporter-role policy');
    h.assert(sql.value(`SELECT program_no FROM casemgmt_note WHERE note_id=${shared}`) === programs[1]
      && programs[0] !== programs[1], 'The allowed shared note does not distinguish stored and chart programs');
    const otherChart = await reader.context.newPage();
    nativeCharts.push({ page: otherChart, program: programs[1] });
    await h.gotoApp(otherChart, config.baseUrl, `/encounter/IncomingEncounter?${new URLSearchParams({
      demographicNo: patient, providerNo: login.providerNo, programId: programs[1],
    })}`);
    await waitForNavbars(otherChart, 90000);
    await otherChart.waitForFunction(() => !window.autoSaveXhr);
    await otherChart.evaluate(() => { if (typeof clearAutoSaveTimer === 'function') clearAutoSaveTimer(); });
    otherToken = await otherChart.locator('#caseManagementEntryForm input[name="chartContext"]').inputValue();
    h.assert(otherToken && otherToken !== token
      && (await otherChart.locator('body').innerText()).includes(clinicalToken(bodies.HIDDEN)),
    'The second program does not prove the hidden note is readable under its own reporter role');
  });
  const stable = snapshot();
  const unchanged = label => {
    assertPatientStateUnchanged(stable, snapshot(), label);
    h.assert(JSON.stringify(policy()) === originalPolicy, `${label}: shared role policy changed`);
    h.assert(sql.value(`SELECT COUNT(*) FROM program_access WHERE program_id IN (${programs.join(',')})`) === '0',
      `${label}: a program policy was changed`);
  };
  const address = (extra = {}, route = '/CaseManagementEntry') => h.appUrl(config.baseUrl, `${route}?${new URLSearchParams({
    ...(route === '/CaseManagementEntry' ? { method: 'print', notes2print: 'ALL_NOTES', printCPP: 'false', printRx: 'false' } : {}),
    demographicNo: patient, chartContext: token, ...extra,
  })}`);
  const printArtifactDir = fs.mkdtempSync('/tmp/carlos-role-print-evidence-');
  let printArtifactIndex = 0;
  const preservePrintResponse = (label, response, bytes, expectedKeys) => {
    const basename = `${printArtifactDir}/response-${++printArtifactIndex}`;
    const proof = { label, status: response.status(),
      fromApplication: Object.hasOwn(response.headers(), h.APPLICATION_HEADER),
      contentType: response.headers()['content-type'] || '', bytes: bytes.length,
      sha256: createHash('sha256').update(bytes).digest('hex'), expectedKeys };
    const artifact = `${basename}.${bytes.subarray(0, 5).toString('latin1') === '%PDF-' ? 'pdf' : 'bin'}`;
    fs.writeFileSync(artifact, bytes, { mode: 0o600 });
    const report = `${basename}.json`;
    const save = () => fs.writeFileSync(report, JSON.stringify(proof, null, 2) + '\n', { mode: 0o600 });
    save();
    let capturedText;
    const addText = text => {
        capturedText = text;
        fs.writeFileSync(`${basename}.txt`, text, { mode: 0o600 });
        proof.textSHA256 = createHash('sha256').update(text).digest('hex');
        proof.cafe = text.includes('café');
        proof.chinese = text.includes('中文');
        proof.tokens = Object.fromEntries(Object.entries(bodies).map(([key, body]) => [key, {
          exact: text.includes(clinicalToken(body)),
          withoutWhitespace: text.replace(/\s+/g, '').includes(clinicalToken(body)),
        }]));
        save();
        return JSON.stringify({ cafe: proof.cafe, chinese: proof.chinese, tokens: proof.tokens, report });
    };
    // Preserve an unexpected or malformed PDF before its status/header check
    // can fail. Diagnostic extraction never changes the exact pass criteria.
    if (bytes.subarray(0, 5).toString('latin1') === '%PDF-') {
      try { addText(execFileSync('pdftotext', ['-', '-'], { input: bytes, encoding: 'utf8', timeout: 20000 })); }
      catch (error) {
        proof.extractionError = { name: error.name, status: Number.isInteger(error.status) ? error.status : null };
        save();
      }
    }
    console.log(`  Print response evidence ${JSON.stringify({ label, artifact, report,
      status: proof.status, bytes: proof.bytes, sha256: proof.sha256 })}`);
    return { report, get text() { return capturedText; }, addText };
  };
  await s.step('shortened note HTML enforces the originating role while retaining readable cross-program notes', async () => {
    const csrf = await chart.evaluate(() => window.CarlosAjax?.getCsrfToken());
    h.assert(csrf, 'The owned chart has no genuine CSRF token');
    for (const [label, selected, context, expected, key] of [
      ['shared note under origin', shared, token, 200, 'SHARED'],
      ['hidden note under origin', hidden, token, 403, null],
      ['hidden note under its readable program', hidden, otherToken, 200, 'HIDDEN'],
    ]) {
      const response = await reader.context.request.get(address({ method: 'viewNote', noteId: selected,
        raw: 'true', chartContext: context, 'CSRF-TOKEN': csrf }, '/CaseManagementView'), { maxRedirects: 0 });
      try {
        const bytes = await response.body();
        const body = bytes.toString('utf8');
        const evidence = preservePrintResponse(label, response, bytes, key ? [key] : []);
        const diagnostic = evidence.addText(evidence.text ?? body);
        h.assert(response.status() === expected && Object.hasOwn(response.headers(), h.APPLICATION_HEADER),
          `${label}: HTTP ${response.status()}, expected ${expected}; ${diagnostic}`);
        if (key) h.assert(body.includes(clinicalToken(bodies[key])) && body.includes('café') && body.includes('中文')
          && !Object.keys(bodies).filter(other => other !== key).some(other => body.includes(clinicalToken(bodies[other]))),
        `${label}: shortened HTML changed the note scope; ${diagnostic}`);
        else h.assert(!body.includes(`${marker}-ROLE-`) && bytes.subarray(0, 5).toString('latin1') !== '%PDF-',
          `${label}: refused HTML returned clinical bytes; ${diagnostic}`);
      } finally { await response.dispose(); }
      unchanged(label);
    }
  });
  const actualPdf = async (label, extra, expectedKeys, route) => {
    const response = await reader.context.request.get(address(extra, route), { maxRedirects: 0, timeout: 40000 });
    try {
      const bytes = await response.body();
      const evidence = preservePrintResponse(label, response, bytes, expectedKeys);
      h.assert(response.status() === 200 && Object.hasOwn(response.headers(), h.APPLICATION_HEADER)
        && /application\/pdf/i.test(response.headers()['content-type'] || '') && bytes.length > 1000
        && bytes.subarray(0, 5).toString('latin1') === '%PDF-'
        && /%%EOF\s*$/.test(bytes.subarray(-1024).toString('latin1')), `${label}: no complete application PDF; ${evidence.report}`);
      const text = evidence.text ?? execFileSync('pdftotext', ['-', '-'], { input: bytes, encoding: 'utf8', timeout: 20000 });
      const diagnostic = evidence.addText(text);
      const unexpectedKeys = Object.keys(bodies).filter(key => !expectedKeys.includes(key));
      h.assert(expectedKeys.every(key => text.includes(clinicalToken(bodies[key])))
        && !unexpectedKeys.some(key => text.includes(clinicalToken(bodies[key])))
        && text.includes('café') && text.includes('中文'),
      `${label}: PDF visibility disagrees with the native chart; ${diagnostic}`);
      console.log(`  PDF role policy ${label}: ${bytes.length} bytes SHA256 ${createHash('sha256').update(bytes).digest('hex')}`);
    } finally { await response.dispose(); }
    unchanged(label);
  };
  await s.step('explicit hidden PDF selections refuse before any PDF or partial clinical output', async () => {
    for (const selected of [hidden, `${local},${hidden}`]) {
      const response = await reader.context.request.get(address({ notes2print: selected }), { maxRedirects: 0 });
      try {
        const bytes = await response.body();
        const body = bytes.toString('utf8');
        const evidence = preservePrintResponse('explicit hidden selection under origin', response, bytes, []);
        if (evidence.text === undefined && bytes.subarray(0, 5).toString('latin1') !== '%PDF-') evidence.addText(body);
        const fromApplication = Object.hasOwn(response.headers(), h.APPLICATION_HEADER);
        const isPdf = bytes.subarray(0, 5).toString('latin1') === '%PDF-';
        const hasClinicalText = Object.values(bodies).some(text => body.includes(clinicalToken(text)));
        h.assert(response.status() === 403 && fromApplication && !isPdf && !hasClinicalText,
          `Hidden explicit selection: HTTP ${response.status()}, application=${fromApplication}, PDF=${isPdf}, clinical=${hasClinicalText}; ${evidence.report}`);
      } finally { await response.dispose(); }
      unchanged('Refused explicit selection');
    }
  });
  await s.step('actual selected, ALL, date, CPP, Other Meds and full-chart PDFs apply role policy and retain readable cross-program notes', async () => {
    await actualPdf('selected shared', { notes2print: shared }, ['SHARED']);
    await actualPdf('ALL', {}, visibleKeys);
    await actualPdf('date range', { pType: 'dates', pStartDate: '1-Jan-2026', pEndDate: '31-Dec-2026' }, visibleKeys);
    await actualPdf('CPP only', { notes2print: '', printCPP: 'true' }, ['CPP', 'OMEDS']);
    await actualPdf('Other Meds only', { notes2print: '', printRx: 'true' }, ['OMEDS']);
    await actualPdf('ALL plus CPP', { printCPP: 'true' }, visibleKeys);
    await actualPdf('full chart', {}, visibleKeys, '/OscarChartPrint');
  });
  await s.step('REST print preserves the signed program against last-window state, query aliases and program-zero hints', async () => {
    const restAddress = (extra = {}, print = { printType: 'all', selectedList: [] }, contexts = [token]) => {
      const query = new URLSearchParams({ printOps: JSON.stringify({ cpp: false, rx: false, labs: false,
        preventions: false, allergies: false, ...print }), ...extra });
      for (const context of contexts) query.append('chartContext', context);
      return h.appUrl(config.baseUrl, `/ws/rs/recordUX/${patient}/print?${query}`);
    };
    const checkRest = async (label, url, expected, allowedKeys = visibleKeys) => {
      const response = await reader.context.request.get(url, { maxRedirects: 0, timeout: 40000 });
      try {
        const bytes = await response.body();
        const evidence = preservePrintResponse(label, response, bytes, expected === 200 ? allowedKeys : []);
        h.assert(response.status() === expected && Object.hasOwn(response.headers(), h.APPLICATION_HEADER),
          `${label}: REST print answered HTTP ${response.status()}, expected ${expected}; ${evidence.report}`);
        if (expected === 200) {
          h.assert(/application\/pdf/i.test(response.headers()['content-type'] || '') && bytes.length > 1000
            && bytes.subarray(0, 5).toString('latin1') === '%PDF-'
            && /%%EOF\s*$/.test(bytes.subarray(-1024).toString('latin1')), `${label}: no complete REST PDF`);
          const text = evidence.text ?? execFileSync('pdftotext', ['-', '-'], { input: bytes, encoding: 'utf8', timeout: 20000 });
          const diagnostic = evidence.addText(text);
          const unexpectedKeys = Object.keys(bodies).filter(key => !allowedKeys.includes(key));
          h.assert(allowedKeys.every(key => text.includes(clinicalToken(bodies[key])))
            && !unexpectedKeys.some(key => text.includes(clinicalToken(bodies[key]))),
          `${label}: REST PDF used another visibility context or note selection; ${diagnostic}`);
        } else {
          h.assert(bytes.subarray(0, 5).toString('latin1') !== '%PDF-'
            && !Object.values(bodies).some(text => bytes.toString('utf8').includes(clinicalToken(text))),
          `${label}: refused REST print returned PDF or clinical bytes`);
        }
      } finally { await response.dispose(); }
      unchanged(label);
    };
    await checkRest('REST signed origin', restAddress(), 200);
    await checkRest('REST path overrides both patient query aliases', restAddress({
      demographicNo: String(Number(patient) + 100000), demographic_no: String(Number(patient) + 100001),
    }), 200);
    await checkRest('REST signed origin ignores program-zero downgrade hints', restAddress({
      programId: '0', case_program_id: '0',
    }), 200);
    await checkRest('REST selected hidden note', restAddress({}, { printType: 'selected', selectedList: [hidden] }), 403);
    await checkRest('REST genuine other-program positive', restAddress({}, {
      printType: 'selected', selectedList: [hidden],
    }, [otherToken]), 200, ['HIDDEN']);
    const signatureStart = token.indexOf('.') + 1;
    const forged = token.slice(0, signatureStart) + (token[signatureStart] === 'A' ? 'B' : 'A')
      + token.slice(signatureStart + 1);
    await checkRest('REST forged signed context', restAddress({}, undefined, [forged]), 403);
    await checkRest('REST conflicting genuine signed contexts', restAddress({}, undefined, [token, otherToken]), 403);
  });
  } catch (error) {
    workflowFailure = error;
    throw error;
  } finally {
    // These are read-test fixtures with autosave already paused. Release only
    // each owned form's session lock through the real API before closing it;
    // this cleanup does not test native pagehide/keepalive transport.
    const failures = [];
    const evidenceDir = fs.mkdtempSync('/tmp/carlos-owned-chart-cleanup-');
    const lockRows = () => sql.rows(`SELECT id,provider_no,demographic_no,note_id,session_id,
      lock_acquired,ip_address FROM casemgmt_note_lock WHERE demographic_no=${patient} ORDER BY id`);
    for (const [index, { page, program }] of nativeCharts.entries()) {
      if (page.isClosed()) continue;
      const file = `${evidenceDir}/chart-${index}.json`;
      const proof = { phase: 'authenticated owned fixture lock cleanup', patient, program,
        provider: login.providerNo, status: null, fromApplication: false };
      const save = () => fs.writeFileSync(file, JSON.stringify(proof, null, 2) + '\n', { mode: 0o600 });
      try {
        const form = await page.evaluate(() => {
          const editor = document.forms.caseManagementEntryForm;
          if (!editor) return null;
          return { patient: String(window.demographicNo), provider: String(window.providerNo),
            note: String(editor.elements.noteId?.value), token: String(editor.elements.chartContext?.value || ''),
            csrf: window.CarlosAjax?.getCsrfToken() };
        });
        if (!form) { await page.close(); continue; }
        h.assert(form.patient === patient && form.provider === login.providerNo && form.csrf
          && Buffer.from(form.token.split('.')[0], 'base64url').toString('utf8') === `${patient}:${program}`
          && /^(?:0|-1|[1-9]\d*)$/.test(form.note), 'Owned chart cleanup identity changed');
        if (!['0', '-1'].includes(form.note)) h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note
          WHERE note_id=${form.note} AND demographic_no=${patient}`) === '1', 'Cleanup note belongs to another patient');
        const sessions = (await reader.context.cookies(config.baseUrl.href)).filter(cookie => cookie.name === 'JSESSIONID');
        h.assert(sessions.length === 1 && sessions[0].value, 'Owned chart cleanup has no unique authenticated session');
        const before = snapshot();
        const beforeLocks = lockRows();
        const allowed = new Set(beforeLocks.filter(row => row[1] === login.providerNo && row[2] === patient
          && row[3] === form.note && row[4] === sessions[0].value).map(row => row[0]));
        const accessBefore = sql.rows(`SELECT * FROM program_access WHERE program_id IN (${programs.join(',')}) ORDER BY id`);
        Object.assign(proof, { note: form.note, csrfPresent: true, contextMatches: true,
          sessionLockScopeVerified: true, permittedLockIds: [...allowed] });
        save();
        const response = await reader.context.request.post(h.appUrl(config.baseUrl,
          `/CaseManagementEntry?${new URLSearchParams({ chartContext: form.token })}`), {
          form: { method: 'releaseNoteLock', demographicNo: patient, providerNo: login.providerNo,
            noteId: form.note, 'CSRF-TOKEN': form.csrf }, maxRedirects: 0, timeout: 15000,
        });
        try {
          const bytes = await response.body();
          proof.status = response.status();
          proof.fromApplication = Object.hasOwn(response.headers(), h.APPLICATION_HEADER);
          proof.responseSHA256 = createHash('sha256').update(bytes).digest('hex');
          save();
          h.assert(proof.status === 200 && proof.fromApplication,
            `Owned chart lock cleanup returned HTTP ${proof.status}; ${file}`);
        } finally { await response.dispose(); }
        const after = snapshot();
        const { locks: ignoredBeforeLocks, ...clinicalBefore } = before;
        const { locks: ignoredAfterLocks, ...clinicalAfter } = after;
        assertPatientStateUnchanged(clinicalBefore, clinicalAfter, 'Owned chart lock cleanup');
        h.assert(JSON.stringify(lockRows()) === JSON.stringify(beforeLocks.filter(row => !allowed.has(row[0]))),
          'Owned chart cleanup changed another lock or failed to release its own session lock');
        h.assert(JSON.stringify(policy()) === originalPolicy
          && JSON.stringify(sql.rows(`SELECT * FROM provider_default_program
            WHERE provider_no=${q(provider)} ORDER BY program_id`)) === sharedDefaults
          && JSON.stringify(sql.rows(`SELECT * FROM program_access WHERE program_id IN (${programs.join(',')}) ORDER BY id`))
            === JSON.stringify(accessBefore), 'Owned chart cleanup changed note access policy');
        const acknowledged = await page.evaluate(expected => {
          const editor = document.forms.caseManagementEntryForm;
          if (!editor || String(window.demographicNo) !== expected.patient
              || String(window.providerNo) !== expected.provider || String(editor.elements.noteId?.value) !== expected.note
              || String(editor.elements.chartContext?.value || '') !== expected.token) return false;
          window.needToReleaseLock = false;
          return true;
        }, form);
        h.assert(acknowledged, 'Owned chart identity changed after lock cleanup acknowledgment');
        Object.assign(proof, { clinicalDraftPolicyUnchanged: true, onlyPermittedLocksRemoved: true,
          redundantUnloadReleaseSuppressed: true });
        save();
        await page.close();
        proof.pageClosed = true;
        save();
        console.log(`  Owned chart fixture cleanup ${JSON.stringify({ ...proof, artifact: file })}`);
      } catch (error) { proof.error = error.message; save(); failures.push(error); }
    }
    if (failures.length && workflowFailure) {
      workflowFailure.message += `; Owned chart cleanup: ${failures.map(error => error.message).join('; ')}`;
      throw workflowFailure;
    }
    h.assert(!failures.length, failures.map(error => error.message).join('; '));
  }
}

module.exports = { checkPrintProgramVisibility };
