/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),{EventEmitter}=require('node:events');
const {reloadChartForCorpus}=require('./waf-clinical-text-corpus-playwright-checks');

test('only the owned old chart native release beacon is excused by its reload',async()=>{
 const context=new EventEmitter(),chart=new EventEmitter(),frame={};chart.mainFrame=()=>frame;
 const recorder={requestFailures:[{errorText:'preexisting'}]},s={context,recorder,patient:'42',provider:'999998',config:{baseUrl:new URL('https://127.0.0.1/carlos')}};
 const request=(overrides={})=>({url:()=> 'https://127.0.0.1/carlos/CaseManagementEntry',method:()=> 'POST',postData:()=> 'method=releaseNoteLock&demographicNo=42&providerNo=999998',resourceType:()=> 'ping',frame:()=>({page:()=>chart}),...overrides});
 const record=(r,errorText='net::ERR_ABORTED')=>{context.emit('request',r);const entry={requestRef:new WeakRef(r),resourceType:r.resourceType(),errorText};recorder.requestFailures.push(entry);return entry;};
 let allowed;const preserved=[];
 chart.reload=async()=>{
  allowed=record(request());
  for(const change of [{url:()=> 'https://foreign.invalid/carlos/CaseManagementEntry'},
   {url:()=> 'https://127.0.0.1/other/CaseManagementEntry'},
   {postData:()=> 'method=releaseNoteLock&demographicNo=43&providerNo=999998'},
   {postData:()=> 'method=releaseNoteLock&demographicNo=42&providerNo=1'},
   {postData:()=> 'method=save&demographicNo=42&providerNo=999998'},
   {frame:()=>({page:()=>({})})},{resourceType:()=> 'xhr'}])preserved.push(record(request(change)));
  preserved.push(record(request(),'net::ERR_FAILED'));
  chart.emit('framenavigated',frame);preserved.push(record(request()));
 };
 await reloadChartForCorpus(s,chart);
 assert(!recorder.requestFailures.includes(allowed));assert.equal(recorder.requestFailures[0].errorText,'preexisting');
 for(const entry of preserved)assert(recorder.requestFailures.includes(entry));
 assert.equal(context.listenerCount('request'),0);assert.equal(chart.listenerCount('framenavigated'),0);
});

test('a failed chart reload remains a failure and disposes its temporary listeners',async()=>{
 const context=new EventEmitter(),chart=new EventEmitter();chart.mainFrame=()=>({});chart.reload=async()=>{throw new Error('navigation failed');};
 await assert.rejects(reloadChartForCorpus({context,recorder:{requestFailures:[]}},chart),/navigation failed/);
 assert.equal(context.listenerCount('request'),0);assert.equal(chart.listenerCount('framenavigated'),0);
});
