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
 * to carry each selection. A final print is delayed past the old three-second
 * lock suppression: it must retain the editor and its database lock, allow another
 * draft save, and still release the lock on an actual navigation. Against the devcontainer
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
 *   ECHART_PROVIDER_NO=999998
 *   MYSQL_HOST/USER/PASSWORD/DATABASE (the owned patient and its cleanup)
 *   ALLOW_NON_LOCAL_BASE_URL=true only for a disposable install that is not
 *     loopback — the chart's draft autosave writes what this check types, so a
 *     private LAN address, host.docker.internal and the compose name `carlos`
 *     all need the opt-in too, and a non-loopback target must be HTTPS
 *
 * What it writes. Nothing is saved as a note, but the eChart's own 5s draft
 * autosave posts whatever is in the textarea, so the corpus phrases land in the
 * patient's draft (casemgmt_tmpsave) while the prints run. The patient is a FAKE
 * one this check creates (lib/owned-patient.js: last name = a FAKE-PW run marker)
 * and removes: it used to be DEMO patient 1, whose own stored draft the cleanup
 * took for "a fresh note" (it holds only the generated header) and deleted through
 * the page's cancel path, so the demo lost a casemgmt_tmpsave row on every run.
 * Because the loopback guard bounds the host and not the data, the check still opens
 * the patient's master record first and refuses to run unless the first or last name
 * carries the synthetic-data prefix (FAKE-, PLAYWRIGHT-). It reads the note before its
 * first print and, when the prints are done, puts that text back and either writes it
 * back over the draft or deletes the draft through the page's own cancel path (the
 * owned patient has no draft of its own, so it is the cancel path). The patient's
 * rows are then deleted by its key and asserted gone.
 */

const { closeBrowserWithChartCleanup } = require('./lib/chart-lock-cleanup');
const h = require('./lib/playwright-harness');
const { createOwnedPatient, newOwnedMarker, removeOwnedPatient } = require('./lib/owned-patient');
const fs = require('fs');
const { execFileSync } = require('node:child_process');
const { chromium } = require('playwright');
const { shouldIgnoreHttpsErrors } = require('./lib/playwright-harness');
const { watchChartPrintDownload } = require('./lib/chart-print-download');
const { observeOwnedReload, provenPrintNavigationAborts } = require('./lib/chart-reload-witness');
const { readPatientState } = require('./echart-two-patient-isolation-playwright-checks');

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';
// The owned patient whose chart is printed, created in main (never a demo patient).
let demographicNo = null;
let patientMarker = null;
const providerNo = requireDigits(process.env.ECHART_PROVIDER_NO || '999998', 'ECHART_PROVIDER_NO');

// Name prefixes that mark a patient as test data: FAKE- is what the demo dataset's
// sanitisation writes on every person name, PLAYWRIGHT- is what the fixture-owning
// checks name the patients they create. A real patient carries neither.
const SYNTHETIC_NAME_PREFIXES = ['FAKE-', 'PLAYWRIGHT-'];
// A chart print with even one note and the patient header comes out well above
// this; a PDF-typed 200 whose body is a stub or a truncated stream does not.
const MIN_PDF_BYTES = 1024;

const badResponses = [];
const recorder = h.createRecorder();
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
  { label: 'multiline Unicode and exact spacing', text: 'H&P + café 中文\nPlan: follow up.\n  Retain trailing spaces  ', crs: 'native textarea serialization control' },
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
  h.wireStrictPage(page, label, recorder, { baseline: [], dialogHandler: async (dialog) => {
    // "nothing to print" means a selection never reached the server: record it as a
    // failure. The same-user lock prompt is accepted so the run proceeds as a clinician
    // would, and reported at the end.
    if (/edit this note in another window/i.test(dialog.message())) {
      notices.push({ label, type: 'dialog', text: dialog.message() });
    } else {
      badResponses.push({ label, type: 'dialog', text: dialog.message() });
    }
    await dialog.accept();
  } });
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

function assertBrowserDiagnostics() {
  h.assertStrictPage(recorder);
  // Retain this workflow's stronger pre-migration HTTP contract, including assets
  // that the common recorder treats as a known missing resource.
  assert(badResponses.length === 0, `unexpected HTTP errors or dialogs: ${JSON.stringify(badResponses, null, 2)}`);
}

/** Observe the real final navigation; never intercept or replace its native lock release. */
async function completePrintNavigation(page, sql) {
  const form = await page.evaluate(() => {
    const editor = document.forms.caseManagementEntryForm;
    return { patient: String(demographicNo), provider: String(providerNo), noteId: String(editor.elements.noteId.value),
      token: editor.elements.chartContext.value, csrf: CarlosAjax.getCsrfToken(),
      endpoint: new URL(chartActionUrl('/CaseManagementEntry'), location.href).href,
      url: location.href, timeOrigin: performance.timeOrigin };
  });
  assert(form.patient === demographicNo && form.provider === providerNo && form.token && form.csrf
    && JSON.stringify([...new URL(form.endpoint).searchParams.keys()]) === JSON.stringify(['chartContext'])
    && new URL(form.endpoint).searchParams.get('chartContext') === form.token,
  'The final navigation lost its exact owned editor authority');
  const cookies = (await page.context().cookies(form.endpoint)).filter(cookie => cookie.name === 'JSESSIONID');
  assert(cookies.length === 1 && cookies[0].value, 'The print editor has no unique authenticated session');
  form.session = cookies[0].value;
  const lockQuery = 'SELECT id,session_id,ip_address,provider_no,note_id,demographic_no,lock_acquired FROM casemgmt_note_lock ORDER BY id';
  const clinical = () => ({ patient: readPatientState(sql, demographicNo),
    drugs: sql.rows(`SELECT * FROM drugs WHERE demographic_no=${demographicNo} ORDER BY drugid`),
    prescriptions: sql.rows(`SELECT * FROM prescription WHERE demographic_no=${demographicNo} ORDER BY script_no`),
    allergies: sql.rows(`SELECT * FROM allergies WHERE demographic_no=${demographicNo} ORDER BY allergyid`),
    admissions: sql.rows(`SELECT * FROM admission WHERE client_id=${demographicNo} ORDER BY am_id`),
    documents: sql.rows(`SELECT * FROM ctl_document WHERE module='demographic' AND module_id=${demographicNo} ORDER BY document_no`),
    otherDrafts: sql.rows(`SELECT * FROM casemgmt_tmpsave WHERE demographic_no<>${demographicNo} ORDER BY id`) });
  const state = { before: clinical(), beforeLocks: sql.rows(lockQuery) };
  const requests = []; const errors = []; const counts = new Map(); const claimed = new Set();
  const oldFailures = new Set(recorder.requestFailures);
  // An unavailable artifact directory must fail before installing any private observers.
  const directory = fs.mkdtempSync(require('node:path').join(require('node:os').tmpdir(), 'carlos-print-completion-'));
  fs.chmodSync(directory, 0o700);
  const passive = await observeOwnedReload(page, form);
  const beforeFailure = request => counts.set(request, recorder.requestFailures.length);
  const observe = request => {
    if (request.resourceType() !== 'ping' || new URL(request.url()).pathname !== new URL(form.endpoint).pathname) return;
    const item = { request, bodyAvailable: request.postData() !== null };
    const fields = new URLSearchParams(request.postData() || '');
    if (request.frame().page() !== page || request.method() !== 'POST' || request.url() !== form.endpoint
      || item.bodyAvailable && !(JSON.stringify([...fields.keys()].sort()) === JSON.stringify(
        ['CSRF-TOKEN', 'demographicNo', 'method', 'noteId', 'providerNo'])
        && fields.get('method') === 'releaseNoteLock' && fields.get('demographicNo') === form.patient
        && fields.get('providerNo') === form.provider && fields.get('noteId') === form.noteId
        && fields.get('CSRF-TOKEN') === form.csrf)) errors.push('A native release differs from its exact owned editor');
    requests.push(item);
  };
  const failed = request => {
    const item = requests.find(value => value.request === request);
    if (!item) return;
    if (item.terminal) { errors.push('The original native Request emitted duplicate terminal events'); return; }
    item.terminal = 'failed';
    const entry = recorder.requestFailures.at(-1);
    if (recorder.requestFailures.length !== counts.get(request) + 1 || !entry || oldFailures.has(entry)
      || claimed.has(entry) || entry.url !== request.url() || entry.resourceType !== 'ping'
      || entry.errorText !== request.failure()?.errorText) errors.push('The original failed Request has no unique newly appended recorder entry');
    else { claimed.add(entry); item.rawFailure = entry; }
  };
  const finished = request => {
    const item = requests.find(value => value.request === request);
    if (item?.terminal) errors.push('The original native Request emitted duplicate terminal events');
    else if (item) item.terminal = 'finished';
  };
  page.prependListener('requestfailed', beforeFailure);
  page.on('request', observe); page.on('requestfailed', failed); page.on('requestfinished', finished);
  const evidence = { phase: 'owned print completion navigation', passiveOnly: true };
  let failure;
  try {
    const destination = appUrl('/demographic/DemographicEdit',
      new URLSearchParams({ demographic_no: demographicNo, displaymode: 'edit', dboperation: 'search_detail' }).toString());
    const response = await page.goto(destination, { waitUntil: 'load', timeout: 30000 });
    assert(response && response.url() === destination, 'Print completion did not load its exact native destination');
    const arrived = await page.evaluate(() => ({ url: location.href, timeOrigin: performance.timeOrigin }));
    arrived.status = response.status(); arrived.application = Object.hasOwn(response.headers(), h.APPLICATION_HEADER);
    const deadline = Date.now() + 15000;
    while (requests.length === 0 || requests.some(item => !item.terminal)
      || sql.value(`SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${demographicNo}`) !== '0') {
      assert(Date.now() < deadline, 'Native print navigation or its original Request terminal events did not complete');
      await new Promise(resolve => setTimeout(resolve, 25));
    }
    const witness = await passive.finish(requests.length);
    evidence.witness = witness;
    state.after = clinical(); state.afterLocks = sql.rows(lockQuery);
    assert(errors.length === 0, errors.join('; '));
    const proof = provenPrintNavigationAborts(recorder, { page, form, arrived, requests, witness, state });
    evidence.audit = proof.audit;
    // Keep the complete raw entry and same-request CDP acknowledgment before exact identity removal.
    fs.writeFileSync(require('node:path').join(directory, 'navigation.json'), JSON.stringify(evidence, null, 2) + '\n', { mode: 0o600 });
    recorder.requestFailures = recorder.requestFailures.filter(entry => !proof.entries.includes(entry));
    console.log(`Native print navigation proof: ${directory}/navigation.json`);
  } catch (error) { failure = error; throw error; }
  finally {
    page.off('request', observe); page.off('requestfailed', failed); page.off('requestfinished', finished);
    page.off('requestfailed', beforeFailure);
    evidence.nativeRequests = requests.map(({ request, ...item }) => item); evidence.errors = errors;
    let cleanupFailure;
    try { fs.writeFileSync(require('node:path').join(directory, 'navigation.json'), JSON.stringify(evidence, null, 2) + '\n', { mode: 0o600 }); }
    catch (error) { cleanupFailure = error; }
    try { await passive.close(); }
    catch (error) {
      if (cleanupFailure) cleanupFailure.message += `; Passive observer cleanup: ${error.message}`;
      else cleanupFailure = error;
    }
    if (cleanupFailure) {
      if (failure) failure.message += `; Print proof cleanup: ${cleanupFailure.message}`;
      else throw cleanupFailure;
    }
  }
}

function assertPrintRequestIdentity(request, endpoint, context, noteText, flags) {
  const actual = new URL(request.url());
  const expected = new URL(endpoint);
  assert(request.method() === 'POST' && actual.origin === expected.origin && actual.pathname === expected.pathname,
    'The print POST did not target the owned application action');
  const body = new URLSearchParams(request.postData() || '');
  const only = (name, value) => {
    const values = body.getAll(name);
    assert(values.length === 1 && values[0] === value, `The print POST carried an unexpected ${name}`);
  };
  only('method', 'print');
  only('demographicNo', demographicNo);
  const aliases = body.getAll('demographic_no').concat(actual.searchParams.getAll('demographicNo'),
    actual.searchParams.getAll('demographic_no'));
  assert(aliases.every(value => value === demographicNo), 'The print POST contained a foreign patient alias');
  const contexts = body.getAll('chartContext').concat(actual.searchParams.getAll('chartContext'));
  assert(context && contexts.length > 0 && contexts.every(value => value === context),
    'The print POST did not retain the owned chart context');
  assert(!actual.searchParams.has('method'), 'The print POST contained an ambiguous method');
  // Native HTML form submission canonicalizes textarea newlines to CRLF.
  // Transform only the expected value; every submitted byte must still match.
  only('caseNote_note', noteText.replace(/\r\n|\r|\n/g, '\r\n'));
  only('notes2print', 'ALL_NOTES');
  // printNotes assigns the unchecked Dates radio's null value to this hidden
  // field, which the native form submits as one empty All-mode sentinel.
  only('pType', '');
  for (const flag of PRINT_FLAG_IDS) only(flag, flags.includes(flag) ? 'true' : 'false');
}

function assertCompletePrintPdf(bytes) {
  assert(bytes.subarray(0, 5).toString('latin1') === '%PDF-', 'The downloaded chart print is not a PDF');
  assert(/%%EOF\s*$/.test(bytes.subarray(-1024).toString('latin1')),
    'The downloaded chart print is truncated (no %%EOF in the final 1 KiB)');
}

async function login(context) {
  const page = await context.newPage();
  // One wiring for the whole session: the eChart runs in this same window, and its
  // draft autosave timer posts the SAME note text under ARGS:note while these cases
  // run, so a regression on that argument surfaces here too. ARGS:noteTxt is NOT
  // covered — that one rides on ajaxSaveNote, which only fires on an explicit
  // save/sign, and this check deliberately never saves a note.
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
 * refused. The refusal deliberately does not print the names.
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
  if (!synthetic) {
    throw new Error(`demographic ${demographicNo} does not carry a synthetic-data name prefix `
      + `(${SYNTHETIC_NAME_PREFIXES.join(' or ')}), so this check will not type into its chart`);
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
  assertBrowserDiagnostics();
  const identity = await page.evaluate(() => {
    const form = document.forms.caseManagementEntryForm;
    return { action: form.action, patient: form.elements.demographicNo.value,
      context: form.elements.chartContext && form.elements.chartContext.value };
  });
  assert(identity.patient === demographicNo && identity.context,
    'The print editor does not own the expected patient and chart context');
  const endpoint = new URL(identity.action);
  const expectedEndpoint = new URL(appUrl('/CaseManagementEntry'));
  assert(endpoint.origin === expectedEndpoint.origin && endpoint.pathname === expectedEndpoint.pathname,
    'The owned print form targets another application action');
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

  let responseTimer;
  let finishResponse;
  const receivedResponse = response => {
    const address = new URL(response.url());
    if (address.origin === endpoint.origin && address.pathname === endpoint.pathname
      && response.request().method() === 'POST'
      && new URLSearchParams(response.request().postData() || '').getAll('method').includes('print')) {
      finishResponse(response);
    }
  };
  const printResponse = new Promise(resolve => {
    let settled = false;
    finishResponse = response => {
      if (settled) return;
      settled = true;
      clearTimeout(responseTimer);
      page.context().off('response', receivedResponse);
      resolve(response);
    };
  });
  page.context().on('response', receivedResponse);
  responseTimer = setTimeout(() => finishResponse(null), 40000);
  // Chromium can assign the native download to either this chart or the
  // transient print page. Observe both before the click and retain the bytes
  // from the actual download; never substitute an API replay of the POST.
  const downloaded = watchChartPrintDownload(page.context(), appUrl('/CaseManagementEntry'));
  try {
    await page.locator('#printOp').click();
    const response = await printResponse;
    assert(response, 'The native chart print POST did not return a response');
    assertPrintRequestIdentity(response.request(), identity.action, identity.context, noteText, flags);
    const pdf = { signature: false, bytes: 0 };
    if (response.status() === 200 && /application\/pdf/i.test(response.headers()['content-type'] || '')) {
      const download = await downloaded.promise;
      if (download) {
        assert(download.url() === response.url(), 'The downloaded PDF belongs to another print request');
        assert(new URLSearchParams(response.request().postData() || '').get('demographicNo') === demographicNo,
          'The print POST named another patient');
        assert(await download.failure() === null, 'The native chart PDF download failed');
        const bytes = fs.readFileSync(await download.path());
        assertCompletePrintPdf(bytes);
        pdf.signature = bytes.subarray(0, 5).toString('latin1') === '%PDF-';
        pdf.bytes = bytes.length;
        // Preserve content-stream order: layout mode interleaves the clinic's
        // left column between wrapped lines of the patient's right-column name.
        const text = execFileSync('pdftotext', ['-raw', '-', '-'],
          { input: bytes, encoding: 'utf8', timeout: 15000 });
        assert(text.replace(/\s+/g, '').includes(patientMarker), 'The downloaded PDF does not identify the owned patient');
      }
    }
    assert(!page.isClosed(), 'The print closed its owning chart editor');
    assert(await readNoteText(page) === noteText, 'The print replaced or changed its owning chart editor');
    assertBrowserDiagnostics();
    return { response, pdf, autosaveStatus: autosaveResponse ? autosaveResponse.status() : null };
  } finally {
    finishResponse(null);
    downloaded.cancel();
  }
}

(async () => {
  const sql = h.createSqlRunner(h.readConfig().mysql);
  const ownedMarker = newOwnedMarker();
  patientMarker = ownedMarker;
  let browser = null;

  // Patient creation, browser launch and context setup are all inside the try whose finally removes the patient and disposes the
  // mysql option file, so a browser that fails to launch cannot strand a FAKE-PW patient.
  try {
    const provider = sql.value(`SELECT provider_no FROM security WHERE user_name=${h.sqlString(testUser)}`);
    assert(provider, 'The configured test login has no provider');
    demographicNo = createOwnedPatient(sql, { marker: ownedMarker, provider });
    browser = await chromium.launch(chromePath ? { executablePath: chromePath } : {});
    // Certificate verification is only relaxed for loopback, where the packaged
    // install serves its own self-signed cert. A target opted in with
    // ALLOW_NON_LOCAL_BASE_URL must still prove its certificate, because this
    // check logs in with real credentials. Same contract as
    // billing-on-third-party and allergy-rx-alert, and the same loopback test as
    // validateBaseUrl(), so every 127.0.0.0/8 literal the guard admits gets it.
    const context = await browser.newContext({
      ignoreHTTPSErrors: shouldIgnoreHttpsErrors(baseUrl),
      acceptDownloads: true,
    });
    context.on('page', opened => wirePage(opened, 'echart'));

    const page = await login(context);
    // Before anything is typed into a chart: refuse a patient that is not test data.
    await verifySyntheticPatient(page);
    await openEchart(page);
    await page.evaluate(() => {
      window.__chartPrintUnloadEvents = 0;
      for (const event of ['beforeunload', 'pagehide']) {
        window.addEventListener(event, () => { window.__chartPrintUnloadEvents++; });
      }
    });
    const lockState = () => JSON.stringify(sql.rows(`SELECT id,provider_no,note_id,SHA2(session_id,256)
      FROM casemgmt_note_lock WHERE demographic_no=${demographicNo} ORDER BY id`));
    assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${demographicNo}`) === '1',
      'The print regression requires the actual editor database lock');
    const originalLock = lockState();

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
      if (printsRemainUseful) {
        let delayedRequests = 0;
        const slowPrint = async route => {
          if (route.request().method() === 'POST'
            && new URL(route.request().url()).pathname.endsWith('/CaseManagementEntry')
            && new URLSearchParams(route.request().postData() || '').get('method') === 'print') {
            delayedRequests++;
            await new Promise(resolve => setTimeout(resolve, 4000));
          }
          await route.continue();
        };
        const pattern = '**/CaseManagementEntry*';
        await context.route(pattern, slowPrint);
        try {
          const { response, pdf, autosaveStatus } = await printChart(page,
            `${worstCaseNote}\n${ownedMarker} delayed print`, [], true);
          printResults.push({ dimension: 'delayed response', label: 'slow print retains editor lock',
            crs: '932100/932110 attack-rce', status: response.status(),
            contentType: response.headers()['content-type'] || '', pdfSignature: pdf.signature,
            pdfBytes: pdf.bytes, autosaveStatus });
          assert(delayedRequests === 1, 'The slow-print control did not delay exactly one real print request');
        } finally {
          await context.unroute(pattern, slowPrint);
        }
        assert(await page.evaluate(() => window.__chartPrintUnloadEvents) === 0,
          'Printing started unloading the editor and could release its note lock');
        assert(lockState() === originalLock, 'Printing replaced or released the editor database lock');
        const afterPrint = `${ownedMarker} saved after slow print`;
        const saved = page.waitForResponse(response => isCaseManagementEntryPost(response, 'autosave')
          && new URLSearchParams(response.request().postData() || '').get('note') === afterPrint, { timeout: 15000 });
        await page.evaluate(text => {
          document.getElementsByName('caseNote_note')[0].value = text;
          autoSave();
        }, afterPrint);
        assert((await saved).status() === 200, 'The draft save after a slow print lost the editor lock');
        assert(sql.value(`SELECT COUNT(*) FROM casemgmt_tmpsave WHERE demographic_no=${demographicNo}
          AND note LIKE ${h.sqlString(`%${afterPrint}%`)}`) === '1',
        'The successful post-print autosave did not persist the new draft');
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

    assert(printResults.length === NOTE_BODIES.length + PRINT_SELECTIONS.length + 1,
      `only ${printResults.length} of ${NOTE_BODIES.length + PRINT_SELECTIONS.length + 1} print cases ran`);

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

    assertBrowserDiagnostics();

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
    assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note_lock WHERE demographic_no=${demographicNo}`) === '1',
      'The normal-navigation control requires the editor to still hold its lock');
    await completePrintNavigation(page, sql);
    assertBrowserDiagnostics();
    console.log(`PASS chart print returned a PDF in all ${printResults.length} cases `
      + `(${NOTE_BODIES.length} note bodies, then ${PRINT_SELECTIONS.length} print selections `
      + `on the worst-case body, then one delayed print); post-print autosave persisted; navigation released lock; ${cleanupOutcome}`);
  } finally {
    try {
      if (browser) await closeBrowserWithChartCleanup(browser, baseUrl);
    } finally {
      try {
        // After the browser is gone: the patient's draft, note lock, eChart row and the patient, by its key.
        if (demographicNo !== null) removeOwnedPatient(sql, demographicNo, ownedMarker);
      } finally {
        sql.dispose();
      }
    }
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
  try { h.assertStrictPage(recorder); } catch (diagnostic) { console.error(diagnostic.message); }
  process.exit(1);
});
