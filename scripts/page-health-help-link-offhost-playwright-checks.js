#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */

/*
 * Administration > Help Link Setting must not call a third party when it opens.
 *
 * User path: login > Schedule > Administration > Help Link Setting (admin/ResourceBaseUrl,
 * a Toast UI rich-text editor for the Help details).
 *
 * resourcebaseurl.jsp constructs `new toastui.Editor({...})` without `usageStatistics:
 * false`, and the editor then sends a usage-statistics event (with the host name) to
 * https://www.google-analytics.com/collect. CreateMessage.jsp and ViewMessage.jsp both
 * pass usageStatistics:false; this page does not. An EMR admin page must make no request
 * to any host but the application. Every off-host request is ABORTED in the browser
 * (nothing leaves the machine) and recorded; the check asserts the record is empty.
 * FAILS today at the final step.
 *
 * READ-ONLY, no fixtures: the page is opened and closed, nothing is saved.
 *
 * Implements: wave-7 `page-health` sweep (off-host requests).
 */

const h = require('./lib/playwright-harness');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { catalogueLinks, dedupe, resolveAuditLink, revealAuditLink } = require('./lib/playwright-link-audit');
const { startSession } = require('./lib/page-health-engine');

async function main() {
  const config = h.readConfig();
  const timeout = Number(process.env.PAGE_HEALTH_TIMEOUT_MS || '20000');
  const session = await startSession(config);
  try {
    const opener = session.schedulePage.locator('#admin-panel, #admin2').first();
    h.assert(await opener.count() > 0, 'The schedule offers no Administration control');
    const { page: adminPage } = await clickOpensPopupOrNavigates(session.schedulePage, opener, {
      context: session.context, label: 'administration', recorder: session.recorder, timeout,
    });
    console.log('  PASS page-health-help-link-offhost: Administration opened from the schedule');

    const item = dedupe(await catalogueLinks(adminPage, { identity: true }))
      .find(candidate => /^Help Link Setting$/i.test(candidate.text));
    h.assert(item, 'The Administration panel offers no "Help Link Setting" item');
    const link = await resolveAuditLink(adminPage, item, timeout);
    await revealAuditLink(adminPage, link, timeout);
    const offHostBefore = session.probe.offHost.length;
    // Live DOM: the item is an `a.xlink[rel=/carlos/admin/ResourceBaseUrl]` that the Administration
    // shell loads into an iframe inside #dynamic-content (a popup is only the legacy
    // popupPage() markup in admin.jsp, which the panel does not render for this item).
    await link.click({ timeout });
    const frame = adminPage.frameLocator('#dynamic-content iframe');
    await frame.locator('.toastui-editor-defaultUI, .ProseMirror').first().waitFor({ state: 'attached', timeout });
    // The editor sends its beacon on a one-second timer.
    await adminPage.waitForTimeout(2500);
    console.log('  PASS page-health-help-link-offhost: Help Link Setting editor rendered');

    const requests = session.probe.offHost.slice(offHostBefore)
      .map(entry => `${entry.resourceType} request to ${entry.url}`);
    h.assert(requests.length === 0,
      `Help Link Setting made ${requests.length} request(s) to a host other than the application, which CARLOS must never do `
      + `from an EMR page (blocked in the browser): ${[...new Set(requests)].join('; ')}`);
    // After the off-host verdict so the telemetry diagnostic stays primary: the editor must
    // also render without a script error, console error or failed same-origin resource.
    h.assertStrictPage(session.recorder);
  } finally {
    await session.close();
  }
}

if (require.main === module) {
  h.runCheck({ name: 'page-health-help-link-offhost', run: main });
}

module.exports = { main };
