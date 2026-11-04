#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Issue #4576 (app-findings-log.md finding 215): the Penicillin and Sulfa allergy shortcuts record a
 * drug CLASS, named by its ATC code, and a prescription in that class warns.
 *
 * The shortcuts used to post the fixed DrugRef ids 44452 and 44159 as an AHFS class. In the packaged
 * DrugRef those ids are a multivitamin brand and a fluoxetine product, so the allergy was filed under
 * another product, and the AHFS class name the check relied on can vanish from a DPD extract. The
 * clinical lead's requirement on the issue: penicillins are ATC J01C and Amoxil (J01CA04) must
 * trigger it; sulfonamides are J01E and Septra (J01EE01) must trigger it.
 *
 * User path: Schedule > Search > Master Record > E-Chart > Allergies, then the Rx module for the
 * patient. Asserted:
 *   1. The Penicillin and Sulfa buttons open the reaction form for an ATC class allergen (type 8, the
 *      class code, no DrugRef id, the class shown in the heading, no non-drug selector) and save it as
 *      one: the allergies row has TYPECODE 8, atc J01C or J01E and no drugref_id.
 *   2. Prescribing Amoxil warns on both penicillin allergies (the one just recorded and one the OLD
 *      shortcut recorded, seeded by SQL with its exact identity: drugref_id 44452, TYPECODE 10) and
 *      on no sulfonamide allergy, and reports nothing as "not checked".
 *   3. Prescribing Septra warns on both sulfonamide allergies and on no penicillin one.
 *   4. Products the DPD files OUTSIDE the ATC class but that contain a class member still warn, through
 *      the AHFS class DrugRef is also asked about: HP-PAC (lansoprazole, amoxicillin and clarithromycin,
 *      A02BD07) on both penicillin allergies, BLEPHAMIDE (sulfacetamide and prednisolone eye drops,
 *      S01CA02) on both sulfonamide allergies. An ATC prefix check alone reports both as clear.
 *   5. Prescribing a drug in neither class (Biaxin, clarithromycin, J01FA09) warns on none of the four
 *      and reports none as "not checked": a class allergy is compared by its code, so a drug outside
 *      the class is a checked and clear answer, not an unresolved one.
 *   6. Amending the allergy the old Sulfa shortcut recorded saves the replacement as the ATC class
 *      J01E with no DrugRef id, and archives the original.
 * Each prescribing step first asserts the drug it picked carries the expected ATC code, so a DrugRef
 * extract without that product is reported as such and not as a missed warning. With the
 * rx_show_highest_allergy_warning preference on, the page reports only the most severe match, and a
 * step expects only that one (the shortcut's allergy, saved as severe).
 *
 * Each prescribing step runs in a fresh login: the Rx stash is per session, and a drug left in it
 * from the step before would fire its own allergy check when the page loads.
 *
 * Environment: the deb-install contract (docs/ui-tests/deb-install-validation.md section 6), plus
 * ALLERGY_CLASS_PENICILLIN_DRUG (default "amoxil"), ALLERGY_CLASS_SULFONAMIDE_DRUG (default
 * "septra"), ALLERGY_CLASS_PENICILLIN_COMBINATION_DRUG (default "hp-pac"),
 * ALLERGY_CLASS_SULFONAMIDE_COMBINATION_DRUG (default "blephamide") and ALLERGY_CLASS_OTHER_DRUG
 * (default "biaxin", any drug outside both classes), each at least three characters (the drug picker
 * does not search a shorter term).
 *
 * Fixtures: the owned FAKE- patient, the two allergies the shortcuts record, two allergies seeded by
 * SQL with the old shortcuts' identity, and the amendment; cleanup deletes every allergies row of that
 * patient and asserts none remain. The prescribing steps stage drugs in the Rx stash only and save
 * no prescription.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const NAME = 'allergy-class-shortcut-alert';
const SAVE_PATH = /\/rx\/addAllergy2?$/;
/** AllergyAtcClass.TYPE_CODE: an ATC class allergen, shown as "ATC Class". */
const ATC_CLASS_TYPE = '8';
const SHORTCUTS = [
  { button: 'Penicillin', allergen: 'PENICILLINS', atc: 'J01C', legacyId: '44452', key: 'penicillin' },
  { button: 'Sulfa', allergen: 'SULFONAMIDES', atc: 'J01E', legacyId: '44159', key: 'sulfonamide' },
];

function drugTerm(variable, fallback) {
  const term = (process.env[variable] || fallback).trim();
  h.assert(term.length >= 3, `${variable} must be at least 3 characters, got "${term}"`);
  return term;
}
const outsideBothClasses = code => /^[A-Z]\d{2}[A-Z]{2}\d{2}$/.test(code) && !code.startsWith('J01C') && !code.startsWith('J01E');
const DRUGS = {
  penicillin: { term: drugTerm('ALLERGY_CLASS_PENICILLIN_DRUG', 'amoxil'), premise: 'under J01C', fits: code => code.startsWith('J01C') },
  sulfonamide: { term: drugTerm('ALLERGY_CLASS_SULFONAMIDE_DRUG', 'septra'), premise: 'under J01E', fits: code => code.startsWith('J01E') },
  // Filed outside the ATC class: only the AHFS class DrugRef is also asked about places them in it.
  penicillinCombination: { term: drugTerm('ALLERGY_CLASS_PENICILLIN_COMBINATION_DRUG', 'hp-pac'), premise: 'under A02BD', fits: code => code.startsWith('A02BD') },
  sulfonamideCombination: { term: drugTerm('ALLERGY_CLASS_SULFONAMIDE_COMBINATION_DRUG', 'blephamide'), premise: 'under S01CA', fits: code => code.startsWith('S01CA') },
  other: { term: drugTerm('ALLERGY_CLASS_OTHER_DRUG', 'biaxin'), premise: 'outside J01C and J01E', fits: outsideBothClasses },
};

/** The identity the old shortcut wrote: its fixed DrugRef id as an AHFS class (type 10). */
function seedLegacyShortcutAllergy(s, shortcut, reaction) {
  const { sql, patient, provider } = s;
  const id = sql.value(`INSERT INTO allergies (demographic_no,entry_date,DESCRIPTION,TYPECODE,drugref_id,reaction,archived,
      severity_of_reaction,onset_of_reaction,life_stage,position,lastUpdateDate,providerNo,nonDrug)
    VALUES (${patient},CURDATE(),${h.sqlString(shortcut.allergen)},10,${h.sqlString(shortcut.legacyId)},${h.sqlString(reaction)},0,
      '2','1','A',0,NOW(),${h.sqlString(provider)},0);
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(id), `The old ${shortcut.button} shortcut's allergy was not seeded`);
  return id;
}

/** The allergies row a reaction marker names, newest first, with the columns this check judges. */
function storedAllergy(sql, patient, reaction) {
  const [row] = sql.rows(`SELECT allergyid, DESCRIPTION, TYPECODE, IFNULL(atc,''), IFNULL(drugref_id,''), archived
    FROM allergies WHERE demographic_no=${patient} AND reaction=${h.sqlString(reaction)} ORDER BY allergyid DESC LIMIT 1`);
  if (!row) return null;
  const [id, description, typeCode, atc, drugrefId, archived] = row;
  return { id, description, typeCode, atc, drugrefId, archived };
}

function assertClassAllergy(stored, shortcut, what) {
  h.assert(stored, `${what} is not stored`);
  h.assert(stored.description === shortcut.allergen && stored.typeCode === ATC_CLASS_TYPE && stored.atc === shortcut.atc
    && stored.drugrefId === '' && stored.archived === '0',
  `${what} is not an active ATC class allergy ${shortcut.atc}: ${stored.description}, TYPECODE ${stored.typeCode}, `
    + `atc ${stored.atc || 'empty'}, drugref_id ${stored.drugrefId || 'none'}, archived ${stored.archived}`);
}

function waitForListReload(page) {
  return page.waitForEvent('framenavigated', {
    predicate: frame => frame === page.mainFrame() && /\/rx\/showAllergy/.test(frame.url()),
    timeout: 30000,
  });
}

/** Saves the open reaction form with `reaction` and waits for the list to reload (rx-allergy-dialog.js). */
async function saveReaction(page, form, reaction) {
  await form.locator('#reactionDescription').fill(reaction);
  await form.locator('select[name="severityOfReaction"]').selectOption('3');
  const [response] = await Promise.all([
    page.waitForResponse(r => r.request().method() === 'POST' && SAVE_PATH.test(new URL(r.url()).pathname), { timeout: 30000 }),
    waitForListReload(page),
    form.locator('input[type="submit"][value="Add Allergy"]').click(),
  ]);
  h.assert(response.status() < 400, `Saving the allergy answered HTTP ${response.status()}`);
  await page.locator('#searchString').waitFor({ state: 'visible' });
}

/**
 * Stages `drug` for the patient in a fresh login and returns the allergy check the Rx page ran for
 * it: the probe's JSON and the ATC code it was asked about.
 */
async function prescribe(s, drug) {
  const context = await h.newContext(s.context.browser(), s.config);
  context.setDefaultTimeout(20000);
  context.on('page', page => h.wireStrictPage(page, `${NAME}-rx`, s.recorder));
  try {
    const landing = await h.login(context, s.config, s.recorder, { label: `${NAME}-rx-login` });
    await landing.close();
    const rx = await context.newPage();
    await h.gotoApp(rx, s.config.baseUrl, `/rx/choosePatient?demographicNo=${encodeURIComponent(s.patient)}`);
    await rx.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await h.assertNotErrorPage(rx, 'the Rx module');
    const search = rx.locator('#searchString');
    await search.waitFor({ state: 'visible' });
    // One key at a time: the picker debounces, and a fill() can skip the request.
    await Promise.all([
      rx.waitForResponse(r => r.url().includes('/rx/searchDrug') && r.request().method() === 'POST', { timeout: 60000 }),
      search.pressSequentially(drug.term, { delay: 120 }),
    ]);
    const items = rx.locator('ul.ui-autocomplete li');
    await items.first().waitFor({ state: 'visible' });
    const [probe] = await Promise.all([
      rx.waitForResponse(r => r.url().includes('/rx/showAllergy') && r.request().method() === 'POST', { timeout: 60000 }),
      items.first().click(),
    ]);
    h.assert(probe.status() < 400, `The allergy check answered HTTP ${probe.status()}`);
    const atcCode = new URLSearchParams(probe.request().postData() || '').get('atcCode') || '';
    // The premise, before any verdict: the picked drug is in the class this step is about.
    h.assert(drug.fits(atcCode),
      `The first "${drug.term}" result has ATC code "${atcCode}", not one ${drug.premise}; `
      + 'this DrugRef extract does not carry the product this step prescribes');
    const payload = await probe.json();
    h.assert(Array.isArray(payload.results) && Array.isArray(payload.unchecked), 'The allergy check answered without its result lists');
    return { payload, atcCode, page: rx, context };
  } catch (error) {
    await context.close().catch(() => {});
    throw error;
  }
}

/** The reaction markers among a result list (the probe returns the patient's own reaction text). */
function reactionsIn(list, markers) {
  return markers.filter(marker => list.some(item => item.reaction === marker));
}

async function workflow(s) {
  const { sql, patient, marker } = s;
  s.cleanup(() => {
    sql.execute(`DELETE FROM allergies WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient}`) === '0', 'Owned allergy rows were not removed');
  });
  const reactions = {};
  for (const shortcut of SHORTCUTS) {
    reactions[shortcut.key] = `${marker} ${shortcut.key} shortcut`;
    reactions[`${shortcut.key}Legacy`] = `${marker} ${shortcut.key} old shortcut`;
  }
  const legacyIds = {};
  for (const shortcut of SHORTCUTS) {
    legacyIds[shortcut.key] = seedLegacyShortcutAllergy(s, shortcut, reactions[`${shortcut.key}Legacy`]);
  }
  const all = Object.values(reactions);
  // With this preference on, the allergy check answers only the most severe match (RxShowAllergy2Action).
  const highestOnly = sql.value("SELECT `value` FROM SystemPreferences WHERE name='rx_show_highest_allergy_warning'") === 'true';
  if (highestOnly) console.log('    (rx_show_highest_allergy_warning is on: each step expects only the most severe match)');

  const chart = await s.chart();
  const list = await s.popup(chart, chart.locator('a[onclick*="showAllergy"]').first(), 'allergies');
  await list.locator('#searchString').waitFor({ state: 'visible' });

  await s.step('the Penicillin and Sulfa shortcuts record the ATC class allergens J01C and J01E, with no DrugRef id', async () => {
    for (const shortcut of SHORTCUTS) {
      const [opened] = await Promise.all([
        list.waitForResponse(r => new URL(r.url()).pathname.endsWith('/rx/addReaction2'), { timeout: 30000 }),
        list.locator(`input[value="${shortcut.button}"]`).click(),
      ]);
      h.assert(opened.status() < 400, `The ${shortcut.button} shortcut's form answered HTTP ${opened.status()}`);
      const form = list.locator('#RxAddAllergyForm');
      await form.waitFor({ state: 'visible' });
      const fields = {};
      // A form without one of the fields (the pre-#4576 form has no atc) reads as null rather than waiting for it.
      for (const field of ['ID', 'type', 'name', 'atc']) {
        const input = form.locator(`input[name="${field}"]`);
        fields[field] = await input.count() ? await input.inputValue() : null;
      }
      h.assert(fields.ID === '' && fields.type === ATC_CLASS_TYPE && fields.name === shortcut.allergen && fields.atc === shortcut.atc,
        `The ${shortcut.button} shortcut opened the form for ${JSON.stringify(fields)}, not the ATC class allergen ${shortcut.atc}`);
      h.assert((await form.locator('#addReactionSubheading').innerText()).includes(`ATC class ${shortcut.atc}`),
        `The ${shortcut.button} form does not say which ATC class it records`);
      h.assert(await form.locator('select[name="nonDrug"]').count() === 0,
        `The ${shortcut.button} form asks whether a drug class is a non-drug substance`);
      await saveReaction(list, form, reactions[shortcut.key]);
      assertClassAllergy(storedAllergy(sql, patient, reactions[shortcut.key]), shortcut, `The ${shortcut.button} shortcut's allergy`);
    }
  });

  const prescriptions = [
    {
      drug: DRUGS.penicillin,
      label: `prescribing ${DRUGS.penicillin.term} (ATC J01C) warns on both penicillin allergies and on no sulfonamide one`,
      warned: [reactions.penicillin, reactions.penicillinLegacy],
    },
    {
      drug: DRUGS.sulfonamide,
      label: `prescribing ${DRUGS.sulfonamide.term} (ATC J01E) warns on both sulfonamide allergies and on no penicillin one`,
      warned: [reactions.sulfonamide, reactions.sulfonamideLegacy],
    },
    {
      drug: DRUGS.penicillinCombination,
      label: `prescribing ${DRUGS.penicillinCombination.term} (ATC A02BD, outside J01C but containing amoxicillin) warns on both penicillin allergies`,
      warned: [reactions.penicillin, reactions.penicillinLegacy],
    },
    {
      drug: DRUGS.sulfonamideCombination,
      label: `prescribing ${DRUGS.sulfonamideCombination.term} (ATC S01CA, outside J01E but containing sulfacetamide) warns on both sulfonamide allergies`,
      warned: [reactions.sulfonamide, reactions.sulfonamideLegacy],
    },
    {
      drug: DRUGS.other,
      label: `prescribing ${DRUGS.other.term} (in neither class) warns on no class allergy and leaves none unchecked`,
      warned: [],
    },
  ];
  for (const { drug, label, warned: matches } of prescriptions) {
    // The shortcut's allergy is saved as severe (3), the seeded one as moderate (2).
    const warned = highestOnly ? matches.filter(reaction => reaction.endsWith(' shortcut') && !reaction.endsWith('old shortcut')) : matches;
    await s.step(label, async () => {
      const { payload, atcCode, page, context } = await prescribe(s, drug);
      try {
        console.log(`    (${drug.term}: ATC ${atcCode})`);
        h.assert(payload.checkFailed !== true, 'The allergy check could not reach DrugRef');
        const got = reactionsIn(payload.results, all);
        h.assert(JSON.stringify(got.slice().sort()) === JSON.stringify(warned.slice().sort()),
          `${drug.term} warned on ${got.length ? got.map(r => r.slice(marker.length + 1)).join(', ') : 'no allergy'}; `
          + `expected ${warned.length ? warned.map(r => r.slice(marker.length + 1)).join(', ') : 'none'}`);
        const unchecked = reactionsIn(payload.unchecked, all);
        h.assert(unchecked.length === 0,
          `${drug.term} left the class allergies ${unchecked.map(r => r.slice(marker.length + 1)).join(', ')} "not checked"`);
        h.assert(payload.checkComplete === true, `${drug.term}: the allergy check did not complete`);
        if (warned.length) {
          const alert = page.locator("table[id^='alleg_tbl_']").first();
          await alert.waitFor({ state: 'visible' });
          for (const reaction of warned) await alert.getByText(reaction, { exact: false }).waitFor({ state: 'visible' });
        }
      } finally {
        await context.close();
      }
    });
  }

  await s.step('amending the allergy the old Sulfa shortcut recorded saves it as the ATC class J01E, with no DrugRef id', async () => {
    const sulfa = SHORTCUTS[1];
    const original = legacyIds[sulfa.key];
    await list.reload({ waitUntil: 'domcontentloaded' });
    await list.locator(`#allergy_${original}`).waitFor({ state: 'visible' });
    await Promise.all([
      list.waitForResponse(r => new URL(r.url()).pathname.endsWith('/rx/addReaction2'), { timeout: 30000 }),
      list.locator(`#allergy_${original} a.modifyAllergyLink`).click(),
    ]);
    const form = list.locator('#RxAddAllergyForm');
    await form.waitFor({ state: 'visible' });
    h.assert(await form.locator('#allergyToArchive').inputValue() === original, 'The amendment form lost the allergy being corrected');
    h.assert(await form.locator('input[name="atc"]').inputValue() === sulfa.atc,
      `The amendment form does not carry the old shortcut allergy's ATC class ${sulfa.atc}`);
    const amended = `${marker} sulfonamide old shortcut amended`;
    await saveReaction(list, form, amended);
    await expectValue(sql, `SELECT archived FROM allergies WHERE allergyid=${Number(original)}`, '1',
      'The amendment did not archive the allergy the old shortcut recorded');
    assertClassAllergy(storedAllergy(sql, patient, amended), sulfa, 'The amendment of the old shortcut\'s allergy');
  });
}

module.exports = { workflow, storedAllergy };
if (require.main === module) runWorkflow(NAME, workflow, { openPatient: true });
