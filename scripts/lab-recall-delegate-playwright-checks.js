#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * lab-recall-delegate — a provider's lab recall settings and the lab Recall button they drive:
 *   Schedule ▸ Preferences ▸ Lab Recall Settings (setProviderStaleDate?method=viewLabRecall, POST
 *   method=saveLabRecallPrefs) ▸ delegate, message subject, "assign the tickler to the delegate",
 *   tickler priority ▸ Submit; then Schedule ▸ Inbox ▸ the lab's row ▸ Recall, which opens a message to
 *   the delegate (messenger/SendDemoMessage?…&recall) and a tickler form (tickler/ForwardDemographicTickler
 *   ?docType=HL7&docId=<lab>&…&recall) for the lab's patient.
 * Asserts:
 *   - The settings page opens empty with Submit disabled until a delegate is picked; Submit stores one
 *     `property` row for each setting; reopening shows them all.
 *   - A delegate who is not an active provider is refused and changes nothing (a crafted POST: the
 *     page lists only active providers).
 *   - Recall opens a message with the delegate ticked and a tickler form assigned to the delegate at the
 *     saved priority, carrying the lab.
 *   - Delete removes every setting and the lab no longer offers Recall.
 *   - Saving the tickler window Recall opened stores a tickler for the lab's patient, assigned to the
 *     delegate at that priority, created by the provider, linked to the lab in ticklerdocs.
 *   - Last: the recall message's Subject is the subject saved in the settings.
 * Not asserted: the text of the banner shown after Submit and Delete (it prints the message key), and the lab
 * display once the tickler is linked to it (it fails to open); both have their own pending findings.
 * Fixtures: one synthetic PATHL7 ORU^R01 (serviceName CARLOSRECALL) in hl7TextMessage/hl7TextInfo with
 * provider routing for the test provider and patient routing for the owned FAKE-PW patient; the tickler
 * saved from Recall carries the run marker. The test provider's recall settings are snapshotted and
 * cleared first, then restored in cleanup and verified. While the check runs, every lab the test provider
 * opens shows Recall, so it must not run concurrently with other checks (live wrapper: EXCLUSIVE=1).
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow } = require('./lib/workflow-session');

const TIMEOUT = 20000;
const SERVICE = 'CARLOSRECALL';
// What the settings page writes, plus the name the messenger reads the subject from.
const SETTINGS = ['lab_recall_delegate', 'labRecallMsgSubject', 'lab_recall_tickler_assignee', 'lab_recall_tickler_priority'];
const SNAPSHOT_NAMES = [...SETTINGS, 'lab_recall_msg_subject'];

function hl7(marker, accession, stamp) {
  return [
    `MSH|^~\\&|PATHL7|CW|CARLOS|TEST|${stamp}||ORU^R01|RECALL${stamp}|P|2.3|||ER|AL`,
    `PID||9999999999|9999999999||${marker}^Recall||19800101|F`,
    `ORC|RE||${accession}|||||||||99999^FAKE-ORDERING^DOC`,
    `OBR|1||${accession}|CHEM^Chemistry||${stamp}|${stamp}|||||||${stamp}||99999^FAKE-ORDERING^DOC||||||${stamp}||CHEM4|F|||99999^FAKE-ORDERING^DOC`,
    `OBX|1|NM|2345-7^Glucose Random||9.8|mmol/L|3.3-7.7|H|||F|||${stamp}`,
  ].join('\r');
}

async function workflow(s) {
  const { sql, patient, provider, marker, context, recorder } = s;
  const q = h.sqlString;
  const delegateRow = sql.rows(`SELECT provider_no, last_name, first_name FROM provider WHERE status='1' AND provider_no NOT LIKE '-%'
    AND provider_no<>${q(provider)} AND last_name LIKE 'FAKE-%' ORDER BY last_name, first_name, provider_no LIMIT 1`)[0];
  if (!delegateRow) throw new h.SkipCheck('No other active FAKE provider exists to be the recall delegate');
  const [delegate, delegateLast, delegateFirst] = delegateRow;
  const subject = `${marker} recall: please book a follow-up`;
  const ticklerMessage = `${marker} recall tickler`;

  // The test provider's recall settings: snapshot, then clear so the page opens empty.
  const settingsWhere = `provider_no=${q(provider)} AND name IN (${SNAPSHOT_NAMES.map(q).join(',')})`;
  const snapshotQuery = `SELECT id, name, COALESCE(value,''), value IS NULL FROM property WHERE ${settingsWhere} ORDER BY id`;
  const snapshot = sql.rows(snapshotQuery);
  s.cleanup(() => {
    const statements = [`DELETE FROM property WHERE ${settingsWhere}`];
    for (const [id, name, value, isNull] of snapshot) {
      h.assert(/^\d+$/.test(id) && SNAPSHOT_NAMES.includes(name), 'Invalid recall settings snapshot');
      statements.push(`INSERT INTO property(id,provider_no,name,value) VALUES (${id},${q(provider)},${q(name)},${isNull === '1' ? 'NULL' : q(value)})`);
    }
    sql.execute(`START TRANSACTION;${statements.join(';')};COMMIT`);
    h.assert(JSON.stringify(sql.rows(snapshotQuery)) === JSON.stringify(snapshot), 'The recall settings were not restored to their snapshot');
  });
  sql.execute(`DELETE FROM property WHERE ${settingsWhere}`);
  // Name → value, keys sorted in JS (SQL name order follows the collation); a name stored twice shows as "<n rows>".
  const stored = () => {
    const rows = sql.rows(`SELECT name, COALESCE(value,'') FROM property WHERE ${settingsWhere} ORDER BY id`);
    const names = [...new Set(rows.map(([name]) => name))].sort();
    return JSON.stringify(Object.fromEntries(names.map(name => {
      const values = rows.filter(([n]) => n === name).map(([, value]) => value);
      return [name, values.length === 1 ? values[0] : `<${values.length} rows>`];
    })));
  };
  const sorted = values => JSON.stringify(Object.fromEntries(Object.keys(values).sort().map(name => [name, values[name]])));

  // The tickler Recall saves: owned patient, marker message.
  const ownedTicklers = `demographic_no=${patient} AND message LIKE ${q(`${marker}%`)}`;
  s.cleanup(() => {
    const ids = sql.rows(`SELECT tickler_no FROM tickler WHERE ${ownedTicklers}`).map(row => row[0]);
    h.assert(ids.every(id => /^[1-9]\d*$/.test(id)), 'Owned tickler ID is invalid');
    if (ids.length) {
      sql.execute(`DELETE FROM ticklerdocs WHERE tickler_id IN (${ids.join(',')});
        DELETE FROM tickler_comments WHERE tickler_no IN (${ids.join(',')});
        DELETE FROM tickler_update WHERE tickler_no IN (${ids.join(',')});
        DELETE FROM tickler WHERE ${ownedTicklers} AND tickler_no IN (${ids.join(',')})`);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler WHERE ${ownedTicklers}`) === '0', 'Owned ticklers were not removed');
  });

  // The lab: one synthetic result routed to the test provider and the owned patient.
  const owned = { labNo: null };
  s.cleanup(() => {
    if (!owned.labNo) return;
    const lab = Number(owned.labNo);
    h.assert(sql.value(`SELECT COUNT(*) FROM hl7TextMessage WHERE lab_id=${lab} AND serviceName=${q(SERVICE)}`) === '1', 'Owned lab identity changed');
    sql.execute(`DELETE FROM patientLabRouting WHERE lab_no=${lab} AND lab_type='HL7'; DELETE FROM providerLabRouting WHERE lab_no=${lab} AND lab_type='HL7';
      DELETE FROM hl7TextInfo WHERE lab_no=${lab}; DELETE FROM hl7TextMessage WHERE lab_id=${lab} AND serviceName=${q(SERVICE)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM patientLabRouting WHERE lab_no=${lab} AND lab_type='HL7')
      + (SELECT COUNT(*) FROM providerLabRouting WHERE lab_no=${lab} AND lab_type='HL7')
      + (SELECT COUNT(*) FROM hl7TextInfo WHERE lab_no=${lab}) + (SELECT COUNT(*) FROM hl7TextMessage WHERE lab_id=${lab})`) === '0', 'Owned lab rows remain');
  });
  const accession = `RECALL-${marker.slice(-10)}`;
  const now = new Date();
  const stamp = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}100000`;
  const labNo = sql.value(`INSERT INTO hl7TextMessage (fileUploadCheck_id, message, type, serviceName, created)
    VALUES (0, ${q(Buffer.from(hl7(marker, accession, stamp), 'utf8').toString('base64'))}, 'PATHL7', ${q(SERVICE)}, NOW()); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(labNo), 'The fixture lab did not insert');
  owned.labNo = labNo;
  sql.execute(`INSERT INTO hl7TextInfo (lab_no, sex, health_no, result_status, final_result_count, obr_date, priority, requesting_client,
      discipline, last_name, first_name, report_status, accessionNum, filler_order_num, sending_facility)
    VALUES (${labNo}, 'F', '9999999999', 'A', 1, NOW(), 'R', 'FAKE-ORDERING, DOC', 'CHEM', ${q(marker)}, 'Recall', 'F', ${q(accession)}, ${q(accession)}, 'CW');
    INSERT INTO providerLabRouting (provider_no, lab_no, status, lab_type) VALUES (${q(provider)}, ${labNo}, 'N', 'HL7');
    INSERT INTO patientLabRouting (demographic_no, lab_no, lab_type, created) VALUES (${patient}, ${labNo}, 'HL7', NOW())`);

  const prefs = await s.popup(s.schedule, s.schedule.getByTitle(/Edit your personal setting/i).first(), 'preferences');
  async function openSettings(label) {
    const link = prefs.locator('a[href*="method=viewLabRecall"]').first();
    await revealAuditLink(prefs, link, TIMEOUT);
    const settings = await s.popup(prefs, link, label);
    await settings.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
    h.assert(h.pathOnly(settings.url()).endsWith('/setProviderStaleDate')
      && new URL(settings.url()).searchParams.get('method') === 'viewLabRecall', 'The Lab Recall Settings link did not open its own page');
    h.assert(await settings.locator('select[name="labRecallDelegate.value"]').count() === 1, 'The Lab Recall Settings page has no delegate list');
    return settings;
  }
  const form = page => ({
    delegate: page.locator('select[name="labRecallDelegate.value"]'),
    subject: page.locator('input[name="labRecallMsgSubject.value"]'),
    assignee: page.locator('input[name="labRecallTicklerAssignee.checked"]'),
    priority: page.locator('select[name="labRecallTicklerPriority.value"]'),
    submit: page.locator('input[name="btnApply"]'),
    remove: page.locator('input[name="delete"]'),
  });
  const saved = sorted({ lab_recall_delegate: delegate, labRecallMsgSubject: subject, lab_recall_tickler_assignee: 'yes', lab_recall_tickler_priority: 'High' });

  await s.step('Preferences ▸ Lab Recall Settings opens empty and Submit stores the delegate, subject, tickler assignee and priority', async () => {
    const settings = await openSettings('lab-recall-settings');
    const f = form(settings);
    h.assert(await f.delegate.inputValue() === '' && await f.subject.inputValue() === '' && !await f.assignee.isChecked()
      && await f.priority.inputValue() === '', 'Lab Recall Settings did not open empty for a provider with no settings');
    h.assert(await f.submit.isDisabled() && !await f.remove.isVisible(), 'Submit is enabled, or Delete shown, before a delegate is picked');
    await f.delegate.selectOption(delegate);
    h.assert(await f.submit.isEnabled(), 'Picking a delegate did not enable Submit');
    await f.subject.fill(subject);
    await f.assignee.check();
    await f.priority.selectOption('High');
    await ui.clickAndAwaitReload(settings, f.submit, { label: 'Lab Recall Settings Submit' });
    await settings.locator('#AlertBanner').waitFor({ state: 'visible', timeout: TIMEOUT });
    h.assert(stored() === saved, `The recall settings were stored as ${stored()}`);
    await settings.close();
  });

  await s.step('reopening Lab Recall Settings shows the saved delegate, subject, tickler assignee and priority', async () => {
    const settings = await openSettings('lab-recall-settings-reopen');
    const f = form(settings);
    h.assert(await f.delegate.inputValue() === delegate, 'The reopened page does not select the saved delegate');
    h.assert(await f.subject.inputValue() === subject, 'The reopened page does not show the saved subject');
    h.assert(await f.assignee.isChecked(), 'The reopened page does not tick "assign the tickler to the delegate"');
    h.assert(await f.priority.inputValue() === 'High', 'The reopened page does not select the saved priority');
    h.assert(await f.submit.isEnabled() && await f.remove.isVisible(), 'The reopened page does not offer Submit and Delete');
    await settings.close();
  });

  await s.step('a delegate who is not an active provider is refused and changes nothing', async () => {
    const inactive = sql.value(`SELECT provider_no FROM provider WHERE status<>'1' AND provider_no NOT LIKE '-%' ORDER BY provider_no LIMIT 1`);
    const refusedDelegate = inactive || String(Number(sql.value("SELECT COALESCE(MAX(CAST(provider_no AS UNSIGNED)),0) FROM provider WHERE provider_no REGEXP '^[0-9]+$'")) + 1);
    h.assert(/^[A-Za-z0-9]+$/.test(refusedDelegate), 'No provider number to refuse');
    const settings = await openSettings('lab-recall-settings-crafted');
    // The page lists only active providers, so this is the same form POST with the delegate swapped.
    const answer = await settings.evaluate(async ({ refused }) => {
      const params = new URLSearchParams(new FormData(document.querySelector('form[action$="/setProviderStaleDate"]')));
      params.set('labRecallDelegate.value', refused);
      const response = await fetch(document.querySelector('form[action$="/setProviderStaleDate"]').action, {
        method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: params.toString(), credentials: 'same-origin',
      });
      // The action answers a refused delegate with its own banner page, which a CSRF or session refusal would not render.
      const body = await response.text();
      return { status: response.status, hasToken: params.has('CSRF-TOKEN') && params.get('CSRF-TOKEN') !== '', banner: body.includes('id="AlertBanner"') };
    }, { refused: refusedDelegate });
    h.assert(answer.hasToken, 'The settings form carries no CSRF token, so the crafted POST proves nothing');
    h.assert(answer.status === 200 && answer.banner, `The refused delegate answered HTTP ${answer.status} without the settings page's own answer`);
    h.assert(stored() === saved, `A refused delegate changed the settings to ${stored()}`);
    await settings.close();
    // A GET carrying saveLabRecallPrefs is not probed here: method contract pending.
  });

  let inbox;
  async function openLab(label) {
    if (!inbox || inbox.isClosed()) {
      ({ page: inbox } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('a#inboxLink').first(),
        { context, recorder, label: 'lab-recall-inbox', timeout: 30000 }));
    } else {
      await inbox.reload({ waitUntil: 'domcontentloaded' });
    }
    await inbox.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
    const row = inbox.locator(`tr[data-segment-id="${labNo}"][data-lab-type="HL7"]`).first();
    await row.waitFor({ state: 'attached', timeout: 45000 });
    const lab = await s.popup(inbox, row.locator('a[onclick*="reportWindow"]').first(), label);
    await lab.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    h.assert((await lab.locator('body').innerText()).includes(accession), 'The lab display is not the owned lab');
    return lab;
  }

  let tickler;
  let recallSubject;
  await s.step('the lab\'s Recall opens a message with the delegate ticked and a tickler form for the delegate at the saved priority', async () => {
    const lab = await openLab('lab-recall-display');
    const recall = lab.locator('input[type="button"][value="Recall"]');
    h.assert(await recall.count() === 1, 'The lab display offers no Recall button with a recall delegate set');
    // handleLab asks documentManager/inboxManage whether the lab has a patient, then opens both windows.
    const pages = [];
    const collect = page => pages.push(page);
    context.on('page', collect);
    try {
      await recall.click();
      const deadline = Date.now() + 30000;
      while (pages.length < 2 && Date.now() < deadline) await lab.waitForTimeout(250);
    } finally {
      context.off('page', collect);
    }
    h.assert(pages.length === 2, `Recall opened ${pages.length} window(s), not a message and a tickler form`);
    await Promise.all(pages.map(page => page.waitForLoadState('domcontentloaded', { timeout: 30000 })));
    const byPath = pathEnd => pages.find(page => h.pathOnly(page.url()).endsWith(pathEnd));
    const message = byPath('/messenger/SendDemoMessage');
    tickler = byPath('/tickler/ForwardDemographicTickler');
    h.assert(message && tickler, `Recall opened ${pages.map(page => h.pathOnly(page.url())).join(' and ')}, not a message and a tickler form`);
    for (const [page, label] of [[message, 'the recall message'], [tickler, 'the recall tickler form']]) {
      await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
      await h.assertNotErrorPage(page, label);
    }
    const messageUrl = new URL(message.url());
    h.assert(messageUrl.searchParams.get('demographic_no') === patient && messageUrl.searchParams.has('recall'),
      'The recall message was not opened for the lab\'s patient as a recall');
    const ticked = message.locator(`input[name="provider"][type="checkbox"][value="${delegate}"]`).first();
    h.assert(await ticked.count() === 1 && await ticked.isChecked(), 'The recall message does not tick the delegate');
    h.assert((await message.locator('body').innerText()).includes(`${delegateLast}, ${delegateFirst}`), 'The recall message does not name the delegate');
    recallSubject = await message.locator('#subject').inputValue();
    await message.close();

    const ticklerUrl = new URL(tickler.url());
    h.assert(ticklerUrl.searchParams.get('docType') === 'HL7' && ticklerUrl.searchParams.get('docId') === labNo
      && ticklerUrl.searchParams.get('demographic_no') === patient && ticklerUrl.searchParams.has('recall'),
    `The recall tickler form was opened as ${ticklerUrl.search}`);
    const ticklerForm = tickler.locator('form[name="serviceform"]');
    await ticklerForm.waitFor({ state: 'visible', timeout: TIMEOUT });
    h.assert(await ticklerForm.locator('input[name="demographic_no"]').last().inputValue() === patient, 'The recall tickler form is not for the lab\'s patient');
    h.assert(await ticklerForm.locator('select[name="task_assigned_to"]').inputValue() === delegate, 'The recall tickler form is not assigned to the delegate');
    h.assert(await ticklerForm.locator('select[name="priority"]').inputValue() === 'High', 'The recall tickler form does not carry the saved priority');
    h.assert(await ticklerForm.locator('input[name="docType"]').inputValue() === 'HL7'
      && await ticklerForm.locator('input[name="docId"]').inputValue() === labNo, 'The recall tickler form does not carry the lab');
    await lab.close();
  });

  // Delete runs while the recall tickler is still unsaved: the lab is reopened here, and a lab that has a linked
  // tickler fails to open (pending finding 287, #4722), which would hide what this step asserts.
  await s.step('Delete in Lab Recall Settings removes every setting and the lab no longer offers Recall', async () => {
    const settings = await openSettings('lab-recall-settings-delete');
    const dialogs = await h.withExpectedDialogs(settings, async () => {
      await ui.clickAndAwaitReload(settings, form(settings).remove, { label: 'Lab Recall Settings Delete' });
    });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', `Delete asked ${JSON.stringify(dialogs)}, not one confirmation`);
    await settings.locator('#AlertBanner').waitFor({ state: 'visible', timeout: TIMEOUT });
    h.assert(stored() === '{}', `Delete left the settings ${stored()}`);
    await settings.close();
    const lab = await openLab('lab-recall-display-after-delete');
    h.assert(await lab.locator('input[type="button"][value="Recall"]').count() === 0, 'The lab still offers Recall after the settings were deleted');
    await lab.close();
  });

  await s.step('saving the recall tickler window stores it for the lab\'s patient, assigned to the delegate at the saved priority and linked to the lab', async () => {
    await tickler.locator('#ticklerMessage').fill(ticklerMessage);
    await tickler.locator('form[name="serviceform"] input.btn-primary[name="Button"]').first().click();
    const sentinel = await tickler.waitForFunction(() => {
      const frame = document.getElementById('ticklerSubmitFrame');
      const doc = frame && frame.contentDocument;
      if (!doc) return null;
      return ['tickler-save-ok', 'tickler-save-ok-link-failed'].find(id => doc.getElementById(id)) || null;
    }, null, { timeout: 30000 }).then(handle => handle.jsonValue());
    h.assert(sentinel === 'tickler-save-ok', `The recall tickler saved with "${sentinel}", not a clean save`);
    const rows = sql.rows(`SELECT tickler_no, task_assigned_to, creator, priority, status FROM tickler
      WHERE demographic_no=${patient} AND message=${q(ticklerMessage)}`);
    h.assert(rows.length === 1, `Expected one recall tickler, found ${rows.length}`);
    const [[ticklerNo, assignee, creator, priority, status]] = rows;
    h.assert(assignee === delegate && creator === provider, `The recall tickler was assigned to ${assignee} and created by ${creator}`);
    h.assert(priority === 'High' && status === 'A', `The recall tickler has priority ${priority} and status ${status}`);
    const link = sql.rows(`SELECT doctype, lab_type, document_no, deleted IS NULL FROM ticklerdocs WHERE tickler_id=${Number(ticklerNo)}`);
    h.assert(JSON.stringify(link) === JSON.stringify([['L', 'HL7', labNo, '1']]), `The recall tickler's ticklerdocs rows were ${JSON.stringify(link)}`);
    if (!tickler.isClosed()) await tickler.close();
  });

  await s.step('the recall message opened by Recall carried the subject saved in Lab Recall Settings', async () => {
    h.assert(recallSubject === subject, `The recall message's Subject was "${recallSubject}", not the saved "${subject}"`);
  });
}

if (require.main === module) runWorkflow('lab-recall-delegate', workflow, { openMaster: false });
module.exports = { workflow };
