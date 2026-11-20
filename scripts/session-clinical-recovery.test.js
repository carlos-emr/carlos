/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {assertAuthenticationRefusal,consumeRefusalDiagnostics,assertVisibleRefusal,consumeAcknowledgedAborts}=require('./session-clinical-recovery-playwright-checks');
const base='https://clinic.invalid/carlos',target=base+'/CaseManagementEntry';
const record=(change={})=>({kind:'note',state:'acknowledged',status:401,body:Buffer.from('Authentication required'),headers:{'x-permitted-cross-domain-policies':'none'},request:{url:()=>target,method:()=> 'POST'},...change});
for(const status of [401,403])test('captured actual authentication refusal is accepted: '+status,()=>assertAuthenticationRefusal(record({status}),base,['private pending text']));
test('local login redirects and an actual login form cannot be confused with an empty save acknowledgement',()=>{
  assertAuthenticationRefusal(record({status:302,headers:{location:'/carlos/index'}}),base,[]);
  assertAuthenticationRefusal(record({status:200,body:Buffer.from('<input id="username"><input id="password">')}),base,[]);
});
for(const [label,change] of [
  ['unacknowledged request',{state:'held'}],['empty successful save',{status:200,body:Buffer.alloc(0)}],
  ['server fault',{status:500}],['foreign redirect',{status:302,headers:{location:'https://other.invalid/carlos/index'}}],
  ['unattributed refusal',{status:403,headers:{}}],['WAF block',{status:403,body:Buffer.from('<center>nginx</center>')}],
  ['unrelated local redirect',{status:302,headers:{location:'/carlos/clinical-success'}}],
  ['private text reflected on error page',{body:Buffer.from('private pending text')}],
  ['incomplete login form',{status:200,body:Buffer.from('<input id="username">')}],
])test('session recovery refuses '+label,()=>assert.throws(()=>assertAuthenticationRefusal(record(change),base,['private pending text'])));
test('only matching observed authentication diagnostics are consumed; unrelated errors remain findings',()=>{
  const recorder={badResponses:[{url:target,method:'POST',status:401}],consoleIssues:[
    {location:{url:target},text:'Failed to load resource: the server responded with a status of 401 (Unauthorized)'},
    {text:'CaseManagement autoSave failed 401 Unauthorized'},
    {text:'An unrelated error'},{location:{url:'https://other.invalid'},text:'Failed to load resource: status of 401 (Unauthorized)'}]};
  consumeRefusalDiagnostics(recorder,{responses:0,console:0},[record()]);assert.equal(recorder.badResponses.length,0);assert.equal(recorder.consoleIssues.length,2);
  assert.equal(recorder.consoleIssues[0].text,'An unrelated error');
});
test('an extra failed response cannot disappear behind an expected expired save',()=>{
  const recorder={badResponses:[{url:target,method:'POST',status:401},{url:base+'/other',method:'GET',status:500}],consoleIssues:[]};
  assert.throws(()=>consumeRefusalDiagnostics(recorder,{responses:0,console:0},[record()]),/Unexpected response/);assert.equal(recorder.badResponses.length,2);
});
test('native generic403 is visibly an error, and never a successful save',()=>{
  assertVisibleRefusal({status:403,loginCount:0,feedback:'CARLOS has encountered an unexpected error\nReceived the exception:\n\nCARLOS Error: 403\n\nBack\nExit'});
  assertVisibleRefusal({status:302,loginCount:1,feedback:'Sign in'});
});
for(const feedback of ['','Pending clinical form','Saved successfully','CARLOS has encountered an unexpected error\nCARLOS Error: 500'])
  test('a refusal cannot be inferred from unrelated/stale/false-success visible feedback: '+JSON.stringify(feedback),()=>assert.throws(()=>assertVisibleRefusal({status:403,loginCount:0,feedback})));
test('only this acknowledged refused request abandoned by its own document may excuse a browser abort',()=>{
  const native=record(),unrelated={},mark={failures:0};
  const entry=(request,changes={})=>({errorText:'net::ERR_ABORTED',navigatedAway:()=>true,requestRef:new WeakRef(request),...changes});
  const recorder={requestFailures:[entry(native.request),entry(unrelated),entry(native.request,{navigatedAway:()=>false}),entry(native.request,{errorText:'net::ERR_FAILED'})]};
  consumeAcknowledgedAborts(recorder,mark,[native]);assert.equal(recorder.requestFailures.length,3);
  const stillUnknown={...native,state:'dispatching'};consumeAcknowledgedAborts(recorder,mark,[stillUnknown]);assert.equal(recorder.requestFailures.length,3);
});
test('observed resource status is literal and must equal the actual acknowledged refusal',()=>{
  const recorder={badResponses:[{url:target,method:'POST',status:401}],consoleIssues:[
    {location:{url:target},text:'Failed to load resource: status of 403 (Forbidden)'},
    {location:{url:target},text:'Failed to load resource: status of 401x (Unauthorized)'},
    {location:{url:target},text:'Failed to load resource: status of 401 (Unauthorized)'}]};
  consumeRefusalDiagnostics(recorder,{responses:0,console:0},[record()]);assert.equal(recorder.consoleIssues.length,2);
  assert.match(recorder.consoleIssues[0].text,/403/);assert.match(recorder.consoleIssues[1].text,/401x/);
});
