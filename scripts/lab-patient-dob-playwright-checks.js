#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Exercise the installed lab patient-search form and its real prepared query.
// Only the runWorkflow-owned FAKE patient is changed; it is removed on exit.
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

async function workflow(s) {
  const page = await s.context.newPage();
  await page.goto(h.appUrl(s.config.baseUrl, '/oscarMDS/ViewPatientSearch'));
  for (const day of ['01', '02', '03', '04', '05', '06', '07', '08', '09', '15']) {
    await s.step(`DOB search finds the owned patient born on day ${day}`, async () => {
      s.sql.execute(`UPDATE demographic SET year_of_birth='1980', month_of_birth='07', date_of_birth=${h.sqlString(day)}
        WHERE demographic_no=${s.patient} AND last_name=${h.sqlString(s.marker)}`);
      await page.locator('#search_dob').check();
      await page.locator('#keyword').fill(`1980-07-${day}`);
      await Promise.all([
        page.waitForNavigation(),
        page.locator('#keyword').press('Enter'),
      ]);
      const row = page.locator('#patientsTable tbody tr').filter({ hasText: s.marker });
      h.assert(await row.count() === 1, `DOB day ${day}: the owned patient was omitted`);
      h.assert((await row.locator('td').first().innerText()).trim() === String(s.patient),
        'DOB search returned a different patient');
    });
  }
}

if (require.main === module) runWorkflow('lab-patient-dob', workflow, { openMaster: false });
module.exports = { workflow };
