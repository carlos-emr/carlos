/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const {assert} = require('./playwright-harness');

/** Observe real read-only preview refusals; never route, inject or replay a request. */
function observeStoredPreviewCapacity({page, endpoint, documentId, label, recorder}) {
  let beforeResponses = new Set(recorder.badResponses), beforeConsole = new Set(recorder.consoleIssues);
  const states = new Map(), pending = new Set(), errors = [];
  let order = 0;
  function matches(request) {
    const url = new URL(request.url());
    return url.origin + url.pathname === endpoint
      && ['viewDocPage', 'showPage'].includes(url.searchParams.get('method'))
      && url.searchParams.get('doc_no') === String(documentId);
  }
  function observe(response) {
    const request = response.request();
    if (!matches(request)) return;
    try {
      const url = new URL(request.url());
      const pageParameter = url.searchParams.get('method') === 'showPage' ? 'page' : 'curPage';
      assert(request.method() === 'GET' && request.resourceType() === 'fetch' && !request.postData(),
        'Stored preview must be a read-only browser fetch');
      assert(url.searchParams.getAll('method').length === 1 && url.searchParams.getAll('doc_no').length === 1
        && url.searchParams.getAll(pageParameter).length === 1 && /^[1-9][0-9]*$/.test(url.searchParams.get(pageParameter)),
      'Stored preview request has ambiguous document/page parameters');
      const state = states.get(url.href) || {busy: 0, lastBusy: 0, lastSuccess: 0};
      states.set(url.href, state);
      const headers = response.headers(), sequence = ++order;
      if (response.status() === 503) {
        // ManageDocument.sendRenderBusy uses sendError, whose error JSP produces HTML.
        // These exact headers and later decoded recovery distinguish it from an outage.
        assert(headers['retry-after'] === '1' && headers['cache-control'] === 'no-store'
          && /^text\/html(?:\s*;|$)/i.test(headers['content-type'] || ''),
        'Stored preview 503 lacks the installed admission-refusal contract');
        state.busy++; state.lastBusy = sequence;
      } else {
        assert(response.status() === 200 && /^image\/png(?:\s*;|$)/i.test(headers['content-type'] || ''),
          'Stored preview did not return a PNG image');
        state.lastSuccess = sequence;
      }
      const completed = Promise.resolve().then(() => response.finished()).then(error => {
        assert(!error, 'Stored preview response body failed');
      }).catch(error => errors.push(error)).finally(() => pending.delete(completed));
      pending.add(completed);
    } catch (error) {errors.push(error);}
  }
  function failed(request) {
    if (matches(request)) errors.push(new Error('Stored preview transport failed'));
  }
  page.on('response', observe); page.on('requestfailed', failed);
  return {
    async finish(decodedUrls) {
      await Promise.all([...pending]);
      if (errors.length) throw errors[0];
      // The loader currently canonicalizes this attribute, but DOM-authored sources
      // may be context-relative. Resolve against this page, never a guessed app root.
      const decoded = new Set(decodedUrls.map(value => new URL(value, page.url()).href));
      const removeResponses = new Set(), removeConsole = new Set();
      for (const [url, state] of states) {
        if (!state.busy) continue;
        assert(state.lastSuccess > state.lastBusy && decoded.has(url),
          'Stored preview capacity refusal did not recover to a decoded image for the same page');
        const responses = recorder.badResponses.filter(entry => !beforeResponses.has(entry)
          && entry.label === label && entry.url === url && entry.method === 'GET'
          && entry.resourceType === 'fetch' && entry.status === 503
          && /^text\/html(?:\s*;|$)/i.test(entry.contentType || ''));
        assert(responses.length === state.busy, 'Recorded preview failures differ from validated capacity responses');
        const messages = recorder.consoleIssues.filter(entry => !beforeConsole.has(entry)
          && entry.label === label && entry.type === 'error' && entry.location?.url === url
          && /^Failed to load resource:.*\b503\b/.test(entry.text || ''));
        assert(messages.length <= state.busy, 'Additional preview capacity console errors were recorded');
        responses.forEach(entry => removeResponses.add(entry));
        messages.forEach(entry => removeConsole.add(entry));
      }
      // Do not edit the recorder at all unless every observed refusal is recovered.
      recorder.badResponses = recorder.badResponses.filter(entry => !removeResponses.has(entry));
      recorder.consoleIssues = recorder.consoleIssues.filter(entry => !removeConsole.has(entry));
      states.clear();
      beforeResponses = new Set(recorder.badResponses); beforeConsole = new Set(recorder.consoleIssues);
    },
    close() {page.off('response', observe); page.off('requestfailed', failed);},
  };
}
module.exports = {observeStoredPreviewCapacity};
