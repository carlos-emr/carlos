/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { EventEmitter } = require('node:events');
const harness = require('./lib/playwright-harness');
const { assertColumn } = require('./next-appointment-lookup-playwright-checks');

const source = fs.readFileSync(path.join(__dirname, 'next-appointment-lookup-playwright-checks.js'), 'utf8');

function scenario(options = {}) {
  const events = [];
  const processRef = new EventEmitter();
  processRef.env = { RESULT_JSON: '/tmp/appointment-result.json', ...options.env };
  let seeded = false;
  let record;
  const sql = {
    value(query) {
      if (query.includes('SELECT last_name')) return 'Synthetic';
      if (query.includes('SELECT COUNT')) return seeded ? '1' : '0';
      return seeded ? '2026-09-15' : '';
    },
    execute(query) {
      if (query.startsWith('INSERT')) {
        seeded = true;
        events.push('insert');
        if (options.interruptAfterInsert) processRef.emit('SIGTERM');
      } else {
        seeded = false;
        events.push('delete');
        if (options.cleanupFails) throw new Error('synthetic cleanup failure');
      }
    },
    dispose() {
      events.push('dispose');
      if (options.disposeFails) throw new Error('synthetic dispose failure');
    },
  };
  const locator = { waitFor: async () => {}, click: async () => {}, first() { return this; } };
  const page = {
    reload: async () => {},
    locator: () => locator,
    keyboard: { type: async () => {} },
    waitForResponse: async () => ({
      status: () => 200,
      headers: () => ({ 'content-type': 'application/json' }),
      json: async () => [{ demographicNo: '1', ...(options.workflowDisabled ? {} : {
        nextAppointment: seeded ? '2026-09-15' : '(none)',
        nextAppt: seeded ? '2026-09-15' : '(none)',
      }) }],
    }),
  };
  const stub = {
    ...harness,
    readConfig: () => ({ mysql: {} }),
    createSqlRunner: () => { events.push('sql'); return sql; },
    launchBrowser: async () => {
      if (options.launchFails) throw new Error('synthetic browser launch failure');
      return { close: async () => {
        events.push('close');
        if (options.interruptOnClose) processRef.emit('SIGTERM');
        if (options.closeFails) throw new Error('synthetic browser close failure');
      } };
    },
    newContext: async () => ({}),
    login: async () => page,
  };
  const mod = { exports: {} };
  const requireStub = (name) => {
    assert.equal(name, './lib/playwright-harness');
    return stub;
  };
  vm.runInNewContext(options.source || source, {
    require: requireStub, module: mod, process: processRef, URL, URLSearchParams,
    console: { log() {} },
  }, { filename: 'next-appointment-lookup-playwright-checks.js' });
  return {
    events, processRef,
    get seeded() { return seeded; },
    get record() { return record; },
    async run() {
      return harness.runCheck({
        name: 'next-appointment-lookup', run: mod.exports.main, processRef,
        stdout: { log() {} }, writeFile: (_file, text) => { record = JSON.parse(text); },
      });
    },
  };
}

test('column and alias mismatch diagnostics do not disclose appointment dates', () => {
  assertColumn({ nextAppointment: '2026-09-15', nextAppt: '2026-09-15' }, '2026-09-15', 'after seeding');
  for (const row of [
    { nextAppointment: '2026-09-15', nextAppt: '2026-09-15' },
    { nextAppointment: '2026-10-01', nextAppt: '2026-09-15' },
  ]) {
    assert.throws(() => assertColumn(row, '2026-10-01', 'after seeding'), (error) => {
      assert.doesNotMatch(error.message, /2026-|2026-10-01|2026-09-15/);
      return true;
    });
  }
});

test('successful run reports PASS only after fixtures and resources are cleaned', async () => {
  const check = scenario();
  const result = await check.run();
  assert.equal(result.outcome, 'PASS');
  assert.equal(check.processRef.exitCode, 0);
  assert.equal(check.record.outcome, 'PASS');
  assert.equal(check.seeded, false);
  assert.deepEqual(check.events, ['sql', 'insert', 'delete', 'delete', 'dispose', 'close']);
});

test('disabled workflow reports explicit SKIP without inserting a fixture', async () => {
  const check = scenario({ workflowDisabled: true });
  const result = await check.run();
  assert.equal(result.outcome, 'SKIP');
  assert.equal(check.processRef.exitCode, 2);
  assert.equal(check.record.outcome, 'SKIP');
  assert.match(result.detail, /workflow_enhance/);
  assert.equal(check.events.includes('insert'), false);
  assert.deepEqual(check.events.slice(-2), ['dispose', 'close']);
});

for (const when of ['interruptAfterInsert', 'interruptOnClose']) {
  test(`${when} reports FAIL in RESULT_JSON and restores the fixture`, async () => {
    const check = scenario({ [when]: true });
    const result = await check.run();
    assert.equal(result.outcome, 'FAIL');
    assert.equal(result.detail, 'interrupted');
    assert.equal(check.record.outcome, 'FAIL');
    assert.equal(check.processRef.exitCode, 143);
    assert.equal(check.seeded, false);
    assert.deepEqual(check.events.slice(-2), ['dispose', 'close']);
  });
}

for (const failure of ['cleanupFails', 'disposeFails', 'closeFails']) {
  test(`${failure} remains a failure and attempts browser cleanup`, async () => {
    const check = scenario({ [failure]: true, workflowDisabled: true });
    const result = await check.run();
    assert.equal(result.outcome, 'FAIL');
    assert.equal(check.record.outcome, 'FAIL');
    assert.equal(check.processRef.exitCode, 1);
    assert.ok(check.events.includes('close'));
  });
}

test('launch failure removes the SQL credential file without inserting fixtures', async () => {
  const check = scenario({ launchFails: true });
  assert.equal((await check.run()).outcome, 'FAIL');
  assert.deepEqual(check.events, ['sql', 'dispose']);
});

for (const value of ['0', '-1', 'NaN', 'Infinity']) {
  test(`invalid timeout ${value} fails before acquiring resources`, async () => {
    const check = scenario({ env: { NEXT_APPT_TIMEOUT_MS: value } });
    assert.equal((await check.run()).outcome, 'FAIL');
    assert.equal(check.processRef.exitCode, 1);
    assert.match(check.record.detail, /positive finite/);
    assert.deepEqual(check.events, []);
  });
}
