# PR #5495 Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make PR #5495 mergeable against the current `release/2026.08` and give the four response wrappers one documented `resetBuffer()` rule, with no silent notice omission and no test-suite state leaks.

**Architecture:** The four wrappers stay independent decorators (no capture-ownership redesign). The work is: a base re-merge with conflict resolution, one rule change applied to the CSRF and privacy wrappers plus doc, a request-scoped notice flag, two small de-duplications, and test hygiene. Every behavioural change gets a failing unit test first and, where a dispatch is involved, an embedded-Tomcat case in the existing fixture.

**Tech Stack:** Java 25, Jakarta Servlet 6.1 on Tomcat 11, JUnit 5 + AssertJ + Spring `MockHttpServletResponse`, embedded Tomcat fixture `ResponseFilterLifecycleIntegrationTest`.

**Spec:** `docs/superpowers/plans/2026-11-04-pr-5495-review.md`

## Global Constraints

- Branch: all commits go on the PR branch `fix/5487-response-filter-lifecycle` (base `release/2026.08`). No rebase, no history rewrite; the base comes in as a merge commit. If executed from a Claude session that may only push `claude/review-pr-5495-filters-xx3sxf`, do the work there and tell the owner to merge it into the PR branch.
- Commits: Conventional Commits, DCO sign-off (`git commit -s`), no model identifiers in commit text.
- Test names: `should<Action>_<context><Condition>`, exactly one underscore, suffix starts lowercase.
- Focused suite command (from the lifecycle doc), referred to below as **FOCUSED**:
  `mvn test -Dtest=ResponseFilterLifecycleIntegrationTest,JavaMelodyResponseIntegrationTest,ResponseSanitizationFilterUnitTest,PrivacyStatementAppendingFilterUnitTest,CsrfGuardScriptInjectionFilterUnitTest,LogoutBroadcastFilterUnitTest,RequestNegotiationUnitTest -Dtest.forkCount=1`
- Do not touch `database/**`, `.github/**`, the two deployment descriptors' attribute values, or `web.xml` filter mappings.
- OWASP/PHI rules do not apply: no user input or patient data is handled by these changes.

## Review Focus

Inputs the spec implies but no existing test exercises; each is pinned to a task below.

1. Stale pending length before a forward with no channel open: expect the forwarded body complete, not framed by the pre-forward number (Task 2, integration case `staleLength`).
2. Two requests in one session, interleaved: expect both printable pages to carry the notice (Task 3).
3. `Content-Type: TEXT/HTML` (upper case) reaching the privacy filter: expect the notice appended (Task 4).
4. Response charset name that is illegal, unsupported, empty or null: expect UTF-8, never an exception (Task 4).
5. CsrfGuard state after the embedded-Tomcat class finishes: expect `isEnabled()` false for later tests in the same fork (Task 5).

---

### Task 1: Re-merge `release/2026.08` and resolve the overlap with #5618

**Files:**
- Modify: `src/main/java/io/github/carlos_emr/carlos/app/CsrfGuardScriptInjectionFilter.java` (conflict in `CaptureResponseWrapper.reset()`)
- Modify: `src/main/java/io/github/carlos_emr/carlos/app/LogoutBroadcastFilter.java` (conflict in `DelegatingServletResponse.reset()`)
- Modify: `src/main/java/io/github/carlos_emr/carlos/utility/ResponseSanitizationFilter.java` (conflict in `CapturingResponseWrapper.reset()`)
- Modify: `src/main/java/io/github/carlos_emr/carlos/commn/printing/PrivacyStatementAppendingFilter.java` (auto-merged duplicate `reset()`)

**Interfaces:**
- Produces: a branch whose head contains `c56b019a49` and compiles. Later tasks edit the PR-side method bodies named below.

- [ ] **Step 1: Confirm the conflict set before merging**

Run: `git fetch origin release/2026.08 && git merge-tree --write-tree origin/release/2026.08 HEAD | head -5`
Expected: the three `.java` files above listed as conflicted; the privacy filter absent from the list.

- [ ] **Step 2: Merge the base**

Run: `git merge --no-ff origin/release/2026.08`
Expected: `CONFLICT (content)` for the three files; the merge stops.

- [ ] **Step 3: Resolve `CsrfGuardScriptInjectionFilter.java`**

Keep the PR side (`resetBuffer()` override followed by `reset()`). The base's `reset()` body is identical to the PR's; delete the base copy and both marker lines.

- [ ] **Step 4: Resolve `LogoutBroadcastFilter.java`**

Keep the PR side of `reset()`: `super.reset()` first, then `clearDeferredContentLength()`, `publishedContentLength = null`, `writer = null`, both `obtained` flags false, both `htmlInjectionBuffer*` flags false. Drop the `chainComplete = false;` line (spec finding 4). Keep the base's comment line "A rejected reset must leave both the delegate and our cached state intact."

- [ ] **Step 5: Resolve `ResponseSanitizationFilter.java` as the union**

`reset()` body, in this order: `super.reset();` `discardCapturedOutput();` then `switchingWriter = null; switchingOutputStream = null; shieldedPassthroughStream = null; writer = null; usingWriter = false; usingOutputStream = false; outputStreamPassthrough = false; committed = false;`. Keep the base's Javadoc sentence about full reset permitting a writer/stream switch.

- [ ] **Step 6: Remove the duplicate `reset()` in `PrivacyStatementAppendingFilter.java`**

Run: `grep -n 'public void reset()' src/main/java/io/github/carlos_emr/carlos/commn/printing/PrivacyStatementAppendingFilter.java`
Expected before: two lines. Delete the method that follows `getWriter()` (the #5618 copy, three field clears). Keep the one after `resetBuffer()` (clears `deferredContentLength` and `publishedContentLength` too).
Expected after: one line.

- [ ] **Step 7: Compile and run the focused suite, including the 258 lines of reset tests #5618 added to `ResponseSanitizationFilterUnitTest`**

Run: **FOCUSED**
Expected: `BUILD SUCCESS`, 0 failures, 0 errors.

- [ ] **Step 8: Commit the merge**

```bash
git add -A
git commit -s -m "Merge branch 'release/2026.08' into fix/5487-response-filter-lifecycle

Resolves the overlap with #5618 (c56b019a49): the PR's reset() bodies are
kept as supersets, the sanitizer takes the union, and the privacy wrapper's
duplicate reset() from the automatic merge is removed."
```

---

### Task 2: One `resetBuffer()` rule for every wrapper

Rule (from the spec): on an **accepted** `resetBuffer()`, a wrapper drops a pending length and removes a length it published, because both described the discarded body. A rejected reset changes nothing. `reset()` clears everything. The logout wrapper already does this; the CSRF and privacy wrappers currently keep the pending value.

**Files:**
- Modify: `src/main/java/io/github/carlos_emr/carlos/app/CsrfGuardScriptInjectionFilter.java` (`CaptureResponseWrapper.resetBuffer()`)
- Modify: `src/main/java/io/github/carlos_emr/carlos/commn/printing/PrivacyStatementAppendingFilter.java` (`DelegatingServletResponse.resetBuffer()`)
- Modify: `src/main/java/io/github/carlos_emr/carlos/app/LogoutBroadcastFilter.java` (`resetBuffer()` Javadoc only)
- Modify: `docs/response-filter-lifecycle.md` (paragraph "A `resetBuffer()` keeps a length that is still pending…")
- Test: `src/test/java/io/github/carlos_emr/carlos/app/CsrfGuardScriptInjectionFilterUnitTest.java` (`shouldPreserveDeferredLength_onBufferResetButClearItOnFullReset`)
- Test: `src/test/java/io/github/carlos_emr/carlos/commn/printing/PrivacyStatementAppendingFilterUnitTest.java` (same name)
- Test: `src/test/java/io/github/carlos_emr/carlos/utility/ResponseFilterLifecycleIntegrationTest.java` (`ContentServlet.doGet`, new parameterized test)

**Interfaces:**
- Consumes: Task 1's merged branch.
- Produces: no new public API. The fixture gains request parameter `staleLength=true` on `/forward` and `/nested`.

- [ ] **Step 1: Invert the two unit tests**

In both suites rename `shouldPreserveDeferredLength_onBufferResetButClearItOnFullReset` to `shouldDropPendingLength_onBufferResetAndOnFullReset` and change the header assertion to:

```java
assertThat(response.getHeader("Content-Length")).as("fullReset=%s", fullReset).isNull();
```

Keep the byte assertion `containsExactly(0, 1, 2, 3)`.

- [ ] **Step 2: Add the stale-length forward case to the fixture**

In `ContentServlet.doGet`, inside the `"/nested".equals(path) || "/forward".equals(path)` branch, before the prefix writes:

```java
if ("true".equals(request.getParameter("staleLength"))) {
    response.setContentType("text/plain;charset=UTF-8");
    response.setContentLength(10);
} else if ("stream".equals(initial)) { ... existing prefix writes unchanged ... }
```

So with `staleLength=true` no channel is opened before the forward. Add:

```java
static Stream<Arguments> staleLengthCases() {
    return Stream.of("/development", "/packaged").flatMap(context ->
            Stream.of("forward", "nested").map(dispatch -> Arguments.of(context, dispatch)));
}

@ParameterizedTest(name = "stale length {0} {1}")
@MethodSource("staleLengthCases")
void shouldNotFrameForwardedBody_withLengthDeclaredBeforeTheForward(String context, String dispatch) throws Exception {
    HttpResponse<byte[]> response = get(context + "/" + dispatch + "?output=plain&staleLength=true&size=8193");
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo(html(8193).getBytes(StandardCharsets.UTF_8));
    assertLength(response);
}
```

- [ ] **Step 3: Run the three tests to verify they fail**

Run: `mvn test -Dtest=CsrfGuardScriptInjectionFilterUnitTest#shouldDropPendingLength*,PrivacyStatementAppendingFilterUnitTest#shouldDropPendingLength*,ResponseFilterLifecycleIntegrationTest#shouldNotFrameForwardedBody* -Dtest.forkCount=1`
Expected: FAIL. Unit tests: `Content-Length` is `"4"` for `fullReset=false`. Integration: body is 10 bytes (or the read times out) instead of 8193.

- [ ] **Step 4: Change `CaptureResponseWrapper.resetBuffer()`**

After `super.resetBuffer()` and the `captureWriter.reset()` block, set `deferredContentLength = null; deferredContentLengthLong = null;` and replace the trailing comment with one sentence stating the rule. (A length already passed through in stream/passthrough mode lives in the real response; Tomcat keeps it across `resetBuffer()`, which is the logout/privacy wrappers' job to retract, so nothing else changes here.)

- [ ] **Step 5: Change the privacy `DelegatingServletResponse.resetBuffer()`**

After `super.resetBuffer()`: `deferredContentLength = null;` then the existing published-length removal. Rewrite the comment to the rule.

- [ ] **Step 6: Align the logout `resetBuffer()` Javadoc**

Replace the paragraph so it states the shared rule in the same words as the doc; no code change.

- [ ] **Step 7: Update `docs/response-filter-lifecycle.md`**

Replace the sentence beginning "A `resetBuffer()` keeps a length that is still pending" with: "On an accepted `resetBuffer()` every decorator drops a pending length and removes one it published: both described the discarded body, and a forward always replaces the body. A rejected reset leaves all state unchanged; `reset()` clears everything. This is stricter than the bare container, which keeps the header." Add one bullet under "Regression coverage": "A non-HTML length declared before a forward with no channel open never frames the forwarded body."

- [ ] **Step 8: Run the focused suite**

Run: **FOCUSED**
Expected: `BUILD SUCCESS`; the three tests from Step 3 pass; `LogoutBroadcastFilterUnitTest#shouldClearDeferredContentLength_whenDownstreamResetsBuffer` still passes.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/io/github/carlos_emr/carlos/app/CsrfGuardScriptInjectionFilter.java \
  src/main/java/io/github/carlos_emr/carlos/commn/printing/PrivacyStatementAppendingFilter.java \
  src/main/java/io/github/carlos_emr/carlos/app/LogoutBroadcastFilter.java \
  docs/response-filter-lifecycle.md src/test/java/io/github/carlos_emr/carlos/app/CsrfGuardScriptInjectionFilterUnitTest.java \
  src/test/java/io/github/carlos_emr/carlos/commn/printing/PrivacyStatementAppendingFilterUnitTest.java \
  src/test/java/io/github/carlos_emr/carlos/utility/ResponseFilterLifecycleIntegrationTest.java
git commit -s -m "fix(filters): drop pending lengths on an accepted buffer reset in every wrapper"
```

---

### Task 3: Request-scoped "notice printed" flag

**Files:**
- Modify: `src/main/java/io/github/carlos_emr/carlos/commn/printing/PrivacyStatementAppendingFilter.java` (`isConfidentialtyNotePrinted`, `resetConfidentialityNote`)
- Test: `src/test/java/io/github/carlos_emr/carlos/commn/printing/PrivacyStatementAppendingFilterUnitTest.java`

**Interfaces:**
- Produces: `ATTRIBUTE_NAME_CONFIDENTIALITY_NOTE_PRINTED` is now only ever a request attribute. Nothing else reads it (`grep -rn CONFIDENTIALITY_NOTE_PRINTED src/main` returns only this class).

- [ ] **Step 1: Write the failing test**

```java
@Test
void shouldAppendNoticeToBothPages_whenTwoRequestsShareOneSessionConcurrently() throws Exception {
    var properties = io.github.carlos_emr.CarlosProperties.getInstance();
    Object original = properties.setProperty("confidentiality_statement.v1", "Synthetic race notice.");
    try {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest outer = new MockHttpServletRequest("GET", "/print-a");
        MockHttpServletRequest inner = new MockHttpServletRequest("GET", "/print-b");
        outer.setSession(session);
        inner.setSession(session);
        MockHttpServletResponse outerResponse = new MockHttpServletResponse();
        MockHttpServletResponse innerResponse = new MockHttpServletResponse();
        filter.doFilter(outer, outerResponse, (req, res) -> {
            res.setContentType("text/html;charset=UTF-8");
            res.getWriter().write("<p>A</p>");
            // A second request on the same session arrives while the first is still rendering.
            filter.doFilter(inner, innerResponse, (req2, res2) -> {
                res2.setContentType("text/html;charset=UTF-8");
                res2.getWriter().write("<p>B</p>");
            });
        });
        assertThat(outerResponse.getContentAsString()).contains("<p>A</p>", "Synthetic race notice.");
        assertThat(innerResponse.getContentAsString()).contains("<p>B</p>", "Synthetic race notice.");
    } finally {
        if (original == null) properties.remove("confidentiality_statement.v1");
        else properties.setProperty("confidentiality_statement.v1", (String) original);
    }
}
```

The `filter` field's exclusions do not match `/print-a` or `/print-b`.

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn test -Dtest=PrivacyStatementAppendingFilterUnitTest#shouldAppendNoticeToBothPages* -Dtest.forkCount=1`
Expected: FAIL, `innerResponse` body lacks "Synthetic race notice.".

- [ ] **Step 3: Make the flag request-scoped**

`isConfidentialtyNotePrinted(HttpServletRequest)`: delete the session branch; keep the existing request-attribute branch as the only body. `resetConfidentialityNote(HttpServletRequest)`: delete the session lines; keep `request.removeAttribute(...)`. Remove the now-unused `HttpSession` import if nothing else uses it. Replace the method's comment with one sentence: the flag only needs to survive dispatches within one request, so a session attribute added a cross-request race and nothing else.

- [ ] **Step 4: Run the focused suite**

Run: **FOCUSED**
Expected: `BUILD SUCCESS`, including the embedded-Tomcat "exactly once" decoration cases.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/carlos_emr/carlos/commn/printing/PrivacyStatementAppendingFilter.java \
  src/test/java/io/github/carlos_emr/carlos/commn/printing/PrivacyStatementAppendingFilterUnitTest.java
git commit -s -m "fix(privacy): scope the printed-notice flag to the request"
```

---

### Task 4: One HTML predicate and one charset fallback in the privacy filter

**Files:**
- Modify: `src/main/java/io/github/carlos_emr/carlos/utility/RequestNegotiation.java`
- Modify: `src/main/java/io/github/carlos_emr/carlos/utility/ResponseSanitizationFilter.java` (`resolveEncoding`, two call sites)
- Modify: `src/main/java/io/github/carlos_emr/carlos/commn/printing/PrivacyStatementAppendingFilter.java` (`doFilter` HTML check, `noticeCharset`)
- Test: `src/test/java/io/github/carlos_emr/carlos/utility/RequestNegotiationUnitTest.java`
- Test: `src/test/java/io/github/carlos_emr/carlos/commn/printing/PrivacyStatementAppendingFilterUnitTest.java`

**Interfaces:**
- Produces: `public static Charset RequestNegotiation.responseCharset(ServletResponse response)` in `io.github.carlos_emr.carlos.utility`. Returns the declared charset when the JRE can use it, else `StandardCharsets.UTF_8`; never throws for null, empty, illegal or unsupported names.

- [ ] **Step 1: Write the failing tests**

In `RequestNegotiationUnitTest`:

```java
@ParameterizedTest
@ValueSource(strings = {"ISO-8859-1", "UTF-16"})
void shouldReturnDeclaredCharset_whenTheJreSupportsIt(String name) {
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setCharacterEncoding(name);
    assertThat(RequestNegotiation.responseCharset(response)).isEqualTo(Charset.forName(name));
}

@Test
void shouldFallBackToUtf8_whenCharsetIsMissingIllegalOrUnsupported() {
    ServletResponse missing = mock(ServletResponse.class);
    when(missing.getCharacterEncoding()).thenReturn(null);
    ServletResponse empty = mock(ServletResponse.class);
    when(empty.getCharacterEncoding()).thenReturn("");
    ServletResponse illegal = mock(ServletResponse.class);
    when(illegal.getCharacterEncoding()).thenReturn("not a charset!");
    ServletResponse unsupported = mock(ServletResponse.class);
    when(unsupported.getCharacterEncoding()).thenReturn("x-carlos-no-such-charset");
    for (ServletResponse response : java.util.List.of(missing, empty, illegal, unsupported)) {
        assertThat(RequestNegotiation.responseCharset(response)).isEqualTo(StandardCharsets.UTF_8);
    }
}
```

In `PrivacyStatementAppendingFilterUnitTest`:

```java
@Test
void shouldAppendNotice_whenContentTypeIsUpperCaseHtml() throws Exception {
    var properties = io.github.carlos_emr.CarlosProperties.getInstance();
    Object original = properties.setProperty("confidentiality_statement.v1", "Synthetic case notice.");
    try {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/print");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            res.setContentType("TEXT/HTML;charset=UTF-8");
            res.getWriter().write("<p>upper</p>");
        });
        assertThat(response.getContentAsString()).contains("<p>upper</p>", "Synthetic case notice.");
    } finally {
        if (original == null) properties.remove("confidentiality_statement.v1");
        else properties.setProperty("confidentiality_statement.v1", (String) original);
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `mvn test -Dtest=RequestNegotiationUnitTest,PrivacyStatementAppendingFilterUnitTest#shouldAppendNotice_whenContentTypeIsUpperCaseHtml -Dtest.forkCount=1`
Expected: compilation error `cannot find symbol: method responseCharset`. The upper-case test is a guard against the locale-sensitive predicate, not a reproduction; it is expected to pass once the build compiles.

- [ ] **Step 3: Implement `RequestNegotiation.responseCharset(ServletResponse)`**

Body: the sanitizer's current `resolveEncoding` logic (null/empty → UTF-8; `Charset.forName` in a `try`, catch `IllegalArgumentException` → UTF-8). Javadoc: one paragraph, mention that Tomcat 11 keeps a rejected charset name visible through `getCharacterEncoding()`, so callers writing at the end of an already-written response must not throw.

- [ ] **Step 4: Replace the two private copies**

`ResponseSanitizationFilter`: delete `resolveEncoding`; both call sites call `RequestNegotiation.responseCharset(response)`. `PrivacyStatementAppendingFilter`: delete `noticeCharset` and its three `java.nio.charset` imports that become unused; `printConfidentialityStatement` calls `RequestNegotiation.responseCharset(response)`. In `doFilter`, replace `contentType.toLowerCase().startsWith("text/html")` with `RequestNegotiation.isHtmlContentType(contentType)` and drop the `IMPROPER_UNICODE` suppression on `doFilter` if `toLowerCase` no longer appears in it (`normalizeServletPath` keeps its own).

- [ ] **Step 5: Run the focused suite**

Run: **FOCUSED**
Expected: `BUILD SUCCESS`; `shouldEncodeStreamNoticeAsUtf8_whenDeclaredCharsetIsUnusable` and `shouldEncodeStreamNotice_withTheDeclaredResponseCharset` still pass against the shared helper.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/github/carlos_emr/carlos/utility/RequestNegotiation.java \
  src/main/java/io/github/carlos_emr/carlos/utility/ResponseSanitizationFilter.java \
  src/main/java/io/github/carlos_emr/carlos/commn/printing/PrivacyStatementAppendingFilter.java \
  src/test/java/io/github/carlos_emr/carlos/utility/RequestNegotiationUnitTest.java \
  src/test/java/io/github/carlos_emr/carlos/commn/printing/PrivacyStatementAppendingFilterUnitTest.java
git commit -s -m "refactor(filters): share the response charset fallback and HTML predicate"
```

---

### Task 5: Integration-test hygiene

**Files:**
- Modify: `src/test/java/io/github/carlos_emr/carlos/utility/ResponseFilterLifecycleIntegrationTest.java` (`startTomcat`, `stopTomcat`, `shouldExposeReplayAfterSuspension_withoutBlamingMonitoring`)

**Interfaces:**
- Consumes: `org.owasp.csrfguard.CsrfGuard.load(Properties)` and `CsrfGuard.getInstance().isEnabled()` (already used by the class).

- [ ] **Step 1: Write the failing assertion**

Add to the end of `stopTomcat()`'s `finally` block, after `CarlosProperties` is restored:

```java
assertThat(CsrfGuard.getInstance().isEnabled()).as("CsrfGuard singleton restored for later tests in this fork").isFalse();
```

- [ ] **Step 2: Run the class to verify it fails**

Run: `mvn test -Dtest=ResponseFilterLifecycleIntegrationTest -Dtest.forkCount=1`
Expected: FAIL in `@AfterAll` with `isEnabled()` true.

- [ ] **Step 3: Restore the singleton**

Promote the local `csrf` Properties to `private static final Properties CSRF_PROPERTIES = new Properties();` and add `private static boolean originalCsrfEnabled;`. In `startTomcat()`, before `CsrfGuard.load(CSRF_PROPERTIES)`: `originalCsrfEnabled = CsrfGuard.getInstance().isEnabled();`. In `stopTomcat()`'s `finally`, after `CarlosProperties` is restored and before the assertion:

```java
Properties restore = new Properties();
restore.putAll(CSRF_PROPERTIES);
restore.setProperty("org.owasp.csrfguard.Enabled", String.valueOf(originalCsrfEnabled));
CsrfGuard.load(restore);
```

Change the Step 1 assertion to `.isEqualTo(originalCsrfEnabled)`.

- [ ] **Step 4: Soften the negative control**

In `shouldExposeReplayAfterSuspension_withoutBlamingMonitoring`, delete the two `assertThat(...body()).isEmpty()` lines for `/sanitizer` and `/default`. Keep the status, `FINISHED_AFTER_FORWARD` and the two positive-control body assertions. Update the method's leading comment: the control pins that Tomcat finished the raw response, not what bytes the broken configuration happens to emit.

- [ ] **Step 5: Run the class twice in one fork alongside another CsrfGuard-sensitive test**

Run: `mvn test -Dtest=ResponseFilterLifecycleIntegrationTest,CsrfGuardScriptInjectionFilterUnitTest -Dtest.forkCount=1`
Expected: `BUILD SUCCESS`; `CsrfGuardScriptInjectionFilterUnitTest` (which arranges its own CsrfGuard state via `withEnabledCsrfGuard`) passes regardless of order.

- [ ] **Step 6: Commit**

```bash
git add src/test/java/io/github/carlos_emr/carlos/utility/ResponseFilterLifecycleIntegrationTest.java
git commit -s -m "test(filters): restore CsrfGuard after the lifecycle fixture and stop pinning Tomcat's broken body"
```

---

### Task 6: Finish the PR

**Files:**
- Modify: PR #5495 description (GitHub), no repository files.

- [ ] **Step 1: Run the full focused suite and the repo's fast checks one last time**

Run: **FOCUSED** then `bash scripts/lint/check-encoder-null-safety.sh`
Expected: `BUILD SUCCESS`; lint prints no new violations.

- [ ] **Step 2: Push**

Run: `git push -u origin fix/5487-response-filter-lifecycle`
Expected: GitHub reports the PR mergeable (`mergeable_state` no longer `dirty`).

- [ ] **Step 3: Update the PR description**

Add one bullet under Description: "On an accepted `resetBuffer()` every wrapper now drops a pending length and removes one it published; this is stricter than the base, where the privacy wrapper re-published a length declared before a forward." Add one bullet: "The printed-notice flag is request-scoped; two concurrent requests in one session each receive the notice." Replace the "Final validated source" hash and the test count with the new head and the FOCUSED total.

- [ ] **Step 4: Open the follow-up issue (ASK-tier `gh issue create`; confirm with the owner first)**

Title: `Share one DeferredContentLength state machine across the response wrappers`. Body, three bullets: the three copies and their divergent malformed-value rule (spec finding 2); the SPDX-versus-documented copyright header drift in new test files; `Alpha20MutationCsrfIntegrationTest` also loads CsrfGuard without restoring it. Type: Maintenance. Link PR #5495.
