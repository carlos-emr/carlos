#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Flyway V1.0.29 (2026.08.0-alpha14) renames the genesis placeholder clinic from the upstream
 * "McMaster Hospital" to "CARLOS Demo Clinic", guarded on the exact seeded value so a clinic
 * that already set its real name is left alone. The name is not decoration: the eForm AP
 * clinic_name and the Rich Text Letter letterhead print it on patient correspondence.
 *
 * This check proves, on the deployed database, that the migration is recorded as applied, that
 * no clinic row still carries the upstream placeholder, and that Administration > Clinic shows
 * the stored name exactly (so what an administrator sees is what letters print). Read-only.
 *
 * Environment: BASE_URL, CHROME_PATH, TEST_USER, TEST_PASSWORD, TEST_PIN, MYSQL_*.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');

async function workflow(s) {
  await s.step('migration V1.0.29 is applied and no clinic keeps the upstream placeholder name', async () => {
    const applied = s.sql.value("SELECT COUNT(*) FROM flyway_schema_history WHERE version='1.0.29' AND success=1");
    h.assert(applied === '1', `flyway_schema_history records ${applied} successful V1.0.29 rows, expected 1`);
    const stale = s.sql.value("SELECT COUNT(*) FROM clinic WHERE clinic_name='McMaster Hospital'");
    h.assert(stale === '0', `${stale} clinic row(s) still carry the upstream placeholder name after V1.0.29`);
  });
  await s.step('Administration > Clinic shows the stored clinic name', async () => {
    const stored = s.sql.value('SELECT clinic_name FROM clinic ORDER BY clinic_no LIMIT 1');
    h.assert(stored, 'The install has no clinic row to display');
    const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
      { context: s.context, recorder: s.recorder, label: 'clinic-admin' });
    const link = admin.locator('a[rel$="/admin/ManageClinic"], a[href$="/admin/ManageClinic"]');
    await link.first().waitFor({ state: 'attached' });
    const panel = link.locator('xpath=ancestor::div[contains(@class,"accordion-collapse")][1]');
    if (await panel.count() && !await panel.isVisible()) await admin.locator(`[data-bs-target="#${await panel.getAttribute('id')}"]`).click();
    await link.first().click();
    const frame = await (await admin.locator('#dynamic-content iframe').elementHandle()).contentFrame();
    h.assert(frame, 'Clinic administration did not open in the Administration iframe');
    const field = frame.locator('#clinic\\.clinicName');
    await field.waitFor({ state: 'visible' });
    const shown = await field.inputValue();
    h.assert(shown === stored, `Administration > Clinic shows "${shown}" but the database stores "${stored}"`);
    h.assert(shown !== 'McMaster Hospital', 'Administration > Clinic still shows the upstream placeholder clinic');
    await admin.close();
  });
}

if (require.main === module) runWorkflow('clinic-demo-name', workflow, { openPatient: false });
module.exports = { workflow };
