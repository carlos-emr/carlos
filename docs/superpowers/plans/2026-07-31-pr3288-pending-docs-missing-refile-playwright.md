# PR 3288 Pending Docs Missing-Refile Playwright Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a deterministic local Playwright regression check proving that a Pending Docs document view remains usable when queue 1 has no `Refile` directory.

**Architecture:** A standalone Node/Playwright script logs into the local CARLOS app, chooses an active document row with a read-only SQL query, temporarily hides only queue 1's `Refile` directory, and opens `ViewShowDocument`. It records browser failures and restores the original directory before Playwright cleanup in `finally`, with synchronous signal cleanup as a second restoration path. `package.json` exposes the check as an npm command.

**Tech Stack:** Node.js 18+, Playwright 1.60, `mysql` CLI, local CARLOS Tomcat, Node `fs`/`path` APIs.

## Global Constraints

- Run only against a local or private CARLOS base URL; reject public hosts unless explicitly opted in.
- The test may rename only `${INCOMING_DOCUMENT_DIR}/1/Refile`; never delete it or its contents.
- Default `INCOMING_DOCUMENT_DIR` is `/var/lib/OscarDocument/oscar/incomingdocs`; allow a local test override through `INCOMING_DOCUMENT_DIR`.
- Use only read-only database queries and fail clearly if no active `document` row exists.
- Always restore the original directory state and close Playwright resources in `finally`.
- Assert browser-observable behavior, not implementation text or mocked calls.

---

## File Structure

- Create: `scripts/pending-docs-missing-refile-playwright-checks.js` — local browser regression script, filesystem guard/restore helper, document lookup, and browser assertions.
- Modify: `package.json` — add `test:pending-docs-missing-refile-playwright` pointing to the new script.

### Task 1: Add the failing browser regression script

**Files:**
- Create: `scripts/pending-docs-missing-refile-playwright-checks.js`
- Test: `scripts/pending-docs-missing-refile-playwright-checks.js` run against the locally deployed pre-fix parent `b92f9716a4^` (`06d8040f93c3a02654d46c0e6fd9cbb684cb9dbf`)

**Interfaces:**
- Consumes: `BASE_URL`, `TEST_USER`, `TEST_PASSWORD`, `TEST_PIN`, `MYSQL_HOST`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_DATABASE`, and `INCOMING_DOCUMENT_DIR` environment variables.
- Produces: exit code 0 only when the document view renders while `${INCOMING_DOCUMENT_DIR}/1/Refile` is absent; non-zero with recorded browser diagnostics otherwise.

- [ ] **Step 1: Write the failing test**

Create the script with these observable assertions and cleanup shape:

```js
const response = await page.goto(appUrl('/documentManager/ViewShowDocument?'
  + new URLSearchParams({ segmentID: String(documentNo), inWindow: 'true', inQueue: 'true' })),
{ waitUntil: 'networkidle', timeout: 30000 });

assert(response && response.ok(), `Pending Docs view returned ${response ? response.status() : 'no response'}`);
const body = await page.locator('body').innerText();
assert(!/CARLOS has encountered an unexpected error|HTTP Status 500|Exception Report/i.test(body),
  'Pending Docs view rendered an application error page');
assert(await page.locator(`#refileDoc_${documentNo}`).count() === 1,
  'Pending Docs view did not render the document refile control');
```

Use `child_process.execFileSync('mysql', [...])` for this exact read-only fixture query:

```sql
SELECT document_no
FROM document
WHERE status = 'A'
ORDER BY document_no DESC
LIMIT 1
```

Implement `hideRefileDirectory()` using `fs.existsSync`, `fs.renameSync`, and a backup name containing `process.pid` plus `Date.now()`. Implement `restoreRefileDirectory()` so an originally absent directory remains absent and an originally present directory is moved back in `finally`.
Make restoration idempotent, run it before Playwright cleanup, and install `SIGINT`/`SIGTERM` handlers that restore synchronously before terminating. Detach page listeners after the settled assertions so browser shutdown cancellations cannot become findings, and include the backup directory path in diagnostics for manual recovery.


- [ ] **Step 2: Run test to verify it fails**

Create a detached parent worktree at `/tmp/carlos-pr3288-parent` from `b92f9716a4^` (`06d8040f93c3a02654d46c0e6fd9cbb684cb9dbf`), build and deploy it with `.devcontainer/development/scripts/make install --skip-checks`, run Tomcat in a persistent foreground session, then run:

```bash
TEST_PASSWORD=carlos2026 TEST_PIN=2026 MYSQL_PASSWORD=password \
  node /tmp/carlos-pr3288/scripts/pending-docs-missing-refile-playwright-checks.js
```

Expected: non-zero exit because this pre-fix build's `EDocUtil.isDocumentAlreadyRefiledInQueue` treats the absent `Refile` directory as invalid and the `ViewShowDocument` request returns an application error / HTTP 500. Confirm the script's `finally` restoration leaves the original directory state unchanged.

- [ ] **Step 3: Write minimal implementation**

Complete the script without mocks:

```js
const baseUrl = validateBaseUrl(process.env.BASE_URL || 'http://127.0.0.1:8080/carlos');
const refilePath = path.join(process.env.INCOMING_DOCUMENT_DIR
  || '/var/lib/OscarDocument/oscar/incomingdocs', '1', 'Refile');
let hiddenRefile = null;

try {
  hiddenRefile = hideRefileDirectory(refilePath);
  // launch Chromium, log in, query one active document, and assert the real view
} finally {
  restoreRefileDirectory(hiddenRefile);
  await browser?.close();
}
```

Add request, page-error, and console listeners. Ignore only established harmless local noise (CSP report-only messages and missing favicon/image-rendering assets); record all other 4xx/5xx responses and `error` console messages. Include the document ID, target URL, original directory state, and recorded findings in failure output.

- [ ] **Step 4: Run test to verify it passes**

Redeploy PR commit `498efb0973` from `/tmp/carlos-pr3288`, keep Tomcat foregrounded, then run the same direct Node command. Expected: exit 0, successful `ViewShowDocument` response, the `refileDoc_<documentNo>` control present, and the `Refile` directory restored to its original state.

- [ ] **Step 5: Commit**

```bash
git add scripts/pending-docs-missing-refile-playwright-checks.js
git commit -m "test: cover missing pending-doc refile directory"
```

### Task 2: Expose the check through npm and run final verification

**Files:**
- Modify: `package.json`
- Test: `npm run test:pending-docs-missing-refile-playwright`

**Interfaces:**
- Consumes: the script created in Task 1 and its documented environment variables.
- Produces: a discoverable npm command with the same exit behavior as the direct Node invocation.

- [ ] **Step 1: Write the failing test**

Add the package script entry:

```json
"test:pending-docs-missing-refile-playwright": "node scripts/pending-docs-missing-refile-playwright-checks.js"
```

Keep it alongside the other `test:*playwright` entries and do not change dependency versions.

- [ ] **Step 2: Run test to verify it fails**

While the parent build is deployed, run:

```bash
TEST_PASSWORD=carlos2026 TEST_PIN=2026 MYSQL_PASSWORD=password \
  npm run test:pending-docs-missing-refile-playwright
```

Expected: npm returns the script's non-zero regression failure, proving the package entry runs the browser test rather than merely checking source text.

- [ ] **Step 3: Write minimal implementation**

No additional production implementation is needed beyond the Task 1 script and this one `package.json` entry. Keep the entry's command exactly `node scripts/pending-docs-missing-refile-playwright-checks.js` so npm preserves the script's real browser-test exit status.

- [ ] **Step 4: Run test to verify it passes**

With PR 3288 deployed, run:

```bash
TEST_PASSWORD=carlos2026 TEST_PIN=2026 MYSQL_PASSWORD=password \
  npm run test:pending-docs-missing-refile-playwright
```

Expected: exit 0; the output identifies the selected document, confirms the document-view route succeeded, reports no blocking browser findings, and confirms the original `Refile` directory state was restored.

Also run:

```bash
mvn -Dtest=IncomingDocUtilPathValidationTest,EFormRenderPdfHtmlComposerUnitTest test
git diff --check
git status --short
```

Expected: 45 focused Java tests passing, no whitespace errors, and only the intended script/package/plan changes before commit.

- [ ] **Step 5: Commit**

```bash
git add package.json scripts/pending-docs-missing-refile-playwright-checks.js \
  docs/superpowers/plans/2026-07-31-pr3288-pending-docs-missing-refile-playwright.md
git commit -m "test: add pending docs missing refile browser coverage"
```
