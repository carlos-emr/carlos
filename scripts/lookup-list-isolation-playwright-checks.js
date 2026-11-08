#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// #5709: all three shipped lists, native consumers, duplicate and cache isolation.
const h=require('./lib/playwright-harness');
const ui=require('./lib/playwright-ui');
const {runWorkflow,expectValue}=require('./lib/workflow-session');
const {revealAuditLink}=require('./lib/playwright-link-audit');
const {settleOperations}=require('./graceful-signal-cancellation');
const {openAdmin}=require('./billing-on-admin-config-playwright-checks');
const {throwawayLoginFixture}=require('./lib/throwaway-login-fixture');
const {captureScheduleContext,assertScheduleContext,seedScheduleAppointment}=require('./lib/native-schedule-context');
const referralsUI=require('./lib/consultation-referral-fixture');
const {authenticatedProbe}=require('./lib/authenticated-probe');
const {openEditForm}=require('./demographic-edit-update-playwright-checks');
const q=h.sqlString;
const NAMES=['reasonCode','consultApptInst','practitionerNoType'];

async function workflow(s) {
  const {sql,marker,patient}=s;
  const inventory=sql.rows('SELECT id,name,listTitle,active FROM LookupList ORDER BY id');
  console.log('  Lookup inventory: '+JSON.stringify(inventory.map(([id,name,,active])=>({id,name,active}))));
  const lists=NAMES.map(name=>{
    const matches=inventory.filter(row=>row[1]===name&&row[3]==='1');
    if(matches.length!==1)throw new h.SkipCheck('Requires exactly one active '+name+' lookup list');
    return {id:matches[0][0],name};
  });
  const phu=inventory.filter(row=>row[1]==='phu'&&row[3]==='1');
  h.assert(phu.length<=1,'Ambiguous configured PHU list');
  if(phu.length&&await s.master.locator('select#PHU').count()===1)lists.push({id:phu[0][0],name:'phu'});
  else console.log('  PHU consumer not selected: '+(phu.length?'phu.hide/profile hides the native select':'no configured phu list'));
  const label=marker+' O\'Neil é & "<b>same</b>"';
  const owned=`lookupListId IN (${lists.map(l=>l.id).join(',')}) AND label LIKE ${q(marker+'%')}`;
  const unrelated=()=>JSON.stringify({lists:sql.rows('SELECT * FROM LookupList ORDER BY id'),
    items:sql.rows(`SELECT * FROM LookupListItem WHERE NOT (${owned}) OR label IS NULL ORDER BY id`)});
  const unrelatedBefore=unrelated();
  s.cleanup(()=>{
    h.assert(sql.value(`SELECT COUNT(*) FROM LookupListItem WHERE ${owned}`)==='0','Owned lookup items remain');
    h.assert(unrelated()===unrelatedBefore,'Unrelated lookup configuration changed');
  });
  s.cleanup(()=>{
    sql.execute(`DELETE FROM demographicExtArchive WHERE demographic_no=${patient}; DELETE FROM demographiccust WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM demographicExtArchive WHERE demographic_no=${patient})+(SELECT COUNT(*) FROM demographiccust WHERE demographic_no=${patient})`)==='0','Demographic consumer support rows remain');
  });
  const fixture=throwawayLoginFixture({sql,marker,provider:s.provider,testUser:s.config.testUser});
  s.cleanup(()=>fixture.cleanup());fixture.create({roleNames:['doctor']});
  h.assert(sql.value(`SELECT COUNT(*) FROM providerArchive WHERE provider_no=${q(fixture.providerNo)}`)==='0','Pre-existing provider archives use the fixture number; refuse to adopt them');
  s.cleanup(()=>{
    h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${q(fixture.providerNo)} AND last_name=${q(marker)}`)==='1','Provider consumer ownership changed');
    h.assert(sql.value(`SELECT COUNT(*) FROM providerArchive WHERE provider_no=${q(fixture.providerNo)} AND (last_name IS NULL OR last_name<>${q(marker)})`)==='0','Provider archive ownership changed; retain it');
    sql.execute(`DELETE FROM providerArchive WHERE provider_no=${q(fixture.providerNo)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM providerArchive WHERE provider_no=${q(fixture.providerNo)}`)==='0','Owned provider archives remain');
  });
  const schedule=await captureScheduleContext(s);
  const appointment=seedScheduleAppointment(s,schedule,{time:'13:15:00',tag:'Lookup'});
  const refs=referralsUI.ownedReferrals(s), reason=marker+' lookup consumer';
  const request=refs.seed({reason});
  let admin=await openAdmin(s);
  async function section(name,ready) {
    if(admin.isClosed()||await admin.locator('#adminNav').count()===0) {
      if(await s.schedule.locator('#scheduleLoggedInProviderNo').count()===0)await s.schedule.goto(s.scheduleUrl,{waitUntil:'load'}); // captured authenticated UI URL
      admin=await openAdmin(s);
    }
    const link=admin.getByRole('link',{name,exact:true,includeHidden:true}).first();
    await revealAuditLink(admin,link,20000);await link.click();
    const iframe=admin.locator('#dynamic-content iframe').first();await iframe.waitFor();
    const frame=await(await iframe.elementHandle()).contentFrame();h.assert(frame,'Administration iframe missing');
    await frame.locator(ready).waitFor();return frame;
  }
  const manager=()=>section('Manage Lookup Lists','#lookupListItems_'+lists[0].id);
  async function post(frame,action) {
    const pending=admin.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/lookupListManagerAction')&&r.request().frame()===frame);
    const [,response]=await settleOperations([Promise.resolve().then(action),pending]);
    h.assert(response.status()===200,'Native lookup mutation failed');
    await frame.waitForFunction(()=>window.jQuery&&window.jQuery.active===0);
  }
  const rows=list=>sql.rows(`SELECT id,value,label,displayOrder,active,createdBy FROM LookupListItem WHERE lookupListId=${list.id} AND label LIKE ${q(marker+'%')} ORDER BY id`);
  async function add(list) {
    const before=rows(list), maximum=Number(sql.value(`SELECT COALESCE(MAX(displayOrder),0) FROM LookupListItem WHERE lookupListId=${list.id} AND active=1`));
    const frame=await manager();await frame.locator('#lookupListItemLabel_'+list.id).fill(label);
    await post(frame,()=>frame.locator('#addLookupListItemButton_'+list.id).click());
    const created=rows(list).filter(row=>!before.some(prior=>prior[0]===row[0]));
    h.assert(created.length===1,'Native add did not create exactly one item');
    const [id,value,text,order,active,provider]=created[0];
    h.assert(text===label&&Number(order)===maximum+1&&active==='1'&&provider===s.provider&&/^[0-9a-f-]{36}$/.test(value),'Added lookup value/label/order/ownership is wrong');
    const reopened=await manager();
    h.assert(await reopened.locator(`#removeLookupListItem_${id}_${list.id}`).count()===1,'Reopened manager lost its native remove control');
    h.assert((await reopened.locator('#lookupListItems_'+list.id+' .label').allTextContents()).filter(text=>text.trim()===label).length===rows(list).length,'Reopened manager lost or duplicated owned labels');
    h.assert(await reopened.locator('#lookupListItems_'+list.id+' b').count()===0,'Punctuation label became markup');
    return {id,value,label,order};
  }
  async function remove(list,item) {
    const frame=await manager();await post(frame,()=>frame.locator(`#removeLookupListItem_${item.id}_${list.id}`).click());
    await expectValue(sql,`SELECT active FROM LookupListItem WHERE id=${item.id} AND lookupListId=${list.id}`,'0','Native removal did not deactivate its row');
    h.assert(await frame.locator(`#removeLookupListItem_${item.id}_${list.id}`).count()===0,'Removed item remains in manager');
  }
  async function appointmentConsumer() {
    // Administration can use the schedule tab itself. Return to the exact URL
    // captured from the authenticated native day sheet, preserving its context.
    await s.schedule.goto(s.scheduleUrl,{waitUntil:'load'});await assertScheduleContext(s,schedule);
    const page=await s.popup(s.schedule,s.schedule.locator(`a.apptLink[onclick*="appointment_no=${appointment}"]`).first(),'lookup-appointment');
    h.assert(await page.locator('[name="appointment_no"]').inputValue()===appointment,'Opened another appointment');
    return {page,select:page.locator('#reasonCode')};
  }
  async function providerConsumer() {
    const frame=await section('Search/Edit Provider Records','input[name="keyword"]');
    await frame.locator('input[name="search_mode"][value="search_providerno"]').check();await frame.locator('input[name="keyword"]').fill(fixture.providerNo);
    await loadFrame(frame,frame.locator('input[name="button"],button[name="button"]').first());
    await loadFrame(frame,frame.getByRole('link',{name:fixture.providerNo,exact:true}));
    h.assert(await frame.locator('input[name="provider_no"]').inputValue()===fixture.providerNo,'Opened another provider');
    return {page:frame,select:frame.locator('#practitionerNoType')};
  }
  async function loadFrame(frame,control) {
    await settleOperations([frame.waitForNavigation({waitUntil:'load'}),Promise.resolve().then(()=>control.click())]);
    await h.assertNotErrorPage(frame,'native provider lookup consumer');
  }
  async function consumer(list) {
    if(list.name==='reasonCode')return appointmentConsumer();
    if(list.name==='practitionerNoType')return providerConsumer();
    if(list.name==='phu') {
      await s.master.reload({waitUntil:'load'});await openEditForm(s.master,20000);
      return {page:s.master,select:s.master.locator('select#PHU')};
    }
    const page=await referralsUI.openFromChart(s,request,reason,'lookup-consultation');
    const select=page.locator('#appointmentInstructions');
    if(await select.count()!==1)throw new h.SkipCheck('CONSULTATION_APPOINTMENT_INSTRUCTIONS_LOOKUP must be enabled');
    return {page,select};
  }
  const optionRows=select=>select.locator('option').evaluateAll(options=>options.map(o=>({value:o.value,id:o.id,text:o.textContent.trim()})));
  const expectedValue=(list,item)=>list.name==='reasonCode'?item.id:item.value;
  async function offered(list,items,absent=[]) {
    const {page,select}=await consumer(list), options=await optionRows(select);
    for(const item of items)h.assert(options.filter(o=>o.value===expectedValue(list,item)&&o.text===item.label&&(list.name!=='reasonCode'||o.id===item.value)).length===1,'Consumer lost exact lookup value/label/list identity: '+list.name);
    for(const item of absent)h.assert(!options.some(o=>o.value===expectedValue(list,item)),'Consumer retained removed item without cache eviction: '+list.name);
    if(!['practitionerNoType','phu'].includes(list.name))await page.close();
  }
  const items=[];let primary,duplicate;
  try {
    await s.step('native manager inventories all shipped lists; empty adds change nothing and no edit/reorder controls are exposed',async()=>{
      const frame=await manager();for(const list of lists)h.assert(await frame.locator('#lookupListItems_'+list.id).count()===1,'Active list missing from native manager');
      h.assert(await frame.locator('a.showHideEdit,.ui-sortable,input[name="lookupListItemDisplayOrder"]').count()===0,'Lookup UI operations changed; extend edit/reorder coverage');
      const before=JSON.stringify(sql.rows('SELECT * FROM LookupListItem ORDER BY id'));
      await frame.locator('#lookupListItemLabel_'+lists[0].id).fill('');await post(frame,()=>frame.locator('#addLookupListItemButton_'+lists[0].id).click());
      h.assert(JSON.stringify(sql.rows('SELECT * FROM LookupListItem ORDER BY id'))===before,'Empty native add changed lookup rows');
    });
    await s.step('native adds preserve punctuation, order and similar labels on three independent lists, including supported duplicate labels',async()=>{
      for(const list of lists)items.push(await add(list));
      duplicate=await add(lists[2]);
      h.assert(duplicate.id!==items[2].id&&duplicate.value!==items[2].value,'Duplicate labels reused a lookup identity');
      for(let i=0;i<lists.length;i++)await offered(lists[i],i===2?[items[2],duplicate]:[items[i]]);
    });
    await s.step('native appointment, consultation and provider selections persist and reopen on their distinct list values',async()=>{
      for(let i=0;i<lists.length;i++) {
        const list=lists[i],item=items[i],{page,select}=await consumer(list);
        await select.selectOption(expectedValue(list,item));
        if(i===0) {
          const pending=page.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/appointment/UpdateRecord'));
          const [,response]=await settleOperations([page.locator('#updateButton').click(),pending]);
          h.assert(response.status()===200,'Appointment lookup selection was refused');
          await expectValue(sql,`SELECT reasonCode FROM appointment WHERE appointment_no=${appointment} AND demographic_no=${patient}`,item.id,'Appointment did not store lookup item ID');
          if(!page.isClosed())await page.close();
        }else if(i===1) {
          await referralsUI.save(page,'update',referralsUI.UPDATED);await page.close();
          h.assert(refs.field(request,'appointmentInstructions')===item.value,'Consultation did not store lookup UUID');
        }else if(list.name==='practitionerNoType') {
          await loadFrame(page,page.locator('input[name="subbutton"]'));
          h.assert(sql.value(`SELECT practitionerNoType FROM provider WHERE provider_no=${q(fixture.providerNo)}`)===item.value,'Provider did not store lookup UUID');
        }else {
          await ui.clickAndAwaitReload(page,page.locator('#updateButton input[type="submit"]').first());
          h.assert(sql.value(`SELECT value FROM demographicExt WHERE demographic_no=${patient} AND key_val='PHU'`)===item.value,'Demographic did not store PHU lookup UUID');
        }
        const reopened=await consumer(list);
        h.assert(await reopened.select.inputValue()===expectedValue(list,item),'Reopened consumer lost its selected lookup identity');
        if(!['practitionerNoType','phu'].includes(list.name))await reopened.page.close();
      }
    });
    await s.step('removing one list item preserves similar items, other consumers and configuration; native caches update immediately',async()=>{
      await remove(lists[0],items[0]);await offered(lists[0],[],[items[0]]);
      await offered(lists[1],[items[1]]);await offered(lists[2],[items[2],duplicate]);
      if(lists[3])await offered(lists[3],[items[3]]);
      h.assert(rows(lists[1])[0][4]==='1'&&rows(lists[2]).every(row=>row[4]==='1'),'Selective removal deactivated another list');
      h.assert(unrelated()===unrelatedBefore,'Lookup workflow changed unrelated configuration');
    });
  } catch(error) {primary=error;}
  // Always evict through supported native removals before browser shutdown/SQL.
  const failures=[];
  await require('./lib/uncertain-server-writes').drainWrites(s.context.browser());
  h.assert(!require('./lib/uncertain-server-writes').hasUncertainWrites(s.context.browser()),'Lookup write outcome is unknown; retain fixtures for reconciliation');
  for(const list of lists)for(const [id,value,text,order,active] of rows(list))if(active==='1')try{await remove(list,{id,value,label:text,order});}catch(error){failures.push(error);}
  if(items.length===lists.length&&duplicate&&!failures.length)try{await s.step('all native removals evict each consumer cache before SQL cleanup',async()=>{
    for(let i=0;i<lists.length;i++)await offered(lists[i],[],i===2?[items[i],duplicate]:[items[i]]);
  });}catch(error){failures.push(error);}
  if(items[1]&&!failures.some(error=>error.message.includes('Native removal')))try{await s.step('tokenless POST and authenticated GET cannot reorder a lookup row',async()=>{
    const item=items[1], before=JSON.stringify(rows(lists[1]));
    const params={method:'order',lookupListItemId:item.id,lookupListItemDisplayOrder:'9000'};
    const url=h.appUrl(s.config.baseUrl,'/lookupListManagerAction');
    const tokenless=await s.context.request.post(url,{form:params,maxRedirects:0});
    try{h.assert(tokenless.status()===403,'Tokenless lookup POST was not refused');}finally{await tokenless.dispose();}
    h.assert(JSON.stringify(rows(lists[1]))===before,'Tokenless lookup POST changed state');
    const get=await authenticatedProbe(s,url,{params});let status;
    try{status=get.status();}finally{await get.dispose();}
    const unchanged=JSON.stringify(rows(lists[1]))===before;
    h.assert(status===405&&unchanged,`Lookup GET refusal failed: HTTP ${status}; stored state ${unchanged?'unchanged':'changed'} (existing finding 58, #3059)`);
  });}catch(error){failures.push(error);}
  // Some consumers incorrectly render inactive entries (#5709's real product
  // finding). Preserve that failure, but do not strand deleted rows in their
  // cached lists. A separate native add/remove evicts caches AFTER SQL teardown.
  // It is cleanup only, never evidence that normal removal worked correctly.
  try {
    if(sql.value(`SELECT COUNT(*) FROM LookupListItem WHERE ${owned}`)!=='0') {
      h.assert(sql.value(`SELECT COUNT(*) FROM LookupListItem WHERE ${owned} AND active=1`)==='0','Native deactivation incomplete; retain lookup rows');
      const sentinelLabel=marker+' cache cleanup';
      h.assert(sql.value(`SELECT COUNT(*) FROM LookupListItem WHERE lookupListId=${lists[0].id} AND label=${q(sentinelLabel)}`)==='0','Cleanup sentinel namespace exists');
      const frame=await manager();await frame.locator('#lookupListItemLabel_'+lists[0].id).fill(sentinelLabel);
      await post(frame,()=>frame.locator('#addLookupListItemButton_'+lists[0].id).click());
      const sentinel=sql.rows(`SELECT id FROM LookupListItem WHERE lookupListId=${lists[0].id} AND label=${q(sentinelLabel)} AND active=1 AND createdBy=${q(s.provider)}`);
      h.assert(sentinel.length===1&&/^[1-9]\d*$/.test(sentinel[0][0]),'Native cleanup sentinel not owned uniquely');
      const id=sentinel[0][0];
      sql.execute(`DELETE FROM LookupListItem WHERE ${owned} AND active=0 AND id<>${id}`);
      await remove(lists[0],{id});
      sql.execute(`DELETE FROM LookupListItem WHERE id=${id} AND lookupListId=${lists[0].id} AND label=${q(sentinelLabel)} AND active=0`);
      h.assert(sql.value(`SELECT COUNT(*) FROM LookupListItem WHERE ${owned}`)==='0','Owned lookup SQL cleanup failed');
      for(const list of lists) {
        const {page,select}=await consumer(list);
        h.assert(!(await optionRows(select)).some(option=>option.text.includes(marker)),'Cleanup left marker-owned lookup values in a consumer cache: '+list.name);
        if(!['practitionerNoType','phu'].includes(list.name))await page.close();
      }
      h.assert(unrelated()===unrelatedBefore,'Lookup baseline changed during cache restoration');
      console.log('  Cleanup verified: owned SQL rows removed, native cache eviction completed and all consumers restored.');
    }
  }catch(error){failures.push(error);}
  if(primary||failures.length)throw new AggregateError([...(primary?[primary]:[]),...failures],'Lookup lifecycle failed: '+[primary,...failures].filter(Boolean).map(e=>e.message).join('; '));
}
if(require.main===module)runWorkflow('lookup-list-isolation',workflow,{preflightConfig:()=>{
  if(process.env.EXCLUSIVE!=='1')throw new h.SkipCheck('Lookup isolation requires EXCLUSIVE=1 for clinic-wide lists and referral fixtures');
}});
module.exports={workflow};
