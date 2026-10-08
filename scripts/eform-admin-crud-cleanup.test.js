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

test('a passing run purges the form before PASS, so a failed cleanup fails the run', () => {
  const purge = source.lastIndexOf('    cleanupProbeForm();\n');
  assert.ok(purge > source.indexOf('assertNoPageErrors(recorder)'), 'purge only after every assertion');
  assert.ok(purge < source.indexOf('console.log(\n      `PASS'), 'purge runs before the PASS line');
  const catchBlock = source.slice(source.indexOf('} catch (error) {', purge));
  assert.match(catchBlock, /process\.exitCode = 1/);
  assert.doesNotMatch(source, /if \(passed\)/, 'no swallowed post-PASS cleanup');
});

test('a browser launch failure still disposes the SQL runner', () => {
  const tryIndex = source.indexOf('  let browser;\n  try {');
  assert.ok(tryIndex > 0, 'browser is declared outside, launched inside, the try');
  assert.ok(source.indexOf('browser = await chromium.launch', tryIndex) > tryIndex);
});

test('database access is optional and the signal handler is disposed last', () => {
  assert.match(source, /process\.env\.MYSQL_PASSWORD\s*\?\s*createSqlRunner/);
  assert.match(source, /if \(!sql\) \{[\s\S]*?return;/);
  const finallyBlock = source.slice(source.indexOf('if (browser) await browser.close()'));
  assert.ok(finallyBlock.indexOf('browser.close()') < finallyBlock.indexOf('sql.dispose()'));
  assert.ok(finallyBlock.indexOf('sql.dispose()') < finallyBlock.indexOf('signalHandlers.dispose()'));
});

test('the stale "is gone by the end" claim is gone from the header', () => {
  assert.doesNotMatch(source, /probe form is gone by the end/);
});
