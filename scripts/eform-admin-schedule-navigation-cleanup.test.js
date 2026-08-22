const assert = require('node:assert/strict');
const test = require('node:test');

const { shouldVerifyDeletedEformRow } = require('./eform-admin-schedule-navigation-cleanup');

test('only verifies eForm removal after a successful cleanup response', () => {
  assert.equal(shouldVerifyDeletedEformRow(204), true);
  assert.equal(shouldVerifyDeletedEformRow(405), false);
});
