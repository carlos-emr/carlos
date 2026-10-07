#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Print through the installed chart dialog and inspect the downloaded PDF.
// SQL fixtures are synthetic, owned by this run, and removed even on failure.
const fs = require('node:fs');
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const print = require('./lib/gap-clinical-print');
const x = require('./lib/export-content-helpers');
const q = h.sqlString;

function message(marker, accession, stamp, token) {
  return [
    `MSH|^~\\&|PATHL7|CW|CARLOS|TEST|${stamp}||ORU^R01|${token}|P|2.3|||ER|AL`,
    `PID||9999999999|9999999999||${marker}^Workflow||19800102|F`,
    `ORC|RE||${accession}|||||||||99999^FAKE-ORDERING^DOC`,
    `OBR|1||${accession}|CHEM^Chemistry||${stamp}|${stamp}|||||||${stamp}||99999^FAKE-ORDERING^DOC||||||${stamp}||CHEM4|F|||99999^FAKE-ORDERING^DOC`,
    `OBX|1|ST|XXX-0001^Synthetic range test||${token}||||||F|||${stamp}`,
  ].join('\r');
}

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  h.assert(process.env.EXCLUSIVE === '1', 'Chart lab range validation requires EXCLUSIVE=1 to restore the global inbox date mode');
  const preference = "name='inboxDateSearchType'";
  const snapshotQuery = `SELECT id, IFNULL(HEX(value),''), value IS NULL FROM SystemPreferences WHERE ${preference} ORDER BY id`;
  const previous = sql.rows(snapshotQuery);
  let insertedPreference;
  s.cleanup(() => {
    if (insertedPreference) sql.execute(`DELETE FROM SystemPreferences WHERE id=${insertedPreference} AND ${preference}`);
    for (const [id, hex, isNull] of previous) {
      h.assert(/^\d+$/.test(id) && /^[0-9A-F]*$/i.test(hex || '') && ['0', '1'].includes(isNull), 'Invalid preference snapshot');
      sql.execute(`UPDATE SystemPreferences SET value=${isNull === '1' ? 'NULL' : `UNHEX('${hex || ''}')`} WHERE id=${id} AND ${preference}`);
    }
    h.assert(JSON.stringify(sql.rows(snapshotQuery)) === JSON.stringify(previous), 'Inbox date preference was not restored');
  });
  if (!previous.length) {
    insertedPreference = sql.value(`INSERT INTO SystemPreferences (name,value,updateDate)
      VALUES ('inboxDateSearchType','serviceObservation',NOW()); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(insertedPreference), 'Inbox date preference was not inserted');
  }
  const setPreference = value => sql.execute(`UPDATE SystemPreferences SET value=${q(value)} WHERE ${preference}`);
  setPreference('serviceObservation');
  const scratch = print.scratchDir();
  const owned = [];
  s.cleanup(() => fs.rmSync(scratch, { recursive: true, force: true }));
  s.cleanup(() => {
    const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
    sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}`) === '0', 'Owned notes remain');
    sql.execute(`DELETE FROM eChart WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM eChart WHERE demographicNo=${patient}`) === '0', 'Owned chart row remains');
    for (const id of owned) {
      h.assert(sql.value(`SELECT COUNT(*) FROM hl7TextMessage WHERE lab_id=${id} AND serviceName=${q(marker)}`) === '1',
        'Owned lab identity changed');
      sql.execute(`DELETE FROM patientLabRouting WHERE lab_no=${id} AND lab_type='HL7';
        DELETE FROM providerLabRouting WHERE lab_no=${id} AND lab_type='HL7';
        DELETE FROM hl7TextInfo WHERE lab_no=${id};
        DELETE FROM hl7TextMessage WHERE lab_id=${id} AND serviceName=${q(marker)}`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM patientLabRouting WHERE lab_no=${id} AND lab_type='HL7')
        + (SELECT COUNT(*) FROM providerLabRouting WHERE lab_no=${id} AND lab_type='HL7')
        + (SELECT COUNT(*) FROM hl7TextInfo WHERE lab_no=${id})
        + (SELECT COUNT(*) FROM hl7TextMessage WHERE lab_id=${id})`) === '0', 'Owned lab rows remain');
    }
  });
  const program = sql.value("SELECT id FROM program WHERE name='OSCAR' ORDER BY id LIMIT 1");
  const role = sql.value(`SELECT role_id FROM program_provider WHERE provider_no=${q(provider)} AND program_id=${program || 0} LIMIT 1`);
  h.assert(program && role, 'The default program or test provider role is missing');
  const noteTokens = { inside: `${marker} NOTEINSIDE`, outside: `${marker} NOTEOUTSIDE` };
  for (const [key, date] of [['inside', '2021-06-15 12:00:00'], ['outside', '2021-06-17 12:00:00']]) {
    sql.execute(`INSERT INTO casemgmt_note (update_date,observation_date,demographic_no,provider_no,note,signed,
        signing_provider_no,encounter_type,program_no,reporter_caisi_role,history,uuid,locked,archived)
      VALUES (NOW(),${q(date)},${patient},${q(provider)},${q(noteTokens[key])},1,${q(provider)},'',${q(program)},
        ${q(role)},${q(noteTokens[key])},UUID(),'0',0)`);
  }
  const cases = [
    ['BEFORE', '2021-06-14 23:59:59', 'BEFORE'],
    ['START', '2021-06-15 00:00:00', 'START'],
    ['END', '2021-06-16 23:59:59', 'END'],
    ['AFTER', '2021-06-17 00:00:00', 'AFTER'],
    ['OLDER', '2021-06-15 12:00:00', 'VERSION'],
    ['NEWER', '2021-06-17 12:00:00', 'VERSION'],
  ];
  const tokens = {};
  for (const [key, date, group] of cases) {
    const token = `RANGE${key}${marker.slice(-8)}`;
    tokens[key] = token;
    const accession = `${marker.slice(-10)}-${group}`;
    const stamp = date.replace(/[- :]/g, '');
    const payload = Buffer.from(message(marker, accession, stamp, token), 'utf8').toString('base64');
    const id = sql.value(`INSERT INTO hl7TextMessage (fileUploadCheck_id,message,type,serviceName,created)
      VALUES (0,${q(payload)},'PATHL7',${q(marker)},NOW()); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), 'Synthetic lab did not insert');
    owned.push(id);
    sql.execute(`INSERT INTO hl7TextInfo (lab_no,sex,health_no,result_status,final_result_count,obr_date,priority,
        requesting_client,discipline,last_name,first_name,report_status,accessionNum,filler_order_num,sending_facility)
      VALUES (${id},'F','9999999999','N',1,${q(date)},'R','FAKE-ORDERING, DOC','CHEM',${q(marker)},'Workflow','F',
        ${q(accession)},${q(accession)},'CW');
      INSERT INTO providerLabRouting (provider_no,lab_no,status,lab_type) VALUES (${q(provider)},${id},'N','HL7');
      INSERT INTO patientLabRouting (demographic_no,lab_no,lab_type,created) VALUES (${patient},${id},'HL7',NOW())`);
  }
  const chart = await s.chart();
  const assertLabs = (text, expected) => {
    const content = x.squash(text);
    for (const [key, token] of Object.entries(tokens)) {
      h.assert(content.includes(token) === expected.includes(key), `Printed lab ${key}: expected ${expected.includes(key) ? 'included' : 'excluded'}`);
    }
  };
  await s.step('unrestricted chart print includes all dates and only the latest accession version', async () => {
    await print.openPrintDialog(chart);
    await chart.locator('#printopAll').check();
    await print.setFlags(chart, ['printLabs']);
    const { text } = await print.pressPrint(chart, scratch);
    assertLabs(text, ['BEFORE', 'START', 'END', 'AFTER', 'NEWER']);
    h.assert(Object.values(noteTokens).every(token => text.includes(token)), 'Unrestricted print omitted an owned note');
  });
  await s.step('date-range print includes both boundary days and the latest version inside the range', async () => {
    await print.openPrintDialog(chart);
    await chart.locator('#printopDates').check();
    await print.setFlags(chart, ['printLabs']);
    await chart.evaluate(() => {
      document.getElementById('printStartDate')._flatpickr.setDate('2021-06-15', false, 'Y-m-d');
      document.getElementById('printEndDate')._flatpickr.setDate('2021-06-16', false, 'Y-m-d');
    });
    const { text } = await print.pressPrint(chart, scratch);
    assertLabs(text, ['START', 'END', 'OLDER']);
    h.assert(text.includes(noteTokens.inside) && !text.includes(noteTokens.outside),
      'Date-range print failed to hydrate the in-range note or included an out-of-range note');
  });
  await s.step('a date range with no matching notes or labs still produces a complete PDF', async () => {
    await print.openPrintDialog(chart);
    await chart.locator('#printopDates').check();
    await chart.evaluate(() => {
      for (const id of ['printStartDate', 'printEndDate']) {
        document.getElementById(id)._flatpickr.setDate('2022-06-15', false, 'Y-m-d');
      }
    });
    const { text } = await print.pressPrint(chart, scratch);
    assertLabs(text, []);
    h.assert(Object.values(noteTokens).every(token => !text.includes(token)), 'Empty range printed an owned note');
  });
  await s.step('received-date mode prints a single lab whose native timestamp includes fractional seconds', async () => {
    // Keep exactly one lab in this owned patient's list: sorting cannot mask the new predicate call.
    sql.execute(`DELETE FROM patientLabRouting WHERE demographic_no=${patient} AND lab_type='HL7'
      AND lab_no IN (${owned.slice(1).join(',')});
      UPDATE hl7TextMessage SET created='2021-06-15 12:00:00' WHERE lab_id=${owned[0]} AND serviceName=${q(marker)}`);
    setPreference('receivedCreated');
    await print.openPrintDialog(chart);
    await chart.locator('#printopDates').check();
    await print.setFlags(chart, ['printLabs']);
    await chart.evaluate(() => {
      for (const id of ['printStartDate', 'printEndDate']) {
        document.getElementById(id)._flatpickr.setDate('2021-06-15', false, 'Y-m-d');
      }
    });
    const { text } = await print.pressPrint(chart, scratch);
    assertLabs(text, ['BEFORE']);
    h.assert(text.includes(noteTokens.inside), 'Received-date print omitted the in-range note');
  });
}

if (require.main === module) runWorkflow('chart-print-lab-range', workflow,
  { openPatient: true, preflight: () => x.requirePoppler('pdftotext') });
module.exports = { workflow };
