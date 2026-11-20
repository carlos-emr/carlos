/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const {assertArchivedOriginal} = require('./echart-note-verify-appointment-status-playwright-checks');
const original = ['t', 'previous', '2026-10-15 09:00:00', '999998', '101'];
const before = [['1', 'older archive']];
const after = [...before, ['2', ...original]];
test('the archive must append the exact old status, audit fields and owner', () => {
  assert.doesNotThrow(() => assertArchivedOriginal(before, after, original, [...original]));
});
test('an unrelated old To Do archive cannot mask a newly archived changed status', () => {
  assert.throws(() => assertArchivedOriginal(before, after, original, ['tS', ...original.slice(1)]), /differs/);
});
for (const index of [1, 2, 3, 4]) test(`the old archive retains original appointment field ${index}`, () => {
  const changed = [...original]; changed[index] = 'changed';
  assert.throws(() => assertArchivedOriginal(before, after, original, changed), /differs/);
});
test('one save cannot silently change existing history or append zero/two archives', () => {
  for (const changed of [before, [...after, ['3']], [['changed'], after[1]]]) {
    assert.throws(() => assertArchivedOriginal(before, changed, original, original), /historical/);
  }
});
