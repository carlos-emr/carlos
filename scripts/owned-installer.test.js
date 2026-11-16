/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {runOwnedInstaller}=require('./lib/owned-installer');
test('installer completion is awaited asynchronously',async()=>{
 let responsive=false;setImmediate(()=>{responsive=true;});await runOwnedInstaller(process.execPath,['-e','setTimeout(()=>{},50)'],{timeoutMs:2000});assert.equal(responsive,true);
});
test('installer timeout stops its descendant writes and requires canary reconciliation',async t=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'carlos-installer-tree-'));t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));const file=path.join(dir,'late-write');
 const grandchild="setTimeout(()=>require('node:fs').writeFileSync(process.argv[1],'late'),700);";
 const parent="require('node:child_process').spawn(process.execPath,['-e',process.argv[1],process.argv[2]],{stdio:'inherit'});setInterval(()=>{},1000);";
 await assert.rejects(runOwnedInstaller(process.execPath,['-e',parent,grandchild,file],{timeoutMs:150,killGraceMs:200}),error=>error.uncertain===true&&/timeout/.test(error.message));
 await new Promise(resolve=>setTimeout(resolve,800));assert.equal(fs.existsSync(file),false);
});
