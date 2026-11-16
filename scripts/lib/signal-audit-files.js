/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const fs=require('node:fs'),path=require('node:path'),os=require('node:os'),crypto=require('node:crypto');
const digest=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
// Read-only inventories. Resolve configured store roots, but never follow an entry symlink or expose names/bytes in results.
function snapshot({roots=[],tempRoot=os.tmpdir(),tempPrefixes=[]}={}) {
  const entries=new Map();
  function visit(file,key) {
    const stat=fs.lstatSync(file);
    const identity=digest(key),metadata=`:${stat.mode&0o7777}:${stat.uid}:${stat.gid}`;
    if(stat.isSymbolicLink()){entries.set(identity,digest('link:'+fs.readlinkSync(file))+metadata);return;}
    if(stat.isDirectory()) {
      entries.set(identity,'directory'+metadata);
      for(const name of fs.readdirSync(file).sort())visit(path.join(file,name),key+'/'+name);
      return;
    }
    if(!stat.isFile())throw new Error('Signal file audit encountered a non-regular store entry');
    const fd=fs.openSync(file,fs.constants.O_RDONLY|fs.constants.O_NOFOLLOW),hash=crypto.createHash('sha256');
    try {
      const before=fs.fstatSync(fd);if(!before.isFile())throw new Error('Signal file audit entry changed type');
      const buffer=Buffer.alloc(1<<20);let count;
      while((count=fs.readSync(fd,buffer,0,buffer.length,null))>0)hash.update(buffer.subarray(0,count));
      const after=fs.fstatSync(fd);
      if(before.size!==after.size||before.mtimeMs!==after.mtimeMs)throw new Error('Signal file audit entry changed during its snapshot');
      entries.set(identity,'file:'+hash.digest('hex')+metadata);
    } finally {fs.closeSync(fd);}
  }
  const seen=new Set();
  for(const root of roots) {
    if(!path.isAbsolute(root)||path.resolve(root)===path.parse(root).root)throw new Error('Signal file audit needs specific absolute fixture stores');
    const canonical=fs.existsSync(root)?fs.realpathSync(root):path.resolve(root);
    if(canonical===path.parse(canonical).root)throw new Error('Signal file audit needs specific absolute fixture stores');
    if(seen.has(canonical))continue;seen.add(canonical);
    if(fs.existsSync(canonical))visit(canonical,'store:'+canonical);
    else entries.set(digest('store:'+canonical),'absent');
  }
  for(const prefix of tempPrefixes)if(!/^[A-Za-z0-9][A-Za-z0-9_-]+-$/.test(prefix))throw new Error('Signal file audit needs literal scratch prefixes');
  for(const name of fs.readdirSync(tempRoot).sort())if(tempPrefixes.some(prefix=>name.startsWith(prefix)))visit(path.join(tempRoot,name),'scratch:'+name);
  return entries;
}
function differences(before,after) {
  return {added:[...after.keys()].filter(key=>!before.has(key)).length,
    removed:[...before.keys()].filter(key=>!after.has(key)).length,
    changed:[...before.keys()].filter(key=>after.has(key)&&before.get(key)!==after.get(key)).length};
}
function scratchPrefixes(source) {
  return [...new Set(['carlos-playwright-sql-','carlos-annotation-fixture-',...Array.from(source.matchAll(/mkdtempSync\(path\.join\(os\.tmpdir\(\),\s*['"]([A-Za-z0-9_-]+-)['"]/g),match=>match[1])])];
}
module.exports={snapshot,differences,scratchPrefixes};
