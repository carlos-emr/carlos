/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

/** Allow a controller to reserve a synthetic line before any fixture mutation. */
function readFaxSuffix(value, digits, randomInt) {
  if (value === undefined) return String(randomInt(10 ** (digits - 1), 10 ** digits));
  if (typeof value !== 'string' || value.length !== digits || !new RegExp(`^[1-9][0-9]{${digits - 1}}$`).test(value)) {
    throw new Error(`PR4055_RX_FAX_SUFFIX must contain exactly ${digits} digits and start with 1-9`);
  }
  return value;
}

function assertFaxDestination(request, sender, destination) {
  const body = new URLSearchParams(request.postData() || '');
  const query = new URL(request.url()).searchParams;
  // Reject ambiguity between query/body and duplicate parameters: servlet parameter
  // precedence must never choose a different account or destination from this guard.
  for (const [key, expected, label] of [['clinicFax', sender, 'owned sender'], ['pharmaFax', destination, 'synthetic destination']]) {
    const values = [...query.getAll(key), ...body.getAll(key)];
    if (values.length !== 1 || values[0] !== expected) throw new Error(`Fax request did not use the ${label}`);
  }
}

/** Abort an incorrectly addressed Rx fax before its POST reaches the application. */
async function installFaxRequestGuard(page, baseUrl, sender, destination, onViolation) {
  const endpoint = new URL(String(baseUrl));
  endpoint.pathname = endpoint.pathname.replace(/\/$/, '') + '/form/createcustomedpdf';
  endpoint.search = '';
  endpoint.hash = '';
  await page.route(url => url.origin === endpoint.origin && url.pathname === endpoint.pathname, async route => {
    const request = route.request();
    const body = new URLSearchParams(request.postData() || '');
    const methods = [...new URL(request.url()).searchParams.getAll('__method'), ...body.getAll('__method')];
    if (request.method() !== 'POST' || !methods.includes('oscarRxFax')) return route.fallback();
    try {
      assertFaxDestination(request, sender, destination);
    } catch (_) {
      onViolation();
      await route.abort('blockedbyclient');
      return;
    }
    await route.continue();
  });
}

module.exports = { readFaxSuffix, assertFaxDestination, installFaxRequestGuard };
