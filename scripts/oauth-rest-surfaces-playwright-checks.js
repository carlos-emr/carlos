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
 * Are the OAuth web-service surfaces published on a deployed system, do they keep
 * their authentication boundaries, and is the session REST surface unchanged?
 *
 * WHY A DEPLOYED CHECK. Issue #3446: /ws/oauth and /ws/services answered CXF's
 * "No service was found." 404 on every packaged install, while every resource
 * unit test passed. OscarSpringContextLoader never read applicationContextREST.xml,
 * so the two JAX-RS servers were never created. Only a running deployment can show
 * that a server is published.
 *
 * WHAT IT DRIVES, in the order a third-party integrator meets it:
 *   1. Unauthenticated probes. /ws/oauth/initiate, /authorize and /token answer
 *      OAuth protocol errors (400), not 404, and /initiate refuses an unregistered
 *      consumer (401 invalid_consumer). /ws/services refuses a call without
 *      credentials and one with an unknown access token (401), and returns no
 *      patient data either way.
 *   2. The OAuth 1.0a handshake. A signed /initiate with an out-of-band callback
 *      returns a request token. The logged-in provider opens the consent page at
 *      /ws/oauth/authorize, as the integrator would send them there, and clicks
 *      Authorize, which shows the verifier. /token refuses a wrong verifier
 *      (401 invalid_verifier), exchanges the right one for an access token, and
 *      refuses a second exchange of the same request token.
 *   3. Signed data calls. /ws/services/oauth/info names the provider who
 *      authorized, and /ws/services/demographics/{id} returns that record. The same
 *      signed request sent again (same nonce) is refused, and so is the real access
 *      token signed with the wrong consumer secret (401).
 *   4. Neither surface takes the other's credential. A logged-in browser session
 *      alone is refused by /ws/services, and a signed access token alone by /ws/rs
 *      (401, no patient data).
 *   3a. Scopes are enforced by default (#4419). /initiate refuses a request with no
 *      scope or an unknown one (400 invalid_scope). The token, granted
 *      demographic.read, is refused (403 insufficient_scope) a read in another domain
 *      and a write in its own (DELETE of a demographic that does not exist, so a
 *      regression answers 404, never deletes). The consent page shows no
 *      "enforcement is off" warning. With EXPECT_OAUTH_MODE=legacy-restricted or
 *      legacy-full (the server's oauth.scope.enforcement.enabled=false, with
 *      oauth.scope.legacy.access unset or full), /initiate accepts a request with no
 *      scope, the consent page shows the matching warning, and the same probes
 *      answer 403 restricted_endpoint, or go through to the service, instead.
 *   3b. In every mode the always-blocked endpoints answer 403 blocked_endpoint, and
 *      the Cortico REST calls (PUT /demographics, POST
 *      /document/saveDocumentToDemographic, GET /demographics/{id}) reach their
 *      service: with an empty body the first two answer the service's own 400, so
 *      nothing is created. Under enforcement that takes a second token granted
 *      demographic.write and document.write; in the legacy modes a scopeless one.
 *   5. The session surface is unaffected. /ws/rs refuses an anonymous call and
 *      serves the logged-in browser. Its JSON dates stay epoch milliseconds: before
 *      the fix, loading applicationContextREST.xml as it was replaced the /ws/rs
 *      mapper (a shared bean id) and turned them into "yyyy-MM-dd" strings. SOAP
 *      still publishes its WSDL and still rejects an unauthenticated operation.
 *   6. Anonymous floods are bounded (#4429, #4438). Anonymous /ws/services
 *      refusals are a plain-text reason, not a CXF XMLFault naming a Java exception
 *      (which the sanitizing filter logged at ERROR). A burst of anonymous calls
 *      writes a bounded number of OAUTH_LOGIN_* audit rows, not one per call, and
 *      behind the packaged front door (EXPECT_FRONT_DOOR=true) some are answered 429.
 *
 * FIXTURE. The check inserts one ServiceClient row with a unique name, key and
 * secret, because the Administration > REST Clients page never shows a client's
 * secret. Through runCheck's cleanup hook, keyed by that client's unique consumer
 * key, it deletes the client and every request token, access token and consumed
 * nonce issued to it, whether the check passes or fails. The
 * OAUTH_LOGIN_* audit rows the interceptor writes are kept: they are the audit
 * trail. It needs MYSQL_* (assertsDatabase).
 *
 * PHI HANDLING. The demographic it reads is chosen by number from the demo
 * dataset. Its surname is used only as a needle that must NOT appear in refused
 * responses, and it is never printed. Failure messages carry statuses, never
 * response bodies from data routes.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:oauth-rest-surfaces-playwright
 *
 * Optional environment (the common contract is in lib/playwright-harness.js):
 *   OAUTH_DEMOGRAPHIC_NO=1   demographic to read through both surfaces. Default:
 *                            the lowest-numbered one with a patient status date.
 *   EXPECT_OAUTH_MODE=scoped the mode the server is configured for: scoped (the
 *                            default), legacy-restricted or legacy-full. The check
 *                            fails if the server behaves as another mode.
 */
const crypto = require('crypto');
const {
  SkipCheck, assert, assertNotErrorPage, assertStrictPage, createRecorder, createSqlRunner, insertId,
  launchBrowser, login, newContext, readConfig, runCheck, sqlString, wireStrictPage,
} = require('./lib/playwright-harness');

const CXF_NOT_PUBLISHED = 'No service was found';
// OAuthInterceptor.FailureAuditBudget.PER_ADDRESS_LIMIT plus its one suppression notice, for
// each of the (at most two) one-minute windows a burst can straddle.
const MAX_FLOOD_AUDIT_ROWS = 2 * (10 + 1);
const FLOOD_REQUESTS = 120;
// A demographic number the demo dataset does not use: the scope-refused DELETE probe targets it,
// so even a regression that let the call through could only answer 404.
const ABSENT_DEMOGRAPHIC_NO = 2147483646;
// Scopes the signed calls below need. Enforcement is on by default (#4419), so the token is
// limited to these; /ws/services/oauth/info is scope-exempt.
const REQUESTED_SCOPES = 'demographic.read provider.read';
// What the Cortico integration needs under enforcement (OAuthScopes' legacy allowlist, scoped).
const CORTICO_SCOPES = 'demographic.write document.write';
const OAUTH_MODES = ['scoped', 'legacy-restricted', 'legacy-full'];
const OAUTH_MODE = process.env.EXPECT_OAUTH_MODE || 'scoped';
assert(OAUTH_MODES.includes(OAUTH_MODE), `EXPECT_OAUTH_MODE must be one of ${OAUTH_MODES.join(', ')}`);
const SCOPED = OAUTH_MODE === 'scoped';

/** RFC 3986 percent-encoding, as OAuth 1.0a section 3.6 requires. */
function pct(value) {
  return encodeURIComponent(String(value))
    .replace(/[!'()*]/g, (c) => `%${c.charCodeAt(0).toString(16).toUpperCase()}`);
}

/**
 * The base string URI the server rebuilds (OAuth1SignatureVerifierImplementation):
 * lower-case scheme and host, default port dropped, path only.
 */
function signatureBaseUri(url) {
  const u = new URL(url);
  const scheme = u.protocol.replace(':', '').toLowerCase();
  const defaultPort = (scheme === 'https' && (u.port === '' || u.port === '443'))
    || (scheme === 'http' && (u.port === '' || u.port === '80'));
  return `${scheme}://${u.hostname.toLowerCase()}${defaultPort ? '' : `:${u.port}`}${u.pathname}`;
}

/**
 * The HMAC-SHA1 signature of a request (RFC 5849 section 3.4): the oauth_* parameters
 * plus the URL's query parameters, encoded, sorted by name then value, under
 * consumer secret & token secret. Pure, so oauth-rest-surfaces.test.js can pin it to a
 * published test vector without a deployment.
 */
function oauthSignature({ method, url, oauthParams, consumerSecret, tokenSecret }) {
  const params = [...Object.entries(oauthParams), ...new URL(url).searchParams.entries()]
    .map(([k, v]) => [pct(k), pct(v)])
    .sort(([ak, av], [bk, bv]) => (ak === bk ? (av < bv ? -1 : av > bv ? 1 : 0) : (ak < bk ? -1 : 1)));
  const base = [
    method.toUpperCase(),
    pct(signatureBaseUri(url)),
    pct(params.map(([k, v]) => `${k}=${v}`).join('&')),
  ].join('&');
  const key = `${pct(consumerSecret)}&${pct(tokenSecret || '')}`;
  // HMAC-SHA1 is the only HMAC method OAuth 1.0a defines (RFC 5849 section 3.4.2) and the
  // only one OAuth1SignatureVerifierImplementation verifies; SHA-1 collisions do not apply to HMAC.
  // nosemgrep: javascript.node-stdlib.cryptography.crypto-weak-algorithm.crypto-weak-algorithm
  return crypto.createHmac('sha1', key).update(base).digest('base64');
}

/**
 * Builds an HMAC-SHA1 signed OAuth 1.0a Authorization header (RFC 5849) with a fresh
 * nonce and the current timestamp.
 */
function oauthHeader({ method, url, consumerKey, consumerSecret, token, tokenSecret, extra = {} }) {
  const oauth = {
    oauth_consumer_key: consumerKey,
    oauth_nonce: crypto.randomBytes(16).toString('hex'),
    oauth_signature_method: 'HMAC-SHA1',
    oauth_timestamp: String(Math.floor(Date.now() / 1000)),
    oauth_version: '1.0',
    ...(token ? { oauth_token: token } : {}),
    ...extra,
  };
  const signature = oauthSignature({ method, url, oauthParams: oauth, consumerSecret, tokenSecret });
  return `OAuth ${Object.entries({ ...oauth, oauth_signature: signature })
    .map(([k, v]) => `${pct(k)}="${pct(v)}"`).join(', ')}`;
}

function formFields(body) {
  return Object.fromEntries(new URLSearchParams(body.trim()));
}

/** Fails with a message that names a missing JAX-RS server rather than a generic status. */
async function expectStatus(response, expected, label) {
  const status = response.status();
  const allowed = Array.isArray(expected) ? expected : [expected];
  if (allowed.includes(status)) return;
  const body = await response.text().catch(() => '');
  assert(!(status === 404 && body.includes(CXF_NOT_PUBLISHED)),
    `${label}: CXF answered 404 "${CXF_NOT_PUBLISHED}." The JAX-RS server for this path is not `
    + 'published, so applicationContextREST.xml was not loaded into the root context (issue #3446)');
  assert(false, `${label}: expected HTTP ${allowed.join(' or ')}, got ${status}`);
}

/**
 * Removes the check's client and everything issued to it, keyed by the unique consumer key so
 * the cleanup also runs when the fixture insert succeeded but its id was never read back. Runs
 * through runCheck's cleanup hook: a cleanup failure is reported alongside the original failure
 * instead of replacing it.
 */
async function cleanup(state) {
  try {
    if (state.browser) await state.browser.close();
  } finally {
    const { sql, consumerKey } = state;
    if (sql) {
      try {
        if (consumerKey) {
          const key = sqlString(consumerKey);
          const owned = `(SELECT id FROM ServiceClient WHERE clientKey=${key})`;
          sql.execute(`DELETE FROM ServiceAccessToken WHERE clientId IN ${owned};
            DELETE FROM ServiceRequestToken WHERE clientId IN ${owned};
            DELETE FROM ServiceOAuthNonce WHERE consumerKey=${key};
            DELETE FROM ServiceClient WHERE clientKey=${key}`);
          assert(sql.value(`SELECT (SELECT COUNT(*) FROM ServiceClient WHERE clientKey=${key})
              + (SELECT COUNT(*) FROM ServiceOAuthNonce WHERE consumerKey=${key})`) === '0',
          'the OAuth client fixture or its nonces were not removed');
        }
      } finally {
        sql.dispose();
      }
    }
  }
}

async function main(state = {}) {
  const config = readConfig();
  const sql = createSqlRunner(config.mysql);
  state.sql = sql;
  const app = (p) => `${config.baseUrl}${p}`;
  const clientName = `PW OAuth surfaces ${crypto.randomUUID()}`;
  const consumerKey = crypto.randomBytes(12).toString('hex');
  const consumerSecret = crypto.randomBytes(12).toString('hex');

  const providerNo = sql.value(
    `SELECT provider_no FROM security WHERE user_name=${sqlString(config.testUser)} LIMIT 1`);
  assert(providerNo, `${config.testUser} has no security row; the check needs the login account's provider`);
  const demographicNo = process.env.OAUTH_DEMOGRAPHIC_NO || sql.value(
    `SELECT demographic_no FROM demographic WHERE patient_status_date > '1900-01-01'
      ORDER BY demographic_no LIMIT 1`);
  if (!/^\d+$/.test(String(demographicNo))) {
    throw new SkipCheck('no demographic with a patient status date; load the demo dataset or set OAUTH_DEMOGRAPHIC_NO');
  }
  const surname = sql.value(`SELECT last_name FROM demographic WHERE demographic_no=${demographicNo}`);
  if (!surname) throw new SkipCheck(`demographic ${demographicNo} does not exist`);
  const noPatientData = async (response, label) => {
    assert(!(await response.text()).includes(surname), `${label} returned the patient's surname`);
  };

  // uri='oob': OscarOAuthDataProvider allows an out-of-band callback only for a client
  // registered as out-of-band, and the verifier is then shown instead of redirected.
  state.consumerKey = consumerKey;
  insertId(sql, `INSERT INTO ServiceClient(name,clientKey,clientSecret,uri,lifetime)
      VALUES(${sqlString(clientName)},${sqlString(consumerKey)},${sqlString(consumerSecret)},'oob',3600)`,
  'ServiceClient');

  const recorder = createRecorder();
  state.browser = await launchBrowser(config);
  // A cookie-less context: what an integrator's server, or a stranger, sends.
  const anon = (await newContext(state.browser, config)).request;
  const demoUrl = app(`/ws/services/demographics/${demographicNo}`);
  const json = { Accept: 'application/json' };

  // 1. Published, and fail-closed without credentials.
  let r = await anon.post(app('/ws/oauth/initiate'), { form: {} });
  await expectStatus(r, 400, 'POST /ws/oauth/initiate without OAuth parameters');
  assert((await r.text()).includes('invalid_oauth_parameters'),
    'POST /ws/oauth/initiate without OAuth parameters did not name the missing parameters');
  r = await anon.get(app('/ws/oauth/authorize'));
  await expectStatus(r, 400, 'GET /ws/oauth/authorize without a request token');
  r = await anon.post(app('/ws/oauth/token'), { form: {} });
  await expectStatus(r, 400, 'POST /ws/oauth/token without OAuth parameters');
  const strangerUrl = app('/ws/oauth/initiate');
  r = await anon.post(strangerUrl, {
    headers: {
      Authorization: oauthHeader({
        method: 'POST', url: strangerUrl, consumerKey: crypto.randomBytes(12).toString('hex'),
        consumerSecret: crypto.randomBytes(12).toString('hex'), extra: { oauth_callback: 'oob' },
      }),
    },
  });
  await expectStatus(r, 401, 'signed POST /ws/oauth/initiate from an unregistered consumer');
  assert((await r.text()).includes('invalid_consumer'),
    '/ws/oauth/initiate did not name an unregistered consumer as invalid_consumer');

  for (const route of ['/ws/services/oauth/info', `/ws/services/demographics/${demographicNo}`]) {
    r = await anon.get(app(route), { headers: json });
    await expectStatus(r, 401, `anonymous GET ${route}`);
    // #4438: OAuth1ExceptionMapper answers with the reason. Without it CXF wrote an XMLFault naming
    // the Java exception, which ResponseSanitizationFilter replaced and logged at ERROR every time.
    const refusal = await r.text();
    assert(refusal.trim() === 'authentication_required',
      `anonymous GET ${route} did not answer the plain reason authentication_required (got ${refusal.length} chars`
      + `${/Exception|XMLFault/.test(refusal) ? ' naming a Java exception or an XMLFault' : ''})`);
    await noPatientData(r, `anonymous GET ${route}`);
  }
  r = await anon.get(demoUrl, {
    headers: {
      ...json,
      Authorization: oauthHeader({
        method: 'GET', url: demoUrl, consumerKey, consumerSecret,
        token: crypto.randomBytes(12).toString('hex'), tokenSecret: 'not-a-token-secret',
      }),
    },
  });
  await expectStatus(r, 401, 'GET /ws/services/demographics/{id} with an unknown access token');
  await noPatientData(r, 'an OAuth call with an unknown access token');

  // 2. Handshake. Scope enforcement is on by default (#4419): /initiate refuses a request
  // token with no scope or an unknown one before it persists anything. In a legacy mode it
  // accepts a request with no scope, as the Cortico integration sends.
  if (SCOPED) {
    for (const [label, query] of [['no scope', ''], ['an unknown scope', `?scope=${pct('everything.write')}`]]) {
      const refusedUrl = app(`/ws/oauth/initiate${query}`);
      r = await anon.post(refusedUrl, {
        headers: {
          Authorization: oauthHeader({
            method: 'POST', url: refusedUrl, consumerKey, consumerSecret, extra: { oauth_callback: 'oob' },
          }),
        },
      });
      await expectStatus(r, 400, `signed POST /ws/oauth/initiate with ${label}`);
      assert((await r.text()).includes('invalid_scope'),
        `/ws/oauth/initiate with ${label} did not answer invalid_scope: is oauth.scope.enforcement.enabled off?`);
    }
    assert(sql.value(`SELECT COUNT(*) FROM ServiceRequestToken WHERE clientId=
        (SELECT id FROM ServiceClient WHERE clientKey=${sqlString(consumerKey)})`) === '0',
    'a refused /ws/oauth/initiate still stored a request token');
  }

  const context = await newContext(state.browser, config);
  await login(context, config, recorder);

  /**
   * Request token, consent in the browser, access token. The consent page must name the client,
   * list the requested scopes, and carry exactly the warning the server's mode calls for.
   */
  const authorize = async (scopes, { probeExchange = false } = {}) => {
    const initiateUrl = app(`/ws/oauth/initiate${scopes ? `?scope=${pct(scopes)}` : ''}`);
    r = await anon.post(initiateUrl, {
      headers: {
        Authorization: oauthHeader({
          method: 'POST', url: initiateUrl, consumerKey, consumerSecret, extra: { oauth_callback: 'oob' },
        }),
      },
    });
    await expectStatus(r, 200, `signed POST /ws/oauth/initiate${scopes ? '' : ' with no scope'}`);
    const requestToken = formFields(await r.text());
    assert(requestToken.oauth_token && requestToken.oauth_token_secret
      && requestToken.oauth_callback_confirmed === 'true',
    '/ws/oauth/initiate did not return oauth_token, oauth_token_secret and oauth_callback_confirmed=true');

    const consent = await context.newPage();
    wireStrictPage(consent, 'oauth-consent', recorder);
    const consentResponse = await consent.goto(
      app(`/ws/oauth/authorize?oauth_token=${pct(requestToken.oauth_token)}`), { waitUntil: 'load' });
    assert(consentResponse && consentResponse.status() === 200,
      `the consent page answered HTTP ${consentResponse && consentResponse.status()}`);
    await assertNotErrorPage(consent, 'OAuth consent page');
    const consentText = await consent.locator('body').innerText();
    assert(consentText.includes(clientName), 'the consent page does not name the requesting application');
    for (const scope of (scopes || '').split(' ').filter(Boolean)) {
      assert(consentText.includes(scope), `the consent page does not list the requested scope ${scope}`);
    }
    const warnings = {
      scoped: null, 'legacy-restricted': '#legacyRestrictedWarning', 'legacy-full': '#fullAccessWarning',
    };
    for (const [mode, selector] of Object.entries(warnings)) {
      if (!selector) continue;
      const shown = await consent.locator(selector).count() > 0;
      assert(shown === (mode === OAUTH_MODE),
        shown ? `the consent page shows the ${mode} warning (${selector}); the server is not in ${OAUTH_MODE} mode`
          : `the consent page lacks the ${mode} warning (${selector}) the server's mode calls for`);
    }
    const [approval] = await Promise.all([
      consent.waitForResponse((resp) => resp.request().method() === 'POST'
        && new URL(resp.url()).pathname.endsWith('/ws/oauth/authorize'), { timeout: 30000 }),
      consent.locator('#scopeForm button[type="submit"]').click(),
    ]);
    assert(approval.status() === 200, `approving the request token answered HTTP ${approval.status()}`);
    const verifier = formFields(await approval.text()).oauth_verifier;
    assert(verifier, 'approving the request token did not show an oauth_verifier');
    await consent.close();

    const tokenUrl = app('/ws/oauth/token');
    const exchange = (oauthVerifier) => anon.post(tokenUrl, {
      headers: {
        Authorization: oauthHeader({
          method: 'POST', url: tokenUrl, consumerKey, consumerSecret,
          token: requestToken.oauth_token, tokenSecret: requestToken.oauth_token_secret,
          extra: { oauth_verifier: oauthVerifier },
        }),
      },
    });
    if (probeExchange) {
      r = await exchange(`${verifier}x`);
      await expectStatus(r, 401, 'signed POST /ws/oauth/token with the wrong verifier');
      assert((await r.text()).includes('invalid_verifier'),
        '/ws/oauth/token did not name a wrong verifier as invalid_verifier');
    }
    r = await exchange(verifier);
    await expectStatus(r, 200, 'signed POST /ws/oauth/token');
    const accessToken = formFields(await r.text());
    assert(accessToken.oauth_token && accessToken.oauth_token_secret,
      '/ws/oauth/token did not return oauth_token and oauth_token_secret');
    if (probeExchange) {
      // Request tokens are single-use (OscarOAuthDataProvider.createAccessToken deletes it).
      r = await exchange(verifier);
      await expectStatus(r, 401, 'a second /ws/oauth/token exchange of the same request token (fresh nonce)');
    }
    return accessToken;
  };

  // In the legacy modes the token is requested the way the Cortico integration does: no scope.
  const accessToken = await authorize(SCOPED ? REQUESTED_SCOPES : '', { probeExchange: true });

  // 3. Signed calls on the OAuth-guarded data API.
  const signed = (method, url, token = accessToken, secret = consumerSecret) => oauthHeader({
    method, url, consumerKey, consumerSecret: secret,
    token: token.oauth_token, tokenSecret: token.oauth_token_secret,
  });
  const signedGet = (url, secret = consumerSecret) => signed('GET', url, accessToken, secret);
  const infoUrl = app('/ws/services/oauth/info');
  const infoAuthorization = signedGet(infoUrl);
  r = await anon.get(infoUrl, { headers: { ...json, Authorization: infoAuthorization } });
  await expectStatus(r, 200, 'signed GET /ws/services/oauth/info');
  assert(String((await r.json()).login) === String(providerNo),
    '/ws/services/oauth/info does not name the provider who authorized the token');

  r = await anon.get(demoUrl, { headers: { ...json, Authorization: signedGet(demoUrl) } });
  await expectStatus(r, 200, 'signed GET /ws/services/demographics/{id}');
  assert(String((await r.json()).demographicNo) === String(demographicNo),
    '/ws/services/demographics/{id} returned a different record than the one requested');

  // 3a. Under enforcement the token holds demographic.read and provider.read only (#4419); in
  // legacy-restricted mode it holds nothing and may call only the Cortico endpoints; in
  // legacy-full mode it may call anything the provider can.
  const refusedAs = async (response, reason, label) => {
    await expectStatus(response, 403, label);
    assert((await response.text()).trim() === reason, `${label} was not refused as ${reason}`);
  };
  const outsideGrant = { scoped: 'insufficient_scope', 'legacy-restricted': 'restricted_endpoint' }[OAUTH_MODE];
  const ticklerUrl = app('/ws/services/tickler/mine');
  r = await anon.get(ticklerUrl, { headers: { ...json, Authorization: signedGet(ticklerUrl) } });
  if (outsideGrant) {
    await refusedAs(r, outsideGrant, 'signed GET /ws/services/tickler/mine with a token granted no tickler scope');
  } else {
    await expectStatus(r, 200, 'signed GET /ws/services/tickler/mine under full legacy access');
  }
  const deleteUrl = app(`/ws/services/demographics/${ABSENT_DEMOGRAPHIC_NO}`);
  r = await anon.delete(deleteUrl, { headers: { ...json, Authorization: signed('DELETE', deleteUrl) } });
  if (outsideGrant) {
    await refusedAs(r, outsideGrant, 'signed DELETE /ws/services/demographics/{id} with only demographic.read');
  } else {
    await expectStatus(r, 404, 'signed DELETE of an absent demographic under full legacy access');
  }
  // The same write through the .json extension mapping CXF strips before routing: before #4419 its
  // root read as "demographics.json", which needed no scope at all.
  const deleteJsonUrl = `${deleteUrl}.json`;
  r = await anon.delete(deleteJsonUrl, { headers: { ...json, Authorization: signed('DELETE', deleteJsonUrl) } });
  await expectStatus(r, outsideGrant ? 403 : 404, 'signed DELETE /ws/services/demographics/{id}.json');

  // 3b. Closed to every OAuth client in every mode: server administration and account reconnaissance.
  for (const route of ['/ws/services/jobs/all', '/ws/services/persona/rights']) {
    const blockedUrl = app(route);
    r = await anon.get(blockedUrl, { headers: { ...json, Authorization: signedGet(blockedUrl) } });
    await refusedAs(r, 'blocked_endpoint', `signed GET ${route} (${OAUTH_MODE})`);
  }
  // The Cortico REST calls reach their service. An empty JSON body is refused by the service itself
  // (400: a demographicNo, or a title and file, is required), so nothing is created; the status
  // proves the call got past the OAuth gate. Under enforcement the first token's demographic.read
  // cannot, and a second token granted what Cortico needs can.
  const corticoCalls = [
    ['PUT', app('/ws/services/demographics/'), 'PUT /ws/services/demographics/'],
    ['POST', app('/ws/services/document/saveDocumentToDemographic/'), 'POST /ws/services/document/saveDocumentToDemographic/'],
  ];
  const corticoCall = async (method, url, token) => anon.fetch(url, {
    method,
    headers: { ...json, 'Content-Type': 'application/json', Authorization: signed(method, url, token) },
    data: '{}',
  });
  if (SCOPED) {
    for (const [method, url, label] of corticoCalls) {
      await refusedAs(await corticoCall(method, url, accessToken), 'insufficient_scope',
        `${label} with only demographic.read`);
    }
  }
  const corticoToken = SCOPED ? await authorize(CORTICO_SCOPES) : accessToken;
  for (const [method, url, label] of corticoCalls) {
    await expectStatus(await corticoCall(method, url, corticoToken), 400,
      `${label} with an empty body${SCOPED ? ' and the Cortico scopes' : ` (${OAUTH_MODE})`}`);
  }
  r = await anon.get(demoUrl, { headers: { ...json, Authorization: signed('GET', demoUrl, corticoToken) } });
  await expectStatus(r, 200, `GET /ws/services/demographics/{id}${SCOPED ? ' with the Cortico scopes' : ''}`);

  r = await anon.get(infoUrl, { headers: { ...json, Authorization: infoAuthorization } });
  await expectStatus(r, 401, 'a replayed signed request (same nonce)');
  // The real access token, signed with the wrong consumer secret: only the HMAC is wrong.
  r = await anon.get(demoUrl, { headers: { ...json, Authorization: signedGet(demoUrl, 'not-the-secret') } });
  await expectStatus(r, 401, 'GET /ws/services/demographics/{id} with a forged signature');
  await noPatientData(r, 'an OAuth call with a forged signature');

  // 4. Neither surface accepts the other's credential.
  r = await context.request.get(demoUrl, { headers: json });
  await expectStatus(r, 401, 'GET /ws/services/demographics/{id} with only a browser session');
  await noPatientData(r, 'a session-only call to /ws/services');
  const sessionDemoUrl = app(`/ws/rs/demographics/${demographicNo}`);
  r = await anon.get(sessionDemoUrl, { headers: { ...json, Authorization: signedGet(sessionDemoUrl) } });
  await expectStatus(r, 401, 'GET /ws/rs/demographics/{id} with only a signed OAuth access token');
  await noPatientData(r, 'an OAuth-only call to /ws/rs');

  // 5. The session surface and SOAP are unaffected.
  r = await anon.get(app('/ws/rs/status/checkIfAuthed'));
  await expectStatus(r, 401, 'anonymous GET /ws/rs/status/checkIfAuthed');
  r = await context.request.get(app('/ws/rs/status/checkIfAuthed'));
  await expectStatus(r, 200, 'session GET /ws/rs/status/checkIfAuthed');
  r = await context.request.get(sessionDemoUrl, { headers: json });
  await expectStatus(r, 200, 'session GET /ws/rs/demographics/{id}');
  const sessionRecord = await r.json();
  assert(typeof sessionRecord.patientStatusDate === 'number',
    `session /ws/rs serialized patientStatusDate as a ${typeof sessionRecord.patientStatusDate}, not epoch `
    + 'milliseconds: the OAuth context has replaced the /ws/rs JSON mapper (shared bean id, issue #3446)');

  r = await anon.get(app('/ws/LoginService?wsdl'));
  await expectStatus(r, 200, 'GET /ws/LoginService?wsdl');
  assert((await r.text()).includes('wsdl:definitions'), '/ws/LoginService?wsdl did not return a WSDL');
  // A real operation, not an empty envelope: WS-Security rejects it before unmarshalling,
  // so an ungated endpoint would answer 200 rather than fail somewhere later.
  r = await anon.post(app('/ws/DemographicService'), {
    headers: { 'Content-Type': 'text/xml' },
    data: '<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body>'
      + `<getDemographic xmlns="http://ws.oscarehr.org/"><arg0>${demographicNo}</arg0></getDemographic>`
      + '</s:Body></s:Envelope>',
  });
  await expectStatus(r, [400, 401], 'unauthenticated SOAP getDemographic');

  // 6. An anonymous burst (#4429). Rows are counted by id, not time, so the app's and the
  // database's clocks cannot disagree. Last, because behind the front door it uses up this
  // address's API request budget for a few seconds.
  const lastLogId = Number(sql.value('SELECT COALESCE(MAX(id), 0) FROM log'));
  const floodUrl = app('/ws/services/oauth/info');
  const statuses = await Promise.all(Array.from({ length: FLOOD_REQUESTS },
    () => anon.get(floodUrl, { headers: json }).then((resp) => resp.status())));
  const unexpected = statuses.filter((status) => status !== 401 && status !== 429);
  assert(unexpected.length === 0,
    `an anonymous burst on /ws/services answered statuses other than 401 and 429: ${[...new Set(unexpected)].join(', ')}`);
  const throttled = statuses.filter((status) => status === 429).length;
  if (config.expectFrontDoor) {
    assert(throttled > 0,
      `${FLOOD_REQUESTS} concurrent anonymous /ws/services calls through the front door drew no 429: `
      + 'the carlos_wsapi limit_req zone is not applied (#4429)');
  }
  const auditRows = Number(sql.value(`SELECT COUNT(*) FROM log WHERE id > ${lastLogId}
      AND action IN ('OAUTH_LOGIN_FAILURE', 'OAUTH_LOGIN_FAILURES_SUPPRESSED')`));
  assert(auditRows <= MAX_FLOOD_AUDIT_ROWS,
    `${FLOOD_REQUESTS - throttled} anonymous /ws/services refusals wrote ${auditRows} audit rows; `
    + `the failure audit budget allows at most ${MAX_FLOOD_AUDIT_ROWS} (#4429)`);

  assertStrictPage(recorder);
  return { surfaces: 4, handshake: 'oob', mode: OAUTH_MODE, flood: { requests: FLOOD_REQUESTS, throttled, auditRows } };
}

if (require.main === module) {
  const state = {};
  runCheck({ name: 'oauth-rest-surfaces', run: () => main(state), cleanup: () => cleanup(state) });
}
module.exports = { main, cleanup, oauthHeader, oauthSignature, pct, signatureBaseUri };
