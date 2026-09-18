#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Log in through the UI, then exercise the legacy pharmacy JSON search contract.
// This is an authenticated endpoint regression, not a pharmacy editor UI test.
// The newer fax picker uses literal LIKE matching; it must not change this
// endpoint's existing wildcard convention or consume literal exclamation marks.
const { assert, sqlString } = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

async function workflow(s) {
  const ids = [];
  s.cleanup(() => {
    for (const id of ids) s.sql.execute(`DELETE FROM pharmacyInfo WHERE recordID=${id} AND name LIKE ${sqlString(`${s.marker}%`)}`);
  });
  for (const suffix of ['! Pharmacy', ' Pharmacy']) {
    const id = s.sql.value(`INSERT INTO pharmacyInfo(name,address,city,province,status)
      VALUES(${sqlString(s.marker + suffix)},'123 Fixture Street','Fixture City','ON','1'); SELECT LAST_INSERT_ID()`);
    assert(/^[1-9]\d*$/.test(id), 'Pharmacy fixture was not created');
    ids.push(id);
  }
  const search = async term => {
    const response = await s.context.request.get(`${s.config.baseUrl}/rx/managePharmacy2`, {
      params: { method: 'search', term },
    });
    assert(response.ok(), `Pharmacy search failed with HTTP ${response.status()}`);
    assert((response.headers()['content-type'] || '').includes('application/json'), 'Pharmacy search did not return JSON');
    const rows = await response.json();
    assert(Array.isArray(rows), 'Pharmacy search did not return a result array');
    return rows.map(row => String(row.id)).sort();
  };
  await s.step('literal exclamation mark finds the matching pharmacy', async () => {
    assert(JSON.stringify(await search(`${s.marker}!`)) === JSON.stringify([ids[0]]),
      'Legacy search consumed the literal exclamation mark and lost its pharmacy');
  });
  await s.step('existing wildcard and city search remains compatible', async () => {
    assert(JSON.stringify(await search(`${s.marker}%Pharmacy,Fixture City`)) === JSON.stringify([...ids].sort()),
      'Legacy wildcard/city search changed its results');
    assert((await search(`${s.marker}!,Different City`)).length === 0, 'City filter was ignored');
  });
}
if (require.main === module) runWorkflow('pharmacy-search', workflow, { openPatient: false });
module.exports = { workflow };
