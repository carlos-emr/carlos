#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Booking with the appointment Location List (#3982): what happens to a booking's location once it
 * is booked, driven from the day sheet as a throwaway login whose "My Group" holds two owned
 * providers. appointment-settings covers the Locations tab and a single booking; this check covers
 * the paths that keep, recreate, move or refuse a booking's location afterwards.
 *
 * User path: the Locations tab (the test login adds two marker locations, then disables and
 * re-enables them as the steps need) ▸ throwaway day sheet, tomorrow ▸ slot ▸ Add Appointment ▸
 * free-text location ▸ (first location enabled) ▸ patient search ▸ Add; Add Appointment ▸ Location
 * dropdown ▸ patient search ▸ Create New ▸ Add Record (back to the booking form); Add Appointment ▸
 * Location dropdown ▸ Add; Edit ▸ Update after its location was disabled; Edit ▸ Group Action ▸
 * tick the second provider ▸ Group Update; Add Appointment ▸ choose a location that is disabled
 * before Add; Edit ▸ Copy ▸ another slot (auto-paste) ▸ Add; Edit ▸ Cut ▸ another slot (auto-paste)
 * ▸ Add.
 *
 * Asserts each appointment row's location text and locationCode after every action: text typed
 * while no location was enabled survives a patient search that re-renders the form with the
 * dropdown, as the selected legacy option, and is saved; the chosen location survives registering a
 * new patient from the booking form; a booking stores the item id with a snapshot of its label and
 * the day sheet marks it; disabling the item changes no booking, and an edit saved unchanged keeps
 * both; Group Update gives every member, including a provider ticked for the first time, the edited
 * appointment's location even after its item was disabled (before the fix, that member's preflight
 * refused the whole update with 409); a choice disabled while the add form was open is refused with
 * HTTP 409, says why, keeps the draft and writes nothing; Copy and Paste keep an active location's
 * code; Cut and Paste of a booking whose item was disabled since keep its name as legacy text,
 * without the code.
 *
 * Fixtures: the clinic's Location List is clinic-wide, so run only in a disposable database with
 * EXCLUSIVE=1 and CARLOS_DISPOSABLE_VM=true. The two marker locations are added and finally disabled
 * through the application, which also clears its lookup-list cache, and deleted in cleanup; the
 * clinic's own locations are left as they are. Needs a clinic without a site setup (multisites,
 * scheduleSiteID, CAISI program locations): with one, the booking screens keep their own control
 * and the check is skipped before it creates anything. The throwaway login, the second provider,
 * the marker group, every row of the two owned providers and the patient the add-patient detour
 * registers (with its child rows) are deleted in cleanup.
 */
const {randomInt} = require('node:crypto');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const {bundleMessage, throwawayLoginFixture} = require('./lib/throwaway-login-fixture');
const {failureMark, consumeExpectedFailure} = require('./lib/concurrency-support');
const {runWorkflow} = require('./lib/workflow-session');

const LIST_NAME = 'appointmentLocationCode';
const SETTINGS = '/appointment/apptLocationSetting';
const NEXT_DAY = 'a.redArrow:has(span.fa-forward-step)';
const ADD_RECORD = /\/appointment\/AddRecord$/;
const UPDATE_RECORD = /\/appointment\/UpdateRecord$/;

function requireDisposableEnvironment(env = process.env) {
  h.assert(env.EXCLUSIVE === '1' && env.CARLOS_DISPOSABLE_VM === 'true',
    'Appointment location booking fixtures require EXCLUSIVE=1 and CARLOS_DISPOSABLE_VM=true');
}

async function waitClosed(page, label) {
  if (!page.isClosed()) await page.waitForEvent('close', {timeout: 20000}).catch(() => {});
  h.assert(page.isClosed(), `${label}: the popup did not close itself, so its save did not complete`);
}

async function workflow(s) {
  requireDisposableEnvironment();
  const {sql, marker, patient, config, recorder} = s;
  const q = h.sqlString;
  const listId = sql.value(`SELECT id FROM LookupList WHERE name=${q(LIST_NAME)}`);
  h.assert(/^[1-9]\d*$/.test(listId), 'Flyway V1.0.81 did not seed the appointmentLocationCode lookup list');
  const alpha = `${marker} Alpha`;
  const beta = `${marker} Beta`;
  const itemId = label => sql.value(`SELECT id FROM LookupListItem WHERE lookupListId=${Number(listId)} AND label=${q(label)}`);
  const activeMarkers = () => sql.rows(`SELECT id FROM LookupListItem WHERE lookupListId=${Number(listId)}
    AND label LIKE ${q(`${marker}%`)} AND active=1`).map(([id]) => id);

  // The Locations tab, as the test login (an administrator). Each change commits through
  // AppointmentLocationService and clears the server's lookup-list cache, as a click there would;
  // a direct SQL change would stay invisible to the booking screens for the cache's lifetime.
  async function locationsTab(change) {
    const page = await s.context.newPage();
    try {
      await h.gotoApp(page, config.baseUrl, SETTINGS);
      await h.assertNotErrorPage(page, 'appointment location settings');
      const form = page.locator('form[action*="apptLocationSetting"]').first();
      await form.locator('input[name="CSRF-TOKEN"]').waitFor({state: 'attached', timeout: 20000});
      const siteSetup = await page.locator('body').getAttribute('data-site-setup') === 'true';
      if (change) {
        const answer = await form.evaluate(async (node, fields) => {
          const token = node.querySelector('input[name="CSRF-TOKEN"]').value;
          const response = await fetch(node.action, {method: 'POST', credentials: 'same-origin',
            body: new URLSearchParams({...fields, 'CSRF-TOKEN': token})});
          const text = await response.text();
          return {status: response.status, saved: text.includes('alert-success'), refused: text.includes('alert-danger')};
        }, change);
        h.assert(answer.status === 200 && answer.saved && !answer.refused,
          `The Locations tab did not save ${change.dispatch}: ${JSON.stringify(answer)}`);
      }
      return {siteSetup};
    } finally {
      await page.close().catch(() => {});
    }
  }

  s.cleanup(() => {
    sql.execute(`DELETE FROM LookupListItem WHERE lookupListId=${Number(listId)} AND label LIKE ${q(`${marker}%`)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM LookupListItem WHERE label LIKE ${q(`${marker}%`)}`) === '0',
      'The marker locations were not removed');
  });
  // The patient the add-patient detour registers is named `${marker}-New`, never the owned
  // patient's exact marker. Remove it with every child row the Add action writes.
  const createdLike = q(`${marker}-%`);
  s.cleanup(() => {
    const ids = sql.rows(`SELECT demographic_no FROM demographic WHERE last_name LIKE ${createdLike}`).map(([id]) => id);
    if (!ids.length) return;
    h.assert(ids.every(id => /^[1-9]\d*$/.test(id)), 'A created patient id is invalid');
    const list = ids.join(',');
    const children = [['admission', 'client_id'], ['demographicArchive', 'demographic_no'],
      ['demographicExt', 'demographic_no'], ['demographicExtArchive', 'demographic_no'],
      ['demographiccust', 'demographic_no'], ['demographiccustArchive', 'demographic_no'],
      ['casemgmt_note_lock', 'demographic_no'], ['casemgmt_tmpsave', 'demographic_no']];
    sql.execute(children.map(([table, column]) => `DELETE FROM ${table} WHERE ${column} IN (${list})`).join(';'));
    sql.execute(`DELETE FROM demographic WHERE demographic_no IN (${list}) AND last_name LIKE ${createdLike}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE last_name LIKE ${createdLike}`) === '0',
      'The patient created from the booking form was not removed');
  });
  if ((await locationsTab(null)).siteSetup) {
    throw new h.SkipCheck('This clinic books by sites or programs, so the booking screens keep their own location control');
  }

  const fixture = throwawayLoginFixture({sql, marker, provider: s.provider, testUser: config.testUser});
  const groupName = 'PW' + marker.slice(-8);
  let second = null;
  let ownedProviders = [];
  const inOwned = () => ownedProviders.map(q).join(',');
  // Cleanups run in reverse: appointments and the group go before the throwaway login.
  s.cleanup(() => {
    h.assert(!fixture.providerNo || sql.value(`SELECT COUNT(*) FROM appointment WHERE provider_no=${q(fixture.providerNo)}`) === '0',
      'Throwaway login retained: appointment rows still reference it');
    fixture.cleanup();
  });
  s.cleanup(() => {
    if (!ownedProviders.length) return;
    h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE provider_no IN (${inOwned()})
      AND demographic_no NOT IN (0,${patient})`) === '0', 'An owned provider holds an appointment for a patient this run does not own');
    const statements = [
      `DELETE FROM appointment WHERE provider_no IN (${inOwned()})`,
      `DELETE FROM appointmentArchive WHERE provider_no IN (${inOwned()})`,
      `DELETE FROM mygroup WHERE mygroup_no=${q(groupName)}`,
      ...['ProviderPreference', 'ProviderPreferenceAppointmentScreenEForm', 'ProviderPreferenceAppointmentScreenForm',
        'ProviderPreferenceAppointmentScreenQuickLink'].map(table => `DELETE FROM ${table} WHERE providerNo IN (${inOwned()})`),
    ];
    if (second) statements.push(`DELETE FROM provider WHERE provider_no=${q(second)} AND last_name=${q(marker)}`);
    sql.execute(statements.join(';'));
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM appointment WHERE provider_no IN (${inOwned()}))
      + (SELECT COUNT(*) FROM appointmentArchive WHERE provider_no IN (${inOwned()}))
      + (SELECT COUNT(*) FROM mygroup WHERE mygroup_no=${q(groupName)})
      + (SELECT COUNT(*) FROM provider WHERE provider_no=${q(second || '')})`) === '0',
    'Owned appointment, group or provider rows were not removed');
  });
  fixture.create();
  const first = fixture.providerNo;
  for (let attempt = 0; attempt < 20 && !second; attempt++) {
    const candidate = String(randomInt(800000, 899999));
    if (candidate !== first && sql.value(`SELECT (SELECT COUNT(*) FROM provider WHERE provider_no=${q(candidate)})
      + (SELECT COUNT(*) FROM security WHERE provider_no=${q(candidate)})`) === '0') second = candidate;
  }
  h.assert(second, 'No unused provider number was found for the second provider');
  ownedProviders = [first, second];
  h.assert(sql.value(`SELECT COUNT(*) FROM mygroup WHERE mygroup_no=${q(groupName)}`) === '0', 'The marker group already exists');
  sql.execute(`INSERT INTO provider (provider_no,last_name,first_name,provider_type,specialty,sex,status,lastUpdateUser,lastUpdateDate)
      SELECT ${q(second)},${q(marker)},'Second',provider_type,specialty,sex,'1',${q(s.provider)},NOW()
      FROM provider WHERE provider_no=${q(s.provider)};
    INSERT INTO mygroup (mygroup_no,provider_no,last_name,first_name) VALUES
      (${q(groupName)},${q(first)},${q(marker)},'Throwaway'),
      (${q(groupName)},${q(second)},${q(marker)},'Second');
    INSERT INTO ProviderPreference (providerNo,startHour,endHour,everyMin,myGroupNo,colourTemplate,printQrCodeOnPrescriptions,
      lastUpdated,appointmentScreenLinkNameDisplayLength,defaultDoNotDeleteBilling)
      VALUES (${q(first)},8,18,15,${q(groupName)},'deepblue',0,NOW(),20,0)`);
  h.assert(sql.value(`SELECT COUNT(*) FROM mygroup WHERE mygroup_no=${q(groupName)}`) === '2', 'The marker group was not created');

  const located = where => sql.rows(`SELECT appointment_no,provider_no,start_time,IFNULL(location,''),IFNULL(locationCode,'')
    FROM appointment WHERE provider_no IN (${inOwned()}) AND ${where} ORDER BY provider_no,start_time,appointment_no`);
  const locationOf = id => JSON.stringify(located(`appointment_no=${Number(id)}`).map(row => [row[3], row[4]]));
  const archived = id => Number(sql.value(`SELECT COUNT(*) FROM appointmentArchive WHERE appointment_no=${Number(id)}
    AND provider_no IN (${inOwned()})`));
  const surname = sql.value(`SELECT last_name FROM demographic WHERE demographic_no=${patient}`);

  // The throwaway's own session: its day sheet shows the marker group's two columns.
  const context = await h.newContext(s.context.browser(), config);
  s.cleanup(() => context.close().catch(() => {}));
  context.setDefaultTimeout(20000);
  context.on('page', page => h.wireStrictPage(page, 'location-booking', recorder));
  const daySheet = await h.login(context, {...config, testUser: fixture.username}, recorder, {label: 'throwaway-login'});

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

  async function book(providerNo, time, locationId, reason) {
    const popup = await openAdd(providerNo, time);
    await pickPatient(popup);
    await popup.locator('select#locationCode').selectOption(locationId);
    await popup.locator('#reason').fill(reason);
    await popup.locator('#addButton').click();
    await waitClosed(popup, 'Add Appointment');
    const rows = located(`reason=${q(reason)}`);
    h.assert(rows.length === 1, `The booking "${reason}" was not written exactly once`);
    await apptLink(rows[0][0]).waitFor({timeout: 20000});
    return rows[0][0];
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

  // Disables every marker location still enabled, through the application, so its cache never
  // keeps a marker location bookable after the run; a failing step must still leave them disabled.
  async function disableMarkers() {
    for (const id of activeMarkers()) await locationsTab({dispatch: 'deactivate', id});
    h.assert(activeMarkers().length === 0, 'A marker location is still enabled');
  }

  try {
    let alphaId;
    let betaId;
    await s.step('the throwaway day sheet shows both owned providers and moves to tomorrow', async () => {
      // The second provider has no schedule, and viewall=0 hides unscheduled providers.
      if (new URL(daySheet.url()).searchParams.get('viewall') !== '1') {
        await h.gotoApp(daySheet, config.baseUrl,
          '/provider/providercontrol?view=0&displaymode=day&dboperation=searchappointmentday&viewall=1');
        await h.assertNotErrorPage(daySheet, 'all-provider day sheet');
      }
      await ui.clickAndAwaitReload(daySheet, daySheet.locator(NEXT_DAY).first(), {label: 'next day'});
      for (const providerNo of ownedProviders) {
        h.assert(await slot(providerNo, '10:00').count() === 1, 'An owned provider column is missing from the group day sheet');
      }
    });

    await s.step('a location typed while the list was off survives the patient search as legacy text once it is on', async () => {
      const clinicLocations = sql.value(`SELECT COUNT(*) FROM LookupListItem WHERE lookupListId=${Number(listId)} AND active=1`);
      if (clinicLocations !== '0') {
        // The clinic already books from its own locations, so no booking form offers free text.
        console.log('  (the clinic already has enabled locations; the free-text booking form is not offered)');
        await locationsTab({dispatch: 'add', name: alpha});
        return;
      }
      const typed = `${marker} Typed`;
      const popup = await openAdd(first, '09:00');
      h.assert(await popup.locator('select#locationCode').count() === 0, 'The Location List is offered while no location is enabled');
      await popup.locator('input#locationText').fill(typed);
      // The first location is enabled while this form is open; the patient search then re-renders
      // the form with the dropdown. Before the fix it started on "Not specified", and saving
      // cleared the typed location without a word.
      await locationsTab({dispatch: 'add', name: alpha});
      await pickPatient(popup);
      const select = popup.locator('select#locationCode');
      h.assert(await select.count() === 1, 'The re-rendered form does not offer the Location List');
      h.assert(await select.inputValue() === 'legacy' && (await select.locator('option[data-legacy]').innerText()).includes(typed),
        'The patient search dropped the typed location');
      await popup.locator('#reason').fill(`${marker} typed`);
      await popup.locator('#addButton').click();
      await waitClosed(popup, 'Add Appointment');
      const rows = located(`reason=${q(`${marker} typed`)}`);
      h.assert(rows.length === 1 && rows[0][3] === typed && rows[0][4] === '', `The booking stored ${JSON.stringify(rows)}`);
    });

    await s.step('the Locations tab enables a second marker location', async () => {
      await locationsTab({dispatch: 'add', name: beta});
      alphaId = itemId(alpha);
      betaId = itemId(beta);
      h.assert(/^[1-9]\d*$/.test(alphaId) && /^[1-9]\d*$/.test(betaId), 'The marker locations were not added');
    });

    await s.step('the location choice survives adding a new patient from the booking form', async () => {
      const newPatient = `${marker}-New`;
      const popup = await openAdd(first, '15:00');
      await popup.locator('select#locationCode').selectOption(betaId);
      await popup.locator('#keyword').fill(newPatient);
      await Promise.all([popup.waitForEvent('domcontentloaded'), popup.locator('#searchBtn').click()]);
      // No patient has this name, so the results page offers Create New, which carries the booking.
      const createNew = popup.locator("form[action$='/demographic/ViewDemographicAddARecordHtm'] button[type='submit']").first();
      await createNew.waitFor({timeout: 20000});
      await Promise.all([popup.waitForEvent('domcontentloaded'), createNew.click()]);
      await h.assertNotErrorPage(popup, 'add-demographic form');
      const form = popup.locator('form[name="adddemographic"]');
      await form.locator('input[name="last_name"]').fill(newPatient);
      await form.locator('input[name="first_name"]').fill('Location');
      await form.locator('select[name="sex"]').selectOption('F');
      await form.locator('input[name="inputDOB"]').fill('1990-01-15');
      // The save-path validator refuses an empty postal code; this one is Canada Post reserved.
      await form.locator('input[name="postal"]').fill('M5W 1E6');
      // Add Record registers the patient, and its result page posts the booking form back.
      await Promise.all([
        popup.waitForURL(/\/appointment\/addappointment/, {timeout: 30000}),
        popup.locator('input[type="submit"][value="Add Record"]').first().click(),
      ]);
      await popup.locator('#addButton').waitFor({timeout: 20000});
      await h.assertNotErrorPage(popup, 'booking form after Add Record');
      const created = sql.value(`SELECT demographic_no FROM demographic WHERE last_name=${q(newPatient)}`);
      h.assert(/^[1-9]\d*$/.test(created), 'Add Record did not register the new patient');
      h.assert(await popup.locator('#demographic_no').inputValue() === created, 'The booking form did not come back with the new patient');
      h.assert(await popup.locator('select#locationCode').inputValue() === betaId, 'Adding a patient dropped the location choice');
      await popup.close();
    });

    let booking;
    await s.step('a booking stores the chosen location\'s id with its label, and the day sheet marks it', async () => {
      booking = await book(first, '10:00', alphaId, `${marker} located`);
      h.assert(locationOf(booking) === JSON.stringify([[alpha, alphaId]]), `The booking stored ${locationOf(booking)}`);
      const chip = daySheet.locator(`.appt-location-chip[data-location-code="${alphaId}"]`).first();
      await chip.waitFor({timeout: 20000});
      h.assert(await chip.getAttribute('aria-label') === alpha, 'The day sheet chip does not name the booked location');
    });

    await s.step('disabling a location changes no booking, and an edit saved unchanged keeps its code and label', async () => {
      await locationsTab({dispatch: 'deactivate', id: alphaId});
      h.assert(sql.value(`SELECT active FROM LookupListItem WHERE id=${Number(alphaId)}`) === '0', 'Disable did not deactivate the location');
      h.assert(locationOf(booking) === JSON.stringify([[alpha, alphaId]]), 'Disabling the location changed the booking');
      const popup = await openEdit(booking);
      const select = popup.locator('select#locationCode');
      h.assert(await select.inputValue() === alphaId && await select.locator(`option[value="${alphaId}"][data-inactive]`).count() === 1,
        'The edit popup does not keep the booking on its disabled location');
      await popup.locator('#reason').fill(`${marker} edited`);
      const [response] = await Promise.all([
        popup.waitForResponse(r => r.request().method() === 'POST' && UPDATE_RECORD.test(new URL(r.url()).pathname)),
        popup.locator('#updateButton').click(),
      ]);
      h.assert(response.status() < 400, `UpdateRecord answered HTTP ${response.status()}`);
      await waitClosed(popup, 'Update Appointment');
      h.assert(sql.value(`SELECT reason FROM appointment WHERE appointment_no=${Number(booking)}`) === `${marker} edited`,
        'The edit was not saved');
      h.assert(locationOf(booking) === JSON.stringify([[alpha, alphaId]]), `Saving the edit stored ${locationOf(booking)}`);
    });

    await s.step('Group Update gives every member the edited appointment\'s location, even one disabled since', async () => {
      const popup = await openEdit(booking);
      await popup.locator('#reason').fill(`${marker} group`);
      await Promise.all([popup.waitForURL(/\/appointment\/appointmentgrouprecords/), popup.locator('#groupButton').click()]);
      await popup.locator('form[name="groupappt"]').waitFor({timeout: 20000});
      await h.assertNotErrorPage(popup, 'group appointment page');
      const box = providerNo => popup.locator(
        `tr:has(input[name^="provider_no"][value="${providerNo}"]) input[type="checkbox"][name^="one"]`);
      h.assert(await box(first).isChecked(), 'Group Action did not preselect the edited appointment');
      h.assert(!(await box(second).isChecked()), 'The second provider, who has no appointment in the slot, was preselected');
      // The second provider has no appointment here: before #3982's fix its preflight started from
      // nothing, refused the disabled location and with it the whole update (HTTP 409).
      await box(second).check();
      const update = bundleMessage('appointment.appointmentgrouprecords.btnGroupUpdate', 'Recurring Update');
      await popup.locator(`form[name="groupappt"] input[type="button"][value="${update}"]`).click();
      await waitClosed(popup, 'Group Update');
      const rows = located(`reason=${q(`${marker} group`)}`);
      h.assert(rows.length === 2 && rows.map(row => row[1]).sort().join() === [...ownedProviders].sort().join()
        && rows.every(row => row[2] === '10:00:00' && row[3] === alpha && row[4] === alphaId),
      `Group Update stored ${JSON.stringify(rows.map(row => row.slice(1)))}`);
      h.assert(located(`appointment_no=${Number(booking)}`).length === 0 && archived(booking) >= 1,
        'Group Update did not replace and archive the edited appointment');
    });

    await s.step('a location disabled while the add form is open is refused with 409, says why and keeps the draft', async () => {
      const popup = await openAdd(first, '11:00');
      await pickPatient(popup);
      await popup.locator('select#locationCode').selectOption(betaId);
      await popup.locator('#reason').fill(`${marker} stale`);
      await locationsTab({dispatch: 'deactivate', id: betaId});
      const mark = failureMark(recorder);
      const [response] = await Promise.all([
        popup.waitForResponse(r => r.request().method() === 'POST' && ADD_RECORD.test(new URL(r.url()).pathname)),
        popup.locator('#addButton').click(),
      ]);
      h.assert(response.status() === 409, `A booking with a location disabled meanwhile answered HTTP ${response.status()}`);
      await popup.waitForLoadState('domcontentloaded');
      const alert = popup.locator('.alert-danger');
      await alert.waitFor({timeout: 20000});
      const reason = bundleMessage('appointment.location.unavailable', 'The selected appointment location is no longer available.');
      h.assert((await alert.innerText()).includes(reason.split('.')[0]), 'The refusal does not say the location is no longer available');
      h.assert(await popup.locator('#reason').inputValue() === `${marker} stale`, 'The refused booking lost its draft');
      h.assert(located(`reason=${q(`${marker} stale`)}`).length === 0, 'A booking with a disabled location was saved');
      consumeExpectedFailure(recorder, mark, {status: 409, path: ADD_RECORD});
      await popup.close();
    });

    await s.step('Copy and Paste keep an active location\'s code; Cut and Paste keep a disabled one as legacy text', async () => {
      await locationsTab({dispatch: 'restore', id: alphaId});
      await locationsTab({dispatch: 'restore', id: betaId});
      const source = await book(first, '12:00', alphaId, `${marker} copy`);
      h.assert(locationOf(source) === JSON.stringify([[alpha, alphaId]]), `The source booking stored ${locationOf(source)}`);

      let popup = await openEdit(source);
      await popup.locator('a.btn:has(i.fa-copy)').click();
      await waitClosed(popup, 'Copy');
      popup = await openAdd(second, '13:00');
      h.assert(await popup.locator('#reason').inputValue() === `${marker} copy`, 'The add form did not paste the copied appointment');
      h.assert(await popup.locator('select#locationCode').inputValue() === alphaId, 'Paste did not select the copied location');
      await popup.locator('#addButton').click();
      await waitClosed(popup, 'Paste after Copy');
      const [copied] = located(`provider_no=${q(second)} AND start_time='13:00:00'`);
      h.assert(copied && copied[3] === alpha && copied[4] === alphaId, `Copy and Paste stored ${JSON.stringify(copied)}`);
      h.assert(locationOf(source) === JSON.stringify([[alpha, alphaId]]), 'Copy changed the source booking');
      await apptLink(copied[0]).waitFor({timeout: 20000});

      // The item is disabled before the cut: the moved booking keeps the name it was booked with.
      await locationsTab({dispatch: 'deactivate', id: alphaId});
      popup = await openEdit(copied[0]);
      await popup.locator('a.btn:has(i.fa-scissors)').click();
      await waitClosed(popup, 'Cut');
      h.assert(located(`appointment_no=${Number(copied[0])}`).length === 0 && archived(copied[0]) === 1,
        'Cut did not remove and archive the booking');
      popup = await openAdd(first, '14:00');
      const select = popup.locator('select#locationCode');
      h.assert(await select.inputValue() === 'legacy' && (await select.locator('option[data-legacy]').innerText()).includes(alpha),
        'Paste did not keep the disabled location as a legacy option');
      await popup.locator('#addButton').click();
      await waitClosed(popup, 'Paste after Cut');
      const moved = located(`provider_no=${q(first)} AND start_time='14:00:00'`);
      h.assert(moved.length === 1 && moved[0][3] === alpha && moved[0][4] === '', `Cut and Paste stored ${JSON.stringify(moved)}`);
    });
  } catch (error) {
    // Cleanup deletes the rows, but only the application can clear its cache: disable first, and
    // report the step's own error rather than any from this restore.
    await disableMarkers().catch(restoreError =>
      console.error(`  (disabling the marker locations through the app also failed: ${restoreError.message})`));
    throw error;
  }
  await s.step('the marker locations are disabled again through the Locations tab', disableMarkers);
  await context.close();
}

if (require.main === module) runWorkflow('appointment-location-booking', workflow, {
  openPatient: true,
  openMaster: false,
  // Runs before the harness creates even its owned patient or opens a database connection.
  preflightConfig: () => requireDisposableEnvironment(),
});
module.exports = {workflow, requireDisposableEnvironment};
