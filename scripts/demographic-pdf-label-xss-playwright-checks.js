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
 * Browser regression check for the demographic print-label page
 * (/demographic/DemographicPdfLabel -> demographicpdflabel.jsp).
 *
 * The page renders an entire patient demographic record -- name, address,
 * HIN, chart number, referral doctor, alert and notes -- and every one of
 * those values used to reach the browser through a raw `<%= %>` scriptlet.
 * Stored markup in any of them therefore executed in the clinician's session
 * when the label was opened. The fix routes them through the null-safe
 * `<carlos:encode>` tag in the HTML, HTML-attribute and JavaScript contexts.
 *
 * A file-level regression test (DemographicPdfLabelJspRegressionTest) pins the
 * tag usage in the JSP source; this check proves the deployed behaviour, which
 * source assertions cannot: that a patient record carrying markup renders the
 * markup as TEXT, executes nothing, and still produces a usable label.
 *
 * WHY THE FIXTURE IS SEEDED IN SQL. Demographic fields are the classic stored
 * sink: they arrive by HL7 import, by Integrator sync and by chart conversion,
 * none of which pass the WAF that guards the browser form. Seeding the row
 * directly is the faithful shape of the threat, and it is also the only way to
 * plant markup at all on a packaged install -- ModSecurity answers 403 to the
 * same payload typed into the add-patient form.
 *
 * Both branches of the referral-doctor cell are covered:
 *   - default config renders the family-doctor XML as HTML (the `rd` value);
 *   - with `isMRefDocSelectList=true` the page builds a `<select>` of
 *     specialists AND an inline `<script>` comparing their names, which is the
 *     one JavaScript-context sink on the page. The check exercises whichever
 *     branch the deployment is configured for, asserts the script path when it
 *     is present, and reports which branch it measured so a run that never saw
 *     the select cannot be mistaken for one that did.
 *
 * Requires the standard deb-install env contract (see
 * docs/ui-tests/deb-install-validation.md section 6):
 *   BASE_URL, TEST_USER, TEST_PASSWORD, TEST_PIN,
 *   MYSQL_HOST/USER/PASSWORD/DATABASE (fixture seed and cleanup)
 * Optional: CHROME_PATH, PDF_LABEL_XSS_SCREENSHOT_DIR (default /tmp).
 */

const { chromium } = require('playwright');
const { execFileSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const {
  assert,
  buildFailureDetails,
  createRecorder,
  getLaunchOptions,
  gotoApp,
  login,
  screenshot,
  validateBaseUrl,
  assertNotErrorPage,
} = require('./eform-local-playwright-utils');

const config = {
  baseUrl: validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos'),
  chromePath: process.env.CHROME_PATH || '',
  testUser: process.env.TEST_USER || 'carlosdoc',
  testPassword: process.env.TEST_PASSWORD || 'carlos2026',
  testPin: process.env.TEST_PIN || '2026',
  screenshotDir: process.env.PDF_LABEL_XSS_SCREENSHOT_DIR || '/tmp',
};
const mysqlHost = process.env.MYSQL_HOST || '127.0.0.1';
const mysqlUser = process.env.MYSQL_USER || 'root';
const mysqlPassword = process.env.MYSQL_PASSWORD || 'password';
const mysqlDatabase = process.env.MYSQL_DATABASE || 'carlos';
const mysqlClient = process.env.MYSQL_CLIENT || 'mysql';

// One marker per sink. If ANY of them executes, the page sets the global
// __cx to the marker's number, so a failure names the field that is still raw
// instead of only reporting "something ran".
const SINKS = {
  address: 1,
  alert: 2,
  notes: 3,
  referralDoctor: 4,
  specialist: 5,
};
// The marker global is terse (__cx, not window.__carlosXss) because it has to
// fit: professionalSpecialists.lName is varchar(32), and a longer payload is
// silently TRUNCATED by MariaDB -- which makes the page look like it mangled
// the value when in fact the fixture never stored it. seedFixture() reads every
// field back for the same reason.
const fire = (marker) => `<img src=x onerror=__cx=${marker}>`;

// Every payload is sized to the column it lands in: last_name 30, address 60,
// hin 20, chart_no 10, family_doctor 80, professionalSpecialists.fName/lName 32.
// FAKE- keeps the row identifiable as synthetic under the demo dataset's own
// naming convention.
const runId = Date.now().toString().slice(-8);
const fixture = {
  lastName: `FAKE-XSS-${runId}`,
  firstName: `"><b>F</b>`,
  address: `">${fire(SINKS.address)}`,
  hin: `<i>HIN</i>`,
  chartNo: `<u>CH</u>`,
  alert: `</td></table>${fire(SINKS.alert)}`,
  notes: `<unotes>${fire(SINKS.notes)}</unotes>`,
  familyDoctor: `<rd>${fire(SINKS.referralDoctor)}</rd><rdohip>9<img></rdohip>`,
  specialistLastName: `"${fire(SINKS.specialist)}`,
  specialistFirstName: `';__cx=${SINKS.specialist};//`,
  specialistReferralNo: `X${runId.slice(-4)}`,
};

let mysqlDefaults = null;
function initMysqlDefaults() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'pdf-label-xss-'));
  const file = path.join(dir, 'mysql-defaults.cnf');
  fs.writeFileSync(file, `[client]\npassword=${mysqlPassword}\n`, { mode: 0o600 });
  mysqlDefaults = { dir, file };
}
function cleanupMysqlDefaults() {
  if (mysqlDefaults) {
    fs.rmSync(mysqlDefaults.dir, { recursive: true, force: true });
    mysqlDefaults = null;
  }
}
function sql(query) {
  assert(mysqlDefaults, 'MySQL defaults file has not been initialized');
  return execFileSync(mysqlClient, [
    `--defaults-extra-file=${mysqlDefaults.file}`,
    '-h', mysqlHost, '-u', mysqlUser, mysqlDatabase, '-N', '-B', '-e', query,
  ], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], timeout: 20000 }).trim();
}
// The fixture text is deliberately full of quotes and angle brackets, so it is
// passed to MariaDB as a hex literal rather than as a quoted string: no
// escaping rule to get wrong, and nothing in the payload can terminate the
// statement.
function hex(value) {
  return `0x${Buffer.from(value, 'utf8').toString('hex')}`;
}

function seedFixture() {
  sql(`INSERT INTO demographic
        (last_name, first_name, address, city, province, postal, phone, email,
         year_of_birth, month_of_birth, date_of_birth, hin, ver, chart_no, sex,
         patient_status, roster_status, hc_type, family_doctor, provider_no,
         date_joined, eff_date, hc_renew_date, end_date,
         lastUpdateUser, lastUpdateDate, pref_name)
       VALUES
        (${hex(fixture.lastName)}, ${hex(fixture.firstName)}, ${hex(fixture.address)},
         'Testville', 'ON', 'M5W 1E6', '555-0100', 'fake@example.invalid',
         '1970', '01', '02', ${hex(fixture.hin)}, 'AB', ${hex(fixture.chartNo)}, 'F',
         'AC', 'RO', 'ON', ${hex(fixture.familyDoctor)}, '999998',
         '2020-01-01', '2020-01-01', '2030-01-01', '2030-01-01',
         '999998', NOW(), '')`);
  const demographicNo = sql(`SELECT demographic_no FROM demographic WHERE last_name=${hex(fixture.lastName)}`);
  assert(/^\d+$/.test(demographicNo), `fixture patient was not created (got '${demographicNo}')`);

  sql(`INSERT INTO demographiccust (demographic_no, cust1, cust2, cust3, cust4, content)
       VALUES (${demographicNo}, '', '', ${hex(fixture.alert)}, '', ${hex(fixture.notes)})`);

  sql(`INSERT INTO professionalSpecialists
        (fName, lName, referralNo, specType, lastUpdated, institutionId, departmentId, hideFromView)
       VALUES (${hex(fixture.specialistFirstName)}, ${hex(fixture.specialistLastName)},
               ${hex(fixture.specialistReferralNo)}, 'Playwright fixture', NOW(), 0, 0, 0)`);

  // Read the payloads back before testing anything. A payload one character
  // over its column width is truncated by MariaDB, and the page then renders
  // something that does not match the fixture -- which reads downstream as the
  // encoder mangling the value. Failing here instead says what actually
  // happened, and names the field.
  assertStored('demographic.address', fixture.address,
    sql(`SELECT address FROM demographic WHERE demographic_no=${demographicNo}`));
  assertStored('demographic.family_doctor', fixture.familyDoctor,
    sql(`SELECT family_doctor FROM demographic WHERE demographic_no=${demographicNo}`));
  assertStored('demographiccust.cust3 (alert)', fixture.alert,
    sql(`SELECT cust3 FROM demographiccust WHERE demographic_no=${demographicNo}`));
  assertStored('professionalSpecialists.lName', fixture.specialistLastName,
    sql(`SELECT lName FROM professionalSpecialists WHERE referralNo=${hex(fixture.specialistReferralNo)}`));
  assertStored('professionalSpecialists.fName', fixture.specialistFirstName,
    sql(`SELECT fName FROM professionalSpecialists WHERE referralNo=${hex(fixture.specialistReferralNo)}`));

  return demographicNo;
}

function assertStored(column, expected, actual) {
  assert(actual === expected,
    `fixture payload for ${column} was not stored verbatim (column too narrow?): `
    + `wanted ${JSON.stringify(expected)}, database holds ${JSON.stringify(actual)}`);
}

function dropFixture(demographicNo) {
  if (/^\d+$/.test(demographicNo || '')) {
    sql(`DELETE FROM demographiccust WHERE demographic_no=${demographicNo}`);
    sql(`DELETE FROM demographicArchive WHERE demographic_no=${demographicNo}`);
    sql(`DELETE FROM demographic WHERE demographic_no=${demographicNo}`);
  }
  sql(`DELETE FROM professionalSpecialists WHERE referralNo=${hex(fixture.specialistReferralNo)}`);
}

(async () => {
  const recorder = createRecorder();
  initMysqlDefaults();
  let demographicNo = null;
  let browser = null;
  try {
    // Seed BEFORE the browser starts: the label is a read-only view, so the
    // whole fixture has to be in place by the time the page is requested.
    demographicNo = seedFixture();

    browser = await chromium.launch(getLaunchOptions(config.chromePath));
    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1100 } });
    const page = await login(context, config, recorder);

    const response = await gotoApp(page, config.baseUrl, `/demographic/DemographicPdfLabel?demographic_no=${demographicNo}`);
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    assert(response && response.status() === 200,
      `print label did not return 200 (got ${response && response.status()})`);
    await assertNotErrorPage(page, 'demographic print label');
    await screenshot(page, config.screenshotDir, 'demographic-pdf-label-xss');

    // 1. Nothing executed. window.__carlosXss is set by whichever payload runs,
    //    and an onerror handler on an injected <img> has already fired by the
    //    time the load event is done.
    const fired = await page.evaluate(() => window.__cx);
    assert(fired === undefined,
      `stored markup EXECUTED on the print label: sink marker ${fired} `
      + `(${Object.keys(SINKS).find((k) => SINKS[k] === fired) || 'unknown'}) ran as script`);
    assert(recorder.dialogs.length === 0,
      `print label raised a dialog, which on this fixture means injected script ran: ${JSON.stringify(recorder.dialogs)}`);
    assert(recorder.pageErrors.length === 0,
      `print label raised page errors: ${JSON.stringify(recorder.pageErrors)}`);

    // 2. Nothing was PARSED as markup either. An injected <img> that 404s
    //    would still fire onerror; one that never became an element cannot.
    const injectedImages = await page.evaluate(() => Array.from(document.images)
      .filter((img) => /(^|\/)x$/.test(new URL(img.src, location.href).pathname)).length);
    assert(injectedImages === 0,
      `payload markup was parsed into ${injectedImages} <img> element(s) — a field is still raw`);

    // 3. The raw response carries the payload ESCAPED, never verbatim. This is
    //    the assertion that survives a browser that silently drops a malformed
    //    tag: the bytes the server sent are what the encoder is judged on.
    const html = await response.text();
    for (const [field, value] of Object.entries({
      address: fixture.address,
      alert: fixture.alert,
      hin: fixture.hin,
      chartNo: fixture.chartNo,
    })) {
      assert(!html.includes(value),
        `${field} reached the browser unencoded: the response contains the payload verbatim`);
    }
    assert(html.includes('&lt;img src=x onerror=__cx=1'),
      'the address payload is not present in HTML-escaped form — the field may not be rendered at all, '
      + 'which would make this check vacuous');

    // 4. The label still shows the data. Encoding that dropped the value would
    //    pass every assertion above and be a worse bug than the XSS.
    const bodyText = await page.locator('body').innerText();
    assert(bodyText.includes(fixture.lastName),
      `print label does not show the patient last name: ${bodyText.replace(/\s+/g, ' ').slice(0, 300)}`);
    assert(bodyText.includes(fixture.hin),
      'print label does not show the HIN as literal text');
    assert(bodyText.includes(fixture.address),
      'print label does not show the address as literal text');
    assert(bodyText.includes(fixture.alert),
      'print label does not show the alert as literal text');

    // 5. The non-body sinks. The title attribute must carry the demographic
    //    number and nothing else; a broken attribute encoding shows up as a
    //    title that swallowed the rest of the tag.
    const title = await page.locator('td[title]').first().getAttribute('title');
    assert(title !== null && title.trim() === demographicNo,
      `title attribute is not the demographic number (got ${JSON.stringify(title)})`);

    // 6. The referral-doctor cell — whichever branch this deployment renders.
    let referralBranch = 'html';
    if (await page.locator('select[name="r_doctor"]').count()) {
      referralBranch = 'select+script';
      // The inline script must still parse: a naive fix that dropped the
      // quoting would produce a SyntaxError and take changeRefDoc() with it.
      const changeRefDoc = await page.evaluate(() => typeof window.changeRefDoc);
      assert(changeRefDoc === 'function',
        `the referral <script> block did not parse (changeRefDoc is ${changeRefDoc})`);
      const scriptSource = await page.evaluate(() => Array.from(document.scripts)
        .map((s) => s.textContent || '').join('\n'));
      // forJavaScript escapes what can escape a JS string literal -- the quote
      // becomes \x22 and the slash \/ , so a payload can never spell the
      // </script> that would close the block -- and deliberately leaves < and >
      // alone, because inside a string literal they are inert. So the test is
      // not "no angle brackets appear"; it is that the STORED value cannot
      // appear as live syntax.
      assert(!scriptSource.includes(fixture.specialistLastName),
        'the specialist name reached the inline script with its quote unescaped — the JavaScript context is unencoded');
      assert(!/<\/script/i.test(scriptSource),
        'a payload spelled a </script> close inside the inline script block');
      const stillClean = await page.evaluate(() => window.__cx);
      assert(stillClean === undefined,
        `the specialist payload executed from the referral script block (marker ${stillClean})`);
      // The option value is HTML-attribute encoded while the script string is
      // JavaScript encoded; both decode to the SAME text, so the lookup the
      // page exists to do still works. This is the functional half of the fix.
      const optionMatches = await page.evaluate((expected) => {
        const select = document.querySelector('select[name="r_doctor"]');
        const option = Array.from(select.options).find((o) => o.value.includes(expected));
        return option ? option.value : null;
      }, fixture.specialistLastName);
      assert(optionMatches && optionMatches.includes(fixture.specialistLastName),
        'the specialist option value did not decode back to the stored name — '
        + 'the attribute encoding changed the value the script compares against');
    } else {
      assert(bodyText.includes(fire(SINKS.referralDoctor)),
        'the family-doctor value is not rendered as literal text on the label');
    }

    const fatalConsole = recorder.consoleIssues.filter((issue) => /SyntaxError|ReferenceError|is not defined/i.test(issue.text || ''));
    assert(fatalConsole.length === 0, `fatal console errors on the print label: ${JSON.stringify(fatalConsole)}`);

    await context.close();
    console.log(`PASS demographic print label encodes stored markup in every patient field `
      + `(demographic_no ${demographicNo}, referral branch: ${referralBranch})`);
  } catch (error) {
    console.error('FAIL demographic print-label XSS Playwright check');
    console.error(error.stack || error.message);
    console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
    process.exitCode = 1;
  } finally {
    // Always remove the fixture, including after a failure: a patient row full
    // of live markup must never be left behind on a shared test install.
    try {
      const leftover = demographicNo
        || sql(`SELECT demographic_no FROM demographic WHERE last_name=${hex(fixture.lastName)}`);
      dropFixture(leftover);
    } catch (cleanupError) {
      console.error(`WARN could not remove the XSS fixture (last_name ${fixture.lastName}): ${cleanupError.message}`);
      process.exitCode = 1;
    }
    cleanupMysqlDefaults();
    if (browser) {
      await browser.close().catch(() => {});
    }
  }
})();
