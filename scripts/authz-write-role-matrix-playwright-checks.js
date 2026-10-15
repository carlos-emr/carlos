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
 * The same script backs three more manifest entries (envSet), so a failure that sits behind another
 * has a live pin of its own:
 *   AUTHZ_WRITE_ONLY=<family key>,...  runs steps 1-3 and only the named families (keys:
 *       tickler-add, rx-save, allergy-add, demographic-update, provider-update, note-save,
 *       billing-on-save); authz-write-role-matrix-billing runs billing-on-save alone.
 *   AUTHZ_WRITE_MODE=chart-bill  (authz-write-chart-bill) gives the role read-only note rights as well
 *       (`_casemgmt.notes` r), so its chart renders the note toolbar, and asserts that toolbar offers
 *       no Bill button to a role without `_billing`.
 *   AUTHZ_WRITE_MODE=issue-change  (authz-write-issue-change) keeps the matrix role and asks the one write
 *       the families above do not reach: may a role with chart read and no note or issue right change the
 *       STATE of a chart issue? CaseManagementEntry2Action.execute gates the whole action on `_demographic`
 *       r (:154) and issueChange (:2737) checks only that a role is in the session (:2744), then saves the
 *       acute / certain / major / resolved flags it is sent (:2790). The full login resolves an owned,
 *       SQL-seeded issue through the note editor's checklist (revealed by a script: nothing reveals it,
 *       finding 227), the captured request is replayed by the full login (it writes) and then by the
 *       restricted login, from its own chart so the session's form bean exists, with its own valid token.
 *       The last step holds only the refusal assertion (h.assertRefused: the application's 403/405/
 *       securityError and the issue row still in its seeded state); a write that lands fails it
 *       (finding 229). Everything before it, including the restricted login's POST, is a separate step.
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
/** The issue-change mode's fixture: catalog issue "Safety" (the one echart-issues-filter seeds), filed under the chart's own program. */
const ISSUE_CODE = 'CTCMM1000';
const CHART_PROGRAM = 10034;
/** The seeded row's update_date, in the past: issueChange always stamps now, so any save moves it and the row is no longer "unchanged". */
const ISSUE_SEEDED_AT = '2020-01-01 00:00:00';

/** The chart-bill mode's role: the write-restricted role with read-only note rights added. */
const NOTE_READER_PRIVILEGES = Object.freeze({ ...WRITE_RESTRICTED_PRIVILEGES, '_casemgmt.notes': 'r' });

/** AUTHZ_WRITE_MODE: the role each mode signs in with and the manifest name the run reports under. */
const MODES = {
  matrix: { privileges: WRITE_RESTRICTED_PRIVILEGES, name: NAME },
  'chart-bill': { privileges: NOTE_READER_PRIVILEGES, name: 'authz-write-chart-bill' },
  'issue-change': { privileges: WRITE_RESTRICTED_PRIVILEGES, name: 'authz-write-issue-change' },
};

// Literal step labels, so a manifest expectedFailure can name the step it fails at.
const STEP_SIGN_IN = {
  matrix: 'the write-restricted role holds exactly _demographic r, _appointment w and _eChart r and signs in through the login form',
  'chart-bill': 'the note-reading role holds exactly _demographic r, _appointment w, _eChart r and _casemgmt.notes r and signs in through the login form',
  'issue-change': 'the write-restricted role holds exactly _demographic r, _appointment w and _eChart r and signs in through the login form',
};
const STEP_TOP_BAR = 'the restricted top bar has no Administration and no billing entry, and its day sheet no Bill link, where the full login has both';
const STEP_CHART = 'the restricted chart offers no Rx "+" and no Allergy "+", where the full login\'s chart offers both (its note toolbar, which holds Bill, is not rendered for a role without note rights)';
// The chart-bill mode runs two steps: a control (the note toolbar renders, the full login's chart offers Bill, the
// Rx and Allergy "+" are still absent) and, LAST, the pinned one (finding 200), which holds only the Bill button.
const STEP_CHART_TOOLBAR = 'with read-only note rights the restricted chart renders its note toolbar and no Rx "+" or Allergy "+", where the full login\'s chart offers its Bill button';
const STEP_CHART_BILL = 'the restricted chart\'s note toolbar offers no Bill button to a role without _billing';
// The issue-change mode runs four steps: the fixture, a control (the full login's request writes), the restricted login's
// POST and, LAST, the pinned one (finding 229), which holds only the refusal assertion.
const STEP_ISSUE_SEED = '(fixture) the patient has one unresolved chart issue, seeded with SQL because no clinician path assigns one (findings 186 and 225)';
const STEP_ISSUE_CONTROL = '(control) the full login resolves the issue from the note editor\'s checklist (revealed by a script, finding 227) and the same request replayed by the full login writes the issue row';
const STEP_ISSUE_SEND = '(probe) the write-restricted login opens the chart and sends the same issueChange with its own valid token';
const STEP_ISSUE_CHANGE = 'chart issue change: the restricted login\'s issueChange with its own valid token is refused and the issue row is unchanged';
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

/**
 * Capture the chart's next CaseManagementEntry POST whose `method` field is `method`. The chart posts several
 * CaseManagementEntry methods (lock refreshes, note saves), so the URL alone would match the wrong one.
 */
function captureChartPost(page, method, act) {
  const watcher = {
    waitForRequest: (predicate, options) => page.waitForRequest(
      (request) => predicate(request) && new URLSearchParams(request.postData() || '').get('method') === method, options),
  };
  return R.captureRequest(watcher, (url) => /\/CaseManagementEntry$/.test(url.pathname), act, { timeout: TIMEOUT });
}

/**
 * The issue-change mode (finding 229): may a role with chart read and no note or issue right change an issue's state?
 * The fixture is one owned casemgmt_issue row in a known state; the question is whether it is still in that state
 * after the restricted login's issueChange. The step that asks it is the last and holds only h.assertRefused.
 */
async function issueChange(s, { restricted, restrictedToken, fullToken, name, catalog }) {
  const { sql, patient, config } = s;
  const q = h.sqlString;
  const origin = config.baseUrl.origin;
  let issueRow;
  // The seeded state, as a COUNT(*) key for h.assertRefused: 1 while the row is as seeded, 0 once any save has touched it.
  const unchanged = () => `id=${issueRow} AND demographic_no=${patient} AND acute=0 AND certain=0 AND major=0 AND resolved=0`
    + ` AND update_date=${q(ISSUE_SEEDED_AT)}`;
  const reseed = () => {
    sql.execute(`UPDATE casemgmt_issue SET acute=0, certain=0, major=0, resolved=0, update_date=${q(ISSUE_SEEDED_AT)}
      WHERE id=${issueRow} AND demographic_no=${patient}`);
    h.assert(R.count(sql, 'casemgmt_issue', unchanged()) === 1, 'The issue fixture could not be put back in its seeded state');
  };
  s.cleanup(() => {
    sql.execute(`DELETE FROM casemgmt_issue_notes WHERE id IN (SELECT id FROM casemgmt_issue WHERE demographic_no=${patient});
      DELETE FROM casemgmt_issue WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_issue WHERE demographic_no=${patient}`) === '0', 'Owned chart issue rows were not removed');
  });

  await s.step(STEP_ISSUE_SEED, async () => {
    issueRow = sql.value(`INSERT INTO casemgmt_issue (demographic_no, issue_id, acute, certain, major, resolved, program_id, type, update_date)
      VALUES (${patient}, ${catalog.id}, 0, 0, 0, 0, ${CHART_PROGRAM}, ${q(catalog.role)}, ${q(ISSUE_SEEDED_AT)}); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(issueRow), 'The casemgmt_issue fixture was not created');
    h.assert(R.count(sql, 'casemgmt_issue', unchanged()) === 1, 'The seeded issue is not readable in its seeded state');
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_issue WHERE demographic_no=${patient}`) === '1', 'The patient has more than the one seeded issue');
  });

  // What the full login's UI really sent, and the one change the replays add to it (a second flag, so the probe
  // sets resolved and one of acute / certain / major, as the finding is about any of the four).
  let captured;
  let overrides;
  const replay = (token) => R.buildReplay(captured, { origin, token, overrides });

  await s.step(STEP_ISSUE_CONTROL, async () => {
    const chart = await s.chart();
    await chart.locator('textarea[name="caseNote_note"]').first().waitFor({ state: 'visible', timeout: TIMEOUT });
    // A script reveals what no control reveals (finding 227); it proves the request, not a clinician path.
    await chart.evaluate(() => { document.getElementById('noteIssues-unresolved').style.display = 'block'; });
    await chart.locator('#noteIssues-unresolved a[onclick^="return displayIssue"]').click({ timeout: TIMEOUT });
    const radio = chart.locator('#noteIssues-unresolved input[type="radio"][name$=".issue.resolved"][value="true"]');
    await radio.waitFor({ state: 'visible', timeout: TIMEOUT });
    captured = await captureChartPost(chart, 'issueChange', () => radio.check({ timeout: TIMEOUT }));
    h.assert(captured.status === 200, `Choosing Resolved in the full login's chart answered HTTP ${captured.status}`);
    await R.expectCount(sql, 'casemgmt_issue', `id=${issueRow} AND resolved=1`, 1, 'The full login\'s own issueChange did not resolve the issue');

    const field = [...captured.body.keys()].map((key) => /^issueCheckList\[(\d+)\]\.issue\.resolved$/.exec(key)).find(Boolean);
    h.assert(field, 'The captured issueChange carries no issueCheckList[N].issue.resolved field');
    overrides = { [`issueCheckList[${field[1]}].issue.major`]: 'true' };

    // Positive control: the request the probe sends, from the login that may write, writes both flags.
    reseed();
    const response = await R.sendReplay(s.context, replay(fullToken));
    h.assert(!h.isWafPage(response.status(), await response.text().catch(() => '')), 'The full login\'s replay was refused by the WAF front door, so it proves nothing about the request');
    await R.expectCount(sql, 'casemgmt_issue', `id=${issueRow} AND resolved=1 AND major=1`, 1,
      `The same issueChange sent by the full login (HTTP ${response.status()}) did not write, so the replay is not a request the application accepts`);
    reseed();
  });

  let sent;
  await s.step(STEP_ISSUE_SEND, async () => {
    // Opened after the reseed: the session's form bean is built from the rows as they are when the chart opens.
    const { offers } = await openRestrictedChart(restricted, config, patient);
    h.assert(offers.toolbar === 0, 'The restricted chart rendered a note toolbar, so the role has note rights and this is not the question asked');
    // edit() files the lock (isNoteEdited, CaseManagementEntry2Action:553) just before it stores the session's form bean (:566).
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${patient} AND provider_no=${q(restricted.login.providerNo)}`) !== '0',
      'Opening the restricted chart filed no note lock, so its session may hold no form bean for issueChange to read');
    const before = R.count(sql, 'casemgmt_issue', unchanged());
    h.assert(before === 1, 'The issue is not in its seeded state before the restricted login\'s request');
    const response = await R.sendReplay(restricted.context, replay(restrictedToken));
    const body = await response.text().catch(() => '');
    h.assert(!h.isWafPage(response.status(), body), 'The restricted login\'s request was refused by the WAF front door, so it says nothing about the route');
    sent = { response, before };
    const where = h.pathOnly(response.headers().location || '');
    // Recorded for the run log only; the verdict is the next step's.
    const [flags] = sql.rows(`SELECT acute, certain, major, resolved FROM casemgmt_issue WHERE id=${issueRow}`);
    console.log(`  probe ${name}: restricted issueChange (resolved + major) -> HTTP ${response.status()}${where ? ` to ${where}` : ''}; `
      + `issue flags now acute=${flags[0]} certain=${flags[1]} major=${flags[2]} resolved=${flags[3]} (seeded 0 0 0 0)`);
  });

  // Pinned (finding 229): holds only the assertion the defect breaks.
  await s.step(STEP_ISSUE_CHANGE, async () => {
    const refusal = await h.assertRefused(s, {
      response: sent.response, table: 'casemgmt_issue', where: unchanged(), before: sent.before,
      label: 'The issueChange sent by the restricted login',
    });
    console.log(`  probe ${name}: ${refusal.evidence}, casemgmt_issue unchanged (${refusal.rows})`);
  });
}

async function workflow(s, { mode, only, name } = selection()) {
  const { sql, marker, provider, config, patient } = s;
  const privileges = MODES[mode].privileges;
  let catalog;
  if (mode === 'issue-change') {
    // Judged before any login is made, so a catalog without the issue skips with its reason and leaves nothing behind.
    const found = sql.rows(`SELECT issue_id, role FROM issue WHERE code=${h.sqlString(ISSUE_CODE)}`);
    if (found.length !== 1) throw new h.SkipCheck(`The issue catalog has no single row with code ${ISSUE_CODE} to seed`);
    catalog = { id: found[0][0], role: found[0][1] };
    h.assert(/^[1-9]\d*$/.test(catalog.id), 'The catalog issue id is not a number');
  }
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

  if (mode === 'issue-change') {
    await issueChange(s, { restricted, restrictedToken, fullToken, name, catalog });
    await restricted.context.close();
    return;
  }

  if (mode === 'chart-bill') {
    // Everything the Bill judgement depends on is established here, so a precondition that fails (no toolbar to judge,
    // a full login that does not offer Bill) reads failed-elsewhere and only the defect reads known-fail.
    let restrictedOffers;
    await s.step(STEP_CHART_TOOLBAR, async () => {
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
      restrictedOffers = offers;
    });
    // Pinned (finding 200): holds only the assertion the defect breaks.
    await s.step(STEP_CHART_BILL, async () => {
      h.assert(restrictedOffers.bill === 0, 'The restricted chart\'s note toolbar offers the Bill button to a role without _billing');
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
