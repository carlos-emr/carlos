#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Edit a custom card's name, or cancel then accept converting a DrugRef product
// into a custom medication. Save and verify the identity on the patient profile.
const h=require('./lib/playwright-harness');
const {runWorkflow,expectValue}=require('./lib/workflow-session');
const {prepareRx,saveOnly}=require('./lib/rx-ui-workflow');
const {stageCustomDrug}=require('./rx-stash-patient-isolation-playwright-checks');
const {stageFromSearch}=require('./rx-edit-discontinue-playwright-checks');
const product=process.env.RX_RENAME_ACTION==='product';
const name=product?'rx-product-name-change-confirmation':'rx-custom-name-edit';
async function workflow(s){
  const {sql,patient,marker}=s,q=h.sqlString,f=await prepareRx(s),rx=await f.open();
  let key=product?await stageFromSearch(rx,'LIPITOR 20','LIPITOR 20MG'):await stageCustomDrug(rx,marker+' original');
  const changed=marker+" O'Neil renamed";
  await s.step(product?'declining a product name change restores the DrugRef product without saving'
    :'editing the custom medication name updates its existing draft card',async()=>{
    const field=rx.locator(`#drugName_${key}`),original=await field.inputValue();
    if(product){
      const dialogs=await h.withExpectedDialogs(rx,async()=>{await field.fill(changed);await field.blur();},{accept:false});
      h.assert(dialogs.length===1&&dialogs[0].type==='confirm','Product rename did not ask for confirmation');
      h.assert(await field.inputValue()===original,'Declined product rename did not restore the original name');
    }else{
      await field.fill('');
      const [r]=await Promise.all([
        rx.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/rx/WriteScript')&&new URLSearchParams(r.request().postData()||'').get('parameterValue')==='saveCustomName'&&new URLSearchParams(r.request().postData()).get('customName')===changed),
        field.pressSequentially(changed),
      ]);
      h.assert(r.status()===200,'Custom rename was refused');await rx.waitForLoadState('networkidle');
      h.assert(await rx.locator('[id^="drugName_"]').count()===1,'Custom rename duplicated the card');
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`)==='0','Name editing saved an unintended medication');
  });
  await s.step('the accepted name is saved once and appears on the patient medication profile',async()=>{
    if(product){
      const field=rx.locator(`#drugName_${key}`);
      await h.withExpectedDialogs(rx,async()=>{
        const [r]=await Promise.all([
          rx.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/rx/WriteScript')&&new URLSearchParams(r.request().postData()||'').get('parameterValue')==='normalDrugSetCustom'),
          field.fill(changed).then(()=>field.blur()),
        ]);
        h.assert(r.status()===200,'Accepted product conversion was refused');
      },{accept:true});
      await rx.waitForFunction(value=>[...document.querySelectorAll('[id^="drugName_"]')].some(x=>x.value===value),changed);
      key=(await rx.locator('[id^="drugName_"]').evaluateAll((nodes,value)=>nodes.filter(x=>x.value===value).map(x=>x.id.split('_')[1]),changed))[0];
    }
    await rx.locator(`#instructions_${key}`).fill('1 tab PO OD x 30 days');
    await Promise.all([rx.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/rx/UpdateScript')),rx.locator(`label[for="jsonDxSearch_${key}"]`).click()]);
    await saveOnly(rx);
    await expectValue(sql,`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient} AND customName=${q(changed)}`,'1','Accepted medication name was not saved exactly');
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`)==='1','Renaming saved a duplicate medication');
    const id=sql.value(`SELECT drugid FROM drugs WHERE demographic_no=${patient}`);
    await rx.locator(`#prescrip_${id}`).waitFor({state:'visible'});
    h.assert((await rx.locator(`#prescrip_${id}`).locator('xpath=ancestor::tr[1]').innerText()).includes(changed),'Profile lost the accepted medication name');
  });
}
if(require.main===module)runWorkflow(name,workflow);
module.exports={workflow};
