#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// rx-card-input-validation: the prescribing card checks what the prescriber typed where they typed
// it, and never saves something they did not enter (#5002, #5003).
// User path: Schedule ▸ Search ▸ Master Record ▸ Prescriptions, then
//   - #5002 renewal pick-up: ReRx an owned medication saved with a pick-up date/time ▸ the card's
//     "more" options ▸ an invalid pick-up date is named and cleared; clearing the optional pick-up
//     date and time raises nothing (it used to raise two format alerts, one printing a regex) ▸
//     Save Only stores the renewal with no pick-up, archives its source as re-prescribed and leaves
//     the unselected control medication alone;
//   - #5003 written date: a custom-drug card with written date 2026-02-31 ▸ Save Only is refused on
//     the card naming the date (it used to save 2026-03-03) and nothing is stored; corrected to
//     2026-02-28 it saves that date;
//   - #5003 inline Min/Max: a non-numeric Min or Max is refused on the card with guidance and sends
//     nothing (it used to POST and get HTTP 500); the server answers such a value 400; a valid
//     fractional dose is accepted and saved.
// Fixtures: the owned FAKE- patient, two marker-named custom drug rows inserted directly (one with a
// pick-up date/time), and the cards this check stages (no DrugRef data). Cleanup removes the
// patient's drugs, prescriptions, drugReason, partial_date and signature rows, and asserts none remain.
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { openRx, stageCustomDrug, saveOnly } = require('./rx-stash-patient-isolation-playwright-checks');

const isPost = (route, value) => response => response.request().method() === 'POST'
  && h.pathOnly(response.url()).endsWith(route)
  && (!value || new URL(response.url()).searchParams.get('parameterValue') === value);

function insertDrug(sql, provider, patient, name, pickup) {
  return h.insertId(sql, `INSERT INTO drugs (provider_no,demographic_no,rx_date,end_date,written_date,customName,special,
      archived,archived_reason,create_date,position,lastUpdateDate,dispenseInternal,quantity,duration,durunit,freqcode,
      takemin,takemax,pickup_datetime)
    VALUES (${h.sqlString(provider)},${patient},CURDATE(),DATE_ADD(CURDATE(),INTERVAL 30 DAY),CURDATE(),${h.sqlString(name)},
      ${h.sqlString(`${name}\n1 tab PO OD x 30 days`)},0,'',NOW(),0,NOW(),0,'30','30','D','OD',1,1,
      ${pickup ? h.sqlString(pickup) : 'NULL'})`, 'drug');
}

/** Types into the card's inline Min/Max (a contentEditable link) and leaves it, like a prescriber. */
async function editInline(rx, id, text, leaveTo) {
  await rx.locator(`#${id}`).click();
  await rx.keyboard.press('ControlOrMeta+A');
  await rx.keyboard.type(text);
  await rx.locator(leaveTo).click();
}

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  s.cleanup(() => {
    const ids = sql.rows(`SELECT drugid FROM drugs WHERE demographic_no=${patient}`).map(row => row[0]);
    h.assert(ids.every(id => /^[1-9]\d*$/.test(id)), 'Owned drug id is invalid');
    sql.execute(`${ids.length ? `DELETE FROM partial_date WHERE table_name=2 AND table_id IN (${ids.join(',')});` : ''}
      DELETE FROM drugReason WHERE demographicNo=${patient};
      DELETE FROM drugs WHERE demographic_no=${patient}; DELETE FROM prescription WHERE demographic_no=${patient};
      DELETE FROM DigitalSignature WHERE demographicId=${patient} AND ModuleType='PRESCRIPTION'`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM prescription WHERE demographic_no=${patient})`) === '0', 'Owned Rx rows were not removed');
  });
  const renewed = insertDrug(sql, provider, patient, `${marker} RENEWED`, '2026-10-20 14:30:00');
  const control = insertDrug(sql, provider, patient, `${marker} CONTROL`, null);
  const controlBefore = () => sql.value(`SELECT CONCAT(archived,'|',IFNULL(pickup_datetime,''),'|',special) FROM drugs WHERE drugid=${control}`);
  const controlState = controlBefore();
  let rx = await openRx(s, patient);

  await s.step('saving the changed renewal field preserves its source and the unselected medication', async () => {
    await rx.locator(`#reRxCheckBox_${renewed}`).check();
    await rx.locator('#reRxConfirmBox input[name="stage"]').click();
    const card = rx.locator(`fieldset[data-drug-ref-id="${renewed}"]`);
    await card.waitFor({ state: 'visible' });
    const key = (await card.locator('[id^="drugName_"]').getAttribute('id')).slice('drugName_'.length);
    await rx.locator(`#moreLessWord_${key}`).click();
    const date = rx.locator(`#pickupDate_${key}`);
    const time = rx.locator(`#pickupTime_${key}`);
    await date.waitFor({ state: 'visible' });
    h.assert(await date.inputValue() === '2026-10-20' && await time.inputValue() === '14:30',
      `The renewal card shows pick-up ${await date.inputValue()} ${await time.inputValue()}`);
    const leave = `#comment_${key}`;

    const invalid = await h.withExpectedDialogs(rx, async () => {
      await date.fill('2026-02-30');
      await rx.locator(leave).click();
    });
    h.assert(invalid.length === 1 && invalid[0].text.includes('"2026-02-30"') && !invalid[0].text.includes('\\d'),
      `An invalid pick-up date raised ${JSON.stringify(invalid)}`);
    h.assert(await date.inputValue() === '', 'An invalid pick-up date was kept');

    // Clearing the optional fields is a valid choice: no dialog at all (an unexpected one fails the step).
    await time.fill('');
    await rx.locator(leave).click();
    h.assert(await date.inputValue() === '' && await time.inputValue() === '', 'The pick-up fields were not left blank');

    await saveOnly(rx);
    await expectValue(sql, `SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient} AND archived=0
      AND customName=${h.sqlString(`${marker} RENEWED`)} AND drugid<>${renewed}`, '1', 'Save Only did not store the renewal');
    const [newRow] = sql.rows(`SELECT IFNULL(pickup_datetime,'none') FROM drugs WHERE demographic_no=${patient} AND archived=0
      AND customName=${h.sqlString(`${marker} RENEWED`)} AND drugid<>${renewed}`);
    h.assert(newRow[0] === 'none', `The renewal saved pick-up ${newRow[0]} after both fields were cleared`);
    h.assert(sql.value(`SELECT CONCAT(archived,'|',archived_reason) FROM drugs WHERE drugid=${renewed}`) === '1|represcribed',
      'The renewed source was not archived as re-prescribed');
    h.assert(controlBefore() === controlState, 'The unselected medication changed');
  });

  await s.step('an invalid written date is refused visibly and retains the unsaved medication', async () => {
    const name = `${marker} WRITTEN`;
    const key = await stageCustomDrug(rx, name);
    await rx.locator(`#instructions_${key}`).fill('1 tab PO OD x 30 days');
    await rx.locator(`label[for="jsonDxSearch_${key}"]`).click();
    await rx.locator(`#moreLessWord_${key}`).click();
    await rx.locator(`#writtenDate_${key}`).fill('2026-02-31');
    let posted = 0;
    const count = request => { if (request.method() === 'POST' && /parameterValue=updateSaveAllDrugs/.test(request.url())) posted++; };
    rx.on('request', count);
    try {
      const dialogs = await h.withExpectedDialogs(rx, () => rx.locator('#saveOnlyButton').click());
      h.assert(dialogs.length === 1 && dialogs[0].text.includes('2026-02-31'),
        `Saving an impossible written date raised ${JSON.stringify(dialogs)}`);
    } finally {
      rx.off('request', count);
    }
    h.assert(posted === 0, 'The impossible written date was sent to the server');
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE customName=${h.sqlString(name)}`) === '0', 'An impossible written date was saved');
    h.assert(await rx.locator(`#drugName_${key}`).count() === 1, 'The refused card was lost');

    await rx.locator(`#writtenDate_${key}`).fill('2026-02-28');
    await saveOnly(rx);
    await expectValue(sql, `SELECT DATE_FORMAT(written_date,'%Y-%m-%d') FROM drugs WHERE customName=${h.sqlString(name)}`, '2026-02-28',
      'The corrected written date was not saved as entered');
  });

  await s.step('an invalid numeric dose is refused without losing the card or changing a medication', async () => {
    rx = await openRx(s, patient);
    const name = `${marker} DOSE`;
    const key = await stageCustomDrug(rx, name);
    const [parsed] = await Promise.all([
      rx.waitForResponse(isPost('/rx/UpdateScript')),
      (async () => {
        await rx.locator(`#instructions_${key}`).fill('1 tab PO OD x 30 days');
        await rx.locator(`label[for="jsonDxSearch_${key}"]`).click();
      })(),
    ]);
    h.assert(parsed.status() === 200, `Parsing the directions answered HTTP ${parsed.status()}`);
    const leave = `label[for="jsonDxSearch_${key}"]`;
    for (const field of ['minimum', 'maximum']) {
      let sent = 0;
      const count = request => { if (request.method() === 'POST' && /parameterValue=updateProperty/.test(request.url())) sent++; };
      rx.on('request', count);
      try {
        const dialogs = await h.withExpectedDialogs(rx, () => editInline(rx, `${field}_${key}`, 'two tabs', leave));
        h.assert(dialogs.length === 1 && dialogs[0].text.includes('"two tabs"'), `A non-numeric ${field} raised ${JSON.stringify(dialogs)}`);
      } finally {
        rx.off('request', count);
      }
      h.assert(sent === 0, `A non-numeric ${field} was sent to the server`);
      h.assert(await rx.locator(`#drugName_${key}`).count() === 1, `The card was lost after a refused ${field}`);
    }

    // Dismissing a validation alert must not let either save path persist the old staged dose
    // while the card still displays the refused edit.
    let saves = 0;
    const countSaves = request => {
      if (request.method() === 'POST' && /parameterValue=updateSaveAllDrugs/.test(request.url())) saves++;
    };
    rx.on('request', countSaves);
    try {
      for (const button of ['saveOnlyButton', 'saveButton']) {
        const dialogs = await h.withExpectedDialogs(rx, () => rx.locator(`#${button}`).click());
        h.assert(dialogs.some(dialog => dialog.type === 'alert' && dialog.text.includes('two tabs')),
          `${button} did not explain the refused dose: ${JSON.stringify(dialogs)}`);
      }
    } finally {
      rx.off('request', countSaves);
    }
    h.assert(saves === 0, 'A refused inline dose still reached prescription persistence');
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE customName=${h.sqlString(name)}`) === '0',
      'Saving after a refused dose persisted the older staged medication');

    const token = await rx.locator('input[name="CSRF-TOKEN"]').first().inputValue();
    const direct = await s.context.request.post(`${s.config.baseUrl}/rx/WriteScript?parameterValue=updateProperty`, {
      form: { demographicNo: patient, elementId: `minimum_${key}`, propertyValue: 'two tabs' },
      headers: { 'CSRF-TOKEN': token, 'X-Requested-With': 'XMLHttpRequest' },
      maxRedirects: 0,
    });
    const answer = await direct.text();
    await direct.dispose();
    h.assert(direct.status() === 400 && answer.includes('INVALID_RX_DOSE'),
      `The server answered a non-numeric Min with HTTP ${direct.status()}: ${answer.slice(0, 120)}`);

    for (const field of ['minimum', 'maximum']) {
      const [updated] = await Promise.all([
        rx.waitForResponse(isPost('/rx/WriteScript', 'updateProperty')),
        editInline(rx, `${field}_${key}`, '0.5', leave),
      ]);
      h.assert(updated.status() === 200, `A valid ${field} answered HTTP ${updated.status()}`);
    }
    await saveOnly(rx);
    await expectValue(sql, `SELECT CONCAT(takemin+0,'|',takemax+0) FROM drugs WHERE customName=${h.sqlString(name)}`, '0.5|0.5',
      'The corrected dose was not saved');
    h.assert(controlBefore() === controlState, 'The control medication changed');
  });
}

if (require.main === module) runWorkflow('rx-card-input-validation', workflow, { openPatient: true });
module.exports = { workflow };
