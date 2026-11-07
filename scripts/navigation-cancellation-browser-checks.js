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
const { createRecorder, wireStrictPage, assertStrictPage, buildFailureDetails, validateCredentialBaseUrl } = require('./lib/playwright-harness');

let browser, server, base;
const sockets = new Set();
before(async () => {
  server = http.createServer((request, response) => {
    if (request.url.startsWith('/pending/')) return;
    if (request.url === '/signal') {
      server.emit('fixture-signal', response);
      return;
    }
    if (request.url === '/abort-signal') {
      server.emit('fixture-abort-signal', response);
      return;
    }
    if (request.url === '/gated') {
      server.emit('fixture-navigation', response);
      return;
    }
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

/** Navigate only to a fixed fixture route on the actual loopback listener. */
async function navigate(page, route = '/') {
  assert.ok(['/', '/replacement', '/#same-document', '/gated'].includes(route), 'Unknown fixture route');
  const target = validateCredentialBaseUrl(new URL(route, base).href, {});
  assert.equal(target.hostname, '127.0.0.1');
  assert.equal(target.port, String(server.address().port));
  assert.equal(target.search, '');
  return page.goto(target.href);
}

/** Open an isolated strict-recorded page and close its context after the test. */
async function fixture(t) {
  const context = await browser.newContext();
  t.after(() => context.close());
  context.setDefaultTimeout(10000);
  const page = await context.newPage();
  const recorder = createRecorder();
  wireStrictPage(page, 'synthetic', recorder, { baseline: [] });
  await navigate(page);
  return { page, recorder };
}

/**
 * Start test-owned XHR/font/image requests and await their browser request events.
 * With all=false, retain only the XHR for the application-abort negative controls.
 * The server deliberately withholds responses until navigation or teardown.
 */
async function pendingLoads(page, all = true) {
  const types = all ? ['xhr', 'font', 'image'] : ['xhr'];
  const started = types.map(type => page.waitForRequest(request =>
    request.url() === `${base}/pending/${type}` && request.resourceType() === type));
  await page.evaluate(() => {
    window.pendingXhr = new XMLHttpRequest();
    window.pendingXhr.open('GET', '/pending/xhr');
    window.pendingXhr.send();
  });
  if (all) {
    await page.evaluate(() => {
      window.pendingImage = new Image();
      window.pendingImage.src = '/pending/image';
      const font = new FontFace('PendingFixture', 'url(/pending/font)');
      document.fonts.add(font);
      font.load().catch(() => {});
    });
  }
  await Promise.all(started);
}

for (const method of ['goto', 'reload']) {
  test(`${method} retains qualified XHR, font and image aborts without a strict failure`, async t => {
    const { page, recorder } = await fixture(t);
    await pendingLoads(page);
    const failures = ['xhr', 'font', 'image'].map(type => page.waitForEvent('requestfailed', {
      predicate: request => request.resourceType() === type,
    }));
    if (method === 'goto') await navigate(page, '/replacement');
    else await page.reload();
    await Promise.all(failures);
    assert.doesNotThrow(() => assertStrictPage(recorder));
    const details = buildFailureDetails(recorder).requestFailures;
    assert.equal(details.length, 3);
    assert.deepEqual(details.map(detail => detail.resourceType).sort(), ['font', 'image', 'xhr']);
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
    if (hashNavigation) await navigate(page, '/#same-document');
    const failed = page.waitForEvent('requestfailed', {
      predicate: request => request.resourceType() === 'xhr',
    });
    await page.evaluate(() => window.pendingXhr.abort());
    await failed;
    assert.throws(() => assertStrictPage(recorder), /xhr request failed \(net::ERR_ABORTED\)/);
    const [detail] = buildFailureDetails(recorder).requestFailures;
    assert.equal(detail.navigationCancellation, undefined);
    // A later real navigation must not rewrite the earlier failure's provenance.
    await navigate(page, '/replacement');
    assert.throws(() => assertStrictPage(recorder), /xhr request failed/);
    assert.equal(buildFailureDetails(recorder).requestFailures[0].navigationCancellation, undefined);
  });
}

for (const abortInApplication of [false, true]) {
  test(`old-document XHR started during pending navigation ${abortInApplication ? 'remains fatal when the application aborts it' : 'qualifies when the browser replaces its document'}`, async t => {
    const { page, recorder } = await fixture(t);
    const ready = once(server, 'fixture-navigation', { signal: AbortSignal.timeout(10000) });
    const signal = once(server, 'fixture-signal', { signal: AbortSignal.timeout(10000) });
    const abortSignal = abortInApplication
      ? once(server, 'fixture-abort-signal', { signal: AbortSignal.timeout(10000) }) : null;
    const started = page.waitForRequest(request => request.url() === `${base}/pending/xhr`);
    const failed = page.waitForEvent('requestfailed', { predicate: request => request.resourceType() === 'xhr' });
    // Install in the old document before goto: evaluations during a pending
    // navigation wait for the replacement execution context. Completing the
    // signal XHR only after /gated arrives proves the late load's ordering.
    for (const pending of [ready, signal, abortSignal, started, failed].filter(Boolean)) pending.catch(() => {});
    if (abortInApplication) {
      await page.evaluate(() => {
        const signal = new XMLHttpRequest();
        signal.open('GET', '/signal');
        signal.onload = () => {
          const xhr = new XMLHttpRequest();
          xhr.open('GET', '/pending/xhr');
          xhr.send();
          const abortSignal = new XMLHttpRequest();
          abortSignal.open('GET', '/abort-signal');
          abortSignal.onload = () => xhr.abort();
          abortSignal.send();
        };
        signal.send();
      });
    } else {
      await page.evaluate(() => {
        const signal = new XMLHttpRequest();
        signal.open('GET', '/signal');
        signal.onload = () => {
          const xhr = new XMLHttpRequest();
          xhr.open('GET', '/pending/xhr');
          xhr.send();
        };
        signal.send();
      });
    }
    const [trigger] = await signal;
    const navigation = navigate(page, '/gated');
    navigation.catch(() => {});
    const [response] = await ready;
    trigger.end('start late load');
    await started;
    if (abortInApplication) {
      const [abortTrigger] = await abortSignal;
      abortTrigger.end('abort late load');
      await failed; // Still no replacement response headers.
    }
    response.writeHead(200, { 'Content-Type': 'text/html' });
    response.end('<!doctype html><title>replacement fixture</title>');
    await navigation;
    await failed;
    const [detail] = buildFailureDetails(recorder).requestFailures;
    assert.equal(detail.resourceType, 'xhr');
    assert.equal(detail.errorText, 'net::ERR_ABORTED');
    if (abortInApplication) {
      assert.throws(() => assertStrictPage(recorder), /xhr request failed/);
      assert.equal(detail.navigationCancellation, undefined);
    } else {
      assert.doesNotThrow(() => assertStrictPage(recorder));
      assert.equal(detail.navigationCancellation.operation, 'goto');
    }
  });
}
