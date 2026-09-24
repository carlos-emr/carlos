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
 *      reported by number, and is independently renderable. Each run therefore
 *      ADDS a document to the chart, which is why this belongs only on a
 *      disposable deployment.
 *
 * What it does NOT prove: that the SOURCE file is left byte-identical. That is the
 * design's central invariant, but asserting it needs filesystem access the browser
 * does not have. It is pinned by AnnotatedDocumentComposerUnitTest instead.
 *
 * Usage:
 *   BASE_URL=https://host/carlos TEST_USER=... TEST_PASSWORD=... TEST_PIN=... \
 *     node scripts/e2e/fax/annotate-document-playwright-checks.js
 *
 * Requires a disposable deployment with demo data. Never point it at real PHI.
 */
'use strict';

const { chromium } = require('playwright');

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
  const parsed = new URL(rawBaseUrl);
  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw new Error(`BASE_URL must use http or https, got ${parsed.protocol}`);
  }
  if (parsed.username || parsed.password) {
    throw new Error('BASE_URL must not embed credentials');
  }
  const host = parsed.hostname.toLowerCase();
  const localHosts = new Set(['localhost', '127.0.0.1', '[::1]', '::1', '0.0.0.0', 'host.docker.internal', 'carlos']);
  const privateIpv4 = /^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[0-1])\.)/.test(host);
  const local = localHosts.has(host) || privateIpv4;
  if (!local && process.env.ALLOW_NON_LOCAL_BASE_URL !== 'true') {
    throw new Error(`Refusing non-local BASE_URL host ${host}; set ALLOW_NON_LOCAL_BASE_URL=true for an intentional test target`);
  }
  return { href: parsed.href.replace(/\/+$/, ''), local, loopback: ['localhost', '127.0.0.1', '[::1]'].includes(host) };
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

async function main() {
  const browser = await chromium.launch({
    headless: true,
    ...(chromePath ? { executablePath: chromePath } : {}),
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  });
  // Self-signed certificates are normal on a local dev deployment and never acceptable for a
  // remote one: this script sends a real password, and skipping verification there would put it
  // on an unauthenticated TLS channel.
  const context = await browser.newContext({ ignoreHTTPSErrors: validatedBaseUrl.loopback });

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

  try {
    const page = await context.newPage();
    page.on('console', (message) => {
      if (message.type() === 'error' && /Content Security Policy/i.test(message.text())) {
        cspViolations.push(message.text());
      }
    });
    page.on('pageerror', (error) => cspViolations.push(`pageerror: ${error.message}`));
    await login(page);

    const docId = process.env.DOC_ID || '1';
    if (!/^[1-9][0-9]*$/.test(docId)) { throw new Error('DOC_ID must be a positive integer'); }

    // ---- word boxes are optional ----
    const boxes = await page.evaluate(async (args) => {
      const response = await fetch(
        `${args.base}/documentManager/DocumentTextBoxes?docId=${args.docId}&page=1`,
        { credentials: 'same-origin', headers: { 'X-Requested-With': 'XMLHttpRequest' } },
      );
      return { status: response.status, body: await response.text() };
    }, { base: baseUrl, docId });

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
      return response.status;
    }, { base: baseUrl, docId });
    check('save endpoint refuses GET with 405', getSave === 405, `status ${getSave}`);

    // ---- the viewer renders ----
    // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection
    // Same validated baseUrl; docId is a positive integer parsed from the environment.
    const viewerResponse = await page.goto(
      `${baseUrl}/documentManager/AnnotateDocument?docId=${docId}`,
      { waitUntil: 'domcontentloaded' });
    await page.locator('svg.overlay').first().waitFor();
    await page.waitForFunction(() => document.querySelector('.page img')?.naturalWidth > 0);

    const csp = viewerResponse.headers()['content-security-policy'] || '';
    check('viewer sends a Content-Security-Policy', csp.length > 0, '(no header)');
    check('the policy admits the page\'s own inline scripts by nonce',
      /script-src[^;]*'nonce-/.test(csp), csp || '(no header)');

    // These two prove the policy is actually satisfied rather than merely well-formed.
    // Without the nonce both blocks are dropped, the viewer renders nothing, and the
    // save path fails with an empty CSRF token.
    const configLoaded = await page.evaluate(() => typeof window.CARLOS_ANNOTATE === 'object');
    check('the inline configuration block executed', configLoaded,
      'window.CARLOS_ANNOTATE is not an object — script-src is blocking it');
    const csrfBootstrapped = await page.evaluate(() => 'csrfTokenReady' in window);
    check('the CSRF bootstrap executed', csrfBootstrapped,
      'window.csrfTokenReady is absent — script-src is blocking csrf-token.jspf');

    const swatchColors = await page.locator('.swatch').evaluateAll(elements =>
      elements.map(element => getComputedStyle(element).backgroundColor));
    check('all six annotation colour choices are visibly distinct',
      new Set(swatchColors).size === 6 && !swatchColors.includes('rgb(255, 255, 255)'), swatchColors.join(', '));

    const toolCount = await page.locator('.tool').count();
    check('viewer renders its toolbar', toolCount >= 6, `${toolCount} tools`);

    const pageCount = await page.locator('.page').count();
    check('viewer renders at least one page container', pageCount >= 1, `${pageCount} pages`);

    const overlayCount = await page.locator('svg.overlay').count();
    check('viewer renders an SVG overlay per page', overlayCount === pageCount,
      `${overlayCount} overlays for ${pageCount} pages`);

    check('viewer requested server-rendered page images', imageRequests.length >= 1,
      `${imageRequests.length} showPage requests`);

    check('viewer loaded NO client-side PDF library', forbiddenRequests.length === 0,
      forbiddenRequests.slice(0, 3).join(', '));

    const csrf = await page.locator('input[name="CSRF-TOKEN"]').count();
    check('viewer bootstraps a CSRF token', csrf >= 1, `${csrf} token inputs`);

    check('the page reported no CSP violation or script error',
      cspViolations.length === 0, cspViolations.slice(0, 3).join(' | '));

    // The status region is an <output>, not a <span role="status">. The element type matters
    // because the viewer's setStatus() writes to it by id and swaps its className: if the swap
    // ever broke the handle or the CSS, saves would appear to do nothing. Assert what the
    // BROWSER computes, not what the markup claims.
    const statusTag = await page.locator('#status').evaluate(el => el.tagName.toLowerCase());
    check('the status region is an <output>', statusTag === 'output', `<${statusTag}>`);
    // includeHidden: while empty the region is display:none via .status:empty, and Playwright's
    // role engine skips hidden elements. The role is asserted again after a save, when it is
    // visible and actually announceable.
    const statusRoleCount = await page.getByRole('status', { includeHidden: true }).count();
    check('the browser exposes the status region with the ARIA status role',
      statusRoleCount >= 1, `${statusRoleCount} elements with role=status`);
    const statusHiddenWhenEmpty = await page.locator('#status')
      .evaluate(el => getComputedStyle(el).display);
    check('the empty status region is hidden by .status:empty',
      statusHiddenWhenEmpty === 'none', `display: ${statusHiddenWhenEmpty}`);

    // ---- highlighting works regardless of the text layer ----
    await page.locator('.tool[data-tool="highlight"]').click();
    const overlay = page.locator('svg.overlay').first();
    const box = await overlay.boundingBox();
    if (box && box.width > 40 && box.height > 40) {
      await page.mouse.move(box.x + 30, box.y + 40);
      await page.mouse.down();
      await page.mouse.move(box.x + box.width * 0.6, box.y + 60, { steps: 8 });
      await page.mouse.up();
      await page.waitForTimeout(400);
      const marks = await overlay.locator('.mark').count();
      check('a highlight can be drawn and appears on the overlay', marks >= 1, `${marks} marks`);
      const saveEnabled = await page.locator('#btnSave').isEnabled();
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

      check('save returns 200', saved.status === 200, `status ${saved.status}`);
      check('save reports success', saved.body && saved.body.success === true,
        JSON.stringify(saved.body || {}).slice(0, 160));
      const newDocNo = saved.body && saved.body.documentNo;
      check('save files the result as a NEW document',
        Number.isInteger(newDocNo) && String(newDocNo) !== String(docId),
        `documentNo=${newDocNo}, source docId=${docId}`);
      if (Number.isInteger(newDocNo)) {
        notes.push(`INFO  annotated copy filed as document ${newDocNo}`);
        // The copy must be reachable in its own right, which also confirms it was
        // written to the document store and not merely recorded.
        const openable = await page.evaluate(async (args) => {
          const r = await fetch(
            `${args.base}/documentManager/ManageDocument?method=showPage&doc_no=${args.docNo}&page=1`,
            { credentials: 'same-origin' },
          );
          return { status: r.status, type: r.headers.get('content-type') || '' };
        }, { base: baseUrl, docNo: newDocNo });
        check('the annotated copy renders as its own document',
          openable.status === 200 && /image/i.test(openable.type),
          `status ${openable.status}, type ${openable.type}`);
      }

      // Everything above posts the model with fetch, which never touches setStatus(). Click the
      // real Save button so the viewer's own status path runs end to end: the handle resolves,
      // the className swap lands, and the region becomes visible. This files one further copy,
      // which is why this script is for disposable deployments only.
      await page.locator('#btnSave').click();
      await page.waitForFunction(
        () => /(?:ok|error)/.test(document.getElementById('status').className),
        null, { timeout: 20000 },
      ).catch(() => {});
      const statusAfterSave = await page.locator('#status').evaluate(el => ({
        text: el.textContent.trim(),
        cls: el.className,
        display: getComputedStyle(el).display,
      }));
      check('clicking Save confirms successful filing', statusAfterSave.cls === 'status ok', JSON.stringify(statusAfterSave));
      check('clicking Save writes into the status region',
        statusAfterSave.text.length > 0, JSON.stringify(statusAfterSave));
      check('setStatus applies its state class to the status region',
        /^status( \w+)?$/.test(statusAfterSave.cls), `className="${statusAfterSave.cls}"`);
      check('the populated status region is visible',
        statusAfterSave.display !== 'none', `display: ${statusAfterSave.display}`);
      // Now that it carries text and is visible, it must be announceable as a live status.
      const visibleStatusRole = await page.getByRole('status').count();
      check('the visible status region is announceable as role=status',
        visibleStatusRole >= 1, `${visibleStatusRole} visible elements with role=status`);
    } else {
      findings.push('FAIL  overlay had no usable geometry to draw on');
    }

    // Run against the deployed viewer, injecting only the named failure at its network boundary.
    // waitUntil 'domcontentloaded' opens the viewer while a held subresource (the annotation
    // font) is still outstanding; the default waits for the load event.
    async function openViewer(waitUntil = 'load') {
      const dismiss = dialog => dialog.accept();
      page.on('dialog', dismiss);
      await page.goto(`${baseUrl}/documentManager/AnnotateDocument?docId=${docId}`, { waitUntil }); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- validated baseUrl; docId is a positive integer
      page.off('dialog', dismiss);
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
    check('a stale source fingerprint refuses filing with an explicit conflict',
      stale.status === 409 && stale.body.error.includes('source document changed'));

    if (pageCount > 1) {
      const lastPage = page.locator('.page').last();
      await lastPage.evaluate(element => element.scrollIntoView({ block: 'start' }));
      await page.waitForFunction(() => [...document.querySelectorAll('.page img')].at(-1).naturalWidth > 0);
      await lastPage.evaluate(element => element.scrollIntoView({ block: 'start' }));
      await page.locator('.tool[data-tool="date"]').click();
      await lastPage.locator('svg.overlay').click({ position: { x: 80, y: 220 } });
      await page.locator('#btnSave').click();
      await waitForSave();
      check('a mark on the last page saves successfully in a multipage document',
        await page.locator('#status').getAttribute('class') === 'status ok');
      await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
    }

    // A successful save must not suppress warnings for subsequent unsaved edits.
    await mark('draw');
    check('editing after a save enables saving again', await page.locator('#btnSave').isEnabled());
    await page.locator('.tool[data-tool="date"]').click();
    await page.locator('svg.overlay').first().click({ position: { x: 230, y: 140 } });
    await page.locator('.tool[data-tool="text"]').click();
    page.once('dialog', dialog => dialog.accept('Synthetic note é 3618'));
    await page.locator('svg.overlay').first().click({ position: { x: 230, y: 180 } });
    if (process.env.EXPECT_SIGNATURE_STAMP === 'true') {
      await page.locator('.tool[data-tool="signature"]').click();
      await page.locator('svg.overlay').first().click({ position: { x: 350, y: 250 } });
    }
    check('preview loads the same font as the PDF composer',
      await page.evaluate(() => document.fonts.load('11px CarlosAnnotation').then(fonts => fonts.length > 0)));
    await page.locator('#btnSave').click();
    await waitForSave();
    check('ink, date and Unicode text save through the real toolbar',
      await page.locator('#status').getAttribute('class') === 'status ok');
    if (process.env.EXPECT_SIGNATURE_STAMP === 'true') {
      check('the provider signature stamp saves through the real toolbar',
        await page.locator('#status').getAttribute('class') === 'status ok');
    }
    if (process.env.ARTIFACT_DIR) {
      require('fs').mkdirSync(process.env.ARTIFACT_DIR, { recursive: true });
      await page.screenshot({ path: require('path').join(process.env.ARTIFACT_DIR, 'annotation-tools.png'), fullPage: true });
    }

    await openViewer();
    await page.locator('.tool[data-tool="text"]').click();
    page.once('dialog', dialog => dialog.accept('Synthetic unsupported 🧬'));
    await page.locator('svg.overlay').first().click({ position: { x: 180, y: 140 } });
    const unsupportedResponse = page.waitForResponse(r => r.url().includes('/SaveAnnotatedDocument'));
    await page.locator('#btnSave').click();
    check('unsupported glyph is rejected instead of silently erased', (await unsupportedResponse).status() === 409);
    await waitForSave();
    check('unsupported glyph rejection stays visible and editable',
      (await page.locator('#status').textContent()).includes('cannot display') && await page.locator('#btnSave').isEnabled());

    // Placed marks can be dragged and re-edited without leaving the current tool, and the
    // moved/edited model must still pass the server's bounds and text validation.
    await openViewer();
    await page.locator('.tool[data-tool="text"]').click();
    page.once('dialog', dialog => dialog.accept('Synthetic movable note'));
    await page.locator('svg.overlay').first().click({ position: { x: 120, y: 140 } });
    const noteBefore = await page.locator('text.mark').first().boundingBox();
    await page.mouse.move(noteBefore.x + 4, noteBefore.y + 4);
    await page.mouse.down();
    await page.mouse.move(noteBefore.x + 84, noteBefore.y + 124, { steps: 8 });
    await page.mouse.up();
    const noteAfter = await page.locator('text.mark').first().boundingBox();
    check('a text note can be dragged while the text tool is active',
      Math.abs(noteAfter.y - noteBefore.y - 120) < 3 && await page.locator('#markCount').textContent() === '1',
      JSON.stringify([noteBefore, noteAfter]));
    page.once('dialog', dialog => dialog.accept('Synthetic edited note'));
    await page.mouse.click(noteAfter.x + 4, noteAfter.y + 4);
    check('clicking a text note edits it in place',
      await page.locator('text.mark').first().textContent() === 'Synthetic edited note'
      && await page.locator('#markCount').textContent() === '1');
    await page.locator('#btnSave').click();
    await waitForSave();
    check('a moved and edited note saves successfully',
      await page.locator('#status').getAttribute('class') === 'status ok');

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
    check('every point of a note text box grabs the note', noteMisses.length === 0, JSON.stringify(noteMisses));

    // Changing an already-saved mark is an unsaved edit, exactly like adding one.
    const savedNote = await page.locator('text.mark').first().boundingBox();
    await page.mouse.move(savedNote.x + 4, savedNote.y + 4);
    await page.mouse.down();
    await page.mouse.move(savedNote.x + 44, savedNote.y + 44, { steps: 6 });
    await page.mouse.up();
    check('moving a saved note enables saving again', await page.locator('#btnSave').isEnabled());
    let warnedOnLeave = false;
    const noteLeaveWarning = dialog => { if (dialog.type() === 'beforeunload') { warnedOnLeave = true; } };
    page.on('dialog', noteLeaveWarning);

    // The grab rules differ by mark kind, so each branch is pinned separately: highlights and
    // ink are drawn across in the drawing tools and move only in select; placed marks move from
    // any tool; a cancelled or non-primary press changes nothing; a move stops at the page edge.
    await openViewer();
    page.off('dialog', noteLeaveWarning);
    check('leaving after moving a saved note warns about unsaved changes', warnedOnLeave);
    await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
    const ov = await page.locator('svg.overlay').first().boundingBox();
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
    await drag(60, 300, 260, 330);
    await drag(100, 315, 300, 345);
    check('a highlight drag that starts on a highlight draws a new one', await markCount() === 2, String(await markCount()));

    await page.locator('.tool[data-tool="select"]').click();
    const hlBefore = await boxOf('rect.mark');
    await drag(70, 305, 70, 405);
    const hlAfter = await boxOf('rect.mark');
    check('select moves a highlight', Math.abs(hlAfter.y - hlBefore.y - 100) < 3 && await markCount() === 2,
      JSON.stringify([hlBefore, hlAfter]));

    await page.mouse.move(ov.x + 70, ov.y + 405);
    await page.mouse.down();
    await page.mouse.move(ov.x + 70, ov.y + 505, { steps: 8 });
    await page.locator('svg.overlay').first().evaluate(svg => svg.dispatchEvent(
      new PointerEvent('pointercancel', { bubbles: true, pointerId: 1, isPrimary: true })));
    await page.mouse.up();
    const hlCancelled = await boxOf('rect.mark');
    check('a cancelled drag leaves the mark where it was', Math.abs(hlCancelled.y - hlAfter.y) < 1.5,
      JSON.stringify([hlAfter, hlCancelled]));

    await page.mouse.click(ov.x + 70, ov.y + 405, { button: 'right' });
    check('a right-click on a mark in select does not delete it', await markCount() === 2, String(await markCount()));

    // A second touch lifting mid-drag must not commit (or cut short) the first pointer's move.
    const hlTouchBefore = await boxOf('rect.mark');
    await page.mouse.move(ov.x + 70, ov.y + 405);
    await page.mouse.down();
    await page.mouse.move(ov.x + 70, ov.y + 445, { steps: 5 });
    await page.locator('svg.overlay').first().evaluate(svg => svg.dispatchEvent(new PointerEvent('pointerup',
      { bubbles: true, pointerId: 99, isPrimary: false, pointerType: 'touch' })));
    await page.mouse.move(ov.x + 70, ov.y + 505, { steps: 5 });
    await page.mouse.up();
    const hlTouchAfter = await boxOf('rect.mark');
    check('another pointer lifting does not end a move in progress',
      Math.abs(hlTouchAfter.y - hlTouchBefore.y - 100) < 3, JSON.stringify([hlTouchBefore, hlTouchAfter]));

    // A redraw in the middle of a drag (here a resize; the annotation font arriving does the
    // same) must not drop the preview: the mark stays where the pointer has taken it.
    const viewport = page.viewportSize();
    const hlRedrawBefore = await boxOf('rect.mark');
    await page.mouse.move(hlRedrawBefore.x + 6, hlRedrawBefore.y + 6);
    await page.mouse.down();
    await page.mouse.move(hlRedrawBefore.x + 6, hlRedrawBefore.y + 66, { steps: 6 });
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
    await page.waitForTimeout(150);
    const hlDuringRedraw = await settledBox('rect.mark');
    await page.mouse.up();
    await page.setViewportSize(viewport);
    const hlRedrawAfter = await settledBox('rect.mark');
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
    await page.mouse.move(hlSaveBefore.x + 6, hlSaveBefore.y + 6);
    await page.mouse.down();
    await page.mouse.move(hlSaveBefore.x + 6, hlSaveBefore.y - 34, { steps: 6 });
    const saveHeldDuringDrag = await page.locator('#btnSave').isDisabled();
    await page.locator('#btnSave').evaluate(button => button.click());
    await page.mouse.up();
    page.off('request', watchDragSave);
    const hlSaveAfter = await settledBox('rect.mark');
    check('Save is held during a drag and the drag still lands',
      saveHeldDuringDrag && !saveDuringDrag && await page.locator('#btnSave').isEnabled()
      && Math.abs(hlSaveAfter.y - hlSaveBefore.y + 40) < 3,
      JSON.stringify([saveHeldDuringDrag, saveDuringDrag, hlSaveBefore, hlSaveAfter]));

    // The page moving under the pointer mid-drag (a scroll here; a resize that re-centres the
    // page is the same) must not leave the mark behind: it follows the pointer on the page.
    const scrollOverlayBefore = await page.locator('svg.overlay').first().boundingBox();
    const hlScrollBefore = await settledBox('rect.mark');
    await page.mouse.move(hlScrollBefore.x + 6, hlScrollBefore.y + 6);
    await page.mouse.down();
    await page.mouse.move(hlScrollBefore.x + 6, hlScrollBefore.y + 46, { steps: 6 });
    await page.evaluate(() => window.scrollBy({ top: 30, behavior: 'instant' }));
    await page.mouse.move(hlScrollBefore.x + 6, hlScrollBefore.y + 47, { steps: 2 });
    await page.mouse.up();
    const scrollOverlayAfter = await page.locator('svg.overlay').first().boundingBox();
    const hlScrollAfter = await settledBox('rect.mark');
    // The press was 6 px into the mark and the last move 47 px, so the pointer travelled 41 px
    // on screen, plus however far the page scrolled up under it.
    const pointerOnPage = 41 + (scrollOverlayBefore.y - scrollOverlayAfter.y);
    const markOnPage = (hlScrollAfter.y - scrollOverlayAfter.y) - (hlScrollBefore.y - scrollOverlayBefore.y);
    await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
    check('a drag keeps the mark under the pointer when the page scrolls mid-drag',
      Math.abs(markOnPage - pointerOnPage) < 3, JSON.stringify({ pointerOnPage, markOnPage }));

    await page.locator('.swatch[data-color="black"]').click();
    await page.locator('.tool[data-tool="draw"]').click();
    await drag(400, 250, 600, 250);
    await page.locator('.tool[data-tool="select"]').click();
    const inkBefore = await boxOf('g.mark[data-kind="stroke"]');
    await drag(500, 250, 500, 350);
    const inkAfter = await boxOf('g.mark[data-kind="stroke"]');
    check('select moves an ink stroke by its hit area', Math.abs(inkAfter.y - inkBefore.y - 100) < 3 && await markCount() === 3,
      JSON.stringify([inkBefore, inkAfter]));

    await page.locator('.tool[data-tool="signature"]').click();
    await page.mouse.click(ov.x + 400, ov.y + 150);
    await page.locator('.tool[data-tool="date"]').click();
    const sigBefore = await boxOf('g.mark[data-kind="placed"]');
    await drag(sigBefore.x - ov.x + 10, sigBefore.y - ov.y + 10, sigBefore.x - ov.x + 10, sigBefore.y - ov.y + 160);
    const sigAfter = await boxOf('g.mark[data-kind="placed"]');
    check('a signature can be dragged while the date tool is active',
      Math.abs(sigAfter.y - sigBefore.y - 150) < 3 && await markCount() === 4, JSON.stringify([sigBefore, sigAfter]));

    await page.locator('.tool[data-tool="text"]').click();
    // Longer than a note's default box, so the edge clamp must use the text's drawn width or
    // the save below is refused with "The annotation text extends beyond the page".
    page.once('dialog', dialog => dialog.accept('Synthetic edge note, deliberately longer than the default note box'));
    await page.mouse.click(ov.x + 200, ov.y + 200);
    const edgeBefore = await boxOf('text.mark');
    await drag(edgeBefore.x - ov.x + 4, edgeBefore.y - ov.y + 4, 5000, 5000);
    const edgeAfter = await boxOf('text.mark');
    check('a move stops at the page edge',
      edgeAfter.x > edgeBefore.x && edgeAfter.x + edgeAfter.width <= ov.x + ov.width + 1
      && edgeAfter.y + edgeAfter.height <= ov.y + ov.height + 1, JSON.stringify([edgeAfter, ov]));

    page.once('dialog', dialog => dialog.accept('Synthetic note to clear'));
    await page.mouse.click(ov.x + 200, ov.y + 250);
    const clearBox = await page.locator('svg.overlay').first().locator('text.mark').nth(1).boundingBox();
    page.once('dialog', dialog => dialog.accept(''));
    await page.mouse.click(clearBox.x + 4, clearBox.y + 4);
    check('clearing a note removes it', await markCount() === 5
      && await page.locator('svg.overlay').first().locator('text.mark').count() === 1, String(await markCount()));

    // Signature save needs a provider stamp this deployment may not have, so the mark is removed
    // (which also pins that a select click still deletes a signature) before the save below.
    await page.locator('.tool[data-tool="select"]').click();
    await page.mouse.click(sigAfter.x + 10, sigAfter.y + 10);
    check('a select click still deletes a signature', await markCount() === 4, String(await markCount()));
    await page.locator('#btnSave').click();
    await waitForSave();
    check('marks moved to the page edge pass server validation',
      await page.locator('#status').getAttribute('class') === 'status ok',
      await page.locator('#status').textContent());

    // Drawing tools never grab; an ink stroke's invisible halo does not hide the note beneath it;
    // a date-tool click inside a signature still stamps a date; a short note reaches the right
    // margin; an edit that lengthens a note keeps it on the page.
    await openViewer();
    await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
    const pageBox = await page.locator('svg.overlay').first().boundingBox();
    const overlayMark = selector => page.locator('svg.overlay').first().locator(selector).first();
    async function dragOn(x0, y0, x1, y1) {
      await page.mouse.move(pageBox.x + x0, pageBox.y + y0);
      await page.mouse.down();
      await page.mouse.move(pageBox.x + x1, pageBox.y + y1, { steps: 8 });
      await page.mouse.up();
    }

    await page.locator('.tool[data-tool="text"]').click();
    page.once('dialog', dialog => dialog.accept('Synthetic crossed note'));
    await page.mouse.click(pageBox.x + 100, pageBox.y + 300);
    const crossed = await overlayMark('text.mark').boundingBox();
    const cx = crossed.x - pageBox.x;
    const cy = crossed.y - pageBox.y;
    // The cursor must not advertise a drag the tool will not do: over a note a placing tool
    // shows the move cursor, while the drawing tools keep the crosshair.
    const cursorOverNote = () => page.evaluate(([x, y]) => getComputedStyle(document.elementFromPoint(x, y)).cursor,
      [crossed.x + 20, crossed.y + crossed.height / 2]);
    const textCursor = await cursorOverNote();
    await page.locator('.tool[data-tool="highlight"]').click();
    const highlightCursor = await cursorOverNote();
    check('a note shows the move cursor only in tools that can grab it',
      textCursor === 'move' && highlightCursor === 'crosshair', JSON.stringify([textCursor, highlightCursor]));
    await page.locator('.tool[data-tool="highlight"]').click();
    await dragOn(cx + 5, cy + 5, cx + 200, cy + 25);
    const crossedAfterHighlight = await overlayMark('text.mark').boundingBox();
    check('a highlight drag that starts on a note draws a highlight and leaves the note',
      await markCount() === 2 && Math.abs(crossedAfterHighlight.x - crossed.x) < 1 && Math.abs(crossedAfterHighlight.y - crossed.y) < 1,
      JSON.stringify([await markCount(), crossed, crossedAfterHighlight]));

    // Drawn after the note, so the ink's hit halo sits above it in the overlay.
    await page.locator('.swatch[data-color="black"]').click();
    await page.locator('.tool[data-tool="draw"]').click();
    await dragOn(cx - 10, cy + 8, cx + 150, cy + 8);
    await page.locator('.tool[data-tool="text"]').click();
    let promptedWith = null;
    page.once('dialog', dialog => { promptedWith = dialog.defaultValue(); dialog.dismiss(); });
    await page.mouse.click(crossed.x + 30, crossed.y + 8);
    check('an ink halo over a note does not hide the note from the text tool',
      promptedWith === 'Synthetic crossed note' && await markCount() === 3, JSON.stringify([promptedWith, await markCount()]));

    await page.locator('.tool[data-tool="signature"]').click();
    await page.mouse.click(pageBox.x + 450, pageBox.y + 150);
    await page.locator('.tool[data-tool="date"]').click();
    const signatureBox = await overlayMark('g.mark[data-kind="placed"]').boundingBox();
    await page.mouse.click(signatureBox.x + 20, signatureBox.y + 20);
    check('a date-tool click inside a signature stamps a date', await markCount() === 5, String(await markCount()));

    const dateStamp = page.locator('svg.overlay').first().locator('text.mark').filter({ hasText: /^\d{4}-\d{2}-\d{2}$/ });
    const dateBefore = await dateStamp.boundingBox();
    await dragOn(dateBefore.x - pageBox.x + 4, dateBefore.y - pageBox.y + 4, 5000, dateBefore.y - pageBox.y + 4);
    const dateAfter = await dateStamp.boundingBox();
    check('a short note can be dragged to the right margin',
      dateAfter.x + dateAfter.width <= pageBox.x + pageBox.width + 1
      && dateAfter.x + dateAfter.width >= pageBox.x + pageBox.width - 24, JSON.stringify([dateAfter, pageBox]));

    await dragOn(cx + 30, cy + 8, 5000, cy + 8);
    page.once('dialog', dialog => dialog.accept('Synthetic crossed note, now edited to be much longer than it was'));
    const crossedAtEdge = await overlayMark('text.mark').boundingBox();
    await page.mouse.click(crossedAtEdge.x + 10, crossedAtEdge.y + 8);
    const crossedEdited = await overlayMark('text.mark').boundingBox();
    check('editing a note to longer text keeps it on the page',
      crossedEdited.width > crossedAtEdge.width && crossedEdited.x + crossedEdited.width <= pageBox.x + pageBox.width + 1,
      JSON.stringify([crossedAtEdge, crossedEdited, pageBox]));

    // No provider stamp is guaranteed here, so the signature goes before the save.
    await page.locator('.tool[data-tool="select"]').click();
    await page.mouse.click(signatureBox.x + signatureBox.width - 10, signatureBox.y + signatureBox.height - 10);
    await page.locator('#btnSave').click();
    await waitForSave();
    check('marks moved and edited under the grab rules save', await markCount() === 4
      && await page.locator('#status').getAttribute('class') === 'status ok', await page.locator('#status').textContent());

    // Deleting an already-saved mark is an unsaved change too: Save comes back and leaving warns.
    // The click lands on the highlight's far end, clear of the ink stroke drawn across its start.
    const savedHighlight = await overlayMark('rect.mark').boundingBox();
    await page.mouse.click(savedHighlight.x + savedHighlight.width - 6, savedHighlight.y + savedHighlight.height - 4);
    check('deleting a saved mark enables saving again', await markCount() === 3
      && await page.locator('svg.overlay').first().locator('rect.mark').count() === 0
      && await page.locator('#btnSave').isEnabled(), String(await markCount()));
    let warnedAfterDelete = false;
    const deleteLeaveWarning = dialog => { if (dialog.type() === 'beforeunload') { warnedAfterDelete = true; } };
    page.on('dialog', deleteLeaveWarning);

    await openViewer();
    page.off('dialog', deleteLeaveWarning);
    check('leaving after deleting a saved mark warns about unsaved changes', warnedAfterDelete);

    // A note placed before the annotation font arrives is fitted to its width in the fallback
    // face. When the real, wider face lands the note must be refitted, or it runs off the page
    // and the composer refuses the save. The font is held back and a narrower fallback forced
    // (through the viewer's own stylesheet, since the page CSP admits no injected style) so the
    // two widths genuinely differ.
    let releaseFont;
    const fontHeld = new Promise(resolve => { releaseFont = resolve; });
    await page.route('**/dejavufonts/ttf/DejaVuSans.ttf', async route => {
      await fontHeld;
      await route.continue();
    });
    await openViewer('domcontentloaded');
    const narrowFallback = await page.evaluate(() => {
      const sheet = [...document.styleSheets].find(s => (s.href || '').includes('documentAnnotate.css'));
      if (!sheet) { return false; }
      sheet.insertRule('.page svg text { font-family: CarlosAnnotation, "Liberation Sans" !important; }', sheet.cssRules.length);
      return !document.fonts.check('11px CarlosAnnotation');
    });
    check('the annotation font is still loading while the note is placed', narrowFallback);
    await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
    const fontBox = await page.locator('svg.overlay').first().boundingBox();
    await page.locator('.tool[data-tool="text"]').click();
    page.once('dialog', dialog => dialog.accept('Synthetic note placed before the annotation font had loaded here'));
    await page.mouse.click(fontBox.x + fontBox.width - 10, fontBox.y + 200);
    const fallbackNote = await page.locator('svg.overlay').first().locator('text.mark').first().boundingBox();
    // Save is pressed while the font is still out: it must wait for the font and post the
    // refitted note, not a snapshot taken at the fallback width.
    let fontSavePosted = false;
    const watchFontSave = request => { if (request.url().includes('/SaveAnnotatedDocument')) { fontSavePosted = true; } };
    page.on('request', watchFontSave);
    await page.locator('#btnSave').click();
    await page.waitForTimeout(500);
    check('a save pressed before the annotation font loads waits for it', !fontSavePosted
      && await page.locator('#status').getAttribute('class') === 'status busy',
      JSON.stringify([fontSavePosted, await page.locator('#status').getAttribute('class')]));
    releaseFont();
    await waitForSave();
    page.off('request', watchFontSave);
    await page.unroute('**/dejavufonts/ttf/DejaVuSans.ttf');
    const fontNote = await page.locator('svg.overlay').first().locator('text.mark').first().boundingBox();
    check('a note placed before the font loaded is refitted onto the page once it arrives',
      fontNote.width > fallbackNote.width && fontNote.x + fontNote.width <= fontBox.x + fontBox.width + 1,
      JSON.stringify([fallbackNote, fontNote, fontBox]));
    check('a note refitted after the font loaded saves',
      await page.locator('#status').getAttribute('class') === 'status ok', await page.locator('#status').textContent());
    await openViewer();

    // If the font is later than the save's wait, the save posts fallback widths. A refit arriving
    // while that request is out must not change the model under it (the page would then report
    // as saved marks the filed copy lacks); it runs once the save ends. Here the fallback-width
    // note overruns the page in the real face, so the save is refused, the deferred refit pulls
    // the note back, and the retry files.
    let releaseLateFont;
    const lateFontHeld = new Promise(resolve => { releaseLateFont = resolve; });
    await page.route('**/dejavufonts/ttf/DejaVuSans.ttf', async route => {
      await lateFontHeld;
      await route.continue();
    });
    await openViewer('domcontentloaded');
    await page.evaluate(() => {
      const sheet = [...document.styleSheets].find(s => (s.href || '').includes('documentAnnotate.css'));
      sheet.insertRule('.page svg text { font-family: CarlosAnnotation, "Liberation Sans" !important; }', sheet.cssRules.length);
      window.scrollTo({ top: 0, behavior: 'instant' });
    });
    const lateBox = await page.locator('svg.overlay').first().boundingBox();
    await page.locator('.tool[data-tool="text"]').click();
    page.once('dialog', dialog => dialog.accept('Synthetic note saved before a very late annotation font arrived'));
    await page.mouse.click(lateBox.x + lateBox.width - 10, lateBox.y + 200);
    const lateNote = page.locator('svg.overlay').first().locator('text.mark').first();
    const lateBefore = await lateNote.boundingBox();
    let releaseLateSave;
    const lateSaveHeld = new Promise(resolve => { releaseLateSave = resolve; });
    let lateSaveSeen;
    const lateSaveArrived = new Promise(resolve => { lateSaveSeen = resolve; });
    await page.route('**/SaveAnnotatedDocument?*', async route => {
      lateSaveSeen();
      await lateSaveHeld;
      await route.continue();
    });
    await page.locator('#btnSave').click();
    await Promise.race([lateSaveArrived,
      new Promise((_, reject) => setTimeout(() => reject(new Error('Save did not post after the font wait')), 15000))]);
    releaseLateFont();
    await page.waitForFunction(() => document.fonts.check('11px CarlosAnnotation'));
    await page.waitForTimeout(300);
    const lateInFlight = await lateNote.boundingBox();
    check('a font arriving during a save does not move a note under the in-flight request',
      Math.abs(lateInFlight.x - lateBefore.x) < 1, JSON.stringify([lateBefore, lateInFlight]));
    releaseLateSave();
    await waitForSave();
    await page.unroute('**/SaveAnnotatedDocument?*');
    await page.unroute('**/dejavufonts/ttf/DejaVuSans.ttf');
    await page.waitForTimeout(300);
    const lateAfter = await lateNote.boundingBox();
    check('the refit deferred by a save runs once it ends',
      lateAfter.x + lateAfter.width <= lateBox.x + lateBox.width + 1 && await page.locator('#btnSave').isEnabled(),
      JSON.stringify([await page.locator('#status').textContent(), lateAfter, lateBox]));
    await page.locator('#btnSave').click();
    await waitForSave();
    check('a note refitted after its save ended saves on retry',
      await page.locator('#status').getAttribute('class') === 'status ok', await page.locator('#status').textContent());
    await openViewer();

    // The composer measures a note's full string, spaces included. A note ending in spaces,
    // placed hard against the right edge, must be fitted by that width or the save is refused.
    await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
    const spaceBox = await page.locator('svg.overlay').first().boundingBox();
    await page.locator('.tool[data-tool="text"]').click();
    page.once('dialog', dialog => dialog.accept('Synthetic note with trailing spaces' + ' '.repeat(24)));
    await page.mouse.click(spaceBox.x + spaceBox.width - 10, spaceBox.y + 260);
    await page.locator('#btnSave').click();
    await waitForSave();
    check('a note ending in spaces fits the page by its full width and saves',
      await page.locator('#status').getAttribute('class') === 'status ok', await page.locator('#status').textContent());
    await openViewer();
    let wordAttempts = 0;
    await page.route('**/DocumentTextBoxes?*', route => {
      wordAttempts += 1;
      return wordAttempts === 1 ? route.fulfill({ status: 503, body: '{}' }) : route.continue();
    });
    await mark();
    await page.waitForTimeout(200);
    await mark();
    check('text-layer HTTP failure retries on the next drag', wordAttempts >= 2);
    await page.unroute('**/DocumentTextBoxes?*');

    // Hold an actual server save response; attempted edits must not re-enable duplicate submission.
    let releaseSave;
    const hold = new Promise(resolve => { releaseSave = resolve; });
    let responseReceived;
    const received = new Promise(resolve => { responseReceived = resolve; });
    await page.route('**/SaveAnnotatedDocument?*', async route => {
      const result = await route.fetch();
      responseReceived();
      await hold;
      await route.fulfill({ response: result });
    });
    const beforeMarks = await page.locator('#markCount').textContent();
    await page.locator('#btnSave').click();
    await Promise.race([received, new Promise((_, reject) => setTimeout(() => reject(new Error('Save did not respond')), 30000))]);
    await mark('draw');
    check('edits cannot re-enable saving during an in-flight save',
      (await page.locator('#markCount').textContent()) === beforeMarks && await page.locator('#btnSave').isDisabled());
    // Existing marks are frozen too: a move or a select-click delete made while the request is
    // out would be missing from the copy being filed, yet marked saved when the response lands.
    await page.locator('.tool[data-tool="select"]').click();
    const inFlightBox = await page.locator('svg.overlay').first().locator('rect.mark').first().boundingBox();
    await page.mouse.move(inFlightBox.x + 6, inFlightBox.y + 6);
    await page.mouse.down();
    await page.mouse.move(inFlightBox.x + 6, inFlightBox.y + 106, { steps: 8 });
    await page.mouse.up();
    await page.mouse.click(inFlightBox.x + 6, inFlightBox.y + 6);
    const inFlightAfter = await page.locator('svg.overlay').first().locator('rect.mark').first().boundingBox();
    check('existing marks cannot be moved or deleted during an in-flight save',
      (await page.locator('#markCount').textContent()) === beforeMarks
      && Math.abs(inFlightAfter.y - inFlightBox.y) < 1 && await page.locator('#btnSave').isDisabled(),
      JSON.stringify([await page.locator('#markCount').textContent(), beforeMarks, inFlightBox, inFlightAfter]));
    const inFlightCursor = await page.evaluate(([x, y]) => getComputedStyle(document.elementFromPoint(x, y)).cursor,
      [inFlightAfter.x + 6, inFlightAfter.y + 6]);
    check('marks frozen by an in-flight save do not show the move cursor', inFlightCursor !== 'move', inFlightCursor);
    releaseSave();
    await waitForSave();
    await page.unroute('**/SaveAnnotatedDocument?*');

    await openViewer();
    await mark();
    await page.route('**/SaveAnnotatedDocument?*', route => route.abort('failed'));
    await page.locator('#btnSave').click();
    await waitForSave();
    check('lost save response gives a check-chart warning and prevents blind retries',
      (await page.locator('#status').textContent()).includes('could not be confirmed')
      && await page.locator('#btnSave').isDisabled());
    await page.unroute('**/SaveAnnotatedDocument?*');

    // A failed page image is visible and cannot be annotated as an empty sheet.
    await page.route('**/ManageDocument?method=showPage&**', route => route.fulfill({ status: 500, body: '' }));
    page.once('dialog', dialog => dialog.accept());
    await page.goto(`${baseUrl}/documentManager/AnnotateDocument?docId=${docId}`);
    await page.waitForFunction(() => document.querySelector('.page.load-failed'));
    check('failed page images report a visible error and do not enable saving',
      (await page.locator('#status').textContent()).includes('could not be loaded')
      && await page.locator('#btnSave').isDisabled());
    await page.unroute('**/ManageDocument?method=showPage&**');
    // No unsaved marks were present, so no before-unload dialog should have fired.
    page.removeAllListeners('dialog');

    await openViewer();
    await mark();
    await page.locator('#btnSaveFax').click();
    await page.waitForURL('**/fax/faxAction*', { timeout: 30000 });
    check('Save and fax reaches the protected POST cover page', await page.locator('#btnSend').count() === 1);
    const preview = await page.locator('input[name="faxFilePath"]').inputValue();
    check('document fax uses a staged copy', preview.includes('carlos-temp'));
    const countResponse = await context.request.get(`${baseUrl}/fax/faxAction`, {
      params: { method: 'getPageCount', faxFilePath: preview }
    });
    check('session-owned document fax preview can be read', countResponse.status() === 200);
    const directory = await context.request.get(`${baseUrl}/fax/SearchFaxRecipient?term=Test`);
    check('both recipient directory queries execute on MariaDB', directory.status() === 200 && Array.isArray(await directory.json()));
    await page.locator('#btnCancel').click();
    await page.waitForURL('**/documentManager/ManageDocument?*', { timeout: 30000 });
    check('cancelling returns to the saved document without a missing result error',
      !(await page.title()).includes('Error'));
    const cancelledPreview = await context.request.get(`${baseUrl}/fax/faxAction`, {
      params: { method: 'getPageCount', faxFilePath: preview }
    });
    check('cancelled document preview cannot be read again', cancelledPreview.status() === 403);
    check('annotation flow has no CSP or uncaught script errors', cspViolations.length === 0, cspViolations.join(' | '));
    await openViewer();
    const blockedInline = await page.evaluate(async () => {
      window.__annotationUnexpectedInline = false;
      const script = document.createElement('script');
      script.textContent = 'window.__annotationUnexpectedInline = true';
      document.body.appendChild(script);
      await new Promise(resolve => setTimeout(resolve, 100));
      return !window.__annotationUnexpectedInline;
    });
    check('the front door enforces the application CSP against an unnonced inline script', blockedInline);


  } finally {
    await context.close();
    await browser.close();
  }
}

main()
  .then(() => {
    notes.forEach((line) => console.log(line));
    if (findings.length) {
      console.log('');
      findings.forEach((line) => console.log(line));
      console.log(`\n${findings.length} check(s) failed`);
      process.exit(1);
    }
    console.log('\nAll annotation viewer checks passed');
  })
  .catch((error) => {
    notes.forEach((line) => console.log(line));
    console.error(`\nERROR ${error && error.message ? error.message : error}`);
    process.exit(1);
  });
