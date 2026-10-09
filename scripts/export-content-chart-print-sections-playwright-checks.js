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
 * medications print, the discontinued and the expired one do not. Archived allergies never print, including when
 * every allergy is archived; restoring an active allergy makes it printable again. Deselecting the Allergies icon
 * omits that section. Identical custom and coded entries collapse even with another drug in between, as does a
 * renewal that differs only in its dates (#4420), while different regimens remain visible (#4270). A newer archived
 * copy must not hide its current sibling.
 * Fixtures: the owned FAKE-PW patient with SQL-seeded allergies, preventions, one prescription row with drugs and
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
  const drug = (name, { archived = false, endOffset = 30, startOffset = 60, gcn = 0, dose = 10 } = {}) => sql.execute(`INSERT INTO drugs(provider_no,demographic_no,rx_date,end_date,written_date,
      BN,GCN_SEQNO,customName,dosage,unit,takemin,takemax,freqcode,duration,durunit,quantity,\`repeat\`,special,archived,archived_reason,archived_date,
      script_no,position,dispenseInternal,create_date,lastUpdateDate)
    VALUES(${q(provider)},${patient},DATE_SUB(CURDATE(),INTERVAL ${startOffset} DAY),DATE_ADD(CURDATE(),INTERVAL ${endOffset} DAY),DATE_SUB(CURDATE(),INTERVAL ${startOffset} DAY),
      ${q(name)},${gcn},${q(name)},${q(String(dose))},'mg',1,1,'OD','30','D','30',0,${q(`${name} ${dose} mg once daily`)},${archived ? 1 : 0},${archived ? "'discontinued'" : "''"},${archived ? 'NOW()' : 'NULL'},
      ${script},0,0,NOW(),NOW())`);
  drug(`${marker}-X`);
  drug(`${marker}-Y`);
  drug(`${marker}-X`);
  drug(`${marker}-DISCONTINUED`, { archived: true });
  drug(`${marker}-EXPIRED`, { endOffset: -10 });
  drug(`${marker}-CODED`, { gcn: 12345 });
  drug(`${marker}-CODED`, { gcn: 12345 });
  drug(`${marker}-CODED`, { gcn: 12345, dose: 20 });
  drug(`${marker}-REGIMEN`);
  drug(`${marker}-REGIMEN`, { dose: 20 });
  drug(`${marker}-DATED`);
  drug(`${marker}-DATED`, { startOffset: 30 });
  drug(`${marker}-STATUS`);
  drug(`${marker}-STATUS`, { archived: true });

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

  await s.step('an allergy removed from the chart is absent from the PDF', async () => {
    h.assert(!squashed(text).includes(squashed(names.archived)),
      'The archived (removed) allergy is printed in the Allergies section');
    h.assert(sql.value(`SELECT archived FROM allergies WHERE demographic_no=${patient} AND DESCRIPTION=${q(names.archived)}`) === '1',
      'Printing changed the archived allergy record');
  });

  await s.step('a chart with only archived allergies prints no allergy entries', async () => {
    sql.execute(`UPDATE allergies SET archived=1 WHERE demographic_no=${patient}`);
    await print.openPrintDialog(chart);
    await print.setFlags(chart, ['printAllergies']);
    const { text: archivedText } = await print.pressPrint(chart, scratch);
    h.assert(archivedText.includes('Patient Allergies'), 'The selected Allergies section is missing');
    for (const name of Object.values(names)) h.assert(!squashed(archivedText).includes(squashed(name)),
      'An archived allergy is printed in an otherwise empty allergy section');
  });

  await s.step('a restored active allergy appears on the next print without its archived sibling', async () => {
    sql.execute(`UPDATE allergies SET archived=0 WHERE demographic_no=${patient} AND DESCRIPTION=${q(names.active)}`);
    await print.openPrintDialog(chart);
    await print.setFlags(chart, ['printAllergies']);
    const { text: restoredText } = await print.pressPrint(chart, scratch);
    h.assert(squashed(restoredText).includes(squashed(names.active)), 'The restored active allergy is missing');
    h.assert(!squashed(restoredText).includes(squashed(names.archived)), 'The archived sibling is printed');
  });

  await s.step('deselecting the Allergies icon omits the section and its active entries', async () => {
    await print.openPrintDialog(chart);
    await print.setFlags(chart, ['printRx', 'printPreventions']);
    const { text: omittedText } = await print.pressPrint(chart, scratch);
    h.assert(!omittedText.includes('Patient Allergies'), 'An unselected Allergies section is printed');
    for (const name of Object.values(names)) h.assert(!squashed(omittedText).includes(squashed(name)),
      'An allergy is printed when its section is unselected');
    h.assert(omittedText.includes('Patient Rx History') && omittedText.includes('Patient Preventions History'),
      'Deselecting Allergies also removed another selected section');
  });

  await s.step('equivalent medications and renewals collapse while distinct regimens remain visible', async () => {
    const rx = squashed(section('Patient Rx History', 'Patient Preventions History'));
    const count = value => rx.split(squashed(value)).length - 1;
    const times = count(`${marker}-X 10 mg once daily`);
    h.assert(times === 1, `The medication prescribed twice is listed ${times} times in the Rx history`);
    for (const name of ['CODED', 'REGIMEN']) {
      for (const dose of [10, 20]) h.assert(count(`${marker}-${name} ${dose} mg once daily`) === 1,
        `The ${name} ${dose} mg regimen is missing or duplicated`);
    }
    h.assert(count(`${marker}-DATED 10 mg once daily`) === 1, 'A renewal that differs only in its dates is listed twice (#4420)');
    h.assert(count(`${marker}-STATUS 10 mg once daily`) === 1, 'The newer archived copy hid the current prescription');
  });
}

if (require.main === module) {
  runWorkflow('export-content-chart-print-sections', workflow, { openPatient: true, preflight: () => x.requirePoppler('pdftotext') });
}
module.exports = { workflow };
