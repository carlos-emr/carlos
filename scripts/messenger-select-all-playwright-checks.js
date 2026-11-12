#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Msg > search owned subjects > Select All > Read, or Archive > Archived >
// Select All > Unarchive. Fixtures are self-deliveries, never messages to people.
const h=require('./lib/playwright-harness');
const ui=require('./lib/playwright-ui');
const {runWorkflow}=require('./lib/workflow-session');
const {settle}=require('./messenger-demographic-link-playwright-checks');
const archive=process.env.MESSENGER_SELECTION_ACTION==='archive';
const name=archive?'messenger-select-all-archive-restore':'messenger-select-all-read-controls';
async function workflow(s){
  const {sql,marker,provider}=s,q=h.sqlString,ids=[];
  const location=sql.value('SELECT locationId FROM oscarcommlocations WHERE current1=1 LIMIT 1');
  h.assert(/^\d+$/.test(location),'No local messaging location');
  s.cleanup(()=>{
    const owned=`SELECT messageid FROM messagetbl WHERE thesubject LIKE ${q(marker+'%')}`;
    sql.execute(`DELETE FROM msgDemoMap WHERE messageID IN (${owned}); DELETE FROM messagelisttbl WHERE message IN (${owned});
      DELETE FROM messagetbl WHERE thesubject LIKE ${q(marker+'%')}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM messagetbl WHERE thesubject LIKE ${q(marker+'%')}`)==='0','Owned messages remain');
  });
  for(const suffix of [' batch A',' batch B',' hidden control']){
    const id=h.insertId(sql,`INSERT INTO messagetbl(thedate,theime,themessage,thesubject,sentby,sentto,sentbyNo,sentByLocation,type)
      VALUES(CURDATE(),CURTIME(),${q(marker)},${q(marker+suffix)},'Synthetic self','Synthetic self',${q(provider)},${location},2)`,'message');
    ids.push(id);sql.execute(`INSERT INTO messagelisttbl(message,provider_no,status,remoteLocation,destinationFacilityId,sourceFacilityId)
      VALUES(${id},${q(provider)},'new',${location},0,0)`);
  }
  let page;
  const boxes=()=>page.locator('input[name="messageNo"]');
  const states=()=>sql.rows(`SELECT message,status FROM messagelisttbl WHERE message IN (${ids.join(',')}) ORDER BY message`);
  async function search(){
    await page.locator('input[name="searchString"]').first().fill(marker+' batch');
    await ui.clickAndAwaitReload(page,page.locator('button[name="btnSearch"]'));
    await settle(page);
    h.assert(await boxes().count()===2,'Search did not isolate both selected messages');
  }
  async function selectAll(){await page.locator('#checkA').check();h.assert(await page.locator('input[name="messageNo"]:checked').count()===2,'Select All did not check both messages');}
  async function submit(button,status){
    await selectAll();
    const [r]=await Promise.all([page.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.includes('/messenger/')),ui.clickAndAwaitReload(page,page.locator(`button[name="${button}"]`).first())]);
    h.assert(r.status()<400,'Message bulk action failed');
    const data=new URLSearchParams(r.request().postData());
    h.assert(data.get('CSRF-TOKEN')&&JSON.stringify(data.getAll('messageNo').sort())===JSON.stringify(ids.slice(0,2).sort()),'Message action submitted the wrong IDs or no CSRF token');
    h.assert(JSON.stringify(states())===JSON.stringify([[ids[0],status],[ids[1],status],[ids[2],'new']]),'Bulk message action affected hidden control or missed selection');
  }
  await s.step('Msg search isolates the two owned self-deliveries and hides bulk actions until selected',async()=>{
    ({page}=await ui.clickOpensPopupOrNavigates(s.schedule,s.schedule.locator('a:has(#oscar_new_msg)').first(),{context:s.context,recorder:s.recorder,label:name}));
    await settle(page);await search();
    h.assert(!await page.locator('button[name="btnRead"]').first().isVisible(),'Read action visible without selection');
    await selectAll();h.assert(await page.locator('button[name="btnRead"]').first().isVisible(),'Select All did not reveal bulk actions');
    await page.locator('#checkA').uncheck();
    h.assert(await page.locator('input[name="messageNo"]:checked').count()===0&&!await page.locator('button[name="btnRead"]').first().isVisible(),'Clearing Select All left selection or actions visible');
  });
  await s.step(archive?'Select All archives both filtered messages and Unarchive restores both without touching the hidden delivery'
    :'Select All marks both filtered messages read and preserves the hidden unread delivery',async()=>{
    await submit(archive?'btnDelete':'btnRead',archive?'del':'read');
    if(archive){
      await ui.clickAndAwaitReload(page,page.locator('a[href*="DisplayMessages?boxType=2"]').first());
      await search();await submit('btnUnarchive','read');
    }
  });
}
if(require.main===module)runWorkflow(name,workflow,{openPatient:false});
module.exports={workflow};
