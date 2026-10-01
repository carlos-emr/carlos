#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Role matrix for read routes (coverage-plan area: authorization, "authz-read" sweep).
 *
 * User path: log in through the login form as a login holding ONE seeded role (er_clerk = no sec
 * objects, receptionist, nurse, doctor), then request read/view/search/list/report routes that
 * role does not hold the sec object for. The routes are the Struts gates reached from the schedule
 * top bar, the Administration panel, the Master Record and the E-Chart (scripts/lib/authz-read-routes.js).
 *
 * Asserted: for every pinned (role, route) pair the application answers 403 to GET and to HEAD
 * ("Security Exception"), never content; a positive control per role proves the session is alive
 * and the 403 is an authorization decision. A route that starts serving a role lacking its object
 * fails the pinning step with the route name.
 *
 * Fixtures: four throwaway logins (own provider rows, copy of the test login's credential) and one
 * owned patient; removed and verified by authzReadFixture.cleanup(). Reads only; nothing mutates.
 * Implements the "authz-read" sweep of the wave-6 plan (role-matrix, denied-by-design pairs).
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { authzReadFixture } = require('./lib/authz-read-fixture');
const { probe, classify, urlFor, signIn } = require('./lib/authz-read-probe');
const { DENIED, ERROR_PAGE_REFUSALS, patientQuery } = require('./lib/authz-read-routes');

// A route each role may open, proving the login is alive (a dead session would also "refuse").
const CONTROL = {
  er_clerk: 'location',
  receptionist: 'demographic/DemographicEdit',
  nurse: 'demographic/DemographicEdit',
  doctor: 'tickler/ViewTicklerMain',
};

async function workflow(s) {
  const { sql, marker, provider, config } = s;
  const fixture = authzReadFixture({ sql, marker, provider, testUser: config.testUser });
  s.cleanup(() => fixture.cleanup());
  const sessions = {};
  const query = patientQuery(s.patient);

  await s.step('four throwaway logins each hold exactly one seeded role and sign in through the login form', async () => {
    h.assert(fixture.roleHoldsNothing('er_clerk'), 'er_clerk is expected to hold no sec object');
    h.assert(fixture.rolePrivileges('receptionist').includes('_appointment:x')
      && !fixture.rolePrivileges('receptionist').some(entry => /^_(eChart|rx|lab|edoc)/.test(entry)),
    'receptionist no longer matches the seeded role matrix');
    for (const role of Object.keys(DENIED)) {
      const login = fixture.addLogin(role);
      sessions[role] = await signIn(s, login);
    }
  });

  await s.step('a positive control per role is served, so the refusals below are authorization decisions', async () => {
    for (const [role, route] of Object.entries(CONTROL)) {
      const result = await probe(sessions[role].context, urlFor(config, `${route}?${query}`));
      h.assert(classify(result) === 'served', `${role} control route ${route} was not served (status ${result.status})`);
    }
  });

  for (const role of Object.keys(DENIED)) {
    await s.step(`${role}: ${DENIED[role].length} read routes outside its sec objects answer 403 to GET`, async () => {
      const wrong = [];
      for (const route of DENIED[role]) {
        const result = await probe(sessions[role].context, urlFor(config, `${route}?${query}`));
        if (result.status !== 403) wrong.push(`${route} -> ${result.status}/${classify(result)}`);
      }
      h.assert(!wrong.length, `${role} was not refused on: ${wrong.join('; ')}`);
    });
  }

  await s.step('the same pairs are refused for HEAD too (403, or 405 where the route is GET-only; a container runs doGet for HEAD)', async () => {
    const wrong = [];
    for (const role of Object.keys(DENIED)) {
      for (const route of DENIED[role]) {
        const result = await probe(sessions[role].context, urlFor(config, `${route}?${query}`), { method: 'HEAD' });
        if (![403, 405].includes(result.status)) wrong.push(`${role} ${route} -> ${result.status}`);
      }
    }
    h.assert(!wrong.length, `HEAD was not refused on: ${wrong.join('; ')}`);
  });

  // Evidence gathered before the logins are torn down; asserted in the last step.
  const errorPages = [];
  let tested = 0;
  await s.step('controls for the error-page refusals: the full-privilege login is served the same URLs', async () => {
    const served = [];
    for (const route of ERROR_PAGE_REFUSALS) {
      const result = await probe(s.context, urlFor(config, `${route}?${query}`));
      // A route that answers the control login with an empty body (a logging endpoint) proves nothing.
      if (classify(result) === 'served') served.push(route);
    }
    h.assert(served.length >= ERROR_PAGE_REFUSALS.length * 0.8, `Only ${served.length} of ${ERROR_PAGE_REFUSALS.length} control routes were served`);
    tested = served.length;
    for (const route of served) {
      const result = await probe(sessions.er_clerk.context, urlFor(config, `${route}?${query}`));
      if (result.status !== 403) errorPages.push({ route, status: result.status });
    }
  });

  await s.step('every owned login and its audit rows are removed', async () => {
    for (const { context } of Object.values(sessions)) await context.close();
    fixture.cleanup();
    h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE last_name=${h.sqlString(marker)}`) === '0',
      'authz-read provider rows remain');
  });

  await s.step('a refusal is a 403, never an HTTP 500 "unexpected error" page (lists the packages whose gates fail that way)', async () => {
    const byPackage = {};
    for (const { route } of errorPages) byPackage[route.split('/')[0]] = (byPackage[route.split('/')[0]] || 0) + 1;
    h.assert(!errorPages.length, `${errorPages.length} of ${tested} routes answered a login with no sec object with HTTP 500 instead of 403 `
      + `(${Object.entries(byPackage).map(([name, count]) => `${name} x${count}`).join(', ')}); first: `
      + errorPages.slice(0, 4).map(entry => `${entry.route} -> ${entry.status}`).join(', '));
  });
}

if (require.main === module) runWorkflow('authz-read-role-matrix', workflow, { openMaster: false });
module.exports = { workflow };
