/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const { cleanupAppointmentArchives } = require('./lib/billing-on-submit-cleanup');
const fixture = { stamp: 'PW_BILL_12345', providerNo: '999998', demographicNo: '1', billingDate: '2024-05-06', labels: ['ohip', 'wsib', 'bonus'], appointmentIds: ['16', '17', '18'] };
const row = (id, appointment, label = 'ohip', provider = '999998') => [String(id), String(appointment), provider, '1', '2024-05-06', `PW_BILL_12345 ${label}`, `PW_BILL_12345 ${label}`];

function database({ live = [], archived = [], failDelete = false, ignoreDelete = false } = {}) {
  const deleted = [];
  return {
    deleted,
    rows(query) {
      if (query.includes('FROM appointmentArchive')) {
        if (query.startsWith('SELECT id,appointment_no')) return archived;
        return archived.map(r => [r[0]]);
      }
      assert.match(query, /FROM appointment WHERE/);
      return live;
    },
    execute(query) {
      assert.match(query, /^DELETE FROM appointmentArchive WHERE id IN \(/);
      assert.match(query, /provider_no='999998' AND demographic_no=1 AND appointment_date='2024-05-06'/);
      assert.match(query, /notes IN \('PW_BILL_12345 ohip','PW_BILL_12345 wsib','PW_BILL_12345 bonus'\) AND reason=notes/);
      assert.doesNotMatch(query, /LIKE|id\s*>/);
      if (failDelete) throw new Error('archive delete failed');
      deleted.push(query);
      if (!ignoreDelete) archived = [];
    },
  };
}

test('real SQL deletes owned archives and preserves a similarly marked foreign archive', t => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'billing-submit-cleanup-'));
  t.after(() => fs.rmSync(root, { recursive: true, force: true }));
  const file = path.join(root, 'fixture.sqlite');
  const query = statement => JSON.parse(execFileSync('python3', ['-c',
    'import json,sqlite3,sys; db=sqlite3.connect(sys.argv[1]); rows=db.execute(sys.argv[2]).fetchall(); db.commit(); print(json.dumps(rows))',
    file, statement], { encoding: 'utf8', timeout: 10000 }));
  query('CREATE TABLE appointment (appointment_no INTEGER,provider_no TEXT,demographic_no INTEGER,appointment_date TEXT,notes TEXT,reason TEXT)');
  query('CREATE TABLE appointmentArchive (id INTEGER,appointment_no INTEGER,provider_no TEXT,demographic_no INTEGER,appointment_date TEXT,notes TEXT,reason TEXT)');
  for (const [appointment,label] of [[16,'ohip'],[17,'wsib'],[18,'bonus']]) {
    const values = `${appointment},'999998',1,'2024-05-06','PW_BILL_12345 ${label}','PW_BILL_12345 ${label}'`;
    query(`INSERT INTO appointment VALUES (${values})`);
    query(`INSERT INTO appointmentArchive VALUES (${appointment + 185},${values})`);
  }
  query("INSERT INTO appointmentArchive VALUES (9999,999,'777777',2,'2024-05-06','PW_BILL_123456 ohip','PW_BILL_123456 ohip')");
  assert.deepEqual(cleanupAppointmentArchives(query, query, fixture), ['16','17','18']);
  assert.deepEqual(query('SELECT id,provider_no,demographic_no,notes FROM appointmentArchive'),
    [[9999,'777777',2,'PW_BILL_123456 ohip']]);
});

test('refuses a foreign version of a captured appointment before deleting any row', () => {
  const db = database({ live: [row(16,16)], archived: [row(201,16), row(9999,17,'wsib','777777')] });
  assert.throws(() => cleanupAppointmentArchives(db.rows, db.execute, fixture), /Unowned/);
  assert.equal(db.deleted.length, 0);
});

test('refuses a changed appointment identity before even considering its archive', () => {
  const db = database({ live: [row(16,16,'ohip','777777')], archived: [row(201,16)] });
  assert.throws(() => cleanupAppointmentArchives(db.rows, db.execute, fixture), /Unowned/);
  assert.equal(db.deleted.length, 0);
});

test('recovers an inserted appointment whose ID lookup was lost using exact run notes and owner fields', () => {
  const db = database({ live: [row(16,16)], archived: [row(201,16)] });
  assert.deepEqual(cleanupAppointmentArchives(db.rows, db.execute, { ...fixture, appointmentIds: [] }), ['16']);
  assert.equal(db.deleted.length, 1);
});

test('propagates partial cleanup failures and refuses to claim success when DELETE leaves rows', () => {
  for (const mode of ['failDelete','ignoreDelete']) {
    const db = database({ archived: [row(201,16)], [mode]: true });
    assert.throws(() => cleanupAppointmentArchives(db.rows, db.execute, fixture), /archive delete failed|archives remain/);
  }
});

test('an interrupted setup before any appointment attempt performs no database action', () => {
  const forbidden = () => { throw new Error('unexpected database action'); };
  assert.deepEqual(cleanupAppointmentArchives(forbidden, forbidden, { ...fixture, labels: [], appointmentIds: [] }), []);
});
