#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
'use strict';
const {randomBytes}=require('node:crypto');
const h=require('./lib/playwright-harness');
const ui=require('./lib/playwright-ui');
const {runWorkflow,expectValue}=require('./lib/workflow-session');
const {throwawayLoginFixture}=require('./lib/throwaway-login-fixture');
const {workflow:recallWorkflow}=require('./lab-recall-delegate-playwright-checks');
const {INBOX_LINK}=require('./messenger-demographic-link-playwright-checks');
const {waitForTicklerTable}=require('./tickler-forward-filters-playwright-checks');
const {revealAuditLink}=require('./lib/playwright-link-audit');
const {saveOwnedProviderProfile}=require('./lib/native-provider-fixture');
const {restartAndWait}=require('./lib/property-profiles');
const q=h.sqlString,NAME='lab-recall-delegate-delivery';

/** Await the real document navigation from an ordinary Messenger control. */
async function clickLoad(page,control) {
  await Promise.all([page.waitForNavigation({waitUntil:'load'}),control.click()]);
  await h.assertNotErrorPage(page,'recall delegate Messenger');
}

/** Send an owned recall, verify delegate usability, and retire clinical fixtures and their caches. */
async function workflow(s) {
  const {sql,marker,patient,config,recorder}=s;
  let cacheCleanupRequired=false;
  // SQL deletes cannot invalidate Spring's active-provider cache. Run last, after
  // every owned clinical/account cleanup and browser shutdown, on this explicit
  // disposable/exclusive deployment. Preserve restart failures as cleanup errors.
  s.cleanup(()=>{if(cacheCleanupRequired){restartAndWait();console.log('  EVIDENCE owned provider cache retired by post-cleanup application restart');}});
  const latch=sql.rows('SELECT id,lastUpdateUser,lastUpdateDate FROM messengerMembershipLock ORDER BY id');
  let latchClaimed=false,latchRow;
  s.cleanup(()=>{
    if(!latchClaimed)return;
    const current=sql.rows('SELECT id,lastUpdateUser,lastUpdateDate FROM messengerMembershipLock ORDER BY id');
    if(latch.length)h.assert(JSON.stringify(current)===JSON.stringify(latch),'Pre-existing Messenger coordinator changed');
    else {
      if(!latchRow)latchRow=current.filter(row=>row[0]==='1'&&row[1]===marker);
      if(!current.length&&!latchRow.length)return;
      h.assert(latchRow.length===1&&JSON.stringify(current)===JSON.stringify(latchRow),'Owned Messenger coordinator changed; retain it');
      sql.execute(`DELETE FROM messengerMembershipLock WHERE id=1 AND lastUpdateUser=${q(marker)} AND lastUpdateDate=${q(latchRow[0][2])}`);
      h.assert(sql.value('SELECT COUNT(*) FROM messengerMembershipLock')==='0','Owned Messenger coordinator remains');
    }
  });
  if(!latch.length) {
    // Claim before INSERT; cleanup can recover an uncertain acknowledgement.
    latchClaimed=true;
    try {sql.execute(`INSERT INTO messengerMembershipLock(id,lastUpdateUser) VALUES(1,${q(marker)})`);}
    finally {latchRow=sql.rows(`SELECT id,lastUpdateUser,lastUpdateDate FROM messengerMembershipLock WHERE id=1 AND lastUpdateUser=${q(marker)}`);}
    h.assert(latchRow.length===1,'Messenger coordinator ownership was not established');
  }else latchClaimed=true;
  const actors=[];
  let namespaceClaimed=false;
  for(let index=0;index<3;index++) {
    const actorMarker=`FAKE-PW${randomBytes(8).toString('hex')}`;
    const fixture=throwawayLoginFixture({sql,marker:actorMarker,provider:s.provider,testUser:config.testUser});
    s.cleanup(()=>{
      if(namespaceClaimed)h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM messagetbl WHERE sentbyNo=${q(fixture.providerNo)})+
        (SELECT COUNT(*) FROM messagelisttbl WHERE provider_no=${q(fixture.providerNo)})+(SELECT COUNT(*) FROM groupMembers_tbl WHERE provider_No=${q(fixture.providerNo)})+
        (SELECT COUNT(*) FROM tickler WHERE creator=${q(fixture.providerNo)} OR task_assigned_to=${q(fixture.providerNo)})+
        (SELECT COUNT(*) FROM demographic WHERE provider_no=${q(fixture.providerNo)})+
        (SELECT COUNT(*) FROM providerArchive WHERE provider_no=${q(fixture.providerNo)})+
        (SELECT COUNT(*) FROM providerLabRouting WHERE provider_no=${q(fixture.providerNo)})`)==='0',
      'Recall account retained while dependent fixture rows remain');
      fixture.cleanup();
    });
    fixture.create({roleNames:index===0?['admin','doctor']:['doctor']});actors.push({fixture,marker:actorMarker});
  }
  const [sender,delegate,control]=actors;
  /** Refuse provider-keyed child cleanup if any fixture identity has been replaced. */
  function assertActorOwners() {
    for(const actor of actors) {
      const no=q(actor.fixture.providerNo);
      h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${no} AND last_name=${q(actor.marker)}`)==='1',
        'Recall provider ownership changed; retaining provider-keyed children');
      h.assert(sql.value(`SELECT COUNT(*) FROM security WHERE provider_no=${no}`)==='1'&&
        sql.value(`SELECT COUNT(*) FROM security WHERE provider_no=${no} AND user_name=${q(actor.fixture.username)}`)==='1',
        'Recall login ownership changed; retaining provider-keyed children');
    }
  }
  const providers=actors.map(actor=>q(actor.fixture.providerNo)).join(',');
  const subject=marker+' recall: please book a follow-up',body=marker+' delegate follow-up body';
  h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM messagetbl WHERE sentbyNo IN (${providers}) OR thesubject=${q(subject)})+
    (SELECT COUNT(*) FROM messagelisttbl WHERE provider_no IN (${providers}))+(SELECT COUNT(*) FROM groupMembers_tbl WHERE provider_No IN (${providers}))+
    (SELECT COUNT(*) FROM tickler WHERE creator IN (${providers}) OR task_assigned_to IN (${providers}))+
    (SELECT COUNT(*) FROM demographic WHERE provider_no IN (${providers}))+
    (SELECT COUNT(*) FROM providerArchive WHERE provider_no IN (${providers}))+
    (SELECT COUNT(*) FROM providerLabRouting WHERE provider_no IN (${providers}))`)==='0','Recall account/message namespace collision');
  const messageIds=()=>sql.rows(`SELECT messageid FROM messagetbl WHERE thesubject=${q(subject)}`).flat();
  s.cleanup(()=>{
    if(!namespaceClaimed)return;
    assertActorOwners();
    const ids=messageIds();
    h.assert(ids.length<=1&&ids.every(id=>/^[1-9]\d*$/.test(id)),'Ambiguous recall message identity');
    for(const id of ids) {
      h.assert(sql.value(`SELECT COUNT(*) FROM messagetbl WHERE messageid=${id} AND sentbyNo=${q(sender.fixture.providerNo)} AND LOCATE(${q(body)},themessage)>0`)==='1','Recall message ownership changed');
      h.assert(sql.value(`SELECT COUNT(*) FROM messagelisttbl WHERE message=${id} AND provider_no<>${q(delegate.fixture.providerNo)}`)==='0','Recall message gained an unintended recipient; retaining it');
      h.assert(sql.value(`SELECT COUNT(*) FROM msgDemoMap WHERE messageID=${id} AND demographic_no<>${patient}`)==='0','Recall message gained another patient; retaining it');
      sql.execute(`DELETE FROM msgDemoMap WHERE messageID=${id}; DELETE FROM messagelisttbl WHERE message=${id};
        DELETE FROM messagetbl WHERE messageid=${id} AND thesubject=${q(subject)} AND sentbyNo=${q(sender.fixture.providerNo)}`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM messagetbl WHERE messageid=${id})+(SELECT COUNT(*) FROM messagelisttbl WHERE message=${id})+
        (SELECT COUNT(*) FROM msgDemoMap WHERE messageID=${id})`)==='0','Recall message/delivery/patient map remains');
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM groupMembers_tbl WHERE provider_No IN (${providers}) AND (groupID<>0 OR facilityId<>0)`)==='0','Owned contact acquired a foreign membership');
    sql.execute(`DELETE FROM groupMembers_tbl WHERE provider_No IN (${providers}) AND groupID=0 AND facilityId=0`);
    h.assert(messageIds().length===0&&sql.value(`SELECT COUNT(*) FROM groupMembers_tbl WHERE provider_No IN (${providers})`)==='0','Owned recall message/contact residue remains');
    for(const actor of actors) {
      const no=q(actor.fixture.providerNo);
      h.assert(sql.value(`SELECT COUNT(*) FROM providerArchive WHERE provider_no=${no} AND (last_name<>${q(actor.marker)} OR last_name IS NULL)`)==='0','Provider archive ownership changed; retain it');
      sql.execute(`DELETE FROM providerArchive WHERE provider_no=${no} AND last_name=${q(actor.marker)}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM providerArchive WHERE provider_no=${no}`)==='0','Owned provider archive remains');
    }
  });
  namespaceClaimed=true;
  const site=sql.value('SELECT site_id FROM site WHERE status=1 ORDER BY site_id LIMIT 1');
  if(!/^[1-9]\d*$/.test(site))throw new h.SkipCheck('Recall delivery requires an active configured clinic');
  sql.execute(`INSERT INTO providersite(provider_no,site_id) VALUES ${actors.map(actor=>`(${q(actor.fixture.providerNo)},${site})`).join(',')}`);
  sql.execute(`INSERT INTO groupMembers_tbl(groupID,provider_No,facilityId) VALUES ${actors.map(actor=>`(0,${q(actor.fixture.providerNo)},0)`).join(',')}`);
  for(const actor of actors) {
    const context=await h.newContext(s.context.browser(),config);s.cleanup(()=>context.close());
    context.setDefaultTimeout(30000);context.on('page',page=>h.wireStrictPage(page,NAME,recorder));
    actor.context=context;actor.schedule=await h.login(context,{...config,testUser:actor.fixture.username},recorder);
  }
  const popup=(page,locator,label)=>ui.clickOpensPopup(page,locator,{context:sender.context,recorder,label});
  await s.step('owned provider setup refreshes the application active-provider cache through a native profile save',async()=>{
    cacheCleanupRequired=true;
    await saveOwnedProviderProfile({...s,context:sender.context,schedule:sender.schedule},sender);
  });
  const findings=[];
  const preference=`provider_no=${q(sender.fixture.providerNo)} AND name='tickler_task_assignee'`;
  h.assert(sql.value(`SELECT COUNT(*) FROM property WHERE ${preference}`)==='0','Fresh sender already has a generic tickler preference');
  s.cleanup(()=>{
    assertActorOwners();
    h.assert(sql.value(`SELECT COUNT(*) FROM property WHERE ${preference} AND (value<>'mrp' OR value IS NULL)`)==='0','Owned generic tickler preference changed; retain it');
    sql.execute(`DELETE FROM property WHERE ${preference} AND value='mrp'`);
    h.assert(sql.value(`SELECT COUNT(*) FROM property WHERE ${preference}`)==='0','Owned generic tickler preference remains');
  });
  const originalMrp=sql.value(`SELECT provider_no FROM demographic WHERE demographic_no=${patient}`);
  s.cleanup(()=>{
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient} AND last_name=${q(marker)} AND provider_no IN (${q(sender.fixture.providerNo)},${q(originalMrp)})`)==='1','Owned patient MRP changed; retaining it');
    sql.execute(`UPDATE demographic SET provider_no=${q(originalMrp)} WHERE demographic_no=${patient} AND last_name=${q(marker)}`);
  });
  sql.execute(`UPDATE demographic SET provider_no=${q(sender.fixture.providerNo)} WHERE demographic_no=${patient} AND last_name=${q(marker)}`);
  let messageId;
  const receipt=await recallWorkflow({...s,provider:sender.fixture.providerNo,context:sender.context,schedule:sender.schedule,popup},
    {delegateIdentity:sql.rows(`SELECT provider_no,last_name,first_name FROM provider WHERE provider_no=${q(delegate.fixture.providerNo)}`)[0],
      async beforeRecall({lab,delegate:expected}) {
        const pages=[],collect=page=>pages.push(page);
        sender.context.on('page',collect);
        try {
          await lab.locator('input[type="button"][value="Recall"]').click();
          const deadline=Date.now()+30000;
          while(pages.length<2&&Date.now()<deadline)await new Promise(resolve=>setTimeout(resolve,100));
        }finally{sender.context.off('page',collect);}
        h.assert(pages.length===2,'Fresh-sender Recall did not open its two native windows');
        await Promise.all(pages.map(page=>page.waitForURL(url=>String(url)!=='about:blank',{waitUntil:'load'})));
        const form=pages.find(page=>new URL(page.url()).pathname.endsWith('/tickler/ForwardDemographicTickler'));
        h.assert(form,'Fresh-sender Recall has no companion task form');
        const requested=new URL(form.url()).searchParams.get('taskTo');
        const actual=await form.locator('select[name="task_assigned_to"]').inputValue();
        const options=await form.locator('select[name="task_assigned_to"] option').count();
        h.assert(await form.locator('select[name="task_assigned_to"] option').evaluateAll((nodes,no)=>nodes.some(node=>node.value===no),expected),
          'Fixture delegate is absent from the deployed active-provider chooser');
        if(requested!==expected||actual!==expected) {
          const error=h.markFailedStep(new Error(`Fresh-sender Recall lost its explicit assignee: taskTo=${requested}, selected=${JSON.stringify(actual)}, options=${options}, genericPreferenceRows=0, activeClinicMembership=1`),'fresh sender explicit recall assignee');
          findings.push(error);console.error('  FAIL '+NAME+': '+error.message);
        }else console.log('  PASS '+NAME+': fresh sender explicit recall assignee');
        await Promise.all(pages.map(page=>page.close()));
        h.assert(messageIds().length===0&&sql.value(`SELECT COUNT(*) FROM tickler WHERE demographic_no=${patient}`)==='0','Fresh-sender probe wrote a message or task');
        const prefs=await popup(sender.schedule,sender.schedule.getByTitle(/Edit your personal setting/i).first(),'recall-owned-preferences');
        const link=prefs.locator('a[href*="method=viewTicklerTaskAssignee"]').first();await revealAuditLink(prefs,link,20000);
        const settings=await popup(prefs,link,'recall-owned-MRP-preference');
        await settings.locator('#taskAssigneeMRP').check();
        await ui.clickAndAwaitReload(settings,settings.locator('form input[type="submit"]'));
        await settings.locator('#AlertBanner').waitFor({state:'visible'});
        h.assert(sql.value(`SELECT COUNT(*) FROM property WHERE ${preference} AND value='mrp'`)==='1','Owned MRP preference was not saved');
        await settings.close();await prefs.close();
      },
      async sendRecall({message,subject:actualSubject}) {
        h.assert(actualSubject===subject,'Recall composer has the wrong configured subject');
        const selected=await message.locator('input[name="provider"]:checked').evaluateAll(nodes=>nodes.map(node=>node.value));
        const localChoices=new Set([delegate.fixture.providerNo,delegate.fixture.providerNo+'-0',delegate.fixture.providerNo+'-0-0']);
        h.assert(selected.length>0&&selected.every(value=>localChoices.has(value)),'Recall composer selected an unintended or remote recipient');
        h.assert(await message.locator('input[name="demographic_no"]').inputValue()===patient,'Recall composer has another patient');
        await message.locator('.toastui-editor-ww-container .ProseMirror').first().fill(body);
        await clickLoad(message,message.locator('button[type="submit"]',{hasText:/Send Message/i}).first());
        const rows=sql.rows(`SELECT messageid,sentbyNo,themessage FROM messagetbl WHERE thesubject=${q(subject)}`);
        h.assert(rows.length===1&&rows[0][1]===sender.fixture.providerNo&&rows[0][2].includes(body),'Recall did not create one correctly attributed message/body');
        messageId=rows[0][0];h.assert(/^[1-9]\d*$/.test(messageId),'Recall message has no valid identity');
        h.assert(JSON.stringify(sql.rows(`SELECT provider_no,status FROM messagelisttbl WHERE message=${messageId} ORDER BY id`))===JSON.stringify([[delegate.fixture.providerNo,'new']]),
          'Recall duplicated or misrouted its recipient delivery');
        h.assert(JSON.stringify(sql.rows(`SELECT demographic_no FROM msgDemoMap WHERE messageID=${messageId} ORDER BY id`))===JSON.stringify([[patient]]),'Recall lost or duplicated its patient association');
      }});
  const immutable=()=>JSON.stringify({message:sql.rows(`SELECT * FROM messagetbl WHERE messageid=${messageId}`),
    patient:sql.rows(`SELECT * FROM msgDemoMap WHERE messageID=${messageId} ORDER BY id`),task:sql.rows(`SELECT * FROM tickler WHERE tickler_no=${receipt.ticklerNo}`),
    docs:sql.rows(`SELECT * FROM ticklerdocs WHERE tickler_id=${receipt.ticklerNo} ORDER BY id`),
    history:sql.rows(`SELECT * FROM tickler_update WHERE tickler_no=${receipt.ticklerNo} ORDER BY id`)});
  const before=immutable();
  async function inbox(actor) {
    const result=await ui.clickOpensPopupOrNavigates(actor.schedule,actor.schedule.locator('a:has(#oscar_new_msg)').first(),
      {context:actor.context,recorder,label:'recall-recipient-inbox'});
    await result.page.waitForLoadState('load');return result.page;
  }
  await s.step('delegate receives and reopens the exact recall message with sender, subject, body and patient context; control receives nothing',async()=>{
    const page=await inbox(delegate),other=await inbox(control);
    for(let read=0;read<2;read++) {
      if(read)await clickLoad(page,page.locator(INBOX_LINK).first());
      h.assert(await page.locator(`input[name="messageNo"][value="${messageId}"]`).count()===1,'Recall is absent or duplicated in delegate inbox');
      await clickLoad(page,page.locator(`a[href*="/messenger/ViewMessage?messageID=${messageId}&"]`).first());
      h.assert((await page.locator('#msgSubject').innerText()).trim()===subject,'Delegate opened another subject');
      h.assert((await page.locator('#sentBy').innerText()).includes(sender.marker),'Delegate sees another sender');
      await page.locator('#viewer').filter({hasText:body}).waitFor({state:'visible'});
      h.assert((await page.locator('#viewer').innerText()).includes(body),'Delegate cannot read the recall body');
      h.assert(await page.locator(`input[readonly][title="${patient}"]`).count()===1,'Delegate sees a missing or wrong linked patient');
      await expectValue(sql,`SELECT status FROM messagelisttbl WHERE message=${messageId} AND provider_no=${q(delegate.fixture.providerNo)}`,'read','Delegate reading did not affect its exact delivery');
      h.assert(await other.locator(`input[name="messageNo"][value="${messageId}"]`).count()===0,'Non-recipient control received the recall');
      h.assert(immutable()===before&&messageIds().length===1,'Reading recall resent it, created a task or changed shared content');
    }
  });
  await s.step('delegate opens the assigned recall task and follows its exact lab link without creating another task or message',async()=>{
    const {page:list}=await ui.clickOpensPopupOrNavigates(delegate.schedule,delegate.schedule.locator('#oscar_new_tickler'),
      {context:delegate.context,recorder,label:'recall-delegate-task-list'});
    await waitForTicklerTable(list);
    const [filtered]=await Promise.all([list.waitForResponse(response=>new URL(response.url()).pathname.endsWith('/tickler/ListTicklers')
      &&new URL(response.url()).searchParams.get('search[value]')===marker),list.locator('#ticklerResults_filter input[type="search"]').fill(marker)]);
    h.assert(filtered.status()===200,'Delegate task search failed');
    await list.locator('#assignedTo').selectOption(delegate.fixture.providerNo);
    const [assigned]=await Promise.all([list.waitForResponse(response=>new URL(response.url()).pathname.endsWith('/tickler/ListTicklers')
      &&new URL(response.url()).searchParams.get('assignee')===delegate.fixture.providerNo),list.locator('#formSubmitBtn').click()]);
    h.assert(assigned.status()===200,'Delegate assigned-task filter failed');await waitForTicklerTable(list);
    const payload=await assigned.json();h.assert(Array.isArray(payload.data)&&payload.data.length===1&&String(payload.data[0].id)===receipt.ticklerNo,'Delegate list contains the wrong task set');
    const row=list.locator(`input[name="checkbox"][value="${receipt.ticklerNo}"]`).locator('xpath=ancestor::tr[1]');
    const editor=await ui.clickOpensPopup(list,row.locator('a[onclick*="openTicklerEdit"]'),{context:delegate.context,recorder,label:'recall-delegate-task'});
    h.assert(new URL(editor.url()).searchParams.get('tickler_no')===receipt.ticklerNo,'Delegate opened another task');
    h.assert(await editor.locator('#assignedToProviders').inputValue()===delegate.fixture.providerNo,'Delegate task editor changed assignee');
    await editor.close();
    const lab=await ui.clickOpensPopup(list,row.locator(`a[href*="/ViewLabDisplay?segmentID=${receipt.labNo}"]`),
      {context:delegate.context,recorder,label:'recall-delegate-linked-lab'});
    await lab.waitForLoadState('load');await h.assertNotErrorPage(lab,'delegate recall lab');
    h.assert(new URL(lab.url()).searchParams.get('segmentID')===receipt.labNo,'Task opened another lab identity');
    const text=await lab.locator('body').innerText();h.assert(text.includes(receipt.accession)&&text.includes(marker),'Task lab is missing its exact accession/patient fixture');
    h.assert(sql.value(`SELECT COUNT(*) FROM patientLabRouting WHERE lab_no=${receipt.labNo} AND lab_type='HL7' AND demographic_no=${patient}`)==='1','Linked lab has the wrong patient routing');
    await lab.close();
    h.assert(immutable()===before&&messageIds().length===1,'Opening task/lab changed recall content, created another task or resent the message');
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler WHERE demographic_no=${patient} AND message LIKE ${q(marker+'%')}`)==='1','Recall did not retain exactly one companion task');
    h.assert(sql.value(`SELECT COUNT(*) FROM messagelisttbl WHERE provider_no=${q(control.fixture.providerNo)}`)==='0','Control acquired an unintended message');
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler WHERE task_assigned_to=${q(control.fixture.providerNo)}`)==='0','Control acquired an unintended task');
  });
  require('./lib/workflow-findings').throwFindings(findings);
}
if(require.main===module)runWorkflow(NAME,workflow,{openMaster:false,preflightConfig:()=>{
  if(process.env.CARLOS_DISPOSABLE_HOST!=='true'||process.env.EXCLUSIVE!=='1')throw new h.SkipCheck('Requires CARLOS_DISPOSABLE_HOST=true and EXCLUSIVE=1 for isolated recall delivery');
}});
module.exports={workflow};
