#!/usr/bin/env node
/*
 * Browser regression checks for Administration > Schedule Management > Schedule Setting.
 *
 * Covers the alpha10 report "unable to set week schedule": the week-setting
 * ("illustrated") window's Next button posted and saved the schedule, but the
 * admin shell stayed parked at the scroll offset the reader had used to reach
 * the button, so the next wizard step loaded off-screen and clicking Next
 * looked like it did nothing. The same root cause — resizeIframe() commented
 * out of the admin shell — also threw
 * "parent.parent.resizeIframe is not a function" out of the sibling Schedule
 * Management pages that call it.
 *
 * The script drives the whole three-step wizard end to end and asserts:
 *   1. Schedule Setting opens in the shell's #myFrame.
 *   2. Selecting a provider loads the week-setting window.
 *   3. Next advances to the calendar step, the shell is scrolled back to the
 *      top, and the framed page is not clipped by the shell's aspect box.
 *   4. The chosen template is actually applied to the generated schedule dates.
 *   5. Next on the calendar step reaches the "setting finished" step.
 *   6. The Schedule Management pages that call parent.parent.resizeIframe()
 *      load without a browser exception.
 *
 * It writes schedule rows for the selected provider, so run it against a
 * disposable local/dev database.
 *
 * Defaults are for the local devcontainer:
 *   node scripts/schedule-setting-playwright-checks.js
 *
 * Optional environment:
 *   BASE_URL=http://127.0.0.1:8080/carlos
 *   CHROME_PATH=/path/to/chrome-or-chromium
 *   TEST_USER=carlosdoc
 *   TEST_PASSWORD=carlos2026
 *   TEST_PIN=2026
 *   TEST_SCHEDULE_PROVIDER=999998  provider_no to configure; blank picks the logged-in provider
 *   ALLOW_NON_LOCAL_BASE_URL=true  only when intentionally targeting a non-local test app
 */

const { chromium } = require('playwright');

/*
 * Two tiers, because the two decisions they gate carry different risk.
 *
 * Loopback: traffic cannot leave this machine, so nothing but this machine can
 * answer it. TLS verification may only be skipped here — the check logs in with
 * real credentials, and a self-signed certificate from anywhere else is exactly
 * what certificate verification exists to reject. A packaged install is reached
 * the way the other packaged-install checks reach one: forward a loopback port
 * to the container's 443 and point BASE_URL at the forward.
 */
const LOOPBACK_HOSTS = new Set(['localhost', '127.0.0.1', '::1', '0:0:0:0:0:0:0:1']);

/*
 * Plus the devcontainer's compose service names, which resolve to sibling
 * containers on the Docker bridge network. Not loopback — so they never get a
 * TLS bypass — but still inside the developer's own machine, and nothing serves
 * TLS on them, so cleartext is allowed for these and nothing else.
 */
const LOCAL_HTTP_HOSTS = new Set([...LOOPBACK_HOSTS, 'db', 'carlos']);

const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const chromePath = process.env.CHROME_PATH || '';
const testUser = process.env.TEST_USER || 'carlosdoc';
const testPassword = process.env.TEST_PASSWORD || 'carlos2026';
const testPin = process.env.TEST_PIN || '2026';
const requestedProvider = process.env.TEST_SCHEDULE_PROVIDER || '';

// The wizard writes real rschedule/scheduledate rows. Use a range far enough out
// that it cannot collide with demo appointments a human is looking at.
const SCHEDULE_START = { year: '2031', month: '3', day: '3' };
const SCHEDULE_END = { year: '2031', month: '3', day: '28' };
const WEEKDAYS = ['mon', 'tue', 'wed', 'thu', 'fri'];

const findings = [];
const visited = [];

/** URL.hostname keeps the brackets on an IPv6 literal; the allowlists below do not. */
function normalizeHost(rawHost) {
  const host = (rawHost || '').toLowerCase();
  return host.startsWith('[') && host.endsWith(']') ? host.slice(1, -1) : host;
}

function isLoopbackHost(rawHost) {
  return LOOPBACK_HOSTS.has(normalizeHost(rawHost));
}

function isLocalHttpHost(rawHost) {
  return LOCAL_HTTP_HOSTS.has(normalizeHost(rawHost));
}

function validateBaseUrl(rawBaseUrl) {
  const parsed = new URL(rawBaseUrl);
  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw new Error(`BASE_URL must use http or https, got ${parsed.protocol}`);
  }
  // Credentials in the URL would ride every navigation and surface in the
  // diagnostics this check prints; it logs in through the form instead.
  if (parsed.username || parsed.password) {
    throw new Error('BASE_URL must not embed a username or password');
  }

  const host = normalizeHost(parsed.hostname);
  // Reachability is the loosest tier: everything the two sets above allow, plus
  // the bind-any address and the Docker host alias. Derived from LOCAL_HTTP_HOSTS
  // rather than restated, so a host added there cannot end up refused here.
  const localHosts = new Set([...LOCAL_HTTP_HOSTS, '0.0.0.0', 'host.docker.internal']);
  const privateIpv4 = /^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[0-1])\.)/.test(host);
  if (!localHosts.has(host) && !privateIpv4 && process.env.ALLOW_NON_LOCAL_BASE_URL !== 'true') {
    throw new Error(`Refusing non-local BASE_URL host ${host}; set ALLOW_NON_LOCAL_BASE_URL=true for an intentional test target`);
  }
  // This check logs in with real credentials, so anything outside this machine
  // must at least carry them over TLS — and, per the context below, prove its
  // certificate while doing it. Same rule as
  // scripts/flu-billing-report-playwright-checks.js.
  if (!isLocalHttpHost(host) && parsed.protocol !== 'https:') {
    throw new Error(`Non-local BASE_URL host ${host} must use https`);
  }
  parsed.pathname = parsed.pathname.replace(/\/$/, '');
  return parsed;
}

/**
 * Query strings on this application's routes carry provider numbers and other
 * identifiers that join back to records; the path and status are what diagnose a
 * failure. Record the path only.
 */
function recordableUrl(rawUrl) {
  try {
    const parsed = new URL(rawUrl);
    return `${parsed.origin}${parsed.pathname}`;
  } catch (e) {
    // Unparseable — a truncated or malformed URL quoted inside diagnostic text.
    // Falling back to the raw value would hand back the very query string this
    // function exists to drop, so cut it at the first query or fragment marker.
    return rawUrl.replace(/[?#].*$/, '');
  }
}

/**
 * Strip query strings out of free-form diagnostic text. Console message
 * locations, page error messages, and the Playwright stack we print on failure
 * all quote URLs verbatim, and this application's routes carry provider numbers
 * in the query string. The path is what identifies the failing route.
 */
function redactUrls(text) {
  if (typeof text !== 'string') {
    return text;
  }
  return text
    .replace(/\bhttps?:\/\/[^\s"'<>()]+/gi, (match) => recordableUrl(match))
    .replace(/(^|[\s"'(<=])(\/[^\s"'<>()?]*)\?[^\s"'<>()]*/g, '$1$2');
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

function isExpectedMissingAsset(status, responseUrl) {
  return status === 404 && (/\/imageRenderingServlet\?/.test(responseUrl) || /\/favicon\.ico$/.test(responseUrl));
}

function isExpectedConsoleNoise(message) {
  const text = message.text();
  return /Content Security Policy.*report-only/i.test(text)
    || /\[Report Only\]/.test(text)
    || /Master token \[CSRF-TOKEN\]/.test(text)
    || /Hidden (?:input element|token fields)/.test(text);
}

function isSevereConsoleMessage(message) {
  if (isExpectedConsoleNoise(message)) {
    return false;
  }
  const text = message.text();
  if (message.type() === 'error') {
    return !/imageRenderingServlet\?|favicon\.ico/i.test(text);
  }
  return /(ReferenceError|TypeError|SyntaxError|is not a function|Cannot read|Cannot set)/i.test(text);
}

function wirePage(page, label) {
  page.on('response', (response) => {
    const status = response.status();
    if (status >= 400 && !isExpectedMissingAsset(status, response.url())) {
      findings.push({ label, type: 'http', status, url: recordableUrl(response.url()) });
    }
  });
  page.on('console', (message) => {
    if (isSevereConsoleMessage(message)) {
      const location = message.location();
      findings.push({
        label,
        type: `console:${message.type()}`,
        text: redactUrls(message.text()),
        location: { ...location, url: recordableUrl(location.url) },
      });
    }
  });
  page.on('pageerror', (error) => {
    findings.push({ label, type: 'pageerror', text: redactUrls(error.message) });
  });
  page.on('dialog', async (dialog) => {
    // Every dialog here is a validation alert from the wizard — that is a failure,
    // not noise: the step under test did not submit.
    findings.push({ label, type: 'dialog', text: redactUrls(dialog.message()) });
    await dialog.accept();
  });
}

async function assertNoErrorPage(frame, label) {
  const bodyText = await frame.locator('body').innerText().catch(() => '');
  if (/CARLOS has encountered an unexpected error|CARLOS Error|HTTP Status 5\d\d|Exception Report/i.test(bodyText)) {
    findings.push({
      label,
      type: 'error-page',
      url: recordableUrl(frame.url()),
      body: redactUrls(bodyText.replace(/\s+/g, ' ')).slice(0, 500),
    });
  }
}

async function login(context) {
  const page = await context.newPage();
  wirePage(page, 'admin-shell');
  await safeGoto(page, '/', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.locator('#username').fill(testUser);
  await page.locator('#password').fill(testPassword);
  await page.locator('#pin').fill(testPin);
  await Promise.all([
    page.waitForURL(/providercontrol/, { timeout: 30000 }),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
  visited.push({ label: 'login', url: recordableUrl(page.url()) });
  return page;
}

/** Opens an Administration section through the shell's .xlink handler and returns its frame. */
async function openAdminSection(page, relSuffix, urlPattern, label) {
  await safeGoto(page, '/administration', { waitUntil: 'domcontentloaded', timeout: 30000 });
  const link = page.locator(`a.xlink[rel$="${relSuffix}"]`).last();
  await expandContainingAccordion(page, link);
  await link.click({ timeout: 20000 });
  const frame = await waitForFrame(page, urlPattern, label);
  if (frame) {
    visited.push({ label, url: recordableUrl(frame.url()) });
    await assertNoErrorPage(frame, label);
  }
  return frame;
}

/**
 * Several Schedule Management entries live only inside the left nav's collapsed
 * accordion (there is no quick-link card for them), so the link exists in the DOM
 * but is not clickable until its section is expanded.
 */
async function expandContainingAccordion(page, link) {
  if (await link.isVisible().catch(() => false)) {
    return;
  }
  const collapseId = await link.evaluate((el) => {
    const collapse = el.closest('.accordion-collapse');
    return collapse ? collapse.id : null;
  }).catch(() => null);
  if (!collapseId) {
    return;
  }
  await page.locator(`button[data-bs-target="#${collapseId}"]`).click({ timeout: 10000 }).catch(() => {});
  await link.waitFor({ state: 'visible', timeout: 10000 }).catch(() => {});
}

async function waitForFrame(page, urlPattern, label, timeoutMs = 30000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const frame = page.frames().find((f) => urlPattern.test(f.url()));
    if (frame) {
      await frame.waitForLoadState('domcontentloaded', { timeout: 15000 }).catch(() => {});
      return frame;
    }
    await page.waitForTimeout(250);
  }
  findings.push({ label, type: 'missing-frame', pattern: String(urlPattern) });
  return null;
}

/** Reads the shell's scroll offset and how far the framed document overflows its box. */
function readShellGeometry(page) {
  return page.evaluate(() => {
    const frame = document.getElementById('myFrame');
    const rect = frame ? frame.getBoundingClientRect() : null;
    let contentHeight = null;
    try {
      const doc = frame && frame.contentDocument;
      contentHeight = doc && doc.documentElement ? doc.documentElement.scrollHeight : null;
    } catch (e) {
      contentHeight = null;
    }
    const scroller = document.scrollingElement || document.documentElement;
    return {
      scrollY: Math.round(window.scrollY),
      shellHeight: Math.round(scroller.scrollHeight),
      viewportHeight: Math.round(scroller.clientHeight),
      frameTopInViewport: rect ? Math.round(rect.top) : null,
      frameHeight: rect ? Math.round(rect.height) : null,
      contentHeight: contentHeight === null ? null : Math.round(contentHeight),
    };
  });
}

/**
 * Put the shell where a reader is when they press a wizard button: scrolled down so
 * the bottom of the framed step — where every Next button sits — is on screen. The
 * defect only shows from this state, so the check has to reproduce it rather than
 * rely on Playwright's actionability scroll, which does nothing when the control
 * already happens to be above the fold.
 */
async function scrollShellToFramedStepBottom(page) {
  await page.evaluate(() => {
    const scroller = document.scrollingElement || document.documentElement;
    // The shell animates itself back to the top with jQuery on every framed load;
    // an animation still in flight would immediately undo this positioning.
    if (window.jQuery) {
      window.jQuery('html, body').stop(true, false);
    }
    // Defeat any smooth-scroll behaviour so the offset lands synchronously.
    const previous = scroller.style.scrollBehavior;
    scroller.style.scrollBehavior = 'auto';
    window.scrollTo(0, scroller.scrollHeight);
    scroller.style.scrollBehavior = previous;
  });
  // The offset is applied on a later frame in some engines; poll until it settles.
  let geometry = await readShellGeometry(page);
  for (let attempt = 0; attempt < 10; attempt++) {
    await page.waitForTimeout(150);
    const next = await readShellGeometry(page);
    if (next.scrollY === geometry.scrollY) {
      return next;
    }
    geometry = next;
  }
  return geometry;
}

/** The shell scrolls back to the top with a jQuery "slow" animation; give it time to settle. */
async function waitForShellScrollTop(page, timeoutMs = 6000) {
  const deadline = Date.now() + timeoutMs;
  let geometry = await readShellGeometry(page);
  while (Date.now() < deadline && geometry.scrollY > 2) {
    await page.waitForTimeout(200);
    geometry = await readShellGeometry(page);
  }
  return geometry;
}

/**
 * The defect this pins: after an in-frame navigation the reader must be looking at
 * the TOP of the newly loaded step, and the step must not be clipped by the shell's
 * fixed aspect-ratio box.
 */
function assertFramedStepIsVisible(geometry, label) {
  if (geometry.scrollY > 2) {
    findings.push({
      label,
      type: 'shell-not-scrolled-to-top',
      detail: `shell still scrolled ${geometry.scrollY}px down, so the new step loaded off-screen`,
    });
  }
  if (geometry.frameTopInViewport !== null && geometry.frameTopInViewport < -2) {
    findings.push({
      label,
      type: 'framed-step-above-viewport',
      detail: `frame top is ${geometry.frameTopInViewport}px above the viewport`,
    });
  }
  if (geometry.contentHeight !== null && geometry.frameHeight !== null
      && geometry.contentHeight - geometry.frameHeight > 2) {
    findings.push({
      label,
      type: 'framed-step-clipped',
      detail: `framed document is ${geometry.contentHeight}px tall inside a ${geometry.frameHeight}px frame`,
    });
  }
}

async function pickProvider(frame) {
  const options = await frame.locator('select[name="provider_no"] option')
    .evaluateAll((els) => els.map((el) => ({ value: el.value, label: (el.textContent || '').trim() })));
  const selectable = options.filter((option) => option.value);
  if (!selectable.length) {
    findings.push({ label: 'schedule-setting', type: 'no-selectable-provider' });
    return null;
  }
  if (requestedProvider) {
    const match = selectable.find((option) => option.value === requestedProvider);
    if (!match) {
      findings.push({
        label: 'schedule-setting',
        type: 'requested-provider-missing',
        detail: 'TEST_SCHEDULE_PROVIDER names a provider the Schedule Setting dropdown does not offer',
        selectableProviders: selectable.length,
      });
      return null;
    }
    return match;
  }
  const own = selectable.find((option) => option.label.toLowerCase().startsWith(testUser.toLowerCase()));
  return own || selectable[0];
}

async function fillWeekSchedule(frame, templateName) {
  await frame.locator('input[name="syear"]').fill(SCHEDULE_START.year);
  await frame.locator('input[name="smonth"]').fill(SCHEDULE_START.month);
  await frame.locator('input[name="sday"]').fill(SCHEDULE_START.day);
  await frame.locator('input[name="eyear"]').fill(SCHEDULE_END.year);
  await frame.locator('input[name="emonth"]').fill(SCHEDULE_END.month);
  await frame.locator('input[name="eday"]').fill(SCHEDULE_END.day);

  await frame.locator('select[name="mytemplate"]').selectOption(templateName);
  for (const day of WEEKDAYS) {
    await frame.locator(`input[name="check${day}"]`).check();
    // The "<<" button copies the selected template into that day's slot.
    await frame.locator(`input[name="${day}to1"]`).click();
  }
}

async function runWizard(context, page) {
  const settingFrame = await openAdminSection(page, '/schedule/TemplateSetting', /\/schedule\/TemplateSetting/, 'schedule-setting');
  if (!settingFrame) {
    return;
  }

  const provider = await pickProvider(settingFrame);
  if (!provider) {
    return;
  }
  await settingFrame.locator('select[name="provider_no"]').selectOption(provider.value);

  const weekFrame = await waitForFrame(page, /\/schedule\/TemplateApplying/, 'schedule-week-setting');
  if (!weekFrame) {
    return;
  }
  // Deliberately no provider number: it is an identifier that joins back to
  // records, and what a failed run actually needs to know is HOW the provider was
  // chosen — a caller reproducing the run sets TEST_SCHEDULE_PROVIDER themselves.
  visited.push({
    label: 'schedule-week-setting',
    url: recordableUrl(weekFrame.url()),
    providerSource: requestedProvider ? 'TEST_SCHEDULE_PROVIDER' : 'auto-selected',
  });
  await assertNoErrorPage(weekFrame, 'schedule-week-setting');
  assertFramedStepIsVisible(await waitForShellScrollTop(page), 'schedule-week-setting');

  const templates = await weekFrame.locator('select[name="mytemplate"] option').evaluateAll((els) => els.map((el) => el.value));
  if (!templates.length) {
    findings.push({ label: 'schedule-week-setting', type: 'no-day-template-available' });
    return;
  }
  const templateName = templates[0];
  await fillWeekSchedule(weekFrame, templateName);

  const weekScroll = await scrollShellToFramedStepBottom(page);
  visited.push({ label: 'schedule-week-setting-next', geometry: weekScroll });
  await weekFrame.locator('input[type="submit"]').click();

  const calendarFrame = await waitForFrame(page, /\/schedule\/CreateDate/, 'schedule-calendar');
  if (!calendarFrame) {
    findings.push({ label: 'schedule-week-setting', type: 'next-did-not-advance' });
    return;
  }
  await calendarFrame.waitForLoadState('load', { timeout: 30000 }).catch(() => {});
  visited.push({ label: 'schedule-calendar', url: recordableUrl(calendarFrame.url()) });
  await assertNoErrorPage(calendarFrame, 'schedule-calendar');
  assertFramedStepIsVisible(await waitForShellScrollTop(page), 'schedule-calendar');

  // The wizard is only useful if the week template actually reached the generated
  // schedule dates — "the schedule is saved" was the one part of the report that worked.
  const calendarText = (await calendarFrame.locator('body').innerText().catch(() => '')).replace(/\s+/g, ' ');
  if (!calendarText.includes(templateName)) {
    findings.push({ label: 'schedule-calendar', type: 'template-not-applied', template: templateName });
  }
  const effectiveRange = `${SCHEDULE_START.year}-${SCHEDULE_START.month.padStart(2, '0')}-${SCHEDULE_START.day.padStart(2, '0')}`;
  if (!calendarText.includes(effectiveRange)) {
    findings.push({ label: 'schedule-calendar', type: 'effective-range-missing', expected: effectiveRange });
  }

  const calendarScroll = await scrollShellToFramedStepBottom(page);
  visited.push({ label: 'schedule-calendar-next', geometry: calendarScroll });
  await calendarFrame.locator('input[type="submit"]').click();
  const finalFrame = await waitForFrame(page, /\/schedule\/DateFinal/, 'schedule-final');
  if (!finalFrame) {
    findings.push({ label: 'schedule-calendar', type: 'next-did-not-advance' });
    return;
  }
  await finalFrame.waitForLoadState('load', { timeout: 30000 }).catch(() => {});
  visited.push({ label: 'schedule-final', url: recordableUrl(finalFrame.url()) });
  await assertNoErrorPage(finalFrame, 'schedule-final');
  assertFramedStepIsVisible(await waitForShellScrollTop(page), 'schedule-final');

  const finalText = (await finalFrame.locator('body').innerText().catch(() => '')).replace(/\s+/g, ' ');
  if (!/finished/i.test(finalText)) {
    findings.push({ label: 'schedule-final', type: 'unexpected-final-step', body: finalText.slice(0, 300) });
  }

  await assertFrameHeightDoesNotRatchet(page, finalFrame, 'schedule-final');
}

/**
 * Reloading the SAME framed document must not change the frame's height.
 *
 * A document shorter than its frame reports a scrollHeight equal to the frame's
 * own height, so a sizing rule that adds its breathing-room margin before
 * comparing grows the frame on every single in-frame navigation and accumulates
 * blank space without limit across a multi-step flow. Reloading one document is
 * the tightest probe for that: nothing about the content changed, so nothing
 * about the frame may change either.
 */
async function assertFrameHeightDoesNotRatchet(page, frame, label) {
  const before = await readShellGeometry(page);
  for (let reload = 0; reload < 3; reload++) {
    // Wait for THIS frame's navigation before its load state: the frame is
    // already loaded when the wait is registered, so waitForLoadState('load')
    // on its own resolves immediately against the pre-reload document and the
    // probe would measure before anything happened.
    const reloaded = page.waitForEvent('framenavigated', {
      predicate: (navigated) => navigated === frame,
      timeout: 30000,
    }).then(() => frame.waitForLoadState('load', { timeout: 30000 })).catch(() => {});
    await Promise.all([
      reloaded,
      frame.evaluate(() => { window.location.reload(); }).catch(() => {}),
    ]);
    await page.waitForTimeout(1500);
  }
  const after = await waitForShellScrollTop(page);
  if (after.frameHeight !== null && before.frameHeight !== null
      && after.frameHeight > before.frameHeight) {
    findings.push({
      label,
      type: 'frame-height-ratchets',
      detail: `frame grew from ${before.frameHeight}px to ${after.frameHeight}px over 3 reloads of the same document`,
    });
  }
  visited.push({
    label: `${label}-reload-stability`,
    frameHeightBefore: before.frameHeight,
    frameHeightAfter: after.frameHeight,
  });
}

/**
 * The sibling Schedule Management pages that call parent.parent.resizeIframe().
 * They are reached from the same menu and were the source of the reported
 * "parent.parent.resizeIframe is not a function" console error.
 */
const RESIZE_CALLER_SECTIONS = [
  { label: 'schedule-my-group', rel: '/admin/ViewAdminDisplayMyGroup', pattern: /\/admin\/ViewAdminDisplayMyGroup/ },
  { label: 'schedule-prevention-notification', rel: '/prevention/ViewPreventionManager', pattern: /\/prevention\/ViewPreventionManager/ },
];

async function checkResizeIframeCallers(page) {
  for (const section of RESIZE_CALLER_SECTIONS) {
    const frame = await openAdminSection(page, section.rel, section.pattern, section.label);
    if (!frame) {
      continue;
    }
    await frame.waitForLoadState('load', { timeout: 30000 }).catch(() => {});
    // Let the framed page's own jQuery ready callback run and throw, if it is going to.
    await page.waitForTimeout(1500);
    assertFramedStepIsVisible(await waitForShellScrollTop(page), section.label);
  }
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
    const context = await browser.newContext({
      ignoreHTTPSErrors: isLoopbackHost(baseUrl.hostname),
      viewport: { width: 1440, height: 900 },
    });
    const page = await login(context);
    context.on('page', (popup) => wirePage(popup, 'popup'));

    await runWizard(context, page);
    await checkResizeIframeCallers(page);

    console.log(JSON.stringify({ visited, findings }, null, 2));

    if (findings.length) {
      throw new Error(`schedule setting browser check found ${findings.length} issue(s)`);
    }
    console.log('PASS schedule setting wizard advances through every step with the new step in view');
  } finally {
    await browser.close();
  }
})().catch((error) => {
  console.error('FAIL schedule setting Playwright check');
  console.error(redactUrls(error.stack || error.message));
  process.exit(1);
});
