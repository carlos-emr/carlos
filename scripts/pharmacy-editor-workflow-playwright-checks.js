#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Requires _rx and _rx.editPharmacy write access on an isolated synthetic login.
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

async function workflow(s) {
  const name = `${s.marker} Pharmacy`;
  let id;
  s.cleanup(() => {
    const ids = s.sql.rows(`SELECT recordID FROM pharmacyInfo WHERE name=${h.sqlString(name)}`).map(row => row[0]);
    for (const owned of ids) {
      h.assert(/^[1-9]\d*$/.test(owned), 'Invalid owned pharmacy ID');
      h.assert(s.sql.value(`SELECT COUNT(*) FROM demographicPharmacy WHERE pharmacyID=${owned} AND demographic_no<>${s.patient}`) === '0',
        'Owned pharmacy is linked to another patient; retain it for inspection');
      s.sql.execute(`DELETE FROM demographicPharmacy WHERE pharmacyID=${owned} AND demographic_no=${s.patient};
        DELETE FROM pharmacyInfo WHERE recordID=${owned} AND name=${h.sqlString(name)}`);
      h.assert(s.sql.value(`SELECT COUNT(*) FROM pharmacyInfo WHERE recordID=${owned}`) === '0', 'Owned pharmacy cleanup failed');
    }
  });
  const rx = await s.popup(s.master, s.master.locator('a[onclick*="rx/choosePatient"]').first(), 'pharmacy-editor-rx');
  await rx.locator('a[href$="/rx/managePharmacy"]').click();
  await rx.waitForURL(/\/rx\/managePharmacy(?:\?|$)/);
  h.assert(await rx.locator('#demographicNo').inputValue() === s.patient, 'Pharmacy editor lost its owned patient');
  const frame = () => rx.frameLocator('#pharmacyModalIframe');
  const row = () => rx.locator(`tr.pharmacyItem[pharmId="${id}"]`);
  const saveButton = () => frame().locator('input[onclick="savePharmacy();"]');
  const association = () => `SELECT COUNT(*) FROM demographicPharmacy WHERE demographic_no=${s.patient} AND pharmacyID=${id} AND status='1'`;
  async function waitForModalShown() {
    // Bootstrap ignores hide() while its opening transition is still running.
    // Observe the real DOM/animations so a fast iframe load cannot race Close.
    await rx.waitForFunction(() => {
      const modal = document.getElementById('pharmacyModal');
      return modal.classList.contains('show')
        && modal.getAnimations({ subtree: true }).every(animation => animation.playState !== 'running');
    });
  }
  async function waitForLoaded(field, value) {
    // Read the real iframe DOM while its AJAX loader runs; do not invoke application handlers.
    await rx.waitForFunction(({ field, value }) => {
      const doc = document.getElementById('pharmacyModalIframe').contentDocument;
      return doc && doc.getElementById(field) && doc.getElementById(field).value === value;
    }, { field, value });
  }
  await s.step('create a clinic pharmacy through the modal editor', async () => {
    await rx.locator('a[onclick="addPharmacy();"]').click();
    await frame().locator('#pharmacyFax').fill('416-555-0100');
    const dialogs = await h.withExpectedDialogs(rx, () => saveButton().click());
    h.assert(dialogs.length === 1 && dialogs[0].type === 'alert', 'Missing pharmacy name did not trigger validation');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM pharmacyInfo WHERE name=${h.sqlString(name)}`) === '0', 'Invalid form created a pharmacy');
    const values = { pharmacyName: name, pharmacyAddress: '123 Fixture Lane', pharmacyCity: 'Fixture City', pharmacyProvince: 'ON',
      pharmacyPostalCode: 'M1A 1A1', pharmacyPhone1: '416-555-0200', pharmacyEmail: 'fixture@example.invalid', pharmacyNotes: `${s.marker} initial note` };
    for (const [field, value] of Object.entries(values)) await frame().locator(`#${field}`).fill(value);
    await saveButton().click();
    await expectValue(s.sql, `SELECT COUNT(*) FROM pharmacyInfo WHERE name=${h.sqlString(name)} AND status='1'`, '1', 'Pharmacy add did not persist');
    id = s.sql.value(`SELECT recordID FROM pharmacyInfo WHERE name=${h.sqlString(name)}`);
    h.assert(/^[1-9]\d*$/.test(id), 'Pharmacy add returned no owned ID');
    await row().waitFor();
    h.assert((await row().locator('.address').innerText()).trim() === '123 Fixture Lane', 'Created address is missing from the list');
  });
  const notes = `${s.marker} keep A+B, %20, & and 'quotes'`;
  await s.step('edit and reopen the pharmacy without losing field values', async () => {
    await row().locator('a[onclick*="editPharmacy"]').click();
    await waitForLoaded('pharmacyId', id);
    h.assert(await frame().locator('#pharmacyAddress').inputValue() === '123 Fixture Lane', 'Edit did not load the saved address');
    await frame().locator('#pharmacyAddress').fill('456 Updated Lane');
    await frame().locator('#pharmacyNotes').fill(notes);
    const dialogs = await h.withExpectedDialogs(rx, async () => {
      await saveButton().click();
      await expectValue(s.sql, `SELECT notes FROM pharmacyInfo WHERE recordID=${id}`, notes, 'Edited pharmacy notes changed or did not persist');
      await row().locator('.address').filter({ hasText: '456 Updated Lane' }).waitFor();
    }, { promptText: 'yes' });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'prompt', 'Clinic-wide pharmacy edit was not confirmed');
    await row().locator('a[onclick*="editPharmacy"]').click();
    await waitForLoaded('pharmacyNotes', notes);
    h.assert(await frame().locator('#pharmacyAddress').inputValue() === '456 Updated Lane', 'Reopened address did not persist');
    await waitForModalShown();
    await rx.locator('#pharmacyModal button[data-bs-dismiss="modal"]').click();
    await rx.locator('#pharmacyModal').waitFor({ state: 'hidden' });
  });
  await s.step('link, unlink and relink the pharmacy for the owned patient', async () => {
    await row().locator('.pharmacyName').click();
    await expectValue(s.sql, association(), '1', 'Preferred pharmacy was not linked');
    await rx.locator('#preferredList').filter({ hasText: name }).waitFor();
    h.assert(await rx.locator('#preferredList .prefUnlink').count() === 1, 'Unexpected patient pharmacy associations');
    await rx.locator('#preferredList .prefUnlink').click();
    await expectValue(s.sql, association(), '0', 'Preferred pharmacy was not unlinked');
    await rx.locator('#preferredList .prefUnlink').waitFor({ state: 'hidden' });
    await row().locator('.pharmacyName').click();
    await expectValue(s.sql, association(), '1', 'Preferred pharmacy was not relinked');
    await rx.locator('#preferredList').filter({ hasText: name }).waitFor();
  });
  await s.step('deactivate the pharmacy and remove its active patient association', async () => {
    const dialogs = await h.withExpectedDialogs(rx, async () => {
      await row().locator('.deletePharm').click();
      await expectValue(s.sql, `SELECT status FROM pharmacyInfo WHERE recordID=${id}`, '0', 'Pharmacy was not deactivated');
      await row().waitFor({ state: 'hidden' });
    }, { promptText: 'yes' });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'prompt', 'Clinic-wide pharmacy deletion was not confirmed');
    await expectValue(s.sql, association(), '0', 'Deactivated pharmacy remains active for the patient');
    await rx.reload();
    h.assert(await row().count() === 0, 'Deactivated pharmacy returned after reload');
    h.assert(!(await rx.locator('#preferredList').innerText()).includes(name), 'Deactivated pharmacy remains in the preferred list');
  });
}
if (require.main === module) runWorkflow('pharmacy-editor-workflow', workflow);
module.exports = { workflow };
