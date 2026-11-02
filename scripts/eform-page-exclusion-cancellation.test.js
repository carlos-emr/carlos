/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const source=fs.readFileSync(path.join(__dirname,'eform-page-exclusion-playwright-checks.js'),'utf8');
test('a cancellation while geometry-check browser shutdown settles prevents a passing run',async()=>{
  const cancelled=new Error('injected cancellation');let interrupted=false,evaluations=0;
  const page={setContent:async()=>{},evaluate:async()=>{
    evaluations++;return evaluations===5?['carlos-interstitial']:{excludedCount:evaluations===3?0:1};
  }};
  await assert.rejects(vm.runInNewContext(source,{__dirname:path.join(__dirname),process:{env:{}},console:{log(){},error(){}},require(name){
    if(name==='playwright')return {chromium:{launch:async()=>({newPage:async()=>page,close:async()=>{interrupted=true;}})}};
    if(name==='./lib/playwright-harness')return {NO_PLAYWRIGHT_SIGNAL_HANDLING:{},runCheck:({run})=>run({cancellation:{throwIfCancelled(){if(interrupted)throw cancelled;}}})};
    return require(name);
  }}),error=>error===cancelled);
});
