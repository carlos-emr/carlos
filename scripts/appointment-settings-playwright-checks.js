#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Appointment Settings end to end (#3982): the Location List and inline status styling.
 *
 * User path: Schedule ▸ Administration ▸ Schedule Management ▸ Appointment Location Setting
 * (appointment/apptLocationSetting, admin iframe) ▸ Add / Paste / Colour / Icon / Move / Rename /
 * Disable (confirm) ▸ the Statuses tab ▸ inline description, colour and icon of a core status;
 * Schedule ▸ slot ▸ Add Appointment popup ▸ Location dropdown ▸ patient search ▸ Add; the day
 * sheet's location chip; the Edit popup's saved and legacy locations.
 *
 * Asserts the LookupListItem, appointment_status and appointment rows after every change, that a
 * cancelled Disable sends nothing, that GET/HEAD cannot reach any change (405), that a refused
 * value (duplicate name, a colour that is not #rrggbb) saves nothing and says why, that the
 * location choice survives the patient-search round trip, and that a booking stores the item's id
 * with a snapshot of its label.
 *
 * Fixtures: the clinic's Location List is clinic-wide, so run with EXCLUSIVE=1. Locations the
 * clinic already enabled are switched off for the run and back on in cleanup; every location this
 * check adds carries the marker and is deleted afterwards, with the marker appointment. A core
 * status (editable=0) is restyled and restored. Needs a clinic without a site setup (multisites,
 * scheduleSiteID, CAISI program locations): with one, the booking half is skipped, as the
 * booking screens keep their site controls.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const {runWorkflow, expectValue} = require('./lib/workflow-session');
const {appointmentMonitorCleanup} = require('./lib/appointment-page-monitor-cleanup');
const {openAdminSection, frameNavigation, dismissedConfirmSendsNothing} = require('./schedule-admin-settings-playwright-checks');

const LIST_NAME = 'appointmentLocationCode';

async function workflow(s) {
  const {sql, marker, provider, recorder, patient, context, schedule} = s;
  const q = h.sqlString;
  const listId = sql.value(`SELECT id FROM LookupList WHERE name=${q(LIST_NAME)}`);
  h.assert(/^[1-9]\d*$/.test(listId), 'Flyway V1.0.81 did not seed the appointmentLocationCode lookup list');
  const preEnabled = sql.rows(`SELECT id FROM LookupListItem WHERE lookupListId=${Number(listId)} AND active=1`).map(r => r[0]);
  const items = () => sql.rows(`SELECT id,label,active,displayOrder,IFNULL(colour,''),IFNULL(icon,'') FROM LookupListItem
      WHERE lookupListId=${Number(listId)} AND label LIKE ${q(`${marker}%`)} ORDER BY active DESC, displayOrder, id`);
  const itemId = label => sql.value(`SELECT id FROM LookupListItem WHERE lookupListId=${Number(listId)} AND label=${q(label)}`);
  const coreStatus = sql.rows(`SELECT id,status,description,color,icon FROM appointment_status
      WHERE editable=0 AND status='N' LIMIT 1`)[0];
  h.assert(coreStatus, 'The seeded No Show status (editable=0) is missing');
  const [statusId, , statusDesc, statusColour, statusIcon] = coreStatus;
  const monitor = appointmentMonitorCleanup({
    sql: statement => (/^\s*select/i.test(statement) ? sql.rows(statement).map(r => r[0]).join('\n') : (sql.execute(statement), '')),
    provider, date: '2028-02-15', baseUrl: s.config.baseUrl,
  });
  s.cleanup(() => {
    sql.execute(`DELETE FROM appointmentArchive WHERE reason LIKE ${q(`${marker}%`)};
      DELETE FROM appointment WHERE reason LIKE ${q(`${marker}%`)};
      DELETE FROM LookupListItem WHERE lookupListId=${Number(listId)} AND label LIKE ${q(`${marker}%`)};
      UPDATE appointment_status SET description=${q(statusDesc)},color=${q(statusColour)},icon=${q(statusIcon)} WHERE id=${Number(statusId)}`);
    if (preEnabled.length) {
      sql.execute(`UPDATE LookupListItem SET active=1 WHERE id IN (${preEnabled.map(Number).join(',')})`);
    }
    monitor.cleanup();
  });
  if (preEnabled.length) {
    sql.execute(`UPDATE LookupListItem SET active=0 WHERE id IN (${preEnabled.map(Number).join(',')})`);
  }

  const {page: admin} = await ui.clickOpensPopupOrNavigates(schedule, schedule.locator('#admin-panel,#admin2').first(),
    {context, recorder, label: 'administration', timeout: 20000});
  let frame;
  const reopen = async () => {
    frame = await openAdminSection(admin, '/appointment/apptLocationSetting', 'h1');
    return frame;
  };
  const submit = locator => frameNavigation(admin, frame, () => locator.click());
  const row = label => frame.locator('#locationTable tbody tr', {has: frame.locator(`input[name="name"][value="${label}"]`)});

  const room = `${marker} Room`;
  const virtual = `${marker} Virtual`;
  const annex = `${marker} Annex`;

  await s.step('the Locations tab adds one location and pastes a list, skipping a name already enabled', async () => {
    await reopen();
    h.assert(await frame.locator('ul.nav-tabs a[href$="/appointment/apptLocationSetting"].active').count() === 1,
      'The Locations tab is not the active Appointment Settings tab');
    await frame.locator('#newLocationName').fill(`  ${room}  `);
    await submit(frame.locator('#addLocationForm button[type="submit"]'));
    h.assert(JSON.stringify(items().map(r => [r[1], r[2]])) === JSON.stringify([[room, '1']]), 'Add did not store the trimmed name');
    await frame.locator('#pasteLocationsForm').evaluate(form => { form.closest('details').open = true; });
    await frame.locator('#pastedLocationNames').fill(`${virtual}\n\n${room.toLowerCase()}\n${annex}`);
    await submit(frame.locator('#pasteLocationsForm button[type="submit"]'));
    h.assert(JSON.stringify(items().map(r => r[1])) === JSON.stringify([room, virtual, annex]),
      `Paste stored ${JSON.stringify(items().map(r => r[1]))}`);
    h.assert((await frame.locator('.alert-success').innerText()).includes('2'), 'Paste did not report what it added');
    if (preEnabled.length === 0) {
      h.assert(await frame.locator('body').getAttribute('data-location-mode') === String(!(await siteSetup())),
        'The page does not report location mode after a location was enabled');
    }
  });

  async function siteSetup() {
    return (await frame.locator('body').getAttribute('data-site-setup')) === 'true';
  }

  await s.step('a duplicate name and an invalid colour are refused with a reason and save nothing', async () => {
    await frame.locator('#newLocationName').fill(virtual.toUpperCase());
    await submit(frame.locator('#addLocationForm button[type="submit"]'));
    h.assert(items().length === 3, 'A duplicate active name was added');
    h.assert(await frame.locator('.alert-danger').count() === 1, 'The duplicate name showed no reason');
    h.assert(await frame.locator('#newLocationName').inputValue() === virtual.toUpperCase(), 'The refused name was not offered again');
    const id = itemId(virtual);
    const status = await frame.locator(`#colour-${id}`).evaluate(async input => {
      const data = new URLSearchParams(new FormData(input.form));
      data.set('colour', 'red;background:url(x)');
      const response = await fetch(input.form.action, {method: 'POST', body: data, credentials: 'same-origin'});
      return {status: response.status, text: await response.text()};
    });
    h.assert(status.status === 200 && status.text.includes('alert-danger'), `An invalid colour was not refused: HTTP ${status.status}`);
    h.assert(items().find(r => r[0] === id)[4] === '', 'An invalid colour was stored');
  });

  await s.step('one rendering of the add form saves once, however often it is sent (FormSubmissionGuard)', async () => {
    const twice = `${marker} Twice`;
    await frame.locator('#newLocationName').fill(twice);
    const responses = await frame.locator('#addLocationForm').evaluate(async form => {
      const data = new URLSearchParams(new FormData(form));
      const send = () => fetch(form.action, {method: 'POST', body: data, credentials: 'same-origin'})
        .then(async response => ({status: response.status, text: await response.text()}));
      return [await send(), await send()];
    });
    h.assert(responses.every(r => r.status === 200 && r.text.includes('alert-success') && !r.text.includes('alert-danger')),
      `A repeated add did not end as the first did: ${responses.map(r => r.status).join(', ')}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM LookupListItem WHERE lookupListId=${Number(listId)} AND label=${q(twice)}`) === '1',
      'A repeated add of one rendering stored the location twice');
    sql.execute(`DELETE FROM LookupListItem WHERE lookupListId=${Number(listId)} AND label=${q(twice)}`);
    await reopen();
  });

  await s.step('colour and icon style the chip; the order and the name change from their own forms', async () => {
    const id = itemId(virtual);
    await frame.locator(`#colour-${id}`).fill('#2fcccf');
    await submit(frame.locator(`form:has(#colour-${id}) button[type="submit"]`));
    await frame.locator(`#icon-${id}`).selectOption('fa-video');
    await submit(frame.locator(`form:has(#icon-${id}) button[type="submit"]`));
    const styled = items().find(r => r[0] === id);
    h.assert(styled[4] === '#2fcccf' && styled[5] === 'fa-video', `Style was not stored: ${JSON.stringify(styled)}`);
    const chip = row(virtual).locator('.appt-location-chip');
    h.assert(await chip.locator('i.fa-video').count() === 1, 'The chip does not draw the chosen icon');
    h.assert((await chip.evaluate(el => getComputedStyle(el).backgroundColor)) === 'rgb(47, 204, 207)', 'The chip is not drawn in the chosen colour');
    await submit(row(virtual).locator('form:has(input[value="moveUp"]) button'));
    h.assert(items()[0][1] === virtual, 'Move up did not put the location first');
    // A Move button sent twice from one rendering moves the location once: the second names a place it has left.
    const repeated = await row(virtual).locator('form:has(input[value="moveDown"])').evaluate(async form => {
      const send = data => fetch(form.action, {method: 'POST', body: data, credentials: 'same-origin'})
        .then(async response => ({status: response.status, text: await response.text()}));
      const down = new URLSearchParams(new FormData(form));
      const results = [await send(down), await send(down)];
      const up = new URLSearchParams(down);
      up.set('dispatch', 'moveUp');
      up.set('position', '2');
      results.push(await send(up));
      return results;
    });
    h.assert(repeated[0].text.includes('alert-success') && repeated[1].text.includes('alert-danger'),
      'A repeated Move down was not refused');
    h.assert(repeated[2].text.includes('alert-success') && items()[0][1] === virtual,
      `Moving back up did not restore the order: ${JSON.stringify(items().map(r => r[1]))}`);
    await reopen();
    const annexId = itemId(annex);
    await frame.locator(`#name-${annexId}`).fill(`${annex} 2`);
    await submit(frame.locator(`form:has(#name-${annexId}) button[type="submit"]`));
    h.assert(items().find(r => r[0] === annexId)[1] === `${annex} 2`, 'Rename was not stored');
  });

  await s.step('Disable asks first: cancelling sends nothing, confirming takes the location out of booking', async () => {
    const annexId = itemId(`${annex} 2`);
    const disable = frame.locator(`#location-${annexId} form:has(input[value="deactivate"]) button`);
    const dismissed = await dismissedConfirmSendsNothing(admin, frame, '/appointment/apptLocationSetting', () => disable.click());
    h.assert(dismissed.length === 1 && dismissed[0].type === 'confirm', 'Disable did not ask for confirmation');
    await expectValue(sql, `SELECT active FROM LookupListItem WHERE id=${Number(annexId)}`, '1', 'A cancelled Disable deactivated the location');
    const confirmed = await h.withExpectedDialogs(admin, () => submit(disable));
    h.assert(confirmed.length === 1, 'Disable did not ask exactly once');
    await expectValue(sql, `SELECT active FROM LookupListItem WHERE id=${Number(annexId)}`, '0', 'Disable did not deactivate the location');
    h.assert(await frame.locator(`#location-${annexId} form:has(input[value="restore"])`).count() === 1, 'A disabled location offers no Enable');
  });

  await s.step('GET and HEAD cannot change a location or a status', async () => {
    const id = itemId(virtual);
    const probes = [
      `/appointment/apptLocationSetting?dispatch=add&name=${encodeURIComponent(`${marker} GET`)}`,
      `/appointment/apptLocationSetting?dispatch=rename&id=${id}&name=x`,
      `/appointment/apptLocationSetting?dispatch=deactivate&id=${id}`,
      `/appointment/apptStatusSetting?dispatch=updateColour&id=${statusId}&apptColor=%23000000`,
    ];
    for (const probe of probes) {
      for (const method of ['GET', 'HEAD']) {
        const response = await context.request.fetch(h.appUrl(s.config.baseUrl, probe), {method, maxRedirects: 0});
        h.assert(response.status() === 405, `${method} ${probe} answered HTTP ${response.status()}`);
      }
    }
    h.assert(items().length === 3 && items()[0][1] === virtual && items()[0][2] === '1', 'A GET probe changed a location');
    h.assert(sql.value(`SELECT color FROM appointment_status WHERE id=${Number(statusId)}`) === statusColour, 'A GET probe changed a status');
  });

  await s.step('the Statuses tab restyles a core status inline and confirms before Reset', async () => {
    // The tab exists only while status editing is switched on (ENABLE_EDIT_APPT_STATUS).
    if (await frame.locator('a[href$="/appointment/apptStatusSetting"]').count() === 0) {
      console.log('  (status editing is switched off on this install; the Statuses tab is not offered)');
      return;
    }
    const statusFrame = await openAdminSection(admin, '/appointment/apptStatusSetting?dispatch=view', '#statusTable');
    const desc = `PW ${marker.slice(-8)}`;
    await statusFrame.locator(`#desc-${statusId}`).fill(desc);
    await frameNavigation(admin, statusFrame, () => statusFrame.locator(`form:has(#desc-${statusId}) button[type="submit"]`).click());
    await statusFrame.locator(`#icon-${statusId}`).selectOption('cancel.gif');
    await frameNavigation(admin, statusFrame, () => statusFrame.locator(`form:has(#icon-${statusId}) button[type="submit"]`).click());
    h.assert(JSON.stringify(sql.rows(`SELECT description,icon FROM appointment_status WHERE id=${Number(statusId)}`)) === JSON.stringify([[desc, 'cancel.gif']]),
      'A core (editable=0) status could not be restyled; editable gates only enable/disable (#3888)');
    h.assert(await statusFrame.locator(`#status-${statusId} form:has(input[value="changestatus"])`).count() === 0,
      'A core status offers Disable');
    const reset = statusFrame.locator('form:has(input[value="reset"]) button');
    const dismissed = await dismissedConfirmSendsNothing(admin, statusFrame, '/appointment/apptStatusSetting', () => reset.click());
    h.assert(dismissed.length === 1, 'Reset did not ask for confirmation');
    h.assert(sql.value(`SELECT description FROM appointment_status WHERE id=${Number(statusId)}`) === desc, 'A cancelled Reset still reset');
  });

  if (await (async () => { await reopen(); return siteSetup(); })()) {
    throw new h.SkipCheck('This clinic books by sites or programs, so the booking screens keep their own location control');
  }

  const virtualId = itemId(virtual);
  const roomId = itemId(room);
  let appointmentNo;
  await s.step('the booking popup offers the dropdown, keeps the choice across patient search, and saves code and label', async () => {
    await schedule.goto(h.appUrl(s.config.baseUrl, '/provider/providercontrol?year=2028&month=2&day=15&view=0&displaymode=day&dboperation=searchappointmentday&viewall=1'),
      {waitUntil: 'domcontentloaded'});
    await monitor.remember(context);
    const slot = schedule.locator(`a.adhour[onclick*="provider_no=${provider}&"]`).first();
    const popup = await ui.clickOpensPopup(schedule, slot, {context, recorder, label: 'add-appointment', timeout: 20000});
    const select = popup.locator('select#locationCode[name="locationCode"]');
    await select.waitFor({timeout: 20000});
    h.assert(await popup.locator('input#locationText').count() === 0, 'The free-text location is still offered next to the dropdown');
    const options = await select.locator('option').evaluateAll(nodes => nodes.map(n => [n.value, n.textContent.trim()]));
    h.assert(JSON.stringify(options) === JSON.stringify([['', options[0][1]], [virtualId, virtual], [roomId, room]]),
      `The dropdown offers ${JSON.stringify(options)}`);
    await select.selectOption(virtualId);
    const surname = sql.value(`SELECT last_name FROM demographic WHERE demographic_no=${Number(patient)}`);
    await popup.locator('#keyword').fill(surname);
    await ui.clickAndAwaitReload(popup, popup.locator('#searchBtn'), {timeout: 20000, label: 'patient search'});
    const pick = popup.locator(`input[type="button"][name="pick_demographic"][value="${patient}"]`).first();
    await pick.waitFor({timeout: 20000});
    await ui.clickAndAwaitReload(popup, pick, {timeout: 20000, label: 'pick patient'});
    await popup.locator('select#locationCode').waitFor({timeout: 20000});
    h.assert(await popup.locator('select#locationCode').inputValue() === virtualId, 'The patient search dropped the location choice');
    await popup.locator('#reason').fill(`${marker} booked`);
    const [response] = await Promise.all([
      popup.waitForResponse(r => r.request().method() === 'POST' && /\/appointment\/AddRecord$/.test(new URL(r.url()).pathname)),
      popup.locator('#addButton').click(),
    ]);
    h.assert(response.status() < 400, `AddRecord answered HTTP ${response.status()}`);
    const saved = sql.rows(`SELECT appointment_no,location,locationCode FROM appointment WHERE reason LIKE ${q(`${marker}%`)}`);
    h.assert(saved.length === 1 && saved[0][1] === virtual && saved[0][2] === virtualId,
      `The booking stored ${JSON.stringify(saved)}`);
    appointmentNo = saved[0][0];
    await popup.close().catch(() => {});
  });

  await s.step('the day sheet draws the location chip, and the edit popup keeps saved and legacy locations', async () => {
    await schedule.reload({waitUntil: 'domcontentloaded'});
    const chip = schedule.locator(`.appt-location-chip[data-location-code="${virtualId}"]`);
    await chip.first().waitFor({timeout: 20000});
    h.assert(await chip.first().getAttribute('aria-label') === virtual, 'The day sheet chip does not name the location');
    const openEdit = async () => {
      const link = schedule.locator(`a.apptLink[onclick*="appointment_no=${appointmentNo}"]`).first();
      return ui.clickOpensPopup(schedule, link, {context, recorder, label: 'edit-appointment', timeout: 20000});
    };
    let popup = await openEdit();
    h.assert(await popup.locator('select#locationCode').inputValue() === virtualId, 'The edit popup did not start on the saved location');
    await popup.close();
    sql.execute(`UPDATE appointment SET location=${q(`${marker} Basement`)}, locationCode=NULL WHERE appointment_no=${Number(appointmentNo)}`);
    popup = await openEdit();
    const legacy = popup.locator('select#locationCode option[data-legacy]');
    h.assert(await popup.locator('select#locationCode').inputValue() === 'legacy'
      && (await legacy.innerText()).includes(`${marker} Basement`), 'The edit popup does not offer the legacy location');
    const [response] = await Promise.all([
      popup.waitForResponse(r => r.request().method() === 'POST' && /\/appointment\/UpdateRecord$/.test(new URL(r.url()).pathname)),
      popup.locator('#updateButton').click(),
    ]);
    h.assert(response.status() < 400, `UpdateRecord answered HTTP ${response.status()}`);
    const kept = sql.rows(`SELECT location,IFNULL(locationCode,'') FROM appointment WHERE appointment_no=${Number(appointmentNo)}`);
    h.assert(JSON.stringify(kept) === JSON.stringify([[`${marker} Basement`, '']]), `Saving the legacy location stored ${JSON.stringify(kept)}`);
    await popup.close().catch(() => {});
  });
}

if (require.main === module) runWorkflow('appointment-settings', workflow, {openMaster: false});
module.exports = {workflow};
