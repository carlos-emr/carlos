/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {captureUploadFiles,removeUploadFiles}=require('./lib/document-upload-files');
function fixture(t) {
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'carlos-upload-files-'));
 t.after(()=>fs.rmSync(root,{recursive:true,force:true}));
 const store=path.join(root,'document'),cache=path.join(root,'document_cache');fs.mkdirSync(store);fs.mkdirSync(cache);
 return {store,cache,write:(root,name)=>fs.writeFileSync(path.join(root,name),'FAKE synthetic content')};
}
test('owned uploads and both default/DPI page renderings are removed while unrelated files survive',t=>{
 const f=fixture(t),before=captureUploadFiles(f.store);
 for(const name of ['owned.pdf','owned-link.html'])f.write(f.store,name);
 for(const name of ['owned.pdf_1.png','owned.pdf_2_150dpi.png','owned-link.html_1.png','owned.pdf-other_1.png','other.pdf_1.png'])f.write(f.cache,name);
 removeUploadFiles(before,['owned.pdf','owned-link.html']);
 assert.deepEqual(fs.readdirSync(f.store),[]);
 assert.deepEqual(fs.readdirSync(f.cache).sort(),['other.pdf_1.png','owned.pdf-other_1.png']);
 removeUploadFiles(before,['owned.pdf']);
});
test('pre-existing entries and unsafe paths are retained and do not prevent later owned-file cleanup',t=>{
 const f=fixture(t);f.write(f.store,'existing.pdf');f.write(f.cache,'existing.pdf_1.png');
 const before=captureUploadFiles(f.store);f.write(f.store,'owned.pdf');
 assert.throws(()=>removeUploadFiles(before,['../escape','existing.pdf','owned.pdf']),AggregateError);
 assert(fs.existsSync(path.join(f.store,'existing.pdf')));assert(fs.existsSync(path.join(f.cache,'existing.pdf_1.png')));
 assert(!fs.existsSync(path.join(f.store,'owned.pdf')));
});
test('links and directories are preserved, cleanup failure is fatal, and later owned entries are still removed',t=>{
 const f=fixture(t),before=captureUploadFiles(f.store);f.write(f.store,'safe.pdf');
 f.write(f.store,'target.pdf');fs.symlinkSync(path.join(f.store,'target.pdf'),path.join(f.store,'link.pdf'));fs.mkdirSync(path.join(f.store,'directory.pdf'));
 assert.throws(()=>removeUploadFiles(before,['link.pdf','directory.pdf','safe.pdf']),AggregateError);
 assert(fs.lstatSync(path.join(f.store,'link.pdf')).isSymbolicLink());assert(fs.existsSync(path.join(f.store,'target.pdf')));
 assert(fs.statSync(path.join(f.store,'directory.pdf')).isDirectory());assert(!fs.existsSync(path.join(f.store,'safe.pdf')));
});
test('replaced upload and cache directories cannot redirect cleanup to unrelated files',t=>{
 for(const rootName of ['store','cache'])for(const replacement of ['link','directory']) {
  const f=fixture(t),before=captureUploadFiles(f.store);
  const root=f[rootName],foreign=path.join(path.dirname(root),'foreign');fs.mkdirSync(foreign);
  const name=rootName==='cache'?'owned.pdf_1.png':'owned.pdf';
  f.write(foreign,name);f.write(f.store,'owned.pdf');
  fs.renameSync(root,root+'-original');
  if(replacement==='link')fs.symlinkSync(foreign,root);
  else {fs.mkdirSync(root);f.write(root,name);}
  assert.throws(()=>removeUploadFiles(before,['owned.pdf']),AggregateError);
  assert(fs.existsSync(path.join(root,name)));
  assert(fs.existsSync(path.join(foreign,name)));
  if(rootName==='cache')assert(!fs.existsSync(path.join(f.store,'owned.pdf')));
 }
});
test('a newly created rendering cache must be a real directory before cleanup',t=>{
 const f=fixture(t);fs.rmdirSync(f.cache);
 const before=captureUploadFiles(f.store),foreign=path.join(path.dirname(f.cache),'foreign');fs.mkdirSync(foreign);
 f.write(foreign,'owned.pdf_1.png');f.write(f.store,'owned.pdf');fs.symlinkSync(foreign,f.cache);
 assert.throws(()=>removeUploadFiles(before,['owned.pdf']),AggregateError);
 assert(fs.existsSync(path.join(foreign,'owned.pdf_1.png')));
 assert(!fs.existsSync(path.join(f.store,'owned.pdf')));
});
