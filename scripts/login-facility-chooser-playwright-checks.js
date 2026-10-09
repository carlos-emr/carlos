#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * The multi-facility login: the facility chooser, what a login can reach before choosing, the audit row of the
 * choice, the per-facility banner message and the single-facility skip (coverage plan: login-facility-chooser).
 *
 * User path: login page > user name, password and PIN > Login. A provider who belongs to more than one active
 * facility (provider_facility, Facility.disabled = 0) is sent to /select_facility and holds no working session
 * until one facility button is pressed (Login2Action sets PENDING_FACILITY_SELECTION and LoginFilter turns every
 * other route back to the chooser); the pick writes a `log in` audit row naming the facility and opens the
 * Schedule (/provider/providercontrol), whose banner (/FacilityMessage?method=view, facility_message) shows the
 * messages of the chosen facility. A provider in exactly one facility is never asked: Login2Action sets that facility
 * and writes the same audit row itself.
 *
 * Asserted:
 *   1. The chooser appears with no click after the password and lists exactly the login's facilities: one button
 *      per facility, named and numbered as the facility is, and not a facility the login does not belong to.
 *   2. Before choosing, a typed Schedule URL and a typed chart URL each get an HTTP redirect to the chooser from the
 *      application, and a browser that follows it lands on the chooser (not the Schedule or the chart, not an error
 *      page, not a blank one). Logout (/logoutPage, which posts /logout) ends that session and audits it, after which
 *      the Schedule URL goes to the logout page and not to the chooser.
 *   3. The pick writes exactly one `log in`/login audit row whose contentId is facilityId=<id> for the chosen
 *      facility (with the client address), and the chooser stage before it wrote none. After the pick the same typed
 *      URLs are served (controls: they were turned back because the choice was pending, not because they are wrong).
 *   4. The Schedule banner shows the chosen facility's live message and no other: not the other facility's, not a
 *      facility the login is not in, not an expired message of its own facility. Both facilities are picked in turn.
 *   5. A one-facility login goes from the password to the Schedule without ever requesting /select_facility, audits
 *      its facility once, is served the chart URL and sees only its facility's banner message.
 *
 * Fixtures: two throwaway logins holding the seeded doctor role in the test login's programs (own provider rows, the
 * test login's credential; authzReadFixture), three facilities of the run
 * (Facility, named after the marker), provider_facility rows (two-facility login: A and B; one-facility login: B) and
 * four facility_message rows (A live, A expired, B live, C live), plus the run's owned patient for the chart URL.
 * Nothing clinic-wide is changed: no facility, property or flag of the install is read or written except to copy a
 * Facility row's configuration columns. authzReadFixture.cleanup() removes the messages, facilities, logins, their
 * provider_facility and audit rows by key and asserts each gone; the teardown step does it while the run is still going.
 * The facility cache (FacilityDaoImpl.findAll) is evicted by DAO writes, not by the SQL these fixtures use; the step after the
 * teardown reads Administration > Facilities to prove no run facility is left listed (when the cache was cold during the run).
 * The last step is the one pinned to finding 265 (the chooser page's own stylesheet and script are turned back by the
 * pending-facility gate); it only judges evidence gathered earlier, so every step before it has run when it fails.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow } = require('./lib/workflow-session');
const { authzReadFixture } = require('./lib/authz-read-fixture');
const { submitLoginForm } = require('./lib/throwaway-login-fixture');
const { probe } = require('./lib/authz-read-probe');
const { settleOperations } = require('./graceful-signal-cancellation');

/** The step the manifest pins to finding 265; one place, so the script and the manifest agree. */
const CHOOSER_ASSETS_STEP = 'the chooser page\'s own stylesheet and script are served to a login that has not chosen a facility';
const CHOOSER_HEADING = /Please select which facility you would like to currently work in/i;
const SCHEDULE_PATH = '/provider/providercontrol?displaymode=day&dboperation=searchappointmentday&viewall=1';
// LogAction.addLog persists on a background executor: a row is not there the moment the response is back, and a
// duplicate may be later still.
const SETTLE_MS = 2500;
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const pathOf = rawUrl => new URL(rawUrl).pathname;
const endsWith = (rawUrl, tail) => pathOf(rawUrl).endsWith(tail);

/**
 * The recorder minus what the chooser page's blocked stylesheet and script produce: the response of the pending-facility
 * redirect to /select_facility read as a script (text/html), the browser's two refusals of it, and the aborted
 * stylesheet request. Nothing else is removed, so any other problem on any of the pages still fails the step.
 */
function withoutChooserAssetDefect(recorder) {
  const chooser = rawUrl => { try { return pathOf(rawUrl).endsWith('/select_facility'); } catch (error) { return false; } };
  const refusal = /^Refused to (?:apply style|execute script) from '([^']*)' because its MIME type \('text\/html'\)/;
  return {
    ...recorder,
    badResponses: recorder.badResponses.filter(entry => !(entry.status === 200 && entry.reason === 'script served as text/html' && chooser(entry.url))),
    consoleIssues: recorder.consoleIssues.filter(entry => !(refusal.test(entry.text) && chooser(refusal.exec(entry.text)[1]))),
    requestFailures: recorder.requestFailures.filter(entry => !(entry.resourceType === 'stylesheet' && chooser(entry.url))),
  };
}

async function workflow(s) {
  const { sql, config, marker, provider } = s;
  const q = h.sqlString;
  const fixture = authzReadFixture({ sql, marker, provider, testUser: config.testUser });
  s.cleanup(() => fixture.cleanup());
  const credentials = login => ({ username: login.username, password: config.testPassword, pin: config.testPin });
  const url = appPath => h.appUrl(config.baseUrl, appPath);
  const browser = s.context.browser();
  // The pages of the throwaway logins report to a recorder of their own. The chooser page's stylesheet and script are
  // turned back by the pending-facility gate (the pinned last step judges that), so the session recorder that every
  // s.step() asserts would fail the first step that opens the chooser. The wrapper below asserts this recorder in
  // every step except the pinned one, minus exactly the entries that defect produces and nothing else.
  const pages = h.createRecorder();
  async function step(label, body, { pinned = false } = {}) {
    await s.step(label, async () => {
      await body();
      if (!pinned) h.assertStrictPage(withoutChooserAssetDefect(pages));
    });
  }

  // A browser context per sign-in: each one is its own session, and its pages are wired to the strict recorder.
  async function signIn(loginRow, label) {
    const context = await h.newContext(browser, config);
    context.setDefaultTimeout(20000);
    context.on('page', page => h.wireStrictPage(page, label, pages));
    s.cleanup(() => context.close().catch(() => {}));
    // Every document the context asks for, so "never saw the chooser" is a fact about requests, not about the last URL.
    const navigations = [];
    context.on('request', request => { if (request.isNavigationRequest()) navigations.push(pathOf(request.url())); });
    const result = await submitLoginForm(context, config, credentials(loginRow));
    return { context, navigations, ...result };
  }

  // What the page in front of the browser is: the chooser (heading plus one form per facility), the Schedule, or other.
  async function pageState(page) {
    await page.waitForLoadState('load', { timeout: 30000 }).catch(() => {});
    const body = (await page.locator('body').innerText({ timeout: 10000 }).catch(() => '')).replace(/\s+/g, ' ').trim();
    return {
      path: pathOf(page.url()),
      heading: CHOOSER_HEADING.test(body),
      choices: await page.locator('form[action$="/select_facility"]').evaluateAll(forms => forms.map(form => ({
        id: ((form.querySelector('input[name="selectedFacilityId"]') || {}).value || '').trim(),
        name: ((form.querySelector('button') || {}).innerText || '').trim(),
      }))).catch(() => []),
      schedule: await page.locator('#firstMenu #navlist').count() > 0,
      blank: body.length === 0,
      errorPage: /CARLOS has encountered an unexpected error|HTTP Status 5\d\d|Exception Report|Error Page/i.test(body),
    };
  }

  // A typed URL for a login that HAS chosen: asked through the context's request API, which follows the redirects the way
  // the browser would but loads no page, so no chart or Schedule script runs and none is aborted by the next step.
  async function servedAfterRedirects(context, appPath) {
    const response = await context.request.get(url(appPath), { timeout: 40000, failOnStatusCode: false });
    const body = await response.text().catch(() => '');
    return {
      status: response.status(), path: pathOf(response.url()), body,
      chooser: CHOOSER_HEADING.test(body), blank: body.trim().length === 0,
      errorPage: /CARLOS has encountered an unexpected error|HTTP Status 5\d\d|Exception Report/i.test(body),
    };
  }

  const logWhere = (providerNo, extra) => `provider_no=${q(providerNo)} AND ${extra}`;
  const logRows = where => sql.rows(`SELECT id,COALESCE(action,''),COALESCE(content,''),COALESCE(contentId,''),COALESCE(ip,'')
    FROM log WHERE ${where} ORDER BY id`).map(([id, action, content, contentId, ip]) => ({ id: Number(id), action, content, contentId, ip }));
  const logWatermark = () => Number(sql.value('SELECT COALESCE(MAX(id),0) FROM log'));
  // Wait for at least one row, then for the late duplicate the background executor might still add.
  async function settledLogRows(where) {
    const deadline = Date.now() + 15000;
    while (logRows(where).length < 1 && Date.now() < deadline) await sleep(200);
    await sleep(SETTLE_MS);
    return logRows(where);
  }
  const facilityRows = providerNo => logRows(logWhere(providerNo, "action='log in' AND contentId LIKE 'facilityId=%'"));

  // The text of the Schedule's facility banner once its AJAX load has filled it.
  async function bannerText(page) {
    await page.waitForLoadState('networkidle', { timeout: 30000 }).catch(() => {});
    await page.locator('#facility_message').waitFor({ state: 'attached', timeout: 15000 });
    await page.waitForFunction(() => ((document.querySelector('#facility_message') || {}).innerText || '').trim().length > 0,
      null, { timeout: 15000 }).catch(() => {});
    return ((await page.locator('#facility_message').innerText()) || '').replace(/\s+/g, ' ').trim();
  }

  // Press the facility's button on the chooser; resolves on the Schedule.
  async function pick(page, facility) {
    const button = page.getByRole('button', { name: facility.name, exact: true });
    h.assert(await button.count() === 1, `The chooser offers no single button for the facility ${facility.name.slice(-1)}`);
    await settleOperations([
      page.waitForURL(/providercontrol|appointment/i, { timeout: 30000 }),
      button.click(),
    ]);
    await page.waitForLoadState('load', { timeout: 30000 }).catch(() => {});
  }

  // ---- fixtures ---------------------------------------------------------------------------------------------------
  let multi; let single; let A; let B; let C;
  const text = {};
  await step('fixtures: a two-facility login, a one-facility login, a facility neither belongs to, and a banner message per facility', async () => {
    h.assert(/^[1-9]\d*$/.test(String(s.patient)), 'The run has no owned patient for the chart URL');
    A = fixture.addFacility('A');
    B = fixture.addFacility('B');
    C = fixture.addFacility('C');
    // The seeded doctor role in the test login's programs: the Schedule and the chart a clinician gets, so nothing the page
    // asks for is refused for want of a sec object (a narrower role's Schedule answers HTTP 500 to the tab-alert refresh:
    // finding 142), and every step below is about the facility.
    multi = fixture.addLogin('doctor');
    single = fixture.addLogin('doctor');
    fixture.joinTestLoginPrograms(multi);
    fixture.joinTestLoginPrograms(single);
    fixture.joinFacility(multi, A);
    fixture.joinFacility(multi, B);
    fixture.joinFacility(single, B);
    h.assert(JSON.stringify(fixture.facilityIdsOf(multi)) === JSON.stringify([A.id, B.id]), 'The two-facility login does not belong to exactly A and B');
    h.assert(JSON.stringify(fixture.facilityIdsOf(single)) === JSON.stringify([B.id]), 'The one-facility login does not belong to exactly B');
    text.A = `${marker}-MSG-A-LIVE`;
    text.Aold = `${marker}-MSG-A-OLD`;
    text.B = `${marker}-MSG-B-LIVE`;
    text.C = `${marker}-MSG-C-LIVE`;
    fixture.addFacilityMessage(A, text.A);
    fixture.addFacilityMessage(A, text.Aold, { expired: true });
    fixture.addFacilityMessage(B, text.B);
    fixture.addFacilityMessage(C, text.C);
    h.assert(sql.value(`SELECT COUNT(*) FROM Facility WHERE disabled=0 AND name IN (${[A, B, C].map(f => q(f.name)).join(',')})`) === '3',
      'The run\'s three facilities are not all enabled');
  });

  // ---- 1. the chooser ---------------------------------------------------------------------------------------------
  let first; // the two-facility login, held at the chooser
  const chooserAssets = [];
  await step('the two-facility login is sent to the facility chooser straight after its password, with no click in between', async () => {
    first = await signIn(multi, 'login-facility-chooser-first');
    h.assert(first.outcome === 'other' && endsWith(first.page.url(), '/select_facility'),
      `The two-facility login landed on ${first.landing} (${first.outcome}), not on the facility chooser`);
    const state = await pageState(first.page);
    h.assert(state.heading, 'The facility chooser page did not render its "select which facility" heading');
    h.assert(!state.schedule, 'The Schedule is showing for a login that has not chosen a facility');
    // Evidence for the pinned last step, gathered while the pending session is alive: what the chooser page's own head
    // links (stylesheets, scripts, icon) answer to the same session. Nothing is asserted here.
    const wanted = await first.page.evaluate(() => [
      ...document.querySelectorAll('link[rel~="stylesheet"][href], link[rel~="icon"][href]'),
      ...document.querySelectorAll('script[src]'),
    ].map(node => ({ kind: node.tagName === 'SCRIPT' ? 'script' : (/\bicon\b/.test(node.rel) ? 'icon' : 'stylesheet'), href: node.src || node.href })));
    for (const { kind, href } of wanted) {
      if (new URL(href).origin !== new URL(url('/')).origin) continue;
      const answer = await probe(first.context, href);
      chooserAssets.push({
        kind, path: pathOf(href), status: answer.status, type: answer.type || '',
        to: answer.location ? pathOf(new URL(answer.location, href).href) : '',
      });
    }
  });

  await step('the chooser lists exactly the login\'s facilities, one button each named and numbered as the facility is', async () => {
    const { choices } = await pageState(first.page);
    const byId = (a, b) => Number(a.id) - Number(b.id);
    const expected = [A, B].map(({ id, name }) => ({ id, name }));
    h.assert(JSON.stringify(choices.slice().sort(byId)) === JSON.stringify(expected),
      `The chooser lists ${choices.length} facilities (ids ${choices.map(choice => choice.id).join(',') || 'none'}); the login belongs to `
      + `exactly ${expected.length} (ids ${expected.map(choice => choice.id).join(',')}) and the install has a third it does not (id ${C.id})`);
    h.assert(!choices.some(choice => choice.id === C.id || choice.name === C.name), 'The chooser offers a facility the login does not belong to');
  });

  // ---- 2. typed URLs before choosing, and logout ---------------------------------------------------------------------
  // The request the application answers (never followed), then what the browser shows after following it.
  async function typedUrlResult(appPath) {
    const hop = await probe(first.context, url(appPath));
    const response = await first.page.goto(url(appPath), { waitUntil: 'load', timeout: 30000 });
    const state = await pageState(first.page);
    return { hop, finalStatus: response ? response.status() : 0, state };
  }
  function assertBackAtChooser(label, { hop, finalStatus, state }) {
    h.assert(hop.status === 302 && hop.fromApp && endsWith(new URL(hop.location || '/', url('/')).href, '/select_facility'),
      `The typed ${label} URL was answered HTTP ${hop.status}${hop.fromApp ? '' : ' by something other than the application'}${hop.location ? ` to ${pathOf(new URL(hop.location, url('/')).href)}` : ''} (${hop.lead || 'no body'}), not redirected to the chooser`);
    h.assert(!state.errorPage, `The typed ${label} URL ended on an error page`);
    h.assert(!state.blank, `The typed ${label} URL ended on a blank page`);
    h.assert(!state.schedule, `The typed ${label} URL served the Schedule to a login that has not chosen a facility`);
    h.assert(finalStatus === 200 && state.path.endsWith('/select_facility') && state.heading && state.choices.length === 2,
      `The typed ${label} URL ended on ${state.path} (HTTP ${finalStatus}), not on the chooser with its two facilities`);
  }

  await step('before a facility is chosen, a typed Schedule URL returns to the chooser', async () => {
    assertBackAtChooser('Schedule', await typedUrlResult(SCHEDULE_PATH));
  });

  const chartPath = () => `/encounter/IncomingEncounter?demographicNo=${s.patient}&providerNo=${multi.providerNo}&curProviderNo=`;
  await step('before a facility is chosen, a typed chart URL returns to the chooser', async () => {
    assertBackAtChooser('chart', await typedUrlResult(chartPath()));
  });

  await step('before a facility is chosen, nothing the login did (password, chooser, typed URLs) audited a facility', async () => {
    await sleep(SETTLE_MS);
    const rows = facilityRows(multi.providerNo);
    h.assert(rows.length === 0, `The chooser stage wrote ${rows.length} facilityId audit row(s) for a facility nobody had chosen`);
  });

  await step('Logout from the chooser ends the session and audits it; the Schedule URL then goes to the logout page, not the chooser', async () => {
    const before = logWatermark();
    await settleOperations([
      first.page.waitForURL(u => /\/index$|\/$/.test(new URL(String(u)).pathname), { timeout: 30000 }),
      first.page.goto(url('/logoutPage'), { waitUntil: 'domcontentloaded', timeout: 30000 }),
    ]);
    await first.page.locator('#username').waitFor({ state: 'attached', timeout: 15000 });
    const hop = await probe(first.context, url(SCHEDULE_PATH));
    h.assert(hop.status === 302 && hop.fromApp && endsWith(new URL(hop.location || '/', url('/')).href, '/logoutPage'),
      `After logout the Schedule URL was answered HTTP ${hop.status}${hop.location ? ` to ${pathOf(new URL(hop.location, url('/')).href)}` : ''}, not sent to the logout page`);
    const chooser = await probe(first.context, url('/select_facility'));
    h.assert(chooser.status === 302 && endsWith(new URL(chooser.location || '/', url('/')).href, '/logoutPage'),
      `After logout the chooser URL was answered HTTP ${chooser.status}, not sent to the logout page`);
    const outs = await settledLogRows(logWhere(multi.providerNo, `action='log out' AND id>${before}`));
    h.assert(outs.length === 1 && outs[0].content === 'login' && Boolean(outs[0].ip),
      `Logging out from the chooser wrote ${outs.length} log out row(s) with the client address, expected exactly one`);
  });

  // ---- 3. the pick ------------------------------------------------------------------------------------------------
  let second; // the two-facility login after choosing A
  let pickMark;
  await step('signing in again and pressing facility A\'s button opens the Schedule', async () => {
    second = await signIn(multi, 'login-facility-chooser-second');
    h.assert(second.outcome === 'other' && endsWith(second.page.url(), '/select_facility'),
      `The second sign-in landed on ${second.landing}, not on the chooser`);
    pickMark = logWatermark();
    await pick(second.page, A);
    const state = await pageState(second.page);
    h.assert(state.path.endsWith('/provider/providercontrol') && state.schedule,
      `Pressing the facility button ended on ${state.path}, not on the Schedule`);
  });

  await step('the pick wrote exactly one log in row naming the chosen facility, with the client address, and none for the other', async () => {
    const rows = await settledLogRows(logWhere(multi.providerNo, `action='log in' AND contentId LIKE 'facilityId=%' AND id>${pickMark}`));
    h.assert(rows.length === 1, `The pick wrote ${rows.length} facilityId audit rows, expected exactly 1`);
    h.assert(rows[0].contentId === `facilityId=${A.id}` && rows[0].content === 'login',
      `The pick audited ${rows[0].contentId} (${rows[0].content}), not facilityId=${A.id} (login)`);
    h.assert(Boolean(rows[0].ip), 'The pick\'s audit row carries no client address');
    h.assert(facilityRows(multi.providerNo).length === 1, 'The login has more than the one facilityId audit row after a single pick');
  });

  await step('controls: once a facility is chosen the same typed Schedule and chart URLs are served, so the redirects above were the pending choice', async () => {
    const schedule = await servedAfterRedirects(second.context, SCHEDULE_PATH);
    h.assert(schedule.status === 200 && schedule.path.endsWith('/provider/providercontrol') && schedule.body.includes('id="firstMenu"') && !schedule.chooser,
      `After the pick the typed Schedule URL answered HTTP ${schedule.status} from ${schedule.path}, not the Schedule`);
    const chart = await servedAfterRedirects(second.context, chartPath());
    h.assert(chart.status === 200 && chart.path.endsWith('/CaseManagementEntry') && !chart.chooser && !chart.errorPage && !chart.blank,
      `After the pick the typed chart URL answered HTTP ${chart.status} from ${chart.path}, not the chart`);
    // The files the chooser page links are real and served to a login that has chosen (the pinned last step asks why they
    // are not served before it): without this control a missing file would read as the pending gate.
    const linked = chooserAssets.filter(asset => asset.kind !== 'icon');
    h.assert(linked.length >= 2, 'The chooser page links no stylesheet and script to compare');
    for (const asset of linked) {
      const served = await probe(second.context, new URL(asset.path, url('/')).href);
      h.assert(served.status === 200 && served.length > 0, `After the pick ${asset.path} answered HTTP ${served.status}, so it is not a file the chooser can be blamed for`);
    }
  });

  // ---- 4. the banner ----------------------------------------------------------------------------------------------
  await step('the banner of the chosen facility A shows its live message and no other: not B\'s, not C\'s, not A\'s expired one', async () => {
    const banner = await bannerText(second.page);
    h.assert(banner.includes(text.A), 'The banner of the chosen facility does not show its live message');
    h.assert(!banner.includes(text.B), 'The banner of facility A shows facility B\'s message');
    h.assert(!banner.includes(text.C), 'The banner of facility A shows the message of a facility the login does not belong to');
    h.assert(!banner.includes(text.Aold), 'The banner of facility A shows its expired message');
  });

  await step('choosing the other facility B shows B\'s message and not A\'s, and audits B once', async () => {
    const third = await signIn(multi, 'login-facility-chooser-third');
    h.assert(third.outcome === 'other' && endsWith(third.page.url(), '/select_facility'), `The third sign-in landed on ${third.landing}, not on the chooser`);
    const mark = logWatermark();
    await pick(third.page, B);
    const banner = await bannerText(third.page);
    h.assert(banner.includes(text.B), 'The banner of facility B does not show its live message');
    h.assert(!banner.includes(text.A) && !banner.includes(text.Aold), 'The banner of facility B shows a message of facility A');
    h.assert(!banner.includes(text.C), 'The banner of facility B shows the message of a facility the login does not belong to');
    const rows = await settledLogRows(logWhere(multi.providerNo, `action='log in' AND contentId LIKE 'facilityId=%' AND id>${mark}`));
    h.assert(rows.length === 1 && rows[0].contentId === `facilityId=${B.id}`,
      `Choosing facility B wrote ${rows.length} facilityId row(s) (${rows.map(row => row.contentId).join(', ')}), expected exactly facilityId=${B.id}`);
  });

  // ---- 5. the single-facility skip --------------------------------------------------------------------------------
  let only;
  await step('a one-facility login goes from its password to the Schedule and never requests the chooser', async () => {
    const mark = logWatermark();
    only = await signIn(single, 'login-facility-chooser-single');
    only.mark = mark;
    h.assert(only.outcome === 'schedule' && endsWith(only.page.url(), '/provider/providercontrol'),
      `The one-facility login landed on ${only.landing} (${only.outcome}), not on the Schedule`);
    h.assert(!only.navigations.some(path => path.endsWith('/select_facility')),
      'The one-facility login was sent through the facility chooser');
    h.assert((await pageState(only.page)).schedule, 'The one-facility login\'s Schedule did not render its top bar');
  });

  await step('the one-facility login audits its facility once at sign-in and is served the chart URL', async () => {
    const rows = await settledLogRows(logWhere(single.providerNo, `action='log in' AND contentId LIKE 'facilityId=%' AND id>${only.mark}`));
    h.assert(rows.length === 1 && rows[0].contentId === `facilityId=${B.id}` && rows[0].content === 'login' && Boolean(rows[0].ip),
      `The one-facility login wrote ${rows.length} facilityId row(s) (${rows.map(row => row.contentId).join(', ')}), expected exactly facilityId=${B.id}`);
    const chart = await servedAfterRedirects(only.context, `/encounter/IncomingEncounter?demographicNo=${s.patient}&providerNo=${single.providerNo}&curProviderNo=`);
    h.assert(chart.status === 200 && chart.path.endsWith('/CaseManagementEntry') && !chart.chooser && !chart.errorPage && !chart.blank,
      `The one-facility login's typed chart URL answered HTTP ${chart.status} from ${chart.path}, not the chart`);
  });

  await step('the one-facility login\'s banner shows its own facility\'s message and no other', async () => {
    const banner = await bannerText(only.page);
    h.assert(banner.includes(text.B), 'The one-facility login\'s banner does not show its facility\'s live message');
    h.assert(!banner.includes(text.A) && !banner.includes(text.Aold) && !banner.includes(text.C),
      'The one-facility login\'s banner shows a message of a facility it does not belong to');
  });

  // ---- teardown ---------------------------------------------------------------------------------------------------
  await step('every owned facility, message, login and audit row is removed', async () => {
    for (const context of [first, second, only].map(session => session && session.context)) {
      if (context) await context.close().catch(() => {});
    }
    fixture.cleanup();
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM Facility WHERE description=${q(marker)} OR name LIKE ${q(`${marker}%`)})
      + (SELECT COUNT(*) FROM facility_message WHERE message LIKE ${q(`${marker}%`)})
      + (SELECT COUNT(*) FROM provider WHERE last_name=${q(marker)})`) === '0', 'Owned facility, message or provider rows remain');
    h.assert(sql.value(`SELECT COUNT(*) FROM provider_facility WHERE facility_id IN (${[A, B, C].map(f => q(f.id)).join(',')})`) === '0',
      'provider_facility rows of the run remain');
  });

  // The facility cache (FacilityDaoImpl.findAll, 15 minutes) is evicted by the DAO's writes, never by SQL. A flow of this run
  // that filled it while the run's facilities existed would leave them listed here after their rows are gone. It can only say
  // so when the cache was cold during the run (a warm cache holds a list from before it, and the run cannot change that).
  await step('no stale facility is left in the facility cache: Administration > Facilities lists none of the run\'s facilities', async () => {
    const list = await servedAfterRedirects(s.context, '/FacilityManager');
    h.assert(list.status === 200 && /FacilityManager\?method=edit&(?:amp;)?id=\d+/.test(list.body),
      `Administration > Facilities answered HTTP ${list.status} from ${list.path} without its facility list, so the cache could not be read`);
    h.assert(!list.body.includes(marker), 'Administration > Facilities still lists a facility of this run after its rows were removed (stale facility cache)');
  });

  // ---- pinned (finding 265) ---------------------------------------------------------------------------------------
  // LAST, so that a script that stops at its first failing step has judged everything above first. It holds only the
  // defect's assertion, over the evidence the chooser step gathered: the chooser page links a stylesheet and a script
  // of the application, and a login that has not chosen a facility must be served both, or the page renders unstyled.
  await step(CHOOSER_ASSETS_STEP, async () => {
    h.assert(chooserAssets.some(asset => asset.kind === 'stylesheet') && chooserAssets.some(asset => asset.kind === 'script'),
      'The chooser page links no stylesheet or no script of the application, so there is nothing to judge');
    const wrong = chooserAssets.filter(asset => asset.status !== 200
      || (asset.kind === 'stylesheet' && !/^text\/css/i.test(asset.type))
      || (asset.kind === 'script' && !/javascript|ecmascript/i.test(asset.type)));
    h.assert(wrong.length === 0, `The facility chooser's own ${wrong.map(asset => asset.kind).join(' and ')} are not served to a login that has `
      + `not chosen a facility: ${wrong.map(asset => `${asset.path} -> HTTP ${asset.status}${asset.to ? ` to ${asset.to}` : ''} (${asset.type || 'no type'})`).join('; ')}`);
  }, { pinned: true });
}

if (require.main === module) runWorkflow('login-facility-chooser', workflow, { openMaster: false });
module.exports = { workflow, CHOOSER_ASSETS_STEP };
