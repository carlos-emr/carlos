#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

// Manual disposable-database gate. An accepted handle is retained even when this process fails.
const fs = require('node:fs');
const path = require('node:path');
const {createHash, randomUUID} = require('node:crypto');
const {fileURLToPath} = require('node:url');
const {execFileSync,spawn} = require('node:child_process');
const h = require('./lib/playwright-harness');
const {assertRetainedLogin} = require('./demographic-cds-retained-recovery-playwright-checks');
const {assertPrimaryJobStatus, importApi:sharedImportApi, awaitRunReceipt, uploadAndAwaitRun, assertSchemaValid} = require('./demographic-cds-import-playwright-checks');
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

function acceptedDescriptorPath(value) {
  h.assert(typeof value === 'string' && !/[\u0000\r\n]/.test(value), 'Accepted descriptor path is invalid');
  if (path.isAbsolute(value)) {
    h.assert(!value.startsWith('//'), 'Accepted descriptor path must be local');
    return value;
  }
  h.assert(/^file:\/\/\/(?!\/)/.test(value) && !/[\u0000-\u0020?#]/.test(value)
    && !/%(?:2f|5c)/i.test(value), 'Accepted descriptor file URL must be local and unambiguous');
  const parsed = new URL(value);
  h.assert(parsed.protocol === 'file:' && parsed.hostname === '' && parsed.username === '' && parsed.password === '',
    'Accepted descriptor file URL must have no authority');
  const decoded = fileURLToPath(parsed);
  h.assert(path.isAbsolute(decoded) && !/[\u0000\r\n]/.test(decoded), 'Accepted descriptor file URL path is invalid');
  return decoded;
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
  const journal = boundedBytes(evidencePath(path.join(path.dirname(acceptedDescriptorPath(command.input)),'chunks.bin'),env),52 * 256);
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
  const snapshotRoot = path.join(acceptedDescriptorPath(document.jobRoot),'snapshot');
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

function assertAcceptance(response,expected,durableOwner) {
  // CdsWebService.job deliberately omits operator/provider fields. Ownership is proven by
  // the actual owner-only endpoint plus an independent exact durable scalar query.
  h.assert(response.status===200 && response.body?.apiVersion===2 && response.body.job?.id===expected.runId
    && response.body.job.sourceNamespace===expected.options.sourceNamespace
    && response.body.job.profile===expected.options.sourceProfile
    && response.body.job.optionsDigest===expected.optionsDigest && durableOwner===expected.owner,
    'Durable acceptance does not match the actual nondefault UI command/operator/source');
}

function assertSingleUploadAcceptance(sql,owner,uploadId) {
  h.assert(sql.value('SELECT COUNT(*) FROM cds_import_run WHERE operator=' + q(owner)
    + " AND submission_key=(SELECT CONCAT(submission_key,'-import') FROM cds_import_upload WHERE id=" + q(uploadId) + ')') === '1',
    'A lost ACK or reconnect created another accepted run, or its import acceptance is missing');
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

async function boundedLifecycleAction(action, milliseconds, message) {
  let timer;
  try {
    return await Promise.race([Promise.resolve().then(action),new Promise((_resolve,reject)=>{
      timer=setTimeout(()=>reject(Error(message)),milliseconds);
    })]);
  } finally { clearTimeout(timer); }
}

function createLifecyclePageCloser(page,recorder,{baseUrl,auxiliaryLogin=false,retainedLoginClosed=false}) {
  const base=new URL(String(baseUrl)),form=base.pathname.replace(/\/$/,'')+'/form';
  const inflight=new Set(),captured=new Set(),terminal=new Set();
  let closing=false,closed=false,started=false,overflow=false,cursor=0,selectedRun,fault;
  const eligible=request=>{
    try {
      const url=new URL(request.url());
      if(request.method()!=='GET' || !['fetch','xhr'].includes(request.resourceType())
        || request.frame()!==page.mainFrame() || request.frame().page()!==page || url.origin!==base.origin
        || url.username || url.password || url.hash)return false;
      if(auxiliaryLogin)return url.pathname===base.pathname.replace(/\/$/,'')+'/status/SessionHeartbeat' && !url.search;
      return [form+'/cdsImportStatus',form+'/cdsImportInspect'].includes(url.pathname)
        && url.searchParams.getAll('apiVersion').length===1 && url.searchParams.get('apiVersion')==='2'
        && url.searchParams.getAll('runId').length===1 && url.searchParams.get('runId')===selectedRun;
    } catch(_){return false;}
  };
  const capture=request=>{if(eligible(request)){if(captured.size>=128)overflow=true;else captured.add(request);}};
  const onRequest=request=>{
    if(inflight.size>=256)overflow=true;else inflight.add(request);
    if(closing)capture(request);
  };
  const onTerminal=request=>{inflight.delete(request);if(captured.has(request))terminal.add(request);};
  const onClosed=()=>{closing=false;closed=true;};
  page.on('request',onRequest);page.on('requestfinished',onTerminal);page.on('requestfailed',onTerminal);page.on('close',onClosed);
  const dispose=()=>{page.off('request',onRequest);page.off('requestfinished',onTerminal);page.off('requestfailed',onTerminal);page.off('close',onClosed);inflight.clear();};
  return {dispose,async close({runId,expectedFault,timeoutMillis=30000}={}) {
    h.assert(!started && !page.isClosed() && (auxiliaryLogin || UUID.test(runId || '')),'Intentional close requires one exact owned page/run');
    started=true;selectedRun=runId;fault=expectedFault;
    const audit=()=>{
      h.assert(closed && page.isClosed() && !overflow,'Intentional close did not prove bounded actual closure');
      const cancellations=new Set();
      const failures=recorder.requestFailures.filter((entry,index)=>{
        const request=entry.requestRef?.deref();
        if(index<cursor || entry.errorText!=='net::ERR_ABORTED' || !captured.has(request))return true;
        h.assert(!cancellations.has(request),'Intentional close emitted duplicate cancellations');cancellations.add(request);return false;
      });
      const view={...recorder,requestFailures:failures};
      if(auxiliaryLogin)assertRetainedLogin(view,baseUrl,retainedLoginClosed);else assertScopedFault(view,fault);
      const observed=new Set([...terminal,...cancellations]),retired=[...captured].filter(request=>!observed.has(request));
      const evidence={classification:'CONTROLLED_PAGE_CLOSED',requests:captured.size,cancellations:cancellations.size,
        observedRequestTerminals:observed.size,retiredByObservedPageClosure:retired.length};
      // Chromium may retire the closed target without any per-request terminal callback.
      // Those exact requests remain interrupted/unobserved, never HTTP/EOF successes.
      Object.defineProperty(evidence,'retiredRequestRefs',{value:retired.map(request=>new WeakRef(request))});
      return evidence;
    };
    try {
      h.assert(!overflow,'Intentional close request inventory exceeded its fixed capacity');
      for(const request of inflight)capture(request);
      cursor=recorder.requestFailures.length;closing=true;
      const close=page.close(); // Capture, cursor and actual close share one Node turn.
      await boundedLifecycleAction(()=>close,timeoutMillis,
        'Owned lifecycle page close deadline elapsed; closure is unknown and accepted state retained');
      await new Promise(resolve=>setImmediate(resolve));
      const evidence=audit();return {audit,evidence};
    } finally {closing=false;dispose();}
  }};
}

async function completedLifecycleDownload(download,timeoutMillis=30000) {
  return boundedLifecycleAction(async()=>{
    h.assert(await download.failure()===null,'A protected report download did not finish');
    const file=await download.path();h.assert(typeof file==='string' && path.isAbsolute(file),'Protected report download has no completed private path');
    return file;
  },timeoutMillis,'Protected report download completion deadline elapsed; accepted state and reports retained');
}

async function assertExpiredSessionDenial(response,baseUrl) {
  const base=new URL(String(baseUrl)),headers=response.headers();
  if(response.status()===302){
    const target=new URL(headers.location || '',base);
    h.assert(headers.location && target.origin===base.origin && target.pathname===base.pathname.replace(/\/$/,'')+'/logoutPage'
      && !target.search && !target.hash && !target.username && !target.password,'Expired-session redirect did not match the exact native logout route');
    return {classification:'EXPIRED_SESSION_NATIVE_REDIRECT'};
  }
  h.assert(response.status()===401,'The expired native session retained protected import access');
  if(headers['content-length'])h.assert(/^\d+$/.test(headers['content-length']) && Number(headers['content-length'])<=4096,
    'Expired-session denial exceeded its bounded response contract');
  const bytes=await boundedLifecycleAction(()=>response.body(),10000,'Expired-session denial body deadline elapsed; accepted state retained');
  h.assert(Buffer.isBuffer(bytes) && bytes.length<=4096,'Expired-session denial exceeded its bounded response contract');
  const text=new TextDecoder('utf-8',{fatal:true}).decode(bytes),type=headers['content-type'] || '';
  let json;try {json=JSON.parse(text);} catch(_){}
  h.assert((/^text\/plain(?:;|$)/i.test(type) && text==='Unauthorized')
    || (/^application\/json(?:;|$)/i.test(type) && json?.error==='unauthorized' && Object.keys(json).length===1),
    'Expired-session401 did not match the native unauthorized response');
  return {classification:'EXPIRED_SESSION_NATIVE_UNAUTHORIZED'};
}

function assertNativeNoteHashes(sql,state,source,copies) {
  h.assert(UUID.test(state.runId) && /^[A-Za-z0-9]{1,20}$/.test(state.sourceId) && [1,256].includes(source.notes)
    && Number.isInteger(copies) && copies>=1 && copies<=8 && Number.isSafeInteger(state.notePadding)
    && state.notePadding>=0 && state.notePadding<=49152,'Native note oracle requires bounded immutable fixture identity');
  const expected=new Map(),padding='X'.repeat(state.notePadding);
  for(let index=1;index<=source.notes;index++){
    const digest=hash(Buffer.from('FAKE-CDS-LIFE-'+state.sourceId+' note '+index+padding));expected.set(digest,(expected.get(digest)||0)+1);
  }
  const maximum=source.notes*copies;
  const rows=sql.rows('SELECT p.copy_key,SHA2(n.note,256) FROM cds_import_patient p JOIN casemgmt_note n'
    +' ON n.demographic_no=p.target_demographic_no WHERE p.run_id='+q(state.runId)+' ORDER BY p.copy_key LIMIT '+(maximum+1));
  h.assert(rows.length===maximum,'Native note hash inventory differs from the bounded source/copy count');
  const actual=new Map();
  for(const row of rows){
    h.assert(row.length===2 && typeof row[0]==='string' && row[0].length>0 && row[0].length<=256 && SHA.test(row[1]),
      'Native note hash inventory has an invalid copy/hash');
    const copy=actual.get(row[0]) || new Map();copy.set(row[1],(copy.get(row[1])||0)+1);actual.set(row[0],copy);
  }
  h.assert(actual.size===copies && [...actual.values()].every(copy=>copy.size===expected.size
    && [...expected].every(([digest,count])=>copy.get(digest)===count)),
    'Native note hash multiset lost, duplicated or changed source content in a target copy');
  return {copies,notesPerCopy:source.notes,totalNotes:maximum,expectedMultisetSha256:hash(Buffer.from(JSON.stringify([...expected].sort())))};
}

async function awaitActualProxyTimeout({page,recorder,response,request,proxy,onStage,timeoutMillis=10000}) {
  let listener;let active=true;let headersVerified=false;let status=null;
  const cancellation=()=>{
    const entries=recorder.requestFailures.filter(entry=>entry.requestRef?.deref()===request);
    h.assert(entries.length<=1,'The exact real-proxy request had duplicate cancellations');
    if(!entries.length)return null;
    h.assert(entries[0].errorText==='net::ERR_ABORTED','The exact real-proxy request failed with an unexpected error');
    return {terminal:'EXACT_REQUEST_ABORTED'};
  };
  try {
    status=response.status();
    h.assert(request && response.request()===request && status===504
      && response.headers()[proxy.proxyHeader.toLowerCase()]===proxy.proxyToken,
      'The response was not the exact Nginx504 from the approved listener');
    h.assert(Number.isSafeInteger(timeoutMillis) && timeoutMillis>0 && timeoutMillis<=30000,'Invalid real-proxy response deadline');
    headersVerified=true;onStage({stage:'proxy-response-headers',status:504,proxyHeaderVerified:true,finished:false});
    const aborted=new Promise((resolve,reject)=>{
      listener=failed=>{
        if(failed!==request)return;
        // The strict recorder is authoritative; let its listener record this event first.
        queueMicrotask(()=>{
          if(!active)return;
          try {
            const proof=cancellation();
            h.assert(proof,'The exact real-proxy cancellation has no raw recorder entry');resolve(proof);
          } catch(error){reject(error);}
        });
      };
      page.on('requestfailed',listener);
    });
    // Register first, then inspect retained raw entries so an earlier cancellation cannot be missed.
    const existing=cancellation();
    const result=existing || await boundedLifecycleAction(()=>Promise.race([aborted,
      Promise.resolve().then(()=>response.finished()).then(finished=>{
        h.assert(finished===null,'The real-proxy response did not reach a successful finished observation');
        return {terminal:'RESPONSE_FINISHED'};
      })]),timeoutMillis,'The exact real-proxy response deadline elapsed; accepted state retained');
    cancellation();
    onStage({stage:'proxy-response-finished',status:504,proxyHeaderVerified:true,finished:true,...result});
    return result;
  } catch(failure) {
    try {onStage({stage:'proxy-response-failed',status,proxyHeaderVerified:headersVerified,finished:false});}
    catch(persistenceFailure){throw safeLifecycleAggregate([failure,persistenceFailure],
      'Proxy failure evidence could not be persisted',{evidencePair:true});}
    throw failure;
  } finally {active=false;if(listener)page.off('requestfailed',listener);}
}

async function cleanupLifecycle({browser,sql,relay,state,save,log=console.log,
  browserTimeoutMillis=30000,relayTimeoutMillis=10000,timeoutMillis=45000}) {
  const failures=[];let active=true;
  state.cleanup={stage:'cleanup-started',browser:browser?'PENDING':'ABSENT',relay:relay?'PENDING':'ABSENT',
    relayPid:relay?.child?.pid || null,relayNativePid:relay?.ready?.pid || null,acceptedStateRetained:true};
  const record=stage=>{
    state.cleanup.stage=stage;
    try {save();log('LIFECYCLE_STAGE',JSON.stringify({runId:state.runId || null,...state.cleanup}));}
    catch(error){state.cleanup.stage='cleanup-failed';failures.push(error);}
  };
  record('cleanup-started');
  try {
    await boundedLifecycleAction(async()=>{
      try {
        if(browser){await boundedLifecycleAction(()=>browser.close(),browserTimeoutMillis,
          'Owned lifecycle browser close deadline elapsed; process closure is unknown and accepted state retained');if(active)state.cleanup.browser='CLOSED';}
      } catch(error){if(active){state.cleanup.browser='UNKNOWN';failures.push(error);}}
      if(!active)return;
      try {if(sql)sql.dispose();} catch(error){failures.push(error);}
      try {
        if(relay){
          const stop=relay.file('stop');
          if(fs.existsSync(stop))h.assert(privateJson(stop).attempt===state.attempt,'Existing relay stop marker belongs to another attempt');
          else publish(stop,{attempt:state.attempt});
          const exitCode=await boundedLifecycleAction(()=>relay.closed,relayTimeoutMillis,
            'The owned proxy relay did not stop; retain its process identity and private controls');
          h.assert(exitCode===0,'Owned relay driver did not attest exit0; native closure is unknown and controls retained');
          if(active)state.cleanup.relay='CLOSED';
        }
      } catch(error){if(active){state.cleanup.relay='UNKNOWN';failures.push(error);}}
    },timeoutMillis,'Lifecycle cleanup deadline elapsed; accepted work and owned process identities retained');
  } catch(error){failures.push(error);
    for(const key of ['browser','relay'])if(state.cleanup[key]==='PENDING')state.cleanup[key]='UNKNOWN';
  } finally {active=false;}
  record(failures.length?'cleanup-failed':'cleanup-settled');
  if(failures.length)throw safeLifecycleAggregate(failures,'Lifecycle cleanup is incomplete; accepted work and owned controls were retained');
  // Never delete accepted jobs, charts, immutable source, checkpoints or recovery state.
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

function startOwnedRelay(env,stateFile,state) {
  const prefix=JSON.parse(env.CDS_LIFECYCLE_PROXY_RELAY_ARGV || 'null');
  h.assert(Array.isArray(prefix) && prefix.length>=2 && prefix.length<=16
    && prefix.every(value => typeof value==='string' && value.length>0 && value.length<4096),'An explicit owned relay executable argv is required');
  const root=fs.mkdtempSync(path.join(path.dirname(stateFile),'proxy-'));
  const child=spawn(prefix[0],[...prefix.slice(1),'http://127.0.0.1:8080/',root,state.attempt,'/carlos','18086'],{stdio:['ignore','pipe','pipe']});
  let output=0;
  for(const stream of [child.stdout,child.stderr])stream.on('data',bytes => {output+=bytes.length;if(output>4096)child.kill('SIGTERM');});
  const closed=new Promise(resolve => child.once('close',resolve));
  const started=new Promise((resolve,reject)=>{child.once('spawn',resolve);child.once('error',failure=>reject(
    new Error('The owned relay process could not start',{cause:failure})));});
  started.catch(()=>{}); // Ownership survives a failed save before readiness is awaited.
  return {root,child,closed,started,file:name => path.join(root,state.attempt + '.' + name + '.json')};
}
async function awaitRelayReady(relay,state,timeoutMillis=20000) {
  await relay.started;
  const ready=await waitPrivate(relay.file('ready'),timeoutMillis,relay.child);
  h.assert(ready.ready===true && ready.attempt===state.attempt && ready.port===18086
    && Number.isSafeInteger(ready.pid) && ready.pid>0,'The exact private relay did not start');
  relay.ready=ready;return relay;
}

function processProof(receipt, expected) {
  h.assert(Number.isSafeInteger(receipt.pid) && receipt.pid>0 && Number.isSafeInteger(receipt.startEpochMillis)
    && receipt.startEpochMillis>0 && (!expected || (receipt.pid===expected.pid && receipt.startEpochMillis===expected.startEpochMillis)),
    'The native session helper process identity changed or was not attested');
  return {pid:receipt.pid,startEpochMillis:receipt.startEpochMillis};
}

function expireSession(command, sessionId, expectedProcess) {
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
  return processProof(receipt,expectedProcess);
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
  return processProof(receipt);
}

function requireNativeEnvironment(env) {
  for (const [name,alias] of [['TEST_USER','CARLOS_USER'],['TEST_PASSWORD','CARLOS_PASSWORD'],['TEST_PIN','CARLOS_PIN']]) {
    const value=env[name]||env[alias];
    h.assert(typeof value==='string' && value.length>0,
      'Required lifecycle native prerequisite missing: '+name+' (no default login or skipped proof)');
  }
  h.assert(typeof env.MYSQL_PASSWORD==='string',
    'Required lifecycle native prerequisite missing: MYSQL_PASSWORD (no skipped proof)');
}
async function requiredLifecycleStep(action) {
  try { return await action(); }
  catch (failure) {
    if (failure instanceof h.SkipCheck || failure?.name==='SkipCheck') {
      throw new Error('Required lifecycle prerequisite is unavailable; this installed gate cannot skip', {cause:failure});
    }
    throw failure;
  }
}
function safeLifecycleFailure(failure,depth=0,seen=new Set()) {
  let code,message;
  try {
    if(failure instanceof AggregateError){
      if(depth>=3)return 'CAUSE_DEPTH_LIMIT: reconcile retained original failures';
      if(seen.has(failure))return 'CAUSE_CYCLE: reconcile retained original failures';
      seen.add(failure);
      try {
        const errors=failure.errors;
        if(Array.isArray(errors) && errors.length){
          const causes=[];
          for(let index=0;index<Math.min(errors.length,3);index++)causes.push(safeLifecycleFailure(errors[index],depth+1,seen));
          if(errors.length>3)causes.push('ADDITIONAL_CAUSES_RETAINED');
          const diagnostic='AGGREGATE_FAILURE: ['+causes.join('; ')+']';
          return diagnostic.length<=1024?diagnostic:diagnostic.slice(0,1000)+'; DIAGNOSTIC_TRUNCATED';
        }
      } finally {seen.delete(failure);}
    }
    code=failure?.code;message=failure?.message;
  } catch(_){return 'UNREADABLE_FAILURE: retain state and reconcile owned runtime';}
  if(message==='The exact real-proxy response deadline elapsed; accepted state retained')
    return 'PROXY_RESPONSE_TIMEOUT: verify owned proxy response evidence and retain accepted job';
  if(message==='Owned lifecycle browser close deadline elapsed; process closure is unknown and accepted state retained')
    return 'BROWSER_CLOSE_TIMEOUT: retain owned process identity and reconcile unknown closure';
  if(message==='The owned proxy relay did not stop; retain its process identity and private controls')
    return 'RELAY_CLOSE_TIMEOUT: retain owned relay process and reconcile unknown closure';
  if(message==='Lifecycle cleanup deadline elapsed; accepted work and owned process identities retained')
    return 'CLEANUP_TIMEOUT: retain owned process identities and reconcile incomplete cleanup';
  if(message==='Owned lifecycle page close deadline elapsed; closure is unknown and accepted state retained')
    return 'PAGE_CLOSE_TIMEOUT: retain owned page/process evidence and reconcile unknown closure';
  if(message==='Owned lifecycle context close deadline elapsed; closure is unknown and accepted state retained')
    return 'CONTEXT_CLOSE_TIMEOUT: retain owned browser context and reconcile unknown closure';
  if(message==='Existing relay stop marker belongs to another attempt')
    return 'RELAY_STOP_OWNER_MISMATCH: retain existing controls and verify attempt ownership';
  if(message==='Owned relay driver did not attest exit0; native closure is unknown and controls retained')
    return 'RELAY_EXIT_UNCONFIRMED: retain owned driver/native identities and reconcile nonzero or signalled exit';
  if(message==='The synthetic CDS fixture does not validate against the pinned OntarioMD migration schema')
    return 'SCHEMA_VALIDATION_FAILED: verify pinned OntarioMD schema bundle and fixture before acceptance';
  if(message==='Required CDS regression prerequisite missing: xmllint (libxml2-utils)')
    return 'MISSING_SCHEMA_VALIDATOR: configure xmllint (libxml2-utils) before execution';
  const prerequisite=typeof message==='string' && /^Required lifecycle native prerequisite missing: (TEST_USER|TEST_PASSWORD|TEST_PIN|MYSQL_PASSWORD) \(no (?:default login or )?skipped proof\)$/.exec(message);
  if(prerequisite)return 'MISSING_NATIVE_PREREQUISITE: configure '+prerequisite[1]+' before execution';
  const reasons={ENOSPC:'evidence filesystem full; restore owned free space',EDQUOT:'evidence quota exhausted; restore owned quota',
    ENOENT:'required local resource missing; verify pinned schema and harness prerequisites',
    EACCES:'evidence permission denied; verify owned permissions',EPERM:'evidence operation denied; verify owned permissions',
    EROFS:'evidence filesystem read-only; verify owned writable storage',EIO:'evidence I/O failed; reconcile owned storage',
    EMFILE:'process descriptor limit exhausted; inspect owned process resources',ENFILE:'descriptor limit exhausted; inspect owned resources',
    EPIPE:'evidence output closed; restore owned log capture',ETIMEDOUT:'operation timed out; verify owned endpoint'};
  if(typeof code==='string' && Object.hasOwn(reasons,code))return code+': '+reasons[code];
  // Unexpected exception text may contain credentials, SQL or protected paths.
  const prefix=typeof message==='string'?message.slice(0,4096):'[no string message]';
  return 'UNCLASSIFIED_FAILURE: retain state; message-prefix4096-sha256='+hash(Buffer.from(prefix));
}
function safeLifecycleAggregate(errors,message,{evidencePair=false}={}) {
  const failure=new AggregateError(errors,message,{cause:errors[0]});
  failure.message=message+'; '+(evidencePair?'primary=['+safeLifecycleFailure(errors[0])+']; evidence=['
    +safeLifecycleFailure(errors[1])+']':'failures=['+safeLifecycleFailure(failure)+']')
    +'; primary failure and accepted state retained';
  return failure;
}
function recordWorkflowFailure(failure,{state,save,log=console.log}) {
  state.workflowFailure={stage:'workflow-failed',observedAt:Date.now(),acceptedStateRetained:true};
  try {
    save();log('LIFECYCLE_STAGE',JSON.stringify({runId:state.runId || null,...state.workflowFailure}));
  } catch(persistenceFailure) {
    throw safeLifecycleAggregate([failure,persistenceFailure],'Lifecycle failure evidence could not be recorded',{evidencePair:true});
  }
  throw failure;
}
async function run(env = process.env) {
  if (env.CDS_LIFECYCLE_EXECUTE !== 'true') {
    console.log('NOT_EXECUTED: lifecycle accepts are held; explicit CDS_LIFECYCLE_EXECUTE=true and reviewed artifact/context are required');
    return {outcome:'NOT_EXECUTED'};
  }
  requireNativeEnvironment(env);
  let browser; let sql; let relay;const closedPageAudits=[];
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
  let nativeProcess;
  if (!synthetic) {
    h.assert(Array.isArray(expiry), 'Native session expiry must be configured before any acceptance');
    nativeProcess=preflightSession(JSON.parse(env.CDS_LIFECYCLE_SESSION_PREFLIGHT_ARGV || 'null'));
    h.assert(Number(expiry.at(-2))===nativeProcess.pid && expiry.at(-1)==='/carlos',
      'Expiry and preflight must address the exact same reviewed native Tomcat process/context');
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
    return requiredLifecycleStep(async () => {
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
    if(!synthetic){
      relay=startOwnedRelay(env,stateFile,state);
      state.relayOwner={controlRoot:relay.root,driverPid:relay.child.pid || null};save();
      await awaitRelayReady(relay,state);state.relayOwner.nativePid=relay.ready.pid;save();
    }
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
      const closer=createLifecyclePageCloser(page,recorder,{baseUrl:config.baseUrl});
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
        for (const evidence of await transport.qualifyPending(prover,{expectedHttpFault:fault})) console.log('CDS_METADATA_EOF_EVIDENCE',JSON.stringify(evidence));
        assertScopedFault(recorder,fault);
      };
      return {page,recorder,transport,qualify,responses,close:closer.close};
    };
    const authenticate = async () => {
      context = await h.newContext(browser,config);
      const login = h.createRecorder();const loginPage=await h.login(context,config,login);
      const closed = login.badResponses.some(entry => entry.status === 503 && entry.resourceType === 'document'
        && new URL(entry.url).pathname === new URL(config.baseUrl).pathname.replace(/\/$/,'') + '/provider/providercontrol');
      // The barrier may release while login is in flight; validate the exact actual native
      // schedule response rather than inventing an atomic observation across two requests.
      assertRetainedLogin(login,config.baseUrl,closed);
      const closer=createLifecyclePageCloser(loginPage,login,{baseUrl:config.baseUrl,auxiliaryLogin:true,retainedLoginClosed:closed});
      try {await loginPage.waitForLoadState('networkidle',{timeout:10000});closedPageAudits.push(await closer.close());}
      finally {closer.dispose();}
    };
    await authenticate(); current = await open(state.runId);
    await current.qualify();
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
      assertAcceptance(response,{runId:id,options,optionsDigest:state.optionsDigest,owner},
        sql.value('SELECT operator FROM cds_import_run WHERE id=' + q(id)));
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
        await awaitActualProxyTimeout({page:current.page,recorder:current.recorder,response:timeout,request:selectedRequest,proxy,
          onStage:stage=>{state.proxyClientResponse={...stage,observedAt:Date.now()};save();
            console.log('LIFECYCLE_STAGE',JSON.stringify({runId:state.runId,...state.proxyClientResponse}));}});
        state.proxyAfter=progress(sql,state.runId);requirePending(state.proxyAfter);save();
        fault={kind:'nginx',request:selectedRequest,response:timeout,uploadId:state.uploadId,
          proxyHeader:proxy.proxyHeader,proxyToken:proxy.proxyToken,accepted:true,runId:state.runId,status:504,proxyVerified:true,
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
      await current.qualify(fault);const closed=await current.close({runId:state.runId,expectedFault:fault});
      closedPageAudits.push(closed);
      requirePending(progress(sql,state.runId));
      state.tabCloseEvidence=closed.audit();save();
      current = await open(state.runId); await bindAccepted(state.runId);
      await current.qualify();
      closed.audit();
      state.completed.push('tab-close'); save();
    }
    if (!synthetic && !state.completed.includes('native-session-expiry')) {
      state.expiryProgress = await waitPending(sql,state.runId); save();
      await current.qualify();
      const csrf = await current.page.locator('input[name="CSRF-TOKEN"]').first().inputValue();
      const closed=await current.close({runId:state.runId});
      closedPageAudits.push(closed);
      const cookies = await context.cookies(String(config.baseUrl));
      const sessions = cookies.filter(cookie => cookie.name === 'JSESSIONID');
      h.assert(sessions.length === 1, 'The synthetic browser has no unique selected native session');
      state.nativeSessionProcess=expireSession(expiry,sessions[0].value,nativeProcess);save();
      state.expiryAfter = progress(sql,state.runId); requirePending(state.expiryAfter); save();
      const refused = await context.request.get(h.appUrl(config.baseUrl,'/form/cdsImportStatus?apiVersion=2&runId=' + state.runId),
        {headers:{'CSRF-TOKEN':csrf,'X-Requested-With':'XMLHttpRequest'},maxRedirects:0,failOnStatusCode:false});
      state.expiryDenial=await assertExpiredSessionDenial(refused,config.baseUrl);save();
      await boundedLifecycleAction(()=>context.close(),30000,
        'Owned lifecycle context close deadline elapsed; closure is unknown and accepted state retained');
      await authenticate(); current = await open(state.runId);
      await bindAccepted(state.runId);
      state.expiryCloseEvidence=closed.audit();save();
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
    const nativeNoteHashes=assertNativeNoteHashes(sql,state,source,copies);
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
    assertSingleUploadAcceptance(sql,owner,state.uploadId);
    await assertPrimaryJobStatus(current.page,state.runId);
    const reports = protectedEvidence(sql,state,env);
    for (const [selector,key] of [['eventReport','event'],['errorReport','error'],['receiptReport','completion'],['unitsReport','units']]) {
      const transfer = current.page.waitForEvent('download',{timeout:30000});
      await current.page.locator('#' + selector).click();
      const download = await transfer;
      const downloaded=await completedLifecycleDownload(download);
      const actual = hashFile(downloaded,8 * 1024 * 1024);
      h.assert(actual.bytes === reports[key].bytes && actual.sha256 === reports[key].sha256,
        'Browser-delivered protected report differs from its durable physical manifest');
    }
    const receipt = await importApi(current.page,'cdsImportReport',{runId:state.runId,type:'receipt'});
    h.assert(receipt.status === 200 && receipt.body?.cleanComplete === true
      && receipt.body.optionsDigest === state.optionsDigest && receipt.body.sourceDigest === state.sourceDigest
      && receipt.body.manifestDigest === state.manifestDigest && receipt.body.rulesetDigest === state.rulesetDigest,
      'Protected completion report differs from the accepted command/source/inventory');
    await current.qualify();
    for(const closed of closedPageAudits)closed.audit();
    state.completed.push('native-reports-clean-complete'); save();
    console.log('LIFECYCLE_EVIDENCE',JSON.stringify({runId:state.runId,artifactSha256:state.artifactSha256,
      sourceSha256:state.sourceSha256,optionsDigest:state.optionsDigest,sourceDigest:state.sourceDigest,
      sourceBytes:source.buffer.length,notePayloadBytes:state.notePadding,
      manifestDigest:state.manifestDigest,rulesetDigest:state.rulesetDigest,completed:state.completed,native:stored,nativeNoteHashes,reports,
      nativeSessionProcess:state.nativeSessionProcess || null,
      sessionExpiry:'selected-native-HttpSession-administrative-expiry-not-inactivity-timer'}));
    }).catch(failure=>recordWorkflowFailure(failure,{state,save}));
  },async cleanup() {
    await cleanupLifecycle({browser,sql,relay,state,save});
  }});
}

if (require.main === module) run().then(result => { if (result?.outcome === 'NOT_EXECUTED') process.exitCode = 2; })
  .catch(failure => { console.error('FAIL lifecycle gate: '+safeLifecycleFailure(failure)
    +'; accepted state retained; inspect protected artifacts'); process.exitCode = 1; });
module.exports = {run,optionsFrom,optionsDigest,fixture,readState,writeState,bindState,progress,requirePending,assertCompletion,assertAcceptance,assertSingleUploadAcceptance,assertScopedFault,expireSession,preflightSession,processProof,hashFile,evidencePath,protectedEvidence,contractApi,requireNativeEnvironment,requiredLifecycleStep,awaitActualProxyTimeout,cleanupLifecycle,boundedLifecycleAction,recordWorkflowFailure,createLifecyclePageCloser,completedLifecycleDownload,startOwnedRelay,awaitRelayReady,assertExpiredSessionDenial,assertNativeNoteHashes};
