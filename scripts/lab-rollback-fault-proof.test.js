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
const fs = require('node:fs');const os = require('node:os');const path = require('node:path');const vm = require('node:vm');
const test = require('node:test');const assert = require('node:assert/strict');
const {createFaultReceipt,verifyOwnedFaultWindow,ownedTriggerStatements,observeOwnedTrigger,dropOwnedTrigger,normalize,sha} = require('./lib/lab-rollback-fault-proof');
const source = path.resolve(__dirname,'..');
const files = {testSourceSha256:'scripts/lab-upload-playwright-checks.js',wrapperSourceSha256:'scripts/lab-upload-rollback-playwright-checks.js',
  helperSourceSha256:'scripts/lib/lab-rollback-fault-proof.js',handlerSourceSha256:'src/main/java/io/github/carlos_emr/carlos/lab/ca/all/upload/handlers/CMLHandler.java'};
const sources = Object.fromEntries(Object.entries(files).map(([k,p])=>[k,sha(fs.readFileSync(path.join(source,p)))]));
const intended=ownedTriggerStatements('lab_upload_probe_A1B2C3D4','LUA1B2C3D4');
const clone = value => structuredClone(value);
function valid() {
  return {schemaVersion:1,databaseMessage:'Synthetic upload rollback probe',triggerName:'lab_upload_probe_A1B2C3D4',accession:'LUA1B2C3D4',...sources,
    payloadSha256:'d'.repeat(64),logTimeZone:'UTC',intentWrittenAt:'2026-10-19T18:55:30.000Z',
    requests:{fault:{method:'POST',path:'/lab/CA/ALL/insideLabUpload',status:200,outcome:'Invalid lab',startedAt:'2026-10-19T18:55:31.000Z',finishedAt:'2026-10-19T18:55:33.000Z'},
      retry:{method:'POST',path:'/lab/CA/ALL/insideLabUpload',status:200,outcome:'Uploaded successfully',startedAt:'2026-10-19T18:55:34.000Z',finishedAt:'2026-10-19T18:55:36.000Z'},
      duplicate:{method:'POST',path:'/lab/CA/ALL/insideLabUpload',status:200,outcome:'Already uploaded',startedAt:'2026-10-19T18:55:37.000Z',finishedAt:'2026-10-19T18:55:39.000Z'}},
    triggerSqlSha256:sha(intended.sql),expectedTriggerBodySha256:sha(normalize(intended.body)),observedTriggerBodySha256:sha(normalize(intended.body)),
    observedTriggerScope:{table:'hl7TextInfo',event:'INSERT',timing:'BEFORE'},triggerAbsentBeforeCreate:true,
    logCursorBeforeFault:'s=0123456789abcdef;i=1;b=abcdef;m=123;t=456;x=789',logSource:'journal unit carlos-emr.service',
    rollbackRows:0,archivedCopiesAfterFailure:0,triggerDropAcknowledged:true,triggerAbsentAfterDrop:true,cleanupAcknowledged:true};
}
function audit() {return [96,97].map(line=>`UNKNOWN     1  ERROR handlers.CMLHandler (CMLHandler.java:${line}) @AbstractDaoImpl.persist :: org.hibernate.exception.GenericJDBCException java.sql.SQLException`).join('\n')
  +'\nFAIL server log audit: 2 unexplained signature(s), 0 known, 2 event(s) since 2026-10-19 18:55:06 UTC\n';}
function errors() {return [96,97].map((line,index)=>`2026-10-19 18:55:32,${index}00 ERROR handlers.CMLHandler (CMLHandler.java:${line}) - Error\norg.hibernate.exception.GenericJDBCException: could not execute statement [Synthetic upload rollback probe]\nCaused by: java.sql.SQLException: Synthetic upload rollback probe\n`).join('');}
function directory(t) {const d=fs.mkdtempSync(path.join(os.tmpdir(),'lab-rollback-proof-'));t.after(()=>fs.rmSync(d,{recursive:true,force:true}));return d;}


test('source-bound exact owned fault pair is recognized without deriving strict PASS',()=>{
  assert.deepEqual(verifyOwnedFaultWindow(errors(),valid(),sources,audit()),{intentionalFaultEvents:2,allOtherErrorsRefused:true,rawStrictAuditUnmodified:true});
});
test('private fsynced intent exists before trigger operations, rejects secret fields and collisions',t=>{
  const d=directory(t);const r=valid();const proof=createFaultReceipt(d,{triggerName:r.triggerName,accession:r.accession,...sources,payloadSha256:r.payloadSha256,logTimeZone:'UTC'});
  assert.equal(fs.statSync(proof.filename).mode&0o777,0o600);assert.equal(JSON.parse(fs.readFileSync(proof.filename)).requests.fault,undefined);
  proof.request('fault',r.requests.fault);proof.update({rollbackRows:0});assert.equal(JSON.parse(fs.readFileSync(proof.filename)).requests.fault.status,200);
  assert.throws(()=>proof.update({secret:'must-never-serialize'}));assert.throws(()=>proof.request('fault',{cookies:'must-never-serialize'}));
  proof.close();proof.close();assert(!fs.readFileSync(proof.filename,'utf8').includes('must-never-serialize'));
  assert.throws(()=>proof.update({cleanupAcknowledged:true}),/closed/);assert.throws(()=>createFaultReceipt(d,{triggerName:r.triggerName,accession:r.accession,...sources,payloadSha256:r.payloadSha256,logTimeZone:'UTC'}),/EEXIST/);
});
for(const [label,mutation] of Object.entries({
  'wrong accession':r=>r.accession='FOREIGN','wrong trigger':r=>r.triggerName='lab_upload_probe_DEADBEEF','different source':r=>r.handlerSourceSha256='0'.repeat(64),
  'missing intent':r=>delete r.intentWrittenAt,'bad payload hash':r=>r.payloadSha256='bad','missing journal cursor':r=>r.logCursorBeforeFault='',
  'changed trigger body':r=>r.observedTriggerBodySha256='0'.repeat(64),'self consistent unowned trigger body':r=>{r.observedTriggerBodySha256='0'.repeat(64);r.expectedTriggerBodySha256='0'.repeat(64)},
  'foreign table':r=>r.observedTriggerScope.table='other','preexisting trigger':r=>r.triggerAbsentBeforeCreate=false,'partial lab rows':r=>r.rollbackRows=1,
  'orphan archive':r=>r.archivedCopiesAfterFailure=1,'missing drop ack':r=>r.triggerDropAcknowledged=false,'trigger not absent':r=>r.triggerAbsentAfterDrop=false,
  'failed cleanup':r=>r.cleanupAcknowledged=false,'nonUTC log':r=>r.logTimeZone='UNVERIFIED','wrong HTTP':r=>r.requests.fault.status=500,
  'empty200 outcome':r=>r.requests.fault.outcome='','wrong method':r=>r.requests.retry.method='GET','wrong route':r=>r.requests.fault.path='/lab/newLabUpload',
  'wrong retry outcome':r=>r.requests.retry.outcome='Invalid lab','missing duplicate':r=>delete r.requests.duplicate,
  'reversed request order':r=>r.requests.retry.startedAt=r.requests.fault.startedAt,'undocumented phase':r=>r.requests.other=r.requests.fault,
})) test('rejects receipt '+label,()=>{const r=valid();mutation(r);assert.throws(()=>verifyOwnedFaultWindow(errors(),r,sources,audit()));});
for(const [label,change] of Object.entries({
  'unrelated ERROR':s=>s+'2026-10-19 18:55:32,900 ERROR Other.Action (Action.java:1) - failure\n',
  'another SQL message':s=>s.replace('Synthetic upload rollback probe','other error'),
  'wrong logger':s=>s.replace('handlers.CMLHandler','Other.Action'),'duplicate source line':s=>s.replace('CMLHandler.java:97','CMLHandler.java:96'),
  'outside request window':s=>s.replaceAll('18:55:32','18:55:34'),'HHH000099':s=>s+'HHH000099\n',
  'missing second event':s=>s.slice(0,s.indexOf('2026-10-19 18:55:32,100')),
})) test('refuses raw log '+label,()=>assert.throws(()=>verifyOwnedFaultWindow(change(errors()),valid(),sources,audit())));
test('any extra or unreadable strict audit evidence remains unaccounted',()=>{
  for(const a of ['',audit()+'UNKNOWN 1 ERROR Other.Action\n',audit().replace('2 event(s)','3 event(s)'),audit().replace('UNKNOWN','KNOWN')])
    assert.throws(()=>verifyOwnedFaultWindow(errors(),valid(),sources,a));
});
test('observed exact trigger body can be dropped; foreign/replaced trigger cannot be dropped',()=>{
  const calls=[];const sql={rows:()=>[['hl7TextInfo','INSERT','BEFORE',Buffer.from(intended.body).toString('hex')]],execute:s=>calls.push(s),value:()=> '0'};
  assert.equal(observeOwnedTrigger(sql,'lab_upload_probe_A1B2C3D4','LUA1B2C3D4').observedTriggerBodySha256,sha(normalize(intended.body)));
  dropOwnedTrigger(sql,'lab_upload_probe_A1B2C3D4','LUA1B2C3D4');assert.deepEqual(calls,['DROP TRIGGER IF EXISTS lab_upload_probe_A1B2C3D4']);
  for(const row of [['other','INSERT','BEFORE',Buffer.from(intended.body).toString('hex')],['hl7TextInfo','INSERT','BEFORE',Buffer.from('BEGIN SIGNAL SQLSTATE \'45000\'; END').toString('hex')]]) {
    calls.length=0;sql.rows=()=>[row];assert.throws(()=>dropOwnedTrigger(sql,'lab_upload_probe_A1B2C3D4','LUA1B2C3D4'));assert.equal(calls.length,0);
  }
});
test('actual native popup helper records exact response and closes pages; no-proof flow stays unchanged',async()=>{
  const sourceText=fs.readFileSync(path.join(__dirname,'lab-upload-playwright-checks.js'),'utf8');
  const start=sourceText.indexOf('async function uploadThroughPopup(');const end=sourceText.indexOf('\n/** POST',start);
  const records=[];let clicked=false,closed=0;const response={status:()=>200,url:()=> 'https://localhost/carlos/lab/CA/ALL/insideLabUpload',request:()=>({method:()=> 'POST'})};
  const locator={setInputFiles:async()=>{},selectOption:async()=>{},click:async()=>{clicked=true},filter(){return this},first(){return this},waitFor:async()=>{},locator(){return this},innerText:async()=> 'Invalid lab'};
  const popup={locator:()=>locator,waitForResponse:async predicate=>{assert(predicate(response));return response},waitForLoadState:async()=>{},close:async()=>{closed++}};
  const context={openUploader:async()=>({popup,inbox:{close:async()=>{closed++}}}),h:{assert},contextPathOf:()=>'/carlos',URL,Date};
  vm.createContext(context);vm.runInContext(sourceText.slice(start,end)+';globalThis.invoke=uploadThroughPopup;',context);
  const session={config:{baseUrl:'https://localhost/carlos'}};
  assert.equal(await context.invoke(session,'fake.hl7','fake.hl7',{request:(phase,record)=>{if(record.startedAt)assert(!clicked);records.push([phase,record])}},'fault'),'Invalid lab');
  assert.equal(records.length,2);assert.equal(records[1][1].method,'POST');assert.equal(records[1][1].status,200);assert.equal(closed,2);
  clicked=false;assert.equal(await context.invoke(session,'fake.hl7','fake.hl7'),'Invalid lab');assert.equal(closed,4);
  response.url=()=> 'https://foreign.example/carlos/lab/CA/ALL/insideLabUpload';await assert.rejects(()=>context.invoke(session,'fake.hl7','fake.hl7',{request(){}},'fault'));assert.equal(closed,6);
});
test('actual fault step refuses a preexisting collision without CREATE or DROP',async()=>{
  const sourceText=fs.readFileSync(path.join(__dirname,'lab-upload-playwright-checks.js'),'utf8');
  const start=sourceText.indexOf("    await session.step('database rejection rolls back the checksum and partial lab'");
  const end=sourceText.indexOf('\n  }\n\n  await session.step',start);const text=sourceText.slice(start,end);
  const calls=[];const context={session:{step:async(_,fn)=>fn()},sql:{value:()=> '1',execute:s=>calls.push(s)},h:{assert,sqlString:s=>"'"+s+"'"},proof:{update(){}},failureTrigger:'lab_upload_probe_A1B2C3D4',triggerMayExist:false};
  vm.createContext(context);await assert.rejects(()=>vm.runInContext('(async()=>{'+text+'})()',context));assert.deepEqual(calls,[]);assert.equal(context.triggerMayExist,false);
});
