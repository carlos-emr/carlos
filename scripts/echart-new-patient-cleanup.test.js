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
const assert = require('node:assert/strict');
const test = require('node:test');
const {DatabaseSync} = require('node:sqlite');
const {createOwnedFixtureCleanup} = require('./lib/echart-new-patient-cleanup');
const {PATIENT_KEYED_TABLES} = require('./lib/owned-patient');
const marker='FAKE-PW0123456789abcdef';

function fixture(t,{wrongOwner=false,skipCustDelete=false,id='3009',sqlReady=true,closeError}={}) {
  const db=new DatabaseSync(':memory:');t.after(()=>db.close());const events=[];
  db.exec('CREATE TABLE demographic(demographic_no INTEGER PRIMARY KEY,last_name TEXT COLLATE NOCASE,first_name TEXT);');
  db.prepare('INSERT INTO demographic VALUES(?,?,?)').run(3009,wrongOwner?'Foreign':marker.toUpperCase(),'NOTES');
  db.prepare('INSERT INTO demographic VALUES(?,?,?)').run(42,'FOREIGN','Patient');
  const pairs=[...PATIENT_KEYED_TABLES,['appointment','demographic_no'],['admission','client_id']];
  for(const [table,column]of pairs){
    db.exec(`CREATE TABLE ${table}(id INTEGER PRIMARY KEY,${column} INTEGER,value TEXT);`);
    db.prepare(`INSERT INTO ${table} VALUES(?,?,?)`).run(1,3009,'owned');
    db.prepare(`INSERT INTO ${table} VALUES(?,?,?)`).run(2,42,'foreign');
  }
  db.exec('CREATE TABLE casemgmt_note(note_id INTEGER PRIMARY KEY,demographic_no INTEGER);INSERT INTO casemgmt_note VALUES(11,3009),(12,42);');
  for(const table of ['casemgmt_issue_notes','casemgmt_note_ext','casemgmt_note_link'])db.exec(`CREATE TABLE ${table}(note_id INTEGER);INSERT INTO ${table} VALUES(11),(12);`);
  const allTables=['demographic',...pairs.map(([table])=>table),'casemgmt_note','casemgmt_issue_notes','casemgmt_note_ext','casemgmt_note_link'];
  function foreign(){return Object.fromEntries(allTables.map(table=>{
    const column=table==='demographic'?'demographic_no':pairs.find(([name])=>name===table)?.[1];
    const where=column?`${column}=42`:table==='casemgmt_note'?'demographic_no=42':'note_id=12';
    return [table,db.prepare(`SELECT * FROM ${table} WHERE ${where}`).all()];
  }));}
  function sql(query){
    events.push(query);
    if(/^SELECT/i.test(query.trim()))return db.prepare(query).all().map(row=>Object.values(row).join('\t')).join('\n');
    if(skipCustDelete)query=query.replace(/DELETE FROM demographiccust WHERE demographic_no=3009(?=;|$)/g,'');
    db.exec(query);return '';
  }
  const state={closed:false};
  const options={sql,marker,isSqlReady:()=>sqlReady,getPatientId:()=>id,closeBrowser:async()=>{events.push('browser-close');if(closeError)throw closeError;state.closed=true;}};
  return {db,events,state,options,foreign,sql,pairs};
}

test('all chart children including the three observed residues disappear before parent without touching foreign rows',async t=>{
  const f=fixture(t);const before=f.foreign();
  await createOwnedFixtureCleanup(f.options)();
  for(const table of ['demographicExt','demographicExtArchive','demographiccust'])assert.equal(f.db.prepare(`SELECT COUNT(*) AS count FROM ${table} WHERE demographic_no=3009`).get().count,0,`${table} owned child remains`);
  assert.equal(f.db.prepare('SELECT COUNT(*) AS count FROM demographic WHERE demographic_no=3009').get().count,0);
  assert.deepEqual(f.foreign(),before);
});

test('browser close finishes before any SQL and all children precede parent deletion',async t=>{
  const f=fixture(t);let release;f.options.closeBrowser=()=>new Promise(resolve=>{release=()=>{f.events.push('browser-closed');resolve();};});
  const cleanup=createOwnedFixtureCleanup(f.options);const pending=cleanup();await new Promise(r=>setImmediate(r));assert.deepEqual(f.events,[]);release();await pending;
  assert.equal(f.events[0],'browser-closed');const parent=f.events.findIndex(q=>/^DELETE FROM demographic WHERE/.test(q));
  assert(parent>0);assert(f.events.slice(0,parent).some(q=>q.includes('DELETE FROM demographicExtArchive')));assert(f.events.slice(0,parent).some(q=>q.includes('DELETE FROM demographiccust')));
});

test('browser-close refusal keeps every row and never runs SQL or retries',async t=>{
  const failure=new Error('browser close failed');const f=fixture(t,{closeError:failure});const before=f.foreign();const cleanup=createOwnedFixtureCleanup(f.options);
  await assert.rejects(cleanup(),e=>e===failure);await assert.rejects(cleanup(),e=>e===failure);assert.deepEqual(f.events,['browser-close']);assert.deepEqual(f.foreign(),before);
  assert.equal(f.db.prepare('SELECT COUNT(*) AS count FROM demographic WHERE demographic_no=3009').get().count,1);
});

test('replaced parent refuses all child and parent deletes',async t=>{
  const f=fixture(t,{wrongOwner:true});const before=f.foreign();await assert.rejects(createOwnedFixtureCleanup(f.options)(),/ownership changed/);
  assert(!f.events.some(q=>q.startsWith('DELETE')));assert.deepEqual(f.foreign(),before);assert.equal(f.db.prepare('SELECT COUNT(*) AS count FROM demographiccust WHERE demographic_no=3009').get().count,1);
});

test('partial child cleanup is a failure and retains the parent for recovery',async t=>{
  const f=fixture(t,{skipCustDelete:true});const before=f.foreign();await assert.rejects(createOwnedFixtureCleanup(f.options)(),/chart rows were not removed/);
  assert.equal(f.db.prepare('SELECT COUNT(*) AS count FROM demographic WHERE demographic_no=3009').get().count,1);assert.deepEqual(f.foreign(),before);
});

test('concurrent signal and finally share one cleanup',async t=>{
  const f=fixture(t);const cleanup=createOwnedFixtureCleanup(f.options);const first=cleanup(),second=cleanup();assert.equal(first,second);await Promise.all([first,second]);assert.equal(f.events.filter(q=>q==='browser-close').length,1);assert.equal(f.events.filter(q=>q.startsWith('DELETE FROM demographic WHERE')).length,1);
});

test('an invalid captured id cannot reach SQL deletion',async t=>{
  const f=fixture(t,{id:'3009 OR 1=1'});await assert.rejects(createOwnedFixtureCleanup(f.options)(),/demographic_no/);assert(!f.events.some(q=>q.startsWith('DELETE')));
});

test('a lost captured id can be recovered by the unique marker and normal uppercase name',async t=>{
  const f=fixture(t);f.options.getPatientId=()=>f.sql(`SELECT demographic_no FROM demographic WHERE last_name='${marker}' AND first_name='NOTES'`);await createOwnedFixtureCleanup(f.options)();assert.equal(f.db.prepare('SELECT COUNT(*) AS count FROM demographic WHERE demographic_no=3009').get().count,0);
});

test('an already removed parent with children is not silently treated as clean',async t=>{
  const f=fixture(t);f.db.exec('DELETE FROM demographic WHERE demographic_no=3009');await assert.rejects(createOwnedFixtureCleanup(f.options)(),/ownership changed/);assert(!f.events.some(q=>q.startsWith('DELETE')));assert.equal(f.db.prepare('SELECT COUNT(*) AS count FROM demographicExt WHERE demographic_no=3009').get().count,1);
});

test('missing initialized SQL closes browser without touching credentials or rows',async t=>{
  const f=fixture(t,{sqlReady:false});await createOwnedFixtureCleanup(f.options)();assert.deepEqual(f.events,['browser-close']);
});
