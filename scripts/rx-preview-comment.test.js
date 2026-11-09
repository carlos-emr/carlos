/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('src/main/webapp/WEB-INF/jsp/rx/ViewScript2.jsp', 'utf8');
const start = source.indexOf('function setComment()');
const end = source.indexOf('function setDefaultAddr()', start);
assert.ok(start >= 0 && end > start);
const controlsStart = source.indexOf('function updateHalfLetterPrintControls()');
const controlsEnd = source.indexOf('function associateSavedSignature(', controlsStart);
assert.ok(controlsStart >= 0 && controlsEnd > controlsStart);

for (const value of ['A new note & "quotes"\nsecond line', '']) {
  for (const [pending, failed] of [[false, false], [true, false], [false, true]]) {
    test(`a reloaded preview uses the current note (${JSON.stringify(value)}, signature pending=${pending}, failed=${failed})`, () => {
      const notes = { style: {}, textContent: 'stale' }, hidden = { value: 'stale' };
      const printButton = { disabled: !(pending || failed), style: {} };
      const preview = {};
      let onload;
      const context = {
        hasPreview: true,
        signatureAssociationPending: pending,
        signatureAssociationFailed: failed,
        document: {
          getElementById: id => id === 'additionalNotes' ? { value } : id === 'preview' ? preview : null,
          addEventListener(type, listener, capture) {
            assert.equal(type, 'load');
            assert.equal(capture, true);
            assert.equal(onload, undefined, 'register the preview handler once');
            onload = listener;
          }
        },
        frames: { preview: { document: {
          getElementById: id => id === 'additNotes' ? notes : id === 'halfLetterPrint' ? printButton : null,
          getElementsByName: name => name === 'additNotes' ? [hidden] : []
        } } }
      };
      vm.runInNewContext(source.slice(start, end).replace(/<%=.*?%>/g, 'original saved note'), context);
      vm.runInNewContext(source.slice(controlsStart, controlsEnd), context);
      assert.equal(typeof onload, 'function', 'the preview must apply the current note after each load');
      onload({ target: {} });
      assert.equal(notes.textContent, 'stale', 'an unrelated load must not update the preview');
      onload({ target: preview });
      assert.equal(notes.textContent, value);
      assert.equal(hidden.value, value.replace(/\n/g, '\r\n'));
      assert.equal(printButton.disabled, pending || failed);
    });
  }
}
