#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Special characters and field lengths in tickler text and Messenger subject/body (wave 6, boundary values).
 * User path: Master Record > Tickler > Add Tickler > text > Save, then Schedule > Msg > Compose > recipient >
 * subject + body > Send Message > the message in the inbox.
 * Asserts: a tickler text with an apostrophe, accents, CJK, an emoji, "&amp;", quotes, backslash, "%41", "+"
 * and ";" is stored byte for byte and listed unchanged; a message subject that fills the 128-character column
 * and a body with the same characters are stored whole and shown unchanged by the message view. A subject
 * past the column and a tickler text past the 65,535-byte TEXT column are refused with the draft intact.
 * Fixtures: the owned FAKE- patient (lib/workflow-session.js); ticklers and messages carry the run marker and
 * are deleted by cleanup, which asserts they are gone.
 * Implements the wave-6 "boundary values" pattern, Part 1 (tickler text, message subject/body).
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const b = require('./lib/boundary-values');
const { runWorkflow } = require('./lib/workflow-session');
const { failureMark, consumeExpectedFailure } = require('./lib/concurrency-support');


function fillCodePoints(length, parts, pad = 'x') {
  let value = '';
  for (const part of parts) {
    const next = value + (value ? ' ' : '') + part;
    if (b.cpLength(next) <= length) value = next;
  }
  return value + pad.repeat(length - b.cpLength(value));
}

/** True when every part occurs in `text`, each after the end of the previous one (whitespace collapsed, as a page shows it). */
function inOrder(text, parts) {
  const flat = String(text).replace(/\s+/g, ' ');
  let from = 0;
  for (const part of parts) {
    const at = flat.indexOf(part.replace(/\s+/g, ' '), from);
    if (at < 0) return false;
    from = at + part.length;
  }
  return true;
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

  // allowRefusal exercises an oversized message: require a 400, a length warning, and an editable draft.
  // Returns {list, refusal} with refusal the alert text or null for successful saves.
  async function addTickler(text, { allowRefusal = false } = {}) {
    const list = await s.popup(s.master, s.master.locator('a[onclick*="/tickler/ViewTicklerMain"]').first(), 'patient-tickler-list');
    const add = await s.popup(list, list.locator('input.btn-primary[onclick*="/tickler/ViewAddTickler"]').first(), 'tickler-add');
    await add.locator('form[name="serviceform"]').waitFor({ state: 'visible', timeout: 20000 });
    await add.locator('textarea[name="ticklerMessage"]').fill(text);
    await add.locator('input[name="xml_appointment_date"]').fill(sql.value('SELECT CURDATE()'));
    const saved = () => add.waitForFunction(() => {
      const frame = document.getElementById('ticklerSubmitFrame');
      return frame && frame.contentDocument && frame.contentDocument.getElementById('tickler-save-ok');
    }, null, { timeout: 30000 });
    let refusal = null;
    if (allowRefusal) {
      const mark = failureMark(s.recorder);
      const dialogs = await h.withExpectedDialogs(add, async () => {
        const [post] = await Promise.all([
          add.waitForResponse(response => response.request().method() === 'POST'
            && h.pathOnly(response.url()).endsWith('/tickler/DbTicklerAdd')),
          add.waitForEvent('dialog', { timeout: 30000 }),
          add.locator('input.btn-primary[name="Button"]').first().click(),
        ]);
        h.assert(post.status() === 400, `Oversized tickler answered HTTP ${post.status()} instead of 400`);
      }, { accept: true });
      consumeExpectedFailure(s.recorder, mark, { status: 400, path: /\/tickler\/DbTicklerAdd$/ });
      h.assert(dialogs.length === 1 && b.lengthRefusal(dialogs[0].text), 'Oversized tickler did not show one length warning');
      refusal = dialogs[0].text;
      h.assert(await add.locator('textarea[name="ticklerMessage"]').inputValue() === text, 'Refusing the tickler discarded its draft');
      h.assert(await add.locator('input.btn-primary[name="Button"]').first().isEnabled(), 'Refusing the tickler left Save disabled');
    } else {
      await add.locator('input.btn-primary[name="Button"]').first().click();
      await saved();
    }
    await add.close().catch(() => {});
    return { list, refusal };
  }

  const tickler = `${marker} ${fillCodePoints(400, parts)}`;
  await s.step('a tickler text with special characters is stored byte for byte and listed unchanged', async () => {
    const { list } = await addTickler(tickler);
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

  await s.step('message serialization preserves entities, formatting, code and links across editor mode changes', async () => {
    const inbox = await compose();
    const result = await inbox.evaluate(() => {
      const source = '<p>literal &amp;amp; &amp;copy; &amp;#169; &amp; &lt;tag&gt; 😀</p><p><strong>bold &amp;amp;</strong> <em>italic &amp;copy;</em> <a href="https://example.invalid/?a=1&amp;b=2">link &amp;amp;</a></p><p><code>code &amp;amp;</code></p><pre><code>block &amp;amp; &amp;copy;</code></pre>';
      const shape = html => { const d = new DOMParser().parseFromString(html,'text/html'); return {text:d.body.textContent, bold:d.querySelector('strong')?.textContent, italic:d.querySelector('em')?.textContent,link:d.querySelector('a')?.getAttribute('href'),codes:[...d.querySelectorAll('code')].map(c=>c.textContent)}; };
      editor.setHTML(source);
      const before=editor.getHTML(), markdown=editor.getMarkdown(), repeated=editor.getMarkdown(), unchanged=editor.getHTML();
      editor.setMarkdown(markdown);
      const after=editor.getHTML();
      editor.changeMode('markdown');editor.changeMode('wysiwyg');
      const roundTrip=editor.getHTML();
      return {before:shape(before),after:shape(after),roundTrip:shape(roundTrip),markdown,repeated,unchanged:before===unchanged};
    });
    h.assert(result.unchanged && result.markdown === result.repeated, 'Serialization mutated the live editor or escaped it repeatedly');
    h.assert(JSON.stringify(result.before) === JSON.stringify(result.after)
      && JSON.stringify(result.before) === JSON.stringify(result.roundTrip), 'Editor round trip changed literal text, formatting, code or link targets');
    h.assert(result.markdown.includes('literal &amp;amp; &amp;copy; &amp;#169; &amp;')
      && result.markdown.includes('`code &amp;`'), 'Normal text entities and code were not serialized separately');
    await leave(inbox);
  });

  await s.step('a message subject of exactly the column length and a body of special characters are stored and shown unchanged', async () => {
    const inbox = await compose();
    await send(inbox, subject, body);
    const id = await pollFor(sql, `SELECT messageid FROM messagetbl WHERE thesubject LIKE ${h.sqlString(`${marker}%`)} ORDER BY messageid DESC LIMIT 1`,
      v => /^\d+$/.test(v), 'The message was not sent');
    b.assertStored(sql, 'messagetbl', 'thesubject', `messageid=${id}`, subject, 'Message subject at the column length');
    const stored = b.readStored(sql, 'messagetbl', 'themessage', `messageid=${id}`);
    const storedBody = Buffer.from(stored.hex, 'hex').toString('utf8');
    // Every token, in the order typed: a subset would let quotes, backslash, "+" or ";" be corrupted unnoticed.
    // Markdown escapes a literal backslash and encodes ampersands in normal text.
    const markdownParts = parts.map(part => part.replace(/\\/g, '\\\\').replace(/&/g, '&amp;'));
    h.assert(inOrder(storedBody.split(`${marker} body`).slice(1).join(''), markdownParts), `The stored message body lost, altered or re-ordered a typed token (stored body: ${storedBody.slice(0, 200)})`);
    // The inbox row, then the message view the recipient reads.
    await leave(inbox);
    const box = await openInbox('messenger-inbox-2');
    const link = box.locator(`a[href*="/messenger/ViewMessage?messageID=${id}&"]`).first();
    await link.waitFor({ state: 'attached', timeout: 20000 });
    h.assert((await link.innerText()).replace(/\s+/g, ' ').trim() === subject, 'The inbox does not list the subject exactly as stored');
    await ui.clickAndAwaitReload(box, link, { required: false });
    const view = (await box.locator('body').innerText()).replace(/\s+/g, ' ');
    // The subject also contains these tokens; assert the body region separately.
    const bodyAt = view.indexOf(`${marker} body`);
    const shownBody = bodyAt < 0 ? '' : view.slice(bodyAt);
    h.assert(bodyAt >= 0 && shownBody.includes(body.replace(/\s+/g, ' ')),
      'The recipient view altered the typed message body, including literal entity text');
    await leave(box);
  });

  await s.step('an oversized subject preserves the draft and recipients, and a corrected retry sends once', async () => {
    const column = b.columnLength(sql, 'messagetbl', 'thesubject');
    const longSubject = `${marker} ${b.exactly(column + 1 - b.cpLength(marker) - 1, 'S')}`;
    const inbox = await compose();
    const before = sql.value(`SELECT COUNT(*) FROM messagetbl WHERE thesubject LIKE ${h.sqlString(`${marker}%`)}`);
    const mark = failureMark(s.recorder);
    const [post] = await Promise.all([
      inbox.waitForResponse(response => response.request().method() === 'POST'
        && h.pathOnly(response.url()).endsWith('/messenger/CreateMessage')),
      send(inbox, longSubject, 'long subject body &amp; 😀'),
    ]);
    h.assert(post.status() === 400, `Oversized subject answered HTTP ${post.status()} instead of 400`);
    consumeExpectedFailure(s.recorder, mark, { status: 400, path: /\/messenger\/CreateMessage$/ });
    h.assert(sql.value(`SELECT COUNT(*) FROM messagetbl WHERE thesubject LIKE ${h.sqlString(`${marker}%`)}`) === before,
      'An oversized subject still sent a message');
    h.assert(b.lengthRefusal(await inbox.locator('body').innerText()), 'The refusal did not explain the subject length');
    h.assert(await inbox.locator('#subject').inputValue() === longSubject, 'The refusal discarded the subject draft');
    h.assert((await inbox.locator('.toastui-editor-ww-container .ProseMirror').first().innerText()).trim() === 'long subject body &amp; 😀',
      'The refusal discarded or changed the message draft');
    h.assert(await inbox.locator(`input[name="provider"][id^="0-"][value^="${provider}-"]`).first().isChecked(),
      'The refusal discarded the selected recipient');
    for (const method of ['GET', 'HEAD']) {
      const refused = await inbox.context().request.fetch(h.appUrl(s.config.baseUrl, '/messenger/CreateMessage'), {
        method, params: { subject: `${marker} GET must not send`, message: 'must not send', provider: `${provider}-0` },
      });
      h.assert(refused.status() === 405 && refused.headers().allow === 'POST', `${method} did not refuse message creation`);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM messagetbl WHERE thesubject LIKE ${h.sqlString(`${marker}%`)}`) === before,
      'A read-method replay sent a message');
    const corrected = `${marker} corrected subject`;
    await inbox.locator('#subject').fill(corrected);
    await inbox.locator('button[type="submit"]', { hasText: /Send Message/i }).click();
    await pollFor(sql, `SELECT COUNT(*) FROM messagetbl WHERE thesubject=${h.sqlString(corrected)}`, value => value === '1',
      'The corrected draft was not sent exactly once');
    h.assert(sql.value(`SELECT COUNT(*) FROM messagetbl WHERE thesubject LIKE ${h.sqlString(`${marker}%`)}`) === String(Number(before) + 1),
      'Correcting the draft created extra messages');
    await leave(inbox);
  });

  await s.step('a tickler exactly filling the UTF-8 TEXT column is stored without losing supplementary characters', async () => {
    const prefix = `${marker} capacity `;
    const remaining = 65535 - Buffer.byteLength(prefix);
    const message = prefix + '😀'.repeat(Math.floor(remaining / 4)) + 'x'.repeat(remaining % 4);
    h.assert(Buffer.byteLength(message) === 65535, 'Test bug: wrong UTF-8 capacity fixture');
    const { list } = await addTickler(message);
    b.assertStored(sql, 'tickler', 'message', `${ownedTicklers} AND message LIKE ${h.sqlString(prefix + '%')}`,
      message, 'Tickler at UTF-8 byte capacity');
    await list.close();
  });

  await s.step('oversized tickler text is refused without truncation or losing the draft', async () => {
    const problems = [];
    const textBytes = 65535;
    const longTickler = `${marker} ${'long tickler text '.repeat(Math.ceil(textBytes / 18) + 40)}`;
    h.assert(Buffer.byteLength(longTickler) > textBytes, 'Test bug: the long tickler text is not past the column');
    const { list, refusal } = await addTickler(longTickler, { allowRefusal: true });
    await list.close().catch(() => {});
    await new Promise(resolve => setTimeout(resolve, 1500));
    const stored = sql.rows(`SELECT LENGTH(message) FROM tickler WHERE ${ownedTicklers} AND message LIKE ${h.sqlString(`${marker} long tickler%`)}`);
    if (stored.length && Number(stored[0][0]) !== Buffer.byteLength(longTickler)) {
      problems.push(`tickler text: ${Buffer.byteLength(longTickler)} bytes were typed into a box with no maxlength and the tickler was saved, but ${stored[0][0]} bytes were stored (tickler.message is TEXT, 65,535 bytes); silent truncation`);
    } else if (!stored.length && !refusal) {
      problems.push('tickler text: a very long tickler was not saved and nothing told the user');
    }
    h.assert(problems.length === 0, problems.join(' || '));
  });
}

if (require.main === module) runWorkflow('boundary-messaging-text', workflow, { openPatient: true });
module.exports = { workflow };
