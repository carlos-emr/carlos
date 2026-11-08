#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
'use strict';
const h=require('./lib/playwright-harness');
const ui=require('./lib/playwright-ui');
const {runWorkflow}=require('./lib/workflow-session');
const {throwawayLoginFixture}=require('./lib/throwaway-login-fixture');
const q=h.sqlString,NAME='appointment-cut-recovery';

async function workflow(s) {
  const {sql,marker,patient,config,recorder}=s;
  const actor=throwawayLoginFixture({sql,marker,provider:s.provider,testUser:config.testUser,reserveSchedule:true});
  s.cleanup(()=>{
    if(actor.providerNo)h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM appointment WHERE provider_no=${q(actor.providerNo)})+
      (SELECT COUNT(*) FROM appointmentArchive WHERE provider_no=${q(actor.providerNo)})+(SELECT COUNT(*) FROM mygroup WHERE provider_no=${q(actor.providerNo)})`)==='0',
    'Cut-recovery actor retained while dependent rows remain');
    actor.cleanup();
  });
  actor.create();
  const provider=q(actor.providerNo),group=q('PW'+marker.slice(-8));
  h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM appointment WHERE provider_no=${provider})+
    (SELECT COUNT(*) FROM appointmentArchive WHERE provider_no=${provider})+(SELECT COUNT(*) FROM mygroup WHERE mygroup_no=${group})`)==='0','Appointment fixture namespace is occupied');
  const all=()=>sql.rows(`SELECT * FROM appointment WHERE provider_no=${provider} ORDER BY appointment_no`);
  const unrelated=JSON.stringify(sql.rows(`SELECT * FROM appointment WHERE provider_no<>${provider} ORDER BY appointment_no`));
  const ids={};
  s.cleanup(()=>{
    h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${provider} AND last_name=${q(marker)}`)==='1','Actor ownership changed');
    for(const table of ['appointment','appointmentArchive'])h.assert(sql.value(`SELECT COUNT(*) FROM ${table} WHERE provider_no=${provider}
      AND (demographic_no<>${patient} OR demographic_no IS NULL OR reason NOT LIKE ${q(marker+'%')} OR reason IS NULL)`)==='0','Foreign appointment encountered; retaining fixtures');
    h.assert(sql.value(`SELECT COUNT(*) FROM other_id WHERE table_name=2 AND table_id IN
      (SELECT appointment_no FROM appointment WHERE provider_no=${provider} UNION SELECT appointment_no FROM appointmentArchive WHERE provider_no=${provider})`)==='0',
    'Unexpected appointment links; retaining fixture');
    sql.execute(`DELETE FROM PageMonitor WHERE providerNo=${provider}; DELETE FROM appointmentArchive WHERE provider_no=${provider};
      DELETE FROM appointment WHERE provider_no=${provider}; DELETE FROM mygroup WHERE mygroup_no=${group} AND provider_no=${provider}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM appointment WHERE provider_no=${provider})+(SELECT COUNT(*) FROM appointmentArchive WHERE provider_no=${provider})+
      (SELECT COUNT(*) FROM PageMonitor WHERE providerNo=${provider})+(SELECT COUNT(*) FROM mygroup WHERE mygroup_no=${group})`)==='0','Owned appointment recovery residue remains');
    h.assert(JSON.stringify(sql.rows(`SELECT * FROM appointment WHERE provider_no<>${provider} ORDER BY appointment_no`))===unrelated,'Unrelated schedule changed');
  });
  sql.execute(`INSERT INTO mygroup (mygroup_no,provider_no,last_name,first_name) VALUES (${group},${provider},${q(marker)},'Cut recovery');
    INSERT INTO ProviderPreference (providerNo,startHour,endHour,everyMin,myGroupNo,colourTemplate,printQrCodeOnPrescriptions,
      lastUpdated,appointmentScreenLinkNameDisplayLength,defaultDoNotDeleteBilling) VALUES (${provider},8,18,15,${group},'deepblue',0,NOW(),20,0)`);
  for(const [index,label] of ['recover','abandon','snapshot','control'].entries()) {
    ids[label]=sql.value(`INSERT INTO appointment (provider_no,appointment_date,start_time,end_time,name,demographic_no,reason,notes,
      location,resources,type,status,createdatetime,updatedatetime,creator,lastupdateuser)
      VALUES (${provider},CURDATE(),${q(`${String(9+index).padStart(2,'0')}:00:00`)},${q(`${String(9+index).padStart(2,'0')}:14:00`)},
      ${q(marker+' Workflow')},${patient},${q(marker+' '+label)},${q(marker+' '+label+' notes')},'','','','t',NOW(),NOW(),${q(actor.username)},${provider}); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(ids[label]),'Seeded source has no appointment identity');
  }
  const full=id=>sql.rows(`SELECT * FROM appointment WHERE appointment_no=${id}`);
  const archive=id=>sql.rows(`SELECT * FROM appointmentArchive WHERE appointment_no=${id} ORDER BY id`);
  const control=JSON.stringify({row:full(ids.control),archive:archive(ids.control)});
  const snapshots=Object.fromEntries(Object.entries(ids).map(([label,id])=>[label,full(id)]));
  const sharedColumns=sql.rows("SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='appointment' ORDER BY ORDINAL_POSITION")
    .map(([name])=>name).filter(name=>name!=='reasonCode');
  // The archive has no reasonCode column. Every other original field is retained.
  const archivedSource=id=>sql.rows(`SELECT ${sharedColumns.map(name=>'`'+name+'`').join(',')} FROM appointmentArchive WHERE appointment_no=${id} ORDER BY id`);
  const originalSource=id=>sql.rows(`SELECT ${sharedColumns.map(name=>'`'+name+'`').join(',')} FROM appointment WHERE appointment_no=${id}`);
  let context,schedule;
  async function login() {
    const created=await h.newContext(s.context.browser(),config);s.cleanup(()=>created.close());
    created.setDefaultTimeout(20000);created.on('page',page=>h.wireStrictPage(page,NAME,recorder));
    context=created;schedule=await h.login(created,{...config,testUser:actor.username},recorder);
  }
  await login();
  async function reload(){await schedule.reload({waitUntil:'networkidle'});}
  async function edit(id) {
    await reload();
    return ui.clickOpensPopup(schedule,schedule.locator(`a.apptLink[onclick*="appointment_no=${id}&"],a.apptLink[onclick*="appointment_no=${id}'"]`).first(),
      {context,recorder,label:'cut-source-editor'});
  }
  async function destination(time) {
    await reload();
    const page=await ui.clickOpensPopup(schedule,schedule.locator(`a.adhour[onclick*="provider_no=${actor.providerNo}&"]`).filter({hasText:time}).first(),
      {context,recorder,label:'cut-destination'});
    await page.waitForLoadState('networkidle');return page;
  }
  async function copyOrCut(id,cut) {
    const page=await edit(id);
    await Promise.all([schedule.waitForEvent('framenavigated',{predicate:frame=>frame===schedule.mainFrame()}),
      page.waitForEvent('close'),page.locator(cut?'a.btn:has(i.fa-scissors)':'a.btn:has(i.fa-copy)').click()]);
    await schedule.waitForLoadState('networkidle');
    h.assert(await schedule.evaluate(()=>localStorage.getItem('copyPaste'))==='1','Native Cut/Copy did not arm automatic Paste');
  }
  async function pasted(page,label) {
    h.assert(await page.locator('#demographic_no').inputValue()===patient&&await page.locator('#reason').inputValue()===marker+' '+label
      &&await page.locator('textarea[name="notes"]').inputValue()===marker+' '+label+' notes','Clipboard patient/reason/notes were substituted');
    h.assert(await page.evaluate(()=>localStorage.getItem('copyPaste'))==='0','Automatic Paste flag did not reset');
    h.assert(await page.locator('#duration').inputValue()==='15','Clipboard duration changed');
  }
  let moved;
  await s.step('native Cut immediately archives the complete original and removes it before any destination exists',async()=>{
    h.assert(sql.value(`SELECT reasonCode IS NULL FROM appointment WHERE appointment_no=${ids.recover}`)==='1','The archive comparison cannot discard a populated reason code');
    const before=originalSource(ids.recover);
    await copyOrCut(ids.recover,true);
    h.assert(full(ids.recover).length===0&&JSON.stringify(archivedSource(ids.recover))===JSON.stringify(before),'Cut did not archive the exact original row');
    h.assert(archive(ids.recover).length===1&&all().length===3,'Cut produced an ambiguous booking/archive outcome');
    h.assert(sql.value(`SELECT COUNT(*) FROM log WHERE provider_no=${provider} AND action='Appointment.cut' AND data=${q('id='+ids.recover)}`)==='1','Cut audit receipt is absent or duplicated');
  });
  await s.step('closing an unsaved destination does not restore or book the cut source; explicit Paste recovers its session snapshot',async()=>{
    let page=await destination('14:00');await pasted(page,'recover');await page.close();
    h.assert(full(ids.recover).length===0&&archive(ids.recover).length===1&&all().length===3,'Cancelling falsely completed or restored the move');
    page=await destination('14:15');
    h.assert(await page.locator('#demographic_no').inputValue()!==patient,'Consumed auto-paste flag unexpectedly pasted again');
    await page.locator('#pasteButton').click();await pasted(page,'recover');
    await page.close();
  });
  await s.step('invalid destination has visible feedback and no write; corrected retry completes exactly one move',async()=>{
    const page=await destination('14:30');await page.locator('#pasteButton').click();await pasted(page,'recover');
    const before=JSON.stringify(all()),writes=[];
    const listen=request=>{if(request.method()==='POST'&&new URL(request.url()).pathname.endsWith('/appointment/AddRecord'))writes.push(request);};
    page.on('request',listen);
    try {
      await page.locator('#duration').fill('7500');await page.locator('#addButton').click();
      await page.locator('#jsAlertBanner').waitFor({state:'visible'});
      h.assert(/start time/i.test(await page.locator('#jsAlertText').innerText())&&/same day/i.test(await page.locator('#jsAlertText').innerText()),'Invalid destination has no actionable same-day feedback');
      h.assert(writes.length===0&&JSON.stringify(all())===before,'Invalid destination partially wrote a booking');
      await page.locator('#duration').fill('15');
      await Promise.all([schedule.waitForEvent('framenavigated',{predicate:frame=>frame===schedule.mainFrame()}),
        page.waitForEvent('close'),page.locator('#addButton').click()]);
      h.assert(writes.length===1,'Corrected retry did not submit exactly once');
    }finally{page.off('request',listen);}
    const result=sql.rows(`SELECT appointment_no,provider_no,appointment_date,TIME_FORMAT(start_time,'%H:%i'),TIME_FORMAT(end_time,'%H:%i'),demographic_no,reason,notes
      FROM appointment WHERE provider_no=${provider} AND reason=${q(marker+' recover')}`);
    h.assert(result.length===1&&result[0][0]!==ids.recover&&JSON.stringify(result[0].slice(1))===JSON.stringify([
      actor.providerNo,sql.value('SELECT CURDATE()'),'14:30','14:44',patient,marker+' recover',marker+' recover notes']),
    'Completed move has wrong identity, slot, patient or content');
    moved=result[0][0];h.assert(all().length===4&&archive(ids.recover).length===1,'Completed move duplicated or lost a booking/archive');
    await reload();h.assert(await schedule.locator(`a.apptLink[onclick*="appointment_no=${moved}&"]`).count()===1,'Moved appointment is absent from the day sheet');
  });
  await s.step('repeated explicit Paste fills the retained snapshot without silently creating another booking',async()=>{
    const before=JSON.stringify(all()),page=await destination('15:00');
    for(let attempt=0;attempt<2;attempt++){await page.locator('#pasteButton').click();await pasted(page,'recover');}
    await page.locator('#backButton').click();
    h.assert(JSON.stringify(all())===before&&archive(ids.recover).length===1,'Repeated Paste or Back silently wrote a duplicate');
  });
  await s.step('browser session interruption after Cut retains a truthful archive with no invented completed move',async()=>{
    const before=originalSource(ids.abandon);
    await copyOrCut(ids.abandon,true);
    h.assert(JSON.stringify(archivedSource(ids.abandon))===JSON.stringify(before),'Abandoned Cut lost its source archive');
    h.assert(sql.value(`SELECT COUNT(*) FROM log WHERE provider_no=${provider} AND action='Appointment.cut' AND data=${q('id='+ids.abandon)}`)==='1','Abandoned Cut has no unique audit receipt');
    await context.close();await login();
    const page=await destination('15:15');
    h.assert(await page.locator('#demographic_no').inputValue()!==patient,'Fresh browser session adopted stale patient clipboard');
    h.assert(await page.locator('#pasteButton').count()===0||await page.locator('#pasteButton').isHidden(),'Fresh session offers an unavailable clipboard');
    await page.close();
    h.assert(full(ids.abandon).length===0&&archive(ids.abandon).length===1&&all().length===3,'Interrupted Cut was falsely restored or completed');
    console.log('  EVIDENCE abandoned Cut: source absent, one exact archive and cut audit retained, destination absent; no automatic undo is provided');
  });
  await s.step('Copy is a reusable session snapshot across later source edits and deletion, and never books by itself',async()=>{
    await copyOrCut(ids.snapshot,false);
    h.assert(JSON.stringify(full(ids.snapshot))===JSON.stringify(snapshots.snapshot)&&archive(ids.snapshot).length===0,'Copy changed or archived the original');
    let page=await edit(ids.snapshot);
    await page.locator('#reason').fill(marker+' snapshot edited');
    const [saved]=await Promise.all([page.waitForResponse(response=>response.request().method()==='POST'&&new URL(response.url()).pathname.endsWith('/appointment/UpdateRecord')),
      schedule.waitForEvent('framenavigated',{predicate:frame=>frame===schedule.mainFrame()}),page.waitForEvent('close'),
      page.locator('#updateButton').click()]);
    h.assert(saved.status()===200,'Source edit failed');
    page=await destination('15:30');await pasted(page,'snapshot');await page.close();
    page=await edit(ids.snapshot);
    await h.withExpectedDialogs(page,async()=>{
      await Promise.all([schedule.waitForEvent('framenavigated',{predicate:frame=>frame===schedule.mainFrame()}),
        page.waitForEvent('close'),page.locator('#deleteButton').click()]);
    },{accept:true});
    h.assert(full(ids.snapshot).length===0&&archive(ids.snapshot).length===2,'Native edited-source deletion has the wrong archive trail');
    const before=JSON.stringify(all());page=await destination('15:45');await page.locator('#pasteButton').click();await pasted(page,'snapshot');await page.close();
    h.assert(JSON.stringify(all())===before&&all().length===2,'Stale Copy silently created a booking');
    h.assert(JSON.stringify({row:full(ids.control),archive:archive(ids.control)})===control,'Recovery operations changed the control booking');
    h.assert(full(moved).length===1&&archive(ids.recover).length===1&&archive(ids.abandon).length===1,'Final move/abandon evidence changed');
  });
}
if(require.main===module)runWorkflow(NAME,workflow,{openMaster:false,preflightConfig:()=>{
  if(process.env.EXCLUSIVE!=='1'||process.env.CARLOS_DISPOSABLE_HOST!=='true')throw new h.SkipCheck('Requires EXCLUSIVE=1 and CARLOS_DISPOSABLE_HOST=true for owned cut-recovery fixtures');
}});
module.exports={workflow};
