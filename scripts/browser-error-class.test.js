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

const APP = { baseUrl: 'http://127.0.0.1:8080/carlos' };

test('errorSourceLocation names the throwing page script without query string, session id or record ids', () => {
  const { errorSourceLocation } = require('./browser-error-class');
  // The shape Playwright hands a pageerror listener: message is the first line only.
  const pageError = { name: 'TypeError', message: "Cannot read properties of null (reading 'value')", stack: [
    "TypeError: Cannot read properties of null (reading 'value')",
    '    at setComment (http://127.0.0.1:8080/carlos/oscarRx/ViewScript2.jsp;jsessionid=ABC123?scriptId=45&demographicNo=1:812:31)',
    '    at onload (http://127.0.0.1:8080/carlos/oscarRx/ViewScript2.jsp?demographicNo=1:1:1)',
  ].join('\n') };
  const location = errorSourceLocation(pageError, APP);
  assert.equal(location, ' at setComment (/carlos/oscarRx/ViewScript2.jsp:812:31)');
  assert.doesNotMatch(location, /demographicNo|scriptId|jsessionid|ABC123/);
  const restPath = { stack: 'Error: x\n    at render (http://127.0.0.1:8080/carlos/ws/rs/demographics/12345/notes/0f8fad5b-d9cb-469f-a165-70867728950e:3:4)' };
  assert.equal(errorSourceLocation(restPath, APP), ' at render (/carlos/ws/rs/demographics/:id/notes/:id:3:4)');
});

test('errorSourceLocation trusts page frames only on the application under test', () => {
  const { errorSourceLocation, createErrorSourceLocator } = require('./browser-error-class');
  // Playwright keeps only the first message line in .message and repeats the rest in .stack,
  // so a multi-line message can put "frames" ahead of the real ones.
  const injected = { name: 'Error', message: 'FAKE-Smith line1', stack: [
    'Error: FAKE-Smith line1',
    '    at Smith (Jane-1970.js:1:1)',
    '    at Smith (https://evil.example/carlos/FAKE-Smith.js:1:1)',
    '    at Smith (http://127.0.0.1:8080/other/FAKE-Smith.js:1:1)',
    '    at Smith (http://127.0.0.1:8080/carlosx/FAKE-Smith.js:1:1)',
    '    at boom (http://127.0.0.1:8080/carlos/oscarRx/ViewScript2.jsp?demographicNo=1:2:36)',
  ].join('\n') };
  // Another origin, another context path, a prefix-sharing path and a non-file "frame" are all
  // dropped; only the application's own script survives.
  assert.equal(errorSourceLocation(injected, APP), ' at boom (/carlos/oscarRx/ViewScript2.jsp:2:36)');
  assert.equal(createErrorSourceLocator('http://127.0.0.1:8080/carlos/')(injected), ' at boom (/carlos/oscarRx/ViewScript2.jsp:2:36)');
  // With no base URL no page frame is trusted.
  assert.equal(errorSourceLocation(injected), '');
  // A function name with spaces cannot drag the query string into the location.
  assert.equal(errorSourceLocation({ stack: 'Error: x\n    at new FAKE (http://127.0.0.1:8080/carlos/x.jsp?name=FAKE/Smith:1:2)' }, APP), ' at (/carlos/x.jsp:1:2)');
});

test('errorSourceLocation reaches the check\'s own frame past Node internals, node_modules and helpers', () => {
  const { errorSourceLocation } = require('./browser-error-class');
  const path = require('node:path');
  // A Playwright error's stack does not begin with its name.
  const timeout = { name: 'TimeoutError', stack: [
    'locator.click: Timeout 30000ms exceeded.',
    'Call log:',
    "  - waiting for locator('#fax')",
    '',
    `    at run (${path.join(__dirname, 'lib', 'playwright-harness.js')}:309:23)`,
    '    at ProtocolError (/repo/node_modules/playwright-core/lib/client/connection.js:1:2)',
    '    at process.processTicksAndRejections (node:internal/process/task_queues:105:5)',
    `    at async runChecks (${path.join(__dirname, 'rx-fax-record-binding-playwright-checks.js')}:800:5)`,
  ].join('\n') };
  assert.equal(errorSourceLocation(timeout, APP), ' at runChecks (rx-fax-record-binding-playwright-checks.js:800:5)');
  assert.equal(browserErrorClass(timeout), 'TimeoutError');
  // With no check frame, the first real helper frame is still better than nothing.
  const helperOnly = { stack: `Error: x\n    at run (${path.join(__dirname, 'lib', 'playwright-harness.js')}:309:23)` };
  assert.equal(errorSourceLocation(helperOnly), ' at run (playwright-harness.js:309:23)');
  // Text shaped like a frame that names no real file under scripts/ is not a location.
  assert.equal(errorSourceLocation({ stack: 'Error: x\n    at Smith (Jane-1970.js:1:1)' }), '');
  assert.equal(errorSourceLocation({ stack: 'Error: x\n    at f (/etc/passwd:1:1)' }), '');
});

test('errorSourceLocation returns nothing rather than an unsafe or missing location', () => {
  const { errorSourceLocation } = require('./browser-error-class');
  assert.equal(errorSourceLocation(null, APP), '');
  assert.equal(errorSourceLocation({ stack: 42 }, APP), '');
  assert.equal(errorSourceLocation({ stack: 'Error: x\n    at eval (eval at <anonymous> (FAKE Patient.js:1:1))' }, APP), '');
  assert.equal(errorSourceLocation({ stack: 'Error: x\n    at f (blob:http://127.0.0.1:8080/0f8fad5b:1:1)' }, APP), '');
  assert.equal(errorSourceLocation({ stack: 'Error: x\n    at f (http://127.0.0.1:8080/carlos/x.js:1:1)' }, { baseUrl: 'not a url' }), '');
  // An anonymous frame keeps its location but no function name.
  assert.equal(errorSourceLocation({ stack: 'Error\n    at http://127.0.0.1:8080/carlos/js/app.js?v=1:3:4' }, APP), ' at (/carlos/js/app.js:3:4)');
  // A function name outside the identifier alphabet is dropped, the location kept.
  assert.equal(errorSourceLocation({ stack: 'Error\n    at Object.<anonymous> (http://127.0.0.1:8080/carlos/js/a.js:9:9)' }, APP), ' at (/carlos/js/a.js:9:9)');
});

test('every check that reports a source location binds it to its own validated BASE_URL', () => {
  const { createErrorSourceLocator } = require('./browser-error-class');
  const pageError = { stack: 'TypeError: x\n    at setComment (http://127.0.0.1:8080/carlos/oscarRx/ViewScript2.jsp?demographicNo=1:5:6)' };
  for (const name of ['rx-fax-record-binding', 'rx-fax-signature-stamp', 'rx-fax-reprint-represcribe', 'patient-list-by-appointment-export']) {
    const source = fs.readFileSync(path.join(__dirname, `${name}-playwright-checks.js`), 'utf8');
    // baseUrl itself, never baseUrl.href: one check's validateBaseUrl returns a string, whose
    // .href is undefined, and an undefined base rejects every page frame.
    assert.match(source, /const errorSourceLocation = createErrorSourceLocator\(baseUrl\);/, name);
  }
  // The two shapes the checks' validators return: a URL object, and the origin+path string.
  const expected = ' at setComment (/carlos/oscarRx/ViewScript2.jsp:5:6)';
  assert.equal(createErrorSourceLocator(new URL('http://127.0.0.1:8080/carlos'))(pageError), expected);
  assert.equal(createErrorSourceLocator('http://127.0.0.1:8080/carlos')(pageError), expected);
  assert.equal(createErrorSourceLocator(undefined)(pageError), '', 'no base URL trusts no page frame');
});
