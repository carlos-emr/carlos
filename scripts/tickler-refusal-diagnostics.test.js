/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test=require('node:test'),assert=require('node:assert/strict');
const {acknowledgeTickler403,acknowledgeTicklerRecipient400}=require('./lib/tickler-refusal-diagnostics');
function setup() {
 const url='https://unit.test/carlos/tickler/EditTickler',page={};
 const http={label:'editor',url,method:'POST',status:403,resourceType:'document'};
 const console={label:'editor',type:'error',text:'Failed to load resource: the server responded with a status of 403 ()',location:{url}};
 const recorder={badResponses:[http],consoleIssues:[console],pageErrors:[{message:'unrelated JavaScript failure'}]};
 const response={request:()=>({method:()=> 'POST',url:()=>url,postData:()=> 'ticklerNo=42'}),frame:()=>({page:()=>page,name:()=> 'ticklerEditFrame'}),finished:async()=>{},status:()=>403,text:async()=>'<h5 id="carlos-permission-denied">Denied</h5>'};
 return {recorder,proof:{before:{http:new Set(),console:new Set()},response,page,expectedUrl:url,ticklerNo:'42',label:'editor'}};
}
test('only the proven 403 diagnostic is removed; old and unrelated failures remain fatal',async()=>{
 const {recorder,proof}=setup(),old={...recorder.badResponses[0]},other={...old,status:500};
 recorder.badResponses.unshift(old);proof.before.http.add(old);recorder.badResponses.push(other);
 const js={label:'editor',type:'error',text:'TypeError: failed',location:{url:proof.expectedUrl}};recorder.consoleIssues.push(js);
 await acknowledgeTickler403(recorder,proof);
 assert.deepEqual(recorder.badResponses,[old,other]);assert.deepEqual(recorder.consoleIssues,[js]);assert.equal(recorder.pageErrors.length,1);
});
test('ambiguous duplicate 403 responses cannot be reconciled',async()=>{
 const {recorder,proof}=setup();recorder.badResponses.push({...recorder.badResponses[0]});
 await assert.rejects(acknowledgeTickler403(recorder,proof),/ambiguous/);assert.equal(recorder.badResponses.length,2);
});
test('a different task or a generic 403 cannot be acknowledged',async()=>{
 for(const mismatch of ['task','body']) {
  const {recorder,proof}=setup();
  if(mismatch==='task')proof.ticklerNo='43';else proof.response.text=async()=>'<h1>CSRF rejected</h1>';
  await assert.rejects(acknowledgeTickler403(recorder,proof));assert.equal(recorder.badResponses.length,1);
 }
});

test('recipient 400 requires its own patient, frame and application marker',async()=>{
 const {recorder,proof}=setup();
 proof.patient='57';proof.response.request=()=>({method:()=> 'POST',url:()=>proof.expectedUrl,postData:()=> 'demographic_no=57'});
 proof.response.frame=()=>({page:()=>proof.page,name:()=> 'ticklerSubmitFrame'});
 proof.response.status=()=>400;proof.response.text=async()=>'<span id="tickler-recipient-refused"></span>';
 recorder.badResponses[0].status=400;recorder.consoleIssues[0].text=recorder.consoleIssues[0].text.replace('403','400');
 await assert.rejects(acknowledgeTicklerRecipient400(recorder,{...proof,patient:'58'}));
 assert.equal(recorder.badResponses.length,1);
 await acknowledgeTicklerRecipient400(recorder,proof);
 assert.equal(recorder.badResponses.length,0);assert.equal(recorder.consoleIssues.length,0);
});
