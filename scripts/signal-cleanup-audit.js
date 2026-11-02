#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),{spawnSync}=require('node:child_process');
const h=require('./lib/playwright-harness');
const {captureBaseline,auditResidueDetailed}=require('./lib/residue-audit');
const {providerNavigationFixture}=require('./lib/provider-navigation-fixture');
const {loadManifest}=require('./run-playwright-suite');
const files=require('./lib/signal-audit-files');
function verdict(status,record,residue) {
  if(residue.length||record?.cleanupFailed)return 'FAIL';
  if(status===2&&record?.outcome==='SKIP')return 'BLOCKED';
  return status===130&&record?.outcome==='FAIL'&&record?.detail==='interrupted'?'PASS':'FAIL';
}
function unavailable(check,env=process.env) {
  if(check.name==='deb-login')return 'Requires a fresh package initial-login/password-reset fixture; the configured-runtime audit must not change the shared administrator credential';
  const province=env.CARLOS_SUITE_PROVINCE||'ON';
  if(!check.provinces.includes('all')&&!check.provinces.includes(province))return `Requires ${check.provinces.join('/')} installation; current province is ${province}`;
  if(check.profile&&check.profile!==(env.CARLOS_PROPERTY_PROFILE_ACTIVE||'default'))return `Requires ${check.profile} configuration profile`;
  return null;
}
async function main({cancellation}) {
  h.assert(process.env.CARLOS_DISPOSABLE_HOST==='true','Signal audit requires CARLOS_DISPOSABLE_HOST=true');
  const only=[],args=process.argv.slice(2);let output;
  while(args.length) {
    const flag=args.shift(),value=args.shift();h.assert(value,'Signal audit arguments need values');
    if(flag==='--only')only.push(value);else if(flag==='--output-dir')output=value;else throw new Error('Unknown signal audit argument');
  }
  h.assert(output&&path.isAbsolute(output),'Signal audit needs an absolute --output-dir');
  const manifest=loadManifest(),selected=only.length?only.map(name=>{
    const check=manifest.find(item=>item.name===name);h.assert(check,`Unknown signal audit check: ${name}`);return check;
  }):manifest.filter(check=>!check.tiers.includes('live-external')&&fs.readFileSync(path.resolve(__dirname,'..',check.script),'utf8').match(/legacyHarness\.(?:runLegacyCheck|armInterruption)\(/));
  h.assert(selected.length,'No migrated checks selected');
  const unique=new Map();for(const check of selected){h.assert(!check.tiers.includes('live-external'),'Live transmission checks require their separately authorized workflow');if(!unique.has(check.script)||unavailable(unique.get(check.script))&&!unavailable(check))unique.set(check.script,check);}
  fs.mkdirSync(output,{recursive:true,mode:0o700});
  const config=h.readConfig(),sql=h.createSqlRunner(config.mysql),results=[];
  try {
    for(const check of unique.values()) {
      cancellation.throwIfCancelled();
      const reason=unavailable(check);
      if(reason) {
        const result={name:check.name,outcome:'BLOCKED',detail:reason,residue:[]};results.push(result);console.log(JSON.stringify(result));
        fs.writeFileSync(path.join(output,'results.json'),JSON.stringify(results,null,2)+'\n',{mode:0o600});continue;
      }
      const script=path.resolve(__dirname,'..',check.script),source=fs.readFileSync(script,'utf8');
      const count=(source.match(/cancellation\?\.throwIfCancelled\(\)/g)||[]).length;
      h.assert(count>0,`${check.name} has no cooperative checkpoint to interrupt`);
      const checkpoint=Math.max(1,Math.floor(count/2)),directory=fs.mkdtempSync(path.join(os.tmpdir(),'carlos-signal-audit-'));
      // Diagnostic screenshots are outputs, not clinic or temporary fixture stores.
      const artifactEnvironment={};
      for(const match of source.matchAll(/process\.env\.([A-Z][A-Z0-9_]*_SCREENSHOT_DIR|SCREENSHOT_DIR)\b/g))artifactEnvironment[match[1]]=path.join(directory,'artifacts');
      for(const target of new Set(Object.values(artifactEnvironment)))fs.mkdirSync(target,{recursive:true,mode:0o700});
      let navigation,record,residue,child;
      const before=captureBaseline({sql,mutates:check.mutates||[]});
      const roots=[process.env.BASE_DOCUMENT_DIR,process.env.DOCUMENT_DIR,process.env.RX_FAX_DOCUMENT_DIR,
        process.env.INCOMINGDOCUMENT_DIR,process.env.ONEDT_INBOX,process.env.ONEDT_ARCHIVE,process.env.EFORM_IMAGE_DIR].filter(Boolean);
      h.assert(roots.length,'Signal audit needs configured fixture stores for independent file verification');
      const fileOptions={roots,tempPrefixes:files.scratchPrefixes(source)},fileBefore=files.snapshot(fileOptions);
      fs.writeFileSync(path.join(output,check.name+'.files-before.json'),JSON.stringify([...fileBefore]),{mode:0o600});
      try {
        navigation=providerNavigationFixture(sql,config.testUser,check.name);
        child=spawnSync(process.execPath,[script],{encoding:'utf8',timeout:check.timeoutSec*1000,maxBuffer:16<<20,
          env:{...process.env,...check.envSet,...artifactEnvironment,CARLOS_INTERRUPT_AFTER_CHECKPOINT:String(checkpoint),RESULT_JSON:path.join(directory,'result.json')}});
        fs.writeFileSync(path.join(output,check.name+'.log'),(child.stdout||'')+(child.stderr||''),{mode:0o600});
        if(fs.existsSync(path.join(directory,'result.json')))record=JSON.parse(fs.readFileSync(path.join(directory,'result.json'),'utf8'));
      } finally {
        try{if(navigation)navigation.cleanup();}finally{fs.rmSync(directory,{recursive:true,force:true});}
      }
      residue=auditResidueDetailed({sql,since:before}).residue;
      const fileChanges=files.differences(fileBefore,files.snapshot(fileOptions));
      if(Object.values(fileChanges).some(Boolean))residue.push({files:fileChanges});
      const result={name:check.name,checkpoint,totalCheckpoints:count,exit:child.status,signal:child.signal,
        outcome:verdict(child.status,record,residue),detail:record?.detail||child.error?.code||'No structured result',residue,fileChanges};
      results.push(result);console.log(JSON.stringify(result));
      fs.writeFileSync(path.join(output,'results.json'),JSON.stringify(results,null,2)+'\n',{mode:0o600});
      h.assert(residue.length===0,`Stopping signal audit after ${check.name}: residue must be investigated before another workflow`);
    }
  } finally {sql.dispose();}
  h.assert(!results.some(result=>result.outcome==='FAIL'),'Signal audit found a failed interruption or residue; see results.json');
  if(results.some(result=>result.outcome==='BLOCKED'))throw new h.SkipCheck('Some signal audits require documented fixtures; see results.json');
}
if(require.main===module)h.runCheck({name:'signal-cleanup-audit',run:main});
module.exports={main,verdict,unavailable};
