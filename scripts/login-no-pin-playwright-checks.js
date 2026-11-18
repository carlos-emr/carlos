#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h=require('./lib/playwright-harness');
const {runWorkflow}=require('./lib/workflow-session');
const {throwawayLoginFixture,submitLoginForm}=require('./lib/throwaway-login-fixture');
async function workflow(s) {
  const fixture=throwawayLoginFixture({sql:s.sql,marker:s.marker,provider:s.provider,testUser:s.config.testUser});
  s.cleanup(()=>fixture.cleanup());fixture.create();
  const pin=()=>s.sql.value(`SELECT SHA2(JSON_ARRAY(pin,pinUpdateDate),256) FROM security WHERE security_no=${fixture.securityNo}`),before=pin();
  const context=await h.newContext(s.context.browser(),s.config);
  context.on('page',page=>h.wireStrictPage(page,'no-pin',s.recorder,{baseline:[]}));
  await s.step('no-PIN configuration exposes password login without a legacy PIN field',async()=>{
    const page=await context.newPage();await h.gotoApp(page,s.config.baseUrl,'/');
    h.assert(await page.locator('#username').isVisible()&&await page.locator('#password').isVisible(),'Password login controls are missing');
    h.assert(await page.locator('#pin').count()===0,'Disabled legacy PIN is still requested by the login form');await page.close();
  });
  await s.step('wrong password remains refused and correct password reaches the schedule without changing stored PIN data',async()=>{
    const refused=await submitLoginForm(context,s.config,{username:fixture.username,password:s.config.testPassword+'-WRONG',pin:''});
    h.assert(refused.outcome==='failed','No-PIN login accepted or misreported a wrong password');await refused.page.close();
    const page=await h.login(context,{...s.config,testUser:fixture.username},s.recorder);
    h.assert(/providercontrol|appointment/i.test(new URL(page.url()).pathname),'Password-only login did not reach the schedule');
    h.assert(pin()===before,'No-PIN sign-in changed the stored PIN or its update date');
  });
}
if(require.main===module)runWorkflow('login-no-pin',workflow,{openPatient:false});
module.exports={workflow};
