#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Rx profile > Discon > choose a reason/comment > Cancel or Discontinue.
// Cover the cancellation with an independent staged draft, then every offered
// reason code with owned drugs and one untouched clinical control.
const h=require('./lib/playwright-harness');
const {runWorkflow,expectValue}=require('./lib/workflow-session');
const {prepareRx}=require('./lib/rx-ui-workflow');
const {stageCustomDrug}=require('./rx-stash-patient-isolation-playwright-checks');
const all=process.env.RX_DISCONTINUE_ACTION==='reasons';
const name=all?'rx-discontinue-reason-options':'rx-discontinue-cancel-preserves-draft';
const reasons=['adverseReaction','allergy','cost','discontinuedByAnotherPhysician','doseChange','drugInteraction',
  'increasedRiskBenefitRatio','ineffectiveTreatment','newScientificEvidence','noLongerNecessary','enteredInError',
  'patientRequest','prescribingError','simplifyingTreatment','unknown','other'];
async function workflow(s){
  const {sql,patient,marker}=s,f=await prepareRx(s),control=f.seed('control');
  const ids=(all?reasons:['other']).map((code,i)=>f.seed('stop '+i));
  const rx=await f.open(),before=JSON.stringify(sql.rows(`SELECT * FROM drugs WHERE drugid=${control}`));
  const panel=rx.locator('#discontinueUI');
  if(!all){
    const key=await stageCustomDrug(rx,marker+' unfinished');
    await rx.locator(`#instructions_${key}`).fill(marker+' unfinished instructions');
    await s.step('Cancel on Discontinue preserves the current medication and another staged draft',async()=>{
      await rx.locator(`#discont_${ids[0]}`).click();await panel.waitFor({state:'visible'});
      await rx.locator('#disReason').selectOption('other');await rx.locator('#disComment').fill(marker+' not submitted');
      await panel.locator('input[onclick*="style.display="]').click();await panel.waitFor({state:'hidden'});
      h.assert(sql.value(`SELECT archived FROM drugs WHERE drugid=${ids[0]}`)==='0','Cancelled discontinuation archived the medication');
      h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}`)==='0','Cancelled discontinuation wrote a chart note');
      h.assert(await rx.locator(`#instructions_${key}`).inputValue()===marker+' unfinished instructions','Cancelled discontinuation discarded another draft');
      h.assert(JSON.stringify(sql.rows(`SELECT * FROM drugs WHERE drugid=${control}`))===before,'Cancelled discontinuation changed the control drug');
    });
  }else{
    await s.step('each offered discontinue reason archives exactly its medication and retains the clinical end date',async()=>{
      for(const [i,code]of reasons.entries()){
        await rx.locator(`#discont_${ids[i]}`).click();await panel.waitFor({state:'visible'});
        h.assert(JSON.stringify(await rx.locator('#disReason option').evaluateAll(nodes=>nodes.map(x=>x.value)))===JSON.stringify(reasons),'The reason options changed without coverage review');
        await rx.locator('#disReason').selectOption(code);await rx.locator('#disComment').fill(marker+' '+code);
        const [response]=await Promise.all([
          rx.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/rx/deleteRx')),
          panel.locator('input[onclick^="Discontinue2("]').click(),
        ]);
        h.assert(response.status()===200,'Discontinuation was refused');
        await expectValue(sql,`SELECT archived_reason FROM drugs WHERE drugid=${ids[i]}`,code,'Discontinuation stored another clinical reason');
        h.assert(sql.value(`SELECT CONCAT(archived,'|',DATE(end_date)) FROM drugs WHERE drugid=${ids[i]}`)===`1|${f.end}`,'Discontinuation altered the clinical end date');
        await rx.waitForFunction(({id,code})=>document.getElementById('discont_'+id)?.textContent.trim()===code,{id:ids[i],code});
      }
      h.assert(JSON.stringify(sql.rows(`SELECT * FROM drugs WHERE drugid=${control}`))===before,'Discontinuation changed the unselected control');
      h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}`)===String(reasons.length),'Discontinuation did not file one chart note per medication');
    });
  }
}
if(require.main===module)runWorkflow(name,workflow);
module.exports={workflow};
