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
    for (const method of ['GET', 'HEAD']) {
      const rejected = await context.request.fetch(config.baseUrl.origin + config.baseUrl.pathname + '/eform/deleteImage',
        {method, params: {filename: imageName}});
      assert(rejected.status() === 405, method + ' must not delete an image');
      assert(rejected.headers().allow === 'POST', '405 must advertise Allow: POST');
    }

    for (const scenario of ['standalone', 'administration', 'token-failure']) {
      if (scenario !== 'standalone') await uploadImage(context, recorder, imagePath, imageName);
      const managerPage = await context.newPage();
      wirePage(managerPage, 'image-delete-' + scenario, recorder);
      const inShell = scenario !== 'standalone';
      if (inShell) {
        await gotoApp(managerPage, config.baseUrl, '/administration?scheduleNav=1');
        await managerPage.waitForLoadState('networkidle');
        await managerPage.locator('#firstTable').waitFor({state: 'visible'});
      }

      let releaseToken;
      const heldToken = new Promise(resolve => { releaseToken = resolve; });
      let sawHeldToken;
      const tokenRequested = new Promise(resolve => { sawHeldToken = resolve; });
      let tokenMode = scenario === 'token-failure' ? 'fail' : 'hold';
      await managerPage.route('**/csrfguard*', async route => {
        const request = route.request();
        // jQuery loads fragment scripts through XHR; stall only fetchCsrfToken's fetch.
        if (request.resourceType() !== 'fetch' || request.frame() !== managerPage.mainFrame()) {
          return route.continue();
        }
        if (tokenMode === 'fail') {
          // A valid HTTP response with no token exercises the parse/retry failure path.
          return route.fulfill({status: 200, contentType: 'text/javascript', body: '// no token'});
        }
        if (tokenMode === 'hold') {
          sawHeldToken();
          await heldToken;
        }
        return route.continue();
      });
      try {
        if (inShell) {
          await managerPage.locator('button[data-bs-target="#collapseForms"]').first().click();
          await managerPage.locator('a.defaultImageUpload').first().click();
          await managerPage.locator('#dynamic-content #tblImage').waitFor({state: 'visible'});
          assert(new URL(managerPage.url()).pathname.endsWith('/administration'),
            'Image Library did not load through the Administration fragment path');
          // The shell may already have tokens from other panels. Force the same empty-token
          // condition as a newly loaded fragment, without replacing the application's fetch.
          await managerPage.locator('input[name="CSRF-TOKEN"]').evaluateAll(inputs => {
            for (const input of inputs) input.value = '';
          });
        } else {
          await gotoApp(managerPage, config.baseUrl, '/eform/efmimagemanager');
          await managerPage.waitForLoadState('load');
        }
        assert(await managerPage.locator('input[name="CSRF-TOKEN"]').first().inputValue() === '',
          'The fixture must exercise an empty CSRF token before clicking Delete');
        managerPage.removeAllListeners('dialog');
        managerPage.on('dialog', async dialog => {
          recorder.dialogs.push({label: scenario, type: dialog.type(), text: dialog.message()});
          await dialog.accept();
        });
        const row = managerPage.locator('#tblImage tbody tr', {hasText: imageName}).first();
        const deleteLink = row.locator('a[onclick^="deleteImg"]').first();
        await deleteLink.waitFor({state: 'visible'});
        let deletePosts = 0;
        managerPage.on('request', request => {
          if (request.url().includes('/eform/deleteImage') && request.method() === 'POST') deletePosts++;
        });

        if (scenario === 'token-failure') {
          const alert = managerPage.waitForEvent('dialog', {predicate: d => d.type() === 'alert'});
          await deleteLink.click();
          assert((await alert).message().length > 0, 'Token failure did not give a user-facing message');
          assert(deletePosts === 0, 'Token failure submitted a delete request');
          assert(await row.count() === 1, 'Token failure removed the image row');
          console.log('PASS token failure: alert shown, no delete POST, image retained');
          tokenMode = 'hold';
        }

        const responsePromise = managerPage.waitForResponse(
          r => r.url().includes('/eform/deleteImage') && r.request().method() === 'POST',
          {timeout: 60000});
        await deleteLink.click();
        let timer;
        try {
          await Promise.race([tokenRequested, new Promise((_, reject) => {
            timer = setTimeout(() => reject(new Error('Delete did not request a CSRF token')), 15000);
          })]);
          assert(deletePosts === 0, 'Delete posted before the stalled token fetch completed');
        } finally {
          clearTimeout(timer);
          tokenMode = 'pass';
          releaseToken();
        }
        const deleteResponse = await responsePromise;
        assertNotBlocked(deleteResponse, 'Deleting an eForm image');
        assert(deleteResponse.status() >= 300 && deleteResponse.status() < 400,
          'Successful deletion must use POST/redirect/GET');
        const post = new URLSearchParams(deleteResponse.request().postData());
        assert(Boolean(post.get('CSRF-TOKEN')), 'Delete POST did not carry its token');
        assert(post.get('filename') === imageName, 'Delete POST selected the wrong fixture');
        await managerPage.waitForURL(url => inShell
          ? url.pathname.endsWith('/administration') && url.searchParams.get('show') === 'ImageUpload'
          : url.pathname.endsWith('/eform/efmimagemanager'));
        await managerPage.waitForLoadState('networkidle');
        if (inShell) {
          assert(new URL(managerPage.url()).searchParams.get('scheduleNav') === '1',
            'Delete redirect lost scheduleNav=1');
          await managerPage.locator('#firstTable').waitFor({state: 'visible'});
          await managerPage.locator('#dynamic-content #tblImage').waitFor({state: 'visible'});
        }
        await managerPage.reload({waitUntil: 'networkidle'});
        await managerPage.locator('#tblImage').waitFor({state: 'visible'});
        assert(await managerPage.locator('#tblImage a.viewImage', {hasText: imageName}).count() === 0,
          'Uploaded image still appears after delete and reload');
        assert(deletePosts === 1, 'A single delete click submitted more than one POST');
        console.log('PASS ' + scenario + ': stalled token, redirect, navigation and deletion persistence');
      } finally {
        releaseToken();
        await managerPage.close();
      }
    }
    assertNoPageErrors(recorder);
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
