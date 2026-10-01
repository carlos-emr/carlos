#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Messenger <-> patient linking, from the message and from the chart.
 * User path: Schedule ▸ Msg ▸ open a received message ▸ "Search Patient"
 * (demographic/DemographicLinkMsg popup ▸ result row) ▸ "Link to Patient";
 * Reply / Forward / Clear (messenger/HandleMessages, messenger/ClearMessage);
 * E-Chart ▸ Messenger (messenger/DisplayDemographicMessages) ▸ Unlink Messages;
 * message ▸ Delete (HandleMessages) ▸ Archived box ▸ Unarchive (ReDisplayMessages).
 * Asserts the msgDemoMap row the link writes and the unlink removes, that a reply
 * and a forward carry the linked patient, that Clear drops the draft and writes
 * nothing, and that delete/unarchive move only the selected messagelisttbl row.
 * Fixtures: an owned FAKE- patient (runWorkflow) and two FAKE-PW messages
 * delivered to the test provider by SQL (one is an untouched control); cleanup
 * deletes only those message ids and their list/map rows, and asserts them gone.
 * Not covered (no UI entry): messenger/ImportDemographic (stub, removed feature)
 * and messenger/ViewMessageByPosition (only the classic casemgmt navigation.jsp).
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
  const subject = `${s.marker} link target`;
  const controlSubject = `${s.marker} control`;
  const body = `${s.marker} original line`;
  const ids = [];
  s.cleanup(() => {
    for (const [id] of s.sql.rows(`SELECT messageid FROM messagetbl WHERE thesubject LIKE ${h.sqlString(`${s.marker}%`)}`)) {
      if (!ids.includes(id)) ids.push(id);
    }
    if (!ids.length) return;
    const list = ids.map(Number).join(',');
    s.sql.execute(`DELETE FROM msgDemoMap WHERE messageID IN (${list});
      DELETE FROM messagelisttbl WHERE message IN (${list});
      DELETE FROM messagetbl WHERE messageid IN (${list}) AND thesubject LIKE ${h.sqlString(`${s.marker}%`)}`);
    h.assert(s.sql.value(`SELECT (SELECT COUNT(*) FROM messagetbl WHERE messageid IN (${list}))
      + (SELECT COUNT(*) FROM messagelisttbl WHERE message IN (${list}))
      + (SELECT COUNT(*) FROM msgDemoMap WHERE messageID IN (${list}))`) === '0', 'Owned message fixtures were not removed');
  });
  const location = s.sql.value('SELECT locationId FROM oscarcommlocations WHERE current1=1 LIMIT 1');
  h.assert(/^\d+$/.test(location), 'No current messenger location is configured');
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
  const status = id => s.sql.value(`SELECT status FROM messagelisttbl WHERE message=${Number(id)} AND provider_no=${provider}`);
  const links = id => s.sql.value(`SELECT COUNT(*) FROM msgDemoMap WHERE messageID=${Number(id)} AND demographic_no=${Number(s.patient)}`);
  const ownedMessages = () => s.sql.value(`SELECT COUNT(*) FROM messagetbl WHERE thesubject LIKE ${h.sqlString(`%${s.marker}%`)}`);
  const patientName = s.sql.rows(`SELECT last_name, first_name FROM demographic WHERE demographic_no=${Number(s.patient)}`)[0];

  let inbox;
  const openMessage = async id => {
    await clickAndLoad(inbox, inbox.locator(`a[href*="/messenger/ViewMessage?messageID=${id}&"]`).first(), '/messenger/ViewMessage');
    await inbox.locator('#msgSubject').waitFor();
  };
  const backToInbox = async () => {
    await clickAndLoad(inbox, inbox.getByRole('link', { name: /^\s*Inbox\s*$/ }).first(), '/messenger/DisplayMessages');
  };

  await s.step('the schedule Msg link opens the inbox listing the received message', async () => {
    ({ page: inbox } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('a:has(#oscar_new_msg)').first(),
      { context: s.context, recorder: s.recorder, label: 'messenger-inbox', timeout: TIMEOUT }));
    await settle(inbox);
    h.assert(/\/messenger\/DisplayMessages/.test(inbox.url()), 'The Msg link did not open the messenger inbox');
    await inbox.locator(`input[name="messageNo"][value="${messageId}"]`).waitFor();
    await inbox.locator(`input[name="messageNo"][value="${controlId}"]`).waitFor();
  });

  await s.step('the patient typeahead picks the owned patient and Link writes one msgDemoMap row', async () => {
    await openMessage(messageId);
    // fill() raises one input event and so one lookup; per-key typing makes the
    // widget abort its own superseded XHRs, which the strict recorder reports.
    const [lookup] = await Promise.all([
      inbox.waitForResponse(r => new URL(r.url()).pathname.endsWith('/demographic/SearchDemographic'), { timeout: TIMEOUT }),
      inbox.locator('#keyword').fill(s.marker),
    ]);
    console.log('DEBUG', lookup.status(), lookup.headers()['content-type'], (await lookup.text()).slice(0, 300));
    await inbox.locator('ul.demographic-autocomplete-list li', { hasText: s.marker }).first().click();
    h.assert(await inbox.locator('input[name="demographic_no"]').inputValue() === s.patient,
      'The typeahead filled a patient other than the owned fixture');
    const selected = (await inbox.locator('input[name="selectedDemo"]').inputValue()).toLowerCase();
    h.assert(patientName.every(part => selected.includes(part.toLowerCase())), 'The selected patient name was not shown');
    h.assert(links(messageId) === '0', 'Picking a patient linked the message before Link was clicked');
    await clickAndLoad(inbox, inbox.locator('input[name="linkDemo"]'), '/messenger/ViewMessage');
    await expectValue(s.sql, `SELECT COUNT(*) FROM msgDemoMap WHERE messageID=${Number(messageId)}`, '1',
      'Link to Patient did not write exactly one msgDemoMap row');
    h.assert(links(messageId) === '1', 'Link to Patient linked the message to a different patient');
    h.assert(s.sql.value(`SELECT COUNT(*) FROM msgDemoMap WHERE messageID=${Number(controlId)}`) === '0', 'The control message was linked');
  });

  await s.step('the reopened message lists the linked patient', async () => {
    await backToInbox();
    await openMessage(messageId);
    await inbox.locator(`input[title="${s.patient}"]`).waitFor();
  });

  await s.step('Reply opens a draft carrying the linked patient and the quoted message', async () => {
    const before = ownedMessages();
    await clickAndLoad(inbox, inbox.locator('button[name="reply"]'), '/messenger/HandleMessages');
    await inbox.locator('#subject').waitFor();
    h.assert(await inbox.locator('#subject').inputValue() === `Re:${subject}`, 'Reply did not prefix the subject with Re:');
    h.assert(await inbox.locator('input[name="demographic_no"]').inputValue() === s.patient, 'Reply did not carry the linked patient');
    const quoted = await inbox.locator('textarea[name="message"]').inputValue();
    h.assert(quoted.includes('wrote:') && quoted.includes(body), 'Reply did not quote the original message');
    h.assert(ownedMessages() === before, 'Opening a reply draft wrote a message');
  });

  await s.step('Clear empties the draft and its patient without writing anything', async () => {
    const before = ownedMessages();
    await clickAndLoad(inbox, inbox.locator('a[href*="/messenger/ClearMessage"]').first(), '/messenger/ClearMessage');
    await inbox.locator('#subject').waitFor();
    h.assert(await inbox.locator('#subject').inputValue() === '', 'Clear kept the reply subject');
    h.assert(await inbox.locator('input[name="demographic_no"]').inputValue() === '', 'Clear kept the linked patient');
    h.assert(await inbox.locator('input[name="selectedDemo"]').inputValue() === 'none', 'Clear kept the selected patient name');
    h.assert(ownedMessages() === before, 'Clear wrote a message');
  });

  await s.step('Forward opens a Fwd: draft that keeps the linked patient', async () => {
    await backToInbox();
    await openMessage(messageId);
    await clickAndLoad(inbox, inbox.locator('button[name="forward"]'), '/messenger/HandleMessages');
    await inbox.locator('#subject').waitFor();
    h.assert(await inbox.locator('#subject').inputValue() === `Fwd:${subject}`, 'Forward did not prefix the subject with Fwd:');
    h.assert(await inbox.locator('input[name="demographic_no"]').inputValue() === s.patient, 'Forward did not carry the linked patient');
  });

  await s.step('the chart Messenger list shows the linked message and Unlink removes the map row', async () => {
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
    await expectValue(s.sql, `SELECT COUNT(*) FROM msgDemoMap WHERE messageID=${Number(messageId)}`, '0', 'Unlink did not remove the msgDemoMap row');
    h.assert(await list.locator(`input[name="messageNo"][value="${messageId}"]`).count() === 0, 'The unlinked message is still listed for the patient');
    h.assert(status(messageId) === 'read' && status(controlId) === 'new', 'Unlinking changed the inbox delivery rows');
    await list.close();
  });

  await s.step('Delete from the message view archives only that message', async () => {
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
    await box.check();
    await clickAndLoad(inbox, inbox.locator('button[name="btnUnarchive"]').first(), '/messenger/ReDisplayMessages');
    await expectValue(s.sql, `SELECT status FROM messagelisttbl WHERE message=${Number(messageId)} AND provider_no=${provider}`, 'read',
      'Unarchive did not restore the delivery row');
    h.assert(status(controlId) === 'new', 'Unarchive changed the control message');
  });
  // Kept last: msgSearchDemo.jsp closes itself on load (see report), so this step
  // asserts the correct behaviour and is expected to fail until that is fixed.
  await s.step('Search Patient (DemographicLinkMsg) lists the owned patient and links the control message', async () => {
    await backToInbox();
    await openMessage(controlId);
    await inbox.locator('#keyword').fill(s.marker);
    const search = await s.popup(inbox, inbox.locator('input[name="searchDemo"]'), 'messenger-patient-search');
    const pick = search.locator(`#patientResults a[onclick*="selectForMessenger"][onclick*="'${s.patient}'"]`);
    await pick.waitFor({ timeout: TIMEOUT });
    h.assert(await search.locator('#patientResults a[onclick*="selectForMessenger"]').count() === 1,
      'The messenger patient search did not narrow to the one owned patient');
    await Promise.all([search.waitForEvent('close', { timeout: TIMEOUT }), pick.click()]);
    h.assert(await inbox.locator('input[name="demographic_no"]').inputValue() === s.patient,
      'Picking the patient did not fill the message link form');
    await clickAndLoad(inbox, inbox.locator('input[name="linkDemo"]'), '/messenger/ViewMessage');
    await expectValue(s.sql, `SELECT COUNT(*) FROM msgDemoMap WHERE messageID=${Number(controlId)} AND demographic_no=${Number(s.patient)}`, '1',
      'Link to Patient after Search Patient did not write the msgDemoMap row');
  });
}

if (require.main === module) runWorkflow('messenger-demographic-link', workflow, { openPatient: true });
module.exports = { workflow };
