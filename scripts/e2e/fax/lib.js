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

const cfg = () => ({
  base: env('BASE_URL'),
  user: env('TEST_USER'),
  pass: env('TEST_PASSWORD'),
  pin: env('TEST_PIN'),
  chrome: process.env.CHROME_PATH || undefined,
  srfax: {
    accessId: env('SRFAX_ACCESS_ID'),
    pass: env('SRFAX_PASS'),
    email: env('SRFAX_USER'),
    faxNumber: env('SRFAX_FAX_NUMBER'),
  },
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
    await p.goto(c.base + '/admin/ViewManageFaxes', { waitUntil: 'domcontentloaded' });
    await p.waitForTimeout(800);
    const rows = await p.locator('table tr').count().catch(() => 0);
    if (rows > sinceCount + 1) return rows;
    await p.waitForTimeout(15000);
  }
  return -1;
}

module.exports = { env, cfg, launch, login, ensureSrfaxConfigured, waitForInboundFax };
