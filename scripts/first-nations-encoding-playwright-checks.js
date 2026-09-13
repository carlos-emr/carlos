#!/usr/bin/env node
/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Browser regression check for the stored-XSS path in
 * manageFirstNationsModule.jsp (PR #2430).
 *
 * The JSP rendered five demographicExt-backed values straight into HTML
 * `value="..."` attributes. A stored value containing a double quote therefore
 * closed the attribute and everything after it became markup: any user who
 * could write a patient's First Nations fields could plant script that ran in
 * the next clinician's session. The fix routes all five through
 * `${carlos:forHtmlAttribute(...)}`.
 *
 * A JSP asset test (ManageFirstNationsModuleAssetRegressionTest) pins the
 * source text. That is not the same claim as this one: the asset test cannot
 * see whether the taglib is wired, whether the EL function resolves at runtime,
 * or what the browser actually parses. This check seeds an attribute-breaking
 * payload into demographicExt, renders the page through the real front door,
 * and asserts against the parsed DOM -- the input's `value` PROPERTY must equal
 * the payload byte for byte (the browser only reports that if the quote was
 * encoded), and no element from the payload may exist in the document. On the
 * unfixed JSP the value property is truncated at the quote and the injected
 * <img> is a live node, so both halves fail.
 *
 * Two render paths are covered:
 *   1. The gate route /demographic/ViewManageFirstNationsModule -- always
 *      available, always asserted.
 *   2. The patient master record, reached the way an operator reaches it
 *      (patient search by demographic number, then the result link). The
 *      module is only included there when FIRST_NATIONS_MODULE=true in
 *      carlos.properties; when it is false the script says so and asserts the
 *      gate path only, rather than failing on a configuration choice.
 *
 * The `<option value>` half of the same fix (firstNationCommunity.value) needs
 * two things to be true, and says so in its PASS line when either is not:
 *   - this install has no `firstNationCommunity` lookup list yet, because
 *     LookupListDao.findByName is @Cacheable and a list seeded behind a warm
 *     cache would not reach the page (a null result is not cached, so on a
 *     fresh install the seed below is visible), and
 *   - the community <select> is actually rendered, which it is not when
 *     showBandNumberOnly is active in carlos.properties.
 *
 * Everything it writes -- the demographicExt rows and, when it seeds them, the
 * LookupList/LookupListItem rows -- is restored or deleted in the finally, and
 * also from the signal handlers, since the runbook drives checks under
 * `timeout --foreground`.
 *
 * KNOWN LIMIT, cleanup side of that same cache. LookupListDaoImpl evicts
 * CacheConfig.LOOKUP_LISTS only from persist/merge/remove; this fixture deletes
 * the seeded list with direct SQL, which bypasses all three. So a CARLOS
 * instance that rendered the page during the run keeps the deleted list cached
 * until a lookup-list write or cache expiry. Consequences to expect, none of
 * which the fixture can fix from SQL:
 *   - a second run inside that window sees no list in the database, seeds a new
 *     one, and can then mis-report or skip the community half, and
 *   - the stale list -- including the payload option -- can still be served to
 *     the browser after this check reports PASS.
 * `carlos-ctl restart` clears it. Prefer one run per application start when the
 * community half matters; the demographicExt half is unaffected, as those
 * values are not cached.
 *
 * Requires the deb-install env contract (docs/ui-tests/deb-install-validation.md §6):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE (to seed and restore the payload)
 * Optional: FIRST_NATIONS_DEMOGRAPHIC_NO (default: lowest demographic_no in the
 *   database), CHROME_PATH, FIRST_NATIONS_SCREENSHOT_DIR (default /tmp).
 */

const { chromium } = require('playwright');
const { execFileSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const {
  assert,
  assertNoPageErrors,
  assertNotErrorPage,
  buildFailureDetails,
  createRecorder,
  getLaunchOptions,
  gotoApp,
  login,
  screenshot,
  validateBaseUrl,
  validateMysqlHost,
  wirePage,
} = require('./eform-local-playwright-utils');

const config = {
  baseUrl: validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos'),
  chromePath: process.env.CHROME_PATH || '',
  testUser: process.env.TEST_USER || 'carlosdoc',
  testPassword: process.env.TEST_PASSWORD || 'carlos2026',
  testPin: process.env.TEST_PIN || '2026',
  resetPassword: process.env.RESET_PASSWORD || '',
  screenshotDir: process.env.FIRST_NATIONS_SCREENSHOT_DIR || '/tmp',
};

const mysqlHost = validateMysqlHost(process.env.MYSQL_HOST || '127.0.0.1');
const mysqlUser = process.env.MYSQL_USER || 'root';
const mysqlPassword = process.env.MYSQL_PASSWORD || 'password';
const mysqlDatabase = process.env.MYSQL_DATABASE || 'carlos';

const LOOKUP_LIST_NAME = 'firstNationCommunity';
// LookupList has no unique constraint on `name` (PRIMARY KEY (id) only), so a
// check-then-insert cannot assume the row it finds afterwards is its own: two
// runs seeding at once both succeed and `WHERE name=...` then matches both.
// Stamp a per-run marker into the description column and resolve by that, so
// each run identifies and removes exactly the list it created.
const RUN_MARKER = `first-nations-encoding-playwright-checks.js run ${process.pid}@${Date.now()}`;

// One payload shape per field, each carrying its own id so a rendered element
// names the field that leaked it. The leading `">` is the whole point: it is
// inert inside an encoded attribute and closes the tag in an unencoded one.
// LookupListItem.value is varchar(50), so the community payload stays short.
const PAYLOADS = {
  statusNum: '"><img src=x id=fnxss-statusnum>',
  fNationFamilyNumber: '"><img src=x id=fnxss-familynumber>',
  fNationFamilyPosition: '"><img src=x id=fnxss-familyposition>',
  ethnicity: '"><img src=x id=fnxss-ethnicity>',
};
const COMMUNITY_PAYLOAD = '"><img src=x id=fnxss-community>';
const SEEDED_KEYS = Object.keys(PAYLOADS);

let mysqlDefaults = null;
function initMysqlDefaults() {
  if (/[\r\n]/.test(mysqlPassword)) {
    throw new Error('MYSQL_PASSWORD must not contain newline characters');
  }
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'first-nations-'));
  const file = path.join(dir, 'mysql-defaults.cnf');
  // Register before writing: a failed write can still have created the file,
  // and cleanupMysqlDefaults() can only remove a directory it knows about.
  // Leaving one behind strands MYSQL_PASSWORD in cleartext under /tmp.
  mysqlDefaults = { dir, file };
  try {
    fs.writeFileSync(file, `[client]\npassword=${mysqlPassword}\n`, { mode: 0o600 });
  } catch (writeError) {
    // This function runs before the main try/finally, so nothing else would
    // remove the directory. Registering the path is not enough on its own --
    // cleanup has to actually run on this path too.
    cleanupMysqlDefaults();
    throw writeError;
  }
}
function cleanupMysqlDefaults() {
  if (mysqlDefaults) {
    fs.rmSync(mysqlDefaults.dir, { recursive: true, force: true });
    mysqlDefaults = null;
  }
}
function sql(query) {
  assert(mysqlDefaults, 'MySQL defaults file has not been initialized');
  return execFileSync('mysql', [
    `--defaults-extra-file=${mysqlDefaults.file}`,
    '-h', mysqlHost, '-u', mysqlUser, mysqlDatabase, '-N', '-B', '-e', query,
  ], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], timeout: 15000 }).trim();
}
/**
 * Same query, but only the final newline is stripped.
 *
 * `sql()` trims, which is right for a single scalar and wrong for a result set:
 * an empty VARCHAR comes back as an empty trailing FIELD (`key\tV\t`), and
 * trimming eats that tab along with the line ending. The row then parses one
 * field short, `hex` is undefined, and the restore writes UNHEX('undefined') --
 * SQL NULL -- over a value that was the empty string. Keep the tabs.
 */
function sqlRows(query) {
  assert(mysqlDefaults, 'MySQL defaults file has not been initialized');
  return execFileSync('mysql', [
    `--defaults-extra-file=${mysqlDefaults.file}`,
    '-h', mysqlHost, '-u', mysqlUser, mysqlDatabase, '-N', '-B', '-e', query,
  ], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], timeout: 15000 }).replace(/\n$/, '');
}
function sqlString(value) {
  return `'${String(value).replace(/\\/g, '\\\\').replace(/'/g, "''")}'`;
}

/**
 * Read the rows this check is about to overwrite, losslessly.
 *
 * `mysql -N -B` is a TEXT protocol and it is lossy in two ways that matter to a
 * check which promises to put the patient's record back exactly as it found it:
 * it renders a real tab as the two characters `\t` (likewise `\n` for a newline
 * and `\\` for a backslash), and it renders SQL NULL as the four characters
 * `NULL`, which no parser can tell from the literal string "NULL". Restoring
 * what comes back out of that would rewrite a tab as a backslash-t and a NULL as
 * the word NULL. Ask for HEX() and a separate null flag instead: both are
 * [0-9A-FN V] only, so the tab/newline field split below is unambiguous, and the
 * value goes back through UNHEX() byte for byte.
 */
function readOriginalExtRows(demographicNo, keys) {
  const rows = sqlRows(
    `SELECT key_val, IF(value IS NULL, 'N', 'V'), IFNULL(HEX(value), '')`
      + ` FROM demographicExt WHERE demographic_no=${demographicNo}`
      + ` AND key_val IN (${keys.map(sqlString).join(',')})`,
  );
  return rows.split('\n').filter(Boolean).map((line) => {
    const fields = line.split('\t');
    // Silent corruption is the whole hazard here, so refuse to guess: a row that
    // does not parse into exactly the three requested fields must stop the run
    // rather than restore something invented.
    assert(
      fields.length === 3 && (fields[1] === 'N' || fields[1] === 'V') && /^[0-9A-F]*$/.test(fields[2]),
      `Unparseable demographicExt capture row ${JSON.stringify(line)}; refusing to seed`,
    );
    const [key, nullFlag, hex] = fields;
    return { key, isNull: nullFlag === 'N', hex };
  });
}

/**
 * Assert the five seeded values survived the round trip intact and that the
 * payload produced no markup.
 *
 * The `value` PROPERTY (not the attribute text) is what proves the fix: the
 * browser exposes the full payload only when the quote inside it was encoded.
 * An unencoded render ends the attribute at the quote, so the property comes
 * back as `` (empty) and the rest of the payload becomes the <img> that the
 * second half of this function looks for.
 */
async function assertEncodedRender(page, label, { expectCommunity }) {
  await assertNotErrorPage(page, label);
  await page.locator('#statusNum').waitFor({ state: 'attached', timeout: 15000 });

  const fields = [
    ['#statusNum', PAYLOADS.statusNum],
    ['input[name="statusNumOrig"]', PAYLOADS.statusNum],
    ['#fNationFamilyNumber', PAYLOADS.fNationFamilyNumber],
    ['#fNationFamilyPosition', PAYLOADS.fNationFamilyPosition],
    ['input[name="ethnicityOrig"]', PAYLOADS.ethnicity],
  ];
  for (const [selector, expected] of fields) {
    const locator = page.locator(selector).first();
    assert(await locator.count() > 0, `[${label}] ${selector} did not render`);
    const actual = await locator.inputValue();
    assert(
      actual === expected,
      `[${label}] ${selector} value is ${JSON.stringify(actual)}, expected the stored value`
        + ` ${JSON.stringify(expected)} unchanged. A shorter value means the attribute was closed`
        + ' by the payload quote, i.e. the HTML-attribute encoding is gone.',
    );
  }

  // The community <select> is wrapped in a showBandNumberOnly property check, so
  // on an install configured band-number-only it is deliberately absent. Seeding
  // the lookup list says the DATA is there; only the rendered control says the
  // assertion is meaningful, and asserting on the seed alone would fail the
  // standard validation loop over a configuration choice.
  let communityAsserted = false;
  if (expectCommunity) {
    if (await page.locator('#fNationCom').count()) {
      const optionValues = await page.locator('#fNationCom option').evaluateAll(
        (nodes) => nodes.map((node) => node.value),
      );
      assert(
        optionValues.includes(COMMUNITY_PAYLOAD),
        `[${label}] no #fNationCom option carried the seeded community value intact;`
          + ` got ${JSON.stringify(optionValues)}`,
      );
      communityAsserted = true;
    } else {
      console.log(
        `NOTE [${label}] no #fNationCom control on this install`
          + ' (showBandNumberOnly is active in carlos.properties);'
          + ' skipped the community option assertion.',
      );
    }
  }

  // Nothing from any payload may exist as markup. getElementById is the direct
  // read; the attribute sweep catches a payload that landed on some other tag.
  const injected = await page.evaluate(() => {
    const ids = Array.from(document.querySelectorAll('[id^="fnxss-"]')).map((node) => node.id);
    const images = Array.from(document.querySelectorAll('img'))
      .filter((node) => node.getAttribute('src') === 'x')
      .map((node) => node.outerHTML);
    return { ids, images };
  });
  assert(
    injected.ids.length === 0 && injected.images.length === 0,
    `[${label}] the seeded payload was parsed as markup: ${JSON.stringify(injected)}`,
  );

  return { communityAsserted };
}

// Seeding state lives at module scope so the signal handlers below can put the
// patient's rows back. This check plants an attribute-breaking payload in a real
// demographic record; leaving it there is a worse outcome than any assertion
// failure, so every exit path has to run the restore.
let demographicNo = null;
let originalExt = null;
let seededLookupListId = null;
// Set before the INSERT, not after: if the process dies between the insert and
// the id SELECT, cleanup still knows a list of ours may exist and can find it
// by name. Safe because the seed only runs when no list of that name existed.
let lookupListSeedAttempted = false;
let restoreDone = false;

/**
 * Put back every row this check touched. Throws if anything could not be
 * restored -- callers must treat that as a failed run, never a warning.
 */
function restoreSeededRows() {
  if (restoreDone) {
    return;
  }
  if (demographicNo && originalExt) {
    const restored = new Map(originalExt.map((row) => [row.key, row]));
    for (const key of SEEDED_KEYS) {
      const row = restored.get(key);
      // Every write is conditional on the column still holding THIS run's
      // payload. Two overlapping runs would otherwise have the second restore
      // its snapshot of the first run's payload back over the first run's
      // cleanup, and an edit made by someone using the app mid-run would be
      // silently reverted. If the value is no longer ours, leaving it alone is
      // the correct outcome.
      const stillOurs = `AND value=${sqlString(PAYLOADS[key])}`;
      if (row) {
        // UNHEX('') is the empty string, not NULL, so the two cases stay
        // distinct all the way back into the column.
        const literal = row.isNull ? 'NULL' : `UNHEX(${sqlString(row.hex)})`;
        sql(
          `UPDATE demographicExt SET value=${literal}`
            + ` WHERE demographic_no=${demographicNo} AND key_val=${sqlString(key)} ${stillOurs}`,
        );
      } else {
        sql(
          `DELETE FROM demographicExt WHERE demographic_no=${demographicNo}`
            + ` AND key_val=${sqlString(key)} ${stillOurs}`,
        );
      }
    }
  }
  if (!seededLookupListId && lookupListSeedAttempted) {
    // The insert ran but the id never came back. Match on the run marker, not
    // the name: a concurrent run may have seeded a list of the same name, and
    // deleting that one would strand its payload instead of ours.
    const recovered = sql(
      `SELECT id FROM LookupList WHERE name=${sqlString(LOOKUP_LIST_NAME)}`
        + ` AND description=${sqlString(RUN_MARKER)}`,
    );
    if (/^\d+$/.test(recovered)) {
      seededLookupListId = recovered;
    }
  }
  if (seededLookupListId) {
    sql(`DELETE FROM LookupListItem WHERE lookupListId=${seededLookupListId}`);
    sql(`DELETE FROM LookupList WHERE id=${seededLookupListId}`);
    seededLookupListId = null;
    lookupListSeedAttempted = false;
  }
  // Only now. Setting this up front meant a restore that threw half way through
  // left the flag true, so the signal handler's retry returned immediately and
  // the remaining rows kept the payload.
  restoreDone = true;
}

// The deb-install runbook drives every check under `timeout --foreground`, and a
// SIGTERM would otherwise kill this process between seeding and the finally,
// stranding the payload in the record. Every sql() call is synchronous, so the
// restore completes inside the handler.
for (const signal of ['SIGINT', 'SIGTERM', 'SIGHUP']) {
  process.on(signal, () => {
    try {
      restoreSeededRows();
      console.error(`WARN received ${signal}; restored seeded First Nations rows before exiting.`);
    } catch (restoreError) {
      console.error(`FAIL received ${signal} and could NOT restore seeded First Nations rows: ${restoreError.message}`);
      console.error(`Restore by hand before using demographic ${demographicNo}: it still holds the test payload.`);
    } finally {
      cleanupMysqlDefaults();
      process.exit(1);
    }
  });
}

(async () => {
  const recorder = createRecorder();
  const browser = await chromium.launch(getLaunchOptions(config.chromePath));
  initMysqlDefaults();
  let coveredMasterRecord = false;
  let communityAsserted = false;
  try {
    demographicNo = process.env.FIRST_NATIONS_DEMOGRAPHIC_NO
      || sql('SELECT MIN(demographic_no) FROM demographic');
    assert(/^\d+$/.test(demographicNo), `No usable demographic_no (got ${JSON.stringify(demographicNo)})`);

    // Remember the patient's real First Nations values so the finally can put
    // them back: this runs against a demo dataset, not a scratch row.
    originalExt = readOriginalExtRows(demographicNo, SEEDED_KEYS);

    // Refuse to run against a record that already holds one of this check's
    // payloads. That means an earlier run died between seeding and restore, so
    // the "original" just captured IS the payload -- and because every restore
    // write is now conditional on the value still being ours, the run would
    // faithfully put the payload back and print PASS over a record that still
    // carries live XSS. Nothing here can know what the real value was, so the
    // only honest move is to stop and say which key needs repairing.
    const stale = originalExt.filter(
      (row) => !row.isNull && row.hex === Buffer.from(PAYLOADS[row.key], 'utf8').toString('hex').toUpperCase(),
    );
    assert(
      stale.length === 0,
      `demographic ${demographicNo} still holds this check's payload in `
        + `${stale.map((row) => row.key).join(', ')} -- an earlier run did not restore it. `
        + 'Repair those rows to their real values before re-running; seeding now would '
        + 'capture the payload as the original and report PASS while preserving it.',
    );

    for (const key of SEEDED_KEYS) {
      sql(
        'INSERT INTO demographicExt (demographic_no, provider_no, key_val, value, date_time, hidden)'
          + ` VALUES (${demographicNo}, '999998', ${sqlString(key)}, ${sqlString(PAYLOADS[key])}, NOW(), '0')`
          + ` ON DUPLICATE KEY UPDATE value=${sqlString(PAYLOADS[key])}`,
      );
    }

    // Only seed the community list when this install has none. See the header:
    // findByName caches a hit, so an existing list would not pick the item up.
    const existingList = sql(`SELECT id FROM LookupList WHERE name=${sqlString(LOOKUP_LIST_NAME)}`);
    if (!existingList) {
      lookupListSeedAttempted = true;
      sql(
        'INSERT INTO LookupList (name, listTitle, description, active, createdBy)'
          + ` VALUES (${sqlString(LOOKUP_LIST_NAME)}, 'First Nation Community',`
          + ` ${sqlString(RUN_MARKER)}, 1, '999998')`,
      );
      seededLookupListId = sql(
        `SELECT id FROM LookupList WHERE name=${sqlString(LOOKUP_LIST_NAME)}`
          + ` AND description=${sqlString(RUN_MARKER)}`,
      );
      assert(/^\d+$/.test(seededLookupListId), 'Failed to seed the firstNationCommunity lookup list');
      sql(
        'INSERT INTO LookupListItem (lookupListId, value, label, displayOrder, active, createdBy)'
          + ` VALUES (${seededLookupListId}, ${sqlString(COMMUNITY_PAYLOAD)}, 'Seeded community', 1, 1, '999998')`,
      );
    }
    const expectCommunity = Boolean(seededLookupListId);

    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1100 } });
    const schedulePage = await login(context, config, recorder);

    // Path 1: the gate route. Always present, so this is the assertion that
    // must hold on every install.
    const gatePage = await context.newPage();
    wirePage(gatePage, 'first-nations-gate', recorder);
    const gateResponse = await gotoApp(
      gatePage,
      config.baseUrl,
      `/demographic/ViewManageFirstNationsModule?demo=${encodeURIComponent(demographicNo)}`,
    );
    assert(gateResponse && gateResponse.ok(), `Gate route answered HTTP ${gateResponse && gateResponse.status()}`);
    await gatePage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    const gateResult = await assertEncodedRender(gatePage, 'first-nations-gate', { expectCommunity });
    communityAsserted = gateResult.communityAsserted;

    // The served bytes, not the parsed DOM: a render that escaped the quote but
    // left the angle brackets raw would still pass the DOM checks on a lenient
    // parser, and this catches it at the source.
    const rawHtml = await gateResponse.text();
    assert(
      !rawHtml.includes('"><img src=x'),
      'The gate route served the payload unencoded (raw `"><img src=x` is present in the response body)',
    );
    await screenshot(gatePage, config.screenshotDir, 'first-nations-gate');

    // Path 2: the master record, reached the way an operator reaches it.
    const searchPage = await context.newPage();
    wirePage(searchPage, 'patient-search', recorder);
    await gotoApp(searchPage, config.baseUrl, '/demographic/ViewSearch');
    await assertNotErrorPage(searchPage, 'patient-search');
    await searchPage.locator('#search_mode').selectOption('search_demographic_no');
    await searchPage.locator('#keyword').fill(String(demographicNo));
    await Promise.all([
      searchPage.waitForURL(/DemographicSearch/, { timeout: 30000 }),
      searchPage.locator('form[name="titlesearch"] input[type="submit"]').first().click(),
    ]);
    await searchPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await assertNotErrorPage(searchPage, 'patient-search-results');

    // Match the row by its exact link text, not by a substring of the onclick
    // URL: demographic_no=1 is a substring of demographic_no=11. Compare the
    // text in JS rather than building a RegExp out of the id -- a hardcoded
    // pattern is not possible here and a dynamic one is both needless and a
    // ReDoS smell, even with the digits-only assertion above.
    const candidateLinks = searchPage.locator('a[title="Master Demographic File"]');
    const linkTexts = await candidateLinks.evaluateAll(
      (nodes) => nodes.map((node) => (node.textContent || '').trim()),
    );
    const rowIndex = linkTexts.indexOf(String(demographicNo));
    assert(
      rowIndex >= 0,
      `The patient search returned no master-record link for demographic ${demographicNo};`
        + ` the results page listed ${JSON.stringify(linkTexts)}`,
    );
    const resultLink = candidateLinks.nth(rowIndex);
    const masterPopup = context.waitForEvent('page', { timeout: 20000 }).catch(() => null);
    await resultLink.click();
    const masterPage = await masterPopup;
    assert(masterPage, 'Clicking the search result opened no master-record window');
    wirePage(masterPage, 'master-record', recorder);
    await masterPage.waitForLoadState('networkidle', { timeout: 45000 }).catch(() => {});
    await assertNotErrorPage(masterPage, 'master-record');

    if (await masterPage.locator('#statusNum').count()) {
      const masterResult = await assertEncodedRender(masterPage, 'master-record', { expectCommunity });
      communityAsserted = communityAsserted || masterResult.communityAsserted;
      await screenshot(masterPage, config.screenshotDir, 'first-nations-master-record');
      coveredMasterRecord = true;
    } else {
      console.log(
        'NOTE master record carries no First Nations module on this install'
          + ' (FIRST_NATIONS_MODULE is not true in carlos.properties); asserted the gate route only.',
      );
    }

    assertNoPageErrors(recorder, ['first-nations-gate', 'master-record']);
    await schedulePage.close();
    await context.close();
    console.log(
      `PASS First Nations demographic values render HTML-attribute encoded for demographic ${demographicNo}`
        + ` (gate route${coveredMasterRecord ? ' + master record' : ''}`
        + `${communityAsserted ? ', community option included'
          : ', community option NOT covered: ' + (expectCommunity ? 'no #fNationCom control rendered' : 'lookup list already present')})`,
    );
  } catch (error) {
    console.error('FAIL First Nations demographic encoding Playwright check');
    console.error(error.stack || error.message);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    try {
      restoreSeededRows();
    } catch (restoreError) {
      // A failed restore leaves an XSS payload in a patient's record. The PASS
      // line may already be on stdout, so the exit code is the only thing left
      // that can stop a green run from hiding it.
      console.error(`FAIL could not restore seeded First Nations rows: ${restoreError.message}`);
      console.error(`Restore by hand before using demographic ${demographicNo}: it still holds the test payload.`);
      process.exitCode = 1;
    }
    cleanupMysqlDefaults();
    await browser.close();
  }
})();
