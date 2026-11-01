#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h=require('./lib/playwright-harness'),ui=require('./lib/playwright-ui');
const {runWorkflow,expectValue}=require('./lib/workflow-session');
const {revealAuditLink}=require('./lib/playwright-link-audit');
const {createOwnedBillingPhysician}=require('./lib/owned-billing-physician');
const {billingOnSave}=require('./lib/mutation-families');
const {authenticatedProbe}=require('./lib/authenticated-probe');
async function workflow(s) {
  const {sql,marker}=s,q=h.sqlString;
  if(sql.value(`SELECT COUNT(*) FROM site s JOIN providersite p ON p.site_id=s.site_id WHERE p.provider_no=${q(s.provider)} AND s.status<>0`)==='0')
    throw new h.SkipCheck('Clinic NBR billing needs an existing active operator site; the check does not manufacture clinic membership');
  const physician=createOwnedBillingPhysician(s),descriptions=[marker+' first',marker+' revised'];
  const code=String(10+Number.parseInt(marker.slice(-4),16)%80);
  // Bill submission stores the first two characters of this native option.
  if(sql.value(`SELECT COUNT(*) FROM clinic_nbr WHERE nbr_value=${q(code)}`)!=='0')throw new h.SkipCheck('Owned two-digit clinic NBR code collides; retry with a new run marker');
  let id;
  s.cleanup(()=>{
    const ids=sql.rows(`SELECT nbr_id FROM clinic_nbr WHERE nbr_value=${q(code)} AND nbr_string IN (${descriptions.map(q).join(',')})`).map(([key])=>key);
    for(const key of ids)sql.execute(`DELETE FROM clinic_nbr WHERE nbr_id=${key} AND nbr_value=${q(code)} AND nbr_string IN (${descriptions.map(q).join(',')})`);
    h.assert(sql.value(`SELECT COUNT(*) FROM clinic_nbr WHERE nbr_value=${q(code)} AND nbr_string IN (${descriptions.map(q).join(',')})`)==='0','Owned clinic NBR remains');
  });
  const {page:admin}=await ui.clickOpensPopupOrNavigates(s.schedule,s.schedule.locator('#admin-panel,#admin2').first(),{context:s.context,recorder:s.recorder,label:'clinic-nbr-administration',timeout:30000});
  const menu=admin.locator('a[rel$="/admin/ViewClinicNbrManage"]').first();await revealAuditLink(admin,menu,30000);
  const loaded=admin.waitForEvent('framenavigated',{predicate:f=>f.parentFrame()&&new URL(f.url()).pathname.endsWith('/admin/ViewClinicNbrManage')});await menu.click();const frame=await loaded;
  await frame.locator('#nbr_value').waitFor();
  async function mutate(button,method) {
    const [response]=await Promise.all([admin.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/admin/clinicNbrManage')),button.click()]);
    h.assert(response.ok(),'Clinic NBR POST failed');const json=await response.json();h.assert(json.success&&json.method===method,'Clinic NBR operation was refused');return json;
  }
  async function add(description) {
    await frame.locator('#nbr_value').fill(code);await frame.locator('#nbr_string').fill(description);
    const json=await mutate(frame.locator('input[onclick*="addCode"]'),'add');h.assert(/^[1-9]\d*$/.test(String(json.id)),'Add returned no clinic NBR row ID');id=String(json.id);
    await expectValue(sql,`SELECT CONCAT(nbr_value,'|',nbr_string) FROM clinic_nbr WHERE nbr_id=${id}`,code+'|'+description,'Clinic NBR was not stored exactly');
    await frame.locator(`#xml_p_nbr option[value="${id}"]`).waitFor({state:'attached'});
  }
  async function remove() {
    await frame.locator('#xml_p_nbr').selectOption(id);await mutate(frame.locator('input[onclick*="removeCode"]'),'remove');
    await expectValue(sql,`SELECT nbr_status FROM clinic_nbr WHERE nbr_id=${id}`,'D','Removed clinic NBR remains active');
  }
  await s.step('native Add stores a clinic NBR and GET cannot replace it',async()=>{
    await add(descriptions[0]);const before=sql.value(`SELECT COUNT(*) FROM clinic_nbr WHERE nbr_id=${id}`);
    const response=await authenticatedProbe(s,h.appUrl(s.config.baseUrl,`/admin/clinicNbrManage?method=remove&nbr=${id}`));
    await h.assertRefused(s,{response,table:'clinic_nbr',where:`nbr_id=${id}`,before,label:'Clinic NBR remove GET'});
  });
  await s.step('the offered replacement operation removes and re-adds the description',async()=>{
    // This release offers Add and Remove Selected; there is no Edit endpoint.
    await remove();await add(descriptions[1]);
  });
  const family=billingOnSave(physician.dependentSession,{appointmentLocation:physician.siteName,configureForm:async page=>{
    const options=await page.locator('select[name="xml_provider"] option').evaluateAll(nodes=>nodes.map(node=>node.value));
    const selected=options.find(value=>value.split('|')[0]===physician.providerNo);h.assert(selected,'Owned billing physician is not offered');
    await page.locator('select[name="xml_provider"]').selectOption(selected);await physician.selectClinic(page);
    const values=await page.locator('select[name="xml_visittype"] option').evaluateAll(nodes=>nodes.map(node=>({value:node.value,text:node.textContent})));
    const offered=values.find(option=>option.text.includes(descriptions[1])&&option.value.split('|')[0].trim()===code);h.assert(offered,'Revised clinic NBR is not offered by the native bill form');
    h.assert(!values.some(option=>option.text.includes(descriptions[0])),'Removed description remains offered');
    await page.locator('select[name="xml_visittype"]').selectOption(offered.value);
  }});physician.dependentSession.cleanup(()=>family.cleanup());family.prepare();
  await s.step('the Ontario native bill stores the chosen clinic NBR',async()=>{
    await family.perform('NBR');await expectValue(sql,`SELECT visittype FROM billing_on_cheader1 WHERE ${family.where('NBR')}`,code,'Saved bill lost its clinic NBR');
  });
  await s.step('Remove Selected retires only the owned clinic NBR',remove);
}
if(require.main===module)runWorkflow('billing-on-clinic-nbr',workflow,{openPatient:true,openMaster:false});
module.exports={workflow};
