/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * The inbox's acknowledge handler (oscarMDSIndex.js updateStatus) serves lab and
 * document forms alike. Only a document has an inbox queue link, and lab segment ids
 * are a separate sequence from document ids, so the queue update must fire for a
 * DOCUMENT acknowledge only: posting a lab id inactivated the queue link of whatever
 * unrelated document carried the same number.
 */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const index = fs.readFileSync(
  path.join(__dirname, '../src/main/webapp/share/javascript/oscarMDSIndex.js'), 'utf8');

function acknowledge(formId, labType) {
  const queueUpdates = [];
  const jQuery = () => ({ length: 0, val: () => '0' });
  jQuery.post = () => ({ done(callback) { callback('{"clearedCount":1}'); return this; } });
  const context = vm.createContext({
    jQuery,
    console: { log() {}, error() {} },
    alert() {},
    contextpath: '',
    document: { getElementById: () => ({}) },
    serializeFormToObject: () => ({ labType }),
    updateDocStatusInQueue: (id) => queueUpdates.push(id),
    window: {},
    _in_window: false,
    labDocumentRows: () => ({ slideUp() {} }),
    updateGlobalDataAndSideNav() {},
  });
  vm.runInContext(index.slice(index.indexOf('function serverClearedCount('),
    index.indexOf('function fileDoc(')), context);
  context.updateStatus(formId);
  return queueUpdates;
}

test('acknowledging a document inactivates that document in the inbox queue', () => {
  assert.deepEqual(acknowledge('acknowledgeForm_22', 'DOC'), ['22']);
});

test('acknowledging a lab never touches the document queue with the lab id', () => {
  assert.deepEqual(acknowledge('acknowledgeForm_22', 'HL7'), []);
});

test('an acknowledge form with no report type is not treated as a document', () => {
  assert.deepEqual(acknowledge('forms_22', undefined), []);
});
