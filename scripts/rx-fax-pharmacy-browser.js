/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const { assert, assertStrictPage } = require('./lib/playwright-harness');

/** Exclude only the deliberately injected, pre-write 409 and its matching browser console entry. */
function assertFaxCaseBrowser(events, label, retry) {
  const expected = entry => retry && entry.label === label && entry.status === 409 && entry.method === 'POST'
    && new URL(entry.url).pathname.endsWith('/rx/WriteToEncounter');
  const rejected = events.badResponses.filter(expected);
  assert(rejected.length === (retry ? 1 : 0), 'The injected retry rejection was not observed exactly once');
  const expectedConsole = entry => retry && entry.label === label && rejected.some(response => entry.location?.url === response.url)
    && /^Failed to load resource: the server responded with a status of 409\b/.test(entry.text);
  assertStrictPage({ ...events, badResponses: events.badResponses.filter(entry => !expected(entry)),
    consoleIssues: events.consoleIssues.filter(entry => !expectedConsole(entry)) }, [label]);
}

module.exports = { assertFaxCaseBrowser };
