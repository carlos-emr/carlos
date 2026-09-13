#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Browser regression check for the Master Record's Print / Labels menu: does
 * each item actually produce a PDF?
 *
 * WHY THIS ONE EXISTS. CLAUDE.md devotes a whole section to direct-response
 * actions, and it was written from real incidents: an action that streams bytes
 * and then returns a named Struts result gets its download replaced by a JSP
 * forward, and one that fails before writing gets `CARLOS Error: 0` because
 * errorpage.jsp has no real HTTP status after result resolution fails (PR #2043).
 * Both failures are HTTP 200 with a Content-Type that is not a PDF. Nothing in
 * this suite looked at the bytes of a generated file, so both were invisible.
 *
 * These are the labels a clinic prints onto a physical chart and an envelope
 * that goes in the post, so "the popup opened" is not the question. The question
 * is whether what came back starts with %PDF.
 *
 * WHAT IT ASSERTS, per item:
 *   1. The menu item exists and clicking it produces a download or a popup --
 *      i.e. the control is wired at all.
 *   2. What comes back is a PDF: the %PDF magic bytes, an application/pdf
 *      content type, a %%EOF trailer, and more than a trivial number of bytes.
 *   3. It is NOT an HTML error page wearing a PDF's filename -- the specific
 *      shape PR #2043 describes, which a content-type check alone can miss when
 *      the container sets the type before the write fails.
 *
 * WHY THE BYTES ARE READ THROUGH THE SESSION AND NOT OUT OF THE RENDERER. A
 * headless Chromium has no PDF viewer, so window.open() on a PDF becomes a
 * download and the check gets no response body; a headed one renders it in a
 * viewer and the check still gets no bytes. The click is what proves the control
 * is wired and is what yields the URL the application itself built; the bytes
 * are then fetched with the same cookies. Nothing here is a typed address.
 *
 * ENTERED THE WAY A CLINIC ENTERS IT: login, Search, the patient's Master
 * Record, the Print / Labels dropdown. The dropdown is itself worth asserting --
 * it is a Bootstrap 5 component, so a broken bundle leaves the button inert and
 * every item below it unreachable.
 *
 * READ-ONLY: generating a label reads the patient and renders it. Nothing is
 * written, and no label is sent to a printer.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:demographic-labels-playwright
 *
 * Optional environment (the common contract is in lib/playwright-harness.js):
 *   LABELS_SEARCH=FAKE-             surname prefix used to reach a patient
 *   LABELS_DEMOGRAPHIC_NO=2         which patient's record to open
 *   LABELS_TIMEOUT_MS=30000         per-item allowance; PDF generation is slow
 *
 * IMPLEMENTS: coverage plan section 2.4, `demographic-labels`
 * (docs/ui-tests/playwright-coverage-plan-2026.08.md). App defects this check
 * finds are recorded in docs/ui-tests/app-findings-log.md, not worked around.
 */

const {
  SkipCheck, assert, assertStrictPage, createRecorder, launchBrowser, login, newContext,
  readConfig, runCheck,
} = require('./lib/playwright-harness');
const { clickDownloadsOrOpens } = require('./lib/playwright-ui');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');

/*
 * The Print / Labels menu, as edit-form-clinical.jsp renders it.
 *
 * `pdf: false` marks the one item that is a settings page rather than a
 * generated file -- it must still open, but asserting %PDF on it would be wrong.
 * `optional` marks items a deployment may legitimately not render.
 */
const MENU_ITEMS = [
  { label: 'PDF Envelope', pdf: true },
  { label: 'PDF Label', pdf: true },
  { label: 'PDF Address Label', pdf: true },
  { label: 'PDF Chart Label', pdf: true },
  {
    label: 'Sexual Health Clinic Label',
    pdf: true,
    optional: 'rendered only where the showSexualHealthLabel property is true',
  },
  // Not a PDF: ViewDemographicLabelPrintSetting is an HTML page the user picks
  // a label layout on. Asserting %PDF here would fail on correct behaviour.
  { label: 'Print Label', pdf: false },
  { label: 'Client Lab Label', pdf: true },
];

/** The smallest thing that could honestly be a one-label PDF. */
const MINIMUM_PDF_BYTES = 400;

const HTML_ERROR = /<html|CARLOS has encountered an unexpected error|HTTP Status \d|Exception Report|CARLOS Error/i;

/** Open the Print / Labels dropdown and prove it opened. */
async function openPrintMenu(masterPage, timeout) {
  const toggle = masterPage.locator('button.dropdown-toggle', { hasText: /Print\s*\/\s*Labels/i }).first();
  assert(await toggle.count() > 0,
    'The Master Record offers no Print / Labels control, so a clinic cannot print a chart label or an envelope at all');
  await toggle.scrollIntoViewIfNeeded().catch(() => {});
  await toggle.click({ timeout });
  const menu = masterPage.locator('ul.dropdown-menu').filter({ hasText: /PDF Chart Label/i }).first();
  // A Bootstrap 5 dropdown is JavaScript: if the bundle failed to load, the
  // button is inert and every item below it is unreachable. That is the defect,
  // and it looks like nothing at all without this assertion.
  await menu.waitFor({ state: 'visible', timeout });
  return menu;
}

/** Everything that makes a response a PDF rather than an error wearing its name. */
function assertIsPdf(item, status, contentType, body) {
  assert(status === 200,
    `${item.label} answered HTTP ${status}. A direct-response action that fails before writing must send a real `
    + 'error status; CLAUDE.md records that returning an unmapped result surfaces as "CARLOS Error: 0" instead.');

  const head = body.subarray(0, Math.min(body.length, 2048)).toString('latin1');
  assert(!HTML_ERROR.test(head),
    `${item.label} returned HTML where a PDF was expected. This is the PR #2043 shape: the action wrote an error `
    + `page into the download. First bytes: ${JSON.stringify(head.slice(0, 120))}`);

  assert(body.subarray(0, 5).toString('latin1') === '%PDF-',
    `${item.label} does not start with the %PDF magic bytes, so it is not a PDF whatever its Content-Type says `
    + `(first bytes: ${JSON.stringify(head.slice(0, 40))})`);

  assert(/application\/pdf/i.test(contentType),
    `${item.label} has the right bytes but Content-Type ${JSON.stringify(contentType)}; a browser will not open it`);

  assert(body.length >= MINIMUM_PDF_BYTES,
    `${item.label} produced only ${body.length} bytes, which is too small to be a rendered label`);

  // A PDF is not complete without its trailer. A stream that was cut off -- by a
  // post-write exception, say -- still starts with %PDF and still has the right
  // Content-Type, and opens as a corrupt file in front of the clinician.
  assert(body.subarray(-1024).toString('latin1').includes('%%EOF'),
    `${item.label} has no %%EOF trailer, so the PDF was truncated: generation started and did not finish`);
}

/** Click one menu item, then read back what it produced. */
async function checkItem(context, masterPage, menu, item, timeout) {
  const entry = menu.locator('a.dropdown-item', { hasText: item.label }).first();
  if (await entry.count() === 0) {
    if (item.optional) {
      throw new SkipCheck(`${item.label} is not offered here (${item.optional})`);
    }
    assert(false, `The Print / Labels menu offers no "${item.label}" item`);
  }

  const produced = await clickDownloadsOrOpens(masterPage, entry, {
    context, label: `label:${item.label}`, timeout,
  });
  try {
    assert(produced.url && !/^about:blank$/i.test(produced.url),
      `${item.label} opened, but to nothing: the control produced no address`);

    if (!item.pdf) {
      // A settings page, not a file. It only has to be a real page.
      assert(produced.kind === 'popup',
        `${item.label} is the label-layout settings page and should open as a page, not download`);
      const text = await produced.page.locator('body').innerText({ timeout }).catch(() => '');
      assert(text.trim().length > 0, `${item.label} opened a blank page`);
      // Rendered TEXT, so the <html> half of HTML_ERROR would match every
      // correct page; only the error markers mean anything here.
      assert(!/CARLOS has encountered an unexpected error|CARLOS Error|Exception Report|HTTP Status \d/i.test(text),
        `${item.label} rendered an error page`);
      return { item: item.label, kind: produced.kind, bytes: 0 };
    }

    // The same session, by cookie, fetching the address the application built.
    const response = await context.request.get(produced.url, { timeout });
    const body = await response.body();
    assertIsPdf(item, response.status(), response.headers()['content-type'] || '', body);
    return { item: item.label, kind: produced.kind, bytes: body.length };
  } finally {
    if (produced.page) {
      await produced.page.close().catch(() => {});
    }
  }
}

async function main() {
  const config = readConfig();
  const searchTerm = process.env.LABELS_SEARCH || 'FAKE-';
  const preferredDemographicNo = process.env.LABELS_DEMOGRAPHIC_NO || '2';
  const timeout = Number(process.env.LABELS_TIMEOUT_MS || '30000');

  const recorder = createRecorder();
  const browser = await launchBrowser(config);
  try {
    const context = await newContext(browser, config);
    const schedulePage = await login(context, config, recorder);
    const { masterPage } = await openMasterRecord(context, schedulePage, recorder, {
      searchTerm, preferredDemographicNo, timeout,
    });

    const menu = await openPrintMenu(masterPage, timeout);

    const generated = [];
    const skipped = [];
    const failures = [];
    for (const item of MENU_ITEMS) {
      try {
        generated.push(await checkItem(context, masterPage, menu, item, timeout));
      } catch (error) {
        if (error instanceof SkipCheck) {
          skipped.push(error.message);
          continue;
        }
        // Collected rather than thrown: one broken label should not hide the
        // state of the other six, and a clinic needs to know which ones work.
        failures.push(String(error.message).split('\n')[0]);
      }
      // The dropdown closes when an item is chosen.
      await openPrintMenu(masterPage, timeout);
    }

    assertStrictPage(recorder, ['patient-search', 'master-record']);
    assert(failures.length === 0,
      `${failures.length} Print / Labels item(s) do not produce what they promise:\n    - ${failures.join('\n    - ')}`);
    assert(generated.filter((entry) => entry.bytes > 0).length >= 3,
      `Only ${generated.filter((entry) => entry.bytes > 0).length} item(s) produced a PDF; this menu should offer at least three, `
      + 'so the menu itself is probably not rendering rather than the labels being broken');

    for (const line of skipped) {
      console.log(`  skipped ${line}`);
    }
    console.log(`  generated ${generated.length} Print / Labels item(s)`);
    return { generated, skipped };
  } finally {
    await browser.close().catch(() => {});
  }
}

if (require.main === module) {
  runCheck({ name: 'demographic-labels', run: main });
}

module.exports = {
  HTML_ERROR, MENU_ITEMS, MINIMUM_PDF_BYTES, assertIsPdf, main, openPrintMenu,
};
