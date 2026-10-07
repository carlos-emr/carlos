#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Concurrency check: two sessions change and cancel / delete the same appointment.
 *
 * User path (both sessions of the shared test login): Schedule day sheet (target date) > the
 * appointment's status letter, or its own link > edit popup > Update Appt / Delete.
 * Asserted, in this order (the failing step is last):
 *   1. control: a stale day-sheet status click (session A clicks after session B already advanced the
 *      status) is refused with HTTP 409 and an alert, and the status is not changed again
 *      (AppointmentStatusTransitionService locks the row and compares the status);
 *   2. control: session A updates an appointment session B has just deleted: the update is refused (404),
 *      no appointment row is resurrected and no second archive row is written;
 *   3. session B cancels the appointment from its edit popup, then session A (popup opened BEFORE the
 *      cancel) changes the reason and clicks Update Appt: the cancellation must survive, the stale
 *      save must retain A's draft with a conflict, and reviewing the current record must allow the
 *      intended reason change without undoing the cancellation.
 * Fixtures: two owned appointments (marker reason, provider of the test login, 401 days ahead) seeded
 * by SQL for the owned FAKE- patient; cleanup deletes the appointment, appointmentArchive and
 * other_id(appt_mc_number) rows it owns and asserts them gone. Wave-7 sweep "concurrency".
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { openSecondSession, failureMark, consumeExpectedFailure, consumeAbortedFetch, waitUntil } = require('./lib/concurrency-support');

const TIMEOUT = 30000;
const DAYS_AHEAD = Number(process.env.CONCURRENCY_APPOINTMENT_DAYS_AHEAD || '401');

async function openDaySheet(page, config, query) {
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      await h.gotoApp(page, config.baseUrl, `/provider/providercontrol?${query}`);
      await page.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
      return;
    } catch (error) {
      if (attempt === 2 || !/ERR_ABORTED|frame was detached|interrupted by another navigation/.test(error.message)) throw error;
      await page.waitForTimeout(750);
    }
  }
}

async function openEdit(s, context, daySheet, id, label) {
  const link = daySheet.locator(`a.apptLink[onclick*="appointment_no=${id}"]`).first();
  await link.waitFor({ state: 'visible', timeout: TIMEOUT });
  const popupPromise = context.waitForEvent('page', { timeout: TIMEOUT });
  await link.click();
  const popup = await popupPromise;
  h.wireStrictPage(popup, label, s.recorder);
  await popup.waitForLoadState('domcontentloaded', { timeout: TIMEOUT });
  await popup.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  h.assert(await popup.locator('input[name="appointment_no"]').inputValue() === String(id), 'The edit popup opened another appointment');
  return popup;
}

async function workflow(s) {
  const { sql, patient, marker, provider } = s;
  const owner = h.sqlString(provider);
  const target = new Date(Date.now() + DAYS_AHEAD * 86400000);
  const dateKey = `${target.getUTCFullYear()}-${String(target.getUTCMonth() + 1).padStart(2, '0')}-${String(target.getUTCDate()).padStart(2, '0')}`;
  const query = new URLSearchParams({
    year: String(target.getUTCFullYear()), month: String(target.getUTCMonth() + 1), day: String(target.getUTCDate()),
    view: '0', displaymode: 'day', dboperation: 'searchappointmentday', viewall: '1',
  }).toString();
  const ids = [];
  s.cleanup(() => {
    const like = h.sqlString(`${marker}%`);
    const idList = ids.length ? ids.join(',') : '0';
    sql.execute(`DELETE FROM other_id WHERE other_key='appt_mc_number' AND table_id IN (${idList.split(',').map(i => `'${i}'`).join(',')});
      DELETE FROM appointmentArchive WHERE demographic_no=${patient} AND reason LIKE ${like};
      DELETE FROM appointment WHERE demographic_no=${patient} AND reason LIKE ${like}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM appointment WHERE demographic_no=${patient} AND reason LIKE ${like})
      + (SELECT COUNT(*) FROM appointmentArchive WHERE demographic_no=${patient} AND reason LIKE ${like})`) === '0',
    'Owned appointment rows were not removed');
  });
  const seed = (start, end, tag) => {
    const id = sql.value(`INSERT INTO appointment (provider_no,appointment_date,start_time,end_time,name,demographic_no,reason,notes,status,
      createdatetime,updatedatetime,creator,lastupdateuser,location,resources,type,style,billing,remarks,urgency,program_id)
      VALUES (${owner},${h.sqlString(dateKey)},${h.sqlString(start)},${h.sqlString(end)},${h.sqlString(marker)},${patient},
        ${h.sqlString(`${marker} ${tag}`)},'','t',NOW(),NOW(),${owner},${owner},'','','','','','','',0); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), 'Appointment fixture was not created');
    ids.push(id);
    return id;
  };
  const apptStatus = id => `SELECT status FROM appointment WHERE appointment_no=${id}`;
  const statusId = seed('08:00:00', '08:14:00', 'status race');
  const deleteId = seed('08:30:00', '08:44:00', 'delete race');
  const cancelId = seed('09:00:00', '09:14:00', 'cancel race');

  const b = await openSecondSession(s, { label: 'second-session', openMaster: false });
  const aSheet = s.schedule;
  const bSheet = b.schedule;
  await openDaySheet(aSheet, s.config, query);
  await openDaySheet(bSheet, s.config, query);
  for (const sheet of [aSheet, bSheet]) {
    h.assert(await sheet.locator(`a.apptLink[onclick*="appointment_no=${statusId}"]`).count() > 0,
      'The day sheet does not list the owned fixture appointment');
  }

  const staleClickMark = failureMark(s.recorder);
  await s.step('control: a stale day-sheet status click is refused with a conflict and an alert', async () => {
    const staleEdit = await openEdit(s, s.context, aSheet, statusId, 'status-edit-a');
    const beforeTime = sql.value(`SELECT updatedatetime FROM appointment WHERE appointment_no=${statusId}`);
    const link = sheet => sheet.locator(`a.apptStatus[onclick*="appointment_no=${statusId}&"]`).first();
    await link(bSheet).waitFor({ state: 'visible', timeout: TIMEOUT });
    await link(bSheet).click();
    await waitUntil(() => sql.value(apptStatus(statusId)) !== 't', 'session B\'s status change');
    const advanced = sql.value(apptStatus(statusId));
    await bSheet.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    const mark = failureMark(s.recorder);
    const dialogs = await h.withExpectedDialogs(aSheet, async () => {
      await link(aSheet).click();
      await waitUntil(() => s.recorder.badResponses.length > mark.responses, 'the refused stale status click', 20000);
      await aSheet.waitForTimeout(800);
    });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'alert', 'The stale status click raised no alert to the user');
    await aSheet.waitForTimeout(300);
    consumeExpectedFailure(s.recorder, mark, { status: 409, path: /\/provider\/providercontrol$/, appConsole: /^Error: HTTP 409/ });
    h.assert(sql.value(apptStatus(statusId)) === advanced, 'The refused stale status click changed the status again');
    // Appointment's JPA @PreUpdate callback advances updatedatetime, but the database column
    // has second precision. Model two writes sharing that timestamp using only the owned fixture:
    // retain the committed status change and restore the rendered timestamp. A timestamp-only
    // stale check would now incorrectly accept the original form.
    sql.execute(`UPDATE appointment SET updatedatetime=${h.sqlString(beforeTime)}
      WHERE appointment_no=${statusId} AND demographic_no=${patient} AND status=${h.sqlString(advanced)}`);
    h.assert(sql.value(`SELECT updatedatetime FROM appointment WHERE appointment_no=${statusId}`) === beforeTime,
      'The owned same-timestamp status-race fixture was not established');
    const reason = `${marker} retained after status-only race`;
    await staleEdit.locator('#reason').fill(reason);
    const editMark = failureMark(s.recorder);
    const [editResponse] = await Promise.all([
      staleEdit.waitForResponse(r => r.request().method() === 'POST' && /\/appointment\/UpdateRecord$/.test(new URL(r.url()).pathname)),
      staleEdit.locator('#updateButton').click(),
    ]);
    h.assert(editResponse.status() === 409, 'A concurrent status change without a timestamp change was not detected');
    await staleEdit.waitForLoadState('domcontentloaded');
    h.assert(await staleEdit.locator('#reason').inputValue() === reason, 'The status conflict lost the entered reason');
    consumeExpectedFailure(s.recorder, editMark, { status: 409, path: /\/appointment\/UpdateRecord$/ });
    h.assert(sql.value(apptStatus(statusId)) === advanced, 'The stale editor reverted the day-sheet status');
    const original = editResponse.request();
    const beforeReplay = sql.rows(`SELECT reason,status FROM appointment WHERE appointment_no=${statusId}`);
    const archiveCount = sql.value(`SELECT COUNT(*) FROM appointmentArchive WHERE appointment_no=${statusId}`);
    for (const [locale, language, message, review] of [
      ['en', 'en', 'This appointment changed', 'Review the current appointment in a new window'],
      ['fr', 'fr', 'Ce rendez-vous a changé', 'Consulter le rendez-vous actuel dans une nouvelle fenêtre'],
      ['es', 'es', 'Esta cita ha cambiado', 'Revisar la cita actual en una ventana nueva'],
      ['pl', 'pl', 'Ta wizyta została zmieniona', 'Sprawdź aktualny stan wizyty w nowym oknie'],
      ['pt-BR', 'pt', 'Esta consulta foi alterada', 'Conferir a consulta atual em uma nova janela'],
      ['de-DE,fr;q=0.9', 'fr', 'Ce rendez-vous a changé', 'Consulter le rendez-vous actuel dans une nouvelle fenêtre'],
      ['de-DE', 'en', 'This appointment changed', 'Review the current appointment in a new window'],
    ]) {
      const replay = await s.context.request.post(original.url(), {
        data: original.postData(), headers: { 'Content-Type': original.headers()['content-type'], 'Accept-Language': locale },
        maxRedirects: 0,
      });
      const body = await replay.text();
      h.assert(replay.status() === 409 && body.includes(`<html lang="${language}">`)
        && body.includes(message) && body.includes(review), `Appointment recovery did not render ${locale}`);
      h.assert(body.includes(reason) && !body.includes('???appointment.edit.'), 'Localized refusal lost the draft or message key');
      h.assert(JSON.stringify(sql.rows(`SELECT reason,status FROM appointment WHERE appointment_no=${statusId}`)) === JSON.stringify(beforeReplay)
        && sql.value(`SELECT COUNT(*) FROM appointmentArchive WHERE appointment_no=${statusId}`) === archiveCount,
      'Localized stale replay changed the appointment or its archive');
    }
    await staleEdit.close();
  });

  await s.step('control: updating an appointment another session deleted is refused and resurrects nothing', async () => {
    const aEdit = await openEdit(s, s.context, aSheet, deleteId, 'edit-a');
    await openDaySheet(bSheet, s.config, query);
    const bEdit = await openEdit(s, b.context, bSheet, deleteId, 'edit-b');
    const confirms = await h.withExpectedDialogs(bEdit, () => bEdit.locator('#deleteButton').click());
    h.assert(confirms.length === 1 && confirms[0].type === 'confirm', 'Delete did not ask for confirmation');
    await expectValue(sql, `SELECT COUNT(*) FROM appointment WHERE appointment_no=${deleteId}`, '0', 'Session B\'s delete did not remove the appointment');
    const archivedBefore = sql.value(`SELECT COUNT(*) FROM appointmentArchive WHERE appointment_no=${deleteId}`);
    h.assert(archivedBefore === '1', 'The delete did not leave exactly one archive row');
    await aEdit.locator('#reason').fill(`${marker} stale update of a deleted appointment`);
    const mark = failureMark(s.recorder);
    const [response] = await Promise.all([
      aEdit.waitForResponse(r => r.request().method() === 'POST' && /\/appointment\/UpdateRecord$/.test(new URL(r.url()).pathname), { timeout: TIMEOUT }),
      aEdit.locator('#updateButton').click(),
    ]);
    h.assert(response.status() === 404, `Updating a deleted appointment answered HTTP ${response.status()} instead of refusing it with 404`);
    await aEdit.waitForTimeout(500);
    consumeExpectedFailure(s.recorder, mark, { status: 404, path: /\/appointment\/UpdateRecord$/ });
    h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE appointment_no=${deleteId}`) === '0', 'The stale update resurrected the deleted appointment');
    h.assert(sql.value(`SELECT COUNT(*) FROM appointmentArchive WHERE appointment_no=${deleteId}`) === archivedBefore, 'The stale update wrote a second archive row');
    h.assert(await aEdit.locator('#reason').inputValue() === `${marker} stale update of a deleted appointment`,
      'The deleted-appointment refusal lost the entered reason');
    await aEdit.close().catch(() => {});
    await bEdit.close().catch(() => {});
  });

  await s.step('session A\'s stale Update Appt does not undo session B\'s cancellation', async () => {
    await openDaySheet(aSheet, s.config, query);
    // The refused status fetch's unread 409 body is reported aborted when the sheet navigates away.
    await aSheet.waitForTimeout(400);
    consumeAbortedFetch(s.recorder, staleClickMark, /\/provider\/providercontrol$/);
    await openDaySheet(bSheet, s.config, query);
    const aEdit = await openEdit(s, s.context, aSheet, cancelId, 'edit-a');
    const bEdit = await openEdit(s, b.context, bSheet, cancelId, 'edit-b');
    const select = bEdit.locator('select[name="status"]');
    h.assert(await select.count() > 0, 'The edit popup offers no status select');
    const options = await select.locator('option').evaluateAll(nodes => nodes.map(n => n.value));
    const cancelled = options.find(value => value === 'C');
    h.assert(cancelled, 'The edit popup status select offers no Cancelled option');
    await select.selectOption(cancelled);
    await Promise.all([
      bEdit.waitForResponse(r => r.request().method() === 'POST' && /\/appointment\/UpdateRecord$/.test(new URL(r.url()).pathname), { timeout: TIMEOUT }),
      bEdit.locator('#updateButton').click(),
    ]);
    await expectValue(sql, apptStatus(cancelId), 'C', 'Session B\'s cancellation did not reach the database');
    const beforeReason = sql.value(`SELECT reason FROM appointment WHERE appointment_no=${cancelId}`);
    const beforeArchive = sql.value(`SELECT COUNT(*) FROM appointmentArchive WHERE appointment_no=${cancelId}`);
    const originalVersion = await aEdit.locator('input[name="appointmentEditVersion"]').inputValue();
    await aEdit.locator('#reason').fill(`${marker} edited by session A`);
    const mark = failureMark(s.recorder);
    const [refusal] = await Promise.all([
      aEdit.waitForResponse(r => r.request().method() === 'POST' && /\/appointment\/UpdateRecord$/.test(new URL(r.url()).pathname), { timeout: TIMEOUT }),
      aEdit.locator('#updateButton').click(),
    ]);
    h.assert(refusal.status() === 409, `The stale edit answered ${refusal.status()} instead of a conflict`);
    await aEdit.waitForLoadState('domcontentloaded');
    consumeExpectedFailure(s.recorder, mark, { status: 409, path: /\/appointment\/UpdateRecord$/ });
    h.assert(await aEdit.locator('#reason').inputValue() === `${marker} edited by session A`, 'Conflict discarded the draft');
    h.assert(await aEdit.locator('input[name="appointmentEditVersion"]').inputValue() === originalVersion,
      'Conflict silently refreshed the stale version and enabled a blind overwrite');
    h.assert(sql.value(`SELECT reason FROM appointment WHERE appointment_no=${cancelId}`) === beforeReason, 'Refusal changed the reason');
    h.assert(sql.value(`SELECT COUNT(*) FROM appointmentArchive WHERE appointment_no=${cancelId}`) === beforeArchive,
      'Refusal wrote an archive');
    h.assert(sql.value(apptStatus(cancelId)) === 'C', 'Refusal undid the cancellation');
    const currentPage = s.context.waitForEvent('page');
    await aEdit.locator('#reviewCurrentAppointment').click();
    const recovered = await currentPage;
    h.wireStrictPage(recovered, 'review-current-appointment', s.recorder);
    await recovered.waitForLoadState('domcontentloaded');
    h.assert(await recovered.locator('select[name="status"]').inputValue() === 'C', 'Recovery did not show the current cancellation');
    h.assert(await aEdit.locator('#reason').inputValue() === `${marker} edited by session A`, 'Opening recovery lost the original draft');
    await recovered.locator('#reason').fill(`${marker} edited by session A`);
    await Promise.all([
      recovered.waitForResponse(r => r.request().method() === 'POST' && /\/appointment\/UpdateRecord$/.test(new URL(r.url()).pathname), { timeout: TIMEOUT }),
      recovered.locator('#updateButton').click(),
    ]);
    await expectValue(sql, `SELECT reason FROM appointment WHERE appointment_no=${cancelId}`, `${marker} edited by session A`,
      'Session A\'s own edit (the reason) was not stored');
    const status = sql.value(apptStatus(cancelId));
    h.assert(status === 'C', `The corrected save silently un-cancelled the appointment (status is now '${status}')`);
    await recovered.close().catch(() => {});
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('concurrency-appointment-edit', workflow, { openPatient: true, openMaster: false });
