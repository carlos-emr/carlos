/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {requireEnglishOcr}=require('./lib/ocr-preflight');
test('OCR preflight proves English model availability with a bounded real language-list invocation',()=>{
  requireEnglishOcr((binary,args,options)=>{
    assert.equal(binary,'tesseract');assert.deepEqual(args,['--list-langs']);assert.equal(options.timeout,10000);
    return 'List of available languages (2):\neng\nosd\n';
  });
});
for(const output of ['', 'List of available languages (1):\nosd\n', 'eng_old\nfrench-eng\n'])test('OCR refuses absent exact English model: '+JSON.stringify(output),()=>{
  assert.throws(()=>requireEnglishOcr(()=>output),/English language data/);
});
for(const code of ['ENOENT','ETIMEDOUT','nonzero'])test('OCR execution failure remains fatal: '+code,()=>{
  const error=Object.assign(new Error(code),{code});assert.throws(()=>requireEnglishOcr(()=>{throw error;}),failure=>failure===error);
});
