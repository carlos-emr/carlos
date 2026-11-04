#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Focused flowsheet editor regression: add patient customizations, open the editor,
// verify the served JSP sets a numeric tracker height, and save warning/target rules.
// Reuses the full workflow's owned patient, database assertions, and cleanup.
const { runWorkflow } = require('./lib/workflow-session');
const { workflow } = require('./flowsheet-patient-customization-playwright-checks');
if (require.main === module) runWorkflow('flowsheet-editor-height', s => workflow(s, { editorOnly: true }));
