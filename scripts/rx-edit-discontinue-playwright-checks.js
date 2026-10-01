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
      ${drugIds.length ? `DELETE FROM partial_date WHERE table_name=2 AND table_id IN (${drugIds.join(',')});` : ''}
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
      // Leave the field for the indication box: Tab would land on Qty, whose blur handler throws (getCost).
      rx.locator(`label[for="jsonDxSearch_${card}"]`).click(),
    ]);
    h.assert(parsed.status() === 200, `rx/UpdateScript answered HTTP ${parsed.status()}`);
    const body = await parsed.json();
    h.assert(String(body.takeMin) === '1' && String(body.takeMax) === '1' && body.frequency === 'BID'
      && String(body.duration) === '14' && body.durationUnit === 'D', `The instructions parsed to ${JSON.stringify(body)}`);
    h.assert((await rx.locator(`#frequency_${card}`).innerText()).trim() === 'BID'
      && (await rx.locator(`#duration_${card}`).innerText()).trim() === '14', 'The staged card does not show the parsed frequency and duration');
  });

  let drug;
  await s.step('Save persists the parsed dose, frequency, duration, quantity and end date on the drugs row', async () => {
    h.assert(drugCount() === '0', 'A drug existed before the save');
    const [saved] = await Promise.all([
      rx.waitForResponse(r => isPost('/rx/WriteScript')(r) && new URL(r.url()).searchParams.get('parameterValue') === 'updateSaveAllDrugs'),
      rx.locator('#saveOnlyButton').click(),
    ]);
    h.assert(saved.status() === 200 && /^[1-9]\d*$/.test(String((await saved.json()).scriptId)), `Save answered HTTP ${saved.status()} without a script id`);
    await rx.locator(`#set_${card}`).waitFor({ state: 'detached' });
    const [row] = sql.rows(`SELECT drugid,BN,takemin,takemax,freqcode,duration,durunit,quantity,special,
        DATEDIFF(end_date,rx_date),archived,provider_no,script_no FROM drugs WHERE demographic_no=${patient}`);
    const [id, brand, takemin, takemax, freq, duration, unit, quantity, special, days, archived, prescriber, script] = row;
    drug = id;
    h.assert(brand === DRUG_NAME, 'The saved drug is not the product chosen from the search');
    h.assert(Number(takemin) === 1 && Number(takemax) === 1 && freq === 'BID' && duration === '14' && unit === 'D',
      `The saved dose/frequency/duration is ${takemin}-${takemax} ${freq} x ${duration}${unit}`);
    h.assert(quantity === '28', `The saved quantity is ${quantity}, not 1 tab BID for 14 days (28)`);
    h.assert(special.includes(INSTRUCTIONS), 'The saved prescription text lost the typed instructions');
    h.assert(days === '14', `The saved end date is ${days} day(s) after the start, not the 14-day duration`);
    h.assert(archived === '0' && prescriber === provider && /^[1-9]\d*$/.test(script), 'The saved drug is archived, not the test provider\'s, or not on a script');
    await rx.locator(`#prescrip_${drug}`).waitFor({ state: 'visible' });
  });

  await s.step('Timeline Drug Profile lists the saved drug and graphs it when ticked', async () => {
    const timeline = await s.popup(rx, rx.locator('a[href*="/rx/ViewChartDrugProfile"]').first(), 'chart-drug-profile');
    h.assert(new URL(timeline.url()).searchParams.get('demographic_no') === patient, 'The timeline opened for another patient');
    const din = sql.value(`SELECT regional_identifier FROM drugs WHERE drugid=${drug}`);
    const box = timeline.locator(`input[name="drug"][value="${din}"]`);
    h.assert(await box.count() === 1, 'The timeline does not list the saved drug');
    h.assert((await box.locator('xpath=..').innerText()).includes(INSTRUCTIONS), 'The timeline entry lacks the saved instructions');
    await box.check();
    await ui.clickAndAwaitReload(timeline, timeline.locator('input[type="submit"]').first(), { label: 'Add Meds to Graph' });
    h.assert(new URL(timeline.url()).searchParams.getAll('drug').includes(din), 'Add Meds to Graph did not submit the ticked drug');
    h.assert(await timeline.locator(`input[name="drug"][value="${din}"]`).isChecked(), 'The graphed drug is not ticked after the reload');
    const graph = timeline.locator('img[src*="/encounter/GraphMeasurements"]');
    h.assert(new URL(await graph.evaluate(img => img.src)).searchParams.getAll('drug').includes(din), 'The chart image does not graph the ticked drug');
    h.assert(await graph.evaluate(img => img.complete && img.naturalWidth > 0), 'The medication chart image did not render');
    await timeline.close();
  });

  await s.step('the indication link files an icd9 reason on the drug through rx/RxReason and lists it when reopened', async () => {
    const comment = `${marker} indication`;
    const openReason = () => s.popup(rx, rx.locator(`a[onclick*="popupRxReasonWindow("][onclick*=",${drug})"]`).first(), 'rx-reason');
    const reason = await openReason();
    h.assert(new URL(reason.url()).searchParams.get('drugId') === drug, 'The reason popup opened for another drug');
    const search = reason.locator('#jsonDxSearch');
    await search.pressSequentially('401', { delay: 80 });
    const option = reason.locator('ul.ui-autocomplete li').filter({ hasText: /^401/ }).first();
    await option.waitFor({ state: 'visible' });
    const code = (await option.innerText()).split(':')[0].trim();
    await option.click();
    h.assert(await search.inputValue() === code, 'Choosing the code did not fill the indication field');
    await reason.locator('#comments').fill(comment);
    await reason.locator('#primaryReasonFlag').check();
    const closed = reason.waitForEvent('close', { timeout: 20000 });
    const [posted] = await Promise.all([reason.waitForResponse(isPost('/rx/RxReason')), reason.locator('#saveRxReason').click()]);
    h.assert(posted.status() === 200, `rx/RxReason answered HTTP ${posted.status()}`);
    await closed;
    await expectValue(sql, `SELECT COUNT(*) FROM drugReason WHERE drugId=${drug} AND demographicNo=${patient}
      AND codingSystem='icd9' AND code=${h.sqlString(code)} AND comments=${h.sqlString(comment)} AND primaryReasonFlag=1
      AND archivedFlag=0 AND providerNo=${h.sqlString(provider)} AND dateCoded=CURDATE()`, '1', 'The indication was not stored on the owned drug');
    const reopened = await openReason();
    const listed = (await reopened.locator('fieldset').filter({ hasText: 'Current Indications' }).innerText()).replace(/\s+/g, ' ');
    h.assert(listed.includes(code) && listed.includes(comment), 'The reopened reason popup does not list the stored indication');
    await reopened.close();
  });

  await s.step('Discon with a reason archives the drug, keeps its end date and files a linked chart note', async () => {
    const endDate = sql.value(`SELECT end_date FROM drugs WHERE drugid=${drug}`);
    const comment = `${marker} stopped`;
    await rx.locator(`#discont_${drug}`).click();
    const panel = rx.locator('#discontinueUI');
    await panel.waitFor({ state: 'visible' });
    h.assert((await rx.locator('#disDrug').innerText()).includes(DRUG_NAME), 'The discontinue panel names another drug');
    await rx.locator('#disReason').selectOption('doseChange');
    await rx.locator('#disComment').fill(comment);
    const [response] = await Promise.all([
      rx.waitForResponse(r => isPost('/rx/deleteRx')(r) && new URL(r.url()).searchParams.get('parameterValue') === 'Discontinue'),
      panel.locator('input[onclick*="Discontinue2("]').click(),
    ]);
    h.assert(response.status() === 200, `Discontinue answered HTTP ${response.status()}`);
    await panel.waitFor({ state: 'hidden' });
    h.assert((await rx.locator(`#discont_${drug}`).innerText()).trim() === 'doseChange', 'The drug row does not show the discontinue reason');
    h.assert(await rx.locator(`#prescrip_${drug}`).evaluate(a => a.style.textDecoration === 'line-through'), 'The discontinued drug is not struck through');
    await expectValue(sql, `SELECT COUNT(*) FROM drugs WHERE drugid=${drug} AND archived=1 AND archived_reason='doseChange'
      AND DATE(archived_date)=CURDATE()`, '1', 'The drug was not archived with the chosen reason and today\'s date');
    h.assert(sql.value(`SELECT end_date FROM drugs WHERE drugid=${drug}`) === endDate, 'Discontinuing rewrote the prescribed end date');
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note n JOIN casemgmt_note_link l ON l.note_id=n.note_id AND l.table_name=2
      AND l.table_id=${drug} WHERE n.demographic_no=${patient} AND n.note LIKE ${h.sqlString(`%Discontinued reason: doseChange%${comment}%`)}`) === '1',
    'No chart note carrying the discontinue reason and comment is linked to the drug');
  });

  await s.step('the static script\'s prescription details show the archived drug and Form Update writes drugs.drug_form', async () => {
    await ui.clickAndAwaitReload(rx, rx.locator(`#prescrip_${drug}`), { label: 'drug row' });
    h.assert(h.pathOnly(rx.url()).endsWith('/rx/ViewStaticScript2'), 'The drug row did not open the static script page');
    const record = await s.popup(rx, rx.locator(`a[onclick*="/rx/ViewDisplayRxRecord?id=${drug}'"]`).first(), 'rx-record');
    const field = async label => (await record.locator('tr').filter({ has: record.locator('td.label', { hasText: new RegExp(`^${label}:$`) }) })
      .first().locator('td').nth(1).innerText()).trim();
    h.assert(await field('Brand Name') === DRUG_NAME && await field('Frequency') === 'BID' && await field('Duration') === '14'
      && await field('Quantity') === '28', 'The record popup does not show the saved product, frequency, duration and quantity');
    h.assert(await field('Archived Reason') === 'doseChange', 'The record popup does not show the discontinue reason');
    h.assert((await field('Problem Code')).includes('(icd9:401'), 'The record popup does not show the filed indication');
    const before = sql.value(`SELECT COALESCE(drug_form,'') FROM drugs WHERE drugid=${drug}`);
    const target = before === 'Capsule' ? 'Tablet' : 'Capsule';
    const form = await s.popup(record, record.locator('a[onclick*="updateForm()"]'), 'rx-update-form');
    h.assert(new URL(form.url()).searchParams.get('id') === drug, 'The form update popup opened for another drug');
    await form.locator('select[name="drugForm"]').selectOption(target);
    const closed = form.waitForEvent('close', { timeout: 20000 });
    const [posted] = await Promise.all([form.waitForResponse(isPost('/rx/ViewUpdateForm')), form.locator('input[type="submit"]').click()]);
    h.assert(posted.status() === 200, `rx/ViewUpdateForm answered HTTP ${posted.status()}`);
    await expectValue(sql, `SELECT drug_form FROM drugs WHERE drugid=${drug} AND demographic_no=${patient}`, target, 'The new drug form was not saved');
    await closed;
    await record.close();
  });

  await s.step('every mutator here refuses GET before writing', async () => {
    const snapshot = () => sql.value(`SELECT CONCAT_WS('|',(SELECT COUNT(*) FROM drugReason WHERE demographicNo=${patient}),
      (SELECT CONCAT(archived,drug_form) FROM drugs WHERE drugid=${drug}),(SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}))`);
    const before = snapshot();
    for (const path of [
      `/rx/RxReason?method=addDrugReason&demographicNo=${patient}&drugId=${drug}&codingSystem=icd9&jsonDxSearch=250`,
      `/rx/clearPending?demographicNo=${patient}&action=close`,
      `/rx/ViewUpdateForm?id=${drug}&action=update&drugForm=Gel`,
      `/rx/UpdateScript?parameterValue=updateDrug&action=parseInstructions&randomId=1&instruction=x&demographicNo=${patient}`,
      `/rx/deleteRx?parameterValue=Discontinue&drugId=${drug}&reason=other&demoNo=${patient}`,
    ]) {
      const response = await s.context.request.get(h.appUrl(s.config.baseUrl, path), { maxRedirects: 0 });
      h.assert(response.status() === 405, `GET ${path.split('?')[0]} answered HTTP ${response.status()} instead of 405`);
    }
    h.assert(snapshot() === before, 'A refused GET changed the chart');
  });

  await s.step('Back to CARLOS after Save And Print clears the staged script (rx/clearPending), so a reopened Rx stages nothing', async () => {
    rx = await openRx('rx-module-print');
    const staged = await stageFromSearch(rx, DRUG_TERM, DRUG_NAME);
    await rx.locator(`#instructions_${staged}`).fill(INSTRUCTIONS);
    await Promise.all([rx.waitForResponse(isPost('/rx/UpdateScript')), rx.locator(`label[for="jsonDxSearch_${staged}"]`).click()]);
    await rx.locator('#saveButton').click();
    const modal = rx.frameLocator('#carlosModalBody iframe');
    await modal.locator('#printPasteButton').waitFor({ state: 'visible', timeout: 30000 });
    await expectValue(sql, `SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}`, '2', 'Save And Print did not persist the second drug');
    // ViewScript2 renders its preview only while the session stash still holds the script.
    await modal.locator('#preview').waitFor({ state: 'attached' });
    let requested = false;
    const seen = request => { if (h.pathOnly(request.url()).endsWith('/rx/clearPending')) requested = true; };
    s.context.on('request', seen);
    const closed = rx.waitForEvent('close', { timeout: 20000 });
    await modal.locator('input[onclick*="clearPending(\'close\')"]').click();
    await closed;
    const reopened = await openRx('rx-module-after-back');
    s.context.off('request', seen);
    const stillStaged = await reopened.locator('[id^="drugName_"]').count();
    h.assert(stillStaged === 0, `Back to CARLOS left ${stillStaged} saved card(s) staged for the next prescription `
      + `(rx/clearPending ${requested ? 'was requested' : 'was never sent: the window closed before the form posted'})`);
  });
}

if (require.main === module) runWorkflow('rx-edit-discontinue', workflow, { openPatient: true });
module.exports = { workflow, stageFromSearch };
