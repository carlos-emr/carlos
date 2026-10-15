/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
/* Tickler fixture and navigation shared by the concurrency-tickler-* checks. */
const h = require('./playwright-harness');
const ui = require('./playwright-ui');

/**
 * Seed one active tickler for the owned patient and register its cleanup (tickler, comments and
 * history rows carrying the id). The row is what a clinician would create from the add popup.
 */
function seedTickler(s, tag) {
  const { sql, patient, marker, provider } = s;
  const message = `${marker} ${tag}`;
  const id = sql.value(`INSERT INTO tickler (demographic_no,program_id,message,status,update_date,service_date,creator,priority,task_assigned_to)
    VALUES (${patient},0,${h.sqlString(message)},'A',NOW(),NOW(),${h.sqlString(provider)},'Normal',${h.sqlString(provider)}); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(id), 'Tickler fixture was not created');
  s.cleanup(() => {
    sql.execute(`DELETE FROM tickler_comments WHERE tickler_no=${id}; DELETE FROM tickler_update WHERE tickler_no=${id};
      DELETE FROM tickler WHERE tickler_no=${id} AND demographic_no=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler WHERE tickler_no=${id}`) === '0', 'Tickler fixture cleanup left the row behind');
  });
  return { id, message };
}

/** Master Record > Tickler: the patient's tickler list, as a popup. */
async function openPatientTicklerList(context, master, recorder, label) {
  const page = await ui.clickOpensPopup(master, master.locator('a[onclick*="/tickler/ViewTicklerMain"]').first(),
    { context, recorder, label, timeout: 20000 });
  await page.waitForLoadState('domcontentloaded', { timeout: 20000 });
  await h.assertNotErrorPage(page, 'the patient tickler list');
  return page;
}

/** The list row carrying the tickler's message (waits for the DataTables fetch). */
async function listRow(list, message) {
  const row = list.locator('#ticklerResults tbody tr').filter({ hasText: message }).first();
  await row.waitFor({ state: 'visible', timeout: 20000 });
  return row;
}

/** List row > edit link > edit popup, with the form rendered. */
async function openTicklerEdit(context, recorder, list, message, label) {
  const row = await listRow(list, message);
  const popup = await ui.clickOpensPopup(list, row.locator('a[onclick*="openTicklerEdit"]'), { context, recorder, label, timeout: 20000 });
  await popup.waitForLoadState('domcontentloaded', { timeout: 20000 });
  await h.assertNotErrorPage(popup, 'the edit tickler popup');
  await popup.locator('form[name="serviceform"]').waitFor({ state: 'visible', timeout: 20000 });
  return popup;
}

/** Save the edit popup and wait for the server's success sentinel in the hidden submit frame. */
async function saveTicklerEdit(popup) {
  await popup.locator('input[name="updateTickler"]').click();
  await popup.waitForFunction(() => {
    const frame = document.getElementById('ticklerEditFrame');
    return Boolean(frame && frame.contentDocument && frame.contentDocument.getElementById('tickler-edit-ok'));
  }, null, { timeout: 30000 });
}

module.exports = { seedTickler, openPatientTicklerList, listRow, openTicklerEdit, saveTicklerEdit };
