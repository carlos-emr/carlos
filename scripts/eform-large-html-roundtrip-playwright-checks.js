#!/usr/bin/env node
/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
'use strict';
const fs=require('node:fs');
const {createHash}=require('node:crypto');
const h=require('./lib/playwright-harness');
const x=require('./lib/export-content-helpers');
const {runWorkflow}=require('./lib/workflow-session');
const NAME='eform-large-html-roundtrip';
const sha=bytes=>createHash('sha256').update(bytes).digest('hex');

/** Export and re-import one owned 17 MiB template through the actual manager UI. */
async function workflow(s) {
  const {sql,marker,provider}=s,name=marker+'LargeHtml',filename=marker+'.html';
  const prefix='<!doctype html><html><head><meta charset="utf-8"></head><body>Zoë Ł\r\n<!--';
  const suffix='--></body></html>\r\n',bytes=17<<20;
  const padding=bytes-Buffer.byteLength(prefix)-Buffer.byteLength(suffix);
  const expected=sha(Buffer.concat([Buffer.from(prefix),Buffer.alloc(padding,120),Buffer.from(suffix)]));
  const scratch=x.scratchDir(),owned=new Set();let sourceId,exported;
  const unrelated=()=>JSON.stringify(sql.rows('SELECT fid,SHA2(CONCAT_WS(CHAR(0),IFNULL(form_name,\'\'),IFNULL(file_name,\'\'),IFNULL(form_creator,\'\'),IFNULL(form_html,\'\'),IFNULL(status,\'\')),256) FROM eform WHERE form_name IS NULL OR form_name<>? ORDER BY fid',[name]));
  const baseline=unrelated();
  const verifyOwned=id=>{
    h.assert(/^[1-9]\d*$/.test(String(id)),'Invalid owned eForm identity');
    h.assert(sql.value('SELECT COUNT(*) FROM eform WHERE fid=? AND form_name=? AND file_name=? AND form_creator=? AND OCTET_LENGTH(form_html)=? AND SHA2(form_html,256)=?',
      [id,name,filename,provider,bytes,expected])==='1','Owned large template identity or bytes changed');
    h.assert(sql.value('SELECT (SELECT COUNT(*) FROM eform_data WHERE fid=?)+(SELECT COUNT(*) FROM eform_values WHERE fid=?)',[id,id])==='0','Large template acquired unexpected patient instances');
  };
  h.assert(sql.value('SELECT COUNT(*) FROM eform WHERE form_name=?',[name])==='0','Large eForm namespace already exists');
  s.cleanup(()=>fs.rmSync(scratch,{recursive:true,force:true}));
  s.cleanup(()=>{
    for(const row of sql.rows('SELECT fid FROM eform WHERE form_name=?',[name])) {
      const id=row[0];verifyOwned(id);owned.add(String(id));
      sql.execute('DELETE FROM eform WHERE fid=? AND form_name=?',[id,name]);
    }
    for(const id of owned)h.assert(sql.value('SELECT COUNT(*) FROM eform WHERE fid=?',[id])==='0','Owned large template remains');
    h.assert(unrelated()===baseline,'Unrelated eForm rows changed');
  });
  await s.step('a synthetic template above the old 16 MiB ceiling is stored without patient instances',async()=>{
    sql.execute('INSERT INTO eform(form_name,file_name,subject,form_date,form_time,form_creator,status,form_html,showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable) VALUES(?,?,?,CURDATE(),CURTIME(),?,1,CONCAT(?,REPEAT(\'x\',?),?),0,0,\'\',0,1)',
      [name,filename,name,provider,prefix,padding,suffix]);
    sourceId=sql.value('SELECT fid FROM eform WHERE form_name=?',[name]);owned.add(String(sourceId));verifyOwned(sourceId);
  });
  const manager=await s.context.newPage();
  await s.step('Manage eForms exports the exact large HTML and metadata',async()=>{
    await h.gotoApp(manager,s.config.baseUrl,'/eform/efmformmanager');
    const links=await manager.locator('#eformTbl a[href*="method=exportEForm"]').evaluateAll(nodes=>nodes.map(a=>a.getAttribute('href')));
    const matches=links.filter(href=>new URL(href,s.config.baseUrl).searchParams.get('fid')===String(sourceId));
    h.assert(matches.length===1,'The owned template has no unique native export link');
    exported=await x.saveDownload(manager,scratch,()=>manager.locator('a[href='+JSON.stringify(matches[0])+']').click(),{route:/\/eform\/manageEForm$/});
    h.assert(exported.status===200,'Large eForm export failed');
    const entries=x.unzip(exported.bytes),htmlEntry=entries[name+'/'+filename];
    h.assert(htmlEntry&&htmlEntry.length===bytes&&sha(htmlEntry)===expected,'Export changed the large HTML bytes');
    h.assert(entries[name+'/eform.properties'],'Export omitted form metadata');
  });
  await s.step('the source is removed and the downloaded ZIP imports the same large template',async()=>{
    verifyOwned(sourceId);sql.execute('DELETE FROM eform WHERE fid=? AND form_name=?',[sourceId,name]);
    h.assert(sql.value('SELECT COUNT(*) FROM eform WHERE form_name=?',[name])==='0','Source could conceal an incomplete import');
    await h.gotoApp(manager,s.config.baseUrl,'/eform/efmformmanager');
    await manager.locator('#eformOptions a[href="#import"]').click();
    const frame=manager.frameLocator('#importFrame');
    await frame.locator('#zippedForm').setInputFiles({name:name+'.zip',mimeType:'application/zip',buffer:exported.bytes});
    const [response]=await Promise.all([manager.waitForResponse(r=>r.request().method()==='POST'&&new URL(r.url()).pathname.endsWith('/eform/manageEForm')),
      frame.locator('input.upload[type="submit"]').click()]);
    h.assert(response.status()===200&&(await response.text()).includes('Your eform was imported.'),'The application refused its own supported large export');
    const imported=sql.value('SELECT fid FROM eform WHERE form_name=?',[name]);owned.add(String(imported));
    h.assert(imported!==sourceId,'Import did not create a fresh template');verifyOwned(imported);
    h.assert(unrelated()===baseline,'Import changed unrelated templates');
  });
}
if(require.main===module)runWorkflow(NAME,workflow,{openPatient:false,openMaster:false});
module.exports={workflow};
