/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Shared helpers for the `search-sort-*` list-screen checks (wave 7): synthetic patient fixtures that carry a
 * per-run name tag, driving the schedule's Search popup, and reading result rows back as data.
 * Fixtures are inserted by SQL (FAKE- names only) and removed through the same tag the other patient-search checks use.
 */
const h = require('./playwright-harness');
const ui = require('./playwright-ui');
const { removeMarkedPatients } = require('./gap-records-fixtures');
const { removeTaggedPatients } = require('./boundary-values');

const TIMEOUT = 30000;

/** Name tag every fixture surname starts with: unique per run, FAKE- prefixed, short enough for any search box. */
function nameTag(marker) {
  return 'FAKE-PW' + marker.slice(-6);
}

/**
 * Register cleanup for every patient whose surname starts with `tag`, BEFORE the first insert.
 * Also removes demographic_merged rows the fixtures own, so a merge fixture can be cleaned too.
 */
function registerPatientCleanup(s, tag) {
  const { sql, marker } = s;
  s.cleanup(() => {
    const ids = sql.rows(`SELECT demographic_no FROM demographic WHERE last_name LIKE ${h.sqlString(tag + '%')}`).map(r => r[0]);
    ids.forEach(id => h.assert(/^[1-9]\d*$/.test(id), 'Fixture patient id is not numeric'));
    if (ids.length) {
      sql.execute(`DELETE FROM demographic_merged WHERE demographic_no IN (${ids.join(',')}) OR merged_to IN (${ids.join(',')})`);
    }
    removeTaggedPatients(sql, tag, marker, removeMarkedPatients);
    h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE last_name LIKE ${h.sqlString(tag + '%')}`) === '0',
      'Fixture patients were not removed');
  });
}

/**
 * Insert one synthetic patient. `spec` fields default to a plain active female patient of the test provider;
 * pass `null` for a column that must be SQL NULL (provider, chart, phone ...). Returns the new demographic_no.
 */
function insertPatient(s, spec) {
  const { sql, provider } = s;
  const val = (v, dflt) => (v === undefined ? h.sqlString(dflt) : v === null ? 'NULL' : h.sqlString(v));
  const dob = spec.dob || '1980-01-02';
  const [y, m, d] = dob.split('-');
  const id = sql.value(`INSERT INTO demographic
    (last_name,first_name,sex,year_of_birth,month_of_birth,date_of_birth,patient_status,provider_no,roster_status,
     chart_no,phone,address,city,hc_type,province,lastUpdateDate)
    VALUES (${h.sqlString(spec.last)},${h.sqlString(spec.first || 'Fixture')},${h.sqlString(spec.sex || 'F')},
      ${h.sqlString(y)},${h.sqlString(m)},${h.sqlString(d)},${val(spec.status, 'AC')},
      ${spec.provider === undefined ? h.sqlString(provider) : val(spec.provider)},${val(spec.roster, 'NR')},
      ${val(spec.chart, null)},${val(spec.phone, null)},${val(spec.address, '1 Fixture Road')},'Testville','ON','ON',NOW());
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(id), 'A patient fixture was not created');
  return id;
}

/** Schedule > Search: the name-search popup. */
async function openSearchPopup(s) {
  return ui.clickOpensPopup(s.schedule, s.schedule.locator('#search a, a:has-text("Search")').first(),
    { context: s.context, recorder: s.recorder, label: 'patient-search', timeout: TIMEOUT });
}

/** Choose a mode, type the keyword and press Search on the popup (the results page replaces it in place). */
async function submitSearch(popup, mode, keyword) {
  await popup.locator('select[name="search_mode"]').selectOption(mode);
  await popup.locator('#keyword, input[name="keyword"]').first().fill(keyword);
  await ui.clickAndAwaitReload(popup, popup.locator("input[type='submit']").first(), { timeout: TIMEOUT, label: `search ${mode}` });
}

/** Rows of the general search results table as {id,name,chart,sex,dob,doctor,roster,status,phone} text. */
async function readResultRows(page) {
  return page.$$eval('#patientResults tr', trs => trs.filter(tr => tr.querySelector('td.demoIdSearch')).map(tr => {
    const t = cls => ((tr.querySelector('td.' + cls) || {}).textContent || '').replace(/\s+/g, ' ').trim();
    return { id: t('demoIdSearch'), name: t('name'), chart: t('chartNo'), sex: t('sex'), dob: t('dob'),
      doctor: t('doctor'), roster: t('rosterStatus'), status: t('patientStatus'), phone: t('phone') };
  }));
}

/** Click a sortable column header on the general search results (a submit button of the search-sort form). */
async function clickSort(page, orderby) {
  await ui.clickAndAwaitReload(page, page.locator(`button[form="search-sort"][value="${orderby}"]`).first(),
    { timeout: TIMEOUT, label: `sort by ${orderby}` });
}

/**
 * Paging buttons of the general search results (submit buttons of the search-page form). `offset` is the offset the
 * page being read was requested with; a button whose value is above it is Next Page, below it Last Page.
 */
async function pagerButtons(page, offset) {
  const buttons = await page.$$eval('button[form="search-page"][name="limit1"]', bs => bs.map(b => ({ value: Number(b.value) })));
  return { next: buttons.some(b => b.value > offset), last: buttons.some(b => b.value < offset), values: buttons.map(b => b.value) };
}

/** Click Next Page (dir > 0) or Last Page (dir < 0) on the general search results. */
async function clickPager(page, offset, dir) {
  const buttons = await page.$$eval('button[form="search-page"][name="limit1"]', bs => bs.map(b => Number(b.value)));
  const target = buttons.find(v => (dir > 0 ? v > offset : v < offset));
  h.assert(target !== undefined, `The results page has no ${dir > 0 ? 'Next' : 'Last'} Page button`);
  await ui.clickAndAwaitReload(page, page.locator(`button[form="search-page"][name="limit1"][value="${target}"]`).first(),
    { timeout: TIMEOUT, label: dir > 0 ? 'next page' : 'last page' });
}

/** Locale-insensitive, case- and accent-insensitive comparison: what a clinician reads as "alphabetical". */
const collator = new Intl.Collator('en', { sensitivity: 'base', numeric: false });

/** True when `values` never decrease under `cmp`. */
function nonDecreasing(values, cmp = (a, b) => collator.compare(a, b)) {
  for (let i = 1; i < values.length; i += 1) if (cmp(values[i - 1], values[i]) > 0) return false;
  return true;
}

/** Keep only the rows this run owns (their surname is rendered as "Last, First"; the id column is authoritative). */
function ownedRows(rows, ids) {
  const set = new Set(ids.map(String));
  return rows.filter(r => set.has(r.id));
}

/**
 * Run `body` and report, instead of throwing, any HTTP failure or uncaught page error it provoked, consuming exactly those recorder
 * entries (and their "Failed to load resource" console twins) so a later strict step is not blamed twice.
 * The caller still ASSERTS the correct behaviour with the returned list; this only lets a check collect several
 * independent defects before it fails, so every provable step is proven first.
 */
async function collectHttpFailures(s, body) {
  const since = { responses: s.recorder.badResponses.length, console: s.recorder.consoleIssues.length, errors: s.recorder.pageErrors.length };
  let thrown = null;
  try { await body(); } catch (error) { thrown = error; }
  const failed = s.recorder.badResponses.splice(since.responses).map(r => `HTTP ${r.status} ${h.pathOnly(r.url)}`);
  const pageErrors = s.recorder.pageErrors.splice(since.errors).map(e => String(e.text).split('\n')[0].slice(0, 160));
  for (let i = s.recorder.consoleIssues.length - 1; i >= since.console; i -= 1) {
    if (/^Failed to load resource:/.test(s.recorder.consoleIssues[i].text)) s.recorder.consoleIssues.splice(i, 1);
  }
  return { failed, pageErrors, thrown };
}

module.exports = {
  collectHttpFailures,
  TIMEOUT, nameTag, registerPatientCleanup, insertPatient, openSearchPopup, submitSearch, readResultRows,
  clickSort, pagerButtons, clickPager, collator, nonDecreasing, ownedRows,
};
