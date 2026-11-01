#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h=require('./lib/playwright-harness');
const {runWorkflow}=require('./lib/workflow-session');
const {masterRecordLink}=require('./lib/master-record-link');
const {viewerDocuments}=require('./document-viewer-review-actions-playwright-checks');
const {sha}=require('./lib/stored-pdf-documents');
const {openInbox,openSidebar,setMode,search}=require('./inbox-file-forward-playwright-checks');
async function workflow(s) {
  const {sql,patient,marker}=s;
  const {docs}=viewerDocuments(s,['primary','control']);
  const primary=docs.primary,control=docs.control;
  const subclass=marker+' Subclass',custom=marker+' Custom';
  s.cleanup(()=>{
    sql.execute(`DELETE FROM ctl_doc_class WHERE reportClass=${h.sqlString(marker)} AND subClass=${h.sqlString(subclass)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM ctl_doc_class WHERE reportClass=${h.sqlString(marker)}`)==='0','Owned subclass fixture remains');
  });
  sql.execute(`INSERT INTO ctl_doc_class(reportClass,subClass) VALUES (${h.sqlString(marker)},${h.sqlString(subclass)})`);
  // The legacy editor round-trips nullable text metadata as empty strings.
  // Normalize those equivalent empty representations, preserving exact dates,
  // provider identities, clinical text and every nonempty value.
  const fields=['docdesc','doctype','docfilename','doccreator','source','sourceFacility','observationdate','reviewer','reviewdatetime','public1','docClass','docSubClass','responsible','abnormal'];
  const row=id=>sql.rows(`SELECT ${fields.map(name=>`COALESCE(${name},'')`).join(',')} FROM document WHERE document_no=${id}`);
  const routing=id=>sql.rows(`SELECT provider_no,status,comment FROM providerLabRouting WHERE lab_type='DOC' AND lab_no=${id} ORDER BY id`);
  const controlBefore=JSON.stringify({row:row(control.id),routing:routing(control.id),file:sha(control.file)});
  const stableBefore=row(primary.id)[0].slice(0,11),routingBefore=JSON.stringify(routing(primary.id)),fileBefore=sha(primary.file);
  const report=await s.popup(s.master,masterRecordLink(s.master,'/documentManager/ViewDocumentReport').first(),'metadata-document-list');
  async function edit() {
    await report.locator(`#docNo${primary.id}`).waitFor({state:'attached'});
    const row=report.locator('tr',{has:report.locator(`#docNo${primary.id}`)});
    const editor=await s.popup(report,row.locator('a[onclick*="/documentManager/ViewEditDocument"]'),'metadata-editor');
    h.assert(await editor.locator('input[name="mode"]').inputValue()===primary.id,'Metadata editor opened the wrong document');
    h.assert(await editor.locator('input[name="docPublic"]').count()===0,'Patient document incorrectly offers provider-public control');
    const expected=stableBefore;
    for(const [selector,index] of [['[name="docDesc"]',0],['[name="docType"]',1],['[name="source"]',4],['[name="sourceFacility"]',5],['[name="observationDate"]',6],['[name="docClass"]',10]]) {
      const value=await editor.locator(selector).inputValue();
      h.assert(value===expected[index],`Reopened metadata ${selector} differs from its unchanged clinical value`);
    }
    return editor;
  }
  async function save(editor) {
    const closed=editor.waitForEvent('close'),loaded=report.waitForEvent('load');
    const [response]=await Promise.all([editor.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/documentManager/addEditDocument')),
      editor.locator('input[name="Submit"]').click()]);
    h.assert(response.status()===200,'Document metadata update failed');await closed;await loaded;
    const changed=fields.slice(0,11).filter((name,index)=>row(primary.id)[0][index]!==stableBefore[index]);
    h.assert(!changed.length,`Metadata edit changed unrelated document fields: ${changed.join(', ')}`);
    h.assert(JSON.stringify(routing(primary.id))===routingBefore,'Responsibility or abnormal metadata changed reviewer routing/acknowledgement');
    h.assert(sha(primary.file)===fileBefore,'Metadata edit changed document bytes');
    h.assert(JSON.stringify({row:row(control.id),routing:routing(control.id),file:sha(control.file)})===controlBefore,'Metadata edit changed the control document');
  }
  await s.step('subclass autocomplete selects a controlled suggestion and reopens its exact value',async()=>{
    let editor=await edit();await editor.locator('#docSubClass').fill(marker);
    await editor.locator('#docSubClass_list li').getByText(subclass,{exact:true}).click();
    h.assert(await editor.locator('#docSubClass').inputValue()===subclass,'Autocomplete did not choose the offered subclass');await save(editor);
    h.assert(row(primary.id)[0][11]===subclass,'Selected subclass did not persist');
    editor=await edit();h.assert(await editor.locator('#docSubClass').inputValue()===subclass,'Subclass did not reopen');await editor.close();
  });
  await s.step('custom subclass saves, cancellation preserves it and clearing reopens empty',async()=>{
    let editor=await edit();await editor.locator('#docSubClass').fill(custom);await save(editor);
    h.assert(row(primary.id)[0][11]===custom,'Custom subclass did not persist');
    editor=await edit();await editor.locator('#docSubClass').fill('unsaved');await editor.close();
    h.assert(row(primary.id)[0][11]===custom,'Cancelled subclass edit persisted');
    editor=await edit();await editor.locator('#docSubClass').fill('');await save(editor);
    h.assert(row(primary.id)[0][11]==='','Subclass did not clear');editor=await edit();h.assert(await editor.locator('#docSubClass').inputValue()==='','Cleared subclass did not reopen');await editor.close();
  });
  await s.step('responsible provider reassigns, reopens, cancels and clears independently of review routing',async()=>{
    let editor=await edit();const options=await editor.locator('select[name="responsibleId"] option').evaluateAll(nodes=>nodes.map(n=>n.value));
    const providers=options.filter(value=>value&&value!==s.provider);if(providers.length<2)throw new h.SkipCheck('Responsibility fixture needs two offered alternate providers');
    await editor.close();
    for(const value of [providers[0],providers[1],'']) {
      editor=await edit();await editor.locator('select[name="responsibleId"]').selectOption(value);await save(editor);
      h.assert((row(primary.id)[0][12]||'')===value,'Responsible provider did not persist');
      editor=await edit();h.assert(await editor.locator('select[name="responsibleId"]').inputValue()===value,'Responsible provider did not reopen');
      await editor.locator('select[name="responsibleId"]').selectOption(s.provider);await editor.close();
      h.assert((row(primary.id)[0][12]||'')===value,'Cancelled provider reassignment persisted');
    }
  });
  await s.step('abnormal flag toggles and survives an unrelated edit without changing review status',async()=>{
    const inbox=await openInbox(s.schedule,s.context,s.recorder,'metadata-inbox');
    await setMode(inbox,'list');await openSidebar(inbox);
    await inbox.locator('#statusNew').check();await inbox.locator('#specificPatients').check();
    await inbox.locator('#inputLastName').fill(marker);
    for(const checked of [true,false]) {
      let editor=await edit();await editor.locator('input[name="abnormal"]').setChecked(checked);await save(editor);
      h.assert(row(primary.id)[0][13]===(checked?'1':'0'),'Abnormal checkbox did not persist');
      editor=await edit();h.assert(await editor.locator('input[name="abnormal"]').isChecked()===checked,'Abnormal state did not reopen');
      await editor.locator('#docSubClass').fill(custom);await save(editor);h.assert(row(primary.id)[0][13]===(checked?'1':'0'),'Unrelated metadata edit reset abnormal state');
      await openSidebar(inbox);await inbox.locator('#abnormalAll').check();await search(inbox);
      const listed=inbox.locator(`#labdoc_DOC_${primary.id}`),controlRow=inbox.locator(`#labdoc_DOC_${control.id}`);
      await listed.waitFor({state:'visible'});await controlRow.waitFor({state:'visible'});
      h.assert((await listed.getAttribute('class')||'').includes('table-danger')===checked,'Inbox abnormal marker differs from the saved checkbox');
      h.assert(!(await controlRow.getAttribute('class')||'').includes('table-danger'),'Abnormal edit marked the control document');
      for(const [selector,expected] of [['#Abnormal',checked],['#abnormalNormal',!checked]]) {
        await openSidebar(inbox);await inbox.locator(selector).check();await search(inbox);
        h.assert(await listed.count()===(expected?1:0),'Inbox abnormal filter disagrees with stored metadata');
        h.assert(await controlRow.count()===(selector==='#abnormalNormal'?1:0),'Inbox abnormal filter changed the normal control document');
      }
    }
    if(inbox!==s.schedule)await inbox.close();
  });
  // Responsibility updates only document.responsible. providerLabRouting remains
  // the independent acknowledgement assignment, as asserted after every save.
  h.assert(sql.value(`SELECT COUNT(*) FROM ctl_document WHERE document_no=${primary.id} AND module='demographic' AND module_id=${patient}`)==='1','Metadata edit changed the patient linkage');
}
if(require.main===module)runWorkflow('document-metadata-controls',workflow,{openPatient:true});
module.exports={workflow};
