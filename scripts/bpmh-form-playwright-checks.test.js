/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const h = require('./lib/playwright-harness');
const x = require('./lib/export-content-helpers');
const { captureBpmhPrint, assertBpmhPdf } = require('./bpmh-form-playwright-checks');

const endpoint = 'https://carlos.test/carlos/formBPMH';
const patient = '123', saved = '37';
const note = 'FAKE-PW1234567890abcdef reconciled with the patient';
const instruction = 'FAKE-PW1234567890abcdef continue at discharge';

// A complete, generated PDF lets the controls exercise actual Poppler extraction without external fixtures.
function pdf(lines = [note, instruction]) {
  const escaped = value => value.replace(/([\\()])/g, '\\$1');
  const stream = `BT /F1 10 Tf 40 740 Td ${lines.map(line => `(${escaped(line)}) Tj 0 -18 Td`).join('\n')} ET`;
  const objects = ['<< /Type /Catalog /Pages 2 0 R >>', '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>', `<< /Length ${Buffer.byteLength(stream)} >>\nstream\n${stream}\nendstream`];
  let text = '%PDF-1.4\n'; const offsets = [0];
  for (const [i, object] of objects.entries()) { offsets.push(Buffer.byteLength(text)); text += `${i + 1} 0 obj\n${object}\nendobj\n`; }
  const at = Buffer.byteLength(text);
  text += `xref\n0 ${offsets.length}\n0000000000 65535 f \n${offsets.slice(1).map(offset => `${String(offset).padStart(10, '0')} 00000 n \n`).join('')}`;
  return Buffer.from(`${text}trailer\n<< /Size ${offsets.length} /Root 1 0 R >>\nstartxref\n${at}\n%%EOF\n`);
}

function fixture({ bytes = pdf(), status = 200, type = 'application/pdf', fields, method = 'POST' } = {}) {
  const state = { clicks: 0, fetches: 0, aborts: 0, disposals: 0, removed: false, fulfilled: [] };
  const frame = {};
  const response = { status: () => status, headers: () => ({ 'content-type': type, 'content-disposition': 'inline; filename=bpmh.pdf' }),
    body: async () => bytes, dispose: async () => { state.disposals++; } };
  const request = { url: () => endpoint, method: () => method, isNavigationRequest: () => true, frame: () => frame,
    postData: () => fields || new URLSearchParams({ method: 'print', 'form.demographicNo': patient, 'form.formId': saved }).toString() };
  const route = { request: () => request,
    fetch: async options => { state.fetches++; assert.deepEqual(options, { maxRedirects: 0, maxRetries: 0, timeout: 40000 }); return response; },
    fulfill: async options => { state.fulfilled.push(options); }, abort: async () => { state.aborts++; } };
  const page = { url: () => `${endpoint}?demographic_no=${patient}&formId=${saved}`, mainFrame: () => frame,
    route: async (matches, handler) => { assert(matches(new URL(endpoint))); assert(!matches(new URL(endpoint + '-other'))); state.matches = matches; state.handler = handler; },
    unroute: async (matches, handler) => { assert.equal(matches, state.matches); assert.equal(handler, state.handler); state.removed = true; },
    locator: selector => { assert.equal(selector, 'button[name="method"][value="print"]'); return { click: async () => { state.clicks++; if (state.handler) await state.handler(route); } }; },
    // The RED baseline presents the real response but has no download event, just like a viewer navigation.
    waitForEvent: async event => { assert.equal(event, 'download'); throw new Error('No download event for the native inline PDF'); },
    context: () => ({ waitForEvent: async event => { assert.equal(event, 'response'); return response; } }),
  };
  return { page, route, response, request, state, bytes };
}

async function microtasks() { for (let i = 0; i < 20; i++) await Promise.resolve(); }

test('native inline Print clicks once and fulfills the exact original response without a download or replay', async () => {
  const f = fixture(), result = await captureBpmhPrint(f.page, { patient, saved });
  assert.equal(f.state.clicks, 1); assert.equal(f.state.fetches, 1); assert.equal(f.state.aborts, 0);
  assert.equal(f.state.disposals, 1); assert.equal(f.state.removed, true);
  assert.equal(result.bytes, f.bytes); assert.equal(result.status, 200);
  assert.equal(f.state.fulfilled[0].response, f.response); assert.equal(f.state.fulfilled[0].body, f.bytes);
  assertBpmhPdf(result, { note, instruction });
});

test('Print waits for the response body and browser fulfillment before disposing and returning', async () => {
  const f = fixture(); let release, done = false;
  f.route.fulfill = async () => new Promise(resolve => { release = resolve; });
  const work = captureBpmhPrint(f.page, { patient, saved }).then(value => { done = true; return value; });
  await microtasks(); assert.equal(done, false); assert.equal(f.state.disposals, 0); assert.equal(f.state.removed, false);
  release(); await work; assert.equal(f.state.disposals, 1); assert.equal(f.state.removed, true);
});

for (const [key, value] of [['method', 'save'], ['form.demographicNo', '124'], ['form.formId', '0']]) {
  test(`a different native ${key} is aborted before transmission`, async () => {
    const fields = new URLSearchParams({ method: 'print', 'form.demographicNo': patient, 'form.formId': saved }); fields.set(key, value);
    const f = fixture({ fields: fields.toString() });
    await assert.rejects(captureBpmhPrint(f.page, { patient, saved }), /Print submitted a different/);
    assert.equal(f.state.fetches, 0); assert.equal(f.state.aborts, 1); assert(f.state.removed);
  });
}

test('a duplicate identity or a non-POST request is never forwarded', async () => {
  for (const options of [{ fields: `method=print&form.demographicNo=${patient}&form.formId=${saved}&form.formId=38` }, { method: 'GET' }]) {
    const f = fixture(options);
    await assert.rejects(captureBpmhPrint(f.page, { patient, saved }), /Print (submitted|did not submit)/);
    assert.equal(f.state.fetches, 0); assert.equal(f.state.aborts, 1); assert(f.state.removed);
  }
});

test('a native print transport failure is retained and never replayed', async () => {
  const f = fixture(), original = new Error('connection reset');
  f.route.fetch = async () => { f.state.fetches++; throw original; };
  await assert.rejects(captureBpmhPrint(f.page, { patient, saved }), error => error === original);
  assert.equal(f.state.fetches, 1); assert.equal(f.state.fulfilled.length, 0); assert.equal(f.state.aborts, 1); assert(f.state.removed);
});

test('a second native request fails and drains the first request before removing the route', async () => {
  const f = fixture(); let release, secondHandled, done = false;
  const observedSecond = new Promise(resolve => { secondHandled = resolve; });
  f.response.body = async () => new Promise(resolve => { release = () => resolve(f.bytes); });
  f.page.locator = () => ({ click: async () => {
    const first = f.state.handler(f.route); await microtasks();
    await f.state.handler(f.route); secondHandled(); await first;
  } });
  const work = captureBpmhPrint(f.page, { patient, saved }); work.then(() => { done = true; }, () => { done = true; });
  await observedSecond; assert.equal(f.state.fetches, 1); assert.equal(f.state.aborts, 1);
  assert.equal(done, false); assert.equal(f.state.removed, false);
  release(); await assert.rejects(work, /more than one BPMH request/);
  assert.equal(f.state.fetches, 1); assert.equal(f.state.disposals, 1); assert(f.state.removed);
});

test('another frame or a changed endpoint is never proof of the native print', async () => {
  for (const change of [{ frame: () => ({}) }, { url: () => endpoint + '?method=print' }]) {
    const f = fixture(); Object.assign(f.request, change);
    await assert.rejects(captureBpmhPrint(f.page, { patient, saved }), /did not submit the native BPMH form/);
    assert.equal(f.state.fetches, 0); assert.equal(f.state.aborts, 1); assert(f.state.removed);
  }
});

test('a click failure removes the route and retains its original error', async () => {
  const f = fixture(), original = new Error('Print button click failed');
  f.page.locator = () => ({ click: async () => { throw original; } });
  await assert.rejects(captureBpmhPrint(f.page, { patient, saved }), error => error === original);
  assert.equal(f.state.fetches, 0); assert(f.state.removed);
});

test('body and fulfillment failures dispose the response and remain fatal', async () => {
  for (const part of ['body', 'fulfill']) {
    const f = fixture(), original = new Error(`${part} failed`);
    (part === 'body' ? f.response : f.route)[part] = async () => { throw original; };
    await assert.rejects(captureBpmhPrint(f.page, { patient, saved }), error => error === original);
    assert.equal(f.state.fetches, 1); assert.equal(f.state.disposals, 1); assert.equal(f.state.aborts, 1); assert(f.state.removed);
  }
});

test('abort, response disposal and route cleanup errors cannot erase a failed native response', async () => {
  const f = fixture(), bodyFailure = new Error('body failed'), abortFailure = new Error('abort failed'), disposeFailure = new Error('dispose failed'), cleanupFailure = new Error('unroute failed');
  f.response.body = async () => { throw bodyFailure; }; f.response.dispose = async () => { throw disposeFailure; };
  f.route.abort = async () => { f.state.aborts++; throw abortFailure; };
  f.page.unroute = async () => { throw cleanupFailure; };
  const contains = (error, expected) => error === expected || error.errors?.some(child => contains(child, expected));
  await assert.rejects(captureBpmhPrint(f.page, { patient, saved }), error => [bodyFailure, abortFailure, disposeFailure, cleanupFailure].every(expected => contains(error, expected)));
  assert.equal(f.state.fetches, 1); assert.equal(f.state.aborts, 1);
});

test('a no-op native Print fails within its budget and removes its route', async t => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  const f = fixture(); f.page.locator = () => ({ click: async () => {} });
  const work = captureBpmhPrint(f.page, { patient, saved }); work.catch(() => {});
  await microtasks(); t.mock.timers.tick(40000);
  await assert.rejects(work, /no completed native BPMH response within 40000ms/);
  assert.equal(f.state.fetches, 0); assert(f.state.removed);
});

for (const [label, change] of [['HTTP 500', { status: 500 }], ['login redirect', { status: 302 }],
  ['HTML response', { type: 'text/html' }], ['lookalike MIME', { type: 'application/pdf-error' }]]) {
  test(`${label} remains a failure even when it carries complete PDF bytes`, async () => {
    const f = fixture(change), result = await captureBpmhPrint(f.page, { patient, saved });
    assert.throws(() => assertBpmhPdf(result, { note, instruction }), /Print answered HTTP/);
    assert.equal(result.status, change.status || 200); assert(f.state.removed);
  });
}

test('HTML masquerading as PDF and a truncated PDF fail before content is trusted', () => {
  for (const bytes of [Buffer.from('<html>login</html>'), pdf().subarray(0, pdf().length - 12)]) {
    assert.throws(() => assertBpmhPdf({ status: 200, headers: { 'content-type': 'application/pdf' }, bytes }, { note, instruction }), /not a PDF|truncated/);
  }
});

test('Poppler must extract every character of both saved fields, including their suffixes', () => {
  for (const [lines, missing] of [[[note, instruction.slice(0, -5)], /drug instruction/], [[note.slice(0, -5), instruction], /saved note/],
    [[note], /drug instruction/], [[instruction], /saved note/]]) {
    assert.throws(() => assertBpmhPdf({ status: 200, headers: { 'content-type': 'application/pdf' }, bytes: pdf(lines) }, { note, instruction }), missing);
  }
  assertBpmhPdf({ status: 200, headers: { 'content-type': 'application/pdf; charset=binary' }, bytes: pdf([note.slice(0, 25), note.slice(25), instruction]) }, { note, instruction });
});

test('actual Print step accepts a native inline response and checks both saved PDF fields', async () => {
  const source = fs.readFileSync(process.env.BPMH_CHECK_SOURCE || path.join(__dirname, 'bpmh-form-playwright-checks.js'), 'utf8');
  const start = source.indexOf("  await s.step('Print answers a PDF of the saved form', async () => {");
  const end = source.indexOf("\n  await s.step('GET and HEAD carrying the save", start);
  assert(start > 0 && end > start);
  for (const bytes of [pdf(), pdf([note]), pdf([instruction])]) {
    const f = fixture({ bytes }), cleanups = [];
    const sandbox = { h, x, form: f.page, patient, saved, note, instruction, captureBpmhPrint, assertBpmhPdf, require,
      process: { env: {} }, console: { log() {} },
      sql: { value: () => '1' }, s: { step: async (_, body) => body(), cleanup: cleanup => cleanups.push(cleanup) } };
    try {
      const work = vm.runInNewContext(`(async () => { ${source.slice(start, end)} })()`, sandbox);
      if (bytes.equals(pdf())) { await work; assert.equal(f.state.clicks, 1); assert.equal(f.state.fetches, 1); }
      else await assert.rejects(work, /printed PDF lost/);
    } finally { for (const cleanup of cleanups.reverse()) cleanup(); }
  }
});
