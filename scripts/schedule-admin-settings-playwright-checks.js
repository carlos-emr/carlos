#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Schedule administration settings end to end. Implements coverage plan §2.3
 * `schedule-admin-settings`.
 *
 * User path: Schedule ▸ Administration ▸ Schedule Management ▸ Schedule Setting (admin iframe)
 * ▸ Holiday Setting popup (schedule/HolidaySetting) and Template Code Setting popup
 * (schedule/TemplateCodeSetting); ▸ provider selector ▸ week setting ▸ template list preview
 * (schedule/DisplayTemplate) ▸ Next ▸ calendar ▸ day popup (schedule/DatePopup ▸
 * schedule/DateSave); ▸ Appointment Status Setting (appointment/apptStatusSetting) ▸ Edit /
 * Disable / Enable; ▸ Appointment Type List (appointment/appointmentTypeAction) ▸ Save / Edit /
 * Delete; Schedule ▸ slot ▸ Add Appointment popup ▸ Type menu offers the owned type.
 *
 * Asserts the `scheduleholiday`, `scheduletemplatecode`, `scheduledate`, `appointment_status` and
 * `appointmentType` rows after each save/delete, the validation alerts, the preview and calendar
 * rendering of the owned code and holiday, and that GET cannot reach any of the mutators.
 * Fixtures: a marker provider with one seeded day template, an unused template code character, a
 * holiday on a free far-future date, and a marker appointment type. One editable appointment
 * status is snapshotted, changed through the UI and restored (run with EXCLUSIVE=1: the status
 * label/colour is clinic-wide). Cleanup deletes only rows carrying the marker or owned keys.
 */
const {randomInt} = require('node:crypto');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const {revealAuditLink} = require('./lib/playwright-link-audit');
const {bundleMessage} = require('./lib/throwaway-login-fixture');
const {runWorkflow, expectValue} = require('./lib/workflow-session');

const WEEKDAYS = ['sun', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat'];
// Never a code already present in either case: the column compares case-insensitively.
const CODE_CANDIDATES = ['Q', 'Z', 'X', 'Y', 'J', 'K', '7', '8', '9', '0', '4', '5'];
const LABEL = {
  save: bundleMessage('schedule.scheduleholidaysetting.btnSave', 'Save'),
  delete: bundleMessage('schedule.scheduleholidaysetting.btnDelete', 'Delete'),
  holidayAlert: bundleMessage('schedule.scheduleholidaysetting.msgCheckInput', 'Please check your input!!!'),
  codeAlert: bundleMessage('schedule.scheduletemplatecodesetting.msgCheckInput', 'Please check your input!!!'),
  codeEdit: bundleMessage('schedule.scheduletemplatecodesetting.btnEdit', 'Edit'),
  codeSave: bundleMessage('schedule.scheduletemplatecodesetting.btnSave', 'Save'),
  codeDelete: bundleMessage('schedule.scheduletemplatecodesetting.btnDelete', 'Delete'),
  dateSave: bundleMessage('schedule.scheduledatepopup.btnSave', 'Save'),
  disable: bundleMessage('admin.appt.status.mgr.label.disable', 'Disable'),
  enable: bundleMessage('admin.appt.status.mgr.label.enableAction', 'Enable'),
};

/** Open a left-nav admin section through its .xlink into the shell's iframe. */
async function openAdminSection(admin, relSuffix, readySelector) {
  const link = admin.locator(`a.xlink[rel$="${relSuffix}"]`).first();
  await revealAuditLink(admin, link, 20000);
  await link.click({timeout: 20000});
  const iframe = admin.locator('#dynamic-content iframe').first();
  await iframe.waitFor({timeout: 20000});
  let frame;
  const deadline = Date.now() + 20000;
  do {
    frame = await (await iframe.elementHandle()).contentFrame();
    if (frame && frame.url().includes(relSuffix.split('?')[0])) break;
    await new Promise(resolve => setTimeout(resolve, 150));
  } while (Date.now() < deadline);
  h.assert(frame && frame.url().includes(relSuffix.split('?')[0]), `${relSuffix} did not load inside the administration iframe`);
  await frame.locator(readySelector).first().waitFor({timeout: 20000});
  await h.assertNotErrorPage(frame, relSuffix);
  return frame;
}

/** Click something inside an admin iframe that navigates that iframe, and wait for it. */
async function frameNavigation(admin, frame, action) {
  const navigated = admin.waitForEvent('framenavigated', {predicate: candidate => candidate === frame, timeout: 20000});
  navigated.catch(() => {});
  await action();
  await navigated;
  await frame.waitForLoadState('domcontentloaded', {timeout: 20000}).catch(() => {});
}

async function navigates(page, action) {
  await Promise.all([page.waitForEvent('domcontentloaded', {timeout: 20000}), action()]);
  await h.assertNotErrorPage(page, 'popup after submit');
}

async function workflow(s) {
  const {sql, marker, provider, recorder} = s;
  const hex = marker.slice(-16);
  const q = h.sqlString;
  // ---- fixtures -----------------------------------------------------------------------------
  const code = CODE_CANDIDATES.find(candidate => sql.value(`SELECT COUNT(*) FROM scheduletemplatecode WHERE code=${q(candidate)}`) === '0');
  if (!code) throw new h.SkipCheck('Every candidate template code character is already defined');
  const codeDesc = marker;
  const codeColor = '#C0FFEE';
  const templateName = `PW${hex.slice(0, 12)}`;
  const typeName = marker;
  // A free far-future weekday, 15 months out, for the holiday and the one-day schedule change.
  const [[target, , targetMonth, targetDay, weekday]] = sql.rows(`SELECT d, YEAR(d), MONTH(d), DAY(d), DAYOFWEEK(d) FROM (
      SELECT DATE_ADD(DATE_ADD(LAST_DAY(DATE_ADD(CURDATE(), INTERVAL 14 MONTH)), INTERVAL 1 DAY), INTERVAL ${randomInt(9, 19)} DAY) d) x`);
  let holidayDate = target;
  let day = Number(targetDay);
  for (let i = 0; i < 7 && (['1', '7'].includes(sql.value(`SELECT DAYOFWEEK(${q(holidayDate)})`))
    || sql.value(`SELECT COUNT(*) FROM scheduleholiday WHERE sdate=${q(holidayDate)}`) !== '0'); i++) {
    day += 1;
    holidayDate = sql.value(`SELECT DATE_ADD(${q(target)}, INTERVAL ${day - Number(targetDay)} DAY)`);
  }
  h.assert(sql.value(`SELECT COUNT(*) FROM scheduleholiday WHERE sdate=${q(holidayDate)}`) === '0', 'No free far-future holiday date was found');
  h.assert(weekday && Number(targetMonth) >= 1, 'The database did not compute the far-future date');
  const holidayName = `${marker} holiday`;
  const dayName = WEEKDAYS[Number(sql.value(`SELECT DAYOFWEEK(${q(holidayDate)})`)) - 1];
  const statusRow = sql.rows(`SELECT id,status,description,color,active FROM appointment_status
    WHERE editable=1 AND active=1 AND status IN ('f','e','d','b','a') ORDER BY status DESC LIMIT 1`)[0];
  if (!statusRow) throw new h.SkipCheck('No editable active custom appointment status is available');
  const [statusId, statusCode, statusDesc, statusColor] = statusRow;
  const statusSnapshot = () => JSON.stringify(sql.rows(`SELECT description,color,active FROM appointment_status WHERE id=${Number(statusId)}`));
  const originalStatus = statusSnapshot();
  let owner = null;

  s.cleanup(() => {
    const statements = [
      `DELETE FROM scheduleholiday WHERE sdate=${q(holidayDate)} AND holiday_name LIKE ${q(`${marker}%`)}`,
      `DELETE FROM scheduletemplatecode WHERE code=${q(code)} AND description LIKE ${q(`${marker}%`)}`,
      `DELETE FROM appointmentType WHERE name=${q(typeName)}`,
      `UPDATE appointment_status SET description=${q(statusDesc)},color=${q(statusColor)},active=${Number(statusRow[4])} WHERE id=${Number(statusId)}`,
    ];
    if (owner) {
      for (const table of ['scheduletemplate', 'scheduledate', 'rschedule']) statements.push(`DELETE FROM ${table} WHERE provider_no=${q(owner)}`);
      statements.push(`DELETE FROM provider WHERE provider_no=${q(owner)} AND last_name=${q(marker)}`);
    }
    sql.execute(statements.join(';'));
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM scheduleholiday WHERE sdate=${q(holidayDate)} AND holiday_name LIKE ${q(`${marker}%`)})
      + (SELECT COUNT(*) FROM scheduletemplatecode WHERE description LIKE ${q(`${marker}%`)})
      + (SELECT COUNT(*) FROM appointmentType WHERE name=${q(typeName)})
      + (SELECT COUNT(*) FROM provider WHERE provider_no=${q(owner || '')})
      + (SELECT COUNT(*) FROM scheduledate WHERE provider_no=${q(owner || '')})
      + (SELECT COUNT(*) FROM rschedule WHERE provider_no=${q(owner || '')})`) === '0', 'Owned schedule settings rows were not removed');
    h.assert(statusSnapshot() === originalStatus, 'The appointment status was not restored to its snapshot');
  });
  for (let attempt = 0; attempt < 20 && !owner; attempt++) {
    const candidate = String(randomInt(800000, 899999));
    if (sql.value(`SELECT (SELECT COUNT(*) FROM provider WHERE provider_no=${q(candidate)})
      + (SELECT COUNT(*) FROM security WHERE provider_no=${q(candidate)})`) === '0') owner = candidate;
  }
  h.assert(owner, 'No unused provider number was found for the schedule provider');
  const timecode = '_'.repeat(36) + code.repeat(8) + '_'.repeat(52);
  sql.execute(`INSERT INTO provider (provider_no,last_name,first_name,provider_type,specialty,sex,status,lastUpdateUser,lastUpdateDate)
      SELECT ${q(owner)},${q(marker)},'Schedule',provider_type,specialty,sex,'1',${q(provider)},NOW() FROM provider WHERE provider_no=${q(provider)};
    INSERT INTO scheduletemplate (provider_no,name,summary,timecode) VALUES (${q(owner)},${q(templateName)},${q(`${marker} day`)},${q(timecode)})`);
  h.assert(sql.value(`SELECT COUNT(*) FROM scheduletemplate WHERE provider_no=${q(owner)}`) === '1', 'The owned day template was not seeded');

  // ---- administration shell --------------------------------------------------------------------
  const {page: admin, isPopup} = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
    {context: s.context, recorder, label: 'administration', timeout: 20000});
  let daySheet = s.schedule;
  if (!isPopup) {
    daySheet = await s.context.newPage();
    await h.gotoApp(daySheet, s.config.baseUrl, '/provider/providercontrol');
    await h.assertNotErrorPage(daySheet, 'day sheet');
  }
  const scheduleSetting = () => openAdminSection(admin, '/schedule/TemplateSetting', 'select[name="provider_no"]');
  const popupFrom = (frame, selector, label) => ui.clickOpensPopup(admin, frame.locator(selector).first(),
    {context: s.context, recorder, label, timeout: 20000});

  async function holidayPopupAtTarget() {
    const frame = await scheduleSetting();
    const popup = await popupFrom(frame, 'a[onclick*="/schedule/HolidaySetting"]', 'holiday-setting');
    await popup.locator('input[name="holiday_name"]').waitFor();
    const want = `${hYear}-${hMonth}`;
    for (let i = 0; i < 30 && (await popup.locator('span.title').first().innerText()).trim() !== want; i++) {
      await navigates(popup, () => popup.locator('a[href*="delta=1&bFirstDisp=0"]').first().click());
    }
    h.assert((await popup.locator('span.title').first().innerText()).trim() === want, 'Holiday Setting did not reach the target month');
    return popup;
  }
  const [hYear, hMonth, hDay] = holidayDate.split('-').map(Number);
  const holidayBox = popup => popup.locator(`input[type="checkbox"][name="sdate_${hMonth}_${hDay}"]`);
  const holidayRows = () => sql.rows(`SELECT holiday_name FROM scheduleholiday WHERE sdate=${q(holidayDate)}`);

  await s.step('Holiday Setting refuses an empty name, then saves the owned far-future holiday', async () => {
    const popup = await holidayPopupAtTarget();
    await holidayBox(popup).check();
    const seen = await h.withExpectedDialogs(popup, async () => {
      await popup.locator(`input[type="button"][value="${LABEL.save}"]`).click();
    });
    h.assert(seen.length === 1 && seen[0].text === LABEL.holidayAlert, 'An empty holiday name did not raise exactly the validation alert');
    h.assert(holidayRows().length === 0, 'A refused holiday save wrote a row');
    await popup.locator('input[name="holiday_name"]').fill(holidayName);
    await navigates(popup, () => popup.locator(`input[type="button"][value="${LABEL.save}"]`).click());
    h.assert(JSON.stringify(holidayRows()) === JSON.stringify([[holidayName]]), 'The holiday row was not saved with the typed name');
    h.assert((await holidayBox(popup).locator('xpath=..').innerText()).includes(holidayName), 'The saved holiday is not shown on its calendar day');
    await popup.close();
  });

  const codeRow = () => sql.rows(`SELECT code,description,duration,color,confirm,bookinglimit FROM scheduletemplatecode
    WHERE description LIKE ${q(`${marker}%`)}`);
  async function codePopup() {
    const frame = await scheduleSetting();
    const popup = await popupFrom(frame, 'a[onclick*="/schedule/TemplateCodeSetting"]', 'template-code-setting');
    await popup.locator('form[name="addtemplatecode"] #code').waitFor();
    return popup;
  }
  async function editCode(popup, value) {
    await popup.locator('form[name="deletetemplatecode"] select[name="code"]').selectOption(value);
    await navigates(popup, () => popup.locator(`form[name="deletetemplatecode"] input[type="submit"][value="${LABEL.codeEdit}"]`).click());
  }
  const saveCode = popup => popup.locator(`form[name="addtemplatecode"] input[type="button"][value="${LABEL.codeSave}"]`);

  await s.step('Template Code Setting refuses a non-numeric booking limit, saves the owned code and reloads it for Edit', async () => {
    const popup = await codePopup();
    const form = popup.locator('form[name="addtemplatecode"]');
    await form.locator('#code').fill(code);
    await form.locator('#description').fill(codeDesc);
    await form.locator('#duration').fill('20');
    await form.locator('#color').fill(codeColor);
    await form.locator('#bookinglimit').fill('two');
    await form.locator('input[name="confirm"][value="No"]').check();
    const seen = await h.withExpectedDialogs(popup, async () => { await saveCode(popup).click(); });
    h.assert(seen.length === 1 && seen[0].text === LABEL.codeAlert, 'A non-numeric booking limit did not raise exactly the validation alert');
    h.assert(codeRow().length === 0, 'A refused template code save wrote a row');
    await form.locator('#bookinglimit').fill('2');
    await navigates(popup, () => saveCode(popup).click());
    h.assert(JSON.stringify(codeRow()) === JSON.stringify([[code, codeDesc, '20', codeColor, 'No', '2']]),
      'The template code row does not hold the typed code, description, duration, colour, limit type and booking limit');
    await editCode(popup, code);
    h.assert(await form.locator('#code').inputValue() === code && await form.locator('#description').inputValue() === codeDesc
      && await form.locator('#duration').inputValue() === '20' && await form.locator('#color').inputValue() === codeColor
      && await form.locator('#bookinglimit').inputValue() === '2'
      && await form.locator('input[name="confirm"][value="No"]').isChecked(), 'Edit did not reload the saved template code');
    await popup.close();
  });

  let settingFrame;
  await s.step('the week-setting template list previews the owned template in the owned code\'s colour and description', async () => {
    settingFrame = await scheduleSetting();
    await frameNavigation(admin, settingFrame, () => settingFrame.locator('select[name="provider_no"]').selectOption(owner));
    h.assert(/\/schedule\/TemplateApplying/.test(settingFrame.url()), 'Selecting the provider did not open the week setting step');
    const option = settingFrame.locator(`select[name="mytemplate"] option[value="${templateName}"]`);
    h.assert(await option.count() === 1, 'The week setting does not list the provider\'s day template');
    const [preview] = await Promise.all([
      admin.waitForResponse(r => new URL(r.url()).pathname.endsWith('/schedule/DisplayTemplate')),
      option.click(),
    ]);
    h.assert(preview.status() === 200, `The template preview answered HTTP ${preview.status()}`);
    const cells = settingFrame.locator(`#template td[title="${codeDesc}"]`);
    await cells.first().waitFor({timeout: 20000});
    h.assert(await cells.count() === 8, 'The preview did not paint exactly the eight owned-code slots');
    h.assert((await cells.first().getAttribute('bgcolor')) === codeColor && (await cells.first().innerText()).trim() === code,
      'The preview does not show the owned code in its colour');
  });

  const dateRows = (where = '1=1') => sql.rows(`SELECT available,hour,status FROM scheduledate WHERE provider_no=${q(owner)}
    AND sdate=${q(holidayDate)} AND ${where} ORDER BY id`);
  await s.step('the calendar step shows the owned holiday and the date popup saves that day as unavailable', async () => {
    const f = settingFrame;
    for (const [name, value] of [['syear', hYear], ['smonth', hMonth], ['sday', 1], ['eyear', hYear], ['emonth', hMonth], ['eday', 28]]) {
      await f.locator(`input[name="${name}"]`).fill(String(value));
    }
    await f.locator('select[name="mytemplate"]').selectOption(templateName);
    await f.locator(`input[name="check${dayName}"]`).check();
    await f.locator(`input[name="${dayName}to1"]`).click();
    await frameNavigation(admin, f, () => f.locator('input[type="submit"]').first().click());
    h.assert(/\/schedule\/CreateDate/.test(f.url()), 'Next did not advance to the calendar step');
    h.assert(JSON.stringify(dateRows()) === JSON.stringify([['1', templateName, 'A']]), 'The week setting did not schedule the owned template on the target day');
    const cell = f.locator(`a[onclick*="/schedule/DatePopup"][onclick*="&day=${hDay}&"]`);
    const cellText = await cell.innerText();
    h.assert(cellText.includes(holidayName) && cellText.includes(templateName), 'The calendar day does not show the owned holiday and template');
    const popup = await ui.clickOpensPopup(admin, cell, {context: s.context, recorder, label: 'schedule-date-popup', timeout: 20000});
    await popup.locator('select[name="hour"]').waitFor();
    h.assert(await popup.locator('input[name="available"][value="1"]').isChecked(), 'The date popup did not load the day as available');
    await popup.locator('input[name="available"][value="0"]').check();
    const reloaded = admin.waitForEvent('framenavigated', {predicate: candidate => candidate === f, timeout: 20000});
    reloaded.catch(() => {});
    await popup.locator(`input[type="button"][value="${LABEL.dateSave}"]`).click();
    await popup.waitForEvent('close', {timeout: 20000}).catch(() => {});
    h.assert(popup.isClosed(), 'The date popup did not close after saving');
    await reloaded;
    h.assert(JSON.stringify(dateRows()) === JSON.stringify([['1', templateName, 'D'], ['0', templateName, 'A']]),
      'DateSave did not supersede the day with an unavailable entry');
    await h.assertNotErrorPage(f, 'calendar after the date popup saved');
  });

  await s.step('Holiday Setting deletes the owned holiday', async () => {
    const popup = await holidayPopupAtTarget();
    await holidayBox(popup).check();
    await navigates(popup, () => popup.locator(`input[type="button"][value="${LABEL.delete}"]`).click());
    h.assert(holidayRows().length === 0, 'Delete did not remove the holiday row');
    h.assert(!(await holidayBox(popup).locator('xpath=..').innerText()).includes(holidayName), 'The deleted holiday is still shown');
    await popup.close();
  });

  await s.step('Template Code Setting deletes the owned code', async () => {
    const popup = await codePopup();
    await editCode(popup, code);
    await navigates(popup, () => popup.locator(`form[name="addtemplatecode"] input[type="button"][value="${LABEL.codeDelete}"]`).click());
    h.assert(codeRow().length === 0, 'Delete did not remove the template code row');
    h.assert(await popup.locator(`form[name="deletetemplatecode"] select[name="code"] option[value="${code}"]`).count() === 0,
      'The deleted code is still offered');
    await popup.close();
  });

  await s.step('Appointment Status Setting edits a status label and colour, disables and enables it, then restores it', async () => {
    const list = () => openAdminSection(admin, '/appointment/apptStatusSetting?dispatch=view', 'table.borderAll');
    let frame = await list();
    async function edit(desc, color) {
      await frameNavigation(admin, frame, () => frame.locator(`a[href*="dispatch=modify&id=${statusId}"]`).first().click());
      h.assert(await frame.locator('#apptStatus').inputValue() === statusCode, 'The status edit form opened another status');
      await frame.locator('#apptDesc').fill(desc);
      await frame.locator('#apptColor').fill(color);
      await frameNavigation(admin, frame, () => frame.locator('form input[type="submit"]').click());
      await frame.locator('table.borderAll').waitFor();
    }
    const desc = `PW${hex}`;
    await edit(desc, '#123456');
    h.assert(JSON.stringify(sql.rows(`SELECT description,color FROM appointment_status WHERE id=${Number(statusId)}`)) === JSON.stringify([[desc, '#123456']]),
      'The status label and colour were not saved');
    h.assert(await frame.locator('tr', {hasText: desc}).count() === 1, 'The status list does not show the new label');
    const toggle = label => frame.locator(`form:has(input[name="id"][value="${statusId}"]) button`, {hasText: label});
    await frameNavigation(admin, frame, () => toggle(LABEL.disable).click());
    await expectValue(sql, `SELECT active FROM appointment_status WHERE id=${Number(statusId)}`, '0', 'Disable did not deactivate the status');
    await frameNavigation(admin, frame, () => toggle(LABEL.enable).click());
    await expectValue(sql, `SELECT active FROM appointment_status WHERE id=${Number(statusId)}`, '1', 'Enable did not reactivate the status');
    frame = await list();
    await edit(statusDesc, statusColor);
    h.assert(statusSnapshot() === originalStatus, 'Editing back did not restore the status label and colour');
  });

  const typeRows = () => sql.rows(`SELECT id,name,duration,reason,notes,location,resources FROM appointmentType WHERE name=${q(typeName)}`);
  let typeFrame;
  await s.step('Appointment Type List saves the owned type and edits it', async () => {
    typeFrame = await openAdminSection(admin, '/appointment/appointmentTypeAction', '#appointmentTypeName');
    const f = typeFrame;
    await f.locator('#appointmentTypeName').fill(typeName);
    await f.locator('#appointmentTypeDuration').fill('25');
    await f.locator('#appointmentTypeReason').fill(`${marker} reason`);
    await f.locator('#appointmentTypeNotes').fill(`${marker} notes`);
    await f.locator('#appointmentTypeLocation').fill(`${marker} room`);
    await f.locator('#appointmentTypeResources').fill('PWROOM');
    await frameNavigation(admin, f, () => f.locator('form input[type="submit"]').first().click());
    let rows = typeRows();
    h.assert(rows.length === 1 && JSON.stringify(rows[0].slice(1)) === JSON.stringify([typeName, '25', `${marker} reason`,
      `${marker} notes`, `${marker} room`, 'PWROOM']), 'The appointment type row does not hold the typed values');
    await frameNavigation(admin, f, () => f.locator(`a[href*="oper=edit"][href*="no=${rows[0][0]}"]`).click());
    h.assert(await f.locator('#appointmentTypeName').inputValue() === typeName, 'Edit did not load the owned type');
    await f.locator('#appointmentTypeNotes').fill(`${marker} notes edited`);
    await f.locator('#appointmentTypeDuration').fill('30');
    await frameNavigation(admin, f, () => f.locator('form input[type="submit"]').first().click());
    rows = typeRows();
    h.assert(rows.length === 1 && rows[0][2] === '30' && rows[0][4] === `${marker} notes edited`, 'Edit did not update the type in place');
  });

  await s.step('the add-appointment form offers the owned type and fills its defaults', async () => {
    const slot = daySheet.locator('a.adhour:not([onclick*="\',\'Yes\',"]):not([onclick*="\',\'Day\',"]):not([onclick*="\',\'Wk\',"]):not([onclick*="\',\'Onc\',"])').first();
    const popup = await ui.clickOpensPopup(daySheet, slot, {context: s.context, recorder, label: 'add-appointment', timeout: 20000});
    await popup.locator('#type-button').waitFor({timeout: 20000});
    await popup.locator('#type-button').click();
    const item = popup.locator('#type-menu li', {hasText: typeName});
    h.assert(await item.count() === 1, 'The add form type menu does not offer the owned type');
    await item.click();
    h.assert(await popup.locator('#duration').inputValue() === '30'
      && await popup.locator('#reason').inputValue() === `${marker} reason`
      && await popup.locator('textarea[name="notes"]').inputValue() === `${marker} notes edited`
      && await popup.locator('input[name="resources"]').inputValue() === 'PWROOM'
      && await popup.locator('#type').inputValue() === typeName, 'Choosing the owned type did not fill its defaults');
    await popup.locator('#backButton').click();
    await popup.waitForEvent('close', {timeout: 20000}).catch(() => {});
    h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE reason LIKE ${q(`${marker}%`)}`) === '0', 'Previewing the type booked an appointment');
  });

  await s.step('Appointment Type List deletes the owned type after confirmation', async () => {
    const f = typeFrame;
    const id = typeRows()[0][0];
    const seen = await h.withExpectedDialogs(admin, async () => {
      await frameNavigation(admin, f, () => f.locator(`form.delete-appointment-type:has(input[name="no"][value="${id}"]) button`).click());
    });
    h.assert(seen.length === 1 && seen[0].type === 'confirm', 'Deleting the type did not ask for confirmation exactly once');
    h.assert(typeRows().length === 0, 'Delete did not remove the appointment type');
  });

  await s.step('GET cannot save a holiday, template code, date, status or appointment type', async () => {
    const probes = [
      `/schedule/HolidaySetting?dboperation=${LABEL.save}&holiday_name=x&sdate_1_1=${holidayDate}`,
      `/schedule/TemplateCodeSetting?dboperation=${LABEL.codeSave}&code=${code}&description=x&bookinglimit=1`,
      `/schedule/DateSave?provider_no=${owner}&date=${holidayDate}&Submit=${LABEL.dateSave}&available=1&hour=${templateName}`,
      `/appointment/apptStatusSetting?dispatch=update&id=${statusId}&apptDesc=x&apptColor=%23000000`,
      `/appointment/appointmentTypeAction?oper=save&name=${encodeURIComponent(typeName)}&duration=15`,
    ];
    for (const probe of probes) {
      const response = await s.context.request.get(h.appUrl(s.config.baseUrl, probe), {maxRedirects: 0});
      h.assert(response.status() === 405, `GET ${probe.split('?')[0]} answered HTTP ${response.status()}`);
    }
    h.assert(holidayRows().length === 0 && codeRow().length === 0 && typeRows().length === 0
      && statusSnapshot() === originalStatus && dateRows().length === 2, 'A GET probe wrote a row');
  });

  await s.step('a template code description with an apostrophe survives Edit', async () => {
    const popup = await codePopup();
    const form = popup.locator('form[name="addtemplatecode"]');
    const desc = `${marker} O'Neil`;
    await form.locator('#code').fill(code);
    await form.locator('#description').fill(desc);
    await form.locator('#bookinglimit').fill('1');
    await navigates(popup, () => saveCode(popup).click());
    h.assert(JSON.stringify(codeRow().map(r => r[1])) === JSON.stringify([desc]), 'The apostrophe description was not saved');
    await editCode(popup, code);
    h.assert(await form.locator('#description').inputValue() === desc,
      'Edit reloaded a template code description with an apostrophe truncated (single-quoted attribute encoded for HTML content)');
    await navigates(popup, () => popup.locator(`form[name="addtemplatecode"] input[type="button"][value="${LABEL.codeDelete}"]`).click());
    h.assert(codeRow().length === 0, 'Delete did not remove the apostrophe template code');
    await popup.close();
  });
}

if (require.main === module) runWorkflow('schedule-admin-settings', workflow, {openPatient: false});
module.exports = {workflow};
