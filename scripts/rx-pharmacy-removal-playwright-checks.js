#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Remove the primary/all preferred pharmacies, return to prescribing, then
// save/preview. Existing ordering tests stop at the management list.
const h=require('./lib/playwright-harness');
const {runWorkflow}=require('./lib/workflow-session');
const {prepareRx}=require('./lib/rx-ui-workflow');
const {stageCustomDrug}=require('./rx-stash-patient-isolation-playwright-checks');
const {viewScriptFrame}=require('./rx-drug-name-quotes-playwright-checks');
const all=process.env.RX_PHARMACY_REMOVAL==='all';
const name=all?'rx-pharmacy-remove-all-return':'rx-pharmacy-remove-primary-return';
async function workflow(s){
  const {sql,patient,marker}=s,f=await prepareRx(s),ids=[];
  s.cleanup(()=>{
    sql.execute(`DELETE FROM demographicPharmacy WHERE demographic_no=${patient}`);
    for(const id of ids){
      h.assert(sql.value(`SELECT COUNT(*) FROM demographicPharmacy WHERE pharmacyID=${id}`)==='0','Owned pharmacy acquired another patient link');
      sql.execute(`DELETE FROM pharmacyInfo WHERE recordID=${id} AND name LIKE ${h.sqlString(marker+'%')}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM pharmacyInfo WHERE recordID=${id}`)==='0','Owned pharmacy remains');
    }
  });
  for(const label of ['primary','secondary'])ids.push(h.insertId(sql,`INSERT INTO pharmacyInfo(uid,name,address,city,province,postalCode,phone1,fax,status)
    VALUES(0,${h.sqlString(marker+' '+label)},'1 Test St','Fixture City','ON','M1A 1A1','416-555-0101','416-555-0102','1')`,'owned pharmacy'));
  sql.execute(`INSERT INTO demographicPharmacy(pharmacyID,demographic_no,status,addDate,preferredOrder,consentToContact)
    VALUES(${ids[0]},${patient},'1',NOW(),1,0),(${ids[1]},${patient},'1',NOW(),2,0)`);
  const rx=await f.open();
  await s.step('removing preferred pharmacies leaves their clinic directory records intact',async()=>{
    await rx.locator('a[href$="/rx/managePharmacy"]').click();await rx.waitForLoadState('networkidle');
    for(const id of all?ids:[ids[0]]){
      await Promise.all([rx.waitForNavigation({waitUntil:'load'}),rx.locator(`#preferredList div[pharmId="${id}"] .prefUnlink`).click()]);
      await rx.waitForLoadState('networkidle');
      h.assert(sql.value(`SELECT COUNT(*) FROM demographicPharmacy WHERE demographic_no=${patient} AND pharmacyID=${id} AND status='1'`)==='0','Removed pharmacy remains preferred');
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM pharmacyInfo WHERE recordID IN (${ids}) AND status='1'`)==='2','Unlinking deleted a clinic pharmacy');
    await rx.locator('input[onclick="returnToRx();"]').click();await rx.waitForURL(/\/rx\/choosePatient/);await rx.waitForLoadState('networkidle');
  });
  await s.step('returning to Rx uses the remaining pharmacy or no pharmacy for the new prescription',async()=>{
    const options=await rx.locator('#Calcs option').evaluateAll(nodes=>nodes.map(x=>JSON.parse(x.value).id.toString()));
    h.assert(JSON.stringify(options)===JSON.stringify(all?[]:[ids[1]]),'Returned Rx shows stale preferred pharmacies');
    const key=await stageCustomDrug(rx,marker+' pharmacy');
    await rx.locator(`#instructions_${key}`).fill('1 tab PO OD x 30 days');
    await Promise.all([rx.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/rx/UpdateScript')),rx.locator(`label[for="jsonDxSearch_${key}"]`).click()]);
    await rx.locator('#saveButton').click();const script=await viewScriptFrame(rx);
    h.assert(new URL(script.url()).searchParams.get('pharmacyId')===(all?null:ids[1]),'Preview names a removed pharmacy');
    h.assert(sql.value(`SELECT COALESCE(pharmacyId,0) FROM drugs WHERE demographic_no=${patient}`)===(all?'0':ids[1]),'Saved medication names a removed pharmacy');
    await script.frameLocator('#preview').locator('#signature').waitFor({state:'attached'});
  });
}
if(require.main===module)runWorkflow(name,workflow);
module.exports={workflow};
