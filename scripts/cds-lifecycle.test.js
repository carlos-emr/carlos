/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const {randomUUID} = require('node:crypto');
const {createHash} = require('node:crypto');
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
    if(s!=='${selected}\\n')process.exit(1);console.log(JSON.stringify({expired:true,scope:'selected-HttpSession',naturalTimeout:false,atomicReference:true}));});`];
  gate.expireSession(command,selected);
  assert.throws(() => gate.expireSession(command,'not-a-cookie'),/identity/);
  assert.throws(() => gate.expireSession([process.execPath,'-e',"console.log('{}')"],selected),/receipt/);
});

test('unsupported atomic attach is refused before a new acceptance', () => {
  const command=[process.execPath,'-e',"console.log(JSON.stringify({preflight:true,supported:true,atomicReference:true}))"];
  gate.preflightSession(command);
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
