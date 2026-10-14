#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * #4544: the BC-only inline 350px menu widened navigation links under the content pane.
 * Uses real centre clicks (no force or offset) at both reported viewports. Eligibility
 * responses are intercepted in the browser BEFORE reaching CARLOS: no Teleplan call is
 * made and this is not a Teleplan integration test. All other pages are the running app.
 * Requires a BC install and the usual harness credentials. BC_NAV_SEARCH and
 * BC_NAV_DEMOGRAPHIC_NO select a synthetic patient (defaults FAKE- and 2).
 */
'use strict';
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');
const { releaseChartLocks } = require('./lib/chart-lock-cleanup');
const VIEWPORTS = [{ width: 1280, height: 720 }, { width: 1600, height: 1000 }];
const TIMEOUT = 30000;

async function assertCenterIsClickable(link) {
  await link.scrollIntoViewIfNeeded();
  h.assert(await link.evaluate(element => {
    const rect = element.getBoundingClientRect();
    const hit = document.elementFromPoint(rect.x + rect.width / 2, rect.y + rect.height / 2);
    return rect.width > 0 && rect.height > 0 && (hit === element || element.contains(hit));
  }), 'The navigation link centre is covered by another element');
}

async function checkViewport(browser, config, viewport) {
  const context = await h.newContext(browser, config, { viewport });
  const recorder = h.createRecorder();
  let requests = 0;
  const pendingResponses = [];
  // Install before login, so even a regression that checks eligibility on hover cannot
  // send a real request. Holding the response proves the menu opens while loading.
  await context.route('**/billing/CA/BC/ManageTeleplan**', async route => {
    requests += 1;
    const url = new URL(route.request().url());
    h.assert(url.searchParams.get('method') === 'checkElig', 'Unexpected eligibility request');
    await new Promise(resolve => { pendingResponses.push(resolve); });
    await route.fulfill({ contentType: 'text/html', body: '<p id="eligibility-test-result">Eligibility test response</p>' });
  });
  try {
    const schedule = await h.login(context, config, recorder);
    const { masterPage } = await openMasterRecord(context, schedule, recorder, {
      searchTerm: process.env.BC_NAV_SEARCH || 'FAKE-',
      preferredDemographicNo: process.env.BC_NAV_DEMOGRAPHIC_NO || '2', timeout: TIMEOUT,
    });
    // Popup feature strings can override the context viewport.
    await masterPage.setViewportSize(viewport);
    const trigger = masterPage.locator('#checkEligibility');
    h.assert(await trigger.count() === 1, 'This check requires a BC master record with billing permissions');
    h.assert(!await masterPage.locator('#menu2').isVisible(), 'Eligibility must start hidden');
    h.assert(!await masterPage.locator('#search_spinner').isVisible(), 'Loading text leaked into navigation');
    await trigger.hover();
    await trigger.focus();
    // An animation frame gives mouse/focus handlers a turn without an arbitrary sleep.
    await masterPage.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
    h.assert(requests === 0, 'Hovering or focusing eligibility must not request Teleplan');

    const chartLink = masterPage.locator('a[onclick*="popupEChart"]').first();
    const invoiceLink = masterPage.locator('a[onclick*="/billing/CA/BC/reprocessBill"]').first();
    for (const link of [chartLink, invoiceLink]) await assertCenterIsClickable(link);
    const invoice = await ui.clickOpensPopup(masterPage, invoiceLink, {
      context, recorder, label: 'bc-invoices', timeout: TIMEOUT,
    });
    h.assert(new URL(invoice.url()).pathname.endsWith('/billing/CA/BC/reprocessBill'),
      'The centred Invoice List click did not open BC invoices');
    await invoice.close();

    await assertCenterIsClickable(trigger);
    await Promise.all([
      masterPage.waitForRequest(request => request.url().includes('/billing/CA/BC/ManageTeleplan'), { timeout: TIMEOUT }),
      trigger.click(),
    ]);
    const dialog = masterPage.locator('.ui-dialog').filter({ has: masterPage.locator('#menu2') });
    await dialog.waitFor({ state: 'visible', timeout: TIMEOUT });
    h.assert(requests === 1, 'Opening eligibility must send exactly one request');
    h.assert(await masterPage.locator('#search_spinner').isVisible(), 'The dialog must show pending status');
    await masterPage.keyboard.press('Escape');
    await dialog.waitFor({ state: 'hidden', timeout: TIMEOUT });
    h.assert(await trigger.evaluate(el => el === document.activeElement), 'Escape must return focus to eligibility');
    await trigger.press('Enter');
    await dialog.waitFor({ state: 'visible', timeout: TIMEOUT });
    h.assert(requests === 1, 'Reopening a pending eligibility check must not duplicate the request');
    pendingResponses[0]();
    await masterPage.locator('#eligibility-test-result').waitFor({ state: 'visible', timeout: TIMEOUT });
    h.assert(!await masterPage.locator('#search_spinner').isVisible(), 'Loading must end after the response');
    await dialog.locator('.ui-dialog-titlebar-close').click();
    await dialog.waitFor({ state: 'hidden', timeout: TIMEOUT });
    h.assert(await trigger.getAttribute('aria-expanded') === 'false', 'Closed eligibility must report collapsed');

    // Prove the dialog has not left an invisible overlay over the navigation.
    await assertCenterIsClickable(chartLink);
    await assertCenterIsClickable(invoiceLink);
    const { page: chart } = await ui.clickOpensPopupOrNavigates(masterPage, chartLink, {
      context, recorder, label: 'bc-chart', timeout: TIMEOUT,
    });
    await chart.locator('#header #patient-label').waitFor({ state: 'visible', timeout: TIMEOUT });
    h.assert(requests === 1, 'Navigating away must not trigger eligibility');
    h.assertStrictPage(recorder);
  } finally {
    pendingResponses.forEach(resolve => resolve());
    await context.unrouteAll({ behavior: 'wait' });
    try { await releaseChartLocks(context, config.baseUrl); }
    finally { await context.close(); }
  }
}

async function main() {
  const config = h.readConfig();
  const browser = await h.launchBrowser(config);
  try {
    for (const viewport of VIEWPORTS) await checkViewport(browser, config, viewport);
  } finally { await browser.close(); }
  return 'BC navigation centre clicks and explicit eligibility dialog verified at 1280x720 and 1600x1000; eligibility response stubbed, no external integration tested';
}
if (require.main === module) h.runCheck({ name: 'bc-master-record-navigation', run: main });
module.exports = { VIEWPORTS, assertCenterIsClickable, checkViewport, main };
