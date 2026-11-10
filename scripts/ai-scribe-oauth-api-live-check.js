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
 * Exercise the OAuth API workflows needed by AI-Scribe against a running,
 * disposable CARLOS EMR deployment. This is intentionally a live check: CXF
 * local-transport tests cannot verify the OAuth handshake, scope enforcement,
 * database persistence, or deployed routing.
 *
 * The check owns one synthetic patient, one BP measurement, one eForm template,
 * OAuth clients, and their issued credentials. Cleanup removes only rows keyed
 * by the unique run markers, including when a request fails part-way through.
 *
 * REST currently reads measurements but has no REST write route. The scoped
 * PUT probe below requires measurement.write and asserts that CXF rejects the
 * unsupported method; SOAP MeasurementService is a separate WS-Security API.
 * The eForm REST API manages templates, not completed patient eForm instances.
 */
const crypto = require('node:crypto');
const {
  SkipCheck, assert, assertNotErrorPage, assertStrictPage, createRecorder, createSqlRunner, insertId,
  launchBrowser, login, newContext, readConfig, runCheck, sqlString, wireStrictPage,
} = require('./lib/playwright-harness');
const { oauthHeader, pct } = require('./oauth-rest-surfaces-playwright-checks');

const EXPECT_OAUTH_MODE = process.env.EXPECT_OAUTH_MODE || 'scoped';
const REQUIRED_SCOPES = 'demographic.read demographic.write measurement.read measurement.write eform.read eform.write';
const LIMITED_SCOPES = 'demographic.read measurement.read';

function formFields(body) {
  return Object.fromEntries(new URLSearchParams(body.trim()));
}

async function expectStatus(response, expected, label) {
  assert(response.status() === expected, `${label}: expected HTTP ${expected}, got ${response.status()}`);
}

async function expectSuccessJson(response, label) {
  await expectStatus(response, 200, label);
  const json = await response.json();
  assert(json && json.status === 'SUCCESS' && json.body != null, `${label}: API did not return a SUCCESS response`);
  return json.body;
}

async function expectInsufficientScope(response, label) {
  await expectStatus(response, 403, label);
  assert((await response.text()).trim() === 'insufficient_scope', `${label}: OAuth did not refuse with insufficient_scope`);
}

function sqlId(value, label) {
  assert(/^[1-9]\d*$/.test(String(value)), `${label} is not a positive numeric id`);
  return String(value);
}

function printEndpointResults(results) {
  const rows = results.map(({ outcome, method, path, status, verification }) => ({
    outcome,
    endpoint: `${method} ${path}`,
    http: status,
    verification,
  }));
  const widths = {
    outcome: Math.max('RESULT'.length, ...rows.map((row) => row.outcome.length)),
    endpoint: Math.max('ENDPOINT'.length, ...rows.map((row) => row.endpoint.length)),
    http: Math.max('HTTP'.length, ...rows.map((row) => String(row.http).length)),
  };
  const line = (row) => `${row.outcome.padEnd(widths.outcome)}  `
    + `${row.endpoint.padEnd(widths.endpoint)}  `
    + `${String(row.http).padEnd(widths.http)}  ${row.verification}`;
  console.log('\nAI-Scribe OAuth API endpoint results');
  console.log(line({ outcome: 'RESULT', endpoint: 'ENDPOINT', http: 'HTTP', verification: 'VERIFICATION' }));
  console.log(rows.map(line).join('\n'));
  console.log(`\n${rows.filter((row) => row.outcome === 'PASS').length} passed; `
    + `${rows.filter((row) => row.outcome === 'GAP').length} capability gaps.`);
}

async function cleanup(state) {
  const { sql, consumerKey, marker, formName, providerNo } = state;
  try {
    if (state.browser) await state.browser.close();
  } finally {
    if (!sql) return;
    try {
      if (marker && providerNo) {
        const markerSql = sqlString(marker);
        const providerSql = sqlString(providerNo);
        const patient = sql.value(`SELECT demographic_no FROM demographic
          WHERE last_name=${markerSql} AND provider_no=${providerSql}`);
        if (patient) {
          const id = sqlId(patient, 'owned synthetic demographic id');
          assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${id}
            AND last_name=${markerSql} AND provider_no=${providerSql}`) === '1',
          'The synthetic demographic ownership check did not match exactly one row');
          sql.execute(`DELETE FROM measurementsExt WHERE measurement_id IN
              (SELECT id FROM measurements WHERE demographicNo=${id});
            DELETE FROM measurements WHERE demographicNo=${id};
            DELETE FROM demographicExtArchive WHERE demographic_no=${id};
            DELETE FROM demographicExt WHERE demographic_no=${id};
            DELETE FROM demographicArchive WHERE demographic_no=${id};
            DELETE FROM demographiccustArchive WHERE demographic_no=${id};
            DELETE FROM demographiccust WHERE demographic_no=${id};
            DELETE FROM admission WHERE client_id=${id};
            DELETE FROM DemographicContact WHERE demographicNo=${id};
            DELETE FROM demographicPharmacy WHERE demographic_no=${id};
            DELETE FROM demographic WHERE demographic_no=${id} AND last_name=${markerSql}
              AND provider_no=${providerSql}`);
          assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${id}`) === '0',
            'The owned synthetic demographic was not removed');
        }
      }
    } finally {
      try {
        if (formName && providerNo) {
          const nameSql = sqlString(formName);
          const providerSql = sqlString(providerNo);
          sql.execute(`DELETE FROM eform WHERE form_name=${nameSql} AND form_creator=${providerSql}`);
          assert(sql.value(`SELECT COUNT(*) FROM eform WHERE form_name=${nameSql} AND form_creator=${providerSql}`) === '0',
            'The owned synthetic eForm template was not removed');
        }
      } finally {
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
            'The owned OAuth client or its nonces were not removed');
          }
        } finally {
          sql.dispose();
        }
      }
    }
  }
}

async function main(state = {}) {
  assert(EXPECT_OAUTH_MODE === 'scoped',
    'AI-Scribe API acceptance requires EXPECT_OAUTH_MODE=scoped on a separate disposable installation');
  const config = readConfig();
  const sql = createSqlRunner(config.mysql);
  state.sql = sql;
  const app = (path) => `${config.baseUrl}${path}`;
  const runMarker = crypto.randomUUID().replace(/-/g, '');
  const marker = `PWAI${runMarker.slice(0, 12)}`;
  const formName = `PW AI-Scribe ${runMarker}`;
  const consumerKey = crypto.randomBytes(12).toString('hex');
  const consumerSecret = crypto.randomBytes(24).toString('hex');
  const providerNo = sql.value(`SELECT provider_no FROM security
    WHERE user_name=${sqlString(config.testUser)} LIMIT 1`);
  if (!providerNo) throw new SkipCheck(`${config.testUser} has no security row; a real provider login is required`);

  state.marker = marker;
  state.formName = formName;
  state.providerNo = providerNo;
  state.consumerKey = consumerKey;
  const patientNo = insertId(sql, `INSERT INTO demographic
      (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
       provider_no,hc_type,province,roster_status,hin,lastUpdateDate,patient_status_date)
    VALUES (${sqlString(marker)},'AI-Scribe','1980','01','02','F','AC',${sqlString(providerNo)},
      'ON','ON','NR','',NOW(),'2026-01-01')`,
  'AI-Scribe synthetic demographic');
  state.patientNo = patientNo;
  const measurementComment = `AI-Scribe OAuth measurement ${runMarker}`;
  sql.execute(`INSERT INTO measurements
      (type,demographicNo,providerNo,dataField,measuringInstruction,comments,dateObserved,dateEntered)
    VALUES ('BP',${patientNo},${sqlString(providerNo)},'121/79','',${sqlString(measurementComment)},NOW(),NOW())`);
  const measurementId = sql.value(`SELECT id FROM measurements
    WHERE demographicNo=${patientNo} AND type='BP' AND comments=${sqlString(measurementComment)}`);
  sqlId(measurementId, 'AI-Scribe measurement fixture id');

  insertId(sql, `INSERT INTO ServiceClient(name,clientKey,clientSecret,uri,lifetime)
      VALUES(${sqlString(`PW AI-Scribe ${runMarker}`)},${sqlString(consumerKey)},
        ${sqlString(consumerSecret)},'oob',3600)`,
  'AI-Scribe OAuth client');

  const recorder = createRecorder();
  const endpointResults = [];
  const recordEndpoint = (method, path, response, verification, outcome = 'PASS') => {
    endpointResults.push({ outcome, method, path, status: response.status(), verification });
  };
  state.browser = await launchBrowser(config);
  const anonContext = await newContext(state.browser, config);
  const anon = anonContext.request;
  const sessionContext = await newContext(state.browser, config);
  await login(sessionContext, config, recorder, { label: 'ai-scribe-oauth-login' });

  async function authorize(scopes) {
    const initiateUrl = app(`/ws/oauth/initiate?scope=${pct(scopes)}`);
    let response = await anon.post(initiateUrl, {
      headers: {
        Authorization: oauthHeader({
          method: 'POST', url: initiateUrl, consumerKey, consumerSecret, extra: { oauth_callback: 'oob' },
        }),
      },
    });
    await expectStatus(response, 200, 'OAuth request-token initiation');
    const requestToken = formFields(await response.text());
    assert(requestToken.oauth_token && requestToken.oauth_token_secret
      && requestToken.oauth_callback_confirmed === 'true',
    'OAuth initiation did not return a confirmed out-of-band request token');
    recordEndpoint('POST', '/ws/oauth/initiate', response, `request token issued (${scopes})`);

    const consent = await sessionContext.newPage();
    wireStrictPage(consent, 'ai-scribe-oauth-consent', recorder);
    const pageResponse = await consent.goto(
      app(`/ws/oauth/authorize?oauth_token=${pct(requestToken.oauth_token)}`), { waitUntil: 'load' });
    assert(pageResponse && pageResponse.status() === 200, 'The OAuth consent page did not load successfully');
    await assertNotErrorPage(consent, 'AI-Scribe OAuth consent page');
    recordEndpoint('GET', '/ws/oauth/authorize', pageResponse, `consent page loaded (${scopes})`);
    const consentText = await consent.locator('body').innerText();
    for (const scope of scopes.split(' ')) {
      assert(consentText.includes(scope), `The OAuth consent page omitted requested scope ${scope}`);
    }
    assert(await consent.locator('#legacyRestrictedWarning, #fullAccessWarning').count() === 0,
      'The test server is not using OAuth scoped mode; use a separate disposable scoped installation');
    const [approval] = await Promise.all([
      consent.waitForResponse((item) => item.request().method() === 'POST'
        && new URL(item.url()).pathname.endsWith('/ws/oauth/authorize'), { timeout: 30000 }),
      consent.locator('#scopeForm button[type="submit"]').click(),
    ]);
    await expectStatus(approval, 200, 'OAuth consent approval');
    const verifier = formFields(await approval.text()).oauth_verifier;
    assert(verifier, 'OAuth consent did not issue a verifier');
    recordEndpoint('POST', '/ws/oauth/authorize', approval, `consent approved (${scopes})`);
    await consent.close();

    const tokenUrl = app('/ws/oauth/token');
    response = await anon.post(tokenUrl, {
      headers: {
        Authorization: oauthHeader({
          method: 'POST', url: tokenUrl, consumerKey, consumerSecret,
          token: requestToken.oauth_token, tokenSecret: requestToken.oauth_token_secret,
          extra: { oauth_verifier: verifier },
        }),
      },
    });
    await expectStatus(response, 200, 'OAuth access-token exchange');
    const accessToken = formFields(await response.text());
    assert(accessToken.oauth_token && accessToken.oauth_token_secret,
      'OAuth access-token exchange did not return signed credentials');
    recordEndpoint('POST', '/ws/oauth/token', response, `access token issued (${scopes})`);
    return accessToken;
  }

  const fullToken = await authorize(REQUIRED_SCOPES);
  const limitedToken = await authorize(LIMITED_SCOPES);
  const signedRequest = (method, path, token, body) => {
    const url = app(path);
    const headers = {
      Accept: 'application/json',
      Authorization: oauthHeader({
        method, url, consumerKey, consumerSecret,
        token: token.oauth_token, tokenSecret: token.oauth_token_secret,
      }),
    };
    const options = { method, headers };
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
      options.data = JSON.stringify(body);
    }
    return anon.fetch(url, options);
  };

  const infoResponse = await signedRequest('GET', '/ws/services/oauth/info', fullToken);
  await expectStatus(infoResponse, 200, 'Signed OAuth token identity check');
  assert(String((await infoResponse.json()).login) === String(providerNo),
    'The OAuth token is not bound to the provider who approved it');
  recordEndpoint('GET', '/ws/services/oauth/info', infoResponse, 'signed token identity verified');

  const demographicPath = `/ws/services/demographics/${patientNo}`;
  const demographicResponse = await signedRequest('GET', demographicPath, fullToken);
  await expectStatus(demographicResponse, 200, 'OAuth demographic fetch');
  const demographic = await demographicResponse.json();
  assert(String(demographic.demographicNo) === patientNo && demographic.lastName === marker,
    'OAuth demographic fetch did not return the owned synthetic record');
  recordEndpoint('GET', '/ws/services/demographics/{demographicNo}', demographicResponse,
    'owned synthetic demographic returned');

  demographic.firstName = 'AI-Scribe Updated';
  const updateResponse = await signedRequest('PUT', '/ws/services/demographics/', fullToken, demographic);
  await expectStatus(updateResponse, 200, 'OAuth demographic update');
  const updated = await updateResponse.json();
  assert(String(updated.demographicNo) === patientNo && updated.firstName === 'AI-Scribe Updated',
    'OAuth demographic update response did not contain the changed field');
  recordEndpoint('PUT', '/ws/services/demographics/', updateResponse, 'updated field returned');
  const demographicReload = await signedRequest('GET', demographicPath, fullToken);
  const reloadedDemographic = await demographicReload.json();
  await expectStatus(demographicReload, 200, 'OAuth demographic update read-after-write');
  assert(reloadedDemographic.firstName === 'AI-Scribe Updated',
    'OAuth demographic update was not persisted');
  recordEndpoint('GET', '/ws/services/demographics/{demographicNo}', demographicReload,
    'updated field persisted on read-after-write');

  const limitedDemographicUpdate = await signedRequest(
    'PUT', '/ws/services/demographics/', limitedToken, reloadedDemographic,
  );
  await expectInsufficientScope(limitedDemographicUpdate,
    'OAuth demographic update with a token lacking demographic.write');
  recordEndpoint('PUT', '/ws/services/demographics/', limitedDemographicUpdate,
    'missing demographic.write refused with insufficient_scope');

  const measurementsResponse = await signedRequest('POST', `/ws/services/measurements/${patientNo}`, fullToken, {
    types: ['BP'],
  });
  await expectStatus(measurementsResponse, 200, 'OAuth measurement fetch');
  const measurements = await measurementsResponse.json();
  const bpRows = measurements.measurements && measurements.measurements.BP;
  assert(Array.isArray(bpRows) && bpRows.some((row) =>
    row.dataField === '121/79' && row.comments === measurementComment),
  'OAuth measurement fetch did not return the owned BP fixture');
  recordEndpoint('POST', '/ws/services/measurements/{demographicNo}', measurementsResponse,
    'owned BP measurement returned');

  const eformsResponse = await signedRequest('GET', '/ws/services/eforms/', fullToken);
  const eforms = await expectSuccessJson(eformsResponse, 'OAuth eForm listing');
  assert(Array.isArray(eforms), 'OAuth eForm listing did not return an array');
  recordEndpoint('GET', '/ws/services/eforms/', eformsResponse, 'template list returned');

  const htmlBefore = `<html><body><form><input name="${marker}" value="initial"></form></body></html>`;
  const createEformResponse = await signedRequest('POST', '/ws/services/eform/json', fullToken, {
    formName,
    formSubject: 'AI-Scribe OAuth API acceptance fixture',
    formHtml: htmlBefore,
    patientIndependent: false,
  });
  const createdEform = await expectSuccessJson(createEformResponse, 'OAuth eForm template create');
  const formId = sqlId(createdEform.id, 'created eForm id');
  state.formId = formId;
  recordEndpoint('POST', '/ws/services/eform/json', createEformResponse, 'template created');
  const updatedListResponse = await signedRequest('GET', '/ws/services/eforms/', fullToken);
  const updatedList = await expectSuccessJson(updatedListResponse, 'OAuth eForm listing after create');
  assert(updatedList.some((form) => String(form.id) === formId && form.formName === formName),
    'The newly created eForm template was absent from the OAuth eForm listing');
  recordEndpoint('GET', '/ws/services/eforms/', updatedListResponse, 'created template appears in listing');

  const eformLoad = await signedRequest('GET', `/ws/services/eform/${formId}`, fullToken);
  const loadedEform = await expectSuccessJson(eformLoad, 'OAuth eForm template load');
  assert(loadedEform.formName === formName && loadedEform.formHtml === htmlBefore,
    'OAuth eForm load did not return the newly created template');
  recordEndpoint('GET', '/ws/services/eform/{id}', eformLoad, 'created template loaded');

  const htmlAfter = `<html><body><form><input name="${marker}" value="edited"></form></body></html>`;
  const updateEform = await signedRequest('PUT', `/ws/services/eform/${formId}/json`, fullToken, {
    formName,
    formHtml: htmlAfter,
    formSubject: 'AI-Scribe OAuth API acceptance fixture updated',
  });
  const updatedEform = await expectSuccessJson(updateEform, 'OAuth eForm template update');
  assert(String(updatedEform.id) === formId, 'OAuth eForm update returned a different template id');
  recordEndpoint('PUT', '/ws/services/eform/{id}/json', updateEform, 'template update returned');
  const reloadedEformResponse = await signedRequest('GET', `/ws/services/eform/${formId}`, fullToken);
  const reloadedEform = await expectSuccessJson(reloadedEformResponse, 'OAuth eForm update read-after-write');
  assert(reloadedEform.formHtml === htmlAfter
    && reloadedEform.subject === 'AI-Scribe OAuth API acceptance fixture updated',
  'OAuth eForm template update was not persisted');
  recordEndpoint('GET', '/ws/services/eform/{id}', reloadedEformResponse,
    'updated template persisted on read-after-write');

  const limitedEformRead = await signedRequest('GET', '/ws/services/eforms/', limitedToken);
  await expectInsufficientScope(limitedEformRead, 'OAuth eForm read with a token lacking eform.read');
  recordEndpoint('GET', '/ws/services/eforms/', limitedEformRead,
    'missing eform.read refused with insufficient_scope');
  const limitedEformUpdate = await signedRequest('PUT', `/ws/services/eform/${formId}/json`, limitedToken, {
    formName, formHtml: htmlAfter,
  });
  await expectInsufficientScope(limitedEformUpdate, 'OAuth eForm update with a token lacking eform.write');
  recordEndpoint('PUT', '/ws/services/eform/{id}/json', limitedEformUpdate,
    'missing eform.write refused with insufficient_scope');

  const limitedMeasurementWrite = await signedRequest(
    'PUT', `/ws/services/measurements/${patientNo}`, limitedToken, { types: ['BP'] },
  );
  await expectStatus(limitedMeasurementWrite, 405, 'REST measurement update capability probe without write scope');
  recordEndpoint('PUT', '/ws/services/measurements/{demographicNo}', limitedMeasurementWrite,
    'route unavailable; method rejection occurs before scope enforcement', 'GAP');

  const unsupportedMeasurementWrite = await signedRequest(
    'PUT', `/ws/services/measurements/${patientNo}`, fullToken, { types: ['BP'] },
  );
  await expectStatus(unsupportedMeasurementWrite, 405, 'REST measurement update capability probe');
  recordEndpoint('PUT', '/ws/services/measurements/{demographicNo}', unsupportedMeasurementWrite,
    'REST measurement update is not implemented, even with measurement.write', 'GAP');

  printEndpointResults(endpointResults);
  assertStrictPage(recorder);
  return {
    oauthMode: 'scoped',
    checked: [
      'signed OAuth request-token and access-token flow',
      'demographic read, update, and persisted read-after-write',
      'insufficient-scope refusal for demographic update',
      'measurement read',
      'eForm list, create, load, update, and persisted read-after-write',
      'insufficient-scope refusals for eForm and demographic operations',
      'measurement REST update is currently unsupported (HTTP 405)',
    ],
  };
}

if (require.main === module) {
  const state = {};
  runCheck({ name: 'ai-scribe-oauth-api', run: () => main(state), cleanup: () => cleanup(state) });
}

module.exports = { main, cleanup };
