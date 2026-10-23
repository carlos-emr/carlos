/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
/* Disposable BC install only: enable OLD_LAB_UPLOAD=yes and PATHNET_LABS=yes.
 * Uploads synthetic PathNet batches through Administration, verifies the entire
 * persisted segment chain, then matches an unlinked result through the patient picker.
 * Set LAB_UPLOAD_DOCUMENT_STORE to remove this run's archived upload copies too.
 */
const fs = require('node:fs');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { submitInFrame, reopenAdminPage } = require('./lib/admin-frame');
const { revealAuditLink } = require('./lib/playwright-link-audit');

function batch(phn, dob, name, accession) {
  const hl7 = `MSH|^~\\&|FAKE|PATHNET|CARLOS|TEST|20261023090000||ORU^R01|${accession}|P|2.3\n`
    + `PID|1|${phn}|||${name}||${dob}|M\nORC|RE||${accession}\n`
    + `OBR|1||${accession}|GLU^Glucose||20261023090000|20261023090000\n`
    + 'OBX|1|NM|GLU^Glucose||5.2|mmol/L|3.6-6.0|N|||F\n';
  return Buffer.from(`<Messages MessageFormat="ORUR01" Version="2.3" MessageCount="1"><Message><![CDATA[${hl7}]]></Message></Messages>`);
}

async function workflow(s) {
  const { sql, patient, marker, cleanup } = s;
  const phn = `99${String(Number.parseInt(marker.slice(-8), 16)).padStart(8, '0').slice(-8)}`;
  sql.execute(`UPDATE demographic SET hin=${h.sqlString(phn)},hc_type='BC',province='BC' WHERE demographic_no=${patient}`);
  let duplicate;
  cleanup(async () => {
    const ids = sql.rows(`SELECT message_id FROM hl7_pid WHERE patient_name LIKE ${h.sqlString(`${marker}%`)}`)
      .map(([id]) => id).filter(id => /^[1-9]\d*$/.test(id));
    if (ids.length) {
      const list = ids.join(',');
      sql.execute(`DELETE x FROM hl7_obx x JOIN hl7_obr o ON o.obr_id=x.obr_id JOIN hl7_pid p ON p.pid_id=o.pid_id WHERE p.message_id IN (${list});
        DELETE o FROM hl7_obr o JOIN hl7_pid p ON p.pid_id=o.pid_id WHERE p.message_id IN (${list});
        DELETE o FROM hl7_orc o JOIN hl7_pid p ON p.pid_id=o.pid_id WHERE p.message_id IN (${list});
        DELETE l FROM hl7_link l JOIN hl7_pid p ON p.pid_id=l.pid_id WHERE p.message_id IN (${list});
        DELETE FROM hl7_pid WHERE message_id IN (${list}); DELETE FROM hl7_msh WHERE message_id IN (${list});
        DELETE FROM patientLabRouting WHERE lab_type='BCP' AND lab_no IN (${list});
        DELETE FROM providerLabRouting WHERE lab_type='BCP' AND lab_no IN (${list});
        DELETE FROM providerLabRoutingLock WHERE lab_no IN (${list}); DELETE FROM hl7_message WHERE message_id IN (${list});`);
    }
    sql.execute(`DELETE FROM fileUploadCheck WHERE filename LIKE ${h.sqlString(`%${marker}%`)}`);
    if (duplicate) sql.execute(`DELETE FROM demographic WHERE demographic_no=${duplicate} AND last_name=${h.sqlString(marker)}`);
    if (process.env.LAB_UPLOAD_DOCUMENT_STORE) {
      const root = fs.realpathSync(process.env.LAB_UPLOAD_DOCUMENT_STORE);
      for (const name of fs.readdirSync(root)) {
        if (name.startsWith(`LabUpload.${marker}-`) && /^LabUpload\.FAKE-PW[a-f0-9]+-[a-z]+\.xml\.\d+$/.test(name)) {
          fs.unlinkSync(path.join(root, name)); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- fixed prefix and own random marker
        }
      }
    }
  });
  const { page: admin } = await clickOpensPopupOrNavigates(s.schedule,
    s.schedule.locator('#admin-panel, #admin2').first(),
    { context: s.context, recorder: s.recorder, label: 'pathnet-administration' });
  const uploadLink = admin.locator('a.xlink[rel$="/lab/labUpload"]');
  const linkingLink = admin.locator('a.xlink[rel$="/lab/CA/BC/ViewIndex"]');
  if (await uploadLink.count() === 0 || await linkingLink.count() === 0) {
    throw new h.SkipCheck('Enable OLD_LAB_UPLOAD and PATHNET_LABS on a disposable BC install');
  }
  const openFrame = async (link, route) => {
    await revealAuditLink(admin, link);
    return reopenAdminPage(admin, route);
  };
  const upload = async (kind, dob, expected) => {
    const name = `${marker}^${kind}`;
    let frame = await openFrame(uploadLink, 'lab/labUpload');
    await frame.locator('input[type=file]').setInputFiles({ name: `${marker}-${kind}.xml`,
      mimeType: 'application/xml', buffer: batch(phn, dob, name, `${marker}-${kind}`) });
    ({ frame } = await submitInFrame(admin, frame.locator('input[type=submit]'), 'lab/labUpload'));
    h.assert((await frame.locator('body').innerText()).includes('Lab File Successfully Uploaded'), `${kind}: upload failed`);
    const row = sql.rows(`SELECT p.pid_id,p.message_id,l.demographic_no,r.demographic_no,x.observation_results
      FROM hl7_pid p JOIN hl7_link l ON l.pid_id=p.pid_id JOIN hl7_orc c ON c.pid_id=p.pid_id
      JOIN hl7_obr o ON o.pid_id=p.pid_id JOIN hl7_obx x ON x.obr_id=o.obr_id
      JOIN patientLabRouting r ON r.lab_no=p.message_id AND r.lab_type='BCP'
      WHERE p.patient_name=${h.sqlString(name)}`);
    h.assert(row.length === 1 && row[0][2] === expected && row[0][3] === expected && row[0][4] === '5.2',
      `${kind}: segment chain or patient linkage is wrong`);
    return row[0][0];
  };
  await s.step('upload links a unique PHN and date-only birth date', () => upload('match', '19800102', patient));
  await s.step('upload links a unique PHN when DOB is absent', () => upload('nodob', '', patient));
  let unlinked;
  await s.step('a conflicting DOB stays visible and unlinked', async () => { unlinked = await upload('mismatch', '19810102', '0'); });
  await s.step('duplicate PHNs stay unlinked', async () => {
    duplicate = sql.value(`INSERT INTO demographic (last_name,first_name,hin,hc_type,province,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,provider_no,lastUpdateDate)
      VALUES (${h.sqlString(marker)},'Duplicate',${h.sqlString(phn)},'BC','BC','1980','01','02','F','AC',${h.sqlString(s.provider)},NOW()); SELECT LAST_INSERT_ID()`);
    await upload('ambiguous', '19800102', '0');
  });
  await s.step('opening linking page makes no patient changes; selection posts the match', async () => {
    let frame = await openFrame(linkingLink, 'lab/CA/BC/ViewIndex');
    let row = frame.locator('tr').filter({ hasText: `${marker}^mismatch` });
    await row.getByText('Select patient', { exact: true }).waitFor();
    frame = await openFrame(linkingLink, 'lab/CA/BC/ViewIndex');
    row = frame.locator('tr').filter({ hasText: `${marker}^mismatch` });
    h.assert(sql.value(`SELECT demographic_no FROM hl7_link WHERE pid_id=${unlinked}`) === '0', 'Opening page auto-linked a lab');
    const popup = await s.popup(admin, row.getByText('Select patient', { exact: true }), 'pathnet-patient-picker');
    await popup.locator('input[name=keyword]').fill(marker);
    await Promise.all([popup.waitForNavigation(), popup.locator('input[type=submit]').click()]);
    const post = admin.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/lab/CA/BC/ViewIndex'));
    await popup.getByRole('link', { name: patient, exact: true }).click();
    h.assert((await post).status() === 200, 'Patient picker did not submit a successful POST');
    h.assert(sql.value(`SELECT l.demographic_no FROM hl7_link l JOIN hl7_pid p ON p.pid_id=l.pid_id
      JOIN patientLabRouting r ON r.lab_no=p.message_id AND r.lab_type='BCP'
      WHERE l.pid_id=${unlinked} AND l.demographic_no=r.demographic_no AND l.status='N'`) === patient,
    'Manual match did not synchronize legacy and inbox/chart links');
    const rejected = await admin.request.get(h.appUrl(s.config.baseUrl, `/lab/CA/BC/ViewIndex?pid=${unlinked}&demo_id=${duplicate}`));
    h.assert(rejected.status() === 405, 'GET patient-link mutation was accepted');
  });
}
if (require.main === module) runWorkflow('pathnet-patient-link', workflow, { openMaster: false });
module.exports = { batch, workflow };
