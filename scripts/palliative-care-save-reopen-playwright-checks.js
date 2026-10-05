#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Focused regression for #4260; the full seven-form suite remains independently available.
// Reuse every Palliative Care persistence/redisplay/revision assertion and force the saved
// entry beyond the navbar's initial list using owned aliases of the same form table.
const { runWorkflow } = require('./lib/workflow-session');
const { workflow, FORMS } = require('./clinical-forms-save-reopen-playwright-checks');

if (require.main === module) runWorkflow('palliative-care-save-reopen', s => workflow(s, {
  forms: FORMS.filter(form => form.key === 'PC'), foldSavedForms: true,
}));
