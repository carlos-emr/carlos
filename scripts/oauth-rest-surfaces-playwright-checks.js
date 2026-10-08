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
 *      OAuth protocol errors (400), not 404. /ws/services rejects a call without
 *      credentials and a call with a forged signature (401), and returns no
 *      patient data either way.
 *   2. The OAuth 1.0a handshake. A signed /initiate with an out-of-band callback
 *      returns a request token. The logged-in provider opens the consent page at
 *      /ws/oauth/authorize, as the integrator would send them there, and clicks
 *      Authorize, which shows the verifier. A signed /token then exchanges the
 *      verifier for an access token.
 *   3. Signed data calls. /ws/services/oauth/info names the provider who
 *      authorized, /ws/services/demographics/{id} returns that record, and
 *      resending a signed request with the same nonce is refused (401).
 *   4. The session surface is unaffected. /ws/rs refuses an anonymous call and
 *      serves the logged-in browser. Its JSON dates stay epoch milliseconds: before
 *      the fix, loading applicationContextREST.xml as it was replaced the /ws/rs
 *      mapper (a shared bean id) and turned them into "yyyy-MM-dd" strings. SOAP
 *      still publishes its WSDL and still rejects an unauthenticated operation.
 *
 * FIXTURE. The check inserts one ServiceClient row with a unique name, key and
 * secret, because the Administration > REST Clients page never shows a client's
 * secret. It deletes that client and every request token, access token and
 * consumed nonce issued to it, whether the check passes or fails. The
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
 */
const crypto = require('crypto');
const {
  SkipCheck, assert, assertNotErrorPage, assertStrictPage, createRecorder, createSqlRunner,
  launchBrowser, login, newContext, readConfig, runCheck, sqlString, wireStrictPage,
} = require('./lib/playwright-harness');

const CXF_NOT_PUBLISHED = 'No service was found';
// Scopes the signed calls below need when oauth.scope.enforcement.enabled is on. With
// enforcement off (the default) they are recorded on the token and not consulted.
const REQUESTED_SCOPES = 'demographic.read provider.read';

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
 * Builds an HMAC-SHA1 signed OAuth 1.0a Authorization header (RFC 5849). Query
 * parameters of the URL take part in the signature, as the server includes them.
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
  const params = [...Object.entries(oauth), ...new URL(url).searchParams.entries()]
    .map(([k, v]) => [pct(k), pct(v)])
    .sort(([ak, av], [bk, bv]) => (ak === bk ? (av < bv ? -1 : av > bv ? 1 : 0) : (ak < bk ? -1 : 1)));
  const base = [
    method.toUpperCase(),
    pct(signatureBaseUri(url)),
    pct(params.map(([k, v]) => `${k}=${v}`).join('&')),
  ].join('&');
  const key = `${pct(consumerSecret)}&${pct(tokenSecret || '')}`;
  const signature = crypto.createHmac('sha1', key).update(base).digest('base64');
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

async function main() {
  const config = readConfig();
  const sql = createSqlRunner(config.mysql);
  const app = (p) => `${config.baseUrl}${p}`;
  const clientName = `PW OAuth surfaces ${crypto.randomUUID()}`;
  const consumerKey = crypto.randomBytes(12).toString('hex');
  const consumerSecret = crypto.randomBytes(12).toString('hex');
  let clientId = '';
  let browser;
  try {
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

    // uri='oob': OscarOAuthDataProvider allows an out-of-band callback only for a client
    // registered as out-of-band, and the verifier is then shown instead of redirected.
    clientId = sql.value(`INSERT INTO ServiceClient(name,clientKey,clientSecret,uri,lifetime)
        VALUES(${sqlString(clientName)},${sqlString(consumerKey)},${sqlString(consumerSecret)},'oob',3600);
      SELECT LAST_INSERT_ID();`);
    assert(/^\d+$/.test(clientId), 'the ServiceClient fixture insert returned no numeric id');

    const recorder = createRecorder();
    browser = await launchBrowser(config);
    // A cookie-less context: what an integrator's server, or a stranger, sends.
    const anonContext = await newContext(browser, config);
    const anon = anonContext.request;

    // 1. Published, and fail-closed without credentials.
    let r = await anon.post(app('/ws/oauth/initiate'), { form: {} });
    await expectStatus(r, 400, 'POST /ws/oauth/initiate without OAuth parameters');
    assert((await r.text()).includes('invalid_oauth_parameters'),
      'POST /ws/oauth/initiate without OAuth parameters did not name the missing parameters');
    r = await anon.get(app('/ws/oauth/authorize'));
    await expectStatus(r, 400, 'GET /ws/oauth/authorize without a request token');
    r = await anon.post(app('/ws/oauth/token'), { form: {} });
    await expectStatus(r, 400, 'POST /ws/oauth/token without OAuth parameters');

    for (const route of ['/ws/services/oauth/info', `/ws/services/demographics/${demographicNo}`]) {
      r = await anon.get(app(route), { headers: { Accept: 'application/json' } });
      await expectStatus(r, 401, `anonymous GET ${route}`);
      assert(!(await r.text()).includes(surname), `anonymous GET ${route} returned the patient's surname`);
    }
    const forgedUrl = app(`/ws/services/demographics/${demographicNo}`);
    r = await anon.get(forgedUrl, {
      headers: {
        Accept: 'application/json',
        Authorization: oauthHeader({
          method: 'GET', url: forgedUrl, consumerKey, consumerSecret: 'not-the-secret',
          token: crypto.randomBytes(12).toString('hex'), tokenSecret: 'not-a-token-secret',
        }),
      },
    });
    await expectStatus(r, 401, 'GET /ws/services/demographics with a forged signature');
    assert(!(await r.text()).includes(surname), 'a forged OAuth call returned the patient\'s surname');

    // 2. Handshake: request token, consent in the browser, access token.
    const initiateUrl = app(`/ws/oauth/initiate?scope=${pct(REQUESTED_SCOPES)}`);
    r = await anon.post(initiateUrl, {
      headers: {
        Authorization: oauthHeader({
          method: 'POST', url: initiateUrl, consumerKey, consumerSecret, extra: { oauth_callback: 'oob' },
        }),
      },
    });
    await expectStatus(r, 200, 'signed POST /ws/oauth/initiate');
    const requestToken = formFields(await r.text());
    assert(requestToken.oauth_token && requestToken.oauth_token_secret
      && requestToken.oauth_callback_confirmed === 'true',
    '/ws/oauth/initiate did not return oauth_token, oauth_token_secret and oauth_callback_confirmed=true');

    const context = await newContext(browser, config);
    await login(context, config, recorder);
    const consent = await context.newPage();
    wireStrictPage(consent, 'oauth-consent', recorder);
    const consentResponse = await consent.goto(
      app(`/ws/oauth/authorize?oauth_token=${pct(requestToken.oauth_token)}`), { waitUntil: 'load' });
    assert(consentResponse && consentResponse.status() === 200,
      `the consent page answered HTTP ${consentResponse && consentResponse.status()}`);
    await assertNotErrorPage(consent, 'OAuth consent page');
    const consentText = await consent.locator('body').innerText();
    assert(consentText.includes(clientName), 'the consent page does not name the requesting application');
    for (const scope of REQUESTED_SCOPES.split(' ')) {
      assert(consentText.includes(scope), `the consent page does not list the requested scope ${scope}`);
    }
    const [approval] = await Promise.all([
      consent.waitForResponse((resp) => resp.request().method() === 'POST'
        && new URL(resp.url()).pathname.endsWith('/ws/oauth/authorize'), { timeout: 30000 }),
      consent.locator('#scopeForm button[type="submit"]').click(),
    ]);
    assert(approval.status() === 200, `approving the request token answered HTTP ${approval.status()}`);
    const verifier = formFields(await approval.text()).oauth_verifier;
    assert(verifier, 'approving the request token did not show an oauth_verifier');

    const tokenUrl = app('/ws/oauth/token');
    r = await anon.post(tokenUrl, {
      headers: {
        Authorization: oauthHeader({
          method: 'POST', url: tokenUrl, consumerKey, consumerSecret,
          token: requestToken.oauth_token, tokenSecret: requestToken.oauth_token_secret,
          extra: { oauth_verifier: verifier },
        }),
      },
    });
    await expectStatus(r, 200, 'signed POST /ws/oauth/token');
    const accessToken = formFields(await r.text());
    assert(accessToken.oauth_token && accessToken.oauth_token_secret,
      '/ws/oauth/token did not return oauth_token and oauth_token_secret');

    // 3. Signed calls on the OAuth-guarded data API.
    const signedGet = (url) => oauthHeader({
      method: 'GET', url, consumerKey, consumerSecret,
      token: accessToken.oauth_token, tokenSecret: accessToken.oauth_token_secret,
    });
    const infoUrl = app('/ws/services/oauth/info');
    const infoAuthorization = signedGet(infoUrl);
    r = await anon.get(infoUrl, { headers: { Accept: 'application/json', Authorization: infoAuthorization } });
    await expectStatus(r, 200, 'signed GET /ws/services/oauth/info');
    assert(String((await r.json()).login) === String(providerNo),
      '/ws/services/oauth/info does not name the provider who authorized the token');

    const demoUrl = app(`/ws/services/demographics/${demographicNo}`);
    r = await anon.get(demoUrl, { headers: { Accept: 'application/json', Authorization: signedGet(demoUrl) } });
    await expectStatus(r, 200, 'signed GET /ws/services/demographics/{id}');
    assert(String((await r.json()).demographicNo) === String(demographicNo),
      '/ws/services/demographics/{id} returned a different record than the one requested');

    r = await anon.get(infoUrl, { headers: { Accept: 'application/json', Authorization: infoAuthorization } });
    await expectStatus(r, 401, 'a replayed signed request (same nonce)');

    // 4. The session surface and SOAP are unaffected.
    r = await anon.get(app('/ws/rs/status/checkIfAuthed'));
    await expectStatus(r, 401, 'anonymous GET /ws/rs/status/checkIfAuthed');
    r = await context.request.get(app('/ws/rs/status/checkIfAuthed'));
    await expectStatus(r, 200, 'session GET /ws/rs/status/checkIfAuthed');
    r = await context.request.get(app(`/ws/rs/demographics/${demographicNo}`),
      { headers: { Accept: 'application/json' } });
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

    assertStrictPage(recorder);
    return { surfaces: 4, handshake: 'oob' };
  } finally {
    try {
      if (browser) await browser.close();
    } finally {
      try {
        if (clientId) {
          sql.execute(`DELETE FROM ServiceAccessToken WHERE clientId=${clientId};
            DELETE FROM ServiceRequestToken WHERE clientId=${clientId};
            DELETE FROM ServiceOAuthNonce WHERE consumerKey=${sqlString(consumerKey)};
            DELETE FROM ServiceClient WHERE id=${clientId}`);
        }
      } finally {
        sql.dispose();
      }
    }
  }
}

if (require.main === module) runCheck({ name: 'oauth-rest-surfaces', run: main });
module.exports = { main, oauthHeader, pct, signatureBaseUri };
