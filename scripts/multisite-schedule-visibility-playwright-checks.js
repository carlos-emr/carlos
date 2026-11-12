#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * The day sheet on a multisite install (multisites=on): its clinic selector, what a chosen clinic shows, and the
 * appointment forms' location menus.
 * User paths: Schedule (the day view of a group) ▸ the clinic selector ▸ a clinic, then All Clinics; Schedule ▸ a
 * time slot (Add Appointment); Schedule ▸ a visit (Edit Appointment) ▸ Location ▸ Update Appt.
 * Asserted, as a login without site access privacy (the doctor role) linked to sites SA and SB:
 *   - the selector offers All Clinics and every active site, SC (linked to no one) included;
 *   - choosing SA shows only the group's provider at SA (PA, not PB) and only PA's visits at SA, not PA's visit at
 *     SB; choosing SB shows only PB, with PB's visit there;
 *   - All Clinics shows both providers and every visit again;
 *   - Add Appointment's location menu lists the login's own sites, SA and SB, and not SC;
 *   - LAST: Edit Appointment moves one of PA's SA visits to SB: it is stored at SB, and the SA view no longer shows it.
 * Skips when multisites is off (the day sheet has no clinic selector).
 * Fixtures: three active sites named with the run marker (SA, SB, SC); two providers, PA (at SA) and PB (at SB),
 * made in preflight, before login, because the day sheet's provider menu reads the cached active providers (the
 * cache fills at login; the live wrapper starts a fresh Tomcat); a group of PA and PB; a throwaway login (the doctor
 * role, without site access privacy) with a preference row on that group; today's visits of the owned patient: PA
 * at SA at 10:00 and 11:00, PA at SB at 10:30, and PB at SB at 10:00. The sites show in every login's clinic lists
 * while the check runs, so it must not run concurrently with other checks (live wrapper: EXCLUSIVE=1). Cleanup
 * removes the visits, the group, the login with the rows the application wrote for it, the site links, the
 * providers and the sites, and asserts each is gone.
 */
const { randomBytes, randomInt } = require('node:crypto');
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');
const { runWorkflow } = require('./lib/workflow-session');

const TIMEOUT = 30000;
const q = h.sqlString;

/** The step labels; one place, so the script and the manifest agree. */
const STEP = {
  selector: 'the day sheet\'s clinic selector offers All Clinics and every active site',
  oneSite: 'choosing a clinic shows only the group\'s providers there, and only their visits there',
  allSites: 'All Clinics shows the whole group and every visit again',
  addMenu: 'Add Appointment\'s location menu lists the login\'s own sites',
  move: 'Edit Appointment moves a visit to another site, and the first site\'s view no longer shows it',
};

function key(value, what) {
  h.assert(/^[1-9]\d*$/.test(String(value)), `The owned ${what} key is invalid`);
  return String(value);
}

/** A provider number no row of any table this check writes or cleans already uses. */
function unusedProviderNo(sql, taken = []) {
  for (let attempt = 0; attempt < 40; attempt++) {
    const candidate = String(randomInt(800000, 899999));
    const c = q(candidate);
    if (!taken.includes(candidate) && sql.value(`SELECT (SELECT COUNT(*) FROM provider WHERE provider_no=${c})
      + (SELECT COUNT(*) FROM security WHERE provider_no=${c}) + (SELECT COUNT(*) FROM providersite WHERE provider_no=${c})
      + (SELECT COUNT(*) FROM mygroup WHERE provider_no=${c}) + (SELECT COUNT(*) FROM appointment WHERE provider_no=${c})
      + (SELECT COUNT(*) FROM ProviderPreference WHERE providerNo=${c})`) === '0') return candidate;
  }
  throw new Error('No unused provider number was found');
}

// PA and PB, made before login (see the header). runWorkflow passes the test login's provider.
let providers = null;
async function preflight({ sql, provider }) {
  const marker = `FAKE-PW${randomBytes(8).toString('hex')}`;
  const pa = unusedProviderNo(sql);
  const pb = unusedProviderNo(sql, [pa]);
  const owned = `provider_no IN (${q(pa)},${q(pb)}) AND last_name=${q(marker)}`;
  // A backstop for a run that dies before its cleanup registers; the cleanup removes the rows and asserts them gone.
  process.once('exit', () => {
    try {
      sql.execute(`DELETE FROM providersite WHERE provider_no IN (${q(pa)},${q(pb)});
        DELETE FROM provider WHERE ${owned}`);
    } catch {
      // The live wrapper's residue counters report a provider left behind.
    }
  });
  sql.execute(`INSERT INTO provider (provider_no,last_name,first_name,provider_type,specialty,sex,status,lastUpdateUser,lastUpdateDate)
    SELECT ${q(pa)},${q(marker)},'SiteA',provider_type,specialty,sex,'1',${q(provider)},NOW() FROM provider WHERE provider_no=${q(provider)};
    INSERT INTO provider (provider_no,last_name,first_name,provider_type,specialty,sex,status,lastUpdateUser,lastUpdateDate)
    SELECT ${q(pb)},${q(marker)},'SiteB',provider_type,specialty,sex,'1',${q(provider)},NOW() FROM provider WHERE provider_no=${q(provider)}`);
  h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE ${owned}`) === '2', 'The owned providers PA and PB were not created');
  providers = { pa, pb, marker, owned };
}

/** Three active sites named with the run marker. Cleanup is registered before the first insert. */
function ownedSites(s) {
  const { sql, marker } = s;
  const hex = marker.slice(-8);
  const owned = `SELECT site_id FROM site WHERE name LIKE ${q(`${marker} S%`)}`;
  s.cleanup(() => {
    const ids = sql.rows(owned).map(([id]) => key(id, 'site')).join(',');
    if (ids) sql.execute(`DELETE FROM providersite WHERE site_id IN (${ids}); DELETE FROM site WHERE site_id IN (${ids})`);
    h.assert(sql.value(`SELECT COUNT(*) FROM site WHERE name LIKE ${q(`${marker} S%`)}`) === '0'
      && (!ids || sql.value(`SELECT COUNT(*) FROM providersite WHERE site_id IN (${ids})`) === '0'),
    'The owned sites were not removed');
  });
  const sites = {};
  for (const [label, color] of [['SA', '#CCFFCC'], ['SB', '#CCCCFF'], ['SC', '#FFCCCC']]) {
    const name = `${marker} ${label}`;
    const id = key(h.insertId(sql, `INSERT INTO site (name, short_name, phone, fax, bg_color, address, city, province, postal, status)
      VALUES (${q(name)}, ${q(`${hex}${label}`)}, '555-0100', '555-0101', ${q(color)}, '1 ${label} Road', 'Testville', 'ON',
        'A1A1A1', 1)`, `site ${label}`), `site ${label}`);
    sites[label] = { id, name };
  }
  return sites;
}

function linkSites(sql, providerNo, sites) {
  sql.execute(sites.map(site => `INSERT INTO providersite (provider_no, site_id) VALUES (${q(providerNo)}, ${site.id})`).join(';'));
}

/** A throwaway login whose day sheet opens on the owned group. Its cleanup is registered before its first write. */
function groupLogin(s, roleNames, groupName) {
  const fixture = throwawayLoginFixture({
    sql: s.sql, marker: `FAKE-PW${randomBytes(8).toString('hex')}`, provider: s.provider, testUser: s.config.testUser,
  });
  s.cleanup(() => fixture.cleanup());
  fixture.create({ roleNames });
  s.sql.execute(`INSERT INTO ProviderPreference (providerNo,startHour,endHour,everyMin,myGroupNo,colourTemplate,
      printQrCodeOnPrescriptions,lastUpdated,appointmentScreenLinkNameDisplayLength,defaultDoNotDeleteBilling)
    VALUES (${q(fixture.providerNo)},8,18,15,${q(groupName)},'deepblue',0,NOW(),20,0)`);
  return fixture;
}

/** A signed-in browser context for one throwaway login, wired like the workflow's own context, and its day sheet. */
async function loginContext(s, fixture, label) {
  const context = await h.newContext(s.context.browser(), s.config);
  s.cleanup(() => context.close().catch(() => {}));
  context.setDefaultTimeout(20000);
  context.on('page', page => h.wireStrictPage(page, label, s.recorder));
  const page = await h.login(context, { ...s.config, testUser: fixture.username }, s.recorder, { label: `${label}-login` });
  await page.waitForLoadState('domcontentloaded');
  // The owned providers have no schedule for the day, and with viewall=0 the day sheet hides unscheduled providers
  // other than the logged-in one. Open the all-provider view; choosing a clinic reloads the same address.
  if (new URL(page.url()).searchParams.get('viewall') !== '1') {
    await h.gotoApp(page, s.config.baseUrl, '/provider/providercontrol?view=0&displaymode=day&dboperation=searchappointmentday&viewall=1');
    await h.assertNotErrorPage(page, 'the all-provider day sheet');
  }
  return { context, page };
}

const slots = (page, providerNo) => page.locator(`a.adhour[onclick*="provider_no=${providerNo}&"]`);
const visit = (page, id) => page.locator(`a.apptLink[onclick*="appointment_no=${id}&"], a.apptLink[onclick*="appointment_no=${id}'"]`);

/** The owned sites' names among a select's options. */
async function ownedOptions(select, sites) {
  const names = Object.values(sites).map(site => site.name);
  return (await select.locator('option').evaluateAll(options => options.map(option => option.value)))
    .filter(value => names.includes(value));
}

/** Chooses a clinic in the selector: the preference popup saves it and reloads the day sheet. */
async function chooseClinic(page, value) {
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 60000 }),
    page.locator('select#site').selectOption(value),
  ]);
  await h.assertNotErrorPage(page, 'the day sheet after choosing a clinic');
  h.assert(await page.locator('select#site').inputValue() === value, `The day sheet did not come back on clinic "${value}"`);
}

/** Asserts which providers' columns and which visits the day sheet shows. */
async function assertShown(page, { columns, hiddenColumns = [], visits, hiddenVisits = [] }, label) {
  for (const providerNo of columns) {
    h.assert(await slots(page, providerNo).count() > 0, `${label}: provider ${providerNo}'s column is not shown`);
  }
  for (const providerNo of hiddenColumns) {
    h.assert(await slots(page, providerNo).count() === 0, `${label}: provider ${providerNo}'s column is shown`);
  }
  for (const [name, id] of visits) h.assert(await visit(page, id).count() > 0, `${label}: the visit ${name} is not shown`);
  for (const [name, id] of hiddenVisits) h.assert(await visit(page, id).count() === 0, `${label}: the visit ${name} is shown`);
}

async function workflow(s) {
  const { sql, marker, patient } = s;
  h.assert(providers, 'The providers PA and PB were not made before login');
  const { pa, pb } = providers;
  s.cleanup(() => {
    sql.execute(`DELETE FROM providersite WHERE provider_no IN (${q(pa)},${q(pb)}); DELETE FROM provider WHERE ${providers.owned}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM provider WHERE provider_no IN (${q(pa)},${q(pb)})`) === '0',
      'The owned providers PA and PB were not removed');
  });
  const groupName = `PW${marker.slice(-8)}`;
  const visitIds = [];
  // Registered before the group (cleanups run in reverse): the visits and the group go before the login.
  const viewer = groupLogin(s, ['doctor'], groupName);
  s.cleanup(() => {
    const ownedProviders = [pa, pb, viewer.providerNo].map(q).join(',');
    h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE provider_no IN (${ownedProviders})
      AND demographic_no NOT IN (0,${patient})`) === '0', 'An owned provider holds a visit for a patient this run does not own');
    if (visitIds.length) sql.execute(`DELETE FROM other_id WHERE table_name=2 AND table_id IN (${visitIds.map(q).join(',')})`);
    sql.execute(`DELETE FROM appointmentArchive WHERE provider_no IN (${ownedProviders}) AND demographic_no=${patient};
      DELETE FROM appointment WHERE provider_no IN (${ownedProviders}) AND demographic_no=${patient};
      DELETE FROM mygroup WHERE mygroup_no=${q(groupName)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM appointment WHERE provider_no IN (${ownedProviders}))
      + (SELECT COUNT(*) FROM appointmentArchive WHERE provider_no IN (${ownedProviders}))
      + (SELECT COUNT(*) FROM mygroup WHERE mygroup_no=${q(groupName)})`) === '0', 'The owned visits or group were not removed');
  });
  h.assert(sql.value(`SELECT COUNT(*) FROM mygroup WHERE mygroup_no=${q(groupName)}`) === '0', 'The marker group already exists');
  sql.execute(`INSERT INTO mygroup (mygroup_no,provider_no,last_name,first_name,vieworder) VALUES
    (${q(groupName)},${q(pa)},${q(providers.marker)},'SiteA','1'), (${q(groupName)},${q(pb)},${q(providers.marker)},'SiteB','2')`);
  const sites = ownedSites(s);
  linkSites(sql, pa, [sites.SA]);
  linkSites(sql, pb, [sites.SB]);
  linkSites(sql, viewer.providerNo, [sites.SA, sites.SB]);
  const day = sql.value('SELECT CURDATE()');
  const book = (label, providerNo, site, start, end) => {
    const id = key(h.insertId(sql, `INSERT INTO appointment (provider_no, appointment_date, start_time, end_time, name,
        demographic_no, notes, reason, location, status, createdatetime, updatedatetime, creator, lastupdateuser)
      VALUES (${q(providerNo)}, ${q(day)}, '${start}', '${end}', ${q(`${marker},${label}`)}, ${patient}, ${q(marker)},
        ${q(marker)}, ${q(site.name)}, 't', NOW(), NOW(), ${q(s.provider)}, ${q(s.provider)})`, `visit ${label}`), `visit ${label}`);
    visitIds.push(id);
    return id;
  };
  const A1 = ['PA at SA, 10:00', book('A1', pa, sites.SA, '10:00:00', '10:14:00')];
  const A2 = ['PA at SB, 10:30', book('A2', pa, sites.SB, '10:30:00', '10:44:00')];
  const A3 = ['PA at SA, 11:00', book('A3', pa, sites.SA, '11:00:00', '11:14:00')];
  const B1 = ['PB at SB, 10:00', book('B1', pb, sites.SB, '10:00:00', '10:14:00')];

  const { context, page: daySheet } = await loginContext(s, viewer, 'multisite-viewer');
  if (await daySheet.locator('select#site').count() === 0) {
    throw new h.SkipCheck('multisites is off on this install (the day sheet has no clinic selector)');
  }

  await s.step(STEP.selector, async () => {
    const select = daySheet.locator('select#site');
    const values = await select.locator('option').evaluateAll(options => options.map(option => option.value));
    h.assert(values[0] === 'none', 'The clinic selector does not start with All Clinics');
    const active = sql.rows('SELECT name FROM site WHERE status=1').map(([name]) => name);
    h.assert(active.every(name => values.includes(name)) && values.length === active.length + 1,
      `The clinic selector offers ${values.length - 1} clinics, not the ${active.length} active sites`);
    h.assert(JSON.stringify((await ownedOptions(select, sites)).sort())
      === JSON.stringify([sites.SA.name, sites.SB.name, sites.SC.name].sort()), 'The clinic selector does not offer SA, SB and SC');
    await assertShown(daySheet, { columns: [pa, pb], visits: [A1, A2, A3, B1] }, 'All Clinics before any choice');
  });

  await s.step(STEP.oneSite, async () => {
    await chooseClinic(daySheet, sites.SA.name);
    await assertShown(daySheet, { columns: [pa], hiddenColumns: [pb], visits: [A1, A3], hiddenVisits: [A2, B1] }, 'Clinic SA');
    await chooseClinic(daySheet, sites.SB.name);
    await assertShown(daySheet, { columns: [pb], hiddenColumns: [pa], visits: [B1], hiddenVisits: [A1, A2, A3] }, 'Clinic SB');
  });

  await s.step(STEP.allSites, async () => {
    await chooseClinic(daySheet, 'none');
    await assertShown(daySheet, { columns: [pa, pb], visits: [A1, A2, A3, B1] }, 'All Clinics');
  });

  await s.step(STEP.addMenu, async () => {
    const slot = slots(daySheet, pa).filter({ hasText: '09:00' }).first();
    const add = await ui.clickOpensPopup(daySheet, slot, { context, recorder: s.recorder, label: 'add-appointment', timeout: 20000 });
    await add.waitForLoadState('domcontentloaded');
    await add.locator('#addButton').waitFor({ timeout: 20000 });
    await h.assertNotErrorPage(add, 'Add Appointment');
    const offered = await ownedOptions(add.locator('form#addappt select[name="location"]'), sites);
    h.assert(JSON.stringify(offered.sort()) === JSON.stringify([sites.SA.name, sites.SB.name].sort()),
      `Add Appointment's location menu offers ${offered.length} of the owned sites, not exactly SA and SB`);
    await add.close();
    h.assert(sql.value(`SELECT COUNT(*) FROM appointment WHERE provider_no=${q(pa)}`) === '3', 'Opening Add Appointment booked something');
  });

  await s.step(STEP.move, async () => {
    const link = visit(daySheet, A3[1]).first();
    const edit = await ui.clickOpensPopup(daySheet, link, { context, recorder: s.recorder, label: 'edit-appointment', timeout: 20000 });
    await edit.waitForLoadState('domcontentloaded');
    await edit.locator('#updateButton').waitFor({ timeout: 20000 });
    await h.assertNotErrorPage(edit, 'Edit Appointment');
    h.assert(await edit.locator('input[name="appointment_no"]').inputValue() === A3[1], 'Edit Appointment opened a different visit');
    const location = edit.locator('select[name="location"]');
    h.assert(await location.inputValue() === sites.SA.name, 'Edit Appointment does not show the visit at SA');
    await location.selectOption(sites.SB.name);
    // The saved popup closes itself and refreshes the day sheet behind it; wait for that reload, if it comes, so it
    // cannot overtake the next clinic choice.
    await Promise.all([
      daySheet.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 15000 }).catch(() => null),
      edit.waitForEvent('close', { timeout: 30000 }),
      edit.locator('#updateButton').click(),
    ]);
    h.assert(sql.value(`SELECT location FROM appointment WHERE appointment_no=${A3[1]}`) === sites.SB.name,
      'Update Appt did not store the visit at SB');
    await chooseClinic(daySheet, sites.SA.name);
    await assertShown(daySheet, { columns: [pa], visits: [A1], hiddenVisits: [A2, A3, B1] }, 'Clinic SA after the move');
    await chooseClinic(daySheet, 'none');
  });
}

if (require.main === module) runWorkflow('multisite-schedule-visibility', workflow, { preflight, openMaster: false });
module.exports = { workflow, STEP };
