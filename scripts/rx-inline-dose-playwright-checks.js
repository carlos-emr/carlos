#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Rx > custom card > structured editable links / PRN > blur the changed field > Save Only.
// Existing parsing checks type a complete instruction string; these exercise the
// clinician's individual dose controls and their updateProperty handoff.
const h=require('./lib/playwright-harness');
const {runWorkflow,expectValue}=require('./lib/workflow-session');
const {prepareRx,saveOnly}=require('./lib/rx-ui-workflow');
const {stageCustomDrug}=require('./rx-stash-patient-isolation-playwright-checks');
const {failureMark,consumeExpectedFailure}=require('./lib/concurrency-support');
const prn=process.env.RX_INLINE_ACTION==='prn';
const invalid=['minimum','maximum'].includes(process.env.RX_INLINE_ACTION)?process.env.RX_INLINE_ACTION:null;
const name=invalid?`rx-inline-${invalid}-validation`:prn?'rx-inline-prn-toggle':'rx-inline-structured-dose-save';
async function workflow(s){
  const {sql,patient,marker}=s,q=h.sqlString,f=await prepareRx(s),control=f.seed('control'),rx=await f.open();
  const key=await stageCustomDrug(rx,`${marker} inline`);
  await rx.locator(`#instructions_${key}`).fill(prn?'1 tab PO OD PRN x 30 days':'1 tab PO OD x 30 days');
  const [parsed]=await Promise.all([
    rx.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/rx/UpdateScript')),
    rx.locator(`label[for="jsonDxSearch_${key}"]`).click(),
  ]);
  h.assert(parsed.status()===200,'Initial card instructions were not parsed');
  await rx.waitForLoadState('networkidle');
  const property=(r,field)=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/rx/WriteScript')
    &&new URL(r.url()).searchParams.get('parameterValue')==='updateProperty'
    &&new URLSearchParams(r.request().postData()).get('elementId')===`${field}_${key}`;
  if(invalid){
    await s.step('an invalid numeric dose is refused without losing the card or changing a medication',async()=>{
      const el=rx.locator(`#${invalid}_${key}`);
      await rx.locator(`a[onclick="focusTo('${invalid}_${key}')"]`).click();await el.fill('not-a-number');
      const mark=failureMark(s.recorder);
      const [response]=await Promise.all([rx.waitForResponse(r=>property(r,invalid)),el.blur()]);
      h.assert([400,422].includes(response.status()),`Invalid dose answered HTTP ${response.status()} instead of a validation refusal`);
      await rx.waitForLoadState('networkidle');
      consumeExpectedFailure(s.recorder,mark,{status:response.status(),path:/\/rx\/WriteScript$/});
      h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`)==='1','Invalid dose saved a medication');
      h.assert(await rx.locator(`#drugName_${key}`).isVisible(),'Invalid dose lost the draft');
      h.assert((await rx.locator('[role="alert"],.alert-danger,.rx-error').allInnerTexts()).some(x=>/dose|number|numeric|valid/i.test(x)),'Invalid dose showed no correction guidance');
      await rx.locator(`a[onclick="focusTo('${invalid}_${key}')"]`).click();await el.fill('1');
      const [fixed]=await Promise.all([rx.waitForResponse(r=>property(r,invalid)),el.blur()]);
      h.assert(fixed.status()===200,'Corrected dose was refused');
      await saveOnly(rx);
      await expectValue(sql,`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`,'2','Corrected dose did not save once');
      h.assert(sql.value(`SELECT archived FROM drugs WHERE drugid=${control}`)==='0','Dose correction archived the control');
    });
    return;
  }
  if(prn){
    await s.step('PRN toggles off and on through its protected property update before saving off',async()=>{
      for(const state of ['false','true','false']){
        const [response]=await Promise.all([rx.waitForResponse(r=>property(r,'prnVal')),rx.locator(`#prn_${key}`).click()]);
        h.assert(response.status()===200,'PRN update was refused');
        h.assert(await rx.locator(`#prnVal_${key}`).inputValue()===state,'PRN state did not toggle');
      }
    });
  }else{
    await s.step('each editable dose link saves its changed value when the clinician leaves the field',async()=>{
      const sent=[];
      const watch=request=>{if(request.method()==='POST'&&new URL(request.url()).searchParams.get('parameterValue')==='updateProperty')sent.push(new URLSearchParams(request.postData()));};
      rx.on('request',watch);
      try{
      for(const [field,value]of Object.entries({method:'mL',route:'NG',frequency:'BID',minimum:'0.5',maximum:'1',duration:'2',durationUnit:'W'})){
        const el=rx.locator(`#${field}_${key}`);
        // The value can be empty; its visible label activates that editable
        // anchor without requiring a click on a zero-width text node.
        await rx.locator(`a[onclick="focusTo('${field}_${key}')"]`).click();await el.fill(value);await el.blur();
      }
      await rx.waitForLoadState('networkidle');
      for(const [field,value]of Object.entries({method:'mL',route:'NG',frequency:'BID',minimum:'0.5',maximum:'1',duration:'2',durationUnit:'W'})){
        const posted=sent.filter(p=>p.get('elementId')===`${field}_${key}`).at(-1);
        h.assert(posted&&posted.get('propertyValue')===value,`Blur did not send the edited ${field}; DOM: ${JSON.stringify(await rx.locator(`#${field}_${key}`).evaluate(el=>({value:el.textContent,blur:el.getAttribute('onblur'),focused:document.activeElement===el})))}`);
      }
      }finally{rx.off('request',watch);}
    });
  }
  await s.step(prn?'Save Only persists the clinician-selected PRN flag on this medication alone'
    :'Save Only persists the structured dose without changing the existing medication',async()=>{
    await saveOnly(rx);
    await expectValue(sql,`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`,'2','Inline editing did not save exactly one new medication');
    const id=sql.value(`SELECT drugid FROM drugs WHERE demographic_no=${patient} AND customName=${q(marker+' inline')}`);
    h.assert(/^[1-9]\d*$/.test(id),'Inline medication was not saved');
    if(prn)h.assert(sql.value(`SELECT prn FROM drugs WHERE drugid=${id}`)==='0','Selected PRN flag was not saved');
    else{
      const row=sql.rows(`SELECT method,route,freqcode,takemin,takemax,duration,durunit FROM drugs WHERE drugid=${id}`)[0];
      h.assert(JSON.stringify(row)===JSON.stringify(['mL','NG','BID','0.5','1','2','W']),`Structured dose was not saved: ${JSON.stringify(row)}`);
    }
    h.assert(sql.value(`SELECT CONCAT(archived,'|',special) FROM drugs WHERE drugid=${control}`)==='0|1 tab PO OD x 30 days','Editing the new card altered the control medication');
  });
}
if(require.main===module)runWorkflow(name,workflow);
module.exports={workflow};
