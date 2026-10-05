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

const PAGE = 5;

/** Rows of demographicsearch2apptresults.jsp: the id from the pick button, and the surname text. */
async function readApptRows(page) {
  return page.$$eval('tr', trs => trs.filter(tr => tr.querySelector('td.demoId input[name="pick_demographic"]')).map(tr => ({
    id: tr.querySelector('td.demoId input[name="pick_demographic"]').value,
    last: ((tr.querySelector('td.lastName') || {}).textContent || '').trim(),
  })));
}

async function openBookingSearch(s, slotIndex, keyword) {
  const slots = s.schedule.locator(`a.adhour[onclick*="provider_no=${s.provider}&"]`);
  const popup = await ui.clickOpensPopup(s.schedule, slots.nth(slotIndex), { context: s.context, recorder: s.recorder, label: 'booking-search', timeout: 20000 });
  await popup.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
  await popup.locator('#keyword').fill(keyword);
  await ui.clickAndAwaitReload(popup, popup.locator('#searchBtn'), { required: false });
  await popup.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
  return popup;
}

async function workflow(s) {
  const { sql, marker } = s;
  const tag = k.nameTag(s.marker);
  k.registerPatientCleanup(s, tag);
  // An address search needs "digits, space, word": the number is derived from the run so two runs never collide.
  const base = 10000 + (parseInt(marker.slice(-5), 16) % 80000);
  const addrX = `${base} ${tag}X`;
  const addrY = `${base + 1} ${tag}Y`;
  const letters = 'LKJIHGFEDCBA'.split('');
  const idsX = letters.map(l => k.insertPatient(s, { last: `${tag}-X${l}`, first: 'Fixture', address: `${addrX} Road` }));
  const idsY = [1, 2, 3, 4, 5, 6, 7].map(n => k.insertPatient(s, { last: `${tag}-Y${n}`, first: 'Fixture', address: `${addrY} Road` }));
  // Y2 has been merged into Y1: a current record is Y1, the merged-away row is hidden by the results page.
  sql.execute(`INSERT INTO demographic_merged (demographic_no, merged_to, deleted, lastUpdateUser, lastUpdateDate)
    VALUES (${idsY[1]}, ${idsY[0]}, 0, ${h.sqlString(s.provider)}, CURDATE())`);
  const defects = [];
  const stripName = text => text.replace(/^.*?-/, '').replace(/^.*?-/, '');

  await s.step('the booking search lists five of the twelve address matches on page one', async () => {
    const popup = await openBookingSearch(s, 2, addrX);
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
    const popup = await openBookingSearch(s, 3, addrY);
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
module.exports = { workflow };
