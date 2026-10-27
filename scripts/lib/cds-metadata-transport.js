/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Distributed under the GNU General Public License, version 2 or any later version.
 */
'use strict';

// Test-harness evidence only. No production fetch, stream, authorization or completion rules change.
const crypto = require('node:crypto');
const qualified = new WeakMap();
const boundFailures = new WeakMap();
const installed = new WeakMap();
const HEADER = 'x-cds-metadata-trace';
const MAX_BYTES = 2 * 1024 * 1024;

function strictObject(text, value, parse = JSON.parse) {
  if (!value || typeof value !== 'object' || Array.isArray(value) || value.ok !== true) throw Error('METADATA_NOT_SUCCESS_OBJECT');
  let at = 0, tokens = 0;
  const space = () => { while (/\s/.test(text[at] || '') && at < text.length) at++; };
  const string = () => {
    const start = at++;
    while (at < text.length) {
      const c = text[at++];
      if (c === '\\') at++;
      else if (c === '"') return parse(text.slice(start, at));
    }
    throw Error('METADATA_STRING_INVALID');
  };
  function visit(depth) {
    if (depth > 32 || ++tokens > 20000) throw Error('METADATA_COMPLEXITY_EXCEEDED');
    space();
    if (text[at] === '"') { string(); return; }
    if (text[at] === '{') {
      at++; space(); const keys = new Set();
      if (text[at] === '}') { at++; return; }
      while (true) {
        space(); const key = string();
        if (keys.has(key)) throw Error('METADATA_DUPLICATE_KEY');
        keys.add(key); space(); at++; visit(depth + 1); space();
        if (text[at++] === '}') return;
      }
    }
    if (text[at] === '[') {
      at++; space(); if (text[at] === ']') { at++; return; }
      while (true) { visit(depth + 1); space(); if (text[at++] === ']') return; }
    }
    while (at < text.length && !/[\s,\]}]/.test(text[at])) at++;
  }
  visit(0); space();
  if (at !== text.length) throw Error('METADATA_TRAILING_DATA');
  return value;
}

function browserObserver(config, validateObject) {
  if (Object.prototype.hasOwnProperty.call(globalThis, config.key)) return;
  const NativeFetch = globalThis.fetch, NativeWritable = globalThis.WritableStream;
  const NativeDecoder = globalThis.TextDecoder, nativeParse = JSON.parse;
  const nativePipe = ReadableStream.prototype.pipeTo;
  const bodies = new WeakMap(), sinks = new WeakMap(), decoders = new WeakMap(), traces = new Map();
  let currentWrite = null, candidate = null, counter = 0, retainedUnits = 0;
  function release(trace) { if (trace?.segments) { retainedUnits -= trace.units || 0; trace.segments = []; trace.units = 0; } }
  const frame = Array.from(crypto.getRandomValues(new Uint32Array(2)), n => n.toString(16)).join('-');
  let documentRetired = false;
  // A restored BFCache document must not revive evidence observed before leaving it.
  globalThis.addEventListener('pagehide', () => { documentRetired = true; }, {once: true});
  function invalidate(trace, code) { if (trace && !trace.invalid) trace.invalid = code; release(trace); }
  function token(value) { if (value == null) return null; if (typeof value !== 'string' || !/^[A-Za-z0-9_-]{1,128}$/.test(value)) throw Error('METADATA_ID_INVALID'); return value; }
  function digest(value) { if (value == null) return null; if (typeof value !== 'string' || !/^[a-f0-9]{64}$/.test(value)) throw Error('METADATA_DIGEST_INVALID'); return value; }
  function integer(value, nullable=false) { if(nullable && value==null)return null; if(!Number.isSafeInteger(value) || value<0)throw Error('METADATA_INTEGER_INVALID'); return value; }
  function boolean(value) {if(typeof value!=='boolean')throw Error('METADATA_BOOLEAN_INVALID');return value;}
  function summary(data, trace) {
    const job = data.job || data.run || {}, upload = data.upload || {}, result = data.summary || {};
    const out = { runId: token(job.id || trace.runId), uploadId: token(upload.id || trace.uploadId),
      sourceDigest: digest(job.sourceDigest || result.sourceDigest),
      optionsDigest: digest(job.optionsDigest || result.optionsDigest),
      rulesetDigest: digest(job.rulesetDigest || result.rulesetDigest),
      receivedBytes: upload.receivedBytes === undefined ? null : upload.receivedBytes,
      cleanComplete: result.cleanComplete === true, state: token(job.state || upload.state) };
    if (out.receivedBytes != null && (!Number.isSafeInteger(out.receivedBytes) || out.receivedBytes < 0)) throw Error('METADATA_COUNTER_INVALID');
    if (trace.runId && job.id && trace.runId !== job.id) throw Error('METADATA_RUN_MISMATCH');
    if (trace.uploadId && upload.id && trace.uploadId !== upload.id) throw Error('METADATA_UPLOAD_MISMATCH');
    if (trace.section === 'uploads') {
      if (!Array.isArray(data.rows) || data.rows.length > 100) throw Error('METADATA_UPLOAD_PAGE_INVALID');
      out.uploadIds = data.rows.map(row => { const id=token(row.id); if(!id)throw Error('METADATA_ID_INVALID'); return id; });
      if(new Set(out.uploadIds).size!==out.uploadIds.length)throw Error('METADATA_UPLOAD_PAGE_INVALID');
    }
    if (/\/cdsImportStatus(?:\.do|\.action)?$/.test(trace.route) && ['uploads','patients','diagnostics','chunks'].includes(trace.section)) {
      if(!Array.isArray(data.rows) || data.rows.length>trace.limit)throw Error('METADATA_PAGE_INVALID');
      const rows=data.rows.map(row=>{
        if(trace.section==='patients')return {patientKey:token(row.patientKey),copyKey:token(row.copyKey),state:token(row.state),held:boolean(row.held),targetDemographicNo:integer(row.targetDemographicNo,true)};
        if(trace.section==='diagnostics')return {id:integer(row.id),code:token(row.code),blocking:boolean(row.blocking),resolved:boolean(row.resolved)};
        if(trace.section==='chunks')return {index:integer(row.index),offset:integer(row.offset),bytes:integer(row.bytes),sha256:digest(row.sha256)};
        return {id:token(row.id),state:token(row.state),receivedBytes:integer(row.receivedBytes),expectedBytes:integer(row.expectedBytes)};
      });
      out.page={section:trace.section,afterKey:trace.afterKey,afterId:trace.afterId,limit:trace.limit,rows,
        nextCursor:trace.section==='diagnostics'?integer(data.nextCursor,true):token(data.nextCursor)};
    }
    if (/\/cdsImportStatus(?:\.do|\.action)?$/.test(trace.route) && trace.section==='job' && !job.id) throw Error('METADATA_RUN_MISSING');
    return out;
  }
  globalThis.TextDecoder = class extends NativeDecoder {
    decode(input, options) {
      const trace = currentWrite || decoders.get(this);
      if (currentWrite) {
        decoders.set(this, currentWrite);
        if (this.fatal !== true || options?.stream !== true || !Number.isSafeInteger(input?.byteLength)) invalidate(trace, 'METADATA_DECODER_INVALID');
        if (input?.byteLength) { trace.bytes += input.byteLength; if (trace.bytes > config.maxBytes) invalidate(trace, 'METADATA_CAPACITY_EXCEEDED'); }
      }
      let value;
      try { value = super.decode(input, options); }
      catch (failure) { invalidate(trace, 'METADATA_UTF8_INVALID'); throw failure; }
      if(trace && !trace.invalid && value) {
        if(trace.segments.length >= 4096 || retainedUnits + value.length > 4*1024*1024)invalidate(trace,'METADATA_OBSERVER_CAPACITY');
        else { trace.segments.push(value); trace.units += value.length; retainedUnits += value.length; }
      }
      if (input === undefined && trace) {
        if(options?.stream===true || this.fatal!==true)invalidate(trace,'METADATA_DECODER_FLUSH_INVALID');
        trace.flushed = true; candidate = trace;
        queueMicrotask(() => { if (candidate === trace) candidate = null; });
      }
      return value;
    }
  };
  globalThis.WritableStream = class extends NativeWritable {
    constructor(sink = {}, strategy) {
      const state = {trace: null};
      const wrapped = sink !== null && ['object','function'].includes(typeof sink) ? new Proxy(sink, {
        get(target, name) {
          const method=Reflect.get(target,name,target);
          if (!['start','write','close','abort'].includes(name) || typeof method!=='function') return method;
          return function (...args) {
            const before=currentWrite;
            if(name==='write')currentWrite=state.trace;
            try { return method.apply(target,args); }
            finally { currentWrite=before; }
          };
        }
      }) : sink;
      super(wrapped, strategy); sinks.set(this, state);
    }
  };
  ReadableStream.prototype.pipeTo = function (destination, options) {
    const trace = bodies.get(this), sink = sinks.get(destination);
    if (trace && sink) sink.trace = trace;
    const result = nativePipe.call(this, destination, options);
    if (trace && sink) result.then(() => { trace.eof = true; }, () => invalidate(trace, 'METADATA_STREAM_FAILED'));
    return result;
  };
  JSON.parse = function (text, reviver) {
    const trace = candidate; candidate = null;
    const value = nativeParse(text, reviver);
    if (trace) {
      try {
        if (!trace.eof || !trace.flushed || reviver !== undefined || trace.invalid) throw Error('METADATA_EOF_NOT_PROVEN');
        let offset=0;
        for(const segment of trace.segments){if(!text.startsWith(segment,offset))throw Error('METADATA_PARSE_SOURCE_MISMATCH');offset+=segment.length;}
        if(offset!==text.length)throw Error('METADATA_PARSE_SOURCE_MISMATCH');
        validateObject(text, value, nativeParse);
        trace.receipt = summary(value, trace); trace.valid = true;
      } catch (failure) { invalidate(trace, failure.message || 'METADATA_JSON_INVALID'); }
      finally { release(trace); }
    }
    return value;
  };
  globalThis.fetch = async function (input, options = {}) {
    if(options===null)options={};
    const supplied = typeof input === 'string' ? input : input.url || input.href;
    const url = new URL(supplied, location.href), match = /^\/(?:cdsImportStatus|cdsUpload|cdsImportSubmit|cdsImportControl|cdsImportDiagnostic)(?:\.do|\.action)?$/.test(url.pathname.slice(config.form.length));
    if (url.origin !== config.origin || !url.pathname.startsWith(config.form) || !match) return NativeFetch.call(this, input, options);
    const method = String(options.method || input.method || 'GET').toUpperCase();
    if (!['GET', 'POST'].includes(method)) return NativeFetch.call(this, input, options);
    const headers = new Headers(options.headers === undefined ? input.headers : options.headers);
    const tag = config.prefix + '-' + frame + '-' + (++counter); headers.set(config.header, tag);
    const params = options.body instanceof URLSearchParams ? options.body : url.searchParams;
    const signal=options.signal===undefined?input.signal:options.signal;
    const trace = {tag, method, route: url.pathname, section: params.get('section') || 'job',
      runId: null, uploadId: null, afterKey:'',afterId:0,limit:50, bytes: 0, eof: false, valid: false,
      owner: document.getElementById('cdsImport')?.dataset.owner || null, aborted: signal?.aborted === true, segments: [], units:0};
    try { trace.runId=token(params.get('runId')); trace.uploadId=token(params.get('uploadId')); trace.owner=token(trace.owner); trace.afterKey=token(params.get('afterKey') || null)||''; trace.afterId=integer(Number(params.get('afterId')||0));trace.limit=integer(Number(params.get('limit')||50));if(trace.limit<1 || trace.limit>100)throw Error('METADATA_PAGE_INVALID'); } catch (failure) { invalidate(trace, 'METADATA_ID_INVALID'); }
    if (signal) signal.addEventListener('abort', () => { trace.aborted = true; }, {once: true});
    while (traces.size >= config.maxTraces) { const oldest=traces.keys().next().value; release(traces.get(oldest)); traces.delete(oldest); }
    traces.set(tag, trace);
    const response = await NativeFetch.call(this, input, {...options, headers});
    trace.status = response.status; trace.redirected = response.redirected;
    trace.json = /^application\/json(?:\s*;|\s*$)/i.test(response.headers.get('content-type') || '');
    if (response.body) bodies.set(response.body, trace);
    return response;
  };
  Object.defineProperty(globalThis, config.key, {value: Object.freeze({
    documentIdentity() { return documentRetired ? null : frame; },
    snapshot(tag) { const t=traces.get(tag); if(!t)return null; const {segments,units,...metadata}=t;return metadata; }, peek(tag) { const t=traces.get(tag); return t ? {ready: t.valid || !!t.invalid, invalid: t.invalid || null} : null; }, take(tag) {
    const trace = traces.get(tag); traces.delete(tag);
    if (!trace) return null;
    release(trace);
    const {receipt, segments, units, ...metadata} = trace;
    return {...metadata, receipt: receipt || null};
  }})});
}

function validateEnvelope(trace) {
  if (!trace || !trace.valid || !trace.eof || !trace.flushed || trace.invalid || trace.aborted || trace.redirected
      || !trace.json || ![200, 202].includes(trace.status) || !Number.isSafeInteger(trace.bytes) || trace.bytes < 1 || trace.bytes > MAX_BYTES) throw Error('CDS_TRANSPORT_EOF_UNPROVEN');
}

function validateProof(trace, proof) {
  validateEnvelope(trace);
  if (!trace.owner || !proof || proof.owner !== trace.owner || proof.uiVerified !== true || proof.independentVerified !== true
      || !['read-only', 'owned-upload', 'owned-run'].includes(proof.kind)) throw Error('CDS_TRANSPORT_OWNER_PROOF_REQUIRED');
  const receipt = trace.receipt;
  if (!receipt) throw Error('CDS_TRANSPORT_RECEIPT_REQUIRED');
  for (const key of ['runId', 'uploadId', 'sourceDigest', 'optionsDigest', 'rulesetDigest']) {
    if (receipt[key] != null && proof[key] !== receipt[key]) throw Error('CDS_TRANSPORT_RECEIPT_MISMATCH');
  }
  if (receipt.receivedBytes != null && (!Number.isSafeInteger(proof.receivedBytes) || proof.receivedBytes < receipt.receivedBytes)) throw Error('CDS_TRANSPORT_UPLOAD_COUNTER_MISMATCH');
  if (receipt.uploadIds && (!Array.isArray(proof.uploadIds) || receipt.uploadIds.some(id => !proof.uploadIds.includes(id)))) throw Error('CDS_TRANSPORT_UPLOAD_PAGE_MISMATCH');
  if (receipt.page && JSON.stringify(proof.page)!==JSON.stringify(receipt.page)) throw Error('CDS_TRANSPORT_PAGE_PROOF_REQUIRED');
  if (trace.method !== 'GET' && proof.kind === 'read-only') throw Error('CDS_TRANSPORT_MUTATION_PROOF_REQUIRED');
  if (trace.method !== 'GET' && proof.kind === 'owned-run' && proof.nativeVerified !== true) throw Error('CDS_TRANSPORT_NATIVE_PROOF_REQUIRED');
  if (receipt.cleanComplete && (proof.nativeVerified !== true || proof.reportsVerified !== true)) throw Error('CDS_TRANSPORT_COMPLETION_PROOF_REQUIRED');
  return {classification: 'CDS_METADATA_EOF_CONFIRMED', tag: trace.tag, method: trace.method, route: trace.route,
    status: trace.status, bytes: trace.bytes, owner: trace.owner, ...receipt, proofKind: proof.kind};
}

async function installObserver(page, recorder, options = {}) {
  const base = new URL(String(options.baseUrl));
  const key = '__cdsTransport_' + crypto.randomBytes(12).toString('hex');
  const config = {key, prefix: crypto.randomBytes(12).toString('hex'), origin: base.origin,
    form: base.pathname.replace(/\/$/, '') + '/form', header: HEADER,
    maxBytes: options.maxBytes || MAX_BYTES, maxTraces: options.maxTraces || 128};
  if (!Number.isSafeInteger(config.maxBytes) || config.maxBytes < 1 || config.maxBytes > MAX_BYTES
      || !Number.isSafeInteger(config.maxTraces) || config.maxTraces < 1 || config.maxTraces > 256) throw Error('CDS_TRANSPORT_LIMIT_INVALID');
  const records = new Map();
  page.on('request', request => {
    const tag = request.headers()[HEADER];
    if (!tag || !tag.startsWith(config.prefix + '-')) return;
    const frame = request.frame();
    const identity = /^([a-f0-9]{1,8}-[a-f0-9]{1,8})-([1-9][0-9]{0,15})$/.exec(tag.slice(config.prefix.length + 1));
    const documentIdentity = identity && Number.isSafeInteger(Number(identity[2])) ? identity[1] : null;
    if (records.has(tag)) { records.get(tag).ambiguous = true; return; }
    while (records.size >= config.maxTraces) records.delete(records.keys().next().value);
    records.set(tag, {tag, frame, documentIdentity, route: new URL(request.url()).pathname, method: request.method(), resourceType: request.resourceType(), requestRef: new WeakRef(request), entries: []});
  });
  page.on('response', response => { const record=records.get(response.request().headers()[HEADER]); if(record) { record.status=response.status(); record.json=/^application\/json(?:\s*;|\s*$)/i.test(response.headers()['content-type'] || ''); record.redirected=!!response.request().redirectedFrom(); } });
  page.on('requestfailed', request => {
    const tag = request.headers()[HEADER], record = records.get(tag);
    if (!record || record.requestRef.deref() !== request) return;
    record.error = request.failure()?.errorText; record.navigated = page.isClosed() || record.frame.isDetached();
    const from=Math.max(0,recorder.requestFailures.length-1);
    queueMicrotask(() => {
      for (const entry of recorder.requestFailures.slice(from)) if (entry.requestRef?.deref() === request) { boundFailures.set(entry, record); record.entries.push(new WeakRef(entry)); }
    });
  });
  const content = '(' + browserObserver.toString() + ')(' + JSON.stringify(config) + ',' + strictObject.toString() + ');';
  await page.addInitScript({content});
  for (const frame of page.frames()) await frame.evaluate(content);
  async function qualifyRecord(record, prover) {
    if (record.audit) return record.audit;
    if (record.error !== 'net::ERR_ABORTED' || record.resourceType !== 'fetch' || record.navigated || record.ambiguous || !record.documentIdentity || page.isClosed() || record.frame.isDetached() || record.redirected || !record.json || ![200,202].includes(record.status)) throw Error('CDS_TRANSPORT_FAILURE_NOT_QUALIFIABLE');
    await record.frame.waitForFunction(({key, tag, documentIdentity}) => {
      const observer=globalThis[key];
      if (observer?.documentIdentity() !== documentIdentity) return true;
      const trace=observer.peek(tag); return !trace || trace.ready;
    }, {key,tag:record.tag,documentIdentity:record.documentIdentity}, {timeout:5000});
    const observed = await record.frame.evaluate(({key, tag}) => {
      const observer=globalThis[key];
      return {documentIdentity:observer?.documentIdentity(), trace:observer?.snapshot(tag)};
    }, {key, tag: record.tag});
    if (observed.documentIdentity !== record.documentIdentity) throw Error('CDS_TRANSPORT_NAVIGATION_CHANGED');
    const trace = observed.trace;
    if (!trace || trace.method !== record.method || trace.route !== record.route || trace.status !== record.status || trace.bytes > config.maxBytes) throw Error('CDS_TRANSPORT_TRACE_MISMATCH');
    validateEnvelope(trace);
    if (trace.receipt?.uploadIds) Object.freeze(trace.receipt.uploadIds);
    if (trace.receipt?.page) {trace.receipt.page.rows.forEach(Object.freeze);Object.freeze(trace.receipt.page.rows);Object.freeze(trace.receipt.page);}
    if (trace.receipt) Object.freeze(trace.receipt);
    const proof = await prover(Object.freeze({...trace.receipt, owner: trace.owner, method: trace.method, route: trace.route,
      section: trace.section, status: trace.status, bytes: trace.bytes, tag: trace.tag}));
    if(page.isClosed() || record.frame.isDetached())throw Error('CDS_TRANSPORT_NAVIGATION_CHANGED');
    // Identity and consuming the exact trace share one browser turn. History-only
    // replaceState keeps this document; reload/replacement/pagehide cannot qualify.
    const finalObserved=await record.frame.evaluate(({key,tag,documentIdentity})=>{
      const observer=globalThis[key];
      if(observer?.documentIdentity()!==documentIdentity)return {documentMatches:false};
      return {documentMatches:true,trace:observer.take(tag)};
    },{key,tag:record.tag,documentIdentity:record.documentIdentity});
    if(!finalObserved.documentMatches)throw Error('CDS_TRANSPORT_NAVIGATION_CHANGED');
    const finalTrace=finalObserved.trace;
    if(!finalTrace || finalTrace.tag!==trace.tag || finalTrace.method!==trace.method || finalTrace.route!==trace.route || finalTrace.status!==trace.status || finalTrace.bytes!==trace.bytes)throw Error('CDS_TRANSPORT_TRACE_CHANGED');
    const audit = validateProof(finalTrace, proof);
    record.audit = Object.freeze(audit);
    for (const reference of record.entries) { const entry=reference.deref(); if(entry && boundFailures.get(entry)===record) qualified.set(entry, record.audit); }
    return record.audit;
  }
  return {
    async qualifyResponse(response, prover) {
      const error = await response.finished();
      if (!error) return {qualified: false, finished: true};
      const request = response.request(), record = records.get(request.headers()[HEADER]);
      if (!record || record.requestRef.deref() !== request || ![200, 202].includes(response.status())) throw Error('CDS_TRANSPORT_RESPONSE_UNPROVEN');
      await Promise.resolve();
      return {qualified: true, evidence: await qualifyRecord(record, prover)};
    },
    async qualifyPending(prover) {
      const audits = [];
      for (const record of records.values()) if (record.error === 'net::ERR_ABORTED' && !record.audit) audits.push(await qualifyRecord(record, prover));
      return audits;
    }
  };
}

function installCdsMetadataTransportObserver(page, recorder, options = {}) {
  const previous=installed.get(page), base=String(options.baseUrl);
  if(previous){if(previous.base!==base || previous.recorder!==recorder)throw Error('CDS_TRANSPORT_OBSERVER_SCOPE_CHANGED');return previous.promise;}
  const promise=installObserver(page,recorder,options);
  installed.set(page,{base,recorder,promise});
  return promise;
}

function isQualifiedCdsMetadataFailure(entry) {
  return entry?.errorText === 'net::ERR_ABORTED' && entry?.resourceType === 'fetch' && qualified.has(entry);
}
function cdsMetadataQualification(entry) { return qualified.get(entry) || null; }

module.exports = {installCdsMetadataTransportObserver, isQualifiedCdsMetadataFailure, cdsMetadataQualification,
  strictObject, validateProof, browserObserver};
