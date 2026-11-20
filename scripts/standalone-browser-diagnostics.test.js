/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const h = require('./lib/playwright-harness');

const scripts = [
  { name: 'application-health', missingAsset: 'isExpectedMissingAsset',
    consoleNoise: 'isExpectedConsoleNoise', strongerWarning: 'DataTable is not a function' },
  { name: 'browser-surface', missingAsset: 'isExpectedMissingFixtureImage',
    strongerWarning: 'forceWindowPaths failed' },
  { name: 'schedule-links', missingAsset: 'isExpectedMissingAsset',
    consoleNoise: 'isExpectedConsoleNoise', strongerWarning: 'redeclaration in schedule' },
];

// Run the real event-wiring functions without importing a browser script's main
// routine, opening a browser, creating fixtures or reading account credentials.
function diagnostics(script) {
  const source = fs.readFileSync(path.join(__dirname, `${script.name}-playwright-checks.js`), 'utf8');
  const recorder = h.createRecorder();
  const findings = [];
  const badResponses = [];
  const consoleIssues = [];
  const sandbox = vm.createContext({ h, recorder, findings, badResponses, consoleIssues });
  const names = [script.missingAsset, script.consoleNoise, 'isSevereConsoleMessage', 'wirePage'].filter(Boolean);
  for (const name of names) {
    const start = source.indexOf(`function ${name}(`);
    assert.ok(start >= 0, `${script.name}: missing real ${name} function`);
    const end = source.indexOf('\n}', start);
    assert.ok(end > start, `${script.name}: missing ${name} closing brace`);
    vm.runInContext(source.slice(start, end + 2), sandbox);
  }
  return { source, sandbox, recorder, findings, badResponses, consoleIssues,
    wire: page => sandbox.wirePage(page, script.name) };
}

function fakePage() {
  const handlers = new Map();
  return {
    on(name, fn) { if (!handlers.has(name)) handlers.set(name, []); handlers.get(name).push(fn); },
    emit(name, value) { return Promise.all((handlers.get(name) || []).map(fn => fn(value))); },
    listenerCount(name) { return (handlers.get(name) || []).length; },
  };
}

function response({ status = 200, contentType = 'text/html', resourceType = 'document',
  url = 'https://127.0.0.1/carlos/example' } = {}) {
  const request = { method: () => 'GET', resourceType: () => resourceType };
  return { status: () => status, headers: () => ({ 'content-type': contentType }),
    request: () => request, url: () => url };
}

const consoleMessage = (type, text) => ({ type: () => type, text: () => text, location: () => ({}) });

for (const script of scripts) {
  test(`${script.name} rejects failed subresources using its real wiring`, async () => {
    const d = diagnostics(script); const page = fakePage(); d.wire(page);
    await page.emit('requestfailed', { url: () => 'https://127.0.0.1/carlos/owned.js',
      resourceType: () => 'script', failure: () => ({ errorText: 'net::ERR_CONNECTION_RESET' }) });
    assert.equal(d.recorder.requestFailures.length, 1);
    assert.throws(() => h.assertStrictPage(d.recorder), /request failed/);
  });

  test(`${script.name} rejects a successful script response carrying an HTML error page`, async () => {
    const d = diagnostics(script); const page = fakePage(); d.wire(page);
    await page.emit('response', response({ resourceType: 'script' }));
    assert.equal(d.recorder.badResponses.length, 1);
    assert.equal(d.findings.length + d.badResponses.length, 0,
      'The old HTTP>=400 observer alone cannot detect this successful HTML response');
    assert.throws(() => h.assertStrictPage(d.recorder), /script served as text\/html/);
  });

  test(`${script.name} dismisses and rejects an unexpected confirm with one listener`, async () => {
    const d = diagnostics(script); const page = fakePage(); d.wire(page);
    let accepted = 0; let dismissed = 0;
    await page.emit('dialog', { type: () => 'confirm', message: () => 'Unexpected change?',
      accept: async () => { accepted += 1; }, dismiss: async () => { dismissed += 1; } });
    assert.equal(page.listenerCount('dialog'), 1);
    assert.equal(accepted, 0); assert.equal(dismissed, 1);
    assert.throws(() => h.assertStrictPage(d.recorder), /unexpected confirm/);
  });

  test(`${script.name} keeps its empty console baseline`, async () => {
    const d = diagnostics(script); const page = fakePage(); d.wire(page);
    await page.emit('console', consoleMessage('error', 'expandPreview failed'));
    assert.equal(d.recorder.consoleIssues.length, 1,
      'The general expandPreview baseline must not suppress a migrated legacy observer');
    assert.throws(() => h.assertStrictPage(d.recorder), /expandPreview failed/);
  });

  test(`${script.name} retains its stronger standalone warning classification`, async () => {
    const d = diagnostics(script); const page = fakePage(); d.wire(page);
    await page.emit('console', consoleMessage('warning', script.strongerWarning));
    assert.equal(d.recorder.consoleIssues.length, 0,
      'This specific warning is outside the common classifier');
    assert.equal(d.findings.length + d.consoleIssues.length, 1,
      'Its original stricter standalone observer must still record the warning');
  });

  test(`${script.name} retains its narrower missing-asset HTTP policy`, async () => {
    const d = diagnostics(script); const page = fakePage(); d.wire(page);
    await page.emit('response', response({ status: 404,
      url: 'https://127.0.0.1/carlos/eform/displayImage?imagefile=signature_pad.min.js' }));
    assert.equal(d.recorder.badResponses.length, 0,
      'The common observer deliberately tolerates this known legacy asset');
    assert.equal(d.findings.length + d.badResponses.length, 1,
      'The standalone observer must retain its narrower existing asset policy');
  });

  test(`${script.name} captures a popup's initial response before later page labelling`, async () => {
    const d = diagnostics(script); const context = fakePage();
    d.sandbox.context = context;
    const earlyWiring = d.source.split('\n').find(line => line.includes("context.on('page', page => h.wireStrictPage("));
    assert.ok(earlyWiring, 'Owned context pages must be wired before a popup can navigate');
    vm.runInContext(earlyWiring.trim(), d.sandbox);
    const page = fakePage(); await context.emit('page', page);
    await page.emit('response', response({ resourceType: 'script' }));
    d.wire(page);
    assert.equal(page.listenerCount('dialog'), 1, 'Relabelling must not add a second dialog listener');
    assert.throws(() => h.assertStrictPage(d.recorder), /script served as text\/html/);
  });
}
