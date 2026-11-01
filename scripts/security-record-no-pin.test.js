/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
for(const page of ['securityupdatesecurity','securityaddarecord']) {
  const source=fs.readFileSync(path.join(__dirname,'../src/main/webapp/WEB-INF/jsp/admin',page+'.jsp'),'utf8');
  function script() {
    const start=source.indexOf('function onsub()'),end=source.indexOf('function handleMfaChange',start);
    return source.slice(start,end).replace(/<%=[\s\S]*?%>/g,'128').replace(/<%[\s\S]*?%>/g,'');
  }
  function setup() {
    const form={user_name:{value:'FAKE-user'},password:{value:'FAKE-password'},conPassword:{value:'FAKE-password'},provider_no:{value:'800001'},role_name:{value:'doctor'},b_ExpireSet:{checked:false},date_ExpireDate:{value:''}};
    const sandbox={document:{forms:[form],updatearecord:form,searchprovider:form},TextEncoder,jQuery:()=>({attr:()=>undefined}),alert(){},setfocus(){},validatePassword:()=>true,validatePin:value=>/^\d{4}$/.test(value)};
    vm.createContext(sandbox);vm.runInContext(script(),sandbox);return {sandbox,form};
  }
  test(`${page}: password/MFA administration can submit without nonexistent PIN controls`,()=>{
    const {sandbox,form}=setup();assert.equal(sandbox.onsub(),true);
    form.provider_no.value='';assert.equal(sandbox.onsub(),false);
  });
  test(`${page}: enabled legacy PIN still enforces required and matching values`,()=>{
    const {sandbox,form}=setup();Object.assign(form,{pinIsRequired:{value:1},b_RemoteLockSet:{checked:true},b_LocalLockSet:{checked:true},pin:{value:''},conPin:{value:''}});
    assert.equal(sandbox.onsub(),false);form.pin.value='1234';form.conPin.value='2345';assert.equal(sandbox.onsub(),false);form.conPin.value='1234';assert.equal(sandbox.onsub(),true);
  });
}
