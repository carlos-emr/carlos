#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// rx-custom-note: the Rx page's Note button (#customNote, SearchDrug3.jsp), which stages a free-text prescription
// card with no drug (rx/WriteScript parameterValue=newCustomNote); Save Only (rx/WriteScript updateSaveAllDrugs)
// stores it as a drugs row with custom_note=1 under its own prescription. rx-stash-patient-isolation and
// rx-card-options stage custom drugs; no check pressed the Note button.
// User path: Schedule ▸ Search ▸ Master Record ▸ Prescriptions ▸ Note (accept its warning) ▸ type the note ▸
// Save Only ▸ the drug profile.
//   1. Note warns first, then stages one card whose quantity and repeats are disabled at 0;
//   2. Save Only stores exactly one note row for the patient (custom_note=1, current, not long-term, no drug code or
//      brand) carrying the typed text, under one prescription that holds only that row;
//   3. the drug profile lists the note's text;
//   4. LAST: the note is stored and listed without a stray "null" before its text (#4873, release fix #4874).
// Fixtures: the owned FAKE- patient only; the note is written through the page. Cleanup removes the patient's drugs,
// prescriptions, drugReason and DigitalSignature rows, and asserts none remain. No clinic-wide state is changed.
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { masterRecordLink } = require('./lib/master-record-link');

const isPost = route => response => response.request().method() === 'POST' && h.pathOnly(response.url()).endsWith(route);
/** Waits for the response the predicate names; a timeout says which request never answered. */
const answered = (page, predicate, what) => page.waitForResponse(predicate, { timeout: 60000 })
  .catch(error => { throw new Error(`${what} never answered: ${error.message.split('\n')[0]}`); });

async function workflow(s) {
  const { sql, patient, marker } = s;
  const text = `${marker} note: bring the old pill bottles`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM drugReason WHERE demographicNo=${patient};
      DELETE FROM drugs WHERE demographic_no=${patient}; DELETE FROM prescription WHERE demographic_no=${patient};
      DELETE FROM DigitalSignature WHERE demographicId=${patient} AND ModuleType='PRESCRIPTION'`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM prescription WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM DigitalSignature WHERE demographicId=${patient})`) === '0', 'Owned Rx rows were not removed');
  });

  // The Master Record's Prescriptions link, by the URL it opens (an onclick on older pages, a .patient-nav href since #4468).
  const rx = await s.popup(s.master, masterRecordLink(s.master, '/rx/choosePatient').first(),
    'rx-module');
  h.assert(new URL(rx.url()).searchParams.get('demographicNo') === patient, 'The Rx module opened for another patient');
  await rx.locator('#searchString').waitFor({ state: 'visible' });
  await rx.waitForLoadState('networkidle');

  let card;
  await s.step('Note warns first, then stages one card with quantity and repeats disabled', async () => {
    const before = await rx.locator("[id^='instructions_']").evaluateAll(els => els.map(el => el.id));
    let staged;
    const dialogs = await h.withExpectedDialogs(rx, async () => {
      [staged] = await Promise.all([
        answered(rx, response => isPost('/rx/WriteScript')(response)
          && new URLSearchParams(response.request().postData() || '').get('parameterValue') === 'newCustomNote',
          'the Note card (rx/WriteScript newCustomNote)'),
        rx.locator('#customNote').click(),
      ]);
    }, { accept: true });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm' && /manually enter a prescription/.test(dialogs[0].text),
      `Note asked ${JSON.stringify(dialogs.map(entry => entry.text))}`);
    h.assert(staged.status() === 200, `rx/WriteScript newCustomNote answered HTTP ${staged.status()}`);
    await rx.waitForFunction(count => document.querySelectorAll("[id^='instructions_']").length > count, before.length,
      { timeout: 20000 });
    const added = (await rx.locator("[id^='instructions_']").evaluateAll(els => els.map(el => el.id)))
      .filter(id => !before.includes(id));
    h.assert(added.length === 1, `Note staged ${added.length} cards`);
    card = added[0].split('_')[1];
    for (const field of ['quantity', 'repeats']) {
      const box = rx.locator(`#${field}_${card}`);
      h.assert(await box.isDisabled() && (await box.inputValue()) === '0', `The note card's ${field} box is not disabled at 0`);
    }
  });

  await s.step('Save Only stores one current note row with the text under its own prescription', async () => {
    await rx.locator(`#instructions_${card}`).fill(text);
    // Leaving the instructions box (here for the indication label) posts rx/UpdateScript to parse them.
    const [parsed] = await Promise.all([
      answered(rx, isPost('/rx/UpdateScript'), 'rx/UpdateScript (parsing the note)'),
      rx.locator(`label[for="jsonDxSearch_${card}"]`).click(),
    ]);
    h.assert(parsed.status() === 200, `rx/UpdateScript answered HTTP ${parsed.status()}`);
    const [saved] = await Promise.all([
      answered(rx, response => isPost('/rx/WriteScript')(response)
        && new URL(response.url()).searchParams.get('parameterValue') === 'updateSaveAllDrugs',
        'Save Only (rx/WriteScript updateSaveAllDrugs)'),
      rx.locator('#saveOnlyButton').click(),
    ]);
    h.assert(saved.status() === 200, `Save Only answered HTTP ${saved.status()}`);
    await expectValue(sql, `SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`, '1', 'Save Only did not store one row');
    const [[customNote, archived, longTerm, gcn, brand, scriptNo, special]] = sql.rows(`SELECT custom_note, archived,
      IFNULL(long_term,0), GCN_SEQNO, IFNULL(BN,''), script_no, special FROM drugs WHERE demographic_no=${patient}`);
    h.assert(customNote === '1' && archived === '0' && longTerm === '0',
      `The note row is custom_note=${customNote}, archived=${archived}, long_term=${longTerm}`);
    h.assert(gcn === '0' && brand === '', `The note row carries drug code ${gcn} and brand ${JSON.stringify(brand)}`);
    h.assert(special.includes(text), `The note row carries ${JSON.stringify(special)}, not the typed text`);
    h.assert(/^[1-9]\d*$/.test(scriptNo), `The note row has no prescription (script_no ${scriptNo})`);
    const prescriptions = sql.rows(`SELECT script_no, (SELECT COUNT(*) FROM drugs d WHERE d.script_no=p.script_no)
      FROM prescription p WHERE demographic_no=${patient}`);
    h.assert(JSON.stringify(prescriptions) === JSON.stringify([[scriptNo, '1']]),
      `The patient's prescriptions are ${JSON.stringify(prescriptions)}, not one holding the note`);
  });

  await s.step('the drug profile lists the note\'s text', async () => {
    const drug = sql.value(`SELECT drugid FROM drugs WHERE demographic_no=${patient}`);
    await rx.reload({ waitUntil: 'domcontentloaded' });
    await rx.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    const entry = rx.locator(`#prescrip_${drug}`);
    await entry.waitFor({ state: 'visible', timeout: 20000 });
    h.assert(await entry.count() === 1, 'The profile does not list the note once');
    h.assert((await entry.innerText()).replace(/\s+/g, ' ').includes(text), 'The profile does not show the note\'s text');
  });

  await s.step('the note is stored and listed without a stray "null" before its text', async () => {
    const [[drug, special]] = sql.rows(`SELECT drugid, special FROM drugs WHERE demographic_no=${patient}`);
    h.assert(!/(^|\n)null(\n|$)/.test(special), `The note is stored as ${JSON.stringify(special)}`);
    const listed = (await rx.locator(`#prescrip_${drug}`).innerText()).replace(/\s+/g, ' ').trim();
    h.assert(listed === text, `The profile lists the note as ${JSON.stringify(listed)}`);
  });
}

if (require.main === module) runWorkflow('rx-custom-note', workflow, { openPatient: true });
module.exports = { workflow };
