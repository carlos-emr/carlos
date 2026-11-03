/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert=require('node:assert/strict');
const test=require('node:test');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const h=require('./lib/playwright-harness');
const accessibility=require('./accessibility-smoke-playwright-checks');
const heartbeat=require('./session-heartbeat-timeout-playwright-checks');

test('axe resolution classifies a missing dependency as SKIP and unexpected errors as failures',()=>{
  assert.throws(()=>accessibility.preflightConfig({resolve(){throw Object.assign(new Error('missing'),{code:'MODULE_NOT_FOUND'});}}),
    error=>error.name==='SkipCheck'&&/axe-core/.test(error.message));
  assert.doesNotThrow(()=>accessibility.preflightConfig({resolve(){return '/installed/axe.min.js';}}));
  assert.throws(()=>accessibility.preflightConfig({resolve(){throw new Error('corrupt dependency');}}),/corrupt dependency/);
});

test('clinic-wide heartbeat prerequisite classification requires both disposable and exclusive gates',()=>{
  for(const env of [{},{CARLOS_DISPOSABLE_VM:'true'},{EXCLUSIVE:'1'}]) {
    assert.throws(()=>heartbeat.preflightConfig({env}),error=>error.name==='SkipCheck'&&/requires/.test(error.message));
  }
  assert.doesNotThrow(()=>heartbeat.preflightConfig({env:{CARLOS_DISPOSABLE_VM:'true',EXCLUSIVE:'1'}}));
});

test('runCheck reports prerequisite as exit 2 and still makes cleanup failure fatal',async()=>{
  for(const cleanupFails of [false,true]) {
    const processRef={on(){},removeListener(){}};let cleaned=false;
    const result=await h.runCheck({name:'test-prerequisite',processRef,log(){},logError(){},
      run(){accessibility.preflightConfig({resolve(){throw Object.assign(new Error('missing'),{code:'MODULE_NOT_FOUND'});}});},
      cleanup(){cleaned=true;if(cleanupFails)throw new Error('owned fixture retained');}});
    assert.equal(cleaned,true);
    assert.equal(result.outcome,cleanupFails?'FAIL':'SKIP');
    assert.equal(processRef.exitCode,cleanupFails?1:2);
  }
});

test('CDS import missing source or disabled feature skips before upload; transport errors remain failures',async()=>{
  const source=fs.readFileSync(path.join(__dirname,'demographic-cds-import-playwright-checks.js'),'utf8');
  const start=source.indexOf('async function openImportFrame(');
  const helper=source.slice(start,source.indexOf('\n}',start)+2);
  for(const state of [{disabled:true},{source:null},{error:true},{source:'approved-source'},
    {disabled:true,diagnostic:true},{source:null,diagnostic:true},{source:'approved-source',responseError:true},
    {disabled:true,responseError:true},{source:null,responseError:true},
    {disabled:true,statusCode:503},{source:null,statusCode:403}]) {
    let selected=false;
    const frame={locator(selector){return {
      waitFor:async()=>{},isDisabled:async()=>Boolean(state.disabled),
      evaluateAll:async()=>{if(state.error)throw new Error('source transport failed');return state.source;},
      selectOption:async()=>{selected=true;},isChecked:async()=>false,
    };},waitForFunction:async()=>{}};
    const admin={locator(selector){return {first(){return this;},click:async()=>{},waitFor:async()=>{},
      elementHandle:async()=>({contentFrame:async()=>frame})};},
    waitForResponse:async()=>{if(state.responseError)throw new Error('response transport failed');return {status:()=>state.statusCode||200};},
    waitForLoadState:async()=>{}};
    const context={h,clickOpensPopupOrNavigates:async()=>({page:admin}),
      observeCdsMetadata:async()=>({transport:{qualifyResponse:async()=>({qualified:false})}}),
      revealAuditLink:async()=>{},createCdsMetadataProver:()=>({}),importApi(){},qualifyCdsMetadata:async()=>{}};
    vm.createContext(context);vm.runInContext(helper,context);
    const recorder=h.createRecorder();
    if(state.diagnostic)recorder.pageErrors.push({label:'import',text:'synthetic uncaught error'});
    const operation=context.openImportFrame({recorder,schedule:{locator(){return {first(){return this;}};}}});
    if(state.error)await assert.rejects(operation,/source transport failed/);
    else if(state.responseError)await assert.rejects(operation,/response transport failed/);
    else if(state.statusCode)await assert.rejects(operation,/Initial owner-scoped upload status was refused/);
    else if(state.diagnostic)await assert.rejects(operation,error=>error.name!=='SkipCheck'&&/JavaScript-layer/.test(error.message));
    else if(state.disabled||!state.source)await assert.rejects(operation,error=>error.name==='SkipCheck'&&/Required/.test(error.message));
    else await assert.doesNotReject(operation);
    assert.equal(selected,Boolean(state.source)&&!state.responseError&&!state.statusCode);
  }
});

test('validation image pins axe to the same version as the repository dependency',()=>{
  const source=fs.readFileSync(path.join(__dirname,'deb-docker-validation.sh'),'utf8');
  const pinned=require('../package.json').devDependencies['axe-core'];
  assert.equal(source.match(/^AXE_CORE_VERSION="([^"]+)"$/m)?.[1],pinned);
  assert.match(source,/"axe-core@\$\{AXE_CORE_VERSION\}"/);
  assert.match(source,/--build-arg AXE_CORE_VERSION="\$AXE_CORE_VERSION"/);
  assert.match(source,/^export CARLOS_DISPOSABLE_VM=true EXCLUSIVE=1 /m);
});
