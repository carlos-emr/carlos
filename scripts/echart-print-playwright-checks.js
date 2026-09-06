#!/usr/bin/env node
/*
 * Browser regression check for the eChart "Print Notes" button.
 *
 * The check follows the same path a clinician follows: login, open a patient's
 * eChart, type a note, open the print dialog and press Print — once per note
 * body, and once per print selection.
 *
 * It exists for a failure the devcontainer CANNOT show. In a packaged (deb)
 * deployment nginx runs ModSecurity with the OWASP CRS in blocking mode, and
 * the chart print POSTs the WHOLE encounter form, note textarea included. CRS
 * cannot score clinical prose: "Reviewed labs with the patient; find attached
 * the CBC" reads as a shell command separator and scores 10 against an inbound
 * threshold of 5, so the print came back as nginx's bare 403 with no
 * application log line — reported on 2026.08.0-alpha11 as "the chart print
 * button gives a 403 no matter what you choose to print". The exclusion that
 * fixes it is rule 1010 in
 * debian/assets/modsecurity/REQUEST-900-EXCLUSION-RULES-BEFORE-CRS.conf.
 *
 * So the note bodies below are not arbitrary: each one is a phrase measured to
 * trip a different CRS family on ARGS:caseNote_note. Against the devcontainer
 * (no WAF) this check still guards the print path itself — that every selection
 * returns a real PDF rather than an HTML error page. Against a packaged install
 * it is the guard for the WAF exclusion.
 *
 * Defaults are for the local devcontainer:
 *   node scripts/echart-print-playwright-checks.js
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc
 *   TEST_PASSWORD=carlos2026
 *   TEST_PIN=2026
 *   ECHART_DEMOGRAPHIC_NO=1
 *   ECHART_PROVIDER_NO=999998
 *   ALLOW_NON_LOCAL_BASE_URL=true only when intentionally targeting a non-local test app
 */

const { chromium } = require('playwright');

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';
const demographicNo = requireDigits(process.env.ECHART_DEMOGRAPHIC_NO || '1', 'ECHART_DEMOGRAPHIC_NO');
const providerNo = requireDigits(process.env.ECHART_PROVIDER_NO || '999998', 'ECHART_PROVIDER_NO');

const badResponses = [];
const printResults = [];

// Each note body is a phrase measured to score over the CRS inbound threshold on
// ARGS:caseNote_note through the packaged front door before rule 1010 covered
// that argument. The rule ids are what the ModSecurity audit log reported.
const NOTE_BODIES = [
  { label: 'plain prose', text: 'Routine follow up. Patient doing well.', crs: 'none' },
  { label: 'sentence semicolon', text: 'Reviewed labs with the patient; find attached the CBC and lytes.', crs: '932100/932110 attack-rce' },
  { label: 'shell-shaped cost', text: 'Cost ${45} per month; patient declined the brand.', crs: '932130 attack-rce' },
  { label: 'either-or plan', text: 'Consider amoxicillin or doxycycline; select per C&S.', crs: '932115/942350 rce+sqli' },
  { label: 'relative file path', text: 'See scanned report ../../images/ecg.png for the tracing.', crs: '930100/930110 attack-lfi' },
  { label: 'pasted report html', text: 'Result <span style="color:red">HIGH</span> flagged by the lab.', crs: '941100/941160 attack-xss' },
  { label: 'wound measurement', text: 'Wound <2cm, clean. <?> follow up in 1 week.', crs: '933100 attack-injection-php' },
];

// The bug reproduced on every selection, because the offending text rides on the
// form rather than on any checkbox. Print each one to prove that.
const PRINT_SELECTIONS = [
  { label: 'all notes', flags: [] },
  { label: 'all notes + CPP', flags: ['printCPP'] },
  { label: 'all notes + Rx', flags: ['printRx'] },
  { label: 'all notes + labs', flags: ['printLabs'] },
  { label: 'all notes + preventions', flags: ['printPreventions'] },
  { label: 'all notes + allergies', flags: ['printAllergies'] },
  { label: 'everything', flags: ['printCPP', 'printRx', 'printLabs', 'printPreventions', 'printAllergies'] },
];

const PRINT_FLAG_IDS = ['printCPP', 'printRx', 'printLabs', 'printPreventions', 'printAllergies'];

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

function requireDigits(value, name) {
  if (!/^\d+$/.test(value)) {
    throw new Error(`${name} must contain digits only, got ${value}`);
  }
  return value;
}

function appUrl(appPath, search) {
  if (!appPath.startsWith('/') || appPath.startsWith('//')) {
    throw new Error(`Application path must be root-relative, got ${appPath}`);
  }
  const url = new URL(baseUrl.href);
  url.pathname = `${baseUrl.pathname}${appPath}`.replace(/\/{2,}/g, '/');
  url.search = search || '';
  return url.toString();
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

/**
 * The encounter note lock is held per demographic and released when the note is
 * saved or the encounter is closed. Driving this many prints through one open
 * encounter outlives that lock, so the draft autosave and the save-note AJAX
 * answer 409 — the application's own "someone else holds the note" path, not the
 * WAF rejection this check exists to catch. The 403 that WOULD be the WAF still
 * fails the run.
 */
function isExpectedNoteLockConflict(status, responseUrl) {
  return status === 409 && /\/CaseManagementEntry$/.test(new URL(responseUrl).pathname);
}

function wirePage(page, label) {
  page.on('dialog', async (dialog) => {
    // "nothing to print" and the note-lock alert are the dialogs this flow can raise.
    // Record both: the first means a selection never reached the server.
    badResponses.push({ label, type: 'dialog', text: dialog.message() });
    await dialog.accept();
  });
  page.on('response', (response) => {
    const status = response.status();
    if (status >= 400 && !isExpectedNoteLockConflict(status, response.url())) {
      badResponses.push({ label, status, url: response.url() });
    }
  });
}

async function login(context) {
  const page = await context.newPage();
  // One wiring for the whole session: the eChart runs in this same window, and its
  // draft autosave timer posts the SAME note text under ARGS:note while these cases
  // run, so a regression on that argument surfaces here too. ARGS:noteTxt is NOT
  // covered — that one rides on ajaxSaveNote, which only fires on an explicit
  // save/sign, and this check deliberately never saves a note.
  wirePage(page, 'echart');
  await page.goto(appUrl('/'), { waitUntil: 'domcontentloaded' }); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- appUrl rejects non-root-relative paths and validateBaseUrl restricts hosts to local/private by default
  await page.locator('#username').fill(testUser);
  await page.locator('#password').fill(testPassword);
  await page.locator('#pin').fill(testPin);
  await Promise.all([
    page.waitForURL(/providercontrol/, { timeout: 30000 }),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
  return page;
}

async function openEchart(page) {
  // The encounter entry point the appointment screen uses. It redirects through
  // ViewForward into CaseManagementEntry?method=setUpMainEncounter.
  const search = new URLSearchParams({
    providerNo,
    appointmentNo: '',
    demographicNo,
    curProviderNo: providerNo,
    reason: '',
    encType: '',
    userName: '',
    curDate: '',
    appointmentDate: '',
    startTime: '',
    status: '',
  }).toString();
  await page.goto(appUrl('/encounter/IncomingEncounter', search), { waitUntil: 'domcontentloaded' }); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- appUrl rejects non-root-relative paths and validateBaseUrl restricts hosts to local/private by default
  await page.locator('textarea[name="caseNote_note"]').first().waitFor({ state: 'attached', timeout: 30000 });
  await page.locator('#printOps').waitFor({ state: 'attached', timeout: 30000 });
}

/**
 * Types the note body, opens the print dialog with the given selection, presses
 * Print, and returns the print POST's response.
 *
 * The print dialog is positioned by printSetup() off a mouse event and the flag
 * icons are toggled by printInfo(), so the selection is set through the hidden
 * inputs those handlers write — the same values the form would carry after the
 * clicks, without depending on the popup's absolute placement.
 */
async function printChart(page, noteText, flags) {
  await page.evaluate(({ text, selectedFlags, flagIds }) => {
    const textareas = document.getElementsByName('caseNote_note');
    if (textareas.length) {
      textareas[0].value = text;
    }
    flagIds.forEach((id) => {
      const input = document.getElementById(id);
      if (input) {
        input.value = selectedFlags.includes(id) ? 'true' : 'false';
      }
    });
    const queued = document.getElementById('notes2print');
    if (queued) {
      queued.value = '';
    }
    document.getElementById('printOps').style.display = 'block';
  }, { text: noteText, selectedFlags: flags, flagIds: PRINT_FLAG_IDS });

  await page.locator('#printopAll').check();

  const printResponse = page.waitForResponse(
    (response) => /\/CaseManagementEntry$/.test(new URL(response.url()).pathname)
      && response.request().method() === 'POST'
      && /(?:^|&)method=print(?:&|$)/.test(response.request().postData() || ''),
    { timeout: 40000 },
  );
  await page.locator('#printOp').click();
  return printResponse;
}

(async () => {
  const browser = await chromium.launch(chromePath ? { executablePath: chromePath } : {});
  const context = await browser.newContext({ ignoreHTTPSErrors: true, acceptDownloads: true });

  try {
    const page = await login(context);
    await openEchart(page);

    // A successful chart print is a download, so the page stays put and the next
    // case can reuse it. A REJECTED one is a navigation to the rejecter's error
    // page, which takes the eChart DOM with it — so stop at the first non-200 and
    // let the assertions below report it, rather than crashing the next case on a
    // missing element. Vary the note body first, then the selection, so a failure
    // names which of the two dimensions broke.
    let printsRemainUseful = true;

    for (const note of NOTE_BODIES) {
      if (!printsRemainUseful) break;
      const response = await printChart(page, note.text, []);
      printResults.push({
        dimension: 'note body', label: note.label, crs: note.crs,
        status: response.status(), contentType: response.headers()['content-type'] || '',
      });
      printsRemainUseful = response.status() === 200;
    }

    const worstCaseNote = NOTE_BODIES.find((note) => note.label === 'sentence semicolon').text;
    for (const selection of PRINT_SELECTIONS) {
      if (!printsRemainUseful) break;
      const response = await printChart(page, worstCaseNote, selection.flags);
      printResults.push({
        dimension: 'selection', label: selection.label, crs: 'n/a',
        status: response.status(), contentType: response.headers()['content-type'] || '',
      });
      printsRemainUseful = response.status() === 200;
    }

    const blocked = printResults.filter((result) => result.status === 403);
    assert(blocked.length === 0,
      'chart print was rejected with HTTP 403 — on a packaged install this is the WAF, and rule 1010 in '
      + 'debian/assets/modsecurity/REQUEST-900-EXCLUSION-RULES-BEFORE-CRS.conf no longer covers the note body: '
      + `${JSON.stringify(blocked, null, 2)}`);

    const failed = printResults.filter((result) => result.status !== 200);
    assert(failed.length === 0, `chart print did not return HTTP 200: ${JSON.stringify(failed, null, 2)}`);

    // A 200 alone is not a print. CaseManagementEntry.print() is a direct-response
    // action, so a failure inside it can still answer 200 with an HTML error page
    // (see the Direct Response Actions rules in CLAUDE.md). Require the PDF.
    const notPdf = printResults.filter((result) => !/application\/pdf/i.test(result.contentType));
    assert(notPdf.length === 0,
      `chart print returned HTTP 200 but not a PDF — the action answered with something else, `
      + `probably an HTML error page: ${JSON.stringify(notPdf, null, 2)}`);

    assert(printResults.length === NOTE_BODIES.length + PRINT_SELECTIONS.length,
      `only ${printResults.length} of ${NOTE_BODIES.length + PRINT_SELECTIONS.length} print cases ran`);

    // The eChart's own autosave and save-note AJAX carry the same note text under
    // ARGS:note and ARGS:noteTxt, so a WAF regression on those shows up here even
    // though no assertion above drives them directly.
    const wafBlocked = badResponses.filter((entry) => entry.status === 403);
    assert(wafBlocked.length === 0,
      'an eChart request carrying the note text was rejected with HTTP 403 — on a packaged install this is the WAF: '
      + `${JSON.stringify(wafBlocked, null, 2)}`);

    assert(badResponses.length === 0, `unexpected HTTP errors or dialogs: ${JSON.stringify(badResponses, null, 2)}`);

    console.log(`PASS chart print returned a PDF for ${NOTE_BODIES.length} note bodies `
      + `and ${PRINT_SELECTIONS.length} print selections`);
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error('FAIL eChart print Playwright check');
  console.error(error.stack || error.message);
  if (printResults.length) {
    console.error(`Print results: ${JSON.stringify(printResults, null, 2)}`);
  }
  if (badResponses.length) {
    console.error(`HTTP errors: ${JSON.stringify(badResponses, null, 2)}`);
  }
  process.exit(1);
});
