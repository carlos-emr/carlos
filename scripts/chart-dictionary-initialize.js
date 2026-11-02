/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
// CAISI's first chart visit installs shared definitions. Do that before the
// Issue Editor check's residue baseline, without opening an unrelated form.
const {runWorkflow}=require('./lib/workflow-session');
async function workflow(s){await s.step('native chart initializes the selected module definitions',()=>s.chart());}
if(require.main===module)require('./lib/property-profiles').verifyActiveProfile('caisi-admin').then(
  ()=>runWorkflow('chart-dictionary-initialize',workflow),error=>{console.error(error.message);process.exitCode=1;});
module.exports={workflow};
