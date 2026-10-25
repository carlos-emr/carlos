/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const { retireOwnedChartPages } = require('./lib/owned-chart-page-cleanup');
const h = require('./lib/playwright-harness');
const shippedSchema = fs.readFileSync(path.join(__dirname, '../database/mysql/migration/common/V1__baseline_schema.sql'), 'utf8');
const policyKeys = Object.fromEntries(['provider_default_program', 'program_provider', 'admission', 'default_role_access'].map(table => {
  const definition = shippedSchema.match(new RegExp('CREATE TABLE `' + table + '` \\(([\\s\\S]*?)\\) ENGINE='));
  const key = definition?.[1].match(/PRIMARY KEY \(`([A-Za-z0-9_]+)`\)/)?.[1];
  assert.ok(key, `The shipped ${table} schema must have a single primary key`);
  return [table, key];
}));

function fixture() {
  const marker = 'FAKE-PW0123456789abcdef'; const patient = '11'; const provider = '999998';
  const form = { patient, provider, note: '0', token: Buffer.from('11:7').toString('base64url') + '.signed', csrf: 'real-csrf' };
  const own = ['1', 'session', '127.0.0.1', provider, '0', patient, '2026-10-25 01:02:03'];
  const other = ['2', 'foreign-session', '127.0.0.2', '999997', '31', '12', '2026-10-25 01:02:03'];
  let locks = [own, other]; let reads = 0; let suppressed = false; let closed = false;
  const behavior = { status: 200, application: true, foreignParent: false, alterClinical: false, alterOtherLock: false, alterForm: false };
  const requests = [];
  const chart = { isClosed: () => closed, evaluate: async (_fn, expected) => {
    if (expected) {
      if (behavior.alterForm) return false;
      assert.deepEqual(expected, form); suppressed = true; return true;
    }
    return form;
  }, close: async () => { assert.equal(suppressed, true); closed = true; } };
  const s = { marker, provider, config: { baseUrl: new URL('https://127.0.0.1/carlos/') },
    sql: { value: () => behavior.foreignParent ? '0' : '1', rows: query => {
      if (query.includes('FROM casemgmt_note_lock')) return structuredClone(locks);
      const policyQuery = query.match(/^SELECT \* FROM ([A-Za-z0-9_]+) ORDER BY ([A-Za-z0-9_]+)$/);
      if (policyQuery && Object.hasOwn(policyKeys, policyQuery[1])) assert.equal(policyQuery[2], policyKeys[policyQuery[1]],
        `The shipped ${policyQuery[1]} primary key is ${policyKeys[policyQuery[1]]}`);
      reads++;
      if (behavior.alterClinical && requests.length && query.includes('FROM casemgmt_note ')) return [['changed-note']];
      return [];
    } }, context: { cookies: async () => [{ name: 'JSESSIONID', value: 'session.route' }], request: { post: async (url, options) => {
      requests.push({ url, options });
      if (behavior.status === 200 && behavior.application) locks = [behavior.alterOtherLock ? [...other.slice(0, 6), 'changed'] : other];
      return { status: () => behavior.status, headers: () => behavior.application ? { [h.APPLICATION_HEADER]: 'none' } : {}, dispose: async () => {} };
    } } } };
  return { s, pages: [{ chart, patient, owner: marker }], evidence: [], behavior, requests,
    suppressed: () => suppressed, closed: () => closed, locks: () => locks, other, reads: () => reads,
    storedSession: session => { locks = [[own[0], session, ...own.slice(2)], other]; },
    addStoredLock: session => { locks.push(['3', session, ...own.slice(2)]); } };
}

test('owned page cleanup accepts the exact observed full routed cookie', async () => {
  const f = fixture(); f.storedSession('session.route');
  await retireOwnedChartPages(f.s, f.pages, f.evidence);
  assert.equal(f.closed(), true); assert.equal(f.requests.length, 1); assert.deepEqual(f.locks(), [f.other]);
});

test('owned page cleanup refuses a foreign route suffix before API mutation or flag changes', async () => {
  const f = fixture(); f.storedSession('session.other-route'); const before = structuredClone(f.locks());
  await assert.rejects(retireOwnedChartPages(f.s, f.pages, f.evidence), /different route suffix/);
  assert.equal(f.requests.length, 0); assert.equal(f.suppressed(), false); assert.equal(f.closed(), false);
  assert.deepEqual(f.locks(), before);
});

test('ambiguous full/core owned page locks are retained before API mutation', async () => {
  const f = fixture(); f.addStoredLock('session.route'); const before = structuredClone(f.locks());
  await assert.rejects(retireOwnedChartPages(f.s, f.pages, f.evidence), /ambiguous full\/core/);
  assert.equal(f.requests.length, 0); assert.equal(f.suppressed(), false); assert.deepEqual(f.locks(), before);
});

test('the schema-aware boundary validates all four policy snapshot order keys against the shipped SQL', async () => {
  const f = fixture();
  for (const [table, key] of Object.entries(policyKeys)) {
    assert.throws(() => f.s.sql.rows(`SELECT * FROM ${table} ORDER BY missing_column`), /shipped.*primary key/);
    assert.deepEqual(f.s.sql.rows(`SELECT * FROM ${table} ORDER BY ${key}`), []);
  }
  assert.throws(() => f.s.sql.rows('SELECT * FROM admission ORDER BY id'), /shipped admission primary key is am_id/);
  await retireOwnedChartPages(f.s, f.pages, f.evidence);
  assert.equal(f.closed(), true);
});

test('only acknowledged owned lock cleanup suppresses its page, retaining all clinical state and other locks', async () => {
  const f = fixture(); await retireOwnedChartPages(f.s, f.pages, f.evidence);
  assert.equal(f.requests.length, 1); assert.equal(f.requests[0].options.maxRedirects, 0);
  assert.equal(f.requests[0].options.form.chartContext, Buffer.from('11:7').toString('base64url') + '.signed');
  assert.equal(f.requests[0].options.form['CSRF-TOKEN'], 'real-csrf');
  assert.equal(f.requests[0].options.form.force, undefined);
  assert.equal(f.suppressed(), true); assert.equal(f.closed(), true); assert.deepEqual(f.locks(), [f.other]);
  assert.equal(f.evidence[0].applicationStatus, 200); assert.deepEqual(f.evidence[0].removedLockIds, ['1']);
});

for (const [label, change, requestCount] of [
  ['foreign patient ownership', f => { f.behavior.foreignParent = true; }, 0],
  ['actual API403', f => { f.behavior.status = 403; }, 1],
  ['missing application marker', f => { f.behavior.application = false; }, 1],
  ['other lock drift', f => { f.behavior.alterOtherLock = true; }, 1],
  ['clinical drift', f => { f.behavior.alterClinical = true; }, 1],
  ['form changed after acknowledgment', f => { f.behavior.alterForm = true; }, 1],
]) {
  test(`${label} never suppresses or closes the unacknowledged page`, async () => {
    const f = fixture(); change(f);
    await assert.rejects(retireOwnedChartPages(f.s, f.pages, f.evidence));
    assert.equal(f.requests.length, requestCount); assert.equal(f.suppressed(), false); assert.equal(f.closed(), false);
  });
}
