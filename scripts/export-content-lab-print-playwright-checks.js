#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Inbox ▸ lab ▸ Print: the lab PDF's content against the HL7 result that was received.
 * User path: Schedule ▸ Inbox ▸ the lab's row (Inboxhub) ▸ the lab display popup ▸ Print (lab/CA/ALL/PrintPDF).
 * lab-line-break-rendering proves the \.br\ marker; nothing read the results themselves out of the printed report,
 * which is the copy that goes to the patient, a specialist or a coroner.
 *
 * Asserts (pdftotext): the PDF names the patient and the accession; each result row carries its test name, value,
 * units and reference range as received (including µmol/L and x10^9/L); the abnormal rows are flagged and the normal
 * row is not; the OBX comment and the NTE comment print with their accents (Spécimen hémolysé); and a comment
 * containing characters outside Latin-1 (≥, ≤, Ł) prints them with embedded fonts.
 * Fixtures: one synthetic PATHL7 ORU^R01 seeded into hl7TextMessage/hl7TextInfo with provider and patient routing for
 * the owned FAKE-PW patient (the lab-line-break fixture shape); cleanup deletes exactly the rows of this run's lab id
 * and asserts it. Needs pdftotext and pdffonts. Nothing leaves the host.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const x = require('./lib/export-content-helpers');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');

const q = h.sqlString;
const SERVICE = 'CARLOSEXPORT';

function message(marker, accession, stamp) {
  return [
    `MSH|^~\\&|PATHL7|CW|CARLOS|TEST|${stamp}||ORU^R01|EXPORT${stamp}|P|2.3|||ER|AL`,
    `PID||9999999999|9999999999||${marker}^Zoë||19800101|F`,
    `ORC|RE||${accession}|||||||||99999^FAKE-ORDERING^DOC`,
    `OBR|1||${accession}|CHEM^Chemistry||${stamp}|${stamp}|||||||${stamp}||99999^FAKE-ORDERING^DOC||||||${stamp}||CHEM4|F|||99999^FAKE-ORDERING^DOC`,
    `OBX|1|NM|2345-7^Glucose Random||5.2|mmol/L|3.3-7.7|N|||F|||${stamp}`,
    `OBX|2|NM|718-7^Hemoglobin||98|g/L|120-160|L|||F|||${stamp}`,
    `OBX|3|NM|777-3^Platelets||450|10*9/L|150-400|H|||F|||${stamp}`,
    `OBX|4|NM|2160-0^Creatinine||88|µmol/L|45-90|N|||F|||${stamp}`,
    `OBX|5|ST|XXX-0001^Specimen Quality||Spécimen hémolysé — recoller||||||F|||${stamp}`,
    `NTE|1|L|Résultat à confirmer pour Renée`,
    `OBX|6|ST|XXX-0002^Interpretation||Value ≥ 5 µg/L and ≤ 9, Łódź||||||F|||${stamp}`,
  ].join('\r');
}

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const scratch = x.scratchDir();
  const owned = { labNo: null };
  s.cleanup(() => require('node:fs').rmSync(scratch, { recursive: true, force: true }));
  s.cleanup(() => {
    if (!owned.labNo) return;
    const lab = Number(owned.labNo);
    h.assert(sql.value(`SELECT COUNT(*) FROM hl7TextMessage WHERE lab_id=${lab} AND serviceName=${q(SERVICE)}`) === '1', 'Owned lab identity changed');
    sql.execute(`DELETE FROM patientLabRouting WHERE lab_no=${lab} AND lab_type='HL7'; DELETE FROM providerLabRouting WHERE lab_no=${lab} AND lab_type='HL7';
      DELETE FROM hl7TextInfo WHERE lab_no=${lab}; DELETE FROM hl7TextMessage WHERE lab_id=${lab} AND serviceName=${q(SERVICE)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM patientLabRouting WHERE lab_no=${lab} AND lab_type='HL7')
      + (SELECT COUNT(*) FROM providerLabRouting WHERE lab_no=${lab} AND lab_type='HL7')
      + (SELECT COUNT(*) FROM hl7TextInfo WHERE lab_no=${lab}) + (SELECT COUNT(*) FROM hl7TextMessage WHERE lab_id=${lab})`) === '0', 'Owned lab rows remain');
  });
  const accession = `EXPORT-${marker.slice(-10)}`;
  const now = new Date();
  const stamp = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}100000`;
  const base64 = Buffer.from(message(marker, accession, stamp), 'utf8').toString('base64');
  const labNo = sql.value(`INSERT INTO hl7TextMessage (fileUploadCheck_id, message, type, serviceName, created)
    VALUES (0, ${q(base64)}, 'PATHL7', ${q(SERVICE)}, NOW()); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(labNo), 'The fixture lab did not insert');
  owned.labNo = labNo;
  sql.execute(`INSERT INTO hl7TextInfo (lab_no, sex, health_no, result_status, final_result_count, obr_date, priority, requesting_client,
      discipline, last_name, first_name, report_status, accessionNum, filler_order_num, sending_facility)
    VALUES (${labNo}, 'F', '9999999999', 'A', 6, NOW(), 'R', 'FAKE-ORDERING, DOC', 'CHEM', ${q(marker)}, 'Zoë', 'F', ${q(accession)}, ${q(accession)}, 'CW');
    INSERT INTO providerLabRouting (provider_no, lab_no, status, lab_type) VALUES (${q(provider)}, ${labNo}, 'N', 'HL7');
    INSERT INTO patientLabRouting (demographic_no, lab_no, lab_type, created) VALUES (${patient}, ${labNo}, 'HL7', NOW())`);

  let lab;
  await s.step('the Inbox lists the lab and its row opens the lab display', async () => {
    const { page: inbox } = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('a#inboxLink').first(),
      { context: s.context, recorder: s.recorder, label: 'lab-print-inbox', timeout: 30000 });
    await inbox.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
    const row = inbox.locator(`tr[data-segment-id="${labNo}"][data-lab-type="HL7"]`).first();
    await row.waitFor({ state: 'attached', timeout: 45000 });
    lab = await s.popup(inbox, row.locator('a[onclick*="reportWindow"]').first(), 'lab-display');
    await lab.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    h.assert((await lab.locator('body').innerText()).includes(accession), 'The lab display is not the owned lab');
  });

  let text;
  let contentOrderText;
  await s.step('Print downloads the lab PDF', async () => {
    const button = lab.locator('input[type="button"][value*="Print"]').first();
    const file = await x.saveDownload(lab, scratch, () => button.click(), { route: /\/lab\/CA\/ALL\/PrintPDF$/ });
    h.assert(file.status === 200, `Print answered HTTP ${file.status}`);
    text = x.pdfText(file.file, { layout: true });
    contentOrderText = x.pdfText(file.file, { raw: true });
    x.assertEmbeddedFonts(file.file);
  });
  const sq = value => x.squash(value);

  await s.step('the PDF names the patient and the accession and lists every result row with its units and range', async () => {
    // Content order keeps wrapped table cells together and preserves identifier hyphens.
    // Layout order can interleave the neighboring column between two accession lines.
    h.assert(sq(contentOrderText).includes(sq(marker)) && contentOrderText.includes('Zoë'), 'The PDF does not name the patient');
    h.assert(sq(contentOrderText).includes(sq(accession)), 'The PDF does not carry the accession number');
    const rows = [['Glucose', '5.2', 'mmol/L', '3.3-7.7'], ['Hemoglobin', '98', 'g/L', '120-160'], ['Platelets', '450', '10*9/L', '150-400'],
      ['Creatinine', '88', 'µmol/L', '45-90']];
    const lines = text.split('\n');
    for (const [name, value, unit, range] of rows) {
      const at = lines.findIndex(l => l.includes(name));
      h.assert(at >= 0, `The PDF has no row for ${name}`);
      const joined = sq(lines.slice(at, at + 12).join(' '));
      for (const part of [value, unit, range]) h.assert(joined.includes(sq(part)), `The ${name} row lacks "${part}" (reads: ${lines.slice(at, at + 6).join(' | ').slice(0, 300)})`);
    }
  });

  await s.step('the abnormal results are flagged and the normal ones are not', async () => {
    const flagOf = (name, value) => {
      const tokens = (text.split('\n').find(l => l.includes(name)) || '').trim().split(/\s+/);
      return tokens[tokens.indexOf(value) + 1];
    };
    h.assert(flagOf('Hemoglobin', '98') === 'L', 'The low hemoglobin is not flagged L in the PDF');
    h.assert(flagOf('Platelets', '450') === 'H', 'The high platelet count is not flagged H in the PDF');
    h.assert(flagOf('Glucose', '5.2') === 'N', 'The normal glucose is not flagged N in the PDF');
  });

  await s.step('the result comment and the NTE comment print with their accents', async () => {
    h.assert(sq(contentOrderText).includes(sq('Spécimen hémolysé')), 'The OBX comment lost its accents in the PDF');
    h.assert(sq(contentOrderText).includes(sq('Résultat à confirmer pour Renée')), 'The NTE comment lost its accents in the PDF');
  });

  await s.step('a comment with characters outside Latin-1 prints them', async () => {
    const lost = ['≥', '≤', 'Ł'].filter(c => !text.includes(c));
    h.assert(!lost.length, `The lab PDF drops ${lost.map(c => `U+${c.codePointAt(0).toString(16).toUpperCase().padStart(4, '0')}`).join(', ')} from a result (the PDF font has no glyph)`);
  });
}

if (require.main === module) {
  runWorkflow('export-content-lab-print', workflow, { openPatient: true, openMaster: false, preflight: () => x.requirePoppler('pdftotext', 'pdffonts') });
}
module.exports = { workflow };
