#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Group booking, copy/paste, cut/paste and the appointment card, driven from the day sheet.
 * Implements coverage plan §2.3 `appointment-group-copy-cut`.
 *
 * User path (as a throwaway login whose "My Group" holds two owned providers): Schedule ▸
 * next day ▸ slot ▸ Add Appointment popup ▸ patient search ▸ Group Appt
 * (appointment/appointmentgrouprecords) ▸ tick the second provider ▸ Add Group Appointment;
 * appointment link ▸ Edit popup ▸ Group Action ▸ Group Update / Group Cancel / Group Delete;
 * Edit popup ▸ Copy (appointment/appointmentcopyrecord) ▸ another slot (auto-paste) ▸ Add;
 * Edit popup ▸ Cut (appointment/CutRecord) ▸ another slot (auto-paste) ▸ Add; Edit popup ▸
 * Print Card (appointment/appointmentviewrecordcard).
 *
 * Asserts the `appointment` rows after every action (provider, date, time, patient, reason,
 * notes, status), `appointmentArchive` rows for update/cancel/delete/cut, the pasted form
 * fields, the day sheet links after each opener refresh, the card's patient/date/provider text
 * and its print call, and that GET cannot reach CutRecord or appointmentgrouprecords.
 * Fixtures: the session's owned patient, a throwaway login (lib/throwaway-login-fixture.js) plus
 * a second marker provider, a marker group holding both and the throwaway's ProviderPreference
 * pointing at it. Cleanup deletes only rows of the two owned providers and asserts they are gone.
 */
const {randomInt} = require('node:crypto');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const {bundleMessage, throwawayLoginFixture} = require('./lib/throwaway-login-fixture');
const {runWorkflow, expectValue} = require('./lib/workflow-session');

const NEXT_DAY = 'a.redArrow:has(span.fa-forward-step)';
// The group page's buttons are labelled from the bundle; the hidden groupappt value is fixed.
const GROUP_BUTTON = {
  add: bundleMessage('appointment.appointmentgrouprecords.btnAddGroupAppt', 'Add Recurring Appointment'),
  update: bundleMessage('appointment.appointmentgrouprecords.btnGroupUpdate', 'Recurring Update'),
  cancel: bundleMessage('appointment.appointmentgrouprecords.btnGroupCancel', 'Recurring Cancel'),
  delete: bundleMessage('appointment.appointmentgrouprecords.btnGroupDelete', 'Recurring Delete'),
};
const APPOINTMENT_COLUMNS = 'appointment_no,provider_no,appointment_date,start_time,end_time,demographic_no,status,reason,notes,name';

function toRow([id, provider, date, start, end, demographic, status, reason, notes, name]) {
  return {id, provider, date, start, end, demographic, status, reason, notes, name};
}

async function waitClosed(page, label) {
  if (!page.isClosed()) await page.waitForEvent('close', {timeout: 20000}).catch(() => {});
  h.assert(page.isClosed(), `${label}: the popup did not close itself, so its save did not complete`);
}

async function workflow(s) {
  const {sql, marker, patient, config, recorder} = s;
  const fixture = throwawayLoginFixture({sql, marker, provider: s.provider, testUser: config.testUser});
  const groupName = 'PW' + marker.slice(-8);
  let second = null;
  let ownedProviders = [];
  const inOwned = () => ownedProviders.map(h.sqlString).join(',');
  // Cleanups run in reverse: appointments and the group go before the throwaway login.
  s.cleanup(() => fixture.cleanup());
  s.cleanup(() => {
    if (!ownedProviders.length) return;
    const ids = sql.rows(`SELECT appointment_no FROM appointment WHERE provider_no IN (${inOwned()})`).map(([id]) => id);
    h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE provider_no IN (${inOwned()})
      AND demographic_no NOT IN (0,${patient})`) === '0', 'An owned provider holds an appointment for a patient this run does not own');
    const statements = [
      `DELETE FROM appointment WHERE provider_no IN (${inOwned()})`,
      `DELETE FROM appointmentArchive WHERE provider_no IN (${inOwned()})`,
      `DELETE FROM mygroup WHERE mygroup_no=${h.sqlString(groupName)}`,
      ...['ProviderPreference', 'ProviderPreferenceAppointmentScreenEForm', 'ProviderPreferenceAppointmentScreenForm',
        'ProviderPreferenceAppointmentScreenQuickLink'].map(table => `DELETE FROM ${table} WHERE providerNo IN (${inOwned()})`),
    ];
    if (ids.length) statements.push(`DELETE FROM other_id WHERE table_name=2 AND table_id IN (${ids.map(h.sqlString).join(',')})`);
    if (second) statements.push(`DELETE FROM provider WHERE provider_no=${h.sqlString(second)} AND last_name=${h.sqlString(marker)}`);
    sql.execute(statements.join(';'));
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM appointment WHERE provider_no IN (${inOwned()}))
      + (SELECT COUNT(*) FROM appointmentArchive WHERE provider_no IN (${inOwned()}))
      + (SELECT COUNT(*) FROM mygroup WHERE mygroup_no=${h.sqlString(groupName)})
      + (SELECT COUNT(*) FROM ProviderPreference WHERE providerNo IN (${inOwned()}))
      + (SELECT COUNT(*) FROM provider WHERE provider_no=${h.sqlString(second || '')})`) === '0',
    'Owned appointment, group, preference or provider rows were not removed');
  });
  fixture.create();
  const first = fixture.providerNo;
  for (let attempt = 0; attempt < 20 && !second; attempt++) {
    const candidate = String(randomInt(800000, 899999));
    if (candidate !== first && sql.value(`SELECT (SELECT COUNT(*) FROM provider WHERE provider_no=${h.sqlString(candidate)})
      + (SELECT COUNT(*) FROM security WHERE provider_no=${h.sqlString(candidate)})`) === '0') second = candidate;
  }
  h.assert(second, 'No unused provider number was found for the second provider');
  ownedProviders = [first, second];
  h.assert(sql.value(`SELECT COUNT(*) FROM mygroup WHERE mygroup_no=${h.sqlString(groupName)}`) === '0', 'The marker group already exists');
  sql.execute(`INSERT INTO provider (provider_no,last_name,first_name,provider_type,specialty,sex,status,lastUpdateUser,lastUpdateDate)
      SELECT ${h.sqlString(second)},${h.sqlString(marker)},'Second',provider_type,specialty,sex,'1',${h.sqlString(s.provider)},NOW()
      FROM provider WHERE provider_no=${h.sqlString(s.provider)};
    INSERT INTO mygroup (mygroup_no,provider_no,last_name,first_name) VALUES
      (${h.sqlString(groupName)},${h.sqlString(first)},${h.sqlString(marker)},'Throwaway'),
      (${h.sqlString(groupName)},${h.sqlString(second)},${h.sqlString(marker)},'Second');
    INSERT INTO ProviderPreference (providerNo,startHour,endHour,everyMin,myGroupNo,colourTemplate,printQrCodeOnPrescriptions,
      lastUpdated,appointmentScreenLinkNameDisplayLength,defaultDoNotDeleteBilling)
      VALUES (${h.sqlString(first)},8,18,15,${h.sqlString(groupName)},'deepblue',0,NOW(),20,0)`);
  h.assert(sql.value(`SELECT COUNT(*) FROM mygroup WHERE mygroup_no=${h.sqlString(groupName)}`) === '2', 'The marker group was not created');

  const ownedRows = (where = '1=1') => sql.rows(`SELECT ${APPOINTMENT_COLUMNS} FROM appointment
    WHERE provider_no IN (${inOwned()}) AND ${where} ORDER BY provider_no,start_time,appointment_no`).map(toRow);
  const archived = id => Number(sql.value(`SELECT COUNT(*) FROM appointmentArchive WHERE appointment_no=${Number(id)}
    AND provider_no IN (${inOwned()})`));
  const surname = sql.value(`SELECT last_name FROM demographic WHERE demographic_no=${patient}`);

  // The throwaway's own session: its day sheet shows the marker group's two columns.
  const context = await h.newContext(s.context.browser(), config);
  context.setDefaultTimeout(20000);
  context.on('page', page => h.wireStrictPage(page, 'group-copy-cut', recorder));
  // The card prints itself on load; record the call instead of opening a print dialog.
  await context.addInitScript(() => {
    window.print = () => { window.__carlosPrintCalls = (window.__carlosPrintCalls || 0) + 1; };
  });
  const daySheet = await h.login(context, {...config, testUser: fixture.username}, recorder, {label: 'throwaway-login'});
  let date;

  const slot = (providerNo, time) => daySheet.locator(`a.adhour[onclick*="provider_no=${providerNo}&"]`)
    .filter({hasText: time}).first();
  const apptLink = id => daySheet.locator(`a.apptLink[onclick*="appointment_no=${id}&"], a.apptLink[onclick*="appointment_no=${id}'"]`).first();

  async function openAdd(providerNo, time) {
    const target = slot(providerNo, time);
    await target.waitFor({timeout: 20000});
    const popup = await ui.clickOpensPopup(daySheet, target, {context, recorder, label: 'add-appointment', timeout: 20000});
    await popup.waitForLoadState('domcontentloaded');
    await popup.locator('#addButton').waitFor({timeout: 20000});
    await h.assertNotErrorPage(popup, 'add appointment');
    h.assert((await popup.locator('form#addappt input[name="start_time"]').inputValue()).startsWith(time),
      'The add form was not prefilled with the clicked slot time');
    return popup;
  }

  async function pickPatient(popup) {
    await popup.locator('#keyword').fill(surname);
    await Promise.all([popup.waitForEvent('domcontentloaded'), popup.locator('#searchBtn').click()]);
    const pick = popup.locator(`input[type="button"][name="pick_demographic"][value="${patient}"]`).first();
    await pick.waitFor({timeout: 20000});
    await Promise.all([popup.waitForEvent('domcontentloaded'), pick.click()]);
    await popup.locator('#addButton').waitFor({timeout: 20000});
    h.assert(await popup.locator('#demographic_no').inputValue() === patient, 'The patient search did not link the owned patient');
  }

  async function openEdit(id) {
    const link = apptLink(id);
    await link.waitFor({timeout: 20000});
    const popup = await ui.clickOpensPopup(daySheet, link, {context, recorder, label: 'edit-appointment', timeout: 20000});
    await popup.waitForLoadState('domcontentloaded');
    await popup.locator('#updateButton').waitFor({timeout: 20000});
    h.assert(await popup.locator('input[name="appointment_no"]').inputValue() === String(id),
      'The edit popup opened a different appointment');
    return popup;
  }

  async function groupPage(popup) {
    await Promise.all([popup.waitForURL(/\/appointment\/appointmentgrouprecords/), popup.locator('#groupButton').click()]);
    await popup.locator('form[name="groupappt"]').waitFor({timeout: 20000});
    await h.assertNotErrorPage(popup, 'group appointment page');
    return popup;
  }
  const groupButton = (page, key) => page.locator(`form[name="groupappt"] input[type="button"][value="${GROUP_BUTTON[key]}"]`);
  const groupBox = (page, providerNo, column) =>
    page.locator(`tr:has(input[name^="provider_no"][value="${providerNo}"]) input[type="checkbox"][name^="${column}"]`);

  await s.step('the throwaway day sheet shows both owned providers and moves to the next day', async () => {
    await ui.clickAndAwaitReload(daySheet, daySheet.locator(NEXT_DAY).first(), {label: 'next day'});
    date = sql.value('SELECT DATE_ADD(CURDATE(), INTERVAL 1 DAY)');
    h.assert((await daySheet.locator('span.dateAppointment').first().innerText()).includes(date), 'The day sheet did not move to tomorrow');
    for (const providerNo of ownedProviders) {
      h.assert(await slot(providerNo, '10:00').count() === 1, 'An owned provider column is missing from the group day sheet');
    }
  });

  let groupIds;
  await s.step('Group Appt books the same patient for both providers in the marker group', async () => {
    const popup = await openAdd(first, '10:00');
    await pickPatient(popup);
    await popup.locator('#reason').fill(`${marker} group`);
    await popup.locator('textarea[name="notes"]').fill(`${marker} group notes`);
    await groupPage(popup);
    h.assert(await groupBox(popup, first, 'one').isChecked(), 'The clicked provider was not preselected for the group booking');
    h.assert(!(await groupBox(popup, second, 'one').isChecked()), 'The second provider was preselected');
    await groupBox(popup, second, 'one').check();
    await groupButton(popup, 'add').click();
    await waitClosed(popup, 'Add Group Appointment');
    const rows = ownedRows(`reason=${h.sqlString(`${marker} group`)}`);
    h.assert(rows.length === 2 && rows.map(r => r.provider).sort().join() === [...ownedProviders].sort().join(),
      'Group booking did not write exactly one appointment per selected provider');
    h.assert(rows.every(r => r.date === date && r.start === '10:00:00' && r.demographic === patient
      && r.notes === `${marker} group notes` && r.status === 't'), 'Group appointments lost the date, time, patient, notes or status');
    groupIds = rows.map(r => r.id);
    for (const id of groupIds) await apptLink(id).waitFor({timeout: 20000});
  });

  await s.step('Group Update from the edit popup rewrites both providers\' appointments', async () => {
    const popup = await openEdit(groupIds[0]);
    await popup.locator('#reason').fill(`${marker} group updated`);
    await groupPage(popup);
    for (const providerNo of ownedProviders) {
      h.assert(await groupBox(popup, providerNo, 'one').isChecked(), 'Group Action did not preselect a matching group appointment');
    }
    await groupButton(popup, 'update').click();
    await waitClosed(popup, 'Group Update');
    const rows = ownedRows(`reason=${h.sqlString(`${marker} group updated`)}`);
    h.assert(rows.length === 2 && rows.every(r => r.date === date && r.start === '10:00:00' && r.demographic === patient),
      'Group Update did not leave both providers with the updated appointment');
    h.assert(ownedRows(`reason=${h.sqlString(`${marker} group`)}`).length === 0, 'Group Update left the original appointments behind');
    h.assert(groupIds.every(id => archived(id) >= 1), 'Group Update replaced appointments without archiving them');
    groupIds = rows.map(r => r.id);
  });

  await s.step('Group Cancel marks both group appointments cancelled', async () => {
    const popup = await openEdit(groupIds[0]);
    await groupPage(popup);
    await groupButton(popup, 'cancel').click();
    await waitClosed(popup, 'Group Cancel');
    const rows = ownedRows(`appointment_no IN (${groupIds.join(',')})`);
    h.assert(rows.length === 2 && rows.every(r => r.status === 'C'), 'Group Cancel did not cancel both appointments');
  });

  await s.step('Group Delete asks first, then removes and archives both appointments', async () => {
    const popup = await openEdit(groupIds[0]);
    await groupPage(popup);
    const seen = await h.withExpectedDialogs(popup, async () => {
      await groupButton(popup, 'delete').click();
      await waitClosed(popup, 'Group Delete');
    });
    h.assert(seen.length === 1 && seen[0].type === 'confirm', 'Group Delete did not ask for confirmation exactly once');
    h.assert(ownedRows(`appointment_no IN (${groupIds.join(',')})`).length === 0, 'Group Delete left an appointment');
    h.assert(groupIds.every(id => archived(id) >= 2), 'Group Delete removed appointments without archiving them');
  });

  let source;
  await s.step('a single booking is written for the copy, cut and card steps', async () => {
    const popup = await openAdd(first, '11:00');
    await pickPatient(popup);
    await popup.locator('#reason').fill(`${marker} copy`);
    await popup.locator('textarea[name="notes"]').fill(`${marker} copy notes`);
    await popup.locator('#addButton').click();
    await waitClosed(popup, 'Add Appointment');
    const rows = ownedRows(`reason=${h.sqlString(`${marker} copy`)}`);
    h.assert(rows.length === 1 && rows[0].provider === first && rows[0].start === '11:00:00' && rows[0].demographic === patient,
      'The single booking was not written for the clicked slot and patient');
    source = rows[0];
    await apptLink(source.id).waitFor({timeout: 20000});
  });

  async function pasteInto(providerNo, time, label) {
    const popup = await openAdd(providerNo, time);
    // The add form pastes on load when the opener set copyPaste=1, and resets the flag.
    h.assert(await popup.locator('#pasteButton').isVisible(), `${label}: the add form offers no Paste button`);
    h.assert(await popup.locator('#demographic_no').inputValue() === patient
      && await popup.locator('#reason').inputValue() === `${marker} copy`
      && await popup.locator('textarea[name="notes"]').inputValue() === `${marker} copy notes`,
    `${label}: the add form did not paste the copied patient, reason and notes`);
    h.assert(await popup.evaluate(() => localStorage.getItem('copyPaste')) === '0', `${label}: the paste flag was not reset`);
    await popup.locator('#addButton').click();
    await waitClosed(popup, `${label} Add Appointment`);
    const rows = ownedRows(`provider_no=${h.sqlString(providerNo)} AND start_time=${h.sqlString(`${time}:00`)}`);
    h.assert(rows.length === 1 && rows[0].date === date && rows[0].demographic === patient && rows[0].reason === `${marker} copy`
      && rows[0].notes === `${marker} copy notes` && rows[0].name === source.name,
    `${label}: the pasted appointment did not keep the patient, reason, notes and name`);
    await apptLink(rows[0].id).waitFor({timeout: 20000});
    return rows[0];
  }

  let copy;
  await s.step('Copy keeps the original and pastes an identical booking into the other provider', async () => {
    const popup = await openEdit(source.id);
    await popup.locator('a.btn:has(i.fa-copy)').click();
    await waitClosed(popup, 'Copy');
    h.assert(JSON.stringify(ownedRows(`appointment_no=${source.id}`)) === JSON.stringify([source]), 'Copy changed the original appointment');
    copy = await pasteInto(second, '13:00', 'copy');
    h.assert(ownedRows(`reason=${h.sqlString(`${marker} copy`)}`).length === 2, 'Copy and paste did not leave exactly two bookings');
  });

  await s.step('Cut archives and removes the booking, and Paste books it in the new slot', async () => {
    const popup = await openEdit(copy.id);
    await popup.locator('a.btn:has(i.fa-scissors)').click();
    await waitClosed(popup, 'Cut');
    h.assert(ownedRows(`appointment_no=${copy.id}`).length === 0, 'Cut left the appointment in place');
    h.assert(archived(copy.id) === 1, 'Cut removed the appointment without archiving it');
    await apptLink(copy.id).waitFor({state: 'detached', timeout: 20000});
    await pasteInto(first, '14:00', 'cut');
    h.assert(ownedRows(`reason=${h.sqlString(`${marker} copy`)}`).length === 2, 'Cut and paste changed the number of bookings');
  });

  await s.step('Print Card renders the patient, date, time and provider and prints itself', async () => {
    const popup = await openEdit(source.id);
    await Promise.all([popup.waitForURL(/\/appointment\/appointmentviewrecordcard/), popup.locator('a.btn:has(i.fa-print)').first().click()]);
    await popup.waitForLoadState('load');
    await h.assertNotErrorPage(popup, 'appointment card');
    const text = (await popup.locator('body').innerText()).replace(/\s+/g, ' ');
    const [[pretty, time]] = sql.rows(`SELECT DATE_FORMAT(appointment_date,'%a, %e %b %Y'),
      LOWER(TIME_FORMAT(start_time,'%l:%i %p')) FROM appointment WHERE appointment_no=${source.id}`);
    h.assert(text.includes(source.name), 'The card does not show the appointment name');
    h.assert(text.includes(`${pretty} at ${time.trim()}`) || text.toLowerCase().includes(`${pretty.toLowerCase()} at ${time.trim()}`),
      'The card does not show the appointment date and time');
    h.assert(text.includes(`Throwaway ${marker}`), 'The card does not name the appointment provider');
    h.assert(await popup.evaluate(() => window.__carlosPrintCalls || 0) >= 1, 'The card did not open the print dialog on load');
    await popup.close();
  });

  await s.step('GET cannot cut an appointment or post a group booking', async () => {
    const cut = await context.request.get(h.appUrl(config.baseUrl, `/appointment/CutRecord?appointment_no=${source.id}`), {maxRedirects: 0});
    h.assert(cut.status() === 405, `GET CutRecord answered HTTP ${cut.status()}`);
    const group = await context.request.get(h.appUrl(config.baseUrl, `/appointment/appointmentgrouprecords?groupappt=Group+Delete&appointment_no=${source.id}`),
      {maxRedirects: 0});
    h.assert(group.status() === 405, `GET appointmentgrouprecords answered HTTP ${group.status()}`);
    h.assert(ownedRows(`appointment_no=${source.id}`).length === 1, 'A GET probe removed the appointment');
  });
  await context.close();
  await expectValue(sql, `SELECT COUNT(*) FROM appointment WHERE provider_no IN (${inOwned()})`, '2',
    'The run ended with an unexpected number of owned appointments');
}

if (require.main === module) runWorkflow('appointment-group-copy-cut', workflow, {openPatient: true, openMaster: false});
module.exports = {workflow};
