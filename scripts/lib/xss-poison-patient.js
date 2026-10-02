/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Owned patient fixture whose text columns carry the inert xss-poison markup. Shared by the Master
// Record, E-Chart and schedule sweeps. The patient is found through its chart number, never its name,
// so no markup has to be typed into a search box (the WAF would refuse it, and that is not the point).
const h = require('./playwright-harness');

// Tables an opened chart can add rows to; removed by demographic number before the parent row.
const SUPPORT = [
  ['casemgmt_note_lock', 'demographic_no'], ['casemgmt_tmpsave', 'demographic_no'], ['demographicExt', 'demographic_no'],
  ['demographicArchive', 'demographic_no'], ['demographiccust', 'demographic_no'], ['measurementsDeleted', 'demographicNo'],
  ['demographicaccessory', 'demographic_no'], ['eChart', 'demographicNo'],
];

function seedPatient(seed, P, provider, chartNo) {
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
  // Support rows are removed first (the Seeder unwinds in reverse order, so register the parent LAST by
  // inserting these before it is deleted). A single entry removes every support table for this patient.
  seed.rows.push({
    table: 'demographicExt', where: `demographic_no=${demographicNo}`,
  });
  return demographicNo;
}

/** Remove support rows an opened chart may have created; call from a cleanup registered AFTER the seed. */
function purgeSupport(sql, demographicNo) {
  h.assert(/^[1-9]\d*$/.test(String(demographicNo)), 'purge needs an owned demographic number');
  sql.execute(SUPPORT.map(([table, column]) => `DELETE FROM ${table} WHERE ${column}=${demographicNo}`).join(';'));
}

module.exports = { seedPatient, purgeSupport, SUPPORT };
