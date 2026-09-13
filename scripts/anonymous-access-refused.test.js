/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const {
  LOGIN_REDIRECT, NOT_PROTECTED, REFUSED_STATUSES, printableRoute, resolveRoute, verdictFor,
} = require('./anonymous-access-refused-playwright-checks');

const SOURCE = fs.readFileSync(
  path.join(__dirname, 'anonymous-access-refused-playwright-checks.js'), 'utf8',
);
const LOGIN_FILTER = fs.readFileSync(path.join(
  __dirname, '..', 'src', 'main', 'java', 'io', 'github', 'carlos_emr', 'carlos', 'sec', 'LoginFilter.java',
), 'utf8');

const BASE = 'http://127.0.0.1:8080/carlos';

/*
 * This check asks whether an unauthenticated request can obtain patient data.
 * Its verdict function decides that, so each way it could be too permissive gets
 * a test -- a check that reports "refused" for a page that was served is worse
 * than not having it.
 */

test('the expected refusal is the one LoginFilter actually performs', () => {
  assert.match(LOGIN_FILTER, /sendRedirect\(contextPath \+ "\/logoutPage"\)/);
  assert.match('/carlos/logoutPage', LOGIN_REDIRECT);
  assert.deepEqual(REFUSED_STATUSES, [401, 403]);
});

test('a redirect to the login surface is a refusal', () => {
  assert.equal(verdictFor({ url: '/x' }, 302, '/carlos/logoutPage', '', 'FAKE-SMITH'), null);
  assert.equal(verdictFor({ url: '/x' }, 302, 'http://host/carlos/login', '', 'FAKE-SMITH'), null);
  assert.equal(verdictFor({ url: '/x' }, 401, '', '', 'FAKE-SMITH'), null);
  assert.equal(verdictFor({ url: '/x' }, 403, '', '', 'FAKE-SMITH'), null);
});

test('a redirect to somewhere ELSE is not a refusal', () => {
  // Bouncing an anonymous caller onward into the application is not refusing it.
  assert.match(
    verdictFor({ url: '/x' }, 302, '/carlos/provider/providercontrol', '', 'FAKE-SMITH'),
    /rather than to the login surface/,
  );
  assert.match(verdictFor({ url: '/x' }, 302, '', '', 'FAKE-SMITH'), /no Location header/);
});

test('a 200 is a failure even when the body looks harmless', () => {
  // "It only returned an empty panel" is still a page served without a session.
  const verdict = verdictFor({ url: '/x' }, 200, '', '<html><body></body></html>', 'FAKE-SMITH');
  assert.match(verdict, /answered HTTP 200 to a session-less request/);
  assert.match(verdict, /LoginFilter should/);
});

test('a 200 carrying the patient surname is reported as a bigger problem', () => {
  // "Reachable" and "returned a patient's name" are different sizes of finding,
  // and a run that conflates them buries the second.
  const verdict = verdictFor({ url: '/x' }, 200, '', 'Patient: fake-smith, John', 'FAKE-SMITH');
  assert.match(verdict, /contains the patient's surname/);
  assert.match(verdict, /patient data served to an unauthenticated caller/);
});

test('the surname is never printed, only named', () => {
  // The repo's rule: diagnostics name the field, never its content.
  const verdict = verdictFor({ url: '/x' }, 200, '', 'FAKE-SMITH', 'FAKE-SMITH');
  assert.ok(!verdict.includes('FAKE-SMITH'), `the message leaks the surname: ${verdict}`);
  assert.ok(!/surname\b.*[:=]\s*\S/.test(verdict.replace("patient's surname", '')));
});

test('a too-short needle cannot match by accident', () => {
  // A two-letter surname would match almost any HTML, turning every 200 into the
  // severe finding and hiding which ones really leak.
  assert.match(verdictFor({ url: '/x' }, 200, '', 'a page about ab', 'ab'), /LoginFilter should/);
  assert.match(verdictFor({ url: '/x' }, 200, '', 'a page', ''), /LoginFilter should/);
  // And main() refuses to run at all without a usable needle.
  assert.match(SOURCE, /Could not read the patient surname from the Master Record/);
});

test('a 404 is not an authentication finding, but it is not a pass either', () => {
  // Nothing was served either way, so reporting it as a failure would bury the
  // real ones -- but counting it as "refused" would let a run where every URL
  // was wrong report success. It gets its own outcome.
  assert.equal(verdictFor({ url: '/x' }, 404, '', '', 'FAKE-SMITH'), 'NOT_FOUND');
  assert.notEqual(verdictFor({ url: '/x' }, 404, '', '', 'FAKE-SMITH'), null);
});

test('a run where most routes 404 fails rather than reporting everything refused', () => {
  // The failure this suite keeps having to fix: a guard that runs, finds
  // nothing, and passes. If the URLs this check builds were wrong, every one
  // would 404 and "60 routes refused" would be a result about the check.
  assert.match(SOURCE, /probed\.length \* 2 > attempted/);
  assert.match(SOURCE, /proved nothing about authentication/);
});

test('a 500 IS a finding, because reaching an exception means getting past the gate', () => {
  assert.match(verdictFor({ url: '/x' }, 500, '', '', 'FAKE-SMITH'), /past the authentication gate/);
});

test('only the application\'s own URLs are probed', () => {
  assert.equal(resolveRoute({ href: '/carlos/admin/ViewAdmin' }, BASE), `${BASE}/admin/ViewAdmin`);
  assert.equal(resolveRoute({ href: 'https://evil.example/x' }, BASE), null, 'another origin is not ours to refuse');
  assert.equal(resolveRoute({ href: '/other-app/x' }, BASE), null, 'another context path on the same host');
  assert.equal(resolveRoute({ href: '' }, BASE), null);
  assert.equal(resolveRoute({ href: 'mailto:someone@example.invalid' }, BASE), null);
});

test('the login surface and static assets are excluded, each with a reason', () => {
  // They are not exceptions to the rule: they are supposed to answer an
  // anonymous request, and reporting them would make the check cry wolf.
  for (const rule of NOT_PROTECTED) {
    assert.equal(typeof rule.reason, 'string');
    assert.ok(rule.reason.length > 10, 'each exclusion must say why');
  }
  const excluded = (url) => NOT_PROTECTED.some((rule) => rule.match.test(url));
  assert.ok(excluded(`${BASE}/logoutPage`));
  assert.ok(excluded(`${BASE}/login`));
  assert.ok(excluded(`${BASE}/images/favicon.ico`));
  assert.ok(!excluded(`${BASE}/admin/ViewAdmin`), 'a real page must not be excluded');
  assert.ok(!excluded(`${BASE}/demographic/DemographicEdit?demographic_no=2`));
});

test('the anonymous half uses a context of its own', () => {
  // Reusing the authenticated context would carry the session cookie and test
  // nothing at all -- the failure that would make this check permanently green.
  assert.match(SOURCE, /browser\.newContext\(/);
  assert.match(SOURCE, /anonymous\.request\.get/);
  assert.ok(!/authed\.request\.get/.test(SOURCE), 'the probes must not run on the logged-in context');
});

test('the routes are catalogued from the UI, not listed or swept', () => {
  // Comments stripped first: the header explains at length that it is NOT a
  // struts-*.xml sweep, and matching that prose was this test failing on the
  // very sentence promising the thing it checks.
  const code = SOURCE.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '');
  assert.match(code, /catalogueLinks/);
  assert.ok(!/struts-.*\.xml/.test(code), 'a config sweep would answer a question about the config');
  assert.ok(!/'\/admin\/ViewAdmin'|"\/demographic\/DemographicEdit"/.test(code),
    'a hand-written route list would answer a question about the list');
});

test('the check only reads', () => {
  for (const statement of [/createSqlRunner/, /\bINSERT\b/i, /\bDELETE\b/i, /request\.post\(/]) {
    assert.ok(!statement.test(SOURCE), `this check may only issue GETs; found ${statement}`);
  }
});

/*
 * A security check that probes nothing must not report a pass.
 *
 * ANON_ROUTE_LIMIT=60 was applied as routes.slice(0, limit), so a caller who
 * set it to 0 -- the natural spelling of "no limit" -- probed zero routes,
 * collected zero failures and the check reported success. The same shape sat
 * behind the Administration panel: when the schedule offered no Administration
 * control, the catalogue silently came back with only the Master Record's few
 * links and a green result meant almost nothing had been tried.
 */
test('ANON_ROUTE_LIMIT=0 probes every route rather than none', () => {
  assert.match(SOURCE, /const selected = limit > 0 \? routes\.slice\(0, limit\) : routes;/);
  assert.match(SOURCE, /ANON_ROUTE_LIMIT=60\s+how many catalogued routes to probe; 0 probes them all/);
  assert.match(SOURCE, /Number\.isFinite\(limit\) && limit >= 0/,
    'a nonsense limit must fail the run, not silently become zero');
});

test('a capped run says so instead of reporting a full sweep', () => {
  // "60 refused" reads as a clean bill of health when 140 were catalogued.
  assert.match(SOURCE, /PARTIAL: \$\{routes\.length\} routes were catalogued/);
  assert.match(SOURCE, /partial: capped/);
});

test('the Administration panel is mandatory, and so is a plausible number of its links', () => {
  // ~120 of the routes this check exists to probe are the panel's. A catalogue
  // that quietly lost them leaves a green result covering almost nothing.
  assert.match(SOURCE, /The schedule offers no Administration control/);
  assert.match(SOURCE, /The Administration panel offered only \$\{adminLinks\.length\} link\(s\); it has around 120/);
});

test('the pages this check drives as a logged-in provider are read back', () => {
  // The catalogue is built by clicking through a real session. A page that
  // broke while cataloguing would otherwise go unreported.
  assert.match(SOURCE, /assertStrictPage\(recorder, \['login', 'administration', 'patient-search', 'master-record'\]\)/);
});

test('a bare onclick route resolves INSIDE the application, not beside it', () => {
  // new URL('DemographicEdit?x', 'http://h/carlos') is 'http://h/DemographicEdit'
  // -- URL treats the last path segment as a file. That failed the context
  // prefix test below and the route was dropped from the probe in silence, so
  // a check answering "can a stranger reach this?" was not asking about the
  // Master Record at all. The browser resolves against the DOCUMENT, and so
  // does this now.
  const page = `${BASE}/demographic/demographicsearchresults.jsp`;
  assert.equal(
    resolveRoute({ route: 'DemographicEdit?demographic_no=1', baseURI: page }, BASE),
    `${BASE}/demographic/DemographicEdit?demographic_no=1`,
  );
  assert.equal(
    resolveRoute({ route: '../encounter/IncomingEncounter', baseURI: page }, BASE),
    `${BASE}/encounter/IncomingEncounter`,
  );
});

test('without a recorded document the context path is still treated as a directory', () => {
  // The fallback for an item catalogued before baseURI existed. Beside-the-root
  // is the wrong answer either way; a directory is the faithful one.
  assert.equal(
    resolveRoute({ route: 'viewformwcb?formId=3' }, BASE),
    `${BASE}/viewformwcb?formId=3`,
  );
});

test('another host is still refused however it was resolved', () => {
  const page = `${BASE}/demographic/demographicsearchresults.jsp`;
  assert.equal(resolveRoute({ href: 'https://example.com/x', baseURI: page }, BASE), null);
  // And a path on the same host but outside the application's context.
  assert.equal(resolveRoute({ href: '/manager/html', baseURI: page }, BASE), null);
});

test('no diagnostic carries the query string a catalogued route came with', () => {
  // The catalogue is built by clicking through an AUTHENTICATED session, so its
  // Master Record and chart links carry the identifiers of whichever patient
  // this run opened (DemographicEdit?demographic_no=123). runCheck() writes
  // these messages to stdout and into RESULT_JSON, which CI archives.
  // CLAUDE.md counts demographic_no among the identifiers that join straight
  // back to a patient.
  const withId = { url: `${BASE}/demographic/DemographicEdit?demographic_no=123&appointmentNo=456` };
  const messages = [
    verdictFor(withId, 302, `${BASE}/somewhere?demographic_no=123`, '', 'FAKE-Smith'),
    verdictFor(withId, 500, '', '', 'FAKE-Smith'),
    verdictFor(withId, 200, '', 'a body', 'FAKE-Smith'),
    verdictFor(withId, 200, '', 'a body naming FAKE-Smith', 'FAKE-Smith'),
  ].filter(Boolean);
  assert.equal(messages.length, 4);
  for (const message of messages) {
    assert.ok(!/demographic_no|appointmentNo|123|456/.test(message),
      `a diagnostic carried a patient identifier: ${message}`);
    // The path is still there: it names the endpoint that answered.
    assert.match(message, /DemographicEdit|somewhere/);
  }
});

test('an empty Location still reads as an absent header, not as a redaction', () => {
  // printableRoute('') must stay empty so the caller's own fallback fires.
  assert.equal(printableRoute(''), '');
  const message = verdictFor({ url: `${BASE}/x` }, 302, '', '', 'FAKE-Smith');
  assert.match(message, /no Location header/);
});

test('a relative Location keeps its path and loses its query', () => {
  // Location headers are frequently relative, which does not parse as a URL on
  // its own -- and losing the path would throw away the only useful half.
  assert.equal(printableRoute('/carlos/logout?demographic_no=9'), '/carlos/logout');
});

test('the default run probes the whole catalogue, not a prefix of it', () => {
  // The default was 60. This check's claim is that NO clinician-reachable route
  // serves a session-less request, and a capped run cannot support it: the
  // routes past the cap were never probed, and the run still reported success.
  // The cap stayed visible in the message and in RESULT_JSON, which is better
  // than silence, but a partial security sweep should be something a person
  // asks for rather than something they have to notice they got.
  const source = require('node:fs').readFileSync(
    require.resolve('./anonymous-access-refused-playwright-checks'), 'utf8',
  );
  assert.match(source, /ANON_ROUTE_LIMIT \|\| '0'/);
  assert.ok(!/ANON_ROUTE_LIMIT \|\| '(?!0')\d+'/.test(source),
    'a non-zero default silently makes the default run partial');
  // 0 must still mean unlimited rather than "probe nothing".
  assert.match(source, /limit > 0 \? routes\.slice\(0, limit\) : routes/);
  // And an explicitly capped run must still say so.
  assert.match(source, /PARTIAL/);
});
