/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const h = require('./playwright-harness');

function isOwnedForwardDialogRequest(request, page, expectedUrl, documentNo) {
  try {
    const fields = new URLSearchParams(request.postData() || '');
    return /^[1-9]\d*$/.test(String(documentNo)) && request.frame().page() === page
      && request.method() === 'POST' && request.url() === expectedUrl
      && fields.getAll('forwardList').length === 1 && fields.get('forwardList') === `${documentNo}:DOC`;
  } catch { return false; }
}

/** Recognize only the injected, observed owned request after its UI/SQL checks passed. */
async function acknowledgeOwnedForwardFailure(recorder, {
  page, expectedUrl, documentNo, request, response, intercepted, before,
}) {
  h.assert(intercepted === 1 && response.request() === request
    && isOwnedForwardDialogRequest(request, page, expectedUrl, documentNo),
  'The controlled Forward failure was not exactly its owned request');
  await response.finished();
  h.assert(response.status() === 503 && await response.text() === 'test outage',
    'The controlled Forward failure was not the injected response');
  const http = recorder.badResponses.filter(entry => !before.http.has(entry)
    && entry.url === expectedUrl && entry.method === 'POST' && entry.status === 503);
  const resource = recorder.consoleIssues.filter(entry => !before.console.has(entry)
    && entry.location?.url === expectedUrl && /^Failed to load resource:.*503/.test(entry.text));
  const reported = recorder.consoleIssues.filter(entry => !before.console.has(entry)
    && /\/share\/javascript\/oscarMDSIndex\.js(?:\?|$)/.test(entry.location?.url || '')
    && /^Forward dialog request failed: error(?: Service Unavailable)?$/.test(entry.text.trim()));
  h.assert(http.length === 1 && resource.length <= 1 && reported.length === 1,
    'The controlled Forward failure has missing or ambiguous diagnostics');
  const audit = { kind: 'owned synthetic Forward503', documentNo: String(documentNo),
    http: [...http], console: [...resource, ...reported] };
  // Every proof above precedes mutation. Other old/new findings stay fatal.
  recorder.badResponses = recorder.badResponses.filter(entry => !http.includes(entry));
  recorder.consoleIssues = recorder.consoleIssues.filter(entry => !resource.includes(entry) && !reported.includes(entry));
  return audit;
}

module.exports = { isOwnedForwardDialogRequest, acknowledgeOwnedForwardFailure };
