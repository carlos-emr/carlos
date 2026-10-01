'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { beginEntry, createLedger, entryFailures, headerFindings, scanText } = require('./lib/page-health-engine');

const kinds = findings => findings.map(finding => finding.kind).sort();

test('scanText reports each rendering accident once with its kind', () => {
  const read = {
    text: 'Name: null\nDOB: undefined\nTotal NaN\n???demographic.foo.bar??? and [object Object]\n'
      + 'Link ${patient.name} and %{foo} and Tom &amp;amp; Jerry, java.lang.NullPointerException',
    values: ['null', 'ok', '???other.key???'],
    title: 'Fine',
  };
  // The whole sorted list: a duplicated finding or an unexpected extra kind fails too. The two
  // missing-resource-key hits are the one in the text and the one in a form value.
  assert.deepEqual(kinds(scanText(read)), [
    'double-encoded-entity', 'field-null', 'java-leak', 'literal-nan', 'literal-null',
    'literal-undefined', 'missing-resource-key', 'missing-resource-key', 'object-object',
    'unresolved-el', 'unresolved-ognl',
  ]);
});

test('scanText leaves ordinary words, paths and identifiers alone', () => {
  const read = {
    text: 'Nullable fields, a nullity test, /carlos/null.png, user@null.example, undefined-behaviour notes',
    values: ['', 'Smith', '0'],
    title: 'Schedule',
  };
  assert.deepEqual(scanText(read).map(f => f.kind), []);
});

test('scanText allows seeded hostile data and the documented token help text', () => {
  const read = {
    text: 'doctor FAKE-<i data-xp=1>"\'\\&amp; and (tokens: ${contextPath} ${demographicId})',
    values: [],
    title: '',
  };
  assert.deepEqual(scanText(read), []);
});

const good = {
  route: '/x', status: 200, resourceType: 'document', contentType: 'text/html;charset=UTF-8',
  headers: {
    'x-frame-options': 'SAMEORIGIN', 'x-content-type-options': 'nosniff', 'cache-control': 'no-store',
    'content-security-policy': "frame-ancestors 'self'; base-uri 'self'; object-src 'none'",
  },
};

test('headerFindings accepts the front door header set', () => {
  assert.deepEqual(headerFindings(good, { https: true }), []);
});

test('headerFindings reports missing, duplicated and hazardous headers', () => {
  const bad = {
    ...good,
    contentType: 'text/html',
    headers: {
      'x-frame-options': 'SAMEORIGIN\nSAMEORIGIN', 'cache-control': 'max-age=60',
      'cross-origin-opener-policy': 'same-origin', server: 'Apache-Coyote/1.1',
      'set-cookie': 'JSESSIONID=1; Path=/carlos',
    },
  };
  const found = kinds(headerFindings(bad, { https: true }));
  for (const kind of ['header-duplicated', 'header-missing-nosniff', 'header-csp-frame-ancestors',
    'header-cache-no-store', 'header-no-charset', 'header-coop-severs-opener', 'header-disclosure', 'cookie-flags']) {
    assert.ok(found.includes(kind), `${kind} should be reported, got ${found.join(',')}`);
  }
});

test('the ledger keeps one entry per distinct defect and counts the pages', () => {
  const ledger = createLedger();
  ledger.suppress('known-kind', 'already filed');
  ledger.add('a', 'k', 'same');
  ledger.add('b', 'k', 'same');
  ledger.add('c', 'known-kind', 'x');
  assert.equal(ledger.list().length, 1);
  assert.equal(ledger.list()[0].count, 2);
  assert.deepEqual(ledger.suppressedSummary(), ['known-kind x1 (known: already filed)']);
});

test('entryFailures reports what the browser recorded since the entry began, and nothing before it', () => {
  const recorder = {
    pageErrors: [{ text: 'old error' }], consoleIssues: [], badResponses: [], requestFailures: [], unexpectedDialogs: [],
  };
  const probe = { offHost: [{ url: 'https://old.example/x', resourceType: 'image', mixed: false, from: '' }] };
  const ledger = createLedger();
  const entry = beginEntry({ recorder, probe });
  recorder.pageErrors.push({ text: 'ReferenceError: boom\n    at x' });
  recorder.requestFailures.push({ resourceType: 'image', errorText: 'net::ERR_BLOCKED_BY_CLIENT' });
  recorder.badResponses.push({ status: 500, resourceType: 'document' });
  probe.offHost.push({ url: 'https://new.example/y', resourceType: 'script', mixed: false, from: 'http://app/p' });
  const lines = entryFailures({ recorder, probe, ledger }, entry, 'entry');
  assert.deepEqual(lines, ['entry: uncaught ReferenceError: boom', 'entry: HTTP 500 on a document']);
  assert.equal(ledger.list().length, 1);
  assert.match(ledger.list()[0].detail, /new\.example/);
  assert.deepEqual(entryFailures({ recorder, probe, ledger }, entry, 'entry', ['uncaught ReferenceError: boom']),
    ['entry: HTTP 500 on a document']);
});
