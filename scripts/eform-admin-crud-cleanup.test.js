/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

/*
 * Issue #4408: eform-admin-crud has no database dependency of its own, so its
 * fixture cleanup is optional SQL keyed on the run's unique form name. These
 * source audits pin the invariants that make that cleanup safe: it touches only
 * the row this run created, a failing run keeps its form for diagnosis, and a
 * missing MYSQL_PASSWORD degrades to "no cleanup" rather than a hard failure.
 */
const source = fs.readFileSync(path.join(__dirname, 'eform-admin-crud-playwright-checks.js'), 'utf8');

test('cleanup deletes by the exact stamped form name, never by pattern', () => {
  const statements = [...source.matchAll(/sql\.execute\(`([^`]*)`\)/g)].map((m) => m[1]);
  assert.equal(statements.length, 1, 'exactly one fixture statement is expected');
  assert.equal(statements[0], 'DELETE FROM eform WHERE form_name = ${sqlString(formName)}');
  assert.doesNotMatch(statements[0], /\bLIKE\b/i, 'a pattern could remove a shared library form');
});

test('the probe name carries the per-run stamp', () => {
  assert.match(source, /const formName = `Playwright Admin CRUD \$\{stamp\}`/);
  assert.match(source, /const stamp = Date\.now\(\)/);
});

test('a passing run purges the form but a failing run leaves it for diagnosis', () => {
  assert.match(source, /if \(passed\) cleanupProbeForm\(\)/);
  const markPassed = source.indexOf('passed = true;');
  assert.ok(markPassed > source.indexOf('assertNoPageErrors(recorder)'), 'passed is set only after every assertion');
  assert.ok(markPassed < source.indexOf('console.log(\n      `PASS'), 'passed is set before the PASS line');
});

test('database access is optional and the signal handler is disposed last', () => {
  assert.match(source, /process\.env\.MYSQL_PASSWORD\s*\?\s*createSqlRunner/);
  assert.match(source, /if \(!sql\) \{[\s\S]*?return;/);
  const finallyBlock = source.slice(source.indexOf('await browser.close()'));
  assert.ok(finallyBlock.indexOf('browser.close()') < finallyBlock.indexOf('cleanupProbeForm()'));
  assert.ok(finallyBlock.indexOf('cleanupProbeForm()') < finallyBlock.indexOf('signalHandlers.dispose()'));
});

test('the stale "is gone by the end" claim is gone from the header', () => {
  assert.doesNotMatch(source, /probe form is gone by the end/);
});
