#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * rx-prescription-lifecycle — one drug through the Rx module the way a prescriber handles it:
 * allergy warning, write, print, change the dose, stop.
 *
 * User path: Schedule ▸ Search ▸ Master Record ▸ Prescriptions (rx/choosePatient) ▸ drug search ▸
 *   0. the patient's allergy list (rx/showAllergy) ▸ search the allergen ▸ pick it from the results
 *      ▸ reaction ▸ Add Allergy;
 *   1. stage the drug to be prescribed, which the patient is now allergic to: the allergy alert
 *      names the allergen and the reaction; the card's remove link unstages it;
 *   2. stage it again ▸ Instructions (rx/UpdateScript parses them) ▸ Save And Print (a prescriber
 *      may prescribe despite the warning):
 *      the print window (rx/viewScript) opens over the Rx page with the saved script's preview;
 *   3. the print window's Print prints that preview frame (rx/ViewPreview2), not the page around it;
 *   4. Rx again ▸ tick the drug's ReRx box ▸ Stage Medication ▸ new instructions ▸ Save Only:
 *      the change is a new prescription and the old one is archived as re-prescribed;
 *   5. Discon on the new drug with a reason: it is archived with that reason and today's date, and a
 *      chart note carrying the reason is linked to it.
 * Each saved drugs row is asserted (dose, frequency, duration, quantity, script, archive state).
 * Other checks cover the neighbours: allergy recording and the alert's JSON (allergy-rx-alert),
 * Save Only, the indication popup and the drug profile (rx-edit-discontinue), the preview text
 * (rx-drug-name-quotes), and stale re-prescribe saves (rx-stash-patient-isolation).
 *
 * Fixtures: the owned synthetic patient; its allergy is recorded through the allergy picker with a
 * FAKE reaction carrying the run marker; every prescription, drugs, partial_date,
 * DigitalSignature, casemgmt_note (+ links), allergies and allergy partial_date row of the patient
 * is removed in cleanup, which asserts none remain. Needs DrugRef data for the search terms
 * (defaults: atorvastatin / ATORVASTATIN for the allergy, LIPITOR 20MG to prescribe). A picked
 * allergen is used because it carries its ATC code; a free-text one depends on DrugRef resolving
 * the name. Staging also asks DrugRef for the product's market status (rx/searchDrug inactive
 * date), so the drug must be one this DrugRef knows: on the 15 Oct dev DrugRef, amoxil products
 * fail that lookup (503) while LIPITOR 20MG passes.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { masterRecordLink } = require('./lib/master-record-link');

const DRUG_TERM = process.env.RX_LIFECYCLE_DRUG_TERM || 'LIPITOR 20';
const DRUG_NAME = process.env.RX_LIFECYCLE_DRUG_NAME || 'LIPITOR 20MG';
const ALLERGY_SEARCH = process.env.RX_LIFECYCLE_ALLERGY_SEARCH || 'atorvastatin';
const ALLERGEN = process.env.RX_LIFECYCLE_ALLERGEN || 'ATORVASTATIN';
const FIRST_INSTRUCTIONS = '1 tab PO OD x 30 days';
const CHANGED_INSTRUCTIONS = '2 tab PO BID x 30 days';
const TIMEOUT = 30000;
const q = h.sqlString;
const isPost = route => response => response.request().method() === 'POST' && h.pathOnly(response.url()).endsWith(route);
// The allergy page posts to the numbered routes (rx/searchAllergy2, rx/addReaction2).
const isPostTo = route => response => response.request().method() === 'POST' && h.pathOnly(response.url()).includes(route);
const squash = text => String(text).replace(/\s+/g, ' ').trim();

/** Type into the Rx search like a prescriber and stage the first result whose text matches `name` (text or RegExp). */
async function stageFromSearch(rx, term, name) {
  const before = await rx.locator('[id^="drugName_"]').count();
  const [search] = await Promise.all([
    rx.waitForResponse(r => isPost('/rx/searchDrug')(r)
      && new URLSearchParams(r.request().postData() || '').get('query') === term.toUpperCase(), { timeout: 60000 }),
    rx.locator('#searchString').pressSequentially(term, { delay: 60 }),
  ]);
  h.assert(search.ok(), `Drug search answered HTTP ${search.status()}`);
  const options = rx.locator('ul.ui-autocomplete li.ui-menu-item');
  const option = name ? options.filter({ hasText: name }).first() : options.first();
  await option.waitFor({ state: 'visible' });
  const [staged] = await Promise.all([
    rx.waitForResponse(r => isPost('/rx/WriteScript')(r)
      && new URLSearchParams(r.request().postData() || '').get('parameterValue') === 'createNewRx'),
    option.click(),
  ]);
  h.assert(staged.ok(), `Staging the drug answered HTTP ${staged.status()}`);
  await rx.locator('[id^="drugName_"]').nth(before).waitFor({ state: 'attached' });
  const cards = await rx.locator('[id^="drugName_"]').evaluateAll(inputs => inputs.map(input => input.id.slice('drugName_'.length)));
  h.assert(cards.length === before + 1, 'Selecting the search result did not stage exactly one card');
  return cards.find(id => id === new URLSearchParams(staged.request().postData()).get('randomId')) || cards[cards.length - 1];
}

/** Type instructions into a staged card and leave the field so rx/UpdateScript parses them. */
async function enterInstructions(rx, card, text) {
  await rx.locator(`#instructions_${card}`).fill(text);
  const [parsed] = await Promise.all([
    rx.waitForResponse(isPost('/rx/UpdateScript')),
    // Leave for the indication label so parsing does not also trigger Qty/price updates.
    rx.locator(`label[for="jsonDxSearch_${card}"]`).click(),
  ]);
  h.assert(parsed.status() === 200, `rx/UpdateScript answered HTTP ${parsed.status()}`);
  return parsed.json();
}

/** The print window is an iframe over the Rx page; poll Playwright's frame list for it. */
async function viewScriptFrame(page, timeout = 60000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const frame = page.frames().find(f => /\/rx\/viewScript\?/.test(f.url()));
    if (frame) return frame;
    await page.waitForTimeout(250);
  }
  throw new Error('The print window (rx/viewScript) did not open');
}

/** The preview nested in the print window, once its text shows every expected string. */
async function previewText(page, expected, timeout = TIMEOUT) {
  const deadline = Date.now() + timeout;
  let last = null;
  while (Date.now() < deadline) {
    const preview = page.frames().find(f => /\/rx\/ViewPreview2\?/.test(f.url()));
    if (preview) {
      last = squash(await preview.locator('body').innerText().catch(() => ''));
      if (expected.every(text => last.includes(text))) return last;
    }
    await page.waitForTimeout(250);
  }
  h.assert(last !== null, 'The print window has no rendered preview frame (rx/ViewPreview2)');
  return last;
}

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const reaction = `${marker} FAKE hives`;
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
      ${drugIds.length ? `DELETE FROM partial_date WHERE table_name=2 AND table_id IN (${drugIds.join(',')});` : ''}
      DELETE FROM drugs WHERE demographic_no=${patient}; DELETE FROM prescription WHERE demographic_no=${patient};
      DELETE FROM DigitalSignature WHERE demographicId=${patient} AND ModuleType='PRESCRIPTION';
      DELETE FROM partial_date WHERE table_name=1 AND table_id IN (SELECT allergyid FROM allergies WHERE demographic_no=${patient});
      DELETE FROM allergies WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM prescription WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM DigitalSignature WHERE demographicId=${patient})
      + (SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})`) === '0', 'Owned Rx rows were not removed');
  });
  const drugCount = () => sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`);

  const openRx = async label => {
    const rx = await s.popup(s.master, masterRecordLink(s.master, '/rx/choosePatient').first(), label);
    h.assert(new URL(rx.url()).searchParams.get('demographicNo') === patient, 'The Rx module opened for another patient');
    await rx.locator('#searchString').waitFor({ state: 'visible' });
    await rx.waitForLoadState('networkidle');
    return rx;
  };
  await s.step('the allergy list ▸ search ▸ pick the allergen ▸ reaction ▸ Add Allergy records it', async () => {
    const allergies = await s.context.newPage();
    await allergies.goto(h.appUrl(s.config.baseUrl, `/rx/showAllergy?demographicNo=${patient}`));
    await allergies.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
    await h.assertNotErrorPage(allergies, 'allergy list');
    await allergies.locator('#searchString').fill(ALLERGY_SEARCH);
    const [search] = await Promise.all([
      allergies.waitForResponse(isPostTo('/rx/searchAllergy'), { timeout: 60000 }),
      allergies.locator('#searchStringButton').click(),
    ]);
    h.assert(search.status() < 400, `The allergy search answered HTTP ${search.status()}`);
    const results = allergies.locator('#searchResultsContainer a[data-id]');
    await results.first().waitFor({ state: 'visible', timeout: TIMEOUT });
    const names = (await results.allTextContents()).map(text => text.trim());
    const pick = names.indexOf(ALLERGEN) >= 0 ? names.indexOf(ALLERGEN) : names.findIndex(name => name.toUpperCase().startsWith(ALLERGEN));
    h.assert(pick >= 0, `The allergy search did not offer ${ALLERGEN}`);
    const [picked] = await Promise.all([
      allergies.waitForResponse(isPostTo('/rx/addReaction'), { timeout: TIMEOUT }),
      results.nth(pick).click(),
    ]);
    h.assert(picked.status() < 400, `Picking ${ALLERGEN} answered HTTP ${picked.status()}`);
    await allergies.locator('#RxAddAllergyForm').waitFor({ state: 'visible', timeout: TIMEOUT });
    await allergies.locator('#reactionDescription').fill(reaction);
    const [added] = await Promise.all([
      allergies.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: TIMEOUT }),
      allergies.locator('#RxAddAllergyForm input[value="Add Allergy"]').click(),
    ]);
    h.assert(added && added.ok(), `Add Allergy answered HTTP ${added && added.status()}`);
    await expectValue(sql, `SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient} AND archived=0
      AND DESCRIPTION=${q(ALLERGEN)} AND reaction=${q(reaction)}`, '1', 'Add Allergy did not record the allergy');
    await allergies.close();
  });

  let rx = await openRx('rx-module');

  await s.step('a drug the patient is allergic to shows the allergy alert as it is staged; removing the card stages nothing', async () => {
    const [allergyCheck] = await Promise.all([
      rx.waitForResponse(r => isPost('/rx/showAllergy')(r), { timeout: 60000 }),
      stageFromSearch(rx, DRUG_TERM, DRUG_NAME),
    ]);
    h.assert(allergyCheck.status() < 400, `The allergy check answered HTTP ${allergyCheck.status()}`);
    const payload = await allergyCheck.json();
    const matched = (payload.results || []).filter(item => item.reaction === reaction).length;
    h.assert(matched === 1, `The allergy check did not report this run's ${ALLERGEN} allergy as a match (matches ${matched}, `
      + `results ${(payload.results || []).length}, unchecked ${(payload.unchecked || []).length}, complete ${payload.checkComplete}, `
      + `failed ${payload.checkFailed === true}, disabled ${payload.disabled === true})`);
    const alert = rx.locator("table[id^='alleg_tbl_']").first();
    await alert.waitFor({ state: 'visible', timeout: TIMEOUT });
    await alert.getByText(reaction, { exact: false }).waitFor({ state: 'visible', timeout: TIMEOUT });
    h.assert((await alert.innerText()).toUpperCase().includes(ALLERGEN), `The allergy alert does not name ${ALLERGEN}`);
    const card = rx.locator('fieldset').filter({ has: rx.locator("table[id^='alleg_tbl_']") }).first();
    await card.locator("a[onclick^='removePrescribingDrug']").first().click();
    await card.waitFor({ state: 'detached', timeout: TIMEOUT });
    h.assert(await rx.locator('[id^="drugName_"]').count() === 0, 'Removing the card left a staged drug');
    h.assert(drugCount() === '0', 'Staging and removing the drug stored a prescription');
  });

  let first;
  await s.step('Save And Print stores the drug as instructed and opens the print window on its preview', async () => {
    const card = await stageFromSearch(rx, DRUG_TERM, DRUG_NAME);
    const body = await enterInstructions(rx, card, FIRST_INSTRUCTIONS);
    h.assert(String(body.takeMin) === '1' && body.frequency === 'OD' && String(body.duration) === '30',
      `The instructions parsed to ${JSON.stringify(body)}`);
    await rx.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
    await rx.locator('#saveButton').click();
    const script = await viewScriptFrame(rx);
    const scriptNo = new URL(script.url()).searchParams.get('scriptId');
    h.assert(/^[1-9]\d*$/.test(scriptNo), 'Save And Print opened no saved script');
    await expectValue(sql, `SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient} AND script_no=${scriptNo}`, '1',
      'The saved script does not hold exactly the one staged drug');
    const [row] = sql.rows(`SELECT drugid,BN,takemin,takemax,freqcode,duration,durunit,quantity,archived,provider_no
      FROM drugs WHERE demographic_no=${patient} AND script_no=${scriptNo}`);
    const [id, brand, takemin, takemax, freq, duration, unit, quantity, archived, prescriber] = row;
    first = id;
    h.assert(brand === DRUG_NAME, `The saved drug is "${brand}", not ${DRUG_NAME}`);
    h.assert(Number(takemin) === 1 && Number(takemax) === 1 && freq === 'OD' && duration === '30' && unit === 'D',
      `The saved dose/frequency/duration is ${takemin}-${takemax} ${freq} x ${duration}${unit}`);
    h.assert(quantity === '30' && archived === '0' && prescriber === provider,
      `The saved drug has quantity ${quantity}, archived=${archived}, prescriber ${prescriber}`);
    await script.locator('#preview').waitFor({ state: 'attached', timeout: TIMEOUT });
    const shown = await previewText(rx, [DRUG_NAME, FIRST_INSTRUCTIONS]);
    h.assert(shown.includes(DRUG_NAME) && shown.includes(FIRST_INSTRUCTIONS),
      'The print preview does not show the saved drug and its instructions');
  });

  await s.step('Print prints the preview frame', async () => {
    const script = await viewScriptFrame(rx);
    const preview = rx.frames().find(f => /\/rx\/ViewPreview2\?/.test(f.url()));
    h.assert(preview, 'The print window has no preview frame');
    // Record the print instead of opening the browser's print dialog, which headless Chromium cannot show.
    await preview.evaluate(() => { window.print = () => { window.__printedByCheck = (window.__printedByCheck || 0) + 1; }; });
    const printButton = script.locator('#printButton');
    h.assert(await printButton.isEnabled(), 'Print is disabled for a saved script with a preview');
    await printButton.click();
    await preview.waitForFunction(() => window.__printedByCheck === 1, null, { timeout: TIMEOUT });
    // printIframe() closes the Rx window once focus returns to it; reopen it for the next steps.
    if (!rx.isClosed()) await rx.close();
  });

  let second;
  await s.step('ReRx with new instructions stores a new prescription and archives the old one as re-prescribed', async () => {
    rx = await openRx('rx-module-rerx');
    await rx.locator(`#reRxCheckBox_${first}`).check();
    await rx.locator('#reRxConfirmBox input[name="stage"]').click();
    const restaged = rx.locator(`fieldset[data-drug-ref-id="${first}"]`);
    await restaged.waitFor({ state: 'visible', timeout: TIMEOUT });
    const card = (await restaged.locator('[id^="instructions_"]').first().getAttribute('id')).slice('instructions_'.length);
    const body = await enterInstructions(rx, card, CHANGED_INSTRUCTIONS);
    h.assert(String(body.takeMin) === '2' && body.frequency === 'BID' && String(body.duration) === '30',
      `The changed instructions parsed to ${JSON.stringify(body)}`);
    const [saved] = await Promise.all([
      rx.waitForResponse(r => isPost('/rx/WriteScript')(r) && new URL(r.url()).searchParams.get('parameterValue') === 'updateSaveAllDrugs'),
      rx.locator('#saveOnlyButton').click(),
    ]);
    h.assert(saved.status() === 200, `Save Only answered HTTP ${saved.status()}`);
    await expectValue(sql, `SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`, '2',
      'Re-prescribing did not store exactly one new drug');
    const [row] = sql.rows(`SELECT drugid,BN,takemin,takemax,freqcode,duration,quantity,archived
      FROM drugs WHERE demographic_no=${patient} AND drugid<>${first}`);
    const [id, brand, takemin, takemax, freq, duration, quantity, archived] = row;
    second = id;
    h.assert(brand === DRUG_NAME && Number(takemin) === 2 && Number(takemax) === 2 && freq === 'BID' && duration === '30'
      && quantity === '120' && archived === '0',
    `The re-prescribed drug is ${brand} ${takemin}-${takemax} ${freq} x ${duration}, quantity ${quantity}, archived=${archived}`);
    await expectValue(sql, `SELECT CONCAT(archived,' ',COALESCE(archived_reason,'')) FROM drugs WHERE drugid=${first}`,
      '1 represcribed', 'The original drug was not archived as re-prescribed');
    h.assert(sql.value(`SELECT COUNT(*) FROM prescription WHERE demographic_no=${patient}`) === '2',
      'The change was not stored as its own prescription');
    await rx.locator(`#prescrip_${second}`).waitFor({ state: 'visible', timeout: TIMEOUT });
  });

  await s.step('Discon with a reason archives the changed drug and files a linked chart note', async () => {
    const comment = `${marker} stopped after the change`;
    await rx.locator(`#discont_${second}`).click();
    const panel = rx.locator('#discontinueUI');
    await panel.waitFor({ state: 'visible' });
    h.assert((await rx.locator('#disDrug').innerText()).includes(DRUG_NAME), 'The discontinue panel names another drug');
    await rx.locator('#disReason').selectOption('adverseReaction');
    await rx.locator('#disComment').fill(comment);
    const [response] = await Promise.all([
      rx.waitForResponse(r => isPost('/rx/deleteRx')(r) && new URL(r.url()).searchParams.get('parameterValue') === 'Discontinue'),
      panel.locator('input[onclick*="Discontinue2("]').click(),
    ]);
    h.assert(response.status() === 200, `Discontinue answered HTTP ${response.status()}`);
    await panel.waitFor({ state: 'hidden' });
    await expectValue(sql, `SELECT COUNT(*) FROM drugs WHERE drugid=${second} AND archived=1
      AND archived_reason='adverseReaction' AND DATE(archived_date)=CURDATE()`, '1',
    'The changed drug was not archived with the chosen reason and today\'s date');
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note n JOIN casemgmt_note_link l ON l.note_id=n.note_id AND l.table_name=2
      AND l.table_id=${second} WHERE n.demographic_no=${patient} AND n.note LIKE ${q(`%Discontinued reason: adverseReaction%${comment}%`)}`) === '1',
    'No chart note carrying the discontinue reason and comment is linked to the drug');
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient} AND archived=0`) === '0',
      'A drug of the patient is still active after the change and the stop');
  });
}

if (require.main === module) runWorkflow('rx-prescription-lifecycle', workflow, { openPatient: true });
module.exports = { workflow };
