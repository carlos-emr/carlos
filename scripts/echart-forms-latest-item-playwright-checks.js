#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The eChart Forms module's started-form item must describe the record it opens (issue #4678).
 *
 * The module shows one item per started form. Its link is the latest-form shortcut
 * (form/forwardshortcutname?...&formId=latest), which FrmData resolves by edit time
 * (formEdited DESC, ID DESC). The item used to be dated from the most recently CREATED row, so for
 * a form whose rows are updated in place the item could show one record's date and open another.
 *
 * User path: Schedule > Search > Master Record > E-Chart for patient A > Forms module > the
 * started Annual item; a second tab opens patient B's chart (the login-wide encounter bean is then
 * B's) before A's item is clicked, as a clinician with two charts open would.
 *
 * Asserts, against rows seeded directly so creation order and edit order disagree:
 *  - A's Annual item is dated (hover title) from the row edited last, not the row created last,
 *    and still links to the intentional formId=latest shortcut;
 *  - with B's chart open, clicking A's item opens A's form, that same row's ID, and shows that
 *    row's stored Current Concerns text, never B's later, higher-numbered row;
 *  - when two of A's rows share an edit time, item and form both resolve to the higher ID.
 *
 * Fixtures: the workflow's FAKE patient (A, female, so Annual renders its female template) and a
 * second FAKE patient (B) with the run marker as last name; three formAnnual rows (two A, one B)
 * carrying the marker in currentConcerns. The shipped "Annual" registration (formAnnual) is used
 * as-is: hidden forms still list their started items, so the registry is not modified. Cleanup
 * deletes the owned formAnnual rows by ID and B's chart support rows and patient row, asserting
 * each is gone; the harness removes A.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { openMasterRecord } = require('./master-record-tabs-playwright-checks');
const { openChart, waitForNavbars } = require('./echart-navbar-modules-playwright-checks');

const TIMEOUT = 20000;
const FORM_NAME = 'Annual';
// The trailing '&' keeps a future "Annual V2" registration from matching.
const ITEM = `#forms a[onclick*="forwardshortcutname?formname=${FORM_NAME}&"]`;
const q = h.sqlString;

async function workflow(s) {
  const { sql, patient: A, provider, marker, context, recorder } = s;
  const owned = [];

  const B = sql.value(`INSERT INTO demographic (last_name, first_name, year_of_birth, month_of_birth,
    date_of_birth, sex, patient_status, provider_no, hc_type, province, roster_status, lastUpdateDate)
    VALUES (${q(marker)}, 'PatientBravo', '1975', '03', '04', 'M', 'AC', ${q(provider)}, 'ON', 'ON', 'NR', NOW());
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(B), 'Patient B fixture was not created');

  // Registered first, so it runs last: after the form rows below are gone. The harness removes A.
  s.cleanup(() => {
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${B}
      AND last_name=${q(marker)}`) === '1', 'Patient B fixture ownership changed');
    const support = [['casemgmt_note_lock', 'demographic_no'], ['casemgmt_tmpsave', 'demographic_no'],
      ['demographicExt', 'demographic_no'], ['demographicArchive', 'demographic_no']];
    sql.execute(support.map(([table, column]) => `DELETE FROM ${table} WHERE ${column}=${B}`).join(';'));
    h.assert(sql.value(`SELECT ${support.map(([table, column]) =>
      `(SELECT COUNT(*) FROM ${table} WHERE ${column}=${B})`).join('+')}`) === '0',
    'Patient B\'s chart support rows were not removed');
    sql.execute(`DELETE FROM demographic WHERE demographic_no=${B} AND last_name=${q(marker)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE demographic_no=${B}`) === '0',
      'Patient B was not removed');
  });
  s.cleanup(() => {
    if (!owned.length) return;
    const ids = owned.join(',');
    sql.execute(`DELETE FROM formAnnual WHERE ID IN (${ids}) AND demographic_no IN (${A},${B})
      AND currentConcerns LIKE ${q(`${marker}-%`)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM formAnnual WHERE ID IN (${ids})`) === '0',
      'The owned formAnnual rows were not removed');
  });

  // Literal timestamps, noon, months apart: a session time-zone difference between this client
  // and the application can move the hour but never the date the assertions read.
  function seed(demographicNo, created, edited, label) {
    const id = sql.value(`INSERT INTO formAnnual (demographic_no, provider_no, formCreated, formEdited, currentConcerns)
      VALUES (${demographicNo}, ${q(provider)}, ${q(created)}, ${q(edited)}, ${q(`${marker}-${label}`)});
      SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), `The ${label} formAnnual row was not created`);
    owned.push(id);
    return id;
  }
  // A's older-created row is the one edited last (an in-place update); its newer-created row was
  // never edited again. B's row is later and higher-numbered than both.
  const editedLast = seed(A, '2026-01-05', '2026-03-09 12:00:00', 'A-created-first-edited-last');
  const createdLast = seed(A, '2026-02-10', '2026-02-10 12:00:00', 'A-created-last');
  const other = seed(B, '2026-04-01', '2026-04-01 12:00:00', 'B-latest');
  h.assert(Number(createdLast) > Number(editedLast) && Number(other) > Number(createdLast),
    'The formAnnual fixture IDs are not in insertion order');

  const chartA = await s.chart();

  async function itemOf(chart) {
    const item = chart.locator(ITEM).first();
    await item.waitFor({ state: 'visible', timeout: TIMEOUT });
    return item;
  }

  async function openItem(chart, label, beforeClose = () => {}) {
    const popup = await s.popup(chart, await itemOf(chart), label);
    await popup.locator('textarea[name="currentConcerns"]').waitFor({ state: 'attached', timeout: TIMEOUT });
    const url = new URL(popup.url());
    const opened = {
      path: url.pathname,
      demographicNo: url.searchParams.get('demographic_no'),
      formId: url.searchParams.get('formId'),
      field: await popup.locator('input[type="hidden"][name="demographic_no"]').first().inputValue(),
      concerns: await popup.locator('textarea[name="currentConcerns"]').inputValue(),
    };
    beforeClose();
    // The item is a tracked popup: closing it reloads the Forms module. Await that reload so the
    // module is current, and never left in flight, when the next step reads it.
    const reloaded = chart.waitForResponse(response =>
      new URL(response.url()).pathname.endsWith('/encounter/displayForms'), { timeout: TIMEOUT });
    reloaded.catch(() => {});
    await popup.close();
    const response = await reloaded;
    await response.finished();
    h.assert(response.status() === 200, 'The Forms module did not reload after the form closed');
    return opened;
  }

  function assertOpened(opened, id, label, what) {
    h.assert(opened.path.endsWith('/form/formannual'), `${what} did not open the Annual form (${opened.path})`);
    h.assert(opened.demographicNo === A && opened.field === A, `${what} opened another patient's form`);
    h.assert(opened.formId === id, `${what} opened form ${opened.formId}, not the row its item is dated from (${id})`);
    h.assert(opened.concerns === `${marker}-${label}`, `${what} shows another record's Current Concerns`);
  }

  await s.step('A\'s Annual item is dated from the row edited last and keeps the latest shortcut', async () => {
    const item = await itemOf(chartA);
    const title = await item.getAttribute('title');
    const onclick = await item.getAttribute('onclick');
    h.assert(/^Annual 09-03-2026 \d\d:\d\d:\d\d$/.test(title || ''),
      `The Annual item is dated "${title}", not from the row edited last (09-03-2026); before #4678 it named the row created last (10-02-2026)`);
    h.assert(onclick.includes(`demographic_no=${A}&`) && onclick.includes('formId=latest'),
      'The Annual item no longer opens A\'s latest form through the shortcut');
    // An item renders a title link and, when dated, a second link with the date: one item, one title.
    const titles = await chartA.locator(ITEM).evaluateAll(links => links.map(link => link.title));
    h.assert(titles.length > 0 && titles.every(other => other === title),
      'A\'s Forms module lists more than one Annual item');
  });

  let chartB;
  await s.step('B\'s chart, opened in a second tab, lists B\'s own Annual item', async () => {
    const second = await context.newPage();
    await second.goto(s.schedule.url(), { waitUntil: 'domcontentloaded' });
    const { masterPage } = await openMasterRecord(context, second, recorder, {
      searchTerm: marker, preferredDemographicNo: B, timeout: TIMEOUT,
    });
    h.assert(new URL(masterPage.url()).searchParams.get('demographic_no') === B, 'The search opened a patient other than B');
    chartB = await openChart(context, masterPage, recorder, TIMEOUT);
    await waitForNavbars(chartB, TIMEOUT);
    h.assert(!chartA.isClosed(), 'Opening B\'s chart closed A\'s window');
    const title = await (await itemOf(chartB)).getAttribute('title');
    h.assert(/^Annual 01-04-2026 /.test(title || ''), `B's Annual item is dated "${title}", not from B's own row`);
  });

  // Gives A's newer-created row the same edit time as the row edited last. Applied while the form
  // popup is still open, so the module refresh its close triggers renders the tie: reloading the
  // chart page instead would abort the chart's note-lock release beacon.
  function tieEditTimes() {
    h.assert(sql.value(`UPDATE formAnnual SET formEdited='2026-03-09 12:00:00' WHERE ID=${createdLast}
      AND demographic_no=${A}; SELECT ROW_COUNT()`) === '1', 'The tie fixture did not update A\'s newer row');
  }

  await s.step('with B\'s chart open, A\'s Annual item opens the row it is dated from', async () => {
    await chartA.bringToFront();
    assertOpened(await openItem(chartA, 'annual-latest-a', tieEditTimes), editedLast,
      'A-created-first-edited-last', 'A\'s Annual item');
  });

  await s.step('when A\'s rows share an edit time, the item and the form both resolve to the higher ID', async () => {
    const title = await (await itemOf(chartA)).getAttribute('title');
    h.assert(/^Annual 09-03-2026 /.test(title || ''), `The tied Annual item is dated "${title}"`);
    assertOpened(await openItem(chartA, 'annual-latest-tie'), createdLast, 'A-created-last',
      'A\'s Annual item after an edit-time tie');
    h.assert(sql.value(`SELECT COUNT(*) FROM formAnnual WHERE ID=${other} AND demographic_no=${B}
      AND currentConcerns=${q(`${marker}-B-latest`)}`) === '1', 'B\'s Annual row changed');
  });
}

if (require.main === module) runWorkflow('echart-forms-latest-item', workflow, { openPatient: true });
module.exports = { workflow };
