#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Chart > Rx > Edit favourites. Existing checks edit a name/unit and retry a
// server error; these exercise dosing/flags, client validation and cancelled Delete.
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');

async function workflow(s) {
  const action = process.env.RX_FAVOURITE_ACTION || 'dose';
  const validation = action === 'validation';
  h.assert(['dose', 'validation', 'multiline'].includes(action), 'RX_FAVOURITE_ACTION must be validation or multiline');
  const ids = [];
  const owned = `provider_no=${h.sqlString(s.provider)} AND favoritename LIKE ${h.sqlString(`${s.marker}%`)}`;
  s.cleanup(() => {
    s.sql.execute(`DELETE FROM favorites WHERE ${owned}; DELETE FROM eChart WHERE demographicNo=${s.patient}`);
    h.assert(s.sql.value(`SELECT COUNT(*) FROM favorites WHERE ${owned}`) === '0', 'Owned favourites remain');
  });
  for (const label of ['target', 'control']) {
    const name = `${s.marker}-${label}`;
    const id = s.sql.value(`INSERT INTO favorites(provider_no,favoritename,customName,GCN_SEQNO,
      takemin,takemax,freqcode,duration,durunit,quantity,\`repeat\`,nosubs,prn,special,custom_instructions,dispenseInternal)
      VALUES(${h.sqlString(s.provider)},${h.sqlString(name)},${h.sqlString(name)},0,1,1,'OD','7','D','7',0,0,0,
        'Original instructions',0,1); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), 'Favourite fixture was not created'); ids.push(id);
  }
  const snapshot = id => JSON.stringify(s.sql.rows(`SELECT * FROM favorites WHERE favoriteid=${id}`));
  const control = snapshot(ids[1]);
  const original = snapshot(ids[0]);
  const chart = await s.chart();
  const rx = await s.popup(chart, chart.locator('#menuTitleRx a').first(), 'rx-favourite-editor');
  await ui.clickAndAwaitReload(rx, rx.locator('a[href*="/rx/ViewEditFavorites2"]').first(), { label: 'Edit favourites' });
  async function index() {
    const input = rx.locator(`input[name^="fldFavoriteId"][value="${ids[0]}"]`);
    await input.waitFor({ state: 'attached' });
    return (await input.getAttribute('name')).replace('fldFavoriteId', '');
  }
  let row = await index();
  const field = prefix => rx.locator(`[name="${prefix}${row}"]`);
  const save = () => rx.locator(`a[onclick="javascript:ajaxUpdateRow(${row});"]`);
  const isSave = response => response.request().method() === 'POST'
    && h.pathOnly(response.url()).endsWith('/rx/updateFavorite2');
  async function commit() {
    const [response] = await Promise.all([rx.waitForResponse(isSave), save().click()]);
    h.assert(response.status() === 204, `Favourite Save Changes answered HTTP ${response.status()}`);
    await rx.locator(`#saveSuccess_${row}`).waitFor({ state: 'visible' });
    h.assert(snapshot(ids[1]) === control, 'Saving the target changed the control favourite');
  }
  if (validation) {
    await s.step('invalid favourite fields show their own alert without posting or changing either row', async () => {
      let writes = 0;
      rx.on('request', request => { if (request.method() === 'POST'
        && h.pathOnly(request.url()).endsWith('/rx/updateFavorite2')) writes++; });
      for (const [prefix, value, message] of [
        ['fldFavoriteName', '', 'Please enter a favorite name.'],
        ['fldTakeMin', 'not-a-dose', 'Incorrect entry in field Take Min.'],
        ['fldTakeMax', '', 'Incorrect entry in field Take Max.'],
        ['fldDuration', 'invalid', 'Incorrect entry in field Duration.'],
        ['fldQuantity', '', 'Incorrect entry in field Quantity.'],
        ['fldRepeat', 'invalid', 'Incorrect entry in field Repeat.'],
      ]) {
        const before = await field(prefix).inputValue();
        await field(prefix).fill(value);
        const dialogs = await h.withExpectedDialogs(rx, () => save().click());
        h.assert(dialogs.length === 1 && dialogs[0].type === 'alert' && dialogs[0].text === message,
          `${prefix} did not show its specific validation alert`);
        h.assert(writes === 0 && snapshot(ids[0]) === original && snapshot(ids[1]) === control,
          'A refused favourite edit was submitted or changed saved data');
        h.assert(!await rx.locator(`#saveSuccess_${row}`).isVisible(), 'A refused edit claimed success');
        await field(prefix).fill(before);
      }
    });
    await s.step('correcting the favourite saves once and cancelled Delete leaves both rows intact', async () => {
      await field('fldSpecial').fill(`${s.marker} corrected directions`);
      await commit();
      const before = snapshot(ids[0]);
      const dialogs = await h.withExpectedDialogs(rx,
        () => Promise.all([rx.waitForEvent('dialog'),
          rx.locator(`a[href="javascript:deleteRow(${row});"]`).click()]), { accept: false });
      h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Delete did not ask for confirmation');
      h.assert(snapshot(ids[0]) === before && snapshot(ids[1]) === control, 'Cancelled Delete changed a favourite');
    });
    await s.step('confirmed Delete removes only the selected favourite from editor and Rx sidebar', async () => {
      await h.withExpectedDialogs(rx, () => ui.clickAndAwaitReload(rx,
        rx.locator(`a[href="javascript:deleteRow(${row});"]`), { label: 'Delete favourite' }));
      h.assert(s.sql.value(`SELECT COUNT(*) FROM favorites WHERE favoriteid=${ids[0]}`) === '0', 'Confirmed Delete retained the favourite');
      h.assert(snapshot(ids[1]) === control, 'Delete changed the control favourite');
      await ui.clickAndAwaitReload(rx, rx.locator('input[value="Back to Search For Drug"]').first(), { label: 'Back to Rx' });
      h.assert(await rx.locator(`a[title="${s.marker}-target"]`).count() === 0, 'The deleted favourite remains in the Rx sidebar');
      h.assert(await rx.locator(`a[title="${s.marker}-control"]`).count() > 0, 'The control favourite disappeared');
    });
    return;
  }
  const name = `${s.marker}-edited`;
  const drug = `${s.marker} custom drug`;
  const instructions = `Take 0.5 to 1 tablet twice daily.${action === 'multiline' ? '\n' : ' '}O'Neil "A&B" directions.`;
  await s.step('Save Changes stores edited dose, frequency, quantity, repeats and clinical flags only on its row', async () => {
    for (const [prefix, value] of Object.entries({fldFavoriteName: name, fldCustomName: drug,
      fldTakeMin: '0.5', fldTakeMax: '1', fldDuration: '2', fldQuantity: '28', fldRepeat: '3', fldSpecial: instructions})) {
      await field(prefix).fill(value);
    }
    await field('fldFrequencyCode').selectOption('BID');
    await field('fldDurationUnit').selectOption('W');
    for (const flag of ['fldNosubs', 'fldPrn', 'customInstr']) await field(flag).check();
    await commit();
    const saved = s.sql.rows(`SELECT favoritename,customName,takemin,takemax,freqcode,duration,durunit,
      quantity,\`repeat\`,nosubs,prn,custom_instructions,dispenseInternal,special FROM favorites WHERE favoriteid=${ids[0]}`)[0];
    h.assert(JSON.stringify(saved.slice(0, 2)) === JSON.stringify([name, drug]) && Number(saved[2]) === 0.5 && Number(saved[3]) === 1,
      'Favourite Save lost its identity, custom drug name or dose');
    h.assert(JSON.stringify(saved.slice(4, 13)) === JSON.stringify(['BID','2','W','28','3','1','1','1','1'])
      && saved[13].replace(/\r\n/g, '\n') === instructions, 'Favourite Save lost dosing, flags or exact directions');
  });
  await s.step('reopening the editor retains all saved values and checked flags', async () => {
    await rx.reload({ waitUntil: 'domcontentloaded' }); row = await index();
    for (const [prefix, value] of Object.entries({fldFavoriteName: name, fldCustomName: drug,
      fldFrequencyCode: 'BID', fldDuration: '2', fldDurationUnit: 'W', fldQuantity: '28', fldRepeat: '3', fldSpecial: instructions})) {
      h.assert(await field(prefix).inputValue() === value, `Reopening lost ${prefix}`);
    }
    h.assert(Number(await field('fldTakeMin').inputValue()) === 0.5 && Number(await field('fldTakeMax').inputValue()) === 1,
      'Reopening lost the edited dose range');
    for (const flag of ['fldNosubs', 'fldPrn', 'customInstr']) h.assert(await field(flag).isChecked(), `Reopening lost ${flag}`);
  });
  let stagedDirections;
  let stagedPrn;
  await s.step('choosing the edited favourite stages its updated medication for this patient', async () => {
    await ui.clickAndAwaitReload(rx, rx.locator('input[value="Back to Search For Drug"]').first(), { label: 'Back to Rx' });
    await rx.locator(`a[title="${name}"]`).first().click();
    await rx.locator('[id^="drugName_"]').first().waitFor({ state: 'attached' });
    await rx.waitForLoadState('networkidle');
    const names = await rx.locator('[id^="drugName_"]').evaluateAll(nodes => nodes.map(node => node.value));
    stagedDirections = await rx.locator('[id^="instructions_"]').evaluateAll(nodes => nodes.map(node => node.value));
    h.assert(JSON.stringify(names) === JSON.stringify([drug]) && stagedDirections.length === 1,
      'Choosing the edited favourite staged stale or additional medication data');
    if (action !== 'multiline') h.assert(stagedDirections[0] === instructions, 'The prescribing card lost the edited directions');
    h.assert(await rx.locator('[id^="quantity_"]').first().inputValue() === '28'
      && await rx.locator('[id^="repeats_"]').first().inputValue() === '3', 'The prescribing card lost edited quantity or repeats');
    h.assert(await rx.locator('[id^="nosubs_"]').first().isChecked(), 'The prescribing card lost No Subs');
    stagedPrn = await rx.locator('[id^="prnVal_"]').first().inputValue();
    h.assert(s.sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${s.patient}`) === '0', 'Staging unexpectedly persisted a prescription');
  });
  if (action !== 'multiline') await s.step('the edited favourite PRN flag survives staging on the prescribing card', async () => {
    h.assert(s.sql.value(`SELECT prn FROM favorites WHERE favoriteid=${ids[0]}`) === '1', 'Staging rewrote the saved favourite PRN flag');
    h.assert(stagedPrn === 'true', `Saved favourite has PRN checked, but the prescribing card PRN value is ${JSON.stringify(stagedPrn)}`);
  });
  if (action === 'multiline') await s.step('multiline favourite directions retain their line break on the prescribing card', async () => {
    h.assert(stagedDirections[0] === instructions, `The prescribing card changed multiline directions: ${JSON.stringify(stagedDirections[0])}`);
  });
}
if (require.main === module) runWorkflow(process.env.RX_FAVOURITE_ACTION === 'validation'
  ? 'rx-favourite-editor-validation' : process.env.RX_FAVOURITE_ACTION === 'multiline'
    ? 'rx-favourite-multiline-directions' : 'rx-favourite-dose-roundtrip', workflow);
module.exports = { workflow };
