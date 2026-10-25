/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const h = require('./playwright-harness');
const { signIn, classify, forbiddenByApp, probe } = require('./authz-read-probe');
const { csrfToken } = require('./front-door-checks');
const { readPatientState, assertPatientStateUnchanged } = require('../echart-two-patient-isolation-playwright-checks');
const q = h.sqlString;

function snapshot(sql, patient) {
  return {
    ...readPatientState(sql, patient),
    locks: sql.rows(`SELECT * FROM casemgmt_note_lock WHERE demographic_no=${patient} ORDER BY id`),
  };
}

/** Module rights do not override a separate patient-record restriction. */
async function checkLockedRecordMeasurements(s, { fixture }) {
  const { sql, provider, marker, config } = s;
  const patients = [];
  const owner = `${marker}-MR`;
  // Register before INSERT; all descendants are restricted to these exact owned patients.
  s.cleanup(() => {
    for (const patient of patients) {
      h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient}
        AND last_name=${q(owner)}`) === '1', 'Measurement access fixture ownership changed');
      const clinical = snapshot(sql, patient);
      for (const [name, rows] of Object.entries(clinical)) {
        if (['demographic', 'demographicExtensions', 'measurements'].includes(name)) continue;
        h.assert(rows.length === 0, `Unexpected ${name} retained on measurement fixture; keeping its patient`);
      }
      sql.execute(`DELETE FROM measurementsExt WHERE measurement_id IN
          (SELECT id FROM measurements WHERE demographicNo=${patient});
        DELETE FROM measurements WHERE demographicNo=${patient};
        DELETE FROM measurementsDeleted WHERE demographicNo=${patient};
        DELETE FROM log WHERE demographic_no=${patient};
        DELETE FROM demographicExt WHERE demographic_no=${patient};
        DELETE FROM demographicArchive WHERE demographic_no=${patient};
        DELETE FROM demographic WHERE demographic_no=${patient} AND last_name=${q(owner)}`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient})
        + (SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient})
        + (SELECT COUNT(*) FROM measurementsDeleted WHERE demographicNo=${patient})
        + (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})
        + (SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no=${patient})
        + (SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${patient})`) === '0',
      'Measurement access fixture or unexpected clinical rows survived cleanup');
    }
  });
  for (const label of ['LockedRecord', 'OpenRecord']) {
    const patient = h.insertId(sql, `INSERT INTO demographic
      (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
       provider_no,hc_type,province,roster_status,lastUpdateDate)
      VALUES (${q(owner)},${q(label)},'1980','01','02','F','AC',${q(provider)},'ON','ON','NR',NOW())`,
    'measurement record-access patient');
    h.assert(/^[1-9]\d*$/.test(patient), 'Measurement record-access patient key is invalid');
    patients.push(patient);
  }
  const [lockedPatient, openPatient] = patients;
  const seed = [[lockedPatient, '70.375'], [openPatient, '80.125']];
  h.assert(sql.value("SELECT COUNT(*) FROM measurementType WHERE type='WT' AND measuringInstruction='in kg'") === '1',
    'The owned measurement access controls need one seeded numeric WT type');
  for (const [patient, value] of seed) {
    h.insertId(sql, `INSERT INTO measurements
      (type,demographicNo,providerNo,dataField,measuringInstruction,comments,dateObserved,dateEntered)
      VALUES ('WT',${patient},${q(provider)},${q(value)},'in kg',${q(owner)},'2026-10-15',NOW())`,
    'measurement record-access reading');
    // The first demographic read initializes legacy demographic fields. Complete this
    // allowed owner operation before the exact read/write refusal snapshots.
    const response = await s.context.request.get(h.appUrl(config.baseUrl,
      `/demographic/DemographicEdit?demographic_no=${patient}`), { maxRedirects: 0 });
    try { h.assert(response.status() === 200, 'The owner could not initialize its measurement fixture'); }
    finally { await response.dispose(); }
  }
  const group = sql.value('SELECT groupName FROM measurementGroupStyle ORDER BY groupID LIMIT 1');
  h.assert(group, 'The measurement setup positive control needs a seeded group');
  const role = fixture.addRole({ _demographic: 'r', _appointment: 'r', _eChart: 'r', _measurement: 'w' });
  const login = fixture.addLogin(role);
  fixture.joinTestLoginPrograms(login);
  h.assert(fixture.lockPatient(login, lockedPatient, ['_demographic']).length === 1,
    'The record-only restriction was not installed');
  const routes = [
    ['/encounter/oscarMeasurements/SetupMeasurements', { groupName: group }, 'html'],
    ['/encounter/oscarMeasurements/SetupHistoryIndex', {}, 'html'],
    ['/encounter/oscarMeasurements/SetupDisplayHistory', { type: 'WT' }, 'html'],
    ['/encounter/GraphMeasurements', { type: 'WT' }, 'image/png'],
    ['/servlet/io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.ScatterPlotChartServlet',
      { type: 'WT', mInstrc: 'in kg' }, 'image/jpeg'],
    ['/encounter/displayMeasurements', { cmd: 'measurements' }, 'navbar'],
  ];
  await s.step('measurement read and navbar routes enforce record access despite module rights', async () => {
    const before = patients.map(patient => snapshot(sql, patient));
    for (const [path, extras, type] of routes) {
      // A downstream demographic lookup can set accountLocked in the session. A fresh
      // real login for every route prevents that side effect from masking a missing gate.
      const reader = await signIn(s, login);
      try {
        const address = patient => h.appUrl(config.baseUrl, `${path}?${new URLSearchParams({
          demographicNo: patient, ...extras,
        })}`);
        const allowed = await probe(reader.context, address(openPatient), { needles: ['80.125'] });
        h.assert(allowed.status === 200 && allowed.fromApp && classify(allowed) === 'served',
          `${path}: the unlocked measurement control was not served`);
        if (type.startsWith('image/')) h.assert(allowed.type === type && allowed.length > 1000,
          `${path}: the unlocked plot was not an actual image`);
        if (type === 'navbar') h.assert(allowed.found.includes('80.125'),
          'The unlocked navbar did not show its own measurement');
        const denied = await probe(reader.context, address(lockedPatient), { needles: ['70.375', owner] });
        h.assert(forbiddenByApp(denied) && denied.found.length === 0,
          `${path}: a record-restricted reader received HTTP ${denied.status} or clinical data`);
      } finally { await reader.context.close(); }
      patients.forEach((patient, index) => assertPatientStateUnchanged(before[index], snapshot(sql, patient),
        `${path} record-access check`));
    }
  });
  await s.step('the patient photo servlet refuses the restricted record and serves its authorized fallback image', async () => {
    const before = patients.map(patient => snapshot(sql, patient));
    h.assert(sql.value(`SELECT COUNT(*) FROM client_image WHERE demographic_no IN (${patients.join(',')})`) === '0',
      'The photo fallback controls unexpectedly acquired an uploaded portrait');
    const reader = await signIn(s, login);
    try {
      // These owned patients have no uploaded portrait. Java separately proves exact
      // stored-GIF byte protection; this installed check exercises the real JPEG fallback.
      for (const patient of [openPatient, lockedPatient, openPatient]) {
        const response = await reader.context.request.get(h.appUrl(config.baseUrl,
          `/imageRenderingServlet?source=local_client&clientId=${patient}`), { maxRedirects: 0 });
        try {
          const body = await response.body();
          const imageType = (response.headers()['content-type'] || '').split(';')[0];
          h.assert(Object.hasOwn(response.headers(), h.APPLICATION_HEADER),
            'The patient photo response did not come from the application');
          if (patient === lockedPatient) {
            h.assert(response.status() === 403 && !imageType.startsWith('image/')
              && !body.toString('utf8').includes(owner), 'The record-restricted photo request was not refused before image output');
          } else {
            h.assert(response.status() === 200 && imageType === 'image/jpeg' && body.length > 4
              && body[0] === 0xff && body[1] === 0xd8 && body.at(-2) === 0xff && body.at(-1) === 0xd9,
            'The authorized patient photo control did not return the complete JPEG fallback');
          }
        } finally { await response.dispose(); }
        patients.forEach((ownedPatient, index) => assertPatientStateUnchanged(before[index], snapshot(sql, ownedPatient),
          'Patient photo record-access read'));
      }
    } finally { await reader.context.close(); }
  });
  await s.step('both measurement writer aliases refuse a restricted record without writes and allow the unlocked control', async () => {
    for (const [path, value] of [['/encounter/Measurements', '79.625'], ['/encounter/Measurements2', '79.875']]) {
      const reader = await signIn(s, login);
      try {
        const token = await csrfToken(reader.page, config.baseUrl);
        const form = patient => ({ demographicNo: patient, numType: '1', 'inputType-0': 'WT',
          'inputTypeDisplayName-0': 'Weight', 'inputValue-0': value, 'inputMInstrc-0': 'in kg',
          'comments-0': owner, 'date-0': '2026-10-15', ajax: 'true', skipCreateNote: 'true', 'CSRF-TOKEN': token });
        const before = patients.map(patient => snapshot(sql, patient));
        const allowed = await reader.context.request.post(h.appUrl(config.baseUrl, path), {
          form: form(openPatient), maxRedirects: 0,
        });
        try { h.assert(allowed.status() === 200 && Object.hasOwn(allowed.headers(), h.APPLICATION_HEADER),
          `${path}: the unlocked measurement write was not accepted by the application`); }
        finally { await allowed.dispose(); }
        h.assert(sql.value(`SELECT COUNT(*) FROM measurements WHERE demographicNo=${openPatient}
          AND providerNo=${q(login.providerNo)} AND dataField=${q(value)} AND comments=${q(owner)}`) === '1',
        `${path}: the unlocked write did not persist its exact owned reading`);
        const afterAllowed = patients.map(patient => snapshot(sql, patient));
        assertPatientStateUnchanged(before[0], afterAllowed[0], `${path} unlocked write bystander`);
        h.assert(afterAllowed[1].measurements.length === before[1].measurements.length + 1,
          `${path}: the unlocked write inserted an unexpected number of readings`);
        assertPatientStateUnchanged({ ...before[1], measurements: [] }, { ...afterAllowed[1], measurements: [] },
          `${path} unlocked write nonmeasurement state`);
        const denied = await reader.context.request.post(h.appUrl(config.baseUrl, path), {
          form: form(lockedPatient), maxRedirects: 0,
        });
        try { h.assert(denied.status() === 403 && Object.hasOwn(denied.headers(), h.APPLICATION_HEADER)
          && !(await denied.text()).includes(owner), `${path}: the restricted write was not refused cleanly`); }
        finally { await denied.dispose(); }
        patients.forEach((patient, index) => assertPatientStateUnchanged(afterAllowed[index], snapshot(sql, patient),
          `${path} refused write`));
      } finally { await reader.context.close(); }
    }
  });
  await s.step('the measurement module login is independently refused the locked master record', async () => {
    const reader = await signIn(s, login);
    try {
      const before = patients.map(patient => snapshot(sql, patient));
      const denied = await probe(reader.context, h.appUrl(config.baseUrl,
        `/demographic/DemographicEdit?demographic_no=${lockedPatient}`));
      const allowed = await probe(reader.context, h.appUrl(config.baseUrl,
        `/demographic/DemographicEdit?demographic_no=${openPatient}`));
      h.assert(forbiddenByApp(denied) && allowed.status === 200 && allowed.fromApp,
        'The patient record restriction did not discriminate independently of measurement rights');
      patients.forEach((patient, index) => assertPatientStateUnchanged(before[index], snapshot(sql, patient),
        'Measurement record restriction control'));
    } finally { await reader.context.close(); }
  });
}

module.exports = { checkLockedRecordMeasurements };
