/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const { spawn } = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

/*
 * Issue #3600: a `finally` does not run when the process is killed, so browser
 * checks that create database rows must also clean up on SIGINT/SIGTERM.
 *
 * Two layers:
 *  1. A real child process that installs the shared handler, "creates a
 *     fixture", and is sent a real signal -- proving the handler, the exit code
 *     and the report-on-failure behaviour end to end, not against a fake process.
 *  2. A source audit that every check named in the issue still wires the helper
 *     and turns off Playwright's own signal handler (which would otherwise call
 *     process.exit before fixture cleanup finishes).
 */

const HARNESS = path.join(__dirname, 'lib', 'playwright-harness.js');

function runInterrupted(signal, cleanupBody) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'cleanup-signal-'));
  const marker = path.join(dir, 'fixture.txt');
  const child = `
    const fs = require('fs');
    const { installCleanupSignalHandlers } = require(${JSON.stringify(HARNESS)});
    fs.writeFileSync(process.env.MARKER, 'fixture');
    installCleanupSignalHandlers(async () => { ${cleanupBody} });
    console.log('READY');
    setInterval(() => {}, 1000);
  `;
  return new Promise((resolve, reject) => {
    const proc = spawn(process.execPath, ['-e', child], {
      env: { ...process.env, MARKER: marker },
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    let stderr = '';
    proc.stderr.on('data', (chunk) => { stderr += chunk; });
    proc.stdout.on('data', (chunk) => { if (String(chunk).includes('READY')) proc.kill(signal); });
    proc.on('error', reject);
    proc.on('close', (code) => {
      const fixtureRemoved = !fs.existsSync(marker);
      fs.rmSync(dir, { recursive: true, force: true });
      resolve({ code, stderr, fixtureRemoved });
    });
  });
}

for (const [signal, code] of [['SIGINT', 130], ['SIGTERM', 143]]) {
  test(`a real ${signal} removes the fixture and exits ${code} (issue #3600)`, async () => {
    const result = await runInterrupted(signal, "fs.rmSync(process.env.MARKER, { force: true });");
    assert.equal(result.fixtureRemoved, true, 'fixture must be gone: the handler ran cleanup before exiting');
    assert.equal(result.code, code);
  });
}

test('a failing cleanup is reported on stderr and the process still exits with the signal code', async () => {
  const result = await runInterrupted('SIGINT', "throw new Error('mysql unreachable');");
  assert.equal(result.code, 130);
  assert.equal(result.fixtureRemoved, false, 'the fixture is, by construction, still there');
  assert.match(result.stderr, /Cleanup after SIGINT failed: mysql unreachable/);
});

// The checks the issue names. consultation-nullable-fields and rx-fax-signature-stamp
// are deliberately absent: they use the cooperative graceful-signal-cancellation
// helper, which waits for an in-flight write to settle before cleaning up.
const ADOPTERS = [
  'add-login-account', 'demographic-add', 'document-upload', 'echart-new-patient-notes',
  'login', 'report-demographic-navigation', 'rx-preview-pharmacy', 'tickler-crud',
  'tickler-note-dialog', 'flowsheet-admin', 'flu-billing-report', 'rx-fax-reprint-represcribe',
];

for (const name of ADOPTERS) {
  test(`${name} installs the shared handler and leaves signals to it`, () => {
    const source = fs.readFileSync(path.join(__dirname, `${name}-playwright-checks.js`), 'utf8');
    assert.match(source, /installCleanupSignalHandlers\(/, 'must call the shared helper');
    assert.match(source, /\.\.\.NO_PLAYWRIGHT_SIGNAL_HANDLING/,
      "must stop Playwright's own SIGINT/SIGTERM handler, which exits before cleanup finishes");
    assert.doesNotMatch(source, /process\.(on|once)\(\s*signal|for \(const signal of \['SIGINT'/,
      'must not hand-roll a second handler');
  });
}
