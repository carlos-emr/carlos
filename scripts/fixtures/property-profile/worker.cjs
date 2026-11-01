/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const fs=require('node:fs'),path=require('node:path');
process.on('SIGTERM',()=>{
  fs.writeFileSync(path.join(process.env.PROFILE_TEST_DIR,'cleaned'),'yes');
  process.exit(0);
});
require('../../lib/property-profiles').verifyActiveProfile('address-lock',{file:path.join(process.env.PROFILE_TEST_DIR,'properties')}).then(()=>{
  fs.writeFileSync(path.join(process.env.PROFILE_TEST_DIR,'ready'),String(process.pid));
  setInterval(()=>{},1000);
},error=>{console.error(error.message);process.exitCode=1;});
