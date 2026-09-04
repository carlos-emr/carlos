#!/usr/bin/env node
/*
 * Browser regression checks for the CARLOS eChart first render and CPP saves.
 *
 * The script follows the same links a user follows: login, schedule Search,
 * patient result, patient page E-Chart link, then the Social History plus icon
 * and save action. It is intentionally narrow because it guards the filter/JSP
 * interaction that can leave the eChart without its clinical-notes DOM or
 * JavaScript handlers, plus the shared save callback used by all CPP sections.
 *
 * Defaults are for the local devcontainer:
 *   node scripts/echart-playwright-checks.js
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc
 *   TEST_PASSWORD=carlos2026
 *   TEST_PIN=2026
 *   ECHART_SEARCH_TERM=FAKE-J
 *   ECHART_DEMOGRAPHIC_NO=1
 *   ECHART_SCREENSHOT_DIR=/tmp
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const { chromium } = require('playwright');
const { buildArtifactPath } = require('./eform-local-playwright-utils');

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';
const searchTerm = process.env.ECHART_SEARCH_TERM || 'FAKE-J';
const demographicNo = process.env.ECHART_DEMOGRAPHIC_NO || '1';
const screenshotDir = process.env.ECHART_SCREENSHOT_DIR || '/tmp';

if (!/^\d+$/.test(demographicNo)) {
  throw new Error(`ECHART_DEMOGRAPHIC_NO must contain digits only, got ${demographicNo}`);
}

const captures = [];
const badResponses = [];
const consoleIssues = [];
const notesLoadRequests = [];

// The notes list pages in older notes from a 1s poll, so "settled" means no new fetch
// for several poll ticks. The overall cap keeps a legitimately long chart from hanging
// the check while still failing the runaway-pagination regression.
const NOTES_POLL_QUIET_MS = 4000;
const NOTES_POLL_TIMEOUT_MS = 30000;

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

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

function isExpectedMissingFixtureImage(status, responseUrl) {
  return status === 404 && /\/imageRenderingServlet\?/.test(responseUrl);
}

function isBlockingConsoleMessage(message) {
  const text = message.text();
  const locationUrl = message.location().url || '';
  if (message.type() === 'error') {
    return !/\/imageRenderingServlet\?/.test(locationUrl);
  }
  return /(ReferenceError|SyntaxError|TypeError|MAXNOTES|notesLoading|encMainDiv|newNoteImg|Cannot read properties)/i.test(text);
}

function wirePage(page, label) {
  page.on('dialog', async (dialog) => {
    consoleIssues.push({ label, type: 'dialog', text: dialog.message() });
    await dialog.accept();
  });
  page.on('request', (request) => {
    const postData = request.postData() || '';
    if (/(?:^|&)method=viewNotesOpt(?:&|$)/.test(postData)) {
      const offsetMatch = /(?:^|&)offset=(\d+)/.exec(postData);
      notesLoadRequests.push({ label, offset: offsetMatch ? Number(offsetMatch[1]) : null });
    }
  });
  page.on('response', async (response) => {
    const responseUrl = response.url();
    const status = response.status();
    const contentType = response.headers()['content-type'] || '';
    if (status >= 400 && !isExpectedMissingFixtureImage(status, responseUrl)) {
      badResponses.push({ label, status, url: responseUrl, contentType });
    }
    if (/CaseManagement(View|Entry)|ViewNewEncounterLayoutJs|newCaseManagementView/i.test(responseUrl)) {
      let bodyLength = 0;
      try {
        bodyLength = (await response.text()).length;
      } catch (error) {
        captures.push({ label, status, url: responseUrl, contentType, unreadable: error.message });
        return;
      }
      captures.push({ label, status, url: responseUrl, contentType, bodyLength });
    }
  });
  page.on('console', (message) => {
    if (isBlockingConsoleMessage(message)) {
      consoleIssues.push({ label, type: message.type(), text: message.text(), location: message.location() });
    }
  });
  page.on('pageerror', (error) => {
    consoleIssues.push({ label, type: 'pageerror', text: error.stack || error.message });
  });
}

async function loginAndOpenSearch(context) {
  const page = await context.newPage();
  wirePage(page, 'schedule');
  await page.goto(appUrl('/'), { waitUntil: 'domcontentloaded' }); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- appUrl rejects non-root-relative paths and validateBaseUrl restricts hosts to local/private by default
  await page.locator('#username').fill(testUser);
  await page.locator('#password').fill(testPassword);
  await page.locator('#pin').fill(testPin);
  await Promise.all([
    page.waitForURL(/providercontrol/, { timeout: 30000 }),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
  await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

  const searchPopup = context.waitForEvent('page');
  await page.locator('a').filter({ hasText: /^Search$/ }).click();
  const searchPage = await searchPopup;
  wirePage(searchPage, 'search');
  await searchPage.waitForLoadState('domcontentloaded', { timeout: 30000 });
  return searchPage;
}

async function openPatientEchart(context, searchPage) {
  await searchPage.locator('#keyword, input[name="keyword"]').first().fill(searchTerm);
  await Promise.all([
    searchPage.waitForLoadState('domcontentloaded').catch(() => {}),
    searchPage.locator('input[type="submit"][value="Search"]').first().click(),
  ]);
  await searchPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

  const patientPopup = context.waitForEvent('page');
  await searchPage.locator(`a[onclick*='DemographicEdit?demographic_no=${demographicNo}']`).first().click();
  const patientPage = await patientPopup;
  wirePage(patientPage, 'patient');
  await patientPage.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});

  const echartPopup = context.waitForEvent('page');
  await patientPage.locator("a[title='E-Chart']").first().click();
  const echart = await echartPopup;
  wirePage(echart, 'echart');
  await echart.waitForLoadState('domcontentloaded', { timeout: 30000 });
  await echart.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
  return echart;
}

async function elementState(page, selector) {
  return page.locator(selector).first().evaluate((element) => {
    const rect = element.getBoundingClientRect();
    return {
      visible: !!(rect.width || rect.height || element.getClientRects().length),
      display: getComputedStyle(element).display,
      visibility: getComputedStyle(element).visibility,
      text: (element.innerText || element.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 500),
      htmlLength: element.innerHTML.length,
    };
  });
}

async function assertVisible(page, selector, label) {
  await page.locator(selector).first().waitFor({ state: 'attached', timeout: 15000 });
  const state = await elementState(page, selector);
  assert(state.visible, `${label} was attached but not visible: ${JSON.stringify(state)}`);
  assert(state.display !== 'none' && state.visibility !== 'hidden', `${label} was hidden: ${JSON.stringify(state)}`);
  return state;
}

/**
 * Parks the notes list at the top of the chart and waits for note pagination to stop.
 *
 * Scrolling to the top arms the 1s poll that loads older notes. Once the server has no
 * more notes to give, the poll must stop and the throbber must clear. The regression this
 * guards let the poll run forever — offset 20, 40, 60, ... on an endless loop, with the
 * loading throbber up for the life of the chart — because an exhausted batch still comes
 * back as a non-empty response body.
 */
async function assertNotesPaginationSettles(page) {
  const wrapper = page.locator('#encMainDivWrapper').first();
  await wrapper.evaluate((element) => { element.scrollTop = 0; });

  const deadline = Date.now() + NOTES_POLL_TIMEOUT_MS;
  let observed = notesLoadRequests.length;
  let stableSince = Date.now();
  while (Date.now() < deadline) {
    await page.waitForTimeout(500);
    if (notesLoadRequests.length !== observed) {
      // A chart with many notes legitimately pages in several batches; restart the
      // quiet window and keep waiting for the poll to run out of notes.
      observed = notesLoadRequests.length;
      stableSince = Date.now();
    } else if (Date.now() - stableSince >= NOTES_POLL_QUIET_MS) {
      const throbber = await elementState(page, '#notesLoading');
      assert(!throbber.visible && throbber.display === 'none',
        `notes loading throbber stayed visible after pagination stopped: ${JSON.stringify(throbber)}`);
      return;
    }
  }

  throw new Error(`notes pagination never stopped while parked at the top of the chart; `
    + `${notesLoadRequests.length} viewNotesOpt requests: ${JSON.stringify(notesLoadRequests)}`);
}

async function screenshot(page, name) {
  await page.screenshot({ path: buildArtifactPath(screenshotDir, name), fullPage: true }); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- buildArtifactPath constrains output to a validated local artifact directory with a sanitized basename
}

async function archiveCppNote(page, noteText) {
  const noteLink = page.locator("#divR1I1 a[id^='listNote']").filter({ hasText: noteText }).first();
  if (!(await noteLink.isVisible().catch(() => false))) {
    return false;
  }

  await noteLink.click();
  await assertVisible(page, '#showEditNote', 'saved Social History editor during cleanup');
  await page.locator("#frmIssueNotes input[type='image'][src*='edit-cut.png']").click();
  await noteLink.waitFor({ state: 'detached', timeout: 15000 });
  return true;
}

function isUnresolvedIssuesResponse(response) {
  const url = new URL(response.url());
  return url.pathname.endsWith('/encounter/displayIssues')
    && url.searchParams.get('cmd') === 'unresolvedIssues'
    && url.searchParams.get('demographicNo') === demographicNo;
}

function isExpectedNoteLockDialog(issue) {
  return issue.type === 'dialog' && /started to edit this note in another window/i.test(issue.text);
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
    const context = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1100 } });
    const searchPage = await loginAndOpenSearch(context);
    const echart = await openPatientEchart(context, searchPage);

    await assertVisible(echart, '#encMainDivWrapper', 'clinical notes wrapper');
    const notes = await assertVisible(echart, '#encMainDiv', 'clinical notes container');
    assert(notes.htmlLength > 1000, `clinical notes container was unexpectedly small: ${notes.htmlLength}`);
    await assertVisible(echart, '#newNoteImg', 'new-note icon');
    await assertVisible(echart, "#divR1I1 a[title='Add Item']", 'Social History plus icon');
    await screenshot(echart, 'echart-initial');

    await assertNotesPaginationSettles(echart);

    await echart.locator("#divR1I1 a[title='Add Item']").first().click();
    const editor = await assertVisible(echart, '#showEditNote', 'Social History editor');
    assert(/Social History/i.test(editor.text), `Social History editor did not contain its expected label: ${editor.text}`);
    await screenshot(echart, 'echart-after-social-history-plus');

    const cppNote = `Playwright Social History ${Date.now()}`;
    let saveFailure = null;
    let cleanupFailure = null;
    let saveConfirmed = false;
    try {
      await echart.locator('#noteEditTxt').fill(cppNote);
      const unresolvedIssuesResponse = echart.waitForResponse(isUnresolvedIssuesResponse, { timeout: 15000 });
      await echart.locator("#frmIssueNotes input[type='image'][src*='note-save.png']").click();

      const refreshResponse = await unresolvedIssuesResponse;
      assert(refreshResponse.ok(),
        `Unresolved Issues refresh failed with HTTP ${refreshResponse.status()}: ${refreshResponse.url()}`);
      await echart.locator('#divR1I1').filter({ hasText: cppNote }).waitFor({ state: 'visible', timeout: 15000 });
      saveConfirmed = true;
      await screenshot(echart, 'echart-after-social-history-save');
    } catch (error) {
      saveFailure = error;
    } finally {
      try {
        const archived = await archiveCppNote(echart, cppNote);
        if (saveConfirmed && !archived) {
          cleanupFailure = new Error('saved Social History item was not available to archive');
        }
      } catch (error) {
        cleanupFailure = error;
      }
    }

    // A failed request is the root cause of most save timeouts, so report it before
    // the save/cleanup errors, which would otherwise surface only as an opaque wait.
    assert(badResponses.length === 0, `unexpected HTTP errors: ${JSON.stringify(badResponses, null, 2)}`);

    if (saveFailure && cleanupFailure) {
      throw new AggregateError([saveFailure, cleanupFailure], 'Social History save and cleanup both failed');
    }
    if (saveFailure) {
      throw saveFailure;
    }
    if (cleanupFailure) {
      throw new Error(`saved Social History cleanup failed: ${cleanupFailure.message}`, { cause: cleanupFailure });
    }

    const fatalConsoleIssues = consoleIssues
      .filter((issue) => !isExpectedNoteLockDialog(issue));
    assert(fatalConsoleIssues.length === 0,
      `unexpected browser console failures: ${JSON.stringify(fatalConsoleIssues, null, 2)}`);

    console.log('PASS eChart clinical notes rendered, note pagination stopped at end of chart, '
      + 'Social History saved and archived, and Unresolved Issues refreshed');
    console.log(`Observed ${notesLoadRequests.length} note pagination requests`);
    console.log(`Observed ${captures.length} eChart-related responses`);
    if (consoleIssues.length) {
      console.log(`Non-blocking browser diagnostics: ${JSON.stringify(consoleIssues, null, 2)}`);
    }
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error('FAIL eChart Playwright check');
  console.error(error.stack || error.message);
  if (badResponses.length) {
    console.error(`HTTP errors: ${JSON.stringify(badResponses, null, 2)}`);
  }
  if (consoleIssues.length) {
    console.error(`Console issues: ${JSON.stringify(consoleIssues, null, 2)}`);
  }
  if (captures.length) {
    console.error(`Captured eChart responses: ${JSON.stringify(captures, null, 2)}`);
  }
  process.exit(1);
});
