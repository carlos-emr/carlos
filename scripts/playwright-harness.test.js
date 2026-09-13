/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const harness = require('./lib/playwright-harness');

const {
  SkipCheck, assertStrictPage, createRecorder, createSqlRunner, isLocalTlsTarget,
  parseMysqlBatchOutput, readConfig, runCheck, sqlString, unescapeMysqlBatchValue,
  wirePage, wireStrictPage,
} = harness;

/** A page double that lets a test deliver the events Playwright would. */
function fakePage() {
  const handlers = {};
  return {
    on(event, handler) { (handlers[event] = handlers[event] || []).push(handler); },
    emit(event, payload) { return Promise.all((handlers[event] || []).map((handler) => handler(payload))); },
    handlers,
  };
}

const consoleMessage = (type, text, location = {}) => ({
  type: () => type, text: () => text, location: () => location,
});
const response = (options) => ({
  url: () => options.url,
  status: () => options.status,
  headers: () => ({ 'content-type': options.contentType || '' }),
  request: () => ({ method: () => options.method || 'GET', resourceType: () => options.resourceType || 'document' }),
});

test('mysql -B output is unescaped, so text containing a backslash compares equal', () => {
  // docs/ui-tests/clinical-workflow-browser-checks.md: a column holding a
  // backslash comes back with it doubled. Every private sql() copy skipped this.
  assert.equal(unescapeMysqlBatchValue('a\\\\_b'), 'a\\_b');
  assert.equal(unescapeMysqlBatchValue('line\\nnext'), 'line\nnext');
  assert.equal(unescapeMysqlBatchValue('col\\tsep'), 'col\tsep');
  assert.equal(unescapeMysqlBatchValue('NULL'), null);
  assert.equal(unescapeMysqlBatchValue('plain'), 'plain');
  // One left-to-right pass: an escaped backslash is not re-read as an escape.
  assert.equal(unescapeMysqlBatchValue('\\\\n'), '\\n');
});

test('mysql -B rows split on tabs and newlines and tolerate an empty result', () => {
  assert.deepEqual(parseMysqlBatchOutput('1\tPW_X\n2\tPW_Y\n'), [['1', 'PW_X'], ['2', 'PW_Y']]);
  assert.deepEqual(parseMysqlBatchOutput(''), []);
  assert.deepEqual(parseMysqlBatchOutput('\n'), []);
});

test('sqlString escapes quotes and backslashes in fixture literals', () => {
  assert.equal(sqlString("O'Brien"), "'O''Brien'");
  assert.equal(sqlString('a\\b'), "'a\\\\b'");
});

test('TLS verification is only waived for a demonstrably local target (issue #3598)', () => {
  for (const host of ['localhost', '127.0.0.1', '::1', '10.1.2.3', '192.168.0.9', '172.16.0.1']) {
    assert.equal(isLocalTlsTarget(host), true, `${host} should count as local`);
  }
  for (const host of ['example.com', 'carlos.example.org', '8.8.8.8', '172.32.0.1']) {
    assert.equal(isLocalTlsTarget(host), false, `${host} must not waive certificate checks`);
  }
});

test('readConfig waives certificate checks for a loopback target and not for a remote one', () => {
  const local = readConfig({ env: { BASE_URL: 'http://127.0.0.1:8080/carlos' } });
  assert.equal(local.ignoreHTTPSErrors, true);
  const remote = readConfig({ env: { BASE_URL: 'https://carlos.example.org/carlos', ALLOW_NON_LOCAL_BASE_URL: 'true' } });
  assert.equal(remote.ignoreHTTPSErrors, false);
});

test('readConfig supplies the devcontainer defaults and the documented aliases', () => {
  const config = readConfig({ env: {} });
  assert.equal(config.testUser, 'carlosdoc');
  assert.equal(config.testPin, '2026');
  assert.equal(config.mysql.database, 'carlos');
  assert.equal(config.expectFrontDoor, false);
  const aliased = readConfig({ env: { CARLOS_USER: 'someone', CHROMIUM_PATH: '/x/chrome' } });
  assert.equal(aliased.testUser, 'someone');
  assert.equal(aliased.chromePath, '/x/chrome');
});

test('a missing required variable is a SkipCheck, not a failure (issue #3313)', () => {
  assert.throws(
    () => readConfig({ env: {}, require: ['EFORM_CORPUS_DIR'] }),
    (error) => error instanceof SkipCheck && /EFORM_CORPUS_DIR/.test(error.message),
  );
  assert.doesNotThrow(() => readConfig({ env: { EFORM_CORPUS_DIR: '/tmp/corpus' }, require: ['EFORM_CORPUS_DIR'] }));
});

test('the sql runner keeps the password out of argv and removes its option file', () => {
  const calls = [];
  const runner = createSqlRunner(
    { host: '127.0.0.1', user: 'root', password: 'sup3r-secret', database: 'carlos' },
    { exec: (file, args) => { calls.push({ file, args }); return '7\n'; }, env: {} },
  );
  assert.equal(runner.value('SELECT 7'), '7');
  const [call] = calls;
  assert.equal(call.file, 'mysql');
  assert.ok(!call.args.some((argument) => argument.includes('sup3r-secret')), 'the password must never reach argv');
  const optionArgument = call.args.find((argument) => argument.startsWith('--defaults-extra-file='));
  const optionFile = optionArgument.slice('--defaults-extra-file='.length);
  assert.equal((fs.statSync(optionFile).mode & 0o777), 0o600);
  assert.match(fs.readFileSync(optionFile, 'utf8'), /sup3r-secret/);
  runner.dispose();
  assert.equal(fs.existsSync(optionFile), false, 'dispose must remove the credential file');
});

test('the sql runner refuses a non-loopback host without the explicit opt-in', () => {
  assert.throws(
    () => createSqlRunner({ host: 'db.example.com', password: 'x' }, { exec: () => '', env: {} }),
    /non-loopback MYSQL_HOST/,
  );
});

test('wireStrictPage fails the check on the JavaScript signals nothing asserted before', async () => {
  const recorder = createRecorder();
  const page = fakePage();
  wireStrictPage(page, 'chart', recorder, { baseline: [] });

  await page.emit('pageerror', new TypeError('getSomething is not a function'));
  await page.emit('console', consoleMessage('error', 'ReferenceError: contextPath is not defined'));
  // issue #3313 item 3: a script answered as text/html is an error page where code
  // should be, so the editor never initialises.
  await page.emit('response', response({
    url: 'https://host/carlos/eform/displayImage.do?imagefile=stamps.js', status: 200, contentType: 'text/html;charset=UTF-8', resourceType: 'script',
  }));
  await page.emit('requestfailed', {
    url: () => 'https://host/carlos/js/app.js', resourceType: () => 'script', failure: () => ({ errorText: 'net::ERR_ABORTED' }),
  });

  assert.equal(recorder.pageErrors.length, 1);
  assert.equal(recorder.consoleIssues.length, 1);
  assert.equal(recorder.badResponses.length, 1);
  assert.match(recorder.badResponses[0].reason, /text\/html/);
  assert.equal(recorder.requestFailures.length, 1);
  assert.throws(() => assertStrictPage(recorder), /JavaScript-layer problem/);
});

test('an unanswered confirm() is a finding, and an expected one is not', async () => {
  const strict = createRecorder();
  const strictPage = fakePage();
  wireStrictPage(strictPage, 'delete', strict, { baseline: [] });
  await strictPage.emit('dialog', { type: () => 'confirm', message: () => 'Delete?', dismiss: async () => {} });
  assert.equal(strict.unexpectedDialogs.length, 1);
  assert.throws(() => assertStrictPage(strict), /unexpected confirm dialog/);

  const expected = createRecorder();
  const expectedPage = fakePage();
  let accepted = false;
  wireStrictPage(expectedPage, 'delete', expected, {
    baseline: [],
    dialogHandler: async (dialog) => { accepted = true; await dialog.accept(); },
  });
  await expectedPage.emit('dialog', { type: () => 'confirm', message: () => 'Delete?', accept: async () => {} });
  assert.equal(accepted, true);
  assert.equal(expected.unexpectedDialogs.length, 0);
  assert.equal(expected.dialogs.length, 1);
});

test('the console baseline suppresses only its listed legacy defects', async () => {
  const recorder = createRecorder();
  const page = fakePage();
  wireStrictPage(page, 'rx', recorder);
  // #3578, on the baseline: tolerated until the issue closes.
  await page.emit('pageerror', new TypeError("Cannot set properties of null at expandPreview (preview.js:12)"));
  assert.equal(recorder.pageErrors.length, 0);
  // Anything else is the finding.
  await page.emit('pageerror', new TypeError('somethingNew is not a function'));
  assert.equal(recorder.pageErrors.length, 1);
});

test('every console-baseline entry names where its removal is tracked', () => {
  const baseline = JSON.parse(fs.readFileSync(path.join(__dirname, 'lib', 'console-baseline.json'), 'utf8'));
  assert.ok(baseline.allow.length > 0);
  for (const entry of baseline.allow) {
    assert.ok(entry.match, 'an entry needs a match');
    assert.ok(entry.issue, `${entry.match} must name the issue or doc that tracks removing it`);
    assert.ok(entry.note && entry.note.length > 40, `${entry.match} must explain the defect`);
  }
});

test('baseline entries are literal substrings, never compiled patterns', () => {
  // Semgrep's detect-non-literal-regexp flagged the earlier new RegExp(entry.pattern)
  // four times. Nothing the baseline needs is a pattern, so the construct is gone
  // rather than suppressed: a committed pathological regex would have hung a check
  // instead of failing it.
  const source = fs.readFileSync(path.join(__dirname, 'lib', 'playwright-harness.js'), 'utf8');
  assert.doesNotMatch(source, /new RegExp\(/, 'the harness must not build a regex at runtime');
});

test('a baseline entry that is too short, or looks like a regex, is refused at load', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-baseline-'));
  const write = (allow) => {
    const file = path.join(directory, `b${Math.random().toString(36).slice(2)}.json`);
    fs.writeFileSync(file, JSON.stringify({ allow }));
    return file;
  };
  const good = { match: 'expandPreview', issue: '#1', note: 'x'.repeat(50) };

  // Short fragments blanket-suppress far more than the one defect they name.
  assert.throws(
    () => harness.loadConsoleBaseline(write([{ ...good, match: 'null' }])),
    /too short to identify one defect/,
  );
  // A regex added by mistake would otherwise match nothing and silently stop working.
  assert.throws(
    () => harness.loadConsoleBaseline(write([{ ...good, match: 'expand.*Preview' }])),
    /looks like a regular expression/,
  );
  assert.throws(
    () => harness.loadConsoleBaseline(write([{ match: 'expandPreview', issue: '#1' }])),
    /needs a match, the issue/,
  );
  assert.equal(harness.loadConsoleBaseline(write([good])).length, 1);
  fs.rmSync(directory, { recursive: true, force: true });
});

test('legacy wirePage records exactly what it recorded before the harness landed', async () => {
  // 75 checks call wirePage. Migrating one to the strict contract must be a
  // reviewed change to that check, not a new failure mode arriving underneath it.
  const recorder = createRecorder();
  const page = fakePage();
  wirePage(page, 'legacy', recorder);
  await page.emit('response', response({
    url: 'https://host/x.js', status: 200, contentType: 'text/html', resourceType: 'script',
  }));
  await page.emit('requestfailed', {
    url: () => 'https://host/y.js', resourceType: () => 'script', failure: () => ({ errorText: 'boom' }),
  });
  await page.emit('dialog', { type: () => 'confirm', message: () => 'Delete?', dismiss: async () => {} });
  assert.deepEqual(recorder.badResponses, []);
  assert.deepEqual(recorder.requestFailures, []);
  assert.deepEqual(recorder.unexpectedDialogs, []);
  // A real 4xx is still recorded, exactly as before.
  await page.emit('response', response({ url: 'https://host/z', status: 500, resourceType: 'document' }));
  assert.equal(recorder.badResponses.length, 1);
});

test('runCheck reports PASS/FAIL/SKIP with the exit code the runner distinguishes', async () => {
  const lines = [];
  const stdout = { log: (line) => lines.push(line) };

  const passed = { exitCode: null, env: {}, on() {}, removeListener() {} };
  await runCheck({ name: 'ok', run: async () => 'value', stdout, processRef: passed });
  assert.equal(passed.exitCode, 0);
  assert.match(lines[0], /^PASS ok$/);

  const failed = { exitCode: null, env: {}, on() {}, removeListener() {} };
  await runCheck({ name: 'bad', run: async () => { throw new Error('the row was not written'); }, stdout, processRef: failed });
  assert.equal(failed.exitCode, 1);
  assert.match(lines[1], /^FAIL bad -- the row was not written$/);

  const skipped = { exitCode: null, env: {}, on() {}, removeListener() {} };
  await runCheck({ name: 'nofixture', run: async () => { throw new SkipCheck('EFORM_CORPUS_DIR is not set'); }, stdout, processRef: skipped });
  assert.equal(skipped.exitCode, 2, 'a missing fixture is a skip, not a failure');
  assert.match(lines[2], /^SKIP nofixture/);
});

test('runCheck always runs cleanup, and a cleanup failure is never silent', async () => {
  const lines = [];
  const stdout = { log: (line) => lines.push(line) };
  let cleaned = false;
  const afterThrow = { exitCode: null, env: {}, on() {}, removeListener() {} };
  await runCheck({
    name: 'seeded',
    run: async () => { throw new Error('assertion failed'); },
    cleanup: async () => { cleaned = true; },
    stdout,
    processRef: afterThrow,
  });
  assert.equal(cleaned, true, 'fixtures must be removed even when the body threw');

  const cleanupBroke = { exitCode: null, env: {}, on() {}, removeListener() {} };
  await runCheck({
    name: 'leaky',
    run: async () => 'fine',
    cleanup: async () => { throw new Error('DELETE failed'); },
    stdout,
    processRef: cleanupBroke,
  });
  assert.equal(cleanupBroke.exitCode, 1, 'a check that leaves its rows behind has not passed');
  assert.match(lines[lines.length - 1], /cleanup failed: DELETE failed/);
});

test('runCheck writes a machine-readable result when RESULT_JSON is set', async () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-runcheck-'));
  const resultPath = path.join(directory, 'result.json');
  const processRef = { exitCode: null, env: { RESULT_JSON: resultPath }, on() {}, removeListener() {} };
  await runCheck({ name: 'recorded', run: async () => null, stdout: { log() {} }, processRef });
  const written = JSON.parse(fs.readFileSync(resultPath, 'utf8'));
  assert.equal(written.name, 'recorded');
  assert.equal(written.outcome, 'PASS');
  fs.rmSync(directory, { recursive: true, force: true });
});

test('the harness loads without playwright installed, so CI can unit-test it', () => {
  // CI runs `npm ci --ignore-scripts` with no browser binary. A top-level
  // require('playwright') here would take the whole script-regressions job down.
  const source = fs.readFileSync(path.join(__dirname, 'lib', 'playwright-harness.js'), 'utf8');
  const topLevel = source.split('\nasync function launchBrowser')[0];
  assert.doesNotMatch(topLevel, /require\('playwright'\)/);
});
