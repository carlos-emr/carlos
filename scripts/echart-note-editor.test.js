/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const CHECK_PATH = path.join(__dirname, 'echart-note-editor-playwright-checks.js');
const SOURCE = fs.readFileSync(CHECK_PATH, 'utf8');
/** The check with its comments removed, so prose about a pattern is not the pattern. */
const CODE = SOURCE.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '');
const check = require('./echart-note-editor-playwright-checks');

/*
 * The note-editor check writes to two tables that clinicians share. Three
 * properties of how it cleans up were found wrong in review and are pinned
 * here so they stay right: a failed delete must fail the check, the lock
 * delete must not reach another session's lock, and a draft the provider
 * already holds must not be typed over.
 */

/** A fake sql runner that records statements and can be told to fail one. */
function fakeSql(failing = /$^/) {
  const executed = [];
  return {
    executed,
    disposed: false,
    execute(statement) {
      executed.push(statement);
      if (failing.test(statement)) {
        throw new Error('the database query failed (mysql exit 1)');
      }
    },
    dispose() { this.disposed = true; },
  };
}

function seed(overrides = {}) {
  Object.assign(check.fixture, {
    sql: fakeSql(), stamp: 'PW_NOTE_EDITOR_1', providerNo: '999998', demographicNo: '2', preexistingLockIds: [],
  }, overrides);
}

test('cleanup is registered with runCheck, so a failed delete becomes a FAIL', () => {
  // process.exitCode set inside main()'s own finally was overwritten by
  // runCheck() recording PASS; only the cleanup hook's rejection survives.
  assert.match(CODE, /runCheck\(\{ name: 'echart-note-editor', run: main, cleanup \}\)/);
  assert.doesNotMatch(CODE, /process\.exitCode\s*=/, 'the check must not set the exit code itself');
});

test('a delete that fails is thrown, after every other statement was still attempted', async () => {
  seed({ sql: fakeSql(/casemgmt_tmpsave/) });
  const { sql } = check.fixture;
  await assert.rejects(check.cleanup(), /could not remove the stamped draft row/);
  assert.equal(sql.executed.length, 2, 'the lock delete must still run when the draft delete failed');
  assert.equal(sql.disposed, true, 'the option file must be removed whatever happened');
  assert.equal(check.fixture.sql, null, 'a second cleanup call has nothing to do');
});

test('both deletes are scoped to the test provider and the patient, and the draft to its stamp', async () => {
  seed();
  const { sql } = check.fixture;
  await check.cleanup();
  const [draft, lock] = sql.executed;
  assert.match(draft, /^DELETE FROM casemgmt_tmpsave WHERE provider_no = '999998' AND demographic_no = 2 AND note LIKE '%PW_NOTE_EDITOR_1%'$/);
  assert.match(lock, /^DELETE FROM casemgmt_note_lock WHERE provider_no = '999998' AND demographic_no = 2$/);
});

test('locks that existed before the chart was opened are never deleted', async () => {
  seed({ preexistingLockIds: ['41', '57'] });
  const { sql } = check.fixture;
  await check.cleanup();
  assert.match(sql.executed[1], /AND id NOT IN \(41, 57\)$/);
});

test('nothing is deleted when the run never learned whose rows it wrote', async () => {
  seed({ providerNo: '' });
  const { sql } = check.fixture;
  await check.cleanup();
  assert.deepEqual(sql.executed, []);
  assert.equal(sql.disposed, true);
});

test('a draft the provider already holds for the patient is refused before typing', () => {
  // The chart restores such a draft into the textarea, the autosave replaces
  // it with the stamped text, and the cleanup would then delete it.
  const before = SOURCE.indexOf('SELECT COUNT(*) FROM casemgmt_tmpsave WHERE provider_no');
  const typing = SOURCE.indexOf('pressSequentially');
  assert.ok(before > 0 && typing > before, 'the prior-draft query must come before the check types');
  assert.match(SOURCE.slice(before, typing), /throw new SkipCheck\(/);
});
