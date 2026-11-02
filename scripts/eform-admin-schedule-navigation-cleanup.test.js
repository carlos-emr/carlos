/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert=require('node:assert/strict'),test=require('node:test');
const {ownedRows,removeOwnedForms}=require('./eform-admin-schedule-navigation-cleanup');
const marker='FAKE-PW0123456789abcdef',identity={marker,name:marker+' Admin Nav',subject:marker+' Schedule navigation'};
const row=['42',identity.name,identity.subject,'a'.repeat(64),`<title>${marker}</title><p>Temporary Playwright navigation regression eForm.</p>`];
test('eForm teardown refuses malformed identities and rows that no longer belong to the uploaded fixture',()=>{
  assert.deepEqual(ownedRows([row],identity),[row]);
  const nativeNull=row.slice();nativeNull[2]=null;assert.deepEqual(ownedRows([nativeNull],identity),[nativeNull]);
  for(const [index,value] of [[0,'1 OR 1=1'],[1,'foreign'],[2,'other subject'],[3,'bad hash'],[4,'foreign HTML']]) {
    const changed=row.slice();changed[index]=value;assert.throws(()=>ownedRows([changed],identity),/ownership changed/);
  }
  assert.throws(()=>ownedRows([],{...identity,marker:'foreign'}),/Invalid owned/);
});
test('eForm teardown never deletes a library fixture that acquired clinical instances',()=>{
  let deleted=false;
  const sql={rows:()=>[row],value:()=> '1',execute:()=>{deleted=true;}};
  assert.throws(()=>removeOwnedForms(sql,identity),/patient instances/);assert.equal(deleted,false);
});
