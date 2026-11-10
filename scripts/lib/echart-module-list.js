/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
/*
 * The eChart's navigation modules as lists: each module (newCaseManagementView.js.jsp's
 * leftNavBarTitles / rightNavBarTitles name its <div>) holds a <ul id="...list"> of rows,
 * each row an <a class="links"> whose text is the item's title and whose title attribute is
 * the item's full link title (LeftNavBarDisplay.jsp), and a hidden <input id="...num"> with
 * how many rows are shown. Also a second owned patient and that patient's chart, for the
 * isolation half of a module check: a row seeded for one patient must not show in the other's
 * chart.
 */
const h = require('./playwright-harness');
const { releaseChartLocks } = require('./chart-lock-cleanup');
const { openMasterRecord } = require('../master-record-tabs-playwright-checks');
const { openChart, waitForNavbars } = require('../echart-navbar-modules-playwright-checks');

const TIMEOUT = 20000;

/** The rows and the shown-count of the module in the chart's <div id=divId>. */
function moduleList(chart, divId) {
  const module = chart.locator(`#${divId}`);
  return {
    module,
    // A row is a <li> with two <a class="links"> (the title, then the date), both carrying
    // the full title; `rows` counts rows, `row()` gives the title link.
    rows: module.locator('ul[id$="list"] li:has(a.links)'),
    async shown() {
      await module.locator('ul[id$="list"]').waitFor({ state: 'attached', timeout: TIMEOUT });
      return module.locator('input[type="hidden"][id$="num"]').inputValue();
    },
    /** The row whose full title (the link's title attribute) contains `text`. */
    row(text) {
      return module.locator(`ul[id$="list"] li:has(a.links[title*=${JSON.stringify(text)}]) a.links`).first();
    },
  };
}

/**
 * A second FAKE patient owned by the run (last name = marker). Its cleanup removes the rows the
 * chart writes for a patient on its own (note lock, temp save, ext, archive; the harness removes
 * those for the workflow's patient only) and then the patient, as
 * chart-session-state-two-window-isolation's createSecondPatient does. The cleanup runs after the
 * browser is closed, so note locks are released while it is open: see `releaseLocksAfter`.
 */
function seedSecondPatient(s, firstName = 'PatientBravo') {
  const { sql, marker, provider } = s;
  let id;
  // Register intent before INSERT: a committed insert can lose its returned key.
  s.cleanup(() => {
    const owned = sql.rows('SELECT demographic_no FROM demographic WHERE last_name=? AND first_name=? AND provider_no=?',
      [marker, firstName, provider]);
    h.assert(owned.length <= 1, 'The second patient marker is ambiguous');
    if (!id && owned.length === 0) return; // INSERT did not commit, or cleanup already ran.
    h.assert(owned.length === 1 && (!id || owned[0][0] === id), 'The second patient fixture changed owner');
    const patient = owned[0][0];
    h.assert(/^[1-9]\d*$/.test(patient), 'The second patient key is invalid');
    const support = [
      ['casemgmt_note_lock', 'demographic_no'], ['casemgmt_tmpsave', 'demographic_no'],
      ['demographicExt', 'demographic_no'], ['demographicArchive', 'demographic_no'],
    ];
    for (const [table, column] of support) sql.execute(`DELETE FROM ${table} WHERE ${column}=?`, [patient]);
    sql.execute('DELETE FROM demographic WHERE demographic_no=? AND last_name=? AND first_name=? AND provider_no=?',
      [patient, marker, firstName, provider]);
    h.assert(sql.value('SELECT COUNT(*) FROM demographic WHERE demographic_no=?', [patient]) === '0', 'The second patient was not removed');
    id = undefined;
  });
  id = h.insertId(sql, `INSERT INTO demographic (last_name, first_name, year_of_birth, month_of_birth,
      date_of_birth, sex, patient_status, provider_no, hc_type, province, roster_status, lastUpdateDate)
    VALUES (?, ?, '1975', '03', '04', 'M', 'AC', ?, 'ON', 'ON', 'NR', NOW())`, 'second patient', [marker, firstName, provider]);
  return id;
}

/**
 * Runs the workflow body and, whatever it did, releases the charts' note locks the way closing
 * their windows would, while the browser is still open (the cleanup runs after it is closed).
 */
async function releaseLocksAfter(s, body) {
  try {
    await body();
  } finally {
    await releaseChartLocks(s.context, s.config.baseUrl).catch(() => {});
  }
}

/**
 * Releases a chart window's note lock and closes it. The window's own release is a beacon sent
 * on pagehide, which the browser reports as an aborted ping because the window is gone; the
 * strict recorder would count that as a failure, so it is released by a request first and the
 * aborted ping is dropped (as chart-session-state-two-window-isolation does).
 */
async function closeChart(s, chart) {
  await releaseChartLocks(s.context, s.config.baseUrl, [chart]).catch(() => {});
  const mark = s.recorder.requestFailures.length;
  await chart.close({ runBeforeUnload: false });
  for (let i = s.recorder.requestFailures.length - 1; i >= mark; i--) {
    const entry = s.recorder.requestFailures[i];
    if (entry.resourceType === 'ping' && /ERR_ABORTED/.test(entry.errorText || '')
        && new URL(entry.url).pathname.endsWith('/CaseManagementEntry')) {
      s.recorder.requestFailures.splice(i, 1);
    }
  }
}

/** Opens the second patient's chart the way a clinician does (schedule, Search, record, E-Chart). */
async function openSecondChart(s, demographicNo, firstChart) {
  assertDistinctCharts(s, firstChart);
  // Keep the login/session shared, but free the application's named popup target.
  // Otherwise popupEChart can navigate the first patient's "encounter" window in place.
  await firstChart.evaluate(name => { window.name = name; }, `${s.marker}-first-chart`);
  const second = await s.context.newPage();
  const schedule = new URL(s.schedule.url());
  const appRoot = s.config.baseUrl.pathname.replace(/\/$/, '');
  h.assert(schedule.pathname.startsWith(`${appRoot}/`), 'The schedule is outside the configured application path');
  await h.gotoApp(second, s.config.baseUrl, schedule.pathname.slice(appRoot.length) + schedule.search);
  const { masterPage } = await openMasterRecord(s.context, second, s.recorder, {
    searchTerm: s.marker, preferredDemographicNo: demographicNo, timeout: TIMEOUT });
  const chart = await openChart(s.context, masterPage, s.recorder, TIMEOUT);
  await waitForNavbars(chart, TIMEOUT);
  h.assert(new URL(chart.url()).searchParams.get('demographicNo') === String(demographicNo), 'The second chart is not the second patient\'s');
  assertDistinctCharts(s, firstChart, chart);
  return chart;
}

/** Both windows retain their own patient while sharing one authenticated browser session. */
function assertDistinctCharts(s, first, second) {
  h.assert(first && !first.isClosed(), 'The first chart was closed');
  h.assert(new URL(first.url()).searchParams.get('demographicNo') === String(s.patient), 'The first chart changed patient');
  if (second) {
    h.assert(first !== second && !second.isClosed(), 'The second chart reused or closed the first window');
    h.assert(first.context() === second.context(), 'Chart isolation must be tested within the same login session');
  }
}

module.exports = { moduleList, seedSecondPatient, openSecondChart, assertDistinctCharts, releaseLocksAfter, closeChart, TIMEOUT };
