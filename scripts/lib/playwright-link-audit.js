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
 * "Open everything this page offers, by clicking it, and report what is broken."
 *
 * WHY THIS IS A SHARED MODULE. Three of the densest surfaces in CARLOS -- the
 * Administration panel (~120 items), the patient Master Record, and the eChart's
 * left navigation -- have the same shape and the same failure mode: dozens of
 * links, nearly all opening popups through popupPage()/popupOscarRx(), almost
 * none covered by a browser check, and a break that is invisible server-side
 * because the page returns HTTP 200 with an inline script that throws. Writing
 * the same loop three times would mean fixing it three times.
 *
 * WHY IT CATALOGUES INSTEAD OF LISTING URLS. The page's own markup is the list.
 * A hard-coded list of routes goes stale silently -- a removed item stops being
 * checked and nobody notices -- and it cannot see a link that was added. It also
 * keeps the check honest about the rule these follow: the target is whatever the
 * link says, and it is reached by clicking the link.
 *
 * ATTRIBUTION IS THE POINT. Browser findings are sliced per item, so a failure
 * names the page that broke ("Manage Billing Form: uncaught ReferenceError...")
 * rather than the run. A check that says only "something failed somewhere in 120
 * pages" does not get acted on.
 */

const { assert, relabelStrictPage, screenshot } = require('./playwright-harness');
const { clickOpensPopup } = require('./playwright-ui');

const DEFAULT_TIMEOUT = 20000;
// How long a click is given to START a navigation before the item is treated
// as one that acts in place. Generous for a local Tomcat, short enough that a
// 120-item sweep does not pay the full item timeout for every in-place item.
const NAVIGATION_START_TIMEOUT = 4000;
const ERROR_PAGE_RE = /CARLOS has encountered an unexpected error|HTTP Status 5\d\d|Exception Report|There is no Action mapped|Whitelabel Error Page/i;

/**
 * Read every navigable item out of the live page.
 *
 * An item is an anchor with visible text that goes somewhere: a real href, or an
 * onclick naming an application path -- which is how almost every CARLOS admin
 * and chart link is written (popupPage(600,900,'<ctx>/route')).
 */
async function catalogueLinks(page, options = {}) {
  const selector = options.selector || 'a';
  const items = await page.$$eval(selector, (anchors) => anchors.map((anchor, index) => {
    const text = (anchor.textContent || '').replace(/\s+/g, ' ').trim();
    const href = anchor.getAttribute('href') || '';
    const onclick = anchor.getAttribute('onclick') || anchor.getAttribute('onClick') || '';
    // Three shapes, in order, because CARLOS writes all three and an
    // absolute-only pattern dropped the other two: the item was catalogued
    // with no route, and an item with href="#" and no route was filtered out
    // of the audit entirely -- so those pages were never opened while the
    // audit still reported a full sweep.
    //   absolute   popupPage(600,900,'/carlos/billing/...')
    //   relative   popupPage(..., '../encounter/IncomingEncounter?...')
    //   bare       popup(..., 'DemographicEdit?demographic_no=...')
    const routeInOnclick = onclick.match(/["']((?:\.{1,2}\/)+[A-Za-z0-9_][^"'\s]*)["']/)
      || onclick.match(/["'](\/[A-Za-z0-9_][A-Za-z0-9_/.-]*(?:\?[^"']*)?)["']/)
      // Bare paths only when the token actually looks like one: it carries a
      // slash, a query string, or a server-page extension. Without that guard
      // this matches the window name and the feature string that sit in the
      // same argument list ('_blank', 'width=600'), and the audit would report
      // a route for every opener whether or not it found one.
      // The segment class excludes '/' deliberately. With '/' inside it, a
      // segment could be split at any slash and (?:\/[^...]*)* had exponentially
      // many ways to match the same string -- catastrophic backtracking on an
      // unterminated quote full of slashes, which would hang the audit inside
      // the page rather than fail it (CodeQL js/redos).
      || onclick.match(/["']([A-Za-z0-9_][A-Za-z0-9_.-]*(?:\/[^"'\s?/]*)*(?:\.(?:jsp|do|html?)\b)?(?:\?[^"']*)?)["']/);
    const looksLikeRoute = routeInOnclick
      && (/^[./]/.test(routeInOnclick[1])
        || /[/?]/.test(routeInOnclick[1])
        || /\.(?:jsp|do|html?)$/i.test(routeInOnclick[1]));
    const hasRealHref = href && href !== '#' && !/^javascript:/i.test(href);
    if (!text || text.length > 80 || (!hasRealHref && !looksLikeRoute)) {
      return null;
    }
    return {
      // WHY THE INDEX. It is the item's identity for clicking. Two admin items
      // routinely share link text while pointing at different routes ("Search",
      // "Report", a module name under two headings); locating by text would
      // click the first one both times, so the second route would never be
      // opened while the check still reported two successes.
      index,
      text,
      href: hasRealHref ? href : '',
      route: looksLikeRoute ? routeInOnclick[1] : '',
      // window.open and target="_blank" open a window exactly as popupPage()
      // does. Classifying them as same-tab sent the audit down the navigation
      // branch, where it waited for a navigation that never came and then read
      // the UNCHANGED host page -- reporting the opener's own content as the
      // item's destination, which passes for every broken popup.
      opensPopup: /popup|newWindow|postToPopup|window\.open/i.test(onclick)
        || (anchor.getAttribute('target') || '').toLowerCase() === '_blank',
    };
  }).filter(Boolean));
  // The selector travels with the item so the click resolves against the same
  // list the index was taken from.
  return items.map((item) => ({ ...item, selector }));
}

/** Drop duplicates (the same item often appears in two permission branches). */
function dedupe(items) {
  const seen = new Set();
  return items.filter((item) => {
    const key = `${item.text}|${item.href || item.route}`;
    if (seen.has(key)) {
      return false;
    }
    seen.add(key);
    return true;
  });
}

function snapshotRecorder(recorder) {
  return {
    pageErrors: recorder.pageErrors.length,
    consoleIssues: recorder.consoleIssues.length,
    badResponses: recorder.badResponses.length,
    requestFailures: recorder.requestFailures.length,
    // Counted because a page that starts asking for confirmation blocks the
    // user-facing flow, and a strict page records that as an unexpected dialog.
    // Leaving it out of the snapshot made the audit blind to a whole class of
    // break while everything it did look at stayed green.
    unexpectedDialogs: (recorder.unexpectedDialogs || []).length,
  };
}

/** Everything the browser reported since the snapshot, phrased against one item. */
function findingsSince(recorder, before, itemText) {
  const problems = [];
  for (const entry of recorder.pageErrors.slice(before.pageErrors)) {
    problems.push(`${itemText}: uncaught ${entry.text.split('\n')[0]}`);
  }
  for (const entry of recorder.consoleIssues.slice(before.consoleIssues)) {
    problems.push(`${itemText}: console ${entry.type}: ${entry.text.split('\n')[0]}`);
  }
  for (const entry of recorder.badResponses.slice(before.badResponses)) {
    problems.push(`${itemText}: HTTP ${entry.status}${entry.reason ? ` (${entry.reason})` : ''} on a ${entry.resourceType || 'resource'}`);
  }
  for (const entry of recorder.requestFailures.slice(before.requestFailures)) {
    problems.push(`${itemText}: ${entry.resourceType} request failed (${entry.errorText})`);
  }
  for (const entry of (recorder.unexpectedDialogs || []).slice(before.unexpectedDialogs || 0)) {
    problems.push(`${itemText}: raised an unexpected ${entry.type} dialog, which was dismissed to keep the audit moving`);
  }
  return problems;
}

/**
 * A page whose scripts POST over AJAX must carry a POPULATED CSRF-TOKEN input.
 *
 * CLAUDE.md's bootstrapping rule: CSRFGuard only injects the token into a form
 * with a real action and a non-GET method, so a page that reads
 * input[name="CSRF-TOKEN"] from fetch() sends an empty token, is answered with an
 * HTML error page, and throws inside a catch the user never sees. Presence alone
 * is not the assertion -- an empty input is the defect.
 */
async function csrfBootstrapFinding(page, itemText) {
  const readsToken = await page.evaluate(() => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-injection.playwright-evaluate-injection -- fixed helper code, no interpolation
    const inline = Array.from(document.querySelectorAll('script:not([src])'))
      .map((element) => element.textContent || '').join('\n');
    return /CSRF-TOKEN/.test(inline) && /fetch\(|XMLHttpRequest|\$\.ajax|\$\.post/.test(inline);
  }).catch(() => false);
  if (!readsToken) {
    return null;
  }
  const token = await page.locator('input[name="CSRF-TOKEN"]').first().inputValue().catch(() => '');
  if (token && token.trim()) {
    return null;
  }
  return `${itemText}: reads input[name="CSRF-TOKEN"] from an AJAX POST, but that input is absent or empty, so those requests are rejected with an HTML error page (CLAUDE.md CSRF bootstrapping rule)`;
}

/**
 * The one anchor this catalogue entry came from.
 *
 * By index within the same selector the catalogue was read from, never by text:
 * see the note in catalogueLinks. The identity check re-reads the anchor and
 * fails loudly if the page changed under us, because clicking a different link
 * than the one being reported on is worse than not clicking at all.
 */
function itemLocator(hostPage, item) {
  assert(Number.isInteger(item.index) && item.index >= 0,
    `catalogue item "${item.text}" has no index; it did not come from catalogueLinks()`);
  return hostPage.locator(item.selector || 'a').nth(item.index);
}

async function openItem(context, hostPage, item, recorder, label, timeout) {
  const link = itemLocator(hostPage, item);
  const stillThere = ((await link.textContent({ timeout }).catch(() => null)) || '').replace(/\s+/g, ' ').trim();
  assert(stillThere === item.text,
    `the page changed under the audit: item ${item.index} was "${item.text}" when catalogued and is "${stillThere}" now`);
  await link.scrollIntoViewIfNeeded().catch(() => {});
  if (item.opensPopup) {
    const popup = await clickOpensPopup(hostPage, link, {
      context, label, recorder, timeout,
    });
    return { page: popup, isPopup: true };
  }
  const before = hostPage.url();
  // ARMED BEFORE THE CLICK. waitForLoadState() asked for after the click
  // resolves instantly against the document still on screen when the new one
  // has not started loading yet, so the audit read the OPENER's body and
  // reported it as this item's destination -- a pass for every item whose page
  // is broken.
  const navigationStarted = hostPage.waitForURL((url) => String(url) !== before, { timeout })
    .then(() => true, () => false);
  await link.click({ timeout });
  // Bounded, because plenty of admin items inject a panel in place and never
  // navigate at all; waiting the full timeout on each of those would add
  // minutes to a 120-item sweep for no signal.
  let settleTimer;
  const settled = new Promise((resolve) => {
    settleTimer = setTimeout(() => resolve(false), Math.min(timeout, NAVIGATION_START_TIMEOUT));
  });
  const navigated = await Promise.race([navigationStarted, settled]);
  clearTimeout(settleTimer);
  if (navigated) {
    await hostPage.waitForLoadState('domcontentloaded', { timeout }).catch(() => {});
    // Same page object, new document: without the relabel it keeps the label
    // it was wired with, and a later assertStrictPage scoped to this item's
    // label finds nothing recorded under it and passes.
    if (recorder) {
      relabelStrictPage(hostPage, label);
    }
  }
  await hostPage.waitForLoadState('networkidle', { timeout }).catch(() => {});
  return { page: hostPage, isPopup: false, cameFrom: before };
}

/**
 * Click every item, assert the destination, and put the host page back.
 *
 * @returns {{opened: string[], skipped: number, failures: string[]}}
 */
async function auditCatalogue(options) {
  const {
    context, hostPage, items, recorder, labelPrefix,
  } = options;
  const timeout = options.timeout || DEFAULT_TIMEOUT;
  const skipRules = options.skipRules || [];
  const limit = options.limit || 0;
  const screenshotDir = options.screenshotDir || '';
  const hostUrl = hostPage.url();

  const opened = [];
  const failures = [];
  let skipped = 0;

  for (const item of items) {
    if (limit && opened.length >= limit) {
      break;
    }
    const rule = skipRules.find((candidate) => candidate.match.test(item.text));
    if (rule) {
      skipped += 1;
      continue;
    }
    const label = `${labelPrefix}:${item.text}`.slice(0, 80);
    const before = snapshotRecorder(recorder);
    let target = null;
    try {
      target = await openItem(context, hostPage, item, recorder, label, timeout);
      const body = await target.page.locator('body').innerText({ timeout }).catch(() => '');
      if (ERROR_PAGE_RE.test(body)) {
        failures.push(`${item.text}: rendered an error page`);
      } else if (!body.trim()) {
        failures.push(`${item.text}: rendered a blank page`);
      } else {
        const csrf = await csrfBootstrapFinding(target.page, item.text);
        if (csrf) {
          failures.push(csrf);
        }
      }
      opened.push(item.text);
    } catch (error) {
      failures.push(`${item.text}: ${String(error.message).split('\n')[0]}`);
    } finally {
      const browserFindings = findingsSince(recorder, before, item.text);
      failures.push(...browserFindings);
      if (screenshotDir && browserFindings.length && target) {
        const safeName = `${labelPrefix}-${item.text.replace(/[^A-Za-z0-9]+/g, '-').slice(0, 40)}`;
        await screenshot(target.page, screenshotDir, safeName).catch(() => {});
      }
      if (target && target.isPopup) {
        await target.page.close().catch(() => {});
      } else if (hostPage.url() !== hostUrl) {
        await hostPage.goBack({ timeout }).catch(() => {});
        await hostPage.waitForLoadState('domcontentloaded', { timeout }).catch(() => {});
      }
    }
  }
  return { opened, skipped, failures };
}

/** Standard reporting so the three audit checks fail the same way. */
function assertAuditClean(result, options = {}) {
  const surface = options.surface || 'surface';
  const minimumOpened = options.minimumOpened || 1;
  assert(result.failures.length === 0,
    `${result.failures.length} ${surface} item(s) are broken:\n    - ${result.failures.join('\n    - ')}`);
  assert(result.opened.length >= minimumOpened,
    `Only ${result.opened.length} ${surface} item(s) opened; expected at least ${minimumOpened}, so the catalogue step is probably broken rather than the pages`);
}

module.exports = {
  ERROR_PAGE_RE,
  assertAuditClean,
  auditCatalogue,
  catalogueLinks,
  csrfBootstrapFinding,
  dedupe,
  findingsSince,
  itemLocator,
  snapshotRecorder,
};
