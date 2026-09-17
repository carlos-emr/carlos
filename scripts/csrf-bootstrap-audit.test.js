/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const {
  auditSource, auditWebapp, hasActionlessForm,
  hasRealPostForm, isSelfContained, jspFiles,
} = require('./lib/csrf-bootstrap-audit');

const CLAUDE_MD = fs.readFileSync(path.join(__dirname, '..', 'CLAUDE.md'), 'utf8');
const CARLOS_AJAX = fs.readFileSync(path.join(
  __dirname, '..', 'src', 'main', 'webapp', 'share', 'javascript', 'carlos-ajax.js',
), 'utf8');

/** carlos-ajax.js with its comments removed, so a mention of fetch() in prose is not a call. */
function codeOnly(source) {
  return source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '');
}

/*
 * CLAUDE.md's CSRF bootstrapping rule had no enforcement at all, which is how a
 * documented rule quietly becomes a documented aspiration. This is the
 * enforcement, and it runs on every pull request because it needs no deployment.
 *
 * THE FAILURE MODE TO GUARD AGAINST IS THE AUDIT FINDING NOTHING. A lint whose
 * pattern is broken reports a clean webapp and is worse than no lint, because it
 * is believed. Two of the tests below exist because exactly that happened while
 * this was being written: the selector pattern stopped at the inner quote of
 * 'input[name="CSRF-TOKEN"]' and matched zero of 1,031 files, and the form
 * pattern stopped inside a <%= %> expression and reported labDisplay.jsp -- the
 * reference implementation CLAUDE.md itself cites -- as a violation.
 */

test('the rule this enforces is the one CLAUDE.md states', () => {
  assert.match(CLAUDE_MD, /CSRF Token Bootstrapping on AJAX JSPs/);
  assert.match(CLAUDE_MD, /WEB-INF\/jspf\/csrf-token\.jspf/);
  assert.match(CLAUDE_MD, /empty placeholder form/i);
});

test('the audit is not vacuous: it finds the pages the rule applies to', () => {
  // If a pattern breaks, this is what catches it. The count is a floor, not a
  // pin: new AJAX pages should raise it, and a drop to zero means the scanner
  // stopped working rather than the webapp getting simpler.
  const { applicable } = auditWebapp();
  assert.ok(applicable.length >= 20,
    `only ${applicable.length} page(s) matched "reads the CSRF token AND posts over AJAX"; `
    + 'the detector is probably broken rather than the webapp having changed that much');
});

test('no page violates the rule', () => {
  // No baseline and no burn-down: every page the rule governs satisfies it
  // today, so a new violation fails the build on the page that introduced it.
  const { violations, unattributed } = auditWebapp();
  assert.deepEqual(violations.map((entry) => `${entry.file}: ${entry.reason}`), [],
    'a page reads input[name="CSRF-TOKEN"] for an AJAX POST but nothing populates it, so those requests are '
    + 'answered with an HTML error page and fail inside a catch the user never sees');
  assert.deepEqual(unattributed.map((entry) => entry.file), [],
    'a fragment could not be attributed to any page that renders it, so the audit cannot say whether the rule '
    + 'holds for it: that needs a human, not a guess');
});

test('a page that posts only through the shared helper is outside the rule', () => {
  // The audit once judged these pages and reported six violations (issue
  // #3665, finding 10). CarlosAjax sends with XMLHttpRequest so that
  // CSRFGuard's injected script puts the CSRF-TOKEN header on every send; the
  // hidden input the helper also reads is a second copy of the token, not the
  // only one. Judging such a page demanded a bootstrap it does not need.
  const viaHelper = `
    <html><body>
    <script src="/carlos/share/javascript/carlos-ajax.js"></script>
    <script>CarlosAjax.request(url, { parameters: { a: 1 } });</script>
    </body></html>`;
  assert.equal(auditSource('helper.jsp', viaHelper), null,
    'a page whose only AJAX write goes through CarlosAjax has nothing to bootstrap');
  // The method does not matter: it is the transport that takes it out of scope.
  const getOnly = '<html><body><script>CarlosAjax.request("/carlos/lookup", { method: "GET" });</script></body></html>';
  assert.equal(auditSource('get-only.jsp', getOnly), null);
  // A page that reads the token ITSELF and sends it over fetch() is still in.
  const itself = `
    <html><body><script>
      var t = document.querySelector('input[name="CSRF-TOKEN"]').value;
      CarlosAjax.request(url, { parameters: { a: 1 } });
      fetch('/carlos/x', { method: 'POST', headers: { 'CSRF-TOKEN': t } });
    </script></body></html>`;
  const verdict = auditSource('itself.jsp', itself);
  assert.ok(verdict, 'reading the input for a fetch() is the rule\'s own case, whatever else the page does');
  assert.equal(verdict.satisfied, false);
});

test('the exclusion rests on the helper using XMLHttpRequest, so that is pinned', () => {
  // The day carlos-ajax.js sends with fetch(), CSRFGuard's interceptor no
  // longer sees its requests and every helper caller IS governed by the rule
  // again. This is the fact the exclusion above depends on, in the helper's
  // own words and in its code.
  assert.match(CARLOS_AJAX, /new XMLHttpRequest\(\)/, 'the helper must send with XMLHttpRequest');
  assert.match(CARLOS_AJAX, /CSRFGuard[^\n]*interceptor/i, 'the helper must say why it uses XMLHttpRequest');
  assert.doesNotMatch(codeOnly(CARLOS_AJAX), /\bfetch\s*\(/,
    'carlos-ajax.js calls fetch(); CSRFGuard cannot intercept that, so widen the audit back to helper callers');
  assert.match(CLAUDE_MD, /fetch\(\)`?\s+calls\s+are\s+\*\*not\*\*\s+hijacked/,
    'CLAUDE.md must keep stating which transport the rule is for');
});

test('a form action must be a real url, not a fragment or a script', () => {
  // `[^"']+` accepted all of these, so hasRealPostForm() could call a page
  // compliant while CSRFGuard injected into nothing.
  assert.equal(hasRealPostForm('<form action="/carlos/x" method="post"></form>'), true);
  assert.equal(hasRealPostForm('<form action="  /carlos/x" method="post"></form>'), true);
  assert.equal(hasRealPostForm('<form action="#" method="post"></form>'), false);
  assert.equal(hasRealPostForm('<form action="#tab" method="post"></form>'), false);
  assert.equal(hasRealPostForm('<form action="javascript:save()" method="post"></form>'), false);
  assert.equal(hasRealPostForm('<form action="   " method="post"></form>'), false);
  // The shapes CARLOS really writes must keep passing.
  assert.equal(hasRealPostForm('<form action="<%= request.getContextPath() %>/x" method="post"></form>'), true);
  assert.equal(hasRealPostForm('<form action="${pageContext.request.contextPath}/x" method="post"></form>'), true);
});

test('a page that reads the token with no form and no bootstrap is a violation', () => {
  const verdict = auditSource('fake.jsp', `
    <html><body>
    <script>
      var el = document.querySelector('input[name="CSRF-TOKEN"]');
      fetch('/carlos/thing', { method: 'POST', headers: { 'CSRF-TOKEN': el.value } });
    </script>
    </body></html>
  `);
  assert.equal(verdict.satisfied, false);
  assert.match(verdict.reason, /token is sent empty/);
});

test('the empty-placeholder anti-pattern gets its own message', () => {
  // "Add a form" is the wrong advice for someone who already thinks they have
  // one. CSRFGuard skips a form with no action, so the input is never populated.
  const verdict = auditSource('fake.jsp', `
    <html><body>
    <form id="csrfForm" style="display:none;"></form>
    <script>
      var el = document.querySelector('input[name="CSRF-TOKEN"]');
      fetch('/carlos/thing', { method: 'POST' });
    </script>
    </body></html>
  `);
  assert.equal(verdict.satisfied, false);
  assert.match(verdict.reason, /CSRFGuard skips action-less forms/);
});

test('a real POST form satisfies the rule, including one built from JSP expressions', () => {
  // The regression that reported the reference implementation as broken: a
  // CARLOS <form> tag spans lines and its attribute values contain ">".
  const realWorld = `
    <form name="reassignForm_<carlos:encode value='<%= segmentID %>' context="htmlAttribute"/>"
          method="post"
          action="<%= request.getContextPath() %>/lab/CA/ALL/Forward">
    </form>
  `;
  assert.equal(hasRealPostForm(realWorld), true);
  assert.equal(hasRealPostForm('<form id="csrfForm" style="display:none;"></form>'), false);
  // A GET form is not one CSRFGuard injects into.
  assert.equal(hasRealPostForm('<form action="/carlos/search" method="get"></form>'), false);
});

test('two adjacent forms cannot lend each other half the requirement', () => {
  // One form with an action and a different one with a method is not a form
  // CSRFGuard will inject into, and a window that ran past the first tag would
  // say it was.
  const two = '<form action="/carlos/a"></form>\n<form method="post"></form>';
  assert.equal(hasRealPostForm(two), false);
});

test('the reference implementation CLAUDE.md cites passes', () => {
  // If this ever fails, either the reference changed or the detector did; both
  // are worth stopping for.
  assert.match(CLAUDE_MD, /labDisplay\.jsp/);
  const labDisplay = fs.readFileSync(path.join(
    __dirname, '..', 'src', 'main', 'webapp', 'WEB-INF', 'jsp', 'lab', 'CA', 'ALL', 'labDisplay.jsp',
  ), 'utf8');
  const verdict = auditSource('labDisplay.jsp', labDisplay);
  assert.ok(verdict, 'the reference implementation must be a page the rule applies to');
  assert.equal(verdict.satisfied, true, verdict.reason);
});

test('a page that neither reads the token nor posts over AJAX is not judged', () => {
  // The rule is about pages that read the input for a fetch/XHR. Reporting on
  // anything else would bury the real findings.
  assert.equal(auditSource('plain.jsp', '<html><body><p>hello</p></body></html>'), null);
  assert.equal(auditSource('form-only.jsp', '<form action="/x" method="post"></form>'), null);
  assert.equal(
    auditSource('ajax-no-token.jsp', '<script>fetch("/x", {method:"POST"});</script>'),
    null,
  );
});

test('a fragment is judged by the pages that render it, not on its own', () => {
  // A page with no <html> has no document to put a form in. rx/ListDrugs.jsp is
  // fetched by SearchDrug3.jsp and injected into its drugProfile element, so the
  // token input it reads is SearchDrug3's.
  const fragment = fs.readFileSync(path.join(
    __dirname, '..', 'src', 'main', 'webapp', 'WEB-INF', 'jsp', 'rx', 'ListDrugs.jsp',
  ), 'utf8');
  assert.equal(isSelfContained(fragment), false);
  const onItsOwn = auditSource('ListDrugs.jsp', fragment);
  assert.equal(onItsOwn.satisfied, false, 'on its own it cannot satisfy the rule');

  const { applicable } = auditWebapp();
  const attributed = applicable.find((entry) => entry.file.endsWith('rx/ListDrugs.jsp'));
  assert.ok(attributed, 'the fragment must still appear in the audit');
  assert.equal(attributed.satisfied, true);
  assert.match(attributed.reason, /rendered into .*SearchDrug3\.jsp/);
});

test('every JSP in the webapp is scanned, not a subdirectory of them', () => {
  const files = jspFiles();
  assert.ok(files.length > 900, `expected the whole webapp, found ${files.length} files`);
  assert.ok(files.some((file) => file.endsWith('.jspf')), 'fragments must be scanned too');
});

test('a form whose action follows a JSP expression is not called action-less', () => {
  // The same [^>]* trap this audit documents for hasRealPostForm: a `>` inside
  // <%= ... %> ends the character class before the scan reaches `action=`. The
  // verdict is unsatisfied either way here, but the REASON is what a maintainer
  // acts on -- and "remove your empty placeholder form" sends them looking for
  // something that is not there.
  const realAction = '<form id="<%= "a>b" %>" action="/carlos/foo" method="get">';
  assert.equal(hasActionlessForm(realAction), false);
});

test('the empty-placeholder anti-pattern is still caught', () => {
  // CLAUDE.md names this one specifically: CSRFGuard skips action-less forms,
  // so the hidden input is never populated and every AJAX POST is rejected.
  assert.equal(hasActionlessForm('<form id="csrfForm" style="display:none;"></form>'), true);
});

test('one form with an action does not excuse a second without one', () => {
  const both = '<form action="/carlos/save" method="post"></form>\n<form id="csrfForm"></form>';
  assert.equal(hasActionlessForm(both), true);
  assert.equal(hasRealPostForm(both), true);
});


/*
 * The pages finding 10 named are the live regression for this exclusion: the
 * static half here says they are out of scope, and
 * scripts/csrf-xhr-token-playwright-checks.js proves on a deployment that their
 * POSTs carry the token header and are accepted. Both halves name the same
 * pages so a reader can go from one to the other.
 */
test('the browser check covers the whole pages the static audit stopped judging', () => {
  const check = fs.readFileSync(path.join(__dirname, 'csrf-xhr-token-playwright-checks.js'), 'utf8');
  assert.match(check, /Pending Docs/, 'documentsInQueues.jsp is reached as Pending Docs');
  assert.match(check, /Row Display/, 'CumulativeLabValues.jsp is reached as the Labs menu\'s Row Display');
  assert.match(check, /openChart\(/, 'newEncounterLayout.jsp is the chart');
  assert.match(check, /allHeaders\(\)/, 'the token must be read off the wire, not out of the DOM');
  assert.match(check, /csrf-token/, 'the header CSRFGuard validates');
});
