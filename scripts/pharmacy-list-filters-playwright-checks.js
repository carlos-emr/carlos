#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const { assert, sqlString } = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

async function workflow(s) {
  const ids = [];
  s.cleanup(() => {
    s.sql.execute(`DELETE FROM demographicPharmacy WHERE demographic_no=${s.patient}`);
    for (const id of ids) {
      assert(s.sql.value(`SELECT COUNT(*) FROM demographicPharmacy WHERE pharmacyID=${id}`) === '0',
        'An owned pharmacy was linked outside the synthetic patient; retain it for inspection');
      s.sql.execute(`DELETE FROM pharmacyInfo WHERE recordID=${id} AND name LIKE ${sqlString(s.marker + '%')}`);
      assert(s.sql.value(`SELECT COUNT(*) FROM pharmacyInfo WHERE recordID=${id}`) === '0', 'Pharmacy fixture cleanup failed');
    }
  });
  for (const fixture of [
    {name:'[Care]+ Pharmacy', address:'10 Clinic Street', city:'Montréal', postal:'H1A 1A1', phone:'4165550200', fax:'4165550100'},
    {name:'Care Pharmacy', address:'20 Other Street', city:'Toronto', postal:'M1A 1A1', phone:'4165550201', fax:'4165550101'},
  ]) {
    const id = s.sql.value(`INSERT INTO pharmacyInfo(name,address,city,province,postalCode,phone1,fax,status)
      VALUES(${sqlString(s.marker + ' ' + fixture.name)},${sqlString(fixture.address)},${sqlString(fixture.city)},'ON',
      ${sqlString(fixture.postal)},${sqlString(fixture.phone)},${sqlString(fixture.fax)},'1'); SELECT LAST_INSERT_ID()`);
    assert(/^[1-9]\d*$/.test(id), 'Pharmacy fixture was not created');
    ids.push(id);
  }
  const rx = await s.popup(s.master, s.master.locator('a[onclick*="rx/choosePatient"]').first(), 'pharmacy-rx');
  await rx.locator('a[href$="/rx/managePharmacy"]').click();
  await rx.waitForURL(/\/rx\/managePharmacy(?:\?|$)/);
  assert(await rx.locator('#demographicNo').inputValue() === s.patient, 'Pharmacy screen lost the selected patient');
  const row = id => rx.locator(`tr.pharmacyItem[pharmId="${id}"]`);
  const expected = async (first, second) => {
    assert(await row(ids[0]).isVisible() === first && await row(ids[1]).isVisible() === second,
      'Pharmacy filters returned the wrong owned rows');
  };
  await s.step('literal punctuation filters without a script error', async () => {
    await rx.locator('#pharmacySearch').fill(s.marker);
    await expected(true, true);
    await rx.locator('#pharmacySearch').pressSequentially(' [');
    await expected(true, false);
    await rx.locator('#pharmacySearch').fill(`${s.marker} [Care]+`);
    await expected(true, false);
  });
  await s.step('name and address work in either input order', async () => {
    await rx.locator('#pharmacySearch').fill(s.marker);
    await rx.locator('#pharmacyAddressSearch').fill('10 Clinic');
    await expected(true, false);
    await rx.locator('#pharmacySearch').fill(`${s.marker} [Care]+`);
    await expected(true, false);
    await rx.locator('#pharmacyAddressSearch').fill('20 Other');
    await expected(false, false);
    await rx.locator('#pharmacySearch').fill(s.marker);
    await expected(false, true);
    await rx.locator('#pharmacyAddressSearch').fill('');
  });
  await s.step('every filter remains applied when the name changes', async () => {
    for (const [field, value] of [['#pharmacyCitySearch','MONTRÉAL'], ['#pharmacyPostalCodeSearch','h1a'],
      ['#pharmacyPhoneSearch','0200'], ['#pharmacyFaxSearch','0100']]) {
      await rx.locator(field).fill(value);
      await rx.locator('#pharmacySearch').fill(s.marker.toLowerCase());
      await expected(true, false);
      await rx.locator(field).fill('not found');
      await rx.locator('#pharmacySearch').fill(s.marker);
      await expected(false, false);
      await rx.locator(field).fill('');
      await expected(true, true);
    }
  });
  await s.step('select the filtered pharmacy and persist the patient association', async () => {
    await rx.locator('#pharmacySearch').fill(`${s.marker} [Care]+`);
    await row(ids[0]).locator('.pharmacyName').click();
    await expectValue(s.sql, `SELECT COUNT(*) FROM demographicPharmacy
      WHERE demographic_no=${s.patient} AND pharmacyID=${ids[0]} AND status='1'`, '1',
      'Selecting the filtered pharmacy did not create its patient association');
    await rx.locator('#preferredList').filter({hasText: `${s.marker} [Care]+ Pharmacy`}).waitFor();
    await rx.reload();
    await rx.locator('#preferredList').filter({hasText: `${s.marker} [Care]+ Pharmacy`}).waitFor();
  });
}
if (require.main === module) runWorkflow('pharmacy-list-filters', workflow);
module.exports = {workflow};
