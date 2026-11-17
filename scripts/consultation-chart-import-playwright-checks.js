#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// consultation-chart-import: the consultation form's Import menus copy the chart into the referral.
// User path: Schedule ▸ Search ▸ Master Record ▸ E-Chart ▸ Consultations "+" (a new request) ▸
//   1. Clinical Information ▸ Import ▸ each chart history in turn (Social, Family, Medical History,
//      Ongoing Concerns, Other Meds, Reminders, Risk Factors): each fills in that section's note,
//      below what is already there;
//   2. Concurrent Problems ▸ Import ▸ Medical History brings an edited note's latest text only, and
//      Ongoing Concerns leaves out an archived note;
//   3. Current Medications ▸ Import ▸ Long Term Medications lists the long-term drug and not the
//      short-term one;
//   4. Allergies ▸ Import Allergies fills in the patient's allergy and its reaction (the import
//      capitalises each word, like the medication imports, so text is compared case-insensitively).
// Each import is a POST to oscarConsultationRequest/consultationClinicalData. The form is never
// submitted, so the check also asserts that no referral was saved.
// consultation-edit-status covers Current Medications ▸ Import ▸ Active Medications; no check uses the
// chart-history items or Long Term Medications.
// Fixtures: the owned FAKE- patient; one CPP note per history (seeded by SQL like
// gap-clinical-chart-print-scope), a second revision of the Medical History note and an archived
// Ongoing Concerns note; a long-term and a short-term current drug; one allergy. Cleanup deletes the patient's
// notes, issues, drugs and any referral, and asserts none remain. If the install auto-imports
// histories into a new request (CONSULTATION_AUTO_INCLUDE_*), the check clears the box first.
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

const q = h.sqlString;
const HISTORIES = ['SocHistory', 'FamHistory', 'MedHistory', 'Concerns', 'OMeds', 'Reminders', 'RiskFactors'];
const lines = text => text.split('\n').map(line => line.trim()).filter(Boolean);

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  s.cleanup(() => {
    const owned = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
    sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${owned});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${owned});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${owned});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient};
      DELETE FROM casemgmt_issue WHERE demographic_no=${patient};
      DELETE FROM casemgmt_cpp WHERE demographic_no=${patient};
      DELETE FROM eChart WHERE demographicNo=${patient};
      DELETE FROM drugs WHERE demographic_no=${patient};
      DELETE FROM allergies WHERE demographic_no=${patient};
      DELETE x FROM consultationRequestExt x JOIN consultationRequests r ON r.requestId=x.requestId WHERE r.demographicNo=${patient};
      DELETE FROM consultationRequests WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM casemgmt_issue WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM consultationRequests WHERE demographicNo=${patient})`) === '0', 'Owned import fixtures were not removed');
  });

  const program = sql.value(`SELECT id FROM program WHERE name='OSCAR' ORDER BY id LIMIT 1`);
  const role = sql.value(`SELECT role_id FROM program_provider WHERE provider_no=${q(provider)} AND program_id=${program || 0} LIMIT 1`);
  h.assert(program && role, 'The default OSCAR program or the test provider\'s role in it is missing');
  const note = (text, { uuid = 'UUID()', archived = 0, when = "'2025-06-15 09:00:00'" } = {}) => sql.value(`INSERT INTO casemgmt_note
      (update_date,observation_date,demographic_no,provider_no,note,signed,signing_provider_no,encounter_type,program_no,
       reporter_caisi_role,history,uuid,locked,archived)
    VALUES (NOW(),${when},${patient},${q(provider)},${q(text)},1,${q(provider)},'',${q(program)},${q(role)},${q(text)},${uuid},'0',${archived});
    SELECT LAST_INSERT_ID()`);
  const file = (code, noteId) => {
    const issue = sql.value(`SELECT issue_id FROM issue WHERE code=${q(code)} LIMIT 1`);
    h.assert(/^[1-9]\d*$/.test(issue || ''), `The ${code} CPP issue is missing`);
    let cmi = sql.value(`SELECT id FROM casemgmt_issue WHERE demographic_no=${patient} AND issue_id=${issue}`);
    if (!cmi) {
      cmi = sql.value(`INSERT INTO casemgmt_issue (demographic_no,issue_id,acute,certain,major,resolved,program_id,type,update_date)
        VALUES (${patient},${issue},0,0,0,0,${program},'doctor',NOW()); SELECT LAST_INSERT_ID()`);
    }
    sql.execute(`INSERT INTO casemgmt_issue_notes (id,note_id) VALUES (${cmi},${noteId})`);
  };
  const text = Object.fromEntries(HISTORIES.map(code => [code, `${marker} FAKE ${code} note`]));
  const ids = {};
  for (const code of HISTORIES) {
    ids[code] = note(text[code]);
    file(code, ids[code]);
  }
  // A later revision of the Medical History note (same uuid, higher id) and an archived concern.
  const editedMedHistory = `${marker} FAKE MedHistory note, edited`;
  const uuid = sql.value(`SELECT uuid FROM casemgmt_note WHERE note_id=${ids.MedHistory}`);
  file('MedHistory', note(editedMedHistory, { uuid: q(uuid), when: "'2025-06-16 09:00:00'" }));
  const archivedConcern = `${marker} FAKE archived concern`;
  file('Concerns', note(archivedConcern, { archived: 1 }));
  const longTerm = `${marker} fake longterm 10 mg once daily`;
  const shortTerm = `${marker} fake shortterm 250 mg three times daily`;
  for (const [special, flag] of [[longTerm, 1], [shortTerm, 0]]) {
    h.insertId(sql, `INSERT INTO drugs(provider_no,demographic_no,rx_date,end_date,written_date,BN,customName,special,long_term,
      position,dispenseInternal,create_date,lastUpdateDate) VALUES(${q(provider)},${patient},CURDATE(),DATE_ADD(CURDATE(),INTERVAL 30 DAY),
      CURDATE(),${q(marker)},${q(marker)},${q(special)},${flag},0,0,NOW(),NOW())`, flag ? 'long-term drug' : 'short-term drug');
  }
  const allergen = `${marker} FAKE ALLERGEN`;
  sql.execute(`INSERT INTO allergies (demographic_no,entry_date,DESCRIPTION,TYPECODE,reaction,archived,position,lastUpdateDate,providerNo)
    VALUES (${patient},CURDATE(),${q(allergen)},0,'FAKE rash',0,0,NOW(),${q(provider)})`);

  const chart = await s.chart();
  const form = await s.popup(chart, chart.locator('a[onclick*="ViewConsultationFormRequest?de="]').first(), 'consult-form');
  await form.locator('#EctConsultationFormRequest2Form').waitFor({ state: 'attached' });
  h.assert(await form.locator('#demographicNo').inputValue() === patient, 'The consultation form opened for another patient');
  await form.waitForLoadState('networkidle');
  /** Open a box's Import menu and pick one item; resolves once the import has answered and the box changed. */
  async function importInto(target, item) {
    const box = form.locator(`#${target}`);
    const before = await box.inputValue();
    await form.locator('.consult-import-dropdown', { has: form.locator(`ul[data-target="${target}"]`) }).locator('.dropdown-toggle').click();
    const [response] = await Promise.all([
      form.waitForResponse(r => r.request().method() === 'POST'
        && h.pathOnly(r.url()).endsWith('/oscarConsultationRequest/consultationClinicalData')),
      form.locator(`#${item}_${target}`).click(),
    ]);
    h.assert(response.status() === 200, `Import ${item} answered HTTP ${response.status()}`);
    const body = await response.json();
    if (body.note && body.note.trim()) {
      await form.waitForFunction(([id, old]) => document.getElementById(id).value !== old, [target, before]);
    }
    return { before, after: await box.inputValue(), body };
  }

  await s.step('each chart-history import fills in that section\'s note below what is already there', async () => {
    const box = form.locator('#clinicalInformation');
    await box.fill('');
    for (const [i, code] of HISTORIES.entries()) {
      const { before, after } = await importInto('clinicalInformation', code);
      const expected = code === 'MedHistory' ? editedMedHistory : text[code];
      h.assert(after.startsWith(before) && lines(after.slice(before.length)).join('|') === expected,
        `Import ${code} added ${JSON.stringify(lines(after.slice(before.length)))}, not that section's note`);
      h.assert(lines(after).length === i + 1, `After ${i + 1} imports Clinical Information holds ${lines(after).length} lines`);
    }
  });

  await s.step('an edited note imports its latest text only, and an archived note is not imported', async () => {
    await form.locator('#concurrentProblems').fill('');
    const med = await importInto('concurrentProblems', 'MedHistory');
    h.assert(lines(med.after).join('|') === editedMedHistory, `Medical History imported ${JSON.stringify(lines(med.after))}`);
    const concerns = await importInto('concurrentProblems', 'Concerns');
    h.assert(!concerns.after.includes(archivedConcern), 'Ongoing Concerns imported the archived note');
    h.assert(lines(concerns.after).join('|') === `${editedMedHistory}|${text.Concerns}`,
      `Concurrent Problems holds ${JSON.stringify(lines(concerns.after))}`);
  });

  await s.step('Long Term Medications imports the long-term drug only, and nothing is saved', async () => {
    await form.locator('#currentMedications').fill('');
    const meds = await importInto('currentMedications', 'fetchLongTermMedications');
    const imported = meds.after.toLowerCase();
    h.assert(imported.includes(longTerm.toLowerCase()), 'Long Term Medications did not import the long-term drug');
    h.assert(!imported.includes(shortTerm.toLowerCase()), 'Long Term Medications imported the short-term drug');
    h.assert(sql.value(`SELECT COUNT(*) FROM consultationRequests WHERE demographicNo=${patient}`) === '0',
      'Importing into the form saved a referral');
  });

  await s.step('Import Allergies fills in the patient\'s allergy', async () => {
    const box = form.locator('#allergies');
    await box.fill('');
    const [response] = await Promise.all([
      form.waitForResponse(r => r.request().method() === 'POST'
        && h.pathOnly(r.url()).endsWith('/oscarConsultationRequest/consultationClinicalData')
        && new URLSearchParams(r.request().postData() || '').get('method') === 'fetchAllergies'),
      form.locator('#fetchAllergies_allergies').click(),
    ]);
    const body = await response.text();
    await form.waitForFunction(() => document.getElementById('allergies').value.trim() !== '', null, { timeout: 5000 }).catch(() => {});
    const imported = (await box.inputValue()).toLowerCase();
    h.assert(response.status() === 200 && imported.includes(allergen.toLowerCase()) && imported.includes('fake rash'),
      `Import Allergies left the box ${JSON.stringify(await box.inputValue())} (HTTP ${response.status()}, ${body.length}-byte answer)`);
    await form.close();
  });
}

if (require.main === module) runWorkflow('consultation-chart-import', workflow, { openPatient: true });
module.exports = { workflow };
