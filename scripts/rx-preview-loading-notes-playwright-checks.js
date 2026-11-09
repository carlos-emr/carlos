#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h=require('./lib/playwright-harness');
const {runWorkflow}=require('./lib/workflow-session');
const {prepareRx}=require('./lib/rx-ui-workflow');
const {stageCustomDrugSettled}=require('./rx-stash-patient-isolation-playwright-checks');
const {viewScriptFrame}=require('./rx-drug-name-quotes-playwright-checks');
const {noteBodyTracker}=require('./gap-encounter-rx-script-notes-playwright-checks');
async function workflow(s) {
  const f=await prepareRx(s),rx=await f.open();
  const consumeNoteBodies=noteBodyTracker(rx,s.recorder);
  const note=s.marker+' loading-preview notes';
  const key=await stageCustomDrugSettled(rx,s.marker+' preview');
  await rx.locator('#instructions_'+key).fill('1 tab PO OD');
  await rx.locator('label[for="jsonDxSearch_'+key+'"]').click();
  await rx.waitForLoadState('networkidle');
  let intercepted=false;
  // Supply a loading document without the preview form, then request the real preview below.
  // Delaying the route itself can be superseded by the native signature refresh and cancel it.
  const handler=async route=>{intercepted=true;await route.fulfill({status:200,contentType:'text/html',body:'<!doctype html><html><body>Loading preview</body></html>'});};
  await s.context.route('**/rx/ViewPreview2?**',handler);
  try {
    const [saved]=await Promise.all([rx.waitForResponse(r=>r.request().method()==='POST'&&r.url().includes('parameterValue=updateSaveAllDrugs')),rx.locator('#saveButton').click()]);
    h.assert(saved.status()===200,'The owned prescription was not saved');
    const script=(await saved.json()).scriptId,view=await viewScriptFrame(rx);
    await view.locator('#additionalNotes').waitFor();
    await view.waitForFunction(()=>typeof signatureAssociationPending !== 'undefined' && !signatureAssociationPending && !signatureAssociationFailed);
    await s.step('early fax preserves state and notes save while the real preview is loading',async()=>{
      h.assert(intercepted,'The real preview was not held');
      const result=await view.evaluate(()=>{
        const select=document.getElementById('faxNumber');
        if(!select.options.length)select.add(new Option('Synthetic','0000000000'));
        const unload=()=>undefined;window.onbeforeunload=unload;
        return {refused:sendFax(false)===false,pending:faxSubmissionPending,unloadUnchanged:window.onbeforeunload===unload,warning:!document.getElementById('rxPreviewLoading').hidden};
      });
      h.assert(result.refused&&!result.pending&&result.unloadUnchanged&&result.warning,'Early fax changed state or lost its visible warning');
      await view.locator('#additionalNotes').fill(note);
      await view.locator('#saveAdditionalNotes').click();
      await view.evaluate(()=>pendingNotesSave);
      h.assert(s.sql.value(`SELECT rx_comments FROM prescription WHERE script_no=${Number(script)} AND demographic_no=${s.patient}`)===note,'Notes did not save independently of the loading preview');
      h.assert(await view.locator('#additionalNotesSaveError').isHidden(),'A confirmed notes save remained in error');
      // Consume only the existing Chromium empty-204 body artefact after durable acknowledgement.
      await consumeNoteBodies();
    });
    await s.context.unroute('**/rx/ViewPreview2?**',handler);
    await view.locator('#preview').evaluate(frame=>{frame.src=frame.src;});
    await view.frameLocator('#preview').locator('#preview2Form').waitFor();
    h.assert(await view.locator('#rxPreviewLoading').isHidden(),'The transient preview loading notice did not clear');
    await view.frameLocator('#preview').locator('#additNotes').filter({hasText:note}).waitFor();
    await rx.waitForLoadState('networkidle');
    await view.evaluate(()=>{window.onbeforeunload=null;});
  } finally { await s.context.unroute('**/rx/ViewPreview2?**',handler); }
}
if(require.main===module)runWorkflow('rx-preview-loading-notes',workflow);
module.exports={workflow};
