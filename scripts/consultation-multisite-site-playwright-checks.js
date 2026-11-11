#!/usr/bin/env node
/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 *
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
/*
 * Issue #5274: the referral's Site selector on a multisite install (multisites=on).
 * User paths: Schedule (the day view) ▸ E on the owned patient's visit at site SB (the E-Chart) ▸ Consultations "+" ▸
 * Site ▸ Submit Consultation Request; Schedule ▸ Consultations ▸ a referral ▸ Site ▸ Update / Update And Print Preview.
 * Asserted, as the test login linked to the owned sites SA, SB and SC (not SD):
 *   1. a new referral opened from the visit at SB offers the login's sites (SA, SB, SC, not SD) with SB selected;
 *   2. choosing SC instead and submitting stores SC (consultationRequests.site_name) and the referral reopens on SC
 *      (release/2026.08 rendered no option selected, so every referral opened on the first site);
 *   3. changing it to SB on Update stores SB, archives SC and leaves the second patient's referral at SA;
 *   4. an Update that changes only the appointment notes keeps SB (release/2026.08 moved it to the first site);
 *   5. two windows open at once on the two patients' referrals (the first from its patient's E-Chart, the second from
 *      the clinic list, which opens every referral in one shared window) keep their own sites: SC chosen in the second
 *      and saved after the first window's Update leaves the first referral at SB and the second at SC;
 *   6. Update And Print Preview prints the referral's letterhead, not the site's address: the site is not a letterhead;
 *   7. a referral saved at SD, a site the login is not linked to (another login's site, or one unlinked since), reopens
 *      from the patient's E-Chart with SD selected, and an Update of its notes alone keeps SD: the login cannot pick SD,
 *      so the form keeps the saved site as an extra option rather than moving the referral to one it can pick.
 * A site logo (the only site-dependent part of the letter) prints only with the operator's faxLogoInConsultation
 * property and a site logo document; that is not configured by this check and is reported as a NOTE.
 * Skips when multisites is off (the day sheet has no clinic selector).
 * Fixtures: lib/consultation-referral-fixture.js (FAKE patients, service, consultant, referrals); four active sites
 * named '<marker> SA/SB/SC/SD', SA-SC linked to the test provider through providersite; today's visit of the owned
 * patient at SB. Every login's clinic lists show the sites while the check runs, so it must not run concurrently with
 * other checks (live wrapper: EXCLUSIVE=1). Cleanup removes every owned row by key and asserts it gone.
 * Operator prerequisite: multisites=on in /etc/carlos-emr/carlos.properties, then carlos-ctl restart; restore the
 * property and restart afterwards.
 */
const fs = require('node:fs');
const h = require('./lib/playwright-harness');
const { requirePoppler, pdfTextBuffer } = require('./lib/export-content-helpers');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const {
  CREATED, UPDATED, openConsultationsList, openFromChart, openFromList, ownedReferrals, pickOwnedServiceAndConsultant, refreshList, save,
} = require('./lib/consultation-referral-fixture');
const { runWorkflow } = require('./lib/workflow-session');

const q = h.sqlString;
const TIMEOUT = 30000;
const squash = text => String(text).replace(/\s+/g, ' ').trim();

const STEP = {
  defaulted: 'a new referral from a visit at SB offers the login\'s sites with SB selected',
  chosen: 'another chosen site is stored and the referral reopens on it',
  changed: 'Update changes only that referral\'s site and archives the previous one',
  unrelated: 'an Update of another field keeps the saved site',
  windows: 'two open referral windows keep their own sites',
  printed: 'the printed letter carries the letterhead, not the site\'s address',
  unlinked: 'a saved site the login is not linked to stays selected through an unrelated Update',
};

function key(value, what) {
  h.assert(/^[1-9]\d*$/.test(String(value)), `The owned ${what} key is invalid`);
  return String(value);
}

/** Four active sites named with the marker; the first three linked to the test provider. */
function ownedSites(s) {
  const { sql, marker, provider } = s;
  const hex = marker.slice(-7);
  const owned = `SELECT site_id FROM site WHERE name LIKE ${q(`${marker} S%`)}`;
  s.cleanup(() => {
    const ids = sql.rows(owned).map(([id]) => key(id, 'site')).join(',');
    if (ids) sql.execute(`DELETE FROM providersite WHERE site_id IN (${ids}); DELETE FROM site WHERE site_id IN (${ids})`);
    h.assert(sql.value(`SELECT COUNT(*) FROM site WHERE name LIKE ${q(`${marker} S%`)}`) === '0'
      && (!ids || sql.value(`SELECT COUNT(*) FROM providersite WHERE site_id IN (${ids})`) === '0'), 'The owned sites were not removed');
  });
  const sites = {};
  for (const [label, street, linked] of [['SA', '11 Alpha Road', true], ['SB', '22 Bravo Road', true],
    ['SC', '33 Charlie Road', true], ['SD', '44 Delta Road', false]]) {
    const name = `${marker} ${label}`;
    const id = key(h.insertId(sql, `INSERT INTO site (name, short_name, phone, fax, bg_color, address, city, province, postal, status)
      VALUES (${q(name)}, ${q(`${hex}${label}`)}, '555-0100', '555-0101', '#FFFFFF', ${q(street)}, 'Testville', 'ON', 'A1A1A1', 1)`,
    `site ${label}`), `site ${label}`);
    sites[label] = { id, name, street };
    if (linked) sql.execute(`INSERT INTO providersite (provider_no, site_id) VALUES (${q(provider)}, ${id})`);
  }
  return sites;
}

async function workflow(s) {
  const { sql, marker, provider, patient } = s;
  const daySheet = s.schedule;
  const day = sql.value('SELECT CURDATE()');
  const [year, month, dayOfMonth] = day.split('-');
  const query = new URLSearchParams({ year, month: String(Number(month)), day: String(Number(dayOfMonth)),
    view: '0', displaymode: 'day', dboperation: 'searchappointmentday', viewall: '1' });
  await h.gotoApp(daySheet, s.config.baseUrl, `/provider/providercontrol?${query}`);
  await daySheet.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
  if (await daySheet.locator('select#site').count() === 0) {
    throw new h.SkipCheck('multisites is off on this install (the day sheet has no clinic selector)');
  }

  const sites = ownedSites(s);
  const referrals = ownedReferrals(s);
  const second = referrals.extraPatient('SiteSecond');
  s.cleanup(() => {
    sql.execute(`DELETE FROM appointmentArchive WHERE demographic_no=${patient}; DELETE FROM appointment WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM appointment WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM appointmentArchive WHERE demographic_no=${patient})`) === '0', 'The owned visit was not removed');
  });
  const visit = key(h.insertId(sql, `INSERT INTO appointment (provider_no, appointment_date, start_time, end_time, name, demographic_no,
      notes, reason, location, status, createdatetime, updatedatetime, creator, lastupdateuser)
    VALUES (${q(provider)}, ${q(day)}, '10:00:00', '10:14:00', ${q(`${marker},Site`)}, ${patient}, ${q(marker)}, ${q(marker)},
      ${q(sites.SB.name)}, 't', NOW(), NOW(), ${q(provider)}, ${q(provider)})`, 'visit'), 'visit');
  const secondReason = `${marker} second patient referral`;
  const secondId = referrals.seed({ reason: secondReason, demographicNo: second.demographicNo, site_name: sites.SA.name });
  const reason = `${marker} site referral`;
  let requestId;
  const site = id => referrals.field(id, 'site_name');

  let form;
  let visitChart;
  await s.step(STEP.defaulted, async () => {
    // The day sheet only lists the visit once reloaded with today's appointments.
    await h.gotoApp(daySheet, s.config.baseUrl, `/provider/providercontrol?${query}`);
    const encounter = daySheet.locator(`a.encounterBtn[onclick*=",'encounter',${visit})"]`).first();
    await encounter.waitFor({ state: 'visible', timeout: TIMEOUT });
    visitChart = await s.popup(daySheet, encounter, 'site-chart');
    const chart = visitChart;
    const plus = chart.locator('a[onclick*="ViewConsultationFormRequest?de="]').first();
    await plus.waitFor({ state: 'attached', timeout: TIMEOUT });
    h.assert((await plus.getAttribute('onclick')).includes(`appNo=${visit}`), 'The chart opened from the visit does not carry it to the new referral');
    form = await s.popup(chart, plus, 'site-new-referral');
    await form.locator('#EctConsultationFormRequest2Form').waitFor({ state: 'attached', timeout: TIMEOUT });
    const select = form.locator('select#siteName');
    const offered = await select.locator('option').evaluateAll(options => options.map(o => o.value));
    for (const label of ['SA', 'SB', 'SC']) h.assert(offered.includes(sites[label].name), `The Site list does not offer ${label}`);
    h.assert(!offered.includes(sites.SD.name), 'The Site list offers a site the login is not linked to');
    h.assert(await select.inputValue() === sites.SB.name,
      `The new referral from the visit at SB opened on ${JSON.stringify(await select.inputValue())}`);
  });

  const list = await openConsultationsList(s);
  const open = async (id, why, label) => {
    const opened = await openFromList(s, list, id, why, label);
    return { form: opened, select: opened.locator('select#siteName') };
  };

  await s.step(STEP.chosen, async () => {
    await pickOwnedServiceAndConsultant(form, referrals);
    await form.locator('textarea[name="reasonForConsultation"]').fill(reason);
    await form.locator('select#siteName').selectOption(sites.SC.name);
    await save(form, 'submitSaveOnly', CREATED);
    await form.close();
    // The patient's chart window is named per patient: close this one before the E-Chart is opened again later.
    await visitChart.close();
    const created = referrals.requestsFor();
    h.assert(created.length === 1, `Submit stored ${created.length} referrals for the patient, not one`);
    [requestId] = created;
    h.assert(site(requestId) === sites.SC.name, `Submit stored the site ${JSON.stringify(site(requestId))}, not SC`);
    await refreshList(list);
    const reopened = await open(requestId, reason, 'site-reopen');
    h.assert(await reopened.select.inputValue() === sites.SC.name,
      `The referral reopened on ${JSON.stringify(await reopened.select.inputValue())}, not its saved site SC`);
    await reopened.form.close();
  });

  await s.step(STEP.changed, async () => {
    const edit = await open(requestId, reason, 'site-change');
    await edit.select.selectOption(sites.SB.name);
    await save(edit.form, 'update', UPDATED);
    await edit.form.close();
    h.assert(site(requestId) === sites.SB.name, `Update stored the site ${JSON.stringify(site(requestId))}, not SB`);
    h.assert(JSON.stringify(referrals.archived(requestId, ['site_name'])) === JSON.stringify([[sites.SC.name]]),
      'The update did not archive the previous site');
    h.assert(site(secondId) === sites.SA.name && referrals.archived(secondId).length === 0, 'The second patient\'s referral changed');
  });

  await s.step(STEP.unrelated, async () => {
    const notes = `${marker} notes changed alone`;
    const edit = await open(requestId, reason, 'site-unrelated');
    h.assert(await edit.select.inputValue() === sites.SB.name, 'The referral did not reopen on its saved site SB');
    await edit.form.locator('textarea[name="appointmentNotes"]').fill(notes);
    await save(edit.form, 'update', UPDATED);
    await edit.form.close();
    h.assert(referrals.field(requestId, 'statusText') === notes, 'The notes were not saved');
    h.assert(site(requestId) === sites.SB.name,
      `An Update of the notes alone moved the referral to ${JSON.stringify(site(requestId))}`);
  });

  await s.step(STEP.windows, async () => {
    // The first from the patient's chart, the second from the clinic list: two windows at once.
    const firstForm = await openFromChart(s, requestId, reason, 'site-window-first');
    const first = { form: firstForm, select: firstForm.locator('select#siteName') };
    const other = await open(secondId, secondReason, 'site-window-second');
    h.assert(await first.select.inputValue() === sites.SB.name && await other.select.inputValue() === sites.SA.name,
      'The two windows did not open on their own saved sites');
    await other.select.selectOption(sites.SC.name);
    await first.form.locator('textarea[name="appointmentNotes"]').fill(`${marker} first window saved first`);
    await save(first.form, 'update', UPDATED);
    h.assert(site(requestId) === sites.SB.name, 'The first window saved the site chosen in the second window');
    await save(other.form, 'update', UPDATED);
    h.assert(site(secondId) === sites.SC.name, `The second window stored ${JSON.stringify(site(secondId))}, not SC`);
    h.assert(site(requestId) === sites.SB.name, 'Saving the second window changed the first referral\'s site');
    h.assert(referrals.field(secondId, 'demographicNo') === second.demographicNo
      && referrals.field(requestId, 'demographicNo') === String(patient), 'A referral changed patient');
    await first.form.close();
    await other.form.close();
  });

  await s.step(STEP.printed, async () => {
    const edit = await open(requestId, reason, 'site-print');
    const letterhead = squash(await edit.form.locator('#letterheadAddress').inputValue());
    const download = edit.form.waitForEvent('download', { timeout: 30000 });
    await clickAndAwaitReload(edit.form, edit.form.locator('input[name="updateAndPrint"]'));
    const text = squash(pdfTextBuffer(fs.readFileSync(await (await download).path())));
    await edit.form.close();
    h.assert(text.includes(reason), 'The PDF is not the owned referral');
    h.assert(letterhead && text.includes(letterhead.split(' ')[0]), 'The PDF does not print the referral\'s letterhead address');
    h.assert(!text.includes(sites.SB.street) && !text.includes(sites.SB.name), 'The PDF prints the site in place of the letterhead');
    h.assert(site(requestId) === sites.SB.name, 'Update And Print Preview changed the saved site');
    console.log('  NOTE consultation-multisite-site: a site logo prints only with the operator\'s faxLogoInConsultation property '
      + 'and a site logo document; neither is configured by this check, so the logo is not exercised.');
  });

  await s.step(STEP.unlinked, async () => {
    // The clinic list shows a login only the referrals at its own sites, so this one is opened from the patient's chart.
    const unlinkedReason = `${marker} unlinked site referral`;
    const unlinkedId = referrals.seed({ reason: unlinkedReason, site_name: sites.SD.name });
    const edit = await openFromChart(s, unlinkedId, unlinkedReason, 'site-unlinked');
    const select = edit.locator('select#siteName');
    const offered = await select.locator('option').evaluateAll(options => options.map(o => o.value));
    h.assert(await select.inputValue() === sites.SD.name,
      `The referral saved at SD reopened on ${JSON.stringify(await select.inputValue())}`);
    h.assert(offered.filter(value => value === sites.SD.name).length === 1
      && ['SA', 'SB', 'SC'].every(label => offered.includes(sites[label].name)),
    `The Site list should offer the login's sites and keep SD once: ${JSON.stringify(offered)}`);
    const notes = `${marker} unlinked notes changed alone`;
    await edit.locator('textarea[name="appointmentNotes"]').fill(notes);
    await save(edit, 'update', UPDATED);
    await edit.close();
    h.assert(referrals.field(unlinkedId, 'statusText') === notes, 'The notes were not saved');
    h.assert(site(unlinkedId) === sites.SD.name,
      `An Update of the notes alone moved the referral from SD to ${JSON.stringify(site(unlinkedId))}`);
    h.assert(JSON.stringify(referrals.archived(unlinkedId, ['site_name'])) === JSON.stringify([[sites.SD.name]]),
      'The update did not archive the referral at SD');
  });
}

if (require.main === module) {
  runWorkflow('consultation-multisite-site', workflow, { openPatient: true, preflight: () => requirePoppler('pdftotext') });
}
module.exports = { workflow, STEP };
