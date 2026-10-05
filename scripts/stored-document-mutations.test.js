/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {createHash} = require('node:crypto');
const test = require('node:test');
const {validateOwnedPost} = require('./stored-document-mutations-playwright-checks');
const {expectedContentUpdates, validatePolicyRefusal, createMutationTracker, probeAssignedPolicy} = require('./stored-document-mutations-playwright-checks');
const {fileProof} = require('./lib/stored-document-mutation-fixture');
const owned = new Set(['42']);
const expected = {method: ['split'], document: ['42'], queueID: ['1'], page: ['2,90', '1,0'], sourceRevision: ['a'.repeat(64)]};
const body = 'method=split&document=42&queueID=1&page=2%2C90&page=1%2C0&CSRF-TOKEN=owned-token&sourceRevision=' + 'a'.repeat(64);

test('policy mode is explicit and cannot silently reinterpret an unexpected installation', () => {
  const old = process.env.STORED_DOCUMENT_EXPECT_CONTENT_UPDATES;
  try {
    delete process.env.STORED_DOCUMENT_EXPECT_CONTENT_UPDATES;
    assert.equal(expectedContentUpdates(), false);
    assert.equal(expectedContentUpdates('false'), false); assert.equal(expectedContentUpdates('true'), true);
    for (const value of ['', 'TRUE', 'yes', '1', null]) assert.throws(() => expectedContentUpdates(value));
  } finally {
    if (old === undefined) delete process.env.STORED_DOCUMENT_EXPECT_CONTENT_UPDATES;
    else process.env.STORED_DOCUMENT_EXPECT_CONTENT_UPDATES = old;
  }
});

function policyResponse(status = 403, data = {success: false, accepted: false, retryable: false}, headers = {'content-type': 'application/json'}) {
  return {status: () => status, headers: () => headers, json: async () => data};
}
function trackedFixture() {
  const events = [], fixture = {ids: () => owned,
    begin() {events.push('begin');}, transportFailed() {events.push('unknown');},
    rejectUnaccepted() {events.push('rejected');}, assertUnchanged() {events.push('unchanged');}};
  return {fixture, events};
}

test('policy refusals require exact JSON403 nonacceptance, not CSRF HTML, capacity or unexpected acceptance', () => {
  const denied = {success: false, accepted: false, retryable: false};
  validatePolicyRefusal(policyResponse(), denied);
  for (const status of [200, 302, 409, 500, 503]) assert.throws(() => validatePolicyRefusal(policyResponse(status), denied));
  for (const data of [null, {...denied, accepted: true}, {...denied, success: true}, {...denied, retryable: true}, {success: false}]) {
    assert.throws(() => validatePolicyRefusal(policyResponse(), data));
  }
  assert.throws(() => validatePolicyRefusal(policyResponse(403, denied, {'content-type': 'text/html'}), denied));
  assert.throws(() => validatePolicyRefusal(policyResponse(403, denied, {'content-type': 'application/json', 'retry-after': '1'}), denied));
});

test('zero-attempt UI precondition failure and synthetic refusal never create an uncertain mutation', () => {
  const {fixture, events} = trackedFixture(), tracker = createMutationTracker(fixture), before = tracker.count();
  tracker.failed({}); tracker.failureSince(before);
  assert.deepEqual(events, []); assert.equal(tracker.count(), 0);
});

test('forwarded request ownership distinguishes read or locally aborted transport from real unknown writes', () => {
  const {fixture, events} = trackedFixture(), tracker = createMutationTracker(fixture), ownedRequest = {};
  tracker.beforeForward(ownedRequest);
  tracker.failed({}); assert.deepEqual(events, ['begin']);
  tracker.failed(ownedRequest); tracker.failureSince(0);
  assert.deepEqual(events, ['begin', 'unknown', 'unknown']);
});

test('a later zero-attempt operation cannot inherit uncertainty solely from a prior confirmed operation', () => {
  const {fixture, events} = trackedFixture(), tracker = createMutationTracker(fixture);
  tracker.beforeForward({}); fixture.rejectUnaccepted();
  const next = tracker.count(); tracker.failureSince(next);
  assert.deepEqual(events, ['begin', 'rejected']);
  tracker.beforeForward({}); tracker.failureSince(next);
  assert.deepEqual(events, ['begin', 'rejected', 'begin', 'unknown']);
});

test('failed pre-dispatch fixture ownership gate does not record a forwarded request', () => {
  const {fixture, events} = trackedFixture(); fixture.begin = () => {throw new Error('ownership changed');};
  const tracker = createMutationTracker(fixture);
  assert.throws(() => tracker.beforeForward({}), /ownership changed/);
  tracker.failureSince(0); assert.equal(tracker.count(), 0); assert.deepEqual(events, []);
});

test('direct policy POST begins immediately before exact dispatch and clears only confirmed refusal', async () => {
  const {fixture, events} = trackedFixture();
  const requester = {async post(endpoint, options) {
    assert.equal(endpoint, 'https://local/owned'); assert.equal(events.at(-1), 'begin');
    assert.equal(options.data, body); assert.equal(options.headers['CSRF-TOKEN'], 'owned-token');
    assert.equal(options.headers['Content-Type'], 'application/x-www-form-urlencoded'); assert.equal(options.maxRedirects, 0);
    events.push('sent'); return policyResponse();
  }};
  await probeAssignedPolicy(requester, 'https://local/owned', new URLSearchParams(body), expected, fixture);
  assert.deepEqual(events, ['unchanged', 'begin', 'sent', 'rejected', 'unchanged']);
});

for (const issue of ['foreign identity', 'empty token', 'duplicate token']) test(`invalid policy probe ${issue} fails before any begin or dispatch`, async () => {
  const {fixture, events} = trackedFixture(), fields = new URLSearchParams(body);
  if (issue === 'foreign identity') fields.set('document', '77');
  if (issue === 'empty token') fields.set('CSRF-TOKEN', '');
  if (issue === 'duplicate token') fields.append('CSRF-TOKEN', 'other');
  const requester = {async post() {assert.fail('must not dispatch');}};
  await assert.rejects(probeAssignedPolicy(requester, 'https://local/owned', fields, expected, fixture));
  assert.deepEqual(events, []);
});

for (const issue of ['transport', 'unexpected acceptance', 'HTML CSRF refusal']) test(`direct policy probe retains fixture after ${issue}`, async () => {
  const {fixture, events} = trackedFixture();
  const requester = {async post() {
    if (issue === 'transport') throw new Error('lost response');
    if (issue === 'unexpected acceptance') return policyResponse(200, {success: true, accepted: true});
    return policyResponse(403, null, {'content-type': 'text/html'});
  }};
  await assert.rejects(probeAssignedPolicy(requester, 'https://local/owned', new URLSearchParams(body), expected, fixture));
  assert.deepEqual(events, ['unchanged', 'begin', 'unknown']);
});

test('installed mutation allowlist preserves repeated-page order and explicit owned identity', () => {
  assert.deepEqual(validateOwnedPost(body, expected, owned).getAll('page'), ['2,90', '1,0']);
});
for (const [name, candidate] of [
  ['different patient document', body.replace('document=42', 'document=43')],
  ['duplicate document', body + '&document=42'],
  ['different operation', body.replace('method=split', 'method=removeFirstPage')],
  ['different queue', body.replace('queueID=1', 'queueID=2')],
  ['changed page order', body.replace('page=2%2C90&page=1%2C0', 'page=1%2C0&page=2%2C90')],
  ['unexpected mutation field', body + '&demographicNo=9'],
  ['duplicate csrf', body + '&CSRF-TOKEN=other'],
  ['changed source revision', body.replace('a'.repeat(64), 'b'.repeat(64))],
]) test(`installed mutation guard refuses ${name}`, () => {
  assert.throws(() => validateOwnedPost(candidate, expected, owned));
});

test('file proof detects changed bytes, permissions, and a replaced inode even at the same name', () => {
  const store = fs.mkdtempSync(path.join(os.tmpdir(), 'stored-document-proof-test-'));
  try {
    const file = path.join(store, 'owned.pdf'); fs.writeFileSync(file, 'first', {mode: 0o600});
    const baseline = fileProof(file, store);
    fs.writeFileSync(file, 'changed'); assert.notDeepEqual(fileProof(file, store), baseline);
    fs.writeFileSync(file, 'first'); fs.chmodSync(file, 0o640); assert.notDeepEqual(fileProof(file, store), baseline);
    fs.chmodSync(file, 0o600);
    const replacement = path.join(store, 'new.pdf'); fs.writeFileSync(replacement, 'first', {mode: 0o600});
    fs.renameSync(replacement, file); assert.notEqual(fileProof(file, store).ino, baseline.ino);
  } finally {fs.rmSync(store, {recursive: true, force: true});}
});

test('file proof rejects symlinks and paths outside the owned store', () => {
  const store = fs.mkdtempSync(path.join(os.tmpdir(), 'stored-document-proof-test-'));
  try {
    const file = path.join(store, 'owned.pdf'), link = path.join(store, 'link.pdf');
    fs.writeFileSync(file, 'owned'); fs.symlinkSync(file, link);
    assert.throws(() => fileProof(link, store), /unsafe/);
    assert.throws(() => fileProof(file, path.dirname(store)), /escaped/);
  } finally {fs.rmSync(store, {recursive: true, force: true});}
});

const {createStoredDocumentFixture, recoverStoredDocumentFixture, expectedSplitRecipients} = require('./lib/stored-document-mutation-fixture');

test('split fixture expects the active forwarding closure without losing source recipients', () => {
  const rules = { '999998': ['999996'], '999996': ['999997'], '999997': ['999998'] };
  const sql = { rows(query) {
    if (query.includes('FROM providerLabRouting')) return [['999998'], ['999995']];
    const provider = /provider_no='([^']+)'/.exec(query)?.[1];
    return (rules[provider] || []).map(forwarded => [forwarded]);
  } };
  assert.deepEqual(expectedSplitRecipients(sql, '42', '999998'),
    ['999995', '999996', '999997', '999998']);
});
function ownedFixture({references = [], discriminator = true} = {}) {
  const store = fs.mkdtempSync(path.join(os.tmpdir(), 'stored-document-cleanup-test-'));
  const saved = {DOCUMENT_DIR: process.env.DOCUMENT_DIR, RX_FAX_DOCUMENT_DIR: process.env.RX_FAX_DOCUMENT_DIR,
    DOCUMENT_CACHE_DIR: process.env.DOCUMENT_CACHE_DIR};
  process.env.DOCUMENT_DIR = store; delete process.env.RX_FAX_DOCUMENT_DIR;
  process.env.DOCUMENT_CACHE_DIR = path.join(store, 'unused-cache');
  const hash = 'a'.repeat(64), other = ['5', 'b'.repeat(64)], callbacks = [], transactions = [];
  const data = {document: [['42', hash]], ctl_document: [['123', 'demographic', '42', hash]],
    patientLabRouting: [['11', hash]], providerLabRouting: [['12', hash]], queue_document_link: [['13', hash]]};
  let foreign = false, rollback = false;
  const sql = {
    rows(query) {
      if (query.includes('ORDER BY ORDINAL_POSITION')) {
        if (/TABLE_NAME='(?:EFormDocs|consultdocs)'/.test(query)) {
          return discriminator ? [['id'], ['document_no'], ['doctype']] : [['id'], ['document_no']];
        }
        return [['fixture_column']];
      }
      if (query.includes('information_schema.COLUMNS c')) return [['document_storage', 'documentNo', '0'],
        ...references.map(row => [row.table, row.column || 'document_no', row.declared ? '1' : '0'])];
      if (query.includes('FROM document ORDER BY')) return [other, ...data.document].map(row => [...row]);
      const table = /FROM `([^`]+)`/.exec(query)?.[1];
      if (table) return structuredClone(data[table]);
      throw new Error('Unexpected rows query');
    },
    value(query) {
      if (query.startsWith('INSERT INTO document')) {assert.equal(callbacks.length, 1); return '42';}
      if (query.includes('START TRANSACTION')) {
        transactions.push(query);
        if (rollback) return '0';
        for (const table of Object.keys(data)) data[table] = [];
        return '1';
      }
      if (query.includes('FROM demographic')) return '1';
      if (query.includes('information_schema.TABLES')) return '0';
      const external = /FROM `(EFormDocs|consultdocs)` WHERE/.exec(query)?.[1];
      if (external) {
        // Model the SQL gate for actual polymorphic rows, not the declaration rows.
        const allowed = /BINARY `doctype` NOT IN \(([^)]+)\)/.exec(query)?.[1]
          .split(',').map(value => value.trim().replace(/^'|'$/g, '')) || [];
        return String(references.filter(row => row.table === external && row.row !== false
          && (row.type == null || !allowed.includes(row.type))).length);
      }
      if (query.includes('document_storage') || query.includes('casemgmt_note_link')) return foreign ? '1' : '0';
      throw new Error('Unexpected value query');
    },
    execute(query) {assert.ok(query.startsWith('INSERT INTO ctl_document'));},
  };
  const session = {sql, marker: 'FAKE-PW1234567890abcdef', patient: '123', provider: '999998',
    cleanup(callback) {callbacks.push(callback);}};
  const fixture = createStoredDocumentFixture(session, '10');
  return {fixture, session, data, transactions, cleanup: callbacks[0], setForeign() {foreign = true;}, rollback() {rollback = true;},
    dispose() {
      for (const [key, value] of Object.entries(saved)) {if (value === undefined) delete process.env[key]; else process.env[key] = value;}
      fs.rmSync(store, {recursive: true, force: true}); fs.rmSync(path.dirname(fixture.journal), {recursive: true, force: true});
    }};
}

test('successful cleanup retains its evidence journal and uses one guarded rollback-capable transaction', async () => {
  const env = ownedFixture();
  try {
    env.fixture.closeAfterDrain(); await env.cleanup();
    assert.equal(env.transactions.length, 1);
    assert.match(env.transactions[0], /FOR UPDATE/); assert.match(env.transactions[0], /ROW_COUNT\(\)=1/);
    assert.match(env.transactions[0], /IF\(@stored_fixture_ok,'COMMIT','ROLLBACK'\)/);
    assert.ok(Object.values(env.data).every(rows => rows.length === 0));
    assert.equal(fs.existsSync(env.fixture.sourceFile), false);
    assert.equal(JSON.parse(fs.readFileSync(env.fixture.journal)).phase, 'cleaned');
    assert.equal(env.fixture.isCleaned(), true);
  } finally {env.dispose();}
});

for (const problem of ['changed routing', 'changed file', 'external reference', 'unknown mutation', 'failed transport', 'undrained browser']) {
  test(`cleanup preserves the fixture with ${problem}`, async () => {
    const env = ownedFixture();
    try {
      if (problem === 'changed routing') env.data.patientLabRouting[0][1] = 'c'.repeat(64);
      if (problem === 'changed file') fs.appendFileSync(env.fixture.sourceFile, 'changed');
      if (problem === 'external reference') env.setForeign();
      if (problem === 'unknown mutation') env.fixture.begin();
      if (problem === 'failed transport') env.fixture.transportFailed();
      if (problem !== 'undrained browser') env.fixture.closeAfterDrain();
      await assert.rejects(env.cleanup());
      assert.equal(env.transactions.length, 0); assert.equal(env.data.document.length, 1);
      assert.equal(fs.existsSync(env.fixture.sourceFile), true); assert.equal(env.fixture.isCleaned(), false);
    } finally {env.dispose();}
  });
}

test('transaction refusal preserves document bytes and all clinical routes for recovery', async () => {
  const env = ownedFixture();
  try {
    const baseline = structuredClone(env.data), bytes = fs.readFileSync(env.fixture.sourceFile);
    env.rollback(); env.fixture.closeAfterDrain();
    await assert.rejects(env.cleanup(), /Atomic stored fixture cleanup refused/);
    assert.deepEqual(env.data, baseline); assert.deepEqual(fs.readFileSync(env.fixture.sourceFile), bytes);
    assert.equal(env.fixture.isCleaned(), false);
  } finally {env.dispose();}
});

for (const [table, types] of [['EFormDocs', ['E', 'L', 'H']], ['consultdocs', ['E', 'L', 'F', 'H']]]) {
  for (const type of types) test(`cleanup preserves unrelated ${table} ${type} links with a colliding numeric ID`, async () => {
    const references = [{table, type}], env = ownedFixture({references});
    try {
      const before = structuredClone(references);
      env.fixture.closeAfterDrain(); await env.cleanup();
      assert.equal(env.transactions.length, 1); assert.equal(env.fixture.isCleaned(), true);
      assert.deepEqual(references, before);
      const sql = env.transactions[0];
      assert.match(sql, /`doctype` IS NULL OR BINARY `doctype` NOT IN/);
      assert.match(sql, new RegExp('FROM `' + table + '` WHERE .* FOR UPDATE'));
      assert.match(sql, new RegExp('NOT EXISTS\\(SELECT 1 FROM `' + table + '` WHERE'));
      assert.ok(!sql.includes('DELETE FROM `' + table + '`'));
    } finally {env.dispose();}
  });
  for (const type of ['D', null, '?', 'e', 'E ']) test(`cleanup retains ${table} reference with unresolved type ${String(type)}`, async () => {
    const env = ownedFixture({references: [{table, type}]});
    try {
      env.fixture.closeAfterDrain(); await assert.rejects(env.cleanup(), /external references/);
      assert.equal(env.transactions.length, 0); assert.equal(env.data.document.length, 1);
      assert.ok(fs.existsSync(env.fixture.sourceFile));
    } finally {env.dispose();}
  });
  test(`a declared document FK overrides ${table} discriminator exclusions in either discovery order`, async () => {
    for (const reverse of [false, true]) {
      const references = [{table, type: 'E'}, {table, type: 'E', declared: true, row: false}];
      if (reverse) references.reverse();
      const env = ownedFixture({references});
      try {
        env.fixture.closeAfterDrain(); await assert.rejects(env.cleanup(), /external references/);
        assert.equal(env.transactions.length, 0); assert.equal(env.data.document.length, 1);
      } finally {env.dispose();}
    }
  });
  test(`missing ${table} discriminator schema preserves every numeric reference`, async () => {
    const env = ownedFixture({references: [{table, type: 'E'}], discriminator: false});
    try {
      env.fixture.closeAfterDrain(); await assert.rejects(env.cleanup(), /external references/);
      assert.equal(env.transactions.length, 0);
    } finally {env.dispose();}
  });
}

test('EFormDocs does not borrow the ConsultDocs-only F discriminator', async () => {
  const env = ownedFixture({references: [{table: 'EFormDocs', type: 'F'}]});
  try {
    env.fixture.closeAfterDrain(); await assert.rejects(env.cleanup(), /external references/);
    assert.equal(env.transactions.length, 0);
  } finally {env.dispose();}
});

function recoveryOptions(env) {
  return {journal: env.fixture.journal, expectedSourceId: env.fixture.sourceId, browserDrainConfirmed: true,
    expectedJournalSha256: createHash('sha256').update(fs.readFileSync(env.fixture.journal)).digest('hex')};
}

test('explicit recovery uses the original full snapshots and leaves the reviewed journal immutable', async () => {
  const env = ownedFixture({references: [{table: 'EFormDocs', type: 'E'}, {table: 'consultdocs', type: 'E'}]});
  try {
    env.fixture.closeAfterDrain(); const original = fs.readFileSync(env.fixture.journal);
    const result = await recoverStoredDocumentFixture(env.session, '10', recoveryOptions(env));
    assert.equal(result.cleaned, true); assert.equal(result.sourceId, '42');
    assert.equal(env.transactions.length, 1);
    assert.match(env.transactions[0], /IF\(@stored_fixture_ok,'COMMIT','ROLLBACK'\)/);
    assert.deepEqual(fs.readFileSync(env.fixture.journal), original);
    assert.notEqual(result.recoveryJournal, env.fixture.journal);
    const receipt = JSON.parse(fs.readFileSync(result.recoveryJournal));
    assert.equal(receipt.phase, 'cleaned'); assert.equal(receipt.cleaned, true);
    assert.equal(receipt.recoveryOf.journal, env.fixture.journal);
    assert.deepEqual(receipt.removed, ['42']);
    assert.equal(fs.existsSync(env.fixture.sourceFile), false);
  } finally {env.dispose();}
});

for (const defect of ['wrong hash', 'wrong document', 'wrong patient', 'wrong program', 'no explicit drain',
  'uncertain mutation', 'failed transport', 'wrong phase', 'partial deletion', 'accepted split',
  'missing snapshot', 'changed file', 'changed routes', 'public journal', 'symlink journal']) {
  test(`explicit recovery refuses ${defect} without deleting rows or original evidence`, async () => {
    const env = ownedFixture();
    try {
      env.fixture.closeAfterDrain();
      const record = JSON.parse(fs.readFileSync(env.fixture.journal));
      if (defect === 'uncertain mutation') record.uncertain = true;
      if (defect === 'failed transport') record.transportUncertain = true;
      if (defect === 'wrong phase') record.phase = 'mutation-pending';
      if (defect === 'partial deletion') record.removed = ['42'];
      if (defect === 'accepted split') record.acceptedSplitId = '43';
      if (defect === 'missing snapshot') delete record.documents[0][1].snapshot.ctl_document;
      fs.writeFileSync(env.fixture.journal, JSON.stringify(record));
      const options = recoveryOptions(env);
      if (defect === 'wrong hash') options.expectedJournalSha256 = '0'.repeat(64);
      if (defect === 'wrong document') options.expectedSourceId = '43';
      if (defect === 'no explicit drain') options.browserDrainConfirmed = false;
      if (defect === 'changed file') fs.appendFileSync(env.fixture.sourceFile, 'changed');
      if (defect === 'changed routes') env.data.providerLabRouting[0][1] = 'c'.repeat(64);
      if (defect === 'public journal') fs.chmodSync(env.fixture.journal, 0o644);
      if (defect === 'symlink journal') {
        const original = env.fixture.journal + '.original'; fs.renameSync(env.fixture.journal, original);
        fs.symlinkSync(original, env.fixture.journal);
      }
      const original = fs.readFileSync(env.fixture.journal), baseline = structuredClone(env.data);
      const session = defect === 'wrong patient' ? {...env.session, patient: '124'} : env.session;
      await assert.rejects(recoverStoredDocumentFixture(session, defect === 'wrong program' ? '11' : '10', options));
      assert.equal(env.transactions.length, 0); assert.deepEqual(env.data, baseline);
      assert.deepEqual(fs.readFileSync(env.fixture.journal), original);
      assert.ok(fs.existsSync(env.fixture.sourceFile));
    } finally {env.dispose();}
  });
}

test('recovery transaction refusal retains the original file and every clinical route', async () => {
  const env = ownedFixture();
  try {
    env.fixture.closeAfterDrain(); env.rollback();
    const original = fs.readFileSync(env.fixture.journal), baseline = structuredClone(env.data);
    await assert.rejects(recoverStoredDocumentFixture(env.session, '10', recoveryOptions(env)), /Atomic stored fixture cleanup refused/);
    assert.deepEqual(fs.readFileSync(env.fixture.journal), original);
    assert.deepEqual(env.data, baseline); assert.ok(fs.existsSync(env.fixture.sourceFile));
  } finally {env.dispose();}
});

test('missing recovery options cannot fall through into creation of a new clinical fixture', async () => {
  await assert.rejects(recoverStoredDocumentFixture({}, '10'), /Explicit reviewed recovery options/);
});
