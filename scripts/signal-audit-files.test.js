/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {snapshot,differences,scratchPrefixes}=require('./lib/signal-audit-files');
test('signal file inventory detects leftover scratch credentials and in-place store changes without reporting content or names',t=>{
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'signal-files-unit-'));t.after(()=>fs.rmSync(root,{recursive:true,force:true}));
  const store=path.join(root,'store');fs.mkdirSync(store);fs.writeFileSync(path.join(store,'private patient.pdf'),'original');
  const options={roots:[store],tempRoot:root,tempPrefixes:['owned-']},before=snapshot(options);
  fs.writeFileSync(path.join(store,'private patient.pdf'),'replaced');fs.mkdirSync(path.join(root,'owned-leftover'));fs.writeFileSync(path.join(root,'owned-leftover','mysql.cnf'),'secret');
  const change=differences(before,snapshot(options));assert.deepEqual(change,{added:2,removed:0,changed:1});
  assert(!JSON.stringify([...snapshot(options)]).includes('private patient'));assert(!JSON.stringify(change).includes('secret'));
  fs.rmSync(path.join(root,'owned-leftover'),{recursive:true});fs.writeFileSync(path.join(store,'private patient.pdf'),'original');
  assert.deepEqual(differences(before,snapshot(options)),{added:0,removed:0,changed:0});
  const changedFile=path.join(store,'private patient.pdf');
  fs.chmodSync(changedFile,(fs.statSync(changedFile).mode&0o777)^0o040);
  assert.deepEqual(differences(before,snapshot(options)),{added:0,removed:0,changed:1});
});
test('signal file inventory does not traverse a symlink outside its fixture store',t=>{
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'signal-files-unit-'));t.after(()=>fs.rmSync(root,{recursive:true,force:true}));
  const store=path.join(root,'store'),outside=path.join(root,'outside');fs.mkdirSync(store);fs.writeFileSync(outside,'original');fs.symlinkSync(outside,path.join(store,'link'));
  const options={roots:[store],tempRoot:root},before=snapshot(options);fs.writeFileSync(outside,'foreign changed bytes');
  assert.deepEqual(differences(before,snapshot(options)),{added:0,removed:0,changed:0});assert.equal(fs.readFileSync(outside,'utf8'),'foreign changed bytes');
});
test('signal scratch inventory includes the actual legacy prefix and rejects broad store roots',()=>{
  assert.deepEqual(scratchPrefixes("fs.mkdtempSync(path.join(os.tmpdir(), 'appt-lifecycle-'))"),['carlos-playwright-sql-','carlos-annotation-fixture-','appt-lifecycle-']);
  assert.throws(()=>snapshot({roots:['/']}),/specific absolute/);
});
