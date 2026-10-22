#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * A chart entry remembers only an appointment of its own patient (issue #4676).
 *
 * IncomingEncounter (the day sheet's E link, and every other chart entry) remembers, per patient in
 * the login, the entry's provider, appointment, reason and encounter type. The chart's notes panel
 * names that provider, a new note takes that encounter type, and the eForm and billing links carry
 * that appointment. The entry used to accept any appointment number, and its legacy appointmentList
 * branch (whose one URL builder, encounter.js getAnotherEncounter(), no page called) loaded the
 * NAMED APPOINTMENT'S patient into the entry and remembered it under that patient, before the page
 * refused to render a chart other than the one requested. So a request naming A with one of B's
 * visits overwrote B's remembered entry while B's chart was open in another window, and a request
 * naming A with B's visit made B's visit A's remembered appointment.
 *
 * User path: two windows of one login. A's chart is entered for A's visit by the logged-in provider;
 * B's chart is entered from another provider's day sheet for one of B's visits. Each chart saves a
 * note, whose re-rendered notes panel names the chart's entry provider.
 *
 * Steps: the entry of A's own visit, also with the old appointmentList flag, opens A's chart for that
 * visit; requests naming A with B's visit (with and without appointmentList), with a visit that does
 * not exist, with a visit booked without a patient, naming B with A's visit, and the old builder's
 * request (no patient) are refused (400) and change neither patient's rows; afterwards each chart's
 * next note save still names its own entry provider and links its own visit. With the old action,
 * B's panel named the logged-in provider after the appointmentList request, A's named the other
 * provider after the request naming A with B's visit, and those requests opened charts instead of
 * being refused.
 *
 * Fixtures: the workflow's FAKE patient (A, first name EntryAlpha), a second FAKE patient (B,
 * EntryBravo, last name the run marker), one visit of A's, two of B's and one booked without a
 * patient, all owned by the run marker. Cleanup removes both patients' notes and their children,
 * locks, drafts, eChart revisions and appointment rows, and the unbooked visit, by key, and asserts
 * each is gone.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { releaseChartLocks } = require('./lib/chart-lock-cleanup');
const { waitForNavbars } = require('./echart-navbar-modules-playwright-checks');
const { readPatientState, assertPatientStateUnchanged, saveCurrentChartNote } =
  require('./echart-two-patient-isolation-playwright-checks');

const TIMEOUT = 20000;
const OBSERVED = new Date().toISOString().slice(0, 10);
const A_FIRST_NAME = 'EntryAlpha';
const B_FIRST_NAME = 'EntryBravo';
const norm = text => text.replace(/\s+/g, ' ').trim();

function key(value) {
  h.assert(/^[1-9]\d*$/.test(String(value)), 'An owned key is invalid');
  return String(value);
}

async function run(s) {
  const { sql, patient: A, provider, marker, context } = s;
  const q = h.sqlString;
  const base = s.config.baseUrl;

  // ---- Fixtures --------------------------------------------------------------------------------
  sql.execute(`UPDATE demographic SET first_name=${q(A_FIRST_NAME)}, provider_no=${q(provider)}
    WHERE demographic_no=${A} AND last_name=${q(marker)}`);
  // B is entered from this provider's day sheet, so B's notes panel names a provider that is not
  // the logged-in one, and an entry that overwrites B's remembered entry is visible.
  const other = sql.value(`SELECT provider_no FROM provider WHERE provider_no<>${q(provider)} AND status='1'
    AND provider_type='doctor' AND provider_no REGEXP '^[1-9][0-9]{0,5}$' ORDER BY provider_no LIMIT 1`);
  h.assert(/^[1-9]\d{0,5}$/.test(other), 'A second active doctor is required for B\'s entry');
  const B = h.insertId(sql, `INSERT INTO demographic (last_name, first_name, year_of_birth, month_of_birth,
    date_of_birth, sex, patient_status, provider_no, hc_type, province, roster_status, lastUpdateDate)
    VALUES (${q(marker)}, ${q(B_FIRST_NAME)}, '1975', '03', '04', 'M', 'AC', ${q(other)}, 'ON', 'ON', 'NR', NOW())`,
  'patient B');
  const patients = `${key(A)},${key(B)}`;
  const visits = [];
  // Registered first, so it runs last: after every child row below is gone. The harness removes A.
  s.cleanup(() => {
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${B} AND last_name=${q(marker)}`) === '1',
      'Patient B fixture ownership changed');
    sql.execute(['demographicExt', 'demographicArchive']
      .map(table => `DELETE FROM ${table} WHERE demographic_no=${B}`).join(';'));
    sql.execute(`DELETE FROM demographic WHERE demographic_no=${B} AND last_name=${q(marker)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${B}`) === '0', 'Patient B was not removed');
  });
  s.cleanup(() => {
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${B}
      AND last_name=${q(marker)}`) === '1', 'Patient B ownership changed before child cleanup');
    const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no IN (${patients})`;
    const owned = visits.length ? visits.map(key).join(',') : '0';
    sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note WHERE demographic_no IN (${patients});
      DELETE FROM casemgmt_issue WHERE demographic_no IN (${patients});
      DELETE FROM casemgmt_note_lock WHERE demographic_no IN (${patients});
      DELETE FROM casemgmt_tmpsave WHERE demographic_no IN (${patients});
      DELETE FROM eChart WHERE demographicNo IN (${patients});
      DELETE FROM appointmentArchive WHERE demographic_no IN (${patients}) OR appointment_no IN (${owned});
      DELETE FROM appointment WHERE (demographic_no IN (${patients}) OR appointment_no IN (${owned}))
        AND name=${q(marker)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no IN (${patients}))
      + (SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no IN (${patients}))
      + (SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no IN (${patients}))
      + (SELECT COUNT(*) FROM eChart WHERE demographicNo IN (${patients}))
      + (SELECT COUNT(*) FROM appointmentArchive WHERE demographic_no IN (${patients}) OR appointment_no IN (${owned}))
      + (SELECT COUNT(*) FROM appointment WHERE demographic_no IN (${patients}) OR appointment_no IN (${owned}))`) === '0',
    'Owned chart-entry rows were not removed');
  });
  /** One owned To Do visit; patient 0 is a visit booked without a patient. */
  const addVisit = (patient, startTime, endTime) => {
    const id = h.insertId(sql, `INSERT INTO appointment (provider_no, appointment_date, start_time, end_time, name,
      demographic_no, notes, reason, status, createdatetime, updatedatetime, creator, lastupdateuser)
      VALUES (${q(provider)}, ${q(OBSERVED)}, ${q(startTime)}, ${q(endTime)}, ${q(marker)},
      ${patient}, ${q(marker)}, ${q(`${marker} booked reason`)}, 't', NOW(), NOW(), ${q(provider)}, ${q(provider)})`,
    'appointment');
    visits.push(id);
    return id;
  };
  const visit = {A: addVisit(A, '23:00:00', '23:14:59'), B1: addVisit(B, '23:15:00', '23:29:59'),
    B2: addVisit(B, '23:30:00', '23:44:59'), unbooked: addVisit(0, '23:45:00', '23:59:59')};
  const nonexistent = String(Number(sql.value('SELECT MAX(appointment_no) FROM appointment')) + 1000);
  const visitRows = () => sql.rows(`SELECT * FROM appointment WHERE appointment_no IN (${visits.map(key).join(',')})
    ORDER BY appointment_no`);

  const entryPath = params => `/encounter/IncomingEncounter?${new URLSearchParams(params)}`;
  const entryOf = {
    A: {demographicNo: A, providerNo: provider, curProviderNo: provider, appointmentNo: visit.A,
      reason: `${marker} A reason`, encType: 'face to face encounter with client', appointmentDate: OBSERVED,
      startTime: '23:00', status: 't'},
    B: {demographicNo: B, providerNo: other, curProviderNo: other, appointmentNo: visit.B1,
      reason: `${marker} B reason`, encType: 'telephone encounter with client', appointmentDate: OBSERVED,
      startTime: '23:15', status: 't'},
  };

  async function entryResponse(params) {
    const response = await context.request.get(h.appUrl(base, entryPath(params)),
      {maxRedirects: 0, failOnStatusCode: false});
    try {
      return {status: response.status(), location: response.headers().location || '', body: await response.text()};
    } finally { await response.dispose(); }
  }

  async function openChart(label, params) {
    const chart = await context.newPage();
    await h.gotoApp(chart, base, entryPath(params));
    await waitForNavbars(chart, TIMEOUT);
    const url = new URL(chart.url());
    h.assert(url.searchParams.get('demographicNo') === String(params.demographicNo),
      `${label}'s chart entry opened a chart of another patient`);
    const banner = norm(await chart.locator('#header-top-row').innerText({timeout: TIMEOUT}));
    const own = label === 'A' ? A_FIRST_NAME : B_FIRST_NAME;
    const foreign = label === 'A' ? B_FIRST_NAME : A_FIRST_NAME;
    h.assert(banner.includes(own) && !banner.includes(foreign), `${label}'s chart does not name ${label} alone`);
    return chart;
  }

  /** Saves a note in the chart; returns the provider its re-rendered notes panel names, and the note's visit. */
  async function saveNote(label, chart, patient, suffix) {
    const editor = chart.locator('#caseManagementEntryForm textarea[name="caseNote_note"]');
    await editor.waitFor({state: 'visible', timeout: TIMEOUT});
    await editor.click();
    await editor.press('Control+End');
    await editor.pressSequentially(` ${marker}-${label}-${suffix}`, {delay: 5});
    const text = await editor.inputValue();
    await saveCurrentChartNote(chart, sql, patient, text);
    const field = await chart.locator('form[name="caseManagementViewForm"] input[name="providerNo"]').inputValue();
    const script = await chart.evaluate(() => window.providerNo);
    h.assert(field === script, `${label}'s notes panel names two different entry providers`);
    const appointment = sql.value(`SELECT appointmentNo FROM casemgmt_note WHERE demographic_no=${key(patient)}
      AND note=${q(text.trim())} ORDER BY note_id DESC LIMIT 1`);
    return {provider: field, appointment};
  }

  let chartA;
  let chartB;

  await s.step('A\'s chart, entered for A\'s own visit, and B\'s, entered from another provider\'s day sheet, each name their own entry provider and visit', async () => {
    chartA = await openChart('A', entryOf.A);
    chartB = await openChart('B', entryOf.B);
    h.assert(!chartA.isClosed() && new URL(chartA.url()).searchParams.get('demographicNo') === String(A),
      'Opening B\'s chart closed or navigated A\'s window');
    const a = await saveNote('A', chartA, A, 'entered');
    h.assert(a.provider === provider, 'A\'s notes panel does not name the logged-in provider A was entered by');
    h.assert(a.appointment === String(visit.A), 'A\'s note is not linked to A\'s own visit');
    const b = await saveNote('B', chartB, B, 'entered');
    h.assert(b.provider === other, 'B\'s notes panel does not name the provider whose day sheet B was entered from');
    h.assert(b.appointment === String(visit.B1), 'B\'s note is not linked to B\'s own visit');
  });

  await s.step('the entry of A\'s own visit, also with the old appointmentList flag, opens A\'s chart for that visit', async () => {
    for (const flag of [{}, {appointmentList: 'true'}]) {
      const entry = await entryResponse({...entryOf.A, ...flag});
      h.assert(entry.status === 302, `The entry of A's own visit${flag.appointmentList ? ' with appointmentList' : ''} answered HTTP ${entry.status}`);
      const target = new URL(entry.location, base);
      h.assert(target.pathname.endsWith('/casemgmt/ViewForward')
        && target.searchParams.get('demographicNo') === String(A)
        && target.searchParams.get('appointmentNo') === String(visit.A),
      'The entry of A\'s own visit does not open A\'s chart for that visit');
    }
  });

  await s.step('entries naming a visit that is not their patient\'s are refused and change neither patient\'s rows', async () => {
    const before = {A: readPatientState(sql, A), B: readPatientState(sql, B), visits: visitRows()};
    const refused = [
      ['A with B\'s other visit through appointmentList', {...entryOf.A, appointmentNo: visit.B2, appointmentList: 'true'}],
      ['A with B\'s visit, from the other provider\'s day sheet', {...entryOf.A, appointmentNo: visit.B1,
        providerNo: other, curProviderNo: other}],
      ['A with a visit that does not exist', {...entryOf.A, appointmentNo: nonexistent, providerNo: other}],
      ['A with a visit booked without a patient', {...entryOf.A, appointmentNo: visit.unbooked, providerNo: other}],
      ['B with A\'s visit', {...entryOf.B, appointmentNo: visit.A, providerNo: provider, curProviderNo: provider}],
    ];
    for (const [label, params] of refused) {
      const entry = await entryResponse(params);
      h.assert(entry.status === 400, `${label} answered HTTP ${entry.status}, not 400`);
      h.assert(!entry.location, `${label} redirected to a chart`);
      h.assert(!entry.body.includes(A_FIRST_NAME) && !entry.body.includes(B_FIRST_NAME),
        `${label}'s refusal names a patient`);
    }
    // The old builder's request names no patient; #4667 already refuses it before the branch.
    const legacy = await entryResponse({appointmentList: 'true', appointmentNo: visit.B1});
    h.assert(legacy.status === 400, `The old appointment switch's request answered HTTP ${legacy.status}, not 400`);
    assertPatientStateUnchanged(before.A, readPatientState(sql, A), 'The refused entries');
    assertPatientStateUnchanged(before.B, readPatientState(sql, B), 'The refused entries');
    h.assert(JSON.stringify(visitRows()) === JSON.stringify(before.visits), 'A refused entry changed a visit row');
  });

  await s.step('after the refused entries, each chart\'s next note names its own entry provider and links its own visit', async () => {
    const b = await saveNote('B', chartB, B, 'after');
    h.assert(b.provider === other,
      `B's notes panel names ${b.provider === provider ? 'the logged-in provider' : 'another provider'}, not the provider B was entered from: a refused entry overwrote B's remembered entry`);
    h.assert(b.appointment === String(visit.B1), 'B\'s note is no longer linked to B\'s own visit');
    const a = await saveNote('A', chartA, A, 'after');
    h.assert(a.provider === provider,
      'A\'s notes panel names another provider, not the one A was entered by: a refused entry replaced A\'s remembered entry');
    h.assert(a.appointment === String(visit.A), 'A\'s note is no longer linked to A\'s own visit');
    await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no IN (${patients})
      AND appointmentNo IN (${key(visit.B2)},${key(visit.unbooked)},${key(nonexistent)})`, '0',
    'A note was linked to a visit a refused entry named');
  });
}

async function workflow(s) {
  try {
    await run(s);
  } finally {
    // The charts take note locks. Release them the way closing the windows would, whatever a step
    // did; the cleanup still deletes any that remain.
    await releaseChartLocks(s.context, s.config.baseUrl).catch(() => {});
  }
}

if (require.main === module) runWorkflow('chart-entry-appointment-ownership', workflow, { openPatient: true });
module.exports = { workflow };
