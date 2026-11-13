#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Messenger Reply ▸ "Send and Archive": the reply is sent and the message replied to moves to the replying provider's
 * archive. The other messenger checks press Send Message (messenger-reply-all-forward, messenger, double-submit-messenger)
 * and archive from the inbox's own Archive button (messenger-inbox-actions); nothing pressed Send and Archive.
 *
 * WHY THIS CHECK EXISTS. Send and Archive does two things from one click on the compose page (CreateMessage.jsp
 * sendAndArchive): it submits the reply once with archiveAfterSend=1 and the server-supplied original message ID, and
 * the server archives that message only after the reply is sent (MsgCreateMessage2Action, #6042). The page used to
 * archive it first with a background POST to messenger/DisplayMessages, so a refused reply left the message archived
 * with no reply, and a clinician who closed the window found it in the archive unanswered.
 *
 * User path: Schedule ▸ Msg ▸ a message from another provider ▸ Reply (messenger/HandleMessages) ▸ Send and Archive.
 * Asserted:
 *   1. Reply opens the compose page with the sender ticked, the subject prefixed "Re:" and a Send and Archive button.
 *      A controlled disabled-recipient form fault then sends a real native Send POST without recipients. The server's
 *      error redisplay must retain the original message ID, raw subject/body and compose key, with no stored reply or
 *      source-row change. The sender is selected again on that redisplay before the following Send and Archive.
 *   2. A refused Send and Archive (the same controlled form fault) posts the reply once with archiveAfterSend=1 and the
 *      original ID, makes no background archive POST, and leaves the message replied to in the inbox: no reply stored and
 *      no delivery row changed.
 *   3. Send and Archive posts the reply once with archiveAfterSend=1 and the original ID, and no background archive POST.
 *      One reply is stored from the test login and delivered to the sender only.
 *   4. The message replied to is archived for the test login only: its messagelisttbl row is "del", the other
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

// A textarea exposes LF in the DOM; native form submission encodes CRLF, which
// the server also stores. Compare the text without treating that encoding as loss.
function messageText(value) {
  return value?.replace(/\r\n/g, '\n');
}

function assertReplyPost(posted, { messageId, subject, message }) {
  h.assert(posted.getAll('provider').length === 0, 'The disabled recipient was included in the refusal POST');
  h.assert(posted.get('archiveMessageId') === messageId, 'The refusal POST lost the original reply carrier');
  h.assert(posted.get('subject') === subject, 'The refusal POST changed the typed subject');
  h.assert(messageText(posted.get('message')) === messageText(message), 'The refusal POST changed the typed message');
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
  const otherRow = () => JSON.stringify(sql.rows(`SELECT * FROM messagelisttbl WHERE message=${messageId}
    AND provider_no=${h.sqlString(OTHER)}`));
  const sourceState = () => JSON.stringify({
    message: sql.rows(`SELECT * FROM messagetbl WHERE messageid=${messageId}`),
    recipients: sql.rows(`SELECT * FROM messagelisttbl WHERE message=${messageId} ORDER BY id`),
    patients: sql.rows(`SELECT * FROM msgDemoMap WHERE messageID=${messageId} ORDER BY id`),
  });

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
    h.assert(await inbox.locator('#sendArchive').getAttribute('data-message-id') === messageId,
      'The compose page lost the original message ID');
    h.assert(await inbox.locator('input[name="archiveMessageId"]').inputValue() === messageId,
      'The reply form does not carry its original message through a failed send');
    // Exercise the packaged privacy policy even on a development server.
    await inbox.evaluate(() => Object.defineProperty(document, 'referrer', { configurable: true, value: '' }));
  });

  let retryBody;
  let recipient;
  await s.step('an actual Send with a disabled recipient reaches server refusal and retains the reply for Send and Archive', async () => {
    const attachmentKey = await inbox.locator('input[name="attachmentKey"]').inputValue();
    const rawSubject = `  Re:${subject}  `;
    await inbox.locator('#subject').fill(rawSubject);
    retryBody = await inbox.evaluate(value => {
      editor.setMarkdown(value);
      writeToMessage();
      return document.forms[0].message.value;
    }, `${marker} retry H&P + café 中文\n${lines.join('\n')}`);
    const selected = inbox.locator('input[name="provider"]:checked');
    const selectedValues = await selected.evaluateAll(boxes => boxes.map(box => box.value));
    h.assert(selectedValues.length > 0 && selectedValues.every(value => value === selectedValues[0]),
      'The reply does not select only its original sender, including any group copies');
    recipient = selectedValues[0];
    // Controlled form fault: validation sees a checked box, while the browser omits
    // its disabled value from the real POST. No shared contact or HTTP response is changed.
    await selected.evaluateAll(boxes => boxes.forEach(box => { box.disabled = true; }));
    const before = sourceState();
    const beforeMessages = JSON.stringify(owned());
    const [refused] = await Promise.all([
      inbox.waitForResponse(r => r.request().method() === 'POST'
        && r.request().isNavigationRequest() && new URL(r.url()).pathname.endsWith('/messenger/CreateMessage'), { timeout: TIMEOUT }),
      inbox.locator('button[type="submit"]').click(),
    ]);
    const posted = new URLSearchParams(refused.request().postData() || '');
    assertReplyPost(posted, { messageId, subject: rawSubject, message: retryBody });
    h.assert(refused.status() === 200 && Object.hasOwn(refused.headers(), h.APPLICATION_HEADER),
      'The no-recipient send did not return the real application compose page');
    await settle(inbox);
    h.assert((await inbox.locator('body').innerText()).includes('No valid recipients selected'),
      'The application did not display its controlled recipient refusal');
    h.assert(sourceState() === before && JSON.stringify(owned()) === beforeMessages,
      'The refused ordinary Send stored a message or changed the original message/delivery rows');
    h.assert(await inbox.locator('#subject').inputValue() === rawSubject
      && await inbox.locator('textarea[name="message"]').inputValue() === retryBody
      && await inbox.locator('input[name="attachmentKey"]').inputValue() === attachmentKey,
    'Redisplay lost the typed subject/body or this compose window attachment key');
    h.assert(await inbox.locator('input[name="archiveMessageId"]').inputValue() === messageId
      && await inbox.locator('#sendArchive').getAttribute('data-message-id') === messageId,
    'The refused send lost the original message for the following Send and Archive');
    await inbox.locator(`input[name="provider"][value="${recipient}"]`).first().check();
  });

  // Background archive POSTs (the old flow) seen while a Send and Archive is pressed: there must be none.
  async function sendAndArchive() {
    const archivePosts = [];
    const watch = request => {
      if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/messenger/DisplayMessages')) archivePosts.push(request);
    };
    inbox.on('request', watch);
    try {
      const [reply] = await Promise.all([
        inbox.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/messenger/CreateMessage'),
          { timeout: 30000 }),
        inbox.locator('#sendArchive').click(),
      ]);
      await settle(inbox);
      const posted = new URLSearchParams(reply.request().postData() || '');
      h.assert(posted.get('archiveAfterSend') === '1' && posted.get('archiveMessageId') === messageId,
        `Send and Archive posted archiveAfterSend=${posted.get('archiveAfterSend')} archiveMessageId=${posted.get('archiveMessageId')}, `
        + `not 1 and the message replied to (${messageId})`);
      h.assert(archivePosts.length === 0, `Send and Archive still made ${archivePosts.length} background archive POST(s) before the reply`);
      return reply;
    } finally {
      inbox.off('request', watch);
    }
  }

  await s.step('a refused Send and Archive leaves the message replied to in the inbox', async () => {
    // The same controlled fault: validation sees the checked sender, the browser omits its disabled value.
    const selected = inbox.locator('input[name="provider"]:checked');
    await selected.evaluateAll(boxes => boxes.forEach(box => { box.disabled = true; }));
    const before = sourceState();
    const beforeMessages = JSON.stringify(owned());
    const refused = await sendAndArchive();
    h.assert(refused.status() === 200 && Object.hasOwn(refused.headers(), h.APPLICATION_HEADER),
      'The refused Send and Archive did not return the real application compose page');
    h.assert((await inbox.locator('body').innerText()).includes('No valid recipients selected'),
      'The application did not display its controlled recipient refusal');
    h.assert(statusOf(self) !== 'del', 'A refused Send and Archive archived the message replied to');
    h.assert(sourceState() === before && JSON.stringify(owned()) === beforeMessages,
      'The refused Send and Archive stored a reply or changed the original message/delivery rows');
    h.assert(await inbox.locator('input[name="archiveMessageId"]').inputValue() === messageId,
      'The refused Send and Archive lost the original message for the next try');
    await inbox.locator(`input[name="provider"][value="${recipient}"]`).first().check();
  });

  let reply;
  await s.step('Send and Archive sends one reply to the sender, then archives exactly the message replied to', async () => {
    const otherBefore = otherRow();
    reply = await sendAndArchive();
    h.assert(reply.status() < 400, `The reply answered HTTP ${reply.status()}`);
    const replies = sql.rows(`SELECT messageid, sentbyNo FROM messagetbl WHERE thesubject=${h.sqlString(`Re:${subject}`)}`);
    h.assert(replies.length === 1 && replies[0][1] === self, `Send and Archive stored ${replies.length} replies, not one from ${self}`);
    h.assert(messageText(sql.value(`SELECT themessage FROM messagetbl WHERE messageid=${replies[0][0]}`)) === messageText(retryBody),
      'The retried reply did not store the exact recovered message text');
    const delivered = sql.rows(`SELECT provider_no FROM messagelisttbl WHERE message=${replies[0][0]} ORDER BY provider_no`).map(row => row[0]);
    h.assert(JSON.stringify(delivered) === JSON.stringify([SENDER]), `The reply was delivered to ${JSON.stringify(delivered)}, not to ${SENDER}`);
    h.assert(!(await inbox.locator('[data-carlos-archive-after-send-failed]').count()),
      'The sent page says the message replied to could not be archived');
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
module.exports = { workflow, assertReplyPost };
