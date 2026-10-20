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
 * Issue #3944 against a running CARLOS: a legacy eForm looks and prints the same inside CARLOS as
 * it does on its own.
 *
 * The eForm renderers used to add the whole Bootstrap stylesheet to every eForm, and the PDF render
 * surface did the same. Bootstrap's reboot moved legacy fields and resized text on screen, in the
 * browser's print and in every downloaded, faxed or filed PDF. The standalone
 * eform-bootstrap-isolation-playwright-checks.js proves the JSPs' asset lists; this check proves the
 * page the application actually serves and the PDF it actually renders:
 *
 *   1. the add page (efmformadd_data) links no Bootstrap stylesheet but the scoped copy, and loads
 *      the Bootstrap bundle once;
 *   2. every measured element of the eForm has the same box and computed style as the bare form,
 *      on screen and in print media; the toolbar and the save alert are still Bootstrap-styled;
 *   3. in the PDF the toolbar's Download produces, each marker word starts where the bare form lays
 *      it out (the renderer prints #page1 as the page, with zero body margin), within a point.
 *      With Bootstrap on the render surface the flow text moved up, the text after a padded input
 *      moved left and the second cell of a table classed "table" moved hundreds of points.
 *
 * FIXTURE. The template is inserted with a FAKE-PW run-marker name and the form is opened for, and
 * saved by the download for, an owned FAKE patient (lib/owned-patient.js). Cleanup deletes the
 * instance, its values and attachment rows by the patient's key, the template by its unique name,
 * and the patient, and asserts each gone. Requires pdftotext (poppler-utils) and MYSQL_*.
 *
 *   npm run test:eform-bootstrap-print-fidelity-playwright
 *
 * Optional environment (common ones: see lib/playwright-harness.js readConfig()):
 *   EFORM_SCREENSHOT_DIR  keep a full-page screenshot and the downloaded PDF here
 */
'use strict';

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const h = require('./lib/playwright-harness');
const { createOwnedPatient, eformRows, newOwnedMarker, removeOwnedPatient } = require('./lib/owned-patient');
const fixture = require('./lib/eform-legacy-layout-fixture');

/** Chromium prints 96 CSS pixels to the inch, PDF has 72 points: one pixel is 0.75 pt. */
const PT_PER_PX = 0.75;
/** Text positions from pdftotext and from the layout agree well within this. */
const X_TOLERANCE_PT = 1;
/** pdftotext's word box top is font-metric based, the browser's is line-box based. */
const Y_TOLERANCE_PT = 3;
const artifactDir = process.env.EFORM_SCREENSHOT_DIR || '';

/** The bare form, laid out with nothing CARLOS adds, in the given media. */
async function bareLayout(context, media, { zeroBodyMargin = false } = {}) {
  const page = await context.newPage();
  try {
    await page.emulateMedia({ media });
    // nosemgrep: javascript.playwright.security.audit.playwright-setcontent-injection.playwright-setcontent-injection -- the fixed fixture template from lib/eform-legacy-layout-fixture.js
    await page.setContent(fixture.legacyEformHtml(), { waitUntil: 'load' });
    // The PDF renderer zeroes the html and body margins itself (EFormBrowserPdfService
    // PREPARE_PRINT_JS) so a scanned page prints at the paper's edge; mirror that for the PDF.
    if (zeroBodyMargin) {
      await page.evaluate(() => { document.body.style.margin = '0'; document.documentElement.style.margin = '0'; });
    }
    return { geometry: await fixture.measureEform(page), markers: await fixture.markerOffsets(page) };
  } finally {
    await page.close();
  }
}

/** Computed values of the named CSS properties on the first element matching `selector`. */
async function computed(page, selector, properties) {
  // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- properties is always a literal CSS property-name list from this script
  return page.locator(selector).first().evaluate((element, names) => {
    const style = getComputedStyle(element);
    return Object.fromEntries(names.map((name) => [name, style[name]]));
  }, properties);
}

/** Word boxes from the first PDF page, keyed by word, in points from the page's top-left corner. */
function pdfWords(pdfPath) {
  const bbox = execFileSync('pdftotext', ['-bbox', '-f', '1', '-l', '1', pdfPath, '-'], { encoding: 'utf8' });
  const words = {};
  for (const match of bbox.matchAll(/<word xMin="([\d.]+)" yMin="([\d.]+)" xMax="[\d.]+" yMax="[\d.]+">([^<]*)<\/word>/g)) {
    if (!words[match[3]]) words[match[3]] = { x: Number(match[1]), y: Number(match[2]) };
  }
  return words;
}

/**
 * Opens the seeded legacy eForm for the owned patient through the installed renderer and
 * compares it with the bare form on screen, in print and in the server-rendered PDF.
 */
async function workflow({ config, context, recorder, fid, patient }) {
  const page = await context.newPage();
  h.wirePage(page, 'eform', recorder);
  await h.gotoApp(page, config.baseUrl,
    `/eform/efmformadd_data?fid=${encodeURIComponent(fid)}&demographic_no=${encodeURIComponent(patient)}`);
  await h.assertNotErrorPage(page, 'eForm add page');
  await page.locator('#toolbarWrapper #remoteSubmitButton').waitFor({ state: 'visible', timeout: 30000 });

  // 1. What the renderer put on the page.
  const assets = await page.evaluate(() => ({
    stylesheets: [...document.querySelectorAll('link[rel~="stylesheet"]')].map((link) => new URL(link.href).pathname),
    bundles: [...document.querySelectorAll('script[src]')].filter((script) => /\/bootstrap\.bundle(\.min)?\.js$/.test(script.src)).length,
  }));
  h.assert(!assets.stylesheets.some((href) => href.includes('/library/bootstrap/')),
    `the eForm page links a global Bootstrap stylesheet: ${assets.stylesheets.join(', ')}`);
  h.assert(assets.stylesheets.some((href) => href.endsWith('/eform/eformFloatingToolbar/bootstrap-eform-scoped.min.css')),
    `the eForm page does not link the scoped Bootstrap copy: ${assets.stylesheets.join(', ')}`);
  h.assert(assets.bundles === 1, `the eForm page loads the Bootstrap bundle ${assets.bundles} times`);

  // 2. The eForm itself, against the bare form, on screen and in print.
  for (const media of ['screen', 'print']) {
    await page.emulateMedia({ media });
    const expected = (await bareLayout(context, media)).geometry;
    const changed = fixture.differences(expected, await fixture.measureEform(page));
    h.assert(changed.length === 0, `the served eForm differs from the bare form in ${media}:\n  ${changed.join('\n  ')}`);
  }
  await page.emulateMedia({ media: 'screen' });
  const save = await computed(page, '#remoteSubmitButton', ['backgroundColor', 'color']);
  h.assert(save.backgroundColor === 'rgb(13, 110, 253)' && save.color === 'rgb(255, 255, 255)',
    `the toolbar Save button is not Bootstrap-styled: ${JSON.stringify(save)}`);
  await page.evaluate(() => showErrorAlert.call(document.getElementById('legacyForm')));
  await page.waitForFunction(() => {
    const alert = document.getElementById('submit-error-alert');
    return alert && getComputedStyle(alert).opacity === '1';
  }, null, { timeout: 10000 });
  const alert = await computed(page, '#submit-error-alert', ['position', 'backgroundColor']);
  h.assert(alert.position === 'fixed' && alert.backgroundColor === 'rgb(248, 215, 218)',
    `the save alert is not a floating Bootstrap alert: ${JSON.stringify(alert)}`);
  if (artifactDir) await h.screenshot(page, artifactDir, 'eform-bootstrap-print-fidelity');
  await page.locator('#submit-error-alert .btn-close').click();

  // 3. The PDF the toolbar's Download renders, against the bare form as the renderer lays it out.
  await page.locator('#remote_eform_subject').fill(`Layout fidelity ${patient}`);
  const downloaded = page.waitForEvent('download', { timeout: 120000 });
  await page.locator('#remoteDownloadButton').click();
  const download = await downloaded;
  const pdfDir = artifactDir || fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-eform-fidelity-'));
  const pdfPath = h.buildArtifactPath(pdfDir, 'eform-bootstrap-print-fidelity', '.pdf');
  try {
    await download.saveAs(pdfPath);
    h.assert(fs.readFileSync(pdfPath).subarray(0, 5).toString() === '%PDF-', 'the download is not a PDF');
    const words = pdfWords(pdfPath);
    const expected = (await bareLayout(context, 'print', { zeroBodyMargin: true })).markers;
    const misplaced = [];
    for (const [word, meaning] of Object.entries(fixture.MARKERS)) {
      h.assert(expected[word], `the bare form has no ${word}`);
      h.assert(words[word], `the PDF has no ${word} (${meaning}); words found: ${Object.keys(words).join(' ')}`);
      const want = { x: expected[word].x * PT_PER_PX, y: expected[word].y * PT_PER_PX };
      const dx = words[word].x - want.x;
      const dy = words[word].y - want.y;
      if (Math.abs(dx) > X_TOLERANCE_PT || Math.abs(dy) > Y_TOLERANCE_PT) {
        misplaced.push(`${word} (${meaning}): at ${words[word].x.toFixed(2)},${words[word].y.toFixed(2)} pt,`
          + ` expected ${want.x.toFixed(2)},${want.y.toFixed(2)} pt`);
      }
    }
    h.assert(misplaced.length === 0, `the PDF moved the eForm's content:\n  ${misplaced.join('\n  ')}`);
  } finally {
    if (!artifactDir) fs.rmSync(pdfDir, { recursive: true, force: true });
  }
  await page.close();
}

/**
 * Seeds an owned FAKE patient and eForm template, runs the workflow, and removes exactly the
 * rows this run created (asserting each is gone), including after a failure.
 */
async function main() {
  const recorder = h.createRecorder();
  const config = h.readConfig();
  const sql = h.createSqlRunner(config.mysql);
  const marker = newOwnedMarker();
  const formName = `${marker} legacy layout fidelity`;
  let ownedRows = null;
  let patient = null;
  let browser = null;
  await h.runCheck({
    name: 'eform-bootstrap-print-fidelity',
    async run() {
      // Taken before anything is saved, so only this run's attachment rows are ever deleted.
      ownedRows = eformRows(sql);
      const provider = sql.value(`SELECT provider_no FROM security WHERE user_name=${h.sqlString(config.testUser)}`);
      h.assert(provider, 'the configured test login has no provider');
      patient = createOwnedPatient(sql, { marker, provider });
      const fid = h.insertId(sql, `INSERT INTO eform (form_name, file_name, subject, form_date, form_time,
          form_creator, status, form_html, showLatestFormOnly, patient_independent, roleType)
        VALUES (${h.sqlString(formName)}, 'legacy-layout-fixture.html', 'Legacy layout fixture', CURDATE(), CURTIME(),
          ${h.sqlString(config.testUser)}, 1, ${h.sqlString(fixture.legacyEformHtml())}, 0, 0, '')`, 'eForm template');
      browser = await h.launchBrowser(config);
      const context = await h.newContext(browser, config, { acceptDownloads: true, viewport: { width: 1100, height: 900 } });
      context.setDefaultTimeout(30000);
      await (await h.login(context, config, recorder)).close();
      await workflow({ config, context, recorder, fid, patient });
    },
    async cleanup() {
      if (browser) await browser.close();
      try {
        sql.execute(`DELETE FROM eform WHERE form_name=${h.sqlString(formName)}`);
        h.assert(sql.value(`SELECT COUNT(*) FROM eform WHERE form_name=${h.sqlString(formName)}`) === '0',
          'the eForm template this run inserted was not removed');
        if (patient) removeOwnedPatient(sql, patient, marker, ownedRows);
      } finally {
        sql.dispose();
      }
    },
  });
}

if (require.main === module) main();
module.exports = { pdfWords };
