#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
// #5708: native multi-recipient saves/removal and fresh CML routing. The offered
// status is source-wide (all active rows), never a per-recipient setting.
const crypto=require('node:crypto');
const fs=require('node:fs');
const os=require('node:os');
const path=require('node:path');
const h=require('./lib/playwright-harness');
const ui=require('./lib/playwright-ui');
const {revealAuditLink}=require('./lib/playwright-link-audit');
const {runWorkflow,expectValue}=require('./lib/workflow-session');
const {throwawayLoginFixture}=require('./lib/throwaway-login-fixture');
const {syntheticCmlLab}=require('./lab-upload-playwright-checks');
const {unusedNumber,openInbox,openForwardingRules,confirmAndReload,uploadFromInbox,
  removeOwnedHl7Labs,removeArchiveFiles,archivesNamed}=require('./lab-forwarding-rules-playwright-checks');
const TIMEOUT=30000;

async function workflow(s) {
  const {sql,marker,config,recorder,patient}=s;
  if(!process.env.LAB_UPLOAD_DOCUMENT_STORE)throw new h.SkipCheck('LAB_UPLOAD_DOCUMENT_STORE is required for verified upload cleanup');
  const fixture=throwawayLoginFixture({sql,marker,provider:s.provider,testUser:config.testUser});
  const targets=[];
  const uploads=[];
  let rulesOwned=false;
  const work=fs.mkdtempSync(path.join(os.tmpdir(),'carlos-fwd-multi-'));
  s.cleanup(()=>fixture.cleanup());
  s.cleanup(()=>{
    const failures=[];
    for(const id of targets)try {
      // IDs are intents registered before INSERT. A failed insert can leave no
      // row; retaining the intent also covers a lost response after commit.
      if(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${h.sqlString(id)}`)==='0')continue;
      h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${h.sqlString(id)} AND last_name=${h.sqlString(marker)}`)==='1','Forwarding provider ownership changed');
      sql.execute(`DELETE FROM provider WHERE provider_no=${h.sqlString(id)} AND last_name=${h.sqlString(marker)}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${h.sqlString(id)}`)==='0','Forwarding provider cleanup failed');
    }catch(error){failures.push(error);}
    if(failures.length)throw new AggregateError(failures,'Forwarding provider cleanup failed: '+failures.map(error=>error.message).join('; '));
  });
  s.cleanup(()=>{
    for(const u of uploads) {
      const labs=sql.rows(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum=${h.sqlString(u.accession)}`).flat();
      removeOwnedHl7Labs(sql,labs);
      const predicate=`filename LIKE ${h.sqlString(`LabUpload.${u.name}.%`)}`;
      sql.execute(`DELETE FROM fileUploadCheck WHERE ${predicate}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM fileUploadCheck WHERE ${predicate}`)==='0','Upload checksum cleanup failed');
      removeArchiveFiles(archivesNamed(u.name));
    }
    if(rulesOwned) {
      const sources=[fixture.providerNo,...targets].map(h.sqlString).join(',');
      sql.execute(`DELETE FROM incomingLabRules WHERE provider_no IN (${sources})`);
      h.assert(sql.value(`SELECT COUNT(*) FROM incomingLabRules WHERE provider_no IN (${sources})`)==='0','Forwarding rule cleanup failed');
    }
    fs.rmSync(work,{recursive:true,force:true});
  });
  fixture.create();
  const source=fixture.providerNo;
  const ohip=unusedNumber(sql,'ohip_no');
  sql.execute(`UPDATE provider SET ohip_no=${h.sqlString(ohip)} WHERE provider_no=${h.sqlString(source)}`);
  for(const name of ['RecipientA','RecipientB','RuleControl']) {
    const id=unusedNumber(sql,'provider_no');targets.push(id);
    sql.execute(`INSERT INTO provider(provider_no,last_name,first_name,provider_type,specialty,sex,status,ohip_no,lastUpdateUser,lastUpdateDate)
      VALUES(${h.sqlString(id)},${h.sqlString(marker)},${h.sqlString(name)},'doctor','','F','1','',${h.sqlString(s.provider)},NOW())`);
  }
  const [a,b,control]=targets;
  h.assert(sql.value(`SELECT COUNT(*) FROM incomingLabRules WHERE provider_no IN (${[source,...targets].map(h.sqlString).join(',')})`)==='0','Allocated provider number has pre-existing forwarding rules');
  rulesOwned=true;
  sql.execute(`INSERT INTO incomingLabRules(provider_no,frwdProvider_no,status,archive) VALUES(${h.sqlString(control)},${h.sqlString(b)},'N','0')`);
  const controlRows=()=>JSON.stringify(sql.rows(`SELECT * FROM incomingLabRules WHERE provider_no=${h.sqlString(control)} ORDER BY id`));
  const unchanged=controlRows();
  const rows=()=>sql.rows(`SELECT frwdProvider_no,status,archive FROM incomingLabRules WHERE provider_no=${h.sqlString(source)} ORDER BY id`);
  async function assertRules(recipients,status) {
    const expected=recipients.map(id=>[id,status]).sort((x,y)=>x[0].localeCompare(y[0]));
    await expectValue(sql,`SELECT COALESCE(GROUP_CONCAT(CONCAT(frwdProvider_no,':',status) ORDER BY frwdProvider_no SEPARATOR '|'),'') FROM incomingLabRules WHERE provider_no=${h.sqlString(source)} AND archive='0'`,expected.map(r=>r.join(':')).join('|'),'Active forwarding rules differ or contain duplicates');
    h.assert(controlRows()===unchanged,'Another source clinician\'s rule changed');
  }
  const context=await h.newContext(s.context.browser(),config);
  context.on('page',page=>h.wireStrictPage(page,'fwd-multi',recorder));
  const schedule=await h.login(context,{...config,testUser:fixture.username},recorder,{label:'fwd-multi'});
  const inbox=await openInbox(schedule,recorder,'fwd-multi-inbox');
  async function update(rules,recipients,status) {
    await rules.locator(`input[name="status"][value="${status}"]`).check();
    await rules.locator('select[name="providerNums"]').selectOption(recipients);
    await confirmAndReload(rules,rules.getByRole('button',{name:'Update Forwarding Rules',exact:true}),'Update multi-recipient rules');
  }
  async function reopened(recipients,status) {
    const rules=await openForwardingRules(inbox,recorder);
    await assertRules(recipients,status);
    h.assert(await rules.locator(`input[name="status"][value="${status}"]`).isChecked(),'Reopened source status differs');
    const text=await rules.locator('ul').allInnerTexts();
    h.assert(recipients.every(id=>text.join('\n').includes(id===a?'RecipientA':'RecipientB')),'Reopened recipient list is incomplete');
    h.assert(await rules.locator('ul li').filter({hasText:marker}).count()===recipients.length,'Reopened recipients are duplicated or stale');
    return rules;
  }
  async function upload(index,recipients,sourceStatus) {
    const accession=`FM${crypto.randomBytes(5).toString('hex').toUpperCase()}`;
    const name=`lab-forward-multi-${index}-${accession}.hl7`;uploads.push({name,accession});
    const file=path.join(work,name);
    fs.writeFileSync(file,Buffer.from(syntheticCmlLab(accession,marker).replaceAll('999998^DR. PROBE',`${ohip}^${marker}`),'latin1'));
    h.assert(await uploadFromInbox(inbox,recorder,file,name)==='Uploaded successfully','CML upload did not succeed');
    await expectValue(sql,`SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=${h.sqlString(accession)}`,'1','Fresh lab was not ingested exactly once');
    const lab=sql.value(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum=${h.sqlString(accession)}`);
    const route=()=>sql.rows(`SELECT provider_no,status FROM providerLabRouting WHERE lab_type='HL7' AND lab_no=${lab} ORDER BY provider_no`);
    const expected=[[source,sourceStatus],...recipients.map(id=>[id,'N'])].sort((x,y)=>x[0].localeCompare(y[0]));
    h.assert(JSON.stringify(route())===JSON.stringify(expected),'Fresh lab routing is missing, duplicated, unintended, or has a wrong status');
    h.assert(sql.value(`SELECT demographic_no FROM patientLabRouting WHERE lab_type='HL7' AND lab_no=${lab}`)===patient,'Lab patient attribution changed');
    return {route,before:JSON.stringify(route())};
  }
  let first,second;
  await s.step('two native recipients reopen exactly once and both receive the first CML lab',async()=>{
    const rules=await openForwardingRules(inbox,recorder);await update(rules,[a,b],'N');await rules.close();
    const again=await reopened([a,b],'N');await update(again,[],'N');await assertRules([a,b],'N');await again.close();
    first=await upload(1,[a,b],'N');
  });
  await s.step('source-wide Filed updates preserve recipients and affect only subsequent lab routing',async()=>{
    const rules=await reopened([a,b],'N');await update(rules,[],'F');await rules.close();
    await (await reopened([a,b],'F')).close();second=await upload(2,[a,b],'F');
    h.assert(JSON.stringify(first.route())===first.before,'Rule update rewrote the first lab\'s review state');
  });
  await s.step('cancelled removal preserves both rules and selective removal preserves the survivor',async()=>{
    const rules=await reopened([a,b],'F');const remove=rules.locator('ul li').filter({hasText:'RecipientA'}).getByRole('link',{name:'Remove',exact:true});
    const before=JSON.stringify(rows());
    const dialogs=await h.withExpectedDialogs(rules,()=>remove.click(),{accept:false});
    h.assert(dialogs.length===1&&dialogs[0].type==='confirm'&&JSON.stringify(rows())===before,'Cancelled removal changed forwarding rules');
    await confirmAndReload(rules,remove,'Remove only recipient A');await assertRules([b],'F');
    h.assert(rows().some(r=>r[0]===a&&r[2]==='1'),'Removed recipient was not archived');await rules.close();
    await (await reopened([b],'F')).close();await upload(3,[b],'F');
    h.assert(JSON.stringify(first.route())===first.before&&JSON.stringify(second.route())===second.before,'Removal changed existing lab routing');
  });
  await s.step('Administration selects the source and preserves another clinician while editing and removing',async()=>{
    const {page:admin}=await ui.clickOpensPopupOrNavigates(s.schedule,s.schedule.locator('#admin-panel,#admin2').first(),{context:s.context,recorder,label:'fwd-multi-admin',timeout:TIMEOUT});
    const link=admin.locator('#adminNav a.contentLink[href$="/admin/labForwardingRules"]').first();await revealAuditLink(admin,link,TIMEOUT);
    await ui.clickInjectsPanel(admin,link,{marker:'#provider-selection',timeout:TIMEOUT});
    await admin.locator('#provider-selection').selectOption(source);
    await admin.locator('#dynamic-content table tbody tr').filter({hasText:'RecipientB'}).waitFor({timeout:TIMEOUT});
    await admin.locator('input[name="status"][value="N"]').check();
    await admin.locator('select[name="providerNums"]').selectOption(a);
    async function mutate(control) {
      const dialogs=await h.withExpectedDialogs(admin,async()=>{
        const [response]=await Promise.all([
          admin.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/admin/ForwardingRules'),{timeout:TIMEOUT}),
          control.click(),
        ]);
        h.assert(response.status()===200,'Administration forwarding save was refused');
        await admin.waitForFunction(()=>!window.jQuery||window.jQuery.active===0,null,{timeout:TIMEOUT});
      });
      h.assert(dialogs.length===1&&dialogs[0].type==='confirm','Administration mutation did not ask for confirmation');
    }
    await mutate(admin.locator('#ForwardRulesForm input[type="submit"]'));await assertRules([a,b],'N');
    await admin.locator('tr').filter({hasText:'RecipientA'}).waitFor({timeout:TIMEOUT});
    await mutate(admin.locator('tr').filter({hasText:'RecipientA'}).locator('button[type="submit"][onclick*="removeProvider"]'));await assertRules([b],'N');
    await admin.close();
  });
  console.log('  Status contract: the native editor applies one source-wide status to every active recipient; it offers no per-recipient status control.');
}
if(require.main===module)runWorkflow('lab-forwarding-multi-recipient',workflow,{openMaster:false});
module.exports={workflow};
