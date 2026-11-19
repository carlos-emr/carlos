#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The booking screen's patient search: order across pages and reaching every patient (wave 7, search-sort).
 * User path: Schedule > an empty slot > Add Appointment popup > type a street address (digits then a word, which the page
 * reads as an address search) > Search (5 rows a page, demographicsearch2apptresults.jsp) > Next Page.
 * Asserts, on 12 owned patients with one address tag, inserted in reverse alphabetical order: page one lists five of them,
 * and reading the pages in turn gives the surnames in alphabetical order (the page sorts only its own five rows, and the
 * query has no ORDER BY, so each page is the next five in table order); and, on 7 owned patients of which one has been
 * merged into another, that paging reaches all six current records: the merged-away row is fetched, hidden, and must not
 * cost the page its Next Page button. Defects are collected and asserted together in the last step.
 * Fixtures: 19 synthetic patients inserted by SQL (surname starts with the run tag) and one demographic_merged row for the
 * merge case; cleanup removes them all and asserts none remain. Nothing is booked.
 * Implements the wave-7 "search-sort" pattern (appointment search, pagination boundaries, hidden rows vs page size).
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const k = require('./lib/search-sort-helpers');
const { appointmentMonitorCleanup } = require('./lib/appointment-page-monitor-cleanup');
const { nativeSlot } = require('./popup-opener-day-sheet-playwright-checks');
const { captureScheduleContext, assertScheduleContext } = require('./lib/native-schedule-context');

const PAGE = 5;

/** Rows of demographicsearch2apptresults.jsp: the id from the pick button, and the surname text. */
async function readApptRows(page) {
  return page.$$eval('tr', trs => trs.filter(tr => tr.querySelector('td.demoId input[name="pick_demographic"]')).map(tr => ({
    id: tr.querySelector('td.demoId input[name="pick_demographic"]').value,
    last: ((tr.querySelector('td.lastName') || {}).textContent || '').trim(),
  })));
}

async function openBookingSearch(s, slotIndex, keyword, monitors) {
  const slots = s.schedule.locator(`a.adhour[onclick*="provider_no=${s.provider}&"]`);
  const slot = await monitors.rememberSlot(await slots.nth(slotIndex).getAttribute('onclick'));
  const popup = await ui.clickOpensPopup(s.schedule, slots.nth(slotIndex), { context: s.context, recorder: s.recorder, label: 'booking-search', timeout: 20000 });
  await popup.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
  const actual = await popup.locator('form#addappt').evaluate(form => Object.fromEntries(
    ['provider_no', 'appointment_date', 'start_time', 'end_time'].map(name => [name, form.elements.namedItem(name).value])));
  h.assert(actual.provider_no === slot.provider && actual.appointment_date === slot.date
    && actual.start_time === slot.start && actual.end_time === slot.end, 'The booking search opened a different native slot');
  await popup.locator('#keyword').fill(keyword);
  await ui.clickAndAwaitReload(popup, popup.locator('#searchBtn'), { required: false });
  await popup.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
  return popup;
}

// Capture each native slot before its first popup script creates a monitor. Patient cleanup
// remains blocked until browser closure and exact session/slot monitor cleanup are proved.
function installSearchMonitorCleanup(s, tag, patients, native) {
  const { sql, context, provider } = s;
  h.assert(/^FAKE-PW[0-9a-f]{6}$/.test(tag) && patients.length === 19
    && patients.every(id => /^[1-9]\d*$/.test(id)) && new Set(patients).size === 19,
    'The booking-search fixture identities are invalid');
  const ownerSql = `SELECT 1 FROM demographic WHERE demographic_no IN (${patients.join(',')})
    AND last_name LIKE ${h.sqlString(`${tag}-%`)} AND provider_no=${h.sqlString(provider)}`;
  const owner = () => h.assert(sql.value(`SELECT COUNT(*) FROM (${ownerSql}) owned`) === '19',
    'The booking-search patient ownership changed');
  const slots = new Map(), observed = new Set();
  let released = false;
  s.cleanup(() => {
    const browser = context.browser();
    h.assert(browser && !browser.isConnected(), 'Booking-search fixtures retained: browser closure is unconfirmed');
    owner();
    h.assert([...slots.values()].every(record => record.ready), 'Booking-search fixtures retained: a slot/session baseline is unconfirmed');
    for (const { monitors } of slots.values()) monitors.cleanup();
    if (observed.size) h.assert(sql.value(`SELECT COUNT(*) FROM PageMonitor WHERE id IN (${[...observed].join(',')})`) === '0',
      'A captured booking-search monitor remains or changed ownership');
    owner(); released = true;
  });
  owner();
  return {
    canRelease: () => released,
    async rememberSlot(onclick) {
      owner(); await assertScheduleContext(s, native);
      const slot = nativeSlot(onclick, s.config.baseUrl);
      h.assert(slot.provider === String(provider) && slot.provider === native.providerNo && slot.date === native.date
        && !slots.has(slot.pageId), 'The booking-search slot is foreign, stale or already used');
      let snapshot = true;
      const monitors = appointmentMonitorCleanup({ provider: slot.provider, date: slot.date, baseUrl: s.config.baseUrl,
        sql(statement) {
          const ids = /^SELECT id FROM PageMonitor WHERE /.test(statement);
          const count = /^SELECT COUNT\(\*\) FROM PageMonitor WHERE /.test(statement);
          const deletion = /^DELETE FROM PageMonitor WHERE /.test(statement);
          h.assert(ids || count || deletion, 'Unexpected booking-search monitor query');
          owner();
          const query = `${statement} AND pageId=${h.sqlString(slot.pageId)} AND providerNo=${h.sqlString(provider)}
            AND (SELECT COUNT(*) FROM (${ownerSql}) monitor_owner)=19`;
          if (deletion) { sql.execute(query); return ''; }
          const rows = sql.rows(query);
          h.assert(rows.every(row => Array.isArray(row) && row.length === 1), 'Booking-search monitor evidence is malformed');
          if (count) h.assert(rows.length === 1 && /^\d+$/.test(String(rows[0][0])), 'Booking-search monitor count is malformed');
          else {
            const values = rows.map(row => String(row[0]));
            h.assert(values.every(id => /^[1-9]\d*$/.test(id)) && new Set(values).size === values.length,
              'Booking-search monitor identities are invalid');
            if (snapshot) {
              h.assert(!values.length, 'The booking-search slot already has an owned-session monitor; it was retained');
              const sessionSha256 = statement.match(/SHA2\(session,256\)='([0-9a-f]{64})'/)?.[1];
              h.assert(sessionSha256, 'The booking-search session hash is unavailable');
              console.log(`FIXTURE search-sort-appt-patient-pages monitor-scope ${JSON.stringify({marker:s.marker, patients,
                provider, pageId:slot.pageId, sessionSha256, baselineIds:values})}`);
            } else { values.forEach(id => observed.add(id)); }
          }
          return rows.map(row => row.join('\t')).join('\n');
        },
      });
      const record = { monitors, ready: false };
      slots.set(slot.pageId, record);
      try { await monitors.remember(context); record.ready = true; } finally { snapshot = false; }
      return slot;
    },
  };
}

// The shared tag cleanup is broader than this run's exact IDs. Gate its callback
// immediately before invocation; a colliding, replaced or missing row retains all parents.
function registerExactSearchPatientCleanup(s, tag, ownership) {
  const marker = s.marker, provider = String(s.provider);
  h.assert(/^[1-9]\d{0,5}$/.test(provider), 'The booking-search provider is invalid');
  h.assert(/^FAKE-PW[0-9a-f]{16}$/.test(marker) && tag === k.nameTag(marker),
    'The booking-search full marker and short tag are inconsistent');
  k.registerPatientCleanup({ ...s, cleanup(callback) { s.cleanup(() => {
    h.assert(s.marker === marker && String(s.provider) === provider && /^FAKE-PW[0-9a-f]{16}$/.test(s.marker) && tag === k.nameTag(s.marker),
      'Booking-search patients retained: full marker or short tag changed');
    h.assert(!ownership.monitors || ownership.monitors.canRelease(),
      'Booking-search patients retained: monitor cleanup is incomplete');
    const expected = ownership.patients.map(String).sort((a, b) => Number(a) - Number(b));
    h.assert(expected.length === 19 && expected.every(id => /^[1-9]\d*$/.test(id)) && new Set(expected).size === 19,
      'Booking-search patients retained: the exact 19 identities are unconfirmed');
    const rows = s.sql.rows(`SELECT demographic_no,last_name,provider_no FROM demographic
      WHERE last_name LIKE ${h.sqlString(`${tag}%`)} ORDER BY demographic_no`);
    h.assert(rows.length === 19 && rows.every(row => Array.isArray(row) && row.length === 3
      && String(row[1]).startsWith(`${tag}-`) && String(row[2]) === provider)
      && JSON.stringify(rows.map(row => String(row[0]))) === JSON.stringify(expected),
      'Booking-search patients retained: the complete tagged patient set or ownership changed');
    callback();
  }); } }, tag);
}

async function workflow(s) {
  const { sql, marker } = s;
  const tag = k.nameTag(s.marker);
  const ownership = { patients: [], monitors: null };
  registerExactSearchPatientCleanup(s, tag, ownership);
  // An address search needs "digits, space, word": the number is derived from the run so two runs never collide.
  const base = 10000 + (parseInt(marker.slice(-5), 16) % 80000);
  const addrX = `${base} ${tag}X`;
  const addrY = `${base + 1} ${tag}Y`;
  const letters = 'LKJIHGFEDCBA'.split('');
  const idsX = letters.map(l => k.insertPatient(s, { last: `${tag}-X${l}`, first: 'Fixture', address: `${addrX} Road` }));
  const idsY = [1, 2, 3, 4, 5, 6, 7].map(n => k.insertPatient(s, { last: `${tag}-Y${n}`, first: 'Fixture', address: `${addrY} Road` }));
  ownership.patients = [...idsX, ...idsY];
  // Y2 has been merged into Y1: a current record is Y1, the merged-away row is hidden by the results page.
  sql.execute(`INSERT INTO demographic_merged (demographic_no, merged_to, deleted, lastUpdateUser, lastUpdateDate)
    VALUES (${idsY[1]}, ${idsY[0]}, 0, ${h.sqlString(s.provider)}, CURDATE())`);
  const native = await captureScheduleContext(s);
  ownership.monitors = { canRelease: () => false };
  ownership.monitors = installSearchMonitorCleanup(s, tag, ownership.patients, native);
  const defects = [];
  const stripName = text => text.replace(/^.*?-/, '').replace(/^.*?-/, '');

  await s.step('the booking search lists five of the twelve address matches on page one', async () => {
    const popup = await openBookingSearch(s, 2, addrX, ownership.monitors);
    const rows = await readApptRows(popup);
    h.assert(rows.length === PAGE && k.ownedRows(rows, idsX).length === PAGE, `Page one listed ${rows.length} rows, not ${PAGE} owned patients (fixture or search problem)`);
    const seen = rows.map(r => stripName(r.last));
    let offset = 0;
    for (let guard = 0; guard < 4; guard += 1) {
      const next = popup.locator('#nextPageButton');
      if (!await next.count()) break;
      await ui.clickAndAwaitReload(popup, next.first(), { timeout: 30000, label: 'booking search next page' });
      offset += PAGE;
      seen.push(...(await readApptRows(popup)).filter(r => idsX.includes(r.id)).map(r => stripName(r.last)));
    }
    await popup.close().catch(() => {});
    h.assert(seen.length === 12 && new Set(seen).size === 12, `Paging listed ${seen.length} owned rows, not the 12 matches once each`);
    if (!k.nonDecreasing(seen)) defects.push(`the booking search pages are not in surname order: ${seen.join(' | ')}`);
  });

  await s.step('paging reaches every current record when a matching patient was merged away', async () => {
    const popup = await openBookingSearch(s, 3, addrY, ownership.monitors);
    const seen = (await readApptRows(popup)).filter(r => idsY.includes(r.id)).map(r => r.id);
    let nextOffered = await popup.locator('#nextPageButton').count();
    for (let guard = 0; nextOffered && guard < 4; guard += 1) {
      await ui.clickAndAwaitReload(popup, popup.locator('#nextPageButton').first(), { timeout: 30000, label: 'booking search next page' });
      seen.push(...(await readApptRows(popup)).filter(r => idsY.includes(r.id)).map(r => r.id));
      nextOffered = await popup.locator('#nextPageButton').count();
    }
    await popup.close().catch(() => {});
    const current = idsY.filter(id => id !== idsY[1]);
    const missing = current.filter(id => !seen.includes(id)).length;
    if (missing) defects.push(`${missing} of the 6 current records matching the address were never listed (the merged-away row used one of the page's five places and the page then offered no Next Page)`);
  });

  await s.step('every matching patient was reachable, in surname order', async () => {
    h.assert(defects.length === 0, `Booking search defects: ${defects.join('; ')} `
      + '(demographicsearch2apptresults.jsp:472-517 calls the DAO overloads without an orderBy and :525 sorts only the fetched page; '
      + ':677 offers Next only when rowCounter, which counts displayed rows, equals the page size)');
  });
}

if (require.main === module) runWorkflow('search-sort-appt-patient-pages', workflow, { openPatient: false });
module.exports = { workflow, installSearchMonitorCleanup, registerExactSearchPatientCleanup };
