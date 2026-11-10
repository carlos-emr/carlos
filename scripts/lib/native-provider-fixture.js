/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
'use strict';
const h=require('./playwright-harness');
const {openAdmin,adminFrame,navigates}=require('../billing-on-admin-config-playwright-checks');
const q=h.sqlString;

/**
 * Save only an identified owned provider through Administration. This invalidates
 * the application's provider caches after SQL fixture setup. The caller owns
 * archive cleanup and the final cache reset after SQL deletion of its accounts.
 */
async function saveOwnedProviderProfile(s,actor,{status}={}) {
  h.assert(status===undefined||status==='0'||status==='1','Unsupported owned provider status');
  const no=q(actor.fixture.providerNo),marker=q(actor.marker);
  h.assert(s.sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${no} AND last_name=${marker}`)==='1','Owned provider identity changed before native save');
  h.assert(s.sql.value(`SELECT COUNT(*) FROM providerArchive WHERE provider_no=${no} AND (last_name<>${marker} OR last_name IS NULL)`)==='0','Foreign provider archive encountered');
  const archives=Number(s.sql.value(`SELECT COUNT(*) FROM providerArchive WHERE provider_no=${no}`));
  const originalStatus=s.sql.value(`SELECT status FROM provider WHERE provider_no=${no}`);
  h.assert(Number.isSafeInteger(archives)&&archives>=0&&['0','1'].includes(originalStatus),'Unsupported owned provider archive/status evidence');
  const scheduleUrl=s.schedule.url();
  let admin,failure;
  try {
    admin=await openAdmin(s);
    const frame=await adminFrame(admin,'/admin/ViewProviderSearchRecordsHtm','input[name="keyword"]');
    await frame.locator('input[name="search_mode"][value="search_providerno"]').check();
    await frame.locator(`input[name="search_status"][value="${originalStatus}"]`).check();
    await frame.locator('input[name="keyword"]').fill(actor.fixture.providerNo);
    await navigates(admin,frame,frame.locator('input[type="submit"]').first());
    await navigates(admin,frame,frame.locator(`a[href$="/admin/ViewProviderUpdateProvider?keyword=${actor.fixture.providerNo}"]`));
    h.assert(await frame.locator('input[name="provider_no"]').inputValue()===actor.fixture.providerNo,'Native profile form opened another provider');
    h.assert(await frame.locator('input[name="last_name"]').inputValue()===actor.marker,'Native profile ownership changed');
    if(status!==undefined)await frame.locator(`input[name="status"][value="${status}"]`).check();
    await navigates(admin,frame,frame.locator('input[type="submit"][name="subbutton"]'));
    h.assert(s.sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${no} AND last_name=${marker} AND status=${q(status??originalStatus)}`)==='1','Native profile save has the wrong owned identity/status');
    h.assert(Number(s.sql.value(`SELECT COUNT(*) FROM providerArchive WHERE provider_no=${no} AND last_name=${marker}`))===archives+1,'Native profile save has no unique archive receipt');
  }catch(error){failure=error;}
  try {
    if(admin&&!admin.isClosed()) {
      if(admin===s.schedule)await s.schedule.goto(scheduleUrl,{waitUntil:'load'});
      else await admin.close();
    }
  }catch(error){failure=failure?new AggregateError([failure,error],failure.message+'; native profile navigation cleanup: '+error.message):error;}
  if(failure)throw failure;
}
module.exports={saveOwnedProviderProfile};
