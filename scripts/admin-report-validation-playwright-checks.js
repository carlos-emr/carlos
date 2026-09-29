#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Exercise report validation after another administration fragment replaces
// global jQuery. Opening a report in a fresh shell alone misses this regression.
const { assert } = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');

async function workflow(s) {
  const { page: admin } = await clickOpensPopupOrNavigates(s.schedule,
    s.schedule.locator('#admin-panel, #admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'report-admin' });
  const clickMenu = async selector => {
    const link = admin.locator(selector).first();
    const panel = link.locator('xpath=ancestor::div[contains(@class,"accordion-collapse")][1]');
    if (await panel.count()) {
      const id = await panel.getAttribute('id');
      assert(id, 'Administration accordion panel has no id');
      // Opening another accordion panel can leave this one briefly visible
      // while Bootstrap is closing it. Wait for that transition before deciding
      // whether to reopen it; otherwise the link becomes permanently hidden.
      await admin.waitForFunction(panelId => {
        const element = document.getElementById(panelId);
        return element && !element.classList.contains('collapsing');
      }, id);
      if (!await panel.isVisible()) {
        await admin.locator(`[data-bs-target="#${id}"]`).click();
      }
    }
    await link.click();
  };
  await s.step('eForm navigation cannot remove Visit Report validation', async () => {
    await clickMenu('a.defaultForms');
    await admin.frameLocator('#uploadFrame').locator('form').first().waitFor({ state: 'visible' });
    await clickMenu('a.defaultvisitreport');
    await admin.locator('#visitForm').waitFor({ state: 'visible' });
    await admin.waitForFunction(() => !!window.jQuery('#visitForm').data('validator'));
    assert(await admin.evaluate(() => typeof window.jQuery.validator.methods.oscarDate === 'function'),
      'Visit Report lost its date validation method');
  });
  await s.step('another eForm navigation preserves required Overnight Batch validation', async () => {
    await clickMenu('a.defaultForms');
    await admin.frameLocator('#uploadFrame').locator('form').first().waitFor({ state: 'visible' });
    await clickMenu('a[href$="/oscarReport/obec"]');
    await admin.locator('#obecForm').waitFor({ state: 'visible' });
    await admin.waitForFunction(() => !!window.jQuery('#obecForm').data('validator'));
    let submissions = 0;
    const record = request => {
      // The legacy form defaults to GET; guard every method, not just POST.
      if (new URL(request.url()).pathname.endsWith('/oscarReport/obec')) submissions++;
    };
    s.context.on('request', record);
    try {
      await admin.locator('#obecForm [name="numDays"]').fill('');
      await admin.locator('#obecForm input[type="submit"]').click();
      await admin.locator('#numDays-error').waitFor({ state: 'visible' });
      assert(submissions === 0, 'An invalid report was submitted despite the required field');
    } finally {
      s.context.off('request', record);
    }
  });
}
if (require.main === module) runWorkflow('admin-report-validation', workflow, { openPatient: false });
module.exports = { workflow };
