/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const vm = require('node:vm');
const { correlatedDenial, validateSubmission, logCheckpoint, readCheckpoint, ownedState,
  proveWafNegative, transitionToClinicalValues, assertPersistedLabValues } = require('./lib/eform-ap-probe');

const marker = 'FAKE-PW0123456789abcdef';
const values = ['-4.25', '<5', '1.2e-3', '+2.5E+4', '≤0.5', '1:40', 'not detected / below limit',
  '"><img id="pr5044-lab-injected" onerror="window.__pr5044LabInjected=true"><script id="pr5044-lab-script">x</script>',
  'literal ${labvalues} and ${patient_name}'];
const scope = { baseUrl: 'https://127.0.0.1/carlos', fid: '21', patient: '3086', provider: '999998', marker,
  expected: values.map((value, index) => ({ measurement: String(101 + index), identifier: `${marker}-lab-${index}`, value })) };
const uri = '/carlos/eform/addEForm?efmfid=21&efmdemographic_no=3086&efmprovider_no=999998';
const action = `https://127.0.0.1${uri}`;
const referer = 'https://127.0.0.1/carlos/eform/efmformadd_data?fid=21&demographic_no=3086';
const jsonValues = rows => JSON.stringify(rows.map(row => ({ loincCode: row.identifier, measurementValue: row.value })));
const submission = () => ({ action, method: 'post', entries: [
  ['CSRF-TOKEN', 'genuine-secret-not-for-log'], ['carlosEformSubmission', 'genuine-submission-not-for-log'],
  ['newForm', 'true'], ['playwright_latest_lab_values', jsonValues(scope.expected)],
  ['checkbox', 'a'], ['checkbox', 'b'],
] });

function transaction(token, options = {}) {
  const tx = { unique_id: '179243572323.079671', request: { method: 'POST', uri,
    headers: { 'X-CARLOS-AP-Probe': token } }, response: { http_code: 403 },
  messages: ['932130', '941110', '949110'].map(ruleId => ({ details: { ruleId,
    data: 'matched within ARGS:playwright_latest_lab_values' } })) };
  return Object.assign(tx, options);
}
const audit = tx => JSON.stringify({ transaction: tx }) + '\n';
const errors = id => `Access denied with code 403 (phase 2) [id "949110"] [unique_id "${id}"]\n`;

function mockSql() {
  let expected = scope.expected.map(row => ({ ...row }));
  const writes = [];
  const state = { forms: [], values: [], owner: '1', changed: false, duplicate: false, refuseUpdate: false };
  return {
    state, writes,
    rows(query, parameters) {
      if (query.includes('FROM measurements m')) {
        assert.deepEqual(parameters, [scope.patient]);
        const rows = expected.map(row => [row.measurement, scope.patient, scope.provider, 'PWLB', row.value, marker, 'identifier', row.identifier]);
        if (state.changed) rows[7][4] = 'foreign change';
        if (state.duplicate) rows.push(rows[0]);
        return rows;
      }
      if (query.includes('SELECT fdid,fid')) return state.forms;
      if (query.includes('SELECT v.var_value')) return state.savedRows || [[jsonValues(expected)]];
      if (query.includes('SELECT v.*')) {
        assert(query.includes('v.demographic_no=? OR v.fdid IN'), 'orphan patient-keyed values must be checked');
        return state.values;
      }
      throw new Error('Unexpected SQL');
    },
    value(query, parameters) {
      assert(query.includes('BINARY last_name=BINARY ?'));
      assert.deepEqual(parameters, [scope.patient, marker, scope.provider]);
      return state.owner;
    },
    execute(query, parameters) {
      writes.push({ query, parameters });
      assert(!query.includes(';'), 'only one bound UPDATE per connection');
      for (const key of ['m.id=?', 'm.demographicNo=?', 'm.providerNo=?', "m.type='PWLB'", 'BINARY m.dataField=BINARY ?',
        'BINARY m.comments=BINARY ?', 'BINARY d.last_name=BINARY ?', 'd.provider_no=?', "x.keyval='identifier'", 'BINARY x.val=BINARY ?']) {
        assert(query.includes(key), `missing exact fixture predicate ${key}`);
      }
      const [value, id, patient, provider, old, comment, lastName, owner, identifier] = parameters;
      assert.equal(patient, scope.patient); assert.equal(provider, scope.provider);
      assert.equal(comment, marker); assert.equal(lastName, marker); assert.equal(owner, scope.provider);
      const row = expected.find(row => row.measurement === id && row.value === old && row.identifier === identifier);
      assert(row);
      if (!state.refuseUpdate) row.value = value;
    },
  };
}

test('real successful controls preserve duplicate entries, Unicode and exact literal JSON', () => {
  const parsed = validateSubmission(submission(), scope);
  assert.equal(parsed.target, action);
  const body = new URLSearchParams(parsed.body);
  assert.deepEqual(body.getAll('checkbox'), ['a', 'b']);
  assert.deepEqual(JSON.parse(body.get('playwright_latest_lab_values')), JSON.parse(jsonValues(scope.expected)));
});

for (const [name, change] of [
  ['foreign origin', s => { s.action = s.action.replace('127.0.0.1', 'example.invalid'); }],
  ['wrong patient', s => { s.action = s.action.replace('3086', '1'); }],
  ['duplicate route identity', s => { s.action += '&efmfid=21'; }],
  ['another workflow route', s => { s.action += '&eform_link=foreign'; }],
  ['conflicting body patient', s => { s.entries.push(['efmdemographic_no', '1']); }],
  ['missing CSRF', s => { s.entries = s.entries.filter(([name]) => name !== 'CSRF-TOKEN'); }],
  ['duplicate submission token', s => { s.entries.push(['carlosEformSubmission', 'other']); }],
  ['missing newForm default', s => { s.entries = s.entries.filter(([name]) => name !== 'newForm'); }],
  ['fax transport', s => { s.entries.push(['faxEForm', 'true']); }],
  ['changed clinical JSON', s => { s.entries.find(([name]) => name === 'playwright_latest_lab_values')[1] = '[]'; }],
]) test(`submission refuses ${name}`, () => {
  const s = submission(); change(s); assert.throws(() => validateSubmission(s, scope));
});

test('fresh exact probe transaction and phase2 rule correlation supplies bounded nonsecret proof', () => {
  const tx = transaction('current');
  const proof = correlatedDenial('tail of old transaction\n' + audit(transaction('old')) + audit(tx), errors(tx.unique_id), { token: 'current', uri });
  assert.equal(proof.transactionId, tx.unique_id);
  assert.deepEqual(proof.rules, ['932130', '941110', '949110']);
  assert(!JSON.stringify(proof).includes('ARGS:') && !JSON.stringify(proof).includes('current'));
});

for (const [name, change] of [
  ['old token only', tx => { tx.request.headers['X-CARLOS-AP-Probe'] = 'old'; }],
  ['wrong request method', tx => { tx.request.method = 'GET'; }],
  ['wrong route', tx => { tx.request.uri += '&other=1'; }],
  ['application-only403', tx => { tx.messages = []; }],
  ['missing marker signature', tx => { tx.messages = tx.messages.filter(m => m.details.ruleId !== '932130'); }],
  ['missing XSS signature', tx => { tx.messages = tx.messages.filter(m => m.details.ruleId !== '941110'); }],
  ['missing evaluation', tx => { tx.messages = tx.messages.filter(m => m.details.ruleId !== '949110'); }],
  ['different argument', tx => { tx.messages.forEach(m => { m.details.data = 'ARGS:other_field'; }); }],
]) test(`denial refuses ${name}`, () => {
  const tx = transaction('current'); change(tx);
  assert.throws(() => correlatedDenial(audit(tx), errors(tx.unique_id), { token: 'current', uri }));
});
test('duplicate token transactions, partial audit tails and unrelated unique_id error lines cannot pass', () => {
  const tx = transaction('current');
  for (const [a, e] of [[audit(tx) + audit(tx), errors(tx.unique_id)], [audit(tx).slice(0, -3), errors(tx.unique_id)],
    [audit(tx), errors('other')], [audit(tx), errors(tx.unique_id).replace('phase 2', 'phase 1')]]) {
    assert.throws(() => correlatedDenial(a, e, { token: 'current', uri }));
  }
});

test('log checkpoints reject truncation, inode replacement and symlinks', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'ap-probe-test-'));
  try {
    const file = path.join(dir, 'audit'); fs.writeFileSync(file, 'before');
    const start = logCheckpoint(file); fs.appendFileSync(file, 'after'); assert.equal(readCheckpoint(start), 'after');
    fs.writeFileSync(file, 'x'); assert.throws(() => readCheckpoint(start));
    fs.renameSync(file, `${file}.old`); fs.writeFileSync(file, 'different inode'); assert.throws(() => readCheckpoint(start));
    fs.symlinkSync(file, path.join(dir, 'link')); assert.throws(() => logCheckpoint(path.join(dir, 'link')));
  } finally { fs.rmSync(dir, { recursive: true, force: true }); }
});

test('exact owned transition changes only two attack fixtures, preserves first seven notation values and checks every UPDATE', () => {
  const sql = mockSql();
  const changed = transitionToClinicalValues(sql, scope);
  assert.equal(sql.writes.length, 2);
  assert.deepEqual(changed.slice(0, 7), scope.expected.slice(0, 7));
  assert.equal(changed[7].value, '3–5 per field'); assert.equal(changed[8].value, '>1000 / mL');
  assert.equal(scope.expected[7].value, values[7]);
});
for (const [name, change] of [
  ['changed patient identity', sql => { sql.state.owner = '0'; }],
  ['changed clinical result', sql => { sql.state.changed = true; }],
  ['duplicate/foreign extension', sql => { sql.state.duplicate = true; }],
  ['already saved form', sql => { sql.state.forms = [['1', scope.fid]]; }],
  ['orphan saved value', sql => { sql.state.values = [['orphan']]; }],
]) test(`owned transition refuses ${name} before any UPDATE`, () => {
  const sql = mockSql(); change(sql); assert.throws(() => transitionToClinicalValues(sql, scope)); assert.equal(sql.writes.length, 0);
});
test('a zero-row ownership-gated UPDATE cannot silently advance the clinical fixture', () => {
  const sql = mockSql(); sql.state.refuseUpdate = true;
  assert.throws(() => transitionToClinicalValues(sql, scope)); assert.equal(sql.writes.length, 1);
});

test('persisted clinical JSON is checked from exact saved patient/template/instance, not fresh AP DOM alone', () => {
  const sql = mockSql(); const expected = transitionToClinicalValues(sql, scope);
  assertPersistedLabValues(sql, { ...scope, expected }, '77');
  sql.state.savedRows = [[jsonValues(scope.expected)]];
  assert.throws(() => assertPersistedLabValues(sql, { ...scope, expected }, '77'));
  sql.state.savedRows = [];
  assert.throws(() => assertPersistedLabValues(sql, { ...scope, expected }, '77'));
});

async function runNegative({ responseStatus = 403, mutate, writeAudit = true } = {}) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'ap-negative-test-'));
  const auditLog = path.join(dir, 'audit'), errorLog = path.join(dir, 'error');
  fs.writeFileSync(auditLog, 'unrelated initial record\n'); fs.writeFileSync(errorLog, 'initial\n');
  const sql = mockSql();
  let posts = 0;
  let click = 0;
  const page = { evaluate: async fn => String(fn).includes('addEventListener') ? undefined : submission(),
    locator: selector => ({ click: async () => { assert.equal(selector, '#remoteSubmitButton'); click++; } }),
    waitForFunction: async () => {}, url: () => referer };
  const context = { request: { post: async (url, options) => {
    posts++; assert.equal(click, 1); assert.equal(url, action); assert.equal(options.maxRedirects, 0);
    assert.equal(options.headers.Origin, 'https://127.0.0.1'); assert.equal(options.headers.Referer, referer);
    assert.equal(new URLSearchParams(options.data).get('CSRF-TOKEN'), 'genuine-secret-not-for-log');
    const tx = transaction(options.headers['X-CARLOS-AP-Probe']);
    if (writeAudit) { fs.appendFileSync(auditLog, audit(tx)); fs.appendFileSync(errorLog, errors(tx.unique_id)); }
    if (mutate) mutate(sql);
    return { status: () => responseStatus, url: () => action, body: async () => Buffer.from('403 Forbidden') };
  } } };
  try {
    const receipt = await proveWafNegative({ context, page, sql, scope, auditLog, errorLog, pause: async () => {} });
    assert.equal(posts, 1); assert.equal(sql.writes.length, 0);
    return receipt;
  } finally { fs.rmSync(dir, { recursive: true, force: true }); }
}

test('actual negative helper binds single genuine-toolbar payload, exact fresh audit and zero saved rows', async () => {
  const receipt = await runNegative();
  assert.equal(receipt.mode, 'WAF_NEGATIVE_NO_SAVE'); assert.equal(receipt.status, 403);
  assert.equal(receipt.afterSavedForms, 0);
  assert(!JSON.stringify(receipt).includes('genuine-secret') && !JSON.stringify(receipt).includes('${labvalues}'));
});
test('403 alone, absent exact audit, saved rows or changed fixture cannot yield a negative PASS', async () => {
  for (const options of [{ responseStatus: 200 }, { writeAudit: false },
    { mutate: sql => { sql.state.forms = [['77', scope.fid]]; } },
    { mutate: sql => { sql.state.values = [['orphan']]; } },
    { mutate: sql => { sql.state.changed = true; } }]) {
    await assert.rejects(runNegative(options));
  }
});

async function runActualAcceptanceSegment({ waf = true, refusal = false } = {}) {
  const source = fs.readFileSync(path.join(__dirname, 'eform-consultation-acceptance-playwright-checks.js'), 'utf8');
  const start = source.indexOf('    const probePage = await openAddEform(context, importedFid);');
  const end = source.indexOf('    const directPage = await openSavedEformDirect(context, fdid);', start);
  assert(start > 0 && end > start);
  const events = [];
  const pages = [0, 1].map(index => ({ locator: () => ({ inputValue: async () => 'owned literal ${medicalHistory}' }),
    close: async () => { events.push(`close${index}`); } }));
  let opened = 0;
  const bindings = { context: {}, importedFid: scope.fid, demographicNo: scope.patient, provider: scope.provider,
    ownedMarker: marker, medicalHistory: 'owned literal ${medicalHistory}', baseUrl: new URL(scope.baseUrl), sql: {},
    wafAuditLog: waf ? '/audit' : undefined, wafErrorLog: waf ? '/error' : undefined, expectedState: {},
    assert: (condition, message) => assert(condition, message), console: { log: text => events.push(text) },
    openAddEform: async () => { events.push(`open${opened}`); return pages[opened++]; },
    assertOwnedLatestLabValues: async (page, rows) => {
      assert.equal(rows.length, 9);
      events.push(page === pages[0] ? 'rawDom9' : 'clinicalDom9');
      if (page === pages[1]) assert(!rows.some(row => row.value.includes('<script>') || row.value.includes('${labvalues}')));
    },
    fillEformForSave: async page => { assert.equal(page, pages[0]); events.push('toolbarFieldsPrepared'); },
    proveWafNegative: async options => {
      assert.equal(options.page, pages[0]); assert.deepEqual(options.scope.expected, scope.expected);
      events.push('negative'); if (refusal) throw new Error('missing exact negative proof'); return { outcome: 'PASS' };
    },
    transitionToClinicalValues: (sql, passedScope) => {
      assert.deepEqual(passedScope.expected, scope.expected); events.push('transition');
      return passedScope.expected.map((row, i) => ({ ...row, value: i === 7 ? '3–5 per field' : i === 8 ? '>1000 / mL' : row.value }));
    },
    saveEformAndCaptureFdid: async page => { assert.equal(page, pages[1]); events.push('savePositive'); return '77'; },
    assertPersistedLabValues: (sql, passedScope, fdid) => {
      assert.equal(fdid, '77'); assert.equal(passedScope.expected[7].value, '3–5 per field'); events.push('databasePersisted');
    }, screenshot: async () => {}, initialValues: scope.expected,
  };
  const run = vm.runInNewContext(`(async () => { let latestLabValues = initialValues; ${source.slice(start, end)} return latestLabValues; })()`, bindings);
  return { run, events };
}

test('actual acceptance segment orders all-nine raw DOM, negative/no-save, owned transition, fresh positive save and DB proof', async () => {
  const { run, events } = await runActualAcceptanceSegment(); await run;
  const named = events.filter(event => ['open0', 'rawDom9', 'toolbarFieldsPrepared', 'negative', 'close0',
    'transition', 'open1', 'clinicalDom9', 'savePositive', 'databasePersisted', 'close1'].includes(event));
  assert.deepEqual(named, ['open0', 'rawDom9', 'toolbarFieldsPrepared', 'negative', 'close0', 'transition',
    'open1', 'clinicalDom9', 'savePositive', 'databasePersisted', 'close1']);
});
test('actual acceptance segment cannot transition or positive-save after missing negative proof', async () => {
  const { run, events } = await runActualAcceptanceSegment({ refusal: true });
  await assert.rejects(run, /missing exact negative proof/);
  assert(!events.includes('transition') && !events.includes('savePositive'));
});
test('non-WAF local mode reports negative transport unrequested while still executing actual positive segment', async () => {
  const { run, events } = await runActualAcceptanceSegment({ waf: false }); await run;
  assert(events.some(event => event.includes('WAF-negative coverage NOT_REQUESTED')));
  assert(!events.includes('negative') && events.includes('rawDom9') && events.includes('savePositive') && events.includes('databasePersisted'));
});
