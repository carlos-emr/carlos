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
  // COMMENTS COME OUT FIRST, BEFORE the split on commas. A comment line inside
  // the block carries no trailing comma of its own, so splitting first glued it
  // to the export BELOW it -- and the old `startsWith('//')` filter then dropped
  // that whole entry, silently taking a real export with it. Found by writing a
  // fixture for the aliased-export case below and watching a plain export
  // disappear next to a comment.
  const entries = match[1]
    .replace(/\/\/[^\n]*/g, '')
    .split(',')
    .map((entry) => entry.trim())
    // AN ALIASED EXPORT IS STILL AN EXPORT. `{ reload: clickAndAwaitReload }`
    // publishes the name on the LEFT, which is what a check imports -- but the
    // parser used to drop every entry containing a colon, so that name was
    // never added to SHARED and never guarded. The sweep then reported success
    // over a helper it had not looked at: the permissive direction, and the
    // same shape as the comment below warns about. No lib file aliases an
    // export today, which is why nothing was missing; it is one rename away.
    .map((entry) => (entry.includes(':') ? entry.slice(0, entry.indexOf(':')).trim() : entry))
    .filter(Boolean);
  // Every surviving entry becomes part of a RegExp below. A non-identifier --
  // a spread like `...harness` (which eform-local-playwright-utils.js really
  // does use, and a lib file could adopt), or a quoted key, or anything
  // carrying regex metacharacters -- would build a pattern that matches the
  // wrong thing or throws. Silently dropping it would be worse than either: the
  // name would stop being guarded and the sweep would still report success.
  // Fail loudly. (This assertion now actually governs every entry; the colon
  // filter above used to route aliased ones around it.)
  for (const entry of entries) {
    assert.match(entry, /^[A-Za-z_$][\w$]*$/,
      `${path.basename(file)} exports "${entry}", which is not a plain identifier; `
      + 'the import sweep builds a RegExp per export and cannot guard this one');
  }
  return entries;
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

test('an export that is not a plain identifier fails the parse instead of weakening the sweep', () => {
  // Every export becomes part of a RegExp. A spread entry -- `...harness`, the
  // shape eform-local-playwright-utils.js already uses -- would build
  // `(?<![\w.$])...harness\s*\(`, where the three dots are wildcards: a pattern
  // that quietly matches the wrong thing while the sweep still reports success.
  const identifier = /^[A-Za-z_$][\w$]*$/;
  assert.ok(identifier.test('assertStrictPage'));
  assert.ok(identifier.test('_private'));
  assert.ok(identifier.test('$dollar'));
  assert.ok(!identifier.test('...harness'), 'a spread must not pass as an export name');
  assert.ok(!identifier.test('a.b'));
  assert.ok(!identifier.test('a('));
  assert.ok(!identifier.test(''));
  // And the parser really applies it, rather than the test asserting on a
  // regex it defined itself.
  const source = fs.readFileSync(path.join(__dirname, 'check-script-imports.test.js'), 'utf8');
  const parser = source.slice(source.indexOf('function exportedNames'), source.indexOf('const SHARED'));
  assert.match(parser, /assert\.match\(entry,/);
});

test('an aliased export is guarded under the name it publishes', () => {
  // `{ reload: clickAndAwaitReload }` publishes the name on the LEFT, which is
  // what a check imports. The parser used to drop every entry containing a
  // colon, so that name never reached SHARED and was never swept -- the
  // permissive direction: the guard covers less and still reports success.
  //
  // Nothing aliases an export in lib/ today, so nothing was actually missing.
  // It was one rename away, and the comment in exportedNames() claimed the
  // parser fails loudly on anything it cannot guard -- which the colon filter
  // quietly contradicted.
  //
  // Driven through the REAL exportedNames() against a fixture, not a
  // reimplementation of it: a copy of the parser in the test would pass while
  // the parser the sweep uses stayed broken.
  const os = require('node:os');
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-exports-'));
  const fixture = path.join(dir, 'fixture.js');
  try {
    fs.writeFileSync(fixture, [
      'module.exports = {',
      '  assert,',
      '  // a comment line',
      '  reload: clickAndAwaitReload,',
      '  runCheck,',
      '};',
      '',
    ].join('\n'));
    assert.deepEqual(exportedNames(fixture), ['assert', 'reload', 'runCheck'],
      'the published name is the key, not the local one, and a comment is not an export');
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('a comment beside an export does not take the export with it', () => {
  // Found while writing the fixture above. A comment line inside module.exports
  // carries no trailing comma, so splitting on commas first glued it to the
  // export BELOW it and the comment filter dropped both -- a real helper
  // silently leaving the swept set, which is the same permissive failure as the
  // aliased-export case and was not what the filter was for.
  const os = require('node:os');
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-exports-'));
  const fixture = path.join(dir, 'fixture.js');
  try {
    fs.writeFileSync(fixture, [
      'module.exports = {',
      '  assert,',
      '  // why runCheck is last',
      '  runCheck,',
      '};',
      '',
    ].join('\n'));
    assert.deepEqual(exportedNames(fixture), ['assert', 'runCheck'],
      'the export under a comment must still be swept');
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('an export the sweep cannot guard fails the parse rather than being dropped', () => {
  // The other half: a spread or a quoted key cannot become a RegExp, and
  // silently skipping it would leave that helper unswept while the run stayed
  // green. Also driven through the real parser.
  const os = require('node:os');
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-exports-'));
  const fixture = path.join(dir, 'fixture.js');
  try {
    fs.writeFileSync(fixture, 'module.exports = {\n  ...harness,\n  runCheck,\n};\n');
    assert.throws(() => exportedNames(fixture), /not a plain identifier/);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('every shared export the sweep uses is a plain identifier', () => {
  for (const name of SHARED) {
    assert.match(name, /^[A-Za-z_$][\w$]*$/);
  }
  assert.ok(SHARED.includes('pathOnly'), 'a recently added export must be in the swept set');
  assert.ok(SHARED.includes('withoutQueryStrings'));
});
