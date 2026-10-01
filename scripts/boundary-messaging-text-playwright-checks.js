#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Special characters and field lengths in tickler text and Messenger subject/body (wave 6, boundary values).
 * User path: Master Record > Tickler > Add Tickler > text > Save, then Schedule > Msg > Compose > recipient >
 * subject + body > Send Message > the message in the inbox.
 * Asserts: a tickler text with an apostrophe, accents, CJK, an emoji, "&amp;", quotes, backslash, "%41", "+"
 * and ";" is stored byte for byte and listed unchanged; a message subject that fills the 128-character column
 * and a body with the same characters are stored whole and shown unchanged by the message view. Last (the
 * defects): a subject past the column and a tickler text past the 65,535-byte TEXT column are refused or
 * visibly limited, never silently cut.
 * Fixtures: the owned FAKE- patient (lib/workflow-session.js); ticklers and messages carry the run marker and
 * are deleted by cleanup, which asserts they are gone.
 * Implements the wave-6 "boundary values" pattern, Part 1 (tickler text, message subject/body).
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const b = require('./lib/boundary-values');
const { runWorkflow } = require('./lib/workflow-session');

function fillCodePoints(length, parts, pad = 'x') {
  let value = '';
  for (const part of parts) {
    const next = value + (value ? ' ' : '') + part;
    if (b.cpLength(next) <= length) value = next;
  }
  return value + pad.repeat(length - b.cpLength(value));
}

async function pollFor(sql, query, predicate, message) {
  const deadline = Date.now() + 20000;
  let value;
  do {
    value = sql.value(query);
    if (predicate(value)) return value;
    await new Promise(resolve => setTimeout(resolve, 250));
  } while (Date.now() < deadline);
  h.assert(false, message);
  return value;
}

async function workflow(s) {
  const { sql, marker, patient, provider } = s;
  const T = b.TOKENS;
  const parts = [T.apostrophe, T.latin, T.cjk, T.emoji, T.entity, T.quotes, T.backslash, T.percent, T.plus, T.semicolon];
  const ownedTicklers = `demographic_no=${patient} AND message LIKE ${h.sqlString(`${marker}%`)}`;
  const messageIds = () => sql.rows(`SELECT messageid FROM messagetbl WHERE thesubject LIKE ${h.sqlString(`${marker}%`)}`).map(row => row[0]);
  s.cleanup(() => {
    const ids = sql.rows(`SELECT tickler_no FROM tickler WHERE ${ownedTicklers}`).map(row => row[0]);
    h.assert(ids.every(id => /^[1-9]\d*$/.test(id)), 'Owned tickler id is invalid');
    if (ids.length) {
      sql.execute(`DELETE FROM ticklerdocs WHERE tickler_id IN (${ids.join(',')});
        DELETE FROM tickler_comments WHERE tickler_no IN (${ids.join(',')});
        DELETE FROM tickler_update WHERE tickler_no IN (${ids.join(',')});
        DELETE FROM tickler WHERE ${ownedTicklers} AND tickler_no IN (${ids.join(',')})`);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler WHERE ${ownedTicklers}`) === '0', 'Owned ticklers were not removed');
    const list = messageIds();
    list.forEach(id => h.assert(/^[1-9]\d*$/.test(id), 'Owned message id is invalid'));
    if (list.length) {
      sql.execute(`DELETE FROM messagelisttbl WHERE message IN (${list.join(',')});
        DELETE FROM msgDemoMap WHERE messageID IN (${list.join(',')});
        DELETE FROM messagetbl WHERE messageid IN (${list.join(',')})`);
    }
    h.assert(messageIds().length === 0, 'Owned messages were not removed');
  });

  async function addTickler(text) {
    const list = await s.popup(s.master, s.master.locator('a[onclick*="/tickler/ViewTicklerMain"]').first(), 'patient-tickler-list');
    const add = await s.popup(list, list.locator('input.btn-primary[onclick*="/tickler/ViewAddTickler"]').first(), 'tickler-add');
    await add.locator('form[name="serviceform"]').waitFor({ state: 'visible', timeout: 20000 });
    await add.locator('textarea[name="ticklerMessage"]').fill(text);
    await add.locator('input[name="xml_appointment_date"]').fill(sql.value('SELECT CURDATE()'));
    await add.locator('input.btn-primary[name="Button"]').first().click();
    await add.waitForFunction(() => {
      const frame = document.getElementById('ticklerSubmitFrame');
      return frame && frame.contentDocument && frame.contentDocument.getElementById('tickler-save-ok');
    }, null, { timeout: 30000 });
    await add.close().catch(() => {});
    return list;
  }

  const tickler = `${marker} ${fillCodePoints(400, parts)}`;
  await s.step('a tickler text with special characters is stored byte for byte and listed unchanged', async () => {
    const list = await addTickler(tickler);
    const id = await pollFor(sql, `SELECT tickler_no FROM tickler WHERE ${ownedTicklers}`, v => /^\d+$/.test(v), 'The tickler was not saved');
    b.assertStored(sql, 'tickler', 'message', `tickler_no=${id}`, tickler, 'Tickler text');
    await list.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    await list.locator('#ticklerResults').waitFor({ state: 'visible', timeout: 20000 });
    await list.locator('#ticklerResults_filter input[type="search"]').fill(marker);
    const row = list.locator('#ticklerResults tbody tr').filter({ hasText: marker }).first();
    await row.waitFor({ state: 'visible', timeout: 20000 });
    const shown = (await row.innerText()).replace(/\s+/g, ' ');
    h.assert(shown.includes(tickler.replace(/\s+/g, ' ')), 'The tickler list does not show the saved text exactly as typed');
    await list.close().catch(() => {});
  });

  const subject = `${marker} ${fillCodePoints(b.columnLength(sql, 'messagetbl', 'thesubject') - b.cpLength(marker) - 1, parts)}`;
  const body = `${marker} body ${parts.join(' ')}`;
  async function compose() {
    const { page: inbox, opened } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('a:has(#oscar_new_msg)').first(),
      { context: s.context, recorder: s.recorder, label: 'messenger-inbox', timeout: 20000 });
    console.log(`    (messenger ${opened ? 'popup' : 'in-place navigation'})`);
    await inbox.waitForLoadState('domcontentloaded', { timeout: 20000 });
    await ui.clickAndAwaitReload(inbox, inbox.locator('a[href*="/messenger/ViewCreateMessage"]').first(), { required: false });
    await inbox.locator('#subject').waitFor({ state: 'visible', timeout: 20000 });
    await inbox.locator(`input[name="provider"][id^="0-"][value^="${provider}-"]`).first().check();
    inbox.__opened = opened;
    return inbox;
  }
  // The messenger is a popup or, in the focused schedule mode, the schedule tab itself: only a popup is closed.
  async function leave(page) {
    if (page.__opened === false || page === s.schedule) {
      await h.gotoApp(s.schedule, s.config.baseUrl, '/provider/providercontrol?displaymode=day&dboperation=searchappointmentday&viewall=1');
    } else await page.close().catch(() => {});
  }
  async function openInbox(label) {
    const { page, opened } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('a:has(#oscar_new_msg)').first(),
      { context: s.context, recorder: s.recorder, label, timeout: 20000 });
    page.__opened = opened;
    await page.waitForLoadState('domcontentloaded', { timeout: 20000 });
    return page;
  }
  async function send(inbox, subjectText, bodyText) {
    await inbox.locator('#subject').fill(subjectText);
    const editor = inbox.locator('.toastui-editor-ww-container .ProseMirror').first();
    await editor.waitFor({ state: 'visible', timeout: 15000 });
    await editor.click();
    await inbox.keyboard.type(bodyText);
    await inbox.locator('button[type="submit"]', { hasText: /Send Message/i }).click();
    await inbox.waitForLoadState('domcontentloaded', { timeout: 30000 }).catch(() => {});
  }

  await s.step('a message subject of exactly the column length and a body of special characters are stored and shown unchanged', async () => {
    const inbox = await compose();
    await send(inbox, subject, body);
    const id = await pollFor(sql, `SELECT messageid FROM messagetbl WHERE thesubject LIKE ${h.sqlString(`${marker}%`)} ORDER BY messageid DESC LIMIT 1`,
      v => /^\d+$/.test(v), 'The message was not sent');
    b.assertStored(sql, 'messagetbl', 'thesubject', `messageid=${id}`, subject, 'Message subject at the column length');
    const stored = b.readStored(sql, 'messagetbl', 'themessage', `messageid=${id}`);
    const storedBody = Buffer.from(stored.hex, 'hex').toString('utf8');
    for (const token of [T.cjk, T.emoji, T.latin, T.entity, T.percent]) {
      h.assert(storedBody.includes(token), `The stored message body lost or altered "${token}" (stored body: ${storedBody.slice(0, 200)})`);
    }
    // The inbox row, then the message view the recipient reads.
    await leave(inbox);
    const box = await openInbox('messenger-inbox-2');
    const link = box.locator(`a[href*="/messenger/ViewMessage?messageID=${id}&"]`).first();
    await link.waitFor({ state: 'attached', timeout: 20000 });
    h.assert((await link.innerText()).replace(/\s+/g, ' ').trim() === subject, 'The inbox does not list the subject exactly as stored');
    await ui.clickAndAwaitReload(box, link, { required: false });
    const view = (await box.locator('body').innerText()).replace(/\s+/g, ' ');
    for (const token of [T.cjk, T.emoji, T.latin, T.entity, T.percent, T.apostrophe]) {
      h.assert(view.includes(token), `The message view does not show "${token}" as typed`);
    }
    await leave(box);
  });

  await s.step('a subject past the column and a tickler text past the TEXT column are refused or stored whole', async () => {
    const problems = [];
    const column = b.columnLength(sql, 'messagetbl', 'thesubject');
    const longSubject = `${marker} ${b.exactly(column + 1 - b.cpLength(marker) - 1, 'S')}`;
    const inbox = await compose();
    await send(inbox, longSubject, 'long subject body');
    await new Promise(resolve => setTimeout(resolve, 1500));
    const rows = sql.rows(`SELECT HEX(thesubject), CHAR_LENGTH(thesubject) FROM messagetbl WHERE thesubject LIKE ${h.sqlString(`${marker}%`)} AND CHAR_LENGTH(thesubject) < ${column + 1} AND thesubject LIKE ${h.sqlString(`${marker} S%`)}`);
    if (rows.length && rows[0][0] !== b.hex(longSubject)) {
      problems.push(`subject: ${b.cpLength(longSubject)} characters were typed into a Subject box with no maxlength and the message was sent, but ${rows[0][1]} were stored (messagetbl.thesubject is ${column}); silent truncation`);
    }
    await leave(inbox);
    const textBytes = 65535;
    const longTickler = `${marker} ${'long tickler text '.repeat(Math.ceil(textBytes / 18) + 40)}`;
    h.assert(Buffer.byteLength(longTickler) > textBytes, 'Test bug: the long tickler text is not past the column');
    const list = await addTickler(longTickler);
    await list.close().catch(() => {});
    await new Promise(resolve => setTimeout(resolve, 1500));
    const stored = sql.rows(`SELECT LENGTH(message) FROM tickler WHERE ${ownedTicklers} AND message LIKE ${h.sqlString(`${marker} long tickler%`)}`);
    if (stored.length && Number(stored[0][0]) !== Buffer.byteLength(longTickler)) {
      problems.push(`tickler text: ${Buffer.byteLength(longTickler)} bytes were typed into a box with no maxlength and the tickler was saved, but ${stored[0][0]} bytes were stored (tickler.message is TEXT, 65,535 bytes); silent truncation`);
    } else if (!stored.length) {
      problems.push('tickler text: a very long tickler was not saved and nothing told the user');
    }
    h.assert(problems.length === 0, problems.join(' || '));
  });
}

if (require.main === module) runWorkflow('boundary-messaging-text', workflow, { openPatient: true });
module.exports = { workflow };
