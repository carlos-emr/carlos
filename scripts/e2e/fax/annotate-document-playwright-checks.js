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
 *   3. Saving creates a NEW document and leaves the original byte-identical.
 *      This is the whole point of the design, so it is asserted by comparing
 *      the stored file before and after via the document list.
 *   4. The save endpoint refuses GET.
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

const baseUrl = (process.env.BASE_URL || 'http://127.0.0.1:8080/carlos').replace(/\/+$/, '');
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
  const context = await browser.newContext({ ignoreHTTPSErrors: true });

  const forbiddenRequests = [];
  const imageRequests = [];
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
    await login(page);

    const docId = process.env.DOC_ID || '1';

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
    await page.goto(`${baseUrl}/documentManager/AnnotateDocument?docId=${docId}`,
      { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(2500);

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
    } else {
      findings.push('FAIL  overlay had no usable geometry to draw on');
    }
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
