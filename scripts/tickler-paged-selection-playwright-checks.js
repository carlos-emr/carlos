#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Main Tickler > own search > default 50-row page > Next > Check All > Complete;
// or first page > Check All > Delete. No unowned task may be submitted.
const h=require('./lib/playwright-harness');
const ui=require('./lib/playwright-ui');
const {runWorkflow}=require('./lib/workflow-session');
const {waitForTicklerTable}=require('./tickler-forward-filters-playwright-checks');
const all=process.env.TICKLER_SELECTION_ACTION==='first';
const name=all?'tickler-first-page-delete':'tickler-last-page-complete';
async function workflow(s){
  const {sql,patient,marker,provider}=s,q=h.sqlString;
  const owned=`demographic_no=${patient} AND message LIKE ${q(marker+'%')}`;
  s.cleanup(()=>{
    const ids=`SELECT tickler_no FROM tickler WHERE ${owned}`;
    sql.execute(`DELETE FROM ticklerdocs WHERE tickler_id IN (${ids}); DELETE FROM tickler_comments WHERE tickler_no IN (${ids});
      DELETE FROM tickler_update WHERE tickler_no IN (${ids}); DELETE FROM tickler WHERE ${owned}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler WHERE ${owned}`)==='0','Owned paged tasks remain');
  });
  const ids=[];
  for(let i=0;i<53;i++)ids.push(h.insertId(sql,`INSERT INTO tickler(demographic_no,message,status,update_date,service_date,creator,priority,task_assigned_to)
    VALUES(${patient},${q(marker+(i===52?' hidden control':' page '+String(i).padStart(2,'0')))},'A',NOW(),CURDATE(),${q(provider)},'Normal',${q(provider)})`,'task'));
  let list;
  const isList=r=>new URL(r.url()).pathname.endsWith('/tickler/ListTicklers');
  const boxes=()=>list.locator('#ticklerResults input[name="checkbox"]');
  await s.step('the task search contains 52 owned results split across two pages',async()=>{
    ({page:list}=await ui.clickOpensPopupOrNavigates(s.schedule,s.schedule.locator('a:has(#oscar_new_tickler)').first(),{context:s.context,recorder:s.recorder,label:name}));
    await waitForTicklerTable(list);
    await Promise.all([list.waitForResponse(r=>isList(r)&&new URL(r.url()).searchParams.get('search[value]')===marker+' page'),list.locator('#ticklerResults_filter input').fill(marker+' page')]);
    await waitForTicklerTable(list);
    h.assert(await boxes().count()===50,'First page is not 50 tasks');
  });
  await s.step(all?'First page Check All deletes only its 50 matching tasks and preserves the last page and hidden task'
    :'Next page Check All completes only its two tasks and leaves the first page and hidden task active',async()=>{
    if(!all) await Promise.all([list.waitForResponse(isList),list.locator('#ticklerResults_next a').click()]);
    await waitForTicklerTable(list);
    const selected=await boxes().evaluateAll(nodes=>nodes.map(x=>x.value));
    h.assert(selected.length===(all?50:2)&&selected.every(id=>ids.slice(0,52).includes(id)),'Pagination exposed unexpected tasks');
    await list.locator('#checkAllLink').click();
    await list.waitForFunction(n=>document.querySelectorAll('#ticklerResults input[name="checkbox"]:checked').length===n,selected.length);
    const action=all?'Delete':'Complete';
    const [response]=await Promise.all([
      list.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/tickler/DbTicklerMain')&&r.request().method()==='POST'),
      list.waitForNavigation({waitUntil:'domcontentloaded'}),
      list.locator(`input[type="button"][onclick*="submit_form.value='${action}'"]`).click(),
    ]);
    h.assert(response.status()<400,`Paged action answered HTTP ${response.status()}`);
    const data=new URLSearchParams(response.request().postData());
    h.assert(data.get('CSRF-TOKEN')&&JSON.stringify(data.getAll('checkbox').sort())===JSON.stringify([...selected].sort()),'Action submitted other than the visible checked IDs');
    for(const id of ids)h.assert(sql.value(`SELECT status FROM tickler WHERE tickler_no=${id}`)===(selected.includes(id)?(all?'D':'C'):'A'),'Paged action changed the wrong task');
    await waitForTicklerTable(list);
  });
}
if(require.main===module)runWorkflow(name,workflow,{openMaster:false});
module.exports={workflow};
