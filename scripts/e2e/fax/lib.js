// Shared helpers for the SRFax outbound end-to-end tests. All configuration
// comes from the environment (see README.md); nothing about any SRFax account
// is hardcoded here.
'use strict';
const { chromium } = require('playwright');

function env(name, required = true) {
  const v = process.env[name];
  if (required && (!v || v === '')) {
    throw new Error(`required environment variable ${name} is not set`);
  }
  return v;
}

// Validate BASE_URL and refuse a non-local target unless explicitly allowed —
// these tests drive a real deployment and send real faxes, so an accidental
// run against the wrong host must be hard, matching scripts/login-playwright-checks.js.
function validateBaseUrl(raw) {
  const u = new URL(raw);
  if (!['http:', 'https:'].includes(u.protocol)) {
    throw new Error(`BASE_URL must be http/https, got ${u.protocol}`);
  }
  const host = u.hostname.toLowerCase();
  const local = new Set(['localhost', '127.0.0.1', '::1', '0.0.0.0', 'carlos', 'carlos-ubuntu26']);
  const privateIp = /^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[0-1])\.)/.test(host);
  if (!local.has(host) && !privateIp && process.env.ALLOW_NON_LOCAL_BASE_URL !== 'true') {
    throw new Error(`refusing non-local BASE_URL host ${host}; set ALLOW_NON_LOCAL_BASE_URL=true for an intentional target`);
  }
  return u.toString().replace(/\/$/, '');
}

// Assemble config from the environment. Pass { srfax: false } for tests that
// drive an already-imported fax and never call the SRFax API, so they do not
// require SRFax account credentials just to run.
const cfg = ({ srfax = true } = {}) => ({
  base: validateBaseUrl(env('BASE_URL')),
  user: env('TEST_USER'),
  pass: env('TEST_PASSWORD'),
  pin: env('TEST_PIN'),
  chrome: process.env.CHROME_PATH || undefined,
  srfax: srfax ? {
    accessId: env('SRFAX_ACCESS_ID'),
    pass: env('SRFAX_PASS'),
    email: env('SRFAX_USER'),
    faxNumber: env('SRFAX_FAX_NUMBER'),
  } : undefined,
});

async function launch() {
  return chromium.launch({
    headless: true,
    executablePath: process.env.CHROME_PATH,
    args: ['--no-sandbox', '--disable-dev-shm-usage'],
  });
}

// Log in, completing a forced password reset if the account is in that state.
async function login(ctx, c) {
  const p = await ctx.newPage();
  // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- URL is validated base + constant path, no user input
  await p.goto(c.base + '/', { waitUntil: 'domcontentloaded' });
  await p.locator('#username').fill(c.user);
  await p.locator('#password').fill(c.pass);
  await p.locator('#pin').fill(c.pin);
  await Promise.all([
    p.waitForLoadState('domcontentloaded').catch(() => {}),
    p.locator('input[type="submit"],button[type="submit"]').first().click(),
  ]);
  await p.waitForTimeout(1500);
  if (/forcepasswordreset/.test(p.url())) {
    throw new Error('account requires a forced password reset; complete it before running these tests');
  }
  return p;
}

// Ensure the SRFax provider is configured and active. Idempotent.
async function ensureSrfaxConfigured(p, c) {
  // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- validated base + constant path
  await p.goto(c.base + '/admin/ViewConfigureFax', { waitUntil: 'domcontentloaded' });
  await p.waitForTimeout(500);
  const already = await p.locator('#faxUser').inputValue().catch(() => '');
  if (already === c.srfax.accessId) return 'already-configured';
  await p.locator('#faxUser').fill(c.srfax.accessId);
  await p.locator('#faxPasswd').fill(c.srfax.pass);
  await p.locator('#faxNumber').fill(c.srfax.faxNumber);
  await p.locator('#senderEmail').fill(c.srfax.email);
  await p.locator('#accountName').fill('E2E loopback');
  await p.locator('#on').check();
  await p.locator('#download_on').check();
  await p.evaluate(() => document.getElementById('submit').removeAttribute('disabled'));
  await Promise.all([
    p.waitForLoadState('domcontentloaded').catch(() => {}),
    p.locator('#submit').click(),
  ]);
  await p.waitForTimeout(1500);
  return 'configured';
}

// Poll the fax inbox (ManageFaxes) until an inbound fax arrives, up to
// timeoutMs. Returns the count of inbound faxes seen. The scheduler imports
// on its own 60s cycle, so allow several minutes for a real round-trip.
async function waitForInboundFax(p, c, { sinceCount = 0, timeoutMs = 480000 } = {}) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    // nosemgrep: javascript.playwright.security.audit.playwright-goto-injection.playwright-goto-injection -- validated base + constant path
    await p.goto(c.base + '/admin/ViewManageFaxes', { waitUntil: 'domcontentloaded' });
    await p.waitForTimeout(800);
    const rows = await p.locator('table tr').count().catch(() => 0);
    if (rows > sinceCount + 1) return rows;
    await p.waitForTimeout(15000);
  }
  return -1;
}

// Scrape the CSRFGuard token from a rendered page. Every authenticated app
// page carries it as a hidden <input name="CSRF-TOKEN">; POSTs without it are
// rejected 403 by CarlosCsrfGuardFilter. Navigate to an app page first.
async function csrfToken(p) {
  const t = await p.evaluate(() => {
    const el = document.querySelector('input[name="CSRF-TOKEN"]');
    return el ? el.value : null;
  });
  if (!t) throw new Error('CSRF-TOKEN not found on page — navigate to an app page before posting');
  return t;
}

// POST a form-encoded request from WITHIN the page context so it carries the
// session cookies and same-origin credentials, with the scraped CSRF token
// appended. url must be absolute (c.base + path) — a relative path would drop
// the /carlos context. Returns { status, url }.
async function postForm(p, url, params) {
  const token = await csrfToken(p);
  // nosemgrep: javascript.playwright.security.audit.playwright-evaluate-arg-injection.playwright-evaluate-arg-injection -- url is the validated base + a constant path and params are test-controlled; no untrusted input reaches evaluate
  return p.evaluate(async ({ url, params, token }) => {
    const body = new URLSearchParams({ ...params, 'CSRF-TOKEN': token }).toString();
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
      credentials: 'same-origin',
    });
    return { status: res.status, url: res.url };
  }, { url, params, token });
}

module.exports = { env, cfg, launch, login, ensureSrfaxConfigured, waitForInboundFax, csrfToken, postForm };
