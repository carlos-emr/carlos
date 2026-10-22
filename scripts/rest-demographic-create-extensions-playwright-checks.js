#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/* Validate POST /ws/rs/demographics with extras through the installed REST service (issue #5119).
 *
 * Creating a patient with extras (for example demo_cell) must:
 *   1. store every extension under the NEW patient's demographic_no, whether the client sent no
 *      number or someone else's;
 *   2. treat a client-supplied extension id as a create, never as an update that re-points another
 *      patient's existing extension row;
 *   3. be atomic: a colliding extension key leaves no patient, admission or extension behind.
 *
 * Every patient created here carries a FAKE-PW marker last name and a marker extension value, and is
 * removed on success or failure.
 * Environment: BASE_URL, CHROME_PATH, TEST_USER, TEST_PASSWORD, TEST_PIN, MYSQL_*.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

function createBody(session, lastName, extras, program) {
  return {
    lastName,
    firstName: 'Extension',
    sex: 'F',
    dobYear: '1980',
    dobMonth: '03',
    dobDay: '14',
    providerNo: session.provider,
    admissionProgramId: Number(program),
    extras,
  };
}

async function postCreate(session, body) {
  return session.context.request.post(h.appUrl(session.config.baseUrl, '/ws/rs/demographics'), {
    headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
    data: body,
    timeout: 30000,
  });
}

async function workflow(session) {
  const { sql, marker, provider } = session;
  const program = sql.value(`SELECT id FROM program WHERE programStatus='active' ORDER BY id LIMIT 1`);
  h.assert(/^[1-9]\d*$/.test(String(program)), 'No active program exists for the admission');

  // Rows are matched by the marker, so cleanup never touches a shared demo chart.
  session.cleanup(() => {
    const owned = `SELECT demographic_no FROM demographic WHERE last_name LIKE ${h.sqlString(`${marker}%`)}`;
    sql.execute(`DELETE FROM demographicExt WHERE value LIKE ${h.sqlString(`${marker}%`)}
      OR demographic_no IN (${owned})`);
    sql.execute(`DELETE FROM admission WHERE client_id IN (${owned})`);
    sql.execute(`DELETE FROM demographic WHERE last_name LIKE ${h.sqlString(`${marker}%`)}`);
  });

  await session.step('extras created with a patient are linked to the new patient', async () => {
    const value = `${marker}-cell`;
    const response = await postCreate(session, createBody(session, `${marker}A`,
      [{ key: 'demo_cell', value }], program));
    h.assert(response.status() === 200, `REST create answered HTTP ${response.status()}`);
    const created = await response.json();
    const createdNo = String(created.demographicNo);
    h.assert(/^[1-9]\d*$/.test(createdNo), 'REST create returned no demographicNo');
    const owner = sql.value(`SELECT demographic_no FROM demographicExt
      WHERE key_val='demo_cell' AND value=${h.sqlString(value)}`);
    h.assert(owner === createdNo,
      `the extension is stored under demographic_no ${JSON.stringify(owner)}, not the new patient ${createdNo}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM admission WHERE client_id=${createdNo}`) === '1',
      'the new patient has no single admission');
  });

  await session.step('a client-supplied extension demographicNo is replaced by the new patient number', async () => {
    const value = `${marker}-supplied`;
    const response = await postCreate(session, createBody(session, `${marker}B`,
      [{ key: 'demo_cell', value, demographicNo: 2147483000 }], program));
    h.assert(response.status() === 200, `REST create answered HTTP ${response.status()}`);
    const createdNo = String((await response.json()).demographicNo);
    const owner = sql.value(`SELECT demographic_no FROM demographicExt WHERE value=${h.sqlString(value)}`);
    h.assert(owner === createdNo, `the extension kept the client-supplied owner ${JSON.stringify(owner)}`);
  });

  await session.step('a client-supplied extension id never re-points another patient\'s extension', async () => {
    // A victim patient with an extension row the request will try to claim by id.
    const victim = h.insertId(sql, `INSERT INTO demographic
      (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,provider_no,
       hc_type,province,roster_status,lastUpdateDate)
      VALUES (${h.sqlString(`${marker}V`)},'Victim','1975','05','06','M','AC',${h.sqlString(provider)},
        'ON','ON','NR',NOW())`, 'victim patient');
    const victimValue = `${marker}-victim`;
    const victimExt = h.insertId(sql, `INSERT INTO demographicExt (demographic_no,provider_no,key_val,value,date_time)
      VALUES (${victim},${h.sqlString(provider)},'demo_cell',${h.sqlString(victimValue)},NOW())`, 'victim extension');

    const attackerValue = `${marker}-claimed`;
    const response = await postCreate(session, createBody(session, `${marker}C`,
      [{ id: Number(victimExt), key: 'demo_cell', value: attackerValue }], program));
    h.assert(response.status() === 200, `REST create answered HTTP ${response.status()}`);
    const createdNo = String((await response.json()).demographicNo);

    const victimRow = sql.rows(`SELECT demographic_no, value FROM demographicExt WHERE id=${victimExt}`);
    h.assert(victimRow.length === 1 && String(victimRow[0][0]) === victim && victimRow[0][1] === victimValue,
      `the victim's extension row was changed or re-pointed: ${JSON.stringify(victimRow)}`);
    const own = sql.value(`SELECT demographic_no FROM demographicExt WHERE value=${h.sqlString(attackerValue)}`);
    h.assert(own === createdNo, 'the new patient did not get its own extension row');
  });

  await session.step('a colliding extension key leaves no partial patient behind', async () => {
    const lastName = `${marker}D`;
    const response = await postCreate(session, createBody(session, lastName, [
      { key: 'demo_cell', value: `${marker}-first` },
      { key: 'demo_cell', value: `${marker}-second` },
    ], program));
    h.assert(response.status() >= 400, `a duplicate extension key was accepted (HTTP ${response.status()})`);
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE last_name=${h.sqlString(lastName)}`) === '0',
      'the failed create left a patient row');
    h.assert(sql.value(`SELECT COUNT(*) FROM demographicExt WHERE value LIKE ${h.sqlString(`${marker}-first`)}
      OR value LIKE ${h.sqlString(`${marker}-second`)}`) === '0', 'the failed create left an extension row');
    h.assert(sql.value(`SELECT COUNT(*) FROM admission WHERE client_id NOT IN (SELECT demographic_no FROM demographic)
      AND admission_date >= DATE_SUB(NOW(), INTERVAL 1 HOUR)`) === '0', 'the failed create left an orphan admission');
  });
}

if (require.main === module) runWorkflow('rest-demographic-create-extensions', workflow, { openPatient: false });
module.exports = { workflow };
