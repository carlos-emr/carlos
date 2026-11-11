#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
// Installed BC regression: real bounded Client Search POSTs (including Soundex), note identity,
// sorting, repeated CSRF paging, and BC tray pagination preserving draft fee codes.
// Owns and removes its patients, source note, tray associations and authorization identities.
const h=require('./lib/playwright-harness');
const {runWorkflow}=require('./lib/workflow-session');
const {createOwnedPatient,newOwnedMarker,removeOwnedPatient}=require('./lib/owned-patient');
const {randomUUID}=require('node:crypto');
const {authzReadFixture}=require('./lib/authz-read-fixture');
const q=h.sqlString;
async function workflow(s) {
 const {sql,marker,provider,patient,config}=s;
 const identities=authzReadFixture({sql,marker,provider,testUser:config.testUser});s.cleanup(()=>identities.cleanup());
 const role=identities.addRole({_appointment:'r',_demographic:'w',_admin:'w',_billing:'w',_pmm_management:'r',_eChart:'w','_casemgmt.notes':'w'});
 const login=identities.addLogin(role);const context=await h.newContext(s.context.browser(),config);
 context.on('page',page=>h.wireStrictPage(page,'bounded-tables',s.recorder));await h.login(context,{...config,testUser:login.username},s.recorder);
 const patients=[];
 s.cleanup(()=>{for(const [id,owned] of patients)removeOwnedPatient(sql,id,owned);});
 for(let i=0;i<23;i++){
  const owned=newOwnedMarker();const id=createOwnedPatient(sql,{marker:owned,provider,firstName:marker});patients.push([id,owned]);
 }
 const note=h.insertId(sql,`INSERT INTO casemgmt_note(update_date,observation_date,demographic_no,provider_no,note,history,uuid,locked,archived,signed)
 VALUES(NOW(),NOW(),${patient},${q(provider)},${q(marker)},'',${q(randomUUID())},'0',0,0)`,'source note');
 s.cleanup(()=>{sql.execute(`DELETE FROM casemgmt_note WHERE note_id=${note} AND demographic_no=${patient}`);h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE note_id=${note}`)==='0','Source note cleanup failed');});
 async function navigate(page,button){const old=await page.locator('[data-server-page-region]').elementHandle();await button.click();await page.waitForFunction(element=>!element.isConnected,old);}
 await s.step('Attach Client bounds real POST searches, preserves the note and unsaved criteria, and repeats CSRF paging',async()=>{
  const page=await context.newPage();await h.gotoApp(page,config.baseUrl,`/PMmodule/ClientSearch2?method=attachForm&noteId=${note}`);
  await page.locator('#attachFirstName').fill(marker);await page.locator('#attachStatus').selectOption('');await page.locator('#attachSearchOutsideDomain').check();
  await Promise.all([page.waitForNavigation({waitUntil:'domcontentloaded'}),page.locator('form[name="clientSearchForm2"] input[type="submit"]').click()]);
  const actual=[];
  for(let index=0;index<3;index++){
   const links=page.locator('#client tbody .attach-note-to-client');const ids=await links.evaluateAll(xs=>xs.map(x=>x.dataset.client));
   h.assert(ids.length<=10,'Attach Client emitted more than 10 rows');actual.push(...ids);
   h.assert((await links.evaluateAll(xs=>xs.map(x=>x.dataset.note))).every(x=>x===note),'Paging changed note identity');
   h.assert(await page.locator('[data-server-page-form] [name="noteId"]').inputValue()===note,'Pager lost note ID');
   if(index===0)await page.locator('#attachFirstName').fill(marker+' unsaved');
   if(index<2){await navigate(page,page.locator('[data-server-page-form] button[name="tablePage"]').nth(2));h.assert(await page.locator('#attachFirstName').inputValue()===marker+' unsaved','Paging replaced the criteria draft');}
  }
  h.assert(new Set(actual).size===23 && actual.join(',')===patients.map(x=>x[0]).join(','),'Client traversal omitted or repeated a record');
  await navigate(page,page.locator('button[name="tableSortChange"][value="2"]'));
  h.assert(await page.locator('#client tbody tr').count()===10,'Server sort lost the first page');
  h.assert(await page.locator('[data-server-page-form] [name="tableSort"]').inputValue()==='2','Server sort was ignored');
  await page.locator('#attachFirstName').fill(marker);await page.locator('#attachSoundex').check();
  await Promise.all([page.waitForNavigation({waitUntil:'domcontentloaded'}),page.locator('form[name="clientSearchForm2"] input[type="submit"]').click()]);
  h.assert(await page.locator('#client tbody tr').count()===10,'Native Soundex search was not bounded');
  await navigate(page,page.locator('[data-server-page-form] button[name="tablePage"]').nth(2));
  h.assert(await page.locator('#client tbody tr').count()===10,'Native Soundex second page failed');
 });
 const fees=[];s.cleanup(()=>{if(fees.length)sql.execute(`DELETE FROM billing_trayfees WHERE id IN (${fees.join(',')})`);h.assert(!fees.length||sql.value(`SELECT COUNT(*) FROM billing_trayfees WHERE id IN (${fees.join(',')})`)==='0','Tray cleanup failed');});
 for(let i=0;i<121;i++)fees.push(h.insertId(sql,"INSERT INTO billing_trayfees(billingServiceNo,billingServiceTrayNo) VALUES('0','0')",'tray association'));
 await s.step('BC tray sends 50 rows per page and paging retains both unsaved fee codes',async()=>{
  const page=await context.newPage();await h.gotoApp(page,config.baseUrl,'/billing/CA/BC/supServiceCodeAssocAction?actionMode=view');
  h.assert(await page.locator('table.displayGrid tbody tr').count()===50,'BC tray did not render a bounded 50-row page');
  await page.locator('#primaryCode').fill('FAKE-DRAFT');await page.locator('#secondaryCode').fill('FAKE-TRAY');
  await navigate(page,page.locator('[data-server-page-form] button[name="tablePage"]').nth(2));
  h.assert(await page.locator('table.displayGrid tbody tr').count()<=50,'BC tray second page exceeded the limit');
  h.assert(await page.locator('#primaryCode').inputValue()==='FAKE-DRAFT'&&await page.locator('#secondaryCode').inputValue()==='FAKE-TRAY','BC tray paging erased draft fee codes');
 });
}
if (require.main === module) runWorkflow('server-table-installed', workflow, {openMaster:false});
module.exports={workflow};
