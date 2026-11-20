/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path'),vm=require('node:vm');
const h=require('./lib/playwright-harness'),profiles=require('./lib/property-profiles');
const {preflight:upgradePreflight}=require('./deb-upgrade-canaries');
const {preflight:addressPreflight}=require('./account-lockout-address-playwright-checks');

for(const script of ['deb-upgrade-canaries.js','account-lockout-address-playwright-checks.js']) {
  test(`${script} registers refusal before SQL setup or browser/fixture creation`,async()=>{
    const module={exports:{}};let registration;
    const loader=name=>{
      if(name==='./lib/playwright-harness')return h;
      if(name==='./lib/workflow-session')return {runWorkflow:(_name,_operation,options)=>{registration=options;}};
      return {};
    };loader.main=module;
    vm.runInNewContext(fs.readFileSync(path.join(__dirname,script),'utf8'),{require:loader,module,process:{env:{EXCLUSIVE:'1'}}});
    assert.equal(typeof registration.preflightConfig,'function');
    assert.throws(()=>registration.preflightConfig(),/disposable install|managed address-lock profile/);
  });
}

test('upgrade preflight accepts only explicit disposable-host authorization',()=>{
  for(const value of [undefined,'','false','1'])assert.throws(()=>upgradePreflight({CARLOS_DISPOSABLE_HOST:value}),/disposable install/);
  assert.doesNotThrow(()=>upgradePreflight({CARLOS_DISPOSABLE_HOST:'true'}));
});

test('address-lock preflight requires the managed profile and verifies its installed mode before attempts',t=>{
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'address-preflight-'));t.after(()=>fs.rmSync(root,{recursive:true,force:true}));
  const file=path.join(root,'properties'),env={EXCLUSIVE:'1',CARLOS_DISPOSABLE_HOST:'true',CARLOS_PROPERTY_PROFILE_ACTIVE:'address-lock'};
  fs.writeFileSync(file,'login_lock=true\n');
  assert.throws(()=>addressPreflight({...env,CARLOS_PROPERTY_PROFILE_ACTIVE:undefined},file),/managed address-lock profile/);
  assert.throws(()=>addressPreflight(env,file),/do not match/);
  fs.writeFileSync(file,profiles.renderProperties(Buffer.from('login_lock=true\n'),profiles.propertiesFor('address-lock',env)));
  assert.doesNotThrow(()=>addressPreflight(env,file));
  fs.appendFileSync(file,'login_lock=true\n');
  assert.throws(()=>addressPreflight(env,file),/do not match/);
});
