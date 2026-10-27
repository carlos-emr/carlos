#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// rx-indication-patient-registry: a diagnosis from the patient's Dx Registry can be filed as a
// medication's indication (#5000).
// User path: Schedule ▸ Search ▸ Master Record ▸ Prescriptions ▸ the medication's Reason link in the
// profile (ListDrugs.jsp ▸ popupRxReasonWindow ▸ rx/RxReason, SelectReason.jsp) ▸ the patient's 401
// diagnosis under "Patient Dx Registry" ▸ Save (POST rx/RxReason method=addDrugReason).
// The registry link used to put the diagnosis DESCRIPTION into the box Save reads and validates as the
// ICD9 code, so every registry pick was refused as an invalid code. Now:
//   1. the link puts 401 in that box and shows the description beside it;
//   2. Save files exactly one active icd9 401 indication for that drug and patient, and closes;
//   3. a second medication of the same patient gets no indication (the control).
// Fixtures: the owned FAKE- patient, one active 401 dxresearch row, and two marker-named custom drug
// rows inserted directly (no DrugRef data). Cleanup removes the patient's drugReason, dxresearch,
// drugs and prescription rows, and asserts none remain.
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { openRx } = require('./rx-stash-patient-isolation-playwright-checks');

function insertDrug(sql, provider, patient, name) {
  return h.insertId(sql, `INSERT INTO drugs (provider_no,demographic_no,rx_date,end_date,written_date,customName,special,
      archived,archived_reason,create_date,position,lastUpdateDate,dispenseInternal,quantity,duration,durunit,freqcode)
    VALUES (${h.sqlString(provider)},${patient},CURDATE(),DATE_ADD(CURDATE(),INTERVAL 30 DAY),CURDATE(),${h.sqlString(name)},
      ${h.sqlString(`${name}\n1 tab PO daily`)},0,'',NOW(),0,NOW(),0,'30','30','D','OD')`, 'drug');
}

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  s.cleanup(() => {
    sql.execute(`DELETE FROM drugReason WHERE demographicNo=${patient}; DELETE FROM dxresearch WHERE demographic_no=${patient};
      DELETE FROM drugs WHERE demographic_no=${patient}; DELETE FROM prescription WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM drugReason WHERE demographicNo=${patient})
      + (SELECT COUNT(*) FROM dxresearch WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient})`) === '0', 'Owned indication rows were not removed');
  });
  h.assert(sql.value(`SELECT COUNT(*) FROM icd9 WHERE icd9='401'`) === '1', 'The ICD9 reference data has no 401');
  const description = sql.value(`SELECT TRIM(description) FROM icd9 WHERE icd9='401'`);
  sql.execute(`INSERT INTO dxresearch (demographic_no,start_date,update_date,status,dxresearch_code,coding_system,association,providerNo)
    VALUES (${patient},CURDATE(),NOW(),'A','401','icd9',0,${h.sqlString(provider)})`);
  const drug = insertDrug(sql, provider, patient, `${marker} INDICATED`);
  const control = insertDrug(sql, provider, patient, `${marker} CONTROL`);
  const rx = await openRx(s, patient);
  const reasonLink = rx.locator(`a[onclick="popupRxReasonWindow(${patient},${drug});"]`);
  await reasonLink.waitFor({ state: 'visible' });
  const popup = await s.popup(rx, reasonLink, 'rx-indication');
  await popup.locator('#jsonDxSearch').waitFor({ state: 'visible' });

  await s.step('the Patient Dx Registry link puts the code in the box Save reads and shows its description', async () => {
    await popup.locator('a[onclick^="assignPatientDxLink(\'401\'"]').click();
    const box = await popup.locator('#jsonDxSearch').inputValue();
    h.assert(box === '401', `The indication box holds ${JSON.stringify(box)} instead of the code 401`);
    const shown = (await popup.locator('#jsonDxSearchDescription').innerText()).trim();
    h.assert(shown === description, `The description beside the box is ${JSON.stringify(shown)}, not ${JSON.stringify(description)}`);
  });

  await s.step('typing over a picked diagnosis drops its description and tooltip until one is picked again', async () => {
    const box = popup.locator('#jsonDxSearch');
    h.assert((await box.getAttribute('title') || '').includes(description), 'The picked diagnosis is not in the box tooltip');
    await box.press('End');
    await box.press('Backspace');
    const shown = (await popup.locator('#jsonDxSearchDescription').innerText()).trim();
    const title = await box.getAttribute('title');
    h.assert(shown === '' && !title, `After retyping the box still shows ${JSON.stringify(shown)} with tooltip ${JSON.stringify(title)}`);
    await popup.locator('a[onclick^="assignPatientDxLink(\'401\'"]').click();
    h.assert(await box.inputValue() === '401', 'Picking the diagnosis again did not restore the code');
  });

  await s.step('the Patient Dx Registry link fills and saves exactly that drug indication', async () => {
    const [saved] = await Promise.all([
      popup.waitForResponse(r => r.request().method() === 'POST' && h.pathOnly(r.url()).endsWith('/rx/RxReason')),
      popup.locator('#saveRxReason').click(),
    ]);
    h.assert(saved.status() === 200, `Save answered HTTP ${saved.status()}`);
    const body = new URLSearchParams(saved.request().postData() || '');
    h.assert(body.get('method') === 'addDrugReason' && body.get('jsonDxSearch') === '401' && body.get('drugId') === drug,
      `Save posted ${JSON.stringify(Object.fromEntries(body))}`);
    await expectValue(sql, `SELECT COUNT(*) FROM drugReason WHERE demographicNo=${patient}`, '1',
      'Save did not file exactly one indication for the patient');
    const [row] = sql.rows(`SELECT drugId, codingSystem, code, archivedFlag FROM drugReason WHERE demographicNo=${patient}`);
    h.assert(JSON.stringify(row) === JSON.stringify([drug, 'icd9', '401', '0']), `The filed indication is ${JSON.stringify(row)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM drugReason WHERE drugId=${control}`) === '0', 'The control medication gained an indication');
  });
}

if (require.main === module) runWorkflow('rx-indication-patient-registry', workflow, { openPatient: true });
module.exports = { workflow };
