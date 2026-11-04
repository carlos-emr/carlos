/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {removeOwnedPdfDocuments}=require('./lib/stored-pdf-documents');
test('a cross-patient document link retains the document and file even without a routing row',t=>{
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'carlos-doc-owner-'));t.after(()=>fs.rmSync(dir,{recursive:true,force:true}));
  const file=path.join(dir,'owned.pdf');fs.writeFileSync(file,'owned PDF');
  const sql={rows:()=>[['7']],value:query=>query.includes('ctl_document')?'1':'0',execute:()=>assert.fail('must retain every row')};
  assert.throws(()=>removeOwnedPdfDocuments({sql,marker:'FAKE-PW0123456789abcdef',patient:'42',files:[file],docs:[{id:'7'}]}),/external document link/);
  assert.equal(fs.readFileSync(file,'utf8'),'owned PDF');
});
test('all shared browser launch options leave SIGINT and SIGTERM with the harness',()=>{
  const options=require('./lib/playwright-harness').getLaunchOptions();assert.equal(options.handleSIGINT,false);assert.equal(options.handleSIGTERM,false);
});
