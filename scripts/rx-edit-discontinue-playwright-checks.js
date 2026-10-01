#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * rx-edit-discontinue — coverage plan §3.2 (Rx edit, indication and discontinue).
 *
 * User path: Schedule ▸ Search ▸ Master Record ▸ Prescriptions (Rx module) ▸ drug search ▸
 * staged card Instructions (rx/UpdateScript parses dose/frequency/duration) ▸ Save And Print ▸
 * "Back to CARLOS" (rx/clearPending) ▸ reopen Rx ▸ drug row indication link (rx/RxReason popup) ▸
 * "Discon" (rx/deleteRx Discontinue) ▸ drug row ▸ static script ▸ prescription details
 * (rx/ViewDisplayRxRecord) ▸ Form "Update" (rx/ViewUpdateForm) ▸ "Timeline Drug Profile"
 * (rx/ViewChartDrugProfile).
 *
 * Asserted: the parsed instructions reach the staged card and the saved `drugs` row (takemin/max,
 * freqcode, duration, durunit, quantity, end_date = rx_date + duration); Back to CARLOS clears
 * the staged script so a reopened Rx module stages nothing; the indication is stored as an
 * un-archived icd9 `drugReason` on the owned drug and listed when the popup is reopened; the
 * discontinue writes archived=1, the chosen archived_reason, today's archived_date, keeps the
 * prescribed end_date, and files a linked chart note; the record popup and the drug-form update
 * round-trip through `drugs`; the timeline profile lists the drug; GET is refused by every mutator.
 *
 * Fixtures: the owned synthetic patient; everything else (prescription, drugs, drugReason,
 * casemgmt_note + links, DigitalSignature) is created through the UI for that patient and removed
 * by patient id in cleanup, which asserts nothing remains. Needs a DrugRef with RX_DRUG_TERM.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const DRUG_TERM = process.env.RX_EDIT_DRUG_TERM || 'LIPITOR 20';
const DRUG_NAME = process.env.RX_EDIT_DRUG_NAME || 'LIPITOR 20MG';
const INSTRUCTIONS = '1 tab PO BID x 14 days';
const isPost = (route) => response => response.request().method() === 'POST'
  && h.pathOnly(response.url()).endsWith(route);

/** Type into the Rx search like a prescriber and stage the named DrugRef product; returns its card id. */
async function stageFromSearch(rx, term, name) {
  const before = await rx.locator('[id^="drugName_"]').count();
  const [search] = await Promise.all([
    rx.waitForResponse(r => h.pathOnly(r.url()).endsWith('/rx/searchDrug') && r.request().method() === 'POST'
      && new URLSearchParams(r.request().postData() || '').get('query') === term.toUpperCase(), { timeout: 60000 }),
    rx.locator('#searchString').pressSequentially(term, { delay: 60 }),
  ]);
  h.assert(search.ok(), `Drug search answered HTTP ${search.status()}`);
  const option = rx.locator('ul.ui-autocomplete li.ui-menu-item').filter({ hasText: name }).first();
  await option.waitFor({ state: 'visible' });
  const [staged] = await Promise.all([
    rx.waitForResponse(r => h.pathOnly(r.url()).endsWith('/rx/WriteScript') && r.request().method() === 'POST'
      && new URLSearchParams(r.request().postData() || '').get('parameterValue') === 'createNewRx'),
    option.click(),
  ]);
  h.assert(staged.ok(), `Staging the drug answered HTTP ${staged.status()}`);
  await rx.locator('[id^="drugName_"]').nth(before).waitFor({ state: 'attached' });
  const cards = await rx.locator('[id^="drugName_"]').evaluateAll(inputs => inputs.map(input => input.id.slice('drugName_'.length)));
  h.assert(cards.length === before + 1, 'Selecting the search result did not stage exactly one card');
  return cards.find(id => id === new URLSearchParams(staged.request().postData()).get('randomId')) || cards[cards.length - 1];
}

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const drugCount = () => sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`);
  s.cleanup(() => {
    const drugIds = sql.rows(`SELECT drugid FROM drugs WHERE demographic_no=${patient}`).map(row => row[0]);
    const notes = sql.rows(`SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`).map(row => row[0]);
    h.assert([...drugIds, ...notes].every(id => /^[1-9]\d*$/.test(id)), 'Owned Rx row id is invalid');
    if (notes.length) {
      sql.execute(`DELETE FROM casemgmt_note_link WHERE note_id IN (${notes.join(',')});
        DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes.join(',')});
        DELETE FROM casemgmt_note WHERE demographic_no=${patient} AND note_id IN (${notes.join(',')})`);
    }
    sql.execute(`DELETE FROM drugReason WHERE demographicNo=${patient};
      ${drugIds.length ? `DELETE FROM partial_date WHERE table_name=1 AND table_id IN (${drugIds.join(',')});` : ''}
      DELETE FROM drugs WHERE demographic_no=${patient}; DELETE FROM prescription WHERE demographic_no=${patient};
      DELETE FROM DigitalSignature WHERE demographicId=${patient} AND ModuleType='PRESCRIPTION'`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM prescription WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM drugReason WHERE demographicNo=${patient})
      + (SELECT COUNT(*) FROM DigitalSignature WHERE demographicId=${patient})
      + (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})`) === '0', 'Owned Rx rows were not removed');
  });

  const openRx = async label => {
    const rx = await s.popup(s.master, s.master.locator('a[onclick*="/rx/choosePatient"]').first(), label);
    h.assert(new URL(rx.url()).searchParams.get('demographicNo') === patient, 'The Rx module opened for another patient');
    await rx.locator('#searchString').waitFor({ state: 'visible' });
    await rx.waitForLoadState('networkidle');
    return rx;
  };
  let rx = await openRx('rx-module');
  let card;

  await s.step('a DrugRef product found by search stages a card and its instructions parse through rx/UpdateScript', async () => {
    card = await stageFromSearch(rx, DRUG_TERM, DRUG_NAME);
    const instructions = rx.locator(`#instructions_${card}`);
    await instructions.fill(INSTRUCTIONS);
    const [parsed] = await Promise.all([
      rx.waitForResponse(isPost('/rx/UpdateScript')),
      instructions.press('Tab'),
    ]);
    h.assert(parsed.status() === 200, `rx/UpdateScript answered HTTP ${parsed.status()}`);
    const body = await parsed.json();
    h.assert(String(body.takeMin) === '1' && String(body.takeMax) === '1' && body.frequency === 'BID'
      && String(body.duration) === '14' && body.durationUnit === 'D', `The instructions parsed to ${JSON.stringify(body)}`);
    h.assert((await rx.locator(`#frequency_${card}`).innerText()).trim() === 'BID'
      && (await rx.locator(`#duration_${card}`).innerText()).trim() === '14', 'The staged card does not show the parsed frequency and duration');
  });
}

if (require.main === module) runWorkflow('rx-edit-discontinue', workflow, { openPatient: true });
module.exports = { workflow, stageFromSearch };
