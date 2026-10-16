#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The prescription REST endpoints (/ws/rs/rx, session login) answer for the patient a prescription
 * is stored under, never the patient named in the path (issue #2822).
 *
 * They used to trust the path: a watermark or PDF for any script number could be requested under any
 * patient the caller could read, recording a print and returning that script's drug text, and
 * re-prescribing a drug needed no write access to its patient. Asserted:
 *   1. The stored patient's own script is served: GET {patient}/watermark/{script} answers a PNG and
 *      POST recordPrescriptionPrint/{script} records the print (prescription.date_printed).
 *   2. The same script under another patient's number is refused with 404 before anything is drawn or
 *      recorded: the watermark and the PDF print both answer 404, neither carries the drug text, and the
 *      prescription row is unchanged.
 *   3. A script that also holds another patient's drug row (a legacy or imported mix) is refused with
 *      404, so that patient's drug text is never drawn into the first patient's watermark.
 *   4. Re-prescribing a drug whose patient grants the provider read but not write on _rx is refused
 *      with a plain-text 403 "Access denied" from the /ws/rs server's AccessDeniedExceptionMapper. The
 *      answer names no patient and no drugs row is added. This is also the live proof that the mapper
 *      is registered on that server: the web application context does not start when it is not.
 *
 * Fixtures: the owned FAKE- patient; a second owned FAKE- patient (the other path patient and the
 * foreign drug row's owner); one prescription row holding one drug for the first patient; and, for
 * step 4 only, one patient-specific privilege row (role doctor, object _rx$<patient>, privilege r)
 * for the test login's doctor role. Cleanup removes every one of them and asserts none remain. No page
 * is driven: the logged-in session's request context calls the endpoints.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

const PNG_SIGNATURE = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

async function workflow(s) {
  const { sql, patient, provider, marker, config, context } = s;
  const rest = `${config.baseUrl}/ws/rs/rx`;
  const otherName = `${marker}B`;
  const privilegeObject = `_rx$${patient}`;
  const ids = { other: null, script: null, drug: null };
  // Registered before the first INSERT, so a failure part-way through still removes what was made.
  s.cleanup(() => {
    const patients = [patient, ids.other].filter(Boolean);
    h.assert(patients.every(id => /^[1-9]\d*$/.test(String(id))), 'Owned patient id is invalid');
    sql.execute(`DELETE FROM secObjPrivilege WHERE objectName=${h.sqlString(privilegeObject)};
      DELETE FROM drugs WHERE demographic_no IN (${patients.join(',')});
      DELETE FROM prescription WHERE demographic_no IN (${patients.join(',')});
      ${ids.other ? `DELETE FROM demographic WHERE demographic_no=${ids.other} AND last_name=${h.sqlString(otherName)};` : ''}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM secObjPrivilege WHERE objectName=${h.sqlString(privilegeObject)})
      + (SELECT COUNT(*) FROM drugs WHERE demographic_no IN (${patients.join(',')}))
      + (SELECT COUNT(*) FROM prescription WHERE demographic_no IN (${patients.join(',')}))
      + (SELECT COUNT(*) FROM demographic WHERE last_name=${h.sqlString(otherName)})`) === '0',
    'The Rx REST fixtures were not removed');
  });
  ids.other = sql.value(`INSERT INTO demographic
      (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
       provider_no,hc_type,province,roster_status,lastUpdateDate)
    VALUES (${h.sqlString(otherName)},'Workflow','1981','02','03','M','AC',
      ${h.sqlString(provider)},'ON','ON','NR',NOW()); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(ids.other), 'The second patient fixture was not created');
  ids.script = sql.value(`INSERT INTO prescription (provider_no,demographic_no,date_prescribed,textView,lastUpdateDate)
    VALUES (${h.sqlString(provider)},${patient},CURDATE(),${h.sqlString(marker)},NOW()); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(ids.script), 'The prescription fixture was not created');
  const insertDrug = owner => sql.value(`INSERT INTO drugs (provider_no,demographic_no,script_no,rx_date,end_date,
      written_date,customName,special,archived,archived_reason,create_date,position,lastUpdateDate,dispenseInternal,
      quantity,duration,durunit,freqcode)
    VALUES (${h.sqlString(provider)},${owner},${ids.script},CURDATE(),DATE_ADD(CURDATE(),INTERVAL 30 DAY),CURDATE(),
      ${h.sqlString(marker)},${h.sqlString(`${marker} 1 tab PO daily`)},0,'',NOW(),0,NOW(),0,'30','30','D','OD');
    SELECT LAST_INSERT_ID()`);
  ids.drug = insertDrug(patient);
  h.assert(/^[1-9]\d*$/.test(ids.drug), 'The drug fixture was not created');

  const printState = () => JSON.stringify(sql.rows(`SELECT IFNULL(date_printed,''),IFNULL(dates_reprinted,'')
    FROM prescription WHERE script_no=${ids.script}`));
  const refused = async (response, status, label) => {
    const body = await response.text();
    await response.dispose();
    h.assert(response.status() === status, `${label} answered HTTP ${response.status()}, not ${status}`);
    h.assert(!body.includes(marker), `${label} carried the prescription's drug text`);
    const patientIds = new Set([String(patient), String(ids.other)]);
    h.assert(!body.split(/\D+/).some(number => patientIds.has(number)), `${label} named a patient`);
    return body;
  };

  await s.step('the stored patient\'s own script answers its watermark and records its print', async () => {
    const watermark = await context.request.get(`${rest}/${patient}/watermark/${ids.script}`);
    const image = await watermark.body();
    const type = watermark.headers()['content-type'] || '';
    await watermark.dispose();
    h.assert(watermark.status() === 200, `The watermark answered HTTP ${watermark.status()}`);
    h.assert(type.startsWith('image/png') && image.subarray(0, 8).equals(PNG_SIGNATURE), 'The watermark is not a PNG');
    h.assert(printState() === JSON.stringify([['', '']]), 'The prescription was already marked printed');
    const printed = await context.request.post(`${rest}/recordPrescriptionPrint/${ids.script}`,
      { headers: { Accept: 'application/json' } });
    await printed.dispose();
    h.assert(printed.status() === 200, `recordPrescriptionPrint answered HTTP ${printed.status()}`);
    h.assert(sql.value(`SELECT date_printed IS NOT NULL FROM prescription WHERE script_no=${ids.script}`) === '1',
      'recordPrescriptionPrint did not record the print');
  });

  await s.step('the script under another patient\'s number is refused before anything is drawn or recorded', async () => {
    const before = printState();
    await refused(await context.request.get(`${rest}/${ids.other}/watermark/${ids.script}`), 404,
      'The watermark under a forged patient');
    await refused(await context.request.post(`${rest}/${ids.other}/print/${ids.script}`,
      { data: { printPoints: [] } }), 404, 'The PDF print under a forged patient');
    h.assert(printState() === before, 'A refused request changed the prescription\'s print record');
  });

  await s.step('a script that also holds another patient\'s drug row is refused', async () => {
    const foreign = insertDrug(ids.other);
    h.assert(/^[1-9]\d*$/.test(foreign), 'The foreign drug row was not created');
    try {
      await refused(await context.request.get(`${rest}/${patient}/watermark/${ids.script}`), 404,
        'The watermark of a mixed-patient script');
    } finally {
      sql.execute(`DELETE FROM drugs WHERE drugid=${foreign} AND demographic_no=${ids.other}`);
    }
  });

  await s.step('re-prescribing without write access to the drug\'s patient is a plain 403 that names no patient', async () => {
    const drugsBefore = sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`);
    sql.execute(`INSERT INTO secObjPrivilege (roleUserGroup,objectName,privilege,priority,provider_no)
      VALUES ('doctor',${h.sqlString(privilegeObject)},'r',0,${h.sqlString(provider)})`);
    try {
      const response = await context.request.post(`${rest}/drug/${ids.drug}`,
        { data: {}, headers: { Accept: 'application/json' } });
      const type = response.headers()['content-type'] || '';
      const body = await refused(response, 403, 'Re-prescribing without _rx write access');
      h.assert(type.startsWith('text/plain') && body === 'Access denied',
        'The refusal is not the plain "Access denied" the REST exception mapper writes');
    } finally {
      sql.execute(`DELETE FROM secObjPrivilege WHERE objectName=${h.sqlString(privilegeObject)}`);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`) === drugsBefore,
      'A refused re-prescription added a drugs row');
  });
}

if (require.main === module) runWorkflow('rx-rest-stored-patient', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
