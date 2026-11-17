#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

/*
 * Page-health crawl of the schedule's own navigation, one click deep.
 *
 * User path: login > Schedule > every top-bar / date-bar link a doctor can click
 * (Search, Inbox, the unclaimed-lab "U", Tickler, Msg, Consultations, eDoc, Report,
 * Administration, the provider preferences link, the calendar popup, Today, Month and
 * the first appointment time slot).
 *
 * Asserts, per destination: HTTP < 400, not the error page, no page error / console
 * error / failed same-origin request, no off-host or mixed-content request (aborted in
 * the browser and recorded), sane security headers (lib/page-health-engine.js), and no
 * literal "null", "undefined", "NaN", "???key???", "[object Object]" or unresolved
 * ${...} in the visible text or pre-filled form fields.
 *
 * READ-ONLY. No fixtures, nothing to clean up. The add-appointment slot is opened and
 * closed without submitting.
 *
 * Implements: wave-7 `page-health` sweep (the schedule top bar). The per-surface
 * pages these links lead to are crawled by the other page-health-* checks.
 */

const h = require('./lib/playwright-harness');
const { catalogueLinks, dedupe } = require('./lib/playwright-link-audit');
const { assertHealthy, crawl, startSession } = require('./lib/page-health-engine');

const TOP_BAR_ANCESTORS = ['navlist', 'userSettingsMenu', 'dateAndCalendar'];

async function main() {
  const config = h.readConfig();
  const timeout = Number(process.env.PAGE_HEALTH_TIMEOUT_MS || '20000');
  const session = await startSession(config);
  try {
    const all = dedupe(await catalogueLinks(session.schedulePage, { identity: true }));
    const topBar = all.filter(item => item.identity.ancestorIds.some(id => TOP_BAR_ANCESTORS.includes(id)));
    // One appointment slot is enough: they all share addappointment. Only an EMPTY slot's opener
    // qualifies; a booked appointment's links lead to patient-specific encounter/billing/edit pages.
    // It must also open without a booking prompt: confirmPopupPage(h, w, url, doConfirm, allowDay, allowWeek) asks a
    // confirm()/alert() first for a slot whose template code sets doConfirm (Yes/Onc/Day/Wk), which the engine would
    // report as an unexpected dialog. Only a slot whose doConfirm argument is empty is taken.
    const opensWithoutPrompt = onclick => !/confirmPopupPage\s*\(/.test(onclick)
      || /\/appointment\/addappointment[^']*'\s*,\s*''/.test(onclick);
    const slot = all.find(item => item.identity.ancestorIds.includes('providerSchedule')
      && /\/appointment\/addappointment\b/.test(`${item.identity.onclick || ''} ${item.identity.href || ''}`)
      && opensWithoutPrompt(item.identity.onclick || ''));
    if (!slot) console.log('  no empty, prompt-free appointment slot opener on this day sheet; the add-appointment page is not crawled');
    const items = [...topBar, ...(slot ? [slot] : [])];
    h.assert(items.length >= 10,
      `the schedule offered only ${items.length} top-bar link(s); the catalogue is probably broken`);
    const result = await crawl({
      context: session.context, hostPage: session.schedulePage, items, recorder: session.recorder,
      probe: session.probe, labelPrefix: 'schedule', timeout, ledger: session.ledger,
      skipRules: [{ match: /^(log\s*out|logout)$/i, reason: 'ends the session' }],
    });
    console.log(`  opened ${result.opened.length} schedule link(s), skipped ${result.skipped}`);
    assertHealthy(result, { surface: 'the schedule top bar', minimumOpened: 10 });
  } finally {
    await session.close();
  }
}

if (require.main === module) {
  h.runCheck({ name: 'page-health-schedule-topbar', run: main });
}

module.exports = { main };
