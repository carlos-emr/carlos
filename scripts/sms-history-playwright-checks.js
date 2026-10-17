#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// sms-history: the patient's SMS history popup (sms/ViewSmsHistory, #3839), opened from the Master Record's patient
// navigation. A plain request lists the patient's messages, 25 to a page and newest first, and never includes their
// text. "Show message" (a POST, method=showMessage) takes a reason, opens one message's full text and writes an audit
// row (log action SmsMessageBody.readFullBody, content sms_transaction, contentId = the message). A request without a
// reason answers 400, and a message that belongs to another patient answers 404 (so ids can't be probed); neither
// opens text or writes an audit row. No check opened this page.
// User path: Schedule ▸ Search ▸ Master Record ▸ SMS History ▸ Next ▸ Show message.
//   1. the history lists the patient's messages newest first, 25 on page 1 of 2, with none of their text on the page;
//   2. Next shows the other 2, page 2 of 2;
//   3. Show message with a reason opens that message's full text and writes one audit row naming the message, the
//      patient and the reason;
//   4. the same request without a reason answers 400, opens no text and writes no audit row;
//   5. LAST: a message that belongs to another patient answers 404, opens no text and writes no audit row.
// Fixtures: the owned FAKE- patient, a second FAKE- patient (owned-patient.js), and 28 sms_transaction rows inserted
// for them (27 and 1) with marker text, in finished states (SENT, DELIVERED, FAILED), so the SMS sender never picks
// them up. Cleanup removes those rows by id and the read's audit rows (this action and content, keyed to those ids),
// asserts none remain, and removes the second patient. Nothing keyed to the test login is deleted. Needs the _sms and
// _msgSMS rights the doctor role is granted by V1.0.67 and V1.0.71.
const crypto = require('node:crypto');
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { createOwnedPatient, newOwnedMarker, removeOwnedPatient } = require('./lib/owned-patient');

const q = h.sqlString;
const PAGE_SIZE = 25;
const MESSAGES = 27;
const AUDIT_ACTION = 'SmsMessageBody.readFullBody';
const AUDIT_CONTENT = 'sms_transaction';
const STATUSES = ['DELIVERED', 'SENT', 'FAILED'];

const isHistory = response => h.pathOnly(response.url()).endsWith('/sms/ViewSmsHistory');

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const ids = [];
  const auditRows = () => ids.length === 0 ? '0' : sql.value(`SELECT COUNT(*) FROM log
    WHERE action=${q(AUDIT_ACTION)} AND content=${q(AUDIT_CONTENT)} AND contentId IN (${ids.map(id => q(String(id))).join(',')})`);
  const otherMarker = newOwnedMarker();
  let other = null;
  s.cleanup(() => {
    if (ids.length) {
      sql.execute(`DELETE FROM log WHERE action=${q(AUDIT_ACTION)} AND content=${q(AUDIT_CONTENT)}
        AND contentId IN (${ids.map(id => q(String(id))).join(',')}); DELETE FROM sms_transaction WHERE id IN (${ids.join(',')})`);
      h.assert(sql.value(`SELECT COUNT(*) FROM sms_transaction WHERE id IN (${ids.join(',')})`) === '0', 'Seeded SMS rows were not removed');
      h.assert(auditRows() === '0', 'The SMS read audit rows were not removed');
    }
    if (other !== null) removeOwnedPatient(sql, other, otherMarker);
  });
  other = createOwnedPatient(sql, { marker: otherMarker, provider, firstName: 'Other' });

  /** Inserts one finished outbound message for a patient, `minutesAgo` old; returns its id and text. */
  function insertMessage(demographicNo, n, minutesAgo) {
    const body = `${marker} FAKE SMS text ${n}: your appointment is confirmed`;
    const sha = crypto.createHash('sha256').update(body, 'utf8').digest('hex');
    const id = sql.value(`INSERT INTO sms_transaction(direction,provider_type,message_purpose,status,demographic_no,
      requested_by_healthcare_provider_no,from_phone_number,to_phone_number,client_reference_id,message_body,
      message_body_sha256,message_body_length,created_at,updated_at,sent_at)
      VALUES('OUTBOUND','VOIPMS','PATIENT_MESSAGE',${q(STATUSES[n % STATUSES.length])},${demographicNo},${q(provider)},
      '5550100000',${q(`555${String(1000000 + n).slice(-7)}`)},${q(`${marker}-${n}`)},${q(body)},${q(sha)},${Buffer.byteLength(body, 'utf8')},
      NOW() - INTERVAL ${minutesAgo} MINUTE,NOW() - INTERVAL ${minutesAgo} MINUTE,NOW() - INTERVAL ${minutesAgo} MINUTE);
      SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), `SMS fixture ${n} was not inserted`);
    ids.push(Number(id));
    return { id, body };
  }
  // n = 1 is the newest.
  const messages = Array.from({ length: MESSAGES }, (_, i) => insertMessage(patient, i + 1, (i + 1) * 5));
  const elsewhere = insertMessage(other, 100, 1);

  const history = await s.popup(s.master, s.master.locator('.patient-nav a[href*="/sms/ViewSmsHistory"], a[href*="/sms/ViewSmsHistory"]').first(),
    'sms-history');
  h.assert(new URL(history.url()).searchParams.get('demographic_no') === patient, 'The SMS history opened for another patient');
  const rows = history.locator('#smsHistoryTable tbody tr');
  const rowIds = () => history.locator('#smsHistoryTable input[name="smsTransactionId"]').evaluateAll(els => els.map(el => el.value));
  const noText = async page => {
    const html = await page.content();
    h.assert(!html.includes(`${marker} FAKE SMS text`), 'The history page carries message text');
  };

  await s.step('the history lists the patient\'s messages newest first, 25 on page 1 of 2, with none of their text', async () => {
    await rows.first().waitFor({ state: 'visible', timeout: 20000 });
    h.assert(await rows.count() === PAGE_SIZE, `Page 1 lists ${await rows.count()} messages, not ${PAGE_SIZE}`);
    h.assert(JSON.stringify(await rowIds()) === JSON.stringify(messages.slice(0, PAGE_SIZE).map(m => m.id)),
      `Page 1 lists ${JSON.stringify(await rowIds())}, not the newest ${PAGE_SIZE} messages in order`);
    h.assert(/\b1\b.*\b2\b/.test(await history.locator('#smsHistoryPaging').innerText()), 'Page 1 does not say 1 of 2');
    await noText(history);
  });

  await s.step('Next shows the other 2, page 2 of 2', async () => {
    await Promise.all([history.waitForURL(/[?&]page=2(&|$)/, { timeout: 20000 }),
      history.locator('#smsHistoryPaging a[href*="page=2"]').click()]);
    h.assert(JSON.stringify(await rowIds()) === JSON.stringify(messages.slice(PAGE_SIZE).map(m => m.id)),
      `Page 2 lists ${JSON.stringify(await rowIds())}, not the oldest 2 messages`);
    await noText(history);
    await Promise.all([history.waitForURL(/[?&]page=1(&|$)/, { timeout: 20000 }),
      history.locator('#smsHistoryPaging a[href*="page=1"]').click()]);
  });

  /** The page's own form for one message, posted with fetch over the page's session; returns status and text. */
  const replay = (fields) => history.evaluate(async ({ action, fields }) => {
    const form = document.querySelector('#smsHistoryTable form');
    const body = new URLSearchParams();
    for (const [key, value] of Object.entries(fields)) body.set(key, value);
    const token = form.querySelector('input[name="CSRF-TOKEN"]');
    if (token) body.set('CSRF-TOKEN', token.value);
    const response = await fetch(action, { method: 'POST', body, credentials: 'same-origin' });
    return { status: response.status, text: await response.text() };
  }, { action: new URL('ViewSmsHistory', history.url()).toString(), fields });

  await s.step('Show message with a reason opens the message\'s full text and writes one audit row', async () => {
    const newest = messages[0];
    const form = history.locator('#smsHistoryTable form').filter({ has: history.locator(`input[name="smsTransactionId"][value="${newest.id}"]`) });
    await form.locator('select[name="reason"]').selectOption('CARE_REVIEW');
    const [opened] = await Promise.all([
      history.waitForResponse(response => isHistory(response) && response.request().method() === 'POST', { timeout: 20000 }),
      form.locator('button[type="submit"]').click(),
    ]);
    h.assert(opened.status() === 200, `Show message answered HTTP ${opened.status()}`);
    const shown = (await history.locator('#smsMessageBody').innerText()).trim();
    h.assert(shown === newest.body, `Show message opened "${shown}", not the message's text`);
    const audit = sql.rows(`SELECT demographic_no, data FROM log WHERE action=${q(AUDIT_ACTION)} AND content=${q(AUDIT_CONTENT)}
      AND contentId=${q(String(newest.id))}`);
    h.assert(audit.length === 1, `Opening the message wrote ${audit.length} audit rows`);
    h.assert(String(audit[0][0]) === patient && /reasonCode=CARE_REVIEW\b/.test(audit[0][1]),
      `The audit row does not name the patient and the reason: ${JSON.stringify(audit[0])}`);
    await history.goBack({ waitUntil: 'domcontentloaded' });
    await rows.first().waitFor({ state: 'visible', timeout: 20000 });
  });

  await s.step('the same request without a reason answers 400, opens no text and writes no audit row', async () => {
    const before = auditRows();
    const answer = await replay({ method: 'showMessage', demographic_no: patient, page: '1', smsTransactionId: messages[1].id });
    h.assert(answer.status === 400, `Show message without a reason answered HTTP ${answer.status}`);
    h.assert(!answer.text.includes(messages[1].body), 'Show message without a reason opened the text');
    h.assert(auditRows() === before, 'Show message without a reason wrote an audit row');
  });

  // Last: the message must be the patient's own.
  await s.step('a message that belongs to another patient answers 404, opens no text and writes no audit row', async () => {
    const before = auditRows();
    const answer = await replay({ method: 'showMessage', demographic_no: patient, page: '1', smsTransactionId: elsewhere.id,
      reason: 'CARE_REVIEW' });
    h.assert(answer.status === 404, `Another patient's message answered HTTP ${answer.status}`);
    h.assert(!answer.text.includes(elsewhere.body), 'Another patient\'s message was opened');
    h.assert(auditRows() === before, 'Another patient\'s message wrote an audit row');
  });
}

if (require.main === module) runWorkflow('sms-history', workflow, { openPatient: true });
module.exports = { workflow };
