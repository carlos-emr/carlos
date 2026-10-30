#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Captures the Annual Health Review pages (female and male) from a running devcontainer as the HTML
 * fixtures of the HTML-to-PDF tests (src/test/resources/htmlpdf/annual/female.html and male.html).
 *
 * The committed fixtures were derived from the form JSPs by scripts/htmlpdf/derive-annual-fixtures.py,
 * because they were made where no Tomcat or database is available. Run this script in the devcontainer to
 * refresh them from the real server: it logs in, opens the Annual form for two FAKE- demo patients (one
 * female, one male), ticks boxes, types two-line text areas, saves, and writes the page the server
 * redisplays for the saved record, so `checked` and the values are server-rendered attributes, as in the
 * page ConvertToEdoc receives.
 *
 * NOT RUN BY ITS AUTHOR: it follows the flow of scripts/clinical-forms-save-reopen-playwright-checks.js
 * (the Annual Health Review is opened as ../form/formannual.jsp, the Save button asks for a confirmation,
 * and Save redisplays the saved form), but no running application was available to try it against; only the
 * page clean-up (`relativise`) was exercised, in headless Chromium on a sample page.
 * Adjust the selectors below if the form changes.
 *
 * It refuses to write a page that does not name a FAKE- patient, and it never writes a page with a
 * non-FAKE name in a text field.
 *
 * Required environment:
 *   FEMALE_DEMOGRAPHIC_NO=<id of a FAKE- female demo patient> MALE_DEMOGRAPHIC_NO=<id of a FAKE- male demo patient>
 *
 * Optional environment (see scripts/lib/playwright-harness.js readConfig):
 *   BASE_URL=http://127.0.0.1:8080/carlos TEST_USER=carlosdoc TEST_PASSWORD=carlos2026 TEST_PIN=2026
 *   CHROME_PATH=/path/to/chrome   OUTPUT_DIR=src/test/resources/htmlpdf/annual
 *
 * Usage:
 *   node scripts/htmlpdf/capture-annual-fixtures.js
 */
const fs = require('fs');
const path = require('path');
const h = require('../lib/playwright-harness');

const outputDirectory = path.resolve(process.env.OUTPUT_DIR || path.join(__dirname, '../../src/test/resources/htmlpdf/annual'));
const TICKS = 12;
const TEXT = {
  currentConcerns: 'FAKE-concern one: fatigue since August\nFAKE-concern two: knee pain, left side',
  physicalSigns: 'General: well appearing\nChest: clear bilaterally',
  assessment: 'FAKE-assessment line one\nFAKE-assessment line two',
  plan: 'FAKE-plan line one\nFAKE-plan line two (follow up in 3 months)',
};

function requiredEnv(name) {
  const value = process.env[name];
  if (!value) {
    throw new Error(`${name} is required (the id of a FAKE- demo patient)`);
  }
  return value;
}

/**
 * Takes what is not part of a printed form out of the page, and returns the page's markup. It works on the
 * browser's DOM, not with patterns over the markup: the absolute base, the icon and the external scripts are
 * removed, and a stylesheet, form or image link that starts with the application path becomes relative to the
 * webapp root.
 */
async function relativise(page, appPath) {
  const root = appPath.replace(/\/$/, '');
  await page.evaluate((applicationRoot) => {
    for (const node of document.querySelectorAll('base, link[rel="icon"], script[src]')) {
      node.remove();
    }
    const prefix = `${applicationRoot}/`;
    for (const attribute of ['href', 'src', 'action']) {
      for (const element of document.querySelectorAll(`[${attribute}]`)) {
        const value = element.getAttribute(attribute);
        if (value.startsWith(prefix)) {
          element.setAttribute(attribute, value.slice(prefix.length));
        }
      }
    }
  }, root);
  return page.content();
}

async function capture(page, config, demographicNo, kind) {
  const url = `/form/formannual.jsp?demographic_no=${encodeURIComponent(demographicNo)}&formId=0&provNo=999998`;
  await h.gotoApp(page, config.baseUrl, url);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

  const name = await page.locator('input[name="pName"]').inputValue();
  if (!/FAKE-/i.test(name)) {
    throw new Error(`the ${kind} demo patient is not a FAKE- patient; refusing to capture a real name`);
  }

  const boxes = page.locator('input[type="checkbox"]');
  const total = await boxes.count();
  for (let i = 0; i < Math.min(TICKS, total); i += 1) {
    await boxes.nth(i * Math.max(1, Math.floor(total / TICKS))).check().catch(() => {});
  }
  for (const [field, value] of Object.entries(TEXT)) {
    const area = page.locator(`textarea[name="${field}"]`);
    if (await area.count() > 0) {
      await area.fill(value);
    }
  }

  // Save asks for a confirmation, then redisplays the saved form with server-rendered values.
  page.once('dialog', dialog => dialog.accept());
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'load', timeout: 30000 }),
    page.locator('input[type="submit"][onclick*="onSave()"]').first().click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

  const html = await relativise(page, new URL(config.baseUrl).pathname);
  const remaining = [...html.matchAll(/<input[^>]*type="text"[^>]*value="([^"]*)"/gi)].map(m => m[1]).filter(Boolean);
  if (remaining.some(value => /^[A-Z][a-z]+,\s*[A-Z][a-z]+$/.test(value) && !/FAKE-/.test(value))) {
    throw new Error(`the captured ${kind} page holds a name that is not FAKE-; refusing to write it`);
  }
  fs.mkdirSync(outputDirectory, { recursive: true });
  // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- kind is the literal 'female' or 'male' that main() passes; outputDirectory is the developer's own OUTPUT_DIR
  const file = path.join(outputDirectory, `${kind}.html`);
  fs.writeFileSync(file, `<!DOCTYPE html>\n${html.replace(/^<!DOCTYPE[^>]*>\s*/i, '')}\n`, 'utf8');
  console.log(`${file}: ${html.length} characters`);
}

async function main() {
  const female = requiredEnv('FEMALE_DEMOGRAPHIC_NO');
  const male = requiredEnv('MALE_DEMOGRAPHIC_NO');
  const config = h.readConfig();
  const recorder = h.createRecorder();
  const browser = await h.launchBrowser(config);
  try {
    const context = await h.newContext(browser, config);
    const page = await h.login(context, config, recorder, { label: 'capture-annual' });
    await capture(page, config, female, 'female');
    await capture(page, config, male, 'male');
  } finally {
    await browser.close();
  }
}

if (require.main === module) {
  main().catch((error) => {
    console.error(error.message || error);
    process.exitCode = 1;
  });
}

module.exports = { relativise };
