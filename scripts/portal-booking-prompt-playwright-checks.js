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
function markup(appointment, readOnly = false) {
  let html = fs.readFileSync(path.join(root, 'src/main/webapp/WEB-INF/jsp/demographic/portalBookingPrompt.jsp'), 'utf8');
  html = html.slice(html.indexOf('<section'), html.indexOf('</section>') + 10);
  if (readOnly) {
    html = html.replace(/<% if \(portalMayCreate\) \{ %>[\s\S]*?<% } %>/, '')
      .replace(/<% if \(portalMayWrite\) \{ %>[\s\S]*?<% } %>/, '');
  }
  html = html.replace(/<fmt:message key="([^"]+)"\/>/g, (_, key) => escape(labels[key]))
    .replace(/data-actor="[^\n]*"/, 'data-actor="999998"')
    .replace(/data-patient="[^"]*"/, 'data-patient="123"')
    .replace(/data-patient-input="[^\n]*"/, `data-patient-input="${appointment ? '#demographic_no' : ''}"`)
    .replace(/data-endpoint="[^\n]*"/, 'data-endpoint="/demographic/portalBookingPrompt"')
    .replace(/<%[\s\S]*?%>/g, '');
  return '<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">'
    + '<link rel="stylesheet" href="/panel.css"><input type="hidden" name="CSRF-TOKEN" value="synthetic-csrf">'
    + (appointment ? '<input id="demographic_no" value="123" readonly><input id="keyword" value="Synthetic Patient">' : '')
    + html + '<script src="/panel.js"></script>';
}
function prompt(id = 7, state = 'sent') {
  return { id, state, urgency: 'routine', appointmentType: 'follow_up', createdAt: '2026-10-01T12:00:00Z',
    readAt: state === 'read' ? '2026-10-01T13:00:00Z' : null };
}
async function main() {
  let active = true, readOnly = false, outage = false, loseCreate = false, loseWithdraw = false;
  let malformed = false;
  let prompts = [], notifications = 0, calls = [];
  const operations = new Map();
  const server = http.createServer(async (req, res) => {
    if (req.url === '/panel.js' || req.url === '/panel.css') {
      const type = req.url.endsWith('.js') ? 'js' : 'css';
      res.setHeader('Content-Type', type === 'js' ? 'text/javascript' : 'text/css');
      res.end(fs.readFileSync(path.join(root, `src/main/webapp/${type}/portalBookingPrompt.${type}`))); return;
    }
    if (req.url === '/master' || req.url === '/appointment') {
      res.setHeader('Content-Type', 'text/html'); res.end(markup(req.url === '/appointment', readOnly)); return;
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
  const waitReady = () => send.waitFor({ state: 'visible' }).then(() => page.waitForFunction(() => !document.querySelector('[data-role="send"]').disabled));
  try {
    await page.goto(url + '/master'); await waitReady();
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    await send.click(); await page.waitForFunction(() => document.querySelector('[data-role="status"]').textContent.includes('confirmed'));
    assert.equal(notifications, 1); assert.equal(await page.evaluate(() => sessionStorage.length), 0);
    assert.match(await page.locator('[data-role="prompts"]').innerText(), /Unread/);
    console.log('PASS active master control, CSRF, confirmation and mobile layout');
    prompts = [prompt(7, 'read')]; await page.locator('[data-role="refresh"]').click();
    await page.waitForFunction(() => document.querySelector('[data-role="prompts"]').textContent.includes('Read'));
    assert.match(await page.locator('[data-role="prompts"]').innerText(), /Read/);
    console.log('PASS read status');
    loseCreate = true; await page.selectOption('[data-role="urgency"]', 'soon');
    await send.click(); await page.waitForFunction(() => document.querySelector('[data-role="status"]').textContent.includes('could not be confirmed'));
    const first = calls.filter(call => call.method === 'create').at(-1);
    assert.equal(await page.locator('[data-role="urgency"]').isDisabled(), true);
    await page.reload(); await waitReady(); assert.match(await send.innerText(), /same request/);
    await send.click(); await page.waitForFunction(() => document.querySelector('[data-role="status"]').textContent.includes('confirmed'));
    const retry = calls.filter(call => call.method === 'create').at(-1);
    assert.equal(retry.operationId, first.operationId); assert.equal(retry.urgency, 'soon'); assert.equal(notifications, 2);
    console.log('PASS lost-response retry keeps identity and choices across reload, one notification');
    loseCreate = true; await send.click();
    await page.waitForFunction(() => document.querySelector('[data-role="status"]').textContent.includes('could not be confirmed'));
    const bookedRequest = calls.filter(call => call.method === 'create').at(-1);
    const bookedPrompt = { ...operations.get(bookedRequest.operationId), state: 'booked' };
    operations.set(bookedRequest.operationId, bookedPrompt); prompts = [bookedPrompt];
    const noticesBeforeRetry = notifications;
    await page.reload(); await waitReady(); await send.click();
    await page.waitForFunction(() => document.querySelector('[data-role="status"]').textContent.includes('confirmed')
      && document.querySelector('[data-role="prompts"]').textContent.includes('Booked'));
    assert.equal(calls.filter(call => call.method === 'create').at(-1).operationId, bookedRequest.operationId);
    assert.equal(notifications, noticesBeforeRetry);
    assert.match(await status.innerText(), /Check its current status below/);
    assert.doesNotMatch(await status.innerText(), /No appointment has been booked/);
    assert.equal(await page.evaluate(() => sessionStorage.length), 0);
    console.log('PASS booked retry confirms original request without a false booking claim or new notification');
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
    active = false; await page.reload(); await page.waitForFunction(() => document.querySelector('[data-role="status"]').textContent.includes('not active'));
    assert.equal(await page.locator('[data-role="create"]').isHidden(), true);
    console.log('PASS inactive account hides create');
    active = true; outage = true; await page.reload(); await page.waitForFunction(() => document.querySelector('[data-role="status"]').textContent.includes('could not be checked'));
    assert.equal(await page.locator('[data-role="create"]').isHidden(), true);
    console.log('PASS outage does not appear as inactive account');
    outage = false; malformed = true; await page.reload(); await page.waitForFunction(() => document.querySelector('[data-role="status"]').textContent.includes('could not be checked'));
    assert.equal(await page.locator('[data-role="create"]').isHidden(), true); malformed = false;
    console.log('PASS missing eligibility fails closed');
    readOnly = true; prompts = [prompt()]; await page.reload();
    await page.waitForFunction(() => document.querySelector('[data-role="prompts"]').textContent.includes('Unread'));
    assert.equal(await send.count(), 0); assert.equal(await page.locator('[data-role="prompts"] button').count(), 0); readOnly = false;
    console.log('PASS read-only fixture shows history with no mutations');
    await page.goto(url + '/appointment'); await waitReady();
    let before = calls.filter(call => call.method === 'create').length;
    await page.evaluate(() => { document.getElementById('demographic_no').value = '456'; });
    await send.click(); assert.match(await status.innerText(), /Save and reopen/);
    assert.equal(calls.filter(call => call.method === 'create').length, before);
    console.log('PASS programmatic patient switch blocks mutation without native events');
    await page.reload(); await waitReady(); await page.fill('#keyword', 'Other patient');
    assert.equal(await page.locator('[data-role="create"]').isHidden(), true);
    assert.match(await status.innerText(), /Save and reopen/);
    assert.equal(calls.filter(call => call.method === 'create').length, before);
    console.log('PASS unresolved free-text patient edit disables controls');
    await page.reload(); await waitReady();
    await page.evaluate(() => { document.getElementById('keyword').value = 'Autocomplete preview'; });
    await send.click(); assert.match(await status.innerText(), /Save and reopen/);
    assert.equal(calls.filter(call => call.method === 'create').length, before);
    console.log('PASS autocomplete name preview blocks stale-patient mutation');
    await page.reload(); await waitReady();
    await page.evaluate(() => { sessionStorage.setItem('portal.booking.pending:999998:123', 'invalid'); });
    await page.reload(); await page.waitForFunction(() => document.querySelector('[data-role="status"]').textContent.includes('cannot retain'));
    assert.equal(await send.isDisabled(), true);
    console.log('PASS corrupt pending storage refuses fresh identity');
    assert.deepEqual(errors, []);
    if (process.env.PORTAL_BOOKING_SCREENSHOT) {
      await page.evaluate(() => sessionStorage.clear()); await page.reload(); await waitReady();
      await page.screenshot({ path: process.env.PORTAL_BOOKING_SCREENSHOT, fullPage: true });
    }
    console.log('PASS all 14 browser scenarios; no page errors');
  } finally { await browser.close(); await new Promise(resolve => server.close(resolve)); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
