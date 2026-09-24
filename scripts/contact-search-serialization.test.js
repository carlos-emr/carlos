/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const jsp = fs.readFileSync(path.join(__dirname,
  '../src/main/webapp/WEB-INF/jsp/demographic/contactSearch.jsp'), 'utf8');

test('the contact popup preserves quotes, backslashes and newlines in its JSON handoff', () => {
  // Execute the page's actual serializer, replacing only its two server-rendered
  // field identifiers. The old string concatenation creates invalid JSON.
  const match = jsp.match(/function serializePopupData\(data1, data2\) \{([\s\S]*?)^ {12}\}/m);
  assert.ok(match, 'contact-search serializer was not found');
  const fields = ['contact_1.contactId', 'contact_1.contactName'];
  const body = match[1].replace(/<carlos:encode\b[^\n]*?\/>/g, () => fields.shift());
  assert.equal(fields.length, 0, 'both rendered field identifiers must be supplied');
  const sampleName = 'FAKE O\'Neil "A&B" \\ contact\nsecond line';
  let received;
  let closed = false;
  vm.runInNewContext(`function serializePopupData(data1, data2) {${body}}\nserializePopupData('42', sampleName);`, {
    sampleName,
    opener: { popUpData(json) { received = JSON.parse(json); } },
    self: { close() { closed = true; } },
  }, { timeout: 1000 });
  assert.deepEqual(received, { 'contact_1.contactId': '42', 'contact_1.contactName': sampleName });
  assert.equal(closed, true);
});
