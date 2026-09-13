/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

/*
 * A helper called but never imported.
 *
 * `assertStrictPage(recorder, ['login'])` was added to surface-audit without
 * adding it to the destructuring above -- a ReferenceError on the first run of
 * all ten surface-audit checks, and invisible to every test here, because
 * require() succeeds: the reference sits inside a function body and is only
 * evaluated when that function is called. Nothing in this suite calls main(),
 * by design (it needs a browser and a database), so the whole class of "used
 * but not in scope" was undetectable.
 *
 * This closes it for the names that matter: everything the three shared
 * libraries export. Those are the helpers a check reaches for, and the ones a
 * careless edit drops from an import list while leaving the call behind.
 */

const LIB = path.join(__dirname, 'lib');

function exportedNames(file) {
  const source = fs.readFileSync(file, 'utf8');
  const match = source.match(/module\.exports\s*=\s*\{([\s\S]*?)\n\};/);
  assert.ok(match, `${path.basename(file)} must export an object literal`);
  return match[1]
    .split(',')
    .map((entry) => entry.trim().replace(/,$/, ''))
    .filter((entry) => entry && !entry.includes(':') && !entry.startsWith('//'));
}

const SHARED = [
  'playwright-harness.js',
  'playwright-ui.js',
  'playwright-link-audit.js',
  'playwright-surfaces.js',
].flatMap((name) => exportedNames(path.join(LIB, name)));

function checkScripts() {
  return fs.readdirSync(__dirname)
    .filter((name) => name.endsWith('.js') && !name.endsWith('.test.js'))
    .map((name) => ({ name, full: path.join(__dirname, name) }));
}

/**
 * Every name brought into scope, however it was brought in.
 *
 * Two spellings are live in this suite and both must count, or the test invents
 * failures: direct destructuring from require(), and destructuring from a
 * module object bound first (`const harness = require(...)` then
 * `const { assert } = harness`). eform-local-playwright-utils.js uses the
 * second; a first draft of this test reported four false positives against it.
 */
function namesInScope(source) {
  const names = new Set();
  for (const match of source.matchAll(/(?:const|let|var)\s*\{([^}]*)\}\s*=/g)) {
    for (const entry of match[1].split(',')) {
      const name = entry.trim().split(':').pop().trim().replace(/=.*$/, '').trim();
      if (name) {
        names.add(name);
      }
    }
  }
  for (const match of source.matchAll(/(?:function|const|let|var|class)\s+([A-Za-z_$][\w$]*)/g)) {
    names.add(match[1]);
  }
  return names;
}

/** Source with comments and string/template literals removed. */
function executableOnly(source) {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/\/\/[^\n]*/g, ' ')
    .replace(/`(?:\\.|[^`\\])*`/g, '``')
    .replace(/'(?:\\.|[^'\\])*'/g, "''")
    .replace(/"(?:\\.|[^"\\])*"/g, '""');
}

test('no check calls a shared helper it did not bring into scope', () => {
  const missing = [];
  for (const { name, full } of checkScripts()) {
    const source = fs.readFileSync(full, 'utf8');
    const scope = namesInScope(source);
    const body = executableOnly(source);
    for (const helper of SHARED) {
      if (scope.has(helper)) {
        continue;
      }
      // Called, and not as a property of something else (harness.assert is fine).
      const called = new RegExp(`(?<![\\w.$])${helper}\\s*\\(`);
      if (called.test(body)) {
        missing.push(`${name} calls ${helper}() without importing it`);
      }
    }
  }
  assert.deepEqual(missing, [],
    'require() succeeds with a missing import, so this only surfaces on a live run — which is too late');
});

test('the shared export list is actually being read', () => {
  // A test that checks nothing is worse than no test. If the export parsing
  // breaks, SHARED goes empty and the sweep above passes vacuously.
  assert.ok(SHARED.length > 30, `only ${SHARED.length} shared helpers parsed; the export scan is broken`);
  for (const expected of ['assertStrictPage', 'clickAndAwaitReload', 'auditCatalogue', 'surfaceByName']) {
    assert.ok(SHARED.includes(expected), `${expected} must be among the parsed exports`);
  }
});

test('the sweep detects a missing import when one exists', () => {
  // Proves the regex actually fires, rather than passing because it matches
  // nothing. This is the failure mode the suite has hit most often.
  const scope = namesInScope("const { assert } = require('./lib/playwright-harness');");
  assert.ok(scope.has('assert'));
  assert.ok(!scope.has('assertStrictPage'));
  const body = executableOnly('  assertStrictPage(recorder, [\'login\']);');
  assert.match(body, /(?<![\w.$])assertStrictPage\s*\(/);
});
