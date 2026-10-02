#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Messenger <-> patient linking, from the message and from the chart.
 * User path: Schedule ▸ Msg ▸ open a patient-linked message ▸ Reply / Forward
 * (messenger/HandleMessages) ▸ Clear (messenger/ClearMessage); E-Chart ▸ Messenger
 * (messenger/DisplayDemographicMessages) ▸ Unlink Messages; message ▸ Delete
 * (HandleMessages) ▸ Archived ▸ Unarchive (messenger/ReDisplayMessages); message ▸
 * Search Patient (demographic/DemographicLinkMsg popup) ▸ pick ▸ Link to Patient.
 * Asserts a reply and a forward carry the linked patient, Clear drops the draft and
 * writes nothing, Unlink deletes the msgDemoMap row, delete/unarchive move only the
 * selected messagelisttbl row, and Search Patient + Link writes the msgDemoMap row.
 * Fixtures: an owned FAKE- patient (runWorkflow) and two FAKE-PW messages delivered
 * to the test provider by SQL, one pre-linked to the patient, one an unlinked
 * control; cleanup deletes only those message ids and their list/map rows.
 * Not covered (no UI entry): messenger/ImportDemographic (stub of a removed
 * feature) and messenger/ViewMessageByPosition (only classic casemgmt navigation).
 * Implements coverage plan §3.4 messenger-demographic-link.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const TIMEOUT = 20000;

async function settle(page) {
  await page.waitForLoadState('domcontentloaded', { timeout: TIMEOUT });
  await page.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
  await h.assertNotErrorPage(page, 'messenger page');
}

// A click that submits a form or follows a link in the same window.
async function clickAndLoad(page, locator, pathname) {
  await Promise.all([
    page.waitForResponse(r => r.request().isNavigationRequest() && r.frame() === page.mainFrame()
      && new URL(r.url()).pathname.endsWith(pathname), { timeout: TIMEOUT }),
    locator.click(),
  ]);
  await settle(page);
}

async function workflow(s) {
  const provider = h.sqlString(s.provider);
  const patient = Number(s.patient);
  const subject = `${s.marker} linked`;
  const controlSubject = `${s.marker} control`;
  const body = `${s.marker} original line`;
  const ids = [];
  s.cleanup(() => {
    for (const [id] of s.sql.rows(`SELECT messageid FROM messagetbl WHERE thesubject LIKE ${h.sqlString(`%${s.marker}%`)}`)) {
      if (!ids.includes(id)) ids.push(id);
    }
    if (!ids.length) return;
    const list = ids.map(Number).join(',');
    s.sql.execute(`DELETE FROM msgDemoMap WHERE messageID IN (${list});
      DELETE FROM messagelisttbl WHERE message IN (${list});
      DELETE FROM messagetbl WHERE messageid IN (${list}) AND thesubject LIKE ${h.sqlString(`%${s.marker}%`)}`);
    h.assert(s.sql.value(`SELECT (SELECT COUNT(*) FROM messagetbl WHERE messageid IN (${list}))
      + (SELECT COUNT(*) FROM messagelisttbl WHERE message IN (${list}))
      + (SELECT COUNT(*) FROM msgDemoMap WHERE messageID IN (${list}))`) === '0', 'Owned message fixtures were not removed');
  });
  const location = s.sql.value('SELECT locationId FROM oscarcommlocations WHERE current1=1 LIMIT 1');
  h.assert(/^\d+$/.test(location), 'No current messenger location is configured');
  // Delivered the way MsgMessageData.sendMessage2 writes a local message.
  const seed = text => {
    const id = s.sql.value(`INSERT INTO messagetbl(thedate,theime,themessage,thesubject,sentby,sentto,sentbyNo,sentByLocation,type)
      VALUES(CURDATE(),CURTIME(),${h.sqlString(body)},${h.sqlString(text)},'FAKE-PW sender','FAKE-PW recipient',${provider},${location},0);
      SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), 'Message fixture was not created');
    ids.push(id);
    s.sql.execute(`INSERT INTO messagelisttbl(message,provider_no,status,remoteLocation,destinationFacilityId,sourceFacilityId)
      VALUES(${id},${provider},'new',${location},0,0)`);
    return id;
  };
  const messageId = seed(subject);
  const controlId = seed(controlSubject);
  s.sql.execute(`INSERT INTO msgDemoMap(messageID,demographic_no) VALUES(${messageId},${patient})`);
  const status = id => s.sql.value(`SELECT status FROM messagelisttbl WHERE message=${Number(id)} AND provider_no=${provider}`);
  const mapRows = id => s.sql.value(`SELECT COUNT(*) FROM msgDemoMap WHERE messageID=${Number(id)}`);
  const ownedMessages = () => s.sql.value(`SELECT COUNT(*) FROM messagetbl WHERE thesubject LIKE ${h.sqlString(`%${s.marker}%`)}`);

  let inbox;
  const openMessage = async id => {
    await clickAndLoad(inbox, inbox.locator(`a[href*="/messenger/ViewMessage?messageID=${id}&"]`).first(), '/messenger/ViewMessage');
    await inbox.locator('#msgSubject').waitFor();
  };
  // The Inbox button on a message/draft page, or the inbox tab on a box page.
  const backToInbox = () => clickAndLoad(inbox, inbox.locator('a.btn[href*="/messenger/DisplayMessages"]:visible,'
    + ' a.nav-link[href*="/messenger/DisplayMessages"]:not([href*="boxType"]):not([href*="orderby"]):visible').first(), '/messenger/DisplayMessages');
  const draft = async () => ({
    subject: await inbox.locator('#subject').inputValue(),
    demographic: await inbox.locator('input[name="demographic_no"]').inputValue(),
    selected: await inbox.locator('input[name="selectedDemo"]').inputValue(),
    text: await inbox.locator('textarea[name="message"]').inputValue(),
  });

  await s.step('the schedule Msg link opens the inbox listing both owned messages', async () => {
    ({ page: inbox } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('a:has(#oscar_new_msg)').first(),
      { context: s.context, recorder: s.recorder, label: 'messenger-inbox', timeout: TIMEOUT }));
    await settle(inbox);
    h.assert(/\/messenger\/DisplayMessages/.test(inbox.url()), 'The Msg link did not open the messenger inbox');
    await inbox.locator(`input[name="messageNo"][value="${messageId}"]`).waitFor();
    await inbox.locator(`input[name="messageNo"][value="${controlId}"]`).waitFor();
  });

  await s.step('Reply opens an unsent Re: draft carrying the linked patient and the quoted text', async () => {
    await openMessage(messageId);
    await inbox.locator(`input[title="${patient}"]`).waitFor();
    const before = ownedMessages();
    await clickAndLoad(inbox, inbox.locator('button[name="reply"]'), '/messenger/HandleMessages');
    await inbox.locator('#subject').waitFor();
    const reply = await draft();
    h.assert(reply.subject === `Re:${subject}`, 'Reply did not prefix the subject with Re:');
    h.assert(reply.demographic === String(patient), 'Reply did not carry the linked patient');
    h.assert(reply.selected !== 'none' && reply.selected !== '', 'Reply did not show the linked patient name');
    h.assert(reply.text.includes('wrote:') && reply.text.includes(body), 'Reply did not quote the original message');
    h.assert(ownedMessages() === before && status(messageId) === 'read', 'Opening a reply draft wrote a message or changed the delivery row');
  });

  await s.step('Clear empties the draft and its patient without writing anything', async () => {
    const before = ownedMessages();
    await clickAndLoad(inbox, inbox.locator('a[href*="/messenger/ClearMessage"]').first(), '/messenger/ClearMessage');
    await inbox.locator('#subject').waitFor();
    const cleared = await draft();
    h.assert(cleared.subject === '' && cleared.demographic === '' && cleared.selected === 'none',
      'Clear kept the reply subject or its patient');
    h.assert(!cleared.text.includes(body), 'Clear kept the quoted reply text');
    h.assert(ownedMessages() === before && mapRows(messageId) === '1', 'Clear wrote or unlinked a message');
  });

  await s.step('Forward opens a Fwd: draft that keeps the linked patient', async () => {
    await backToInbox();
    await openMessage(messageId);
    await clickAndLoad(inbox, inbox.locator('button[name="forward"]'), '/messenger/HandleMessages');
    await inbox.locator('#subject').waitFor();
    const forward = await draft();
    h.assert(forward.subject === `Fwd:${subject}`, 'Forward did not prefix the subject with Fwd:');
    h.assert(forward.demographic === String(patient), 'Forward did not carry the linked patient');
    h.assert(forward.text.includes(body), 'Forward did not include the original message');
  });

  await s.step('the chart Messenger list shows only the linked message and Unlink deletes its map row', async () => {
    const chart = await s.chart();
    const list = await s.popup(chart,
      chart.locator('#leftNavBar a,#rightNavBar a').filter({ hasText: /^\s*Messenger\s*$/ }).first(), 'patient-messages');
    await settle(list);
    h.assert(new URL(list.url()).pathname.endsWith('/messenger/DisplayDemographicMessages'), 'Chart Messenger did not open the patient message list');
    const box = list.locator(`input[name="messageNo"][value="${messageId}"]`);
    await box.waitFor();
    h.assert(await list.locator(`input[name="messageNo"][value="${controlId}"]`).count() === 0, 'The patient list showed an unlinked message');
    await box.check();
    await clickAndLoad(list, list.locator('input[value="Unlink Messages"]'), '/messenger/DisplayDemographicMessages');
    await expectValue(s.sql, `SELECT COUNT(*) FROM msgDemoMap WHERE messageID=${Number(messageId)}`, '0', 'Unlink did not delete the msgDemoMap row');
    h.assert(await list.locator(`input[name="messageNo"][value="${messageId}"]`).count() === 0, 'The unlinked message is still listed for the patient');
    h.assert(status(messageId) === 'read' && status(controlId) === 'new' && ownedMessages() === '2', 'Unlinking changed a message or delivery row');
    await list.close();
  });

  await s.step('Delete from the message view archives only that delivery row', async () => {
    await backToInbox();
    await openMessage(messageId);
    await clickAndLoad(inbox, inbox.locator('button[name="delete"]'), '/messenger/HandleMessages');
    await expectValue(s.sql, `SELECT status FROM messagelisttbl WHERE message=${Number(messageId)} AND provider_no=${provider}`, 'del',
      'Delete did not archive the delivery row');
    h.assert(status(controlId) === 'new', 'Delete changed the control message');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM messagetbl WHERE messageid=${Number(messageId)}`) === '1', 'Delete removed the message content row');
  });

  await s.step('the archived box lists the deleted message and Unarchive restores it as read', async () => {
    await clickAndLoad(inbox, inbox.locator('a[href*="/messenger/DisplayMessages?boxType=2"]').first(), '/messenger/DisplayMessages');
    const box = inbox.locator(`input[name="messageNo"][value="${messageId}"]`);
    await box.waitFor();
    h.assert(await inbox.locator(`input[name="messageNo"][value="${controlId}"]`).count() === 0, 'The archived box listed the control message');
    await box.check();
    await clickAndLoad(inbox, inbox.locator('button[name="btnUnarchive"]').first(), '/messenger/ReDisplayMessages');
    await expectValue(s.sql, `SELECT status FROM messagelisttbl WHERE message=${Number(messageId)} AND provider_no=${provider}`, 'read',
      'Unarchive did not restore the delivery row');
    h.assert(status(controlId) === 'new', 'Unarchive changed the control message');
  });

  // Kept last: in this build the Search Patient popup closes itself on load and the
  // Link to Patient form posts without a CSRF token (see report). The step asserts
  // the correct behaviour, so it fails until both are fixed.
  await s.step('Search Patient (DemographicLinkMsg) picks the patient and Link to Patient writes the map row', async () => {
    await backToInbox();
    await openMessage(controlId);
    await inbox.locator('#keyword').fill(s.marker);
    const [search] = await Promise.all([
      s.context.waitForEvent('page', { timeout: TIMEOUT }),
      inbox.locator('input[name="searchDemo"]').click(),
    ]);
    const closed = search.waitForEvent('close', { timeout: TIMEOUT }).then(() => true, () => false);
    const pick = search.locator(`#patientResults a[onclick*="selectForMessenger"][onclick*="'${patient}'"]`);
    const shown = await pick.waitFor({ timeout: TIMEOUT }).then(() => true, () => false);
    h.assert(shown, search.isClosed()
      ? 'The Search Patient popup (demographic/DemographicLinkMsg) closed itself before listing any patient'
      : 'The Search Patient popup did not list the owned patient');
    h.assert(await search.locator('#patientResults a[onclick*="selectForMessenger"]').count() === 1,
      'The messenger patient search did not narrow to the one owned patient');
    await pick.click();
    h.assert(await closed, 'Picking a patient did not close the search popup');
    h.assert(await inbox.locator('input[name="demographic_no"]').inputValue() === String(patient),
      'Picking the patient did not fill the message link form');
    h.assert(mapRows(controlId) === '0', 'Picking a patient linked the message before Link was clicked');
    await clickAndLoad(inbox, inbox.locator('input[name="linkDemo"]'), '/messenger/ViewMessage');
    await expectValue(s.sql, `SELECT COUNT(*) FROM msgDemoMap WHERE messageID=${Number(controlId)} AND demographic_no=${patient}`, '1',
      'Link to Patient did not write the msgDemoMap row');
    await inbox.locator(`input[title="${patient}"]`).waitFor();
  });
}

if (require.main === module) runWorkflow('messenger-demographic-link', workflow, { openPatient: true });
module.exports = { workflow };
