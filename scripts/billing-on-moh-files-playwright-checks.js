#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const fs=require('node:fs'),path=require('node:path');
const h=require('./lib/playwright-harness'),ui=require('./lib/playwright-ui');
const {runWorkflow}=require('./lib/workflow-session');
const {revealAuditLink}=require('./lib/playwright-link-audit');
const {directory,sha}=require('./lib/stored-pdf-documents');
const {authenticatedProbe}=require('./lib/authenticated-probe');
const entryExists=file=>fs.lstatSync(file,{throwIfNoEntry:false})!==undefined;
async function workflow(s) {
  const inbox=directory('ONEDT_INBOX','ONEDT_INBOX'),archive=directory('ONEDT_ARCHIVE','ONEDT_ARCHIVE');
  h.assert(inbox!==archive,'MOH Inbox and Archive must be distinct directories');
  const files=['primary','control'].map(key=>({name:`${s.marker}-${key}.txt`,bytes:Buffer.from(`${s.marker} ${key} synthetic MOH file\n`)}));
  for(const file of files)for(const dir of [inbox,archive])h.assert(!entryExists(path.join(dir,file.name)),'Owned MOH file path already exists');
  s.cleanup(()=>{
    for(const file of files)for(const dir of [inbox,archive]) {
      const target=path.join(dir,file.name);if(!entryExists(target))continue;
      h.assert(fs.lstatSync(target).isFile()&&!fs.lstatSync(target).isSymbolicLink()&&fs.readFileSync(target).equals(file.bytes),'Owned MOH file changed; retain it for inspection');
      fs.unlinkSync(target);
    }
    for(const file of files)for(const dir of [inbox,archive])h.assert(!entryExists(path.join(dir,file.name)),'Owned MOH file remains');
  });
  const owner=fs.statSync(inbox);
  for(const file of files){const target=path.join(inbox,file.name);fs.writeFileSync(target,file.bytes,{flag:'wx',mode:0o640});fs.chownSync(target,owner.uid,owner.gid);}
  const [primary,control]=files,controlSha=sha(path.join(inbox,control.name));
  const {page:admin}=await ui.clickOpensPopupOrNavigates(s.schedule,s.schedule.locator('#admin-panel,#admin2').first(),
    {context:s.context,recorder:s.recorder,label:'moh-file-administration',timeout:30000});
  const link=admin.locator('#adminNav a[rel$="/billing/CA/ON/moveMOHFiles"]');await revealAuditLink(admin,link,30000);await link.click();
  const iframe=admin.locator('#dynamic-content iframe').first();await iframe.waitFor();
  const page=await(await iframe.elementHandle()).contentFrame();h.assert(page,'MOH Files iframe is absent');await page.locator('#reportFolder').waitFor({state:'attached'});
  await s.step('an authenticated GET cannot archive the selected owned file',async()=>{
    const response=await authenticatedProbe(s,h.appUrl(s.config.baseUrl,`/billing/CA/ON/moveMOHFiles?folder=inbox&mohFile=${encodeURIComponent(primary.name)}`));
    await h.assertRefused(s,{response,table:'provider',where:`provider_no=${h.sqlString(s.provider)}`,before:'1',label:'MOH file archive GET'});
    h.assert(fs.readFileSync(path.join(inbox,primary.name)).equals(primary.bytes)&&!entryExists(path.join(archive,primary.name)),'A GET moved or changed the owned file');
  });
  await s.step('Archive moves exactly the checked file, preserves its bytes and shows it in the archive list',async()=>{
    await page.locator(`input[name="mohFile"][value="${primary.name}"]`).check();
    const [response]=await Promise.all([admin.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/billing/CA/ON/moveMOHFiles')),
      Promise.all([page.waitForNavigation({waitUntil:'domcontentloaded',timeout:30000}),page.locator('input[type="submit"][value="Archive"]').first().click()])]);
    h.assert(response.status()===200,'MOH archive failed');const sent=new URLSearchParams(response.request().postData()||'');
    h.assert(JSON.stringify(sent.getAll('mohFile'))===JSON.stringify([primary.name]),'Archive submitted another selected file');
    h.assert(!entryExists(path.join(inbox,primary.name))&&fs.readFileSync(path.join(archive,primary.name)).equals(primary.bytes),'Archive did not move the original bytes');
    h.assert(sha(path.join(inbox,control.name))===controlSha&&!entryExists(path.join(archive,control.name)),'Archive changed the unchecked control file');
    await Promise.all([page.waitForNavigation({waitUntil:'domcontentloaded',timeout:30000}),page.locator('select[name="folder"]').first().selectOption('archive')]);
    await page.getByText(primary.name,{exact:true}).first().waitFor();
    h.assert(await page.getByText(control.name,{exact:true}).count()===0,'Unchecked control appeared in Archive');
  });
}
if(require.main===module)runWorkflow('billing-on-moh-files',workflow,{openPatient:false});
module.exports={workflow};
