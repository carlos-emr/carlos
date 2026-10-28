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
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { DatabaseSync } = require('node:sqlite');
const { schedulelessRole } = require('./schedule-role-access-playwright-checks');

test('the complete random marker and role suffix fit the actual30-character privilege key', () => {
  for (const marker of ['FAKE-PW871f5a24ebe3648a','FAKE-PW871f5a24ebe3648b']) {
    const role=schedulelessRole(marker);
    assert.ok(role.startsWith(marker));assert.ok(role.length<=30);
  }
  assert.notEqual(schedulelessRole('FAKE-PW871f5a24ebe3648a'),schedulelessRole('FAKE-PW871f5a24ebe3648b'));
});
test('actual SQL exact cleanup removes the new role without truncation and preserves a foreign prefix control', () => {
  const db=new DatabaseSync(':memory:');
  try {
    db.exec('CREATE TABLE secObjPrivilege(roleUserGroup TEXT PRIMARY KEY CHECK(length(roleUserGroup)<=30),objectName TEXT)');
    const marker='FAKE-PW871f5a24ebe3648a';const oldRole=marker+'-noschedule';
    assert.throws(()=>db.prepare('INSERT INTO secObjPrivilege VALUES(?,?)').run(oldRole,'_msg'),/CHECK/);
    const owned=schedulelessRole(marker),foreign=oldRole.slice(0,30);
    db.prepare('INSERT INTO secObjPrivilege VALUES(?,?)').run(owned,'_msg');
    db.prepare('INSERT INTO secObjPrivilege VALUES(?,?)').run(foreign,'_msg');
    db.prepare('DELETE FROM secObjPrivilege WHERE roleUserGroup=?').run(owned);
    assert.deepEqual(db.prepare('SELECT roleUserGroup FROM secObjPrivilege').all().map(r=>r.roleUserGroup),[foreign]);
  } finally { db.close(); }
});
test('an invalid or shortened marker is refused instead of silently reducing ownership entropy', () => {
  for(const marker of ['FAKE-PW871f','another marker',"FAKE-PW871f5a24ebe3648a'",'FAKE-PW871f5a24ebe3648aff']) {
    assert.throws(()=>schedulelessRole(marker),/complete run marker/);
  }
});
