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
const {
  assert,
  buildFailureDetails,
  createRecorder,
  getLaunchOptions,
  gotoApp,
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
};
const demographicNo = process.env.FORM_DEMOGRAPHIC_NO || '1';
const providerNo = process.env.FORM_PROVIDER_NO || '999998';

// Forms that carry the assets this check is about. formcaregiver is the page reported in
// #3727; the others carry the same pattern, which is why the fix swept every form/*.jsp
// rather than only the reported one. formannualfemaleprint is here because its images are
// assembled inside a JSP declaration, a shape the other three do not have.
//
// This list is a sample and cannot prove the sweep was complete. scripts/form-asset-paths.test.js
// resolves every reference in every form JSP statically for that.
const FORMS = [
  { name: 'formcaregiver', path: '/form/formcaregiver' },
  { name: 'formmmse', path: '/form/formmmse' },
  { name: 'formSF36', path: '/form/formSF36' },
  { name: 'formannualfemaleprint', path: '/form/formannualfemaleprint' },
];

// A form asset is anything served out of the form module. A request for one of these that
// does not return 200 is the #3727 failure: the bare relative path resolved against the page's
// <base> (the context root), or it was rewritten somewhere that does not exist, or the
// extension is missing from struts.action.excludePattern so Struts claims the request and 404s
// it before the container can serve the packaged file. Only same-origin URLs are considered,
// so CDN stylesheets the forms have nothing to do with are never policed here.
const FORM_ASSET = /\/(formScripts\.js|form\/graphics\/|graphics\/|form\/[A-Za-z0-9_-]+\.css)/;

const failures = [];

function formPath(form) {
  const query = new URLSearchParams({
    demographic_no: demographicNo,
    formId: '0',
    provNo: providerNo,
    parentAjaxId: 'forms',
  });
  return `${form.path}?${query.toString()}`;
}

function watchAssets(page, formName) {
  page.on('response', (response) => {
    const url = response.url();
    if (url.startsWith(config.baseUrl) && FORM_ASSET.test(url) && response.status() !== 200) {
      failures.push(`${formName}: ${response.status()} ${url}`);
    }
  });
  page.on('console', (message) => {
    const text = message.text();
    if (/Refused to execute script|MIME type/i.test(text)) {
      failures.push(`${formName}: console refusal — ${text}`);
    }
  });
}

(async () => {
  const recorder = createRecorder();
  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  try {
    const context = await browser.newContext({ ignoreHTTPSErrors: true });
    const landingPage = await login(context, config, recorder);
    await landingPage.close();

    let assetsSeen = 0;

    for (const form of FORMS) {
      const page = await context.newPage();
      wirePage(page, form.name, recorder);
      watchAssets(page, form.name);

      const response = await gotoApp(page, config.baseUrl, formPath(form), 'networkidle');
      if (!response || response.status() !== 200) {
        failures.push(`${form.name}: page returned ${response ? response.status() : 'no response'}`);
        await page.close();
        continue;
      }

      // Every asset reference the page emits must be absolute from the context root and point
      // into /form/. A bare relative value here is the defect, regardless of whether this
      // particular run happened to request it.
      const bareRefs = await page.evaluate(() => {
        const bad = [];
        for (const el of document.querySelectorAll('script[src], img[src], link[href]')) {
          const raw = el.getAttribute('src') || el.getAttribute('href') || '';
          if (/^(formScripts\.js|graphics\/|[A-Za-z0-9_-]+\.css)/.test(raw)) {
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

    assert(assetsSeen > 0, 'No form assets were found on any page; the check would pass vacuously');
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
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    await browser.close();
  }
})();
