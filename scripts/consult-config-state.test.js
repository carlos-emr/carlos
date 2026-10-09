/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

/*
 * scripts/lib/consult-config-state.js: the snapshot, switch-off and restore of the clinic-wide state the
 * consultation configuration actions change (the request/response properties, the Referring Doctor service and
 * the legacy specialistsJavascript script), behind consultation-services-admin and get-reject-consult-config.
 *
 * Both checks drive (or probe) Enable Request/Response, which writes two property rows and activates a service
 * on the clinic's behalf, so a restore that misses a row leaves every later check a different clinic. The
 * database is a stub here; the live proof is the runner's --residue-audit and the checks' own read-back.
 */

const assert = require('node:assert/strict');
const test = require('node:test');

const state = require('./lib/consult-config-state');

/**
 * A stub of the harness client. `snapshots` are the answers to successive snapshot reads (property rows, then
 * Referring Doctor rows); `created` is what the "rows this run created" query returns; every executed
 * statement is recorded.
 */
function fakeSql({ properties, services, created = [], propertiesAfter, servicesAfter, scriptLines = '0' }) {
  const sql = {
    statements: [],
    restored: false,
    rows(query) {
      if (/FROM property WHERE name IN/.test(query)) return (sql.restored ? propertiesAfter || properties : properties).map((row) => [...row]);
      if (/SELECT serviceId,active,active IS NULL/.test(query)) return (sql.restored ? servicesAfter || services : services).map((row) => [...row]);
      if (/SELECT serviceId FROM consultationServices/.test(query)) return created.map((id) => [id]);
      throw new Error(`unexpected rows() query: ${query}`);
    },
    value(query) {
      assert.match(query, /FROM specialistsJavascript/);
      return scriptLines;
    },
    execute(query) {
      sql.statements.push(query);
      // The last restore statement is the one that puts the Referring Doctor rows back.
      if (/UPDATE consultationServices SET active=.* WHERE serviceId=/.test(query) || /^DELETE FROM consultationServices/.test(query.trim())) sql.restored = true;
    },
  };
  return sql;
}

const SNAPSHOT = {
  properties: [['7', 'consultRequestEnabled', 'Y', '0'], ['8', 'consultResponseEnabled', null, '1'], ['9', 'consultResponseEnabled', 'NULL', '0']],
  services: [['40', '02', '0']],
};

test('shouldDropOnlyTheBlockOfTheNamedService_fromTheLegacyScript', () => {
  const script = [
    'function makeSpecialistslist(dec){', 'K(1,"Adolescent Medicine");', 'D(1,"1","555","A, B","555","Toronto");', '',
    'K(5,"FAKE-PW0123456789abcdef-Svc");', 'D(5,"9","555","FAKE-PW0123456789abcdef, Ann","555","1 Way");', '',
    'K(15,"Another");', 'D(15,"2","555","C, D","555","Kitchener");', '', '}', '',
  ].join('\n');
  // The pattern is PCRE (MariaDB REGEXP_REPLACE); (?m) is the multiline flag JavaScript spells `m`.
  const pattern = state.scriptBlockPattern('5');
  assert.ok(pattern.startsWith('(?m)'));
  const trimmed = script.replace(new RegExp(pattern.slice(4), 'gm'), '');
  assert.ok(!trimmed.includes('K(5,') && !trimmed.includes('D(5,'), 'the whole block of service 5 goes');
  assert.ok(trimmed.includes('K(1,') && trimmed.includes('K(15,') && trimmed.includes('D(15,'), 'services 1 and 15 stay');
  assert.equal(trimmed, script.replace(/K\(5,[\s\S]*?\n\n/, ''), 'the block ends with its blank line, so the script is as if the service never existed');
  assert.throws(() => state.scriptBlockPattern('5; DROP'), /numeric service id/);
});

test('shouldRestoreEverySnapshottedRow_andDeleteOnlyTheRowsTheRunCreated', () => {
  const sql = fakeSql({ ...SNAPSHOT, created: ['41'] });
  state.restoreSwitch(sql, SNAPSHOT);
  const text = sql.statements.join('\n');
  assert.match(text, /DELETE FROM property WHERE name IN \('consultRequestEnabled','consultResponseEnabled'\) AND id NOT IN \(7,8,9\)/);
  assert.match(text, /UPDATE property SET value='Y' WHERE id=7/);
  // SQL NULL and the text 'NULL' print alike in `mysql -B`; only the IS NULL flag tells them apart.
  assert.match(text, /UPDATE property SET value=NULL WHERE id=8/);
  assert.match(text, /UPDATE property SET value='NULL' WHERE id=9/);
  assert.match(text, /DELETE FROM consultationServices WHERE serviceDesc='Referring Doctor' AND serviceId NOT IN \(40\)/);
  assert.match(text, /UPDATE consultationServices SET active='02' WHERE serviceId=40/);
  // The service the run created is dropped from the legacy script before its row goes.
  assert.ok(text.indexOf("LIKE '%K(41,%'") !== -1 && text.indexOf("LIKE '%K(41,%'") < text.indexOf('DELETE FROM consultationServices'));
});

test('shouldDeleteEveryRow_whenTheSnapshotHeldNone', () => {
  const empty = { properties: [], services: [] };
  const sql = fakeSql({ ...empty, created: [] });
  state.restoreSwitch(sql, empty);
  const text = sql.statements.join('\n');
  assert.match(text, /DELETE FROM property WHERE name IN \('consultRequestEnabled','consultResponseEnabled'\)\s*$/m);
  assert.doesNotMatch(text, /NOT IN \(\)/);
  assert.match(text, /DELETE FROM consultationServices WHERE serviceDesc='Referring Doctor'\s*$/m);
});

test('shouldFailTheRestore_whenTheSwitchDiffersFromItsSnapshot', () => {
  const sql = fakeSql({ ...SNAPSHOT, propertiesAfter: [['7', 'consultRequestEnabled', 'N', '0']] });
  assert.throws(() => state.restoreSwitch(sql, SNAPSHOT), /was not restored to its snapshot/);
});

test('shouldFailTheRestore_whenTheLegacyScriptStillNamesACreatedService', () => {
  const sql = fakeSql({ ...SNAPSHOT, created: ['41'], scriptLines: '2' });
  assert.throws(() => state.restoreSwitch(sql, SNAPSHOT), /still names a Referring Doctor service the run created/);
});

test('shouldSwitchOff_withoutCreatingOrLosingARow', () => {
  const sql = fakeSql({ ...SNAPSHOT, created: [] });
  state.switchOff(sql, SNAPSHOT);
  const text = sql.statements.join('\n');
  assert.match(text, /DELETE FROM property WHERE name IN .* AND id NOT IN \(7,8,9\)/);
  assert.match(text, /UPDATE property SET value=NULL WHERE name IN/);
  assert.match(text, /UPDATE consultationServices SET active='02' WHERE serviceDesc='Referring Doctor'/);
  assert.doesNotMatch(text, /INSERT/);
});

test('shouldRefuseScriptEdits_whenAnIdIsNotNumeric', () => {
  const sql = fakeSql({ ...SNAPSHOT });
  assert.throws(() => state.removeScriptBlocks(sql, ["1' OR '1'='1"]), /numeric service ids/);
  assert.throws(() => state.scriptLinesFor(sql, ['x']), /numeric service ids/);
  assert.equal(state.scriptLinesFor(sql, []), 0);
});
