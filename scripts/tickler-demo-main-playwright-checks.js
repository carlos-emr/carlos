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

const { chromium } = require('playwright');

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';
const demographicNo = process.env.TICKLER_DEMOGRAPHIC_NO || '1';

const pageErrors = [];

function validateBaseUrl(rawBaseUrl) {
  const parsed = new URL(rawBaseUrl);
  if (parsed.username || parsed.password) {
    throw new Error('BASE_URL must not embed a username or password');
  }
  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw new Error(`BASE_URL must use http or https, got ${parsed.protocol}`);
  }
  const host = parsed.hostname.toLowerCase();
  const localHosts = new Set(['localhost', '127.0.0.1', '::1', '0.0.0.0', 'host.docker.internal', 'carlos']);
  const privateIpv4 = /^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[0-1])\.)/.test(host);
  if (!localHosts.has(host) && !privateIpv4 && process.env.ALLOW_NON_LOCAL_BASE_URL !== 'true') {
    throw new Error(`Refusing non-local BASE_URL host ${host}; set ALLOW_NON_LOCAL_BASE_URL=true for an intentional test target`);
  }
  parsed.pathname = parsed.pathname.replace(/\/$/, '');
  return parsed;
}

function appUrl(appPath, query) {
  const url = new URL(baseUrl.toString());
  url.pathname = `${baseUrl.pathname}${appPath}`;
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      url.searchParams.set(key, value);
    }
  }
  return url.toString();
}

async function gotoApp(page, appPath, query = null) {
  const url = appUrl(appPath, query);
  // BASE_URL is restricted by validateBaseUrl(), and appUrl() only accepts root-relative app paths.
  // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection
  return page.goto(url, { waitUntil: 'networkidle', timeout: 30000 });
}

async function login(page) {
  await gotoApp(page, '/');
  await page.locator('#username').fill(testUser);
  await page.locator('#password').fill(testPassword);
  await page.locator('#pin').fill(testPin);
  await Promise.all([
    page.waitForURL(/providercontrol/, { timeout: 30000 }),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
}

(async () => {
  const launchOptions = {
    headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  };
  if (chromePath) {
    launchOptions.executablePath = chromePath;
  }

  const browser = await chromium.launch(launchOptions);
  const context = await browser.newContext({ ignoreHTTPSErrors: true });
  try {
    const loginPage = await context.newPage();
    await login(loginPage);
    await loginPage.close();

    const page = await context.newPage();
    page.on('pageerror', (error) => pageErrors.push(error.stack || error.message));

    // Direct navigation: this tab has no opener, which is the #3731 case.
    const response = await gotoApp(page, '/tickler/ViewTicklerDemoMain', {
      demoview: demographicNo,
      ticklerview: 'A',
    });
    if (!response || response.status() !== 200) {
      throw new Error(`Tickler page returned ${response ? response.status() : 'no response'}`);
    }

    const hasOpener = await page.evaluate(() => window.opener !== null);
    if (hasOpener) {
      throw new Error('Expected an opener-less tab; the check would not exercise #3731');
    }

    // setup() must have run to completion, not thrown partway through it.
    const setupCompleted = await page.evaluate(() => {
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
    if (setupCompleted) {
      pageErrors.push(`setup() threw when called without an opener: ${setupCompleted}`);
    }

    await page.close();
    await context.close();

    if (pageErrors.length > 0) {
      console.error('FAIL tickler demo main Playwright check');
      for (const error of pageErrors) {
        console.error(`  - ${error}`);
      }
      process.exitCode = 1;
    } else {
      console.log('PASS tickler demo main loads without an opener and setup() completes');
    }
  } catch (error) {
    console.error('FAIL tickler demo main Playwright check');
    console.error(error.stack || error.message);
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();
