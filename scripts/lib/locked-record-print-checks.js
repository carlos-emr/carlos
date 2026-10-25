/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

const { execFileSync } = require('node:child_process');
const { TextDecoder } = require('node:util');
const h = require('./playwright-harness');
const { signIn } = require('./authz-read-probe');
const { readPatientState, assertPatientStateUnchanged } = require('../echart-two-patient-isolation-playwright-checks');
const q = h.sqlString;

function snapshot(sql, patient) {
  return {
    ...readPatientState(sql, patient),
    locks: sql.rows(`SELECT * FROM casemgmt_note_lock WHERE demographic_no=${patient} ORDER BY id`),
  };
}

/** Print module grants do not override a separate demographic record exclusion. */
async function checkLockedRecordPrints(s, { fixture }) {
  const { sql, provider, marker, config } = s;
  const owner = `${marker}-PR`;
  const patients = [];
  const notes = [];
  const clinicalTokens = ['LOCKED', 'OPEN'].map(label => `${marker}-PRINT-${label}-NOTE`);
  const texts = clinicalTokens.map(token => `${token} H&P + café 中文 <b>literal markup</b>\nSecond line & retained`);

  // A shared fixture supplies unique role/login names. This helper only owns its
  // two new patients; register cleanup before creating either parent or child.
  s.cleanup(() => {
    for (const [index, patient] of patients.entries()) {
      h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient}
        AND last_name=${q(owner)}`) === '1', 'Print access fixture ownership changed');
      const allowed = new Set(['demographic', 'demographicExtensions', 'notes']);
      for (const [domain, rows] of Object.entries(snapshot(sql, patient))) {
        h.assert(allowed.has(domain) || rows.length === 0,
          `Print cleanup retained the patient because unexpected ${domain} rows need recovery`);
      }
      h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}
        ${notes[index] ? `AND note_id<>${notes[index]}` : ''}`) === '0',
      'Print cleanup retained the patient because unexpected notes need recovery');
    }
    // All parents and child domains are checked before deleting any fixture.
    for (const patient of patients) {
      const ownedNotes = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
      sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${ownedNotes});
        DELETE FROM casemgmt_note_ext WHERE note_id IN (${ownedNotes});
        DELETE FROM casemgmt_note_link WHERE note_id IN (${ownedNotes});
        DELETE FROM hash_audit WHERE type='enc' AND id IN (${ownedNotes});
        DELETE FROM casemgmt_note WHERE demographic_no=${patient};
        DELETE FROM casemgmt_tmpsave WHERE demographic_no=${patient};
        DELETE FROM casemgmt_note_lock WHERE demographic_no=${patient};
        DELETE FROM eChart WHERE demographicNo=${patient};
        DELETE FROM log WHERE demographic_no=${patient};
        DELETE FROM demographicExt WHERE demographic_no=${patient};
        DELETE FROM demographicArchive WHERE demographic_no=${patient};
        DELETE FROM demographic WHERE demographic_no=${patient} AND last_name=${q(owner)}`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient})
        + (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})
        + (SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no=${patient})
        + (SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${patient})
        + (SELECT COUNT(*) FROM eChart WHERE demographicNo=${patient})`) === '0',
      'The owned print access fixtures were not removed');
    }
  });
  const programRole = sql.rows(`SELECT program_id,role_id FROM program_provider
    WHERE provider_no=${q(provider)} AND role_id IS NOT NULL ORDER BY id LIMIT 1`)[0];
  h.assert(programRole && programRole.every(id => /^[1-9]\d*$/.test(id)),
    'Print access controls need the test provider\'s existing program and clinical role');
  for (const [index, label] of ['LockedPrint', 'OpenPrint'].entries()) {
    const patient = h.insertId(sql, `INSERT INTO demographic
      (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
       provider_no,hc_type,province,roster_status,lastUpdateDate)
      VALUES (${q(owner)},${q(label)},'1980','01','02','F','AC',${q(provider)},'ON','ON','NR',NOW())`,
    'print record-access patient');
    h.assert(/^[1-9]\d*$/.test(patient), 'Print access patient key is invalid');
    patients.push(patient);
    const note = h.insertId(sql, `INSERT INTO casemgmt_note
      (update_date,observation_date,demographic_no,provider_no,note,signed,signing_provider_no,
       encounter_type,program_no,reporter_caisi_role,history,uuid,locked,archived)
      VALUES (NOW(),NOW(),${patient},${q(provider)},${q(texts[index])},1,${q(provider)},'',
        ${q(programRole[0])},${q(programRole[1])},${q(texts[index])},UUID(),'0',0)`,
    'print record-access note');
    h.assert(/^[1-9]\d*$/.test(note), 'Print access note key is invalid');
    notes.push(note);
    // Complete legacy demographic normalization through an allowed owner read
    // before taking exact snapshots of subsequent clinical read operations.
    const initialized = await s.context.request.get(h.appUrl(config.baseUrl,
      `/demographic/DemographicEdit?demographic_no=${patient}`), { maxRedirects: 0 });
    try {
      h.assert(initialized.status() === 200 && Object.hasOwn(initialized.headers(), h.APPLICATION_HEADER),
        'The owner could not initialize its print access fixture');
    } finally { await initialized.dispose(); }
  }
  const [lockedPatient, openPatient] = patients;
  // Whole-chart print always asks TicklerManager for this patient's ticklers,
  // even when the result is empty; its module read gate must also be granted.
  const role = fixture.addRole({ _demographic: 'r', _appointment: 'r', _eChart: 'r',
    '_casemgmt.notes': 'r', _tickler: 'r' });
  const login = fixture.addLogin(role);
  fixture.joinTestLoginPrograms(login);
  h.assert(fixture.lockPatient(login, lockedPatient, ['_demographic']).length === 1,
    'The print record-only restriction was not installed');
  h.assert(sql.value(`SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${q(login.providerNo)}
    AND objectName IN (${q(`_demographic$${lockedPatient}`)},${q(`_eChart$${lockedPatient}`)})`) === '1'
    && sql.value(`SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${q(login.providerNo)}
      AND objectName=${q(`_demographic$${lockedPatient}`)} AND privilege='|o|'`) === '1',
  'The print restriction is not exactly one demographic exclusion');

  const routes = [
    { path: '/CaseManagementEntry', method: 'displayNotes', type: 'html' },
    { path: '/CaseManagementEntry', method: 'print', type: 'pdf' },
    { path: '/OscarChartPrint', type: 'pdf' },
  ];
  const address = (route, parameters) => {
    const query = new URLSearchParams({ ...(route.method ? { method: route.method } : {}),
      notes2print: notes[1], printCPP: 'false', printRx: 'false',
      printLabs: 'false', printPreventions: 'false', printAllergies: 'false' });
    for (const [name, value] of parameters) {
      if (name === 'notes2print') query.set(name, value);
      else query.append(name, value);
    }
    return h.appUrl(config.baseUrl, `${route.path}?${query}`);
  };
  const state = () => patients.map(patient => snapshot(sql, patient));
  const unchanged = (before, label) => patients.forEach((patient, index) =>
    assertPatientStateUnchanged(before[index], snapshot(sql, patient), label));
  const read = (reader, url) => reader.context.request.get(url,
    { maxRedirects: 0, timeout: 40000, failOnStatusCode: false });
  const refuse = async (response, status, label) => {
    const bytes = await response.body();
    const body = new TextDecoder('utf-8', { fatal: true }).decode(bytes);
    h.assert(response.status() === status && Object.hasOwn(response.headers(), h.APPLICATION_HEADER)
      && !h.isWafPage(response.status(), body),
    `${label}: expected application HTTP ${status}, received ${response.status()}`);
    h.assert(!clinicalTokens.some(token => body.includes(token))
      && bytes.subarray(0, 5).toString('latin1') !== '%PDF-', `${label}: refused print leaked clinical text or PDF bytes`);
  };

  await s.step('all three chart print routes require record access despite complete print module grants', async () => {
    for (const route of routes) {
      // A downstream demographic lookup can set accountLocked. A fresh real
      // login for each route keeps one denial from hiding another missing gate.
      const reader = await signIn(s, login);
      try {
        const before = state();
        const allowed = await read(reader, address(route, [['demographicNo', openPatient]]));
        try {
          const headers = allowed.headers();
          const bytes = await allowed.body();
          h.assert(allowed.status() === 200 && Object.hasOwn(headers, h.APPLICATION_HEADER),
            `${route.method || route.path}: the open print control was not served by the application`);
          if (route.type === 'html') {
            h.assert(/text\/html\s*;\s*charset=UTF-8/i.test(headers['content-type'] || ''),
              'The HTML note print does not declare UTF-8');
            const html = new TextDecoder('utf-8', { fatal: true }).decode(bytes);
            const rendered = await reader.page.evaluate(source => {
              const document = new DOMParser().parseFromString(source, 'text/html');
              for (const br of document.querySelectorAll('br')) br.replaceWith('\n');
              return { text: document.body.textContent, markup: Boolean(document.querySelector('b')) };
            }, html);
            h.assert(rendered.text.includes(texts[1]) && !rendered.markup && html.includes('&lt;b&gt;')
              && html.includes('&amp;') && !html.includes(clinicalTokens[0]),
            'The HTML note print lost UTF-8/literal markup or included the other patient');
          } else {
            h.assert(/application\/pdf/i.test(headers['content-type'] || '') && bytes.length > 1000
              && bytes.subarray(0, 5).toString('latin1') === '%PDF-'
              && /%%EOF\s*$/.test(bytes.subarray(-1024).toString('latin1')),
            `${route.method || route.path}: the print control is not a complete PDF`);
            const text = execFileSync('pdftotext', ['-', '-'], {
              input: bytes, encoding: 'utf8', timeout: 20000, maxBuffer: 8 << 20,
            });
            h.assert(text.includes(clinicalTokens[1]) && text.includes('café') && text.includes('中文')
              && !text.includes(clinicalTokens[0]),
            `${route.method || route.path}: the actual PDF omitted its owned note or contained another patient`);
          }
        } finally { await allowed.dispose(); }
        unchanged(before, `${route.method || route.path} allowed read`);
        const denied = await read(reader, address(route,
          [['demographicNo', lockedPatient], ['notes2print', notes[0]]]));
        try { await refuse(denied, 403, route.method || route.path); }
        finally { await denied.dispose(); }
        unchanged(before, `${route.method || route.path} denied read`);
      } finally { await reader.context.close(); }
    }
  });

  await s.step('print patient identities reject malformed or conflicting values before any clinical read', async () => {
    const invalid = [
      ['missing', []], ['malformed', [['demographicNo', 'not-a-patient']]],
      ['overflow', [['demographicNo', '2147483648']]],
      ['conflicting aliases', [['demographicNo', openPatient], ['demographic_no', lockedPatient]]],
      ['conflicting repeats', [['demographicNo', openPatient], ['demographicNo', lockedPatient]]],
    ];
    for (const route of routes) {
      const reader = await signIn(s, login);
      try {
        const before = state();
        for (const [label, parameters] of invalid) {
          const response = await read(reader, address(route, parameters));
          try { await refuse(response, 400, `${route.method || route.path} ${label}`); }
          finally { await response.dispose(); }
          unchanged(before, `${route.method || route.path} ${label}`);
        }
      } finally { await reader.context.close(); }
    }
  });

  await s.step('HTML note selection refuses foreign or mixed patient rows without partial output', async () => {
    const reader = await signIn(s, login);
    try {
      const before = state();
      for (const selected of [notes[0], `${notes[1]},${notes[0]}`, `${notes[0]},${notes[1]}`]) {
        const url = new URL(address(routes[0], [['demographicNo', openPatient]]));
        url.searchParams.set('notes2print', selected);
        const response = await read(reader, url.href);
        try { await refuse(response, 403, 'Foreign/mixed HTML note selection'); }
        finally { await response.dispose(); }
        unchanged(before, 'Foreign/mixed HTML note selection');
      }
    } finally { await reader.context.close(); }
  });
}

module.exports = { checkLockedRecordPrints };
