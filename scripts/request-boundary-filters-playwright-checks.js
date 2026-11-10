#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Request-boundary filters around Struts actions (issue #5305), checked on a deployed install.
 *
 * web.xml now maps DbConnectionFilter (legacy JDBC cleanup) as the first application filter and
 * UserActivityFilter (inactivity policy) between LoginFilter and Struts; before, both sat after
 * struts2-execute and never ran for a Struts action. This check drives the deployment through
 * the paths the move changed. The filter order itself, exactly-once cleanup and the inactivity
 * redirect (which needs a two-hour-old session) are proven in
 * RequestBoundaryFilterOrderIntegrationTest; this is the end-to-end regression on the real stack.
 *
 * User path: login page ▸ Schedule; legacy Annual form opened for the owned patient
 * (form/forwardshortcutname, a Struts action whose JSP reads through legacy JDBC); Schedule top
 * bar ▸ Logout.
 *
 * Asserted:
 *  1. anonymous requests to a public Struts page, the session heartbeat and a static asset set
 *     no JSESSIONID: the activity filter, now in front of every request, never creates a session;
 *  2. a signed-in session's heartbeat answers JSON {"valid":true} with and without
 *     autoRefresh=true, never a redirect, and the session stays signed in;
 *  3. opening the legacy Annual form repeatedly renders it each time and leaves the number of
 *     MariaDB connections on the CARLOS database where it was (connections are returned at the
 *     end of every Struts request);
 *  4. Logout loads /logoutPage once and posts /logout once, landing on the login page: neither
 *     logout route is redirected back into the other.
 *
 * Fixtures: the workflow's FAKE patient only (created and removed by lib/workflow-session.js).
 * Opening the form without saving writes no form row.
 */
const { request: playwrightRequest } = require('playwright');
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

const TIMEOUT = 20000;
const FORM_NAME = 'Annual';
const FORM_LOADS = 20;
// A request that left its connection open would add one per load. Pooled connections and the
// scheduler's short-lived ones make small moves normal; a third of the loads is a clear leak.
const CONNECTION_GROWTH_LIMIT = Math.floor(FORM_LOADS / 3);
const HEARTBEAT = '/status/SessionHeartbeat';

function sessionCookies(response) {
  return response.headersArray()
    .filter(header => header.name.toLowerCase() === 'set-cookie' && /^JSESSIONID=/i.test(header.value))
    .map(header => header.value.split(';')[0].replace(/=.*/, '=<redacted>'));
}

async function workflow(s) {
  const { sql, config, context, schedule, patient } = s;
  const connectionsToCarlos = () => Number(sql.value(
    'SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB=DATABASE() AND ID<>CONNECTION_ID()'));

  await s.step('anonymous requests to a public action, the heartbeat and a static asset mint no session', async () => {
    const anonymous = await playwrightRequest.newContext({ ignoreHTTPSErrors: config.ignoreHTTPSErrors });
    try {
      for (const route of ['/logoutPage', `${HEARTBEAT}?autoRefresh=true`, '/images/favicon.ico']) {
        const response = await anonymous.get(h.appUrl(config.baseUrl, route), { maxRedirects: 0, timeout: TIMEOUT });
        h.assert(response.status() < 400, `Anonymous GET ${route} answered HTTP ${response.status()}`);
        const minted = sessionCookies(response);
        h.assert(minted.length === 0, `Anonymous GET ${route} created a session (${minted.join(', ')})`);
        if (route.startsWith(HEARTBEAT)) {
          const body = await response.json().catch(() => null);
          h.assert(body && body.valid === false, `The anonymous heartbeat did not answer {"valid":false}`);
        }
      }
    } finally {
      await anonymous.dispose();
    }
  });

  await s.step('a signed-in heartbeat answers JSON, with or without autoRefresh, and never redirects', async () => {
    for (const route of [`${HEARTBEAT}?autoRefresh=true`, HEARTBEAT]) {
      const response = await context.request.get(h.appUrl(config.baseUrl, route), { maxRedirects: 0, timeout: TIMEOUT });
      h.assert(response.status() === 200, `GET ${route} answered HTTP ${response.status()} for a signed-in session`);
      const body = await response.json().catch(() => null);
      h.assert(body && body.valid === true, `GET ${route} did not report a valid session`);
    }
  });

  await s.step(`opening the legacy ${FORM_NAME} form ${FORM_LOADS} times returns its database connections`, async () => {
    const route = `/form/forwardshortcutname?formname=${FORM_NAME}&demographic_no=${patient}`;
    const page = await context.newPage();
    try {
      // One warm-up load lets the pool open what the page normally needs before the baseline.
      await h.gotoApp(page, config.baseUrl, route);
      await h.assertNotErrorPage(page, `${FORM_NAME} form`);
      const before = connectionsToCarlos();
      h.assert(before >= 1, 'No CARLOS connection is visible in PROCESSLIST; MYSQL_USER needs the PROCESS privilege');
      for (let i = 0; i < FORM_LOADS; i++) {
        const response = await h.gotoApp(page, config.baseUrl, route);
        h.assert(response && response.status() === 200, `Load ${i + 1} of the ${FORM_NAME} form answered HTTP ${response && response.status()}`);
        await h.assertNotErrorPage(page, `${FORM_NAME} form (load ${i + 1})`);
      }
      const after = connectionsToCarlos();
      h.assert(after - before <= CONNECTION_GROWTH_LIMIT,
        `CARLOS held ${before} database connections before ${FORM_LOADS} form loads and ${after} after`);
    } finally {
      await page.close();
    }
  });

  await s.step('Logout passes through /logoutPage and /logout once each and lands on the login page', async () => {
    const seen = [];
    context.on('request', req => {
      const path = h.pathOnly(req.url()).replace(/^.*?(\/logoutPage|\/logout)$/, '$1');
      if (path === '/logoutPage' || path === '/logout') seen.push(`${req.method()} ${path}`);
    });
    await schedule.locator('#logoutButton').click();
    await schedule.waitForURL(/\/index(\?|$)/, { timeout: 30000 });
    await schedule.locator('#username').waitFor({ timeout: TIMEOUT });
    // A loop would keep alternating between the two routes; give it time to show.
    await new Promise(resolve => setTimeout(resolve, 3000));
    const pageLoads = seen.filter(entry => entry.endsWith('/logoutPage')).length;
    const logoutPosts = seen.filter(entry => entry === 'POST /logout').length;
    h.assert(pageLoads === 1 && logoutPosts === 1,
      `Logout requested ${seen.join(', ')}; expected one /logoutPage and one POST /logout`);
    const heartbeat = await context.request.get(h.appUrl(config.baseUrl, HEARTBEAT), { maxRedirects: 0, timeout: TIMEOUT });
    const body = await heartbeat.json().catch(() => null);
    h.assert(body && body.valid === false, 'The session survived Logout');
  });
}

if (require.main === module) runWorkflow('request-boundary-filters', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
