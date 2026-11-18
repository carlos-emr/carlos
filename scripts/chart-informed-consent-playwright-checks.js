#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h=require('./lib/playwright-harness');
const {runWorkflow,expectValue}=require('./lib/workflow-session');
const {authzReadFixture}=require('./lib/authz-read-fixture');
const {signIn}=require('./lib/authz-read-probe');
const {openMasterRecord}=require('./master-record-tabs-playwright-checks');
const {openChart,waitForNavbars}=require('./echart-navbar-modules-playwright-checks');
const {reloadChart}=require('./chart-session-state-two-window-isolation-playwright-checks');
const {chartNoteSave}=require('./lib/mutation-families');
const {programReferenceGuard}=require('./lib/program-reference-guard');
async function confirmWithUnconfirmedAlert(chart) {
  return h.withExpectedDialogs(chart, async () => {
    await Promise.all([
      chart.waitForEvent('dialog', {predicate: dialog => dialog.type() === 'alert', timeout: 30000}),
      chart.locator('#informedConsentCheck').click(),
    ]);
  });
}

async function workflow(s) {
  const {sql,marker,provider,patient}=s,program=process.env.CONSENT_PROGRAM_ID;
  h.assert(/^[1-9]\d*$/.test(program||''),'CONSENT_PROGRAM_ID must name an unused fixture program ID');
  h.assert(sql.value(`SELECT COUNT(*) FROM program WHERE id=${program}`)==='0','Consent program ID is already in use; choose an unused fixture ID');
  const references=programReferenceGuard(sql,program);references.assertUnused();
  const fixture=authzReadFixture({sql,marker,provider,testUser:s.config.testUser});s.cleanup(()=>fixture.cleanup());
  const login=fixture.addLogin('doctor'),who=h.sqlString(login.providerNo),name=marker+'-consent';
  const facility=sql.value('SELECT MIN(id) FROM Facility WHERE disabled=0');h.assert(/^[1-9]\d*$/.test(facility),'No facility for consent fixture');
  let created=false;
  s.cleanup(()=>{
    sql.execute(`DELETE FROM provider_default_program WHERE provider_no=${who} AND program_id=${program};
      DELETE FROM program_provider WHERE provider_no=${who} AND program_id=${program}`);
    if(created) {
      h.assert(sql.value(`SELECT COUNT(*) FROM program WHERE id=${program} AND name=${h.sqlString(name)}`)==='1','Consent program ownership changed');
      references.assertUnused();
      sql.execute(`DELETE FROM program WHERE id=${program} AND name=${h.sqlString(name)}`);
    }
  });
  sql.execute(`INSERT INTO program(id,facilityId,name,type,maxAllowed,programStatus,transgender,firstNation,alcohol,physicalHealth,mentalHealth,housing,exclusiveView,ageMin,ageMax,enableEncounterTime,enableEncounterTransportationTime,enableOCAN)
    VALUES (${program},${facility},${h.sqlString(name)},'service',1,'active',0,0,0,0,0,0,'none',0,150,0,0,0)`);created=true;
  const role=sql.value("SELECT role_no FROM secRole WHERE role_name='doctor'");
  h.assert(/^[1-9]\d*$/.test(role),'Seeded doctor program role is absent');
  sql.execute(`INSERT INTO program_provider(program_id,provider_no,role_id) VALUES (${program},${who},${role});
    INSERT INTO provider_default_program(provider_no,program_id,signnote) VALUES (${who},${program},0);
    INSERT INTO provider_facility(provider_no,facility_id) VALUES (${who},${facility})`);
  const cleanup=chartNoteSave(s);s.cleanup(()=>cleanup.cleanup());
  const clinician=await signIn(s,login);
  clinician.context.on('page',page=>h.wireStrictPage(page,'consent-clinician',s.recorder));
  const {masterPage}=await openMasterRecord(clinician.context,clinician.page,s.recorder,{searchTerm:marker,preferredDemographicNo:patient,timeout:30000});
  const chart=await openChart(clinician.context,masterPage,s.recorder,30000);await waitForNavbars(chart,30000);
  const stored=`SELECT COUNT(*) FROM demographicExt WHERE demographic_no=${patient} AND key_val='informedConsent' AND value='yes'`;
  await s.step('cancelled informed-consent confirmation leaves the banner and stored consent unchanged',async()=>{
    await chart.locator('#informedConsentDiv').waitFor({state:'visible'});
    const dialogs=await h.withExpectedDialogs(chart,()=>chart.locator('#informedConsentCheck').click(),{accept:false});
    h.assert(dialogs.length===1&&dialogs[0].type==='confirm','Consent did not ask for confirmation');
    h.assert(sql.value(stored)==='0'&&await chart.locator('#informedConsentDiv').isVisible(),'Cancelled consent was recorded');
    h.assert(!await chart.locator('#informedConsentCheck').isChecked(),'Cancelled consent remains selected');
  });
  const pattern='**/DemographicExtService';
  await s.step('an unconfirmed consent write requires reopening before retrying',async()=>{
    const refuse=route=>route.fulfill({status:200,contentType:'application/json',body:'{"value":"0"}'});
    await chart.route(pattern,refuse);
    try {
      const dialogs=await confirmWithUnconfirmedAlert(chart);
      h.assert(dialogs.length===2&&dialogs[0].type==='confirm'&&dialogs[1].type==='alert'
        &&/could not be confirmed.*reopen the chart.*only if consent is still absent/i.test(dialogs[1].text),
      'Unconfirmed consent did not explain when it is safe to retry');
      h.assert(sql.value(stored)==='0'&&await chart.locator('#informedConsentDiv').isVisible(),'Unconfirmed consent changed the stored state or hid the banner');
      h.assert(!await chart.locator('#informedConsentCheck').isChecked()&&await chart.locator('#informedConsentCheck').isDisabled(),
        'Unconfirmed consent remained selectable before reopening');
    } finally {await chart.unroute(pattern,refuse);}
    await reloadChart(s,chart,'reopened chart without consent');
    h.assert(await chart.locator('#informedConsentCheck').isEnabled(),'Reopening did not allow a still-absent consent to be saved');
  });
  await s.step('a saved consent with a lost acknowledgement cannot be saved again from the same chart',async()=>{
    let sent, acknowledged;
    const loseAcknowledgement=async route=>{
      sent=new URLSearchParams(route.request().postData()||'');
      const response=await route.fetch();
      try {
        acknowledged=response.status()===200&&Number((await response.json()).value)>0;
        await route.fulfill({status:200,contentType:'application/json',body:'{'});
      } finally {await response.dispose();}
    };
    await chart.route(pattern,loseAcknowledgement);
    try {
      const dialogs=await confirmWithUnconfirmedAlert(chart);
      h.assert(dialogs.length===2&&/reopen the chart.*only if consent is still absent/i.test(dialogs[1].text),
        'The lost acknowledgement allowed an unsafe retry');
    } finally {await chart.unroute(pattern,loseAcknowledgement);}
    h.assert(acknowledged,'The consent POST did not commit before its acknowledgement was lost');
    h.assert(sent.get('demographicNo')===patient&&sent.get('key')==='informedConsent'&&sent.get('value')==='yes','Consent POST targeted a different patient or extension');
    await expectValue(sql,stored,'1','Consent with a lost acknowledgement was not saved exactly once');
    h.assert(await chart.locator('#informedConsentCheck').isDisabled(),'The old chart still allowed another consent save');
    await reloadChart(s,chart,'reopened informed consent');
    h.assert(await chart.locator('#informedConsentDiv').count()===0,'Collected consent banner returned after reload');
  });
}
if(require.main===module)runWorkflow('chart-informed-consent',workflow,{openPatient:true,openMaster:false});
module.exports={workflow};
