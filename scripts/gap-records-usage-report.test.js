/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
/*
 * scripts/gap-records-usage-report-playwright-checks.js picks an idle demo provider with a query of its
 * own. That query named the Ontario bill header table, which a BC schema does not have, so on BC the
 * check failed on its own SQL before it opened the report. These cases pin the query for both schemas
 * without a database.
 */
const assert = require('node:assert/strict');
const test = require('node:test');
const { idleProviderNo } = require('./gap-records-usage-report-playwright-checks');
const { SkipCheck } = require('./lib/playwright-harness');

/** A stand-in SQL runner for a schema that has, or lacks, the Ontario bill header table. */
function schema({ ontarioBills, idle = [['7']] }) {
  const asked = [];
  return {
    asked,
    value(query) {
      asked.push(query);
      assert.match(query, /FROM information_schema\.TABLES WHERE TABLE_SCHEMA=DATABASE\(\) AND TABLE_NAME='billing_on_cheader1'$/);
      return ontarioBills ? '1' : '0';
    },
    rows(query) {
      asked.push(query);
      // What MariaDB does with a table the schema does not have.
      assert.ok(ontarioBills || !/billing_on_cheader1/.test(query), "Table 'billing_on_cheader1' doesn't exist");
      return idle;
    },
  };
}

test('an Ontario schema still requires the idle provider to have no invoices', () => {
  const sql = schema({ ontarioBills: true });
  assert.equal(idleProviderNo(sql), '7');
  assert.match(sql.asked[1], /AND NOT EXISTS \(SELECT 1 FROM billing_on_cheader1 b WHERE b\.provider_no=p\.provider_no\)/);
});

test('a schema without the Ontario bill header is not asked about it', () => {
  const sql = schema({ ontarioBills: false });
  assert.equal(idleProviderNo(sql), '7');
  assert.doesNotMatch(sql.asked[1], /billing_on_cheader1/);
  // Every other idleness condition is unchanged.
  for (const table of ['demographic', 'appointment', 'casemgmt_note', 'drugs', 'providerLabRouting']) {
    assert.match(sql.asked[1], new RegExp(`AND NOT EXISTS \\(SELECT 1 FROM ${table} \\w WHERE \\w\\.provider_no=p\\.provider_no\\)`));
  }
});

test('no idle provider is a skip on either schema, not a failure', () => {
  for (const ontarioBills of [true, false]) {
    assert.throws(() => idleProviderNo(schema({ ontarioBills, idle: [] })),
      error => error instanceof SkipCheck && /no idle demo provider/.test(error.message));
  }
});
