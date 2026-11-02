/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {retireOwnedFile}=require('./lib/owned-file-retirement');
for(const replacement of ['file','symlink'])test(`retirement preserves a ${replacement} swapped after initial verification`,t=>{
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'carlos-retire-race-'));t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
  const file=path.join(dir,'owned'),foreign=path.join(dir,'foreign');fs.writeFileSync(file,'owned');fs.writeFileSync(foreign,'foreign');
  const rename=fs.renameSync;let injected=false;
  t.mock.method(fs,'renameSync',(from,to)=>{if(from===file&&!injected){injected=true;fs.unlinkSync(file);if(replacement==='file')fs.writeFileSync(file,'foreign');else fs.symlinkSync(foreign,file);}return rename(from,to);});
  assert.throws(()=>retireOwnedFile(file,bytes=>bytes.equals(Buffer.from('owned'))),/changed|symlink|ELOOP/);
  assert.equal(fs.lstatSync(file).isSymbolicLink(),replacement==='symlink');
  if(replacement==='symlink')assert.equal(fs.readlinkSync(file),foreign);
  assert.equal(fs.readFileSync(file,'utf8'),'foreign');assert.equal(fs.readFileSync(foreign,'utf8'),'foreign');
  assert.deepEqual(fs.readdirSync(dir).sort(),['foreign','owned']);
});

test('a FIFO replacement is rejected without blocking on an open',t=>{
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'carlos-retire-fifo-'));t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
  const file=path.join(dir,'fifo'),{execFileSync,spawnSync}=require('node:child_process');execFileSync('mkfifo',[file]);
  const script="require('node:assert/strict').throws(()=>require(process.argv[1]).readRegular(process.argv[2]),/regular/);";
  const result=spawnSync(process.execPath,['-e',script,require.resolve('./lib/owned-file-retirement'),file],{encoding:'utf8',timeout:2000});
  assert.equal(result.status,0,result.stderr);assert.equal(result.error,undefined);assert.equal(fs.lstatSync(file).isFIFO(),true);
});
test('quarantine setup failure removes only its empty temporary directory',t=>{
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'carlos-retire-chmod-'));t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
  const file=path.join(dir,'owned');fs.writeFileSync(file,'owned');t.mock.method(fs,'chmodSync',()=>{throw new Error('injected chmod failure');});
  assert.throws(()=>retireOwnedFile(file,()=>true),/chmod failure/);assert.deepEqual(fs.readdirSync(dir),['owned']);assert.equal(fs.readFileSync(file,'utf8'),'owned');
});
