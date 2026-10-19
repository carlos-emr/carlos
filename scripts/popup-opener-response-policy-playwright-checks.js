#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/* Application-wide COOP coverage for static pages, JSP forwards, redirects,
 * error responses and early authentication rejection. Uses an owned patient
 * only to open an unsaved form; the workflow harness removes its fixture. */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

async function workflow(s) {
  const routes = [
    ['/admin/picker.html', 200],
    [`/form/formrourke2017complete?demographic_no=${s.patient}&formId=0`, 200],
    ['/popup-policy-missing-page', 404],
    ['/ws/popup-policy-missing-resource', 404],
  ];
  for (const [route, expectedStatus] of routes) {
    await s.step(`same-origin policy is present on ${route.split('?')[0]}`, async () => {
      const response = await s.context.request.get(h.appUrl(s.config.baseUrl, route), { maxRedirects: 0 });
      h.assert(response.status() === expectedStatus,
        `${route.split('?')[0]} answered HTTP ${response.status()} instead of ${expectedStatus}`);
      h.assert(response.headers()['cross-origin-opener-policy'] === 'same-origin',
        `${route.split('?')[0]} omitted the common opener policy`);
    });
  }
  await s.step('the unauthenticated redirect retains the same opener policy', async () => {
    const anonymous = await s.context.browser().newContext({ ignoreHTTPSErrors: s.config.ignoreHTTPSErrors === true });
    try {
      const response = await anonymous.request.get(h.appUrl(s.config.baseUrl, `/demographic/DemographicEdit?demographic_no=${s.patient}`),
        { maxRedirects: 0 });
      h.assert(response.status() >= 300 && response.status() < 400, 'The anonymous protected route did not redirect to login');
      h.assert(response.headers()['cross-origin-opener-policy'] === 'same-origin',
        'The authentication redirect omitted the common opener policy');
    } finally {
      await anonymous.close();
    }
  });
}

if (require.main === module) runWorkflow('popup-opener-response-policy', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
