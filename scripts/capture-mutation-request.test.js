/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {captureRequest}=require('./lib/get-reject-probe');
test('mutation capture excludes background lock/autosave calls and drains the actual save response',async()=>{
  let predicate,resolve,finished=false;
  const page={waitForRequest(filter){predicate=filter;return new Promise(done=>{resolve=done;});}};
  const request=method=>({method:()=> 'POST',url:()=> 'https://localhost/carlos/CaseManagementEntry',postData:()=>new URLSearchParams({method,caseNote_note:'FAKE note'}).toString(),allHeaders:async()=>({'content-type':'application/x-www-form-urlencoded'}),frame:()=>({url:()=> 'https://localhost/carlos/encounter/IncomingEncounter'}),response:async()=>({status:()=>200,finished:async()=>{finished=true;}})});
  const captured=await captureRequest(page,url=>url.pathname.endsWith('/CaseManagementEntry'),async()=>{
    assert.equal(predicate(request('isNoteEdited')),false);assert.equal(predicate(request('autosave')),false);
    const saved=request('save');assert.equal(predicate(saved),true);resolve(saved);
  },{acceptRequest:request=>new URLSearchParams(request.postData()).get('method')==='save'});
  assert.equal(captured.body.get('method'),'save');assert.equal(captured.status,200);assert.equal(finished,true);
});
