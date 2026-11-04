#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const {runWorkflow}=require('./lib/workflow-session');
const {eachUiQualitySurface}=require('./lib/ui-quality-surfaces');
const h=require('./lib/playwright-harness');
function requireAxeCore(resolve=require.resolve) {
  try { return resolve('axe-core/axe.min.js'); }
  catch(error) {
    if(error.code==='MODULE_NOT_FOUND') throw new h.SkipCheck('accessibility-smoke requires axe-core@4.14.0; install the pinned dependency from the Debian validation runbook');
    throw error;
  }
}
async function workflow(s) {
  const axePath=requireAxeCore();
  await eachUiQualitySurface(s,async(label,page)=>s.step(`${label}: report accessibility rules`,async()=>{
    await page.addScriptTag({path:axePath});
    const report=await page.evaluate(async()=>{
      const result=await window.axe.run(document,{resultTypes:['violations','incomplete'],preload:false});
      // Report rules and counts only. Axe's HTML and accessible-name fields can
      // contain patient data and must never enter the suite's logs.
      return {violations:result.violations.map(rule=>({id:rule.id,impact:rule.impact,count:rule.nodes.length})),
        incomplete:result.incomplete.map(rule=>({id:rule.id,count:rule.nodes.length}))};
    });
    console.log(JSON.stringify({surface:label,...report}));
  }));
}
if(require.main===module)runWorkflow('accessibility-smoke',workflow,{openPatient:true,preflightConfig:()=>requireAxeCore()});
module.exports={workflow,requireAxeCore};
