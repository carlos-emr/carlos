/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {DatabaseSync} = require('node:sqlite');
const {demographicUpdate, chartNoteSave} = require('./lib/mutation-families');

const marker = 'FAKE-PW0123456789abcdef';
const families = [
  [demographicUpdate, [['demographicExtArchive', 'demographic_no'], ['demographiccustArchive', 'demographic_no'], ['demographiccust', 'demographic_no']]],
  [chartNoteSave, [['eChart', 'demographicNo']]],
];

function fixture(t, factory, support) {
  const db = new DatabaseSync(':memory:');
  t.after(() => db.close());
  db.exec('CREATE TABLE demographic(demographic_no INTEGER PRIMARY KEY,last_name TEXT,first_name TEXT);');
  db.prepare('INSERT INTO demographic VALUES(?,?,?)').run(42, marker, 'Workflow');
  db.prepare('INSERT INTO demographic VALUES(?,?,?)').run(43, 'FOREIGN', 'Workflow');
  for (const [table, column] of [...support, ['casemgmt_tmpsave', 'demographic_no'], ['casemgmt_note_lock', 'demographic_no']]) {
    db.exec(`CREATE TABLE ${table}(${column} INTEGER); INSERT INTO ${table} VALUES(42),(43);`);
  }
  db.exec('CREATE TABLE casemgmt_note(note_id INTEGER,demographic_no INTEGER,note TEXT);');
  const writes = [];
  const state = {beforeWrite: null};
  const sql = {
    value: query => String(Object.values(db.prepare(query).get())[0]),
    rows: query => db.prepare(query).all().map(row => Object.values(row).map(String)),
    execute(query) { if (state.beforeWrite) state.beforeWrite(); writes.push(query); db.exec(query); },
  };
  const family = factory({sql, patient: '42', marker});
  return {db, family, writes, state, count(table, column, patient) {
    return db.prepare(`SELECT COUNT(*) count FROM ${table} WHERE ${column}=?`).get(patient).count;
  }};
}

for (const [factory, support] of families) {
  test(`${factory.name}: cleans replay support and preserves foreign rows and both parents`, t => {
    const f = fixture(t, factory, support);
    f.family.cleanup();
    for (const [table, column] of support) {
      assert.equal(f.count(table, column, 42), 0);
      assert.equal(f.count(table, column, 43), 1);
    }
    assert.equal(f.db.prepare('SELECT COUNT(*) count FROM demographic').get().count, 2);
  });
  test(`${factory.name}: lost parent ownership refuses every delete`, t => {
    const f = fixture(t, factory, support);
    f.db.exec("UPDATE demographic SET last_name='FOREIGN' WHERE demographic_no=42");
    assert.throws(() => f.family.cleanup(), /ownership changed/);
    assert.equal(f.writes.length, 0);
    for (const [table, column] of support) assert.equal(f.count(table, column, 42), 1);
  });
  test(`${factory.name}: ownership changes between check and delete cannot sweep support rows`, t => {
    const f = fixture(t, factory, support);
    f.state.beforeWrite = () => f.db.exec("UPDATE demographic SET last_name='FOREIGN' WHERE demographic_no=42");
    assert.throws(() => f.family.cleanup(), /support rows remain/);
    for (const [table, column] of support) {
      assert.equal(f.count(table, column, 42), 1);
      assert.equal(f.count(table, column, 43), 1);
    }
  });
}
