/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const source=fs.readFileSync(path.join(__dirname,'../src/main/webapp/js/newCaseManagementView.js.jsp'),'utf8');
const start=source.indexOf('function doInformedConsent('),code=source.slice(start,source.indexOf('window.addEventListener(',start));
function setup(checked,confirm) {
  let removed=false;const requests=[];
  const element={is:()=>checked,attr:()=>undefined,prop:(name,value)=>{assert.equal(name,'checked');checked=value;},remove:()=>{removed=true;}};
  const jQuery=()=>element;jQuery.post=(url,body,callback)=>{requests.push({url,body});callback({value:'42'});};
  const context={jQuery,ctx:'/carlos',window:{confirm:()=>confirm},parseInt};vm.runInNewContext(code,context);
  return {run:()=>context.doInformedConsent('20001'),requests,checked:()=>checked,removed:()=>removed};
}
test('a user-checked checkbox submits consent for the originating patient and removes the banner',()=>{
  const fixture=setup(true,true);fixture.run();assert.equal(fixture.requests.length,1);
  assert.deepEqual(JSON.parse(JSON.stringify(fixture.requests[0])),{url:'/carlos/DemographicExtService',body:{method:'saveNewValue',demographicNo:'20001',key:'informedConsent',value:'yes'}});
  assert.equal(fixture.removed(),true);
});
test('declining confirmation clears the user-checked state without submitting consent',()=>{
  const fixture=setup(true,false);fixture.run();assert.equal(fixture.checked(),false);assert.equal(fixture.requests.length,0);assert.equal(fixture.removed(),false);
});
test('an unchecked consent control cannot submit consent',()=>{
  const fixture=setup(false,true);fixture.run();assert.equal(fixture.requests.length,0);assert.equal(fixture.removed(),false);
});
