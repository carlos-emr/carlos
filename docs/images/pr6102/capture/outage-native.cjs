/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const root=process.env.CARLOS_TEST_ROOT||'/root/carlos';
const {execFileSync}=require('node:child_process');
const h=require(root+'/scripts/lib/playwright-harness'),ui=require(root+'/scripts/lib/playwright-ui');
const {runWorkflow}=require(root+'/scripts/lib/workflow-session');
const {workflow:uploadFixture}=require(root+'/scripts/lab-upload-playwright-checks');
const {versionMessage,VERSIONS}=require(root+'/scripts/lab-version-ingestion-playwright-checks');
const {openInbox,openSidebar,setMode,search}=require(root+'/scripts/inbox-file-forward-playwright-checks');
const {readPatientState,assertPatientStateUnchanged}=require(root+'/scripts/echart-two-patient-isolation-playwright-checks');
const q=h.sqlString,service='carlos-emr-render-browser';
async function workflow(s){
 h.assert(process.env.EXCLUSIVE==='1'&&process.env.CARLOS_DISPOSABLE_VM==='true','Requires an exclusive disposable installed database');
 execFileSync('systemctl',['is-active','--quiet',service],{timeout:10000});await s.chart();
 const rendererEnv=require('node:fs').readFileSync('/etc/carlos-emr/renderer.env','utf8');
 const base=/^CARLOS_RENDER_URL_BASE=([a-f0-9]{32})$/m.exec(rendererEnv),port=/^CARLOS_RENDER_PORT=(\d{2,5})$/m.exec(rendererEnv);
 h.assert(base&&port,'Private renderer health configuration is unavailable');
 const healthUrl='http://127.0.0.1:'+port[1]+'/'+base[1]+'/status'; // Never log this capability URL.
 const findings=[],record=(okay,message)=>{if(!okay){findings.push(message);console.log('FINDING '+message);}};
 await uploadFixture(s,{initialContent:(acc,last)=>versionMessage(acc,last,VERSIONS.original),afterInitial:async source=>{
  const id=s.sql.value(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum=${q(source.accession)}`);h.assert(/^[1-9]\d*$/.test(id),'Missing owned source');
  const before=readPatientState(s.sql,s.patient),raw=s.sql.rows(`SELECT * FROM hl7TextMessage WHERE lab_id=${id}`);
  const inbox=await openInbox(s.schedule,s.context,s.recorder,'outage-inbox');await setMode(inbox,'list');await openSidebar(inbox);
  await inbox.locator('#statusAll').check();await inbox.locator('#specificPatients').check();await inbox.locator('#inputLastName').fill(s.marker);await search(inbox);
  const row=inbox.locator(`tr[data-lab-type="HL7"][data-segment-id="${id}"]`);
  const report=await ui.clickOpensPopup(inbox,row.locator('a[onclick*="reportWindow"]').first(),{context:s.context,recorder:s.recorder,label:'outage-source-report',timeout:30000});
  const value=await s.popup(report,report.getByRole('link',{name:'SYNTHETIC RESULT',exact:true}),'outage-value-graph');
  const graphResponse=r=>{const url=new URL(r.url());return r.request().method()==='GET'&&url.pathname.endsWith('/encounter/GraphMeasurements')&&url.searchParams.get('method')==='actualLab'&&url.searchParams.get('demographic_no')===String(s.patient);};
  const [healthy]=await Promise.all([value.waitForResponse(graphResponse),ui.clickAndAwaitReload(value,value.getByRole('button',{name:'Plot',exact:true}))]);
  h.assert(healthy.status()===200,'Healthy graph did not render');await value.waitForLoadState('networkidle');
  const plot=value.locator('img[src*="/encounter/GraphMeasurements"]');h.assert(await plot.evaluate(n=>n.complete&&n.naturalWidth===1000),'Healthy graph pixels are absent');
  const control=await s.context.newPage();h.wireStrictPage(control,'outage-healthy-control',s.recorder);await control.goto(value.url(),{waitUntil:'networkidle'});await control.close();
  let stopped=false,failed;
  try{
   stopped=true;execFileSync('systemctl',['stop',service],{timeout:30000});
   const inactive=require('node:child_process').spawnSync('systemctl',['is-active',service],{encoding:'utf8',timeout:10000});
   h.assert(!inactive.error&&inactive.status===3&&inactive.stdout.trim()==='inactive','Renderer service is not proven inactive');
   const badMark=s.recorder.badResponses.length,consoleMark=s.recorder.consoleIssues.length;
   [failed]=await Promise.all([value.waitForResponse(graphResponse),value.reload({waitUntil:'networkidle'})]);
   record(failed.status()===503,'Renderer outage must return HTTP 503');record(Number(failed.headers()['retry-after'])>0,'Renderer outage must include Retry-After');
   const warning=value.locator('#graphUnavailable');const visible=await warning.count()===1&&await warning.isVisible();
   record(visible,'Broken graph must have a visible recovery warning');
   if(visible){record((await warning.innerText()).trim().length>10,'Recovery warning is empty');record(await warning.getByRole('button',{name:'Retry',exact:true}).count()===1,'Recovery warning lacks Retry');}
   const failedView=await s.context.newPage();h.wireStrictPage(failedView,'outage-visible-failure',s.recorder);await failedView.goto(value.url(),{waitUntil:'networkidle'});await failedView.close();
   // Only the physically injected renderer outage's image responses are expected.
   const expected=s.recorder.badResponses.slice(badMark).filter(e=>e.status===failed.status()&&e.method==='GET'&&e.url===failed.url());
   h.assert(expected.length===2,'Expected exactly two controlled outage image requests');s.recorder.badResponses=s.recorder.badResponses.filter(e=>!expected.includes(e));
   const resources=s.recorder.consoleIssues.slice(consoleMark).filter(e=>e.location?.url===failed.url()&&new RegExp('^Failed to load resource:.*'+failed.status()).test(e.text));
   h.assert(resources.length<=2,'Unexpected duplicate outage resource errors');s.recorder.consoleIssues=s.recorder.consoleIssues.filter(e=>!resources.includes(e));
  }finally{if(stopped)execFileSync('systemctl',['start',service],{timeout:30000});}
  let ready=false;
  for(let attempt=0;attempt<100&&!ready;attempt++){
   try{const status=await fetch(healthUrl,{signal:AbortSignal.timeout(1000)});ready=status.ok&&(await status.json()).value?.ready===true;}catch{/* Retry only the bounded renderer-start health probe. */}
   if(!ready)await new Promise(resolve=>setTimeout(resolve,200));
  }
  h.assert(ready,'Renderer service did not become ready after restart');
  const retry=value.locator('#graphUnavailable').getByRole('button',{name:'Retry',exact:true});
  const [recovered]=await Promise.all([value.waitForResponse(graphResponse),await retry.count()===1?retry.click():value.reload({waitUntil:'networkidle'})]);
  h.assert(recovered.status()===200,'Graph did not recover after the service restarted');await value.waitForLoadState('networkidle');
  h.assert(await plot.evaluate(n=>n.complete&&n.naturalWidth===1000),'Recovered graph has no pixels');
  if(await value.locator('#graphUnavailable').count())h.assert(!await value.locator('#graphUnavailable').isVisible(),'Stale outage warning survives a successful retry');
  assertPatientStateUnchanged(before,readPatientState(s.sql,s.patient),'Outage/recovery reads');
  h.assert(JSON.stringify(s.sql.rows(`SELECT * FROM hl7TextMessage WHERE lab_id=${id}`))===JSON.stringify(raw),'Outage changed source bytes');
  console.log('EVIDENCE '+JSON.stringify({healthy:healthy.status(),outage:failed.status(),retryAfter:failed.headers()['retry-after'],recovered:recovered.status()}));
  for(const page of new Set([value,report,inbox]))if(page!==s.schedule&&!page.isClosed())await page.close();
 }});
 h.assertStrictPage(s.recorder);h.assert(findings.length===0,findings.join('; '));
}
runWorkflow('measurement-renderer-outage',workflow);
