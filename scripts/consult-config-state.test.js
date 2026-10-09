/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

/*
 * scripts/lib/consult-config-state.js: the snapshot, switch-off and restore of the clinic-wide state the
 * consultation configuration actions change (the request/response properties, the Referring Doctor service and
 * the legacy specialistsJavascript script), behind consultation-services-admin and get-reject-consult-config.
 *
 * Both checks drive (or probe) Enable Request/Response, which writes two property rows and activates a service
 * on the clinic's behalf, and every service or consultant write regenerates the script, so a restore that
 * misses a row leaves every later check a different clinic. The database is a stub here; the live proof is the
 * runner's --residue-audit and the checks' own read-back (the restore asserts the script's MD5).
 */

const assert = require('node:assert/strict');
const test = require('node:test');

const state = require('./lib/consult-config-state');

const SCRIPT_HEX = Buffer.from('K(1,"A");\n', 'utf8').toString('hex').toUpperCase();
const SNAPSHOT = {
  properties: [['7', 'consultRequestEnabled', 'Y', '0'], ['8', 'consultResponseEnabled', null, '1'], ['9', 'consultResponseEnabled', 'NULL', '0']],
  services: [['40', '02', '0']],
  scripts: [['1', '1', SCRIPT_HEX, '0', 'aaaa']],
};

/**
 * A stub of the harness client. It answers the three snapshot reads from `snapshot` until the restore has
 * written, then from `after` (the state the restore left), and answers the script digest the same way; every
 * executed statement is recorded.
 */
function fakeSql({ snapshot = SNAPSHOT, after = {}, digestBefore } = {}) {
  let digestReads = 0;
  let restoring = true;
  const sql = {
    statements: [],
    view() { return { ...snapshot, ...(restoring ? {} : after) }; },
    rows(query) {
      const now = sql.view();
      if (/FROM property WHERE name IN/.test(query)) return now.properties.map((row) => [...row]);
      if (/SELECT serviceId,active,active IS NULL/.test(query)) return now.services.map((row) => [...row]);
      if (/IFNULL\(HEX\(javascriptString\)/.test(query)) {
        // The read-back that follows the restore sees what the restore left.
        restoring = false;
        return sql.view().scripts.map((row) => [...row]);
      }
      if (/javascriptString IS NULL,IFNULL\(MD5/.test(query)) {
        digestReads += 1;
        // The first digest is the one restoreScripts compares with the snapshot; the second is the read-back.
        if (digestReads === 1 && digestBefore) return digestBefore;
        restoring = false;
        return sql.view().scripts.map(([id, setId, , isNull, md5]) => [id, setId, isNull, md5]);
      }
      throw new Error(`unexpected rows() query: ${query}`);
    },
    execute(query) {
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

test('shouldPutTheScriptBackVerbatim_whenARunChangedIt', () => {
  const sql = fakeSql({ digestBefore: [['1', '1', '0', 'bbbb']] });
  state.restoreSwitch(sql, SNAPSHOT);
  const writes = sql.statements.filter((statement) => /UPDATE specialistsJavascript/.test(statement));
  assert.equal(writes.length, 2, 'it empties the row, then appends the one piece of the script');
  assert.match(writes[0], /SET javascriptString='' WHERE id=1/);
  assert.match(writes[1], new RegExp(`CONCAT\\(javascriptString,CONVERT\\(UNHEX\\('${SCRIPT_HEX}'\\) USING utf8mb4\\)\\)\\s+WHERE id=1`));
});

test('shouldLeaveTheScriptAlone_whenItsMd5IsUnchanged', () => {
  const sql = fakeSql();
  state.restoreSwitch(sql, SNAPSHOT);
  assert.equal(sql.statements.filter((statement) => /UPDATE specialistsJavascript/.test(statement)).length, 0);
});

test('shouldPutBackANullScript_asNull', () => {
  const snapshot = { ...SNAPSHOT, scripts: [['1', '1', '', '1', '']] };
  const sql = fakeSql({ snapshot, digestBefore: [['1', '1', '0', 'bbbb']] });
  state.restoreSwitch(sql, snapshot);
  assert.ok(sql.statements.some((statement) => /SET javascriptString=NULL WHERE id=1/.test(statement)));
});

test('shouldFailTheRestore_whenTheSwitchDiffersFromItsSnapshot', () => {
  const sql = fakeSql({ after: { properties: [['7', 'consultRequestEnabled', 'N', '0']] } });
  assert.throws(() => state.restoreSwitch(sql, SNAPSHOT), /was not restored to its snapshot/);
});

test('shouldFailTheRestore_whenTheScriptDiffersFromItsSnapshotAfterwards', () => {
  const sql = fakeSql({ digestBefore: [['1', '1', '0', 'bbbb']], after: { scripts: [['1', '1', SCRIPT_HEX, '0', 'cccc']] } });
  assert.throws(() => state.restoreSwitch(sql, SNAPSHOT), /specialistsJavascript\) was not restored to its snapshot/);
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
  assert.doesNotMatch(text, /specialistsJavascript/, 'a probe leaves the script to the final restore');
});

test('shouldSplitAScriptOnlyBetweenCharacters_whenItExceedsOneStatement', () => {
  // "é" is two bytes and "€" three: every piece must decode on its own and the pieces must rejoin to the text.
  const text = `K(1,"${'é€'.repeat(40)}");\n`;
  const hex = Buffer.from(text, 'utf8').toString('hex');
  for (const limit of [4, 5, 7, 11, 64]) {
    const pieces = state.utf8Chunks(hex, limit);
    assert.ok(pieces.length > 1);
    for (const piece of pieces) {
      assert.ok(Buffer.from(piece, 'hex').length <= limit);
      assert.ok(!Buffer.from(piece, 'hex').toString('utf8').includes('�'), `limit ${limit}: a piece ends inside a character`);
    }
    assert.equal(Buffer.concat(pieces.map((piece) => Buffer.from(piece, 'hex'))).toString('utf8'), text);
  }
  assert.deepEqual(state.utf8Chunks('', 10), []);
  assert.throws(() => state.utf8Chunks('4', 10), /whole bytes/);
  assert.throws(() => state.utf8Chunks('4B', 3), /four-byte character/);
});
