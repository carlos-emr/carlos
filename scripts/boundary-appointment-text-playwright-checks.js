#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Special characters and field lengths on the appointment booking and edit popups (wave 6, boundary values).
 * User path (throwaway login, its own day sheet): Schedule > empty slot > Add Appointment popup > patient
 * search > reason / notes / resources > Add Appointment > the appointment's own link (edit popup) > Update.
 * Asserts: a reason and notes that fill their boxes exactly (80 and 255 UTF-16 units) and carry an
 * apostrophe, accents, CJK, an emoji, "&amp;", quotes, backslash, "%41", "+" and ";" are stored byte for
 * byte, redisplayed identically by the edit popup, and an unchanged Update leaves every column as it was.
 * Last (the defects): a reason and notes that fill their box with a line break inside are stored whole
 * (the browser counts a break as one character, submits two); a free-text appointment name and a resources
 * entry past their columns are refused or visibly limited, never silently cut.
 * Fixtures: the throwaway login (lib/throwaway-login-fixture.js) and the owned FAKE- patient; cleanup deletes
 * the owned appointment and archive rows, then the throwaway, and asserts they are gone.
 * Implements the wave-6 "boundary values" pattern, Part 1 (appointment name / reason / notes).
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const b = require('./lib/boundary-values');
const { runWorkflow } = require('./lib/workflow-session');
const { createUnbookedThrowaway, registerAppointmentCleanup } = require('./lib/gap-provider-fixture');

/** A string whose UTF-16 length is exactly `units` built from the special-character parts, padded with 'x'. */
function fillUnits(units, parts, pad = 'x') {
  let value = '';
  for (const part of parts) {
    if ((value + (value ? ' ' : '') + part).length <= units) value += (value ? ' ' : '') + part;
  }
  return value + pad.repeat(units - value.length);
}

async function pollFor(sql, query, predicate, message) {
  const deadline = Date.now() + 15000;
  let value;
  do {
    value = sql.value(query);
    if (predicate(value)) return value;
    await new Promise(resolve => setTimeout(resolve, 200));
  } while (Date.now() < deadline);
  h.assert(false, message);
  return value;
}

async function workflow(s) {
  const { sql, config, recorder, marker, patient } = s;
  // A throwaway login whose provider number owns no appointment row, so the by-number cleanup removes only this run's rows.
  const fixture = createUnbookedThrowaway(s);
  registerAppointmentCleanup(s, fixture);
  const owner = h.sqlString(fixture.providerNo);
  const context = await h.newContext(s.context.browser(), config);
  context.setDefaultTimeout(20000);
  context.on('page', page => h.wireStrictPage(page, 'throwaway', recorder));
  const schedule = await h.login(context, { ...config, testUser: fixture.username }, recorder, { label: 'throwaway-login' });
  const T = b.TOKENS;
  const parts = [T.apostrophe, T.latin, T.cjk, T.emoji, T.entity, T.quotes, T.backslash, T.percent, T.plus, T.semicolon];
  const reasonColumn = b.columnLength(sql, 'appointment', 'reason');
  const notesColumn = b.columnLength(sql, 'appointment', 'notes');
  const reason = fillUnits(reasonColumn, parts);
  const notes = fillUnits(notesColumn, parts);
  const resources = fillUnits(60, parts);
  const field = (popup, name) => popup.locator(`form#addappt [name="${name}"]`);
  const byApptNo = () => `provider_no=${owner} AND appointment_no=`;
  let slotIndex = 4;

  async function openBooking() {
    const slots = schedule.locator(`a.adhour[onclick*="provider_no=${fixture.providerNo}&"]`);
    const popup = await ui.clickOpensPopup(schedule, slots.nth(slotIndex), { context, recorder, label: 'add-appointment', timeout: 20000 });
    slotIndex += 6;
    await popup.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    return popup;
  }
  async function pickPatient(popup) {
    await popup.locator('#keyword').fill(marker);
    await ui.clickAndAwaitReload(popup, popup.locator('#searchBtn'), { required: false });
    await popup.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    const pick = popup.locator(`table tr input[type="button"][name="pick_demographic"][value="${patient}"]`).first();
    await pick.waitFor({ state: 'visible' });
    await ui.clickAndAwaitReload(popup, pick, { required: false });
    await popup.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    h.assert(await popup.locator('#demographic_no').inputValue() === patient, 'The booking form did not take the owned patient');
  }
  async function openEdit(apptNo) {
    const link = schedule.locator(`a[onclick*="/appointment/editappointment?appointment_no=${apptNo}"]`).first();
    await link.waitFor({ state: 'attached', timeout: 20000 });
    const edit = await ui.clickOpensPopup(schedule, link, { context, recorder, label: 'edit-appointment', timeout: 20000 });
    await edit.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    return edit;
  }
  async function reloadDaySheet() {
    // The popup's save refreshes the opener on its own; a second reload races that navigation.
    await new Promise(resolve => setTimeout(resolve, 1500));
    await schedule.waitForLoadState('load', { timeout: 20000 }).catch(() => {});
    try { await schedule.reload({ waitUntil: 'domcontentloaded' }); } catch (error) { await schedule.waitForLoadState('load', { timeout: 20000 }); }
  }

  let apptNo;
  await s.step('booking with a full-length reason, notes and resources of special characters stores them byte for byte', async () => {
    const popup = await openBooking();
    await pickPatient(popup);
    await field(popup, 'reason').fill(reason);
    await field(popup, 'notes').fill(notes);
    await field(popup, 'resources').fill(resources);
    h.assert(await field(popup, 'reason').inputValue() === reason && await field(popup, 'notes').inputValue() === notes,
      'The reason or notes box refused text that exactly fills its maxlength');
    await ui.clickAndAwaitReload(popup, popup.locator('#addButton'), { required: false });
    apptNo = await pollFor(sql, `SELECT appointment_no FROM appointment WHERE provider_no=${owner}`, v => /^\d+$/.test(v), 'The booking was not saved');
    for (const [column, value] of [['reason', reason], ['notes', notes], ['resources', resources]]) {
      b.assertStored(sql, 'appointment', column, `${byApptNo()}${apptNo}`, value, `Booked appointment ${column}`);
    }
    await popup.close().catch(() => {});
    await reloadDaySheet();
  });

  await s.step('the edit popup redisplays the values identically and an unchanged Update changes no column', async () => {
    const edit = await openEdit(apptNo);
    const wrong = [];
    for (const [name, value] of [['reason', reason], ['notes', notes], ['resources', resources]]) {
      if (await edit.locator(`form [name="${name}"]`).first().inputValue() !== value) wrong.push(name);
    }
    h.assert(wrong.length === 0, `The edit popup does not redisplay what was stored: ${wrong.join(', ')}`);
    const snapshot = () => sql.rows(`SELECT HEX(name), HEX(reason), HEX(notes), HEX(resources) FROM appointment WHERE ${byApptNo()}${apptNo}`)[0].join('|');
    const before = snapshot();
    await edit.locator('#updateButton').click();
    await edit.waitForEvent('close', { timeout: 15000 }).catch(() => {});
    await pollFor(sql, `SELECT COUNT(*) FROM appointmentArchive WHERE appointment_no=${apptNo}`, v => Number(v) >= 1, 'The unchanged Update was not recorded');
    h.assert(snapshot() === before, 'An Update with no edits altered the stored name, reason, notes or resources (re-encoding on save)');
    await reloadDaySheet();
  });

  await s.step('line breaks inside a full box, and a long free-text name and resources entry, are stored whole or refused', async () => {
    const problems = [];
    // (a) a break counts one character in the box (maxlength) but travels as CR LF, so a box filled to the limit overflows the column.
    // The box counts UTF-16 units but the column counts code points, so an emoji (two units, one code point) would give the
    // submitted CR LF text one code point of slack and hide the overflow: leave it out of these probes.
    const bmpParts = parts.filter(part => part !== T.emoji);
    const breakReason = `${fillUnits(reasonColumn - 2, bmpParts)}\nZ`;
    const breakNotes = `${fillUnits(notesColumn - 2, bmpParts)}\nZ`;
    const edit = await openEdit(apptNo);
    await edit.locator('form [name="reason"]').first().fill(breakReason);
    await edit.locator('form [name="notes"]').first().fill(breakNotes);
    const accepted = [await edit.locator('form [name="reason"]').first().inputValue(), await edit.locator('form [name="notes"]').first().inputValue()];
    await edit.locator('#updateButton').click();
    await edit.waitForEvent('close', { timeout: 15000 }).catch(() => {});
    await new Promise(resolve => setTimeout(resolve, 1500));
    const lf = value => String(value).replace(/\r\n/g, '\n');
    for (const [column, typed, shown] of [['reason', breakReason, accepted[0]], ['notes', breakNotes, accepted[1]]]) {
      const stored = b.readStored(sql, 'appointment', column, `${byApptNo()}${apptNo}`);
      const text = Buffer.from(stored.hex, 'hex').toString('utf8');
      if (lf(text) !== lf(shown)) problems.push(`${column}: a box filled to its limit with a line break inside (${b.cpLength(shown)} characters) was stored as ${b.cpLength(text)} (the break is submitted as CR LF, one more than the box counted); the last character is lost`);
      if (lf(shown) !== lf(typed)) problems.push(`${column}: the box silently dropped typed text`);
    }
    // (b) a free-text appointment name and a resources entry past their columns.
    await reloadDaySheet();
    const nameColumn = b.columnLength(sql, 'appointment', 'name');
    const resourcesColumn = b.columnLength(sql, 'appointment', 'resources');
    const longName = b.exactly(nameColumn + 1, 'N' + marker.slice(-6));
    const longResources = b.exactly(resourcesColumn + 1, 'R');
    // Each field is probed in a booking of its own, so a refusal of one cannot mask what happens to the other.
    // The outcome must be visible either way: the box limits the text, the booking stores it whole, or the
    // booking is refused with a length-specific message (alert or page). No row and no message is a defect too.
    const probeOverlong = async (column, selector, typed, otherName) => {
      const popup = await openBooking();
      if (otherName) await popup.locator('#keyword').fill(otherName);
      await popup.locator(selector).fill(typed);
      const shown = await popup.locator(selector).inputValue();
      if (b.cpLength(shown) < b.cpLength(typed)) { await popup.close().catch(() => {}); return; } // visibly limited by the box
      const highWater = Number(sql.value(`SELECT COALESCE(MAX(appointment_no),0) FROM appointment WHERE provider_no=${owner}`));
      let pageText = '';
      const dialogs = await h.withExpectedDialogs(popup, async () => {
        await ui.clickAndAwaitReload(popup, popup.locator('#addButton'), { required: false });
        await new Promise(resolve => setTimeout(resolve, 1500));
        if (!popup.isClosed()) pageText = await popup.locator('body').innerText().catch(() => '');
      });
      const row = sql.value(`SELECT appointment_no FROM appointment WHERE provider_no=${owner} AND appointment_no>${highWater} ORDER BY appointment_no DESC LIMIT 1`);
      if (/^\d+$/.test(row)) {
        const stored = b.readStored(sql, 'appointment', column, `${byApptNo()}${row}`);
        if (stored.hex !== b.hex(shown)) problems.push(`${column}: ${b.cpLength(shown)} characters were typed into a box with no maxlength and the booking was accepted, but ${stored.chars} were stored (column ${b.columnLength(sql, 'appointment', column)}); silent truncation`);
      } else if (!b.lengthRefusal([...dialogs.map(entry => entry.text), pageText].join('\n'))) {
        problems.push(`${column}: a ${b.cpLength(shown)}-character entry past its column was not saved, but no length refusal was shown (no alert or page message naming the limit)`);
      }
      await popup.close().catch(() => {});
      await reloadDaySheet();
    };
    await probeOverlong('name', '#keyword', longName, null);
    await probeOverlong('resources', 'form#addappt [name="resources"]', longResources, `N${marker.slice(-6)}`);
    h.assert(problems.length === 0, problems.join(' || '));
  });
}

if (require.main === module) runWorkflow('boundary-appointment-text', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
