/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const {EventEmitter}=require('node:events');
const h=require('./lib/playwright-harness');
const {createGracefulSignalCancellation}=require('./graceful-signal-cancellation');
const source=fs.readFileSync(path.join(__dirname,'deb-login-playwright-checks.js'),'utf8');
for(const signal of ['SIGINT','SIGTERM'])for(const uncertain of [false,true]) {
  test(`packaged reset retains its generated credential after ${signal}, ${uncertain?'unconfirmed':'confirmed'} commit`,async()=>{
    const output=[],events=[];
    const signalProcess=Object.assign(new EventEmitter(),{env:{ADMIN_USER:'operator',ADMIN_PASSWORD:'old-synthetic',ADMIN_PIN:'1234'}});
    const cancellation=createGracefulSignalCancellation({signalProcess});
    let current='https://127.0.0.1/carlos/',logins=0;
    const locator={async fill(){},async waitFor(){},async count(){return 1;},first(){return this;},
      async innerText(){return 'invalid login or reset form';},async getAttribute(){return '/carlos/forcepasswordresetSubmit';}};
    const page={locator:()=>locator,url:()=>current,on(){},async goto(){return {status:()=>200,headers:()=>({'strict-transport-security':'max-age=1'})};},
      async waitForURL(){},async waitForLoadState(){},async content(){return '/csrfguard Schedule';}};
    const browser={async newContext(){return {async newPage(){return page;}};},
      async close(){events.push('closed');assert(output.includes('generated password: generated-synthetic-recovery'));
        throw new Error('injected browser shutdown failure');}};
    await vm.runInNewContext(source,{URL,process:signalProcess,console:{log:value=>output.push(value),error(){}},require(name){
      if(name==='./lib/legacy-check')return {runLegacyCheck:(_name,operation)=>operation({cancellation})};
      if(name==='./lib/playwright-harness')return {...h,launchBrowser:async()=>browser};
      if(name==='./lib/deb-login-password')return {generatedPassword:()=> 'generated-synthetic-recovery'};
      if(name==='./lib/deb-login-navigation')return {
        LOGIN_NAVIGATION_TIMEOUT_MS:100,
        async submitLoginDocument(_page,_button,{label}) {
          if(label==='the login button'){if(++logins===2)current='https://127.0.0.1/carlos/forcepasswordreset';return;}
          assert(output.includes('generated password: generated-synthetic-recovery'),'Recovery must be reported before the accepted write');
          events.push('reset committed');signalProcess.emit(signal);await Promise.resolve();
          if(uncertain)throw new Error('response lost after commit');
          current='https://127.0.0.1/carlos/provider/providercontrol';
        },async waitForProviderSchedule(){},
      };
      return require(name);
    }});
    cancellation.dispose();
    assert.deepEqual(events,['reset committed','closed']);
    assert(output.includes('generated password: generated-synthetic-recovery'));
    assert(output.some(line=>uncertain?/outcome is unconfirmed/.test(line):/password is now/.test(line)));
    assert(!output.some(line=>line.includes('fresh session')),'Cancellation must prevent later logins');
  });
}
