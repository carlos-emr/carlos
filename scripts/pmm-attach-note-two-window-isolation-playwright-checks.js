#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The PMM client view and the chart's "attach note to client" popup keep their own patient and
 * note when two windows of one login use them (issue #4673).
 *
 * User path (a login whose role holds the PMM client-management right, _pmm_management, on a
 * facility with phone encounters enabled, which shows the note editor's Attach button):
 * E-Chart for A and for B ▸ write and Save a note in each ▸ Attach in A's window, then in B's ▸ in
 * A's popup search for client C and choose C, then the same in B's popup: C's chart receives a
 * copy of A's note and a copy of B's note, never two copies of the note of the popup opened last.
 * The popup used to read the note to attach from the session, where the second popup replaced it.
 *
 * Also pins the popup's search form (fixed here): its criteria were rendered as checkboxes,
 * which post "on" or nothing, so it could not find a client; it keeps the criteria it searched
 * with; and its Status filter, named so that the search never read it, applies. And the attach
 * itself: each copy is posted with a CSRF token (it was a link, so a GET copied the note), is a
 * new note of C's own (a new uuid and no appointment: with the source's uuid the copy became the
 * newest revision of the source note, which its chart could then no longer save), A's chart saves
 * its note again afterwards, and a GET attach copies nothing.
 *
 * Not driven: the PMM client view (PMmodule/ClientManager?id=N). Its result names a Tiles page
 * (page.pmm.client) that does not exist in CARLOS, so it cannot render; ClientManager2ActionTest
 * covers its request attributes.
 *
 * Fixtures: the workflow's FAKE patient (A) and two more FAKE patients (B, C) with the run's
 * marker as last name; a throwaway login with the test login's roles plus an owned role
 * (FAKEPW<hex>) that holds _pmm_management, and the test provider's program memberships; one visit
 * each for A and B, which their charts are entered for, so the notes written for A and B carry
 * them; the copies attached to C. Facility.enablePhoneEncounter is set
 * for the run and restored to its value. Cleanup removes every owned row by key and asserts it
 * gone.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { throwawayLoginFixture } = require('./lib/throwaway-login-fixture');
const { waitForNavbars } = require('./echart-navbar-modules-playwright-checks');

const TIMEOUT = 30000;
const NOTE = '#caseManagementEntryForm textarea[name="caseNote_note"]';
const q = h.sqlString;

function key(value, what) {
  h.assert(/^[1-9]\d*$/.test(String(value)), `The owned ${what} key is invalid`);
  return String(value);
}

/** A FAKE patient owned by this run (the marker last name); removed after its child rows. */
function ownedPatient(s, firstName) {
  const { sql, marker, provider } = s;
  const patient = key(h.insertId(sql, `INSERT INTO demographic (last_name, first_name, year_of_birth,
      month_of_birth, date_of_birth, sex, patient_status, provider_no, hc_type, province, roster_status,
      lastUpdateDate)
    VALUES (${q(marker)}, ${q(firstName)}, '1977', '05', '06', 'F', 'AC', ${q(provider)}, 'ON', 'ON', 'NR',
      NOW())`, firstName), firstName);
  s.cleanup(() => {
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient}
      AND last_name=${q(marker)}`) === '1', `The ${firstName} fixture changed owner`);
    const support = [
      ['casemgmt_note_lock', 'demographic_no'], ['casemgmt_tmpsave', 'demographic_no'],
      ['demographicExt', 'demographic_no'], ['demographicArchive', 'demographic_no'],
      ['admission', 'client_id'],
    ];
    sql.execute(support.map(([table, column]) => `DELETE FROM ${table} WHERE ${column}=${patient}`).join(';'));
    h.assert(sql.value(`SELECT ${support.map(([table, column]) => `(SELECT COUNT(*) FROM ${table} WHERE ${column}=${patient})`)
      .join(' + ')}`) === '0', `The ${firstName} fixture's locks, drafts, archive or admission rows were not removed`);
    sql.execute(`DELETE FROM demographic WHERE demographic_no=${patient} AND last_name=${q(marker)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${patient}`) === '0',
      `The ${firstName} fixture was not removed`);
  });
  return patient;
}

/** Notes, their links and the legacy eChart rows of the owned patients. */
function ownChartRows(s, patients) {
  const { sql } = s;
  const list = patients.join(',');
  s.cleanup(() => {
    const notes = sql.rows(`SELECT note_id FROM casemgmt_note WHERE demographic_no IN (${list})`)
      .map(([id]) => key(id, 'note'));
    const ids = notes.join(',');
    const children = ['casemgmt_issue_notes', 'casemgmt_note_ext', 'casemgmt_note_link'];
    if (notes.length) {
      sql.execute(`${children.map(table => `DELETE FROM ${table} WHERE note_id IN (${ids})`).join(';')};
        DELETE FROM casemgmt_note WHERE note_id IN (${ids}) AND demographic_no IN (${list})`);
    }
    sql.execute(`DELETE FROM eChart WHERE demographicNo IN (${list});
      DELETE FROM casemgmt_note_lock WHERE demographic_no IN (${list});
      DELETE FROM casemgmt_tmpsave WHERE demographic_no IN (${list})`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no IN (${list}))
      + (SELECT COUNT(*) FROM eChart WHERE demographicNo IN (${list}))
      + (SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no IN (${list}))
      + (SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no IN (${list}))${notes.length
    ? children.map(table => ` + (SELECT COUNT(*) FROM ${table} WHERE note_id IN (${ids}))`).join('') : ''}`) === '0',
    'Owned notes, their links, locks, drafts or eChart rows were not removed');
  });
}

/**
 * The role that holds the PMM client-management right, owned by this run, and the facility
 * switch that shows the note editor's Attach button. Neither is granted to any seeded role or
 * facility, so without them the attach popup and the client view are unreachable.
 */
function grantPmmManagement(s, role) {
  const { sql } = s;
  h.assert(/^FAKEPW[0-9a-f]{16}pmm$/.test(role), 'The owned role name is not this run\'s');
  s.cleanup(() => {
    sql.execute(`DELETE FROM secObjPrivilege WHERE roleUserGroup=${q(role)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${q(role)}`) === '0',
      'The owned role\'s privileges were not removed');
  });
  sql.execute(`INSERT INTO secObjPrivilege (roleUserGroup, objectName, privilege, priority, provider_no)
    VALUES (${q(role)}, '_pmm_management', 'x', 0, ${q(s.provider)})`);
  const facilities = sql.rows('SELECT id, enablePhoneEncounter FROM Facility ORDER BY id');
  h.assert(facilities.length > 0, 'The install has no facility');
  s.cleanup(() => {
    for (const [id, enabled] of facilities) {
      sql.execute(`UPDATE Facility SET enablePhoneEncounter=${Number(enabled) ? 1 : 0} WHERE id=${key(id, 'facility')}`);
    }
    h.assert(JSON.stringify(sql.rows('SELECT id, enablePhoneEncounter FROM Facility ORDER BY id'))
      === JSON.stringify(facilities), 'The facility switch was not restored');
  });
  sql.execute('UPDATE Facility SET enablePhoneEncounter=1');
}

async function settle(page, label) {
  await page.waitForLoadState('domcontentloaded', { timeout: TIMEOUT });
  await page.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
  await h.assertNotErrorPage(page, label);
}

/** Write and Save a note in an open chart; returns the saved note's id. */
async function saveNote(chart, sql, patient, text) {
  const editor = chart.locator(NOTE);
  await editor.waitFor({ state: 'visible', timeout: TIMEOUT });
  const previousId = await chart.locator('#caseManagementEntryForm [name="noteId"]').inputValue();
  await editor.click();
  await editor.press('Control+End');
  await editor.pressSequentially(` ${text}`, { delay: 5 });
  await chart.waitForFunction(() => !window.autoSaveXhr, undefined, { timeout: TIMEOUT });
  const [saved] = await Promise.all([
    chart.waitForResponse(response => response.request().method() === 'POST'
      && new URL(response.url()).pathname.endsWith('/CaseManagementEntry')
      && new URLSearchParams(response.request().postData() || '').get('method') === 'save', { timeout: TIMEOUT }),
    chart.locator('#saveImg').first().click(),
  ]);
  await saved.finished();
  h.assert(saved.status() === 200, `The note save answered HTTP ${saved.status()}`);
  // The editor names the saved revision, a new note id, once the save has been applied to the page.
  const handle = await chart.waitForFunction(previous => {
    const id = document.forms.caseManagementEntryForm?.elements.noteId?.value;
    return /^[1-9]\d*$/.test(id || '') && id !== previous && !window.autoSaveXhr && id;
  }, previousId, { timeout: TIMEOUT });
  const noteId = key(await handle.jsonValue(), 'saved note');
  await handle.dispose();
  await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_note WHERE note_id=${noteId} AND demographic_no=${patient}
    AND LOCATE(${q(text)}, note) > 0`, '1', 'The saved note is not the patient\'s own with the typed text');
  return noteId;
}

async function workflow(s) {
  const { sql, marker, provider, config, patient: A } = s;
  const B = ownedPatient(s, 'PatientBravo');
  const C = ownedPatient(s, 'PatientCharlie');
  ownChartRows(s, [A, B, C]);
  // Each source chart is entered for a visit of its own, so the note it saves carries that visit,
  // which the copy attached to C must not.
  const visitOf = {};
  for (const [label, patient] of [['A', A], ['B', B]]) {
    visitOf[label] = key(h.insertId(sql, `INSERT INTO appointment (provider_no, appointment_date, start_time,
        end_time, name, demographic_no, notes, reason, status, createdatetime, updatedatetime, creator,
        lastupdateuser)
      VALUES (${q(provider)}, CURDATE(), '23:00:00', '23:14:59', ${q(marker)}, ${patient}, ${q(marker)},
        ${q(marker)}, 't', NOW(), NOW(), ${q(provider)}, ${q(provider)})`, 'appointment'), 'appointment');
  }
  s.cleanup(() => {
    sql.execute(`DELETE FROM appointmentArchive WHERE demographic_no IN (${A},${B});
      DELETE FROM appointment WHERE demographic_no IN (${A},${B}) AND name=${q(marker)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM appointment WHERE demographic_no IN (${A},${B}))
      + (SELECT COUNT(*) FROM appointmentArchive WHERE demographic_no IN (${A},${B}))`) === '0',
    'The owned visits were not removed');
  });

  const role = `${marker.replace(/^FAKE-PW/, 'FAKEPW')}pmm`;
  const login = throwawayLoginFixture({ sql, marker, provider, testUser: config.testUser });
  s.cleanup(() => login.cleanup());
  grantPmmManagement(s, role);
  // The test login's own roles, copied as they are, plus the owned PMM role. secUserRole rows are
  // keyed by the throwaway's provider number, so the fixture's cleanup removes this one too.
  login.create();
  sql.execute(`INSERT INTO secUserRole (provider_no, role_name, activeyn, lastUpdateDate)
    VALUES (${q(login.providerNo)}, ${q(role)}, 1, NOW())`);
  // The throwaway holds the test provider's program memberships, so the owned charts open for it.
  s.cleanup(() => {
    sql.execute(`DELETE FROM provider_default_program WHERE provider_no=${q(login.providerNo)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM provider_default_program WHERE provider_no=${q(login.providerNo)}`) === '0',
      'The throwaway default program was not removed');
  });
  sql.execute(`INSERT INTO program_provider (program_id,provider_no,role_id,team_id)
    SELECT program_id,${q(login.providerNo)},role_id,team_id FROM program_provider WHERE provider_no=${q(provider)}`);
  sql.execute(`INSERT INTO provider_default_program (provider_no,program_id,signnote)
    SELECT ${q(login.providerNo)},program_id,signnote FROM provider_default_program WHERE provider_no=${q(provider)}`);

  const context = await h.newContext(s.context.browser(), config);
  context.setDefaultTimeout(TIMEOUT);
  context.on('page', page => h.wireStrictPage(page, 'pmm-login', s.recorder));
  await h.login(context, { ...config, testUser: login.username }, s.recorder);

  const state = {};
  await s.step('each chart\'s attach popup attaches its own note to the chosen client', async () => {
    const charts = {};
    const notes = {};
    for (const [label, patient] of [['A', A], ['B', B]]) {
      // The chart the day sheet's E link opens. (A login without a saved schedule navigation
      // preference opens the top menu in place, so the Master Record route is not used here.)
      const chart = await context.newPage();
      await h.gotoApp(chart, config.baseUrl, `/encounter/IncomingEncounter?demographicNo=${patient}`
        + `&providerNo=${encodeURIComponent(login.providerNo)}&curProviderNo=&appointmentNo=${visitOf[label]}`);
      await settle(chart, `${label}'s chart`);
      await waitForNavbars(chart, TIMEOUT);
      h.assert(new URL(chart.url()).searchParams.get('demographicNo') === patient,
        `${label}'s chart is not ${label}'s`);
      charts[label] = chart;
      notes[label] = await saveNote(chart, sql, patient, `${marker}-${label}-note-to-attach`);
      h.assert(sql.value(`SELECT appointmentNo FROM casemgmt_note WHERE note_id=${notes[label]}`) === visitOf[label],
        `${label}'s saved note does not carry the visit its chart was entered for`);
    }
    // Both popups open before either searches: the second popup's request is the last to name a note.
    const popups = {};
    for (const label of ['A', 'B']) {
      const chart = charts[label];
      const attach = chart.locator('#attachNoteImg');
      h.assert(await attach.count() === 1, `${label}'s note editor offers no Attach button`);
      const [popup] = await Promise.all([
        context.waitForEvent('page', { timeout: TIMEOUT }),
        attach.click(),
      ]);
      await settle(popup, `${label}'s attach popup`);
      h.assert(new URL(popup.url()).searchParams.get('noteId') === notes[label],
        `${label}'s Attach did not open the popup for ${label}'s note`);
      popups[label] = popup;
    }
    for (const label of ['A', 'B']) {
      const popup = popups[label];
      await popup.locator('input[name="criteria.lastName"]').fill(marker);
      await popup.locator('input[name="criteria.firstName"]').fill('PatientCharlie');
      // C is not admitted to a program, so finding C requires "Search all clients".
      // PMMFilter initializes the authenticated program domain before this action runs.
      await popup.locator('input[name="criteria.searchOutsideDomain"]').check();
      const search = async what => {
        // The navigation wait is armed before the click, so the results read below are the search's.
        await ui.clickAndAwaitReload(popup, popup.locator('input[type="submit"][value="search"]'),
          { label: `${label}'s ${what}`, timeout: TIMEOUT });
        await settle(popup, `${label}'s ${what}`);
      };
      if (label === 'A') {
        // The Status filter applies (it was named "active", which the search never read): C,
        // admitted to no program, is not among the admitted clients.
        await popup.locator('#attachStatus').selectOption('1');
        await search('admitted-client search');
        h.assert(await popup.locator(`a.attach-note-to-client[data-client="${C}"]`).count() === 0,
          'The attach search listed C, who is admitted to no program, among the admitted clients');
      }
      await popup.locator('#attachStatus').selectOption('');
      await search('attach search');
      h.assert(await popup.locator('#attachStatus').inputValue() === '',
        `${label}'s attach search did not keep its Status choice`);
      const links = popup.locator(`a.attach-note-to-client[data-client="${C}"]`);
      h.assert(await links.count() >= 1, `${label}'s attach search did not find client C`);
      // A standards-mode page (its messages render inside the body, not above the doctype), whose
      // result table's sort links repeat the search but not the search form's CSRF token.
      h.assert(await popup.evaluate(() => document.compatMode) === 'CSS1Compat',
        `${label}'s attach popup renders in quirks mode`);
      const sortLinks = popup.locator('a[href*="/PMmodule/ClientSearch2"]');
      h.assert(await sortLinks.count() > 0, `${label}'s attach results offer no sort links`);
      h.assert(await popup.locator('a[href*="CSRF-TOKEN"]').count() === 0,
        `${label}'s attach results put the CSRF token in a link's URL`);
      const attached = await links.first().getAttribute('data-note');
      h.assert(attached === notes[label], `${label}'s popup attaches ${attached === notes.A ? 'A\'s'
        : attached === notes.B ? 'B\'s' : 'another'} note, not ${label}'s own`);
      h.assert(await popup.locator('input[name="criteria.lastName"]').inputValue() === marker,
        `${label}'s attach search did not keep its criteria`);
      // Attaching writes the copy, so the popup posts it (with its CSRF token) rather than
      // following a link, and is redirected to C's chart.
      const [posted] = await Promise.all([
        popup.waitForRequest(r => r.method() === 'POST'
          && new URL(r.url()).pathname.endsWith('/encounter/IncomingEncounter'), { timeout: TIMEOUT }),
        popup.waitForURL(/casemgmt|CaseManagement|IncomingEncounter/, { timeout: TIMEOUT }),
        links.first().click(),
      ]);
      const sent = new URLSearchParams(posted.postData() || '');
      h.assert(sent.get('PEAttach') === 'yes' && sent.get('noteId') === notes[label] && sent.get('selectId') === C
        && sent.get('demographicNo') === C && /\S/.test(sent.get('CSRF-TOKEN') || ''),
      `${label}'s attach did not post its own note for C with a CSRF token`);
      // The popup becomes C's chart. Wait for its note editor, as the user sees it load: a chart
      // closed before its editor arrives throws from its unload handler.
      await settle(popup, `C's chart after ${label}'s attach`);
      await popup.locator(NOTE).waitFor({ state: 'visible', timeout: TIMEOUT });
    }
    for (const label of ['A', 'B']) {
      await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${C}
        AND LOCATE(${q(`${marker}-${label}-note-to-attach`)}, note) > 0`, '1',
      `Client C did not receive exactly one copy of ${label}'s note`);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no IN (${A},${B})
      AND note_id NOT IN (${notes.A},${notes.B}) AND LOCATE(${q(marker)}, note) > 0`) === '0',
    'Attaching wrote a note into A\'s or B\'s own chart');
    for (const page of Object.values(popups)) {
      if (!page.isClosed()) await page.close().catch(() => {});
    }
    state.charts = charts;
    state.notes = notes;
  });

  await s.step('a copy is a new note of the client\'s own, the source chart still saves its note, and a GET copies nothing', async () => {
    const { charts, notes } = state;
    for (const label of ['A', 'B']) {
      const copy = sql.rows(`SELECT uuid, appointmentNo FROM casemgmt_note WHERE demographic_no=${C}
        AND LOCATE(${q(`${marker}-${label}-note-to-attach`)}, note) > 0`);
      const source = sql.value(`SELECT uuid FROM casemgmt_note WHERE note_id=${notes[label]}`);
      h.assert(copy.length === 1 && copy[0][0] !== source,
        `C's copy of ${label}'s note shares the source note's uuid, so it is the newest revision of ${label}'s note`);
      h.assert(copy[0][1] === '0', `C's copy of ${label}'s note keeps ${label}'s appointment`);
    }
    // The source chart edits its note again after the attach: the copy is not a revision of it.
    // With the copy's uuid the source's, this save was refused ("Note UUID belongs to a different
    // demographic") and the note could no longer be saved or signed.
    const resaved = await saveNote(charts.A, sql, A, `${marker}-A-saved-after-attach`);
    h.assert(sql.value(`SELECT uuid FROM casemgmt_note WHERE note_id=${resaved}`)
      === sql.value(`SELECT uuid FROM casemgmt_note WHERE note_id=${notes.A}`),
    'A\'s save after the attach did not write a revision of A\'s own note');
    // The attach used to be a link: a GET (which CSRFGuard does not check) copied the note.
    const copies = () => sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${C}`);
    const before = copies();
    const response = await context.request.get(h.appUrl(config.baseUrl, `/encounter/IncomingEncounter?selectId=${C}`
      + `&demographicNo=${C}&PEAttach=yes&appointmentNo=0&noteId=${notes.A}`), { maxRedirects: 0, failOnStatusCode: false });
    try {
      h.assert(response.status() === 405, `A GET attach answered HTTP ${response.status()}, not 405`);
    } finally { await response.dispose(); }
    h.assert(copies() === before, 'A GET attach copied the note into C\'s chart');
    for (const page of Object.values(charts)) {
      if (!page.isClosed()) await page.close().catch(() => {});
    }
  });
}

if (require.main === module) runWorkflow('pmm-attach-note-two-window-isolation', workflow, { openPatient: true });

module.exports = { workflow };
