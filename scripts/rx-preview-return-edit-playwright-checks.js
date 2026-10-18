#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Save And Print > dismiss using X/Escape > correct the acknowledged saved
// card > Save Only. Exercise a full edit after dismissal, including active-medication identity.
const h=require('./lib/playwright-harness');
const {runWorkflow,expectValue}=require('./lib/workflow-session');
const {prepareRx,saveOnly}=require('./lib/rx-ui-workflow');
const {stageCustomDrug}=require('./rx-stash-patient-isolation-playwright-checks');
const {viewScriptFrame}=require('./rx-drug-name-quotes-playwright-checks');
const escape=process.env.RX_PREVIEW_RETURN==='escape';
const name=escape?'rx-preview-escape-return-edit':'rx-preview-close-return-edit';
async function workflow(s){
  const {sql,patient,marker}=s,f=await prepareRx(s),rx=await f.open(),key=await stageCustomDrug(rx,marker+' preview edit');
  async function instructions(text){
    await rx.locator(`#instructions_${key}`).fill(text);
    await Promise.all([rx.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/rx/UpdateScript')),rx.locator(`label[for="jsonDxSearch_${key}"]`).click()]);
  }
  await instructions('1 tab PO OD x 30 days');
  await rx.locator('#saveButton').click();const script=await viewScriptFrame(rx);
  await script.frameLocator('#preview').locator('#signature').waitFor({state:'attached'});
  await s.step('dismissing the preview retains one acknowledged saved card without archiving the medication',async()=>{
    if(escape)await rx.keyboard.press('Escape');else await rx.locator('#carlosModal .modal-header button[data-bs-dismiss="modal"]').click();
    await rx.locator('#carlosModal').waitFor({state:'hidden'});
    h.assert(await rx.locator(`#drugName_${key}`).isVisible(),'Preview dismissal lost the editable card');
    h.assert(sql.value(`SELECT CONCAT(COUNT(*),'|',MAX(archived)) FROM drugs WHERE demographic_no=${patient}`)==='1|0','Preview dismissal archived or duplicated the medication');
  });
  await s.step('correcting the saved card after dismissal updates that medication once and clears the stage',async()=>{
    await instructions('1 tab PO BID x 14 days');await saveOnly(rx);
    await expectValue(sql,`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient} AND freqcode='BID' AND duration='14' AND archived=0`,'1','Correction was not saved');
    const rows=sql.rows(`SELECT drugid,freqcode,duration,archived FROM drugs WHERE demographic_no=${patient} ORDER BY drugid`);
    h.assert(rows.filter(row=>row[3]==='0').length===1,`Correction left competing active prescriptions: ${JSON.stringify(rows)}`);
    await rx.locator(`#set_${key}`).waitFor({state:'detached'});
  });
}
if(require.main===module)runWorkflow(name,workflow);
module.exports={workflow};
