#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Preferences > HL7 result preferences > offer filing; Inbox > synthetic HL7 lab > File for...
 * Exercises real CSRF, disabled opt-outs, atomic rejection, filing attribution and audit, and
 * the combined acknowledge dialog. Owns one synthetic lab and two throwaway providers.
 * Restores the test login's exact offer preference. Run exclusively (EXCLUSIVE=1).
 */
'use strict';
const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');
const {throwawayLoginFixture} = require('./lib/throwaway-login-fixture');
const {syntheticCmlLab} = require('./lab-upload-playwright-checks');
const {uploadLab, openInbox, openSidebar, setMode, search} = require('./inbox-file-forward-playwright-checks');
const {removeOwnedHl7Labs, removeArchiveFiles, archivesNamed} = require('./lab-forwarding-rules-playwright-checks');
const q = h.sqlString;

async function workflow(s) {
  const {sql, provider, marker, config} = s;
  const allowed = throwawayLoginFixture({sql, provider, marker, testUser: config.testUser});
  const denied = throwawayLoginFixture({sql, provider, marker: `FAKE-PW${crypto.randomBytes(8).toString('hex')}`,
    testUser: config.testUser});
  s.cleanup(() => allowed.cleanup()); s.cleanup(() => denied.cleanup());
  const offerScope = `provider_no=${q(provider)} AND name='hl7_offer_file_for_others'`;
  const original = sql.rows(`SELECT id, IFNULL(HEX(value),'NULL') FROM property WHERE ${offerScope}`);
  h.assert(original.every(([id, value]) => /^\d+$/.test(id) && /^(NULL|[0-9A-F]*)$/.test(value)), 'Invalid preference snapshot');
  s.cleanup(() => {
    const ids = original.map(([id]) => id);
    sql.execute(`DELETE FROM property WHERE ${offerScope}${ids.length ? ` AND id NOT IN (${ids.join(',')})` : ''}`);
    for (const [id, value] of original) sql.execute(`UPDATE property SET value=${value === 'NULL' ? 'NULL' : `UNHEX('${value}')`}
      WHERE id=${id} AND ${offerScope}`);
  });
  const workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-file-others-'));
  const accession = `FO${crypto.randomBytes(5).toString('hex').toUpperCase()}`;
  const fileName = `lab-file-for-others-${accession}.hl7`;
  let labNo;
  s.cleanup(() => {
    const ids = sql.rows(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum=${q(accession)}`).map(([id]) => id);
    removeOwnedHl7Labs(sql, ids);
    sql.execute(`DELETE FROM fileUploadCheck WHERE filename LIKE ${q(`LabUpload.${fileName}.%`)}`);
    removeArchiveFiles(archivesNamed(fileName));
    fs.rmSync(workDir, {recursive: true, force: true});
  });
  allowed.create(); denied.create();
  // Only the owned provider opts in; the second provider exercises the default-off behaviour.
  sql.execute(`INSERT INTO property (provider_no,name,value) VALUES (${q(allowed.providerNo)},'hl7_allow_others_file_for_you','true')`);
  let prefs;
  await s.step('HL7 preferences are reachable and offer filing can be enabled', async () => {
    const personal = await s.popup(s.schedule, s.schedule.getByTitle(/Edit your personal setting/i).first(), 'personal-preferences');
    await personal.locator('[data-bs-target="#secLab"]').click();
    prefs = await s.popup(personal, personal.locator('a[href*="method=viewHl7LabResultPrefs"]'), 'hl7-preferences');
    const toggle = prefs.locator('#offerFileForOthers');
    if (!await toggle.isChecked()) {
      const response = prefs.waitForResponse(r => r.request().method() === 'POST'
        && r.url().includes('method=setOfferFileForOthersPref'));
      await toggle.check(); h.assert((await response).ok(), 'Offer preference was refused');
    }
    h.assert(await toggle.isChecked(), 'Offer preference did not remain checked');
    await prefs.close(); await personal.close();
  });
  await s.step('a routed lab offers consenting colleagues and disables opted-out colleagues', async () => {
    const file = path.join(workDir, fileName); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- generated filename in owned temp directory
    fs.writeFileSync(file, Buffer.from(syntheticCmlLab(accession, marker), 'latin1'));
    h.assert(await uploadLab(s, file, fileName) === 'Uploaded successfully', 'Synthetic lab upload failed');
    labNo = sql.value(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum=${q(accession)}`);
    h.assert(/^[1-9]\d*$/.test(labNo), 'Missing synthetic lab');
    for (const target of [allowed.providerNo, denied.providerNo]) sql.execute(`INSERT INTO providerLabRouting
      (provider_no,lab_no,lab_type,status,comment) VALUES (${q(target)},${labNo},'HL7','N','Preserved comment')`);
    sql.execute(`UPDATE demographic SET hin='' WHERE demographic_no=${s.patient} AND last_name=${q(marker)}`);
  });
  const inbox = await openInbox(s.schedule, s.context, s.recorder, 'filing-inbox');
  await setMode(inbox, 'list'); await openSidebar(inbox);
  await inbox.locator('#statusNew').check(); await inbox.locator('#specificPatients').check();
  await inbox.locator('#inputLastName').fill(marker); await search(inbox);
  const row = inbox.locator(`tr[data-lab-type="HL7"][data-segment-id="${labNo}"]`).first();
  const popup = await s.popup(inbox, row.locator('a[onclick*="reportWindow"]').first(), 'file-for-others-lab');
  const dialog = popup.locator(`#labFiling_${labNo}`);
  await s.step('crafted opt-out and unrouted requests are refused without partial filing', async () => {
    await popup.locator(`[data-file-for="${labNo}"]`).first().click();
    h.assert(await dialog.locator(`input[value="${allowed.providerNo}"]`).isEnabled(), 'Opted-in provider is disabled');
    h.assert(await dialog.locator(`input[value="${denied.providerNo}"]`).isDisabled(), 'Opted-out provider is selectable');
    const states = () => JSON.stringify(sql.rows(`SELECT id,status,comment FROM providerLabRouting WHERE lab_no=${labNo} AND lab_type='HL7' ORDER BY id`));
    const before = states();
    for (const targets of [[allowed.providerNo, denied.providerNo], [allowed.providerNo, '999997']]) {
      const token = await popup.locator('input[name="CSRF-TOKEN"]').evaluateAll(inputs => inputs.map(i => i.value).find(Boolean));
      const endpoint = await dialog.getAttribute('data-endpoint');
      const data = new URLSearchParams({labNo, labType: 'HL7', onBehalfOfOtherProvider: 'false'});
      targets.forEach(p => data.append('providerNo', p));
      const response = await s.context.request.post(new URL(endpoint, popup.url()).href, {
        headers: {'CSRF-TOKEN': token, 'X-Requested-With': 'XMLHttpRequest',
          'Content-Type': 'application/x-www-form-urlencoded'}, data: data.toString()});
      h.assert(response.status() === 403, `Crafted filing returned ${response.status()}`);
      h.assert((await response.json()).error === 'Filing was refused', 'Refusal did not come from the filing endpoint');
      h.assert(states() === before, 'Rejected batch wrote routing rows');
    }
    await dialog.locator('[data-filing-cancel]').click();
  });
  await s.step('File for changes only the consenting provider and preserves attribution and audit', async () => {
    await popup.locator(`[data-file-for="${labNo}"]`).first().click();
    await dialog.locator(`input[value="${allowed.providerNo}"]`).check();
    const response = popup.waitForResponse(r => r.request().method() === 'POST' && r.url().includes('/oscarMDS/FileLabsOnBehalf'));
    await dialog.locator('[type="submit"]').click();
    h.assert((await response).ok(), 'On-behalf filing failed');
    await popup.waitForLoadState('networkidle');
    h.assert(sql.value(`SELECT status FROM providerLabRouting WHERE lab_no=${labNo} AND provider_no=${q(allowed.providerNo)} AND lab_type='HL7'`) === 'F', 'Target route was not filed');
    h.assert(sql.value(`SELECT status FROM providerLabRouting WHERE lab_no=${labNo} AND provider_no=${q(denied.providerNo)} AND lab_type='HL7'`) === 'N', 'Opted-out route changed');
    const comment = sql.value(`SELECT comment FROM providerLabRouting WHERE lab_no=${labNo} AND provider_no=${q(allowed.providerNo)} AND lab_type='HL7'`);
    h.assert(comment.startsWith('Preserved comment\nFiled by ') && comment.includes(' on behalf of '), 'Filing attribution or original comment is missing');
    h.assert(sql.value(`SELECT COUNT(*) FROM log WHERE action='lab.fileOnBehalf' AND content=${q(labNo)}
      AND data=${q(`filedFor=${allowed.providerNo}`)} AND provider_no=${q(provider)}`) === '1', 'Strict filing audit is missing');
  });
  await s.step('Acknowledge opens the combined dialog after on-behalf filing', async () => {
    await popup.locator('input[onclick*="openLabFilingDialog"][onclick*="true"]').first().click();
    await dialog.waitFor({state: 'visible'});
    h.assert(await dialog.locator('[data-filing-comment]').isVisible(), 'Combined dialog hides the acknowledgement comment');
    h.assert(await dialog.locator('details').getAttribute('open') !== null, 'Combined dialog filing section is collapsed');
    await dialog.locator('[data-filing-cancel]').click();
  });
}
if (require.main === module) runWorkflow('lab-file-for-others', workflow, {openMaster: false});
module.exports = {workflow};
