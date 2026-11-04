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
const onload = source.match(/<iframe id='preview'[^>]*onload="([^"]+)"/);
assert.ok(onload, 'the preview must apply the current note after each load');

for (const value of ['A new note & "quotes"\nsecond line', '']) {
  for (const pending of [false, true]) {
    test(`a reloaded preview uses the current note (${JSON.stringify(value)}, signature pending=${pending})`, () => {
      const notes = { style: {}, textContent: 'stale' }, hidden = { value: 'stale' };
      const printButton = { disabled: !pending, style: {} };
      const context = {
        hasPreview: true,
        signatureAssociationPending: pending,
        document: { getElementById: id => id === 'additionalNotes' ? { value } : null },
        frames: { preview: { document: {
          getElementById: id => id === 'additNotes' ? notes : id === 'halfLetterPrint' ? printButton : null,
          getElementsByName: name => name === 'additNotes' ? [hidden] : []
        } } }
      };
      vm.runInNewContext(source.slice(start, end).replace(/<%=.*?%>/g, 'original saved note'), context);
      vm.runInNewContext(source.slice(controlsStart, controlsEnd), context);
      vm.runInNewContext(onload[1], context);
      assert.equal(notes.textContent, value);
      assert.equal(hidden.value, value.replace(/\n/g, '\r\n'));
      assert.equal(printButton.disabled, pending);
    });
  }
}
