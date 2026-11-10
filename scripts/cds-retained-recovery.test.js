/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const vm = require('node:vm');
const {createHash} = require('node:crypto');
const {assertRetainedLogin,sourceFixture} = require('./demographic-cds-retained-recovery-playwright-checks');
const empty = () => ({badResponses:[],consoleIssues:[],pageErrors:[],requestFailures:[],unexpectedDialogs:[]});
test('retained login accepts only the expected clinical document503 and favicon404', () => {
  const recorder = empty();
  recorder.badResponses.push({status:503,resourceType:'document',url:'http://localhost/carlos/provider/providercontrol'});
  recorder.consoleIssues.push({text:'Failed to load resource: the server responded with a status of 503 ()',location:{url:'http://localhost/carlos/provider/providercontrol'}});
  recorder.consoleIssues.push({text:'Failed to load resource: the server responded with a status of 404 ()',location:{url:'http://localhost/favicon.ico'}});
  assert.doesNotThrow(() => assertRetainedLogin(recorder,'http://localhost/carlos'));
  recorder.badResponses.push({status:503,resourceType:'script',url:'http://localhost/carlos/share/javascript/Oscar.js'});
  assert.throws(() => assertRetainedLogin(recorder,'http://localhost/carlos'),/unexpected error response/);
});
test('retained login never excuses a script or network failure', () => {
  const recorder=empty();recorder.requestFailures.push({errorText:'net::ERR_ABORTED'});
  assert.throws(() => assertRetainedLogin(recorder,'http://localhost/carlos'),/script, network or dialog failure/);
  recorder.requestFailures=[];recorder.consoleIssues.push({text:'Uncaught TypeError',location:{url:'http://localhost/carlos/js/login.js'}});
  assert.throws(() => assertRetainedLogin(recorder,'http://localhost/carlos'),/unexpected console error/);
});
test('recovery fixture preserves exact bytes and refuses a different independently pinned digest', () => {
  const directory=fs.mkdtempSync(path.join(os.tmpdir(),'cds-source-fixture-'));
  try {
    const file=path.join(directory,'source.xml'),bytes=Buffer.from('<OmdCds>café 😀</OmdCds>');fs.writeFileSync(file,bytes);
    const env={CDS_RECOVERY_SOURCE_FILE:file,CDS_RECOVERY_SOURCE_NAME:'original.xml',CDS_RECOVERY_SOURCE_SHA256:createHash('sha256').update(bytes).digest('hex')};
    assert.deepEqual(sourceFixture(env).buffer,bytes);
    assert.throws(() => sourceFixture({...env,CDS_RECOVERY_SOURCE_SHA256:'0'.repeat(64)}),/differs from the retained original/);
    assert.throws(() => sourceFixture({...env,CDS_RECOVERY_SOURCE_NAME:'../source.xml'}),/safe original XML/);
  } finally {fs.rmSync(directory,{recursive:true,force:true});}
});

test('an already accepted completed recovery can authenticate without weakening its clinical responses', () => {
  assert.doesNotThrow(() => assertRetainedLogin(empty(),'http://localhost/carlos',false));
  const recorder=empty();recorder.badResponses.push({status:503,resourceType:'document',url:'http://localhost/carlos/provider/providercontrol'});
  assert.throws(() => assertRetainedLogin(recorder,'http://localhost/carlos',false),/unexpected error response/);
});


test('sealed zero-write failure cancellation preserves INCOMPLETE and requires a fresh non-success delivery', () => {
  const {retainedCancellationComplete}=require('./demographic-cds-retained-recovery-playwright-checks');
  const id='11111111-1111-4111-8111-111111111111';
  const value={job:{id,state:'INCOMPLETE',cancelRequested:true,reportGeneration:3},
    summary:{operationTerminal:true,resumeRequested:false,reportsDelivered:true,clinicalComplete:false,cleanComplete:false}};
  assert.equal(retainedCancellationComplete(value,id,2),true);
  assert.equal(retainedCancellationComplete(value,id,3),false,'prior delivery cannot acknowledge a new cancel');
  assert.equal(retainedCancellationComplete(value,'22222222-2222-4222-8222-222222222222',2),false);
  for(const change of [{cancelRequested:false},{state:'COMPLETE'},{reportGeneration:0}])
    assert.equal(retainedCancellationComplete({...value,job:{...value.job,...change}},id,2),false);
  for(const change of [{operationTerminal:false},{resumeRequested:true},{reportsDelivered:false},
    {clinicalComplete:true},{clinicalComplete:undefined},{cleanComplete:true}])
    assert.equal(retainedCancellationComplete({...value,summary:{...value.summary,...change}},id,2),false);
});

test('cancellation failure evidence retains only typed durable flags and never source or clinical text', () => {
  const {cancellationSnapshot}=require('./demographic-cds-retained-recovery-playwright-checks');
  const id='11111111-1111-4111-8111-111111111111';
  const evidence=cancellationSnapshot(JSON.stringify({job:{id,state:'INCOMPLETE',cancelRequested:true,reportGeneration:1,
    privateLocation:'/private/clinical-source.xml',patientName:'PRIVATE-CLINICAL-TEXT'},
    summary:{operationTerminal:true,resumeRequested:false,reportsDelivered:true,reportState:'DELIVERED',
      cleanupComplete:true,clinicalComplete:false,cleanComplete:false,diagnostic:'PRIVATE-CLINICAL-TEXT'}}),id,1);
  assert.equal(evidence.parsed,true); assert.equal(evidence.job.reportGeneration,1);
  assert.equal(evidence.summary.operationTerminal,true); assert.equal(evidence.summary.reportsDelivered,true);
  assert.equal(JSON.stringify(evidence).includes('PRIVATE'),false); assert.equal(JSON.stringify(evidence).includes('/private'),false);
  for(const raw of [null,'not-json','x'.repeat(32769)]) assert.equal(cancellationSnapshot(raw,id,1).parsed,false);
  const invalid=cancellationSnapshot(JSON.stringify({job:{id,state:'PRIVATECLINICALTEXT',cancelRequested:'true',reportGeneration:-1},
    summary:{operationTerminal:'true',reportState:'PRIVATECLINICALTEXT'}}),id,1);
  assert.equal(invalid.job.state,null); assert.equal(invalid.job.cancelRequested,null); assert.equal(invalid.job.reportGeneration,null);
  assert.equal(invalid.summary.operationTerminal,null); assert.equal(invalid.summary.reportState,null);
});

test('cancellation timeout preserves its original failure and exact unchanged completion gate while capturing the DOM', async () => {
  const {waitForCancellation}=require('./demographic-cds-retained-recovery-playwright-checks');
  const id='11111111-1111-4111-8111-111111111111', failure=Error('original timeout'), logs=[];
  const page={waitForFunction:async(predicate,args,options)=>{
    assert.deepEqual(args,{id,generation:1}); assert.deepEqual(options,{timeout:180000});
    const current={job:{id,cancelRequested:true,reportGeneration:2},
      summary:{operationTerminal:true,resumeRequested:false,reportsDelivered:true}};
    const ready=value=>vm.runInNewContext('('+predicate.toString()+')(args)',
      {args,document:{getElementById:()=>({textContent:JSON.stringify(value)})}});
    assert.equal(ready(current),true);
    for(const update of [{reportGeneration:1},{cancelRequested:false},{id:'22222222-2222-4222-8222-222222222222'}])
      assert.equal(ready({...current,job:{...current.job,...update}}),false);
    for(const update of [{operationTerminal:false},{resumeRequested:true},{reportsDelivered:false}])
      assert.equal(ready({...current,summary:{...current.summary,...update}}),false);
    throw failure;
  },locator:selector=>{assert.equal(selector,'#runSummary');return{textContent:async options=>{
    assert.deepEqual(options,{timeout:3000});return JSON.stringify({job:{id,state:'INCOMPLETE',reportGeneration:1,cancelRequested:true},
      summary:{operationTerminal:true,resumeRequested:false,reportsDelivered:true}});
  }}}};
  await assert.rejects(waitForCancellation(page,id,1,{log:(...values)=>logs.push(values)}),error=>error===failure);
  assert.equal(logs.length,1);assert.equal(logs[0][0],'CDS_RECOVERY_CANCEL_FAILURE_EVIDENCE');
  const evidence=JSON.parse(logs[0][1]);assert.equal(evidence.job.reportGeneration,1);assert.equal(evidence.summary.reportsDelivered,true);
  page.locator=()=>({textContent:async()=>{throw Error('browser closed');}});
  await assert.rejects(waitForCancellation(page,id,1,{log:(...values)=>logs.push(values)}),error=>error===failure);
  assert.equal(JSON.parse(logs[1][1]).unavailable,true);
});
