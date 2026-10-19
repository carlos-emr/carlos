#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Rx print window ▸ "Remove pharmacy info": the patient's pharmacy leaves the printable prescription and stays off it.
 *
 * WHY THIS CHECK EXISTS. When the patient has a preferred pharmacy, the print window (rx/viewScript) writes the pharmacy's
 * name, address and numbers into the printable preview (rx/ViewPreview2's #pharmInfo) with a "Remove pharmacy info" link
 * under them and a paper-size warning beside the preview. The link is how a prescriber prints a script the patient takes
 * to a pharmacy of their choice. rx-preview-pharmacy covers a reprint with no pharmacy, rx-fax-pharmacy-phone the fax
 * line and gap-encounter-rx-pharmacy-order the preferred list; nothing clicked the link.
 *
 * User path: Schedule ▸ Master Record ▸ E-Chart ▸ Prescriptions "+" (the Rx page) ▸ Custom Drug ▸ Save And Print (the
 * print window over the Rx page) ▸ the preview's "Remove pharmacy info" ▸ Additional Rx Notes (which reloads the preview)
 * ▸ Print.
 * Asserted:
 *   1. Save And Print stores one prescription for the owned patient, and the preview names the preferred pharmacy (its
 *      name, address and fax) with the remove link, while the paper-size warning shows.
 *   2. Remove pharmacy info empties the preview's pharmacy block and hides the warning; the drug stays on the preview.
 *   3. A note typed in Additional Rx Notes reloads the preview with the note and still without the pharmacy.
 *   4. Print prints the preview without the pharmacy (the preview frame's print() is replaced, for this one call, by a
 *      recorder of the text it would print, since a headless browser prints nothing).
 * Fixtures: the owned synthetic patient, one owned pharmacy (pharmacyInfo, name with the run marker) linked to it as its
 * preferred pharmacy, and one custom drug named with the run marker. The pharmacy is in the clinic-wide pharmacy search while
 * the run lasts, so the check must not run concurrently with other checks (live wrapper: EXCLUSIVE=1). Cleanup removes the drug and its script (with any
 * signature row the print stamped), the link and the pharmacy, and asserts them gone.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { stageCustomDrug, clearOwnedPrescriptionRows } = require('./rx-stash-patient-isolation-playwright-checks');
const { viewScriptFrame, noteBodyTracker } = require('./gap-encounter-rx-script-notes-playwright-checks');

const q = h.sqlString;

async function workflow(s) {
  const { sql, marker, patient } = s;
  const drug = `${marker}-pharm`;
  const pharmacy = { name: `${marker} Pharmacy`, address: '9 Fixture Lane', fax: '416-555-0399' };
  let pharmacyId;
  s.cleanup(() => {
    clearOwnedPrescriptionRows(sql, patient, marker);
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient} AND customName LIKE ${q(`${marker}%`)}`) === '0',
      'Owned prescription drugs were not removed');
    sql.execute(`DELETE FROM demographicPharmacy WHERE demographic_no=${patient}`);
    if (pharmacyId) {
      h.assert(sql.value(`SELECT COUNT(*) FROM demographicPharmacy WHERE pharmacyID=${pharmacyId}`) === '0',
        'The owned pharmacy is linked to another patient; retained for inspection');
      sql.execute(`DELETE FROM pharmacyInfo WHERE recordID=${pharmacyId} AND name=${q(pharmacy.name)}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM pharmacyInfo WHERE recordID=${pharmacyId}`) === '0', 'The owned pharmacy was not removed');
    }
  });
  // uid is NOT NULL with no default: strict sql_mode refuses a row that omits it.
  pharmacyId = h.insertId(sql, `INSERT INTO pharmacyInfo(uid,name,address,city,province,postalCode,phone1,fax,email,notes,status)
    VALUES(0,${q(pharmacy.name)},${q(pharmacy.address)},'Fixture City','ON','M1A 1A1','416-555-0398',${q(pharmacy.fax)},
      'pharmacy@example.invalid','','1')`, 'pharmacy');
  sql.execute(`INSERT INTO demographicPharmacy(pharmacyID,demographic_no,status,addDate,preferredOrder,consentToContact)
    VALUES(${pharmacyId},${patient},'1',NOW(),1,0)`);

  const chart = await s.chart();
  const rx = await s.popup(chart, chart.locator('#menuTitleRx a').first(), 'rx-page');
  // The note box's fetch leaves its empty reply unread (net::ERR_ABORTED); consumed once the save is proven.
  const consumeNoteBodies = noteBodyTracker(rx, s.recorder);
  let script;
  let scriptNo;
  const preview = () => {
    const frame = script.childFrames().find(f => /\/rx\/ViewPreview2\?/.test(f.url()));
    h.assert(frame, 'The print window has no preview frame');
    return frame;
  };
  const pharmacyBlock = async () => ((await preview().locator('#pharmInfo').innerText()) || '').trim();
  const warningShown = () => script.locator('#selectedPharmacy').isVisible();

  await s.step('Save And Print shows the preferred pharmacy on the preview with its remove link', async () => {
    const key = await stageCustomDrug(rx, drug);
    await rx.locator(`#instructions_${key}`).fill('Take one tablet daily');
    await rx.locator(`#instructions_${key}`).blur();
    await rx.locator('#saveButton').click();
    script = await viewScriptFrame(rx);
    scriptNo = new URL(script.url()).searchParams.get('scriptId');
    h.assert(/^[1-9]\d*$/.test(scriptNo), 'Save And Print opened no saved script');
    h.assert(sql.value(`SELECT COUNT(*) FROM prescription WHERE script_no=${scriptNo} AND demographic_no=${patient}`) === '1',
      'Save And Print did not store a prescription for the owned patient');
    h.assert(new URL(script.url()).searchParams.get('pharmacyId') === pharmacyId,
      'The print window was not opened for the patient\'s preferred pharmacy');
    await rx.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await preview().locator('#pharmInfo a').waitFor({ state: 'visible', timeout: 30000 });
    const block = await pharmacyBlock();
    h.assert([pharmacy.name, pharmacy.address, pharmacy.fax].every(part => block.includes(part)),
      'The preview\'s pharmacy block does not name the preferred pharmacy with its address and fax');
    h.assert(await warningShown(), 'The paper-size warning is not shown while the pharmacy is on the preview');
  });

  await s.step('Remove pharmacy info empties the pharmacy block and hides the warning, and the drug stays', async () => {
    await preview().locator('#pharmInfo a').click();
    await preview().waitForFunction(() => document.getElementById('pharmInfo').innerText.trim() === '', null, { timeout: 10000 });
    h.assert(!(await warningShown()), 'The paper-size warning is still shown after the pharmacy was removed');
    h.assert((await preview().locator('body').innerText()).includes(drug), 'Removing the pharmacy took the drug off the preview');
  });

  await s.step('a note that reloads the preview keeps the pharmacy off it', async () => {
    const note = `Pharmacy of the patient's choice ${marker}`;
    const reloaded = preview().waitForNavigation({ waitUntil: 'load', timeout: 30000 });
    const saved = rx.waitForResponse(r => /\/rx\/ViewAddRxComment/.test(r.url()) && r.request().method() === 'POST', { timeout: 30000 });
    await script.locator('#additionalNotes').fill(note);
    await script.locator('#additionalNotes').blur();
    h.assert((await saved).ok(), 'rx/ViewAddRxComment was refused');
    await expectValue(sql, `SELECT COUNT(*) FROM prescription WHERE script_no=${scriptNo} AND rx_comments=${q(note)}`, '1',
      'The note was not stored on the prescription');
    await reloaded.catch(() => {});
    await preview().waitForFunction(text => document.body.innerText.includes(text), note, { timeout: 15000 });
    h.assert(await pharmacyBlock() === '', 'Reloading the preview brought the removed pharmacy back');
    await consumeNoteBodies();
  });

  await s.step('Print prints the preview without the pharmacy', async () => {
    await preview().evaluate(() => {
      window.print = () => { window.__printedText = document.body.innerText; };
    });
    await script.locator('#printButton').click();
    const printed = await preview().waitForFunction(() => window.__printedText, null, { timeout: 10000 })
      .then(handle => handle.jsonValue());
    h.assert(printed.includes(drug), 'Print did not print the prescription');
    h.assert(![pharmacy.name, pharmacy.address, pharmacy.fax].some(part => printed.includes(part)),
      'Print printed the pharmacy that was removed');
  });
}

if (require.main === module) runWorkflow('rx-print-remove-pharmacy', workflow, { openPatient: true });
module.exports = { workflow };
