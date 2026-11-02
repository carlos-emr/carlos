#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h=require('./lib/playwright-harness'),ui=require('./lib/playwright-ui');
const {runWorkflow,expectValue}=require('./lib/workflow-session');
const {signIn}=require('./lib/authz-read-probe');
const {viewerDocuments}=require('./document-viewer-review-actions-playwright-checks');
const {openInbox,openSidebar,setMode,search}=require('./inbox-file-forward-playwright-checks');
const {openMasterRecord}=require('./master-record-tabs-playwright-checks');
const {masterRecordLink}=require('./lib/master-record-link');
const {openChart,waitForNavbars}=require('./echart-navbar-modules-playwright-checks');
const {chartNoteSave,providerUpdate}=require('./lib/mutation-families');
const {authzReadFixture}=require('./lib/authz-read-fixture');
const {retireOwnedChartPages}=require('./lib/owned-chart-page-cleanup');
const q=h.sqlString;
async function workflow(s) {
  const {sql,patient,provider,marker}=s;
  const fixture=authzReadFixture({sql,marker,provider,testUser:s.config.testUser});
  let activated=false;
  s.cleanup(()=>{h.assert(!activated,'Owned clinician cache retirement failed; retain the account for recovery');fixture.cleanup();});
  const clinician=fixture.addLogin('doctor');
  h.assert(clinician.providerNo!==provider,'The reviewer must be distinct from the document sender');
  if(!fixture.joinTestLoginPrograms(clinician))throw new h.SkipCheck('Document follow-up requires a configured program membership to copy to the owned clinician');
  const activation=providerUpdate(s,{target:clinician});s.cleanup(()=>activation.cleanup());
  s.cleanup(()=>{
    h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${q(clinician.providerNo)} AND last_name=${q(marker)}`)==='1','Clinician ownership changed');
    sql.execute(`DELETE FROM provider_default_program WHERE provider_no=${q(clinician.providerNo)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM provider_default_program WHERE provider_no=${q(clinician.providerNo)}`)==='0','Owned clinician default program remains');
  });
  // SQL fixture creation alone does not evict the active-provider picker cache.
  // A native edit publishes this owned clinician; a native inactive edit below
  // removes it from the cache before the SQL fixture is deleted.
  activated=true;
  try {
  await s.step('a native staff edit publishes the distinct owned reviewer to provider pickers',async()=>{
    await s.schedule.goto(s.scheduleUrl,{waitUntil:'networkidle'}); // nosemgrep: captured authenticated schedule URL
    await activation.perform('STAFF');
  });
  const {docs,openViewer}=viewerDocuments(s,['primary','control']);
  const primary=docs.primary,control=docs.control;
  // This synthetic document starts unrouted to the chosen clinician; the real
  // Forward control below must create that assignment. Other owned routes remain.
  sql.execute(`DELETE FROM providerLabRouting WHERE lab_type='DOC' AND lab_no=${primary.id} AND provider_no=${q(clinician.providerNo)}`);
  const [[lastName,firstName]]=sql.rows(`SELECT last_name,first_name FROM provider WHERE provider_no=${q(clinician.providerNo)}`);
  const routing=id=>sql.rows(`SELECT provider_no,status,comment FROM providerLabRouting WHERE lab_type='DOC' AND lab_no=${id} ORDER BY id`);
  const controlDocument=JSON.stringify({document:sql.rows(`SELECT * FROM document WHERE document_no=${control.id}`),routing:routing(control.id)});
  const taskMessage=marker+'-followup',controlMessage=marker+'-control-task';
  s.cleanup(()=>{
    const ids=sql.rows(`SELECT tickler_no FROM tickler WHERE demographic_no=${patient} AND message IN (${q(taskMessage)},${q(controlMessage)})`).map(([id])=>id);
    if(ids.length) {
      h.assert(ids.every(id=>/^[1-9]\d*$/.test(id)),'Invalid owned follow-up task identity');
      sql.execute(`DELETE FROM ticklerdocs WHERE tickler_id IN (${ids.join(',')});DELETE FROM tickler_comments WHERE tickler_no IN (${ids.join(',')});
        DELETE FROM tickler_update WHERE tickler_no IN (${ids.join(',')});DELETE FROM tickler WHERE tickler_no IN (${ids.join(',')}) AND demographic_no=${patient}`);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler WHERE message IN (${q(taskMessage)},${q(controlMessage)})`)==='0','Owned follow-up tasks remain');
  });
  const noteCleanup=chartNoteSave(s);s.cleanup(()=>noteCleanup.cleanup());
  const controlTask=h.insertId(sql,`INSERT INTO tickler(demographic_no,creator,task_assigned_to,message,status,priority,service_date,creation_date)
    VALUES (${patient},${q(provider)},${q(clinician.providerNo)},${q(controlMessage)},'A','Normal',CURDATE(),NOW())`,'follow-up control task');
  const controlTaskState=JSON.stringify(sql.rows(`SELECT * FROM tickler WHERE tickler_no=${controlTask}`));
  const popup=(page,locator,label)=>ui.clickOpensPopup(page,locator,{context:page.context(),recorder:s.recorder,label,timeout:30000});
  const mine=`lab_type='DOC' AND lab_no=${primary.id} AND provider_no=${q(clinician.providerNo)}`;
  await s.step('Forward routes the owned document to its intended clinician through the provider autocomplete',async()=>{
    const viewer=await openViewer(primary);await viewer.locator(`#fwdBtn_${primary.id}`).click();
    const dialog=viewer.getByRole('dialog',{name:'Forward Documents',exact:true});await dialog.waitFor();
    await dialog.locator('#autocompleteprov').fill(`${lastName}, ${firstName}`);
    const options=viewer.locator('.ui-autocomplete:visible .ui-menu-item');await options.first().waitFor();
    const index=await options.evaluateAll((nodes,id)=>nodes.findIndex(node=>String(window.jQuery(node).data('ui-autocomplete-item')?.providerNo)===id),clinician.providerNo);
    h.assert(index>=0,'The intended clinician was not offered');await options.nth(index).click();
    const [response]=await Promise.all([viewer.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/oscarMDS/ReportReassign')),
      viewer.waitForEvent('domcontentloaded'),dialog.getByRole('button',{name:'Forward',exact:true}).click()]);
    h.assert(response.status()===200,'Document Forward failed');await expectValue(sql,`SELECT COUNT(*) FROM providerLabRouting WHERE ${mine} AND status='N'`,'1','Forward did not create exactly one New route');
    await viewer.close();
  });
  await retireOwnedChartPages(s,[{chart:await s.chart(),patient,owner:marker}],[]);
  // Reauthenticate the assigned clinician, then reopen this patient's Inbox
  // and chart rather than inheriting the sender window's transient state.
  const who=await signIn(s,clinician);who.context.on('page',page=>h.wireStrictPage(page,'followup-clinician',s.recorder));
  const {masterPage}=await openMasterRecord(who.context,who.page,s.recorder,{searchTerm:marker,preferredDemographicNo:patient,timeout:30000});
  let task,comment=marker+' reviewed: clinical follow-up required';
  await s.step('the clinician opens the forwarded document from Inbox and records its review comment',async()=>{
    const inbox=await openInbox(who.page,who.context,s.recorder,'followup-inbox');await setMode(inbox,'list');await openSidebar(inbox);
    await inbox.locator('#statusNew').check();await inbox.locator('#specificPatients').check();await inbox.locator('#inputLastName').fill(marker);await search(inbox);
    const row=inbox.locator(`#labdoc_DOC_${primary.id}`);await row.waitFor();
    await s.step('the forwarded Inbox row displays its exact seeded description and patient identity',async()=>{
      const cells=row.locator('td');
      h.assert((await cells.nth(7).innerText()).trim()===primary.label,'Inbox omits the exact seeded document description');
      h.assert((await cells.nth(1).innerText()).includes(marker+', Workflow'),'Inbox omits the originating synthetic patient identity');
    });
    const viewer=await popup(inbox,row.locator('a[onclick*="reportWindow"]').first(),'review-forwarded-document');
    h.assert(new URL(viewer.url()).searchParams.get('segmentID')===primary.id,'Inbox opened another document');
    for(const [selector,value] of [[`#docDesc_${primary.id}`,primary.label],[`#docType_${primary.id}`,'others'],[`#observationDate${primary.id}`,'2026/01/02']])
      h.assert(await viewer.locator(selector).inputValue()===value,`Viewer omitted or changed seeded clinical field ${selector}`);
    const content=viewer.locator(`#docPDF_${primary.id},#docImg_${primary.id}`).first();
    await content.waitFor({state:'attached'});
    const image=await content.evaluate(element=>element.tagName==='IMG');
    if(image)await content.evaluate(async element=>{
      if(!element.complete||element.getAttribute('data-document-image-state')!=='loaded')await new Promise((resolve,reject)=>{const started=Date.now();const timer=setInterval(()=>{if(element.getAttribute('data-document-image-state')==='failed'||Date.now()-started>20000){clearInterval(timer);reject(new Error('Viewer document image did not render'));}else if(element.complete&&element.naturalWidth>0&&element.getAttribute('data-document-image-state')==='loaded'){clearInterval(timer);resolve();}},100);});
      await element.decode();
    });
    const target=new URL(image?await content.locator('..').getAttribute('href'):await content.getAttribute('data'),viewer.url());
    h.assert(target.origin===s.config.baseUrl.origin&&target.searchParams.get('doc_no')===primary.id,'Viewer content points at a different document');
    const delivered=await require('./lib/authenticated-probe').authenticatedProbe({...s,context:who.context},target.href);
    try{h.assert(delivered.status()===200&&Buffer.from(await delivered.body()).equals(require('node:fs').readFileSync(primary.file)),'Viewer content does not deliver the exact seeded PDF');}finally{await delivered.dispose();}
    await h.withExpectedDialogs(viewer,async()=>{
      const [response]=await Promise.all([viewer.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/oscarMDS/UpdateStatus')),
        viewer.locator(`#acknowledgeForm_${primary.id} input[onclick^="addDocComment("]`).click()]);
      h.assert(response.status()===200,'Review comment failed');
    },{promptText:comment});
    await expectValue(sql,`SELECT CONCAT(status,'|',comment) FROM providerLabRouting WHERE ${mine}`,'N|'+comment,'Review comment or provider attribution differs');
    await viewer.close();
    const report=await popup(masterPage,masterRecordLink(masterPage,'/documentManager/ViewDocumentReport').first(),'followup-document-list');
    const links=report.locator('a[onclick*="ForwardDemographicTickler"]');
    const index=await links.evaluateAll((nodes,id)=>nodes.findIndex(node=>{
      const code=node.getAttribute('onclick')||'',needle='docId='+id,at=code.indexOf(needle),next=code.charAt(at+needle.length);
      return at>=0&&!(next>='0'&&next<='9');
    }),primary.id);
    h.assert(index>=0,'Document offers no linked follow-up task control');
    const add=await popup(report,links.nth(index),'followup-add-task');
    h.assert(await add.locator('form[name="serviceform"] input[name="demographic_no"]').first().inputValue()===patient&&await add.locator('input[name="docId"]').inputValue()===primary.id,'Follow-up form lost its patient/document linkage');
    await add.locator('textarea[name="ticklerMessage"]').fill(taskMessage);
    await add.locator('select[name="task_assigned_to"]').first().selectOption(clinician.providerNo);
    await Promise.all([add.waitForEvent('close'),add.locator('input.btn-primary[name="Button"]').first().click()]);
    task=sql.value(`SELECT tickler_no FROM tickler WHERE demographic_no=${patient} AND message=${q(taskMessage)}`);h.assert(/^[1-9]\d*$/.test(task),'No follow-up task was saved');
    h.assert(sql.value(`SELECT COUNT(*) FROM ticklerdocs WHERE tickler_id=${task} AND doctype='D' AND document_no=${primary.id}`)==='1','Task lost the reviewed document');
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler WHERE tickler_no=${task} AND creator=${q(clinician.providerNo)} AND task_assigned_to=${q(clinician.providerNo)} AND status='A'`)==='1','Follow-up task was attributed incorrectly or completed before clinical action');
    await report.close();
    const reopened=await popup(inbox,row.locator('a[onclick*="reportWindow"]').first(),'acknowledge-reviewed-document');
    const closed=reopened.waitForEvent('close');
    await h.withExpectedDialogs(reopened,()=>reopened.locator(`#ackBtn_${primary.id}`).click(),{promptText:comment});await closed;
    await expectValue(sql,`SELECT CONCAT(status,'|',comment) FROM providerLabRouting WHERE ${mine}`,'A|'+comment,'Acknowledgement lost the review comment');
    if(inbox!==who.page)await inbox.close();
  });
  await s.step('cancelled completion preserves Active; the assigned clinician records clinical action before completing the same task',async()=>{
    const list=await popup(masterPage,masterRecordLink(masterPage,'/tickler/ViewTicklerMain').first(),'clinical-followup-task-list');
    const row=list.locator('#ticklerResults tbody tr').filter({hasText:taskMessage}).first();await row.waitFor();
    let editor=await popup(list,row.locator('a[onclick*="openTicklerEdit"]'),'cancel-followup-completion');
    h.assert(new URL(editor.url()).searchParams.get('tickler_no')===task,'Task list opened another follow-up');
    await editor.locator('#status').selectOption('C');await editor.close();
    h.assert(sql.value(`SELECT status FROM tickler WHERE tickler_no=${task}`)==='A','Cancelled completion falsely completed clinical follow-up');
    const chart=await openChart(who.context,masterPage,s.recorder,30000);await waitForNavbars(chart,30000);
    const action=marker+`-NS-Reviewed document ${primary.id}; discussed findings and completed the clinical follow-up.`;
    await chart.locator('#encMainDiv textarea[name="caseNote_note"]').first().fill(action);
    const [saved]=await Promise.all([chart.waitForResponse(r=>{
      if(r.request().method()!=='POST'||!new URL(r.url()).pathname.endsWith('/CaseManagementEntry'))return false;
      const body=new URLSearchParams(r.request().postData()||'');
      return ['save','ajaxsave'].includes(body.get('method'))&&(body.get('caseNote_note')||body.get('noteTxt'))===action;
    }),
      chart.locator('#saveImg').first().click()]);h.assert(saved.status()===200,'Clinical action note failed');await saved.finished();
    await expectValue(sql,`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient} AND provider_no=${q(clinician.providerNo)} AND note=${q(action)}`,'1','Clinical action note, patient or clinician attribution differs');
    editor=await popup(list,row.locator('a[onclick*="openTicklerEdit"]'),'complete-clinical-followup');
    h.assert((await editor.locator('#attachmentNames').innerText()).includes(primary.label),'Reopened task does not show the reviewed document');
    await editor.locator('#newMessage').fill(marker+' clinical action documented in chart');await editor.locator('#status').selectOption('C');
    await editor.locator('input[name="updateTickler"]').click();
    await editor.waitForFunction(()=>document.getElementById('ticklerEditFrame')?.contentDocument?.getElementById('tickler-edit-ok'));
    await expectValue(sql,`SELECT status FROM tickler WHERE tickler_no=${task}`,'C','Clinical follow-up task did not complete');await editor.close();
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler_comments WHERE tickler_no=${task} AND provider_no=${q(clinician.providerNo)} AND message=${q(marker+' clinical action documented in chart')}`)==='1','Completion comment or clinician attribution did not persist');
    h.assert(JSON.stringify(sql.rows(`SELECT * FROM tickler WHERE tickler_no=${controlTask}`))===controlTaskState,'Completing follow-up changed the unrelated control task');
    h.assert(JSON.stringify({document:sql.rows(`SELECT * FROM document WHERE document_no=${control.id}`),routing:routing(control.id)})===controlDocument,'Review/follow-up changed the unrelated control document');
  });
  } finally {
    await s.schedule.goto(s.scheduleUrl,{waitUntil:'networkidle'}); // nosemgrep: captured authenticated schedule URL
    const retirement=providerUpdate(s,{target:clinician,configureForm:form=>form.locator('#statusInactive').check()});
    await retirement.perform('RETIRED');
    await expectValue(sql,`SELECT status FROM provider WHERE provider_no=${q(clinician.providerNo)}`,'0','Owned reviewer was not retired from native provider pickers');
    activated=false;
  }

}
if(require.main===module)runWorkflow('document-clinical-followup',workflow,{openPatient:true});
module.exports={workflow};
