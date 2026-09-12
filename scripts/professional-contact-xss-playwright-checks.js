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
 * Browser regression checks for output encoding on the professional contact
 * form (WEB-INF/jsp/demographic/addEditProfessionalContact.jsp).
 *
 * The page interpolates request parameters, request attributes and persisted
 * contact fields into single-quoted JavaScript string literals and into
 * `value="..."` attributes. Before the fix those interpolations were raw, so
 * `param.keyword` / `param.contactType` were a reflected XSS sink and a stored
 * contact's `lastName` / `firstName`, plus the stored `role` that comes back as
 * `requestScope.contactRole`, were a stored one. The JSP now routes each
 * through `carlos:forJavaScript` or `carlos:forHtmlAttribute`.
 *
 * WHAT THIS MEASURES, AND WHERE TO POINT IT
 *
 * This script exists to prove the ENCODER, so BASE_URL must reach the
 * application without a web application firewall in front of it. On a packaged
 * install that means Tomcat directly (http://127.0.0.1:18080/carlos), not the
 * nginx front door: the CRS XSS family is left inspecting on :443, so the
 * tag-injection payload below is refused with 403 before the JSP ever renders
 * and the run would pass without executing the code it is meant to check.
 *
 * The WAF is a second layer, not the fix, and how much of a layer it is on this
 * sink was measured rather than assumed. Set WAF_BASE_URL to the front door and
 * the script probes it: the tag-injection payload must be blocked, and the
 * quote-breakout payload's status is reported as an observation because the
 * rule set does not score it (measured: 200 at paranoia level 1 -- it carries
 * no angle brackets). On that shape the JSP encoder is the only control, which
 * is the whole argument for the fix.
 *
 * Payloads are written so that an UNENCODED render EXECUTES and an encoded one
 * round-trips as inert text, and both halves are asserted: a check that only
 * looked for "no script ran" would also pass against a page that rendered
 * nothing at all. Every case therefore asserts both that the sentinel
 * `window.__carlosXss` was never set and that the field still carries the
 * payload verbatim as data.
 *
 * The stored cases are seeded through the database rather than through the
 * application's own save path on purpose. The front door scores these payloads,
 * so a UI-driven save could not create the row; and a stored value that arrived
 * by import, HL7 or a legacy row never passed through that save path either,
 * which is precisely the case the encoder has to survive. Rows are removed in a
 * finally block.
 *
 * Defaults are for the local devcontainer:
 *   node scripts/professional-contact-xss-playwright-checks.js
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos   app under test, WAF-free
 *   WAF_BASE_URL=https://127.0.0.1/carlos   front door; when set, each payload
 *                                           must be refused there with 403
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc
 *   TEST_PASSWORD=carlos2026
 *   TEST_PIN=2026
 *   MYSQL_HOST=db MYSQL_USER=root MYSQL_PASSWORD=password MYSQL_DATABASE=carlos
 *   XSS_DEMOGRAPHIC_NO=1                    patient the seeded contact links to
 *   SKIP_STORED_XSS=true                    run only the reflected cases
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const fs = require('node:fs');
const net = require('node:net');
const os = require('node:os');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const { chromium } = require('playwright');

const EXACT_LOCAL_HOSTS = new Set([
  'localhost',
  '127.0.0.1',
  '::1',
  '0:0:0:0:0:0:0:1',
  '0.0.0.0',
  'host.docker.internal',
  'carlos',
]);

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const wafBaseUrl = process.env.WAF_BASE_URL ? validateBaseUrl(process.env.WAF_BASE_URL) : null;
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';

const mysqlHost = process.env.MYSQL_HOST || 'db';
const mysqlUser = process.env.MYSQL_USER || 'root';
const mysqlPassword = process.env.MYSQL_PASSWORD || 'password';
const mysqlDatabase = process.env.MYSQL_DATABASE || 'carlos';
const storedDemographicNo = Number(process.env.XSS_DEMOGRAPHIC_NO || '1');
const skipStored = process.env.SKIP_STORED_XSS === 'true';

// One stamp per run, so a crashed earlier run's rows are never mistaken for
// this run's and the cleanup only ever deletes what this process inserted.
const stamp = `xsscheck${Date.now()}`;

/*
 * The payloads. Each one is chosen for the exact context it lands in, and each
 * ends in a state where the surrounding markup still parses -- an injection
 * that merely corrupted the page would show up as a pageerror rather than as
 * the sentinel, and the two failures want telling apart.
 */

// JavaScript single-quoted string literal: close the string, run, reopen it so
// the statement still terminates. Unencoded this assigns the sentinel.
function jsBreakout(slot) {
  return `');window.__carlosXss='${slot}';var ${stamp}=('`;
}

// HTML attribute value in a double-quoted attribute: close the attribute and
// add event-handler attributes of our own. `autofocus` fires `onfocus` with no
// user interaction, and the trailing `<img>` covers the case where a filter
// strips handlers but not tags.
function attrBreakout(slot) {
  return `" autofocus onfocus="window.__carlosXss='${slot}'" data-${stamp}="x`;
}

function imgBreakout(slot) {
  return `"><img id="${stamp}img" src="x" onerror="window.__carlosXss='${slot}'">`;
}

const findings = [];
const checks = [];
// Measurements that are reported but not asserted -- see checkWafBlocks.
const observations = [];

let mysqlDefaults = null;
const seededContactIds = [];
const seededDemographicContactIds = [];

function normalizedHostname(url) {
  const host = url.hostname.toLowerCase();
  return host.startsWith('[') && host.endsWith(']') ? host.slice(1, -1) : host;
}

function isExactLocalHost(host) {
  return EXACT_LOCAL_HOSTS.has(host);
}

function isPrivateIpv4(host) {
  if (!net.isIPv4(host)) {
    return false;
  }
  const [first, second] = host.split('.').map(Number);
  return first === 10
    || (first === 192 && second === 168)
    || (first === 172 && second >= 16 && second <= 31);
}

function validateBaseUrl(rawBaseUrl) {
  const parsed = new URL(rawBaseUrl);
  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw new Error(`BASE_URL must use http or https, got ${parsed.protocol}`);
  }
  if (parsed.username || parsed.password) {
    throw new Error('BASE_URL must not contain embedded credentials');
  }

  const host = normalizedHostname(parsed);
  const exactLocalHost = isExactLocalHost(host);
  const privateIpv4 = isPrivateIpv4(host);
  if (!exactLocalHost && !privateIpv4 && process.env.ALLOW_NON_LOCAL_BASE_URL !== 'true') {
    throw new Error(`Refusing non-local BASE_URL host ${host}; set ALLOW_NON_LOCAL_BASE_URL=true for an intentional test target`);
  }
  if (!exactLocalHost && parsed.protocol !== 'https:') {
    throw new Error('Non-local BASE_URL targets must use https');
  }
  parsed.pathname = parsed.pathname.replace(/\/$/, '');
  parsed.search = '';
  parsed.hash = '';
  return parsed;
}

function appUrl(appPath, query, root) {
  if (!appPath.startsWith('/') || appPath.startsWith('//')) {
    throw new Error(`Application path must be root-relative, got ${appPath}`);
  }
  const base = root || baseUrl;
  const url = new URL(base.href);
  url.pathname = `${base.pathname}${appPath}`.replace(/\/{2,}/g, '/');
  url.search = '';
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      url.searchParams.set(key, value);
    }
  }
  return url.toString();
}

function isWithinConfiguredApp(url) {
  const appRoot = baseUrl.pathname || '/';
  return url.origin === baseUrl.origin
    && (appRoot === '/' || url.pathname === appRoot || url.pathname.startsWith(`${appRoot}/`));
}

async function safeGoto(page, appPath, query, options) {
  const response = await page.goto(appUrl(appPath, query), options); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- appUrl rejects non-root-relative paths and validateBaseUrl restricts hosts to loopback by default // NOSONAR - same rationale
  if (!isWithinConfiguredApp(new URL(page.url()))) {
    throw new Error('Navigation left the configured CARLOS EMR application');
  }
  return response;
}

function waitForAppPath(page, pathPattern, options) {
  return page.waitForURL((url) => isWithinConfiguredApp(url) && pathPattern.test(url.pathname), options);
}

async function installNavigationGuard(context) {
  await context.route('**/*', async (route) => {
    const request = route.request();
    if (request.isNavigationRequest() && !isWithinConfiguredApp(new URL(request.url()))) {
      findings.push({ label: 'navigation', type: 'outside-app-navigation-blocked' });
      await route.abort('blockedbyclient');
      return;
    }
    await route.continue();
  });
}

function isExpectedConsoleNoise(message) {
  const text = message.text();
  return /Content Security Policy.*report-only/i.test(text)
    || /Master token \[CSRF-TOKEN\]/.test(text)
    || /Hidden token fields .* were updated with new token value/.test(text);
}

function isSevereConsoleMessage(message) {
  if (isExpectedConsoleNoise(message)) {
    return false;
  }
  const text = message.text();
  if (message.type() === 'error') {
    return !/imageRenderingServlet\?|favicon\.ico/i.test(text);
  }
  return /(ReferenceError|TypeError|SyntaxError|redeclaration|Cannot read|Cannot set)/i.test(text);
}

function isExpectedMissingAsset(status, responseUrl) {
  return status === 404 && (/\/imageRenderingServlet\?/.test(responseUrl) || /\/favicon\.ico$/.test(responseUrl));
}

function wirePage(page, label) {
  page.on('response', (response) => {
    const responseUrl = response.url();
    const status = response.status();
    if (status >= 400 && !isExpectedMissingAsset(status, responseUrl)) {
      findings.push({ label, type: 'http', status });
    }
  });
  page.on('console', (message) => {
    if (isSevereConsoleMessage(message)) {
      findings.push({ label, type: `console:${message.type()}` });
    }
  });
  page.on('pageerror', (error) => {
    // A syntax error here is itself evidence of a broken-out string literal,
    // so it is reported with its name rather than swallowed.
    findings.push({ label, type: 'pageerror', name: error && error.name ? error.name : 'Error' });
  });
  page.on('dialog', async (dialog) => {
    findings.push({ label, type: 'dialog' });
    await dialog.accept();
  });
}

async function assertNoErrorPage(page, label) {
  const bodyText = await page.locator('body').innerText().catch(() => '');
  if (/CARLOS has encountered an unexpected error|HTTP Status 500|Exception Report/i.test(bodyText)) {
    findings.push({ label, type: 'error-page' });
  }
}

function record(label, pass, detail) {
  checks.push({ label, pass });
  if (!pass) {
    findings.push(detail ? { label, type: 'assertion', detail } : { label, type: 'assertion' });
  }
}

function expectValue(label, actual, expected) {
  record(label, actual === expected, actual === expected ? undefined : 'value-mismatch');
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

/* ---------------------------------------------------------------- database */

function createMysqlDefaultsFile() {
  if (/[\r\n]/.test(mysqlPassword)) {
    throw new Error('MYSQL_PASSWORD must not contain newline characters');
  }
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-procontact-mysql-'));
  const file = path.join(dir, 'client.cnf');
  fs.writeFileSync(file, `[client]\npassword=${mysqlPassword}\n`, { mode: 0o600 });
  return { dir, file };
}

function cleanupMysqlDefaultsFile() {
  if (mysqlDefaults) {
    fs.rmSync(mysqlDefaults.dir, { recursive: true, force: true });
    mysqlDefaults = null;
  }
}

function sql(query) {
  return execFileSync('mysql', [
    `--defaults-extra-file=${mysqlDefaults.file}`,
    '-h', mysqlHost,
    '-u', mysqlUser,
    mysqlDatabase,
    '-N',
    '-B',
    '-e',
    query,
  ], {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  }).trim();
}

function escapeSql(value) {
  return String(value).replace(/\\/g, '\\\\').replace(/'/g, "''");
}

/**
 * Seed one professional contact carrying the payloads and link it to the
 * patient, returning the DemographicContact id the page is opened with.
 *
 * The discriminator is read off the table rather than hardcoded: `Contact` is a
 * SINGLE_TABLE hierarchy whose `type` column is Hibernate's default entity-name
 * discriminator, and a row inserted with the wrong value loads as the wrong
 * subclass (or not at all) and the page would render an empty form that passes
 * every "no script ran" assertion for the wrong reason.
 */
function seedStoredContact(payloads) {
  const discriminator = sql("SELECT type FROM Contact WHERE type LIKE '%ProfessionalContact%' LIMIT 1")
    || 'ProfessionalContact';

  sql(`INSERT INTO Contact (type, lastName, firstName, note, deleted)
       VALUES ('${escapeSql(discriminator)}',
               '${escapeSql(payloads.lastName)}',
               '${escapeSql(payloads.firstName)}',
               '${escapeSql(stamp)}', 0)`);
  // NOT LAST_INSERT_ID(): every sql() call is its own `mysql` process and so its
  // own connection, where LAST_INSERT_ID() is 0. A contact linked at id 0 loads
  // as null and the page answers 500 -- a failure that looks exactly like the
  // encoder defect this script is hunting. The run stamp is the key instead.
  const contactId = sql(`SELECT id FROM Contact WHERE note = '${escapeSql(stamp)}' ORDER BY id DESC LIMIT 1`);
  assert(/^[1-9]\d*$/.test(contactId), 'failed to seed Contact row');
  seededContactIds.push(contactId);

  sql(`INSERT INTO DemographicContact
         (facilityId, creator, updateDate, deleted, demographicNo, contactId, role, type, category, note, active)
       VALUES (0, '${escapeSql(testUser)}', NOW(), 0, ${storedDemographicNo},
               '${escapeSql(contactId)}',
               '${escapeSql(payloads.role)}',
               2, 'professional', '${escapeSql(stamp)}', 1)`);
  const demographicContactId = sql(`SELECT id FROM DemographicContact WHERE note = '${escapeSql(stamp)}' ORDER BY id DESC LIMIT 1`);
  assert(/^[1-9]\d*$/.test(demographicContactId), 'failed to seed DemographicContact row');
  seededDemographicContactIds.push(demographicContactId);

  return demographicContactId;
}

function cleanupSeededRows() {
  if (!mysqlDefaults) {
    return;
  }
  for (const id of seededDemographicContactIds) {
    try {
      sql(`DELETE FROM DemographicContact WHERE id = ${Number(id)} AND note = '${escapeSql(stamp)}'`);
    } catch (error) {
      findings.push({ label: 'cleanup', type: 'demographic-contact-delete-failed' });
    }
  }
  for (const id of seededContactIds) {
    try {
      sql(`DELETE FROM Contact WHERE id = ${Number(id)} AND note = '${escapeSql(stamp)}'`);
    } catch (error) {
      findings.push({ label: 'cleanup', type: 'contact-delete-failed' });
    }
  }
}

/* ----------------------------------------------------------------- asserts */

/**
 * The core assertion pair, run on every rendered page.
 *
 * `window.__carlosXss` proves nothing executed. `pageerror` is checked by the
 * page wiring. Neither is enough on its own, so callers pair this with a
 * round-trip assertion on the field that carried the payload.
 */
async function expectNoScriptExecution(page, label) {
  const sentinel = await page.evaluate(() => window.__carlosXss);
  record(`${label}-no-script-execution`, sentinel === undefined,
    sentinel === undefined ? undefined : 'sentinel-set');

  // A tag-injection payload leaves a node behind even when its handler never
  // fires (a cached image, a CSP that blocks inline handlers). Catching the
  // node keeps the check honest about "the markup was never broken out of"
  // rather than only about "no code ran here today".
  const injected = await page.locator(`#${stamp}img`).count();
  record(`${label}-no-injected-node`, injected === 0, injected === 0 ? undefined : 'node-injected');
}

/**
 * A raw `'` surviving into a single-quoted literal is the defect itself, so the
 * served source is checked as well as the live DOM: a browser that recovered
 * from the broken literal would still render a page the DOM assertions pass.
 */
function expectEncodedJsLiteral(label, html, variableName) {
  const literal = new RegExp(`var ${variableName} = '([^'\\n]*)';`);
  const match = literal.exec(html);
  record(`${label}-literal-intact`, match !== null, match ? undefined : 'literal-broken-or-missing');
  if (match) {
    record(`${label}-literal-has-no-raw-quote`, !match[1].includes("'"));
    // OWASP's JavaScript encoder emits \x27 for the apostrophe. Requiring it
    // pins that the encoder actually ran, not merely that the payload happened
    // to arrive quote-free.
    record(`${label}-literal-escaped-quote`, match[1].includes('\\x27'),
      match[1].includes('\\x27') ? undefined : 'no-escaped-quote');
  }
}

async function expectAttributeRoundTrip(page, label, selector, rawPayload) {
  const field = page.locator(selector);
  const count = await field.count();
  record(`${label}-field-present`, count > 0, count > 0 ? undefined : 'field-missing');
  if (count === 0) {
    return;
  }
  expectValue(`${label}-value-round-trip`, await field.inputValue(), rawPayload);
  for (const attribute of ['onfocus', 'autofocus', `data-${stamp}`]) {
    const injected = await field.getAttribute(attribute);
    record(`${label}-no-${attribute}-attribute`, injected === null,
      injected === null ? undefined : 'attribute-injected');
  }
}

/* -------------------------------------------------------------------- flow */

async function login(context) {
  const page = await context.newPage();
  wirePage(page, 'login');
  await safeGoto(page, '/', null, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.locator('#username').fill(testUser);
  await page.locator('#password').fill(testPassword);
  const pin = page.locator('#pin');
  if (await pin.count()) {
    await pin.fill(testPin);
  }
  await Promise.all([
    waitForAppPath(page, /providercontrol|appointment/i, { timeout: 30000 }),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  await assertNoErrorPage(page, 'login');
  return page;
}

/**
 * The reflected sinks: `param.keyword` into a JS literal and `param.contactType`
 * into a hidden field's `value`. Both arrive on the same GET, so one navigation
 * exercises the JavaScript and the HTML-attribute encoder together.
 */
async function checkReflected(context) {
  const keywordPayload = jsBreakout('reflected-keyword');
  const contactTypePayload = attrBreakout('reflected-contact-type');

  const page = await context.newPage();
  wirePage(page, 'reflected');
  const response = await safeGoto(page, '/demographic/Contact', {
    method: 'addProContact',
    keyword: keywordPayload,
    contactType: contactTypePayload,
    demographicNo: String(storedDemographicNo),
  }, { waitUntil: 'domcontentloaded', timeout: 30000 });

  record('reflected-page-served', response !== null && response.status() === 200,
    response && response.status() === 200 ? undefined : 'unexpected-status');
  await assertNoErrorPage(page, 'reflected');

  const html = await page.content();
  expectEncodedJsLiteral('reflected-keyword', html, 'keyword');
  await expectAttributeRoundTrip(page, 'reflected-contact-type', '#contactType', contactTypePayload);
  await expectNoScriptExecution(page, 'reflected');

  await page.close();
}

/**
 * The same hidden field, this time with a tag-injection payload. Splitting it
 * from the handler payload keeps a failure readable: the attribute assertions
 * above answer "was the attribute broken out of", this one answers "was the
 * element broken out of".
 */
async function checkReflectedTagInjection(context) {
  const contactTypePayload = imgBreakout('reflected-img');

  const page = await context.newPage();
  wirePage(page, 'reflected-tag');
  await safeGoto(page, '/demographic/Contact', {
    method: 'addProContact',
    contactType: contactTypePayload,
    demographicNo: String(storedDemographicNo),
  }, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await assertNoErrorPage(page, 'reflected-tag');

  await expectAttributeRoundTrip(page, 'reflected-tag-contact-type', '#contactType', contactTypePayload);
  await expectNoScriptExecution(page, 'reflected-tag');

  await page.close();
}

/**
 * The stored sinks: a contact's `lastName` reaches both a JS literal and the
 * editable field's `value`, `firstName` reaches a JS literal only, and the
 * stored `role` comes back as `requestScope.contactRole` in a third literal.
 */
async function checkStored(context, demographicContactId, payloads) {
  const page = await context.newPage();
  wirePage(page, 'stored');
  await safeGoto(page, '/demographic/Contact', {
    method: 'editHealthCareTeam',
    contactId: demographicContactId,
    demographicNo: String(storedDemographicNo),
  }, { waitUntil: 'domcontentloaded', timeout: 30000 });
  await assertNoErrorPage(page, 'stored');

  const html = await page.content();
  expectEncodedJsLiteral('stored-last-name', html, 'lastName');
  expectEncodedJsLiteral('stored-first-name', html, 'firstName');
  expectEncodedJsLiteral('stored-contact-role', html, 'contactRole');

  // The editable last-name field must still show the clinician exactly what is
  // stored -- an encoder that dropped or mangled the value would "pass" the
  // execution assertions while silently corrupting a record.
  await expectAttributeRoundTrip(page, 'stored-last-name', '#pcontact\\.lastName', payloads.lastName);
  await expectNoScriptExecution(page, 'stored');

  await page.close();
}

/**
 * Defence in depth, measured rather than assumed.
 *
 * Only ONE of these two probes is a requirement. The tag-injection payload
 * carries `<img ... onerror=`, which the CRS XSS family scores, and the front
 * door must refuse it -- a 403 there is the second layer working.
 *
 * The quote-breakout payload carries no angle brackets and no tag name, and
 * measured against this package's rule set at paranoia level 1 it is NOT
 * scored: it reaches the application with a 200. That is recorded as an
 * observation, not asserted, for two reasons. Requiring the block would pin
 * behaviour the rule set does not promise; requiring the pass-through would
 * raise a false alarm the day someone tightens the policy. Either way the point
 * it makes is the one this script exists for: on this sink the WAF is not
 * standing between the payload and the page, the encoder is.
 *
 * Probed with the request client rather than a browser page: a 403 from nginx
 * is an error page, and driving it through the navigation guard would only
 * obscure the status.
 */
async function checkWafBlocks(context) {
  async function probeStatus(query) {
    const url = appUrl('/demographic/Contact', query, wafBaseUrl);
    // maxRedirects: 0 so a 302 to the login page is reported as the 302 it is.
    // Anything other than 403 means the payload reached the application.
    const response = await context.request.get(url, { maxRedirects: 0 }).catch(() => null);
    return response ? response.status() : 0;
  }

  const tagStatus = await probeStatus({ method: 'addProContact', contactType: imgBreakout('waf') });
  record('waf-tag-injection-blocked', tagStatus === 403,
    tagStatus === 403 ? undefined : `status-${tagStatus}`);

  const breakoutStatus = await probeStatus({ method: 'addProContact', keyword: jsBreakout('waf') });
  observations.push({
    label: 'waf-js-breakout-status',
    status: breakoutStatus,
    note: breakoutStatus === 403
      ? 'the front door scored the quote-breakout payload'
      : 'the front door did NOT score the quote-breakout payload; the JSP encoder is the only control on this sink',
  });
}

(async () => {
  const launchOptions = {
    headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  };
  if (chromePath) {
    launchOptions.executablePath = chromePath;
  }

  const payloads = {
    lastName: jsBreakout('stored-last-name'),
    firstName: jsBreakout('stored-first-name'),
    role: jsBreakout('stored-role'),
  };

  const browser = await chromium.launch(launchOptions);
  try {
    let demographicContactId = null;
    if (!skipStored) {
      mysqlDefaults = createMysqlDefaultsFile();
      demographicContactId = seedStoredContact(payloads);
    }

    // The front-door probe rides on this context's request client, and a
    // packaged install serves :443 with a self-signed certificate, so the WAF
    // target's locality has to count towards this too.
    const context = await browser.newContext({
      ignoreHTTPSErrors: isExactLocalHost(normalizedHostname(baseUrl))
        && (!wafBaseUrl || isExactLocalHost(normalizedHostname(wafBaseUrl))),
      viewport: { width: 1024, height: 700 },
    });
    await installNavigationGuard(context);

    const landingPage = await login(context);
    // Close the landing page once the session cookie exists. It stays wired to
    // the finding collectors, so leaving it open lets a late console error
    // there fail the run under a label with nothing to do with this page.
    await landingPage.close();

    await checkReflected(context);
    await checkReflectedTagInjection(context);
    if (demographicContactId) {
      await checkStored(context, demographicContactId, payloads);
    }
    if (wafBaseUrl) {
      await checkWafBlocks(context);
    }

    const failed = checks.filter((check) => !check.pass);
    if (failed.length || findings.length) {
      throw new Error(`professional contact XSS browser check found ${findings.length} issue(s) across ${failed.length} failed assertion(s)`);
    }

    console.log('PASS CARLOS EMR professional contact form encodes reflected and stored values');
  } finally {
    try {
      cleanupSeededRows();
    } finally {
      cleanupMysqlDefaultsFile();
    }
    console.log(JSON.stringify({ checks, observations, findings }, null, 2));
    await browser.close();
  }
})().catch((error) => {
  console.error('FAIL CARLOS EMR professional contact XSS Playwright check');
  console.error(`Failure type: ${error && error.name ? error.name : 'Error'}`);
  process.exit(1);
});
