/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
const test=require('node:test');
const assert=require('node:assert/strict');
const {cleanupClients}=require('./oauth-token-lifecycle-playwright-checks');
const {cleanupSharedFixtures,removePatientAudit}=require('./contact-shared-directory-playwright-checks');

test('OAuth cleanup recovers an INSERT whose ID read was lost',()=>{
  const client={key:'owned-key',name:'FAKE-PW-client'};let deleted=false;
  const sql={rows(query){
    if(query.startsWith('SELECT id'))return [['42']];
    return [['owned-key','FAKE-PW-client']];
  },execute(query){assert.match(query,/clientId=42/);deleted=true;},value(){return deleted?'0':'1';}};
  cleanupClients(sql,[client]);assert.equal(client.id,'42');assert.equal(deleted,true);
});

test('OAuth cleanup retains a changed owner, continues independent clients and reports every failure',()=>{
  const attempted=[];
  const clients=[1,2,3].map(id=>({id:String(id),key:'key'+id,name:'FAKE-PW-'+id}));
  const sql={rows(query){
    const id=/WHERE id=(\d+)/.exec(query)[1];
    if(id==='1')return [['other-owner','other-name']];
    if(id==='3')throw new Error('injected SQL read failure');
    return []; // The application deleted client 2; its issued tokens still need cleanup.
  },execute(query){attempted.push(query);},value(){return '0';}};
  assert.throws(()=>cleanupClients(sql,clients),error=>error instanceof AggregateError&&error.errors.length===2
    &&/ownership changed/.test(error.message)&&/injected SQL read failure/.test(error.message));
  assert.equal(attempted.length,1);assert.match(attempted[0],/clientId=2/);assert.doesNotMatch(attempted[0],/clientId=1/);
});

test('shared-contact cleanup tolerates an uncreated secondary patient and still cleans the primary links and control',async()=>{
  const writes=[];const removed=new Set();
  const sql={rows(query){assert.doesNotMatch(query,/undefined/);return [];},value(query){
    assert.doesNotMatch(query,/undefined/);
    if(query.includes('FROM log WHERE'))return '0';
    if(query.includes('DemographicContact'))return '0';
    if(query.includes('Contact WHERE'))return query.includes('AND lastName')?'1':'0';
    const id=/demographic_no=(\d+)/.exec(query)[1];
    return removed.has(id)?'0':'1';
  },execute(query){writes.push(query);const match=/DELETE FROM demographic WHERE demographic_no=(\d+)/.exec(query);if(match)removed.add(match[1]);}};
  await cleanupSharedFixtures(sql,{auditTiming:{quietMs:0},patient:'1',marker:'FAKE-PW',ownedPatients:[{id:'2',name:'FAKE-PW-B'},{name:'FAKE-PW-C'}],control:'42'});
  assert.deepEqual([...removed],['2']);
  assert(writes.some(query=>query==='DELETE FROM DemographicContact WHERE demographicNo=1'));
  assert(writes.some(query=>query.startsWith('DELETE FROM Contact WHERE id=42')));
  assert(writes.every(query=>!query.includes('undefined')));
});

test('a changed shared-contact patient cannot prevent cleanup of independently owned patients and contacts',async()=>{
  const removed=new Set();const writes=[];
  const sql={rows(){return [];},value(query){
    if(query.includes('FROM log WHERE'))return '0';
    if(query.includes('DemographicContact'))return '0';
    if(query.includes('Contact WHERE'))return query.includes('AND lastName')?'1':'0';
    const id=/demographic_no=(\d+)/.exec(query)[1];
    if(id==='2')return '0'; // Its ownership no longer matches: never delete it or its links.
    return removed.has(id)?'0':'1';
  },execute(query){writes.push(query);const match=/DELETE FROM demographic WHERE demographic_no=(\d+)/.exec(query);if(match)removed.add(match[1]);}};
  await assert.rejects(cleanupSharedFixtures(sql,{auditTiming:{quietMs:0},patient:'1',marker:'FAKE-PW',ownedPatients:[{id:'2',name:'FAKE-PW-B'},{id:'3',name:'FAKE-PW-C'}],control:'42'}),/ownership changed/);
  assert.deepEqual([...removed],['3']);assert(writes.every(query=>!query.includes('demographicNo=2')&&!query.includes('demographic_no=2')));
  assert(writes.some(query=>query.startsWith('DELETE FROM Contact WHERE id=42')));
});


test('secondary patient audit cleanup drains a late write and preserves unrelated audits',async()=>{
  let owned=1,foreign=1,removed=0;
  const sql={value(query){assert.match(query,/WHERE demographic_no=2$/);return String(owned);},execute(query){assert.equal(query,'DELETE FROM log WHERE demographic_no=2');owned=0;removed++;}};
  const late=setTimeout(()=>owned++,15);
  try{await removePatientAudit(sql,'2',{quietMs:30,timeoutMs:2000,intervalMs:5});}finally{clearTimeout(late);}
  assert.equal(owned,0);assert.equal(foreign,1);assert.equal(removed,2);
});

test('secondary patient audit cleanup refuses persistent asynchronous residue',async()=>{
  const sql={value:()=> '1',execute:()=>{}};
  await assert.rejects(removePatientAudit(sql,'2',{quietMs:0,timeoutMs:20,intervalMs:2}),/did not settle; retain patient/);
});

/** Model a lost INSERT acknowledgment without allowing deletion of another owner. */
function paymentProviderFixture(mode) {
  const {seedProvider}=require('./lib/bc-payment-boundaries');
  const cleanups=[];let owner=null,deletes=0;
  const sql={value(query){
    if(query.includes('OR ohip_no='))return '0';
    if(query.includes('AND last_name='))return owner==='FAKE-PW-provider'?'1':'0';
    return owner===null?'0':'1';
  },execute(query){
    if(query.startsWith('INSERT')){
      if(mode!=='before')owner='FAKE-PW-provider';
      if(mode==='before'||mode==='after')throw new Error('synthetic provider insert error');
    }else if(query.startsWith('DELETE')){deletes++;owner=null;}
  }};
  return {create:()=>seedProvider({sql,marker:'FAKE-PW-provider',provider:'999998',cleanup:fn=>cleanups.push(fn)}),cleanup:()=>cleanups[0](),replace:value=>{owner=value;},deletes:()=>deletes};
}

test('BC provider cleanup accepts an uncommitted insert failure without hiding it',()=>{
  const f=paymentProviderFixture('before');assert.throws(f.create,/synthetic provider insert error/);f.cleanup();assert.equal(f.deletes(),0);
});

test('BC provider cleanup recovers a committed insert with a lost acknowledgment',()=>{
  const f=paymentProviderFixture('after');assert.throws(f.create,/synthetic provider insert error/);f.cleanup();assert.equal(f.deletes(),1);
});

test('BC provider cleanup retains a changed owner or missing confirmed provider',()=>{
  for(const owner of ['another-owner',null]){
    const f=paymentProviderFixture('confirmed');f.create();f.replace(owner);assert.throws(f.cleanup,/ownership changed/);assert.equal(f.deletes(),0);
  }
});

test('forwarding cleanup continues other uploads, archives and rules after independent failures',t=>{
  const fs=require('node:fs'),path=require('node:path'),os=require('node:os');
  const {cleanupForwardingResources}=require('./lab-forwarding-multi-recipient-playwright-checks');
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'carlos-forward-cleanup-test-')),work=path.join(root,'work');fs.mkdirSync(work);
  const previous=process.env.LAB_UPLOAD_DOCUMENT_STORE;process.env.LAB_UPLOAD_DOCUMENT_STORE=root;
  t.after(()=>{if(previous===undefined)delete process.env.LAB_UPLOAD_DOCUMENT_STORE;else process.env.LAB_UPLOAD_DOCUMENT_STORE=previous;fs.rmSync(root,{recursive:true,force:true});});
  const uploads=[{name:'first.hl7',accession:'FIRST'},{name:'second.hl7',accession:'SECOND'}];
  for(const u of uploads)fs.writeFileSync(path.join(root,'LabUpload.'+u.name+'.1'),'owned');
  const foreign=path.join(root,'LabUpload.foreign.hl7.1');fs.writeFileSync(foreign,'unrelated');
  const seen=[],writes=[];
  const sql={rows(query){seen.push(query);if(query.includes("'FIRST'"))throw new Error('first upload discovery failed');return [];},value:()=> '0',execute(query){writes.push(query);if(query.startsWith('DELETE FROM incomingLabRules'))throw new Error('rule cleanup failed');}};
  assert.throws(()=>cleanupForwardingResources(sql,{uploads,providers:['810001','810002'],rulesOwned:true,work}),error=>error instanceof AggregateError&&error.errors.length===2&&/first upload discovery failed/.test(error.message)&&/rule cleanup failed/.test(error.message));
  assert.equal(seen.length,2);assert.ok(writes.some(query=>query.includes('second.hl7')));assert.ok(writes.some(query=>query.startsWith('DELETE FROM incomingLabRules')));
  assert.equal(fs.existsSync(work),false);assert.equal(fs.existsSync(path.join(root,'LabUpload.first.hl7.1')),false);assert.equal(fs.existsSync(path.join(root,'LabUpload.second.hl7.1')),false);assert.equal(fs.existsSync(foreign),true);
});


test('document findings remain FAIL when a later uploader prerequisite is missing',async()=>{
  const {runCheck}=require('./lib/playwright-harness');
  const {unavailableWithFindings}=require('./document-format-matrix-playwright-checks');
  for(const defects of [[],['stored upload bytes differ']]){
    const processRef={exitCode:null,env:{},on(){},removeListener(){}};let cleaned=false;
    const result=await runCheck({name:'document-prerequisite-classification',processRef,stdout:{log(){}},run:()=>unavailableWithFindings(defects,'modern uploader unavailable'),cleanup:()=>{cleaned=true;}});
    assert.equal(cleaned,true);assert.equal(result.outcome,defects.length?'FAIL':'SKIP');assert.equal(processRef.exitCode,defects.length?1:2);
    if(defects.length)assert.match(result.detail,/stored upload bytes differ/);
  }
});
