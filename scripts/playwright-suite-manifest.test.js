/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { spawnSync } = require('node:child_process');

const { loadManifest, parseArguments, selectChecks, toJUnit } = require('./run-playwright-suite');
const { NAVIGATION, REQUIRED_SECTIONS } = require('./lib/playwright-ui');

const VALID_TIERS = new Set(['smoke', 'core', 'extended', 'front-door', 'standalone', 'live-external']);
const checks = loadManifest();

function suiteScripts(directory = __dirname, prefix = 'scripts') {
  const found = [];
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const relative = `${prefix}/${entry.name}`;
    if (entry.isDirectory()) {
      found.push(...suiteScripts(path.join(directory, entry.name), relative));
    } else if (entry.isFile() && (entry.name.endsWith('-playwright-checks.js')
      || entry.name === 'demographic-master-crud-smoke.js')) {
      found.push(relative);
    }
  }
  return found.sort();
}

test('browser inventory finds nested checks and preserves distinct relative paths', (t) => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-browser-inventory-'));
  t.after(() => fs.rmSync(root, { recursive: true, force: true }));
  fs.mkdirSync(path.join(root, 'e2e', 'fax'), { recursive: true });
  for (const file of ['document-playwright-checks.js', 'e2e/fax/document-playwright-checks.js',
    'e2e/fax/demographic-master-crud-smoke.js', 'e2e/fax/helper.js', 'manifest.test.js']) {
    fs.writeFileSync(path.join(root, file), '');
  }
  assert.deepEqual(suiteScripts(root), [
    'scripts/document-playwright-checks.js',
    'scripts/e2e/fax/demographic-master-crud-smoke.js',
    'scripts/e2e/fax/document-playwright-checks.js',
  ]);
});

/*
 * The point of the manifest is that it cannot fall behind the suite. Before it
 * existed, the run list was a bash for-loop in docs/ui-tests/deb-install-validation.md
 * and a new check was only in the suite if somebody remembered to mention it in
 * the prose; two of them (application-health, demographic-add among others) also
 * never got an npm alias. Both directions are asserted here.
 */
test('every browser check in scripts/ has a manifest entry', () => {
  const named = new Set(checks.map((check) => check.script));
  const missing = suiteScripts().filter((script) => !named.has(script));
  assert.deepEqual(missing, [], `these checks have no scripts/playwright-suite.json entry: ${missing.join(', ')}`);
});

test('every manifest entry names a script that exists', () => {
  for (const check of checks) {
    assert.ok(
      fs.existsSync(path.join(__dirname, '..', check.script)),
      `${check.name} points at ${check.script}, which does not exist`,
    );
  }
});

test('manifest entries are well formed and uniquely named', () => {
  const seen = new Set();
  for (const check of checks) {
    assert.ok(check.name && !seen.has(check.name), `duplicate or missing name: ${check.name}`);
    seen.add(check.name);
    assert.ok(Array.isArray(check.tiers) && check.tiers.length > 0, `${check.name} needs at least one tier`);
    for (const tier of check.tiers) {
      assert.ok(VALID_TIERS.has(tier), `${check.name} has unknown tier ${tier}`);
    }
    assert.equal(typeof check.assertsDatabase, 'boolean', `${check.name} must declare assertsDatabase`);
    assert.ok(Array.isArray(check.provinces) && check.provinces.length, `${check.name} needs provinces`);
    assert.ok(Number.isInteger(check.timeoutSec) && check.timeoutSec > 0, `${check.name} needs a positive timeoutSec`);
  }
});

test('a manual check runs only when named, never by tier or by default', () => {
  const manual = checks.filter((check) => check.manual);
  assert.ok(manual.length > 0, 'the packaged-install login check is manual');
  for (const check of manual) {
    assert.equal(check.manual, true, `${check.name}: manual must be the literal true`);
    assert.ok(!check.runLast, `${check.name}: a manual check is not scheduled, so runLast means nothing`);
    const everything = selectChecks(checks, parseArguments([]));
    assert.ok(!everything.some((c) => c.name === check.name), `${check.name} leaked into the default run`);
    for (const tier of check.tiers) {
      const tiered = selectChecks(checks, parseArguments(['--tier', tier]));
      assert.ok(!tiered.some((c) => c.name === check.name), `${check.name} leaked into --tier ${tier}`);
    }
    const named = selectChecks(checks, parseArguments(['--only', check.name]));
    assert.deepEqual(named.map((c) => c.name), [check.name]);
    const listed = selectChecks(checks, parseArguments(['--list']));
    assert.ok(listed.some((c) => c.name === check.name), `${check.name} must stay discoverable in --list`);
  }
});

test('standalone checks never require a database', () => {
  for (const check of checks.filter((entry) => entry.tiers.includes('standalone'))) {
    assert.equal(check.assertsDatabase, false,
      `${check.name} requires a database but standalone promises no deployment or database`);
  }
});

test('the smoke tier stays small enough to gate a pull request', () => {
  const smoke = checks.filter((check) => check.tiers.includes('smoke'));
  assert.ok(smoke.length > 0, 'there must be a smoke tier for CI to run');
  const budgetSeconds = smoke.reduce((total, check) => total + check.timeoutSec, 0);
  assert.ok(budgetSeconds <= 3600, `the smoke tier's worst case is ${budgetSeconds}s; it is meant to gate a PR`);
});

test('exactly one check runs last, and it is the one with the destructive probes', () => {
  const last = checks.filter((check) => check.runLast);
  assert.equal(last.length, 1, 'more than one runLast check means their order is undefined');
  assert.equal(last[0].name, 'login', 'login submits deliberate bad passwords and can lock the shared test account');
});

test('selectChecks orders runLast after everything else, whatever the manifest order', () => {
  const ordered = selectChecks(checks, parseArguments(['--tier', 'smoke']));
  assert.ok(ordered.length > 1);
  assert.equal(ordered[ordered.length - 1].name, 'login');
  assert.equal(ordered.filter((check) => check.runLast).length, 1);
});

test('selection by tier, name and province narrows the run', () => {
  const onlyOne = selectChecks(checks, parseArguments(['--only', 'tickler-crud']));
  assert.deepEqual(onlyOne.map((check) => check.name), ['tickler-crud']);

  const skipped = selectChecks(checks, parseArguments(['--tier', 'smoke', '--skip', 'login']));
  assert.equal(skipped.filter((check) => check.name === 'login').length, 0);

  // A BC run must not pick up the Ontario-only billing checks.
  const bc = selectChecks(checks, parseArguments(['--province', 'BC']));
  assert.equal(bc.filter((check) => check.provinces.includes('ON') && !check.provinces.includes('all')).length, 0);
  const on = selectChecks(checks, parseArguments(['--province', 'ON']));
  assert.ok(on.length > bc.length, 'Ontario-only checks should be selected for an ON run');
});

test('parseArguments rejects an unknown flag rather than silently running everything', () => {
  assert.throws(() => parseArguments(['--tyer', 'smoke']), /Unknown argument/);
  assert.throws(() => parseArguments(['--tier']), /needs a value/);
});

test('the JUnit report distinguishes a skip from a failure', () => {
  const xml = toJUnit([
    { name: 'a', outcome: 'PASS', detail: '', durationMs: 1200 },
    { name: 'b', outcome: 'FAIL', detail: 'the row was not written', durationMs: 2400 },
    { name: 'c', outcome: 'SKIP', detail: 'no corpus', durationMs: 10 },
  ]);
  assert.match(xml, /tests="3" failures="1" skipped="1"/);
  assert.match(xml, /<failure message="the row was not written"\/>/);
  assert.match(xml, /<skipped message="no corpus"\/>/);
});

test('the JUnit report escapes a detail that contains markup', () => {
  const xml = toJUnit([{ name: 'x', outcome: 'FAIL', detail: 'expected <b>1</b> & got "0"', durationMs: 0 }]);
  assert.match(xml, /&lt;b&gt;1&lt;\/b&gt; &amp; got &quot;0&quot;/);
  assert.doesNotMatch(xml, /message="expected <b>/);
});

/*
 * The navigation map is the "enter through the opener, not the address" rule made
 * mechanical: 31 of the 75 checks navigate straight to a page the UI opens as a
 * popup. An entry that carried a URL would quietly reintroduce exactly that.
 */
test('every navigation entry is a click, never a URL', () => {
  for (const [section, entry] of Object.entries(NAVIGATION)) {
    assert.equal(typeof entry.opens, 'string', `${section} must say what it opens`);
    assert.ok('validated' in entry, `${section} must declare whether a live run has confirmed its selector`);
    // `!== null` let an entry that OMITS click through untested, and undefined
    // with it -- so the one assertion standing between this suite and a map of
    // URLs could be skipped by leaving a key out. Only a literal null means
    // "no click needed", and it has to be written.
    assert.ok('click' in entry, `${section} must declare a click target, or null if none is needed`);
    if (entry.click !== null) {
      assert.equal(typeof entry.click, 'string', `${section}.click must be a selector string or null`);
      assert.doesNotMatch(entry.click, /^https?:|^\//, `${section} names a URL (${entry.click}); it must name the element the user clicks`);
    }
  }
});

test('the navigation map covers every section the Priority 1 checks reach', () => {
  for (const section of REQUIRED_SECTIONS) {
    assert.ok(NAVIGATION[section], `the coverage plan's Priority 1 checks need to reach ${section} by clicking`);
  }
});

test('package.json exposes every check, so none is reachable only by full path', () => {
  const manifest = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'package.json'), 'utf8'));
  const scriptBodies = Object.values(manifest.scripts).join('\n');
  const unexposed = checks
    .map((check) => check.script)
    .filter((script) => !scriptBodies.includes(path.basename(script)));
  assert.deepEqual(unexposed, [], `no npm alias runs: ${unexposed.join(', ')}`);
  assert.ok(manifest.scripts['test:playwright'], 'there must be one alias that runs the suite through the runner');
});

test('a check is spawned by absolute path, so the runner works from any directory', () => {
  // Manifest paths are repo-relative. Spawning them relative to process.cwd()
  // made every check "fail to start" when the runner was invoked from anywhere
  // but the repo root -- a failure with nothing to do with the check.
  const runner = fs.readFileSync(path.join(__dirname, 'run-playwright-suite.js'), 'utf8');
  assert.match(runner, /path\.resolve\(__dirname, '\.\.', check\.script\)/);
  assert.ok(!/run\(process\.execPath, \[check\.script\]/.test(runner),
    'the raw manifest path must not be handed to the spawn');
});

// Check the operational budget directly. Hand-maintained prose counts make
// independent test additions fail only after merging, despite retaining every
// manifest entry. Completeness and script/alias parity are checked above.
test('the smoke tier stays within its configured timeout budget', () => {
  const smoke = checks.filter((check) => check.tiers.includes('smoke'));
  const budgetSeconds = smoke.reduce((total, check) => total + check.timeoutSec, 0);
  assert.ok(budgetSeconds <= 3600,
    `smoke timeout budget ${budgetSeconds}s exceeds the 3600s ceiling`);
});

test('a partially valid selection cannot silently omit an unknown check', () => {
  for (const flag of ['--only', '--skip']) {
    assert.throws(() => selectChecks(checks, parseArguments([
      '--only', 'tickler-crud', flag, 'surface-scratch',
    ])), /Unknown check name: surface-scratch/);
  }
});


test('unknown selection exits before starting any browser check', () => {
  const { main } = require('./run-playwright-suite');
  const messages = [];
  const output = { log: line => messages.push(line), error: line => messages.push(line) };
  assert.equal(main(['--only', 'tickler-crud', '--only', 'surface-scratch'], {}, output), 1);
  assert.match(messages.join(' '), /Unknown check name/);
  assert.doesNotMatch(messages.join(' '), /--- tickler-crud/);
});

// Queue ownership and Poppler are opt-in deployment prerequisites.
test('core runs exclude incoming PDF fixtures while explicit selection retains them', () => {
  const core = selectChecks(checks, parseArguments(['--tier', 'core']));
  assert.ok(!core.some((check) => check.name === 'incoming-pdf-extraction'));
  const extended = selectChecks(checks, parseArguments(['--tier', 'extended']));
  assert.ok(extended.some((check) => check.name === 'incoming-pdf-extraction'));
  const explicit = selectChecks(checks, parseArguments(['--only', 'incoming-pdf-extraction']));
  assert.deepEqual(explicit.map((check) => check.name), ['incoming-pdf-extraction']);
});

// Parsing does not open browsers or require a deployment. Catch duplicate imports
// and other syntax errors even when a live workflow is not scheduled in CI.
test('every registered browser check parses before it can be scheduled', () => {
  const { execFileSync } = require('node:child_process');
  for (const check of checks) {
    execFileSync(process.execPath, ['--check', path.join(__dirname, '..', check.script)],
      { stdio: 'pipe', timeout: 10000 });
  }
});

/*
 * KNOWN-FAILURE BOOKKEEPING.
 *
 * A check asserts correct behaviour, so while a defect stands it fails at the step that
 * exercises the defect. That used to be recorded as prose in `notes` ("Fails on 2026.08: ..."),
 * and 94 failures of the alpha19 run were triaged by hand. `expectedFailure: { finding, step }`
 * turns it into data the runner can compare with what actually happened:
 *
 *   known-fail        failed at exactly that step: reported, does not fail the run
 *   unexpected-pass   passed although a failure was expected: reported, does not fail the run
 *   failed-elsewhere  failed anywhere else (another step, no labelled step, cleanup, timeout):
 *                     FAILS the run, because a known defect must not hide a new one
 */
const {
  browserVersionsOf, classifyResult, cleanBrowserVersion, exitCodeFor, runOne, main, summarise, validateExpectedFailure,
} = require('./run-playwright-suite');
const { EXIT_FAIL, EXIT_PASS } = require('./lib/playwright-harness');

// What a conforming script contains: it reports through the harness, so its failing step is recorded.
const REPORTING = 'if (require.main === module) runWorkflow(\'two-windows\', workflow);\n';
const EXPECTED = Object.freeze({ finding: 140, step: 'send from X\'s window' });
const expectingCheck = (overrides = {}) => ({
  name: 'two-windows', script: 'scripts/two-windows-playwright-checks.js', tiers: ['core'],
  assertsDatabase: false, provinces: ['all'], timeoutSec: 5, expectedFailure: { ...EXPECTED }, ...overrides,
});

/** A spawn double: the "child" writes the RESULT_JSON record runCheck would, then exits with `status`. */
function child({ status = 1, record = null, seen = null } = {}) {
  return (command, args, spawnOptions) => {
    if (seen) seen.push(spawnOptions.env.RESULT_JSON);
    if (record) fs.writeFileSync(spawnOptions.env.RESULT_JSON, JSON.stringify(record));
    return { status };
  };
}
const failureAt = (failedStep, extra = {}) => ({ name: 'two-windows', outcome: 'FAIL', detail: 'assertion failed', failedStep, ...extra });

test('shouldClassifyKnownFail_whenFailedStepMatches', () => {
  const result = runOne(expectingCheck(), { env: {} }, child({ record: failureAt('send from X\'s window') }));
  assert.equal(result.outcome, 'known-fail');
  assert.match(result.detail, /finding 140/);
  assert.match(result.detail, /send from X's window/);
  assert.equal(exitCodeFor([result]), EXIT_PASS, 'a known failure must not fail the run');
});

test('shouldFailRun_whenFailedElsewhere', () => {
  const elsewhere = runOne(expectingCheck(), { env: {} }, child({ record: failureAt('seed the patients') }));
  assert.equal(elsewhere.outcome, 'failed-elsewhere');
  assert.match(elsewhere.detail, /expected to fail at step "send from X's window" \(finding 140\)/);
  assert.match(elsewhere.detail, /seed the patients/, 'the step it actually failed at must be named');
  assert.equal(exitCodeFor([elsewhere]), EXIT_FAIL, 'a known defect must not hide a failure at another step');
});

test('shouldFailRun_whenFailureHasNoLabelledStep', () => {
  // The child crashed before writing a record, or failed outside any step (final strict-page check, cleanup).
  for (const [label, run] of [
    ['no record', child({ status: 1 })],
    ['a record without a step', child({ record: { name: 'two-windows', outcome: 'FAIL', detail: 'cleanup failed' } })],
  ]) {
    const result = runOne(expectingCheck(), { env: {} }, run);
    assert.equal(result.outcome, 'failed-elsewhere', label);
    assert.equal(exitCodeFor([result]), EXIT_FAIL, label);
  }
});

test('shouldFailRun_whenKnownFailureAlsoLeaksItsFixtures', () => {
  const result = runOne(expectingCheck(), { env: {} },
    child({ record: failureAt('send from X\'s window', { cleanupFailed: true }) }));
  assert.equal(result.outcome, 'failed-elsewhere');
  assert.match(result.detail, /cleanup/);
});

test('shouldFailRun_whenExpectedFailureTimesOutOrIsInterrupted', () => {
  const timedOut = runOne(expectingCheck(), { env: {} }, () => ({ status: null, error: Object.assign(new Error('x'), { code: 'ETIMEDOUT' }) }));
  assert.equal(timedOut.outcome, 'failed-elsewhere');
  assert.match(timedOut.detail, /timed out after 5s/);
  // Exit 130/143 is a signal, not a failure at a step, even if a record named the step.
  const interrupted = runOne(expectingCheck(), { env: {} },
    child({ status: 130, record: failureAt('send from X\'s window') }));
  assert.equal(interrupted.outcome, 'failed-elsewhere');
});

test('shouldReportUnexpectedPass_whenExpectedFailurePasses', () => {
  const result = runOne(expectingCheck(), { env: {} }, child({ status: 0, record: { name: 'two-windows', outcome: 'PASS', detail: '' } }));
  assert.equal(result.outcome, 'unexpected-pass');
  assert.match(result.detail, /finding 140/);
  assert.match(result.detail, /remove expectedFailure/);
  assert.equal(exitCodeFor([result]), EXIT_PASS, 'an unexpected pass is reported, not punished');
});

test('shouldKeepSkip_whenExpectedFailureCheckSkips', () => {
  const result = runOne(expectingCheck(), { env: {} }, child({ status: 2 }));
  assert.equal(result.outcome, 'SKIP');
  assert.equal(exitCodeFor([result]), EXIT_PASS);
});

test('shouldLeaveOrdinaryOutcomesUnchanged_whenNoFailureIsExpected', () => {
  const plain = expectingCheck({ expectedFailure: undefined });
  assert.equal(runOne(plain, { env: {} }, child({ status: 0 })).outcome, 'PASS');
  assert.equal(runOne(plain, { env: {} }, child({ status: 2 })).outcome, 'SKIP');
  const failed = runOne(plain, { env: {} }, child({ status: 1, record: failureAt('send from X\'s window') }));
  assert.equal(failed.outcome, 'FAIL');
  assert.match(failed.detail, /exit 1/);
  assert.match(failed.detail, /send from X's window/, 'a plain failure names its step too, so triage can start there');
  assert.equal(exitCodeFor([failed]), EXIT_FAIL);
  assert.equal(runOne(plain, { env: {} }, child({ status: 1 })).detail, 'exit 1');
});

test('shouldGiveEveryCheckItsOwnResultFile_andRemoveIt', () => {
  const seen = [];
  runOne(expectingCheck({ name: 'a' }), { env: { RESULT_JSON: '/tmp/caller-exported.json' } }, child({ seen }));
  runOne(expectingCheck({ name: 'b' }), { env: {} }, child({ seen }));
  assert.equal(seen.length, 2);
  assert.notEqual(seen[0], seen[1], 'a shared result file would let one check inherit the previous check\'s failed step');
  assert.notEqual(seen[0], '/tmp/caller-exported.json', 'the runner owns RESULT_JSON for the children it starts');
  for (const file of seen) assert.equal(fs.existsSync(file), false, 'the temporary record is removed after the run');
});

test('shouldIgnoreAStaleRecord_whenTheChildWritesNone', () => {
  // A record left by an earlier run must not be read as this run's: the file is created fresh per check.
  const result = runOne(expectingCheck(), { env: {} }, child({ status: 1 }));
  assert.equal(result.outcome, 'failed-elsewhere');
});

/**
 * The doubles above prove the classification; this proves the plumbing. A real child process
 * fails through the real runCheck() and markFailedStep(), and the real runner reads the
 * RESULT_JSON record it handed that child.
 */
function realChild(t, body) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-runner-child-'));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  const script = path.join(dir, 'child-playwright-checks.js');
  const harnessPath = JSON.stringify(path.join(__dirname, 'lib', 'playwright-harness'));
  fs.writeFileSync(script, `const h = require(${harnessPath});\nh.runCheck({ name: 'child', async run() { ${body} } });\n`);
  const piped = (command, args, spawnOptions) => spawnSync(command, args, { ...spawnOptions, stdio: 'pipe' });
  return { script, piped };
}

test('shouldClassifyKnownFail_whenARealChildFailsAtTheExpectedStep', (t) => {
  const { script, piped } = realChild(t, `
    try { throw new Error('boom'); } catch (error) { throw h.markFailedStep(error, 'second step'); }`);
  const check = expectingCheck({ script, timeoutSec: 30, expectedFailure: { finding: 140, step: 'second step' } });
  assert.equal(runOne(check, { env: process.env }, piped).outcome, 'known-fail');
  const elsewhere = expectingCheck({ script, timeoutSec: 30, expectedFailure: { finding: 140, step: 'first step' } });
  assert.equal(runOne(elsewhere, { env: process.env }, piped).outcome, 'failed-elsewhere');
});

test('shouldClassifyUnexpectedPass_whenARealChildPasses', (t) => {
  const { script, piped } = realChild(t, 'return null;');
  const result = runOne(expectingCheck({ script, timeoutSec: 30 }), { env: process.env }, piped);
  assert.equal(result.outcome, 'unexpected-pass');
});

test('shouldClassifyDirectly_whenGivenARawResult', () => {
  const check = expectingCheck();
  assert.deepEqual(classifyResult(expectingCheck({ expectedFailure: undefined }), { outcome: 'FAIL', detail: 'exit 1' }),
    { outcome: 'FAIL', detail: 'exit 1' });
  assert.equal(classifyResult(check, { outcome: 'FAIL', detail: 'exit 1', failedStep: 'send from X\'s window' }).outcome, 'known-fail');
  assert.equal(classifyResult(check, { outcome: 'PASS', detail: '' }).outcome, 'unexpected-pass');
  assert.equal(classifyResult(check, { outcome: 'SKIP', detail: 'no fixture' }).outcome, 'SKIP');
});

test('shouldFailRunOnlyOn_failAndFailedElsewhere', () => {
  const outcome = (name) => ({ name, outcome: name, detail: '', durationMs: 0 });
  assert.equal(exitCodeFor(['PASS', 'SKIP', 'known-fail', 'unexpected-pass'].map(outcome)), EXIT_PASS);
  assert.equal(exitCodeFor(['PASS', 'FAIL'].map(outcome)), EXIT_FAIL);
  assert.equal(exitCodeFor(['PASS', 'known-fail', 'failed-elsewhere'].map(outcome)), EXIT_FAIL);
});

function captured() {
  const lines = [];
  return { lines, out: { log: (line = '') => lines.push(line), error: (line) => lines.push(line) } };
}

test('shouldSurfaceAllThreeOutcomes_inTheConsoleSummary', () => {
  const { lines, out } = captured();
  summarise([
    { name: 'a', outcome: 'PASS', detail: '', durationMs: 1000 },
    { name: 'b', outcome: 'known-fail', detail: 'known failure at step "s" (finding 140)', durationMs: 1000 },
    { name: 'c', outcome: 'unexpected-pass', detail: 'passed although expected to fail', durationMs: 1000 },
    { name: 'd', outcome: 'failed-elsewhere', detail: 'expected to fail at step "s" (finding 140) but failed at step "t"', durationMs: 1000 },
  ], out);
  const text = lines.join('\n');
  assert.match(text, /known-fail\s+b/);
  assert.match(text, /unexpected-pass\s+c/);
  assert.match(text, /failed-elsewhere\s+d/);
  assert.match(text, /1 passed, 0 failed, 0 skipped, 1 known-fail, 1 unexpected-pass, 1 failed-elsewhere/);
});

test('shouldKeepTheOriginalSummaryLine_whenNoFailureIsExpected', () => {
  const { lines, out } = captured();
  summarise([{ name: 'a', outcome: 'PASS', detail: '', durationMs: 1000 }, { name: 'b', outcome: 'SKIP', detail: 'x', durationMs: 1 }], out);
  assert.ok(lines.includes('  1 passed, 0 failed, 1 skipped'), lines.join('\n'));
});

test('shouldWriteJUnit_forKnownFailUnexpectedPassAndFailedElsewhere', () => {
  const xml = toJUnit([
    { name: 'a', outcome: 'PASS', detail: '', durationMs: 1200 },
    { name: 'b', outcome: 'known-fail', detail: 'known failure at step "send" (finding 140)', durationMs: 10 },
    { name: 'c', outcome: 'unexpected-pass', detail: 'passed although expected to fail at step "x" (finding 7)', durationMs: 10 },
    { name: 'd', outcome: 'failed-elsewhere', detail: 'expected to fail at step "x" (finding 7) but failed at step "y"', durationMs: 10 },
    { name: 'e', outcome: 'FAIL', detail: 'exit 1', durationMs: 10 },
  ]);
  assert.match(xml, /tests="5" failures="2" skipped="1"/, 'failed-elsewhere is a failure; known-fail is a skip');
  assert.match(xml, /<skipped message="known-fail: known failure at step &quot;send&quot; \(finding 140\)"\/>/);
  assert.match(xml, /<failure message="failed-elsewhere: expected to fail at step &quot;x&quot; \(finding 7\) but failed at step &quot;y&quot;"\/>/);
  assert.match(xml, /<testcase [^>]*name="c"[^>]*>\s*<system-out>unexpected-pass: passed although expected to fail at step &quot;x&quot; \(finding 7\)<\/system-out>\s*<\/testcase>/);
  assert.match(xml, /<failure message="exit 1"\/>/);
});

function mainWith(checks, run, argv = [], env = {}) {
  const { lines, out } = captured();
  const code = main(argv, env, out, { checks, run, readBuildIdentity: () => null });
  return { code, text: lines.join('\n') };
}

test('shouldExitNonZeroFromMain_whenAnExpectedFailureFailsElsewhere', () => {
  const { code, text } = mainWith([expectingCheck()], child({ record: failureAt('seed the patients') }));
  assert.equal(code, EXIT_FAIL);
  assert.match(text, /failed-elsewhere\s+two-windows/);
});

test('shouldExitZeroFromMain_whenOnlyKnownFailuresAndUnexpectedPassesOccur', () => {
  const checks = [expectingCheck({ name: 'known' }), expectingCheck({ name: 'fixed' })];
  const run = (command, args, spawnOptions) => {
    const name = args[0].includes('known') ? 'known' : 'fixed';
    return child(name === 'known' ? { record: failureAt('send from X\'s window') } : { status: 0 })(command, args, spawnOptions);
  };
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-runner-junit-'));
  try {
    const junit = path.join(dir, 'out.xml');
    const checksWithScripts = checks.map((check) => ({ ...check, script: `scripts/${check.name}-playwright-checks.js` }));
    const { code, text } = mainWith(checksWithScripts, run, ['--junit', junit]);
    assert.equal(code, EXIT_PASS);
    assert.match(text, /known-fail\s+known/);
    assert.match(text, /unexpected-pass\s+fixed/);
    const xml = fs.readFileSync(junit, 'utf8');
    assert.match(xml, /known-fail: known failure at step/);
    assert.match(xml, /unexpected-pass: passed although/);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

/*
 * The browser version. A failure that comes from the browser (Chromium 154 refuses a beforeunload prompt
 * from a handler that removes itself, and names a non-ASCII download "download" under the POSIX locale)
 * reads exactly like an application defect, so a result is recorded next to the browser that produced it:
 * on the check's result, once under the console summary, and in the JUnit <properties>.
 */
const BROWSER = 'chromium 154.0.8025.0';
const passingCheck = (overrides = {}) => expectingCheck({ expectedFailure: undefined, ...overrides });
const passRecord = (extra = {}) => ({ name: 'two-windows', outcome: 'PASS', detail: '', durationMs: 1, ...extra });

test('shouldCarryBrowserVersion_whenTheChildRecordsOne', () => {
  const passed = runOne(passingCheck(), { env: {} }, child({ status: 0, record: passRecord({ browserVersion: BROWSER }) }));
  assert.equal(passed.outcome, 'PASS');
  assert.equal(passed.browserVersion, BROWSER);
  // A browser-caused failure is when the version is wanted most, and it must not disturb the classification.
  const known = runOne(expectingCheck(), { env: {} }, child({ record: failureAt(EXPECTED.step, { browserVersion: BROWSER }) }));
  assert.equal(known.outcome, 'known-fail');
  assert.match(known.detail, /finding 140/);
  assert.equal(known.browserVersion, BROWSER);
  const elsewhere = runOne(expectingCheck(), { env: {} }, child({ record: failureAt('another step', { browserVersion: BROWSER }) }));
  assert.equal(elsewhere.outcome, 'failed-elsewhere');
  assert.equal(elsewhere.browserVersion, BROWSER);
  assert.equal(exitCodeFor([elsewhere]), EXIT_FAIL);
});

test('shouldLeaveTheResultUnchanged_whenTheChildRecordsNoBrowser', () => {
  const passed = runOne(passingCheck(), { env: {} }, child({ status: 0 }));
  assert.deepEqual(Object.keys(passed).sort(), ['detail', 'durationMs', 'name', 'outcome']);
  const failed = runOne(passingCheck(), { env: {} }, child({ record: failureAt('a step') }));
  assert.equal('browserVersion' in failed, false);
  const timedOut = runOne(passingCheck(), { env: {} }, () => ({ status: null, error: { code: 'ETIMEDOUT' } }));
  assert.equal(timedOut.detail, 'timed out after 5s');
  assert.equal('browserVersion' in timedOut, false);
});

test('shouldDropABrowserVersion_thatIsNotPlainVersionText', () => {
  // The record is a file the child wrote: its value is printed and written into JUnit, so it is data.
  for (const bad of ['', '   ', 'x"><script>', 'a\nb', 'x'.repeat(81), 154, null, {}, ['chromium 1'], '-1', ' chromium 154']) {
    assert.equal(cleanBrowserVersion(bad), undefined, `${JSON.stringify(bad)} must not reach the report`);
  }
  assert.equal(cleanBrowserVersion(BROWSER), BROWSER);
  assert.equal(cleanBrowserVersion('chromium 154.0.8025.0-rc1+build.7'), 'chromium 154.0.8025.0-rc1+build.7');
  const hostile = runOne(passingCheck(), { env: {} }, child({ status: 0, record: passRecord({ browserVersion: '"/><script>' }) }));
  assert.equal('browserVersion' in hostile, false);
});

test('shouldListEachBrowserVersionOnce_inOrderFirstSeen', () => {
  const rows = [BROWSER, undefined, BROWSER, 'chromium 153.0.1'].map((browserVersion, index) => ({ name: `c${index}`, browserVersion }));
  assert.deepEqual(browserVersionsOf(rows), [BROWSER, 'chromium 153.0.1']);
  assert.deepEqual(browserVersionsOf([{ name: 'a' }]), []);
});

test('shouldPrintTheBrowserOnce_underTheSummary', () => {
  const row = (name, extra = {}) => ({ name, outcome: 'PASS', detail: '', durationMs: 1000, ...extra });
  const { lines, out } = captured();
  summarise([row('a', { browserVersion: BROWSER }), row('b', { browserVersion: BROWSER }), row('c')], out);
  assert.equal(lines.filter((line) => /browser:/.test(line)).length, 1, 'once per run, not once per check');
  assert.ok(lines.includes(`  browser: ${BROWSER}`), lines.join('\n'));
  assert.equal(lines.at(-1), `  browser: ${BROWSER}`, 'under the counts line, where a reader looks for the verdict');
  const mixed = captured();
  summarise([row('a', { browserVersion: BROWSER }), row('b', { browserVersion: 'chromium 153.0.1' })], mixed.out);
  assert.ok(mixed.lines.includes(`  browser: ${BROWSER}, chromium 153.0.1`), 'a second version is visible');
  const none = captured();
  summarise([row('a')], none.out);
  assert.equal(none.lines.some((line) => /browser/.test(line)), false, 'no line when no check reported a browser');
});

test('shouldWriteBrowserVersionIntoJUnitProperties', () => {
  const row = (name, extra = {}) => ({ name, outcome: 'PASS', detail: '', durationMs: 1200, ...extra });
  const xml = toJUnit([row('a', { browserVersion: BROWSER }), row('b', { browserVersion: BROWSER }), row('c')]);
  assert.match(xml, new RegExp(`<testsuite [^>]*>\\s*<properties>\\s*<property name="browserVersion" value="${BROWSER}"/>\\s*</properties>\\s*<testcase`),
    'properties is the first child of the testsuite, before the testcases');
  assert.equal((xml.match(/name="browserVersion"/g) || []).length, 1, 'one property for the suite');
  assert.match(xml, /tests="3" failures="0" skipped="0"/);
  // Nothing changes for a run that launched no browser.
  const bare = toJUnit([row('a')]);
  assert.doesNotMatch(bare, /<properties/);
  assert.match(bare, /<testsuite name="carlos-playwright-suite" tests="1" failures="0" skipped="0">\n    <testcase /);
  // The value is escaped even though cleanBrowserVersion already restricts it.
  assert.match(toJUnit([row('a', { browserVersion: 'x"y<z' })]), /value="x&quot;y&lt;z"/);
});

test('shouldReportBrowserVersionFromARealChild_throughMainIntoJUnit', (t) => {
  const { script, piped } = realChild(t, `
    h.recordBrowserVersion({ version: () => '154.0.8025.0', browserType: () => ({ name: () => 'chromium' }) });
    return null;`);
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-runner-browser-'));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  const junit = path.join(dir, 'out.xml');
  const absolute = { ...passingCheck({ script, timeoutSec: 30 }) };
  const { code, text } = mainWith([absolute], piped, ['--junit', junit], process.env);
  assert.equal(code, EXIT_PASS);
  assert.match(text, /\n {2}browser: chromium 154\.0\.8025\.0/);
  assert.match(fs.readFileSync(junit, 'utf8'), /<property name="browserVersion" value="chromium 154\.0\.8025\.0"\/>/);
  // A child that never launched a browser reports none.
  const quiet = realChild(t, 'return null;');
  const result = runOne(passingCheck({ script: quiet.script, timeoutSec: 30 }), { env: process.env }, quiet.piped);
  assert.equal(result.outcome, 'PASS');
  assert.equal('browserVersion' in result, false);
});

/*
 * Three checks failed on alpha19 for reasons that belong to the browser and the front door, not the
 * application. The fixes remove the artefact; these guards keep a later "simplification" from putting
 * it back (each artefact was reproduced on the packaged Chromium 154).
 */
function scriptText(name) {
  return fs.readFileSync(path.join(__dirname, name), 'utf8').replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '');
}

test('shouldNotRegisterABeforeunloadHandlerThatRemovesItself_inDoubleSubmitEform', () => {
  // Chromium suppresses the prompt of a handler that removes itself while it runs ({ once: true } does),
  // and blames a missing user gesture. The check disarms the handler from a timer instead.
  const source = scriptText('double-submit-eform-playwright-checks.js');
  assert.doesNotMatch(source, /addEventListener\('beforeunload',[\s\S]{0,160}?\},\s*\{\s*once:\s*true/,
    'a once:true beforeunload handler is blocked by Chromium 154');
  assert.equal((source.match(/addEventListener\('beforeunload'/g) || []).length, 1, 'one registration, in the helper');
  assert.match(source, /setTimeout\(\(\) => window\.removeEventListener\('beforeunload'/);
  assert.match(source, /armUnsavedChangesPrompt\(form\)/);
  assert.match(source, /await heading\.click\(\)/, 'the page is clicked before it is navigated away from');
});

test('shouldAssertTheHeaderFilename_notTheBrowsersDownloadName_inEformExportZip', () => {
  // download.suggestedFilename() is the browser's choice and follows its locale: Chromium 154 under the
  // POSIX locale names a download whose filename*= is not ASCII "download".
  const source = scriptText('export-content-eform-export-zip-playwright-checks.js');
  assert.doesNotMatch(source, /\bfile\.name\b|suggestedFilename|browserDownload/);
  assert.match(source, /decodedName === form\.download/);
  assert.match(source, /plainFilename\(header\) === form\.download/);
});

test('shouldClassifyTheFrontDoorsRefusal_withIsWafPage_inMessengerWriteToEncounter', () => {
  const source = scriptText('gap-provider-messenger-write-to-encounter-playwright-checks.js');
  assert.match(source, /h\.isWafPage\(status,/);
  // Only the out-of-range id may be answered by the WAF; the others must reach the application's 400.
  assert.match(source, /\{ id: '2147483648', wafMayRefuse: true \}/);
  assert.equal((source.match(/wafMayRefuse: true/g) || []).length, 1);
  assert.match(source, /written\(\) === rowsBefore/, 'a refusal is only shown with the rows unchanged');
});

/*
 * The manifest side: expectedFailure is only as good as the finding and the step it cites.
 */
const FINDINGS_LOG = fs.readFileSync(path.join(__dirname, '..', 'docs', 'ui-tests', 'app-findings-log.md'), 'utf8');

/** finding number -> status, read like app-findings-log.test.js reads the log. */
function findingStatuses() {
  const statuses = new Map();
  for (const line of FINDINGS_LOG.split('\n').filter((row) => /^\|\s*\d+\s*\|/.test(row))) {
    const cells = line.split('|').slice(1, -1).map((cell) => cell.trim());
    statuses.set(Number(cells[0]), cells[cells.length - 1].replace(/`/g, '').trim());
  }
  return statuses;
}

/**
 * What validateExpectedFailure reads for one script: `scriptSource`, the script's OWN text (the
 * reporting guard looks only there), and `source`, that text plus the repository modules it
 * requires, because step labels may live in a shared engine (xss-poison-admin-walk).
 *
 * The two shared modules every check requires, lib/playwright-harness.js and lib/workflow-session.js,
 * are left out of `source` unless asked for: they define markFailedStep() and runCheck()
 * themselves, so they would make every script look like it tags its steps.
 */
const SHARED_HARNESS_MODULES = new Set(['playwright-harness.js', 'workflow-session.js']);
function stepSources(script, { includeHarness = false } = {}) {
  const file = path.join(__dirname, '..', script);
  const scriptSource = fs.readFileSync(file, 'utf8');
  const parts = [scriptSource];
  for (const match of scriptSource.matchAll(/require\('(\.[^']+)'\)/g)) {
    for (const candidate of [match[1], `${match[1]}.js`]) {
      const resolved = path.resolve(path.dirname(file), candidate);
      if (!fs.existsSync(resolved) || !fs.statSync(resolved).isFile()) continue;
      if (!includeHarness && path.basename(path.dirname(resolved)) === 'lib'
        && SHARED_HARNESS_MODULES.has(path.basename(resolved))) continue;
      parts.push(fs.readFileSync(resolved, 'utf8'));
    }
  }
  return { source: parts.join('\n'), scriptSource };
}

test('every expectedFailure cites an open finding and a step label its script contains', () => {
  const statuses = findingStatuses();
  const withExpectation = checks.filter((check) => check.expectedFailure !== undefined);
  assert.ok(withExpectation.length > 0, 'the prose "Fails on ..." notes that name a finding and a step are converted');
  for (const check of withExpectation) {
    const problems = validateExpectedFailure(check, { statuses, ...stepSources(check.script) });
    assert.deepEqual(problems, [], `${check.name}: ${problems.join('; ')}`);
  }
});

// The synthetic scripts below are one text: it is both what the script says and what it requires.
const validate = (check, { statuses, source }) => validateExpectedFailure(check, { statuses, source, scriptSource: source });

test('shouldRejectExpectedFailure_whenFindingIsFixedOrMissing', () => {
  const statuses = new Map([[140, 'issue-filed'], [141, 'fixed'], [142, 'open'], [143, 'needs-live-check']]);
  const source = REPORTING + 'await s.step(\'send from X\\\'s window\', async () => {});';
  const ok = (finding) => validate(expectingCheck({ expectedFailure: { finding, step: 'send from X\'s window' } }), { statuses, source });
  assert.deepEqual(ok(140), []);
  assert.deepEqual(ok(142), []);
  assert.deepEqual(ok(143), []);
  assert.match(ok(141).join(';'), /finding 141 is fixed/);
  assert.match(ok(9999).join(';'), /finding 9999 is not a row in docs\/ui-tests\/app-findings-log\.md/);
});

test('shouldRejectExpectedFailure_whenShapeIsWrong', () => {
  const statuses = new Map([[140, 'issue-filed']]);
  const source = REPORTING + 'await s.step(\'a step\', async () => {});';
  const problems = (expectedFailure) => validate(expectingCheck({ expectedFailure }), { statuses, source }).join(';');
  assert.match(problems(140), /must be an object/);
  assert.match(problems(null), /must be an object/);
  assert.match(problems({ finding: '140', step: 'a step' }), /finding must be a positive integer/);
  assert.match(problems({ finding: 0, step: 'a step' }), /finding must be a positive integer/);
  assert.match(problems({ finding: 140, step: '' }), /step must be a non-empty string/);
  assert.match(problems({ finding: 140 }), /step must be a non-empty string/);
  assert.match(problems({ finding: 140, step: 'a step', reason: 'x' }), /only finding and step/);
});

test('shouldRejectExpectedFailure_whenTheScriptHasNoSuchStepLabel', () => {
  // failedStep can only ever be a label the script passes to step(); anything else would read as failed-elsewhere forever.
  const statuses = new Map([[140, 'issue-filed']]);
  const check = expectingCheck({ expectedFailure: { finding: 140, step: 'a step that does not exist' } });
  const problems = validate(check, { statuses, source: REPORTING + 'await s.step(\'another step\', async () => {});' });
  assert.match(problems.join(';'), /no step labelled "a step that does not exist"/);
  // An escaped quote in the script's string literal is still the same label.
  const escaped = expectingCheck({ expectedFailure: { finding: 140, step: 'Send in X\'s window' } });
  assert.deepEqual(validate(escaped, { statuses, source: REPORTING + 'await s.step(\'Send in X\\\'s window\', f);' }), []);
  assert.deepEqual(validate(escaped, { statuses, source: REPORTING + 'await s.step("Send in X\'s window", f);' }), []);
});

test('shouldRejectExpectedFailure_whenTheScriptDoesNotReportThroughRunCheck', () => {
  // Only runCheck() writes the failing step where the runner can read it; a script with its own
  // main() and its own PASS/FAIL printing can never satisfy an expectedFailure.
  const statuses = new Map([[140, 'issue-filed']]);
  const source = 'const step = (name, ok) => console.log(ok ? "PASS" : "FAIL", name); step(\'send from X\\\'s window\', true);';
  const problems = validate(expectingCheck(), { statuses, source });
  assert.match(problems.join(';'), /neither runWorkflow\(\) nor a runCheck\(\) whose steps call markFailedStep\(\)/);
  // runCheck() alone is not enough: its own step helper must tag the failing step.
  const bare = source + ' runCheck({ name: \'x\', run });';
  assert.match(validate(expectingCheck(), { statuses, source: bare }).join(';'), /neither runWorkflow/);
  const tagged = `${bare} h.markFailedStep(error, 'send from X\\'s window');`;
  assert.deepEqual(validate(expectingCheck(), { statuses, source: tagged }), []);
});

test('shouldRejectExpectedFailure_whenARealRunCheckDirectScriptNeverTagsItsSteps', () => {
  // These scripts call runCheck() directly with a step helper of their own that does not tag the
  // error. The shared harness text they require DOES contain markFailedStep( and runCheck(, so
  // reading the combined text made the guard vacuous: an expectedFailure on one of them passed
  // the manifest test and then read failed-elsewhere on every run.
  const statuses = new Map([[140, 'issue-filed']]);
  for (const script of ['scripts/report-print-playwright-checks.js', 'scripts/admin-index-links-playwright-checks.js',
    'scripts/clinical-calculators-playwright-checks.js']) {
    const { scriptSource } = stepSources(script);
    assert.match(scriptSource, /\brunCheck\(/, `${script} is expected to be a runCheck-direct script`);
    assert.doesNotMatch(scriptSource, /\bmarkFailedStep\(|\brunWorkflow\(/, `${script} now tags its own steps; pick another script`);
    const label = /step\(\s*'([^']+)'/.exec(scriptSource);
    const check = { name: path.basename(script), script, expectedFailure: { finding: 140, step: label ? label[1] : 'a step' } };
    // The old combined text, harness included, is what used to satisfy the guard.
    const combined = stepSources(script, { includeHarness: true });
    assert.match(combined.source, /function markFailedStep\(/, 'the combined text must contain the harness for this regression test to mean anything');
    for (const sources of [stepSources(script), combined]) {
      const problems = validateExpectedFailure(check, { statuses, ...sources });
      assert.match(problems.join(';'), /neither runWorkflow\(\) nor a runCheck\(\) whose steps call markFailedStep\(\)/, script);
    }
  }
});

test('shouldAcceptExpectedFailure_whenARealWorkflowScriptTagsItsStepsThroughSessionStep', () => {
  const statuses = new Map([[140, 'issue-filed']]);
  const script = 'scripts/eform-email-two-windows-playwright-checks.js';
  const check = { name: 'eform-email-two-windows', script, expectedFailure: { finding: 140, step: 'Send in X\'s window delivers to X only, with X\'s own eForm' } };
  assert.deepEqual(validateExpectedFailure(check, { statuses, ...stepSources(script) }), []);
  assert.deepEqual(validateExpectedFailure(check, { statuses, ...stepSources(script, { includeHarness: true }) }), []);
});

test('shouldTakeTheReportingGuardFromTheScriptsOwnText_notFromWhatItRequires', () => {
  const statuses = new Map([[140, 'issue-filed']]);
  const check = expectingCheck();
  const label = 'await s.step(\'send from X\\\'s window\', f);';
  // Only a required module mentions runWorkflow and markFailedStep: the script itself does neither.
  const problems = validateExpectedFailure(check, {
    statuses, source: `${label}\nfunction markFailedStep() {} runWorkflow( runCheck(`, scriptSource: label,
  });
  assert.match(problems.join(';'), /neither runWorkflow/);
  assert.deepEqual(validateExpectedFailure(check, { statuses, source: label, scriptSource: `${label} runWorkflow('x', f);` }), []);
  // A caller that forgets scriptSource fails safe rather than reading the combined text.
  assert.match(validateExpectedFailure(check, { statuses, source: `${label} runWorkflow(` }).join(';'), /neither runWorkflow/);
});

/*
 * THE `mutates` FIELD.
 *
 * A check that changes clinic-wide state (a properties row, the fax account, a form registration,
 * a schedule) has to say what, so the next check that reads that state can be told what may have
 * moved under it, and so `run-playwright-suite.js --residue-audit` knows which property rows to
 * snapshot. `mutates` is a list of clinic-wide objects; scripts/lib/residue-audit.js parseMutates()
 * is its grammar: a table name, `property:<name>` (or its alias `UserProperty:<name>`), or
 * `file:<label>` for something on disk.
 */
const { parseMutates } = require('./lib/residue-audit');

const KNOWN_WRITERS = ['fax-configure', 'schedule-setting', 'eform-admin-crud', 'eform-image-delete', 'prevention-add-data'];

test('shouldDeclareMutates_onTheKnownClinicWideWriters', () => {
  for (const name of KNOWN_WRITERS) {
    const check = checks.find((entry) => entry.name === name);
    assert.ok(check, `${name} is no longer in the manifest`);
    assert.ok(Array.isArray(check.mutates) && check.mutates.length > 0,
      `${name} changes clinic-wide state and must list it in mutates`);
  }
  assert.deepEqual(checks.find((entry) => entry.name === 'fax-configure').mutates, ['fax_config']);
});

test('shouldKeepMutatesWellFormed_whereverItIsDeclared', () => {
  for (const check of checks.filter((entry) => entry.mutates !== undefined)) {
    assert.doesNotThrow(() => parseMutates(check.mutates), `${check.name}: mutates is not a valid list`);
    assert.equal(new Set(check.mutates).size, check.mutates.length, `${check.name}: mutates repeats an entry`);
  }
});

test('shouldRequireAnExclusiveRun_whenACheckMutatesMoreThanTheAuditLog', () => {
  // Global constraint: a check that changes clinic-wide state must not run beside another one. The
  // audit log is append-only and every login writes it, so it alone does not count.
  for (const check of checks.filter((entry) => Array.isArray(entry.mutates))) {
    if (check.mutates.every((entry) => entry === 'log')) continue;
    assert.match(check.fixtures || '', /must not run concurrently with other checks \(live wrapper: EXCLUSIVE=1\)/,
      `${check.name} mutates ${check.mutates.join(', ')}; its fixtures must say it needs an exclusive run`);
  }
});

test('shouldDeclareThatFaxConfigureReadsTheDatabase_sinceItNowRestoresTheRow', () => {
  const check = checks.find((entry) => entry.name === 'fax-configure');
  assert.equal(check.assertsDatabase, true, 'it snapshots and reads back fax_config');
  assert.match(check.fixtures, /fax_config/);
  assert.match(check.fixtures, /restores/);
});
