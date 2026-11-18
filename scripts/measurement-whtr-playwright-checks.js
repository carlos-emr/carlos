#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Issue #5152 / PR #5153: the measurement-entry popup converts a waist typed in inches to cm on
// double click and calculates the waist-to-height ratio (WHtR) from WAIS and HT.
//
// User path: Schedule ▸ Master Record ▸ E-Chart ▸ Measurements menu ▸ a group holding HT, Waist and
// a WHtR type (popup) ▸ HT 5'10" and Waist 34 double-clicked to cm ▸ a waist with a unit ▸ Submit.
// Asserts both conversions ask first and land in cm, that WHtR follows the converted values at once
// (86.4 / 177.8 = 0.49) without a script error, that a waist the ratio cannot use ("86 cm") clears
// the calculated ratio instead of writing NaN or keeping the old one, and that Submit stores HT,
// WAIS and WHtR rows for the patient. The node test measurement-whtr-calculation.test.js covers the
// edge cases of the same script without a browser.
//
// CARLOS does not ship a WHtR measurement type: a clinic adds one coded WHtR, which Add Measurement
// Type stores upper-cased as WHTR. The check therefore stages a WHTR type when the database has
// none (and reuses an existing one, in whatever case, otherwise), plus a run-private group, so the
// shared Anthropometrics group is not changed. Fixtures: the owned FAKE-PW patient (runWorkflow);
// the group and its style row are named by the run marker. Cleanup deletes the patient's readings
// and notes, the group, its style row and the staged type, and asserts they are gone.
const h = require('./lib/playwright-harness');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const group = `${marker} WHtR`;
  // Type codes compare case-insensitively (utf8mb4_general_ci), as the application looks them up.
  const typeRow = (code) => sql.rows(`SELECT type, typeDisplayName FROM measurementType
    WHERE type=? ORDER BY id LIMIT 1`, [code])[0];
  const [, heightDisplay] = typeRow('HT') || [];
  const [, waistDisplay] = typeRow('WAIS') || [];
  h.assert(heightDisplay && waistDisplay, 'The database has no HT or WAIS measurement type to group with WHtR');
  const clinicRatio = typeRow('WHTR');
  const ratioCode = clinicRatio ? clinicRatio[0] : 'WHTR';
  const ratioDisplay = clinicRatio ? clinicRatio[1] : `${marker} WHtR`;
  const staged = !clinicRatio;
  const stagedTypeCount = () => (staged ? sql.value(`SELECT COUNT(*) FROM measurementType
    WHERE BINARY type='WHTR' AND typeDisplayName=?`, [ratioDisplay]) : '0');

  s.cleanup(() => {
    // The patient is the run's own fixture, so every reading and note on it was written by this run.
    const ownedNotes = 'SELECT note_id FROM casemgmt_note WHERE demographic_no=?';
    sql.execute('DELETE FROM measurements WHERE demographicNo=?', [patient]);
    for (const table of ['casemgmt_issue_notes', 'casemgmt_note_ext', 'casemgmt_note_link']) {
      sql.execute(`DELETE FROM ${table} WHERE note_id IN (${ownedNotes})`, [patient]);
    }
    sql.execute('DELETE FROM casemgmt_note WHERE demographic_no=?', [patient]);
    sql.execute('DELETE FROM measurementGroup WHERE name=?', [group]);
    sql.execute('DELETE FROM measurementGroupStyle WHERE groupName=?', [group]);
    if (staged) {
      sql.execute(`DELETE FROM measurementType WHERE BINARY type='WHTR' AND typeDisplayName=?`, [ratioDisplay]);
    }
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM measurements WHERE demographicNo=?)
      + (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=?)
      + (SELECT COUNT(*) FROM measurementGroup WHERE name=?)
      + (SELECT COUNT(*) FROM measurementGroupStyle WHERE groupName=?)`, [patient, patient, group, group]) === '0'
      && stagedTypeCount() === '0', 'Owned readings, notes, group or the staged WHtR type were not removed');
  });
  h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM measurementGroup WHERE name=?)
    + (SELECT COUNT(*) FROM measurementGroupStyle WHERE groupName=?)`, [group, group]) === '0',
  'The per-run measurement group already exists');
  if (staged) {
    sql.execute(`INSERT INTO measurementType (type,typeDisplayName,typeDescription,measuringInstruction,validation,createDate)
      SELECT 'WHTR', ?, 'Waist to height ratio', 'Waist (cm) / height (cm)', id, NOW()
      FROM validations WHERE name='Numeric Value: 0 to 10' ORDER BY id LIMIT 1`, [ratioDisplay]);
    h.assert(stagedTypeCount() === '1', 'The WHtR measurement type was not staged with a numeric validation');
  }
  sql.execute('INSERT INTO measurementGroupStyle (groupName,cssID) VALUES (?,0)', [group]);
  for (const display of [heightDisplay, waistDisplay, ratioDisplay]) {
    sql.execute('INSERT INTO measurementGroup (name,typeDisplayName) VALUES (?,?)', [group, display]);
  }

  const valueInput = (page, type) => page.locator(`[id="row-${type}"] td:nth-child(3) input`);
  let entry;
  await s.step('the chart offers the group and its popup wires the waist conversion and WHtR', async () => {
    const chart = await s.chart();
    const link = chart.locator('#leftNavBar .menu a, #rightNavBar .menu a').filter({ hasText: group });
    h.assert(await link.count() === 1, `The chart measurement menu does not list ${group}`);
    await revealAuditLink(chart, link, 20000);
    entry = await s.popup(chart, link, 'whtr-entry');
    await entry.waitForLoadState('load');
    await h.assertNotErrorPage(entry, 'whtr-entry');
    h.assert(new URL(entry.url()).searchParams.get('demographicNo') === patient, 'The group opened for another patient');
    for (const type of ['HT', 'WAIS', ratioCode]) {
      await valueInput(entry, type).waitFor({ state: 'visible' });
    }
    h.assert(await valueInput(entry, 'WAIS').getAttribute('title') === 'Double click to convert inches to cm',
      'The waist value is not offered for inch to cm conversion');
  });

  await s.step('double clicks convert height and waist to cm and WHtR follows at once', async () => {
    const height = valueInput(entry, 'HT');
    const waist = valueInput(entry, 'WAIS');
    const dialogs = await h.withExpectedDialogs(entry, async () => {
      await height.fill('5\'10"');
      await height.dblclick();
      await waist.fill('34');
      await waist.dblclick();
    });
    h.assert(dialogs.length === 2 && /feet/.test(dialogs[0].text) && /34 inch\(es\) to 86\.4cm/.test(dialogs[1].text),
      `The conversions did not each ask once: ${JSON.stringify(dialogs.map((dialog) => dialog.text))}`);
    h.assert(await height.inputValue() === '177.8', `The height was not converted to cm: ${await height.inputValue()}`);
    h.assert(await waist.inputValue() === '86.4', `The waist was not converted to cm: ${await waist.inputValue()}`);
    h.assert(await valueInput(entry, ratioCode).inputValue() === '0.49',
      `WHtR did not follow the converted values: ${await valueInput(entry, ratioCode).inputValue()}`);
  });

  await s.step('a waist the ratio cannot use clears the calculated WHtR rather than writing NaN', async () => {
    const waist = valueInput(entry, 'WAIS');
    const ratio = valueInput(entry, ratioCode);
    await waist.fill('86 cm');
    await waist.blur();
    h.assert(await ratio.inputValue() === '', `A waist with a unit left WHtR at ${await ratio.inputValue()}`);
    await waist.fill('86.4');
    await waist.blur();
    h.assert(await ratio.inputValue() === '0.49', `WHtR was not recalculated: ${await ratio.inputValue()}`);
  });

  await s.step('a manually entered ratio survives missing inputs and a later valid waist recalculates it', async () => {
    const waist = valueInput(entry, 'WAIS');
    const ratio = valueInput(entry, ratioCode);
    await ratio.fill('0.50');
    await waist.fill('');
    await waist.blur();
    h.assert(await ratio.inputValue() === '0.50', 'Clearing the waist erased a manually entered WHtR');
    await waist.fill('86.4');
    await waist.blur();
    h.assert(await ratio.inputValue() === '0.49', 'A valid waist did not replace the manual ratio with its calculation');
  });

  await s.step('Submit stores the height, the waist and their ratio for the patient', async () => {
    // The observation date the form submits (a UTC calendar date), not the database's CURDATE(),
    // which differs from it for part of every day in Canadian time zones.
    const observed = await entry.locator('[id="row-HT"] input[name^="date-"]').inputValue();
    h.assert(/^\d{4}-\d{2}-\d{2}$/.test(observed), `The form has no observation date: ${observed}`);
    const closed = entry.waitForEvent('close', { timeout: 20000 });
    await entry.getByRole('button', { name: 'Submit', exact: true }).click();
    await closed;
    const reading = `SELECT COUNT(*) FROM measurements WHERE demographicNo=? AND type=? AND dataField=?
      AND providerNo=? AND DATE(dateObserved)=?`;
    const stored = (type, value) => [patient, type, value, provider, observed];
    await expectValue(sql, reading, '1', 'The calculated WHtR was not stored', stored(ratioCode, '0.49'));
    h.assert(sql.value(reading, stored('HT', '177.8')) === '1', 'The converted height was not stored');
    h.assert(sql.value(reading, stored('WAIS', '86.4')) === '1', 'The converted waist was not stored');
    h.assert(sql.value('SELECT COUNT(*) FROM measurements WHERE demographicNo=?', [patient]) === '3',
      'The group saved readings other than HT, WAIS and WHtR');
  });
}

if (require.main === module) runWorkflow('measurement-whtr', workflow);
module.exports = { workflow };
