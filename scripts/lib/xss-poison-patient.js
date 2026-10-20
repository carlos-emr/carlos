/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Owned patient fixture whose text columns carry the inert xss-poison markup. Shared by the Master
// Record, E-Chart, schedule, document, eForm and messenger sweeps. The patient is found through its chart
// number, never its name, so no markup has to be typed into a search box (the WAF would refuse it, and
// that is not the point).

// Rows the application itself may add under the patient while the sweep walks the chart (locks, temp
// saves, archive copies, audit rows). They are recorded right after the patient row, so the Seeder (which
// unwinds in reverse) removes them after this run's own child rows and before the patient itself. Derived from
// the payload sweep's PATIENT_TABLES, less the tables the checks seed themselves (and so record by key), so the
// per-run cleanup and the killed-run sweep always cover the same application-added tables.
const h = require('./playwright-harness');
const { PATIENT_TABLES } = require('./xss-poison-helpers');

const SEEDED_BY_CHECKS = new Set(['allergies', 'drugs', 'measurements', 'preventions', 'eform_data', 'tickler',
  'consultationRequests', 'relationships', 'waitingList']);
const SUPPORT = PATIENT_TABLES.filter(([table]) => !SEEDED_BY_CHECKS.has(table));

function seedPatient(seed, P, provider, chartNo) {
  // The checks find this patient by chart number and require exactly one result, so a number already in use
  // (by real data or another run) must stop the run here rather than fail the search later.
  if (seed.sql.value(`SELECT COUNT(*) FROM demographic WHERE chart_no=${h.sqlString(chartNo)}`) !== '0') {
    throw new Error(`xss-poison patient chart number ${chartNo} is already in use`);
  }
  const demographicNo = seed.insert('demographic', {
    last_name: P('patient last name', 30), first_name: P('patient first name', 30), middleNames: P('patient middle names', 100),
    alias: P('patient alias', 70), pref_name: P('patient preferred name', 30), address: P('patient address', 60),
    city: P('patient city', 50), province: 'ON', postal: 'K1A0B1', phone: '6135550100', email: P('patient email', 100),
    previousAddress: P('patient previous address', 60), residentialAddress: P('patient residential address', 60),
    residentialCity: P('patient residential city', 50), family_doctor: P('patient family doctor', 80),
    citizenship: P('patient citizenship', 40), official_lang: P('patient official language', 60), spoken_lang: P('patient spoken language', 60),
    year_of_birth: '1980', month_of_birth: '01', date_of_birth: '02', sex: 'F', patient_status: 'AC', hc_type: P('patient health card type', 20),
    roster_status: 'NR', roster_enrolled_to: P('patient roster enrolled to', 20), provider_no: provider, chart_no: chartNo, lastUpdateDate: { raw: 'NOW()' },
  }, { key: 'demographic_no' });
  for (const [table, column] of SUPPORT) seed.track(table, `${column}=${demographicNo}`);
  return demographicNo;
}

module.exports = { seedPatient, SUPPORT };
