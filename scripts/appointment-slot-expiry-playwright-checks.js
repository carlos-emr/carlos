#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
'use strict';
const {randomBytes}=require('node:crypto');
const h=require('./lib/playwright-harness');
const ui=require('./lib/playwright-ui');
const {runWorkflow}=require('./lib/workflow-session');
const {throwawayLoginFixture}=require('./lib/throwaway-login-fixture');
const q=h.sqlString;
const NAME='appointment-slot-expiry';
const isMonitor=response=>new URL(response.url()).pathname.endsWith('/PageMonitoringService')&&response.request().method()==='POST';

/** Check server-clock evidence, rather than timing expiry from a browser action. */
function expiryEvidence(row,{expired=false}={}) {
  h.assert(row&&row.length===6,'Missing or ambiguous heartbeat evidence');
  const [id,pageId,locked,updated,timeout,now]=row;
  h.assert(/^[1-9]\d*$/.test(id)&&pageId&&locked==='1','The owner did not hold a real monitor lock');
  h.assert([updated,timeout,now].every(value=>/^\d+(?:\.\d+)?$/.test(value)),'Heartbeat evidence is not numeric');
  const values=[updated,timeout,now].map(Number);
  h.assert(values.every(Number.isFinite)&&values[1]===20,'Heartbeat evidence has an unexpected timeout or timestamp');
  const age=values[2]-values[0];
  h.assert(age>=0&&(expired?age>values[1]:age<values[1]),`Heartbeat age ${age}s is outside the ${expired?'expired':'live'} boundary`);
  return {id,pageId,updated:values[0],timeout:values[1],age};
}

/** Every rendered booking control must agree with the native lock state. */
async function buttons(page,visible) {
  let found=0;
  for(const id of ['addButton','apptRepeatButton','groupButton']) {
    const control=page.locator('#'+id);
    if(await control.count()) {found++;h.assert(await control.isVisible()===visible,`${id} has the wrong lock visibility`);}
  }
  h.assert(found>0,'No booking buttons were rendered');
}

/** Trigger the form's own duration handler and await its actual lock response. */
async function refresh(page) {
  const [response]=await Promise.all([page.waitForResponse(isMonitor),page.locator('input[name="duration"]').dispatchEvent('change')]);
  h.assert(response.status()===200,'Native lock heartbeat failed');
  await response.finished();
  const payload=await response.json();
  h.assert(Array.isArray(payload),'Native lock response is not an array');
  return payload;
}

async function workflow(s) {
  const {sql,marker,patient}=s;
  const actors=[];
  for(let index=0;index<3;index++) {
    const fixture=throwawayLoginFixture({sql,marker:`FAKE-PW${randomBytes(8).toString('hex')}`,provider:s.provider,testUser:s.config.testUser,reserveSchedule:true});
    s.cleanup(()=>{
      if(fixture.providerNo)h.assert(sql.value(`SELECT
        (SELECT COUNT(*) FROM appointment WHERE provider_no=${q(fixture.providerNo)})+
        (SELECT COUNT(*) FROM appointmentArchive WHERE provider_no=${q(fixture.providerNo)})+
        (SELECT COUNT(*) FROM mygroup WHERE provider_no=${q(fixture.providerNo)})+
        (SELECT COUNT(*) FROM PageMonitor WHERE providerNo=${q(fixture.providerNo)})`)==='0',
      'Actor retained while dependent fixture rows remain');
      fixture.cleanup();
    });
    fixture.create({roleNames:index===0?['admin','doctor']:['doctor']});
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM appointment WHERE provider_no=${q(fixture.providerNo)})
      +(SELECT COUNT(*) FROM appointmentArchive WHERE provider_no=${q(fixture.providerNo)})`)=== '0','An actor number already owns appointments');
    actors.push(fixture);
  }
  const [owner,booker,control]=actors;
  const numbers=actors.map(actor=>q(actor.providerNo)).join(',');
  const group=q('PW'+marker.slice(-8)), reason=marker+' expiry';
  const unrelated=JSON.stringify(sql.rows(`SELECT * FROM appointment WHERE provider_no NOT IN (${numbers}) ORDER BY appointment_no`));
  h.assert(sql.value(`SELECT COUNT(*) FROM PageMonitor WHERE providerNo IN (${numbers})`)==='0','Actor numbers already own monitors');
  h.assert(sql.value(`SELECT COUNT(*) FROM mygroup WHERE mygroup_no=${group}`)==='0','Schedule group namespace is occupied');
  s.cleanup(()=>{
    for(const actor of actors)h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${q(actor.providerNo)} AND last_name=${q(actor.username.replace(/^FAKEPW/,'FAKE-PW'))}`)==='1','Actor ownership changed; retaining child rows');
    h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE provider_no IN (${numbers}) AND NOT
      (provider_no=${q(owner.providerNo)} AND demographic_no=${patient} AND reason=${q(reason)})`)==='0','Unexpected actor appointment; retaining fixtures');
    h.assert(sql.value(`SELECT COUNT(*) FROM appointmentArchive WHERE provider_no IN (${numbers})`)==='0','Unexpected appointment archive; retaining fixtures');
    h.assert(sql.value(`SELECT COUNT(*) FROM other_id WHERE table_name=2 AND table_id IN
      (SELECT appointment_no FROM appointment WHERE provider_no IN (${numbers}))`)==='0','Unexpected appointment extension; retaining fixtures');
    sql.execute(`DELETE FROM PageMonitor WHERE providerNo IN (${numbers});
      DELETE FROM appointment WHERE provider_no=${q(owner.providerNo)} AND demographic_no=${patient} AND reason=${q(reason)};
      DELETE FROM mygroup WHERE mygroup_no=${group} AND provider_no=${q(owner.providerNo)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM PageMonitor WHERE providerNo IN (${numbers}))+
      (SELECT COUNT(*) FROM appointment WHERE provider_no IN (${numbers}))+(SELECT COUNT(*) FROM mygroup WHERE mygroup_no=${group})`)==='0','Owned locks, booking or group remain');
    h.assert(JSON.stringify(sql.rows(`SELECT * FROM appointment WHERE provider_no NOT IN (${numbers}) ORDER BY appointment_no`))===unrelated,'Unrelated schedule changed');
  });
  sql.execute(`INSERT INTO mygroup (mygroup_no,provider_no,last_name,first_name) VALUES (${group},${q(owner.providerNo)},${q(marker)},'Expiry');
    INSERT INTO ProviderPreference (providerNo,startHour,endHour,everyMin,myGroupNo,colourTemplate,printQrCodeOnPrescriptions,
      lastUpdated,appointmentScreenLinkNameDisplayLength,defaultDoNotDeleteBilling)
      VALUES (${q(owner.providerNo)},8,18,15,${group},'deepblue',0,NOW(),20,0)`);
  sql.execute(`INSERT INTO ProviderPreference (providerNo,startHour,endHour,everyMin,myGroupNo,colourTemplate,printQrCodeOnPrescriptions,
    lastUpdated,appointmentScreenLinkNameDisplayLength,defaultDoNotDeleteBilling)
    VALUES (${q(booker.providerNo)},8,18,15,${group},'deepblue',0,NOW(),20,0)`);
  h.assert(sql.value(`SELECT COUNT(*) FROM secUserRole u JOIN secObjPrivilege p ON p.roleUserGroup=u.role_name
    WHERE u.provider_no=${q(booker.providerNo)} AND u.activeyn=1 AND p.objectName='_admin' AND p.privilege NOT IN ('|0|','')`)==='0','Successor has admin privileges');
  const sessions=[];
  for(const [index,actor] of actors.entries()) {
    const context=await h.newContext(s.context.browser(),s.config);
    s.cleanup(()=>context.close());context.setDefaultTimeout(20000);
    context.on('page',page=>h.wireStrictPage(page,`${NAME}-${index}`,s.recorder));
    const schedule=await h.login(context,{...s.config,testUser:actor.username},s.recorder);
    sessions.push({context,schedule});
  }
  const rows=actor=>sql.rows(`SELECT id,pageId,locked,UNIX_TIMESTAMP(updateDate),timeout,UNIX_TIMESTAMP(NOW(3))
    FROM PageMonitor WHERE pageName='addappointment' AND providerNo=${q(actor.providerNo)}`);
  const sole=actor=>{const result=rows(actor);h.assert(result.length===1,'Actor has no unique monitor');return result[0];};
  let abandoned,successor,live,cdp,last,liveInitial,slot,bookingDate;
  await s.step('native owner lock blocks an overlapping non-admin booker while a separate slot stays live',async()=>{
    abandoned=await ui.clickOpensPopup(sessions[0].schedule,
      sessions[0].schedule.locator(`a.adhour[onclick*="provider_no=${owner.providerNo}&"]`).filter({hasText:'10:00'}).first(),
      {context:sessions[0].context,recorder:s.recorder,label:'expiry-owner'});
    await abandoned.waitForLoadState('networkidle');
    if(!await abandoned.evaluate(()=>typeof updatePageLock==='function'))throw new h.SkipCheck('Requires appointment-lock-expiry property profile');
    await refresh(abandoned);expiryEvidence(sole(owner));await buttons(abandoned,true);
    slot=abandoned.url();
    await h.gotoApp(sessions[1].schedule,s.config.baseUrl,
      '/provider/providercontrol?view=0&displaymode=day&dboperation=searchappointmentday&viewall=1');
    successor=await ui.clickOpensPopup(sessions[1].schedule,
      sessions[1].schedule.locator(`a.adhour[onclick*="provider_no=${owner.providerNo}&"]`).filter({hasText:'10:00'}).first(),
      {context:sessions[1].context,recorder:s.recorder,label:'expiry-successor'});
    await successor.waitForLoadState('networkidle');
    await successor.locator('input[name="start_time"]').fill('10:05');
    const blocked=await refresh(successor);
    h.assert(blocked.some(row=>row.locked&&!row.self),'Overlapping successor is not blocked');await buttons(successor,false);
    h.assert(sole(owner)[1]!==sole(booker)[1],'Overlap control uses identical page IDs');
    live=await sessions[2].context.newPage();await live.goto(slot);await live.waitForLoadState('networkidle');
    await live.locator('input[name="start_time"]').fill('16:00');
    const nonoverlap=await refresh(live);
    h.assert(!nonoverlap.some(row=>row.locked&&!row.self),'Non-overlapping slot sees a foreign lock');
    await buttons(live,true);liveInitial=expiryEvidence(sole(control));
    bookingDate=await successor.locator('input[name="appointment_date"]').inputValue();
  });
  await s.step('freeze without unload or release and prove the last accepted lock remains effective before expiry',async()=>{
    // Pause this owned renderer's JavaScript through DevTools. Page lifecycle
    // freezing is insufficient: Chromium can resume it when visibility changes.
    // No application function is replaced and no unload/release runs.
    await refresh(abandoned);
    cdp=await sessions[0].context.newCDPSession(abandoned);
    await cdp.send('Debugger.enable');
    let timer;
    try {
      await Promise.all([new Promise((resolve,reject)=>{
        cdp.once('Debugger.paused',resolve);timer=setTimeout(()=>reject(new Error('Owner renderer did not pause')),10000);
      }),cdp.send('Debugger.pause')]);
    }finally{clearTimeout(timer);}
    last=expiryEvidence(sole(owner));
    console.log(`  EVIDENCE timeout=${last.timeout}s lastAcceptedEpoch=${last.updated} monitor=${last.id}`);
    const before=await refresh(successor);
    h.assert(before.some(row=>row.locked&&!row.self),'Lock released before expiry');await buttons(successor,false);
    const still=expiryEvidence(sole(owner));
    h.assert(still.id===last.id&&still.updated===last.updated,'Frozen owner released or renewed its monitor');
  });
  await s.step('application expiry recovers the overlapping slot within one timeout plus bounded refresh tolerance',async()=>{
    const deadline=Date.now()+35000;
    let recovered=false;
    do {
      // Use the rendered duration control to request a native refresh as well as
      // the page's timers: inactive Chromium tabs can throttle background timers.
      await refresh(live);
      const result=await refresh(successor);
      const old=rows(owner),next=rows(booker);
      if(old.length===0&&next.length===1&&next[0][2]==='1'){recovered=true;break;}
      if(old.length) {
        h.assert(old.length===1&&old[0][0]===last.id&&Number(old[0][3])===last.updated,'Frozen owner renewed or changed its monitor');
        h.assert(next.length===1&&next[0][2]==='0'&&result.some(row=>row.locked&&!row.self),'Successor took a lock while the old owner still held it');
        await buttons(successor,false);
      }
      h.assert(expiryEvidence(sole(control)).id===liveInitial.id,'Live control was expired and recreated');
      await new Promise(resolve=>setTimeout(resolve,500));
    }while(Date.now()<deadline);
    h.assert(recovered,'The abandoned slot did not expire through native heartbeats');
    const now=Number(sql.value('SELECT UNIX_TIMESTAMP(NOW(3))'));
    h.assert(now-last.updated>last.timeout&&now-last.updated<=last.timeout+12,'Takeover occurred outside the supported expiry tolerance');
    const native=await refresh(successor);
    h.assert(!native.some(row=>row.locked&&!row.self),'Successor still sees a foreign lock');await buttons(successor,true);
    const renewing=expiryEvidence(sole(control));
    h.assert(renewing.id===liveInitial.id&&renewing.updated>liveInitial.updated,'Live control expired or did not renew');
    console.log(`  EVIDENCE recoveredAfter=${now-last.updated}s liveControlRenewed=true`);
  });
  await s.step('resumed stale heartbeat and release cannot steal or delete the successor or live control',async()=>{
    const successorId=sole(booker)[0],liveId=sole(control)[0];
    await cdp.send('Debugger.resume');
    await cdp.send('Debugger.disable');
    const stale=await refresh(abandoned);
    h.assert(stale.some(row=>row.locked&&!row.self),'Resumed stale owner does not see the successor');
    h.assert(sole(owner)[2]==='0','Resumed owner stole the lock');await buttons(abandoned,false);
    const [released]=await Promise.all([abandoned.waitForResponse(response=>isMonitor(response)&&new URLSearchParams(response.request().postData()).get('method')==='cancel'),
      abandoned.waitForEvent('close'),
      abandoned.locator('#backButton').click()]);
    h.assert(released.status()===200&&rows(owner).length===0,'Native stale-owner release failed');
    h.assert(sole(booker)[0]===successorId&&sole(booker)[2]==='1'&&sole(control)[0]===liveId&&sole(control)[2]==='1','Stale release disturbed a live lock');
  });
  await s.step('successor completes the exact owned appointment through patient search and Add',async()=>{
    await successor.locator('#keyword').fill(marker);
    await ui.clickAndAwaitReload(successor,successor.locator('#searchBtn'),{required:false});
    await ui.clickAndAwaitReload(successor,successor.locator(`input[name="pick_demographic"][value="${patient}"]`).first(),{required:false});
    h.assert(await successor.locator('#demographic_no').inputValue()===patient,'Native picker selected the wrong patient');
    await successor.locator('#reason').fill(reason);
    const start=await successor.locator('input[name="start_time"]').inputValue();
    const end=await successor.locator('input[name="end_time"]').inputValue();
    await buttons(successor,true);
    const bookingLock=expiryEvidence(sole(booker));
    const [saved]=await Promise.all([successor.waitForResponse(response=>response.request().method()==='POST'&&new URL(response.url()).pathname.endsWith('/appointment/AddRecord')),
      successor.waitForEvent('close'),
      successor.locator('#addButton').click()]);
    h.assert(saved.status()===200,'Native appointment Add failed');
    const booked=sql.rows(`SELECT demographic_no,provider_no,appointment_date,TIME_FORMAT(start_time,'%H:%i'),TIME_FORMAT(end_time,'%H:%i'),reason,creator
      FROM appointment WHERE provider_no=${q(owner.providerNo)}`);
    h.assert(booked.length===1&&JSON.stringify(booked[0].slice(0,6))===JSON.stringify([patient,owner.providerNo,bookingDate,start,end,reason]),'Native booking differs from the recovered slot');
    h.assert([booker.username,booker.providerNo].includes(booked[0][6]),'Booking was not created by the successor');
    // Form submission deliberately removes beforeunload's cancellation handler.
    // A completed booking can therefore leave its non-renewing monitor until
    // the supported timeout. Retire it through the application, never SQL.
    const deadline=Date.now()+35000;
    while(rows(booker).length&&Date.now()<deadline) {
      const remaining=sole(booker);
      h.assert(remaining[0]===bookingLock.id&&Number(remaining[3])===bookingLock.updated,'Closed booking editor renewed or replaced its lock');
      await refresh(live);
      await new Promise(resolve=>setTimeout(resolve,500));
    }
    h.assert(rows(booker).length===0,'Completed booking monitor did not retire through native expiration');
    h.assert(expiryEvidence(sole(control)).id===liveInitial.id,'Booking allowed the live control to expire and recreate its lock');
    await sessions[0].schedule.reload();
    h.assert(await sessions[0].schedule.locator(`a.apptLink[onclick*="appointment_no=${sql.value(`SELECT appointment_no FROM appointment WHERE provider_no=${q(owner.providerNo)}`)}&"]`).count()===1,'Booked appointment is missing from the owner day sheet');
  });
}
if(require.main===module)runWorkflow(NAME,workflow,{openMaster:false,preflightConfig:()=>{
  if(process.env.EXCLUSIVE!=='1'||process.env.CARLOS_DISPOSABLE_HOST!=='true')throw new h.SkipCheck('Requires EXCLUSIVE=1 and CARLOS_DISPOSABLE_HOST=true for owned slot-expiry fixtures');
}});
module.exports={workflow,expiryEvidence};
