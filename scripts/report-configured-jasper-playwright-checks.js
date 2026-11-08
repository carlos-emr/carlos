#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Explicitly installed synthetic override only. This never counts as bundled-template evidence.
const { runWorkflow } = require('./lib/workflow-session');
const { overrideWorkflow, preflight } = require('./report-fixed-jasper-playwright-checks');

if (require.main === module) runWorkflow('report-configured-jasper', overrideWorkflow, {
  openPatient: false,
  openMaster: false,
  preflight: () => preflight('override'),
});
module.exports = { workflow: overrideWorkflow };
