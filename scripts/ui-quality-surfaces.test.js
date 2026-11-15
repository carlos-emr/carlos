/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
test('UI quality audits the captured schedule after CAISI search has navigated its tab',async()=>{
  const saved='https://127.0.0.1/carlos/provider/providercontrol?displaymode=day';let current='https://127.0.0.1/carlos/PMmodule/ClientSearch2',closed=false;
  const schedule={url:()=>current,async goto(url){current=url;}},login={url:()=>'/login'},visited=[];
  const sandbox={module:{exports:{}},require(name){
    if(name==='./playwright-harness')return {assert:(ok,message)=>assert(ok,message),newContext:async()=>({newPage:async()=>login,close:async()=>{closed=true;}}),wireStrictPage(){},gotoApp:async()=>{}};
    return {};
  }};
  vm.runInNewContext(fs.readFileSync(path.join(__dirname,'lib/ui-quality-surfaces.js'),'utf8'),sandbox);
  const stop=new Error('stop after schedule');
  await assert.rejects(sandbox.module.exports.eachUiQualitySurface({schedule,scheduleUrl:saved,context:{browser:()=>({})},config:{baseUrl:new URL('https://127.0.0.1/carlos')},recorder:{}},async(name,page)=>{
    visited.push(name);if(name==='schedule'){assert.equal(page.url(),saved);throw stop;}
  }),error=>error===stop);
  assert.deepEqual(visited,['login','schedule']);assert.equal(closed,true);
});
