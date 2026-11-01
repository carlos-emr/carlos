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
async function workflow(s) {
  const {sql,marker,provider,patient}=s,program=process.env.CONSENT_PROGRAM_ID;
  h.assert(/^[1-9]\d*$/.test(program||''),'CONSENT_PROGRAM_ID must name an unused fixture program ID');
  h.assert(sql.value(`SELECT COUNT(*) FROM program WHERE id=${program}`)==='0','Consent program ID is already in use; choose an unused fixture ID');
  const fixture=authzReadFixture({sql,marker,provider,testUser:s.config.testUser});s.cleanup(()=>fixture.cleanup());
  const login=fixture.addLogin('doctor'),who=h.sqlString(login.providerNo),name=marker+'-consent';
  const facility=sql.value('SELECT MIN(id) FROM Facility');h.assert(/^[1-9]\d*$/.test(facility),'No facility for consent fixture');
  let created=false;
  s.cleanup(()=>{
    sql.execute(`DELETE FROM provider_default_program WHERE provider_no=${who} AND program_id=${program};
      DELETE FROM program_provider WHERE provider_no=${who} AND program_id=${program}`);
    if(created) {
      h.assert(sql.value(`SELECT COUNT(*) FROM program WHERE id=${program} AND name=${h.sqlString(name)}`)==='1','Consent program ownership changed');
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM admission WHERE program_id=${program})+(SELECT COUNT(*) FROM program_provider WHERE program_id=${program})+(SELECT COUNT(*) FROM casemgmt_note WHERE program_no=${h.sqlString(program)})`)==='0','Consent program acquired external clinical references');
      sql.execute(`DELETE FROM program WHERE id=${program} AND name=${h.sqlString(name)}`);
    }
  });
  sql.execute(`INSERT INTO program(id,facilityId,name,type,maxAllowed,programStatus,transgender,firstNation,alcohol,physicalHealth,mentalHealth,housing,exclusiveView,ageMin,ageMax)
    VALUES (${program},${facility},${h.sqlString(name)},'service',1,'active',0,0,0,0,0,0,'none',0,150)`);created=true;
  sql.execute(`INSERT INTO program_provider(program_id,provider_no) VALUES (${program},${who});
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
    const dialogs=await h.withExpectedDialogs(chart,()=>chart.locator('#informedConsentCheck').check(),{accept:false});
    h.assert(dialogs.length===1&&dialogs[0].type==='confirm','Consent did not ask for confirmation');
    h.assert(sql.value(stored)==='0'&&await chart.locator('#informedConsentDiv').isVisible(),'Cancelled consent was recorded');
    h.assert(!await chart.locator('#informedConsentCheck').isChecked(),'Cancelled consent remains selected');
  });
  await s.step('confirmed informed consent stores the originating patient extension and stays collected after reopening',async()=>{
    const [response]=await Promise.all([chart.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/DemographicExtService')),
      h.withExpectedDialogs(chart,()=>chart.locator('#informedConsentCheck').check())]);
    h.assert(response.status()===200,'Recording informed consent failed');
    const sent=new URLSearchParams(response.request().postData()||'');
    h.assert(sent.get('demographicNo')===patient&&sent.get('key')==='informedConsent'&&sent.get('value')==='yes','Consent POST targeted a different patient or extension');
    await expectValue(sql,stored,'1','Confirmed consent was not saved exactly once');
    await chart.locator('#informedConsentDiv').waitFor({state:'detached'});
    await reloadChart(s,chart,'reopened informed consent');
    h.assert(await chart.locator('#informedConsentDiv').count()===0,'Collected consent banner returned after reload');
  });
}
if(require.main===module)runWorkflow('chart-informed-consent',workflow,{openPatient:true,openMaster:false});
module.exports={workflow};
