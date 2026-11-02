#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
// #5177 / transferred #5282: an actual second INSERT failure is different from
// a committed transaction whose HTTP response never reaches the browser.
const fs=require('node:fs'),os=require('node:os'),path=require('node:path'),crypto=require('node:crypto');
const h=require('./lib/playwright-harness');
const {runWorkflow,expectValue}=require('./lib/workflow-session');
const {prepareRx,stageExisting}=require('./lib/rx-ui-workflow');
const {stageCustomDrugSettled}=require('./rx-stash-patient-isolation-playwright-checks');
const {failureMark,consumeExpectedFailure,waitUntil}=require('./lib/concurrency-support');
const {requiredRxServerLog,verifyActiveRxServerLog}=require('./lib/rx-server-log-prerequisite');
const q=h.sqlString,normalize=value=>value.replace(/\s+/g,' ').trim();
function triggerIntent(name,patient,oldIds,marker) {
  h.assert(/^pw_rx_late_[a-f0-9]{16}$/.test(name)&&/^[1-9]\d*$/.test(patient)&&oldIds.length>0&&oldIds.every(id=>/^[1-9]\d*$/.test(id))&&/^FAKE-PW[a-f0-9]{16}$/.test(marker),'Invalid owned Rx fault scope');
  const message='PW_RX_LATE_'+name.slice(-16);
  const body=`BEGIN
    IF NEW.demographic_no=${patient} AND LOCATE(${q(marker)},NEW.customName)=1
      AND EXISTS(SELECT 1 FROM drugs WHERE demographic_no=${patient} AND drugid NOT IN (${oldIds.join(',')})) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT=${q(message)};
    END IF;
  END`;
  return {message,body,sql:`DELIMITER //\nCREATE TRIGGER ${name} BEFORE INSERT ON drugs FOR EACH ROW\n${body}//\nDELIMITER ;`};
}
function consumeRxRefusal(s,mark,status) {
  consumeExpectedFailure(s.recorder,mark,{status,path:/\/rx\/WriteScript$/});
  // CSRFGuard wraps the native XHR, so Chromium may attribute the one expected
  // resource error to that wrapper. The exact owned response above is mandatory.
  const source=new URL(h.appUrl(s.config.baseUrl,'/csrfguard'));
  const matches=s.recorder.consoleIssues.slice(mark.console).filter(entry=>{
    if(entry.type!=='error'||entry.text!==`Failed to load resource: the server responded with a status of ${status} ()`)return false;
    try {const location=new URL(entry.location?.url);return location.origin===source.origin&&location.pathname===source.pathname;}catch{return false;}
  });
  h.assert(matches.length<=1,'Unexpected duplicate XHR wrapper errors');
  for(const entry of matches)s.recorder.consoleIssues.splice(s.recorder.consoleIssues.indexOf(entry),1);
}
function uncertainWarning(text,refused) {
  return typeof text==='string'&&!text.startsWith(refused)&&!/was NOT saved/i.test(text)&&/\b(review|check|uncertain|may)\b/i.test(text);
}
async function workflow(s) {
  const mode=process.env.RX_SAVE_FAILURE_MODE||'only-new',print=mode.startsWith('print-'),lost=mode.endsWith('-lost'),mixed=mode.endsWith('-mixed');
  h.assert(['only-new','print-new','only-mixed','print-mixed','only-lost','print-lost'].includes(mode),'Unknown Rx failure case');
  h.assert(process.env.CARLOS_DISPOSABLE_HOST==='true','Rx fault injection requires a disposable install');
  const reader=lost?null:requiredRxServerLog(),f=await prepareRx(s),{sql,patient,marker,provider}=s;
  const control=f.seed('unchanged-control'),source=mixed?f.seed('renewal-source'):null;
  const rx=await f.open();if(reader)await verifyActiveRxServerLog(reader,s,rx);
  const controls=()=>JSON.stringify(sql.rows(`SELECT * FROM drugs WHERE drugid=${control}`)),controlBefore=controls();
  const cards=[];
  if(source)cards.push({key:await stageExisting(rx,source),name:marker+' renewal-source'});
  for(let index=cards.length;index<2;index++)cards.push({key:await stageCustomDrugSettled(rx,marker+' retry-'+index),name:marker+' retry-'+index});
  for(const [index,card] of cards.entries()) {
    card.instructions=`${index+1} tab PO OD x ${index+10} days`;
    await rx.locator(`#instructions_${card.key}`).fill(card.instructions);
    const [parsed]=await Promise.all([rx.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/rx/UpdateScript')),rx.locator(`label[for="jsonDxSearch_${card.key}"]`).click()]);
    h.assert(parsed.status()===200,'The native direction parser refused the owned medication');await parsed.finished();
    card.quantity=await rx.locator(`#quantity_${card.key}`).inputValue();
    card.repeats=await rx.locator(`#repeats_${card.key}`).inputValue();
    card.expectedSpecial=`${card.name}\n${card.instructions}\nQty:${card.quantity||'0'} Repeats:${card.repeats||'0'}`;
  }
  const queries=[`SELECT * FROM prescription WHERE demographic_no=${patient} ORDER BY script_no`,
    `SELECT * FROM drugs WHERE demographic_no=${patient} ORDER BY drugid`,
    `SELECT * FROM drugReason WHERE demographicNo=${patient} ORDER BY id`,
    `SELECT * FROM partial_date WHERE table_name=2 AND table_id IN (SELECT drugid FROM drugs WHERE demographic_no=${patient}) ORDER BY id`,
    `SELECT * FROM DigitalSignature WHERE demographicId=${patient} AND ModuleType='PRESCRIPTION' ORDER BY id`,
    `SELECT * FROM casemgmt_note WHERE demographic_no=${patient} ORDER BY note_id`,
    `SELECT * FROM casemgmt_note_link WHERE note_id IN (SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}) ORDER BY id`,
    `SELECT * FROM casemgmt_note_ext WHERE note_id IN (SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}) ORDER BY id`];
  const snapshot=()=>queries.map(query=>JSON.stringify(sql.rows(query))),before=snapshot();
  const saveMatch=r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/rx/WriteScript')&&new URL(r.url()).searchParams.get('parameterValue')==='updateSaveAllDrugs';
  const button=rx.locator(print?'#saveButton':'#saveOnlyButton');
  let lostMessages,refusedMessage;
  async function saveExpected(status,error) {
    const mark=failureMark(s.recorder);let response;
    const expected=await rx.evaluate(()=>({incomplete:jsMsg.saveIncomplete,stale:jsMsg.staleDraft}));
    const dialogs=await h.withExpectedDialogs(rx,async()=>{
      const alert=rx.waitForEvent('dialog',{predicate:dialog=>dialog.type()==='alert',timeout:30000});alert.catch(()=>{});
      [response]=await Promise.all([rx.waitForResponse(saveMatch),button.click()]);await response.finished();await alert;
    });
    h.assert(response.status()===status&&(await response.json()).error===error,'Save did not report its exact failure contract');
    h.assert(dialogs.length===1&&dialogs[0].type==='alert'&&dialogs[0].text===(status===500?expected.incomplete:expected.stale),'Save did not show its accurate failure message');
    consumeRxRefusal(s,mark,status);
    h.assert(await rx.locator('#carlosModal:visible').count()===0,'A failed save opened a success preview');
    for(const card of cards)h.assert(await rx.locator(`#instructions_${card.key}`).inputValue()===card.instructions,'Failure lost an entered medication/direction');
    return response;
  }
  if(!lost) {
    const name='pw_rx_late_'+crypto.randomBytes(8).toString('hex'),oldIds=sql.rows(`SELECT drugid FROM drugs WHERE demographic_no=${patient}`).map(([id])=>id),intent=triggerIntent(name,patient,oldIds,marker);
    const directory=fs.mkdtempSync(path.join(os.tmpdir(),'carlos-rx-fault-')),receipt=path.join(directory,'receipt.json');let created=false;
    const metadata=()=>sql.rows(`SELECT EVENT_OBJECT_TABLE,EVENT_MANIPULATION,ACTION_TIMING,HEX(ACTION_STATEMENT) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE() AND TRIGGER_NAME=${q(name)}`);
    function observe() {const rows=metadata();h.assert(rows.length===1&&rows[0][0]==='drugs'&&rows[0][1]==='INSERT'&&rows[0][2]==='BEFORE'&&normalize(Buffer.from(rows[0][3],'hex').toString())===normalize(intent.body),'Owned Rx trigger identity changed; retain it for inspection');}
    function drop(){if(!created)return;if(metadata().length===0){created=false;return;}observe();sql.execute(`DROP TRIGGER ${name}`);h.assert(metadata().length===0,'Owned Rx fault trigger remains');created=false;}
    s.cleanup(()=>{drop();fs.rmSync(directory,{recursive:true,force:true});});
    await s.step('the owned second medication INSERT fault is reached and retains the accurate error and draft',async()=>{
      h.assert(metadata().length===0,'Rx fault trigger already exists');
      fs.writeFileSync(receipt,JSON.stringify({name,patient,mode,message:intent.message,scopeSha256:crypto.createHash('sha256').update(normalize(intent.body)).digest('hex')}),{flag:'wx',mode:0o600});
      // Record ownership before DDL so cleanup can recover a lost CREATE reply.
      created=true;
      try {
        sql.execute(intent.sql);observe();const mark=reader.mark();await saveExpected(500,'INCOMPLETE_RX_SAVE');
        await waitUntil(()=>reader.since(mark).includes(intent.message),'the owned late-INSERT failure in the configured server log');
        h.assert(controls()===controlBefore,'Failed batch changed its unrelated persisted control');
      } finally {drop();}
    });
    await s.step('a real second medication INSERT fails and the complete clinical batch rolls back with its draft retained',async()=>{
        const after=snapshot(),tables=['prescription','drugs','drugReason','partial_date','DigitalSignature','casemgmt_note','casemgmt_note_link','casemgmt_note_ext'];
        const changed=tables.filter((_,index)=>before[index]!==after[index]);
        h.assert(changed.length===0,'A mid-save failure left partial clinical state in: '+changed.join(', '));
    });
    await s.step('retry through the same Save control writes the exact medications once',async()=>{
      const [response]=await Promise.all([rx.waitForResponse(saveMatch),button.click()]);h.assert(response.status()===200,'The retained draft could not be retried');await response.finished();
    });
  } else {
    await s.step('a committed save with a lost response stays distinct from server rollback',async()=>{
      let intercepted=false;const mark=failureMark(s.recorder);let committed;
      const pattern='**/rx/WriteScript?**';
      const handler=async route=>{
        const request=route.request();if(request.method()!=='POST'||new URL(request.url()).searchParams.get('parameterValue')!=='updateSaveAllDrugs'||intercepted){await route.continue();return;}
        intercepted=true;const response=await route.fetch();h.assert(response.status()===200,'Lost-response control did not commit successfully');committed=await response.json();await response.dispose();await route.abort('failed');
      };
      await s.context.route(pattern,handler);
      let dialogs;refusedMessage=await rx.evaluate(()=>jsMsg.saveRefused);
      try {dialogs=await h.withExpectedDialogs(rx,async()=>{
        const alert=rx.waitForEvent('dialog',{predicate:dialog=>dialog.type()==='alert',timeout:30000});alert.catch(()=>{});
        await button.click();await alert;
      });}finally{await s.context.unroute(pattern,handler);}
      h.assert(intercepted&&/^[1-9]\d*$/.test(String(committed?.scriptId)),'Lost-response control never observed a successful server result');
      h.assert(dialogs.length===1&&dialogs[0].type==='alert','Lost response was reported as success');lostMessages=[dialogs[0].text];
      const failures=s.recorder.requestFailures.slice(mark.failures);
      h.assert(failures.length===1&&new URL(failures[0].url).pathname.endsWith('/rx/WriteScript')&&/ERR_FAILED/.test(failures[0].errorText),'Lost response caused an unrelated transport failure');
      const urls=new Set(failures.map(row=>row.url));urls.add(h.appUrl(s.config.baseUrl,'/csrfguard'));s.recorder.requestFailures.splice(mark.failures,1);
      for(let index=s.recorder.consoleIssues.length-1;index>=mark.console;index--){const item=s.recorder.consoleIssues[index];if(urls.has(item.location?.url)&&/^Failed to load resource:.*ERR_FAILED/.test(item.text))s.recorder.consoleIssues.splice(index,1);}
      h.assert(await rx.locator('#carlosModal:visible').count()===0,'Lost response opened a false success preview');
      const persisted=snapshot(),retryMark=failureMark(s.recorder);let retry;
      const retryDialogs=await h.withExpectedDialogs(rx,async()=>{
        const alert=rx.waitForEvent('dialog',{predicate:dialog=>dialog.type()==='alert',timeout:30000});alert.catch(()=>{});
        [retry]=await Promise.all([rx.waitForResponse(saveMatch),button.click()]);await retry.finished();await alert;
      });
      // An acknowledged Print keeps cards with advanced revisions (409); Save
      // Only clears them and intentionally sends the application's HTML 400.
      const retryStatus=print?409:400,body=await retry.text();
      h.assert(retry.status()===retryStatus,'Consumed-card retry returned the wrong source contract');
      if(print)h.assert((await retry.json()).error==='STALE_RX_STASH','Print retry was not a revision conflict');
      else h.assert(/text\/html/.test(retry.headers()['content-type']||'')&&h.isApplicationErrorPage(body)&&!h.isWafPage(retry.status(),body),
        'Consumed-card retry did not reach the source-qualified application 400');
      h.assert(retryDialogs.length===1&&retryDialogs[0].type==='alert','Consumed-card retry did not report its refusal');
      lostMessages.push(retryDialogs[0].text);consumeRxRefusal(s,retryMark,retryStatus);
      h.assert(JSON.stringify(snapshot())===JSON.stringify(persisted),'Retry duplicated or changed a committed prescription');
    });
  }
  await s.step('reopened medication profile and prescription agree with exact storage and leave the control unchanged',async()=>{
    h.assert(controls()===controlBefore,'Saving changed its unrelated control');
    await expectValue(sql,`SELECT COUNT(*) FROM prescription WHERE demographic_no=${patient}`,'1','Save wrote other than one prescription');
    for(const card of cards)h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient} AND provider_no=${q(provider)} AND customName=${q(card.name)} AND special=${q(card.expectedSpecial)} AND archived=0`)==='1','Final medication clinical values differ or are duplicated');
    if(source)h.assert(sql.value(`SELECT archived FROM drugs WHERE drugid=${source}`)==='1','Successful renewal did not archive its source');
    await rx.reload({waitUntil:'networkidle'});
    for(const card of cards)await rx.locator('#drugProfile').getByText(card.name,{exact:false}).first().waitFor();
    const script=sql.value(`SELECT script_no FROM prescription WHERE demographic_no=${patient}`);
    await rx.getByRole('link',{name:'Reprint',exact:true}).first().click();
    await rx.locator(`#reprint a[onclick*="reprint2('${script}')"]`).first().click();
    const {viewScriptFrame}=require('./rx-drug-name-quotes-playwright-checks');
    const view=await viewScriptFrame(rx);h.assert(view,'Reprint did not open its native prescription view');
    const preview=view.frameLocator('#preview');
    for(const card of cards)await preview.locator('body').filter({hasText:card.name}).waitFor({state:'visible',timeout:30000});
  });
  if(lost)await s.step('an uncertain committed save asks the clinician to check rather than claiming nothing was saved',async()=>{
    h.assert(uncertainWarning(lostMessages[0],refusedMessage),'A confirmed committed save was reported as NOT saved after a lost response');
  });
}
if(require.main===module)runWorkflow('rx-save-failure-retry',workflow,{openPatient:true});
module.exports={workflow,triggerIntent,consumeRxRefusal,uncertainWarning};
