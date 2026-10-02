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

for (const value of ['A new note & "quotes"\nsecond line', '']) {
  test(`a reloaded preview uses the current note (${JSON.stringify(value)})`, () => {
    const notes = { style: {}, textContent: 'stale' }, hidden = { value: 'stale' };
    const context = {
      hasPreview: true,
      document: { getElementById: () => ({ value }) },
      frames: { preview: { document: {
        getElementById: () => notes, getElementsByName: () => [hidden]
      } } }
    };
    vm.runInNewContext(source.slice(start, end).replace(/<%=.*?%>/g, 'original saved note'), context);
    context.setComment();
    assert.equal(notes.textContent, value);
    assert.equal(hidden.value, value.replace(/\n/g, '\r\n'));
    assert.match(source, /<iframe id='preview'[^>]*onload="setComment\(\)"/);
  });
}
