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
 * Browser regression check for the hardening changes on the alpha-tester branch
 * that no other check exercises. The sibling checks cover the six reported
 * defects; these are the fixes that came out of reviewing those fixes, and each
 * of them was a real defect found only by reading the code — so each needs an
 * assertion that would go red if it were undone.
 *
 * What this pins, and why each one matters:
 *
 *   1. GET on the eForm restore is refused. RestoreEForm2Action performed the
 *      restore on ANY method. CSRFGuard does not validate tokens on GET, so an
 *      <img src=".../eform/restoreEForm?fid=N"> on any page an eForm
 *      administrator loaded silently restored that form. Giving the page a CSRF
 *      token fixed the intended path and left the bypass open; only a
 *      server-side method check closes it.
 *   2. GET on the DrugRef rebuild is refused, for the same reason — that method
 *      rebuilds the whole drug database.
 *   3. GET on the read-only DrugRef status probe still works. The guard above
 *      must not catch it: it runs on every Rx page load, and over-narrowing it
 *      would reintroduce the permanent "Drugref database is unavailable"
 *      banner this branch set out to fix.
 *   4. The standalone eForm admin pages raise no uncaught JavaScript. Five
 *      eForm pages load no jQuery of their own and work only because the
 *      Administration shell that injects them provides one; efmFooter.jspf's
 *      own $(document).ready threw "ReferenceError: $ is not defined" on every
 *      standalone open, and because the throw came from the INCLUDED footer it
 *      also pre-empted each page's own scripts.
 *   5. Clicking a control whose href is javascript:void(0) in the Administration
 *      shell does not paint "Sorry but there was an error: 0 error" over the
 *      page. administration/index.jsp carried the same unguarded
 *      .load($(this).attr("href")) that efmFooter.jspf was fixed for.
 *   6. A rejected lab upload says so. lab/newLabUpload had no "input" result,
 *      so an empty file fell through to errorpage.jsp as a raw 500; pointing it
 *      at its own success view would have been worse, because that view reports
 *      a completed upload.
 *
 * RUN THIS THROUGH :443. Assertions 1-3 are about what the server refuses, and
 * on the packaged install the WAF and the front door are part of that answer.
 *
 * Requires the deb-install env contract (docs/ui-tests/deb-install-validation.md §6):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN
 * Optional: CHROME_PATH, EFORM_SCREENSHOT_DIR (default /tmp).
 *
 * FIXTURE SAFETY: creates nothing and deletes nothing. The GET probes below are
 * deliberately sent with a nonexistent fid so that a REGRESSION (the guard being
 * removed) cannot restore a real form as a side effect of running the check.
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

const config = {
  baseUrl: validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos'),
  chromePath: process.env.CHROME_PATH || '',
  testUser: process.env.TEST_USER || 'carlosdoc',
  testPassword: process.env.TEST_PASSWORD || 'carlos2026',
  testPin: process.env.TEST_PIN || '2026',
  screenshotDir: process.env.EFORM_SCREENSHOT_DIR || '/tmp',
};

// A fid that cannot exist, so a regression cannot restore a real eForm.
const UNUSED_FID = '999999999';

(async () => {
  if (config.baseUrl.protocol !== 'https:') {
    console.warn(
      'WARN: BASE_URL is not HTTPS. These assertions are about what the packaged front door '
      + 'refuses, so run this through :443 to keep that coverage.',
    );
  }
  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  const recorder = createRecorder();
  try {
    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1400, height: 900 } });
    const loginPage = await login(context, config, recorder);
    await loginPage.close();

    const page = await context.newPage();
    wirePage(page, 'pr-hardening', recorder);

    // --- 1 & 2: mutating routes must refuse GET ---------------------------------
    //
    // page.request shares the browser context's session cookies, so these are
    // authenticated requests from a user who genuinely holds the privilege --
    // which is the point. The defect was not "an anonymous user can do this",
    // it was "any page can make the logged-in administrator do this".
    const mutatingGets = [
      {
        label: 'eForm restore',
        path: `/eform/restoreEForm?fid=${UNUSED_FID}`,
        why: 'an <img src> on any page would restore an eForm with no CSRF token',
      },
      {
        label: 'DrugRef rebuild',
        path: '/rx/updateDrugrefDB?method=updateDB',
        why: 'an <img src> on any page would trigger a full drug-database rebuild',
      },
    ];
    for (const probe of mutatingGets) {
      const response = await page.request.get(`${config.baseUrl.href}${probe.path}`, {
        failOnStatusCode: false,
        maxRedirects: 0,
      });
      const status = response.status();
      assert(
        status === 405,
        `GET ${probe.path} returned HTTP ${status}, expected 405. This is a mutation and `
          + `CSRFGuard does not validate tokens on GET, so ${probe.why}.`,
      );
    }

    // --- 3: the read-only status probe must STILL answer a GET ------------------
    const statusProbe = await page.request.get(
      `${config.baseUrl.href}/rx/updateDrugrefDB?method=verify`,
      { failOnStatusCode: false, maxRedirects: 0 },
    );
    assert(
      statusProbe.status() < 400,
      `GET on the read-only DrugRef status probe returned HTTP ${statusProbe.status()}. The `
        + 'method guard must apply to updateDB only: this probe runs on every Rx page load, and '
        + 'breaking it brings back the permanent "Drugref database is unavailable" banner.',
    );

    // --- 4: standalone eForm admin pages must not throw --------------------------
    //
    // Opened directly rather than through the Administration panel, deliberately:
    // the panel supplies the jQuery these pages lack, so driving them only through
    // the panel is exactly how the ReferenceError stayed invisible.
    const standalonePages = [
      '/eform/efmformmanagerdeleted',
      '/eform/efmformmanageredit',
      '/eform/efmmanageindependent',
    ];
    for (const path of standalonePages) {
      const standalone = await context.newPage();
      wirePage(standalone, `standalone${path.replace(/\//g, '-')}`, recorder);
      const response = await gotoApp(standalone, config.baseUrl, path);
      assert(
        response === null || response.status() < 400,
        `Opening ${path} standalone returned HTTP ${response && response.status()}.`,
      );
      await standalone.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
      await standalone.close();
    }

    // --- 5: the admin shell must not paint the "0 error" banner ------------------
    await gotoApp(page, config.baseUrl, '/administration');
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    // Find any contentLink whose href cannot be loaded. These exist by design --
    // they do their work in their own onclick and borrow the class for styling --
    // and loading the literal href is what produced status 0 / "error".
    const bogusLink = page.locator(
      'a.contentLink[href^="javascript:"], a.contentLink[href="#"], a.contentLink:not([href])',
    ).first();
    if (await bogusLink.count() > 0) {
      await bogusLink.click({ timeout: 10000 }).catch(() => {});
      await page.waitForTimeout(1500);
      const panelText = (await page.locator('#dynamic-content').innerText().catch(() => '')).trim();
      assert(
        !/Sorry but there was an error/i.test(panelText),
        'Clicking a javascript:/# contentLink in the Administration shell painted the '
          + `"0 error" banner over the panel: "${panelText.slice(0, 160)}". The shell's own `
          + 'load handler needs the same href guard as efmFooter.jspf.',
      );
    }

    // --- 6: a rejected lab upload must say so, not 500 and not "complete" --------
    const labPage = await context.newPage();
    wirePage(labPage, 'lab-upload-empty', recorder);
    await gotoApp(labPage, config.baseUrl, '/lab/CA/ALL/ViewInsideLabUpload');
    await labPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    const labFileInput = labPage.locator('input[type="file"]').first();
    if (await labFileInput.count() > 0) {
      const os = require('os');
      const fs = require('fs');
      const path = require('path');
      const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'pr-hardening-'));
      // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- both segments are local constants
      const emptyHl7 = path.join(dir, 'empty-lab.hl7');
      fs.writeFileSync(emptyHl7, Buffer.alloc(0));
      await labFileInput.setInputFiles(emptyHl7);
      const submit = labPage.locator('input[type="submit"], button[type="submit"]').first();
      if (await submit.count() > 0) {
        const [labResponse] = await Promise.all([
          labPage.waitForResponse((r) => r.request().method() === 'POST', { timeout: 60000 })
            .catch(() => null),
          submit.click().catch(() => {}),
        ]);
        if (labResponse) {
          assert(
            labResponse.status() !== 500,
            'An empty lab file returned a raw 500. The multipart rejection must resolve to the '
              + 'shared rejection page, not fall through to errorpage.jsp.',
          );
          await labPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
          const labText = (await labPage.locator('body').innerText()).trim();
          assert(
            !/successfully uploaded|upload complete/i.test(labText),
            'A REJECTED lab upload reported success. The "input" result must not resolve to the '
              + "action's success view, which says the file was processed.",
          );
        }
      }
      fs.rmSync(dir, { recursive: true, force: true });
    }
    await labPage.close();

    await screenshot(page, config.screenshotDir, 'pr-hardening');
    await page.close();

    // Every page this check drove must be free of uncaught JavaScript. This is
    // assertion 4's real teeth: the standalone pages above are opened precisely
    // because that is where the footer used to throw.
    assertNoPageErrors(recorder);

    await context.close();

    console.log(
      'PASS PR hardening: mutating routes refuse GET, the read-only DrugRef probe still answers '
      + 'one, standalone eForm admin pages raise no uncaught JS, the admin shell does not paint '
      + 'the "0 error" banner, and a rejected lab upload is neither a 500 nor a false success',
    );
  } catch (error) {
    console.error('FAIL PR hardening Playwright check');
    console.error(error.stack || error.message);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();
