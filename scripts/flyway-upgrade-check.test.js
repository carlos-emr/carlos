/* Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
// Proposal: an install of any published release must be able to upgrade to this branch.
// Released migrations are listed in scripts/flyway-released-migrations.txt (see its header).
const assert = require('node:assert/strict');
const path = require('node:path');
const test = require('node:test');
const { readLedger, readMigrations, upgradeErrors } = require('./lib/flyway-ledger');

const released = readLedger(path.join(__dirname, 'flyway-released-migrations.txt'))
  .map(([tag, file, hash]) => ({ tag, path: file, hash }));

test('every published release can upgrade to this branch', () => {
  assert.ok(released.length > 40, 'must read the seeded ledger');
  assert.deepEqual(upgradeErrors(released, readMigrations()), []);
});

// Known cases, on a small made-up release so each rule is visible on its own.
const shipped = [
  { tag: 'r1', path: 'common/V1.0.56__a.sql', hash: 'h56' },
  { tag: 'r2', path: 'common/V1.0.61__probe.sql', hash: 'h61' },
];

test('refuses an unreleased migration below a released one (release ships 1.0.61, branch has 1.0.60)', () => {
  const errors = upgradeErrors(shipped, { 'common/V1.0.56__a.sql': 'h56', 'common/V1.0.61__probe.sql': 'h61', 'common/V1.0.60__x.sql': 'h60' });
  assert.equal(errors.length, 1);
  assert.match(errors[0], /common\/V1\.0\.60__x\.sql is not in any release, but r2 already shipped 1\.0\.61/);
});

test('refuses a released migration that was renamed, removed or edited', () => {
  assert.match(upgradeErrors(shipped, { 'common/V1.0.61__probe.sql': 'h61' })[0], /V1\.0\.56__a\.sql shipped in r1 but is missing or renamed/);
  assert.match(upgradeErrors(shipped, { 'common/V1.0.56__a.sql': 'changed', 'common/V1.0.61__probe.sql': 'h61' })[0], /content changed/);
});

test('judges Ontario and BC separately', () => {
  // A BC-only release at 1.0.70 does not hold back an Ontario-only migration at 1.0.65.
  const bcOnly = [{ tag: 'r3', path: 'bc/V1.0.70__b.sql', hash: 'h70' }];
  assert.deepEqual(upgradeErrors(bcOnly, { 'bc/V1.0.70__b.sql': 'h70', 'on/V1.0.65__o.sql': 'h65' }), []);
  assert.equal(upgradeErrors(bcOnly, { 'bc/V1.0.70__b.sql': 'h70', 'common/V1.0.65__c.sql': 'h65' }).length, 1);
});
