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
const {assert,wireStrictPage}=require('./playwright-harness');
const sha=value=>require('node:crypto').createHash('sha256').update(value).digest('hex');

// Label-only capture: a new about:blank window is not completed output. Wait for
// a completed native download (on either window), or the popup's own completed
// main-document response. URLs always come from the actual click's output.
async function clickLabelOutput(page,locator,options={}){
  const context=options.context||page.context(),timeout=options.timeout||30000,label=options.label||'label';
  assert(Number.isFinite(timeout)&&timeout>0,'Label output timeout must be positive');
  const deadline=Date.now()+timeout,remaining=()=>Math.max(1,deadline-Date.now());
  const popups=new Set(),responses=new Map(),requests=new WeakMap(),requestBuffers=new Map(),removers=[],operations=new Set(),deferred=new Set(),seenResponses=new WeakSet();
  let selected,resolve,reject,timer,disposed=false,firstFailure;
  const nativeDownloads=[],requestEntries=new Set(),failures=[],evidence=[];
  const record=(event,fields={})=>evidence.push({event,at:new Date().toISOString(),...fields});
  const outcome=new Promise((yes,no)=>{resolve=yes;reject=no;});outcome.catch(()=>{});
  const listen=(emitter,event,fn)=>{emitter.on(event,fn);removers.push(()=>emitter.off(event,fn));};
  const observerFailure=error=>{firstFailure ||= error;failures.push({error,kind:'observer',url:null});reject(error);};
  const complete=value=>{if(!selected&&!disposed){selected=value;clearTimeout(timer);resolve(value);}};
  const observe=(fn,kind,url,owner,entry)=>{const operation=Promise.resolve().then(fn).catch(error=>{firstFailure ||= error;failures.push({error,kind,url,owner,entry});if(kind==='download')reject(error);});operations.add(operation);operation.finally(()=>operations.delete(operation));};
  // Chromium converts a PDF navigation into a native file: its actual Request
  // fails with ERR_ABORTED, while Response.finished can wait forever or reject
  // merely because the transient blank Page closes. Pair that exact Request
  // with one completed native file; a URL-wide or TargetClosed exemption is not
  // evidence. Multiple matching requests/files are deliberately ambiguous.
  function pairNativeHandoffs(){
    for(const file of nativeDownloads){
      const entries=[...requestEntries].filter(entry=>popups.has(entry.owner)&&entry.owner===file.owner&&entry.url===file.url);
      const files=nativeDownloads.filter(other=>other.owner===file.owner&&other.url===file.url);
      if(entries.length>1||files.length>1){observerFailure(new Error('Ambiguous label native download handoff'));continue;}
      const entry=entries[0];if(entry&&!entry.nativeFile&&file.completed&&entry.failure?.message==='net::ERR_ABORTED'&&entry.status!==undefined&&entry.status<400){entry.nativeFile=file;record('native-request-handoff',{urlSha256:sha(file.url)});}
    }
  }
  const download=(file,popup)=>observe(async()=>{
    record('download-observed',{source:popup?'popup':'opener',urlSha256:sha(file.url())});
    const failure=await file.failure();if(failure!==null)throw new Error('Label native download failed',{cause:new Error(failure)});
    const url=file.url();assert(url&&!/^about:blank$/i.test(url),'Label download has no destination');
    const native={owner:popup||page,url,completed:true};nativeDownloads.push(native);pairNativeHandoffs();record('download-complete',{urlSha256:sha(url)});complete({kind:'download',url,download:file,page:popup||null});
  },'download',file.url(),popup||page);
  const mainResponse=(popup,response)=>{if(seenResponses.has(response))return;seenResponses.add(response);return observe(async()=>{
    record('main-response-observed',{urlSha256:sha(response.url()),status:response.status(),contentType:response.headers()['content-type']||''});
    assert(response.status()<400,`Label main document answered HTTP ${response.status()}`);
    const entry=requests.get(response.request());
    if(entry){entry.status=response.status();pairNativeHandoffs();await entry.done;}
    const failure=await response.finished();if(failure!==null)throw failure;
    const url=response.url();assert(url&&!/^about:blank$/i.test(url),'Label document response has no destination');
    if(response.status()>=300&&response.status()<400){record('redirect-complete',{urlSha256:sha(url),status:response.status()});return;}
    if(!/^application\/pdf(?:\s*;|\s*$)/i.test(response.headers()['content-type']||'')){
      await popup.waitForURL(value=>String(value)===url,{timeout:remaining(),waitUntil:'domcontentloaded'});
      await popup.waitForLoadState('domcontentloaded',{timeout:remaining()});
    }
    record('main-response-complete',{urlSha256:sha(url)});complete({kind:'popup',url,download:null,page:popup});
  },'response',response.url(),popup,requests.get(response.request()));};
  // Public Request.frame() can be unavailable before the initial popup Frame
  // has its Page. Defer only that documented state; unknown API errors stay fatal.
  const earlyFrameMessage=['Frame for this navigation request is not available, because the request',
    'was issued before the frame is created. You can check whether the request',
    'is a navigation request by calling isNavigationRequest() method.'].join('\n');
  function frameFor(request){try{return request.frame();}catch(error){if(error.message===earlyFrameMessage)return null;throw error;}}
  function associate(operation){
    let done=false,finish;
    const retry=()=>{try{if(!operation())return;done=true;deferred.delete(retry);finish?.();}catch(error){done=true;deferred.delete(retry);finish?.();observerFailure(error);}};
    retry();if(!done){deferred.add(retry);const associated=new Promise(resolve=>{finish=resolve});observe(()=>associated,'association',null,null);}
  }
  const retryDeferred=()=>{for(const retry of [...deferred])retry();};
  const mainRequest=entry=>{if(entry.observed)return;entry.observed=true;return observe(async()=>{await entry.done;record('main-request-complete',{urlSha256:sha(entry.url)});},'request',entry.url,entry.owner,entry);};
  listen(context,'request',request=>{
    try{
      if(!request.isNavigationRequest()||request.serviceWorker?.())return;
      let yes,no;const done=new Promise((resolve,reject)=>{yes=resolve;no=reject;});done.catch(()=>{});
      const entry={owner:null,url:request.url(),done,yes,no};requests.set(request,entry);requestEntries.add(entry);
      associate(()=>{const frame=frameFor(request);if(!frame)return false;const owner=frame.page();
        if(owner!==page&&frame===owner.mainFrame()){
          entry.owner=owner;if(!requestBuffers.has(owner))requestBuffers.set(owner,[]);requestBuffers.get(owner).push(entry);
          if(popups.has(owner))mainRequest(entry);pairNativeHandoffs();
        }return true;});
    }catch(error){observerFailure(error);}
  });
  listen(context,'requestfinished',request=>{requests.get(request)?.yes();retryDeferred();});
  listen(context,'requestfailed',request=>{const entry=requests.get(request);if(entry){entry.failure=new Error(request.failure()?.errorText||'Label main request failed');entry.no(entry.failure);pairNativeHandoffs();}retryDeferred();});
  listen(page,'download',file=>download(file,null));
  listen(context,'response',response=>{
    try{
      const request=response.request();if(!request.isNavigationRequest()||request.serviceWorker?.())return;
      associate(()=>{const frame=frameFor(request);if(!frame)return false;const popup=frame.page();
        if(popup!==page&&frame===popup.mainFrame()){
          if(!responses.has(popup))responses.set(popup,[]);responses.get(popup).push(response);if(popups.has(popup))mainResponse(popup,response);
        }return true;});retryDeferred();
    }catch(error){observerFailure(error);}
  });
  listen(page,'popup',popup=>{
    try{popups.add(popup);record('popup-observed');
    // Synchronous registration observes a popup download/startup exception even
    // when it fires before locator.click() resolves.
    listen(popup,'download',file=>download(file,popup));
    if(options.recorder)wireStrictPage(popup,label,options.recorder,options);
    for(const entry of requestBuffers.get(popup)||[])mainRequest(entry);
    for(const response of responses.get(popup)||[])mainResponse(popup,response);
    retryDeferred();pairNativeHandoffs();
    }catch(error){observerFailure(error);}
  });
  const detach=()=>{clearTimeout(timer);for(const remove of removers.splice(0))remove();};
  async function drain(budget){
    let expired;try{await Promise.race([(async()=>{while(operations.size)await Promise.all([...operations]);})(),
      new Promise((_,no)=>{expired=setTimeout(()=>no(new Error('Label output operations did not drain')),budget);})]);
    }finally{clearTimeout(expired);}
  }
  async function dispose(){
    if(disposed)return;disposed=true;clearTimeout(timer);const cleanupFailures=[];
    // Finish every observed output before closing its window; otherwise a valid
    // concurrent PDF response can be aborted by the test itself.
    if(selected)try{await drain(remaining());}catch(error){cleanupFailures.push(error);}
    for(const popup of popups){try{await popup.close();assert(popup.isClosed(),'Label popup did not close');record('popup-closed');}catch(error){cleanupFailures.push(error);}}
    if(operations.size)try{await drain(1000);}catch(error){cleanupFailures.push(error);}
    detach();
    // A completed native download is the positive evidence for Chromium's exact
    // document-to-download handoff. Every other response/download failure remains fatal.
    for(const failure of failures){if(!((failure.kind==='response'||failure.kind==='request')&&failure.entry?.nativeFile&&failure.error===failure.entry.failure&&failure.error.message==='net::ERR_ABORTED'))cleanupFailures.push(failure.error);}
    if(cleanupFailures.length)throw new AggregateError(cleanupFailures,'Label popup cleanup or output completion failed');
  }
  timer=setTimeout(()=>{const failure=new Error(`${label}: no completed label output within ${timeout}ms`,firstFailure?{cause:firstFailure}:undefined);reject(failure);},timeout);
  try{
    await locator.scrollIntoViewIfNeeded({timeout:remaining()});
    await locator.click({timeout:remaining()});
    const result=await outcome;
    return {...result,evidence,dispose};
  }catch(error){
    try{await dispose();}catch(cleanup){throw new AggregateError([error,cleanup],'Label output failed and popup cleanup failed');}
    throw error;
  }
}
module.exports={clickLabelOutput};
