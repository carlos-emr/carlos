/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const fs = require('node:fs');
const { createHash } = require('node:crypto');
const h = require('./playwright-harness');
const { signIn } = require('./authz-read-probe');
const { waitForNavbars } = require('../echart-navbar-modules-playwright-checks');
const { readPatientState, assertPatientStateUnchanged } = require('../echart-two-patient-isolation-playwright-checks');
const q = h.sqlString;

function startupCases(program) {
  h.assert(/^(?:0|[1-9]\d*)$/.test(String(program)), 'Invalid startup program fixture');
  return [{ label: 'default', parameters: {} }, ...['programId', 'case_program_id'].map(alias => ({
    label: alias, parameters: { [alias]: String(program) },
  }))];
}

function requireOwnedForm(form, patient, provider, program) {
  h.assert(form && form.patient === patient && form.provider === provider && form.csrf
    && Buffer.from(form.token.split('.')[0], 'base64url').toString('utf8') === `${patient}:${program}`
    && ['0', '-1'].includes(form.note), 'Startup form is not the expected owned empty-note chart');
}

/** Real first-chart startup, including the supported provider with no programs. */
async function checkChartProgramStartup(s, { fixture, program, role, facility, sharedState }) {
  const { sql, config, marker } = s;
  for (const [kind, chosen] of [['configured', String(program)], ['programless', '0']]) {
    const login = fixture.addLogin('doctor');
    const owner = `${marker}-${kind === 'configured' ? 'SC' : 'SP'}`;
    let patient;
    let baseline;
    let defaults;
    let membership;
    let ownsDefaults = false;
    const locks = () => sql.rows(`SELECT id,provider_no,demographic_no,note_id,session_id,
      lock_acquired,ip_address FROM casemgmt_note_lock WHERE demographic_no=${patient} ORDER BY id`);
    const metadata = () => ({
      defaults: sql.rows(`SELECT id,provider_no,program_id,signnote FROM provider_default_program WHERE provider_no=${q(login.providerNo)} ORDER BY id`),
      membership: sql.rows(`SELECT id,program_id,provider_no,role_id,team_id FROM program_provider WHERE provider_no=${q(login.providerNo)} ORDER BY id`),
      facilities: sql.rows(`SELECT provider_no,facility_id FROM provider_facility WHERE provider_no=${q(login.providerNo)} ORDER BY facility_id`),
    });
    let stagedMetadata;
    // Registration precedes every fixture write and native request. A failed
    // INSERT is recovered only by its unique marker/provider ownership.
    s.cleanup(() => {
      const recovered = sql.rows(`SELECT demographic_no FROM demographic WHERE last_name=${q(owner)}
        AND first_name='Startup' AND provider_no=${q(login.providerNo)}`);
      h.assert(recovered.length <= 1 && (!patient || recovered.length === 1 && recovered[0][0] === patient),
        'Startup patient recovery is ambiguous or its owner changed');
      patient ||= recovered[0]?.[0];
      if (stagedMetadata) h.assert(JSON.stringify(metadata()) === JSON.stringify(stagedMetadata),
        'Startup provider metadata changed; retaining its fixtures');
      if (patient) {
        const state = readPatientState(sql, patient);
        if (baseline) assertPatientStateUnchanged(baseline, state, 'Startup fixture cleanup');
        else for (const [domain, rows] of Object.entries(state)) h.assert(
          ['demographic', 'demographicExtensions'].includes(domain) || rows.length === 0,
          `Unexpected startup ${domain} rows; retaining fixture`);
        h.assert(locks().length === 0, 'Startup fixture still has a session lock; retaining fixture');
      }
      const recoveredMetadata = metadata();
      h.assert(recoveredMetadata.facilities.length <= 1 && recoveredMetadata.facilities.every(row =>
        row[0] === login.providerNo && row[1] === String(facility)), 'Startup facility ownership changed');
      if (chosen === '0') h.assert(recoveredMetadata.membership.length === 0,
        'The programless startup provider acquired a program; retaining fixture');
      if (chosen !== '0') h.assert(membership
        ? JSON.stringify(recoveredMetadata.membership) === JSON.stringify(membership)
        : recoveredMetadata.membership.length <= 1 && recoveredMetadata.membership.every(row =>
          row[1] === String(program) && row[2] === login.providerNo && row[3] === String(role) && row[4] === '0'),
      'Startup membership ownership changed');
      if (ownsDefaults) {
        const actual = metadata().defaults;
        h.assert(defaults ? JSON.stringify(actual) === JSON.stringify(defaults)
          : actual.length <= 1 && actual.every(row => row[1] === login.providerNo && row[2] === '0' && row[3] === '0'),
        'Startup default ownership changed');
        sql.execute(`DELETE FROM provider_default_program WHERE provider_no=${q(login.providerNo)}`);
      }
      if (chosen !== '0') {
        const actual = metadata().membership;
        h.assert(membership ? JSON.stringify(actual) === JSON.stringify(membership)
          : actual.length <= 1 && actual.every(row => row[1] === String(program) && row[2] === login.providerNo
            && row[3] === String(role) && row[4] === '0'), 'Startup membership ownership changed');
        sql.execute(`DELETE FROM program_provider WHERE provider_no=${q(login.providerNo)} AND program_id=${program}`);
      }
      if (patient) {
        sql.execute(`DELETE FROM log WHERE demographic_no=${patient};
          DELETE FROM demographicExt WHERE demographic_no=${patient};
          DELETE FROM demographicArchive WHERE demographic_no=${patient};
          DELETE FROM demographic WHERE demographic_no=${patient} AND last_name=${q(owner)}
            AND provider_no=${q(login.providerNo)}`);
        h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient}`) === '0',
          'Startup patient survived owned cleanup');
      }
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM provider_default_program WHERE provider_no=${q(login.providerNo)})
        + (SELECT COUNT(*) FROM program_provider WHERE provider_no=${q(login.providerNo)})`) === '0',
      'Startup program metadata survived cleanup');
    });
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE last_name=${q(owner)}`) === '0',
      'Startup patient marker already exists');
    h.assert(sql.value(`SELECT COUNT(*) FROM provider_default_program WHERE provider_no=${q(login.providerNo)}`) === '0'
      && metadata().membership.length === 0 && metadata().facilities.length === 0,
    'Startup login has preexisting program metadata');
    ownsDefaults = true;
    sql.execute(`INSERT INTO provider_default_program(provider_no,program_id,signnote)
      VALUES (${q(login.providerNo)},0,0)`);
    defaults = metadata().defaults;
    h.assert(defaults.length === 1, 'Startup default signature fixture is ambiguous');
    sql.execute(`INSERT INTO provider_facility(provider_no,facility_id) VALUES (${q(login.providerNo)},${facility})`);
    if (chosen !== '0') {
      sql.execute(`INSERT INTO program_provider(program_id,provider_no,role_id,team_id)
        VALUES (${program},${q(login.providerNo)},${role},0)`);
      membership = metadata().membership;
      h.assert(membership.length === 1, 'Configured startup needs exactly one available program');
    }
    stagedMetadata = metadata();
    patient = h.insertId(sql, `INSERT INTO demographic
      (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
       provider_no,hc_type,province,roster_status,lastUpdateDate)
      VALUES (${q(owner)},'Startup','1980','01','02','F','AC',${q(login.providerNo)},'ON','ON','NR',NOW())`,
    'owned first-chart patient');
    // An explicit owner read completes legacy demographic normalization before
    // startup's exact snapshots. No later clinical change is allowlisted.
    const normalized = await s.context.request.get(h.appUrl(config.baseUrl,
      `/demographic/DemographicEdit?demographic_no=${patient}`), { maxRedirects: 0, timeout: 30000 });
    try { h.assert(normalized.status() === 200 && Object.hasOwn(normalized.headers(), h.APPLICATION_HEADER),
      'The owner could not normalize its startup fixture'); }
    finally { await normalized.dispose(); }
    baseline = readPatientState(sql, patient);
    h.assert(locks().length === 0 && sql.value(`SELECT COUNT(*) FROM admission WHERE client_id=${patient}`) === '0',
      'The startup patient already has a session lock or program admission');
    const sharedBefore = JSON.stringify(sharedState());
    const reader = await signIn(s, login);
    s.cleanup(() => reader.context.close());
    reader.context.on('page', page => h.wireStrictPage(page, `startup-${kind}`, s.recorder, { baseline: [] }));
    const evidenceDir = fs.mkdtempSync('/tmp/carlos-chart-startup-');
    for (const [index, test] of startupCases(chosen).entries()) {
      await s.step(`${kind} first chart starts with ${test.label} program selection`, async () => {
        const page = await reader.context.newPage();
        let failure;
        let form;
        const file = `${evidenceDir}/${index}.json`;
        const proof = { kind, selection: test.label, patient, provider: login.providerNo, expectedProgram: chosen };
        const save = () => fs.writeFileSync(file, JSON.stringify(proof, null, 2) + '\n', { mode: 0o600 });
        save();
        try {
          const response = await h.gotoApp(page, config.baseUrl, `/encounter/IncomingEncounter?${new URLSearchParams({
            demographicNo: patient, providerNo: login.providerNo, ...test.parameters,
          })}`);
          h.assert(response && response.status() === 200 && Object.hasOwn(response.headers(), h.APPLICATION_HEADER),
            `${kind}/${test.label}: native startup did not reach application200`);
          await waitForNavbars(page, 90000);
          await page.waitForFunction(() => !window.autoSaveXhr && !window.autoSaveOperation);
          await page.evaluate(() => { if (typeof clearAutoSaveTimer === 'function') clearAutoSaveTimer(); });
          form = await page.evaluate(() => {
            const editor = document.forms.caseManagementEntryForm;
            return editor && { patient: String(window.demographicNo), provider: String(window.providerNo),
              note: String(editor.elements.noteId?.value), token: String(editor.elements.chartContext?.value || ''),
              csrf: window.CarlosAjax?.getCsrfToken() };
          });
          requireOwnedForm(form, patient, login.providerNo, chosen);
          h.assert(await page.locator('#caseManagementEntryForm textarea').count() > 0,
            'Native startup did not create its actual note editor');
          assertPatientStateUnchanged(baseline, readPatientState(sql, patient), `${kind}/${test.label} startup`);
          h.assert(JSON.stringify(metadata()) === JSON.stringify(stagedMetadata)
            && JSON.stringify(sharedState()) === sharedBefore, 'Native startup changed program/default policy');
          Object.assign(proof, { application200: true, contextMatches: true, nativeEditorReady: true });
          save();
        } catch (error) { failure = error; }
        finally {
          try {
            // Recover the same owned form even if readiness/assertions failed.
            form ||= await page.evaluate(() => {
              const editor = document.forms.caseManagementEntryForm;
              return editor && { patient: String(window.demographicNo), provider: String(window.providerNo),
                note: String(editor.elements.noteId?.value), token: String(editor.elements.chartContext?.value || ''),
                csrf: window.CarlosAjax?.getCsrfToken() };
            });
            if (form) {
              requireOwnedForm(form, patient, login.providerNo, chosen);
              const sessions = (await reader.context.cookies(config.baseUrl.href)).filter(cookie => cookie.name === 'JSESSIONID');
              h.assert(sessions.length === 1 && sessions[0].value, 'Startup cleanup has no unique authenticated session');
              const sessionIds = new Set([sessions[0].value, sessions[0].value.split('.')[0]]);
              const beforeLocks = locks();
              h.assert(beforeLocks.every(row => row[1] === login.providerNo && row[2] === patient
                && row[3] === form.note && sessionIds.has(row[4])), 'Startup acquired an unrelated lock');
              const release = await reader.context.request.post(h.appUrl(config.baseUrl,
                `/CaseManagementEntry?${new URLSearchParams({ chartContext: form.token })}`), {
                form: { method: 'releaseNoteLock', demographicNo: patient, providerNo: login.providerNo,
                  noteId: form.note, 'CSRF-TOKEN': form.csrf }, maxRedirects: 0, timeout: 15000,
              });
              try {
                const bytes = await release.body();
                Object.assign(proof, { cleanupStatus: release.status(),
                  cleanupApplication: Object.hasOwn(release.headers(), h.APPLICATION_HEADER),
                  cleanupSHA256: createHash('sha256').update(bytes).digest('hex'), allowedLockIds: beforeLocks.map(row => row[0]) });
                save();
                h.assert(proof.cleanupStatus === 200 && proof.cleanupApplication, 'Startup lock release was not acknowledged');
              } finally { await release.dispose(); }
              h.assert(locks().length === 0, 'Startup owned lock survived its acknowledged release');
              assertPatientStateUnchanged(baseline, readPatientState(sql, patient), 'Startup lock cleanup');
              h.assert(JSON.stringify(metadata()) === JSON.stringify(stagedMetadata)
                && JSON.stringify(sharedState()) === sharedBefore, 'Startup cleanup changed program/default policy');
              const same = await page.evaluate(expected => {
                const editor = document.forms.caseManagementEntryForm;
                if (!editor || String(window.demographicNo) !== expected.patient || String(window.providerNo) !== expected.provider
                    || String(editor.elements.noteId?.value) !== expected.note
                    || String(editor.elements.chartContext?.value || '') !== expected.token) return false;
                window.needToReleaseLock = false;
                return true;
              }, form);
              h.assert(same, 'Startup form changed after its lock-release acknowledgment');
              proof.clinicalDraftPolicyUnchanged = true;
              save();
            } else h.assert(locks().length === 0, 'Failed startup has an unrecoverable owned lock');
            await page.close();
          } catch (error) {
            if (failure) failure.message += `; Startup cleanup: ${error.message}`;
            else failure = error;
          }
        }
        if (failure) { proof.failure = failure.message; save(); throw failure; }
        h.assertStrictPage(s.recorder);
        console.log(`  Native startup proof ${JSON.stringify({ kind, selection: test.label, artifact: file })}`);
      });
    }
  }
}
module.exports = { checkChartProgramStartup, startupCases, requireOwnedForm };
