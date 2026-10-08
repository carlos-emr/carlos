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
 * Asserted:
 *   1. the role holds exactly those three rights, and its login signs in through the login form;
 *   2. its top bar has no Administration and no billing entry, and its day sheet shows its own
 *      appointment with no Bill link, where the full login's top bar has Administration and its day
 *      sheet a Bill link (the same page, the same kind of appointment);
 *   3. its chart offers no Rx "+", no Allergy "+" and no Bill button, where the full login's chart
 *      offers all three; the role has no Search and its day sheet offers no M or E link, so the
 *      Master Record is opened at its address (a read the role holds) and the chart from its
 *      E-Chart link;
 *   4. for tickler add, Rx Save, allergy add, demographic update, provider update, chart note save
 *      and the Ontario bill save: the full login's UI write lands; the replay with the restricted
 *      login's valid token passes h.assertRefused (the application's 403/405/securityError and an
 *      unchanged marker-keyed COUNT(*)); the same replay from the full login writes its row.
 * Families that pass come first, so a known failure cannot hide them (expectedFailure in the manifest);
 * AUTHZ_WRITE_ONLY=<family key>,... runs only the named families, to see a step that sits behind a
 * known failure (keys: tickler-add, rx-save, allergy-add, demographic-update, provider-update,
 * note-save, billing-on-save).
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

// One literal label per family, so the manifest's expectedFailure can name the step it fails at.
const STEPS = {
  'tickler-add': 'tickler add: the restricted login\'s replay with its own valid token is refused, and the full login\'s replay writes',
  'rx-save': 'Rx Save Only: the restricted login\'s replay with its own valid token is refused, and the full login\'s replay writes',
  'allergy-add': 'allergy add: the restricted login\'s replay with its own valid token is refused, and the full login\'s replay writes',
  'demographic-update': 'demographic update: the restricted login\'s replay with its own valid token is refused, and the full login\'s replay writes',
  'provider-update': 'provider update: the restricted login\'s replay with its own valid token is refused, and the full login\'s replay writes',
  'note-save': 'chart note save: the restricted login\'s replay with its own valid token is refused, and the full login\'s replay writes',
  'billing-on-save': 'Ontario bill save: the restricted login\'s replay with its own valid token is refused, and the full login\'s replay writes',
};

/** The chart's navigation modules have all answered (loaded or failed), as navBarLoader records it. */
async function navbarSettled(chart) {
  const handle = await chart.waitForFunction(() => {
    const state = window.carlosNavbarLoadState;
    if (!state || !state.scheduled || state.pending !== 0 || !Object.keys(state.modules).length) return null;
    return { modules: Object.keys(state.modules), failed: state.failed };
  }, undefined, { timeout: TIMEOUT });
  return handle.jsonValue();
}

/** What a chart offers: the Rx and Allergy "+" links, the Bill button, and which module boxes rendered. */
async function chartOffers(chart) {
  return {
    rxPlus: await chart.locator('#menuTitleRx a').count(),
    allergyPlus: await chart.locator('#menuTitleallergies a').count(),
    bill: await chart.locator(CHART_BILL).count(),
    rxBox: await chart.locator('#leftNavBar #Rx, #rightNavBar #Rx').count(),
    allergyBox: await chart.locator('#leftNavBar #allergies, #rightNavBar #allergies').count(),
  };
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

async function workflow(s) {
  const { sql, marker, provider, config, patient } = s;
  const fixture = authzReadFixture({ sql, marker, provider, testUser: config.testUser });
  s.cleanup(() => fixture.cleanup());
  const visibilityPrefix = `${marker}-MV-`;
  s.cleanup(() => F.removeAppointments(s, visibilityPrefix));
  let restricted;
  let restrictedToken;
  let fullToken;
  let target;

  await s.step('the write-restricted role holds exactly _demographic r, _appointment w and _eChart r and signs in through the login form', async () => {
    const role = fixture.addRole(WRITE_RESTRICTED_PRIVILEGES);
    h.assert(JSON.stringify(fixture.rolePrivileges(role).sort())
      === JSON.stringify(Object.entries(WRITE_RESTRICTED_PRIVILEGES).map(([object, right]) => `${object}:${right}`).sort()),
    'The restricted role does not hold exactly its three rights');
    const login = fixture.addLogin(role);
    // Staff as a clinic sets them up: in the test login's programs. Without that the chart note save
    // fails on the missing program (a rolled-back 500) before any authorization question is asked.
    const programs = fixture.joinTestLoginPrograms(login);
    target = fixture.addLogin('er_clerk');
    restricted = await signIn(s, login);
    h.assert(/\/provider\/providercontrol/.test(h.pathOnly(restricted.page.url())),
      `The restricted login landed on ${h.pathOnly(restricted.page.url())}, not the schedule`);
    restrictedToken = await R.sessionToken(restricted.page, config.baseUrl);
    fullToken = await R.sessionToken(s.schedule, config.baseUrl);
    h.assert(restrictedToken !== fullToken, 'The two logins share a CSRF token, so they are not two sessions');
    console.log(`  probe ${NAME}: the restricted login is in the test login's ${programs} program(s)`);
  });

  await s.step('the restricted top bar has no Administration and no billing entry, and its day sheet no Bill link, where the full login has both', async () => {
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
    console.log(`  probe ${NAME}: restricted top bar = ${items.join(' | ')}; day sheet shows its appointment without a Bill link`);
  });

  await s.step('the restricted chart offers no Rx "+", no Allergy "+" and no Bill button, where the full login\'s chart offers all three', async () => {
    const fullChart = await s.chart();
    const fullOffers = await chartOffers(fullChart);
    h.assert(fullOffers.rxPlus > 0 && fullOffers.allergyPlus > 0 && fullOffers.bill > 0,
      `Control: the full login's chart does not offer Rx "+", Allergy "+" and Bill (${JSON.stringify(fullOffers)})`);

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
    const offers = await chartOffers(chart);
    h.assert(offers.rxBox > 0 && offers.allergyBox > 0,
      'The restricted chart did not render its Rx and Allergies boxes, so their "+" links could not be judged');
    h.assert(offers.rxPlus === 0, 'The restricted chart offers the Rx "+"');
    h.assert(offers.allergyPlus === 0, 'The restricted chart offers the Allergy "+"');
    h.assert(offers.bill === 0, 'The restricted chart offers the Bill button');
    console.log(`  probe ${NAME}: restricted chart offers ${JSON.stringify(offers)}`
      + `${settled.failed.length ? `; navbar modules that failed for this role: ${settled.failed.join(', ')}` : ''}`);
    // The chart stays open: it is how this login holds the patient's note lock, which a chart note
    // save requires, so the note-save replay below asks the question a real user of the role could.
  });

  // Families that pass on 2026.08 first, the known failures last (manifest expectedFailure).
  // AUTHZ_WRITE_ONLY=billing-on-save,... narrows a debugging run to those families (a step behind a
  // known failure is otherwise not reached); a misspelt key fails rather than selecting nothing.
  const all = [F.ticklerAdd(s), F.rxSave(s), F.allergyAdd(s), F.demographicUpdate(s), F.providerUpdate(s, { target }),
    F.chartNoteSave(s), F.billingOnSave(s)];
  const only = process.env.AUTHZ_WRITE_ONLY ? process.env.AUTHZ_WRITE_ONLY.split(',').map((key) => key.trim()) : null;
  h.assert(!only || only.every((key) => all.some((family) => family.key === key)),
    `AUTHZ_WRITE_ONLY names an unknown family; valid: ${all.map((family) => family.key).join(', ')}`);
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
      console.log(`  probe ${NAME}: ${family.key} UI -> HTTP ${captured.status}; restricted replay -> ${refusal.evidence}, `
        + `${family.table} unchanged (${refusal.rows}); full replay -> HTTP ${written}, wrote`);
    });
  }

  if (restricted) await restricted.context.close();
}

if (require.main === module) runWorkflow(NAME, workflow, { openPatient: true });
module.exports = { workflow };
