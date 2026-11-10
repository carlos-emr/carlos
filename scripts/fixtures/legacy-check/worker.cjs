/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const fs=require('node:fs'),path=require('node:path');
const {runLegacyCheck,sanitizedError}=require('../../lib/legacy-check');
const directory=process.env.LEGACY_CHECK_FIXTURE_DIR;
runLegacyCheck('legacy-fixture',async({cancellation})=>{
  try {
    fs.writeFileSync(path.join(directory,'ready'),'yes');
    // Model an accepted write: cancellation must drain it before cleanup.
    await new Promise(resolve=>setTimeout(()=>{
      fs.writeFileSync(path.join(directory,'settled'),'yes');resolve();
    },100));
    await cancellation.throwIfCancelled();
    if(process.env.LEGACY_CHECK_RESULT)process.exitCode=Number(process.env.LEGACY_CHECK_RESULT);
    fs.writeFileSync(path.join(directory,'continued'),'yes');
  } catch(error) {
    throw sanitizedError(error,cancellation.isCancellation);
  } finally {
    if(!fs.existsSync(path.join(directory,'settled')))throw new Error('Cleanup raced the accepted write');
    fs.writeFileSync(path.join(directory,'cleaned'),'yes');
  }
},{realSignalCheckpoints:process.env.LEGACY_CHECK_OS_CHECKPOINTS==='true'});
