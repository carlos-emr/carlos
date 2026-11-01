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
  assert.equal(fs.readFileSync(file,'utf8'),'foreign');assert.equal(fs.readFileSync(foreign,'utf8'),'foreign');
  assert.deepEqual(fs.readdirSync(dir).sort(),['foreign','owned']);
});
