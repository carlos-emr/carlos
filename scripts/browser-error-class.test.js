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

test('fax workflow failure phases use fixed labels without browser or database content', () => {
  for (const name of ['signature-stamp', 'reprint-represcribe']) {
    const source = fs.readFileSync(path.join(__dirname, `rx-fax-${name}-playwright-checks.js`), 'utf8');
    const assignments = [...source.matchAll(/checkPhase\s*=\s*([^;]+);/g)];
    assert.ok(assignments.length > 5, `${name} needs enough phases to locate workflow failures`);
    for (const [, value] of assignments) {
      assert.match(value, /^(?:'[a-z-]+'|method === 'GET' \? '[a-z-]+' : '[a-z-]+')$/);
    }
    assert.ok(source.includes('${checkPhase}: ${browserErrorClass(error)}'));
  }
});

test('errorSourceLocation names the throwing script without its query string or session id', () => {
  const { errorSourceLocation } = require('./browser-error-class');
  const pageError = new TypeError('FAKE-Patient secret');
  pageError.stack = [
    "TypeError: Cannot read properties of null (reading 'value') for FAKE-Patient",
    '    at setComment (http://127.0.0.1:8080/carlos/oscarRx/ViewScript2.jsp;jsessionid=ABC123?scriptId=45&demographicNo=1:812:31)',
    '    at onload (http://127.0.0.1:8080/carlos/oscarRx/ViewScript2.jsp?demographicNo=1:1:1)',
  ].join('\n');
  const location = errorSourceLocation(pageError);
  assert.equal(location, ' at setComment (/carlos/oscarRx/ViewScript2.jsp:812:31)');
  assert.doesNotMatch(location, /demographicNo|scriptId|jsessionid|ABC123|FAKE|Cannot read/);
});

test('errorSourceLocation skips Node internals and node_modules to reach the check\'s own frame', () => {
  const { errorSourceLocation } = require('./browser-error-class');
  const timeout = { name: 'TimeoutError', stack: [
    'TimeoutError: locator.click: Timeout 30000ms exceeded.',
    '    at ProtocolError (/repo/node_modules/playwright-core/lib/client/connection.js:1:2)',
    '    at process.processTicksAndRejections (node:internal/process/task_queues:105:5)',
    '    at async runChecks (/home/user/carlos/scripts/rx-fax-record-binding-playwright-checks.js:800:5)',
  ].join('\n') };
  assert.equal(errorSourceLocation(timeout), ' at runChecks (rx-fax-record-binding-playwright-checks.js:800:5)');
  assert.equal(browserErrorClass(timeout), 'TimeoutError');
});

test('errorSourceLocation returns nothing rather than an unsafe or missing location', () => {
  const { errorSourceLocation } = require('./browser-error-class');
  assert.equal(errorSourceLocation(null), '');
  assert.equal(errorSourceLocation({ stack: 42 }), '');
  assert.equal(errorSourceLocation({ stack: 'Error: x\n    at eval (eval at <anonymous> (FAKE Patient.js:1:1))' }), '');
  // An anonymous frame keeps its location but no function name.
  assert.equal(errorSourceLocation({ stack: 'Error\n    at http://127.0.0.1:8080/carlos/js/app.js?v=1:3:4' }), ' at (/carlos/js/app.js:3:4)');
  // A function name outside the identifier alphabet is dropped, the location kept.
  assert.equal(errorSourceLocation({ stack: 'Error\n    at Object.<anonymous> (/x/scripts/check.js:9:9)' }), ' at (check.js:9:9)');
});
