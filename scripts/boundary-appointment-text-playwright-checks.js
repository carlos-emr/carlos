#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Special characters and field lengths on the appointment booking and edit popups (wave 6, boundary values).
 * User path (throwaway login, its own day sheet): Schedule > empty slot > Add Appointment popup > patient
 * search > reason / notes / resources > Add Appointment > the appointment's own link (edit popup) > Update.
 * Asserts: a reason and notes that fill their boxes exactly (80 and 255 UTF-16 units) and carry an
 * apostrophe, accents, CJK, an emoji, "&amp;", quotes, backslash, "%41", "+" and ";" are stored byte for
 * byte, redisplayed identically by the edit popup, and an unchanged Update leaves every column as it was.
 * Last (the defects): a reason and notes that fill their box with a line break inside are stored whole
 * (the browser counts a break as one character, submits two); a free-text appointment name and a resources
 * entry past their columns are refused or visibly limited, never silently cut.
 * Fixtures: the throwaway login (lib/throwaway-login-fixture.js) and the owned FAKE- patient; cleanup deletes
 * exact native appointment/archive identities and hashed-session slot monitors after browser closure, then
 * the throwaway and patient. Any ownership or cleanup uncertainty retains both marker parents.
 * Implements the wave-6 "boundary values" pattern, Part 1 (appointment name / reason / notes).
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const b = require('./lib/boundary-values');
const { runWorkflow } = require('./lib/workflow-session');
const { createUnbookedThrowaway } = require('./lib/gap-provider-fixture');
const { appointmentMonitorCleanup } = require('./lib/appointment-page-monitor-cleanup');
const { captureScheduleContext, assertScheduleContext } = require('./lib/native-schedule-context');

function positiveId(value, label) {
  const id = String(value);
  h.assert(/^[1-9]\d*$/.test(id) && id.length <= 10 && BigInt(id) <= 2147483647n,
    `The owned ${label} identity is invalid`);
  return id;
}

// Read the actual native slot before opening it: the first script in that popup already writes a lock.
function nativeSlot(onclick, baseUrl) {
  const match = String(onclick || '').match(/['"]([^'"]*\/appointment\/addappointment\?[^'"]+)['"]/);
  h.assert(match, 'The native slot has no Add Appointment URL');
  const text = match[1].replace(/\\(?:u([0-9a-f]{4})|x([0-9a-f]{2})|([\\/]))/gi,
    (_, unicode, hex, escaped) => escaped || String.fromCharCode(parseInt(unicode || hex, 16)));
  h.assert(!text.includes('\\'), 'The native slot URL has unsupported escaping');
  const base = new URL(baseUrl);
  const url = new URL(text, base);
  h.assert(url.origin === base.origin && url.pathname === `${base.pathname.replace(/\/$/, '')}/appointment/addappointment`,
    'The native slot is outside the configured application');
  const get = key => {
    h.assert(url.searchParams.getAll(key).length === 1, `The native slot has an ambiguous ${key}`);
    return url.searchParams.get(key);
  };
  const provider = positiveId(get('provider_no'), 'slot provider');
  const year = get('year'), month = get('month'), day = get('day');
  h.assert(/^\d{4}$/.test(year) && /^\d{1,2}$/.test(month) && /^\d{1,2}$/.test(day), 'The native slot date is invalid');
  const date = `${year}-${month.padStart(2, '0')}-${day.padStart(2, '0')}`;
  const parsed = new Date(`${date}T12:00:00Z`);
  h.assert(Number.isFinite(parsed.getTime()) && parsed.toISOString().slice(0, 10) === date, 'The native slot date is invalid');
  const start = get('start_time');
  let duration = get('duration');
  h.assert(/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(start), 'The native slot time is invalid');
  if (duration === '') {
    // A slot with no template code (a fresh provider's day sheet) carries no duration, and Add Appointment then
    // uses the provider's slot length (everyMin). The same URL's end_time is start + that length - 1, written
    // without carrying minutes past 59 (08:50 + 15 gives "08:64"), so it is read as a count of minutes.
    const end = /^(\d{1,2}):(\d{1,3})$/.exec(get('end_time'));
    h.assert(end, 'The native slot end time is invalid');
    duration = String(Number(end[1]) * 60 + Number(end[2]) - (Number(start.slice(0, 2)) * 60 + Number(start.slice(3))) + 1);
  }
  h.assert(/^[1-9]\d{0,3}$/.test(duration), 'The native slot time is invalid');
  const endMinutes = Number(start.slice(0, 2)) * 60 + Number(start.slice(3)) + Number(duration) - 1;
  h.assert(endMinutes < 1440, 'The native slot duration crosses midnight');
  const end = `${String(Math.floor(endMinutes / 60)).padStart(2, '0')}:${String(endMinutes % 60).padStart(2, '0')}`;
  return { provider, date, start, end, pageId: `${provider}|${date}|${start}|${end}` };
}

/** Capture exact native slots before their first scripts can create a page monitor. */
function installOwnedBoundaryCleanup(s, fixture) {
  const {sql, marker} = s;
  const patient = positiveId(s.patient, 'patient');
  const actor = positiveId(s.provider, 'patient owner');
  const provider = positiveId(fixture.providerNo, 'throwaway provider');
  const security = positiveId(fixture.securityNo, 'throwaway security');
  h.assert(/^FAKE-PW[0-9a-f]{16}$/.test(marker) && fixture.username === `FAKEPW${marker.slice(7)}`,
    'The boundary fixture marker or username is invalid');
  const ownerSql = `SELECT 1 FROM demographic d JOIN provider p ON p.provider_no=${h.sqlString(provider)}
    JOIN security u ON u.security_no=${security} AND u.provider_no=p.provider_no
    WHERE d.demographic_no=${patient} AND d.last_name=${h.sqlString(marker)} AND d.first_name='Workflow'
    AND HEX(d.last_name)=${h.sqlString(b.hex(marker))} AND HEX(d.first_name)=${h.sqlString(b.hex('Workflow'))} AND d.provider_no=${h.sqlString(actor)}
    AND p.last_name=${h.sqlString(marker)} AND u.user_name=${h.sqlString(fixture.username)}`;
  const owner = () => h.assert(sql.value(`SELECT COUNT(*) FROM (${ownerSql}) owned`) === '1',
    'The boundary patient or throwaway ownership changed');
  const slots = new Map(), captured = new Set(), observedMonitors = new Set();
  const names = new Set();
  const longLast = `${marker}-Longxx`, longFirst = `Linked-${marker.slice(-16)}xxxxxxx`;
  h.assert(longLast.length === 30 && longFirst.length === 30, 'The linked-name fixture does not fill both legal patient columns');
  const linkedName = `${longLast},${longFirst}`;
  let linkedIntent = false, linkedPatient;
  const linkedRows = () => sql.rows(`SELECT demographic_no,provider_no,HEX(last_name),HEX(first_name) FROM demographic
    WHERE last_name=${h.sqlString(longLast)} AND first_name=${h.sqlString(longFirst)}`);
  const resolveLinkedPatient = () => {
    if (!linkedIntent) return;
    const rows = linkedRows();
    h.assert(rows.length <= 1 && rows.every(row => row.length === 4 && row[1] === actor && row[2] === b.hex(longLast) && row[3] === b.hex(longFirst)), 'The additional linked patient ownership changed');
    if (!rows.length) { h.assert(!linkedPatient, 'The additional linked patient disappeared'); return; }
    const id = positiveId(rows[0][0], 'long-name patient');
    h.assert(!linkedPatient || linkedPatient === id, 'The additional linked patient identity changed');
    linkedPatient = id;
  };
  const linkedOwnerSql = () => `SELECT 1 FROM demographic WHERE demographic_no=${linkedPatient}
    AND last_name=${h.sqlString(longLast)} AND first_name=${h.sqlString(longFirst)}
    AND HEX(last_name)=${h.sqlString(b.hex(longLast))} AND HEX(first_name)=${h.sqlString(b.hex(longFirst))} AND provider_no=${h.sqlString(actor)}`;
  let context, native, siteId, ready = false, released = false;
  const remaining = () => sql.value(`SELECT (SELECT COUNT(*) FROM appointment WHERE provider_no=${h.sqlString(provider)})
    + (SELECT COUNT(*) FROM appointmentArchive WHERE provider_no=${h.sqlString(provider)})
    + (SELECT COUNT(*) FROM providersite WHERE provider_no=${h.sqlString(provider)})
    + (SELECT COUNT(*) FROM PageMonitor WHERE providerNo=${h.sqlString(provider)})`);
  const numericRows = (rows, label) => {
    h.assert(rows.every(row => Array.isArray(row) && row.length === 1), `${label} evidence is malformed`);
    const ids = rows.map(row => positiveId(row[0], label));
    h.assert(new Set(ids).size === ids.length, `${label} identities are duplicated`);
    return ids;
  };
  s.cleanup(() => {
    const browser = s.context.browser();
    h.assert(browser && !browser.isConnected() && (!context || context.browser() === browser),
      'Boundary fixtures retained: browser closure is unconfirmed');
    owner();
    h.assert(ready, 'Boundary fixtures retained: the initial child baseline is unconfirmed');
    resolveLinkedPatient();
    for (const value of slots.values()) value.monitors.cleanup();
    if (observedMonitors.size) h.assert(sql.value(`SELECT COUNT(*) FROM PageMonitor WHERE id IN (${[...observedMonitors].join(',')})`) === '0',
      'A captured boundary appointment monitor remains or changed ownership');
    owner();
    const candidate = `provider_no=${h.sqlString(provider)}${captured.size ? ` OR appointment_no IN (${[...captured].join(',')})` : ''}`;
    const fields = 'appointment_no,provider_no,demographic_no,appointment_date,start_time,end_time,name,location';
    const live = sql.rows(`SELECT appointment_no,${fields} FROM appointment WHERE ${candidate}`);
    const archived = sql.rows(`SELECT id,${fields} FROM appointmentArchive WHERE ${candidate}`);
    const check = row => {
      h.assert(Array.isArray(row) && row.length === 9, 'Boundary appointment identity evidence is malformed');
      const [id, appt, bookedProvider, demographic, date, start, end, name, location] = row;
      positiveId(id, 'appointment/archive'); positiveId(appt, 'appointment');
      const slot = slots.get(`${provider}|${date}|${String(start).slice(0,5)}|${String(end).slice(0,5)}`);
      h.assert(bookedProvider === provider && /^(?:[01]\d|2[0-3]):[0-5]\d:00$/.test(start)
        && /^(?:[01]\d|2[0-3]):[0-5]\d:00$/.test(end) && slot && native
        && location === native.location && ((demographic === patient && name === `${marker},Workflow`)
          || (demographic === linkedPatient && name === linkedName)
          || (demographic === '0' && names.has(name))),
      'An unowned boundary appointment/archive was retained');
    };
    live.forEach(check); archived.forEach(check);
    h.assert(new Set(live.map(row=>row[0])).size === live.length
      && new Set(archived.map(row=>row[0])).size === archived.length, 'Boundary appointment identities are duplicated');
    for (const [table, key, rows] of [['appointmentArchive','id',archived],['appointment','appointment_no',live]]) {
      for (const [id, appt, bookedProvider, demographic, date, start, end, name, location] of rows) {
        sql.execute(`DELETE FROM ${table} WHERE ${key}=${id} AND appointment_no=${appt}
          AND provider_no=${h.sqlString(bookedProvider)} AND demographic_no=${demographic}
          AND appointment_date=${h.sqlString(date)} AND start_time=${h.sqlString(start)} AND end_time=${h.sqlString(end)}
          AND name=${h.sqlString(name)} AND location=${h.sqlString(location)} AND EXISTS (${ownerSql})
          ${demographic === linkedPatient ? `AND EXISTS (${linkedOwnerSql()})` : ''}`);
      }
    }
    const allIds = [...new Set([...captured,...live.map(row=>row[1]),...archived.map(row=>row[1])])];
    if (allIds.length) h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM appointment WHERE appointment_no IN (${allIds.join(',')}))
      + (SELECT COUNT(*) FROM appointmentArchive WHERE appointment_no IN (${allIds.join(',')}))`) === '0',
    'Captured boundary appointment rows remain or changed ownership');
    if (siteId) sql.execute(`DELETE FROM providersite WHERE provider_no=${h.sqlString(provider)} AND site_id=${siteId}
      AND EXISTS (${ownerSql})`);
    owner();
    h.assert(remaining() === '0' && sql.value(`SELECT (SELECT COUNT(*) FROM appointment WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM appointmentArchive WHERE demographic_no=${patient})`) === '0',
    'Foreign or uncertain boundary fixture rows were retained');
    if (linkedPatient) {
      resolveLinkedPatient();
      const references = [['appointment','demographic_no'],['appointmentArchive','demographic_no'],
        ['casemgmt_note_lock','demographic_no'],['casemgmt_tmpsave','demographic_no'],['measurementsDeleted','demographicNo'],
        ['demographicExt','demographic_no'],['demographicArchive','demographic_no'],
        ['demographicExtArchive','demographic_no'],['demographiccust','demographic_no']];
      h.assert(sql.value(`SELECT ${references.map(([table,column]) => `(SELECT COUNT(*) FROM ${table} WHERE ${column}=${linkedPatient})`).join('+')}`) === '0',
        'The additional linked patient still has clinical or appointment children; it was retained');
      sql.execute(`DELETE FROM demographic WHERE demographic_no=${linkedPatient}
        AND last_name=${h.sqlString(longLast)} AND first_name=${h.sqlString(longFirst)} AND provider_no=${h.sqlString(actor)}
        AND HEX(last_name)=${h.sqlString(b.hex(longLast))} AND HEX(first_name)=${h.sqlString(b.hex(longFirst))}
        AND EXISTS (SELECT 1 FROM (${ownerSql} GROUP BY d.demographic_no) original_owner)
        ${references.map(([table,column]) => `AND NOT EXISTS (SELECT 1 FROM ${table} WHERE ${column}=${linkedPatient})`).join(' ')}`);
      h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${linkedPatient}`) === '0',
        'The additional linked patient was not removed or changed ownership');
    }
    released = true;
  });
  owner();
  h.assert(remaining() === '0', 'The boundary provider already owns appointment or site rows');
  ready = true;
  return {
    canRelease: () => released,
    createLongNamePatient() {
      owner(); h.assert(!linkedIntent && linkedRows().length === 0, 'The long-name patient fixture already exists');
      linkedIntent = true; // cleanup intent exists before the first insert, including a lost reply
      sql.execute(`INSERT INTO demographic (last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
          provider_no,hc_type,province,roster_status,lastUpdateDate)
        SELECT ${h.sqlString(longLast)},${h.sqlString(longFirst)},year_of_birth,month_of_birth,date_of_birth,sex,patient_status,
          provider_no,hc_type,province,roster_status,lastUpdateDate FROM demographic
        WHERE demographic_no=${patient} AND last_name=${h.sqlString(marker)} AND first_name='Workflow'
          AND provider_no=${h.sqlString(actor)} AND EXISTS (${ownerSql})`);
      resolveLinkedPatient(); h.assert(linkedPatient, 'The owned long-name patient was not created');
      return {patient:linkedPatient,lastName:longLast,firstName:longFirst,name:linkedName};
    },
    provisionSite(source) {
      h.assert(!native && !siteId, 'The boundary site was already provisioned');
      if (!source.multisites) return;
      const rows = sql.rows(`SELECT site.site_id FROM site JOIN providersite membership ON membership.site_id=site.site_id
        WHERE site.status<>0 AND site.name=${h.sqlString(source.location)} AND membership.provider_no=${h.sqlString(actor)}`);
      const ids = numericRows(rows, 'site');
      h.assert(ids.length === 1, 'The source native site is absent or ambiguous');
      siteId = ids[0]; owner();
      sql.execute(`INSERT INTO providersite(provider_no,site_id)
        SELECT ${h.sqlString(provider)},site.site_id FROM site JOIN providersite membership ON membership.site_id=site.site_id
        WHERE site.site_id=${siteId} AND site.status<>0 AND site.name=${h.sqlString(source.location)}
        AND membership.provider_no=${h.sqlString(actor)} AND EXISTS (${ownerSql})`);
      h.assert(sql.value(`SELECT COUNT(*) FROM providersite WHERE provider_no=${h.sqlString(provider)} AND site_id=${siteId}`) === '1',
        'The throwaway did not receive its exact native site');
    },
    bind(value, capturedContext) {
      h.assert(!context && !native && value.browser() === s.context.browser(), 'Boundary browser context ownership changed');
      h.assert(capturedContext.providerNo === provider, 'The boundary schedule belongs to another provider');
      context = value; native = capturedContext;
    },
    allowFreeName(name, column) {
      h.assert(typeof name === 'string' && name.includes(marker.slice(-6)) && /^[1-9]\d*$/.test(String(column)),
        'The free-text booking name has no owned marker namespace');
      names.add(name); names.add([...name].slice(0,Number(column)).join(''));
    },
    rememberAppointment(id) {
      const appointment = positiveId(id, 'appointment'); captured.add(appointment);
      console.log(`FIXTURE boundary-appointment-text appointment-id ${JSON.stringify({patient, marker, provider, appointment})}`);
    },
    async rememberSlot(onclick) {
      h.assert(context && native, 'The boundary native schedule context is unavailable');
      const slot = nativeSlot(onclick, s.config.baseUrl);
      h.assert(slot.provider === provider && slot.date === native.date && !slots.has(slot.pageId),
        'The boundary slot is foreign, stale or already used');
      let snapshot = true;
      const monitors = appointmentMonitorCleanup({provider, date:slot.date, baseUrl:s.config.baseUrl, sql(statement) {
        const ids = /^SELECT id FROM PageMonitor WHERE /.test(statement);
        const count = /^SELECT COUNT\(\*\) FROM PageMonitor WHERE /.test(statement);
        const deletion = /^DELETE FROM PageMonitor WHERE /.test(statement);
        h.assert(ids || count || deletion, 'Unexpected boundary monitor cleanup query');
        const query = `${statement} AND pageId=${h.sqlString(slot.pageId)} AND providerNo=${h.sqlString(provider)} AND EXISTS (${ownerSql})`;
        if (deletion) { sql.execute(query); return ''; }
        const rows = sql.rows(query);
        if (count) h.assert(rows.length === 1 && rows[0].length === 1 && /^\d+$/.test(String(rows[0][0])),
          'Boundary monitor count evidence is malformed');
        else {
          const values = numericRows(rows, 'appointment monitor');
          if (snapshot) {
            h.assert(!values.length, 'The boundary slot already has an owned-session monitor; it was retained');
            const hash = statement.match(/SHA2\(session,256\)='([0-9a-f]{64})'/)?.[1];
            h.assert(hash, 'The boundary session hash evidence is unavailable');
            console.log(`FIXTURE boundary-appointment-text monitor-scope ${JSON.stringify({patient, marker,
              provider, pageId:slot.pageId, sessionSha256:hash, baselineIds:values})}`);
          } else {
            values.forEach(id=>observedMonitors.add(id));
            if (values.length) console.log(`FIXTURE boundary-appointment-text monitor-ids ${JSON.stringify({patient, marker, ids:values})}`);
          }
        }
        return rows.map(row=>row.join('\t')).join('\n');
      }});
      slots.set(slot.pageId,{slot,monitors});
      try { await monitors.remember(context); } finally { snapshot = false; }
      return slot;
    },
    async checkBooking(popup, slot) {
      const actual = await popup.locator('form#addappt').evaluate(form=>Object.fromEntries(
        ['provider_no','appointment_date','start_time','end_time'].map(name=>[name,form.elements.namedItem(name).value])));
      h.assert(actual.provider_no === provider && actual.appointment_date === slot.date
        && actual.start_time === slot.start && actual.end_time === slot.end, 'The opened boundary slot differs from its captured cleanup scope');
      if (native.multisites) {
        const location = popup.locator('form#addappt select[name="location"]');
        h.assert(await location.count() === 1 && await location.locator('option').evaluateAll((rows,name)=>
          rows.filter(option=>!option.disabled && option.value===name).length,native.location) === 1,
        'The booking does not offer the owned native site');
        await location.selectOption({value:native.location});
        h.assert(await location.inputValue() === native.location, 'The booking did not retain the native site');
      }
    },
  };
}

/** A string whose UTF-16 length is exactly `units` built from the special-character parts, padded with 'x'. */
function fillUnits(units, parts, pad = 'x') {
  let value = '';
  for (const part of parts) {
    if ((value + (value ? ' ' : '') + part).length <= units) value += (value ? ' ' : '') + part;
  }
  return value + pad.repeat(units - value.length);
}

async function pollFor(sql, query, predicate, message) {
  const deadline = Date.now() + 15000;
  let value;
  do {
    value = sql.value(query);
    if (predicate(value)) return value;
    await new Promise(resolve => setTimeout(resolve, 200));
  } while (Date.now() < deadline);
  h.assert(false, message);
  return value;
}

async function workflow(s) {
  const { sql, config, recorder, marker, patient } = s;
  // A throwaway login whose provider number owns no appointment row, so the by-number cleanup removes only this run's rows.
  const sourceContext = await captureScheduleContext(s);
  let owned, retainProvider = false;
  const fixture = createUnbookedThrowaway({...s, cleanup(callback) {
    s.cleanup(() => {
      h.assert(!retainProvider || owned?.canRelease(), 'Boundary throwaway retained: child cleanup is incomplete');
      return callback();
    });
  }});
  retainProvider = true;
  owned = installOwnedBoundaryCleanup(s, fixture);
  owned.provisionSite(sourceContext);
  const owner = h.sqlString(fixture.providerNo);
  const context = await h.newContext(s.context.browser(), config);
  context.setDefaultTimeout(20000);
  context.on('page', page => h.wireStrictPage(page, 'throwaway', recorder));
  const schedule = await h.login(context, { ...config, testUser: fixture.username }, recorder, { label: 'throwaway-login' });
  const nativeSession = {...s, schedule, provider:fixture.providerNo};
  const scheduleContext = await captureScheduleContext(nativeSession, {preferredSite:sourceContext.location});
  h.assert(scheduleContext.location === sourceContext.location && scheduleContext.multisites === sourceContext.multisites,
    'The throwaway schedule did not preserve the source native site');
  owned.bind(context, scheduleContext);
  const T = b.TOKENS;
  const parts = [T.apostrophe, T.latin, T.cjk, T.emoji, T.entity, T.quotes, T.backslash, T.percent, T.plus, T.semicolon];
  const reasonColumn = b.columnLength(sql, 'appointment', 'reason');
  const notesColumn = b.columnLength(sql, 'appointment', 'notes');
  const reason = fillUnits(reasonColumn, parts);
  const notes = fillUnits(notesColumn, parts);
  const resources = fillUnits(60, parts);
  const field = (popup, name) => popup.locator(`form#addappt [name="${name}"]`);
  const byApptNo = () => `provider_no=${owner} AND appointment_no=`;
  let slotIndex = 4;

  async function openBooking() {
    const slots = schedule.locator(`a.adhour[onclick*="provider_no=${fixture.providerNo}&"]`);
    await assertScheduleContext(nativeSession, scheduleContext);
    const slot = await owned.rememberSlot(await slots.nth(slotIndex).getAttribute('onclick'));
    const popup = await ui.clickOpensPopup(schedule, slots.nth(slotIndex), { context, recorder, label: 'add-appointment', timeout: 20000 });
    slotIndex += 6;
    await popup.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    await owned.checkBooking(popup, slot);
    return popup;
  }
  async function pickPatient(popup, selected = {patient, lastName:marker}) {
    await popup.locator('#keyword').fill(selected.lastName);
    await ui.clickAndAwaitReload(popup, popup.locator('#searchBtn'), { required: false });
    await popup.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    const pick = popup.locator(`table tr input[type="button"][name="pick_demographic"][value="${selected.patient}"]`).first();
    await pick.waitFor({ state: 'visible' });
    await ui.clickAndAwaitReload(popup, pick, { required: false });
    await popup.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    h.assert(await popup.locator('#demographic_no').inputValue() === selected.patient, 'The booking form did not take the owned patient');
  }
  async function openEdit(apptNo) {
    await assertScheduleContext(nativeSession, scheduleContext);
    const link = schedule.locator(`a[onclick*="/appointment/editappointment?appointment_no=${apptNo}"]`).first();
    await link.waitFor({ state: 'attached', timeout: 20000 });
    const edit = await ui.clickOpensPopup(schedule, link, { context, recorder, label: 'edit-appointment', timeout: 20000 });
    await edit.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    return edit;
  }
  async function reloadDaySheet() {
    // The popup's save refreshes the opener on its own; a second reload races that navigation.
    await new Promise(resolve => setTimeout(resolve, 1500));
    await schedule.waitForLoadState('load', { timeout: 20000 }).catch(() => {});
    try { await schedule.reload({ waitUntil: 'domcontentloaded' }); } catch (error) { await schedule.waitForLoadState('load', { timeout: 20000 }); }
    await assertScheduleContext(nativeSession, scheduleContext);
  }

  let apptNo;
  await s.step('booking with a full-length reason, notes and resources of special characters stores them byte for byte', async () => {
    const popup = await openBooking();
    await pickPatient(popup);
    await field(popup, 'reason').fill(reason);
    await field(popup, 'notes').fill(notes);
    await field(popup, 'resources').fill(resources);
    h.assert(await field(popup, 'reason').inputValue() === reason && await field(popup, 'notes').inputValue() === notes,
      'The reason or notes box refused text that exactly fills its maxlength');
    await ui.clickAndAwaitReload(popup, popup.locator('#addButton'), { required: false });
    apptNo = await pollFor(sql, `SELECT appointment_no FROM appointment WHERE provider_no=${owner}`, v => /^\d+$/.test(v), 'The booking was not saved');
    owned.rememberAppointment(apptNo);
    for (const [column, value] of [['reason', reason], ['notes', notes], ['resources', resources]]) {
      b.assertStored(sql, 'appointment', column, `${byApptNo()}${apptNo}`, value, `Booked appointment ${column}`);
    }
    await popup.close().catch(() => {});
    await reloadDaySheet();
  });

  await s.step('the edit popup redisplays the values identically and an unchanged Update changes no column', async () => {
    const edit = await openEdit(apptNo);
    const wrong = [];
    for (const [name, value] of [['reason', reason], ['notes', notes], ['resources', resources]]) {
      if (await edit.locator(`form [name="${name}"]`).first().inputValue() !== value) wrong.push(name);
    }
    h.assert(wrong.length === 0, `The edit popup does not redisplay what was stored: ${wrong.join(', ')}`);
    const snapshot = () => sql.rows(`SELECT HEX(name), HEX(reason), HEX(notes), HEX(resources) FROM appointment WHERE ${byApptNo()}${apptNo}`)[0].join('|');
    const before = snapshot();
    await edit.locator('#updateButton').click();
    await edit.waitForEvent('close', { timeout: 15000 }).catch(() => {});
    await pollFor(sql, `SELECT COUNT(*) FROM appointmentArchive WHERE appointment_no=${apptNo}`, v => Number(v) >= 1, 'The unchanged Update was not recorded');
    h.assert(snapshot() === before, 'An Update with no edits altered the stored name, reason, notes or resources (re-encoding on save)');
    await reloadDaySheet();
  });

  async function roundTripName(expected, linked) {
    const popup = await openBooking();
    if (linked) await pickPatient(popup, linked);
    else {
      owned.allowFreeName(expected, b.columnLength(sql, 'appointment', 'name'));
      await popup.locator('#keyword').fill(expected);
      h.assert(['', '0'].includes(await popup.locator('#demographic_no').inputValue()), 'The free-text name unexpectedly links a patient');
    }
    h.assert(await popup.locator('#keyword').inputValue() === expected, 'The legal appointment name was silently limited before Add');
    const highWater = Number(sql.value(`SELECT COALESCE(MAX(appointment_no),0) FROM appointment WHERE provider_no=${owner}`));
    await ui.clickAndAwaitReload(popup, popup.locator('#addButton'), {required:false});
    const id = await pollFor(sql, `SELECT appointment_no FROM appointment WHERE provider_no=${owner} AND appointment_no>${highWater} ORDER BY appointment_no DESC LIMIT 1`,
      value => /^[1-9]\d*$/.test(value), 'The legal appointment name was not saved');
    owned.rememberAppointment(id);
    b.assertStored(sql, 'appointment', 'name', `${byApptNo()}${id}`, expected, 'Created full appointment name');
    h.assert(sql.value(`SELECT demographic_no FROM appointment WHERE ${byApptNo()}${id}`) === (linked ? linked.patient : '0'),
      'The saved appointment did not preserve its linked/free-text identity');
    await popup.close().catch(() => {}); await reloadDaySheet();
    const edit = await openEdit(id);
    h.assert(await edit.locator('form [name="keyword"]').first().inputValue() === expected, 'The edit popup omitted legal appointment-name text');
    await edit.locator('#updateButton').click(); await edit.waitForEvent('close',{timeout:15000}).catch(() => {});
    await pollFor(sql, `SELECT COUNT(*) FROM appointmentArchive WHERE provider_no=${owner} AND appointment_no=${id}`,
      value => Number(value) >= 1, 'The unchanged name Update has no archive');
    b.assertStored(sql, 'appointment', 'name', `${byApptNo()}${id}`, expected, 'Updated full appointment name');
    const archived = sql.rows(`SELECT HEX(name) FROM appointmentArchive WHERE provider_no=${owner} AND appointment_no=${id}`);
    h.assert(archived.length > 0 && archived.every(row => row.length === 1 && row[0] === b.hex(expected)),
      'An appointment archive omitted part of the legal name');
    await reloadDaySheet();
  }

  await s.step('a legal 61-character linked patient name survives create, reopen, unchanged Update and archive', async () => {
    h.assert(b.columnLength(sql,'appointment','name') >= 255 && b.columnLength(sql,'appointmentArchive','name') >= 255,
      'Both live and archived appointment names must support 255 characters');
    const linked = owned.createLongNamePatient();
    h.assert(linked.lastName.length === 30 && linked.firstName.length === 30 && linked.name.length === 61,
      'The linked-name boundary does not exercise 61 characters');
    await roundTripName(linked.name, linked);
  });

  await s.step('a legal 255-character free-text name survives create, reopen, unchanged Update and archive', async () => {
    await roundTripName(b.exactly(255, `N${marker.slice(-6)}-legal-`));
  });

  await s.step('line breaks inside a full box, and a long free-text name and resources entry, are stored whole or refused', async () => {
    const problems = [];
    // (a) a break counts one character in the box (maxlength) but travels as CR LF, so a box filled to the limit overflows the column.
    // The box counts UTF-16 units but the column counts code points, so an emoji (two units, one code point) would give the
    // submitted CR LF text one code point of slack and hide the overflow: leave it out of these probes.
    const bmpParts = parts.filter(part => part !== T.emoji);
    const breakReason = `${fillUnits(reasonColumn - 2, bmpParts)}\nZ`;
    const breakNotes = `${fillUnits(notesColumn - 2, bmpParts)}\nZ`;
    const edit = await openEdit(apptNo);
    await edit.locator('form [name="reason"]').first().fill(breakReason);
    await edit.locator('form [name="notes"]').first().fill(breakNotes);
    const accepted = [await edit.locator('form [name="reason"]').first().inputValue(), await edit.locator('form [name="notes"]').first().inputValue()];
    await edit.locator('#updateButton').click();
    await edit.waitForEvent('close', { timeout: 15000 }).catch(() => {});
    await new Promise(resolve => setTimeout(resolve, 1500));
    const lf = value => String(value).replace(/\r\n/g, '\n');
    for (const [column, typed, shown] of [['reason', breakReason, accepted[0]], ['notes', breakNotes, accepted[1]]]) {
      const stored = b.readStored(sql, 'appointment', column, `${byApptNo()}${apptNo}`);
      const text = Buffer.from(stored.hex, 'hex').toString('utf8');
      if (lf(text) !== lf(shown)) problems.push(`${column}: a box filled to its limit with a line break inside (${b.cpLength(shown)} characters) was stored as ${b.cpLength(text)} (the break is submitted as CR LF, one more than the box counted); the last character is lost`);
      if (lf(shown) !== lf(typed)) problems.push(`${column}: the box silently dropped typed text`);
    }
    // (b) a free-text appointment name and a resources entry past their columns.
    await reloadDaySheet();
    const nameColumn = b.columnLength(sql, 'appointment', 'name');
    const resourcesColumn = b.columnLength(sql, 'appointment', 'resources');
    const longName = b.exactly(nameColumn + 1, 'N' + marker.slice(-6));
    const longResources = b.exactly(resourcesColumn + 1, 'R');
    owned.allowFreeName(longName, nameColumn);
    owned.allowFreeName(`N${marker.slice(-6)}`, nameColumn);
    // Each field is probed in a booking of its own, so a refusal of one cannot mask what happens to the other.
    // The outcome must be visible either way: the box limits the text, the booking stores it whole, or the
    // booking is refused with a length-specific message (alert or page). No row and no message is a defect too.
    const probeOverlong = async (column, selector, typed, otherName) => {
      const popup = await openBooking();
      if (otherName) await popup.locator('#keyword').fill(otherName);
      await popup.locator(selector).fill(typed);
      const shown = await popup.locator(selector).inputValue();
      if (b.cpLength(shown) < b.cpLength(typed)) { await popup.close().catch(() => {}); return; } // visibly limited by the box
      const highWater = Number(sql.value(`SELECT COALESCE(MAX(appointment_no),0) FROM appointment WHERE provider_no=${owner}`));
      let pageText = '';
      const dialogs = await h.withExpectedDialogs(popup, async () => {
        await ui.clickAndAwaitReload(popup, popup.locator('#addButton'), { required: false });
        await new Promise(resolve => setTimeout(resolve, 1500));
        if (!popup.isClosed()) pageText = await popup.locator('body').innerText().catch(() => '');
      });
      const row = sql.value(`SELECT appointment_no FROM appointment WHERE provider_no=${owner} AND appointment_no>${highWater} ORDER BY appointment_no DESC LIMIT 1`);
      if (/^\d+$/.test(row)) {
        owned.rememberAppointment(row);
        const stored = b.readStored(sql, 'appointment', column, `${byApptNo()}${row}`);
        if (stored.hex !== b.hex(shown)) problems.push(`${column}: ${b.cpLength(shown)} characters were typed into a box with no maxlength and the booking was accepted, but ${stored.chars} were stored (column ${b.columnLength(sql, 'appointment', column)}); silent truncation`);
      } else if (!b.lengthRefusal([...dialogs.map(entry => entry.text), pageText].join('\n'))) {
        problems.push(`${column}: a ${b.cpLength(shown)}-character entry past its column was not saved, but no length refusal was shown (no alert or page message naming the limit)`);
      }
      await popup.close().catch(() => {});
      await reloadDaySheet();
    };
    await probeOverlong('name', '#keyword', longName, null);
    await probeOverlong('resources', 'form#addappt [name="resources"]', longResources, `N${marker.slice(-6)}`);
    h.assert(problems.length === 0, problems.join(' || '));
  });
}

if (require.main === module) runWorkflow('boundary-appointment-text', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow, installOwnedBoundaryCleanup, nativeSlot };
