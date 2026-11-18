#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
// #5728/#5729: native group expansion, then independent delivery state for the
// SAME message in separately authenticated recipient sessions. No shared login,
// real provider, group, message or patient is edited.
const h=require('./lib/playwright-harness');
const ui=require('./lib/playwright-ui');
const {runWorkflow,expectValue}=require('./lib/workflow-session');
const {throwawayLoginFixture}=require('./lib/throwaway-login-fixture');
const {openAdmin}=require('./billing-on-admin-config-playwright-checks');
const {revealAuditLink}=require('./lib/playwright-link-audit');
const {INBOX_LINK}=require('./messenger-demographic-link-playwright-checks');
const q=h.sqlString;
const TIMEOUT=30000;

/** Real navigation, observed before the click and settled at the new document. */
async function clickLoad(page,control) {
  try{await Promise.all([page.waitForNavigation({waitUntil:'load',timeout:TIMEOUT}),control.click()]);}
  catch(error){throw new Error('Messenger native navigation failed at '+String(control)+': '+error.message,{cause:error});}
  await h.assertNotErrorPage(page,'owned Messenger workflow');
}

async function workflow(s) {
  const {sql,marker,config,recorder,patient}=s;
  const latchBefore=sql.rows('SELECT id,lastUpdateUser,lastUpdateDate FROM messengerMembershipLock ORDER BY id');
  let latchOwned;
  s.cleanup(()=>{
    const after=sql.rows('SELECT id,lastUpdateUser,lastUpdateDate FROM messengerMembershipLock ORDER BY id');
    if(latchBefore.length)h.assert(JSON.stringify(after)===JSON.stringify(latchBefore),'Pre-existing membership coordinator changed; retain it');
    else if(after.length) {
      h.assert(after.length===1&&after[0][0]==='1'&&after[0][1]===marker&&(!latchOwned||JSON.stringify(after[0])===JSON.stringify(latchOwned)),'Owned membership coordinator changed; retain it');
      sql.execute(`DELETE FROM messengerMembershipLock WHERE id=1 AND lastUpdateUser=${q(marker)} AND lastUpdateDate=${q(after[0][2])}`);
      h.assert(sql.value('SELECT COUNT(*) FROM messengerMembershipLock')==='0','Membership coordinator baseline was not restored');
    }
  });
  if(!latchBefore.length) {
    sql.execute(`INSERT INTO messengerMembershipLock(id,lastUpdateUser) VALUES(1,${q(marker)})`);
    [latchOwned]=sql.rows('SELECT id,lastUpdateUser,lastUpdateDate FROM messengerMembershipLock WHERE id=1');
    h.assert(latchOwned?.[1]===marker,'Membership coordinator ownership was not established');
  }
  const users=[];
  for(let index=0;index<5;index++) {
    const name=marker.slice(0,-2)+String(index).padStart(2,'0');
    const fixture=throwawayLoginFixture({sql,marker:name,provider:s.provider,testUser:config.testUser});
    s.cleanup(()=>fixture.cleanup());fixture.create({roleNames:['doctor']});users.push({fixture,name});
  }
  const [sender,a,b,c,removed]=users;
  const providers=users.map(user=>q(user.fixture.providerNo)).join(',');
  const groupNames=[marker+' Group A',marker+' Group B'];
  const messages=()=>sql.rows(`SELECT messageid FROM messagetbl WHERE thesubject LIKE ${q(marker+' %')}`).flat();
  // All intents are registered before native writes; recover IDs by exact names.
  s.cleanup(()=>{
    const failures=[];
    try {
      const ids=messages();h.assert(ids.every(id=>/^[1-9]\d*$/.test(id)),'Invalid owned message identity');
      if(ids.length) {
        const list=ids.join(',');
        sql.execute(`DELETE FROM msgDemoMap WHERE messageID IN (${list}); DELETE FROM messagelisttbl WHERE message IN (${list}); DELETE FROM messagetbl WHERE messageid IN (${list}) AND thesubject LIKE ${q(marker+' %')}`);
        h.assert(messages().length===0&&sql.value(`SELECT (SELECT COUNT(*) FROM messagelisttbl WHERE message IN (${list}))+(SELECT COUNT(*) FROM msgDemoMap WHERE messageID IN (${list}))`)==='0','Owned messages or deliveries remain');
      }
    }catch(error){failures.push(error);}
    for(const name of groupNames)try {
      const ids=sql.rows(`SELECT groupID FROM groups_tbl WHERE groupDesc=${q(name)}`).flat();
      h.assert(ids.length<=1&&ids.every(id=>/^[1-9]\d*$/.test(id)),'Ambiguous owned Messenger group');
      for(const id of ids) {
        h.assert(sql.value(`SELECT COUNT(*) FROM groupMembers_tbl WHERE groupID=${id} AND provider_No NOT IN (${providers})`)==='0','Owned group gained a foreign member; retain that group');
        sql.execute(`DELETE FROM groupMembers_tbl WHERE groupID=${id}; DELETE FROM groups_tbl WHERE groupID=${id} AND groupDesc=${q(name)}`);
        h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM groups_tbl WHERE groupID=${id})+(SELECT COUNT(*) FROM groupMembers_tbl WHERE groupID=${id})`)==='0','Owned group cleanup failed');
      }
    }catch(error){failures.push(error);}
    try {
      sql.execute(`DELETE FROM groupMembers_tbl WHERE provider_No IN (${providers})`);
      h.assert(sql.value(`SELECT COUNT(*) FROM groupMembers_tbl WHERE provider_No IN (${providers})`)==='0','Owned contact registrations remain');
    }catch(error){failures.push(error);}
    if(failures.length)throw new AggregateError(failures,'Messenger cleanup failed: '+failures.map(error=>error.message).join('; '));
  });
  h.assert(sql.value(`SELECT COUNT(*) FROM groupMembers_tbl WHERE provider_No IN (${providers})`)==='0','Owned providers have pre-existing Messenger memberships');
  const unrelated=()=>JSON.stringify({groups:sql.rows(`SELECT * FROM groups_tbl WHERE COALESCE(groupDesc,'') NOT IN (${groupNames.map(q).join(',')}) ORDER BY groupID`),
    members:sql.rows(`SELECT * FROM groupMembers_tbl WHERE provider_No NOT IN (${providers}) ORDER BY id`)});
  const beforeUnrelated=unrelated();
  const clinical=()=>JSON.stringify({patient:sql.rows(`SELECT * FROM demographic WHERE demographic_no=${patient}`),
    ext:sql.rows(`SELECT * FROM demographicExt WHERE demographic_no=${patient} ORDER BY id`)});
  const beforeClinical=clinical();
  const admin=await openAdmin(s);
  const adminLink=admin.locator('a.contentLink[href$="/messenger?method=fetch"]');
  await revealAuditLink(admin,adminLink,TIMEOUT);
  await ui.clickInjectsPanel(admin,adminLink,{marker:'#local-contacts',timeout:TIMEOUT});
  const idle=()=>admin.waitForFunction(()=>!window.jQuery||window.jQuery.active===0,null,{timeout:TIMEOUT});
  async function mutate(control,method) {
    const [response]=await Promise.all([admin.waitForResponse(r=>r.request().method()==='POST'
      &&new URL(r.url()).pathname.endsWith('/messenger')&&new URL(r.url()).searchParams.get('method')===method,{timeout:TIMEOUT}),control.click()]);
    h.assert(response.status()===200,'Native group/contact mutation did not succeed');await idle();
  }
  async function add(group,user) {
    await admin.locator('a.nav-link[href="#manageGroups"]').click();
    await admin.locator(`a.nav-link[href="#group-${group}"]`).click();
    const id=await ui.typeAutocomplete(admin,admin.locator(`input.search-provider[id="${group}"]`),user.name,{option:user.name,hidden:`#add-member-id-${group}`,timeout:TIMEOUT});
    h.assert(id.startsWith(user.fixture.providerNo+'-'),'Autocomplete chose another group member');
    await mutate(admin.locator(`#add-${group}`),'add');
    await expectValue(sql,`SELECT COUNT(*) FROM groupMembers_tbl WHERE groupID=${group} AND provider_No=${q(user.fixture.providerNo)}`,'1','Native group add did not create one exact membership');
  }
  async function remove(group,user) {
    await admin.locator('a.nav-link[href="#manageGroups"]').click();await admin.locator(`a.nav-link[href="#group-${group}"]`).click();
    const entry=admin.locator(`#group-member-list-${group} [data-member-key^="${user.fixture.providerNo}-"]`);
    h.assert(await entry.count()===1,'Native group removal has ambiguous member identity');
    await mutate(entry.locator('xpath=..').locator(`i.group-member[data-group-id="${group}"][data-member-id^="${user.fixture.providerNo}-"]`),'remove');
    await expectValue(sql,`SELECT COUNT(*) FROM groupMembers_tbl WHERE groupID=${group} AND provider_No=${q(user.fixture.providerNo)}`,'0','Native group remove retained the membership');
  }
  const groups=[];
  await s.step('Administration creates overlapping groups and removes the control member through native controls',async()=>{
    for(const name of groupNames) {
      await admin.locator('a.nav-link[href="#manageGroups"]').click();await admin.locator('a.nav-link[href="#new-group"]').click();
      await admin.locator('#new-group-name').fill(name);
      await Promise.all([admin.waitForResponse(r=>r.request().method()==='GET'&&new URL(r.url()).pathname.endsWith('/messenger')&&new URL(r.url()).searchParams.get('method')==='fetch',{timeout:TIMEOUT}),admin.locator('#add-group-btn').click()]);
      await idle();const id=sql.value(`SELECT GROUP_CONCAT(groupID) FROM groups_tbl WHERE groupDesc=${q(name)}`);
      h.assert(/^[1-9]\d*$/.test(id),'Native group creation did not produce one owned ID');groups.push(id);
    }
    await add(groups[0],a);await add(groups[0],b);await add(groups[0],removed);await remove(groups[0],removed);
    await add(groups[1],b);await add(groups[1],c);
    h.assert(unrelated()===beforeUnrelated,'Group administration rewrote unrelated groups or memberships');
  });
  for(const user of [sender,a,b,c]) {
    const context=await h.newContext(s.context.browser(),config);context.setDefaultTimeout(TIMEOUT);
    context.on('page',page=>h.wireStrictPage(page,'messenger-owned-session',recorder));
    const schedule=await h.login(context,{...config,testUser:user.fixture.username},recorder,{label:'messenger-owned-login'});
    const {page}=await ui.clickOpensPopupOrNavigates(schedule,schedule.locator('a:has(#oscar_new_msg)').first(),{context,recorder,label:'messenger-owned-inbox',timeout:TIMEOUT});
    await page.waitForLoadState('load');user.context=context;user.page=page;
  }
  const toBox=async(user,box)=>clickLoad(user.page,user.page.locator(box==='0'?INBOX_LINK:`a[href*="/messenger/DisplayMessages?boxType=${box}"]:visible`).first());
  const membership=()=>JSON.stringify(sql.rows(`SELECT * FROM groupMembers_tbl WHERE groupID IN (${groups.join(',')}) ORDER BY id`));
  async function send(subject,body,targets) {
    const page=sender.page;await toBox(sender,'0');await clickLoad(page,page.locator('a[href*="/messenger/ViewCreateMessage"]').first());
    for(const id of groups)await page.locator(`#member_group_${id}`).check();
    await page.locator(`input[name="provider"][id^="0-"][value^="${b.fixture.providerNo}-"]`).check();
    h.assert(!await page.locator(`input[name="provider"][id^="0-"][value^="${removed.fixture.providerNo}-"]`).isChecked(),'Removed group member was selected for delivery');
    const checked=await page.locator('input[name="provider"]:checked').evaluateAll(nodes=>nodes.map(node=>node.value.split('-')[0]));
    h.assert(checked.filter(id=>id===b.fixture.providerNo).length===3,'Overlapping groups and direct recipient did not exercise three selections of one clinician');
    h.assert(JSON.stringify([...new Set(checked)].sort())===JSON.stringify(targets.map(user=>user.fixture.providerNo).sort()),'Composer selected an unintended recipient');
    await page.locator('#keyword').fill(marker);
    const [search]=await Promise.all([sender.context.waitForEvent('page',{timeout:TIMEOUT}),page.locator('input[name="searchDemo"]').click()]);
    const pick=search.locator(`#patientResults a[onclick*="selectForMessenger"][onclick*="'${patient}'"]`);await pick.waitFor();
    h.assert(await search.locator('#patientResults a[onclick*="selectForMessenger"]').count()===1,'Composer patient search did not select the sole owned patient');
    await Promise.all([search.waitForEvent('close'),pick.click({noWaitAfter:true}).catch(error=>{if(!search.isClosed()||!/Target page, context or browser has been closed/.test(error.message))throw error;})]);
    h.assert(await page.locator('input[name="demographic_no"]').inputValue()===patient,'Composer linked another patient');
    await page.locator('#subject').fill(subject);await page.locator('.toastui-editor-ww-container .ProseMirror').first().fill(body);
    const priorMembership=membership();
    await clickLoad(page,page.locator('button[type="submit"]',{hasText:/Send Message/i}));
    h.assert(membership()===priorMembership,'Send silently rewrote group memberships');
    const rows=sql.rows(`SELECT messageid,sentbyNo,themessage FROM messagetbl WHERE thesubject=${q(subject)}`);
    h.assert(rows.length===1&&rows[0][1]===sender.fixture.providerNo&&rows[0][2].includes(body),'Send did not store one correctly attributed message and body');
    const id=rows[0][0];h.assert(/^[1-9]\d*$/.test(id),'Invalid sent message ID');
    const deliveries=sql.rows(`SELECT id,provider_no,status FROM messagelisttbl WHERE message=${id} ORDER BY provider_no,id`);
    h.assert(deliveries.length===targets.length&&JSON.stringify(deliveries.map(row=>row[1]).sort())===JSON.stringify(targets.map(user=>user.fixture.providerNo).sort())&&deliveries.every(row=>row[2]==='new'),'Send duplicated or misattributed delivery rows');
    h.assert(sql.value(`SELECT GROUP_CONCAT(demographic_no ORDER BY id) FROM msgDemoMap WHERE messageID=${id}`)===patient,'Send lost or duplicated its patient link');
    h.assert(unrelated()===beforeUnrelated&&clinical()===beforeClinical,'Send changed unrelated groups or clinical data');
    return {id,subject,body,deliveries};
  }
  let original,control;
  const snapshot=id=>JSON.stringify({content:sql.rows(`SELECT * FROM messagetbl WHERE messageid=${id}`),patient:sql.rows(`SELECT * FROM msgDemoMap WHERE messageID=${id} ORDER BY id`)});
  const row=(user,id)=>user.page.locator(`input[name="messageNo"][value="${id}"]`).locator('xpath=ancestor::tr[1]');
  async function view(user,message,status,box='0') {
    await toBox(user,box);h.assert(await row(user,message.id).count()===1,'Exact message is missing or duplicated in the expected recipient box');
    const rendered=(await row(user,message.id).locator('td').nth(1).innerText()).trim();
    h.assert(rendered===(status==='del'?'deleted':status),'Recipient inbox renders another delivery status');
  }
  await s.step('one native send to both groups and their directly selected overlap delivers exactly once to each intended recipient',async()=>{
    original=await send(marker+' PRIMARY',marker+' original body Māori & "quoted"',[a,b,c]);
    for(const user of [a,b,c])await view(user,original,'new');
    await toBox(sender,'0');await toBox(sender,'1');h.assert(await sender.page.locator(`a[href*="/messenger/ViewMessage?messageID=${original.id}&"]`).count()===1,'Sender Sent view lost or duplicated its copy');
  });
  const originalDeliveries=JSON.stringify(sql.rows(`SELECT * FROM messagelisttbl WHERE message=${original.id} ORDER BY id`));
  await s.step('native membership changes affect only a later send and legacy duplicates are neither delivered twice nor rewritten',async()=>{
    await remove(groups[1],c);
    // Legacy duplicate membership is SQL setup; the supported native Add rejects
    // duplicates. The existing group-admin regression remains independently intact.
    sql.execute(`INSERT INTO groupMembers_tbl(groupID,provider_No,facilityId) VALUES(${groups[0]},${q(b.fixture.providerNo)},0)`);
    control=await send(marker+' CONTROL',marker+' untouched second message',[a,b]);
    h.assert(JSON.stringify(sql.rows(`SELECT * FROM messagelisttbl WHERE message=${original.id} ORDER BY id`))===originalDeliveries,'Later membership changes rewrote the prior message deliveries');
    for(const user of [a,b])await view(user,control,'new');
    await toBox(c,'0');h.assert(await row(c,control.id).count()===0,'Removed group member received the later message');
  });
  const immutable=snapshot(original.id),controlState=JSON.stringify({shared:snapshot(control.id),deliveries:sql.rows(`SELECT * FROM messagelisttbl WHERE message=${control.id} ORDER BY id`)});
  const delivery=user=>sql.rows(`SELECT * FROM messagelisttbl WHERE id=${original.deliveries.find(item=>item[1]===user.fixture.providerNo)[0]} AND message=${original.id} AND provider_no=${q(user.fixture.providerNo)}`);
  function unchangedOther(user,expected) {
    h.assert(JSON.stringify(delivery(user))===expected,'Recipient action changed another exact delivery row');
    h.assert(snapshot(original.id)===immutable,'Recipient action changed shared body, sender, attachments or patient link');
    h.assert(JSON.stringify({shared:snapshot(control.id),deliveries:sql.rows(`SELECT * FROM messagelisttbl WHERE message=${control.id} ORDER BY id`)})===controlState,'Recipient action changed the control message');
  }
  async function open(user) {
    await view(user,original,'new');
    await clickLoad(user.page,user.page.locator(`a[href*="/messenger/ViewMessage?messageID=${original.id}&"]`).first());
    h.assert((await user.page.locator('#msgSubject').innerText()).trim()===original.subject,'Recipient opened another subject');
    await expectValue(sql,`SELECT status FROM messagelisttbl WHERE message=${original.id} AND provider_no=${q(user.fixture.providerNo)}`,'read','Opening did not mark the exact recipient delivery read');
  }
  async function bulk(user,button,from,status) {
    await toBox(user,from);const checkbox=user.page.locator(`input[name="messageNo"][value="${original.id}"]`);await checkbox.check();
    const selected=await user.page.locator('input[name="messageNo"]:checked').inputValue();h.assert(selected===original.id&&await user.page.locator('input[name="messageNo"]:checked').count()===1,'Bulk action selected another message');
    await clickLoad(user.page,user.page.locator(`button[name="${button}"]`).first());
    await expectValue(sql,`SELECT status FROM messagelisttbl WHERE message=${original.id} AND provider_no=${q(user.fixture.providerNo)}`,status,'Bulk action changed the wrong delivery state');
  }
  await s.step('A read, unread, archive and restore preserve B delivery and the shared message and second-message control',async()=>{
    const beforeB=JSON.stringify(delivery(b));await open(a);unchangedOther(b,beforeB);await view(a,original,'read');await view(b,original,'new');
    for(const [button,from,status] of [['btnUnread','0','new'],['btnDelete','0','del'],['btnUnarchive','2','read']]) {
      await bulk(a,button,from,status);unchangedOther(b,beforeB);await view(a,original,status,status==='del'?'2':'0');await view(b,original,'new');
    }
  });
  await s.step('B read and archive remain independent while A stays logged in and sender Sent retains immutable content',async()=>{
    const beforeA=JSON.stringify(delivery(a));await open(b);unchangedOther(a,beforeA);await view(a,original,'read');
    await bulk(b,'btnDelete','0','del');unchangedOther(a,beforeA);await view(b,original,'del','2');await view(a,original,'read');await view(c,original,'new');
    await toBox(sender,'0');await toBox(sender,'1');await clickLoad(sender.page,sender.page.locator(`a[href*="/messenger/ViewMessage?messageID=${original.id}&"]`).first());
    h.assert((await sender.page.locator('#msgSubject').innerText()).trim()===original.subject,'Sender Sent copy is no longer available');
    unchangedOther(a,beforeA);h.assert(unrelated()===beforeUnrelated&&clinical()===beforeClinical,'Delivery lifecycle changed unrelated or clinical state');
  });
}
if(require.main===module)runWorkflow('messenger-recipient-isolation',workflow,{openMaster:false,preflightConfig:()=>{
  if(process.env.EXCLUSIVE!=='1'||process.env.CARLOS_DISPOSABLE_HOST!=='true')throw new h.SkipCheck('Requires EXCLUSIVE=1 and CARLOS_DISPOSABLE_HOST=true for verified membership-coordinator restoration');
}});
module.exports={workflow};
