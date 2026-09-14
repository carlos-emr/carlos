/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const {
  SURFACES, describeEntry, entryStrategy, surfaceByName, surfacesForProvince,
} = require('./lib/playwright-surfaces');
const { assertSafeTarget, readBuildIdentity, runOne } = require('./run-playwright-suite');

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
});

test('an unset BASE_URL fingerprints the default deployment, not nothing', () => {
  // The runner's target gate defaults to http://127.0.0.1:8080/carlos, so a
  // devcontainer run with nothing exported DOES have a deployment under it.
  // Reading BASE_URL alone left that commonest case with the redeploy guard
  // silently disabled -- the guard ran, compared null to null, and passed.
  let requested = null;
  const identity = readBuildIdentity({}, (command, args) => {
    requested = args[args.length - 1];
    return { status: 0, stdout: 'HTTP/1.1 200\r\nETag: "x"\r\n' };
  });
  assert.equal(requested, 'http://127.0.0.1:8080/carlos/images/favicon.ico');
  assert.equal(identity, '"x"');
});

test('the runner refuses a remote MYSQL_HOST even when BASE_URL is local', () => {
  // Eleven checks still read MYSQL_HOST themselves instead of going through
  // createSqlRunner, so nothing else in the run validates it. A local BASE_URL
  // with a remote database would otherwise seed, update and delete rows there.
  assert.throws(
    () => assertSafeTarget([{ name: 'x', assertsDatabase: false }], {
      BASE_URL: 'http://127.0.0.1:8080/carlos',
      MYSQL_HOST: 'db.example.org',
    }),
    /non-loopback MYSQL_HOST/,
  );
  // The existing opt-in still works, and an unset MYSQL_HOST is not invented.
  assert.doesNotThrow(() => assertSafeTarget([{ name: 'x', assertsDatabase: false }], {
    BASE_URL: 'http://127.0.0.1:8080/carlos',
    MYSQL_HOST: 'db.example.org',
    ALLOW_NON_LOCAL_MYSQL_HOST: 'true',
  }));
  assert.doesNotThrow(() => assertSafeTarget([{ name: 'x', assertsDatabase: false }], {
    BASE_URL: 'http://127.0.0.1:8080/carlos',
  }));
});

test('certificate verification is waived for the build probe on loopback and private ranges, nowhere else', () => {
  // The wording matters, so it is exact: the policy is LOOPBACK AND RFC1918,
  // not "local". isLocalTlsTarget() covers both, because a devcontainer is as
  // often reached at a LAN address as at 127.0.0.1 and both serve the same
  // self-signed certificate -- without the waiver the restart guard is disabled
  // on every such run. The 192.168.1.20 case below is that branch, and an
  // earlier version of this test called it "local", which read as though only
  // loopback were waived.
  //
  // The residual is worth stating rather than glossing: on a private range the
  // certificate is unverified, so a host on that network could supply this
  // fingerprint. That is accepted for a disposable dev target and is why the
  // waiver stops there -- for a public host, reached under
  // ALLOW_NON_LOCAL_BASE_URL=true, "skip verification" would mean the guard can
  // be satisfied by anyone on the path, and a guard that proves nothing is
  // worse than none.
  const headers = 'HTTP/1.1 200\r\nETag: "abc"\r\n\r\n';
  const flagsFor = (base) => {
    let seen = null;
    readBuildIdentity({ BASE_URL: base }, (command, args) => {
      seen = args[0];
      return { status: 0, stdout: headers };
    });
    return seen;
  };
  assert.equal(flagsFor('https://127.0.0.1:8443/carlos'), '-sSkI', 'loopback: waived');
  assert.equal(flagsFor('https://192.168.1.20:8443/carlos'), '-sSkI', 'RFC1918: waived, deliberately');
  assert.equal(flagsFor('https://emr.example.com/carlos'), '-sSI', 'public host: verified');
  // A name that merely LOOKS private is not private -- the host must parse as
  // four numeric octets before the range test, or 10.example.com would be
  // waived. That is the #3598 boundary, pinned here at the probe as well.
  assert.equal(flagsFor('https://10.example.com/carlos'), '-sSI', 'a private-looking NAME is still public');
});

test('an unparseable BASE_URL disables the build probe rather than guessing', () => {
  let called = false;
  const identity = readBuildIdentity({ BASE_URL: 'not a url' }, () => {
    called = true;
    return { status: 0, stdout: 'HTTP/1.1 200\r\nETag: "abc"\r\n\r\n' };
  });
  assert.equal(identity, null);
  assert.equal(called, false, 'nothing should be fetched from a target that cannot be parsed');
});

test('each check runs against the environment the target gate validated', () => {
  // main() validates BASE_URL and MYSQL_HOST out of the env it was HANDED, but
  // runOne started each child from process.env -- so a caller passing an
  // explicit environment gated one deployment and ran the check against
  // another, which is precisely the disassociation assertSafeTarget exists to
  // prevent.
  let childEnv = null;
  runOne(
    { name: 'probe', script: 'scripts/probe-playwright-checks.js', timeoutSec: 5 },
    { env: { BASE_URL: 'http://gated.test/carlos', MYSQL_HOST: '127.0.0.1' } },
    (command, args, spawnOptions) => { childEnv = spawnOptions.env; return { status: 0 }; },
  );
  assert.equal(childEnv.BASE_URL, 'http://gated.test/carlos');
  assert.equal(childEnv.MYSQL_HOST, '127.0.0.1');
});

test('an envSet entry still overrides the inherited environment', () => {
  // The table-driven families (surface-audit, direct-response-contract) select
  // which surface a shared script runs by envSet, so it has to win.
  let childEnv = null;
  runOne(
    { name: 'surface-audit:inbox', script: 'scripts/surface-audit-playwright-checks.js', timeoutSec: 5, envSet: { SURFACE: 'inbox' } },
    { env: { BASE_URL: 'http://gated.test/carlos', SURFACE: 'report' } },
    (command, args, spawnOptions) => { childEnv = spawnOptions.env; return { status: 0 }; },
  );
  assert.equal(childEnv.SURFACE, 'inbox');
  assert.equal(childEnv.BASE_URL, 'http://gated.test/carlos');
});

/*
 * THE PER-SURFACE BUDGET.
 *
 * SURFACE_LIMIT bounds how many items each surface audits. Number('-1') is -1,
 * which is truthy, and auditCatalogue breaks out before clicking the first item
 * once a set limit is reached -- so SURFACE_LIMIT=-1 audited nothing at all and
 * the run still reported success, because the only other gate is a minimum on
 * items.length that is satisfied before any clicking happens. A malformed budget
 * must not be able to manufacture a green run.
 */
const { surfaceLimitFrom } = require('./surface-audit-playwright-checks');

test('an absent or empty budget means unlimited', () => {
  assert.equal(surfaceLimitFrom(undefined), 0);
  assert.equal(surfaceLimitFrom(''), 0);
  assert.equal(surfaceLimitFrom('0'), 0);
  // Whitespace is Number(' ') === 0, so it lands on unlimited rather than being
  // refused. That is the safe direction: unlimited audits everything, and the
  // failure this validation exists to stop is a budget that audits NOTHING while
  // the run still reports success.
  assert.equal(surfaceLimitFrom(' '), 0);
});

test('a real budget is read as written', () => {
  assert.equal(surfaceLimitFrom('3'), 3);
});

test('a budget that would silently audit nothing is refused', () => {
  for (const raw of ['-1', '-10', 'abc', '1.5', 'Infinity', 'NaN', '2e3x']) {
    assert.throws(() => surfaceLimitFrom(raw), /SURFACE_LIMIT must be a non-negative whole number/,
      `SURFACE_LIMIT=${raw} must fail loudly rather than quietly auditing nothing`);
  }
});

/*
 * THE TWO eCHART NAVBARS, COUNTED SEPARATELY.
 *
 * #leftNavBar and #rightNavBar are filled by separate AJAX module groups, so
 * losing one is exactly the half-broken chart a clinician reports: no Allergies
 * or Prescriptions where they expect them. The wait summed the two counts, so a
 * populated left navbar masked a completely empty right one and the check went
 * on to pass. A sum could only ever catch both failing at once -- the one case
 * that is obvious anyway.
 */
const { waitForNavbars } = require('./echart-navbar-modules-playwright-checks');

/**
 * A chartPage double that runs the real browser-side predicate against a fake
 * DOM, so the test exercises the predicate itself rather than a paraphrase of it.
 *
 * @param counts links present in each container, e.g. { leftNavBar: 4, rightNavBar: 0 }
 */
function fakeChartPage(counts) {
  const document = {
    getElementById: (id) => (id in counts
      ? { querySelectorAll: () => ({ length: counts[id] }) }
      : null),
  };
  return {
    locator: () => ({ first: () => ({ waitFor: async () => {} }) }),
    waitForFunction: async (fn) => {
      const previous = global.document;
      global.document = document;
      try {
        const value = fn();
        // Playwright polls until the predicate returns something truthy and
        // rejects on timeout; null here is that timeout.
        if (!value) throw new Error('timeout');
        return { jsonValue: async () => value };
      } finally {
        if (previous === undefined) delete global.document; else global.document = previous;
      }
    },
  };
}

test('both navbars populated is the only passing state', async () => {
  await waitForNavbars(fakeChartPage({ leftNavBar: 6, rightNavBar: 4 }), 100);
});

test('a populated left navbar does not excuse an empty right one', async () => {
  await assert.rejects(
    () => waitForNavbars(fakeChartPage({ leftNavBar: 6, rightNavBar: 0 }), 100),
    /do not BOTH contain links/,
    'summing the two counts is what let this pass: 6 + 0 is still more than zero');
});

test('a populated right navbar does not excuse an empty left one', async () => {
  await assert.rejects(
    () => waitForNavbars(fakeChartPage({ leftNavBar: 0, rightNavBar: 4 }), 100),
    /do not BOTH contain links/);
});

test('a navbar container missing from the page altogether is a failure, not a skip', async () => {
  await assert.rejects(
    () => waitForNavbars(fakeChartPage({ leftNavBar: 6 }), 100),
    /do not BOTH contain links/);
});
