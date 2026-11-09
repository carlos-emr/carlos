/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const root=process.env.CARLOS_TEST_ROOT||'/root/carlos';
const fs=require('node:fs'),path=require('node:path'),{createRequire}=require('node:module');
const {PNG}=createRequire(path.join(root,'package.json'))('pngjs');
const h=require(root+'/scripts/lib/playwright-harness');
const {runWorkflow}=require(root+'/scripts/lib/workflow-session');
const {nativeGraphResponse}=require(root+'/scripts/lib/native-graph-response');
const {readPatientState,assertPatientStateUnchanged}=require(root+'/scripts/echart-two-patient-isolation-playwright-checks');
const q=h.sqlString;
async function workflow(s){
 h.assert(process.env.EXCLUSIVE==='1'&&process.env.CARLOS_DISPOSABLE_VM==='true','Requires an exclusive disposable installed database');
 const output=process.env.MEASUREMENT_PLOT_ARTIFACT_DIR;
 h.assert(output&&path.isAbsolute(output),'MEASUREMENT_PLOT_ARTIFACT_DIR must be absolute');fs.mkdirSync(output,{recursive:true});
 s.cleanup(()=>s.sql.execute(`DELETE FROM measurements WHERE demographicNo=${s.patient} AND comments=${q(s.marker)}`));
 const chart=await s.chart(),index=await s.popup(chart,chart.getByRole('link',{name:'Measurements',exact:true}).first(),'boundary-index');
 const findings=[],records=[];
 let failure;
 try {
 for(const value of ['99','0','0.05','-0.05','5000000','12500000','45000000','-6000000']){
  let history,response;
  const file='constant-'+value.replace('-','n').replace('.','_')+'.png';
  try{
   s.sql.execute(`DELETE FROM measurements WHERE demographicNo=${s.patient} AND comments=${q(s.marker)};
    INSERT INTO measurements(type,demographicNo,providerNo,dataField,measuringInstruction,comments,dateObserved,dateEntered)
    VALUES('WT',${s.patient},${q(s.provider)},${q(value)},'in kg',${q(s.marker)},'2024-01-15',NOW())`);
   await index.reload({waitUntil:'load'});
   h.assert(await index.locator('a[onclick*="SetupDisplayHistory?type=WT"]').count()>0,'Native measurement index lacks the populated WT history');
   const before=readPatientState(s.sql,s.patient);
   history=await s.popup(index,index.locator('a[onclick*="SetupDisplayHistory?type=WT"]').first(),'boundary-history-'+value);
   h.assert((await history.locator('td[title="data"]').innerText()).trim()===value,'Native history changed the source literal');
   response=await nativeGraphResponse(s,()=>history.locator('input[onclick*="GraphMeasurements"]').click(),{type:'WT'});
   await response.page.waitForLoadState('load');
   const receipt={value,status:response.status};records.push(receipt);
   assertPatientStateUnchanged(before,readPatientState(s.sql,s.patient),'Boundary graph read');
   if(response.status!==200)throw new Error('Native graph answered HTTP '+response.status);
   h.assert((response.headers['content-type']||'').startsWith('image/png'),'Expected native PNG');
   fs.writeFileSync(path.join(output,file),response.body,{flag:'wx'});
   const png=PNG.sync.read(response.body,{checkCRC:true});h.assert(png.width===1000&&png.height===600,'Incorrect native chart dimensions');
   const image=response.page.locator('img');await image.waitFor({state:'visible'});
   h.assert(await image.evaluate(node=>node.complete&&node.naturalWidth===1000&&node.naturalHeight===600),'Browser cannot display graph');
   // A constant one-point series must have a visible red marker inside the central
   // plot. The legend sits below this region; axes/grid/text cannot satisfy it.
   let pixels=0;for(let y=120;y<450;y++)for(let x=250;x<750;x++){
    const i=(y*png.width+x)*4,r=png.data[i],g=png.data[i+1],b=png.data[i+2];
    if(r>=180&&r>g+45&&r>b+45)pixels++;
   }
   receipt.centralMarkerPixels=pixels;h.assert(pixels>=9,'Single-point series has no visible central marker');
   console.log('PASS native constant '+value+': '+pixels+' visible marker pixels');
  }catch(error){findings.push(value+': '+error.message);console.log('FINDING native constant '+value+': '+error.message);}
  finally{
   const cleanup = await Promise.allSettled([...new Set([response?.page,history])]
     .filter(page=>page&&!page.isClosed()&&page!==index).map(page=>page.close()));
   for(const result of cleanup)if(result.status==='rejected')findings.push('Cleanup: '+result.reason.message);
  }
 }
 h.assertStrictPage(s.recorder);h.assert(findings.length===0,findings.join('; '));
 } catch(error) { failure=error; }
 try {
  const temporary=path.join(output,'verdicts.json.tmp');
  fs.writeFileSync(temporary,JSON.stringify({records,findings},null,2)+'\n');
  fs.renameSync(temporary,path.join(output,'verdicts.json'));
 } catch(error) { failure=failure?new AggregateError([failure,error],'Boundary workflow and verdict persistence failed'):error; }
 if(failure)throw failure;
}
runWorkflow('measurement-native-boundaries',workflow);
