/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Shared helpers for the popup-opener-*-playwright-checks.js family.
 *
 * WHY THESE EXIST. A popup that calls back into window.opener only works while the
 * popup and its opener stay in one browsing-context group. CARLOS sends
 * Cross-Origin-Opener-Policy: same-origin from the Struts `coop` interceptor, and
 * that interceptor sets the header in a PreResultListener: a document that is not
 * the result of a Struts action reaching a result (a static .html, a servlet, a
 * gate that forwards inside the action and returns NONE, any action that answers
 * with response.sendRedirect()) carries no COOP at all. Chromium enforces COOP on
 * every response of a navigation, redirects included, so one COOP-less hop in the
 * popup's history -- or a COOP mismatch between the popup's first document and the
 * opener's top-level document -- severs window.opener for good. The page then
 * throws a TypeError, or silently does nothing, where it should hand a value back.
 *
 * documentChain() records every top-level document response per page (status,
 * path, COOP value) so a failing pick can say WHICH hop lost the opener, and
 * pickInPopup() clicks a picker row and requires the popup to close itself, which
 * is what every one of these pickers does once the opener callback has run.
 */
const h = require('./playwright-harness');

const DEFAULT_TIMEOUT = 20000;

/** Records top-level document responses per page: [{status, path, coop}]. */
function documentChain(context) {
  const chains = new WeakMap();
  context.on('response', (response) => {
    try {
      const request = response.request();
      if (request.resourceType() !== 'document') return;
      const frame = request.frame();
      const page = frame.page();
      if (!page || frame !== page.mainFrame()) return;
      if (!chains.has(page)) chains.set(page, []);
      chains.get(page).push({
        status: response.status(),
        path: new URL(response.url()).pathname,
        coop: response.headers()['cross-origin-opener-policy'] || 'none',
      });
    } catch (error) {
      // A detached frame has no page; such a response cannot be the popup's own.
    }
  });
  return {
    chain(page) { return chains.get(page) || []; },
    /** One line naming each hop and its COOP value, for assertion messages. */
    describe(page) {
      const hops = chains.get(page) || [];
      return hops.length
        ? hops.map(hop => `${hop.status} ${hop.path} [COOP ${hop.coop}]`).join(' -> ')
        : '(no document responses recorded)';
    },
  };
}

/**
 * Whether the popup can still reach its opener: 'live', 'closed' or 'null'.
 * Diagnostic only (it reads state; it drives nothing).
 */
async function openerState(popup) {
  if (popup.isClosed()) return 'popup-closed';
  return popup.evaluate(() => {
    if (window.opener === null) return 'null';
    return window.opener.closed ? 'closed' : 'live';
  }).catch(error => `unreadable (${error.message.split('\n')[0]})`);
}

/**
 * Click a picker row and require the popup to close itself. The pickers call
 * window.opener first and self.close() last, so a popup that stays open means
 * the opener call threw before it could close. Returns true when it closed.
 */
async function pickInPopup(popup, locator, options = {}) {
  const timeout = options.timeout || DEFAULT_TIMEOUT;
  const target = typeof locator === 'string' ? popup.locator(locator) : locator;
  await target.first().waitFor({ state: 'visible', timeout });
  const closed = popup.waitForEvent('close', { timeout }).then(() => true, () => false);
  await target.first().click({ timeout, noWaitAfter: true }).catch((error) => {
    if (!popup.isClosed()) throw error;
  });
  return closed;
}

/** The failure text for a picker whose value never reached the opener. */
async function lostOpenerMessage(what, popup, chain, detail = 'the value picked in the popup never reached the parent form') {
  const state = await openerState(popup);
  return `${what}: ${detail} (window.opener ${state}; popup documents: ${chain.describe(popup)})`;
}

/**
 * A page that closes itself in its own onload (close.jsp and friends) aborts the
 * CSRFGuard script it was still loading: Chromium reports net::ERR_ABORTED for
 * /csrfguard. Consume exactly those entries for `label`, recorded since `since`,
 * once the caller has proven the save and the close; every other failure stays strict.
 */
function consumeClosingAbort(recorder, since, label) {
  for (let index = recorder.requestFailures.length - 1; index >= since; index -= 1) {
    const entry = recorder.requestFailures[index];
    if (entry.label === label && entry.resourceType === 'script' && entry.errorText === 'net::ERR_ABORTED'
      && new URL(entry.url).pathname.endsWith('/csrfguard')) recorder.requestFailures.splice(index, 1);
  }
}

/** Positive integer guard for ids captured from SQL. */
function assertId(value, message) {
  h.assert(/^[1-9]\d*$/.test(String(value || '')), message);
  return String(value);
}

module.exports = { documentChain, openerState, pickInPopup, lostOpenerMessage, consumeClosingAbort, assertId };
