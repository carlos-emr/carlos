/* Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
// Proposal: databases built from this branch must be able to take every later migration.
// Merge order is kept in scripts/flyway-merge-order.txt (see its header).
const assert = require('node:assert/strict');
const path = require('node:path');
const test = require('node:test');
const { orderErrors, readLedger, readMigrations } = require('./lib/flyway-ledger');

const order = readLedger(path.join(__dirname, 'flyway-merge-order.txt'))
  .map(([file, mark]) => ({ path: file, forwardMerge: mark === 'forward-merge' }));

test('migrations are listed in merge order, each above the ones before it', () => {
  // release/2026.08 seeds 48 migrations (develop seeds 61), so the sanity bound is below both.
  assert.ok(order.length > 40, 'must read the seeded ledger');
  assert.deepEqual(orderErrors(order, readMigrations()), []);
});

// Known cases, replayed on a short made-up history.
const files = (...paths) => Object.fromEntries(paths.map((p) => [p, 'h']));
const listed = (...paths) => paths.map((p) => ({ path: p, forwardMerge: p.endsWith(' forward-merge') }))
  .map((e) => ({ ...e, path: e.path.replace(' forward-merge', '') }));

test('refuses 1.0.60 merged after 1.0.78 (the #4134 case)', () => {
  const errors = orderErrors(listed('common/V1.0.66__a.sql', 'common/V1.0.78__b.sql', 'common/V1.0.60__c.sql'),
    files('common/V1.0.66__a.sql', 'common/V1.0.78__b.sql', 'common/V1.0.60__c.sql'));
  assert.equal(errors.length, 1);
  assert.match(errors[0], /V1\.0\.60__c\.sql was added after common\/V1\.0\.78__b\.sql/);
});

test('refuses a stale 1.0.59 next to its own renumbered copy at 1.0.78 (the #4481 and #4069 case)', () => {
  const errors = orderErrors(listed('common/V1.0.78__add_sms_config.sql', 'common/V1.0.59__add_sms_config.sql'),
    files('common/V1.0.78__add_sms_config.sql', 'common/V1.0.59__add_sms_config.sql'));
  assert.equal(errors.length, 1);
});

test('refuses a migration that is on disk but not listed', () => {
  assert.match(orderErrors(listed('common/V1.0.78__b.sql'), files('common/V1.0.78__b.sql', 'common/V1.0.79__d.sql'))[0],
    /V1\.0\.79__d\.sql is not listed; append it/);
});

test('allows a released migration below the top when marked forward-merge', () => {
  assert.deepEqual(orderErrors(listed('common/V1.0.78__b.sql', 'common/V1.0.57__r.sql forward-merge'),
    files('common/V1.0.78__b.sql', 'common/V1.0.57__r.sql')), []);
});
