#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * gap-provider-messenger-write-to-encounter — a patient-linked message pasted into the patient's chart.
 *
 * User path: Schedule ▸ Msg ▸ open a message linked to a patient ▸ the patient row's "Write to
 * Encounter" button (messenger/ViewMessage popup(...,'writeToEncounter') ▸ POST
 * messenger/WriteToEncounter ▸ redirect encounter/IncomingEncounter?...&msgId=) ▸ the chart opens
 * with the message in the new-note editor ▸ Sign & Save.
 * messenger-demographic-link-playwright-checks covers Reply/Forward/Link/Unlink; nothing drove
 * this button, so a break in the redirect, the msgId plumbing or the message formatting reached a
 * clinician first.
 *
 * Asserted: the button opens the chart for the LINKED patient (not a different one); the note
 * editor starts with the formatted message (From/To/Date/Subject/body lines); the audit log carries
 * the MsgWriteToEncounter2Action row for that patient; Sign & Save stores a signed casemgmt_note for
 * the patient whose text is the pasted message; the message itself is untouched (still delivered,
 * still linked, not duplicated).
 * Regressions include a missing CSRF token, a lost message ID across chart redirects and sidebar
 * requests rebuilding the shared encounter bean before the note fragment renders.
 * Fixtures: an owned FAKE- patient (runWorkflow) and one FAKE-PW message delivered to the test
 * provider by SQL and linked by msgDemoMap. Cleanup deletes the message rows, the notes written on
 * the owned patient (+ issue/ext/link rows) and the chart rows, and asserts they are gone.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const TIMEOUT = 20000;

async function workflow(s) {
  const { sql, marker, patient } = s;
  const provider = h.sqlString(s.provider);
  const subject = `${marker} write to chart`;
  const body = `${marker} line one\nline two`;
  let messageId;
  s.cleanup(() => {
    const notes = sql.rows(`SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`).map(row => row[0]);
    h.assert(notes.every(id => /^\d+$/.test(id)), 'Owned note id is invalid');
    const list = notes.length ? notes.join(',') : '0';
    sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${list});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${list});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${list});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient};
      DELETE FROM eChart WHERE demographicNo=${patient};
      DELETE FROM log WHERE action='MsgWriteToEncounter2Action' AND data=${h.sqlString(`demographicNo=${patient}`)}`);
    const ids = sql.rows(`SELECT messageid FROM messagetbl WHERE thesubject LIKE ${h.sqlString(`%${marker}%`)}`).map(row => row[0]);
    if (ids.length) {
      const idList = ids.map(Number).join(',');
      sql.execute(`DELETE FROM msgDemoMap WHERE messageID IN (${idList});
        DELETE FROM messagelisttbl WHERE message IN (${idList});
        DELETE FROM messagetbl WHERE messageid IN (${idList}) AND thesubject LIKE ${h.sqlString(`%${marker}%`)}`);
    }
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM messagetbl WHERE thesubject LIKE ${h.sqlString(`%${marker}%`)})`) === '0', 'Owned note/message rows were not removed');
  });
  const location = sql.value('SELECT locationId FROM oscarcommlocations WHERE current1=1 LIMIT 1');
  h.assert(/^\d+$/.test(location), 'No current messenger location is configured');
  messageId = sql.value(`INSERT INTO messagetbl(thedate,theime,themessage,thesubject,sentby,sentto,sentbyNo,sentByLocation,type)
    VALUES(CURDATE(),CURTIME(),${h.sqlString(body)},${h.sqlString(subject)},'FAKE-PW sender','FAKE-PW recipient',${provider},${location},0);
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(messageId), 'Message fixture was not created');
  sql.execute(`INSERT INTO messagelisttbl(message,provider_no,status,remoteLocation,destinationFacilityId,sourceFacilityId)
    VALUES(${messageId},${provider},'new',${location},0,0);
    INSERT INTO msgDemoMap(messageID,demographic_no) VALUES(${messageId},${patient})`);
  const ownedState = () => JSON.stringify([
    sql.rows(`SELECT messageid,thesubject,themessage FROM messagetbl WHERE thesubject LIKE ${h.sqlString(`%${marker}%`)}`),
    sql.rows(`SELECT demographic_no FROM msgDemoMap WHERE messageID=${messageId}`)]);
  const before = ownedState();
  const logRows = () => sql.value(`SELECT COUNT(*) FROM log WHERE provider_no=${provider} AND action='MsgWriteToEncounter2Action'
    AND data LIKE ${h.sqlString(`%demographicNo=${patient}%`)}`);
  const logBefore = logRows();

  let inbox;
  let chart;
  await s.step('Schedule ▸ Msg ▸ the owned message opens with the linked patient and a Write to Encounter button', async () => {
    ({ page: inbox } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('a:has(#oscar_new_msg)').first(),
      { context: s.context, recorder: s.recorder, label: 'messenger-inbox', timeout: TIMEOUT }));
    await inbox.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
    await Promise.all([
      inbox.waitForResponse(r => r.request().isNavigationRequest() && new URL(r.url()).pathname.endsWith('/messenger/ViewMessage')),
      inbox.locator(`a[href*="/messenger/ViewMessage?messageID=${messageId}&"]`).first().click(),
    ]);
    await inbox.locator('#msgSubject').waitFor();
    await inbox.locator(`input[title="${patient}"]`).waitFor();
    h.assert(await inbox.locator('button[name="writeEncounter"]').count() === 1, 'The message offers no Write to Encounter button for the linked patient');
  });

  await s.step('Write to Encounter opens the chart of the linked patient with the message in the note editor', async () => {
    const pagesBefore = new Set(s.context.pages());
    const posted = s.context.waitForEvent('response', {
      predicate: r => r.request().method() === 'POST' && new URL(r.url()).pathname.endsWith('/messenger/WriteToEncounter'), timeout: TIMEOUT });
    posted.catch(() => {});
    await inbox.locator('button[name="writeEncounter"]').click();
    const answer = await posted;
    // ViewMessage builds this form at click time, so CSRFGuard has not given it a token (same pattern as Link to Patient).
    h.assert(answer.status() !== 403, 'POST messenger/WriteToEncounter was refused with HTTP 403: the form ViewMessage builds when the button is clicked carries no CSRF token, so the button opens an error page instead of the chart');
    const deadline = Date.now() + TIMEOUT;
    while (!chart && Date.now() < deadline) {
      chart = s.context.pages().find(page => !pagesBefore.has(page) && !page.isClosed() && /\/encounter\/IncomingEncounter|casemgmt/.test(page.url()));
      if (!chart) await new Promise(resolve => setTimeout(resolve, 250));
    }
    h.assert(chart, 'Write to Encounter did not open a chart window');
    await chart.waitForLoadState('domcontentloaded');
    await chart.locator('#encMainDiv textarea[name="caseNote_note"]').first().waitFor({ timeout: TIMEOUT });
    h.assert(new URL(chart.url()).searchParams.get('demographicNo') === String(patient)
      || await chart.locator(`input[name="demographicNo"][value="${patient}"], input[name="demographic_no"][value="${patient}"]`).count() > 0,
    'The chart that opened is not for the linked patient');
    const text = await chart.locator('#encMainDiv textarea[name="caseNote_note"]').first().inputValue();
    h.assert(text.includes('From: FAKE-PW sender') && text.includes('To: FAKE-PW recipient') && text.includes(`Subject: ${subject}`)
      && text.includes(`${marker} line one`) && text.includes('line two'),
    'The note editor does not start with the formatted message (From/To/Subject/body)');
    h.assert(logRows() === String(Number(logBefore) + 1), 'The write-to-encounter access wrote no audit row for the patient');
  });

  await s.step('Sign & Save stores the pasted message as a signed note for the linked patient', async () => {
    await chart.waitForFunction(() => window.carlosNavbarLoadState?.pending === 0, null, { timeout: TIMEOUT });
    const closed = chart.waitForEvent('close', { timeout: 30000 });
    await chart.locator('#signSaveImg').first().click();
    await closed;
    await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient} AND signed=1
      AND note LIKE ${h.sqlString(`%Subject: ${subject}%`)} AND note LIKE ${h.sqlString(`%${marker} line one%`)}`, '1',
    'The signed note does not hold the pasted message for the linked patient');
    h.assert(chart.isClosed(), 'The chart stayed open after Sign & Save');
  });

  await s.step('the message itself is unchanged and still linked', async () => {
    h.assert(ownedState() === before, 'Writing the message to the chart changed or duplicated the message');
    // Still delivered: the provider's delivery row remains (its status is not asserted, opening the message marks it read).
    h.assert(sql.value(`SELECT COUNT(*) FROM messagelisttbl WHERE message=${messageId} AND provider_no=${provider}`) === '1',
      'Writing the message to the chart removed its delivery row');
  });

  await s.step('invalid message IDs receive a controlled bad-request response', async () => {
    for (const messageId of ['not-a-number', '2147483648', '0']) {
      const response = await s.context.request.get(`${s.config.baseUrl}/CaseManagementEntry`, {
        params: { method: 'setUpMainEncounter', demographicNo: patient, msgId: messageId }, maxRedirects: 0,
      });
      h.assert(response.status() === 400, `Invalid msgId ${messageId} answered HTTP ${response.status()}`);
    }
  });

}

if (require.main === module) runWorkflow('gap-provider-messenger-write-to-encounter', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
