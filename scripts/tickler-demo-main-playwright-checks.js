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
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Browser regression check for the demographic tickler page opened WITHOUT an opener.
 *
 * ticklerDemoMain.jsp's setup() runs from body onload and used to read
 * `window.opener.URLs` before any guard. `window.opener` is null on direct navigation,
 * a bookmark, or in a browser that severs the opener (COOP, rel=noopener, popup
 * blockers), so setup() threw "Cannot read properties of null (reading 'URLs')" and
 * every later statement in it was skipped (#3731).
 *
 * Navigating straight to the route — no window.open — is exactly the opener-less case,
 * so a plain page load is the regression test. The check fails on any uncaught page
 * error, which is what the bug produced.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:tickler-demo-main-playwright
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc TEST_PASSWORD=carlos2026 TEST_PIN=2026
 *   TICKLER_DEMOGRAPHIC_NO=1
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const {
  assert,
  buildFailureDetails,
  createRecorder,
  gotoApp,
  launchBrowser,
  login,
  newContext,
  readConfig,
  wirePage,
} = require('./lib/playwright-harness');

const config = readConfig();
const demographicNo = process.env.TICKLER_DEMOGRAPHIC_NO || '1';

(async () => {
  const recorder = createRecorder();
  const browser = await launchBrowser(config);
  try {
    // newContext applies isLocalTlsTarget, so certificate errors are waived only for a
    // loopback or RFC1918 target. An unconditional ignoreHTTPSErrors here would post
    // TEST_PASSWORD to a remote host with an invalid certificate under
    // ALLOW_NON_LOCAL_BASE_URL=true (issue #3598).
    const context = await newContext(browser, config);
    const landingPage = await login(context, config, recorder);
    await landingPage.close();

    const page = await context.newPage();
    wirePage(page, 'tickler-demo-main', recorder);

    // Direct navigation: this tab has no opener, which is the #3731 case.
    const query = new URLSearchParams({ demoview: demographicNo, ticklerview: 'A' });
    const response = await gotoApp(
      page, config.baseUrl, `/tickler/ViewTicklerDemoMain?${query.toString()}`, 'networkidle',
    );
    assert(response && response.status() === 200,
      `Tickler page returned ${response ? response.status() : 'no response'}`);

    assert(await page.evaluate(() => window.opener === null),
      'Expected an opener-less tab; the check would not exercise #3731');

    // setup() must run to completion, not throw partway through it.
    const setupError = await page.evaluate(() => {
      if (typeof window.setup !== 'function') {
        return 'setup() is not defined on the page';
      }
      try {
        window.setup();
        return null;
      } catch (error) {
        return error.message;
      }
    });
    assert(!setupError, `setup() threw when called without an opener: ${setupError}`);

    const pageErrors = recorder.pageErrors.filter((entry) => entry.label === 'tickler-demo-main');
    assert(pageErrors.length === 0,
      `Tickler page reported uncaught errors: ${JSON.stringify(pageErrors)}`);

    await page.close();
    await context.close();

    console.log('PASS tickler demo main loads without an opener and setup() completes');
  } catch (error) {
    console.error('FAIL tickler demo main Playwright check');
    console.error(error.stack || error.message);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();
