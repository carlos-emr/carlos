/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.join(__dirname, '../src/main/webapp');
const index = fs.readFileSync(path.join(root, 'share/javascript/oscarMDSIndex.js'), 'utf8');
const inbox = fs.readFileSync(path.join(root, 'WEB-INF/jsp/web/inboxhub/InboxhubForm.jsp'), 'utf8');

function setup(rows, modern = true) {
  function collection(items) {
    return { length: items.length, items,
      attr(name) { return items[0]?.[name]; },
      find() { return { val: () => items[0]?.checkbox }; },
      filter(predicate) { return collection(items.filter(row => typeof predicate === 'function'
        ? predicate.call(row) : row['data-lab-type'] === predicate.match(/="([^"]+)"/)[1])); },
    };
  }
  function jQuery(selector) {
    if (!selector) return collection([]);
    if (typeof selector === 'object') return collection([selector]);
    const matches = [...selector.matchAll(/\[(id|data-segment-id)="([^"]+)"\]/g)];
    assert(matches.length > 0, 'selector must use the expected bounded attribute lookup');
    return collection(rows.filter(row => matches.some(([, name, value]) => row[name] === value)));
  }
  const context = vm.createContext({ jQuery });
  if (modern) {
    vm.runInContext(inbox.slice(inbox.indexOf('function inboxhubItemElement('),
      inbox.indexOf('var countedAcknowledgedItems')), context);
  }
  vm.runInContext(index.slice(index.indexOf('function labDocumentRows('), index.indexOf('function bulkInboxAction(')), context);
  return (id, type) => context.labDocumentRows(id, type).items;
}
const rows = ['DOC', 'HL7', 'HRM'].map(type => ({ id: `labdoc_${type}_170`,
  'data-segment-id': '170', 'data-lab-type': type, checkbox: `170:${type}` }));

test('modern inbox resolves only the requested report type when segment numbers collide', () => {
  const find = setup(rows);
  for (const type of ['DOC', 'HL7', 'HRM']) assert.deepEqual(find('170', type), rows.filter(r => r['data-lab-type'] === type));
  assert.deepEqual(find('170'), []);
});

test('modern inbox accepts an untyped single match but rejects malformed selector tokens', () => {
  const find = setup([rows[0]]);
  assert.deepEqual(find('170'), [rows[0]]);
  assert.deepEqual(find('170"]'), []);
  assert.deepEqual(find('170', 'HL7"]'), []);
});

test('cached old inbox markup is still qualified by report type', () => {
  const old = rows.map(row => ({ ...row, id: 'labdoc_170', 'data-segment-id': undefined }));
  assert.deepEqual(setup(old)('170', 'DOC'), [old[0]]);
});

test('legacy checkbox identity distinguishes duplicate row ids', () => {
  const old = rows.map(row => ({ id: 'labdoc_170', checkbox: row.checkbox }));
  const find = setup(old, false);
  assert.deepEqual(find('170', 'HL7'), [old[1]]);
  assert.deepEqual(find('170'), []);
});

test('legacy known wrong-type row never falls back to an untyped match', () => {
  const old = { id: 'labdoc_170', checkbox: '170:DOC' };
  assert.deepEqual(setup([old], false)('170', 'HL7'), []);
});

test('legacy untyped unique row remains compatible but malformed input is rejected', () => {
  const old = { id: 'labdoc_170' };
  const find = setup([old], false);
  assert.deepEqual(find('170', 'DOC'), [old]);
  assert.deepEqual(find('170', 'DOC"]'), []);
  assert.deepEqual(find('170"]', 'DOC'), []);
});
