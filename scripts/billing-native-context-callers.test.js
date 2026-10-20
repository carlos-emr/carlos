/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const vm = require('node:vm');
const {test} = require('node:test');
const CALLERS = process.env.BILLING_NATIVE_CALLER_ROOT || __dirname;
const OWNED = Object.freeze({providerNo:'970001',ohipNo:'960001',multisites:true,clinic:'FAKE Owned Clinic'});
const STOP_NATIVE = Error('test: native context prefix completed');
const STOP_CLAIM = Error('test: exact owned claim prefix completed');
const STOP_REPORT = Error('test: explicit complete report submission');
const h = {assert:(ok,message)=>assert(ok,message),sqlString:value=>"'"+String(value).replaceAll("'","''")+"'",SkipCheck:class SkipCheck extends Error{}};

function load(name, overrides={}, {entry=false, sandbox={}}={}) {
  const filename=path.join(__dirname,name), module={exports:{}};
  function req(id) {
    if(Object.hasOwn(overrides,id))return overrides[id];
    if(id==='./lib/playwright-harness'||id==='./playwright-harness')return h;
    if(id.startsWith('node:'))return require(id);
    if(id==='./lib/workflow-session')return {runWorkflow(){}};
    if(id==='./lib/playwright-ui'||id==='./playwright-ui'||id==='./lib/playwright-link-audit')return {};
    if(id==='../master-record-tabs-playwright-checks'||id==='../echart-navbar-modules-playwright-checks')return {};
    throw Error('Unexpected pure-control import '+id);
  }
  req.main=entry?module:null;
  const context={require:req,module,exports:module.exports,process:{env:{}},console,URL,Date,setTimeout,clearTimeout,...sandbox};
  const source=fs.readFileSync(entry?path.join(CALLERS,name):filename,'utf8');
  vm.createContext(context);vm.runInContext(source,context,{filename});
  return {api:module.exports,context};
}
const siteApi=load('lib/billing-site-context.js').api;
const reportApi=load('lib/billing-report-context.js').api;
const finalizer=load('lib/workflow-session.js').api.cleanupOwnedWorkflow;
function shared(overrides={}) {
  return load('billing-on-invoice-third-party-playwright-checks.js',{
    './lib/billing-site-context':siteApi,'./lib/billing-report-context':reportApi,
    './lib/owned-billing-physician':{},'./lib/master-record-link':{},...overrides,
  }).api;
}

function browserSession(t, {browserCloseFails=false, claimCleanupFails=false, raCleanupFails=false, stale=false}={}) {
  const events=[], cleanups=[], headers=new Map();
  let connected=true, providerExists=false, configExists=false, raExists=false, patientExists=true, nextId=40;
  const browser={isConnected:()=>connected,async close(){events.push('browser-close');if(browserCloseFails)throw Error('browser close refused');connected=false;}};
  const sql={
    rows(query) {
      if(query.includes('information_schema.columns'))return ['provider_no','ohip_no','last_name','first_name','status','comments'].map(x=>[x]);
      if(/^SELECT id FROM billing_on_cheader1/.test(query))return [...headers.keys()].map(id=>[id]);
      if(/^SELECT id FROM billing_on_item/.test(query))return [['100']];
      if(/^SELECT s.site_id FROM providersite/.test(query))return [];
      throw Error('Unexpected rows expression '+query);
    },
    value(query) {
      if(/^INSERT INTO billing_on_cheader1/.test(query)) {
        events.push('header-insert');const id=String(++nextId);headers.set(id,query);return id;
      }
      if(/^INSERT INTO billing_on_item/.test(query))return String(++nextId);
      if(/^INSERT INTO raheader/.test(query)){raExists=true;return'80';}
      if(/^SELECT provider_no FROM provider/.test(query))return'999998';
      if(/^SELECT ohip_no FROM provider/.test(query))return'123456';
      if(/^SELECT clinic_location_no/.test(query))return'0000';
      if(/^SELECT value FROM billingservice/.test(query))return'12.00';
      if(/^SELECT hin FROM demographic/.test(query))return'9000000001';
      if(/^SELECT COUNT\(\*\) FROM demographic WHERE demographic_no=/.test(query))return patientExists?'1':'0';
      if(/^SELECT COUNT\(\*\) FROM provider WHERE provider_no='970001'/.test(query))return stale&&query.includes("AND status='1'")?'0':providerExists?'1':'0';
      if(/^SELECT COUNT\(\*\) FROM provider WHERE provider_no=/.test(query))return stale?'0':'1';
      if(/^SELECT COUNT\(DISTINCT site.site_id\)/.test(query))return stale?'0':'1';
      if(/^SELECT COUNT\(\*\) FROM billing_on_cheader1 WHERE demographic_no=/.test(query))return String(headers.size);
      if(/^SELECT COUNT\(\*\) FROM billing_on_cheader1 WHERE id=/.test(query))return'1';
      if(/^SELECT COUNT\(\*\) FROM billing_on_cheader1 WHERE provider_no=/.test(query))return String(headers.size);
      if(/^SELECT COUNT\(\*\) FROM billingservice WHERE service_code IN/.test(query))return configExists?'2':'0';
      if(/^SELECT COUNT\(\*\) FROM diagnosticcode WHERE description LIKE/.test(query))return configExists?'2':'0';
      if(query.includes('FROM raheader WHERE payable=')&&query.startsWith('SELECT ('))return raExists?'1':'0';
      if(query.includes('FROM provider WHERE provider_no=')&&query.startsWith('SELECT ('))return providerExists?'1':'0';
      if(query.includes('FROM billing_on_cheader1 WHERE id=')&&query.startsWith('SELECT (')){
        const match=query.match(/FROM billing_on_cheader1 WHERE id=(\d+)/);return headers.has(match[1])?'1':'0';
      }
      return'0';
    },
    execute(query) {
      if(query.startsWith('INSERT INTO billingservice'))configExists=true;
      if(query.startsWith('INSERT INTO provider')){providerExists=true;events.push('provider-insert');}
      if(query.startsWith('DELETE FROM radetail')){events.push('ra-cleanup');if(raCleanupFails)throw Error('RA cleanup refused');raExists=false;}
      if(query.startsWith('DELETE FROM billing_on_repo')) {
        events.push('claim-cleanup');if(claimCleanupFails)throw Error('claim cleanup refused');
        const found=query.match(/DELETE FROM billing_on_cheader1 WHERE id=(\d+)/);assert(found);headers.delete(found[1]);
      }
      if(query.startsWith('DELETE FROM ctl_billingservice')){events.push('config-cleanup');configExists=false;}
      if(query.startsWith('DELETE FROM providersite')){events.push('provider-cleanup');providerExists=false;}
      if(query.startsWith('DELETE FROM demographic WHERE')){events.push('patient-cleanup');patientExists=false;}
    },
    dispose(){events.push('sql-dispose');},
  };
  const s={sql,patient:'777',provider:'999998',marker:'FAKE-PW0123456789abcdef',context:{browser:()=>browser},
    cleanup:fn=>cleanups.push(fn),master:{getByRole:()=>({first(){return this;},count:async()=>1})},
    schedule:{locator:()=>({first(){return this;}})}};
  async function finish(){return finalizer({browser,sql,patient:s.patient,marker:s.marker,cleanups});}
  return {s,sql,browser,events,cleanups,headers,finish,get providerExists(){return providerExists;},
    set providerExists(value){providerExists=value;}};
}

function invoice({foreign=false}={}) {
  let clinic='none', provider='';
  const options=values=>({evaluateAll:async fn=>fn(values.map(value=>({value,disabled:false,closest:()=>null})))});
  const sites={count:async()=>1,locator:()=>options(['none',OWNED.clinic]),inputValue:async()=>clinic,selectOption:async value=>{clinic=value;}};
  const providers={waitFor:async()=>{},count:async()=>1,locator:()=>options(clinic==='none'?[]:[foreign?'999998|123456':OWNED.providerNo+'|'+OWNED.ohipNo]),inputValue:async()=>provider,selectOption:async value=>{provider=value;}};
  let closed=false;
  return {url:()=> 'https://example.invalid/carlos/billing?demographic_no=777',isClosed:()=>closed,close:async()=>{closed=true;},
    locator(selector){if(selector==='select[name="site"]')return sites;if(selector==='select[name="xml_provider"]')return providers;
      if(selector==='select[name="xml_billtype"]')return{waitFor:async()=>{}};
      throw STOP_NATIVE;}};
}

// Use the actual physician factory, durable private ledger, dependency wrapper,
// and browser-drain guards. Only its DB prepare/cleanup operations are doubled;
// SQL row/schema behavior belongs to its existing dedicated fixture tests.
function physicianFactory(t,c) {
  const directory=fs.mkdtempSync(path.join(os.tmpdir(),'billing-native-caller-'));
  fs.chmodSync(directory,0o700);t.after(()=>fs.rmSync(directory,{recursive:true,force:true}));
  const loaded=load('lib/owned-billing-physician.js',{}, {sandbox:{process:{env:{RESULT_JSON:path.join(directory,'result.json')},getuid:()=>process.getuid()}}});
  loaded.context.prepare=(_sql,owner,save)=>{c.events.push('physician-reserved');c.providerExists=true;save({status:'ACTIVE',providerNo:OWNED.providerNo,ohipNo:OWNED.ohipNo,siteId:'1',siteName:OWNED.clinic,authority:owner});};
  loaded.context.cleanup=(_sql,_owner,state,save)=>{c.events.push('physician-cleanup');c.providerExists=false;state.status='CLEANED';save(state);};
  return loaded.api.createOwnedBillingPhysician;
}

async function adminRun(t, options={}) {
  const c=browserSession(t,options), page=invoice(options), factory=physicianFactory(t,c);
  const actualShared=shared();
  const module=load('billing-on-admin-config-playwright-checks.js',{
    'node:crypto':{randomInt:(()=>{let n=0;return max=>(++n)%max;})()},
    './lib/master-record-link':{masterRecordLink:()=>({first(){return this;},count:async()=>1})},
    './lib/playwright-ui':{clickOpensPopupOrNavigates:async()=>({page:{}})},
    './lib/owned-billing-physician':{createOwnedBillingPhysician:factory},
    './lib/billing-site-context':{...siteApi,selectBillingSiteAndProvider:async(...args)=>{const context=await siteApi.selectBillingSiteAndProvider(...args);c.events.push('native-context');return context;}},
    './billing-on-invoice-third-party-playwright-checks':{...actualShared,openHistory:async()=>{throw STOP_CLAIM;}},
  },{entry:true});
  c.s.popup=async()=>page;
  c.s.step=async(label,body)=>{
    if(label==='the bill form offers the owned form with its codes, private code and location') {
      try{await body();}catch(error){if(error!==STOP_NATIVE)throw error;}
    } else if(label.startsWith('correction ▸ service code Search')) await body();
  };
  let error;try{await module.api.workflow(c.s);}catch(e){error=e;}
  return Object.assign(c,{error,page});
}

test('admin actual caller binds native owned context before its sole header INSERT',async t=>{
  const c=await adminRun(t);assert.equal(c.error,STOP_CLAIM);
  assert(c.events.indexOf('physician-reserved')<c.events.indexOf('native-context'));
  assert(c.events.indexOf('native-context')<c.events.indexOf('header-insert'));
  assert.equal(c.headers.size,1);
  const query=[...c.headers.values()][0];assert(query.endsWith("'999998', 'FAKE Owned Clinic'); SELECT LAST_INSERT_ID()"));assert(query.includes("'970001'"));
  await c.finish();
});
test('admin actual caller rejects foreign native physician before any header',async t=>{
  const c=await adminRun(t,{foreign:true});assert(c.error&&c.error!==STOP_CLAIM);assert.equal(c.headers.size,0);await c.finish();
});
test('admin actual caller revalidates context and refuses stale provider before header',async t=>{
  const c=await adminRun(t,{stale:true});assert(c.error&&c.error!==STOP_CLAIM);assert.equal(c.headers.size,0);await c.finish();
});
test('admin actual seeder, physician gate and workflow finalizer clean child before physician and patient',async t=>{
  const c=await adminRun(t);assert.equal(c.error,STOP_CLAIM);await c.finish();
  for(const [a,b]of[['browser-close','claim-cleanup'],['claim-cleanup','physician-cleanup'],['physician-cleanup','patient-cleanup']])assert(c.events.indexOf(a)>=0&&c.events.indexOf(a)<c.events.indexOf(b));
  assert.equal(c.headers.size,0);assert.equal(c.providerExists,false);
});
test('admin actual dependency wrapper retains physician and patient after claim cleanup refusal',async t=>{
  const c=await adminRun(t,{claimCleanupFails:true});assert.equal(c.error,STOP_CLAIM);await assert.rejects(c.finish(),/claim cleanup refused/);
  assert(c.providerExists);assert.equal(c.headers.size,1);assert(!c.events.includes('physician-cleanup'));assert(!c.events.includes('patient-cleanup'));
});
test('admin actual caller guards every child under the default finalizer when browser drain is unconfirmed',async t=>{
  const c=await adminRun(t,{browserCloseFails:true});assert.equal(c.error,STOP_CLAIM);await assert.rejects(c.finish(),/browser close/);
  assert(!c.events.some(x=>['claim-cleanup','config-cleanup','physician-cleanup','patient-cleanup'].includes(x)));
});

function reportFrame({foreign=false,duplicate=false,disabled=false}={}) {
  const state={site:'none',provider:'',options:[],start:'2026-01-01',end:'2026-10-20',fields:{},all:true,autoSubmits:0,submitted:null,events:[]};
  const option=value=>typeof value==='string'?{value,disabled:false,closest:()=>null}:({...value,closest:()=>null});
  const providers={waitFor:async()=>{},count:async()=>1,last(){return this;},locator:()=>({evaluateAll:async fn=>fn(state.options.map(option))}),inputValue:async()=>state.provider,
    selectOption:async value=>{state.events.push('provider');state.provider=value;if(state.start&&state.end)state.autoSubmits++;}};
  const sites={count:async()=>1,locator:()=>({evaluateAll:async fn=>fn(['none',OWNED.clinic].map(option))}),inputValue:async()=>state.site,
    selectOption:async value=>{state.events.push('site');state.site=value;state.options=foreign?['all','999998']:[{value:OWNED.providerNo,disabled},...(duplicate?[OWNED.providerNo]:[])];}};
  return {state,waitForLoadState:async()=>{},locator(selector){
    if(selector.includes('providerview'))return providers;
    if(selector==='select[name="site"]')return sites;
    if(selector==='#xml_vdate'||selector==='#xml_appointment_date'){
      const key=selector==='#xml_vdate'?'start':'end';return{fill:async value=>{state.events.push(key+':'+value);state[key]=value;},inputValue:async()=>state[key]};
    }
    if(selector==='body')return{click:async()=>{}};
    if(selector==='#ALL')return{isChecked:async()=>state.all,click:async()=>{state.all=!state.all;}};
    if(selector.includes('Create Report'))return{click:async()=>{state.submitted={...state,fields:{...state.fields}};throw STOP_REPORT;}};
    return {fill:async value=>{state.fields[selector]=value;},check:async()=>{state.fields[selector]=true;},selectOption:async value=>{state.fields[selector]=value;}};
  }};
}

async function filtersRun(t, options={}) {
  const c=browserSession(t,options), page=invoice(options), frame=reportFrame(options.reportOptions||{});
  const actualShared=shared();
  const fixture=load('billing-on-ohip-simulation-report-playwright-checks.js',{
    'node:crypto':{randomInt:()=>1},'./lib/billing-site-context':siteApi,
    './billing-on-group-disk-zero-total-playwright-checks':{},
  }).api;
  let runOptions;
  const module=load('gap-billing-invoice-report-filters-playwright-checks.js',{
    './lib/workflow-session':{runWorkflow(_name,_workflow,value){runOptions=value;}},
    './billing-on-invoice-third-party-playwright-checks':actualShared,
    './lib/billing-report-context':reportApi,
    './billing-on-ohip-simulation-report-playwright-checks':{...fixture,
      openAdministration:async()=>({waitForEvent:async()=>{}}),openAdminFrame:async()=>frame},
  },{entry:true});
  c.s.popup=async()=>{c.events.push('native-invoice');return page;};
  c.s.step=async(label,body)=>{if(label.startsWith('the native invoice')||label.startsWith('the report lists each owned claim'))await body();};
  let error;try{await module.api.workflow(c.s);}catch(e){error=e;}
  return Object.assign(c,{error,page,frame,runOptions});
}

test('report-filter actual entrypoint enables the owned master required by native context capture',async t=>{
  const c=await filtersRun(t);assert.notEqual(c.runOptions.openMaster,false);await c.finish();
});
test('report-filter actual caller captures native context before all three owned claims',async t=>{
  const c=await filtersRun(t);assert.equal(c.error,STOP_REPORT);assert.equal(c.headers.size,3);
  assert(c.events.indexOf('native-invoice')<c.events.indexOf('header-insert'));
  for(const query of c.headers.values())assert(query.endsWith("'999998', 'FAKE Owned Clinic'); SELECT LAST_INSERT_ID()"));
  assert(c.page.isClosed());await c.finish();
});
test('report-filter foreign native physician is refused before any claim INSERT',async t=>{
  const c=await filtersRun(t,{foreign:true});assert(c.error&&c.error!==STOP_REPORT);assert.equal(c.headers.size,0);assert(c.page.isClosed());await c.finish();
});
test('report-filter stale native provider is refused before any claim INSERT',async t=>{
  const c=await filtersRun(t,{stale:true});assert(c.error&&c.error!==STOP_REPORT);assert.equal(c.headers.size,0);await c.finish();
});
test('actual report callback selects exact context before restoring dates and all explicit filters',async t=>{
  const c=await filtersRun(t);assert.equal(c.error,STOP_REPORT);
  const state=c.frame.state;assert.equal(state.autoSubmits,0);assert(state.submitted);
  assert.equal(state.site,OWNED.clinic);assert.equal(state.provider,OWNED.providerNo);
  assert.equal(state.start,state.end);assert.match(state.start,/^\d{4}-\d{2}-\d{2}$/);
  assert(state.events.indexOf('provider')<state.events.indexOf('start:'+state.start));
  assert.equal(state.fields['form[name="serviceform"] input[name="demographicNo"]'],'777');
  assert.equal(state.fields['form[name="serviceform"] input[name="serviceCode"]'],'%');
  assert.equal(state.fields['form[name="serviceform"] input[name="dx"]'],'');
  assert.equal(state.fields['form[name="serviceform"] input[name="raCode"]'],'');
  assert.equal(state.fields['form[name="serviceform"] input[name="claimNo"]'],'');
  assert.equal(state.fields['form[name="serviceform"] select[name="visitType"]'],'-');
  assert.equal(state.fields['#statusTypeSubmittedOHIP'],true);assert.equal(state.all,true);await c.finish();
});
for(const [label,option] of [['foreign',{foreign:true}],['duplicate',{duplicate:true}],['disabled',{disabled:true}]]) {
  test('report-filter actual callback refuses '+label+' physician options without All fallback or submission',async t=>{
    const c=await filtersRun(t,{reportOptions:option});
    assert(c.error&&c.error!==STOP_REPORT);assert.equal(c.frame.state.autoSubmits,0);
    assert.equal(c.frame.state.submitted,null);assert.notEqual(c.frame.state.provider,'all');await c.finish();
  });
}
test('report-filter actual cleanup runs RA then claims then owned provider then patient',async t=>{
  const c=await filtersRun(t);await c.finish();
  for(const [a,b] of [['browser-close','ra-cleanup'],['ra-cleanup','claim-cleanup'],['claim-cleanup','provider-cleanup'],['provider-cleanup','patient-cleanup']])assert(c.events.indexOf(a)>=0&&c.events.indexOf(a)<c.events.indexOf(b));
  assert.equal(c.headers.size,0);assert.equal(c.providerExists,false);
});
test('report-filter existing claim ownership cleanup prevents provider retirement after child refusal',async t=>{
  const c=await filtersRun(t,{claimCleanupFails:true});await assert.rejects(c.finish(),/claim cleanup refused/);
  assert(c.providerExists);assert.equal(c.headers.size,3);assert(!c.events.includes('provider-cleanup'));assert(!c.events.includes('patient-cleanup'));
});
test('report-filter RA refusal cannot retire provider after the later claim cleanup succeeds',async t=>{
  const c=await filtersRun(t,{raCleanupFails:true});await assert.rejects(c.finish(),/RA cleanup refused/);
  assert.equal(c.headers.size,0);assert(c.providerExists);assert(!c.events.includes('provider-cleanup'));assert(!c.events.includes('patient-cleanup'));
});
test('report-filter actual caller guards all callbacks under default finalizer after browser-close failure',async t=>{
  const c=await filtersRun(t,{browserCloseFails:true});await assert.rejects(c.finish(),/browser close/);
  assert(!c.events.some(x=>['ra-cleanup','claim-cleanup','provider-cleanup','patient-cleanup'].includes(x)));
});

// Actual bill-form referral picker/native self-close and second-claim contract.
{
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {test} = require('node:test');

const APP = process.env.REFERRAL_APP_ROOT || path.resolve(__dirname, '..');
const CALLERS = process.env.REFERRAL_CALLER_ROOT || __dirname;
const h = {assert:(ok,message)=>assert(ok,message), sqlString:value=>"'"+String(value).replaceAll("'","''")+"'"};
function load(file, deps, extra={}) {
  const module={exports:{}};
  const req=id=>{
    if(Object.hasOwn(deps,id))return deps[id];
    throw Error('Unexpected control import '+id);
  };
  req.main=null;
  const context={module,exports:module.exports,require:req,console,URL,Date,Math,...extra};
  vm.runInNewContext(fs.readFileSync(file,'utf8'),context,{filename:file});
  return module.exports;
}
const helpers = load(path.join(APP,'scripts/lib/popup-opener-helpers.js'),{'./playwright-harness':h});
const jsp = fs.readFileSync(path.join(APP,'src/main/webapp/WEB-INF/jsp/billing/CA/ON/searchRefDoc.jsp'),'utf8');
const callback = jsp.match(/function typeInData2\(data1, data2\) \{[\s\S]*?\n            \}/)?.[0];
assert(callback,'Native two-field referral callback missing');
const nativeCallback = callback.replace(/<carlos:encode value='\$\{refDocModel\.(fld[12])\.(formIdx|fieldId)\}' context='[^']+'\/>/g,
  (_,field,key)=>key==='formIdx'?'0':field==='fld1'?'referralCode':'referralDocName');
assert(!nativeCallback.includes('<carlos:'),'Control must substitute every native field expression');

async function run(options={}) {
  const marker='FAKE-PW0123456789abcdef',referralNo='100000';
  const events=[],cleanups=[],fields={referralCode:{value:''},referralDocName:{value:''}};
  let popupClosed=Boolean(options.preClosed),openerClosed=false,closeResolve,saveResolve;
  let persisted=null,saveClicks=0;
  const closed=new Promise(resolve=>{closeResolve=resolve;});
  const response={status:()=>options.httpStatus||200,request:()=>({method:()=> 'POST'}),url:()=> 'https://example.invalid/carlos/billing/CA/ON/BillingONSave'};
  const close=()=>{events.push('native-close');popupClosed=true;closeResolve();if(options.closeOpener)openerClosed=true;};
  const native={opener:{document:{forms:[{elements:fields}]}},self:{close}};
  vm.createContext(native);vm.runInContext(nativeCallback,native);
  const row={
    first(){return this;},
    async waitFor(){if(popupClosed)throw Error('Popup was already closed');events.push('row-visible');},
    async count(){return options.duplicateRows?2:1;},
    filter({hasText}){assert.equal(hasText,referralNo);return this;},
    async click(clickOptions){
      events.push('row-click');
      if(options.clickFailure)throw Error('Synthetic unrelated click failure');
      if(options.noUpdate){close();}
      else {
        const number=options.wrongNumber?'999999':referralNo;
        const name=options.wrongName?marker+',Foreign':marker+',Referral';
        if(options.staysOpen){fields.referralCode.value=number;fields.referralDocName.value=name;}
        else native.typeInData2(number,name);
      }
      if(options.closeRace)throw Error('locator.click: Target page, context or browser has been closed');
      if(clickOptions)assert.equal(clickOptions.noWaitAfter,true);
    },
  };
  const popup={
    isClosed:()=>popupClosed,
    locator(selector){assert.equal(selector,'#tblDocs tbody tr');return row;},
    waitForEvent(event){
      assert.equal(event,'close');events.push('close-armed');
      return options.staysOpen?Promise.reject(Error('Synthetic close timeout')):closed;
    },
  };
  const firstForm={async close(){events.push('first-form-close');}};
  const next={
    isClosed:()=>openerClosed,
    locator(selector){
      if(selector==='a[href*="referralScriptAttach2"]')return {kind:'referral-link'};
      const input=selector.match(/^input\[name="(referralCode|referralDocName)"\]$/)?.[1];
      if(input)return {async inputValue(){if(openerClosed)throw Error('Opener context closed');return fields[input].value;}};
      if(selector==='input[name="serviceCode0"]'||selector==='input[name="dxCode"]')return {async fill(value){if(openerClosed)throw Error('Opener context closed');events.push('fill '+value);}};
      if(selector==='form[name="titlesearch"] input[type="submit"][value="Save"]')return {async click(){
        if(openerClosed)throw Error('Opener context closed');saveClicks++;events.push('native-save-post');
        if(!options.noPersist&&response.status()===200)persisted={referral:options.wrongSavedRef?'999999':fields.referralCode.value,
          patient:options.foreignSavedPatient?'888':'777',count:options.duplicateClaims?2:1};
        saveResolve(response);
      }};
      throw Error('Unexpected next-form locator '+selector);
    },
    waitForResponse(predicate){
      assert.equal(predicate(response),true,'Native save POST must be observed');
      assert.equal(predicate({...response,request:()=>({method:()=> 'GET'})}),false,'A GET must not satisfy save proof');
      assert.equal(predicate({...response,url:()=> 'https://example.invalid/carlos/unrelated'}),false);
      return new Promise(resolve=>{saveResolve=resolve;});
    },
  };
  const sql={
    execute(query){assert(query.startsWith('INSERT INTO professionalSpecialists'));events.push('owned-referral-fixture');},
    value(query){
      if(query==='SELECT COUNT(*) FROM billing_on_cheader1 WHERE appointment_no=882 AND demographic_no=777')return persisted?.patient==='777'?String(persisted.count):'0';
      if(query==='SELECT ref_num FROM billing_on_cheader1 WHERE appointment_no=882')return persisted?.referral||'';
      throw Error('Unexpected claim proof '+query);
    },
  };
  let appointment=880;
  const g={registerOwnedBillCleanup(){},scheduleFee:()=>1,showSeededAppointments:async()=>{},
    openBillForm:async(_s,id)=>id==='881'?firstForm:next,chooseBillingPhysician:async()=>{},
    nextToReview:async(page)=>{assert.equal(page,next);events.push('native-next-review');}};
  const api=load(path.join(CALLERS,'gap-billing-bill-form-options-playwright-checks.js'),{
    './lib/playwright-harness':h,
    './lib/native-schedule-context':{captureScheduleContext:async()=>({}),assertScheduleContext:async()=>{},seedScheduleAppointment:()=>String(++appointment)},
    './lib/workflow-session':{runWorkflow(){}},
    './billing-on-ohip-simulation-report-playwright-checks':{createBillingFixture:()=>({providerNo:'970001'})},
    './lib/gap-billing-support':g,'./lib/popup-opener-helpers':helpers,
  },{Math:Object.assign(Object.create(Math),{random:()=>0})});
  const s={sql,patient:'777',marker,cleanup:fn=>cleanups.push(fn),
    async step(label,fn){if(label.startsWith('day sheet')||label.startsWith('Referral Doctor'))await fn();},
    async popup(page,link,label){assert.equal(page,next);assert.equal(link.kind,'referral-link');assert.equal(label,'referral-search');return popup;}};
  let error;
  try{await api.workflow(s);}catch(caught){error=caught;}
  return {error,events,saveClicks,persisted,fields,popupClosed};
}

test('actual caller + existing helper + native JSP callback tolerate dispatch-time popup self-close only after exact handoff and second save',async()=>{
  const result=await run({closeRace:true});assert.ifError(result.error);
  assert(result.events.indexOf('close-armed')<result.events.indexOf('row-click'));
  assert(result.events.indexOf('native-close')<result.events.indexOf('native-next-review'));
  assert.equal(result.fields.referralCode.value,'100000');assert.equal(result.fields.referralDocName.value,'FAKE-PW0123456789abcdef,Referral');
  assert.equal(result.saveClicks,1);assert.equal(result.persisted.referral,'100000');assert.equal(result.persisted.patient,'777');
});
test('ordinary successful click still requires the same native callback and saved claim',async()=>{const r=await run();assert.ifError(r.error);assert.equal(r.saveClicks,1);});
for(const [name,options,message] of [
  ['click failure while popup remains open',{clickFailure:true},'Synthetic unrelated click failure'],
  ['preclosed picker',{preClosed:true},'already closed'],
  ['callback fills fields but popup stays open',{staysOpen:true},'did not close'],
  ['popup closes without updating opener',{noUpdate:true,closeRace:true},'referral number'],
  ['wrong referral number despite native close',{wrongNumber:true,closeRace:true},'referral number'],
  ['same marker but wrong given name',{wrongName:true,closeRace:true},'doctor name'],
  ['opener/browser closes too',{closeOpener:true,closeRace:true},'Opener context closed'],
  ['duplicate matching rows',{duplicateRows:true},'exactly once'],
])test(name+' cannot reach Save',async()=>{const r=await run(options);assert(r.error);assert.match(r.error.message,new RegExp(message));assert.equal(r.saveClicks,0);});
for(const [name,options,message] of [
  ['HTTP500 save',{httpStatus:500},'HTTP 500'],
  ['save writes no second claim',{noPersist:true},'exactly one second claim'],
  ['save writes a foreign patient claim',{foreignSavedPatient:true},'exactly one second claim'],
  ['save writes duplicate second claims',{duplicateClaims:true},'exactly one second claim'],
  ['save keeps a different referral',{wrongSavedRef:true},'picked referral number'],
])test(name+' remains a failure after successful picker handoff',async()=>{const r=await run(options);assert(r.error);assert.match(r.error.message,new RegExp(message));assert.equal(r.saveClicks,1);});

}
