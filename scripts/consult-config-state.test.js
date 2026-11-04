/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

/*
 * scripts/lib/consult-config-state.js: the snapshot, switch-off and restore of the clinic-wide state the
 * consultation configuration actions change (the request/response properties and the Referring Doctor service),
 * and the check that they leave the legacy specialistsJavascript script alone, behind consultation-services-admin
 * and get-reject-consult-config.
 *
 * Both checks drive (or probe) Enable Request/Response, which writes two property rows and activates a service on
 * the clinic's behalf, so a restore that misses a row leaves every later check a different clinic. The service and
 * consultant writes used to regenerate the script for no reader (finding 260, #4617); the restore now asserts its
 * digest is unchanged and never writes it. The database is a stub here; the live proof is the runner's
 * --residue-audit and the checks' own read-back.
 */

const assert = require('node:assert/strict');
const test = require('node:test');

const state = require('./lib/consult-config-state');

const SNAPSHOT = {
  properties: [['7', 'consultRequestEnabled', 'Y', '0'], ['8', 'consultResponseEnabled', null, '1'], ['9', 'consultResponseEnabled', 'NULL', '0']],
  services: [['40', '02', '0']],
  scripts: [['1', '1', '0', 'aaaa']],
};

/**
 * A stub of the harness client. It answers the snapshot reads from `snapshot` until the restore has written, then
 * from `after` (the state the restore left, merged over the snapshot); every executed statement is recorded.
 */
function fakeSql({ snapshot = SNAPSHOT, after = {} } = {}) {
  let written = false;
  const sql = {
    statements: [],
    queries: [],
    view() { return { ...snapshot, ...(written ? after : {}) }; },
    rows(query) {
      sql.queries.push(query);
      const now = sql.view();
      if (/FROM property WHERE name IN/.test(query)) return now.properties.map((row) => [...row]);
      if (/SELECT serviceId,active,active IS NULL/.test(query)) return now.services.map((row) => [...row]);
      if (/javascriptString IS NULL,IFNULL\(MD5\(javascriptString\)/.test(query)) return now.scripts.map((row) => [...row]);
      throw new Error(`unexpected rows() query: ${query}`);
    },
    execute(query) {
      written = true;
      sql.statements.push(query);
    },
  };
  return sql;
}

test('shouldRestoreEverySnapshottedRow_andDeleteOnlyTheRowsTheRunCreated', () => {
  const sql = fakeSql();
  state.restoreSwitch(sql, SNAPSHOT);
  const text = sql.statements.join('\n');
  assert.match(text, /DELETE FROM property WHERE name IN \('consultRequestEnabled','consultResponseEnabled'\) AND id NOT IN \(7,8,9\)/);
  assert.match(text, /UPDATE property SET value='Y' WHERE id=7/);
  // SQL NULL and the text 'NULL' print alike in `mysql -B`; only the IS NULL flag tells them apart.
  assert.match(text, /UPDATE property SET value=NULL WHERE id=8/);
  assert.match(text, /UPDATE property SET value='NULL' WHERE id=9/);
  assert.match(text, /DELETE FROM consultationServices WHERE serviceDesc='Referring Doctor' AND serviceId NOT IN \(40\)/);
  assert.match(text, /UPDATE consultationServices SET active='02' WHERE serviceId=40/);
});

test('shouldDeleteEveryRow_whenTheSnapshotHeldNone', () => {
  const empty = { properties: [], services: [], scripts: [] };
  const sql = fakeSql({ snapshot: empty });
  state.restoreSwitch(sql, empty);
  const text = sql.statements.join('\n');
  assert.match(text, /DELETE FROM property WHERE name IN \('consultRequestEnabled','consultResponseEnabled'\)\s*$/m);
  assert.doesNotMatch(text, /NOT IN \(\)/);
  assert.match(text, /DELETE FROM consultationServices WHERE serviceDesc='Referring Doctor'\s*$/m);
});

test('shouldSnapshotTheScriptAsADigest_withoutReadingItsText', () => {
  const sql = fakeSql();
  const snapshot = state.snapshotSwitch(sql);
  assert.deepEqual(snapshot, SNAPSHOT);
  // MariaDB computes the MD5; the script's text never leaves the database.
  assert.ok(sql.queries.every((query) => !/HEX\(/.test(query)));
  assert.equal(sql.statements.length, 0, 'a snapshot writes nothing');
});

test('shouldNeverWriteTheScript_whenTheRunLeftItAlone', () => {
  const sql = fakeSql();
  state.restoreSwitch(sql, SNAPSHOT);
  assert.ok(sql.statements.length > 0, 'the switch rows are still restored');
  assert.doesNotMatch(sql.statements.join('\n'), /specialistsJavascript/);
});

test('shouldFailTheRestore_whenTheSwitchDiffersFromItsSnapshot', () => {
  const sql = fakeSql({ after: { properties: [['7', 'consultRequestEnabled', 'N', '0']] } });
  assert.throws(() => state.restoreSwitch(sql, SNAPSHOT), /was not restored to its snapshot/);
});

test('shouldFailTheRestore_afterRestoringTheSwitch_whenAnActionRewroteTheScript', () => {
  const sql = fakeSql({ after: { scripts: [['1', '1', '0', 'bbbb']] } });
  assert.throws(() => state.restoreSwitch(sql, SNAPSHOT), /rewrote the legacy specialistsJavascript script \(finding 260, #4617\)/);
  const text = sql.statements.join('\n');
  // The clinic-wide switch is put back before the script is judged, and the script itself is never written.
  assert.match(text, /UPDATE property SET value='Y' WHERE id=7/);
  assert.match(text, /UPDATE consultationServices SET active='02' WHERE serviceId=40/);
  assert.doesNotMatch(text, /specialistsJavascript/);
});

test('shouldFailTheRestore_whenTheScriptRowWasAddedOrRemoved', () => {
  const added = fakeSql({ after: { scripts: [['1', '1', '0', 'aaaa'], ['2', '1', '1', '']] } });
  assert.throws(() => state.restoreSwitch(added, SNAPSHOT), /rewrote the legacy specialistsJavascript script/);
  const removed = fakeSql({ after: { scripts: [] } });
  assert.throws(() => state.restoreSwitch(removed, SNAPSHOT), /rewrote the legacy specialistsJavascript script/);
});

test('shouldSwitchOff_withoutCreatingOrLosingARow', () => {
  const sql = fakeSql();
  state.switchOff(sql, SNAPSHOT);
  const text = sql.statements.join('\n');
  assert.match(text, /DELETE FROM property WHERE name IN .* AND id NOT IN \(7,8,9\)/);
  assert.match(text, /UPDATE property SET value=NULL WHERE name IN/);
  assert.match(text, /DELETE FROM consultationServices WHERE serviceDesc='Referring Doctor'\s+AND serviceId NOT IN \(40\)/);
  assert.match(text, /UPDATE consultationServices SET active='02' WHERE serviceDesc='Referring Doctor'/);
  assert.doesNotMatch(text, /INSERT/);
  assert.doesNotMatch(text, /specialistsJavascript/, 'a probe never touches the legacy script');
});
