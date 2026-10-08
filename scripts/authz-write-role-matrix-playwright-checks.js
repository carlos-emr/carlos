#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Role matrix, write half: a role that may READ a chart and book appointments cannot WRITE
 * clinical, billing, demographic or provider data, and its UI does not offer what it cannot do.
 *
 * WHY. authz-read-role-matrix proves restricted roles are refused on reads. Nothing proved the
 * write side: a mutation route that checks only a read privilege (or none) would let a front-desk
 * login change a chart, and nobody would notice until a record was wrong. The probe has to be a
 * request the application really accepts, or it passes on a malformed request: so each mutation is
 * first performed by the full-privilege login through the real UI, captured, and replayed with the
 * restricted login's cookies and its OWN valid CSRF token (lib/mutation-replay.js), so CSRFGuard
 * lets it through and the action's own privilege check is what answers. The same request is then
 * sent again by the full-privilege login and must write: the refusal was about the role, not the
 * request.
 *
 * The role (lib/authz-read-fixture.js WRITE_RESTRICTED_PRIVILEGES) holds exactly `_demographic` r,
 * `_appointment` w and `_eChart` r; no install seeds it, so the fixture makes it and removes it.
 *
 * Asserted (the default mode, manifest entry authz-write-role-matrix):
 *   1. the role holds exactly those three rights, and its login signs in through the login form;
 *   2. its top bar has no Administration and no billing entry, and its day sheet shows its own
 *      appointment with no Bill link, where the full login's top bar has Administration and its day
 *      sheet a Bill link (the same page, the same kind of appointment);
 *   3. its chart offers no Rx "+" and no Allergy "+", where the full login's chart offers both; the
 *      role has no Search and its day sheet offers no M or E link, so the Master Record is opened at
 *      its address (a read the role holds) and the chart from its E-Chart link. The chart's Bill
 *      button sits in the note toolbar, which is not rendered at all for a role without note rights
 *      (ChartNotes.jsp:106-109), so this role cannot show whether Bill is hidden; the chart-bill mode
 *      below judges it;
 *   4. for tickler add, Rx Save, allergy add, demographic update, provider update, chart note save
 *      and the Ontario bill save: the full login's UI write lands; the replay with the restricted
 *      login's valid token passes h.assertRefused (the application's 403/405/securityError and an
 *      unchanged marker-keyed COUNT(*)); the same replay from the full login writes its row.
 * Families that pass come first, so a known failure cannot hide them (expectedFailure in the manifest).
 *
 * The same script backs two more manifest entries (envSet), so a failure that sits behind another
 * has a live pin of its own:
 *   AUTHZ_WRITE_ONLY=<family key>,...  runs steps 1-3 and only the named families (keys:
 *       tickler-add, rx-save, allergy-add, demographic-update, provider-update, note-save,
 *       billing-on-save); authz-write-role-matrix-billing runs billing-on-save alone.
 *   AUTHZ_WRITE_MODE=chart-bill  (authz-write-chart-bill) gives the role read-only note rights as well
 *       (`_casemgmt.notes` r), so its chart renders the note toolbar, and asserts that toolbar offers
 *       no Bill button to a role without `_billing`.
 *
 * Fixtures: the owned FAKE patient (runWorkflow), the custom role and two throwaway logins (the
 * restricted one and an er_clerk login whose provider row is the provider-update target), every
 * row each family writes (marker-keyed: `<marker>-<family>-<tag>` text, or the owned patient /
 * appointment / provider). Cleanup removes them by key and asserts they are gone. No clinic-wide
 * state is changed. Ontario only: the bill family posts the Ontario bill save.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { authzReadFixture, WRITE_RESTRICTED_PRIVILEGES } = require('./lib/authz-read-fixture');
const { signIn } = require('./lib/authz-read-probe');
const F = require('./lib/mutation-families');
const R = require('./lib/mutation-replay');

const NAME = 'authz-write-role-matrix';
const TIMEOUT = 30000;
const BILL_LINK = 'a[onclick*="/billing?billRegion"]';
const CHART_BILL = 'input[src*="dollar-sign-icon"]';

/** The chart-bill mode's role: the write-restricted role with read-only note rights added. */
const NOTE_READER_PRIVILEGES = Object.freeze({ ...WRITE_RESTRICTED_PRIVILEGES, '_casemgmt.notes': 'r' });

/** AUTHZ_WRITE_MODE: the role each mode signs in with and the manifest name the run reports under. */
const MODES = {
  matrix: { privileges: WRITE_RESTRICTED_PRIVILEGES, name: NAME },
  'chart-bill': { privileges: NOTE_READER_PRIVILEGES, name: 'authz-write-chart-bill' },
};

// Literal step labels, so a manifest expectedFailure can name the step it fails at.
const STEP_SIGN_IN = {
  matrix: 'the write-restricted role holds exactly _demographic r, _appointment w and _eChart r and signs in through the login form',
  'chart-bill': 'the note-reading role holds exactly _demographic r, _appointment w, _eChart r and _casemgmt.notes r and signs in through the login form',
};
const STEP_TOP_BAR = 'the restricted top bar has no Administration and no billing entry, and its day sheet no Bill link, where the full login has both';
const STEP_CHART = 'the restricted chart offers no Rx "+" and no Allergy "+", where the full login\'s chart offers both (its note toolbar, which holds Bill, is not rendered for a role without note rights)';
const STEP_CHART_BILL = 'with read-only note rights the restricted chart renders its note toolbar but offers no Bill button, where the full login\'s chart offers it';
const STEPS = {
  'tickler-add': 'tickler add: the restricted login\'s replay with its own valid token is refused, and the full login\'s replay writes',
  'rx-save': 'Rx Save Only: the restricted login\'s replay with its own valid token is refused, and the full login\'s replay writes',
  'allergy-add': 'allergy add: the restricted login\'s replay with its own valid token is refused, and the full login\'s replay writes',
  'demographic-update': 'demographic update: the restricted login\'s replay with its own valid token is refused, and the full login\'s replay writes',
  'provider-update': 'provider update: the restricted login\'s replay with its own valid token is refused, and the full login\'s replay writes',
  'note-save': 'chart note save: the restricted login\'s replay with its own valid token is refused, and the full login\'s replay writes',
  'billing-on-save': 'Ontario bill save: the restricted login\'s replay with its own valid token is refused, and the full login\'s replay writes',
};

/** The run's mode and family selection from the environment; anything unknown is an error, not a default. */
function selection(env = process.env) {
  const mode = env.AUTHZ_WRITE_MODE || 'matrix';
  h.assert(MODES[mode], `AUTHZ_WRITE_MODE must be one of ${Object.keys(MODES).join(', ')}; got "${mode}"`);
  const only = env.AUTHZ_WRITE_ONLY ? env.AUTHZ_WRITE_ONLY.split(',').map((key) => key.trim()) : null;
  h.assert(!only || only.every((key) => STEPS[key]), `AUTHZ_WRITE_ONLY names an unknown family; valid: ${Object.keys(STEPS).join(', ')}`);
  h.assert(!(only && mode !== 'matrix'), 'AUTHZ_WRITE_ONLY selects families of the matrix mode; it cannot be combined with AUTHZ_WRITE_MODE');
  const name = only && only.length === 1 && only[0] === 'billing-on-save' ? `${NAME}-billing` : MODES[mode].name;
  return { mode, only, name };
}

/** The chart's navigation modules have all answered (loaded or failed), as navBarLoader records it. */
async function navbarSettled(chart) {
  const handle = await chart.waitForFunction(() => {
    const state = window.carlosNavbarLoadState;
    if (!state || !state.scheduled || state.pending !== 0 || !Object.keys(state.modules).length) return null;
    return { modules: Object.keys(state.modules), failed: state.failed };
  }, undefined, { timeout: TIMEOUT });
  return handle.jsonValue();
}

/**
 * The chart's notes panel (ChartNotes.jsp, loaded after the page) has answered: either its note
 * toolbar is on the page (the Save button, #saveImg) or it rendered the securityError refusal
 * (ChartNotes.jsp:106-109 for a role without `_casemgmt.notes`). Read before the toolbar is
 * judged, so a panel that is still loading is never taken for an absent toolbar.
 */
async function notesPanelSettled(chart) {
  await chart.waitForFunction(() => !!document.querySelector('#saveImg')
    || /Security Exception/i.test(document.body ? document.body.innerText : ''), undefined, { timeout: TIMEOUT });
}

/**
 * What a chart offers: the Rx and Allergy "+" links and the boxes they sit in, the note toolbar
 * (its Save button, ChartNotes.jsp) and the Bill button that toolbar holds.
 */
async function chartOffers(chart) {
  return {
    rxPlus: await chart.locator('#menuTitleRx a').count(),
    allergyPlus: await chart.locator('#menuTitleallergies a').count(),
    rxBox: await chart.locator('#leftNavBar #Rx, #rightNavBar #Rx').count(),
    allergyBox: await chart.locator('#leftNavBar #allergies, #rightNavBar #allergies').count(),
    toolbar: await chart.locator('#saveImg').count(),
    bill: await chart.locator(CHART_BILL).count(),
  };
}

/**
 * The restricted login's chart of the owned patient. The role has no Search and its day sheet offers
 * no M or E link, so the Master Record is opened at its address (a read the role holds) and the chart
 * from its E-Chart link. The chart stays open: it is how the login holds the patient's note lock,
 * which a chart note save requires, so the note-save replay asks what a real user of the role could.
 */
async function openRestrictedChart(restricted, config, patient, { awaitToolbar = false } = {}) {
  const master = await restricted.context.newPage();
  await h.gotoApp(master, config.baseUrl, `/demographic/DemographicEdit?demographic_no=${patient}`);
  await master.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
  const echart = master.locator('a').filter({ hasText: /^\s*E-?Chart\s*$/i }).first();
  h.assert(await echart.count() === 1, 'The restricted Master Record offers no E-Chart link');
  const [chart] = await Promise.all([restricted.context.waitForEvent('page', { timeout: TIMEOUT }), echart.click()]);
  // The popup starts on about:blank and is redirected (IncomingEncounter, casemgmt/ViewForward) to the chart.
  await chart.waitForURL((url) => /\/CaseManagementEntry$/.test(new URL(String(url)).pathname), { timeout: TIMEOUT }).catch(() => {});
  h.assert(/\/CaseManagementEntry$/.test(h.pathOnly(chart.url())), `The E-Chart link opened ${h.pathOnly(chart.url()) || 'nothing'}, not the chart`);
  await chart.waitForLoadState('domcontentloaded', { timeout: TIMEOUT });
  const settled = await navbarSettled(chart);
  // A role expected to have the toolbar waits for it, so another box's refusal text cannot end the
  // wait first; a missing toolbar then fails the precondition below instead of passing silently.
  if (awaitToolbar) await chart.locator('#saveImg').first().waitFor({ state: 'attached', timeout: TIMEOUT }).catch(() => {});
  await notesPanelSettled(chart);
  const offers = await chartOffers(chart);
  h.assert(offers.rxBox > 0 && offers.allergyBox > 0,
    'The restricted chart did not render its Rx and Allergies boxes, so their "+" links could not be judged');
  return { chart, settled, offers };
}

async function openDaySheet(page, baseUrl, date) {
  const [year, month, day] = date.split('-').map(Number);
  await h.gotoApp(page, baseUrl, `/provider/providercontrol?year=${year}&month=${month}&day=${day}`
    + '&view=0&displaymode=day&dboperation=searchappointmentday&viewall=1');
  await page.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
  await page.locator('#firstMenu #navlist').waitFor({ state: 'attached', timeout: TIMEOUT });
}

/** The visible top bar entries' text, one per item. */
async function topBarItems(page) {
  return page.locator('#firstMenu #navlist li').evaluateAll((items) => items
    .map((item) => (item.innerText || '').replace(/\s+/g, ' ').trim()).filter(Boolean));
}

async function workflow(s, { mode, only, name } = selection()) {
  const { sql, marker, provider, config, patient } = s;
  const privileges = MODES[mode].privileges;
  const fixture = authzReadFixture({ sql, marker, provider, testUser: config.testUser });
  s.cleanup(() => fixture.cleanup());
  const visibilityPrefix = `${marker}-MV-`;
  s.cleanup(() => F.removeAppointments(s, visibilityPrefix));
  let restricted;
  let restrictedToken;
  let fullToken;
  let target;

  await s.step(STEP_SIGN_IN[mode], async () => {
    const role = fixture.addRole(privileges);
    h.assert(JSON.stringify(fixture.rolePrivileges(role).sort())
      === JSON.stringify(Object.entries(privileges).map(([object, right]) => `${object}:${right}`).sort()),
    'The restricted role does not hold exactly its rights');
    const login = fixture.addLogin(role);
    // Staff as a clinic sets them up: in the test login's programs. Without that the chart note save
    // fails on the missing program (a rolled-back 500) before any authorization question is asked.
    const programs = fixture.joinTestLoginPrograms(login);
    if (mode === 'matrix') target = fixture.addLogin('er_clerk');
    restricted = await signIn(s, login);
    h.assert(/\/provider\/providercontrol/.test(h.pathOnly(restricted.page.url())),
      `The restricted login landed on ${h.pathOnly(restricted.page.url())}, not the schedule`);
    restrictedToken = await R.sessionToken(restricted.page, config.baseUrl);
    fullToken = await R.sessionToken(s.schedule, config.baseUrl);
    h.assert(restrictedToken !== fullToken, 'The two logins share a CSRF token, so they are not two sessions');
    console.log(`  probe ${name}: the restricted login is in the test login's ${programs} program(s)`);
  });

  if (mode === 'chart-bill') {
    await s.step(STEP_CHART_BILL, async () => {
      const fullChart = await s.chart();
      await notesPanelSettled(fullChart);
      const full = await chartOffers(fullChart);
      h.assert(full.toolbar > 0 && full.bill > 0,
        `Control: the full login's chart does not render the note toolbar with its Bill button (${JSON.stringify(full)})`);
      const { offers } = await openRestrictedChart(restricted, config, patient, { awaitToolbar: true });
      console.log(`  probe ${name}: restricted (read-only notes) chart offers ${JSON.stringify(offers)}`);
      // Judged only where the toolbar is on the page: an absent toolbar would hide Bill for another reason.
      h.assert(offers.toolbar > 0, 'The restricted chart did not render its note toolbar, so its Bill button cannot be judged');
      h.assert(offers.rxPlus === 0, 'The restricted chart offers the Rx "+"');
      h.assert(offers.allergyPlus === 0, 'The restricted chart offers the Allergy "+"');
      h.assert(offers.bill === 0, 'The restricted chart\'s note toolbar offers the Bill button to a role without _billing');
    });
    await restricted.context.close();
    return;
  }

  await s.step(STEP_TOP_BAR, async () => {
    // No top-bar entry is gated by _billing for any login (mainMenu.jsp has no Billing item; the full
    // login's has none either), so "no Billing in the top bar" alone would pass vacuously: the
    // day-sheet Bill link, gated by _billing r (appointmentprovideradminday.jsp:454-456), is the
    // billing-visibility evidence, with the full login's own Bill link as its control.
    const date = F.futureDate(413);
    const full = F.ownedAppointment(s, { date, start: '13:00:00', text: `${visibilityPrefix}FULL` });
    const own = sql.value(`INSERT INTO appointment (provider_no, appointment_date, start_time, end_time, name, demographic_no,
      program_id, notes, reason, location, resources, type, style, billing, status, createdatetime, updatedatetime, creator, remarks, urgency)
      VALUES (${h.sqlString(restricted.login.providerNo)}, ${h.sqlString(date)}, '13:00:00', '13:14:00', ${h.sqlString(`${marker},Workflow`)},
      ${patient}, 0, ${h.sqlString(`${visibilityPrefix}OWN`)}, ${h.sqlString(`${visibilityPrefix}OWN`)}, '', '', '', '', '', 't', NOW(), NOW(),
      ${h.sqlString(provider)}, '', ''); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(own), 'The restricted login\'s appointment fixture was not created');

    await openDaySheet(s.schedule, config.baseUrl, date);
    h.assert(await s.schedule.locator('#admin-panel').count() > 0, 'Control: the full login\'s top bar has no Administration entry');
    h.assert(await s.schedule.locator(`a.apptLink[onclick*="appointment_no=${full}"]`).count() === 1,
      'Control: the full login\'s day sheet does not show its appointment');
    h.assert(await s.schedule.locator(`${BILL_LINK}[onclick*="appointment_no=${full}&"]`).count() === 1,
      'Control: the full login\'s day sheet offers no Bill link on its appointment');

    await openDaySheet(restricted.page, config.baseUrl, date);
    const items = await topBarItems(restricted.page);
    h.assert(items.some((item) => /schedule/i.test(item)), 'The restricted top bar did not render its Schedule entry');
    h.assert(await restricted.page.locator('#admin-panel, #admin2').count() === 0, 'The restricted top bar offers Administration');
    h.assert(!items.some((item) => /administration|bill/i.test(item)),
      `The restricted top bar offers an administration or billing entry: ${items.join(' | ')}`);
    h.assert(await restricted.page.locator(`a.apptLink[onclick*="appointment_no=${own}"]`).count() === 1,
      'The restricted day sheet does not show the login\'s own appointment, so the Bill link check would prove nothing');
    h.assert(await restricted.page.locator(BILL_LINK).count() === 0, 'The restricted day sheet offers a Bill link');
    console.log(`  probe ${name}: restricted top bar = ${items.join(' | ')}; day sheet shows its appointment without a Bill link`);
  });

  await s.step(STEP_CHART, async () => {
    const full = await chartOffers(await s.chart());
    h.assert(full.rxPlus > 0 && full.allergyPlus > 0,
      `Control: the full login's chart does not offer Rx "+" and Allergy "+" (${JSON.stringify(full)})`);
    const { settled, offers } = await openRestrictedChart(restricted, config, patient);
    h.assert(offers.rxPlus === 0, 'The restricted chart offers the Rx "+"');
    h.assert(offers.allergyPlus === 0, 'The restricted chart offers the Allergy "+"');
    // Bill is judged only where the note toolbar that holds it rendered. For this role it does not
    // (no _casemgmt.notes), so its absence says nothing about Bill; authz-write-chart-bill judges it.
    if (offers.toolbar > 0) h.assert(offers.bill === 0, 'The restricted chart\'s note toolbar offers the Bill button');
    console.log(`  probe ${name}: restricted chart offers ${JSON.stringify(offers)}`
      + `${settled.failed.length ? `; navbar modules that failed for this role: ${settled.failed.join(', ')}` : ''}`);
    if (offers.toolbar === 0) {
      console.log(`  NOTE ${name}: Bill not judged here: the chart renders no note toolbar for a role without note rights `
        + '(ChartNotes.jsp:106-109); authz-write-chart-bill judges it with read-only note rights');
    }
  });

  // Families that pass on 2026.08 first, the known failures last (manifest expectedFailure).
  const all = [F.ticklerAdd(s), F.rxSave(s), F.allergyAdd(s), F.demographicUpdate(s), F.providerUpdate(s, { target }),
    F.chartNoteSave(s), F.billingOnSave(s)];
  const families = only ? all.filter((family) => only.includes(family.key)) : all;
  for (const family of families) s.cleanup(() => family.cleanup());

  for (const family of families) {
    h.assert(STEPS[family.key], `${family.key} has no step label`);
    await s.step(STEPS[family.key], async () => {
      if (family.prepare) await family.prepare();
      const captured = await R.uiWrite(s, family, 'UI');
      const { response, before } = await R.replayAimed(s, family, captured,
        { tag: 'RR', context: restricted.context, token: restrictedToken, write: false });
      const refusal = await h.assertRefused(s, {
        response, table: family.table, where: family.where('RR'), before, label: `${family.label} replayed by the restricted login`,
      }).catch((error) => error);
      const written = await R.proveReplayWrites(s, family, captured,
        { tag: 'RF', context: s.context, token: fullToken, who: 'the full login' });
      if (refusal instanceof Error) throw refusal;
      console.log(`  probe ${name}: ${family.key} UI -> HTTP ${captured.status}; restricted replay -> ${refusal.evidence}, `
        + `${family.table} unchanged (${refusal.rows}); full replay -> HTTP ${written}, wrote`);
    });
  }

  if (restricted) await restricted.context.close();
}

if (require.main === module) {
  const chosen = selection();
  runWorkflow(chosen.name, (s) => workflow(s, chosen), { openPatient: true });
}
module.exports = { workflow, selection };
