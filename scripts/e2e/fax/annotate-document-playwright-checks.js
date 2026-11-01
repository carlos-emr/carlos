/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Browser regression check for the document annotation viewer.
 *
 * What it proves against a running deployment:
 *   1. The viewer renders SERVER-produced page images and loads no PDF library.
 *      A network assertion fails the run if any .mjs, pdf.worker or /webjars/
 *      request appears, which is what a PDF.js regression would look like.
 *   2. Word boxes are OPTIONAL. The endpoint answers 200 with hasTextLayer
 *      either way, and highlighting works on a page with no text layer.
 *   3. The page's Content-Security-Policy is BOTH present and satisfied. A strict
 *      script-src silently disables the inline configuration block and the CSRF
 *      bootstrap, which leaves the viewer blank and every save rejected; the browser
 *      reports that only on the console, so the console is asserted here.
 *   4. The save endpoint refuses GET.
 *   4. A real save round-trips: the composed copy is filed as a NEW document,
 *      reported by number, and is independently renderable. The check owns its synthetic source patient, source PDF and every annotated copy;
 *      accepted saves finish before guarded row/file/cache cleanup.
 *
 * Its fixture also verifies unchanged source rows/PDF bytes and removes only its
 * captured annotated copies and filename-scoped page-cache files.
 *
 * Usage:
 *   BASE_URL=https://host/carlos TEST_USER=... TEST_PASSWORD=... TEST_PIN=... \
 *     node scripts/e2e/fax/annotate-document-playwright-checks.js
 *
 * Requires a disposable deployment, configured DOCUMENT_DIR and guarded MySQL access.
 */
'use strict';
const legacyHarness=require('../../lib/legacy-check');
const h=require('../../lib/playwright-harness');
const {createOwnedAnnotationFixture}=require('../../lib/owned-annotation-fixture');
const {authenticatedProbe}=require('../../lib/authenticated-probe');


const { chromium } = require('playwright');
const { shouldIgnoreHttpsErrors } = require('../../lib/playwright-harness');
const {checkAnnotationSessions} = require('../../lib/annotation-multisession-check');
const {readConfig} = require('../../lib/playwright-harness');

function requireEnv(name) {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Missing required environment variable ${name}`);
  }
  return value;
}

/**
 * Validates BASE_URL before it reaches page.goto or a credentialed request, matching the guard in
 * scripts/login-playwright-checks.js. This script types a real password into the target, so it
 * must not be pointed at an arbitrary host by an unchecked environment variable.
 */
function validateBaseUrl(rawBaseUrl) {
  return require('../../lib/playwright-harness').validateCredentialBaseUrl(rawBaseUrl,process.env);
}

/** One navigation boundary for ordinary, busy and failed-image viewer checks. */
async function navigateAnnotationViewer(page, rawBaseUrl, viewerDocId, waitUntil = 'load') {
  if (!/^[1-9][0-9]*$/.test(String(viewerDocId))) throw new Error('Viewer document ID must be a positive integer');
  const target = new URL(validateBaseUrl(rawBaseUrl).href);
  target.pathname = target.pathname.replace(/\/+$/, '') + '/documentManager/AnnotateDocument';
  target.search = '';
  target.hash = '';
  target.searchParams.set('docId', String(viewerDocId));
  // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- Revalidated HTTP(S), credential-free local/explicitly opted-in base; fixed route and positive-integer query value. Guard behavior is exercised in annotation-navigation-guard.test.js.
  return page.goto(target.href, {waitUntil});
}

/** Report a refused fax handoff immediately instead of waiting for a URL that cannot arrive. */
async function waitForAnnotationFaxCover(page) {
  const outcome = await page.waitForFunction(() => {
    if (document.getElementById('btnSend')) return 'ready';
    const refusal = document.querySelector('[role="alert"]');
    if (window.location.pathname.endsWith('/documentManager/FaxDocument') && refusal) {
      return /No active fax accounts are configured/.test(refusal.textContent) ? 'no-account' : 'refused';
    }
    if (document.querySelector('#status.error')) return 'save-failed';
    return false;
  }, null, {timeout: 120000});
  const result = await outcome.jsonValue();
  if (result === 'no-account') {
    throw new Error('Annotation Save and fax requires an active fax account available to the test provider. Prepare an owned UI-only fax fixture; this required handoff check cannot pass without it.');
  }
  if (result !== 'ready') throw new Error('Annotation Save and fax was refused before reaching the cover page; inspect the visible save/fax error.');
}

const validatedBaseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const baseUrl = validatedBaseUrl.href;
const testUser = requireEnv('TEST_USER');
const testPassword = requireEnv('TEST_PASSWORD');
const testPin = requireEnv('TEST_PIN');
const newPassword = process.env.TEST_NEW_PASSWORD || '';
const chromePath = process.env.CHROME_PATH || '';

const findings = [];
const notes = [];

function check(label, condition, detail) {
  if (condition) {
    notes.push(`PASS  ${label}`);
  } else {
    findings.push(`FAIL  ${label}${detail ? ` — ${detail}` : ''}`);
  }
}

/**
 * The viewer must never pull a client-side PDF stack. Any request matching these
 * is the regression this design exists to prevent.
 */
const FORBIDDEN_ASSET = /\.mjs(\?|$)|pdf\.worker|pdfjs|\/webjars\//i;

async function login(page) {
  // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection
  // baseUrl comes from validateBaseUrl(): protocol allow-listed, credentials refused, host local
  // unless ALLOW_NON_LOCAL_BASE_URL is set deliberately.
  // operator-configured test URL passes validateBaseUrl; no application request controls it.
  // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection
  await page.goto(`${baseUrl}/`, { waitUntil: 'domcontentloaded' });
  await page.locator('#username').fill(testUser);
  await page.locator('#password').fill(testPassword);
  if (await page.locator('#pin').count()) {
    await page.locator('#pin').fill(testPin);
  }
  await Promise.all([
    page.waitForLoadState('domcontentloaded').catch(() => {}),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);

  // A package-bootstrapped account is flagged for a forced reset, so the first
  // login lands on the change-password page rather than the schedule.
  const looksLikeReset = await page
    .locator('input[type="password"]')
    .count()
    .then((n) => n >= 2)
    .catch(() => false);

  if (looksLikeReset && newPassword) {
    const fields = page.locator('input[type="password"]');
    const count = await fields.count();
    await fields.nth(0).fill(testPassword);
    for (let i = 1; i < count; i += 1) {
      await fields.nth(i).fill(newPassword);
    }
    await Promise.all([
      page.waitForLoadState('domcontentloaded').catch(() => {}),
      page.locator('input[type="submit"], button[type="submit"]').first().click(),
    ]);
  }
  return page;
}

async function main({cancellation,fixture}={}) {
  const cleanupReleases=[];
  const browser = await chromium.launch({...({
    headless: true,
    ...(chromePath ? { executablePath: chromePath } : {}),
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  }),...legacyHarness.NO_PLAYWRIGHT_SIGNAL_HANDLING});
  // Self-signed certificates are normal on a local dev deployment. For a remote one this script
  // sends a real password, so verification stays on unless ALLOW_UNVERIFIED_TLS=true says otherwise.
  let context;
  try {
  context = await browser.newContext({ ignoreHTTPSErrors: shouldIgnoreHttpsErrors() });
  fixture.bind(context);
  context.setDefaultTimeout(120000);
  context.setDefaultNavigationTimeout(120000);

  const forbiddenRequests = [];
  const imageRequests = [];
  // A CSP that blocks the page's own inline scripts fails silently in the DOM: the
  // console is the only place it is reported.
  const cspViolations = [];
  context.on('request', (request) => {
    const url = request.url();
    if (FORBIDDEN_ASSET.test(url)) {
      forbiddenRequests.push(url);
    }
    if (/method=showPage/.test(url)) {
      imageRequests.push(url);
    }
  });

    const page = await context.newPage();
    cancellation?.throwIfCancelled();
    page.on('console', (message) => {
      if (message.type() === 'error' && /Content Security Policy/i.test(message.text())) {
        cspViolations.push(message.text());
      }
    });
    page.on('pageerror', (error) => cspViolations.push(`pageerror: ${error.message}`));
    await login(page);
    cancellation?.throwIfCancelled();

    const docId = fixture.documentId;
    if (!/^[1-9][0-9]*$/.test(docId)) { throw new Error('DOC_ID must be a positive integer'); }

    // ---- word boxes are optional ----
    const boxes = await page.evaluate(async (args) => {
      const response = await fetch(
        `${args.base}/documentManager/DocumentTextBoxes?docId=${args.docId}&page=1`,
        { credentials: 'same-origin', headers: { 'X-Requested-With': 'XMLHttpRequest' } },
      );
      return { status: response.status, body: await response.text() };
    }, { base: baseUrl, docId });
    cancellation?.throwIfCancelled();

    check('word-box endpoint answers 200', boxes.status === 200, `status ${boxes.status}`);
    let parsed = null;
    try {
      parsed = JSON.parse(boxes.body);
    } catch (e) {
      parsed = null;
    }
    check('word-box response is JSON with a words array',
      parsed && Array.isArray(parsed.words),
      boxes.body.slice(0, 120));
    check('word-box response states whether a text layer exists',
      parsed && typeof parsed.hasTextLayer === 'boolean',
      parsed ? `hasTextLayer=${parsed.hasTextLayer}` : 'unparseable');
    if (parsed) {
      notes.push(`INFO  page 1 hasTextLayer=${parsed.hasTextLayer}, words=${parsed.words.length}`);
    }

    // ---- the save endpoint is POST only ----
    const getSave = await page.evaluate(async (args) => {
      const response = await fetch(
        `${args.base}/documentManager/SaveAnnotatedDocument?docId=${args.docId}`,
        { credentials: 'same-origin', headers: { 'X-Requested-With': 'XMLHttpRequest' } },
      );
      await response.arrayBuffer();
      return response.status;
    }, { base: baseUrl, docId });
    cancellation?.throwIfCancelled();
    check('save endpoint refuses GET with 405', getSave === 405, `status ${getSave}`);

    // ---- the viewer renders ----
    const viewerResponse = await navigateAnnotationViewer(page, baseUrl, docId, 'domcontentloaded');
    cancellation?.throwIfCancelled();
    await page.locator('svg.overlay').first().waitFor();
    cancellation?.throwIfCancelled();
    await page.waitForFunction(() => document.querySelector('.page img')?.naturalWidth > 0);
    cancellation?.throwIfCancelled();

    const csp = viewerResponse.headers()['content-security-policy'] || '';
    check('viewer sends a Content-Security-Policy', csp.length > 0, '(no header)');
    check('the policy admits the page\'s own inline scripts by nonce',
      /script-src[^;]*'nonce-/.test(csp), csp || '(no header)');

    // These two prove the policy is actually satisfied rather than merely well-formed.
    // Without the nonce both blocks are dropped, the viewer renders nothing, and the
    // save path fails with an empty CSRF token.
    const configLoaded = await page.evaluate(() => typeof window.CARLOS_ANNOTATE === 'object');
    cancellation?.throwIfCancelled();
    check('the inline configuration block executed', configLoaded,
      'window.CARLOS_ANNOTATE is not an object — script-src is blocking it');
    const csrfBootstrapped = await page.evaluate(() => 'csrfTokenReady' in window);
    cancellation?.throwIfCancelled();
    check('the CSRF bootstrap executed', csrfBootstrapped,
      'window.csrfTokenReady is absent — script-src is blocking csrf-token.jspf');

    const swatchColors = await page.locator('.swatch').evaluateAll(elements =>
      elements.map(element => getComputedStyle(element).backgroundColor));
    cancellation?.throwIfCancelled();
    check('all six annotation colour choices are visibly distinct',
      new Set(swatchColors).size === 6 && !swatchColors.includes('rgb(255, 255, 255)'), swatchColors.join(', '));

    const toolCount = await page.locator('.tool').count();
    cancellation?.throwIfCancelled();
    check('viewer renders its toolbar', toolCount >= 6, `${toolCount} tools`);

    const pageCount = await page.locator('.page').count();
    cancellation?.throwIfCancelled();
    check('viewer renders at least one page container', pageCount >= 1, `${pageCount} pages`);

    const overlayCount = await page.locator('svg.overlay').count();
    cancellation?.throwIfCancelled();
    check('viewer renders an SVG overlay per page', overlayCount === pageCount,
      `${overlayCount} overlays for ${pageCount} pages`);

    check('viewer requested server-rendered page images', imageRequests.length >= 1,
      `${imageRequests.length} showPage requests`);

    check('viewer loaded NO client-side PDF library', forbiddenRequests.length === 0,
      forbiddenRequests.slice(0, 3).join(', '));

    const csrf = await page.locator('input[name="CSRF-TOKEN"]').count();
    cancellation?.throwIfCancelled();
    check('viewer bootstraps a CSRF token', csrf >= 1, `${csrf} token inputs`);

    check('the page reported no CSP violation or script error',
      cspViolations.length === 0, cspViolations.slice(0, 3).join(' | '));

    // The status region is an <output>, not a <span role="status">. The element type matters
    // because the viewer's setStatus() writes to it by id and swaps its className: if the swap
    // ever broke the handle or the CSS, saves would appear to do nothing. Assert what the
    // BROWSER computes, not what the markup claims.
    const statusTag = await page.locator('#status').evaluate(el => el.tagName.toLowerCase());
    cancellation?.throwIfCancelled();
    check('the status region is an <output>', statusTag === 'output', `<${statusTag}>`);
    // includeHidden: while empty the region is display:none via .status:empty, and Playwright's
    // role engine skips hidden elements. The role is asserted again after a save, when it is
    // visible and actually announceable.
    const statusRoleCount = await page.getByRole('status', { includeHidden: true }).count();
    cancellation?.throwIfCancelled();
    check('the browser exposes the status region with the ARIA status role',
      statusRoleCount >= 1, `${statusRoleCount} elements with role=status`);
    const statusHiddenWhenEmpty = await page.locator('#status')
      .evaluate(el => getComputedStyle(el).display);
    cancellation?.throwIfCancelled();
    check('the empty status region is hidden by .status:empty',
      statusHiddenWhenEmpty === 'none', `display: ${statusHiddenWhenEmpty}`);

    // ---- highlighting works regardless of the text layer ----
    await page.locator('.tool[data-tool="highlight"]').click();
    cancellation?.throwIfCancelled();
    let coldDocumentNo;
    const overlay = page.locator('svg.overlay').first();
    const box = await overlay.boundingBox();
    cancellation?.throwIfCancelled();
    if (box && box.width > 40 && box.height > 40) {
      await page.mouse.move(box.x + 30, box.y + 40);
    cancellation?.throwIfCancelled();
      await page.mouse.down();
    cancellation?.throwIfCancelled();
      await page.mouse.move(box.x + box.width * 0.6, box.y + 60, { steps: 8 });
    cancellation?.throwIfCancelled();
      await page.mouse.up();
    cancellation?.throwIfCancelled();
      await page.waitForTimeout(400);
    cancellation?.throwIfCancelled();
      const marks = await overlay.locator('.mark').count();
    cancellation?.throwIfCancelled();
      check('a highlight can be drawn and appears on the overlay', marks >= 1, `${marks} marks`);
      const saveEnabled = await page.locator('#btnSave').isEnabled();
    cancellation?.throwIfCancelled();
      check('Save becomes available once a mark exists', saveEnabled);

      // ---- a real save, which is the whole point of the design ----
      // Posting the model directly rather than clicking Save keeps the assertion on
      // the server contract: what comes back must name a NEW document.
      const saved = await page.evaluate(async (args) => {
        const input = document.querySelector('input[name="CSRF-TOKEN"]');
        const response = await fetch(
          `${args.base}/documentManager/SaveAnnotatedDocument?docId=${args.docId}`,
          {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
              'Content-Type': 'application/json',
              'X-Requested-With': 'XMLHttpRequest',
              'CSRF-TOKEN': input ? input.value : '',
            },
            body: JSON.stringify({
              sourceDigest: window.CARLOS_ANNOTATE.sourceDigest,
              annotations: [
                { type: 'highlight', page: 1, x: 0.1, y: 0.2, w: 0.5, h: 0.03, color: 'yellow' },
                { type: 'date', page: 1, x: 0.6, y: 0.05, w: 0.25, h: 0.03, text: '2026-01-01', fontSize: 11 },
              ],
            }),
          },
        );
        let body = null;
        try {
          body = JSON.parse(await response.text());
        } catch (e) {
          body = null;
        }
        return { status: response.status, body };
      }, { base: baseUrl, docId });
    cancellation?.throwIfCancelled();

      check('save returns 200', saved.status === 200, `status ${saved.status}`);
      check('save reports success', saved.body && saved.body.success === true,
        JSON.stringify(saved.body || {}).slice(0, 160));
      const newDocNo = saved.body && saved.body.documentNo;
      check('save files the result as a NEW document',
        Number.isInteger(newDocNo) && String(newDocNo) !== String(docId),
        `documentNo=${newDocNo}, source docId=${docId}`);
      if (Number.isInteger(newDocNo)) {
        coldDocumentNo = newDocNo;
        notes.push(`INFO  annotated copy filed as document ${newDocNo}`);
        // The copy must be reachable in its own right, which also confirms it was
        // written to the document store and not merely recorded.
        const openable = await page.evaluate(async (args) => {
          const r = await fetch(
            `${args.base}/documentManager/ManageDocument?method=showPage&doc_no=${args.docNo}&page=1`,
            { credentials: 'same-origin' },
          );
          const bytes = await r.arrayBuffer();
          return { status: r.status, type: r.headers.get('content-type') || '', byteLength: bytes.byteLength };
        }, { base: baseUrl, docNo: newDocNo });
    cancellation?.throwIfCancelled();
        check('the annotated copy renders as its own document',
          openable.status === 200 && /image/i.test(openable.type) && openable.byteLength > 0,
          `status ${openable.status}, type ${openable.type}`);
      }

      // Everything above posts the model with fetch, which never touches setStatus(). Click the
      // real Save button so the viewer's own status path runs end to end: the handle resolves,
      // the className swap lands, and the region becomes visible. This files one further copy,
      // which is why this script is for disposable deployments only.
      await page.locator('#btnSave').click();
    cancellation?.throwIfCancelled();
      await page.waitForFunction(
        () => /(?:ok|error)/.test(document.getElementById('status').className),
        null, { timeout: 120000 },
      ).catch(() => {});
    cancellation?.throwIfCancelled();
      const statusAfterSave = await page.locator('#status').evaluate(el => ({
        text: el.textContent.trim(),
        cls: el.className,
        display: getComputedStyle(el).display,
      }));
    cancellation?.throwIfCancelled();
      check('clicking Save confirms successful filing', statusAfterSave.cls === 'status ok', JSON.stringify(statusAfterSave));
      check('clicking Save writes into the status region',
        statusAfterSave.text.length > 0, JSON.stringify(statusAfterSave));
      check('setStatus applies its state class to the status region',
        /^status( \w+)?$/.test(statusAfterSave.cls), `className="${statusAfterSave.cls}"`);
      check('the populated status region is visible',
        statusAfterSave.display !== 'none', `display: ${statusAfterSave.display}`);
      // Now that it carries text and is visible, it must be announceable as a live status.
      const visibleStatusRole = await page.getByRole('status').count();
    cancellation?.throwIfCancelled();
      check('the visible status region is announceable as role=status',
        visibleStatusRole >= 1, `${visibleStatusRole} visible elements with role=status`);
    } else {
      findings.push('FAIL  overlay had no usable geometry to draw on');
    }

    // Run against the deployed viewer, injecting only the named failure at its network boundary.
    // waitUntil 'domcontentloaded' opens the viewer while a held subresource (the annotation
    // font) is still outstanding; the default waits for the load event.
    async function openViewer(waitUntil = 'load', viewerDocId = docId) {
      const dismiss = dialog => dialog.accept();
      page.on('dialog', dismiss);
      try { await navigateAnnotationViewer(page, baseUrl, viewerDocId, waitUntil); }
      finally { page.off('dialog', dismiss); }
      await page.waitForFunction(() => document.querySelector('.page img')?.naturalWidth > 0);
    }
    async function mark(tool = 'highlight') {
      // The viewer's CSS sets scroll-behavior: smooth. A default scrollTo would animate, and the
      // overlay's bounding box read below could still be the pre-scroll one, putting the whole
      // stroke off the page. 'instant' makes the scroll complete before the box is measured.
      await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
      if (tool === 'draw') { await page.locator('.swatch[data-color="black"]').click(); }
      await page.locator(`.tool[data-tool="${tool}"]`).click();
      const bounds = await page.locator('svg.overlay').first().boundingBox();
      await page.mouse.move(bounds.x + 30, bounds.y + 80);
      await page.mouse.down();
      await page.mouse.move(bounds.x + 170, bounds.y + 110, { steps: 12 });
      await page.mouse.up();
    }
    async function waitForSave() {
      await page.waitForFunction(() => /(?:ok|error)/.test(document.getElementById('status').className));
    }

    const stale = await page.evaluate(async () => {
      const result = await fetch(window.CARLOS_ANNOTATE.contextPath
        + '/documentManager/SaveAnnotatedDocument?docId=' + window.CARLOS_ANNOTATE.docId, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest',
          'CSRF-TOKEN': document.querySelector('input[name="CSRF-TOKEN"]').value },
        body: JSON.stringify({ sourceDigest: '0'.repeat(64), annotations: [
          { type: 'highlight', page: 1, x: .1, y: .1, w: .2, h: .1 }
        ] })
      });
      return { status: result.status, body: await result.json() };
    });
    cancellation?.throwIfCancelled();
    check('a stale source fingerprint refuses filing with an explicit conflict',
      stale.status === 409 && stale.body.error.includes('source document changed'));

    if (pageCount > 1) {
      if (!coldDocumentNo) { throw new Error('The last-page regression requires the newly filed document'); }
      const activePageRequests = new Set();
      let peakPageRequests = 0;
      const pageResponses = [];
      const ownsPageRequest = request => {
        const url = new URL(request.url());
        return (url.searchParams.get('doc_no') === String(coldDocumentNo) && url.searchParams.get('method') === 'showPage')
          || (url.pathname.endsWith('/DocumentTextBoxes') && url.searchParams.get('docId') === String(coldDocumentNo));
      };
      const onPageRequest = request => {
        if (ownsPageRequest(request)) {
          activePageRequests.add(request);
          peakPageRequests = Math.max(peakPageRequests, activePageRequests.size);
        }
      };
      const onPageFinished = request => activePageRequests.delete(request);
      const onPageResponse = response => {
        if (ownsPageRequest(response.request())) pageResponses.push({status: response.status(),
          page: new URL(response.url()).searchParams.get('page')});
      };
      page.on('request', onPageRequest);
      page.on('requestfinished', onPageFinished);
      page.on('requestfailed', onPageFinished);
      page.on('response', onPageResponse);
      // A new copy has no cached later-page PNGs; rerunning against the source would
      // mask the burst that originally exhausted the server's shared PDF workers.
      await openViewer('load', coldDocumentNo);
    cancellation?.throwIfCancelled();
      await page.locator('.tool[data-tool="highlight"]').click();
    cancellation?.throwIfCancelled();
      const lastPage = page.locator('.page').last();
      await lastPage.evaluate(element => element.scrollIntoView({ block: 'start' }));
    cancellation?.throwIfCancelled();
      await page.waitForFunction(() => {
        const last = [...document.querySelectorAll('.page')].at(-1);
        return last.querySelector('img').naturalWidth > 0 || last.classList.contains('load-failed');
      }).catch(async error => {
        const diagnostics = await page.evaluate(() => {
          const image = [...document.querySelectorAll('.page img')].at(-1);
          return {scrollY, top: image.getBoundingClientRect().top, imageUrl: image.dataset.imageUrl,
            naturalWidth: image.naturalWidth, status: document.getElementById('status').textContent};
        });
        throw new Error(`Last-page loading failed: ${JSON.stringify({diagnostics, pageResponses, peakPageRequests})}`, {cause: error});
      });
    cancellation?.throwIfCancelled();
      check('cold multipage highlight scrolling keeps combined image and text demand bounded', peakPageRequests <= 4,
        JSON.stringify({peakPageRequests, pageResponses}));
      check('the cold last page renders after any capacity waiting without a genuine image or text failure',
        await lastPage.locator('img').evaluate(image => image.naturalWidth > 0)
          && await page.locator('.page.load-failed').count() === 0
          && pageResponses.every(response => response.status === 200 || response.status === 503),
        JSON.stringify(pageResponses));
    cancellation?.throwIfCancelled();
      if (await lastPage.locator('img').evaluate(image => image.naturalWidth === 0)) {
        throw new Error(`Cold last page did not render: ${JSON.stringify(pageResponses)}`);
      }
      page.off('request', onPageRequest);
      page.off('requestfinished', onPageFinished);
      page.off('requestfailed', onPageFinished);
      page.off('response', onPageResponse);
      await lastPage.evaluate(element => element.scrollIntoView({ block: 'start' }));
    cancellation?.throwIfCancelled();
      await page.locator('.tool[data-tool="date"]').click();
    cancellation?.throwIfCancelled();
      await lastPage.locator('svg.overlay').click({ position: { x: 80, y: 220 } });
    cancellation?.throwIfCancelled();
      await page.locator('#btnSave').click();
    cancellation?.throwIfCancelled();
      await waitForSave();
    cancellation?.throwIfCancelled();
      check('a mark on the last page saves successfully in a multipage document',
        await page.locator('#status').getAttribute('class') === 'status ok');
    cancellation?.throwIfCancelled();
      await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
    cancellation?.throwIfCancelled();
    }

    // A successful save must not suppress warnings for subsequent unsaved edits.
    await mark('draw');
    cancellation?.throwIfCancelled();
    check('editing after a save enables saving again', await page.locator('#btnSave').isEnabled());
    cancellation?.throwIfCancelled();
    await page.locator('.tool[data-tool="date"]').click();
    cancellation?.throwIfCancelled();
    await page.locator('svg.overlay').first().click({ position: { x: 230, y: 140 } });
    cancellation?.throwIfCancelled();
    await page.locator('.tool[data-tool="text"]').click();
    cancellation?.throwIfCancelled();
    page.once('dialog', dialog => dialog.accept('Synthetic note é 3618'));
    await page.locator('svg.overlay').first().click({ position: { x: 230, y: 180 } });
    cancellation?.throwIfCancelled();
    if (process.env.EXPECT_SIGNATURE_STAMP === 'true') {
      await page.locator('.tool[data-tool="signature"]').click();
    cancellation?.throwIfCancelled();
      await page.locator('svg.overlay').first().click({ position: { x: 350, y: 250 } });
    cancellation?.throwIfCancelled();
    }
    check('preview loads the same font as the PDF composer',
      await page.evaluate(() => document.fonts.load('11px CarlosAnnotation').then(fonts => fonts.length > 0)));
    cancellation?.throwIfCancelled();
    await page.locator('#btnSave').click();
    cancellation?.throwIfCancelled();
    await waitForSave();
    cancellation?.throwIfCancelled();
    check('ink, date and Unicode text save through the real toolbar',
      await page.locator('#status').getAttribute('class') === 'status ok');
    cancellation?.throwIfCancelled();
    if (process.env.EXPECT_SIGNATURE_STAMP === 'true') {
      check('the provider signature stamp saves through the real toolbar',
        await page.locator('#status').getAttribute('class') === 'status ok');
    cancellation?.throwIfCancelled();
    }
    if (process.env.ARTIFACT_DIR) {
      require('fs').mkdirSync(process.env.ARTIFACT_DIR, { recursive: true });
      await page.screenshot({ path: require('path').join(process.env.ARTIFACT_DIR, 'annotation-tools.png'), fullPage: true });
    cancellation?.throwIfCancelled();
    }

    await openViewer();
    cancellation?.throwIfCancelled();
    await page.locator('.tool[data-tool="text"]').click();
    cancellation?.throwIfCancelled();
    page.once('dialog', dialog => dialog.accept('Synthetic unsupported 🧬'));
    await page.locator('svg.overlay').first().click({ position: { x: 180, y: 140 } });
    cancellation?.throwIfCancelled();
    const unsupportedResponse = page.waitForResponse(r => r.url().includes('/SaveAnnotatedDocument'));
    await page.locator('#btnSave').click();
    cancellation?.throwIfCancelled();
    check('unsupported glyph is rejected instead of silently erased', (await unsupportedResponse).status() === 409);
    cancellation?.throwIfCancelled();
    await waitForSave();
    cancellation?.throwIfCancelled();
    check('unsupported glyph rejection stays visible and editable',
      (await page.locator('#status').textContent()).includes('cannot display') && await page.locator('#btnSave').isEnabled());
    cancellation?.throwIfCancelled();

    // Placed marks can be dragged and re-edited without leaving the current tool, and the
    // moved/edited model must still pass the server's bounds and text validation.
    await openViewer();
    cancellation?.throwIfCancelled();
    await page.locator('.tool[data-tool="text"]').click();
    cancellation?.throwIfCancelled();
    page.once('dialog', dialog => dialog.accept('Synthetic movable note'));
    await page.locator('svg.overlay').first().click({ position: { x: 120, y: 140 } });
    cancellation?.throwIfCancelled();
    const noteBefore = await page.locator('text.mark').first().boundingBox();
    cancellation?.throwIfCancelled();
    await page.mouse.move(noteBefore.x + 4, noteBefore.y + 4);
    cancellation?.throwIfCancelled();
    await page.mouse.down();
    cancellation?.throwIfCancelled();
    await page.mouse.move(noteBefore.x + 84, noteBefore.y + 124, { steps: 8 });
    cancellation?.throwIfCancelled();
    await page.mouse.up();
    cancellation?.throwIfCancelled();
    const noteAfter = await page.locator('text.mark').first().boundingBox();
    cancellation?.throwIfCancelled();
    check('a text note can be dragged while the text tool is active',
      Math.abs(noteAfter.y - noteBefore.y - 120) < 3 && await page.locator('#markCount').textContent() === '1',
      JSON.stringify([noteBefore, noteAfter]));
    cancellation?.throwIfCancelled();
    page.once('dialog', dialog => dialog.accept('Synthetic edited note'));
    await page.mouse.click(noteAfter.x + 4, noteAfter.y + 4);
    cancellation?.throwIfCancelled();
    check('clicking a text note edits it in place',
      await page.locator('text.mark').first().textContent() === 'Synthetic edited note'
      && await page.locator('#markCount').textContent() === '1');
    cancellation?.throwIfCancelled();
    await page.locator('#btnSave').click();
    cancellation?.throwIfCancelled();
    await waitForSave();
    cancellation?.throwIfCancelled();
    check('a moved and edited note saves successfully',
      await page.locator('#status').getAttribute('class') === 'status ok');
    cancellation?.throwIfCancelled();

    // SVG text hit-tests on glyph outlines, so the gaps between letters need the note's hit box.
    const noteMisses = await page.evaluate(() => {
      const note = document.querySelector('svg.overlay text.mark');
      const id = note.getAttribute('data-id');
      const r = note.getBoundingClientRect();
      const misses = [];
      for (let i = 1; i < 10; i += 1) {
        for (let j = 1; j < 4; j += 1) {
          const x = r.left + (r.width * i) / 10;
          const y = r.top + (r.height * j) / 4;
          const owner = document.elementFromPoint(x, y)?.closest('[data-id]');
          if (!owner || owner.getAttribute('data-id') !== id) { misses.push([Math.round(x), Math.round(y)]); }
        }
      }
      return misses;
    });
    cancellation?.throwIfCancelled();
    check('every point of a note text box grabs the note', noteMisses.length === 0, JSON.stringify(noteMisses));

    // Changing an already-saved mark is an unsaved edit, exactly like adding one.
    const savedNote = await page.locator('text.mark').first().boundingBox();
    cancellation?.throwIfCancelled();
    await page.mouse.move(savedNote.x + 4, savedNote.y + 4);
    cancellation?.throwIfCancelled();
    await page.mouse.down();
    cancellation?.throwIfCancelled();
    await page.mouse.move(savedNote.x + 44, savedNote.y + 44, { steps: 6 });
    cancellation?.throwIfCancelled();
    await page.mouse.up();
    cancellation?.throwIfCancelled();
    check('moving a saved note enables saving again', await page.locator('#btnSave').isEnabled());
    cancellation?.throwIfCancelled();
    let warnedOnLeave = false;
    const noteLeaveWarning = dialog => { if (dialog.type() === 'beforeunload') { warnedOnLeave = true; } };
    page.on('dialog', noteLeaveWarning);

    // The grab rules differ by mark kind, so each branch is pinned separately: highlights and
    // ink are drawn across in the drawing tools and move only in select; placed marks move from
    // any tool; a cancelled or non-primary press changes nothing; a move stops at the page edge.
    await openViewer();
    cancellation?.throwIfCancelled();
    page.off('dialog', noteLeaveWarning);
    check('leaving after moving a saved note warns about unsaved changes', warnedOnLeave);
    await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
    cancellation?.throwIfCancelled();
    const ov = await page.locator('svg.overlay').first().boundingBox();
    cancellation?.throwIfCancelled();
    const markCount = async () => Number(await page.locator('#markCount').textContent());
    const boxOf = selector => page.locator('svg.overlay').first().locator(selector).first().boundingBox();
    // Every press below lands above y ~ 570 of the overlay: with the overlay starting ~140 px
    // down, anything lower falls outside the default 720 px viewport and never reaches the page.
    async function drag(x0, y0, x1, y1) {
      await page.mouse.move(ov.x + x0, ov.y + y0);
      await page.mouse.down();
      await page.mouse.move(ov.x + x1, ov.y + y1, { steps: 8 });
      await page.mouse.up();
    }

    await page.locator('.tool[data-tool="highlight"]').click();
    cancellation?.throwIfCancelled();
    await drag(60, 300, 260, 330);
    cancellation?.throwIfCancelled();
    await drag(100, 315, 300, 345);
    cancellation?.throwIfCancelled();
    check('a highlight drag that starts on a highlight draws a new one', await markCount() === 2, String(await markCount()));
    cancellation?.throwIfCancelled();

    await page.locator('.tool[data-tool="select"]').click();
    cancellation?.throwIfCancelled();
    const hlBefore = await boxOf('rect.mark');
    cancellation?.throwIfCancelled();
    await drag(70, 305, 70, 405);
    cancellation?.throwIfCancelled();
    const hlAfter = await boxOf('rect.mark');
    cancellation?.throwIfCancelled();
    check('select moves a highlight', Math.abs(hlAfter.y - hlBefore.y - 100) < 3 && await markCount() === 2,
      JSON.stringify([hlBefore, hlAfter]));
    cancellation?.throwIfCancelled();

    await page.mouse.move(ov.x + 70, ov.y + 405);
    cancellation?.throwIfCancelled();
    await page.mouse.down();
    cancellation?.throwIfCancelled();
    await page.mouse.move(ov.x + 70, ov.y + 505, { steps: 8 });
    cancellation?.throwIfCancelled();
    await page.locator('svg.overlay').first().evaluate(svg => svg.dispatchEvent(
      new PointerEvent('pointercancel', { bubbles: true, pointerId: 1, isPrimary: true })));
    cancellation?.throwIfCancelled();
    await page.mouse.up();
    cancellation?.throwIfCancelled();
    const hlCancelled = await boxOf('rect.mark');
    cancellation?.throwIfCancelled();
    check('a cancelled drag leaves the mark where it was', Math.abs(hlCancelled.y - hlAfter.y) < 1.5,
      JSON.stringify([hlAfter, hlCancelled]));

    await page.mouse.click(ov.x + 70, ov.y + 405, { button: 'right' });
    cancellation?.throwIfCancelled();
    check('a right-click on a mark in select does not delete it', await markCount() === 2, String(await markCount()));
    cancellation?.throwIfCancelled();

    // A second touch lifting mid-drag must not commit (or cut short) the first pointer's move.
    const hlTouchBefore = await boxOf('rect.mark');
    cancellation?.throwIfCancelled();
    await page.mouse.move(ov.x + 70, ov.y + 405);
    cancellation?.throwIfCancelled();
    await page.mouse.down();
    cancellation?.throwIfCancelled();
    await page.mouse.move(ov.x + 70, ov.y + 445, { steps: 5 });
    cancellation?.throwIfCancelled();
    await page.locator('svg.overlay').first().evaluate(svg => svg.dispatchEvent(new PointerEvent('pointerup',
      { bubbles: true, pointerId: 99, isPrimary: false, pointerType: 'touch' })));
    cancellation?.throwIfCancelled();
    await page.mouse.move(ov.x + 70, ov.y + 505, { steps: 5 });
    cancellation?.throwIfCancelled();
    await page.mouse.up();
    cancellation?.throwIfCancelled();
    const hlTouchAfter = await boxOf('rect.mark');
    cancellation?.throwIfCancelled();
    check('another pointer lifting does not end a move in progress',
      Math.abs(hlTouchAfter.y - hlTouchBefore.y - 100) < 3, JSON.stringify([hlTouchBefore, hlTouchAfter]));

    // A stale drag is released by a same-kind primary press anywhere in the viewer, not only on
    // a page: here the press lands on the toolbar.
    const toolbarStaleBefore = await boxOf('rect.mark');
    cancellation?.throwIfCancelled();
    await page.mouse.move(toolbarStaleBefore.x + 6, toolbarStaleBefore.y + 6);
    cancellation?.throwIfCancelled();
    await page.mouse.down();
    cancellation?.throwIfCancelled();
    await page.mouse.move(toolbarStaleBefore.x + 6, toolbarStaleBefore.y + 46, { steps: 5 });
    cancellation?.throwIfCancelled();
    const heldBeforeToolbarPress = await page.locator('#btnSave').isDisabled();
    cancellation?.throwIfCancelled();
    await page.locator('.tool[data-tool="select"]').evaluate(button => button.dispatchEvent(new PointerEvent('pointerdown',
      { bubbles: true, pointerId: 1, isPrimary: true, button: 0, pointerType: 'mouse' })));
    cancellation?.throwIfCancelled();
    const releasedByToolbarPress = await page.locator('#btnSave').isEnabled();
    cancellation?.throwIfCancelled();
    const previewAfterToolbarPress = await page.locator('svg.overlay').first().locator('.moving').count();
    cancellation?.throwIfCancelled();
    await page.mouse.move(toolbarStaleBefore.x + 6, toolbarStaleBefore.y + 6, { steps: 5 });
    cancellation?.throwIfCancelled();
    await page.mouse.up();
    cancellation?.throwIfCancelled();
    const toolbarStaleAfter = await boxOf('rect.mark');
    cancellation?.throwIfCancelled();
    check('a same-kind press on the toolbar drops a stale drag and releases Save',
      heldBeforeToolbarPress && releasedByToolbarPress && previewAfterToolbarPress === 0
      && Math.abs(toolbarStaleAfter.y - toolbarStaleBefore.y) < 1.5 && await markCount() === 2,
      JSON.stringify([heldBeforeToolbarPress, releasedByToolbarPress, previewAfterToolbarPress, toolbarStaleBefore, toolbarStaleAfter]));
    cancellation?.throwIfCancelled();

    // A press by another kind of pointer on the same page must not end a live drag: a pen press
    // while the mouse drags leaves the mouse gesture alone (and takes no gesture of its own).
    // Dragged upward: the later drags below move the mark down, and it must stay in the viewport.
    const penBefore = await boxOf('rect.mark');
    cancellation?.throwIfCancelled();
    await page.mouse.move(penBefore.x + 6, penBefore.y + 6);
    cancellation?.throwIfCancelled();
    await page.mouse.down();
    cancellation?.throwIfCancelled();
    await page.mouse.move(penBefore.x + 6, penBefore.y - 24, { steps: 4 });
    cancellation?.throwIfCancelled();
    await page.locator('svg.overlay').first().evaluate(svg => svg.dispatchEvent(new PointerEvent('pointerdown',
      { bubbles: true, pointerId: 5, isPrimary: true, button: 0, pointerType: 'pen', clientX: -50, clientY: -50 })));
    cancellation?.throwIfCancelled();
    const heldThroughPen = await page.locator('#btnSave').isDisabled();
    cancellation?.throwIfCancelled();
    const previewThroughPen = await page.locator('svg.overlay').first().locator('.moving').count();
    cancellation?.throwIfCancelled();
    await page.mouse.move(penBefore.x + 6, penBefore.y - 54, { steps: 4 });
    cancellation?.throwIfCancelled();
    await page.mouse.up();
    cancellation?.throwIfCancelled();
    const penAfter = await boxOf('rect.mark');
    cancellation?.throwIfCancelled();
    check('a pen press on the same page does not end a mouse drag in progress',
      heldThroughPen && previewThroughPen === 1 && Math.abs(penAfter.y - penBefore.y + 60) < 3 && await markCount() === 2,
      JSON.stringify([heldThroughPen, previewThroughPen, penBefore, penAfter]));
    cancellation?.throwIfCancelled();

    // A gesture whose pointerup never arrives must not hold Save for the rest of the session. A
    // new primary press of the same pointer kind on ANOTHER page proves the old gesture is over:
    // the stale drag is dropped (its mark snaps back) and Save is released.
    const staleBefore = await boxOf('rect.mark');
    cancellation?.throwIfCancelled();
    await page.mouse.move(staleBefore.x + 6, staleBefore.y + 6);
    cancellation?.throwIfCancelled();
    await page.mouse.down();
    cancellation?.throwIfCancelled();
    await page.mouse.move(staleBefore.x + 6, staleBefore.y + 46, { steps: 5 });
    cancellation?.throwIfCancelled();
    const saveHeldByStale = await page.locator('#btnSave').isDisabled();
    cancellation?.throwIfCancelled();
    await page.locator('svg.overlay').nth(1).evaluate(svg => svg.dispatchEvent(new PointerEvent('pointerdown',
      { bubbles: true, pointerId: 1, isPrimary: true, button: 0, pointerType: 'mouse', clientX: -50, clientY: -50 })));
    cancellation?.throwIfCancelled();
    const saveAfterOtherPagePress = await page.locator('#btnSave').isEnabled();
    cancellation?.throwIfCancelled();
    const previewsAfterOtherPagePress = await page.locator('svg.overlay').first().locator('.moving').count();
    cancellation?.throwIfCancelled();
    // Back to the press point before lifting, so a build that still owns the drag commits no move
    // and the rest of the run is unaffected either way.
    await page.mouse.move(staleBefore.x + 6, staleBefore.y + 6, { steps: 5 });
    cancellation?.throwIfCancelled();
    await page.mouse.up();
    cancellation?.throwIfCancelled();
    const staleAfter = await boxOf('rect.mark');
    cancellation?.throwIfCancelled();
    check('a press on another page drops a stale drag and releases Save',
      saveHeldByStale && saveAfterOtherPagePress && previewsAfterOtherPagePress === 0
      && Math.abs(staleAfter.y - staleBefore.y) < 1.5 && await markCount() === 2,
      JSON.stringify([saveHeldByStale, saveAfterOtherPagePress, previewsAfterOtherPagePress, staleBefore, staleAfter]));
    cancellation?.throwIfCancelled();

    // A redraw in the middle of a drag (here a resize; the annotation font arriving does the
    // same) must not drop the preview: the mark stays where the pointer has taken it.
    const viewport = page.viewportSize();
    const hlRedrawBefore = await boxOf('rect.mark');
    cancellation?.throwIfCancelled();
    await page.mouse.move(hlRedrawBefore.x + 6, hlRedrawBefore.y + 6);
    cancellation?.throwIfCancelled();
    await page.mouse.down();
    cancellation?.throwIfCancelled();
    await page.mouse.move(hlRedrawBefore.x + 6, hlRedrawBefore.y + 66, { steps: 6 });
    cancellation?.throwIfCancelled();
    // The redraw replaces the mark's elements, so a measurement can land on one just detached;
    // retry until a box comes back.
    const settledBox = async selector => {
      for (let attempt = 0; attempt < 20; attempt++) {
        const box = await boxOf(selector);
        if (box) { return box; }
        await page.waitForTimeout(50);
      }
      return boxOf(selector);
    };
    await page.setViewportSize({ width: viewport.width, height: viewport.height + 40 });
    cancellation?.throwIfCancelled();
    await page.waitForTimeout(150);
    cancellation?.throwIfCancelled();
    const hlDuringRedraw = await settledBox('rect.mark');
    cancellation?.throwIfCancelled();
    await page.mouse.up();
    cancellation?.throwIfCancelled();
    await page.setViewportSize(viewport);
    cancellation?.throwIfCancelled();
    const hlRedrawAfter = await settledBox('rect.mark');
    cancellation?.throwIfCancelled();
    check('a redraw during a drag keeps the dragged mark under the pointer',
      Math.abs(hlDuringRedraw.y - hlRedrawBefore.y - 60) < 3 && Math.abs(hlRedrawAfter.y - hlRedrawBefore.y - 60) < 3,
      JSON.stringify([hlRedrawBefore, hlDuringRedraw, hlRedrawAfter]));

    // Save is held while a mark is being dragged: a save taken mid-drag would post the mark
    // where it was and the drag would then be lost under the in-flight save.
    let saveDuringDrag = false;
    const watchDragSave = request => { if (request.url().includes('/SaveAnnotatedDocument')) { saveDuringDrag = true; } };
    page.on('request', watchDragSave);
    // The resize restored above redraws the page, so read the mark once it has settled.
    const hlSaveBefore = await settledBox('rect.mark');
    cancellation?.throwIfCancelled();
    await page.mouse.move(hlSaveBefore.x + 6, hlSaveBefore.y + 6);
    cancellation?.throwIfCancelled();
    await page.mouse.down();
    cancellation?.throwIfCancelled();
    await page.mouse.move(hlSaveBefore.x + 6, hlSaveBefore.y - 34, { steps: 6 });
    cancellation?.throwIfCancelled();
    const saveHeldDuringDrag = await page.locator('#btnSave').isDisabled();
    cancellation?.throwIfCancelled();
    await page.locator('#btnSave').evaluate(button => button.click());
    cancellation?.throwIfCancelled();
    await page.mouse.up();
    cancellation?.throwIfCancelled();
    page.off('request', watchDragSave);
    const hlSaveAfter = await settledBox('rect.mark');
    cancellation?.throwIfCancelled();
    check('Save is held during a drag and the drag still lands',
      saveHeldDuringDrag && !saveDuringDrag && await page.locator('#btnSave').isEnabled()
      && Math.abs(hlSaveAfter.y - hlSaveBefore.y + 40) < 3,
      JSON.stringify([saveHeldDuringDrag, saveDuringDrag, hlSaveBefore, hlSaveAfter]));
    cancellation?.throwIfCancelled();

    // The page moving under the pointer mid-drag (a scroll here; a resize that re-centres the
    // page is the same) must not leave the mark behind: it follows the pointer on the page.
    const scrollOverlayBefore = await page.locator('svg.overlay').first().boundingBox();
    cancellation?.throwIfCancelled();
    const hlScrollBefore = await settledBox('rect.mark');
    cancellation?.throwIfCancelled();
    await page.mouse.move(hlScrollBefore.x + 6, hlScrollBefore.y + 6);
    cancellation?.throwIfCancelled();
    await page.mouse.down();
    cancellation?.throwIfCancelled();
    await page.mouse.move(hlScrollBefore.x + 6, hlScrollBefore.y + 46, { steps: 6 });
    cancellation?.throwIfCancelled();
    await page.evaluate(() => window.scrollBy({ top: 30, behavior: 'instant' }));
    cancellation?.throwIfCancelled();
    await page.mouse.move(hlScrollBefore.x + 6, hlScrollBefore.y + 47, { steps: 2 });
    cancellation?.throwIfCancelled();
    await page.mouse.up();
    cancellation?.throwIfCancelled();
    const scrollOverlayAfter = await page.locator('svg.overlay').first().boundingBox();
    cancellation?.throwIfCancelled();
    const hlScrollAfter = await settledBox('rect.mark');
    cancellation?.throwIfCancelled();
    // The press was 6 px into the mark and the last move 47 px, so the pointer travelled 41 px
    // on screen, plus however far the page scrolled up under it.
    const pointerOnPage = 41 + (scrollOverlayBefore.y - scrollOverlayAfter.y);
    const markOnPage = (hlScrollAfter.y - scrollOverlayAfter.y) - (hlScrollBefore.y - scrollOverlayBefore.y);
    await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
    cancellation?.throwIfCancelled();
    check('a drag keeps the mark under the pointer when the page scrolls mid-drag',
      Math.abs(markOnPage - pointerOnPage) < 3, JSON.stringify({ pointerOnPage, markOnPage }));

    await page.locator('.swatch[data-color="black"]').click();
    cancellation?.throwIfCancelled();
    await page.locator('.tool[data-tool="draw"]').click();
    cancellation?.throwIfCancelled();
    await drag(400, 250, 600, 250);
    cancellation?.throwIfCancelled();
    await page.locator('.tool[data-tool="select"]').click();
    cancellation?.throwIfCancelled();
    const inkBefore = await boxOf('g.mark[data-kind="stroke"]');
    cancellation?.throwIfCancelled();
    await drag(500, 250, 500, 350);
    cancellation?.throwIfCancelled();
    const inkAfter = await boxOf('g.mark[data-kind="stroke"]');
    cancellation?.throwIfCancelled();
    check('select moves an ink stroke by its hit area', Math.abs(inkAfter.y - inkBefore.y - 100) < 3 && await markCount() === 3,
      JSON.stringify([inkBefore, inkAfter]));
    cancellation?.throwIfCancelled();

    await page.locator('.tool[data-tool="signature"]').click();
    cancellation?.throwIfCancelled();
    await page.mouse.click(ov.x + 400, ov.y + 150);
    cancellation?.throwIfCancelled();
    await page.locator('.tool[data-tool="date"]').click();
    cancellation?.throwIfCancelled();
    const sigBefore = await boxOf('g.mark[data-kind="placed"]');
    cancellation?.throwIfCancelled();
    await drag(sigBefore.x - ov.x + 10, sigBefore.y - ov.y + 10, sigBefore.x - ov.x + 10, sigBefore.y - ov.y + 160);
    cancellation?.throwIfCancelled();
    const sigAfter = await boxOf('g.mark[data-kind="placed"]');
    cancellation?.throwIfCancelled();
    check('a signature can be dragged while the date tool is active',
      Math.abs(sigAfter.y - sigBefore.y - 150) < 3 && await markCount() === 4, JSON.stringify([sigBefore, sigAfter]));
    cancellation?.throwIfCancelled();

    await page.locator('.tool[data-tool="text"]').click();
    cancellation?.throwIfCancelled();
    // Longer than a note's default box, so the edge clamp must use the text's drawn width or
    // the save below is refused with "The annotation text extends beyond the page".
    page.once('dialog', dialog => dialog.accept('Synthetic edge note, deliberately longer than the default note box'));
    await page.mouse.click(ov.x + 200, ov.y + 200);
    cancellation?.throwIfCancelled();
    const edgeBefore = await boxOf('text.mark');
    cancellation?.throwIfCancelled();
    await drag(edgeBefore.x - ov.x + 4, edgeBefore.y - ov.y + 4, 5000, 5000);
    cancellation?.throwIfCancelled();
    const edgeAfter = await boxOf('text.mark');
    cancellation?.throwIfCancelled();
    check('a move stops at the page edge',
      edgeAfter.x > edgeBefore.x && edgeAfter.x + edgeAfter.width <= ov.x + ov.width + 1
      && edgeAfter.y + edgeAfter.height <= ov.y + ov.height + 1, JSON.stringify([edgeAfter, ov]));

    page.once('dialog', dialog => dialog.accept('Synthetic note to clear'));
    await page.mouse.click(ov.x + 200, ov.y + 250);
    cancellation?.throwIfCancelled();
    const clearBox = await page.locator('svg.overlay').first().locator('text.mark').nth(1).boundingBox();
    cancellation?.throwIfCancelled();
    page.once('dialog', dialog => dialog.accept(''));
    await page.mouse.click(clearBox.x + 4, clearBox.y + 4);
    cancellation?.throwIfCancelled();
    check('clearing a note removes it', await markCount() === 5
      && await page.locator('svg.overlay').first().locator('text.mark').count() === 1, String(await markCount()));
    cancellation?.throwIfCancelled();

    // Signature save needs a provider stamp this deployment may not have, so the mark is removed
    // (which also pins that a select click still deletes a signature) before the save below.
    await page.locator('.tool[data-tool="select"]').click();
    cancellation?.throwIfCancelled();
    await page.mouse.click(sigAfter.x + 10, sigAfter.y + 10);
    cancellation?.throwIfCancelled();
    check('a select click still deletes a signature', await markCount() === 4, String(await markCount()));
    cancellation?.throwIfCancelled();
    await page.locator('#btnSave').click();
    cancellation?.throwIfCancelled();
    await waitForSave();
    cancellation?.throwIfCancelled();
    check('marks moved to the page edge pass server validation',
      await page.locator('#status').getAttribute('class') === 'status ok',
      await page.locator('#status').textContent());
    cancellation?.throwIfCancelled();

    // Drawing tools never grab; an ink stroke's invisible halo does not hide the note beneath it;
    // a date-tool click inside a signature still stamps a date; a short note reaches the right
    // margin; an edit that lengthens a note keeps it on the page.
    await openViewer();
    cancellation?.throwIfCancelled();
    await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
    cancellation?.throwIfCancelled();
    const pageBox = await page.locator('svg.overlay').first().boundingBox();
    cancellation?.throwIfCancelled();
    const overlayMark = selector => page.locator('svg.overlay').first().locator(selector).first();
    async function dragOn(x0, y0, x1, y1) {
      await page.mouse.move(pageBox.x + x0, pageBox.y + y0);
      await page.mouse.down();
      await page.mouse.move(pageBox.x + x1, pageBox.y + y1, { steps: 8 });
      await page.mouse.up();
    }

    await page.locator('.tool[data-tool="text"]').click();
    cancellation?.throwIfCancelled();
    page.once('dialog', dialog => dialog.accept('Synthetic crossed note'));
    await page.mouse.click(pageBox.x + 100, pageBox.y + 300);
    cancellation?.throwIfCancelled();
    const crossed = await overlayMark('text.mark').boundingBox();
    cancellation?.throwIfCancelled();
    const cx = crossed.x - pageBox.x;
    const cy = crossed.y - pageBox.y;
    // The cursor must not advertise a drag the tool will not do: over a note a placing tool
    // shows the move cursor, while the drawing tools keep the crosshair.
    const cursorOverNote = () => page.evaluate(([x, y]) => getComputedStyle(document.elementFromPoint(x, y)).cursor,
      [crossed.x + 20, crossed.y + crossed.height / 2]);
    const textCursor = await cursorOverNote();
    cancellation?.throwIfCancelled();
    await page.locator('.tool[data-tool="highlight"]').click();
    cancellation?.throwIfCancelled();
    const highlightCursor = await cursorOverNote();
    cancellation?.throwIfCancelled();
    check('a note shows the move cursor only in tools that can grab it',
      textCursor === 'move' && highlightCursor === 'crosshair', JSON.stringify([textCursor, highlightCursor]));
    await page.locator('.tool[data-tool="highlight"]').click();
    cancellation?.throwIfCancelled();
    await dragOn(cx + 5, cy + 5, cx + 200, cy + 25);
    cancellation?.throwIfCancelled();
    const crossedAfterHighlight = await overlayMark('text.mark').boundingBox();
    cancellation?.throwIfCancelled();
    check('a highlight drag that starts on a note draws a highlight and leaves the note',
      await markCount() === 2 && Math.abs(crossedAfterHighlight.x - crossed.x) < 1 && Math.abs(crossedAfterHighlight.y - crossed.y) < 1,
      JSON.stringify([await markCount(), crossed, crossedAfterHighlight]));
    cancellation?.throwIfCancelled();
    // The highlight now lies over the note. In the text tool a press there grabs the note (the
    // hit test looks through the highlight), so the cursor over the highlight must say so.
    await page.locator('.tool[data-tool="text"]').click();
    cancellation?.throwIfCancelled();
    await page.mouse.move(crossed.x + 20, crossed.y + 8, { steps: 3 });
    cancellation?.throwIfCancelled();
    const throughCursor = await page.evaluate(([x, y]) => [document.elementFromPoint(x, y).tagName,
      getComputedStyle(document.elementFromPoint(x, y)).cursor], [crossed.x + 20, crossed.y + 8]);
    cancellation?.throwIfCancelled();
    await page.mouse.move(pageBox.x + 400, pageBox.y + 500);
    cancellation?.throwIfCancelled();
    check('the cursor over a highlight covering a note shows the grab the text tool will make',
      throughCursor[0] === 'rect' && throughCursor[1] === 'move', JSON.stringify(throughCursor));

    // Drawn after the note, so the ink's hit halo sits above it in the overlay.
    await page.locator('.swatch[data-color="black"]').click();
    cancellation?.throwIfCancelled();
    await page.locator('.tool[data-tool="draw"]').click();
    cancellation?.throwIfCancelled();
    await dragOn(cx - 10, cy + 8, cx + 150, cy + 8);
    cancellation?.throwIfCancelled();
    await page.locator('.tool[data-tool="text"]').click();
    cancellation?.throwIfCancelled();
    let promptedWith = null;
    page.once('dialog', dialog => { promptedWith = dialog.defaultValue(); dialog.dismiss(); });
    await page.mouse.click(crossed.x + 30, crossed.y + 8);
    cancellation?.throwIfCancelled();
    check('an ink halo over a note does not hide the note from the text tool',
      promptedWith === 'Synthetic crossed note' && await markCount() === 3, JSON.stringify([promptedWith, await markCount()]));
    cancellation?.throwIfCancelled();

    await page.locator('.tool[data-tool="signature"]').click();
    cancellation?.throwIfCancelled();
    await page.mouse.click(pageBox.x + 450, pageBox.y + 150);
    cancellation?.throwIfCancelled();
    await page.locator('.tool[data-tool="date"]').click();
    cancellation?.throwIfCancelled();
    const signatureBox = await overlayMark('g.mark[data-kind="placed"]').boundingBox();
    cancellation?.throwIfCancelled();
    await page.mouse.click(signatureBox.x + 20, signatureBox.y + 20);
    cancellation?.throwIfCancelled();
    check('a date-tool click inside a signature stamps a date', await markCount() === 5, String(await markCount()));
    cancellation?.throwIfCancelled();

    const dateStamp = page.locator('svg.overlay').first().locator('text.mark').filter({ hasText: /^\d{4}-\d{2}-\d{2}$/ });
    const dateBefore = await dateStamp.boundingBox();
    cancellation?.throwIfCancelled();
    await dragOn(dateBefore.x - pageBox.x + 4, dateBefore.y - pageBox.y + 4, 5000, dateBefore.y - pageBox.y + 4);
    cancellation?.throwIfCancelled();
    const dateAfter = await dateStamp.boundingBox();
    cancellation?.throwIfCancelled();
    check('a short note can be dragged to the right margin',
      dateAfter.x + dateAfter.width <= pageBox.x + pageBox.width + 1
      && dateAfter.x + dateAfter.width >= pageBox.x + pageBox.width - 24, JSON.stringify([dateAfter, pageBox]));

    await dragOn(cx + 30, cy + 8, 5000, cy + 8);
    cancellation?.throwIfCancelled();
    page.once('dialog', dialog => dialog.accept('Synthetic crossed note, now edited to be much longer than it was'));
    const crossedAtEdge = await overlayMark('text.mark').boundingBox();
    cancellation?.throwIfCancelled();
    await page.mouse.click(crossedAtEdge.x + 10, crossedAtEdge.y + 8);
    cancellation?.throwIfCancelled();
    const crossedEdited = await overlayMark('text.mark').boundingBox();
    cancellation?.throwIfCancelled();
    check('editing a note to longer text keeps it on the page',
      crossedEdited.width > crossedAtEdge.width && crossedEdited.x + crossedEdited.width <= pageBox.x + pageBox.width + 1,
      JSON.stringify([crossedAtEdge, crossedEdited, pageBox]));

    // No provider stamp is guaranteed here, so the signature goes before the save.
    await page.locator('.tool[data-tool="select"]').click();
    cancellation?.throwIfCancelled();
    await page.mouse.click(signatureBox.x + signatureBox.width - 10, signatureBox.y + signatureBox.height - 10);
    cancellation?.throwIfCancelled();
    await page.locator('#btnSave').click();
    cancellation?.throwIfCancelled();
    await waitForSave();
    cancellation?.throwIfCancelled();
    check('marks moved and edited under the grab rules save', await markCount() === 4
      && await page.locator('#status').getAttribute('class') === 'status ok', await page.locator('#status').textContent());
    cancellation?.throwIfCancelled();

    // Deleting an already-saved mark is an unsaved change too: Save comes back and leaving warns.
    // The click lands on the highlight's far end, clear of the ink stroke drawn across its start.
    const savedHighlight = await overlayMark('rect.mark').boundingBox();
    cancellation?.throwIfCancelled();
    await page.mouse.click(savedHighlight.x + savedHighlight.width - 6, savedHighlight.y + savedHighlight.height - 4);
    cancellation?.throwIfCancelled();
    check('deleting a saved mark enables saving again', await markCount() === 3
      && await page.locator('svg.overlay').first().locator('rect.mark').count() === 0
      && await page.locator('#btnSave').isEnabled(), String(await markCount()));
    cancellation?.throwIfCancelled();
    let warnedAfterDelete = false;
    const deleteLeaveWarning = dialog => { if (dialog.type() === 'beforeunload') { warnedAfterDelete = true; } };
    page.on('dialog', deleteLeaveWarning);

    await openViewer();
    cancellation?.throwIfCancelled();
    page.off('dialog', deleteLeaveWarning);
    check('leaving after deleting a saved mark warns about unsaved changes', warnedAfterDelete);

    // A note placed before the annotation font arrives is fitted to its width in the fallback
    // face. When the real, wider face lands the note must be refitted, or it runs off the page
    // and the composer refuses the save. The font is held back and a narrower fallback forced
    // (through the viewer's own stylesheet, since the page CSP admits no injected style) so the
    // two widths genuinely differ.
    // Those scenarios need "Liberation Sans" installed where the browser runs (Debian/Ubuntu
    // package fonts-liberation). Without it the fallback resolves to DejaVu Sans, the same
    // face as the annotation font, the widths never differ, and the refusal the scenarios
    // provoke never happens. Say so plainly instead of timing out on a disabled button.
    const liberationInstalled = await page.evaluate(() => {
      const context = document.createElement('canvas').getContext('2d');
      const sample = 'Synthetic note saved before a very late annotation font arrived';
      context.font = '11px "Liberation Sans", monospace';
      const liberation = context.measureText(sample).width;
      context.font = '11px monospace';
      return Math.abs(liberation - context.measureText(sample).width) > 1;
    });
    cancellation?.throwIfCancelled();
    if (!liberationInstalled) {
      throw new Error('The late-font scenarios need the "Liberation Sans" font where the browser runs '
        + '(install fonts-liberation); it resolves to a fallback face here');
    }
    let releaseFont;
    const fontHeld = new Promise(resolve => { releaseFont = resolve; cleanupReleases.push(resolve); });
    await page.route('**/dejavufonts/ttf/DejaVuSans.ttf', async route => {
      await fontHeld;
      await route.continue();
    });
    cancellation?.throwIfCancelled();
    await openViewer('domcontentloaded');
    cancellation?.throwIfCancelled();
    const narrowFallback = await page.evaluate(() => {
      const sheet = [...document.styleSheets].find(s => (s.href || '').includes('documentAnnotate.css'));
      if (!sheet) { return false; }
      sheet.insertRule('.page svg text { font-family: CarlosAnnotation, "Liberation Sans" !important; }', sheet.cssRules.length);
      return !document.fonts.check('11px CarlosAnnotation');
    });
    cancellation?.throwIfCancelled();
    check('the annotation font is still loading while the note is placed', narrowFallback);
    await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
    cancellation?.throwIfCancelled();
    const fontBox = await page.locator('svg.overlay').first().boundingBox();
    cancellation?.throwIfCancelled();
    await page.locator('.tool[data-tool="text"]').click();
    cancellation?.throwIfCancelled();
    page.once('dialog', dialog => dialog.accept('Synthetic note placed before the annotation font had loaded here'));
    await page.mouse.click(fontBox.x + fontBox.width - 10, fontBox.y + 200);
    cancellation?.throwIfCancelled();
    const fallbackNote = await page.locator('svg.overlay').first().locator('text.mark').first().boundingBox();
    cancellation?.throwIfCancelled();
    // Save is pressed while the font is still out: it must wait for the font and post the
    // refitted note, not a snapshot taken at the fallback width.
    let fontSavePosted = false;
    const watchFontSave = request => { if (request.url().includes('/SaveAnnotatedDocument')) { fontSavePosted = true; } };
    page.on('request', watchFontSave);
    await page.locator('#btnSave').click();
    cancellation?.throwIfCancelled();
    await page.waitForTimeout(500);
    cancellation?.throwIfCancelled();
    check('a save pressed before the annotation font loads waits for it', !fontSavePosted
      && await page.locator('#status').getAttribute('class') === 'status busy',
      JSON.stringify([fontSavePosted, await page.locator('#status').getAttribute('class')]));
    cancellation?.throwIfCancelled();
    releaseFont();
    await waitForSave();
    cancellation?.throwIfCancelled();
    page.off('request', watchFontSave);
    await page.unroute('**/dejavufonts/ttf/DejaVuSans.ttf');
    cancellation?.throwIfCancelled();
    const fontNote = await page.locator('svg.overlay').first().locator('text.mark').first().boundingBox();
    cancellation?.throwIfCancelled();
    check('a note placed before the font loaded is refitted onto the page once it arrives',
      fontNote.width > fallbackNote.width && fontNote.x + fontNote.width <= fontBox.x + fontBox.width + 1,
      JSON.stringify([fallbackNote, fontNote, fontBox]));
    check('a note refitted after the font loaded saves',
      await page.locator('#status').getAttribute('class') === 'status ok', await page.locator('#status').textContent());
    cancellation?.throwIfCancelled();
    await openViewer();
    cancellation?.throwIfCancelled();

    // A page with no notes has nothing to measure in the annotation font, so its save must not
    // wait for the font: with the font held, a highlight-only save posts at once.
    let releaseNoNoteFont;
    const noNoteFontHeld = new Promise(resolve => { releaseNoNoteFont = resolve; cleanupReleases.push(resolve); });
    await page.route('**/dejavufonts/ttf/DejaVuSans.ttf', async route => {
      await noNoteFontHeld;
      await route.continue();
    });
    cancellation?.throwIfCancelled();
    await openViewer('domcontentloaded');
    cancellation?.throwIfCancelled();
    await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
    cancellation?.throwIfCancelled();
    const noNoteBox = await page.locator('svg.overlay').first().boundingBox();
    cancellation?.throwIfCancelled();
    await page.locator('.tool[data-tool="highlight"]').click();
    cancellation?.throwIfCancelled();
    await page.mouse.move(noNoteBox.x + 60, noNoteBox.y + 120);
    cancellation?.throwIfCancelled();
    await page.mouse.down();
    cancellation?.throwIfCancelled();
    await page.mouse.move(noNoteBox.x + 220, noNoteBox.y + 150, { steps: 6 });
    cancellation?.throwIfCancelled();
    await page.mouse.up();
    cancellation?.throwIfCancelled();
    let noNoteSavePosted = false;
    const watchNoNoteSave = request => { if (request.url().includes('/SaveAnnotatedDocument')) { noNoteSavePosted = true; } };
    page.on('request', watchNoNoteSave);
    await page.locator('#btnSave').click();
    cancellation?.throwIfCancelled();
    await page.waitForTimeout(700);
    cancellation?.throwIfCancelled();
    const postedWithoutFont = noNoteSavePosted;
    releaseNoNoteFont();
    await waitForSave();
    cancellation?.throwIfCancelled();
    page.off('request', watchNoNoteSave);
    await page.unroute('**/dejavufonts/ttf/DejaVuSans.ttf');
    cancellation?.throwIfCancelled();
    check('a save with no notes does not wait for the annotation font',
      postedWithoutFont && await page.locator('#status').getAttribute('class') === 'status ok',
      JSON.stringify([postedWithoutFont, await page.locator('#status').getAttribute('class')]));
    cancellation?.throwIfCancelled();

    // If the font is later than the save's wait, the save posts fallback widths. A refit arriving
    // while that request is out must not change the model under it (the page would then report
    // as saved marks the filed copy lacks); it runs once the save ends. Here the fallback-width
    // note overruns the page in the real face, so the save is refused, the deferred refit pulls
    // the note back, and the retry files.
    let releaseLateFont;
    const lateFontHeld = new Promise(resolve => { releaseLateFont = resolve; cleanupReleases.push(resolve); });
    await page.route('**/dejavufonts/ttf/DejaVuSans.ttf', async route => {
      await lateFontHeld;
      await route.continue();
    });
    cancellation?.throwIfCancelled();
    await openViewer('domcontentloaded');
    cancellation?.throwIfCancelled();
    await page.evaluate(() => {
      const sheet = [...document.styleSheets].find(s => (s.href || '').includes('documentAnnotate.css'));
      sheet.insertRule('.page svg text { font-family: CarlosAnnotation, "Liberation Sans" !important; }', sheet.cssRules.length);
      window.scrollTo({ top: 0, behavior: 'instant' });
    });
    cancellation?.throwIfCancelled();
    const lateBox = await page.locator('svg.overlay').first().boundingBox();
    cancellation?.throwIfCancelled();
    await page.locator('.tool[data-tool="text"]').click();
    cancellation?.throwIfCancelled();
    page.once('dialog', dialog => dialog.accept('Synthetic note saved before a very late annotation font arrived'));
    await page.mouse.click(lateBox.x + lateBox.width - 10, lateBox.y + 200);
    cancellation?.throwIfCancelled();
    const lateNote = page.locator('svg.overlay').first().locator('text.mark').first();
    const lateBefore = await lateNote.boundingBox();
    cancellation?.throwIfCancelled();
    let releaseLateSave;
    const lateSaveHeld = new Promise(resolve => { releaseLateSave = resolve; cleanupReleases.push(resolve); });
    let lateSaveSeen;
    const lateSaveArrived = new Promise(resolve => { lateSaveSeen = resolve; });
    await page.route('**/SaveAnnotatedDocument?*', async route => {
      lateSaveSeen();
      await lateSaveHeld;
      await route.continue();
    });
    cancellation?.throwIfCancelled();
    await page.locator('#btnSave').click();
    cancellation?.throwIfCancelled();
    await Promise.race([lateSaveArrived,
      new Promise((_, reject) => setTimeout(() => reject(new Error('Save did not post after the font wait')), 15000))]);
    cancellation?.throwIfCancelled();
    releaseLateFont();
    await page.waitForFunction(() => document.fonts.check('11px CarlosAnnotation'));
    cancellation?.throwIfCancelled();
    await page.waitForTimeout(300);
    cancellation?.throwIfCancelled();
    const lateInFlight = await lateNote.boundingBox();
    cancellation?.throwIfCancelled();
    check('a font arriving during a save does not move a note under the in-flight request',
      Math.abs(lateInFlight.x - lateBefore.x) < 1, JSON.stringify([lateBefore, lateInFlight]));
    releaseLateSave();
    await waitForSave();
    cancellation?.throwIfCancelled();
    await page.unroute('**/SaveAnnotatedDocument?*');
    cancellation?.throwIfCancelled();
    await page.unroute('**/dejavufonts/ttf/DejaVuSans.ttf');
    cancellation?.throwIfCancelled();
    await page.waitForTimeout(300);
    cancellation?.throwIfCancelled();
    const lateAfter = await lateNote.boundingBox();
    cancellation?.throwIfCancelled();
    check('the refit deferred by a save runs once it ends',
      lateAfter.x + lateAfter.width <= lateBox.x + lateBox.width + 1 && await page.locator('#btnSave').isEnabled(),
      JSON.stringify([await page.locator('#status').textContent(), lateAfter, lateBox]));
    cancellation?.throwIfCancelled();
    await page.locator('#btnSave').click();
    cancellation?.throwIfCancelled();
    await waitForSave();
    cancellation?.throwIfCancelled();
    check('a note refitted after its save ended saves on retry',
      await page.locator('#status').getAttribute('class') === 'status ok', await page.locator('#status').textContent());
    cancellation?.throwIfCancelled();
    await openViewer();
    cancellation?.throwIfCancelled();

    // A save that fails before fetch() starts has sent nothing, so Save must stay usable. Building
    // the request body is the last step before the request leaves: fail it once, for the save
    // payload only, and prove no request went out, the error is shown and a retry files.
    await page.evaluate(() => {
      const original = JSON.stringify;
      JSON.stringify = function (value, ...rest) {
        if (value && typeof value === 'object' && 'sourceDigest' in value) {
          JSON.stringify = original;
          throw new Error('synthetic save-body failure');
        }
        return original.call(this, value, ...rest);
      };
    });
    cancellation?.throwIfCancelled();
    let preSendPosts = 0;
    const countPreSend = request => { if (request.url().includes('/SaveAnnotatedDocument')) { preSendPosts += 1; } };
    page.on('request', countPreSend);
    await mark();
    cancellation?.throwIfCancelled();
    await page.locator('#btnSave').click();
    cancellation?.throwIfCancelled();
    await waitForSave();
    cancellation?.throwIfCancelled();
    page.off('request', countPreSend);
    check('a save that fails before its request leaves keeps Save enabled',
      preSendPosts === 0 && await page.locator('#status').getAttribute('class') === 'status error'
        && await page.locator('#btnSave').isEnabled(),
      JSON.stringify([preSendPosts, await page.locator('#status').textContent()]));
    cancellation?.throwIfCancelled();
    await page.locator('#btnSave').click();
    cancellation?.throwIfCancelled();
    await waitForSave();
    cancellation?.throwIfCancelled();
    check('the retry after a pre-send failure files the copy',
      await page.locator('#status').getAttribute('class') === 'status ok', await page.locator('#status').textContent());
    cancellation?.throwIfCancelled();
    await openViewer();
    cancellation?.throwIfCancelled();

    // Once fetch() has started the server may already have filed the copy, even if the
    // connection drops before any reply. Save must then be held so the operator checks first.
    await page.route('**/SaveAnnotatedDocument?*', route => fixture.abort(route,'connectionreset'));
    cancellation?.throwIfCancelled();
    await mark();
    cancellation?.throwIfCancelled();
    await page.locator('#btnSave').click();
    cancellation?.throwIfCancelled();
    await waitForSave();
    cancellation?.throwIfCancelled();
    await page.unroute('**/SaveAnnotatedDocument?*');
    cancellation?.throwIfCancelled();
    check('a save whose connection drops after sending holds Save until the operator checks',
      await page.locator('#status').getAttribute('class') === 'status error'
        && !await page.locator('#btnSave').isEnabled() && !await page.locator('#btnSaveFax').isEnabled(),
      await page.locator('#status').textContent());
    cancellation?.throwIfCancelled();
    await openViewer();
    cancellation?.throwIfCancelled();

    // The composer measures a note's full string, spaces included. A note ending in spaces,
    // placed hard against the right edge, must be fitted by that width or the save is refused.
    await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
    cancellation?.throwIfCancelled();
    const spaceBox = await page.locator('svg.overlay').first().boundingBox();
    cancellation?.throwIfCancelled();
    await page.locator('.tool[data-tool="text"]').click();
    cancellation?.throwIfCancelled();
    page.once('dialog', dialog => dialog.accept('Synthetic note with trailing spaces' + ' '.repeat(24)));
    await page.mouse.click(spaceBox.x + spaceBox.width - 10, spaceBox.y + 260);
    cancellation?.throwIfCancelled();
    await page.locator('#btnSave').click();
    cancellation?.throwIfCancelled();
    await waitForSave();
    cancellation?.throwIfCancelled();
    check('a note ending in spaces fits the page by its full width and saves',
      await page.locator('#status').getAttribute('class') === 'status ok', await page.locator('#status').textContent());
    cancellation?.throwIfCancelled();
    // A note that begins with spaces paints its first glyph after them, but the hit box must
    // start at the text origin, so a press on the leading spaces grabs the note rather than
    // placing a new one.
    page.once('dialog', dialog => dialog.accept(' '.repeat(12) + 'Synthetic note with leading spaces'));
    // Placed hard against the left edge, so the note's origin is 0: a valid origin the hit box
    // must keep, not a missing one to fall back from.
    await page.mouse.click(spaceBox.x, spaceBox.y + 300);
    cancellation?.throwIfCancelled();
    const leadingNote = page.locator('svg.overlay').first().locator('text.mark').last();
    const leadingOrigin = await leadingNote.evaluate(text => ({ x: Number(text.getAttribute('x')), y: text.getBBox().y + text.getBBox().height / 2 }));
    cancellation?.throwIfCancelled();
    const leadingHitX = await page.locator('svg.overlay').first().locator('rect.mark-hit').last().getAttribute('x');
    cancellation?.throwIfCancelled();
    check('a note at the left edge keeps its hit box at the zero origin',
      leadingOrigin.x < 1 && Number(leadingHitX) === leadingOrigin.x, JSON.stringify([leadingOrigin, leadingHitX]));
    const leadingBefore = await leadingNote.boundingBox();
    cancellation?.throwIfCancelled();
    const marksBeforeLeading = await markCount();
    cancellation?.throwIfCancelled();
    let promptedOnLeading = false;
    const leadingPrompt = dialog => { promptedOnLeading = true; dialog.dismiss(); };
    page.on('dialog', leadingPrompt);
    await page.mouse.move(spaceBox.x + leadingOrigin.x + 3, spaceBox.y + leadingOrigin.y);
    cancellation?.throwIfCancelled();
    await page.mouse.down();
    cancellation?.throwIfCancelled();
    await page.mouse.move(spaceBox.x + leadingOrigin.x + 63, spaceBox.y + leadingOrigin.y + 40, { steps: 6 });
    cancellation?.throwIfCancelled();
    await page.mouse.up();
    cancellation?.throwIfCancelled();
    page.off('dialog', leadingPrompt);
    const leadingAfter = await leadingNote.boundingBox();
    cancellation?.throwIfCancelled();
    check('a press on a note\'s leading spaces grabs the note',
      !promptedOnLeading && await markCount() === marksBeforeLeading
      && Math.abs(leadingAfter.x - leadingBefore.x - 60) < 3 && Math.abs(leadingAfter.y - leadingBefore.y - 40) < 3,
      JSON.stringify([promptedOnLeading, marksBeforeLeading, await markCount(), leadingBefore, leadingAfter]));
    cancellation?.throwIfCancelled();
    await openViewer();
    cancellation?.throwIfCancelled();

    // The annotation font arriving redraws a page with notes once (the refit), not once for a
    // whole-viewer redraw and again for the refit. Counted by the note elements a redraw removes.
    let releaseCountedFont;
    const countedFontHeld = new Promise(resolve => { releaseCountedFont = resolve; cleanupReleases.push(resolve); });
    await page.route('**/dejavufonts/ttf/DejaVuSans.ttf', async route => {
      await countedFontHeld;
      await route.continue();
    });
    cancellation?.throwIfCancelled();
    await openViewer('domcontentloaded');
    cancellation?.throwIfCancelled();
    await page.evaluate(() => {
      const sheet = [...document.styleSheets].find(s => (s.href || '').includes('documentAnnotate.css'));
      sheet.insertRule('.page svg text { font-family: CarlosAnnotation, "Liberation Sans" !important; }', sheet.cssRules.length);
      window.scrollTo({ top: 0, behavior: 'instant' });
    });
    cancellation?.throwIfCancelled();
    const countedBox = await page.locator('svg.overlay').first().boundingBox();
    cancellation?.throwIfCancelled();
    await page.locator('.tool[data-tool="text"]').click();
    cancellation?.throwIfCancelled();
    page.once('dialog', dialog => dialog.accept('Synthetic note counted through the font arrival'));
    await page.mouse.click(countedBox.x + countedBox.width - 10, countedBox.y + 200);
    cancellation?.throwIfCancelled();
    await page.evaluate(() => {
      window.__removedNotes = 0;
      new MutationObserver(records => {
        for (const record of records) {
          for (const node of record.removedNodes) {
            if (node.matches && node.matches('text.mark')) { window.__removedNotes += 1; }
          }
        }
      }).observe(document.querySelector('svg.overlay'), { childList: true });
    });
    cancellation?.throwIfCancelled();
    releaseCountedFont();
    await page.waitForFunction(() => document.fonts.check('11px CarlosAnnotation'));
    cancellation?.throwIfCancelled();
    await page.waitForTimeout(500);
    cancellation?.throwIfCancelled();
    const removedNotes = await page.evaluate(() => window.__removedNotes);
    cancellation?.throwIfCancelled();
    check('the annotation font arriving redraws a page with notes once', removedNotes === 1, String(removedNotes));
    await page.unroute('**/dejavufonts/ttf/DejaVuSans.ttf');
    cancellation?.throwIfCancelled();
    await openViewer();
    cancellation?.throwIfCancelled();
    let wordAttempts = 0;
    await page.route('**/DocumentTextBoxes?*', route => {
      wordAttempts += 1;
      return wordAttempts === 1 ? route.fulfill({ status: 503, body: '{}' }) : route.continue();
    });
    cancellation?.throwIfCancelled();
    await mark();
    cancellation?.throwIfCancelled();
    await page.waitForTimeout(200);
    cancellation?.throwIfCancelled();
    await mark();
    cancellation?.throwIfCancelled();
    check('text-layer HTTP failure retries on the next drag', wordAttempts >= 2);
    await page.unroute('**/DocumentTextBoxes?*');
    cancellation?.throwIfCancelled();

    // A stroke being drawn holds Save too: it is committed on release, and a save taken mid-stroke
    // would refuse it then and lose it.
    await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
    cancellation?.throwIfCancelled();
    await page.locator('.tool[data-tool="draw"]').click();
    cancellation?.throwIfCancelled();
    const strokeBox = await page.locator('svg.overlay').first().boundingBox();
    cancellation?.throwIfCancelled();
    const marksBeforeStroke = await markCount();
    cancellation?.throwIfCancelled();
    let saveDuringStroke = false;
    const watchStrokeSave = request => { if (request.url().includes('/SaveAnnotatedDocument')) { saveDuringStroke = true; } };
    page.on('request', watchStrokeSave);
    await page.mouse.move(strokeBox.x + 60, strokeBox.y + 420);
    cancellation?.throwIfCancelled();
    await page.mouse.down();
    cancellation?.throwIfCancelled();
    await page.mouse.move(strokeBox.x + 260, strokeBox.y + 440, { steps: 8 });
    cancellation?.throwIfCancelled();
    const saveHeldDuringStroke = await page.locator('#btnSave').isDisabled();
    cancellation?.throwIfCancelled();
    await page.locator('#btnSave').evaluate(button => button.click());
    cancellation?.throwIfCancelled();
    await page.mouse.up();
    cancellation?.throwIfCancelled();
    page.off('request', watchStrokeSave);
    check('Save is held while a stroke is drawn and the stroke still lands',
      saveHeldDuringStroke && !saveDuringStroke && await markCount() === marksBeforeStroke + 1
      && await page.locator('#btnSave').isEnabled(),
      JSON.stringify([saveHeldDuringStroke, saveDuringStroke, marksBeforeStroke, await markCount()]));
    cancellation?.throwIfCancelled();

    // A redraw mid-stroke (a resize here; the annotation font arriving does the same) must not
    // blank the stroke preview: the provider keeps seeing the line they are drawing.
    const strokeViewport = page.viewportSize();
    const previewCount = () => page.locator('svg.overlay').first().locator('.preview').count();
    const marksBeforeRedraw = await markCount();
    cancellation?.throwIfCancelled();
    await page.mouse.move(strokeBox.x + 60, strokeBox.y + 470);
    cancellation?.throwIfCancelled();
    await page.mouse.down();
    cancellation?.throwIfCancelled();
    await page.mouse.move(strokeBox.x + 220, strokeBox.y + 490, { steps: 6 });
    cancellation?.throwIfCancelled();
    const previewBeforeResize = await previewCount();
    cancellation?.throwIfCancelled();
    await page.setViewportSize({ width: strokeViewport.width, height: strokeViewport.height + 40 });
    cancellation?.throwIfCancelled();
    await page.waitForTimeout(150);
    cancellation?.throwIfCancelled();
    const previewAfterResize = await previewCount();
    cancellation?.throwIfCancelled();
    await page.mouse.up();
    cancellation?.throwIfCancelled();
    await page.setViewportSize(strokeViewport);
    cancellation?.throwIfCancelled();
    check('a redraw during a stroke keeps the stroke preview visible',
      previewBeforeResize === 1 && previewAfterResize === 1 && await markCount() === marksBeforeRedraw + 1,
      JSON.stringify([previewBeforeResize, previewAfterResize, marksBeforeRedraw, await markCount()]));
    cancellation?.throwIfCancelled();

    // A whitespace-only note is no note: the parser refuses blank text, and an invisible mark
    // would fail the whole save.
    await page.locator('.tool[data-tool="text"]').click();
    cancellation?.throwIfCancelled();
    const marksBeforeBlank = await markCount();
    cancellation?.throwIfCancelled();
    page.once('dialog', dialog => dialog.accept('   '));
    await page.mouse.click(strokeBox.x + 300, strokeBox.y + 200);
    cancellation?.throwIfCancelled();
    check('a whitespace-only note is not placed', await markCount() === marksBeforeBlank,
      JSON.stringify([marksBeforeBlank, await markCount()]));
    cancellation?.throwIfCancelled();

    // A stroke finishes as the mark it started as. A second pointer (here a programmatic click)
    // switching the tool and swatch mid-stroke must not change what the first pointer commits.
    await page.locator('.swatch[data-color="black"]').click();
    cancellation?.throwIfCancelled();
    await page.locator('.tool[data-tool="draw"]').click();
    cancellation?.throwIfCancelled();
    const inkStroke = () => page.locator('svg.overlay').first().locator('g.mark[data-kind="stroke"] polyline:not(.ink-hit)');
    const highlightRects = () => page.locator('svg.overlay').first().locator('rect.mark').count();
    const inksBeforeSwitch = await inkStroke().count();
    cancellation?.throwIfCancelled();
    const highlightsBeforeSwitch = await highlightRects();
    cancellation?.throwIfCancelled();
    // The viewer's black (COLORS.black in documentAnnotate.js); the earlier inks on this page
    // were drawn in whatever swatch was active, so none of them is a safe reference.
    const blackStroke = '#1A1A1A';
    await page.mouse.move(strokeBox.x + 60, strokeBox.y + 520);
    cancellation?.throwIfCancelled();
    await page.mouse.down();
    cancellation?.throwIfCancelled();
    await page.mouse.move(strokeBox.x + 160, strokeBox.y + 530, { steps: 4 });
    cancellation?.throwIfCancelled();
    await page.locator('.tool[data-tool="highlight"]').evaluate(button => button.click());
    cancellation?.throwIfCancelled();
    await page.locator('.swatch[data-color="yellow"]').evaluate(button => button.click());
    cancellation?.throwIfCancelled();
    await page.mouse.move(strokeBox.x + 260, strokeBox.y + 540, { steps: 4 });
    cancellation?.throwIfCancelled();
    await page.mouse.up();
    cancellation?.throwIfCancelled();
    const switchedStroke = await inkStroke().last().getAttribute('stroke');
    cancellation?.throwIfCancelled();
    check('a tool or colour change mid-stroke does not change what the stroke commits as',
      await inkStroke().count() === inksBeforeSwitch + 1 && await highlightRects() === highlightsBeforeSwitch
      && switchedStroke === blackStroke,
      JSON.stringify([inksBeforeSwitch, await inkStroke().count(), highlightsBeforeSwitch, await highlightRects(), blackStroke, switchedStroke]));
    cancellation?.throwIfCancelled();
    await page.locator('.swatch[data-color="yellow"]').click();
    cancellation?.throwIfCancelled();

    // A click on a mark is dispatched by the tool the press started in, not the toolbar at
    // release. A second pointer (a programmatic click) switching Select to Text mid-press must
    // still delete the pressed signature, not stamp a text note; and switching Text to Signature
    // mid-press on a note must still open the note, not stamp a signature over it.
    await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
    cancellation?.throwIfCancelled();
    const switchBox = await page.locator('svg.overlay').first().boundingBox();
    cancellation?.throwIfCancelled();
    await page.locator('.tool[data-tool="signature"]').click();
    cancellation?.throwIfCancelled();
    // Kept inside the viewport: a synthetic click below it lands nowhere.
    await page.mouse.click(switchBox.x + switchBox.width * 0.55, switchBox.y + 300);
    cancellation?.throwIfCancelled();
    const switchSig = await page.locator('svg.overlay').first().locator('g.mark[data-kind="placed"]').last().boundingBox();
    cancellation?.throwIfCancelled();
    const marksBeforeSwitchClick = await markCount();
    cancellation?.throwIfCancelled();
    let promptedOnSwitch = false;
    const noteSwitchPrompt = dialog => { promptedOnSwitch = true; dialog.dismiss(); };
    page.on('dialog', noteSwitchPrompt);
    await page.locator('.tool[data-tool="select"]').click();
    cancellation?.throwIfCancelled();
    await page.mouse.move(switchSig.x + 12, switchSig.y + 12);
    cancellation?.throwIfCancelled();
    await page.mouse.down();
    cancellation?.throwIfCancelled();
    await page.locator('.tool[data-tool="text"]').evaluate(button => button.click());
    cancellation?.throwIfCancelled();
    await page.mouse.up();
    cancellation?.throwIfCancelled();
    const marksAfterSwitchClick = await markCount();
    cancellation?.throwIfCancelled();
    check('a tool change mid-press still deletes the pressed signature in select',
      marksAfterSwitchClick === marksBeforeSwitchClick - 1 && !promptedOnSwitch,
      JSON.stringify([marksBeforeSwitchClick, marksAfterSwitchClick, promptedOnSwitch]));
    page.off('dialog', noteSwitchPrompt);
    await page.locator('.tool[data-tool="text"]').click();
    cancellation?.throwIfCancelled();
    page.once('dialog', dialog => dialog.accept('Synthetic note pressed under a tool switch'));
    await page.mouse.click(switchBox.x + switchBox.width * 0.55, switchBox.y + 340);
    cancellation?.throwIfCancelled();
    const switchNote = await page.locator('svg.overlay').first().locator('text.mark').last().boundingBox();
    cancellation?.throwIfCancelled();
    const marksBeforeNoteSwitch = await markCount();
    cancellation?.throwIfCancelled();
    promptedOnSwitch = false;
    page.on('dialog', noteSwitchPrompt);
    await page.mouse.move(switchNote.x + 4, switchNote.y + 4);
    cancellation?.throwIfCancelled();
    await page.mouse.down();
    cancellation?.throwIfCancelled();
    await page.locator('.tool[data-tool="signature"]').evaluate(button => button.click());
    cancellation?.throwIfCancelled();
    await page.mouse.up();
    cancellation?.throwIfCancelled();
    page.off('dialog', noteSwitchPrompt);
    check('a tool change mid-press still opens the pressed note in the text tool',
      promptedOnSwitch && await markCount() === marksBeforeNoteSwitch,
      JSON.stringify([promptedOnSwitch, marksBeforeNoteSwitch, await markCount()]));
    cancellation?.throwIfCancelled();

    // Hold an actual server save response; attempted edits must not re-enable duplicate submission.
    let releaseSave;
    const hold = new Promise(resolve => { releaseSave = resolve; cleanupReleases.push(resolve); });
    let responseReceived;
    const received = new Promise(resolve => { responseReceived = resolve; });
    await page.route('**/SaveAnnotatedDocument?*', async route => {
      const result = await fixture.fetch(route);
      responseReceived();
      await hold;
      await route.fulfill({ response: result });
    });
    cancellation?.throwIfCancelled();
    const beforeMarks = await page.locator('#markCount').textContent();
    cancellation?.throwIfCancelled();
    await page.locator('#btnSave').click();
    cancellation?.throwIfCancelled();
    await Promise.race([received, new Promise((_, reject) => setTimeout(() => reject(new Error('Save did not respond')), 30000))]);
    cancellation?.throwIfCancelled();
    await mark('draw');
    cancellation?.throwIfCancelled();
    check('edits cannot re-enable saving during an in-flight save',
      (await page.locator('#markCount').textContent()) === beforeMarks && await page.locator('#btnSave').isDisabled());
    cancellation?.throwIfCancelled();
    // Existing marks are frozen too: a move or a select-click delete made while the request is
    // out would be missing from the copy being filed, yet marked saved when the response lands.
    await page.locator('.tool[data-tool="select"]').click();
    cancellation?.throwIfCancelled();
    const inFlightBox = await page.locator('svg.overlay').first().locator('rect.mark').first().boundingBox();
    cancellation?.throwIfCancelled();
    await page.mouse.move(inFlightBox.x + 6, inFlightBox.y + 6);
    cancellation?.throwIfCancelled();
    await page.mouse.down();
    cancellation?.throwIfCancelled();
    await page.mouse.move(inFlightBox.x + 6, inFlightBox.y + 106, { steps: 8 });
    cancellation?.throwIfCancelled();
    await page.mouse.up();
    cancellation?.throwIfCancelled();
    await page.mouse.click(inFlightBox.x + 6, inFlightBox.y + 6);
    cancellation?.throwIfCancelled();
    const inFlightAfter = await page.locator('svg.overlay').first().locator('rect.mark').first().boundingBox();
    cancellation?.throwIfCancelled();
    check('existing marks cannot be moved or deleted during an in-flight save',
      (await page.locator('#markCount').textContent()) === beforeMarks
      && Math.abs(inFlightAfter.y - inFlightBox.y) < 1 && await page.locator('#btnSave').isDisabled(),
      JSON.stringify([await page.locator('#markCount').textContent(), beforeMarks, inFlightBox, inFlightAfter]));
    cancellation?.throwIfCancelled();
    const inFlightCursor = await page.evaluate(([x, y]) => getComputedStyle(document.elementFromPoint(x, y)).cursor,
      [inFlightAfter.x + 6, inFlightAfter.y + 6]);
    cancellation?.throwIfCancelled();
    check('marks frozen by an in-flight save do not show the move cursor', inFlightCursor !== 'move', inFlightCursor);
    releaseSave();
    await waitForSave();
    cancellation?.throwIfCancelled();
    await page.unroute('**/SaveAnnotatedDocument?*');
    cancellation?.throwIfCancelled();

    await openViewer();
    cancellation?.throwIfCancelled();
    await mark();
    cancellation?.throwIfCancelled();
    await page.route('**/SaveAnnotatedDocument?*', route => fixture.abort(route,'failed'));
    cancellation?.throwIfCancelled();
    await page.locator('#btnSave').click();
    cancellation?.throwIfCancelled();
    await waitForSave();
    cancellation?.throwIfCancelled();
    check('lost save response gives a check-chart warning and prevents blind retries',
      (await page.locator('#status').textContent()).includes('could not be confirmed')
      && await page.locator('#btnSave').isDisabled());
    cancellation?.throwIfCancelled();
    await page.unroute('**/SaveAnnotatedDocument?*');
    cancellation?.throwIfCancelled();

    // A failed page image is visible and cannot be annotated as an empty sheet.
    let busyImageAttempts = 0;
    const busyImagePattern = '**/documentManager/ManageDocument?**';
    const busyImage = route => {
      const url = new URL(route.request().url());
      if (url.searchParams.get('method') !== 'showPage' || url.searchParams.get('page') !== '1') return route.continue();
      busyImageAttempts++;
      return busyImageAttempts <= 5
        ? route.fulfill({status: 503, headers: {'Retry-After': '1'}, body: 'Synthetic document capacity limit'})
        : route.continue();
    };
    await page.route(busyImagePattern, busyImage);
    cancellation?.throwIfCancelled();
    const acceptBusyNavigation = dialog => dialog.accept();
    page.on('dialog', acceptBusyNavigation);
    try { await navigateAnnotationViewer(page, baseUrl, docId, 'domcontentloaded');
    cancellation?.throwIfCancelled(); }
    finally { page.off('dialog', acceptBusyNavigation); }
    await page.waitForFunction(() => /Waiting for document capacity/.test(document.getElementById('status').textContent));
    cancellation?.throwIfCancelled();
    check('document capacity waiting is visible and does not mark the page as broken',
      await page.locator('#status').getAttribute('class') === 'status busy'
        && await page.locator('.page.load-failed').count() === 0);
    cancellation?.throwIfCancelled();
    await page.waitForFunction(() => document.querySelector('.page img').naturalWidth > 0);
    cancellation?.throwIfCancelled();
    check('document image continues automatically after more than three capacity refusals', busyImageAttempts === 6);
    await page.unroute(busyImagePattern, busyImage);
    cancellation?.throwIfCancelled();
    await openViewer();
    cancellation?.throwIfCancelled();
    check('document capacity recovery leaves the viewer usable',
      await page.locator('.page img').first().evaluate(image => image.naturalWidth > 0));
    cancellation?.throwIfCancelled();

    await mark();
    cancellation?.throwIfCancelled();
    const marksBeforeCapacityWait = await page.locator('#markCount').textContent();
    cancellation?.throwIfCancelled();
    let capacitySaveAttempts = 0;
    const saveCapacity = route => {
      capacitySaveAttempts++;
      return capacitySaveAttempts <= 5 ? route.fulfill({status: 503, contentType: 'application/json',
        headers: {'Retry-After': '1'}, body: JSON.stringify({success: false, retryable: true, error: 'Waiting for capacity'})})
        : route.continue();
    };
    await page.route('**/SaveAnnotatedDocument?*', saveCapacity);
    cancellation?.throwIfCancelled();
    await page.locator('#btnSave').click();
    cancellation?.throwIfCancelled();
    await page.waitForFunction(() => /Waiting for document capacity/.test(document.getElementById('status').textContent));
    cancellation?.throwIfCancelled();
    check('an unaccepted save preserves marks and stays locked while waiting for capacity',
      await page.locator('#markCount').textContent() === marksBeforeCapacityWait
        && await page.locator('#btnSave').isDisabled());
    cancellation?.throwIfCancelled();
    await page.waitForFunction(() => document.getElementById('status').className === 'status ok');
    cancellation?.throwIfCancelled();
    check('an explicitly unaccepted save continues automatically until it is filed once', capacitySaveAttempts === 6);
    await page.unroute('**/SaveAnnotatedDocument?*', saveCapacity);
    cancellation?.throwIfCancelled();

    await page.route('**/ManageDocument?method=showPage&**', route => route.fulfill({ status: 500, body: '' }));
    cancellation?.throwIfCancelled();
    page.once('dialog', dialog => dialog.accept());
    await navigateAnnotationViewer(page, baseUrl, docId);
    cancellation?.throwIfCancelled();
    await page.waitForFunction(() => document.querySelector('.page.load-failed'));
    cancellation?.throwIfCancelled();
    check('failed page images report a visible error and do not enable saving',
      (await page.locator('#status').textContent()).includes('could not be loaded')
      && await page.locator('#btnSave').isDisabled());
    cancellation?.throwIfCancelled();
    await page.unroute('**/ManageDocument?method=showPage&**');
    cancellation?.throwIfCancelled();
    // No unsaved marks were present, so no before-unload dialog should have fired.
    page.removeAllListeners('dialog');

    await openViewer();
    cancellation?.throwIfCancelled();
    await mark();
    cancellation?.throwIfCancelled();
    await page.locator('#btnSaveFax').click();
    cancellation?.throwIfCancelled();
    await waitForAnnotationFaxCover(page);
    cancellation?.throwIfCancelled();
    await page.waitForURL('**/fax/faxAction*', { timeout: 120000 });
    cancellation?.throwIfCancelled();
    check('Save and fax reaches the protected POST cover page', await page.locator('#btnSend').count() === 1);
    cancellation?.throwIfCancelled();
    const preview = await page.locator('input[name="faxFilePath"]').inputValue();
    cancellation?.throwIfCancelled();
    check('document fax uses a staged copy', preview.includes('carlos-temp'));
    const countResponse = await authenticatedProbe({config:readConfig(),context},`${baseUrl}/fax/faxAction`, {
      params: { method: 'getPageCount', faxFilePath: preview }
    });
    cancellation?.throwIfCancelled();
    check('session-owned document fax preview can be read', countResponse.status() === 200);
    const directory = await authenticatedProbe({config:readConfig(),context},`${baseUrl}/fax/SearchFaxRecipient?term=Test`);
    cancellation?.throwIfCancelled();
    check('both recipient directory queries execute on MariaDB', directory.status() === 200 && Array.isArray(await directory.json()));
    cancellation?.throwIfCancelled();
    await page.locator('#btnCancel').click();
    cancellation?.throwIfCancelled();
    await page.waitForURL('**/documentManager/ManageDocument?*', { timeout: 30000 });
    cancellation?.throwIfCancelled();
    check('cancelling returns to the saved document without a missing result error',
      !(await page.title()).includes('Error'));
    cancellation?.throwIfCancelled();
    const cancelledPreview = await authenticatedProbe({config:readConfig(),context},`${baseUrl}/fax/faxAction`, {
      params: { method: 'getPageCount', faxFilePath: preview }
    });
    cancellation?.throwIfCancelled();
    check('cancelled document preview cannot be read again', cancelledPreview.status() === 403);
    check('annotation flow has no CSP or uncaught script errors', cspViolations.length === 0, cspViolations.join(' | '));
    await openViewer();
    cancellation?.throwIfCancelled();
    const blockedInline = await page.evaluate(async () => {
      window.__annotationUnexpectedInline = false;
      const script = document.createElement('script');
      script.textContent = 'window.__annotationUnexpectedInline = true';
      document.body.appendChild(script);
      await new Promise(resolve => setTimeout(resolve, 100));
      return !window.__annotationUnexpectedInline;
    });
    cancellation?.throwIfCancelled();
    check('the front door enforces the application CSP against an unnonced inline script', blockedInline);

    if (process.env.ANNOTATION_SECOND_USER_FIXTURE) {
      const sessions = await checkAnnotationSessions(browser, readConfig(), process.env.ANNOTATION_SECOND_USER_FIXTURE);
    cancellation?.throwIfCancelled();
      check('independent providers share bounded rendering capacity without crossing patient permissions or annotations', true);
      notes.push(`INFO  annotation concurrency ${JSON.stringify(sessions)}`);
    }



  } finally {
    for(const release of cleanupReleases)release();
    try {
      // Block future retries, then finish accepted server calls before closing their session.
      for(const page of context?.pages()||[])await page.route('**/SaveAnnotatedDocument?*',route=>fixture.abort(route,'blockedbyclient'));
      await fixture.drain();
    } finally {
      try {if(context)await context.close();} finally {await browser.close();}
      fixture.confirmClosed();
    }
  }
}

let ownedFixture;
h.runCheck({name:'annotate-document',async run(context) {
  legacyHarness.armInterruption(context);
  ownedFixture=createOwnedAnnotationFixture(readConfig());
  ownedFixture.create();
  try {await main({...context,fixture:ownedFixture});}
  finally {notes.forEach(line=>console.log(line));findings.forEach(line=>console.log(line));}
  h.assert(findings.length===0,`${findings.length} annotation check(s) failed`);
  console.log('All annotation viewer checks passed');
},async cleanup(){if(ownedFixture)await ownedFixture.cleanup();}});
