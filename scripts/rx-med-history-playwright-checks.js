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
 * Browser regression check for the Rx previous-instructions (*) modal.
 *
 * Required fixture:
 *   RX_MED_HISTORY_SCRIPT_ID=123 npm run test:rx-med-history-playwright
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   RX_MED_HISTORY_DEMOGRAPHIC_NO=1
 *   TEST_USER=carlosdoc
 *   TEST_PASSWORD=carlos2026
 *   TEST_PIN=2026
 *   CHROME_PATH=/path/to/chrome-or-chromium
 */

const { chromium } = require('playwright');

const baseUrl = new URL(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const scriptId = String(process.env.RX_MED_HISTORY_SCRIPT_ID || '').trim();
const demographicNo = String(process.env.RX_MED_HISTORY_DEMOGRAPHIC_NO || '1').trim();
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';

if (!/^\d+$/.test(scriptId) || !/^\d+$/.test(demographicNo)) {
  throw new Error('RX_MED_HISTORY_SCRIPT_ID and RX_MED_HISTORY_DEMOGRAPHIC_NO must be numeric');
}
if (!['http:', 'https:'].includes(baseUrl.protocol)
    || !new Set(['localhost', '127.0.0.1', '::1', '0.0.0.0', 'host.docker.internal', 'carlos']).has(baseUrl.hostname)) {
  throw new Error('BASE_URL must target a local CARLOS instance');
}
baseUrl.pathname = baseUrl.pathname.replace(/\/$/, '');

function appUrl(path) {
  if (!path.startsWith('/') || path.startsWith('//')) throw new Error(`Invalid app path: ${path}`);
  const url = new URL(baseUrl.href);
  const relative = new URL(path, 'http://app.local');
  url.pathname = `${baseUrl.pathname}${relative.pathname}`.replace(/\/{2,}/g, '/');
  url.search = relative.search;
  return url.toString();
}

async function login(page) {
  await page.goto(appUrl('/'), { waitUntil: 'domcontentloaded', timeout: 30000 }); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- appUrl permits only a local base URL and root-relative application paths.
  await page.locator('#username').fill(testUser);
  await page.locator('#password').fill(testPassword);
  if (await page.locator('#pin').count()) await page.locator('#pin').fill(testPin);
  await Promise.all([
    page.waitForURL(/providercontrol|appointment/i, { timeout: 30000 }),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
}

async function initializePrescriptionSession(page) {
  await page.goto(appUrl(`/rx/choosePatient?demographicNo=${encodeURIComponent(demographicNo)}`), { waitUntil: 'domcontentloaded', timeout: 30000 }); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- demographicNo is numeric-validated and appUrl permits only local root-relative routes.
  const result = await page.evaluate(async (currentScriptId) => {
    const csrf = (document.querySelector('input[name="CSRF-TOKEN"]') || {}).value || '';
    const body = new URLSearchParams({ scriptNo: currentScriptId, rand: String(Date.now()) });
    if (csrf) body.append('CSRF-TOKEN', csrf);
    const contextPath = window.location.pathname.split('/rx/')[0] || '';
    const response = await fetch(`${contextPath}/rx/rePrescribe2?method=reprint2`, {
      method: 'POST', credentials: 'same-origin',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8', 'X-Requested-With': 'XMLHttpRequest', 'CSRF-TOKEN': csrf },
      body,
    });
    return { status: response.status, text: await response.text() };
  }, scriptId);
  if (result.status !== 200) throw new Error(`Rx session setup failed: HTTP ${result.status} ${result.text.slice(0, 300)}`);
}

(async () => {
  const browser = await chromium.launch({ headless: true, executablePath: process.env.CHROME_PATH || undefined, args: ['--no-sandbox', '--disable-dev-shm-usage'] });
  try {
    const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    await login(page);
    await initializePrescriptionSession(page);
    await page.goto(appUrl('/rx/searchDrug'), { waitUntil: 'domcontentloaded', timeout: 30000 }); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- appUrl permits only a local base URL and root-relative application paths.

    const randomId = await page.evaluate(() => {
      const match = document.documentElement.innerHTML.match(/displayMedHistory\((\d+)\)/);
      return match ? match[1] : '';
    });
    if (!randomId) throw new Error('The prepared prescription did not expose a previous-instructions (*) action');

    const responsePromise = page.waitForResponse((response) => response.url().includes('/rx/WriteScript?parameterValue=listPreviousInstructions'));
    await page.evaluate((id) => window.displayMedHistory(id), randomId);
    const response = await responsePromise;
    if (response.status() !== 200) throw new Error(`Previous-instructions request returned HTTP ${response.status()}`);

    const iframe = page.locator('#xmaskframe');
    await iframe.waitFor({ state: 'visible', timeout: 10000 });
    const frame = await (await iframe.elementHandle()).contentFrame();
    if (!frame) throw new Error('Previous-instructions modal iframe did not load');
    const body = await frame.locator('body').innerText({ timeout: 10000 });
    if (!/Rx\s+Examples|Medication history is unavailable/i.test(body)) {
      throw new Error(`Previous-instructions modal rendered no usable content: ${body.slice(0, 300)}`);
    }
    console.log(JSON.stringify({ scriptId, demographicNo, randomId, modalText: body.replace(/\s+/g, ' ').trim() }, null, 2));
  } finally {
    await browser.close();
  }
})();
