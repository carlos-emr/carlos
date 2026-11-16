/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const fs=require('node:fs'),os=require('node:os'),path=require('node:path'),assert=require('node:assert/strict'),test=require('node:test');
const {ownedUploadedImage}=require('./lib/owned-uploaded-image');
test('uploaded image teardown requires confirmed browser closure and preserves replacement bytes',t=>{
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'owned-image-unit-'));t.after(()=>fs.rmSync(root,{recursive:true,force:true}));
  const name='FAKE-PW0123456789abcdef-bg.png',file=path.join(root,name),bytes=Buffer.from('synthetic image');
  const owned=ownedUploadedImage(root,name,bytes);fs.writeFileSync(file,bytes);owned.verify();
  assert.throws(()=>owned.cleanup(),/shutdown is unconfirmed/);assert(fs.existsSync(file));
  fs.writeFileSync(file,'foreign replacement');assert.throws(()=>owned.cleanup({browserClosed:true}),/changed/);assert.equal(fs.readFileSync(file,'utf8'),'foreign replacement');
  fs.writeFileSync(file,bytes);owned.cleanup({browserClosed:true});assert(!fs.existsSync(file));
});
test('uploaded image intent refuses pre-existing entries and never follows a symlink to foreign content',t=>{
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'owned-image-unit-'));t.after(()=>fs.rmSync(root,{recursive:true,force:true}));
  const name='FAKE-PW0123456789abcdef-bg.png',file=path.join(root,name),target=path.join(root,'foreign');fs.writeFileSync(target,'foreign');
  const owned=ownedUploadedImage(root,name,Buffer.from('owned'));fs.symlinkSync(target,file);
  assert.throws(()=>owned.cleanup({browserClosed:true}),/changed/);assert.equal(fs.readFileSync(target,'utf8'),'foreign');
  assert.throws(()=>ownedUploadedImage(root,name,Buffer.from('owned')),/already exists/);
});
