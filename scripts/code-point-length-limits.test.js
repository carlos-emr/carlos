/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/share/javascript/codePointLengthLimits.js'), 'utf8');

function fixture(value, maximum) {
  const events = {};
  const field = {
    value, dataset: { codePointMaxlength: String(maximum) }, selectionStart: value.length, selectionEnd: value.length,
    removeAttribute(name) { this.removedAttribute = name; },
    setSelectionRange(start, end) { this.selectionStart = start; this.selectionEnd = end; },
  };
  vm.runInNewContext(source, { document: {
    addEventListener(name, handler) { events[name] = handler; },
    querySelectorAll() { return [field]; },
  } });
  return { field, dispatch(name, properties = {}) { events[name]({ target: field, ...properties }); } };
}

test('supplementary characters can fill the entire code-point capacity', () => {
  const value = '😀'.repeat(255);
  const f = fixture(value, 255);
  f.dispatch('input');
  assert.equal(f.field.value, value);
  assert.equal(f.field.selectionStart, 510);
});

test('overflow is limited at a complete code point and selection remains within the value', () => {
  const f = fixture('A😀漢🩺Z', 4);
  f.dispatch('input');
  assert.equal(f.field.value, 'A😀漢🩺');
  assert.equal(f.field.selectionStart, f.field.value.length);
  assert.equal(f.field.selectionEnd, f.field.value.length);
});

test('composition stays intact until compositionend then uses the same code-point limit', () => {
  const f = fixture('😀漢🩺', 2);
  f.dispatch('input', { isComposing: true });
  assert.equal(f.field.value, '😀漢🩺');
  f.dispatch('compositionend');
  assert.equal(f.field.value, '😀漢');
});

test('initial server-refused drafts are preserved while native UTF-16 limits are removed', () => {
  const f = fixture('retained oversized draft', 2);
  f.dispatch('DOMContentLoaded');
  assert.equal(f.field.value, 'retained oversized draft');
  assert.equal(f.field.removedAttribute, 'maxlength');
});

test('fields without a code-point limit are left unchanged', () => {
  const f = fixture('unlimited text', 2);
  delete f.field.dataset.codePointMaxlength;
  f.dispatch('input');
  assert.equal(f.field.value, 'unlimited text');
});
