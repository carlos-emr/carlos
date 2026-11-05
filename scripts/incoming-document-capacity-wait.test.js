/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/incomingDocumentCapacityWait.js'), 'utf8');
const base = 'https://local/carlos/documentManager/ManageDocument?queueId=1&pdfDir=File&pdfName=owned.pdf&curPage=2&method=';
function preview(url, {marker = true, attempt = 0, random = .5, storageFailure = false} = {}) {
  const timers = [], events = {}, stored = [];
  let reloads = 0;
  const math = Object.create(Math); math.random = () => random;
  const window = {location: {href: url, reload() {reloads++;}}, sessionStorage: {
    getItem() {if (storageFailure) throw new Error('disabled'); return String(attempt);},
    setItem(key, value) {stored.push({key, value});}
  }, setTimeout(callback, delay) {timers.push({callback, delay});return timers.length;},
  clearTimeout(id) {timers[id - 1].cancelled = true;}, addEventListener: (name, handler) => {events[name] = handler;}};
  vm.runInNewContext(source, {URL, Math: math, window, document: {getElementById: () => marker ? {} : null}});
  return {timers, events, stored, reloads: () => reloads};
}
for (const mode of ['viewIncomingDocPageAsPdf', 'viewIncomingDocPageAsImage']) {
  test(`${mode}: authorized capacity HTML continues safe preview GETs beyond three busy responses`, () => {
    for (let attempt = 0; attempt < 8; attempt++) {
      const value = preview(base + mode, {attempt});
      assert.equal(value.timers.length, 1);
      assert.ok(value.timers[0].delay >= 1000 && value.timers[0].delay <= 10000);
      value.timers[0].callback(); assert.equal(value.reloads(), 1);
      assert.doesNotMatch(value.stored[0].key, /owned|\.pdf|queueId|File/);
      assert.ok(Number(value.stored[0].value) <= 3);
    }
  });
}
test('ordinary error HTML without the server capacity marker never retries', () => {
  assert.equal(preview(base + 'viewIncomingDocPageAsPdf', {marker: false}).timers.length, 0);
});
test('mutation actions, other routes and invalid preview identity cannot turn into reload loops', () => {
  for (const url of [base + 'rotate90', base + 'extractPagePDF', base.replace('/ManageDocument?', '/SaveAnnotatedDocument?') + 'viewIncomingDocPageAsPdf',
    base.replace('queueId=1', 'queueId=-1') + 'viewIncomingDocPageAsPdf',
    base.replace('curPage=2', 'curPage=0') + 'viewIncomingDocPageAsPdf',
    base.replace('pdfName=owned.pdf', 'pdfName=') + 'viewIncomingDocPageAsPdf']) {
    assert.equal(preview(url).timers.length, 0);
  }
});
test('changing/removing the iframe cancels its timer; history restoration starts one replacement timer', () => {
  const value = preview(base + 'viewIncomingDocPageAsImage');
  value.events.pagehide(); assert.equal(value.timers[0].cancelled, true);
  value.events.pageshow(); assert.equal(value.timers.length, 2);
  value.events.pageshow(); assert.equal(value.timers.length, 2, 'duplicate pageshow must not multiply retries');
});
test('steady-state waits retain jitter and storage-disabled sessions still wait safely', () => {
  const first = preview(base + 'viewIncomingDocPageAsImage', {attempt: 100, random: 0});
  const second = preview(base + 'viewIncomingDocPageAsImage', {attempt: 100, random: 1});
  assert.ok(first.timers[0].delay < second.timers[0].delay);
  assert.ok(second.timers[0].delay <= 10000);
  assert.equal(preview(base + 'viewIncomingDocPageAsPdf', {storageFailure: true}).timers.length, 1);
});

test('queue-root previews with an explicitly empty directory keep waiting in both modes', () => {
  for (const mode of ['viewIncomingDocPageAsPdf', 'viewIncomingDocPageAsImage']) {
    const root = preview(base.replace('pdfDir=File', 'pdfDir=') + mode);
    assert.equal(root.timers.length, 1);
    root.timers[0].callback();
    assert.equal(root.reloads(), 1, 'the same valid queue-root GET must retry');
    assert.equal(preview(base.replace('pdfDir=File&', '') + mode).timers.length, 0,
      'an absent directory parameter remains invalid, as on the server');
  }
});
