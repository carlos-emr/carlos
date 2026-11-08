/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path');
const {rendererFileFixture}=require('./eform-deployed-page-exclusion-playwright-checks');
const title='FAKE-PW0123456789abcdef page exclusion clean';
const titleOf=bytes=>bytes.toString();
function store(t) {
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'carlos-eform-renderer-fixture-'));
  t.after(()=>fs.rmSync(root,{recursive:true,force:true}));return root;
}
test('recover an owned server PDF after a lost download acknowledgment',t=>{
  const root=store(t),fixture=rendererFileFixture(root,[title],{titleOf});
  fs.writeFileSync(path.join(root,'eform-browser-render-101.pdf'),title);
  fixture.cleanup();assert.deepEqual(fs.readdirSync(root),[]);
});
test('a preexisting matching renderer PDF is never adopted or removed',t=>{
  const root=store(t),file=path.join(root,'eform-browser-render-101.pdf');fs.writeFileSync(file,title);
  rendererFileFixture(root,[title],{titleOf}).cleanup();assert.equal(fs.readFileSync(file,'utf8'),title);
});
test('foreign new files remain while independent owned renderer files are still cleaned',t=>{
  const root=store(t),fixture=rendererFileFixture(root,[title],{titleOf});
  fs.writeFileSync(path.join(root,'eform-browser-render-101.pdf'),'foreign');fs.writeFileSync(path.join(root,'eform-browser-render-102.pdf'),title);
  assert.throws(()=>fixture.cleanup(),/lacks an owned marker title/);
  assert.deepEqual(fs.readdirSync(root),['eform-browser-render-101.pdf']);
});
test('a replacement renderer store retains every file for reconciliation',t=>{
  const parent=store(t),root=path.join(parent,'render');fs.mkdirSync(root);
  const fixture=rendererFileFixture(root,[title],{titleOf});fs.renameSync(root,root+'-old');fs.mkdirSync(root);
  const file=path.join(root,'eform-browser-render-101.pdf');fs.writeFileSync(file,title);
  assert.throws(()=>fixture.cleanup(),/store changed/);assert.equal(fs.readFileSync(file,'utf8'),title);
});
test('a new symlink is refused without touching its target',t=>{
  const parent=store(t),root=path.join(parent,'render');fs.mkdirSync(root);
  const target=path.join(parent,'target');fs.writeFileSync(target,title);
  const fixture=rendererFileFixture(root,[title],{titleOf}),link=path.join(root,'eform-browser-render-101.pdf');fs.symlinkSync(target,link);
  assert.throws(()=>fixture.cleanup(),/Renderer fixture cleanup failed/);
  assert.ok(fs.lstatSync(link).isSymbolicLink());assert.equal(fs.readFileSync(target,'utf8'),title);
});
test('unreadable PDF ownership cannot become a clean renderer fixture',t=>{
  const root=store(t),fixture=rendererFileFixture(root,[title],{titleOf:()=>{throw new Error('PDF metadata unreadable');}});
  const file=path.join(root,'eform-browser-render-101.pdf');fs.writeFileSync(file,title);
  assert.throws(()=>fixture.cleanup(),/PDF metadata unreadable/);assert.ok(fs.existsSync(file));
});
