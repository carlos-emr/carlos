#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Compatibility-deployment check: requires AbandonOldChart=false. The core soft-wrap
// workflow runs separately with the modern chart enabled. Both own their fixtures.
const { runWorkflow } = require('./lib/workflow-session');
const { workflow } = require('./echart-note-soft-wrap-playwright-checks');
if (require.main === module) {
  runWorkflow('encounter-legacy-note-soft-wrap', session => workflow(session, { legacyOnly: true }));
}
