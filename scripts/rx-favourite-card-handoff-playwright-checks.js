#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// rx-favourite-card-handoff: choosing a saved Rx favourite stages a card that carries the favourite's
// own clinical values (#4996).
// User path: Schedule ▸ Search ▸ Master Record ▸ Prescriptions ▸ the favourite's link in the left
// sidebar (SideLinksEditFavorites2.jsp useFav2 ▸ POST rx/useFavorite parameterValue=useFav2) ▸ the
// staged card ▸ Save Only. Two owned favourites:
//   - multiline directions (two lines, the second with an apostrophe) with PRN and No Subs: the card
//     used a single-line input, whose value drops the line break, so "daily." and "O'Neil" ran
//     together; and the card re-parsed the directions while it was being inserted, which cleared
//     the saved PRN flag because the text does not say "prn";
//   - single-line directions with PRN, the ordinary path, as the control.
// Each card must show the saved directions exactly, PRN on, the saved quantity, repeats and No Subs,
// staging must not re-parse the directions, and Save Only must store the same values.
// Fixtures: the owned FAKE- patient and two marker-named favourites of the test provider (custom
// drugs, no DrugRef data). Cleanup removes the favourites and the patient's drugs, prescriptions and
// signature rows, and asserts none remain.
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { openRx, saveOnly } = require('./rx-stash-patient-isolation-playwright-checks');

const MULTILINE = "Take 1 tab PO daily.\nO'Neil synthetic reminder: with food";
const SINGLE = 'Take 1 tab PO daily for synthetic pain';

function insertFavourite(sql, provider, name, special) {
  return h.insertId(sql, `INSERT INTO favorites (provider_no, favoritename, customName, GCN_SEQNO, takemin,
    takemax, freqcode, duration, durunit, quantity, \`repeat\`, nosubs, prn, special, dispenseInternal)
    VALUES (${h.sqlString(provider)}, ${h.sqlString(name)}, ${h.sqlString(name)}, 0, 1, 1, 'OD', '30', 'D',
      '30', 2, 1, 1, ${h.sqlString(special)}, 0)`, 'favourite');
}

async function stageFavourite(rx, favourite) {
  const before = await rx.locator("[id^='drugName_']").count();
  const parses = [];
  const watch = request => {
    if (request.method() === 'POST' && h.pathOnly(request.url()).endsWith('/rx/UpdateScript')
      && /action=parseInstructions/.test(request.postData() || '')) parses.push(request.url());
  };
  rx.on('request', watch);
  try {
    const [staged] = await Promise.all([
      rx.waitForResponse(r => r.request().method() === 'POST' && h.pathOnly(r.url()).endsWith('/rx/useFavorite')),
      rx.locator(`a[onclick="useFav2('${favourite}');"]`).click(),
    ]);
    h.assert(staged.status() === 200, `rx/useFavorite answered HTTP ${staged.status()}`);
    await rx.waitForFunction(count => document.querySelectorAll("[id^='drugName_']").length > count, before);
    await rx.waitForLoadState('networkidle').catch(() => {});
  } finally {
    rx.off('request', watch);
  }
  const ids = await rx.locator("[id^='drugName_']").evaluateAll(els => els.map(el => el.id.split('_')[1]));
  return { card: ids[ids.length - 1], parses };
}

async function cardValues(rx, card) {
  return rx.evaluate(key => ({
    directions: document.getElementById(`instructions_${key}`).value,
    tag: document.getElementById(`instructions_${key}`).tagName,
    prn: document.getElementById(`prnVal_${key}`).value,
    prnLabel: document.getElementById(`prn_${key}`).textContent.trim(),
    quantity: document.getElementById(`quantity_${key}`).value,
    repeats: document.getElementById(`repeats_${key}`).value,
    nosubs: document.getElementById(`nosubs_${key}`).checked,
  }), card);
}

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  s.cleanup(() => {
    sql.execute(`DELETE FROM favorites WHERE provider_no=${h.sqlString(provider)} AND favoritename LIKE ${h.sqlString(`${marker}%`)};
      DELETE FROM drugReason WHERE demographicNo=${patient};
      DELETE FROM drugs WHERE demographic_no=${patient}; DELETE FROM prescription WHERE demographic_no=${patient};
      DELETE FROM DigitalSignature WHERE demographicId=${patient} AND ModuleType='PRESCRIPTION'`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM favorites WHERE favoritename LIKE ${h.sqlString(`${marker}%`)})
      + (SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM prescription WHERE demographic_no=${patient})`) === '0', 'Owned favourite/Rx rows were not removed');
  });
  const multiName = `${marker} MULTI`;
  const singleName = `${marker} SINGLE`;
  const multi = insertFavourite(sql, provider, multiName, MULTILINE);
  const single = insertFavourite(sql, provider, singleName, SINGLE);
  const rx = await openRx(s, patient);
  let multiCard;
  let singleCard;

  await s.step('multiline favourite directions retain their line break on the prescribing card', async () => {
    const staged = await stageFavourite(rx, multi);
    multiCard = staged.card;
    const shown = await cardValues(rx, multiCard);
    h.assert(shown.directions === MULTILINE,
      `The card shows the directions as ${JSON.stringify(shown.directions)} (${shown.tag}), not the saved ${JSON.stringify(MULTILINE)}`);
    h.assert(staged.parses.length === 0, `Staging the favourite re-parsed its directions ${staged.parses.length} time(s)`);
  });

  await s.step('the edited favourite PRN flag survives staging on the prescribing card', async () => {
    const shown = await cardValues(rx, multiCard);
    h.assert(shown.prn === 'true' && shown.prnLabel === 'prn', `The staged card has PRN ${shown.prn} (${JSON.stringify(shown.prnLabel)})`);
    h.assert(shown.quantity === '30' && shown.repeats === '2' && shown.nosubs === true,
      `The staged card has quantity ${shown.quantity}, repeats ${shown.repeats}, No Subs ${shown.nosubs}`);
    const staged = await stageFavourite(rx, single);
    singleCard = staged.card;
    const control = await cardValues(rx, singleCard);
    h.assert(control.directions === SINGLE && control.prn === 'true' && control.repeats === '2' && control.nosubs === true,
      `The single-line favourite staged as ${JSON.stringify(control)}`);
    h.assert(staged.parses.length === 0, 'Staging the single-line favourite re-parsed its directions');
  });

  await s.step('Save Only stores the favourites\' directions, PRN, quantity, repeats and No Subs', async () => {
    await saveOnly(rx);
    await expectValue(sql, `SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient} AND archived=0`, '2',
      'Save Only did not store both favourite cards');
    const row = name => sql.rows(`SELECT prn, nosubs, \`repeat\`, quantity,
        INSTR(special, ${h.sqlString(name === multiName ? "daily.\nO'Neil" : SINGLE)}) > 0,
        INSTR(special, 'daily.O''Neil') > 0
      FROM drugs WHERE demographic_no=${patient} AND customName=${h.sqlString(name)}`)[0] || [];
    for (const name of [multiName, singleName]) {
      const [prn, nosubs, repeat, quantity, keptDirections, joined] = row(name);
      h.assert(prn === '1' && nosubs === '1' && repeat === '2' && quantity === '30',
        `${name} saved prn=${prn}, nosubs=${nosubs}, repeat=${repeat}, quantity=${quantity}`);
      h.assert(keptDirections === '1' && joined === '0', `${name} saved directions that differ from the favourite`);
    }
  });
}

if (require.main === module) runWorkflow('rx-favourite-card-handoff', workflow, { openPatient: true });
module.exports = { workflow };
