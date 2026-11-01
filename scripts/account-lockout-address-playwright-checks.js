#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const {randomBytes}=require('node:crypto');
const h=require('./lib/playwright-harness');
const ui=require('./lib/playwright-ui');
const {runWorkflow}=require('./lib/workflow-session');
const {revealAuditLink}=require('./lib/playwright-link-audit');
const {throwawayLoginFixture,submitLoginForm}=require('./lib/throwaway-login-fixture');
const {loopbackSourceProxy}=require('./lib/loopback-source-proxy');
async function workflow(s) {
  h.assert(process.env.EXCLUSIVE==='1','Address-lock validation requires EXCLUSIVE=1 on a disposable deployment');
  const owned=[0,1].map(()=>throwawayLoginFixture({sql:s.sql,marker:'FAKE-PW'+randomBytes(8).toString('hex'),provider:s.provider,testUser:s.config.testUser}));
  for(const fixture of owned){s.cleanup(()=>fixture.cleanup());fixture.create();}
  const relays=[];
  s.cleanup(async()=>{for(const relay of relays)await relay.close();});
  const contexts=[];
  for(const source of ['127.0.0.2','127.0.0.3']) {
    const relay=await loopbackSourceProxy(s.config.baseUrl,source);relays.push(relay);
    contexts.push(await h.newContext(s.context.browser(),s.config,{proxy:relay.proxy}));
  }
  const {page:admin}=await ui.clickOpensPopupOrNavigates(s.schedule,s.schedule.locator('#admin-panel,#admin2').first(),
    {context:s.context,recorder:s.recorder,label:'address-unlock-administration',timeout:20000});
  async function unlockPage() {
    const link=admin.locator('#adminNav a[rel$="/admin/UnLock"]').first();await revealAuditLink(admin,link,20000);await link.click();
    const iframe=admin.locator('#dynamic-content iframe').first();await iframe.waitFor();
    const frame=await(await iframe.elementHandle()).contentFrame();h.assert(frame,'Unlock iframe is missing');
    await frame.waitForURL(/\/admin\/UnLock/,{waitUntil:'load'});await frame.locator('select[name="userName"]').waitFor({state:'attached'});return frame;
  }
  const keys=frame=>frame.locator('select[name="userName"] option').evaluateAll(nodes=>nodes.map(node=>node.value));
  const initial=await unlockPage(),initialKeys=await keys(initial);h.assert(['127.0.0.2','127.0.0.3'].every(ip=>!(initialKeys||[]).includes(ip)),'An owned source address was already tracked before the workflow');
  let tracked=false;
  const attempt=async(index,password,source=0)=>{
    const result=await submitLoginForm(contexts[source],s.config,{username:owned[index].username,password,pin:s.config.testPin});
    const outcome=result.outcome;await result.page.close();return outcome;
  };
  try {
    await s.step('failed passwords are tracked and locked by the real client address rather than either username',async()=>{
      let locked=false;
      for(let index=0;index<6&&!locked;index++) {
        tracked=true;const result=await attempt(0,s.config.testPassword+'-WRONG');
        h.assert(['failed','locked'].includes(result),'Address-lock attempt did not produce a native authentication refusal');locked=result==='locked';
      }
      h.assert(locked,'Owned loopback source did not reach address lockout');
      const frame=await unlockPage(),listed=await keys(frame);
      h.assert(listed.includes('127.0.0.2')&&!listed.includes(owned[0].username)&&!listed.includes(owned[1].username),'Unlock list used usernames instead of the source address');
      h.assert(s.sql.value(`SELECT COUNT(*) FROM log WHERE action='failed' AND content='login' AND contentId=${h.sqlString(owned[0].username)} AND ip='127.0.0.2'`)!=='0','Failed-login audit did not record the controlled source address');
    });
    await s.step('another account at the locked address is blocked while the same credentials work at an independent address',async()=>{
      h.assert(await attempt(1,s.config.testPassword)==='locked','Address lock did not cover another account at the same address');
      h.assert(await attempt(1,s.config.testPassword,1)==='schedule','Address lock incorrectly followed the username to an independent source');
    });
    await s.step('the native Unlock control releases only the owned address and the blocked account can sign in',async()=>{
      const frame=await unlockPage();await frame.locator('select[name="userName"]').selectOption('127.0.0.2');
      await Promise.all([frame.waitForNavigation({waitUntil:'domcontentloaded',timeout:20000}),frame.locator('input[name="submit"]').click()]);
      h.assert((await frame.locator('.alert-success').innerText()).includes('Account unlocked: 127.0.0.2'),'Unlock did not confirm the owned address');
      h.assert(!(await keys(frame)).includes('127.0.0.2'),'Unlocked source remains tracked');tracked=false;
      h.assert(await attempt(0,s.config.testPassword)==='schedule','Address remains blocked after administrative unlock');
    });
  } finally {
    if(tracked) {
      // Recovery is scoped to the address proved absent before this run. Keep
      // the admin browser alive until this exact lock is released and checked.
      const frame=await unlockPage();
      if((await keys(frame)).includes('127.0.0.2')) {
        await frame.locator('select[name="userName"]').selectOption('127.0.0.2');
        await Promise.all([frame.waitForNavigation({waitUntil:'domcontentloaded',timeout:20000}),frame.locator('input[name="submit"]').click()]);
      }
      h.assert(!(await keys(frame)).includes('127.0.0.2'),'Owned address lock remains after recovery');
    }
  }
}
if(require.main===module)runWorkflow('account-lockout-address',workflow,{openPatient:false});
module.exports={workflow};
