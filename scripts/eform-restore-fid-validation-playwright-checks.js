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
 * Browser regression check for issue #3571: POST /eform/restoreEForm must reject a
 * malformed fid with HTTP 400 instead of treating it as id 0, restoring nothing and
 * still answering with the success redirect (which made the UI report a restore that
 * never happened).
 *
 * The POSTs carry a real CSRF token copied from the deleted-eForms page, so a 400 here
 * is the action's own validation and not a CSRFGuard 403. A well-formed but nonexistent
 * fid must still pass validation (a redirect, not a 400), which proves the guard is not
 * over-broad. Every fid sent is either malformed or nonexistent, so the check can never
 * restore a real form: it creates nothing and changes nothing.
 *
 * Requires the deb-install env contract (docs/ui-tests/deb-install-validation.md §6):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN
 * Optional: CHROME_PATH, EFORM_SCREENSHOT_DIR (default /tmp).
 */

const { chromium } = require('playwright');
const {
  assert,
  assertNoPageErrors,
  buildFailureDetails,
  createRecorder,
  getLaunchOptions,
  gotoApp,
  login,
  screenshot,
  validateBaseUrl,
  wirePage,
} = require('./eform-local-playwright-utils');

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const config = {
  baseUrl,
  // Certificate errors are ignorable only against a loopback target (the packaged install's
  // self-signed certificate); never unconditionally (issue #3598).
  ignoreHTTPSErrors: ['127.0.0.1', 'localhost', '[::1]'].includes(baseUrl.hostname),
  chromePath: process.env.CHROME_PATH || '',
  testUser: process.env.TEST_USER || 'carlosdoc',
  testPassword: process.env.TEST_PASSWORD || 'carlos2026',
  testPin: process.env.TEST_PIN || '2026',
  screenshotDir: process.env.EFORM_SCREENSHOT_DIR || '/tmp',
};

// A positive int that cannot exist, so a regression cannot restore a real eForm.
const UNUSED_FID = '999999999';
const MALFORMED_FIDS = ['abc', '4x', '0', '-5', '1.5', '99999999999999', '%20'];

(async () => {
  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  const recorder = createRecorder();
  try {
    const context = await browser.newContext({ ignoreHTTPSErrors: config.ignoreHTTPSErrors, viewport: { width: 1400, height: 900 } });
    const loginPage = await login(context, config, recorder);
    await loginPage.close();

    const page = await context.newPage();
    wirePage(page, 'eform-restore-fid', recorder);
    await gotoApp(page, config.baseUrl, '/eform/efmformmanagerdeleted');
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

    const token = await page.evaluate(() => {
      const input = document.querySelector('input[name="CSRF-TOKEN"]');
      return input && input.value ? input.value : null;
    });
    assert(token, 'The deleted-eForms page exposed no CSRF token, so the POST probes cannot be authenticated.');

    // page.request shares the session cookies. The CSRF token travels in the form body, which
    // is how CSRFGuard validates a classic form POST, so a 400 below is the action's own
    // validation and not a CSRF rejection.
    const restoreUrl = `${config.baseUrl.href.replace(/\/+$/, '')}/eform/restoreEForm`;
    const restoreStatus = async (fid) => {
      const form = { 'CSRF-TOKEN': token };
      if (fid !== null) form.fid = fid;
      const response = await page.request.post(restoreUrl, { form, failOnStatusCode: false, maxRedirects: 0 });
      return response.status();
    };

    for (const bad of [...MALFORMED_FIDS.map((f) => decodeURIComponent(f)), null]) {
      const status = await restoreStatus(bad);
      assert(
        status === 400,
        `POST /eform/restoreEForm fid=${JSON.stringify(bad)} returned HTTP ${status}, expected 400. `
          + 'A malformed fid must be rejected, not treated as id 0 and answered with the success redirect.',
      );
    }

    const wellFormed = await restoreStatus(UNUSED_FID);
    assert(
      wellFormed >= 200 && wellFormed < 400,
      `POST /eform/restoreEForm fid=${UNUSED_FID} (well-formed, nonexistent) returned HTTP ${wellFormed}; `
        + 'validation must only reject malformed ids and let the normal flow through.',
    );

    await screenshot(page, config.screenshotDir, 'eform-restore-fid-validation');
    await page.close();
    assertNoPageErrors(recorder);
    await context.close();

    console.log(
      'PASS eForm restore fid validation: malformed, zero, negative, overflowing and missing fid are '
      + 'rejected with 400; a well-formed fid still takes the normal flow',
    );
  } catch (error) {
    console.error('FAIL eForm restore fid validation Playwright check');
    console.error(error.stack || error.message);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();
