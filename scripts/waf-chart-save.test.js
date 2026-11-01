/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {awaitNativeChartSave}=require('./waf-clinical-text-corpus-playwright-checks');
function page(events){const previous={dispose:async()=>events.push('dispose')};return {previous,locator:selector=>selector==='#caseManagementEntryForm'?{elementHandle:async()=>previous}:{first:()=>({waitFor:async()=>events.push('new editor visible')})},waitForFunction:async(fn,element)=>{assert.equal(element,previous);assert.equal(fn({isConnected:false}),true);assert.equal(fn({isConnected:true}),false);events.push('old editor detached');},waitForLoadState:async state=>{assert.equal(state,'networkidle');events.push('idle');}};}
test('native chart save awaits its full response and exact editor replacement before continuing',async()=>{
 const events=[],chart=page(events);let finished,settled=false;
 const captured={response:{status:()=>200,finished:()=>new Promise(resolve=>{finished=()=>{events.push('body finished');resolve(null);};})}};
 const operation=awaitNativeChartSave(chart,async()=>{events.push('submitted');return captured;}).then(result=>{settled=true;return result;});
 await Promise.resolve();await Promise.resolve();await Promise.resolve();assert.equal(settled,false);assert.deepEqual(events,['submitted']);
 finished();assert.equal(await operation,captured);assert.deepEqual(events,['submitted','body finished','old editor detached','new editor visible','idle','dispose']);
});
test('failed native save response cannot be treated as a completed editor transition',async()=>{
 const events=[],chart=page(events);
 await assert.rejects(awaitNativeChartSave(chart,async()=>({response:{status:()=>200,finished:async()=>new Error('lost body')}})),/response did not finish/);
 assert.deepEqual(events,['dispose']);
 await assert.rejects(awaitNativeChartSave(chart,async()=>{throw new Error('save failed');}),/save failed/);
 assert.deepEqual(events,['dispose','dispose']);
});

for(const status of [403,409,500])test(`native HTTP ${status} remains available without waiting for a replacement editor`,async()=>{
 const events=[],chart=page(events),captured={response:{status:()=>status,finished:async()=>{events.push('response finished');return null;}}};
 assert.equal(await awaitNativeChartSave(chart,async()=>captured),captured);
 assert.deepEqual(events,['response finished','dispose']);
});
