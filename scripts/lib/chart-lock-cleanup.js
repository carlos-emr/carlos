/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const { appUrl, assert } = require('./playwright-harness');

function navigationContextError(error) {
  return /Execution context was destroyed(?:, most likely because of a navigation)?|Cannot find context with specified id|Target page, context or browser has been closed/.test(error?.message || '');
}

async function readChartLockForm(page, appRoot) {
  // A closing legacy popup can reload its opener during this read. Allow one
  // settled-document re-read; a live page that keeps navigating must fail cleanup.
  for (let attempt = 0; attempt < 2; attempt++) {
    if (page.isClosed() || !page.url().startsWith(appRoot)) return null;
    try {
      return await page.evaluate(expectedRoot => {
        // Recheck inside the same document as the form, in case navigation won
        // the race between the outer URL check and execution of this callback.
        if (!globalThis.location.href.startsWith(expectedRoot)) return null;
        const chart = document.forms.caseManagementEntryForm;
        // This is final teardown, not the print-triggered pagehide suppression.
        if (!chart || !chart.elements.noteId) return null;
        return {
          method: 'releaseNoteLock',
          demographicNo: String(globalThis.demographicNo),
          noteId: chart.elements.noteId.value,
          'CSRF-TOKEN': globalThis.CarlosAjax?.getCsrfToken(),
        };
      }, appRoot);
    } catch (error) {
      if (!navigationContextError(error)) throw error;
      if (page.isClosed()) return null;
      if (attempt) throw error;
      try {
        await page.waitForLoadState('domcontentloaded', {timeout: 20000});
      } catch (waitError) {
        if (navigationContextError(waitError) && page.isClosed()) return null;
        throw waitError;
      }
    }
  }
}

// Browser/context.close() can terminate Chromium before a chart's pagehide
// beacon reaches the server. Await the same authenticated release operation
// before tearing down these test charts. Never force another lock,
// delete drafts, or use this helper to test the application's unload behaviour.
async function releaseChartLocks(context, baseUrl, pages = context.pages()) {
  const endpoint = appUrl(baseUrl, '/CaseManagementEntry');
  const appRoot = endpoint.slice(0, endpoint.lastIndexOf('/') + 1);
  for (const page of pages) {
    const form = await readChartLockForm(page, appRoot);
    if (!form) continue;
    assert(/^\d+$/.test(form.demographicNo) && /^-?\d+$/.test(form.noteId)
      && form['CSRF-TOKEN'], 'Chart lock cleanup requires the chart identity and CSRF token');
    const response = await context.request.post(endpoint, { form, maxRedirects: 0 });
    try {
      assert(response.status() === 200, `Chart lock cleanup returned HTTP ${response.status()}`);
    } finally {
      await response.dispose();
    }
  }
}

async function closeBrowserWithChartCleanup(browser, baseUrl) {
  try {
    for (const context of browser.contexts()) await releaseChartLocks(context, baseUrl);
  } finally {
    await browser.close();
  }
}

module.exports = { releaseChartLocks, closeBrowserWithChartCleanup };
