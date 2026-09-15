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

test('signature findings and summary retain no browser content or patient identifiers', () => {
  const source = fs.readFileSync(path.join(__dirname, 'prescription-signature-playwright-checks.js'), 'utf8');
  const recorder = source.slice(source.indexOf('function wirePage('), source.indexOf('async function login('));
  assert.doesNotMatch(recorder, /(?:url|body|text|location):|error\.(?:stack|message)|dialog\.message\(/);
  assert.doesNotMatch(source, /visited\.push\([^\n]*url:|\$\{(?:result\.text|text|bodyText|storedPreview\.src)/);
  assert.doesNotMatch(source, /return \{\s*scriptId: prescriptionScriptId/);
  assert.match(source, /signatureMatched: true/);
  assert.doesNotMatch(source, /isExpectedPageError|Cannot set properties of null|expandPreview/);
  const handlers = {};
  const findings = [];
  const wireStart = source.indexOf('function wirePage(');
  const wireEnd = source.indexOf('async function assertNoErrorPage(', wireStart);
  const context = require('node:vm').createContext({ findings, browserErrorClass });
  require('node:vm').runInContext(source.slice(wireStart, wireEnd), context);
  context.wirePage({ on: (event, callback) => { handlers[event] = callback; } }, 'signature');
  handlers.pageerror(new TypeError("Cannot set properties of null (setting 'innerHTML')"));
  assert.equal(findings.length, 1);
  assert.equal(findings[0].errorClass, 'TypeError');
});

test('eDoc diagnostics omit raw errors and URLs and screenshots require explicit opt-in', () => {
  const source = fs.readFileSync(path.join(__dirname, 'edoc-schedule-navigation-playwright-checks.js'), 'utf8');
  assert.doesNotMatch(source, /\b(?:error|e)\.(?:message|stack|stderr)\b/);
  assert.doesNotMatch(source, /\$\{page\.url\(\)\}/);
  assert.match(source, /screenshotDir: process\.env\.EDOC_NAV_SCREENSHOT_DIR \|\| ''/);
  assert.match(source, /if \(!config\.screenshotDir\) return;/);
});
