/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const {assertSharedHistoryText, assertDeniedHistory} = require('./echart-group-note-history-playwright-checks');
const texts = {shared: 'SHARED assessment', privateText: 'PRIVATE assessment', future: 'FUTURE assessment', cumulative: 'CUMULATIVE assessment'};
test('shared history contains the granted row without other clinical text', () => {
  assert.doesNotThrow(() => assertSharedHistoryText(`Note History ${texts.shared}`, texts));
});
for (const name of ['privateText', 'future', 'cumulative']) test(`shared history rejects leaked ${name}`, () => {
  assert.throws(() => assertSharedHistoryText(`${texts.shared} ${texts[name]}`, texts), /disclosed/);
});
test('shared history cannot pass with a blank or unrelated response', () => {
  assert.throws(() => assertSharedHistoryText('Login required', texts), /omitted/);
});
test('denied history must be an explicit refusal without clinical text', () => {
  assert.doesNotThrow(() => assertDeniedHistory(403, '', Object.values(texts)));
  for (const status of [200, 302, 404, 500]) assert.throws(() => assertDeniedHistory(status, '', Object.values(texts)), /refused/);
  for (const text of Object.values(texts)) assert.throws(() => assertDeniedHistory(403, text, Object.values(texts)), /disclosed/);
});
