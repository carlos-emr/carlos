#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const fs=require('node:fs');
const h=require('./lib/playwright-harness');
const {runWorkflow}=require('./lib/workflow-session');
const {renalInputFixture}=require('./lib/renal-input-fixture');
const {waitUntil}=require('./lib/concurrency-support');
const AUDIT='/var/log/carlos-emr/modsec/modsec_audit.log';
async function workflow(s) {
  h.assert(process.env.EXPECT_FRONT_DOOR==='true','The WAF privacy check requires the installed nginx + ModSecurity front door');
  h.assert(fs.existsSync(AUDIT),'The installed WAF audit log is unavailable');
  const fixture=renalInputFixture(s),page=await s.context.newPage();
  await h.gotoApp(page,s.config.baseUrl,`/rx/choosePatient?demographicNo=${s.patient}`);
  await page.locator('#searchString').waitFor();
  const endpoint=`${s.config.baseUrl}/rx/ViewRenalDosing`,marker=s.marker+'-renal-audit';
  const before=fs.statSync(AUDIT).size;
  const evidence=()=>fs.readFileSync(AUDIT).subarray(before).toString('utf8');
  await s.step('unusable stored observations return N/A without auditing session headers or the patient URL',async()=>{
    for(const [index,input] of [{weight:'70 kg',cr:'100'}, {weight:'70',cr:'<20'}, {weight:'70',cr:'100',crDate:null}].entries()) {
      fixture.apply(input);
      const response=await s.context.request.get(endpoint,{params:{demographicNo:s.patient,atcCode:'J01MA02',divId:marker+'-'+index}});
      h.assert(response.status()===200,'Routine stored-data conditions generated a WAF-relevant error status');
      h.assert((await response.text()).includes('data-renal-dosing-panel'),'Unusable observations did not retain the explicit renal panel');
      h.assert(!evidence().includes(marker),'Routine renal data conditions entered the WAF audit log');
    }
  });
  await s.step('a real injection remains blocked and audited by the installed WAF',async()=>{
    const attack=marker+'-attack';
    const response=await s.context.request.get(endpoint,{params:{demographicNo:s.patient,atcCode:"1 UNION SELECT password FROM security --",divId:attack}});
    h.assert(response.status()===403,'The WAF did not block the injection control');
    await waitUntil(()=>evidence().includes(attack),'The blocked injection was not audited');
    // Only Boolean evidence leaves this process; never print cookies, URLs or audit payloads.
    h.assert(!evidence().includes(marker+'-0')&&!evidence().includes(marker+'-1')&&!evidence().includes(marker+'-2'),
      'Routine renal requests entered the security audit');
  });
}
if(require.main===module)runWorkflow('rx-renal-waf-privacy',workflow,{openPatient:true});
module.exports={workflow};
