#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Lab forwarding rules, end to end (coverage plan: lab-forwarding-rules).
 *
 * User path, as a THROWAWAY provider login (so the shared test login's own rules, which other
 * checks route through, are never touched): Schedule ▸ Inbox ▸ Forwarding Rules (popup,
 * oscarMDS/ForwardingRules) ▸ pick the owned target provider, status New ▸ Update; then Inbox ▸
 * HL7 Lab Upload with a synthetic CML lab ordered by the throwaway (OBR-16 = its OHIP number);
 * then the lab from the Inbox list ▸ Label (lab/CA/ALL/createLabLabel) ▸ Unlink
 * (lab/CA/ALL/UnlinkDemographic); then Forwarding Rules ▸ Remove. Last, as the test login:
 * Schedule ▸ Administration ▸ Labs/Inbox ▸ Lab Forwarding Rules (admin/labForwardingRules panel
 * posting admin/ForwardingRules) ▸ choose the throwaway in the provider list.
 * Asserts: the incomingLabRules row (status, archive), the providerLabRouting rows the upload
 * gives the source AND the forward target, hl7TextInfo.label, the unlink's patientLabRouting
 * reset and the audit comment on each routing row, the rule's archive on Remove, and that the
 * admin panel shows the chosen provider's rules.
 * Fixtures: the throwaway login (lib/throwaway-login-fixture.js) with an owned OHIP number, an
 * owned target provider, the run's FAKE- patient, the lab (unique accession) and its archived
 * upload file. Cleanup removes exactly those rows/files and asserts they are gone.
 */
const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');
const { syntheticCmlLab } = require('./lab-upload-playwright-checks');
const { settle } = require('./inboxhub-filters-playwright-checks');

const TIMEOUT = 30000;
const ARCHIVE_NAME = /^LabUpload\.[A-Za-z0-9._-]+\.hl7\.\d+$/;

/** Unused six-digit number in the 8xxxxx range for provider_no or ohip_no. */
function unusedNumber(sql, column) {
  for (let attempt = 0; attempt < 20; attempt++) {
    const candidate = String(crypto.randomInt(800000, 899999));
    const taken = sql.value(`SELECT (SELECT COUNT(*) FROM provider WHERE ${column}=${h.sqlString(candidate)})
      + (SELECT COUNT(*) FROM security WHERE provider_no=${h.sqlString(candidate)})`);
    if (taken === '0') return candidate;
  }
  throw new Error(`No unused ${column} was found`);
}

/**
 * Removes every row an uploaded or form-created HL7 lab writes (routing, measurements, the
 * message and its checksum) for the given lab numbers, plus the archived upload file named by
 * each of the lab's fileUploadCheck rows, and asserts nothing remains. A checksum row whose
 * filename is not an archive name the uploader generates is still deleted, but its file is not
 * guessed at: cleanup fails and names it, so a changed naming scheme never leaks silently.
 */
function removeOwnedHl7Labs(sql, labNos) {
  const labs = [...new Set(labNos.map(String))].filter((labNo) => /^[1-9]\d*$/.test(labNo));
  if (!labs.length) return;
  const list = labs.join(',');
  const checks = sql.rows(`SELECT DISTINCT f.id, f.filename FROM fileUploadCheck f
    JOIN hl7TextMessage m ON m.fileUploadCheck_id=f.id WHERE m.lab_id IN (${list})`)
    .filter(([id]) => /^[1-9]\d*$/.test(id));
  const checkIds = checks.map(([id]) => id).join(',');
  const archives = checks.map(([, name]) => name).filter((name) => ARCHIVE_NAME.test(name));
  const unexpected = checks.map(([, name]) => name).filter((name) => !ARCHIVE_NAME.test(name));
  const measurements = sql.rows(`SELECT measurement_id FROM measurementsExt
    WHERE keyval='lab_no' AND val IN (${labs.map((labNo) => h.sqlString(labNo)).join(',')})`)
    .map(([id]) => id).filter((id) => /^\d+$/.test(id));
  if (measurements.length) {
    const ids = measurements.join(',');
    sql.execute(`DELETE FROM measurementsExt WHERE measurement_id IN (${ids});
      DELETE FROM measurements WHERE id IN (${ids})`);
  }
  sql.execute(`DELETE FROM providerLabRouting WHERE lab_type='HL7' AND lab_no IN (${list});
    DELETE FROM patientLabRouting WHERE lab_type='HL7' AND lab_no IN (${list});
    DELETE FROM hl7TextInfo WHERE lab_no IN (${list});
    DELETE FROM hl7TextMessage WHERE lab_id IN (${list})`);
  if (checkIds) sql.execute(`DELETE FROM fileUploadCheck WHERE id IN (${checkIds})`);
  removeArchiveFiles(archives);
  h.assert(sql.value(`SELECT
      (SELECT COUNT(*) FROM providerLabRouting WHERE lab_type='HL7' AND lab_no IN (${list}))
    + (SELECT COUNT(*) FROM patientLabRouting WHERE lab_type='HL7' AND lab_no IN (${list}))
    + (SELECT COUNT(*) FROM hl7TextInfo WHERE lab_no IN (${list}))
    + (SELECT COUNT(*) FROM hl7TextMessage WHERE lab_id IN (${list}))
    + ${checkIds ? `(SELECT COUNT(*) FROM fileUploadCheck WHERE id IN (${checkIds}))` : '0'}`) === '0',
  'The synthetic lab rows were not all removed');
  h.assert(!unexpected.length,
    `Archived lab upload(s) not removed: the stored name is not a generated archive name: ${JSON.stringify(unexpected)}`);
}

/**
 * Deletes the named archives (each already matched ARCHIVE_NAME, so no separators) from
 * LAB_UPLOAD_DOCUMENT_STORE and asserts they are gone; without the store it warns and keeps them.
 */
function removeArchiveFiles(names) {
  if (!names.length) return;
  const store = process.env.LAB_UPLOAD_DOCUMENT_STORE;
  if (!store) {
    console.warn(`    archived lab upload retained: set LAB_UPLOAD_DOCUMENT_STORE to remove ${names.join(', ')}`);
    return;
  }
  const root = fs.realpathSync(store);
  for (const name of names) {
    h.assert(ARCHIVE_NAME.test(name), 'Refusing to remove a file that is not a generated lab archive name');
    // name matched ARCHIVE_NAME (no separators) and is joined to the resolved store root.
    const file = path.join(root, name); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal
    if (fs.existsSync(file)) fs.unlinkSync(file);
    h.assert(!fs.existsSync(file), 'An archived synthetic lab upload was not removed');
  }
}

/**
 * The run's own archives found by the known upload name (LabUpload.<fileName>.<millis>), so a
 * file the uploader saved before a rejected or rolled-back upload (no committed lab row to
 * discover it through) is still found. Without the store it warns with the run's archive name
 * prefix (as removeArchiveFiles does for known names) and returns none, so a retained file is
 * always reported.
 */
function archivesNamed(fileName) {
  const store = process.env.LAB_UPLOAD_DOCUMENT_STORE;
  if (!store) {
    console.warn(`    archived lab upload may be retained: set LAB_UPLOAD_DOCUMENT_STORE to check/remove LabUpload.${fileName}.*`);
    return [];
  }
  const prefix = `LabUpload.${fileName}.`;
  return fs.readdirSync(fs.realpathSync(store))
    .filter((name) => name.startsWith(prefix) && /^\d+$/.test(name.slice(prefix.length)) && ARCHIVE_NAME.test(name));
}

/**
 * The Label button's fetch() never reads the action's empty 200 reply, and Chromium cancels the
 * unread body (CDP loadingFailed canceled=true, net::ERR_ABORTED) after the page already handled
 * the response. Consume exactly that one entry, only once the database proved the save; any other
 * failure stays strict.
 */
function consumeDiscardedLabelBody(recorder, since) {
  const added = recorder.requestFailures.slice(since);
  const index = added.findIndex((entry) => entry.label === 'fwd-lab-display' && entry.resourceType === 'fetch'
    && entry.errorText === 'net::ERR_ABORTED' && new URL(entry.url).pathname.endsWith('/lab/CA/ALL/createLabLabel'));
  if (index >= 0) recorder.requestFailures.splice(since + index, 1);
}

/** Schedule ▸ Inbox (popup or same tab, by deployment), settled. */
async function openInbox(schedule, recorder, label) {
  const { page } = await ui.clickOpensPopupOrNavigates(schedule, schedule.locator('#inboxLink').first(),
    { context: schedule.context(), recorder, label, timeout: TIMEOUT });
  await settle(page, 60000);
  return page;
}

/** Inbox ▸ HL7 Lab Upload: choose the file and CML, Upload; returns the status the page reports. */
async function uploadFromInbox(inbox, recorder, filePath, fileName) {
  const link = inbox.locator('a[href*="ViewInsideLabUpload"]').first();
  const popup = await ui.clickOpensPopup(inbox, link, { context: inbox.context(), recorder, label: 'fwd-lab-upload', timeout: TIMEOUT });
  try {
    await popup.locator('#importFiles').setInputFiles(filePath);
    await popup.locator('#type').selectOption('CML');
    const [response] = await Promise.all([
      popup.waitForResponse((r) => r.request().method() === 'POST'
        && new URL(r.url()).pathname.endsWith('/lab/CA/ALL/insideLabUpload'), { timeout: 60000 }),
      popup.locator('#uploadForm button[type="submit"]').click(),
    ]);
    h.assert(response.status() < 400, `HL7 lab upload answered HTTP ${response.status()}`);
    const item = popup.locator('#file-list .file-item').filter({ hasText: fileName }).first();
    await item.waitFor({ state: 'visible', timeout: TIMEOUT });
    return (await item.locator('.upload-text').innerText()).trim();
  } finally {
    await popup.close().catch(() => {});
  }
}

/** Inbox ▸ Forwarding Rules popup for the logged-in provider. */
async function openForwardingRules(inbox, recorder) {
  const link = inbox.locator('a[href*="oscarMDS/ForwardingRules"]').first();
  const popup = await ui.clickOpensPopup(inbox, link, { context: inbox.context(), recorder, label: 'fwd-rules', timeout: TIMEOUT });
  await popup.locator('form[name="RULES"]').waitFor({ state: 'attached', timeout: TIMEOUT });
  return popup;
}

/** Click a RULES form control that asks confirm(), accept it and wait for the page to reload. */
async function confirmAndReload(page, locator, what) {
  const dialogs = await h.withExpectedDialogs(page, async () => {
    await ui.clickAndAwaitReload(page, locator, { timeout: TIMEOUT, label: what });
  });
  h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', `${what} did not ask for exactly one confirmation`);
}

async function workflow(s) {
  const { sql, config, recorder, marker, patient } = s;
  const stamp = crypto.randomBytes(4).toString('hex').toUpperCase();
  const accession = `FW${stamp}`;
  const fileName = `lab-forwarding-probe-${stamp}.hl7`;
  const fixture = throwawayLoginFixture({ sql, marker, provider: s.provider, testUser: config.testUser });
  let target = null;
  let labNo = null;
  const workDir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-lab-fwd-'));
  const ownedRules = () => `provider_no IN (${[fixture.providerNo, target].filter(Boolean).map(h.sqlString).join(',')})`;

  s.cleanup(() => fixture.cleanup());
  s.cleanup(() => {
    if (!target) return;
    sql.execute(`DELETE FROM provider WHERE provider_no=${h.sqlString(target)} AND last_name=${h.sqlString(marker)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no=${h.sqlString(target)}`) === '0',
      'The owned forward-target provider was not removed');
  });
  s.cleanup(() => {
    fs.rmSync(workDir, { recursive: true, force: true });
    const labs = sql.rows(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum=${h.sqlString(accession)}`).map(([id]) => id);
    if (labNo) labs.push(labNo);
    removeOwnedHl7Labs(sql, labs);
    // A rejected or failed upload leaves its archive (and possibly a checksum row) without any
    // lab row; the run's unique upload name still identifies both.
    sql.execute(`DELETE FROM fileUploadCheck WHERE filename LIKE ${h.sqlString(`LabUpload.${fileName}.%`)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM fileUploadCheck WHERE filename LIKE ${h.sqlString(`LabUpload.${fileName}.%`)}`) === '0',
      'The run\'s lab upload checksum row was not removed');
    removeArchiveFiles(archivesNamed(fileName));
    if (fixture.providerNo) {
      // Types cascade from their rule (FOREIGN KEY ... ON DELETE CASCADE).
      sql.execute(`DELETE FROM incomingLabRules WHERE ${ownedRules()}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM incomingLabRules WHERE ${ownedRules()}`) === '0',
        'The owned forwarding rules were not removed');
    }
  });

  fixture.create();
  const source = fixture.providerNo;
  const ohip = unusedNumber(sql, 'ohip_no');
  sql.execute(`UPDATE provider SET ohip_no=${h.sqlString(ohip)} WHERE provider_no=${h.sqlString(source)}
    AND last_name=${h.sqlString(marker)}`);
  target = unusedNumber(sql, 'provider_no');
  sql.execute(`INSERT INTO provider (provider_no,last_name,first_name,provider_type,specialty,sex,status,ohip_no,lastUpdateUser,lastUpdateDate)
    VALUES (${h.sqlString(target)},${h.sqlString(marker)},'Target','doctor','','F','1','',${h.sqlString(s.provider)},NOW())`);
  h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no IN (${h.sqlString(source)},${h.sqlString(target)})
    AND last_name=${h.sqlString(marker)}`) === '2', 'The owned providers were not created');
  h.assert(sql.value(`SELECT COUNT(*) FROM incomingLabRules WHERE ${ownedRules()}`) === '0',
    'An owned provider already had forwarding rules');
  const rule = `SELECT status, archive FROM incomingLabRules WHERE provider_no=${h.sqlString(source)}
    AND frwdProvider_no=${h.sqlString(target)} ORDER BY id DESC LIMIT 1`;

  const other = await h.newContext(s.context.browser(), config);
  other.setDefaultTimeout(TIMEOUT);
  other.on('page', (page) => h.wireStrictPage(page, 'fwd-throwaway', recorder));
  const schedule = await h.login(other, { ...config, testUser: fixture.username }, recorder, { label: 'fwd-throwaway' });
  const inbox = await openInbox(schedule, recorder, 'fwd-throwaway-inbox');

  await s.step('Inbox ▸ Forwarding Rules saves a forward-to rule for the logged-in provider', async () => {
    const rules = await openForwardingRules(inbox, recorder);
    h.assert(await rules.getByText('There are no forwarding rules set').count() === 1,
      'A fresh provider did not start with no forwarding rules');
    await rules.locator('#statusNew').check();
    await rules.locator('select[name="providerNums"]').selectOption(target);
    await confirmAndReload(rules, rules.getByRole('button', { name: 'Update Forwarding Rules' }), 'Update Forwarding Rules');
    const [[status, archive]] = sql.rows(rule);
    h.assert(status === 'N' && archive === '0', `The saved rule reads status=${status} archive=${archive}`);
    const forwarded = rules.locator('ul li').filter({ hasText: marker });
    h.assert(await forwarded.count() === 1 && (await forwarded.innerText()).includes('Target'),
      'The reloaded page does not list the forward target');
    await rules.close();
  });

  await s.step('an HL7 lab ordered by the provider is routed to it and to its forward target', async () => {
    const filePath = path.join(workDir, fileName); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal
    // Same synthetic CML message as lab-upload, ordered by the throwaway's OHIP number.
    fs.writeFileSync(filePath, Buffer.from(syntheticCmlLab(accession, marker)
      .replaceAll('999998^DR. PROBE', `${ohip}^${marker}`), 'latin1'));
    const status = await uploadFromInbox(inbox, recorder, filePath, fileName);
    h.assert(status === 'Uploaded successfully', `The upload reported "${status}"`);
    await expectValue(sql, `SELECT COUNT(*) FROM hl7TextInfo WHERE accessionNum=${h.sqlString(accession)}`, '1',
      'The uploaded lab did not reach hl7TextInfo');
    labNo = sql.value(`SELECT lab_no FROM hl7TextInfo WHERE accessionNum=${h.sqlString(accession)}`);
    h.assert(/^[1-9]\d*$/.test(labNo), 'The uploaded lab has no lab number');
    const routed = sql.rows(`SELECT provider_no, status FROM providerLabRouting WHERE lab_type='HL7' AND lab_no=${labNo}
      AND provider_no IN (${h.sqlString(source)},${h.sqlString(target)}) ORDER BY provider_no=${h.sqlString(target)}`);
    h.assert(JSON.stringify(routed) === JSON.stringify([[source, 'N'], [target, 'N']]),
      `Routing for the ordering provider and forward target was ${JSON.stringify(routed.map(([, st]) => st))}`);
    h.assert(sql.value(`SELECT demographic_no FROM patientLabRouting WHERE lab_type='HL7' AND lab_no=${labNo}`) === patient,
      'The uploaded lab was not matched to the run patient');
  });

  let report;
  await s.step('Label on the lab display stores the typed label on the lab', async () => {
    // Search again so the list includes the lab uploaded after it was first drawn.
    if (!await inbox.locator('#inbox-sidebar').isVisible()) await inbox.locator('#inbox-sidebar-toggle').click();
    await ui.clickAndAwaitReload(inbox, inbox.locator('#inboxhubFormSearchBtn'), { timeout: TIMEOUT, label: 'Inbox search' });
    await settle(inbox, 60000);
    const row = inbox.locator(`tr[data-lab-type="HL7"][data-segment-id="${labNo}"]`);
    await row.first().waitFor({ state: 'visible', timeout: TIMEOUT });
    report = await ui.clickOpensPopup(inbox, row.locator('a[onclick*="reportWindow"]').first(),
      { context: other, recorder, label: 'fwd-lab-display', timeout: TIMEOUT });
    const label = `FAKE ${stamp}`;
    await report.locator(`#acklabel_${labNo}`).fill(label);
    const failuresBefore = recorder.requestFailures.length;
    const [response] = await Promise.all([
      report.waitForResponse((r) => r.request().method() === 'POST'
        && new URL(r.url()).pathname.endsWith('/lab/CA/ALL/createLabLabel'), { timeout: TIMEOUT }),
      report.locator(`#createLabel_${labNo}`).click(),
    ]);
    h.assert(response.status() === 200, `createLabLabel answered HTTP ${response.status()}`);
    await expectValue(sql, `SELECT label FROM hl7TextInfo WHERE lab_no=${labNo}`, label,
      'hl7TextInfo.label does not hold the typed label');
    await report.waitForFunction(({ id, text }) => document.querySelector(`#labelspan_${id} i`)?.textContent.trim() === text,
      { id: labNo, text: label }, { timeout: TIMEOUT });
    consumeDiscardedLabelBody(recorder, failuresBefore);
  });

  await s.step('Unlink detaches the lab from the patient and returns it to both inboxes as new', async () => {
    const before = sql.rows(`SELECT provider_no FROM providerLabRouting WHERE lab_type='HL7' AND lab_no=${labNo}`).length;
    const reason = `FAKE wrong patient ${stamp}`;
    const dialogs = await h.withExpectedDialogs(report, async () => {
      const reloaded = report.waitForEvent('framenavigated', { predicate: (f) => f === report.mainFrame(), timeout: TIMEOUT });
      const [response] = await Promise.all([
        report.waitForResponse((r) => r.request().method() === 'POST'
          && new URL(r.url()).pathname.endsWith('/lab/CA/ALL/UnlinkDemographic'), { timeout: TIMEOUT }),
        report.getByRole('button', { name: 'Unlink' }).click(),
      ]);
      h.assert(response.status() === 200, `UnlinkDemographic answered HTTP ${response.status()}`);
      // The page reloads itself (and its opener) only when the JSON reply says success.
      await reloaded;
    }, { promptText: reason });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'prompt', 'Unlink did not ask for a reason exactly once');
    h.assert(sql.value(`SELECT demographic_no FROM patientLabRouting WHERE lab_type='HL7' AND lab_no=${labNo}`) === '0',
      'The lab is still linked to the patient');
    const rows = sql.rows(`SELECT status, comment FROM providerLabRouting WHERE lab_type='HL7' AND lab_no=${labNo}`);
    h.assert(rows.length === before, 'Unlinking changed the set of providers the lab is routed to');
    for (const [status, comment] of rows) {
      h.assert(status === 'N', 'A routing row was not returned to New');
      h.assert(comment === `Lab unlinked from incorrect demographic number: ${patient}`,
        'The routing row comment is not the unlink note');
    }
    await report.close();
  });

  await s.step('Forwarding Rules ▸ Remove archives the rule for the target', async () => {
    const rules = await openForwardingRules(inbox, recorder);
    const remove = rules.locator('ul li').filter({ hasText: marker }).getByRole('link', { name: 'Remove' });
    await confirmAndReload(rules, remove, 'Remove forward target');
    // Remove's link is href="#" and submits the form from its handler, so the hash change can
    // be observed before the POST's reply replaces the page: wait for the reply's content.
    await expectValue(sql, `SELECT COUNT(*) FROM incomingLabRules WHERE provider_no=${h.sqlString(source)} AND archive='0'`, '0',
      'Removing the target left an active rule for the provider');
    h.assert(await rules.getByText('There are no forwarding rules set').waitFor({ timeout: TIMEOUT }).then(() => true, () => false),
      'The reloaded page still shows forwarding rules');
    await rules.close();
  });

  await s.step('Administration ▸ Lab Forwarding Rules shows and saves the chosen provider\'s rules', async () => {
    const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
      { context: s.context, recorder, label: 'fwd-administration', timeout: TIMEOUT });
    const link = admin.locator('#adminNav a.contentLink[href$="/admin/labForwardingRules"]').first();
    await revealAuditLink(admin, link, TIMEOUT);
    const panel = await ui.clickInjectsPanel(admin, link, { marker: '#provider-selection', timeout: TIMEOUT });
    // Choosing a provider must load that provider's rules; with none chosen the page can only
    // say "no provider selected" and its Update refuses to save.
    await admin.locator('#provider-selection').selectOption(source);
    await admin.waitForFunction((value) => {
      const select = document.querySelector('#dynamic-content #provider-selection');
      return select && select.value === value && document.querySelector('#dynamic-content form[name="RULES"] .text-info');
    }, source, { timeout: 10000 }).catch(() => {});
    h.assert(await panel.locator('.text-info').count() === 1,
      'Choosing a provider in Lab Forwarding Rules did not load that provider\'s rules');
    await admin.locator('select[name="providerNums"]').selectOption(target);
    await h.withExpectedDialogs(admin, async () => {
      await admin.locator('#ForwardRulesForm input[type="submit"]').click();
      await expectValue(sql, `SELECT COUNT(*) FROM incomingLabRules WHERE provider_no=${h.sqlString(source)}
        AND frwdProvider_no=${h.sqlString(target)} AND archive='0'`, '1',
      'Administration ▸ Lab Forwarding Rules did not save the forward-to rule');
    });
  });
}

if (require.main === module) runWorkflow('lab-forwarding-rules', workflow, { openMaster: false });
module.exports = { workflow, removeOwnedHl7Labs, removeArchiveFiles, archivesNamed };
