/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const {
  DEFAULT_INACTIVE_STATUSES, MODES, PAGE_LIMIT, activePredicate, seedFor,
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

test('the HIN predicate matches the overload the SEARCH PAGE calls, not the other one', () => {
  // DemographicDaoImpl carries two HIN searches and they disagree:
  //
  //   searchDemographicByHIN(String)                -> hin like :hin
  //                                                    + patientStatus != 'MERGED'
  //                                                    + setParameter("hin", hinStr.trim())      EXACT
  //   searchDemographicByHIN(String, int, int, ...) -> hin like :hin (+ statuses, program domain)
  //                                                    + setParameter("hin", trim() + "%")       PREFIX
  //
  // demographicsearchresults.jsp:583 calls the SECOND. The first is reached
  // only by HRM report matching, which no browser route touches.
  //
  // This test previously asserted the first one's text, found it in the file,
  // and passed -- while the oracle modelled the first one's semantics. Both
  // were self-consistently wrong about which code the browser runs, so pin the
  // caller as well as the query.
  assert.match(RESULTS_JSP, /searchDemographicByHIN\(keyword, limit, offset, orderBy, providerNo, outOfDomain\)/);
  assert.match(DAO, /setParameter\("hin", hinStr\.trim\(\) \+ "%"\)/);

  const sql = MODES.find((mode) => mode.name === 'search_hin').predicate('d', '1234567890');
  assert.match(sql, /d\.hin LIKE '1234567890%'/);
  // Prefix, never substring: a leading wildcard would let the check tolerate an
  // over-matching search on the most sensitive identifier on the form.
  assert.ok(!/hin LIKE '%/.test(sql), 'a HIN search must not be turned into a substring match');
  // And no MERGED clause: the overload the page calls accepts an ignoreMerged
  // argument and never reads it, so there is nothing there to model.
  assert.ok(!/MERGED/.test(sql),
    'the paged HIN overload has no merged exclusion; adding one would hide merged records the page shows');
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

test('date of birth is three LIKE matches on three columns, bound from the parsed keyword', () => {
  assert.match(DAO, /d\.yearOfBirth like :yearOfBirth AND d\.monthOfBirth like :monthOfBirth AND d\.dateOfBirth like :dateOfBirth/);
  // Issue #3956: the DAO binds DobSearchPattern's segments, not "<segment>%".
  assert.match(DAO, /setParameter\("monthOfBirth", dob\.get\(\)\.month\(\)\)/);
  const [full, yearMonth, anyMonth] = MODES.filter((mode) => mode.name === 'search_dob');
  assert.ok(full && yearMonth && anyMonth, 'full, year-month and wildcard DOB shapes must all be checked');

  const sql = full.predicate('d', '1980-05-12');
  assert.match(sql, /year_of_birth LIKE '1980'/);
  assert.match(sql, /month_of_birth LIKE '05'/);
  assert.match(sql, /date_of_birth LIKE '12'/);

  assert.equal(yearMonth.seedValue(['1980', '05']), '1980-05');
  assert.match(yearMonth.predicate('d', '1980-05'), /date_of_birth LIKE '%'/);

  assert.equal(anyMonth.seedValue(['1980', '12']), '1980-%-12');
  const wildcard = anyMonth.predicate('d', '1980-%-12');
  assert.match(wildcard, /month_of_birth LIKE '%'/);
  assert.match(wildcard, /date_of_birth LIKE '12'/);

  // One-digit parts are padded the way the DAO pads them.
  assert.match(full.predicate('d', '1980-5-2'), /month_of_birth LIKE '05' AND d\.date_of_birth LIKE '02'/);
  assert.throws(() => full.predicate('d', '198'), /DOB search grammar/);
});

test('the DOB validation check expects malformed input refused and a year search allowed', () => {
  // The inverse of the pre-#3956 rule, which refused "2020" outright.
  assert.match(SOURCE, /for \(const typed of \['198', '1980-13'\]\)/);
  assert.ok(!/keyword\.type\('2020'/.test(SOURCE), 'a bare year is a valid DOB search since issue #3956');
});

test('the DOB oracle rejects out-of-range and all-wildcard searches without echoing patient input', () => {
  const mode = MODES.find((candidate) => candidate.name === 'search_dob');
  for (const value of ['1980-00', '1980-13', '1980-01-00', '1980-01-32', '%', '%-%-%']) {
    assert.throws(() => mode.predicate('d', value), (error) => {
      assert.ok(!error.message.includes(value));
      return /DOB search grammar/.test(error.message);
    });
  }
});

test('the DOB oracle accepts the parser trailing separator and boundary values', () => {
  const mode = MODES.find((candidate) => candidate.name === 'search_dob');
  assert.equal(mode.predicate('d', '1980-'), mode.predicate('d', '1980'));
  assert.equal(mode.predicate('d', '1980-12-'), mode.predicate('d', '1980-12'));
  assert.match(mode.predicate('d', '%-12-31'), /month_of_birth LIKE '12' AND d\.date_of_birth LIKE '31'/);
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

test('no failure message carries a typed value or a patient identifier', () => {
  // runCheck() writes a thrown message to stdout and into RESULT_JSON. The
  // material here is the worst kind to put there: the typed value IS the PHI
  // (a health number, a phone number, an address, a date of birth) and the
  // rendered predicate carries it, while demographic numbers join straight back
  // to those patients. CLAUDE.md: a diagnostic names the field, never content.
  // A window after each assert( rather than a balanced-paren parse: this is a
  // lint, and a window that overshoots into the next statement only makes it
  // stricter, never laxer.
  const assertions = [...SOURCE.matchAll(/\bassert\(/g)]
    .map((match) => SOURCE.slice(match.index, match.index + 400));
  assert.ok(assertions.length > 3, 'the assertions must actually be found');
  for (const body of assertions) {
    assert.ok(!/mode\.predicate\(/.test(body),
      `an assertion message renders the predicate, which embeds the typed value: ${body.slice(0, 80)}`);
    assert.ok(!/\$\{(extra|missing)\.join/.test(body),
      `an assertion message lists demographic numbers: ${body.slice(0, 80)}`);
    assert.ok(!/demographic \$\{number\}|\$\{seed\.value\}/.test(body),
      `an assertion message carries an identifier or the typed value: ${body.slice(0, 80)}`);
  }
});

test('the value main() returns carries no patient identifier', () => {
  // Corrected: runCheck() archives { name, outcome, detail, durationMs }, not
  // the return value. The rule stands anyway -- the returned object is logged
  // and passed on, and a demographic number in it buys nothing.
  assert.ok(!/inactiveDemographic/.test(SOURCE),
    'the status-scope result must not return the demographic number it searched for');
  assert.match(SOURCE, /return \{ statusScopeChecked: true \}/);
});

test('the option file is removed when the run cannot even start', () => {
  // createSqlRunner writes MYSQL_PASSWORD to a 0600 temp file and only
  // dispose() removes it, so anything that throws between there and the
  // try/finally -- the domain-restriction skip, a browser that will not launch
  // -- would leave that file behind with nothing running to collect it.
  const guard = SOURCE.slice(SOURCE.indexOf('const sql = createSqlRunner'), SOURCE.indexOf('const context = await newContext'));
  assert.match(guard, /catch \(error\) \{\s*\n\s*sql\.dispose\(\);\s*\n\s*throw error;/);
  const disposeIndex = guard.indexOf('sql.dispose()');
  const launchIndex = guard.indexOf('await launchBrowser(config)');
  const skipIndex = guard.indexOf('assertDomainRestrictionInactive(sql)');
  assert.ok(launchIndex > -1 && skipIndex > -1 && disposeIndex > launchIndex && disposeIndex > skipIndex,
    'both the skip and the browser launch must be inside the cleanup boundary');
});

test('the digits-only guard names the shape of a bad value, never the value', () => {
  // The value reaching this guard comes from the check's own SELECT over
  // demographic_no, so echoing it puts a patient-joining identifier into stdout
  // and RESULT_JSON in exchange for telling the reader something one query
  // would re-derive.
  const mode = MODES.find((candidate) => candidate.name === 'search_demographic_no');
  let message = '';
  try {
    mode.predicate('d', "1 OR '1'='1");
  } catch (error) {
    message = error.message;
  }
  assert.match(message, /digits only/);
  assert.ok(!message.includes("1 OR '1'='1"), `the rejected value was echoed: ${message}`);
  assert.match(message, /\d+ character\(s\)/);
});

/*
 * seedFor() is EXECUTED here, not read.
 *
 * The merge-tail predicate was declared with `const` AFTER the candidate query
 * that uses it. `const` is not hoisted into scope, so every call died with
 * "Cannot access 'notAMergedTail' before initialization" before a single search
 * mode ran -- a check that could never pass, shipped green because nothing in
 * the suite called the function.
 *
 * That is the same class as the assertStrictPage ReferenceError earlier on this
 * branch, and check-script-imports.test.js does NOT cover it: that sweep looks
 * for shared-library names missing from an import list, and this name is local
 * and correctly spelled. The only thing that catches a temporal-dead-zone use
 * is running the code, so these drive seedFor with a fake sql runner.
 */
function fakeSql(rowsByQuery = []) {
  const queries = [];
  let call = 0;
  return {
    queries,
    rows(sql) {
      queries.push(sql);
      const result = rowsByQuery[call] || [];
      call += 1;
      return result;
    },
  };
}

const HIN_MODE = {
  columns: ['hin'],
  seedValue: (row) => row[0],
  predicate: (alias, value) => `${alias}.hin LIKE '${value}%'`,
};

test('seedFor runs at all, rather than throwing before the first query', () => {
  const sql = fakeSql();
  assert.doesNotThrow(() => seedFor(sql, HIN_MODE, ['IN']));
  assert.equal(sql.queries.length, 1, 'the candidate query must actually have been built');
});

test('both oracle queries exclude a current merged tail, as the results page does', () => {
  // demographicsearchresults.jsp:416-428 skips any record whose
  // DemographicMerged.getHead() is not itself, so a merged tail is never
  // rendered however well it matches. Without the same filter the oracle
  // expected a row the UI correctly withholds, and the check reported an
  // application defect that was its own model being wrong.
  const sql = fakeSql([[['1234567890']], []]);
  seedFor(sql, HIN_MODE, ['IN']);
  assert.equal(sql.queries.length, 2, 'the candidate query and the expected-set query');
  for (const query of sql.queries) {
    assert.match(query, /NOT EXISTS \(SELECT 1 FROM demographic_merged dm/);
    assert.match(query, /dm\.deleted = 0/);
    assert.match(query, /dm\.merged_to <> /);
  }
});
