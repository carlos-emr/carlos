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
    await page.goto(`${config.baseUrl}/oscarMDS/ViewSplit?document=${documentId}&queueID=1&demoName=`);
    const first = page.locator('#picker li').nth(0);
    const second = page.locator('#picker li').nth(1);
    await first.getByText('Waiting for document capacity. This image will load automatically.', {exact: true}).waitFor();
    await second.locator('img.page').scrollIntoViewIfNeeded();
    await page.waitForFunction(() => {
      const img = document.querySelectorAll('#picker img.page')[1];
      return img?.complete && img.naturalWidth > 0;
    });
    const dimensions = await second.locator('img.page').evaluate(img => ({width: img.naturalWidth, height: img.naturalHeight}));
    const blob = await second.locator('img.page').getAttribute('src');
    assert(blob.startsWith('blob:'), 'split must render the bounded loader resource');
    await second.locator('div').first().click();
    for (const angle of [90, 180]) {
      await page.locator('#tool_rotate').click();
      const geometry = await second.locator('canvas').evaluate(canvas => {
        const li = canvas.closest('li'), previous = li.previousElementSibling, next = li.nextElementSibling;
        const c = canvas.getBoundingClientRect(), box = li.getBoundingClientRect();
        return {width: canvas.width, height: canvas.height, angle: canvas.angle,
          contained: c.width > 0 && c.height > 0 && c.left >= box.left - 1 && c.right <= box.right + 1
            && c.top >= box.top - 1 && c.bottom <= box.bottom + 1,
          noOverlap: (!previous || previous.getBoundingClientRect().bottom <= box.top + 1)
            && (!next || next.getBoundingClientRect().top >= box.bottom - 1)};
      });
      assert(geometry.angle === angle && Math.abs(geometry.width - (angle === 90 ? dimensions.height : dimensions.width)) <= 1
        && Math.abs(geometry.height - (angle === 90 ? dimensions.width : dimensions.height)) <= 1,
      'rotation must keep the full decoded bitmap resolution');
      assert(geometry.contained && geometry.noOverlap, 'responsive rotated preview must remain inside its own nonoverlapping list item');
      // Ordinary Playwright clicks exercise hit testing: a rotated canvas must
      // never cover the preceding page and require force-clicking through it.
      await first.locator('div').first().click();
      assert(await page.evaluate(() => selected.length === 1 && selected[0].querySelector('span.num').textContent === '1'),
        'preceding page must remain selectable after rotation');
      await second.locator('div').first().click();
    }
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
    const zoomDimensions = await page.locator('.ui-dialog canvas').evaluate(canvas => ({width: canvas.width, height: canvas.height}));
    assert(Math.abs(zoomDimensions.width - dimensions.width) <= 1 && Math.abs(zoomDimensions.height - dimensions.height) <= 1,
      'zoom must retain full bitmap resolution after responsive rotation');
    await page.locator('.ui-dialog-titlebar-close').click();

    // Exercise actual serialization while a page is waiting. No clinical write:
    // intercept the POST and suppress only its resulting popup in this test.
    await first.locator('div').first().click();
    await page.locator('#tool_add').click();
    const postedBodies = [], mutationFailures = new Map();
    let mutationPhase = 'busy';
    await page.route('**/documentManager/SplitDocument', async route => {
      const fields = new URLSearchParams(route.request().postData());
      assert(fields.getAll('CSRF-TOKEN').length === 1 && fields.get('CSRF-TOKEN')
        && route.request().headers()['csrf-token'] === fields.get('CSRF-TOKEN'), 'Split must pin its original token in body and header');
      assert(fields.getAll('sourceRevision').length === 1 && /^[0-9a-f]{64}$/.test(fields.get('sourceRevision')),
        'Split must include its exact source revision');
      postedBodies.push(route.request().postData());
      let status = 200, data = {success: true, accepted: true, newDocNum: Number(documentId)};
      if (mutationPhase === 'busy' && postedBodies.length <= 4) {
        status = 503; data = {success: false, accepted: false, retryable: true};
      } else if (mutationPhase === 'uncertain') {
        status = 500; data = {success: false, accepted: true, retryable: false};
      }
      if (status !== 200) {
        const statuses = mutationFailures.get(route.request().url()) || new Set();
        statuses.add(status); mutationFailures.set(route.request().url(), statuses);
      }
      await route.fulfill({status, contentType: 'application/json', headers: {'Retry-After': '1'}, body: JSON.stringify(data)});
    });
    await page.evaluate(() => {window.popup = () => {};});
    await page.locator('#tool_savecontinue').click();
    await page.locator('#split-save-cancel').waitFor({state: 'visible'});
    assert(await page.locator('#builder > li').count() === 1, 'busy admission must preserve the visible selected pages');
    assert(await page.locator('#tool_remove').getAttribute('aria-disabled') === 'true', 'mutators must be disabled while admission waits');
    await page.evaluate(() => {
      document.getElementById('tool_savecontinue').click();
      document.getElementById('tool_remove').click();
      document.getElementById('tool_rotate').click();
    });
    assert(await page.locator('#builder > li').count() === 1, 'blocked duplicate submission and edits must retain selected pages');
    await page.waitForFunction(() => document.querySelectorAll('#builder > li').length === 0
      && !splitSaveController.isLocked());
    assert(postedBodies.length === 5 && new Set(postedBodies).size === 1,
      'four explicit admission refusals must retry the identical frozen POST once at a time');
    const posted = new URLSearchParams(postedBodies[0]);
    assert(posted.getAll('page').length === 1 && posted.get('page') === '1,0', 'waiting status must never enter submitted page numbers');
    assert(posted.get('document') === String(documentId) && posted.get('queueID') === '1', 'split must retain valid document and queue identity');

    // A possibly accepted server failure cannot invite another clinical write.
    // Keep the rotated page visible, including its decoded bitmap and ownership.
    mutationPhase = 'uncertain';
    const remaining = page.locator('#picker li').filter({has: page.locator('canvas')}).first();
    await remaining.locator('div').first().click();
    await page.locator('#tool_add').click();
    await page.locator('#tool_savecontinue').click();
    await page.locator('#split-save-status[role="alert"]').waitFor();
    assert(await page.locator('#split-save-status').textContent(), 'unknown outcome must have a visible explanation');
    const preserved = await page.locator('#builder canvas').evaluate(canvas => ({
      width: canvas.width, height: canvas.height, angle: canvas.angle,
      blob: canvas.getAttribute('data-document-image-blob'),
      pixels: canvas.getContext('2d').getImageData(0, 0, canvas.width, canvas.height).data.some((v, i) => i % 4 === 3 && v > 0),
    }));
    assert(preserved.angle === 180 && preserved.blob === blob && preserved.pixels
      && Math.abs(preserved.width - dimensions.width) <= 1 && Math.abs(preserved.height - dimensions.height) <= 1,
    'unconfirmed save must retain the complete selected rotated page');
    await page.evaluate(() => {
      document.getElementById('tool_savecontinue').click();
      document.getElementById('tool_remove').click();
      document.getElementById('tool_rotate').click();
    });
    assert(await page.locator('#builder > li').count() === 1
      && await page.locator('#builder canvas').evaluate(canvas => canvas.angle) === 180,
    'unknown outcome must lock selected-page mutations');
    assert(postedBodies.length === 6 && await page.evaluate(() => splitSaveController.isLocked()),
      'unknown outcome must prevent duplicate filing');
    assert(peak <= 4, `legacy viewers exceeded four image requests (${peak})`);

    // Filter only the failures explicitly injected above; unrelated errors remain fatal.
    recorder.badResponses = recorder.badResponses.filter(entry => !(entry.status === 503 && capacityUrls.has(entry.url))
      && !mutationFailures.get(entry.url)?.has(entry.status));
    recorder.consoleIssues = recorder.consoleIssues.filter(entry => {
      const code = /Failed to load resource:.*(503|500)/.exec(entry.text)?.[1];
      return !(code && ((code === '503' && capacityUrls.has(entry.location?.url))
        || mutationFailures.get(entry.location?.url)?.has(Number(code))));
    });
    assertStrictPage(recorder);
    return {capacityResponses: injected, peak, rotations: 2, interceptedSplit: true, splitBusyResponses: 4, preservedUncertainSplit: true};
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
