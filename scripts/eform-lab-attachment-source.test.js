/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/eform/eformFloatingToolbar/eform_floating_toolbar.js'), 'utf8');
const start = source.indexOf('function eformAttachmentSubmissionValue(');
const end = source.indexOf('\n}\n', start) + 3;
assert.ok(start >= 0 && end > start);
const context = vm.createContext({});
vm.runInContext(source.slice(start, end), context);
function value(name, id, labType, unlisted = false) {
  return context.eformAttachmentSubmissionValue({
    val: () => id,
    attr: key => ({ name, 'data-lab-type': labType }[key]),
    hasClass: key => key === 'unlisted_attachment_check' && unlisted,
  });
}
test('toolbar preserves source identity when two lab systems share the same ID', () => {
  assert.equal(value('labNo', '77', 'HL7'), 'HL7:77');
  assert.equal(value('labNo', '77', 'MDS'), 'MDS:77');
  assert.equal(value('docNo', '77'), '77');
});
test('toolbar rejects missing or invalid source metadata instead of submitting an ambiguous lab', () => {
  for (const source of [undefined, '', 'DOC', 'HL7:7']) {
    assert.throws(() => value('labNo', '77', source), /Invalid lab/);
  }
  assert.throws(() => value('labNo', '0', 'HL7'), /Invalid lab/);
});
test('toolbar preserves unresolved stored selections until the user explicitly removes them', () => {
  assert.equal(value('labNo', 'UNRESOLVED:77', undefined, true), 'UNRESOLVED:77');
  assert.equal(value('labNo', 'MDS:77', undefined, true), 'MDS:77');
});
