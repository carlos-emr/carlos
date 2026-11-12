#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * On a multisite install the prescription print window preselects the clinic address of the
 * patient's own visit, after the day sheet opened another patient's visit (issue #4673).
 *
 * User path (multisites=on): Schedule ▸ Rx on A's appointment at site SA (the Rx window) ▸ M on
 * B's appointment at site SB (the Master Record) ▸ back in A's Rx window: Custom Drug ▸ Save And
 * Print ▸ the print window's Address list shows SA; the prescriber picks SC by hand. Then Rx on
 * B's appointment ▸ Save And Print shows SB, and a prescription for C, who has no visit today,
 * shows SC, the address last picked by hand.
 *
 * Why: the print window looked up "the current visit" in the login-wide cur_appointment_no, which
 * the day sheet set to the appointment last clicked for any patient, so A's prescription showed
 * B's clinic. Loading the window also stored that preselection as the remembered address, so C's
 * prescription showed B's clinic as well. The visit is now filed per patient
 * (provider/ViewStoreApptInSession) and only a hand-picked address is remembered.
 *
 * Also pins the multisite tickler page, which looked the same visit up as if to preselect its clinic
 * but never applied it: A's new tickler still starts on "select clinic", and picking A's clinic
 * lists its providers. And the print window's address list shows a clinic name with markup (site
 * SD) as written; it printed the names unencoded.
 *
 * Skips when multisites is off (the day sheet has no clinic selector).
 *
 * Fixtures: the workflow's FAKE patient (A), two more FAKE patients (B with a visit, C without),
 * four active sites named with the run's marker and linked to the test provider, A's and B's
 * appointments today, and the custom drugs and scripts saved for A, B and C. Cleanup removes
 * every owned row by key and asserts it gone.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { stageCustomDrug, clearOwnedPrescriptionRows } = require('./rx-stash-patient-isolation-playwright-checks');

const TIMEOUT = 30000;
const q = h.sqlString;

function key(value, what) {
  h.assert(/^[1-9]\d*$/.test(String(value)), `The owned ${what} key is invalid`);
  return String(value);
}

async function settle(page, label) {
  await page.waitForLoadState('domcontentloaded', { timeout: TIMEOUT });
  await page.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
  await h.assertNotErrorPage(page, label);
}

/** A FAKE patient owned by this run (the marker last name); removed after its child rows. */
function ownedPatient(s, firstName) {
  const { sql, marker, provider } = s;
  const patient = key(h.insertId(sql, `INSERT INTO demographic (last_name, first_name, year_of_birth,
      month_of_birth, date_of_birth, sex, patient_status, provider_no, hc_type, province, roster_status,
      lastUpdateDate)
    VALUES (${q(marker)}, ${q(firstName)}, '1981', '07', '08', 'F', 'AC', ${q(provider)}, 'ON', 'ON', 'NR',
      NOW())`, firstName), firstName);
  s.cleanup(() => {
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient}
      AND last_name=${q(marker)}`) === '1', `The ${firstName} fixture changed owner`);
    sql.execute(`DELETE FROM demographicExt WHERE demographic_no=${patient};
      DELETE FROM demographicArchive WHERE demographic_no=${patient};
      DELETE FROM demographic WHERE demographic_no=${patient} AND last_name=${q(marker)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient}`) === '0',
      `The ${firstName} fixture was not removed`);
  });
  return patient;
}

/** Three active clinic sites named with the marker, linked to the test provider. */
function ownedSites(s) {
  const { sql, marker, provider } = s;
  const hex = marker.slice(-8);
  const sites = {};
  // Registered before the first insert: a site left behind would show in every login's clinic
  // lists. The sites are found by the run's marker, which no other site name carries.
  const owned = `SELECT site_id FROM site WHERE name LIKE ${q(`${marker} S%`)}`;
  s.cleanup(() => {
    const ids = sql.rows(owned).map(([id]) => key(id, 'site')).join(',');
    if (ids) {
      sql.execute(`DELETE FROM providersite WHERE site_id IN (${ids});
        DELETE FROM site WHERE site_id IN (${ids})`);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM site WHERE name LIKE ${q(`${marker} S%`)}`) === '0'
      && (!ids || sql.value(`SELECT COUNT(*) FROM providersite WHERE site_id IN (${ids})`) === '0'),
    'The owned sites were not removed');
  });
  // SD's name carries markup, which the print window's address list must show as text.
  for (const [label, street, suffix] of [['SA', '11 Alpha Road', ''], ['SB', '22 Bravo Road', ''],
    ['SC', '33 Charlie Road', ''], ['SD', '44 Delta Road', ' <i>Clinic</i> & "Co"']]) {
    const name = `${marker} ${label}${suffix}`;
    const id = key(h.insertId(sql, `INSERT INTO site (name, short_name, phone, fax, bg_color, address, city,
        province, postal, status)
      VALUES (${q(name)}, ${q(`${hex}${label}`)}, '555-0100', '555-0101', '#FFFFFF', ${q(street)}, 'Testville',
        'ON', 'A1A1A1', 1)`, `site ${label}`), `site ${label}`);
    sites[label] = { id, name };
  }
  sql.execute(Object.values(sites).map(site =>
    `INSERT INTO providersite (provider_no, site_id) VALUES (${q(provider)}, ${site.id})`).join(';'));
  return sites;
}

/** Today's appointments for the owned patients, each at its own site. */
function ownedVisits(s, visits) {
  const { sql, marker, provider } = s;
  const day = sql.value('SELECT CURDATE()');
  const list = visits.map(visit => visit.patient).join(',');
  s.cleanup(() => {
    sql.execute(`DELETE FROM appointmentArchive WHERE demographic_no IN (${list});
      DELETE FROM appointment WHERE demographic_no IN (${list})`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM appointment WHERE demographic_no IN (${list}))
      + (SELECT COUNT(*) FROM appointmentArchive WHERE demographic_no IN (${list}))`) === '0',
    'The owned appointments were not removed');
  });
  const appointmentOf = {};
  for (const { label, patient, site, start, end } of visits) {
    appointmentOf[label] = key(h.insertId(sql, `INSERT INTO appointment (provider_no, appointment_date,
        start_time, end_time, name, demographic_no, notes, reason, location, status, createdatetime,
        updatedatetime, creator, lastupdateuser)
      VALUES (${q(provider)}, ${q(day)}, '${start}', '${end}', ${q(`${marker},${label}`)}, ${patient},
        ${q(marker)}, ${q(marker)}, ${q(site.name)}, 't', NOW(), NOW(), ${q(provider)}, ${q(provider)})`,
    `${label}'s appointment`), `${label}'s appointment`);
  }
  return { day, appointmentOf };
}

/** The print window is an iframe over the Rx page; poll Playwright's own frame list for it. */
async function printWindow(rx) {
  const deadline = Date.now() + 60000;
  while (Date.now() < deadline) {
    const frame = rx.frames().find(f => /\/rx\/viewScript\?/.test(f.url()));
    if (frame) return frame;
    await rx.waitForTimeout(250);
  }
  throw new Error('The print window (rx/viewScript) did not open');
}

async function workflow(s) {
  const { sql, marker, provider, context, recorder } = s;
  const A = key(s.patient, 'patient');
  const daySheet = s.schedule;
  const B = ownedPatient(s, 'VisitBravo');
  const C = ownedPatient(s, 'NoVisitCharlie');
  for (const patient of [A, B, C]) s.cleanup(() => clearOwnedPrescriptionRows(sql, patient, marker));
  const sites = ownedSites(s);
  const { day, appointmentOf } = ownedVisits(s, [
    { label: 'A', patient: A, site: sites.SA, start: '10:00:00', end: '10:14:00' },
    { label: 'B', patient: B, site: sites.SB, start: '10:15:00', end: '10:29:00' },
  ]);

  const [year, month, dayOfMonth] = day.split('-');
  const query = new URLSearchParams({ year, month: String(Number(month)), day: String(Number(dayOfMonth)),
    view: '0', displaymode: 'day', dboperation: 'searchappointmentday', viewall: '1' });
  await h.gotoApp(daySheet, s.config.baseUrl, `/provider/providercontrol?${query}`);
  await settle(daySheet, 'day sheet');
  if (await daySheet.locator('select#site').count() === 0) {
    throw new h.SkipCheck('multisites is off on this install (the day sheet has no clinic selector)');
  }

  /** A day-sheet link of the patient's appointment; records it as the patient's visit and opens a window. */
  async function openFromDaySheet(label, link, path) {
    await link.waitFor({ state: 'visible', timeout: TIMEOUT });
    const [stored, opened] = await Promise.all([
      daySheet.waitForResponse(r => r.request().method() === 'POST'
        && new URL(r.url()).pathname.endsWith('/provider/ViewStoreApptInSession'), { timeout: TIMEOUT }),
      context.waitForEvent('page', { timeout: TIMEOUT }),
      link.click(),
    ]);
    h.assert(stored.status() === 204 && new URLSearchParams(stored.request().postData() || '')
      .get('appointment_no') === appointmentOf[label],
    `The day sheet did not record ${label}'s appointment (HTTP ${stored.status()})`);
    await opened.waitForURL(url => url.pathname.endsWith(path), { timeout: TIMEOUT });
    await settle(opened, `${label}'s ${path} from the day sheet`);
    return opened;
  }
  const rxLink = label => daySheet.locator(`a[onclick*="/rx/choosePatient"][onclick*=",'rx',${appointmentOf[label]})"]`).first();
  const masterLink = label => daySheet.locator(`a.masterBtn[onclick*="appointment=${appointmentOf[label]}'"]`).first();

  /** Custom Drug ▸ Save And Print on an Rx page; returns the print window and its preselected address. */
  async function saveAndPrint(rx, label, patient) {
    const drug = `${marker}-${label}`;
    const card = await stageCustomDrug(rx, drug);
    await rx.locator(`#instructions_${card}`).fill('Take one tablet daily');
    await rx.locator(`#instructions_${card}`).blur();
    await rx.locator('#saveButton').click();
    const print = await printWindow(rx);
    const scriptNo = new URL(print.url()).searchParams.get('scriptId');
    h.assert(/^[1-9]\d*$/.test(scriptNo || ''), `Save And Print opened no saved script for ${label}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM drugs WHERE script_no=${scriptNo} AND demographic_no=${patient}
      AND customName=${q(drug)}`) === '1', `${label}'s Save And Print did not save ${label}'s drug`);
    const address = print.locator('#addressSel');
    await address.waitFor({ state: 'visible', timeout: TIMEOUT });
    const shown = await address.evaluate(select => select.options[select.selectedIndex]?.label || '');
    return { print, address, shown };
  }

  let rxA;
  await s.step('A\'s print window preselects A\'s visit site after B\'s visit was opened from the day sheet', async () => {
    rxA = await openFromDaySheet('A', rxLink('A'), '/rx/choosePatient');
    h.assert(new URL(rxA.url()).searchParams.get('demographicNo') === A, 'The day sheet\'s Rx link opened another patient than A');
    const master = await openFromDaySheet('B', masterLink('B'), '/demographic/DemographicEdit');
    await master.close();
    const { address, shown } = await saveAndPrint(rxA, 'A', A);
    // A clinic name is text: its markup is shown, not applied.
    const labels = await address.evaluate(select => Array.from(select.options, option => option.label));
    h.assert(labels.includes(sites.SD.name), 'The print window\'s address list does not show a clinic name with markup as written');
    h.assert(shown === sites.SA.name, `A's print window preselected ${shown === sites.SB.name
      ? 'B\'s visit site, the visit the day sheet opened last' : `"${shown}"`} instead of A's visit site`);

    // The prescriber picks another clinic by hand: that choice is remembered for later prescriptions.
    const [remembered] = await Promise.all([
      rxA.waitForResponse(r => r.request().method() === 'POST'
        && new URL(r.url()).pathname.endsWith('/rx/ViewSetDefaultAddr'), { timeout: TIMEOUT }),
      address.selectOption({ label: sites.SC.name }),
    ]);
    h.assert(remembered.ok(), `Remembering the picked address answered HTTP ${remembered.status()}`);
    await rxA.close();
    // The page's fetch never reads the empty reply, so its body may stay open until the window
    // closes and Chromium then cancels it (net::ERR_ABORTED). Wait, briefly, for that to be
    // reported: the entry, for the request answered above, is not a failure.
    await Promise.race([remembered.finished().catch(() => {}), new Promise(resolve => setTimeout(resolve, 5000))]);
    for (let i = recorder.requestFailures.length - 1; i >= 0; i--) {
      const entry = recorder.requestFailures[i];
      if (entry.errorText === 'net::ERR_ABORTED' && new URL(entry.url).pathname.endsWith('/rx/ViewSetDefaultAddr')) {
        recorder.requestFailures.splice(i, 1);
      }
    }
  });

  await s.step('B\'s print window preselects B\'s visit site without changing the address picked by hand', async () => {
    const rxB = await openFromDaySheet('B', rxLink('B'), '/rx/choosePatient');
    h.assert(new URL(rxB.url()).searchParams.get('demographicNo') === B, 'The day sheet\'s Rx link opened another patient than B');
    const { shown } = await saveAndPrint(rxB, 'B', B);
    h.assert(shown === sites.SB.name, `B's print window preselected "${shown}" instead of B's visit site`);
    await rxB.close();
  });

  await s.step('a prescription for a patient with no visit shows the address picked by hand, not another patient\'s visit site', async () => {
    const rxC = await context.newPage();
    await h.gotoApp(rxC, s.config.baseUrl, `/rx/choosePatient?providerNo=${encodeURIComponent(provider)}&demographicNo=${C}`);
    await settle(rxC, 'C\'s Rx page');
    const { shown } = await saveAndPrint(rxC, 'C', C);
    h.assert(shown === sites.SC.name, `C's print window preselected ${shown === sites.SB.name
      ? 'B\'s visit site, which loading B\'s print window had remembered' : `"${shown}"`}`
      + ' instead of the address picked by hand');
    await rxC.close();
  });

  await s.step('A\'s new tickler preselects no clinic, and picking A\'s clinic lists its providers', async () => {
    // The tickler page looked up the login's last-opened visit (here B's) as if to preselect its
    // clinic but never applied it; the lookup is gone and the page still starts on "select clinic".
    const tickler = await context.newPage();
    await h.gotoApp(tickler, s.config.baseUrl,
      `/tickler/ViewAddTickler?updateParent=true&bFirstDisp=false&demographic_no=${A}`);
    await settle(tickler, 'A\'s new tickler');
    const clinic = tickler.locator('select#site');
    h.assert(await clinic.inputValue() === 'none', `A's new tickler preselected the clinic "${await clinic.inputValue()}"`);
    // A saved assignee preference shows its name in place of the clinic list until Change is clicked.
    if (!await clinic.isVisible()) await tickler.locator('#nameWrapper a').click();
    await clinic.selectOption({ label: sites.SA.name });
    const assignees = await tickler.locator('select#task_assigned_to option')
      .evaluateAll(options => options.map(option => option.value));
    h.assert(assignees.includes(provider), 'Picking A\'s clinic did not list the clinic\'s provider as an assignee');
    await tickler.close();
  });
}

if (require.main === module) runWorkflow('rx-preview-visit-site-isolation', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
