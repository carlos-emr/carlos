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
 * It follows the flow of scripts/clinical-forms-save-reopen-playwright-checks.js: the Annual Health Review is
 * opened at /form/formannual (the route; the .jsp it was written for answers 404), the Save button asks for a
 * confirmation, which the harness has to be told to expect (`withExpectedDialogs`; a page listener of the
 * script's own is a second handler for the same dialog and fails), and Save redisplays the saved form. It was
 * run once, against a packaged install (the .deb files in an Ubuntu 26.04 container) with the demo data of the
 * FAKE- patients, and wrote female.html and male.html with the structure of the committed fixtures. Adjust
 * the selectors below if the form changes.
 *
 * It refuses to write a page that does not name a FAKE- patient, and it never writes a page whose text boxes or
 * text areas hold anything but what it typed itself, a FAKE- value, or a date or number (the server fills in the
 * age and the dates): a record's own text can only come from a real patient.
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
  other: 'FAKE-other finding line one\nFAKE-other finding line two',
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
 * The values, in the saved page, of the text boxes and text areas that nothing explains: not empty, not marked
 * FAKE-, not a date or a number, and not one of the texts this script typed. The page is read from the browser's
 * DOM, so a control is found whatever the order of its attributes. Only their number is ever reported, because a
 * value that nothing explains may be a patient's.
 */
async function unexplainedText(page) {
  const typed = new Set(Object.values(TEXT).map(value => value.trim()));
  const values = await page.evaluate(() => [...document.querySelectorAll('textarea, input:not([type]), input[type="text"]')]
    .map(control => control.value));
  return values
    .map(value => value.replace(/\r\n/g, '\n').trim())
    .filter(value => value !== '' && !/FAKE-/.test(value) && !/^[\d\s/:.-]+$/.test(value) && !typed.has(value));
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
  const url = `/form/formannual?demographic_no=${encodeURIComponent(demographicNo)}&formId=0&provNo=999998`;
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

  // Save asks for a confirmation, then redisplays the saved form with server-rendered values. The harness
  // answers every dialog itself, so the one expected here is declared to it.
  await h.withExpectedDialogs(page, async () => {
    await Promise.all([
      page.waitForNavigation({ waitUntil: 'load', timeout: 30000 }),
      page.locator('input[type="submit"][onclick*="onSave()"]').first().click(),
    ]);
  });
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

  const unexplained = await unexplainedText(page);
  if (unexplained.length > 0) {
    throw new Error(`the captured ${kind} page holds ${unexplained.length} text value(s) that the script did not type and `
      + 'that are not marked FAKE-; refusing to write it');
  }
  const html = await relativise(page, new URL(config.baseUrl).pathname);
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

module.exports = { relativise, unexplainedText };
