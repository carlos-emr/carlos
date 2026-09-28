/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const {assert, assertStrictPage, createRecorder, wireStrictPage} = require('./playwright-harness');

/** Real installed UI plus explicit capacity responses; the split POST is intercepted before filing. */
async function checkLegacyDocumentImages(context, config, documentId) {
  const recorder = createRecorder();
  const page = await context.newPage();
  page.setDefaultTimeout(120000); page.setDefaultNavigationTimeout(120000);
  wireStrictPage(page, 'legacy-document-capacity', recorder);
  const render = requestUrl => {
    const url = new URL(requestUrl);
    return url.pathname.endsWith('/documentManager/ManageDocument')
      && ['showPage', 'viewDocPage'].includes(url.searchParams.get('method'))
      && url.searchParams.get('doc_no') === String(documentId);
  };
  const capacityUrls = new Set();
  let injected = 0, phase = 'viewer';
  const pending = new Set(); let peak = 0, drainFinished;
  const finished = request => {
    pending.delete(request);
    if (pending.size === 0 && drainFinished) drainFinished();
  };
  page.on('request', request => {
    if (render(request.url())) {pending.add(request); peak = Math.max(peak, pending.size);}
  });
  page.on('requestfinished', finished); page.on('requestfailed', finished);
  await page.route('**/documentManager/ManageDocument?*', async route => {
    const url = new URL(route.request().url());
    if (render(url.href) && ((phase === 'viewer' && url.searchParams.get('curPage') === '2' && injected < 5)
      || (phase === 'split' && url.searchParams.get('curPage') === '1'))) {
      injected++; capacityUrls.add(url.href);
      await route.fulfill({status: 503, headers: {'Retry-After': '1', 'Content-Type': 'text/plain'}, body: 'Document capacity is busy'});
    } else await route.continue();
  });
  let originalError;
  try {
    await page.goto(`${config.baseUrl}/documentManager/ViewShowDocument?segmentID=${documentId}&inWindow=true`);
    const image = page.locator(`#docImg_${documentId}`);
    await page.waitForFunction(id => {
      const img = document.getElementById(`docImg_${id}`);
      return img?.getAttribute('data-document-image-state') === 'loaded' && img.complete && img.naturalWidth > 0;
    }, documentId);
    await page.locator(`#nextP_${documentId}`).click();
    await page.getByText('Waiting for document capacity. This image will load automatically.', {exact: true}).waitFor();
    await page.waitForFunction(id => {
      const img = document.getElementById(`docImg_${id}`);
      return img?.getAttribute('data-document-image-state') === 'loaded' && img.complete && img.naturalWidth > 0
        && new URL(img.getAttribute('data-document-image-src')).searchParams.get('curPage') === '2';
    }, documentId);
    assert(injected === 5, 'legacy image must recover after more than three capacity responses');
    assert(await image.isVisible(), 'recovered preview must be visible');
    assert(pending.size === 0, 'viewer must finish accepted image work before navigation');

    phase = 'split';
    await page.goto(`${config.baseUrl}/oscarMDS/ViewSplit?document=${documentId}&queueID=0&demoName=`);
    const first = page.locator('#picker li').nth(0);
    const second = page.locator('#picker li').nth(1);
    await first.getByText('Waiting for document capacity. This image will load automatically.', {exact: true}).waitFor();
    await second.locator('img.page').scrollIntoViewIfNeeded();
    await page.waitForFunction(() => {
      const img = document.querySelectorAll('#picker img.page')[1];
      return img?.complete && img.naturalWidth > 0;
    });
    const blob = await second.locator('img.page').getAttribute('src');
    assert(blob.startsWith('blob:'), 'split must render the bounded loader resource');
    await second.locator('div').first().click();
    await page.locator('#tool_rotate').click();
    await page.locator('#tool_rotate').click();
    const hasPixels = await second.locator('canvas').evaluate(canvas => {
      const pixels = canvas.getContext('2d').getImageData(0, 0, canvas.width, canvas.height).data;
      return canvas.angle === 180 && pixels.some((value, index) => index % 4 === 3 && value > 0);
    });
    assert(hasPixels, 'two rotations must preserve decoded document pixels');
    assert(await second.locator('canvas').getAttribute('data-document-image-blob') === blob,
      'rotated canvas must retain the original decoded resource');
    await second.locator('div').first().dblclick();
    await page.locator('.ui-dialog canvas').waitFor();
    assert(await page.locator('.ui-dialog canvas').evaluate(canvas =>
      canvas.getContext('2d').getImageData(0, 0, canvas.width, canvas.height).data.some((v, i) => i % 4 === 3 && v > 0)),
    'zoom must preserve rotated canvas pixels');
    await page.locator('.ui-dialog-titlebar-close').click();

    // Exercise actual serialization while a page is waiting. No clinical write:
    // intercept the POST and suppress only its resulting popup in this test.
    await first.locator('div').first().click();
    await page.locator('#tool_add').click();
    let posted;
    await page.route('**/documentManager/SplitDocument', async route => {
      posted = new URLSearchParams(route.request().postData());
      await route.fulfill({status: 200, contentType: 'application/json', body: JSON.stringify({newDocNum: documentId})});
    });
    await page.evaluate(() => {window.popup = () => {};});
    await page.locator('#tool_savecontinue').click();
    await page.getByText('Save & Continue', {exact: true}).waitFor();
    assert(posted?.get('page') === '1,0', 'waiting status must never enter submitted page numbers');
    assert(posted?.get('document') === String(documentId), 'split must retain source document identity');
    assert(peak <= 4, `legacy viewers exceeded four image requests (${peak})`);

    // Remove only explicit capacity responses that this test proved recoverable
    // (or cancelled by removing the split page). Keep all unrelated failures.
    recorder.badResponses = recorder.badResponses.filter(entry => !(entry.status === 503 && capacityUrls.has(entry.url)));
    recorder.consoleIssues = recorder.consoleIssues.filter(entry => !(capacityUrls.has(entry.location?.url)
      && /Failed to load resource:.*503/.test(entry.text)));
    assertStrictPage(recorder);
    return {capacityResponses: injected, peak, rotations: 2, interceptedSplit: true};
  } catch (error) {
    originalError = error;
    throw error;
  } finally {
    // Accepted renders stay counted until complete even when their page is removed.
    // Keep cleanup failures visible, together with the original assertion if any.
    const cleanupErrors = [];
    try {
      if (pending.size) await new Promise((resolve, reject) => {
        const timer = setTimeout(() => reject(new Error('Accepted legacy image requests did not settle before cleanup')), 120000);
        drainFinished = () => {clearTimeout(timer); resolve();};
      });
    } catch (error) {cleanupErrors.push(error);}
    try {await page.close();} catch (error) {cleanupErrors.push(error);}
    if (cleanupErrors.length) throw new AggregateError([originalError, ...cleanupErrors].filter(Boolean),
      'Legacy document image check or cleanup failed');
  }
}
module.exports = {checkLegacyDocumentImages};
