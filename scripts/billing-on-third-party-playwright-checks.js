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
 * table. The bill entry was lost with the 500. The same release also prefixed
 * the Ontario unbilled "Bill" links with the context path (they 404'd at the
 * host root) and put billRegion=ON on them.
 *
 * Why the region assertion and not just "no 500": the fall-back is a property,
 * `billregion`, and an install that HAS it set (the packaged Ontario install
 * does) still routes correctly with the query parameter missing. Asserting only
 * the status code would therefore pass on a re-broken page. This check asserts
 * the thing the fix actually pins -- billRegion travelling on every self
 * navigation -- and treats the rendered Ontario form and the absence of an
 * error page as the user-visible corroboration.
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
 *   BILLING_ON_SCREENSHOT_DIR=/tmp/carlos-billing-on-playwright
 *   ALLOW_NON_LOCAL_BASE_URL=true for any target that is not loopback
 */
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const { buildArtifactPath } = require('./eform-local-playwright-utils');

/*
 * Hosts this script will browse without an explicit opt-in. It logs in as an
 * administrator, so anything that is not plainly this machine or its compose
 * network has to be opted into.
 */
const LOCAL_HOSTS = new Set(['localhost', '127.0.0.1', '::1', '0.0.0.0', 'host.docker.internal', 'db', 'carlos']);
const EXACT_LOCAL_HOSTS = new Set(['localhost', '127.0.0.1', '::1', 'db', 'carlos']);

function normalizeHost(rawHost) {
  return rawHost.toLowerCase().replace(/^\[|\]$/g, '');
}

function isLocalHost(rawHost) {
  return LOCAL_HOSTS.has(normalizeHost(rawHost));
}

function isExactLocalHost(rawHost) {
  return EXACT_LOCAL_HOSTS.has(normalizeHost(rawHost));
}

function validateBaseUrl(rawBaseUrl) {
  const parsed = new URL(rawBaseUrl);
  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw new Error(`BASE_URL must use http or https, got ${parsed.protocol}`);
  }
  // Credentials in the URL would travel into navigations and can surface in
  // failure logging, so reject them outright.
  if (parsed.username || parsed.password) {
    throw new Error('BASE_URL must not contain embedded credentials');
  }
  if (!isLocalHost(parsed.hostname) && process.env.ALLOW_NON_LOCAL_BASE_URL !== 'true') {
    throw new Error(`Refusing non-local BASE_URL host ${parsed.hostname}; set ALLOW_NON_LOCAL_BASE_URL=true for an intentional test target`);
  }
  // This script logs in with real credentials, so anything that is not plainly
  // loopback has to prove its certificate.
  if (!isExactLocalHost(parsed.hostname) && parsed.protocol !== 'https:') {
    throw new Error(`Non-loopback BASE_URL host ${parsed.hostname} must use https`);
  }
  parsed.pathname = parsed.pathname.replace(/\/$/, '');
  return parsed.toString().replace(/\/$/, '');
}

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const contextPath = new URL(baseUrl).pathname || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';
const screenshotDir = process.env.BILLING_ON_SCREENSHOT_DIR || '/tmp/carlos-billing-on-playwright';

const appointmentNo = process.env.BILLING_APPOINTMENT_NO || '11';
const demographicNo = process.env.BILLING_DEMOGRAPHIC_NO || '1';
const providerNo = process.env.BILLING_PROVIDER_NO || '999998';
const appointmentDate = process.env.BILLING_APPOINTMENT_DATE || '2024-04-16';
const startTime = process.env.BILLING_START_TIME || '12:00:00';

/*
 * The bill types whose onChangePrivate() branch rebuilds the URL — these are
 * exactly the ones that lost billRegion. PAT/OCF/ODS/CPP/STD route through
 * curBillForm=PRI ("3rd party"), BON through the primary-care-incentive view
 * ("Bonus Codes").
 */
const THIRD_PARTY_PREFIXES = ['PAT', 'OCF', 'ODS', 'CPP', 'STD'];
const BONUS_PREFIX = 'BON';

const results = [];

function check(name, ok, detail) {
  results.push({ name, ok: Boolean(ok), detail: detail || '' });
  console.log(`${ok ? 'PASS' : 'FAIL'} ${name}${ok || !detail ? '' : `: ${detail}`}`);
}

function billEntryUrl() {
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
  return `./billing?${params.toString()}`;
}

async function login(page) {
  await page.goto('./', { waitUntil: 'domcontentloaded', timeout: 60000 });
  await page.locator('#username').fill(testUser);
  await page.locator('#password').fill(testPassword);
  await page.locator('#pin').fill(testPin);
  await page.locator('input[type="submit"], button[type="submit"]').first().click();
  await page.waitForLoadState('domcontentloaded', { timeout: 60000 });
}

/* The Ontario bill form is identified by its own bill-type control; billingBC.jsp
   has no xml_billtype select, which is precisely how the misroute showed up. */
async function describeBillPage(page) {
  const body = await page.content();
  return {
    url: page.url(),
    isOntarioForm: (await page.locator('select[name="xml_billtype"]').count()) > 0,
    hasErrorPage: /CARLOS Error/i.test(body),
    mentionsBcTable: /billingvisit/i.test(body),
  };
}

async function switchBillType(page, optionValue) {
  await page.goto(billEntryUrl(), { waitUntil: 'domcontentloaded', timeout: 60000 });
  const select = page.locator('select[name="xml_billtype"]');
  await select.waitFor({ state: 'attached', timeout: 60000 });

  let documentStatus = null;
  const onResponse = (response) => {
    if (response.request().resourceType() === 'document') documentStatus = response.status();
  };
  page.on('response', onResponse);
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 60000 }).catch(() => null),
    select.selectOption(optionValue),
  ]);
  page.off('response', onResponse);

  return { documentStatus, ...(await describeBillPage(page)) };
}

async function run() {
  const browser = await chromium.launch({
    executablePath: process.env.CHROME_PATH || undefined,
    args: ['--no-sandbox'],
  });
  const context = await browser.newContext({ ignoreHTTPSErrors: true, baseURL: `${baseUrl}/` });
  const page = await context.newPage();

  try {
    await login(page);

    await page.goto(billEntryUrl(), { waitUntil: 'domcontentloaded', timeout: 60000 });
    const entry = await describeBillPage(page);
    check('the Ontario bill form opens for the configured appointment', entry.isOntarioForm,
      `not the Ontario form at ${entry.url}; set BILLING_APPOINTMENT_NO/BILLING_DEMOGRAPHIC_NO to a billable appointment`);
    if (!entry.isOntarioForm) return;

    const options = await page.locator('select[name="xml_billtype"] option')
      .evaluateAll((nodes) => nodes.map((node) => node.value));

    const wanted = [...THIRD_PARTY_PREFIXES, BONUS_PREFIX]
      .map((prefix) => ({ prefix, value: options.find((value) => value.startsWith(prefix)) }))
      .filter((entryOption) => Boolean(entryOption.value));

    check('the bill-type control offers 3rd-party and bonus-code types',
      wanted.some((o) => THIRD_PARTY_PREFIXES.includes(o.prefix)) && wanted.some((o) => o.prefix === BONUS_PREFIX),
      `offered: ${JSON.stringify(options)}`);

    for (const { prefix, value } of wanted) {
      const outcome = await switchBillType(page, value);
      const label = prefix === BONUS_PREFIX ? `bonus codes (${prefix})` : `3rd party (${prefix})`;

      // The fix itself: the self-navigation must carry the region forward.
      check(`${label} keeps billRegion=ON on its navigation`,
        /[?&]billRegion=ON(&|$)/.test(outcome.url),
        `url was ${outcome.url}`);

      // What the user saw when it did not: a 500, or the BC form.
      check(`${label} stays on the Ontario bill form`,
        outcome.isOntarioForm && !outcome.mentionsBcTable,
        `ontarioForm=${outcome.isOntarioForm} bcTable=${outcome.mentionsBcTable} url=${outcome.url}`);
      check(`${label} does not render an error page`,
        !outcome.hasErrorPage && (outcome.documentStatus === null || outcome.documentStatus < 400),
        `status=${outcome.documentStatus} errorPage=${outcome.hasErrorPage}`);
    }

    // The sibling fix: Ontario unbilled "Bill" links are context-path prefixed
    // (they used to resolve at the host root and 404) and carry the region.
    for (const report of ['./billing/CA/ON/ViewBillingONNewReport', './billing/CA/ON/ViewBillingReportControl']) {
      await page.goto(report, { waitUntil: 'domcontentloaded', timeout: 60000 }).catch(() => null);
      const billLinks = await page.locator('a[href*="/billing?"]')
        .evaluateAll((nodes) => nodes.map((node) => node.getAttribute('href')).filter(Boolean));
      const entryLinks = billLinks.filter((href) => /billForm=/.test(href));
      if (entryLinks.length === 0) {
        console.log(`SKIP ${report} exposes no unbilled "Bill" link on this dataset`);
        continue;
      }
      check(`${report} "Bill" links are context-path prefixed`,
        entryLinks.every((href) => href.startsWith(`${contextPath}/billing?`) || /^https?:\/\//.test(href)),
        `hrefs: ${JSON.stringify(entryLinks.slice(0, 3))}`);
      check(`${report} "Bill" links carry billRegion=ON`,
        entryLinks.every((href) => /[?&]billRegion=ON(&|$)/.test(href)),
        `hrefs: ${JSON.stringify(entryLinks.slice(0, 3))}`);
    }
  } finally {
    await browser.close();
  }
}

run()
  .catch((error) => check('script completed without error', false, error.message))
  .finally(() => {
    fs.mkdirSync(screenshotDir, { recursive: true });
    fs.writeFileSync(buildArtifactPath(screenshotDir, 'results', '.json'), JSON.stringify(results, null, 2));
    const failed = results.filter((result) => !result.ok);
    console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
    process.exit(failed.length ? 1 : 0);
  });
