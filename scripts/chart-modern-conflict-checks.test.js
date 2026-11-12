/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const { revokeOwnedModernLock, acknowledgeModernConflict } = require('./lib/chart-modern-conflict-checks');
const h = require('./lib/playwright-harness');

const owned = ['7', 'session_A', '127.0.0.1', '999998', '0', '11', '2026-10-25 01:02:03'];
const bystander = ['8', 'session_B', '127.0.0.2', '999997', '41', '12', '2026-10-25 01:02:04'];
function lockFixture() {
  let rows = structuredClone([owned, bystander]); let expectedOwned = owned; let ownedParent = true; let changeBeforeDelete;
  const writes = [];
  const sql = {
    rows: () => structuredClone(rows),
    value(query) {
      if (query.startsWith('SELECT COUNT(*) FROM demographic')) return ownedParent ? '1' : '0';
      assert.match(query, /^DELETE FROM casemgmt_note_lock WHERE id<=>'7'/);
      for (const column of ['session_id', 'ip_address', 'provider_no', 'note_id', 'demographic_no', 'lock_acquired']) {
        assert.ok(query.includes(`${column}<=>`), `The conditional removal must preserve ${column}`);
      }
      if (changeBeforeDelete) changeBeforeDelete(rows);
      if (JSON.stringify(rows.find(row => row[0] === '7')) !== JSON.stringify(expectedOwned)) return '0';
      writes.push(query); rows = rows.filter(row => row[0] !== '7'); return '1';
    },
  };
  const identity = { patient: '11', provider: '999998', noteId: '0', sessionId: 'session_A', marker: 'FAKE-PW0123456789abcdef' };
  return { sql, identity, writes, read: () => structuredClone(rows), replace: value => { rows = value; },
    foreignParent: () => { ownedParent = false; }, drift: callback => { changeBeforeDelete = callback; },
    storedSession: session => { expectedOwned = [owned[0], session, ...owned.slice(2)]; rows = [expectedOwned, bystander]; } };
}

for (const stored of ['session_A', 'session_A.route1']) test(`the observed routed cookie revokes only its exact full/core lock (${stored})`, () => {
  const f = lockFixture(); f.identity.sessionId = 'session_A.route1'; f.storedSession(stored);
  const result = revokeOwnedModernLock(f.sql, f.identity);
  assert.equal(result.revoked[1], stored); assert.deepEqual(f.read(), [bystander]); assert.equal(f.writes.length, 1);
});

test('a different route suffix is retained without any SQL mutation', () => {
  const f = lockFixture(); f.identity.sessionId = 'session_A.route1'; f.storedSession('session_A.route2');
  const before = f.read(); assert.throws(() => revokeOwnedModernLock(f.sql, f.identity));
  assert.deepEqual(f.read(), before); assert.equal(f.writes.length, 0);
});

test('simultaneous full and core session locks are ambiguous and retained before SQL mutation', () => {
  const f = lockFixture(); f.identity.sessionId = 'session_A.route1';
  f.replace([owned, ['9', 'session_A.route1', ...owned.slice(2)], bystander]);
  const before = f.read(); assert.throws(() => revokeOwnedModernLock(f.sql, f.identity));
  assert.deepEqual(f.read(), before); assert.equal(f.writes.length, 0);
});

test('revokes only the exact owned lock and preserves every bystander lock', () => {
  const f = lockFixture(); const result = revokeOwnedModernLock(f.sql, f.identity);
  assert.deepEqual(result.revoked, owned); assert.deepEqual(result.otherLocks, [bystander]);
  assert.deepEqual(f.read(), [bystander]); assert.equal(f.writes.length, 1);
});

for (const [label, change] of [
  ['foreign session', f => { f.identity.sessionId = 'session_B'; }],
  ['foreign provider', f => { f.identity.provider = '999997'; }],
  ['foreign patient', f => { f.identity.patient = '12'; }],
  ['different selected note', f => { f.identity.noteId = '41'; }],
  ['foreign patient marker', f => f.foreignParent()],
  ['duplicate owned locks', f => f.replace([owned, ['9', ...owned.slice(1)], bystander])],
  ['invalid lock identity', f => f.replace([['0', ...owned.slice(1)], bystander])],
]) {
  test(`retains all lock rows without mutation after ${label}`, () => {
    const f = lockFixture(); change(f); const before = f.read();
    assert.throws(() => revokeOwnedModernLock(f.sql, f.identity));
    assert.deepEqual(f.read(), before); assert.equal(f.writes.length, 0);
  });
}

test('a lock changed after observation fails its conditional removal and is never restored over', () => {
  const f = lockFixture(); f.drift(rows => { rows[0][1] = 'new_owner'; });
  assert.throws(() => revokeOwnedModernLock(f.sql, f.identity), /conditional revocation/);
  assert.equal(f.writes.length, 0); assert.equal(f.read()[0][1], 'new_owner');
});

function conflictFixture() {
  const recorder = h.createRecorder(); const url = 'https://127.0.0.1/carlos/CaseManagementEntry?chartContext=owned';
  const http = { url, method: 'POST', status: 409 };
  const resource = { location: { url }, text: 'Failed to load resource: the server responded with a status of 409 ()' };
  recorder.badResponses.push(http); recorder.consoleIssues.push(resource);
  return { recorder, response: { status: () => 409, url: () => url },
    before: { http: new Set(), console: new Set() }, http, resource };
}

test('acknowledges one controlled409 while retaining its raw audit and unrelated errors', () => {
  const f = conflictFixture(); const unknown = { url: 'https://127.0.0.1/carlos/other', method: 'GET', status: 500 };
  f.recorder.badResponses.push(unknown);
  const audit = acknowledgeModernConflict(f.recorder, f.response, f.before);
  assert.deepEqual(audit, { http: [f.http], console: [f.resource] });
  assert.deepEqual(f.recorder.badResponses, [unknown]); assert.deepEqual(f.recorder.consoleIssues, []);
  assert.throws(() => h.assertStrictPage(f.recorder), /HTTP 500/);
});

for (const [label, change] of [
  ['wrong response status', f => { f.response.status = () => 500; }],
  ['different response URL', f => { f.response.url = () => 'https://127.0.0.1/carlos/other'; }],
  ['duplicate409 response', f => { f.recorder.badResponses.push({ ...f.http }); }],
  ['duplicate resource diagnostic', f => { f.recorder.consoleIssues.push({ ...f.resource }); }],
  ['preexisting409', f => { f.before.http.add(f.http); }],
]) {
  test(`retains every finding after ${label}`, () => {
    const f = conflictFixture(); change(f); const before = structuredClone(f.recorder);
    assert.throws(() => acknowledgeModernConflict(f.recorder, f.response, f.before));
    assert.deepEqual(f.recorder, before);
  });
}
