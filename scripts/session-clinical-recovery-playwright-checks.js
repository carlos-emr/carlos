#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const assert=require('node:assert/strict');
const h=require('./lib/playwright-harness');
const ui=require('./lib/playwright-ui');
const {runWorkflow,expectValue}=require('./lib/workflow-session');
const {throwawayLoginFixture}=require('./lib/throwaway-login-fixture');
const {saveOwnedProviderProfile}=require('./lib/native-provider-fixture');
const {restartAndWait}=require('./lib/property-profiles');
const {heldClinicalRequests}=require('./lib/held-clinical-requests');
const {openSecondSession,failureMark,waitUntil}=require('./lib/concurrency-support');
const {openMasterRecord}=require('./master-record-tabs-playwright-checks');
const {openChart,waitForNavbars}=require('./echart-navbar-modules-playwright-checks');
const {releaseChartLocks}=require('./lib/chart-lock-cleanup');
const {consumeUnloadBeacons}=require('./echart-note-draft-recovery-playwright-checks');
const {newOwnedMarker,createOwnedPatient,removeOwnedPatient,eformRows}=require('./lib/owned-patient');
const {reserveInstanceIds}=require('./eform-deployed-page-exclusion-playwright-checks');
const {multipartFields}=require('./lib/get-reject-probe');
const {readPatientState}=require('./echart-two-patient-isolation-playwright-checks');
const NOTE='#caseManagementEntryForm textarea[name="caseNote_note"]',STATUS='[id="autosaveTime"][data-autosave-state]',q=h.sqlString;
function assertAuthenticationRefusal(record,baseUrl,privateTexts){
  h.assert(record.state==='acknowledged','Clinical save has no actual upstream acknowledgement');
  const body=record.body.toString('utf8');
  h.assert(privateTexts.every(text=>!body.includes(text)),'Authentication refusal reflected pending clinical content');
  h.assert(!h.isWafPage(record.status,body),'A WAF block cannot qualify as an application authentication refusal');
  if(record.status===401||record.status===403){
    h.assert(Object.hasOwn(record.headers,h.APPLICATION_HEADER),'Authentication refusal has no independent application-origin receipt');return;
  }
  if([302,303].includes(record.status)){
    const target=new URL(record.headers.location,record.request.url()),base=new URL(baseUrl);
    h.assert(target.origin===base.origin&&[base.pathname.replace(/\/$/,'')+'/index',base.pathname.replace(/\/$/,'')+'/'].includes(target.pathname),
      'Unauthenticated clinical save redirected outside the local login entry');return;
  }
  h.assert(record.status===200&&/\bid=["']username["']/.test(body)&&/\bid=["']password["']/.test(body),
    'Clinical save returned neither a recognized authentication refusal nor the actual login form');
}
function consumeRefusalDiagnostics(recorder,mark,records){
  const refused=records.filter(record=>record.state==='acknowledged'&&record.status>=400);
  const bad=recorder.badResponses.slice(mark.responses);
  h.assert(bad.length===refused.length&&bad.every(entry=>refused.some(record=>record.request.url()===entry.url
    &&record.request.method()===entry.method&&record.status===entry.status)),'Unexpected response accompanied session-expiry refusals');
  recorder.badResponses.splice(mark.responses,bad.length);
  for(let i=recorder.consoleIssues.length-1;i>=mark.console;i--){
    const entry=recorder.consoleIssues[i];
    const resourceFailure=/^Failed to load resource:.*status of (\d{3}) \(/.exec(entry.text);
    if(resourceFailure&&refused.some(record=>entry.location?.url===record.request.url()
      &&Number(resourceFailure[1])===record.status))recorder.consoleIssues.splice(i,1);
    else if(records.some(record=>record.kind==='note'&&record.state==='acknowledged')
      &&/^CaseManagement autoSave failed (?:401|403|200) (?:Unauthorized|Forbidden|Unexpected autosave response)$/.test(entry.text))recorder.consoleIssues.splice(i,1);
  }
}
function assertVisibleRefusal({status,loginCount,feedback}){
  h.assert(!/save successful|saved successfully/i.test(feedback),'Refused eForm save falsely reported success');
  const error=/CARLOS Error:\s*(\d+)\b/.exec(feedback);
  const canonicalError=error&&Number(error[1])===status&&/^CARLOS has encountered an unexpected error/.test(feedback);
  h.assert(loginCount===1||[401,403].includes(status)&&(canonicalError
    ||/\bforbidden\b|access denied|authentication|session.*expired|csrf|security exception|insufficient privileges/i.test(feedback)),
  'Refused form save lacks visible login/authentication-refusal feedback: '+feedback.slice(0,160));
}
function consumeAcknowledgedAborts(recorder,mark,records){
  for(let index=recorder.requestFailures.length-1;index>=mark.failures;index--){
    const entry=recorder.requestFailures[index];
    if(!/ERR_ABORTED/.test(entry.errorText||'')||!entry.navigatedAway?.())continue;
    const record=records.find(record=>record.request===entry.requestRef?.deref()&&record.state==='acknowledged');
    if(!record)continue;
    const refused=['note','form'].includes(record.kind)&&[401,403].includes(record.status);
    const expired=record.kind==='heartbeat'&&record.status===200&&JSON.parse(record.body.toString()).valid===false;
    if(refused||expired)recorder.requestFailures.splice(index,1);
  }
}
function preflightConfig(){if(process.env.CARLOS_DISPOSABLE_HOST!=='true'||process.env.EXCLUSIVE!=='1')
  throw new h.SkipCheck('Session clinical recovery requires CARLOS_DISPOSABLE_HOST=true and EXCLUSIVE=1');}
async function workflow(s){
  const {sql,marker,patient,provider,config}=s;
  const baseline=marker+' saved clinical baseline',draft=marker+' durable private draft',pending=marker+' pending private note',formSaved=marker+' saved form',formPending=marker+' pending private form';
  const unauthenticatedSecrets=[marker,baseline,draft,pending,formSaved,formPending];
  let cacheDirty=false,fid,held,original;
  const actors=[];
  s.cleanup(()=>{if(cacheDirty)restartAndWait();});
  for(const suffix of ['writer','other']){
    const actor={marker:newOwnedMarker(),label:suffix};actor.fixture=throwawayLoginFixture({sql,marker:actor.marker,provider,testUser:config.testUser});actors.push(actor);
    s.cleanup(()=>actor.fixture.cleanup());actor.fixture.create();
    s.cleanup(()=>{
      const no=q(actor.fixture.providerNo);
      h.assert(sql.value(`SELECT COUNT(*) FROM providerArchive WHERE provider_no=${no} AND (last_name<>${q(actor.marker)} OR last_name IS NULL)`)==='0','Foreign provider archive; retain fixtures');
      sql.execute(`DELETE FROM providerArchive WHERE provider_no=${no} AND last_name=${q(actor.marker)};DELETE FROM provider_default_program WHERE provider_no=${no}`);
    });
    sql.execute(`INSERT INTO program_provider(program_id,provider_no,role_id,team_id) SELECT program_id,${q(actor.fixture.providerNo)},role_id,team_id FROM program_provider WHERE provider_no=${q(provider)};
      INSERT INTO provider_default_program(provider_no,program_id,signnote) SELECT ${q(actor.fixture.providerNo)},program_id,signnote FROM provider_default_program WHERE provider_no=${q(provider)}`);
    h.assert(Number(sql.value(`SELECT COUNT(*) FROM program_provider WHERE provider_no=${q(actor.fixture.providerNo)}`))>0,'Owned account has no chart program');
    cacheDirty=true;await saveOwnedProviderProfile(s,actor);
  }
  const writer=actors[0].fixture,other=actors[1].fixture;
  const controlMarker=newOwnedMarker();let control;
  s.cleanup(()=>{
    control||=sql.value(`SELECT demographic_no FROM demographic WHERE last_name=${q(controlMarker)}`);
    if(control)removeOwnedPatient(sql,control,controlMarker);
  });
  control=createOwnedPatient(sql,{marker:controlMarker,provider});
  const controlState=()=>({clinical:readPatientState(sql,control),forms:sql.rows(`SELECT * FROM eform_data WHERE demographic_no=${control} ORDER BY fdid`),values:sql.rows(`SELECT * FROM eform_values WHERE demographic_no=${control} ORDER BY fdid,var_name`)});
  const controlBefore=controlState();
  const attachments=eformRows(sql),name=marker+' session recovery',field='recoveryValue';
  // Claim the template namespace before setup, including an INSERT with a lost acknowledgement.
  h.assert(sql.value(`SELECT COUNT(*) FROM eform WHERE form_name=${q(name)}`)==='0','Owned eForm namespace already exists');
  s.cleanup(()=>{
    const ids=sql.rows(`SELECT fid FROM eform WHERE form_name=${q(name)}`).flat();
    for(const id of ids){h.assert(sql.value(`SELECT COUNT(*) FROM eform_data WHERE fid=${id} AND demographic_no<>${patient}`)==='0','Owned template acquired foreign patient instances');}
    for(const link of attachments.via)sql.execute(`DELETE FROM ${link.table} WHERE ${link.column} IN (SELECT fdid FROM eform_data WHERE demographic_no=${patient})${link.above?' AND '+link.above.column+'>'+link.above.value:''}`);
    sql.execute(`DELETE FROM eform_data WHERE demographic_no=${patient};DELETE FROM eform WHERE form_name=${q(name)} AND form_creator=${q(provider)}`);
    const notes=`SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
    sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes});DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes});DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient};DELETE FROM casemgmt_tmpsave WHERE demographic_no=${patient};DELETE FROM casemgmt_note_lock WHERE demographic_no=${patient};DELETE FROM eChart WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM eform WHERE form_name=${q(name)})+(SELECT COUNT(*) FROM eform_data WHERE demographic_no=${patient})+(SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})+(SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no=${patient})`)==='0','Owned clinical recovery rows remain');
  });
  const html=`<!doctype html><html><head><title>${name}</title></head><body><form name="FormName" id="FormName"><label>Clinical recovery value <input type="text" name="${field}" id="${field}"></label></form></body></html>`;
  fid=sql.value(`INSERT INTO eform(form_name,file_name,subject,form_date,form_time,form_creator,status,form_html,showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable)
    VALUES(${q(name)},${q(marker+'.html')},${q(name)},CURDATE(),CURTIME(),${q(provider)},1,${q(html)},0,0,'',0,1);SELECT LAST_INSERT_ID()`);
  reserveInstanceIds(s,{id:fid,name});
  const snapshots=()=>({notes:sql.rows(`SELECT * FROM casemgmt_note WHERE demographic_no=${patient} ORDER BY note_id`),
    drafts:sql.rows(`SELECT * FROM casemgmt_tmpsave WHERE demographic_no=${patient} ORDER BY id`),
    forms:sql.rows(`SELECT * FROM eform_data WHERE demographic_no=${patient} ORDER BY fdid`),
    values:sql.rows(`SELECT * FROM eform_values WHERE demographic_no=${patient} ORDER BY fdid,var_name`)});
  const assertDurableDraft=()=>{
    const rows=sql.rows(`SELECT note FROM casemgmt_tmpsave WHERE demographic_no=${patient} AND provider_no=${q(writer.providerNo)}`);
    h.assert(rows.length===1&&rows[0][0].replace(/\r\n/g,'\n').trim()===durableText.replace(/\r\n/g,'\n').trim(),
      'The acknowledged private draft was lost, changed or duplicated during recovery/account switching');
  };
  const noPending=()=>{
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_note WHERE LOCATE(${q(pending)},note)>0)
      +(SELECT COUNT(*) FROM casemgmt_tmpsave WHERE LOCATE(${q(pending)},note)>0)
      +(SELECT COUNT(*) FROM eform_values WHERE LOCATE(${q(formPending)},var_value)>0)`)==='0','Pending content reached clinical storage, including another patient/account');
    assert.deepEqual(controlState(),controlBefore,'Session-loss operations changed unrelated control clinical history');
  };
  const writerConfig={...config,testUser:writer.username};
  let chart,form,savedFdid,savedState,durableText,pendingText,pendingFormSourceUrl,expiredMark,enderContext,enderPage;
  const consumedRefusals=new Set();
  const note=page=>page.locator(NOTE).filter({visible:true}).first();
  const popup=(parent,locator,label)=>ui.clickOpensPopup(parent,locator,{context:parent.context(),recorder:s.recorder,label,timeout:20000});
  async function openSavedForm(parent){
    const list=await popup(parent,parent.locator('a[onclick*="/eform/efmpatientformlist?"]').first(),'recovery-saved-eforms');
    const row=list.locator('#efmTable tbody tr').filter({hasText:name});h.assert(await row.count()===1,'Saved form list has missing or duplicate owned instance');
    return popup(list,row.locator('a').filter({hasText:name}).first(),'recovery-saved-instance');
  }
  let primary;
  try{
    await s.step('native saves establish one clinical note, one eForm and a separately acknowledged private draft',async()=>{
      original=await openSecondSession(s,{label:'clinical-recovery-writer',config:writerConfig});
      original.context.on('request',request=>{
        const url=new URL(request.url());if(request.method()!=='POST'||!url.pathname.includes('/eform/'))return;
        const type=request.headers()['content-type']||'',data=/multipart\/form-data/i.test(type)?new URLSearchParams(multipartFields(request.postDataBuffer(),type)):new URLSearchParams(request.postData()||'');
        console.log('  EVIDENCE native eForm request '+JSON.stringify({path:url.pathname,contentType:type.split(';')[0],hasRecoveryField:data.has(field),pendingLiteral:data.get(field)===formPending,patientMatches:data.get('demographicNo')===patient,templateMatches:data.get('fid')===fid,instanceMatches:data.get('fdid')===savedFdid}));
      });
      original.context.on('response',response=>{
        const request=response.request(),url=new URL(request.url());
        if(request.method()==='POST'&&url.pathname.includes('CaseManagementEntry')){
          const data=new URLSearchParams(request.postData()||'');
          console.log('  EVIDENCE native note response '+JSON.stringify({method:data.get('method'),status:response.status(),durableDraftLiteral:data.get('note')?.includes(draft)||false}));
        }
      });
      chart=await openChart(original.context,original.master,s.recorder,20000);await waitForNavbars(chart,20000);
      await note(chart).fill(baseline);await chart.locator('#saveImg').click();
      await expectValue(sql,`SELECT CONCAT(COUNT(*),'/',SUM(signed=0 AND LOCATE(${q(baseline)},note)>0)) FROM casemgmt_note WHERE demographic_no=${patient} AND provider_no=${q(writer.providerNo)}`,'1/1','Native baseline note was not saved exactly once');
      await chart.waitForFunction(()=>document.querySelector('#caseManagementEntryForm')?.getAttribute('aria-busy')!=='true');
      await chart.waitForLoadState('networkidle');
      // Finish the baseline's managed login before opening the distinct draft
      // editor. Its normal teardown logout retires every lock of that session.
      // The later session-loss experiment uses only the new browser context.
      await original.context.close();
      await expectValue(sql,`SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${patient} AND provider_no=${q(writer.providerNo)}`,'0','Baseline login did not retire its note locks');
      original=await openSecondSession(s,{label:'clinical-recovery-editor',config:writerConfig});
      chart=await openChart(original.context,original.master,s.recorder,20000);await waitForNavbars(chart,20000);
      await chart.waitForLoadState('networkidle');
      await chart.waitForFunction(()=>typeof issueUpdatesPending==='number'&&issueUpdatesPending===0&&!noteSwitchSavePending&&!issueRefreshPending);
      const response=chart.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/CaseManagementEntry')
        &&new URLSearchParams(r.request().postData()||'').get('method')==='autosave'&&new URLSearchParams(r.request().postData()||'').get('note')?.includes(draft),{timeout:60000});response.catch(()=>{});
      await note(chart).click();await note(chart).press('Control+End');await note(chart).pressSequentially(' '+draft,{delay:10});
      h.assert((await response).status()===200,'Baseline draft autosave failed');
      await expectValue(sql,`SELECT CONCAT(COUNT(*),'/',SUM(LOCATE(${q(draft)},note)>0)) FROM casemgmt_tmpsave WHERE demographic_no=${patient} AND provider_no=${q(writer.providerNo)}`,'1/1','Draft was not durably autosaved');
      h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${patient} AND provider_no=${q(writer.providerNo)}`)==='1','Durable draft has no unique owned editor lock');
      durableText=await note(chart).inputValue();
      const list=await popup(chart,chart.locator('#menuTitleeforms a').first(),'clinical-recovery-eform-list');
      form=await popup(list,list.locator('#efmTable a').filter({hasText:name}).first(),'clinical-recovery-eform');
      await form.locator('#'+field).fill(formSaved);await form.locator('#remote_eform_subject').fill(marker+' recovery baseline');
      await form.locator('#remoteSubmitButton').click();
      await expectValue(sql,`SELECT CONCAT(COUNT(*),'/',SUM(v.var_value=${q(formSaved)})) FROM eform_values v JOIN eform_data d ON d.fdid=v.fdid WHERE d.demographic_no=${patient} AND d.fid=${fid} AND v.var_name=${q(field)}`,'1/1','Native saved form baseline differs');
      // Reopen the saved instance, rather than accidentally testing another new form.
      if(!form.isClosed())await form.close();form=await openSavedForm(chart,original.context);
      h.assert(await form.locator('#'+field).inputValue()===formSaved,'Reopened baseline form differs');
      savedFdid=sql.value(`SELECT fdid FROM eform_data WHERE demographic_no=${patient} AND fid=${fid}`);
      h.assert(/^[1-9]\d*$/.test(savedFdid),'Saved baseline has no unique eForm instance identity');
      h.assert(sql.value(`SELECT form_provider FROM eform_data WHERE fdid=${savedFdid}`)===writer.providerNo,'Native baseline form has the wrong saving account');
      // Prepare the separate native Logout window while the session is valid.
      // Its cold schedule load must not consume a pending autosave's XHR budget.
      enderContext=await h.newContext(original.context.browser(),config);
      enderContext.on('page',page=>h.wireStrictPage(page,'clinical-session-ender',s.recorder));
      await enderContext.addCookies(await original.context.cookies());enderPage=await enderContext.newPage();
      await h.gotoApp(enderPage,config.baseUrl,'/provider/providercontrol');await enderPage.waitForLoadState('networkidle');
      await chart.waitForLoadState('networkidle');
      savedState=snapshots();h.assert(savedState.notes.length===1&&savedState.drafts.length===1&&savedState.forms.length===1,'Baseline has missing or extra owned clinical rows');
    });
    await s.step('pending native editor requests are held before dispatch and the server session ends elsewhere',async()=>{
      held=heldClinicalRequests({context:original.context},{matches:()=>true,identity:marker,select:request=>{
        const url=new URL(request.url()),base=new URL(config.baseUrl);if(url.origin!==base.origin)return null;
        if(url.pathname.endsWith('/status/SessionHeartbeat'))return {kind:'heartbeat',write:false};
        if(request.method()!=='POST')return null;const type=request.headers()['content-type']||'';
        const data=/multipart\/form-data/i.test(type)?new URLSearchParams(multipartFields(request.postDataBuffer(),type)):new URLSearchParams(request.postData()||'');
        if(url.pathname.endsWith('/CaseManagementEntry')&&data.get('method')==='autosave'){
          h.assert(data.get('demographicNo')===patient&&request.frame().page()===chart,'Owned autosave names another patient/editor');return {kind:'note',write:true};}
        if(data.get(field)===formPending){
          h.assert(url.pathname.endsWith('/eform/addEForm'),'Pending eForm used an unrecognized native save route');
          // The viewer's server-added demographicNo/fid/fdid elements have IDs,
          // but no submitted names. Native Save identifies the patient/template
          // in its action URL; bind the source to the actual reopened instance.
          const source=request.frame();
          h.assert(source.page()===form&&new URL(source.url()).searchParams.get('fdid')===savedFdid
            &&url.searchParams.get('efmfid')===fid&&url.searchParams.get('efmdemographic_no')===patient
            &&url.searchParams.get('efmprovider_no')===writer.providerNo
            &&['efmfid','efmdemographic_no','efmprovider_no'].every(key=>url.searchParams.getAll(key).length===1)
            &&data.getAll(field).length===1,'Pending form names another patient/template/instance');return {kind:'form',write:true};}
        return null;
      }});s.cleanup(()=>held.cleanup());await held.install();for(const kind of ['note','form','heartbeat'])held.hold(kind);
      expiredMark=failureMark(s.recorder);
      pendingText=durableText+' '+pending;await note(chart).fill(pendingText);
      // Fill is one native input operation. A timer already in flight may still
      // carry D1; acknowledge only that exact older draft while authentication is
      // live, keeping every future autosave held until the full N2 request exists.
      const preceding=new Set();let pendingRequest;
      for(let attempt=0;attempt<3&&!pendingRequest;attempt++){
        const record=await held.wait(r=>r.kind==='note'&&r.state==='held'&&!preceding.has(r));
        const text=new URLSearchParams(record.request.postData()||'').get('note');
        if(text===pendingText){pendingRequest=record;break;}
        h.assert(text===durableText,'Native pre-expiry autosave contains unaccounted partial content');preceding.add(record);held.releaseOne(record);
        await held.wait(r=>r===record&&r.state==='acknowledged'&&r.delivered);
        h.assert(record.status===200&&record.body.toString().trim()==='','The older durable draft was not actually acknowledged');assertDurableDraft();
      }
      h.assert(pendingRequest,'The native editor did not produce the complete pending autosave');
      console.log('  EVIDENCE pending native note held before dispatch');
      h.assert(await chart.locator(STATUS).getAttribute('data-autosave-state')==='pending','Held autosave is falsely marked saved');
      await form.locator('#'+field).fill(formPending);
      pendingFormSourceUrl=form.url();
      const click=form.locator('#remoteSubmitButton').click({noWaitAfter:true});click.catch(()=>{});
      await held.wait(r=>r.kind==='form'&&r.state==='held');await click;
      console.log('  EVIDENCE pending native saved-eForm request held before dispatch');
      const heldState=snapshots();
      for(const key of ['notes','forms','values'])assert.deepEqual(heldState[key],savedState[key],'Holding requests changed saved '+key);
      assertDurableDraft();noPending();savedState=heldState;
      try{await enderPage.locator('#logoutButton').click();await enderPage.locator('#username').waitFor();}
      finally{await enderContext.close();}
      const beat=await original.context.request.get(h.appUrl(config.baseUrl,'/status/SessionHeartbeat'),{maxRedirects:0});
      try{h.assert(beat.status()===200&&(await beat.json()).valid===false,'Native out-of-band logout left the session valid');}finally{await beat.dispose();}
      h.assert(!chart.isClosed()&&await note(chart).inputValue()===pendingText,'Original editor received a logout broadcast before its held save');
    });
    await s.step('expired saves are refused with truthful feedback and no unauthorized clinical write',async()=>{
      held.release('note');held.release('form');
      const refused=[];for(const kind of ['note','form'])refused.push(await held.wait(r=>r.kind===kind&&r.state==='acknowledged'));
      for(const record of refused)assertAuthenticationRefusal(record,config.baseUrl,unauthenticatedSecrets);
      await waitUntil(async()=>await chart.locator(STATUS).getAttribute('data-autosave-state')==='failed','visible failed draft status');
      h.assert(await note(chart).inputValue()===pendingText,'Refused autosave discarded pending editor text');
      assert.deepEqual(snapshots(),savedState,'An unauthenticated save changed clinical rows or durable draft');noPending();
      await form.waitForURL(url=>String(url)!==pendingFormSourceUrl,{waitUntil:'load'});
      h.assert(!form.isClosed(),'Refused form save closed without visible feedback');
      const feedback=await form.locator('body').innerText(),formRefusal=refused.find(record=>record.kind==='form');
      assertVisibleRefusal({status:formRefusal.status,loginCount:await form.locator('#username').count(),feedback});
      const errorContent=(await form.content())+'\n'+feedback;
      h.assert(unauthenticatedSecrets.every(text=>!errorContent.includes(text)),'Login/error page exposed patient identity or saved/draft/pending clinical text');
      // Seal only this short assertion boundary. Native retries resume in the
      // next step, so they reach the real expired session instead of timing out
      // artificially behind a held route while waiting for the heartbeat.
      held.hold('note');
      await waitUntil(()=>held.records.filter(r=>r.kind==='note'||r.kind==='form').every(r=>r.state==='held'||r.state==='acknowledged'&&r.delivered),'completed native refusal delivery');
      const observed=held.records.filter(r=>['note','form'].includes(r.kind)&&r.state==='acknowledged');
      for(const record of observed){assertAuthenticationRefusal(record,config.baseUrl,unauthenticatedSecrets);consumedRefusals.add(record);}
      consumeRefusalDiagnostics(s.recorder,expiredMark,observed);
    });
    await s.step('the native expired heartbeat closes the old chart and returns its schedule to login',async()=>{
      const after=failureMark(s.recorder);held.release('note');held.release('heartbeat');
      const beat=await held.wait(r=>r.kind==='heartbeat'&&r.state==='acknowledged'&&JSON.parse(r.body.toString()).valid===false,90000);
      h.assert(beat.status===200,'Native expired heartbeat failed');
      await waitUntil(()=>chart.isClosed(),'expired clinical chart closure',75000);await original.schedule.locator('#username').waitFor({timeout:75000});
      for(const page of original.context.pages())if(!page.isClosed()&&await page.locator('#username').count()){
        const body=(await page.content())+'\n'+await page.locator('body').innerText();
        h.assert(unauthenticatedSecrets.every(text=>!body.includes(text)),'Login/error page exposed patient identity or saved/draft/pending clinical text');
      }
      // Logout's session listener must have retired the former session's lock.
      await expectValue(sql,`SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${patient} AND provider_no=${q(writer.providerNo)}`,'0','Expired session retained a clinical lock');
      consumeUnloadBeacons(s.recorder,after);assert.deepEqual(snapshots(),savedState,'Expiry changed saved clinical content');
      for(const record of held.records)if(record.deliveryError&&record.page?.isClosed())held.acceptClosedDelivery(record);
      await held.shutdown();
      const later=held.records.filter(record=>!consumedRefusals.has(record)&&record.state==='acknowledged');
      for(const record of later.filter(record=>record.kind==='note'||record.kind==='form'))assertAuthenticationRefusal(record,config.baseUrl,unauthenticatedSecrets);
      for(const record of held.records.filter(record=>record.kind==='heartbeat'&&record.state==='acknowledged'))
        h.assert(record.status===200&&JSON.parse(record.body.toString()).valid===false,'A captured native heartbeat revived or misreported the expired session');
      consumeRefusalDiagnostics(s.recorder,after,later.filter(record=>record.kind==='note'||record.kind==='form'));
      consumeAcknowledgedAborts(s.recorder,after,held.records);
      // The same browser now enters a new, authenticated recovery phase. Keep
      // supervising its real writes, without aborting the recovered D1 autosave.
      held.resume();
    });
    await s.step('same-account recovery in the original browser restores only the acknowledged draft and saved eForm',async()=>{
      const schedule=await h.login(original.context,writerConfig,s.recorder,{label:'same-account-recovery'});
      const {masterPage}=await openMasterRecord(original.context,schedule,s.recorder,{searchTerm:marker,preferredDemographicNo:patient,timeout:20000});
      chart=await openChart(original.context,masterPage,s.recorder,20000);await waitForNavbars(chart,20000);
      h.assert((await note(chart).inputValue()).trim()===durableText.trim(),'Same user did not recover exactly the acknowledged private draft');
      assertDurableDraft();
      form=await openSavedForm(chart,original.context);h.assert(await form.locator('#'+field).inputValue()===formSaved,'Same user recovered pending or duplicated form content');
      noPending();h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}`)==='1'&&sql.value(`SELECT COUNT(*) FROM eform_data WHERE demographic_no=${patient}`)==='1','Recovery duplicated saved clinical rows');
      const mark=failureMark(s.recorder);await releaseChartLocks(original.context,config.baseUrl);await chart.close();await form.close();consumeUnloadBeacons(s.recorder,mark);
      await schedule.locator('#logoutButton').click();await schedule.locator('#username').waitFor();
    });
    await s.step('another account in the same browser cannot see the previous account’s private pending work or draft',async()=>{
      const schedule=await h.login(original.context,{...config,testUser:other.username},s.recorder,{label:'other-account-same-browser'});
      const {masterPage}=await openMasterRecord(original.context,schedule,s.recorder,{searchTerm:marker,preferredDemographicNo:patient,timeout:20000});
      chart=await openChart(original.context,masterPage,s.recorder,20000);await waitForNavbars(chart,20000);
      h.assert(await note(chart).isVisible(),'Other account has no usable editor');
      for(const page of original.context.pages())if(!page.isClosed()){
        const content=await page.content();h.assert([pending,formPending,draft].every(text=>!content.includes(text)),'Previous account private content remains in the shared browser');
        const stored=await page.evaluate(()=>JSON.stringify(Object.entries(sessionStorage)));
        h.assert([pending,formPending,draft].every(text=>!stored.includes(text)),'Previous account private content remains in browser session storage');
      }
      const storage=JSON.stringify((await original.context.storageState({indexedDB:true})).origins);
      h.assert([pending,formPending,draft].every(text=>!storage.includes(text)),'Previous account private content remains in browser local/IndexedDB storage');
      form=await openSavedForm(chart,original.context);h.assert(await form.locator('#'+field).inputValue()===formSaved,'Other account saw pending form text');noPending();
      assert.deepEqual(controlState(),controlBefore,'Unrelated control clinical history changed');
      assert.deepEqual(snapshots().notes,savedState.notes,'Read/recovery changed saved note history');
      assert.deepEqual(snapshots().forms,savedState.forms,'Read/recovery changed saved form history');
      assert.deepEqual(snapshots().values,savedState.values,'Read/recovery changed saved form values');
      h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no=${patient} AND provider_no=${q(other.providerNo)}`)==='0','Other account acquired the prior account draft');
      assertDurableDraft();
      const mark=failureMark(s.recorder);await releaseChartLocks(original.context,config.baseUrl);await chart.close();await form.close();consumeUnloadBeacons(s.recorder,mark);
    });
  }catch(error){primary=error;console.error('  session-recovery assertion failed before cleanup: '+error.message);}
  try{if(held)await held.shutdown();}catch(error){primary=primary?new AggregateError([primary,error],primary.message+'; request shutdown: '+error.message):error;}
  if(primary)throw primary;
}
if(require.main===module)runWorkflow('session-clinical-recovery',workflow,{preflightConfig});
module.exports={workflow,preflightConfig,assertAuthenticationRefusal,consumeRefusalDiagnostics,assertVisibleRefusal,consumeAcknowledgedAborts};
