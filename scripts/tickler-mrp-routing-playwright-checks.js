#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
'use strict';
const {randomBytes}=require('node:crypto');
const h=require('./lib/playwright-harness');
const ui=require('./lib/playwright-ui');
const {runWorkflow}=require('./lib/workflow-session');
const {throwawayLoginFixture}=require('./lib/throwaway-login-fixture');
const {createMetadataPatientFixture}=require('./lib/document-metadata-patient-fixture');
const {saveOwnedProviderProfile}=require('./lib/native-provider-fixture');
const {restartAndWait}=require('./lib/property-profiles');
const {openMasterRecord}=require('./master-record-tabs-playwright-checks');
const {masterRecordLink}=require('./lib/master-record-link');
const {revealAuditLink}=require('./lib/playwright-link-audit');
const {selectTicklerAssignee}=require('./lib/tickler-clinic');
const {waitForTicklerTable,waitForSaveSentinel}=require('./tickler-forward-filters-playwright-checks');
const q=h.sqlString,NAME='tickler-mrp-routing';

/** Verify actual MRP defaults, patient changes and receiver usability using owned records. */
async function workflow(s) {
  const {sql,marker,patient,config,recorder}=s,browser=s.context.browser();
  let cached=false;
  s.cleanup(()=>{if(cached)restartAndWait();});
  const actors=[];let claimed=false;
  for(let index=0;index<4;index++) {
    const actorMarker=`FAKE-PW${randomBytes(8).toString('hex')}`;
    const fixture=throwawayLoginFixture({sql,marker:actorMarker,provider:s.provider,testUser:config.testUser});
    s.cleanup(()=>{
      if(claimed)h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM tickler WHERE creator=${q(fixture.providerNo)} OR task_assigned_to=${q(fixture.providerNo)})+
        (SELECT COUNT(*) FROM demographic WHERE provider_no=${q(fixture.providerNo)})+(SELECT COUNT(*) FROM providerArchive WHERE provider_no=${q(fixture.providerNo)})`)==='0',
      'MRP account retained while clinical references remain');
      fixture.cleanup();
    });
    fixture.create({roleNames:index===0?['admin','doctor']:['doctor']});actors.push({fixture,marker:actorMarker});
  }
  const [author,a,b,control]=actors,providers=actors.map(actor=>q(actor.fixture.providerNo)).join(',');
  h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM tickler WHERE creator IN (${providers}) OR task_assigned_to IN (${providers}))+
    (SELECT COUNT(*) FROM demographic WHERE provider_no IN (${providers}))+(SELECT COUNT(*) FROM providerArchive WHERE provider_no IN (${providers}))`)==='0','MRP actor namespace collision');
  claimed=true;
  const originalMrp=sql.value(`SELECT provider_no FROM demographic WHERE demographic_no=${patient}`);
  const bMarker=`FAKE-PW${randomBytes(8).toString('hex')}`;
  const other=createMetadataPatientFixture({sql,marker:bMarker,provider:b.fixture.providerNo});
  s.cleanup(()=>{
    h.assert(!browser.isConnected(),'Second patient retained while browser shutdown is unconfirmed');
    other.cleanup({browserClosed:true});
  });
  const patientB=other.create(),patients=[patient,patientB],patientSet=patients.join(',');
  const owner=()=>{
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient} AND last_name=${q(marker)}`)==='1','First owned patient identity changed');
    other.verifyOwner();
    for(const actor of actors)h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${q(actor.fixture.providerNo)} AND last_name=${q(actor.marker)}`)==='1','MRP actor identity changed');
  };
  s.cleanup(()=>{
    owner();
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient} AND provider_no IN
      (${q(originalMrp)},${q(a.fixture.providerNo)},${q(b.fixture.providerNo)},'')`)==='1','First patient MRP changed outside the owned cases');
    sql.execute(`UPDATE demographic SET provider_no=${q(originalMrp)} WHERE demographic_no=${patient} AND last_name=${q(marker)}`);
    const tasks=sql.rows(`SELECT tickler_no FROM tickler WHERE demographic_no IN (${patientSet}) AND message LIKE ${q(marker+'%')}`).flat();
    h.assert(tasks.every(id=>/^[1-9]\d*$/.test(id)),'Ambiguous owned MRP task IDs');
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler WHERE demographic_no IN (${patientSet}) AND NOT
      (message LIKE ${q(marker+'%')} AND creator=${q(author.fixture.providerNo)} AND task_assigned_to IN (${providers}))`)==='0','Unexpected patient task; retaining fixtures');
    if(tasks.length) {
      const ids=tasks.join(',');
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM ticklerdocs WHERE tickler_id IN (${ids}))+
        (SELECT COUNT(*) FROM tickler_comments WHERE tickler_no IN (${ids}))`)==='0','Unexpected task attachment/comment; retaining fixtures');
      sql.execute(`DELETE FROM tickler_update WHERE tickler_no IN (${ids}); DELETE FROM tickler
        WHERE tickler_no IN (${ids}) AND demographic_no IN (${patientSet}) AND message LIKE ${q(marker+'%')}`);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler WHERE demographic_no IN (${patientSet})`)==='0','Owned MRP tasks remain');
    for(const actor of actors) {
      const no=q(actor.fixture.providerNo);
      h.assert(sql.value(`SELECT COUNT(*) FROM providerArchive WHERE provider_no=${no} AND (last_name<>${q(actor.marker)} OR last_name IS NULL)`)==='0','Provider archive ownership changed');
      sql.execute(`DELETE FROM providerArchive WHERE provider_no=${no} AND last_name=${q(actor.marker)}`);
    }
  });
  sql.execute(`UPDATE demographic SET provider_no=${q(a.fixture.providerNo)} WHERE demographic_no=${patient} AND last_name=${q(marker)}`);
  const site=sql.value('SELECT site_id FROM site WHERE status=1 ORDER BY site_id LIMIT 1');
  if(!/^[1-9]\d*$/.test(site))throw new h.SkipCheck('MRP routing requires an active configured clinic');
  sql.execute(`INSERT INTO providersite(provider_no,site_id) VALUES ${actors.map(actor=>`(${q(actor.fixture.providerNo)},${site})`).join(',')}`);
  for(const actor of actors) {
    const context=await h.newContext(browser,config);s.cleanup(()=>context.close());context.setDefaultTimeout(25000);
    context.on('page',page=>h.wireStrictPage(page,NAME,recorder));actor.context=context;
    actor.schedule=await h.login(context,{...config,testUser:actor.fixture.username},recorder);
  }
  const authorSession={...s,provider:author.fixture.providerNo,context:author.context,schedule:author.schedule};
  const popup=(page,locator,label)=>ui.clickOpensPopup(page,locator,{context:author.context,recorder,label});
  const preference=`provider_no=${q(author.fixture.providerNo)} AND name='tickler_task_assignee'`;
  h.assert(sql.value(`SELECT COUNT(*) FROM property WHERE ${preference}`)==='0','Author preference namespace collision');
  s.cleanup(()=>{
    owner();h.assert(sql.value(`SELECT COUNT(*) FROM property WHERE ${preference} AND (value<>'mrp' OR value IS NULL)`)==='0','MRP preference ownership changed');
    sql.execute(`DELETE FROM property WHERE ${preference} AND value='mrp'`);
  });
  await s.step('native provider registration refreshes fixture caches and Preferences selects MRP',async()=>{
    cached=true;await saveOwnedProviderProfile(authorSession,author);
    const prefs=await popup(author.schedule,author.schedule.getByTitle(/Edit your personal setting/i).first(),'MRP-preferences');
    const link=prefs.locator('a[href*="method=viewTicklerTaskAssignee"]').first();await revealAuditLink(prefs,link,20000);
    const form=await popup(prefs,link,'MRP-default-assignee');await form.locator('#taskAssigneeMRP').check();
    await ui.clickAndAwaitReload(form,form.locator('form input[type="submit"]'));await form.locator('#AlertBanner').waitFor();
    h.assert(sql.value(`SELECT COUNT(*) FROM property WHERE ${preference} AND value='mrp'`)==='1','Native MRP preference was not saved');
    await form.close();await prefs.close();
  });
  const views=new WeakMap();
  async function master(id,needle) {
    const scheduleUrl=author.schedule.url();
    const result=await openMasterRecord(author.context,author.schedule,recorder,{searchTerm:needle,preferredDemographicNo:id,timeout:25000});
    // The application reuses its named Search window. Retire each completed
    // search so the next patient's opener is observed as a new native window.
    if(result.searchPage!==author.schedule)await result.searchPage.close();
    else await author.schedule.goto(scheduleUrl,{waitUntil:'load'});
    return result.masterPage;
  }
  async function composer(id,needle) {
    const chart=await master(id,needle);
    const list=await popup(chart,masterRecordLink(chart,'/tickler/ViewTicklerMain').first(),'MRP-patient-task-list');
    const page=await popup(list,list.locator('input.btn-primary[onclick*="/tickler/ViewAddTickler"]').first(),'MRP-task-composer');
    views.set(page,{chart,list});return page;
  }
  const field=page=>page.locator('form[name="serviceform"] input[name="demographic_no"]').last();
  async function assertAssignee(page,actor) {
    const select=page.locator('select[name="task_assigned_to"]').first();
    h.assert(await select.inputValue()===actor.fixture.providerNo,'Composer does not select the patient MRP');
    h.assert((await select.locator('option:checked').innerText()).includes(actor.marker),'Composer displays another MRP name');
  }
  async function save(page,id,actor,label,message=marker+' '+label) {
    h.assert(await field(page).inputValue()===id,'Composer has another patient');await assertAssignee(page,actor);
    if (arguments.length < 5) await page.locator('#ticklerMessage').fill(message);
    else h.assert(await page.locator('#ticklerMessage').inputValue() === message, 'Draft changed before save');
    await page.locator('input[name="xml_appointment_date"]').fill(sql.value('SELECT CURDATE()'));
    const [response]=await Promise.all([page.waitForResponse(answer=>answer.request().method()==='POST'&&new URL(answer.url()).pathname.endsWith('/tickler/DbTicklerAdd')),
      waitForSaveSentinel(page,'ticklerSubmitFrame','tickler-save-ok'),page.locator('form[name="serviceform"] input.btn-primary[name="Button"]').first().click()]);
    h.assert(response.status()===200,'MRP task save failed');
    const submitted=new URLSearchParams(response.request().postData());
    h.assert(submitted.get('task_assigned_to')===actor.fixture.providerNo&&submitted.get('demographic_no')===id,'Native request submitted another patient/assignee');
    const rows=sql.rows(`SELECT tickler_no,demographic_no,task_assigned_to,creator,status FROM tickler WHERE message=${q(submitted.get('ticklerMessage'))}`);
    h.assert(rows.length===1&&JSON.stringify(rows[0].slice(1))===JSON.stringify([id,actor.fixture.providerNo,author.fixture.providerNo,'A']),'Saved MRP task has wrong identity, assignee or creator');
    h.assert(sql.value(`SELECT HEX(message) FROM tickler WHERE tickler_no=${rows[0][0]}`) === Buffer.from(submitted.get('ticklerMessage'), 'utf8').toString('hex').toUpperCase(), 'Stored task text differs from the submitted draft');
    if(!page.isClosed())await page.close();
    const view=views.get(page);
    if(view) {
      if(view.list===author.schedule)await author.schedule.goto(view.scheduleUrl,{waitUntil:'load'});
      else await view.list.close();
      if(view.chart)await view.chart.close();
    }
    return rows[0][0];
  }
  h.assert(sql.value(`SELECT COUNT(*) FROM tickler WHERE demographic_no IN (${patientSet})`)==='0','Owned patient already has task references');
  const controlId=h.insertId(sql,`INSERT INTO tickler(demographic_no,message,status,update_date,service_date,creator,priority,task_assigned_to)
    VALUES(${patientB},${q(marker+' CONTROL')},'A',NOW(),CURDATE(),${q(author.fixture.providerNo)},'Normal',${q(control.fixture.providerNo)})`,'MRP control task');
  const controlState=JSON.stringify(sql.rows(`SELECT * FROM tickler WHERE tickler_no=${controlId}`));
  const primary={};
  await s.step('each patient native master-record workflow saves exactly one task to its distinct active MRP',async()=>{
    primary.a=await save(await composer(patient,marker),patient,a,'MRP-A');
    primary.b=await save(await composer(patientB,bMarker),patientB,b,'MRP-B');
  });
  const ownedRow=(list,id)=>list.locator(`input[name="checkbox"][value="${id}"]`).locator('xpath=ancestor::tr[1]');
  async function assignedList(actor) {
    const {page:list}=await ui.clickOpensPopupOrNavigates(actor.schedule,actor.schedule.locator('a:has(#oscar_new_tickler)').first(),
      {context:actor.context,recorder,label:'MRP-assigned-list'});
    await waitForTicklerTable(list);
    await Promise.all([list.waitForResponse(response=>new URL(response.url()).pathname.endsWith('/tickler/ListTicklers')&&
      new URL(response.url()).searchParams.get('search[value]')===marker),list.locator('#ticklerResults_filter input[type="search"]').fill(marker)]);
    await list.locator('#assignedTo').selectOption(actor.fixture.providerNo);
    const [response]=await Promise.all([list.waitForResponse(answer=>new URL(answer.url()).pathname.endsWith('/tickler/ListTicklers')&&
      new URL(answer.url()).searchParams.get('assignee')===actor.fixture.providerNo),list.locator('#formSubmitBtn').click()]);
    h.assert(response.status()===200,'MRP assigned-task filter failed');await waitForTicklerTable(list);
    const payload=await response.json();h.assert(Array.isArray(payload.data),'MRP assigned-task response is malformed');
    return {list,ids:payload.data.map(row=>String(row.id)).sort()};
  }
  await s.step('distinct receiving MRPs see only their task and open the correct patient; control keeps its own task',async()=>{
    for(const [actor,id,demographic] of [[a,primary.a,patient],[b,primary.b,patientB],[control,controlId,patientB]]) {
      const {list,ids}=await assignedList(actor);
      h.assert(JSON.stringify(ids)===JSON.stringify([id]),'Recipient sees an unintended MRP/control task');
      const row=ownedRow(list,id);
      const chart=await ui.clickOpensPopup(list,row.locator(`a[onclick*="/DemographicEdit?demographic_no=${demographic}"]`),
        {context:actor.context,recorder,label:'MRP-recipient-patient'});
      h.assert(new URL(chart.url()).searchParams.get('demographic_no')===demographic,'Recipient task opens the wrong patient');
      await h.assertNotErrorPage(chart,'MRP recipient patient');await chart.close();
    }
    h.assert(JSON.stringify(sql.rows(`SELECT * FROM tickler WHERE tickler_no=${controlId}`))===controlState,'MRP task reads changed the control');
  });
  const findings=[];
  function finding(label,message) {
    const error=h.markFailedStep(new Error(message),label);findings.push(error);console.error('  FAIL '+NAME+': '+label+' -- '+message);
  }
  await s.step('independent composers retain their own unsaved drafts',async()=>{
    const one=await composer(patient,marker);
    await one.locator('#ticklerMessage').fill(marker+' first draft');
    await one.locator('input[name="xml_appointment_date"]').fill('2031-01-31');
    await one.locator('select[name="priority"]').selectOption('High');
    await selectTicklerAssignee(one,control.fixture.providerNo);
    // The New Tickler button always reuses the window named "attachment".
    // Duplicate the natively opened URL as a browser tab, in the SAME context,
    // then change its patient through the real search form. This models the
    // browser's Duplicate Tab operation without replacing the app's opener.
    h.assert(new URL(one.url()).pathname.endsWith('/tickler/ViewAddTickler'),'Only an observed native composer can be duplicated');
    const two=await author.context.newPage();h.wireStrictPage(two,'MRP-duplicate-composer',recorder);
    await two.goto(one.url(),{waitUntil:'load'});await two.locator('#ticklerMessage').waitFor();
    views.set(two,{});
    await two.locator('#ticklerMessage').fill(marker+' second draft');
    await attach(two,patientB,bMarker,marker+' second draft');await assertAssignee(two,b);
    const search=one.locator('form[name="ADDAPPT"]');
    await search.locator('input[name="keyword"]').fill(marker);
    await ui.clickAndAwaitReload(one,search.locator('input[type="submit"][name="Submit"]'));
    const repeated=one.locator('form[name="titlesearch"]');
    if(!await repeated.locator('input[name="keyword"]').isVisible())await one.locator('#searchPopUpButton').click();
    h.assert(sql.value("SELECT COUNT(*) FROM demographic WHERE last_name LIKE 'FAKE-PW%' ")==='2','Paging namespace contains unexpected patients');
    await repeated.locator('input[name="keyword"]').fill('FAKE-PW');
    await repeated.locator('input[name="limit2"]').evaluate(input=>{input.value='1';});
    const [repeatedResponse]=await Promise.all([
      one.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/demographic/DemographicSearch')),
      ui.clickAndAwaitReload(one,repeated.locator('input[type="submit"]')),
    ]);
    const repeatedFields=new URLSearchParams(repeatedResponse.request().postData());
    for(const name of ['ticklerMessage','ticklerDraftDate','ticklerDraftPriority','taskTo','originalpage'])
      h.assert(repeatedFields.getAll(name).length===1,'Repeated search duplicated or omitted a draft field: '+name);
    for(const button of ['#nextPageButton','#prevPageButton']) {
      await ui.clickAndAwaitReload(one,one.locator(button));
      const paging=one.locator('form[name="nextform"]');
      h.assert(await paging.locator('input[name="ticklerMessage"]').inputValue()===marker+' first draft'&&await paging.locator('input[name="ticklerDraftDate"]').inputValue()==='2031-01-31','Paging lost the owned draft');
    }
    await ui.clickAndAwaitReload(one,one.locator('#ticklerReturnDraft'));
    h.assert(await one.locator('#ticklerMessage').inputValue()===marker+' first draft','First composer lost its own draft');
    h.assert(await two.locator('#ticklerMessage').inputValue()===marker+' second draft','Another composer substituted the draft');
    h.assert(await one.locator('input[name="xml_appointment_date"]').inputValue()==='2031-01-31'&&await one.locator('select[name="priority"]').inputValue()==='High','Returning from search lost the draft date or priority');
    await assertAssignee(one,control);await assertAssignee(two,b);
    h.assert(await field(one).inputValue()===patient&&await field(two).inputValue()===patientB,'Returning a draft changed another patient association');
    for(const page of [one,two]) {
      const view=views.get(page);await page.close();if(view.list&&!view.list.isClosed())await view.list.close();if(view.chart&&!view.chart.isClosed())await view.chart.close();
    }
  });
  async function attach(page,id,needle,draft) {
    const form=page.locator('form[name="ADDAPPT"]');await form.locator('input[name="keyword"]').fill(needle);
    await ui.clickAndAwaitReload(page,form.locator('input[type="submit"][name="Submit"]'));
    await ui.clickAndAwaitReload(page,page.locator(`input[name="pick_demographic"][value="${id}"]`).first());
    h.assert(await field(page).inputValue()===id,'Late patient attachment selected another patient');
    if(await page.locator('#ticklerMessage').inputValue()!==draft)finding('unsaved MRP composer survives patient selection','Native patient selection lost the unsaved tickler message');
  }
  await s.step('patient-free composition follows attached and changed patient MRPs, then saves the final association',async()=>{
    const scheduleUrl=author.schedule.url();
    const {page:list}=await ui.clickOpensPopupOrNavigates(author.schedule,author.schedule.locator('a:has(#oscar_new_tickler)').first(),
      {context:author.context,recorder,label:'MRP-patient-free-list'});
    const page=await popup(list,list.locator('input.btn-primary[onclick*="/tickler/ViewAddTickler"]').first(),'MRP-patient-free-composer');
    views.set(page,{list,scheduleUrl});
    h.assert(['','0'].includes(await field(page).inputValue()),'Patient-free entry unexpectedly inherited another patient');
    const draft=marker+' late-attachment draft\nline two: "quoted" & café';await page.locator('#ticklerMessage').fill(draft);
    const search=page.locator('form[name="ADDAPPT"]');
    await search.locator('input[name="keyword"]').fill(bMarker);
    await ui.clickAndAwaitReload(page,search.locator('input[type="submit"][name="Submit"]'));
    await ui.clickAndAwaitReload(page,page.locator('#ticklerReturnDraft'));
    h.assert(await page.locator('#ticklerMessage').inputValue()===draft,'Returning from patient search lost the draft');
    h.assert(['','0'].includes(await field(page).inputValue()),'Cancelled search attached a patient');
    await attach(page,patientB,bMarker,draft);await assertAssignee(page,b);
    await page.locator('#ticklerMessage').fill(draft);
    await attach(page,patient,marker,draft);await assertAssignee(page,a);
    await save(page,patient,a,'MRP-LATE-FINAL',draft);
  });
  await s.step('a deliberate manual assignee overrides the patient MRP without changing the control task',async()=>{
    const page=await composer(patient,marker);await assertAssignee(page,a);
    const draft=marker+' deliberate manual recipient';await page.locator('#ticklerMessage').fill(draft);
    await selectTicklerAssignee(page,control.fixture.providerNo);
    await attach(page,patientB,bMarker,draft);await assertAssignee(page,control);
    await save(page,patientB,control,'MRP-MANUAL',draft);
    h.assert(JSON.stringify(sql.rows(`SELECT * FROM tickler WHERE tickler_no=${controlId}`))===controlState,'Manual assignment changed the control task');
  });
  async function unavailable(label,mrp) {
    sql.execute(`UPDATE demographic SET provider_no=${q(mrp)} WHERE demographic_no=${patient} AND last_name=${q(marker)}`);
    const page=await composer(patient,marker),draft=marker+' '+label+' draft';
    await page.locator('#ticklerMessage').fill(draft);
    const selected=await page.locator('select[name="task_assigned_to"]').first().inputValue();
    const guidance=(await page.locator('[role="alert"]:visible,[role="status"]:visible').allInnerTexts()).join(' ');
    if(!['',author.fixture.providerNo].includes(selected)||!/MRP|responsible|unavailable|inactive|assign.*provider/i.test(guidance))
      finding(label+' MRP has explicit safe fallback or validation',`Unavailable MRP silently selected ${JSON.stringify(selected)} without explicit guidance; save to that recipient was not attempted`);
    else console.log('  PASS '+NAME+': '+label+' MRP has explicit safe fallback or validation');
    await s.step(label+' MRP draft survives a deliberate manual assignment and owned save',async()=>{
      await selectTicklerAssignee(page,author.fixture.providerNo);
      h.assert(await page.locator('#ticklerMessage').inputValue()===draft,'Manual fallback lost the draft');
      await save(page,patient,author,'MRP-'+label.toUpperCase()+'-MANUAL',draft);
    });
    sql.execute(`UPDATE demographic SET provider_no=${q(a.fixture.providerNo)} WHERE demographic_no=${patient} AND last_name=${q(marker)}`);
  }
  await unavailable('missing','');
  const stale=await composer(patientB,bMarker),staleDraft=marker+' stale recipient draft\nquoted \"text\" & café';
  await assertAssignee(stale,b);await stale.locator('#ticklerMessage').fill(staleDraft);
  await s.step('native administration makes only the owned second MRP inactive for the unavailable-recipient case',async()=>{
    await b.context.close();await saveOwnedProviderProfile(authorSession,b,{status:'0'});
  });
  await s.step('a recipient made inactive after composition is refused without a write and the draft recovers manually',async()=>{
    const snapshot=()=>JSON.stringify(sql.rows(`SELECT * FROM tickler WHERE demographic_no IN (${patientSet}) ORDER BY tickler_no`));
    const initial=snapshot(),before={http:new Set(recorder.badResponses),console:new Set(recorder.consoleIssues)};
    const [response]=await Promise.all([
      stale.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/tickler/DbTicklerAdd')),
      stale.locator('form[name="serviceform"] input.btn-primary[name="Button"]').first().click(),
    ]);
    h.assert(response.status()===400,'A stale inactive recipient was not refused');
    await stale.locator('#error').waitFor({state:'visible'});
    h.assert(!stale.isClosed()&&await stale.locator('#ticklerMessage').inputValue()===staleDraft&&await stale.locator('#error').innerText()!=='','Recipient refusal was silent or lost the draft');
    h.assert(snapshot()===initial,'Recipient refusal changed task records');
    await require('./lib/tickler-refusal-diagnostics').acknowledgeTicklerRecipient400(recorder,{
      before,response,page:stale,expectedUrl:h.appUrl(config.baseUrl,'/tickler/DbTicklerAdd'),patient:patientB,label:'MRP-task-composer',
    });
    await selectTicklerAssignee(stale,author.fixture.providerNo);
    await save(stale,patientB,author,'MRP-STALE-RECOVERED',staleDraft);
  });
  await unavailable('inactive',b.fixture.providerNo);
  h.assert(JSON.stringify(sql.rows(`SELECT * FROM tickler WHERE tickler_no=${controlId}`))===controlState,'Unavailable MRP cases changed the control task');
  owner();
  require('./lib/workflow-findings').throwFindings(findings);
}
if(require.main===module)runWorkflow(NAME,workflow,{openMaster:false,preflightConfig:()=>{
  if(process.env.CARLOS_DISPOSABLE_HOST!=='true'||process.env.EXCLUSIVE!=='1')throw new h.SkipCheck('Requires CARLOS_DISPOSABLE_HOST=true and EXCLUSIVE=1 for owned MRP fixtures/cache retirement');
}});
module.exports={workflow};
