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
 * Browser regression check for the Administration panel: open every item it
 * offers, by clicking it, and fail on any that is broken.
 *
 * WHY THIS EXISTS. The Administration panel is the densest collection of pages in
 * CARLOS -- roughly 120 items across User Management, Billing, Labs/Inbox,
 * Forms/eForms, Reports, eChart, CAISI, Schedule Management, Doctor Content
 * Management, System Management, System Reports, Integration, Status and Data
 * Management -- and almost none of them had any browser coverage. Nearly all open
 * as popups through popupPage()/popupOscarRx()/postToPopup(), so the failure mode
 * is not a 500 the server logs: it is a page that answers HTTP 200 whose inline
 * script throws, leaving an administrator on a half-rendered form whose buttons
 * do nothing. That is how "ReferenceError: contextPath is not defined" shipped on
 * the Inbox (issue #3313 item 1) and how the Select Forms panel rendered white
 * (issue #3377) -- both green through every check the suite had.
 *
 * READ-ONLY. It opens each item and closes it again; it submits no form, so it
 * seeds nothing and has nothing to clean up. The three items that would not be
 * read-only are listed in SKIP_ITEMS with the reason.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:admin-index-links-playwright
 *
 * Optional environment (the common contract is in lib/playwright-harness.js):
 *   ADMIN_LINKS_ONLY=billing         only items whose label contains this
 *   ADMIN_LINKS_LIMIT=25             stop after N items, for a quick pass
 *   ADMIN_LINKS_TIMEOUT_MS=20000     per-item allowance
 *   ADMIN_LINKS_SCREENSHOT_DIR=/tmp  capture a screenshot of each failing item
 *
 * IMPLEMENTS: coverage plan section 3.7, `admin-index-links`
 * (docs/ui-tests/playwright-coverage-plan-2026.08.md). App defects this check
 * finds are recorded in docs/ui-tests/app-findings-log.md, not worked around.
 */

const {
  assert, assertStrictPage, createRecorder, launchBrowser, login, newContext, readConfig, runCheck,
} = require('./lib/playwright-harness');
const { clickOpensPopup } = require('./lib/playwright-ui');
const { assertAuditClean, auditCatalogue, catalogueLinks, dedupe } = require('./lib/playwright-link-audit');

/*
 * Items deliberately not opened, each with its reason. Keep this list tiny and
 * justified: every entry is an admin page this check can no longer see break.
 */
const SKIP_ITEMS = [
  {
    match: /update\s*drugref/i,
    reason: 'triggers a 15-60 minute DrugRef rebuild from Health Canada; drugref-update-playwright-checks.js covers this page on its own terms',
  },
  {
    match: /database\/document download/i,
    reason: 'streams a full backup archive of the deployment; belongs in the direct-response contract check',
  },
  {
    match: /^log\s*out$/i,
    reason: 'ends the session the remaining items need',
  },
];

async function main() {
  const config = readConfig();
  const only = (process.env.ADMIN_LINKS_ONLY || '').toLowerCase();
  const limit = Number(process.env.ADMIN_LINKS_LIMIT || '0');
  const timeout = Number(process.env.ADMIN_LINKS_TIMEOUT_MS || '20000');
  const screenshotDir = process.env.ADMIN_LINKS_SCREENSHOT_DIR || '';

  const recorder = createRecorder();
  const browser = await launchBrowser(config);
  try {
    const context = await newContext(browser, config);
    const schedulePage = await login(context, config, recorder);

    // Enter the panel the way an administrator does -- the schedule's own
    // Administration control, never its URL. A broken opener is the first finding
    // this check can make, and it is a real one: with it broken, nothing in
    // Administration is reachable at all.
    const opener = schedulePage.locator('#admin-panel, #admin2').first();
    assert(await opener.count() > 0,
      'The schedule offers no Administration control (#admin-panel / #admin2), so an administrator cannot reach the panel');
    const adminPage = await clickOpensPopup(schedulePage, opener, {
      context, label: 'administration', recorder, timeout,
    });

    // BEFORE the catalogue, because auditCatalogue snapshots per item and
    // measures from the first one onward: a pageerror, a failed resource, a
    // script served as text/html or an unanswered confirm() during the panel's
    // OWN startup is recorded and then never read by anything. Without this the
    // check can report 120 pages opened cleanly while the panel that lists them
    // is itself broken.
    assertStrictPage(recorder, ['login', 'administration']);

    const items = dedupe(await catalogueLinks(adminPage))
      .filter((item) => !only || item.text.toLowerCase().includes(only));
    assert(items.length > 0, 'The Administration panel offered no navigable links at all');

    const result = await auditCatalogue({
      context,
      hostPage: adminPage,
      items,
      recorder,
      labelPrefix: 'admin',
      skipRules: SKIP_ITEMS,
      limit,
      timeout,
      screenshotDir,
    });

    console.log(`  opened ${result.opened.length} Administration item(s), skipped ${result.skipped} by policy`);
    // The floor guards the catalogue step itself: if the selector stopped
    // matching, every item would "pass" by never being opened.
    assertAuditClean(result, { surface: 'Administration', minimumOpened: only || limit ? 1 : 20 });
    return { opened: result.opened.length, skipped: result.skipped };
  } finally {
    await browser.close().catch(() => {});
  }
}

if (require.main === module) {
  runCheck({ name: 'admin-index-links', run: main });
}

module.exports = { SKIP_ITEMS, main };
