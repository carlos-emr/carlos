/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

/*
 * scripts/lib/mcedt-local-state.js: the way the MCEDT mailbox check puts the clinic's MCEDT password
 * property and the EDT outbox directory back.
 *
 * The password is a credential for a provincial claims service, so the properties of the helper are
 * that it never moves the value (it renames the rows, nothing else) and that the restore is proved by
 * a digest, not by reading the value. The database is a stub here; the live proof is the runner's
 * --residue-audit with the check's `mutates` entry, and the outbox listing before and after.
 */

const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const test = require('node:test');

const {
  PASSWORD_PROPERTY, createOutboxLedger, createPropertyParking, diffListings, listDirectory,
} = require('./lib/mcedt-local-state');

const MARKER = 'FAKE-PW0123456789abcdef';
const SECRET = 'clinic-real-password-do-not-print';

/** The `property` table as a list of rows, answering exactly the statements the helper sends. */
function fakeSql(initial) {
  const state = { rows: initial.map((row) => ({ ...row })), log: [], nextId: 100 };
  const named = (name) => state.rows.filter((row) => row.name === name);
  const digestOf = (rows) => crypto.createHash('sha256').update(rows.slice().sort((a, b) => a.id - b.id)
    .map((row) => `${row.id}|${row.value === null ? 'N' : Buffer.from(row.value).toString('hex')}|${row.provider_no === null ? 'N' : row.provider_no}`).join(';')).digest('hex');
  function statement(text) {
    const update = /^UPDATE `property` SET `name`='([^']+)' WHERE `name`='([^']+)'$/.exec(text);
    if (update) { for (const row of named(update[2])) row.name = update[1]; return; }
    const del = /^DELETE FROM `property` WHERE `name`='([^']+)'$/.exec(text);
    if (del) { state.rows = state.rows.filter((row) => row.name !== del[1]); return; }
    if (text === 'START TRANSACTION' || text === 'COMMIT') return;
    throw new Error(`unexpected statement: ${text}`);
  }
  return {
    state,
    /** The run's own Change Password: creates (or updates) a row under `name`. */
    write(name, value) {
      const existing = named(name)[0];
      if (existing) existing.value = value;
      else state.rows.push({ id: state.nextId++, name, value, provider_no: null });
    },
    value(query) {
      state.log.push(query);
      const like = /WHERE `name` LIKE '([^']*)%'$/.exec(query);
      if (like) return String(state.rows.filter((row) => row.name.startsWith(like[1].replace(/\\(.)/g, '$1'))).length);
      const count = /^SELECT COUNT\(\*\) FROM `property` WHERE `name`='([^']+)'$/.exec(query);
      if (count) return String(named(count[1]).length);
      const digest = /SHA2\([\s\S]*FROM `property` WHERE `name`='([^']+)'$/.exec(query);
      if (digest) return digestOf(named(digest[1]));
      throw new Error(`unexpected query: ${query}`);
    },
    execute(query) {
      state.log.push(query);
      for (const part of query.split(';').map((text) => text.trim()).filter(Boolean)) statement(part);
    },
  };
}

const original = () => ({ id: 7, name: PASSWORD_PROPERTY, value: SECRET, provider_no: null });

test('shouldMoveTheClinicsRowAsideAndBack_whenTheRunWritesItsOwnPassword', () => {
  const sql = fakeSql([original(), { id: 8, name: 'other', value: 'x', provider_no: '1' }]);
  const parking = createPropertyParking({ sql, name: PASSWORD_PROPERTY, marker: MARKER });
  const digest = parking.digest();
  parking.park();
  assert.equal(sql.state.rows.filter((row) => row.name === PASSWORD_PROPERTY).length, 0, 'the clinic row is parked');
  assert.equal(sql.state.rows.find((row) => row.id === 7).name, parking.parkedName);
  sql.write(PASSWORD_PROPERTY, 'FAKE-PW-run-password');
  sql.write(PASSWORD_PROPERTY, 'FAKE-PW-second-password');
  parking.restore();
  assert.deepEqual(sql.state.rows.filter((row) => row.name === PASSWORD_PROPERTY).map((row) => [row.id, row.value]), [[7, SECRET]]);
  assert.equal(parking.digest(), digest);
  assert.equal(sql.state.rows.find((row) => row.id === 8).name, 'other', 'an unrelated property is untouched');
});

test('shouldNeverPutTheValueInAStatement_whenParkingAndRestoring', () => {
  const sql = fakeSql([original()]);
  const parking = createPropertyParking({ sql, name: PASSWORD_PROPERTY, marker: MARKER });
  parking.park();
  parking.restore();
  const hex = Buffer.from(SECRET).toString('hex');
  for (const text of sql.state.log) {
    assert.ok(!text.includes(SECRET) && !text.toLowerCase().includes(hex), 'no statement carries the value or its hex');
  }
});

test('shouldLeaveNoRow_whenTheInstallHadNoPasswordAndTheRunCreatedOne', () => {
  const sql = fakeSql([]);
  const parking = createPropertyParking({ sql, name: PASSWORD_PROPERTY, marker: MARKER });
  parking.park();
  sql.write(PASSWORD_PROPERTY, 'FAKE-PW-run-password');
  assert.equal(sql.state.rows.length, 1);
  parking.restore();
  assert.deepEqual(sql.state.rows, []);
});

test('shouldDoNothing_whenRestoredBeforeItWasParked', () => {
  const sql = fakeSql([original()]);
  const parking = createPropertyParking({ sql, name: PASSWORD_PROPERTY, marker: MARKER });
  parking.restore();
  assert.deepEqual(sql.state.log, []);
  assert.equal(sql.state.rows[0].name, PASSWORD_PROPERTY);
});

test('shouldBeSafeToRestoreTwice_whenCleanupRunsAfterASignal', () => {
  const sql = fakeSql([original()]);
  const parking = createPropertyParking({ sql, name: PASSWORD_PROPERTY, marker: MARKER });
  parking.park();
  parking.restore();
  const statements = sql.state.log.length;
  parking.restore();
  assert.equal(sql.state.log.length, statements, 'the second restore sends nothing');
});

test('shouldRefuseToPark_whenAnEarlierRunLeftItsRowsParked', () => {
  const sql = fakeSql([{ id: 7, name: `${PASSWORD_PROPERTY}.parked-FAKE-PWffffffffffffffff`, value: SECRET, provider_no: null }]);
  const parking = createPropertyParking({ sql, name: PASSWORD_PROPERTY, marker: MARKER });
  assert.throws(() => parking.park(), (error) => /still parked/.test(error.message) && /UPDATE property SET name='mcedt_account_password'/.test(error.message)
    && !error.message.includes(SECRET));
  assert.equal(sql.state.rows[0].name, `${PASSWORD_PROPERTY}.parked-FAKE-PWffffffffffffffff`, 'nothing was moved');
});

test('shouldFailLoudly_whenTheParkedRowsAreGoneAtRestoreTime', () => {
  const sql = fakeSql([original()]);
  const parking = createPropertyParking({ sql, name: PASSWORD_PROPERTY, marker: MARKER });
  parking.park();
  sql.state.rows = [];
  assert.throws(() => parking.restore(), (error) => /was not restored/.test(error.message) && !error.message.includes(SECRET));
});

test('shouldNameThePutBackStatement_whenAskedForTheRecoveryHint', () => {
  const sql = fakeSql([]);
  const parking = createPropertyParking({ sql, name: PASSWORD_PROPERTY, marker: MARKER });
  assert.equal(parking.parkedHint(), `UPDATE property SET name='mcedt_account_password' WHERE name='mcedt_account_password.parked-${MARKER}'`);
});

test('shouldRefuseABadMarkerOrName_whenCreatingTheParking', () => {
  const sql = fakeSql([]);
  assert.throws(() => createPropertyParking({ sql, name: PASSWORD_PROPERTY, marker: 'FAKE-PW-short' }), /run marker/);
  assert.throws(() => createPropertyParking({ sql, name: "x'; DROP TABLE property;--", marker: MARKER }), /plain property name/);
});

/** A scratch ONEDT_OUTBOX: <root>/onEDTDocs/outbox plus its siblings. */
function scratch(t) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'mcedt-ledger-'));
  t.after(() => fs.rmSync(root, { recursive: true, force: true }));
  const parent = path.join(root, 'onEDTDocs');
  fs.mkdirSync(path.join(parent, 'outbox'), { recursive: true });
  fs.mkdirSync(path.join(parent, 'sent'));
  return { parent, outbox: path.join(parent, 'outbox') };
}

test('shouldRemoveTheFilesTheRunCausedAndBothTimestampFiles_whenTheOutboxWasEmpty', (t) => {
  const { parent, outbox } = scratch(t);
  const ledger = createOutboxLedger({ outbox });
  const claim = Buffer.from('HEB claim\r\n');
  fs.writeFileSync(path.join(outbox, 'HK123456.998'), claim);
  ledger.expect('HK123456.998', claim);
  fs.writeFileSync(path.join(outbox, '.timestamp'), '');
  fs.writeFileSync(path.join(parent, 'outbox.timestamp'), '09-10-2026 00:13');
  ledger.restore();
  assert.deepEqual(fs.readdirSync(outbox), []);
  assert.deepEqual(fs.readdirSync(parent).sort(), ['outbox', 'sent']);
});

test('shouldPutTheTimestampBytesBack_whenTheOutboxHadTimestampFiles', (t) => {
  const { parent, outbox } = scratch(t);
  fs.writeFileSync(path.join(outbox, '.timestamp'), 'old inside');
  fs.writeFileSync(path.join(parent, 'outbox.timestamp'), '01-01-2026 08:00');
  const ledger = createOutboxLedger({ outbox });
  fs.writeFileSync(path.join(outbox, '.timestamp'), '');
  fs.unlinkSync(path.join(parent, 'outbox.timestamp'));
  ledger.restore();
  assert.equal(fs.readFileSync(path.join(outbox, '.timestamp'), 'utf8'), 'old inside');
  assert.equal(fs.readFileSync(path.join(parent, 'outbox.timestamp'), 'utf8'), '01-01-2026 08:00');
});

test('shouldLeaveAFileInPlaceAndFail_whenItNowHoldsOtherBytes', (t) => {
  const { outbox } = scratch(t);
  const ledger = createOutboxLedger({ outbox });
  ledger.expect('HK123456.998', Buffer.from('mine'));
  fs.writeFileSync(path.join(outbox, 'HK123456.998'), 'a real claim file');
  assert.throws(() => ledger.restore(), /HK123456\.998 now holds other bytes/);
  assert.equal(fs.readFileSync(path.join(outbox, 'HK123456.998'), 'utf8'), 'a real claim file');
});

test('shouldFail_whenAnUnexpectedFileIsInTheOutboxAfterTheRun', (t) => {
  const { outbox } = scratch(t);
  const ledger = createOutboxLedger({ outbox });
  fs.writeFileSync(path.join(outbox, 'HA777777.001'), 'foreign');
  assert.throws(() => ledger.restore(), /1 entry added to the outbox: HA777777\.001/);
  assert.ok(fs.existsSync(path.join(outbox, 'HA777777.001')), 'a file the run does not own is never deleted');
});

test('shouldFail_whenAFileThatWasThereIsGone', (t) => {
  const { outbox } = scratch(t);
  fs.writeFileSync(path.join(outbox, 'OBECE9123456789012.TXT'), 'bystander');
  const ledger = createOutboxLedger({ outbox });
  fs.unlinkSync(path.join(outbox, 'OBECE9123456789012.TXT'));
  assert.throws(() => ledger.restore(), /removed from the outbox: OBECE9123456789012\.TXT/);
});

test('shouldFail_whenSomethingIsLeftBesideTheOutbox', (t) => {
  const { parent, outbox } = scratch(t);
  const ledger = createOutboxLedger({ outbox });
  fs.writeFileSync(path.join(parent, 'marker.txt'), 'x');
  assert.throws(() => ledger.restore(), /added beside the outbox: marker\.txt:file/);
});

test('shouldReportOnlyVisibleChanges_whenTheApplicationTouchesItsTimestampFile', (t) => {
  const { outbox } = scratch(t);
  const ledger = createOutboxLedger({ outbox });
  fs.writeFileSync(path.join(outbox, '.timestamp'), '');
  assert.deepEqual(ledger.changedVisible(), { added: [], removed: [], changed: [] });
  fs.writeFileSync(path.join(outbox, 'HK123456.998'), 'x');
  assert.deepEqual(ledger.changedVisible().added, ['HK123456.998']);
  assert.deepEqual(ledger.visible().map((entry) => entry.name), ['HK123456.998']);
});

test('shouldTellAddedRemovedAndChanged_whenComparingTwoListings', (t) => {
  const { outbox } = scratch(t);
  fs.writeFileSync(path.join(outbox, 'a'), '1');
  fs.writeFileSync(path.join(outbox, 'b'), '2');
  const was = listDirectory(outbox);
  fs.writeFileSync(path.join(outbox, 'a'), '11');
  fs.unlinkSync(path.join(outbox, 'b'));
  fs.writeFileSync(path.join(outbox, 'c'), '3');
  assert.deepEqual(diffListings(was, listDirectory(outbox)), { added: ['c'], removed: ['b'], changed: ['a'] });
});
