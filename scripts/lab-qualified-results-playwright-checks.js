#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const h=require('./lib/playwright-harness'),ui=require('./lib/playwright-ui');
const {runWorkflow}=require('./lib/workflow-session');
const {submitCreateLab}=require('./lab-manual-entry-cumulative-playwright-checks');
const {removeOwnedHl7Labs}=require('./lab-forwarding-rules-playwright-checks');
const {heldClinicalRequests}=require('./lib/held-clinical-requests');
const {retainFixtures}=require('./lib/uncertain-server-writes');
const {readPatientState}=require('./echart-two-patient-isolation-playwright-checks');
const {newOwnedMarker,createOwnedPatient,removeOwnedPatient}=require('./lib/owned-patient');
const {requirePoppler}=require('./lib/stored-pdf-documents');
const {assertMessage,inspectPlot,verifyGeometry,assertPdf,assertNoNumericPoints}=require('./lib/qualified-lab-oracle');
const {requireEnglishOcr}=require('./lib/ocr-preflight');
const graph=require('./lib/pdf-graph'),q=h.sqlString;
// #6122 policy: a final-status word result the shipped filter does not match (Pending) is filed whole as a
// text measurement. Its report/PDF preserve it; the grid, value history and Row Display show it labelled as text.
const isTextLiteral=test=>test.code==='3007'&&test.value==='Pending';
// Each page callback below reads a cell's text without its text label, and whether it has one, since the
// label is in the page's language. The helper is written inline: a page's script policy may refuse eval.
const HGB={code:'2010',name:'HEMOGLOBIN',unit:'g/L',low:'120',high:'160',flag:'N'};
const HCT={code:'2013',name:'HEMATOCRIT',unit:'L/L',low:'0.36',high:'0.46',flag:'N'};
const CHOL={code:'3120',name:'CHOLESTEROL',unit:'mmol/L',low:'3',high:'7',flag:'N'};
// Explicit fixture projections retain the PR's OSCAR 19 plotting contract pending #6118/#6120.
// Raw source, history and PDF assertions continue to require the qualified literals.
const PANEL=[{...HGB,value:'<0.1',plotValue:'0.1',flag:'A'},
  {...HCT,value:'>100',plotValue:'100',flag:'A'},
  {code:'3210',name:'SODIUM',value:'Negative',unit:'qual-Na',low:'0',high:'1',flag:'N'},
  {code:'3007',name:'GLUCOSE FASTING-SER',gridName:'GLUCOSE-FASTING',value:'Pending',unit:'qual-glucose',low:'2',high:'4',flag:'N'},
  {...CHOL,value:'5.7'}];
function ledger(){const failures=[];let verdict;return {async check(label,body){try{await body();console.log('  PASS laboratory assertion: '+label);}catch(error){failures.push(new Error(label+': '+error.message,{cause:error}));console.error('  FINDING laboratory assertion: '+label+': '+error.message);}},finish(){if(failures.length){verdict||=Object.assign(new AggregateError(failures,'Qualified laboratory acceptance: '+failures.map(error=>error.message).join('; ')),{labAcceptance:true});throw verdict;}}};}
function preflightConfig(){if(process.env.CARLOS_DISPOSABLE_HOST!=='true'||process.env.EXCLUSIVE!=='1')throw new h.SkipCheck('Qualified labs require CARLOS_DISPOSABLE_HOST=true and EXCLUSIVE=1');h.assert(process.env.LAB_UPLOAD_DOCUMENT_STORE&&fs.statSync(process.env.LAB_UPLOAD_DOCUMENT_STORE).isDirectory(),'Actual lab archive store is required');requirePoppler('pdfinfo','pdftotext');requireEnglishOcr();}
function assertNativeIdentityFloor(sql){
  const checksumNext=Number(sql.value("SELECT AUTO_INCREMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='fileUploadCheck'"));
  const checksumReference=Number(sql.value('SELECT COALESCE(MAX(fileUploadCheck_id),0) FROM hl7TextMessage'));
  h.assert(Number.isSafeInteger(checksumNext)&&checksumNext>0&&checksumNext<2147483640&&Number.isSafeInteger(checksumReference)&&checksumReference>=0&&checksumNext>checksumReference,'Native checksum allocator could inherit a dangling lab reference');
  const next=Number(sql.value("SELECT AUTO_INCREMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='hl7TextMessage'"));
  h.assert(Number.isSafeInteger(next)&&next>0&&next<2147483640,'No safe native HL7 identity range');
  for(const [table,column,where] of [['hl7TextInfo','lab_no','1'],['providerLabRouting','lab_no',"lab_type='HL7'"],['patientLabRouting','lab_no',"lab_type='HL7'"],['providerLabRoutingLock','lab_no','1'],['measurementsExt','val',"keyval='lab_no'"]]){
    const maximum=Number(sql.value(`SELECT COALESCE(MAX(CAST(\`${column}\` AS UNSIGNED)),0) FROM \`${table}\` WHERE (${where}) AND CAST(\`${column}\` AS CHAR) REGEXP '^[0-9]+$'`));
    h.assert(Number.isSafeInteger(maximum)&&maximum>=0&&next>maximum,'Native HL7 allocator could inherit a dangling reference; qualify its fixture range before running');
  }
}
async function workflow(s){
  const {sql,patient,provider,marker,context}=s,checks=ledger();let guard,primary;
  assertNativeIdentityFloor(sql);
  const controlMarker=newOwnedMarker();let control;
  s.cleanup(()=>{control||=sql.value('SELECT demographic_no FROM demographic WHERE last_name=?',[controlMarker]);if(control)removeOwnedPatient(sql,control,controlMarker);});
  control=createOwnedPatient(sql,{marker:controlMarker,provider});
  const stamp=crypto.randomBytes(6).toString('hex').toUpperCase(),labs=[
    {date:'2021-01-01',tests:PANEL},
    {date:'2022-05-07',tests:[{...HGB,value:'Negative'}]},
    {date:'2023-12-29',tests:[{...HGB,value:'137'}]},
    {date:'2025-10-02',tests:[{...HGB,value:'>100',plotValue:'100',flag:'A'}]},
    {date:'2026-11-01',tests:[{...HGB,value:'145'},{...CHOL,value:'6.2'},{...HCT,value:'0.42'}]},
  ].map((lab,index)=>({...lab,accession:'QL'+stamp+index,owner:patient,marker}));
  const controlLab={date:'2024-08-09',tests:[{...HGB,value:'999.37'}],accession:'QL'+stamp+'C',owner:control,marker:controlMarker},all=[...labs,controlLab];
  for(const lab of all)h.assert(sql.value('SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=?',[lab.accession])==='0','Qualified lab accession namespace already exists');
  s.cleanup(()=>{
    const ids=all.flatMap(lab=>sql.rows('SELECT lab_no FROM hl7TextInfo WHERE accessionNum=?',[lab.accession]).flat());
    for(const id of ids){const lab=all.find(lab=>sql.value('SELECT accessionNum FROM hl7TextInfo WHERE lab_no=?',[id])===lab.accession);h.assert(lab&&sql.value("SELECT COUNT(*) FROM patientLabRouting WHERE lab_type='HL7' AND lab_no=? AND demographic_no<>?",[id,lab.owner])==='0','Owned lab acquired another patient; retain it');h.assert(sql.value('SELECT COUNT(*) FROM hl7TextMessage m JOIN hl7TextMessage other ON other.fileUploadCheck_id=m.fileUploadCheck_id WHERE m.lab_id=? AND other.lab_id<>m.lab_id',[id])==='0','Owned lab checksum is shared; retain it');}
    removeOwnedHl7Labs(sql,ids);
    h.assert(all.every(lab=>sql.value('SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=?',[lab.accession])==='0'),'Qualified lab metadata remains');
  });
  const mainBase=()=>{const value=readPatientState(sql,patient);delete value.echart;return value;};
  const state=owner=>{const ids=all.filter(lab=>lab.owner===owner).map(lab=>lab.id).filter(Boolean).join(',')||'0';return {clinical:owner===patient?mainBase():readPatientState(sql,owner),messages:sql.rows('SELECT * FROM hl7TextMessage WHERE lab_id IN('+ids+') ORDER BY lab_id'),metadata:sql.rows('SELECT * FROM hl7TextInfo WHERE lab_no IN('+ids+') ORDER BY lab_no'),routing:sql.rows("SELECT * FROM patientLabRouting WHERE lab_type='HL7' AND lab_no IN("+ids+") ORDER BY id"),providers:sql.rows("SELECT * FROM providerLabRouting WHERE lab_type='HL7' AND lab_no IN("+ids+") ORDER BY id")};};
  try{
    guard=heldClinicalRequests(s,{identity:marker,matches:url=>url.origin===new URL(s.config.baseUrl).origin&&['/oscarMDS/SubmitLab','/lab/CA/ALL/PrintPDF','/encounter/GraphMeasurements'].some(route=>url.pathname.endsWith(route)),select:request=>{
      const url=new URL(request.url()),body=new URLSearchParams(request.postData()||'');
      if(url.pathname.endsWith('/oscarMDS/SubmitLab')){const lab=all.find(lab=>lab.accession===body.get('accession'));h.assert(request.method()==='POST'&&lab&&body.get('lastname')===lab.marker,'Native lab submission escaped its owned accession/patient');return {kind:'entry',write:true,accession:lab.accession};}
      if(url.pathname.endsWith('/lab/CA/ALL/PrintPDF')){h.assert(request.method()==='POST'&&labs.some(lab=>lab.id===body.get('segmentID')),'Native Print selected another lab');return {kind:'print',write:false,labId:body.get('segmentID')};}
      h.assert(request.method()==='GET'&&url.searchParams.get('method')==='actualLab'&&url.searchParams.get('demographic_no')===patient&&PANEL.some(test=>test.code===url.searchParams.get('identifier')),'Native Plot escaped its patient/test namespace');return {kind:'plot',write:false,code:url.searchParams.get('identifier')};
    }});s.cleanup(()=>guard.cleanup());await guard.install();
    await s.step('native Create Lab stores literal qualified/qualitative/numeric observations and exact patient/test/time identities',async()=>{
      for(const lab of all){const start=guard.records.length;try{
        const {form,inbox}=await submitCreateLab({...s,marker:lab.marker,patient:lab.owner},{accession:lab.accession,tests:lab.tests,date:lab.date,label:'qualified-lab-entry'});
        h.assert(sql.value('SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=?',[lab.accession])==='1','Native lab entry did not store exactly one message');lab.id=sql.value('SELECT lab_no FROM hl7TextInfo WHERE accessionNum=?',[lab.accession]);
        h.assert(sql.value("SELECT COUNT(*) FROM patientLabRouting WHERE lab_type='HL7' AND lab_no=? AND demographic_no=?",[lab.id,lab.owner])==='1','Native entry matched the wrong patient');
        assertMessage(sql.value('SELECT message FROM hl7TextMessage WHERE lab_id=?',[lab.id]),lab);
        await form.close();if(inbox!==s.schedule)await inbox.close();
      }catch(error){if(guard.records.slice(start).some(record=>record.kind==='entry'&&['dispatching','acknowledged','unknown-write'].includes(record.state)))retainFixtures(context);throw error;}}
    });
    const before=state(patient),controlBefore=state(control),unchanged=()=>{assert.deepEqual(state(patient),before,'Lab reads/print/plot changed owned clinical/message state');assert.deepEqual(state(control),controlBefore,'Lab reads/print/plot changed another patient');};
    const chart=await s.chart();
    const index=await s.popup(chart,chart.locator('a[onclick*="/lab/ViewDemographicLab?"]').first(),'qualified-native-lab-list');
    const reportLinks=index.locator('#labResultsTbl tbody a[href*="ViewLabDisplay?"]');await index.locator('#labResultsTbl').waitFor();const ids=await reportLinks.evaluateAll(links=>links.map(link=>new URL(link.href).searchParams.get('segmentID')));assert.deepEqual(ids.slice().sort(),labs.map(lab=>lab.id).sort(),'Patient lab list has missing, duplicate or foreign reports');
    async function openReport(lab){const position=ids.indexOf(lab.id);h.assert(position>=0,'Native patient lab list omitted its source report');const report=await s.popup(index,reportLinks.nth(position),'qualified-native-report');h.assert(new URL(report.url()).searchParams.get('segmentID')===lab.id,'Native report selected another lab');return report;}

    async function values(report,test){const link=report.locator('a[href*="CA/ON/ViewLabValues"]').filter({hasText:test.name}).first();const value=await s.popup(report,link,'qualified-native-values');const url=new URL(value.url());h.assert(url.searchParams.get('demo')===patient&&url.searchParams.get('identifier')===test.code,'Native value history selected another patient/test');return value;}
    await checks.check('unmatched history keeps its message and malformed identities fail closed',async()=>{
      for(const demo of [null,'','0','null','undefined','bad','-1','2147483648']){
        const params=new URLSearchParams({labType:'HL7',testName:HGB.name,identifier:HGB.code});
        if(demo!==null)params.set('demo',demo);
        const response=await s.context.request.get(h.appUrl(s.config.baseUrl,'/lab/CA/ON/ViewLabValues')+'?'+params);
        try{
          const unmatched=demo===null||['','0','null','undefined'].includes(demo);
          // The packaged WAF can refuse an overflowing identity before the action's 400 gate.
          h.assert(unmatched?response.status()===200:[400,403].includes(response.status()),
            'History returned HTTP '+response.status()+' for synthetic demo '+JSON.stringify(demo));
          const body=await response.text();
          if(unmatched)h.assert(body.includes('The demographic number is not valid'),'Unmatched history lost its established recovery message');
          h.assert(!body.includes('id="tblDiscs"'),'An absent/invalid patient returned clinical history');
        }finally{await response.dispose();}
      }
    });
    for(const lab of labs){const report=await openReport(lab);
      await checks.check(lab.date+' native report literal associations',async()=>{for(const test of lab.tests){const link=report.locator('a[href*="CA/ON/ViewLabValues"]').filter({hasText:test.name}).first();h.assert(await link.count()===1,'Report omitted the native test/history link');const row=link.locator('xpath=ancestor::tr[1]'),cells=(await row.locator('td').allInnerTexts()).map(text=>text.replace(/\s+/g,' ').trim());h.assert(cells.includes(test.value)&&cells.includes(test.unit)&&cells.includes(test.low+' - '+test.high),'Report changed a literal qualifier/value/unit/range association: '+test.name);}const text=await report.locator('body').innerText();h.assert(text.includes(marker)&&!text.includes(controlMarker)&&!text.includes('999.37'),'Report leaked another patient/result');});
      if(lab===labs[0])for(const test of PANEL){const value=await values(report,test);const history=labs.flatMap(lab=>lab.tests.filter(row=>row.code===test.code).map(row=>({...row,date:lab.date})));
        await checks.check(test.name+(isTextLiteral(test)?' text result value history':' native value history'),async()=>{const rows=await value.locator('#tblDiscs tbody tr').evaluateAll(rows=>{const labelled=node=>{const copy=node.cloneNode(true);const label=copy.querySelector('[data-carlos-lab-text-result="true"]');if(label)label.remove();return {text:(copy.innerText??copy.textContent).replace(/\s+/g,' ').trim(),textLabel:!!label};};return rows.map(row=>[...row.querySelectorAll('td')].map(labelled)).filter(cells=>cells.length===6);});h.assert(rows.length===history.length,'Value history lost or duplicated qualified/qualitative observations');for(const expected of history)h.assert(rows.some(row=>row[0].text===test.name&&row[1].text===expected.value&&row[1].textLabel===isTextLiteral(test)&&row[2].text===expected.flag&&row[3].text===expected.low+' - '+expected.high&&row[4].text===expected.unit&&row[5].text.startsWith(expected.date)),'Value history changed a literal/date/unit/range/flag association or its text label');});
        const offered=await value.getByRole('button',{name:'Plot',exact:true}).count();
        if(!offered)console.log('  EVIDENCE native Plot is not offered for '+test.name+'; its literal-history assertion remains independently required');
        else await checks.check(test.name+' native Plot retains the declared legacy numeric projections',async()=>{const start=guard.records.length;await ui.clickAndAwaitReload(value,value.getByRole('button',{name:'Plot',exact:true}),{timeout:30000,label:'native lab Plot'});const receipt=await guard.wait(record=>guard.records.indexOf(record)>=start&&record.kind==='plot'&&record.code===test.code&&record.state==='acknowledged');h.assert(receipt.status===200&&(receipt.headers['content-type']||'').startsWith('image/png'),'Native lab graph did not return PNG');if(process.env.LAB_QUALIFIED_ARTIFACT_DIR)fs.writeFileSync(h.buildArtifactPath(process.env.LAB_QUALIFIED_ARTIFACT_DIR,marker+'-'+test.code,'.png'),receipt.body,{flag:'wx',mode:0o600});const numeric=history.map(row=>({...row,value:row.plotValue??row.value})).filter(row=>/^-?\d+(?:\.\d+)?$/.test(row.value));if(numeric.length)verifyGeometry(inspectPlot(receipt.body),numeric);else assertNoNumericPoints(receipt.body);});
        await value.waitForLoadState('networkidle');await value.close();
      }
      await checks.check(lab.date+' actual native report PDF preserves literals',async()=>{const start=guard.records.length;await report.locator('input[onclick^="printPDF("]').first().click();const receipt=await guard.wait(record=>guard.records.indexOf(record)>=start&&record.kind==='print'&&record.labId===lab.id&&record.state==='acknowledged');h.assert(receipt.status===200&&(receipt.headers['content-type']||'').startsWith('application/pdf'),'Native Print did not serve a PDF');const text=Array.from({length:graph.pageCount(receipt.body)},(_,i)=>graph.pageText(receipt.body,i+1)).join('\n');if(process.env.LAB_QUALIFIED_ARTIFACT_DIR)fs.writeFileSync(h.buildArtifactPath(process.env.LAB_QUALIFIED_ARTIFACT_DIR,marker+'-'+lab.id,'.pdf'),receipt.body,{flag:'wx',mode:0o600});assertPdf(receipt.body,{...lab,marker,controlMarker});});
      await report.waitForLoadState('networkidle');await report.close();
    }
    async function menu(name){const pattern=name==='Grid Display'?/^\s*Grid Display\s*$/:/^\s*Row Display\s*$/;const link=chart.locator('#menu2 a').filter({hasText:pattern}).first();if(!await link.isVisible())await chart.locator('#menuTitle2 a').first().hover();return s.popup(chart,link,'qualified-'+name);}
    const grid=await menu('Grid Display');
    const headers=await grid.locator('#cumulativeLabTable thead a[data-seg-id]').evaluateAll(links=>links.map(link=>link.dataset.segId));
    await checks.check('cumulative grid includes every source lab',()=>assert.deepEqual(headers.slice().sort(),labs.map(lab=>lab.id).sort(),'Grid omitted a nonnumeric lab or included a foreign one'));
    await checks.check('cumulative grid states its measurement scope',async()=>{const notice=grid.locator('[data-carlos-lab-grid-scope="measurements"]');h.assert(await notice.isVisible()&&(await notice.innerText()).length>60,'Cumulative grid lost its scope notice');});
    for(const test of PANEL)await checks.check(test.name+(isTextLiteral(test)?' cumulative grid shows the text result labelled':' cumulative grid retains current and historical literals'),async()=>{
      const row=grid.locator('#cumulativeLabTable tbody tr').filter({has:grid.locator('td:first-child',{hasText:test.gridName||test.name})});const rowCount=await row.count();h.assert(rowCount===1,'Grid omitted the qualitative/qualified test: '+test.name);const cells=await row.locator('td').evaluateAll(cells=>cells.map(node=>{const copy=node.cloneNode(true);const label=copy.querySelector('[data-carlos-lab-text-result="true"]');if(label)label.remove();return {text:(copy.innerText??copy.textContent).replace(/\s+/g,' ').trim(),textLabel:!!label};}));const history=labs.flatMap(lab=>lab.tests.filter(row=>row.code===test.code).map(row=>({...row,date:lab.date})));const latest=history.at(-1);h.assert(cells[1].text===latest.value&&cells[2].text===latest.date,'Grid latest result/date is stale or coerced: expected '+latest.value+' actual '+cells[1].text);h.assert(cells[1].textLabel===isTextLiteral(test),'Grid latest result has the wrong text label: '+test.name);
      for(const expected of history){const column=headers.indexOf(labs.find(lab=>lab.date===expected.date).id);h.assert(column>=0&&cells[column+3].text===expected.value&&cells[column+3].textLabel===isTextLiteral(test),'Grid changed a historical literal or its text label: '+test.name+' '+expected.date+' expected '+JSON.stringify(expected.value)+' actual '+JSON.stringify(cells[column+3]));}
    });await grid.close();
    const rows=await menu('Row Display');for(const test of PANEL)await checks.check(test.name+(isTextLiteral(test)?' cumulative row history shows the text result labelled':' cumulative row literal history'),async()=>{const button=rows.getByRole('button',{name:test.name,exact:true});h.assert(await button.count()===1,'Row Display omitted a qualitative test');await Promise.all([rows.waitForResponse(response=>new URL(response.url()).pathname.endsWith('/lab/ViewDisplayLabValue')),button.click()]);const section=rows.locator('#cumulativeLab .preventionSection').filter({hasText:test.name.slice(0,8)}).last();await section.waitFor();const actual=await section.locator('.preventionProcedure').evaluateAll(nodes=>nodes.map(node=>{const copy=node.cloneNode(true);const label=copy.querySelector('[data-carlos-lab-text-result="true"]');if(label)label.remove();return {text:(copy.innerText??copy.textContent).replace(/\s+/g,' ').trim(),textLabel:!!label};}));const history=labs.flatMap(lab=>lab.tests.filter(row=>row.code===test.code).map(row=>({...row,date:lab.date})));h.assert(actual.length===history.length,'Row history lost or duplicated observations');for(const expected of history){h.assert(actual.some(row=>row.text.startsWith(expected.value+' '+expected.date)&&row.textLabel===isTextLiteral(test)),'Row history lost literal/time association or its text label: '+test.name+' '+expected.date);const procedure=section.locator('.preventionProcedure').filter({hasText:expected.value}).filter({hasText:expected.date});h.assert(await procedure.count()===1,'Native row history identity is ambiguous');await procedure.hover();await rows.waitForFunction(({value,units,range})=>typeof oDv!=='undefined'&&getComputedStyle(oDv).visibility==='visible'&&dvHdr.textContent.trim()===value&&dvBdy.textContent.trim()===units+' '+range,{value:expected.value,units:expected.unit,range:expected.low+' - '+expected.high},{timeout:10000});}});await rows.close();
    await checks.check('read/print/plot preserve exact owned and unrelated clinical state',unchanged);await checks.check('strict browser diagnostics',()=>h.assertStrictPage(s.recorder));checks.finish();
  }catch(error){try{if(!error.labAcceptance)checks.finish();}catch(findings){if(findings!==error)primary=new AggregateError([error,findings],error.message+'; '+findings.message);}primary||=error;}
  try{if(guard)await guard.shutdown();}catch(error){primary=primary?new AggregateError([primary,error],primary.message+'; native request drain: '+error.message):error;}
  if(primary)throw primary;
}
if(require.main===module)runWorkflow('lab-qualified-results',workflow,{preflightConfig});
module.exports={workflow,preflightConfig,assertNativeIdentityFloor,PANEL,HGB,CHOL,ledger};
