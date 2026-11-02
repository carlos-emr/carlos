#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const {createHash}=require('node:crypto');
const h=require('./lib/playwright-harness');
const ui=require('./lib/playwright-ui');
const {runWorkflow}=require('./lib/workflow-session');
const {authzReadFixture}=require('./lib/authz-read-fixture');
const R=require('./lib/authz-read-probe');
const {authenticatedProbe}=require('./lib/authenticated-probe');
const {directory,ownedPdfDocuments,seedOwnedPdfDocuments,removeOwnedPdfDocuments,assertOwnedPdfDocumentsRemoved,sha}=require('./lib/stored-pdf-documents');

async function workflow(s) {
  const {sql,patient,provider,marker}=s,store=directory('DOCUMENT_DIR','DOCUMENT_DIR');
  const docs=ownedPdfDocuments(store,marker,[{key:'PROVIDER'},{key:'CONTROL'},{key:'PATIENT'}]);
  const owned={sql,patient,marker,docs,files:docs.map(doc=>doc.file)};
  s.cleanup(()=>{
    const ids=docs.map(doc=>doc.id).filter(Boolean);
    if(ids.length)sql.execute(`DELETE FROM ctl_document WHERE document_no IN (${ids.join(',')}) AND module='providers' AND module_id=${h.sqlString(provider)}`);
    removeOwnedPdfDocuments(owned);assertOwnedPdfDocumentsRemoved(owned);
    if(ids.length)h.assert(sql.value(`SELECT COUNT(*) FROM ctl_document WHERE document_no IN (${ids.join(',')})`)==='0','Provider document links remain');
  });
  seedOwnedPdfDocuments({sql,store,patient,provider,docs});
  const [primary,control,patientDoc]=docs;
  for(const doc of [primary,control])sql.execute(`DELETE FROM ctl_document WHERE document_no=${doc.id} AND module='demographic' AND module_id=${patient};
    INSERT INTO ctl_document(module,module_id,document_no,status) VALUES ('providers',${h.sqlString(provider)},${doc.id},'A')`);
  // An imported public flag on a patient document must never move it into the
  // provider public bucket or bypass its actual patient's access restrictions.
  sql.execute(`UPDATE document SET public1=1 WHERE document_no=${patientDoc.id}`);
  const controlState=JSON.stringify(sql.rows(`SELECT * FROM document WHERE document_no=${control.id}`)),controlSha=sha(control.file),primarySha=sha(primary.file);
  const fixture=authzReadFixture({sql,marker,provider,testUser:s.config.testUser});s.cleanup(()=>fixture.cleanup());
  const reader=fixture.addLogin('doctor');
  const restricted=fixture.addLogin(fixture.addRole({_appointment:'r',_demographic:'r',_eChart:'r'}));
  fixture.lockPatient(reader,patient);
  const who=await R.signIn(s,reader),denied=await R.signIn(s,restricted);
  const openOwnReport=async(context,schedule,label)=>{
    const [response,{page}]=await Promise.all([context.waitForEvent('response',{predicate:r=>new URL(r.url()).pathname.endsWith('/documentManager/ViewDocumentReport')}),
      ui.clickOpensPopupOrNavigates(schedule,schedule.locator("a[onclick*='/documentManager/ViewDocumentReport']").first(),
        {context,recorder:s.recorder,label,timeout:20000,checkErrorPage:false})]);
    h.assert(response.status()===200,`${label} returned HTTP ${response.status()}`);
    await h.assertNotErrorPage(page,label);return page;
  };
  const report=await openOwnReport(s.context,s.schedule,'owner-provider-document-list'),otherReport=await openOwnReport(who.context,who.page,'reader-provider-document-list');
  const listed=(page,doc)=>page.locator('a').filter({hasText:doc.label});
  const flag=()=>sql.value(`SELECT public1 FROM document WHERE document_no=${primary.id}`);
  async function editor() {
    const row=report.locator('tr',{has:report.locator(`#docNo${primary.id}`)});
    const page=await s.popup(report,row.locator('a[onclick*="/documentManager/ViewEditDocument"]'),'provider-public-editor');
    h.assert(await page.locator('input[name="mode"]').inputValue()===primary.id,'Public editor opened another document');
    await page.locator('input[name="docPublic"]').waitFor({state:'visible'});return page;
  }
  async function save(page) {
    const closed=page.waitForEvent('close'),loaded=report.waitForEvent('load');
    const [response]=await Promise.all([page.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/documentManager/addEditDocument')),
      page.locator('input[name="Submit"]').click()]);
    h.assert(response.status()===200,'Public flag update failed');await closed;await loaded;
    h.assert(JSON.stringify(sql.rows(`SELECT * FROM document WHERE document_no=${control.id}`))===controlState&&sha(control.file)===controlSha,'Public edit changed the control document');
    h.assert(sha(primary.file)===primarySha,'Public edit changed the stored PDF');
  }
  await s.step('provider Public toggles persist and reopen; cancellation changes neither flag nor visibility',async()=>{
    h.assert(await listed(otherReport,primary).count()===0&&await listed(otherReport,control).count()===0,'Another provider list exposed private provider documents');
    let page=await editor();await page.locator('input[name="docPublic"]').check();await page.close();
    h.assert(flag()==='0','Cancelled Public edit persisted');
    page=await editor();await page.locator('input[name="docPublic"]').check();await save(page);h.assert(flag()==='1','Public flag did not persist');
    page=await editor();h.assert(await page.locator('input[name="docPublic"]').isChecked(),'Public flag did not reopen');await page.close();
    await otherReport.reload();await listed(otherReport,primary).waitFor({state:'visible'});
    h.assert(await listed(otherReport,control).count()===0&&await listed(otherReport,patientDoc).count()===0,'Public bucket included a private control or patient document');
  });
  await s.step('an authorized document reader opens the public PDF through its real list control',async()=>{
    const result=await ui.clickDownloadsOrOpens(otherReport,listed(otherReport,primary).first(),{context:who.context,recorder:s.recorder,label:'public-document-bytes',timeout:20000});
    const params=new URL(result.url).searchParams;
    h.assert(params.get('doc_no')===primary.id,'Public document link opened another document');
    const response=await authenticatedProbe({context:who.context,config:s.config},result.url);
    h.assert(response.status()===200&&createHash('sha256').update(await response.body()).digest('hex')===primarySha,'Public document view returned different bytes');
    await response.dispose();if(result.page)await result.page.close();
  });
  await s.step('Public does not bypass document-read privilege or the actual patient lock',async()=>{
    const paths=[new URL(otherReport.url()).pathname.replace(s.config.baseUrl.pathname,'')+new URL(otherReport.url()).search,
      `/documentManager/ManageDocument?method=display&doc_no=${primary.id}`,
      `/documentManager/ViewShowDocument?segmentID=${primary.id}`];
    for(const path of paths) {
      const result=await R.probe(denied.context,h.appUrl(s.config.baseUrl,path),{baseUrl:s.config.baseUrl});
      h.assert(R.refusedByApp(result),'A login without document-read rights accessed a Public list/view/download');
    }
    for(const path of [`/documentManager/ManageDocument?method=display&doc_no=${patientDoc.id}`,`/documentManager/ViewShowDocument?segmentID=${patientDoc.id}`]) {
      const result=await R.probe(who.context,h.appUrl(s.config.baseUrl,path),{baseUrl:s.config.baseUrl});
      h.assert(R.refusedByApp(result),'A Public flag exposed a locked patient document');
    }
  });
  await s.step('clearing Public removes the document from another provider list and reopens unchecked',async()=>{
    let page=await editor();await page.locator('input[name="docPublic"]').uncheck();await save(page);h.assert(flag()==='0','Public flag did not clear');
    await otherReport.reload();h.assert(await listed(otherReport,primary).count()===0,'Cleared Public document remains in another provider list');
    page=await editor();h.assert(!await page.locator('input[name="docPublic"]').isChecked(),'Cleared Public flag did not reopen');await page.close();
  });
}
if(require.main===module)runWorkflow('document-public-visibility',workflow,{openPatient:true});
module.exports={workflow};
