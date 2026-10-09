/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const path = require('node:path');
const test = require('node:test');
const { spawnSync } = require('node:child_process');

/*
 * Five checks run one script under several manifest entries and pick the finding a run pins with an
 * environment variable (ALLERGY_PIN, MCEDT_PIN, OAUTH_PIN, ECHART_VERIFY_PIN, ECHART_ISSUES_PIN). A bad value is an error of the RUN, so it is
 * judged when the check runs and never when the module is required: oauth-rest-surfaces.test.js requires
 * the module only for its OAuth signer, and a stray OAUTH_PIN in whoever's shell runs the meta-tests must
 * not fail that. Each case runs in a child process because the variable is read once, at load.
 */
const CASES = [
  { script: 'allergy-add-penicillin-playwright-checks.js', variable: 'ALLERGY_PIN', label: 'Allergy', good: ['', 'shortcut-id'],
    run: 'main({ cancellation: { throwIfCancelled() {}, run: (body) => body() } })' },
  { script: 'mcedt-mailbox-outbox-playwright-checks.js', variable: 'MCEDT_PIN', label: 'Mcedt', good: ['', 'short-name', 'plaintext'],
    run: 'workflow({})' },
  { script: 'oauth-rest-surfaces-playwright-checks.js', variable: 'OAUTH_PIN', label: 'Oauth', good: ['', 'scope-list'],
    run: 'main({})' },
  { script: 'echart-note-verify-appointment-status-playwright-checks.js', variable: 'ECHART_VERIFY_PIN', label: 'EchartVerify', good: ['', 'archive'],
    run: 'workflow({})' },
  { script: 'echart-issues-filter-playwright-checks.js', variable: 'ECHART_ISSUES_PIN', label: 'EchartIssues',
    good: ['', 'editor', 'heading', 'resolve', 'panel'], run: 'workflow({})' },
];

function child(script, variable, value, body) {
  const env = { ...process.env, [variable]: value };
  return spawnSync(process.execPath, ['-e', `const m = require(${JSON.stringify(path.join(__dirname, script))}); ${body}`],
    { env, encoding: 'utf8', timeout: 30000 });
}

for (const { script, variable, label, good, run } of CASES) {
  test(`shouldLoadWithoutThrowing_when${label}PinIsInvalid`, () => {
    const result = child(script, variable, 'not-a-pin', "console.log('loaded', typeof m.validatePin);");
    assert.equal(result.status, 0, `${script} threw while being required: ${result.stderr}`);
    assert.match(result.stdout, /loaded function/);
  });

  test(`shouldRefuseToRun_when${label}PinIsInvalid`, () => {
    const result = child(script, variable, 'not-a-pin',
      `m.${run}.then(() => { console.log('RESOLVED'); }, (error) => { console.log('REJECTED ' + error.message); });`);
    assert.equal(result.status, 0, `${script}: ${result.stderr}`);
    assert.match(result.stdout, new RegExp(`REJECTED ${variable} must be unset`), `${script} did not refuse a bad ${variable} when it ran`);
  });

  test(`shouldAcceptEveryDocumentedValue_for${label}Pin`, () => {
    for (const value of good) {
      const result = child(script, variable, value, 'm.validatePin(); console.log("accepted");');
      assert.equal(result.status, 0, `${script} refused ${variable}=${JSON.stringify(value)}: ${result.stderr}`);
      assert.match(result.stdout, /accepted/);
    }
  });
}
