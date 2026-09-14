/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const harness = require('./lib/playwright-harness');

const {
  SkipCheck, assertStrictPage, createRecorder, createSqlRunner, isLocalTlsTarget,
  parseMysqlBatchOutput, readConfig, relabelStrictPage, runCheck, sqlString, unescapeMysqlBatchValue,
  wirePage, wireStrictPage, withExpectedDialogs,
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

test('a failing query never carries its own text into the error a run logs', () => {
  // execFileSync folds captured stderr into the thrown error's message, and
  // runCheck logs that message -- so the mysql client's echo of the statement
  // would reach the log, and a statement can carry a patient name or a note.
  const runner = createSqlRunner({ host: '127.0.0.1', password: 'secret' }, {
    exec: () => {
      const error = new Error('Command failed: mysql\nERROR 1064 near \'last_name="Wolowitz"\'');
      error.status = 1;
      error.stderr = 'ERROR 1064 near \'last_name="Wolowitz"\'';
      throw error;
    },
  });
  try {
    assert.throws(() => runner.value('SELECT last_name FROM demographic WHERE demographic_no = 2'), (error) => {
      assert.ok(!/Wolowitz|last_name|SELECT/.test(error.message),
        `the bounded message still leaks the statement: ${error.message}`);
      assert.match(error.message, /database query failed/);
      assert.match(error.message, /mysql exit 1/, 'the exit status is safe and worth keeping');
      return true;
    });
  } finally {
    runner.dispose();
  }
});

test('a timed-out query is reported as a timeout, not as a generic failure', () => {
  // The distinction is the whole point of keeping any detail at all: a timeout
  // means the database is unreachable or wedged, a refusal means the query is
  // wrong, and a run that cannot tell them apart wastes the next hour.
  const runner = createSqlRunner({ host: '127.0.0.1', password: 'secret' }, {
    exec: () => {
      const error = new Error('Command failed');
      error.code = 'ETIMEDOUT';
      throw error;
    },
  });
  try {
    assert.throws(() => runner.value('SELECT 1'), /timed out after 30s/);
  } finally {
    runner.dispose();
  }
});

/*
 * A control can navigate the SAME page instead of opening a popup (the
 * schedule's Search is a same-tab href under the caisi module), so the page a
 * check later asserts against is often the one it logged in on. Before the
 * label became mutable, every caller scoping assertStrictPage to
 * ['patient-search', ...] was scoping to a label nothing had been recorded
 * under: the assertion ran, found nothing, and passed.
 */

test('a relabelled page records new findings under the new label', async () => {
  const recorder = createRecorder();
  const page = fakePage();
  wireStrictPage(page, 'login', recorder);

  await page.emit('pageerror', new Error('before the navigation'));
  assert.equal(recorder.pageErrors[0].label, 'login');

  assert.equal(relabelStrictPage(page, 'patient-search'), true);
  await page.emit('pageerror', new Error('after the navigation'));
  assert.equal(recorder.pageErrors[1].label, 'patient-search');

  // And the scoped assertion now actually sees it, which is the whole point.
  assert.throws(() => assertStrictPage(recorder, ['patient-search']), /after the navigation/);
});

test('an earlier finding keeps the label it was recorded under', () => {
  // Relabelling must not rewrite history: the error that happened on the login
  // page happened there, and a run that says otherwise sends someone to the
  // wrong page.
  const recorder = createRecorder();
  const page = fakePage();
  wireStrictPage(page, 'login', recorder);
  return page.emit('pageerror', new Error('on login')).then(() => {
    relabelStrictPage(page, 'patient-search');
    assert.equal(recorder.pageErrors[0].label, 'login');
  });
});

test('relabelling a page that was never wired reports false rather than pretending', () => {
  assert.equal(relabelStrictPage(fakePage(), 'whatever'), false);
});

test('wiring the same page twice relabels it instead of doubling every finding', async () => {
  // Two listener sets would record every finding twice and, worse, a second
  // dialog listener races the first -- the failure already documented on
  // dialogHandler.
  const recorder = createRecorder();
  const page = fakePage();
  wireStrictPage(page, 'first', recorder);
  wireStrictPage(page, 'second', recorder);
  assert.equal(page.handlers.pageerror.length, 1, 'a second wiring must not add a second listener');
  assert.equal(page.handlers.dialog.length, 1);
  await page.emit('pageerror', new Error('once'));
  assert.equal(recorder.pageErrors.length, 1);
  assert.equal(recorder.pageErrors[0].label, 'second');
});

/*
 * withExpectedDialogs.
 *
 * A check that deliberately triggers a confirm() used to add its own
 * page.on('dialog') listener. Playwright delivers to EVERY listener, so the
 * strict one still recorded the dialog as unexpected -- and assertStrictPage()
 * then failed precisely when the validation under test was working. There is
 * one dialog listener per page, and this is how a step borrows it.
 */
test('an expected dialog is answered and is not recorded as unexpected', async () => {
  const recorder = createRecorder();
  const page = fakePage();
  wireStrictPage(page, 'patient-search', recorder);
  let accepted = false;
  const seen = await withExpectedDialogs(page, async () => {
    await page.emit('dialog', {
      type: () => 'alert',
      message: () => 'Please enter a valid date',
      accept: async () => { accepted = true; },
    });
  });
  assert.equal(accepted, true, 'the expected dialog must still be answered');
  assert.equal(seen.length, 1);
  assert.match(seen[0].text, /valid date/);
  assert.equal(recorder.unexpectedDialogs.length, 0,
    'a dialog the check asked for is not a finding');
  assertStrictPage(recorder, ['patient-search']);
});

test('the strict handler is restored after the step, even when it throws', async () => {
  const recorder = createRecorder();
  const page = fakePage();
  wireStrictPage(page, 'patient-search', recorder);
  await assert.rejects(() => withExpectedDialogs(page, async () => {
    throw new Error('the assertion inside the step failed');
  }), /the assertion inside the step failed/);
  // Back to strict: a dialog raised afterwards is a finding again.
  await page.emit('dialog', { type: () => 'confirm', message: () => 'Delete?', dismiss: async () => {} });
  assert.equal(recorder.unexpectedDialogs.length, 1);
});

test('withExpectedDialogs refuses a page that was never wired', async () => {
  // On an unwired page there is no strict handler to stand in for, so the
  // dialog would go unanswered and the click would hang until the timeout.
  await assert.rejects(() => withExpectedDialogs(fakePage(), async () => {}),
    /needs a page wired by wireStrictPage/);
});

test('a dialog can be dismissed rather than accepted when that is the user path', async () => {
  const recorder = createRecorder();
  const page = fakePage();
  wireStrictPage(page, 'chart', recorder);
  let dismissed = false;
  await withExpectedDialogs(page, async () => {
    await page.emit('dialog', {
      type: () => 'confirm',
      message: () => 'Discard this note?',
      accept: async () => { throw new Error('must not accept'); },
      dismiss: async () => { dismissed = true; },
    });
  }, { accept: false });
  assert.equal(dismissed, true);
  assert.equal(recorder.unexpectedDialogs.length, 0);
});

test('login works through the authentication stages in whatever order they arrive', () => {
  // Login2Action's forced-reset branch runs BEFORE its MFA branch, so an
  // MFA-enrolled account with a flagged password sees the reset form first --
  // and after a successful reset the action sets forcedpasswordchange = false
  // and falls through to the shared path, which reaches
  // beginPendingMfaChallenge (Login2Action:630) and serves the MFA page again.
  // Handling each stage once, in a fixed order, left such an account sitting on
  // the MFA page while login() returned as though it had succeeded.
  const source = fs.readFileSync(require.resolve('./lib/playwright-harness'), 'utf8');
  const login = source.slice(source.indexOf('async function login'));
  const body = login.slice(0, login.indexOf('\nasync function screenshot'));

  // Both stages are reachable repeatedly, from one loop.
  assert.match(body, /for \(let stage = 0; stage < STAGES; stage \+= 1\)/);
  assert.ok(body.indexOf('loginMfa/i.test(url)') > body.indexOf('for (let stage'),
    'the MFA stage must be inside the loop, not ahead of it');
  assert.ok(body.indexOf('forcepasswordreset/i.test(url)') > body.indexOf('for (let stage'),
    'the reset stage must be inside the loop, not ahead of it');

  // The reset submit must accept a landing on the MFA page, or that hand-off is
  // a 30s timeout instead of the next turn of the loop.
  const resetStage = body.slice(body.indexOf('forcepasswordreset/i.test(url)'));
  assert.match(resetStage.slice(0, resetStage.indexOf('continue;')),
    /waitForURL\(\/providercontrol\|appointment\|select_facility\|loginMfa\/i/);

  // And the loop is bounded, with a diagnosis rather than a silent success when
  // a stage keeps re-serving itself.
  assert.match(body, /const STAGES = \d+;/);
  assert.match(body, /login is still on \$\{pathOnly\(page\.url\(\)\)\} after working through/);
});

/*
 * WHAT LEAVES THE HARNESS WHEN A CHECK FAILS.
 *
 * buildFailureDetails is the one place recorded browser noise crosses out of the
 * run and into a failure message. CARLOS puts demographic_no in the query string
 * of nearly every clinical URL, and CLAUDE.md names it a PHI-correlating
 * operational identifier: it joins straight back to a patient row. Stripping it
 * from the structured `url` field was only half the job -- a pageerror's stack
 * and a console message quote the address they were working on, in prose, in
 * `text` and `message`. Those went out untouched.
 */
test('recorded urls are reduced to their path, losing the identifiers in the query', () => {
  const details = harness.buildFailureDetails({
    badResponses: [{ status: 500, url: 'https://host/carlos/casemgmt/forward.jsp?demographicNo=12345&providerNo=999' }],
    consoleIssues: [], pageErrors: [], requestFailures: [], dialogs: [],
  });
  // The ORIGIN stays on purpose -- which host answered is diagnostic and is not
  // PHI. It is the query that carries demographicNo and providerNo.
  assert.equal(details.badResponses[0].url, 'https://host/carlos/casemgmt/forward.jsp');
  assert.equal(details.badResponses[0].status, 500, 'the status is the finding and must survive');
});

test('the message text is stripped too, not just the url field', () => {
  const details = harness.buildFailureDetails({
    badResponses: [],
    consoleIssues: [{ type: 'error', text: 'Failed to load /carlos/demographic/demographiccontrol.jsp?demographicNo=12345' }],
    pageErrors: [{ message: 'TypeError at https://host/carlos/oscarMDS/Search.do?demographicNo=778 line 4' }],
    requestFailures: [{ errorText: 'net::ERR_ABORTED loading /carlos/lab/lab.jsp?segmentID=44&demographicNo=901' }],
    dialogs: [{ reason: 'confirm on /carlos/appointment/edit.jsp?appointment_no=55' }],
  });
  for (const value of [
    details.consoleIssues[0].text,
    details.pageErrors[0].message,
    details.requestFailures[0].errorText,
    details.dialogs[0].reason,
  ]) {
    assert.ok(!/demographicNo|appointment_no|segmentID|providerNo/i.test(value),
      `an identifier survived into a failure message: ${value}`);
    assert.ok(!value.includes('?'), `a query string survived into a failure message: ${value}`);
  }
  // The message itself is the finding; stripping must not gut it.
  assert.ok(details.pageErrors[0].message.startsWith('TypeError'));
  assert.ok(details.consoleIssues[0].text.startsWith('Failed to load'));
});

test('entries that are not objects pass through rather than throwing', () => {
  const details = harness.buildFailureDetails({
    badResponses: ['a bare string', null],
    consoleIssues: [], pageErrors: [], requestFailures: [], dialogs: [],
  });
  assert.deepEqual(details.badResponses, ['a bare string', null]);
});
