#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * rx-card-options — the options a prescriber sets under a staged card's "more" link, saved and
 * read back.
 *
 * User path: Schedule ▸ Search ▸ Master Record ▸ Prescriptions (rx/choosePatient) ▸ type a name ▸
 * Custom Drug ▸ Instructions ▸ the card's "more" link ▸
 *   1. Long Term Medication, Refill Duration and Quantity, Dispense Interval, Prescribed by Outside
 *      Provider (name, OHIP no.), Past Medication, Patient Compliance, Non-Authoritative,
 *      Substitution not allowed, Last Refill Date, Written Date, Protocol Reference, Pick Up Date and
 *      Time, Comment, eTreatment type and Rx status ▸ Save Only: the drugs row stores each value;
 *   2. Rx again ▸ tick the drug's ReRx box ▸ Stage Medication: the restaged card shows the saved
 *      options, including the last refill and pick-up date and time, and a Written Date reset to
 *      the new script's start date (by design: RxPrescriptionData.newPrescription sets both to
 *      today); its remove link unstages it and nothing is saved. (Re-prescribing does not carry the
 *      Comment or Non-Authoritative over: the same method copies neither. Whether it should is a
 *      product question, so the check asserts neither way;)
 *   3. a second custom drug with a 12:30 pick-up time ▸ Save Only: the time is stored as 12:30. The
 *      field asks for 24-hour HH:MM (checkDate.js isValidTime), but RxWriteScript2Action parses it
 *      with "hh:mm", which reads 12 as midnight. This step runs last so the earlier ones are proven
 *      first.
 * Other checks cover the neighbours: instructions parsing and Save Only (rx-edit-discontinue), the
 * card's Add to Favorites (gap-encounter-rx-add-favourite), the quantity price (gap-encounter-rx-
 * quantity-price), re-prescribing (rx-stash-patient-isolation, rx-fax-reprint-represcribe), removing
 * a card (rx-stash-deletion), and the long term, compliance, outside prescriber and no-substitution
 * values a favourite carries (rx-favorites-choose-drug). None sets the card's "more" options.
 *
 * Fixtures: the owned synthetic patient and marker-named custom drugs. Cleanup removes every drugs,
 * prescription, partial_date, drugReason and DigitalSignature row of the patient and asserts none
 * remain. No DrugRef data is needed.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { stageCustomDrug } = require('./rx-stash-patient-isolation-playwright-checks');
const { masterRecordLink } = require('./lib/master-record-link');

const INSTRUCTIONS = '1 tab PO OD x 30 days';
const TIMEOUT = 30000;
const isPost = route => response => response.request().method() === 'POST' && h.pathOnly(response.url()).endsWith(route);

/** The option values typed on the card, and the drugs column each is stored in. */
function optionsFor(marker) {
  return {
    refillDuration: '28',
    refillQuantity: '30',
    dispenseInterval: '7',
    outsideName: 'FAKE-Outside Prescriber',
    outsideOhip: '123456',
    lastRefill: '2026-09-01',
    written: '2026-10-01',
    protocol: `${marker}-protocol`,
    pickupDate: '2026-10-20',
    pickupTime: '17:45',
    comment: `${marker} FAKE comment`,
    eTreatment: 'CHRON',
    rxStatus: 'Active',
  };
}

/** Type instructions into a staged card and leave the field so rx/UpdateScript parses them. */
async function enterInstructions(rx, card, text) {
  await rx.locator(`#instructions_${card}`).fill(text);
  const [parsed] = await Promise.all([
    rx.waitForResponse(isPost('/rx/UpdateScript')),
    // Leave the field for the indication box: Tab would land on Qty, whose blur handler throws (getCost).
    rx.locator(`label[for="jsonDxSearch_${card}"]`).click(),
  ]);
  h.assert(parsed.status() === 200, `rx/UpdateScript answered HTTP ${parsed.status()}`);
}

/** Open the card's "more" options the way the prescriber does. */
async function openMore(rx, card) {
  const more = rx.locator(`#rx_more_${card}`);
  h.assert(!(await more.isVisible()), 'The card opened with its more options already shown');
  await rx.locator(`#moreLessWord_${card}`).click();
  await more.waitFor({ state: 'visible' });
}

async function saveOnly(rx) {
  const [saved] = await Promise.all([
    rx.waitForResponse(r => isPost('/rx/WriteScript')(r) && new URL(r.url()).searchParams.get('parameterValue') === 'updateSaveAllDrugs'),
    rx.locator('#saveOnlyButton').click(),
  ]);
  h.assert(saved.status() === 200 && /^[1-9]\d*$/.test(String((await saved.json()).scriptId)),
    `Save Only answered HTTP ${saved.status()} without a script id`);
}

async function workflow(s) {
  const { sql, patient, marker } = s;
  const options = optionsFor(marker);
  s.cleanup(() => {
    const drugIds = sql.rows(`SELECT drugid FROM drugs WHERE demographic_no=${patient}`).map(row => row[0]);
    h.assert(drugIds.every(id => /^[1-9]\d*$/.test(id)), 'Owned drug id is invalid');
    sql.execute(`DELETE FROM drugReason WHERE demographicNo=${patient};
      ${drugIds.length ? `DELETE FROM partial_date WHERE table_name=2 AND table_id IN (${drugIds.join(',')});` : ''}
      DELETE FROM drugs WHERE demographic_no=${patient}; DELETE FROM prescription WHERE demographic_no=${patient};
      DELETE FROM DigitalSignature WHERE demographicId=${patient} AND ModuleType='PRESCRIPTION'`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM prescription WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM drugReason WHERE demographicNo=${patient})
      + (SELECT COUNT(*) FROM DigitalSignature WHERE demographicId=${patient})`) === '0', 'Owned Rx rows were not removed');
  });
  const drugCount = () => sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`);

  const openRx = async label => {
    const rx = await s.popup(s.master, masterRecordLink(s.master, '/rx/choosePatient').first(), label);
    h.assert(new URL(rx.url()).searchParams.get('demographicNo') === patient, 'The Rx module opened for another patient');
    await rx.locator('#searchString').waitFor({ state: 'visible' });
    await rx.waitForLoadState('networkidle');
    return rx;
  };
  let rx = await openRx('rx-module');
  let drug;

  await s.step('the card\'s more options are saved on the drugs row', async () => {
    h.assert(drugCount() === '0', 'A drug existed before the save');
    const card = await stageCustomDrug(rx, `${marker} FAKE options drug`);
    await enterInstructions(rx, card, INSTRUCTIONS);
    await openMore(rx, card);
    await rx.locator(`#longTermY_${card}`).check();
    await rx.locator(`#refillDuration_${card}`).fill(options.refillDuration);
    await rx.locator(`#refillQuantity_${card}`).fill(options.refillQuantity);
    await rx.locator(`#dispenseInterval_${card}`).fill(options.dispenseInterval);
    await rx.locator(`#ocheck_${card}`).check();
    await rx.locator(`#otext_${card}`).waitFor({ state: 'visible' });
    await rx.locator(`#outsideProviderName_${card}`).fill(options.outsideName);
    await rx.locator(`#outsideProviderOhip_${card}`).fill(options.outsideOhip);
    await rx.locator(`#pastMedY_${card}`).check();
    await rx.locator(`#patientComplianceN_${card}`).check();
    await rx.locator(`#nonAuthoritativeN_${card}`).check();
    await rx.locator(`#nosubs_${card}`).check();
    await rx.locator(`#lastRefillDate_${card}`).fill(options.lastRefill);
    await rx.locator(`#writtenDate_${card}`).fill(options.written);
    await rx.locator(`#protocol_${card}`).fill(options.protocol);
    await rx.locator(`#pickupDate_${card}`).fill(options.pickupDate);
    await rx.locator(`#pickupTime_${card}`).fill(options.pickupTime);
    await rx.locator(`#comment_${card}`).fill(options.comment);
    await rx.locator(`select[name="eTreatmentType_${card}"]`).selectOption({ value: options.eTreatment });
    await rx.locator(`select[name="rxStatus_${card}"]`).selectOption({ value: options.rxStatus });
    await saveOnly(rx);
    await expectValue(sql, `SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`, '1', 'Save Only did not store exactly one drug');
    const [row] = sql.rows(`SELECT drugid, long_term, refill_duration, refill_quantity, dispense_interval,
        outside_provider_name, outside_provider_ohip, past_med, patient_compliance, non_authoritative, nosubs,
        last_refill_date, written_date, protocol, DATE_FORMAT(pickup_datetime,'%Y-%m-%d %H:%i'), comment,
        eTreatmentType, rxStatus, archived FROM drugs WHERE demographic_no=${patient}`);
    const [id, longTerm, refillDuration, refillQuantity, dispenseInterval, outsideName, outsideOhip, pastMed,
      compliance, nonAuthoritative, nosubs, lastRefill, written, protocol, pickup, comment, eTreatment, rxStatus, archived] = row;
    drug = id;
    const expected = {
      longTerm: '1', refillDuration: options.refillDuration, refillQuantity: options.refillQuantity,
      dispenseInterval: options.dispenseInterval, outsideName: options.outsideName, outsideOhip: options.outsideOhip,
      pastMed: '1', compliance: '0', nonAuthoritative: '1', nosubs: '1', lastRefill: options.lastRefill,
      written: options.written, protocol: options.protocol, pickup: `${options.pickupDate} ${options.pickupTime}`,
      comment: options.comment, eTreatment: options.eTreatment, rxStatus: options.rxStatus, archived: '0',
    };
    const actual = {
      longTerm, refillDuration, refillQuantity, dispenseInterval, outsideName, outsideOhip, pastMed, compliance,
      nonAuthoritative, nosubs, lastRefill, written, protocol, pickup, comment, eTreatment, rxStatus, archived,
    };
    const wrong = Object.keys(expected).filter(key => actual[key] !== expected[key]);
    h.assert(wrong.length === 0, `The saved drug stored ${wrong.map(key => `${key}=${actual[key]} (typed ${expected[key]})`).join(', ')}`);
  });

  await s.step('ReRx restages the drug with its saved options, and removing the card saves nothing', async () => {
    await rx.close();
    rx = await openRx('rx-module-rerx');
    await rx.locator(`#reRxCheckBox_${drug}`).check();
    await rx.locator('#reRxConfirmBox input[name="stage"]').click();
    const restaged = rx.locator(`fieldset[data-drug-ref-id="${drug}"]`);
    await restaged.waitFor({ state: 'visible', timeout: TIMEOUT });
    const card = (await restaged.locator('[id^="instructions_"]').first().getAttribute('id')).slice('instructions_'.length);
    const shown = {
      longTerm: await rx.locator(`#longTermY_${card}`).isChecked(),
      refillDuration: await rx.locator(`#refillDuration_${card}`).inputValue(),
      refillQuantity: await rx.locator(`#refillQuantity_${card}`).inputValue(),
      dispenseInterval: await rx.locator(`#dispenseInterval_${card}`).inputValue(),
      outside: await rx.locator(`#ocheck_${card}`).isChecked(),
      outsideName: await rx.locator(`#outsideProviderName_${card}`).inputValue(),
      outsideOhip: await rx.locator(`#outsideProviderOhip_${card}`).inputValue(),
      pastMed: await rx.locator(`#pastMedY_${card}`).isChecked(),
      compliance: await rx.locator(`#patientComplianceN_${card}`).isChecked(),
      nosubs: await rx.locator(`#nosubs_${card}`).isChecked(),
      lastRefill: await rx.locator(`#lastRefillDate_${card}`).inputValue(),
      pickupDate: await rx.locator(`#pickupDate_${card}`).inputValue(),
      pickupTime: await rx.locator(`#pickupTime_${card}`).inputValue(),
      protocol: await rx.locator(`#protocol_${card}`).inputValue(),
      eTreatment: await rx.locator(`select[name="eTreatmentType_${card}"]`).inputValue(),
      rxStatus: await rx.locator(`select[name="rxStatus_${card}"]`).inputValue(),
    };
    const expected = {
      longTerm: true, refillDuration: options.refillDuration, refillQuantity: options.refillQuantity,
      dispenseInterval: options.dispenseInterval, outside: true, outsideName: options.outsideName,
      outsideOhip: options.outsideOhip, pastMed: true, compliance: true, nosubs: true,
      lastRefill: options.lastRefill, pickupDate: options.pickupDate, pickupTime: options.pickupTime,
      protocol: options.protocol, eTreatment: options.eTreatment, rxStatus: options.rxStatus,
    };
    const wrong = Object.keys(expected).filter(key => shown[key] !== expected[key]);
    h.assert(wrong.length === 0, `The restaged card shows ${wrong.map(key => `${key}=${shown[key]} (saved ${expected[key]})`).join(', ')}`);
    const written = await rx.locator(`#writtenDate_${card}`).inputValue();
    const started = await rx.locator(`#rxDate_${card}`).inputValue();
    h.assert(/^\d{4}-\d{2}-\d{2}$/.test(written) && written === started && written !== options.written,
      `The restaged card's Written Date is ${written}, not the new script's start date ${started}`);
    const [removed] = await Promise.all([
      rx.waitForResponse(r => isPost('/rx/rxStashDelete')(r)
        && new URLSearchParams(r.request().postData() || '').get('parameterValue') === 'deletePrescribe'),
      restaged.locator('a[onclick*="removePrescribingDrug("]').click(),
    ]);
    h.assert(removed.ok(), `Removing the restaged card answered HTTP ${removed.status()}`);
    await restaged.waitFor({ state: 'detached' });
    h.assert(drugCount() === '1' && sql.value(`SELECT archived FROM drugs WHERE drugid=${drug}`) === '0',
      'Removing the restaged card changed the saved drugs');
  });

  await s.step('a 12:30 pick-up time is stored as 12:30', async () => {
    const card = await stageCustomDrug(rx, `${marker} FAKE noon pickup`);
    await enterInstructions(rx, card, INSTRUCTIONS);
    await openMore(rx, card);
    await rx.locator(`#pickupDate_${card}`).fill(options.pickupDate);
    await rx.locator(`#pickupTime_${card}`).fill('12:30');
    await saveOnly(rx);
    await expectValue(sql, `SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`, '2', 'Save Only did not store the second drug');
    const pickup = sql.value(`SELECT DATE_FORMAT(pickup_datetime,'%Y-%m-%d %H:%i') FROM drugs
      WHERE demographic_no=${patient} AND drugid<>${drug}`);
    h.assert(pickup === `${options.pickupDate} 12:30`, `A 12:30 pick-up time was stored as ${pickup}`);
  });
}

if (require.main === module) runWorkflow('rx-card-options', workflow, { openPatient: true });
module.exports = { workflow };
