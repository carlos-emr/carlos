/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const root=process.env.CARLOS_TEST_ROOT||'/root/carlos';
const fs=require('node:fs');
const h=require(root+'/scripts/lib/playwright-harness'),ui=require(root+'/scripts/lib/playwright-ui');
const {runWorkflow}=require(root+'/scripts/lib/workflow-session');
const {workflow:uploadFixture,uploadThroughPopup}=require(root+'/scripts/lab-upload-playwright-checks');
const {versionMessage,VERSIONS}=require(root+'/scripts/lab-version-ingestion-playwright-checks');
const {openInbox,openSidebar,setMode,search}=require(root+'/scripts/inbox-file-forward-playwright-checks');
const q=h.sqlString;
async function workflow(s) {
 h.assert(process.env.EXCLUSIVE==='1'&&process.env.CARLOS_DISPOSABLE_VM==='true','Requires an exclusive disposable installed database');
 const controlRoutes=process.env.UNLINK_ROUTE_CONTROL==='1';
 const findings=[],record=(okay,message)=>{if(!okay){findings.push(message);console.log('FINDING '+message);}};
 await s.chart();
 await uploadFixture(s,{initialContent:(acc,last)=>versionMessage(acc,last,VERSIONS.original),afterInitial:async source=>{
  const ids=()=>s.sql.rows(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum=${q(source.accession)} ORDER BY lab_no`).flat();
  const original=ids()[0];h.assert(/^[1-9]\d*$/.test(original),'Missing original source');
  const originalAccess=s.sql.rows(`SELECT provider_no,mrpDemographicNo FROM providerLabRouting WHERE lab_type='HL7' AND lab_no=${original} ORDER BY id`);
  console.log('FIXTURE original routing provenance '+JSON.stringify(originalAccess));
  if(controlRoutes){
  // Upload grants access through this patient's automatic MRP rule. Set up an
  // explicit independent assignment before exercising the native acknowledgement.
  s.sql.execute(`UPDATE providerLabRouting SET mrpDemographicNo=NULL WHERE lab_type='HL7' AND lab_no=${original} AND provider_no=${q(s.provider)}`);
  h.assert(s.sql.value(`SELECT COUNT(*) FROM providerLabRouting WHERE lab_type='HL7' AND lab_no=${original} AND provider_no=${q(s.provider)} AND mrpDemographicNo IS NULL`)==='1','Independent original route fixture was not established');
  }
  async function open(id,label) {
   const inbox=await openInbox(s.schedule,s.context,s.recorder,label+'-inbox');await setMode(inbox,'list');await openSidebar(inbox);
   await inbox.locator('#statusAll').check();await inbox.locator('#specificPatients').check();await inbox.locator('#inputLastName').fill(s.marker);await search(inbox);
   const row=inbox.locator(`tr[data-lab-type="HL7"][data-segment-id="${id}"]`);await row.waitFor({state:'visible'});
   const report=await ui.clickOpensPopup(inbox,row.locator('a[onclick*="reportWindow"]').first(),{context:s.context,recorder:s.recorder,label,timeout:30000});
   return {inbox,report};
  }
  // Acknowledge the original through the real UI, then deliver a correction through the uploader.
  const first=await open(original,'unlink-original-review');
  await h.withExpectedDialogs(first.report,async()=>{
   const [response]=await Promise.all([first.report.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/oscarMDS/UpdateStatus')),
    first.report.locator('input[onclick*="ackLab"],button[onclick*="ackLab"]').first().click()]);
   h.assert(response.status()===200,'Original acknowledgment failed');
  });
  h.assert(s.sql.value(`SELECT COUNT(*) FROM providerLabRouting WHERE lab_type='HL7' AND lab_no=${original} AND provider_no=${q(s.provider)} AND status='A'`)==='1','Original is not reviewed');
  // Acknowledgment closes its own window asynchronously. Let that native close finish before retiring other pages.
  if(!first.report.isClosed())await first.report.waitForEvent('close',{timeout:10000});
  for(const p of new Set([first.report,first.inbox]))if(p!==s.schedule&&!p.isClosed())await p.close();
  if(s.schedule.isClosed())s.schedule=await s.context.newPage();
  if(s.schedule.url()!==s.scheduleUrl)await s.schedule.goto(s.scheduleUrl,{waitUntil:'load'});
  fs.writeFileSync(source.filePath,Buffer.from(versionMessage(source.accession,s.marker,VERSIONS.corrected),'latin1'));
  h.assert(await uploadThroughPopup(s,source.filePath,source.fileName)==='Uploaded successfully','Correction was not ingested');
  const chain=ids();h.assert(chain.length===2&&chain.every(id=>/^[1-9]\d*$/.test(id)),'Expected exactly two ingested source reports');
  const list=chain.join(','),quoted=chain.map(q).join(',');
  if(controlRoutes){
  s.sql.execute(`UPDATE providerLabRouting SET mrpDemographicNo=NULL WHERE lab_type='HL7' AND lab_no IN (${list}) AND provider_no=${q(s.provider)}`);
  h.assert(s.sql.value(`SELECT COUNT(*) FROM providerLabRouting WHERE lab_type='HL7' AND lab_no IN (${list}) AND provider_no=${q(s.provider)} AND mrpDemographicNo IS NULL`)===String(chain.length),'Every source needs its independent provider route');
  const automaticProvider=s.sql.value(`SELECT provider_no FROM provider p WHERE p.provider_no REGEXP '^[1-9][0-9]*$' AND p.provider_no<>${q(s.provider)} AND NOT EXISTS (SELECT 1 FROM providerLabRouting r WHERE r.lab_type='HL7' AND r.lab_no IN (${list}) AND r.provider_no=p.provider_no) ORDER BY provider_no LIMIT 1`);
  h.assert(/^[1-9]\d*$/.test(automaticProvider),'No distinct existing provider is available for the obsolete-MRP control');
  for(const id of chain)s.sql.execute(`INSERT INTO providerLabRouting(provider_no,lab_no,status,comment,timestamp,lab_type,mrpDemographicNo) VALUES(${q(automaticProvider)},${id},'A','Owned obsolete automatic MRP control',NOW(),'HL7',${s.patient})`);
  h.assert(s.sql.value(`SELECT COUNT(*) FROM providerLabRouting WHERE lab_type='HL7' AND lab_no IN (${list}) AND provider_no=${q(automaticProvider)} AND mrpDemographicNo=${s.patient}`)===String(chain.length),'Automatic MRP controls are missing');
  }
  const providerBefore=s.sql.rows(`SELECT id,lab_no,provider_no,mrpDemographicNo FROM providerLabRouting WHERE lab_type='HL7' AND lab_no IN (${list}) ORDER BY id`);
  const independent=providerBefore.filter(row=>row[3]===null),automatic=providerBefore.filter(row=>row[3]!==null);
  h.assert(providerBefore.length>0&&providerBefore.every(row=>/^[1-9]\d*$/.test(row[0])),'Provider provenance snapshot is incomplete');
  if(controlRoutes)h.assert(independent.length>=chain.length&&automatic.length>=chain.length,'Provenance controls must exercise both route classes');
  const active=s.sql.rows(`SELECT m.id,m.dataField,DATE_FORMAT(m.dateObserved,'%Y-%m-%d %H:%i:%s'),e.val FROM measurements m JOIN measurementsExt e ON e.measurement_id=m.id AND e.keyval='lab_no' WHERE e.val IN (${quoted}) ORDER BY m.id`);
  h.assert(active.length>0,'Fixture must have actual active derived measurements');
  const activeIds=[...new Set(active.map(row=>row[0]))];h.assert(activeIds.every(id=>/^[1-9]\d*$/.test(id)),'Invalid projection IDs');
  s.cleanup(()=>{
   s.sql.execute(`DELETE FROM measurementsDeleted WHERE originalId IN (${activeIds.join(',')}) AND demographicNo=${s.patient}`);
   h.assert(s.sql.value(`SELECT COUNT(*) FROM measurementsDeleted WHERE originalId IN (${activeIds.join(',')})`)==='0','Owned archived projections remain');
  });
  const raw=()=>JSON.stringify({info:s.sql.rows(`SELECT * FROM hl7TextInfo WHERE lab_no IN (${list}) ORDER BY lab_no`),messages:s.sql.rows(`SELECT * FROM hl7TextMessage WHERE lab_id IN (${list}) ORDER BY lab_id`)});
  const rawBefore=raw(),extBefore=s.sql.rows(`SELECT measurement_id,keyval,val FROM measurementsExt WHERE measurement_id IN (${activeIds.join(',')}) ORDER BY id`);
  const clinical=()=>JSON.stringify({audit:s.sql.rows(`SELECT * FROM log WHERE action='unlink' AND content='lab' AND contentId IN (${quoted}) AND demographic_no=${s.patient} ORDER BY id`),patient:s.sql.rows(`SELECT * FROM patientLabRouting WHERE lab_type='HL7' AND lab_no IN (${list}) ORDER BY id`),provider:s.sql.rows(`SELECT * FROM providerLabRouting WHERE lab_type='HL7' AND lab_no IN (${list}) ORDER BY id`),active:s.sql.rows(`SELECT * FROM measurements WHERE id IN (${activeIds.join(',')}) ORDER BY id`),deleted:s.sql.rows(`SELECT * FROM measurementsDeleted WHERE originalId IN (${activeIds.join(',')}) ORDER BY id`),extensions:s.sql.rows(`SELECT * FROM measurementsExt WHERE measurement_id IN (${activeIds.join(',')}) ORDER BY id`)});
  const opened=await open(original,'unlink-selected-version');
  const stale=await s.context.newPage();h.wireStrictPage(stale,'unlink-stale-tab',s.recorder);await stale.goto(opened.report.url(),{waitUntil:'networkidle'});
  // Closing a third actual view records the precise pre-mutation native UI.
  const pre=await s.context.newPage();h.wireStrictPage(pre,'unlink-before-mutation',s.recorder);await pre.goto(opened.report.url(),{waitUntil:'networkidle'});await pre.close();
  if(process.env.UNLINK_AUDIT_FAULT==='1'){
   const trigger='pw_unlink_'+source.stamp;
   h.assert(s.sql.value(`SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() AND TRIGGER_NAME=${q(trigger)}`)==='0','Fault trigger namespace already exists');
   const beforeFault=clinical(),badMark=s.recorder.badResponses.length,consoleMark=s.recorder.consoleIssues.length;
   try{
    s.sql.execute(`DELIMITER //
CREATE TRIGGER ${trigger} BEFORE INSERT ON log FOR EACH ROW BEGIN
 IF NEW.action='unlink' AND NEW.content='lab' AND NEW.demographic_no=${s.patient} AND NEW.contentId IN (${quoted}) THEN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic late unlink audit fault'; END IF; END//
DELIMITER ;`);
    const observed=s.sql.rows(`SELECT EVENT_OBJECT_TABLE,ACTION_TIMING,EVENT_MANIPULATION,ACTION_STATEMENT FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() AND TRIGGER_NAME=${q(trigger)}`);
    h.assert(observed.length===1&&observed[0][0]==='log'&&observed[0][1]==='BEFORE'&&observed[0][2]==='INSERT'&&observed[0][3].includes('Synthetic late unlink audit fault'),'Physical audit fault trigger is not armed');
    let failedResponse;
    const alerts=await h.withExpectedDialogs(opened.report,async()=>{
     const alert=opened.report.waitForEvent('dialog',{predicate:d=>d.type()==='alert',timeout:15000});
     const [response]=await Promise.all([opened.report.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/UnlinkDemographic')),
      opened.report.locator('input[onclick^="unlinkDemographic("]').click()]);
     failedResponse=response;h.assert(response.status()===500,'Injected late audit failure must return HTTP 500');await alert;
    },{promptText:'Injected synthetic audit failure'});
    h.assert(alerts.some(e=>e.type==='alert'&&/not.*confirmed|error|refresh|try again/i.test(e.text)),'Audit failure lacks a visible recovery alert');
    h.assert(clinical()===beforeFault,'Late audit failure changed committed clinical/routing/provenance rows');h.assert(raw()===rawBefore,'Late audit failure changed source records');
    const expected=s.recorder.badResponses.slice(badMark).filter(e=>e.status===500&&e.method==='POST'&&e.url===failedResponse.url());
    h.assert(expected.length===1,'Expected exactly one observed audit-fault response');s.recorder.badResponses=s.recorder.badResponses.filter(e=>!expected.includes(e));
    const resources=s.recorder.consoleIssues.slice(consoleMark).filter(e=>e.location?.url===failedResponse.url()&&/^Failed to load resource:.*500/.test(e.text));
    h.assert(resources.length<=1,'Duplicate controlled-fault resource errors');s.recorder.consoleIssues=s.recorder.consoleIssues.filter(e=>!resources.includes(e));
    console.log('PASS physical MariaDB late audit INSERT failure: HTTP 500, visible recovery and complete clinical/source rollback');
   }finally{
    s.sql.execute(`DROP TRIGGER IF EXISTS ${trigger}`);
    h.assert(s.sql.value(`SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() AND TRIGGER_NAME=${q(trigger)}`)==='0','Audit fault trigger remains');
   }
  }
  await h.withExpectedDialogs(opened.report,async()=>{
   const [response]=await Promise.all([opened.report.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/UnlinkDemographic')),
    opened.report.locator('input[onclick^="unlinkDemographic("]').click()]);
   h.assert(response.status()===200,'Unlink failed with HTTP '+response.status());
   // The native success handler immediately reloads; its response body may already be detached.
   // Assert committed clinical rows below instead of racing Chromium's response-body lifetime.
   await opened.report.waitForLoadState('networkidle');
  },{promptText:'Incorrect synthetic patient match'});
  record(s.sql.value(`SELECT COUNT(*) FROM patientLabRouting WHERE lab_type='HL7' AND lab_no IN (${list}) AND demographic_no>0`)==='0','Entire chain must be unmatched');
  record(s.sql.value(`SELECT COUNT(*) FROM measurements WHERE id IN (${activeIds.join(',')})`)==='0','Every active wrong-patient chain projection must be removed');
  record(s.sql.value(`SELECT COUNT(*) FROM measurementsDeleted WHERE originalId IN (${activeIds.join(',')}) AND demographicNo=${s.patient}`)===String(activeIds.length),'Every removed projection must have an archive');
  record(s.sql.value(`SELECT COUNT(*) FROM measurementsExt WHERE measurement_id IN (${activeIds.join(',')}) AND keyval='lab_proj_archive' AND val='UNLINK'`)===String(activeIds.length),'Every archive must retain its exact untruncated UNLINK provenance key/value');
  for(const [measurement,key,value] of extBefore)record(s.sql.value(`SELECT COUNT(*) FROM measurementsExt WHERE measurement_id=${measurement} AND keyval=${q(key)} AND val=${q(value)}`)==='1','Original projection provenance must remain');
  for(const [id,lab,provider] of independent)record(s.sql.value(`SELECT COUNT(*) FROM providerLabRouting WHERE id=${id} AND lab_type='HL7' AND lab_no=${lab} AND provider_no=${q(provider)} AND status='N' AND mrpDemographicNo IS NULL`)==='1','Independent provider route must reopen: '+id);
  for(const [id] of automatic)record(s.sql.value(`SELECT COUNT(*) FROM providerLabRouting WHERE id=${id}`)==='0','Obsolete automatic MRP route must be revoked: '+id);
  h.assert(raw()===rawBefore,'Unlink changed raw source records');
  const after=clinical(),badMark=s.recorder.badResponses.length,consoleMark=s.recorder.consoleIssues.length;
  let staleStatus;
  const dialogs=await h.withExpectedDialogs(stale,async()=>{
   const [response]=await Promise.all([stale.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/UnlinkDemographic')),
    stale.locator('input[onclick^="unlinkDemographic("]').click()]);staleStatus=response.status();
   await stale.waitForTimeout(500);
  },{promptText:'Repeated obsolete synthetic unlink'});
  record(staleStatus===409,'Obsolete second tab must receive HTTP 409');record(clinical()===after,'Obsolete retry must leave committed clinical state unchanged');
  if(staleStatus===409){
   h.assert(dialogs.some(e=>e.type==='alert'&&/changed|refresh|reload|match/i.test(e.text)),'Stale rejection lacks a visible recovery alert');
   const expected=s.recorder.badResponses.slice(badMark).filter(e=>e.label==='unlink-stale-tab'&&e.status===409&&e.method==='POST'&&new URL(e.url).pathname.endsWith('/UnlinkDemographic'));
   h.assert(expected.length===1,'Expected exactly the observed stale-unlink rejection');
   s.recorder.badResponses=s.recorder.badResponses.filter(e=>!expected.includes(e));
   const resource=s.recorder.consoleIssues.slice(consoleMark).filter(e=>e.label==='unlink-stale-tab'&&/Failed to load resource:.*status of 409/.test(e.text)&&new URL(e.location.url).pathname.endsWith('/UnlinkDemographic'));
   h.assert(resource.length<=1,'Unexpected duplicate stale-rejection console errors');s.recorder.consoleIssues=s.recorder.consoleIssues.filter(e=>!resource.includes(e));
   console.log('PASS explicit stale-tab HTTP 409, recovery dialog and unchanged clinical state');
  }
  h.assert(raw()===rawBefore,'Retry changed raw source records');
  fs.writeFileSync(source.filePath,Buffer.from(versionMessage(source.accession,s.marker,VERSIONS.older),'latin1'));
  h.assert(await uploadThroughPopup(s,source.filePath,source.fileName)==='Uploaded successfully','Later delivery was not accepted');
  const allIds=ids();h.assert(allIds.length===3,'Expected third independent source delivery');
  record(s.sql.value(`SELECT COUNT(*) FROM patientLabRouting WHERE lab_type='HL7' AND lab_no IN (${allIds.join(',')}) AND demographic_no>0`)==='0','A later delivery must not restore a deliberately removed wrong-patient match');
  record(s.sql.value(`SELECT COUNT(*) FROM measurements m JOIN measurementsExt e ON e.measurement_id=m.id AND e.keyval='lab_no' WHERE e.val IN (${allIds.map(q).join(',')})`)==='0','A later delivery must not restore wrong-patient projections');
  h.assert(raw()===rawBefore,'Later delivery changed earlier raw source records');
  console.log('EVIDENCE '+JSON.stringify({sourceVersions:allIds.length,independentRoutes:independent.length,automaticRoutes:automatic.length,controlRoutes,activeBefore:active.length,archivedAfter:s.sql.value(`SELECT COUNT(*) FROM measurementsDeleted WHERE originalId IN (${activeIds.join(',')})`),staleStatus}));
  for(const p of new Set([opened.report,stale,opened.inbox]))if(p!==s.schedule&&!p.isClosed())await p.close();
 }});
 h.assertStrictPage(s.recorder);h.assert(findings.length===0,findings.join('; '));
}
runWorkflow('measurement-unlink-chain',workflow);
