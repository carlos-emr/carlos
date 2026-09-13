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
 * Can someone with no session reach what a clinician reaches?
 *
 * WHY THIS IS THE ONE WORTH BEING SURE OF. Every other check in this suite asks
 * whether the product works. This one asks whether an unauthenticated request
 * can obtain patient data, which is the failure that ends a deployment rather
 * than inconveniencing it. The suite's coverage of it was one route:
 * logout-session-invalidation re-requests /provider/providercontrol after
 * logging out. Everything else -- the Administration panel, the Master Record
 * and everything they link to -- had none.
 *
 * WHERE THE ROUTES COME FROM, and why that matters. They are not a list, and not
 * a sweep of struts-*.xml. The check logs in, opens the surfaces a clinician
 * works from, and CATALOGUES WHAT THEY OFFER -- the same engine the audit checks
 * use. So the question it answers is the real one: "everything a clinician can
 * reach from here, can a stranger reach it too?" A hand-written list would
 * answer a question about the list.
 *
 * A typed address is the point HERE, unlike everywhere else in this suite: the
 * attack being modelled is somebody pasting a URL without a session. The URLs
 * are still the application's own, taken from its own markup.
 *
 * WHAT COUNTS AS REFUSED. LoginFilter answers an unauthenticated request with
 * 302 to /logoutPage, so that is the expected outcome; 401 and 403 are accepted
 * as refusals too. A 200 is a failure -- and the check then looks for the
 * patient's surname in the body and says so separately, because "reachable" and
 * "returned a patient's name" are different sizes of problem.
 *
 * PHI HANDLING. The surname is read from the authenticated session and used only
 * as a needle. It is never printed: a failure says "the response contains the
 * patient's surname", never the surname. Same rule as the rest of the suite.
 *
 * READ-ONLY, twice over: the authenticated half only catalogues, and the
 * anonymous half only issues GETs that are expected to be refused.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:anonymous-access-refused-playwright
 *
 * Optional environment (the common contract is in lib/playwright-harness.js):
 *   ANON_ROUTE_LIMIT=60          how many catalogued routes to probe
 *   ANON_SEARCH=FAKE-            surname prefix used to reach a patient
 *   ANON_DEMOGRAPHIC_NO=2        which patient's Master Record to catalogue
 *   ANON_TIMEOUT_MS=20000        per-request allowance
 *
 * IMPLEMENTS: coverage plan section 2.2, the authorisation half.
 * App defects this finds are recorded in docs/ui-tests/app-findings-log.md.
 */

const {
  assert, createRecorder, launchBrowser, login, newContext, readConfig, runCheck,
} = require('./lib/playwright-harness');
const { clickOpensPopup } = require('./lib/playwright-ui');
const { catalogueLinks, dedupe } = require('./lib/playwright-link-audit');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');

/** Statuses that mean "you are not getting this without a session". */
const REFUSED_STATUSES = [401, 403];

/** Where LoginFilter sends an unauthenticated request. */
const LOGIN_REDIRECT = /\/(logoutPage|login|index)(\?|$)/;

/**
 * Routes never probed anonymously, with a reason.
 *
 * These are not exceptions to the rule -- they are pages the rule does not
 * cover, because they are the login surface itself and are SUPPOSED to answer
 * an anonymous request.
 */
const NOT_PROTECTED = [
  { match: /\/(logoutPage|logout|login|index|loginfailed|loginResource)(\?|$)/, reason: 'the login surface itself' },
  { match: /\/(images|css|js|library|share\/javascript|fonts)\//, reason: 'a static asset, served before any filter' },
];

/** Absolute, same-application URL for a catalogued item, or null. */
function resolveRoute(item, baseUrl) {
  const raw = item.href || item.route;
  if (!raw) {
    return null;
  }
  let url;
  try {
    url = new URL(raw, baseUrl);
  } catch {
    return null;
  }
  const base = new URL(baseUrl);
  if (url.origin !== base.origin) {
    return null;
  }
  // Only the application's own context path: an absolute link to somewhere else
  // on the host is not this application's to refuse.
  const prefix = base.pathname.replace(/\/$/, '');
  if (prefix && !url.pathname.startsWith(`${prefix}/`)) {
    return null;
  }
  return url.toString();
}

/** Catalogue what a clinician can reach, from the surfaces they work from. */
async function catalogueReachable(context, schedulePage, recorder, options) {
  const { searchTerm, preferredDemographicNo, timeout, baseUrl } = options;
  const found = [];

  const adminControl = schedulePage.locator('#admin-panel');
  if (await adminControl.count() > 0) {
    const admin = await clickOpensPopup(schedulePage, adminControl, {
      context, label: 'administration', recorder, timeout,
    });
    found.push(...dedupe(await catalogueLinks(admin)));
    await admin.close().catch(() => {});
  }

  const { masterPage } = await openMasterRecord(context, schedulePage, recorder, {
    searchTerm, preferredDemographicNo, timeout,
  });
  found.push(...dedupe(await catalogueLinks(masterPage)));

  // The needle, read from the authenticated page and never printed.
  const surname = await masterPage.locator('[name="last_name"]').first().inputValue().catch(() => '');

  const routes = [];
  const seen = new Set();
  for (const item of found) {
    const url = resolveRoute(item, baseUrl);
    if (!url || seen.has(url)) {
      continue;
    }
    const exempt = NOT_PROTECTED.find((rule) => rule.match.test(url));
    if (exempt) {
      continue;
    }
    seen.add(url);
    routes.push({ url, text: item.text });
  }
  return { routes, surname, masterPage };
}

/** Classify one anonymous response. */
function verdictFor(route, status, location, body, surname) {
  if (REFUSED_STATUSES.includes(status)) {
    return null;
  }
  if (status >= 300 && status < 400) {
    if (LOGIN_REDIRECT.test(String(location || ''))) {
      return null;
    }
    return `${route.url} redirected a session-less request to ${location || '(no Location header)'} `
      + 'rather than to the login surface';
  }
  if (status === 404) {
    // Not a finding about authentication: nothing was served either way. It IS
    // counted separately by the caller, because a run where everything 404s has
    // proved nothing and must not report success.
    return 'NOT_FOUND';
  }
  if (status >= 500) {
    return `${route.url} answered HTTP ${status} to a session-less request. It should have been refused before `
      + 'anything ran; reaching an exception means the request got past the authentication gate';
  }

  // A 200. The size of the problem depends on what came back.
  const leaksPatient = surname && surname.trim().length >= 3
    && body.toLowerCase().includes(surname.trim().toLowerCase());
  if (leaksPatient) {
    return `${route.url} answered HTTP 200 to a session-less request AND the response contains the patient's `
      + 'surname. This is patient data served to an unauthenticated caller.';
  }
  return `${route.url} answered HTTP 200 to a session-less request (${body.length} bytes). LoginFilter should `
    + 'have redirected it to /logoutPage.';
}

async function main() {
  const config = readConfig();
  const searchTerm = process.env.ANON_SEARCH || 'FAKE-';
  const preferredDemographicNo = process.env.ANON_DEMOGRAPHIC_NO || '2';
  const limit = Number(process.env.ANON_ROUTE_LIMIT || '60');
  const timeout = Number(process.env.ANON_TIMEOUT_MS || '20000');

  const recorder = createRecorder();
  const browser = await launchBrowser(config);
  try {
    const authed = await newContext(browser, config);
    const schedulePage = await login(authed, config, recorder);
    const { routes, surname } = await catalogueReachable(authed, schedulePage, recorder, {
      searchTerm, preferredDemographicNo, timeout, baseUrl: config.baseUrl,
    });
    assert(routes.length >= 20,
      `Only ${routes.length} route(s) were catalogued from the Administration panel and the Master Record; `
      + 'the catalogue step is probably broken rather than the application having that few pages');
    assert(surname && surname.trim().length >= 3,
      'Could not read the patient surname from the Master Record, so a response leaking it could not be '
      + 'recognised. That needle is the difference between "reachable" and "served patient data".');

    // A SEPARATE context: no cookies, no session, nothing carried over. Sharing
    // the authenticated one would test nothing at all.
    const anonymous = await browser.newContext({ ignoreHTTPSErrors: config.ignoreHTTPSErrors === true });
    const probed = [];
    const notFound = [];
    const failures = [];
    try {
      for (const route of routes.slice(0, limit)) {
        let response;
        try {
          response = await anonymous.request.get(route.url, { timeout, maxRedirects: 0 });
        } catch (error) {
          failures.push(`${route.url}: the request itself failed -- ${String(error.message).split('\n')[0]}`);
          continue;
        }
        const status = response.status();
        const body = status === 200 ? await response.text().catch(() => '') : '';
        const verdict = verdictFor(route, status, response.headers().location, body, surname);
        if (verdict === 'NOT_FOUND') {
          notFound.push(route.url);
        } else if (verdict) {
          failures.push(verdict);
        } else {
          probed.push(route.url);
        }
      }
    } finally {
      await anonymous.close().catch(() => {});
    }

    assert(failures.length === 0,
      `${failures.length} of ${probed.length + failures.length} route(s) a clinician reaches are not refused to a `
      + `session-less caller:\n    - ${failures.join('\n    - ')}`);

    // THE VACUOUS CASE. A route that 404s anonymously proves nothing about
    // authentication, so if most of them do, the URLs this check built are wrong
    // and "everything was refused" is a result about the check, not the
    // application. That is the failure this whole suite keeps having to fix:
    // a guard that runs, finds nothing, and passes.
    const attempted = probed.length + notFound.length;
    assert(probed.length * 2 > attempted,
      `${notFound.length} of ${attempted} probed route(s) answered 404 to a session-less request, so most of this `
      + 'run proved nothing about authentication. The URLs were built from the catalogued links, so they are '
      + `probably wrong rather than the routes being gone: ${notFound.slice(0, 5).join(', ')}`);

    console.log(`  ${probed.length} catalogued route(s) refused a session-less request`
      + `${notFound.length ? `, ${notFound.length} answered 404 and proved nothing` : ''}`);
    return { probed: probed.length, notFound: notFound.length, catalogued: routes.length };
  } finally {
    await browser.close().catch(() => {});
  }
}

if (require.main === module) {
  runCheck({ name: 'anonymous-access-refused', run: main });
}

module.exports = {
  LOGIN_REDIRECT, NOT_PROTECTED, REFUSED_STATUSES, main, resolveRoute, verdictFor,
};
