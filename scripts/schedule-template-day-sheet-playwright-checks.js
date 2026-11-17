#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * schedule-template-day-sheet — coverage plan §2.3: a day template applied in Schedule Setting
 * reaches the provider's own day sheet. schedule-setting applies an existing template and checks
 * the generated dates, and schedule-admin-settings checks the wizard's own preview and calendar.
 * Neither opens the day sheet that the template was applied to.
 *
 * User path, as a THROWAWAY login that is a doctor plus an owned schedule-administrator role (as in
 * schedule-admin-settings), on its own schedule only:
 *   Administration ▸ Schedule Management ▸ Schedule Setting ▸ Template Code Setting (the two owned codes);
 *   Schedule Setting ▸ the throwaway ▸ week setting
 *     (template A on today's and tomorrow's weekdays, today to tomorrow) ▸ Next ▸ calendar;
 *   Schedule ▸ today, tomorrow;
 *   calendar ▸ today's date popup ▸ template B ▸ Save; Schedule ▸ today, tomorrow.
 *
 * Asserted: Template Code Setting stores both owned codes as typed. Next stores one active scheduledate
 * row with template A for each of the two days; today's and tomorrow's day sheets then show A's code
 * letter, colour and description on exactly the 09:00 to 10:45 slots, and on no other slot. After the
 * date popup puts template B on today, today's day sheet shows B's code on exactly 12:00 to 12:45, with
 * A gone, while tomorrow keeps its one template A row, not rewritten (#4880), and its day sheet still
 * shows A unchanged.
 *
 * Fixtures: the throwaway login with an owned schedule-administrator role and ProviderPreference
 * (08:00-18:00 in 15-minute slots, its own group), and two owned day templates, seeded by SQL; the two
 * template codes (unused code characters, marker descriptions) are saved through Template Code Setting,
 * because the day sheet reads codes from a cache that SQL writes never evict. Cleanup removes the codes,
 * the throwaway's templates, scheduledate, rschedule and ProviderPreference rows, and the role, then the
 * throwaway.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { bundleMessage, throwawayLoginFixture } = require('./lib/throwaway-login-fixture');
const { runWorkflow } = require('./lib/workflow-session');
const { openAdminSection, frameNavigation, navigates } = require('./schedule-admin-settings-playwright-checks');

const TIMEOUT = 20000;
const WEEKDAYS = ['sun', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat'];
// Never a code already present in either case: the column compares case-insensitively.
const CODE_CANDIDATES = ['Q', 'Z', 'X', 'Y', 'J', 'K', '7', '8', '9', '0', '4', '5'];
const DATE_SAVE = bundleMessage('schedule.scheduledatepopup.btnSave', 'Save');
const CODE_SAVE = bundleMessage('schedule.scheduletemplatecodesetting.btnSave', 'Save');

/** Quarter-hour labels from start for count slots, e.g. ('09:00', 3) -> 09:00, 09:15, 09:30. */
function quarterHours(start, count) {
  const [hours, minutes] = start.split(':').map(Number);
  return Array.from({ length: count }, (_, i) => {
    const total = hours * 60 + minutes + 15 * i;
    return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
  });
}

async function workflow(s) {
  const { sql, marker, provider, recorder } = s;
  const hex = marker.slice(-16);
  const q = h.sqlString;

  // ---- fixtures -----------------------------------------------------------------------------
  // Taken from the end of the list: schedule-admin-settings takes the first free one, so the two never pick the same.
  const free = CODE_CANDIDATES.filter(candidate => sql.value(`SELECT COUNT(*) FROM scheduletemplatecode WHERE code=${q(candidate)}`) === '0').reverse();
  if (free.length < 2) throw new h.SkipCheck('Fewer than two candidate template code characters are unused');
  // A: eight quarter-hours from 09:00. B: four from 12:00. Each timecode covers the day's 96 quarter-hours.
  const A = { code: free[0], desc: `${marker} A`, color: '#C0FFEE', name: `PWA${hex.slice(0, 11)}`,
    timecode: '_'.repeat(36) + free[0].repeat(8) + '_'.repeat(52), slots: quarterHours('09:00', 8) };
  const B = { code: free[1], desc: `${marker} B`, color: '#FFD8B0', name: `PWB${hex.slice(0, 11)}`,
    timecode: '_'.repeat(48) + free[1].repeat(4) + '_'.repeat(44), slots: quarterHours('12:00', 4) };
  const [[today, tomorrow, todayDow, tomorrowDow]] = sql.rows(`SELECT CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 DAY),
    DAYOFWEEK(CURDATE()), DAYOFWEEK(DATE_ADD(CURDATE(), INTERVAL 1 DAY))`);
  const days = [[today, WEEKDAYS[Number(todayDow) - 1]], [tomorrow, WEEKDAYS[Number(tomorrowDow) - 1]]];

  const fixture = throwawayLoginFixture({ sql, marker, provider, testUser: s.config.testUser });
  // The seeded admin role carries _site_access_privacy; the throwaway is a doctor plus an owned
  // schedule-administrator role instead, as in schedule-admin-settings. It is created with exactly
  // those two roles, so no inherited role has to be removed from it afterwards.
  const role = `PW${hex}`;
  let owner = null;
  // Cleanups run in reverse: schedule rows go before the throwaway login.
  s.cleanup(() => fixture.cleanup());
  s.cleanup(() => {
    const statements = [
      `DELETE FROM scheduletemplatecode WHERE code IN (${q(A.code)},${q(B.code)}) AND description LIKE ${q(`${marker}%`)}`,
      `DELETE FROM secObjPrivilege WHERE roleUserGroup=${q(role)}`,
      `DELETE FROM secRole WHERE role_name=${q(role)}`,
    ];
    if (owner) {
      for (const table of ['scheduletemplate', 'scheduledate', 'rschedule']) statements.push(`DELETE FROM ${table} WHERE provider_no=${q(owner)}`);
      statements.push(`DELETE FROM ProviderPreference WHERE providerNo=${q(owner)}`);
    }
    sql.execute(statements.join(';'));
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM scheduletemplatecode WHERE description LIKE ${q(`${marker}%`)})
      + (SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${q(role)})
      + (SELECT COUNT(*) FROM secRole WHERE role_name=${q(role)})
      + (SELECT COUNT(*) FROM ProviderPreference WHERE providerNo=${q(owner || '')})
      + (SELECT COUNT(*) FROM scheduletemplate WHERE provider_no=${q(owner || '')})
      + (SELECT COUNT(*) FROM scheduledate WHERE provider_no=${q(owner || '')})
      + (SELECT COUNT(*) FROM rschedule WHERE provider_no=${q(owner || '')})`) === '0', 'Owned schedule rows were not removed');
  });
  h.assert(sql.value(`SELECT COUNT(*) FROM secUserRole WHERE provider_no=${q(provider)} AND role_name='doctor' AND activeyn=1`) === '1',
    'The test login is not a doctor, so a throwaway copy of it would have no schedule of its own');
  h.assert(sql.value(`SELECT COUNT(*) FROM secRole WHERE role_name=${q(role)}`) === '0', 'The owned role already exists');
  sql.execute(`INSERT INTO secRole (role_name,description) VALUES (${q(role)},${q(`${marker} schedule admin`)});
    INSERT INTO secObjPrivilege (roleUserGroup,objectName,privilege,priority,provider_no) VALUES (${q(role)},'_admin.schedule','x',0,${q(provider)})`);
  fixture.create({ roleNames: ['doctor', role] });
  owner = fixture.providerNo;
  h.assert(sql.value(`SELECT COUNT(*) = 2 AND SUM(role_name IN ('doctor',${q(role)})) = 2 FROM secUserRole WHERE provider_no=${q(owner)}`) === '1',
    'The throwaway does not hold exactly the doctor and owned schedule roles');
  sql.execute(`INSERT INTO ProviderPreference (providerNo,startHour,endHour,everyMin,myGroupNo,colourTemplate,printQrCodeOnPrescriptions,
      lastUpdated,appointmentScreenLinkNameDisplayLength,defaultDoNotDeleteBilling)
      VALUES (${q(owner)},8,18,15,${q(owner)},'deepblue',0,NOW(),20,0);
    INSERT INTO scheduletemplate (provider_no,name,summary,timecode) VALUES
      (${q(owner)},${q(A.name)},${q(`${marker} A day`)},${q(A.timecode)}), (${q(owner)},${q(B.name)},${q(`${marker} B day`)},${q(B.timecode)})`);
  h.assert(sql.value(`SELECT COUNT(*) FROM scheduletemplate WHERE provider_no=${q(owner)}`) === '2', 'The owned day templates were not seeded');

  // ---- the throwaway's schedule and administration shell -----------------------------------------
  const context = await h.newContext(s.context.browser(), s.config);
  context.setDefaultTimeout(TIMEOUT);
  context.on('page', page => h.wireStrictPage(page, 'schedule-template-day-sheet', recorder));
  const schedule = await h.login(context, { ...s.config, testUser: fixture.username }, recorder, { label: 'throwaway-login' });
  // providercontrol renders an empty document without the landing parameters, so every day sheet
  // below is the exact URL the login landed on with only its date changed.
  const landing = new URL(schedule.url());
  const { page: admin, isPopup } = await ui.clickOpensPopupOrNavigates(schedule, schedule.locator('#admin-panel,#admin2').first(),
    { context, recorder, label: 'administration', timeout: TIMEOUT });
  const daySheet = isPopup ? schedule : await context.newPage();

  /** Open the throwaway's day sheet for one date and list the slot times showing each code. */
  async function codedSlots(date, templates) {
    const [year, month, day] = date.split('-').map(Number);
    const url = new URL(landing);
    url.searchParams.set('year', String(year));
    url.searchParams.set('month', String(month));
    url.searchParams.set('day', String(day));
    await daySheet.goto(url.toString(), { waitUntil: 'domcontentloaded' });
    await daySheet.locator('a.adhour').first().waitFor({ timeout: TIMEOUT });
    await h.assertNotErrorPage(daySheet, `day sheet ${date}`);
    const found = {};
    for (const t of templates) {
      const cells = daySheet.locator(`td[title="${t.desc}"]`);
      found[t.code] = await cells.evaluateAll((tds, expected) => tds.map(td => ({
        time: td.closest('tr')?.querySelector('a.adhour')?.textContent.trim() || '?',
        color: (td.getAttribute('bgcolor') || '').toUpperCase(),
        text: td.textContent.trim(),
        right: (td.getAttribute('bgcolor') || '').toUpperCase() === expected.color && td.textContent.trim() === expected.code,
      })), { color: t.color.toUpperCase(), code: t.code });
    }
    return found;
  }
  const times = cells => JSON.stringify(cells.map(cell => cell.time));

  const scheduleSetting = () => openAdminSection(admin, '/schedule/TemplateSetting', 'select[name="provider_no"]');
  const dateRows = date => sql.rows(`SELECT available,hour,status FROM scheduledate WHERE provider_no=${q(owner)} AND sdate=${q(date)} ORDER BY id`);

  // The day sheet reads template codes through ScheduleTemplateCodeDao.findAll(), a 30-minute cache that
  // only the DAO's own writes evict. Codes seeded by SQL stay invisible to it (no colour, no description),
  // so the two owned codes are saved through Template Code Setting, as an administrator would.
  await s.step('Template Code Setting saves the two owned codes', async () => {
    const frame = await scheduleSetting();
    const popup = await ui.clickOpensPopup(admin, frame.locator('a[onclick*="/schedule/TemplateCodeSetting"]').first(),
      { context, recorder, label: 'template-code-setting', timeout: TIMEOUT });
    const form = popup.locator('form[name="addtemplatecode"]');
    for (const t of [A, B]) {
      await form.locator('#code').fill(t.code);
      await form.locator('#description').fill(t.desc);
      await form.locator('#duration').fill('15');
      await form.locator('#color').fill(t.color);
      await form.locator('#bookinglimit').fill('1');
      await form.locator('input[name="confirm"][value="No"]').check();
      await navigates(popup, () => form.locator(`input[type="button"][value="${CODE_SAVE}"]`).click());
    }
    await popup.close();
    h.assert(JSON.stringify(sql.rows(`SELECT code,description,duration,color FROM scheduletemplatecode
      WHERE description LIKE ${q(`${marker}%`)} ORDER BY description`)) === JSON.stringify([A, B].map(t => [t.code, t.desc, '15', t.color])),
    'The owned template codes were not saved with the typed code, description, duration and colour');
  });

  let settingFrame;
  await s.step('the week setting applies template A to today and tomorrow, one active row per day', async () => {
    // ProviderDao.getActiveProviders() is a five-minute Spring cache evicted only by provider
    // admin writes, so a provider row seeded by SQL appears once the entry expires.
    const deadline = Date.now() + 330000;
    settingFrame = await scheduleSetting();
    while (await settingFrame.locator(`select[name="provider_no"] option[value="${owner}"]`).count() === 0) {
      h.assert(Date.now() < deadline, 'Schedule Setting never offered the owned provider');
      await new Promise(resolve => setTimeout(resolve, 15000));
      settingFrame = await scheduleSetting();
    }
    const f = settingFrame;
    await frameNavigation(admin, f, () => f.locator('select[name="provider_no"]').selectOption(owner));
    h.assert(/\/schedule\/TemplateApplying/.test(f.url()), 'Selecting the provider did not open the week setting step');
    const [sy, sm, sd] = today.split('-').map(Number);
    const [ey, em, ed] = tomorrow.split('-').map(Number);
    for (const [name, value] of [['syear', sy], ['smonth', sm], ['sday', sd], ['eyear', ey], ['emonth', em], ['eday', ed]]) {
      await f.locator(`input[name="${name}"]`).fill(String(value));
    }
    await f.locator('select[name="mytemplate"]').selectOption(A.name);
    for (const [, weekday] of days) {
      await f.locator(`input[name="check${weekday}"]`).check();
      await f.locator(`input[name="${weekday}to1"]`).click();
    }
    await frameNavigation(admin, f, () => f.locator('input[type="submit"]').first().click());
    h.assert(/\/schedule\/CreateDate/.test(f.url()), 'Next did not advance to the calendar step');
    for (const [date] of days) {
      h.assert(JSON.stringify(dateRows(date)) === JSON.stringify([['1', A.name, 'A']]), `The week setting did not schedule template A on ${date}`);
    }
  });

  await s.step('today\'s and tomorrow\'s day sheets show A\'s code, colour and description on exactly 09:00-10:45', async () => {
    for (const [date] of days) {
      const found = await codedSlots(date, [A, B]);
      h.assert(times(found[A.code]) === JSON.stringify(A.slots),
        `The ${date} day sheet shows code ${A.code} at ${times(found[A.code])}, not ${A.slots[0]}-${A.slots[A.slots.length - 1]}`);
      h.assert(found[A.code].every(cell => cell.right), `The ${date} day sheet does not paint code ${A.code} in its colour with its letter`);
      h.assert(found[B.code].length === 0, `The ${date} day sheet shows template B's code before B was applied`);
    }
  });

  await s.step('the date popup puts template B on today: today shows B on exactly 12:00-12:45 and A is gone', async () => {
    const f = settingFrame;
    const [, , todayDay] = today.split('-').map(Number);
    const cell = f.locator(`a[onclick*="/schedule/DatePopup"][onclick*="&day=${todayDay}&"]`);
    const popup = await ui.clickOpensPopup(admin, cell, { context, recorder, label: 'schedule-date-popup', timeout: TIMEOUT });
    await popup.locator('select[name="hour"]').waitFor();
    h.assert(await popup.locator('input[name="available"][value="1"]').isChecked(), 'The date popup did not load today as available');
    await popup.locator('select[name="hour"]').selectOption(B.name);
    const reloaded = admin.waitForEvent('framenavigated', { predicate: candidate => candidate === f, timeout: TIMEOUT });
    reloaded.catch(() => {});
    await popup.locator(`input[type="button"][value="${DATE_SAVE}"]`).click();
    await popup.waitForEvent('close', { timeout: TIMEOUT }).catch(() => {});
    h.assert(popup.isClosed(), 'The date popup did not close after saving');
    await reloaded;
    h.assert(JSON.stringify(dateRows(today)) === JSON.stringify([['1', A.name, 'D'], ['1', B.name, 'A']]),
      'DateSave did not replace today\'s template A with B');
    const found = await codedSlots(today, [A, B]);
    h.assert(times(found[B.code]) === JSON.stringify(B.slots) && found[B.code].every(cell => cell.right),
      `Today's day sheet shows code ${B.code} at ${times(found[B.code])}, not ${B.slots[0]}-${B.slots[B.slots.length - 1]} in its colour`);
    h.assert(found[A.code].length === 0, 'Today\'s day sheet still shows template A after B replaced it');
  });

  await s.step('tomorrow\'s day sheet still shows template A, untouched by today\'s change', async () => {
    // DateSave redisplays the calendar with a display-only request (#4880), so CreateDate must not run again:
    // tomorrow keeps exactly the one row Next wrote, with no D row from a rewrite.
    h.assert(JSON.stringify(dateRows(tomorrow)) === JSON.stringify([['1', A.name, 'A']]),
      `Today's date popup save rewrote tomorrow's rule-made row (rows: ${JSON.stringify(dateRows(tomorrow))})`);
    const found = await codedSlots(tomorrow, [A, B]);
    h.assert(times(found[A.code]) === JSON.stringify(A.slots) && found[A.code].every(cell => cell.right) && found[B.code].length === 0,
      `Tomorrow's day sheet changed: ${A.code} at ${times(found[A.code])}, ${B.code} on ${found[B.code].length} slot(s)`);
  });
}

if (require.main === module) runWorkflow('schedule-template-day-sheet', workflow, { openPatient: false });
module.exports = { workflow };
