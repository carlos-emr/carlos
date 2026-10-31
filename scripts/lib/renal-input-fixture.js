/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h = require('./playwright-harness');
const CASES = {
  'male-valid': {age: 60, sex: 'M', weight: '80', cr: '100', clearance: 80},
  'female-valid': {age: 60, sex: 'F', weight: '80', cr: '100', clearance: 68},
  'older-low-weight': {age: 80, sex: 'F', weight: '40', cr: '100', clearance: 26},
  'younger-high-weight': {age: 40, sex: 'M', weight: '100', cr: '100', clearance: 125},
  'missing-weight': {weight: null, cr: '100'}, 'missing-cr': {weight: '80', cr: null},
  'missing-both': {weight: null, cr: null}, 'future-age': {age: -1, weight: '80', cr: '100'},
  'lab-only': {weight: '80', cr: null, lab: '100', labDate: '03-Jan-25', clearance: 80, source: '2025-Jan-03'},
  'lab-newer': {weight: '80', cr: '200', crDate: '2025-01-02', lab: '100', labDate: '03-Jan-25', clearance: 80, source: '2025-Jan-03'},
  'measurement-newer': {weight: '80', cr: '200', crDate: '2025-01-04', lab: '100', labDate: '03-Jan-25', clearance: 40, source: '2025-Jan-04'},
  // A tie deliberately prefers the lab: the production contract changes to a
  // measurement only when its observation date is strictly newer.
  'equal-date-lab': {weight: '80', cr: '200', crDate: '2025-01-03', lab: '100', labDate: '03-Jan-25', clearance: 80, source: '2025-Jan-03'},
  'newer-unusable-source': {weight: '80', cr: 'not-a-number', crDate: '2025-01-04', lab: '100', labDate: '03-Jan-25'},
};
for (const [name, value] of Object.entries({blank: '', text: 'not-a-number', zero: '0', negative: '-1', nan: 'NaN', infinity: 'Infinity'})) {
  CASES['weight-'+name] = {weight: value, cr: '100'};
  CASES['cr-'+name] = {weight: '80', cr: value};
}
for (const [clearance, cr] of [[4,'2000'],[5,'1600'],[6,'1333.333333'],[29,'275.862069'],[30,'266.666667'],[31,'258.064516']]) {
  CASES['boundary-'+clearance] = {weight: '80', cr, clearance};
}
function renalInputFixture(s) {
  const {sql, patient, provider, marker} = s;
  const labs = [];
  function clear() {
    const ids = sql.rows(`SELECT id FROM measurements WHERE demographicNo=${patient}`).map(row=>row[0]);
    if (ids.length) sql.execute(`DELETE FROM measurementsExt WHERE measurement_id IN (${ids.join(',')})`);
    sql.execute(`DELETE FROM measurements WHERE demographicNo=${patient}`);
    for (const id of labs) {
      h.assert(sql.value(`SELECT COUNT(*) FROM labPatientPhysicianInfo WHERE id=${id} AND accession_num=${h.sqlString(marker)}`) === '1', 'Renal lab fixture ownership changed');
      sql.execute(`DELETE FROM patientLabRouting WHERE lab_no=${id} AND lab_type='CML' AND demographic_no=${patient};
        DELETE FROM labTestResults WHERE labPatientPhysicianInfo_id=${id};
        DELETE FROM labPatientPhysicianInfo WHERE id=${id} AND accession_num=${h.sqlString(marker)}`);
    }
    labs.length=0;
  }
  s.cleanup(clear);
  function measure(type, value, date, instruction) {
    if (value === null || value === undefined) return;
    sql.execute(`INSERT INTO measurements(type,demographicNo,providerNo,dataField,measuringInstruction,comments,dateObserved,dateEntered)
      VALUES (${h.sqlString(type)},${patient},${h.sqlString(provider)},${h.sqlString(value)},${h.sqlString(instruction)},${h.sqlString(marker)},${h.sqlString(date)},NOW())`);
  }
  return {clear, apply(testCase) {
    clear();
    const age = testCase.age ?? 60;
    sql.execute(`UPDATE demographic SET sex=${h.sqlString(testCase.sex || 'M')},year_of_birth=YEAR(CURDATE())-(${age}),month_of_birth='01',date_of_birth='01' WHERE demographic_no=${patient}`);
    measure('WT',testCase.weight,'2025-01-01','in kg'); measure('SCR',testCase.cr,testCase.crDate || '2025-01-02','umol/L');
    if (testCase.lab !== undefined) {
      const id = sql.value(`INSERT INTO labPatientPhysicianInfo(accession_num,patient_first_name,patient_last_name,collection_date,lastUpdateDate)
        VALUES (${h.sqlString(marker)},'Synthetic',${h.sqlString(marker)},${h.sqlString(testCase.labDate)},NOW()); SELECT LAST_INSERT_ID()`);
      h.assert(/^[1-9]\d*$/.test(id), 'Renal lab fixture was not inserted'); labs.push(id);
      sql.execute(`INSERT INTO labTestResults(labPatientPhysicianInfo_id,test_name,abn,minimum,maximum,units,result)
        VALUES (${id},'CREATININE','','0','9999','umol/L',${h.sqlString(testCase.lab)});
        INSERT INTO patientLabRouting(lab_no,demographic_no,lab_type) VALUES (${id},${patient},'CML')`);
    }
  }};
}
module.exports = {CASES, renalInputFixture};
