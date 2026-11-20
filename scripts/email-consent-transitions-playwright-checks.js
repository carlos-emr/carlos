#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
'use strict';
const h=require('./lib/playwright-harness'),ui=require('./lib/playwright-ui');
const {runWorkflow,expectValue}=require('./lib/workflow-session');
const {captureMail}=require('./lib/loopback-mail-capture');
const {prepareOwnedMasterPostal,assertOwnedMasterPostal}=require('./lib/owned-master-postal');
const {openMasterRecord}=require('./master-record-tabs-playwright-checks');
const {reserveInstanceIds}=require('./eform-deployed-page-exclusion-playwright-checks');
const {nextEmailSenderId}=require('./lib/email-sender-fixture-id');
const q=h.sqlString,NAME='email-consent-transitions';
const STATUS={in:'Explicit Opt-In',out:'Explicit Opt-Out',unknown:'Unknown'};

/** Pin the release's warning/acknowledgment and composer-snapshot contract. */
async function workflow(s) {
  const {sql,patient,marker,provider}=s,recipient=marker.toLowerCase()+'@example.com',sender=marker.toLowerCase()+'-sender@example.com';
  const properties=sql.rows("SELECT value FROM property WHERE name='email_communication'");
  if(!properties.length)throw new h.SkipCheck('Email consent tracking must be configured');
  h.assert(properties.length===1&&properties[0][0],'Ambiguous configured email consent type');
  const type=properties[0][0].split(/[,;\s()]+/)[0];
  h.assert(/^[A-Za-z_][A-Za-z0-9_]*$/.test(type),'Unsupported native consent type identity');
  const types=sql.rows(`SELECT id,name FROM consentType WHERE type=${q(type)} AND active=1`);
  if(!types.length)throw new h.SkipCheck('Configured email consent type must be active');
  h.assert(types.length===1,'Ambiguous active email consent type');
  const [typeId,typeName]=types[0],sink=await captureMail(recipient);s.cleanup(()=>sink.close());
  const name=marker+'MAILCONSENT',filename=marker+'.html',details=JSON.stringify({host:'127.0.0.1',port:String(sink.port)});
  const html='<html><head><title>'+name+'</title></head><body><form method="post" name="FormName" id="FormName">'
    +'<input name="ownedDraft" id="ownedDraft" value="'+marker+'-FORM">'
    +'<input type="hidden" name="attachEFormToEmail" value="false"><input type="hidden" name="enableEmailEncryption" value="false">'
    +'<input type="hidden" name="encryptEmailAttachments" value="false"><input type="hidden" name="emailPatientChartOption" value="doNotAddAsNote">'
    +'</form></body></html>';
  let configId,fid,claimed=false;
  h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM emailConfig WHERE senderEmail=${q(sender)})+(SELECT COUNT(*) FROM eform WHERE form_name=${q(name)})+(SELECT COUNT(*) FROM Consent WHERE demographic_no=${patient})`)==='0','Email consent fixture namespace collision');
  s.cleanup(()=>{
    if(!claimed)return;
    const namedConfigs=sql.rows(`SELECT id FROM emailConfig WHERE senderEmail=${q(sender)}`).flat();
    h.assert(namedConfigs.length<=1,'Ambiguous sender fixture');
    const configs=[...new Set([...namedConfigs,...(configId?[configId]:[])])];
    h.assert(configs.every(id=>/^[1-9]\d*$/.test(id)),'Invalid sender fixture identity');
    for(const id of configs) {
      if(sql.value(`SELECT COUNT(*) FROM emailConfig WHERE id=${id}`)==='0')continue;
      h.assert(!configId||id===configId,'Sender identity changed');
      h.assert(sql.value(`SELECT COUNT(*) FROM emailConfig WHERE id=${id} AND senderEmail=${q(sender)} AND configDetails=${q(details)} AND emailProvider='LOCAL'`)==='1','Sender fixture ownership changed');
      const logs=`SELECT id FROM emailLog WHERE configId=${id}`;
      h.assert(sql.value(`SELECT COUNT(*) FROM emailLog WHERE configId=${id} AND COALESCE((DemographicNo=${patient} AND fromEmail=${q(sender)} AND toEmail=${q(recipient)} AND subject LIKE ${q(marker+'%')}),0)=0`)==='0','Unexpected sender log attribution');
      h.assert(sql.value(`SELECT COUNT(*) FROM emailAttachment WHERE logId IN (${logs})`)==='0','Unexpected email attachment');
      sql.execute(`DELETE FROM emailLog WHERE configId=${id} AND DemographicNo=${patient}; DELETE FROM emailConfig WHERE id=${id} AND senderEmail=${q(sender)}`);
    }
    const namedTemplates=sql.rows(`SELECT fid FROM eform WHERE form_name=${q(name)}`).flat();
    h.assert(namedTemplates.length<=1,'Ambiguous mail template fixture');
    const templates=[...new Set([...namedTemplates,...(fid?[fid]:[])])];
    h.assert(templates.every(id=>/^[1-9]\d*$/.test(id)),'Invalid mail template fixture identity');
    for(const id of templates) {
      if(sql.value(`SELECT COUNT(*) FROM eform WHERE fid=${id}`)==='0') {
        h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM eform_data WHERE fid=${id})+(SELECT COUNT(*) FROM eform_values WHERE fid=${id})`)==='0','Missing mail template retains saved references');
        continue;
      }
      h.assert((!fid||id===fid)&&sql.value(`SELECT COUNT(*) FROM eform WHERE fid=${id} AND form_name=${q(name)} AND file_name=${q(filename)} AND form_html=${q(html)} AND form_creator=${q(provider)}`)==='1','Mail template fixture ownership changed');
      h.assert(sql.value(`SELECT COUNT(*) FROM eform_data WHERE (fid=${id} OR form_name=${q(name)}) AND COALESCE((fid=${id} AND form_name=${q(name)} AND demographic_no=${patient} AND form_provider=${q(provider)}),0)=0`)==='0','Mail saved instance ownership changed');
      h.assert(sql.value(`SELECT COUNT(*) FROM EFormDocs WHERE fdid IN (SELECT fdid FROM eform_data WHERE fid=${id})`)==='0','Unexpected mail eForm attachment');
      sql.execute(`DELETE FROM eform_values WHERE fdid IN (SELECT fdid FROM eform_data WHERE fid=${id}); DELETE FROM eform_data WHERE fid=${id} AND demographic_no=${patient}; DELETE FROM eform WHERE fid=${id} AND form_name=${q(name)}`);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}`)==='0','Email unexpectedly created a clinical note; retain it');
    h.assert(sql.value(`SELECT COUNT(*) FROM Consent WHERE demographic_no=${patient} AND consent_type_id<>${typeId}`)==='0','Unexpected patient consent type');
    sql.execute(`DELETE FROM Consent WHERE demographic_no=${patient} AND consent_type_id=${typeId}; DELETE FROM demographicExtArchive WHERE demographic_no=${patient}; DELETE FROM demographiccust WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM emailConfig WHERE senderEmail=${q(sender)})+(SELECT COUNT(*) FROM emailLog WHERE fromEmail=${q(sender)})+(SELECT COUNT(*) FROM eform WHERE form_name=${q(name)})+(SELECT COUNT(*) FROM eform_data WHERE form_name=${q(name)} OR demographic_no=${patient})+(SELECT COUNT(*) FROM Consent WHERE demographic_no=${patient})`)==='0','Email consent fixture rows remain');
  });
  claimed=true;
  configId=nextEmailSenderId(sql);
  sql.execute(`UPDATE demographic SET email=${q(recipient)} WHERE demographic_no=${patient} AND last_name=${q(marker)};
    INSERT INTO emailConfig(id,emailType,emailProvider,active,senderFirstName,senderLastName,senderEmail,configDetails)
    VALUES(${configId},'SMTP','LOCAL',1,'Coverage','Consent',${q(sender)},${q(details)})`);
  h.assert(sql.value(`SELECT id FROM emailConfig WHERE senderEmail=${q(sender)}`)===configId,'Owned SMTP sender missing');
  sql.execute(`INSERT INTO eform(form_name,file_name,subject,form_date,form_time,form_creator,status,form_html,showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable)
    VALUES(${q(name)},${q(filename)},${q(name)},CURDATE(),CURTIME(),${q(provider)},1,${q(html)},0,0,'',0,1)`);
  fid=sql.value(`SELECT fid FROM eform WHERE form_name=${q(name)}`);h.assert(/^[1-9]\d*$/.test(fid),'Owned consent mail eForm missing');reserveInstanceIds(s,{id:fid,name});
  await prepareOwnedMasterPostal(s);
  const other=await h.newContext(s.context.browser(),s.config);s.cleanup(()=>other.close());
  other.on('page',page=>h.wireStrictPage(page,NAME, s.recorder));
  const schedule=await h.login(other,s.config,s.recorder);
  const {masterPage:master}=await openMasterRecord(other,schedule,s.recorder,{searchTerm:marker,preferredDemographicNo:patient,timeout:25000});
  const second={...s,context:other,schedule,master};
  async function setConsent(state) {
    await master.reload({waitUntil:'load'});await master.locator('#editBtn').click();await master.locator('#editDemographic').waitFor({state:'visible'});
    if(state==='unknown') {
      const dialogs=await h.withExpectedDialogs(master,()=>master.locator(`input[name="clearRadio_${type}_btn"]`).click());
      h.assert(dialogs.length===1,'Native consent Clear lacks its confirmation');
    }else await master.locator(`#opt${state==='in'?'in':'out'}_${type}`).check();
    await assertOwnedMasterPostal(second);
    await ui.clickAndAwaitReload(master,master.locator('#updateButton input[type="submit"]').first());
    const live=sql.rows(`SELECT explicit,optout FROM Consent WHERE demographic_no=${patient} AND consent_type_id=${typeId} AND deleted=0`);
    h.assert(state==='unknown'?live.length===0:live.length===1&&JSON.stringify(live[0])===JSON.stringify(state==='in'?['1','0']:['1','1']),'Native consent state was not persisted');
    h.assert(sql.value(`SELECT email FROM demographic WHERE demographic_no=${patient} AND last_name=${q(marker)}`)===recipient,'Consent save changed patient/recipient');
  }
  const chart=await s.chart(),list=await s.popup(chart,chart.locator('a[onclick*="/eform/efmformslistadd"]').first(),'consent-mail-forms');
  let deliveries=0;
  const logs=()=>sql.value(`SELECT COUNT(*) FROM emailLog WHERE configId=${configId}`);
  async function compose(state,{refuseFirst=false}={}) {
    const form=await s.popup(list,list.getByRole('link',{name,exact:true}),'consent-mail-form');
    await form.locator('#remoteEmailButton').waitFor();
    h.assert(await form.locator('#emailConsentStatus').inputValue()===STATUS[state],'eForm toolbar has the wrong initial consent');
    const before=sql.value(`SELECT COUNT(*) FROM eform_data WHERE fid=${fid}`),oldLogs=logs();
    if(refuseFirst) {
      const dialogs=await h.withExpectedDialogs(form,()=>form.locator('#remoteEmailButton').click(),{promptText:'No'});
      h.assert(dialogs.length===1&&dialogs[0].text.includes(typeName)&&dialogs[0].text.includes('not explicitly opted-in'),'Non-opt-in consent lacks the native warning');
      h.assert(sql.value(`SELECT COUNT(*) FROM eform_data WHERE fid=${fid}`)===before&&logs()===oldLogs&&sink.messages.length===deliveries,'Refused consent warning saved/sent work');
      h.assert(await form.locator('#ownedDraft').inputValue()===marker+'-FORM','Refusing consent discarded the eForm draft');
    }
    const dialogs=await h.withExpectedDialogs(form,()=>form.locator('#remoteEmailButton').click(),{promptText:'Yes'});
    h.assert(dialogs.length===(state==='in'?0:1),'Consent entry prompt count changed');
    await form.waitForURL(/\/email\/emailComposeAction/);await form.locator('#senderEmailAddress').selectOption(configId);
    await form.locator('#doNotAddAsNoteOption').check();
    h.assert(await form.locator('input[name="emailConsentStatus"]').inputValue()===STATUS[state],'Composer has the wrong consent snapshot');
    h.assert((await form.locator('input[name="emailConsentStatus"]').locator('..').innerText()).includes(typeName),'Composer lacks the configured consent name');
    h.assert(await form.locator('[name="receiverEmailAddress"][type="hidden"]').inputValue()===recipient,'Composer names another recipient');return form;
  }
  async function send(page,label,pendingBody) {
    const subject=marker+' '+label,body=pendingBody??marker+' BODY '+label;
    await page.locator('#subjectEmail').fill(subject);
    if(pendingBody===undefined)await page.locator('#bodyEmail').fill(body);
    h.assert(await page.locator('#bodyEmail').inputValue()===body,'Pending draft changed before actual send');
    const [response]=await Promise.all([page.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/email/emailSendAction')),
      page.locator('#btnSend').click()]);
    h.assert(response.status()===200,'Native consent mail send failed');await page.locator('#successMessage').waitFor();deliveries++;
    await expectValue(sql,`SELECT COUNT(*) FROM emailLog WHERE configId=${configId} AND DemographicNo=${patient} AND status='SUCCESS'`,String(deliveries),'Send did not persist exactly one owned success log');
    h.assert(logs()===String(deliveries)&&sink.messages.length===deliveries,'SMTP capture and email log counts differ');
    const payload=sink.messages[deliveries-1].replace(/=\r\n/g,'');
    h.assert(payload.includes(subject)&&payload.includes(body)&&payload.includes(recipient),'Captured mail has the wrong subject/body/recipient');
    h.assert(sql.value(`SELECT COUNT(*) FROM emailLog WHERE configId=${configId} AND DemographicNo=${patient} AND subject=${q(subject)} AND toEmail=${q(recipient)} AND fromEmail=${q(sender)} AND status='SUCCESS' AND CONVERT(FROM_BASE64(body) USING utf8mb4)=${q(body)}`)==='1','Email log lost exact delivery attribution/content');
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}`)==='0','Do not add as note created a clinical note');
    if(!page.isClosed())await page.close();
  }
  for(const state of ['unknown','out','in'])await s.step('fresh '+STATUS[state]+': truthful consent display, warning acknowledgment and one actual delivery',async()=>{
    if(state!=='unknown')await setConsent(state);
    await send(await compose(state,{refuseFirst:state!=='in'}),'fresh-'+state);
  });
  for(const changed of ['out','unknown'])await s.step('open opt-in composer then native '+STATUS[changed]+': snapshot, validation and actual send contract',async()=>{
    await setConsent('in');const draft=await compose('in');
    const text=marker+' PENDING '+changed;await draft.locator('#bodyEmail').fill(text);
    await setConsent(changed);
    h.assert(await draft.locator('input[name="emailConsentStatus"]').inputValue()===STATUS.in,'Existing composer did not retain the documented consent snapshot');
    await draft.locator('#subjectEmail').fill('');const before=logs();await draft.locator('#btnSend').click();await draft.locator('#subjectError').waitFor({state:'visible'});
    h.assert(await draft.locator('#bodyEmail').inputValue()===text&&logs()===before&&sink.messages.length===deliveries,'Validation discarded/sent the pending draft');
    // Current release does not recheck consent at send. This is explicit observed
    // coverage of its existing acknowledgment/snapshot policy, not an opt-in gate.
    await send(draft,'changed-'+changed,text);
    const fresh=await compose(changed,{refuseFirst:true});await fresh.close();
  });
  await s.step('restored native opt-in sends exactly once and all six deliveries reconcile',async()=>{
    await setConsent('in');await send(await compose('in'),'restored-in');
    h.assert(deliveries===6&&sink.messages.length===6&&logs()==='6','Unexpected final consent mail delivery count');
  });
}

if(require.main===module)runWorkflow(NAME,workflow,{preflightConfig:()=>{
  if(process.env.CARLOS_DISPOSABLE_HOST!=='true'||process.env.EXCLUSIVE!=='1')throw new h.SkipCheck('Local SMTP consent lifecycle requires CARLOS_DISPOSABLE_HOST=true and EXCLUSIVE=1');
}});
module.exports={workflow};
