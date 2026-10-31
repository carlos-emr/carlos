/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Distributed under the GNU General Public License, version 2 or any later version.
 */
'use strict';
const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const {webcrypto} = require('node:crypto');
const {browserObserver, strictObject, validateProof, isQualifiedCdsMetadataFailure} = require('./lib/cds-metadata-transport');

async function observed(body, options = {}) {
  class LocalReadable extends ReadableStream {}
  class LocalWritable extends WritableStream {}
  class LocalDecoder extends TextDecoder {}
  const config = {key:'__trace',prefix:'probe',origin:'http://localhost',form:'/carlos/form',header:'x-cds-metadata-trace',maxBytes:options.maxBytes || 2048,maxTraces:4};
  let tag;const tags=[],listeners=new Map();
  const bytes = typeof body === 'string' ? new TextEncoder().encode(body) : body;
  const sandbox = vm.createContext({URL,URLSearchParams,Headers,crypto:webcrypto,queueMicrotask,
    addEventListener:(name,listener)=>{if(!listeners.has(name))listeners.set(name,[]);listeners.get(name).push(listener);},
    ReadableStream:LocalReadable,WritableStream:LocalWritable,TextDecoder:LocalDecoder,
    Request,AbortController,
    location:{href:'http://localhost/carlos/form/importUpload'},document:{getElementById:()=>({dataset:{owner:'web-1'}})},
    fetch:async(input,init)=>{tag=init.headers.get(config.header);tags.push(tag);const address=new URL(typeof input==='string'?input:input.url||input.href,'http://localhost');const chosen=options.dynamicBodies?.[address.searchParams.get('runId')]||body;const bytes=typeof chosen==='string'?new TextEncoder().encode(chosen):chosen;return {status:options.status || 200,redirected:!!options.redirected,headers:new Headers({'content-type':options.type || 'application/json'}),
      body:new LocalReadable({start(controller){controller.enqueue(bytes);if(options.streamError)setTimeout(()=>controller.error(Error('synthetic truncated transport')),2);else controller.close();}})};}});
  sandbox.fixtureConfig=config;
  vm.runInContext('('+browserObserver.toString()+')(fixtureConfig,'+strictObject.toString()+')',sandbox);
  const controller=new AbortController();sandbox.controller=controller;
  const requestPath='/carlos/form/cdsImportStatus?runId=run-1'+(options.section?'&section='+options.section+'&afterKey=&afterId=0&limit=50':'');
  sandbox.fixtureOptions=options;sandbox.fixturePath=requestPath;sandbox.fixtureUrl='http://localhost'+requestPath;
  let failure;
  try {
    await vm.runInContext(`(async()=>{
      const input=fixtureOptions.request?new Request(fixtureUrl,{signal:controller.signal}):fixturePath;
      const response=await fetch(input,fixtureOptions.nullSignal?{signal:null}:{});
      const decoder=new TextDecoder('utf-8',{fatal:fixtureOptions.fatal!==false});let bytes=0,text='';
      await response.body.pipeTo(new WritableStream({write(chunk){
        bytes+=chunk.byteLength;if(bytes>(fixtureOptions.productLimit||fixtureConfig.maxBytes))throw Error('capacity');
        text+=decoder.decode(chunk,{stream:true});
        if(fixtureOptions.unrelatedDecoder)new TextDecoder('utf-8',{fatal:true}).decode(new Uint8Array([120]),{stream:true});
      }}));text+=decoder.decode();
      if(fixtureOptions.unrelatedParse)JSON.parse('{"ok":true,"job":{"id":"run-1"}}');
      return JSON.parse(text);
    })()`,sandbox);
  } catch (e) {failure=e;}
  await Promise.resolve();
  if(options.abortAfterEof)controller.abort();
  sandbox.fixtureTag=tag;
  const trace=vm.runInContext('globalThis.__trace.take(fixtureTag)',sandbox);
  return {trace,failure,sandbox,config,tags,dispatch:name=>{for(const listener of listeners.get(name)||[])listener();}};
}
function proof(trace, overrides={}) {return {kind:'read-only',owner:'web-1',uiVerified:true,independentVerified:true,runId:'run-1',...overrides};}

test('binds successful bounded fatal UTF8 JSON EOF to its exact safe receipt',async()=>{
  const {trace,failure}=await observed('{"ok":true,"job":{"id":"run-1","state":"INCOMPLETE"}}');
  assert.equal(failure,undefined);assert.equal(trace.valid,true);assert.equal(trace.eof,true);assert.equal(trace.receipt.runId,'run-1');
  assert.equal(validateProof(trace,proof(trace)).classification,'CDS_METADATA_EOF_CONFIRMED');
  assert.equal(JSON.stringify(trace).includes('"text"'),false);
});

for(const [name,body,options] of [
  ['truncated JSON','{"ok":true,',{}],
  ['complete JSON followed by stream error','{"ok":true}',{streamError:true}],
  ['trailing data','{"ok":true} ignored',{}],
  ['duplicate success key','{"ok":false,"ok":true}',{}],
  ['escaped duplicate key','{"ok":true,"\\u006f\\u006b":true}',{}],
  ['array response','[{"ok":true}]',{}],
  ['false success flag','{"ok":false}',{}],
  ['oversize response','{"ok":true,"padding":"'+ 'x'.repeat(200)+'"}',{maxBytes:64}],
  ['invalid UTF8',new Uint8Array([123,34,111,107,34,58,116,114,117,101,44,34,120,34,58,34,255,34,125]),{}],
  ['nonfatal decoder','{"ok":true}',{fatal:false}],
  ['wrong response run','{"ok":true,"job":{"id":"other-run"}}',{}]
]) test('does not qualify '+name,async()=>{
  const {trace}=await observed(body,options);
  assert.throws(()=>validateProof(trace,proof(trace)));
});

test('rejects non-success HTTP, redirect, abort, missing proof and identity mismatches',async()=>{
  const {trace}=await observed('{"ok":true,"job":{"id":"run-1"}}');
  for(const change of [{status:500},{status:201},{redirected:true},{json:false},{aborted:true},{eof:false},{flushed:false},{bytes:2097153},{valid:false}])assert.throws(()=>validateProof({...trace,...change},proof(trace)));
  for(const change of [{owner:'web-other'},{uiVerified:false},{independentVerified:false},{runId:'other'},{kind:'ignored'}])assert.throws(()=>validateProof(trace,proof(trace,change)));
});

test('requires current native evidence for mutations and native/report evidence for completion',async()=>{
  const {trace}=await observed('{"ok":true,"job":{"id":"run-1"},"summary":{"cleanComplete":true}}');
  assert.throws(()=>validateProof(trace,proof(trace)));
  assert.equal(validateProof(trace,proof(trace,{nativeVerified:true,reportsVerified:true})).proofKind,'read-only');
  const mutation={...trace,method:'POST',receipt:{...trace.receipt,cleanComplete:false}};
  assert.throws(()=>validateProof(mutation,proof(trace)));
  assert.throws(()=>validateProof(mutation,proof(trace,{kind:'owned-run'})));
  assert.equal(validateProof(mutation,proof(trace,{kind:'owned-run',nativeVerified:true})).proofKind,'owned-run');
});

test('does not grant a qualification from a caller-provided field or URL',()=>{
  assert.equal(isQualifiedCdsMetadataFailure({errorText:'net::ERR_ABORTED',resourceType:'fetch',url:'http://localhost/carlos/form/cdsImportStatus',qualified:true}),false);
});

test('freezes document identity and permanently retires it when a cached document is left',async()=>{
  const f=await observed('{"ok":true,"job":{"id":"run-1"}}');
  const identity=vm.runInContext('__trace.documentIdentity()',f.sandbox);
  assert.match(identity,/^[a-f0-9]{1,8}-[a-f0-9]{1,8}$/);
  assert.equal(vm.runInContext('Object.isFrozen(__trace)',f.sandbox),true);
  assert.throws(()=>vm.runInContext('"use strict";__trace.documentIdentity=()=>"forged"',f.sandbox));
  f.dispatch('pagehide');f.dispatch('pageshow');
  assert.equal(vm.runInContext('__trace.documentIdentity()',f.sandbox),null);
});

test('observes existing Request signals after EOF and preserves explicit null override',async()=>{
  const body='{"ok":true,"job":{"id":"run-1"}}';
  const aborted=await observed(body,{request:true,abortAfterEof:true});
  assert.equal(aborted.trace.aborted,true);assert.throws(()=>validateProof(aborted.trace,proof(aborted.trace)));
  const overridden=await observed(body,{request:true,nullSignal:true,abortAfterEof:true});
  assert.equal(overridden.trace.aborted,false);assert.equal(validateProof(overridden.trace,proof(overridden.trace)).classification,'CDS_METADATA_EOF_CONFIRMED');
});
test('rejects an unrelated parse even in the same synchronous decoder turn',async()=>{
  const {trace}=await observed('{"ok":true,"job":{"id":"run-1","state":"INCOMPLETE"}}',{unrelatedParse:true});
  assert.equal(trace.valid,false);assert.throws(()=>validateProof(trace,proof(trace)));
});

const {EventEmitter} = require('node:events');
const {installCdsMetadataTransportObserver} = require('./lib/cds-metadata-transport');
async function networkFixture(options={}) {
  const initial=await observed('{"ok":true,"job":{"id":"run-1","state":"INCOMPLETE"}}');
  const traces=new Map(),recorder={requestFailures:[],badResponses:[]};
  let documentIdentity='a-b',documentRetired=false,detached=false;
  const realm=vm.createContext({});
  const frame={isDetached:()=>detached,page:()=>page,evaluate:async(fn,arg)=>{
    if(typeof fn==='string')return;
    realm.argument=arg;
    return structuredClone(vm.runInContext('('+fn.toString()+')(argument)',realm));
  },waitForFunction:async()=>{}};
  const page=new EventEmitter();page.isClosed=()=>false;page.frames=()=>[frame];
  page.addInitScript=async({content})=>{
    const start=content.lastIndexOf(')({"key":')+2;const end=content.indexOf('},function strictObject',start)+1;
    page.config=JSON.parse(content.slice(start,end));
    Object.defineProperty(realm,page.config.key,{value:Object.freeze({
      documentIdentity:()=>documentRetired?null:documentIdentity,
      peek:tag=>{const trace=traces.get(tag);return trace?{ready:trace.valid||!!trace.invalid}:null;},
      snapshot:tag=>{const trace=traces.get(tag);return trace?structuredClone(trace):null;},
      take:tag=>{const trace=traces.get(tag);traces.delete(tag);return trace?structuredClone(trace):null;}
    })});
  };
  const observer=await installCdsMetadataTransportObserver(page,recorder,{baseUrl:'http://localhost/carlos',maxTraces:options.maxTraces||4,
    finishTimeoutMillis:options.finishTimeoutMillis});
  function emit(number,changes={}) {
    const tag=page.config.prefix+'-'+(changes.documentIdentity||documentIdentity)+'-'+number;
    traces.set(tag,{...structuredClone(initial.trace),tag,...changes.trace});
    const request={headers:()=>({'x-cds-metadata-trace':tag}),frame:()=>frame,
      url:()=>changes.url || 'http://localhost/carlos/form/cdsImportStatus?runId=run-1',method:()=>changes.method || 'GET',
      postData:()=>changes.body ?? null,resourceType:()=>changes.resourceType || 'fetch',
      failure:()=>({errorText:changes.error||'net::ERR_ABORTED'}),redirectedFrom:()=>changes.redirected?{}:null};
    const response={request:()=>request,status:()=>changes.status || 200,
      headers:()=>changes.headers || {'content-type':'application/json'},finished:changes.finished||(async()=>Error('net::ERR_ABORTED'))};
    page.emit('request',request);page.emit('response',response);
    const entry={url:request.url(),resourceType:'fetch',errorText:request.failure().errorText};
    Object.defineProperty(entry,'requestRef',{value:new WeakRef(request)});
    if(changes.failed!==false){page.emit('requestfailed',request);recorder.requestFailures.push(entry);}
    return {tag,request,response,entry};
  }
  return {page,frame,recorder,observer,traces,emit,
    replaceDocument:()=>{documentIdentity='c-d';page.emit('framenavigated',frame);},
    retireDocument:()=>{documentRetired=true;page.emit('framenavigated',frame);},
    detach:()=>{detached=true;page.emit('framedetached',frame);}};
}

test('qualifies only the exact correlated raw request and preserves its original observation',async()=>{
  const f=await networkFixture(),a=f.emit(1),b=f.emit(2);await Promise.resolve();
  const result=await f.observer.qualifyResponse(a.response,receipt=>proof(null));
  assert.equal(result.qualified,true);assert.equal(isQualifiedCdsMetadataFailure(a.entry),true);
  assert.equal(isQualifiedCdsMetadataFailure(b.entry),false);assert.equal(f.recorder.requestFailures.length,2);
  assert.equal(a.entry.errorText,'net::ERR_ABORTED');
  assert.equal(isQualifiedCdsMetadataFailure({...a.entry}),false);
});

test('bounds a missing terminal signal without inventing a request failure or qualification',async()=>{
  const f=await networkFixture({finishTimeoutMillis:10});let proved=0;
  const a=f.emit(1,{failed:false,finished:()=>new Promise(()=>{})});
  await assert.rejects(f.observer.qualifyResponse(a.response,()=>{proved++;return proof(null);}),/CDS_TRANSPORT_FINISH_TIMEOUT/);
  assert.equal(proved,0);assert.equal(f.recorder.requestFailures.length,0);
  assert.deepEqual(await f.observer.qualifyPending(()=>{proved++;return proof(null);}),[]);
  assert.equal(proved,0);assert.equal(isQualifiedCdsMetadataFailure(a.entry),false);
});

test('uses an exact existing raw terminal failure with the unchanged strict evidence path',async()=>{
  const f=await networkFixture({finishTimeoutMillis:10});let finishedCalls=0;
  const a=f.emit(1,{finished:()=>{finishedCalls++;return new Promise(()=>{});}});await Promise.resolve();
  const result=await f.observer.qualifyResponse(a.response,()=>proof(null));
  assert.equal(result.qualified,true);assert.equal(finishedCalls,0);
  assert.equal(f.recorder.requestFailures.length,1);assert.equal(a.entry.errorText,'net::ERR_ABORTED');
  assert.equal(isQualifiedCdsMetadataFailure(a.entry),true);
});

test('a terminal error object without an actual raw requestfailed event cannot qualify',async()=>{
  const f=await networkFixture({finishTimeoutMillis:10});let proved=0;
  const a=f.emit(1,{failed:false});
  await assert.rejects(f.observer.qualifyResponse(a.response,()=>{proved++;return proof(null);}),/QUALIFIABLE/);
  assert.equal(proved,0);assert.equal(f.recorder.requestFailures.length,0);
  assert.equal(isQualifiedCdsMetadataFailure(a.entry),false);
});

test('an ordinarily finished successful response needs no failure qualification',async()=>{
  const f=await networkFixture({finishTimeoutMillis:10});let proved=0;
  const a=f.emit(1,{failed:false,finished:async()=>null});
  assert.deepEqual(await f.observer.qualifyResponse(a.response,()=>{proved++;return proof(null);}),{qualified:false,finished:true});
  assert.equal(proved,0);assert.equal(f.recorder.requestFailures.length,0);
});

test('the terminal wait cannot be configured without a finite ten-second bound',async()=>{
  for(const finishTimeoutMillis of [0,-1,10001,1.5,Infinity])
    await assert.rejects(networkFixture({finishTimeoutMillis}),/CDS_TRANSPORT_LIMIT_INVALID/);
});

test('eviction while the terminal response is pending cannot publish a held record',async()=>{
  const f=await networkFixture({maxTraces:1,finishTimeoutMillis:1000});let finish,proved=0;
  const a=f.emit(1,{failed:false,finished:()=>new Promise(resolve=>{finish=resolve;})});
  const pending=f.observer.qualifyResponse(a.response,()=>{proved++;return proof(null);});
  f.page.emit('requestfailed',a.request);f.recorder.requestFailures.push(a.entry);
  f.emit(2);finish(Error('net::ERR_ABORTED'));
  await assert.rejects(pending,/CDS_TRANSPORT_RECORD_EVICTED/);
  assert.equal(proved,0);assert.equal(isQualifiedCdsMetadataFailure(a.entry),false);
});

test('eviction during independent proof cannot publish a held record',async()=>{
  const f=await networkFixture({maxTraces:1}),a=f.emit(1);await Promise.resolve();let proved=0;
  await assert.rejects(f.observer.qualifyResponse(a.response,()=>{
    proved++;f.emit(2);return proof(null);
  }),/CDS_TRANSPORT_RECORD_EVICTED/);
  assert.equal(proved,1);assert.equal(isQualifiedCdsMetadataFailure(a.entry),false);
});

test('rejects navigation during proof, including a reload of the same address',async()=>{
  const f=await networkFixture(),a=f.emit(1);await Promise.resolve();
  await assert.rejects(f.observer.qualifyResponse(a.response,()=>{f.replaceDocument();return proof(null);}),/NAVIGATION/);
  assert.equal(isQualifiedCdsMetadataFailure(a.entry),false);
});

test('allows same-document history events before and during independently verified proof',async()=>{
  const f=await networkFixture(),a=f.emit(1);await Promise.resolve();
  f.page.emit('framenavigated',f.frame);
  const result=await f.observer.qualifyResponse(a.response,()=>{f.page.emit('framenavigated',f.frame);return proof(null);});
  assert.equal(result.qualified,true);assert.equal(isQualifiedCdsMetadataFailure(a.entry),true);
  assert.equal(a.entry.errorText,'net::ERR_ABORTED');
});

for(const [name,change] of [
  ['same-address document replacement',f=>f.replaceDocument()],
  ['restored retired document',f=>f.retireDocument()],
  ['detached frame',f=>f.detach()]
]) test('does not qualify '+name+' before or during independent proof',async()=>{
  for(const duringProof of [false,true]) {
    const f=await networkFixture(),a=f.emit(1);await Promise.resolve();let proved=0;
    if(!duringProof)change(f);
    await assert.rejects(f.observer.qualifyResponse(a.response,()=>{proved++;if(duringProof)change(f);return proof(null);}));
    assert.equal(proved,duringProof?1:0);assert.equal(isQualifiedCdsMetadataFailure(a.entry),false);
  }
});

test('cannot substitute a different private document nonce in the request tag',async()=>{
  const f=await networkFixture(),a=f.emit(1,{documentIdentity:'c-d'});await Promise.resolve();let proved=0;
  await assert.rejects(f.observer.qualifyResponse(a.response,()=>{proved++;return proof(null);}),/NAVIGATION/);
  assert.equal(proved,0);assert.equal(isQualifiedCdsMetadataFailure(a.entry),false);
});

test('rechecks a Request abort after EOF while independent proof is running',async()=>{
  const f=await networkFixture(),a=f.emit(1);await Promise.resolve();
  await assert.rejects(f.observer.qualifyResponse(a.response,()=>{f.traces.get(a.tag).aborted=true;return proof(null);}),/EOF_UNPROVEN/);
  assert.equal(isQualifiedCdsMetadataFailure(a.entry),false);
});

test('eviction, redirect, unrecognized failures and tag reuse cannot grant exceptions',async()=>{
  const f=await networkFixture({maxTraces:1}),a=f.emit(1),b=f.emit(2);await Promise.resolve();
  await assert.rejects(f.observer.qualifyResponse(a.response,()=>proof(null)),/UNPROVEN/);
  assert.equal(isQualifiedCdsMetadataFailure(a.entry),false);
  f.emit(2);await assert.rejects(f.observer.qualifyResponse(b.response,()=>proof(null)),/QUALIFIABLE/);
  for(const changes of [{redirected:true},{error:'net::ERR_CONNECTION_RESET'}]) {
    const x=await networkFixture(),r=x.emit(1,changes);await Promise.resolve();
    await assert.rejects(x.observer.qualifyResponse(r.response,()=>proof(null)));
    assert.equal(isQualifiedCdsMetadataFailure(r.entry),false);
  }
});

test('keeps concurrent decoder EOF and parse evidence bound to their own response',async()=>{
  const f=await observed('{"ok":true,"job":{"id":"run-1"}}',{dynamicBodies:{
    'run-A':'{"ok":true,"job":{"id":"run-A","state":"COMPLETE"}}',
    'run-B':'{"ok":true,"job":{"id":"run-B","state":"INCOMPLETE"}}'}});
  await vm.runInContext(`Promise.all(['run-A','run-B'].map(async id=>{const response=await fetch('/carlos/form/cdsImportStatus?runId='+id);const d=new TextDecoder('utf-8',{fatal:true});let text='';await response.body.pipeTo(new WritableStream({write(c){text+=d.decode(c,{stream:true});}}));text+=d.decode();return JSON.parse(text);}));`,f.sandbox);
  for(let i=1;i<=2;i++){const trace=vm.runInContext(`__trace.take(${JSON.stringify(f.tags[i])})`,f.sandbox);const expected=i===1?'run-A':'run-B';assert.equal(trace.receipt.runId,expected);assert.equal(validateProof(trace,proof(trace,{runId:expected})).classification,'CDS_METADATA_EOF_CONFIRMED');}
});
test('rejects additional unrelated decoder output inside a sink callback',async()=>{
  const {trace}=await observed('{"ok":true,"job":{"id":"run-1"}}',{unrelatedDecoder:true});
  assert.equal(trace.valid,false);assert.throws(()=>validateProof(trace,proof(trace)));
});

test('binds safe page identities and cursor proof without retaining source text',async()=>{
  const key='a'.repeat(64),body=JSON.stringify({ok:true,rows:[{patientKey:key,copyKey:'real',state:'COMPLETE',held:false,targetDemographicNo:42,sourcePatientId:'NEVER RETAIN ORIGINAL SOURCE TEXT'}],nextCursor:key});
  const {trace}=await observed(body,{section:'patients'});
  assert.equal(trace.valid,true);assert.equal(trace.receipt.page.rows[0].patientKey,key);
  assert.equal(JSON.stringify(trace).includes('NEVER RETAIN'),false);
  assert.throws(()=>validateProof(trace,proof(trace)),/PAGE_PROOF/);
  assert.throws(()=>validateProof(trace,proof(trace,{page:{...trace.receipt.page,afterKey:'different'}})),/PAGE_PROOF/);
  assert.equal(validateProof(trace,proof(trace,{page:trace.receipt.page})).classification,'CDS_METADATA_EOF_CONFIRMED');
});

const acceptedRun='12345678-1234-4234-8234-123456789abc';
const acceptedUpload='22345678-1234-4234-8234-123456789abc';
async function expectedProxyFixture(options={}) {
  const f=await networkFixture(options),token='a'.repeat(32);
  const selected=f.emit(1,{url:'http://localhost/carlos/form/cdsImportSubmit',method:'POST',status:504,
    body:'apiVersion=2&uploadId='+acceptedUpload,headers:{'content-type':'text/html','x-owned-proxy':token},
    ...(options.requestChanges || {})});
  const badResponse={status:selected.response.status(),url:selected.request.url(),method:selected.request.method(),resourceType:selected.request.resourceType()};
  f.recorder.badResponses.push(badResponse);await Promise.resolve();
  const fault={kind:'nginx',accepted:true,proxyVerified:true,status:504,runId:acceptedRun,uploadId:acceptedUpload,
    request:selected.request,response:selected.response,proxyHeader:'X-Owned-Proxy',proxyToken:token,badResponse};
  return{...f,selected,fault};
}

test('a verified exact proxy504 is excluded only from successful EOF qualification and remains raw for strict fault audit',async()=>{
  const f=await expectedProxyFixture();let proved=0;
  for(let repeat=0;repeat<2;repeat++)assert.deepEqual(await f.observer.qualifyPending(()=>{proved++;return proof(null);},
    {expectedHttpFault:f.fault}),[]);
  assert.equal(proved,0);assert.equal(isQualifiedCdsMetadataFailure(f.selected.entry),false);
  assert.equal(f.recorder.requestFailures[0],f.selected.entry);assert.equal(f.recorder.badResponses[0],f.fault.badResponse);
  const h=require('./lib/playwright-harness'),{assertScopedFault}=require('./demographic-cds-lifecycle-playwright-checks');
  const recorder={...h.createRecorder(),...f.recorder};
  assert.throws(()=>h.assertStrictPage(recorder));assertScopedFault(recorder,f.fault);
  recorder.badResponses.push({...f.fault.badResponse});assert.throws(()=>assertScopedFault(recorder,f.fault));
  recorder.badResponses.pop();recorder.requestFailures.push({errorText:'net::ERR_CONNECTION_RESET',requestRef:new WeakRef({})});
  assert.throws(()=>assertScopedFault(recorder,f.fault));
});

test('no expected fault option leaves the observed proxy504 subject to the unchanged strict EOF refusal',async()=>{
  const f=await expectedProxyFixture();let proved=0;
  await assert.rejects(f.observer.qualifyPending(()=>{proved++;return proof(null);}),/FAILURE_NOT_QUALIFIABLE/);
  assert.equal(proved,0);assert.equal(isQualifiedCdsMetadataFailure(f.selected.entry),false);
});

test('exact proxy scope survives bounded-record eviction without exempting any other metadata',async()=>{
  const f=await expectedProxyFixture({maxTraces:1}),metadata=f.emit(2);await Promise.resolve();let proved=0;
  const first=await f.observer.qualifyPending(()=>{proved++;return proof(null);},{expectedHttpFault:f.fault});
  assert.equal(first.length,1);assert.equal(first[0].status,200);assert.equal(proved,1);
  assert.equal(isQualifiedCdsMetadataFailure(metadata.entry),true);assert.equal(isQualifiedCdsMetadataFailure(f.selected.entry),false);
  assert.deepEqual(await f.observer.qualifyPending(()=>{proved++;return proof(null);},{expectedHttpFault:f.fault}),[]);
  assert.equal(proved,1);
});

test('a normally finished exact proxy504 still receives no metadata EOF qualification',async()=>{
  const f=await expectedProxyFixture({requestChanges:{failed:false,finished:async()=>null}});
  assert.deepEqual(await f.observer.qualifyPending(()=>{throw Error('A504 must not request EOF proof');},{expectedHttpFault:f.fault}),[]);
  assert.equal(f.recorder.requestFailures.length,0);assert.equal(isQualifiedCdsMetadataFailure(f.selected.entry),false);
});

for(const [name,change]of[
  ['unaccepted run',f=>{f.fault.accepted=false;}],
  ['unverified proxy',f=>{f.fault.proxyVerified=false;}],
  ['unknown fault kind',f=>{f.fault.kind='ignored';}],
  ['malformed run',f=>{f.fault.runId='not-a-run';}],
  ['malformed upload',f=>{f.fault.uploadId='not-an-upload';}],
  ['another response request',f=>{f.fault.response={...f.selected.response,request:()=>({})};}],
  ['another response status',f=>{f.fault.response={...f.selected.response,status:()=>502};}],
  ['missing owned header',f=>{f.fault.response={...f.selected.response,headers:()=>({'content-type':'text/html'})};}],
  ['wrong owned token',f=>{f.fault.proxyToken='b'.repeat(32);}],
  ['another origin',f=>{f.selected.request.url=()=> 'http://other.invalid/carlos/form/cdsImportSubmit';}],
  ['URL credentials',f=>{f.selected.request.url=()=> 'http://private:secret@localhost/carlos/form/cdsImportSubmit';f.fault.badResponse.url=f.selected.request.url();}],
  ['URL query',f=>{f.selected.request.url=()=> 'http://localhost/carlos/form/cdsImportSubmit?apiVersion=2';f.fault.badResponse.url=f.selected.request.url();}],
  ['URL fragment',f=>{f.selected.request.url=()=> 'http://localhost/carlos/form/cdsImportSubmit#private';f.fault.badResponse.url=f.selected.request.url();}],
  ['another owner route',f=>{f.selected.request.url=()=> 'http://localhost/carlos/form/cdsImportStatus';}],
  ['another method',f=>{f.selected.request.method=()=> 'GET';}],
  ['document resource',f=>{f.selected.request.resourceType=()=> 'document';}],
  ['another receiver page',f=>{f.frame.page=()=>({});}],
  ['closed receiver page',f=>{f.page.isClosed=()=>true;}],
  ['detached receiver frame',f=>{f.detach();}],
  ['redirected request',f=>{f.selected.request.redirectedFrom=()=>({});}],
  ['wrong body contract',f=>{f.selected.request.postData=()=> 'apiVersion=1&uploadId='+acceptedUpload;}],
  ['duplicate body contract',f=>{f.selected.request.postData=()=> 'apiVersion=2&apiVersion=2&uploadId='+acceptedUpload;}],
  ['another body upload',f=>{f.selected.request.postData=()=> 'apiVersion=2&uploadId='+acceptedRun;}],
  ['duplicate body upload',f=>{f.selected.request.postData=()=> 'apiVersion=2&uploadId='+acceptedUpload+'&uploadId='+acceptedUpload;}],
  ['oversized request body',f=>{f.selected.request.postData=()=> 'x'.repeat(16385);}],
  ['absent request body',f=>{f.selected.request.postData=()=>null;}],
  ['unretained raw response',f=>{f.fault.badResponse={...f.fault.badResponse};}],
  ['duplicate raw response',f=>{f.recorder.badResponses.push(f.fault.badResponse);}],
  ['unexpected raw failure',f=>{f.selected.entry.errorText='net::ERR_CONNECTION_RESET';}],
  ['duplicate raw cancellation',f=>{f.recorder.requestFailures.push(f.selected.entry);}]
])test('expected proxy scope refuses '+name,async()=>{
  const f=await expectedProxyFixture();change(f);let proved=0;
  await assert.rejects(f.observer.qualifyPending(()=>{proved++;return proof(null);},{expectedHttpFault:f.fault}));
  assert.equal(proved,0);assert.equal(isQualifiedCdsMetadataFailure(f.selected.entry),false);
});

test('the exact expected proxy never excuses an unrelated non-JSON504 or malformed successful metadata',async()=>{
  for(const another504 of[true,false]){
    const f=await expectedProxyFixture();
    const other=f.emit(2,another504?{url:f.selected.request.url(),method:'POST',status:504,
      body:f.selected.request.postData(),headers:f.selected.response.headers()}:{trace:{valid:false,invalid:'METADATA_JSON_INVALID'}});
    await Promise.resolve();
    await assert.rejects(f.observer.qualifyPending(()=>proof(null),{expectedHttpFault:f.fault}));
    assert.equal(isQualifiedCdsMetadataFailure(f.selected.entry),false);assert.equal(isQualifiedCdsMetadataFailure(other.entry),false);
    assert.equal(f.recorder.requestFailures.length,2);
  }
});

test('ambiguous tag reuse cannot receive the expected HTTP exclusion',async()=>{
  const f=await expectedProxyFixture();f.emit(1);await Promise.resolve();
  await assert.rejects(f.observer.qualifyPending(()=>proof(null),{expectedHttpFault:f.fault}),/EXPECTED_HTTP_FAULT_UNPROVEN/);
  assert.equal(isQualifiedCdsMetadataFailure(f.selected.entry),false);
});

test('the originally observed route cannot be replaced by later request getters',async()=>{
  const f=await expectedProxyFixture({requestChanges:{url:'http://localhost/carlos/form/cdsImportControl'}});
  f.selected.request.url=()=> 'http://localhost/carlos/form/cdsImportSubmit';f.fault.badResponse.url=f.selected.request.url();
  await assert.rejects(f.observer.qualifyPending(()=>proof(null),{expectedHttpFault:f.fault}),/EXPECTED_HTTP_FAULT_UNPROVEN/);
  assert.equal(isQualifiedCdsMetadataFailure(f.selected.entry),false);
});
