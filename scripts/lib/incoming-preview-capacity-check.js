/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const {assert} = require('./playwright-harness');

/** Exercise both native incoming-preview modes using the workflow's owned PDF. */
async function checkIncomingPreviewCapacity(session, page, ownedName) {
  const pattern = '**/documentManager/ManageDocument?*';
  const targets = new Set();
  let method, attempts;
  const matches = value => {
    const url = new URL(value);
    return url.pathname.endsWith('/documentManager/ManageDocument')
      && url.searchParams.get('method') === method && url.searchParams.get('pdfName') === ownedName;
  };
  // Match the server's GET503 contract; production markup/encoding is separately
  // verified by ManageDocument action tests. The script and final bytes are real.
  const scriptPath = new URL(`${session.config.baseUrl}/js/incomingDocumentCapacityWait.js`).pathname;
  const escapedPath = scriptPath.replace(/[&<>"']/g, char => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[char]));
  const busyHtml = '<!doctype html><html><body><p id="incomingDocumentCapacityWait" role="status">Waiting for document capacity.</p>'
    + '<script src="' + escapedPath + '"></script></body></html>';
  const routeHandler = async route => {
    if (!matches(route.request().url())) return route.continue();
    assert(route.request().method() === 'GET', 'incoming preview retry attempted a mutation');
    attempts++;
    if (attempts > 5) return route.continue();
    targets.add(route.request().url());
    return route.fulfill({status: 503, contentType: 'text/html', headers: {'Retry-After': '1', 'Cache-Control': 'no-store'}, body: busyHtml});
  };
  await page.route(pattern, routeHandler);
  try {
    for (const [mode, suffix, contentType] of [['Pdf', 'Pdf', 'application/pdf'], ['Image', 'Image', 'image/png']]) {
      method = `viewIncomingDocPageAs${suffix}`; attempts = 0;
      const ready = page.waitForResponse(response => matches(response.url()) && response.status() === 200
        && response.headers()['content-type']?.startsWith(contentType), {timeout: 120000});
      ready.catch(() => {}); // keep a failed UI action from leaving an unhandled waiter
      await Promise.all([page.waitForEvent('domcontentloaded', {timeout: 120000}), page.locator('#imageTypeList').selectOption(mode)]);
      await page.frameLocator('#docdisp iframe').locator('#incomingDocumentCapacityWait').waitFor({timeout: 120000});
      assert(await page.frameLocator('#docdisp iframe').locator('#incomingDocumentCapacityWait').getAttribute('role') === 'status',
        'incoming capacity wait must be accessible inside the preview');
      const response = await ready;
      let timer;
      const bytes = await Promise.race([response.body(), new Promise((_, reject) => {
        timer = setTimeout(() => reject(new Error(`${mode} preview body did not finish`)), 120000);
      })]).finally(() => clearTimeout(timer));
      assert(bytes.length > 100, `${mode} recovered to an empty preview`);
      assert(attempts === 6, `${mode} stopped waiting or did not recover after five capacity responses`);
      if (mode === 'Pdf') assert(bytes.subarray(0, 5).toString() === '%PDF-', 'recovered PDF body is not a PDF');
      else assert(bytes.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])), 'recovered image body is not PNG');
    }
    // Only responses deliberately injected and subsequently recovered are expected.
    session.recorder.badResponses = session.recorder.badResponses.filter(entry => !(entry.status === 503 && targets.has(entry.url)));
    session.recorder.consoleIssues = session.recorder.consoleIssues.filter(entry => !(targets.has(entry.location?.url)
      && /Failed to load resource:.*503/.test(entry.text)));
  } finally { await page.unroute(pattern, routeHandler); }
}
module.exports = {checkIncomingPreviewCapacity};
