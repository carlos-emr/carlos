#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The classic note editor's "add billing" button opens one billing window for its own patient
 * (issue #4677).
 *
 * Index2.jsp stores the application context path as the session's casemgmt_oscar_baseurl, and
 * CaseManagementEntry2Action.prepareBillingUrl used to append request.getContextPath() to it again,
 * so the button opened /carlos/carlos/billing?... . The doubled path is not a billing page.
 *
 * User path: Schedule > Search > Master Record > E-Chart (entering the chart through Index2.jsp is what
 * stores casemgmt_oscar_baseurl in the session), then the classic note editor
 * (CaseManagementEntry?method=edit&from=casemgmt), which the chart window opens as a popup with itself as
 * window.opener. The editor shows the "add billing" button (billing role, not a TORONTO_RFQ install);
 * the E-Chart's own inline editor does not. The check clicks the button and follows the window it opens.
 *
 * Steps: the button opens an address whose path is the application's /billing with the context path
 * exactly once; the popup is a usable billing page (the strict-page harness refuses an error page)
 * that names the owned patient; the address carries the owned patient's demographic_no and no other.
 *
 * Fixtures: the owned FAKE- patient only. The check reads and writes no billing rows (it closes the
 * billing window without saving), so cleanup has nothing to remove.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

/**
 * Closing the editor reloads its opener (the editor's own window.opener.location.reload), and the chart
 * sends its note-lock release as a beacon from pagehide, which Chromium reports as an aborted "ping" once
 * the document is gone. That is the browser abandoning a load, not an application failure. Excuse exactly
 * those entries recorded since `mark` (a ping to CaseManagementEntry, ERR_ABORTED), and only after the
 * caller saw the chart reload; every other failure stays a finding.
 */
function consumeUnloadBeacons(recorder, mark) {
  for (let i = recorder.requestFailures.length - 1; i >= mark; i--) {
    const entry = recorder.requestFailures[i];
    if (entry.resourceType === 'ping' && /ERR_ABORTED/.test(entry.errorText || '')
        && new URL(entry.url).pathname.endsWith('/CaseManagementEntry')) {
      recorder.requestFailures.splice(i, 1);
    }
  }
}

const ADD_BILLING = 'input[type="button"][value="add billing"]';

async function workflow(s) {
  const { patient, provider, context, config, recorder } = s;
  const contextPath = new URL(config.baseUrl).pathname.replace(/\/+$/, '');

  await s.step('The add billing button opens the billing route with the context path exactly once', async () => {
    const chart = await s.chart();
    const mark = recorder.requestFailures.length;
    let chartReloads = 0;
    chart.on('load', () => { chartReloads++; });
    // Opened from the chart window, as the classic editor's own entry points do: its init and unload
    // handlers read window.opener, and a page opened by direct navigation raises uncaught TypeErrors.
    const editorUrl = h.appUrl(config.baseUrl, `/CaseManagementEntry?${new URLSearchParams({
      method: 'edit', note_edit: 'new', from: 'casemgmt', demographicNo: patient, providerNo: provider })}`);
    const [editor] = await Promise.all([
      context.waitForEvent('page', { timeout: 20000 }),
      chart.evaluate(url => { window.open(url, '_blank'); }, editorUrl),
    ]);
    await editor.waitForLoadState('domcontentloaded');
    const button = editor.locator(ADD_BILLING).first();
    await button.waitFor({ state: 'visible', timeout: 30000 });
    const billing = await s.popup(editor, button, 'classic add billing');
    try {
      await billing.waitForLoadState('domcontentloaded');
      const opened = new URL(billing.url());
      h.assert(opened.pathname === `${contextPath}/billing`,
        `The billing window opened ${opened.pathname}, not ${contextPath}/billing`);
      h.assert(opened.searchParams.get('demographic_no') === patient,
        'The billing window is not for the owned patient');
      h.assert(opened.searchParams.getAll('demographic_no').length === 1,
        'The billing window names more than one patient');
      // The route must also be served: the doubled address was an application 404.
      const served = await context.request.get(opened.href, { failOnStatusCode: false, maxRedirects: 5 });
      h.assert(served.status() === 200, `The billing address answered ${served.status()}, not 200`);
    } finally {
      if (!billing.isClosed()) await billing.close().catch(() => {});
      if (!editor.isClosed()) await editor.close().catch(() => {});
      // The editor reloads the chart on close; let that settle before judging the page's failures.
      await chart.waitForLoadState('load').catch(() => {});
      await chart.waitForTimeout(2000);
      h.assert(chartReloads > 0, 'Closing the classic editor did not reload the chart window that opened it');
      consumeUnloadBeacons(recorder, mark);
    }
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('classic-note-editor-billing-link', workflow, { openPatient: true });
