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
 * Schedule ▸ Administration ▸ the left-nav link for `route` ▸ its page in the frame.
 * @returns {Promise<{admin: import('playwright').Page, frame: import('playwright').Frame}>}
 */
async function openAdminPage(s, route, { label = route, timeout = TIMEOUT } = {}) {
  const { page: admin } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel, #admin2').first(),
    { context: s.context, recorder: s.recorder, label: `admin-${label}`, timeout });
  const link = admin.locator(`a.xlink[rel$="/${route}"]`).first();
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
 * address, so the frame is read only after the second load.
 */
async function submitInFrame(admin, control, route, { afterRoute = route, afterQuery = '', timeout = TIMEOUT } = {}) {
  const [response] = await Promise.all([
    admin.waitForResponse(r => r.request().method() === 'POST' && routeOf(r.url()).endsWith(`/${route}`), { timeout }),
    control.click(),
  ]);
  const frame = await frameAt(admin, afterRoute, { query: afterQuery, timeout });
  await frame.waitForLoadState('load', { timeout });
  return { response, frame };
}

module.exports = { openAdminPage, frameAt, submitInFrame };
