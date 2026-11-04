#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * provider-preference-effects — coverage plan §2.2 (provider preferences): what two Preferences
 * settings change on the pages that read them. gap-provider-preferences-save-all stores every
 * section of the form; nothing followed these two any further.
 *
 * User paths, as a THROWAWAY login (lib/throwaway-login-fixture.js) so the shared test provider's
 * chart and schedule never change under a parallel check:
 *   Schedule ▸ Preferences ▸ Clinical Settings ▸ Stale date for case notes ▸ Save All ▸ Search ▸
 *     Master Record ▸ E-Chart (closed and opened again after each save) ▸ click the shortened note;
 *   Schedule ▸ Preferences ▸ Display & UI ▸ Schedule navigation mode ▸ Save All ▸ Tickler in the
 *     schedule's top bar (and Alt+click while Focused).
 *
 * Asserted, in this order:
 * - stale date, control: with no preference both owned notes (7 days and 3 months old) show in full.
 * - navigation mode: with no saved mode (and no legacy encounter-in-tab flag) Focused applies, as
 *   UserProperty.resolveScheduleNavigationMode says: Tickler replaces the schedule itself under the
 *   schedule's top bar with scheduleNav=1, while Alt+click still opens a plain tab. Popup stores popup
 *   plus encounter_open_in_tab=no and opens Tickler in a new window that keeps its opener and has no
 *   scheduleNav flag. Tabs stores tab plus encounter_open_in_tab=yes and opens Tickler in a new tab with
 *   scheduleNav=1 and no opener, and a second schedule window that is never reloaded follows that save
 *   through the broadcast the save page sends. Focused, saved from Tabs mode, stores focused plus
 *   encounter_open_in_tab=no; Preferences opened as a tab without an opener, so the save cannot reload
 *   the schedule, which follows the broadcast and replaces itself with Tickler again.
 * - stale date, last because it fails today: "1 months" stores cme_note_date=-1, and the chart then
 *   shortens the old note to one line (full=false, at most 50 characters, its tail missing) while the
 *   recent one stays in full; clicking the shortened note loads its whole text. "All" stores A, and
 *   both notes show in full again. The eChart loads its notes through CaseManagementView
 *   method=viewNotesOpt, which never passes cme_note_date to ChartNotesAjax.jsp, so the chart always
 *   applies the one-year default and the "1 months" step fails while that defect stands.
 *
 * Fixtures: the throwaway login with the test provider's program rows (so the chart opens for it), and
 * two signed FAKE notes on the owned patient. Cleanup removes the notes and the eChart row, then the
 * default-program row, then the throwaway (its property and program_provider rows go with it).
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { revealAuditLink } = require('./lib/playwright-link-audit');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');
const { openChart, waitForNavbars } = require('./echart-navbar-modules-playwright-checks');
const { submitAndClose } = require('./gap-provider-preferences-save-all-playwright-checks');
const { consumeUnloadBeacons } = require('./echart-note-draft-recovery-playwright-checks');
const { failureMark } = require('./lib/concurrency-support');

const TIMEOUT = 20000;
const TICKLER = /\/tickler\/ViewTicklerMain/;

async function workflow(s) {
  const { sql, config, recorder, marker, patient, provider } = s;
  const fixture = throwawayLoginFixture({ sql, marker, provider, testUser: config.testUser });
  s.cleanup(() => fixture.cleanup());
  fixture.create();
  const owner = h.sqlString(fixture.providerNo);

  // The throwaway holds the same program memberships as the test provider, so the chart opens for it.
  // program_provider goes with the throwaway's cleanup; the default-program row does not.
  s.cleanup(() => {
    sql.execute(`DELETE FROM provider_default_program WHERE provider_no=${owner}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM provider_default_program WHERE provider_no=${owner}`) === '0',
      'The throwaway default program was not removed');
  });
  sql.execute(`INSERT INTO program_provider (program_id,provider_no,role_id,team_id)
    SELECT program_id,${owner},role_id,team_id FROM program_provider WHERE provider_no=${h.sqlString(provider)}`);
  sql.execute(`INSERT INTO provider_default_program (provider_no,program_id,signnote)
    SELECT ${owner},program_id,signnote FROM provider_default_program WHERE provider_no=${h.sqlString(provider)}`);
  const program = sql.value(`SELECT id FROM program WHERE name='OSCAR' ORDER BY id LIMIT 1`);
  const role = sql.value(`SELECT role_id FROM program_provider WHERE provider_no=${h.sqlString(provider)} AND program_id=${program || 0} LIMIT 1`);
  h.assert(program && role, 'The default OSCAR program or the test provider\'s role in it is missing');

  // Two notes longer than the 50 characters a shortened note keeps, each with a tail of its own.
  const notes = [];
  s.cleanup(() => {
    if (notes.length) {
      sql.execute(`DELETE FROM casemgmt_note_link WHERE note_id IN (${notes.join(',')});
        DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes.join(',')});
        DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes.join(',')});
        DELETE FROM casemgmt_note WHERE demographic_no=${patient} AND note_id IN (${notes.join(',')})`);
    }
    sql.execute(`DELETE FROM eChart WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}`) === '0', 'Owned notes were not removed');
  });
  const addNote = (daysAgo, text) => {
    const id = sql.value(`INSERT INTO casemgmt_note (update_date,observation_date,demographic_no,provider_no,note,signed,signing_provider_no,
      encounter_type,program_no,reporter_caisi_role,history,uuid,locked,archived) VALUES (NOW(),NOW() - INTERVAL ${daysAgo} DAY,${patient},
      ${owner},${h.sqlString(text)},1,${owner},'',${h.sqlString(program)},${h.sqlString(role)},'x',UUID(),'0',0);
      SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), 'A note fixture was not created');
    notes.push(id);
    return id;
  };
  const recentTail = `recent tail ${marker}`;
  const oldTail = `old tail ${marker}`;
  const recent = addNote(7, `FAKE recent visit note, written to run well past fifty characters. ${recentTail}`);
  const old = addNote(92, `FAKE older visit note, written to run well past fifty characters. ${oldTail}`);

  const context = await h.newContext(s.context.browser(), config);
  context.setDefaultTimeout(TIMEOUT);
  context.on('page', page => h.wireStrictPage(page, 'throwaway', recorder));
  const schedule = await h.login(context, { ...config, testUser: fixture.username }, recorder, { label: 'throwaway-login' });
  const scheduleUrl = schedule.url();
  const property = name => sql.value(`SELECT COALESCE(MAX(value),'') FROM property WHERE provider_no=${owner} AND name=${h.sqlString(name)}`);
  const openPrefs = () => ui.clickOpensPopup(schedule, schedule.getByTitle(/Edit your personal setting/i).first(),
    { context, recorder, label: 'preferences', timeout: TIMEOUT });

  /**
   * Save one select on the Preferences page; Save All closes the window. The save page reloads the
   * schedule through window.opener, which exists only when Preferences opened as a popup window: in
   * Tabs mode popupTab() severs the opener, and the schedule learns of a new mode only by broadcast.
   */
  async function savePreference(name, value) {
    const prefs = await openPrefs();
    const reloadsSchedule = await prefs.evaluate(() => window.opener !== null);
    const select = prefs.locator(`form[name="UPDATEPRE"] select[name="${name}"]`);
    await revealAuditLink(prefs, select, TIMEOUT);
    await select.selectOption(value);
    await schedule.evaluate(() => { window.__beforePreferenceSave = true; });
    await submitAndClose(recorder, prefs, prefs.locator('.footer-bar button[type="submit"]'));
    if (reloadsSchedule) {
      await schedule.waitForFunction(() => !window.__beforePreferenceSave && document.readyState === 'complete', null, { timeout: TIMEOUT });
    }
  }

  // ---- stale date for case notes: the control, before any Save All stores a value ----
  const { masterPage: master } = await openMasterRecord(context, schedule, recorder,
    { searchTerm: marker, preferredDemographicNo: patient, timeout: TIMEOUT });
  let chart = await openChart(context, master, recorder, TIMEOUT);
  await waitForNavbars(chart, TIMEOUT);
  /** Read how the chart shows one note: in full or not, and its text. */
  async function shown(noteId) {
    await chart.locator(`#txt${noteId}`).waitFor({ state: 'attached', timeout: TIMEOUT });
    return {
      full: await chart.locator(`#full${noteId}`).inputValue(),
      text: (await chart.locator(`#txt${noteId}`).innerText()).replace(/\s+/g, ' ').trim(),
    };
  }
  /**
   * Close the chart and open it again from the master record, as a user does to see a changed setting.
   * Closing sends the note-lock release beacon from pagehide, which Chromium reports as an aborted ping
   * once the document is gone; the lock row going away proves the beacon arrived. (A reload would send
   * the same beacon, but from a document whose URL does not change, so nothing could prove it abandoned.)
   */
  async function reopenChart() {
    const mark = failureMark(recorder);
    const closed = chart.waitForEvent('close', { timeout: TIMEOUT });
    await chart.close({ runBeforeUnload: true });
    await closed;
    await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${patient} AND provider_no=${owner}`, '0',
      'Closing the chart left its note lock behind');
    consumeUnloadBeacons(recorder, mark);
    chart = await openChart(context, master, recorder, TIMEOUT);
    await waitForNavbars(chart, TIMEOUT);
  }

  await s.step('with no stale-date preference both notes show in full', async () => {
    h.assert(property('cme_note_date') === '', 'The throwaway started with a stale-date preference');
    const [a, b] = [await shown(recent), await shown(old)];
    h.assert(a.full === 'true' && a.text.includes(recentTail), 'The recent note is not shown in full');
    h.assert(b.full === 'true' && b.text.includes(oldTail), 'The 3-month-old note is not shown in full under the 1-year default');
  });

  // ---- schedule navigation mode ----
  /** Click Tickler in a schedule's top bar; report whether a new window opened or the schedule itself moved. */
  async function openTickler(page, { alt = false } = {}) {
    const link = page.locator('a:has(#oscar_new_tickler)').first();
    await link.waitFor({ state: 'visible', timeout: TIMEOUT });
    // Only one of the two happens; the other waiter times out later and is ignored.
    const opened = context.waitForEvent('page', { timeout: TIMEOUT }).then(win => ({ win }));
    const moved = page.waitForURL(TICKLER, { timeout: TIMEOUT, waitUntil: 'domcontentloaded' }).then(() => ({ same: true }));
    opened.catch(() => {});
    moved.catch(() => {});
    await link.click({ modifiers: alt ? ['Alt'] : [] });
    const outcome = await Promise.race([opened, moved]);
    if (outcome.same) return { same: true, url: new URL(page.url()) };
    const { win } = outcome;
    await win.waitForURL(TICKLER, { timeout: TIMEOUT, waitUntil: 'domcontentloaded' });
    const result = { same: false, url: new URL(win.url()), hasOpener: await win.evaluate(() => window.opener !== null) };
    await win.close();
    return result;
  }
  const flag = url => url.searchParams.get('scheduleNav');
  /**
   * A window that is not reloaded learns a new mode only from the save page's broadcast, which may land
   * after the save returns. Wait for it (bounded) before clicking; the click's outcome is still the assertion.
   */
  const modeReaches = (page, mode) => page.waitForFunction(m => window.scheduleNavigationMode === m, mode, { timeout: TIMEOUT }).catch(() => {});

  await s.step('with no saved mode Focused applies: Tickler replaces the schedule under its top bar with scheduleNav=1', async () => {
    h.assert(property('schedule_navigation_mode') === '' && property('encounter_open_in_tab') === '',
      'The throwaway started with a saved navigation mode or encounter-in-tab flag');
    const result = await openTickler(schedule);
    h.assert(result.same && flag(result.url) === '1', `With no saved mode Tickler did not replace the schedule with scheduleNav=1 (same=${result.same})`);
    h.assert(await schedule.locator('#navlist').count() > 0, 'Tickler opened without the schedule top bar');
    await schedule.goto(scheduleUrl, { waitUntil: 'domcontentloaded' });
  });

  await s.step('Alt+click while Focused still opens Tickler in a plain tab without the schedule shell', async () => {
    const result = await openTickler(schedule, { alt: true });
    h.assert(!result.same && !result.hasOpener && flag(result.url) === null,
      `Alt+click opened Tickler as same=${result.same}, opener=${result.hasOpener}, scheduleNav=${flag(result.url)}`);
  });

  await s.step('Popup is stored with encounter_open_in_tab=no and opens Tickler in a new window that keeps its opener and has no scheduleNav flag', async () => {
    await savePreference('schedule_navigation_mode', 'popup');
    h.assert(property('schedule_navigation_mode') === 'popup' && property('encounter_open_in_tab') === 'no',
      'Popup mode was not stored, or it left the encounter-in-tab flag on');
    const result = await openTickler(schedule);
    h.assert(!result.same && result.hasOpener && flag(result.url) === null,
      `Popup mode opened Tickler as same=${result.same}, opener=${result.hasOpener}, scheduleNav=${flag(result.url)}`);
  });

  // A second schedule window, never reloaded: only the save page's broadcast can tell it about the change.
  const second = await context.newPage();
  await second.goto(scheduleUrl, { waitUntil: 'domcontentloaded' });
  await second.locator('a:has(#oscar_new_tickler)').first().waitFor({ state: 'visible', timeout: TIMEOUT });
  await second.evaluate(() => { window.__notReloaded = true; });

  await s.step('Tabs is stored with encounter_open_in_tab=yes and opens Tickler in a new tab with scheduleNav=1 and no opener', async () => {
    await savePreference('schedule_navigation_mode', 'tab');
    h.assert(property('schedule_navigation_mode') === 'tab' && property('encounter_open_in_tab') === 'yes',
      'Tabs mode was not stored with the legacy encounter-in-tab flag');
    const result = await openTickler(schedule);
    h.assert(!result.same && !result.hasOpener && flag(result.url) === '1',
      `Tabs mode opened Tickler as same=${result.same}, opener=${result.hasOpener}, scheduleNav=${flag(result.url)}`);
  });

  await s.step('the second schedule window, not reloaded, follows the Tabs save through the broadcast', async () => {
    h.assert(await second.evaluate(() => window.__notReloaded === true), 'The second schedule window was reloaded');
    await modeReaches(second, 'tab');
    const result = await openTickler(second);
    h.assert(!result.same && !result.hasOpener && flag(result.url) === '1',
      `The unreloaded window still opens Tickler as same=${result.same}, opener=${result.hasOpener}, scheduleNav=${flag(result.url)}`);
    await second.close();
  });

  await s.step('Focused, saved from Tabs mode, is stored with encounter_open_in_tab=no and the schedule, told by broadcast, replaces itself with Tickler', async () => {
    // Preferences opened as a tab without an opener, so nothing reloads the schedule: the mode reaches it by broadcast.
    await savePreference('schedule_navigation_mode', 'focused');
    h.assert(property('schedule_navigation_mode') === 'focused' && property('encounter_open_in_tab') === 'no',
      'Focused mode was not stored, or it left the encounter-in-tab flag on');
    await modeReaches(schedule, 'focused');
    const result = await openTickler(schedule);
    h.assert(result.same && flag(result.url) === '1', `Focused mode did not move the schedule to Tickler with scheduleNav=1 (same=${result.same})`);
    h.assert(await schedule.locator('#navlist').count() > 0, 'Tickler opened without the schedule top bar');
    await schedule.goto(scheduleUrl, { waitUntil: 'domcontentloaded' });
  });

  // ---- stale date for case notes: what the setting changes on the chart ----
  await s.step('"1 months" is stored and the chart shortens the old note to one line, keeping the recent one in full', async () => {
    await savePreference('cme_note_date', '-1');
    await expectValue(sql, `SELECT COALESCE(MAX(value),'') FROM property WHERE provider_no=${owner} AND name='cme_note_date'`, '-1',
      'The stale date was not stored as -1 (one month)');
    await reopenChart();
    const [a, b] = [await shown(recent), await shown(old)];
    h.assert(a.full === 'true' && a.text.includes(recentTail), 'The recent note stopped showing in full');
    h.assert(b.full === 'false' && !b.text.includes(oldTail) && b.text.length <= 50,
      `The old note is not shortened (full=${b.full}, ${b.text.length} characters)`);
  });

  await s.step('clicking the shortened note loads its whole text', async () => {
    // The expand listener sits on the note's #n box; click its text so no inner link takes the click.
    await chart.locator(`#txt${old}`).click();
    await chart.waitForFunction(id => document.getElementById(`full${id}`)?.value === 'true', old, { timeout: TIMEOUT });
    h.assert((await shown(old)).text.includes(oldTail), 'The expanded note does not show its whole text');
  });

  await s.step('"All" is stored and both notes show in full again', async () => {
    await savePreference('cme_note_date', 'A');
    await expectValue(sql, `SELECT COALESCE(MAX(value),'') FROM property WHERE provider_no=${owner} AND name='cme_note_date'`, 'A',
      'The stale date was not stored as A (all)');
    await reopenChart();
    const [a, b] = [await shown(recent), await shown(old)];
    h.assert(a.full === 'true' && b.full === 'true' && b.text.includes(oldTail), 'Not every note shows in full under "All"');
  });
}

if (require.main === module) runWorkflow('provider-preference-effects', workflow, { openPatient: true, openMaster: false });
module.exports = { workflow };
