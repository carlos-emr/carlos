#!/usr/bin/env node
/*
 * Browser regression check for clinical free text surviving the packaged WAF.
 *
 * The eChart chart-print 403 (package exclusion 1010) was not a one-off. On a
 * packaged (deb) deployment nginx runs ModSecurity with the OWASP CRS in
 * blocking mode, and the CRS content signatures cannot tell a clinician's prose
 * from an attack: rules 932100/932110 read a sentence-ending semicolon as a
 * shell command separator, 930100/930110 read a "../" in a reference to a filed
 * report as path traversal, 941100/941160 read HTML pasted out of a hospital
 * report as an XSS attribute vector. At PL1 with an inbound threshold of 5, one
 * match blocks the save with nginx's bare 403 and no application log line.
 *
 * A survey of the clinician-facing free-text POST arguments through the
 * packaged front door found EVERY one of them blocked on the same ordinary
 * sentence. This check drives the two workflows that carry the most prose and
 * are reachable without fixture setup — the consultation request (a referral
 * letter) and the demographic master record's Alert and Notes — through the
 * real UI, once per phrase in a corpus chosen so that each phrase trips a
 * different CRS family.
 *
 * Like scripts/echart-print-playwright-checks.js, this only covers the WAF
 * defect when run through the packaged `:443` front door. Against the
 * devcontainer (no WAF) the phrases are just ordinary notes and the check
 * degrades to guarding that these two save paths still work.
 *
 * Defaults are for the local devcontainer:
 *   node scripts/clinical-freetext-playwright-checks.js
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc
 *   TEST_PASSWORD=carlos2026
 *   TEST_PIN=2026
 *   CLINICAL_DEMOGRAPHIC_NO=1
 *   CLINICAL_PROVIDER_NO=999998
 *   CLINICAL_CONSULT_SERVICE_ID=1
 *   ALLOW_NON_LOCAL_BASE_URL=true only for a disposable install that is not this
 *     machine — this check writes, so a private LAN address needs the opt-in too
 */

const { chromium } = require('playwright');

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';
const demographicNo = requireDigits(process.env.CLINICAL_DEMOGRAPHIC_NO || '1', 'CLINICAL_DEMOGRAPHIC_NO');
const providerNo = requireDigits(process.env.CLINICAL_PROVIDER_NO || '999998', 'CLINICAL_PROVIDER_NO');
const consultationServiceId = requireDigits(process.env.CLINICAL_CONSULT_SERVICE_ID || '1', 'CLINICAL_CONSULT_SERVICE_ID');

const saveResults = [];
const badResponses = [];

// Each phrase is a sentence a clinician would actually write, measured through
// the packaged front door to score over the CRS inbound threshold on its own.
// The rule ids are what the ModSecurity audit log reported.
const PROSE_CORPUS = [
  { label: 'plain prose', text: 'Routine follow up. Patient doing well.', crs: 'none' },
  { label: 'sentence semicolon', text: 'Reviewed labs with the patient; find attached the CBC and lytes.', crs: '932100/932110 attack-rce' },
  { label: 'shell-shaped cost', text: 'Cost ${45} per month; patient declined the brand.', crs: '932130 attack-rce' },
  { label: 'relative file path', text: 'See scanned report ../../images/ecg.png for the tracing.', crs: '930100/930110 attack-lfi' },
  { label: 'pasted report html', text: 'Result <span style="color:red">HIGH</span> flagged by the lab.', crs: '941100/941160 attack-xss' },
  { label: 'wound measurement', text: 'Wound <2cm, clean. <?> follow up in 1 week.', crs: '933100 attack-injection-php' },
];

/*
 * Deliberately NARROWER than the guard the read-only checks share: this one
 * admits only hosts that are unambiguously this machine or its compose network,
 * and not the private IPv4 ranges the shared guard also allows. Both of this
 * check's workflows perform REAL writes — a run rewrites the patient's Alert and
 * Notes with a corpus phrase and files six consultation requests — and RFC1918
 * is exactly where a real clinic's server lives. Same reasoning, and the same
 * opt-in, as the fixture-teardown guards in edoc-schedule-navigation and
 * assign-role.
 */
function validateBaseUrl(rawBaseUrl) {
  const parsed = new URL(rawBaseUrl);
  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw new Error(`BASE_URL must use http or https, got ${parsed.protocol}`);
  }

  const exactLocalHosts = new Set(['localhost', '127.0.0.1', '::1', '0.0.0.0', 'host.docker.internal', 'carlos']);
  const host = parsed.hostname.toLowerCase().replace(/^\[|\]$/g, '');
  if (!exactLocalHosts.has(host) && process.env.ALLOW_NON_LOCAL_BASE_URL !== 'true') {
    throw new Error(`Refusing non-local BASE_URL host ${host}: this check overwrites clinical free text and files `
      + 'consultation requests. Set ALLOW_NON_LOCAL_BASE_URL=true only for a disposable test install');
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
 * The demo dataset ships no provider signature stamp, so the consultation form's
 * letterhead image 404s. That is a fixture gap, not a regression, and it is never
 * the 403 this check exists to catch.
 */
function isExpectedMissingFixture(status, responseUrl) {
  return status === 404 && /\/provider\/providerSignatureImage\?/.test(responseUrl);
}

function wirePage(page, label) {
  page.on('dialog', async (dialog) => {
    badResponses.push({ label, type: 'dialog', text: dialog.message() });
    await dialog.accept();
  });
  page.on('response', (response) => {
    if (response.status() >= 400 && !isExpectedMissingFixture(response.status(), response.url())) {
      badResponses.push({ label, status: response.status(), url: response.url() });
    }
  });
}

async function login(context) {
  const page = await context.newPage();
  wirePage(page, 'login');
  await page.goto(appUrl('/'), { waitUntil: 'domcontentloaded' }); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- appUrl rejects non-root-relative paths and validateBaseUrl restricts hosts to this machine unless explicitly opted out of
  await page.locator('#username').fill(testUser);
  await page.locator('#password').fill(testPassword);
  await page.locator('#pin').fill(testPin);
  await Promise.all([
    page.waitForURL(/providercontrol/, { timeout: 30000 }),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
  await page.close();
}

/**
 * One clinical workflow: the page a clinician opens, the form on it, and the
 * free-text fields that carry their prose.
 *
 * The check opens the page ONCE per workflow and serialises the real form —
 * every hidden field, and the CSRF token CSRFGuard injected into it — then
 * replays that exact body once per prose phrase with only the free-text fields
 * swapped. Clicking through each form's own validation JS six times instead
 * would measure the form's bespoke required-field rules (a missing consultation
 * service, a demographic name check) rather than the WAF, and those rules
 * differ per form and per record state. The replay posts through the page's own
 * session over the same route, so the request the WAF sees is the real one.
 */
const WORKFLOWS = [
  {
    name: 'consultation request',
    // The referral letter — the longest prose a clinician writes into CARLOS.
    // ConsultationFormRequest.jsp reads the patient from `de` only; it takes
    // demographicNo and providerNo from the form's own hidden fields, so passing
    // them in the query string here would be dead weight.
    open: () => appUrl('/encounter/ViewRequest', new URLSearchParams({
      de: demographicNo,
    }).toString()),
    ready: 'textarea[name="reasonForConsultation"]',
    formName: 'EctConsultationFormRequest2Form',
    action: '/encounter/RequestConsultation',
    fields: ['reasonForConsultation', 'clinicalInformation', 'concurrentProblems', 'currentMedications', 'allergies'],
    // checkForm() sets `service` and `saved` before it submits, and refuses without
    // a service. EctConsultationFormRequest2Action branches on the `submission`
    // prefix: `Submit…` creates a request, `Update…` edits the one named by
    // ARGS:requestId. This URL carries no requestId and EctViewRequest2Action sets
    // no reqId attribute, so ConsultationFormRequest.jsp always renders the
    // new-request form — `Submit` is the branch that actually stores the prose.
    overrides: () => ({ service: consultationServiceId, saved: 'true', submission: 'Submit Consultation Request' }),
    // Guard the assumption above rather than trusting it. If a future change makes
    // this URL render an existing request, `Submit` would file a duplicate instead
    // of measuring this save — the replay would still reach the WAF, but it would
    // stop being the save this check reports.
    requireEmptyFields: { requestId: 'the page rendered an existing consultation, not the new-request form' },
  },
  {
    name: 'demographic alert and notes',
    // The patient master record's Alert and Notes: standing clinical instructions.
    open: () => appUrl('/demographic/DemographicEdit', new URLSearchParams({
      demographic_no: demographicNo, displaymode: 'edit', dboperation: 'search_detail',
    }).toString()),
    ready: 'textarea[name="alert"]',
    formName: 'updatedelete',
    action: '/demographic/DemographicUpdate',
    fields: ['alert', 'notes'],
    overrides: () => ({ displaymode: 'Update Record', dboperation: 'update_record' }),
  },
];

/**
 * Serialises the named form in the page, exactly as the browser would on submit.
 */
async function captureForm(page, formName) {
  return page.evaluate((name) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- the page function is a literal and `name` is a form name constant from WORKFLOWS, structured-cloned rather than interpolated into page script
    const form = document.forms[name];
    if (!form) return null;
    return Array.from(new FormData(form).entries())
      .filter(([, value]) => typeof value === 'string');
  }, formName);
}

/**
 * Replays a captured body with the free-text fields carrying `phrase`, from
 * inside the page so the request uses the same session and origin.
 */
async function replay(page, workflow, entries, phrase) {
  return page.evaluate(async ({ pairs, action, fields, text, overrides }) => { // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- the page function is a literal; the arguments are the form body this same page just rendered plus constants from this file, structured-cloned rather than interpolated into page script
    const body = new URLSearchParams();
    const replaced = new Set(fields);
    for (const [key, value] of pairs) {
      if (replaced.has(key)) continue;
      if (Object.prototype.hasOwnProperty.call(overrides, key)) continue;
      body.append(key, value);
    }
    for (const field of fields) body.append(field, text);
    for (const [key, value] of Object.entries(overrides)) body.append(key, value);

    const token = document.querySelector('input[name="CSRF-TOKEN"]');
    const response = await fetch(action, {
      method: 'POST',
      credentials: 'same-origin',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        ...(token && token.value ? { 'CSRF-TOKEN': token.value } : {}),
      },
      body: body.toString(),
      redirect: 'manual',
    });
    // A save that succeeds usually answers with a redirect. `redirect: 'manual'`
    // normally surfaces that as an opaque-redirect response (type
    // 'opaqueredirect', status 0), but report a plain 3xx as the same redirect
    // rather than as a failure, so a change in how the browser filters manual
    // redirects cannot turn a successful save into a spurious FAIL. A WAF
    // rejection is never either of those: it is always a real 403 from nginx.
    if (response.type === 'opaqueredirect') return 'redirect';
    if (response.status >= 300 && response.status < 400) return 'redirect';
    return response.status;
  }, {
    pairs: entries,
    action: new URL(appUrlForPage(workflow.action)).pathname,
    fields: workflow.fields,
    text: phrase.text,
    overrides: workflow.overrides(),
  });
}

function appUrlForPage(appPath) {
  return appUrl(appPath);
}

async function runWorkflow(context, workflow) {
  const page = await context.newPage();
  wirePage(page, workflow.name);
  try {
    await page.goto(workflow.open(), { waitUntil: 'domcontentloaded' }); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- appUrl rejects non-root-relative paths and validateBaseUrl restricts hosts to this machine unless explicitly opted out of
    await page.locator(workflow.ready).first().waitFor({ state: 'attached', timeout: 30000 });
    // CSRFGuard's client script fills the hidden input after the page loads, so wait
    // for a populated token rather than racing it; a still-empty one is reported by
    // the assertion below rather than as a timeout.
    await page.waitForFunction( // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- the page function is a literal and `name` is a form name constant from WORKFLOWS, structured-cloned rather than interpolated into page script
      (name) => {
        const form = document.forms[name];
        if (!form) return false;
        const inputs = form.querySelectorAll('input[name="CSRF-TOKEN"]');
        return inputs.length > 0 && Array.from(inputs).every((input) => input.value !== '');
      },
      workflow.formName,
      { timeout: 15000 },
    ).catch(() => {});

    const entries = await captureForm(page, workflow.formName);
    assert(entries && entries.length,
      `${workflow.name}: form ${workflow.formName} was not on the page, so nothing was measured`);
    // The token has to be POPULATED, not merely present: CSRFGuard's client script
    // fills the hidden input after the page loads, and an empty one would answer
    // the replay with a CSRF 403 that this check would report as a WAF block.
    const csrfToken = entries.find(([key]) => key === 'CSRF-TOKEN');
    assert(csrfToken && csrfToken[1].trim() !== '',
      `${workflow.name}: form ${workflow.formName} carried no populated CSRF token `
      + `(${csrfToken ? 'the field is empty' : 'the field is absent'}), so a 403 could not be attributed to the WAF`);

    // Fail loudly rather than quietly measuring something else: each workflow's
    // overrides assume a particular render of its page, and a hidden field is the
    // cheapest way to confirm the page came back in that shape.
    for (const [key, value] of Object.entries(workflow.requireEmptyFields || {})) {
      const found = entries.find(([name]) => name === key);
      assert(found && found[1].trim() === '',
        `${workflow.name}: form field ${key} is set to "${found ? found[1] : '(absent)'}" (${value}), `
        + 'so this run would not measure what the check claims');
    }

    for (const phrase of PROSE_CORPUS) {
      const status = await replay(page, workflow, entries, phrase);
      saveResults.push({
        workflow: workflow.name, phrase: phrase.label, crs: phrase.crs, status,
      });
    }
  } finally {
    await page.close();
  }
}

(async () => {
  const browser = await chromium.launch(chromePath ? { executablePath: chromePath } : {});
  const context = await browser.newContext({ ignoreHTTPSErrors: true, acceptDownloads: true });

  try {
    await login(context);

    for (const workflow of WORKFLOWS) {
      await runWorkflow(context, workflow);
    }

    const blocked = saveResults.filter((result) => result.status === 403);
    assert(blocked.length === 0,
      'a clinical free-text save was rejected with HTTP 403 — on a packaged install this is the WAF rejecting '
      + 'the clinician\'s own prose, and the exclusions in '
      + 'debian/assets/modsecurity/REQUEST-900-EXCLUSION-RULES-BEFORE-CRS.conf no longer cover these arguments: '
      + `${JSON.stringify(blocked, null, 2)}`);

    const failed = saveResults.filter((result) => result.status !== 200 && result.status !== 'redirect');
    assert(failed.length === 0, `a clinical free-text save did not return HTTP 200: ${JSON.stringify(failed, null, 2)}`);

    const wafBlocked = badResponses.filter((entry) => entry.status === 403);
    assert(wafBlocked.length === 0,
      `a request carrying clinical free text was rejected with HTTP 403: ${JSON.stringify(wafBlocked, null, 2)}`);

    assert(badResponses.length === 0, `unexpected HTTP errors or dialogs: ${JSON.stringify(badResponses, null, 2)}`);

    console.log(`PASS ${WORKFLOWS.length} clinical free-text workflows saved `
      + `${PROSE_CORPUS.length} prose variants each without a WAF rejection`);
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error('FAIL clinical free-text Playwright check');
  console.error(error.stack || error.message);
  if (saveResults.length) {
    console.error(`Save results: ${JSON.stringify(saveResults, null, 2)}`);
  }
  if (badResponses.length) {
    console.error(`HTTP errors: ${JSON.stringify(badResponses, null, 2)}`);
  }
  process.exit(1);
});
