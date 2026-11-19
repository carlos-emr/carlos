#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
'use strict';
// #5811: real row/encoded-output limits and a reporting-account SQL timeout.
// Each run's audit receipt is its row in the `log` audit table (read/query_by_example, finding 357),
// not the INFO line, which the packaged WARN log level discards. The application log is still read,
// to prove the submitted SQL never reaches it.
const assert=require('node:assert/strict'),crypto=require('node:crypto');
const h=require('./lib/playwright-harness');
const ui=require('./lib/playwright-ui');
const {runWorkflow}=require('./lib/workflow-session');
const {revealAuditLink}=require('./lib/playwright-link-audit');
const {serverLogFromEnvironment}=require('./lib/server-log');
const {waitUntil}=require('./lib/concurrency-support');
const q=h.sqlString;
const ROW_CAP=1000,CHAR_CAP=1000000;
const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));

/** The actual table fragment, not the page wrapper or DOM serialization. */
function resultMarkup(html) {
  const matches=[...html.matchAll(/<table\b[^>]*\bid=['"]results['"][^>]*>[\s\S]*?<\/table>/gi)];
  h.assert(matches.length===1,'Native QBE response must contain one result table');
  const markup=matches[0][0];
  h.assert(markup.length<=CHAR_CAP,'Encoded QBE result-table markup exceeds its character budget');
  h.assert(!markup.replace(/&(?:[a-zA-Z][a-zA-Z0-9]*|#\d+|#x[0-9a-fA-F]+);/g,'').includes('&'),'QBE result ends with an incomplete HTML entity');
  return markup;
}

const queryHash=query=>crypto.createHash('sha256').update(query).digest('hex');
/** The row's data as RptByExampleData.auditData writes it: metadata only, never SQL or a result value. */
const AUDIT_DATA=/^queryHash=([a-f0-9]{64}) queryLength=(\d+) durationMs=(\d+) rowCount=(\d+) outcome=([a-z_]+)$/;

/**
 * Every `log` row written after `after` (a MAX(id) mark) whose data names this query's hash, by any
 * provider or action, so a substituted or duplicated row is seen too. The hash is of SQL that carries
 * the run's marker, so no other check's row can match. The SQL text itself is never put in the lookup.
 */
function auditRows(sql,after,query) {
  return sql.rows(`SELECT id,COALESCE(provider_no,''),COALESCE(action,''),COALESCE(content,''),COALESCE(contentId,''),
    COALESCE(CAST(demographic_no AS CHAR),''),COALESCE(ip,''),COALESCE(data,'') FROM log
    WHERE id>${Number(after)} AND data LIKE ${q('%queryHash='+queryHash(query)+'%')} ORDER BY id`)
    .map(([id,provider,action,content,contentId,demographic,ip,data])=>({id:Number(id),provider,action,content,contentId,demographic,ip,data}));
}

/** One authoritative audit row; missing, substituted or duplicate rows fail. Messages never carry a row value. */
function verifyAudit(rows,query,provider,outcome,count) {
  h.assert(rows.length===1,'QBE audit requires exactly one authoritative receipt; found '+rows.length);
  const [row]=rows;
  h.assert(row.action==='read'&&row.content==='query_by_example'&&row.contentId==='','QBE audit row has the wrong action, content type or content id');
  h.assert(row.demographic==='','QBE audit row is filed under a patient');
  h.assert(row.provider===provider&&Boolean(row.ip),'QBE audit has incorrect provider or no client address');
  h.assert(![row.content,row.contentId,row.data].some(text=>text.includes(query)),'QBE application audit disclosed raw SQL');
  const match=AUDIT_DATA.exec(row.data);
  h.assert(match,'QBE audit row data is not the documented query metadata');
  h.assert(match[1]===queryHash(query)&&Number(match[2])===query.length,'QBE audit has incorrect query hash or length');
  h.assert([2,3,4].every(index=>Number.isSafeInteger(Number(match[index]))),'QBE audit numeric metadata is invalid');
  h.assert(match[5]===outcome&&Number(match[4])===count,'QBE audit has the wrong outcome or row count');
  return {duration:Number(match[3]),rows:Number(match[4]),outcome:match[5]};
}

async function workflow(s) {
  const {sql,marker,patient,provider}=s;
  const reportUser=process.env.QBE_REPORTING_USER||'carlos_report';
  const reader=serverLogFromEnvironment();
  const prefix=marker+'-qbe-',narrowPrefix=prefix+'row-',widePrefix=prefix+'wide-';
  const active=()=>sql.rows(`SELECT ID,USER,TIME FROM information_schema.PROCESSLIST WHERE USER=${q(reportUser)}
    AND COMMAND='Query' AND INFO LIKE ${q('%'+marker+'%')} AND ID<>CONNECTION_ID()`);
  const successes=[];
  let namespaceClaimed=false;
  s.cleanup(()=>{
    if(!namespaceClaimed)return;
    h.assert(active().length===0,'Owned reporting query is still running; retain fixtures for reconciliation');
    h.assert(sql.value(`SELECT COUNT(*) FROM demographicExt WHERE demographic_no=${patient}
      AND NOT (key_val LIKE ${q(prefix+'%')} AND provider_no=${q(provider)})`)==='0','QBE fixture ownership changed; retain the patient');
    h.assert(sql.value(`SELECT COUNT(*) FROM reportByExamples WHERE query LIKE ${q('%'+marker+'%')} AND providerNo<>${q(provider)}`)==='0','Another provider owns marker query history; retain it');
    h.assert(sql.value(`SELECT COUNT(*) FROM reportByExamplesFavorite WHERE name LIKE ${q(marker+'%')}`)==='0','Unexpected QBE favourite fixture requires reconciliation');
    sql.execute(`DELETE FROM reportByExamples WHERE providerNo=${q(provider)} AND query LIKE ${q('%'+marker+'%')};
      DELETE FROM demographicExt WHERE demographic_no=${patient} AND key_val LIKE ${q(prefix+'%')} AND provider_no=${q(provider)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM reportByExamples WHERE query LIKE ${q('%'+marker+'%')})+
      (SELECT COUNT(*) FROM demographicExt WHERE demographic_no=${patient})`)==='0','Owned QBE fixtures or history remain');
  });
  h.assert(sql.value(`SELECT COUNT(*) FROM reportByExamples WHERE query LIKE ${q('%'+marker+'%')}`)==='0','QBE history namespace collision');
  h.assert(sql.value(`SELECT COUNT(*) FROM demographicExt WHERE demographic_no=${patient}`)==='0','QBE patient already has extension rows before setup');
  namespaceClaimed=true;
  const wideValue='<&"\'>'.repeat(300);
  const fixtures=[...Array.from({length:1001},(_,i)=>({key:narrowPrefix+String(i+1).padStart(4,'0'),value:'ROW'+String(i+1).padStart(4,'0')})),
    ...Array.from({length:200},(_,i)=>({key:widePrefix+String(i+1).padStart(4,'0'),value:wideValue}))];
  for(let start=0;start<fixtures.length;start+=20) {
    const batch=fixtures.slice(start,start+20);
    sql.execute(`INSERT INTO demographicExt(demographic_no,provider_no,key_val,value,date_time,hidden) VALUES `
      +batch.map(item=>`(${patient},${q(provider)},${q(item.key)},${q(item.value)},NOW(),0)`).join(','));
  }
  const stored=sql.rows(`SELECT id,key_val,value FROM demographicExt WHERE demographic_no=${patient} ORDER BY id`);
  h.assert(stored.length===fixtures.length,'QBE fixture row count is incomplete');
  for(let i=0;i<stored.length;i++)assert.deepEqual(stored[i].slice(1),[fixtures[i].key,fixtures[i].value],'QBE fixture seed differs from its independent data');
  // One value also crosses many JDBC reader chunks; construct it in SQL setup
  // so neither the CLI argument limit nor an upload substitutes for the test.
  const giantKey=prefix+'giant',giantValue='<&'.repeat(300000);
  sql.execute(`INSERT INTO demographicExt(demographic_no,provider_no,key_val,value,date_time,hidden)
    VALUES(${patient},${q(provider)},${q(giantKey)},REPEAT('<&',300000),NOW(),0)`);
  const giant=sql.rows(`SELECT id,key_val,value FROM demographicExt WHERE demographic_no=${patient} AND key_val=${q(giantKey)}`);
  h.assert(giant.length===1,'QBE giant-value fixture is missing or duplicated');
  assert.deepEqual(giant[0].slice(1),[giantKey,giantValue],'QBE giant-value seed differs from its independent content');
  const clinicalBefore=sql.rows(`SELECT * FROM demographic WHERE demographic_no=${patient}`);
  const history=query=>sql.value(`SELECT COUNT(*) FROM reportByExamples WHERE providerNo=${q(provider)} AND query=${q(query)}`);
  const baseQuery=which=>`SELECT id AS source_id,key_val AS row_key,value AS cell_value FROM demographicExt WHERE demographic_no=${patient} AND key_val LIKE ${q(which+'%')} ORDER BY id`;
  const {page:admin}=await ui.clickOpensPopupOrNavigates(s.schedule,s.schedule.locator('#admin-panel,#admin2').first(),
    {context:s.context,recorder:s.recorder,label:'qbe-budget-admin'});
  const link=admin.getByRole('link',{name:'Query By Example',exact:true,includeHidden:true});
  await revealAuditLink(admin,link,30000);await link.click();
  const iframe=admin.locator('#dynamic-content iframe').first();await iframe.waitFor();
  const frame=await(await iframe.elementHandle()).contentFrame();h.assert(frame,'Native QBE frame did not load');
  const alerts=()=>frame.locator('.alert[role="alert"]:visible');
  async function run(query) {
    await frame.locator('#sql').fill(query);
    const response=admin.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/oscarReport/RptByExample'),{timeout:45000});
    const navigation=admin.waitForEvent('framenavigated',{predicate:candidate=>candidate===frame,timeout:45000});
    response.catch(()=>{});navigation.catch(()=>{});
    await frame.locator('#queryForm button[type="submit"]').click();
    const received=await response;await navigation;await frame.waitForLoadState('networkidle',{timeout:45000});
    h.assert(received.status()===200,'Native QBE run did not return its application outcome');
    const submitted=new URLSearchParams(received.request().postData());
    h.assert(submitted.get('sql')===query&&submitted.get('CSRF-TOKEN'),'Native QBE form changed the SQL or omitted CSRF protection');
    return received.text();
  }
  async function table(html) {
    const markup=resultMarkup(html),target=frame.locator('#results');
    const rows=await target.locator('tr:has(td)').evaluateAll(nodes=>nodes.map(node=>[...node.querySelectorAll('td')].map(cell=>cell.textContent)));
    assert.deepEqual(await target.locator('th').allTextContents(),['source_id','row_key','cell_value'],'Native QBE changed result column identities');
    h.assert(await target.locator('script,img,iframe,input,b').count()===0,'QBE result interpreted encoded fixture markup');
    h.assert((markup.match(/<tr\b/g)||[]).length===rows.length&&(markup.match(/<\/tr>/g)||[]).length===rows.length,'QBE result row markup is incomplete');
    h.assert((markup.match(/<td\b/g)||[]).length===rows.length*3&&(markup.match(/<\/td>/g)||[]).length===rows.length*3,'QBE result cells are incomplete');
    return {markup,rows};
  }
  // A run's window: the application log position and the highest `log` row id before it.
  const markRun=()=>({log:reader.mark(),audit:Number(sql.value('SELECT COALESCE(MAX(id),0) FROM log'))});
  async function receipt(mark,query,outcome,rows) {
    // The row is committed before the page answers; the wait only tolerates a slow database.
    await waitUntil(()=>auditRows(sql,mark.audit,query).length>0,'QBE audit receipt');
    await pause(200);
    const record=verifyAudit(auditRows(sql,mark.audit,query),query,provider,outcome,rows);
    h.assert(!reader.since(mark.log).includes(marker),'QBE audit/log span includes submitted SQL marker text');
    return record;
  }
  for(const count of [999,1000,1001])await s.step('row cap '+count+': exact ordered IDs, warning boundary and successful history/audit',async()=>{
    const query=baseQuery(narrowPrefix)+' LIMIT '+count,mark=markRun();
    const {rows}=await table(await run(query));assert.deepEqual(rows,stored.slice(0,Math.min(count,ROW_CAP)),'Row cap returned missing, duplicated or wrong source rows');
    const messages=await alerts().allTextContents();
    if(count>ROW_CAP)h.assert(messages.length===1&&/Additional rows were omitted after the 1[,.\u00a0\u202f]?000-row display limit was reached\./.test(messages[0]),'Above-cap result lacks its distinct row-limit warning');
    else h.assert(messages.length===0,'Complete at/below-cap result wrongly warns of truncation');
    h.assert(history(query)==='1','Successful bounded query was not recorded exactly once');successes.push(query);
    await receipt(mark,query,'success',rows.length);
  });
  await s.step('encoded character budget caps only the actual result table and clearly marks its partial final cell',async()=>{
    const query=baseQuery(widePrefix),mark=markRun(),{rows,markup}=await table(await run(query));
    h.assert(rows.length>1&&rows.length<200&&markup.length>CHAR_CAP-100,'Character limit did not reach its independent encoded-output boundary');
    h.assert(rows.length<ROW_CAP,'Character test reached the row cap instead');
    const expected=stored.slice(1001);
    for(let i=0;i<rows.length;i++) {
      assert.deepEqual(rows[i].slice(0,2),expected[i].slice(0,2),'Character-budget output changed row order or identity');
      if(i<rows.length-1)h.assert(rows[i][2]===wideValue,'A preceding character-budget cell is incomplete');
      else h.assert(rows[i][2].endsWith('…')&&wideValue.startsWith(rows[i][2].slice(0,-1)),'Final truncated cell is not an explicit faithful prefix');
    }
    const messages=await alerts().allTextContents();
    h.assert(messages.length===1&&/Output was truncated after 1[,.\u00a0\u202f]?000[,.\u00a0\u202f]?000 characters\./.test(messages[0]),'Encoded budget lacks its distinct size warning');
    h.assert(history(query)==='1','Size-truncated success was not recorded exactly once');successes.push(query);
    await receipt(mark,query,'success',rows.length);
  });
  await s.step('one streamed large value remains bounded across reader chunks with a complete table and explicit ellipsis',async()=>{
    const query=baseQuery(giantKey),mark=markRun(),{rows,markup}=await table(await run(query));
    h.assert(rows.length===1&&markup.length>CHAR_CAP-100,'A single streamed cell did not reach the independent character boundary');
    assert.deepEqual(rows[0].slice(0,2),giant[0].slice(0,2),'Stream truncation changed the source identity');
    h.assert(rows[0][2].length>200000&&rows[0][2].endsWith('…')&&giantValue.startsWith(rows[0][2].slice(0,-1)),
      'Streamed-cell output lost, duplicated or silently cut content across chunks');
    const messages=await alerts().allTextContents();
    h.assert(messages.length===1&&/Output was truncated after 1[,.\u00a0\u202f]?000[,.\u00a0\u202f]?000 characters\./.test(messages[0]),'Streamed cell lacks the size warning or falsely reports a row limit');
    h.assert(history(query)==='1','Stream-truncated success did not enter history exactly once');successes.push(query);await receipt(mark,query,'success',1);
  });
  await s.step('a genuine bounded database query times out as the reporting user and leaves no stale results or successful history',async()=>{
    const clauses=['a','b','c'].map(alias=>`${alias}.demographic_no=${patient} AND ${alias}.key_val LIKE ${q(narrowPrefix+'%')}`).join(' AND ');
    const query=`SELECT COUNT(*) AS matches FROM demographicExt a CROSS JOIN demographicExt b CROSS JOIN demographicExt c WHERE ${clauses} AND SHA2(CONCAT(a.value,b.value,c.value),512) LIKE 'Z%'`;
    const mark=markRun(),started=Date.now();let finished=false,failure,html,maxDatabaseSeconds=0,observed=false;
    const running=run(query).then(value=>{html=value;finished=true;},error=>{failure=error;finished=true;});
    while(!finished&&Date.now()-started<45000) {
      const processes=active();
      for(const [id,user,seconds] of processes){h.assert(/^[1-9]\d*$/.test(id)&&user===reportUser,'Timeout query used the wrong database identity');observed=true;maxDatabaseSeconds=Math.max(maxDatabaseSeconds,Number(seconds));}
      await pause(200);
    }
    h.assert(finished,'Native database timeout did not complete within its bounded observation');await running;if(failure)throw failure;
    h.assert(observed&&maxDatabaseSeconds>=12,'No genuine long-running database query was observed under the reporting account');
    const messages=await alerts().allTextContents();
    h.assert(messages.length===1&&messages[0].trim()==='The query exceeded the 15-second time limit and was stopped.','Native outcome is not the actual SQL-timeout guidance');
    h.assert(await frame.locator('#results').count()===0&&!/<table\b[^>]*\bid=['"]results['"]/.test(html),'Timeout retained an earlier result table');
    h.assert(history(query)==='0','Timed-out execution entered successful query history');
    await waitUntil(()=>active().length===0,'timed-out database query cancellation',5000);
    const audit=await receipt(mark,query,'timeout',0);
    h.assert(audit.duration>=12000&&audit.duration<=22000,'SQL timeout audit duration misses the documented 15-second boundary');
  });
  await s.step('narrowing in the same composer recovers without a stale timeout/truncation warning',async()=>{
    const query=baseQuery(narrowPrefix)+' LIMIT 3',mark=markRun();
    const {rows}=await table(await run(query));assert.deepEqual(rows,stored.slice(0,3),'Recovery returned the wrong rows');
    h.assert(await alerts().count()===0,'Recovered query retained timeout or truncation guidance');
    h.assert(history(query)==='1','Recovered query did not enter successful history');successes.push(query);await receipt(mark,query,'success',3);
    h.assert(sql.value(`SELECT COUNT(*) FROM reportByExamples WHERE providerNo=${q(provider)} AND query LIKE ${q('%'+marker+'%')}`)===String(successes.length),'Query history contains an extra/missing execution');
    assert.deepEqual(sql.rows(`SELECT * FROM demographic WHERE demographic_no=${patient}`),clinicalBefore,'QBE resource limits changed the owned clinical record');
  });
}
function preflightConfig(){
  if(process.env.CARLOS_DISPOSABLE_VM!=='true')throw new h.SkipCheck('Real bounded database timeout fixture requires CARLOS_DISPOSABLE_VM=true');
  const reader=serverLogFromEnvironment();if(!reader.configured)throw new h.SkipCheck('QBE requires a readable application log via LAB_UPLOAD_JOURNAL_UNIT or LAB_UPLOAD_SERVER_LOG to prove submitted SQL never reaches it');reader.since(reader.mark());
  h.assert(/^[A-Za-z0-9_.-]{1,128}$/.test(process.env.QBE_REPORTING_USER||'carlos_report'),'Invalid configured QBE reporting username');
}
if(require.main===module)runWorkflow('report-query-by-example-budgets',workflow,{openMaster:false,preflightConfig,preflight:({sql})=>{
  const grants=sql.rows('SHOW GRANTS FOR CURRENT_USER()').flat();
  if(!grants.some(grant=>/^GRANT (?:ALL PRIVILEGES|[^;]*\bPROCESS\b[^;]*) ON \*\.\*/i.test(grant)))
    throw new h.SkipCheck('QBE timeout observation requires the SQL fixture account to hold PROCESS; its grant text is never logged');
}});
module.exports={resultMarkup,auditRows,verifyAudit,workflow,preflightConfig};
