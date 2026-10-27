#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

// Manual disposable-database gate. An accepted handle is retained even when this process fails.
const fs = require('node:fs');
const path = require('node:path');
const {createHash, randomUUID} = require('node:crypto');
const {execFileSync,spawn} = require('node:child_process');
const h = require('./lib/playwright-harness');
const {assertRetainedLogin} = require('./demographic-cds-retained-recovery-playwright-checks');
const {importApi:sharedImportApi, awaitRunReceipt, uploadAndAwaitRun, assertSchemaValid} = require('./demographic-cds-import-playwright-checks');
const {installCdsMetadataTransportObserver} = require('./lib/cds-metadata-transport');
const {createCdsMetadataProver} = require('./lib/cds-metadata-domain-proof');
const {effectiveOptions} = require('../src/main/webapp/js/demographic/cds-import');
const {privateJson,publish} = require('./lib/cds-lifecycle-proxy-relay');
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const SHA = /^[0-9a-f]{64}$/;
const q = h.sqlString;
const xml = value => String(value).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;');
const hash = bytes => createHash('sha256').update(bytes).digest('hex');

async function contractApi(frame,route,params = {},method = 'GET',call = sharedImportApi) {
  const binary = route === 'cdsImportReport';
  const response = await call(frame,route,binary ? params : {...params,apiVersion:2},method);
  if (!binary) h.assert(response.apiVersion === '2' && response.body?.apiVersion === 2,
    'JSON status/receipt does not implement the exact deployed CDS API contract2');
  return response;
}
const importApi = (...args) => contractApi(...args);

function optionsFrom(env, synthetic = false) {
  const positive = name => {
    h.assert(/^[1-9][0-9]*$/.test(env[name] || '') && Number.isSafeInteger(Number(env[name])), name + ' must identify a reviewed context');
    return Number(env[name]);
  };
  const options = effectiveOptions({providerNo:env.CDS_LIFECYCLE_PROVIDER_NO,
    programId:positive('CDS_LIFECYCLE_PROGRAM_ID'),facilityId:positive('CDS_LIFECYCLE_FACILITY_ID'),
    sourceNamespace:env.CDS_LIFECYCLE_SOURCE_NAMESPACE,sourceProfile:env.CDS_LIFECYCLE_SOURCE_PROFILE,
    mergeExisting:false,dryRun:false,matchProviderNames:false,synthetic,
    courseId:synthetic ? positive('CDS_LIFECYCLE_COURSE_ID') : undefined,
    timeshiftInDays:synthetic ? Number(env.CDS_LIFECYCLE_SHIFT_DAYS) : 0});
  h.assert(/^[A-Za-z0-9_.-]{1,128}$/.test(options.sourceNamespace)
    && /^[A-Za-z0-9_.-]{1,32}$/.test(options.sourceProfile), 'A registered source namespace/profile is required');
  if (synthetic) h.assert(Number.isInteger(options.timeshiftInDays) && options.timeshiftInDays !== 0
    && Math.abs(options.timeshiftInDays) <= 36600, 'The synthetic gate requires an explicit bounded nonzero day shift');
  return options;
}

/** Independent encoding of the published length-delimited clinical options contract. */
function optionsDigest(options) {
  const digest = createHash('sha256');
  for (const value of [options.providerNo,options.programId ?? '',options.facilityId ?? '',options.mergeExisting,
    options.matchProviderNames,options.timeshiftInDays,options.courseId ?? '',options.synthetic]) {
    const bytes = Buffer.from(String(value)); digest.update(String(bytes.length) + ':'); digest.update(bytes);
  }
  return digest.digest('hex');
}

function hashFile(file, maximum = 1024 * 1024 * 1024) {
  const descriptor = fs.openSync(file,fs.constants.O_RDONLY | fs.constants.O_NOFOLLOW);
  try {
    const info = fs.fstatSync(descriptor);
    h.assert(info.isFile() && info.size >= 0 && info.size <= maximum, 'Evidence file exceeds its finite capacity');
    const digest = createHash('sha256'), buffer = Buffer.alloc(65536);
    let total = 0, count;
    while ((count = fs.readSync(descriptor,buffer,0,buffer.length,null)) > 0) {
      total += count; h.assert(total <= maximum, 'Evidence file grew beyond its finite capacity');
      digest.update(buffer.subarray(0,count));
    }
    h.assert(total === info.size, 'Evidence file changed size during verification');
    return {bytes:total,sha256:digest.digest('hex')};
  } finally { fs.closeSync(descriptor); }
}

function evidencePath(value, env) {
  const remote = env.CDS_LIFECYCLE_SERVER_ROOT, local = env.CDS_LIFECYCLE_LOCAL_ROOT;
  h.assert(typeof remote === 'string' && path.isAbsolute(remote) && typeof local === 'string' && path.isAbsolute(local),
    'Exact server/local protected evidence roots must be declared before execution');
  const relative = path.relative(path.resolve(remote),path.resolve(value));
  h.assert(relative !== '' && relative !== '..' && !relative.startsWith('../') && !path.isAbsolute(relative),
    'Protected evidence escaped the declared server root');
  const root = fs.realpathSync(local), selected = fs.realpathSync(path.join(root,relative));
  h.assert(selected.startsWith(root + path.sep), 'Protected evidence escaped the local private root');
  h.assert(!fs.lstatSync(path.join(root,relative)).isSymbolicLink(), 'Protected evidence must not be a symbolic link');
  return selected;
}

function boundedJson(file, maximum = 65536) {
  const verified = hashFile(file,maximum);
  const value = JSON.parse(boundedText(file,maximum));
  h.assert(hashFile(file,maximum).sha256 === verified.sha256, 'Protected JSON changed during verification');
  return {value,...verified};
}

function boundedText(file, maximum) {
  return new TextDecoder('utf-8',{fatal:true}).decode(boundedBytes(file,maximum));
}

function boundedBytes(file, maximum) {
  const descriptor = fs.openSync(file,fs.constants.O_RDONLY | fs.constants.O_NOFOLLOW);
  try {
    const info = fs.fstatSync(descriptor);
    h.assert(info.isFile() && info.size <= maximum, 'Protected text exceeded its fixed capacity');
    const bytes = Buffer.alloc(maximum + 1);
    let total = 0, count;
    while (total < bytes.length && (count = fs.readSync(descriptor,bytes,total,bytes.length - total,null)) > 0) total += count;
    h.assert(total <= maximum && total === info.size, 'Protected text changed size or exceeded capacity');
    return bytes.subarray(0,total);
  } finally { fs.closeSync(descriptor); }
}

/** Verify the actual immutable acceptance, retained original, inventory and full report set. */
function protectedEvidence(sql, state, env) {
  const [row] = sql.rows('SELECT snapshot_location,report_location,report_digest FROM cds_import_run WHERE id=' + q(state.runId));
  h.assert(row && row.length === 3 && SHA.test(row[2]), 'Durable protected evidence selection is absent');
  const accepted = boundedJson(evidencePath(row[0],env));
  h.assert(path.basename(row[0]) === 'job-' + accepted.sha256 + '.json', 'Accepted command document hash changed');
  const document = accepted.value, command = document.command;
  h.assert(document.actor.operator === state.owner && document.uploadId === state.uploadId
    && document.acceptedRulesetDigest === state.rulesetDigest && document.snapshot === null,
    'Protected acceptance actor/upload/rules/source changed');
  for (const key of ['providerNo','programId','facilityId','sourceNamespace','sourceProfile','mergeExisting','dryRun','matchProviderNames','synthetic'])
    h.assert(command[key] === state.options[key], 'Protected accepted effective option differs: ' + key);
  h.assert(command.timeshiftDays === state.options.timeshiftInDays
    && (command.courseId ?? null) === (state.options.courseId ?? null), 'Protected accepted course/date-shift differs');
  // WEB acceptance intentionally has snapshot=null: its sourceDigest binds the fixed-size upload
  // receipt journal. The fenced worker publishes the distinct immutable snapshot afterwards.
  const source = fixture(state.sourceId,state.options.synthetic ? 1 : 256,state.notePadding || 0);
  const [upload] = sql.rows('SELECT expected_bytes,received_bytes,chunk_count,manifest_digest FROM cds_import_upload WHERE id='
    + q(state.uploadId) + ' AND operator_key=' + q(state.owner) + ' AND run_id=' + q(state.runId));
  const journal = boundedBytes(evidencePath(path.join(path.dirname(command.input),'chunks.bin'),env),52 * 256);
  h.assert(upload && Number(upload[0]) === source.buffer.length && Number(upload[1]) === source.buffer.length
    && Number(upload[2]) * 52 === journal.length && upload[3] === state.sourceDigest && hash(journal) === state.sourceDigest,
    'The accepted upload journal/source digest is incomplete or changed');
  let offset = 0;
  for (let position = 0; position < journal.length; position += 52) {
    const bytes = journal.readInt32BE(position + 16);
    h.assert(journal.readBigInt64BE(position) === BigInt(position / 52)
      && journal.readBigInt64BE(position + 8) === BigInt(offset) && bytes > 0 && offset + bytes <= source.buffer.length
      && journal.subarray(position + 20,position + 52).toString('hex') === hash(source.buffer.subarray(offset,offset + bytes)),
      'The retained ordered upload receipt does not bind the exact original source');
    offset += bytes;
  }
  h.assert(offset === source.buffer.length, 'The accepted source journal omits original bytes');
  const snapshotRoot = path.join(document.jobRoot,'snapshot');
  const files = evidencePath(path.join(snapshotRoot,'files.jsonl'),env);
  const inventory = boundedText(files,65536);
  const entries = inventory.trim().split('\n').map(line => JSON.parse(line));
  h.assert(entries.length === 1 && entries[0].sha256 === state.sourceSha256, 'Retained snapshot inventory differs from exact source bytes');
  const seal = boundedJson(evidencePath(path.join(snapshotRoot,'snapshot.seal'),env)).value;
  h.assert(seal.version === 2 && seal.manifestSha256 === hash(Buffer.from(inventory)) && seal.archiveSha256 === ''
    && seal.files === 1 && seal.patientXmlFiles === 1 && seal.totalBytes === source.buffer.length,
    'The actual worker snapshot seal/inventory differs from the retained original');
  h.assert(/^[0-9]{12}\.bin$/.test(entries[0].member), 'Snapshot member identity is invalid');
  const retained = hashFile(evidencePath(path.join(snapshotRoot,'members',entries[0].member),env),16 * 1024 * 1024);
  h.assert(retained.sha256 === state.sourceSha256, 'The original accepted source is no longer intact');
  const manifest = boundedJson(evidencePath(row[1],env));
  h.assert(manifest.sha256 === row[2] && manifest.value.runId === state.runId
    && manifest.value.sourceDigest === state.sourceDigest && manifest.value.manifestDigest === state.manifestDigest,
    'Selected report-set manifest differs from durable accepted inventory');
  h.assert(Object.keys(manifest.value.files).sort().join(',') === 'completion,error,event,units', 'Protected report set is incomplete');
  const names = {completion:'completion.json',error:'ImportErrorLog.jsonl',event:'ImportEventLog.jsonl',units:'units.jsonl'};
  const verified = {};
  for (const [key,name] of Object.entries(names)) {
    const entry = manifest.value.files[key];
    h.assert(entry.name === name && SHA.test(entry.sha256) && Number.isSafeInteger(entry.bytes) && entry.bytes <= 8 * 1024 * 1024,
      'Protected report descriptor is invalid or unbounded for this fixture');
    const actual = hashFile(evidencePath(path.join(path.dirname(row[1]),name),env),8 * 1024 * 1024);
    h.assert(actual.bytes === entry.bytes && actual.sha256 === entry.sha256, 'Protected report bytes differ from their committed manifest');
    verified[key] = actual;
  }
  return verified;
}

function fixture(sourceId, notes = 256, padding = 0) {
  h.assert(/^[A-Za-z0-9]{1,20}$/.test(sourceId) && [1,256].includes(notes), 'The lifecycle fixture identity/count is invalid');
  h.assert(Number.isSafeInteger(padding) && padding >= 0 && padding <= 49152, 'The finite note payload is invalid');
  const name = 'FAKE-CDS-LIFE-' + sourceId;
  const rows = Array.from({length:notes}, (_,index) => `<ClinicalNotes><NoteType>Clinical</NoteType>`
    + `<MyClinicalNotesContent>${xml(name)} note ${index + 1}${'X'.repeat(padding)}</MyClinicalNotesContent>`
    + '<EventDateTime><cdsd:FullDateTime>2024-03-10T12:34:56</cdsd:FullDateTime></EventDateTime></ClinicalNotes>').join('');
  const buffer = Buffer.from(`<?xml version="1.0" encoding="UTF-8"?><OmdCds xmlns="cds" xmlns:cdsd="cds_dt"><PatientRecord>`
    + '<Demographics><Names><cdsd:LegalName namePurpose="L"><cdsd:FirstName><cdsd:Part>Lifecycle</cdsd:Part>'
    + '<cdsd:PartType>GIV</cdsd:PartType></cdsd:FirstName><cdsd:LastName><cdsd:Part>' + name
    + '</cdsd:Part><cdsd:PartType>FAMC</cdsd:PartType></cdsd:LastName></cdsd:LegalName></Names>'
    + '<DateOfBirth>1971-03-04</DateOfBirth><Gender>F</Gender><UniqueVendorIdSequence>' + sourceId
    + '</UniqueVendorIdSequence><PersonStatusCode><PersonStatusAsEnum>A</PersonStatusAsEnum></PersonStatusCode>'
    + '</Demographics>' + rows + '</PatientRecord></OmdCds>');
  return {name:sourceId + '.xml',mimeType:'application/xml',buffer,sourceSha256:hash(buffer),notes,units:2 * (notes + 1)};
}

function safeStatePath(value) {
  h.assert(typeof value === 'string' && path.isAbsolute(value), 'An absolute private lifecycle state file is required');
  const resolved = path.resolve(value);
  h.assert(['/tmp/','/workspace/'].some(root => resolved.startsWith(root)), 'Lifecycle state must stay in the disposable workspace');
  const parent = fs.lstatSync(path.dirname(resolved));
  h.assert(parent.isDirectory() && !parent.isSymbolicLink(), 'The state directory must be a real directory');
  const physical = fs.realpathSync(path.dirname(resolved));
  h.assert(['/tmp/','/workspace/'].some(root => (physical + '/').startsWith(root)), 'State directory resolves outside the disposable workspace');
  return resolved;
}

function readState(file) {
  const info = fs.lstatSync(file);
  h.assert(info.isFile() && !info.isSymbolicLink() && (info.mode & 0o077) === 0 && info.size <= 16384,
    'Lifecycle state must be a private regular bounded file');
  const state = JSON.parse(fs.readFileSync(file,'utf8'));
  h.assert(state.version === 1 && UUID.test(state.attempt) && SHA.test(state.sourceSha256)
    && SHA.test(state.optionsDigest) && Array.isArray(state.completed), 'The retained lifecycle state is invalid');
  if (state.runId) h.assert(UUID.test(state.runId), 'The retained accepted handle is invalid');
  if (state.uploadId) h.assert(UUID.test(state.uploadId), 'The retained upload handle is invalid');
  return state;
}

function writeState(file, state, initial = false) {
  const bytes = JSON.stringify(state) + '\n';
  h.assert(Buffer.byteLength(bytes) <= 16384, 'Lifecycle state exceeded its bounded budget');
  const target = initial ? file : file + '.next';
  const fd = fs.openSync(target,fs.constants.O_WRONLY | fs.constants.O_CREAT | fs.constants.O_EXCL | fs.constants.O_NOFOLLOW,0o600);
  try { fs.writeFileSync(fd,bytes); fs.fsyncSync(fd); } finally { fs.closeSync(fd); }
  if (!initial) fs.renameSync(target,file);
  const directory = fs.openSync(path.dirname(file),fs.constants.O_RDONLY);
  try { fs.fsyncSync(directory); } finally { fs.closeSync(directory); }
}

function bindState(state, options, source, artifact) {
  h.assert(state.sourceId && fixture(state.sourceId,source.notes,state.notePadding || 0).sourceSha256 === source.sourceSha256
    && state.sourceSha256 === source.sourceSha256 && state.optionsDigest === optionsDigest(options)
    && JSON.stringify(state.options) === JSON.stringify(options) && state.artifactSha256 === artifact,
    'Retained source, command, or artifact differs; never create a replacement acceptance');
}

function progress(sql, runId) {
  h.assert(UUID.test(runId), 'A verified accepted handle is required');
  const [row] = sql.rows(`SELECT expected_units,
    (SELECT COUNT(*) FROM cds_import_checkpoint c WHERE c.first_run_id=r.id),
    (SELECT COUNT(*) FROM cds_import_unit u WHERE u.run_id=r.id AND u.state NOT IN ('COMMITTED','ALREADY_COMPLETE')),
    (SELECT COUNT(*) FROM cds_import_patient p JOIN demographic d ON d.demographic_no=p.target_demographic_no WHERE p.run_id=r.id),
    (SELECT COUNT(*) FROM cds_import_patient p WHERE p.run_id=r.id AND p.held=1),
    (SELECT COUNT(*) FROM casemgmt_note n JOIN cds_import_patient p ON n.demographic_no=p.target_demographic_no WHERE p.run_id=r.id)
    FROM cds_import_run r WHERE id=${q(runId)}`);
  h.assert(row && row.length === 6, 'Accepted progress is unavailable');
  const values = row.map(Number);
  h.assert(values.every(Number.isSafeInteger), 'Durable progress is malformed');
  return Object.fromEntries(['expected','checkpoints','pending','patients','holds','notes'].map((key,i) => [key,values[i]]));
}

function requirePending(value) {
  h.assert(value.expected === 514 && value.checkpoints > 0 && value.checkpoints < value.expected
    && value.pending > 0 && value.patients === 1 && value.holds === 1 && value.notes > 0,
    'The actual written/pending/held progress window was missed; this fault was not proved');
}

async function waitPending(sql, id, timeout = 120000) {
  const until = Date.now() + timeout;
  while (Date.now() < until) {
    const value = progress(sql,id);
    if (value.checkpoints > 0 && value.notes > 0) { requirePending(value); return value; }
    h.assert(value.checkpoints < 514, 'Import completed before the fault window');
    await new Promise(resolve => setTimeout(resolve,100));
  }
  throw Error('No durable native/checkpoint progress before the fault deadline');
}

function assertCompletion(value, expected) {
  const summary = value.summary;
  h.assert(value.job.id === expected.runId && value.job.optionsDigest === expected.optionsDigest
    && value.job.sourceDigest === expected.sourceDigest && value.job.rulesetDigest === expected.rulesetDigest
    && value.job.manifestDigest === expected.manifestDigest, 'Completion changed accepted immutable provenance');
  for (const key of ['clinicalComplete','discoveryComplete','reportsDelivered','cleanupComplete','cleanComplete'])
    h.assert(summary[key] === true, 'Completion evidence missing: ' + key);
  h.assert(summary.resumeRequested === false && summary.maintenanceReleasePending === false
    && summary.maintenanceReleaseState === 'RELEASED' && summary.pendingUnits === 0
    && summary.inventoryState === 'SEALED' && summary.expectedUnits === expected.units
    && summary.sourcePatients === 1 && summary.expectedPatients === expected.copies && summary.targetCopiesPlanned === expected.copies,
    'Completion still has pending work, a hold, or unreleased maintenance');
}

function assertScopedFault(recorder, fault) {
  if (!fault) {h.assertStrictPage(recorder);return;}
  h.assert(fault.kind==='nginx','Only the genuine Nginx fault has a scoped expectation');
    h.assert(fault.accepted === true && fault.status === 504 && fault.proxyVerified === true
      && UUID.test(fault.runId) && recorder.badResponses.filter(entry => entry === fault.badResponse).length === 1,
      'The timeout was not the exact real-proxy response following native acceptance');
    const console = recorder.consoleIssues.filter(entry => !(entry.location?.url === fault.request.url()
      && /^Failed to load resource: the server responded with a status of 504(?: \([^\r\n]{0,40}\))?$/.test(entry.text)));
    h.assert(recorder.consoleIssues.length-console.length <= 1,'The scoped proxy emitted unexpected duplicate errors');
    const failures=recorder.requestFailures.filter(entry => !(entry.requestRef?.deref()===fault.request && entry.errorText==='net::ERR_ABORTED'));
    h.assert(recorder.requestFailures.length-failures.length<=1,'The exact proxy error response had duplicate cancellations');
    h.assertStrictPage({...recorder,badResponses:recorder.badResponses.filter(entry => entry !== fault.badResponse),consoleIssues:console,requestFailures:failures});
}

async function waitPrivate(file, milliseconds = 20000, child) {
  const until=Date.now()+milliseconds;
  while(Date.now()<until) {
    if(fs.existsSync(file))return privateJson(file);
    h.assert(!child || child.exitCode === null,'The owned relay stopped before its receipt');
    await new Promise(resolve => setTimeout(resolve,50));
  }
  throw Error('The owned relay receipt deadline elapsed; retain the original submission');
}

async function startRelay(env,stateFile,state) {
  const prefix=JSON.parse(env.CDS_LIFECYCLE_PROXY_RELAY_ARGV || 'null');
  h.assert(Array.isArray(prefix) && prefix.length>=2 && prefix.length<=16
    && prefix.every(value => typeof value==='string' && value.length>0 && value.length<4096),'An explicit owned relay executable argv is required');
  const root=fs.mkdtempSync(path.join(path.dirname(stateFile),'proxy-'));
  const child=spawn(prefix[0],[...prefix.slice(1),'http://127.0.0.1:8080/',root,state.attempt,'/carlos','18086'],{stdio:['ignore','pipe','pipe']});
  let output=0;
  for(const stream of [child.stdout,child.stderr])stream.on('data',bytes => {output+=bytes.length;if(output>4096)child.kill('SIGTERM');});
  const closed=new Promise(resolve => child.once('close',resolve));
  const relay={root,child,closed,file:name => path.join(root,state.attempt + '.' + name + '.json')};
  try {
    await new Promise((resolve,reject) => {child.once('spawn',resolve);child.once('error',() => reject(Error('The owned relay process could not start')));});
    const ready=await waitPrivate(relay.file('ready'),20000,child);
    h.assert(ready.ready===true && ready.attempt===state.attempt && ready.port===18086,'The exact private relay did not start');
    return relay;
  } catch(failure) {
    publish(relay.file('stop'),{attempt:state.attempt});
    await Promise.race([closed,new Promise(resolve => setTimeout(resolve,10000))]);
    throw failure;
  }
}

function expireSession(command, sessionId) {
  h.assert(Array.isArray(command) && command.length >= 2 && command.length <= 16
    && command.every(part => typeof part === 'string' && part.length > 0 && part.length < 4096),
    'An explicit native Tomcat session-expiry helper argv is required');
  h.assert(/^[A-Fa-f0-9]{16,128}(?:\.[A-Za-z0-9_-]{1,64})?$/.test(sessionId), 'The selected synthetic session identity is invalid');
  let result;
  try { result = execFileSync(command[0],command.slice(1),{input:sessionId + '\n',encoding:'utf8',timeout:30000,
    maxBuffer:4096,stdio:['pipe','pipe','pipe']}); }
  catch (_) { throw Error('The native synthetic-session expiry helper failed; accepted work was retained'); }
  const receipt = JSON.parse(result);
  h.assert(receipt.expired === true && receipt.scope === 'selected-HttpSession' && receipt.naturalTimeout === false
    && receipt.atomicReference === true,
    'The native session expiry receipt is incomplete');
}

function preflightSession(command) {
  h.assert(Array.isArray(command) && command.length>=2 && command.length<=16
    && command.every(part => typeof part==='string' && part.length>0 && part.length<4096),
    'An explicit non-mutating atomic session-helper preflight argv is required');
  let output;
  try {output=execFileSync(command[0],command.slice(1),{input:'',encoding:'utf8',timeout:30000,maxBuffer:4096,stdio:['pipe','pipe','pipe']});}
  catch(_){throw Error('Atomic native session expiration is unsupported; no new acceptance is allowed');}
  const receipt=JSON.parse(output);
  h.assert(receipt.preflight===true && receipt.supported===true && receipt.atomicReference===true,
    'Atomic native session preflight did not prove support before acceptance');
}

async function run(env = process.env) {
  if (env.CDS_LIFECYCLE_EXECUTE !== 'true') {
    console.log('NOT_EXECUTED: lifecycle accepts are held; explicit CDS_LIFECYCLE_EXECUTE=true and reviewed artifact/context are required');
    return {outcome:'NOT_EXECUTED'};
  }
  let browser; let sql; let relay;
  const stateFile = safeStatePath(env.CDS_LIFECYCLE_STATE_FILE);
  const synthetic = env.CDS_LIFECYCLE_MODE === 'synthetic';
  h.assert(synthetic || !env.CDS_LIFECYCLE_MODE || env.CDS_LIFECYCLE_MODE === 'faults', 'Unknown lifecycle scenario');
  const options = optionsFrom(env,synthetic);
  h.assert(SHA.test(env.CDS_LIFECYCLE_ARTIFACT_SHA256 || ''), 'A frozen reviewed artifact SHA256 is required');
  h.assert(env.CDS_LIFECYCLE_ARTIFACT_FILE && hashFile(env.CDS_LIFECYCLE_ARTIFACT_FILE).sha256 === env.CDS_LIFECYCLE_ARTIFACT_SHA256,
    'The exact reviewed artifact bytes must be pinned before any acceptance');
  h.assert(path.isAbsolute(env.CDS_LIFECYCLE_SERVER_ROOT || '') && path.isAbsolute(env.CDS_LIFECYCLE_LOCAL_ROOT || '')
    && fs.statSync(env.CDS_LIFECYCLE_LOCAL_ROOT).isDirectory(), 'Declare the exact mounted server/local evidence roots before acceptance');
  if (synthetic) h.assert(/^[1-8]$/.test(env.CDS_LIFECYCLE_EXPECTED_COPIES || ''),
    'The authorized course copy count is required before acceptance');
  const expiry = synthetic ? null : JSON.parse(env.CDS_LIFECYCLE_SESSION_EXPIRE_ARGV || 'null');
  if (!synthetic) {
    h.assert(Array.isArray(expiry), 'Native session expiry must be configured before any acceptance');
    preflightSession(JSON.parse(env.CDS_LIFECYCLE_SESSION_PREFLIGHT_ARGV || 'null'));
  }
  let state = fs.existsSync(stateFile) ? readState(stateFile) : null;
  const notePadding=state?.notePadding ?? (synthetic ? 0 : 49152);
  const source = fixture(state?.sourceId || 'LIFE' + randomUUID().replaceAll('-','').slice(0,16),synthetic ? 1 : 256,notePadding);
  assertSchemaValid(source.buffer.toString('utf8'));
  if (state) bindState(state,options,source,env.CDS_LIFECYCLE_ARTIFACT_SHA256);
  else {
    state = {version:1,attempt:randomUUID(),sourceId:source.name.slice(0,-4),sourceSha256:source.sourceSha256,
      artifactSha256:env.CDS_LIFECYCLE_ARTIFACT_SHA256,options,optionsDigest:optionsDigest(options),notePadding,completed:[]};
    writeState(stateFile,state,true);
  }
  const save = () => writeState(stateFile,state);
  return h.runCheck({name:'demographic-cds-lifecycle',async run() {
    const proxy=synthetic ? null : boundedJson(env.CDS_LIFECYCLE_PROXY_CONTRACT_FILE).value;
    if(proxy)h.assert(proxy.listener==='http://127.0.0.1:18085/carlos' && proxy.upstream==='http://127.0.0.1:18086'
      && proxy.proxyReadTimeoutSeconds===15 && proxy.blockingModSecurity===true
      && typeof proxy.proxyHeader==='string' && /^[a-f0-9]{32}$/.test(proxy.proxyToken),'The exact real Nginx fault listener contract is unavailable');
    const config = h.readConfig({env,baseUrl:proxy?.listener || env.BASE_URL}); sql = h.createSqlRunner(config.mysql);
    h.assert(['carlos_cds_test','cds_full_test','cds_ledger_test'].includes(sql.value('SELECT DATABASE()')),
      'Lifecycle clinical writes require the disposable reference database');
    const owner = 'web-' + sql.value('SELECT security_no FROM security WHERE user_name=' + q(config.testUser));
    h.assert(/^web-[1-9][0-9]*$/.test(owner), 'The native synthetic operator is unavailable');
    if (state.owner) h.assert(state.owner === owner, 'The retained acceptance belongs to another native operator');
    state.owner = owner; save();
    if(!synthetic)relay=await startRelay(env,stateFile,state);
    if (synthetic) {
      h.assert(sql.value(`SELECT COUNT(DISTINCT pp.provider_no) FROM program_provider pp
        JOIN secRole r ON r.role_no=pp.role_id JOIN provider p ON p.provider_no=pp.provider_no
        WHERE pp.program_id=${options.courseId} AND LOWER(r.role_name)='student' AND p.status='1'`)
        === env.CDS_LIFECYCLE_EXPECTED_COPIES, 'The actual authorized course membership differs from the reviewed copy count');
    }
    browser = await h.launchBrowser(config);
    let context;
    let current;
    const open = async runId => {
      const recorder = h.createRecorder();
      const page = await context.newPage(); h.wireStrictPage(page,'cds-lifecycle',recorder);
      const responses=new WeakMap();
      page.on('response',response => {
        if(response.status()>=400) {
          const raw=recorder.badResponses.at(-1);
          if(raw?.status===response.status() && raw.url===response.url())responses.set(response.request(),raw);
        }
      });
      const transport = await installCdsMetadataTransportObserver(page,recorder,{baseUrl:config.baseUrl});
      const response = await page.goto(h.appUrl(config.baseUrl,'/form/importUpload' + (runId ? '?runId=' + runId : '')));
      h.assert(response.status() === 200, 'Authenticated lifecycle form is unavailable');
      if(proxy)h.assert(response.headers()[proxy.proxyHeader.toLowerCase()]===proxy.proxyToken,'Browser form bypassed the approved real proxy');
      await page.locator('#cdsImport').waitFor();
      h.assert(await page.locator('#cdsImport').getAttribute('data-owner') === owner, 'The form operator differs from the retained native owner');
      if (runId) await page.waitForFunction(id => {
        try { return JSON.parse(document.getElementById('runSummary').textContent).job.id === id; }
        catch (_) { return false; }
      },runId,{timeout:30000});
      const prover = createCdsMetadataProver({frame:page,config,sql,readApi:importApi});
      const qualify = async fault => {
        await page.waitForLoadState('networkidle',{timeout:10000});
        for (const evidence of await transport.qualifyPending(prover)) console.log('CDS_METADATA_EOF_EVIDENCE',JSON.stringify(evidence));
        assertScopedFault(recorder,fault);
      };
      return {page,recorder,transport,qualify,responses};
    };
    const authenticate = async () => {
      context = await h.newContext(browser,config);
      const login = h.createRecorder(); await h.login(context,config,login);
      const closed = login.badResponses.some(entry => entry.status === 503 && entry.resourceType === 'document'
        && new URL(entry.url).pathname === new URL(config.baseUrl).pathname.replace(/\/$/,'') + '/provider/providercontrol');
      // The barrier may release while login is in flight; validate the exact actual native
      // schedule response rather than inventing an atomic observation across two requests.
      assertRetainedLogin(login,config.baseUrl,closed);
    };
    await authenticate(); current = await open(state.runId);
    const setOptions = async page => {
      await page.locator('#cdsSource').selectOption(options.sourceNamespace + '|' + options.sourceProfile);
      for (const name of ['providerNo','programId','facilityId']) await page.locator('#' + name).fill(String(options[name]));
      await page.locator('#mergeExisting').uncheck(); await page.locator('#matchProviderNames').uncheck();
      await page.locator('#dryRun').uncheck();
      if (await page.locator('#synthetic').count()) await page.locator('#synthetic').setChecked(synthetic);
      if (synthetic) { await page.locator('#courseId').fill(String(options.courseId)); await page.locator('#timeshiftInDays').fill(String(options.timeshiftInDays)); }
    };
    const bindAccepted = async id => {
      h.assert(UUID.test(id), 'The upstream acceptance has no durable run handle');
      const response = await importApi(current.page,'cdsImportStatus',{runId:id});
      h.assert(response.status === 200 && response.body?.apiVersion === 2 && response.body.job?.id === id
        && response.body.job.operator === owner && response.body.job.optionsDigest === state.optionsDigest,
        'Durable acceptance does not match the actual nondefault UI command/operator');
      const job = response.body.job;
      if (state.runId) h.assert(state.runId === id && state.sourceDigest === job.sourceDigest
        && state.rulesetDigest === job.rulesetDigest, 'Reconnect changed the accepted run/source/rules');
      Object.assign(state,{runId:id,sourceDigest:job.sourceDigest,rulesetDigest:job.rulesetDigest,
        manifestDigest:job.manifestDigest || state.manifestDigest}); save();
      return response.body;
    };
    if (state.uploadId && !state.runId) {
      const recovered = await importApi(current.page,'cdsImportStatus',{section:'upload',uploadId:state.uploadId});
      h.assert(recovered.status === 200 && recovered.body?.apiVersion === 2 && UUID.test(recovered.body.upload?.runId || ''),
        'Submission outcome is not yet known: retain the upload handle and do not resubmit');
      await bindAccepted(recovered.body.upload.runId);
    }
    let fault;
    if (!state.runId) {
      h.assert(!state.submitRequested, 'A prior submission may be accepted; do not resubmit it');
      await setOptions(current.page);
      if (synthetic) {
        await current.page.route('**/form/cdsImportSubmit',async route => {
          try {
            const request = route.request();
            const params = new URLSearchParams(request.postData());
            h.assert(!state.submitRequested && params.get('apiVersion') === '2' && UUID.test(params.get('uploadId') || ''),
              'Synthetic UI attempted an unversioned or duplicate acceptance');
            state.submitRequested = true; state.uploadId = params.get('uploadId'); save();
            await route.continue();
          } catch (_) { await route.abort('failed'); }
        });
        const result = await uploadAndAwaitRun({config},current.page,current.page,source);
        await current.page.unroute('**/form/cdsImportSubmit');
        state.uploadId = result.uploadId; await bindAccepted(result.id);
      } else {
        let selectedRequest;
        await current.page.route('**/form/cdsImportSubmit',async route => {
            try {
              h.assert(!state.submitRequested, 'The UI attempted another submission after an unknown acceptance');
              const request = route.request();
              const params = new URLSearchParams(request.postData());
              h.assert(params.get('apiVersion') === '2', 'Actual UI submission did not use contract2');
              const uploadId = params.get('uploadId'); h.assert(UUID.test(uploadId || ''), 'Submission has no owned upload');
              state.submitRequested = true; state.uploadId = uploadId; save();
              selectedRequest=request;
              publish(relay.file('arm'),{attempt:state.attempt,uploadId,bodySha256:hash(Buffer.from(request.postData()))});
              await route.continue();
            } catch (_) {await route.abort('failed');}
        });
        const timedOut=current.page.waitForResponse(response => response.request()===selectedRequest && response.status()===504,{timeout:120000});
        timedOut.catch(() => {});
        await current.page.locator('#importFile').setInputFiles(source);
        await current.page.locator('#cdsStart').click();
        const accepted=await waitPrivate(relay.file('accepted'),120000,relay.child);
        h.assert(accepted.attempt===state.attempt && accepted.uploadId===state.uploadId && accepted.status===202
          && accepted.withheld===true && accepted.bodySha256===hash(Buffer.from(selectedRequest.postData())),
          'The real proxy relay did not preserve the exact accepted request');
        await bindAccepted(accepted.runId);
        state.proxyProgress=await waitPending(sql,state.runId);save();
        const timeout=await timedOut;
        const finished=await timeout.finished();
        h.assert((finished===null || finished.message==='net::ERR_ABORTED')
          && timeout.headers()[proxy.proxyHeader.toLowerCase()]===proxy.proxyToken,
          'The response was not the actual Nginx504 from the approved listener');
        state.proxyAfter=progress(sql,state.runId);requirePending(state.proxyAfter);save();
        fault={kind:'nginx',request:selectedRequest,accepted:true,runId:state.runId,status:504,proxyVerified:true,
          badResponse:current.responses.get(selectedRequest)};
        state.proxyEvidence={acceptedSha256:hashFile(relay.file('accepted'),16384).sha256,
          relayAttempt:state.attempt,proxyTimeoutSeconds:15,clientStatus:504};save();
        await current.page.waitForFunction(() => !document.getElementById('cdsOptions').disabled);
        await current.page.unroute('**/form/cdsImportSubmit');
        const ownership = await importApi(current.page,'cdsImportStatus',{section:'upload',uploadId:state.uploadId});
        h.assert(ownership.body?.apiVersion === 2 && ownership.body.upload?.runId === state.runId,
          'The owner-scoped acceptance lookup did not recover the lost ACK');
        await current.qualify(fault);
        state.completed.push('nginx-accepted-ack-timeout'); save();
        await current.page.locator('#runId').fill(state.runId); await current.page.locator('#openRun').click();
        await current.page.waitForFunction(id => {
          try { return JSON.parse(document.getElementById('runSummary').textContent).job.id === id; }
          catch (_) { return false; }
        },state.runId,{timeout:30000});
      }
    }
    await bindAccepted(state.runId);
    if (!synthetic && !state.completed.includes('tab-close')) {
      state.tabProgress = await waitPending(sql,state.runId); save();
      await current.qualify(fault); await current.page.close();
      requirePending(progress(sql,state.runId));
      assertScopedFault(current.recorder,fault);
      current = await open(state.runId); await bindAccepted(state.runId);
      await current.qualify();
      state.completed.push('tab-close'); save();
    }
    if (!synthetic && !state.completed.includes('native-session-expiry')) {
      state.expiryProgress = await waitPending(sql,state.runId); save();
      await current.qualify();
      const csrf = await current.page.locator('input[name="CSRF-TOKEN"]').first().inputValue();
      await current.page.close();
      const cookies = await context.cookies(config.baseUrl);
      const sessions = cookies.filter(cookie => cookie.name === 'JSESSIONID');
      h.assert(sessions.length === 1, 'The synthetic browser has no unique selected native session');
      expireSession(expiry,sessions[0].value);
      state.expiryAfter = progress(sql,state.runId); requirePending(state.expiryAfter); save();
      const refused = await context.request.get(h.appUrl(config.baseUrl,'/form/cdsImportStatus?apiVersion=2&runId=' + state.runId),
        {headers:{'CSRF-TOKEN':csrf,'X-Requested-With':'XMLHttpRequest'},maxRedirects:0,failOnStatusCode:false});
      h.assert([401,302].includes(refused.status()), 'The expired native session retained protected import access');
      await context.close(); await authenticate(); current = await open(state.runId);
      await bindAccepted(state.runId);
      state.completed.push('native-session-expiry'); save();
    }
    const value = await awaitRunReceipt(current.page,state.runId,300000);
    if (!synthetic) h.assert(['nginx-accepted-ack-timeout','tab-close','native-session-expiry'].every(stage => state.completed.includes(stage)),
      'Accepted work recovered, but one required browser fault lacks its actual evidence');
    const copies = synthetic ? Number(env.CDS_LIFECYCLE_EXPECTED_COPIES) : 1;
    h.assert(Number.isSafeInteger(copies) && copies > 0 && copies <= 8, 'Explicit approved copy count is required');
    // Inventory digest is sealed after acceptance; bind it once, then retain it on every retry.
    if (!state.manifestDigest) { state.manifestDigest = value.job.manifestDigest; save(); }
    assertCompletion(value,{...state,units:source.units * copies,copies});
    const stored = progress(sql,state.runId);
    h.assert(stored.expected === source.units * copies && stored.checkpoints === stored.expected
      && stored.pending === 0 && stored.patients === copies && stored.holds === 0 && stored.notes === source.notes * copies,
      'Native/chart/checkpoint completeness differs from the accepted source inventory');
    const shifted = text => {
      const value = new Date(text + 'T12:00:00Z'); value.setUTCDate(value.getUTCDate() + options.timeshiftInDays);
      return value.toISOString().slice(0,10);
    };
    h.assert(sql.value(`SELECT COUNT(*) FROM cds_import_patient p JOIN demographic d ON d.demographic_no=p.target_demographic_no
      WHERE p.run_id=${q(state.runId)} AND CONCAT(d.year_of_birth,'-',LPAD(d.month_of_birth,2,'0'),'-',LPAD(d.date_of_birth,2,'0'))=${q(shifted('1971-03-04'))}`) === String(copies),
      'The actual native birth date did not preserve the selected synthetic shift');
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note n JOIN cds_import_patient p ON n.demographic_no=p.target_demographic_no
      WHERE p.run_id=${q(state.runId)} AND DATE(n.observation_date)=${q(shifted('2024-03-10'))}
      AND n.provider_no='' AND n.signed=0 AND COALESCE(n.signing_provider_no,'')='' AND EXISTS(SELECT 1 FROM admission a WHERE a.client_id=p.target_demographic_no
      AND a.program_id=n.program_no AND a.admission_status='current')`) === String(source.notes * copies),
      'Actual native note dates, truthful authorship, or explicit per-copy program context differ');
    if (!synthetic) h.assert(sql.value(`SELECT COUNT(*) FROM admission a JOIN cds_import_patient p ON p.target_demographic_no=a.client_id
      WHERE p.run_id=${q(state.runId)} AND a.program_id=${options.programId} AND a.admission_status='current'`) === '1',
      'The nondefault UI program was ignored by actual native admission');
    if(synthetic)h.assert(sql.value(`SELECT COUNT(DISTINCT p.target_demographic_no) FROM cds_import_patient p
      JOIN admission a ON a.client_id=p.target_demographic_no AND a.admission_status='current'
      JOIN program personal ON personal.id=a.program_id
      JOIN program_provider pp ON personal.name=CONCAT('program',pp.provider_no) AND pp.program_id=${options.courseId}
      JOIN secRole r ON r.role_no=pp.role_id AND LOWER(r.role_name)='student'
      JOIN provider student ON student.provider_no=pp.provider_no AND student.status='1'
      WHERE p.run_id=${q(state.runId)} AND personal.programStatus='active' AND personal.facilityId=${options.facilityId}
      AND p.copy_key=CONCAT('course-',${options.courseId},'-student-',pp.provider_no,'-program-',personal.id)`)
      === String(copies),'Synthetic web controls did not create exactly the eligible learner/personal-program copies');
    h.assert(sql.value('SELECT COUNT(*) FROM cds_import_run WHERE operator=' + q(owner)
      + ' AND submission_key=(SELECT submission_key FROM cds_import_upload WHERE id=' + q(state.uploadId) + ')') === '1',
      'A lost ACK or reconnect created another accepted run');
    const reports = protectedEvidence(sql,state,env);
    for (const [selector,key] of [['eventReport','event'],['errorReport','error'],['receiptReport','completion']]) {
      const transfer = current.page.waitForEvent('download',{timeout:30000});
      await current.page.locator('#' + selector).click();
      const download = await transfer;
      h.assert(await download.failure() === null, 'A protected report download did not finish');
      const actual = hashFile(await download.path(),8 * 1024 * 1024);
      h.assert(actual.bytes === reports[key].bytes && actual.sha256 === reports[key].sha256,
        'Browser-delivered protected report differs from its durable physical manifest');
    }
    const receipt = await importApi(current.page,'cdsImportReport',{runId:state.runId,type:'receipt'});
    h.assert(receipt.status === 200 && receipt.body?.cleanComplete === true
      && receipt.body.optionsDigest === state.optionsDigest && receipt.body.sourceDigest === state.sourceDigest
      && receipt.body.manifestDigest === state.manifestDigest && receipt.body.rulesetDigest === state.rulesetDigest,
      'Protected completion report differs from the accepted command/source/inventory');
    await current.qualify();
    state.completed.push('native-reports-clean-complete'); save();
    console.log('LIFECYCLE_EVIDENCE',JSON.stringify({runId:state.runId,artifactSha256:state.artifactSha256,
      sourceSha256:state.sourceSha256,optionsDigest:state.optionsDigest,sourceDigest:state.sourceDigest,
      sourceBytes:source.buffer.length,notePayloadBytes:state.notePadding,
      manifestDigest:state.manifestDigest,rulesetDigest:state.rulesetDigest,completed:state.completed,native:stored,reports,
      sessionExpiry:'selected-native-HttpSession-administrative-expiry-not-inactivity-timer'}));
  },async cleanup() {
    const failures=[];
    try {if(browser)await browser.close();} catch(failure){failures.push(failure);}
    try {if(sql)sql.dispose();} catch(failure){failures.push(failure);}
    try {
      if(relay) {
        publish(relay.file('stop'),{attempt:state.attempt});
        const stopped=await Promise.race([relay.closed.then(() => true),new Promise(resolve => setTimeout(() => resolve(false),10000))]);
        h.assert(stopped,'The owned proxy relay did not stop; retain its private control directory');
      }
    } catch(failure){failures.push(failure);}
    if(failures.length)throw new AggregateError(failures,'Lifecycle cleanup is incomplete; accepted work and controls were retained');
    // Never delete accepted jobs, charts, immutable source, checkpoints or recovery state.
  }});
}

if (require.main === module) run().then(result => { if (result?.outcome === 'NOT_EXECUTED') process.exitCode = 2; })
  .catch(() => { console.error('FAIL lifecycle gate: accepted state retained; inspect protected artifacts'); process.exitCode = 1; });
module.exports = {run,optionsFrom,optionsDigest,fixture,readState,writeState,bindState,progress,requirePending,assertCompletion,assertScopedFault,expireSession,preflightSession,hashFile,evidencePath,protectedEvidence,contractApi};
