#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
// #5732: one native-created directory contact, two independently saved patient
// associations, shared-detail editing and patient-scoped flags/removal.
const h=require('./lib/playwright-harness');
const ui=require('./lib/playwright-ui');
const {runWorkflow,expectValue}=require('./lib/workflow-session');
const {masterRecordLink}=require('./lib/master-record-link');
const {openMasterRecord}=require('./master-record-tabs-playwright-checks');
const {workflow:createDirectory}=require('./contact-editor-playwright-checks');
const q=h.sqlString;
/** Drain asynchronous audit writes for a verified owned patient after browser shutdown. */
async function removePatientAudit(sql,patient,{quietMs=1000,timeoutMs=10000,intervalMs=100}={}) {
  h.assert(/^[1-9]\d*$/.test(patient),'Invalid patient audit cleanup identity');
  const deadline=Date.now()+timeoutMs;let quietSince;
  while(Date.now()<=deadline) {
    const before=sql.value(`SELECT COUNT(*) FROM log WHERE demographic_no=${patient}`);
    h.assert(/^\d+$/.test(before),'Owned patient audit count is unreadable');
    if(before!=='0'){sql.execute(`DELETE FROM log WHERE demographic_no=${patient}`);quietSince=undefined;}
    const after=sql.value(`SELECT COUNT(*) FROM log WHERE demographic_no=${patient}`);
    h.assert(/^\d+$/.test(after),'Owned patient audit count is unreadable');
    if(before==='0'&&after==='0'){
      quietSince??=Date.now();if(Date.now()-quietSince>=quietMs)return;
    } else quietSince=undefined;
    await new Promise(resolve=>setTimeout(resolve,intervalMs));
  }
  throw new Error('Owned patient audit writes did not settle; retain patient for recovery');
}

/** Reconcile incomplete inserts, preserving ownership and independent cleanup. */
async function cleanupSharedFixtures(sql,{patient,marker,ownedPatients,control,auditTiming}) {
  const failures=[];
  for(const p of [{id:patient,name:marker,primary:true},...ownedPatients])try {
    if(!p.id) {
      const ids=sql.rows(`SELECT demographic_no FROM demographic WHERE last_name=${q(p.name)} AND first_name='Shared'`).flat();
      h.assert(ids.length<=1,'Ambiguous shared-contact patient setup');
      if(!ids.length)continue;
      [p.id]=ids;
    }
    h.assert(/^[1-9]\d*$/.test(p.id),'Invalid shared-contact patient identity');
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${p.id} AND last_name=${q(p.name)}`)==='1','Shared-contact patient ownership changed');
    sql.execute(`DELETE FROM DemographicContact WHERE demographicNo=${p.id}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM DemographicContact WHERE demographicNo=${p.id}`)==='0','Owned patient associations remain');
    // The shared harness owns the primary parent and deletes it only after all
    // child cleanups succeed. Its deliberate retention on any failure remains.
    if(!p.primary) {
      await removePatientAudit(sql,p.id,auditTiming);
      sql.execute(`DELETE FROM demographicExt WHERE demographic_no=${p.id}; DELETE FROM demographicArchive WHERE demographic_no=${p.id}; DELETE FROM demographic WHERE demographic_no=${p.id} AND last_name=${q(p.name)}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${p.id}`)==='0','Owned patient cleanup failed');
      h.assert(sql.value(`SELECT COUNT(*) FROM log WHERE demographic_no=${p.id}`)==='0','Secondary patient audit residue remains');
    }
  } catch(error){failures.push(error);}
  try {
    if(!control) {
      const ids=sql.rows(`SELECT id FROM Contact WHERE lastName=${q(marker+' Control')} AND firstName='Unrelated' AND type='Contact'`).flat();
      h.assert(ids.length<=1,'Ambiguous control-contact setup');[control]=ids;
    }
    if(control) {
      h.assert(/^[1-9]\d*$/.test(control),'Invalid control contact identity');
      h.assert(sql.value(`SELECT COUNT(*) FROM Contact WHERE id=${control} AND lastName=${q(marker+' Control')}`)==='1','Control contact ownership changed');
      sql.execute(`DELETE FROM Contact WHERE id=${control} AND lastName=${q(marker+' Control')}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM Contact WHERE id=${control}`)==='0','Control contact cleanup failed');
    }
  } catch(error){failures.push(error);}
  if(failures.length)throw new AggregateError(failures,'Shared-contact cleanup failed: '+failures.map(error=>error.message).join('; '));
}

async function workflow(s) {
  const {sql,marker,patient,provider}=s;
  const originalPages=new Set(s.context.pages());
  await createDirectory(s);
  for(const page of s.context.pages())if(!originalPages.has(page))await page.close();
  // Search is a named window: close the original before opening patient B.
  if(s.search&&s.search!==s.schedule&&!s.search.isClosed())await s.search.close();
  const name=`${marker} Contact`;
  const contact=sql.value(`SELECT id FROM Contact WHERE lastName=${q(name)}`);
  h.assert(/^[1-9]\d*$/.test(contact),'Native contact creation did not retain one exact contact');
  const ownedPatients=[];
  let control;
  s.cleanup(()=>cleanupSharedFixtures(sql,{patient,marker,ownedPatients,control}));
  for(const suffix of ['B','C']) {
    const p={name:`${marker}-${suffix}`};ownedPatients.push(p);
    p.id=sql.value(`INSERT INTO demographic(last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,provider_no,hc_type,province,roster_status,lastUpdateDate)
      VALUES(${q(p.name)},'Shared','1980','01','02','F','AC',${q(provider)},'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(p.id),'Owned secondary patient was not created');
  }
  const [b,c]=ownedPatients;
  control=sql.value(`INSERT INTO Contact(type,lastName,firstName,deleted) VALUES('Contact',${q(marker+' Control')},'Unrelated',0); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(control),'Control contact was not created');
  sql.execute(`INSERT INTO DemographicContact(demographicNo,contactId,type,category,role,sdm,ec,consentToContact,active,note,deleted)
    VALUES(${c.id},${control},2,'personal','Guardian','','',1,1,${q(marker+' CONTROL')},0)`);
  const controlState=()=>JSON.stringify({patient:sql.rows(`SELECT * FROM demographic WHERE demographic_no=${c.id}`),
    contact:sql.rows(`SELECT * FROM Contact WHERE id=${control}`),link:sql.rows(`SELECT * FROM DemographicContact WHERE demographicNo=${c.id} ORDER BY id`)});
  const unchanged=controlState();
  // The application's named Master Record popup is reused within one context.
  // Keep A and B in separate real sessions so opening B cannot replace A.
  const contextB=await h.newContext(s.context.browser(),s.config);
  contextB.on('page',page=>h.wireStrictPage(page,'shared-contact-B',s.recorder));
  const scheduleB=await h.login(contextB,s.config,s.recorder,{label:'shared-contact-B'});
  const {masterPage:masterB}=await openMasterRecord(contextB,scheduleB,s.recorder,{searchTerm:b.name,preferredDemographicNo:b.id,timeout:20000});
  const popup=(page,locator,label)=>ui.clickOpensPopup(page,locator,{context:page.context(),recorder:s.recorder,label,timeout:20000});
  const links=p=>sql.rows(`SELECT * FROM DemographicContact WHERE demographicNo=${p} AND contactId=${contact} AND type=2 ORDER BY id`);
  const directory=()=>JSON.stringify(sql.rows(`SELECT * FROM Contact WHERE id=${contact}`));
  async function open(master,id) {
    h.assert(new URL(master.url()).searchParams.get('demographic_no')===id,'Wrong patient master record opened');
    return popup(master,masterRecordLink(master,'Contact?method=manage').first(),'shared-contact-manager');
  }
  const field=(editor,name)=>editor.locator(`[name="contact_1.${name}"]`);
  async function clickAndClose(editor,control) {
    const closed=editor.waitForEvent('close');
    await Promise.all([closed,control.click({noWaitAfter:true}).catch(error=>{
      // Chromium can observe this native window.close() before acknowledging
      // the initiating click. Accept only that exact error and observed close.
      if(!editor.isClosed()||!/Target page, context or browser has been closed/.test(error.message))throw error;
    })]);
  }
  const cancel=editor=>clickAndClose(editor,editor.locator('[name="cancel"]'));
  async function save(editor) {
    const master=await editor.opener();h.assert(master,'Contact manager lost its patient opener');
    const refreshed=master.waitForEvent('framenavigated',{predicate:frame=>frame===master.mainFrame()});
    await Promise.all([refreshed,clickAndClose(editor,editor.locator('input[type="submit"]'))]);
    await master.waitForLoadState('load');
  }
  async function associate(master,id,role,sdm,ec,consent) {
    const editor=await open(master,id);await editor.locator('a[onclick="addContact();"]').click();await field(editor,'type').selectOption('2');
    const search=await popup(editor,editor.locator('a[onclick*="doPersonalSearch"]').first(),'shared-directory-search');
    await search.locator('input[name="keyword"][type="text"]').fill(name);
    await ui.clickAndAwaitReload(search,search.locator('input[type="submit"]').first());
    await clickAndClose(search,search.locator('tr[onclick]').filter({hasText:name}));
    h.assert(await field(editor,'contactId').inputValue()===contact,'Search selected a different directory contact');
    await field(editor,'role').selectOption(role);await field(editor,'sdm').setChecked(sdm);await field(editor,'ec').setChecked(ec);
    await field(editor,'consentToContact').selectOption(consent);await field(editor,'note').fill(`${marker}-${id}`);await save(editor);
    await expectValue(sql,`SELECT CONCAT(role,'|',sdm,'|',ec,'|',consentToContact,'|',active) FROM DemographicContact WHERE demographicNo=${id} AND contactId=${contact} AND type=2 AND deleted=0`,
      `${role}|${sdm?'true':''}|${ec?'true':''}|${consent}|1`,'Patient relationship flags were not saved exactly');
  }
  let beforeA,beforeB;
  await s.step('two patient managers link the same native-created directory ID with distinct flags',async()=>{
    await associate(s.master,patient,'Guardian',true,false,'1');await associate(masterB,b.id,'Parent',false,true,'0');
    beforeA=JSON.stringify(links(patient));beforeB=JSON.stringify(links(b.id));
    h.assert(links(patient).length===1&&links(b.id).length===1,'Associations were duplicated');
    h.assert(controlState()===unchanged,'Association creation changed unrelated data');
  });
  const firstName='Shared Māori & "quoted"';
  await s.step('editing shared details through patient A is visible from both patients without changing either link',async()=>{
    await s.master.reload({waitUntil:'load'});
    const details=s.master.locator('#otherContacts2 li').filter({hasText:marker}).getByRole('link',{name:'details',exact:true});
    const openEditor=async()=>{
      const page=await popup(s.master,details,'shared-contact-details');
      await ui.clickAndAwaitReload(page,page.locator('input[name="Edit"]'));
      return page;
    };
    const untouched=directory();let view=await openEditor();
    await view.locator('input[name="contact.firstName"]').fill(marker+' CANCEL');
    await clickAndClose(view,view.locator('input[name="Cancel"]'));
    h.assert(directory()===untouched&&JSON.stringify(links(patient))===beforeA&&JSON.stringify(links(b.id))===beforeB,
      'Abandoning directory edits changed stored details or patient associations');
    view=await openEditor();
    h.assert(await view.locator('input[name="contact.id"]').inputValue()===contact,'Shared editor changed directory identity');
    await view.locator('input[name="contact.firstName"]').fill(firstName);
    await view.locator('input[name="contact.note"]').fill(`${marker} shared O'Brien`);
    await ui.clickAndAwaitReload(view,view.locator('input[type="submit"][name="submit"]'));
    await expectValue(sql,`SELECT firstName FROM Contact WHERE id=${contact}`,firstName,'Shared contact details did not persist');
    h.assert(sql.value(`SELECT COUNT(*) FROM Contact WHERE lastName=${q(name)}`)==='1','Directory edit duplicated the contact');
    h.assert(JSON.stringify(links(patient))===beforeA&&JSON.stringify(links(b.id))===beforeB,'Directory edit changed patient relationship rows');
    await view.close();
    for(const [master,id] of [[s.master,patient],[masterB,b.id]]) {
      await master.reload({waitUntil:'load'});const editor=await open(master,id);
      h.assert(await field(editor,'contactId').inputValue()===contact&&(await field(editor,'contactName').inputValue()).includes(firstName),'Reopened patient did not show the same shared directory details');
      const isA=id===patient;
      h.assert(await field(editor,'sdm').isChecked()===isA&&await field(editor,'ec').isChecked()===!isA
        &&await field(editor,'consentToContact').inputValue()===(isA?'1':'0')
        &&await field(editor,'role').inputValue()===(isA?'Guardian':'Parent'),'Patient-specific flags did not reopen independently');
      await cancel(editor);
    }
    h.assert(controlState()===unchanged,'Shared details edit changed unrelated data');
  });
  await s.step('cancelled and saved A flag edits preserve B and the shared directory',async()=>{
    const currentDirectory=directory();let editor=await open(s.master,patient);
    await field(editor,'sdm').uncheck();await field(editor,'note').fill(marker+' CANCEL');await cancel(editor);
    h.assert(JSON.stringify(links(patient))===beforeA,'Cancelled patient edit changed an association');
    editor=await open(s.master,patient);await field(editor,'sdm').uncheck();await field(editor,'ec').check();await field(editor,'consentToContact').selectOption('0');await save(editor);
    await expectValue(sql,`SELECT CONCAT(sdm,'|',ec,'|',consentToContact) FROM DemographicContact WHERE demographicNo=${patient} AND contactId=${contact} AND deleted=0`,'|true|0','A flags did not update');
    h.assert(JSON.stringify(links(b.id))===beforeB&&directory()===currentDirectory,'A flag edit changed B or the directory');
    h.assert(controlState()===unchanged,'A flag edit changed control data');
  });
  await s.step('removing only A association retains the directory and B exact relationship',async()=>{
    const before=directory(),beforeLink=JSON.stringify(links(patient));let editor=await open(s.master,patient);
    await editor.locator('#contact_1 a[onclick*="deleteContact("]').click();await cancel(editor);
    h.assert(JSON.stringify(links(patient))===beforeLink&&JSON.stringify(links(b.id))===beforeB&&directory()===before,
      'Abandoned association removal changed shared or patient-specific data');
    editor=await open(s.master,patient);
    await editor.locator('#contact_1 a[onclick*="deleteContact("]').click();await save(editor);
    await expectValue(sql,`SELECT COUNT(*) FROM DemographicContact WHERE demographicNo=${patient} AND contactId=${contact} AND deleted=0`,'0','A still has an active association');
    h.assert(JSON.stringify(links(b.id))===beforeB&&directory()===before,'Removing A changed B or deleted shared directory data');
    await s.master.reload({waitUntil:'load'});await masterB.reload({waitUntil:'load'});
    h.assert(await s.master.locator('#otherContacts2 li').filter({hasText:marker}).count()===0,'A still lists the removed link');
    h.assert(await masterB.locator('#otherContacts2 li').filter({hasText:marker}).count()===1,'B lost or duplicated its contact');
    h.assert(controlState()===unchanged,'Association removal changed control data');
  });
}
if(require.main===module)runWorkflow('contact-shared-directory',workflow,{openPatient:true,preflightConfig:()=>{if(process.env.EXCLUSIVE!=='1')throw new h.SkipCheck('Shared Contact directory mutation requires EXCLUSIVE=1');}});
module.exports={workflow,cleanupSharedFixtures,removePatientAudit};
