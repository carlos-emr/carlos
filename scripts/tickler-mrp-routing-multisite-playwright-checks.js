#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const {runWorkflow}=require('./lib/workflow-session');
const {workflow,preflightConfig}=require('./tickler-mrp-routing-playwright-checks');
if(require.main===module)runWorkflow('tickler-mrp-routing-multisite',
  session=>workflow({...session,requireMultisite:true}),{openMaster:false,preflightConfig});
