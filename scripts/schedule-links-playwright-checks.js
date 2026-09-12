#!/usr/bin/env node
/*
 * Browser regression checks for high-value schedule-page links.
 *
 * The script follows the same links a user sees after login, opening the
 * schedule top-nav links for Tickler, Consultations, Scratch Pad, Reports,
 * Inbox, and eDoc. It fails on unexpected HTTP errors, CARLOS error pages, and
 * severe browser JavaScript failures.
 *
 * Defaults are for the local devcontainer:
 *   node scripts/schedule-links-playwright-checks.js
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc
 *   TEST_PASSWORD=carlos2026
 *   TEST_PIN=2026
 *   TEST_PROVIDER_LAST_NAME=optional provider last-name prefix; blank lists active providers
 *   TEST_PROVIDER_SEARCH_ONLY=true to skip the pre-existing schedule-link checks
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const { chromium } = require('playwright');

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';
const testProviderLastName = process.env.TEST_PROVIDER_LAST_NAME || '';
const testProviderSearchOnly = process.env.TEST_PROVIDER_SEARCH_ONLY === 'true';

// Mirrors org.owasp.csrfguard.TokenName; used only when the rendered name cannot be read.
const DEFAULT_CSRF_TOKEN_NAME = 'CSRF-TOKEN';

const findings = [];
const visited = [];

const scheduleLinks = [
  { label: 'schedule-tickler-link', selector: "a[onclick*='/carlos/tickler/ViewTicklerMain']" },
  { label: 'schedule-consultations-link', selector: "a[onclick*='/carlos/encounter/IncomingConsultation']" },
  { label: 'schedule-scratch-link', selector: "a[onclick*='/carlos/Scratch']" },
  { label: 'schedule-report-link', selector: "a[onclick*='/carlos/report/ViewReportindex']" },
  { label: 'schedule-inbox-link', selector: "a[onclick*='/carlos/web/inboxhub/Inboxhub']" },
  { label: 'schedule-edoc-link', selector: "a[onclick*='/carlos/documentManager/ViewDocumentReport']" },
];

function validateBaseUrl(rawBaseUrl) {
  const parsed = new URL(rawBaseUrl);
  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw new Error(`BASE_URL must use http or https, got ${parsed.protocol}`);
  }

  const host = parsed.hostname.toLowerCase();
  const localHosts = new Set(['localhost', '127.0.0.1', '::1', '0.0.0.0', 'host.docker.internal', 'carlos']);
  const privateIpv4 = /^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[0-1])\.)/.test(host);
  if (!localHosts.has(host) && !privateIpv4 && process.env.ALLOW_NON_LOCAL_BASE_URL !== 'true') {
    throw new Error(`Refusing non-local BASE_URL host ${host}; set ALLOW_NON_LOCAL_BASE_URL=true for an intentional test target`);
  }
  parsed.pathname = parsed.pathname.replace(/\/$/, '');
  return parsed;
}

function appUrl(appPath) {
  if (!appPath.startsWith('/') || appPath.startsWith('//')) {
    throw new Error(`Application path must be root-relative, got ${appPath}`);
  }
  const url = new URL(baseUrl.href);
  url.pathname = `${baseUrl.pathname}${appPath}`.replace(/\/{2,}/g, '/');
  url.search = '';
  return url.toString();
}

function safeGoto(page, appPath, options) {
  return page.goto(appUrl(appPath), options); // nosemgrep // NOSONAR - appUrl validates local-only BASE_URL and root-relative paths.
}

async function responseWithTimeout(request, timeoutMs) {
  let timeoutId;
  try {
    return await Promise.race([
      request.response(),
      new Promise((resolve) => {
        timeoutId = setTimeout(() => resolve(null), timeoutMs);
      }),
    ]);
  } finally {
    clearTimeout(timeoutId);
  }
}

function isExpectedMissingAsset(status, responseUrl) {
  return status === 404 && (/\/imageRenderingServlet\?/.test(responseUrl) || /\/favicon\.ico$/.test(responseUrl));
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
  return /(ReferenceError|TypeError|SyntaxError|DataTable is not a function|redeclaration|Cannot read|Cannot set)/i.test(text);
}

function wirePage(page, label) {
  page.on('response', (response) => {
    const responseUrl = response.url();
    const status = response.status();
    if (status >= 400 && !isExpectedMissingAsset(status, responseUrl)) {
      findings.push({ label, type: 'http', status, url: responseUrl });
    }
  });
  page.on('console', (message) => {
    if (isSevereConsoleMessage(message)) {
      findings.push({ label, type: `console:${message.type()}`, text: message.text(), location: message.location() });
    }
  });
  page.on('pageerror', (error) => {
    findings.push({ label, type: 'pageerror', text: error.stack || error.message });
  });
  page.on('dialog', async (dialog) => {
    findings.push({ label, type: 'dialog', text: dialog.message() });
    await dialog.accept();
  });
}

async function assertNoErrorPage(page, label) {
  const bodyText = await page.locator('body').innerText().catch(() => '');
  if (/CARLOS has encountered an unexpected error|HTTP Status 500|Exception Report/i.test(bodyText)) {
    findings.push({
      label,
      type: 'error-page',
      url: page.url(),
      body: bodyText.replace(/\s+/g, ' ').slice(0, 500),
    });
  }
}

async function login(context) {
  const page = await context.newPage();
  wirePage(page, 'schedule');
  await safeGoto(page, '/', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.locator('#username').fill(testUser);
  await page.locator('#password').fill(testPassword);
  await page.locator('#pin').fill(testPin);
  await Promise.all([
    page.waitForURL(/providercontrol/, { timeout: 30000 }),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  visited.push({ label: 'schedule', url: page.url() });
  await assertNoErrorPage(page, 'schedule');
  return page;
}

async function clickScheduleLink(context, schedulePage, linkSpec) {
  const locator = schedulePage.locator(linkSpec.selector).first();
  // Wait for the link to attach rather than reading count() once: selecting a provider
  // reloads the schedule window, and its navigation commits (URL updates) before the body
  // renders, so an immediate count() races the reload and reports every link missing.
  try {
    await locator.waitFor({ state: 'attached', timeout: 15000 });
  } catch {
    findings.push({ label: linkSpec.label, type: 'missing-user-link', selector: linkSpec.selector });
    return;
  }

  const existingPages = context.pages();
  const popupPromise = context.waitForEvent('page', { timeout: 10000 }).catch(() => null);
  await locator.click();
  let targetPage = await popupPromise;

  if (!targetPage) {
    const currentPages = context.pages();
    targetPage = currentPages.find((page) => !existingPages.includes(page)) || currentPages[currentPages.length - 1];
  }

  wirePage(targetPage, linkSpec.label);
  await targetPage.waitForLoadState('domcontentloaded', { timeout: 30000 }).catch(() => {});
  await targetPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  visited.push({ label: linkSpec.label, url: targetPage.url() });
  await assertNoErrorPage(targetPage, linkSpec.label);
}

async function selectProviderFromLastNameSearch(context, schedulePage) {
  const searchInput = schedulePage.locator('form[name="findprovider"] input[name="providername"]');
  if (!await searchInput.count()) {
    findings.push({ label: 'schedule-provider-search', type: 'missing-search-input' });
    return;
  }

  const requestPromise = context.waitForEvent('request', {
    predicate: (request) => request.method() === 'POST'
      && /\/provider\/providercontrol(?:\?|$)/.test(request.url()),
    timeout: 30000,
  }).catch(() => null);
  const providerSearchPath = new URL(appUrl('/provider/ViewReceptionistFindProvider')).pathname;
  const tokenNamePromise = context.waitForEvent('response', {
    predicate: (response) => response.request().resourceType() === 'document'
      && response.status() === 200
      && new URL(response.url()).pathname === providerSearchPath,
    timeout: 30000,
  }).then((response) => response.text())
    .then((html) => {
      const match = /id="providerSelectionCsrfToken"[^>]*\sname="([^"]+)"/.exec(html);
      return match ? match[1] : null;
    })
    .catch(() => null);
  const popupPromise = context.waitForEvent('page', { timeout: 10000 }).catch(() => null);
  await searchInput.fill(testProviderLastName);
  await searchInput.press('Enter');
  const resultPage = await popupPromise;
  if (!resultPage) {
    findings.push({ label: 'schedule-provider-search', type: 'missing-search-popup' });
    return;
  }
  wirePage(resultPage, 'schedule-provider-search');

  const resultLoadedPromise = resultPage.waitForLoadState('domcontentloaded', { timeout: 30000 })
    .then(() => true)
    .catch(() => false);
  let providerRequest = await Promise.race([
    requestPromise,
    resultLoadedPromise.then(() => null),
  ]);
  if (!providerRequest) {
    const resultLoaded = await resultLoadedPromise;
    if (resultLoaded) {
      await resultPage.locator('a[onclick*="selectProvider("]').first()
        .click({ timeout: 5000 })
        .catch(() => {});
    }
    providerRequest = await requestPromise;
    if (!providerRequest && !resultLoaded) {
      findings.push({ label: 'schedule-provider-search', type: 'search-popup-load-failure' });
    }
  }

  if (!providerRequest) {
    if (!findings.some((finding) => finding.label === 'schedule-provider-search'
        && finding.type === 'search-popup-load-failure')) {
      findings.push({ label: 'schedule-provider-search', type: 'missing-provider-result' });
    }
    await resultPage.close();
    return;
  }
  const response = await responseWithTimeout(providerRequest, 30000);
  const requestBody = providerRequest.postData() || '';

  // The page event is emitted only after the initial response starts loading, and every
  // selection navigates the popup by submitting the generated form. The context response
  // wait above is therefore registered before the search and preserves the result HTML.
  const capturedTokenName = await tokenNamePromise;
  const observedTokenName = capturedTokenName
    || await resultPage.locator('#providerSelectionCsrfToken')
      .getAttribute('name', { timeout: 2000 })
      .catch(() => null);
  if (!observedTokenName) {
    findings.push({ label: 'schedule-provider-search', type: 'csrf-token-name-unresolved' });
  }
  const tokenName = observedTokenName || DEFAULT_CSRF_TOKEN_NAME;

  visited.push({
    label: 'schedule-provider-search',
    url: providerRequest.url(),
    status: response ? response.status() : null,
  });
  if (!response) {
    findings.push({ label: 'schedule-provider-search', type: 'provider-post-no-response' });
  } else if (response.status() >= 400
      && !isExpectedMissingAsset(response.status(), response.url())
      && !findings.some((finding) => finding.label === 'schedule-provider-search'
        && finding.type === 'http' && finding.url === response.url())) {
    findings.push({
      label: 'schedule-provider-search',
      type: 'http',
      status: response.status(),
      url: response.url(),
    });
  }
  if (!new URLSearchParams(requestBody).get(tokenName)) {
    findings.push({ label: 'schedule-provider-search', type: 'missing-csrf-token', tokenName });
  }
  await resultPage.waitForLoadState('domcontentloaded', { timeout: 30000 }).catch(() => {});
  await assertNoErrorPage(resultPage, 'schedule-provider-search');
  await resultPage.close();
  // Selecting a provider navigates the MAIN schedule window (the popup submits into
  // its opener), so schedulePage is mid-reload when this returns. Wait for that reload
  // to settle before the caller asserts the schedule nav links — otherwise every link
  // check races a torn-down DOM and reports a false "missing-user-link".
  await schedulePage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
}

(async () => {
  const launchOptions = {
    headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  };
  if (chromePath) {
    launchOptions.executablePath = chromePath;
  }

  const browser = await chromium.launch(launchOptions);
  try {
    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1000 } });
    const schedulePage = await login(context);

    await selectProviderFromLastNameSearch(context, schedulePage);

    if (!testProviderSearchOnly) {
      for (const linkSpec of scheduleLinks) {
        await clickScheduleLink(context, schedulePage, linkSpec);
      }
    }

    console.log(JSON.stringify({ visited, findings }, null, 2));

    const blockingFindings = findings.filter((finding) => finding.type !== 'dialog');
    if (blockingFindings.length) {
      throw new Error(`schedule link browser check found ${blockingFindings.length} issue(s)`);
    }

    console.log(testProviderSearchOnly
      ? 'PASS schedule provider search submitted a CSRF-protected selection'
      : 'PASS schedule links rendered without HTTP or browser console failures');
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error('FAIL schedule link Playwright check');
  console.error(error.stack || error.message);
  process.exit(1);
});
