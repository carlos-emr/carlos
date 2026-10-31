#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Issue #5279 section 8: a Canadian Vaccine Catalogue lot's expiry date on a recorded vaccination.
 * prevention-brand-picker covers a catalogue lot with NO expiry date, and a historical undated lot; this covers dated
 * lots: the expiry date a chosen lot fills in, what is stored, what reopens, and what happens on a change of lot.
 * User path: E-Chart ▸ Preventions ▸ the recorded Tdap (a catalogue vaccination whose lot was not entered) ▸ Lot: the
 * first dated lot ▸ Save; the record ▸ Lot: the second dated lot ▸ Save; after a catalogue refresh drops that lot,
 * the record ▸ Save unchanged.
 * A default install has no catalogue address (cvc.url), so the page offers no catalogue search; the edit form of a
 * vaccination recorded from the catalogue is how a clinician reaches the catalogue's lot list there. That record is
 * an owned fixture, as are the synthetic brand, its medication and its lots; every lot choice is made in the form.
 * Asserts: the brand's lots are offered with no lot chosen and no expiry invented; choosing the first lot fills its
 * expiry date, and the save stores exactly that lot and date with the brand; the record reopens on that lot and date
 * (its summary too); choosing the second lot replaces the date in the form and in the replacement record, which holds
 * one expiry row, while the archived record keeps the first lot and date. When the catalogue no longer lists the
 * recorded lot, the record reopens with that lot and its recorded expiry in plain fields and an unchanged save keeps
 * both: a refreshed catalogue never erases or re-dates what was given.
 * Fixtures: the runWorkflow FAKE- patient and its recorded Tdap; a synthetic catalogue brand (not a generic, so the
 * Preventions page's list of types is untouched), its medication and two lots. Cleanup removes the catalogue rows, the
 * patient's preventions and their ext rows, and asserts none remain.
 */
const h = require('./lib/playwright-harness');
const { openPreventionIndex, ownPatientPreventions, preventionForms } = require('./lib/prevention-coverage');
const { runWorkflow } = require('./lib/workflow-session');

const TYPE = 'Tdap';
const GIVEN_ON = '2026-04-07';

async function workflow(s) {
  const { sql, patient, provider } = s;
  const q = h.sqlString;
  ownPatientPreventions(s);
  // Every synthetic key comes from the run's random marker, so two runs never share (or clean up) each other's rows.
  const stamp = s.marker.slice(-16);
  h.assert(/^[0-9a-f]{16}$/.test(stamp), 'The run marker does not end in 16 hex digits');
  const digits = (BigInt(`0x${stamp}`) % 10n ** 16n).toString().padStart(16, '0');
  const generic = `8${digits}`;
  const brand = `8${digits}1`;
  const brandName = `PWCVC${stamp} brand`;
  const first = { lot: `PWCVC${stamp}A`, expiry: '2027-03-31' };
  const second = { lot: `PWCVC${stamp}B`, expiry: '2027-09-30' };
  // Registered before the first INSERT, so a failed seed is still removed.
  s.cleanup(() => {
    sql.execute(`DELETE l FROM CVCMedicationLotNumber l JOIN CVCMedication m ON m.id=l.cvcMedicationId WHERE m.snomedCode=${q(brand)};
      DELETE FROM CVCMedication WHERE snomedCode=${q(brand)};
      DELETE FROM CVCImmunization WHERE snomedConceptId=${q(brand)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM CVCMedication WHERE snomedCode=${q(brand)})
      + (SELECT COUNT(*) FROM CVCImmunization WHERE snomedConceptId=${q(brand)})
      + (SELECT COUNT(*) FROM CVCMedicationLotNumber WHERE lotNumber IN (${q(first.lot)},${q(second.lot)}))`) === '0',
    'The synthetic catalogue rows were not removed');
  });
  const index = await openPreventionIndex(s);
  sql.execute(`INSERT INTO CVCImmunization (versionId,snomedConceptId,displayName,picklistName,generic,parentConceptId,ispa)
      VALUES (0,${q(brand)},${q(brandName)},${q(brandName)},0,${q(generic)},0);
    INSERT INTO CVCMedication (versionId,snomedCode,snomedDisplay,status,isBrand) VALUES (0,${q(brand)},${q(brandName)},'active',1);
    INSERT INTO CVCMedicationLotNumber (cvcMedicationId,lotNumber,expiryDate)
      SELECT id,${q(first.lot)},${q(first.expiry)} FROM CVCMedication WHERE snomedCode=${q(brand)}
      UNION ALL SELECT id,${q(second.lot)},${q(second.expiry)} FROM CVCMedication WHERE snomedCode=${q(brand)}`);
  const recorded = sql.value(`INSERT INTO preventions (demographic_no,creation_date,prevention_date,provider_no,prevention_type,
      deleted,refused,never,creator,lastUpdateDate,snomedId)
    VALUES (${patient},NOW(),${q(`${GIVEN_ON} 00:00:00`)},${q(provider)},${q(TYPE)},'0','0','0',${q(provider)},NOW(),${q(generic)});
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(recorded), 'The recorded vaccination fixture was not created');
  sql.execute(`INSERT INTO preventionsExt (prevention_id,keyval,val) VALUES (${recorded},'brandSnomedId',${q(brand)}),
    (${recorded},'name',${q(brandName)})`);
  await index.reload();
  await index.getByText('Immunization Schedules', { exact: false }).first().waitFor({ state: 'visible' });
  const forms = preventionForms(s, index, TYPE);
  const lotChoices = editor => editor.locator('#cvcLot option').evaluateAll(options =>
    options.map(option => `${option.value}=${option.getAttribute('expiryDate') || ''}`));
  /** The catalogue lot lookup the form starts on load has answered: the select holds `count` options. */
  const lotsLoaded = (editor, count) => editor.waitForFunction(n => document.getElementById('cvcLot')?.options.length === n, count);
  let id = recorded;
  let editor;

  await s.step('the brand\'s dated lots are offered, and choosing one fills its expiry date, which is stored with it', async () => {
    editor = await forms.open(id);
    await lotsLoaded(editor, 3);
    h.assert(await editor.locator('#cvcName').inputValue() === brand, 'The recorded brand is not selected');
    h.assert(await editor.locator('#cvcLot').isVisible(), 'The catalogue lots are not offered');
    const offered = await lotChoices(editor);
    h.assert(JSON.stringify(offered) === JSON.stringify(['=', `${first.lot}=${first.expiry}`, `${second.lot}=${second.expiry}`]),
      `The form offers the lots ${offered.join(', ')}`);
    h.assert(await editor.locator('#cvcLot').inputValue() === '' && await editor.locator('#expiryDate').inputValue() === '',
      'A lot or an expiry date was chosen before the clinician chose one');
    await editor.locator('#cvcLot').selectOption(first.lot);
    h.assert(await editor.locator('#expiryDate').inputValue() === first.expiry,
      `Choosing ${first.lot} filled the expiry date ${await editor.locator('#expiryDate').inputValue()}`);
    const previous = id;
    id = await forms.saveAmendment(editor, previous);
    const ext = forms.exts(id);
    h.assert(ext.lot === first.lot && ext.expiryDate === first.expiry && ext.brandSnomedId === brand,
      `The save stored lot ${ext.lot}, expiry ${ext.expiryDate}, brand ${ext.brandSnomedId}`);
    h.assert(sql.value(`SELECT IFNULL(snomedId,'none') FROM preventions WHERE id=${id}`) === generic, 'The save lost the vaccine\'s catalogue code');
  });

  await s.step('the record reopens on its lot and expiry date, and choosing the other lot replaces the date', async () => {
    editor = await forms.open(id);
    await lotsLoaded(editor, 3);
    await editor.waitForFunction(lot => document.getElementById('cvcLot').value === lot, first.lot);
    h.assert(await editor.locator('#expiryDate').inputValue() === first.expiry, 'The reopened record lost its expiry date');
    h.assert((await editor.locator('#summary').inputValue()).includes(`ExpiryDate: ${first.expiry}`), 'The summary lacks the expiry date');
    await editor.locator('#cvcLot').selectOption(second.lot);
    h.assert(await editor.locator('#expiryDate').inputValue() === second.expiry,
      `Changing to ${second.lot} left the expiry date at ${await editor.locator('#expiryDate').inputValue()}`);
    const previous = id;
    id = await forms.saveAmendment(editor, previous);
    const ext = forms.exts(id);
    h.assert(ext.lot === second.lot && ext.expiryDate === second.expiry,
      `The change of lot stored lot ${ext.lot} with expiry ${ext.expiryDate}`);
    const archived = forms.exts(previous);
    h.assert(archived.lot === first.lot && archived.expiryDate === first.expiry, 'The archived record no longer holds its own lot and expiry');
    editor = await forms.open(id);
    await lotsLoaded(editor, 3);
    await editor.waitForFunction(lot => document.getElementById('cvcLot').value === lot, second.lot);
    h.assert(await editor.locator('#expiryDate').inputValue() === second.expiry, 'The record reopened with another expiry date');
    await editor.close();
  });

  await s.step('a recorded lot the refreshed catalogue no longer lists keeps its lot and expiry date through a save', async () => {
    // The catalogue update a clinic receives: the lot that was given is no longer distributed.
    sql.execute(`DELETE l FROM CVCMedicationLotNumber l JOIN CVCMedication m ON m.id=l.cvcMedicationId
      WHERE m.snomedCode=${q(brand)} AND l.lotNumber=${q(second.lot)}`);
    editor = await forms.open(id);
    await lotsLoaded(editor, 2);
    await editor.waitForFunction(() => document.getElementById('cvcLot').style.display === 'none');
    h.assert(await editor.locator('#lot').isVisible() && await editor.locator('#lot').inputValue() === second.lot,
      'The recorded lot is not kept in the lot field');
    h.assert(await editor.locator('#expiryDate').inputValue() === second.expiry, 'The recorded expiry date was cleared or changed');
    const previous = id;
    id = await forms.saveAmendment(editor, previous);
    const ext = forms.exts(id);
    h.assert(ext.lot === second.lot && ext.expiryDate === second.expiry && ext.brandSnomedId === brand,
      `Saving stored lot ${ext.lot}, expiry ${ext.expiryDate}, brand ${ext.brandSnomedId}`);
  });
}

if (require.main === module) runWorkflow('prevention-cvc-lot-expiry', workflow);
module.exports = { workflow };
