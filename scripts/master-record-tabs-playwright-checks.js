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
 * Browser regression check for the patient Master Record: open everything it
 * links to, by clicking it, and fail on any that is broken.
 *
 * WHY THIS EXISTS. The Master Record is the hub a clinician works from -- Appt.
 * History, Billing History, Invoice List, Consultations, Prescriptions, E-Chart,
 * Prevention, Tickler, the AR forms, Documents, eForms, Manage Contacts, Add
 * Relation, Enrollment History, the print/label menu -- and the suite reached
 * almost none of it. `browser-surface` opened the patient edit page and three
 * others; everything else on the page was unguarded. Like the Administration
 * panel, most of these are popupPage() openers, so a break is an HTTP 200 whose
 * inline script throws and leaves a clinician looking at a dead popup.
 *
 * ENTERED THE WAY A CLINICIAN ENTERS IT: login, the schedule's Search control,
 * a search, the patient row. Not a demographic URL. The search step is itself
 * worth asserting -- it is how every clinician reaches every patient.
 *
 * READ-ONLY: it opens each destination and closes it, submits no form, and seeds
 * nothing. Destinations that would mutate or leave the record are in SKIP_ITEMS.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:master-record-tabs-playwright
 *
 * Optional environment (the common contract is in lib/playwright-harness.js):
 *   MASTER_RECORD_SEARCH=FAKE-        surname prefix to search for
 *   MASTER_RECORD_DEMOGRAPHIC_NO=2    prefer this patient from the results.
 *                                     Defaults to 2, not 1: demographic 1's chart
 *                                     answers 500 on the demo dataset because its
 *                                     HRM rows point at report files that never
 *                                     shipped (alpha-11 observation 17).
 *   MASTER_RECORD_LIMIT=0             stop after N items
 *   MASTER_RECORD_TIMEOUT_MS=20000    per-item allowance
 *   MASTER_RECORD_SCREENSHOT_DIR=     capture a screenshot of each failing item
 *
 * IMPLEMENTS: coverage plan section 2.4, `master-record-tabs`
 * (docs/ui-tests/playwright-coverage-plan-2026.08.md). App defects this check
 * finds are recorded in docs/ui-tests/app-findings-log.md, not worked around.
 */

const {
  assert, createRecorder, launchBrowser, login, newContext, readConfig, runCheck,
} = require('./lib/playwright-harness');
const { clickOpensPopup, clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { assertAuditClean, auditCatalogue, catalogueLinks, dedupe } = require('./lib/playwright-link-audit');

const SKIP_ITEMS = [
  {
    match: /^(log\s*out|exit|close)$/i,
    reason: 'ends the session or the window the remaining items need',
  },
  {
    match: /^(delete|merge)/i,
    reason: 'mutates the patient record; this check is read-only, and the merge flow has its own check',
  },
];

/** Open the schedule's Search control and land on one patient's Master Record. */
async function openMasterRecord(context, schedulePage, recorder, options) {
  const { searchTerm, preferredDemographicNo, timeout } = options;

  // The Search control is conditional in the JSP: a same-tab href to
  // PMmodule/ClientSearch2 with the caisi module loaded, a popupPage2 otherwise
  // (appointmentprovideradminday.jsp, <li id="search">). Assuming either one
  // makes the check wrong on half the deployments, so race them.
  const search = await clickOpensPopupOrNavigates(
    schedulePage,
    schedulePage.locator('#search a').first(),
    {
      context, label: 'patient-search', recorder, timeout,
    },
  );
  const searchPage = search.page;

  await searchPage.locator('#keyword, input[name="keyword"]').first().fill(searchTerm);
  await Promise.all([
    searchPage.waitForLoadState('domcontentloaded').catch(() => {}),
    searchPage.locator("input[type='submit']").first().click({ timeout }),
  ]);
  await searchPage.waitForLoadState('networkidle', { timeout }).catch(() => {});

  // The Master Record control on a result row, as demographicsearchresults.jsp
  // renders it: a[title="Master Demographic File"] whose onclick is
  // popup(800,1200,'DemographicEdit?demographic_no=N'). Matching on the title is
  // what distinguishes it from the E / Rx / T / C badges in the same row, which
  // go to the chart, prescriptions, tickler and consultations instead.
  const results = searchPage.locator('a[title="Master Demographic File"]');
  const resultCount = await results.count();
  assert(resultCount > 0,
    `The patient search for ${JSON.stringify(searchTerm)} returned no rows; set MASTER_RECORD_SEARCH to a surname present in this dataset`);

  // Prefer the configured patient so the run is deterministic across datasets;
  // fall back to the first row rather than failing on a dataset that lacks it.
  let chosen = results.first();
  let chosenNo = '';
  for (let index = 0; index < resultCount; index += 1) {
    const candidate = results.nth(index);
    // A literal pattern, compared numerically: building the regex from the
    // environment value would be a dynamic RegExp, which the repo's Semgrep
    // rules flag (and rightly -- nothing here needs one).
    const found = (await candidate.getAttribute('onclick') || '').match(/demographic_no=(\d+)/);
    if (index === 0 && found) {
      chosenNo = found[1];
    }
    if (found && found[1] === String(preferredDemographicNo)) {
      chosen = candidate;
      chosenNo = found[1];
      break;
    }
  }

  // THE POINT OF THIS FUNCTION. The row's onclick is popup(...), so the Master
  // Record opens in a NEW window. Keeping the search page here would audit the
  // search results and report them as the Master Record -- every item green,
  // nothing of the record actually checked.
  const masterPage = await clickOpensPopup(searchPage, chosen, {
    context, label: 'master-record', recorder, timeout,
  });

  const landed = masterPage.url().match(/demographic_no=(\d+)/);
  assert(landed, `The Master Record popup did not land on a demographic page (${masterPage.url().split('?')[0]})`);
  if (chosenNo) {
    assert(landed[1] === chosenNo,
      `Clicked the row for demographic ${chosenNo} but landed on ${landed[1]}`);
  }

  const body = await masterPage.locator('body').innerText({ timeout }).catch(() => '');
  assert(body.trim().length > 0, 'The Master Record rendered a blank page');
  return { masterPage, searchPage, demographicNo: landed[1] };
}

async function main() {
  const config = readConfig();
  const searchTerm = process.env.MASTER_RECORD_SEARCH || 'FAKE-';
  const preferredDemographicNo = process.env.MASTER_RECORD_DEMOGRAPHIC_NO || '2';
  const limit = Number(process.env.MASTER_RECORD_LIMIT || '0');
  const timeout = Number(process.env.MASTER_RECORD_TIMEOUT_MS || '20000');
  const screenshotDir = process.env.MASTER_RECORD_SCREENSHOT_DIR || '';

  const recorder = createRecorder();
  const browser = await launchBrowser(config);
  try {
    const context = await newContext(browser, config);
    const schedulePage = await login(context, config, recorder);
    const { masterPage } = await openMasterRecord(context, schedulePage, recorder, {
      searchTerm, preferredDemographicNo, timeout,
    });

    const items = dedupe(await catalogueLinks(masterPage));
    assert(items.length > 0, 'The Master Record offered no navigable links at all');

    const result = await auditCatalogue({
      context,
      hostPage: masterPage,
      items,
      recorder,
      labelPrefix: 'master-record',
      skipRules: SKIP_ITEMS,
      limit,
      timeout,
      screenshotDir,
    });

    console.log(`  opened ${result.opened.length} Master Record item(s), skipped ${result.skipped} by policy`);
    assertAuditClean(result, { surface: 'Master Record', minimumOpened: limit ? 1 : 8 });
    return { opened: result.opened.length, skipped: result.skipped };
  } finally {
    await browser.close().catch(() => {});
  }
}

if (require.main === module) {
  runCheck({ name: 'master-record-tabs', run: main });
}

module.exports = { SKIP_ITEMS, main, openMasterRecord };
