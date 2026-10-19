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
 * Does the deployed OAuth REST surface write each timestamp as one JSON type, whatever its value?
 *
 * WHY A DEPLOYED CHECK. Issue #3877: /ws/services (the surface Ocean and other integrations
 * call) writes a java.util.Date through SmartDateSerializer, which picks the JSON shape from the
 * value: local midnight becomes "yyyy-MM-dd", a value on 1970-01-01 "HH:mm:ss", anything else
 * epoch milliseconds. A timestamp column that happened to hold one of those values therefore
 * arrived as a string, and Ocean's sync broke on it. The fix annotates the timestamp fields of the
 * REST transfer objects; the unit tests drive them through the mapper loaded from the Spring XML.
 * Only a running deployment shows that the packaged WAR's /ws/services mapper is that mapper and
 * that rows read from MariaDB keep the shape end to end.
 *
 * WHAT IT DRIVES:
 *   1. Fixture. One demographic with an active allergy. Its lastUpdateDate and one active
 *      allergy's lastUpdateDate are set to local midnight, and a measurement of a type no other
 *      row uses is inserted with dateObserved at today's midnight and dateEntered on 1970-01-01.
 *      Outside legacy-restricted mode, the demographic's latest appointment also gets a midnight
 *      createdatetime and a 1970-01-01 updatedatetime.
 *   2. The OAuth 1.0a out-of-band handshake, approved in the browser as the logged-in provider.
 *   3. GET /ws/services/demographics/{id}?includes[]=allergies&includes[]=measurements, the call
 *      the legacy integration makes and the one every OAuth mode allows. Each seeded timestamp must
 *      be an epoch-millisecond number equal to what the session /ws/rs surface (which always wrote
 *      epoch milliseconds) returns for the same call. A calendar-date column (patientStatusDate)
 *      must still be a "yyyy-MM-dd" string on /ws/services, which shows SmartDateSerializer is the
 *      serializer under test.
 *   4. Outside legacy-restricted mode, the same for GET /ws/services/allergies/active and
 *      POST /ws/services/schedule/{id}/appointmentHistory, whose appointmentDate and startTime
 *      (DATE and TIME columns) keep their string shapes.
 *
 * FIXTURE. Every changed column's original value is read first and written back by runCheck's
 * cleanup hook, which also deletes the inserted measurement and the check's ServiceClient with
 * every token and nonce issued to it, whether the check passes or fails. It needs MYSQL_*
 * (assertsDatabase). Run it against a disposable database: it updates demo rows in place.
 *
 * PHI HANDLING. The demographic is chosen by number. Failure messages name fields and JSON types,
 * never response bodies.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:oauth-rest-epoch-dates-playwright
 *
 * Optional environment (the common contract is in lib/playwright-harness.js):
 *   OAUTH_DEMOGRAPHIC_NO=1   demographic to use. Default: the lowest-numbered one with a patient
 *                            status date and an active allergy.
 *   EXPECT_OAUTH_MODE=legacy-restricted
 *                            the server's OAuth access mode: legacy-restricted (the shipped
 *                            default; only the demographic call is reachable), scoped
 *                            (oauth.scope.enforcement.enabled=true) or legacy-full
 *                            (oauth.scope.legacy.access=full).
 */
const crypto = require('crypto');
const {
  SkipCheck, assert, assertNotErrorPage, assertStrictPage, createRecorder, createSqlRunner, insertId,
  launchBrowser, login, newContext, readConfig, runCheck, sqlString, wireStrictPage,
} = require('./lib/playwright-harness');
const { oauthHeader, pct } = require('./oauth-rest-surfaces-playwright-checks');

const OAUTH_MODES = ['scoped', 'legacy-restricted', 'legacy-full'];
const OAUTH_MODE = process.env.EXPECT_OAUTH_MODE || 'legacy-restricted';
assert(OAUTH_MODES.includes(OAUTH_MODE), `EXPECT_OAUTH_MODE must be one of ${OAUTH_MODES.join(', ')}`);
const SCOPED = OAUTH_MODE === 'scoped';
const RESTRICTED = OAUTH_MODE === 'legacy-restricted';
// What the calls below need under enforcement (OAuthScopes: demographics, allergies, schedule).
const SCOPES = 'demographic.read allergy.read schedule.read';

// The two values SmartDateSerializer turned into strings before the fix, in the server's zone.
const MIDNIGHT = '2026-03-11 00:00:00'; // was "2026-03-11"
const EPOCH_DAY = '1970-01-01 09:30:00'; // was "09:30:00"
// A type no demo row uses, so the inserted row is always the latest of its type: the demographic
// call's measurements include returns only the most recent row of each type.
const MEASUREMENT_TYPE = 'PWEPOCH';

const YYYY_MM_DD = /^\d{4}-\d{2}-\d{2}$/;
const HH_MM_SS = /^\d{2}:\d{2}:\d{2}$/;

function formFields(body) {
  return Object.fromEntries(new URLSearchParams(body.trim()));
}

async function expectStatus(response, expected, label) {
  assert(response.status() === expected, `${label}: expected HTTP ${expected}, got ${response.status()}`);
}

async function json(response, label) {
  await expectStatus(response, 200, label);
  try {
    return await response.json();
  } catch (error) {
    throw new Error(`${label}: the response is not JSON`);
  }
}

function describe(value) {
  return value === undefined ? 'absent' : `${typeof value}${typeof value === 'string' ? ` of length ${value.length}` : ''}`;
}

/**
 * The fix's contract for one timestamp: an epoch-millisecond number on /ws/services, equal to
 * the session surface's value for the same row. Pre-fix the OAuth value was a string here.
 */
function assertEpochOnBoth(oauthValue, sessionValue, label) {
  assert(typeof sessionValue === 'number',
    `${label}: /ws/rs wrote ${describe(sessionValue)}, not epoch milliseconds; the session surface changed`);
  assert(typeof oauthValue === 'number' && Number.isInteger(oauthValue),
    `${label}: /ws/services wrote ${describe(oauthValue)}, not epoch milliseconds (issue #3877)`);
  assert(oauthValue === sessionValue,
    `${label}: /ws/services and /ws/rs wrote different instants for the same row`);
}

function byId(list, id, label) {
  assert(Array.isArray(list), `${label}: the response has no list`);
  const item = list.find((entry) => String(entry && entry.id) === String(id));
  assert(item, `${label}: the seeded row is not in the response`);
  return item;
}

/** Restores every changed column and removes everything the check created. */
async function cleanup(state) {
  try {
    if (state.browser) await state.browser.close();
  } finally {
    const { sql } = state;
    if (sql) {
      try {
        for (const restore of (state.restores || []).reverse()) {
          sql.execute(restore);
        }
        if (state.measurementId) {
          sql.execute(`DELETE FROM measurements WHERE id=${state.measurementId}`);
        }
        if (state.consumerKey) {
          const key = sqlString(state.consumerKey);
          const owned = `(SELECT id FROM ServiceClient WHERE clientKey=${key})`;
          sql.execute(`DELETE FROM ServiceAccessToken WHERE clientId IN ${owned};
            DELETE FROM ServiceRequestToken WHERE clientId IN ${owned};
            DELETE FROM ServiceOAuthNonce WHERE consumerKey=${key};
            DELETE FROM ServiceClient WHERE clientKey=${key}`);
          assert(sql.value(`SELECT (SELECT COUNT(*) FROM ServiceClient WHERE clientKey=${key})
              + (SELECT COUNT(*) FROM ServiceOAuthNonce WHERE consumerKey=${key})`) === '0',
          'the OAuth client fixture or its nonces were not removed');
        }
        assert(sql.value(`SELECT COUNT(*) FROM measurements WHERE type=${sqlString(MEASUREMENT_TYPE)}`) === '0',
          `the ${MEASUREMENT_TYPE} measurement fixture was not removed`);
      } finally {
        sql.dispose();
      }
    }
  }
}

/** Reads a DATETIME column and queues the statement that writes it back. */
function rememberDatetime(sql, state, table, column, keyColumn, key) {
  const [isNull, value] = sql.rows(`SELECT ${column} IS NULL, ${column} FROM ${table} WHERE ${keyColumn}=${key}`)[0];
  const original = isNull === '1' ? 'NULL' : sqlString(value);
  state.restores.push(`UPDATE ${table} SET ${column}=${original} WHERE ${keyColumn}=${key}`);
}

async function main(state = {}) {
  const config = readConfig();
  const sql = createSqlRunner(config.mysql);
  state.sql = sql;
  state.restores = [];
  const app = (p) => `${config.baseUrl}${p}`;

  const providerNo = sql.value(
    `SELECT provider_no FROM security WHERE user_name=${sqlString(config.testUser)} LIMIT 1`);
  assert(providerNo, `${config.testUser} has no security row; the check needs the login account's provider`);
  const demographicNo = process.env.OAUTH_DEMOGRAPHIC_NO || sql.value(
    `SELECT d.demographic_no FROM demographic d WHERE d.patient_status_date > '1900-01-01'
      AND d.patient_status_date <> '1970-01-01'
      AND EXISTS (SELECT 1 FROM allergies a WHERE a.demographic_no=d.demographic_no AND a.archived=0)
      ORDER BY d.demographic_no LIMIT 1`);
  if (!/^\d+$/.test(String(demographicNo))) {
    throw new SkipCheck('no demographic with a patient status date and an active allergy; load the demo dataset or set OAUTH_DEMOGRAPHIC_NO');
  }
  const allergyId = sql.value(`SELECT allergyid FROM allergies WHERE demographic_no=${demographicNo} AND archived=0
      ORDER BY allergyid LIMIT 1`);
  if (!/^\d+$/.test(String(allergyId))) throw new SkipCheck(`demographic ${demographicNo} has no active allergy`);
  assert(sql.value(`SELECT COUNT(*) FROM measurements WHERE type=${sqlString(MEASUREMENT_TYPE)}`) === '0',
    `a ${MEASUREMENT_TYPE} measurement already exists; a previous run left its fixture behind`);

  // 1. Fixture: originals first, so cleanup can restore whatever happens next.
  rememberDatetime(sql, state, 'demographic', 'lastUpdateDate', 'demographic_no', demographicNo);
  rememberDatetime(sql, state, 'allergies', 'lastUpdateDate', 'allergyid', allergyId);
  sql.execute(`UPDATE demographic SET lastUpdateDate=${sqlString(MIDNIGHT)} WHERE demographic_no=${demographicNo}`);
  sql.execute(`UPDATE allergies SET lastUpdateDate=${sqlString(MIDNIGHT)} WHERE allergyid=${allergyId}`);
  state.measurementId = insertId(sql, `INSERT INTO measurements(type, demographicNo, providerNo, dataField,
      measuringInstruction, comments, dateObserved, dateEntered)
      VALUES(${sqlString(MEASUREMENT_TYPE)}, ${demographicNo}, ${sqlString(providerNo)}, '1', '', '',
      CURDATE(), ${sqlString(EPOCH_DAY)})`, 'measurement');
  let appointmentNo = null;
  if (!RESTRICTED) {
    appointmentNo = sql.value(`SELECT appointment_no FROM appointment WHERE demographic_no=${demographicNo}
        ORDER BY appointment_no DESC LIMIT 1`) || null;
    if (appointmentNo) {
      rememberDatetime(sql, state, 'appointment', 'createdatetime', 'appointment_no', appointmentNo);
      rememberDatetime(sql, state, 'appointment', 'updatedatetime', 'appointment_no', appointmentNo);
      sql.execute(`UPDATE appointment SET createdatetime=${sqlString(MIDNIGHT)}, updatedatetime=${sqlString(EPOCH_DAY)}
          WHERE appointment_no=${appointmentNo}`);
    }
  }

  // 2. Handshake. uri='oob': the verifier is shown on the consent page instead of redirected.
  const clientName = `PW OAuth epoch dates ${crypto.randomUUID()}`;
  const consumerKey = crypto.randomBytes(12).toString('hex');
  const consumerSecret = crypto.randomBytes(12).toString('hex');
  state.consumerKey = consumerKey;
  insertId(sql, `INSERT INTO ServiceClient(name,clientKey,clientSecret,uri,lifetime)
      VALUES(${sqlString(clientName)},${sqlString(consumerKey)},${sqlString(consumerSecret)},'oob',3600)`,
  'ServiceClient');

  const recorder = createRecorder();
  state.browser = await launchBrowser(config);
  const anon = (await newContext(state.browser, config)).request;
  const context = await newContext(state.browser, config);
  await login(context, config, recorder);

  const initiateUrl = app(`/ws/oauth/initiate${SCOPED ? `?scope=${pct(SCOPES)}` : ''}`);
  let r = await anon.post(initiateUrl, {
    headers: {
      Authorization: oauthHeader({
        method: 'POST', url: initiateUrl, consumerKey, consumerSecret, extra: { oauth_callback: 'oob' },
      }),
    },
  });
  await expectStatus(r, 200, 'signed POST /ws/oauth/initiate');
  const requestToken = formFields(await r.text());
  assert(requestToken.oauth_token && requestToken.oauth_token_secret, '/ws/oauth/initiate returned no request token');
  const consent = await context.newPage();
  wireStrictPage(consent, 'oauth-consent', recorder);
  const consentResponse = await consent.goto(
    app(`/ws/oauth/authorize?oauth_token=${pct(requestToken.oauth_token)}`), { waitUntil: 'load' });
  assert(consentResponse && consentResponse.status() === 200, 'the consent page did not load');
  await assertNotErrorPage(consent, 'OAuth consent page');
  const [approval] = await Promise.all([
    consent.waitForResponse((resp) => resp.request().method() === 'POST'
      && new URL(resp.url()).pathname.endsWith('/ws/oauth/authorize'), { timeout: 30000 }),
    consent.locator('#scopeForm button[type="submit"]').click(),
  ]);
  await expectStatus(approval, 200, 'approving the request token');
  const verifier = formFields(await approval.text()).oauth_verifier;
  assert(verifier, 'approving the request token did not show an oauth_verifier');
  await consent.close();
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
  assert(accessToken.oauth_token && accessToken.oauth_token_secret, '/ws/oauth/token returned no access token');

  const accept = { Accept: 'application/json' };
  // The same call on both surfaces: signed on /ws/services, with the browser session on /ws/rs.
  const both = async (method, path, label) => {
    const url = app(`/ws/services${path}`);
    const signed = {
      ...accept,
      'Content-Type': 'application/json',
      Authorization: oauthHeader({
        method, url, consumerKey, consumerSecret,
        token: accessToken.oauth_token, tokenSecret: accessToken.oauth_token_secret,
      }),
    };
    const data = method === 'POST' ? '{}' : undefined;
    const oauth = await json(await anon.fetch(url, { method, headers: signed, data }), `${method} /ws/services${label}`);
    const session = await json(await context.request.fetch(app(`/ws/rs${path}`), {
      method, headers: { ...accept, 'Content-Type': 'application/json' }, data,
    }), `${method} /ws/rs${label}`);
    return { oauth, session };
  };

  // 3. The demographic record with its allergies and measurements: reachable in every mode.
  const includes = `includes[]=allergies&includes[]=measurements`;
  const demo = await both('GET', `/demographics/${demographicNo}?${includes}`, '/demographics/{id}');
  assertEpochOnBoth(demo.oauth.lastUpdateDate, demo.session.lastUpdateDate, 'DemographicTo1.lastUpdateDate');
  const oauthAllergy = byId(demo.oauth.allergies, allergyId, 'demographic allergies on /ws/services');
  const sessionAllergy = byId(demo.session.allergies, allergyId, 'demographic allergies on /ws/rs');
  assertEpochOnBoth(oauthAllergy.lastUpdateDate, sessionAllergy.lastUpdateDate, 'AllergyTo1.lastUpdateDate');
  const oauthMeasurement = byId(demo.oauth.measurements, state.measurementId, 'demographic measurements on /ws/services');
  const sessionMeasurement = byId(demo.session.measurements, state.measurementId, 'demographic measurements on /ws/rs');
  assertEpochOnBoth(oauthMeasurement.dateObserved, sessionMeasurement.dateObserved, 'MeasurementTo1.dateObserved');
  assertEpochOnBoth(oauthMeasurement.createDate, sessionMeasurement.createDate, 'MeasurementTo1.createDate');
  // A DATE column beside them: still a calendar-date string on /ws/services. If this were a
  // number too, the OAuth mapper would not be running SmartDateSerializer, and the assertions
  // above would prove nothing about the fix.
  assert(typeof demo.oauth.patientStatusDate === 'string' && YYYY_MM_DD.test(demo.oauth.patientStatusDate),
    `DemographicTo1.patientStatusDate on /ws/services is ${describe(demo.oauth.patientStatusDate)}, not a `
    + 'yyyy-MM-dd string: the /ws/services mapper is not the SmartDateModule mapper this check tests');
  const checked = ['DemographicTo1.lastUpdateDate', 'AllergyTo1.lastUpdateDate', 'MeasurementTo1.dateObserved',
    'MeasurementTo1.createDate'];

  // 4. Further integration reads, where the mode lets this token make them.
  if (!RESTRICTED) {
    const active = await both('GET', `/allergies/active?demographicNo=${demographicNo}`, '/allergies/active');
    assertEpochOnBoth(byId(active.oauth.allergies, allergyId, '/ws/services/allergies/active').lastUpdateDate,
      byId(active.session.allergies, allergyId, '/ws/rs/allergies/active').lastUpdateDate,
      'AllergyResponse AllergyTo1.lastUpdateDate');
    checked.push('AllergyResponse AllergyTo1.lastUpdateDate');
    if (appointmentNo) {
      const history = await both('POST', `/schedule/${demographicNo}/appointmentHistory`, '/schedule/{id}/appointmentHistory');
      const oauthAppt = byId(history.oauth.appointments, appointmentNo, '/ws/services appointmentHistory');
      const sessionAppt = byId(history.session.appointments, appointmentNo, '/ws/rs appointmentHistory');
      assertEpochOnBoth(oauthAppt.createDateTime, sessionAppt.createDateTime, 'AppointmentTo1.createDateTime');
      assertEpochOnBoth(oauthAppt.updateDateTime, sessionAppt.updateDateTime, 'AppointmentTo1.updateDateTime');
      // The slot's DATE and TIME columns keep the shapes updateAppointment round-trips (#2957).
      assert(YYYY_MM_DD.test(String(oauthAppt.appointmentDate)) && HH_MM_SS.test(String(oauthAppt.startTime)),
        'AppointmentTo1.appointmentDate/startTime on /ws/services lost their yyyy-MM-dd / HH:mm:ss shapes');
      checked.push('AppointmentTo1.createDateTime', 'AppointmentTo1.updateDateTime');
    }
  }

  assertStrictPage(recorder);
  return { mode: OAUTH_MODE, checked };
}

if (require.main === module) {
  const state = {};
  runCheck({ name: 'oauth-rest-epoch-dates', run: () => main(state), cleanup: () => cleanup(state) });
}
module.exports = { main, cleanup, assertEpochOnBoth };
