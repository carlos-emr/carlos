/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const { loadManifest, parseArguments, selectChecks, toJUnit } = require('./run-playwright-suite');
const { NAVIGATION, REQUIRED_SECTIONS } = require('./lib/playwright-ui');

const VALID_TIERS = new Set(['smoke', 'core', 'extended', 'front-door', 'standalone', 'live-external']);
const checks = loadManifest();

function suiteScripts() {
  return fs.readdirSync(path.join(__dirname))
    .filter((name) => name.endsWith('-playwright-checks.js') || name === 'demographic-master-crud-smoke.js')
    .sort();
}

/*
 * The point of the manifest is that it cannot fall behind the suite. Before it
 * existed, the run list was a bash for-loop in docs/ui-tests/deb-install-validation.md
 * and a new check was only in the suite if somebody remembered to mention it in
 * the prose; two of them (application-health, demographic-add among others) also
 * never got an npm alias. Both directions are asserted here.
 */
test('every browser check in scripts/ has a manifest entry', () => {
  const named = new Set(checks.map((check) => path.basename(check.script)));
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

/*
 * The coverage plan states the suite's size in prose, and prose does not
 * recompute itself. It shipped saying "92 named check entries over 83 scripts"
 * and "its thirteen checks" while the manifest held 97 over 88 and twelve smoke
 * entries -- numbers a reader uses to decide whether a gap is real. Pinning them
 * here means the next entry either updates the sentence or fails the build.
 */
test('the coverage plan states the manifest\'s real size', () => {
  const plan = fs.readFileSync(
    path.join(__dirname, '..', 'docs', 'ui-tests', 'playwright-coverage-plan-2026.08.md'),
    'utf8',
  );
  const scripts = new Set(checks.map((check) => check.script)).size;
  assert.ok(
    plan.includes(`The manifest: ${checks.length} named check entries over ${scripts} scripts`),
    `the plan must say "${checks.length} named check entries over ${scripts} scripts"`,
  );

  const words = ['zero', 'one', 'two', 'three', 'four', 'five', 'six', 'seven', 'eight', 'nine', 'ten',
    'eleven', 'twelve', 'thirteen', 'fourteen', 'fifteen', 'sixteen', 'seventeen', 'eighteen', 'nineteen', 'twenty'];
  const smoke = checks.filter((check) => check.tiers.includes('smoke'));
  assert.ok(smoke.length < words.length, 'extend the number words if the smoke tier grows past twenty');
  const budgetSeconds = smoke.reduce((total, check) => total + check.timeoutSec, 0);
  // The prose writes the budget with a thousands separator ("3,600s"), which is
  // the right way to write it; compare against a copy with the separators taken
  // out rather than forcing the document to read like a log line.
  const plainNumbers = plan.replace(/(\d),(?=\d{3}(?!\d))/g, '$1');
  assert.ok(
    plainNumbers.includes(`Its ${words[smoke.length]} checks come to ${budgetSeconds}s of`),
    `the plan must say the smoke tier's ${words[smoke.length]} checks come to ${budgetSeconds}s`,
  );
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
