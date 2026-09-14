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

const {
  assert, relabelStrictPage, screenshot, withoutQueryStrings,
} = require('./playwright-harness');
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
    // A javascript: HREF IS AN OPENER TOO, and 256 anchors across the webapp
    // write theirs that way -- five in admin.jsp alone, e.g.
    //   <a href='javascript: popupPage(500, 900, "<ctx>/quickBillingBC");'>
    // They carry no onclick, so reading openers from onclick alone found no
    // route, hasRealHref rejected javascript: (rightly -- it is not a
    // destination), and the anchor was dropped from the catalogue entirely.
    // The audits then reported a complete sweep of a surface they had never
    // opened part of. The body after the scheme is the same script an onclick
    // would hold, so it is read the same way.
    const jsHref = /^javascript:/i.test(href) ? href.replace(/^javascript:/i, '') : '';
    const opener = onclick || jsHref;
    // THE ADMINISTRATION SHELL PUTS ITS ROUTE IN rel. Its left nav is 124
    // anchors written
    //   <a href='javascript:void(0);' class="xlink" rel="<ctx>/admin/UnLock">
    // and the .xlink handler (leftNav.jspf:906) reads $(this).attr('rel') and
    // loads it into an iframe. href gives nothing -- void(0) has no route in it
    // -- and there is no onclick, so every one of those was dropped and
    // admin-index-links swept a surface with its primary navigation missing.
    //
    // Only when it looks like a PATH. rel is a standard HTML attribute whose
    // ordinary values are keywords (noopener, noreferrer, nofollow, stylesheet);
    // requiring a slash keeps those out without maintaining a keyword list.
    const relAttribute = (anchor.getAttribute('rel') || '').trim();
    const relRoute = /^[./]/.test(relAttribute) || /^[A-Za-z0-9_][A-Za-z0-9_.-]*\//.test(relAttribute)
      ? relAttribute
      : '';
    // Three shapes, in order, because CARLOS writes all three and an
    // absolute-only pattern dropped the other two: the item was catalogued
    // with no route, and an item with href="#" and no route was filtered out
    // of the audit entirely -- so those pages were never opened while the
    // audit still reported a full sweep.
    //   absolute   popupPage(600,900,'/carlos/billing/...')
    //   relative   popupPage(..., '../encounter/IncomingEncounter?...')
    //   bare       popup(..., 'DemographicEdit?demographic_no=...')
    const routeInOnclick = opener.match(/["']((?:\.{1,2}\/)+[A-Za-z0-9_][^"'\s]*)["']/)
      || opener.match(/["'](\/[A-Za-z0-9_][A-Za-z0-9_/.-]*(?:\?[^"']*)?)["']/)
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
      || opener.match(/["']([A-Za-z0-9_][A-Za-z0-9_.-]*(?:\/[^"'\s?/]*)*(?:\.(?:jsp|do|html?)\b)?(?:\?[^"']*)?)["']/);
    const looksLikeRoute = routeInOnclick
      && (/^[./]/.test(routeInOnclick[1])
        || /[/?]/.test(routeInOnclick[1])
        || /\.(?:jsp|do|html?)$/i.test(routeInOnclick[1]));
    const route = looksLikeRoute ? routeInOnclick[1] : relRoute;
    // A FRAGMENT IS NOT A DESTINATION. Excluding only the exact string '#' let
    // every in-page tab and collapse through (`href="#custom"`,
    // `href="#collapseClinical"`, `href="#top"`). Clicking one stays on the host
    // document, so auditCatalogue counted an unchanged page as a successful
    // open -- inflating the coverage count, and satisfying the per-surface
    // minimum with items that opened nothing. An onclick-derived route on the
    // same anchor is still kept: many CARLOS openers are written
    // `href="#" onclick="popupPage(...)"`.
    const hasRealHref = href && !href.startsWith('#') && !/^javascript:/i.test(href);
    if (!text || text.length > 80 || (!hasRealHref && !route)) {
      return null;
    }
    return {
      // The browser's own resolution base for this anchor -- document.baseURI,
      // which honours any <base> tag. A bare onclick route like
      // 'DemographicEdit?demographic_no=1' means "next to the page I am on",
      // and resolving it against the context root instead sends it outside the
      // application. See resolveRoute in anonymous-access-refused.
      baseURI: document.baseURI,
      // WHY THE INDEX. It is the item's identity for clicking. Two admin items
      // routinely share link text while pointing at different routes ("Search",
      // "Report", a module name under two headings); locating by text would
      // click the first one both times, so the second route would never be
      // opened while the check still reported two successes.
      index,
      text,
      href: hasRealHref ? href : '',
      route,
      // window.open and target="_blank" open a window exactly as popupPage()
      // does. Classifying them as same-tab sent the audit down the navigation
      // branch, where it waited for a navigation that never came and then read
      // the UNCHANGED host page -- reporting the opener's own content as the
      // item's destination, which passes for every broken popup.
      opensPopup: /popup|newWindow|postToPopup|window\.open/i.test(opener)
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
    // THE SHARED HELPER COUNTS TOO, and on its own. carlos-ajax.js reads
    // input[name="CSRF-TOKEN"] on the page's behalf, so a page calling
    // CarlosAjax never mentions the token itself -- which is why the static
    // audit missed eight such pages, six of them violations, until it was
    // widened to cover the helper. Without this the browser half stays blind to
    // them too, and those six open clean in every surface audit that touches
    // them.
    //
    // Mirrors sendsMutatingViaSharedHelper() in lib/csrf-bootstrap-audit.js:
    // the helper defaults to POST and injects no token for GET or HEAD, so a
    // page whose every call is a GET has nothing to bootstrap. Written out
    // longhand rather than imported because this body runs inside the page.
    const helperCalls = [...inline.matchAll(/\bCarlosAjax\s*\.\s*(?:request|updater|post)\s*\(/g)];
    const mutatesViaHelper = helperCalls.some((call) => {
      const options = inline.slice(call.index, call.index + 400);
      const method = options.match(/\bmethod\s*:\s*['"]([A-Za-z]+)['"]/);
      return !method || !['GET', 'HEAD'].includes(method[1].toUpperCase());
    });
    if (mutatesViaHelper) {
      return true;
    }
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
  //
  // BOUNDED BY NAVIGATION_START_TIMEOUT, NOT BY THE ITEM TIMEOUT. Plenty of
  // admin items inject a panel in place and never navigate at all; waiting the
  // full timeout on each of those would add minutes to a 120-item sweep for no
  // signal. This used to be a Promise.race against a settle timer while
  // waitForURL still carried the full item timeout -- so every in-place item
  // left a waiter alive on the page for another (timeout - 4s), and a long
  // sweep accumulated several of them at once for a result nothing reads.
  // Putting the bound on waitForURL itself gives the same answer at the same
  // moment with nothing left pending: the rejection on timeout IS the "did not
  // navigate" signal.
  const startTimeout = Math.min(timeout, NAVIGATION_START_TIMEOUT);
  const navigationStarted = hostPage.waitForURL((url) => String(url) !== before, { timeout: startTimeout })
    .then(() => true, () => false);
  // For the in-place case below: what the page looked like before the click.
  const markupBefore = await hostPage.evaluate(() => document.body.innerHTML.length) // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-injection.playwright-evaluate-injection -- fixed helper code, no interpolation
    .catch(() => -1);
  const pagesBefore = context && typeof context.pages === 'function' ? context.pages().length : 0;
  await link.click({ timeout });
  const navigated = await navigationStarted;
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

  // DID THE CLICK DO ANYTHING AT ALL? An item that neither navigates nor opens
  // a popup used to be handed back as the host page, whose body is of course
  // non-empty -- so auditCatalogue counted it as opened, and an inert or broken
  // control could satisfy the per-surface minimum. Requiring navigation is
  // wrong here (unlike the surface audit): plenty of admin items legitimately
  // inject a panel in place. So the test is whether ANYTHING happened.
  //
  // A popup counts even when the item was not catalogued as an opener: several
  // CARLOS handlers reach window.open through a helper this module's pattern
  // does not name, and reporting those as "did nothing" would trade a false
  // pass for a false failure.
  let actedInPlace = navigated;
  if (!actedInPlace) {
    const pagesAfter = context && typeof context.pages === 'function' ? context.pages().length : 0;
    actedInPlace = pagesAfter > pagesBefore;
  }
  if (!actedInPlace && markupBefore >= 0) {
    actedInPlace = await hostPage.waitForFunction(
      (previousLength) => document.body.innerHTML.length !== previousLength, // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-injection.playwright-evaluate-injection -- fixed helper code, no interpolation
      markupBefore,
      { timeout: startTimeout },
    ).then(() => true, () => false);
  }
  return {
    page: hostPage, isPopup: false, cameFrom: before, actedInPlace,
  };
}

/**
 * The text of the DESTINATION, which for an in-place item is not the host body.
 *
 * The Administration shell loads a .contentLink route into #dynamic-content by
 * AJAX and an .xlink route into an iframe inside it. Either way the host body
 * still contains the whole shell -- its nav, its header, its chrome -- so
 * reading document.body found plenty of text no matter what came back. A
 * destination that was blank, zero bytes, or an error page passed as an opened
 * item, which is the failure this audit exists to report.
 *
 * `inPlaceTarget` is the container a surface loads into, supplied by the check
 * that knows its own shell. Without it the host body is still the best
 * available reading, and the actedInPlace test in openItem is what stands
 * between an inert click and a pass.
 */
async function destinationText(target, inPlaceTarget, timeout) {
  const read = (locator) => locator.innerText({ timeout }).catch(() => '');
  if (target.isPopup || !inPlaceTarget) {
    return read(target.page.locator('body'));
  }
  const container = target.page.locator(inPlaceTarget).first();
  if (await container.count().catch(() => 0) === 0) {
    return read(target.page.locator('body'));
  }
  // An .xlink item renders into an iframe: the container's own innerText is
  // empty because the content lives in another document.
  const frame = container.locator('iframe').first();
  if (await frame.count().catch(() => 0) > 0) {
    const frameBody = await frame.contentFrame().catch(() => null);
    if (frameBody) {
      return read(frameBody.locator('body'));
    }
  }
  return read(container);
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
  const inPlaceTarget = options.inPlaceTarget || '';
  const skipRules = options.skipRules || [];
  const limit = options.limit || 0;
  const screenshotDir = options.screenshotDir || '';
  const hostUrl = hostPage.url();

  const opened = [];
  const failures = [];
  let skipped = 0;

  // ATTEMPTS, not successes. The budget was compared with `opened`, which counts
  // only items that opened cleanly -- so with SURFACE_LIMIT=1, a first item that
  // failed or was skipped left the loop running through the entire catalogue,
  // which is the opposite of what a limit is for and turns a diagnostic run into
  // the full sweep it was meant to avoid.
  let attempted = 0;
  for (const item of items) {
    if (limit && attempted >= limit) {
      break;
    }
    const rule = skipRules.find((candidate) => candidate.match.test(item.text));
    if (rule) {
      skipped += 1;
      continue;
    }
    attempted += 1;
    const label = `${labelPrefix}:${item.text}`.slice(0, 80);
    const before = snapshotRecorder(recorder);
    let target = null;
    try {
      target = await openItem(context, hostPage, item, recorder, label, timeout);
      const body = await destinationText(target, inPlaceTarget, timeout);
      if (ERROR_PAGE_RE.test(body)) {
        failures.push(`${item.text}: rendered an error page`);
      } else if (!body.trim()) {
        failures.push(`${item.text}: rendered a blank page`);
      } else if (target.isPopup === false && target.actedInPlace === false) {
        // Counted as opened before this, because the unchanged host page has a
        // perfectly good body. An inert control could therefore make up the
        // per-surface minimum.
        failures.push(`${item.text}: clicking it did nothing -- no navigation, no popup, and the page did not `
          + 'change, so whatever this item is meant to reach was never reached');
      } else {
        const csrf = await csrfBootstrapFinding(target.page, item.text);
        if (csrf) {
          failures.push(csrf);
        }
        opened.push(item.text);
      }
    } catch (error) {
      // Sanitised for the same reason as anonymous-access: this message is
      // Playwright's, and a master-record item's route carries demographic_no.
      failures.push(`${item.text}: ${withoutQueryStrings(String(error.message).split('\n')[0])}`);
    } finally {
      const browserFindings = findingsSince(recorder, before, item.text);
      failures.push(...browserFindings);
      if (screenshotDir && browserFindings.length && target) {
        // item.index disambiguates. The name was labelPrefix + sanitised text
        // alone, and admin surfaces routinely carry two items with the SAME
        // text pointing at different routes (see the note on index in
        // catalogueLinks) -- so the second failure's screenshot overwrote the
        // first one's and the evidence for that item was simply gone.
        const safeName = `${labelPrefix}-${item.index}-${item.text.replace(/[^A-Za-z0-9]+/g, '-').slice(0, 40)}`;
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
