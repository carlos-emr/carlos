/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {adjustmentMessage}=require('./billing-on-ra-replay-playwright-checks');
const fixture={owned:{groupNo:'8123',ohipNo:'961234',hin:'9999999999'},marker:'FAKE-PW0123456789abcdef',claimId:'123',code:'A007A'};
test('MOH adjustment independently encodes zero new invoicing and a negative five-dollar recovery',()=>{
  const lines=adjustmentMessage(fixture).split('\r\n');assert.equal(lines.pop(),'');assert.equal(lines.length,5);
  assert(lines.every(line=>line.length===79));
  assert.equal(lines[0].slice(21,29),'20040616');assert.equal(lines[0].slice(59,69),'000000500-');
  assert.equal(lines[1].slice(23,31),'00000123');assert.equal(lines[1].slice(15,21),'961234');
  assert.equal(lines[2].slice(15,23),'20040512');assert.equal(lines[2].slice(25,30),'A007A');
  assert.equal(lines[2].slice(31,37),'000000');assert.equal(lines[2].slice(37,44),'000500-');
  assert.equal(lines[1].slice(3,14),lines[2].slice(3,14));
});
for(const [label,patch,reason] of [
  ['oversized account',{claimId:'123456789'},/adjustment identity/],
  ['non-string account',{claimId:123},/adjustment identity/],
  ['unowned marker',{marker:'OTHER'},/adjustment identity/],
  ['invalid provider',{owned:{...fixture.owned,ohipNo:'96DROP'}},/provider\/HIN\/code/],
  ['overflowing service code',{code:'A007AEXTRA'},/provider\/HIN\/code/],
])test('fixed-width adjustment refuses '+label,()=>assert.throws(()=>adjustmentMessage({...fixture,...patch}),reason));

test('RA cleanup ownership rejects SQL NULLs, a foreign provider and changed captured identity using real row queries',t=>{
  const fs=require('node:fs'),os=require('node:os'),path=require('node:path'),{spawnSync}=require('node:child_process');
  const {assertOwnedRemittance}=require('./billing-on-ra-import-playwright-checks');
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'ra-cleanup-'));t.after(()=>fs.rmSync(root,{recursive:true,force:true}));
  const database=path.join(root,'rows.sqlite');
  function query(statement) {
    const result=spawnSync('python3',['-c',
      'import sqlite3,sys,json; db=sqlite3.connect(sys.argv[1]); rows=db.execute(sys.argv[2]).fetchall(); db.commit(); print(json.dumps(rows))',database,statement],{encoding:'utf8',timeout:10000});
    assert.equal(result.status,0,result.stderr);return JSON.parse(result.stdout);
  }
  query('CREATE TABLE raheader(raheader_no INTEGER,filename TEXT,payable TEXT)');
  query('CREATE TABLE radetail(raheader_no INTEGER,billing_no INTEGER,providerohip_no TEXT)');
  query("INSERT INTO raheader VALUES(1,'owned.001','FAKE-PW')");
  query("INSERT INTO radetail VALUES(1,123,'961234')");
  const sql={value:statement=>String(query(statement)[0][0])},owner={id:1,name:'owned.001',marker:'FAKE-PW',claimIds:['123'],ohipNo:'961234'};
  assert.doesNotThrow(()=>assertOwnedRemittance(sql,owner));
  for(const assignment of ["billing_no=NULL","billing_no=456","providerohip_no=NULL","providerohip_no='foreign'"]) {
    query('UPDATE radetail SET '+assignment);
    assert.throws(()=>assertOwnedRemittance(sql,owner),/unexpected claim\/provider/);
    query("UPDATE radetail SET billing_no=123,providerohip_no='961234'");
  }
  query("UPDATE raheader SET filename='changed.001'");
  assert.throws(()=>assertOwnedRemittance(sql,owner),/ownership changed/);
  assert.equal(query('SELECT COUNT(*) FROM radetail')[0][0],1,'Refusals must leave rows intact');
});
