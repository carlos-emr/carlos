#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
// ISMP Canada's Do Not Use list bans a trailing zero ("1.0 mg" is read as 10 mg) and a missing
// leading zero (".5 mg" is read as 5 mg) in medication text (#6185).
// Rx record > Dosage; Rx > re-prescribe a dose finer than a thousandth > leave Min and Max >
// Save Only; Rx > custom card > quantity typed with a unit > Save Only; Edit Favorites > Take.
'use strict';
const h = require('./lib/playwright-harness');
const {runWorkflow, expectValue} = require('./lib/workflow-session');
const {prepareRx, saveOnly, stageExisting} = require('./lib/rx-ui-workflow');
const {stageCustomDrug} = require('./rx-stash-patient-isolation-playwright-checks');

const isPost = (response, path) => response.request().method() === 'POST'
  && new URL(response.url()).pathname.endsWith(path);

async function workflow(s) {
  const {sql, patient, provider, marker, config, context} = s, q = h.sqlString, f = await prepareRx(s);
  // A sixteenth and five sixteenths are exact in the float columns and finer than the three
  // decimal places the dose text was rounded to. The directions state no dose, so nothing but
  // the two stored numbers can supply one.
  const fine = f.seed('fine', {takemin: '0.0625', takemax: '0.3125', special: 'Apply as directed'});
  const favorite = h.insertId(sql, `INSERT INTO favorites(provider_no,favoritename,customName,GCN_SEQNO,takemin,takemax,
      freqcode,duration,durunit,quantity,\`repeat\`,nosubs,prn,special,dispenseInternal)
    VALUES(${q(provider)},${q(marker + ' favourite')},${q(marker + ' medication')},0,1,2,'OD','30','D','30',0,0,0,
      '1 tab PO OD',0)`, 'owned favourite');
  s.cleanup(() => sql.execute(`DELETE FROM favorites WHERE favoriteid=${favorite} AND provider_no=${q(provider)}
    AND favoritename=${q(marker + ' favourite')}`));
  const rx = await f.open();

  await s.step('the Rx record shows a dose finer than a thousandth in full', async () => {
    const record = await context.newPage();
    await h.gotoApp(record, config.baseUrl, `/rx/ViewDisplayRxRecord?id=${fine}`);
    const dose = (await record.locator('td.label', {hasText: /^\s*Dosage:\s*$/})
      .locator('xpath=following-sibling::td[1]').innerText()).trim();
    h.assert(dose === '0.0625-0.3125', `The Rx record rounded or reformatted the stored dose: ${JSON.stringify(dose)}`);
    await record.close();
  });

  await s.step('re-prescribing keeps the stored dose when the prescriber only visits Min and Max', async () => {
    const key = await stageExisting(rx, fine);
    await rx.waitForLoadState('networkidle');
    const shown = async field => (await rx.locator(`#${field}_${key}`).textContent()).trim();
    h.assert(await shown('minimum') === '0.0625' && await shown('maximum') === '0.3125',
      `The staging card rounded the stored dose: ${await shown('minimum')} to ${await shown('maximum')}`);
    for (const [field, expected] of [['minimum', '0.0625'], ['maximum', '0.3125']]) {
      // Leaving the field posts its text back as the dose, changed or not.
      const property = response => isPost(response, '/rx/WriteScript')
        && new URL(response.url()).searchParams.get('parameterValue') === 'updateProperty'
        && new URLSearchParams(response.request().postData()).get('elementId') === `${field}_${key}`;
      await rx.locator(`a[onclick="focusTo('${field}_${key}')"]`).click();
      const [posted] = await Promise.all([rx.waitForResponse(property), rx.locator(`#${field}_${key}`).blur()]);
      h.assert(posted.status() === 200, `Leaving ${field} was refused (HTTP ${posted.status()})`);
      // The card prints the number after a blank; the server trims it.
      const sent = new URLSearchParams(posted.request().postData()).get('propertyValue');
      h.assert(sent.trim() === expected, `Leaving ${field} posted ${JSON.stringify(sent)} as the dose`);
    }
    await saveOnly(rx);
    await expectValue(sql, `SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`, '2',
      'Re-prescribing did not save exactly one new medication');
    const saved = sql.rows(`SELECT takemin,takemax FROM drugs WHERE demographic_no=${patient} AND drugid<>${fine}`)[0];
    h.assert(JSON.stringify(saved) === JSON.stringify(['0.0625', '0.3125']),
      `The saved dose is not the stored dose: ${JSON.stringify(saved)}`);
    h.assert(JSON.stringify(sql.rows(`SELECT takemin,takemax FROM drugs WHERE drugid=${fine}`)[0])
      === JSON.stringify(['0.0625', '0.3125']), 'Re-prescribing changed the original medication');
  });

  await s.step('a quantity typed with a decimal amount and a unit is saved whole', async () => {
    const key = await stageCustomDrug(rx, `${marker} liquid`);
    await rx.locator(`#instructions_${key}`).fill('Apply as directed');
    const [parsed] = await Promise.all([
      rx.waitForResponse(response => isPost(response, '/rx/UpdateScript')),
      rx.locator(`label[for="jsonDxSearch_${key}"]`).click(),
    ]);
    h.assert(parsed.status() === 200, 'The card instructions were not parsed');
    await rx.waitForLoadState('networkidle');
    const quantity = rx.locator(`#quantity_${key}`);
    await quantity.fill('2.5 mL');
    const [updated] = await Promise.all([
      rx.waitForResponse(response => isPost(response, '/rx/WriteScript')
        && new URLSearchParams(response.request().postData()).get('action') === 'updateQty'),
      quantity.blur(),
    ]);
    h.assert(updated.status() === 200, `The quantity was refused (HTTP ${updated.status()})`);
    await rx.waitForLoadState('networkidle');
    await saveOnly(rx);
    const id = sql.value(`SELECT drugid FROM drugs WHERE demographic_no=${patient} AND customName=${q(marker + ' liquid')}`);
    h.assert(/^[1-9]\d*$/.test(id), 'The liquid medication was not saved');
    const row = sql.rows(`SELECT quantity,unitName FROM drugs WHERE drugid=${id}`)[0];
    h.assert(JSON.stringify(row) === JSON.stringify(['2.5', 'mL']),
      `The quantity was not saved as the amount and its unit: ${JSON.stringify(row)}`);
    const special = sql.value(`SELECT special FROM drugs WHERE drugid=${id}`);
    h.assert(/(^|\n)Qty:2\.5 mL Repeats:0$/.test(special),
      `The instruction text does not state the quantity as typed: ${JSON.stringify(special)}`);
    h.assert(!/Qty:\d+ \.\d/.test(special), 'The instruction text still splits the decimal quantity');
  });

  await s.step('the favourites editor shows a whole dose without a trailing zero', async () => {
    const editor = await context.newPage();
    // The address of the Rx page's "Edit Favorites" link.
    await h.gotoApp(editor, config.baseUrl, `/rx/ViewEditFavorites2?demographicNo=${patient}`);
    const row = await editor.locator('input[id^="fldFavoriteName"]').evaluateAll((inputs, name) => {
      const match = inputs.find(input => input.value === name);
      return match ? match.id.replace('fldFavoriteName', '') : null;
    }, `${marker} favourite`);
    h.assert(row !== null, 'The owned favourite is not in the favourites editor');
    const take = [await editor.locator(`#fldTakeMin${row}`).inputValue(), await editor.locator(`#fldTakeMax${row}`).inputValue()];
    h.assert(JSON.stringify(take) === JSON.stringify(['1', '2']),
      `The favourites editor did not show the dose as plain numbers: ${JSON.stringify(take)}`);
    await editor.close();
    h.assert(sql.value(`SELECT CONCAT(takemin,'|',takemax) FROM favorites WHERE favoriteid=${favorite}`) === '1|2',
      'Opening the favourites editor changed the favourite');
  });
}
if (require.main === module) runWorkflow('rx-dose-text', workflow);
module.exports = {workflow};
