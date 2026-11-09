/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const root=process.env.CARLOS_TEST_ROOT||'/root/carlos';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const h=require(root+'/scripts/lib/playwright-harness'),ui=require(root+'/scripts/lib/playwright-ui');
const {runWorkflow}=require(root+'/scripts/lib/workflow-session');
const {workflow:uploadFixture,uploadThroughPopup}=require(root+'/scripts/lab-upload-playwright-checks');
const {versionMessage,VERSIONS}=require(root+'/scripts/lab-version-ingestion-playwright-checks');
const {readPatientState,assertPatientStateUnchanged}=require(root+'/scripts/echart-two-patient-isolation-playwright-checks');
const {fence,settleWrites}=require('./mysql-row-fence.cjs');
const {openInbox,openSidebar,setMode,search}=require(root+'/scripts/inbox-file-forward-playwright-checks');
const q=h.sqlString;
async function workflow(s){
 h.assert(process.env.EXCLUSIVE==='1'&&process.env.CARLOS_DISPOSABLE_VM==='true','Requires an exclusive disposable installed database');
 const output=process.env.CML_REPAIR_SCREENSHOT_DIR;h.assert(output&&path.isAbsolute(output),'Absolute screenshot directory required');fs.mkdirSync(output,{recursive:true});
 const captures=[];
 async function capture(page,label){const image=String(captures.length+1).padStart(3,'0')+'-'+label+'.png',file=path.join(output,image);
  await page.screenshot({path:file,fullPage:true});captures.push({image,label,browser:s.context.browser().version(),sha256:crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex')});fs.writeFileSync(path.join(output,'captures.json'),JSON.stringify(captures,null,2)+'\n');}
 await s.chart();
 await uploadFixture(s,{initialContent:(acc,last)=>versionMessage(acc,last,VERSIONS.original),afterInitial:async source=>{
  const ids=()=>s.sql.rows(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum=${q(source.accession)} ORDER BY lab_no`).flat();
  const original=ids()[0],old=s.sql.rows(`SELECT m.id,m.dataField FROM measurements m JOIN measurementsExt e ON e.measurement_id=m.id AND e.keyval='lab_no' WHERE e.val=${q(original)}`);
  h.assert(old.length===1&&old[0][1]==='5.1','Original projection missing');
  fs.writeFileSync(source.filePath,Buffer.from(versionMessage(source.accession,s.marker,VERSIONS.corrected),'latin1'));
  h.assert(await uploadThroughPopup(s,source.filePath,source.fileName)==='Uploaded successfully','Correction failed');
  const chain=ids();h.assert(chain.length===2,'Expected two raw source reports');const corrected=chain[1],list=chain.join(','),quoted=chain.map(q).join(',');
  const current=s.sql.rows(`SELECT m.id,m.dataField FROM measurements m JOIN measurementsExt e ON e.measurement_id=m.id AND e.keyval='lab_no' WHERE e.val IN (${quoted}) ORDER BY m.id`);
  h.assert(current.length===1&&current[0][1]==='12.7','Correction did not replace the active projection');
  h.assert(s.sql.value(`SELECT COUNT(*) FROM measurementsExt WHERE measurement_id=${current[0][0]} AND keyval='lab_proj_origin' AND CHAR_LENGTH(val)=64`)==='1','Origin fingerprint was truncated in physical schema');
  h.assert(s.sql.value(`SELECT COUNT(*) FROM measurementsExt WHERE measurement_id=${old[0][0]} AND keyval='lab_proj_archive' AND val='SUPERSEDED'`)==='1','Supersession marker was truncated');
  const sourceState=()=>JSON.stringify({messages:s.sql.rows(`SELECT * FROM hl7TextMessage WHERE lab_id IN (${list}) ORDER BY lab_id`),info:s.sql.rows(`SELECT * FROM hl7TextInfo WHERE lab_no IN (${list}) ORDER BY lab_no`),patient:s.sql.rows(`SELECT * FROM patientLabRouting WHERE lab_type='HL7' AND lab_no IN (${list}) ORDER BY id`),provider:s.sql.rows(`SELECT * FROM providerLabRouting WHERE lab_type='HL7' AND lab_no IN (${list}) ORDER BY id`)});
  const clinical=()=>JSON.stringify({patient:readPatientState(s.sql,s.patient),archives:s.sql.rows(`SELECT * FROM measurementsDeleted WHERE demographicNo=${s.patient} ORDER BY id`),ext:s.sql.rows(`SELECT e.* FROM measurementsExt e WHERE e.measurement_id IN (SELECT originalId FROM measurementsDeleted WHERE demographicNo=${s.patient}) OR e.measurement_id IN (SELECT id FROM measurements WHERE demographicNo=${s.patient}) ORDER BY e.id`),audit:s.sql.rows(`SELECT * FROM log WHERE content='HL7_LAB_PROJECTION' AND demographic_no=${s.patient} ORDER BY id`)});
  s.cleanup(()=>{const owned=ids().map(q).join(',');if(owned)s.sql.execute(`DELETE FROM log WHERE content IN ('HL7_LAB_PROJECTION','lab') AND demographic_no=${s.patient} AND contentId IN (${owned})`);});
  const untouched=sourceState();
  // Explicit owned setup reproduces the historical duplicate, without fabricating raw reports.
  s.sql.execute(`INSERT INTO measurements(id,type,demographicNo,providerNo,dataField,measuringInstruction,comments,dateObserved,dateEntered)
SELECT originalId,type,demographicNo,providerNo,dataField,measuringInstruction,comments,dateObserved,dateEntered FROM measurementsDeleted WHERE originalId=${old[0][0]} AND demographicNo=${s.patient};
DELETE FROM measurementsDeleted WHERE originalId=${old[0][0]} AND demographicNo=${s.patient};
DELETE FROM measurementsExt WHERE measurement_id=${old[0][0]} AND keyval IN ('lab_proj_archive','lab_proj_origin')`);
  const repair=await s.context.newPage();h.wireStrictPage(repair,'cml-reviewed-repair',s.recorder);
  const url=new URL('/carlos/admin/CmlProjectionRepair',s.schedule.url());
  async function preview(){await repair.goto(url.href,{waitUntil:'load'});await repair.locator('input[name=reportNo]').fill(corrected);
   await ui.clickAndAwaitReload(repair,repair.getByRole('button',{name:'Preview repair',exact:true}));}
  const before=clinical();await preview();h.assert(clinical()===before,'Read-only preview changed clinical state');
  h.assert((await repair.locator('#archiveCount').innerText()).trim()==='1'&&(await repair.locator('#createCount').innerText()).trim()==='0','Incorrect repair plan');
  await capture(repair,'duplicate-preview');
  const stalePayload=await repair.locator('form[method=post]').evaluate(form=>Object.fromEntries(new FormData(form)));
  const trigger='pw_repair_'+source.stamp;h.assert(s.sql.value(`SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() AND TRIGGER_NAME=${q(trigger)}`)==='0','Fault trigger already exists');
  try{
   s.sql.execute(`DELIMITER //
CREATE TRIGGER ${trigger} BEFORE INSERT ON log FOR EACH ROW BEGIN
 IF NEW.content='HL7_LAB_PROJECTION' AND NEW.demographic_no=${s.patient} THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic late repair audit fault'; END IF; END//
DELIMITER ;`);
   const mark=s.recorder.badResponses.length,consoleMark=s.recorder.consoleIssues.length;
   const [failed]=await Promise.all([repair.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname===url.pathname),ui.clickAndAwaitReload(repair,repair.locator('form[method=post] button'))]);
   h.assert(failed.status()===500&&await repair.getByRole('alert').isVisible(),'Late audit failure did not show recovery');
   h.assert(clinical()===before&&sourceState()===untouched,'Repair audit failure left a partial commit');await capture(repair,'audit-failure-rollback');
   const expected=s.recorder.badResponses.slice(mark).filter(r=>r.status===500&&r.url===failed.url());h.assert(expected.length===1,'Expected one physical repair fault');s.recorder.badResponses=s.recorder.badResponses.filter(r=>!expected.includes(r));
   const resources=s.recorder.consoleIssues.slice(consoleMark).filter(e=>e.location?.url===failed.url()&&/^Failed to load resource:.*500/.test(e.text));h.assert(resources.length<=1,'Unexpected repeated repair resource failures');s.recorder.consoleIssues=s.recorder.consoleIssues.filter(e=>!resources.includes(e));
  }finally{s.sql.execute(`DROP TRIGGER IF EXISTS ${trigger}`);}
  await preview();await ui.clickAndAwaitReload(repair,repair.locator('form[method=post] button'));
  h.assert(await repair.getByRole('status').isVisible(),'Committed repair lacks confirmation');await capture(repair,'repair-committed');
  h.assert(s.sql.value(`SELECT COUNT(*) FROM measurements m JOIN measurementsExt e ON e.measurement_id=m.id AND e.keyval='lab_no' WHERE e.val IN (${quoted})`)==='1','Repair did not leave exactly one active winner');
  h.assert(s.sql.value(`SELECT COUNT(*) FROM measurementsExt WHERE measurement_id=${old[0][0]} AND keyval='lab_proj_archive' AND val='REPAIR'`)==='1','Exact repair provenance missing');
  h.assert(s.sql.value(`SELECT COUNT(*) FROM log WHERE content='HL7_LAB_PROJECTION' AND demographic_no=${s.patient}`)==='1','Repair must audit exactly once');
  const committed=clinical();const stale=await s.context.request.post(url.href,{form:stalePayload});h.assert(stale.status()===409,'Stale apply must return 409');h.assert(clinical()===committed,'Stale apply changed clinical state');
  await preview();h.assert(await repair.locator('form[method=post]').count()===0,'No-op preview offers another repair');h.assert(clinical()===committed,'No-op preview wrote clinical state');await capture(repair,'idempotent-preview');
  h.assert(sourceState()===untouched,'Repair changed raw source or review state');
  // Reproduce the duplicate again, then force real repair/import transactions to
  // queue on the same source row. Either serialization must leave the correction active.
  s.sql.execute(`INSERT INTO measurements(id,type,demographicNo,providerNo,dataField,measuringInstruction,comments,dateObserved,dateEntered)
SELECT originalId,type,demographicNo,providerNo,dataField,measuringInstruction,comments,dateObserved,dateEntered FROM measurementsDeleted WHERE originalId=${old[0][0]} AND demographicNo=${s.patient};
DELETE FROM measurementsDeleted WHERE originalId=${old[0][0]} AND demographicNo=${s.patient};
DELETE FROM measurementsExt WHERE measurement_id=${old[0][0]} AND keyval IN ('lab_proj_archive','lab_proj_origin')`);
  await preview();const concurrentPayload=await repair.locator('form[method=post]').evaluate(form=>Object.fromEntries(new FormData(form)));
  const key=crypto.createHash('sha256').update('3:CML'+source.accession).digest().readInt32BE(0)|0x80000000;
  h.assert(s.sql.value(`SELECT COUNT(*) FROM providerLabRoutingLock WHERE lab_no=${key}`)==='1','Source coordination row missing');
  const held=await fence(s.config.mysql,key);
  let applying,uploading,queued=false,dispatchError;
  try{
   applying=s.context.request.post(url.href,{form:concurrentPayload,timeout:45000});applying.catch(()=>{});
   fs.writeFileSync(source.filePath,Buffer.from(versionMessage(source.accession,s.marker,VERSIONS.older),'latin1'));
   uploading=uploadThroughPopup(s,source.filePath,source.fileName);uploading.catch(()=>{});
   const deadline=Date.now()+15000;
   while(Date.now()<deadline){
    const waiters=Number(s.sql.value(`SELECT COUNT(*) FROM information_schema.INNODB_LOCK_WAITS w JOIN information_schema.INNODB_TRX b ON b.trx_id=w.blocking_trx_id WHERE b.trx_mysql_thread_id=${held.connection}`));
    if(waiters>=2){queued=true;break;}await new Promise(resolve=>setTimeout(resolve,100));
   }
  }catch(error){dispatchError=error;}finally{try{await held.release();}catch(error){dispatchError=dispatchError?new AggregateError([dispatchError,error],'Dispatch and fence release failed'):error;}}
  const [applied,uploaded]=await settleWrites(dispatchError,[applying,uploading]);
  h.assert(queued,'Did not prove both native transactions waited on the physical source fence');
  h.assert([200,409].includes(applied.status())&&uploaded==='Uploaded successfully','Concurrent native mutation failed');
  const winners=s.sql.rows(`SELECT m.dataField FROM measurements m JOIN measurementsExt e ON e.measurement_id=m.id AND e.keyval='lab_no' WHERE e.val IN (${ids().map(q).join(',')})`);
  h.assert(winners.length===1&&winners[0][0]==='12.7','Concurrent repair/import lost or demoted the correction');
  h.assert(ids().length===3,'Concurrent import lost a raw source');await preview();await capture(repair,'concurrent-repair-import');
  const unavailable={...VERSIONS.corrected,key:'MISSING-TIME',time:'20260310140000',value:'20.4'};
  const missing=versionMessage(source.accession,s.marker,unavailable).split('\r').map(line=>{
   const fields=line.split('|');if(fields[0]==='OBR'){fields[7]='';fields[22]='';}if(fields[0]==='OBX')fields[14]='';return fields.join('|');
  }).join('\r');
  fs.writeFileSync(source.filePath,Buffer.from(missing,'latin1'));h.assert(await uploadThroughPopup(s,source.filePath,source.fileName)==='Uploaded successfully','Unorderable raw report was lost');
  const invalid=ids().at(-1);h.assert(ids().length===4,'Missing-time raw source did not persist');
  const inbox=await openInbox(s.schedule,s.context,s.recorder,'cml-missing-time-inbox');await setMode(inbox,'list');await openSidebar(inbox);
  await inbox.locator('#statusAll').check();await inbox.locator('#specificPatients').check();await inbox.locator('#inputLastName').fill(s.marker);await search(inbox);
  const row=inbox.locator(`tr[data-lab-type="HL7"][data-segment-id="${invalid}"]`);await row.waitFor({state:'visible'});
  const raw=await ui.clickOpensPopup(inbox,row.locator('a[onclick*="reportWindow"]').first(),{context:s.context,recorder:s.recorder,label:'cml-missing-time-source',timeout:30000});
  h.assert(await raw.locator('#cmlProjectionReview').isVisible(),'Unorderable report lacks the clinical review warning');
  h.assert((await raw.locator('body').innerText()).includes('20.4'),'Unorderable raw source result is inaccessible');await capture(raw,'missing-revision-source-review');
  const {heldClinicalRequests}=require(root+'/scripts/lib/held-clinical-requests'),graph=require(root+'/scripts/lib/pdf-graph');
  const printGuard=heldClinicalRequests(s,{identity:s.marker,matches:u=>u.origin===new URL(s.config.baseUrl).origin&&u.pathname.endsWith('/lab/CA/ALL/PrintPDF'),select:request=>{
   const body=new URLSearchParams(request.postData()||'');h.assert(request.method()==='POST'&&body.get('segmentID')===invalid,'Raw PDF escaped the owned source');return{kind:'print',write:false};
  }});s.cleanup(()=>printGuard.cleanup());await printGuard.install();const rawUrl=raw.url();
  try {
  await raw.locator('input[onclick^="printPDF("]').first().click();
  const receipt=await printGuard.wait(r=>r.kind==='print'&&r.state==='acknowledged');h.assert(receipt.status===200&&(receipt.headers['content-type']||'').startsWith('application/pdf'),'Missing revision blocked raw PDF');
  const text=Array.from({length:graph.pageCount(receipt.body)},(_,i)=>graph.pageText(receipt.body,i+1)).join(' ').replace(/\s+/g,' ');
  h.assert(text.includes('20.4')&&text.includes('do not rely'),'Raw PDF lost the source value or ambiguity warning');fs.writeFileSync(path.join(output,'missing-revision-source.pdf'),receipt.body);
  } finally { await printGuard.shutdown(); }
  if(!raw.isClosed())await raw.goto(rawUrl,{waitUntil:'networkidle'});

  const unlinkFence=await fence(s.config.mysql,key);let unlinkStatus,unlinking,lateUpload,unlinkQueued=false,unlinkError;
  const unlinkBad=s.recorder.badResponses.length,unlinkConsole=s.recorder.consoleIssues.length;
  async function clickUnlink(){await h.withExpectedDialogs(raw,async()=>{
   const [response]=await Promise.all([raw.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/UnlinkDemographic')),raw.locator('input[onclick^="unlinkDemographic("]').click()]);
   unlinkStatus=response.status();h.assert([200,409].includes(unlinkStatus),'Missing-time report prevented complete-chain unlink');
   if(unlinkStatus===200)await raw.waitForLoadState('networkidle');
  },{promptText:'Incorrect synthetic patient match with missing revision time'});}
  try{
   unlinking=clickUnlink();unlinking.catch(()=>{});
   fs.writeFileSync(source.filePath,Buffer.from(versionMessage(source.accession,s.marker,{...VERSIONS.older,key:'CONCURRENT-LATE',time:'20260310060000'}),'latin1'));
   lateUpload=uploadThroughPopup(s,source.filePath,source.fileName);lateUpload.catch(()=>{});
   const deadline=Date.now()+15000;
   while(Date.now()<deadline){
    if(Number(s.sql.value(`SELECT COUNT(*) FROM information_schema.INNODB_LOCK_WAITS w JOIN information_schema.INNODB_TRX b ON b.trx_id=w.blocking_trx_id WHERE b.trx_mysql_thread_id=${unlinkFence.connection}`))>=2){unlinkQueued=true;break;}
    await new Promise(resolve=>setTimeout(resolve,100));
   }
  }catch(error){unlinkError=error;}finally{try{await unlinkFence.release();}catch(error){unlinkError=unlinkError?new AggregateError([unlinkError,error],'Dispatch and fence release failed'):error;}}
  const [,lateResult]=await settleWrites(unlinkError,[unlinking,lateUpload]);h.assert(unlinkQueued&&lateResult==='Uploaded successfully','Native unlink/import concurrency was not proven');
  const concurrentUnlinkStatus=unlinkStatus;
  if(unlinkStatus===409){
   const expected=s.recorder.badResponses.slice(unlinkBad).filter(r=>r.status===409&&new URL(r.url).pathname.endsWith('/UnlinkDemographic'));
   h.assert(expected.length===1,'Expected one stale concurrent unlink response');s.recorder.badResponses=s.recorder.badResponses.filter(r=>!expected.includes(r));
   const resources=s.recorder.consoleIssues.slice(unlinkConsole).filter(e=>e.location?.url&&new URL(e.location.url).pathname.endsWith('/UnlinkDemographic')&&/^Failed to load resource:.*409/.test(e.text));
   h.assert(resources.length<=1,'Unexpected concurrent unlink resource failures');s.recorder.consoleIssues=s.recorder.consoleIssues.filter(e=>!resources.includes(e));
   await raw.reload({waitUntil:'networkidle'});await capture(raw,'concurrent-unlink-stale-retry');await clickUnlink();h.assert(unlinkStatus===200,'Refreshed unlink retry failed');
  }
  h.assert(s.sql.value(`SELECT COUNT(*) FROM patientLabRouting WHERE lab_type='HL7' AND lab_no IN (${ids().join(',')}) AND demographic_no>0`)==='0','Missing-time chain remained matched');
  h.assert(s.sql.value(`SELECT COUNT(*) FROM measurements WHERE demographicNo=${s.patient}`)==='0','Missing-time unlink retained a projection');
  console.log('EVIDENCE '+JSON.stringify({sourceVersions:ids().length,stale:stale.status(),auditRollback:true,exactPhysicalMarkers:true,physicalWaiters:2,concurrentRepairStatus:applied.status(),missingTimeRawAndUnlink:true,concurrentUnlinkStatus,unlinkPhysicalWaiters:2}));
  for(const page of new Set([raw,inbox,repair]))if(!page.isClosed())await page.close();
 }});
 h.assertStrictPage(s.recorder);
}
runWorkflow('cml-reviewed-projection-repair',workflow);
