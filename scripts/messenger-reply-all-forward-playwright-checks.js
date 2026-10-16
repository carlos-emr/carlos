#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Messenger Reply All, the Sent box, and Forward of a message with several lines. The other messenger
 * checks press Reply and Forward on a one-line message sent to the test login alone, and read only
 * the inbox and archive boxes: nothing pressed Reply All, opened the Sent box, or forwarded more than one line.
 *
 * User path: Schedule ▸ Msg ▸ a message from another provider, also sent to a third one ▸ Reply All
 * (messenger/HandleMessages) ▸ Send Message (messenger/CreateMessage); Msg ▸ Sent
 * (messenger/DisplayMessages?boxType=1); the original message ▸ Forward.
 * Asserted: Reply All ticks the sender and the other recipient, not the replying provider, prefixes the
 * subject with "Re:" and quotes every original line; Send delivers one reply to exactly those two; the
 * Sent box lists the reply and not the message the provider received; Forward keeps every original
 * line on its own quoted line.
 * Fixtures: one message seeded by SQL the way a local send writes it (sender: messenger contact 1;
 * recipients: the test login and contact 2; subject and lines carry the run marker). SKIP when the
 * messenger contacts are not 1 and 2 plus the test login. Cleanup deletes every message whose subject
 * carries the marker, with its delivery rows, and asserts they are gone.
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
  const subject = `${marker} team`;
  const lines = [`${marker} first line`, `${marker} second line`, `${marker} third line`];
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

  let inbox;
  const openMessage = async () => {
    await clickAndLoad(inbox, inbox.locator(`a[href*="/messenger/ViewMessage?messageID=${messageId}&"]`).first(), '/messenger/ViewMessage');
    await inbox.locator('#msgSubject').waitFor();
  };
  const toBox = async boxType => clickAndLoad(inbox, inbox.locator(boxType
    ? `a[href*="/messenger/DisplayMessages?boxType=${boxType}"]:visible`
    : 'a.btn[href*="/messenger/DisplayMessages"]:visible, a.nav-link[href*="/messenger/DisplayMessages"]:not([href*="boxType"]):not([href*="orderby"]):visible').first(),
  '/messenger/DisplayMessages');
  const ticked = async () => (await inbox.locator('input[name="provider"][id^="0-"]:checked').evaluateAll(boxes => boxes.map(box => box.value)))
    .map(value => value.split('-')[0]).sort();
  // Each original line must be its own quoted line in the draft.
  const quotedLines = text => lines.filter(line => text.split('\n').some(row => row.replace(/^[>\s]+/, '').trim() === line));

  await s.step('the schedule Msg link opens the inbox listing the message', async () => {
    ({ page: inbox } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('a:has(#oscar_new_msg)').first(),
      { context: s.context, recorder: s.recorder, label: 'messenger-inbox', timeout: TIMEOUT }));
    await settle(inbox);
    await inbox.locator(`input[name="messageNo"][value="${messageId}"]`).waitFor();
  });

  let replyText;
  await s.step('Reply All ticks the sender and the other recipient, not the replying provider, and quotes every line', async () => {
    await openMessage();
    await clickAndLoad(inbox, inbox.locator('button[name="replyAll"]'), '/messenger/HandleMessages');
    await inbox.locator('#subject').waitFor();
    const recipients = await ticked();
    h.assert(JSON.stringify(recipients) === JSON.stringify([SENDER, OTHER].sort()),
      `Reply All ticked ${JSON.stringify(recipients)}, not the sender ${SENDER} and the other recipient ${OTHER}`);
    h.assert(await inbox.locator('#subject').inputValue() === `Re:${subject}`, 'Reply All did not prefix the subject with Re:');
    replyText = await inbox.locator('textarea[name="message"]').inputValue();
    h.assert(quotedLines(replyText).length === lines.length, `Reply All did not quote every original line: ${JSON.stringify(replyText)}`);
  });

  let replyId;
  await s.step('Send delivers one reply to exactly the sender and the other recipient', async () => {
    const [sent] = await Promise.all([
      inbox.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/messenger/CreateMessage'), { timeout: 30000 }),
      inbox.locator('button[type="submit"]', { hasText: /Send Message/i }).click(),
    ]);
    h.assert(sent.status() < 400, `Send answered HTTP ${sent.status()}`);
    await settle(inbox);
    const replies = sql.rows(`SELECT messageid, sentbyNo FROM messagetbl WHERE thesubject=${h.sqlString(`Re:${subject}`)}`);
    h.assert(replies.length === 1 && replies[0][1] === self, `Send stored ${replies.length} replies, not one from ${self}`);
    replyId = replies[0][0];
    const delivered = sql.rows(`SELECT provider_no FROM messagelisttbl WHERE message=${replyId} ORDER BY provider_no`).map(row => row[0]);
    h.assert(JSON.stringify(delivered) === JSON.stringify([SENDER, OTHER].sort()),
      `The reply was delivered to ${JSON.stringify(delivered)}, not to ${SENDER} and ${OTHER}`);
  });

  await s.step('the Sent box lists the reply and not the message the provider received', async () => {
    // The page after Send links back to the inbox only; the Sent tab is on the inbox.
    await toBox(0);
    await toBox(1);
    await inbox.locator(`a[href*="/messenger/ViewMessage?messageID=${replyId}&"]`).first().waitFor({ state: 'attached', timeout: TIMEOUT });
    h.assert(await inbox.locator(`a[href*="/messenger/ViewMessage?messageID=${messageId}&"]`).count() === 0,
      'The Sent box lists a message the provider received');
  });

  await s.step('Forward keeps every original line on its own quoted line', async () => {
    await toBox(0);
    await openMessage();
    await clickAndLoad(inbox, inbox.locator('button[name="forward"]'), '/messenger/HandleMessages');
    await inbox.locator('#subject').waitFor();
    h.assert(await inbox.locator('#subject').inputValue() === `Fwd:${subject}`, 'Forward did not prefix the subject with Fwd:');
    const text = await inbox.locator('textarea[name="message"]').inputValue();
    const kept = quotedLines(text);
    h.assert(kept.length === lines.length,
      `Forward kept ${kept.length} of ${lines.length} original lines on their own line: ${JSON.stringify(text.trim())}`);
  });
}

if (require.main === module) runWorkflow('messenger-reply-all-forward', workflow, { openPatient: false });
module.exports = { workflow };
