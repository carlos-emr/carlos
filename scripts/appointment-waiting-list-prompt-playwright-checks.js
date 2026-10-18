#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// appointment-waiting-list-prompt: booking an appointment for a patient who is on a waiting list. After the
// booking is saved, the result page (AppointmentAddRecord2Action ▸ appointmentaddarecord.jsp) asks "Click OK to
// remove patient from the waiting list: <list name>"; OK posts waitinglist/RemoveFromWaitingList. The clinic can
// turn the question off with MANUALLY_CLEANUP_WL=yes in carlos.properties. waiting-list covers the Master Record's
// own waiting-list controls; nothing else books an appointment for a waiting-list patient.
// User path: Schedule ▸ a time slot (add-appointment popup) ▸ the patient (keyword search) ▸ Add Appointment.
//   1. the booking is saved and the question names the list as written (the list name carries an apostrophe);
//      Cancel keeps the patient on the list;
//   2. a second booking ▸ OK: the patient is taken off the list (the waiting-list row becomes history);
//   3. LAST: the pages on the way link only stylesheets the webapp ships. Their missing-stylesheet failures are
//      moved out of the recorder after each step and asserted here (finding 100), as immunization-schedule-config does.
// With MANUALLY_CLEANUP_WL=yes (set this environment variable to mirror the server's carlos.properties):
//   1. the booking is saved, no question is asked, and the patient stays on the list.
// Fixtures: the owned FAKE- patient, a marker-named waiting-list name (for the login's schedule group) and the
// patient's row on it, seeded by SQL as waiting-list does. Cleanup deletes the patient's appointments this check
// booked (by its reason), the waiting-list rows and the list name, and asserts none remain. An active list name
// makes every booking look for waiting-list rows, so the check runs alone (EXCLUSIVE).
const h = require('./lib/playwright-harness');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const q = h.sqlString;
const POSITIVE = /^[1-9]\d*$/;

// Stylesheets legacy JSPs link but the webapp does not ship (finding 100). Their failures are moved out of the
// recorder after each step and asserted in the last step, not ignored.
const MISSING_STYLESHEETS = ['/styles.css'];

function deferMissingStylesheets(recorder, contextPath, deferred) {
  const urls = MISSING_STYLESHEETS.map(path => contextPath + path);
  const isMissing = url => { try { return urls.includes(new URL(url).pathname); } catch { return false; } };
  const take = (list, predicate) => {
    for (let i = list.length - 1; i >= 0; i--) if (predicate(list[i])) deferred.push(...list.splice(i, 1));
  };
  take(recorder.requestFailures, entry => entry.resourceType === 'stylesheet' && isMissing(entry.url));
  take(recorder.badResponses, entry => entry.resourceType === 'stylesheet' && isMissing(entry.url));
  take(recorder.consoleIssues, entry => {
    const match = /^Refused to apply style from '([^']+)'/.exec(entry.text);
    return Boolean(match && isMissing(match[1]));
  });
}

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  const manualCleanup = (process.env.MANUALLY_CLEANUP_WL || '').toLowerCase() === 'yes';
  const listName = `${marker} O'Test list`;
  const reason = `${marker} booked`;
  const contextPath = new URL(s.config.baseUrl).pathname.replace(/\/$/, '');
  const deferred = [];
  const step = (label, body) => s.step(label, async () => {
    await body();
    deferMissingStylesheets(s.recorder, contextPath, deferred);
  });
  let listId;

  const booked = () => sql.value(`SELECT COUNT(*) FROM appointment WHERE demographic_no=${patient} AND reason=${q(reason)}`);
  const onList = history => sql.value(`SELECT COUNT(*) FROM waitingList WHERE listID=${listId} AND demographic_no=${patient}
    AND is_history=${q(history)}`);
  s.cleanup(() => {
    sql.execute(`DELETE FROM appointmentArchive WHERE demographic_no=${patient} AND reason=${q(reason)};
      DELETE FROM appointment WHERE demographic_no=${patient} AND reason=${q(reason)}`);
    if (listId) {
      sql.execute(`DELETE FROM waitingList WHERE listID=${listId} AND demographic_no=${patient};
        DELETE FROM waitingListName WHERE ID=${listId} AND name=${q(listName)}`);
    }
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM appointment WHERE demographic_no=${patient} AND reason=${q(reason)})
      + (SELECT COUNT(*) FROM appointmentArchive WHERE demographic_no=${patient} AND reason=${q(reason)})
      + (SELECT COUNT(*) FROM waitingListName WHERE name=${q(listName)})
      + ${listId ? `(SELECT COUNT(*) FROM waitingList WHERE listID=${listId})` : '0'}`) === '0',
    'Owned appointment or waiting-list rows remain');
  });

  const group = sql.value(`SELECT COALESCE(MAX(myGroupNo), '') FROM ProviderPreference WHERE providerNo=${q(provider)}`) || '';
  listId = sql.value(`INSERT INTO waitingListName (name, group_no, provider_no, create_date, is_history)
    VALUES (${q(listName)}, ${q(group)}, ${q(provider)}, NOW(), 'N'); SELECT LAST_INSERT_ID()`);
  h.assert(POSITIVE.test(listId || ''), 'The waiting-list name fixture was not created');
  sql.execute(`INSERT INTO waitingList (listID, demographic_no, note, position, onListSince, is_history)
    VALUES (${listId}, ${patient}, ${q(`${marker} on the list`)}, 1, NOW(), 'N')`);
  const lastName = sql.value(`SELECT last_name FROM demographic WHERE demographic_no=${patient}`);

  /** Books an appointment for the owned patient from the Schedule; answers the waiting-list question as told. */
  async function book(label, accept) {
    const before = Number(booked());
    // A previous booking's result page calls opener.refresh(), which may still be reloading the Schedule.
    await s.schedule.reload({ waitUntil: 'domcontentloaded' }).catch(async () => {
      await s.schedule.waitForLoadState('domcontentloaded');
    });
    await s.schedule.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    const slots = s.schedule.locator(`a.adhour[onclick*="provider_no=${provider}&"]`);
    await slots.first().waitFor({ state: 'attached', timeout: 20000 }).catch(() => {});
    h.assert(await slots.count() > 0, `The Schedule offers no bookable slot for provider ${provider}`);
    // The last slot keeps clear of the demo data's morning bookings; a double booking only warns.
    const popup = await s.popup(s.schedule, slots.last(), label);
    await popup.locator('form#addappt #keyword').waitFor({ state: 'visible' });
    // The keyword search page, as double-submit-appointment uses: the type-ahead lists only patients in the
    // login's programs, and the owned patient is seeded without an admission.
    await popup.locator('#keyword').fill(lastName);
    await clickAndAwaitReload(popup, popup.locator('#searchBtn'), { required: false });
    const pick = popup.locator(`table tr input[type="button"][name="pick_demographic"][value="${patient}"]`).first();
    await pick.waitFor({ state: 'visible', timeout: 30000 });
    await clickAndAwaitReload(popup, pick, { required: false });
    await popup.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    h.assert(await popup.locator('#demographic_no').inputValue() === String(patient), 'The booking form chose another patient');
    await popup.locator('#reason').fill(reason);
    const removals = [];
    popup.on('request', request => {
      if (new URL(request.url()).pathname.endsWith('/waitinglist/RemoveFromWaitingList')) removals.push(request);
    });
    // The result page asks its question, then calls self.close(); the popup closing ends the booking.
    const dialogs = await h.withExpectedDialogs(popup, async () => {
      const closed = popup.waitForEvent('close', { timeout: 30000 });
      await popup.locator('#addButton').click();
      await closed;
    }, { accept });
    await expectValue(sql, `SELECT COUNT(*) FROM appointment WHERE demographic_no=${patient} AND reason=${q(reason)}`,
      String(before + 1), `The ${label} booking was not saved`);
    return { dialogs: dialogs.filter(entry => entry.type === 'confirm'), others: dialogs.filter(entry => entry.type !== 'confirm'), removals };
  }

  if (manualCleanup) {
    await step('with MANUALLY_CLEANUP_WL=yes, booking a waiting-list patient asks nothing and keeps them on the list', async () => {
      const { dialogs, removals } = await book('add-appointment-manual-cleanup', false);
      h.assert(dialogs.length === 0, `The booking asked ${JSON.stringify(dialogs.map(entry => entry.text))}`);
      h.assert(removals.length === 0 && onList('N') === '1' && onList('Y') === '0',
        'The patient was taken off the waiting list although the clinic cleans it up by hand');
    });
  } else {
    await askedSteps();
  }

  await s.step('the booking and waiting-list pages link only stylesheets the webapp ships', async () => {
    const pages = [...new Set(deferred.map(entry => entry.label))].join(', ');
    h.assert(deferred.length === 0, `${pages || 'A page'} linked a stylesheet the webapp does not ship `
      + `(${MISSING_STYLESHEETS.join(', ')}): ${deferred.length} failure(s)`);
  });

  async function askedSteps() {
    const question = `Click OK to remove patient from the waiting list: ${listName}`;
    await step('booking a waiting-list patient asks to remove them, naming the list as written; Cancel keeps them on it', async () => {
      const { dialogs, others, removals } = await book('add-appointment-cancel', false);
      h.assert(dialogs.length === 1 && others.length === 0,
        `The booking asked ${JSON.stringify(dialogs.map(entry => entry.text))} (other dialogs: ${JSON.stringify(others)})`);
      h.assert(dialogs[0].text === question, `The question reads ${JSON.stringify(dialogs[0].text)}, not ${JSON.stringify(question)}`);
      h.assert(removals.length === 0 && onList('N') === '1' && onList('Y') === '0',
        'Cancel took the patient off the waiting list');
    });

    await step('a second booking ▸ OK takes the patient off the waiting list', async () => {
      const { dialogs, removals } = await book('add-appointment-ok', true);
      h.assert(dialogs.length === 1 && dialogs[0].text === question, `The booking asked ${JSON.stringify(dialogs.map(entry => entry.text))}`);
      await expectValue(sql, `SELECT CONCAT(
          (SELECT COUNT(*) FROM waitingList WHERE listID=${listId} AND demographic_no=${patient} AND is_history='N'), '|',
          (SELECT COUNT(*) FROM waitingList WHERE listID=${listId} AND demographic_no=${patient} AND is_history='Y'))`, '0|1',
      `OK did not take the patient off the waiting list (removal requests sent: ${removals.length}; `
        + `current rows ${onList('N')}, history rows ${onList('Y')})`);
    });
  }
}

if (require.main === module) runWorkflow('appointment-waiting-list-prompt', workflow, { openPatient: true });
module.exports = { workflow };
