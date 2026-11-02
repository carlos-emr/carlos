#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h=require('./lib/playwright-harness'),ui=require('./lib/playwright-ui');
const {runWorkflow}=require('./lib/workflow-session');
async function workflow(s) {
  const {sql,patient,provider,marker}=s,q=h.sqlString,ids=[];
  s.cleanup(()=>{
    for(const id of ids)sql.execute(`DELETE FROM formAnnual WHERE ID=${id} AND demographic_no=${patient} AND currentConcerns LIKE ${q(marker+'%')}`);
    h.assert(!ids.length||sql.value(`SELECT COUNT(*) FROM formAnnual WHERE ID IN (${ids.join(',')})`)==='0','Owned forms remain');
  });
  for(let i=1;i<=11;i++){
    const id=sql.value(`INSERT INTO formAnnual(demographic_no,provider_no,formCreated,formEdited,currentConcerns) VALUES (${patient},${q(provider)},DATE_ADD('2026-01-01',INTERVAL ${i} DAY),DATE_ADD('2026-01-01 12:00:00',INTERVAL ${i} DAY),${q(marker+'-'+i)});SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id),'Owned Annual form was not inserted');ids.push(id);
  }
  const chart=await s.chart();let list;
  const shown=()=>list.locator('a[onclick*="formId"]').evaluateAll(nodes=>nodes.map(node=>/formId=(\d+)/.exec(node.getAttribute('onclick').replace(/\\x([0-9a-f]{2})/gi,(_,hex)=>String.fromCharCode(parseInt(hex,16))).replace(/\\u([0-9a-f]{4})/gi,(_,hex)=>String.fromCharCode(parseInt(hex,16))))?.[1]));
  await s.step('the chart Forms heading opens this patient’s started-form list',async()=>{
    list=await s.popup(chart,chart.locator('#forms a[onclick*="ViewFormlist"]').first(),'forms-list');
    await list.waitForLoadState('domcontentloaded');await h.assertNotErrorPage(list,'Forms list');
    h.assert(new URL(list.url()).searchParams.get('demographic_no')===patient,'Forms heading opened another patient');
    await list.locator('a[onclick*="formId"]').first().waitFor();
    const found=await shown();h.assert(found.length>0&&found.every(id=>ids.includes(id))&&new Set(found).size===found.length,'Forms list did not identify the independently seeded records');
  });

  await s.step('Forms heading paginates ten records, Next changes the rows and Previous restores them',async()=>{
    const expected=ids.slice().reverse(),first=await shown();
    h.assert(JSON.stringify(first)===JSON.stringify(expected.slice(0,10)),`First Forms page did not contain exactly the latest ten owned records (shown: ${first.length})`);
    await ui.clickAndAwaitReload(list,list.locator('a[href*="ViewFormlist"][href*="limit1=10&"]').first());
    h.assert(JSON.stringify(await shown())===JSON.stringify(expected.slice(10)),'Next did not show only the eleventh owned form');
    await ui.clickAndAwaitReload(list,list.locator('a[href*="ViewFormlist"][href*="limit1=0&"]').first());
    h.assert(JSON.stringify(await shown())===JSON.stringify(first),'Previous did not restore the first page');
  });
}
if(require.main===module)runWorkflow('echart-forms-list-paging',workflow,{openPatient:true});
module.exports={workflow};
