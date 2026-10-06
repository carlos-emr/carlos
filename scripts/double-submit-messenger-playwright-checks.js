#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Double-submit check: Messenger Send Message.
 *
 * User path: Schedule top bar Msg > Compose Message > tick the (logged-in provider) recipient > type a
 * subject and a body into the Toast UI editor > Send Message. Each rapid activation (dblclick(), two
 * back-to-back clicks, double Enter in the Subject field, slow-response re-click) sends ONE message
 * with its own marker subject and the check asserts EXACTLY ONE messagetbl row and ONE messagelisttbl
 * delivery row for that subject. Captured POSTs are also replayed without client JavaScript.
 *
 * Fixtures: marker subjects only; cleanup deletes the messagelisttbl/msgDemoMap/messagetbl rows
 * whose subject carries the run marker and asserts they are gone. Recipient is the test login itself
 * (already a messenger contact on the install; the check does not touch group membership).
 * Wave-6 pattern sweep "double-submit".
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { failureMark, consumeExpectedFailure } = require('./lib/concurrency-support');
const { MODES_REPLAY: MODES, rapid, settledCount, watchPosts, verdicts, armSlowServer, sleep } = require('./lib/double-submit-helpers');

async function workflow(s) {
  const { sql, marker, provider } = s;
  const subj = (tag) => `${marker}-${tag} double submit`;
  const ids = () => sql.rows(`SELECT messageid FROM messagetbl WHERE thesubject LIKE ${h.sqlString(`${marker}-%`)}`).map(([id]) => id);
  // Child rows are scoped above high-water marks taken before anything is sent: demo-seeded installs ship orphan
  // messagelisttbl/msgDemoMap rows whose message ids this run's messages can reuse, and those are not ours.
  const listMark = Number(sql.value('SELECT IFNULL(MAX(id), 0) FROM messagelisttbl'));
  const demoMapMark = Number(sql.value('SELECT IFNULL(MAX(id), 0) FROM msgDemoMap'));
  s.cleanup(() => {
    const list = ids();
    for (const id of list) h.assert(/^[1-9]\d*$/.test(id), 'Owned message id is invalid');
    if (list.length) {
      sql.execute(`DELETE FROM messagelisttbl WHERE message IN (${list.join(',')}) AND id > ${listMark}`);
      sql.execute(`DELETE FROM msgDemoMap WHERE messageID IN (${list.join(',')}) AND id > ${demoMapMark}`);
      sql.execute(`DELETE FROM messagetbl WHERE messageid IN (${list.join(',')})`);
      h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM messagelisttbl WHERE message IN (${list.join(',')}) AND id > ${listMark})
        + (SELECT COUNT(*) FROM msgDemoMap WHERE messageID IN (${list.join(',')}) AND id > ${demoMapMark})`) === '0',
      'Owned delivery rows were not removed');
    }
    h.assert(ids().length === 0, 'Owned messages were not removed');
  });
  const v = verdicts('messenger-send');

  async function compose(subject) {
    const { page: inbox, isPopup: opened } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('a:has(#oscar_new_msg)').first(),
      { context: s.context, recorder: s.recorder, label: 'messenger-inbox', timeout: 20000 });
    console.log(`    (messenger ${opened ? 'popup' : 'in-place navigation'})`);
    await inbox.waitForLoadState('domcontentloaded', { timeout: 20000 });
    await ui.clickAndAwaitReload(inbox, inbox.locator('a[href*="/messenger/ViewCreateMessage"]').first(), { required: false });
    await inbox.locator('#subject').waitFor({ state: 'visible', timeout: 20000 });
    await inbox.locator(`input[name="provider"][id^="0-"][value^="${provider}-"]`).first().check();
    await inbox.locator('#subject').fill(subject);
    const editor = inbox.locator('.toastui-editor-ww-container .ProseMirror').first();
    await editor.waitFor({ state: 'visible', timeout: 15000 });
    await editor.click();
    await inbox.keyboard.type('double submit body');
    return { inbox, opened };
  }
  async function leave(inbox, opened) {
    if (opened) { if (!inbox.isClosed()) await inbox.close(); }
    else await h.gotoApp(s.schedule, s.config.baseUrl, '/provider/providercontrol?displaymode=day&dboperation=searchappointmentday&viewall=1');
  }

  for (const mode of MODES) {
    await s.step(`Send Message via ${mode.label} delivers at most one message`, async () => {
      const { inbox, opened } = await compose(subj(mode.tag));
      const route = /\/messenger\/CreateMessage$/;
      const submitted = [];
      const responses = [];
      const capture = request => {
        if (request.method() === 'POST' && route.test(request.url())) submitted.push(request);
      };
      const captureResponse = response => {
        if (response.request().method() === 'POST' && route.test(response.url())) responses.push(response);
      };
      inbox.on('request', capture);
      inbox.on('response', captureResponse);
      const failures = failureMark(s.recorder);
      const posts = watchPosts(inbox.context(), route);
      const disarm = mode.key === 'slowResubmit' ? await armSlowServer(s.context, /\/messenger\/CreateMessage$/) : null;
      await rapid(mode.key, inbox.locator('button[type="submit"]', { hasText: /Send Message/i }),
        { textField: inbox.locator('#subject') });
      const q = `SELECT COUNT(*) FROM messagetbl WHERE thesubject=${h.sqlString(subj(mode.tag))}`;
      const count = await settledCount(sql, q, { min: 1 });
      if (disarm) await disarm();
      posts.stop();
      if (mode.key === 'replay') {
        h.assert(responses.some(response => response.status() === 409), 'Reload did not reject the consumed message submission');
        consumeExpectedFailure(s.recorder, failures, { status: 409, path: route });
      }
      h.assert(submitted.length > 0, 'No message POST was observed');
      const original = submitted[0];
      const repeated = await s.context.request.post(original.url(), {
        data: original.postData(), headers: { 'Content-Type': original.headers()['content-type'] }, maxRedirects: 0,
      });
      h.assert(repeated.status() === 409 && (await repeated.text()).includes('Check Sent Messages'),
        'Direct replay was accepted or omitted recovery guidance');
      h.assert(sql.value(q) === '1', 'Direct replay created another message');
      if (mode.key === 'slowResubmit') h.assert(posts.seen.length === 1, 'Send remained active during a delayed response');
      inbox.off('request', capture);
      inbox.off('response', captureResponse);
      const listRows = sql.value(`SELECT COUNT(*) FROM messagelisttbl l JOIN messagetbl m ON m.messageid=l.message
        WHERE m.thesubject=${h.sqlString(subj(mode.tag))} AND l.id > ${listMark}`);
      console.log(`    (${posts.seen.length} CreateMessage POST(s) sent; ${listRows} delivery row(s))`);
      v.record(mode.label, count, { exactly: 1 });
      // One recipient was ticked, so exactly one delivery row must exist: a duplicated or lost
      // delivery is as much a double-submit defect as a duplicated message row.
      v.record(`${mode.label} (delivery rows)`, Number(listRows), { exactly: 1 });
      await leave(inbox, opened);
    });
  }
  await s.step('invalid and canceled submissions retain an editable message and can send once afterward', async () => {
    const subject = subj('CANCEL');
    const { inbox, opened } = await compose(subject);
    const send = inbox.locator('button[type="submit"]', { hasText: /Send Message/i });
    const q = `SELECT COUNT(*) FROM messagetbl WHERE thesubject=${h.sqlString(subject)}`;
    const token = await inbox.locator('input[name=carlosMessageSubmission]').inputValue();
    await inbox.locator('#subject').evaluate(field => field.setCustomValidity('Fixture requires correction'));
    await send.click();
    await sleep(300);
    h.assert(sql.value(q) === '0' && await send.isEnabled(), 'Invalid form sent or disabled the Send button');
    await inbox.locator('#subject').evaluate(field => field.setCustomValidity(''));
    await inbox.evaluate(() => window.addEventListener('submit', event => event.preventDefault(), { once: true }));
    await send.click();
    await sleep(300);
    h.assert(sql.value(q) === '0' && await send.isEnabled(), 'Late cancellation sent or prevented a corrected retry');
    await inbox.evaluate(() => {
      window.__messageTestUnload = event => { event.preventDefault(); event.returnValue = ''; };
      window.addEventListener('beforeunload', window.__messageTestUnload);
    });
    const dialogs = await h.withExpectedDialogs(inbox, () => Promise.all([
      inbox.waitForEvent('dialog', { predicate: dialog => dialog.type() === 'beforeunload' }),
      send.click({ noWaitAfter: true }),
    ]), { accept: false });
    await sleep(300);
    await inbox.evaluate(() => {
      window.removeEventListener('beforeunload', window.__messageTestUnload);
      delete window.__messageTestUnload;
    });
    h.assert(dialogs.length === 1 && sql.value(q) === '0' && await send.isEnabled(),
      'Canceled navigation sent or trapped the editable draft');
    h.assert(await inbox.locator('input[name=carlosMessageSubmission]').inputValue() === token,
      'Canceled submission changed the draft identity');
    await send.click();
    h.assert(await settledCount(sql, q) === 1, 'Corrected draft was not sent exactly once');
    await leave(inbox, opened);
  });

  await s.step('independent compose windows can each send identical message text', async () => {
    const subject = subj('WINDOWS');
    const { inbox: first, opened } = await compose(subject);
    const second = await s.context.newPage();
    await second.goto(first.url(), { waitUntil: 'domcontentloaded' });
    await second.locator('#subject').waitFor();
    await second.locator(`input[name="provider"][id^="0-"][value^="${provider}-"]`).first().check();
    await second.locator('#subject').fill(subject);
    await second.locator('.toastui-editor-ww-container .ProseMirror').first().click();
    await second.keyboard.type('double submit body');
    const firstToken = await first.locator('input[name=carlosMessageSubmission]').inputValue();
    const secondToken = await second.locator('input[name=carlosMessageSubmission]').inputValue();
    h.assert(firstToken !== secondToken, 'Independent compose windows shared a submission identity');
    for (const page of [first, second]) await page.locator('button[type="submit"]', { hasText: /Send Message/i }).click();
    h.assert(await settledCount(sql, `SELECT COUNT(*) FROM messagetbl WHERE thesubject=${h.sqlString(subject)}`, { min: 2 }) === 2,
      'Independent intended messages were discarded or duplicated');
    h.assert(sql.value(`SELECT COUNT(*) FROM messagelisttbl l JOIN messagetbl m ON m.messageid=l.message
      WHERE m.thesubject=${h.sqlString(subject)} AND l.id > ${listMark}`) === '2', 'Independent deliveries were discarded or duplicated');
    await second.close();
    await leave(first, opened);
  });
  v.finish();
}

module.exports = { workflow };
if (require.main === module) runWorkflow('double-submit-messenger', workflow, { openPatient: false });
