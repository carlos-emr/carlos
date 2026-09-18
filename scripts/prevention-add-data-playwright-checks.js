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
 * Browser regression check for the Add Prevention popup.
 *
 * AddPreventionData.jsp runs `disableifchecked(document.getElementById('neverWarn'),
 * 'nextDate')` from body onload. The "never warn" checkbox and the "next date" field are
 * rendered only for prevention types that schedule a follow-up, so for every other type
 * that call passed null and the page threw "Cannot read properties of null (reading
 * 'checked')" before the user touched anything (#3732).
 *
 * The check loads one prevention type that renders neither control and one that renders
 * both, so it pins the fix without hiding a regression in the case that always worked:
 * a guard that simply returned early for everything would still pass the first page but
 * fail the assertion that the second page's field really is wired up.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:prevention-add-data-playwright
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc TEST_PASSWORD=carlos2026 TEST_PIN=2026
 *   PREVENTION_DEMOGRAPHIC_NO=1
 *   PREVENTION_WITHOUT_NEXTDATE=Flu
 *   PREVENTION_WITH_NEXTDATE=HPV
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const { chromium } = require('playwright');

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';
const demographicNo = process.env.PREVENTION_DEMOGRAPHIC_NO || '1';
const typeWithoutNextDate = process.env.PREVENTION_WITHOUT_NEXTDATE || 'Flu';
const typeWithNextDate = process.env.PREVENTION_WITH_NEXTDATE || 'HPV';

const failures = [];

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

async function inspectPreventionPage(context, prevention) {
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.stack || error.message));

  const response = await gotoApp(page, '/prevention/ViewAddPreventionData', {
    demographic_no: demographicNo,
    prevention,
  });
  if (!response || response.status() !== 200) {
    failures.push(`${prevention}: page returned ${response ? response.status() : 'no response'}`);
    await page.close();
    return null;
  }

  const state = await page.evaluate(() => ({
    neverWarnPresent: !!document.getElementById('neverWarn'),
    nextDatePresent: !!document.getElementById('nextDate'),
    // Re-run the exact call body onload makes; it must not throw either way.
    reRunError: (() => {
      try {
        // eslint-disable-next-line no-undef
        disableifchecked(document.getElementById('neverWarn'), 'nextDate');
        return null;
      } catch (error) {
        return error.message;
      }
    })(),
  }));

  for (const error of errors) {
    failures.push(`${prevention}: uncaught page error — ${error}`);
  }
  if (state.reRunError) {
    failures.push(`${prevention}: disableifchecked threw — ${state.reRunError}`);
  }
  await page.close();
  return state;
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

    const without = await inspectPreventionPage(context, typeWithoutNextDate);
    const withNextDate = await inspectPreventionPage(context, typeWithNextDate);

    // Guard the fixtures: if the chosen types ever stop representing the two shapes, the
    // check would pass without exercising the bug.
    if (without && without.neverWarnPresent) {
      failures.push(`${typeWithoutNextDate} now renders a neverWarn control; pick another PREVENTION_WITHOUT_NEXTDATE so the null case is still covered`);
    }
    if (withNextDate && !withNextDate.nextDatePresent) {
      failures.push(`${typeWithNextDate} no longer renders a nextDate field; pick another PREVENTION_WITH_NEXTDATE so the wired-up case is still covered`);
    }

    await context.close();

    if (failures.length > 0) {
      console.error('FAIL add prevention data Playwright check');
      for (const failure of failures) {
        console.error(`  - ${failure}`);
      }
      process.exitCode = 1;
    } else {
      console.log(`PASS add prevention popup loads clean for ${typeWithoutNextDate} (no next-date controls) and ${typeWithNextDate} (with them)`);
    }
  } catch (error) {
    console.error('FAIL add prevention data Playwright check');
    console.error(error.stack || error.message);
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();
