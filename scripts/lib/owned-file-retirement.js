/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const fs=require('node:fs'),path=require('node:path');
const {assert}=require('./playwright-harness');
function readRegular(file) {
  let fd;
  try {fd=fs.openSync(file,fs.constants.O_RDONLY|fs.constants.O_NOFOLLOW);}
  catch(error){if(error.code==='ENOENT')return null;throw error;}
  try {const stat=fs.fstatSync(fd);assert(stat.isFile(),'Owned file must be regular, not a symlink or directory');return {stat,bytes:fs.readFileSync(fd)};}
  finally {fs.closeSync(fd);}
}
// Call only after all writers have drained. Rename the directory entry into a
// private directory before the final identity check, so a replacement cannot be
// unlinked between verification and removal. Preserve unexpected entries.
function retireOwnedFile(file,accept) {
  const observed=readRegular(file);if(!observed)return;
  assert(accept(observed.bytes),'Owned file changed; retain it for recovery');
  const quarantine=fs.mkdtempSync(path.join(path.dirname(file),'.carlos-retire-'));
  fs.chmodSync(quarantine,0o700);const held=path.join(quarantine,'entry');let moved=false;
  try {
    fs.renameSync(file,held);moved=true;
    const current=readRegular(held);
    assert(current&&current.stat.dev===observed.stat.dev&&current.stat.ino===observed.stat.ino&&accept(current.bytes),'Owned file changed during retirement; retain it for recovery');
    fs.unlinkSync(held);moved=false;
  } catch(error) {
    if(moved) {
      // link is exclusive: never overwrite a concurrently created original name.
      try {fs.linkSync(held,file);fs.unlinkSync(held);moved=false;}
      catch {throw new Error(`Owned file retirement failed; unexpected entry retained in ${quarantine}`);}
    }
    throw error;
  } finally {if(!moved)fs.rmdirSync(quarantine);}
}
module.exports={readRegular,retireOwnedFile};
