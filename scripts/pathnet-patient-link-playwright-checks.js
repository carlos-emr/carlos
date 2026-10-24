/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
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
const { failureMark, consumeExpectedFailure } = require('./lib/concurrency-support');

function batch(phn, dob, name, accession) {
  const obr = Array(26).fill('');
  obr[0] = 'OBR'; obr[1] = '1'; obr[3] = accession; obr[4] = 'GLU^Glucose';
  for (const field of [6, 7, 14, 22]) obr[field] = '20261023090000';
  obr[24] = 'CH'; obr[25] = 'F';
  const hl7 = `MSH|^~\\&|FAKE|PATHNET|CARLOS|TEST|20261023090000||ORU^R01|${accession}|P|2.3\n`
    + `PID|1|${phn}|||${name}||${dob}|M\nORC|RE||${accession}\n`
    + `${obr.join('|')}\n`
    + 'OBX|1|NM|GLU^Glucose||5.2|mmol/L|3.6-6.0|N|||F\n';
  return Buffer.from(`<Messages MessageFormat="ORUR01" Version="2.3" MessageCount="1"><Message><![CDATA[${hl7}]]></Message></Messages>`);
}

async function workflow(s) {
  const { sql, patient, marker, cleanup } = s;
  const phn = `99${String(Number.parseInt(marker.slice(-8), 16)).padStart(8, '0').slice(-8)}`;
  sql.execute(`UPDATE demographic SET hin=${h.sqlString(phn)},hc_type='BC',province='BC' WHERE demographic_no=${patient}`);
  let duplicate;
  const existingLocks = new Set(sql.rows('SELECT lab_no FROM providerLabRoutingLock').map(([id]) => id));
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
        DELETE FROM hl7_message WHERE message_id IN (${list});`);
    }
    const newLocks = ids.filter(id => !existingLocks.has(id));
    if (newLocks.length) sql.execute(`DELETE FROM providerLabRoutingLock WHERE lab_no IN (${newLocks.join(',')})`);
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
      mimeType: 'application/xml', buffer: batch(phn, dob, name, `F-${marker.slice(-8)}${kind}`) });
    ({ frame } = await submitInFrame(admin, frame.locator('input[type=submit]'), 'lab/labUpload'));
    const resultText = await frame.locator('body').innerText();
    h.assert(resultText.includes('Lab File Successfully Uploaded'), `${kind}: upload failed: ${resultText.slice(-700)}`);
    const row = sql.rows(`SELECT p.pid_id,p.message_id,l.demographic_no,r.demographic_no,x.observation_results,l.status
      FROM hl7_pid p JOIN hl7_link l ON l.pid_id=p.pid_id JOIN hl7_orc c ON c.pid_id=p.pid_id
      JOIN hl7_obr o ON o.pid_id=p.pid_id JOIN hl7_obx x ON x.obr_id=o.obr_id
      JOIN patientLabRouting r ON r.lab_no=p.message_id AND r.lab_type='BCP'
      WHERE p.patient_name=${h.sqlString(name)}`);
    h.assert(row.length === 1 && row[0][2] === expected && row[0][3] === expected && row[0][4] === '5.2',
      `${kind}: segment chain or patient linkage is wrong`);
    h.assert(row[0][5] === (expected === '0' ? 'P' : 'N'), `${kind}: legacy link status is wrong`);
    return row[0][0];
  };
  await s.step('upload links a unique PHN and date-only birth date', () => upload('match', '19800102', patient));
  let noDob;
  await s.step('upload links a unique PHN when DOB is absent', async () => { noDob = await upload('nodob', '', patient); });
  let unlinked;
  await s.step('a conflicting DOB stays visible and unlinked', async () => { unlinked = await upload('mismatch', '19810102', '0'); });
  await s.step('malformed DOB stays unlinked despite an exact PHN', () => upload('malformed', '19800102junk', '0'));
  await s.step('duplicate PHNs stay unlinked', async () => {
    duplicate = sql.value(`INSERT INTO demographic (last_name,first_name,hin,hc_type,province,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,provider_no,lastUpdateDate)
      VALUES (${h.sqlString(marker)},'Duplicate',${h.sqlString(phn)},'BC','BC','1980','01','02','F','AC',${h.sqlString(s.provider)},NOW()); SELECT LAST_INSERT_ID()`);
    await upload('ambiguous', '19800102', '0');
  });
  await s.step('opening linking page makes no patient changes; selection posts the match', async () => {
    let frame = await openFrame(linkingLink, 'lab/CA/BC/ViewIndex');
    h.assert(await frame.getByText(`${marker}^nodob`, { exact: true }).count() === 0,
      'An automatically matched lab is still listed as unlinked');
    const noDobReport = await s.context.newPage();
    await noDobReport.goto(h.appUrl(s.config.baseUrl, `/lab/CA/BC/ViewSearchReports?cmd_search=search&provider_no=${s.provider}`));
    await noDobReport.getByText(`${marker}^nodob`, { exact: true }).waitFor();
    await noDobReport.goto(h.appUrl(s.config.baseUrl, `/lab/CA/BC/ViewReport?pid=${noDob}`));
    h.assert((await noDobReport.locator('body').innerText()).includes('5.2'), 'A missing DOB prevented displaying the observation');
    await noDobReport.close();
    let row = frame.locator('tr').filter({ hasText: `${marker}^mismatch` });
    await row.getByRole('button', { name: 'Select patient', exact: true }).waitFor();
    const report = await s.popup(admin, row.getByRole('link', { name: `${marker}^mismatch`, exact: true }), 'pathnet-result');
    h.assert((await report.locator('body').innerText()).includes('5.2'), 'The stored observation is missing from the report');
    await report.close();
    // A bad later checkbox must never leave the earlier explicit match committed,
    // including a numerically valid identifier rejected inside the transaction.
    for (const invalid of ['invalid', '2147483647']) {
      frame = await openFrame(linkingLink, 'lab/CA/BC/ViewIndex');
      const failure = failureMark(s.recorder);
      const rejectedPost = admin.waitForResponse(r => r.request().method() === 'POST'
        && new URL(r.url()).pathname.endsWith('/lab/CA/BC/ViewIndex'));
      await frame.locator('#patientLinkForm').evaluate((form, selection) => {
        form.elements.pid.value = selection.pid;
        form.elements.demo_id.value = selection.patient;
        const checkbox = document.createElement('input');
        checkbox.type = 'hidden'; checkbox.name = 'chk'; checkbox.value = selection.invalid;
        form.appendChild(checkbox);
        form.requestSubmit();
      }, { pid: unlinked, patient, invalid });
      h.assert((await rejectedPost).status() === 400, 'Invalid later selection was accepted');
      await frame.waitForLoadState('load');
      consumeExpectedFailure(s.recorder, failure, { status: 400, path: /\/lab\/CA\/BC\/ViewIndex$/ });
      h.assert(sql.value(`SELECT COUNT(*) FROM hl7_link l JOIN hl7_pid p ON p.pid_id=l.pid_id
        JOIN patientLabRouting r ON r.lab_no=p.message_id AND r.lab_type='BCP'
        WHERE l.pid_id=${unlinked} AND l.demographic_no=0 AND l.status='P' AND r.demographic_no=0`) === '1',
      'A rejected batch left the earlier patient match committed');
    }
    frame = await openFrame(linkingLink, 'lab/CA/BC/ViewIndex');
    row = frame.locator('tr').filter({ hasText: `${marker}^mismatch` });
    h.assert(sql.value(`SELECT demographic_no FROM hl7_link WHERE pid_id=${unlinked}`) === '0', 'Opening page auto-linked a lab');
    const popup = await s.popup(admin, row.getByRole('button', { name: 'Select patient', exact: true }), 'pathnet-patient-picker');
    await popup.getByLabel('Patient Search', { exact: true }).fill(marker);
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
    h.assert(sql.value(`SELECT COUNT(*) FROM hl7_link l JOIN hl7_pid p ON p.pid_id=l.pid_id
      WHERE p.patient_name IN (${h.sqlString(`${marker}^malformed`)},${h.sqlString(`${marker}^ambiguous`)})
      AND l.demographic_no=0`) === '2', 'Manual linking changed other unmatched reports');
  });
}
if (require.main === module) runWorkflow('pathnet-patient-link', workflow, { openMaster: false });
module.exports = { batch, workflow };
