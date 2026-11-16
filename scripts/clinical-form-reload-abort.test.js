/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {keepOwnReloadFailure}=require('./clinical-form-followup-playwright-checks');
test('own reload excuses only its already-running read request by object identity',()=>{
 const request={},other={},base=new URL('https://127.0.0.1/carlos'),entry={requestRef:new WeakRef(request),resourceType:'xhr',errorText:'net::ERR_ABORTED',url:'https://127.0.0.1/carlos/encounter/displayForms'};
 assert.equal(keepOwnReloadFailure(entry,new Set([request]),base),false);
 for(const changed of [{requestRef:new WeakRef(other)},{requestRef:undefined},{resourceType:'fetch'},{errorText:'net::ERR_FAILED'},{url:'https://foreign.invalid/carlos/encounter/displayForms'},{url:'https://127.0.0.1/carlos/CaseManagementEntry'}])assert.equal(keepOwnReloadFailure({...entry,...changed},new Set([request]),base),true);
 assert.equal(keepOwnReloadFailure(entry,new Set(),base),true);
});

test('a terminal read during form close retains its identity, while the new document stays strict',()=>{
 const {EventEmitter}=require('node:events'),{createFormReadTracker}=require('./clinical-form-followup-playwright-checks');
 const context=new EventEmitter(),page=new EventEmitter(),frame={};page.mainFrame=()=>frame;
 const base=new URL('https://127.0.0.1/carlos'),tracker=createFormReadTracker(context,base),harvest=tracker.begin(page);
 const request=()=>({url:()=> 'https://127.0.0.1/carlos/encounter/displayForms',resourceType:()=> 'xhr',frame:()=>({page:()=>page})});
 const failure=r=>({requestRef:new WeakRef(r),resourceType:'xhr',errorText:'net::ERR_ABORTED',url:r.url()});
 const duringClose=request();context.emit('request',duringClose);context.emit('requestfailed',duringClose);assert.equal(harvest.keepFailure(failure(duringClose)),false);
 page.emit('framenavigated',frame);const newDocument=request();context.emit('request',newDocument);context.emit('requestfailed',newDocument);assert.equal(harvest.keepFailure(failure(newDocument)),true);
 const beforeReload=request();context.emit('request',beforeReload);harvest.beforeReload();page.emit('framenavigated',frame);context.emit('requestfailed',beforeReload);assert.equal(harvest.keepFailure(failure(beforeReload)),false);
 const afterReload=request();context.emit('request',afterReload);context.emit('requestfailed',afterReload);assert.equal(harvest.keepFailure(failure(afterReload)),true);
 harvest.finish();tracker.dispose();assert.equal(page.listenerCount('framenavigated'),0);for(const event of ['request','requestfinished','requestfailed'])assert.equal(context.listenerCount(event),0);
});
