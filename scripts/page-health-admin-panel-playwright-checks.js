#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

/*
 * Page-health crawl of the Administration panel, one click deep.
 *
 * User path: login > Schedule > Administration > every item the panel offers
 * (User Management, Billing, Labs/Inbox, Forms/eForms, Reports, eChart, CAISI, Schedule
 * Management, Doctor Content, System Management, System Reports, Integration, Status and
 * Data Management).
 *
 * admin-index-links already proves each item OPENS without an error page, page error,
 * console error or failed request. This check asks the other questions of the same
 * destinations (lib/page-health-engine.js): is any visible text or pre-filled field a
 * rendering accident ("null", "undefined", "NaN", "???key???", "[object Object]",
 * "${...}", a double-encoded entity, a leaked Java class name), did the page ask for an
 * off-host or mixed-content resource (aborted, recorded), and are the front door's
 * security headers present exactly once on every document.
 *
 * READ-ONLY, no fixtures. The same three items admin-index-links refuses to open are
 * skipped here for the same reasons.
 *
 * Optional environment: ADMIN_LINKS_ONLY=text, ADMIN_LINKS_LIMIT=N, PAGE_HEALTH_TIMEOUT_MS,
 * PAGE_HEALTH_SHARD=1/2|2/2 (the panel has ~108 items and a full pass takes about nine minutes).
 *
 * Implements: wave-7 `page-health` sweep (Administration panel).
 */

const h = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { catalogueLinks, dedupe } = require('./lib/playwright-link-audit');
const { assertHealthy, beginEntry, crawl, judgePage, startSession } = require('./lib/page-health-engine');
const { SKIP_ITEMS } = require('./admin-index-links-playwright-checks');

async function main() {
  const config = h.readConfig();
  const only = (process.env.ADMIN_LINKS_ONLY || '').toLowerCase();
  const limit = Number(process.env.ADMIN_LINKS_LIMIT || '0');
  const timeout = Number(process.env.PAGE_HEALTH_TIMEOUT_MS || '20000');
  const session = await startSession(config);
  try {
    const opener = session.schedulePage.locator('#admin-panel, #admin2').first();
    h.assert(await opener.count() > 0, 'The schedule offers no Administration control (#admin-panel / #admin2)');
    // Opening the shell is judged too: its startup errors and failed resources are reported with the crawl's.
    const entry = beginEntry(session);
    const { page: adminPage } = await clickOpensPopupOrNavigates(session.schedulePage, opener, {
      context: session.context, label: 'administration', recorder: session.recorder, timeout,
    });
    await judgePage(session.ledger, session.probe, 'administration (shell)', adminPage, {});
    // PAGE_HEALTH_SHARD=1/2 or 2/2 splits the ~108 items so each half fits a shared live slot.
    const [shard, shards] = (process.env.PAGE_HEALTH_SHARD || '1/1').split('/').map(Number);
    h.assert(shard >= 1 && shard <= shards, `PAGE_HEALTH_SHARD must look like 1/2, got ${process.env.PAGE_HEALTH_SHARD}`);
    const items = dedupe(await catalogueLinks(adminPage))
      .filter(item => !only || item.text.toLowerCase().includes(only))
      .filter((item, position) => position % shards === shard - 1);
    h.assert(items.length > 0, 'The Administration panel offered no navigable links at all');
    const result = await crawl({
      context: session.context, hostPage: adminPage, items, recorder: session.recorder, probe: session.probe,
      labelPrefix: 'admin', inPlaceTarget: '#dynamic-content', skipRules: SKIP_ITEMS, limit, timeout,
      ledger: session.ledger, entry: { window: entry, label: 'administration (entry)' },
      // Filed: Unlock Account answers 500 whenever any login is tracked (ISSUES.md L1,
      // app-findings-log 71). Only that 500 signature is counted, not failed a second time: an
      // uncaught error, another failed request or a text finding on the same link still fails.
      knownFailures: [{
        match: /^admin:Unlock Account: (?:HTTP 500\b|console error: Failed to load resource: the server responded with a status of 500|rendered an error page)/,
        // The 500 page's own text (exception names read through the panel iframe) is the same filed defect.
        page: /^admin:Unlock Account$/,
        reason: 'Unlock Account 500, ISSUES.md L1 / finding 71',
      }],
    });
    console.log(`  opened ${result.opened.length} Administration item(s), skipped ${result.skipped}`);
    assertHealthy(result, { surface: 'Administration', minimumOpened: only || limit || shards > 1 ? 1 : 20 });
  } finally {
    await session.close();
  }
}

if (require.main === module) {
  h.runCheck({ name: 'page-health-admin-panel', run: main });
}

module.exports = { main };
