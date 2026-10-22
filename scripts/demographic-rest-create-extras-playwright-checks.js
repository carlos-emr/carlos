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
 * Every extension value is this run's FAKE-PW<hex> marker plus a suffix, so the check and its cleanup
 * find the rows it created by value alone: a row stored with a null or a client-chosen demographic_no
 * (the defect) is still found, detected and removed, and no other patient's row can match. Environment:
 * the common contract (BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN, MYSQL_*).
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

const CLIENT_CHOSEN_NO = 2147480000;

async function workflow(s) {
  const endpoint = `${s.config.baseUrl}/ws/rs/demographics`;
  const programId = s.sql.value('SELECT id FROM program ORDER BY id LIMIT 1', []);
  h.assert(/^[1-9]\d*$/.test(programId), 'The install has no program to admit the new patient to');

  const cellA = `${s.marker}-cellA`;
  const cellB = `${s.marker}-cellB`;
  const likeMarker = `${s.marker}%`;
  s.cleanup(() => {
    // Extensions are matched by this run's marker value, wherever they were stored (the new patient,
    // the client-chosen number, or no patient at all); patients and admissions by the marker last name.
    s.sql.execute('DELETE FROM demographicExt WHERE value LIKE ?', [likeMarker]);
    s.sql.execute(`DELETE FROM admission WHERE client_id IN
      (SELECT demographic_no FROM demographic WHERE last_name LIKE ?)`, [likeMarker]);
    s.sql.execute('DELETE FROM demographic WHERE last_name LIKE ?', [likeMarker]);
    h.assert(s.sql.value('SELECT COUNT(*) FROM demographic WHERE last_name LIKE ?', [likeMarker]) === '0',
      'Owned REST patients were not removed');
    h.assert(s.sql.value('SELECT COUNT(*) FROM demographicExt WHERE value LIKE ?', [likeMarker]) === '0',
      'Owned extension rows were not removed');
  });

  const body = (lastName, extras) => ({
    firstName: 'Rest', lastName, sex: 'F', dobYear: '1980', dobMonth: '01', dobDay: '02',
    hcType: 'ON', providerNo: s.provider, admissionProgramId: Number(programId),
    address: { address: '1 Test Street', city: 'Toronto', province: 'ON', postal: 'M5V 2T6' },
    extras,
  });
  const extra = (key, value, demographicNo) => ({ key, value, demographicNo, providerNo: s.provider });
  const count = (sql, parameters) => Number(s.sql.value(sql, parameters));

  await s.step('an extra is linked to the new patient even when the client sent another number', async () => {
    const lastName = `${s.marker}-A`;
    const response = await s.context.request.post(endpoint, {
      data: body(lastName, [extra('demo_cell', cellA, CLIENT_CHOSEN_NO)]),
    });
    h.assert(response.status() === 200, `create expected HTTP 200, received ${response.status()}`);
    const saved = await response.json();
    await response.dispose();
    const patient = Number(saved.demographicNo);
    h.assert(Number.isSafeInteger(patient) && patient > 0 && patient !== CLIENT_CHOSEN_NO,
      'The create response carries no new demographic number');
    h.assert(count('SELECT COUNT(*) FROM demographic WHERE demographic_no=? AND last_name=?',
      [patient, lastName]) === 1, 'The patient row was not created');
    const linked = s.sql.rows('SELECT key_val,value FROM demographicExt WHERE demographic_no=?', [patient]);
    h.assert(linked.length === 1 && linked[0][0] === 'demo_cell' && linked[0][1] === cellA,
      `The new patient's extension rows are ${JSON.stringify(linked)}, expected one demo_cell row`);
    h.assert(count('SELECT COUNT(*) FROM demographicExt WHERE demographic_no=? AND value LIKE ?',
      [CLIENT_CHOSEN_NO, likeMarker]) === 0, 'An extension row was stored under the demographic number the client chose');
    const summary = await s.context.request.get(`${s.config.baseUrl}/ws/rs/demographics/summary/${patient}`);
    h.assert(summary.status() === 200, `summary expected HTTP 200, received ${summary.status()}`);
    h.assert((await summary.json()).alternativePhone === cellA,
      'The summary endpoint does not show the saved cell phone as the alternative phone');
    await summary.dispose();
  });

  await s.step('an extra sent without a number is linked to the new patient', async () => {
    const lastName = `${s.marker}-B`;
    const response = await s.context.request.post(endpoint, {
      data: body(lastName, [extra('demo_cell', cellB, null)]),
    });
    h.assert(response.status() === 200, `create expected HTTP 200, received ${response.status()}`);
    const patient = Number((await response.json()).demographicNo);
    await response.dispose();
    h.assert(Number.isSafeInteger(patient) && patient > 0, 'The create response carries no new demographic number');
    const rows = s.sql.rows('SELECT demographic_no FROM demographicExt WHERE value=?', [cellB]);
    h.assert(rows.length === 1 && rows[0][0] === String(patient),
      `The extra was stored as ${JSON.stringify(rows)}, expected one row linked to the new patient ${patient}`);
  });

  await s.step('colliding extras fail the request and keep no patient, admission or extension', async () => {
    const lastName = `${s.marker}-C`;
    const response = await s.context.request.post(endpoint, {
      data: body(lastName, [extra('demo_cell', `${s.marker}-dupA`, null), extra('demo_cell', `${s.marker}-dupB`, null)]),
    });
    h.assert(response.status() >= 400, `a duplicate-key create expected an error status, received ${response.status()}`);
    await response.dispose();
    const kept = s.sql.rows('SELECT demographic_no FROM demographic WHERE last_name=?', [lastName]);
    h.assert(kept.length === 0, `The failed create left the patient behind: ${JSON.stringify(kept)}`);
    h.assert(count(`SELECT COUNT(*) FROM admission a JOIN demographic d ON d.demographic_no=a.client_id
      WHERE d.last_name LIKE ?`, [`${s.marker}-C%`]) === 0, 'The failed create left an admission behind');
    // Found by value, not through a surviving demographic row: an extension orphaned by the rollback
    // (patient gone, extension kept) is exactly what this step must catch.
    h.assert(count('SELECT COUNT(*) FROM demographicExt WHERE value LIKE ?', [`${s.marker}-dup%`]) === 0,
      'The failed create left an extension behind');
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('demographic-rest-create-extras', workflow, { openPatient: false });
