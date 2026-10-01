#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

/*
 * The About / License footer links on Master Record sub-pages must open their popups.
 *
 * User path: login > Schedule > Search > FAKE- > patient row > Master Record >
 *   (1) Manage Contacts > footer "About" and "License";
 *   (2) Preventions > the first prevention type (the add-prevention form) > footer
 *       "About" and "License".
 *
 * Roughly forty pages end in
 *   <a href="javascript:popupStart(300,400,'.../encounter/ViewAbout')">About</a> | ...License
 * but popupStart is defined only by encounter/js/encounter.js, oscarMDSIndex.js and a
 * few pages' own scripts. On the rest the click throws "ReferenceError: popupStart is
 * not defined" and nothing opens. Asserts: each click opens a popup that is not an error
 * page, with no uncaught error recorded. FAILS today at step 1 (Manage Contacts).
 *
 * READ-ONLY on an existing demo patient, no fixtures, nothing submitted.
 *
 * Implements: wave-7 `page-health` sweep (footer links).
 */

const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { closeBrowserWithChartCleanup } = require('./lib/chart-lock-cleanup');
const { beginEntry, createLedger, entryFailures, startSession } = require('./lib/page-health-engine');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');

async function openHub(session, masterPage, name, timeout) {
  const entry = masterPage.locator('a').filter({ hasText: new RegExp(`^\\s*${name}\\s*$`) }).first();
  h.assert(await entry.count() > 0, `The Master Record offers no "${name}" link`);
  const { page } = await ui.clickOpensPopupOrNavigates(masterPage, entry, {
    context: session.context, label: name, recorder: session.recorder, timeout,
  });
  return page;
}

/** Click one footer link and require a healthy popup; returns the findings. */
async function footerLink(session, page, text, timeout, reported) {
  const link = page.locator('a[href^="javascript:"]').filter({ hasText: new RegExp(`^\\s*${text}\\s*$`) }).first();
  if (await link.count() === 0) return [`no "${text}" footer link on ${page.url().split('?')[0].split('/').pop()}`];
  const problems = [];
  const before = session.recorder.pageErrors.length;
  try {
    const popup = await ui.clickOpensPopup(page, link, {
      context: session.context, label: `footer ${text}`, recorder: session.recorder, timeout,
    });
    await popup.close().catch(() => {});
  } catch (error) {
    problems.push(`"${text}" footer link on ${page.url().split('?')[0].split('/').pop()} opened no popup`);
  }
  for (const entry of session.recorder.pageErrors.slice(before)) {
    problems.push(`"${text}" footer link threw: ${entry.text.split('\n')[0]}`);
    reported.push(`uncaught ${entry.text.split('\n')[0]}`);
  }
  return problems;
}

async function main() {
  const config = h.readConfig();
  const timeout = Number(process.env.PAGE_HEALTH_TIMEOUT_MS || '20000');
  const session = await startSession(config);
  const problems = [];
  // Footer ReferenceErrors are reported per link above; everything else the browser recorded
  // while getting here and clicking is asserted at the end.
  const reported = [];
  const ledger = createLedger();
  const entry = beginEntry(session);
  try {
    const { masterPage } = await openMasterRecord(session.context, session.schedulePage, session.recorder, {
      searchTerm: 'FAKE-', preferredDemographicNo: '2', timeout,
    });

    const contacts = await openHub(session, masterPage, 'Manage Contacts', timeout);
    for (const text of ['About', 'License']) problems.push(...await footerLink(session, contacts, text, timeout, reported));
    await contacts.close().catch(() => {});
    console.log(`  step page-health-footer-links: Manage Contacts footer links clicked, ${problems.length} problem(s) recorded`);

    const preventions = await openHub(session, masterPage, 'Preventions', timeout);
    const first = preventions.locator('a[onclick*="ViewAddPreventionData"], a[href*="ViewAddPreventionData"]').first();
    h.assert(await first.count() > 0, 'The Preventions index offers no add-prevention link');
    const form = await ui.clickOpensPopup(preventions, first, {
      context: session.context, label: 'add prevention', recorder: session.recorder, timeout,
    });
    for (const text of ['About', 'License']) problems.push(...await footerLink(session, form, text, timeout, reported));
    await form.close().catch(() => {});
    await preventions.close().catch(() => {});
    console.log(`  step page-health-footer-links: add-prevention form footer links clicked, ${problems.length} problem(s) recorded`);

    problems.push(...entryFailures({ ...session, ledger }, entry, 'browser', reported), ...ledger.lines());
    h.assert(problems.length === 0,
      `${problems.length} problem(s) on the footer-link pages:\n    - ${problems.join('\n    - ')}`);
  } finally {
    await closeBrowserWithChartCleanup(session.browser, config.baseUrl);
  }
}

if (require.main === module) {
  h.runCheck({ name: 'page-health-footer-links', run: main });
}

module.exports = { main };
