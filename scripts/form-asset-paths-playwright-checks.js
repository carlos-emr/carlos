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
 * Browser regression check for static-asset paths on the legacy `form/*.jsp` pages.
 *
 * These pages emit `<base href="<context root>/">`, so a bare relative reference like
 * `src="formScripts.js"` resolves against the CONTEXT ROOT, not against `/form/` where
 * the file actually lives. Every such reference 404s, and for the script the browser
 * additionally refuses to execute the returned error page ("MIME type ('text/html') is
 * not executable"), so whatever formScripts.js provides silently never loads (#3727).
 *
 * The check drives each form in a real browser and fails on any request for a form
 * asset that does not come back 200 — which is what regressed, and what a re-introduced
 * bare relative path would do again. Asserting on the served markup alone would not
 * catch it, because the markup is only wrong relative to the <base> the browser applies.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:form-asset-paths-playwright
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc TEST_PASSWORD=carlos2026 TEST_PIN=2026
 *   FORM_DEMOGRAPHIC_NO=1
 *   FORM_PROVIDER_NO=999998
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const { chromium } = require('playwright');

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';
const demographicNo = process.env.FORM_DEMOGRAPHIC_NO || '1';
const providerNo = process.env.FORM_PROVIDER_NO || '999998';

// Forms that carry the assets this check is about. formcaregiver is the page reported in
// #3727; the Rourke and MMSE pages carry the same pattern for their chart images, which is
// why the fix swept every form/*.jsp rather than only the reported one.
const FORMS = [
  { name: 'formcaregiver', path: '/form/formcaregiver' },
  { name: 'formmmse', path: '/form/formmmse' },
  { name: 'formSF36', path: '/form/formSF36' },
];

// A form asset is anything served out of the form module. A request for one of these that
// does not return 200 is the #3727 failure: either the bare relative path resolved to the
// context root (404) or the path was rewritten to somewhere that does not exist.
const FORM_ASSET = /\/(formScripts\.js|form\/graphics\/|graphics\/)/;

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

function watchAssets(page, formName) {
  page.on('response', (response) => {
    const url = response.url();
    if (!FORM_ASSET.test(url)) {
      return;
    }
    if (response.status() !== 200) {
      failures.push(`${formName}: ${response.status()} ${url}`);
    }
  });
  // A stylesheet/script that 404s to an HTML error page also surfaces here.
  page.on('console', (message) => {
    const text = message.text();
    if (/Refused to execute script|MIME type/i.test(text)) {
      failures.push(`${formName}: console refusal — ${text}`);
    }
  });
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

    let assetsSeen = 0;

    for (const form of FORMS) {
      const page = await context.newPage();
      watchAssets(page, form.name);
      const response = await gotoApp(page, form.path, {
        demographic_no: demographicNo,
        formId: '0',
        provNo: providerNo,
        parentAjaxId: 'forms',
      });
      if (!response || response.status() !== 200) {
        failures.push(`${form.name}: page returned ${response ? response.status() : 'no response'}`);
        await page.close();
        continue;
      }

      // Every asset reference the page emits must be absolute from the context root and
      // point into /form/. A bare relative value here is the defect, regardless of whether
      // this particular run happened to request it.
      const bareRefs = await page.evaluate(() => {
        const bad = [];
        for (const el of document.querySelectorAll('script[src], img[src], link[href]')) {
          const raw = el.getAttribute('src') || el.getAttribute('href') || '';
          if (/^(formScripts\.js|graphics\/)/.test(raw)) {
            bad.push(raw);
          }
        }
        return bad;
      });
      for (const raw of bareRefs) {
        failures.push(`${form.name}: bare relative asset reference "${raw}" resolves against <base>, not /form/`);
      }

      const resolved = await page.evaluate(() => Array.from(
        document.querySelectorAll('script[src], img[src]'),
        (el) => el.src,
      ).filter((src) => /\/form\/(formScripts\.js|graphics\/)/.test(src)));
      assetsSeen += resolved.length;

      await page.close();
    }

    if (assetsSeen === 0) {
      failures.push('No form assets were found on any page; the check would pass vacuously');
    }

    await context.close();

    if (failures.length > 0) {
      console.error('FAIL form asset path Playwright check');
      for (const failure of failures) {
        console.error(`  - ${failure}`);
      }
      process.exitCode = 1;
    } else {
      console.log(`PASS form asset paths resolve under /form/ (${assetsSeen} assets across ${FORMS.length} forms)`);
    }
  } catch (error) {
    console.error('FAIL form asset path Playwright check');
    console.error(error.stack || error.message);
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();
