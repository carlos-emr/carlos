#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
'use strict';
// #5810: real server responses are held, never replaced by fabricated task data.
const assert=require('node:assert/strict');
const h=require('./lib/playwright-harness');
const ui=require('./lib/playwright-ui');
const {runWorkflow}=require('./lib/workflow-session');
const {withTicklerConfirmations}=require('./lib/tickler-confirmation');
const {waitForTicklerTable}=require('./tickler-forward-filters-playwright-checks');
const q=h.sqlString;
const listRequest=request=>request.method()==='GET'&&new URL(request.url()).pathname.endsWith('/tickler/ListTicklers');
const bulkRequest=request=>request.method()==='POST'&&new URL(request.url()).pathname.endsWith('/tickler/DbTicklerMain');

/** One held real response, with an explicit release even when an assertion fails. */
function responseBarrier(match) {
  let arrive,release,complete;
  const arrived=new Promise(resolve=>{arrive=resolve;});
  const released=new Promise(resolve=>{release=resolve;});
  const completed=new Promise(resolve=>{complete=resolve;});
  let captured=false;
  return {arrived,release,async finish(){
    release();
    if(captured){const error=await completed;if(error)throw error;}
  },async handle(route) {
    if(captured||!match(route.request()))return route.continue();
    captured=true;
    let response,failure;
    try {
      response=await route.fetch({timeout:15000});
      const payload=await response.json();
      h.assert(response.status()===200&&Array.isArray(payload.data),'Held ListTicklers request did not produce real task data');
      arrive({request:route.request(),payload});
      await released;
      await route.fulfill({response});
    }catch(error){
      failure=error;arrive({error});
      try{await route.abort();}catch(abort){failure=new AggregateError([error,abort],error.message+'; held response abort: '+abort.message);}
    }finally{
      try{if(response)await response.dispose();}catch(error){failure=failure?new AggregateError([failure,error],failure.message+'; response disposal: '+error.message):error;}
      complete(failure);
    }
  }};
}

/** Observe a bounded capture without leaving a timer behind after success. */
async function arrival(barrier,label) {
  let timer;
  try {
    const captured=await Promise.race([barrier.arrived,new Promise((_,reject)=>{timer=setTimeout(()=>reject(new Error('No real held tickler response for '+label)),20000);})]);
    if(captured.error)throw captured.error;
    return captured;
  }finally{clearTimeout(timer);}
}

/** Drain/dispose every held response and remove its route, preserving all failures. */
async function withBarrier(context,barrier,work) {
  await context.route('**/tickler/ListTicklers*',barrier.handle);
  const failures=[];
  try{await work();}catch(error){failures.push(error);}
  try{await barrier.finish();}catch(error){failures.push(error);}
  try{await context.unroute('**/tickler/ListTicklers*',barrier.handle);}catch(error){failures.push(error);}
  if(failures.length===1)throw failures[0];
  if(failures.length)throw new AggregateError(failures,failures.map(error=>error.message).join('; '),{cause:failures[0]});
}

/** Verify stale selection is cleared across native list redraws and bulk actions affect only visible owned tasks. */
async function workflow(s) {
  const {sql,patient,provider,marker}=s;
  const other=sql.value(`SELECT provider_no FROM provider WHERE status='1' AND provider_no<>${q(provider)}
    AND provider_no REGEXP '^[1-9][0-9]*$' ORDER BY CAST(provider_no AS UNSIGNED) LIMIT 1`);
  if(!other)throw new h.SkipCheck('Stale tickler selection requires a second active offered assignee');
  const owned=`demographic_no=${patient} AND message LIKE ${q(marker+'%')}`;
  s.cleanup(()=>{
    const known=[...tasks.map(task=>task.id),'0'].join(',');
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler WHERE (demographic_no=${patient} OR tickler_no IN (${known}))
      AND NOT (${owned} AND creator=${q(provider)})`)==='0','Tickler fixture ownership changed; retain parent and task records');
    const ids=sql.rows(`SELECT tickler_no FROM tickler WHERE ${owned}`).flat();
    h.assert(ids.every(id=>/^[1-9]\d*$/.test(id)),'Invalid owned stale-selection tickler identity');
    if(ids.length) {
      const list=ids.join(',');
      sql.execute(`DELETE FROM ticklerdocs WHERE tickler_id IN (${list}); DELETE FROM tickler_comments WHERE tickler_no IN (${list});
        DELETE FROM tickler_update WHERE tickler_no IN (${list})`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM ticklerdocs WHERE tickler_id IN (${list}))+
        (SELECT COUNT(*) FROM tickler_comments WHERE tickler_no IN (${list}))+(SELECT COUNT(*) FROM tickler_update WHERE tickler_no IN (${list}))`)==='0','Owned tickler child rows remain');
      sql.execute(`DELETE FROM tickler WHERE ${owned} AND tickler_no IN (${list})`);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler WHERE demographic_no=${patient} OR tickler_no IN (${known})`)==='0','Owned stale-selection ticklers remain');
  });
  const tasks=[];
  for(const [label,status,assignee] of [
    ...Array.from({length:53},(_,index)=>['page '+String(index).padStart(2,'0'),'A',provider]),
    ['other first','A',other],['other second','A',other],['completed first','C',provider],['completed second','C',provider],
    ['deleted control','D',provider],['hidden control','A',provider],
  ]) {
    const id=h.insertId(sql,`INSERT INTO tickler(demographic_no,message,status,update_date,service_date,creator,priority,task_assigned_to)
      VALUES(${patient},${q(marker+' '+label)},${q(status)},NOW(),DATE_SUB(CURDATE(),INTERVAL 1 DAY),${q(provider)},'Normal',${q(assignee)})`,'stale-selection task');
    tasks.push({id,label,status,assignee});
  }
  const ids=tasks.map(task=>task.id).join(',');
  const rows=()=>sql.rows(`SELECT * FROM tickler WHERE tickler_no IN (${ids}) ORDER BY tickler_no`);
  const history=()=>sql.rows(`SELECT * FROM tickler_update WHERE tickler_no IN (${ids}) ORDER BY id`);
  const baseline=rows();
  const {page:list}=await ui.clickOpensPopupOrNavigates(s.schedule,s.schedule.locator('a:has(#oscar_new_tickler)').first(),
    {context:s.context,recorder:s.recorder,label:'stale-tickler-list'});
  const checked=()=>list.locator('#ticklerResults input[name="checkbox"]:checked');
  const boxes=()=>list.locator('#ticklerResults input[name="checkbox"]');
  const visibleIds=()=>boxes().evaluateAll(nodes=>nodes.map(node=>node.value));
  const action=name=>list.locator(name==='Complete'?'#completeButton':'#deleteButton');
  const search=list.locator('#ticklerResults_filter input[type="search"]');
  const bulkPosts=[];
  const watch=request=>{if(bulkRequest(request))bulkPosts.push(request);};
  s.context.on('request',watch);
  const unchanged=()=>{assert.deepEqual(rows(),baseline,'Filtering or pending selection mutated task rows');assert.deepEqual(history(),[],'Filtering or pending selection wrote task history');};
  async function draw(trigger,predicate=()=>true) {
    const [response]=await Promise.all([list.waitForResponse(r=>listRequest(r.request())&&predicate(new URL(r.url()).searchParams)),trigger()]);
    h.assert(response.status()===200,'Native tickler list draw failed');
    await waitForTicklerTable(list);
    return response.json();
  }
  async function selectFirst() {
    h.assert(await boxes().count()>0,'No visible task to select before changing the result set');
    await boxes().first().check();h.assert(await checked().count()===1,'Native selection did not check exactly one row');
  }
  async function clear() {
    await list.locator('a[href="javascript:ClearAll();"]').click();
    await list.waitForFunction(()=>document.querySelectorAll('#ticklerResults input[name="checkbox"]:checked').length===0);
  }
  async function changeWithHeldResponse(label,trigger,predicate,expected) {
    await selectFirst();
    const barrier=responseBarrier(request=>listRequest(request)&&predicate(new URL(request.url()).searchParams));
    await withBarrier(s.context,barrier,async()=>{
      await trigger();
      const captured=await arrival(barrier,label);
      h.assert(!await action('Complete').isVisible()&&!await action('Delete').isVisible(),'Pending '+label+' exposes stale bulk actions');
      h.assert(bulkPosts.length===0,'Pending '+label+' submitted stale task IDs');unchanged();
      const response=list.waitForResponse(r=>listRequest(r.request())&&new URL(r.url()).searchParams.get('draw')===new URL(captured.request.url()).searchParams.get('draw'));
      barrier.release();await response;await waitForTicklerTable(list);
      assert.deepEqual((await visibleIds()).sort(),[...expected].sort(),label+' rendered the wrong tasks');
      h.assert(await checked().count()===0,label+' carried selection to a replacement result set');
      await list.locator('#checkAllLink').click();
      await list.waitForFunction(count=>document.querySelectorAll('#ticklerResults input[name="checkbox"]:checked').length===count,expected.length);
      await clear();unchanged();
    });
  }
  await s.step('native search isolates two default-size pages and selections reset for changed search results',async()=>{
    await waitForTicklerTable(list);
    await draw(()=>search.fill(marker+' page'),p=>p.get('search[value]')===marker+' page');
    h.assert(await boxes().count()===50,'Expected default 50-row first page');
    const expected=tasks.filter(task=>task.label==='page 00').map(task=>task.id);
    await changeWithHeldResponse('search',()=>search.fill(marker+' page 00'),p=>p.get('search[value]')===marker+' page 00',expected);
  });
  await s.step('status and assignee changes keep old checked rows inert until their real replacement draw',async()=>{
    await draw(()=>search.fill(marker),p=>p.get('search[value]')===marker);
    await changeWithHeldResponse('Completed status',()=>list.locator('#ticklerview').selectOption('C'),p=>p.get('status')==='C',tasks.filter(task=>task.status==='C').map(task=>task.id));
    await draw(()=>list.locator('#ticklerview').selectOption('A'),p=>p.get('status')==='A');
    await list.locator('#assignedTo').selectOption(other);
    await changeWithHeldResponse('assignee',()=>list.locator('#formSubmitBtn').click(),p=>p.get('assignee')===other,tasks.filter(task=>task.assignee===other).map(task=>task.id));
    await list.locator('#assignedTo').selectOption(provider);
    await draw(()=>list.locator('#formSubmitBtn').click(),p=>p.get('assignee')===provider);
  });
  await s.step('changing page discards prior selections and Clear All applies to the newly drawn last page',async()=>{
    await draw(()=>search.fill(marker+' page'),p=>p.get('search[value]')===marker+' page');
    const first=await visibleIds(),expected=tasks.filter(task=>task.label.startsWith('page ')&&!first.includes(task.id)).map(task=>task.id);
    h.assert(expected.length===3,'Expected three owned tasks on the last page');
    await changeWithHeldResponse('pagination',()=>list.locator('#ticklerResults_next a').click(),p=>p.get('start')==='50',expected);
  });
  await s.step('out-of-order real responses preserve the latest filter and its current-page action selection',async()=>{
    const oldNeedle=marker+' page 01',latestNeedle=marker+' page 02';
    const barrier=responseBarrier(request=>listRequest(request)&&new URL(request.url()).searchParams.get('search[value]')===oldNeedle);
    await withBarrier(s.context,barrier,async()=>{
      await search.fill(oldNeedle);
      const captured=await arrival(barrier,'out-of-order request');
      await draw(()=>search.fill(latestNeedle),p=>p.get('search[value]')===latestNeedle);
      const expected=tasks.find(task=>task.label==='page 02').id;
      assert.deepEqual(await visibleIds(),[expected],'Latest filter did not render its exact task');
      const oldDraw=new URL(captured.request.url()).searchParams.get('draw');
      // Response headers alone do not prove the old AJAX success handler ran.
      // Observe its native xhr event, then the next task after that handler's
      // synchronous draw/ignore decision before checking the final DOM.
      await list.evaluate(draw=>{
        const table=document.getElementById('ticklerResults');
        if(table.hasAttribute('data-carlos-test-settled-draw'))throw new Error('Tickler observation marker already exists');
        window.jQuery(table).one('xhr.dt.carlosStaleSelection',(_event,_settings,json)=>{
          if(String(json?.draw)===draw)setTimeout(()=>{table.dataset.carlosTestSettledDraw=draw;},0);
        });
      },oldDraw);
      const oldResponse=list.waitForResponse(r=>listRequest(r.request())&&new URL(r.url()).searchParams.get('search[value]')===oldNeedle);
      barrier.release();await oldResponse;
      await list.waitForFunction(draw=>document.getElementById('ticklerResults').dataset.carlosTestSettledDraw===draw,oldDraw);
      await list.waitForFunction(needle=>window.jQuery('#ticklerResults').DataTable().search()===needle,latestNeedle);
      assert.deepEqual(await visibleIds(),[expected],'Late old response replaced the latest filter');
      h.assert(await checked().count()===0,'Late response restored old checkbox state');
      await list.evaluate(()=>{delete document.getElementById('ticklerResults').dataset.carlosTestSettledDraw;});
      unchanged();
    });
  });
  const changed=new Map();
  /** Submit one visible active owned task through native Complete/Delete and verify exact request, status and history. */
  async function submit(name) {
    const visible=await visibleIds();await boxes().first().check();
    const selected=await checked().evaluateAll(nodes=>nodes.map(node=>node.value));
    h.assert(selected.length===1&&selected.every(id=>visible.includes(id)&&tasks.some(task=>task.id===id&&task.status==='A'&&!changed.has(id))),
      'Refuse bulk mutation: selection is not exactly one currently visible, active, owned task');
    let response;
    await withTicklerConfirmations(list,name==='Delete'?1:0,async()=>{
      [response]=await Promise.all([list.waitForResponse(r=>bulkRequest(r.request())),list.waitForNavigation({waitUntil:'domcontentloaded'}),action(name).click()]);
    });
    h.assert(response.status()<400,'Native '+name+' failed');
    const data=new URLSearchParams(response.request().postData());
    assert.deepEqual(data.getAll('checkbox').sort(),[...selected].sort(),'Bulk POST includes stale/hidden/out-of-page IDs');
    h.assert(data.get('CSRF-TOKEN')&&data.get('submit_form')===name,'Bulk POST omitted the chosen action or CSRF protection');
    changed.set(selected[0],name==='Complete'?'C':'D');
    for(const task of tasks) {
      const desired=changed.get(task.id)||task.status;
      h.assert(sql.value(`SELECT status FROM tickler WHERE tickler_no=${task.id}`)===desired,'Bulk action mutated an unintended task');
      const updates=sql.rows(`SELECT status,assignedTo,provider_no FROM tickler_update WHERE tickler_no=${task.id} ORDER BY id`);
      if(changed.has(task.id))assert.deepEqual(updates,[[desired,null,provider]],'Bulk action did not write exactly one status-change history row');
      else assert.deepEqual(updates,[],'Hidden or previously selected task received history');
    }
    await waitForTicklerTable(list);
  }
  await s.step('Complete and Delete submit exactly current checked IDs and preserve every sentinel task/history',async()=>{
    await submit('Complete');
    await draw(()=>search.fill(marker+' page 03'),p=>p.get('search[value]')===marker+' page 03');
    await submit('Delete');
    h.assert(bulkPosts.length===2,'Unexpected duplicate bulk submission');
    for(const row of baseline) {
      if(!changed.has(row[0]))assert.deepEqual(sql.rows(`SELECT * FROM tickler WHERE tickler_no=${row[0]}`),[row],'Unselected complete task row changed');
    }
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM tickler_comments WHERE tickler_no IN (${ids}))+(SELECT COUNT(*) FROM ticklerdocs WHERE tickler_id IN (${ids}))`)==='0','Selection or status mutation created an unrelated comment/attachment');
  });
  await s.step('Deleted view still prohibits Complete/Delete after a pending status transition',async()=>{
    await draw(()=>search.fill(marker),p=>p.get('search[value]')===marker);
    await draw(()=>list.locator('#ticklerview').selectOption('D'),p=>p.get('status')==='D');
    h.assert(!await action('Complete').isVisible()&&!await action('Delete').isVisible(),'Deleted view offers an active-task mutation');
    await clear();h.assert(bulkPosts.length===2,'Deleted-view Clear All posted a task mutation');
  });
  s.context.off('request',watch);
}
if(require.main===module)runWorkflow('tickler-stale-selection',workflow,{openMaster:false});
module.exports={responseBarrier,withBarrier,workflow};
