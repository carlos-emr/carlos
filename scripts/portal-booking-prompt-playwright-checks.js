/* SPDX-License-Identifier: GPL-2.0-or-later */
/* Browser checks use the actual shared panel markup/JS with synthetic API fixtures.
 * Java tests verify action access/account boundaries; targeted JSPC verifies both host views.
 * Run: HEAVY_SLOTS=1 heavy node scripts/portal-booking-prompt-playwright-checks.js
 */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const { chromium } = require('playwright');
const root = path.join(__dirname, '..');
const labels = Object.fromEntries(fs.readFileSync(path.join(root, 'src/main/resources/oscarResources_en.properties'), 'utf8')
  .split('\n').filter(line => line.startsWith('portal.booking.')).map(line => {
    const split = line.indexOf('='); return [line.slice(0, split), line.slice(split + 1)];
  }));
const escape = value => value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('"', '&quot;');
function markup(appointment, readOnly = false, lateCsrf = false, rejectCsrf = false) {
  let html = fs.readFileSync(path.join(root, 'src/main/webapp/WEB-INF/jsp/demographic/portalBookingPrompt.jsp'), 'utf8');
  html = html.slice(html.indexOf('<section'), html.indexOf('</section>') + 10);
  if (readOnly) {
    html = html.replace(/<% if \(portalMayCreate\) \{ %>[\s\S]*?<% } %>/, '')
      .replace(/<% if \(portalMayWrite\) \{ %>[\s\S]*?<% } %>/, '');
  }
  html = html.replace(/<fmt:message key="([^"]+)" var="[^"]+"\/>/g, '')
    .replace(/\$\{carlos:forHtmlAttribute\(portalBookingCloseLabel\)\}/, escape(labels['portal.booking.close']))
    .replace(/<fmt:message key="([^"]+)"\/>/g, (_, key) => escape(labels[key]))
    .replace(/data-actor="[^\n]*"/, 'data-actor="999998"')
    .replace(/data-patient="[^"]*"/, 'data-patient="123"')
    .replace(/data-patient-input="[^\n]*"/, `data-patient-input="${appointment ? '#demographic_no' : ''}"`)
    .replace(/data-endpoint="[^\n]*"/, 'data-endpoint="/demographic/portalBookingPrompt"')
    .replace(/<%[\s\S]*?%>/g, '');
  // lateCsrf: like csrf-token.jspf, the token arrives after DOMContentLoaded, and the panel script is
  // deferred as on the real pages, so it runs before that.
  // rejectCsrf: the bootstrap's fetch fails, but another form on the page already carries the token.
  const csrf = rejectCsrf
    ? '<input type="hidden" name="CSRF-TOKEN" value=""><form method="post"><input type="hidden" name="CSRF-TOKEN" value="synthetic-csrf"></form>'
      + '<script>window.csrfTokenReady = null; document.addEventListener("DOMContentLoaded", function () {'
      + 'window.csrfTokenReady = Promise.reject(new Error("fetch failed")); window.csrfTokenReady.catch(function () {}); });</script>'
    : lateCsrf
    ? '<input type="hidden" name="CSRF-TOKEN" value=""><script>window.csrfTokenReady = null;'
      + 'document.addEventListener("DOMContentLoaded", function () { window.csrfTokenReady = new Promise(function (resolve) {'
      + 'setTimeout(function () { document.querySelector(\'input[name="CSRF-TOKEN"]\').value = "synthetic-csrf"; resolve(); }, 300); }); });'
      + '</script>'
    : '<input type="hidden" name="CSRF-TOKEN" value="synthetic-csrf">';
  return '<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">'
    + '<link rel="stylesheet" href="/panel.css">' + csrf
    + (appointment ? '<input id="demographic_no" value="123" readonly><input id="keyword" value="Synthetic Patient">' : '')
    + html + (lateCsrf || rejectCsrf ? '<script defer src="/panel.js"></script>' : '<script src="/panel.js"></script>');
}
function prompt(id = 7, state = 'sent') {
  return { id, state, urgency: 'routine', appointmentType: 'follow_up', createdAt: '2026-10-01T12:00:00Z',
    readAt: state === 'read' ? '2026-10-01T13:00:00Z' : null };
}
async function main() {
  let active = true, readOnly = false, outage = false, loseCreate = false, loseWithdraw = false;
  let malformed = false, refuseCreate = false;
  let prompts = [], notifications = 0, calls = [];
  const operations = new Map();
  const server = http.createServer(async (req, res) => {
    if (req.url === '/panel.js' || req.url === '/panel.css') {
      const type = req.url.endsWith('.js') ? 'js' : 'css';
      res.setHeader('Content-Type', type === 'js' ? 'text/javascript' : 'text/css');
      res.end(fs.readFileSync(path.join(root, `src/main/webapp/${type}/portalBookingPrompt.${type}`))); return;
    }
    if (['/master', '/appointment', '/master-late-csrf', '/master-reject-csrf'].includes(req.url)) {
      res.setHeader('Content-Type', 'text/html');
      res.end(markup(req.url === '/appointment', readOnly, req.url === '/master-late-csrf', req.url === '/master-reject-csrf')); return;
    }
    if (req.url !== '/demographic/portalBookingPrompt') { res.writeHead(404).end(); return; }
    let raw = ''; for await (const chunk of req) { raw += chunk; }
    const params = Object.fromEntries(new URLSearchParams(raw)); calls.push(params);
    assert.equal(req.method, 'POST'); assert.equal(req.headers['csrf-token'], 'synthetic-csrf');
    assert.equal(params.demographicNo, '123');
    res.setHeader('Content-Type', 'application/json');
    if (outage) { res.writeHead(503).end('{"ok":false}'); return; }
    let body;
    if (params.method === 'panel') {
      body = { ok: true, accountActive: active, mayCreate: !readOnly, mayWithdraw: !readOnly, prompts };
      if (malformed) { delete body.accountActive; }
    } else if (params.method === 'create') {
      if (refuseCreate) { res.writeHead(404).end('{"ok":false,"code":"portal_account_inactive"}'); return; }
      const created = !operations.has(params.operationId);
      if (created) {
        const item = { ...prompt(), urgency: params.urgency, appointmentType: params.appointmentType };
        operations.set(params.operationId, item); prompts = [item]; notifications++;
      }
      body = { ok: true, created, prompt: operations.get(params.operationId) };
      if (loseCreate) { loseCreate = false; res.writeHead(502).end('{"ok":false}'); return; }
    } else if (params.method === 'withdraw') {
      prompts = prompts.map(item => item.id === Number(params.promptId) ? { ...item, state: 'withdrawn' } : item);
      body = { ok: true, prompt: prompts.find(item => item.id === Number(params.promptId)) };
      if (loseWithdraw) { loseWithdraw = false; res.writeHead(502).end('{"ok":false}'); return; }
    } else { res.writeHead(400).end(); return; }
    res.end(JSON.stringify(body));
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const url = `http://127.0.0.1:${server.address().port}`;
  const browser = await chromium.launch({ headless: true, ...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
  const context = await browser.newContext({ viewport: { width: 390, height: 844 } });
  const page = await context.newPage();
  const errors = []; page.on('pageerror', error => errors.push(error.message));
  const status = page.locator('[data-role="status"]');
  const send = page.locator('[data-role="send"]');
  // The controls are in a dialog opened from the box; every check works with it open, so a hidden
  // control is hidden by the panel's own rules, not because the dialog is shut.
  const openDialog = async () => {
    await page.locator('[data-role="open"]').waitFor({ state: 'visible' });
    if (!(await page.locator('[data-role="dialog"]').evaluate(dialog => dialog.open))) {
      await page.locator('[data-role="open"]').click();
    }
  };
  const waitReady = () => openDialog().then(() => send.waitFor({ state: 'visible' }))
    .then(() => page.waitForFunction(() => !document.querySelector('[data-role="send"]').disabled));
  try {
    await page.goto(url + '/master'); await waitReady();
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    await send.click(); await page.waitForFunction(() => document.querySelector('[data-role="status"]').textContent.includes('confirmed'));
    assert.equal(notifications, 1); assert.equal(await page.evaluate(() => sessionStorage.length), 0);
    assert.match(await page.locator('[data-role="prompts"]').innerText(), /Unread/);
    console.log('PASS active master control, CSRF, confirmation and mobile layout');
    // The box on the page sums the requests up; closing the dialog returns to its button.
    assert.match(await page.locator('[data-role="summary"]').innerText(), /^Last: Follow-up · Routine · .+ · Sent · Unread$/);
    assert.equal(await page.locator('[data-role="openCount"]').innerText(), 'Open requests: 1');
    await page.locator('.portal-booking-dialog-footer [data-role="close"]').click();
    assert.equal(await page.locator('[data-role="dialog"]').evaluate(dialog => dialog.open), false);
    assert.equal(await page.evaluate(() => document.activeElement.dataset.role), 'open');
    await openDialog();
    console.log('PASS box summary, open count, and closing the dialog');
    prompts = [prompt(7, 'read')]; await page.locator('[data-role="refresh"]').click();
    await page.waitForFunction(() => document.querySelector('[data-role="prompts"]').textContent.includes('Read'));
    assert.match(await page.locator('[data-role="prompts"]').innerText(), /Read/);
    console.log('PASS read status');
    loseCreate = true; await page.selectOption('[data-role="urgency"]', 'soon');
    await send.click(); await page.waitForFunction(() => document.querySelector('[data-role="status"]').textContent.includes('could not be confirmed'));
    const first = calls.filter(call => call.method === 'create').at(-1);
    assert.equal(await page.locator('[data-role="urgency"]').isDisabled(), true);
    await page.reload(); await openDialog(); await waitReady(); assert.match(await send.innerText(), /same request/);
    await send.click(); await page.waitForFunction(() => document.querySelector('[data-role="status"]').textContent.includes('confirmed'));
    const retry = calls.filter(call => call.method === 'create').at(-1);
    assert.equal(retry.operationId, first.operationId); assert.equal(retry.urgency, 'soon'); assert.equal(notifications, 2);
    console.log('PASS lost-response retry keeps identity and choices across reload, one notification');
    for (const state of ['choice_pending', 'declined_all', 'booked', 'expired']) {
      prompts = [prompt(7, state)]; await page.locator('[data-role="refresh"]').click();
      const withdraw = page.locator('[data-role="prompts"] button'); await withdraw.waitFor();
      await withdraw.click(); await page.waitForFunction(() => document.querySelector('[data-role="status"]').textContent.includes('withdrawn'));
      assert.equal(prompts[0].state, 'withdrawn'); assert.equal(await withdraw.count(), 0);
    }
    console.log('PASS withdrawal across supported non-withdrawn states');
    prompts = [prompt()]; loseWithdraw = true; await page.locator('[data-role="refresh"]').click();
    const withdraw = page.locator('[data-role="prompts"] button'); await withdraw.waitFor(); await withdraw.click();
    await page.waitForFunction(() => document.querySelector('[data-role="status"]').textContent.includes('withdrawal could not'));
    assert.equal(await withdraw.isDisabled(), true); await page.locator('[data-role="refresh"]').click();
    await page.waitForFunction(() => !document.querySelector('[data-role="prompts"] button'));
    console.log('PASS uncertain withdrawal requires refreshed status');
    active = false; await page.reload(); await openDialog(); await page.waitForFunction(() => document.querySelector('[data-role="status"]').textContent.includes('not active'));
    assert.equal(await page.locator('[data-role="create"]').isHidden(), true);
    console.log('PASS inactive account hides create');
    active = true; outage = true; await page.reload(); await openDialog(); await page.waitForFunction(() => document.querySelector('[data-role="status"]').textContent.includes('could not be checked'));
    assert.equal(await page.locator('[data-role="create"]').isHidden(), true);
    console.log('PASS outage does not appear as inactive account');
    outage = false; malformed = true; await page.reload(); await openDialog(); await page.waitForFunction(() => document.querySelector('[data-role="status"]').textContent.includes('could not be checked'));
    assert.equal(await page.locator('[data-role="create"]').isHidden(), true); malformed = false;
    console.log('PASS missing eligibility fails closed');
    loseCreate = true; await page.reload(); await openDialog(); await waitReady(); await send.click();
    await page.waitForFunction(() => document.querySelector('[data-role="status"]').textContent.includes('could not be confirmed'));
    active = false; await page.reload(); await openDialog();
    await page.waitForFunction(() => { const text = document.querySelector('[data-role="status"]').textContent;
      return text.includes('could not be confirmed') && text.includes('not active'); });
    assert.equal(await page.locator('[data-role="create"]').isHidden(), true);
    console.log('PASS unconfirmed request with an inactive account shows both');
    active = true; outage = true; await page.reload(); await openDialog();
    await page.waitForFunction(() => { const text = document.querySelector('[data-role="status"]').textContent;
      return text.includes('could not be confirmed') && text.includes('could not be checked'); });
    console.log('PASS unconfirmed request during an outage shows both');
    outage = false; const notified = notifications; await page.reload(); await openDialog(); await waitReady();
    assert.match(await send.innerText(), /same request/); await send.click();
    await page.waitForFunction(() => document.querySelector('[data-role="status"]').textContent.includes('confirmed.'));
    assert.equal(notifications, notified); assert.equal(await page.evaluate(() => sessionStorage.length), 0);
    console.log('PASS unconfirmed request retried once the portal is back, no second notice');
    await page.reload(); await openDialog(); await waitReady(); refuseCreate = true; active = false; await send.click();
    await page.waitForFunction(() => { const text = document.querySelector('[data-role="status"]').textContent;
      return text.includes('was not sent') && text.includes('not active'); });
    assert.equal(await page.evaluate(() => sessionStorage.length), 0);
    assert.equal(await page.locator('[data-role="create"]').isHidden(), true);
    refuseCreate = false; active = true;
    console.log('PASS a refused first attempt drops its retry identity and says it was not sent');
    loseCreate = true; await page.reload(); await openDialog(); await waitReady(); await send.click();
    await page.waitForFunction(() => document.querySelector('[data-role="status"]').textContent.includes('could not be confirmed'));
    const unconfirmed = calls.filter(call => call.method === 'create').at(-1).operationId;
    await page.reload(); await openDialog(); await waitReady();
    refuseCreate = true; await send.click();
    await page.waitForFunction(() => !document.querySelector('[data-role="send"]').disabled
      && document.querySelector('[data-role="status"]').textContent.includes('could not be confirmed'));
    assert.equal(JSON.parse(await page.evaluate(() => sessionStorage.getItem('portal.booking.pending:999998:123'))).operationId, unconfirmed);
    refuseCreate = false; const noticesBefore = notifications; await send.click();
    await page.waitForFunction(() => document.querySelector('[data-role="status"]').textContent.includes('confirmed.'));
    assert.equal(calls.filter(call => call.method === 'create').at(-1).operationId, unconfirmed);
    assert.equal(notifications, noticesBefore);
    console.log('PASS a refusal after an unconfirmed attempt (and a reload) keeps the identity; no second notice');
    // A page that died mid-send leaves its entry in storage; a refusal of the retry must keep it.
    const stranded = '0f8d2c1e-5b7a-4c3d-9e1f-2a3b4c5d6e7f';
    await page.evaluate(id => sessionStorage.setItem('portal.booking.pending:999998:123',
      JSON.stringify({ operationId: id, urgency: 'routine', appointmentType: 'follow_up' })), stranded);
    await page.reload(); await openDialog(); await waitReady(); refuseCreate = true; await send.click();
    await page.waitForFunction(() => !document.querySelector('[data-role="send"]').disabled
      && document.querySelector('[data-role="status"]').textContent.includes('could not be confirmed'));
    assert.doesNotMatch(await status.innerText(), /was not sent/);
    assert.equal(JSON.parse(await page.evaluate(() => sessionStorage.getItem('portal.booking.pending:999998:123'))).operationId, stranded);
    refuseCreate = false; await send.click();
    await page.waitForFunction(() => document.querySelector('[data-role="status"]').textContent.includes('confirmed.'));
    assert.equal(calls.filter(call => call.method === 'create').at(-1).operationId, stranded);
    console.log('PASS a stored entry from an interrupted page is kept when its retry is refused');
    await page.goto(url + '/master-late-csrf'); await openDialog();
    await page.waitForFunction(() => document.querySelector('[data-role="status"]').textContent.includes('shown below'));
    await waitReady(); await send.click();
    await page.waitForFunction(() => document.querySelector('[data-role="status"]').textContent.includes('confirmed.'));
    console.log('PASS waits for the page CSRF bootstrap on first load');
    await page.goto(url + '/master-reject-csrf'); await openDialog();
    await page.waitForFunction(() => document.querySelector('[data-role="status"]').textContent.includes('shown below'));
    console.log('PASS a failed CSRF bootstrap falls back to a token already on the page');
    const plain = await browser.newContext({ viewport: { width: 390, height: 844 } });
    const plainPage = await plain.newPage(); plainPage.on('pageerror', error => errors.push(error.message));
    await plainPage.addInitScript(() => { delete Crypto.prototype.randomUUID; });
    await plainPage.goto(url + '/master');
    await plainPage.locator('[data-role="open"]').click();
    await plainPage.locator('[data-role="send"]').waitFor({ state: 'visible' });
    await plainPage.waitForFunction(() => !document.querySelector('[data-role="send"]').disabled);
    await plainPage.locator('[data-role="send"]').click();
    await plainPage.waitForFunction(() => document.querySelector('[data-role="status"]').textContent.includes('confirmed.'));
    assert.match(calls.filter(call => call.method === 'create').at(-1).operationId, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
    await plain.close();
    console.log('PASS operation IDs without randomUUID (plain HTTP) are random version-4 UUIDs');
    readOnly = true; prompts = [prompt()]; await page.reload(); await openDialog();
    await page.waitForFunction(() => document.querySelector('[data-role="prompts"]').textContent.includes('Unread'));
    assert.equal(await send.count(), 0); assert.equal(await page.locator('[data-role="prompts"] button').count(), 0); readOnly = false;
    console.log('PASS read-only fixture shows history with no mutations');
    await page.goto(url + '/appointment'); await waitReady();
    let before = calls.filter(call => call.method === 'create').length;
    await page.evaluate(() => { document.getElementById('demographic_no').value = '456'; });
    await send.click(); assert.match(await status.innerText(), /Save and reopen/);
    assert.equal(calls.filter(call => call.method === 'create').length, before);
    console.log('PASS programmatic patient switch blocks mutation without native events');
    await page.reload(); await openDialog(); await waitReady();
    // The open dialog makes the form behind it inert: staff close it, edit the patient, reopen.
    await page.locator('.portal-booking-dialog-footer [data-role="close"]').click();
    await page.fill('#keyword', 'Other patient'); await openDialog();
    assert.equal(await page.locator('[data-role="create"]').isHidden(), true);
    assert.match(await status.innerText(), /Save and reopen/);
    assert.equal(calls.filter(call => call.method === 'create').length, before);
    console.log('PASS unresolved free-text patient edit disables controls');
    await page.reload(); await openDialog(); await waitReady();
    await page.evaluate(() => { document.getElementById('keyword').value = 'Autocomplete preview'; });
    await send.click(); assert.match(await status.innerText(), /Save and reopen/);
    assert.equal(calls.filter(call => call.method === 'create').length, before);
    console.log('PASS autocomplete name preview blocks stale-patient mutation');
    await page.reload(); await openDialog(); await waitReady();
    await page.evaluate(() => { sessionStorage.setItem('portal.booking.pending:999998:123',
      JSON.stringify({ operationId: 'x', urgency: 'routine', appointmentType: 'follow_up' })); });
    await page.reload(); await openDialog(); await page.waitForFunction(() => document.querySelector('[data-role="status"]').textContent.includes('cannot retain'));
    assert.equal(await send.isDisabled(), true);
    assert.doesNotMatch(await status.innerText(), /could not be confirmed/);
    console.log('PASS corrupt pending storage refuses fresh identity');
    assert.deepEqual(errors, []);
    if (process.env.PORTAL_BOOKING_SCREENSHOT) {
      await page.evaluate(() => sessionStorage.clear()); await page.reload(); await openDialog(); await waitReady();
      await page.screenshot({ path: process.env.PORTAL_BOOKING_SCREENSHOT, fullPage: true });
    }
    console.log('PASS all 22 browser scenarios; no page errors');
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
