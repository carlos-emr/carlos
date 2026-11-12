#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The legacy encounter editor saves, signs and prints only the patient its own window was
 * opened for (issue #4673).
 *
 * The legacy editor (encounter/Index2.jsp) renders only with the CAISI module (caisi=on in
 * carlos.properties) and only for an IncomingEncounter request carrying casetoEncounter. Nothing in
 * the application links there; once open, the editor re-renders itself after each save through its
 * own casetoEncounter field. Its save (EctSaveEncounter2Action, /encounter/SaveEncounter2) used to
 * take the patient, the entry metadata and the revision the editor was opened from out of the
 * login-wide EctSessionBean and the session's eChartID, which IncomingEncounter rewrites for
 * whichever chart was entered last in any window. With A's and B's editors open in one login,
 * saving A's editor appended A's text to B's chart and signed B's appointment, and Print printed B.
 * The save action itself is reachable in every configuration; echart-two-patient-isolation drives
 * it directly. This check drives the editor a clinician actually sees.
 *
 * OPERATOR PREREQUISITE: caisi=on in /etc/carlos-emr/carlos.properties, then `carlos-ctl restart`
 * (restore the property and restart afterwards). Without it the editor does not render and the
 * check reports SKIP. The CAISI module changes clinic-wide behaviour, so this is a manual check.
 *
 * User path: two windows of one login, each opened from the day sheet with
 * IncomingEncounter?demographicNo=...&providerNo=...&casetoEncounter=true; A's window also names A's
 * appointment, as an appointment's encounter link does. Sign & Save closes its window and refreshes
 * the day sheet that opened it.
 *
 * Steps: each editor shows its own patient; each Save, made after the other editor was opened last,
 * writes a revision of its own patient only and redisplays its own patient; Print prints the window's
 * own revision; an editor whose revision another A editor has superseded is refused with its own text
 * on the concurrency page and writes nothing; Sign & Save signs only A's own appointment; an editor
 * keeps its own visit through a Save after A was entered again from another visit, so its Sign signs
 * that visit; Verify & Sign verifies only A's own appointment; Split Chart (offered once the
 * encounter fills half of what the editor holds) stores A's whole encounter as A's split revision and
 * continues A's chart from its end in the next one; Exit closes the editor and saves neither chart;
 * on a BC install, Sign, Save & Bill opens a bill of A's own visit; a double-clicked Save posts the
 * editor once, writes one revision and shows the editor again. Every step compares the other
 * patient's complete clinical rows before and after. On release/2026.08 the editor posted to an
 * address that does not exist (it saved nothing); with that fixed but the old save action, each Save
 * writes the other chart, Verify & Sign and Split Chart fail, and on a BC install Sign, Save & Bill
 * writes the other chart and opens no bill. Exit writes nothing with either action.
 *
 * Fixtures: the workflow's FAKE patient (A, first name LegacyAlpha), a second FAKE patient (B,
 * LegacyBravo, last name the run marker), one owned appointment each and three more of A's (four on
 * a BC install). Cleanup removes both patients' eChart revisions, appointments, appointment archive
 * rows, note locks and drafts by key and asserts each is gone, and restores the provider's
 * encounterWindow (editor layout) row exactly.
 */
const fs = require('node:fs');
const h = require('./lib/playwright-harness');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { readPatientState, assertPatientStateUnchanged } = require('./echart-two-patient-isolation-playwright-checks');
const { checkLegacyMessageHandoff } = require('./lib/legacy-message-handoff-checks');

const TIMEOUT = 20000;
const OBSERVED = '2026-02-03';
const A_FIRST_NAME = 'LegacyAlpha';
const B_FIRST_NAME = 'LegacyBravo';
const namesA = text => text.includes(A_FIRST_NAME);
const namesB = text => text.includes(B_FIRST_NAME);
const norm = text => text.replace(/\s+/g, ' ').trim();

function patientKey(value) {
  h.assert(/^[1-9]\d*$/.test(String(value)), 'An owned patient key is invalid');
  return String(value);
}

/** Observe one native Exit without weakening unrelated browser diagnostics. */
function observeLegacyExit(page, recorder, { base, patient, chartContext, timeout = TIMEOUT }) {
  const target = new URL(h.appUrl(base, '/encounter/SaveEncounter2'));
  const guard = new URL(h.appUrl(base, '/csrfguard'));
  const frame = page.mainFrame();
  const candidates = [];
  const failuresBefore = new WeakMap();
  let exitRequest;
  let exitCount = 0;
  let acknowledged = false;
  let closed = false;
  let ambiguity = false;
  const onRequest = request => {
    let issuingFrame;
    try { issuingFrame = request.frame(); } catch { return; }
    if (issuingFrame !== frame) return;
    if (request.url() === target.href && request.method() === 'POST' && request.isNavigationRequest()) {
      const body = new URLSearchParams(request.postData() || '');
      if (body.getAll('btnPressed').length === 1 && body.get('btnPressed') === 'Exit'
          && body.getAll('demographicNo').length === 1 && body.get('demographicNo') === String(patient)
          && body.getAll('chartContext').length === 1 && body.get('chartContext') === chartContext) {
        exitRequest = request;
        exitCount++;
      }
      return;
    }
    if (exitRequest && request.url() === guard.href && request.method() === 'GET'
        && request.resourceType() === 'script' && frame.url() === target.href
        && request.headers().referer === target.href) {
      let resolve;
      const terminal = new Promise(done => { resolve = done; });
      candidates.push({ request, terminal, resolve, state: null, entry: null });
    }
  };
  // prepend establishes the exact count immediately before the common listener;
  // the later observer may claim only the entry appended for this same event.
  const beforeFailure = request => failuresBefore.set(request, recorder.requestFailures.length);
  const onFailure = request => {
    const candidate = candidates.find(value => value.request === request);
    if (!candidate) return;
    const index = failuresBefore.get(request);
    const entry = recorder.requestFailures.at(-1);
    const error = request.failure()?.errorText;
    if (candidate.state || index === undefined || recorder.requestFailures.length !== index + 1
        || !entry || entry.url !== request.url() || entry.resourceType !== 'script' || entry.errorText !== error) {
      ambiguity = true;
    } else candidate.entry = entry;
    candidate.state = 'failed';
    candidate.error = error;
    candidate.resolve();
  };
  const onFinished = request => {
    const candidate = candidates.find(value => value.request === request);
    if (candidate) { if (candidate.state) ambiguity = true; candidate.state = 'finished'; candidate.resolve(); }
  };
  const onClosed = () => { closed = true; };
  page.on('request', onRequest);
  page.prependListener('requestfailed', beforeFailure);
  page.on('requestfailed', onFailure);
  page.on('requestfinished', onFinished);
  page.on('close', onClosed);
  return {
    acknowledge(response) {
      h.assert(exitCount === 1 && response.request() === exitRequest && response.url() === target.href
        && response.status() === 200 && Object.hasOwn(response.headers(), h.APPLICATION_HEADER),
      'Exit did not acknowledge the exact owned native form POST');
      acknowledged = true;
    },
    async settle() {
      h.assert(acknowledged && closed && page.isClosed(), 'Exit has no acknowledged native closing phase');
      let timer;
      try {
        await Promise.race([Promise.all(candidates.map(value => value.terminal)), new Promise((_, reject) => {
          timer = setTimeout(() => reject(new Error('The owned Exit script has no terminal browser event')), timeout);
        })]);
      } finally { clearTimeout(timer); }
      h.assert(!ambiguity && candidates.length <= 1, 'Exit script failure ownership is ambiguous or duplicated');
      const candidate = candidates[0];
      if (!candidate || candidate.state === 'finished') return;
      h.assert(candidate.error === 'net::ERR_ABORTED' && candidate.entry
        && recorder.requestFailures.filter(entry => entry === candidate.entry).length === 1,
      'The owned Exit script failure was not one uniquely recorded navigation abort');
      const artifact = fs.mkdtempSync('/tmp/carlos-legacy-exit-') + '/acknowledgment.json';
      fs.writeFileSync(artifact, JSON.stringify({ patient: String(patient), nativeExitStatus: 200,
        nativeExitAcknowledged: true, editorClosed: true, exactRequestAndEntry: true,
        rawFailure: { ...candidate.entry } }, null, 2) + '\n', { mode: 0o600 });
      recorder.requestFailures.splice(recorder.requestFailures.indexOf(candidate.entry), 1);
      return artifact;
    },
    dispose() {
      page.off('request', onRequest);
      page.off('requestfailed', beforeFailure);
      page.off('requestfailed', onFailure);
      page.off('requestfinished', onFinished);
      page.off('close', onClosed);
    },
  };
}

async function run(s) {
  const { sql, patient: A, provider, marker, context, recorder, schedule } = s;
  const q = h.sqlString;
  const base = s.config.baseUrl;

  // ---- Fixtures --------------------------------------------------------------------------------
  sql.execute(`UPDATE demographic SET first_name=${q(A_FIRST_NAME)} WHERE demographic_no=${A} AND last_name=${q(marker)}`);
  const B = h.insertId(sql, `INSERT INTO demographic (last_name, first_name, year_of_birth, month_of_birth,
    date_of_birth, sex, patient_status, provider_no, hc_type, province, roster_status, lastUpdateDate)
    VALUES (${q(marker)}, ${q(B_FIRST_NAME)}, '1975', '03', '04', 'M', 'AC', ${q(provider)}, 'ON', 'ON', 'NR', NOW())`,
  'patient B');
  const patients = `${A},${B}`;
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
    sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note WHERE demographic_no IN (${patients});
      DELETE FROM casemgmt_note_lock WHERE demographic_no IN (${patients});
      DELETE FROM casemgmt_tmpsave WHERE demographic_no IN (${patients});
      DELETE FROM eChart WHERE demographicNo IN (${patients});
      DELETE FROM appointmentArchive WHERE demographic_no IN (${patients});
      DELETE FROM appointment WHERE demographic_no IN (${patients})`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no IN (${patients}))
      + (SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no IN (${patients}))
      + (SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no IN (${patients}))
      + (SELECT COUNT(*) FROM eChart WHERE demographicNo IN (${patients}))
      + (SELECT COUNT(*) FROM appointmentArchive WHERE demographic_no IN (${patients}))
      + (SELECT COUNT(*) FROM appointment WHERE demographic_no IN (${patients}))`) === '0',
    'Owned legacy encounter rows were not removed');
  });
  // A legacy save stores the editor's row sizes for its provider; restore the provider's exact row.
  const layoutQuery = `SELECT provider_no,rowOneSize,rowTwoSize,presBoxSize,rowThreeSize FROM encounterWindow
    WHERE provider_no=${q(provider)}`;
  const layoutBefore = sql.rows(layoutQuery);
  s.cleanup(() => {
    sql.execute(`DELETE FROM encounterWindow WHERE provider_no=${q(provider)}`
      + layoutBefore.map(row => `; INSERT INTO encounterWindow (provider_no,rowOneSize,rowTwoSize,presBoxSize,rowThreeSize)
        VALUES (${q(row[0])},${row.slice(1).map(Number).join(',')})`).join(''));
    h.assert(JSON.stringify(sql.rows(layoutQuery)) === JSON.stringify(layoutBefore),
      'The provider\'s legacy encounter layout was not restored');
  });
  /** One owned To Do visit of a patient; the cleanup above removes every visit of A and B. */
  const addVisit = patient => h.insertId(sql, `INSERT INTO appointment (provider_no, appointment_date, start_time,
    end_time, name, demographic_no, notes, reason, status, createdatetime, updatedatetime, creator, lastupdateuser)
    VALUES (${q(provider)}, ${q(OBSERVED)}, '23:00:00', '23:14:59', ${q(marker)}, ${patient}, ${q(marker)},
    ${q(marker)}, 't', NOW(), NOW(), ${q(provider)}, ${q(provider)})`, 'appointment');
  const appointmentOf = {A: addVisit(A), B: addVisit(B)};
  const visitRow = appointment => JSON.stringify(sql.rows(`SELECT demographic_no,status,lastupdateuser FROM appointment
    WHERE appointment_no=${patientKey(appointment)}`));

  const snapshot = patient => readPatientState(sql, patient);
  const revisionsOf = patient => sql.rows(`SELECT eChartId FROM eChart WHERE demographicNo=${patientKey(patient)}
    ORDER BY eChartId`).map(row => row[0]);
  const entryQuery = (patient, appointment) => new URLSearchParams({
    demographicNo: patient, providerNo: provider, curProviderNo: provider, casetoEncounter: 'true', status: 't',
    appointmentDate: OBSERVED, ...(appointment ? {appointmentNo: appointment} : {}),
  });

  // The editor renders only in CAISI mode; elsewhere Index2.jsp forwards to the case-management chart.
  const probe = await context.request.get(h.appUrl(base, `/encounter/IncomingEncounter?${entryQuery(A)}`),
    {maxRedirects: 0, failOnStatusCode: false});
  const probeStatus = probe.status();
  let editorRenders;
  let forwardedToChart;
  try {
    editorRenders = probeStatus === 200 && /<form name="encForm"/.test(await probe.text());
    forwardedToChart = probeStatus === 302
      && /\/casemgmt\/ViewForward\b/.test(probe.headers().location || '');
  } finally { await probe.dispose(); }
  if (forwardedToChart) {
    throw new h.SkipCheck('the legacy encounter editor renders only with caisi=on in carlos.properties '
      + '(then carlos-ctl restart); this deployment forwards it to the case-management chart');
  }
  // Anything else (an error, another page) is the editor failing, not a configuration to skip.
  h.assert(editorRenders, `The legacy encounter editor answered HTTP ${probeStatus} without its form`);

  /**
   * The editor's left column loads its modules (preventions, ticklers, ...) by XHR after the page
   * loads, and a Save that navigates first aborts them. Track one page's module loads so that every
   * step acts on an editor that has finished loading, as the clinician's does.
   */
  function trackEditorModules(page) {
    const pending = new Set();
    let finished = 0;
    let waiter = null;
    const isModule = request => request.resourceType() === 'xhr'
      && /\/encounter\/display[A-Za-z]+$/.test(new URL(request.url()).pathname);
    const release = () => {
      if (waiter && pending.size === 0 && finished >= waiter.expected) waiter.resolve();
    };
    page.on('framenavigated', frame => { if (frame === page.mainFrame()) finished = 0; });
    page.on('request', request => { if (isModule(request)) pending.add(request); });
    const settle = request => {
      if (pending.delete(request)) {
        finished++;
        release();
      }
    };
    page.on('requestfinished', settle);
    page.on('requestfailed', settle);
    return async label => {
      // A navigation is committed before the new editor's scripts have run; wait for its config.
      await page.waitForFunction(() => typeof encounterConfig === 'object', undefined, {timeout: TIMEOUT})
        .catch(() => {});
      const expected = await page.evaluate(() => (typeof encounterConfig === 'object'
        ? Object.keys(encounterConfig.urls.leftNavbar).length : 0));
      h.assert(expected > 0, `${label} is not the legacy editor: ${h.pathOnly(page.url())} "${norm(await page.title())}"`);
      let timer;
      try {
        await new Promise((resolve, reject) => {
          timer = setTimeout(() => reject(new Error(`${label}: the editor's left column never finished loading`)), TIMEOUT);
          waiter = {expected, resolve};
          release();
        });
      } finally {
        clearTimeout(timer);
        waiter = null;
      }
    };
  }
  const modulesLoaded = new WeakMap();
  const editorLoaded = (page, label) => modulesLoaded.get(page)(label);

  /**
   * Open a patient's legacy editor in a window of its own, opened from the day sheet as the day
   * sheet opens a chart entry: Sign & Save closes that window and refreshes its opener.
   */
  async function openEditor(patient, appointment) {
    const url = h.appUrl(base, `/encounter/IncomingEncounter?${entryQuery(patient, appointment)}`);
    // Tracked from the context's page event, which fires before the window's first request.
    const opened = context.waitForEvent('page', {timeout: TIMEOUT}).then(page => {
      modulesLoaded.set(page, trackEditorModules(page));
      return page;
    });
    await schedule.evaluate(target => { window.open(target, '_blank', 'width=1000,height=800'); }, url);
    const page = await opened;
    await page.locator('form[name="encForm"] textarea[name="enTextarea"]').waitFor({state: 'visible', timeout: TIMEOUT});
    await editorLoaded(page, 'An opened editor');
    return page;
  }
  async function editorOwner(page) {
    const text = norm(await page.locator('body').innerText());
    // Read only when present, so an editor that does not carry its revision fails the step's own
    // assertion instead of a locator timeout.
    const revision = page.locator('form[name="encForm"] input[name="eChartId"]');
    return {
      patient: await page.locator('form[name="encForm"] input[name="demographicNo"]').inputValue(),
      revision: await revision.count() === 1 ? await revision.inputValue() : null,
      chartContext: await page.locator('form[name="encForm"] input[name="chartContext"]').inputValue(),
      namesA: namesA(text), namesB: namesB(text),
    };
  }
  const assertOwnEditor = (owner, patient, label) => {
    h.assert(owner.chartContext, `${label} has no bound chart program context`);
    h.assert(owner.patient === String(patient)
      && (String(patient) === String(A) ? owner.namesA && !owner.namesB : owner.namesB && !owner.namesA),
    `${label} is not headed with its own patient alone`);
  };
  /** Click one of the editor's submit buttons and wait for the save's response document. */
  async function press(page, button, label, {redisplaysEditor = true} = {}) {
    await clickAndAwaitReload(page, page.locator(`form[name="encForm"] input[type="button"][value="${button}"]`), {label});
    if (redisplaysEditor) await editorLoaded(page, `${label}'s redisplayed editor`);
  }

  // ---- Steps -----------------------------------------------------------------------------------
  let editorA;
  let editorB;
  const signSaveBill = 'Sign, Save & Bill';
  let offersBilling = false;
  await s.step('A\'s and B\'s legacy editors, opened in two windows of one login, each show their own patient', async () => {
    editorA = await openEditor(A, appointmentOf.A);
    assertOwnEditor(await editorOwner(editorA), A, 'A\'s editor');
    editorB = await openEditor(B);
    assertOwnEditor(await editorOwner(editorB), B, 'B\'s editor');
    assertOwnEditor(await editorOwner(editorA), A, 'A\'s editor, after B\'s was opened');
    // A BC install's editor also offers Sign, Save & Bill (billregion=BC).
    offersBilling = await editorA.locator(`form[name="encForm"] input[type="button"][value="${signSaveBill}"]`).count() > 0;
  });

  const directions = [
    {label: 'A', patient: A, other: B, editor: () => editorA},
    {label: 'B', patient: B, other: A, editor: () => editorB},
  ];
  const saved = {};
  for (const d of directions) {
    await s.step(`${d.label}'s Save, after the other editor was opened last, writes only ${d.label}'s revision`, async () => {
      // Entering the other chart afresh makes it the login's most recently opened chart.
      await openEditor(d.other);
      const before = snapshot(d.other);
      const editor = d.editor();
      const text = `${marker}-${d.label}-legacy-editor`;
      const originalOwner = await editorOwner(editor);
      const base = originalOwner.revision;
      await editor.locator('textarea[name="enTextarea"]').fill(text);
      await press(editor, 'Save', `${d.label} legacy Save`, {redisplaysEditor: false});
      // The rows first, so that a Save which wrote the other chart says so rather than only failing
      // to redisplay.
      assertPatientStateUnchanged(before, snapshot(d.other), `${d.label}'s legacy Save`);
      const revision = revisionsOf(d.patient).at(-1);
      h.assert(revision && revision !== base
        && sql.value(`SELECT encounter FROM eChart WHERE eChartId=${revision}`) === text
        && sql.value(`SELECT providerNo FROM eChart WHERE eChartId=${revision}`) === provider,
      `${d.label}'s Save did not write a revision of ${d.label}`);
      await editorLoaded(editor, `${d.label}'s editor after its Save`);
      const owner = await editorOwner(editor);
      assertOwnEditor(owner, d.patient, `${d.label}'s editor after its Save`);
      h.assert(owner.chartContext === originalOwner.chartContext, `${d.label}'s Save changed its chart program context`);
      h.assert(owner.revision === revision
        && norm(await editor.locator('textarea[name="enTextarea"]').inputValue()) === text,
      `${d.label}'s redisplayed editor does not show the revision it saved`);
      saved[d.label] = {revision, text, chartContext: owner.chartContext};
    });
  }

  await s.step('A\'s Print prints A\'s own revision after B\'s editor was opened last', async () => {
    await openEditor(B);
    const popup = await s.popup(editorA, editorA.locator('input[type="button"][value="Print"]'), 'A legacy print');
    await popup.locator('pre[name="enTextarea"]').waitFor({state: 'attached', timeout: TIMEOUT});
    h.assert(new URL(popup.url()).searchParams.get('demographicNo') === String(A)
      && new URL(popup.url()).searchParams.get('echartid') === saved.A.revision, 'Print opened another chart or revision');
    h.assert(new URL(popup.url()).searchParams.get('chartContext') === saved.A.chartContext,
      'Print lost the legacy editor\'s chart program context');
    const text = norm(await popup.locator('body').innerText());
    h.assert(namesA(text) && !namesB(text) && text.includes(saved.A.text) && !text.includes(saved.B.text),
      'A\'s Print did not print A\'s revision alone');
    await popup.close();
  });

  await s.step('an A editor whose revision was superseded keeps its own text on the concurrency page and writes nothing', async () => {
    const second = await openEditor(A);
    h.assert((await editorOwner(second)).revision === saved.A.revision, 'A\'s second editor did not open A\'s latest revision');
    const newer = `${marker}-A-legacy-newer`;
    await second.locator('textarea[name="enTextarea"]').fill(newer);
    await press(second, 'Save', 'A second legacy Save');
    const latest = revisionsOf(A).at(-1);
    h.assert(sql.value(`SELECT encounter FROM eChart WHERE eChartId=${latest}`) === newer, 'A\'s second editor did not save');
    await openEditor(B);
    const before = {A: snapshot(A), B: snapshot(B)};
    const stale = `${marker}-A-legacy-stale`;
    await editorA.locator('textarea[name="enTextarea"]').fill(stale);
    const mark = recorder.badResponses.length;
    const consoleMark = recorder.consoleIssues.length;
    await press(editorA, 'Save', 'A stale legacy Save', {redisplaysEditor: false});
    const refused = recorder.badResponses.slice(mark);
    h.assert(refused.length === 1 && refused[0].status === 409 && new URL(refused[0].url).pathname.endsWith('/encounter/SaveEncounter2'),
      'The stale Save was not refused as a conflict');
    // The refusal is this step's expected outcome, not a page failure: excuse its response and
    // Chromium's console line for that same document load, and nothing else.
    recorder.badResponses.splice(mark, 1);
    for (let i = recorder.consoleIssues.length - 1; i >= consoleMark; i--) {
      const entry = recorder.consoleIssues[i];
      if (entry.type === 'error' && /status of 409\b/.test(entry.text) && entry.location && entry.location.url
          && new URL(entry.location.url).pathname.endsWith('/encounter/SaveEncounter2')) {
        recorder.consoleIssues.splice(i, 1);
      }
    }
    const recovered = await editorA.locator('textarea[name="encounterTextarea"]').inputValue();
    const page = norm(await editorA.locator('body').innerText());
    h.assert(norm(recovered) === stale && !namesA(page) && !namesB(page),
      'The concurrency page did not keep the refused editor\'s own text alone');
    assertPatientStateUnchanged(before.A, snapshot(A), 'A\'s stale Save (A\'s rows)');
    assertPatientStateUnchanged(before.B, snapshot(B), 'A\'s stale Save');
    await second.close();
  });

  /**
   * Sign & Save: the closing page closes the editor's window and, as a visit changed, refreshes the
   * day sheet that opened it.
   */
  async function signAndSave(editor, button = 'Sign & Save') {
    const chartContext = (await editorOwner(editor)).chartContext;
    const closed = editor.waitForEvent('close', {timeout: TIMEOUT});
    closed.catch(() => {});
    const refreshed = schedule.waitForEvent('framenavigated', {predicate: frame => frame === schedule.mainFrame(),
      timeout: TIMEOUT}).then(() => true, () => false);
    const response = editor.waitForResponse(r => r.request().method() === 'POST'
      && new URL(r.url()).pathname.endsWith('/encounter/SaveEncounter2'), {timeout: TIMEOUT});
    await editor.locator(`form[name="encForm"] input[type="button"][value="${button}"]`).click();
    const saved = await response;
    h.assert(saved.status() === 200, `${button} did not answer its closing page`);
    h.assert(new URLSearchParams(saved.request().postData() || '').get('chartContext') === chartContext,
      `${button} lost the legacy editor's chart program context`);
    await closed;
    h.assert(await refreshed, `${button} did not refresh the day sheet that opened the editor`);
    await schedule.waitForLoadState('load', {timeout: TIMEOUT});
  }
  const signedBy = patient => JSON.stringify([[String(patient), 'tS', provider]]);
  const untouched = patient => JSON.stringify([[String(patient), 't', provider]]);

  await s.step('A\'s Sign & Save signs only A\'s own appointment', async () => {
    const editor = await openEditor(A, appointmentOf.A);
    await openEditor(B, appointmentOf.B);
    const before = snapshot(B);
    const text = `${marker}-A-legacy-signed`;
    await editor.locator('textarea[name="enTextarea"]').fill(text);
    await signAndSave(editor);
    h.assert(visitRow(appointmentOf.A) === signedBy(A), 'Sign & Save did not sign A\'s own appointment');
    h.assert(visitRow(appointmentOf.B) === untouched(B), 'Sign & Save changed B\'s appointment');
    const revision = revisionsOf(A).at(-1);
    h.assert(sql.value(`SELECT encounter FROM eChart WHERE eChartId=${revision}`).startsWith(`${text}\n[`),
      'Sign & Save did not write A\'s signed revision');
    assertPatientStateUnchanged(before, snapshot(B), 'A\'s Sign & Save');
  });

  await s.step('A\'s editor keeps its own visit through a Save after A was entered again from another visit', async () => {
    // A's remembered entry is the latest of A's windows; the second names another of A's visits.
    const own = addVisit(A);
    const other = addVisit(A);
    const editor = await openEditor(A, own);
    await openEditor(A, other);
    const before = snapshot(B);
    await editor.locator('textarea[name="enTextarea"]').fill(`${marker}-A-legacy-own-visit`);
    await press(editor, 'Save', 'A own-visit Save');
    h.assert(await editor.locator('form[name="encForm"] input[name="appointment_no"]').inputValue() === String(own),
      'A\'s redisplayed editor names another visit than the one it was opened from');
    await signAndSave(editor);
    h.assert(visitRow(own) === signedBy(A), 'Sign & Save after a Save did not sign the editor\'s own visit');
    h.assert(visitRow(other) === untouched(A), 'Sign & Save after a Save signed A\'s other visit');
    assertPatientStateUnchanged(before, snapshot(B), 'A\'s own-visit Sign & Save');
  });

  await s.step('A\'s Verify & Sign, after B\'s editor was opened last, verifies only A\'s own appointment', async () => {
    const visit = addVisit(A);
    const editor = await openEditor(A, visit);
    const other = await openEditor(B, appointmentOf.B);
    const before = snapshot(B);
    const text = `${marker}-A-legacy-verified`;
    await editor.locator('textarea[name="enTextarea"]').fill(text);
    await signAndSave(editor, 'Verify & Sign');
    h.assert(visitRow(visit) === JSON.stringify([[String(A), 'tV', provider]]),
      'Verify & Sign did not verify A\'s own appointment');
    h.assert(visitRow(appointmentOf.B) === untouched(B), 'Verify & Sign changed B\'s appointment');
    const revision = revisionsOf(A).at(-1);
    h.assert(/^Verified and Signed on /.test((sql.value(`SELECT encounter FROM eChart WHERE eChartId=${revision}`) || '')
      .slice(text.length + 2)) && sql.value(`SELECT encounter FROM eChart WHERE eChartId=${revision}`).startsWith(`${text}\n[`),
    'Verify & Sign did not write A\'s verified revision');
    assertPatientStateUnchanged(before, snapshot(B), 'A\'s Verify & Sign');
    await other.close();
  });

  await s.step('A\'s Split Chart, after B\'s editor was opened last, splits only A\'s chart', async () => {
    // Split Chart is offered once the encounter text fills half of what the editor holds (16 KiB).
    const writer = await openEditor(A);
    const long = Array.from({length: 360}, (_, n) => `${marker}-A-split line ${String(n).padStart(3, '0')} FAKE-PW text`)
      .join('\n');
    await writer.locator('textarea[name="enTextarea"]').fill(long);
    await press(writer, 'Save', 'A long legacy Save');
    await writer.close();
    const editor = await openEditor(A);
    const split = editor.locator('form[name="encForm"] input[type="submit"][value="Split Chart"]');
    h.assert(await split.count() === 1, 'A\'s editor with a long encounter does not offer Split Chart');
    const other = await openEditor(B);
    const before = snapshot(B);
    const revisionsBefore = revisionsOf(A);
    // Split Chart asks first; its page then saves the split chart's new revision by itself.
    const dialogs = await h.withExpectedDialogs(editor, async () => {
      const resaved = editor.waitForResponse(r => r.request().method() === 'POST'
        && new URL(r.url()).pathname.endsWith('/encounter/SaveEncounter2')
        && new URLSearchParams(r.request().postData() || '').get('btnPressed') === 'Save', {timeout: TIMEOUT});
      await split.click();
      h.assert((await resaved).status() === 200, 'The split chart\'s own Save did not redisplay the editor');
    });
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Split Chart did not ask for confirmation once');
    await editor.waitForLoadState('load', {timeout: TIMEOUT});
    await editorLoaded(editor, 'A\'s editor after Split Chart');
    assertPatientStateUnchanged(before, snapshot(B), 'A\'s Split Chart');
    const written = revisionsOf(A).slice(revisionsBefore.length);
    h.assert(written.length === 2, `Split Chart wrote ${written.length} revisions of A instead of the split and its successor`);
    // A form post sends a textarea's line breaks as CRLF.
    const stored = id => (sql.value(`SELECT encounter FROM eChart WHERE eChartId=${id}`) || '').replace(/\r\n/g, '\n');
    const subject = sql.value(`SELECT subject FROM eChart WHERE eChartId=${written[0]}`);
    h.assert(subject === 'SPLIT CHART' && stored(written[0]).startsWith(long),
      `Split Chart did not store A's full encounter as A's split revision (subject "${subject}")`);
    const successor = stored(written[1]);
    h.assert(successor.includes('$$SPLIT CHART$$') && successor.length < long.length && successor.includes(`${marker}-A-split line 359`),
      'The split chart\'s successor does not continue A\'s encounter from its end');
    const owner = await editorOwner(editor);
    assertOwnEditor(owner, A, 'A\'s editor after Split Chart');
    h.assert(owner.revision === written[1], 'A\'s editor after Split Chart does not show the revision it saved');
    await editor.close();
    await other.close();
  });

  await s.step('A\'s Exit, after B\'s editor was opened last, closes the editor without saving either chart', async () => {
    const editor = await openEditor(A);
    const other = await openEditor(B);
    const before = {A: snapshot(A), B: snapshot(B)};
    await editor.locator('textarea[name="enTextarea"]').fill(`${marker}-A-legacy-exit`);
    const closed = editor.waitForEvent('close', {timeout: TIMEOUT});
    closed.catch(() => {});
    const ownership = observeLegacyExit(editor, recorder, { base, patient: A,
      chartContext: (await editorOwner(editor)).chartContext });
    let dialogs;
    try {
      dialogs = await h.withExpectedDialogs(editor, async () => {
        const response = editor.waitForResponse(r => r.request().method() === 'POST'
          && new URL(r.url()).pathname.endsWith('/encounter/SaveEncounter2'), {timeout: TIMEOUT});
        response.catch(() => {});
        await editor.locator('form[name="encForm"] input[type="button"][value="Exit"]').click();
        ownership.acknowledge(await response);
        await closed;
      });
      h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Exit did not ask for confirmation once');
      const artifact = await ownership.settle();
      if (artifact) console.log(`  Exact native Exit abort acknowledgment ${artifact}`);
    } finally { ownership.dispose(); }
    assertPatientStateUnchanged(before.A, snapshot(A), 'A\'s Exit (A\'s rows)');
    assertPatientStateUnchanged(before.B, snapshot(B), 'A\'s Exit');
    await other.close();
  });

  if (offersBilling) {
    await s.step('A\'s Sign, Save & Bill, after B\'s editor was opened last, opens a bill of A\'s own visit (BC)', async () => {
      const visit = addVisit(A);
      const editor = await openEditor(A, visit);
      const other = await openEditor(B, appointmentOf.B);
      const before = snapshot(B);
      const text = `${marker}-A-legacy-billed`;
      await editor.locator('textarea[name="enTextarea"]').fill(text);
      await clickAndAwaitReload(editor, editor.locator(`form[name="encForm"] input[type="button"][value="${signSaveBill}"]`),
        {label: 'A legacy Sign, Save & Bill'});
      await editor.locator('input[name="billKey"]').waitFor({state: 'attached', timeout: TIMEOUT});
      const page = norm(await editor.locator('body').innerText());
      h.assert(namesA(page) && !namesB(page), 'The bill Sign, Save & Bill opened does not name A alone');
      // The form's own reload links name the bill's patient and visit.
      const named = (await editor.content()).match(/demographic_no=' \+ '(\d+)' \+ '&appointment_no=' \+ '(\d*)'/);
      h.assert(named && named[1] === String(A) && named[2] === String(visit),
        'The bill Sign, Save & Bill opened is not for A\'s own visit');
      const revision = revisionsOf(A).at(-1);
      h.assert((sql.value(`SELECT encounter FROM eChart WHERE eChartId=${revision}`) || '').startsWith(`${text}\n[`),
        'Sign, Save & Bill did not write A\'s signed revision');
      h.assert(visitRow(appointmentOf.B) === untouched(B), 'Sign, Save & Bill changed B\'s appointment');
      assertPatientStateUnchanged(before, snapshot(B), 'A\'s Sign, Save & Bill');
      await editor.close();
      await other.close();
    });
  }

  await s.step('a double-clicked Save writes one revision and shows the editor again, not the concurrency page', async () => {
    // B's first editor still shows B's latest revision. Its second post, made from the revision the
    // first post replaced, used to be refused as a conflicting edit.
    const before = revisionsOf(B);
    const text = `${marker}-B-legacy-double-click`;
    await editorB.locator('textarea[name="enTextarea"]').fill(text);
    const badMark = recorder.badResponses.length;
    const posts = [];
    const onRequest = request => {
      if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/encounter/SaveEncounter2')) posts.push(request);
    };
    editorB.on('request', onRequest);
    try {
      const navigated = editorB.waitForEvent('framenavigated', {
        predicate: frame => frame === editorB.mainFrame(), timeout: TIMEOUT}).then(() => true, () => false);
      await editorB.locator('form[name="encForm"] input[type="button"][value="Save"]').dblclick();
      h.assert(await navigated, 'A double-clicked Save did not leave the editor');
      await editorLoaded(editorB, 'B\'s editor after a double-clicked Save');
    } finally {
      editorB.off('request', onRequest);
    }
    h.assert(posts.length === 1, `A double-clicked Save posted the editor ${posts.length} times`);
    h.assert(recorder.badResponses.length === badMark, 'A double-clicked Save was answered with an error page');
    const after = revisionsOf(B);
    h.assert(after.length === before.length + 1 && sql.value(`SELECT encounter FROM eChart WHERE eChartId=${after.at(-1)}`) === text,
      'A double-clicked Save did not write exactly one revision of B');
    const owner = await editorOwner(editorB);
    assertOwnEditor(owner, B, 'B\'s editor after a double-clicked Save');
    h.assert(owner.revision === after.at(-1), 'B\'s editor after a double-clicked Save does not show the revision it saved');
  });
  await checkLegacyMessageHandoff(s, { patient: A, otherPatient: B, trackEditorModules });
}

if (require.main === module) runWorkflow('legacy-encounter-editor-isolation', run, { openPatient: true, openMaster: false });
module.exports = { run, observeLegacyExit };
