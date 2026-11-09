/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const root=process.env.CARLOS_TEST_ROOT||'/root/carlos';
const h=require(root+'/scripts/lib/playwright-harness');
const {runWorkflow}=require(root+'/scripts/lib/workflow-session');
const {readPatientState,assertPatientStateUnchanged}=require(root+'/scripts/echart-two-patient-isolation-playwright-checks');
const q=h.sqlString;
async function open(s,group,forward) {
 const page=await s.context.newPage();
 await h.gotoApp(page,s.config.baseUrl,'/oscarReport/oscarMeasurements/SetupSelectCDMReport');
 await page.locator('select[name="value(CDMgroup)"]').selectOption(group);
 await Promise.all([page.waitForURL(/\/SelectCDMReport(?:$|[?#])/),page.locator(`input[type="button"][onclick*="set('${forward}')"]`).click()]);
 await h.assertNotErrorPage(page,'rendered CDM form');
 return page;
}
async function workflow(s) {
 const specs=[
  {expected:(t,n)=>`${t} probe -> (1.0/1.0)`,forward:'patientWhoMetGuideline',select:'guidelineCheckbox',endpoint:'InitializePatientsMetGuidelineCDMReport',fill:async(p,n)=>{
   await p.locator('input[name="guidelineB"]').fill(String(n-1));
   await p.locator('input[name="value(aboveBelow0)"][value=">"]').check();
  }},
  {expected:(t,n)=>`${t} probe -> From ${n-1} to ${n+1}: (1.0/1.0)`,forward:'patientInAbnormalRange',select:'abnormalCheckbox',endpoint:'InitializePatientsInAbnormalRangeCDMReport',fill:async(p,n)=>{
   await p.locator('input[name="lowerBound"]').fill(String(n-1));await p.locator('input[name="upperBound"]').fill(String(n+1));
  }},
  {expected:(t,n)=>`${t} probe -> 1.0/1.0 (100.0%)`,forward:'freqencyOfReleventTests',select:'frequencyCheckbox',endpoint:'InitializeFrequencyOfRelevantTestsCDMReport',fill:async p=>{
   await p.locator('input[name="exactly"]').fill('1');
   await p.locator('input[name="moreThan"]').fill('0');
   await p.locator('input[name="lessThan"]').fill('2');
  }}
 ];
 const types=['Z901','Z902'],groups=[s.marker+'-tab-A',s.marker+'-tab-B'],names=[s.marker+'-numeric-A',s.marker+'-numeric-B'];
 h.assert(s.sql.value(`SELECT COUNT(*) FROM measurementType WHERE type IN ('Z901','Z902')`)==='0','CDM type namespace already exists');
 h.assert(s.sql.value(`SELECT COUNT(*) FROM measurementGroup WHERE name IN (${groups.map(q).join(',')})`)==='0','CDM group namespace already exists');
 const validation=s.sql.value('SELECT id FROM validations WHERE isNumeric=1 AND minValue<=1 AND maxValue1>=20 ORDER BY id LIMIT 1');
 h.assert(/^\d+$/.test(validation),'Numeric validation is unavailable');
 s.cleanup(()=>{
  s.sql.execute(`DELETE FROM measurements WHERE demographicNo=${s.patient} AND type IN ('Z901','Z902');
    DELETE FROM measurementGroup WHERE name IN (${groups.map(q).join(',')});
    DELETE FROM measurementType WHERE (type='Z901' AND typeDisplayName=${q(names[0])}) OR (type='Z902' AND typeDisplayName=${q(names[1])})`);
  h.assert(s.sql.value(`SELECT COUNT(*) FROM measurementGroup WHERE name IN (${groups.map(q).join(',')})`)==='0','CDM group residue');
  h.assert(s.sql.value(`SELECT COUNT(*) FROM measurementType WHERE type IN ('Z901','Z902')`)==='0','CDM type residue');
 });
 for(let i=0;i<2;i++) s.sql.execute(`INSERT INTO measurementType(type,typeDisplayName,typeDescription,measuringInstruction,validation,createDate)
   VALUES(${q(types[i])},${q(names[i])},'Synthetic tab isolation','probe',${q(validation)},NOW());
   INSERT INTO measurementGroup(name,typeDisplayName) VALUES(${q(groups[i])},${q(names[i])});
   INSERT INTO measurements(type,demographicNo,providerNo,dataField,measuringInstruction,comments,dateObserved,dateEntered)
   VALUES(${q(types[i])},${s.patient},${q(s.provider)},${q(String((i+1)*10))},'probe','',CURDATE()-INTERVAL 1 DAY,NOW()-INTERVAL 1 DAY)`);
 await s.chart();
 const before=readPatientState(s.sql,s.patient),findings=[];
 for(const spec of specs) await s.step(spec.forward+' submits both independently rendered groups',async()=>{
  const a=await open(s,groups[0],spec.forward),b=await open(s,groups[1],spec.forward);
  try {
   for(const [page,type,value] of [[a,types[0],10],[b,types[1],20]]) {
    await page.locator(`input[name="${spec.select}"][value="0"]`).check();await spec.fill(page,value);
    await Promise.all([page.waitForURL(new RegExp('/'+spec.endpoint+'(?:$|[?#])')),page.locator('input[type="submit"][name="submitBtn"]').click()]);
    const text=(await page.locator('body').innerText()).replace(/\s+/g,' ');
    const other=types.find(t=>t!==type);
    if(!text.includes(spec.expected(type,value))||text.includes(other)){
     findings.push(spec.forward+': original '+type+' form did not retain its selected group');
     console.log('FINDING '+findings.at(-1));
    } else console.log('PASS '+spec.forward+': '+type+' remains isolated after the other tab rendered');
   }
  } finally {await a.close();await b.close();}
 });
 assertPatientStateUnchanged(before,readPatientState(s.sql,s.patient),'CDM two-tab report reads');
 h.assertStrictPage(s.recorder);
 h.assert(findings.length===0,findings.join('; '));
}
runWorkflow('measurement-cdm-tabs',workflow);
