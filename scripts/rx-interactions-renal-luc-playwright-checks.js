#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * rx-interactions-renal-luc — coverage plan §3.2 (Rx decision support on the staged card).
 *
 * User path: Schedule ▸ preferences icon ▸ Prescriptions ▸ "Rx interaction warning level"
 * (provider/rxInteractionWarningLevel) ▸ Search ▸ Master Record ▸ Prescriptions (Rx module) ▸
 * drug search ▸ staged cards: Dosing Information (rx/ViewRenalDosing), Limited Use Codes
 * (rx/ViewLimitedUseCode) ▸ a second, interacting drug.
 *
 * Asserted: the warning-level select auto-saves to the provider's `property` row and reads back
 * on reopen; staging ciprofloxacin renders its renal dosing table from the patient's own seeded
 * weight and serum creatinine, with the computed Clcr and the matching recommendation selected;
 * staging an ODB limited-use product lists exactly the LU codes the shipped formulary extract
 * gives its DIN; staging theophylline next to ciprofloxacin shows the interaction marker at the
 * significance DrugRef's `interactions` table records for that ATC pair.
 *
 * Fixtures: the owned synthetic patient (female, born 1980) with two owned `measurements` rows
 * (WT, SCR), deleted by patient id in cleanup. The provider's rxInteractionWarningLevel
 * `property` row is snapshotted and restored exactly. Nothing is saved: staged cards stay in the
 * session only and cleanup asserts the patient has no drugs. Expected LU codes come from
 * src/main/resources/oscar/oscarRx/data_extract_20250730.xml (override RX_ODB_FILE when the
 * install loads another formulary).
 */
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { stageFromSearch } = require('./rx-edit-discontinue-playwright-checks');

const RENAL = { term: 'CIPRO 500', name: 'CIPRO 500MG', atc: 'J01MA02' };
const INTERACTING = { term: 'UNIPHYL 4', name: 'UNIPHYL 400MG', atc: 'R03DA04' };
const LIMITED_USE = { term: 'PROSCAR', name: 'PROSCAR 5MG', din: '02010909' };
const ODB_FILE = process.env.RX_ODB_FILE
  || path.join(__dirname, '..', 'src/main/resources/oscar/oscarRx/data_extract_20250730.xml');
const SIGNIFICANCE = { 1: 'minor', 2: 'moderate', 3: 'major' };

/** LU reason-for-use ids of the last formulary group listing the DIN (LimitedUseLookup keeps the last). */
function formularyLuCodes(din) {
  const groups = fs.readFileSync(ODB_FILE, 'latin1').split('<pcgGroup').slice(1)
    .map(group => group.slice(0, group.indexOf('</pcgGroup>')))
    .filter(group => group.includes(`<drug id="${din}"`) && group.includes('<lccNote'));
  h.assert(groups.length, `The formulary extract lists no limited-use group for DIN ${din}`);
  return [...groups[groups.length - 1].matchAll(/<lccNote[^>]*reasonForUseId="([^"]+)"/g)].map(match => match[1]);
}

async function workflow(s) {
  const { sql, patient, provider } = s;
  const predicate = `provider_no=${h.sqlString(provider)} AND name='rxInteractionWarningLevel'`;
  const snapshotQuery = `SELECT id,COALESCE(HEX(value),''),value IS NULL FROM property WHERE ${predicate} ORDER BY id`;
  const original = sql.rows(snapshotQuery);
  s.cleanup(() => {
    const statements = [`DELETE FROM property WHERE ${predicate}`];
    for (const [id, hex, isNull] of original) {
      h.assert(/^\d+$/.test(id) && /^[0-9a-f]*$/i.test(hex), 'Invalid warning-level restore snapshot');
      statements.push(`INSERT INTO property(id,provider_no,name,value) VALUES (${id},${h.sqlString(provider)},
        'rxInteractionWarningLevel',${isNull === '1' ? 'NULL' : `UNHEX('${hex}')`})`);
    }
    sql.execute(`START TRANSACTION;${statements.join(';')};COMMIT`);
    h.assert(JSON.stringify(sql.rows(snapshotQuery)) === JSON.stringify(original), 'The warning level was not restored to its snapshot');
    sql.execute(`DELETE FROM measurements WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient})
      + (SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient})`) === '0', 'Owned measurements remain, or a staged drug was saved');
  });
  // Weight and creatinine as the measurement flowsheet records them for this patient only.
  const weight = 50;
  const creatinine = 250;
  sql.execute(`INSERT INTO measurements(type,demographicNo,providerNo,dataField,measuringInstruction,comments,dateObserved,dateEntered)
    VALUES ('WT',${patient},${h.sqlString(provider)},'${weight}','in kg','',NOW(),NOW()),
           ('SCR',${patient},${h.sqlString(provider)},'${creatinine}','umol/L','',NOW(),NOW())`);
  h.assert(sql.value(`SELECT COUNT(*) FROM measurements WHERE demographicNo=${patient}`) === '2', 'The measurement fixtures were not created');
  const [age, sex] = sql.rows(`SELECT TIMESTAMPDIFF(YEAR, STR_TO_DATE(CONCAT(year_of_birth,'-',month_of_birth,'-',date_of_birth),'%Y-%m-%d'), CURDATE()), sex
    FROM demographic WHERE demographic_no=${patient}`)[0];
  const clcr = Math.round((140 - Number(age)) * weight / (creatinine * 0.8) * (sex === 'F' ? 0.85 : 1));

  await s.step('the Rx interaction warning level auto-saves to the provider property and reads back on reopen', async () => {
    const current = sql.value(`SELECT value FROM property WHERE ${predicate} ORDER BY id DESC LIMIT 1`) || '0';
    const chosen = current === '3' ? '2' : '3';
    const openPrefs = async label => {
      const prefs = await s.popup(s.schedule, s.schedule.getByTitle(/Edit your personal setting/i).first(), label);
      await prefs.locator('button[data-bs-target="#secRx"]').click();
      const select = prefs.locator('#rxInteractionWarningLevel');
      await select.waitFor({ state: 'visible' });
      return { prefs, select };
    };
    const { prefs, select } = await openPrefs('preferences');
    h.assert(await select.inputValue() === current, 'The preference page does not show the stored warning level');
    const [saved] = await Promise.all([
      prefs.waitForResponse(r => r.request().method() === 'POST' && h.pathOnly(r.url()).endsWith('/provider/rxInteractionWarningLevel')),
      select.selectOption(chosen),
    ]);
    h.assert(saved.status() === 200 && (await saved.text()).trim() === 'ok', `The warning level save answered HTTP ${saved.status()}`);
    await expectValue(sql, `SELECT CONCAT(COUNT(*),':',MAX(value)) FROM property WHERE ${predicate}`, `1:${chosen}`,
      'The warning level was not stored as exactly one provider property');
    await prefs.close();
    const reopened = await openPrefs('preferences-reopened');
    h.assert(await reopened.select.inputValue() === chosen, 'The reopened preferences do not show the saved warning level');
    await reopened.prefs.close();
  });

  const rx = await s.popup(s.master, s.master.locator('a[onclick*="/rx/choosePatient"]').first(), 'rx-module');
  h.assert(new URL(rx.url()).searchParams.get('demographicNo') === patient, 'The Rx module opened for another patient');
  await rx.locator('#searchString').waitFor({ state: 'visible' });
  await rx.waitForLoadState('networkidle');
  let renalCard;

  await s.step(`staging ciprofloxacin shows renal dosing from the patient's own weight and creatinine (Clcr ${clcr}) with the matching dose selected`, async () => {
    const [dosing] = await Promise.all([
      rx.waitForResponse(r => h.pathOnly(r.url()).endsWith('/rx/ViewRenalDosing') && r.request().method() === 'GET'),
      (async () => { renalCard = await stageFromSearch(rx, RENAL.term, RENAL.name); })(),
    ]);
    const asked = new URL(dosing.url()).searchParams;
    h.assert(dosing.status() === 200 && asked.get('demographicNo') === patient && asked.get('atcCode') === RENAL.atc,
      `Renal dosing was requested for another patient or drug (HTTP ${dosing.status()})`);
    const panel = rx.locator(`#renalDosing_${renalCard}`);
    await panel.locator('table.sofT').waitFor({ state: 'visible' });
    const equation = (await panel.locator('table.equation').innerText()).replace(/\s+/g, ' ');
    h.assert(equation.includes(`Clcr ${clcr} =`) && equation.includes(`${weight}.0`) && equation.includes(`${creatinine}.0 sCr`),
      `The dosing equation does not use the seeded weight and creatinine: ${equation}`);
    const selected = panel.locator('table.sofT tr.selected');
    h.assert(await selected.count() === 1, 'No single dosing recommendation is selected for the computed Clcr');
    const [range] = (await selected.locator('td').first().innerText()).trim().split('-').map(Number);
    h.assert(clcr >= range, 'The selected recommendation does not cover the computed Clcr');
    h.assert((await selected.innerText()).includes('250-500 mg po q18h'), 'The selected recommendation is not the renal-adjusted ciprofloxacin dose');
  });

  await s.step('staging an ODB limited-use product lists exactly the formulary\'s LU codes for its DIN, and a click adds one to the instructions', async () => {
    const expected = formularyLuCodes(LIMITED_USE.din);
    let card;
    const [lookup] = await Promise.all([
      rx.waitForResponse(r => h.pathOnly(r.url()).endsWith('/rx/ViewLimitedUseCode')
        && new URL(r.url()).searchParams.get('din') === LIMITED_USE.din),
      (async () => { card = await stageFromSearch(rx, LIMITED_USE.term, LIMITED_USE.name); })(),
    ]);
    h.assert(lookup.status() === 200, `rx/ViewLimitedUseCode answered HTTP ${lookup.status()}`);
    const table = rx.locator(`#luc_${card} table`);
    await table.waitFor({ state: 'visible' });
    h.assert((await table.locator('th').innerText()).trim() === 'Limited Use Codes', 'The LU block has no heading');
    const shown = (await table.locator('a[onclick*="addLuCode("]').allInnerTexts()).map(text => text.trim()).filter(Boolean);
    h.assert(JSON.stringify(shown) === JSON.stringify(expected), `The LU block lists ${shown.join(',')} instead of ${expected.join(',')}`);
    h.assert(await rx.locator(`#luc_${renalCard} table`).count() === 0, 'A non-LU drug was given limited-use codes');
    const instructions = rx.locator(`#instructions_${card}`);
    const before = await instructions.inputValue();
    await table.locator('a[onclick*="addLuCode("]').filter({ hasText: expected[0] }).first().click();
    h.assert(await instructions.inputValue() === `${before} LU Code: ${expected[0]}`, 'Clicking the LU code did not append it to the instructions');
  });

  await s.step('staging theophylline beside ciprofloxacin shows the interaction at DrugRef\'s significance', async () => {
    const significance = sql.value(`SELECT MAX(significance) FROM drugref2.interactions WHERE
      (affectingatc=${h.sqlString(RENAL.atc)} AND affectedatc=${h.sqlString(INTERACTING.atc)})
      OR (affectingatc=${h.sqlString(INTERACTING.atc)} AND affectedatc=${h.sqlString(RENAL.atc)})`);
    const level = SIGNIFICANCE[significance];
    h.assert(level, 'DrugRef records no graded interaction for the fixture pair');
    const card = await stageFromSearch(rx, INTERACTING.term, INTERACTING.name);
    await rx.waitForLoadState('networkidle');
    const markers = [card, renalCard].map(id => rx.locator(`#${level}_${id}`));
    const visible = await Promise.all(markers.map(marker => marker.isVisible()));
    h.assert(visible.some(Boolean), `No ${level} interaction marker is shown on either staged card for ciprofloxacin + theophylline `
      + `(DrugRef significance ${significance}); the Rx page never requests rx/ViewInteractionDisplay or rx/ViewUpdateInteractingDrugs`);
  });
}

if (require.main === module) runWorkflow('rx-interactions-renal-luc', workflow, { openPatient: true });
module.exports = { workflow };
