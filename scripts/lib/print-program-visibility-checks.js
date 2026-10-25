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
  const actualPdf = async (label, extra, expectedKeys, route) => {
    const response = await reader.context.request.get(address(extra, route), { maxRedirects: 0, timeout: 40000 });
    try {
      const bytes = await response.body();
      h.assert(response.status() === 200 && Object.hasOwn(response.headers(), h.APPLICATION_HEADER)
        && /application\/pdf/i.test(response.headers()['content-type'] || '') && bytes.length > 1000
        && bytes.subarray(0, 5).toString('latin1') === '%PDF-'
        && /%%EOF\s*$/.test(bytes.subarray(-1024).toString('latin1')), `${label}: no complete application PDF`);
      const text = execFileSync('pdftotext', ['-', '-'], { input: bytes, encoding: 'utf8', timeout: 20000 });
      const unexpectedKeys = Object.keys(bodies).filter(key => !expectedKeys.includes(key));
      h.assert(expectedKeys.every(key => text.includes(clinicalToken(bodies[key])))
        && !unexpectedKeys.some(key => text.includes(clinicalToken(bodies[key])))
        && text.includes('café') && text.includes('中文'),
      `${label}: PDF visibility disagrees with the native chart`);
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
        const fromApplication = Object.hasOwn(response.headers(), h.APPLICATION_HEADER);
        const isPdf = bytes.subarray(0, 5).toString('latin1') === '%PDF-';
        const hasClinicalText = Object.values(bodies).some(text => body.includes(clinicalToken(text)));
        h.assert(response.status() === 403 && fromApplication && !isPdf && !hasClinicalText,
          `Hidden explicit selection: HTTP ${response.status()}, application=${fromApplication}, PDF=${isPdf}, clinical=${hasClinicalText}`);
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
        h.assert(response.status() === expected && Object.hasOwn(response.headers(), h.APPLICATION_HEADER),
          `${label}: REST print answered HTTP ${response.status()}, expected ${expected}`);
        if (expected === 200) {
          h.assert(/application\/pdf/i.test(response.headers()['content-type'] || '') && bytes.length > 1000
            && bytes.subarray(0, 5).toString('latin1') === '%PDF-'
            && /%%EOF\s*$/.test(bytes.subarray(-1024).toString('latin1')), `${label}: no complete REST PDF`);
          const text = execFileSync('pdftotext', ['-', '-'], { input: bytes, encoding: 'utf8', timeout: 20000 });
          const unexpectedKeys = Object.keys(bodies).filter(key => !allowedKeys.includes(key));
          h.assert(allowedKeys.every(key => text.includes(clinicalToken(bodies[key])))
            && !unexpectedKeys.some(key => text.includes(clinicalToken(bodies[key]))),
          `${label}: REST PDF used another visibility context or note selection`);
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
  } finally {
    // Logout broadcasts navigation to open charts. Retire only these owned
    // documents while their session still exists. Chromium abandons keepalive
    // ping responses on navigation: forward the exact original native request
    // and retain its real server acknowledgment before classifying that abort.
    const failures = [];
    const auditDir = fs.mkdtempSync('/tmp/carlos-pdf-native-retirement-');
    for (const [chartIndex, { page, program }] of nativeCharts.entries()) {
      if (page.isClosed()) continue;
      let active = true;
      let capturing = true;
      let routeInstalled = false;
      const matched = new Map();
      const pending = [];
      const associationErrors = [];
      let previousFailureCount = s.recorder.requestFailures.length;
      const mappedEntries = new Set();
      let terminalWait;
      const waitForTerminals = () => {
        if (!terminalWait) terminalWait = (async () => {
          let timer;
          try {
            await Promise.race([Promise.all([...matched.values()].map(proof => proof.waitForTerminal)),
              new Promise((_, reject) => {
                timer = setTimeout(() => reject(new Error('An owned native request has no terminal browser event')), 15000);
              })]);
          } finally { clearTimeout(timer); }
        })();
        return terminalWait;
      };
      const observeFinished = request => {
        const proof = matched.get(request);
        if (active && proof) { proof.terminal = 'finished'; proof.resolveTerminal(); }
      };
      const observeFailure = request => {
        const count = s.recorder.requestFailures.length;
        const match = matched.get(request);
        if (active && match) {
          const entry = s.recorder.requestFailures[count - 1];
          if (count !== previousFailureCount + 1 || !entry || mappedEntries.has(entry)
              || entry.url !== request.url() || entry.resourceType !== request.resourceType()
              || entry.errorText !== request.failure()?.errorText) {
            associationErrors.push('Native request failure could not be uniquely associated with its strict recorder entry');
          } else { match.entry = entry; mappedEntries.add(entry); }
          match.terminal = 'failed';
          match.resolveTerminal();
        }
        previousFailureCount = count;
      };
      const endpoint = new URL(h.appUrl(config.baseUrl, '/CaseManagementEntry'));
      const routePattern = url => url.origin === endpoint.origin && url.pathname === endpoint.pathname;
      let routeHandler;
      let expectedBeacons = 0;
      let allAppeared;
      let appeared;
      page.on('requestfailed', observeFailure); // Core strict page wiring was installed first.
      page.on('requestfinished', observeFinished);
      try {
        const form = await page.evaluate(() => {
          const editor = document.forms.caseManagementEntryForm;
          if (!editor) return null;
          return { patient: String(window.demographicNo), provider: String(window.providerNo),
            note: String(editor.elements.noteId?.value), token: String(editor.elements.chartContext?.value || ''),
            csrf: window.CarlosAjax?.getCsrfToken(),
            registrations: [...document.scripts].filter(script => script.src
              && new URL(script.src, location.href).pathname.endsWith('/js/newCaseManagementView.js.jsp')).length,
            release: Boolean(window.needToReleaseLock && editor.elements.noteId) };
        });
        if (form) {
          h.assert(form.patient === patient && form.provider === login.providerNo && form.csrf
            && Buffer.from(form.token.split('.')[0], 'base64url').toString('utf8') === `${patient}:${program}`
            && /^-?\d+$/.test(form.note) && [1, 2].includes(form.registrations),
          'Native PDF chart cleanup identity or handler count changed');
          expectedBeacons = form.release ? form.registrations : 0;
          appeared = new Promise(resolve => { allAppeared = resolve; });
          routeHandler = async route => {
            const request = route.request();
            const url = new URL(request.url());
            const body = new URLSearchParams(request.postData() || '');
            let ownPage = false;
            try { ownPage = request.frame().page() === page; } catch { /* Unknown origin stays strict. */ }
            const exact = form.release && ownPage && request.method() === 'POST'
              && request.resourceType() === 'ping'
              && /^application\/x-www-form-urlencoded(?:;|$)/i.test(request.headers()['content-type'] || '')
              && [...url.searchParams.keys()].length === 1
              && url.searchParams.getAll('chartContext').length === 1
              && url.searchParams.get('chartContext') === form.token
              && [...body.keys()].length === 5
              && ['method', 'demographicNo', 'providerNo', 'noteId', 'CSRF-TOKEN'].every(name => body.getAll(name).length === 1)
              && body.get('method') === 'releaseNoteLock' && body.get('demographicNo') === patient
              && body.get('providerNo') === login.providerNo && body.get('noteId') === form.note
              && body.get('CSRF-TOKEN') === form.csrf;
            if (!exact) { await route.continue(); return; }
            if (!capturing) {
              failures.push(new Error('An additional owned native beacon arrived after the captured handler count was sealed'));
              await route.continue(); return;
            }
            let resolveTerminal;
            const waitForTerminal = new Promise(resolve => { resolveTerminal = resolve; });
            const proof = { request, entry: null, serverStatus: null, fromApplication: false,
              terminal: null, waitForTerminal, resolveTerminal,
              method: 'releaseNoteLock', patient, program, provider: login.providerNo, note: form.note,
              csrfMatches: true, contextMatches: true,
              bodySHA256: createHash('sha256').update(request.postData()).digest('hex') };
            matched.set(request, proof);
            const forwarding = (async () => {
              h.assert(matched.size <= expectedBeacons && matched.size <= 2,
                'More than the captured baseline native lock-release handlers ran');
              const response = await route.fetch({ maxRedirects: 0, timeout: 15000 });
              try {
                proof.serverStatus = response.status();
                proof.fromApplication = Object.hasOwn(response.headers(), h.APPLICATION_HEADER);
                h.assert(proof.serverStatus === 200 && proof.fromApplication,
                  `Owned native chart unload returned HTTP ${proof.serverStatus}`);
                // No request URL, body, headers, cookies or CSRF value is changed.
                await route.fulfill({ response });
              } finally { await response.dispose(); }
            })();
            pending.push(forwarding);
            if (matched.size === expectedBeacons) allAppeared();
            try { await forwarding; } catch (error) { failures.push(error); await route.abort().catch(() => {}); }
          };
          await reader.context.route(routePattern, routeHandler);
          routeInstalled = true;
        }
        // A known existing same-origin static image avoids extra clinical reads.
        await page.goto(h.appUrl(config.baseUrl, '/images/empty.gif'));
        if (expectedBeacons) {
          let timer;
          try {
            await Promise.race([appeared, new Promise((_, reject) => {
              timer = setTimeout(() => reject(new Error('Not all owned native lock-release handlers were observed')), 15000);
            })]);
          } finally { clearTimeout(timer); }
        }
        capturing = false;
        await Promise.allSettled(pending);
        await page.close();
        await Promise.allSettled(pending);
        await waitForTerminals();
        h.assert(matched.size === expectedBeacons && matched.size <= 2,
          'The owned native chart did not send its expected lock-release beacon');
        h.assert(!associationErrors.length, associationErrors.join('; '));
        for (const proof of matched.values()) {
          h.assert(proof.serverStatus === 200 && proof.fromApplication
            && ['finished', 'failed'].includes(proof.terminal),
            'Native retirement has no successful application acknowledgment');
          const entry = proof.entry;
          const classified = Boolean(entry);
          if (entry) h.assert(proof.request.resourceType() === 'ping'
            && entry.errorText === 'net::ERR_ABORTED' && typeof entry.navigatedAway === 'function'
            && entry.navigatedAway(), 'An owned native request failed without proven document abandonment');
          const { request, entry: rawStrictEntry, waitForTerminal, resolveTerminal, ...safeProof } = proof;
          const audit = { phase: 'authenticated owned chart retirement', ...safeProof,
            classified, rawStrictEntry: rawStrictEntry || null };
          // Preserve the complete strict entry privately BEFORE removing only
          // that exact object from active findings. Console/HTTP errors remain.
          const artifact = `${auditDir}/chart-${chartIndex}-request-${[...matched.keys()].indexOf(request)}.json`;
          fs.writeFileSync(artifact, JSON.stringify(audit, null, 2) + '\n', { mode: 0o600 });
          (s.recorder.classifiedNativeChartDepartures ||= []).push({ artifact, ...safeProof, classified });
          console.log(`  Native chart retirement ${JSON.stringify({ ...safeProof, classified, artifact })}`);
          if (entry) {
            const index = s.recorder.requestFailures.indexOf(entry);
            h.assert(index >= 0, 'The exact native strict failure entry disappeared before classification');
            s.recorder.requestFailures.splice(index, 1);
          }
        }
      } catch (error) { failures.push(error); }
      finally {
        capturing = false;
        await Promise.allSettled(pending);
        try { await waitForTerminals(); } catch (error) { failures.push(error); }
        active = false;
        try {
          if (routeInstalled) await reader.context.unroute(routePattern, routeHandler);
        } catch (error) { failures.push(error); }
        page.off('requestfailed', observeFailure);
        page.off('requestfinished', observeFinished);
      }
    }
    h.assert(!failures.length, failures.map(error => error.message).join('; '));
  }
}

module.exports = { checkPrintProgramVisibility };
