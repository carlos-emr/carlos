#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * E-Chart print: what the Allergies, Preventions and Rx sections of the PDF say, against the chart's rows.
 * User path: Schedule ▸ Search ▸ Master Record ▸ E-Chart ▸ printer icon ▸ Allergies, Preventions and Rx icons ▸
 * Print all ▸ Print.
 * gap-clinical-chart-print-scope proves the allergy and prevention sections appear when their icons are on; it does not
 * read what they list. A printed chart goes to a specialist or a lawyer, so an allergy that was deleted from the chart
 * must not be printed as if it were current, and a medication must not be listed twice.
 *
 * Asserts (pdftotext): the active allergy prints with its reaction, severity and start date; the given prevention
 * prints with its date and the refused one is flagged "(Refused)"; the deleted prevention is absent; the current
 * medications print, the discontinued and the expired one do not. LAST (fails today): an ARCHIVED allergy (removed
 * from the chart) is not printed (CaseManagementPrint uses findAllergies, which includes archived rows, and the
 * printer filters nothing), and a custom-name medication prescribed twice with another drug in between is listed
 * once (RxPrescriptionData.getUniquePrescriptionsByPatient lets the last comparison decide).
 * Fixtures: the owned FAKE-PW patient with SQL-seeded allergies, preventions, one prescription row with five drugs and
 * one signed note; cleanup deletes the patient's rows of each table and asserts it. Needs pdftotext.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const print = require('./lib/gap-clinical-print');
const x = require('./lib/export-content-helpers');

const q = h.sqlString;

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const scratch = print.scratchDir();
  s.cleanup(() => require('node:fs').rmSync(scratch, { recursive: true, force: true }));
  s.cleanup(() => {
    const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
    sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient};
      DELETE FROM casemgmt_issue WHERE demographic_no=${patient};
      DELETE FROM allergies WHERE demographic_no=${patient};
      DELETE FROM preventions WHERE demographic_no=${patient};
      DELETE FROM drugs WHERE demographic_no=${patient};
      DELETE FROM prescription WHERE demographic_no=${patient};
      DELETE FROM eChart WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient}) + (SELECT COUNT(*) FROM preventions WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient}) + (SELECT COUNT(*) FROM prescription WHERE demographic_no=${patient})`) === '0',
    'Owned chart rows were not removed');
  });
  const program = sql.value('SELECT id FROM program WHERE name=\'OSCAR\' ORDER BY id LIMIT 1');
  const role = sql.value(`SELECT role_id FROM program_provider WHERE provider_no=${q(provider)} AND program_id=${program || 0} LIMIT 1`);
  h.assert(program && role, 'The default OSCAR program or the test provider\'s role in it is missing');
  sql.execute(`INSERT INTO casemgmt_note (update_date,observation_date,demographic_no,provider_no,note,signed,signing_provider_no,
      encounter_type,program_no,reporter_caisi_role,history,uuid,locked,archived)
    VALUES (NOW(),NOW(),${patient},${q(provider)},${q(`${marker} NOTE`)},1,${q(provider)},'',${q(program)},${q(role)},'',UUID(),'0',0)`);

  const names = { active: `${marker} PENICILLIN`, archived: `${marker} ARCHIVEDALLERGEN` };
  sql.execute(`INSERT INTO allergies (demographic_no,entry_date,DESCRIPTION,TYPECODE,reaction,archived,position,lastUpdateDate,providerNo,
      severity_of_reaction,start_date)
    VALUES (${patient},CURDATE(),${q(names.active)},13,'hives, rash',0,0,NOW(),${q(provider)},'3','2020-02-03'),
      (${patient},CURDATE(),${q(names.archived)},13,'entered in error',1,1,NOW(),${q(provider)},'1','2019-01-01')`);
  sql.execute(`INSERT INTO preventions (demographic_no,creation_date,prevention_date,provider_no,prevention_type,deleted,refused,never,lastUpdateDate)
    VALUES (${patient},NOW(),'2022-01-02 00:00:00',${q(provider)},'MMR','0','0','0',NOW()),
      (${patient},NOW(),'2022-03-04 00:00:00',${q(provider)},'Td','1','0','0',NOW()),
      (${patient},NOW(),'2022-05-06 00:00:00',${q(provider)},'Flu','0','1','0',NOW())`);
  const script = sql.value(`INSERT INTO prescription(provider_no,demographic_no,date_prescribed,date_printed,textView,lastUpdateDate)
    VALUES(${q(provider)},${patient},CURDATE(),CURDATE(),'Synthetic prescription',NOW()); SELECT LAST_INSERT_ID()`);
  const drug = (name, { archived = false, endOffset = 30 } = {}) => sql.execute(`INSERT INTO drugs(provider_no,demographic_no,rx_date,end_date,written_date,
      BN,GCN_SEQNO,customName,takemin,takemax,freqcode,duration,durunit,quantity,\`repeat\`,special,archived,archived_reason,archived_date,
      script_no,position,dispenseInternal,create_date,lastUpdateDate)
    VALUES(${q(provider)},${patient},DATE_SUB(CURDATE(),INTERVAL 60 DAY),DATE_ADD(CURDATE(),INTERVAL ${endOffset} DAY),DATE_SUB(CURDATE(),INTERVAL 60 DAY),
      ${q(name)},0,${q(name)},1,1,'OD','30','D','30',0,${q(`${name} 10 mg once daily`)},${archived ? 1 : 0},${archived ? "'discontinued'" : "''"},${archived ? 'NOW()' : 'NULL'},
      ${script},0,0,NOW(),NOW())`);
  drug(`${marker}-X`);
  drug(`${marker}-Y`);
  drug(`${marker}-X`);
  drug(`${marker}-DISCONTINUED`, { archived: true });
  drug(`${marker}-EXPIRED`, { endOffset: -10 });

  const chart = await s.chart();
  await chart.locator('#imgPrintEncounter').waitFor({ state: 'visible', timeout: 20000 });
  let text;
  await s.step('Print all with the Allergies, Preventions and Rx icons on downloads a PDF with the three sections', async () => {
    await print.openPrintDialog(chart);
    await chart.locator('#printopAll').check();
    await print.setFlags(chart, ['printAllergies', 'printPreventions', 'printRx']);
    ({ text } = await print.pressPrint(chart, scratch));
    h.assert(text.includes('Patient Allergies') && text.includes('Patient Preventions History') && text.includes('Patient Rx History'),
      'The print lacks one of the Allergies, Preventions and Rx sections');
  });
  const section = (from, to) => {
    const start = text.indexOf(from);
    const end = to ? text.indexOf(to, start + from.length) : -1;
    return text.slice(start, end > start ? end : undefined);
  };
  const squashed = value => x.squash(value);

  await s.step('the active allergy prints with its reaction, severity and start date', async () => {
    const allergies = squashed(section('Patient Allergies', 'Documentation Date'));
    h.assert(allergies.includes(squashed(names.active)), 'The active allergy is missing from the Allergies section');
    for (const part of ['hives,rash', 'Severe', '03-Feb-2020']) h.assert(allergies.includes(part), `The active allergy lacks "${part}" (section reads: ${allergies.slice(0, 260)})`);
  });

  await s.step('the given prevention prints with its date, the refused one is flagged and the deleted one is absent', async () => {
    const preventions = squashed(section('Patient Preventions History', 'Patient Allergies'));
    const when = (day, mon, year) => [`${year}-${mon}-${day}`, `${day}-${['', 'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun'][Number(mon)]}-${year}`];
    const dated = (name, [iso, long]) => preventions.includes(`${iso}-${name}`) || preventions.includes(`${long}-${name}`);
    h.assert(dated('MMR', when('02', '01', '2022')), `The given MMR is missing or has no date (section reads: ${preventions.slice(0, 200)})`);
    h.assert(dated('Flu(Refused)', when('06', '05', '2022')), 'The refused Flu is not flagged (Refused)');
    h.assert(!preventions.includes('Td'), 'The deleted Td prevention is printed');
  });

  await s.step('the current medications print and the discontinued and the expired one do not', async () => {
    const rx = squashed(section('Patient Rx History', 'Patient Preventions History'));
    h.assert(rx.includes(squashed(`${marker}-Y 10 mg once daily`)), 'The current medication Y is missing from the Rx history');
    h.assert(rx.includes(squashed(`${marker}-X 10 mg once daily`)), 'The current medication X is missing from the Rx history');
    h.assert(!rx.includes('DISCONTINUED'), 'A discontinued medication is printed as current');
    h.assert(!rx.includes('EXPIRED'), 'An expired medication is printed as current');
  });

  await s.step('an allergy removed from the chart is not printed and a repeated medication is listed once', async () => {
    const problems = [];
    if (squashed(text).includes(squashed(names.archived))) problems.push('the archived (removed) allergy is printed in the Allergies section with no marker that it is inactive');
    const times = squashed(text).split(squashed(`${marker}-X 10 mg once daily`)).length - 1;
    if (times !== 1) problems.push(`the medication prescribed twice is listed ${times} times in the Rx history`);
    h.assert(!problems.length, `The printed chart misstates the record: ${problems.join('; ')}`);
  });
}

if (require.main === module) {
  runWorkflow('export-content-chart-print-sections', workflow, { openPatient: true, preflight: () => x.requirePoppler('pdftotext') });
}
module.exports = { workflow };
