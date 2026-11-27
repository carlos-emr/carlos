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
 * The check owns one synthetic patient, two BP measurements (a SQL fixture and,
 * when the measurement-write group runs, the API reading), one synthetic
 * medication/prescription, one eForm template, OAuth clients, and their issued
 * credentials. Cleanup removes only rows keyed by the unique run markers,
 * including when a request fails part-way through or the run is interrupted
 * (SIGINT/SIGTERM stop the run before its next request; runCheck then runs
 * cleanup). It requires
 * CARLOS_DISPOSABLE_HOST=true and EXPECT_OAUTH_MODE=scoped.
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
  launchBrowser, login, newContext, readConfig, runCheck, wireStrictPage,
} = require('./lib/playwright-harness');
const { oauthHeader, pct } = require('./lib/oauth1-signing');

const EXPECT_OAUTH_MODE = process.env.EXPECT_OAUTH_MODE || 'scoped';
const API_ONLY = process.env.AI_SCRIBE_API_ONLY?.trim().toLowerCase() || '';
const API_TEST_GROUPS = new Set([
  'demographics', 'measurements', 'measurements.read', 'measurements.write',
  'labs', 'rxlookup', 'rx', 'schedule', 'eforms', 'eforms.templates',
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
  const rows = results.map(({ outcome, method, path: endpointPath, status, verification }) => ({
    outcome,
    endpoint: `${method} ${endpointPath}`,
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
  // A browser that fails to close must not skip the database cleanup, and its error must still
  // reach the caller (a `return` inside `finally` used to swallow it when there was no SQL runner).
  let browserCloseError = null;
  try {
    if (state.browser) await state.browser.close();
  } catch (error) {
    browserCloseError = error;
  }
  if (sql) {
    try {
      if (marker && providerNo) {
        const patient = state.patientNo || sql.value(`SELECT demographic_no FROM demographic
          WHERE last_name=? AND first_name IN ('AI-Scribe','AI-Scribe Updated')
            AND provider_no=?`, [marker, providerNo]);
        if (patient) {
          const id = sqlId(patient, 'owned synthetic demographic id');
          const ownedRows = sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=?
            AND last_name=? AND first_name IN ('AI-Scribe','AI-Scribe Updated')
            AND provider_no=?`, [id, marker, providerNo]);
          const anyRows = sql.value('SELECT COUNT(*) FROM demographic WHERE demographic_no=?', [id]);
          // Zero owned rows is the API-deleted state only when no row has this id at all. A row that
          // exists without the run's marker is someone else's patient: its dependent rows must not
          // be deleted, so cleanup stops here.
          assert(ownedRows === '1' || (state.patientNo && ownedRows === '0' && anyRows === '0'),
            ownedRows === '0' && anyRows !== '0'
              ? 'The synthetic demographic id now names a patient without this run\'s marker; cleanup stopped'
              : 'The synthetic demographic ownership check did not match the created patient or its deleted state');
          // A lab-cleanup failure must not leave the patient's other clinical rows behind: it is
          // held until the rest of the patient cleanup below has run, then rethrown.
          let labCleanupError = null;
          try {
            if (state.labAccession) {
              const filePattern = state.labFilePrefix
                ? `%${state.labRunMarker}%`
                : null;
              const fileUploadIds = filePattern
                ? sql.rows(
                    'SELECT id FROM fileUploadCheck WHERE filename LIKE ?',
                    [filePattern],
                  )
                    .map(([id]) => id)
                    .filter((id) => /^[1-9]\d*$/.test(id))
                : [];
              const fileUploadIdPlaceholders = fileUploadIds.map(() => '?').join(',');
              const uploadedLabIds = fileUploadIds.length
                ? sql.rows(
                    `SELECT lab_id FROM hl7TextMessage
                      WHERE fileUploadCheck_id IN (${fileUploadIdPlaceholders})`,
                    fileUploadIds,
                  )
                    .map(([labId]) => labId)
                    .filter((labId) => /^[1-9]\d*$/.test(labId))
                : [];
              const labIds = [...new Set([
                ...sql.rows(
                  'SELECT lab_no FROM hl7TextInfo WHERE accessionNum=?',
                  [state.labAccession],
                )
                  .map(([labNo]) => labNo)
                  .filter((labNo) => /^[1-9]\d*$/.test(labNo)),
                ...uploadedLabIds,
              ])];

              if (labIds.length) {
                const labIdPlaceholders = labIds.map(() => '?').join(',');
                const measurementIds = [...new Set(
                  sql.rows(
                    `SELECT measurement_id FROM measurementsExt
                      WHERE keyval='lab_no' AND val IN (${labIdPlaceholders})`,
                    labIds,
                  )
                    .map(([measurementId]) => measurementId)
                    .filter((measurementId) => /^[1-9]\d*$/.test(measurementId)),
                )];

                if (measurementIds.length) {
                  const measurementPlaceholders = measurementIds.map(() => '?').join(',');
                  sql.execute(
                    `DELETE FROM measurementsExt
                      WHERE measurement_id IN (${measurementPlaceholders})`,
                    measurementIds,
                  );
                  sql.execute(
                    `DELETE FROM measurements
                      WHERE id IN (${measurementPlaceholders})`,
                    measurementIds,
                  );
                }

                sql.execute(
                  `DELETE FROM providerLabRouting
                    WHERE lab_type='HL7' AND lab_no IN (${labIdPlaceholders})`,
                  labIds,
                );
                // providerLabRoutingLock is keyed by a bare lab_no that other lab types (documents,
                // other HL7 sources) share and that the row's upsert never re-stamps. Remove only a
                // lock row this run created (stamped at or after the pre-upload floor) for an owned
                // lab that no remaining routing row of any type still uses.
                if (state.labLockFloor) {
                  sql.execute(
                    `DELETE FROM providerLabRoutingLock
                      WHERE lab_no IN (${labIdPlaceholders}) AND lastUpdateDate >= ?
                        AND NOT EXISTS (SELECT 1 FROM providerLabRouting r
                          WHERE r.lab_no = providerLabRoutingLock.lab_no)`,
                    [...labIds, state.labLockFloor],
                  );
                }
                sql.execute(
                  `DELETE FROM patientLabRouting
                    WHERE lab_type='HL7' AND lab_no IN (${labIdPlaceholders})`,
                  labIds,
                );
                sql.execute(
                  `DELETE FROM hl7TextInfo WHERE lab_no IN (${labIdPlaceholders})`,
                  labIds,
                );
                sql.execute(
                  `DELETE FROM hl7TextMessage WHERE lab_id IN (${labIdPlaceholders})`,
                  labIds,
                );
              }

              if (state.labFilePrefix) {
                sql.execute(
                  'DELETE FROM fileUploadCheck WHERE filename LIKE ?',
                  [filePattern],
                );
                assert(
                  sql.value(
                    'SELECT COUNT(*) FROM fileUploadCheck WHERE filename LIKE ?',
                    [filePattern],
                  ) === '0',
                  'The owned synthetic HL7 upload bookkeeping row was not removed',
                );

                if (fileUploadIds.length) {
                  assert(
                    sql.value(
                      `SELECT COUNT(*) FROM hl7TextMessage
                        WHERE fileUploadCheck_id IN (${fileUploadIdPlaceholders})`,
                      fileUploadIds,
                    ) === '0',
                    'The owned synthetic HL7 message rows were not removed',
                  );

                  if (uploadedLabIds.length) {
                    const uploadedLabPlaceholders = uploadedLabIds.map(() => '?').join(',');
                    assert(
                      sql.value(
                        `SELECT COUNT(*) FROM hl7TextInfo
                          WHERE lab_no IN (${uploadedLabPlaceholders})`,
                        uploadedLabIds,
                      ) === '0',
                      'The owned synthetic HL7 lab information rows were not removed',
                    );
                  }
                }
                removeOwnedLabFile(state);
              }

              assert(
                sql.value(
                  'SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=?',
                  [state.labAccession],
                ) === '0',
                'The owned synthetic HL7 lab rows were not removed',
              );
            }
          } catch (error) {
            labCleanupError = error;
          }
          sql.execute('DELETE FROM drugReason WHERE demographicNo=?', [id]);
          sql.execute('DELETE FROM drugs WHERE demographic_no=?', [id]);
          sql.execute('DELETE FROM prescription WHERE demographic_no=?', [id]);
          sql.execute(`DELETE FROM DigitalSignature
            WHERE demographicId=? AND ModuleType='PRESCRIPTION'`, [id]);
          sql.execute(`DELETE FROM measurementsExt WHERE measurement_id IN
            (SELECT id FROM measurements WHERE demographicNo=?)`, [id]);
          sql.execute('DELETE FROM measurements WHERE demographicNo=?', [id]);
          sql.execute('DELETE FROM demographicExtArchive WHERE demographic_no=?', [id]);
          sql.execute('DELETE FROM demographicExt WHERE demographic_no=?', [id]);
          sql.execute('DELETE FROM demographicArchive WHERE demographic_no=?', [id]);
          sql.execute('DELETE FROM demographiccustArchive WHERE demographic_no=?', [id]);
          sql.execute('DELETE FROM demographiccust WHERE demographic_no=?', [id]);
          sql.execute('DELETE FROM admission WHERE client_id=?', [id]);
          sql.execute('DELETE FROM DemographicContact WHERE demographicNo=?', [id]);
          sql.execute('DELETE FROM demographicPharmacy WHERE demographic_no=?', [id]);
          sql.execute(`DELETE FROM demographic WHERE demographic_no=?
            AND last_name=? AND first_name IN ('AI-Scribe','AI-Scribe Updated')
            AND provider_no=?`, [id, marker, providerNo]);

          assert(sql.value('SELECT COUNT(*) FROM demographic WHERE demographic_no=?', [id]) === '0',
            'The owned synthetic demographic was not removed');
          assert(sql.value(`SELECT (SELECT COUNT(*) FROM drugs WHERE demographic_no=?)
              + (SELECT COUNT(*) FROM prescription WHERE demographic_no=?)
              + (SELECT COUNT(*) FROM measurements WHERE demographicNo=?)`,
          [id, id, id]) === '0',
          'Owned clinical test rows remain after cleanup');
          if (labCleanupError) throw labCleanupError;
        }
      }
    } finally {
      try {
        if (formName && providerNo) {
          sql.execute(
            'DELETE FROM eform WHERE form_name=? AND form_creator=?',
            [formName, providerNo],
          );
          assert(
            sql.value(
              'SELECT COUNT(*) FROM eform WHERE form_name=? AND form_creator=?',
              [formName, providerNo],
            ) === '0',
            'The owned synthetic eForm template was not removed',
          );
        }
      } finally {
        try {
          if (consumerKey) {
            const clientIds = sql.rows(
              'SELECT id FROM ServiceClient WHERE clientKey=?',
              [consumerKey],
            ).map(([id]) => id).filter((id) => /^[1-9]\d*$/.test(id));

            if (clientIds.length) {
              const placeholders = clientIds.map(() => '?').join(',');
              sql.execute(
                `DELETE FROM ServiceAccessToken WHERE clientId IN (${placeholders})`,
                clientIds,
              );
              sql.execute(
                `DELETE FROM ServiceRequestToken WHERE clientId IN (${placeholders})`,
                clientIds,
              );
            }

            sql.execute(
              'DELETE FROM ServiceOAuthNonce WHERE consumerKey=?',
              [consumerKey],
            );
            sql.execute(
              'DELETE FROM ServiceClient WHERE clientKey=?',
              [consumerKey],
            );

            assert(
              sql.value(
                `SELECT (SELECT COUNT(*) FROM ServiceClient WHERE clientKey=?)
                  + (SELECT COUNT(*) FROM ServiceOAuthNonce WHERE consumerKey=?)`,
                [consumerKey, consumerKey],
              ) === '0',
              'The owned OAuth client or its nonces were not removed',
            );
          }
        } finally {
          sql.dispose();
        }
      }
    }
  }
  if (browserCloseError) throw browserCloseError;
}

/**
 * Endpoint result rows for the run's table. `recordEndpoint` updates the row
 * `signedRequest` opened for a response (or adds one); `recordFailure` marks a failed
 * check, reusing the transport-failure row when the request itself failed, so it is reported once.
 */
function createEndpointRecorder(endpointResults) {
  const endpointRecords = new WeakMap();
  const recordEndpoint = (method, endpointPath, response, verification, outcome = 'PASS') => {
    const row = endpointRecords.get(response);
    if (row) {
      Object.assign(row, { outcome, path: endpointPath, status: response.status(), verification });
    } else {
      endpointResults.push({ outcome, method, path: endpointPath, status: response.status(), verification });
    }
  };
  // A failed check of one request: updates the row its response recorded, or, when the request
  // itself failed (timeout, connection reset), the FAIL row signedRequest already recorded, so a
  // transport failure is reported once.
  const recordFailure = (method, endpointPath, response, error, verification) => {
    if (response) {
      recordEndpoint(method, endpointPath, response, verification, 'FAIL');
    } else if (error && error.endpointRecord) {
      Object.assign(error.endpointRecord, { outcome: 'FAIL', path: endpointPath, verification });
    } else {
      endpointResults.push({ outcome: 'FAIL', method, path: endpointPath, status: 'n/a', verification });
    }
  };
  return { endpointRecords, recordEndpoint, recordFailure };
}

/**
 * Returns authorize(scopes): the out-of-band OAuth 1.0a flow for this run's client, approved in the
 * signed-in provider's browser session.
 */
function createAuthorizer({
  state, app, anon, sessionContext, recorder, consumerKey, consumerSecret, recordEndpoint,
}) {
  return async function authorize(scopes) {
    if (state.throwIfCancelled) state.throwIfCancelled();
    // Cooperative cancellation: checked before each step, so a step already sent finishes and the
    // next one is not started once SIGINT/SIGTERM is recorded.
    const checkCancelled = () => { if (state.throwIfCancelled) state.throwIfCancelled(); };
    const initiateUrl = app(`/ws/oauth/initiate?scope=${pct(scopes)}`);
    let response = await handshakeStep('OAuth request-token initiation', () => anon.post(initiateUrl, {
      headers: {
        Authorization: oauthHeader({
          method: 'POST', url: initiateUrl, consumerKey, consumerSecret, extra: { oauth_callback: 'oob' },
        }),
      },
    }));
    await expectStatus(response, 200, 'OAuth request-token initiation');
    const requestToken = formFields(await response.text());
    assert(requestToken.oauth_token && requestToken.oauth_token_secret
      && requestToken.oauth_callback_confirmed === 'true',
    'OAuth initiation did not return a confirmed out-of-band request token');
    recordEndpoint('POST', '/ws/oauth/initiate', response, `request token issued (${scopes})`);

    checkCancelled();
    const consent = await sessionContext.newPage();
    wireStrictPage(consent, 'ai-scribe-oauth-consent', recorder);
    const pageResponse = await handshakeStep('OAuth consent page', () => consent.goto(
      app(`/ws/oauth/authorize?oauth_token=${pct(requestToken.oauth_token)}`), { waitUntil: 'load' }));
    assert(pageResponse && pageResponse.status() === 200, 'The OAuth consent page did not load successfully');
    await assertNotErrorPage(consent, 'AI-Scribe OAuth consent page');
    recordEndpoint('GET', '/ws/oauth/authorize', pageResponse, `consent page loaded (${scopes})`);
    const consentText = await consent.locator('body').innerText();
    for (const scope of scopes.split(' ')) {
      assert(consentText.includes(scope), `The OAuth consent page omitted requested scope ${scope}`);
    }
    assert(await consent.locator('#legacyRestrictedWarning, #fullAccessWarning').count() === 0,
      'The test server is not using OAuth scoped mode; use a separate disposable scoped installation');
    checkCancelled();
    const [approval] = await handshakeStep('OAuth consent approval', () => Promise.all([
      consent.waitForResponse((item) => item.request().method() === 'POST'
        && new URL(item.url()).pathname.endsWith('/ws/oauth/authorize'), { timeout: 30000 }),
      consent.locator('#scopeForm button[type="submit"]').click(),
    ]));
    await expectStatus(approval, 200, 'OAuth consent approval');
    const verifier = formFields(await approval.text()).oauth_verifier;
    assert(verifier, 'OAuth consent did not issue a verifier');
    recordEndpoint('POST', '/ws/oauth/authorize', approval, `consent approved (${scopes})`);
    await consent.close();

    checkCancelled();
    const tokenUrl = app('/ws/oauth/token');
    response = await handshakeStep('OAuth access-token exchange', () => anon.post(tokenUrl, {
      headers: {
        Authorization: oauthHeader({
          method: 'POST', url: tokenUrl, consumerKey, consumerSecret,
          token: requestToken.oauth_token, tokenSecret: requestToken.oauth_token_secret,
          extra: { oauth_verifier: verifier },
        }),
      },
    }));
    await expectStatus(response, 200, 'OAuth access-token exchange');
    const accessToken = formFields(await response.text());
    assert(accessToken.oauth_token && accessToken.oauth_token_secret,
      'OAuth access-token exchange did not return signed credentials');
    recordEndpoint('POST', '/ws/oauth/token', response, `access token issued (${scopes})`);
    return accessToken;
  };
}

/**
 * A bounded description of a failed request. Playwright's request errors carry a call log that can
 * include the request's headers, among them the signed OAuth Authorization header, and handshake URLs
 * carrying tokens or verifiers. Only a category and the caller's own label leave this function, so
 * none of that reaches the results table, stdout or RESULT_JSON.
 */
function requestFailure(label, error) {
  const text = String((error && error.message) || '');
  let category = 'request failed';
  if (/timeout/i.test(text)) category = 'timed out';
  else if (/ERR_CERT_|ERR_SSL_|SELF[_ ]SIGNED|certificate/i.test(text)) category = 'TLS certificate verification failed';
  else if (/ECONNREFUSED|ECONNRESET|EHOSTUNREACH|ENOTFOUND|EAI_AGAIN|socket hang up/i.test(text)) category = 'connection failed';
  else if (/has been closed|Target (page|context|browser).*closed/i.test(text)) category = 'browser closed';
  return new Error(`${label}: ${category}`);
}

/** Runs one network step of the OAuth handshake, replacing a raw failure with requestFailure's. */
async function handshakeStep(label, operation) {
  try {
    return await operation();
  } catch (error) {
    throw requestFailure(label, error);
  }
}

/** Returns signedRequest(method, path, token, body), which records a row for every request it sends. */
function createSignedRequest({
  state, app, anon, consumerKey, consumerSecret, endpointResults, endpointRecords,
}) {
  return async (method, requestPath, token, body) => {
    // Cooperative cancellation (runCheck's SIGINT/SIGTERM handling): stop before sending the next
    // request, never mid-request, so a submitted write settles before cleanup removes its rows.
    if (state.throwIfCancelled) state.throwIfCancelled();
    const url = app(requestPath);
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
      // Not the raw error: its call log may carry the signed Authorization header.
      const failure = requestFailure(`${method} ${result.path}`, error);
      result.outcome = 'FAIL';
      result.verification = failure.message;
      failure.endpointRecord = result;
      throw failure;
    }
  };
}

/**
 * Checks the opt-ins, registers this run's OAuth client, signs in, issues the full and limited
 * tokens, and creates the owned synthetic patient and its BP fixture. Returns the run context the
 * group checks share. Everything created is recorded on `state` for cleanup.
 */
async function setUpRun(state) {
  // The suite-wide opt-in for checks that write to the deployment (run-playwright-suite.js
  // demands it of every live run). Required here too because this check is normally run directly.
  if (process.env.CARLOS_DISPOSABLE_HOST !== 'true') {
    throw new SkipCheck('Requires CARLOS_DISPOSABLE_HOST=true: this check creates and deletes '
      + 'patient, clinical, eForm and OAuth rows on a separate disposable scoped installation');
  }
  assert(EXPECT_OAUTH_MODE === 'scoped',
    'AI-Scribe API acceptance requires EXPECT_OAUTH_MODE=scoped on a separate disposable installation');
  assert(!API_ONLY || API_ONLY === 'all' || API_TEST_GROUPS.has(API_ONLY),
    `Unsupported AI_SCRIBE_API_ONLY=${API_ONLY}; use all, demographics, measurements, `
      + 'measurements.read, measurements.write, labs, rxlookup, rx, schedule, eforms, '
      + 'or eforms.templates');
  const config = readConfig();
  const sql = createSqlRunner(config.mysql);
  state.sql = sql;
  const app = (appPath) => `${config.baseUrl}${appPath}`;
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
  const providerNo = sql.value(
    'SELECT provider_no FROM security WHERE user_name=? LIMIT 1',
    [config.testUser],
  );
  if (!providerNo) throw new SkipCheck(`${config.testUser} has no security row; a real provider login is required`);
  const admissionProgramId = sql.value('SELECT id FROM program ORDER BY id LIMIT 1');
  assert(/^[1-9]\d*$/.test(admissionProgramId),
    'The install has no program to admit the synthetic demographic to');

  state.marker = marker;
  state.formName = formName;
  state.providerNo = providerNo;
  state.consumerKey = consumerKey;
  insertId(
    sql,
    `INSERT INTO ServiceClient(name,clientKey,clientSecret,uri,lifetime)
      VALUES(?,?,?,'oob',3600)`,
    'AI-Scribe OAuth client',
    [`PW AI-Scribe ${runMarker}`, consumerKey, consumerSecret],
  );

  const recorder = createRecorder();
  const endpointResults = [];
  state.endpointResults = endpointResults;
  state.resultsPrinted = false;
  const { endpointRecords, recordEndpoint, recordFailure } = createEndpointRecorder(endpointResults);
  state.browser = await launchBrowser(config);
  const anonContext = await newContext(state.browser, config);
  const anon = anonContext.request;
  const sessionContext = await newContext(state.browser, config);
  await login(sessionContext, config, recorder, { label: 'ai-scribe-oauth-login' });

  const authorize = createAuthorizer({
    state, app, anon, sessionContext, recorder, consumerKey, consumerSecret, recordEndpoint,
  });
  const fullToken = await authorize(REQUIRED_SCOPES);
  const limitedToken = await authorize(LIMITED_SCOPES);
  const signedRequest = createSignedRequest({
    state, app, anon, consumerKey, consumerSecret, endpointResults, endpointRecords,
  });

  const ctx = {
    state, sql, config, recorder, endpointResults, recordEndpoint, recordFailure, signedRequest,
    fullToken, limitedToken, runMarker, marker, formName, providerNo, admissionProgramId,
    today, isoDate, nextIsoDate,
  };
  await verifyTokenIdentity(ctx);
  await createPatientFixture(ctx);
  return ctx;
}

async function verifyTokenIdentity({ signedRequest, fullToken, providerNo, recordEndpoint }) {
  const infoResponse = await signedRequest('GET', '/ws/services/oauth/info', fullToken);
  await expectStatus(infoResponse, 200, 'Signed OAuth token identity check');
  assert(String((await infoResponse.json()).login) === String(providerNo),
    'The OAuth token is not bound to the provider who approved it');
  recordEndpoint('GET', '/ws/services/oauth/info', infoResponse, 'signed token identity verified');
}

/**
 * Creates the owned synthetic patient over OAuth and inserts its BP fixture directly, recording on
 * the context the ids and names the group checks use.
 */
async function createPatientFixture(ctx) {
  const {
    state, sql, signedRequest, fullToken, recordEndpoint, runMarker, marker, providerNo, admissionProgramId,
  } = ctx;
  // The patient's first name as CARLOS currently stores it. The demographics group renames the
  // patient; later fixtures that identify the patient by name (the HL7 PID segment) read this
  // instead of assuming that rename ran, so a focused run (AI_SCRIBE_API_ONLY=labs) matches too.
  let patientFirstName = 'AI-Scribe';
  const createDemographicResponse = await signedRequest('POST', '/ws/services/demographics/', fullToken, {
    firstName: patientFirstName,
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
    VALUES ('BP',?,?, '121/79','',?,NOW(),NOW())`,
  [patientNo, providerNo, measurementComment]);

  const measurementId = sql.value(`SELECT id FROM measurements
    WHERE demographicNo=? AND type='BP' AND comments=?`,
  [patientNo, measurementComment]);
  ctx.patientFirstName = patientFirstName;
  ctx.patientNo = patientNo;
  ctx.measurementComment = measurementComment;
  ctx.measurementId = measurementId;
  ctx.demographicPath = `/ws/services/demographics/${patientNo}`;
}

async function checkDemographics(ctx) {
  const {
    sql, signedRequest, fullToken, limitedToken, recordEndpoint, marker, patientNo, demographicPath,
  } = ctx;
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
  // The list holds only the patients this provider may read (DemographicManager.getActiveDemographics).
  // So the synthetic patient's place in it is at most the number of active patients with a lower id,
  // and lower when some of those are restricted. Scan pages back from that bound until it appears.
  const offsetBound = Number(sql.value(`SELECT COUNT(*) FROM demographic
    WHERE demographic_no < ? AND patient_status = 'AC'`, [patientNo]));
  const pageSize = 50;
  let pageEnd = offsetBound;
  let patientPageResponse;
  let found = false;
  while (!found && pageEnd >= 0) {
    const pageStart = Math.max(0, pageEnd - pageSize + 1);
    patientPageResponse = await signedRequest(
      'GET', `/ws/services/demographics/?offset=${pageStart}&limit=${pageSize}`, fullToken,
    );
    await expectStatus(patientPageResponse, 200, 'OAuth demographic page scan for the synthetic patient');
    const patientPage = await patientPageResponse.json();
    const patientItems = patientPage.Item || patientPage.content || [];
    assert(Array.isArray(patientItems), 'Paginated demographic list did not return a result page');
    found = patientItems.some((item) => String(item.demographicNo) === patientNo);
    if (!found) {
      recordEndpoint('GET', '/ws/services/demographics/', patientPageResponse,
        `readable page from offset ${pageStart} scanned; synthetic patient not on it`);
    }
    pageEnd = pageStart - 1;
  }
  assert(found, 'Paginated demographic list did not include the synthetic patient at or below its calculated offset');
  recordEndpoint('GET', '/ws/services/demographics/', patientPageResponse,
    'synthetic patient returned on a readable page at or below its calculated offset');

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
  await expectStatus(demographicReload, 200, 'OAuth demographic update read-after-write');
  const reloadedDemographic = await demographicReload.json();
  assert(reloadedDemographic.firstName === 'AI-Scribe Updated',
    'OAuth demographic update was not persisted');
  ctx.patientFirstName = reloadedDemographic.firstName;
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

async function checkMeasurementRead({
  signedRequest, fullToken, limitedToken, recordEndpoint, patientNo, measurementComment,
}) {
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

  // The limited token (LIMITED_SCOPES) holds measurement.read: show it is accepted for a read
  // it is granted, not only refused for writes it lacks.
  const limitedMeasurementsResponse = await signedRequest(
    'POST', `/ws/services/measurements/${patientNo}`, limitedToken, { types: ['BP'] },
  );
  await expectStatus(limitedMeasurementsResponse, 200, 'OAuth measurement fetch with measurement.read only');
  const limitedMeasurements = await limitedMeasurementsResponse.json();
  const limitedBpRows = limitedMeasurements.measurements && limitedMeasurements.measurements.BP;
  assert(Array.isArray(limitedBpRows) && limitedBpRows.some((row) =>
    row.dataField === '121/79' && row.comments === measurementComment),
  'The measurement.read-only token did not read the owned BP fixture');
  recordEndpoint('POST', '/ws/services/measurements/{demographicNo}', limitedMeasurementsResponse,
    'limited token (demographic.read measurement.read) accepted for the measurement read');
}

async function checkMeasurementWrite({
  sql, signedRequest, fullToken, limitedToken, recordEndpoint, runMarker, patientNo,
  measurementComment, measurementId,
}) {
  const newMeasurementComment = `AI-Scribe OAuth new measurement ${runMarker}`;
  const newMeasurementPayload = {
    type: 'BP',
    dataField: '122/80',
    comments: newMeasurementComment,
    // Must match a configured measurementType row: the endpoint refuses a
    // type/instruction pair it cannot resolve to exactly one type. The ON and
    // BC seed data (V1.0.2__*_data.sql) configure BP as "sitting position".
    measuringInstruction: 'sitting position',
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

  // Correcting an existing measurement is not implemented (#6136): the service routes no PUT.
  // Sent with the full token, which holds measurement.write, so a 404/405 is the missing route
  // and not a scope refusal.
  const measurementCorrectionResponse = await signedRequest(
    'PUT', `/ws/services/measurements/${patientNo}`, fullToken,
    { id: Number(newMeasurementId), dataField: '123/81' },
  );
  assert([404, 405].includes(measurementCorrectionResponse.status()),
    `Measurement correction route should be unavailable, got HTTP ${measurementCorrectionResponse.status()}`);
  assert(sql.value('SELECT dataField FROM measurements WHERE id=?', [newMeasurementId])
    === newMeasurementPayload.dataField,
  'The unrouted measurement correction changed the stored reading');
  recordEndpoint('PUT', '/ws/services/measurements/{demographicNo}', measurementCorrectionResponse,
    'measurement correction is not implemented (see #6136); PUT is not routed', 'GAP');
}

async function checkLabs(ctx) {
  const {
    state, sql, signedRequest, fullToken, recordEndpoint, endpointResults, runMarker, marker, patientNo,
  } = ctx;
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

  const documentStore = process.env.LAB_UPLOAD_DOCUMENT_STORE;
  if (!documentStore) {
    endpointResults.push({
      outcome: 'UNVERIFIED',
      method: 'POST',
      path: '/ws/services/labs/hl7LabUpload',
      status: 'not run',
      verification: 'valid HL7 import requires LAB_UPLOAD_DOCUMENT_STORE so cleanup can remove its archived file',
    });
  } else {
    state.documentStore = fs.realpathSync(documentStore);
    assert(fs.statSync(state.documentStore).isDirectory(),
      'LAB_UPLOAD_DOCUMENT_STORE must point to the running server document directory');
    // Local until the preflight passes: cleanup deletes by state.labAccession, so recording an
    // accession that already existed would hand it lab rows this run does not own.
    const labAccession = `PWAI-${runMarker}`;
    assert(
      sql.value('SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=?', [labAccession]) === '0',
      'The synthetic lab accession already exists',
    );
    // Recorded before the upload, so cleanup still removes the rows if the write succeeds but its
    // response is lost.
    state.labRunMarker = runMarker;
    state.labAccession = labAccession;
    state.labFilePrefix = `LabUpload.ai-scribe-${runMarker}.xml.`;
    // Database time before the upload: cleanup removes only providerLabRoutingLock rows created
    // from here on (the lock key is a bare lab_no shared with other lab types' ids).
    state.labLockFloor = sql.value('SELECT NOW()');

    const hl7Timestamp = new Date().toISOString().replace(/\D/g, '').slice(0, 14);
    const hl7Segments = [
      `MSH|^~\\&|PATHL7|CW|CARLOS|TEST|${hl7Timestamp}||ORU^R01|${runMarker}|P|2.3|||ER|AL`,
      `PID|1||||${marker}^${ctx.patientFirstName}||19800102|F`,
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
    const savedLabFile = String(uploadedLab.fileName);
    // Cleanup removes files by this run's prefix; a name outside it, or a file not on the configured
    // mount, would leave the archived upload behind.
    assert(path.basename(savedLabFile) === savedLabFile && savedLabFile.startsWith(state.labFilePrefix)
      && /^LabUpload\.ai-scribe-[A-Fa-f0-9]+\.xml\.\d+$/.test(savedLabFile),
    'HL7 lab upload returned a saved file name outside this run\'s owned prefix');
    assert(fs.existsSync(path.join(state.documentStore, savedLabFile)),
      'The uploaded lab file is not in LAB_UPLOAD_DOCUMENT_STORE; point it at the server document directory');
    const labNo = sqlId(
      sql.value('SELECT lab_no FROM hl7TextInfo WHERE accessionNum=?', [state.labAccession]),
      'stored synthetic HL7 lab id',
    );
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

async function checkRxLookup({ signedRequest, fullToken, recordEndpoint, recordFailure }) {
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

  await checkDrugDetails({ signedRequest, fullToken, recordEndpoint, recordFailure }, drugCatalogId);

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

/**
 * Loads the catalog entry's details. Isolated: a failure (often a DrugRef without get_drug_2 or
 * get_generic_name) is recorded and the run continues.
 */
async function checkDrugDetails({ signedRequest, fullToken, recordEndpoint, recordFailure }, drugCatalogId) {
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
    recordFailure('GET', '/ws/services/rxlookup/details', drugDetailsResponse, error, verification);
  }
}

async function checkRxReads({ signedRequest, fullToken, recordEndpoint, recordFailure, patientNo }) {
  const rxReadRoutes = [
    ['/rx/drugs', 'all drugs'],
    [`/rx/drugs/all/${patientNo}`, 'all-drugs view'],
    [`/rx/drugs/current/${patientNo}`, 'current-drugs view'],
    [`/rx/drugs/longterm/${patientNo}`, 'long-term-drugs view'],
    [`/rx/drugs/archived/${patientNo}`, 'archived-drugs view'],
  ];
  for (const [route, description] of rxReadRoutes) {
    const requestPath = route === '/rx/drugs'
      ? `${route}?demographicNo=${patientNo}` : route;
    let response;
    try {
      response = await signedRequest('GET', `/ws/services${requestPath}`, fullToken);
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
      recordFailure('GET', `/ws/services${route}`, response, error, verification);
    }
  }
}

async function checkMedicationLifecycle({
  sql, signedRequest, fullToken, recordEndpoint, runMarker, providerNo, patientNo, isoDate, nextIsoDate,
}) {
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
  // RxManagerImpl.discontinue archives the drug row in place: archived=1, the reason and a date.
  const discontinuedRows = sql.rows(`SELECT archived, archived_reason, archived_date IS NOT NULL
    FROM drugs WHERE drugid=? AND demographic_no=?`, [latestDrugId, patientNo]);
  assert(discontinuedRows.length === 1 && discontinuedRows[0][0] === '1'
    && discontinuedRows[0][1] === 'other' && discontinuedRows[0][2] === '1',
  'Medication discontinue did not archive the drug row with reason "other" and an archived date');
  recordEndpoint('POST', '/ws/services/rx/discontinue', discontinueResponse,
    'drug row archived in the database with reason "other" and an archived date');

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
  const prescriptionList = await prescriptionsResponse.json();
  // The owned patient has exactly the one prescription the prescribe call above created.
  const scriptNos = sql.rows('SELECT script_no FROM prescription WHERE demographic_no=?', [patientNo]);
  assert(scriptNos.length === 1, 'The prescribe call did not store exactly one prescription row');
  const scriptNo = sqlId(scriptNos[0][0], 'stored synthetic prescription id');
  assert(Array.isArray(prescriptionList)
    && prescriptionList.some((item) => String(item.scriptId) === scriptNo),
  'Prescriptions list did not include the prescription created earlier in the run');
  recordEndpoint('GET', '/ws/services/rx/prescriptions', prescriptionsResponse,
    'patient prescription list includes the prescription created in this run');
}

async function checkSchedule({
  signedRequest, fullToken, recordEndpoint, providerNo, patientNo, today, isoDate,
}) {
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
  for (const [route, description] of scheduleRoutes) {
    const response = await signedRequest('GET', `/ws/services${route}`, fullToken);
    await expectStatus(response, 200, `OAuth ${description}`);
    await response.json();
    recordEndpoint('GET', `/ws/services${route}`, response, `${description} returned`);
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

async function checkEformTemplates({
  signedRequest, fullToken, limitedToken, recordEndpoint, marker, formName,
}) {
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

async function checkDemographicDelete({
  sql, signedRequest, fullToken, recordEndpoint, marker, patientNo, demographicPath,
}) {
  const deleteDemographicResponse = await signedRequest(
    'DELETE', demographicPath, fullToken,
  );
  await expectStatus(deleteDemographicResponse, 200, 'OAuth synthetic demographic delete');
  const deletedDemographic = await deleteDemographicResponse.json();
  assert(String(deletedDemographic.demographicNo) === patientNo
    && deletedDemographic.lastName === marker,
  'Demographic delete did not identify the owned synthetic patient');
  // The REST delete is a soft delete (DemographicManagerImpl.deleteDemographic): the row stays
  // with patient_status 'DE'. Cleanup removes the row afterwards.
  assert(sql.value('SELECT patient_status FROM demographic WHERE demographic_no=? AND last_name=?',
    [patientNo, marker]) === 'DE',
  'Demographic delete did not mark the owned synthetic patient as deleted (patient_status DE)');
  recordEndpoint('DELETE', '/ws/services/demographics/{dataId}', deleteDemographicResponse,
    'patient soft-deleted: row kept with patient_status DE (cleanup removes it)');
}

/** Fails the run if the browser pages or any endpoint check failed; otherwise returns the summary. */
function finishRun({ recorder, endpointResults }, summary) {
  assertStrictPage(recorder);
  const failedEndpoints = endpointResults.filter((row) => row.outcome === 'FAIL');
  assert(failedEndpoints.length === 0,
    `${failedEndpoints.length} endpoint validation(s) failed; see the endpoint results table`);
  return summary;
}

async function main(state = {}) {
  const ctx = await setUpRun(state);
  if (shouldRun('demographics')) await checkDemographics(ctx);
  if (shouldRun('measurements') || shouldRun('measurements.read')) await checkMeasurementRead(ctx);
  if (shouldRun('measurements') || shouldRun('measurements.write')) await checkMeasurementWrite(ctx);
  if (API_ONLY.startsWith('measurements')) {
    return finishRun(ctx, {
      oauthMode: 'scoped',
      selected: API_ONLY,
      checked: ['OAuth bootstrap', 'temporary demographic fixture setup', `${API_ONLY} API workflow`],
    });
  }
  if (shouldRun('labs')) await checkLabs(ctx);
  if (shouldRun('rxlookup')) await checkRxLookup(ctx);
  if (shouldRun('rx')) {
    await checkRxReads(ctx);
    await checkMedicationLifecycle(ctx);
  }
  if (shouldRun('schedule')) await checkSchedule(ctx);
  if (shouldRun('eforms') || shouldRun('eforms.templates')) await checkEformTemplates(ctx);
  // Last, so every other group runs against the patient before the API deletes it.
  if (shouldRun('demographics')) await checkDemographicDelete(ctx);
  return finishRun(ctx, {
    oauthMode: 'scoped',
    selected: API_ONLY || 'all',
    checked: ['signed OAuth request-token and access-token flow',
      `${API_ONLY && API_ONLY !== 'all' ? API_ONLY : 'all'} API workflow(s)`],
  });
}


if (require.main === module) {
  const state = {};
  runCheck({
    name: 'ai-scribe-oauth-api',
    run: async ({ throwIfCancelled }) => {
      // SIGINT/SIGTERM: runCheck keeps the process alive and runs cleanup in its finally; main()
      // checks this before each request so an interrupted run stops early instead of finishing.
      state.throwIfCancelled = throwIfCancelled;
      try {
        return await main(state);
      } catch (error) {
        state.failureMessage = error.message || 'check failed';
        // A skip (missing opt-in or provider) is reported by runCheck as a skip, not as a FAIL row.
        if (!(error instanceof SkipCheck) && !(state.endpointResults || []).some((row) =>
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
