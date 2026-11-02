/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const h=require('./lib/playwright-harness');
const source=fs.readFileSync(path.join(__dirname,'lib/authz-read-probe.js'),'utf8');
for(const status of [200,403])for(const recorded of [false,true]) {
  test(`restricted schedule requires both application 403 and recorded refusal: HTTP ${status}, recorded=${recorded}`,async()=>{
    const recorder=h.createRecorder(),baseUrl=new URL('https://127.0.0.1/carlos');
    const url='https://127.0.0.1/carlos/provider/providercontrol';
    let disposed=false;
    const context={setDefaultTimeout(){}};
    const module={exports:{}};
    vm.runInNewContext(source,{URL,Buffer,module,require(name){
      if(name==='./playwright-harness')return {...h,newContext:async()=>context,login:async()=>{
        if(recorded)recorder.badResponses.push({status:403,method:'GET',url});return {url:()=>url};}};
      if(name==='./authenticated-probe')return {authenticatedProbe:async()=>({status:()=>status,
        headers:()=>({[h.APPLICATION_HEADER]:'none'}),text:async()=> 'Security Exception',dispose:async()=>{disposed=true;}})};
      if(name==='./concurrency-support')return require('./lib/concurrency-support');
      return require(name);
    }});
    const operation=module.exports.signIn({context:{browser:()=>({})},config:{baseUrl},recorder},{username:'restricted'},{allowForbiddenSchedule:true});
    if(status===403&&recorded){await operation;assert.equal(recorder.badResponses.length,0);}
    else await assert.rejects(operation,/authenticated application refusal|exactly one HTTP 403/);
    assert(disposed,'Every refusal proof response must be disposed');
  });
}
