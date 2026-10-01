#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Double-submit check: Messenger Send Message.
 *
 * User path: Schedule top bar Msg > Compose Message > tick the (logged-in provider) recipient > type a
 * subject and a body into the Toast UI editor > Send Message. Each rapid activation (dblclick(), two
 * back-to-back clicks, double Enter in the Subject field, slow-response re-click) sends ONE message
 * with its own marker subject and the check asserts EXACTLY ONE messagetbl row and ONE messagelisttbl
 * delivery row for that subject. CreateMessage.jsp has no disable-on-submit and no token.
 *
 * Fixtures: marker subjects only; cleanup deletes the messagelisttbl/msgDemoMap/messagetbl rows
 * whose subject carries the run marker and asserts they are gone. Recipient is the test login itself
 * (already a messenger contact on the install; the check does not touch group membership).
 * Wave-6 pattern sweep "double-submit".
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { MODES_REPLAY: MODES, rapid, settledCount, watchPosts, verdicts, armSlowServer } = require('./lib/double-submit-helpers');

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

  for (const mode of MODES) {
    await s.step(`Send Message via ${mode.label} delivers at most one message`, async () => {
      const { page: inbox, isPopup: opened } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('a:has(#oscar_new_msg)').first(),
        { context: s.context, recorder: s.recorder, label: 'messenger-inbox', timeout: 20000 });
      console.log(`    (messenger ${opened ? 'popup' : 'in-place navigation'})`);
      await inbox.waitForLoadState('domcontentloaded', { timeout: 20000 });
      await ui.clickAndAwaitReload(inbox, inbox.locator('a[href*="/messenger/ViewCreateMessage"]').first(), { required: false });
      await inbox.locator('#subject').waitFor({ state: 'visible', timeout: 20000 });
      await inbox.locator(`input[name="provider"][id^="0-"][value^="${provider}-"]`).first().check();
      await inbox.locator('#subject').fill(subj(mode.tag));
      const editor = inbox.locator('.toastui-editor-ww-container .ProseMirror').first();
      await editor.waitFor({ state: 'visible', timeout: 15000 });
      await editor.click();
      await inbox.keyboard.type('double submit body');
      const posts = watchPosts(inbox.context(), /\/messenger\/CreateMessage$/);
      const disarm = mode.key === 'slowResubmit' ? await armSlowServer(s.context, /\/messenger\/CreateMessage$/) : null;
      await rapid(mode.key, inbox.locator('button[type="submit"]', { hasText: /Send Message/i }),
        { textField: inbox.locator('#subject') });
      const q = `SELECT COUNT(*) FROM messagetbl WHERE thesubject=${h.sqlString(subj(mode.tag))}`;
      const count = await settledCount(sql, q, { min: 1 });
      if (disarm) await disarm();
      posts.stop();
      const listRows = sql.value(`SELECT COUNT(*) FROM messagelisttbl l JOIN messagetbl m ON m.messageid=l.message
        WHERE m.thesubject=${h.sqlString(subj(mode.tag))} AND l.id > ${listMark}`);
      console.log(`    (${posts.seen.length} CreateMessage POST(s) sent; ${listRows} delivery row(s))`);
      v.record(mode.label, count, { exactly: 1 });
      // One recipient was ticked, so exactly one delivery row must exist: a duplicated or lost
      // delivery is as much a double-submit defect as a duplicated message row.
      v.record(`${mode.label} (delivery rows)`, Number(listRows), { exactly: 1 });
      if (opened) { if (!inbox.isClosed()) await inbox.close().catch(() => {}); }
      else await h.gotoApp(s.schedule, s.config.baseUrl, '/provider/providercontrol?displaymode=day&dboperation=searchappointmentday&viewall=1');
    });
  }
  v.finish();
}

module.exports = { workflow };
if (require.main === module) runWorkflow('double-submit-messenger', workflow, { openPatient: false });
