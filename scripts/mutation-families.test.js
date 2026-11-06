/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {DatabaseSync} = require('node:sqlite');
const {ownedAppointment,rxSave} = require('./lib/mutation-families');
test('mutation fixture uses the inserted appointment identity and never accepts a missing key', () => {
  const session = {provider: '900001', patient: '17', marker: 'FAKE-owned', sql: {value: () => '42'}};
  assert.equal(ownedAppointment(session, {date: '2026-11-06', start: '10:00', text: 'FAKE-owned'}), '42');
  for (const id of ['0', '', '42;DELETE', '-1']) {
    session.sql.value = () => id;
    assert.throws(() => ownedAppointment(session, {date: '2026-11-06', start: '10:00', text: 'FAKE-owned'}), /owned numeric id/);
  }
});

for (const enforceForeignKeys of [false,true]) test(
  `Rx cleanup removes owned note extensions before parents with foreign keys ${enforceForeignKeys?'enabled':'disabled'}`, () => {
    const db=new DatabaseSync(':memory:');
    try {
      db.exec(`PRAGMA foreign_keys=${enforceForeignKeys?'ON':'OFF'};
        CREATE TABLE casemgmt_note(note_id INTEGER PRIMARY KEY,demographic_no INTEGER);
        CREATE TABLE casemgmt_note_ext(id INTEGER PRIMARY KEY,note_id INTEGER REFERENCES casemgmt_note(note_id),value TEXT);
        CREATE TABLE casemgmt_note_link(note_id INTEGER);
        CREATE TABLE casemgmt_issue_notes(note_id INTEGER);
        CREATE TABLE drugs(drugid INTEGER PRIMARY KEY,demographic_no INTEGER);
        CREATE TABLE prescription(script_no INTEGER PRIMARY KEY,demographic_no INTEGER);
        CREATE TABLE drugReason(demographicNo INTEGER);
        CREATE TABLE partial_date(table_name INTEGER,table_id INTEGER,value TEXT);
        CREATE TABLE DigitalSignature(demographicId INTEGER,ModuleType TEXT);
        INSERT INTO casemgmt_note VALUES(11,101),(22,202);
        INSERT INTO casemgmt_note_ext VALUES(1,11,'FAKE-owned'),(2,22,'FAKE-unrelated');
        INSERT INTO casemgmt_note_link VALUES(11),(22);
        INSERT INTO casemgmt_issue_notes VALUES(11),(22);
        INSERT INTO drugs VALUES(31,101),(32,202);
        INSERT INTO prescription VALUES(41,101),(42,202);
        INSERT INTO drugReason VALUES(101),(202);
        INSERT INTO partial_date VALUES(2,31,'FAKE-owned'),(1,31,'FAKE-other-type'),(2,32,'FAKE-unrelated');
        INSERT INTO DigitalSignature VALUES(101,'PRESCRIPTION'),(101,'CONSULTATION'),(202,'PRESCRIPTION');`);
      const sql={execute:query=>db.exec(query),rows:query=>db.prepare(query).all().map(row=>Object.values(row).map(String)),
        value:query=>String(Object.values(db.prepare(query).get())[0])};
      rxSave({patient:'101',marker:'FAKE-PW0123456789abcdef',sql}).cleanup();
      assert.equal(sql.value('SELECT COUNT(*) FROM casemgmt_note_ext WHERE note_id=11'),'0');
      assert.deepEqual(sql.rows('SELECT * FROM casemgmt_note_ext'),[['2','22','FAKE-unrelated']]);
      assert.deepEqual(sql.rows('SELECT * FROM casemgmt_note'),[['22','202']]);
      assert.deepEqual(sql.rows('SELECT * FROM drugs'),[['32','202']]);
      assert.deepEqual(sql.rows('SELECT * FROM prescription'),[['42','202']]);
      assert.deepEqual(sql.rows('SELECT * FROM partial_date ORDER BY table_name'),
        [['1','31','FAKE-other-type'],['2','32','FAKE-unrelated']]);
      assert.deepEqual(sql.rows('SELECT * FROM DigitalSignature'),[['101','CONSULTATION'],['202','PRESCRIPTION']]);
    } finally {db.close();}
  });
