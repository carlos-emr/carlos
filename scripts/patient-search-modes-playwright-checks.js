#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Browser regression check for patient search: every mode, against the database.
 *
 * WHY THIS IS WORTH A CHECK OF ITS OWN. Patient search is the single most-used
 * control in the product -- it is how every clinician reaches every patient --
 * and it had no coverage beyond "the page renders". It is also the one place
 * where being WRONG is a privacy incident rather than an inconvenience: a search
 * that over-matches shows one patient's name, date of birth and health number to
 * a clinician who asked about someone else.
 *
 * WHAT IT ASSERTS, and why in both directions:
 *   1. Everything the page shows actually matches what was typed, under that
 *      mode's own rule. This is the direction that catches over-matching, and it
 *      is checked against the database, not against the page's own claim.
 *   2. Everything the database says matches IS shown. This is the direction that
 *      catches a search that quietly finds nothing -- the failure a "does the
 *      page render" check passes straight through.
 *
 * The modes do not share a matching rule, and that is the point: `hin` is an
 * exact match, `chart_no` and the name are prefix matches, `phone` and `address`
 * are substring matches, and `dob` is three independent prefix matches on the
 * year, month and day columns (DemographicDaoImpl). A check that assumed one
 * rule would pass on the mode it was written for and mean nothing on the rest.
 *
 * IT ALSO DRIVES REAL JAVASCRIPT, which is why it is a browser check and not an
 * HTTP one. checkTypeIn() lowercases a name search, rewrites a scanned health
 * card barcode into a HIN search, and refuses a short date of birth with an
 * alert(); searchInactive() and searchAll() rewrite the hidden ptstatus field
 * before submitting. None of that exists server-side.
 *
 * ENTERED THE WAY A CLINICIAN ENTERS IT: login, the schedule's Search control,
 * then the form. Never by navigating to a search URL.
 *
 * READ-ONLY: it types and submits searches. It creates, edits and deletes
 * nothing, and it opens no patient record.
 *
 * Defaults are for the local devcontainer:
 *   MYSQL_PASSWORD=... npm run test:patient-search-modes-playwright
 *
 * Required environment:
 *   MYSQL_PASSWORD                the dev database password; the check compares
 *                                 the rendered result set against SQL
 * Optional environment (the common contract is in lib/playwright-harness.js):
 *   SEARCH_MODES=all              comma-separated subset of the mode names
 *   SEARCH_INACTIVE_STATUSES=     override the deployment's inactive_statuses
 *                                 property (default: the app's own default)
 *   SEARCH_TIMEOUT_MS=20000       per-step allowance
 *
 * IMPLEMENTS: coverage plan section 2.4, `patient-search-modes`
 * (docs/ui-tests/playwright-coverage-plan-2026.08.md). App defects this check
 * finds are recorded in docs/ui-tests/app-findings-log.md, not worked around.
 */

const {
  SkipCheck, assert, assertStrictPage, createRecorder, createSqlRunner, launchBrowser, login,
  newContext, readConfig, runCheck, sqlString, withExpectedDialogs,
} = require('./lib/playwright-harness');
const { clickAndAwaitReload, clickOpensPopupOrNavigates } = require('./lib/playwright-ui');

/*
 * The app's own default for the `inactive_statuses` property, read out of
 * demographicsearchresults.jsp. A deployment that changed the property must pass
 * SEARCH_INACTIVE_STATUSES to match, or the active/inactive assertions below
 * would be comparing against a different definition of "active" than the app's.
 */
const DEFAULT_INACTIVE_STATUSES = ['IN', 'DE', 'IC', 'ID', 'MO', 'FI'];

/** How many candidate rows the seed queries consider. Keeps them O(window). */
const SEED_WINDOW = 200;

/** A seed must match few enough patients to fit the page's own 10-row limit. */
const PAGE_LIMIT = 10;

/**
 * One row per search mode: how the app matches, and how to seed a search of it.
 *
 * `predicate(alias, value)` is the mode's matching rule transcribed from
 * DemographicDaoImpl, as SQL against the demographic table. It is the whole
 * assertion -- get it wrong and the check is comparing the app against a
 * different query than the one it runs.
 *
 * `seedValue(row)` turns a candidate row into the string a user would type.
 */
const MODES = [
  {
    name: 'search_demographic_no',
    title: 'demographic number',
    // "d.demographicNo = :demographicNo" -- exact.
    columns: ['demographic_no'],
    seedValue: (row) => row[0],
    predicate: (alias, value) => {
      // Refused rather than coerced: Number('1 OR 1=1') is NaN, which would
      // build syntactically invalid SQL and fail with a message about MySQL
      // syntax instead of about the value that was wrong.
      assert(/^\d+$/.test(String(value).trim()),
        `a demographic number search takes digits only, got ${JSON.stringify(String(value))}`);
      return `${alias}.demographic_no = ${Number(value)}`;
    },
  },
  {
    name: 'search_name',
    title: 'name',
    // "d.lastName like :lastName" (+ firstName when a comma is typed), each
    // with a trailing % only. checkTypeIn() lowercases the box first.
    columns: ['last_name', 'first_name'],
    seedValue: (row) => `${row[0]},${row[1]}`,
    predicate: (alias, value) => {
      const [last, first] = String(value).split(',');
      return `${alias}.last_name LIKE ${sqlString(`${last.trim()}%`)}`
        + ` AND (${alias}.first_name LIKE ${sqlString(`${first.trim()}%`)}`
        + ` OR ${alias}.alias LIKE ${sqlString(`${first.trim()}%`)})`;
    },
  },
  {
    name: 'search_hin',
    title: 'health number',
    // PREFIX, and no merged exclusion. Both halves of this were wrong before,
    // and the comment asserted the opposite of the code it described.
    //
    // demographicsearchresults.jsp:583 calls searchDemographicByHIN(keyword,
    // limit, offset, orderBy, providerNo, outOfDomain), which funnels into the
    // implementation that binds `hinStr.trim() + "%"` -- a prefix match. The
    // exact binding lives on searchDemographicByHIN(String), a different
    // overload used by HRM report matching, which no browser route reaches.
    // And that implementation builds `From Demographic d where d.hin like :hin`
    // plus statuses, program domain and order: its `ignoreMerged` parameter is
    // accepted and never read, so there is no MERGED clause to model.
    //
    // As written before, any health number that is a prefix of another would
    // have failed the "everything that matches is shown" direction.
    columns: ['hin'],
    seedValue: (row) => row[0],
    predicate: (alias, value) => `${alias}.hin LIKE ${sqlString(`${value}%`)}`,
  },
  {
    name: 'search_chart_no',
    title: 'chart number',
    // "d.chartNo like :chartNo" with a trailing % -- prefix.
    columns: ['chart_no'],
    seedValue: (row) => row[0],
    predicate: (alias, value) => `${alias}.chart_no LIKE ${sqlString(`${value}%`)}`,
  },
  {
    name: 'search_phone',
    title: 'phone',
    // THREE numbers, not two. DemographicDaoImpl.searchDemographicByPhone*
    // UNIONS its "(d.phone like :phone OR d.phone2 LIKE :phone)" branch with a
    // second query over demographicExt:
    //
    //   INNER JOIN demographicExt dext ON dext.demographic_no = de.demographic_no
    //   WHERE dext.key_val = 'demo_cell' AND dext.value LIKE :value
    //     AND de.patient_status != 'MERGED'
    //
    // Modelling only phone/phone2 meant a patient whose CELL is the matching
    // number was returned by the UI and absent from the oracle -- reported as
    // the application showing a patient it should not have, which is the
    // privacy direction and the most serious verdict this check can reach. A
    // false one, from an incomplete model.
    //
    // All three are substring matches (the DAO wraps %...% on both branches).
    // The MERGED exclusion is on the extension branch ONLY; that asymmetry is
    // the DAO's, and reproducing it is the point.
    columns: ['phone'],
    seedValue: (row) => row[0],
    predicate: (alias, value) => `((${alias}.phone LIKE ${sqlString(`%${value}%`)}`
      + ` OR ${alias}.phone2 LIKE ${sqlString(`%${value}%`)})`
      + ` OR (${alias}.patient_status <> 'MERGED' AND EXISTS (`
      + `SELECT 1 FROM demographicExt dext WHERE dext.demographic_no = ${alias}.demographic_no`
      + ` AND dext.key_val = 'demo_cell' AND dext.value LIKE ${sqlString(`%${value}%`)})))`,
  },
  {
    name: 'search_address',
    title: 'address',
    // "d.address like :address" wrapped in %...% -- substring.
    columns: ['address'],
    seedValue: (row) => row[0],
    predicate: (alias, value) => `${alias}.address LIKE ${sqlString(`%${value}%`)}`,
  },
  {
    name: 'search_dob',
    title: 'date of birth',
    // THREE independent prefix matches, one per column, not a date comparison.
    // The page's formatDateInput() reformats the digits into YYYY-MM-DD as the
    // user types, and checkTypeIn() refuses anything shorter.
    columns: ['year_of_birth', 'month_of_birth', 'date_of_birth'],
    seedValue: (row) => `${row[0]}-${row[1]}-${row[2]}`,
    predicate: (alias, value) => {
      const [year, month, day] = String(value).split('-');
      return `${alias}.year_of_birth LIKE ${sqlString(`${year}%`)}`
        + ` AND ${alias}.month_of_birth LIKE ${sqlString(`${month}%`)}`
        + ` AND ${alias}.date_of_birth LIKE ${sqlString(`${day}%`)}`;
    },
  },
];

/** The app's definition of "active", as SQL for one table alias. */
function activePredicate(alias, inactiveStatuses) {
  const list = inactiveStatuses.map((status) => sqlString(status)).join(', ');
  return `${alias}.patient_status NOT IN (${list})`;
}

/**
 * Refuse to run where the page applies a restriction this oracle does not model.
 *
 * demographicsearchresults.jsp:352-360 passes `providerNo` and `outOfDomain`
 * into every DAO call, and the DAO adds PROGRAM_DOMAIN_RESTRICTION when
 * `providerNo != null && !outOfDomain`. `outOfDomain` starts true and is only
 * turned off when the Caisi module is loaded AND
 * pmm.client.search.outside.of.domain.enabled is not "true".
 *
 * On such a deployment the page shows a SUBSET of what this oracle expects, and
 * the check would report "the application hid a patient it should have shown"
 * -- a confident, specific, wrong verdict about a working search.
 *
 * A skip that names the condition beats modelling a program-domain join that
 * cannot be verified without exactly that deployment to run it on. Adding the
 * restriction blind would be trading a visible false failure for an invisible
 * false pass.
 */
function assertDomainRestrictionInactive(sql) {
  const modules = sql.value("SELECT value FROM property WHERE name = 'ModuleNames' LIMIT 1") || '';
  if (!/caisi/i.test(modules)) {
    return;
  }
  const outside = sql.value(
    "SELECT value FROM property WHERE name = 'pmm.client.search.outside.of.domain.enabled' LIMIT 1",
  );
  // The JSP's own default for the property is "true", so only an explicit
  // non-true value narrows the search.
  if (outside !== null && outside !== '' && String(outside).trim().toLowerCase() !== 'true') {
    throw new SkipCheck(
      'the Caisi module is loaded and pmm.client.search.outside.of.domain.enabled is not true, so the search '
      + 'page restricts results to the provider\'s program domain. This check\'s SQL oracle does not model that '
      + 'restriction and would report working behaviour as a missing patient.',
    );
  }
}

/**
 * Find a value a clinician could type that matches between 1 and 10 patients.
 *
 * The upper bound is not arbitrary: the form posts limit2=10, so a seed matching
 * more than a page would make "everything that matches is shown" false for a
 * reason that is not a defect. The candidate window keeps the correlated count
 * from scanning the whole table once per row.
 */
function seedFor(sql, mode, inactive) {
  const notBlank = mode.columns.map((column) => `c.${column} <> ''`).join(' AND ');
  const candidates = sql.rows(
    `SELECT ${mode.columns.map((column) => `d.${column}`).join(', ')} FROM ( `
    + `SELECT * FROM demographic c `
    + `WHERE ${notBlank} AND ${activePredicate('c', inactive)} AND c.patient_status <> 'MERGED' `
    + `ORDER BY c.demographic_no LIMIT ${SEED_WINDOW}) d`,
  );
  for (const row of candidates) {
    const value = mode.seedValue(row);
    if (!String(value).trim()) {
      continue;
    }
    const matches = sql.rows(
      `SELECT x.demographic_no FROM demographic x `
      + `WHERE ${mode.predicate('x', value)} AND ${activePredicate('x', inactive)}`,
    ).map((match) => match[0]);
    if (matches.length >= 1 && matches.length <= PAGE_LIMIT) {
      return { value, expected: matches.sort() };
    }
  }
  return null;
}

/** The search form, wherever we are -- the results page re-includes it. */
async function searchForm(page, timeout) {
  const form = page.locator('form[name="titlesearch"]').first();
  await form.waitFor({ state: 'attached', timeout });
  const keyword = form.locator('#keyword, input[name="keyword"]').first();
  if (!await keyword.isVisible().catch(() => false)) {
    // demographicsearchresults.jsp hides the form behind its own Search toggle
    // on some layouts; click the toggle rather than forcing a hidden fill.
    await page.locator('#searchPopUpButton').first().click({ timeout }).catch(() => {});
    await keyword.waitFor({ state: 'visible', timeout });
  }
  return { form, keyword };
}

/** Demographic numbers the results table is currently showing, as strings. */
async function shownDemographicNumbers(page) {
  // The row's link TEXT is the patient's own number; its onclick carries the
  // HEAD record instead when the patient was merged, so the text is the honest
  // reading of "which patient is this row".
  const texts = await page.locator('a[title="Master Demographic File"]').allTextContents();
  return texts.map((text) => text.trim()).filter((text) => /^\d+$/.test(text)).sort();
}

/** Type a search the way a user types it and wait for the results page. */
async function runSearch(page, mode, value, timeout, submit = 'active') {
  const { form, keyword } = await searchForm(page, timeout);
  await form.locator('select[name="search_mode"]').first().selectOption(mode);
  await keyword.fill('');
  // Typed, not filled: the date-of-birth box reformats on every input event,
  // and the barcode branch of checkTypeIn() reads what the box contains.
  await keyword.type(String(value), { delay: 15 });
  const control = submit === 'active'
    ? form.locator('input[type="submit"]').first()
    : form.locator(`input[type="button"][onclick^="search${submit}"]`).first();
  await clickAndAwaitReload(page, control, { timeout, label: `the ${mode} search` });
}

/** One mode: search it, then check the result set in both directions. */
async function checkMode(page, sql, mode, inactive, timeout) {
  const seed = seedFor(sql, mode, inactive);
  if (!seed) {
    throw new SkipCheck(
      `no ${mode.title} in this dataset matches between 1 and ${PAGE_LIMIT} active patients, `
      + 'so a result set could not be compared against SQL',
    );
  }

  await runSearch(page, mode.name, seed.value, timeout);
  const shown = await shownDemographicNumbers(page);

  // Direction 1: nothing is shown that does not match. This is the privacy
  // direction -- an over-matching search puts one patient's record in front of
  // a clinician who asked about another.
  const extra = shown.filter((number) => !seed.expected.includes(number));
  assert(extra.length === 0,
    `Searching by ${mode.title} returned ${extra.length} patient(s) that do not match what was typed `
    + `(demographic ${extra.join(', ')}). The mode's rule is ${mode.predicate('d', seed.value)}.`);

  // Direction 2: everything that matches is shown. This is the direction a
  // "does the page render" check passes straight through.
  const missing = seed.expected.filter((number) => !shown.includes(number));
  assert(missing.length === 0,
    `Searching by ${mode.title} did not show ${missing.length} patient(s) it should have `
    + `(demographic ${missing.join(', ')}). The one legitimate cause is the Caisi program-domain `
    + 'restriction, which narrows results to the provider\'s own programs; anything else is a defect.');

  return { mode: mode.name, matched: shown.length };
}

/**
 * The active / inactive / all contract, which lives entirely in JavaScript.
 *
 * searchInactive() and searchAll() rewrite the hidden ptstatus field and submit;
 * there is no separate route and no separate form. If either handler breaks, the
 * button silently performs an ordinary active search, and a clinician looking
 * for a discharged patient is told they do not exist.
 */
async function checkStatusScope(page, sql, inactive, timeout) {
  const list = inactive.map((status) => sqlString(status)).join(', ');
  const row = sql.rows(
    `SELECT demographic_no FROM demographic `
    + `WHERE patient_status IN (${list}) ORDER BY demographic_no LIMIT 1`,
  );
  if (!row.length) {
    throw new SkipCheck('this dataset has no inactive patient, so the active/inactive scope cannot be checked');
  }
  const [number] = row[0];

  // Searched by demographic number, not by name: that mode is an exact match, so
  // the patient either is or is not in the result set. A name prefix can match
  // more than the page's ten rows, which would make "absent" ambiguous between
  // the status filter working and the patient being on page two.
  await runSearch(page, 'search_demographic_no', number, timeout, 'active');
  assert(!(await shownDemographicNumbers(page)).includes(number),
    `The default (active) search showed demographic ${number}, whose patient_status is inactive`);

  await runSearch(page, 'search_demographic_no', number, timeout, 'Inactive');
  assert((await shownDemographicNumbers(page)).includes(number),
    `The Inactive button did not show demographic ${number}, which is inactive. `
    + 'searchInactive() sets the hidden ptstatus field to "inactive" before submitting; '
    + 'if that handler is broken the button performs an ordinary active search.');

  await runSearch(page, 'search_demographic_no', number, timeout, 'All');
  assert((await shownDemographicNumbers(page)).includes(number),
    `The All button did not show demographic ${number}. searchAll() clears the hidden ptstatus field.`);

  return { inactiveDemographic: number };
}

/**
 * A short date of birth is refused in the browser, before any request is made.
 *
 * This is the one validation on this form that is client-only, so nothing
 * server-side would catch its loss. Without it a stray "2020" becomes a
 * year-prefix search that returns every patient born that year.
 */
async function checkDobValidation(page, timeout) {
  const before = page.url();
  // Through the strict wiring's ONE handler, not a second listener. Playwright
  // delivers a dialog to every listener, so adding one alongside the strict
  // handler does not replace it: the alert would still be recorded as an
  // unexpected dialog and this check would fail its own assertStrictPage()
  // precisely when the validation works. See withExpectedDialogs().
  const dialogs = await withExpectedDialogs(page, async () => {
    const { form, keyword } = await searchForm(page, timeout);
    await form.locator('select[name="search_mode"]').first().selectOption('search_dob');
    await keyword.fill('');
    await keyword.type('2020', { delay: 15 });
    await form.locator('input[type="submit"]').first().click({ timeout });
    // No navigation is the assertion, so there is nothing to wait FOR. Give the
    // handler a beat and then check the page did not move.
    await page.waitForTimeout(750);
  });

  assert(dialogs.length > 0,
    'Typing a four-digit date of birth was accepted without complaint. checkTypeIn() is supposed to '
    + 'refuse anything that is not YYYY-MM-DD; without it "2020" becomes a year-prefix search that '
    + 'returns every patient born that year.');
  assert(dialogs[0].type === 'alert', `Expected an alert() for a short date of birth, saw ${dialogs[0].type}`);
  assert(page.url() === before,
    'The form submitted anyway after warning about the date format, so the warning is cosmetic');
  return { dialog: dialogs[0].type };
}

async function main() {
  const config = readConfig({ require: ['MYSQL_PASSWORD'] });
  const timeout = Number(process.env.SEARCH_TIMEOUT_MS || '20000');
  const selected = (process.env.SEARCH_MODES || '').split(',').map((name) => name.trim()).filter(Boolean);
  const inactive = (process.env.SEARCH_INACTIVE_STATUSES || '')
    .split(',').map((status) => status.trim()).filter(Boolean);
  const inactiveStatuses = inactive.length ? inactive : DEFAULT_INACTIVE_STATUSES;

  const modes = selected.length
    ? selected.map((name) => {
      const mode = MODES.find((candidate) => candidate.name === name);
      // A failure, not a skip: the name comes from the suite manifest, so a
      // typo would report SKIP forever and the mode would stop being checked.
      assert(mode, `SEARCH_MODES names ${name}, which is not a search mode this page offers`);
      return mode;
    })
    : MODES;

  const sql = createSqlRunner(config.mysql);
  assertDomainRestrictionInactive(sql);
  const recorder = createRecorder();
  const browser = await launchBrowser(config);
  try {
    const context = await newContext(browser, config);
    const schedulePage = await login(context, config, recorder);

    // The Search control is a same-tab href under the caisi module and a
    // popupPage2 otherwise, so take whichever this deployment does.
    const { page } = await clickOpensPopupOrNavigates(
      schedulePage, schedulePage.locator('#search a').first(),
      {
        context, label: 'patient-search', recorder, timeout,
      },
    );

    const results = [];
    const skipped = [];
    for (const mode of modes) {
      try {
        results.push(await checkMode(page, sql, mode, inactiveStatuses, timeout));
      } catch (error) {
        if (error instanceof SkipCheck) {
          skipped.push(`${mode.name}: ${error.message}`);
          continue;
        }
        throw error;
      }
    }
    assert(results.length > 0,
      `No search mode could be checked against this dataset:\n    - ${skipped.join('\n    - ')}`);

    let scope = null;
    try {
      scope = await checkStatusScope(page, sql, inactiveStatuses, timeout);
    } catch (error) {
      if (!(error instanceof SkipCheck)) {
        throw error;
      }
      skipped.push(`status scope: ${error.message}`);
    }
    const dob = await checkDobValidation(page, timeout);

    assertStrictPage(recorder);
    for (const line of skipped) {
      console.log(`  skipped ${line}`);
    }
    console.log(`  checked ${results.length} search mode(s) against the database`);
    return { modes: results, scope, dob };
  } finally {
    sql.dispose();
    await browser.close().catch(() => {});
  }
}

if (require.main === module) {
  runCheck({ name: 'patient-search-modes', run: main });
}

module.exports = {
  DEFAULT_INACTIVE_STATUSES, MODES, PAGE_LIMIT, activePredicate, assertDomainRestrictionInactive, main,
  seedFor, shownDemographicNumbers,
};
