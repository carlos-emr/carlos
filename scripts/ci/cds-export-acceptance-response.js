/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

// Retain bounded metadata only: acceptance bodies and transport messages can contain sensitive values.
async function readAcceptanceResponse(response, diagnostic) {
  diagnostic.httpStatus = response.status();
  const type = String(response.headers()['content-type'] || '').toLowerCase();
  diagnostic.contentType = !type ? 'MISSING' : type.includes('json') ? 'JSON'
    : type.includes('html') ? 'HTML' : type.startsWith('text/') ? 'TEXT' : 'OTHER';
  let body;
  try {
    body = await response.body();
  } catch (failure) {
    diagnostic.bodyAvailable = false;
    const message = String(failure.message || '');
    diagnostic.errorCategory = /No resource with given identifier|No data found for resource|body is unavailable/i.test(message)
      ? 'BODY_UNAVAILABLE' : /(?:page|context|browser).*closed/i.test(message) ? 'CONTEXT_CLOSED'
        : /timeout|timed out/i.test(message) ? 'BODY_TIMEOUT' : 'BODY_READ_ERROR';
    throw failure;
  }
  diagnostic.bodyAvailable = true;
  diagnostic.bodyBytes = body.length;
  try {
    return JSON.parse(body.toString('utf8'));
  } catch (failure) {
    diagnostic.errorCategory = 'INVALID_JSON';
    throw failure;
  }
}

module.exports = {readAcceptanceResponse};
