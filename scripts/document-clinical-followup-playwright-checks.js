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
const {chartNoteSave}=require('./lib/mutation-families');
const {retireOwnedChartPages}=require('./lib/owned-chart-page-cleanup');
const q=h.sqlString;
async function workflow(s) {
  const {sql,patient,provider,marker}=s;
  const clinician={providerNo:provider,username:s.config.testUser};
  const {docs,openViewer}=viewerDocuments(s,['primary','control']);
  const primary=docs.primary,control=docs.control;
  // This synthetic document starts unrouted to the chosen clinician; the real
  // Forward control below must create that assignment. Other owned routes remain.
  sql.execute(`DELETE FROM providerLabRouting WHERE lab_type='DOC' AND lab_no=${primary.id} AND provider_no=${q(provider)}`);
  const [[lastName,firstName]]=sql.rows(`SELECT last_name,first_name FROM provider WHERE provider_no=${q(provider)}`);
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
    const viewer=await popup(inbox,row.locator('a[onclick*="reportWindow"]').first(),'review-forwarded-document');
    h.assert(new URL(viewer.url()).searchParams.get('segmentID')===primary.id,'Inbox opened another document');
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
    const [saved]=await Promise.all([chart.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/CaseManagementEntry')),
      chart.locator('#saveImg').first().click()]);h.assert(saved.status()===200,'Clinical action note failed');
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
}
if(require.main===module)runWorkflow('document-clinical-followup',workflow,{openPatient:true});
module.exports={workflow};
