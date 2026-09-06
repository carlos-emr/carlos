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
 * The chart carries clinical prose the OWASP CRS reads as an attack while it does
 * this (see CLINICAL_TEXT_THE_WAF_SCORES), so the run is only meaningful against
 * the packaged front door on :443 — through bare Tomcat there is no WAF to
 * false-positive and that half of the check proves nothing.
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
// Set when a response arrives through the packaged nginx front door, which is the only
// configuration where the WAF can see (and so false-positive on) the seeded clinical text.
let frontDoorObserved = false;

// The notes list pages in older notes from a 1s poll, so "settled" means no new fetch
// for several poll ticks. The overall cap keeps a legitimately long chart from hanging
// the check while still failing the runaway-pagination regression.
const NOTES_POLL_QUIET_MS = 4000;
const NOTES_POLL_TIMEOUT_MS = 30000;

// Ordinary clinical prose that the OWASP CRS scores as an attack: the pasted PACS link's
// own query string carries the literal "&cmd", which is rule 932110 (Windows command
// injection), and one CRITICAL match is the whole request at the packaged anomaly
// threshold. Every argument that carries this on POST /carlos/CaseManagementEntry —
// ARGS:value (the CPP body), ARGS:caseNote_note (the encounter note in the serialized
// form) and ARGS:note (the draft autosave) — is exempted per-argument by exclusion 1010
// in debian/assets/modsecurity/REQUEST-900-EXCLUSION-RULES-BEFORE-CRS.conf. This string is
// the check's whole point through the front door, so keep it signature-shaped: replacing
// it with clean prose makes the check green on a re-broken WAF policy.
const CLINICAL_TEXT_THE_WAF_SCORES =
  "reviewed prior imaging at http://pacs.example.org/study?id=1&cmd=view; pt's father had COPD, BP > 140/90";

// backup() re-arms every 5s and autosaves whenever the note textarea differs from the
// value the chart loaded, so one tick plus generous slack is enough to observe a draft save.
const AUTOSAVE_WAIT_MS = 20000;

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
    if (/nginx/i.test(response.headers()['server'] || '')) {
      frontDoorObserved = true;
    }
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
  // login/index.jsp renders #pin only when MfaManager.isOscarLegacyPinEnabled(); filling it
  // unconditionally throws on an install with the legacy PIN disabled and the check never runs.
  const pin = page.locator('#pin');
  if ((await pin.count()) > 0) await pin.fill(testPin);
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
  // Constrain the pane rather than trusting the chart to overflow on its own: the poll
  // only fires when the notes wrapper overflows AND sits at the top, so on a short chart
  // (or a tall window) an unforced check would report "settled" without ever arming the
  // pagination it exists to test.
  const geometry = await wrapper.evaluate((element) => {
    const original = { flex: element.style.flex, height: element.style.height };
    element.style.flex = 'none';
    element.style.height = '80px';
    element.scrollTop = 0;
    return { original, scrollHeight: element.scrollHeight, clientHeight: element.clientHeight };
  });
  assert(geometry.scrollHeight > geometry.clientHeight,
    `notes wrapper did not overflow, so the pagination poll was never armed: ${JSON.stringify(geometry)}`);

  try {
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
  } finally {
    // Hand the chart back at its real size — the Social History steps and their
    // screenshots come next, and an 80px notes pane is not the layout they mean to test.
    await wrapper.evaluate((element, original) => {
      element.style.flex = original.flex;
      element.style.height = original.height;
    }, geometry.original).catch(() => {});
  }
}

async function screenshot(page, name) {
  await page.screenshot({ path: buildArtifactPath(screenshotDir, name), fullPage: true }); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- buildArtifactPath constrains output to a validated local artifact directory with a sanitized basename
}

/**
 * Writes text into the encounter-note textarea that lives inside caseManagementEntryForm
 * and returns whatever was there before.
 *
 * It has to be the textarea inside THAT form, not merely a visible one: only what the form
 * serializes travels on the CPP save's issue-refresh POST, and the autosave reads the same
 * element. The form arrives with the AJAX render of ChartNotes.jsp into #notCPP rather than
 * with the first paint, so wait for it instead of assuming the chart is already whole. The
 * value is assigned directly rather than through fill() because the field sits behind the
 * CPP editor overlay by the time this runs.
 */
async function seedEncounterNoteText(page, text) {
  await page.locator('#caseManagementEntryForm textarea[name="caseNote_note"]')
    .first().waitFor({ state: 'attached', timeout: 15000 });
  // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- the argument is this script's own constant or a value just read back out of the same textarea; it is assigned to .value, never used to build a URL or a request target
  return page.evaluate((value) => {
    const form = document.forms['caseManagementEntryForm'];
    const textarea = form && form.querySelector('textarea[name="caseNote_note"]');
    if (!textarea) {
      throw new Error('encounter note textarea was not found inside caseManagementEntryForm');
    }
    const previous = textarea.value;
    textarea.value = value;
    return previous;
  }, text);
}

/**
 * Drops the temporary draft the autosave stored for this provider/patient/program.
 *
 * Calls the page's own deleteAutoSave(), which posts method=cancel and makes the action
 * call deleteTmpSave(). Without it the seeded text survives the run: edit() restores a
 * tmpsave on the next open of this chart, so the check would hand the next reader its own
 * attack-shaped prose as an unsaved note.
 */
async function discardEncounterNoteDraft(page) {
  const cancelled = page.waitForResponse(
    (response) => isCaseManagementEntryPost(response, 'cancel'), { timeout: 15000 });
  await page.evaluate(() => {
    if (typeof deleteAutoSave !== 'function') {
      throw new Error('deleteAutoSave() is not defined on the chart page');
    }
    deleteAutoSave();
  });
  const response = await cancelled;
  assert(response.ok(), `discarding the note draft failed with HTTP ${response.status()}`);
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

/**
 * True when the response answers a POST to the note route whose body carries `method=<name>`.
 * The method travels in the body, not the query string, on every call this check watches.
 */
function isCaseManagementEntryPost(response, method) {
  if (response.request().method() !== 'POST') {
    return false;
  }
  if (!new URL(response.url()).pathname.endsWith('/CaseManagementEntry')) {
    return false;
  }
  return new RegExp(`(?:^|&)method=${method}(?:&|$)`).test(response.request().postData() || '');
}

function isAutosaveResponse(response) {
  return isCaseManagementEntryPost(response, 'autosave');
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

    const cppNoteToken = `Playwright Social History ${Date.now()}`;
    const cppNote = `${cppNoteToken} — ${CLINICAL_TEXT_THE_WAF_SCORES}`;
    let saveFailure = null;
    let cleanupFailure = null;
    let saveConfirmed = false;
    let originalEncounterNote = null;
    try {
      // Put clinical text the CRS scores into the ENCOUNTER note before touching the CPP
      // box. The CPP save is not self-contained: its issue-refresh callback re-serializes
      // the whole caseManagementEntryForm (ARGS:caseNote_note), and the 5s draft autosave
      // posts the same text again as ARGS:note. Behind the packaged front door both were
      // 403ed while the CPP item itself saved, which is how a saved Social History entry
      // still produced "403 ... your session has expired". Leave this seeding in place —
      // without it the check drives only the one shape that already worked.
      originalEncounterNote = await seedEncounterNoteText(echart, CLINICAL_TEXT_THE_WAF_SCORES);

      // Arm the autosave wait BEFORE the save click. Waiting a fixed interval and moving on
      // would pass silently in the one case worth catching: if the note timer never re-arms,
      // no autosave is sent, ARGS:note is never exercised, and a re-broken exclusion goes
      // unnoticed. Requiring the request also settles whether assigning textarea.value is
      // enough to trigger it — backup() polls the value against origCaseNote rather than
      // listening for input events, so no synthetic event is needed, and this proves it.
      const autosaveResponse = echart.waitForResponse(isAutosaveResponse, { timeout: AUTOSAVE_WAIT_MS });

      await echart.locator('#noteEditTxt').fill(cppNote);
      const unresolvedIssuesResponse = echart.waitForResponse(isUnresolvedIssuesResponse, { timeout: 15000 });
      await echart.locator("#frmIssueNotes input[type='image'][src*='note-save.png']").click();

      const refreshResponse = await unresolvedIssuesResponse;
      assert(refreshResponse.ok(),
        `Unresolved Issues refresh failed with HTTP ${refreshResponse.status()}: ${refreshResponse.url()}`);
      await echart.locator('#divR1I1').filter({ hasText: cppNoteToken }).waitFor({ state: 'visible', timeout: 15000 });
      saveConfirmed = true;

      const draftSave = await autosaveResponse;
      assert(draftSave.ok(),
        `note draft autosave failed with HTTP ${draftSave.status()}; the encounter note text is `
        + `blocked before it reaches the application, and nothing in the UI reports it`);
      await screenshot(echart, 'echart-after-social-history-save');
    } catch (error) {
      saveFailure = error;
    } finally {
      if (originalEncounterNote !== null) {
        // Restore the note text, then drop the draft the autosave above deposited. Restoring
        // alone is not cleanup: the value now matches origCaseNote, so no later tick
        // overwrites the stored draft, and edit() restores it on the next open of this chart
        // — the run's signature-shaped test text would come back as the clinician's own
        // unsaved note. deleteAutoSave() is the page's own cancel path.
        await seedEncounterNoteText(echart, originalEncounterNote).catch(() => {});
        try {
          await discardEncounterNoteDraft(echart);
        } catch (error) {
          cleanupFailure = new Error(`note draft cleanup failed: ${error.message}`, { cause: error });
        }
      }
      try {
        const archived = await archiveCppNote(echart, cppNoteToken);
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
      + 'Social History saved and archived, note draft autosaved and discarded, and '
      + 'Unresolved Issues refreshed');
    console.log(`Observed ${notesLoadRequests.length} note pagination requests`);
    console.log(`Observed ${captures.length} eChart-related responses`);
    // Say plainly whether the WAF was in the path. This script's default BASE_URL is the
    // devcontainer's bare Tomcat, where CLINICAL_TEXT_THE_WAF_SCORES passes for the boring
    // reason that nothing inspected it — a green run there is NOT evidence that exclusion
    // 1010 is intact, and only the packaged front door on :443 can give that.
    console.log(frontDoorObserved
      ? 'WAF coverage: requests went through the packaged front door, so the '
        + 'attack-shaped clinical text exercised exclusion 1010'
      : 'WAF coverage: NONE — no front-door responses seen, so this run says nothing about '
        + 'the WAF exclusions; re-run with BASE_URL pointing at the packaged install on :443');
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
