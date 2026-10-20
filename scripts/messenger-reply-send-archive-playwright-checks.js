#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Messenger Reply ▸ "Send and Archive": the reply is sent and the message replied to moves to the replying provider's
 * archive. The other messenger checks press Send Message (messenger-reply-all-forward, messenger, double-submit-messenger)
 * and archive from the inbox's own Archive button (messenger-inbox-actions); nothing pressed Send and Archive.
 *
 * WHY THIS CHECK EXISTS. Send and Archive does two things from one click on the compose page (CreateMessage.jsp
 * XMLHttpRequestSendnArch): it takes the message being answered from the page that opened the compose window
 * (document.referrer's messageID), archives it with a background POST to messenger/DisplayMessages
 * (btnDelete=archive&messageNo=<id>), and then submits the reply. If the background archive is refused the page only
 * shows a warning for a moment and sends anyway, so a clinician who asked for the message to be archived finds it still in
 * their inbox, with nothing on screen saying why.
 *
 * User path: Schedule ▸ Msg ▸ a message from another provider ▸ Reply (messenger/HandleMessages) ▸ Send and Archive.
 * Asserted:
 *   1. Reply opens the compose page with the sender ticked, the subject prefixed "Re:" and a Send and Archive button.
 *   2. Send and Archive posts the archive for exactly the message replied to (DisplayMessages, btnDelete, its messageNo)
 *      and the application accepts it, then posts the reply (CreateMessage). One reply is stored from the test login and
 *      delivered to the sender only.
 *   3. The message replied to is archived for the test login only: its messagelisttbl row is "del", the other
 *      recipient's row is unchanged, the inbox no longer lists it and the archive box (boxType=2) does.
 * Fixtures: one message seeded by SQL the way a local send writes it (sender: messenger contact 1; recipients: the test
 * login and contact 2; subject and lines carry the run marker). SKIP when the messenger contacts are not 1 and 2 plus the
 * test login. Cleanup deletes every message whose subject carries the marker, with its delivery and patient-link rows, and
 * asserts they are gone.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');

const TIMEOUT = 20000;
const SENDER = '1';
const OTHER = '2';

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
  const { sql, marker } = s;
  const self = s.provider;
  const subject = `${marker} archive me`;
  const lines = [`${marker} first line`, `${marker} second line`];
  const owned = () => sql.rows(`SELECT messageid FROM messagetbl WHERE thesubject LIKE ${h.sqlString(`%${marker}%`)}`).map(row => row[0]);
  s.cleanup(() => {
    const ids = owned();
    if (!ids.length) return;
    sql.execute(`DELETE FROM msgDemoMap WHERE messageID IN (${ids.join(',')});
      DELETE FROM messagelisttbl WHERE message IN (${ids.join(',')});
      DELETE FROM messagetbl WHERE messageid IN (${ids.join(',')}) AND thesubject LIKE ${h.sqlString(`%${marker}%`)}`);
    h.assert(owned().length === 0 && sql.value(`SELECT COUNT(*) FROM messagelisttbl WHERE message IN (${ids.join(',')})`) === '0',
      'Owned messages were not removed');
  });
  const contacts = sql.rows(`SELECT DISTINCT g.provider_No FROM groupMembers_tbl g JOIN provider p ON p.provider_no=g.provider_No
    WHERE g.facilityId=0 AND p.status='1' ORDER BY g.provider_No`).map(row => row[0]);
  for (const needed of [SENDER, OTHER, self]) {
    if (!contacts.includes(needed)) throw new h.SkipCheck(`Messenger contact ${needed} is not an active local member (contacts: ${contacts.join(', ')})`);
  }
  const location = sql.value('SELECT locationId FROM oscarcommlocations WHERE current1=1 LIMIT 1');
  h.assert(/^\d+$/.test(location), 'No current messenger location is configured');
  // Written the way MsgMessageData.sendMessage2 writes a local message to two recipients.
  const messageId = sql.value(`INSERT INTO messagetbl(thedate,theime,themessage,thesubject,sentby,sentto,sentbyNo,sentByLocation,type)
    VALUES(CURDATE(),CURTIME(),${h.sqlString(lines.join('\n'))},${h.sqlString(subject)},'FAKE-PW sender','FAKE-PW recipients',
      ${h.sqlString(SENDER)},${location},0); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(messageId), 'Message fixture was not created');
  for (const recipient of [self, OTHER]) {
    sql.execute(`INSERT INTO messagelisttbl(message,provider_no,status,remoteLocation,destinationFacilityId,sourceFacilityId)
      VALUES(${messageId},${h.sqlString(recipient)},'new',${location},0,0)`);
  }
  const statusOf = who => sql.value(`SELECT IFNULL(GROUP_CONCAT(status),'') FROM messagelisttbl WHERE message=${messageId}
    AND provider_no=${h.sqlString(who)}`);
  const otherRow = () => JSON.stringify(sql.rows(`SELECT id, status FROM messagelisttbl WHERE message=${messageId}
    AND provider_no=${h.sqlString(OTHER)}`));

  let inbox;
  const listed = () => inbox.locator(`a[href*="/messenger/ViewMessage?messageID=${messageId}&"]`);

  await s.step('Reply opens the compose page for the sender with a Send and Archive button', async () => {
    ({ page: inbox } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('a:has(#oscar_new_msg)').first(),
      { context: s.context, recorder: s.recorder, label: 'messenger-inbox', timeout: TIMEOUT }));
    await settle(inbox);
    await clickAndLoad(inbox, listed().first(), '/messenger/ViewMessage');
    await inbox.locator('#msgSubject').waitFor();
    await clickAndLoad(inbox, inbox.locator('button[name="reply"]'), '/messenger/HandleMessages');
    await inbox.locator('#subject').waitFor();
    const ticked = (await inbox.locator('input[name="provider"][id^="0-"]:checked').evaluateAll(boxes => boxes.map(box => box.value)))
      .map(value => value.split('-')[0]);
    h.assert(JSON.stringify(ticked) === JSON.stringify([SENDER]), `Reply ticked ${JSON.stringify(ticked)}, not the sender ${SENDER} alone`);
    h.assert(await inbox.locator('#subject').inputValue() === `Re:${subject}`, 'Reply did not prefix the subject with Re:');
    await inbox.locator('#sendArchive').waitFor({ state: 'visible' });
  });

  let archive;
  let reply;
  await s.step('Send and Archive archives exactly the message replied to, then sends one reply to the sender', async () => {
    const otherBefore = otherRow();
    [archive, reply] = await Promise.all([
      inbox.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/messenger/DisplayMessages'),
        { timeout: 30000 }),
      inbox.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/messenger/CreateMessage'),
        { timeout: 30000 }),
      inbox.locator('#sendArchive').click(),
    ]);
    const sent = new URLSearchParams(archive.request().postData() || '');
    h.assert(sent.get('btnDelete') === 'archive' && JSON.stringify(sent.getAll('messageNo')) === JSON.stringify([messageId]),
      `Send and Archive posted ${JSON.stringify(Object.fromEntries(sent))} to DisplayMessages, not the archive of message ${messageId}`);
    h.assert(reply.status() < 400, `The reply answered HTTP ${reply.status()}`);
    await settle(inbox);
    const replies = sql.rows(`SELECT messageid, sentbyNo FROM messagetbl WHERE thesubject=${h.sqlString(`Re:${subject}`)}`);
    h.assert(replies.length === 1 && replies[0][1] === self, `Send and Archive stored ${replies.length} replies, not one from ${self}`);
    const delivered = sql.rows(`SELECT provider_no FROM messagelisttbl WHERE message=${replies[0][0]} ORDER BY provider_no`).map(row => row[0]);
    h.assert(JSON.stringify(delivered) === JSON.stringify([SENDER]), `The reply was delivered to ${JSON.stringify(delivered)}, not to ${SENDER}`);
    h.assert(archive.status() >= 200 && archive.status() < 300,
      `The archive of the message replied to answered HTTP ${archive.status()}; the reply was sent but the message stays in the `
      + `inbox (its row for the test login is "${statusOf(self)}")`);
    h.assert(statusOf(self) === 'del', `The message replied to has status "${statusOf(self)}" for the test login, not archived ("del")`);
    h.assert(otherRow() === otherBefore, 'Send and Archive changed the other recipient\'s copy of the message');
  });

  await s.step('the inbox no longer lists the message replied to and the archive box does', async () => {
    await h.gotoApp(inbox, s.config.baseUrl, '/messenger/DisplayMessages?boxType=0');
    await settle(inbox);
    h.assert(await listed().count() === 0, 'The inbox still lists the archived message');
    await h.gotoApp(inbox, s.config.baseUrl, '/messenger/DisplayMessages?boxType=2');
    await settle(inbox);
    await listed().first().waitFor({ state: 'attached', timeout: TIMEOUT });
  });
}

if (require.main === module) runWorkflow('messenger-reply-send-archive', workflow, { openPatient: false });
module.exports = { workflow };
