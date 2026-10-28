#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Double click the actual final Save control. The UI and committed data must
// agree: one medication, one prescription, no stranded draft or duplicate print.
const h=require('./lib/playwright-harness');
const {runWorkflow,expectValue}=require('./lib/workflow-session');
const {prepareRx}=require('./lib/rx-ui-workflow');
const {stageCustomDrug}=require('./rx-stash-patient-isolation-playwright-checks');
const print=process.env.RX_DOUBLE_SAVE_ACTION==='print';
const name=print?'rx-double-save-print':'rx-double-save-only';
async function workflow(s){
  const {sql,patient,marker}=s,f=await prepareRx(s),rx=await f.open(),key=await stageCustomDrug(rx,marker+' double');
  await rx.locator(`#instructions_${key}`).fill('1 tab PO OD x 30 days');
  await Promise.all([rx.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/rx/UpdateScript')),rx.locator(`label[for="jsonDxSearch_${key}"]`).click()]);
  await s.step('a real double click saves exactly one medication and one prescription without an error',async()=>{
    const [response]=await Promise.all([
      rx.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/rx/WriteScript')&&new URL(r.url()).searchParams.get('parameterValue')==='updateSaveAllDrugs'),
      rx.locator(print?'#saveButton':'#saveOnlyButton').dblclick({delay:40}),
    ]);
    h.assert(response.status()===200,'Double-click save was refused');
    await rx.waitForLoadState('networkidle');
    await expectValue(sql,`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`,'1','Double click saved other than one medication');
    h.assert(sql.value(`SELECT COUNT(*) FROM prescription WHERE demographic_no=${patient}`)==='1','Double click duplicated the prescription');
    h.assert(await rx.locator('[id^="drugName_"]').count()===(print?1:0),'Double click left an unexpected number of cards');
    if(print){
      const frames=rx.frames().filter(f=>new URL(f.url()==='about:blank'?'https://invalid/':f.url()).pathname.endsWith('/rx/viewScript'));
      h.assert(frames.length===1,'Double click opened other than one print window');
      const script=new URL(frames[0].url()).searchParams.get('scriptId');
      h.assert(sql.value(`SELECT COUNT(*) FROM prescription WHERE demographic_no=${patient} AND script_no=${Number(script)}`)==='1','Print window names another prescription');
      const preview=rx.frameLocator('#carlosModalBody iframe');
      await preview.locator('input[onclick="resetStash();"]').click();
      await rx.locator('#carlosModal').waitFor({state:'hidden'});
      h.assert(await rx.locator('[id^="drugName_"]').count()===0,'Create New Rx retained the saved card');
      h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`)==='1','Finalization saved the medication twice');
    }
  });
}
if(require.main===module)runWorkflow(name,workflow);
module.exports={workflow};
