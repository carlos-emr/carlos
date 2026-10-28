#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The edit popup's "No show" and "Cancel Appt" buttons. Both post the form to appointment/UpdateRecord
 * with buttoncancel set, a branch of AppointmentUpdateRecord2Action that changes only the status.
 * appointment-lifecycle cancels through the status select instead, so no check pressed either button.
 *
 * User path (throwaway login, its own day sheet): Schedule ▸ the appointment's own link ▸ edit popup
 * (appointment/editappointment) ▸ No show; another appointment ▸ Cancel Appt; a billed appointment ▸
 * Cancel Appt ▸ the "has been Billed" confirmation, dismissed and then accepted.
 * Asserted: No show stores status N and Cancel Appt status C, with the throwaway as lastupdateuser and
 * every other field as it was; each writes one appointmentArchive row holding the status before; the
 * popup closes and the day sheet reloads, greys the cell (class Cancelled) and drops the appointment
 * from the provider's count. Dismissing the billed confirmation sends nothing and leaves the popup open.
 * Fixtures: the throwaway login (lib/gap-provider-fixture.js, a number owning no appointment rows), the
 * owned FAKE- patient and three appointments for today seeded by SQL. Cleanup deletes the appointment
 * and archive rows by the throwaway's number, then the throwaway, and asserts they are gone.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { createUnbookedThrowaway, registerAppointmentCleanup } = require('./lib/gap-provider-fixture');

// Every column the full-update branch rewrites from the form, which the buttoncancel branch must leave alone.
const KEPT = ['appointment_date', 'start_time', 'end_time', 'name', 'demographic_no', 'reason', 'notes', 'location', 'resources',
  'type', 'style', 'billing', 'remarks', 'urgency'];

async function workflow(s) {
  const { sql, config, recorder, marker, patient } = s;
  const fixture = createUnbookedThrowaway(s);
  registerAppointmentCleanup(s, fixture);
  const owner = h.sqlString(fixture.providerNo);
  // The schedule opens on the server's today, which can differ from the runner's clock around midnight; the database date
  // is the one the other checks seed against (CURDATE()).
  const dateKey = sql.value("SELECT DATE_FORMAT(CURDATE(),'%Y-%m-%d')");
  const name = `${marker},Workflow`;
  // Non-empty values for every kept column, so "unchanged" cannot hold by accident; style and billing are VARCHAR(10).
  const marked = h.sqlString(marker);
  const short = h.sqlString(`p${marker.slice(-8)}`);
  const seeded = {};
  for (const [label, start, status] of [['noShow', '09:00:00', 't'], ['cancel', '10:00:00', 't'], ['billed', '11:00:00', 'B']]) {
    seeded[label] = sql.value(`INSERT INTO appointment (provider_no,appointment_date,start_time,end_time,name,demographic_no,reason,notes,
        location,resources,type,style,billing,remarks,urgency,status,createdatetime,updatedatetime,creator,lastupdateuser)
      VALUES (${owner},${h.sqlString(dateKey)},${h.sqlString(start)},${h.sqlString(start.replace(':00:00', ':14:00'))},${h.sqlString(name)},${patient},
        ${h.sqlString(`${marker} ${label}`)},${h.sqlString(`${marker} notes`)},${marked},${marked},${marked},${short},${short},${marked},${marked},
        ${h.sqlString(status)},NOW(),NOW(),${h.sqlString(s.provider)},${h.sqlString(s.provider)});
      SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(seeded[label]), `The ${label} appointment was not seeded`);
  }
  const kept = id => JSON.stringify(sql.rows(`SELECT ${KEPT.join(',')} FROM appointment WHERE appointment_no=${id}`));
  const before = Object.fromEntries(Object.entries(seeded).map(([label, id]) => [label, kept(id)]));

  const context = await h.newContext(s.context.browser(), config);
  context.setDefaultTimeout(20000);
  context.on('page', page => h.wireStrictPage(page, 'throwaway', recorder));
  const schedule = await h.login(context, { ...config, testUser: fixture.username }, recorder, { label: 'throwaway-login' });
  const link = id => schedule.locator(`a[onclick*="/appointment/editappointment?appointment_no=${id}&"]`).first();
  const cell = id => schedule.locator('td.appt').filter({ has: link(id) });
  // The zoom-view link heads the provider's column as "<name> (<count>)", counting booked, not cancelled, patients.
  const count = async () => {
    const text = await schedule.locator(`a[onclick^="goZoomView('${fixture.providerNo}'"]`).first().innerText();
    const match = /\((\d+)\)\s*$/.exec(text.trim());
    h.assert(match, `The day sheet's provider heading shows no appointment count: "${text}"`);
    return Number(match[1]);
  };
  const openEdit = async (id, label) => {
    await link(id).waitFor({ state: 'attached' });
    const edit = await ui.clickOpensPopup(schedule, link(id), { context, recorder, label, timeout: 20000 });
    await edit.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    return edit;
  };
  // The result page calls opener.refresh() (a reload of the day sheet) and closes itself.
  const pressAndAwaitClose = async (edit, button, body = () => button.click({ noWaitAfter: true })) => {
    const reloaded = schedule.waitForEvent('framenavigated', { predicate: frame => frame === schedule.mainFrame(), timeout: 20000 })
      .then(() => true, () => false);
    const closed = edit.waitForEvent('close', { timeout: 20000 }).then(() => true, () => false);
    // Chromium can report the popup's own window.close() before it acknowledges the click.
    await body().catch(async error => { if (!await closed) throw error; });
    h.assert(await closed, 'The edit popup did not close after the save');
    h.assert(await reloaded, 'The day sheet did not reload after the popup saved');
    await schedule.waitForLoadState('domcontentloaded');
  };
  const archived = id => sql.value(`SELECT COALESCE(GROUP_CONCAT(status ORDER BY id), '') FROM appointmentArchive WHERE appointment_no=${id}`);

  await s.step('the day sheet lists the three appointments and counts them', async () => {
    for (const id of Object.values(seeded)) {
      await link(id).waitFor({ state: 'attached' });
      h.assert(!/\bCancelled\b/.test(await cell(id).getAttribute('class')), `Appointment ${id} is greyed before any change`);
    }
    h.assert(await count() === 3, `The provider heading counts ${await count()} appointments, not 3`);
  });

  for (const [label, buttonId, status, remaining] of [['noShow', '#noShowButton', 'N', 2], ['cancel', '#cancelButton', 'C', 1]]) {
    const id = seeded[label];
    await s.step(`${buttonId === '#noShowButton' ? 'No show' : 'Cancel Appt'} stores status ${status}, archives the booking and leaves the rest`, async () => {
      const edit = await openEdit(id, `edit-${label}`);
      await pressAndAwaitClose(edit, edit.locator(buttonId));
      await expectValue(sql, `SELECT status FROM appointment WHERE appointment_no=${id}`, status, `The appointment status is not ${status}`);
      h.assert(sql.value(`SELECT lastupdateuser FROM appointment WHERE appointment_no=${id}`) === fixture.providerNo,
        'The change is not recorded as made by the logged-in provider');
      h.assert(kept(id) === before[label], 'The button changed more than the status');
      h.assert(archived(id) === 't', `The archive holds "${archived(id)}", not the one booking as it was (t)`);
    });

    await s.step(`the reloaded day sheet greys the ${status} appointment and counts ${remaining}`, async () => {
      await link(id).waitFor({ state: 'attached' });
      h.assert(/\bCancelled\b/.test(await cell(id).getAttribute('class')), `The ${status} appointment is not greyed on the day sheet`);
      h.assert(await count() === remaining, `The provider heading counts ${await count()} appointments, not ${remaining}`);
    });
  }

  await s.step('Cancel Appt on a billed appointment asks first; dismissing sends nothing', async () => {
    const id = seeded.billed;
    const edit = await openEdit(id, 'edit-billed');
    const posts = [];
    edit.on('request', request => { if (request.method() === 'POST') posts.push(request.url()); });
    const dialogs = await h.withExpectedDialogs(edit, () => edit.locator('#cancelButton').click(), { accept: false });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm' && /billed/i.test(dialogs[0].text),
      `Cancelling a billed appointment did not ask about the bill: ${JSON.stringify(dialogs)}`);
    await edit.waitForTimeout(500);
    h.assert(!edit.isClosed() && posts.length === 0, 'Dismissing the confirmation still sent the form');
    h.assert(sql.value(`SELECT status FROM appointment WHERE appointment_no=${id}`) === 'B' && archived(id) === '',
      'Dismissing the confirmation changed the billed appointment');

    await pressAndAwaitClose(edit, edit.locator('#cancelButton'), () => h.withExpectedDialogs(edit,
      () => edit.locator('#cancelButton').click({ noWaitAfter: true }), { accept: true }));
    await expectValue(sql, `SELECT status FROM appointment WHERE appointment_no=${id}`, 'C', 'Accepting did not cancel the billed appointment');
    h.assert(kept(id) === before.billed, 'Cancelling the billed appointment changed more than the status');
    h.assert(archived(id) === 'B', `The archive holds "${archived(id)}", not the billed booking (B)`);
    await link(id).waitFor({ state: 'attached' });
    h.assert(/\bCancelled\b/.test(await cell(id).getAttribute('class')) && await count() === 0,
      'The day sheet does not grey the cancelled billed appointment and count none');
  });
}

if (require.main === module) runWorkflow('appointment-cancel-no-show', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
