#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * messenger-reply-conversation — a message answered with Reply. messenger covers composing and
 * sending, messenger-demographic-link opens the Reply and Forward drafts and clears them, and
 * messenger-reply-all-forward (#4753) is the Reply All coverage (recipients, Sent box, Forward). No
 * check sent a plain Reply, so who it reaches, whether the patient link survives it, and whose copy
 * reading marks read were never asserted.
 *
 * User path, as THROWAWAY logins (lib/throwaway-login-fixture.js) A and B; C is a third throwaway that
 * only receives:
 *   B: Schedule ▸ Msg ▸ the message from A (also sent to C) ▸ Reply (messenger/HandleMessages) ▸ type ▸
 *      Send Message (messenger/CreateMessage);
 *   A: Schedule ▸ Msg ▸ B's reply.
 *
 * What the code means to do (MsgHandleMessages2Action with MessagingManagerImpl): Reply addresses the
 * original sender only (getReplyToSender: sentbyNo at sentByLocation), so CreateMessage.jsp ticks that
 * one Local Member even when another recipient of the original is listed too; the subject gains "Re:",
 * the original is quoted with ">" lines, and the original's linked patient (msgDemoMap) rides along as
 * demographic_no, which MsgCreateMessage2Action links to the reply. Sending writes one messagelisttbl
 * row per ticked recipient (status new) and none for the sender. Reading marks the reader's own
 * delivery row read.
 *
 * Asserted: opening the original marks B's copy read and leaves C's new. B's Reply ticks exactly A,
 * although C is a listed contact and a recipient of the original, and carries Re:, the quoted original
 * and the linked patient. Sent, it is one Re: message from B, quoting the original and carrying B's new
 * line, delivered new to A alone and linked to the owned patient. A's inbox lists the reply; opening it
 * shows the quoted original and marks A's copy read, while C's copy of the original stays new.
 *
 * Fixtures: three throwaway logins, each enrolled as a local messenger contact the way Group Admin's
 * add writes it (groupMembers_tbl: group 0, facility 0, the current location); one message from A to B
 * and C, delivered by SQL the way MsgMessageData.sendMessage2 writes a local message and linked to the
 * owned FAKE- patient. Cleanup removes every message whose subject carries the run marker (with its
 * delivery and patient-link rows), the contact rows, then the throwaways.
 */
const { randomBytes } = require('node:crypto');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');
const { settle, clickAndLoad } = require('./messenger-demographic-link-playwright-checks');

const TIMEOUT = 20000;

async function workflow(s) {
  const { sql, config, recorder, marker } = s;
  const q = h.sqlString;
  const patient = Number(s.patient);
  const location = sql.value('SELECT locationId FROM oscarcommlocations WHERE current1=1 LIMIT 1');
  h.assert(/^\d+$/.test(location), 'No current messenger location is configured');

  // ---- three throwaway providers; A carries the run marker, B and C markers of their own. C only receives ----
  const people = {};
  for (const [key, ownMarker] of [['A', marker], ['B', `FAKE-PW${randomBytes(8).toString('hex')}`],
    ['C', `FAKE-PW${randomBytes(8).toString('hex')}`]]) {
    const fixture = throwawayLoginFixture({ sql, marker: ownMarker, provider: s.provider, testUser: config.testUser });
    s.cleanup(() => fixture.cleanup());
    fixture.create();
    people[key] = { fixture, no: fixture.providerNo, name: `Throwaway ${ownMarker}` };
  }
  const { A, B, C } = people;
  const owners = [A, B, C].map(p => q(p.no)).join(',');
  s.cleanup(() => {
    sql.execute(`DELETE FROM groupMembers_tbl WHERE provider_No IN (${owners}) AND groupID=0 AND facilityId=0`);
    h.assert(sql.value(`SELECT COUNT(*) FROM groupMembers_tbl WHERE provider_No IN (${owners})`) === '0',
      'The throwaways\' messenger contact rows were not removed');
  });
  sql.execute(`INSERT INTO groupMembers_tbl (groupID,provider_No,facilityId,clinicLocationNo) VALUES
    ${[A, B, C].map(p => `(0,${q(p.no)},0,${location})`).join(',')}`);

  // ---- the original message, A to B and C, linked to the owned patient ----
  const like = q(`%${marker}%`);
  s.cleanup(() => {
    const ids = sql.rows(`SELECT messageid FROM messagetbl WHERE thesubject LIKE ${like}`).map(([id]) => Number(id));
    if (ids.length) {
      const list = ids.join(',');
      sql.execute(`DELETE FROM msgDemoMap WHERE messageID IN (${list});
        DELETE FROM messagelisttbl WHERE message IN (${list});
        DELETE FROM messagetbl WHERE messageid IN (${list}) AND thesubject LIKE ${like}`);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM messagetbl WHERE thesubject LIKE ${like}`) === '0', 'Owned messages were not removed');
  });
  const subject = `${marker} conversation`;
  const body = `${marker} original question`;
  const original = sql.value(`INSERT INTO messagetbl (thedate,theime,themessage,thesubject,sentby,sentto,sentbyNo,sentByLocation,type)
    VALUES (CURDATE(),CURTIME(),${q(body)},${q(subject)},${q(A.name)},${q(`${B.name}, ${C.name}.`)},${q(A.no)},${location},2);
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(original), 'The original message was not created');
  sql.execute(`INSERT INTO messagelisttbl (message,provider_no,status,remoteLocation,destinationFacilityId,sourceFacilityId) VALUES
    (${original},${q(B.no)},'new',${location},0,0), (${original},${q(C.no)},'new',${location},0,0);
    INSERT INTO msgDemoMap (messageID,demographic_no) VALUES (${original},${patient})`);

  /** Delivery rows of one message as [provider_no, status], by provider number. */
  const deliveries = id => sql.rows(`SELECT provider_no,status FROM messagelisttbl WHERE message=${Number(id)} ORDER BY provider_no`);
  const expectDeliveries = (id, expected, message) => {
    const want = expected.map(([p, status]) => [p.no, status]).sort((x, y) => x[0].localeCompare(y[0]));
    h.assert(JSON.stringify(deliveries(id)) === JSON.stringify(want), `${message}: ${JSON.stringify(deliveries(id))}`);
  };
  const linkedPatients = id => sql.rows(`SELECT demographic_no FROM msgDemoMap WHERE messageID=${Number(id)}`).map(([d]) => d);

  // ---- one browser context per provider ----
  async function signIn(person, label) {
    const context = await h.newContext(s.context.browser(), config);
    context.setDefaultTimeout(TIMEOUT);
    context.on('page', page => h.wireStrictPage(page, label, recorder));
    const schedule = await h.login(context, { ...config, testUser: person.fixture.username }, recorder, { label });
    const { page: inbox } = await ui.clickOpensPopupOrNavigates(schedule, schedule.locator('a:has(#oscar_new_msg)').first(),
      { context, recorder, label: `${label}-inbox`, timeout: TIMEOUT });
    await settle(inbox);
    h.assert(/\/messenger\/DisplayMessages/.test(inbox.url()), `The Msg link did not open ${label}'s messenger inbox`);
    return inbox;
  }
  async function openMessage(inbox, id) {
    await clickAndLoad(inbox, inbox.locator(`a[href*="/messenger/ViewMessage?messageID=${id}&"]`).first(), '/messenger/ViewMessage');
    await inbox.locator('#msgSubject').waitFor();
  }
  /** The compose form's ticked recipients, as provider numbers (the value is providerNo-facility-location). */
  const ticked = page => page.locator('input[name="provider"]:checked').evaluateAll(boxes => boxes.map(box => box.value.split('-')[0]).sort());
  async function draft(page) {
    await page.locator('#subject').waitFor();
    return {
      subject: await page.locator('#subject').inputValue(),
      demographic: await page.locator('input[name="demographic_no"]').inputValue(),
      text: await page.locator('textarea[name="message"]').inputValue(),
      recipients: await ticked(page),
    };
  }
  /** Type a line above the quoted original and send; returns the new message's id. */
  async function sendReply(page, sender, line) {
    const editor = page.locator('.toastui-editor-ww-container .ProseMirror').first();
    await editor.waitFor({ state: 'visible', timeout: TIMEOUT });
    await editor.click();
    await page.keyboard.press('Control+Home');
    await page.keyboard.type(line);
    await page.keyboard.press('Enter');
    const before = sql.value(`SELECT COALESCE(MAX(messageid),0) FROM messagetbl`);
    const [response] = await Promise.all([
      page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/messenger/CreateMessage'), { timeout: TIMEOUT }),
      page.locator('button[type="submit"]', { hasText: /Send Message/i }).click(),
    ]);
    h.assert(response.status() < 400, `Sending the reply answered HTTP ${response.status()}`);
    await settle(page);
    const text = await page.locator('body').innerText();
    h.assert(!/No valid recipients|Failed to send message/i.test(text), `The send page reported an error: ${text.slice(0, 200)}`);
    const rows = sql.rows(`SELECT messageid,thesubject,themessage,sentbyNo FROM messagetbl WHERE messageid>${before}
      AND thesubject LIKE ${like} ORDER BY messageid`);
    h.assert(rows.length === 1, `Sending wrote ${rows.length} messages, not one`);
    const [id, storedSubject, storedText, sentBy] = rows[0];
    h.assert(storedSubject === `Re:${subject}` && sentBy === sender.no, 'The reply was not stored as Re:<subject> from the replier');
    // The editor posts markdown, which escapes some punctuation: compare without backslashes.
    const plain = storedText.replace(/\\/g, '');
    h.assert(plain.includes(line) && plain.includes(body), 'The reply does not hold the typed line and the quoted original');
    return id;
  }

  // ---- B: Reply ----
  const inboxB = await signIn(B, 'provider-b');
  let replyB;
  await s.step('B\'s Reply ticks only the sender and carries Re:, the quoted original and the linked patient', async () => {
    await openMessage(inboxB, original);
    expectDeliveries(original, [[B, 'read'], [C, 'new']], 'Opening the message did not mark only B\'s copy read');
    await clickAndLoad(inboxB, inboxB.locator('button[name="reply"]'), '/messenger/HandleMessages');
    const d = await draft(inboxB);
    h.assert(JSON.stringify(d.recipients) === JSON.stringify([A.no]), `Reply ticked ${JSON.stringify(d.recipients)}, not only the sender`);
    h.assert(d.subject === `Re:${subject}`, 'Reply did not prefix the subject with Re:');
    h.assert(d.demographic === String(patient), 'Reply did not carry the linked patient');
    h.assert(d.text.includes(`>${body}`) && d.text.includes(`${A.name} wrote:`), 'Reply did not quote the original under its sender');
  });

  await s.step('B sends the reply: one Re: message, delivered new to the sender alone and linked to the patient', async () => {
    replyB = await sendReply(inboxB, B, `${marker} reply from B`);
    expectDeliveries(replyB, [[A, 'new']], 'The reply was not delivered to the sender alone');
    h.assert(JSON.stringify(linkedPatients(replyB)) === JSON.stringify([String(patient)]), 'The reply is not linked to the original\'s patient');
  });

  // ---- A reads the reply ----
  await s.step('A\'s inbox lists the reply; opening it shows the quoted original and marks only A\'s copy read', async () => {
    const inboxA = await signIn(A, 'provider-a');
    await inboxA.locator(`input[name="messageNo"][value="${replyB}"]`).waitFor({ state: 'attached', timeout: TIMEOUT });
    await openMessage(inboxA, replyB);
    h.assert((await inboxA.locator('#msgSubject').innerText()).includes(`Re:${subject}`), 'B\'s reply opened under another subject');
    h.assert((await inboxA.locator('body').innerText()).includes(body), 'B\'s reply does not show the quoted original');
    expectDeliveries(replyB, [[A, 'read']], 'Opening the reply did not mark A\'s copy read');
    expectDeliveries(original, [[B, 'read'], [C, 'new']], 'Reading and replying changed C\'s copy of the original');
  });
}

if (require.main === module) runWorkflow('messenger-reply-conversation', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
