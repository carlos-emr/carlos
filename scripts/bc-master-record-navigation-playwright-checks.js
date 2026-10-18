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
const { assertPopupDestinations } = require('./page-health-master-record-playwright-checks');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');
const { releaseChartLocks } = require('./lib/chart-lock-cleanup');
const { masterRecordLink } = require('./lib/master-record-link');
const VIEWPORTS = [{ width: 1280, height: 720 }, { width: 1600, height: 1000 }];
const TIMEOUT = 30000;
const ELIGIBILITY_FRAGMENT = 'Failure-claim &lt;unsafe&gt; &amp; &quot;quoted&quot;<br>'
  + '<span style="color:red; font-weight:bold; background:url(/eligibility-fragment-probe)"'
  + ' id="eligibility-injected-id" onmouseover="globalThis.eligibilityInjected=true">ELIG_ON_DOS: no</span><br>'
  + 'Last line 🩺'
  + '<script>globalThis.eligibilityInjected=true</script>'
  + '<img src="/eligibility-fragment-probe" onerror="globalThis.eligibilityInjected=true">'
  + '<iframe src="/eligibility-fragment-probe"></iframe>'
  + '<svg onload="globalThis.eligibilityInjected=true"><foreignObject><span>foreign markup</span></foreignObject></svg>'
  + '<style>body{display:none}</style><a href="javascript:globalThis.eligibilityInjected=true">unsafe link</a>'
  + '<math><mtext><table><mglyph><style><!--</style><img title="--><img src=/eligibility-fragment-probe onerror=globalThis.eligibilityInjected=true>">';
const ELIGIBILITY_ERROR = '-Error in teleplan connection : Test &lt;connection&gt; &amp; retry';

async function assertEligibilityFragment(page) {
  const result = page.locator('#returnTeleplanMsg');
  await page.waitForFunction(() => document.getElementById('returnTeleplanMsg').textContent.includes('Last line 🩺'));
  h.assert(await result.textContent() === 'Failure-claim <unsafe> & "quoted"ELIG_ON_DOS: noLast line 🩺',
    'Eligibility lost or changed the decoded message lines');
  h.assert(await result.locator('br').count() === 2, 'Eligibility must preserve every line break');
  h.assert(await result.locator('span').count() === 1, 'Eligibility must preserve the denial highlight');
  h.assert(await result.locator('span').evaluate(span => span.style.color === 'red'
    && span.style.fontWeight === 'bold' && span.attributes.length === 1),
  'Eligibility must rebuild the fixed highlight without response attributes');
  h.assert(await result.locator('*:not(br):not(span)').count() === 0, 'Eligibility adopted unsupported markup');
  h.assert(await result.locator('[id], [onmouseover], [src], [href]').count() === 0,
    'Eligibility copied response attributes');
  await result.locator('span').hover();
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
  h.assert(!await page.evaluate(() => globalThis.eligibilityInjected), 'Eligibility executed response markup');
}

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
  let resourceRequests = 0;
  const pendingResponses = [];
  await context.route('**/eligibility-fragment-probe**', async route => {
    resourceRequests += 1;
    await route.fulfill({ status: 204, body: '' });
  });
  // Install before login, so even a regression that checks eligibility on hover cannot
  // send a real request. Holding the response proves the menu opens while loading.
  await context.route('**/billing/CA/BC/ManageTeleplan**', async route => {
    const responseIndex = requests++;
    await new Promise(resolve => { pendingResponses.push(resolve); });
    await route.fulfill({ contentType: 'text/html', body: responseIndex === 0 ? ELIGIBILITY_FRAGMENT : ELIGIBILITY_ERROR });
  });
  try {
    const schedule = await h.login(context, config, recorder);
    const { masterPage } = await openMasterRecord(context, schedule, recorder, {
      searchTerm: process.env.BC_NAV_SEARCH || 'FAKE-',
      preferredDemographicNo: process.env.BC_NAV_DEMOGRAPHIC_NO || '2', timeout: TIMEOUT,
    });
    await assertPopupDestinations(masterPage);
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

    const chartLink = masterRecordLink(masterPage, 'popupEChart').first();
    // The Invoice List link is a plain href here (its onclick only reads this.href), outside any .patient-nav.
    const invoiceLink = masterPage.locator('a[href*="/billing/CA/BC/reprocessBill"]').first();
    for (const link of [chartLink, invoiceLink]) await assertCenterIsClickable(link);
    const invoice = await ui.clickOpensPopup(masterPage, invoiceLink, {
      context, recorder, label: 'bc-invoices', timeout: TIMEOUT,
    });
    h.assert(new URL(invoice.url()).pathname.endsWith('/billing/CA/BC/reprocessBill'),
      'The centred Invoice List click did not open BC invoices');
    await invoice.close();

    await assertCenterIsClickable(trigger);
    const [eligibilityRequest] = await Promise.all([
      masterPage.waitForRequest(request => request.url().includes('/billing/CA/BC/ManageTeleplan'), { timeout: TIMEOUT }),
      trigger.click(),
    ]);
    // Inspect the intercepted browser request, including the real CSRFGuard XHR hook.
    // The server's verb guard is covered separately with mocked Teleplan dependencies.
    h.assert(eligibilityRequest.method() === 'POST', 'Eligibility must POST to the guarded action');
    const headers = await eligibilityRequest.allHeaders();
    h.assert(headers['content-type']?.startsWith('application/x-www-form-urlencoded'),
      'Eligibility parameters must be form encoded');
    const parameters = new URLSearchParams(eligibilityRequest.postData() || '');
    h.assert(parameters.getAll('method').length === 1 && parameters.get('method') === 'checkElig',
      'Eligibility POST must contain method=checkElig');
    const demographic = await masterPage.locator('#menu2').getAttribute('data-demographic');
    h.assert(demographic && parameters.getAll('demographic').length === 1 && parameters.get('demographic') === demographic,
      'Eligibility POST must contain the displayed patient demographic');
    h.assert(Boolean(headers['csrf-token']) && !headers['csrf-token'].includes(','),
      'The existing CSRFGuard XHR hook must supply one CSRF token header');
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
    await assertEligibilityFragment(masterPage);
    h.assert(resourceRequests === 0, 'Parsing eligibility must not load resources from the response');
    h.assert(!await masterPage.locator('#search_spinner').isVisible(), 'Loading must end after the response');
    await dialog.locator('.ui-dialog-titlebar-close').click();
    await dialog.waitFor({ state: 'hidden', timeout: TIMEOUT });
    h.assert(await trigger.getAttribute('aria-expanded') === 'false', 'Closed eligibility must report collapsed');

    await Promise.all([
      masterPage.waitForRequest(request => request.url().includes('/billing/CA/BC/ManageTeleplan'), { timeout: TIMEOUT }),
      trigger.click(),
    ]);
    h.assert(requests === 2, 'Explicit reopening after completion must check eligibility again');
    pendingResponses[1]();
    await masterPage.waitForFunction(() => document.getElementById('returnTeleplanMsg').textContent
      === '-Error in teleplan connection : Test <connection> & retry');
    h.assert(await masterPage.locator('#returnTeleplanMsg *').count() === 0, 'Connection errors must remain plain text');
    await dialog.locator('.ui-dialog-titlebar-close').click();
    await dialog.waitFor({ state: 'hidden', timeout: TIMEOUT });

    // Prove the dialog has not left an invisible overlay over the navigation.
    await assertCenterIsClickable(chartLink);
    await assertCenterIsClickable(invoiceLink);
    const { page: chart } = await ui.clickOpensPopupOrNavigates(masterPage, chartLink, {
      context, recorder, label: 'bc-chart', timeout: TIMEOUT,
    });
    await chart.locator('#header #patient-label').waitFor({ state: 'visible', timeout: TIMEOUT });
    h.assert(requests === 2, 'Navigating away must not trigger eligibility');
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
  return 'BC navigation centre clicks, eligibility POST parameters and CSRF header verified at 1280x720 and 1600x1000; eligibility response stubbed, no external integration tested';
}
if (require.main === module) h.runCheck({ name: 'bc-master-record-navigation', run: main });
module.exports = { VIEWPORTS, ELIGIBILITY_FRAGMENT, ELIGIBILITY_ERROR, assertEligibilityFragment,
  assertCenterIsClickable, checkViewport, main };
