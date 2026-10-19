#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Rx profile > Hide CPP checkbox. Persist/unhide one drug with an unchecked
// control; independent failure case requires an honest UI when the save fails.
const h=require('./lib/playwright-harness');
const {runWorkflow,expectValue}=require('./lib/workflow-session');
const {prepareRx}=require('./lib/rx-ui-workflow');
const {failureMark,consumeExpectedFailure,consumeAbortedFetch}=require('./lib/concurrency-support');
const refused=process.env.RX_CPP_ACTION==='refused';
const name=refused?'rx-cpp-visibility-refused-state':'rx-cpp-visibility-roundtrip';
async function workflow(s){
  const {sql,patient}=s,f=await prepareRx(s),drug=f.seed('visibility'),control=f.seed('control');
  const rx=await f.open(),box=rx.locator(`#hidecpp_${drug}`);
  const isSave=r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/rx/hideCpp');
  const state=()=>sql.rows(`SELECT drugid,hide_cpp,archived FROM drugs WHERE demographic_no=${patient} ORDER BY drugid`);
  if(refused){
    await s.step('a refused Hide CPP save reports failure and restores the actual unchecked state',async()=>{
      const before=JSON.stringify(state()),mark=failureMark(s.recorder);
      await rx.route('**/rx/hideCpp',route=>route.fulfill({status:403,contentType:'text/plain',body:'Synthetic refusal'}));
      try{
        const [response]=await Promise.all([rx.waitForResponse(isSave),box.check()]);
        h.assert(response.status()===403,'Refusal fixture did not answer the UI request');
        await rx.waitForLoadState('networkidle');
        consumeExpectedFailure(s.recorder,mark,{status:403,path:/\/rx\/hideCpp$/});
        h.assert(JSON.stringify(state())===before,'Refused Hide CPP changed clinical state');
        h.assert(!await box.isChecked(),'The checkbox claims Hidden after its save was refused');
        const text=await rx.locator('[role="alert"],.alert-danger,.rx-error').allInnerTexts();
        h.assert(text.some(x=>/fail|refus|unable|error/i.test(x)),'Refused Hide CPP showed no actionable error');
      }finally{await rx.unroute('**/rx/hideCpp');}
    });
  }else{
    await s.step('Hide CPP changes exactly the selected medication and survives reopening',async()=>{
      const mark=failureMark(s.recorder);
      const [response]=await Promise.all([rx.waitForResponse(isSave),box.check()]);
      h.assert(response.status()===200,'Hide CPP was refused');
      const data=new URLSearchParams(response.request().postData());
      h.assert(data.get('prescriptId')===drug&&data.get('value')==='true'&&response.request().headers()['csrf-token'],'Hide CPP submitted another drug or no CSRF token');
      await expectValue(sql,`SELECT hide_cpp FROM drugs WHERE drugid=${drug}`,'1','Hide CPP was not saved');
      h.assert(sql.value(`SELECT hide_cpp FROM drugs WHERE drugid=${control}`)==='0','Hide CPP affected the unselected drug');
      await rx.reload();await box.waitFor();await rx.waitForLoadState('networkidle');
      // The app leaves this tiny successful fetch body unread. Navigating the
      // page can abort it after the committed save; scope consumption to this
      // response and this reload, with the database proof above.
      consumeAbortedFetch(s.recorder,mark,/\/rx\/hideCpp$/);
      h.assert(await box.isChecked(),'Hidden flag did not reopen');
    });
    await s.step('clearing Hide CPP restores visibility without archiving either medication',async()=>{
      const mark=failureMark(s.recorder);
      await Promise.all([rx.waitForResponse(isSave),box.uncheck()]);
      await expectValue(sql,`SELECT hide_cpp FROM drugs WHERE drugid=${drug}`,'0','Unhide did not save');
      await rx.reload();await box.waitFor();await rx.waitForLoadState('networkidle');
      consumeAbortedFetch(s.recorder,mark,/\/rx\/hideCpp$/);
      h.assert(!await box.isChecked(),'Unhide did not reopen');
      h.assert(JSON.stringify(state())===JSON.stringify([[drug,'0','0'],[control,'0','0']]),'Visibility change altered another field or medication');
    });
  }
}
if(require.main===module)runWorkflow(name,workflow);
module.exports={workflow};
