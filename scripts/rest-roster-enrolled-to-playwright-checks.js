#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * The demographic REST API carries rosterEnrolledTo (#3902; adapted from MagentaHealth/Open-O).
 *
 * demographic.roster_enrolled_to (the provider a patient is rostered to) was stored and shown on
 * the master record but missing from DemographicTo1, so an integration reading the patient over
 * /ws/rs could not see the enrolment, and an update through the API silently dropped it.
 *
 * Driven through the packaged front door with the browser's own session (/ws/rs is
 * session-authenticated), using XMLHttpRequest so CSRFGuard's injected client adds its token
 * the way it does for the application's own calls:
 *
 *   1. GET /ws/rs/demographics/{id} returns the stored rosterEnrolledTo;
 *   2. PUT /ws/rs/demographics with rosterEnrolledTo changed persists the new value.
 *
 * The check owns a synthetic patient (runWorkflow); nothing else is written.
 *
 * Environment (see docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, CHROME_PATH, TEST_USER, TEST_PASSWORD, TEST_PIN, MYSQL_*
 */

const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

// The REST root is under the deployment's context path, taken from BASE_URL (as the rest of the
// suite does) rather than guessed from the current page's URL, so a root-context install works too.
const contextPathOf = (baseUrl) => new URL(baseUrl).pathname.replace(/\/+$/, '');

async function xhr(page, context, method, path, body) {
  return page.evaluate(({ context, method: verb, path: target, body: payload }) => new Promise((resolve) => {
    const request = new XMLHttpRequest();
    request.open(verb, `${context}${target}`);
    request.setRequestHeader('Accept', 'application/json');
    if (payload !== undefined) request.setRequestHeader('Content-Type', 'application/json');
    request.onload = () => resolve({ status: request.status, text: request.responseText });
    request.onerror = () => resolve({ status: 0, text: '' });
    request.send(payload === undefined ? null : JSON.stringify(payload));
  }), { context, method, path, body });
}

function parse(result, label) {
  h.assert(result.status === 200, `${label} answered HTTP ${result.status}`);
  try {
    return JSON.parse(result.text);
  } catch (error) {
    throw new Error(`${label} did not answer JSON`);
  }
}

async function workflow(session) {
  const { sql, patient, provider, schedule } = session;
  const context = contextPathOf(session.config.baseUrl);
  const other = sql.value(`SELECT provider_no FROM provider WHERE status='1' AND provider_no <> ${h.sqlString(provider)}
    AND provider_no REGEXP '^[0-9]+$' ORDER BY provider_no LIMIT 1`);
  if (!other) throw new h.SkipCheck('needs a second active provider to re-enrol the patient to');
  sql.execute(`UPDATE demographic SET roster_status='RO', roster_enrolled_to=${h.sqlString(provider)}
    WHERE demographic_no=${patient}`);

  let record;
  await session.step('GET returns the stored rosterEnrolledTo', async () => {
    record = parse(await xhr(schedule, context, 'GET', `/ws/rs/demographics/${patient}`), 'GET /ws/rs/demographics/{id}');
    h.assert(String(record.demographicNo) === String(patient), 'the API returned a different patient');
    h.assert(record.rosterEnrolledTo === provider,
      `rosterEnrolledTo is ${JSON.stringify(record.rosterEnrolledTo)}, expected the enrolled provider`);
  });

  await session.step('PUT with a changed rosterEnrolledTo persists it', async () => {
    const updated = parse(await xhr(schedule, context, 'PUT', '/ws/rs/demographics', { ...record, rosterEnrolledTo: other }),
      'PUT /ws/rs/demographics');
    h.assert(updated.rosterEnrolledTo === other, 'the PUT response does not carry the new rosterEnrolledTo');
    await expectValue(sql, `SELECT roster_enrolled_to FROM demographic WHERE demographic_no=${patient}`, other,
      'the PUT did not persist rosterEnrolledTo');
    const reread = parse(await xhr(schedule, context, 'GET', `/ws/rs/demographics/${patient}`), 'GET after PUT');
    h.assert(reread.rosterEnrolledTo === other, 'a fresh GET does not return the updated rosterEnrolledTo');
  });
}

if (require.main === module) runWorkflow('rest-roster-enrolled-to', workflow);
module.exports = { workflow };
