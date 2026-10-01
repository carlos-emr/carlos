/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Page-health crawl of the schedule's tool surfaces (Report, Inbox, Tickler, Msg,
 * Consultations, eDoc, ...) using the same entry table the surface audit uses
 * (lib/playwright-surfaces.js): open the surface from the schedule control a user clicks,
 * then every link it offers, judging each destination with lib/page-health-engine.js.
 *
 * One definition so the page-health-<area>-playwright-checks.js scripts stay five lines.
 */

const h = require('./playwright-harness');
const { catalogueLinks, dedupe } = require('./playwright-link-audit');
const { NEVER_OPEN, surfaceByName } = require('./playwright-surfaces');
const { assertHealthy, crawl, createLedger, judgePage, startSession } = require('./page-health-engine');
const { openSurface } = require('../surface-audit-playwright-checks');

/** Open each named surface, crawl what it offers, fail with every finding in one list. */
async function runSurfaceHealth(names, options = {}) {
  const config = h.readConfig();
  const timeout = Number(process.env.PAGE_HEALTH_TIMEOUT_MS || '20000');
  const limit = Number(process.env.PAGE_HEALTH_LIMIT || '0');
  const session = await startSession(config);
  const failures = [];
  const ledger = session.ledger || createLedger();
  let openedTotal = 0;
  try {
    const scheduleUrl = session.schedulePage.url();
    for (const name of names) {
      const surface = surfaceByName(name);
      h.assert(surface, `unknown surface ${name}; see lib/playwright-surfaces.js`);
      let page;
      try {
        page = await openSurface(session.context, session.schedulePage, surface, session.recorder, timeout);
      } catch (error) {
        if (error && error.name === 'SkipCheck') {
          console.log(`  ${name}: not offered here (${error.message})`);
          continue;
        }
        failures.push(`[${name}] ${String(error.message).split('\n')[0]}`);
        continue;
      }
      try {
        await h.assertNotErrorPage(page, surface.title, {});
        await judgePage(ledger, session.probe, `${name} (landing)`, page, {});
        if (surface.controls) {
          console.log(`  ${name}: form surface, landing page judged`);
          continue;
        }
        const items = dedupe(await catalogueLinks(page, surface.scope ? { selector: `${surface.scope} a` } : {}));
        h.assert(items.length >= (surface.minimum || 1),
          `${surface.title} offered ${items.length} link(s); expected at least ${surface.minimum}`);
        const result = await crawl({
          context: session.context, hostPage: page, items, recorder: session.recorder, probe: session.probe,
          labelPrefix: name, timeout, limit, ledger,
          skipRules: [...NEVER_OPEN, ...(surface.skip || []), ...(options.skip || [])],
        });
        console.log(`  ${name}: opened ${result.opened.length}, skipped ${result.skipped}`);
        openedTotal += result.opened.length;
        failures.push(...result.failures.map(line => `[${name}] ${line}`));
      } finally {
        if (page !== session.schedulePage) await page.close().catch(() => {});
        else if (session.schedulePage.url() !== scheduleUrl) {
          await session.schedulePage.goBack({ timeout }).catch(() => {});
        }
      }
    }
    assertHealthy({ failures, ledger, opened: new Array(openedTotal) },
      { surface: names.join(' + '), minimumOpened: options.minimumOpened || 1 });
  } finally {
    await session.close();
  }
}

module.exports = { runSurfaceHealth };
