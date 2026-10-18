#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// rx-preview-return-edit: correcting a prescription after dismissing its Save And Print preview
// leaves one active medication (#5005).
// User path: Schedule ▸ Search ▸ Master Record ▸ Prescriptions ▸ Custom Drug ▸ directions OD for 30
// days ▸ Save And Print (the preview opens over the page) ▸ dismiss the preview ▸ change the retained
// card to BID for 14 days ▸ Save Only. Save And Print keeps its acknowledged card staged so it can be
// corrected; the correction used to insert a second active medication beside the first. Now the first
// row is archived as re-prescribed and the corrected one is the only active copy. Run twice: the
// preview's X button, and Escape. An unrelated medication is the control throughout.
// Fixtures: the owned FAKE- patient, one marker-named control drug row inserted directly, and the
// cards this check stages (no DrugRef data). Cleanup removes the patient's drugs, prescriptions,
// drugReason, partial_date and signature rows, and asserts none remain.
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { openRx, stageCustomDrug, saveOnly } = require('./rx-stash-patient-isolation-playwright-checks');

const isPost = (route, value) => response => response.request().method() === 'POST'
  && h.pathOnly(response.url()).endsWith(route)
  && (!value || new URL(response.url()).searchParams.get('parameterValue') === value);

/** The print window and its preview are frames over the Rx page; poll Playwright's frame list. */
async function waitForFrame(page, pattern, timeout = 60000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    const frame = page.frames().find(f => pattern.test(f.url()));
    if (frame) return frame;
    await page.waitForTimeout(250);
  }
  throw new Error(`The preview frame ${pattern} did not open`);
}

async function enterDirections(rx, key, text) {
  const [parsed] = await Promise.all([
    rx.waitForResponse(isPost('/rx/UpdateScript')),
    (async () => {
      await rx.locator(`#instructions_${key}`).fill(text);
      await rx.locator(`label[for="jsonDxSearch_${key}"]`).click();
    })(),
  ]);
  h.assert(parsed.status() === 200, `Parsing the directions answered HTTP ${parsed.status()}`);
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
  const control = h.insertId(sql, `INSERT INTO drugs (provider_no,demographic_no,rx_date,end_date,written_date,customName,special,
      archived,archived_reason,create_date,position,lastUpdateDate,dispenseInternal,quantity,duration,durunit,freqcode)
    VALUES (${h.sqlString(provider)},${patient},CURDATE(),DATE_ADD(CURDATE(),INTERVAL 30 DAY),CURDATE(),${h.sqlString(`${marker} CONTROL`)},
      ${h.sqlString(`${marker} CONTROL\n1 tab PO daily`)},0,'',NOW(),0,NOW(),0,'30','30','D','OD')`, 'drug');
  const controlState = () => sql.value(`SELECT CONCAT(archived,'|',special,'|',lastUpdateDate) FROM drugs WHERE drugid=${control}`);
  const controlBefore = controlState();
  const rx = await openRx(s, patient);

  for (const [label, dismiss] of [
    ['X', page => page.locator('#carlosModal .btn-close').click()],
    // The preview frame takes the keyboard while it loads; the prescriber's Escape is meant for the
    // dialog, so focus goes to the dialog's title bar first, as a click there would put it.
    ['Escape', async page => {
      await page.locator('#carlosModalLabel').click();
      await page.keyboard.press('Escape');
    }],
  ]) {
    const name = `${marker} ${label}`;
    const rows = () => sql.rows(`SELECT drugid, archived, archived_reason, freqcode, duration FROM drugs
      WHERE demographic_no=${patient} AND customName=${h.sqlString(name)} ORDER BY drugid`);
    let key;
    await s.step(`Save And Print stores the card and the preview closes with ${label}`, async () => {
      key = await stageCustomDrug(rx, name);
      await enterDirections(rx, key, '1 tab PO OD x 30 days');
      const [saved] = await Promise.all([
        rx.waitForResponse(isPost('/rx/WriteScript', 'updateSaveAllDrugs')),
        rx.locator('#saveButton').click(),
      ]);
      h.assert(saved.status() === 200, `Save And Print answered HTTP ${saved.status()}`);
      await expectValue(sql, `SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient} AND customName=${h.sqlString(name)} AND archived=0`,
        '1', 'Save And Print did not store the card');
      const modal = rx.locator('#carlosModal');
      await modal.waitFor({ state: 'visible' });
      await rx.locator('#carlosModal.show').waitFor({ state: 'attached' });
      // Dismiss the preview the prescriber has seen: wait for the print window and its preview to load.
      const script = await waitForFrame(rx, /\/rx\/viewScript\?/);
      await script.waitForLoadState('load');
      const preview = await waitForFrame(rx, /\/rx\/ViewPreview2\?/);
      await preview.waitForLoadState('load');
      await dismiss(rx);
      await modal.waitFor({ state: 'hidden' });
      h.assert(await rx.locator(`#instructions_${key}`).count() === 1, `Dismissing the preview with ${label} lost the acknowledged card`);
    });

    await s.step(`correcting the saved card after dismissal (${label}) updates that medication once and clears the stage`, async () => {
      await enterDirections(rx, key, '1 tab PO BID x 14 days');
      await saveOnly(rx);
      await expectValue(sql, `SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient} AND customName=${h.sqlString(name)}`, '2',
        'The correction did not store a corrected row');
      const [original, corrected] = rows();
      h.assert(original[1] === '1' && original[2] === 'represcribed',
        `The original OD row is archived=${original[1]} (${original[2]}) after the correction`);
      h.assert(corrected[1] === '0' && corrected[3] === 'BID' && corrected[4] === '14',
        `The corrected row is ${JSON.stringify(corrected)}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient} AND customName=${h.sqlString(name)} AND archived=0`) === '1',
        'More than one copy of the corrected medication is active');
      await rx.waitForFunction(() => document.querySelectorAll("[id^='drugName_']").length === 0);
      h.assert(controlState() === controlBefore, 'The unrelated medication changed');
    });
  }
}

if (require.main === module) runWorkflow('rx-preview-return-edit', workflow, { openPatient: true });
module.exports = { workflow };
