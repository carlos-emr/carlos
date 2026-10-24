/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Issue #4412: four browser checks failed on a freshly installed package for want
 * of a fixture the install never had. A missing fixture is a SKIP (exit 2, #3313)
 * that names what to stage; these pin that for the checks that can decide it
 * before a browser starts, and pin the fixture each one now relies on.
 */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const { loadManifest, runOne } = require('./run-playwright-suite');

const ROOT = path.join(__dirname, '..');

function runScript(script, env) {
  // A clean environment, so an exported MYSQL_* or fixture profile on the
  // developer's shell cannot change which branch the script takes.
  return spawnSync(process.execPath, [path.join(__dirname, script)], {
    env: {
      PATH: process.env.PATH, HOME: os.tmpdir(), ...(process.env.NODE_PATH ? { NODE_PATH: process.env.NODE_PATH } : {}), ...env,
    },
    encoding: 'utf8',
    timeout: 30000,
  });
}

test('o19-migrated-smoke SKIPs with the reason on a deployment that never ran import-o19', () => {
  const stateDir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-o19-absent-'));
  try {
    const result = runScript('o19-migrated-smoke-playwright-checks.js', { O19_STATE_DIR: stateDir });
    assert.equal(result.status, 2, result.stdout + result.stderr);
    assert.match(result.stdout, /^SKIP o19-migrated-smoke -- no break-glass credentials at .*admin-credentials\.txt: .*carlos-ctl import-o19/m);
    assert.equal(result.stderr, '');
    // Nothing was staged before deciding: the state dir is untouched.
    assert.deepEqual(fs.readdirSync(stateDir), []);
  } finally {
    fs.rmSync(stateDir, { recursive: true, force: true });
  }
});

test('o19-migrated-smoke still FAILS on a credentials file that exists but is incomplete', () => {
  const stateDir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-o19-broken-'));
  try {
    fs.writeFileSync(path.join(stateDir, 'admin-credentials.txt'), 'user: admin\n');
    const result = runScript('o19-migrated-smoke-playwright-checks.js', {
      O19_STATE_DIR: stateDir, MYSQL_HOST: 'localhost',
    });
    assert.equal(result.status, 1, result.stdout + result.stderr);
    assert.doesNotMatch(result.stdout, /^SKIP/m);
    assert.match(result.stderr, /did not carry a user, password and pin/);
  } finally {
    fs.rmSync(stateDir, { recursive: true, force: true });
  }
});

test('o19-migrated-smoke FAILS, not SKIPs, when the state path runs through a regular file (ENOTDIR)', () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-o19-notdir-'));
  try {
    const notADirectory = path.join(root, 'o19-import');
    fs.writeFileSync(notADirectory, 'a misconfigured O19_STATE_DIR\n');
    const result = runScript('o19-migrated-smoke-playwright-checks.js', { O19_STATE_DIR: notADirectory, MYSQL_HOST: 'localhost' });
    assert.equal(result.status, 1, result.stdout + result.stderr);
    assert.doesNotMatch(result.stdout, /^SKIP/m);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test('patient-list export SKIPs naming both fixture routes when it has neither', () => {
  const result = runScript('patient-list-by-appointment-export-playwright-checks.js', {});
  assert.equal(result.status, 2, result.stdout + result.stderr);
  assert.match(result.stdout, /^SKIP patient-list-by-appointment-export -- no fixture: .*PATIENT_LIST_FIXTURE_PROFILE=local-seed-obec-report-v1.*MYSQL_\*/m);
});

test('patient-list export FAILS on an unknown fixture profile rather than skipping it', () => {
  const result = runScript('patient-list-by-appointment-export-playwright-checks.js', { PATIENT_LIST_FIXTURE_PROFILE: 'typo' });
  assert.equal(result.status, 1);
  assert.doesNotMatch(result.stdout, /^SKIP/m);
  assert.match(result.stderr, /PATIENT_LIST_FIXTURE_PROFILE must be local-seed-obec-report-v1/);
});

test('the RTL print check defaults to a template every install is seeded with', () => {
  const script = fs.readFileSync(path.join(__dirname, 'eform-rtl-print-pdf-playwright-checks.js'), 'utf8');
  const seeded = /const SEEDED_TEMPLATE_NAME = '([^']+)';/.exec(script);
  assert.ok(seeded, 'the check names its seeded default template');
  const deployer = fs.readFileSync(path.join(ROOT,
    'src/main/java/io/github/carlos_emr/carlos/eform/EFormAssetDeployer.java'), 'utf8');
  const assets = /private static final String\[\] ASSETS = \{([^}]*)\}/.exec(deployer);
  assert.ok(assets, 'EFormAssetDeployer.ASSETS is where the seeded templates are listed');
  assert.ok(assets[1].includes(`"${seeded[1]}"`), `${seeded[1]} is no longer deployed by EFormAssetDeployer`);
  assert.notEqual(seeded[1], 'blank.rtl', 'blank.rtl was never sandboxed, so it cannot catch the regression');
  assert.ok(fs.existsSync(path.join(ROOT, 'src/main/webapp/WEB-INF/eform-assets', seeded[1])));
  // An operator-named template that is absent ends the check as SKIP, not as a timeout.
  assert.match(script, /process\.exit\(2\)/);
  assert.match(script, /templateIsFixture: Boolean\(process\.env\.RTL_TEMPLATE_NAME\)/);
});

test('the four #4412 checks carry a fixtures note, and the runner prints it on a SKIP', () => {
  const checks = loadManifest();
  for (const name of ['eform-rtl-print-pdf', 'rx-fax-record-binding', 'o19-migrated-smoke', 'patient-list-by-appointment-export']) {
    const check = checks.find((entry) => entry.name === name);
    assert.ok(check && typeof check.fixtures === 'string' && check.fixtures.length > 20, `${name} needs a fixtures note`);
  }
  const patientList = checks.find((entry) => entry.name === 'patient-list-by-appointment-export');
  assert.equal(patientList.assertsDatabase, true, 'it seeds appointments when given MYSQL_*');
  const o19 = checks.find((entry) => entry.name === 'o19-migrated-smoke');
  const skipped = runOne(o19, { env: {} }, () => ({ status: 2 }));
  assert.equal(skipped.outcome, 'SKIP');
  assert.ok(skipped.detail.endsWith(o19.fixtures), skipped.detail);
  const bare = runOne({ ...o19, fixtures: undefined }, { env: {} }, () => ({ status: 2 }));
  assert.equal(bare.detail, 'a fixture or credential this check needs is not configured');
});
