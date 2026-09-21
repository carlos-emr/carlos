#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Local-only browser regression check for the eForm Image Library's Delete
 * control (Administration > eForms > Image Library, efmimagemanager.jsp).
 *
 * The defect: deleteImg() built its POST form in JavaScript at click time and
 * never attached a CSRF-TOKEN input, and the page never included
 * csrf-token.jspf, so it had no hidden input for CSRFGuard's client script to
 * populate either. CarlosCsrfGuardFilter rejects a token-less POST with 403
 * ("Required Token is missing from the Request") -- the exact 403 an operator
 * sees deleting an image, on every attempt, not just a race. The sibling
 * eForm-library delete control (confirmNDelete() in efmformmanager.jsp) had
 * already been fixed for the narrower version of this bug where the page DOES
 * bootstrap a token but an early click can still race the async fetch; see
 * eform-admin-crud-playwright-checks.js. This check pins both: efmimagemanager
 * now bootstraps a token via csrf-token.jspf, and deleteImg() attaches it, so
 * it must survive the same early-click race that check drives.
 *
 * RUN THIS THROUGH :443 for full packaged-install parity. Against bare Tomcat
 * the CSRF assertions still hold; only the WAF layer is not exercised, and
 * this defect lives entirely in the application layer, not the WAF.
 *
 * FIXTURE SAFETY: this check uploads its own uniquely-named image and deletes
 * only that one. A PASSING run leaves nothing behind: the delete step is the
 * last mutation. A FAILING run leaves the probe image in the eForm image
 * directory for diagnosis; names are timestamped so repeated failures
 * accumulate rather than collide.
 */

const fs = require('fs');
const os = require('os');
const path = require('path');
const { chromium } = require('playwright');
const {
  assert,
  assertNoPageErrors,
  buildFailureDetails,
  createRecorder,
  getLaunchOptions,
  gotoApp,
  isLocalTlsTarget,
  login,
  validateBaseUrl,
  wirePage,
} = require('./lib/playwright-harness');

const config = {
  baseUrl: validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos'),
  chromePath: process.env.CHROME_PATH || '',
  testUser: process.env.TEST_USER || 'carlosdoc',
  testPassword: process.env.TEST_PASSWORD || 'carlos2026',
  testPin: process.env.TEST_PIN || '2026',
  resetPassword: process.env.RESET_PASSWORD || '',
};

const stamp = Date.now();
const imageName = `playwright_image_delete_${stamp}.png`;
// A minimal valid 1x1 PNG; only its bytes matter, never its content.
const onePixelPngBase64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl5n2QAAAAASUVORK5CYII=';

function assertNotBlocked(response, what) {
  const status = response.status();
  assert(
    status !== 403,
    `${what} was rejected with 403. efmimagemanager.jsp must bootstrap a CSRF token `
      + '(csrf-token.jspf) and deleteImg() must attach it to the form it builds at click time -- '
      + 'CarlosCsrfGuardFilter rejects a token-less POST unconditionally.',
  );
  assert(status < 400, `${what} returned HTTP ${status}`);
}

async function uploadImage(context, recorder, imagePath, name) {
  const page = await context.newPage();
  wirePage(page, 'image-delete-upload', recorder);
  try {
    await gotoApp(page, config.baseUrl, '/eform/efmimagemanager');
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    const frame = page.frameLocator('#uploadFrame');
    await frame.locator('#image').setInputFiles(imagePath);
    await frame.locator('input.upload[type="submit"]').click();
    await page.waitForURL(/administration\?show=ImageUpload/, { timeout: 10000 }).catch(() => {});
    await gotoApp(page, config.baseUrl, '/eform/efmimagemanager');
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await page.locator('#tblImage a.viewImage', { hasText: name }).first()
      .waitFor({ state: 'visible', timeout: 15000 });
  } finally {
    await page.close();
  }
}

(async () => {
  const recorder = createRecorder();
  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-eform-image-delete-'));
  const imagePath = path.join(tempDir, imageName); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- imageName is generated from a timestamp and validated below before joining under a private temp directory
  assert(/^[A-Za-z0-9_.-]+$/.test(imageName), `Invalid generated image fixture name: ${imageName}`);
  fs.writeFileSync(imagePath, Buffer.from(onePixelPngBase64, 'base64'));

  try {
    if (config.baseUrl.protocol !== 'https:') {
      console.log(
        '[warn] BASE_URL is not HTTPS, so this run does NOT go through nginx/ModSecurity. '
        + 'The defect this check pins lives entirely in the application layer, so the '
        + 'assertions below still cover it.',
      );
    }

    // Never unconditionally: see isLocalTlsTarget in playwright-harness.js. A
    // non-loopback ALLOW_NON_LOCAL_BASE_URL target must still present a
    // certificate the browser trusts, so a forged/expired cert on such a host
    // fails this check instead of posting TEST_PASSWORD to it unverified.
    const context = await browser.newContext({
      ignoreHTTPSErrors: isLocalTlsTarget(config.baseUrl),
      viewport: { width: 1400, height: 900 },
    });
    const landingPage = await login(context, config, recorder);
    await landingPage.close();

    await uploadImage(context, recorder, imagePath, imageName);

    // Delete while the CSRF token fetch is still in flight. csrf-token.jspf
    // populates the hidden CSRF-TOKEN input from an async fetch; a click that
    // lands before it resolves reads an empty value, and submitting anyway
    // reproduces the exact 403 an operator sees. Stalling the fetch widens
    // that window enough to click inside it reliably.
    const csrfStallMs = 5000;
    await context.route('**/csrfguard*', async (route) => {
      if (route.request().resourceType() !== 'script') {
        await new Promise((resolve) => setTimeout(resolve, csrfStallMs));
      }
      await route.continue();
    });

    const managerPage = await context.newPage();
    wirePage(managerPage, 'image-delete-manager', recorder);
    await gotoApp(managerPage, config.baseUrl, '/eform/efmimagemanager');
    // 'load', not 'networkidle': the stalled token fetch must still be in
    // flight when we read the input below.
    await managerPage.waitForLoadState('load', { timeout: 30000 }).catch(() => {});

    // wirePage installs a dialog handler that dismisses confirm(), which would
    // cancel deleteImg() before it ever builds the form. Replace it for this
    // page only.
    managerPage.removeAllListeners('dialog');
    managerPage.on('dialog', async (dialog) => {
      recorder.dialogs.push({ label: 'image-delete-manager', type: dialog.type(), text: dialog.message() });
      await dialog.accept().catch(() => {});
    });

    const tokenAtLoad = await managerPage.locator('input[name="CSRF-TOKEN"]').first().inputValue();
    assert(
      tokenAtLoad === '',
      'The /csrfguard stall did not hold: the CSRF token was already populated on load, so this '
        + 'run did not exercise the empty-token race it is meant to cover.',
    );

    const row = managerPage.locator('#tblImage tbody tr', { hasText: imageName }).first();
    await row.waitFor({ state: 'visible', timeout: 15000 });
    const deleteLink = row.locator('a[onclick^="deleteImg"]').first();
    assert(await deleteLink.count() > 0, `No delete control on the row for ${imageName}`);

    const [deleteResponse] = await Promise.all([
      managerPage.waitForResponse(
        (r) => r.url().includes('/eform/deleteImage') && r.request().method() === 'POST',
        { timeout: 60000 },
      ),
      deleteLink.click(),
    ]);
    // Reaching here at all is part of the assertion: before the fix, the click
    // submitted immediately with no token and this POST came back 403.
    assertNotBlocked(deleteResponse, 'Deleting an eForm image');
    await context.unroute('**/csrfguard*');
    await managerPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

    const landedOn = managerPage.url();
    assert(
      landedOn.includes('/eform/efmimagemanager'),
      `After deleting, the operator should land back on the Image Library, got ${landedOn}`,
    );

    // Persistence, not the redirect, is the proof: reload from scratch.
    await gotoApp(managerPage, config.baseUrl, '/eform/efmimagemanager');
    await managerPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    const remaining = await managerPage.locator('#tblImage a.viewImage', { hasText: imageName }).count();
    assert(remaining === 0, `Uploaded image ${imageName} still appears in the Image Library after deleting it`);

    assertNoPageErrors(recorder);
    await managerPage.close();
    await context.close();

    console.log(
      `PASS eForm Image Library delete (${imageName}): delete was not CSRF-blocked and the `
      + 'image is gone after reload',
    );
  } catch (error) {
    console.error('FAIL eForm image delete Playwright check');
    console.error(error.stack || error.message);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    await browser.close();
    fs.rmSync(tempDir, { recursive: true, force: true });
  }
})();
