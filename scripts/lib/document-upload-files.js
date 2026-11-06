/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {assert}=require('./playwright-harness');
const {readRegular,retireOwnedFile}=require('./owned-file-retirement');
const digest=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
function directoryIdentity(root) {
  const info=fs.lstatSync(root);
  assert(info.isDirectory()&&fs.realpathSync(root)===root,'Upload cleanup store must remain a real directory');
  return {dev:info.dev,ino:info.ino};
}
function verifyDirectory(root,expected) {
  if(!expected&&!fs.lstatSync(root,{throwIfNoEntry:false}))return false;
  const current=directoryIdentity(root);
  assert(!expected||(current.dev===expected.dev&&current.ino===expected.ino),
    'Upload cleanup store was replaced; retain its contents for review');
  return true;
}
function captureUploadFiles(store,cache=path.join(path.dirname(store),'document_cache')) {
  assert(path.isAbsolute(store)&&path.isAbsolute(cache),'Upload cleanup needs absolute configured stores');
  store=fs.realpathSync(store);
  cache=fs.existsSync(cache)?fs.realpathSync(cache):path.resolve(cache);
  assert(store!==cache,'Upload and rendering stores must be distinct');
  return {store,cache,storeIdentity:directoryIdentity(store),
    cacheIdentity:fs.lstatSync(cache,{throwIfNoEntry:false})?directoryIdentity(cache):null,
    files:new Set(fs.readdirSync(store)),
    cacheExisted:fs.existsSync(cache),cached:new Set(fs.existsSync(cache)?fs.readdirSync(cache):[])};
}
// The caller supplies names from its nonce-owned document rows. Never remove a
// pre-existing entry, a path, a link or a replacement that races retirement.
function removeUploadFiles(before,names) {
  assert(before,'Upload cleanup needs its pre-write file inventory');
  const failures=[];
  function remove(root,name,previous) {
    try {
      verifyDirectory(root,root===before.store?before.storeIdentity:before.cacheIdentity);
      assert(typeof name==='string'&&name&&name===path.basename(name)&&name!=='.'&&name!=='..','Invalid owned upload filename');
      const file=path.join(root,name);
      if(!fs.existsSync(file)&&!fs.lstatSync(file,{throwIfNoEntry:false}))return;
      assert(!previous.has(name),'Upload entry predates this run; retain it for review');
      const observed=readRegular(file);
      if(observed)retireOwnedFile(file,bytes=>digest(bytes)===digest(observed.bytes));
    } catch(error) { failures.push(error); }
  }
  for(const name of new Set(names)) {
    if(typeof name!=='string'||name!==path.basename(name)||!name||name==='.'||name==='..') {
      failures.push(new Error('Invalid owned upload filename'));continue;
    }
    try {
      if(verifyDirectory(before.cache,before.cacheIdentity))for(const cached of fs.readdirSync(before.cache)) {
        if(cached.startsWith(name+'_')&&/^[1-9]\d*(?:_[1-9]\d*dpi)?\.png$/.test(cached.slice(name.length+1))) {
          remove(before.cache,cached,before.cached);
        }
      }
    } catch(error) { failures.push(error); }
    remove(before.store,name,before.files);
  }
  try {
    if(!before.cacheExisted&&verifyDirectory(before.cache,before.cacheIdentity)
      &&!fs.readdirSync(before.cache).length)fs.rmdirSync(before.cache);
  } catch(error) { failures.push(error); }
  if(failures.length)throw new AggregateError(failures,'Owned upload files or page renderings were not restored');
}
module.exports={captureUploadFiles,removeUploadFiles};
