#!/usr/bin/env node
/*
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
 * Browser checks for the concurrent-session policy (issue #3980).
 *
 * Each isolated Playwright browser context stands for a separate browser or
 * device signing in as the same user. The checks drive the real login form and
 * the real chooser buttons, and read the outcome the way a user would: which
 * page each browser lands on next.
 *
 * The policy is read from carlos.properties when CARLOS starts, so this script
 * cannot switch it. Tell it which policy the server runs, then run it once per
 * policy, restarting CARLOS in between:
 *
 *   CONCURRENT_SESSION_POLICY=allow  (default) login behaves as before: no chooser
 *   CONCURRENT_SESSION_POLICY=prompt           the chooser, keep, sign out, cancel
 *   CONCURRENT_SESSION_POLICY=single           older sessions are signed out
 *   CONCURRENT_SESSION_MAX=<n>                 login.concurrent_sessions.max; with
 *                                              allow or prompt the (n+1)th browser
 *                                              must sign out the others
 *
 * Every policy also checks the chooser route itself: GET and HEAD answer 405,
 * and a POST without the CSRF token cannot finish the pending login.
 *
 * Optional audit assertions (read-only, needs the mysql client):
 *   MYSQL_PASSWORD, MYSQL_HOST (default localhost), MYSQL_USER, MYSQL_DATABASE
 *   Checks the log table for the concurrent_sessions_* rows this run wrote.
 *
 * It signs out the test user's OTHER sessions under prompt and single. Run it on
 * a disposable test deployment, and not while another check is signed in as the
 * same user.
 *
 * Common environment (see scripts/lib/playwright-harness.js readConfig):
 *   BASE_URL, CHROME_PATH, TEST_USER, TEST_PASSWORD, TEST_PIN
 *
 *   npm run test:concurrent-session-policy-playwright
 */

const {
  SkipCheck,
  appUrl,
  assert,
  assertStrictPage,
  buildFailureDetails,
  createRecorder,
  createSqlRunner,
  gotoApp,
  launchBrowser,
  login,
  newContext: newBrowserContext,
  readConfig,
  runCheck,
  sqlString,
  wireStrictPage,
} = require('./lib/playwright-harness');

const recorder = createRecorder();

async function newContext(browser, config) {
  const context = await newBrowserContext(browser, config);
  context.on('page', (page) => wireStrictPage(page, 'session-policy', recorder));
  return context;
}

async function waitForPageAssets(page) {
  await page.waitForLoadState('load');
  // Fonts may start loading after DOMContentLoaded. Finish them before a test-driven
  // navigation/reload, which would otherwise abort a valid request and trip strict checks.
  await page.evaluate(async () => { await document.fonts.ready; });
}

const SCHEDULE_URL = /provider\/(providercontrol|ViewAppointmentAdminDay)/;
const AFTER_FACILITY_OR_SCHEDULE = /provider\/(providercontrol|ViewAppointmentAdminDay)|select_facility/i;
const AFTER_LOGIN_SUBMIT = /providercontrol|appointment|forcepasswordreset|loginMfa|select_facility|\/login[?#]|\/login$/i;

function readPolicy(env = process.env) {
  const policy = (env.CONCURRENT_SESSION_POLICY || 'allow').trim().toLowerCase();
  assert(['allow', 'prompt', 'single'].includes(policy),
    `CONCURRENT_SESSION_POLICY must be allow, prompt or single, got ${policy}`);
  const rawMax = (env.CONCURRENT_SESSION_MAX || '0').trim();
  // Validate the whole string: parseInt would silently truncate "2.5" or "2abc" to 2.
  assert(/^\d+$/.test(rawMax), `CONCURRENT_SESSION_MAX must be a whole number, got ${rawMax}`);
  const max = Number(rawMax);
  return { policy, max };
}

/**
 * Submits the login form and reports where the browser landed: the schedule or
 * the chooser. Anything else (forced reset, MFA) means the account is not ready
 * for this check.
 */
async function signIn(context, config, label) {
  const page = await context.newPage();
  await gotoApp(page, config.baseUrl, '/');
  await waitForPageAssets(page);
  await page.locator('#username').fill(config.testUser);
  await page.locator('#password').fill(config.testPassword);
  const pin = page.locator('#pin');
  if (await pin.count() > 0) await pin.fill(config.testPin);
  await Promise.all([
    page.waitForURL(AFTER_LOGIN_SUBMIT, { timeout: 30000 }),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
  await page.waitForLoadState('domcontentloaded');
  if (/forcepasswordreset|loginMfa/i.test(page.url())) {
    throw new SkipCheck(`${config.testUser} must finish its forced reset / MFA before this check (${label})`);
  }
  const atChooser = await page.locator('#sessionChoiceForm').count() > 0;
  if (!atChooser) {
    await passFacilitySelection(page);
  }
  await waitForPageAssets(page);
  return { page, atChooser };
}

/**
 * Finishes a login that landed on (or is heading to) the schedule. A provider linked to several
 * facilities is sent to /select_facility first; pick the first facility the way the shared
 * harness login() does, then require the schedule.
 */
async function passFacilitySelection(page) {
  await page.waitForURL(AFTER_FACILITY_OR_SCHEDULE, { timeout: 30000 });
  if (/select_facility/i.test(page.url())) {
    await waitForPageAssets(page);
    await Promise.all([
      page.waitForURL(SCHEDULE_URL, { timeout: 30000 }),
      page.locator('form button[type="submit"], form input[type="submit"]').first().click(),
    ]);
    console.log('  ok selected a facility before reaching the schedule');
  }
  await page.waitForURL(SCHEDULE_URL, { timeout: 30000 });
}

async function assertAuthenticated(page, config, label) {
  await gotoApp(page, config.baseUrl, '/provider/providercontrol');
  await page.waitForLoadState('domcontentloaded');
  await waitForPageAssets(page);
  assert(SCHEDULE_URL.test(page.url()), `${label} is no longer signed in: landed on ${new URL(page.url()).pathname}`);
  const html = await page.content();
  assert(/Schedule|appointment|provider/i.test(html), `${label} did not render the schedule`);
}

/** Reads the server's own answer about this browser's session, without navigating. */
async function heartbeat(page, config) {
  const response = await page.request.get(appUrl(config.baseUrl, '/status/SessionHeartbeat'));
  assert(response.ok(), `heartbeat answered ${response.status()}`);
  return (await response.json()).valid === true;
}

/**
 * A signed-out browser's next click must land on the login page with the notice,
 * in one hop and never through /logoutPage (issue #2245 was a redirect loop
 * there), and the notice must not come back on a reload.
 */
async function assertSignedOutElsewhere(page, config, label) {
  // A fetch that follows an unauthenticated redirect must not consume the one-time notice.
  const background = await page.evaluate(async (url) => {
    const response = await fetch(url, { credentials: 'same-origin' });
    return { status: response.status, body: await response.text() };
  }, appUrl(config.baseUrl, '/provider/providercontrol'));
  assert(background.status === 200, `${label} background redirect did not reach the login page`);
  assert(!background.body.includes('id="signedOutElsewhereNotice"'), `${label} background fetch consumed the notice`);
  const legacy = await page.request.get(appUrl(config.baseUrl, '/index'), {
    headers: { 'X-Requested-With': 'XMLHttpRequest' },
  });
  assert(legacy.ok(), `${label} legacy AJAX request failed`);
  assert(!(await legacy.text()).includes('id="signedOutElsewhereNotice"'), `${label} legacy AJAX consumed the notice`);
  const paths = [];
  const onNavigate = (frame) => {
    if (frame === page.mainFrame()) paths.push(new URL(frame.url()).pathname);
  };
  page.on('framenavigated', onNavigate);
  await gotoApp(page, config.baseUrl, '/provider/providercontrol');
  await page.waitForLoadState('domcontentloaded');
  page.off('framenavigated', onNavigate);

  const landed = new URL(page.url()).pathname;
  assert(/\/index$/.test(landed), `${label} landed on ${landed}, expected the login page; path ${paths.join(' -> ')}`);
  assert(!paths.some((p) => p.endsWith('/logoutPage')), `${label} went through /logoutPage: ${paths.join(' -> ')}`);
  assert(paths.filter((p) => p.endsWith('/index')).length <= 1, `${label} looped on the login page: ${paths.join(' -> ')}`);
  await page.locator('#username').waitFor({ timeout: 10000 });
  const notice = page.locator('#signedOutElsewhereNotice');
  assert(await notice.count() === 1, `${label} login page did not explain the sign-out`);
  assert((await notice.innerText()).trim().length > 10, `${label} sign-out notice is blank`);
  await waitForPageAssets(page);

  await page.reload({ waitUntil: 'domcontentloaded' });
  await waitForPageAssets(page);
  assert(await page.locator('#signedOutElsewhereNotice').count() === 0, `${label} notice was shown twice`);
  assert(await heartbeat(page, config) === false, `${label} still has an authenticated session`);
}

async function chooserFacts(page) {
  await page.locator('#sessionChoiceCard').waitFor({ timeout: 10000 });
  // CSRFGuard injects its token into the real POST form after its script loads.
  await page.waitForFunction(() => {
    const input = document.querySelector('#sessionChoiceForm input[name="CSRF-TOKEN"]');
    return input && input.value && input.value.length > 10;
  }, null, { timeout: 15000 });
  return {
    rows: await page.locator('#otherSessionsTable tbody tr').count(),
    keepOffered: await page.locator('#keepOtherSessions').count() === 1,
    limitNotice: await page.locator('#sessionChoiceLimitReached').count() === 1,
    warning: (await page.locator('#sessionChoiceWarning').innerText()).trim(),
    intro: (await page.locator('#sessionChoiceIntro').innerText()).trim(),
  };
}

async function clickAndReachSchedule(page, selector) {
  await Promise.all([
    page.waitForURL(AFTER_FACILITY_OR_SCHEDULE, { timeout: 30000 }),
    page.locator(selector).click(),
  ]);
  await passFacilitySelection(page);
  await waitForPageAssets(page);
}

async function checkRouteGuards(browser, config, step) {
  const context = await newContext(browser, config);
  try {
    const page = await context.newPage();
    for (const method of ['GET', 'HEAD']) {
      const response = await page.request.fetch(appUrl(config.baseUrl, '/login/sessionChoice'), {
        method, maxRedirects: 0,
      });
      assert(response.status() === 405, `${method} /login/sessionChoice answered ${response.status()}, expected 405`);
      assert((response.headers().allow || '').includes('POST'), `${method} /login/sessionChoice is missing Allow: POST`);
    }
    const post = await page.request.post(appUrl(config.baseUrl, '/login/sessionChoice'), {
      form: { sessionChoice: 'signOutOthers' }, maxRedirects: 0,
    });
    const location = post.headers().location || '';
    assert(!/providercontrol/.test(location), 'a bare POST to the chooser reached the schedule');
    assert(await heartbeat(page, config) === false, 'a bare POST to the chooser created a signed-in session');
    step('chooser route answers GET/HEAD with 405 and a bare POST signs nobody in');
  } finally {
    await context.close();
  }
}

async function checkAllow(browser, config, step, contexts) {
  const a = await newContext(browser, config);
  const b = await newContext(browser, config);
  contexts.push(a, b);
  const first = await signIn(a, config, 'browser A');
  const second = await signIn(b, config, 'browser B');
  assert(!first.atChooser && !second.atChooser, 'the default policy showed the session chooser');
  await assertAuthenticated(first.page, config, 'browser A');
  await assertAuthenticated(second.page, config, 'browser B');
  step('default policy: a second browser signs in with no chooser and both stay signed in');
}

async function checkPrompt(browser, config, step, contexts) {
  const a = await newContext(browser, config);
  const b = await newContext(browser, config);
  const c = await newContext(browser, config);
  const d = await newContext(browser, config);
  contexts.push(a, b, c, d);

  const first = await signIn(a, config, 'browser A');
  assert(!first.atChooser, 'browser A saw the chooser with no other session signed in');

  const second = await signIn(b, config, 'browser B');
  assert(second.atChooser, 'browser B was not asked about browser A');
  const facts = await chooserFacts(second.page);
  assert(facts.rows >= 1, 'the chooser listed no other session');
  assert(/\d/.test(facts.intro), 'the chooser did not say how many other sessions there are');
  assert(facts.warning.length > 40, 'the chooser did not warn that unsaved work is lost');
  assert(facts.keepOffered, 'keep was not offered below the limit');
  assert(await heartbeat(second.page, config) === false,
    'browser B is already signed in while its chooser is still open');
  step('prompt: the chooser appears before the schedule, lists the other session and warns about unsaved work');

  // A POST carrying B's cookie but not its CSRF token must not finish the login.
  const forged = await second.page.request.post(appUrl(config.baseUrl, '/login/sessionChoice'), {
    form: { sessionChoice: 'signOutOthers' }, maxRedirects: 0,
  });
  assert(!/providercontrol/.test(forged.headers().location || ''), 'a CSRF-less chooser POST reached the schedule');
  assert(await heartbeat(second.page, config) === false, 'a CSRF-less chooser POST signed browser B in');
  await assertAuthenticated(first.page, config, 'browser A after the CSRF-less POST');
  step('prompt: a chooser POST without the CSRF token signs nobody in and nobody out');

  await clickAndReachSchedule(second.page, '#keepOtherSessions');
  await assertAuthenticated(first.page, config, 'browser A after B kept it');
  await assertAuthenticated(second.page, config, 'browser B after keeping');
  step('prompt: "keep" signs browser B in and leaves browser A signed in');

  const cancelled = await signIn(d, config, 'browser D');
  assert(cancelled.atChooser, 'browser D was not asked');
  await chooserFacts(cancelled.page);
  await Promise.all([
    cancelled.page.waitForURL(/\/index/, { timeout: 30000 }),
    cancelled.page.locator('#cancelSessionChoice').click(),
  ]);
  assert(await heartbeat(cancelled.page, config) === false, 'cancel left browser D signed in');
  await assertAuthenticated(first.page, config, 'browser A after D cancelled');
  step('prompt: cancel returns to the login page and signs nobody out');

  const third = await signIn(c, config, 'browser C');
  assert(third.atChooser, 'browser C was not asked about A and B');
  const thirdFacts = await chooserFacts(third.page);
  assert(thirdFacts.rows >= 2, `browser C saw ${thirdFacts.rows} other session(s), expected at least 2`);
  await clickAndReachSchedule(third.page, '#signOutOtherSessions');
  await assertAuthenticated(third.page, config, 'browser C after signing the others out');
  await assertSignedOutElsewhere(first.page, config, 'browser A');
  await assertSignedOutElsewhere(second.page, config, 'browser B');
  await assertAuthenticated(third.page, config, 'browser C after A and B were sent to login');
  step('prompt: "sign out other sessions" ends A and B; each lands once on the login page with the notice, no loop');

  return ['concurrent_sessions_prompted', 'concurrent_sessions_kept', 'concurrent_sessions_revoked'];
}

async function checkSingle(browser, config, step, contexts) {
  const a = await newContext(browser, config);
  const b = await newContext(browser, config);
  contexts.push(a, b);
  const first = await signIn(a, config, 'browser A');
  const second = await signIn(b, config, 'browser B');
  assert(!second.atChooser, 'the single policy asked instead of signing A out');
  await assertAuthenticated(second.page, config, 'browser B');
  await assertSignedOutElsewhere(first.page, config, 'browser A');
  step('single: a new sign-in ends the older session, which lands on the login page with the notice');
  return ['concurrent_sessions_revoked_auto'];
}

async function checkLimit(browser, config, step, contexts, max, policy) {
  const pages = [];
  for (let i = 0; i < max; i += 1) {
    const context = await newContext(browser, config);
    contexts.push(context);
    const signedIn = await signIn(context, config, `browser ${i + 1}`);
    if (signedIn.atChooser) {
      // Below the limit under prompt: keep is offered and keeps the earlier ones.
      assert(policy === 'prompt', `browser ${i + 1} was asked below the limit under ${policy}`);
      assert((await chooserFacts(signedIn.page)).keepOffered, `browser ${i + 1} was refused keep below the limit`);
      await clickAndReachSchedule(signedIn.page, '#keepOtherSessions');
    }
    pages.push(signedIn.page);
  }
  // Exercise the shared suite helper at the limit: it must stop without revoking
  // any of the authenticated browsers, even though the chooser offers sign-out.
  const sharedHelperContext = await newContext(browser, config);
  contexts.push(sharedHelperContext);
  let refused;
  try {
    await login(sharedHelperContext, config, recorder);
  } catch (error) {
    refused = error;
  }
  assert(refused && /configured login\.concurrent_sessions\.max limit/.test(refused.message),
    'the shared login helper did not report the session limit');
  const pendingPage = sharedHelperContext.pages()[0];
  await waitForPageAssets(pendingPage);
  assert(await heartbeat(pendingPage, config) === false, 'the refused shared login became authenticated');
  for (const page of pages) {
    assert(await heartbeat(page, config), 'the shared login helper revoked an existing session');
  }
  step('shared login helper refuses at the limit and preserves every existing session');

  const overLimit = await newContext(browser, config);
  contexts.push(overLimit);
  const last = await signIn(overLimit, config, `browser ${max + 1}`);
  assert(last.atChooser, `browser ${max + 1} was not stopped at the limit of ${max}`);
  const facts = await chooserFacts(last.page);
  assert(!facts.keepOffered, 'keep was offered with the session limit reached');
  assert(facts.limitNotice, 'the chooser did not explain the session limit');
  await clickAndReachSchedule(last.page, '#signOutOtherSessions');
  for (let i = 0; i < pages.length; i += 1) {
    await assertSignedOutElsewhere(pages[i], config, `browser ${i + 1}`);
  }
  step(`max=${max}: browser ${max + 1} must sign the others out, and does`);
  return ['concurrent_sessions_revoked'];
}

function checkAudit(config, since, expected, step) {
  if (expected.length === 0) {
    return;
  }
  if (!config.mysql.password) {
    step('audit rows not checked (MYSQL_PASSWORD not set)');
    return;
  }
  const sql = createSqlRunner(config.mysql);
  try {
    for (const content of expected) {
      const count = Number(sql.value(
        `SELECT COUNT(*) FROM log WHERE action = 'log in' AND content = ${sqlString(content)}`
        + ` AND dateTime >= ${sqlString(since)}`,
      ));
      assert(count > 0, `no ${content} audit row was written`);
    }
    step(`audit rows written: ${expected.join(', ')}`);
  } finally {
    sql.dispose();
  }
}

function mysqlNow(date) {
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} `
    + `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

if (require.main === module) {
  let browser;
  const contexts = [];
  runCheck({
    name: 'concurrent-session-policy',
    run: async () => {
      const config = readConfig();
      const { policy, max } = readPolicy();
      // One second back so a row written in the same second as the start still counts.
      const since = mysqlNow(new Date(Date.now() - 1000));
      const step = (message) => console.log(`  ok ${message}`);
      browser = await launchBrowser(config);

      await checkRouteGuards(browser, config, step);
      let audited = [];
      if (max > 0 && policy !== 'single') {
        audited = await checkLimit(browser, config, step, contexts, max, policy);
      } else if (policy === 'prompt') {
        audited = await checkPrompt(browser, config, step, contexts);
      } else if (policy === 'single') {
        audited = await checkSingle(browser, config, step, contexts);
      } else {
        await checkAllow(browser, config, step, contexts);
      }
      checkAudit(config, since, audited, step);
      try {
        assertStrictPage(recorder);
      } catch (error) {
        console.error(JSON.stringify(buildFailureDetails(recorder), null, 2));
        throw error;
      }
    },
    cleanup: async () => {
      for (const context of contexts) {
        await context.close().catch(() => {});
      }
      if (browser) {
        await browser.close().catch(() => {});
      }
    },
  });
}

module.exports = { readPolicy, mysqlNow };
