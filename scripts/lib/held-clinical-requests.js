/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const fs=require('node:fs'),os=require('node:os'),path=require('node:path'),crypto=require('node:crypto');
const {assert}=require('./playwright-harness');
const {trackWrite,retainFixtures}=require('./uncertain-server-writes');
const {waitUntil}=require('./concurrency-support');

/** Hold real UI requests before dispatch, then forward their unchanged bytes once. */
function heldClinicalRequests(session,{matches,select,identity}) {
  assert(/^FAKE-PW[0-9a-f]{16}$/.test(identity)&&typeof matches==='function'&&typeof select==='function','Held clinical requests require an owned marker and complete classifiers');
  const directory=fs.realpathSync(fs.mkdtempSync(path.join(os.tmpdir(),'clinical-session-recovery-')));
  fs.chmodSync(directory,0o700);const journal=path.join(directory,'journal.json');
  const holds=new Set(),records=[],pending=new Set(),failures=[];let closing=false,unknown=false;
  function persist() {
    const rows=records.map(({kind,state,request,status})=>({kind,state,status,method:request.method(),path:new URL(request.url()).pathname,
      bodySha256:crypto.createHash('sha256').update(request.postData()||'').digest('hex')}));
    fs.writeFileSync(journal+'.next',JSON.stringify({identity,unknown,records:rows},null,2)+'\n',{mode:0o600});fs.renameSync(journal+'.next',journal);
  }
  persist();
  async function handler(route) {
    const request=route.request();let selection;
    try{selection=select(request);}catch(error){failures.push(error);try{await route.abort();}catch(abortError){failures.push(abortError);}return;}
    if(!selection)return route.continue();
    try{assert(typeof selection.kind==='string'&&selection.kind&&typeof selection.write==='boolean','Incomplete held-request classification');}
    catch(error){failures.push(error);try{await route.abort();}catch(abortError){failures.push(abortError);}return;}
    if(closing&&!selection.write)return route.continue();
    let decide;
    let page;try{page=request.frame().page();}catch{/* A worker request has no frame. */}
    const record={...selection,request,state:'held',page,decision:new Promise(resolve=>{decide=resolve;})};
    record.decide=decide;records.push(record);
    if(closing)decide(false);else if(!holds.has(record.kind))decide(true);
    const operation=(async()=>{
      let response,dispatched=false,acknowledged=false;
      try {
        persist();
        if(!await record.decision){
          // A native expired heartbeat may already have closed this page. The
          // held request was never forwarded, so a closed route is also proof
          // of cancellation before server dispatch.
          try{await route.abort();}catch(error){if(!record.page?.isClosed()&&!request.failure?.())throw error;}
          record.state='aborted-before-dispatch';persist();return;
        }
        assert(!record.page?.isClosed()&&!request.failure?.(),'Held native request was cancelled before release; no server dispatch is authorized');
        record.state='dispatching';persist();dispatched=true;
        response=await route.fetch({maxRedirects:0,maxRetries:0,timeout:60000});
        record.body=await response.body();record.status=response.status();record.headers=response.headers();acknowledged=true;
        record.state='acknowledged';persist();
        try{await route.fulfill({response,body:record.body});record.delivered=true;}
        catch(error){record.deliveryError=error;record.delivered=false;}
      } catch(error) {
        if(dispatched&&!acknowledged&&record.write){unknown=true;retainFixtures(session.context);record.state='unknown-write';}
        else record.state=dispatched?'failed-before-acknowledgement':'aborted-before-dispatch';
        failures.push(error);try{persist();}catch(journalError){failures.push(journalError);}
        try{await route.abort();}catch(abortError){if(!record.page?.isClosed())failures.push(abortError);}
      } finally {if(response)try{await response.dispose();}catch(error){failures.push(error);}}
    })().catch(error=>{failures.push(error);});pending.add(operation);operation.then(()=>pending.delete(operation),()=>pending.delete(operation));
    if(record.write)trackWrite(session.context,operation);await operation;
  }
  function assertSafe() {
    const delivery=records.filter(record=>record.deliveryError&&!record.closedDeliveryAccepted).map(record=>record.deliveryError);
    const errors=[...failures,...delivery];
    if(unknown||errors.length)throw new AggregateError(errors,'Clinical session request supervision failed: '+errors.map(error=>String(error.message).split('\n')[0]).join('; ')+'; inspect '+journal);
  }
  return {
    records,journal,
    install:()=>session.context.route(matches,handler),
    hold:kind=>holds.add(kind),
    releaseOne(record){assert(records.includes(record)&&record.state==='held'&&!record.released,'Only one still-held native request may be released once');record.released=true;record.decide(true);},
    release(kind){holds.delete(kind);for(const record of records)if(record.kind===kind&&record.state==='held')record.decide(true);},
    wait:(predicate,timeout=45000)=>waitUntil(()=>{if(unknown||failures.length)assertSafe();return records.find(predicate);},'the expected native clinical/session request',timeout),
    assertSafe,
    resume(){assert(closing&&pending.size===0,'Resume requires a drained request boundary');assertSafe();holds.clear();closing=false;},
    acceptClosedDelivery(record){assert(records.includes(record)&&record.state==='acknowledged'&&record.page?.isClosed(),'Only a captured acknowledgement whose browser page closed may explain failed delivery');record.closedDeliveryAccepted=true;},
    async shutdown(){
      closing=true;for(const record of records)if(record.state==='held')record.decide(false);
      while(pending.size)await Promise.allSettled([...pending]);assertSafe();
    },
    cleanup(){
      assert(closing&&pending.size===0&&session.context.pages().every(page=>page.isClosed()),'Clinical request journal retained until browser teardown');
      assert(!unknown,'Unacknowledged clinical write retains its recovery journal');
      assert(records.every(record=>['acknowledged','aborted-before-dispatch'].includes(record.state)),'Unreconciled native request retains '+journal);
      fs.unlinkSync(journal);fs.rmdirSync(directory);
    },
  };
}
module.exports={heldClinicalRequests};
