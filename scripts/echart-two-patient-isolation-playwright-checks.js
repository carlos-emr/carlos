#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Two patients' charts open in one login must stay isolated from each other.
 *
 * A clinician who opens patient A's E-Chart and then patient B's in a second window of the same
 * login expects each window to keep showing, and writing to, its own patient. The chart keeps ONE
 * session-wide EctSessionBean, rebuilt whenever a chart is opened, and its navbar modules, the
 * measurement history, the Forms module and the form setup all read the bean rather than the
 * patient their own window was opened for. Opening B therefore switches A's window to B the next
 * time it reloads a module or opens a history or a form (finding 182, a patient-safety defect).
 * This is the chart-side twin of rx-stash-patient-isolation, which pins the same fault in the
 * prescription module (fixed per tab in #3908).
 *
 * User path: Schedule > Search > Master Record > E-Chart for patient A; a second browser tab ->
 * Schedule > Search > Master Record > E-Chart for patient B; then back to A's window.
 *
 * Reads, asserted first: with only A open, A's Measurements module, its WT history, the plot, the
 * Forms module's Add Form links and a form opened from them are A's. With B open, the same in B's
 * window. Back in A's window the modules are reloaded the way the app reloads them (close a popup
 * the module opened), and then the module, the history, the plot, the Forms module and a form
 * opened from it must still be A's and must not have become B's. Writes, asserted after: a
 * measurement, a Dx code, a CPP item (Risk Factors), a prevention and a history Delete, each made
 * from A's window, land on A and leave every one of B's row counts unchanged.
 *
 * Fixtures: the workflow's FAKE patient (A, first name PatientAlpha), a second FAKE patient (B,
 * PatientBravo, last name the run marker) and one WT measurement each (61.2 kg for A, 87.4 kg for
 * B; the two plots differ, which is what lets the plot say whose data it shows). Cleanup removes both patients' measurements, archived
 * measurements, Dx, prevention, note, CPP and lock rows by key and asserts each is gone. No
 * clinic-wide state is changed.
 *
 * Not driven: the Vascular Tracker (SetupForm), the only form routed through FrmSetupForm2Action.
 * As seeded it is broken end to end (its registration is a .do route that answers 404, SetupForm
 * refuses the .do with HTTP 400 and SubmitForm answers 400), so a form save cannot be exercised
 * here; finding 182 is pinned through the history, the module reload and the Forms module instead.
 */
const crypto = require('node:crypto');
const h = require('./lib/playwright-harness');
const { clickAndAwaitReload } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { releaseChartLocks } = require('./lib/chart-lock-cleanup');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');
const { openChart, waitForNavbars } = require('./echart-navbar-modules-playwright-checks');

const TIMEOUT = 20000;
const A_WEIGHT = '61.2';
const B_WEIGHT = '87.4';
const OBSERVED = '2026-02-03';
const A_FIRST_NAME = 'PatientAlpha';
const B_FIRST_NAME = 'PatientBravo';
// Unique tokens (nothing else on a page contains them), so a plain substring says whose name a page prints.
const namesA = text => text.includes(A_FIRST_NAME);
const namesB = text => text.includes(B_FIRST_NAME);

const norm = text => text.replace(/\s+/g, ' ').trim();
const sha = bytes => crypto.createHash('sha256').update(bytes).digest('hex').slice(0, 16);

async function run(s) {
  const { sql, patient: A, provider, marker, context, recorder } = s;
  const q = h.sqlString;

  // ---- Fixtures --------------------------------------------------------------------------------
  // Two first names no page text or script contains, so a page that prints one names its patient.
  sql.execute(`UPDATE demographic SET first_name=${q(A_FIRST_NAME)} WHERE demographic_no=${A} AND last_name=${q(marker)}`);
  const B = sql.value(`INSERT INTO demographic (last_name, first_name, year_of_birth, month_of_birth,
    date_of_birth, sex, patient_status, provider_no, hc_type, province, roster_status, lastUpdateDate)
    VALUES (${q(marker)}, ${q(B_FIRST_NAME)}, '1975', '03', '04', 'M', 'AC', ${q(provider)}, 'ON', 'ON', 'NR', NOW());
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(B), 'Patient B fixture was not created');
  const patients = `${A},${B}`;

  // Registered first, so it runs last: after every child row below is gone. The harness removes A.
  s.cleanup(() => {
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${B}
      AND last_name=${q(marker)}`) === '1', 'Patient B fixture ownership changed');
    // The chart's own children (notes, locks, drafts, Dx and the rest) are deleted by the cleanup
    // registered after this one, which runs first; what is left is the patient's own support rows.
    sql.execute(['demographicExt', 'demographicArchive']
      .map(table => `DELETE FROM ${table} WHERE demographic_no=${B}`).join(';'));
    sql.execute(`DELETE FROM demographic WHERE demographic_no=${B} AND last_name=${q(marker)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${B}`) === '0',
      'Patient B was not removed');
  });
  s.cleanup(() => {
    const notes = `SELECT note_id FROM casemgmt_note WHERE demographic_no IN (${patients})`;
    sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${notes});
      DELETE FROM casemgmt_note WHERE demographic_no IN (${patients});
      DELETE FROM casemgmt_issue WHERE demographic_no IN (${patients});
      DELETE FROM casemgmt_cpp WHERE demographic_no IN (${patients});
      DELETE FROM casemgmt_note_lock WHERE demographic_no IN (${patients});
      DELETE FROM casemgmt_tmpsave WHERE demographic_no IN (${patients});
      DELETE FROM eChart WHERE demographicNo IN (${patients});
      DELETE FROM dxresearch WHERE demographic_no IN (${patients});
      DELETE x FROM preventionsExt x JOIN preventions p ON p.id=x.prevention_id WHERE p.demographic_no IN (${patients});
      DELETE FROM preventions WHERE demographic_no IN (${patients});
      DELETE FROM measurements WHERE demographicNo IN (${patients});
      DELETE FROM measurementsDeleted WHERE demographicNo IN (${patients})`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no IN (${patients}))
      + (SELECT COUNT(*) FROM casemgmt_issue WHERE demographic_no IN (${patients}))
      + (SELECT COUNT(*) FROM casemgmt_cpp WHERE demographic_no IN (${patients}))
      + (SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no IN (${patients}))
      + (SELECT COUNT(*) FROM eChart WHERE demographicNo IN (${patients}))
      + (SELECT COUNT(*) FROM dxresearch WHERE demographic_no IN (${patients}))
      + (SELECT COUNT(*) FROM preventions WHERE demographic_no IN (${patients}))
      + (SELECT COUNT(*) FROM measurements WHERE demographicNo IN (${patients}))
      + (SELECT COUNT(*) FROM measurementsDeleted WHERE demographicNo IN (${patients}))`) === '0',
    'Owned chart rows were not removed');
  });

  const seedWeight = (demo, value) => sql.value(`INSERT INTO measurements
    (type, demographicNo, providerNo, dataField, measuringInstruction, comments, dateObserved, dateEntered)
    VALUES ('WT', ${demo}, ${q(provider)}, ${q(value)}, 'in kg', ${q(marker)}, ${q(OBSERVED)}, NOW());
    SELECT LAST_INSERT_ID()`);
  const weight = { a: seedWeight(A, A_WEIGHT), b: seedWeight(B, B_WEIGHT) };
  h.assert(/^[1-9]\d*$/.test(weight.a) && /^[1-9]\d*$/.test(weight.b), 'The WT measurement fixtures were not created');

  /** Row counts that a write from A's window must leave alone for B. */
  const counts = demo => sql.rows(`SELECT
    (SELECT COUNT(*) FROM measurements WHERE demographicNo=${demo}),
    (SELECT COUNT(*) FROM measurementsDeleted WHERE demographicNo=${demo}),
    (SELECT COUNT(*) FROM dxresearch WHERE demographic_no=${demo}),
    (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${demo}),
    (SELECT COUNT(*) FROM preventions WHERE demographic_no=${demo})`)[0].join(',');
  let bBaseline;
  const assertBUnchanged = what => h.assert(counts(B) === bBaseline,
    `${what}: patient B's rows changed (measurements, archived measurements, Dx, notes, preventions: `
    + `${bBaseline} became ${counts(B)})`);

  // ---- Page helpers ----------------------------------------------------------------------------
  const moduleText = async (chart, id) => norm(await chart.locator(`#${id}`).innerText());
  const weightLink = chart => chart.locator('#measurements a[onclick*="SetupDisplayHistory?type=WT"]').first();
  const ownerLabel = owner => (owner === String(A) ? 'A' : owner === String(B) ? 'B' : 'another patient');
  const idsLabel = ids => ids.map(id => (id === weight.a ? 'A' : id === weight.b ? 'B' : 'another patient')).join('+');

  /** The WT history opened from a chart window: whose rows it lists and whose name it prints. */
  async function openWeightHistory(chart, label) {
    await weightLink(chart).waitFor({ state: 'visible', timeout: TIMEOUT });
    const popup = await s.popup(chart, weightLink(chart), label);
    await popup.locator('tr.data').first().waitFor({ state: 'visible', timeout: TIMEOUT });
    const ids = await popup.locator('input[name="deleteCheckbox"]').evaluateAll(els => els.map(e => e.value));
    const text = norm(await popup.locator('body').innerText());
    return { popup, ids, text };
  }

  /** The digest of the plot the history's Plot control opens (the plot is deterministic per data set). */
  async function plotOf(history) {
    const rendered = context.waitForEvent('response', {
      predicate: r => new URL(r.url()).pathname.endsWith('/GraphMeasurements') && r.request().isNavigationRequest(),
      timeout: TIMEOUT,
    });
    rendered.catch(() => {});
    await history.locator('input[onclick*="GraphMeasurements"]').click();
    const response = await rendered;
    h.assert(response.status() === 200 && (response.headers()['content-type'] || '').startsWith('image/png'),
      'The Plot control did not answer with a PNG');
    const bytes = await response.body();
    h.assert(bytes.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])),
      'The Plot control returned something other than PNG bytes');
    return sha(bytes);
  }

  /** Close the plot popup the Plot control opened, if it is still open (it reuses a named window). */
  async function closePlot() {
    for (const page of context.pages()) {
      if (!page.isClosed() && /\/encounter\/GraphMeasurements/.test(page.url())) await page.close();
    }
  }

  /**
   * Close a popup that a module item opened and wait for the module to repaint from the reload the
   * app issues when such a popup closes (reloadWindows polling in newCaseManagementView.js). The
   * module's first child is marked first: the repaint replaces every child, so the mark going away
   * is the repaint having landed, whether or not the content changed.
   */
  async function closeAndAwaitModuleReload(chart, popup, id) {
    await chart.locator(`#${id} > *`).first().evaluate(el => { el.setAttribute('data-stale-before-reload', '1'); });
    await popup.close();
    await chart.locator(`#${id} [data-stale-before-reload]`).waitFor({ state: 'detached', timeout: TIMEOUT })
      .catch(() => { throw new Error(`The ${id} module did not reload after the popup it opened closed`); });
  }

  /** The Add Form links of the Forms module: the demographic number each carries. */
  async function formLinkOwners(chart) {
    const handlers = await chart.locator('#forms a.menuItemleft').evaluateAll(els => els.map(e => e.getAttribute('onclick') || ''));
    return handlers.map(onclick => (onclick.match(/demographic_no=(\d+)/) || [])[1]).filter(Boolean);
  }

  /** Open the first Add Form item of a chart window's Forms module. */
  async function openFirstForm(chart, label) {
    await chart.locator('#menuTitle1 a').hover();
    const item = chart.locator('#forms a.menuItemleft').first();
    await item.waitFor({ state: 'visible', timeout: TIMEOUT });
    return s.popup(chart, item, label);
  }

  /**
   * Whose form a popup is: the demographic number in its address and the name it prints. The
   * name is read from the page source, because a form prints it in an input's value.
   */
  async function formOwner(form) {
    const text = norm(await form.content());
    return {
      demographicNo: new URL(form.url()).searchParams.get('demographic_no'),
      namesA: namesA(text),
      namesB: namesB(text),
    };
  }

  let chartA;
  let chartB;
  let plotA;
  const entered = {};

  /**
   * Load B's chart afresh, in a window of its own, so that B is the most recently loaded chart
   * whatever the previous step did. A chart load is what rebuilds the session's patient, and any
   * module of A's window that names its patient (the CPP boxes do) rebuilds it back to A, so
   * without this a write step could pass only because an earlier step had pointed the session at
   * A. A new window rather than chartB.reload(): leaving a chart sends its note-lock release
   * beacon, which the browser reports as an aborted ping and the strict recorder counts as a failure.
   */
  async function reloadB() {
    const fresh = await context.newPage();
    await fresh.goto(chartB.url(), { waitUntil: 'domcontentloaded' });
    await waitForNavbars(fresh, TIMEOUT);
  }

  // ---- Controls: with a single chart open nothing can cross, so a failure here is a broken
  // fixture or selector, not finding 182. --------------------------------------------------------
  await s.step('A\'s chart, opened first, shows A\'s measurement history, plot, Forms links and form', async () => {
    chartA = await s.chart();
    const text = await moduleText(chartA, 'measurements');
    h.assert(text.includes(A_WEIGHT) && !text.includes(B_WEIGHT), 'A\'s Measurements module does not list A\'s weight alone');
    const history = await openWeightHistory(chartA, 'a-history-alone');
    h.assert(history.ids.length === 1 && history.ids[0] === weight.a,
      'A\'s history, opened alone, does not list exactly A\'s measurement');
    h.assert(namesA(history.text) && !namesB(history.text), 'A\'s history is not headed with A\'s name');
    plotA = await plotOf(history.popup);
    await closePlot();
    await history.popup.close();
    const owners = await formLinkOwners(chartA);
    h.assert(owners.length > 0 && owners.every(owner => owner === String(A)),
      'A\'s Forms module, opened alone, does not carry A\'s demographic number on every Add Form link');
    const form = await openFirstForm(chartA, 'a-form-alone');
    const owner = await formOwner(form);
    h.assert(owner.demographicNo === String(A), `A form opened from A's chart, alone, is for ${ownerLabel(owner.demographicNo)}`);
    h.assert(owner.namesA && !owner.namesB, 'A form opened from A\'s chart, alone, does not print A\'s name alone');
    await closeAndAwaitModuleReload(chartA, form, 'forms');
  });

  await s.step('B\'s chart, opened in a second window of the same login, shows B\'s own rows', async () => {
    // A second browser tab, as a clinician opens one: its popups do not re-target A's windows.
    const second = await context.newPage();
    await second.goto(s.schedule.url(), { waitUntil: 'domcontentloaded' });
    const { masterPage } = await openMasterRecord(context, second, recorder, {
      searchTerm: marker, preferredDemographicNo: B, timeout: TIMEOUT,
    });
    h.assert(new URL(masterPage.url()).searchParams.get('demographic_no') === B, 'The search opened a patient other than B');
    chartB = await openChart(context, masterPage, recorder, TIMEOUT);
    await waitForNavbars(chartB, TIMEOUT);
    h.assert(!chartA.isClosed() && new URL(chartA.url()).searchParams.get('demographicNo') === A,
      'Opening B\'s chart closed or navigated A\'s window');
    h.assert(new URL(chartB.url()).searchParams.get('demographicNo') === B, 'B\'s chart window is not B\'s chart');
    const text = await moduleText(chartB, 'measurements');
    h.assert(text.includes(B_WEIGHT) && !text.includes(A_WEIGHT), 'B\'s Measurements module does not list B\'s weight alone');
    const history = await openWeightHistory(chartB, 'b-history');
    h.assert(history.ids.length === 1 && history.ids[0] === weight.b, 'B\'s history does not list exactly B\'s measurement');
    h.assert(namesB(history.text) && !namesA(history.text), 'B\'s history is not headed with B\'s name');
    h.assert(await plotOf(history.popup) !== plotA,
      'The two patients\' plots are identical, so a plot cannot say whose data it shows');
    await closePlot();
    await history.popup.close();
    const owners = await formLinkOwners(chartB);
    h.assert(owners.length > 0 && owners.every(owner => owner === String(B)),
      'B\'s Forms module does not carry B\'s demographic number on every Add Form link');
    bBaseline = counts(B);
  });

  // ---- Reads from A's window with B open: the first defect step on 2026.08 ---------------------
  await s.step('A\'s reloaded navbar, measurement history and plot show only A\'s rows', async () => {
    const problems = [];
    // 1. The history link was rendered while A was the only chart; it names no patient, so the
    //    server decides whose history to show.
    const first = await openWeightHistory(chartA, 'a-history-with-b-open');
    if (first.ids.join() !== weight.a) problems.push(`A's history lists the measurement(s) of ${idsLabel(first.ids)}`);
    if (namesB(first.text) || !namesA(first.text)) problems.push('A\'s history is headed with another patient\'s name');
    if (await plotOf(first.popup) !== plotA) problems.push('A\'s plot is not the plot of A\'s data');
    await closePlot();
    // 2. Closing the history makes the app reload the module, as it does after every module popup.
    await closeAndAwaitModuleReload(chartA, first.popup, 'measurements');
    const text = await moduleText(chartA, 'measurements');
    if (!text.includes(A_WEIGHT)) problems.push('A\'s reloaded Measurements module does not list A\'s weight');
    if (text.includes(B_WEIGHT)) problems.push('A\'s reloaded Measurements module lists B\'s weight');
    // 3. The reloaded module's own link, followed again.
    const again = await openWeightHistory(chartA, 'a-history-after-reload');
    if (again.ids.join() !== weight.a) problems.push(`A's history, opened from the reloaded module, lists the measurement(s) of ${idsLabel(again.ids)}`);
    await again.popup.close();
    h.assert(problems.length === 0, `A's window, with B's chart open, no longer shows only A: ${problems.join('; ')}`);
  });

  await s.step('a form opened from A\'s chart shows A', async () => {
    const problems = [];
    // Open an Add Form item and close it: the Forms module reloads when the popup closes.
    const before = await openFirstForm(chartA, 'a-form-before-reload');
    await closeAndAwaitModuleReload(chartA, before, 'forms');
    const owners = await formLinkOwners(chartA);
    if (owners.some(owner => owner !== String(A))) {
      problems.push(`A's reloaded Forms module carries ${[...new Set(owners.map(ownerLabel))].join('+')} on its Add Form links`);
    }
    const form = await openFirstForm(chartA, 'a-form-after-reload');
    const owner = await formOwner(form);
    if (owner.demographicNo !== String(A)) problems.push(`the form opened from A's window is for ${ownerLabel(owner.demographicNo)}`);
    if (owner.namesB || !owner.namesA) problems.push('the form is headed with another patient\'s name');
    await form.close();
    h.assert(problems.length === 0, `A's Forms module and forms follow another patient: ${problems.join('; ')}`);
  });

  // ---- Writes from A's window with B open ------------------------------------------------------
  await s.step('a measurement entered from A\'s window lands on A and leaves B unchanged', async () => {
    await reloadB();
    const where = `type='BP' AND comments=${q(`${marker}-bp`)}`;
    await chartA.locator('#menuTitle3 a').hover();
    const vitals = chartA.locator('#menu3 a.menuItemleft').filter({ hasText: 'Vitals' });
    await vitals.waitFor({ state: 'visible', timeout: TIMEOUT });
    const group = await s.popup(chartA, vitals, 'a-vitals');
    await group.locator('#row-BP').waitFor({ state: 'visible', timeout: TIMEOUT });
    const opened = new URL(group.url()).searchParams.get('demographicNo');
    h.assert(opened === String(A), `A's Vitals entry form opened for ${ownerLabel(opened)}`);
    const row = group.locator('#row-BP');
    await row.locator('input[name^="inputValue-"]').fill('128/82');
    await row.locator('input[name^="date-"]').fill('2026-03-04');
    await row.locator('input[name^="comments-"]').fill(`${marker}-bp`);
    const closed = group.waitForEvent('close', { timeout: TIMEOUT });
    await group.getByRole('button', { name: 'Submit', exact: true }).click();
    await closed;
    await expectValue(sql, `SELECT COUNT(*) FROM measurements WHERE ${where}`, '1', 'The measurement was not saved');
    entered.bp = sql.value(`SELECT id FROM measurements WHERE ${where}`);
    h.assert(sql.value(`SELECT demographicNo FROM measurements WHERE id=${entered.bp}`) === A,
      'The measurement entered in A\'s window was saved to another patient');
    assertBUnchanged('A\'s measurement');
  });

  await s.step('a Dx code added from A\'s window lands on A and leaves B unchanged', async () => {
    await reloadB();
    const plus = chartA.locator('#Dx a[onclick*="setupDxResearch"]').filter({ hasText: '+' }).first();
    const registry = await s.popup(chartA, plus, 'a-dx');
    const form = registry.locator('form[action*="/oscarResearch/oscarDxResearch/dxResearch"]');
    await form.locator('input[name="demographicNo"]').waitFor({ state: 'attached', timeout: TIMEOUT });
    h.assert(await form.locator('input[name="demographicNo"]').inputValue() === A, 'A\'s Dx registry opened for another patient');
    await form.locator('select[name="selectedCodingSystem"]').selectOption('icd9');
    await form.locator('input[name="xml_research1"]').fill('250');
    await clickAndAwaitReload(registry, form.locator('input[name="codeAdd"]'), { label: 'Dx Add' });
    const owned = `dxresearch_code='250' AND coding_system='icd9' AND demographic_no IN (${patients})`;
    await expectValue(sql, `SELECT COUNT(*) FROM dxresearch WHERE ${owned}`, '1', 'The Dx code was not saved');
    h.assert(sql.value(`SELECT demographic_no FROM dxresearch WHERE ${owned}`) === A,
      'The Dx code added in A\'s window was saved to another patient');
    assertBUnchanged('A\'s Dx code');
    await registry.close();
  });

  await s.step('a CPP item added from A\'s window lands on A and leaves B unchanged', async () => {
    await reloadB();
    const note = `${marker} risk factor`;
    await chartA.locator('#rightNavBar a[title="Add Item"][onclick*="Risk Factors"]').first().click();
    const dialog = chartA.locator('#showEditNote');
    await dialog.waitFor({ state: 'visible', timeout: TIMEOUT });
    await chartA.locator('#noteEditTxt').fill(note);
    const saved = chartA.waitForResponse(r => /method=issueNoteSave/.test(r.url()) && r.request().method() === 'POST', { timeout: TIMEOUT });
    await dialog.locator('input[type="image"][title="Sign & Save"]').click();
    h.assert((await saved).status() < 400, 'The CPP item save was refused');
    await dialog.waitFor({ state: 'hidden', timeout: TIMEOUT });
    const owned = `note=${q(note)} AND demographic_no IN (${patients})`;
    await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_note WHERE ${owned}`, '1', 'The CPP item was not saved');
    h.assert(sql.value(`SELECT demographic_no FROM casemgmt_note WHERE ${owned}`) === A,
      'The CPP item added in A\'s window was saved to another patient');
    assertBUnchanged('A\'s CPP item');
  });

  await s.step('a prevention added from A\'s window lands on A and leaves B unchanged', async () => {
    await reloadB();
    const plus = () => chartA.locator('#preventions a[onclick*="ViewPreventionIndex"]').filter({ hasText: '+' }).first();
    // Open and close the list first: the Preventions module reloads when its popup closes, as it
    // does after every prevention a clinician records.
    await closeAndAwaitModuleReload(chartA, await s.popup(chartA, plus(), 'a-prevention-reload'), 'preventions');
    const list = await s.popup(chartA, plus(), 'a-prevention');
    const opened = new URL(list.url()).searchParams.get('demographic_no');
    h.assert(opened === String(A), `A's Preventions list opened for ${ownerLabel(opened)}`);
    await list.locator('#immunization').fill('Fluzone');
    const editor = await s.popup(list,
      list.locator('#immunization_choices [class*="item"], #immunization_choices div, #immunization_choices li').first(), 'a-prevention-editor');
    await editor.locator('[name="given"][value="given"]').check();
    await editor.locator('#prevDate').fill('2026-01-05');
    await editor.locator('[name="comments"]').fill(`${marker}-prevention`);
    await editor.locator('input[type="submit"][name="action"]').first().click();
    const owned = `prevention_type='Inf' AND deleted=0 AND DATE(prevention_date)='2026-01-05' AND demographic_no IN (${patients})`;
    await expectValue(sql, `SELECT COUNT(*) FROM preventions WHERE ${owned}`, '1', 'The prevention was not saved');
    h.assert(sql.value(`SELECT demographic_no FROM preventions WHERE ${owned}`) === A,
      'The prevention added in A\'s window was saved to another patient');
    assertBUnchanged('A\'s prevention');
    for (const page of [editor, list]) if (!page.isClosed()) await page.close();
  });

  await s.step('a history Delete in A\'s window archives A\'s reading and leaves B\'s', async () => {
    await reloadB();
    const history = await openWeightHistory(chartA, 'a-history-delete');
    const box = history.popup.locator(`input[name="deleteCheckbox"][value="${weight.a}"]`);
    h.assert(await box.count() === 1, `A's history lists the measurement(s) of ${idsLabel(history.ids)}, not A's weight`);
    await box.check();
    await clickAndAwaitReload(history.popup, history.popup.locator('input[onclick="submit();"]'), { label: 'history Delete' });
    await expectValue(sql, `SELECT COUNT(*) FROM measurements WHERE id=${weight.a}`, '0', 'The selected reading was not deleted');
    h.assert(sql.value(`SELECT COUNT(*) FROM measurementsDeleted WHERE originalId=${weight.a} AND demographicNo=${A}
      AND dataField=${q(A_WEIGHT)}`) === '1', 'The deleted reading was not archived intact for A');
    h.assert(sql.value(`SELECT dataField FROM measurements WHERE id=${weight.b}`) === B_WEIGHT, 'Delete changed B\'s weight');
    h.assert(sql.value(`SELECT COUNT(*) FROM measurements WHERE id=${entered.bp} AND demographicNo=${A}`) === '1',
      'Delete removed a reading of A that was not selected');
    assertBUnchanged('A\'s history Delete');
    await history.popup.close();
  });
}

async function workflow(s) {
  try {
    await run(s);
  } finally {
    // The charts take note locks. Release them the way closing the windows would, whatever a
    // step did, so a failed run does not leave locks for the cleanup to find; the cleanup still
    // deletes any that remain.
    await releaseChartLocks(s.context, s.config.baseUrl).catch(() => {});
  }
}

if (require.main === module) runWorkflow('echart-two-patient-isolation', workflow, { openPatient: true });
module.exports = { workflow };
