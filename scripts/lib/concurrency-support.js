/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
/*
 * Shared plumbing for the `concurrency` (lost update / stale edit) checks.
 *
 * Every check drives TWO real browser sessions of the same login (the application supports
 * simultaneous sessions) against one owned fixture: session A opens a form, session B changes and
 * saves the same record, then session A saves from the form it loaded BEFORE B's save. The
 * correct behaviour is a refusal or a merge; a silent overwrite is the defect. Nothing here counts
 * global rows and nothing writes outside the owned fixture.
 */
const h = require('./playwright-harness');
const { openMasterRecord } = require('../master-record-tabs-playwright-checks');

/**
 * Log a second session of the shared test login in (its own browser context, strict page wiring)
 * and, unless `openMaster` is false, open the owned patient's Master Record in it.
 * The context is closed with the workflow's browser.
 */
async function openSecondSession(s, { label = 'second-session', openMaster = true, config = s.config, dialogHandler } = {}) {
  const context = await h.newContext(s.context.browser(), config);
  context.setDefaultTimeout(20000);
  context.on('page', page => h.wireStrictPage(page, label, s.recorder, dialogHandler ? { dialogHandler } : {}));
  const schedule = await h.login(context, config, s.recorder);
  let master = null;
  if (openMaster) {
    ({ masterPage: master } = await openMasterRecord(context, schedule, s.recorder, {
      searchTerm: s.marker, preferredDemographicNo: s.patient, timeout: 20000,
    }));
    h.assert(new URL(master.url()).searchParams.get('demographic_no') === s.patient,
      'The second session opened a patient other than the owned fixture');
  }
  return { context, schedule, master };
}

/** Snapshot of the recorder's failure buffers, taken before a step that deliberately fails. */
function failureMark(recorder) {
  return { responses: recorder.badResponses.length, console: recorder.consoleIssues.length, failures: recorder.requestFailures.length };
}

/**
 * Consume exactly the one HTTP failure a negative step asserts (method, status, URL path pattern);
 * anything else the recorder collected stays a finding.
 */
function consumeExpectedFailure(recorder, mark, { status, method = 'POST', path, appConsole, max = 1 }) {
  const errors = recorder.badResponses.slice(mark.responses);
  h.assert(errors.length >= 1 && errors.length <= max && errors.every(e => e.status === status && e.method === method
    && path.test(new URL(e.url).pathname)),
  `Expected ${max === 1 ? 'exactly one' : `1 to ${max}`} HTTP ${status} on ${method} ${path} and nothing else`);
  const urls = new Set(errors.map(e => e.url));
  recorder.badResponses.splice(mark.responses, errors.length);
  for (let i = recorder.consoleIssues.length - 1; i >= mark.console; i--) {
    const entry = recorder.consoleIssues[i];
    if (entry.location && urls.has(entry.location.url) && entry.text.startsWith('Failed to load resource:')
        && entry.text.includes(`status of ${status} (`)) recorder.consoleIssues.splice(i, 1);
    // The page's own handler logging the refusal it just showed the user (console.error(new Error('HTTP 409'))).
    else if (appConsole && appConsole.test(entry.text)) recorder.consoleIssues.splice(i, 1);
  }
}

/**
 * A refused fetch whose body the page never reads is reported as ERR_ABORTED once the page navigates
 * away. Consume exactly those entries (fetch, ERR_ABORTED, path) recorded since `mark`.
 */
function consumeAbortedFetch(recorder, mark, path) {
  for (let i = recorder.requestFailures.length - 1; i >= mark.failures; i--) {
    const entry = recorder.requestFailures[i];
    if (entry.resourceType === 'fetch' && /ERR_ABORTED/.test(entry.errorText) && path.test(new URL(entry.url).pathname)) {
      recorder.requestFailures.splice(i, 1);
    }
  }
}

/**
 * Drop console entries matching `pattern` recorded since `mark`: a defect the application already has on
 * the page the check must pass through (named by the caller), so it does not mask the step under test.
 */
function consumeKnownConsole(recorder, mark, pattern) {
  for (let i = recorder.consoleIssues.length - 1; i >= mark.console; i--) {
    if (pattern.test(recorder.consoleIssues[i].text)) recorder.consoleIssues.splice(i, 1);
  }
  // The same missing asset also shows up as a failed request / bad response.
  for (let i = recorder.requestFailures.length - 1; i >= mark.failures; i--) {
    if (pattern.test(recorder.requestFailures[i].url)) recorder.requestFailures.splice(i, 1);
  }
  for (let i = recorder.badResponses.length - 1; i >= mark.responses; i--) {
    if (pattern.test(recorder.badResponses[i].url)) recorder.badResponses.splice(i, 1);
  }
}

/** Poll `read()` until it returns a truthy value or the deadline passes. */
async function waitUntil(read, description, timeout = 15000) {
  const deadline = Date.now() + timeout;
  let last;
  do {
    last = await read();
    if (last) return last;
    await new Promise(resolve => setTimeout(resolve, 200));
  } while (Date.now() < deadline);
  throw new Error(`timed out waiting for ${description}`);
}

module.exports = { openSecondSession, failureMark, consumeExpectedFailure, consumeAbortedFetch, consumeKnownConsole, waitUntil };
