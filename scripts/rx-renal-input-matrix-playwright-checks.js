#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');
const {stageFromSearch} = require('./rx-edit-discontinue-playwright-checks');
const {masterRecordLink} = require('./lib/master-record-link');
const {CASES, renalInputFixture} = require('./lib/renal-input-fixture');
const {failureMark, consumeExpectedFailure} = require('./lib/concurrency-support');
async function consumeRenalRefusal(s,rx,response,mark,status=400) {
  await rx.waitForLoadState('networkidle');
  h.assert(s.recorder.badResponses.slice(mark.responses).every(entry=>entry.url===response.url()),
    'The expected renal refusal cannot account for another failing request');
  consumeExpectedFailure(s.recorder,mark,{status,method:'GET',path:/\/rx\/ViewRenalDosing$/});
}
async function workflow(s) {
  const name = process.env.RX_RENAL_CASE || 'male-valid';
  h.assert(Object.hasOwn(CASES,name),'Unknown RX_RENAL_CASE');
  const testCase = CASES[name], fixture = renalInputFixture(s);
  // Independent initial rendering must not depend on a working Refresh link.
  fixture.apply(testCase);
  const rx = await s.popup(s.master,masterRecordLink(s.master,'/rx/choosePatient').first(),'renal-matrix-rx');
  let card,panel,originalAtc;
  await s.step('renal inputs render through the native drug card without a server error',async()=>{
    const initialFailure=name==='male-valid';
    const mark=(initialFailure||testCase.status===400)?failureMark(s.recorder):null;
    if(initialFailure)await rx.route(url=>url.pathname.endsWith('/rx/ViewRenalDosing'),
      route=>route.fulfill({status:503,contentType:'text/html',body:'<p>Unexpected renal transport failure</p>'}),{times:1});
    const [response,staged]=await Promise.all([
      rx.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/rx/ViewRenalDosing')&&new URL(r.url()).searchParams.get('demographicNo')===s.patient),
      stageFromSearch(rx,'CIPRO 500','CIPRO 500MG'),
    ]);
    card=staged;panel=rx.locator(`#renalDosing_${card}`);originalAtc=new URL(response.url()).searchParams.get('atcCode');
    h.assert(originalAtc?.split(',').includes('J01MA02'),'The staged renal product is not ciprofloxacin (J01MA02)');
    h.assert(response.status()===(initialFailure?503:testCase.status||200),`Renal rendering returned HTTP ${response.status()}, expected ${initialFailure?503:testCase.status||200}`);await response.finished();
    if(initialFailure) {
      await panel.locator('[data-renal-dosing-unavailable]').waitFor({state:'visible'});
      h.assert(!(await panel.innerText()).includes('Unexpected renal transport failure'),'The renal panel displayed an error response body');
      await consumeRenalRefusal(s,rx,response,mark,503);
      const [retry]=await Promise.all([rx.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/rx/ViewRenalDosing')),
        panel.getByRole('button',{name:'Refresh',exact:true}).click()]);
      h.assert(retry.status()===200,'The initial renal failure could not be retried');await retry.finished();
    }
    await panel.locator('table.equation').waitFor({state:'visible'});
    if(mark&&!initialFailure)await consumeRenalRefusal(s,rx,response,mark);
  });
  await s.step('render independent clinical inputs and exact inclusive recommendation boundaries',async()=>{
    const text=(await panel.innerText()).replace(/\s+/g,' '), selected=panel.locator('table.sofT tr.selected');
    if(testCase.clearance===undefined) {
      h.assert(text.includes('N/A') && !/NaN|Infinity/.test(text),'Unusable renal input was displayed as a usable number');
      h.assert(await selected.count()===0,'Unusable renal input retained a selected recommendation');
    } else {
      h.assert(text.includes(`Clcr ${testCase.clearance} =`),'Displayed creatinine clearance differs from the independent expected value');
      const inRange=testCase.clearance>=5 && testCase.clearance<=30;
      h.assert(await selected.count()===(inRange?1:0),'The 5–30 inclusive ciprofloxacin recommendation boundary is wrong');
      if(inRange)h.assert((await selected.innerText()).includes('250-500 mg po q18h'),'Wrong renal dose was highlighted');
      if(testCase.source)h.assert(text.includes(testCase.source),'The displayed creatinine source date is wrong');
    }
  });
  if(name==='male-valid')await s.step('invalid refresh removes an existing dose recommendation and remains refreshable',async()=>{
    fixture.apply({weight:'40',cr:'200'});
    const refresh=()=>Promise.all([rx.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/rx/ViewRenalDosing')),
      panel.getByRole('button',{name:'Refresh',exact:true}).click()]);
    await refresh();
    await panel.locator('table.sofT tr.selected').waitFor({state:'visible'});
    fixture.apply({weight:'40',cr:'not-a-number',crDate:null,lab:'200',labDate:'03-Jan-25'});
    const mark=failureMark(s.recorder);
    const [refusal]=await refresh();
    h.assert(refusal.status()===400,'Invalid stored observation did not answer HTTP 400');await refusal.finished();
    await panel.getByRole('button',{name:'Refresh',exact:true}).waitFor({state:'visible'});
    await rx.waitForFunction(id=>document.querySelector(id)?.textContent.includes('N/A'),`#renalDosing_${card}`);
    h.assert(await panel.locator('table.equation').count()===1 && await panel.locator('tr.selected').count()===0,
      'Refused refresh retained a stale recommendation or appended a duplicate panel');
    await consumeRenalRefusal(s,rx,refusal,mark);
    const infantInput={age:0,weight:'80',cr:'100'};
    fixture.apply(infantInput);
    const [infant]=await refresh();
    h.assert(infant.status()===200,'An age below one year should render N/A without rejecting valid observations');await infant.finished();
    await rx.waitForFunction(({id,weight,cr})=>{
      const text=document.querySelector(`${id} table.equation`)?.textContent;
      return text?.includes(weight)&&text.includes(cr)&&text.includes('N/A');
    },{id:`#renalDosing_${card}`,weight:Number(infantInput.weight).toFixed(1),cr:`${Number(infantInput.cr).toFixed(1)} sCr`});
    h.assert(await panel.locator('tr.selected').count()===0,'An age below one year selected an adult dose');
  });
  if(name==='male-valid')await s.step('incomplete and malformed birth dates refuse dosing without a server error',async()=>{
    fixture.apply({weight:'40',cr:'200'});
    const refresh=()=>Promise.all([rx.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/rx/ViewRenalDosing')),
      panel.getByRole('button',{name:'Refresh',exact:true}).click()]);
    await refresh();
    await panel.locator('tr.selected').waitFor({state:'visible'});
    for(const birthDate of [['','01','01'],['1966','','01'],['1966','01',''],
      ['text','01','01'],['1966','xx','01'],['1966','01','xx'],['2025','02','29']]) {
      s.sql.execute('UPDATE demographic SET year_of_birth=?,month_of_birth=?,date_of_birth=? WHERE demographic_no=?',
        [...birthDate,s.patient]);
      const mark=failureMark(s.recorder),[response]=await refresh();
      h.assert(response.status()===400,'An incomplete or malformed birth date did not answer HTTP 400');await response.finished();
      await rx.waitForFunction(id=>document.querySelector(`${id} table.equation`)?.textContent.includes('N/A'),`#renalDosing_${card}`);
      h.assert(await panel.locator('tr.selected').count()===0,'An unusable birth date retained a dose recommendation');
      await consumeRenalRefusal(s,rx,response,mark);
    }
  });
  await s.step('restoring valid observations refreshes the same panel without retaining stale input',async()=>{
    fixture.apply({...testCase,weight:'80',cr:'100',crDate:'2025-01-02',lab:undefined});
    const mark=testCase.age<0?failureMark(s.recorder):null;
    const [response]=await Promise.all([rx.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/rx/ViewRenalDosing')),
      panel.getByRole('button',{name:'Refresh',exact:true}).click()]);
    h.assert(response.status()===(testCase.age<0?400:200),'Restoring renal observations returned an unexpected status');await response.finished();
    const parameters=new URL(response.url()).searchParams;
    h.assert(parameters.get('demographicNo')===s.patient&&parameters.get('atcCode')===originalAtc&&parameters.get('divId')===`renalDosing_${card}`,
      'Refresh lost the originating patient, controlled drug or panel identity');
    const age=testCase.age??60;
    const clearance=age>0?Math.round((140-age)*80/(100*0.8)*(testCase.sex==='F'?0.85:1)):undefined;
    await rx.waitForFunction(({id,token})=>document.querySelector(id)?.textContent.includes(token),
      {id:`#renalDosing_${card}`,token:clearance===undefined?'N/A':`Clcr ${clearance} =`});
    const refreshedEquation=(await panel.locator('table.equation').innerText()).replace(/\s+/g,' ');
    for(const token of ['80.0','100.0 sCr','2025-Jan-02']) {
      h.assert(refreshedEquation.includes(token),`Refreshed renal equation did not reread ${token}`);
    }
    h.assert(await panel.locator('table.sofT tr.selected').count()===0,'Restored valid high clearance retained the previous recommendation');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${s.patient}`)==='0','Input checking unexpectedly saved a prescription');
    if(mark)await consumeRenalRefusal(s,rx,response,mark);
  });
}
if(require.main===module)runWorkflow('rx-renal-input-matrix',workflow,{openPatient:true});
module.exports={workflow};
