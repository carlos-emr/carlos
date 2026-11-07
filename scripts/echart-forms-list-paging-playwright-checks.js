#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h=require('./lib/playwright-harness'),ui=require('./lib/playwright-ui');
const {runWorkflow}=require('./lib/workflow-session');
async function workflow(s) {
  const {sql,patient,provider,marker}=s,q=h.sqlString,ids=[],cesdIds=[],nullableIds=[];
  const nullableName=marker+' null',nullableValue='../form/pharmaForms/formBPMH.jsp?demographic_no=&fixture='+marker;
  s.cleanup(()=>{
    for(const id of ids)sql.execute(`DELETE FROM formAnnual WHERE ID=${id} AND demographic_no=${patient} AND currentConcerns LIKE ${q(marker+'%')}`);
    for(const id of cesdIds)sql.execute(`DELETE FROM formCESD WHERE ID=${id} AND demographic_no=${patient} AND studyID=${q(marker.slice(-16))}`);
    for(const id of nullableIds)sql.execute(`DELETE FROM formBPMH WHERE id=${id} AND demographic_no=${patient} AND note=${q(marker)}`);
    h.assert(!ids.length||sql.value(`SELECT COUNT(*) FROM formAnnual WHERE ID IN (${ids.join(',')})`)==='0','Owned forms remain');
    h.assert(!cesdIds.length||sql.value(`SELECT COUNT(*) FROM formCESD WHERE ID IN (${cesdIds.join(',')})`)==='0','Owned CESD forms remain');
    h.assert(!nullableIds.length||sql.value(`SELECT COUNT(*) FROM formBPMH WHERE id IN (${nullableIds.join(',')})`)==='0','Owned nullable forms remain');
    sql.execute(`DELETE FROM encounterForm WHERE form_value=${q(nullableValue)} AND form_name=${q(nullableName)} AND form_table='formBPMH'`);
    h.assert(sql.value(`SELECT COUNT(*) FROM encounterForm WHERE form_value=${q(nullableValue)}`)==='0','Owned form registration remains');
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

  // Read the rendered rows, including type: IDs can collide between form tables.
  const rows=()=>list.locator('a[onclick*="formId"]').evaluateAll(nodes=>nodes.map(node=>{
    const onclick=node.getAttribute('onclick')
      .replace(/\\x([0-9a-f]{2})/gi,(_,hex)=>String.fromCharCode(parseInt(hex,16)))
      .replace(/\\u([0-9a-f]{4})/gi,(_,hex)=>String.fromCharCode(parseInt(hex,16)));
    const cells=node.closest('tr').querySelectorAll('td');
    return {id:/formId=(\d+)/.exec(onclick)?.[1],
      name:decodeURIComponent(/formname=([^&']*)/.exec(onclick)?.[1]||''),
      current:node.textContent.includes('(current)'),yellow:node.closest('tr').getAttribute('bgcolor')==='yellow',
      created:cells[1].textContent.trim(),edited:cells[2].textContent.trim()};
  }));
  const rowKeys=found=>found.map(row=>row.name+':'+row.id);
  const navigate=async(params={})=>{
    const url=new URL(list.url());
    url.search='';url.searchParams.set('demographic_no',patient);
    for(const [key,value] of Object.entries(params))url.searchParams.set(key,value);
    const response=await list.goto(url.href);
    h.assert(response?.status()===200,'Forms list navigation did not return HTTP 200');
    await h.assertNotErrorPage(list,'Forms list');
  };
  const navigation=()=>list.locator('a[href*="ViewFormlist"][href*="limit1="]');
  const assertContext=async(expectedOffsets,expectedProvider)=>{
    const links=await navigation().evaluateAll(nodes=>nodes.map(node=>node.href));
    h.assert(links.length===expectedOffsets.length,'Forms list has missing or duplicate navigation links');
    links.forEach((href,index)=>{
      const params=new URL(href).searchParams;
      h.assert(params.get('limit1')===String(expectedOffsets[index])&&params.get('limit2')==='10','Paging link has incorrect bounds');
      h.assert(params.get('demographic_no')===patient&&params.get('provider_no')===expectedProvider,'Paging lost patient or provider context');
    });
  };

  await s.step('one page spans form types and preserves the current highlight across the page boundary',async()=>{
    // Seven Annual rows followed by six CESD rows; the third and fourth CESD
    // rows share their created date, straddling the page boundary.
    for(const id of ids.slice(0,4))sql.execute(`DELETE FROM formAnnual WHERE ID=${id} AND demographic_no=${patient} AND currentConcerns LIKE ${q(marker+'%')}`);
    const dates=['2026-02-06','2026-02-05','2026-02-04','2026-02-04','2026-02-02','2026-02-01'];
    for(const date of dates){
      const id=sql.value(`INSERT INTO formCESD(demographic_no,provider_no,formCreated,formEdited,studyID) VALUES (${patient},${q(provider)},${q(date)},${q(date+' 12:00:00')},${q(marker.slice(-16))});SELECT LAST_INSERT_ID()`);
      h.assert(/^[1-9]\d*$/.test(id),'Owned CESD form was not inserted');cesdIds.push(id);
    }
    const expected=[...ids.slice(4).reverse().map(id=>'Annual:'+id),...[0,1,3,2,4,5].map(index=>'CESD:'+cesdIds[index])];
    await navigate({provider_no:provider});
    h.assert(JSON.stringify(rowKeys(await rows()))===JSON.stringify(expected.slice(0,10)),'First page did not span Annual and CESD forms in display order');
    await assertContext([10],provider);
    await ui.clickAndAwaitReload(list,navigation().first());
    const second=await rows();
    h.assert(JSON.stringify(rowKeys(second))===JSON.stringify(expected.slice(10)),'Second page omitted, duplicated or reordered cross-type rows');
    h.assert(second[0].current===false&&second[0].yellow===false,'A tied date at the page boundary was incorrectly marked current');
    h.assert(second[1].current&&second[1].yellow,'A different created date lost the current highlight');
    await assertContext([0],provider);
    await ui.clickAndAwaitReload(list,navigation().first());
    h.assert(JSON.stringify(rowKeys(await rows()))===JSON.stringify(expected.slice(0,10)),'Previous did not restore the cross-type page');
  });

  await s.step('invalid and stale paging parameters retain all rows and encode provider context',async()=>{
    const expectedFirst=rowKeys(await rows());
    for(const params of [{limit1:'abc',limit2:'x'},{limit1:'-1',limit2:'0'},{limit1:'99999999999',limit2:'99999999999'}]){
      await navigate(params);
      h.assert(JSON.stringify(rowKeys(await rows()))===JSON.stringify(expectedFirst),'Invalid limits did not fall back to the first ten rows');
      await assertContext([10],null);
    }
    await navigate({limit1:'500'});
    h.assert((await rows()).length===3,'A stale offset did not move to the final page');
    await assertContext([0],null);
    const encodedProvider=provider+' &extra=one+two#section';
    await navigate({provider_no:encodedProvider});
    await assertContext([10],encodedProvider);
    await ui.clickAndAwaitReload(list,navigation().first());
    await assertContext([0],encodedProvider);
    h.assert(new URL(list.url()).searchParams.get('provider_no')===encodedProvider,'Encoded provider did not round-trip through Next');
  });

  await s.step('nullable created and edited dates render blank without losing rows or changing their order',async()=>{
    // Own the registration too; ON does not register the shipped BPMH form.
    sql.execute(`INSERT INTO encounterForm(form_name,form_value,form_table,hidden) VALUES (${q(nullableName)},${q(nullableValue)},'formBPMH',0)`);
    for(const [created,edited] of [[null,'2026-03-05'],[null,'2026-03-04'],['2026-03-03',null],[null,null]]){
      const id=sql.value(`INSERT INTO formBPMH(demographic_no,provider_no,formCreated,formEdited,note) VALUES (${patient},${q(provider)},${created===null?'NULL':q(created)},${edited===null?'NULL':q(edited)},${q(marker)});SELECT LAST_INSERT_ID()`);
      h.assert(/^[1-9]\d*$/.test(id),'Owned nullable form was not inserted');nullableIds.push(id);
    }
    // Some provinces also register BPMH under their own name. Use one full
    // window here so those aliases cannot push the owned registration off-page.
    await navigate({limit2:'100'});
    const found=(await rows()).filter(row=>row.name===nullableName);
    h.assert(JSON.stringify(found.map(row=>row.id))===JSON.stringify(nullableIds),'Nullable date sorting omitted or reordered rows');
    h.assert(found[0].created===''&&found[1].created===''&&found[3].created==='','NULL creation dates were not blank');
    h.assert(found[0].edited!==''&&found[1].edited!==''&&found[2].edited===''&&found[3].edited==='','NULL edit dates were not blank');
    h.assert(found[0].current&&!found[1].current&&found[2].current&&found[3].current,'Nullable dates changed the current-form labels');
    await assertContext([],null);
  });
}
if(require.main===module)runWorkflow('echart-forms-list-paging',workflow,{openPatient:true});
module.exports={workflow};
