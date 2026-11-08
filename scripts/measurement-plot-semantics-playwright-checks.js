#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// #5716: actual native PNG geometry, independently calibrated printed axes.
const fs=require('node:fs');
const strict=require('node:assert/strict');
const h=require('./lib/playwright-harness');
const ui=require('./lib/playwright-ui');
const {runWorkflow,expectValue}=require('./lib/workflow-session');
const {nativeGraphResponse}=require('./lib/native-graph-response');
const graph=require('./lib/measurement-plot');
const {readPatientState,assertPatientStateUnchanged}=require('./echart-two-patient-isolation-playwright-checks');
const q=h.sqlString;
// Deliberately neither chronological nor uniformly spaced; no three vertices
// are collinear, making a missing, extra or swapped observation distinguishable.
const OBSERVATIONS=[{date:'2023-12-29',value:'99'},{date:'2021-01-01',value:'55'},
  {date:'2025-10-02',value:'71'},{date:'2022-05-07',value:'63'},{date:'2021-09-19',value:'82'}];

async function workflow(s) {
  const {sql,patient,marker,provider}=s;
  const controlName=marker+'-Control';
  let control;
  s.cleanup(()=>{
    sql.execute(`DELETE FROM measurements WHERE demographicNo=${patient} AND comments=${q(marker)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient}`)==='0','Unexpected owned-patient measurement retained');
  });
  // Register durable ownership before INSERT, including a lost-ID response.
  s.cleanup(()=>{
    const ids=sql.rows(`SELECT demographic_no FROM demographic WHERE last_name=${q(controlName)}`).flat();
    h.assert(ids.length<=1&&(!control||ids.length===0||ids[0]===control),'Control-patient ownership changed');
    for(const id of ids) {
      h.assert(/^[1-9]\d*$/.test(id),'Invalid control patient key');
      const state=readPatientState(sql,id);
      h.assert(Object.entries(state).every(([name,rows])=>['demographic','measurements'].includes(name)||rows.length===0),'Unexpected control clinical rows; retain fixture');
      h.assert(sql.value(`SELECT COUNT(*) FROM measurements WHERE demographicNo=${id} AND (comments IS NULL OR comments<>${q(marker)})`)==='0','Control measurements gained a foreign row');
      sql.execute(`DELETE FROM measurements WHERE demographicNo=${id} AND comments=${q(marker)};
        DELETE FROM demographic WHERE demographic_no=${id} AND last_name=${q(controlName)}`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM demographic WHERE demographic_no=${id})+(SELECT COUNT(*) FROM measurements WHERE demographicNo=${id})`)==='0','Control fixture cleanup failed');
    }
  });
  control=sql.value(`INSERT INTO demographic(last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES(${q(controlName)},'Control','1980','01','02','F','AC',${q(provider)},'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(control),'Control patient was not created');
  const insert=(owner,row)=>sql.value(`INSERT INTO measurements(type,demographicNo,providerNo,dataField,measuringInstruction,comments,dateObserved,dateEntered)
    VALUES('WT',${owner},${q(provider)},${q(row.value)},'in kg',${q(marker)},${q(row.date)},NOW()); SELECT LAST_INSERT_ID()`);
  insert(control,{date:'2022-08-09',value:'175'});
  const controlBefore=readPatientState(sql,control);
  const ids=[insert(patient,OBSERVATIONS[0])];
  const chart=await s.chart();
  const index=await s.popup(chart,chart.getByRole('link',{name:'Measurements',exact:true}).first(),'plot-semantics-index');
  const openHistory=()=>s.popup(index,index.locator('a[onclick*="SetupDisplayHistory?type=WT"]').first(),'plot-semantics-history');
  let history=await openHistory();
  const plot=()=>history.locator('input[onclick*="GraphMeasurements"]');
  async function capture(stage) {
    const before=readPatientState(sql,patient);
    const response=await nativeGraphResponse(s,()=>plot().click(),{type:'WT'});
    h.assert(response.status===200&&(response.headers['content-type']||'').startsWith('image/png'),'Native graph status or PNG content type is wrong');
    graph.decode(response.body);
    await response.page.waitForLoadState('load');
    const image=response.page.locator('img');
    await image.waitFor({state:'visible'});
    h.assert(await image.evaluate(node=>node.complete&&node.naturalWidth===1000&&node.naturalHeight===600),'Browser could not render native PNG');
    if(process.env.MEASUREMENT_PLOT_ARTIFACT_DIR)fs.writeFileSync(h.buildArtifactPath(process.env.MEASUREMENT_PLOT_ARTIFACT_DIR,marker+'-'+stage,'.png'),response.body,{flag:'wx',mode:0o600});
    assertPatientStateUnchanged(before,readPatientState(sql,patient),'Plot read');
    assertPatientStateUnchanged(controlBefore,readPatientState(sql,control),'Plot control');
    // popupPage reuses its named window, including the History popup itself.
    // Close this consumed image and reopen History through the original UI link.
    await response.page.close();
    if(history.isClosed())history=await openHistory();
    return response.body;
  }
  await s.step('a single kg observation renders; deleting it restores empty history without a Plot control',async()=>{
    const single=await capture('single');
    // JFreeChart's native renderer draws lines without point shapes: one sample
    // has axes/legend but no segment. The populated geometry is checked below.
    h.assert(single.length>1000,'Single-observation chart is unexpectedly empty');
    const row=history.locator('tr.data').filter({has:history.locator(`input[name="deleteCheckbox"][value="${ids[0]}"]`)});
    h.assert((await row.innerText()).includes('in kg')&&(await row.locator('td[title="data"]').innerText()).trim()==='99','Single observation lost type, unit or value');
    await history.locator(`input[name="deleteCheckbox"][value="${ids[0]}"]`).check();
    await ui.clickAndAwaitReload(history,history.locator('input[onclick="submit();"]'));
    h.assert(await history.locator('input[name="deleteCheckbox"]').count()===0&&await plot().count()===0,'Empty history offers observations or a plot');
    ids.length=0;
  });
  let output;
  await s.step('five out-of-order observations plot at the independently calibrated chronological kg coordinates',async()=>{
    for(const row of OBSERVATIONS)ids.push(insert(patient,row));
    await history.reload({waitUntil:'load'});
    h.assert(await history.locator('input[name="deleteCheckbox"]').count()===5,'History has missing or foreign rows');
    for(let i=0;i<ids.length;i++) {
      const row=history.locator('tr.data').filter({has:history.locator(`input[name="deleteCheckbox"][value="${ids[i]}"]`)});
      h.assert((await row.locator('td[title="data"]').innerText()).trim()===OBSERVATIONS[i].value
        &&(await row.locator('td[title="observed date"]').innerText()).trim()===OBSERVATIONS[i].date
        &&(await row.innerText()).includes('in kg'),'History date, value or kg instruction differs from the fixture');
    }
    h.assert(await history.locator('input[type="date"],input[type="range"],select').count()===0,'Native range controls changed; extend coverage before accepting');
    for(const label of ['Print','Close','Delete','Plot'])h.assert(await history.getByRole('button',{name:label,exact:true}).count()===1,'Native history control is missing: '+label);
    output=graph.inspect(await capture('five'));
    console.log('  '+JSON.stringify(graph.verifySeries(output,OBSERVATIONS)));
  });
  await s.step('geometry controls reject omitted, swapped, shifted and foreign observations',async()=>{
    strict.throws(()=>graph.verifySeries(output,OBSERVATIONS.slice(1)),/vertex count differs/);
    const swapped=OBSERVATIONS.map(row=>({...row}));[swapped[0].value,swapped[1].value]=[swapped[1].value,swapped[0].value];
    strict.throws(()=>graph.verifySeries(output,swapped),/wrong date\/value geometry/);
    const shifted=OBSERVATIONS.map(row=>({...row}));shifted[3].date='2022-09-07';
    strict.throws(()=>graph.verifySeries(output,shifted),/wrong date\/value geometry/);
    strict.throws(()=>graph.verifySeries(output,[...OBSERVATIONS,{date:'2022-08-09',value:'175'}]),/outside printed axes/);
  });
  await s.step('native deletion removes exactly its vertex; remaining coordinates and all unrelated clinical rows survive',async()=>{
    const before=readPatientState(sql,patient);
    await history.locator(`input[name="deleteCheckbox"][value="${ids[3]}"]`).check();
    await ui.clickAndAwaitReload(history,history.locator('input[onclick="submit();"]'));
    await expectValue(sql,`SELECT COUNT(*) FROM measurements WHERE id=${ids[3]}`,'0','Selected observation survived native deletion');
    h.assert(sql.value(`SELECT COUNT(*) FROM measurementsDeleted WHERE originalId=${ids[3]} AND demographicNo=${patient} AND dataField='63' AND DATE(dateObserved)='2022-05-07'`)==='1','Selected observation archive is missing or incorrect');
    const after=readPatientState(sql,patient);
    const unchanged=state=>Object.fromEntries(Object.entries(state).filter(([name])=>!['measurements','deletedMeasurements'].includes(name)));
    assertPatientStateUnchanged(unchanged(before),unchanged(after),'Native selected deletion');
    strict.deepEqual(after.measurements,before.measurements.filter(row=>row[0]!==ids[3]),'Native deletion changed another measurement');
    h.assert(after.deletedMeasurements.length===before.deletedMeasurements.length+1,'Native deletion added unexpected archive rows');
    graph.verifySeries(graph.inspect(await capture('after-delete')),OBSERVATIONS.filter((_,i)=>i!==3));
    h.assert(await history.locator('input[name="deleteCheckbox"]').count()===4,'History did not refresh after selected deletion');
    // Return to empty using the real multi-delete, rather than a SQL reset.
    for(const checkbox of await history.locator('input[name="deleteCheckbox"]').all())await checkbox.check();
    await ui.clickAndAwaitReload(history,history.locator('input[onclick="submit();"]'));
    h.assert(await plot().count()===0&&await history.locator('input[name="deleteCheckbox"]').count()===0,'Deleting all observations did not restore empty native history');
    h.assert(sql.value(`SELECT COUNT(*) FROM measurementsDeleted WHERE demographicNo=${patient}`)==='6','Multi-delete lost or duplicated archives');
    assertPatientStateUnchanged(controlBefore,readPatientState(sql,control),'Final control');
  });
}
if(require.main===module)runWorkflow('measurement-plot-semantics',workflow,{preflight:()=>{
  require('node:child_process').execFileSync('tesseract',['--version'],{timeout:10000,stdio:'pipe'});
}});
module.exports={workflow,OBSERVATIONS};
