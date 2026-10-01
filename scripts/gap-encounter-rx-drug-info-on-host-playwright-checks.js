#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Rx "Info" on a staged drug must not send the drug name to another host.
 *
 * User path: Schedule ▸ Master Record ▸ Rx ▸ Search (no match) ▸ Custom Drug (rx/chooseDrug) ▸ the
 * write-script page's staged-drug list ▸ "Info" (opens rx/drugInfo in a window; the same control is on
 * the Rx card list, the reprint list and the print window).
 * RxDrugInfo2Action answers with a redirect to http://resource.oscarmcmaster.org/... carrying the drug
 * name in the query string: plain HTTP, a third party the deployment does not control, and the name of
 * what the patient is prescribed leaves the clinic's network. Asserts that Info answers without a
 * redirect to another host. Nothing leaves this machine: the Info link is clicked for real but
 * window.open is replaced by a recorder, the recorded CARLOS address is fetched by the check without
 * following redirects, and only the redirect target's HOST is looked at (never the URL, which carries
 * the drug text). The assertion fails while the redirect stands.
 * Fixtures: the owned synthetic patient and one session-only custom drug named with the run marker.
 * Implements gap-encounter "drug information from the prescription window" (rx/drugInfo had no check).
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

async function workflow(s) {
  const { patient, marker, config } = s;
  const appHost = new URL(config.baseUrl).host;
    const chart = await s.chart();
  let page;

  await s.step('Prescriptions ▸ Search (no match) ▸ Custom Drug opens the write-script page with an Info link for the staged drug', async () => {
    page = await s.popup(chart, chart.locator('#menuTitleRx a').first(), 'rx-page');
    await h.gotoApp(page, config.baseUrl, `/rx/searchDrug?demographicNo=${patient}&searchString=${encodeURIComponent(`${marker}-info-nosuchdrug`)}`);
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    const chosen = page.waitForResponse(r => /\/rx\/chooseDrug(\?|$)/.test(r.url()), { timeout: 30000 });
    chosen.catch(() => {});
    await h.withExpectedDialogs(page, async () => {
      await page.locator('a[href="javascript:customWarning();"]').click();
      await chosen;
    }, { accept: true });
    await page.locator('form#frm textarea[name="customName"]').waitFor({ state: 'attached', timeout: 20000 });
    h.assert(await page.locator('a[href^="javascript:ShowDrugInfo("]').count() >= 1, 'The staged drug has no Info link');
  });

  await s.step('clicking Info opens an address on CARLOS that does not redirect to another host', async () => {
    // The link is clicked for real; window.open is replaced by a recorder so that no window (and so no
    // redirect) is ever followed by the browser. The recorded address is then read from CARLOS WITHOUT
    // following redirects, and only the Location's host is looked at (the address carries the drug text).
    await page.evaluate(() => {
      window.__opened = [];
      window.open = address => { window.__opened.push(String(address)); return null; };
    });
    await page.locator('a[href^="javascript:ShowDrugInfo("]').first().click();
    const opened = await page.evaluate(() => window.__opened);
    h.assert(opened.length === 1, 'Clicking Info did not open exactly one window');
    const target = new URL(opened[0], page.url());
    h.assert(target.host === appHost && target.pathname.endsWith('/rx/drugInfo'), 'The Info link does not open a CARLOS address');
    const response = await s.context.request.get(target.toString(), { maxRedirects: 0 });
    const location = response.headers().location;
    const host = location ? new URL(location, target).host : null;
    await response.dispose();
    h.assert(host === null || host === appHost,
      `Rx Info redirects to another host (${host}), carrying the drug name in the address`);
  });
}

if (require.main === module) runWorkflow('gap-encounter-rx-drug-info-on-host', workflow, { openPatient: true });
module.exports = { workflow };
