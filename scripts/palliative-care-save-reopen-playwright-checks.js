#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Focused regression for #4260; the full seven-form suite remains independently available.
// Reuse every Palliative Care persistence/redisplay/revision assertion and force the saved
// entry beyond the navbar's initial list using owned aliases of the same form table.
const { runWorkflow } = require('./lib/workflow-session');
const claims = require('./lib/form-claims');
const { workflow, FORMS } = require('./clinical-forms-save-reopen-playwright-checks');

const forms = FORMS.filter(form => form.key === 'PC');
// Every Palliative Care pair, named here rather than read from CLINICAL_FORMS_ONLY / CLINICAL_FORMS_EXCEPT: those
// select the entries of clinical-forms-save-reopen, and a value left exported in a shell must not narrow this check.
const select = forms.flatMap(form => form.concerns.map(concern => claims.claimKey(form.claim, concern)));

if (require.main === module) runWorkflow('palliative-care-save-reopen', s => workflow(s, {
  forms, foldSavedForms: true, select,
}));
module.exports = { select };
