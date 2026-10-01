#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

/*
 * Page-health crawl of the hub pages a clinician reaches from the Master Record, one
 * click deeper than page-health-master-record: the Prescriptions (Rx) page, the
 * Preventions index and its add-prevention forms, Create Invoice (the billing form),
 * Consultations and Manage Contacts.
 *
 * User path: login > Schedule > Search > FAKE- > patient row > Master Record >
 * <hub link> > every link that hub page offers. Links that save or change something
 * (provider stale date "*", favourite "copy", delete/remove/discontinue) are skipped
 * and named below; the add-prevention forms are only opened, never submitted.
 *
 * Asserts, per destination (lib/page-health-engine.js): HTTP < 400, not the error page,
 * no page error / console error / failed same-origin request, no off-host or
 * mixed-content request (aborted and recorded), the front door's security headers once
 * each, and no literal "null" / "undefined" / "NaN" / "???key???" / "[object Object]" /
 * unresolved ${...} / double-encoded entity in visible text or pre-filled fields.
 *
 * READ-ONLY on an existing demo patient; no fixtures. PAGE_HEALTH_PREVENTIONS=all opens
 * every prevention form (about 150, ~8 minutes); the default samples 24 spread across
 * the list.
 *
 * Implements: wave-7 `page-health` sweep (Rx, billing form, preventions, consultations).
 */

const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { catalogueLinks, dedupe } = require('./lib/playwright-link-audit');
const { closeBrowserWithChartCleanup, releaseChartLocks } = require('./lib/chart-lock-cleanup');
const { assertHealthy, crawl, judgePage, startSession } = require('./lib/page-health-engine');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');

const HUBS = [
  {
    name: 'Prescriptions',
    skip: [
      { match: /^\*$/, reason: 'setProviderStaleDate changes the provider\'s stale-date preference' },
      { match: /^copy$/i, reason: 'copyFavorite writes a favourite' },
    ],
    minimum: 5,
  },
  { name: 'Create Invoice', skip: [], minimum: 1 },
  { name: 'Consultations', skip: [], minimum: 1 },
  { name: 'Manage Contacts', skip: [], minimum: 1 },
  { name: 'Preventions', skip: [], minimum: 20, sample: true },
];

/** Evenly spread subset of `items`, always including the first and the last. */
function sampleSpread(items, count) {
  if (items.length <= count) return items;
  const picked = new Set();
  for (let i = 0; i < count; i += 1) picked.add(Math.round((i * (items.length - 1)) / (count - 1)));
  return items.filter((_, index) => picked.has(index));
}

async function main() {
  const config = h.readConfig();
  const timeout = Number(process.env.PAGE_HEALTH_TIMEOUT_MS || '20000');
  const allPreventions = process.env.PAGE_HEALTH_PREVENTIONS === 'all';
  const session = await startSession(config);
  const failures = [];
  let opened = 0;
  try {
    const { masterPage } = await openMasterRecord(session.context, session.schedulePage, session.recorder, {
      searchTerm: process.env.MASTER_RECORD_SEARCH || 'FAKE-',
      preferredDemographicNo: process.env.MASTER_RECORD_DEMOGRAPHIC_NO || '2',
      timeout,
    });
    for (const hub of HUBS) {
      const entry = masterPage.locator('a').filter({ hasText: new RegExp(`^\\s*${hub.name}\\s*$`) }).first();
      h.assert(await entry.count() > 0, `The Master Record offers no "${hub.name}" link`);
      let hubPage;
      let isPopup = true;
      try {
        ({ page: hubPage, isPopup } = await ui.clickOpensPopupOrNavigates(masterPage, entry, {
          context: session.context, label: hub.name, recorder: session.recorder, timeout,
        }));
        await judgePage(session.ledger, session.probe, `${hub.name} (hub)`, hubPage, {});
        let items = dedupe(await catalogueLinks(hubPage, { identity: true }));
        h.assert(items.length >= hub.minimum, `${hub.name} offered ${items.length} link(s); expected at least ${hub.minimum}`);
        if (hub.sample && !allPreventions) items = sampleSpread(items, 24);
        const result = await crawl({
          context: session.context, hostPage: hubPage, items, recorder: session.recorder, probe: session.probe,
          labelPrefix: hub.name, timeout, skipRules: hub.skip, ledger: session.ledger, tolerateReshuffle: true,
          beforePopupClose: page => releaseChartLocks(session.context, config.baseUrl, [page]),
        });
        console.log(`  ${hub.name}: opened ${result.opened.length}, skipped ${result.skipped}`);
        opened += result.opened.length;
        failures.push(...result.failures.map(line => `[${hub.name}] ${line}`));
      } finally {
        if (hubPage && isPopup) await hubPage.close().catch(() => {});
        else if (hubPage) await masterPage.goBack({ timeout }).catch(() => {});
      }
    }
    assertHealthy({ failures, ledger: session.ledger, opened: new Array(opened) },
      { surface: 'the Master Record hub pages', minimumOpened: 8 });
  } finally {
    await closeBrowserWithChartCleanup(session.browser, config.baseUrl);
  }
}

if (require.main === module) {
  h.runCheck({ name: 'page-health-patient-hubs', run: main });
}

module.exports = { HUBS, main, sampleSpread };
