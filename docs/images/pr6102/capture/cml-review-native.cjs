/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const root=process.env.CARLOS_TEST_ROOT||'/root/carlos';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const h=require(root+'/scripts/lib/playwright-harness'),ui=require(root+'/scripts/lib/playwright-ui');
const {runWorkflow}=require(root+'/scripts/lib/workflow-session');
const {workflow:uploadFixture,uploadThroughPopup}=require(root+'/scripts/lab-upload-playwright-checks');
const {versionMessage,VERSIONS}=require(root+'/scripts/lab-version-ingestion-playwright-checks');
const {readPatientState,assertPatientStateUnchanged}=require(root+'/scripts/echart-two-patient-isolation-playwright-checks');
const {openInbox,openSidebar,setMode,search}=require(root+'/scripts/inbox-file-forward-playwright-checks');
const q=h.sqlString;
function message(accession,last,version,second){
 const lines=versionMessage(accession,last,version).split('\r');
 if(second!==undefined){const row=lines.find(line=>line.startsWith('OBX|')).split('|');Object.assign(row,{1:'2',3:'7020^SYNTHETIC SECOND RESULT',5:second,7:'3-20',8:'N'});lines.splice(lines.findIndex(line=>line.startsWith('FTS|')),0,row.join('|'));}
 return lines.join('\r');
}
async function workflow(s){
 h.assert(process.env.EXCLUSIVE==='1'&&process.env.CARLOS_DISPOSABLE_VM==='true','Requires an exclusive disposable installed database');
 const output=process.env.CML_REVIEW_SCREENSHOT_DIR;h.assert(output&&path.isAbsolute(output),'Absolute screenshot directory required');fs.mkdirSync(output,{recursive:true});
 const captures=[];
 async function capture(page,label){const image=String(captures.length+1).padStart(3,'0')+'-'+label+'.png',file=path.join(output,image);await page.screenshot({path:file,fullPage:true});captures.push({image,label,route:new URL(page.url()).pathname,browser:s.context.browser().version(),sha256:crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex')});fs.writeFileSync(path.join(output,'captures.json'),JSON.stringify(captures,null,2)+'\n');}
 await s.chart();
 await uploadFixture(s,{initialContent:(accession,last)=>message(accession,last,VERSIONS.original,'9.2'),afterInitial:async source=>{
  const ids=()=>s.sql.rows(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum=${q(source.accession)} ORDER BY lab_no`).flat();
  const values=()=>s.sql.rows(`SELECT m.dataField FROM measurements m JOIN measurementsExt e ON e.measurement_id=m.id AND e.keyval='lab_no' WHERE e.val IN (${ids().map(q).join(',')}) ORDER BY m.dataField`).flat();
  const status=id=>s.sql.value(`SELECT status FROM providerLabRouting WHERE lab_type='HL7' AND lab_no=${id} AND provider_no=${q(s.provider)}`);
  const raw=()=>JSON.stringify({messages:s.sql.rows(`SELECT * FROM hl7TextMessage WHERE lab_id IN (${ids().join(',')}) ORDER BY lab_id`),info:s.sql.rows(`SELECT * FROM hl7TextInfo WHERE lab_no IN (${ids().join(',')}) ORDER BY lab_no`),patient:s.sql.rows(`SELECT * FROM patientLabRouting WHERE lab_type='HL7' AND lab_no IN (${ids().join(',')}) ORDER BY id`)});
  s.cleanup(()=>{const owned=ids().map(q).join(',');if(owned)s.sql.execute(`DELETE FROM log WHERE content IN ('lab','HL7_LAB_PROJECTION') AND demographic_no=${s.patient} AND contentId IN (${owned})`);});
  async function upload(version,second){fs.writeFileSync(source.filePath,Buffer.from(message(source.accession,s.marker,version,second),'latin1'));h.assert(await uploadThroughPopup(s,source.filePath,source.fileName)==='Uploaded successfully','Native source upload failed');return ids().at(-1);}
  async function opened(id){
   if(s.schedule.isClosed())s.schedule=await s.context.newPage();
   if(s.schedule.url()!==s.scheduleUrl)await s.schedule.goto(s.scheduleUrl,{waitUntil:'load'});
   const inbox=await openInbox(s.schedule,s.context,s.recorder,'cml-source-review-inbox');await setMode(inbox,'list');await openSidebar(inbox);
   await inbox.locator('#statusAll').check();await inbox.locator('#specificPatients').check();await inbox.locator('#inputLastName').fill(s.marker);await search(inbox);
   const row=inbox.locator(`tr[data-lab-type="HL7"][data-segment-id="${id}"]`);await row.waitFor({state:'visible'});
   const report=await ui.clickOpensPopup(inbox,row.locator('a[onclick*="reportWindow"]').first(),{context:s.context,recorder:s.recorder,label:'cml-review-source',timeout:30000});await report.waitForLoadState('networkidle');return{inbox,report};
  }
  async function close(open){for(const page of new Set([open.report,open.inbox]))if(page!==s.schedule&&!page.isClosed()){await page.waitForLoadState('networkidle');await page.close();}}
  async function acknowledge(open){
   const before=raw(),clinical=readPatientState(s.sql,s.patient),closed=open.report.waitForEvent('close',{timeout:30000});closed.catch(()=>{});
   await h.withExpectedDialogs(open.report,async()=>{const [response]=await Promise.all([open.report.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/oscarMDS/UpdateStatus')),open.report.locator('input[onclick*="ackLab"],button[onclick*="ackLab"]').first().click()]);h.assert(response.status()===200,'Native acknowledgment failed');});
   await closed;h.assert(raw()===before,'Acknowledgment changed raw source or patient membership');assertPatientStateUnchanged(clinical,readPatientState(s.sql,s.patient),'Acknowledgment clinical isolation');await close(open);
  }
  const original=ids()[0];h.assert(JSON.stringify(values())===JSON.stringify(['5.1','9.2']),'Original full projection missing');
  const partial=await upload(VERSIONS.corrected);h.assert(JSON.stringify(values())===JSON.stringify(['12.7','9.2']),'Partial correction erased an omitted observation');
  let open=await opened(partial);await capture(open.report,'partial-correction-review');await acknowledge(open);
  open=await opened(original);await capture(open.report,'original-after-partial-review');
  h.assert(status(partial)==='A'&&status(original)==='N','Partial review silently filed the unread omitted observation');await close(open);
  const full=await upload({...VERSIONS.corrected,key:'FULL-CORRECTION',time:'20260310140000',value:'15.2'},'9.3');
  await acknowledge(await opened(full));h.assert(status(full)==='A'&&status(original)!=='N'&&status(partial)==='A','Fully covering review failed or erased prior acknowledgment');
  const conflict=await upload({...VERSIONS.corrected,key:'FULL-CONFLICT',time:'20260310140000',value:'16.1'},'9.3');
  open=await opened(conflict);h.assert(await open.report.locator('#cmlProjectionReview').isVisible(),'Conflicting source lacks visible review warning');
  const reviews=()=>JSON.stringify(s.sql.rows(`SELECT * FROM providerLabRouting WHERE lab_type='HL7' AND lab_no IN (${ids().join(',')}) ORDER BY id`));
  const clinical=readPatientState(s.sql,s.patient),reviewBefore=reviews(),rawBefore=raw(),bad=s.recorder.badResponses.length,consoleBefore=s.recorder.consoleIssues.length;let refused;
  const dialogs=await h.withExpectedDialogs(open.report,async()=>{
   const alert=open.report.waitForEvent('dialog',{predicate:d=>d.type()==='alert',timeout:15000});alert.catch(()=>{});
   const [response]=await Promise.all([open.report.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/oscarMDS/UpdateStatus')),open.report.locator('input[onclick*="ackLab"],button[onclick*="ackLab"]').first().click()]);refused=response;h.assert(response.status()===500,'Conflicting revisions must refuse automatic acknowledgment');await alert;
  });
  h.assert(dialogs.some(d=>d.type==='alert'&&/error|refresh|try again/i.test(d.text)),'Conflict acknowledgment failure lacks visible recovery');
  h.assert(reviews()===reviewBefore&&raw()===rawBefore,'Refused acknowledgment changed source or review state');assertPatientStateUnchanged(clinical,readPatientState(s.sql,s.patient),'Refused conflicting acknowledgment');
  const expected=s.recorder.badResponses.slice(bad).filter(r=>r.status===500&&r.method==='POST'&&r.url===refused.url());h.assert(expected.length===1,'Expected one controlled conflict rejection');s.recorder.badResponses=s.recorder.badResponses.filter(r=>!expected.includes(r));
  const expectedConsole=s.recorder.consoleIssues.slice(consoleBefore).filter(e=>(e.location?.url===refused.url()&&/^Failed to load resource:.*500/.test(e.text))||e.text.startsWith('Failed to process lab action:'));
  h.assert(expectedConsole.length>=1&&expectedConsole.length<=2,'Unexpected conflict acknowledgment diagnostics');s.recorder.consoleIssues=s.recorder.consoleIssues.filter(e=>!expectedConsole.includes(e));
  await capture(open.report,'conflicting-revision-ack-blocked');await close(open);
  const resolved=await upload({...VERSIONS.corrected,key:'UNAMBIGUOUS-CORRECTION',time:'20260310160000',value:'17.2'},'9.4');
  h.assert(JSON.stringify(values())===JSON.stringify(['17.2','9.4']),'Later explicit correction failed to resolve an obsolete conflict');
  open=await opened(resolved);h.assert(await open.report.locator('#cmlProjectionReview').count()===0,'Resolved source still requires conflict review');await capture(open.report,'newer-unambiguous-resolution');await acknowledge(open);
  h.assert(ids().every(id=>status(id)!=='N')&&status(resolved)==='A'&&status(full)==='A'&&status(partial)==='A','Fully covering review lost prior acknowledgments or left covered rows NEW');
  open=await opened(resolved);await capture(open.report,'fully-covered-history-reviewed');await close(open);
  console.log('EVIDENCE '+JSON.stringify({sourceVersions:ids().length,partialLeavesOmittedSourceNew:true,completeCoverageFilesOlder:true,conflictAckStatus:refused.status(),visibleRecovery:true,blockedMutationUnchanged:true,newerResolvesConflict:true,acknowledgmentsPreserved:true,activeValues:values()}));
 }});
 h.assertStrictPage(s.recorder);
}
runWorkflow('cml-partial-correction-source-review',workflow);
