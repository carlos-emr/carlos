/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
/*
 * Administration pages that load into the Administration shell's frame: Schedule ▸ Administration
 * ▸ a left-nav link (an `a.xlink` whose rel names the route) ▸ the page, inside the shell's iframe.
 *
 * A Frame has no request or response events of its own, so waits for a form's POST are made on
 * the page that holds the frame (`admin`), and the frame is looked up again by route after it
 * reloads.
 */
const h = require('./playwright-harness');
const { clickOpensPopupOrNavigates } = require('./playwright-ui');
const { revealAuditLink } = require('./playwright-link-audit');

const TIMEOUT = 30000;

function routeOf(url) {
  try {
    return new URL(url, 'http://x').pathname;
  } catch (error) {
    return '';
  }
}

/**
 * The shell's child frame once it shows `route` (and `query`, when given).
 * @returns {Promise<import('playwright').Frame>}
 */
async function frameAt(admin, route, { query = '', timeout = TIMEOUT } = {}) {
  const deadline = Date.now() + timeout;
  let frame;
  while (!(frame = admin.frames().find(f => f.parentFrame() && routeOf(f.url()).endsWith(`/${route}`) && f.url().includes(query)))) {
    h.assert(Date.now() < deadline, `The administration frame did not show ${route}${query ? ` with ${query}` : ''}`);
    await admin.waitForTimeout(100);
  }
  await frame.waitForLoadState('domcontentloaded');
  return frame;
}

/**
 * Schedule ▸ Administration ▸ the left-nav link for `route` ▸ its page in the frame. With
 * `skipReason`, a menu that has no entry for `route` ends the check as SKIP with that reason.
 * @returns {Promise<{admin: import('playwright').Page, frame: import('playwright').Frame}>}
 */
async function openAdminPage(s, route, { label = route, timeout = TIMEOUT, skipReason = '' } = {}) {
  const { page: admin } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
    { context: s.context, recorder: s.recorder, label: `admin-${label}`, timeout });
  const link = admin.locator(`a.xlink[rel$="/${route}"]`).first();
  if (skipReason) {
    // Some entries are shown only with a property or a module on; the left nav is rendered whole,
    // so once any entry is there, a missing one is missing by configuration.
    await admin.locator('a.xlink').first().waitFor({ state: 'attached', timeout });
    if (await link.count() === 0) throw new h.SkipCheck(skipReason);
  }
  await link.waitFor({ state: 'attached', timeout });
  await revealAuditLink(admin, link, timeout);
  await link.click();
  const frame = await frameAt(admin, route, { timeout });
  await h.assertNotErrorPage(frame, label);
  return { admin, frame };
}

/**
 * Click `control` in the frame and wait for the POST to `route` it sends, answered; then the frame
 * once it has reloaded at `afterRoute` (default: the same route). A save that redirects back
 * (post/redirect/get) navigates the frame twice: pass `afterQuery`, a piece of the redirected
 * address. The frame is returned only once it holds a new document.
 */
async function submitInFrame(admin, control, route, { afterRoute = route, afterQuery = '', timeout = TIMEOUT } = {}) {
  await markStale(admin, route);
  const [response] = await Promise.all([
    admin.waitForResponse(r => r.request().method() === 'POST' && routeOf(r.url()).endsWith(`/${route}`), { timeout }),
    control.click(),
  ]);
  const frame = await freshFrameAt(admin, afterRoute, { query: afterQuery, timeout });
  return { response, frame };
}

/** Mark the document the frame at `route` now shows, so a wait can tell it from the next one. */
async function markStale(admin, route) {
  const old = admin.frames().find(f => f.parentFrame() && routeOf(f.url()).endsWith(`/${route}`));
  if (old) await old.evaluate(() => { window.__carlosAdminFrameStale = true; }).catch(() => {});
}

/** The frame at `route` (and `query`) once it holds a new, fully loaded document. */
async function freshFrameAt(admin, route, { query = '', timeout = TIMEOUT } = {}) {
  const deadline = Date.now() + timeout;
  for (;;) {
    const frame = admin.frames().find(f => f.parentFrame() && routeOf(f.url()).endsWith(`/${route}`) && f.url().includes(query));
    if (frame) {
      const stale = await frame.evaluate(() => window.__carlosAdminFrameStale === true || document.readyState !== 'complete')
        .catch(() => true);
      if (!stale) return frame;
    }
    h.assert(Date.now() < deadline, `The administration frame did not load a new ${route}${query ? ` with ${query}` : ''}`);
    await admin.waitForTimeout(100);
  }
}

/**
 * The left-nav link for `route` clicked again, as a user reopens a page: returns the frame only once
 * it holds a NEW document, never the page it just left.
 */
async function reopenAdminPage(admin, route, { timeout = TIMEOUT } = {}) {
  await markStale(admin, route);
  await admin.locator(`a.xlink[rel$="/${route}"]`).first().click();
  return freshFrameAt(admin, route, { timeout });
}

module.exports = { openAdminPage, frameAt, submitInFrame, reopenAdminPage };
