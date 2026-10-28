/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
'use strict';
const fs=require('node:fs');const os=require('node:os');const path=require('node:path');const {EventEmitter}=require('node:events');
const test=require('node:test');const assert=require('node:assert/strict');
const {createApNegativeProof,resolveOwnedLookup,verifyApNegativeFaultWindow,sha,SOURCE_KEYS}=require('./lib/apcache-negative-fault-proof');
const sources=Object.fromEntries(SOURCE_KEYS.map((k,i)=>[k,String(i).repeat(64)]));
const intent={runId:'1792440492014-123',missingKey:'renderer_missing_ap_1792440492014',negativeName:'Playwright APCache missing 1792440492014-123',ownedMarker:'FAKE-PW0123456789abcdef',sourceHashes:sources};
const fixture={patient:'55',negativeFid:'5',templateHtmlSha256:'a'.repeat(64)};
function request(url='https://localhost/carlos/eform/efmformapconfig_lookup?oscarAPCacheLookupType='+intent.missingKey+'&key='+intent.missingKey+'&fid=5&demographic_no=55',method='GET'){return {url:()=>url,method:()=>method};}
function valid(csrf=true){return {schemaVersion:1,...intent,intentWrittenAt:'2026-10-19T20:08:10.000Z',fixture,
  requests:[31,41].map(second=>({method:'GET',path:'/eform/efmformapconfig_lookup',key:intent.missingKey,fid:'5',patient:'55',status:200,failureMarkerPresent:true,responseSha256:'c'.repeat(64),startedAt:`2026-10-19T20:08:${second}.000Z`,finishedAt:`2026-10-19T20:08:${second+2}.000Z`})),
  directPost:{method:'POST',path:'/EFormApCacheForPdfGenerationServlet',status:csrf?403:405,startedAt:'2026-10-19T20:08:44.000Z',finishedAt:'2026-10-19T20:08:45.000Z'},
  positiveWorkflowCompleted:true,negativeWorkflowCompleted:true,browserClosed:true,cleanupAcknowledged:true};}
function errors(csrf=true){return [32,42].map(second=>`2026-10-19 20:08:${second},000 ERROR eform.efmformapconfig_005flookup_jsp (efmformapconfig_005flookup_jsp.java:335) - AP config lookup requested an unconfigured key=${intent.missingKey} fid=5\n`).join('')
 +(csrf?'2026-10-19 20:08:44,500 ERROR action.Log (Log.java:73) - CSRF violation: (user:<anonymous>, ip:127.0.0.1, method:POST, error:The token should exist in the storage at this point)\n':'');}
function audit(csrf=true){return 'UNKNOWN     2  ERROR eform.efmformapconfig_005flookup_jsp (efmformapconfig_005flookup_jsp.java:335)\n'
 +(csrf?'KNOWN       1  ERROR action.Log (Log.java:73) csrf-method:POST csrf-error:The token should exist in the storage at this point\n          (Existing baseline note remains untouched)\n':'')
 +`FAIL server log audit: 1 unexplained signature(s), ${csrf?1:0} known, ${csrf?3:2} event(s) since 2026-10-19 20:08:10 UTC\n`;}
const clone=x=>structuredClone(x);
function dir(t){const d=fs.mkdtempSync(path.join(os.tmpdir(),'ap-negative-proof-'));t.after(()=>fs.rmSync(d,{recursive:true,force:true}));return d;}

test('exact two negative lookups and actual tokenless403 are recognized without editing raw evidence',()=>{
 assert.deepEqual(verifyApNegativeFaultWindow(errors(),valid(),sources,audit()),{intentionalLookupFaultEvents:2,actualTokenlessPostFaultEvents:1,allOtherErrorsRefused:true,rawStrictAuditUnmodified:true});
});
test('native405 without CSRF error is permitted only with exactly two JSP errors',()=>{
 assert.equal(verifyApNegativeFaultWindow(errors(false),valid(false),sources,audit(false)).actualTokenlessPostFaultEvents,0);
 assert.throws(()=>verifyApNegativeFaultWindow(errors(),valid(false),sources,audit(false)));
});
for(const [label,change]of Object.entries({
 'source mismatch':r=>r.sourceHashes.testSourceSha256='f'.repeat(64),'missing intent':r=>delete r.intentWrittenAt,
 'wrong key':r=>r.missingKey='renderer_missing_ap_1792440492015','wrong owned form name':r=>r.negativeName='foreign',
 'foreign patient':r=>r.requests[0].patient='56','foreign template':r=>r.requests[0].fid='6','wrong method':r=>r.requests[0].method='POST',
 'wrong path':r=>r.requests[0].path='/other','HTTP error':r=>r.requests[0].status=500,'missing actual failure marker':r=>r.requests[0].failureMarkerPresent=false,
 'missing response hash':r=>delete r.requests[0].responseSha256,'missing response':r=>delete r.requests[0].finishedAt,
 'extra lookup':r=>r.requests.push(r.requests[0]),'missing lookup':r=>r.requests.pop(),'reversed windows':r=>r.requests.reverse(),
 'wrong direct method':r=>r.directPost.method='GET','no tokenless request':r=>delete r.directPost,
 'nonCSRF status':r=>r.directPost.status=200,'unproven positive flow':r=>r.positiveWorkflowCompleted=false,
 'unproven negative approval':r=>r.negativeWorkflowCompleted=false,'browser not closed':r=>r.browserClosed=false,'cleanup failed':r=>r.cleanupAcknowledged=false,
 'private extra field':r=>r.cookie='secret','unbound fixture extra field':r=>r.fixture.cookie='secret',
}))test('refuses '+label,()=>{const r=clone(valid());change(r);assert.throws(()=>verifyApNegativeFaultWindow(errors(),r,sources,audit()));});
for(const [label,change]of Object.entries({
 'extra unrelated ERROR':s=>s+'2026-10-19 20:08:44,700 ERROR Other.Action (Action.java:1) - unexpected\n',
 'foreign key':s=>s.replace(intent.missingKey,'renderer_missing_ap_1792440492015'),'foreign fid':s=>s.replace('fid=5','fid=6'),
 'lookup outside response window':s=>s.replace('20:08:32','20:08:34'),'CSRF outside actual POST':s=>s.replace('20:08:44,500','20:08:43,500'),
 'different JSP failure':s=>s.replace('requested an unconfigured','query failed for'),'missing second JSP event':s=>s.replace(/^2026-10-19 20:08:42[^\n]*\n/m,''),
 'unexpected trace':s=>s.replace(' fid=5\n',' fid=5\njava.lang.NullPointerException\n'),'HHH000099':s=>s+'HHH000099\n',
}))test('refuses raw '+label,()=>assert.throws(()=>verifyApNegativeFaultWindow(change(errors()),valid(),sources,audit())));
test('unknown/extra/unreadable audit evidence cannot be accepted',()=>{
 for(const a of ['',audit()+'UNKNOWN 1 ERROR Other.Action\n',audit().replace('UNKNOWN     2','UNKNOWN     3'),audit().replace('3 event(s)','4 event(s)'),audit()+'unrecognized audit output\n'])
  assert.throws(()=>verifyApNegativeFaultWindow(errors(),valid(),sources,a));
});
test('resolver accepts actual fid and save-view efmfid routes but never foreign or malformed lookups',()=>{
 assert.equal(resolveOwnedLookup(request(),'https://localhost/carlos',intent,fixture).fid,'5');
 const save=request(request().url().replace('fid=5&demographic_no=55','efmfid=5&efmdemographic_no=55'));
 assert.equal(resolveOwnedLookup(save,'https://localhost/carlos',intent,fixture).patient,'55');
 assert.equal(resolveOwnedLookup(request(request().url().replace('localhost','foreign.example')),'https://localhost/carlos',intent,fixture),null);
 assert.equal(resolveOwnedLookup(request(request().url().replace(intent.missingKey,'safe_key').replace(intent.missingKey,'safe_key')),'https://localhost/carlos',intent,fixture),null);
 for(const req of [request(request().url(),'POST'),request(request().url()+'&fid=6'),request(request().url().replace('55','56')),request(request().url()+'#fragment'),request(request().url()+'&key=other')])
  assert.throws(()=>resolveOwnedLookup(req,'https://localhost/carlos',intent,fixture));
});
test('durable private intent precedes binding and passive observer records actual response bytes without raw data',async t=>{
 const d=dir(t);const proof=createApNegativeProof(d,intent);assert.equal(fs.statSync(proof.filename).mode&0o777,0o600);
 assert.equal(JSON.parse(fs.readFileSync(proof.filename)).fixture,undefined);proof.bindFixture(fixture);
 const context=new EventEmitter();proof.attach(context,'https://localhost/carlos');
 for(let i=0;i<2;i++){const req=request();context.emit('request',req);context.emit('response',{request:()=>req,status:()=>200,text:async()=>`<input name="oscarAPCacheLookupFailures" value="${intent.missingKey}"><!--private-value-not-serialized-->`});await new Promise(resolve=>setImmediate(resolve));}
 await proof.drain();const record=JSON.parse(fs.readFileSync(proof.filename));assert.equal(record.requests.length,2);assert.equal(record.requests[0].status,200);assert.equal(record.requests[0].failureMarkerPresent,true);
 assert(!fs.readFileSync(proof.filename,'utf8').includes('private-value-not-serialized'));assert.throws(()=>proof.update({token:'secret'}));
 assert.throws(()=>proof.bindFixture(fixture));proof.close();proof.close();assert.throws(()=>createApNegativeProof(d,intent),/EEXIST/);
});
for(const [label,build]of Object.entries({
 'no events':async()=>{},'one event':async(c)=>{const req=request();c.emit('request',req);c.emit('response',{request:()=>req,status:()=>200,text:async()=>`<input name="oscarAPCacheLookupFailures" value="${intent.missingKey}">`});},
 'failure-marker absent':async(c)=>{for(let i=0;i<2;i++){const req=request();c.emit('request',req);c.emit('response',{request:()=>req,status:()=>200,text:async()=>'<input value="">'});}},
 'HTTP500':async(c)=>{for(let i=0;i<2;i++){const req=request();c.emit('request',req);c.emit('response',{request:()=>req,status:()=>500,text:async()=>''});}},
 'response never completes':async(c)=>{for(let i=0;i<2;i++)c.emit('request',request());},
}))test('passive observer refuses '+label,async t=>{const proof=createApNegativeProof(dir(t),intent);t.after(()=>proof.close());proof.bindFixture(fixture);const c=new EventEmitter();proof.attach(c,'https://localhost/carlos');await build(c);
 await assert.rejects(()=>proof.drain());});

test('actual main orders durable intent before DML and completes proof only after child/browser cleanup',async()=>{
 const vm=require('node:vm');const source=fs.readFileSync(path.join(__dirname,'eform-apcache-renderer-playwright-checks.js'),'utf8');
 const start=source.indexOf('async function main() {');const end=source.lastIndexOf("\nif (process.env.APCACHE_NEGATIVE_FAULT_ARTIFACT_DIR)");
 for (const failureMode of ['none','closed-write','create','name-collision']) {
 const failClosedReceipt=failureMode==='closed-write';
 const calls=[];const diagnostics=[];const proof={attach(){calls.push('attach')},bindFixture(){calls.push('bind')},update(v){calls.push(Object.keys(v)[0]);if(failClosedReceipt&&v.browserClosed)throw new Error('Synthetic receipt write failure')},async drain(){calls.push('drain')},close(){calls.push('close')}};
 let imports=0;const html=Buffer.from('synthetic fixture');const sql={value:q=>q.includes('FROM security')?'1':q.startsWith('UPDATE demographic')?'1':q.includes('HEX(form_html)')?html.toString('hex'):failureMode==='name-collision'&&q.includes('SELECT COUNT(*) FROM eform WHERE')?'1':'0',execute(){calls.push('sql.delete')},dispose(){calls.push('sql.dispose')}};
 const context={};const processRef={pid:123,env:{APCACHE_NEGATIVE_FAULT_ARTIFACT_DIR:'/tmp/private'},exitCode:undefined};
 const fakeRequire=()=>({});fakeRequire.resolve=()=>'/fake-helper';
 const sandbox={Date,Set,Buffer,Number,require:fakeRequire,__filename:'/fake-script',__dirname:'/fake-scripts',path,os,process:processRef,
  config:{journalUnit:'carlos-emr.service',probeUrl:'http://localhost/carlos',fixtureHtmlPath:'/fake-fixture',baseUrl:new URL('https://localhost/carlos')},
  LITERAL_LOOKUPS:[{expected:null}],h:{createSqlRunner:()=>sql,readConfig:()=>({mysql:{}}),sqlString:s=>"'"+s+"'"},eformRows:()=>({}),newOwnedMarker:()=>intent.ownedMarker,
  createApNegativeProof(){calls.push('intent');if(failureMode==='create')throw new Error('Synthetic receipt collision');return proof},assert,sha,fs:{readFileSync:()=>html,mkdtempSync:()=>'/tmp/fake',rmSync(){}},
  validateConfig(){},createOwnedPatient(){calls.push('patient.insert');return '55'},writeFixture:()=>'/tmp/fake.html',
  chromium:{async launch(){return {async newContext(){return context},async close(){calls.push('browser.close')}}}},getLaunchOptions:()=>({}),shouldIgnoreHttpsErrors:()=>false,
  async login(){return {async close(){}}},async uploadEform(){calls.push('template.import');return String(++imports+3)},
  async checkPositiveRender(){calls.push('positive');return {fdid:'6',providerValue:'synthetic',todayValue:'2026-10-19'}},
  async checkNegativeRender(){calls.push('negative');return {fdid:'7'}},checkJournal:()=>({warnLines:2}),async checkDirectProbes(){calls.push('probes');return {}},
  splitApprovedViewLookups(){},approvedViewLookupFailures:[],recorder:{badResponses:[],dialogs:[]},LOOKUP_PATH:'/eform/efmformapconfig_lookup',SERVLET_PATH:'/EFormApCacheForPdfGenerationServlet',
  describe:JSON.stringify,console:{log(){},error(...v){diagnostics.push(v.join(' '))}},async listSavedFdids(){return []},async cleanupSavedEform(){calls.push('instance.cleanup')},async cleanupImportedEform(){calls.push('template.cleanup')},
  removeOwnedPatient(){calls.push('patient.cleanup')},redactSensitiveFailureText:String,buildFailureDetails:()=>({})};
 vm.createContext(sandbox);await vm.runInContext(source.slice(start,end)+';main();',sandbox);
 assert.equal(processRef.exitCode,failureMode==='none'?undefined:1,diagnostics.join('\n'));
 if(failureMode==='create'||failureMode==='name-collision'){assert(calls.includes('sql.dispose'));assert(!calls.includes('sql.delete'));assert(!calls.includes('template.import'));if(failureMode==='create')assert(!calls.includes('patient.insert'));continue;}assert(calls.indexOf('intent')<calls.indexOf('patient.insert'));
 assert(calls.indexOf('bind')<calls.indexOf('positive'));assert(calls.indexOf('negativeWorkflowCompleted')<calls.indexOf('drain'));
 assert(calls.indexOf('browser.close')<calls.indexOf('patient.cleanup'));if(failClosedReceipt)assert(!calls.includes('cleanupAcknowledged'));else{assert(calls.indexOf('patient.cleanup')<calls.indexOf('cleanupAcknowledged'));assert(calls.indexOf('cleanupAcknowledged')<calls.indexOf('close'));}
 assert(calls.includes('sql.dispose'));assert(calls.includes('close'));
 }
});
test('actual proof-mode wrapper cannot turn internal workflow or cleanup exit1 into raw PASS',async()=>{
 const vm=require('node:vm');const source=fs.readFileSync(path.join(__dirname,'eform-apcache-renderer-playwright-checks.js'),'utf8');const tail=source.slice(source.lastIndexOf("if (process.env.APCACHE_NEGATIVE_FAULT_ARTIFACT_DIR)"));
 for(const code of [undefined,1]){let options;const sandbox={process:{env:{APCACHE_NEGATIVE_FAULT_ARTIFACT_DIR:'/tmp/private'},exitCode:code},h:{runCheck:o=>{options=o}},main:async()=>{},assert};vm.createContext(sandbox);vm.runInContext(tail,sandbox);assert.equal(options.name,'eform-apcache-renderer');if(code)await assert.rejects(()=>options.run());else await options.run();}
});

test('actual generated positive and negative forms include two shipped literal lookups without changing source fixture',t=>{
 const vm=require('node:vm');const source=fs.readFileSync(path.join(__dirname,'eform-apcache-renderer-playwright-checks.js'),'utf8');
 const constants=source.slice(source.indexOf('const LITERAL_LOOKUPS = ['),source.indexOf('\n];',source.indexOf('const LITERAL_LOOKUPS = ['))+3);
 const start=source.indexOf('function writeFixture(');const end=source.indexOf('\nasync function uploadEform',start);
 const actualFixture=path.join(__dirname,'fixtures/eform/apcache-renderer-bridge.html');
 const original=sha(fs.readFileSync(actualFixture));const d=dir(t);
 const sandbox={fs,path,assert,config:{fixtureHtmlPath:actualFixture},PROVIDER_KEY:'current_user_fname_lname',TODAY_KEY:'today'};
 vm.createContext(sandbox);vm.runInContext(constants+'\n'+source.slice(start,end)+';globalThis.write=writeFixture;',sandbox);
 for(const missing of [null,intent.missingKey]){const file=sandbox.write(d,missing?'negative':'positive',missing);const html=fs.readFileSync(file,'utf8');
  assert(html.includes("apCache.lookup('first_last_name');"));assert(html.includes("apCache.lookup('addressline');"));
  assert(html.includes('id="apcache_literal_name" readonly'));assert(html.includes('id="apcache_literal_address" readonly'));
  assert(!html.includes('name="apcache_literal_name"'));assert(!html.includes('name="apcache_literal_address"'));
  for(const name of ['apcache_provider','apcache_today','subject']) assert(html.includes('name="'+name+'"'));
  assert(html.includes('name="apcache_missing"')===Boolean(missing));
  assert.equal(html.includes("apCache.lookup('"+intent.missingKey+"');"),Boolean(missing));}
 assert.equal(sha(fs.readFileSync(actualFixture)),original);
});
test('actual native literal assertion rejects old cross-alias substitutions and executable-node evidence',async()=>{
 const vm=require('node:vm');const source=fs.readFileSync(path.join(__dirname,'eform-apcache-renderer-playwright-checks.js'),'utf8');
 const start=source.indexOf('async function assertLiteralLookups(');const end=source.indexOf('\nasync function downloadPdf',start);
 const literal={key:'first_last_name',id:'apcache_literal_name',expected:'${last_name} Fake-pw0123456789abcdef'};
 const sandbox={assert,LITERAL_LOOKUPS:[literal],readViewerValue:async()=>literal.expected};vm.createContext(sandbox);vm.runInContext(source.slice(start,end)+';globalThis.invoke=assertLiteralLookups;',sandbox);
 const valid={responseCount:1,responseValue:literal.expected,cacheValue:literal.expected,fieldValue:literal.expected,injectedNodes:0,projectionReadOnly:true,projectionUnnamed:true,projectionSubmitted:false,ordinaryFieldsSubmitted:true};
 const page={__ownedLiteralLookups:new Map([['first_last_name',Promise.resolve({html:'private response'})]]),evaluate:async()=>valid};
 assert.equal(await sandbox.invoke(page),1);
 for(const [field,value]of [['responseValue','Z9 Z9'],['cacheValue','Z9 Z9'],['fieldValue','Z9 Z9'],['injectedNodes',1],['responseCount',0],['projectionReadOnly',false],['projectionUnnamed',false],['projectionSubmitted',true],['ordinaryFieldsSubmitted',false]]){
  page.evaluate=async()=>({...valid,[field]:value});await assert.rejects(()=>sandbox.invoke(page));}
});

test('actual markup-to-benign transition is constrained by exact patient marker provider and prior values',()=>{
 const vm=require('node:vm');const source=fs.readFileSync(path.join(__dirname,'eform-apcache-renderer-playwright-checks.js'),'utf8');const start=source.indexOf('function prepareLiteralSave('),end=source.indexOf('\nasync function checkPositiveRender',start);
 const lookups=[{},{expected:'render-only'}];const sandbox={assert,h:{sqlString:s=>"'"+s+"'"},LITERAL_LOOKUPS:lookups};vm.createContext(sandbox);vm.runInContext(source.slice(start,end)+';globalThis.invoke=prepareLiteralSave;',sandbox);
 const queries=[];const owner={patient:'55',marker:intent.ownedMarker,provider:'999998'};sandbox.invoke({value:q=>{queries.push(q);return '1'}},owner);
 assert(queries[0].includes('demographic_no=55'));assert(queries[0].includes("last_name='"+intent.ownedMarker+"'"));assert(queries[0].includes("provider_no='999998'"));
 assert(queries[0].includes("first_name='${last_name}'"));assert(queries[0].includes("address='<img id=apInjected>${city}'"));assert(queries[0].includes("city='OWNED-CITY'"));
 assert.equal(lookups[1].expected,'Literal ${city}, OWNED-CITY, ON, A1A1A1');
 lookups[1].expected='unchanged';assert.throws(()=>sandbox.invoke({value:()=> '0'},owner));assert.equal(lookups[1].expected,'unchanged');
});
test('actual positive native flow proves markup before transition and saves only the fresh benign viewer',async()=>{
 const vm=require('node:vm');const source=fs.readFileSync(path.join(__dirname,'eform-apcache-renderer-playwright-checks.js'),'utf8');const start=source.indexOf('async function checkPositiveRender('),end=source.indexOf('\n/** The value of the hidden input',start);
 const calls=[];const lookups=[{key:'first_last_name',expectedPdf:'${last_name} Fake-pw0123456789abcdef'},{key:'addressline',expectedPdf:'render-only'}];
 const sandbox={assert,assertSavedClinicalFields(){calls.push('persistedClinicalFields')},LITERAL_LOOKUPS:lookups,PROVIDER_KEY:'current_user_fname_lname',TODAY_KEY:'today',
  async openAddEform(_,__,label){calls.push(label);return {async close(){calls.push('close')}}},
  async readViewerValue(_,key){return key==='today'?'2026-10-19':'SYNTHETIC'},async assertLiteralLookups(){calls.push('literalDOM');return 2},
  prepareLiteralSave(){calls.push('transition');lookups[1].expectedPdf='Literal ${city}, OWNED-CITY, ON, A1A1A1'},
  async downloadPdf(){calls.push('nativeSavePDF');return '/tmp/owned.pdf'},pdfText(){return 'APCACHE-VALUE[current_user_fname_lname]=SYNTHETIC APCACHE-VALUE[today]=2026-10-19 '+lookups.map(l=>'APCACHE-VALUE['+l.key+']='+l.expectedPdf).join(' ')},async savedFdidOnPage(){return '7'}};
 vm.createContext(sandbox);vm.runInContext(source.slice(start,end)+';globalThis.invoke=checkPositiveRender;',sandbox);const result=await sandbox.invoke({},'5','run',new Set(),{},{});
 assert.equal(result.literalLookupControlsVerified,2);assert(calls.indexOf('literalDOM')<calls.indexOf('transition'));
 assert(calls.indexOf('transition')<calls.indexOf('apcache-positive-save'));assert(calls.indexOf('apcache-positive-save')<calls.indexOf('nativeSavePDF'));
 assert.equal(calls.filter(c=>c==='literalDOM').length,3);
});

test('actual saved-field assertion binds exact owned form and persists only genuine clinical controls',()=>{
 const vm=require('node:vm');const source=fs.readFileSync(path.join(__dirname,'eform-apcache-renderer-playwright-checks.js'),'utf8');
 const start=source.indexOf('function assertSavedClinicalFields('),end=source.indexOf('\nasync function checkPositiveRender',start);
 const sandbox={assert,Buffer};vm.createContext(sandbox);vm.runInContext(source.slice(start,end)+';globalThis.invoke=assertSavedClinicalFields;',sandbox);
 const queries=[];let calls=0;const sql={value:q=>{queries.push(q);return ++calls===3?'0':'1'}};
 sandbox.invoke(sql,'7','5',{patient:'55'},'Owned Provider','2026-10-19');assert.equal(queries.length,3);
 for(const q of queries){assert(q.includes('fdid=7 AND fid=5 AND demographic_no=55'));assert(!/DELETE|UPDATE|INSERT/.test(q));}
 for(const failIndex of [1,2,3]){let index=0;assert.throws(()=>sandbox.invoke({value:()=>++index===failIndex?(failIndex===3?'1':'0'):(index===3?'0':'1')},'7','5',{patient:'55'},'Owned Provider','2026-10-19'));}
 assert.throws(()=>sandbox.invoke({value:()=>{throw Error('must not query')}},'invalid','5',{patient:'55'},'Owned Provider','2026-10-19'));
});

const julWarning='19-Oct-2026 20:08:33.500 WARNING [eform-render-driver-start] org.openqa.selenium.devtools.CdpVersionFinder.findNearestMatch Unable to find CDP implementation matching 154\n';
test('JUL warning is a separate record after an owned ERROR and preserves the complete fault proof',()=>{
 const raw=errors().replace(' fid=5\n',' fid=5\n'+julWarning);const before=sha(raw);
 assert.deepEqual(verifyApNegativeFaultWindow(raw,valid(),sources,audit()),{intentionalLookupFaultEvents:2,actualTokenlessPostFaultEvents:1,allOtherErrorsRefused:true,rawStrictAuditUnmodified:true});assert.equal(sha(raw),before);
});
test('structured non-error JUL records before between and after Log4j errors are not stack continuations',()=>{
 const raw=julWarning+errors().replace(' fid=5\n',' fid=5\n'+julWarning.replace('WARNING','INFO'))+julWarning;
 assert.equal(verifyApNegativeFaultWindow(raw,valid(),sources,audit()).intentionalLookupFaultEvents,2);
});
for(const [name,extra]of Object.entries({
 'JUL severe':julWarning.replace('WARNING','SEVERE'),
 'unknown JUL level':julWarning.replace('WARNING','ERROR'),
 'unrecognized JUL month':julWarning.replace('-Oct-','-Unknown-'),
 'malformed JUL header':julWarning.replace('[eform-render-driver-start]','eform-render-driver-start'),
 'trace after JUL warning':julWarning+'java.lang.NullPointerException: unexpected\n',
 'unknown continuation after ERROR':'java.lang.IllegalStateException: unexpected\n',
 'unstructured ERROR before first record':'ERROR unexpected journal failure\n',
 'HHH diagnostic inside JUL warning':julWarning.replace('Unable to find CDP implementation matching 154','HHH000099 unexpected assertion'),
 'extra Log4j ERROR after JUL warning':julWarning+'2026-10-19 20:08:44,900 ERROR Other.Action (Action.java:1) - unexpected\n',
}))test('JUL boundary refuses '+name,()=>assert.throws(()=>verifyApNegativeFaultWindow(extra+errors(),valid(),sources,audit())));
