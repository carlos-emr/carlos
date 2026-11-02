/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const fs=require('node:fs'),path=require('node:path');
const h=require('./playwright-harness');
const {readRegular,retireOwnedFile}=require('./owned-file-retirement');
function ownedUploadedImage(directory,filename,bytes) {
  h.assert(path.isAbsolute(directory)&&/^FAKE-PW[0-9a-f]{16}-[A-Za-z0-9_.-]+$/.test(filename)&&Buffer.isBuffer(bytes),'Invalid owned image upload intent');
  const root=fs.realpathSync(directory);h.assert(fs.statSync(root).isDirectory(),'Image store is not a directory');
  const file=path.join(root,filename);
  function stat(){try{return fs.lstatSync(file);}catch(error){if(error.code==='ENOENT')return null;throw error;}}
  h.assert(stat()===null,'Owned image filename already exists; refusing to overwrite it');
  function verify(){let entry;try{entry=readRegular(file);}catch{throw new Error('Uploaded image changed; retain it for recovery');}h.assert(entry&&entry.bytes.equals(bytes),'Uploaded image changed; retain it for recovery');}
  return {verify,cleanup({browserClosed=false}={}) {
    h.assert(browserClosed,'Browser shutdown is unconfirmed; retain the uploaded image');
    if(stat()===null)return;verify();retireOwnedFile(file,current=>current.equals(bytes));
  }};
}
module.exports={ownedUploadedImage};
