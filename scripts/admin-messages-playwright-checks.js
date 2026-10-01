#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * System Messages workflow (coverage plan §3.7 admin-messages).
 *
 * User path: Schedule ▸ Administration ▸ CAISI ▸ System Messages (hosted in the
 * administration iframe) ▸ Create New Message ▸ Save; Schedule reload (the day
 * sheet fetches SystemMessage?method=view and injects it into #system_message);
 * Administration ▸ System Messages ▸ edit (pencil) ▸ Save with a past expiry.
 *
 * Asserted: the empty-message validation alert fires and nothing is posted; a
 * message carrying punctuation and a <script> literal is listed with its exact
 * text and stored in SystemMessage with a future expiry; the day-sheet banner
 * shows the literal text as text (no <script> element, no dialog, no page
 * error); editing the same row to a past expiry keeps its id, marks it expired
 * in the list, and removes it from the banner on the next day-sheet load.
 *
 * Fixtures: only SystemMessage rows created through the UI in this run; cleanup
 * deletes rows above the pre-run id high-water mark that carry the run marker
 * and asserts they are gone. The UI offers no delete control (reported).
 */
const h = require('./lib/playwright-harness');
const {clickOpensPopupOrNavigates} = require('./lib/playwright-ui');
const {revealAuditLink} = require('./lib/playwright-link-audit');
const {runWorkflow, expectValue} = require('./lib/workflow-session');

const TIMEOUT = 20000;

function isBannerView(response) {
  const url = new URL(response.url());
  return url.pathname.endsWith('/SystemMessage') && url.searchParams.get('method') === 'view';
}

// Perform one action that navigates the administration iframe and wait for the
// new document; the Frame object survives in-frame navigations.
async function frameNavigation(admin, frame, action) {
  const navigated = admin.waitForEvent('framenavigated', {predicate: candidate => candidate === frame, timeout: TIMEOUT});
  navigated.catch(() => {});
  await action();
  await navigated;
  await frame.waitForLoadState('domcontentloaded', {timeout: TIMEOUT});
  await frame.waitForLoadState('networkidle', {timeout: TIMEOUT}).catch(() => {});
  h.assert((await frame.title()) === 'System Messages', 'The administration frame left the System Messages pages');
}

async function workflow(s) {
  const {sql, marker} = s;
  const message = `${marker} O'Neil "A&B" 100% <script>alert('pw')</script> done`;
  const highWater = Number(sql.value('SELECT COALESCE(MAX(id),0) FROM SystemMessage'));
  const ownedPredicate = `id>${highWater} AND message LIKE ${h.sqlString(marker + '%')}`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM SystemMessage WHERE ${ownedPredicate}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM SystemMessage WHERE ${ownedPredicate}`) === '0', 'Owned system messages were not removed');
  });
  const [today, tomorrow, yesterday] = sql.rows(
    'SELECT CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 DAY), DATE_SUB(CURDATE(), INTERVAL 1 DAY)')[0];
  h.assert(/^\d{4}-\d{2}-\d{2}$/.test(today), 'The database did not report a calendar date');
  const scheduleUrl = s.schedule.url();

  let admin;
  let isPopup = true;
  let frame;
  // Enter System Messages through the schedule's Administration opener and the
  // CAISI accordion; the page is hosted in the shell's #myFrame iframe.
  async function messagesFrame() {
    if (admin && !admin.isClosed() && frame && !frame.isDetached()) return frame;
    ({page: admin, isPopup} = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
      {context: s.context, recorder: s.recorder, label: 'messages-administration', timeout: TIMEOUT}));
    const link = admin.getByRole('link', {name: 'System Messages', exact: true, includeHidden: true}).first();
    await revealAuditLink(admin, link, TIMEOUT);
    await link.click();
    const iframe = admin.locator('#dynamic-content iframe[src*="/SystemMessage"]').first();
    await iframe.waitFor({timeout: TIMEOUT});
    frame = await (await iframe.elementHandle()).contentFrame();
    h.assert(frame, 'System Messages did not load in the administration frame');
    await frame.waitForLoadState('domcontentloaded', {timeout: TIMEOUT});
    await frame.locator('input[value="Create New Message"]').waitFor({timeout: TIMEOUT});
    return frame;
  }
  // Load the day sheet again and wait until its banner request has been applied
  // (jQuery runs the success callback before it decrements jQuery.active).
  async function reloadDaySheet() {
    const banner = s.schedule.waitForResponse(isBannerView, {timeout: TIMEOUT});
    if (admin && !isPopup && !admin.isClosed()) { frame = null; await s.schedule.goto(scheduleUrl); } else await s.schedule.reload();
    const response = await banner;
    h.assert(response.status() === 200, `The banner request answered HTTP ${response.status()}`);
    await s.schedule.locator('#scheduleTable').waitFor({timeout: TIMEOUT});
    await s.schedule.waitForFunction(() => window.jQuery && window.jQuery.active === 0, null, {timeout: TIMEOUT});
    return s.schedule.locator('#system_message');
  }
  async function openEditor(action) {
    const list = await messagesFrame();
    await frameNavigation(admin, list, action);
    await list.locator('input[name="system_message.message"]').waitFor({timeout: TIMEOUT});
    return list;
  }
  async function save(editor, expiryDay, text) {
    await editor.locator('input[name="system_message.expiry_day"]').fill(expiryDay);
    await editor.locator('input[name="system_message.message"]').fill(text);
    await frameNavigation(admin, editor, () => editor.locator('input[name="submit"]').click());
    // The save result is the list page; its "Saved" action message is not
    // rendered by the list (reported), so the list control and the row are the proof.
    await editor.locator('input[value="Create New Message"]').waitFor({timeout: TIMEOUT});
  }

  let id;
  await s.step('an empty message is refused by the editor without a request', async () => {
    const editor = await openEditor(() => frame.locator('input[value="Create New Message"]').click());
    let posts = 0;
    const listener = request => { if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/SystemMessage')) posts++; };
    s.context.on('request', listener);
    try {
      const dialogs = await h.withExpectedDialogs(admin, async () => {
        await editor.locator('input[name="system_message.expiry_day"]').fill(tomorrow);
        await editor.locator('input[name="submit"]').click();
        await editor.locator('input[name="system_message.message"]').waitFor({timeout: TIMEOUT});
      });
      h.assert(dialogs.length === 1 && /required/.test(dialogs[0].text || ''), 'The required-message validation alert did not fire exactly once');
      h.assert(posts === 0, 'A refused message was still posted');
    } finally { s.context.off('request', listener); }
    h.assert(sql.value(`SELECT COUNT(*) FROM SystemMessage WHERE ${ownedPredicate}`) === '0', 'A refused message reached the database');
  });
  await s.step('a punctuated message with a script literal is listed and stored with a future expiry', async () => {
    await save(frame, tomorrow, message);
    h.assert(await frame.getByRole('cell', {name: message, exact: true}).count() === 1, 'The list does not show the exact message text once');
    await expectValue(sql, `SELECT COUNT(*) FROM SystemMessage WHERE ${ownedPredicate}`, '1', 'Exactly one owned message was not stored');
    id = sql.value(`SELECT id FROM SystemMessage WHERE ${ownedPredicate}`);
    h.assert(/^[1-9]\d*$/.test(id), 'The stored message has no id');
    h.assert(sql.value(`SELECT CONCAT(BINARY message=BINARY ${h.sqlString(message)}, '|', expiryDate>NOW(), '|', DATE(expiryDate)) FROM SystemMessage WHERE id=${id}`)
      === `1|1|${tomorrow}`, 'The stored message text or expiry does not match what was entered');
    h.assert(await frame.locator(`tr[style*="red"] a[href*="method=edit&id=${id}"]`).count() === 0, 'An active message is listed as expired');
  });
  await s.step('the day sheet shows the active message as text and does not execute its script', async () => {
    const banner = await reloadDaySheet();
    await banner.locator('font', {hasText: marker}).waitFor({timeout: TIMEOUT});
    const texts = (await banner.locator('font').allInnerTexts()).map(text => text.trim());
    h.assert(texts.filter(text => text === message).length === 1, 'The banner does not show the literal message text exactly once');
    h.assert(await banner.locator('script, img, b').count() === 0, 'The banner rendered message markup as elements');
    h.assert(await s.schedule.locator('#system_message script').count() === 0, 'The system message injected a script element');
  });
  await s.step('editing the message to a past expiry keeps its id and lists it as expired', async () => {
    const editor = await openEditor(() => frame.locator(`a[href*="method=edit&id=${id}"]`).click());
    await save(editor, yesterday, message);
    h.assert(sql.value(`SELECT COUNT(*) FROM SystemMessage WHERE ${ownedPredicate}`) === '1', 'Editing created a second message');
    h.assert(sql.value(`SELECT CONCAT(expiryDate<NOW(), '|', DATE(expiryDate), '|', BINARY message=BINARY ${h.sqlString(message)}) FROM SystemMessage WHERE id=${id}`)
      === `1|${yesterday}|1`, 'The edited expiry or text did not persist on the same row');
    h.assert(await frame.locator(`tr[style*="red"] a[href*="method=edit&id=${id}"]`).count() === 1, 'The expired message is not listed in the expired style');
  });
  await s.step('the expired message no longer appears on the day sheet', async () => {
    const banner = await reloadDaySheet();
    h.assert(!(await banner.innerText()).includes(marker), 'The expired message is still shown on the day sheet');
    h.assert(await banner.locator('font', {hasText: marker}).count() === 0, 'The expired message is still rendered in the banner');
  });
  if (admin && isPopup && !admin.isClosed()) await admin.close();
}

if (require.main === module) runWorkflow('admin-messages', workflow, {openPatient: false});
module.exports = {workflow};
