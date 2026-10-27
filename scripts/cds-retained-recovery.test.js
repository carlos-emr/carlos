/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
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
