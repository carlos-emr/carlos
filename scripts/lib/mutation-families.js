/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
/*
 * The mutation families of authz-write-role-matrix and csrf-negative-matrix: for each, how the
 * full-privilege login performs the write through the real UI (so the request a probe replays is
 * the one the page really sends), which owned rows a write tagged `tag` creates, changes or
 * deletes, and how a replay is aimed at a new tag. Both checks drive the same definitions; only
 * the replaying session and the token differ (lib/mutation-replay.js).
 *
 * A family is made by family(s, options) for one workflow session and returns:
 *   key, label          what the step labels name
 *   route               RegExp on the request pathname the UI action posts to
 *   kind                'create' | 'update' | 'delete': a write moves the owned COUNT(*) by +1 for
 *                       create/update (rows carrying the tag) and -1 for delete (the tag's target)
 *   table, where(tag)   the owned rows, for h.assertRefused and the write proofs
 *   prepare()           owned fixtures the action needs (optional)
 *   perform(tag)        the UI action, in the full session; resolves to the captured request
 *   aim(captured, tag, { write })
 *                       { overrides, query } that point a replay at `tag`: a new marker text, or
 *                       for a delete another owned appointment. It may open UI first when the
 *                       write needs fresh server state the page would also have (an eForm's
 *                       one-time submission id, a freshly staged Rx card when `write` says the
 *                       replay is the positive control that must write).
 *   cleanup()           removes every owned row and asserts it is gone; the check registers it
 *                       with s.cleanup() before the family writes anything
 *
 * Every tag becomes `${marker}-${short}-${tag}` text (or a shortened form where the column is
 * narrow), so rows are found by the run marker, never by guessing at shared data.
 *
 * Fixture text is plain letters, digits and hyphens: nothing a CRS rule reads as an attack, so the
 * front door's WAF never refuses the check's own text (Review Focus 5).
 */
const h = require('./playwright-harness');
const ui = require('./playwright-ui');
const { captureRequest } = require('./get-reject-probe');
const { revealAuditLink } = require('./playwright-link-audit');

const TIMEOUT = 30000;
const q = h.sqlString;

/** captureRequest over a whole browser context: popups and their frames included. */
function captureInContext(context, match, act, options = {}) {
  const watcher = {
    waitForRequest: (predicate, { timeout }) => context.waitForEvent('request', { predicate, timeout }),
  };
  return captureRequest(watcher, match, act, { timeout: TIMEOUT, ...options });
}

const pathIs = (route) => (url) => route.test(url.pathname);
const assertId = (id, what) => h.assert(/^[1-9]\d*$/.test(String(id)), `${what} is not an owned numeric id`);

/** Insert an owned appointment for the patient (reason/notes carry the text); returns its number. */
function ownedAppointment(s, { date, start, text }) {
  const no = s.sql.value(`INSERT INTO appointment (provider_no, appointment_date, start_time, end_time, name, demographic_no,
    program_id, notes, reason, location, resources, type, style, billing, status, createdatetime, updatedatetime, creator, remarks, urgency)
    VALUES (${q(s.provider)}, ${q(date)}, ${q(start)}, ADDTIME(${q(start)}, '00:14:00'), ${q(`${s.marker},Workflow`)}, ${s.patient}, 0,
    ${q(text)}, ${q(text)}, '', '', '', '', '', 't', NOW(), NOW(), ${q(s.provider)}, '', ''); SELECT LAST_INSERT_ID()`);
  assertId(no, 'The appointment fixture');
  return no;
}

/** Remove the patient's appointments (and archive rows) whose reason carries `prefix`. */
function removeAppointments(s, prefix) {
  const like = q(`${prefix}%`);
  s.sql.execute(`DELETE FROM appointmentArchive WHERE demographic_no=${s.patient} AND reason LIKE ${like};
    DELETE FROM appointment WHERE demographic_no=${s.patient} AND reason LIKE ${like}`);
  h.assert(s.sql.value(`SELECT (SELECT COUNT(*) FROM appointment WHERE demographic_no=${s.patient} AND reason LIKE ${like})
    + (SELECT COUNT(*) FROM appointmentArchive WHERE demographic_no=${s.patient} AND reason LIKE ${like})`) === '0',
  'Owned appointment rows were not removed');
}

function dayQuery(date) {
  const [year, month, day] = date.split('-').map(Number);
  return new URLSearchParams({ year, month, day, view: '0', displaymode: 'day', dboperation: 'searchappointmentday', viewall: '1' });
}

async function openDaySheet(s, date) {
  await h.gotoApp(s.schedule, s.config.baseUrl, `/provider/providercontrol?${dayQuery(date)}`);
  await s.schedule.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
}

/** A future date far from demo appointments, `days` ahead (UTC). */
function futureDate(days) {
  return new Date(Date.now() + days * 86400000).toISOString().slice(0, 10);
}

// ---------------------------------------------------------------------------------------------
// Appointments (csrf: add, update, delete)

function appointmentAdd(s) {
  const prefix = `${s.marker}-AA-`;
  const date = futureDate(410);
  return {
    key: 'appointment-add', label: 'appointment add (appointment/AddRecord)', route: /\/appointment\/AddRecord$/, kind: 'create',
    table: 'appointment', where: (tag) => `demographic_no=${s.patient} AND reason=${q(prefix + tag)}`,
    cleanup: () => removeAppointments(s, prefix),
    async perform(tag) {
      await openDaySheet(s, date);
      const slots = s.schedule.locator(`a.adhour[onclick*="provider_no=${s.provider}&"]`);
      h.assert(await slots.count() > 3, 'The day sheet rendered too few bookable slots');
      const popup = await s.popup(s.schedule, slots.nth(2), 'appointment-add');
      await popup.waitForLoadState('domcontentloaded', { timeout: TIMEOUT });
      await popup.locator('#keyword').fill(s.marker);
      await ui.clickAndAwaitReload(popup, popup.locator('#searchBtn'), { required: false });
      const pick = popup.locator(`table tr input[type="button"][name="pick_demographic"][value="${s.patient}"]`).first();
      await pick.waitFor({ state: 'visible', timeout: TIMEOUT });
      await ui.clickAndAwaitReload(popup, pick, { required: false });
      await popup.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
      h.assert(await popup.locator('#demographic_no').inputValue() === s.patient, 'The booking form holds another patient');
      await popup.locator('#reason').fill(prefix + tag);
      const captured = await captureInContext(s.context, pathIs(this.route), () => popup.locator('#addButton').click());
      if (!popup.isClosed()) await popup.close().catch(() => {});
      return captured;
    },
    aim: (captured, tag) => ({ overrides: { reason: prefix + tag } }),
  };
}

/** Open the edit popup of an owned appointment from its link on the day sheet. */
async function openAppointmentEditor(s, date, appointmentNo, label) {
  await openDaySheet(s, date);
  const link = s.schedule.locator(`a.apptLink[onclick*="appointment_no=${appointmentNo}"]`).first();
  await link.waitFor({ state: 'visible', timeout: 45000 });
  const popup = await s.popup(s.schedule, link, label);
  await popup.waitForLoadState('domcontentloaded', { timeout: TIMEOUT });
  h.assert(await popup.locator('input[name="appointment_no"]').inputValue() === String(appointmentNo),
    'The edit popup opened another appointment');
  return popup;
}

function appointmentUpdate(s) {
  const prefix = `${s.marker}-AU-`;
  const date = futureDate(411);
  let appointmentNo;
  return {
    key: 'appointment-update', label: 'appointment update (appointment/UpdateRecord)', route: /\/appointment\/UpdateRecord$/, kind: 'update',
    table: 'appointment', where: (tag) => `appointment_no=${appointmentNo} AND demographic_no=${s.patient} AND reason=${q(prefix + tag)}`,
    cleanup: () => removeAppointments(s, prefix),
    prepare() { appointmentNo = ownedAppointment(s, { date, start: '10:00:00', text: `${prefix}BOOKED` }); },
    async perform(tag) {
      const popup = await openAppointmentEditor(s, date, appointmentNo, 'appointment-edit');
      await popup.locator('#reason').fill(prefix + tag);
      const captured = await captureInContext(s.context, pathIs(this.route), () => popup.locator('#updateButton').click());
      if (!popup.isClosed()) await popup.close().catch(() => {});
      return captured;
    },
    aim: (captured, tag) => ({ overrides: { reason: prefix + tag } }),
  };
}

function appointmentDelete(s) {
  const prefix = `${s.marker}-AD-`;
  const date = futureDate(412);
  const targets = {};
  let hour = 8;
  const target = (tag) => {
    if (!targets[tag]) {
      targets[tag] = ownedAppointment(s, { date, start: `${String(hour).padStart(2, '0')}:00:00`, text: prefix + tag });
      hour += 1;
    }
    return targets[tag];
  };
  return {
    key: 'appointment-delete', label: 'appointment delete (appointment/DeleteRecord)', route: /\/appointment\/DeleteRecord$/, kind: 'delete',
    table: 'appointment', where: (tag) => `appointment_no=${target(tag)} AND demographic_no=${s.patient}`,
    cleanup: () => removeAppointments(s, prefix),
    async perform(tag) {
      const appointmentNo = target(tag);
      const popup = await openAppointmentEditor(s, date, appointmentNo, 'appointment-delete');
      let captured;
      // The delete confirm is raised by the form's own onSubmit; accepting it is the user saying yes.
      const dialogs = await h.withExpectedDialogs(popup, async () => {
        captured = await captureInContext(s.context, pathIs(this.route), () => popup.locator('#deleteButton').click());
      });
      h.assert(dialogs.length === 1 && /delete/i.test(dialogs[0].text || ''), 'Delete raised no delete confirmation');
      if (!popup.isClosed()) await popup.close().catch(() => {});
      return captured;
    },
    aim: (captured, tag) => ({ overrides: { appointment_no: target(tag) }, query: captured.query.has('appointment_no') ? { appointment_no: target(tag) } : {} }),
  };
}

// ---------------------------------------------------------------------------------------------
// Demographics (csrf: add, update; authz: update)

function demographicAdd(s) {
  const prefix = `${s.marker}-DA-`;
  const owned = () => s.sql.rows(`SELECT demographic_no FROM demographic WHERE last_name LIKE ${q(`${prefix}%`)}`).map(([id]) => id);
  return {
    key: 'demographic-add', label: 'demographic add (demographic/DemographicAddRecord)', route: /\/demographic\/DemographicAddRecord$/, kind: 'create',
    table: 'demographic', where: (tag) => `last_name=${q(prefix + tag)}`,
    cleanup() {
      const ids = owned();
      ids.forEach(id => assertId(id, 'An owned new patient'));
      if (ids.length) {
        const list = ids.join(',');
        const children = [['admission', 'client_id'], ['demographicArchive', 'demographic_no'], ['demographicExt', 'demographic_no'],
          ['demographicExtArchive', 'demographic_no'], ['demographiccust', 'demographic_no'], ['demographiccustArchive', 'demographic_no'],
          ['casemgmt_note_lock', 'demographic_no'], ['casemgmt_tmpsave', 'demographic_no']];
        for (const [table, column] of children) s.sql.execute(`DELETE FROM ${table} WHERE ${column} IN (${list})`);
        for (const [table, column] of children) {
          h.assert(s.sql.value(`SELECT COUNT(*) FROM ${table} WHERE ${column} IN (${list})`) === '0', `Child rows remain in ${table}`);
        }
        s.sql.execute(`DELETE FROM demographic WHERE demographic_no IN (${list}) AND last_name LIKE ${q(`${prefix}%`)}`);
      }
      h.assert(owned().length === 0, 'Owned new patients were not removed');
    },
    async perform(tag) {
      const lastName = prefix + tag;
      // The top bar opens the search into a NAMED window (popupPage2, 'apptProviderSearch'): one the
      // session opened earlier (to reach the Master Record) would be navigated again and no new page
      // would appear, so it is closed first.
      for (const page of s.context.pages()) {
        if (page === s.schedule || page.isClosed()) continue;
        if (await page.evaluate(() => window.name).catch(() => '') === 'apptProviderSearch') await page.close().catch(() => {});
      }
      const search = await s.popup(s.schedule, s.schedule.locator('a').filter({ hasText: /^Search$/ }).first(), 'patient-search');
      await search.waitForLoadState('domcontentloaded', { timeout: TIMEOUT });
      await search.locator('#keyword, input[name="keyword"]').first().fill(lastName);
      await ui.clickAndAwaitReload(search, search.locator("input[type='submit']").first(), { required: false });
      await search.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
      await ui.clickAndAwaitReload(search, search.locator("form[action$='/demographic/ViewDemographicAddARecordHtm'] button[type='submit']").first(),
        { required: false });
      await search.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
      await h.assertNotErrorPage(search, 'add-demographic form');
      const form = search.locator('form[name="adddemographic"]');
      await form.locator('input[name="last_name"]').fill(lastName);
      await form.locator('input[name="first_name"]').fill('Check');
      await form.locator('select[name="sex"]').selectOption('F');
      await form.locator('input[name="inputDOB"]').fill('1990-01-15');
      await form.locator('input[name="postal"]').fill('M5W 1E6');
      const captured = await captureInContext(s.context, pathIs(this.route),
        () => search.locator('input[type="submit"][value="Add Record"]').first().click());
      await search.waitForLoadState('domcontentloaded', { timeout: TIMEOUT }).catch(() => {});
      if (!search.isClosed()) await search.close().catch(() => {});
      return captured;
    },
    aim: (captured, tag) => ({ overrides: { last_name: prefix + tag } }),
  };
}

function demographicUpdate(s) {
  const prefix = `${s.marker}-DU-`;
  return {
    key: 'demographic-update', label: 'demographic update (demographic/DemographicUpdate)', route: /\/demographic\/DemographicUpdate$/, kind: 'update',
    table: 'demographic', where: (tag) => `demographic_no=${s.patient} AND city=${q(prefix + tag)}`,
    cleanup() {
      // runWorkflow removes the owned patient's demographicExt and demographicArchive rows; the
      // update also archives the extension rows it replaces.
      s.sql.execute(`DELETE FROM demographicExtArchive WHERE demographic_no=${s.patient}`);
      h.assert(s.sql.value(`SELECT COUNT(*) FROM demographicExtArchive WHERE demographic_no=${s.patient}`) === '0',
        'Owned demographicExtArchive rows were not removed');
    },
    async perform(tag) {
      const master = s.master;
      h.assert(master && new URL(master.url()).searchParams.get('demographic_no') === String(s.patient),
        'The Master Record window is not the owned patient\'s');
      // Reloaded so the form is rendered from the row as it is now (an earlier family's save has
      // already redirected this window back to the record).
      await master.reload({ waitUntil: 'domcontentloaded' });
      await master.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
      await master.locator('#editBtn').click();
      await master.locator('#editDemographic').waitFor({ state: 'visible', timeout: TIMEOUT });
      await master.locator('[name="city"]').first().fill(prefix + tag);
      await master.locator('[name="postal"]').first().fill('M5W 1E6');
      const captured = await captureInContext(s.context, pathIs(this.route),
        () => master.locator('#updateButton input[type="submit"]').first().click());
      await master.waitForLoadState('domcontentloaded', { timeout: TIMEOUT }).catch(() => {});
      return captured;
    },
    aim(captured, tag) {
      // A rendering of the form names each of the patient's extension rows by id (`demo_cell_id`,
      // ...) and omits the id of a row that does not exist yet. The UI's own save creates those
      // rows, so the form as it was rendered would insert them a second time on replay
      // (uk_demo_ext_single_value, the stale-form defect concurrency-demographic-ext-insert pins).
      // The ids are set as the same form opened again would carry them.
      const overrides = { city: prefix + tag };
      for (const [key, id] of s.sql.rows(`SELECT key_val, id FROM demographicExt WHERE demographic_no=${s.patient}`)) {
        if (/^[A-Za-z][A-Za-z0-9_]{0,63}$/.test(key) && /^[1-9]\d*$/.test(id)) overrides[`${key}_id`] = id;
      }
      return { overrides };
    },
  };
}

// ---------------------------------------------------------------------------------------------
// Ontario bill save (both)

function billingOnSave(s) {
  const prefix = `${s.marker}-BS-`;
  const date = process.env.BILLING_SUBMIT_DATE || '2024-05-06';
  const code = process.env.BILLING_OHIP_CODE || 'A007A';
  const formName = process.env.BILLING_FORM_NAME || 'General Practice';
  const targets = {};
  let hour = 8;
  const target = (tag) => {
    if (!targets[tag]) {
      targets[tag] = ownedAppointment(s, { date, start: `${String(hour).padStart(2, '0')}:30:00`, text: prefix + tag });
      hour += 1;
    }
    return targets[tag];
  };
  const headers = () => {
    const ids = Object.values(targets);
    return ids.length ? s.sql.rows(`SELECT id FROM billing_on_cheader1 WHERE appointment_no IN (${ids.join(',')})
      AND demographic_no=${s.patient}`).map(([id]) => id) : [];
  };
  return {
    key: 'billing-on-save', label: 'Ontario bill save (billing/CA/ON/BillingONSave)', route: /\/billing\/CA\/ON\/BillingONSave$/, kind: 'create',
    table: 'billing_on_cheader1', where: (tag) => `appointment_no=${target(tag)} AND demographic_no=${s.patient}`,
    prepare() {
      s.sql.execute(`UPDATE demographic SET hin='9876543217', ver='ZZ', hc_type='ON', hc_renew_date='2099-12-31',
        date_joined='2020-01-01' WHERE demographic_no=${s.patient} AND last_name=${q(s.marker)}`);
    },
    cleanup() {
      for (const id of headers()) {
        assertId(id, 'An owned bill');
        s.sql.execute(`DELETE FROM billing_on_transaction WHERE ch1_id=${id}; DELETE FROM billing_on_ext WHERE billing_no=${id};
          DELETE FROM billing_on_item WHERE ch1_id=${id}; DELETE FROM billing_on_cheader1 WHERE id=${id} AND demographic_no=${s.patient}`);
      }
      h.assert(headers().length === 0, 'Owned bills were not removed');
      removeAppointments(s, prefix);
    },
    async perform(tag) {
      const appointmentNo = target(tag);
      await openDaySheet(s, date);
      const bill = s.schedule.locator(`a[onclick*="/billing?billRegion"][onclick*="appointment_no=${appointmentNo}&"]`).first();
      await bill.waitFor({ state: 'attached', timeout: 20000 });
      const page = await s.popup(s.schedule, bill, 'bill-form');
      await page.waitForLoadState('domcontentloaded', { timeout: TIMEOUT });
      await page.locator('select[name="xml_billtype"]').waitFor({ state: 'visible', timeout: TIMEOUT });
      const physician = page.locator('select[name="xml_provider"]');
      const options = await physician.locator('option').evaluateAll((es) => es.map((e) => e.value));
      const own = options.find((value) => value.split('|')[0] === s.provider);
      await physician.selectOption(own || options.find((value) => /^-?\d+\|\d+$/.test(value)));
      const billType = await page.locator('select[name="xml_billtype"] option').evaluateAll((es) => (es.find((e) => e.value.startsWith('ODP')) || {}).value);
      await page.locator('select[name="xml_billtype"]').selectOption(billType);
      await page.locator('a[onclick*="showHideLayers(\'Layer1\',\'\',\'show\')"]').first().click();
      await page.locator('#Layer1').waitFor({ state: 'visible', timeout: 15000 });
      await page.locator('#Layer1 a', { hasText: formName }).first().click();
      await page.waitForFunction(() => document.getElementById('Layer1').style.visibility !== 'visible', null, { timeout: 15000 });
      await page.locator(`input[name="xml_${code}"]:visible`).first().check();
      await page.locator('input[name="dxCode"]').fill(process.env.BILLING_DX_CODE || '250');
      await page.locator('input[name="dxCode"]').dispatchEvent('change');
      await Promise.all([page.waitForURL(/ViewBillingONReview/, { timeout: TIMEOUT }),
        page.locator('#titlesearch input[type="submit"][name="submit"]').click()]);
      await page.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
      await h.assertNotErrorPage(page, 'bill review');
      const save = page.locator('form[name="titlesearch"] input[type="submit"][value="Save"]');
      h.assert(await save.count(), 'The review page did not offer Save');
      const captured = await captureInContext(s.context, pathIs(this.route), () => save.click());
      await page.waitForLoadState('domcontentloaded', { timeout: TIMEOUT }).catch(() => {});
      if (!page.isClosed()) await page.close().catch(() => {});
      return captured;
    },
    aim(captured, tag) {
      const appointmentNo = target(tag);
      const overrides = {};
      for (const name of ['appointment_no', 'appointmentNo', 'appointment_no_1']) {
        if (captured.body.has(name)) overrides[name] = appointmentNo;
      }
      h.assert(Object.keys(overrides).length, 'The captured bill save names no appointment to aim the replay at');
      return { overrides };
    },
  };
}

// ---------------------------------------------------------------------------------------------
// Chart writes (authz: tickler, note, Rx, allergy; csrf: eForm, consultation, prevention, measurement)

function ticklerAdd(s) {
  const prefix = `${s.marker}-TA-`;
  const like = q(`${prefix}%`);
  const ids = () => s.sql.rows(`SELECT tickler_no FROM tickler WHERE demographic_no=${s.patient} AND message LIKE ${like}`).map(([id]) => id);
  return {
    key: 'tickler-add', label: 'tickler add (tickler/DbTicklerAdd)', route: /\/tickler\/DbTicklerAdd$/, kind: 'create',
    table: 'tickler', where: (tag) => `demographic_no=${s.patient} AND message=${q(prefix + tag)}`,
    cleanup() {
      for (const id of ids()) {
        assertId(id, 'An owned tickler');
        s.sql.execute(`DELETE FROM tickler_comments WHERE tickler_no=${id}; DELETE FROM tickler_update WHERE tickler_no=${id};
          DELETE FROM ticklerdocs WHERE tickler_id=${id}`);
      }
      s.sql.execute(`DELETE FROM tickler WHERE demographic_no=${s.patient} AND message LIKE ${like}`);
      h.assert(ids().length === 0, 'Owned ticklers were not removed');
    },
    async perform(tag) {
      const list = await s.popup(s.master, s.master.locator('a[onclick*="/tickler/ViewTicklerMain"]').first(), 'tickler-list');
      await list.waitForLoadState('domcontentloaded', { timeout: TIMEOUT });
      const add = await s.popup(list, list.locator('input.btn-primary[onclick*="/tickler/ViewAddTickler"]').first(), 'tickler-add');
      await add.locator('form[name="serviceform"]').waitFor({ state: 'visible', timeout: TIMEOUT });
      await add.locator('textarea[name="ticklerMessage"]').fill(prefix + tag);
      await add.locator('select[name="task_assigned_to"]').first().selectOption(s.provider);
      const captured = await captureInContext(s.context, pathIs(this.route),
        () => add.locator('input.btn-primary[name="Button"]').first().click());
      await add.waitForEvent('close', { timeout: TIMEOUT }).catch(() => {});
      for (const page of [add, list]) if (!page.isClosed()) await page.close().catch(() => {});
      return captured;
    },
    aim: (captured, tag) => ({ overrides: { ticklerMessage: prefix + tag } }),
  };
}

function chartNoteSave(s) {
  const prefix = `${s.marker}-NS-`;
  const like = q(`%${prefix}%`);
  const noteIds = () => s.sql.rows(`SELECT note_id FROM casemgmt_note WHERE demographic_no=${s.patient} AND note LIKE ${like}`).map(([id]) => id);
  return {
    key: 'note-save', label: 'chart note save (CaseManagementEntry)', route: /\/CaseManagementEntry$/, kind: 'create',
    table: 'casemgmt_note', where: (tag) => `demographic_no=${s.patient} AND note LIKE ${q(`%${prefix}${tag}%`)}`,
    cleanup() {
      const ids = noteIds();
      ids.forEach(id => assertId(id, 'An owned note'));
      if (ids.length) {
        const list = ids.join(',');
        s.sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${list}); DELETE FROM casemgmt_note_ext WHERE note_id IN (${list});
          DELETE FROM casemgmt_note_link WHERE note_id IN (${list}); DELETE FROM casemgmt_note WHERE note_id IN (${list}) AND demographic_no=${s.patient}`);
      }
      s.sql.execute(`DELETE FROM casemgmt_tmpsave WHERE demographic_no=${s.patient}; DELETE FROM casemgmt_note_lock WHERE demographic_no=${s.patient}`);
      h.assert(noteIds().length === 0, 'Owned chart notes were not removed');
    },
    async perform(tag) {
      const chart = await s.chart();
      const note = chart.locator('textarea[name="caseNote_note"]').first();
      await note.waitFor({ state: 'visible', timeout: TIMEOUT });
      await note.click();
      await note.fill(`${prefix}${tag} note text`);
      return captureInContext(s.context,
        (url) => this.route.test(url.pathname), () => chart.locator('#saveImg').first().click());
    },
    aim(captured, tag) {
      const field = ['caseNote_note', 'noteTxt', 'value'].find(name => captured.body.has(name));
      h.assert(field, 'The captured note save carries no note text field');
      return { overrides: { [field]: `${prefix}${tag} note text` } };
    },
  };
}

/**
 * Rx Save Only (rx/WriteScript?parameterValue=updateSaveAllDrugs). The save posts the drug form,
 * but WHAT it saves is the session's stash of staged cards, so a replay that is meant to write
 * stages a fresh custom drug in the same Rx window first and points the captured form at that
 * card (its random id renamed in every field, and the card's name set to the tag). A replay
 * that is meant to be refused is sent as captured with only the card's name set to the tag: the
 * privilege check runs before the stash is read (RxWriteScript2Action.execute, checkPrivilege then
 * resolveForWrite), and the owned count covers every drug the family's window could save.
 */
function rxSave(s) {
  const prefix = `${s.marker}-RX-`;
  let rx = null;
  const drugIds = () => s.sql.rows(`SELECT drugid FROM drugs WHERE demographic_no=${s.patient}`).map(([id]) => id);
  async function stage(name) {
    const before = await rx.locator("[id^='drugName_']").evaluateAll((els) => els.map((el) => el.id));
    await rx.locator('#searchString').fill(name);
    // The Custom Drug button confirms first; accept it as the prescriber does.
    await h.withExpectedDialogs(rx, () => rx.locator('#customDrug').click(), { accept: true });
    await rx.waitForFunction((count) => document.querySelectorAll("[id^='drugName_']").length > count, before.length, { timeout: TIMEOUT });
    const added = (await rx.locator("[id^='drugName_']").evaluateAll((els) => els.map((el) => el.id))).filter((id) => !before.includes(id));
    h.assert(added.length === 1, `Staging a custom drug added ${added.length} cards`);
    const card = added[0].slice('drugName_'.length);
    await rx.locator(`#instructions_${card}`).fill('1 tab PO BID x 14 days');
    // Leaving the instructions box posts them to the stash (rx/UpdateScript), which moves the card's
    // revision; the save is sent only once that answer is in, as when a prescriber clicks on. The
    // click lands on the Indication label (as double-submit-rx does), not Tab into Quantity: a
    // quantity box that still has focus posts updateQty when the window later loses focus, and for
    // a card a replayed save has already taken from the stash that request ends in a logged NPE.
    const [updated] = await Promise.all([
      rx.waitForResponse((response) => response.request().method() === 'POST' && /\/rx\/UpdateScript$/.test(new URL(response.url()).pathname),
        { timeout: TIMEOUT }),
      rx.locator(`label[for="jsonDxSearch_${card}"]`).click(),
    ]);
    h.assert(updated.ok(), `Posting the instructions answered HTTP ${updated.status()}`);
    return card;
  }
  return {
    key: 'rx-save', label: 'Rx Save Only (rx/WriteScript updateSaveAllDrugs)', route: /\/rx\/WriteScript$/, kind: 'create',
    // Every drug the family's Rx window can save carries the prefix (each card is named for its
    // tag), so a replay that wrote ANY of them -- the tagged one, or a card still in a stash --
    // moves this count; `tag` only names the replay in messages.
    table: 'drugs', where: () => `demographic_no=${s.patient} AND customName LIKE ${q(`${prefix}%`)}`,
    cleanup() {
      const drugs = drugIds();
      drugs.forEach(id => assertId(id, 'An owned drug'));
      const notes = s.sql.rows(`SELECT note_id FROM casemgmt_note WHERE demographic_no=${s.patient}`).map(([id]) => id);
      notes.forEach(id => assertId(id, 'An owned note'));
      if (notes.length) {
        s.sql.execute(`DELETE FROM casemgmt_note_link WHERE note_id IN (${notes.join(',')});
          DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes.join(',')});
          DELETE FROM casemgmt_note WHERE demographic_no=${s.patient} AND note_id IN (${notes.join(',')})`);
      }
      s.sql.execute(`DELETE FROM drugReason WHERE demographicNo=${s.patient};
        ${drugs.length ? `DELETE FROM partial_date WHERE table_name=2 AND table_id IN (${drugs.join(',')});` : ''}
        DELETE FROM drugs WHERE demographic_no=${s.patient}; DELETE FROM prescription WHERE demographic_no=${s.patient};
        DELETE FROM DigitalSignature WHERE demographicId=${s.patient} AND ModuleType='PRESCRIPTION'`);
      h.assert(s.sql.value(`SELECT (SELECT COUNT(*) FROM drugs WHERE demographic_no=${s.patient})
        + (SELECT COUNT(*) FROM prescription WHERE demographic_no=${s.patient})`) === '0', 'Owned Rx rows were not removed');
    },
    async perform(tag) {
      rx = await s.popup(s.master, s.master.locator('a[onclick*="/rx/choosePatient"]').first(), 'rx-module');
      await rx.locator('#searchString').waitFor({ state: 'visible', timeout: TIMEOUT });
      await rx.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
      this.card = await stage(prefix + tag);
      return captureInContext(s.context,
        (url) => this.route.test(url.pathname) && url.searchParams.get('parameterValue') === 'updateSaveAllDrugs',
        () => rx.locator('#saveOnlyButton').click());
    },
    async aim(captured, tag, { write } = {}) {
      if (!write) {
        // Refused replays go as captured, with the card renamed to the tag so the request names a
        // drug that is not already in the table.
        const name = `drugName_${this.card}`;
        h.assert(captured.body.has(name), 'The captured Rx save does not carry the staged card it saved');
        return { overrides: { [name]: prefix + tag } };
      }
      const card = await stage(prefix + tag);
      const overrides = {};
      for (const name of new Set(captured.body.keys())) {
        if (!name.endsWith(`_${this.card}`)) continue;
        const renamed = `${name.slice(0, -this.card.length)}${card}`;
        overrides[name] = null;
        overrides[renamed] = captured.body.getAll(name).map((value) => (name.startsWith('drugName_') ? prefix + tag : value));
      }
      if (captured.body.has('randomId')) overrides.randomId = card;
      h.assert(Object.keys(overrides).length, 'The captured Rx save names no staged card to point at the new one');
      // Each card carries the stash revision it was rendered from (STALE_RX_STASH otherwise): the
      // new card's own, as its page holds it.
      const revision = rx.locator(`input[name="draftRevision_${card}"]`).first();
      if (await revision.count()) overrides[`draftRevision_${card}`] = await revision.inputValue();
      return { overrides };
    },
  };
}

function allergyAdd(s) {
  const prefix = `${s.marker}-AL-`;
  return {
    key: 'allergy-add', label: 'allergy add (rx/addAllergy2)', route: /\/rx\/addAllergy2$/, kind: 'create',
    table: 'allergies', where: (tag) => `demographic_no=${s.patient} AND reaction=${q(prefix + tag)}`,
    cleanup() {
      s.sql.execute(`DELETE FROM allergies WHERE demographic_no=${s.patient} AND reaction LIKE ${q(`${prefix}%`)}`);
      h.assert(s.sql.value(`SELECT COUNT(*) FROM allergies WHERE demographic_no=${s.patient}
        AND reaction LIKE ${q(`${prefix}%`)}`) === '0', 'Owned allergies were not removed');
    },
    async perform(tag) {
      const chart = await s.chart();
      const page = await s.popup(chart, chart.locator('a[onclick*="showAllergy"]').first(), 'allergy-list');
      const form = page.locator('#RxAddAllergyForm');
      await page.locator('#searchString').fill(s.marker.slice(-16).toUpperCase());
      await h.withExpectedDialogs(page, () => page.locator('input[value="Custom Allergy"]').click());
      await form.waitFor({ state: 'visible', timeout: TIMEOUT });
      await form.locator('#reactionDescription').fill(prefix + tag);
      await form.locator('[name="nonDrug"]').selectOption('on');
      await form.locator('[name="severityOfReaction"]').selectOption('3');
      await form.locator('[name="onSetOfReaction"]').selectOption('1');
      await form.locator('[name="lifeStage"]').selectOption('A');
      await form.locator('#startDate').fill('2026-01-02');
      await form.locator('#startDate').press('Escape');
      await form.locator('#startDate').press('Tab');
      const captured = await captureInContext(s.context, pathIs(this.route), () => form.locator('input[type="submit"]').first().click());
      await page.waitForLoadState('domcontentloaded', { timeout: TIMEOUT }).catch(() => {});
      if (!page.isClosed()) await page.close().catch(() => {});
      return captured;
    },
    aim(captured, tag) {
      const field = ['reactionDescription', 'reaction'].find(name => captured.body.has(name));
      h.assert(field, 'The captured allergy add carries no reaction field');
      return { overrides: { [field]: prefix + tag } };
    },
  };
}

/**
 * Provider update (admin/ProviderUpdate) of a provider the check owns: the target is a second
 * throwaway login of the authz fixture (`target` = {providerNo}), so no real provider is edited.
 */
function providerUpdate(s, { target }) {
  // provider.team is varchar(20): the run marker's last eight hex digits keep the tag unique.
  const prefix = `PW${s.marker.slice(-8)}`;
  const no = () => q(target.providerNo);
  return {
    key: 'provider-update', label: 'provider update (admin/ProviderUpdate)', route: /\/admin\/ProviderUpdate$/, kind: 'update',
    table: 'provider', where: (tag) => `provider_no=${no()} AND team=${q(prefix + tag)}`,
    cleanup() {
      // The fixture removes the login's own rows; the edit page also writes these.
      s.sql.execute(`DELETE FROM providerArchive WHERE provider_no=${no()}; DELETE FROM providerbillcenter WHERE provider_no=${no()}`);
      h.assert(s.sql.value(`SELECT (SELECT COUNT(*) FROM providerArchive WHERE provider_no=${no()})
        + (SELECT COUNT(*) FROM providerbillcenter WHERE provider_no=${no()})`) === '0', 'Owned provider edit rows were not removed');
    },
    async perform(tag) {
      const { page: admin } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('#admin-panel,#admin2').first(),
        { context: s.context, recorder: s.recorder, label: 'provider-admin', timeout: TIMEOUT });
      const link = admin.locator('#adminNav').getByRole('link', { name: 'Search/Edit Provider Records', exact: true, includeHidden: true });
      await revealAuditLink(admin, link, TIMEOUT);
      await link.click();
      const iframe = admin.locator('#dynamic-content iframe').first();
      await iframe.waitFor({ timeout: TIMEOUT });
      const frame = await (await iframe.elementHandle()).contentFrame();
      h.assert(frame, 'The provider search frame did not load');
      await frame.locator('input[name="keyword"]').waitFor({ timeout: TIMEOUT });
      const navigate = async (locator) => {
        const navigated = admin.waitForEvent('framenavigated', { predicate: f => f === frame, timeout: TIMEOUT });
        navigated.catch(() => {});
        await locator.click();
        await navigated;
        await frame.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
      };
      await frame.locator('input[name="search_mode"][value="search_providerno"]').check();
      await frame.locator('input[name="keyword"]').fill(target.providerNo);
      await navigate(frame.locator('input[name="button"], button[name="button"]').first());
      await navigate(frame.getByRole('link', { name: target.providerNo, exact: true }));
      const form = frame.locator('form[name="updatearecord"]');
      h.assert(await form.locator('input[name="provider_no"]').inputValue() === target.providerNo, 'The edit form holds another provider');
      await form.locator('input[name="team"]').fill(prefix + tag);
      const captured = await captureInContext(s.context, pathIs(this.route), () => navigate(form.locator('input[name="subbutton"]')));
      if (admin !== s.schedule && !admin.isClosed()) await admin.close().catch(() => {});
      else await h.gotoApp(s.schedule, s.config.baseUrl, '/provider/providercontrol?displaymode=day&dboperation=searchappointmentday&viewall=1');
      return captured;
    },
    aim: (captured, tag) => ({ overrides: { team: prefix + tag } }),
  };
}

/**
 * eForm Submit from the floating toolbar (eform/addEForm) of an eForm template the check owns.
 * Every rendering is issued a one-time submission id (EFormSubmissionGuard) bound to the session,
 * the template and the patient, and the UI's save consumes its own; so each replay is aimed at a
 * fresh rendering of the same form, opened from the same list, whose id is unclaimed. Without
 * that, a replay that got past CSRFGuard would be turned away as a duplicate (409) and write
 * nothing, and the row count could not tell a refusal from a stale id.
 */
function eformAdd(s) {
  const prefix = `${s.marker}-EF-`;
  const formName = `${s.marker} csrf form`;
  const html = '<html><head><title>csrf fixture</title></head><body>'
    + `<form method="post" action="" name="FormName" id="FormName"><h2>${s.marker.replace(/-/g, '')}</h2>`
    + '<input type="text" name="subject" id="subject"><input type="text" name="note" id="note">'
    + '<input type="submit" value="Submit" id="SubmitButton"></form></body></html>';
  let fid;
  let list;
  async function render() {
    const form = await s.popup(list, list.locator('#efmTable a').filter({ hasText: formName }).first(), 'eform-fill');
    await form.locator('#remoteSubmitButton').waitFor({ state: 'visible', timeout: TIMEOUT });
    return form;
  }
  return {
    key: 'eform-add', label: 'eForm submit (eform/addEForm)', route: /\/eform\/addEForm$/i, kind: 'create',
    table: 'eform_data', where: (tag) => `demographic_no=${s.patient} AND form_name=${q(formName)} AND subject=${q(prefix + tag)}`,
    prepare() {
      fid = s.sql.value(`INSERT INTO eform(form_name,file_name,subject,form_date,form_time,form_creator,status,form_html,
        showLatestFormOnly,patient_independent,roleType,restrictToProgram,stable)
        VALUES(${q(formName)},'','csrf fixture',CURDATE(),CURTIME(),${q(s.provider)},1,${q(html)},0,0,'',0,1); SELECT LAST_INSERT_ID()`);
      assertId(fid, 'The eForm template fixture');
    },
    cleanup() {
      const fdids = s.sql.rows(`SELECT fdid FROM eform_data WHERE demographic_no=${s.patient} AND form_name=${q(formName)}`).map((r) => r[0]);
      for (const fdid of fdids) {
        assertId(fdid, 'An owned eForm instance');
        s.sql.execute(`DELETE FROM eform_values WHERE fdid=${fdid}; DELETE FROM eform_data WHERE fdid=${fdid} AND demographic_no=${s.patient}`);
      }
      if (fid) s.sql.execute(`DELETE FROM eform WHERE fid=${fid} AND form_name=${q(formName)}`);
      h.assert(s.sql.value(`SELECT (SELECT COUNT(*) FROM eform WHERE form_name=${q(formName)})
        + (SELECT COUNT(*) FROM eform_data WHERE form_name=${q(formName)})`) === '0', 'Owned eForm rows were not removed');
    },
    async perform(tag) {
      const chart = await s.chart();
      // The Add eForm list stays open: unloading it throws on a null window.opener (covered by eform-groups).
      list = await s.popup(chart, chart.locator('#menuTitleeforms a').first(), 'eform-add-list');
      await list.locator('#efmTable').waitFor({ timeout: TIMEOUT });
      const form = await render();
      await form.locator('#remote_eform_subject').fill(prefix + tag);
      await form.locator('#note').fill('csrf matrix');
      const captured = await captureInContext(s.context, pathIs(this.route), () => form.locator('#remoteSubmitButton').click());
      await form.waitForEvent('close', { timeout: 10000 }).catch(() => {});
      if (!form.isClosed()) await form.close().catch(() => {});
      return captured;
    },
    async aim(captured, tag) {
      h.assert(captured.body.has('carlosEformSubmission'), 'The captured eForm submit carries no submission id');
      const form = await render();
      const fresh = await form.locator('input[name="carlosEformSubmission"]').first().inputValue();
      await form.close().catch(() => {});
      h.assert(/^[0-9a-f-]{36}$/.test(fresh) && fresh !== captured.body.get('carlosEformSubmission'),
        'A fresh rendering of the eForm was not issued its own submission id');
      const overrides = { carlosEformSubmission: fresh };
      for (const name of ['subject', 'remote_eform_subject']) if (captured.body.has(name)) overrides[name] = prefix + tag;
      h.assert(overrides.subject || overrides.remote_eform_subject, 'The captured eForm submit carries no subject');
      return { overrides };
    },
  };
}

function messengerSend(s) {
  const prefix = `${s.marker}-MS-`;
  const ids = () => s.sql.rows(`SELECT messageid FROM messagetbl WHERE thesubject LIKE ${q(`${prefix}%`)}`).map(([id]) => id);
  // Demo installs ship orphan delivery rows whose message ids a new message can reuse; only rows
  // above these marks are this run's.
  const listMark = Number(s.sql.value('SELECT IFNULL(MAX(id), 0) FROM messagelisttbl'));
  const demoMapMark = Number(s.sql.value('SELECT IFNULL(MAX(id), 0) FROM msgDemoMap'));
  return {
    key: 'messenger-send', label: 'messenger send (messenger/CreateMessage)', route: /\/messenger\/CreateMessage$/, kind: 'create',
    table: 'messagetbl', where: (tag) => `thesubject=${q(prefix + tag)}`,
    cleanup() {
      const list = ids();
      list.forEach(id => assertId(id, 'An owned message'));
      if (list.length) {
        s.sql.execute(`DELETE FROM messagelisttbl WHERE message IN (${list.join(',')}) AND id > ${listMark};
          DELETE FROM msgDemoMap WHERE messageID IN (${list.join(',')}) AND id > ${demoMapMark};
          DELETE FROM messagetbl WHERE messageid IN (${list.join(',')})`);
      }
      h.assert(ids().length === 0, 'Owned messages were not removed');
    },
    async perform(tag) {
      const { page: inbox, isPopup } = await ui.clickOpensPopupOrNavigates(s.schedule, s.schedule.locator('a:has(#oscar_new_msg)').first(),
        { context: s.context, recorder: s.recorder, label: 'messenger-inbox', timeout: TIMEOUT });
      await inbox.waitForLoadState('domcontentloaded', { timeout: TIMEOUT });
      await ui.clickAndAwaitReload(inbox, inbox.locator('a[href*="/messenger/ViewCreateMessage"]').first(), { required: false });
      await inbox.locator('#subject').waitFor({ state: 'visible', timeout: TIMEOUT });
      await inbox.locator(`input[name="provider"][id^="0-"][value^="${s.provider}-"]`).first().check();
      await inbox.locator('#subject').fill(prefix + tag);
      const editor = inbox.locator('.toastui-editor-ww-container .ProseMirror').first();
      await editor.waitFor({ state: 'visible', timeout: 15000 });
      await editor.click();
      await inbox.keyboard.type('csrf matrix body');
      const captured = await captureInContext(s.context, pathIs(this.route),
        () => inbox.locator('button[type="submit"]', { hasText: /Send Message/i }).click());
      await inbox.waitForLoadState('domcontentloaded', { timeout: TIMEOUT }).catch(() => {});
      if (isPopup) { if (!inbox.isClosed()) await inbox.close().catch(() => {}); }
      else await h.gotoApp(s.schedule, s.config.baseUrl, '/provider/providercontrol?displaymode=day&dboperation=searchappointmentday&viewall=1');
      return captured;
    },
    aim: (captured, tag) => ({ overrides: { subject: prefix + tag } }),
  };
}

function consultationRequest(s) {
  const prefix = `${s.marker}-CR-`;
  const like = q(`${prefix}%`);
  const ids = () => s.sql.rows(`SELECT requestId FROM consultationRequests WHERE demographicNo=${s.patient} AND reason LIKE ${like}`).map(([id]) => id);
  async function pickFirst(page, selector) {
    await page.locator(selector).click();
    const item = page.locator('ul.ui-autocomplete:visible li.ui-menu-item').first();
    await item.waitFor({ state: 'visible', timeout: 15000 });
    await item.click();
    await page.waitForTimeout(250);
  }
  return {
    key: 'consultation-request', label: 'consultation request (encounter/RequestConsultation)', route: /\/encounter\/RequestConsultation$/,
    kind: 'create', table: 'consultationRequests', where: (tag) => `demographicNo=${s.patient} AND reason=${q(prefix + tag)}`,
    cleanup() {
      const list = ids();
      list.forEach(id => assertId(id, 'An owned consultation request'));
      if (list.length) {
        const present = new Set(s.sql.rows(`SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()
          AND table_name IN ('consultdocs','consultationRequestExt','consultationRequestExtArchive')`).map(([name]) => name));
        for (const table of present) s.sql.execute(`DELETE FROM ${table} WHERE requestId IN (${list.join(',')})`);
        s.sql.execute(`DELETE FROM consultationRequests WHERE requestId IN (${list.join(',')}) AND demographicNo=${s.patient}`);
      }
      s.sql.execute(`DELETE FROM DigitalSignature WHERE demographicId=${s.patient} AND ModuleType='CONSULTATION'`);
      h.assert(ids().length === 0, 'Owned consultation requests were not removed');
    },
    async perform(tag) {
      const chart = await s.chart();
      const form = await s.popup(chart, chart.locator('a[onclick*="ViewConsultationFormRequest?de="]').first(), 'consult-form');
      await form.locator('#EctConsultationFormRequest2Form').waitFor({ state: 'attached', timeout: TIMEOUT });
      await pickFirst(form, '#serviceInput');
      await pickFirst(form, '#specialistInput');
      await form.locator('#urgency').selectOption('1');
      await form.locator('textarea[name="reasonForConsultation"]').fill(prefix + tag);
      const captured = await captureInContext(s.context, pathIs(this.route), () => form.locator('input[name="submitSaveOnly"]').click());
      await form.waitForLoadState('domcontentloaded', { timeout: TIMEOUT }).catch(() => {});
      if (!form.isClosed()) await form.close().catch(() => {});
      return captured;
    },
    aim: (captured, tag) => ({ overrides: { reasonForConsultation: prefix + tag } }),
  };
}

function preventionAdd(s) {
  const prefix = `${s.marker}-PV-`;
  return {
    key: 'prevention-add', label: 'prevention add (prevention/AddPrevention)', route: /\/prevention\/AddPrevention$/, kind: 'create',
    table: 'preventions',
    where: (tag) => `demographic_no=${s.patient} AND deleted='0' AND id IN (SELECT prevention_id FROM preventionsExt
      WHERE keyval='comments' AND val=${q(prefix + tag)})`,
    cleanup() {
      s.sql.execute(`DELETE x FROM preventionsExt x JOIN preventions p ON p.id=x.prevention_id WHERE p.demographic_no=${s.patient};
        DELETE FROM preventions WHERE demographic_no=${s.patient}`);
      h.assert(s.sql.value(`SELECT COUNT(*) FROM preventions WHERE demographic_no=${s.patient}`) === '0', 'Owned preventions were not removed');
    },
    async perform(tag) {
      const chart = await s.chart();
      const list = await s.popup(chart, chart.locator('a[onclick*="ViewPreventionIndex"]').first(), 'prevention-index');
      await list.locator('#immunization').fill('Fluzone');
      const editor = await s.popup(list,
        list.locator('#immunization_choices [class*="item"], #immunization_choices div, #immunization_choices li').first(), 'prevention-editor');
      await editor.locator('[name="given"][value="given"]').check();
      await editor.locator('#prevDate').fill('2026-01-05');
      await editor.locator('[name="comments"]').fill(prefix + tag);
      const captured = await captureInContext(s.context, pathIs(this.route),
        () => editor.locator('input[type="submit"][name="action"]').first().click());
      await editor.waitForEvent('close', { timeout: 10000 }).catch(() => {});
      for (const page of [editor, list]) if (!page.isClosed()) await page.close().catch(() => {});
      return captured;
    },
    aim: (captured, tag) => ({ overrides: { comments: prefix + tag } }),
  };
}

function measurementSave(s) {
  const prefix = `${s.marker}-ME-`;
  return {
    key: 'measurement-save', label: 'measurement save (encounter/Measurements)', route: /\/encounter\/Measurements$/, kind: 'create',
    table: 'measurements', where: (tag) => `demographicNo=${s.patient} AND type='BP' AND comments=${q(prefix + tag)}`,
    cleanup() {
      s.sql.execute(`DELETE FROM measurements WHERE demographicNo=${s.patient}; DELETE FROM measurementsDeleted WHERE demographicNo=${s.patient}`);
      h.assert(s.sql.value(`SELECT COUNT(*) FROM measurements WHERE demographicNo=${s.patient}`) === '0', 'Owned measurements were not removed');
    },
    async perform(tag) {
      const chart = await s.chart();
      await chart.locator('#menuTitle3 a').hover();
      const item = chart.locator('#menu3 a.menuItemleft').filter({ hasText: 'Vitals' });
      await item.waitFor({ state: 'visible', timeout: TIMEOUT });
      const group = await s.popup(chart, item, 'vitals-entry');
      const row = group.locator('#row-BP');
      await row.waitFor({ state: 'visible', timeout: TIMEOUT });
      await row.locator('input[name^="inputValue-"]').fill('128/82');
      await row.locator('input[name^="date-"]').fill('2026-03-04');
      await row.locator('input[name^="comments-"]').fill(prefix + tag);
      this.commentsField = await row.locator('input[name^="comments-"]').getAttribute('name');
      const captured = await captureInContext(s.context, pathIs(this.route),
        () => group.getByRole('button', { name: 'Submit', exact: true }).click());
      await group.waitForEvent('close', { timeout: 10000 }).catch(() => {});
      if (!group.isClosed()) await group.close().catch(() => {});
      return captured;
    },
    aim(captured, tag) {
      h.assert(this.commentsField && captured.body.has(this.commentsField), 'The captured measurement save carries no comments field');
      return { overrides: { [this.commentsField]: prefix + tag } };
    },
  };
}

module.exports = {
  allergyAdd, appointmentAdd, appointmentDelete, appointmentUpdate, billingOnSave, captureInContext, chartNoteSave,
  consultationRequest, demographicAdd, demographicUpdate, eformAdd, futureDate, measurementSave, messengerSend,
  ownedAppointment, preventionAdd, providerUpdate, removeAppointments, rxSave, ticklerAdd,
};
