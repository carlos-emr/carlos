/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const h = require('./lib/playwright-harness');

function fakePage() {
  const listeners = new Map();
  return {
    on(name, callback) { if (!listeners.has(name)) listeners.set(name, []); listeners.get(name).push(callback); },
    emit(name, payload) { return Promise.all((listeners.get(name) || []).map(callback => callback(payload))); },
    listenerCount(name) { return (listeners.get(name) || []).length; },
  };
}

function extract(source, name) {
  const start = source.indexOf(`function ${name}(`); assert.ok(start >= 0, `Missing actual ${name}`);
  const end = source.indexOf('\n}', start); assert.ok(end > start);
  return source.slice(source.slice(start - 6, start) === 'async ' ? start - 6 : start, end + 2);
}

function diagnostics(name) {
  const source = fs.readFileSync(path.join(__dirname, `${name}-playwright-checks.js`), 'utf8');
  const recorder = h.createRecorder(); const context = fakePage();
  const sandbox = vm.createContext({ h, recorder, strictRecorder: recorder, context,
    wireStrictPage: h.wireStrictPage, badResponses: [], consoleIssues: [], displayImageResponses: [] });
  const early = source.split('\n').find(line => line.includes("context.on('page', page =>") && line.includes('wireStrictPage('));
  assert.ok(early, 'Owned popups need synchronous diagnostics before their first script');
  vm.runInContext(early.trim(), sandbox);
  if (name === 'eform-render' || name === 'tickler-crud') {
    for (const helper of name === 'eform-render'
      ? ['isExpectedMissingAsset', 'isIgnorableConsoleMessage', 'isSevereConsoleMessage', 'wirePage'] : ['wirePage']) {
      vm.runInContext(extract(source, helper), sandbox);
    }
  }
  return { recorder, context, sandbox, async page() {
    const page = fakePage(); await context.emit('page', page);
    if (sandbox.wirePage) sandbox.wirePage(page, name);
    return page;
  } };
}

for (const name of ['drug-search', 'document-upload', 'eform-render', 'tickler-crud']) {
  test(`${name}: actual early wiring rejects failed scripts`, async () => {
    const d = diagnostics(name); const page = await d.page();
    await page.emit('requestfailed', { url: () => 'https://127.0.0.1/carlos/required.js',
      resourceType: () => 'script', failure: () => ({ errorText: 'net::ERR_CONNECTION_RESET' }) });
    assert.throws(() => h.assertStrictPage(d.recorder), /request failed/);
  });
  test(`${name}: actual early wiring rejects HTML served as successful script`, async () => {
    const d = diagnostics(name); const page = await d.page();
    await page.emit('response', { url: () => 'https://127.0.0.1/carlos/required.js', status: () => 200,
      headers: () => ({ 'content-type': 'text/html' }), request: () => ({ method: () => 'GET', resourceType: () => 'script' }) });
    assert.throws(() => h.assertStrictPage(d.recorder), /script served as text\/html/);
  });
  test(`${name}: unexpected confirmation stays fatal with one dialog handler`, async () => {
    const d = diagnostics(name); const page = await d.page(); let dismissed = 0;
    await page.emit('dialog', { type: () => 'confirm', message: () => 'Unexpected change?',
      accept: async () => assert.fail('An unexpected confirmation must not be accepted'), dismiss: async () => { dismissed++; } });
    assert.equal(page.listenerCount('dialog'), 1); assert.equal(dismissed, 1);
    assert.throws(() => h.assertStrictPage(d.recorder), /unexpected confirm/);
  });
  test(`${name}: existing uncaught errors cannot acquire a shared baseline exemption`, async () => {
    const d = diagnostics(name); const page = await d.page();
    await page.emit('pageerror', new Error('expandPreview failed'));
    assert.equal(d.recorder.pageErrors.length, 1);
    assert.throws(() => h.assertStrictPage(d.recorder), /expandPreview failed/);
  });
  test(`${name}: existing severe console messages remain unbaselined`, async () => {
    const d = diagnostics(name); const page = await d.page();
    await page.emit('console', { type: () => 'error', text: () => 'expandPreview failed', location: () => ({}) });
    assert.equal(d.recorder.consoleIssues.length, 1);
    assert.throws(() => h.assertStrictPage(d.recorder), /expandPreview failed/);
  });
}

for (const name of ['eform-render', 'tickler-crud']) {
  test(`${name}: its original narrower HTTP policy still records a legacy asset404`, async () => {
    const d = diagnostics(name); const page = await d.page();
    await page.emit('response', { url: () => 'https://127.0.0.1/carlos/eform/displayImage?imagefile=signature_pad.min.js',
      status: () => 404, headers: () => ({}), request: () => ({ method: () => 'GET', resourceType: () => 'script' }) });
    assert.equal(d.recorder.badResponses.length, 0);
    assert.equal(d.sandbox.badResponses.length, 1, 'Retain the preexisting stricter HTTP observer');
  });
}

function uploadNavigation() {
  const source = fs.readFileSync(path.join(__dirname, 'eform-render-playwright-checks.js'), 'utf8');
  const sandbox = vm.createContext({ URL, assert: h.assert,
    appUrl: route => `https://127.0.0.1/carlos${route}` });
  vm.runInContext(extract(source, 'submitUploadAndAwaitAdmin'), sandbox);
  return sandbox.submitUploadAndAwaitAdmin;
}

test('upload waits for a new completion navigation registered before native submit', async () => {
  const submit = uploadNavigation(); const events = []; let complete; let options; let settled = false;
  const page = { waitForNavigation(value) { events.push('registered'); options = value; return new Promise(resolve => { complete = resolve; }); } };
  const pending = submit(page, { click: async () => { events.push('clicked'); } }, 'Forms').then(() => { settled = true; });
  await Promise.resolve(); await Promise.resolve();
  assert.deepEqual(events, ['registered', 'clicked']); assert.equal(settled, false);
  assert.equal(options.url(new URL('https://127.0.0.1/carlos/administration?show=Forms')), true);
  assert.equal(options.url(new URL('https://127.0.0.1/carlos/administration?show=ImageUpload')), false);
  assert.equal(options.url(new URL('https://other.invalid/carlos/administration?show=Forms')), false);
  complete({ status: () => 200 }); await pending; assert.equal(settled, true);
});

for (const value of [null, { status: () => 500 }]) {
  test(`upload refuses ${value ? 'failed' : 'same-document'} completion instead of swallowing it`, async () => {
    await assert.rejects(uploadNavigation()({ waitForNavigation: async () => value }, { click: async () => {} }, 'Forms'),
      /native upload did not finish/);
  });
}
