#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
/*
 * Creates patients through the session REST endpoint (POST /ws/rs/demographics) with extras, as an
 * integrating client does for the cell phone (key demo_cell), and reads the result back from the
 * database and from the summary endpoint (issue #5119).
 *
 * Step 1: an extra sent with a client-chosen demographicNo is linked to the new patient anyway: the
 * extension row carries the new demographic_no, the client's number gets no row, and the summary
 * endpoint shows the value as the patient's alternative phone. Before the fix the row was stored
 * under the number the client sent (or null), so the patient never showed it.
 * Step 2: two extras with the same key violate uk_demo_ext_single_value after the patient and its
 * admission are written. The request fails and no patient, admission or extension row survives:
 * creation is one transaction.
 *
 * Every row belongs to a FAKE- patient created by the check and removed by its cleanup. Environment:
 * the common contract (BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN, MYSQL_*).
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

const CELL = '555-0142';
const CLIENT_CHOSEN_NO = 2147480000;

async function workflow(s) {
  const quote = h.sqlString;
  const endpoint = `${s.config.baseUrl}/ws/rs/demographics`;
  const programId = s.sql.value('SELECT id FROM program ORDER BY id LIMIT 1');
  h.assert(/^[1-9]\d*$/.test(programId), 'The install has no program to admit the new patient to');

  const created = [];
  s.cleanup(() => {
    // The fixtures are found by their unique last name and by the number the client chose, so a
    // failed run cannot leave an orphan behind and no other patient's rows can be touched.
    const owned = `(SELECT demographic_no FROM demographic WHERE last_name LIKE ${quote(`${s.marker}%`)})`;
    s.sql.execute(`DELETE FROM demographicExt WHERE demographic_no IN ${owned};
      DELETE FROM demographicExt WHERE demographic_no=${CLIENT_CHOSEN_NO} AND value=${quote(CELL)};
      DELETE FROM admission WHERE client_id IN ${owned};
      DELETE FROM demographic WHERE last_name LIKE ${quote(`${s.marker}%`)}`);
    h.assert(s.sql.value(`SELECT COUNT(*) FROM demographic WHERE last_name LIKE ${quote(`${s.marker}%`)}`) === '0',
      'Owned REST patients were not removed');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM demographicExt WHERE demographic_no=${CLIENT_CHOSEN_NO}`) === '0',
      'Orphaned extension rows remain');
  });

  const body = (lastName, extras) => ({
    firstName: 'Rest', lastName, sex: 'F', dobYear: '1980', dobMonth: '01', dobDay: '02',
    hcType: 'ON', providerNo: s.provider, admissionProgramId: Number(programId),
    address: { address: '1 Test Street', city: 'Toronto', province: 'ON', postal: 'M5V 2T6' },
    extras,
  });
  const extra = (key, value, demographicNo) => ({ key, value, demographicNo, providerNo: s.provider });
  const count = sql => Number(s.sql.value(sql));

  await s.step('an extra is linked to the new patient even when the client sent another number', async () => {
    const lastName = `${s.marker}-A`;
    const response = await s.context.request.post(endpoint, {
      data: body(lastName, [extra('demo_cell', CELL, CLIENT_CHOSEN_NO)]),
    });
    h.assert(response.status() === 200, `create expected HTTP 200, received ${response.status()}`);
    const saved = await response.json();
    await response.dispose();
    const patient = Number(saved.demographicNo);
    h.assert(Number.isSafeInteger(patient) && patient > 0 && patient !== CLIENT_CHOSEN_NO,
      'The create response carries no new demographic number');
    created.push(patient);
    h.assert(count(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient}
      AND last_name=${quote(lastName)}`) === 1, 'The patient row was not created');
    const linked = s.sql.rows(`SELECT key_val,value FROM demographicExt WHERE demographic_no=${patient}`);
    h.assert(linked.length === 1 && linked[0][0] === 'demo_cell' && linked[0][1] === CELL,
      `The new patient's extension rows are ${JSON.stringify(linked)}, expected one demo_cell row`);
    h.assert(count(`SELECT COUNT(*) FROM demographicExt WHERE demographic_no=${CLIENT_CHOSEN_NO}`) === 0,
      'An extension row was stored under the demographic number the client chose');
    const summary = await s.context.request.get(`${s.config.baseUrl}/ws/rs/demographics/summary/${patient}`);
    h.assert(summary.status() === 200, `summary expected HTTP 200, received ${summary.status()}`);
    h.assert((await summary.json()).alternativePhone === CELL,
      'The summary endpoint does not show the saved cell phone as the alternative phone');
    await summary.dispose();
  });

  await s.step('an extra sent without a number is linked to the new patient', async () => {
    const lastName = `${s.marker}-B`;
    const response = await s.context.request.post(endpoint, {
      data: body(lastName, [extra('demo_cell', CELL, null)]),
    });
    h.assert(response.status() === 200, `create expected HTTP 200, received ${response.status()}`);
    const patient = Number((await response.json()).demographicNo);
    await response.dispose();
    h.assert(Number.isSafeInteger(patient) && patient > 0, 'The create response carries no new demographic number');
    created.push(patient);
    h.assert(count(`SELECT COUNT(*) FROM demographicExt WHERE demographic_no=${patient}
      AND key_val='demo_cell' AND value=${quote(CELL)}`) === 1, 'The extra was not linked to the new patient');
    h.assert(count(`SELECT COUNT(*) FROM demographicExt WHERE demographic_no IS NULL AND value=${quote(CELL)}`) === 0,
      'An orphaned extension row (no patient) was stored');
  });

  await s.step('colliding extras fail the request and keep no patient, admission or extension', async () => {
    const lastName = `${s.marker}-C`;
    const response = await s.context.request.post(endpoint, {
      data: body(lastName, [extra('demo_cell', '555-0100', null), extra('demo_cell', '555-0199', null)]),
    });
    h.assert(response.status() >= 400, `a duplicate-key create expected an error status, received ${response.status()}`);
    await response.dispose();
    const kept = s.sql.rows(`SELECT demographic_no FROM demographic WHERE last_name=${quote(lastName)}`);
    h.assert(kept.length === 0, `The failed create left the patient behind: ${JSON.stringify(kept)}`);
    h.assert(count(`SELECT COUNT(*) FROM admission a JOIN demographic d ON d.demographic_no=a.client_id
      WHERE d.last_name LIKE ${quote(`${s.marker}-C%`)}`) === 0, 'The failed create left an admission behind');
    h.assert(count(`SELECT COUNT(*) FROM demographicExt WHERE value IN ('555-0100','555-0199')
      AND demographic_no IN (SELECT demographic_no FROM demographic WHERE last_name LIKE ${quote(`${s.marker}%`)})`) === 0,
    'The failed create left an extension behind');
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('demographic-rest-create-extras', workflow, { openPatient: false });
