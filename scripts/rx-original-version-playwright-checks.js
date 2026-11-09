#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h=require('./lib/playwright-harness');
const {runWorkflow}=require('./lib/workflow-session');
const {failureMark,consumeExpectedFailure}=require('./lib/concurrency-support');
async function workflow(s) {
  const {sql,provider,patient,marker,config}=s,q=h.sqlString;
  const script=h.insertId(sql,`INSERT INTO prescription(provider_no,demographic_no,date_prescribed,rx_comments) VALUES(${q(provider)},${patient},CURDATE(),'Original')`,'owned prescription');
  s.cleanup(()=>sql.execute(`DELETE FROM prescription WHERE script_no=${script} AND demographic_no=${patient}`));
  const drug=h.insertId(sql,`INSERT INTO drugs(provider_no,demographic_no,customName,special,script_no,drug_form,position,archived) VALUES(${q(provider)},${patient},${q(marker+' version')},'1 tab PO OD',${script},'Tablet',1,0)`,'owned drug');
  s.cleanup(()=>sql.execute(`DELETE FROM drugs WHERE drugid=${drug} AND demographic_no=${patient} AND customName=${q(marker+' version')}`));
  const first=await s.context.newPage(),second=await s.context.newPage();
  const url=`${config.baseUrl}/rx/ViewUpdateForm?id=${drug}`;
  await first.goto(url);await second.goto(url);
  await s.step('two native editors retain the original version and refuse the stale second form edit',async()=>{
    const original=await second.locator('input[name="originalVersion"]').inputValue();
    h.assert(original===await first.locator('input[name="originalVersion"]').inputValue(),'Editors did not start from the same version');
    await first.locator('select[name="drugForm"]').selectOption('Capsule');
    await first.getByRole('button',{name:'submit',exact:true}).click();
    await first.waitForLoadState('networkidle');
    h.assert(sql.value(`SELECT drug_form FROM drugs WHERE drugid=${drug}`)==='Capsule','The first edit was not saved');
    await second.locator('select[name="drugForm"]').selectOption('Powder');
    const mark=failureMark(s.recorder);
    const [refused]=await Promise.all([second.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/rx/ViewUpdateForm')),second.getByRole('button',{name:'submit',exact:true}).click()]);
    h.assert(refused.status()===409,'A stale editor overwrote the first edit');
    await second.getByRole('alert').filter({hasText:'changed after you opened it'}).waitFor();
    await second.waitForLoadState('networkidle');
    consumeExpectedFailure(s.recorder,mark,{status:409,method:'POST',path:/\/rx\/ViewUpdateForm$/});
    h.assert(await second.locator('select[name="drugForm"]').inputValue()==='Powder','The conflict lost the second editor\'s draft');
    h.assert(sql.value(`SELECT drug_form FROM drugs WHERE drugid=${drug}`)==='Capsule','The stale edit changed the clinical row');
  });
  await s.step('comment writes compare original text, acknowledge exact retries and preserve another window\'s notes',async()=>{
    const csrf=await first.locator('input[name="CSRF-TOKEN"]').first().inputValue();
    const endpoint=`${config.baseUrl}/rx/ViewAddRxComment`;
    const post=comment=>s.context.request.post(endpoint,{form:{scriptNo:script,comment,originalComment:'Original'},headers:{'CSRF-TOKEN':csrf,'X-Requested-With':'XMLHttpRequest'}});
    const saved=await post('First notes');h.assert(saved.status()===204&&saved.headers()['x-carlos-rx-comment']==='written','Notes were not acknowledged');
    const stale=await post('Stale notes');h.assert(stale.status()===409,'Stale notes were not refused');
    h.assert(sql.value(`SELECT rx_comments FROM prescription WHERE script_no=${script}`)==='First notes','Stale notes overwrote the first edit');
    const replay=await post('First notes');h.assert(replay.status()===204,'An exact confirmed notes retry was not idempotent');
  });
  await s.step('legacy REST responses keep their shape and opted-in versions reject a stale update',async()=>{
    const endpoint=`${config.baseUrl}/ws/rs/rx/drugs/all/${patient}`;
    const legacy=await s.context.request.get(endpoint,{headers:{Accept:'application/json'}});
    h.assert(legacy.status()===200,'The legacy REST list failed');
    const old=(await legacy.json()).content.find(row=>String(row.drugId)===String(drug));
    h.assert(old&&!Object.hasOwn(old,'rowVersion'),'Legacy REST unexpectedly added a version element');
    const versioned=await s.context.request.get(endpoint+'?includeRowVersion=true',{headers:{Accept:'application/json'}});
    h.assert(versioned.status()===200,'The version-aware REST list failed');
    const original=(await versioned.json()).content.find(row=>String(row.drugId)===String(drug));
    h.assert(Number.isSafeInteger(original.rowVersion),'The requested original version was omitted');
    const csrf=await first.locator('input[name="CSRF-TOKEN"]').first().inputValue();
    const url=`${config.baseUrl}/ws/rs/rx/updateDrug?demographicNo=${patient}&includeRowVersion=true`;
    const post=form=>s.context.request.post(url,{data:{...original,form},headers:{Accept:'application/json','CSRF-TOKEN':csrf,'X-Requested-With':'XMLHttpRequest'}});
    const saved=await post('Cream');
    h.assert(saved.status()===200&&(await saved.json()).success===true,'The version-aware update was not saved');
    const refused=await post('Powder');
    h.assert(refused.status()===409,'A stale REST version overwrote the saved medication');
    h.assert(s.sql.value(`SELECT drug_form FROM drugs WHERE drugid=${drug}`)==='Cream','The stale REST update changed the clinical row');
  });
}
if(require.main===module)runWorkflow('rx-original-version',workflow,{openPatient:true});
module.exports={workflow};
