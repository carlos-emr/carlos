/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const root=path.resolve(__dirname,'..');
test('native measurement evidence identifies the retained capture utilities exactly',()=>{
 const manifest=JSON.parse(fs.readFileSync(path.join(root,'docs/images/pr6102/native/manifest.json'),'utf8'));
 assert.ok(Object.keys(manifest.reproductionUtilities).length>=5);
 for(const [relative,expected] of Object.entries(manifest.reproductionUtilities)){
  assert.match(relative,/^docs\/images\/pr6102\/capture\/[A-Za-z0-9_.-]+$/);
  const actual=crypto.createHash('sha256').update(fs.readFileSync(path.join(root,relative))).digest('hex');
  assert.equal(actual,expected,relative+' changed; refresh its evidence manifest hash');
 }
});
