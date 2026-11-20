/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const h = require('./playwright-harness');

// Explicit opt-in for workflows that submit their own Canadian master-record fixture.
// The country-of-origin field is independent of this mailing-address precondition.
const POSTAL = 'K1A0B1';
const CANADIAN_PROVINCE = /^(?:CA-)?(?:AB|BC|MB|NB|NL|NT|NS|NU|ON|PE|QC|SK|YT)$/;
function ownerClause(s) {
  const patient = String(s.patient);
  h.assert(/^[1-9]\d*$/.test(patient) && patient.length <= 10 && BigInt(patient) <= 2147483647n,
    'The owned postal patient identity is invalid');
  h.assert(/^FAKE-PW[0-9a-f]{16}$/.test(s.marker), 'The owned postal marker is invalid');
  return `demographic_no=${patient} AND last_name=${h.sqlString(s.marker)}`;
}
function ownedAddress(s) {
  const rows = s.sql.rows(`SELECT province, IFNULL(postal,'') FROM demographic WHERE ${ownerClause(s)}`);
  h.assert(rows.length === 1 && rows[0].length === 2, 'The owned postal patient ownership changed');
  h.assert(CANADIAN_PROVINCE.test(rows[0][0]), 'The owned postal fixture requires an existing Canadian province');
  return rows[0];
}
async function assertOwnedMasterPostal(s) {
  const [, postal] = ownedAddress(s);
  h.assert(postal === POSTAL, 'The owned postal fixture changed');
  h.assert(new URL(s.master.url()).searchParams.get('demographic_no') === String(s.patient),
    'The postal Master Record belongs to another patient');
  h.assert(await s.master.locator('input[name="postal"]').inputValue() === POSTAL,
    'The native postal field changed');
  const province = s.master.locator('select[name="province"]');
  await province.locator('option').first().waitFor({ state: 'attached', timeout: 20000 });
  h.assert(CANADIAN_PROVINCE.test(await province.inputValue()), 'The native mailing province is not Canadian');
  const country = s.master.locator('select[name="country"]');
  if (await country.count()) h.assert(await country.inputValue() === 'CA', 'The native mailing country is not Canada');
}
async function prepareOwnedMasterPostal(s) {
  const [province] = ownedAddress(s);
  s.sql.execute(`UPDATE demographic SET postal=${h.sqlString(POSTAL)} WHERE ${ownerClause(s)} AND province=${h.sqlString(province)}`);
  h.assert(ownedAddress(s)[1] === POSTAL, 'The owned postal fixture was not stored');
  await s.master.reload({ waitUntil: 'domcontentloaded' });
  await assertOwnedMasterPostal(s);
}
module.exports = { prepareOwnedMasterPostal, assertOwnedMasterPostal };
