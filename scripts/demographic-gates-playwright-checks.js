#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Deliberate unauthenticated route probes complement the UI-driven positive workflows.
const h = require('./lib/playwright-harness');
const { REFUSED_STATUSES, contextPathOf, isLoginSurface } = require('./anonymous-access-refused-playwright-checks');
const ROUTES = [
  'ViewContact', 'ViewContactSearch', 'ViewProContact', 'ViewProContactSearch',
  'ViewProfessionalSpecialistSearch', 'ViewSearch', 'ViewAddDemoToPatientSet',
  'ViewAddNewDemographicSwipe', 'ViewZdemographicSwipe',
  'ViewZdemographicFullTitleSearch', 'ViewDemographicAddARecordHtm', 'ViewDemographicAudit',
  'ViewDemographicCohort', 'ViewDemographicEditDemographicJs', 'ViewDemographicLabelPrintSetting',
  'ViewDemographicPrintDemographic', 'ViewDemographicSearch2ReportResults',
  'ViewDisplayFirstNationsModule', 'ViewManageFirstNationsModule', 'ViewDisplayHealthCareTeam',
  'ViewManageHealthCareTeam', 'ViewEnrollmentHistory', 'ViewPrintEnvelope', 'ViewPrintAddressLabel',
  'ViewPrintClientLabLabel', 'ViewPrintDemoChartLabel', 'ViewPrintDemoLabel',
];
// Same login-surface rule as anonymous-access-refused: an exact context-root action,
// never "any same-origin path ending in /index", which would pass a redirect into
// a protected page such as /administration/index.
function assertProtected(status, location, baseUrl) {
  if (REFUSED_STATUSES.includes(status)) return;
  h.assert(status >= 300 && status < 400 && location, 'Unauthenticated demographic route did not reject or redirect');
  const target = new URL(location, baseUrl);
  h.assert(target.origin === new URL(baseUrl).origin && isLoginSurface(target.href, contextPathOf(baseUrl)),
    'Unauthenticated demographic route redirected somewhere other than login');
}
async function main() {
  const config = h.readConfig();
  const browser = await h.launchBrowser(config);
  try {
    for (const route of ROUTES) {
      const context = await h.newContext(browser, config);
      try {
        const response = await context.request.get(h.appUrl(config.baseUrl, `/demographic/${route}`), { maxRedirects: 0 });
        assertProtected(response.status(), response.headers().location, config.baseUrl);
        console.log(`  PASS anonymous gate: ${route}`);
      } finally { await context.close(); }
    }
    return { protectedRoutes: ROUTES.length };
  } finally { await browser.close(); }
}
if (require.main === module) h.runCheck({ name: 'demographic-gates', run: main });
module.exports = { ROUTES, assertProtected, main };
