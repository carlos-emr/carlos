/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert=require('node:assert/strict');
/** Reconcile only the observed owned iframe refusal, after UI and no-write assertions. */
async function acknowledgeRefusal(recorder,{before,response,page,expectedUrl,identity,label},contract) {
 const {status,marker,frameName,field}=contract;
 const request=response.request(),fields=new URLSearchParams(request.postData()||'');
 assert(/^[1-9]\d*$/.test(String(identity))&&request.method()==='POST'&&request.url()===expectedUrl
  &&fields.getAll(field).length===1&&fields.get(field)===String(identity)
  &&response.frame().page()===page&&response.frame().name()===frameName,'Refusal is not the owned editor iframe request');
 await response.finished();
 assert(response.status()===status&&(await response.text()).includes('id="'+marker+'"'),'Response is not an application permission refusal');
 const http=recorder.badResponses.filter(e=>!before.http.has(e)&&e.label===label&&e.url===expectedUrl&&e.method==='POST'&&e.status===status&&e.resourceType==='document');
 const console=recorder.consoleIssues.filter(e=>!before.console.has(e)&&e.label===label&&e.type==='error'&&e.location?.url===expectedUrl&&new RegExp('^Failed to load resource: the server responded with a status of '+status+' \\([^\\r\\n()]*\\)$').test(e.text));
 assert(http.length===1&&console.length<=1,'Refusal diagnostics are missing or ambiguous');
 recorder.badResponses=recorder.badResponses.filter(e=>!http.includes(e));
 recorder.consoleIssues=recorder.consoleIssues.filter(e=>!console.includes(e));
 return {http:http.length,console:console.length};
}
function acknowledgeTickler403(recorder,proof) {
 return acknowledgeRefusal(recorder,{...proof,identity:proof.ticklerNo},{status:403,marker:'carlos-permission-denied',frameName:'ticklerEditFrame',field:'ticklerNo'});
}
function acknowledgeTicklerRecipient400(recorder,proof) {
 return acknowledgeRefusal(recorder,{...proof,identity:proof.patient},{status:400,marker:'tickler-recipient-refused',frameName:'ticklerSubmitFrame',field:'demographic_no'});
}
module.exports={acknowledgeTickler403,acknowledgeTicklerRecipient400};
