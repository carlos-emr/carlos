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
  const scratch = print.scratchDir();
  const owned = [];
  s.cleanup(() => fs.rmSync(scratch, { recursive: true, force: true }));
  s.cleanup(() => {
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
  });
}

if (require.main === module) runWorkflow('chart-print-lab-range', workflow,
  { openPatient: true, preflight: () => x.requirePoppler('pdftotext') });
module.exports = { workflow };
