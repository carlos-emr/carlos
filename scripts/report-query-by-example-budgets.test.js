/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {resultMarkup,auditRows,verifyAudit}=require('./report-query-by-example-budgets-playwright-checks');
const {spawnSync}=require('node:child_process');
const QUERY='SELECT 1',HASH='e004ebd5b5532a4b85984a62f8ad48a81aa3460c1ca07701f386135d72cdecf5';
/** One `log` row as auditRows maps it (NULL columns read as ''). */
function row({provider='999998',action='read',content='query_by_example',contentId='',demographic='',ip='127.0.0.1',hash=HASH,length=8,duration=15,rows=1,outcome='success',suffix=''}={}) {
  return {id:7,provider,action,content,contentId,demographic,ip,
    data:`queryHash=${hash} queryLength=${length} durationMs=${duration} rowCount=${rows} outcome=${outcome}${suffix}`};
}
test('one authoritative QBE audit receipt retains its exact outcome/count/duration',()=>{
  assert.deepEqual(verifyAudit([row()],QUERY,'999998','success',1),{duration:15,rows:1,outcome:'success'});
});
for(const [label,rows] of [
  ['missing receipt',[]],['another audit content',[row({content:'demographic_report'})]],['another audit action',[row({action:'export'})]],
  ['wrong query hash',[row({hash:'0'.repeat(64)})]],['wrong provider',[row({provider:'2'})]],['wrong query length',[row({length:9})]],
  ['wrong row count',[row({rows:0})]],['wrong outcome',[row({outcome:'timeout'})]],['duplicated receipt',[row(),row()]],
  ['unexpected metadata',[row({suffix:' sqlBase64=unrecognized'})]],['unsafe numeric metadata',[row({duration:'99999999999999999999'})]],
  ['raw SQL disclosure',[row({suffix:' sql='+QUERY})]],['a patient on the row',[row({demographic:'42'})]],
  ['a content id on the row',[row({contentId:'42'})]],['a row without its client address',[row({ip:''})]],
])test('QBE audit refuses '+label,()=>assert.throws(()=>verifyAudit(rows,QUERY,'999998','success',1)));

test('the QBE audit reader scopes the log table to the run window and the query hash, never the SQL text',()=>{
  const statements=[];
  const sql={rows:statement=>{statements.push(statement);return [['7','999998','read','query_by_example','','','127.0.0.1',row().data]];}};
  assert.deepEqual(auditRows(sql,41,QUERY),[row()]);
  assert.equal(statements.length,1);
  assert.match(statements[0],/\bFROM log\s+WHERE id>41 AND data LIKE '%queryHash=e004ebd5b5532a4b85984a62f8ad48a81aa3460c1ca07701f386135d72cdecf5%' ORDER BY id$/);
  assert.ok(!statements[0].includes(QUERY),'the lookup must not carry the submitted SQL');
});

const table='<table id="results"><th>value</th><tr><td>&lt;&amp;&#34;&#39;&gt;</td></tr></table>';
test('the QBE size oracle measures the actual encoded table independently of page size',()=>{
  assert.equal(resultMarkup('page wrapper'.repeat(100000)+table+'tail'),table);
});
for(const [label,html] of [
  ['missing result table','<table id="other"></table>'],['duplicated tables',table+table],
  ['oversized encoded table','<table id="results"><tr><td>'+'a'.repeat(1000000)+'</td></tr></table>'],
  ['partial table',table.replace('</table>','')],['dangling entity',table.replace('&amp;','&am')],
  ['raw ampersand',table.replace('&amp;','&')],
])test('QBE size oracle rejects '+label,()=>assert.throws(()=>resultMarkup(html)));

for(const value of ['true','1','false',''])test('QBE timeout preflight uses the suite disposable-VM convention: '+JSON.stringify(value),()=>{
  const child=spawnSync(process.execPath,['-e',`try{require('./scripts/report-query-by-example-budgets-playwright-checks').preflightConfig();}catch(error){process.stdout.write(error.message);process.exitCode=2;}`],
    {cwd:require('node:path').resolve(__dirname,'..'),encoding:'utf8',env:{...process.env,CARLOS_DISPOSABLE_VM:value,LAB_UPLOAD_JOURNAL_UNIT:'',LAB_UPLOAD_SERVER_LOG:''}});
  assert.equal(child.status,2);assert.match(child.stdout,value==='true'?/readable application log/:/CARLOS_DISPOSABLE_VM=true/);
});
