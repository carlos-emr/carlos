/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h=require('./playwright-harness');
const ui=require('./playwright-ui');
const {masterRecordLink}=require('./master-record-link');
const {openSurface}=require('../surface-audit-playwright-checks');
const {surfaceByName}=require('./playwright-surfaces');
const {openOntarioBillForm}=require('./billing-on-bill-form');

async function eachUiQualitySurface(s,visit) {
  const scheduleUrl=s.schedule.url();
  const anonymous=await h.newContext(s.context.browser(),s.config);
  const login=await anonymous.newPage();h.wireStrictPage(login,'quality-login',s.recorder);await h.gotoApp(login,s.config.baseUrl,'/');
  await visit('login',login,'#username,#password,input[type="submit"],button[type="submit"]');
  await anonymous.close();
  await visit('schedule',s.schedule,'#inboxLink,#admin-panel,#admin2,#dateAndCalendar a');
  await visit('master-record',s.master,'input[type="button"]:visible,button:visible,a[href]:visible');
  const chart=await s.chart();await visit('chart',chart,'#saveImg,#newNoteImg');
  const rx=await s.popup(s.master,masterRecordLink(s.master,'/rx/choosePatient').first(),'quality-prescription');
  await rx.locator('#searchString').waitFor();
  await visit('prescription',rx,'#searchString,#customDrug,#saveOnlyButton,#saveButton');await rx.close();
  const inbox=await openSurface(s.context,s.schedule,surfaceByName('inbox-surface'),s.recorder,30000);
  await visit('inbox',inbox,'button:visible,input[type="button"]:visible,select:visible');
  if(inbox!==s.schedule)await inbox.close();
  const {page:admin}=await ui.clickOpensPopupOrNavigates(s.schedule,s.schedule.locator('#admin-panel,#admin2').first(),
    {context:s.context,recorder:s.recorder,label:'quality-administration',timeout:30000});
  await admin.locator('#adminNav').waitFor();await visit('administration',admin,'#adminNav a:visible');
  if(admin!==s.schedule)await admin.close();
  else await s.schedule.goto(scheduleUrl,{waitUntil:'domcontentloaded'}); // nosemgrep: captured authenticated schedule URL
  const {page:bill}=await openOntarioBillForm(s,'quality-bill-form');
  await visit('bill-form',bill,'input[name="serviceCode0"],input[type="submit"]:visible,button[type="submit"]:visible');await bill.close();
}
module.exports={eachUiQualitySurface};
