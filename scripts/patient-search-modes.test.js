/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const {
  DEFAULT_INACTIVE_STATUSES, MODES, PAGE_LIMIT, activePredicate,
} = require('./patient-search-modes-playwright-checks');

const SOURCE = fs.readFileSync(path.join(__dirname, 'patient-search-modes-playwright-checks.js'), 'utf8');
const DAO = fs.readFileSync(path.join(
  __dirname, '..', 'src', 'main', 'java', 'io', 'github', 'carlos_emr', 'carlos',
  'commn', 'dao', 'DemographicDaoImpl.java',
), 'utf8');
const RESULTS_JSP = fs.readFileSync(path.join(
  __dirname, '..', 'src', 'main', 'webapp', 'WEB-INF', 'jsp', 'demographic', 'demographicsearchresults.jsp',
), 'utf8');
const FORM_JSP = fs.readFileSync(path.join(
  __dirname, '..', 'src', 'main', 'webapp', 'WEB-INF', 'jsp', 'demographic', 'zdemographicfulltitlesearch.jsp',
), 'utf8');

/*
 * The check compares the rendered result set against SQL it writes itself, so
 * that SQL IS the assertion: if a predicate here drifts from the DAO's query,
 * the check silently starts comparing the app against the wrong thing and can
 * fail on a working search or pass on a broken one. These tests pin each
 * predicate to the DAO line it was transcribed from, and the mode list to the
 * <option> values the form actually offers.
 */

test('every mode the form offers is checked, and no mode that it does not', () => {
  const offered = [...FORM_JSP.matchAll(/<option value="(search_[a-z_]+)"/g)].map((found) => found[1]);
  assert.ok(offered.length > 0, 'the search form must offer modes');
  const checked = MODES.map((mode) => mode.name);
  for (const mode of checked) {
    assert.ok(offered.includes(mode), `${mode} is checked but the form does not offer it`);
  }
  // search_band_number is behind an oscarPropertiesCheck, so it is the one
  // offered mode that a standard deployment does not render.
  const unchecked = offered.filter((mode) => !checked.includes(mode));
  assert.deepEqual(unchecked, ['search_band_number'],
    `these modes are offered but unchecked: ${unchecked.join(', ')}`);
});

test('the name predicate is a PREFIX match on the surname, as the DAO writes it', () => {
  assert.match(DAO, /d\.lastName like :lastName/);
  assert.match(DAO, /setParameter\("lastName", name\[0\]\.trim\(\) \+ "%"\)/);
  const sql = MODES.find((mode) => mode.name === 'search_name').predicate('d', 'fake-smith,jo');
  assert.match(sql, /d\.last_name LIKE 'fake-smith%'/);
  // The DAO also matches the alias on the given-name half.
  assert.match(DAO, /d\.firstName like :firstName or d\.alias like :firstName/);
  assert.match(sql, /d\.alias LIKE 'jo%'/);
});

test('the HIN predicate is EXACT: the DAO adds no wildcard to it', () => {
  // This is the one mode that does not wildcard, and getting it wrong in the
  // permissive direction would make the check tolerate an over-matching search
  // on the most sensitive identifier on the form.
  assert.match(DAO, /setParameter\("hin", hinStr\.trim\(\)\)/);
  const sql = MODES.find((mode) => mode.name === 'search_hin').predicate('d', '1234567890');
  assert.match(sql, /d\.hin LIKE '1234567890'/);
  assert.ok(!/hin LIKE '%/.test(sql), 'a HIN search must not be turned into a substring match');
  // It is also the only mode carrying the merged-record exclusion.
  assert.match(DAO, /d\.hin like :hin and d\.patientStatus != 'MERGED'/);
  assert.match(sql, /patient_status <> 'MERGED'/);
});

test('phone and address are SUBSTRING matches, and phone covers both numbers', () => {
  assert.match(DAO, /setParameter\("phone", "%" \+ phoneStr\.trim\(\) \+ "%"\)/);
  assert.match(DAO, /d\.phone like :phone OR d\.phone2 LIKE :phone/);
  const phone = MODES.find((mode) => mode.name === 'search_phone').predicate('d', '5550101');
  assert.match(phone, /d\.phone LIKE '%5550101%'/);
  assert.match(phone, /d\.phone2 LIKE '%5550101%/);

  assert.match(DAO, /setParameter\("address", "%" \+ addressStr\.trim\(\) \+ "%"\)/);
  assert.match(MODES.find((mode) => mode.name === 'search_address').predicate('d', 'Main'),
    /d\.address LIKE '%Main%'/);
});

test('chart number is a prefix match and demographic number is an equality', () => {
  assert.match(DAO, /setParameter\("chartNo", chartNoStr\.trim\(\) \+ "%"\)/);
  assert.match(MODES.find((mode) => mode.name === 'search_chart_no').predicate('d', 'AB1'),
    /d\.chart_no LIKE 'AB1%'/);

  assert.match(DAO, /d\.demographicNo = :demographicNo/);
  assert.match(MODES.find((mode) => mode.name === 'search_demographic_no').predicate('d', '42'),
    /d\.demographic_no = 42$/);
});

test('the demographic-number predicate refuses anything but digits', () => {
  // The value comes from our own database today, but a numeric column compared
  // against an unchecked string is the kind of thing that quietly becomes an
  // injection point the first time somebody wires an environment variable into
  // it. Coercing would be worse than refusing: Number('1 OR 1=1') is NaN, and
  // the run would fail with a MySQL syntax error instead of naming the value.
  const mode = MODES.find((candidate) => candidate.name === 'search_demographic_no');
  assert.throws(() => mode.predicate('d', "1 OR '1'='1"), /digits only/);
  assert.throws(() => mode.predicate('d', ''), /digits only/);
  assert.match(mode.predicate('d', ' 42 '), /d\.demographic_no = 42$/);
});

test('date of birth is three prefix matches on three columns, not a date compare', () => {
  assert.match(DAO, /d\.yearOfBirth like :yearOfBirth AND d\.monthOfBirth like :monthOfBirth AND d\.dateOfBirth like :dateOfBirth/);
  const sql = MODES.find((mode) => mode.name === 'search_dob').predicate('d', '1980-05-12');
  assert.match(sql, /year_of_birth LIKE '1980%'/);
  assert.match(sql, /month_of_birth LIKE '05%'/);
  assert.match(sql, /date_of_birth LIKE '12%'/);
});

test('"active" means what the application means by it', () => {
  // demographicsearchresults.jsp reads the inactive_statuses property with this
  // default; a check using a different list would be asserting a rule the app
  // does not follow.
  assert.match(RESULTS_JSP, /getProperty\("inactive_statuses", "IN, DE, IC, ID, MO, FI"\)/);
  assert.deepEqual(DEFAULT_INACTIVE_STATUSES, ['IN', 'DE', 'IC', 'ID', 'MO', 'FI']);
  const sql = activePredicate('d', DEFAULT_INACTIVE_STATUSES);
  assert.match(sql, /d\.patient_status NOT IN \('IN', 'DE', 'IC', 'ID', 'MO', 'FI'\)/);
});

test('the seed cap matches the row limit the form actually posts', () => {
  // A seed matching more than one page would make "everything that matches is
  // shown" false for a reason that is not a defect.
  assert.match(FORM_JSP, /NAME="limit2" VALUE="10"/);
  assert.equal(PAGE_LIMIT, 10);
});

test('the check asserts both directions, not just that something came back', () => {
  // The over-matching direction is the privacy one and is easy to leave out.
  assert.match(SOURCE, /do not match what was typed/);
  assert.match(SOURCE, /did not show \$\{missing\.length\} patient\(s\) it should have/);
});

test('the check writes nothing', () => {
  for (const statement of [/\bINSERT\s+INTO\b/i, /\bUPDATE\s+demographic\b/i, /\bDELETE\s+FROM\b/i]) {
    assert.ok(!statement.test(SOURCE), `patient search is read-only; found ${statement}`);
  }
  assert.ok(!/sql\.execute\(/.test(SOURCE), 'this check may only read');
});

test('the search is entered by clicking the schedule control, not by a URL', () => {
  assert.match(SOURCE, /schedulePage\.locator\('#search a'\)/);
  assert.ok(!/page\.goto\(/.test(SOURCE),
    'entering by address would skip the opener the whole suite exists to exercise');
});

test('the DOB validation alert is taken through the page\'s single dialog handler', () => {
  // Playwright delivers a dialog to EVERY listener. A second page.on('dialog')
  // added here meant the strict handler still recorded the alert as unexpected,
  // so assertStrictPage() failed precisely when the DOB validation worked --
  // a check that breaks on the behaviour it is asserting.
  assert.match(SOURCE, /await withExpectedDialogs\(page, async \(\) => \{/);
  assert.ok(!/page\.on\('dialog'/.test(SOURCE),
    'the check must borrow the wired handler, never add a second listener');
});

test('the phone oracle models all three numbers the DAO searches', () => {
  // DemographicDaoImpl.searchDemographicByPhone* UNIONS its phone/phone2 branch
  // with a demographicExt query on demo_cell. Modelling only phone and phone2
  // meant a patient whose CELL was the matching number came back from the UI
  // and was absent from the oracle -- reported as the application showing a
  // patient it should not have, which is the privacy direction and the most
  // serious verdict this check can reach. A false one, from a partial model.
  const phone = MODES.find((mode) => mode.name === 'search_phone');
  const sql = phone.predicate('x', '5551234');
  assert.match(sql, /x\.phone LIKE '%5551234%'/);
  assert.match(sql, /x\.phone2 LIKE '%5551234%'/);
  assert.match(sql, /demographicExt/);
  assert.match(sql, /dext\.key_val = 'demo_cell'/);
  assert.match(sql, /dext\.value LIKE '%5551234%'/);
  // The MERGED exclusion belongs to the extension branch only. That asymmetry
  // is the DAO's; reproducing it is the point, so it must not migrate to the
  // phone/phone2 half.
  assert.match(sql, /x\.patient_status <> 'MERGED' AND EXISTS/);
});
