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
 * Browser check for the Ontario 3rd-Party / Bonus-Codes bill-entry path.
 *
 * An alpha tester on 2026.08.0-alpha10 hit "CARLOS Error: 500" when switching
 * the bill type on the Ontario bill form. billingON.jsp rebuilt its own
 * navigation query string WITHOUT billRegion, and Billing2Action's fall-back
 * read a holder nothing populated, so the router answered "BC" and handed an
 * Ontario request to billingBC.jsp -- which queries the BC-only billingvisit
 * table. The bill entry was lost with the 500. The same release prefixed the
 * Ontario unbilled "Bill" links with the context path (they 404'd at the host
 * root) and put billRegion=ON on them.
 *
 * Why the region assertion and not just "no 500": the fall-back is a property,
 * `billregion`, and an install that HAS it set (the packaged Ontario install
 * does) still routes correctly with the query parameter missing. Asserting only
 * the status code would therefore pass on a re-broken page.
 *
 * Page identity is asserted from the Ontario bill-type CODES (ODP/BON), not
 * from the presence of a control named xml_billtype: billingBC.jsp carries an
 * xml_billtype field of its own, so that control cannot tell the two forms
 * apart. The codes can -- BC offers ICBC/WCB/Pri. Codes are also locale
 * independent, unlike the page title.
 *
 * Each bill-type switch must actually navigate. A swallowed navigation would
 * leave the browser on the entry URL, which already carries billRegion=ON and
 * already renders the Ontario form, so every assertion would pass without the
 * branch having run at all.
 *
 * READ-ONLY: it switches the bill type and reads links. It never submits a
 * bill, so it seeds nothing and has nothing to clean up.
 *
 * Defaults are for the local devcontainer:
 *   npm run test:billing-on-third-party-playwright
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc TEST_PASSWORD=carlos2026 TEST_PIN=2026
 *   Record pointers into the demo dataset (an appointment that can be billed):
 *     BILLING_APPOINTMENT_NO=11 BILLING_DEMOGRAPHIC_NO=1 BILLING_PROVIDER_NO=999998
 *     BILLING_APPOINTMENT_DATE=2024-04-16 BILLING_START_TIME=12:00:00
 *   BILLING_ON_ARTIFACT_DIR=/tmp/carlos-billing-on-playwright
 *   ALLOW_NON_LOCAL_BASE_URL=true for any target that is not loopback
 */
const fs = require('fs');
const { chromium } = require('playwright');
const {
  assert,
  buildArtifactPath,
  getLaunchOptions,
  gotoApp,
  validateBaseUrl,
} = require('./eform-local-playwright-utils');

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
// validateBaseUrl strips a trailing slash, so a root deployment yields '' here
// rather than '/' -- the link prefix below would otherwise look for '//billing?'.
const contextPath = baseUrl.pathname;
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';
const artifactDir = process.env.BILLING_ON_ARTIFACT_DIR || '/tmp/carlos-billing-on-playwright';

/*
 * Record pointers are interpolated into navigation URLs, so they are shape
 * checked before use: only digits and ISO dates/times reach the query string.
 */
function digits(name, raw, fallback) {
  const value = raw === undefined || raw === '' ? fallback : raw;
  assert(/^\d{1,10}$/.test(value), `${name} must be 1-10 digits, got ${value}`);
  return value;
}

function isoDate(name, raw, fallback) {
  const value = raw === undefined || raw === '' ? fallback : raw;
  assert(/^\d{4}-\d{2}-\d{2}$/.test(value), `${name} must be YYYY-MM-DD, got ${value}`);
  return value;
}

function isoTime(name, raw, fallback) {
  const value = raw === undefined || raw === '' ? fallback : raw;
  assert(/^\d{2}:\d{2}:\d{2}$/.test(value), `${name} must be HH:MM:SS, got ${value}`);
  return value;
}

const appointmentNo = digits('BILLING_APPOINTMENT_NO', process.env.BILLING_APPOINTMENT_NO, '11');
const demographicNo = digits('BILLING_DEMOGRAPHIC_NO', process.env.BILLING_DEMOGRAPHIC_NO, '1');
const providerNo = digits('BILLING_PROVIDER_NO', process.env.BILLING_PROVIDER_NO, '999998');
const appointmentDate = isoDate('BILLING_APPOINTMENT_DATE', process.env.BILLING_APPOINTMENT_DATE, '2024-04-16');
const startTime = isoTime('BILLING_START_TIME', process.env.BILLING_START_TIME, '12:00:00');

/*
 * The bill types whose onChangePrivate() branch rebuilds the URL -- exactly the
 * ones that lost billRegion. PAT/OCF/ODS/CPP/STD route through curBillForm=PRI
 * ("3rd party"), BON through the primary-care-incentive view ("Bonus Codes").
 */
const THIRD_PARTY_PREFIXES = ['PAT', 'OCF', 'ODS', 'CPP', 'STD'];
const BONUS_PREFIX = 'BON';
/* Ontario-only bill-type codes; billingBC.jsp offers ICBC/WCB/Pri instead. */
const ONTARIO_MARKER_PREFIXES = ['ODP', 'BON'];

const results = [];

function check(name, ok, detail) {
  results.push({ name, ok: Boolean(ok), detail: detail || '' });
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${ok || !detail ? '' : `: ${detail}`}`);
}

function billEntryPath() {
  const params = new URLSearchParams({
    billRegion: 'ON',
    hotclick: '',
    appointment_no: appointmentNo,
    demographic_no: demographicNo,
    apptProvider_no: providerNo,
    providerview: providerNo,
    appointment_date: appointmentDate,
    status: 't',
    start_time: startTime,
    bNewForm: '1',
  });
  return `/billing?${params.toString()}`;
}

/*
 * Identifiers that correlate back to a patient or a provider stay out of console
 * output and the results file. A routing failure needs billRegion, xml_billtype
 * and curBillForm; appointment_no, demographic_no/name, providerview and user_no
 * add nothing to the diagnosis, so the query string is filtered to the former.
 */
const DIAGNOSTIC_PARAMS = new Set(['billRegion', 'xml_billtype', 'curBillForm', 'billForm', 'reportAction']);

function queryParam(rawUrl, name) {
  try {
    return new URL(rawUrl, baseUrl).searchParams.get(name);
  } catch (error) {
    return null;
  }
}

function redactUrl(rawUrl) {
  let parsed;
  try {
    parsed = new URL(rawUrl, baseUrl);
  } catch (error) {
    return '(unparseable url)';
  }
  const kept = new URLSearchParams();
  for (const [key, value] of parsed.searchParams) {
    if (DIAGNOSTIC_PARAMS.has(key)) kept.set(key, value);
  }
  const query = kept.toString();
  const dropped = [...parsed.searchParams.keys()].some((key) => !DIAGNOSTIC_PARAMS.has(key));
  return `${parsed.pathname}${query ? `?${query}` : ''}${dropped ? ' (identifiers redacted)' : ''}`;
}

async function login(page) {
  await gotoApp(page, baseUrl, '/');
  await page.locator('#username').fill(testUser);
  await page.locator('#password').fill(testPassword);
  // login/index.jsp renders #pin only when MfaManager.isOscarLegacyPinEnabled().
  // Filling it unconditionally throws on an install with legacy PIN disabled,
  // and the check would never run there.
  const pin = page.locator('#pin');
  if ((await pin.count()) > 0) await pin.fill(testPin);
  await page.locator('input[type="submit"], button[type="submit"]').first().click();
  await page.waitForLoadState('domcontentloaded', { timeout: 60000 });
}

async function billTypeCodes(page) {
  const values = await page.locator('select[name="xml_billtype"] option')
    .evaluateAll((nodes) => nodes.map((node) => node.value))
    .catch(() => []);
  return values.map((value) => value.slice(0, 3));
}

async function describeBillPage(page) {
  const codes = await billTypeCodes(page);
  return {
    url: page.url(),
    codes,
    // Positive identity: the Ontario code set. The bare presence of an
    // xml_billtype control does NOT distinguish the forms (see header).
    isOntarioForm: ONTARIO_MARKER_PREFIXES.every((prefix) => codes.includes(prefix)),
    hasErrorPage: /CARLOS Error/i.test(await page.content()),
  };
}

async function switchBillType(page, optionValue, expectedCode) {
  await gotoApp(page, baseUrl, billEntryPath());
  const entryUrl = page.url();
  const select = page.locator('select[name="xml_billtype"]');
  await select.waitFor({ state: 'attached', timeout: 60000 });

  let documentStatus = null;
  const onResponse = (response) => {
    if (response.request().resourceType() === 'document') documentStatus = response.status();
  };
  page.on('response', onResponse);
  let navigated = true;
  try {
    // NOT swallowed: a branch that stops navigating would leave us on the entry
    // URL, which already satisfies every assertion below.
    await Promise.all([
      page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 60000 }),
      select.selectOption(optionValue),
    ]);
  } catch (error) {
    navigated = false;
  } finally {
    page.off('response', onResponse);
  }

  const described = await describeBillPage(page);
  return {
    documentStatus,
    navigated: navigated && described.url !== entryUrl,
    // Parsed, not pattern-matched: building a RegExp from the code would be a
    // dynamic expression for no gain (and Semgrep flags it as a ReDoS shape).
    carriesSelectedType: queryParam(described.url, 'xml_billtype') === expectedCode,
    ...described,
  };
}

/*
 * The report's "Bill" anchors are href="#"; the real URL -- the one #3588
 * fixed -- is the third argument of their onclick popupPage(h, w, "URL")
 * handler, so an href selector finds nothing and would skip forever.
 *
 * Two details the markup forces on us: the URL may be single- OR double-quoted,
 * and it is written through <carlos:encode context="javaScriptAttribute">, so
 * its separators arrive escaped (`&` as `\x26`). Decode before asserting, or a
 * literal `&billRegion=ON` test can never match.
 */
function decodeJsAttribute(raw) {
  return raw
    .replace(/\\x([0-9a-fA-F]{2})/g, (_, hex) => String.fromCharCode(parseInt(hex, 16)))
    .replace(/\\u([0-9a-fA-F]{4})/g, (_, hex) => String.fromCharCode(parseInt(hex, 16)))
    .replace(/\\(['"/\\])/g, '$1');
}

async function unbilledBillLinks(page) {
  const handlers = await page.locator('a[onclick*="popupPage"]')
    .evaluateAll((nodes) => nodes.map((node) => node.getAttribute('onclick') || ''));
  const urls = [];
  for (const handler of handlers) {
    const match = handler.match(/popupPage\s*\([^,]*,[^,]*,\s*(['"])((?:\\.|(?!\1).)*)\1/);
    if (!match) continue;
    const url = decodeJsAttribute(match[2]);
    if (url.includes('/billing?')) urls.push(url);
  }
  return urls;
}

async function run() {
  const browser = await chromium.launch(getLaunchOptions(process.env.CHROME_PATH || undefined));
  // Certificate verification is only relaxed for loopback, where the packaged
  // install serves its own self-signed cert. A non-local target opted in via
  // ALLOW_NON_LOCAL_BASE_URL must still prove its certificate, because this
  // script submits administrator credentials to it.
  const loopback = new Set(['localhost', '127.0.0.1', '::1', '0:0:0:0:0:0:0:1']);
  const host = baseUrl.hostname.replace(/^\[|\]$/g, '').toLowerCase();
  const context = await browser.newContext({
    ignoreHTTPSErrors: loopback.has(host),
    baseURL: `${baseUrl.toString().replace(/\/$/, '')}/`,
  });
  const page = await context.newPage();

  try {
    await login(page);

    await gotoApp(page, baseUrl, billEntryPath());
    const entry = await describeBillPage(page);
    check('the Ontario bill form opens for the configured appointment', entry.isOntarioForm,
      `codes=${JSON.stringify(entry.codes)} at ${redactUrl(entry.url)}; set BILLING_APPOINTMENT_NO/BILLING_DEMOGRAPHIC_NO to a billable appointment`);
    if (!entry.isOntarioForm) return;

    const options = await page.locator('select[name="xml_billtype"] option')
      .evaluateAll((nodes) => nodes.map((node) => node.value));
    const wanted = [...THIRD_PARTY_PREFIXES, BONUS_PREFIX]
      .map((prefix) => ({ prefix, value: options.find((value) => value.startsWith(prefix)) }))
      .filter((option) => Boolean(option.value));

    // Every expected code must be present. A `some()` here would let a code
    // disappear (OCF, STD...) while the check still passed and the loop below
    // silently stopped covering that branch.
    const expected = [...THIRD_PARTY_PREFIXES, BONUS_PREFIX];
    const missing = expected.filter((prefix) => !wanted.some((o) => o.prefix === prefix));
    check('the bill-type control offers every 3rd-party and bonus-code type',
      missing.length === 0,
      `missing: ${JSON.stringify(missing)}; offered: ${JSON.stringify(options)}`);

    for (const { prefix, value } of wanted) {
      const outcome = await switchBillType(page, value, prefix);
      const label = prefix === BONUS_PREFIX ? `bonus codes (${prefix})` : `3rd party (${prefix})`;

      check(`${label} actually navigates on selection`,
        outcome.navigated && outcome.carriesSelectedType,
        `navigated=${outcome.navigated} carriesType=${outcome.carriesSelectedType} url=${redactUrl(outcome.url)}`);
      // The fix itself: the self-navigation must carry the region forward.
      check(`${label} keeps billRegion=ON on its navigation`,
        queryParam(outcome.url, 'billRegion') === 'ON', `url was ${redactUrl(outcome.url)}`);
      // What the user saw when it did not: a 500, or the BC form.
      check(`${label} stays on the Ontario bill form`, outcome.isOntarioForm,
        `codes=${JSON.stringify(outcome.codes)} url=${redactUrl(outcome.url)}`);
      check(`${label} does not render an error page`,
        !outcome.hasErrorPage && (outcome.documentStatus === null || outcome.documentStatus < 400),
        `status=${outcome.documentStatus} errorPage=${outcome.hasErrorPage}`);
    }

    // The sibling fix: Ontario unbilled "Bill" links are context-path prefixed
    // (they used to resolve at the host root and 404) and carry the region.
    // reportAction=unbilled is what populates the rows; without it the
    // assembler returns an empty model and the report renders nothing.
    const reportParams = new URLSearchParams({ reportAction: 'unbilled', providerview: providerNo });
    for (const report of ['/billing/CA/ON/ViewBillingONNewReport', '/billing/CA/ON/ViewBillingReportControl']) {
      const path = `${report}?${reportParams.toString()}`;
      // An unreachable route or an error page must fail, not fall through to
      // the empty-list branch and be reported as a dataset skip.
      let status = null;
      try {
        const response = await gotoApp(page, baseUrl, path);
        status = response ? response.status() : null;
      } catch (error) {
        status = null;
      }
      const loaded = status !== null && status < 400;
      check(`${report} loads its unbilled report`, loaded, `status=${status === null ? 'no response' : status}`);
      if (!loaded) continue;

      const links = await unbilledBillLinks(page);
      if (links.length === 0) {
        console.log(`SKIP ${report} produced no unbilled "Bill" link on this dataset`);
        continue;
      }
      const redacted = links.slice(0, 3).map(redactUrl);
      check(`${report} "Bill" links are context-path prefixed`,
        links.every((href) => href.startsWith(`${contextPath}/billing?`)),
        `hrefs: ${JSON.stringify(redacted)}`);
      check(`${report} "Bill" links carry billRegion=ON`,
        links.every((href) => queryParam(href, 'billRegion') === 'ON'),
        `hrefs: ${JSON.stringify(redacted)}`);
    }
  } finally {
    await browser.close();
  }
}

run()
  .catch((error) => check('script completed without error', false, error.message))
  .finally(() => {
    // buildArtifactPath validates the directory (restricted to /tmp or the
    // workspace) and creates it; no unvalidated mkdir here.
    fs.writeFileSync(buildArtifactPath(artifactDir, 'results', '.json'), JSON.stringify(results, null, 2));
    const failed = results.filter((result) => !result.ok);
    console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
    process.exit(failed.length ? 1 : 0);
  });
