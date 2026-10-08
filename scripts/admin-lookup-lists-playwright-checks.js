#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Lookup list administration workflow (coverage plan §3.7 admin-lookup-lists).
 *
 * User path: E-Chart ▸ Consultations "+" (new consultation form, Appointment
 * Instructions dropdown) as the baseline; Schedule ▸ Administration ▸ Forms/eForms ▸
 * Customize Consult Appointment Instructions (lookupListManagerAction manageSingle,
 * #myFrame) ▸ add; E-Chart ▸ Consultations "+" again; Administration ▸ System
 * Management ▸ Manage Lookup Lists (method=manage) ▸ X on the owned item; form again.
 *
 * Asserted: an owned item added to the consultApptInst list is listed with its exact
 * text and stored in LookupListItem (active, appended display order, creator, UUID
 * value); the consultation form then offers it by that value and label, last; removing
 * it from the all-lists page deactivates the same row (soft delete) and the form stops
 * offering it; a tokenless POST and a GET to the mutator change nothing.
 *
 * Fixtures: one LookupListItem added through the UI with the run marker in its label;
 * cleanup deletes only rows on that list carrying the marker and asserts they are gone.
 * LookupListManager evicts its LOOKUP_LISTS cache only on its own writes, so when a step
 * fails with an owned item still active the check first removes it through Manage Lookup
 * Lists (best effort, while the browser is open) before the SQL teardown.
 * Reordering (method=order) has no UI control and is not driven (reported).
 */
const h = require('./lib/playwright-harness');
const {clickOpensPopupOrNavigates} = require('./lib/playwright-ui');
const {revealAuditLink} = require('./lib/playwright-link-audit');
const {settleOperations} = require('./graceful-signal-cancellation');
const {runWorkflow, expectValue} = require('./lib/workflow-session');

const TIMEOUT = 20000;
const LIST_NAME = 'consultApptInst';

async function workflow(s) {
  const {sql, marker, provider} = s;
  const label = `${marker} O'Neil & "A<b>B</b>" reply`;
  const listId = sql.value(`SELECT id FROM LookupList WHERE name=${h.sqlString(LIST_NAME)} AND active=1 ORDER BY id LIMIT 1`);
  if (!/^[1-9]\d*$/.test(listId)) throw new h.SkipCheck('This install has no active consultApptInst lookup list');
  const owned = `lookupListId=${listId} AND label LIKE ${h.sqlString(marker + '%')}`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM LookupListItem WHERE ${owned}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM LookupListItem WHERE ${owned}`) === '0', 'Owned lookup list items were not removed');
  });
  const othersBefore = JSON.stringify(sql.rows(`SELECT id,value,label,displayOrder,active FROM LookupListItem
    WHERE lookupListId=${listId} AND NOT (${owned}) ORDER BY id`));
  const orderBefore = Number(sql.value(`SELECT COALESCE(MAX(displayOrder),0) FROM LookupListItem WHERE lookupListId=${listId} AND active=1`));
  const chart = await s.chart();

  // E-Chart ▸ Consultations "+" opens a fresh consultation request form.
  async function instructionOptions() {
    const form = await s.popup(chart, chart.locator('#menuTitleconsultation a').first(), 'consult-form');
    await form.waitForLoadState('domcontentloaded', {timeout: TIMEOUT});
    await h.assertNotErrorPage(form, 'the new consultation form');
    const select = form.locator('select#appointmentInstructions');
    await form.locator('#EctConsultationFormRequest2Form, form[name="EctConsultationFormRequest2Form"]').first().waitFor({timeout: TIMEOUT});
    if (await select.count() === 0) {
      await form.close();
      throw new h.SkipCheck('The consultation form shows no Appointment Instructions list (CONSULTATION_APPOINTMENT_INSTRUCTIONS_LOOKUP is off)');
    }
    const options = await select.locator('option').evaluateAll(nodes => nodes.map(o => ({value: o.value, text: o.textContent.trim()})));
    await form.close();
    return options;
  }

  let admin;
  async function adminFrame(name, ready) {
    if (!admin || admin.isClosed()) {
      ({page: admin} = await clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
        {context: s.context, recorder: s.recorder, label: 'lookup-administration', timeout: TIMEOUT}));
    }
    const link = admin.getByRole('link', {name, exact: true, includeHidden: true}).first();
    await revealAuditLink(admin, link, TIMEOUT);
    await link.click();
    const iframe = admin.locator('#dynamic-content iframe#myFrame');
    await iframe.waitFor({timeout: TIMEOUT});
    const frame = await (await iframe.elementHandle()).contentFrame();
    h.assert(frame, `${name} did not load in the administration frame`);
    await frame.locator(ready).waitFor({timeout: TIMEOUT});
    return frame;
  }
  async function managerPost(frame, action) {
    const response = admin.waitForResponse(r => r.request().method() === 'POST'
      && new URL(r.url()).pathname.endsWith('/lookupListManagerAction') && r.request().frame() === frame, {timeout: TIMEOUT});
    // Settle both so a click that fails before dispatch cannot leave the waiter to reject unhandled.
    const [, answered] = await settleOperations([Promise.resolve().then(action), response]);
    h.assert(answered.status() === 200, `The lookup list manager answered HTTP ${answered.status()}`);
    await frame.waitForFunction(() => window.jQuery && window.jQuery.active === 0, null, {timeout: TIMEOUT});
  }

  // Best-effort application-layer removal of owned items a failed run left active, through
  // the same X control (method=remove, LookupListManager.removeLookupListItem) the removal
  // step drives. Errors are only logged so the original failure is the one reported.
  async function removeActiveOwnedItems() {
    const active = sql.rows(`SELECT id FROM LookupListItem WHERE ${owned} AND active=1`).map(([id]) => id);
    if (!active.length) return;
    const frame = await adminFrame('Manage Lookup Lists', `#lookupListItems_${listId}`);
    for (const id of active) {
      h.assert(/^[1-9]\d*$/.test(id), 'Owned lookup list item id is invalid');
      await managerPost(frame, () => frame.locator(`#removeLookupListItem_${id}_${listId}`).click());
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM LookupListItem WHERE ${owned} AND active=1`) === '0', 'An owned item is still active');
  }

  let itemId;
  let value;
  try {
    await s.step('the consultation form does not offer the owned instruction before it exists', async () => {
      const options = await instructionOptions();
      h.assert(options.length > 0 && !options.some(o => o.text.includes(marker)), 'The baseline consultation form already offers the owned instruction');
    });

    await s.step('Customize Consult Appointment Instructions adds the item and stores it on that list', async () => {
      const frame = await adminFrame('Customize Consult Appointment Instructions', `#lookupListItemLabel_${listId}`);
      await frame.locator(`#lookupListItemLabel_${listId}`).fill(label);
      await managerPost(frame, () => frame.locator(`#addLookupListItemButton_${listId}`).click());
      const items = frame.locator(`#lookupListItems_${listId} li.lookupListItem .label`);
      await items.filter({hasText: marker}).first().waitFor({timeout: TIMEOUT});
      const texts = (await items.allInnerTexts()).map(t => t.trim());
      h.assert(texts.filter(t => t === label).length === 1, 'The list does not show the exact added label once');
      h.assert(await frame.locator(`#lookupListItems_${listId} b`).count() === 0, 'The list rendered the label markup as elements');
      h.assert(await frame.locator(`#lookupListItemLabel_${listId}`).inputValue() === '', 'The add field was not cleared after the save');
      await expectValue(sql, `SELECT COUNT(*) FROM LookupListItem WHERE ${owned}`, '1', 'Exactly one owned item was not stored');
      const [row] = sql.rows(`SELECT id,value,BINARY label=BINARY ${h.sqlString(label)},active,displayOrder,createdBy
        FROM LookupListItem WHERE ${owned}`);
      [itemId, value] = row;
      h.assert(/^[1-9]\d*$/.test(itemId), 'The stored item has no id');
      h.assert(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(value), 'The stored item value is not a generated UUID');
      h.assert(row.slice(2).join('|') === `1|1|${orderBefore + 1}|${provider}`,
        'The stored item label, active flag, appended display order or creator does not match the add');
    });

    await s.step('the consultation form offers the added instruction by its value and label, last', async () => {
      const options = await instructionOptions();
      const ours = options.filter(o => o.value === value);
      h.assert(ours.length === 1 && ours[0].text === label, 'The consultation form does not offer the added instruction exactly once with its label');
      h.assert(options[options.length - 1].value === value, 'The appended instruction is not offered after the existing ones');
    });

    await s.step('Manage Lookup Lists removes the item: the row is deactivated, not deleted', async () => {
      const frame = await adminFrame('Manage Lookup Lists', `#lookupListItems_${listId}`);
      const remove = frame.locator(`#removeLookupListItem_${itemId}_${listId}`);
      h.assert(await remove.count() === 1, 'The all-lists page does not offer the owned item for removal');
      await managerPost(frame, () => remove.click());
      await frame.locator(`#removeLookupListItem_${itemId}_${listId}`).waitFor({state: 'detached', timeout: TIMEOUT});
      h.assert(!(await frame.locator(`#lookupListItems_${listId}`).innerText()).includes(marker), 'The removed item is still listed');
      await expectValue(sql, `SELECT CONCAT(COUNT(*),'|',MIN(active)) FROM LookupListItem WHERE id=${itemId} AND ${owned}`, '1|0',
        'The removed item was not kept as an inactive row');
      h.assert(JSON.stringify(sql.rows(`SELECT id,value,label,displayOrder,active FROM LookupListItem
        WHERE lookupListId=${listId} AND NOT (${owned}) ORDER BY id`)) === othersBefore, 'Adding or removing the owned item changed other items on the list');
    });
  } catch (error) {
    await removeActiveOwnedItems().catch(removal => console.log(`  Best-effort removal through Manage Lookup Lists failed: ${removal.message}`));
    throw error;
  }

  await s.step('the consultation form no longer offers the removed instruction', async () => {
    const options = await instructionOptions();
    h.assert(!options.some(o => o.value === value || o.text.includes(marker)), 'The consultation form still offers the removed instruction');
  });

  const orderNow = () => sql.value(`SELECT displayOrder FROM LookupListItem WHERE id=${itemId}`);
  const probe = {method: 'order', lookupListItemId: itemId, lookupListItemDisplayOrder: String(orderBefore + 50)};
  await s.step('a POST without the CSRF token does not change the item', async () => {
    const before = orderNow();
    const response = await s.context.request.post(h.appUrl(s.config.baseUrl, '/lookupListManagerAction'), {form: probe, maxRedirects: 0});
    h.assert(response.status() !== 200 || !(await response.text()).includes('lookupListItems_'), 'A tokenless POST was answered with the manager page');
    h.assert(orderNow() === before, 'A tokenless POST changed the item display order');
  });
  await s.step('GET and HEAD to the lookup list mutator are refused and change nothing', async () => {
    const before = orderNow();
    for (const method of ['GET', 'HEAD']) {
      const response = await s.context.request.fetch(h.appUrl(s.config.baseUrl, '/lookupListManagerAction'),
        {method, params: probe, maxRedirects: 0});
      h.assert(orderNow() === before, `${method} changed the item display order`);
      h.assert(response.status() === 405, `${method} to the lookup list mutator answered HTTP ${response.status()} instead of 405`);
    }
  });
  if (admin && admin !== s.schedule && !admin.isClosed()) await admin.close();
}

if (require.main === module) runWorkflow('admin-lookup-lists', workflow, {openPatient: true});
module.exports = {workflow};
