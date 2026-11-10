/* SPDX-License-Identifier: GPL-2.0-or-later */
/* Manage portal ▸ myCarlos devices ▸ Refresh, with a patient's earlier approval requests expired (#6124).
 * Refresh reads the patient's requests again each time, and Portal still lists the earlier ones, so
 * CARLOS refuses them again as challenge_expired. The status line above the list must not then say
 * "expired" over a request staff can approve now.
 *
 * The real panel markup (portalManage.jsp, English labels from the bundle) and the real script
 * (share/javascript/demographic/mycarlos-devices.js) run against synthetic answers from this script's
 * own loopback server. FAKE data only; no CARLOS server or database is needed.
 * Run: heavy node scripts/mycarlos-devices-expired-playwright-checks.js
 */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const { chromium } = require('playwright');
const root = path.join(__dirname, '..');

const unescape = value => value.replace(/\\u([0-9a-fA-F]{4})/g, (_, hex) => String.fromCharCode(parseInt(hex, 16)));
const english = Object.fromEntries(fs.readFileSync(path.join(root, 'src/main/resources/oscarResources_en.properties'), 'utf8')
  .split('\n').filter(line => /^(mycarlos\.approval\.|demographic\.portal\.refresh=)/.test(line)).map(line => {
    const split = line.indexOf('='); return [line.slice(0, split), unescape(line.slice(split + 1).trim())];
  }));
const escape = value => value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('"', '&quot;'); // nosemgrep: javascript.audit.detect-replaceall-sanitization.detect-replaceall-sanitization -- Test-only: escapes this repo's own English label file into a synthetic fixture page; no user input reaches it.

function markup() {
  let html = fs.readFileSync(path.join(root, 'src/main/webapp/WEB-INF/jsp/demographic/portalManage.jsp'), 'utf8');
  const start = html.indexOf('<section id="mycarlos-devices"');
  assert.ok(start >= 0, 'myCarlos devices panel in portalManage.jsp');
  html = html.slice(start, html.indexOf('</section>', start) + '</section>'.length);
  const items = html.match(/<c:forTokens var="label" delims="," items="([^"]+)">/);
  assert.ok(items, 'label list in portalManage.jsp');
  const list = items[1].split(',').map(label =>
    `<li data-key="${escape(label)}">${escape(english[`mycarlos.approval.${label}`] ?? '')}</li>`).join('');
  html = html.replace(/<c:forTokens[\s\S]*?<\/c:forTokens>/, list)
    .replace(/<\/?c:if[^>]*>/g, '')
    .replace(/<fmt:message key="([^"]+)"\/>/g, (_, key) => escape(english[key] ?? ''))
    .replace(/data-patient="[^"]*"/, 'data-patient="123"')
    .replace(/data-endpoint="[^"]*"/, 'data-endpoint="/demographic/portalDeviceApproval"')
    .replace(/data-write="[^"]*"/, 'data-write="true"');
  assert.doesNotMatch(html, /\$\{|<c:|<fmt:/, 'every JSP construct in the panel is resolved');
  return '<!doctype html><meta charset="utf-8"><input type="hidden" name="CSRF-TOKEN" value="synthetic-csrf">'
    + html + '<script src="/panel.js"></script>';
}

const row = (id, fingerprint, expired) => ({ challengeId: `FAKE-challenge-${id}`, challengeSha256: 'c'.repeat(64),
  fingerprint, expiresAt: '2026-11-10T15:44:00Z', firstDevice: true, expired });
const VALID = 'a'.repeat(64);
const OLD = 'b'.repeat(64);
// What Refresh answers in each case; the first read on page load lists nothing.
const CASES = {
  expiredOnly: { refusedRequests: ['challenge_expired', 'challenge_expired'], pending: [row(1, OLD, true)], devices: [] },
  expiredNothingListed: { refusedRequests: ['challenge_expired'], pending: [], devices: [] },
  pendingOnly: { refusedRequests: [], pending: [row(2, VALID, false)], devices: [] },
  // Portal's order puts the expired request first; the one staff can approve must still come first.
  both: { refusedRequests: ['challenge_expired', 'challenge_expired'], pending: [row(1, OLD, true), row(2, VALID, false)], devices: [] },
  otherRefusal: { refusedRequests: ['challenge_expired', 'publication_pending'], pending: [row(2, VALID, false)], devices: [] },
};

async function main() {
  let current = 'pendingOnly';
  const calls = [];
  const server = http.createServer(async (req, res) => {
    if (req.url === '/panel.js') {
      res.setHeader('Content-Type', 'text/javascript');
      res.end(fs.readFileSync(path.join(root, 'src/main/webapp/share/javascript/demographic/mycarlos-devices.js'))); return;
    }
    if (req.url === '/page') { res.setHeader('Content-Type', 'text/html'); res.end(markup()); return; }
    const [route, query] = req.url.split('?');
    if (route !== '/demographic/portalDeviceApproval') { res.writeHead(404).end(); return; }
    let raw = ''; for await (const chunk of req) { raw += chunk; }
    const params = Object.fromEntries(new URLSearchParams(req.method === 'GET' ? query : raw));
    calls.push(params);
    assert.equal(params.demographicNo, '123');
    res.setHeader('Content-Type', 'application/json');
    if (params.method === 'read') {
      assert.equal(req.method, 'GET');
      res.end(JSON.stringify({ ok: true, refusedRequests: [], pending: [], devices: [] })); return;
    }
    assert.equal(req.method, 'POST'); assert.equal(params.method, 'refresh');
    assert.equal(req.headers['csrf-token'], 'synthetic-csrf');
    res.end(JSON.stringify({ ok: true, ...CASES[current] }));
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const url = `http://127.0.0.1:${server.address().port}`;
  const browser = await chromium.launch({ headless: true, ...(process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {}) });
  const page = await browser.newPage();
  const errors = []; page.on('pageerror', error => errors.push(error.message));
  const status = page.locator('#mycarlos-devices-status');
  const entries = page.locator('#mycarlos-devices-list > div');
  // The panel marks itself busy before each request and clears that only after drawing the answer.
  async function settled(count) {
    for (let waited = 0; calls.length < count; waited += 25) {
      assert.ok(waited < 10000, `request ${count} reached the server`);
      await new Promise(resolve => setTimeout(resolve, 25));
    }
    await page.waitForFunction(() => !document.getElementById('mycarlos-devices').hasAttribute('aria-busy'));
  }
  // Loads the panel (its own read), then presses Refresh with the given answer.
  async function refresh(name) {
    current = name;
    const before = calls.length;
    // The origin is this script's own loopback fixture server and the route a fixed literal.
    await page.goto(url + '/page'); // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- loopback fixture origin plus a fixed literal route
    await settled(before + 1);
    await page.locator('#mycarlos-devices-check').click();
    await settled(before + 2);
    assert.equal(calls.length, before + 2, `${name}: one read and one refresh`);
    assert.deepEqual(calls.slice(before).map(call => call.method), ['read', 'refresh']);
  }
  const earlier = english['mycarlos.approval.status.earlierExpired'];
  const rowExpired = english['mycarlos.approval.status.expired'];
  try {
    assert.ok(earlier && rowExpired && earlier !== rowExpired, 'both expired texts exist and differ');

    await refresh('expiredOnly');
    assert.equal(await status.textContent(), earlier);
    assert.equal(await entries.count(), 1);
    assert.equal(await entries.nth(0).locator('p').textContent(), rowExpired);
    assert.equal(await entries.nth(0).locator('form').count(), 0);
    console.log('PASS only expired requests: the status line says an earlier request expired; no approve form');

    await refresh('expiredNothingListed');
    assert.equal(await status.textContent(), earlier);
    assert.equal(await page.locator('#mycarlos-devices-list').textContent(), english['mycarlos.approval.none']);
    console.log('PASS expired refusals with nothing listed: the earlier-expired notice and "no requests"');

    await refresh('pendingOnly');
    assert.equal(await status.textContent(), '');
    assert.equal(await entries.count(), 1);
    assert.equal(await entries.nth(0).locator('pre').textContent(), VALID);
    assert.equal(await entries.nth(0).locator('form').count(), 1);
    console.log('PASS only a pending request: no status text, the approve form is shown');

    await refresh('both');
    assert.equal(await status.textContent(), '', 'nothing about expiry above a request staff can approve');
    assert.equal(await entries.count(), 2);
    assert.equal(await entries.nth(0).locator('pre').textContent(), VALID, 'the request staff can approve comes first');
    assert.equal(await entries.nth(0).locator('form').count(), 1);
    assert.equal(await entries.nth(1).locator('pre').textContent(), OLD);
    assert.equal(await entries.nth(1).locator('p').textContent(), rowExpired, 'the expired request keeps its own label');
    assert.equal(await entries.nth(1).locator('form').count(), 0);
    console.log('PASS both: no expired notice above, the valid request first with its form, the expired one after');

    await refresh('otherRefusal');
    assert.equal(await status.textContent(), english['mycarlos.approval.status.unavailable']);
    console.log('PASS another refusal beside expired ones still says the status is unavailable');

    assert.deepEqual(errors, []);
  } finally {
    await browser.close();
    server.close();
  }
}

main().catch(error => { console.error(error); process.exit(1); });
