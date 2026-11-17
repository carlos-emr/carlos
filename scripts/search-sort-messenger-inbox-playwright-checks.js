#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The Messenger inbox: search, paging at 25 rows a page, and the sortable headers (wave 7, search-sort).
 * User path: Schedule > Msg (messenger/DisplayMessages) > the search box > Next / Previous page > the Status, From, Subject,
 * Date and Linked headers, each clicked twice (ascending then descending).
 * Asserts, on 27 owned messages in the test provider's inbox (some read, some new, three senders, every third one linked
 * to a patient): searching the run marker lists 25 rows with a Next link, page two lists the remaining two with a
 * Previous link, and the two pages hold each message once; the Subject, From, Status and Date headers order the whole result
 * (across the page boundary) by that column and the second click reverses it; and the Linked header groups the messages that
 * are linked to a patient together instead of leaving them where they were. Defects are collected and asserted together in
 * the last step, so every provable step runs first.
 * Fixtures: the owned synthetic patient, 27 messagetbl rows with their messagelisttbl rows for the test provider and 9
 * msgDemoMap links, inserted by SQL (subjects carry the run marker); cleanup deletes them all and asserts none remain.
 * Messages are only listed and sorted here; nothing is sent, read, archived or deleted through the UI.
 * Implements the wave-7 "search-sort" pattern (inbox lists, sort by every header, pagination boundaries, count vs rows).
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const k = require('./lib/search-sort-helpers');

async function readRows(page) {
  return page.$$eval('table.table-striped tbody tr', trs => trs.filter(tr => tr.querySelector('input[name="messageNo"]')).map(tr => {
    const td = i => ((tr.children[i] || {}).textContent || '').replace(/\s+/g, ' ').trim();
    return { id: tr.querySelector('input[name="messageNo"]').value, status: td(1), from: td(2), subject: td(3), date: td(4), linked: td(5) };
  }));
}

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const q = h.sqlString;
  const tag = k.nameTag(marker);
  k.registerPatientCleanup(s, tag);
  const secondPatient = k.insertPatient(s, { last: `${tag}-Linked`, first: 'Fixture' });
  const location = sql.value('SELECT locationId FROM oscarcommlocations WHERE current1=1 LIMIT 1') || '145';
  const subjects = () => sql.rows(`SELECT messageid FROM messagetbl WHERE thesubject LIKE ${q(`${marker}%`)}`).map(r => r[0]);
  s.cleanup(() => {
    const ids = subjects();
    ids.forEach(id => h.assert(/^[1-9]\d*$/.test(id), 'Owned message id is invalid'));
    if (ids.length) {
      sql.execute(`DELETE FROM msgDemoMap WHERE messageID IN (${ids.join(',')});
        DELETE FROM messagelisttbl WHERE message IN (${ids.join(',')});
        DELETE FROM messagetbl WHERE messageid IN (${ids.join(',')})`);
    }
    h.assert(subjects().length === 0, 'Owned messages were not removed');
  });
  const words = ['pear', 'Apple', 'cherry', 'Banana', 'date', 'Elder', 'fig', 'Grape', 'honey', 'Iris', 'jam', 'Kiwi', 'lime', 'Mango', 'nut',
    'Olive', 'plum', 'Quince', 'rose', 'Sage', 'thyme', 'Umber', 'vine', 'Wheat', 'yam', 'Zest', 'amber'];
  const senders = ['Zed Sender', 'amy sender', 'Bo Sender'];
  const ids = [];
  for (let n = 0; n < 27; n += 1) {
    const i = (n * 7) % 27; // inserted out of alphabetical order so id order is not subject order
    const id = sql.value(`INSERT INTO messagetbl (thedate,theime,themessage,thesubject,sentby,sentto,sentbyNo,sentByLocation,type)
      VALUES (DATE_SUB(CURDATE(), INTERVAL ${i} DAY),'09:00:00',${q(`${marker} body`)},${q(`${marker} ${words[i]}`)},${q(senders[i % 3])},
        'Test Provider',${q(provider)},${location},0); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), 'A message fixture was not created');
    sql.execute(`INSERT INTO messagelisttbl (message,provider_no,status,remoteLocation,destinationFacilityId,sourceFacilityId) VALUES (${id},${q(provider)},'${i % 4 === 0 ? 'read' : 'new'}',${location},0,0)`);
    if (i % 3 === 0) sql.execute(`INSERT INTO msgDemoMap (messageID, demographic_no) VALUES (${id}, ${patient})`);
    // A message linked to two patients still occupies one inbox row and one paging slot.
    if (i === 0) sql.execute(`INSERT INTO msgDemoMap (messageID, demographic_no) VALUES (${id}, ${secondPatient})`);
    ids.push({ id, subject: `${marker} ${words[i]}` });
  }
  const defects = [];

  const opened = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('a:has(#oscar_new_msg)').first(),
    { context: s.context, recorder: s.recorder, label: 'messenger', timeout: 30000 });
  const inbox = opened.page;
  h.assert(h.pathOnly(inbox.url()).includes('/messenger/'), 'The schedule Msg link did not open the messenger');
  const owned = rows => rows.filter(r => ids.some(m => m.id === r.id));

  await s.step('searching the run marker lists 25 owned messages with a Next link; page two lists the other two', async () => {
    await inbox.locator('input[name="searchString"]').fill(marker);
    await ui.clickAndAwaitReload(inbox, inbox.locator('button[name="btnSearch"]'), { timeout: 30000, label: 'messenger search' });
    const page1 = await readRows(inbox);
    h.assert(page1.length === 25 && owned(page1).length === 25, `Page one listed ${page1.length} rows (${owned(page1).length} owned), not 25`);
    const next = inbox.locator('a[title="next page"]').first();
    h.assert(await next.count() === 1, 'Page one offers no Next link');
    h.assert(await inbox.locator('a[title="previous page"]').count() === 0, 'Page one offers a Previous link');
    await ui.clickAndAwaitReload(inbox, next, { timeout: 30000, label: 'messenger next page' });
    const page2 = await readRows(inbox);
    h.assert(page2.length === 2 && owned(page2).length === 2, `Page two listed ${page2.length} rows, not the remaining 2`);
    h.assert(new Set([...page1, ...page2].map(r => r.id)).size === 27, 'The two pages do not hold the 27 messages once each');
    h.assert(await inbox.locator('a[title="previous page"]').count() >= 1, 'Page two offers no Previous link');
    h.assert(await inbox.locator('a[title="next page"]').count() === 0, 'The last page still offers a Next link');
  });

  /** Read both pages of the current ordering (page one is where a header click lands). */
  const bothPages = async () => {
    const first = await readRows(inbox);
    const next = inbox.locator('a[title="next page"]').first();
    h.assert(await next.count() === 1, 'A sorted page one offers no Next link');
    await ui.clickAndAwaitReload(inbox, next, { timeout: 30000, label: 'messenger next page' });
    return [...first, ...await readRows(inbox)];
  };
  const textCmp = (a, b) => k.collator.compare(a, b);
  // The inbox shows dates as dd-MM-yyyy; order them as dates, not as text.
  const dateKey = v => v.replace(/^(\d{2})-(\d{2})-(\d{4})$/, '$3-$2-$1');
  const dateCmp = (a, b) => (dateKey(a) < dateKey(b) ? -1 : dateKey(a) > dateKey(b) ? 1 : 0);
  const columns = [['Subject', 'subject', 'subject', textCmp], ['From', 'from', 'from', textCmp], ['Status', 'status', 'status', textCmp],
    ['Date', 'date', 'date', dateCmp]];

  await s.step('the Subject, From, Status and Date headers order all 27 messages across the page boundary, and a second click reverses it', async () => {
    for (const [label, param, field, cmp] of columns) {
      for (const dir of ['ascending', 'descending']) {
        await ui.clickAndAwaitReload(inbox, inbox.locator(`a[href*="orderby=${param}"]`).first(), { timeout: 30000, label: `messenger sort by ${label}` });
        const rows = await bothPages();
        if (owned(rows).length !== 27) { defects.push(`${label} ${dir}: lists ${owned(rows).length} of the 27 messages`); continue; }
        const values = rows.map(r => r[field]);
        const ordered = values.every((v, i) => i === 0 || (dir === 'ascending' ? cmp(values[i - 1], v) <= 0 : cmp(values[i - 1], v) >= 0));
        if (!ordered) defects.push(`${label} ${dir}: not in order (${values.slice(0, 6).map(v => v.replace(marker, '').trim()).join(' | ')} ...)`);
      }
    }
  });

  await s.step('the Linked header, clicked twice, groups the messages linked to a patient together both ways', async () => {
    for (const dir of ['first click', 'second click']) {
      await ui.clickAndAwaitReload(inbox, inbox.locator('a[href*="orderby=linked"]').first(), { timeout: 30000, label: 'messenger sort by Linked' });
      const rows = await bothPages();
      const linked = rows.filter(r => r.linked !== '').length;
      // Zero transitions would also be the answer for 27 empty Linked cells, so the shape of the listing is proven first.
      if (rows.length !== 27 || linked !== 9) {
        defects.push(`Linked ${dir}: lists ${rows.length} rows with ${linked} linked cells, not 27 with 9 (the grouping cannot be judged)`);
        continue;
      }
      const flags = rows.map(r => (r.linked !== '' ? 'L' : 'u'));
      const changes = flags.filter((f, i) => i > 0 && f !== flags[i - 1]).length;
      if (changes > 1) defects.push(`Linked ${dir}: the 9 linked and 18 unlinked messages are not grouped (${flags.join('')})`);
    }
  });

  await s.step('legacy rows with NULL facility IDs still render in the inbox', async () => {
    const before = await readRows(inbox);
    const legacyId = before[0].id;
    h.assert(ids.some(row => row.id === legacyId), 'The nullable-facility fixture must be an owned message');
    sql.execute(`UPDATE messagelisttbl SET destinationFacilityId=NULL,sourceFacilityId=NULL
      WHERE message=${legacyId} AND provider_no=${q(provider)}`);
    const {failed, pageErrors, thrown} = await k.collectHttpFailures(s, async () => {
      await inbox.reload({waitUntil: 'networkidle'});
    });
    if (failed.length || pageErrors.length || thrown) {
      defects.push('a legacy message with NULL facility IDs makes the inbox fail to render');
    } else {
      const after = await readRows(inbox);
      h.assert(after.length === before.length && after.some(row => row.id === legacyId),
        'The legacy NULL-facility message disappeared from the inbox');
    }
  });

  await s.step('every messenger sort and page listed the owned messages correctly', async () => {
    h.assert(defects.length === 0, `Messenger inbox defects: ${defects.join('; ')} `
      + '(MsgDisplayMessagesBean.java:524 sorts the inbox by "m.messageid is null", which is never true, so the Linked header cannot group linked messages)');
  });
}

if (require.main === module) runWorkflow('search-sort-messenger-inbox', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
