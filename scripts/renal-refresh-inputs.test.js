/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const h=require('./lib/playwright-harness');
const source=fs.readFileSync(path.join(__dirname,'rx-renal-input-matrix-playwright-checks.js'),'utf8');
for(const equation of ['80.0 100.0 sCr 2025-Jan-02','79.0 100.0 sCr 2025-Jan-02',
  '80.0 99.0 sCr 2025-Jan-02','80.0 100.0 sCr 2024-Jan-01']) {
  test(`renal Refresh checks observations even when the clearance stays at 80: ${equation}`,async()=>{
    const module={exports:{}},events=[];let refreshClicks=0;
    const selected={count:async()=>0},panel={innerText:async()=> 'Clcr 80 =',getByRole:(role,options)=>{
      assert.equal(role,'button');assert.equal(options.name,'Refresh');assert.equal(options.exact,true);
      return {click:async()=>{refreshClicks++;}};
    },
      locator:selector=>selector==='table.equation'?{waitFor:async()=>{},innerText:async()=>refreshClicks?equation:'40.0 200.0 sCr 2024-Jan-01'}:selected};
    const response={status:()=>200,finished:async()=>{},url:()=>
      'https://127.0.0.1/carlos/rx/ViewRenalDosing?demographicNo=42&atcCode=J01MA02&divId=renalDosing_7'};
    const rx={waitForResponse:async()=>response,waitForFunction:async()=>{},locator:()=>panel};
    vm.runInNewContext(source,{URL,process:{env:{RX_RENAL_CASE:'refresh-unit'}},module,require(name){
      if(name==='./lib/playwright-harness')return h;
      if(name==='./lib/renal-input-fixture')return {CASES:{'refresh-unit':{clearance:80,sex:'M',age:60}},
        renalInputFixture:()=>({apply:value=>events.push(value)})};
      if(name==='./rx-edit-discontinue-playwright-checks')return {stageFromSearch:async()=>7};
      if(name==='./lib/master-record-link')return {masterRecordLink:()=>({first(){return this;}})};
      return {};
    }});
    const run=module.exports.workflow({patient:'42',master:{},popup:async()=>rx,sql:{value:()=> '0'},step:async(_name,operation)=>operation()});
    if(equation==='80.0 100.0 sCr 2025-Jan-02')await run;
    else await assert.rejects(run,/Refreshed renal equation did not reread/);
    assert.equal(refreshClicks,1,'The workflow must click the native Refresh button');
    assert.equal(events.length,2);
  });
}
