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
 * The check owns one synthetic patient, one BP measurement, one synthetic
 * medication/prescription, one eForm template, OAuth clients, and their issued
 * credentials. Cleanup removes only rows keyed by the unique run markers,
 * including when a request fails part-way through.
 *
 * Measurement rows are immutable. The REST write operation records a separate
 * new measurement for the patient and leaves previous readings unchanged.
 * The eForm REST API manages templates, not completed patient eForm instances.
 */
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const {
  SkipCheck, assert, assertNotErrorPage, assertStrictPage, createRecorder, createSqlRunner, insertId,
  launchBrowser, login, newContext, readConfig, runCheck, sqlString, wireStrictPage,
} = require('./lib/playwright-harness');
const { oauthHeader, pct } = require('./oauth-rest-surfaces-playwright-checks');

const EXPECT_OAUTH_MODE = process.env.EXPECT_OAUTH_MODE || 'scoped';
const API_ONLY = process.env.AI_SCRIBE_API_ONLY?.trim().toLowerCase() || '';
const API_TEST_GROUPS = new Set([
  'demographics', 'measurements', 'measurements.read', 'measurements.write',
  'labs', 'rxlookup', 'rx', 'schedule', 'eforms',
]);
const REQUIRED_SCOPES = [
  'demographic.read', 'demographic.write', 'measurement.read', 'measurement.write',
  'eform.read', 'eform.write', 'lab.read', 'lab.write',
  'rx.read', 'rx.write', 'schedule.read', 'schedule.write',
].join(' ');
const LIMITED_SCOPES = 'demographic.read measurement.read';

function shouldRun(group) {
  return !API_ONLY || API_ONLY === 'all' || API_ONLY === group;
}

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
    + `${rows.filter((row) => row.outcome === 'GAP').length} capability gaps; `
    + `${rows.filter((row) => row.outcome === 'UNVERIFIED').length} workflows unverified; `
    + `${rows.filter((row) => row.outcome === 'FAIL').length} failed.`);
}

function removeOwnedLabFile(state) {
  if (!state.documentStore || !state.labFilePrefix) return;
  const root = fs.realpathSync(state.documentStore);
  const ownedFiles = () => fs.readdirSync(root)
    .filter((name) => name.startsWith(state.labFilePrefix)
      && /^LabUpload\.ai-scribe-[A-Fa-f0-9]+\.xml\.\d+$/.test(name));
  for (const name of ownedFiles()) {
    const file = path.join(root, name);
    assert(fs.lstatSync(file).isFile(), `Refusing to remove non-file lab artifact ${name}`);
    fs.unlinkSync(file);
  }
  assert(ownedFiles().length === 0, 'The owned synthetic HL7 upload file was not removed');
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
        const patient = state.patientNo || sql.value(`SELECT demographic_no FROM demographic
          WHERE last_name=${markerSql} AND first_name IN ('AI-Scribe','AI-Scribe Updated')
            AND provider_no=${providerSql}`);
        if (patient) {
          const id = sqlId(patient, 'owned synthetic demographic id');
          const ownedRows = sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${id}
            AND last_name=${markerSql} AND first_name IN ('AI-Scribe','AI-Scribe Updated')
            AND provider_no=${providerSql}`);
          assert(ownedRows === '1' || (state.demographicDeleted && ownedRows === '0'),
            'The synthetic demographic ownership check did not match the created patient or its deleted state');
          if (state.labAccession) {
            const accessionSql = sqlString(state.labAccession);
            const filePattern = state.labFilePrefix ? sqlString(`%${state.labRunMarker}%`) : null;
            const fileUploadIds = filePattern
              ? sql.rows(`SELECT id FROM fileUploadCheck WHERE filename LIKE ${filePattern}`)
                  .map(([id]) => id)
                  .filter((id) => /^[1-9]\d*$/.test(id))
              : [];
            const uploadedLabIds = fileUploadIds.length
              ? sql.rows(`SELECT lab_id FROM hl7TextMessage
                    WHERE fileUploadCheck_id IN (${fileUploadIds.join(',')})`)
                  .map(([labId]) => labId)
                  .filter((labId) => /^[1-9]\d*$/.test(labId))
              : [];
            const labIds = [...new Set([
              ...sql.rows(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum=${accessionSql}`)
                  .map(([labNo]) => labNo)
                  .filter((labNo) => /^[1-9]\d*$/.test(labNo)),
              ...uploadedLabIds,
            ])];
            if (labIds.length) {
              const ids = labIds.join(',');
              const measurementIds = sql.rows(`SELECT measurement_id FROM measurementsExt
                  WHERE keyval='lab_no' AND val IN (${ids})`)
                .map(([measurementId]) => measurementId)
                .filter((measurementId) => /^[1-9]\d*$/.test(measurementId));
              if (measurementIds.length) {
                const measurementList = [...new Set(measurementIds)].join(',');
                sql.execute(`DELETE FROM measurementsExt WHERE measurement_id IN (${measurementList});
                  DELETE FROM measurements WHERE id IN (${measurementList})`);
              }
              sql.execute(`DELETE FROM providerLabRoutingLock WHERE lab_no IN (${ids});
                DELETE FROM providerLabRouting WHERE lab_type='HL7' AND lab_no IN (${ids});
                DELETE FROM patientLabRouting WHERE lab_type='HL7' AND lab_no IN (${ids});
                DELETE FROM hl7TextInfo WHERE lab_no IN (${ids});
                DELETE FROM hl7TextMessage WHERE lab_id IN (${ids})`);
            }
            if (state.labFilePrefix) {
              sql.execute(`DELETE FROM fileUploadCheck WHERE filename LIKE ${filePattern}`);
              assert(sql.value(`SELECT COUNT(*) FROM fileUploadCheck WHERE filename LIKE ${filePattern}`) === '0',
                'The owned synthetic HL7 upload bookkeeping row was not removed');
              if (fileUploadIds.length) {
                assert(sql.value(`SELECT COUNT(*) FROM hl7TextMessage
                  WHERE fileUploadCheck_id IN (${fileUploadIds.join(',')})`) === '0',
                'The owned synthetic HL7 message rows were not removed');
                if (uploadedLabIds.length) {
                  assert(sql.value(`SELECT COUNT(*) FROM hl7TextInfo
                    WHERE lab_no IN (${uploadedLabIds.join(',')})`) === '0',
                  'The owned synthetic HL7 lab information rows were not removed');
                }
              }
              removeOwnedLabFile(state);
            }
            assert(sql.value(`SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=${accessionSql}`) === '0',
              'The owned synthetic HL7 lab rows were not removed');
          }
          sql.execute(`DELETE FROM drugReason WHERE demographicNo=${id};
            DELETE FROM drugs WHERE demographic_no=${id};
            DELETE FROM prescription WHERE demographic_no=${id};
            DELETE FROM DigitalSignature WHERE demographicId=${id} AND ModuleType='PRESCRIPTION';
            DELETE FROM measurementsExt WHERE measurement_id IN
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
              AND first_name IN ('AI-Scribe','AI-Scribe Updated') AND provider_no=${providerSql}`);
          assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${id}`) === '0',
            'The owned synthetic demographic was not removed');
          assert(sql.value(`SELECT (SELECT COUNT(*) FROM drugs WHERE demographic_no=${id})
              + (SELECT COUNT(*) FROM prescription WHERE demographic_no=${id})
              + (SELECT COUNT(*) FROM measurements WHERE demographicNo=${id})`) === '0',
          'Owned clinical test rows remain after cleanup');
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
  assert(!API_ONLY || API_ONLY === 'all' || API_TEST_GROUPS.has(API_ONLY),
    `Unsupported AI_SCRIBE_API_ONLY=${API_ONLY}; use all, demographics, measurements, `
      + 'measurements.read, measurements.write, labs, rxlookup, rx, schedule, or eforms');
  const config = readConfig();
  const sql = createSqlRunner(config.mysql);
  state.sql = sql;
  const app = (path) => `${config.baseUrl}${path}`;
  const runMarker = crypto.randomUUID().replace(/-/g, '');
  const marker = `PWAI${runMarker.slice(0, 12)}`;
  const today = new Date();
  const isoDate = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
  const tomorrow = new Date(today);
  tomorrow.setDate(tomorrow.getDate() + 1);
  const nextIsoDate = `${tomorrow.getFullYear()}-${String(tomorrow.getMonth() + 1).padStart(2, '0')}-${String(tomorrow.getDate()).padStart(2, '0')}`;
  const formName = `PW AI-Scribe ${runMarker}`;
  const consumerKey = crypto.randomBytes(12).toString('hex');
  const consumerSecret = crypto.randomBytes(24).toString('hex');
  const providerNo = sql.value(`SELECT provider_no FROM security
    WHERE user_name=${sqlString(config.testUser)} LIMIT 1`);
  if (!providerNo) throw new SkipCheck(`${config.testUser} has no security row; a real provider login is required`);
  const admissionProgramId = sql.value('SELECT id FROM program ORDER BY id LIMIT 1');
  assert(/^[1-9]\d*$/.test(admissionProgramId),
    'The install has no program to admit the synthetic demographic to');

  state.marker = marker;
  state.formName = formName;
  state.providerNo = providerNo;
  state.consumerKey = consumerKey;
  insertId(sql, `INSERT INTO ServiceClient(name,clientKey,clientSecret,uri,lifetime)
      VALUES(${sqlString(`PW AI-Scribe ${runMarker}`)},${sqlString(consumerKey)},
        ${sqlString(consumerSecret)},'oob',3600)`,
  'AI-Scribe OAuth client');

  const recorder = createRecorder();
  const endpointResults = [];
  const endpointRecords = new WeakMap();
  state.endpointResults = endpointResults;
  state.resultsPrinted = false;
  const recordEndpoint = (method, path, response, verification, outcome = 'PASS') => {
    const row = endpointRecords.get(response);
    if (row) {
      Object.assign(row, { outcome, path, status: response.status(), verification });
    } else {
      endpointResults.push({ outcome, method, path, status: response.status(), verification });
    }
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
  const signedRequest = async (method, path, token, body) => {
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
    const result = {
      outcome: 'PENDING',
      method,
      path: new URL(url).pathname,
      status: 'n/a',
      verification: 'request sent; response assertions pending',
    };
    endpointResults.push(result);
    try {
      const response = await anon.fetch(url, options);
      result.status = response.status();
      endpointRecords.set(response, result);
      return response;
    } catch (error) {
      result.outcome = 'FAIL';
      result.verification = `request failed: ${error.message}`;
      throw error;
    }
  };

  const infoResponse = await signedRequest('GET', '/ws/services/oauth/info', fullToken);
  await expectStatus(infoResponse, 200, 'Signed OAuth token identity check');
  assert(String((await infoResponse.json()).login) === String(providerNo),
    'The OAuth token is not bound to the provider who approved it');
  recordEndpoint('GET', '/ws/services/oauth/info', infoResponse, 'signed token identity verified');

  const createDemographicResponse = await signedRequest('POST', '/ws/services/demographics/', fullToken, {
    firstName: 'AI-Scribe',
    lastName: marker,
    dobYear: '1980',
    dobMonth: '01',
    dobDay: '02',
    sex: 'F',
    patientStatus: 'AC',
    rosterStatus: 'NR',
    providerNo,
    admissionProgramId: Number(admissionProgramId),
    hin: '',
    hcType: 'ON',
    address: { address: '', city: '', province: 'ON', postal: '' },
    previousAddress: { address: '', city: '', province: 'ON', postal: '' },
  });
  await expectStatus(createDemographicResponse, 200, 'OAuth synthetic demographic create');
  const createdDemographic = await createDemographicResponse.json();
  const patientNo = sqlId(createdDemographic.demographicNo, 'created synthetic demographic id');
  assert(createdDemographic.firstName === 'AI-Scribe' && createdDemographic.lastName === marker,
    'Demographic create did not return the uniquely owned synthetic record');
  state.patientNo = patientNo;
  recordEndpoint('POST', '/ws/services/demographics/', createDemographicResponse,
    'temporary patient created as selected-workflow setup');

  const measurementComment = `AI-Scribe OAuth measurement ${runMarker}`;
  sql.execute(`INSERT INTO measurements
      (type,demographicNo,providerNo,dataField,measuringInstruction,comments,dateObserved,dateEntered)
    VALUES ('BP',${patientNo},${sqlString(providerNo)},'121/79','',${sqlString(measurementComment)},NOW(),NOW())`);
  const measurementId = sql.value(`SELECT id FROM measurements
    WHERE demographicNo=${patientNo} AND type='BP' AND comments=${sqlString(measurementComment)}`);
  sqlId(measurementId, 'AI-Scribe measurement fixture id');

  const demographicPath = `/ws/services/demographics/${patientNo}`;
  if (shouldRun('demographics')) {
  const allDemographicsResponse = await signedRequest(
    'GET', '/ws/services/demographics/?offset=0&limit=2', fullToken,
  );
  await expectStatus(allDemographicsResponse, 200, 'OAuth paginated demographic list');
  const allDemographics = await allDemographicsResponse.json();
  const demographicPage = allDemographics.Item || allDemographics.content || [];
  assert(Array.isArray(demographicPage) && Number(allDemographics.total) >= 1,
    'Paginated demographic list did not return a result page');
  recordEndpoint('GET', '/ws/services/demographics/', allDemographicsResponse,
    'paginated patient-list response returned');

  const searchOffset = Number(sql.value(`SELECT COUNT(*) FROM demographic
    WHERE demographic_no < ${patientNo} AND patient_status <> 'DE'`));
  const patientPageResponse = await signedRequest(
    'GET', `/ws/services/demographics/?offset=${searchOffset}&limit=1`, fullToken,
  );
  await expectStatus(patientPageResponse, 200, 'OAuth demographic page containing the synthetic patient');
  const patientPage = await patientPageResponse.json();
  const patientItems = patientPage.Item || patientPage.content || [];
  assert(Array.isArray(patientItems)
    && patientItems.some((item) => String(item.demographicNo) === patientNo),
  'Paginated demographic list did not include the synthetic patient at its calculated offset');
  recordEndpoint('GET', '/ws/services/demographics/', patientPageResponse,
    'synthetic patient returned at the matching pagination offset');

  const demographicResponse = await signedRequest('GET', demographicPath, fullToken);
  await expectStatus(demographicResponse, 200, 'OAuth demographic fetch');
  const demographic = await demographicResponse.json();
  assert(String(demographic.demographicNo) === patientNo && demographic.lastName === marker,
    'OAuth demographic fetch did not return the owned synthetic record');
  recordEndpoint('GET', '/ws/services/demographics/{demographicNo}', demographicResponse,
    'owned synthetic demographic returned');

  const basicDemographicResponse = await signedRequest(
    'GET', `/ws/services/demographics/basic/${patientNo}`, fullToken,
  );
  await expectStatus(basicDemographicResponse, 200, 'OAuth basic demographic fetch');
  const basicDemographic = await basicDemographicResponse.json();
  assert(String(basicDemographic.demographicNo) === patientNo && basicDemographic.lastName === marker,
    'Basic demographic fetch did not return the synthetic patient');
  recordEndpoint('GET', '/ws/services/demographics/basic/{dataId}', basicDemographicResponse,
    'basic profile returned');

  const demographicSummaryResponse = await signedRequest(
    'GET', `/ws/services/demographics/summary/${patientNo}`, fullToken,
  );
  await expectStatus(demographicSummaryResponse, 200, 'OAuth demographic summary fetch');
  const demographicSummary = await demographicSummaryResponse.json();
  assert(String(demographicSummary.demographicNo) === patientNo
    && demographicSummary.lastName === marker,
  'Demographic summary did not return the synthetic patient');
  recordEndpoint('GET', '/ws/services/demographics/summary/{demographicNo}', demographicSummaryResponse,
    'short demographic summary returned');

  const quickSearchResponse = await signedRequest(
    'GET', `/ws/services/demographics/quickSearch?query=${encodeURIComponent(marker)}`, fullToken,
  );
  await expectStatus(quickSearchResponse, 200, 'OAuth demographic quick search');
  const quickSearch = await quickSearchResponse.json();
  assert(Array.isArray(quickSearch.content)
    && quickSearch.content.some((item) => String(item.demographicNo) === patientNo),
  'Name quick search did not return the synthetic patient');
  recordEndpoint('GET', '/ws/services/demographics/quickSearch', quickSearchResponse,
    'quickSearch finds the synthetic patient by last name');

  const demographicSearchResponse = await signedRequest(
    'POST', '/ws/services/demographics/search?startIndex=0&itemsToReturn=10', fullToken,
    { type: 'Name', term: marker, active: true },
  );
  await expectStatus(demographicSearchResponse, 200, 'OAuth demographic search request');
  const demographicSearch = await demographicSearchResponse.json();
  assert(Array.isArray(demographicSearch.content)
    && demographicSearch.content.some((item) => String(item.demographicNo) === patientNo),
  'Structured demographic search did not return the synthetic patient');
  recordEndpoint('POST', '/ws/services/demographics/search', demographicSearchResponse,
    'structured name search finds the synthetic patient');

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
  }

  if (shouldRun('measurements') || shouldRun('measurements.read')) {
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
  }

  if (shouldRun('measurements') || shouldRun('measurements.write')) {
    const newMeasurementComment = `AI-Scribe OAuth new measurement ${runMarker}`;
    const newMeasurementPayload = {
      type: 'BP',
      dataField: '122/80',
      comments: newMeasurementComment,
      measuringInstruction: 'Standing',
      dateObserved: Date.now(),
    };
    const limitedMeasurementCreate = await signedRequest(
      'POST', `/ws/services/measurements/${patientNo}/new`, limitedToken, newMeasurementPayload,
    );
    await expectInsufficientScope(limitedMeasurementCreate,
      'OAuth new measurement without measurement.write');
    recordEndpoint('POST', '/ws/services/measurements/{demographicNo}/new', limitedMeasurementCreate,
      'missing measurement.write refused with insufficient_scope');

    const newMeasurementResponse = await signedRequest(
      'POST', `/ws/services/measurements/${patientNo}/new`, fullToken, newMeasurementPayload,
    );
    await expectStatus(newMeasurementResponse, 200, 'OAuth new measurement create');
    const newMeasurement = await newMeasurementResponse.json();
    const newMeasurementId = sqlId(newMeasurement.id, 'created new measurement id');
    assert(newMeasurementId !== measurementId
      && String(newMeasurement.demographicId) === patientNo
      && newMeasurement.type === newMeasurementPayload.type
      && newMeasurement.dataField === newMeasurementPayload.dataField
      && newMeasurement.comments === newMeasurementComment,
    'OAuth new measurement did not return the saved patient reading');
    recordEndpoint('POST', '/ws/services/measurements/{demographicNo}/new', newMeasurementResponse,
      'new BP measurement recorded for the patient');

    const newMeasurementReadResponse = await signedRequest(
      'POST', `/ws/services/measurements/${patientNo}`, fullToken, { types: ['BP'] },
    );
    await expectStatus(newMeasurementReadResponse, 200, 'OAuth new measurement read-after-write');
    const newMeasurementRead = await newMeasurementReadResponse.json();
    const bpRowsAfterCreate = newMeasurementRead.measurements && newMeasurementRead.measurements.BP;
    assert(Array.isArray(bpRowsAfterCreate)
      && bpRowsAfterCreate.some((row) => String(row.id) === newMeasurementId
        && row.dataField === newMeasurementPayload.dataField && row.comments === newMeasurementComment)
      && bpRowsAfterCreate.some((row) => String(row.id) === measurementId
        && row.dataField === '121/79' && row.comments === measurementComment),
    'OAuth new measurement was not persisted or an existing reading changed');
    recordEndpoint('POST', '/ws/services/measurements/{demographicNo}', newMeasurementReadResponse,
      'new reading persisted and previous reading retained on read-after-write');
  }

  if (API_ONLY.startsWith('measurements')) {
    assertStrictPage(recorder);
    const failedEndpoints = endpointResults.filter((row) => row.outcome === 'FAIL');
    assert(failedEndpoints.length === 0,
      `${failedEndpoints.length} endpoint validation(s) failed; see the endpoint results table`);
    return {
      oauthMode: 'scoped',
      selected: API_ONLY,
      checked: ['OAuth bootstrap', 'temporary demographic fixture setup', `${API_ONLY} API workflow`],
    };
  }

  if (shouldRun('labs')) {
  const labReadResponse = await signedRequest(
    'GET', `/ws/services/labs/hl7LabsByDemographicNo?demographicNo=${patientNo}&offset=0&limit=10`, fullToken,
  );
  await expectStatus(labReadResponse, 200, 'OAuth HL7 lab read');
  const labRead = await labReadResponse.json();
  assert(labRead && Array.isArray(labRead.messages),
    'HL7 lab read did not return a message list');
  recordEndpoint('GET', '/ws/services/labs/hl7LabsByDemographicNo', labReadResponse,
    'patient lab message list returned');

  const invalidLabUploadResponse = await signedRequest(
    'POST', '/ws/services/labs/hl7LabUpload', fullToken,
    { fileName: `${marker}.hl7`, type: 'PHS', base64EncodedeMessage: '' },
  );
  await expectStatus(invalidLabUploadResponse, 400, 'OAuth HL7 lab upload validation');
  recordEndpoint('POST', '/ws/services/labs/hl7LabUpload', invalidLabUploadResponse,
    'malformed upload rejected before import');

  const documentStore = process.env.AI_SCRIBE_DOCUMENT_STORE;
  if (!documentStore) {
    endpointResults.push({
      outcome: 'UNVERIFIED',
      method: 'POST',
      path: '/ws/services/labs/hl7LabUpload',
      status: 'not run',
      verification: 'valid HL7 import requires AI_SCRIBE_DOCUMENT_STORE so cleanup can remove its archived file',
    });
  } else {
    state.documentStore = fs.realpathSync(documentStore);
    assert(fs.statSync(state.documentStore).isDirectory(),
      'AI_SCRIBE_DOCUMENT_STORE must point to the running server document directory');
    state.labRunMarker = runMarker;
    state.labAccession = `PWAI-${runMarker}`;
    state.labFilePrefix = `LabUpload.ai-scribe-${runMarker}.xml.`;
    const accessionSql = sqlString(state.labAccession);
    assert(sql.value(`SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=${accessionSql}`) === '0',
      'The synthetic lab accession already exists');

    const hl7Timestamp = new Date().toISOString().replace(/\D/g, '').slice(0, 14);
    const hl7Segments = [
      `MSH|^~\\&|PATHL7|CW|CARLOS|TEST|${hl7Timestamp}||ORU^R01|${runMarker}|P|2.3|||ER|AL`,
      `PID|1||||${marker}^AI-Scribe Updated||19800102|F`,
      `ORC|NW|${state.labAccession}|${state.labAccession}||F|||||||999998^DR. PROBE|||${hl7Timestamp}`,
      `OBR|1|${state.labAccession}||ML70^SYNTHETIC PANEL||${hl7Timestamp}|${hl7Timestamp}|||||||||999998^DR. PROBE|||||||||F`,
      'OBX|1|ST|7010^SYNTHETIC RESULT|^^CHEMISTRY|NORMAL|||N|||F||765^1007010||70',
    ];
    const escapeXml = (text) => text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    const hl7Xml = `<messages><message>${hl7Segments.map(escapeXml).join('&#13;')}</message></messages>`;
    const labFileName = `ai-scribe-${runMarker}.xml`;
    const uploadLabResponse = await signedRequest(
      'POST', '/ws/services/labs/hl7LabUpload', fullToken,
      {
        fileName: labFileName,
        type: 'PATHL7',
        base64EncodedeMessage: Buffer.from(hl7Xml, 'utf8').toString('base64'),
      },
    );
    await expectStatus(uploadLabResponse, 200, 'OAuth synthetic HL7 lab upload');
    const uploadedLab = await uploadLabResponse.json();
    assert(uploadedLab && uploadedLab.fileName, 'HL7 lab upload did not return its saved file name');
    const labNo = sqlId(sql.value(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum=${accessionSql}`),
      'stored synthetic HL7 lab id');
    recordEndpoint('POST', '/ws/services/labs/hl7LabUpload', uploadLabResponse,
      `synthetic PATHL7 result persisted as lab ${labNo}`);

    const importedLabReadResponse = await signedRequest(
      'GET', `/ws/services/labs/hl7LabsByDemographicNo?demographicNo=${patientNo}&offset=0&limit=10`,
      fullToken,
    );
    await expectStatus(importedLabReadResponse, 200, 'OAuth synthetic HL7 lab read-after-upload');
    const importedLabRead = await importedLabReadResponse.json();
    assert(Array.isArray(importedLabRead.messages)
      && importedLabRead.messages.some((message) => String(message.id) === labNo),
    'The uploaded synthetic lab was not returned for the owned patient');
    recordEndpoint('GET', '/ws/services/labs/hl7LabsByDemographicNo', importedLabReadResponse,
      'uploaded synthetic result is linked to and retrievable for the owned patient');
  }
  }

  if (shouldRun('rxlookup')) {
  const drugLookupResponse = await signedRequest(
    'GET', `/ws/services/rxlookup/search?string=${encodeURIComponent('acetaminophen')}`, fullToken,
  );
  await expectStatus(drugLookupResponse, 200, 'OAuth drug lookup search');
  const drugLookup = await drugLookupResponse.json();
  assert(drugLookup.success === true && Array.isArray(drugLookup.drugs) && drugLookup.drugs.length > 0,
    'Drug lookup search did not return a medication entry');
  const detailCandidate = drugLookup.drugs.find((drug) => drug.category === 13) || drugLookup.drugs[0];
  const drugCatalogId = detailCandidate.id;
  assert(drugCatalogId !== null && drugCatalogId !== undefined,
    'Drug lookup search returned an entry without a catalog id');
  recordEndpoint('GET', '/ws/services/rxlookup/search', drugLookupResponse,
    'drug catalog search returned an entry');

  let drugDetailsResponse;
  try {
    drugDetailsResponse = await signedRequest(
      'GET', `/ws/services/rxlookup/details?id=${encodeURIComponent(drugCatalogId)}`, fullToken,
    );
    await expectStatus(drugDetailsResponse, 200, 'OAuth drug lookup details');
    const drugDetails = await drugDetailsResponse.json();
    const detailDrug = Array.isArray(drugDetails.drugs) ? drugDetails.drugs[0] : null;
    assert(drugDetails.success === true && Array.isArray(drugDetails.drugs)
      && drugDetails.drugs.length === 1 && typeof detailDrug?.name === 'string'
      && detailDrug.name.trim() !== '',
    `Drug lookup details did not return a populated drug record (success=${drugDetails.success}, `
      + `records=${drugDetails.drugs?.length ?? 0}, fields=${detailDrug ? Object.keys(detailDrug).join(',') : 'none'}, `
      + `namePresent=${typeof detailDrug?.name === 'string' && detailDrug.name.trim() !== ''}, `
      + `components=${Array.isArray(detailDrug?.components) ? detailDrug.components.length : 'missing'})`);
    recordEndpoint('GET', '/ws/services/rxlookup/details', drugDetailsResponse,
      'drug detail returned for the selected catalog id');
  } catch (error) {
    const verification = `details lookup failed: ${error.message || 'response validation failed'}`;
    if (drugDetailsResponse) {
      recordEndpoint('GET', '/ws/services/rxlookup/details', drugDetailsResponse, verification, 'FAIL');
    } else {
      endpointResults.push({
        outcome: 'FAIL',
        method: 'GET',
        path: '/ws/services/rxlookup/details',
        status: 'n/a',
        verification,
      });
    }
  }

  const parseInstructions = 'Take 1 tablet by mouth twice daily for 7 days';
  const parseDrugResponse = await signedRequest(
    'POST', `/ws/services/rxlookup/parse?input=${encodeURIComponent(parseInstructions)}`, fullToken,
  );
  await expectStatus(parseDrugResponse, 200, 'OAuth prescription instruction parsing');
  const parsedDrug = await parseDrugResponse.json();
  assert(parsedDrug.drug && parsedDrug.drug.duration === 7,
    'Instruction parsing did not extract the seven-day duration');
  recordEndpoint('POST', '/ws/services/rxlookup/parse', parseDrugResponse,
    'prescription instructions parsed into drug fields');
  }

  if (shouldRun('rx')) {
  const rxReadRoutes = [
    ['/rx/drugs', 'all drugs'],
    [`/rx/drugs/all/${patientNo}`, 'all-drugs view'],
    [`/rx/drugs/current/${patientNo}`, 'current-drugs view'],
    [`/rx/drugs/longterm/${patientNo}`, 'long-term-drugs view'],
    [`/rx/drugs/archived/${patientNo}`, 'archived-drugs view'],
  ];
  for (const [route, description] of rxReadRoutes) {
    const path = route === '/rx/drugs'
      ? `${route}?demographicNo=${patientNo}` : route;
    let response;
    try {
      response = await signedRequest('GET', `/ws/services${path}`, fullToken);
      await expectStatus(response, 200, `OAuth ${description}`);
      const body = await response.json();
      const bodyShape = Array.isArray(body) ? 'array'
        : body && typeof body === 'object' ? `object with fields ${Object.keys(body).join(',')}`
          : typeof body;
      assert(body && Array.isArray(body.drug),
        `${description} did not return a drug list (response shape: ${bodyShape})`);
      recordEndpoint('GET', `/ws/services${route}`, response, `${description} returned`);
    } catch (error) {
      const verification = `${description} failed: ${error.message || 'response validation failed'}`;
      if (response) {
        recordEndpoint('GET', `/ws/services${route}`, response, verification, 'FAIL');
      } else {
        endpointResults.push({
          outcome: 'FAIL',
          method: 'GET',
          path: `/ws/services${route}`,
          status: 'n/a',
          verification,
        });
      }
    }
  }

  const medicationName = `AI-Scribe synthetic medication ${runMarker}`;
  const medication = {
    customName: medicationName,
    genericName: medicationName,
    demographicNo: Number(patientNo),
    providerNo,
    takeMin: 1,
    takeMax: 1,
    rxDate: isoDate,
    writtenDate: isoDate,
    endDate: nextIsoDate,
    frequency: 'BID',
    duration: 7,
    durationUnit: 'D',
    route: 'PO',
    form: 'tablet',
    method: 'oral',
    strength: 500,
    strengthUnit: 'mg',
    prn: false,
    archived: false,
    repeats: 0,
    quantity: 14,
    instructions: 'AI-Scribe test fixture: take one tablet twice daily',
    longTerm: false,
    noSubstitutions: false,
  };
  const addDrugResponse = await signedRequest(
    'POST', `/ws/services/rx/new?demographicNo=${patientNo}`, fullToken, medication,
  );
  await expectStatus(addDrugResponse, 200, 'OAuth synthetic medication create');
  const addedDrugBody = await addDrugResponse.json();
  assert(addedDrugBody.success === true && addedDrugBody.drug && addedDrugBody.drug.drugId,
    `Medication create did not return a saved drug (success=${addedDrugBody.success}, `
      + `fields=${Object.keys(addedDrugBody).join(',')}, `
      + `drugIdPresent=${Boolean(addedDrugBody.drug?.drugId)}, message=${addedDrugBody.message || ''})`);
  const addedDrug = addedDrugBody.drug;
  recordEndpoint('POST', '/ws/services/rx/new', addDrugResponse,
    'synthetic medication created for the owned patient');

  const represcribeResponse = await signedRequest(
    'POST', `/ws/services/rx/drug/${addedDrug.drugId}`, fullToken, {},
  );
  await expectStatus(represcribeResponse, 200, 'OAuth pre-prescribe/represcribe lookup');
  const represcribed = await represcribeResponse.json();
  assert(represcribed.success === true && String(represcribed.drug.drugId) === String(addedDrug.drugId),
    'Pre-prescribe endpoint did not return the saved synthetic medication');
  recordEndpoint('POST', '/ws/services/rx/drug/{drugId}', represcribeResponse,
    'prescribing candidate returned without changing the saved medication');

  const updatedMedication = {
    ...addedDrug,
    instructions: 'AI-Scribe test fixture: updated to one tablet once daily',
    frequency: 'OD',
  };
  const updateDrugResponse = await signedRequest(
    'POST', `/ws/services/rx/update?demographicNo=${patientNo}`, fullToken, updatedMedication,
  );
  await expectStatus(updateDrugResponse, 200, 'OAuth synthetic medication update');
  const updatedDrugBody = await updateDrugResponse.json();
  assert(updatedDrugBody.success === true && updatedDrugBody.drug
    && String(updatedDrugBody.drug.drugId) !== String(addedDrug.drugId),
  'Medication update did not return the replacement drug row');
  const updatedMedicationRow = updatedDrugBody.drug;
  recordEndpoint('POST', '/ws/services/rx/update', updateDrugResponse,
    'updated medication saved as a replacement row');

  const prescribeResponse = await signedRequest(
    'POST', `/ws/services/rx/prescribe?demographicNo=${patientNo}`, fullToken, [updatedMedicationRow],
  );
  await expectStatus(prescribeResponse, 200, 'OAuth prescription create');
  const prescriptionResult = await prescribeResponse.json();
  assert(prescriptionResult.success === true && prescriptionResult.prescription
    && Array.isArray(prescriptionResult.drugs) && prescriptionResult.drugs.length === 1,
  'Prescription create did not return the prescription and its drug');
  const prescribedDrug = prescriptionResult.drugs[0];
  const latestDrugId = sqlId(prescribedDrug.drugId, 'prescribed synthetic drug id');
  recordEndpoint('POST', '/ws/services/rx/prescribe', prescribeResponse,
    'prescription created for the synthetic patient');

  const discontinueResponse = await signedRequest(
    'POST',
    `/ws/services/rx/discontinue?drugId=${latestDrugId}&demographicNo=${patientNo}&reason=other`,
    fullToken,
  );
  await expectStatus(discontinueResponse, 200, 'OAuth synthetic medication discontinue');
  const discontinueResult = await discontinueResponse.json();
  assert(discontinueResult.status === 'SUCCESS',
    'Medication discontinue did not return a success response');
  recordEndpoint('POST', '/ws/services/rx/discontinue', discontinueResponse,
    'synthetic medication discontinued');

  const rxHistoryResponse = await signedRequest(
    'GET', `/ws/services/rx/history?id=${latestDrugId}&demographicNo=${patientNo}`, fullToken,
  );
  await expectStatus(rxHistoryResponse, 200, 'OAuth prescription history');
  assert(Array.isArray((await rxHistoryResponse.json()).drug),
    'Prescription history did not return a drug list');
  recordEndpoint('GET', '/ws/services/rx/history', rxHistoryResponse,
    'patient prescription history returned');

  const prescriptionsResponse = await signedRequest(
    'GET', `/ws/services/rx/prescriptions?demographicNo=${patientNo}`, fullToken,
  );
  await expectStatus(prescriptionsResponse, 200, 'OAuth prescriptions list');
  assert(Array.isArray(await prescriptionsResponse.json()),
    'Prescriptions endpoint did not return a list');
  recordEndpoint('GET', '/ws/services/rx/prescriptions', prescriptionsResponse,
    'patient prescription list returned');
  }

  if (shouldRun('schedule')) {
  const scheduleRoutes = [
    ['/schedule/day/today', 'daily appointment list'],
    ['/schedule/statuses', 'appointment statuses'],
  ];
  const year = today.getFullYear();
  const month = today.getMonth() + 1;
  scheduleRoutes.push(
    [`/schedule/fetchMonthly/${providerNo}/${year}/${month}`, 'monthly appointment list'],
    [`/schedule/fetchProviderAppts/${providerNo}/${isoDate}/${isoDate}`, 'provider appointment list for period'],
    [`/schedule/fetchDays/${isoDate}/${isoDate}/${providerNo}`, 'appointments by period and provider'],
  );
  for (const [path, description] of scheduleRoutes) {
    const response = await signedRequest('GET', `/ws/services${path}`, fullToken);
    await expectStatus(response, 200, `OAuth ${description}`);
    await response.json();
    recordEndpoint('GET', `/ws/services${path}`, response, `${description} returned`);
  }

  const existingAppointmentsResponse = await signedRequest(
    'POST', `/ws/services/schedule/${patientNo}/appointmentHistory`, fullToken, {},
  );
  await expectStatus(existingAppointmentsResponse, 200, 'OAuth existing appointments for synthetic patient');
  assert(Array.isArray((await existingAppointmentsResponse.json()).appointments),
    'Existing appointments response did not contain an appointment list');
  recordEndpoint('POST', '/ws/services/schedule/{demographicNo}/appointmentHistory',
    existingAppointmentsResponse, 'patient appointment history returned');

  const availabilityResponse = await signedRequest(
    'GET',
    `/ws/services/schedule/${isoDate}/${providerNo}/09%3A00/09%3A30/checkProvAvali`,
    fullToken,
  );
  assert([404, 405].includes(availabilityResponse.status()),
    `Provider-availability route should be unavailable, got HTTP ${availabilityResponse.status()}`);
  recordEndpoint('GET', '/ws/services/schedule/{date}/{provider}/{start}/{end}/checkProvAvali',
    availabilityResponse, 'availability operation is commented out and not routed', 'GAP');
  }

  if (shouldRun('eforms')) {
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
  }

  if (shouldRun('demographics')) {
  const deleteDemographicResponse = await signedRequest(
    'DELETE', demographicPath, fullToken,
  );
  await expectStatus(deleteDemographicResponse, 200, 'OAuth synthetic demographic delete');
  const deletedDemographic = await deleteDemographicResponse.json();
  assert(String(deletedDemographic.demographicNo) === patientNo
    && deletedDemographic.lastName === marker,
  'Demographic delete did not identify the owned synthetic patient');
  state.demographicDeleted = true;
  recordEndpoint('DELETE', '/ws/services/demographics/{dataId}', deleteDemographicResponse,
    'patient deletion endpoint returned the owned patient; cleanup verifies no row remains');
  }

  assertStrictPage(recorder);
  const failedEndpoints = endpointResults.filter((row) => row.outcome === 'FAIL');
  assert(failedEndpoints.length === 0,
    `${failedEndpoints.length} endpoint validation(s) failed; see the endpoint results table`);
  return {
    oauthMode: 'scoped',
    selected: API_ONLY || 'all',
    checked: ['signed OAuth request-token and access-token flow',
      `${API_ONLY && API_ONLY !== 'all' ? API_ONLY : 'all'} API workflow(s)`],
  };
}

if (require.main === module) {
  const state = {};
  runCheck({
    name: 'ai-scribe-oauth-api',
    run: async () => {
      try {
        return await main(state);
      } catch (error) {
        state.failureMessage = error.message || 'check failed';
        if (!(state.endpointResults || []).some((row) =>
          row.outcome === 'PENDING' || row.outcome === 'FAIL')) {
          state.endpointResults = state.endpointResults || [];
          state.endpointResults.push({
            outcome: 'FAIL',
            method: 'RUN',
            path: 'acceptance workflow',
            status: 'n/a',
            verification: state.failureMessage,
          });
        }
        throw error;
      }
    },
    cleanup: async () => {
      try {
        await cleanup(state);
      } catch (error) {
        state.endpointResults = state.endpointResults || [];
        state.endpointResults.push({
          outcome: 'FAIL',
          method: 'CLEANUP',
          path: 'owned synthetic fixtures',
          status: 'n/a',
          verification: error.message || 'cleanup failed',
        });
        throw error;
      } finally {
        const results = state.endpointResults || [];
        for (const row of results) {
          if (row.outcome === 'PENDING') {
            row.outcome = 'FAIL';
            row.verification = `request returned; validation incomplete after: ${state.failureMessage || 'check ended'}`;
          }
        }
        if (!state.resultsPrinted) {
          printEndpointResults(results);
          state.resultsPrinted = true;
        }
      }
    },
  });
}

module.exports = { main, cleanup };
