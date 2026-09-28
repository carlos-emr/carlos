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
 * Browser regression check for the eChart's navigation modules: wait for them to
 * load, then open everything they link to and fail on any that is broken.
 *
 * WHY THIS EXISTS. The chart's navigation is 20 modules -- Allergies,
 * Consultations, Documents, Dx Registry, eForms, Forms, HRM, Labs, Measurements,
 * Messenger, Pregnancy, Episode, Contacts, Decision Support, Preventions,
 * Prescriptions, Ticklers, Issues, Resolved Issues, Immunization -- and the suite
 * reached three of them. It is also the part of CARLOS most exposed to the
 * failure this check is for: the modules are injected by navBarLoader() over
 * AJAX into #leftNavBar and #rightNavBar, so a module whose fragment throws
 * leaves a gap in the navigation with no server error anywhere. A clinician sees
 * a chart that is missing a section and has no way to know why.
 *
 * THE FIRST ASSERTION IS THAT THEY LOADED AT ALL. A chart whose navbars come back
 * empty is the most important thing this check can catch, and it is exactly what
 * a check that only opened named modules would miss -- it would find nothing to
 * click and pass.
 *
 * ENTERED THE WAY A CLINICIAN ENTERS IT: login, Search, the patient, the record's
 * E-Chart link. Not an encounter URL.
 *
 * READ-ONLY: it opens each module's destination and closes it, and submits no
 * form. The "+" add controls are deliberately NOT clicked -- those open entry
 * forms that write, and each belongs to a workflow check of its own.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:echart-navbar-modules-playwright
 *
 * Optional environment (the common contract is in lib/playwright-harness.js):
 *   ECHART_NAV_SEARCH=FAKE-          surname prefix to search for
 *   ECHART_NAV_DEMOGRAPHIC_NO=2      prefer this patient. Defaults to 2, not 1:
 *                                    demographic 1's chart answers 500 on the demo
 *                                    dataset (alpha-11 observation 17).
 *   ECHART_NAV_LIMIT=0               stop after N items
 *   ECHART_NAV_TIMEOUT_MS=20000      per-item allowance
 *   ECHART_NAV_SCREENSHOT_DIR=       capture a screenshot of each failing item
 *
 * IMPLEMENTS: coverage plan section 2.5, `echart-navbar-modules`
 * (docs/ui-tests/playwright-coverage-plan-2026.08.md). App defects this check
 * finds are recorded in docs/ui-tests/app-findings-log.md, not worked around.
 */

const {
  assert, assertStrictPage, createRecorder, launchBrowser, login, newContext, readConfig, runCheck,
} = require('./lib/playwright-harness');
const { closeBrowserWithChartCleanup, releaseChartLocks } = require('./lib/chart-lock-cleanup');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { assertAuditClean, auditCatalogue, catalogueLinks, dedupe } = require('./lib/playwright-link-audit');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');

/** The navbar containers newEncounterLayout.jsp renders and navBarLoader() fills. */
const NAVBAR_SELECTOR = '#leftNavBar a, #rightNavBar a';

const SKIP_ITEMS = [
  {
    // The "+" controls open entry forms that write. Each belongs to the workflow
    // check for its module, where the row it creates can be asserted and removed.
    match: /^\s*\+\s*$|^add\b/i,
    reason: 'opens a writing entry form; covered by that module\'s own workflow check',
  },
  {
    match: /^(log\s*out|exit|close|sign)/i,
    reason: 'ends the session or the chart the remaining items need',
  },
];

/** Open the chart from the Master Record's own E-Chart link. */
async function openChart(context, masterPage, recorder, timeout) {
  const chartLink = masterPage.locator('a').filter({ hasText: /^\s*E-?Chart\s*$/i }).first();
  assert(await chartLink.count() > 0,
    'The Master Record offers no E-Chart link, so a clinician cannot open the chart from the patient record');

  // edit.jsp calls popupEChart(), so in practice this is a popup -- but
  // popupEChart reuses a named window, so a chart already open for this patient
  // is navigated in place and no 'page' event fires. Waiting only for the popup
  // would then burn the full timeout and land on the Master Record anyway, which
  // is how a chart check ends up asserting against the patient edit form.
  const { page: chartPage } = await clickOpensPopupOrNavigates(masterPage, chartLink, {
    context, label: 'echart', recorder, timeout,
  });
  return chartPage;
}

/**
 * Wait for navBarLoader() to finish injecting the modules.
 *
 * Asserting this separately is the point: an empty navbar is a real, silent
 * failure, and without this a run would simply find nothing to click and pass.
 */
async function waitForNavbars(chartPage, timeout) {
  await chartPage.locator('#leftNavBar, #rightNavBar').first()
    .waitFor({ state: 'attached', timeout })
    .catch(() => {});
  // Link counts can stay unchanged while a cold module request is still in
  // flight, or forever after that request failed. Observe the actual loader's
  // completion instead. Unrelated note polling does not delay this predicate.
  const settled = await chartPage.waitForFunction(() => {
    const state = window.carlosNavbarLoadState;
    if (!state || !state.scheduled || state.pending !== 0 || !Object.keys(state.modules).length) return null;
    const count = (id) => {
      const element = document.getElementById(id);
      return element ? element.querySelectorAll('a').length : 0;
    };
    return { left: count('leftNavBar'), right: count('rightNavBar'), failed: state.failed };
  }, undefined, { timeout }).then((handle) => handle.jsonValue()).catch(() => null);
  if (!settled) {
    const pending = await chartPage.evaluate(() => {
      const state = window.carlosNavbarLoadState;
      return state ? Object.keys(state.modules).filter(name => state.modules[name].status === 'loading') : ['loader not started'];
    });
    assert(false, `The eChart navigation modules never finished loading: ${pending.join(', ')}`);
  }
  assert(settled.failed.length === 0,
    `The eChart navigation modules failed to load: ${settled.failed.join(', ')}`);
  assert(settled.left > 0 && settled.right > 0,
    'The eChart navigation modules never loaded: #leftNavBar and #rightNavBar do not BOTH contain links after the '
    + 'AJAX load. They are filled from separate module groups, so one empty container is a chart missing half its '
    + 'sections -- no Allergies, Prescriptions, Labs or Preventions where the clinician expects them.');

  return { left: settled.left, right: settled.right };
}

/** Hold one real module request while the others finish, as on a cold server. */
async function openChartWithDelayedNavbar(context, masterPage, recorder, timeout) {
  let release;
  let intercepted = false;
  const gate = new Promise(resolve => { release = resolve; });
  const pattern = '**/encounter/displayPrevention?*';
  const handler = async route => {
    if (!intercepted) {
      intercepted = true;
      await gate;
    }
    await route.continue();
  };
  await context.route(pattern, handler);
  try {
    const chart = await openChart(context, masterPage, recorder, timeout);
    await chart.waitForFunction(() => {
      const state = window.carlosNavbarLoadState;
      return state && state.scheduled && state.modules.preventions?.status === 'loading'
        && Object.entries(state.modules).every(([name, module]) => name === 'preventions' || module.status !== 'loading');
    }, undefined, { timeout });
    assert(intercepted, 'The delayed prevention request was not intercepted');
    assert(await chart.locator('#leftNavBar').getAttribute('aria-busy') === 'true',
      'The chart reported its left navigation ready while Preventions was still pending');
    await chart.locator('#leftColLoader').waitFor({ state: 'visible', timeout });
    let settled = false;
    const waiting = waitForNavbars(chart, timeout);
    waiting.then(() => { settled = true; }, () => { settled = true; });
    // Longer than the former three 250ms stable-count polls. The request is
    // deliberately held, so finishing during this interval is a false pass.
    await new Promise(resolve => setTimeout(resolve, 1000));
    assert(!settled, 'Navbar readiness accepted stable link counts while one module was pending');
    release();
    await waiting;
    assert(await chart.locator('#leftColLoader').count() === 0,
      'The completed navigation retained its loading indicator');
    console.log('  PASS delayed prevention module keeps navigation busy until its actual response');
    return chart;
  } finally {
    release();
    await context.unroute(pattern, handler);
  }
}

async function main() {
  const config = readConfig();
  const searchTerm = process.env.ECHART_NAV_SEARCH || 'FAKE-';
  const preferredDemographicNo = process.env.ECHART_NAV_DEMOGRAPHIC_NO || '2';
  const limit = Number(process.env.ECHART_NAV_LIMIT || '0');
  const timeout = Number(process.env.ECHART_NAV_TIMEOUT_MS || '20000');
  const screenshotDir = process.env.ECHART_NAV_SCREENSHOT_DIR || '';

  const recorder = createRecorder();
  const browser = await launchBrowser(config);
  try {
    const context = await newContext(browser, config);
    const schedulePage = await login(context, config, recorder);
    const { masterPage } = await openMasterRecord(context, schedulePage, recorder, {
      searchTerm, preferredDemographicNo, timeout,
    });
    const chartPage = await openChartWithDelayedNavbar(context, masterPage, recorder, timeout);

    // Chart initialisation is the densest JavaScript in the product and it runs
    // before any item is clicked, so auditCatalogue's per-item snapshots never
    // see it. Assert it here or a pageerror during chart startup is recorded and
    // discarded.
    assertStrictPage(recorder, ['login', 'patient-search', 'master-record', 'echart']);

    const items = dedupe(await catalogueLinks(chartPage, { selector: NAVBAR_SELECTOR }));
    assert(items.length > 0,
      'The eChart navigation loaded but offered no navigable links');

    const result = await auditCatalogue({
      context,
      hostPage: chartPage,
      items,
      recorder,
      labelPrefix: 'echart-nav',
      skipRules: SKIP_ITEMS,
      limit,
      timeout,
      screenshotDir,
      beforePopupClose: page => releaseChartLocks(context, config.baseUrl, [page]),
    });

    console.log(`  opened ${result.opened.length} eChart module link(s), skipped ${result.skipped} by policy`);
    assertAuditClean(result, { surface: 'eChart navigation', minimumOpened: limit ? 1 : 5 });
    return { opened: result.opened.length, skipped: result.skipped };
  } finally {
    await closeBrowserWithChartCleanup(browser, config.baseUrl);
  }
}

if (require.main === module) {
  runCheck({ name: 'echart-navbar-modules', run: main });
}

module.exports = { NAVBAR_SELECTOR, SKIP_ITEMS, main, openChart, waitForNavbars };
