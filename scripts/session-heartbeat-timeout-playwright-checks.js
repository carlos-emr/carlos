#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Session loss while working: coverage plan §2.2 session-heartbeat-timeout.
 *
 * User path: Schedule ▸ Master Record ▸ E-Chart open for an owned patient; the server
 * session then ends out of band (a second browser holding the same session cookie
 * clicks Logout, so no in-browser broadcast reaches these windows) and each window's
 * own status/SessionHeartbeat poll must notice. Second session: Schedule ▸ Tickler after
 * the session ended. Third: Schedule ▸ Tickler ▸ New Tickler ▸ Suggested Text ▸ Save
 * (closenreload). Fourth, a read-only tickler login: Schedule ▸ Tickler ▸ Complete
 * Selected (securityError).
 *
 * Asserted: the chart's heartbeat reports a live session, then the first heartbeat
 * after the session ends reports it expired, the chart closes itself and the schedule
 * returns to the login form; the chart URL serves no chart HTML without a session; a
 * Tickler click after session loss lands on the login form with no tickler content;
 * the suggested-text save answers closenreload (no PHI), closes the editor, reloads the
 * New Tickler page with the new text and writes the row; a read-only user's Complete
 * lands on securityError (403, no PHI) and leaves the tickler untouched.
 *
 * Fixtures: the owned synthetic patient, one marker tickler on it, one marker
 * suggested-text row (plus any row the save writes on this run's behalf), and a
 * throwaway login holding a marker role with only _appointment, _tickler and _msg r; all
 * removed and verified by cleanup. encounter/ViewTimeOut has no UI entry and is not
 * driven.
 */
const h = require('./lib/playwright-harness');
const { clickOpensPopup, clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { bundleMessage, throwawayLoginFixture } = require('./lib/throwaway-login-fixture');
const { waitForTicklerTable } = require('./tickler-forward-filters-playwright-checks');
const { settleOperations } = require('./graceful-signal-cancellation');

const TIMEOUT = 20000;
// LogoutBroadcastFilter polls every 60 s from page load; allow one full period plus latency.
const HEARTBEAT_WAIT = 75000;
const HEARTBEAT = '/status/SessionHeartbeat';

/** Record every heartbeat a context's pages send, with the page and the parsed answer. */
function trackHeartbeats(ctx) {
  const seen = [];
  ctx.on('response', response => {
    if (!h.pathOnly(response.url()).endsWith(HEARTBEAT)) return;
    let page = null;
    try { page = response.frame().page(); } catch (error) { page = null; }
    seen.push({ page, at: Date.now(), status: response.status(), body: response.json().catch(() => null) });
  });
  return {
    async next({ after = 0, page = null, timeout = HEARTBEAT_WAIT } = {}) {
      const deadline = Date.now() + timeout;
      for (;;) {
        const entry = seen.find(item => item.at > after && (!page || item.page === page));
        if (entry) return { ...entry, body: await entry.body };
        h.assert(Date.now() < deadline, `No session heartbeat was sent within ${timeout} ms`);
        await new Promise(resolve => setTimeout(resolve, 250));
      }
    },
  };
}

/** The tickler list's own DataTables search box (sent as search[value]). */
async function searchTicklers(page, needle) {
  const [response] = await settleOperations([
    page.waitForResponse(r => h.pathOnly(r.url()).endsWith('/tickler/ListTicklers')
      && new URL(r.url()).searchParams.get('search[value]') === needle, { timeout: 30000 }),
    page.locator('#ticklerResults_filter input[type="search"]').fill(needle),
  ]);
  h.assert(response.status() === 200, `Searching the tickler list answered HTTP ${response.status()}`);
  await waitForTicklerTable(page);
}

async function workflow(s) {
  const { sql, context, config, recorder, patient, provider, marker } = s;
  const contexts = [];
  s.cleanup(async () => { for (const ctx of contexts) await ctx.close().catch(() => {}); });
  async function freshContext(label) {
    const ctx = await h.newContext(context.browser(), config);
    contexts.push(ctx);
    ctx.on('page', p => h.wireStrictPage(p, label, recorder));
    return ctx;
  }
  async function sessionValid(ctx) {
    const response = await ctx.request.get(h.appUrl(config.baseUrl, HEARTBEAT));
    h.assert(response.status() === 200, `The session heartbeat answered HTTP ${response.status()}`);
    return (await response.json()).valid;
  }
  async function onLoginForm(page) {
    await page.waitForURL(/\/index(?:$|\?)/, { timeout: TIMEOUT });
    await page.locator('#username').waitFor({ state: 'visible', timeout: TIMEOUT });
    const text = await page.locator('body').innerText();
    h.assert(!text.includes(marker), 'The login page carried the patient fixture\'s name');
  }
  // End the server session behind these windows' backs: another browser holding the
  // same session cookie clicks Logout on its schedule. Browser contexts share no
  // BroadcastChannel or storage, so only the heartbeat can tell the original windows.
  async function endSessionElsewhere(source) {
    const ctx = await freshContext('session-ender');
    await ctx.addCookies(await source.cookies());
    const page = await ctx.newPage();
    await h.gotoApp(page, config.baseUrl, '/provider/providercontrol');
    await page.locator('#logoutButton').waitFor({ timeout: TIMEOUT });
    // Let the day sheet's own start-up requests finish so Logout aborts none of them.
    await page.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
    await page.locator('#logoutButton').click();
    await onLoginForm(page);
    h.assert(await sessionValid(source) === false, 'Logging out elsewhere did not end the shared session');
    await ctx.close();
  }

  // ---- fixtures ------------------------------------------------------------
  // The read-only role also holds _msg r: without it the schedule's message-count
  // refresh (provider/ViewTabAlertsRefresh?id=oscar_new_msg) answers HTTP 500 -- an
  // application defect reported separately, not what this check is about.
  const role = `FAKEPW${marker.slice(-16)}`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM secObjPrivilege WHERE roleUserGroup=${h.sqlString(role)};
      DELETE FROM secUserRole WHERE role_name=${h.sqlString(role)};
      DELETE FROM secRole WHERE role_name=${h.sqlString(role)} AND description=${h.sqlString(marker)}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=${h.sqlString(role)})
      + (SELECT COUNT(*) FROM secRole WHERE role_name=${h.sqlString(role)})`) === '0', 'The marker role was not removed');
  });
  const reader = throwawayLoginFixture({ sql, marker, provider, testUser: config.testUser });
  s.cleanup(() => reader.cleanup());
  const ticklerMessage = `${marker} read-only tickler`;
  const ownedTickler = `demographic_no=${patient} AND message=${h.sqlString(ticklerMessage)}`;
  s.cleanup(() => {
    const ids = sql.rows(`SELECT tickler_no FROM tickler WHERE ${ownedTickler}`).map(([id]) => id);
    h.assert(ids.every(id => /^[1-9]\d*$/.test(id)), 'Owned tickler id is invalid');
    if (ids.length) {
      sql.execute(`DELETE FROM tickler_update WHERE tickler_no IN (${ids.join(',')});
        DELETE FROM tickler_comments WHERE tickler_no IN (${ids.join(',')});
        DELETE FROM tickler WHERE ${ownedTickler} AND tickler_no IN (${ids.join(',')})`);
    }
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler WHERE ${ownedTickler}`) === '0', 'The owned tickler was not removed');
  });
  const suggestion = `${marker} suggestion`;
  const suggestionsBefore = sql.value('SELECT COALESCE(MAX(id),0) FROM tickler_text_suggest');
  // Rows the editor save writes for this run: the marker text, and any blank row it
  // creates on this provider's behalf (asserted against in the last step).
  const ownedSuggestions = `id>${suggestionsBefore} AND creator=${h.sqlString(provider)} AND suggested_text IN (${h.sqlString(suggestion)},'')`;
  s.cleanup(() => {
    sql.execute(`DELETE FROM tickler_text_suggest WHERE ${ownedSuggestions}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler_text_suggest WHERE ${ownedSuggestions}`) === '0', 'Owned suggested text was not removed');
  });
  reader.create();
  const readerNo = h.sqlString(reader.providerNo);
  sql.execute(`DELETE FROM secUserRole WHERE provider_no=${readerNo};
    INSERT INTO secRole (role_name,description) VALUES (${h.sqlString(role)},${h.sqlString(marker)});
    INSERT INTO secObjPrivilege (roleUserGroup,objectName,privilege,priority,provider_no)
      VALUES (${h.sqlString(role)},'_appointment','r',0,${h.sqlString(provider)}),(${h.sqlString(role)},'_tickler','r',0,${h.sqlString(provider)}),
        (${h.sqlString(role)},'_msg','r',0,${h.sqlString(provider)});
    INSERT INTO secUserRole (provider_no,role_name,orgcd,activeyn,lastUpdateDate) VALUES (${readerNo},${h.sqlString(role)},'R0000001',1,NOW())`);
  const ticklerNo = sql.value(`INSERT INTO tickler (demographic_no,message,status,update_date,service_date,creator,priority,task_assigned_to)
    VALUES (${patient},${h.sqlString(ticklerMessage)},'A',NOW(),DATE_SUB(CURDATE(),INTERVAL 1 DAY),${h.sqlString(provider)},'Normal',${readerNo});
    SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(ticklerNo), 'The read-only tickler fixture was not created');

  // ---- session loss with the chart open -------------------------------------
  const heartbeats = trackHeartbeats(context);
  const chart = await s.chart();
  const chartUrl = chart.url();

  await s.step('the open chart\'s heartbeat reports a live session', async () => {
    const beat = await heartbeats.next({ page: chart });
    h.assert(beat.status === 200 && beat.body && beat.body.valid === true, 'The chart\'s heartbeat did not report a live session');
  });

  let ended;
  await s.step('ending the session elsewhere leaves no chart HTML for the chart URL', async () => {
    await endSessionElsewhere(context);
    ended = Date.now();
    const response = await context.request.get(chartUrl, { maxRedirects: 0 });
    const body = await response.text();
    h.assert(response.status() === 302 && /\/logoutPage$/.test(new URL(response.headers().location, config.baseUrl).pathname),
      `The chart URL answered HTTP ${response.status()} without a session instead of redirecting to the login`);
    h.assert(!body.includes(marker) && !/casemgmt|encounter/i.test(body), 'The refused chart request carried chart content');
  });

  await s.step('the next heartbeat reports the expiry; the chart closes itself and the schedule returns to login', async () => {
    const failuresBefore = recorder.requestFailures.length;
    const chartClosed = chart.isClosed() ? Promise.resolve() : chart.waitForEvent('close', { timeout: HEARTBEAT_WAIT + TIMEOUT });
    const beat = await heartbeats.next({ after: ended });
    h.assert(beat.status === 200 && beat.body && beat.body.valid === false, 'The first heartbeat after the session ended did not report it expired');
    await chartClosed;
    // The chart releases its note lock with a sendBeacon on pagehide, which Chromium aborts
    // as the window closes. Consume only that ping from this close; all else stays strict.
    const added = recorder.requestFailures.splice(failuresBefore);
    recorder.requestFailures.push(...added.filter(entry => !(entry.resourceType === 'ping'
      && entry.errorText === 'net::ERR_ABORTED' && h.pathOnly(entry.url).endsWith('/CaseManagementEntry'))));
    await onLoginForm(s.schedule);
    h.assert(await s.schedule.locator('a.adhour').count() === 0, 'The schedule still showed the day sheet after session loss');
  });

  await s.step('after session loss a click on Tickler lands on the login form with no tickler content', async () => {
    const ctx = await freshContext('expired-tickler');
    const ctxBeats = trackHeartbeats(ctx);
    const schedule = await h.login(ctx, config, recorder, { label: 'expired-schedule' });
    // Act straight after the schedule's own heartbeat so it cannot notice first.
    await ctxBeats.next({ page: schedule });
    await endSessionElsewhere(ctx);
    const { page } = await clickOpensPopupOrNavigates(schedule, schedule.locator('a:has(#oscar_new_tickler)').first(),
      { context: ctx, recorder, label: 'expired-tickler', timeout: TIMEOUT });
    await onLoginForm(page);
    h.assert(await page.locator('#ticklerResults').count() === 0, 'Tickler content was served without a session');
    await ctx.close();
  });

  // ---- closenreload ----------------------------------------------------------
  await s.step('Suggested Text save answers closenreload: editor closes, New Tickler reloads with the text, row written', async () => {
    const ctx = await freshContext('tickler-suggest');
    const schedule = await h.login(ctx, config, recorder, { label: 'tickler-suggest' });
    const { page: list } = await clickOpensPopupOrNavigates(schedule, schedule.locator('a:has(#oscar_new_tickler)').first(),
      { context: ctx, recorder, label: 'tickler-suggest-list', timeout: TIMEOUT });
    await waitForTicklerTable(list);
    const add = await clickOpensPopup(list, list.locator(`input[type="button"][value="${bundleMessage('tickler.ticklerMain.btnAddTickler', 'New Tickler')}"]`),
      { context: ctx, recorder, label: 'tickler-suggest-add', timeout: TIMEOUT });
    const editor = await clickOpensPopup(add, add.locator('a[onclick*="ViewTicklerSuggestedText"]'),
      { context: ctx, recorder, label: 'tickler-suggest-editor', timeout: TIMEOUT });
    await editor.locator('#newTextSuggest').fill(suggestion);
    await editor.locator('input[name="addNewTextSuggest"]').click();
    h.assert(await editor.locator('select[name="activeText"] option', { hasText: suggestion }).count() === 1, 'Add did not list the new text');
    // Pass the save through unchanged, keeping its body: closenreload closes the window at once.
    let saved;
    await ctx.route('**/tickler/EditTicklerTextSuggest', async route => {
      const response = await route.fetch();
      saved = { status: response.status(), body: await response.text() };
      await route.fulfill({ response, body: saved.body });
    });
    const reloaded = add.waitForEvent('load', { timeout: TIMEOUT });
    await settleOperations([editor.waitForEvent('close', { timeout: TIMEOUT }), editor.locator('input[name="saveTextChanges"]').click()]);
    await ctx.unroute('**/tickler/EditTicklerTextSuggest');
    h.assert(saved && saved.status === 200 && saved.body.includes('Closing Window') && !saved.body.includes(marker),
      'The save did not answer the closenreload page, or it echoed what was typed');
    await reloaded;
    await add.locator('select[name="suggestedText"] option', { hasText: suggestion }).waitFor({ state: 'attached', timeout: TIMEOUT });
    h.assert(sql.value(`SELECT COUNT(*) FROM tickler_text_suggest WHERE id>${suggestionsBefore} AND suggested_text=${h.sqlString(suggestion)}
      AND active=1 AND creator=${h.sqlString(provider)}`) === '1', 'The new suggested text was not written once, active, by this provider');
    await ctx.close();
  });

  // ---- securityError -------------------------------------------------------
  await s.step('a read-only tickler user\'s Complete lands on securityError (403, no PHI) and changes nothing', async () => {
    const ctx = await freshContext('tickler-readonly');
    const schedule = await h.login(ctx, { ...config, testUser: reader.username }, recorder, { label: 'tickler-readonly' });
    const { page: list } = await clickOpensPopupOrNavigates(schedule, schedule.locator('a:has(#oscar_new_tickler)').first(),
      { context: ctx, recorder, label: 'tickler-readonly-list', timeout: TIMEOUT });
    await waitForTicklerTable(list);
    await searchTicklers(list, marker);
    await list.locator(`#ticklerResults input[name="checkbox"][value="${ticklerNo}"]`).check();
    const since = { responses: recorder.badResponses.length, console: recorder.consoleIssues.length };
    await settleOperations([
      list.waitForURL(/\/securityError\?type=_tickler$/, { timeout: TIMEOUT }),
      list.locator(`input[type="button"][value="${bundleMessage('tickler.ticklerMain.btnComplete', 'Complete Selected')}"]`).click(),
    ]);
    const added = recorder.badResponses.slice(since.responses);
    h.assert(added.length === 1 && added[0].status === 403 && added[0].method === 'GET'
      && h.pathOnly(added[0].url).endsWith('/securityError'), 'The refusal did not answer exactly one 403 security page');
    recorder.badResponses.splice(since.responses, 1);
    for (let i = recorder.consoleIssues.length - 1; i >= since.console; i--) {
      const entry = recorder.consoleIssues[i];
      if (/\/securityError\?/.test(entry.location.url || '') && entry.text.includes('status of 403')) recorder.consoleIssues.splice(i, 1);
    }
    const text = await list.locator('body').innerText();
    h.assert(text.includes('Security Exception') && text.includes('Object:_tickler'), 'The security page did not name the refused object');
    h.assert(!text.includes(marker) && !text.includes(String(patient)), 'The security page carried patient or tickler details');
    h.assert(sql.value(`SELECT CONCAT(status,'|',(SELECT COUNT(*) FROM tickler_update WHERE tickler_no=${ticklerNo}))
      FROM tickler WHERE tickler_no=${ticklerNo}`) === 'A|0', 'The refused Complete changed the tickler');
    await ctx.close();
  });

  await s.step('the Suggested Text save wrote no blank suggestion', async () => {
    await expectValue(sql, `SELECT COUNT(*) FROM tickler_text_suggest WHERE id>${suggestionsBefore} AND creator=${h.sqlString(provider)}
      AND suggested_text=''`, '0', 'Saving the Suggested Text editor with an empty Inactive list wrote a blank inactive suggestion row');
  });
}

if (require.main === module) runWorkflow('session-heartbeat-timeout', workflow, { openPatient: true });
module.exports = { workflow };
