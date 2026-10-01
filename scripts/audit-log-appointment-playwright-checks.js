#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Audit trail of the appointment lifecycle for an owned patient (wave 7 sweep `audit-log`).
 *
 * User path: Schedule day sheet (a far-future day) > an empty slot > Add Appointment popup > patient keyword
 * search > pick the patient > reason > Add Appointment; the appointment link > Edit popup > change the reason
 * > Update; the status letter on the day sheet; the Edit popup > status Cancelled > Update; the Edit popup >
 * Delete (confirm).
 *
 * Asserts the `appointment` rows after each step (the booking, the edit, the status change, the
 * cancellation, the removal), and, for each of the five writes, that the `log` table gained at least one
 * row that names the appointment (its id as contentId or in data) and is an appointment write, carrying
 * the provider and the client address; and that the rows carry demographic_no = the patient (the delete
 * row names only the appointment id, so the patient it concerned cannot be found from the audit trail
 * once the appointment row is gone). Every expectation is evaluated and the violated ones are reported
 * together in the last step so each write is judged.
 *
 * Fixtures: the harness's owned synthetic patient; one appointment booked on a random day 500-900 days
 * ahead, its reason carrying the run marker. Cleanup deletes the appointment, appointmentArchive and
 * other_id rows carrying the marker and the audit rows scoped to the appointment id or the patient,
 * and asserts them gone.
 */
const { randomInt } = require('node:crypto');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { auditProbe } = require('./lib/audit-log-helpers');

async function workflow(s) {
  const { sql, marker, patient, provider, config, schedule } = s;
  const q = h.sqlString;
  const probe = auditProbe({ sql, patient });
  const defects = [];
  const expect = (ok, message) => { if (!ok) defects.push(message); };
  const reason = `${marker} reason`;
  const apptWhere = `reason LIKE ${q(`${marker}%`)}`;
  let apptNo = null;
  s.cleanup(() => {
    sql.execute(`DELETE FROM appointmentArchive WHERE ${apptWhere}`);
    if (apptNo) sql.execute(`DELETE FROM other_id WHERE table_name=2 AND table_id=${q(apptNo)}`);
    sql.execute(`DELETE FROM appointment WHERE ${apptWhere}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE ${apptWhere}`) === '0'
      && sql.value(`SELECT COUNT(*) FROM appointmentArchive WHERE ${apptWhere}`) === '0', 'Owned appointment rows were not removed');
    if (apptNo) sql.execute(`DELETE FROM log WHERE (action LIKE '%ppointment%' OR content LIKE '%ppointment%') AND data REGEXP ${q(`(^|[^0-9])${apptNo}([^0-9]|$)`)}`);
    probe.cleanup();
  });
  const appointment = () => sql.rows(`SELECT appointment_no,status,reason,demographic_no,provider_no FROM appointment WHERE ${apptWhere}`)
    .map(([id, status, text, demographic, owner]) => ({ id, status, text, demographic, owner }))[0];
  async function waitAppointment(accept, description) {
    const deadline = Date.now() + 20000;
    while (Date.now() < deadline) {
      const row = appointment();
      if (row && accept(row)) return row;
      await new Promise(resolve => setTimeout(resolve, 250));
    }
    h.assert(false, `The appointment row did not reach: ${description}`);
  }
  // Audit rows about THIS appointment written after `since`: an appointment action/content naming the id, or any row naming it as contentId.
  const idPattern = () => `(^|[^0-9])${apptNo}([^0-9]|$)`;
  const writesSince = since => (apptNo ? sql.rows(`SELECT id,COALESCE(provider_no,'~NULL~'),action,COALESCE(content,'~NULL~'),COALESCE(contentId,'~NULL~'),COALESCE(ip,'~NULL~'),COALESCE(CAST(demographic_no AS CHAR),'~NULL~')
      FROM log WHERE id>${Number(since)} AND (action LIKE '%ppointment%' OR content LIKE '%ppointment%')
      AND (contentId=${q(String(apptNo))} OR data REGEXP ${q(idPattern())}) AND action NOT LIKE '%get%' AND action NOT LIKE '%find%' ORDER BY id`)
    .map(([id, who, action, content, contentId, ip, demographic]) => ({ id, who, action, content, contentId, ip, demographic })) : []);
  async function judge(what, since) {
    await probe.settle(2500);
    const rows = writesSince(since);
    expect(rows.length >= 1, `${what} wrote no audit row naming the appointment`);
    for (const r of rows) {
      expect(r.who === provider, `${what}: the audit row ${r.action} does not carry the provider`);
      expect(r.ip !== '~NULL~', `${what}: the audit row ${r.action} carries no client address`);
      expect(r.demographic === String(patient), `${what}: the audit row ${r.action} carries no demographic_no, so it cannot be found from the patient`);
    }
  }

  // A weekday far ahead that offers a slot in the provider's column.
  let slots;
  async function openDay() {
    await h.gotoApp(schedule, config.baseUrl, `/provider/providercontrol?${new URLSearchParams(s.day)}`);
    await schedule.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
    slots = schedule.locator(`a.adhour[onclick*="provider_no=${provider}&"]`);
  }
  for (let attempt = 0; attempt < 6; attempt++) {
    const when = new Date(Date.now() + randomInt(500, 900) * 86400000);
    s.day = { year: String(when.getUTCFullYear()), month: String(when.getUTCMonth() + 1), day: String(when.getUTCDate()), view: '0',
      displaymode: 'day', dboperation: 'searchappointmentday', viewall: '1' };
    await openDay();
    if (await slots.count() > 0) break;
  }
  h.assert(await slots.count() > 0, 'No far-future day offered a bookable slot in the provider column');

  await s.step('the Add Appointment popup books the owned patient on a free slot (audit rows observed)', async () => {
    const before = probe.mark();
    const popup = await ui.clickOpensPopup(schedule, slots.first(), { context: s.context, recorder: s.recorder, label: 'audit-appointment-add', timeout: 30000 });
    await popup.waitForLoadState('domcontentloaded');
    await popup.locator('#keyword').fill(marker);
    await ui.clickAndAwaitReload(popup, popup.locator('#searchBtn'), { timeout: 30000, label: 'patient search' });
    const row = popup.locator(`table tr input[type="button"][name="pick_demographic"][value="${patient}"]`).first();
    await row.waitFor({ state: 'visible', timeout: 30000 });
    await ui.clickAndAwaitReload(popup, row, { timeout: 30000, label: 'pick patient' });
    h.assert(await popup.locator('#demographic_no').inputValue() === String(patient), 'The booking form did not take the owned patient');
    await popup.locator('#reason').fill(reason);
    const closed = popup.waitForEvent('close', { timeout: 30000 }).then(() => true, () => false);
    await popup.locator('#addButton').click();
    await closed;
    const booked = await waitAppointment(row2 => row2.demographic === String(patient) && row2.owner === provider, 'booked for the patient');
    apptNo = booked.id;
    probe.own('appointment', apptNo);
    await judge('Adding the appointment', before);
  });

  async function openEdit() {
    await openDay();
    const link = schedule.locator(`a.apptLink[onclick*="appointment_no=${apptNo}"]`).first();
    await link.waitFor({ state: 'visible', timeout: 30000 });
    const popup = await ui.clickOpensPopup(schedule, link, { context: s.context, recorder: s.recorder, label: 'audit-appointment-edit', timeout: 30000 });
    await popup.waitForLoadState('domcontentloaded');
    h.assert(await popup.locator('input[name="appointment_no"]').inputValue() === String(apptNo), 'The edit popup opened another appointment');
    return popup;
  }

  await s.step('the Edit popup saves a changed reason (audit rows observed)', async () => {
    const before = probe.mark();
    const popup = await openEdit();
    await popup.locator('#reason').fill(`${reason} edited`);
    const closed = popup.waitForEvent('close', { timeout: 30000 }).then(() => true, () => false);
    await popup.locator('#updateButton').click();
    await closed;
    await waitAppointment(row => row.text === `${reason} edited`, 'the edited reason');
    await judge('Editing the appointment', before);
  });

  await s.step('the status letter on the day sheet advances the status (audit rows observed)', async () => {
    const before = probe.mark();
    const first = appointment().status;
    await openDay();
    const letter = schedule.locator(`a.apptStatus[onclick*="appointment_no=${apptNo}"]`).first();
    await letter.waitFor({ state: 'visible', timeout: 30000 });
    await ui.clickAndAwaitReload(schedule, letter, { timeout: 30000, label: 'status letter' });
    await waitAppointment(row => row.status !== first, 'a new status');
    await judge('Advancing the status from the day sheet', before);
  });

  await s.step('the Edit popup cancels the appointment (audit rows observed)', async () => {
    const before = probe.mark();
    const popup = await openEdit();
    const select = popup.locator('select[name="status"]');
    if (await select.count() > 0) {
      const options = await select.locator('option').evaluateAll(nodes => nodes.map(n => n.value));
      await select.selectOption(options.find(value => value === 'C' || value.startsWith('C')));
    } else await popup.locator('input[type="text"][name="status"]').fill('C');
    const closed = popup.waitForEvent('close', { timeout: 30000 }).then(() => true, () => false);
    await popup.locator('#updateButton').click();
    await closed;
    await waitAppointment(row => row.status.startsWith('C'), 'Cancelled');
    await judge('Cancelling the appointment', before);
  });

  await s.step('the Edit popup deletes the appointment, archived then removed (audit rows observed)', async () => {
    const before = probe.mark();
    const popup = await openEdit();
    const deleted = popup.waitForResponse(r => r.request().method() === 'POST' && /\/appointment\/DeleteRecord$/.test(new URL(r.url()).pathname), { timeout: 30000 });
    const closed = popup.waitForEvent('close', { timeout: 30000 }).then(() => true, () => false);
    const dialogs = await h.withExpectedDialogs(popup, async () => {
      await popup.locator('#deleteButton').click();
      h.assert((await deleted).status() < 400, 'DeleteRecord was refused');
    }, { accept: true });
    h.assert(dialogs.length === 1 && /delete/i.test(dialogs[0].text), 'Delete did not ask exactly one delete confirmation');
    await closed;
    const deadline = Date.now() + 20000;
    while (appointment() && Date.now() < deadline) await new Promise(resolve => setTimeout(resolve, 250));
    h.assert(!appointment(), 'The appointment row was not removed');
    h.assert(sql.value(`SELECT COUNT(*) FROM appointmentArchive WHERE ${apptWhere}`) !== '0', 'The delete wrote no appointmentArchive row');
    await judge('Deleting the appointment', before);
  });

  await s.step('every expectation of the appointment audit trail held', async () => {
    h.assert(!defects.length, `Audit-trail defects on the appointment lifecycle:\n  - ${[...new Set(defects)].join('\n  - ')}`);
  });
}

if (require.main === module) runWorkflow('audit-log-appointment', workflow);
module.exports = { workflow };
