/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {resultMarkup,verifyAudit}=require('./report-query-by-example-budgets-playwright-checks');
const {spawnSync}=require('node:child_process');
const QUERY='SELECT 1',HASH='e004ebd5b5532a4b85984a62f8ad48a81aa3460c1ca07701f386135d72cdecf5';
function log({provider='999998',hash=HASH,length=8,duration=15,rows=1,outcome='success',logger='data.RptByExampleData',level='INFO',suffix=''}={}) {
  return `2026-11-08 00:00:00,000 ${level} ${logger} (RptByExampleData.java:246) - Query-by-Example audit provider=${provider} queryHash=${hash} queryLength=${length} durationMs=${duration} rowCount=${rows} outcome=${outcome}${suffix}`;
}
test('one authoritative QBE audit receipt retains its exact outcome/count/duration',()=>{
  assert.deepEqual(verifyAudit(log(),QUERY,'999998','success',1),{duration:15,rows:1,outcome:'success'});
});
for(const [label,text] of [
  ['missing receipt',''],['another logger',log({logger:'service.OtherReport'})],['wrong severity',log({level:'WARN'})],
  ['wrong query hash',log({hash:'0'.repeat(64)})],['wrong provider',log({provider:'2'})],['wrong query length',log({length:9})],
  ['wrong row count',log({rows:0})],['wrong outcome',log({outcome:'timeout'})],['duplicated receipt',log()+'\n'+log()],
  ['unexpected metadata',log({suffix:' sqlBase64=unrecognized'})],['unsafe numeric metadata',log({duration:'99999999999999999999'})],
  ['raw SQL disclosure',log()+'\nsubmitted SQL: '+QUERY],
])test('QBE audit refuses '+label,()=>assert.throws(()=>verifyAudit(text,QUERY,'999998','success',1)));

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
  assert.equal(child.status,2);assert.match(child.stdout,value==='true'?/readable application audit/:/CARLOS_DISPOSABLE_VM=true/);
});
