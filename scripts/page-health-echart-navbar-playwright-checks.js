#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

/*
 * Page-health crawl of the E-Chart navigation modules, one click deep.
 *
 * User path: login > Schedule > Search > FAKE- > patient row > Master Record > E-Chart >
 * every link in the left and right navigation bars (Allergies, Preventions, Rx, Labs,
 * Documents, eForms, Measurements, Forms, Consultations, Tickler, ... the "+" entry
 * forms and anything that closes the chart are skipped as echart-navbar-modules does).
 *
 * echart-navbar-modules proves each module link opens without an error. This check
 * judges the destinations with lib/page-health-engine.js: no literal "null" / "undefined"
 * / "NaN" / "???key???" / "[object Object]" / unresolved ${...} / double-encoded entity
 * in visible text or pre-filled fields, no off-host or mixed-content request (aborted and
 * recorded), the front door's security headers once each on every document.
 *
 * READ-ONLY, no fixtures: an existing demo patient, nothing submitted; chart locks are
 * released after each destination as the existing audit does.
 *
 * Implements: wave-7 `page-health` sweep (E-Chart modules: Rx, eForms, billing links).
 */

const h = require('./lib/playwright-harness');
const { catalogueLinks, dedupe } = require('./lib/playwright-link-audit');
const { closeBrowserWithChartCleanup, releaseChartLocks } = require('./lib/chart-lock-cleanup');
const { assertHealthy, crawl, judgePage, startSession } = require('./lib/page-health-engine');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');
const { NAVBAR_SELECTOR, SKIP_ITEMS, openChart, waitForNavbars } = require('./echart-navbar-modules-playwright-checks');

async function main() {
  const config = h.readConfig();
  const timeout = Number(process.env.PAGE_HEALTH_TIMEOUT_MS || '20000');
  const session = await startSession(config);
  try {
    const { masterPage } = await openMasterRecord(session.context, session.schedulePage, session.recorder, {
      searchTerm: process.env.ECHART_NAV_SEARCH || 'FAKE-',
      preferredDemographicNo: process.env.ECHART_NAV_DEMOGRAPHIC_NO || '2',
      timeout,
    });
    const chartPage = await openChart(session.context, masterPage, session.recorder, timeout);
    await waitForNavbars(chartPage, timeout);
    await judgePage(session.ledger, session.probe, 'echart (landing)', chartPage, {});
    const items = dedupe(await catalogueLinks(chartPage, { selector: NAVBAR_SELECTOR, identity: true }));
    h.assert(items.length > 0, 'The E-Chart navigation loaded but offered no navigable links');
    const result = await crawl({
      context: session.context, hostPage: chartPage, items, recorder: session.recorder, probe: session.probe,
      labelPrefix: 'echart-nav', timeout, tolerateReshuffle: true, skipRules: SKIP_ITEMS, ledger: session.ledger,
      beforeItem: () => waitForNavbars(chartPage, timeout),
      beforePopupClose: page => releaseChartLocks(session.context, config.baseUrl, [page]),
    });
    console.log(`  opened ${result.opened.length} E-Chart module link(s), skipped ${result.skipped}`);
    assertHealthy(result, { surface: 'the E-Chart navigation', minimumOpened: 5 });
  } finally {
    await closeBrowserWithChartCleanup(session.browser, config.baseUrl);
  }
}

if (require.main === module) {
  h.runCheck({ name: 'page-health-echart-navbar', run: main });
}

module.exports = { main };
