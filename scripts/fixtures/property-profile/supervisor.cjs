/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const fs=require('node:fs'),path=require('node:path');
const {withPropertyProfile,runProfileChild}=require('../../lib/property-profiles');
const root=process.env.PROFILE_TEST_DIR;
withPropertyProfile('address-lock',({signal})=>runProfileChild([path.join(__dirname,'worker.cjs')],'address-lock',signal,{file:path.join(root,'properties'),env:{...process.env,CARLOS_DISPOSABLE_HOST:'true'}}),{
  env:{CARLOS_DISPOSABLE_HOST:'true'},file:path.join(root,'properties'),readRestarts:()=> '0',
  restart:()=>fs.appendFileSync(path.join(root,'restarts'),'restart\n'),
}).then(code=>{process.exitCode=code;},error=>{console.error(error.message);process.exitCode=20;});
