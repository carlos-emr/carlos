#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Audit trail of the E-Chart clinical write modules: allergy, prevention, prescription (wave 7 sweep `audit-log`).
 *
 * User path: Schedule > Search > Master Record > E-Chart >
 *   Allergies "+" (rx/showAllergy popup) > type a name > Custom Allergy (confirm) > reaction > Add Allergy; the
 *   list's archive link (confirm);
 *   Preventions "+" (ViewPreventionIndex popup) > Fluzone > the prevention editor > Refused > Save; reopen > Delete;
 *   Prescriptions (rx module) > type a name > Custom Drug (confirm) > instructions > Save Only; the row's Discon >
 *   reason > Discontinue.
 *
 * Asserts the clinical rows (allergies, preventions, drugs, prescription) after each write, and, scoped to the
 * owned patient: each write is audited with the provider, the client address, demographic_no = the patient and
 * contentId = the written row (allergy id, prescription script no); adding or deleting a prevention is audited at
 * all; and no audit row carries the clinical text typed in (the allergy name and reaction, the drug name and its
 * instructions, the prevention comment) nor a Java object dump. Every expectation is evaluated and the violated
 * ones are reported together in the last step.
 *
 * Fixtures: the harness's owned synthetic patient; allergies, preventions(+Ext), drugs, prescription and
 * DigitalSignature rows created through the UI for that patient and removed by patient id; the audit rows scoped
 * to the patient. Cleanup asserts nothing remains.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { auditProbe, phiLeaks, incomplete, label } = require('./lib/audit-log-helpers');
const { stageCustomDrug, clearOwnedPrescriptionRows } = require('./rx-stash-patient-isolation-playwright-checks');

async function workflow(s) {
  const { sql, patient, marker, provider } = s;
  const probe = auditProbe({ sql, patient });
  const defects = [];
  const expect = (ok, message) => { if (!ok) defects.push(message); };
  const drugName = `${marker}-drug`;
  const instructions = `Take one tablet daily ${marker}`;
  const allergyName = marker.slice(-16).toUpperCase();
  s.cleanup(() => {
    sql.execute(`DELETE FROM allergies WHERE demographic_no=${patient}`);
    sql.execute(`DELETE x FROM preventionsExt x JOIN preventions p ON p.id=x.prevention_id WHERE p.demographic_no=${patient};
      DELETE FROM preventions WHERE demographic_no=${patient}`);
    const drugIds = sql.rows(`SELECT drugid FROM drugs WHERE demographic_no=${patient}`).map(row => row[0]);
    const notes = sql.rows(`SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`).map(row => row[0]);
    h.assert([...drugIds, ...notes].every(id => /^[1-9]\d*$/.test(id)), 'Owned row id is invalid');
    if (notes.length) {
      sql.execute(`DELETE FROM casemgmt_note_link WHERE note_id IN (${notes.join(',')});
        DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes.join(',')});
        DELETE FROM casemgmt_note WHERE demographic_no=${patient} AND note_id IN (${notes.join(',')})`);
    }
    sql.execute(`DELETE FROM drugReason WHERE demographicNo=${patient}`);
    if (drugIds.length) sql.execute(`DELETE FROM partial_date WHERE table_name=2 AND table_id IN (${drugIds.join(',')})`);
    clearOwnedPrescriptionRows(sql, patient, marker);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM preventions WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM prescription WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})`) === '0', 'Owned chart-module rows were not removed');
    probe.cleanup();
  });
  const writes = since => probe.since(since, `action NOT LIKE 'read%' AND action NOT LIKE '%Manager.get%' AND action NOT LIKE '%Manager.find%' AND action NOT LIKE '%search%'
    AND action NOT LIKE 'DemographicManager.%' AND action NOT LIKE 'PatientConsentManager.%'`).filter(r => r.provider === provider || r.provider === null);
  const noText = (rows, needles, what) => {
    const leaks = phiLeaks(rows, needles);
    expect(!leaks.length, `${what}: the audit row carries the clinical text typed in (${leaks.join(', ')})`);
    expect(!rows.some(r => /\$[A-Za-z]*@[0-9a-f]+|\.model\.[A-Z]/.test(r.data || '')), `${what}: the audit row carries a Java object dump instead of a description`);
  };
  async function judge(what, since, { wantAction, contentId } = {}) {
    await probe.settle(2500);
    const rows = writes(since).filter(r => !wantAction || wantAction.test(`${r.action}/${r.content}`));
    expect(rows.length >= 1, `${what} wrote no audit row`);
    for (const r of rows) {
      const problems = incomplete([r], { provider, patient });
      expect(!problems.length, `${what}: the audit row ${label(r)} is incomplete (${problems.join(', ')})`);
    }
    // The action may write companion rows (discontinuing also files a reason note), so the written row must be named
    // by at least one of them rather than by each.
    if (contentId && rows.length) {
      expect(rows.some(r => r.contentId === String(contentId)),
        `${what}: no audit row names the written row (${rows.map(r => label(r)).join(', ')})`);
    }
    return rows;
  }

  const chart = await s.chart();
  const allergyPage = await s.popup(chart, chart.locator('a[onclick*="showAllergy"]').first(), 'allergy-list');
  let allergyId;
  await s.step('Allergies: a custom allergy is added and then archived (audit rows observed)', async () => {
    const before = probe.mark();
    const form = allergyPage.locator('#RxAddAllergyForm');
    await allergyPage.locator('#searchString').fill(allergyName);
    await h.withExpectedDialogs(allergyPage, () => allergyPage.locator('input[value="Custom Allergy"]').click());
    await form.waitFor({ state: 'visible' });
    await form.locator('#reactionDescription').fill(`${marker} hives`);
    await form.locator('[name="nonDrug"]').selectOption('on');
    await form.locator('[name="severityOfReaction"]').selectOption('3');
    await form.locator('input[type="submit"]').click();
    await expectValue(sql, `SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient}`, '1', 'The allergy was not saved');
    allergyId = sql.value(`SELECT allergyid FROM allergies WHERE demographic_no=${patient}`);
    await allergyPage.locator(`#allergy_${allergyId}`).waitFor({ state: 'visible' });
    const added = await judge('Adding the allergy', before, { wantAction: /^add\/allergy$/, contentId: allergyId });
    expect(added.length === 1, `Adding the allergy wrote ${added.length} add/allergy rows, expected 1`);
    noText(added, [allergyName, `${marker} hives`], 'Adding the allergy');
    const mid = probe.mark();
    await h.withExpectedDialogs(allergyPage, () => allergyPage.locator(`#allergy_${allergyId} a.deleteAllergyLink`).click());
    await expectValue(sql, `SELECT archived FROM allergies WHERE allergyid=${allergyId}`, '1', 'The allergy was not archived');
    const removed = await judge('Archiving the allergy', mid, { contentId: allergyId });
    noText(removed, [allergyName, `${marker} hives`], 'Archiving the allergy');
  });

  const index = await s.popup(chart, chart.locator('a[onclick*="ViewPreventionIndex"]').first(), 'prevention-index');
  let preventionId;
  await s.step('Preventions: a refused Fluzone is recorded and then deleted (audit rows observed)', async () => {
    const before = probe.mark();
    await index.locator('#immunization').fill('Fluzone');
    let editor = await s.popup(index, index.locator('#immunization_choices [class*="item"], #immunization_choices div, #immunization_choices li').first(), 'prevention-editor');
    await editor.locator('[name="given"][value="refused"]').check();
    await editor.locator('#prevDate').fill('2026-01-02');
    await editor.locator('[name="comments"]').fill(`${marker} declined`);
    await editor.locator('input[type="submit"][name="action"]').first().click();
    if (!editor.isClosed()) await editor.waitForEvent('close');
    await expectValue(sql, `SELECT COUNT(*) FROM preventions WHERE demographic_no=${patient} AND deleted=0`, '1', 'The prevention was not recorded');
    preventionId = sql.value(`SELECT id FROM preventions WHERE demographic_no=${patient} AND deleted=0`);
    const added = await judge('Recording the prevention', before);
    noText(added, [`${marker} declined`], 'Recording the prevention');
    const mid = probe.mark();
    const link = index.locator(`[onclick*="ViewAddPreventionData"][onclick*="id=${preventionId}"]`).first();
    await link.waitFor({ state: 'visible' });
    editor = await s.popup(index, link, 'prevention-editor');
    await editor.locator('input[name="delete"]').click();
    await expectValue(sql, `SELECT deleted FROM preventions WHERE id=${preventionId}`, '1', 'The prevention was not deleted');
    await judge('Deleting the prevention', mid);
  });

  let scriptNo;
  let drugId;
  const rx = await s.popup(chart, chart.locator('#menuTitleRx a').first(), 'rx-page');
  await s.step('Prescriptions: a custom drug is saved and then discontinued (audit rows observed)', async () => {
    const before = probe.mark();
    const key = await stageCustomDrug(rx, drugName);
    await rx.locator(`#instructions_${key}`).fill(instructions);
    await rx.locator(`#instructions_${key}`).blur();
    const saved = rx.waitForResponse(r => r.request().method() === 'POST' && /\/rx\/WriteScript\?[^#]*parameterValue=updateSaveAllDrugs/.test(r.url()), { timeout: 30000 });
    await rx.locator('#saveOnlyButton').click();
    h.assert((await saved).status() === 200, 'Save Only was refused');
    await expectValue(sql, `SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient} AND customName=${h.sqlString(drugName)}`, '1', 'The drug was not saved');
    [drugId, scriptNo] = sql.rows(`SELECT drugid,script_no FROM drugs WHERE demographic_no=${patient}`)[0];
    const added = await judge('Saving the prescription', before, { wantAction: /^add\/prescription$/, contentId: scriptNo });
    expect(added.length === 1, `Saving the prescription wrote ${added.length} add/prescription rows, expected 1`);
    noText(added, [drugName, instructions], 'Saving the prescription');
    const mid = probe.mark();
    await rx.locator(`#discont_${drugId}`).click();
    const panel = rx.locator('#discontinueUI');
    await panel.waitFor({ state: 'visible' });
    await rx.locator('#disReason').selectOption('doseChange');
    await rx.locator('#disComment').fill(`${marker} stopped`);
    await Promise.all([
      rx.waitForResponse(r => r.request().method() === 'POST' && /\/rx\/deleteRx/.test(r.url()) && /parameterValue=Discontinue/.test(r.url())),
      panel.locator('input[onclick*="Discontinue2("]').click(),
    ]);
    await expectValue(sql, `SELECT archived FROM drugs WHERE drugid=${drugId}`, '1', 'The drug was not discontinued');
    // The discontinue row names the drug (RxDeleteRx2Action logs drug.getId()), not the script the add row names.
    const stopped = await judge('Discontinuing the prescription', mid, { contentId: drugId });
    noText(stopped, [drugName, instructions, `${marker} stopped`], 'Discontinuing the prescription');
  });

  await s.step('every expectation of the chart-module audit trail held', async () => {
    h.assert(!defects.length, `Audit-trail defects on the chart modules:\n  - ${[...new Set(defects)].join('\n  - ')}`);
  });
}

if (require.main === module) runWorkflow('audit-log-chart-modules', workflow);
module.exports = { workflow };
