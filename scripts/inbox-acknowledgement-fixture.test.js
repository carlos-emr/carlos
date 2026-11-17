/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const {createInboxAcknowledgementFixture} = require('./lib/inbox-acknowledgement-fixture');
const {parseMysqlBatchOutput} = require('./lib/playwright-harness');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function environment({unassigned = false, ignoredRestore = false, parserOutput = false} = {}) {
  const original = parserOutput ? parseMysqlBatchOutput('7\t100\t4E\tNULL\tNULL\n')
    : [['7', '100', '4E', 'NULL', 'NULL']];
  let rows = structuredClone(original);
  let marker = '';
  let disposed = false;
  const writes = [];
  const sql = {
    value: query => query.includes('FROM security') ? '101' : (unassigned ? '1' : '0'),
    rows: query => query.includes('HEX(status)') ? structuredClone(rows)
      : rows.filter(row => row[3] === marker).map(row => [row[0]]),
    execute(query) {
      writes.push(query);
      if (query.startsWith('INSERT')) {
        marker = query.match(/'N','([^']+)'/)[1];
        rows.push(['8', '101', '4E', marker, 'NULL']);
      } else if (query.startsWith('DELETE')) rows = rows.filter(row => row[0] !== '8');
      else if (query.startsWith('UPDATE') && !ignoredRestore) {
        const row = rows.find(row => row[0] === '7');
        if (row) Object.assign(row, structuredClone(original[0]));
      }
    },
    dispose() { disposed = true; },
  };
  return {sql, writes, rows: () => rows, disposed: () => disposed,
    removeOriginal() { rows = rows.filter(row => row[0] !== '7'); }};
}
const config = {testUser: 'review-user', mysql: {}};
const view = chain => ({evaluate: async () => chain});

test('restores acknowledged older versions and deletes only pretracked missing routes', async t => {
  // A marker may contain a later lab's digits without routing that lab.
  t.mock.method(require('node:crypto'), 'randomUUID', () => '00000102-0000-4000-8000-000000000000');
  const env = environment();
  const fixture = createInboxAcknowledgementFixture(config, () => env.sql);
  await fixture.prepare(view('100,101,102'), 'HL7:101');
  env.rows().forEach(row => { row[2] = '41'; row[3] = ''; row[4] = '32303236'; });
  fixture.cleanup();
  assert.deepEqual(env.rows(), [['7', '100', '4E', 'NULL', 'NULL']]);
  assert.equal(env.writes.length, 3);
  assert.match(env.writes[0], /VALUES \('101',101,'N',/);
  assert.match(env.writes[1], /^DELETE FROM providerLabRouting WHERE id=8 AND /);
  assert.match(env.writes[2], /WHERE id=7 AND lab_no=100 AND /);
  for (const query of env.writes.slice(1)) {
    assert.match(query, /AND lab_no IN \(100,101\)$/);
  }
  assert.equal(env.disposed(), true);
});

for (const failure of ['missing original row', 'ignored restoration']) {
  test(`fails cleanup instead of silently passing with ${failure}`, async () => {
    const env = environment({ignoredRestore: failure === 'ignored restoration'});
    const fixture = createInboxAcknowledgementFixture(config, () => env.sql);
    await fixture.prepare(view('100'), 'HL7:100');
    if (failure === 'missing original row') env.removeOriginal();
    else env.rows()[0][2] = '41';
    assert.throws(() => fixture.cleanup(), /exact routing snapshot/);
    assert.equal(env.disposed(), true);
  });
}

test('refuses provider-zero routes before any mutation and disposes credentials', async () => {
  const env = environment({unassigned: true});
  const fixture = createInboxAcknowledgementFixture(config, () => env.sql);
  await assert.rejects(fixture.prepare(view('100'), 'HL7:100'), {name: 'SkipCheck'});
  fixture.cleanup();
  assert.deepEqual(env.writes, []);
  assert.equal(env.disposed(), true);
});

test('rejects malformed source chains before querying the database', async () => {
  const fixture = createInboxAcknowledgementFixture(config, () => { throw new Error('must not query'); });
  await assert.rejects(fixture.prepare(view('100,1 OR 1=1'), 'HL7:100'), /Invalid acknowledgement/);
});

for (const mode of ['preview', 'rapid']) {
  test(`actual native ${mode} main composes parser-null routing preparation and post-browser cleanup`, async () => {
    const filename = mode === 'preview' ? 'inbox-preview-acknowledge-playwright-checks.js'
      : 'inbox-rapid-review-advance-playwright-checks.js';
    const source = fs.readFileSync(path.join(__dirname, filename), 'utf8');
    const env = environment({parserOutput: true});
    assert.equal(env.rows()[0][3], null);
    const prepared = new Error('Stop after actual routing preparation; do not mock a clinical acknowledgement');
    const order = [], cleanup = [];
    const real = createInboxAcknowledgementFixture(config, () => env.sql);
    const frame = view('100');
    const target = {identity: 'HL7:100', index: 1, frame, popup: frame};
    const scenario = {inbox: {evaluate: async () => true}, target,
      before: ['HL7:99', 'HL7:100', 'HL7:101']};
    const fixture = {async prepare(...args) {
      await real.prepare(...args); order.push('routing-prepared'); throw prepared;
    }, cleanup() { real.cleanup(); order.push('routing-cleaned'); }};
    const module = {exports: {}};
    const requireForCaller = name => {
      if (name === './lib/playwright-harness') return {assert, SkipCheck: Error};
      if (name === './lib/inbox-acknowledgement-fixture') return {createInboxAcknowledgementFixture: () => fixture};
      if (name === './lib/playwright-ui') return {};
      if (name === './inboxhub-filters-playwright-checks') return {settle: async () => {}};
      if (name === './inbox-preview-acknowledge-playwright-checks') return {widenToAnyProvider: async () => {}};
      throw new Error('Unexpected native-main dependency: ' + name);
    };
    const substitutions = mode === 'preview'
      ? 'openInbox=async()=>scenario.inbox;enterPreviewMode=async()=>3;settleList=async()=>{};loadEveryPreviewPage=async()=>{};shownCards=async()=>scenario.before;findAcknowledgeable=async()=>scenario.target;'
      : 'openInbox=async()=>scenario.inbox;enterListMode=async()=>3;rowsInDisplayOrder=async()=>scenario.before;openAcknowledgeableTarget=async()=>scenario.target;';
    vm.runInNewContext(source + '\n' + substitutions, {module, exports: module.exports,
      require: requireForCaller, process: {env: {}}, scenario}, {filename});
    const session = {config, recorder: {}, schedule: {},
      context: {async close() { order.push('browser-drained'); }}, cleanup(fn) { cleanup.push(fn); }};
    await assert.rejects(module.exports.main({session}), error => error === prepared);
    assert.deepEqual(order, ['routing-prepared', 'browser-drained']);
    for (const dispose of cleanup.reverse()) await dispose();
    assert.deepEqual(order, ['routing-prepared', 'browser-drained', 'routing-cleaned']);
    assert.equal(env.disposed(), true);
    assert.deepEqual(env.rows(), parseMysqlBatchOutput('7\t100\t4E\tNULL\tNULL\n'));
    assert.match(env.writes.at(-1), /comment=NULL,\s+timestamp=NULL/);
  });
}
