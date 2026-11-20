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
const { createHash } = require('node:crypto');
const { DatabaseSync } = require('node:sqlite');
const { appointmentMonitorCleanup, finishAppointmentCleanup } = require('./lib/appointment-page-monitor-cleanup');

function fixture(fn) {
  const db = new DatabaseSync(':memory:');
  db.function('SHA2', (text, size) => { assert.equal(size, 256); return createHash('sha256').update(text).digest('hex'); });
  db.exec('CREATE TABLE PageMonitor(id INTEGER PRIMARY KEY,pageName TEXT,pageId TEXT,session TEXT)');
  const queries = [];
  const sql = query => { queries.push(query); return /^SELECT /i.test(query)
    ? db.prepare(query).all().map(row => Object.values(row).join('\t')).join('\n')
    : (db.exec(query), ''); };
  const cleanup = appointmentMonitorCleanup({ sql, provider:'999998', date:'2027-11-23', baseUrl:'https://127.0.0.1/carlos' });
  const context = { cookies: async () => [{name:'JSESSIONID',value:'owned-private-cookie',path:'/carlos'}] };
  const insert = (id, session='owned-private-cookie', provider='999998', date='2027-11-23', page='addappointment') =>
    db.prepare('INSERT INTO PageMonitor VALUES(?,?,?,?)').run(id,page,`${provider}|${date}|08:00|08:14`,session);
  try { return Promise.resolve(fn({db,queries,cleanup,context,insert})).finally(()=>db.close()); }
  catch(error) { db.close(); throw error; }
}

test('real SQL removes only added locks for the owned session/provider/date and preserves foreign controls', () => fixture(async f => {
  f.insert(1); await f.cleanup.remember(f.context);
  f.insert(2); f.insert(3,'another-session'); f.insert(4,undefined,'111111');
  f.insert(5,undefined,undefined,'2027-11-24'); f.insert(6,undefined,undefined,undefined,'editappointment');
  assert.equal(f.cleanup.cleanup(),1);
  assert.deepEqual(f.db.prepare('SELECT id FROM PageMonitor ORDER BY id').all().map(r=>r.id),[1,3,4,5,6]);
  assert.ok(f.queries.every(query=>!query.includes('owned-private-cookie')));
}));
test('remembering a context twice does not adopt the new lock into the preexisting baseline', () => fixture(async f => {
  await f.cleanup.remember(f.context);f.insert(1);await f.cleanup.remember(f.context);
  assert.equal(f.cleanup.cleanup(),1);assert.equal(f.cleanup.cleanup(),0);
}));
test('a missing browser session refuses setup before any lock is changed', () => fixture(async f => {
  f.insert(1);await assert.rejects(()=>f.cleanup.remember({cookies:async()=>[]}),/fixture session/);
  assert.equal(f.cleanup.cleanup(),0);assert.equal(f.db.prepare('SELECT COUNT(*) AS n FROM PageMonitor').get().n,1);
}));
test('the app-context session takes precedence over an unrelated root-path cookie', () => fixture(async f => {
  await f.cleanup.remember({cookies:async()=>[{name:'JSESSIONID',value:'root-session',path:'/'},{name:'JSESSIONID',value:'owned-private-cookie',path:'/carlos'}]});
  f.insert(1);f.insert(2,'root-session');assert.equal(f.cleanup.cleanup(),1);
  assert.deepEqual(f.db.prepare('SELECT id FROM PageMonitor').all().map(r=>r.id),[2]);
}));
test('a second owned session is tracked independently without consuming its preexisting lock', () => fixture(async f => {
  await f.cleanup.remember(f.context);f.insert(1);f.insert(2,'second-session');
  await f.cleanup.remember({cookies:async()=>[{name:'JSESSIONID',value:'second-session',path:'/carlos'}]});f.insert(3,'second-session');
  assert.equal(f.cleanup.cleanup(),2);assert.deepEqual(f.db.prepare('SELECT id FROM PageMonitor').all().map(r=>r.id),[2]);
}));
test('invalid cleanup scopes fail before any SQL execution', () => {
  for(const fields of [{provider:"1' OR 1=1",date:'2027-11-23'},{provider:'1',date:"2027-11-23%' OR 1=1"}]) {
    assert.throws(()=>appointmentMonitorCleanup({sql:()=>assert.fail('No SQL may execute'),...fields}),/Invalid/);
  }
});

test('row and lock cleanup failures are both retained while both cleanups run', () => {
  const first=new Error('row failure'),second=new Error('lock failure');let attempted=false;
  assert.throws(()=>finishAppointmentCleanup(()=>{throw first;},()=>{attempted=true;throw second;}),error=>
    error instanceof AggregateError && error.errors.length===2 && error.errors[0]===first && error.errors[1]===second);
  assert.equal(attempted,true);
});
test('a sole cleanup failure retains the original error and successful cleanup still runs', () => {
  const original=new Error('original');let cleaned=false;
  assert.throws(()=>finishAppointmentCleanup(()=>{throw original;},()=>{cleaned=true;}),error=>error===original);
  assert.equal(cleaned,true);
});

test("the shared validator URL object is normalized to Playwright's string cookie API", async () => {
  let actual;
  const cleanup=appointmentMonitorCleanup({sql:()=>'',provider:'999998',date:'2027-11-23',baseUrl:new URL('https://127.0.0.1/carlos')});
  await cleanup.remember({cookies:async url=>{actual=url;assert.equal(typeof url,'string');return[{name:'JSESSIONID',value:'owned',path:'/carlos'}];}});
  assert.equal(actual,'https://127.0.0.1/carlos');assert.equal(cleanup.cleanup(),0);
});
