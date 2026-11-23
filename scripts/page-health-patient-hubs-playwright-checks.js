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
const { assertHealthy, beginEntry, crawl, entryFailures, judgePage, startSession } = require('./lib/page-health-engine');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');

// Applies to every hub: a destructive control must never be clicked by a read-only crawl.
const DESTRUCTIVE_SKIP = [
  { match: /^(?:del(?:ete)?|remove|discon(?:tinue)?)$/i, reason: 'mutates the patient record' },
];

const HUBS = [
  {
    name: 'Prescriptions',
    skip: [
      { match: /^\*$/, reason: 'setProviderStaleDate changes the provider\'s stale-date preference' },
      { match: /^copy$/i, reason: 'copyFavorite writes a favourite' },
    ],
    minimum: 5,
  },
  // The Ontario bill form links to other pages. The BC form (billingBC.jsp) has only in-page
  // javascript:void(0) anchors (its date pickers and referral-doctor pickers), so on a BC install
  // the hub page itself is judged and there is nothing behind it to crawl.
  { name: 'Create Invoice', skip: [], minimum: 1, minimumByProvince: { BC: 0 } },
  { name: 'Consultations', skip: [], minimum: 1 },
  { name: 'Manage Contacts', skip: [], minimum: 1 },
  { name: 'Preventions', skip: [], minimum: 20, sample: true },
];

/**
 * The fewest links `hub` must offer on an install of `province` (CARLOS_SUITE_PROVINCE: 'ON', 'BC',
 * or '' when the run did not say, which relaxes nothing).
 */
function hubMinimum(hub, province) {
  const known = String(province || '').trim().toUpperCase();
  h.assert(['', 'ON', 'BC'].includes(known), `CARLOS_SUITE_PROVINCE must be ON or BC when it is set; got "${province}"`);
  const forProvince = hub.minimumByProvince || {};
  return Object.hasOwn(forProvince, known) ? forProvince[known] : hub.minimum;
}

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
  // Judged before anything is opened, so a mistyped province is an error of the run, not of a hub.
  const minimums = new Map(HUBS.map(hub => [hub.name, hubMinimum(hub, process.env.CARLOS_SUITE_PROVINCE)]));
  const session = await startSession(config);
  const failures = [];
  let opened = 0;
  try {
    // Opening the Master Record and each hub is judged too: their startup errors and failed resources are reported.
    const masterEntry = beginEntry(session);
    const { masterPage } = await openMasterRecord(session.context, session.schedulePage, session.recorder, {
      searchTerm: process.env.MASTER_RECORD_SEARCH || 'FAKE-',
      preferredDemographicNo: process.env.MASTER_RECORD_DEMOGRAPHIC_NO || '2',
      timeout,
    });
    failures.push(...entryFailures(session, masterEntry, 'master-record (entry)'));
    for (const hub of HUBS) {
      const entry = masterPage.locator('a').filter({ hasText: new RegExp(`^\\s*${hub.name}\\s*$`) }).first();
      h.assert(await entry.count() > 0, `The Master Record offers no "${hub.name}" link`);
      let hubPage;
      let isPopup = true;
      const hubEntry = beginEntry(session);
      try {
        ({ page: hubPage, isPopup } = await ui.clickOpensPopupOrNavigates(masterPage, entry, {
          context: session.context, label: hub.name, recorder: session.recorder, timeout,
        }));
        await judgePage(session.ledger, session.probe, `${hub.name} (hub)`, hubPage, {});
        let items = dedupe(await catalogueLinks(hubPage, { identity: true }));
        const minimum = minimums.get(hub.name);
        h.assert(items.length >= minimum, `${hub.name} offered ${items.length} link(s); expected at least ${minimum}`);
        if (hub.sample && !allPreventions) items = sampleSpread(items, 24);
        const result = await crawl({
          context: session.context, hostPage: hubPage, items, recorder: session.recorder, probe: session.probe,
          labelPrefix: hub.name, timeout, skipRules: [...DESTRUCTIVE_SKIP, ...hub.skip], ledger: session.ledger, tolerateReshuffle: true,
          entry: { window: hubEntry, label: `${hub.name} (entry)` },
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

module.exports = { HUBS, hubMinimum, main, sampleSpread };
