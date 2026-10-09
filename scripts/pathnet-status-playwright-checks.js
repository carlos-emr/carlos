#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// Requires legacy BC tables and PATHNET_LABS=yes in a disposable installation.
// Exercise the real Hibernate projection and patient lab list with owned labs.
const { randomInt } = require('node:crypto');
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');

async function preflight({sql, env = process.env}) {
  // This optional integration cannot be inferred from leftover BC tables.
  // Operators opt in only when the installed application's property is enabled.
  if ((env.PATHNET_LABS || '').trim() !== 'yes') {
    throw new h.SkipCheck('Set PATHNET_LABS=yes only for an installation with PathNet enabled');
  }
  for (const table of ['hl7_message', 'hl7_msh', 'hl7_pid', 'hl7_orc', 'hl7_obr', 'hl7_obx']) {
    if (sql.value(`SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()
      AND table_name=${h.sqlString(table)}`) !== '1') {
      throw new h.SkipCheck('Legacy BC PathNet tables and PATHNET_LABS=yes are required');
    }
  }
}

async function workflow(s) {
  const {sql, patient, provider, marker} = s;
  const owned = [];
  s.cleanup(() => {
    for (const id of owned) {
      h.assert(sql.value(`SELECT COUNT(*) FROM hl7_message WHERE message_id=${id}
        AND notes=${h.sqlString(marker)}`) === '1', 'Owned PathNet message identity changed');
      sql.execute(`DELETE FROM patientLabRouting WHERE lab_type='BCP' AND lab_no=${id} AND demographic_no=${patient};
        DELETE FROM providerLabRouting WHERE lab_type='BCP' AND lab_no=${id} AND provider_no=${h.sqlString(provider)};
        DELETE FROM hl7_obr WHERE pid_id=${id}; DELETE FROM hl7_orc WHERE pid_id=${id};
        DELETE FROM hl7_pid WHERE pid_id=${id} AND message_id=${id};
        DELETE FROM hl7_msh WHERE message_id=${id};
        DELETE FROM hl7_message WHERE message_id=${id} AND notes=${h.sqlString(marker)}`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM hl7_message WHERE message_id=${id})
        +(SELECT COUNT(*) FROM hl7_pid WHERE pid_id=${id})+(SELECT COUNT(*) FROM hl7_msh WHERE message_id=${id})
        +(SELECT COUNT(*) FROM hl7_orc WHERE pid_id=${id})+(SELECT COUNT(*) FROM hl7_obr WHERE pid_id=${id})
        +(SELECT COUNT(*) FROM patientLabRouting WHERE lab_type='BCP' AND lab_no=${id})
        +(SELECT COUNT(*) FROM providerLabRouting WHERE lab_type='BCP' AND lab_no=${id})`) === '0',
      'Owned PathNet fixture was not removed');
    }
  });
  for (const status of ['F', 'P']) {
    const id = randomInt(1000000000, 1800000000);
    const occupied = sql.value(`SELECT (SELECT COUNT(*) FROM hl7_message WHERE message_id=${id})
      +(SELECT COUNT(*) FROM hl7_pid WHERE pid_id=${id} OR message_id=${id})
      +(SELECT COUNT(*) FROM hl7_msh WHERE message_id=${id})
      +(SELECT COUNT(*) FROM hl7_orc WHERE pid_id=${id})+(SELECT COUNT(*) FROM hl7_obr WHERE pid_id=${id})
      +(SELECT COUNT(*) FROM patientLabRouting WHERE lab_no=${id})
      +(SELECT COUNT(*) FROM providerLabRouting WHERE lab_no=${id})
      +(SELECT COUNT(*) FROM hl7TextInfo WHERE lab_no=${id})`);
    h.assert(occupied === '0', 'Random PathNet fixture identifier is already occupied');
    sql.execute(`INSERT INTO hl7_message(message_id,date_time,notes) VALUES(${id},NOW(),${h.sqlString(marker)})`);
    owned.push(id);
    // The legacy patient query joins PID IDs to message IDs. Keep that historic
    // relationship here; these regressions concern the projected status type.
    sql.execute(`INSERT INTO hl7_msh(message_id,date_time_of_message) VALUES(${id},NOW());
      INSERT INTO hl7_pid(pid_id,message_id,patient_name,external_id,sex)
        VALUES(${id},${id},${h.sqlString(`${marker}^Workflow`)},'FAKE-PATHNET','F');
      INSERT INTO hl7_orc(pid_id,filler_order_number,ordering_provider)
        VALUES(${id},'FAKE-ACCESSION-TEST',${h.sqlString(`000000^${marker}^${status}`)});
      INSERT INTO hl7_obr(pid_id,result_status,diagnostic_service_sect_id,
        requested_date_time,observation_date_time,specimen_received_date_time,results_report_status_change)
        VALUES(${id},${h.sqlString(status)},'CHEM',NOW(),NOW(),NOW(),NOW());
      INSERT INTO patientLabRouting(lab_no,lab_type,demographic_no,created) VALUES(${id},'BCP',${patient},NOW());
      INSERT INTO providerLabRouting(lab_no,lab_type,provider_no,status)
        VALUES(${id},'BCP',${h.sqlString(provider)},'U')`);
  }
  const page = await s.context.newPage();
  await s.step('patient lab listing retains final and preliminary PathNet reports', async () => {
    await h.gotoApp(page, s.config.baseUrl, `/lab/ViewDemographicLab?demographicNo=${patient}`);
    await h.assertNotErrorPage(page, 'legacy PathNet patient lab listing');
    await page.locator('#labResultsTbl').waitFor();
    for (const [index, status] of ['Final', 'Partial'].entries()) {
      // The href embeds a JavaScript string, so its slashes and separators may
      // be escaped. The owned numeric segment ID survives that encoding.
      const row = page.locator('#labResultsTbl tbody tr').filter({has:
        page.locator(`a[href*="${owned[index]}"]`)});
      h.assert(await row.count() === 1, `PathNet ${status} report was omitted from the patient listing`);
      h.assert((await row.locator('a').first().getAttribute('href')).includes('ViewLabDisplay'),
        'PathNet row has no lab-display link');
      h.assert((await row.innerText()).includes(marker), 'PathNet row lost its owned ordering provider');
      h.assert((await row.locator('td').nth(4).innerText()).trim() === status,
        `PathNet ${status} report has the wrong final/partial classification`);
    }
  });
}

if (require.main === module) runWorkflow('pathnet-status', workflow, {preflight});
module.exports = {workflow, preflight};
