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
 * User path: Schedule > Search > Master Record > E-Chart; the note editor shows the "add billing"
 * button (from=casemgmt, billing role, not a TORONTO_RFQ install). The check reads the address the
 * button opens, follows it in a real popup and compares the clicked link with the control routes.
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

const ADD_BILLING = 'input[type="button"][value="add billing"]';

async function workflow(s) {
  const { patient, context, config } = s;
  const contextPath = new URL(config.baseUrl).pathname.replace(/\/+$/, '');

  await s.step('The add billing button opens the billing route with the context path exactly once', async () => {
    const chart = await s.chart();
    const button = chart.locator(ADD_BILLING).first();
    await button.waitFor({ state: 'visible', timeout: 30000 });
    const billing = await s.popup(chart, button, 'classic add billing');
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
    }
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('classic-note-editor-billing-link', workflow, { openPatient: true });
