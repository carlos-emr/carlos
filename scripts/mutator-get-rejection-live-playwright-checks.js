#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * The live twin of MutatorActionGetRejectionContractUnitTest: does the DEPLOYED
 * application refuse a GET on every action that mutates?
 *
 * WHY A LIVE TWIN IS NOT REDUNDANT. The unit contract drives each action class
 * directly with a MockHttpServletRequest. That proves the code in the class is
 * right, and it is the fast feedback a build needs -- but it is blind to
 * everything between the browser and that method: the Struts action mapping, the
 * interceptor stack, the filter chain, and whether the route is even registered.
 * An action whose `execute()` rejects GET perfectly is still exploitable if its
 * route resolves somewhere else, or if a result mapping answers before the
 * method check runs. Only a request through the real stack can tell.
 *
 * WHY THIS MATTERS AT ALL. A mutator reachable by GET is a one-click CSRF: no
 * token is checked on a GET, so a crafted <img src> or a link in an email
 * performs the action with the clinician's own session. It is also the class of
 * bug a browser never shows you, because nothing renders.
 *
 * WHAT IT DRIVES, AND WHY IT DERIVES IT. The route list is read out of the Java
 * contract's own `unconditionalMutators()` block and resolved through the
 * modular Struts config (see lib/mutator-routes.js). CLAUDE.md makes registering
 * a new mutator in that contract a build requirement, so deriving the list means
 * this check cannot cover less than the unit contract does. A hand-written list
 * would fall behind the first time somebody added an action -- exactly the drift
 * the contract exists to prevent, reintroduced one layer up.
 *
 * THE ASSERTION IS 405, and it is unambiguous. Every registered mutator rejects
 * with `response.sendError(SC_METHOD_NOT_ALLOWED, "POST required")` before any
 * side effect; 170 files in this repository do it that way. So a 200 means the
 * action ran, a 302 means it redirected instead of refusing, and a 500 means it
 * got far enough to throw. None of those is the contract.
 *
 * READ-ONLY, AND DELIBERATELY UNDER-EQUIPPED. Each GET is sent with NO
 * parameters. If the contract holds, nothing runs; if it does not, an action
 * that reached its body would find no demographic_no, no appointment_no and no
 * form data, so the check detects the hole without handing it the data a real
 * mutation would need.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:mutator-get-rejection-live-playwright
 *
 * Optional environment (the common contract is in lib/playwright-harness.js):
 *   MUTATOR_GET_TIMEOUT_MS=15000   per-route allowance
 *
 * IMPLEMENTS: coverage plan section 2.2, `mutator-get-rejection-live`
 * (docs/ui-tests/playwright-coverage-plan-2026.08.md). App defects this check
 * finds are recorded in docs/ui-tests/app-findings-log.md, not worked around.
 */

const {
  assert, createRecorder, launchBrowser, login, newContext, readConfig, runCheck,
} = require('./lib/playwright-harness');
const { mutatorRoutes } = require('./lib/mutator-routes');

/** What the contract requires, and what each other answer would mean. */
const EXPECTED_STATUS = 405;

function diagnose(status) {
  if (status === 200) {
    return 'the action RAN on a GET. A mutator reachable by GET is a one-click CSRF: no token is checked on a '
      + 'GET, so a crafted <img src> or a link in an email performs it with the clinician\'s own session';
  }
  if (status >= 300 && status < 400) {
    return 'the action redirected instead of refusing, so the method check did not run before the result did';
  }
  if (status === 404) {
    return 'the route did not resolve at all. Either the mapping was removed while the contract still lists the '
      + 'class, or it is registered under a different name than the Struts config declares';
  }
  if (status === 401 || status === 403) {
    return 'the request was refused, but for authentication rather than method. The session this check drives is '
      + 'a logged-in provider, so this points at the login step rather than the contract';
  }
  if (status >= 500) {
    return 'the action got far enough into its body to throw, which is past the point the contract requires it to '
      + 'have stopped';
  }
  return 'this is not the 405 the GET/HEAD rejection contract requires';
}

/**
 * Routes ordered so a hole cannot take the session down with it.
 *
 * `logout` is on the list and is supposed to answer 405. If it does not, the GET
 * ends the session, and every route after it would report 401/403 -- one real
 * finding turning into thirty misleading ones. It goes last, so the rest are
 * already measured.
 */
function orderedRoutes(routes) {
  const endsSession = (entry) => /logout/i.test(entry.route) || /Logout/.test(entry.simpleName);
  return [...routes.filter((entry) => !endsSession(entry)), ...routes.filter(endsSession)];
}

async function main() {
  const config = readConfig();
  const timeout = Number(process.env.MUTATOR_GET_TIMEOUT_MS || '15000');

  const { routes, unmapped } = mutatorRoutes();
  // Not a skip: an unresolvable class means the contract and the Struts config
  // disagree, and the live check would silently stop covering that action.
  assert(unmapped.length === 0,
    `${unmapped.length} action(s) are registered in the GET-rejection contract but have no Struts route: `
    + `${unmapped.join(', ')}. Either the route was removed and the contract entry is stale, or the config `
    + 'declares it under a name this resolver does not recognise.');
  assert(routes.length >= 20,
    `Only ${routes.length} mutator route(s) were derived from the contract; it registers far more than that, `
    + 'so the parser is probably broken rather than the contract having shrunk');

  const recorder = createRecorder();
  const browser = await launchBrowser(config);
  try {
    const context = await newContext(browser, config);
    // A real logged-in session, established through the UI: the point is to test
    // the method check, not the authentication one, and an anonymous request
    // would be refused before it ever reached the action.
    await login(context, config, recorder);

    const base = String(config.baseUrl).replace(/\/$/, '');
    const failures = [];
    const refused = [];
    for (const entry of orderedRoutes(routes)) {
      const url = `${base}/${entry.route}`;
      let status;
      try {
        const response = await context.request.get(url, { timeout, maxRedirects: 0 });
        status = response.status();
      } catch (error) {
        failures.push(`${entry.route} (${entry.simpleName}): the request itself failed -- ${String(error.message).split('\n')[0]}`);
        continue;
      }
      if (status === EXPECTED_STATUS) {
        refused.push(entry.route);
        continue;
      }
      failures.push(
        `${entry.route} (${entry.simpleName}) answered HTTP ${status} to a GET, not ${EXPECTED_STATUS}: ${diagnose(status)}`,
      );
    }

    assert(failures.length === 0,
      `${failures.length} of ${routes.length} mutator route(s) do not refuse a GET:\n    - ${failures.join('\n    - ')}`);
    console.log(`  ${refused.length} mutator route(s) refused a GET with ${EXPECTED_STATUS}`);
    return { refused: refused.length, routes: routes.length };
  } finally {
    await browser.close().catch(() => {});
  }
}

if (require.main === module) {
  runCheck({ name: 'mutator-get-rejection-live', run: main });
}

module.exports = { EXPECTED_STATUS, diagnose, main, orderedRoutes };
