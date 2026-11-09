#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
// #5726: native ingestion with owned legacy-counter and unreadable-source recovery probes.
const fs=require('node:fs');
const h=require('./lib/playwright-harness'),ui=require('./lib/playwright-ui');
const {runWorkflow}=require('./lib/workflow-session');
const {workflow:uploadFixture,uploadThroughPopup}=require('./lab-upload-playwright-checks');
const {openInbox,openSidebar,setMode,search}=require('./inbox-file-forward-playwright-checks');
const {waitForNavbars}=require('./echart-navbar-modules-playwright-checks');
const {readPatientState,assertPatientStateUnchanged}=require('./echart-two-patient-isolation-playwright-checks');
const q=h.sqlString;
const VERSIONS={
  original:{key:'ORIGINAL',time:'20260310080000',date:'2026-03-10 08:00:00',value:'5.1',flag:'N',status:'F'},
  corrected:{key:'CORRECTION',time:'20260310120000',date:'2026-03-10 12:00:00',value:'12.7',flag:'H',status:'C'},
  older:{key:'OLDER',time:'20260310070000',date:'2026-03-10 07:00:00',value:'4.3',flag:'N',status:'F'},
  lateFinal:{key:'LATE_FINAL',time:'20260310120000',date:'2026-03-10 12:00:00',value:'8.6',flag:'N',status:'F'},
};

/** CML ORU^R01: explicit OBR-7/22/25 and OBX-5/8/11/14, one accession. */
function versionMessage(accession,last,version) {
  const orc=Array(16).fill(''),obr=Array(26).fill(''),obx=Array(15).fill('');
  Object.assign(orc,{0:'ORC',1:'RE',2:accession,5:version.status,12:'999998^DR. PROBE',15:version.time});
  Object.assign(obr,{0:'OBR',1:'1',2:accession,4:'ML70^SYNTHETIC PANEL',6:version.time,7:version.time,16:'999998^DR. PROBE',22:version.time,25:version.status});
  Object.assign(obx,{0:'OBX',1:'1',2:'NM',3:'7010^SYNTHETIC RESULT',4:'^^CHEMISTRY',5:version.value,6:'mmol/L',7:'3-8',8:version.flag,11:version.status,14:version.time});
  return [`MSH|^~\\&|Reports|CML|||${version.time}||ORU^R01|${accession}${version.key}|P|2.3`,
    `PID|1||||${last}^Workflow||19800102|F`,orc.join('|'),obr.join('|'),obx.join('|'),
    `NTE|1|L|${last} ${version.key} SYNTHETIC RESULT - NOT A PATIENT RESULT`,'FTS|1',''].join('\r');
}

/** Both source history and unrelated clinical state are independent SQL snapshots. */
async function workflow(s) {
  const {sql,patient,provider,marker}=s;
  const controlName=marker.slice(0,-1)+(marker.endsWith('0')?'1':'0');let control,controlClaimed=false;
  s.cleanup(()=>{
    if(!controlClaimed)return;
    const rows=sql.rows(`SELECT demographic_no FROM demographic WHERE last_name=${q(controlName)}`);
    h.assert(rows.length<=1&&(!control||!rows.length||rows[0][0]===control),'Control patient ownership changed');
    for(const [id] of rows){
      h.assert(/^[1-9]\d*$/.test(id),'Invalid control patient key');
      h.assert(sql.value(`SELECT COUNT(*) FROM patientLabRouting WHERE demographic_no=${id}`)==='0','Control lab cleanup failed; retain parent');
      const state=readPatientState(sql,id);h.assert(Object.entries(state).every(([key,rows])=>key==='demographic'||rows.length===0),'Control clinical residue remains');
      sql.execute(`DELETE FROM demographic WHERE demographic_no=${id} AND last_name=${q(controlName)}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${id}`)==='0','Control patient remains');
    }
  });
  h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE last_name=${q(controlName)}`)==='0','Control patient namespace is already owned');
  controlClaimed=true;
  control=sql.value(`INSERT INTO demographic(last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES(${q(controlName)},'Workflow','1980','01','02','F','AC',${q(provider)},'ON','ON','NR',NOW());SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(control),'Control patient was not created');
  // Establish the normal first-open chart state before read-isolation snapshots.
  await s.chart();
  const defects=[];const record=(okay,message)=>{if(!okay){defects.push(message);console.log('  FAIL lab versions: '+message);}};
  const labState=accession=>{
    const ids=sql.rows(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum=${q(accession)} ORDER BY lab_no`).flat();
    h.assert(ids.length&&ids.every(id=>/^[1-9]\d*$/.test(id)),'Lab source identities are missing or malformed');const list=ids.join(',');
    return {info:sql.rows(`SELECT * FROM hl7TextInfo WHERE lab_no IN (${list}) ORDER BY lab_no`),message:sql.rows(`SELECT * FROM hl7TextMessage WHERE lab_id IN (${list}) ORDER BY lab_id`),
      patient:sql.rows(`SELECT * FROM patientLabRouting WHERE lab_type='HL7' AND lab_no IN (${list}) ORDER BY id`),provider:sql.rows(`SELECT * FROM providerLabRouting WHERE lab_type='HL7' AND lab_no IN (${list}) ORDER BY id`),
      measurements:sql.rows(`SELECT m.*,e.id,e.keyval,e.val FROM measurements m JOIN measurementsExt e ON e.measurement_id=m.id WHERE m.id IN (SELECT measurement_id FROM measurementsExt WHERE keyval='lab_no' AND val IN (${ids.map(q).join(',')})) ORDER BY m.id,e.id`)};
  };
  await uploadFixture({...s,patient:control,marker:controlName,step:(label,body)=>s.step('control accession: '+label,body)},{initialContent:(accession,last)=>versionMessage(accession,last,VERSIONS.original),afterInitial:async controlSource=>{
    const controlBefore=labState(controlSource.accession),controlClinical=readPatientState(sql,control);
    const checkControl=()=>{h.assert(JSON.stringify(labState(controlSource.accession))===JSON.stringify(controlBefore),'Another patient/accession lab state changed');assertPatientStateUnchanged(controlClinical,readPatientState(sql,control),'Version ingestion control');};
    await uploadFixture(s,{initialContent:(accession,last)=>versionMessage(accession,last,VERSIONS.original),afterInitial:async source=>{
      const {accession,filePath,fileName}=source,known=new Map();
      const sourceRows=()=>sql.rows(`SELECT lab_no,obr_date,report_status,final_result_count FROM hl7TextInfo WHERE accessionNum=${q(accession)} ORDER BY lab_no`);
      function capture(version) {
        const rows=sourceRows().filter(row=>row[1]===version.date&&!known.has(row[0]));h.assert(rows.length===1,'Version metadata did not retain one new source at its exact observation time');
        const [id,date,status,finals]=rows[0];h.assert(/^[1-9]\d*$/.test(id),'Invalid ingested version ID');
        record(status===version.status,version.key+': stored report status differs');
        record(finals==='1',version.key+': completed observation was not counted');
        const raw=Buffer.from(sql.value(`SELECT message FROM hl7TextMessage WHERE lab_id=${id}`),'base64').toString('latin1');
        h.assert(raw.includes(`|${version.value}|mmol/L|3-8|${version.flag}|||${version.status}|`),version.key+': raw observation value/flag/status changed');
        h.assert(sql.value(`SELECT COUNT(*) FROM patientLabRouting WHERE lab_type='HL7' AND lab_no=${id} AND demographic_no=${patient}`)==='1'
          &&sql.value(`SELECT COUNT(*) FROM patientLabRouting WHERE lab_type='HL7' AND lab_no=${id}`)==='1','Version was not linked to exactly the intended patient');
        const routes=sql.rows(`SELECT provider_no,status FROM providerLabRouting WHERE lab_type='HL7' AND lab_no=${id} ORDER BY provider_no`);
        h.assert(routes.length&&routes.some(([owner])=>owner===provider),'Version has no native routing to the configured ordering provider');
        h.assert(new Set(routes.map(row=>row[0])).size===routes.length,'Version has duplicate provider routing');
        if(known.size)record(JSON.stringify(routes.map(row=>row[0]))===JSON.stringify([...known.values()][0].routes.map(row=>row[0])),version.key+': provider recipients differ');
        known.set(id,{...version,id,raw,routes,finals});checkControl();return id;
      }
      const original=capture(VERSIONS.original);
      async function inspect(report,id,label) {
        const version=known.get(id);h.assert(version,'Native report rendered an unowned version');
        const rendered=await report.locator('form[id^="acknowledgeForm_"]').evaluateAll(forms=>forms.map(form=>form.id.replace('acknowledgeForm_','')));
        h.assert(JSON.stringify(rendered)===JSON.stringify([id]),label+': report mixed or changed source identities');
        const name=report.getByRole('link',{name:'SYNTHETIC RESULT',exact:true});
        h.assert(await name.count()===1,label+': result name is missing or duplicated');
        const result=name.locator('xpath=ancestor::tr[1]');
        const cells=(await result.locator(':scope > td').allInnerTexts()).map(text=>text.trim());
        record(cells.includes(version.value)&&cells.includes(version.flag)&&cells.includes(version.date)&&cells.includes(version.status==='F'?'Final':'C'),label+': value/flag/time/status cells differ');
        h.assert((await report.locator('body').innerText()).includes(marker),label+': wrong patient identity');
      }
      async function inboxReport(requested) {
        const inbox=await openInbox(s.schedule,s.context,s.recorder,'ingested-versions-inbox');await setMode(inbox,'list');await openSidebar(inbox);
        await inbox.locator('#statusAll').check();await inbox.locator('#specificPatients').check();await inbox.locator('#inputLastName').fill(marker);await search(inbox);
        const visible=await inbox.locator('tr[data-lab-type="HL7"]').evaluateAll(rows=>rows.map(row=>row.getAttribute('data-segment-id')).sort());
        h.assert(JSON.stringify(visible)===JSON.stringify([...known.keys()].sort()),'Inbox ALL lost/duplicated a version or included the control patient');
        const row=inbox.locator(`tr[data-lab-type="HL7"][data-segment-id="${requested}"]`);await row.waitFor({state:'visible',timeout:30000});
        const report=await ui.clickOpensPopup(inbox,row.locator('a[onclick*="reportWindow"]').first(),{context:s.context,recorder:s.recorder,label:'ingested-version-report',timeout:30000});
        return {inbox,report};
      }
      async function retireReports(opened) {
        // Inbox may navigate the day sheet and reportWindow may reuse its named
        // window. Never close the schedule merely because a helper returned it.
        for(const page of new Set([opened.report,opened.inbox]))if(page!==s.schedule&&!page.isClosed()){
          await page.waitForLoadState('networkidle',{timeout:30000});await page.close();
        }
        if(s.schedule.isClosed())s.schedule=await s.context.newPage();
        await s.schedule.waitForLoadState('networkidle',{timeout:30000});
        h.relabelStrictPage(s.schedule,'ingested-versions-schedule');
        if(s.schedule.url()!==s.scheduleUrl)await s.schedule.goto(s.scheduleUrl,{waitUntil:'networkidle'});
        await s.schedule.locator('#inboxLink').waitFor({state:'visible'});
        await s.schedule.waitForFunction(()=>typeof window.jQuery==='function'&&window.jQuery.active===0);
      }
      let opened=await inboxReport(original);await inspect(opened.report,original,'Original before review');
      await s.step('native acknowledgment reviews the original before any correction is ingested',async()=>{
        await h.withExpectedDialogs(opened.report,async()=>{
          const [response]=await Promise.all([opened.report.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/oscarMDS/UpdateStatus')),
            opened.report.locator('input[onclick*="ackLab"],button[onclick*="ackLab"]').first().click()]);h.assert(response.status()===200,'Native acknowledgment failed');
        });
        h.assert(sql.value(`SELECT COUNT(*) FROM providerLabRouting WHERE lab_type='HL7' AND lab_no=${original} AND provider_no=${q(provider)} AND status='A'`)==='1','Original acknowledgment was not stored');
      });
      await retireReports(opened);
      const originalReviewed=sql.rows(`SELECT * FROM providerLabRouting WHERE lab_type='HL7' AND lab_no=${original} ORDER BY id`);
      async function verifySurfaces(expected,label) {
        const clinicalBefore=readPatientState(sql,patient),sourceBefore=labState(accession);
        opened=await inboxReport(expected);
        const ids=await opened.report.locator('form[id^="acknowledgeForm_"]').evaluateAll(forms=>forms.map(form=>form.id.replace('acknowledgeForm_','')));
        h.assert(ids.length===1&&known.has(ids[0]),label+': Inbox rendered an unknown source');
        record(ids[0]===expected,label+': selected Inbox correction opened another source');await inspect(opened.report,ids[0],label+' Inbox selected correction');
        for(const id of known.keys()) {
          if(id===ids[0])continue;
          // Match the actual native version href by its parsed source identity.
          const links=opened.report.locator('a[href*="ViewLabDisplay"]');let target;
          for(let index=0;index<await links.count();index++){const entry=links.nth(index);if(new URL(await entry.getAttribute('href'),opened.report.url()).searchParams.get('segmentID')===id){h.assert(!target,'Duplicate native historical-version link');target=entry;}}
          h.assert(target,label+': native historical-version link is missing');await ui.clickAndAwaitReload(opened.report,target);await inspect(opened.report,id,label+' historical '+known.get(id).key);
          ids[0]=id;
        }
        await retireReports(opened);
        const chart=await s.chart();await chart.reload({waitUntil:'domcontentloaded'});await waitForNavbars(chart,30000);
        const links=chart.locator('#leftNavBar a[onclick*="lab/CA/ALL/ViewLabDisplay"],#rightNavBar a[onclick*="lab/CA/ALL/ViewLabDisplay"],#leftNavBar a[href*="lab/CA/ALL/ViewLabDisplay"],#rightNavBar a[href*="lab/CA/ALL/ViewLabDisplay"]');
        // Each native list item has both a title and a date anchor. Count
        // source-bearing items, retaining the duplicate-item detection.
        const candidates=new Map();
        for(let index=0;index<await links.count();index++){
          const entry=links.nth(index),target=(await entry.getAttribute('onclick'))||(await entry.getAttribute('href'));
          const matches=[...(target||'').matchAll(/[?&]segmentID=([1-9]\d*)(?=&|['"]|$)/g)];
          h.assert(matches.length<=1,'Native chart link has ambiguous lab source parameters');
          const sourceId=matches[0]?.[1];
          if(!known.has(sourceId))continue;
          const item=await entry.evaluate(node=>Array.from(document.querySelectorAll('#leftNavBar li,#rightNavBar li')).indexOf(node.closest('li')));
          h.assert(item>=0,'Native lab link has no list-item identity');
          const previous=candidates.get(item);h.assert(!previous||previous.sourceId===sourceId,'One chart entry links to different clinical versions');
          if(!previous)candidates.set(item,{entry,sourceId});
        }
        h.assert(candidates.size===1,label+': patient chart has '+candidates.size+' current accession items');
        const report=await s.popup(chart,[...candidates.values()][0].entry,'ingested-current-chart-lab');
        const current=await report.locator('form[id^="acknowledgeForm_"]').evaluateAll(forms=>forms.map(form=>form.id.replace('acknowledgeForm_','')));
        h.assert(current.length===1&&known.has(current[0]),label+': chart rendered an unknown version');record(current[0]===expected,label+': chart current source is not the correction');
        await inspect(report,current[0],label+' chart current');await report.close();
        assertPatientStateUnchanged(clinicalBefore,readPatientState(sql,patient),label+' native lab reads');
        h.assert(JSON.stringify(labState(accession))===JSON.stringify(sourceBefore),label+': native report/chart reads changed source or review state');
        checkControl();h.assertStrictPage(s.recorder);console.log('  Observed '+label+': selected correction, every historical native source, chart current selection and independent control checked');
      }
      let corrected;
      for(const version of [VERSIONS.corrected,VERSIONS.older,VERSIONS.lateFinal]) {
        fs.writeFileSync(filePath,Buffer.from(versionMessage(accession,marker,version),'latin1'));
        h.assert(await uploadThroughPopup(s,filePath,fileName)==='Uploaded successfully',version.key+': changed message was not ingested');
        const id=capture(version);if(version===VERSIONS.corrected)corrected=id;
        h.assert(sourceRows().length===known.size,'Ingestion duplicated or dropped a clinical version');
        record(JSON.stringify(sql.rows(`SELECT * FROM providerLabRouting WHERE lab_type='HL7' AND lab_no=${original} ORDER BY id`))===JSON.stringify(originalReviewed),version.key+': original review state was altered');
        record(sql.value(`SELECT COUNT(*) FROM providerLabRouting WHERE lab_type='HL7' AND lab_no=${corrected} AND provider_no=${q(provider)} AND status='N'`)==='1',version.key+': new correction is not independently New');
        const active=sql.rows(`SELECT m.demographicNo,m.dataField,DATE_FORMAT(m.dateObserved,'%Y-%m-%d %H:%i:%s'),e.val FROM measurements m JOIN measurementsExt e ON e.measurement_id=m.id AND e.keyval='lab_no' WHERE e.val IN (${[...known.keys()].map(q).join(',')}) ORDER BY m.id`);
        record(JSON.stringify(active)===JSON.stringify([[patient,VERSIONS.corrected.value,VERSIONS.corrected.date,corrected]]),version.key+': active derived measurements do not retain only the corrected value/source');
        console.log('  Observed '+version.key+' source metadata: '+JSON.stringify({versions:sourceRows(),activeMeasurements:active}));
        await verifySurfaces(corrected,version.key+' ingestion');
        if(version===VERSIONS.corrected){
          // Existing pre-fix corrections have this persisted counter even though their
          // immutable C source is complete. Do not manufacture a version chain.
          sql.execute(`UPDATE hl7TextInfo SET final_result_count=0 WHERE lab_no=${corrected} AND accessionNum=${q(accession)} AND report_status='C' AND final_result_count=1`);
          h.assert(sql.value(`SELECT final_result_count FROM hl7TextInfo WHERE lab_no=${corrected}`)==='0','Owned legacy completeness fixture was not established');
          await verifySurfaces(corrected,'historical zero-count correction metadata');
          record(sql.value(`SELECT final_result_count FROM hl7TextInfo WHERE lab_no=${corrected}`)==='0','A read rewrote historical completeness metadata');
        }
      }
      h.assert(known.size===4,'Native version matrix omitted an original, correction, older delivery or same-time late final');
      await s.step('a missing historical source preserves selected-report and patient-list reads',async()=>{
        const before=labState(accession),clinical=readPatientState(sql,patient);
        const stored=sql.value(`SELECT message FROM hl7TextMessage WHERE lab_id=${original} AND type='CML'`);
        h.assert(stored,'Owned historical source is missing before the refusal probe');
        let cleared=false,probe,healthy;
        const macroRows=()=>sql.rows(`SELECT id,value IS NULL,IFNULL(HEX(value),'') FROM property WHERE provider_no=${q(provider)} AND name='labMacroJSON' ORDER BY id`);
        const macrosBefore=macroRows();h.assert(macrosBefore.length<=1,'Macro preferences are ambiguous before the owned probe');
        const macroValue=JSON.stringify([{name:marker+' read-only macro',acknowledge:{comment:'SYNTHETIC READ ONLY PROBE'},closeOnSuccess:false}]);
        const macroHex=Buffer.from(macroValue,'utf8').toString('hex').toUpperCase();
        let macrosClaimed=false;
        const restoreMacros=()=>{
          if(!macrosClaimed)return;
          const current=macroRows();
          if(JSON.stringify(current)===JSON.stringify(macrosBefore)){macrosClaimed=false;return;}
          h.assert(current.length===1&&current[0][2]===macroHex,'Owned macro preferences changed outside the read-only probe');
          const id=current[0][0];h.assert(/^[1-9]\d*$/.test(id),'Owned macro property has no valid identity');
          if(macrosBefore.length){
            h.assert(id===macrosBefore[0][0],'Macro property ownership changed');
            sql.execute(`UPDATE property SET value=${macrosBefore[0][1]==='1'?'NULL':`UNHEX(${q(macrosBefore[0][2])})`} WHERE id=${id}`);
          }else sql.execute(`DELETE FROM property WHERE id=${id} AND provider_no=${q(provider)} AND name='labMacroJSON' AND HEX(value)=${q(macroHex)}`);
          h.assert(JSON.stringify(macroRows())===JSON.stringify(macrosBefore),'Macro preferences were not restored exactly');
          macrosClaimed=false;
        };
        s.cleanup(restoreMacros);
        const restore=()=>{
          if(!cleared)return;
          const current=sql.value(`SELECT message FROM hl7TextMessage WHERE lab_id=${original} AND type='CML'`);
          h.assert(current==='','Historical source changed outside the owned missing-body probe');
          sql.execute(`UPDATE hl7TextMessage SET message=${q(stored)} WHERE lab_id=${original} AND type='CML' AND message=''`);
          h.assert(sql.value(`SELECT message FROM hl7TextMessage WHERE lab_id=${original}`)===stored,'Historical source restoration failed');
          cleared=false;
        };
        s.cleanup(restore);
        try{
          macrosClaimed=true;
          if(macrosBefore.length)sql.execute(`UPDATE property SET value=${q(macroValue)} WHERE id=${macrosBefore[0][0]} AND provider_no=${q(provider)} AND name='labMacroJSON'`);
          else sql.execute(`INSERT INTO property(provider_no,name,value) VALUES(${q(provider)},'labMacroJSON',${q(macroValue)})`);
          healthy=await inboxReport(corrected);
          h.assert(await healthy.inbox.locator(`#labdoc_HL7_${corrected} input[name="flaggedLabs"]`).isEnabled(),'Healthy correction is not selectable in the inbox');
          h.assert(await healthy.report.locator('.macro-dropdown').isVisible()&&await healthy.report.locator('.macro-dropdown a').filter({hasText:marker+' read-only macro'}).count()===1,'Configured macro menu precondition was not established');
          await retireReports(healthy);healthy=null;
          cleared=true;
          sql.execute(`UPDATE hl7TextMessage SET message='' WHERE lab_id=${original} AND type='CML' AND message=${q(stored)}`);
          h.assert(sql.value(`SELECT LENGTH(message) FROM hl7TextMessage WHERE lab_id=${original}`)==='0','Missing-body probe was not established');
          probe=await inboxReport(corrected);await inspect(probe.report,corrected,'Incomplete history');
          await probe.inbox.locator('input[name="checkA"]').first().check();
          for(const id of known.keys()){
            const inboxRow=probe.inbox.locator(`#labdoc_HL7_${id}`);
            h.assert(await inboxRow.locator('[data-carlos-cml-count-warning="unavailable"]').isVisible(),'Incomplete acknowledgement history has no inbox warning for source '+id);
            const selection=inboxRow.locator('input[name="flaggedLabs"]');
            h.assert(await selection.isDisabled()&&!await selection.isChecked(),'Incomplete history remains selectable through bulk Select All for source '+id);
          }
          const warning=probe.report.locator('[data-carlos-hl7-chain-warning="unavailable"]');
          h.assert(await warning.isVisible()&&(await warning.innerText()).length>60,'Incomplete history has no visible explanation');
          h.assert(await probe.report.locator('.macro-dropdown').count()===0,'Incomplete history still exposes configured macro anchors');
          h.assert(await probe.report.locator('input[onclick*="ackLab"],button[onclick*="ackLab"],input[onclick*="ForwardSelectedRows"],input[onclick*="unlinkDemographic"],a[href*="SearchPatient"],input[onclick*="SearchPatient"]').count()===0,'Incomplete history still offers a chain-dependent mutation');
          h.assert(await probe.report.locator('input[onclick*="printPDF"]:enabled').count()===0,'Unverified version headers are still printable');
          const chart=await s.chart();await chart.reload({waitUntil:'domcontentloaded'});await waitForNavbars(chart,30000);
          const hrefs=await chart.locator('#leftNavBar a,#rightNavBar a').evaluateAll(nodes=>nodes.map(node=>(node.getAttribute('onclick')||'')+' '+(node.getAttribute('href')||'')));
          const visible=new Set(hrefs.flatMap(text=>[...text.matchAll(/[?&]segmentID=([1-9]\d*)(?=&|['"]|$)/g)].map(match=>match[1])).filter(id=>known.has(id)));
          h.assert(visible.size===known.size,'Incomplete history lost a visible patient report');
          assertPatientStateUnchanged(clinical,readPatientState(sql,patient),'Incomplete history read');checkControl();
        }finally{
          try{restore();}finally{restoreMacros();}
          if(healthy)await retireReports(healthy);
          if(probe)await retireReports(probe);
        }
        h.assert(JSON.stringify(labState(accession))===JSON.stringify(before),'Missing-history read or restoration changed lab state');
      });
      const beforeReplay=labState(accession),clinicalBeforeReplay=readPatientState(sql,patient);
      fs.writeFileSync(filePath,Buffer.from(versionMessage(accession,marker,VERSIONS.corrected),'latin1'));
      h.assert(await uploadThroughPopup(s,filePath,fileName)==='Already uploaded','Repeated correction was not recognized byte-for-byte');
      h.assert(JSON.stringify(labState(accession))===JSON.stringify(beforeReplay),'Correction replay changed versions/routes/measurements');assertPatientStateUnchanged(clinicalBeforeReplay,readPatientState(sql,patient),'Correction replay');
      for(const [id,version] of known)h.assert(Buffer.from(sql.value(`SELECT message FROM hl7TextMessage WHERE lab_id=${id}`),'base64').toString('latin1')===version.raw,'A historical raw source changed');
      const derived=sql.rows(`SELECT m.id,m.demographicNo,e.val FROM measurements m JOIN measurementsExt e ON e.measurement_id=m.id WHERE e.keyval='lab_no' AND e.val IN (${[...known.keys()].map(q).join(',')})`);
      h.assert(derived.every(([,owner,sourceId])=>owner===patient&&known.has(sourceId)),'Derived measurements refer to the wrong patient/source');checkControl();
      h.assert(sql.value(`SELECT COUNT(*) FROM fileUploadCheck WHERE filename LIKE ${q('%'+fileName+'%')}`)==='4','Correction replay changed checksum identities');
      h.assert(fs.readdirSync(process.env.LAB_UPLOAD_DOCUMENT_STORE).filter(name=>name.startsWith('LabUpload.'+fileName+'.')).length===4,'Correction replay changed archived-upload count');
      console.log('  Observed exact correction replay: no new version, route, clinical or measurement effect; original raw history preserved');

    }});
    checkControl();
  }});
  h.assert(defects.length===0,'Lab version-ingestion failures: '+defects.join('; '));
}
if(require.main===module)runWorkflow('lab-version-ingestion',workflow,{preflightConfig:()=>{
  if(process.env.EXCLUSIVE!=='1'||process.env.CARLOS_DISPOSABLE_VM!=='true')throw new h.SkipCheck('Version-chain routing locks require EXCLUSIVE=1 and CARLOS_DISPOSABLE_VM=true');
  if(!process.env.LAB_UPLOAD_DOCUMENT_STORE)throw new h.SkipCheck('Version ingestion requires the actual archived-upload document store');
}});
module.exports={workflow,versionMessage,VERSIONS};
