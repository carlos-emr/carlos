/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const { browserErrorClass } = require('./browser-error-class');

test('browser diagnostics retain only a standard error class', () => {
  assert.equal(browserErrorClass(new TypeError('patient secret at /rx?demographicNo=42')), 'TypeError');
  assert.equal(browserErrorClass({ name: 'PatientSecretError', message: 'clinical text', stack: 'clinical text' }), 'Error');
  assert.equal(browserErrorClass(null), 'Error');
});

for (const name of ['record-binding', 'signature-stamp', 'reprint-represcribe']) {
  test(`fax ${name} diagnostics never emit raw exceptions or response excerpts`, () => {
    const source = fs.readFileSync(path.join(__dirname, `rx-fax-${name}-playwright-checks.js`), 'utf8');
    assert.doesNotMatch(source, /\b(?:error|e)\.(?:message|stack|stderr)\b/);
    assert.doesNotMatch(source, /console\.(?:error|log)\(\s*(?:error|e)\s*\)/);
    assert.doesNotMatch(source, /\$\{(?:faxBody|unsignedBody|body)(?:\.|\})/);
    // execFileSync otherwise forwards child stderr on failure before the catch
    // can replace the diagnostic, potentially echoing a clinical SQL statement.
    assert.match(source, /stdio: \['ignore', 'pipe', 'pipe'\]/);
    assert.doesNotMatch(source, /\{ encoding: 'utf8', timeout: 30000 \}/);
  });
}
