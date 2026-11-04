#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Issue #4410 item 1: an amendment of an allergy that is no longer active is refused, and the
 * clinician keeps their entries and is told to review the current list.
 *
 * User path: Schedule > Search > Master Record > E-Chart > Allergies > the allergy's modify (pencil)
 * link > change the severity and reaction > Add Allergy. An amendment adds a replacement and archives
 * the original (RxAddAllergy2Action, allergyToArchive).
 *
 * Asserted, in one login with two allergy windows open on the same patient:
 *   1. Window B opens the amendment form for the allergy and types a change. Window A amends the same
 *      allergy first (control: the original is archived, one active allergy remains).
 *   2. Window B saves its now-stale form. The save is answered HTTP 409 by /rx/addAllergy2 itself;
 *      the dialogue stays open with the typed reaction and severity still in it and a role="alert"
 *      saying the allergy was changed in another window and to review the current allergies; the
 *      chart still has exactly one active version, A's, and B's reaction text was stored nowhere.
 *   3. Race: both windows open the amendment form for a second allergy and save at the same moment.
 *      Exactly one amendment lands (one active version, the original archived once) and the other
 *      is refused with 409: the conditional archive is the arbiter, so the two cannot both add.
 * Before the fix step 2 stored a second active allergy beside A's and step 3 usually stored two.
 *
 * Fixtures: two owned allergies seeded by SQL (marker description) for the owned FAKE- patient;
 * cleanup deletes every allergies row of that patient and asserts none remain.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { failureMark, consumeExpectedFailure } = require('./lib/concurrency-support');

const SAVE_PATH = /\/rx\/addAllergy2?$/;
const STALE_ALERT = /this allergy was changed or inactivated in another window/;

function seedAllergy(s, description) {
  const { sql, patient, provider } = s;
  const id = sql.value(`INSERT INTO allergies (demographic_no,entry_date,DESCRIPTION,TYPECODE,reaction,archived,start_date,age_of_onset,
      severity_of_reaction,onset_of_reaction,life_stage,position,lastUpdateDate,providerNo,nonDrug)
    VALUES (${patient},CURDATE(),${h.sqlString(description)},0,${h.sqlString(description)},0,NULL,0,'2','1','A',0,NOW(),${h.sqlString(provider)},1);
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(id), 'The allergy fixture was not created');
  return id;
}

/** Opens the amendment form for allergy `id` and types a change, without saving. */
async function openAmendment(page, id, severity, reaction) {
  const form = page.locator('#RxAddAllergyForm');
  await page.locator(`#allergy_${id} a.modifyAllergyLink`).click();
  await form.waitFor({ state: 'visible' });
  h.assert(await form.locator('#allergyToArchive').inputValue() === id, 'The amendment form lost the original allergy');
  await form.locator('[name="severityOfReaction"]').selectOption(severity);
  await form.locator('#reactionDescription').fill(reaction);
  return form;
}

function waitForSave(page) {
  return page.waitForResponse(r => r.request().method() === 'POST' && SAVE_PATH.test(new URL(r.url()).pathname), { timeout: 30000 });
}

async function listWindow(s, chart, label) {
  const page = await s.popup(chart, chart.locator('a[onclick*="showAllergy"]').first(), label);
  await page.locator('#searchString').waitFor({ state: 'visible' });
  return page;
}

/** A second window of the same login on the same allergy list. */
async function secondListWindow(s, first) {
  const page = await s.context.newPage();
  await page.goto(first.url(), { waitUntil: 'domcontentloaded' });
  await page.locator('#searchString').waitFor({ state: 'visible' });
  return page;
}

async function workflow(s) {
  const { sql, patient, marker } = s;
  s.cleanup(() => {
    sql.execute(`DELETE FROM allergies WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient}`) === '0', 'Owned allergy rows were not removed');
  });
  const first = seedAllergy(s, `${marker}-ONE`);
  const second = seedAllergy(s, `${marker}-TWO`);
  const active = (suffix) => `SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient} AND archived=0 AND DESCRIPTION=${h.sqlString(`${marker}-${suffix}`)}`;

  const chart = await s.chart();
  const a = await listWindow(s, chart, 'allergy-a');
  const b = await secondListWindow(s, a);
  for (const page of [a, b]) await page.locator(`#allergy_${first}`).waitFor({ state: 'visible' });

  let staleForm;
  const staleReaction = `${marker}-stale-entry`;
  await s.step('window B types an amendment while window A amends the same allergy first', async () => {
    staleForm = await openAmendment(b, first, '3', staleReaction);
    const form = await openAmendment(a, first, '1', `${marker}-current`);
    await Promise.all([waitForSave(a), form.locator('input[type="submit"]').click()]);
    await expectValue(sql, `SELECT archived FROM allergies WHERE allergyid=${first}`, '1', 'Window A\'s amendment did not archive the original');
    await expectValue(sql, active('ONE'), '1', 'Window A\'s amendment did not leave exactly one active allergy');
  });

  await s.step('window B\'s stale amendment is refused with 409, keeps its entries and points at the current list', async () => {
    const mark = failureMark(s.recorder);
    const [response] = await Promise.all([waitForSave(b), staleForm.locator('input[type="submit"]').click()]);
    h.assert(response.status() === 409, `The stale amendment answered HTTP ${response.status()}, not 409`);
    const alert = staleForm.locator('.allergySaveStatus[role="alert"]');
    await alert.waitFor({ state: 'visible' });
    const text = await alert.textContent();
    h.assert(STALE_ALERT.test(text) && /review the patient’s current allergies/.test(text),
      `The dialogue did not explain the stale amendment: ${JSON.stringify(text)}`);
    h.assert(/\/rx\/showAllergy/.test(b.url()) && await staleForm.isVisible(), 'The refused save left the allergy dialogue');
    h.assert(await staleForm.locator('#reactionDescription').inputValue() === staleReaction, 'The refused save lost the typed reaction');
    h.assert(await staleForm.locator('[name="severityOfReaction"]').inputValue() === '3', 'The refused save lost the chosen severity');
    h.assert(await staleForm.locator('input[type="submit"]').isEnabled(), 'Add Allergy stayed disabled after the refusal');
    await b.waitForTimeout(500);
    consumeExpectedFailure(s.recorder, mark, { status: 409, path: SAVE_PATH });
    h.assert(sql.value(active('ONE')) === '1', 'The stale amendment added a second active version of the allergy');
    h.assert(sql.value(`SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient} AND reaction=${h.sqlString(staleReaction)}`) === '0',
      'The refused amendment was stored');
  });

  await s.step('two windows saving an amendment of the same allergy at once store exactly one', async () => {
    for (const page of [a, b]) {
      await page.goto(a.url(), { waitUntil: 'domcontentloaded' });
      await page.locator(`#allergy_${second}`).waitFor({ state: 'visible' });
    }
    const formA = await openAmendment(a, second, '1', `${marker}-race-a`);
    const formB = await openAmendment(b, second, '3', `${marker}-race-b`);
    const mark = failureMark(s.recorder);
    const responses = await Promise.all([
      waitForSave(a), waitForSave(b),
      formA.locator('input[type="submit"]').click(), formB.locator('input[type="submit"]').click(),
    ]);
    const statuses = responses.slice(0, 2).map(r => r.status()).sort();
    console.log(`    (race statuses: ${statuses.join(', ')})`);
    h.assert(statuses.filter(code => code === 409).length === 1,
      `Expected one save to land and one to be refused with 409, got ${statuses.join(', ')}`);
    await a.waitForTimeout(1000);
    consumeExpectedFailure(s.recorder, mark, { status: 409, path: SAVE_PATH });
    h.assert(sql.value(`SELECT archived FROM allergies WHERE allergyid=${second}`) === '1', 'The raced original was not archived');
    h.assert(sql.value(active('TWO')) === '1', `The race left ${sql.value(active('TWO'))} active versions of the allergy`);
    h.assert(sql.value(`SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient} AND DESCRIPTION=${h.sqlString(`${marker}-TWO`)}`) === '2',
      'The race stored more than one replacement');
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('allergy-stale-amend-refused', workflow, { openPatient: true });
