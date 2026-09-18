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
 * Browser regression check for opening an appointment by appointment_no alone.
 *
 * The day sheet appends demographic_no, provider_no, date and time to the edit link, but a
 * link that carries only appointment_no — a bookmark, the receipt window, a support link —
 * does not. editappointment.jsp parsed those absent parameters anyway and answered HTTP 500
 * with a NumberFormatException before rendering (#3729).
 *
 * The strongest assertion available here is equivalence: the bare link and the full
 * day-sheet link address the same appointment, so they must render the same form. Checking
 * only "not 500" would pass on a page that rendered an empty or wrong-patient form.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:edit-appointment-direct-link-playwright
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc TEST_PASSWORD=carlos2026 TEST_PIN=2026
 *   APPOINTMENT_NO=1 APPOINTMENT_DEMOGRAPHIC_NO=1
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const { chromium } = require('playwright');

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';
const appointmentNo = process.env.APPOINTMENT_NO || '1';
const demographicNo = process.env.APPOINTMENT_DEMOGRAPHIC_NO || '1';

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
  return page.goto(url, { waitUntil: 'domcontentloaded', timeout: 30000 });
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

async function fetchEdit(page, query, label, expectedStatus) {
  const response = await gotoApp(page, '/appointment/editappointment', query);
  const status = response ? response.status() : 0;
  if (status !== expectedStatus) {
    failures.push(`${label}: expected HTTP ${expectedStatus}, got ${status}`);
    return null;
  }
  return status === 200 ? page.content() : '';
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

    // The #3729 case: only appointment_no, as a bookmark or receipt-window link carries it.
    const bare = await fetchEdit(page, { appointment_no: appointmentNo }, 'bare appointment_no link', 200);

    // The day sheet's own link to the same appointment.
    const daySheet = await fetchEdit(page, {
      appointment_no: appointmentNo,
      demographic_no: demographicNo,
      dboperation: 'search',
    }, 'day-sheet link', 200);

    if (bare && daySheet && bare !== daySheet) {
      failures.push('bare appointment_no link rendered a different form than the day-sheet link for the same appointment');
    }
    if (bare && !/appointment_no"\s+value="/.test(bare)) {
      failures.push('bare appointment_no link did not render the edit form (no appointment_no field)');
    }

    // A malformed or stale link is a client error, not a server fault.
    await fetchEdit(page, { dboperation: 'search' }, 'no appointment_no', 400);
    await fetchEdit(page, { appointment_no: 'abc' }, 'non-numeric appointment_no', 400);
    await fetchEdit(page, { appointment_no: '999999999' }, 'unknown appointment_no', 404);

    await page.close();
    await context.close();

    if (failures.length > 0) {
      console.error('FAIL edit appointment direct link Playwright check');
      for (const failure of failures) {
        console.error(`  - ${failure}`);
      }
      process.exitCode = 1;
    } else {
      console.log(`PASS appointment ${appointmentNo} opens from a bare link identically to the day-sheet link, and malformed links are 400/404`);
    }
  } catch (error) {
    console.error('FAIL edit appointment direct link Playwright check');
    console.error(error.stack || error.message);
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();
