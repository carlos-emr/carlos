/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),{EventEmitter}=require('node:events');
const {clickLabelOutput}=require('./lib/label-file-output');
const {createRecorder,assertStrictPage}=require('./lib/playwright-harness');
const URL='https://carlos.test/carlos/label';
function fixture(){
 const context=new EventEmitter(),page=new EventEmitter(),popup=new EventEmitter();let closed=false,address='about:blank';
 popup.url=()=>address;const frame={page:()=>popup};popup.mainFrame=()=>frame;
 popup.waitForURL=async predicate=>{assert(predicate(URL));address=URL;};popup.waitForLoadState=async()=>{};
 popup.close=async()=>{closed=true;};popup.isClosed=()=>closed;
 const response=(finished=async()=>null,type='application/pdf')=>({url:()=>URL,status:()=>200,headers:()=>({'content-type':type}),finished,
  request:()=>({isNavigationRequest:()=>true,frame:()=>frame})});
 const file=(failure=async()=>null,url=URL)=>({failure,url:()=>url});
 const click=fn=>({scrollIntoViewIfNeeded:async()=>{},click:fn});
 return {context,page,popup,response,file,click,closed:()=>closed};
}
async function microtasks(){for(let i=0;i<8;i++)await Promise.resolve();}
function clean(f){assert.equal(f.context.listenerCount('page'),0);assert.equal(f.context.listenerCount('response'),0);for(const event of ['request','requestfinished','requestfailed'])assert.equal(f.context.listenerCount(event),0);assert.equal(f.page.listenerCount('download'),0);assert.equal(f.page.listenerCount('popup'),0);assert.equal(f.popup.listenerCount('download'),0);}

test('a download on the blank popup is observed before click returns and must finish',async()=>{
 const f=fixture();let finish;const completed=new Promise(r=>{finish=r});
 const work=clickLabelOutput(f.page,f.click(async()=>{f.page.emit('popup',f.popup);f.popup.emit('download',f.file(()=>completed));}),{context:f.context,timeout:100});
 let settled=false;work.then(()=>{settled=true});await microtasks();assert.equal(settled,false);assert.equal(f.closed(),false);finish(null);
 const result=await work;assert.equal(result.kind,'download');assert.equal(result.url,URL);assert.equal(result.page,f.popup);await result.dispose();assert.equal(f.closed(),true);clean(f);
});
test('cold main-response navigation beyond5seconds remains within the original30secondbudget',async t=>{
 t.mock.timers.enable({apis:['Date','setTimeout']});const f=fixture();let settled=false;
 const work=clickLabelOutput(f.page,f.click(async()=>{f.page.emit('popup',f.popup);}),{context:f.context,timeout:30000});work.then(()=>{settled=true});
 await microtasks();t.mock.timers.tick(6000);await microtasks();assert.equal(settled,false);assert.equal(f.closed(),false);
 f.context.emit('response',f.response());const result=await work;assert.equal(result.url,URL);assert.equal(result.kind,'popup');await result.dispose();clean(f);
});
test('stable inlinePDF requires its actual response to complete before return',async()=>{
 const f=fixture();let finish;const completed=new Promise(r=>{finish=r});let settled=false;
 const work=clickLabelOutput(f.page,f.click(async()=>{f.page.emit('popup',f.popup);f.context.emit('response',f.response(()=>completed));}),{context:f.context,timeout:100});work.then(()=>{settled=true});await microtasks();assert.equal(settled,false);finish(null);
 const result=await work;assert.equal(result.kind,'popup');assert.equal(result.url,URL);await result.dispose();clean(f);
});
test('opener download completes without a popup',async()=>{
 const f=fixture(),result=await clickLabelOutput(f.page,f.click(async()=>f.page.emit('download',f.file())),{context:f.context,timeout:100});
 assert.equal(result.kind,'download');assert.equal(result.page,null);await result.dispose();clean(f);
});
test('coexisting popup/opener outputs drain before the popup closes',async()=>{
 const f=fixture();let finish;const completed=new Promise(r=>{finish=r});
 const result=await clickLabelOutput(f.page,f.click(async()=>{f.page.emit('popup',f.popup);f.context.emit('response',f.response(()=>completed));f.page.emit('download',f.file());}),{context:f.context,timeout:100});
 const disposal=result.dispose();await microtasks();assert.equal(f.closed(),false);finish(null);await disposal;assert.equal(f.closed(),true);clean(f);
});
test('an initial response emitted before opener popup event is still the popup own main response',async()=>{
 const f=fixture(),result=await clickLabelOutput(f.page,f.click(async()=>{f.context.emit('response',f.response());f.page.emit('popup',f.popup);}),{context:f.context,timeout:100});
 assert.equal(result.url,URL);await result.dispose();clean(f);
});
test('HTML settings wait for their own navigation and DOM',async()=>{
 const f=fixture();let navigated=false,loaded=false;f.popup.waitForURL=async predicate=>{assert(predicate(URL));navigated=true};f.popup.waitForLoadState=async()=>{assert(navigated);loaded=true};
 const result=await clickLabelOutput(f.page,f.click(async()=>{f.page.emit('popup',f.popup);f.context.emit('response',f.response(async()=>null,'text/html;charset=UTF-8'));}),{context:f.context,timeout:100});
 assert(loaded);await result.dispose();clean(f);
});
test('a no-op click fails on the configured budget and leaves no outcome listeners',async()=>{
 const f=fixture();await assert.rejects(clickLabelOutput(f.page,f.click(async()=>{}),{context:f.context,timeout:10}),/no completed label output within 10ms/);clean(f);
});
test('a click failure retains its exact original error and closes an already-open popup',async()=>{
 const f=fixture(),original=new Error('button handler failed');await assert.rejects(clickLabelOutput(f.page,f.click(async()=>{f.page.emit('popup',f.popup);throw original}),{context:f.context,timeout:100}),e=>e===original);assert(f.closed());clean(f);
});
test('failed native download remains a failure and retains its cause',async()=>{
 const f=fixture();await assert.rejects(clickLabelOutput(f.page,f.click(async()=>{f.page.emit('popup',f.popup);f.popup.emit('download',f.file(async()=> 'server disconnected'));}),{context:f.context,timeout:100}),e=>String(e).includes('failed')&&(e.cause?.message==='server disconnected'||e.errors?.some(x=>x.cause?.message==='server disconnected')));assert(f.closed());clean(f);
});
test('a failed response with no completed download is not accepted or suppressed',async()=>{
 const f=fixture(),original=new Error('response transport failed');await assert.rejects(clickLabelOutput(f.page,f.click(async()=>{f.page.emit('popup',f.popup);f.context.emit('response',f.response(async()=>original));}),{context:f.context,timeout:10}),e=>String(e).includes('failed')||e.cause===original);assert(f.closed());clean(f);
});
test('startup browser errors remain in the strict recorder before click resolution',async()=>{
 const f=fixture(),recorder=createRecorder();const result=await clickLabelOutput(f.page,f.click(async()=>{f.page.emit('popup',f.popup);f.popup.emit('pageerror',new Error('startup failed'));f.popup.emit('download',f.file());}),{context:f.context,recorder,timeout:100});
 assert.throws(()=>assertStrictPage(recorder),/startup failed/);await result.dispose();clean(f);
});
test('cleanup failure cannot replace or erase the original click error',async()=>{
 const f=fixture(),original=new Error('original click failure'),cleanup=new Error('close failed');f.popup.close=async()=>{throw cleanup};
 await assert.rejects(clickLabelOutput(f.page,f.click(async()=>{f.page.emit('popup',f.popup);throw original}),{context:f.context,timeout:100}),e=>e instanceof AggregateError&&e.errors[0]===original&&e.errors[1].errors.includes(cleanup));clean(f);
});
test('a failed popup close is reported after an otherwise complete output',async()=>{
 const f=fixture(),result=await clickLabelOutput(f.page,f.click(async()=>{f.page.emit('popup',f.popup);f.popup.emit('download',f.file());}),{context:f.context,timeout:100});f.popup.close=async()=>{throw new Error('close failed')};await assert.rejects(result.dispose(),/cleanup/);clean(f);
});

test('exact aborted document handoff needs a completed sameURL native download',async()=>{
 const f=fixture(),request=mainRequest(f),response=f.response();response.request=()=>request;const result=await clickLabelOutput(f.page,f.click(async()=>{f.context.emit('request',request);f.page.emit('popup',f.popup);f.context.emit('response',response);f.context.emit('requestfailed',request);f.popup.emit('download',f.file());}),{context:f.context,timeout:100});
 assert.equal(result.kind,'download');await result.dispose();clean(f);assert(result.evidence.some(x=>x.event==='download-complete'));
});
for(const [label,message,url]of [['differentURL','net::ERR_ABORTED',URL+'-other'],['otherFailure','net::ERR_CONNECTION_RESET',URL]])test('coexisting '+label+' failure cannot hide behind a completed download',async()=>{
 const f=fixture();const result=await clickLabelOutput(f.page,f.click(async()=>{f.page.emit('popup',f.popup);f.context.emit('response',f.response(async()=>new Error(message)));f.page.emit('download',f.file(async()=>null,url));}),{context:f.context,timeout:100});
 await assert.rejects(result.dispose(),/output completion failed/);clean(f);
});
test('a subframe response is not proof that the selected label popup loaded',async()=>{
 const f=fixture(),response=f.response();response.request=()=>({isNavigationRequest:()=>true,frame:()=>({page:()=>f.popup})});
 await assert.rejects(clickLabelOutput(f.page,f.click(async()=>{f.page.emit('popup',f.popup);f.context.emit('response',response);}),{context:f.context,timeout:10}),/no completed label output/);assert(f.closed());clean(f);
});

test('a redirect is not mistaken for the final output or left waiting for the oldURL',async()=>{
 const f=fixture(),redirect=f.response(),final=f.response();redirect.status=()=>302;redirect.headers=()=>({'content-type':'text/html'});redirect.url=()=>URL+'-redirect';
 const result=await clickLabelOutput(f.page,f.click(async()=>{f.context.emit('response',redirect);f.page.emit('popup',f.popup);f.context.emit('response',final);}),{context:f.context,timeout:100});
 assert.equal(result.url,URL);await result.dispose();assert(result.evidence.some(e=>e.event==='redirect-complete'));clean(f);
});
test('a redirect with no final response is not a completed label',async()=>{
 const f=fixture(),redirect=f.response();redirect.status=()=>302;redirect.headers=()=>({'content-type':'text/html'});
 await assert.rejects(clickLabelOutput(f.page,f.click(async()=>{f.page.emit('popup',f.popup);f.context.emit('response',redirect);}),{context:f.context,timeout:10}),/no completed label output/);clean(f);
});
test('observer API failure propagates with a completed concurrent download',async()=>{
 const f=fixture(),original=new Error('observer failed');const response={request(){throw original}};
 await assert.rejects(clickLabelOutput(f.page,f.click(async()=>{f.context.emit('response',response);f.page.emit('download',f.file());}),{context:f.context,timeout:100}),e=>e===original||e.errors?.includes(original));clean(f);
});
test('throwing strict recorder registration is caught and cleans the new popup',async()=>{
 const f=fixture(),original=new Error('registration failed'),on=f.popup.on.bind(f.popup);let calls=0;f.popup.on=(...args)=>{if(++calls===2)throw original;return on(...args)};
 await assert.rejects(clickLabelOutput(f.page,f.click(async()=>f.page.emit('popup',f.popup)),{context:f.context,recorder:createRecorder(),timeout:100}),e=>e===original||e.errors?.includes(original));assert(f.closed());clean(f);
});

test('frozen common helper returns blank at5seconds while label-only capture still waits for actual output',async t=>{
 t.mock.timers.enable({apis:['Date','setTimeout']});const old=fixture();old.popup.waitForURL=async(_,options)=>new Promise((_,reject)=>setTimeout(()=>reject(new Error('URL wait timed out')),options.timeout));
 const {clickDownloadsOrOpens}=require('./lib/playwright-ui');const before=clickDownloadsOrOpens(old.page,old.click(async()=>old.context.emit('page',old.popup)),{context:old.context,timeout:30000});await microtasks();t.mock.timers.tick(5000);await microtasks();assert.equal((await before).url,'about:blank');await old.popup.close();clean(old);
 const next=fixture();let settled=false;const after=clickLabelOutput(next.page,next.click(async()=>next.page.emit('popup',next.popup)),{context:next.context,timeout:30000});after.then(()=>{settled=true});await microtasks();t.mock.timers.tick(6000);await microtasks();assert.equal(settled,false);next.context.emit('response',next.response());const result=await after;assert.equal(result.url,URL);await result.dispose();clean(next);
});
test('actual label caller retains PDF byte checks and disposes successful output',async()=>{
 const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');const source=fs.readFileSync(path.join(__dirname,'demographic-labels-playwright-checks.js'),'utf8');
 const {assertIsPdf}=require('./demographic-labels-playwright-checks');let disposed=0;const evidence=[],entry={count:async()=>1};
 const pdf=Buffer.from('%PDF-1.4\n'+'x'.repeat(500)+'\n%%EOF\n');const produced={url:URL,kind:'popup',evidence,dispose:async()=>{disposed++}};
 const sandbox={assert,assertIsPdf,clickLabelOutput:async()=>produced};vm.createContext(sandbox);vm.runInContext(source.slice(source.indexOf('async function checkItem('),source.indexOf('\nasync function main('))+';globalThis.invoke=checkItem;',sandbox);
 const context={request:{get:async url=>{assert.equal(url,URL);return {status:()=>200,headers:()=>({'content-type':'application/pdf'}),body:async()=>pdf}}}};
 const result=await sandbox.invoke(context,{}, {locator:()=>({first:()=>entry})},{label:'PDF Label',pdf:true},{},100);assert.equal(result.bytes,pdf.length);assert.equal(result.capture,evidence);assert.equal(disposed,1);
});
test('actual label caller preserves a failing PDF assertion together with cleanup failure',async()=>{
 const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');const source=fs.readFileSync(path.join(__dirname,'demographic-labels-playwright-checks.js'),'utf8');
 const {assertIsPdf}=require('./demographic-labels-playwright-checks'),cleanup=new Error('cleanup failed'),entry={count:async()=>1};
 const sandbox={assert,assertIsPdf,AggregateError,clickLabelOutput:async()=>({url:URL,kind:'popup',dispose:async()=>{throw cleanup}})};vm.createContext(sandbox);vm.runInContext(source.slice(source.indexOf('async function checkItem('),source.indexOf('\nasync function main('))+';globalThis.invoke=checkItem;',sandbox);
 const context={request:{get:async()=>({status:()=>500,headers:()=>({'content-type':'text/html'}),body:async()=>Buffer.from('error')})}};
 await assert.rejects(sandbox.invoke(context,{}, {locator:()=>({first:()=>entry})},{label:'PDF Label',pdf:true},{},100),e=>e instanceof AggregateError&&e.errors[0].message.includes('answered HTTP500'.replace('HTTP500','HTTP 500'))&&e.errors[1]===cleanup);
});

test('an owned HTTP500 response cannot hide behind a concurrent successful download',async()=>{
 const f=fixture(),response=f.response();response.status=()=>500;
 const result=await clickLabelOutput(f.page,f.click(async()=>{f.context.emit('response',response);f.page.emit('popup',f.popup);f.page.emit('download',f.file());}),{context:f.context,timeout:100});
 await assert.rejects(result.dispose(),e=>e instanceof AggregateError&&e.errors.some(x=>x.message.includes('HTTP 500')));clean(f);
});

test('a foreign-opener popup is neither accepted nor closed',async()=>{
 const f=fixture();await assert.rejects(clickLabelOutput(f.page,f.click(async()=>{f.context.emit('page',f.popup);f.context.emit('response',f.response());}),{context:f.context,timeout:10}),/no completed label output/);assert.equal(f.closed(),false);clean(f);
});
test('sameURL aborted response on another Page cannot use the opener completed download',async()=>{
 const f=fixture();const result=await clickLabelOutput(f.page,f.click(async()=>{f.page.emit('popup',f.popup);f.context.emit('response',f.response(async()=>new Error('net::ERR_ABORTED')));f.page.emit('download',f.file());}),{context:f.context,timeout:100});
 await assert.rejects(result.dispose(),e=>e instanceof AggregateError&&e.errors.some(x=>x.message==='net::ERR_ABORTED'));clean(f);
});

function mainRequest(f){return {isNavigationRequest:()=>true,frame:()=>f.popup.mainFrame(),url:()=>URL,failure:()=>({errorText:'net::ERR_ABORTED'})};}
test('an owned main request before response headers must finish before disposal closes its popup',async()=>{
 const f=fixture(),request=mainRequest(f);const result=await clickLabelOutput(f.page,f.click(async()=>{f.context.emit('request',request);f.page.emit('popup',f.popup);f.page.emit('download',f.file());}),{context:f.context,timeout:100});
 const disposed=result.dispose();await microtasks();assert.equal(f.closed(),false);f.context.emit('requestfinished',request);await disposed;assert(f.closed());clean(f);
});
test('a response arriving during disposal joins the accepted request drain',async()=>{
 const f=fixture(),request=mainRequest(f);let finish;const completed=new Promise(r=>{finish=r});
 const result=await clickLabelOutput(f.page,f.click(async()=>{f.page.emit('popup',f.popup);f.context.emit('request',request);f.page.emit('download',f.file());}),{context:f.context,timeout:100});
 const disposed=result.dispose();await microtasks();f.context.emit('response',f.response(()=>completed));f.context.emit('requestfinished',request);await microtasks();assert.equal(f.closed(),false);finish(null);await disposed;assert(f.closed());clean(f);
});
test('a failed owned request cannot use another Page sameURL completed file',async()=>{
 const f=fixture(),request=mainRequest(f);const result=await clickLabelOutput(f.page,f.click(async()=>{f.page.emit('popup',f.popup);f.context.emit('request',request);f.page.emit('download',f.file());}),{context:f.context,timeout:100});
 const disposed=result.dispose();await microtasks();f.context.emit('requestfailed',request);await assert.rejects(disposed,e=>e instanceof AggregateError&&e.errors.some(x=>x.message==='net::ERR_ABORTED'));clean(f);
});
test('exact owning Page completed download is required for aborted main-request handoff',async()=>{
 const f=fixture(),request=mainRequest(f),response=f.response();response.request=()=>request;const result=await clickLabelOutput(f.page,f.click(async()=>{f.context.emit('request',request);f.page.emit('popup',f.popup);f.context.emit('response',response);f.context.emit('requestfailed',request);f.popup.emit('download',f.file());}),{context:f.context,timeout:100});
 await result.dispose();assert(f.closed());clean(f);
});

const EARLY_FRAME=['Frame for this navigation request is not available, because the request','was issued before the frame is created. You can check whether the request','is a navigation request by calling isNavigationRequest() method.'].join('\n');
test('documented early request.frame state retries at the owned popup without losing request drain',async()=>{
 const f=fixture(),request=mainRequest(f);let ready=false;request.frame=()=>{if(!ready)throw new Error(EARLY_FRAME);return f.popup.mainFrame()};
 const result=await clickLabelOutput(f.page,f.click(async()=>{f.context.emit('request',request);ready=true;f.page.emit('popup',f.popup);f.page.emit('download',f.file());}),{context:f.context,timeout:100});
 const disposed=result.dispose();await microtasks();assert.equal(f.closed(),false);f.context.emit('requestfinished',request);await disposed;assert(f.closed());clean(f);
});
test('early response frame association retries at the owned popup',async()=>{
 const f=fixture(),request=mainRequest(f),response=f.response();let ready=false;request.frame=()=>{if(!ready)throw new Error(EARLY_FRAME);return f.popup.mainFrame()};response.request=()=>request;
 const result=await clickLabelOutput(f.page,f.click(async()=>{f.context.emit('request',request);f.context.emit('response',response);ready=true;f.page.emit('popup',f.popup);f.context.emit('requestfinished',request);}),{context:f.context,timeout:100});
 assert.equal(result.kind,'popup');await result.dispose();clean(f);
});
test('a deferred foreign request is not adopted or closed when its frame becomes available',async()=>{
 const f=fixture(),request=mainRequest(f);let ready=false;request.frame=()=>{if(!ready)throw new Error(EARLY_FRAME);return f.popup.mainFrame()};
 const result=await clickLabelOutput(f.page,f.click(async()=>{f.context.emit('request',request);ready=true;f.context.emit('requestfinished',request);f.page.emit('download',f.file());}),{context:f.context,timeout:100});
 await result.dispose();assert.equal(f.closed(),false);clean(f);
});
test('unresolved early frame work prevents clean disposal rather than being ignored',async()=>{
 const f=fixture(),request=mainRequest(f);request.frame=()=>{throw new Error(EARLY_FRAME)};
 const result=await clickLabelOutput(f.page,f.click(async()=>{f.context.emit('request',request);f.page.emit('download',f.file());}),{context:f.context,timeout:10});
 await assert.rejects(result.dispose(),e=>e instanceof AggregateError&&e.errors.some(x=>/did not drain/.test(x.message)));assert.equal(f.closed(),false);clean(f);
});
test('unknown frame errors remain fatal instead of entering the documented deferral path',async()=>{
 const f=fixture(),request=mainRequest(f),original=new Error('Unknown frame lookup failure');request.frame=()=>{throw original};
 await assert.rejects(clickLabelOutput(f.page,f.click(async()=>{f.context.emit('request',request);f.page.emit('download',f.file());}),{context:f.context,timeout:100}),e=>e===original||e.errors?.includes(original));clean(f);
});

test('actual aborted Request plus completed native file never waits on nonexistent document completion',async()=>{
 const f=fixture(),request=mainRequest(f);let finishedCalls=0;const response=f.response(async()=>{finishedCalls++;throw new Error('Target page, context or browser has been closed')});response.request=()=>request;
 const result=await clickLabelOutput(f.page,f.click(async()=>{f.context.emit('request',request);f.page.emit('popup',f.popup);f.context.emit('response',response);f.context.emit('requestfailed',request);f.popup.emit('download',f.file());}),{context:f.context,timeout:100});
 await result.dispose();assert.equal(finishedCalls,0);assert(result.evidence.some(e=>e.event==='native-request-handoff'));clean(f);
});
test('actual aborted Request plus completed file drains a response.finished that would never settle',async()=>{
 const f=fixture(),request=mainRequest(f),response=f.response(()=>new Promise(()=>{}));response.request=()=>request;
 const result=await clickLabelOutput(f.page,f.click(async()=>{f.context.emit('request',request);f.page.emit('popup',f.popup);f.context.emit('response',response);f.context.emit('requestfailed',request);f.popup.emit('download',f.file());}),{context:f.context,timeout:100});
 await result.dispose();assert(f.closed());clean(f);
});
test('HTTP500 is fatal before finished even with its own actual abort and successful native file',async()=>{
 const f=fixture(),request=mainRequest(f);let calls=0;const response=f.response(async()=>{calls++;throw new Error('Target page, context or browser has been closed')});response.status=()=>500;response.request=()=>request;
 const result=await clickLabelOutput(f.page,f.click(async()=>{f.context.emit('request',request);f.page.emit('popup',f.popup);f.context.emit('response',response);f.context.emit('requestfailed',request);f.popup.emit('download',f.file());}),{context:f.context,timeout:100});
 await assert.rejects(result.dispose(),e=>e instanceof AggregateError&&e.errors.some(x=>x.message.includes('HTTP 500')));assert.equal(calls,0);clean(f);
});
test('unknown actual Request failure cannot use a successful samePage sameURL download',async()=>{
 const f=fixture(),request=mainRequest(f),response=f.response();request.failure=()=>({errorText:'net::ERR_CONNECTION_RESET'});response.request=()=>request;
 const result=await clickLabelOutput(f.page,f.click(async()=>{f.context.emit('request',request);f.page.emit('popup',f.popup);f.context.emit('response',response);f.context.emit('requestfailed',request);f.popup.emit('download',f.file());}),{context:f.context,timeout:100});
 await assert.rejects(result.dispose(),e=>e instanceof AggregateError&&e.errors.some(x=>x.message==='net::ERR_CONNECTION_RESET'));clean(f);
});
for(const message of ['Target page, context or browser has been closed','TargetClosedError','unknown response completion failure'])test('a normally finished Request cannot hide '+message+' behind its native file',async()=>{
 const f=fixture(),request=mainRequest(f),response=f.response(async()=>{throw new Error(message)});response.request=()=>request;
 const result=await clickLabelOutput(f.page,f.click(async()=>{f.context.emit('request',request);f.page.emit('popup',f.popup);f.context.emit('response',response);f.context.emit('requestfinished',request);f.popup.emit('download',f.file());}),{context:f.context,timeout:100});
 await assert.rejects(result.dispose(),e=>e instanceof AggregateError&&e.errors.some(x=>x.message===message));clean(f);
});
test('manual close before an incomplete download cannot certify output or erase cancellation',async()=>{
 const f=fixture(),request=mainRequest(f),response=f.response();response.request=()=>request;let finish;const file=f.file(()=>new Promise(resolve=>{finish=resolve}));
 const work=clickLabelOutput(f.page,f.click(async()=>{f.context.emit('request',request);f.page.emit('popup',f.popup);f.context.emit('response',response);f.context.emit('requestfailed',request);f.popup.emit('download',file);}),{context:f.context,timeout:100});work.catch(()=>{});await microtasks();await f.popup.close();finish('canceled');await assert.rejects(work,e=>e instanceof AggregateError&&e.errors.some(x=>x.cause?.message==='canceled'));clean(f);
});
test('a successful file cannot bypass an unknown pending own Request with no actual abort',async()=>{
 const f=fixture(),request=mainRequest(f),response=f.response();response.request=()=>request;
 const result=await clickLabelOutput(f.page,f.click(async()=>{f.context.emit('request',request);f.page.emit('popup',f.popup);f.context.emit('response',response);f.popup.emit('download',f.file());}),{context:f.context,timeout:10});
 await assert.rejects(result.dispose(),e=>e instanceof AggregateError&&e.errors.some(x=>/did not drain/.test(x.message)));clean(f);
});
test('one completed file cannot account for two samePage sameURL aborted Requests',async()=>{
 const f=fixture(),requests=[mainRequest(f),mainRequest(f)];
 await assert.rejects(clickLabelOutput(f.page,f.click(async()=>{f.page.emit('popup',f.popup);for(const request of requests){const response=f.response();response.request=()=>request;f.context.emit('request',request);f.context.emit('response',response);f.context.emit('requestfailed',request);}f.popup.emit('download',f.file());}),{context:f.context,timeout:100}),e=>e.message.includes('Ambiguous')||e.errors?.some(x=>x.message.includes('Ambiguous')));clean(f);
});
test('a late second sameURL Request cannot reuse an already paired native file',async()=>{
 const f=fixture(),first=mainRequest(f),firstResponse=f.response();firstResponse.request=()=>first;
 const result=await clickLabelOutput(f.page,f.click(async()=>{f.page.emit('popup',f.popup);f.context.emit('request',first);f.context.emit('response',firstResponse);f.context.emit('requestfailed',first);f.popup.emit('download',f.file());}),{context:f.context,timeout:100});
 const second=mainRequest(f);f.context.emit('request',second);f.context.emit('requestfailed',second);await assert.rejects(result.dispose(),e=>e instanceof AggregateError&&e.errors.some(x=>x.message.includes('Ambiguous')));clean(f);
});
