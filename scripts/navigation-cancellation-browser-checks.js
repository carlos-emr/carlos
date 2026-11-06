/* SPDX-License-Identifier: GPL-2.0-or-later */
/* Copyright (c) 2026 CARLOS Contributors. */
// Real Chromium companion to navigation-cancellation.test.js (issue #5577).
// Run explicitly: CHROMIUM_EXECUTABLE=/path/to/chrome node --test scripts/navigation-cancellation-browser-checks.js
// This synthetic loopback fixture does not replace installed-package acceptance.
const assert = require('node:assert/strict');
const http = require('node:http');
const { once } = require('node:events');
const { before, after, test } = require('node:test');
const { chromium } = require('playwright');
const { createRecorder, wireStrictPage, assertStrictPage, buildFailureDetails } = require('./lib/playwright-harness');

let browser, server, base;
const sockets = new Set();
before(async () => {
  server = http.createServer((request, response) => {
    if (request.url.startsWith('/pending/')) return;
    response.writeHead(200, { 'Content-Type': 'text/html' });
    response.end('<!doctype html><title>synthetic cancellation fixture</title>');
  });
  server.on('connection', socket => {
    sockets.add(socket);
    socket.on('close', () => sockets.delete(socket));
  });
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  base = `http://127.0.0.1:${server.address().port}`;
  browser = await chromium.launch({
    executablePath: process.env.CHROMIUM_EXECUTABLE || undefined,
    headless: true,
    args: ['--no-sandbox'],
  });
});
after(async () => {
  try { await browser?.close(); } finally {
    for (const socket of sockets) socket.destroy();
    if (server?.listening) await new Promise(resolve => server.close(resolve));
  }
});

async function fixture(t) {
  const context = await browser.newContext();
  t.after(() => context.close());
  const page = await context.newPage();
  const recorder = createRecorder();
  wireStrictPage(page, 'synthetic', recorder, { baseline: [] });
  await page.goto(base);
  return { page, recorder };
}

async function pendingLoads(page, all = true) {
  const types = all ? ['xhr', 'font', 'image'] : ['xhr'];
  const started = types.map(type => page.waitForRequest(request =>
    request.url() === `${base}/pending/${type}` && request.resourceType() === type));
  await page.evaluate(all => {
    window.pendingXhr = new XMLHttpRequest();
    window.pendingXhr.open('GET', '/pending/xhr');
    window.pendingXhr.send();
    if (all) {
      window.pendingImage = new Image();
      window.pendingImage.src = '/pending/image';
      const font = new FontFace('PendingFixture', 'url(/pending/font)');
      document.fonts.add(font);
      font.load().catch(() => {});
    }
  }, all);
  await Promise.all(started);
}

for (const method of ['goto', 'reload']) {
  test(`${method} retains qualified XHR, font and image aborts without a strict failure`, async t => {
    const { page, recorder } = await fixture(t);
    await pendingLoads(page);
    const failures = ['xhr', 'font', 'image'].map(type => page.waitForEvent('requestfailed', {
      predicate: request => request.resourceType() === type,
    }));
    if (method === 'goto') await page.goto(`${base}/replacement`);
    else await page.reload();
    await Promise.all(failures);
    assert.doesNotThrow(() => assertStrictPage(recorder));
    const details = buildFailureDetails(recorder).requestFailures;
    assert.equal(details.length, 3);
    for (const detail of details) {
      assert.equal(detail.errorText, 'net::ERR_ABORTED');
      assert.equal(detail.navigationCancellation.classification, 'BROWSER_NAVIGATION_CANCELLATION');
      assert.equal(detail.navigationCancellation.operation, method);
    }
  });
}

for (const hashNavigation of [false, true]) {
  test(`application XHR abort remains fatal${hashNavigation ? ' after same-document navigation' : ''}`, async t => {
    const { page, recorder } = await fixture(t);
    await pendingLoads(page, false);
    if (hashNavigation) await page.goto(`${base}/#same-document`);
    const failed = page.waitForEvent('requestfailed', {
      predicate: request => request.resourceType() === 'xhr',
    });
    await page.evaluate(() => window.pendingXhr.abort());
    await failed;
    assert.throws(() => assertStrictPage(recorder), /xhr request failed \(net::ERR_ABORTED\)/);
    const [detail] = buildFailureDetails(recorder).requestFailures;
    assert.equal(detail.navigationCancellation, undefined);
    // A later real navigation must not rewrite the earlier failure's provenance.
    await page.goto(`${base}/replacement`);
    assert.throws(() => assertStrictPage(recorder), /xhr request failed/);
    assert.equal(buildFailureDetails(recorder).requestFailures[0].navigationCancellation, undefined);
  });
}
