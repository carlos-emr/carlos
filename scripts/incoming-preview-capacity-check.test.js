/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {recoveredPreviewBytes, capacityTarget, expectedCapacityConsole} = require('./lib/incoming-preview-capacity-check');
const destination = 'https://127.0.0.1/carlos/documentManager/ManageDocument?method=viewIncomingDocPageAsPdf&pdfName=owned.pdf';
const pdf = Buffer.from('%PDF-1.7\n' + 'x'.repeat(120) + '\n%%EOF\n');
function fixture({status = 200, mime = 'application/pdf', bytes = pdf, failBody = false} = {}) {
  let disposed = 0;
  const gets = [];
  return {gets, disposed: () => disposed, navigation: {url: () => destination,
    body: async () => Buffer.from('<html>Chromium PDF viewer document</html>')},
    context: {request: {get: async (url, options) => {
      gets.push({url, options});
      return {status: () => status, headers: () => ({'content-type': mime}),
        body: async () => {if (failBody) throw new Error('transport failed');return bytes;},
        dispose: async () => {disposed++;}};
    }}}};
}
test('native viewer HTML requires complete PDF bytes from its same authorized destination', async () => {
  const f = fixture();
  assert.deepEqual(await recoveredPreviewBytes(f.context, f.navigation, 'Pdf'), pdf);
  assert.equal(f.gets.length, 1);
  assert.equal(f.gets[0].url, destination);
  assert.equal(f.gets[0].options.maxRedirects, 0);
  assert.equal(f.disposed(), 1);
});
for (const [name, values] of [
  ['redirect', {status: 302}], ['busy', {status: 503}], ['HTML login', {mime: 'text/html'}],
  ['mislabelled HTML', {bytes: Buffer.from('<html>' + 'x'.repeat(200) + '</html>')}],
  ['truncated PDF', {bytes: pdf.subarray(0, -7)}], ['empty body', {bytes: Buffer.alloc(0)}],
  ['transport failure', {failBody: true}]
]) test(`${name} cannot stand in for a recovered PDF and disposes the response`, async () => {
  const f = fixture(values);
  await assert.rejects(recoveredPreviewBytes(f.context, f.navigation, 'Pdf'));
  assert.equal(f.disposed(), 1);
});
test('image navigation uses its actual response bytes without a second request', async () => {
  const f = fixture();const png = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  assert.deepEqual(await recoveredPreviewBytes(f.context, {body: async () => png}, 'Image'), png);
  assert.equal(f.gets.length, 0);
});

test('only a proven injected503 for the exact preview may ignore its client-only PDF-view fragment', () => {
  const targets = new Set([capacityTarget(destination)]);
  const issue = {type: 'error', text: 'Failed to load resource: the server responded with a status of 503 (Service Unavailable)',
    location: {url: destination + '#view=fitV'}};
  assert(expectedCapacityConsole(issue, targets));
  for (const change of [{text: 'Failed to load resource: 500'}, {type: 'warning'},
    {text: 'Unrelated 503 problem'}, {location: {url: destination.replace('owned.pdf', 'other.pdf')}},
    {location: {url: destination.replace('127.0.0.1', 'other.invalid')}}, {location: {}}, {location: null}]) {
    assert.equal(expectedCapacityConsole({...issue, ...change}, targets), false);
  }
  assert.equal(capacityTarget('not a URL'), null);
});
