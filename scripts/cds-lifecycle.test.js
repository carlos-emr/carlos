/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {randomUUID} = require('node:crypto');
const {createHash} = require('node:crypto');
const {EventEmitter} = require('node:events');
const {spawnSync} = require('node:child_process');
const h = require('./lib/playwright-harness');
const gate = require('./demographic-cds-lifecycle-playwright-checks');
const ordinary = () => ({CDS_LIFECYCLE_PROVIDER_NO:'EDU1',CDS_LIFECYCLE_PROGRAM_ID:'7',CDS_LIFECYCLE_FACILITY_ID:'1',
  CDS_LIFECYCLE_SOURCE_NAMESPACE:'browser-synthetic',CDS_LIFECYCLE_SOURCE_PROFILE:'pcon-2024-02'});
function state() {
  const options = gate.optionsFrom(ordinary()), source = gate.fixture('LIFE0123456789ABCDEF');
  return {version:1,attempt:randomUUID(),sourceId:'LIFE0123456789ABCDEF',sourceSha256:source.sourceSha256,
    options,optionsDigest:gate.optionsDigest(options),artifactSha256:'a'.repeat(64),completed:[]};
}

test('the ordinary fault fixture fixes one patient,256 real notes and514 native/source units', () => {
  const source = gate.fixture('LIFE0123456789ABCDEF');
  assert.equal(source.notes,256); assert.equal(source.units,514);
  assert.equal((source.buffer.toString().match(/<ClinicalNotes>/g) || []).length,256);
  assert.equal((source.buffer.toString().match(/<PatientRecord>/g) || []).length,1);
  assert.equal(gate.fixture('LIFE0123456789ABCDEF',1).units,4);
  assert.throws(() => gate.fixture('../unsafe'),/identity/);
  assert.throws(() => gate.fixture('LIFE1',257),/count/);
});

test('nondefault effective options require explicit provider/program/facility and a separate synthetic context', () => {
  const options = gate.optionsFrom(ordinary());
  assert.equal(options.matchProviderNames,false); assert.equal(options.mergeExisting,false);
  assert.equal(options.programId,7); assert.equal(options.facilityId,1); assert.equal(options.synthetic,false);
  assert.throws(() => gate.optionsFrom({...ordinary(),CDS_LIFECYCLE_PROGRAM_ID:''}),/reviewed context/);
  assert.throws(() => gate.optionsFrom({...ordinary(),CDS_LIFECYCLE_SOURCE_NAMESPACE:'bad\nname'}),/namespace/);
  assert.throws(() => gate.optionsFrom(ordinary(),true),/reviewed context/);
  const course = gate.optionsFrom({...ordinary(),CDS_LIFECYCLE_COURSE_ID:'42',CDS_LIFECYCLE_SHIFT_DAYS:'7'},true);
  assert.equal(course.courseId,42); assert.equal(course.timeshiftInDays,7);
  assert.throws(() => gate.optionsFrom({...ordinary(),CDS_LIFECYCLE_COURSE_ID:'42',CDS_LIFECYCLE_SHIFT_DAYS:'0'},true),/nonzero/);
});

test('clinical options fingerprint binds all mutable clinical choices while excluding source transport identity', () => {
  const options = gate.optionsFrom(ordinary()), expected = gate.optionsDigest(options);
  for (const [name,value] of Object.entries({providerNo:'EDU2',programId:8,facilityId:2,mergeExisting:true,
    matchProviderNames:true,timeshiftInDays:7,courseId:42,synthetic:true})) {
    assert.notEqual(gate.optionsDigest({...options,[name]:value}),expected,name);
  }
  assert.equal(gate.optionsDigest({...options,sourceNamespace:'another-source',dryRun:true}),expected);
});

test('all JSON protocol probes send contract2 and independently require both response markers', async () => {
  let sent;
  const call = async (_frame,_route,params) => {sent=params;return {apiVersion:'2',body:{apiVersion:2}};};
  await gate.contractApi(null,'cdsImportStatus',{runId:'accepted'},'GET',call);
  assert.equal(sent.apiVersion,2);
  for (const result of [{apiVersion:'1',body:{apiVersion:2}},{apiVersion:'2',body:{apiVersion:1}},{body:{ok:true}}])
    await assert.rejects(gate.contractApi(null,'cdsImportStatus',{},'GET',async () => result),/contract2/);
  await gate.contractApi(null,'cdsImportReport',{type:'receipt'},'GET',call);
  assert.equal(sent.apiVersion,undefined);
});

test('private durable state retains exact accepted source/options/artifact and refuses replacement', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(),'cds-lifecycle-'));
  try {
    const file = path.join(directory,'attempt.json'), initial = state();
    gate.writeState(file,initial,true);
    assert.equal(fs.statSync(file).mode & 0o077,0);
    initial.runId = randomUUID(); gate.writeState(file,initial);
    const saved = gate.readState(file), source = gate.fixture(saved.sourceId);
    assert.equal(saved.runId,initial.runId);
    gate.bindState(saved,saved.options,source,saved.artifactSha256);
    assert.throws(() => gate.writeState(file,initial,true),/EEXIST/);
    assert.throws(() => gate.bindState(saved,{...saved.options,providerNo:'OTHER'},source,saved.artifactSha256),/never create a replacement/);
    assert.throws(() => gate.bindState(saved,saved.options,source,'b'.repeat(64)),/never create a replacement/);
    fs.chmodSync(file,0o644); assert.throws(() => gate.readState(file),/private/);
    fs.unlinkSync(file); fs.symlinkSync(path.join(directory,'other'),file);
    assert.throws(() => gate.readState(file),/private/);
  } finally { fs.rmSync(directory,{recursive:true,force:true}); }
});

test('faults require actual written native data, checkpoints, pending units and a held patient', () => {
  const written = {expected:514,checkpoints:4,pending:510,patients:1,holds:1,notes:1};
  gate.requirePending(written);
  for (const change of [{checkpoints:0},{checkpoints:514},{pending:0},{patients:0},{holds:0},{notes:0},{expected:518}])
    assert.throws(() => gate.requirePending({...written,...change}),/progress window/);
});

test('completion rejects accepted-only, pending cleanup/reports, resumed work and changed immutable provenance', () => {
  const expected = {runId:randomUUID(),optionsDigest:'a',sourceDigest:'b',rulesetDigest:'c',manifestDigest:'d',units:514,copies:1};
  const value = {job:{id:expected.runId,...expected},summary:{clinicalComplete:true,discoveryComplete:true,reportsDelivered:true,
    cleanupComplete:true,cleanComplete:true,resumeRequested:false,maintenanceReleasePending:false,
    maintenanceReleaseState:'RELEASED',pendingUnits:0,inventoryState:'SEALED',sourcePatients:1,expectedUnits:514,expectedPatients:1,targetCopiesPlanned:1}};
  gate.assertCompletion(value,expected);
  for (const key of ['clinicalComplete','discoveryComplete','reportsDelivered','cleanupComplete','cleanComplete'])
    assert.throws(() => gate.assertCompletion({...value,summary:{...value.summary,[key]:false}},expected),/evidence/);
  for (const patch of [{resumeRequested:true},{maintenanceReleasePending:true},{maintenanceReleaseState:'HELD'},{pendingUnits:1}])
    assert.throws(() => gate.assertCompletion({...value,summary:{...value.summary,...patch}},expected),/pending/);
  assert.throws(() => gate.assertCompletion({...value,job:{...value.job,optionsDigest:'changed'}},expected),/provenance/);
});

test('sanitized web job ownership comes from its real owner route and an independent durable scalar', () => {
  const options=gate.optionsFrom(ordinary()),runId=randomUUID(),fingerprint=gate.optionsDigest(options);
  const response={status:200,body:{apiVersion:2,job:{id:runId,sourceNamespace:options.sourceNamespace,
    profile:options.sourceProfile,optionsDigest:fingerprint}}};
  const expected={runId,options,optionsDigest:fingerprint,owner:'web-130'};
  gate.assertAcceptance(response,expected,'web-130');
  assert.equal(response.body.job.operator,undefined);
  assert.throws(() => gate.assertAcceptance(response,expected,'web-131'),/operator/);
  for(const patch of [{sourceNamespace:'another'},{profile:'another'},{optionsDigest:'changed'},{id:randomUUID()}])
    assert.throws(() => gate.assertAcceptance({...response,body:{...response.body,job:{...response.body.job,...patch}}},expected,'web-130'),/acceptance/);
});

test('a browser-simulated timeout is never counted as the genuine proxy fault', () => {
  const recorder = h.createRecorder(), request = {url:() => 'http://localhost/carlos/form/cdsImportSubmit'};
  const raw = {errorText:'net::ERR_TIMED_OUT',requestRef:new WeakRef(request)};
  recorder.requestFailures.push(raw);
  const fault = {request,accepted:true,runId:randomUUID()};
  assert.throws(() => gate.assertScopedFault(recorder,fault),/genuine Nginx/);
  assert.throws(() => gate.assertScopedFault(recorder),/request failed/);
  assert.equal(recorder.requestFailures[0],raw);
});

test('real Nginx504 proof excuses only its exact raw response and explicit non-JSON cancellation', () => {
  const recorder=h.createRecorder(),request={url:() => 'http://localhost:18085/carlos/form/cdsImportSubmit'};
  const raw={status:504,url:request.url(),method:'POST',resourceType:'fetch'};
  recorder.badResponses.push(raw);
  recorder.requestFailures.push({errorText:'net::ERR_ABORTED',requestRef:new WeakRef(request)});
  recorder.consoleIssues.push({text:'Failed to load resource: the server responded with a status of 504 (Gateway Time-out)',location:{url:request.url()}});
  const fault={kind:'nginx',request,accepted:true,runId:randomUUID(),status:504,proxyVerified:true,badResponse:raw};
  gate.assertScopedFault(recorder,fault);
  assert.equal(recorder.badResponses[0],raw);assert.equal(recorder.requestFailures.length,1);
  assert.throws(() => gate.assertScopedFault(recorder,{...fault,proxyVerified:false}),/real-proxy/);
  recorder.badResponses.push({...raw});
  assert.throws(() => gate.assertScopedFault(recorder,fault),/HTTP|504|JavaScript/);
  recorder.badResponses.pop();recorder.requestFailures.push({errorText:'net::ERR_CONNECTION_RESET',requestRef:new WeakRef(request)});
  assert.throws(() => gate.assertScopedFault(recorder,fault),/request failed/);
});

test('bounded evidence hashing refuses symlinks and capacity overflow', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(),'cds-lifecycle-hash-'));
  try {
    const file = path.join(directory,'bytes'); fs.writeFileSync(file,'original');
    const proof = gate.hashFile(file,8); assert.equal(proof.bytes,8);
    assert.throws(() => gate.hashFile(file,7),/capacity/);
    fs.symlinkSync(file,path.join(directory,'link'));
    assert.throws(() => gate.hashFile(path.join(directory,'link')),/ELOOP/);
    const env = {CDS_LIFECYCLE_SERVER_ROOT:'/server/private',CDS_LIFECYCLE_LOCAL_ROOT:directory};
    assert.equal(gate.evidencePath('/server/private/bytes',env),file);
    assert.throws(() => gate.evidencePath('/server/outside',env),/escaped/);
  } finally { fs.rmSync(directory,{recursive:true,force:true}); }
});

test('the session helper receives only the selected synthetic cookie through stdin and rejects false scope', () => {
  const selected = 'A'.repeat(32);
  const command = [process.execPath,'-e',`let s='';process.stdin.on('data',c=>s+=c);process.stdin.on('end',()=>{
    if(s!=='${selected}\\n')process.exit(1);console.log(JSON.stringify({expired:true,scope:'selected-HttpSession',naturalTimeout:false,atomicReference:true,pid:123,startEpochMillis:456}));});`];
  assert.deepEqual(gate.expireSession(command,selected,{pid:123,startEpochMillis:456}),{pid:123,startEpochMillis:456});
  assert.throws(() => gate.expireSession(command,selected,{pid:123,startEpochMillis:457}),/process identity changed/);
  assert.throws(() => gate.expireSession(command,'not-a-cookie'),/identity/);
  assert.throws(() => gate.expireSession([process.execPath,'-e',"console.log('{}')"],selected),/receipt/);
});

test('unsupported atomic attach is refused before a new acceptance', () => {
  const command=[process.execPath,'-e',"console.log(JSON.stringify({preflight:true,supported:true,atomicReference:true,pid:123,startEpochMillis:456}))"];
  assert.deepEqual(gate.preflightSession(command),{pid:123,startEpochMillis:456});
  assert.throws(() => gate.preflightSession(null),/preflight argv/);
  assert.throws(() => gate.preflightSession([process.execPath,'-e',"console.log('{}')"]),/before acceptance/);
  assert.throws(() => gate.preflightSession([process.execPath,'-e','process.exit(1)']),/no new acceptance/);
});

test('default lifecycle execution makes no acceptance and explicitly reports NOT_EXECUTED', async () => {
  assert.equal((await gate.run({})).outcome,'NOT_EXECUTED');
});

function privateEvidenceFixture() {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(),'cds-lifecycle-evidence-'));
  const selected = state(), source = gate.fixture(selected.sourceId);
  const sha = bytes => createHash('sha256').update(bytes).digest('hex');
  selected.runId = randomUUID(); selected.uploadId = randomUUID(); selected.owner = 'web-130';
  selected.rulesetDigest = 'b'.repeat(64); selected.manifestDigest = 'c'.repeat(64);
  const env = {CDS_LIFECYCLE_SERVER_ROOT:'/server/private',CDS_LIFECYCLE_LOCAL_ROOT:directory};
  const serverJob = '/server/private/jobs/' + selected.runId;
  const local = remote => path.join(directory,path.relative('/server/private',remote));
  const put = (remote,bytes) => { const file=local(remote);fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,bytes);return file; };
  const journal = Buffer.alloc(52);journal.writeBigInt64BE(0n,0);journal.writeBigInt64BE(0n,8);
  journal.writeInt32BE(source.buffer.length,16);Buffer.from(source.sourceSha256,'hex').copy(journal,20);
  selected.sourceDigest = sha(journal);
  put('/server/private/uploads/' + selected.uploadId + '/chunks.bin',journal);
  const {timeshiftInDays,...options} = selected.options;
  const document = {actor:{operator:selected.owner},uploadId:selected.uploadId,acceptedRulesetDigest:selected.rulesetDigest,
    snapshot:null,jobRoot:serverJob,command:{...options,input:'/server/private/uploads/' + selected.uploadId + '/source.xml',
      timeshiftDays:timeshiftInDays,courseId:null}};
  let accepted;
  const saveDocument = () => { const bytes=Buffer.from(JSON.stringify(document));accepted=serverJob + '/job-' + sha(bytes) + '.json';put(accepted,bytes); };
  saveDocument();
  const snapshot = serverJob + '/snapshot';
  const inventory = JSON.stringify({relativeName:source.name,member:'000000000000.bin',sha256:source.sourceSha256,
    length:source.buffer.length,patientXml:true}) + '\n';
  put(snapshot + '/files.jsonl',inventory);put(snapshot + '/members/000000000000.bin',source.buffer);
  put(snapshot + '/snapshot.seal',JSON.stringify({version:2,manifestSha256:sha(inventory),archiveSha256:'',files:1,patientXmlFiles:1,totalBytes:source.buffer.length}));
  const reports = serverJob + '/reports-1/report-set.json';
  const names = {completion:'completion.json',error:'ImportErrorLog.jsonl',event:'ImportEventLog.jsonl',units:'units.jsonl'};
  const files = {};
  for (const [key,name] of Object.entries(names)) {
    const bytes = Buffer.from(key === 'error' ? '' : '{}\n');put(path.dirname(reports) + '/' + name,bytes);
    files[key]={name,bytes:bytes.length,sha256:sha(bytes)};
  }
  const manifest = JSON.stringify({runId:selected.runId,sourceDigest:selected.sourceDigest,manifestDigest:selected.manifestDigest,files});
  put(reports,manifest);
  const sql = {rows(query) {
    if (query.startsWith('SELECT snapshot_location')) return [[accepted,reports,sha(manifest)]];
    if (query.startsWith('SELECT expected_bytes')) return [[String(source.buffer.length),String(source.buffer.length),'1',selected.sourceDigest]];
    throw Error('Unexpected evidence query');
  }};
  return {directory,selected,env,sql,document,saveDocument,put,snapshot,reports};
}

test('web acceptance snapshot remains null while actual worker seal,original source,journal and all report bytes verify', () => {
  const fixture = privateEvidenceFixture();
  try {
    const result = gate.protectedEvidence(fixture.sql,fixture.selected,fixture.env);
    assert.deepEqual(Object.keys(result).sort(),['completion','error','event','units']);
    assert.equal(fixture.document.snapshot,null);
  } finally {fs.rmSync(fixture.directory,{recursive:true,force:true});}
});

for (const [name,mutate,match] of [
  ['changed retained original',f => f.put(f.snapshot + '/members/000000000000.bin','changed'),/original accepted source/],
  ['old snapshot seal',f => f.put(f.snapshot + '/snapshot.seal',JSON.stringify({version:1})),/seal\/inventory/],
  ['missing inventory',f => fs.unlinkSync(f.put(f.snapshot + '/files.jsonl','')),/ENOENT/],
  ['changed protected report',f => f.put(path.dirname(f.reports) + '/units.jsonl','wrong'),/report bytes/],
  ['wrong accepted UI option',f => {f.document.command.matchProviderNames=true;f.saveDocument();},/effective option/],
  ['wrong native snapshot assumption',f => {f.document.snapshot={root:f.snapshot};f.saveDocument();},/acceptance/]
]) test('protected completeness refuses ' + name, () => {
  const fixture = privateEvidenceFixture();
  try {mutate(fixture);assert.throws(() => gate.protectedEvidence(fixture.sql,fixture.selected,fixture.env),match);}
  finally {fs.rmSync(fixture.directory,{recursive:true,force:true});}
});


test('an enabled required lifecycle gate refuses missing native credentials before fixture or acceptance work',async()=>{
  await assert.rejects(()=>gate.run({CDS_LIFECYCLE_EXECUTE:'true'}),error=>error.name!=='SkipCheck'
    && error.message.includes('Required lifecycle native prerequisite missing: TEST_USER'));
  const native={TEST_USER:'synthetic',TEST_PASSWORD:'private-unit',TEST_PIN:'2026',MYSQL_PASSWORD:''};
  assert.doesNotThrow(()=>gate.requireNativeEnvironment(native));
  for(const name of Object.keys(native)) {
    const env={...native};delete env[name];
    assert.throws(()=>gate.requireNativeEnvironment(env),error=>error.name!=='SkipCheck'&&error.message.includes(name));
  }
});

test('a nested missing-prerequisite SkipCheck becomes required-gate FAIL1 while actual cleanup still runs',async()=>{
  const processRef={env:{}};let cleaned=false;
  const result=await h.runCheck({name:'required-lifecycle-unit',processRef,stdout:{log(){}},
    createCancellation:()=>({throwIfCancelled(){},isCancellation(){return false;},dispose(){}}),
    run:()=>gate.requiredLifecycleStep(()=>{throw new h.SkipCheck('unit prerequisite unavailable');}),
    cleanup(){cleaned=true;}});
  assert.equal(result.outcome,'FAIL');assert.equal(processRef.exitCode,1);assert.equal(cleaned,true);
  assert.match(result.detail,/cannot skip/);
});

function proxyTimeoutFixture(finished=()=>new Promise(()=>{})) {
  const page=new EventEmitter(),recorder=h.createRecorder(),stages=[];
  const request={url:()=> 'http://localhost:18085/carlos/form/cdsImportSubmit'};
  const proxy={proxyHeader:'X-Owned-Proxy',proxyToken:'1'.repeat(32)};
  const response={request:()=>request,status:()=>504,headers:()=>({'x-owned-proxy':proxy.proxyToken}),finished};
  const options={page,recorder,response,request,proxy,onStage:stage=>stages.push(stage),timeoutMillis:20};
  const abort=(selected=request,errorText='net::ERR_ABORTED')=>{
    recorder.requestFailures.push({requestRef:new WeakRef(selected),errorText});page.emit('requestfailed',selected);
  };
  return {...options,options,stages,abort};
}

test('the real proxy response records verified headers before a normal finished EOF',async()=>{
  const f=proxyTimeoutFixture(async()=>{assert.equal(f.stages[0].stage,'proxy-response-headers');return null;});
  assert.deepEqual(await gate.awaitActualProxyTimeout(f.options),{terminal:'RESPONSE_FINISHED'});
  assert.equal(f.stages.at(-1).stage,'proxy-response-finished');assert.equal(f.page.listenerCount('requestfailed'),0);
});

test('an already recorded exact raw cancellation does not wait on Playwright finished',async()=>{
  let called=false;const f=proxyTimeoutFixture(()=>{called=true;return new Promise(()=>{});});f.abort();
  assert.deepEqual(await gate.awaitActualProxyTimeout(f.options),{terminal:'EXACT_REQUEST_ABORTED'});
  assert.equal(called,false);assert.equal(f.recorder.requestFailures.length,1);assert.equal(f.page.listenerCount('requestfailed'),0);
});

test('a late exact raw cancellation releases a response whose finished promise never settles',async()=>{
  const f=proxyTimeoutFixture();const pending=gate.awaitActualProxyTimeout(f.options);
  assert.equal(f.page.listenerCount('requestfailed'),1);f.abort();
  assert.deepEqual(await pending,{terminal:'EXACT_REQUEST_ABORTED'});
  assert.equal(f.page.listenerCount('requestfailed'),0);assert.equal(f.recorder.requestFailures.length,1);
});

test('a cancellation recorded during observer registration cannot be missed',async()=>{
  const f=proxyTimeoutFixture();const on=f.page.on;
  f.page.on=function(name,listener){on.call(this,name,listener);f.abort();return this;};
  assert.deepEqual(await gate.awaitActualProxyTimeout(f.options),{terminal:'EXACT_REQUEST_ABORTED'});
  assert.equal(f.page.listenerCount('requestfailed'),0);
});

for(const [name,change,pattern] of [
  ['another response request',f=>{f.response.request=()=>({url:f.request.url});},/exact Nginx504/],
  ['another response status',f=>{f.response.status=()=>502;},/exact Nginx504/],
  ['missing proxy header',f=>{f.response.headers=()=>({});},/approved listener/],
  ['wrong proxy header',f=>{f.response.headers=()=>({'x-owned-proxy':'2'.repeat(32)});},/approved listener/],
  ['unexpected selected request error',f=>f.abort(f.request,'net::ERR_CONNECTION_RESET'),/unexpected error/],
  ['duplicate selected request cancellations',f=>{f.abort();f.abort();},/duplicate cancellations/],
  ['an aborted finished result without raw evidence',f=>{f.response.finished=async()=>Error('net::ERR_ABORTED');},/successful finished/]
])test('real proxy timeout proof refuses '+name,async()=>{
  const f=proxyTimeoutFixture();change(f);
  await assert.rejects(gate.awaitActualProxyTimeout(f.options),pattern);
  assert.equal(f.stages.at(-1).stage,'proxy-response-failed');assert.equal(f.page.listenerCount('requestfailed'),0);
});

test('another request object with the same URL cannot prove cancellation and expires visibly',async()=>{
  const f=proxyTimeoutFixture();f.abort({url:f.request.url});
  await assert.rejects(gate.awaitActualProxyTimeout(f.options),/deadline elapsed/);
  assert.equal(f.stages.at(-1).stage,'proxy-response-failed');assert.equal(f.page.listenerCount('requestfailed'),0);
  assert.equal(f.recorder.requestFailures.length,1);
});

test('late wrong or duplicate exact cancellations fail and detach their observer',async()=>{
  for(const duplicate of [false,true]){
    const f=proxyTimeoutFixture();const pending=gate.awaitActualProxyTimeout(f.options);
    if(duplicate){f.abort();f.abort();}else f.abort(f.request,'net::ERR_FAILED');
    await assert.rejects(pending,duplicate?/duplicate/:/unexpected error/);
    assert.equal(f.page.listenerCount('requestfailed'),0);assert.equal(f.stages.at(-1).finished,false);
  }
});

test('a response without EOF or a raw cancellation reaches a finite failed stage',async()=>{
  const f=proxyTimeoutFixture();await assert.rejects(gate.awaitActualProxyTimeout(f.options),/deadline elapsed/);
  assert.deepEqual(f.stages.map(stage=>stage.stage),['proxy-response-headers','proxy-response-failed']);
  assert.equal(f.page.listenerCount('requestfailed'),0);
});

test('failed durable header recording cannot start a body wait or publish a finished stage',async()=>{
  let finished=false;const f=proxyTimeoutFixture(async()=>{finished=true;return null;});
  f.options.onStage=stage=>{f.stages.push(stage);if(stage.stage==='proxy-response-headers')throw Error('owned state write failed');};
  await assert.rejects(gate.awaitActualProxyTimeout(f.options),/owned state write failed/);
  assert.equal(finished,false);assert.equal(f.stages.at(-1).stage,'proxy-response-failed');
  assert.equal(f.page.listenerCount('requestfailed'),0);
});

test('a later finished rejection after exact cancellation is handled without an unhandled rejection',async()=>{
  let rejectFinished;const f=proxyTimeoutFixture(()=>new Promise((_resolve,reject)=>{rejectFinished=reject;}));
  const unhandled=[];const observe=error=>unhandled.push(error);process.on('unhandledRejection',observe);
  try {
    const pending=gate.awaitActualProxyTimeout(f.options);await new Promise(resolve=>setImmediate(resolve));f.abort();
    await pending;rejectFinished(Error('late finished rejection'));
    await new Promise(resolve=>setImmediate(resolve));assert.deepEqual(unhandled,[]);
    assert.equal(f.page.listenerCount('requestfailed'),0);
  } finally {process.off('unhandledRejection',observe);}
});

test('a failed proxy wait persists failure before cleanup and retains accepted handles without retry',async t=>{
  const directory=fs.mkdtempSync(path.join(os.tmpdir(),'cds-proxy-state-'));t.after(()=>fs.rmSync(directory,{recursive:true}));
  const selected={...state(),runId:randomUUID(),uploadId:randomUUID(),submitRequested:true};
  const file=path.join(directory,'state.json');gate.writeState(file,selected,true);
  const f=proxyTimeoutFixture();f.options.onStage=stage=>{selected.proxyClientResponse=stage;gate.writeState(file,selected);};
  let cleanups=0;const processRef={env:{}};
  const result=await h.runCheck({name:'proxy-state-unit',processRef,stdout:{log(){}},
    createCancellation:()=>({throwIfCancelled(){},isCancellation(){return false;},dispose(){}}),
    run:()=>gate.requiredLifecycleStep(()=>gate.awaitActualProxyTimeout(f.options)),
    cleanup(){cleanups++;assert.equal(gate.readState(file).proxyClientResponse.stage,'proxy-response-failed');}});
  assert.equal(result.outcome,'FAIL');assert.equal(processRef.exitCode,1);assert.equal(cleanups,1);
  const retained=gate.readState(file);
  for(const key of ['runId','uploadId','submitRequested','sourceSha256','optionsDigest','artifactSha256'])assert.equal(retained[key],selected[key]);
  assert.deepEqual(retained.completed,[]);assert.deepEqual(fs.readdirSync(directory),['state.json']);
});

test('cleanup uses an idempotent same-attempt stop marker and records only confirmed closures',async t=>{
  const directory=fs.mkdtempSync(path.join(os.tmpdir(),'cds-proxy-cleanup-'));t.after(()=>fs.rmSync(directory,{recursive:true}));
  const selected={...state(),runId:randomUUID()};const stop=path.join(directory,'stop.json');let closes=0,disposals=0,saves=0;
  const options={state:selected,save(){saves++;},log(){},browser:{async close(){closes++;}},sql:{dispose(){disposals++;}},
    relay:{child:{pid:123},file:()=>stop,closed:Promise.resolve(0)}};
  await gate.cleanupLifecycle(options);const marker=fs.readFileSync(stop);await gate.cleanupLifecycle(options);
  assert.deepEqual(fs.readFileSync(stop),marker);assert.equal(closes,2);assert.equal(disposals,2);assert.equal(saves,4);
  assert.equal(selected.cleanup.stage,'cleanup-settled');assert.equal(selected.cleanup.browser,'CLOSED');assert.equal(selected.cleanup.relay,'CLOSED');
  fs.writeFileSync(stop,JSON.stringify({attempt:randomUUID()}),{mode:0o600});const foreign=fs.readFileSync(stop);
  await assert.rejects(gate.cleanupLifecycle(options),/cleanup is incomplete/);assert.deepEqual(fs.readFileSync(stop),foreign);
});

test('browser and relay cleanup deadlines retain unknown process closure and accepted identity',async t=>{
  const directory=fs.mkdtempSync(path.join(os.tmpdir(),'cds-proxy-cleanup-timeout-'));t.after(()=>fs.rmSync(directory,{recursive:true}));
  const selected={...state(),runId:randomUUID(),submitRequested:true};const stop=path.join(directory,'stop.json');let disposals=0;
  await assert.rejects(gate.cleanupLifecycle({state:selected,save(){},log(){},browser:{close:()=>new Promise(()=>{})},
    sql:{dispose(){disposals++;}},relay:{child:{pid:123},file:()=>stop,closed:new Promise(()=>{})},
    browserTimeoutMillis:5,relayTimeoutMillis:5,timeoutMillis:100}),/cleanup is incomplete/);
  assert.equal(selected.cleanup.browser,'UNKNOWN');assert.equal(selected.cleanup.relay,'UNKNOWN');assert.equal(selected.cleanup.relayPid,123);
  assert.equal(selected.cleanup.stage,'cleanup-failed');assert.equal(selected.submitRequested,true);assert.equal(disposals,1);
  assert.equal(JSON.parse(fs.readFileSync(stop)).attempt,selected.attempt);
});

test('the overall cleanup bound does not claim a later browser close or continue later cleanup actions',async()=>{
  let close;let disposals=0;const selected=state();
  const pending=gate.cleanupLifecycle({state:selected,save(){},log(){},browser:{close:()=>new Promise(resolve=>{close=resolve;})},
    sql:{dispose(){disposals++;}},browserTimeoutMillis:50,timeoutMillis:5});
  await assert.rejects(pending,/cleanup is incomplete/);assert.equal(selected.cleanup.browser,'UNKNOWN');
  close();await new Promise(resolve=>setImmediate(resolve));
  assert.equal(selected.cleanup.browser,'UNKNOWN');assert.equal(selected.cleanup.stage,'cleanup-failed');assert.equal(disposals,0);
});

test('cleanup state persistence failure remains a failed gate after confirmed resource closure',async()=>{
  let closed=false;const selected=state();
  await assert.rejects(gate.cleanupLifecycle({state:selected,save(){throw Error('owned state write failed');},log(){},
    browser:{async close(){closed=true;}}}),/cleanup is incomplete/);
  assert.equal(closed,true);assert.equal(selected.cleanup.browser,'CLOSED');assert.equal(selected.cleanup.stage,'cleanup-failed');
});

test('an operation-specific close deadline stops workflow continuation even if owned closure finishes later',async()=>{
  let close;let continued=false;let disposed=false;
  const pending=gate.requiredLifecycleStep(async()=>{
    await gate.boundedLifecycleAction(()=>new Promise(resolve=>{close=()=>{disposed=true;resolve();};}),5,'owned close deadline');
    continued=true;
  });
  await assert.rejects(pending,/owned close deadline/);close();await new Promise(resolve=>setImmediate(resolve));
  assert.equal(disposed,true);assert.equal(continued,false);
});

test('workflow failure recording retains the primary failure when state persistence or logging fails',()=>{
  for(const failedStep of ['save','log']){
    const primary=Error('primary proxy failure'),secondary=Error('owned evidence unavailable');const selected=state();
    assert.throws(()=>gate.recordWorkflowFailure(primary,{state:selected,
      save(){if(failedStep==='save')throw secondary;},log(){if(failedStep==='log')throw secondary;}}),error=>{
      assert.ok(error instanceof AggregateError);assert.deepEqual(error.errors,[primary,secondary]);
      assert.match(error.message,/primary failure and accepted state retained/);return true;
    });
    assert.equal(selected.workflowFailure.stage,'workflow-failed');assert.equal(selected.workflowFailure.acceptedStateRetained,true);
  }
});

test('successful workflow failure recording rethrows the exact primary failure after durable evidence',()=>{
  const primary=Error('primary proxy failure'),selected=state(),steps=[];
  assert.throws(()=>gate.recordWorkflowFailure(primary,{state:selected,
    save(){steps.push('saved');assert.equal(selected.workflowFailure.stage,'workflow-failed');},
    log(label,payload){steps.push('logged');assert.equal(label,'LIFECYCLE_STAGE');assert.equal(JSON.parse(payload).acceptedStateRetained,true);}}),
  error=>error===primary);
  assert.deepEqual(steps,['saved','logged']);
});

test('the actual runner retains both safe workflow and evidence reasons plus cleanup status',async t=>{
  const directory=fs.mkdtempSync(path.join(os.tmpdir(),'cds-safe-reasons-'));t.after(()=>fs.rmSync(directory,{recursive:true}));
  for(const failedStep of ['save','log']){
    const primary=Error('The exact real-proxy response deadline elapsed; accepted state retained');
    const secondary=Object.assign(Error('private evidence details'),{code:failedStep==='save'?'ENOSPC':'EPIPE'});
    const selected=state(),lines=[],resultFile=path.join(directory,failedStep+'.json'),processRef={env:{RESULT_JSON:resultFile}};let thrown,cleanups=0;
    const result=await h.runCheck({name:'lifecycle-recording-boundary-unit',processRef,stdout:{log:line=>lines.push(line)},
      createCancellation:()=>({throwIfCancelled(){},isCancellation(){return false;},dispose(){}}),
      run(){try {gate.recordWorkflowFailure(primary,{state:selected,save(){if(failedStep==='save')throw secondary;},
        log(){if(failedStep==='log')throw secondary;}});} catch(error){thrown=error;throw error;}},
      cleanup(){cleanups++;if(failedStep==='log')throw Error('controlled cleanup pending');}});
    assert.equal(result.outcome,'FAIL');assert.equal(processRef.exitCode,1);assert.equal(cleanups,1);
    assert.ok(thrown instanceof AggregateError);assert.deepEqual(thrown.errors,[primary,secondary]);
    assert.match(result.detail,/primary=\[PROXY_RESPONSE_TIMEOUT: verify owned proxy response evidence/);
    assert.match(result.detail,failedStep==='save'?/evidence=\[ENOSPC: evidence filesystem full; restore owned free space\]/
      :/evidence=\[EPIPE: evidence output closed; restore owned log capture\]/);
    assert.equal(result.cleanupFailed,failedStep==='log'?true:undefined);
    const retained=JSON.parse(fs.readFileSync(resultFile));assert.equal(retained.detail,result.detail);assert.equal(retained.outcome,'FAIL');
    assert.equal(retained.cleanupFailed,result.cleanupFailed);
    t.diagnostic('RETAINED_RUNNER_RESULT '+JSON.stringify({case:failedStep,outcome:retained.outcome,
      detail:retained.detail,cleanupFailed:retained.cleanupFailed || false}));
    assert.ok(lines.some(line=>line.includes(result.detail)));assert.ok(result.detail.length<600);
    assert.equal(selected.workflowFailure.acceptedStateRetained,true);
  }
});

test('the actual runner redacts unexpected primary and evidence secrets into bounded fingerprints',async t=>{
  const directory=fs.mkdtempSync(path.join(os.tmpdir(),'cds-safe-fingerprints-'));t.after(()=>fs.rmSync(directory,{recursive:true}));
  const sensitive='password=synthetic-secret SELECT diagnosis FROM patient /workspace/protected/clinical.json cookie=private-token\n';
  const primary=Error(sensitive.repeat(100)),secondary=Object.assign(Error('evidence '+sensitive.repeat(100)),{code:'ENOSPC\n'+sensitive});
  const lines=[],resultFile=path.join(directory,'result.json'),processRef={env:{RESULT_JSON:resultFile}};let thrown,cleaned=false;
  const result=await h.runCheck({name:'lifecycle-private-diagnostic-unit',processRef,stdout:{log:line=>lines.push(line)},
    createCancellation:()=>({throwIfCancelled(){},isCancellation(){return false;},dispose(){}}),
    run(){try {gate.recordWorkflowFailure(primary,{state:state(),save(){throw secondary;},log(){}});}
      catch(error){thrown=error;throw error;}},cleanup(){cleaned=true;}});
  assert.equal(result.outcome,'FAIL');assert.equal(processRef.exitCode,1);assert.equal(cleaned,true);
  assert.deepEqual(thrown.errors,[primary,secondary]);
  const serialized=fs.readFileSync(resultFile,'utf8');assert.equal(JSON.parse(serialized).detail,result.detail);
  for(const error of [primary,secondary])assert.ok(result.detail.includes('message-prefix4096-sha256='
    +createHash('sha256').update(error.message.slice(0,4096)).digest('hex')));
  for(const secret of ['synthetic-secret','SELECT','diagnosis','/workspace/protected','private-token','\n']){
    assert.equal(result.detail.includes(secret),false);assert.equal(lines.join('').includes(secret),false);
    if(secret!=='\n')assert.equal(serialized.includes(secret),false);
  }
  assert.ok(result.detail.length<600);assert.ok(lines.every(line=>line.length<700));
});

test('the actual pre-run CLI catch reports a safe missing prerequisite and exit1 before native work',()=>{
  const secret='synthetic-cli-secret SELECT patient /workspace/protected/private.json';
  const child=spawnSync(process.execPath,[path.join(__dirname,'demographic-cds-lifecycle-playwright-checks.js')],
    {encoding:'utf8',timeout:5000,maxBuffer:8192,env:{...process.env,CDS_LIFECYCLE_EXECUTE:'true',
      TEST_USER:'',CARLOS_USER:'',TEST_PASSWORD:secret,CARLOS_PASSWORD:secret,MYSQL_PASSWORD:secret}});
  assert.equal(child.error,undefined);assert.equal(child.signal,null);assert.equal(child.status,1);assert.equal(child.stdout,'');
  assert.match(child.stderr,/FAIL lifecycle gate: MISSING_NATIVE_PREREQUISITE: configure TEST_USER before execution/);
  assert.match(child.stderr,/accepted state retained/);assert.ok(child.stderr.length<400);
  for(const privateText of ['synthetic-cli-secret','SELECT','patient','/workspace/protected'])assert.equal(child.stderr.includes(privateText),false);
});

test('the actual composed proxy timeout and two evidence failures retain all safe reasons through the runner',async t=>{
  const directory=fs.mkdtempSync(path.join(os.tmpdir(),'cds-nested-reasons-'));t.after(()=>fs.rmSync(directory,{recursive:true}));
  const secret='password=synthetic-nested-secret SELECT patient /workspace/protected/nested.json';
  const innerEvidence=Object.assign(Error(secret),{code:'ENOSPC'}),outerEvidence=Object.assign(Error(secret),{code:'ENOSPC'});
  const f=proxyTimeoutFixture();f.options.onStage=stage=>{f.stages.push(stage);if(stage.stage==='proxy-response-failed')throw innerEvidence;};
  const resultFile=path.join(directory,'result.json'),processRef={env:{RESULT_JSON:resultFile}},lines=[];
  let inner,thrown,cleanups=0;
  const result=await h.runCheck({name:'nested-proxy-diagnostic-unit',processRef,stdout:{log:line=>lines.push(line)},
    createCancellation:()=>({throwIfCancelled(){},isCancellation(){return false;},dispose(){}}),
    async run(){
      try {await gate.awaitActualProxyTimeout(f.options);} catch(error){
        inner=error;
        try {gate.recordWorkflowFailure(inner,{state:state(),save(){throw outerEvidence;},log(){}});}
        catch(outer){thrown=outer;throw outer;}
      }
    },cleanup(){cleanups++;}});
  assert.equal(result.outcome,'FAIL');assert.equal(processRef.exitCode,1);assert.equal(cleanups,1);
  assert.ok(inner instanceof AggregateError);assert.equal(inner.errors[1],innerEvidence);
  assert.match(inner.errors[0].message,/exact real-proxy response deadline elapsed/);
  assert.deepEqual(thrown.errors,[inner,outerEvidence]);assert.equal(f.page.listenerCount('requestfailed'),0);
  assert.match(result.detail,/primary=\[AGGREGATE_FAILURE: \[PROXY_RESPONSE_TIMEOUT:/);
  assert.equal((result.detail.match(/ENOSPC: evidence filesystem full/g) || []).length,2);
  const serialized=fs.readFileSync(resultFile,'utf8');assert.equal(JSON.parse(serialized).detail,result.detail);
  assert.ok(lines.some(line=>line.includes(result.detail)));assert.equal(result.cleanupFailed,undefined);
  for(const text of [result.detail,serialized,...lines])for(const privateText of ['synthetic-nested-secret','SELECT','/workspace/protected'])
    assert.equal(text.includes(privateText),false);
  t.diagnostic('RETAINED_COMPOSED_RESULT '+JSON.stringify({outcome:result.outcome,detail:result.detail,cleanupFailed:false}));
});

test('nested aggregate diagnostics have finite cause depth,width and output while retaining original objects',async()=>{
  const secret='synthetic-aggregate-secret /workspace/protected/aggregate.json SELECT patient';
  const cyclic=new AggregateError([],secret);cyclic.errors.push(cyclic);
  const wide=new AggregateError(Array.from({length:20},()=>Error(secret)),secret);
  const truncated=new AggregateError([wide,wide,wide],secret);
  const shared=new AggregateError([Object.assign(Error(secret),{code:'ENOSPC'})],secret),aliases=new AggregateError([shared,shared],secret);
  let deep=Error(secret);for(let index=0;index<10;index++)deep=new AggregateError([deep],secret);
  for(const [primary,marker] of [[cyclic,'CAUSE_CYCLE'],[wide,'ADDITIONAL_CAUSES_RETAINED'],[deep,'CAUSE_DEPTH_LIMIT'],
    [aliases,'AGGREGATE_FAILURE'],[truncated,'DIAGNOSTIC_TRUNCATED']]){
    const secondary=Object.assign(Error(secret),{code:'ENOSPC'}),processRef={env:{}};let thrown,cleaned=false;
    const result=await h.runCheck({name:'bounded-aggregate-unit',processRef,stdout:{log(){}},
      createCancellation:()=>({throwIfCancelled(){},isCancellation(){return false;},dispose(){}}),
      run(){try {gate.recordWorkflowFailure(primary,{state:state(),save(){throw secondary;},log(){}});}
        catch(error){thrown=error;throw error;}},cleanup(){cleaned=true;}});
    assert.equal(result.outcome,'FAIL');assert.equal(processRef.exitCode,1);assert.equal(cleaned,true);
    assert.deepEqual(thrown.errors,[primary,secondary]);assert.ok(result.detail.includes(marker));assert.ok(result.detail.length<2300);
    if(primary===aliases){assert.equal(result.detail.includes('CAUSE_CYCLE'),false);assert.equal((result.detail.match(/ENOSPC:/g) || []).length,3);}
    for(const privateText of ['synthetic-aggregate-secret','/workspace/protected','SELECT'])assert.equal(result.detail.includes(privateText),false);
  }
});
