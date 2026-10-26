/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { createJournalLogSource, MAX_BYTES, MAX_RECORDS, COMMAND_TIMEOUT_MS } = require('./logout-journal-log');

function record(id, message = 'routine log entry', timestamp = '1780000000123456') {
  return { __CURSOR: `s=123;i=${id};b=456;m=789;t=abc;x=def`, __REALTIME_TIMESTAMP: timestamp, MESSAGE: message };
}
function success(entries) {
  return { status: 0, stdout: entries.map(entry => JSON.stringify(entry)).join('\n') + '\n', stderr: '' };
}
function harness(results) {
  const calls = [];
  const source = createJournalLogSource('carlos-emr.service', { run(command, args, options) {
    calls.push({ command, args, options });
    assert(results.length, 'unexpected journal command');
    const result = results.shift();
    if (result instanceof Error) throw result;
    return result;
  } });
  return { source, calls };
}

test('journal unit must be one explicit service name before a command is attempted', () => {
  for (const unit of ['', undefined, '--system', '-bad.service', 'carlos-emr', 'a/b.service', 'a.service\nb.service', 'a.service\n', 'a b.service', '*.service', 'a'.repeat(200) + '.service']) {
    assert.throws(() => createJournalLogSource(unit, { run() { throw new Error('command must not run'); } }), /must name one explicit/);
  }
  for (const unit of ['carlos-emr.service', 'carlos@alpha.service']) assert.doesNotThrow(() => createJournalLogSource(unit));
});

test('journal captures the starting cursor and reads only messages inside both verified boundaries', () => {
  const before = record('1', 'old failure must not be included', '1780000000000000');
  const middle = record('2', 'first new message', '1780000000100000');
  const end = record('3', 'last new message');
  const after = record('4', 'later concurrent message');
  const h = harness([success([before]), success([end]), success([before, middle, end, after])]);
  const snapshot = h.source.capture();
  assert.deepEqual(h.source.readDelta(snapshot), { text: 'first new message\nlast new message', entries: 2 });
  assert.equal(h.calls.length, 3);
  for (const call of h.calls) {
    assert.equal(call.command, 'journalctl');
    assert(call.args.includes('--unit=carlos-emr.service'));
    assert(call.args.includes('--boot=0'));
    assert(call.args.includes('--all'));
    assert.equal(call.args.includes('--quiet'), false, 'permission warnings must remain visible to the helper');
    assert.equal(call.options.timeout, COMMAND_TIMEOUT_MS);
    assert.equal(call.options.maxBuffer, MAX_BYTES);
    assert.deepEqual(call.options.stdio, ['ignore', 'pipe', 'pipe']);
  }
  assert(h.calls[2].args.includes(`--cursor=${before.__CURSOR}`));
  assert(h.calls[2].args.includes('--until=@1780000000.123456'));
  assert(h.calls[2].args.includes(`--lines=+${MAX_RECORDS}`));
});

test('same valid start and end cursor is a legitimate empty interval', () => {
  const before = record('1');
  const h = harness([success([before]), success([before])]);
  assert.deepEqual(h.source.readDelta(h.source.capture()), { text: '', entries: 0 });
  assert.equal(h.calls.length, 2);
});

for (const [label, result] of [
  ['permission warning despite exit zero', { status: 0, stdout: JSON.stringify(record('1')), stderr: 'private access diagnostic' }],
  ['permission error', { status: 1, stdout: 'private output', stderr: 'private credentials' }],
  ['command unavailable', { error: new Error('private path'), status: null }],
  ['timeout', { signal: 'SIGTERM', status: null }],
  ['buffer overflow', { error: Object.assign(new Error('private stderr'), { code: 'ENOBUFS' }), status: null }],
  ['thrown command error', new Error('private command arguments')],
]) {
  test(`journal fails visibly and redacts ${label}`, () => {
    const h = harness([result]);
    assert.throws(() => h.source.capture(), error => {
      assert.match(error.message, /Journal log scan/);
      assert.doesNotMatch(error.message, /private|credentials|arguments/);
      return true;
    });
  });
}

for (const [label, entries] of [
  ['no journal records', []],
  ['multiple capture records', [record('1'), record('2')]],
  ['missing cursor', [{ __REALTIME_TIMESTAMP: '123', MESSAGE: 'hidden' }]],
  ['invalid cursor', [{ ...record('1'), __CURSOR: 's=1\nprivate' }]],
  ['missing timestamp', [{ __CURSOR: 's=1', MESSAGE: 'hidden' }]],
  ['nondecimal timestamp', [{ ...record('1'), __REALTIME_TIMESTAMP: '1;private' }]],
  ['binary message', [{ ...record('1'), MESSAGE: [112, 114, 105, 118, 97, 116, 101] }]],
]) {
  test(`journal rejects ${label} rather than reporting an empty successful scan`, () => {
    const h = harness([success(entries)]);
    assert.throws(() => h.source.capture(), /Journal log scan/);
  });
}

test('malformed JSON never leaks its source text', () => {
  const h = harness([{ status: 0, stdout: 'private malformed content', stderr: '' }]);
  assert.throws(() => h.source.capture(), error => error.message === 'Journal log scan returned malformed records');
});

test('journal bounds byte and record count even if the command ignores its limits', () => {
  const bytes = harness([{ status: 0, stdout: 'x'.repeat(MAX_BYTES + 1), stderr: '' }]);
  assert.throws(() => bytes.source.capture(), /output limit/);
  const rows = harness([success(Array.from({ length: MAX_RECORDS + 1 }, (_, i) => record(String(i))))]);
  assert.throws(() => rows.source.capture(), /record limit/);
});

for (const [label, interval] of [
  ['vacuumed starting cursor', [record('2'), record('3')]],
  ['empty interval', []],
  ['absent ending cursor', [record('1'), record('2')]],
  ['changed starting timestamp', [record('1', '', '1'), record('3')]],
  ['changed ending timestamp', [record('1'), record('3', '', '2')]],
  ['duplicate interior cursor', [record('1'), record('2'), record('2'), record('3')]],
]) {
  test(`journal refuses ${label}`, () => {
    const h = harness([success([record('1')]), success([record('3')]), success(interval)]);
    assert.throws(() => h.source.readDelta(h.source.capture()), /Journal log scan/);
  });
}

test('invalid caller cursor fails before issuing a command', () => {
  const h = harness([]);
  assert.throws(() => h.source.readDelta({ __CURSOR: '--private', __REALTIME_TIMESTAMP: 'bad' }), /invalid starting cursor/);
  assert.equal(h.calls.length, 0);
});

test('same cursor with changed metadata fails instead of reporting no new records', () => {
  const h = harness([success([record('1')]), success([record('1', '', '1')])]);
  assert.throws(() => h.source.readDelta(h.source.capture()), /boundary metadata changed/);
});

const script = fs.readFileSync(path.join(__dirname, 'logout-redirect-playwright-checks.js'), 'utf8');
function functionSource(name) {
  const start = script.indexOf(`function ${name}(`);
  assert(start >= 0);
  return script.slice(start, script.indexOf('\n}', start) + 2);
}
function scriptHarness(journalLog, file = '') {
  const context = vm.createContext({ journalLog, carlosLogFile: file, visited: [], findings: [], Buffer, fs });
  for (const name of ['summarizeText', 'captureLogSnapshot', 'readLogDelta', 'checkLogDelta']) {
    vm.runInContext(functionSource(name), context);
  }
  return context;
}

test('logout script rejects simultaneous file and journal configuration before starting browser', () => {
  const start = script.indexOf('const carlosLogFile =');
  const end = script.indexOf('const timeout =', start);
  assert.throws(() => vm.runInNewContext(script.slice(start, end), {
    process: { env: { CARLOS_LOG_FILE: '/private/file', CARLOS_LOG_JOURNAL_UNIT: 'carlos-emr.service' } }, createJournalLogSource,
  }), /Choose either CARLOS_LOG_FILE or CARLOS_LOG_JOURNAL_UNIT/);
});

test('logout script checks journal delta with existing failure patterns and emits only metadata', () => {
  const secret = 'private patient detail';
  const h = scriptHarness({ capture: () => ({ __CURSOR: 's=1' }), readDelta: () => ({
    text: `IllegalStateException: response committed ${secret}\nDmsInboxManage2Action`, entries: 1,
  }) });
  h.checkLogDelta(h.captureLogSnapshot());
  assert.equal(h.findings.length, 1);
  assert.deepEqual(Array.from(h.findings[0].signals), ['response-commit-failure', 'affected-action-stacktrace']);
  assert.equal(h.visited[0].source, 'journal');
  assert.equal(h.visited[0].checked, true);
  assert.doesNotMatch(JSON.stringify({ findings: h.findings, visited: h.visited }), /private|patient|s=1/);
});

test('logout script reports a checked zero-entry journal interval successfully', () => {
  const h = scriptHarness({ capture: () => ({}), readDelta: () => ({ text: '', entries: 0 }) });
  h.checkLogDelta(h.captureLogSnapshot());
  assert.equal(h.findings.length, 0);
  assert.equal(h.visited[0].checked, true);
  assert.equal(h.visited[0].entries, 0);
});

test('logout script propagates a journal failure instead of marking log scan checked', () => {
  const h = scriptHarness({ capture: () => ({}), readDelta() { throw new Error('Journal log scan failed'); } });
  assert.throws(() => h.checkLogDelta(h.captureLogSnapshot()), /Journal log scan failed/);
  assert.equal(h.visited.length, 0);
});

test('logout script explicitly reports when neither log source is selected', () => {
  const h = scriptHarness(null);
  assert.equal(h.captureLogSnapshot(), null);
  assert.equal(h.visited[0].checked, false);
  assert.equal(h.visited[0].source, 'none');
});
