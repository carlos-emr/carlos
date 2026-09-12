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
 * trip a different CRS family on ARGS:caseNote_note. The two dimensions are run
 * in sequence, not as a matrix: every body is printed once, then every print
 * selection is printed with the worst-case body. The 403 rides on the note in the
 * serialized form rather than on any print checkbox, so one scored body is enough
 * to carry each selection, and 15 prints cover what 56 would. Against the devcontainer
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
 *   ECHART_ALLOW_NON_SYNTHETIC_PATIENT=true only when the target patient is known
 *     to be test data but does not carry the FAKE-/PLAYWRIGHT- name prefix
 *   ALLOW_NON_LOCAL_BASE_URL=true only for a disposable install that is not
 *     loopback — the chart's draft autosave writes what this check types, so a
 *     private LAN address, host.docker.internal and the compose name `carlos`
 *     all need the opt-in too, and a non-loopback target must be HTTPS
 *
 * What it writes. Nothing is saved as a note, but the eChart's own 5s draft
 * autosave posts whatever is in the textarea, so the corpus phrases land in the
 * patient's draft (casemgmt_tmpsave) while the prints run. Because the loopback
 * guard bounds the host and not the data, the check first opens the patient's
 * master record and refuses to run unless the first or last name carries the
 * synthetic-data prefix (FAKE-, PLAYWRIGHT-). It reads the note before its first
 * print and, when the prints are done, puts that text back and either writes it
 * back over the draft (a clinician's restored draft) or deletes the draft through
 * the page's own cancel path (a fresh note).
 */

const fs = require('fs');
const { chromium } = require('playwright');

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';
const demographicNo = requireDigits(process.env.ECHART_DEMOGRAPHIC_NO || '1', 'ECHART_DEMOGRAPHIC_NO');
const providerNo = requireDigits(process.env.ECHART_PROVIDER_NO || '999998', 'ECHART_PROVIDER_NO');
const allowNonSyntheticPatient = process.env.ECHART_ALLOW_NON_SYNTHETIC_PATIENT === 'true';

// Name prefixes that mark a patient as test data: FAKE- is what the demo dataset's
// sanitisation writes on every person name, PLAYWRIGHT- is what the fixture-owning
// checks name the patients they create. A real patient carries neither.
const SYNTHETIC_NAME_PREFIXES = ['FAKE-', 'PLAYWRIGHT-'];
// A chart print with even one note and the patient header comes out well above
// this; a PDF-typed 200 whose body is a stub or a truncated stream does not.
const MIN_PDF_BYTES = 1024;

const badResponses = [];
// Set when a response arrives through the packaged nginx front door. This script's
// default BASE_URL is bare Tomcat, where every note body passes for the boring reason
// that nothing inspected it, so a green run there says nothing about rule 1010.
// EXPECT_FRONT_DOOR=true makes a run that never saw an nginx-served response FAIL:
// the Server header is the only cheap signal, same as echart-playwright-checks.js.
let frontDoorObserved = false;
const expectFrontDoor = /^(1|true|yes)$/i.test(process.env.EXPECT_FRONT_DOOR || '');
const printResults = [];

// Each note body is a phrase measured to score over the CRS inbound threshold on
// ARGS:caseNote_note through the packaged front door before rule 1010 covered
// that argument. The rule ids are what the ModSecurity audit log reported. The
// pasted link goes FIRST in its body on purpose: 931100 is anchored on the start
// of the argument, so a link buried mid-sentence would not exercise attack-rfi,
// which 1010 unhooks alongside the other families.
const NOTE_BODIES = [
  { label: 'plain prose', text: 'Routine follow up. Patient doing well.', crs: 'none' },
  { label: 'sentence semicolon', text: 'Reviewed labs with the patient; find attached the CBC and lytes.', crs: '932100/932110 attack-rce' },
  { label: 'pasted PACS link first', text: 'http://10.0.0.5/pacs/study?id=1&cmd=view reviewed prior imaging with the patient.', crs: '931100 attack-rfi + 932110 attack-rce' },
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
  // Credentials in the URL would travel into Playwright navigations and can
  // surface in request or failure logging, so reject them outright.
  if (parsed.username || parsed.password) {
    throw new Error('BASE_URL must not contain embedded credentials');
  }

  // Loopback only without the opt-in. This check is not read-only: the eChart's
  // draft autosave posts whatever it types into the note as the patient's draft
  // (casemgmt_tmpsave), so a shared install on a private LAN, host.docker.internal
  // and the compose name `carlos` all need ALLOW_NON_LOCAL_BASE_URL=true like any
  // other remote host, the same line clinical-freetext-playwright-checks.js draws.
  const host = parsed.hostname.toLowerCase().replace(/^\[|\]$/g, '');
  if (!isLoopback(host) && process.env.ALLOW_NON_LOCAL_BASE_URL !== 'true') {
    throw new Error(`Refusing non-local BASE_URL host ${host}; set ALLOW_NON_LOCAL_BASE_URL=true for an intentional, disposable test target`);
  }
  // The login sends TEST_USER and TEST_PASSWORD. Off this machine that has to be
  // over TLS; the opt-in above covers the target, not a cleartext hop to it.
  if (!isLoopback(host) && parsed.protocol !== 'https:') {
    throw new Error(`Refusing plain-http BASE_URL to non-loopback host ${host}: the login would send credentials in cleartext`);
  }
  parsed.pathname = parsed.pathname.replace(/\/$/, '');
  return parsed;
}

/**
 * Loopback only: localhost, any 127.0.0.0/8 literal (four real octets, so a DNS
 * name like `127.attacker.example` does not pass), ::1 in either spelling, 0.0.0.0.
 */
function isLoopback(host) {
  if (['localhost', '::1', '0:0:0:0:0:0:0:1', '0.0.0.0'].includes(host)) return true;
  const octets = host.split('.');
  return octets.length === 4
    && octets.every((octet) => /^\d{1,3}$/.test(octet) && Number(octet) <= 255)
    && Number(octets[0]) === 127;
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

// Dialogs the chart raises that are not failures of this check: the same-user note-lock
// prompt on open ("You have started to edit this note in another window ... continue?") is
// the application reclaiming a lock a previous session of this provider left behind, which
// an earlier aborted run of this very check does. Accepted and reported, not failed.
const notices = [];

function wirePage(page, label) {
  page.on('dialog', async (dialog) => {
    // "nothing to print" means a selection never reached the server: record it as a
    // failure. The same-user lock prompt is accepted so the run proceeds as a clinician
    // would, and reported at the end.
    if (/edit this note in another window/i.test(dialog.message())) {
      notices.push({ label, type: 'dialog', text: dialog.message() });
    } else {
      badResponses.push({ label, type: 'dialog', text: dialog.message() });
    }
    await dialog.accept();
  });
  page.on('response', (response) => {
    const status = response.status();
    if (/nginx/i.test(response.headers()['server'] || '')) {
      frontDoorObserved = true;
    }
    if (status >= 400) {
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
  // login/index.jsp renders #pin only when MfaManager.isOscarLegacyPinEnabled(); filling it
  // unconditionally throws on an install with the legacy PIN disabled and the check never runs.
  const pin = page.locator('#pin');
  if ((await pin.count()) > 0) await pin.fill(testPin);
  await Promise.all([
    page.waitForURL(/providercontrol/, { timeout: 30000 }),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
  return page;
}

/**
 * Refuses to type into a patient that does not look like test data.
 *
 * Opens the master record and reads the names off the form's own controls (not
 * FormData: a name field the role cannot edit is disabled and would be skipped).
 * Either name carrying a synthetic prefix is enough; a record with neither is
 * refused unless ECHART_ALLOW_NON_SYNTHETIC_PATIENT=true says the operator knows
 * what it is. The refusal deliberately does not print the names.
 */
async function verifySyntheticPatient(page) {
  const search = new URLSearchParams({
    demographic_no: demographicNo, displaymode: 'edit', dboperation: 'search_detail',
  }).toString();
  await page.goto(appUrl('/demographic/DemographicEdit', search), { waitUntil: 'domcontentloaded' }); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- appUrl rejects non-root-relative paths and validateBaseUrl restricts hosts to loopback unless explicitly opted out of
  await page.locator('form[name="updatedelete"]').first().waitFor({ state: 'attached', timeout: 30000 });
  const names = await page.evaluate(() => {
    const form = document.forms['updatedelete'];
    const read = (name) => (form && form.elements[name] ? String(form.elements[name].value || '') : '');
    return { firstName: read('first_name'), lastName: read('last_name') };
  });
  const synthetic = [names.firstName, names.lastName].some((name) =>
    SYNTHETIC_NAME_PREFIXES.some((prefix) => name.trim().toUpperCase().startsWith(prefix)));
  if (!synthetic && !allowNonSyntheticPatient) {
    throw new Error(`demographic ${demographicNo} does not carry a synthetic-data name prefix `
      + `(${SYNTHETIC_NAME_PREFIXES.join(' or ')}), so this check will not type into its chart. Point `
      + 'ECHART_DEMOGRAPHIC_NO at a test patient, or set ECHART_ALLOW_NON_SYNTHETIC_PATIENT=true only if you '
      + 'know this record is test data');
  }
  if (!synthetic) {
    console.log(`WARNING demographic ${demographicNo} carries no synthetic-data name prefix; `
      + 'proceeding because ECHART_ALLOW_NON_SYNTHETIC_PATIENT=true');
  }
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
 * True when the response answers a POST to the note route whose form body carries
 * `method=<name>`; the method travels in the body on every call this check watches.
 */
function isCaseManagementEntryPost(response, method) {
  const request = response.request();
  if (request.method() !== 'POST') return false;
  if (!new URL(response.url()).pathname.endsWith('/CaseManagementEntry')) return false;
  return new URLSearchParams(request.postData() || '').getAll('method').includes(method);
}

// A fresh encounter note opens with only its generated header, "[12-Sep-2026 .:
// Tel-Progress Note]" and a newline. A note holding nothing but that header is
// nobody's unsaved work; anything beyond it may be a clinician's restored draft.
const GENERATED_NOTE_HEADER_ONLY = /^\s*\[\d{2}-[A-Za-z]{3}-\d{4} \.: [^\]]*\]\s*$/;

function holdsClinicianText(noteText) {
  return noteText.trim() !== '' && !GENERATED_NOTE_HEADER_ONLY.test(noteText);
}

/** Reads the encounter note textarea's current value, or null when the page has none. */
async function readNoteText(page) {
  return page.evaluate(() => {
    const textareas = document.getElementsByName('caseNote_note');
    return textareas.length ? textareas[0].value : null;
  });
}

/**
 * Undoes what the run's typing left in the patient's draft.
 *
 * This check never saves a note, but it does not need to: the chart's 5s draft
 * autosave posts the textarea whenever it differs from what the chart loaded, so
 * the corpus phrases land in casemgmt_tmpsave and edit() would hand them to the
 * next reader of this chart as their own unsaved note. Put the original text back
 * first. Then, if that original was a clinician's restored draft, write it back
 * over ours with the page's own autoSave(); if it was only the generated header,
 * no draft existed before this run, so the page's own cancel path deletes ours.
 * The same split, and the same page functions, as echart-playwright-checks.js.
 */
async function cleanUpNoteDraft(page, originalNote) {
  await page.evaluate((text) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- the argument is the value this script read out of the same textarea; it is assigned to .value, never used to build a URL or a request target
    const textareas = document.getElementsByName('caseNote_note');
    if (!textareas.length) throw new Error('encounter note textarea is no longer on the page');
    textareas[0].value = text;
  }, originalNote);
  if (holdsClinicianText(originalNote)) {
    const saved = page.waitForResponse((response) => isCaseManagementEntryPost(response, 'autosave'), { timeout: 15000 });
    await page.evaluate(() => {
      if (typeof autoSave !== 'function') throw new Error('autoSave() is not defined on the chart page');
      autoSave();
    });
    const response = await saved;
    // A conflict means the original draft was not restored; it is a cleanup failure.
    assert(response.ok(),
      `re-saving the original note draft failed with HTTP ${response.status()}`);
    return 'original draft written back';
  }
  const cancelled = page.waitForResponse((response) => isCaseManagementEntryPost(response, 'cancel'), { timeout: 15000 });
  await page.evaluate(() => {
    if (typeof clearAutoSaveTimer !== 'function' || typeof deleteAutoSave !== 'function') {
      throw new Error('clearAutoSaveTimer()/deleteAutoSave() are not defined on the chart page');
    }
    // deleteAutoSave() only posts the cancel; clearAutoSaveTimer() stops the 5s
    // timer and aborts an autosave on the wire, so nothing lands after the delete.
    clearAutoSaveTimer();
    deleteAutoSave();
  });
  const response = await cancelled;
  assert(response.ok(), `discarding the run's note draft failed with HTTP ${response.status()}`);
  return "run's draft discarded";
}

/**
 * Types the note body, opens the print dialog with the given selection, presses
 * Print, and returns the print POST's response together with what the browser
 * downloaded: whether the bytes start with the PDF signature, and how many.
 *
 * The Content-Type alone does not prove a print. CaseManagementEntry.print() sets
 * application/pdf before it generates, so a failure after the response is
 * committed can still answer a PDF-typed 200 with a truncated or empty body. A
 * successful print arrives as a download, so the bytes are read from that.
 *
 * The print dialog is positioned by printSetup() off a mouse event and the flag
 * icons are toggled by printInfo(), so the selection is set through the hidden
 * inputs those handlers write — the same values the form would carry after the
 * clicks, without depending on the popup's absolute placement.
 */
async function printChart(page, noteText, flags, expectAutosave) {
  // Drive the chart's own autosave with each distinct note body and require success.
  // Printing must retain this window's lock so subsequent draft writes still work.
  // The selection dimension reprints one unchanged body and does not repeat the save.
  const autosave = expectAutosave
    ? page.waitForResponse(
      (response) => isCaseManagementEntryPost(response, 'autosave')
        && (new URLSearchParams(response.request().postData() || '').get('note') || '').includes(noteText),
      { timeout: 15000 },
    ).catch(() => null)
    : Promise.resolve(null);
  await page.evaluate(({ text, selectedFlags, flagIds }) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- the page function is a literal, and every argument is a structured-cloned constant from this file (note text, print-flag ids), never interpolated into page script
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

  if (expectAutosave) {
    await page.evaluate(() => {
      if (typeof autoSave !== 'function') throw new Error('autoSave() is not defined on the chart page');
      autoSave();
    });
  }
  const autosaveResponse = await autosave;

  await page.locator('#printopAll').check();

  const printResponse = page.waitForResponse(
    (response) => /\/CaseManagementEntry$/.test(new URL(response.url()).pathname)
      && response.request().method() === 'POST'
      && /(?:^|&)method=print(?:&|$)/.test(response.request().postData() || ''),
    { timeout: 40000 },
  );
  // Armed before the click. Left dangling on purpose when no download comes (a
  // 403, or an HTML error page): the catch keeps the timeout from surfacing as an
  // unhandled rejection, and the result below records that nothing was received.
  const downloadPromise = page.waitForEvent('download', { timeout: 40000 }).catch(() => null);
  await page.locator('#printOp').click();
  const response = await printResponse;
  const pdf = { signature: false, bytes: 0 };
  if (response.status() === 200 && /application\/pdf/i.test(response.headers()['content-type'] || '')) {
    const download = await downloadPromise;
    if (download) {
      const bytes = fs.readFileSync(await download.path());
      pdf.signature = bytes.subarray(0, 5).toString('latin1') === '%PDF-';
      pdf.bytes = bytes.length;
    }
  }
  return { response, pdf, autosaveStatus: autosaveResponse ? autosaveResponse.status() : null };
}

(async () => {
  const browser = await chromium.launch(chromePath ? { executablePath: chromePath } : {});
  // Certificate verification is only relaxed for loopback, where the packaged
  // install serves its own self-signed cert. A target opted in with
  // ALLOW_NON_LOCAL_BASE_URL must still prove its certificate, because this
  // check logs in with real credentials. Same contract as
  // billing-on-third-party and allergy-rx-alert, and the same loopback test as
  // validateBaseUrl(), so every 127.0.0.0/8 literal the guard admits gets it.
  const context = await browser.newContext({
    ignoreHTTPSErrors: isLoopback(baseUrl.hostname.replace(/^\[|\]$/g, '').toLowerCase()),
    acceptDownloads: true,
  });

  try {
    const page = await login(context);
    // Before anything is typed into a chart: refuse a patient that is not test data.
    await verifySyntheticPatient(page);
    await openEchart(page);

    // What the note held before this run typed into it: a clinician's restored
    // draft, or just the generated header. Read once, before the first print.
    const originalNote = await readNoteText(page);
    assert(originalNote !== null, 'the eChart opened without its encounter note textarea');
    let cleanupOutcome = null;
    let cleanupFailure = null;

    // A successful chart print is a download, so the page stays put and the next
    // case can reuse it. A REJECTED one is a navigation to the rejecter's error
    // page, which takes the eChart DOM with it — so stop at the first non-200 and
    // let the assertions below report it, rather than crashing the next case on a
    // missing element. Vary the note body first, then the selection, so a failure
    // names which of the two dimensions broke.
    let printsRemainUseful = true;

    try {
      for (const note of NOTE_BODIES) {
        if (!printsRemainUseful) break;
        const { response, pdf, autosaveStatus } = await printChart(page, note.text, [], true);
        printResults.push({
          dimension: 'note body', label: note.label, crs: note.crs,
          status: response.status(), contentType: response.headers()['content-type'] || '',
          pdfSignature: pdf.signature, pdfBytes: pdf.bytes, autosaveStatus,
        });
        printsRemainUseful = response.status() === 200;
      }

      const worstCaseNote = NOTE_BODIES.find((note) => note.label === 'sentence semicolon').text;
      for (const selection of PRINT_SELECTIONS) {
        if (!printsRemainUseful) break;
        const { response, pdf, autosaveStatus } = await printChart(page, worstCaseNote, selection.flags, false);
        printResults.push({
          dimension: 'selection', label: selection.label, crs: 'n/a',
          status: response.status(), contentType: response.headers()['content-type'] || '',
          pdfSignature: pdf.signature, pdfBytes: pdf.bytes, autosaveStatus,
        });
        printsRemainUseful = response.status() === 200;
      }
    } finally {
      // Whatever the prints did, do not leave the run's phrases as the patient's
      // draft. A rejected print navigates the page to the error response, where
      // the note textarea no longer exists and cleanUpNoteDraft() would throw with
      // the corpus still sitting in casemgmt_tmpsave — so go back to the chart
      // first. That is the exact path this check exists to catch, which makes it
      // the path the cleanup most needs to survive. If the chart itself will not
      // open, record that and let the print failure below stay the headline.
      try {
        if ((await page.locator('textarea[name="caseNote_note"]').count()) === 0) {
          await openEchart(page);
        }
        cleanupOutcome = await cleanUpNoteDraft(page, originalNote);
      } catch (error) {
        cleanupFailure = error;
      }
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

    // And a PDF Content-Type alone is not a PDF: the header is set before the
    // bytes are generated. Require the downloaded bytes to carry the %PDF- signature
    // and a size no real chart print comes under.
    const corrupt = printResults.filter((result) => !result.pdfSignature || result.pdfBytes < MIN_PDF_BYTES);
    assert(corrupt.length === 0,
      `chart print answered application/pdf but the downloaded bytes are not a usable PDF `
      + `(no %PDF- signature, or under ${MIN_PDF_BYTES} bytes): ${JSON.stringify(corrupt, null, 2)}`);

    assert(printResults.length === NOTE_BODIES.length + PRINT_SELECTIONS.length,
      `only ${printResults.length} of ${NOTE_BODIES.length + PRINT_SELECTIONS.length} print cases ran`);

    // Every body must be observed in a successful draft save. A 409 reaches the
    // application but means the print lost the lock, so it must fail this check too.
    // Explicit save/sign (ARGS:noteTxt) remains outside this draft-only workflow.
    const noAutosave = printResults.filter((result) => result.dimension === 'note body' && result.autosaveStatus === null);
    assert(noAutosave.length === 0,
      'no draft autosave carrying the note body was observed, so ARGS:note was not exercised: '
      + `${JSON.stringify(noAutosave, null, 2)}`);
    const autosaveRejected = printResults.filter((result) => result.autosaveStatus !== null && result.autosaveStatus !== 200);
    assert(autosaveRejected.length === 0,
      `the draft autosave of a note body was rejected: ${JSON.stringify(autosaveRejected, null, 2)}`);
    const wafBlocked = badResponses.filter((entry) => entry.status === 403);
    assert(wafBlocked.length === 0,
      'an eChart request carrying the note text was rejected with HTTP 403 — on a packaged install this is the WAF: '
      + `${JSON.stringify(wafBlocked, null, 2)}`);

    assert(badResponses.length === 0, `unexpected HTTP errors or dialogs: ${JSON.stringify(badResponses, null, 2)}`);

    assert(cleanupFailure === null,
      `every print passed, but the note draft this run left behind could not be cleaned up: ${cleanupFailure && cleanupFailure.message}`);

    // Say the shape plainly: this is NOT a body x selection matrix. Each body is
    // printed once with no extra selection, and the worst-case body is printed once
    // per selection, because the defect rides on the form rather than on any
    // checkbox -- one scored body proves every selection carries it.
    if (expectFrontDoor && !frontDoorObserved) {
      throw new Error('EXPECT_FRONT_DOOR is set but no response carried an nginx Server header; the run did not go through the packaged front door');
    }
    console.log(frontDoorObserved
      ? 'Front door observed: responses carried an nginx Server header, so the WAF was in the path of every print'
      : 'WARNING: no response carried an nginx Server header, so this run did NOT exercise the packaged WAF; rule 1010 is unverified');
    if (notices.length) {
      console.log(`Notices (accepted, not failures): ${JSON.stringify(notices, null, 2)}`);
    }
    console.log(`PASS chart print returned a PDF in all ${printResults.length} cases `
      + `(${NOTE_BODIES.length} note bodies, then ${PRINT_SELECTIONS.length} print selections `
      + `on the worst-case body); ${cleanupOutcome}`);
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
