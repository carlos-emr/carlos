/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Modifications by CARLOS Contributors, 2026.
// One-time environment initializer for the Vascular Tracker browser checks
// (clinical-forms-save-reopen-vascular-tracker, -save and -script).
//
// The first time anyone opens Vascular Tracker, FrmSetupForm2Action creates the form's
// clinic-wide measurementType (and validations) rows from form/VTForm.xml. Those checks refuse
// to start until the rows exist (requireVascularDictionaryReady), so that this one-off creation
// never lands inside a suite run's residue window and is never deleted by a check's cleanup.
// Run this once against a disposable install before the suite; scripts/deb-docker-validation.sh
// does so after the first-login reset.
//
// It opens the shipped Vascular Tracker route for its own synthetic patient, which runWorkflow
// then removes. Only the dictionary rows stay, exactly as after a clinician's first visit. Run
// again it is a no-op apart from that visit: the application adds only missing definitions.
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { requireVascularDictionaryReady } = require('./clinical-forms-save-reopen-playwright-checks');

async function workflow(s) {
  // E-Chart also creates shared default definitions on its first visit. Do that
  // in environment setup, before any clinical check takes its residue baseline.
  await s.step('opening E-Chart once initializes its shared measurement definitions', async () => {
    await s.chart();
  });
  await s.step('opening Vascular Tracker once creates its measurement dictionary', async () => {
    const page = await s.context.newPage();
    const base = s.config.baseUrl.href.endsWith('/') ? s.config.baseUrl.href : `${s.config.baseUrl.href}/`;
    const url = new URL(`form/SetupForm?formName=VTForm&demographic_no=${encodeURIComponent(s.patient)}`, base);
    const response = await page.goto(url.href, { waitUntil: 'load' });
    h.assert(response && response.status() === 200, `Vascular Tracker setup answered ${response ? response.status() : 'nothing'}`);
    await page.close();
    requireVascularDictionaryReady(s.sql);
  });
}

if (require.main === module) runWorkflow('vascular-dictionary-initialize', workflow);
module.exports = { workflow };
