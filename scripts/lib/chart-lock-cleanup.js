/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const { appUrl, assert } = require('./playwright-harness');

// Browser/context.close() can terminate Chromium before a chart's pagehide
// beacon reaches the server. Await the same authenticated release operation
// before tearing down these test charts. Never force another lock,
// delete drafts, or use this helper to test the application's unload behaviour.
async function releaseChartLocks(context, baseUrl) {
  const endpoint = appUrl(baseUrl, '/CaseManagementEntry');
  const appRoot = endpoint.slice(0, endpoint.lastIndexOf('/') + 1);
  for (const page of context.pages()) {
    if (page.isClosed() || !page.url().startsWith(appRoot)) continue;
    const form = await page.evaluate(() => {
      const chart = document.forms.caseManagementEntryForm;
      if (!chart || !chart.elements.noteId || globalThis.needToReleaseLock === false) return null;
      return {
        method: 'releaseNoteLock',
        demographicNo: String(globalThis.demographicNo),
        noteId: chart.elements.noteId.value,
        'CSRF-TOKEN': globalThis.CarlosAjax?.getCsrfToken(),
      };
    });
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
