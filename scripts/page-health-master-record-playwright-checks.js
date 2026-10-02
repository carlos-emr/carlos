#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

/*
 * Page-health crawl of the patient Master Record, one click deep.
 *
 * User path: login > Schedule > Search > FAKE- > patient row (demo patient, read-only) >
 * every link on the Master Record (Appt. History, Billing History, Invoice List,
 * Consultations, Prescriptions, E-Chart, Prevention, Tickler, Documents, eForms, Manage
 * Contacts, Enrollment History, the print/label menu, ...).
 *
 * master-record-tabs proves each one opens cleanly. This check judges the destinations
 * with lib/page-health-engine.js: no literal "null" / "undefined" / "NaN" / "???key???" /
 * "[object Object]" / unresolved ${...} / double-encoded entity in visible text or
 * pre-filled fields, no off-host or mixed-content request (aborted and recorded), the
 * front door's security headers once each on every document, and the click does not end
 * on the login page.
 *
 * READ-ONLY, no fixtures: it uses an existing demo patient and submits nothing. Chart
 * locks taken by the E-Chart link are released as master-record-tabs does.
 *
 * Implements: wave-7 `page-health` sweep (Master Record).
 */

const h = require('./lib/playwright-harness');
const { catalogueLinks, dedupe } = require('./lib/playwright-link-audit');
const { closeBrowserWithChartCleanup, releaseChartLocks } = require('./lib/chart-lock-cleanup');
const { assertHealthy, beginEntry, crawl, judgePage, startSession } = require('./lib/page-health-engine');
const { SKIP_ITEMS, openMasterRecord } = require('./master-record-tabs-playwright-checks');

async function main() {
  const config = h.readConfig();
  const timeout = Number(process.env.PAGE_HEALTH_TIMEOUT_MS || '20000');
  const session = await startSession(config);
  try {
    // Search and the Master Record landing are judged with the crawl (recorder and off-host requests).
    const entry = beginEntry(session);
    const { masterPage } = await openMasterRecord(session.context, session.schedulePage, session.recorder, {
      searchTerm: process.env.MASTER_RECORD_SEARCH || 'FAKE-',
      preferredDemographicNo: process.env.MASTER_RECORD_DEMOGRAPHIC_NO || '2',
      timeout,
    });
    await judgePage(session.ledger, session.probe, 'master-record (landing)', masterPage, {});
    const items = dedupe(await catalogueLinks(masterPage));
    h.assert(items.length > 0, 'The Master Record offered no navigable links at all');
    const result = await crawl({
      context: session.context, hostPage: masterPage, items, recorder: session.recorder, probe: session.probe,
      labelPrefix: 'master-record', timeout, entry: { window: entry, label: 'master-record (entry)' }, skipRules: SKIP_ITEMS, ledger: session.ledger,
      beforePopupClose: page => releaseChartLocks(session.context, config.baseUrl, [page]),
    });
    console.log(`  opened ${result.opened.length} Master Record link(s), skipped ${result.skipped}`);
    assertHealthy(result, { surface: 'the Master Record', minimumOpened: 8 });
  } finally {
    await closeBrowserWithChartCleanup(session.browser, config.baseUrl);
  }
}

if (require.main === module) {
  h.runCheck({ name: 'page-health-master-record', run: main });
}

module.exports = { main };
