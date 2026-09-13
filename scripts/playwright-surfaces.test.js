/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const {
  SURFACES, describeEntry, entryStrategy, surfaceByName, surfacesForProvince,
} = require('./lib/playwright-surfaces');
const { assertSafeTarget, readBuildIdentity } = require('./run-playwright-suite');

const MANIFEST = JSON.parse(fs.readFileSync(path.join(__dirname, 'playwright-suite.json'), 'utf8'));

/*
 * The surface table is data that a browser check trusts completely: if a row
 * names a control that does not exist, the check fails on a live deployment and
 * nobody finds out until someone runs it. These tests pin the parts that can be
 * checked without a browser -- the shape of each row, and the fact that every
 * name the suite manifest selects is a row that exists.
 */

test('every surface names its control with exactly one strategy', () => {
  for (const surface of SURFACES) {
    assert.doesNotThrow(() => entryStrategy(surface), `${surface.name} must name exactly one of label/title/selector`);
    assert.ok(describeEntry(surface).length > 0);
  }
});

test('a label regex is never end-anchored, because a live count follows the label', () => {
  // <oscar:newLab>/<oscar:newTickler>/<oscar:newMessage> append "<sup>N</sup>"
  // INSIDE the anchor, so "Inbox" becomes "Inbox3" the moment a lab is waiting.
  // An end-anchored regex passes on an empty demo inbox and fails in the clinic.
  const countable = { 'tickler-surface': 'Tickler', 'messenger-surface': 'Msg', 'inbox-surface': 'Inbox' };
  for (const [name, label] of Object.entries(countable)) {
    const surface = surfaceByName(name);
    assert.ok(surface, `${name} must exist`);
    if (entryStrategy(surface) !== 'label') {
      continue;
    }
    assert.ok(!surface.entry.label.source.endsWith('$'),
      `${name}'s label regex is end-anchored; a "<sup>N</sup>" count would stop it matching`);
    assert.match(`${label}3`, surface.entry.label,
      `${name}'s label regex must still match "${label}3", the text with a count appended`);
  }
});

test('an optional surface says WHY it may be absent, so a skip is readable', () => {
  for (const surface of SURFACES) {
    if (!surface.optional) {
      continue;
    }
    assert.equal(typeof surface.optional, 'string',
      `${surface.name}: optional must carry the reason, not just true`);
    assert.ok(surface.optional.length > 20,
      `${surface.name}: name the property or module that gates it`);
  }
});

test('there is no Billing surface, because the schedule has no Billing control', () => {
  // Verified against appointmentprovideradminday.jsp: the only billing links on
  // the schedule are the per-appointment "B" badges, which are a workflow.
  // Keeping a row for a control that does not exist would fail every run.
  assert.equal(surfaceByName('billing-surface'), null);
});

test('every SURFACE the suite manifest selects is a surface that exists', () => {
  // The real guard against a rename: the manifest and the table are separate
  // files, and a stale name would have reported SKIP forever.
  const selected = MANIFEST.checks
    .filter((check) => check.envSet && check.envSet.SURFACE)
    .map((check) => ({ name: check.name, surface: check.envSet.SURFACE }));
  assert.ok(selected.length > 0, 'the manifest must register the surface checks individually');
  for (const entry of selected) {
    assert.ok(surfaceByName(entry.surface),
      `${entry.name} selects SURFACE=${entry.surface}, which is not in lib/playwright-surfaces.js`);
  }
});

test('a surface that lists a province is still returned for that province', () => {
  for (const surface of SURFACES) {
    const forIts = surfacesForProvince(surface.province === 'all' ? 'ON' : surface.province);
    assert.ok(forIts.some((candidate) => candidate.name === surface.name),
      `${surface.name} disappears from its own province`);
  }
});

/*
 * The runner's two safety rails. Both were previously shaped so they could never
 * fire, which is worse than not having them: the suite reported a guard it did
 * not actually apply.
 */

test('a non-local BASE_URL is refused even when nothing asserts the database', () => {
  // The old guard keyed on assertsDatabase, but assertsDatabase means "reads
  // rows back", not "writes". eform-admin-crud and friends create and delete
  // records through the UI with assertsDatabase false.
  const readOnlyLooking = [{ name: 'surface-audit:report-index', assertsDatabase: false }];
  assert.throws(
    () => assertSafeTarget(readOnlyLooking, { BASE_URL: 'https://carlos.example.org/carlos' }),
    /ALLOW_NON_LOCAL_BASE_URL/,
  );
});

test('a local BASE_URL needs no opt-in, and an explicit opt-in is honoured', () => {
  const checks = [{ name: 'x', assertsDatabase: true }];
  assert.doesNotThrow(() => assertSafeTarget(checks, { BASE_URL: 'http://127.0.0.1:8080/carlos' }));
  assert.doesNotThrow(() => assertSafeTarget(checks, {
    BASE_URL: 'https://carlos.example.org/carlos',
    ALLOW_NON_LOCAL_BASE_URL: 'true',
  }));
});

test('the deployment fingerprint is a validator, not an HTTP status', () => {
  // It returned %{http_code} before, so it was "200" on both sides of the run
  // and the comparison could never report a redeploy.
  const headers = [
    'HTTP/1.1 200',
    'ETag: W/"1406-1756713600000"',
    'Last-Modified: Mon, 01 Sep 2026 10:15:00 GMT',
    'Content-Length: 1406',
    '',
  ].join('\r\n');
  const identity = readBuildIdentity({ BASE_URL: 'http://127.0.0.1:8080/carlos' },
    () => ({ status: 0, stdout: headers }));
  assert.notEqual(identity, '200');
  assert.match(identity, /1756713600000/);
  assert.match(identity, /Last-Modified|GMT/);
});

test('the fingerprint is null rather than a guess when it cannot be read', () => {
  const args = { BASE_URL: 'http://127.0.0.1:8080/carlos' };
  assert.equal(readBuildIdentity(args, () => ({ status: 7, stdout: '' })), null);
  assert.equal(readBuildIdentity(args, () => ({ status: 0, stdout: 'HTTP/1.1 404\r\n\r\n' })), null);
  assert.equal(readBuildIdentity(args, () => ({ status: 0, stdout: 'HTTP/1.1 200\r\n\r\n' })), null);
  assert.equal(readBuildIdentity({}, () => ({ status: 0, stdout: 'HTTP/1.1 200\r\nETag: "x"\r\n' })), null);
});
