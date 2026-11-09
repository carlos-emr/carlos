/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert=require('node:assert/strict');
/** Reconcile only the observed owned iframe refusal, after UI and no-write assertions. */
async function acknowledgeTickler403(recorder,{before,response,page,expectedUrl,ticklerNo,label}) {
 const request=response.request(),fields=new URLSearchParams(request.postData()||'');
 assert(/^[1-9]\d*$/.test(String(ticklerNo))&&request.method()==='POST'&&request.url()===expectedUrl
  &&fields.getAll('ticklerNo').length===1&&fields.get('ticklerNo')===String(ticklerNo)
  &&response.frame().page()===page&&response.frame().name()==='ticklerEditFrame','Refusal is not the owned editor iframe request');
 await response.finished();
 assert(response.status()===403&&(await response.text()).includes('id="carlos-permission-denied"'),'Response is not an application permission refusal');
 const http=recorder.badResponses.filter(e=>!before.http.has(e)&&e.label===label&&e.url===expectedUrl&&e.method==='POST'&&e.status===403&&e.resourceType==='document');
 const console=recorder.consoleIssues.filter(e=>!before.console.has(e)&&e.label===label&&e.type==='error'&&e.location?.url===expectedUrl&&/^Failed to load resource: the server responded with a status of 403 \([^\r\n()]*\)$/.test(e.text));
 assert(http.length===1&&console.length<=1,'Refusal diagnostics are missing or ambiguous');
 recorder.badResponses=recorder.badResponses.filter(e=>!http.includes(e));
 recorder.consoleIssues=recorder.consoleIssues.filter(e=>!console.includes(e));
 return {http:http.length,console:console.length};
}
module.exports={acknowledgeTickler403};
