# eForm Upload Schedule Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve the focused schedule navigation shell after successful HTML eForm uploads and ZIP eForm imports from Administration.

**Architecture:** Explicitly propagate the trusted flag `scheduleNav=1` from both Administration entry links into the eForm manager, from the manager into both upload iframes, and from each iframe through its multipart POST into the success redirect. Direct eForm-manager access and any value other than the exact string `1` retain the current clean `/administration?show=Forms` destination.

**Tech Stack:** Jakarta JSP/JSTL, Struts 7 multipart actions, JUnit 5/AssertJ structural regression tests, Node.js Playwright 1.60, Bootstrap 5 Administration UI.

## Global Constraints

- Cover both HTML eForm Upload and ZIP eForm Import.
- Propagate only the exact value `scheduleNav=1`; never accept or propagate an arbitrary return URL or query string.
- Do not change upload/import persistence, authorization, validation, or error handling.
- Direct access without `scheduleNav=1` must retain `/administration?show=Forms`.
- Playwright verification must capture screenshots before upload and after successful Upload and Import redirects.

---

### Task 1: Lock the explicit navigation-context contract with failing tests

**Files:**
- Modify: `src/test/java/io/github/carlos_emr/carlos/web/AdministrationNavigationRegressionTest.java`
- Modify: `src/test/java/io/github/carlos_emr/carlos/eform/EFormJspMigrationRegressionTest.java`

**Interfaces:**
- Consumes: JSP source files as UTF-8 text.
- Produces: Regression assertions for the exact `scheduleNav=1` handoff at every request boundary.

- [ ] **Step 1: Add failing Administration entry-link coverage**

Add a test that reads both Administration entrypoint JSPs and requires their Manage eForms URLs to preserve only the exact schedule flag:

```java
@Test
@DisplayName("Manage eForms links should preserve focused schedule navigation")
void shouldPreserveScheduleNavigation_forManageEFormsLinks() throws IOException {
    for (Path jspPath : List.of(
            Path.of("src/main/webapp/WEB-INF/jsp/administration/index.jsp"),
            Path.of("src/main/webapp/WEB-INF/jsp/administration/leftNav.jspf"))) {
        String jsp = Files.readString(jspPath);

        assertThat(jsp)
                .contains("/eform/efmformmanager${param.scheduleNav eq '1' ? '?scheduleNav=1' : ''}")
                .doesNotContain("/eform/efmformmanager?scheduleNav=1\"");
    }
}
```

- [ ] **Step 2: Add failing manager, multipart-form, and redirect coverage**

Add `EFM_FORM_MANAGER_JSP` and a test to `EFormJspMigrationRegressionTest`:

```java
private static final Path EFM_FORM_MANAGER_JSP =
        Path.of("src/main/webapp/WEB-INF/jsp/eform/efmformmanager.jsp");

@Test
@DisplayName("eForm uploads and imports should preserve explicit schedule navigation")
void shouldPreserveScheduleNavigation_throughEFormUploadAndImport() throws IOException {
    String manager = Files.readString(EFM_FORM_MANAGER_JSP, StandardCharsets.UTF_8);
    String upload = Files.readString(UPLOAD_PARTIAL_JSP, StandardCharsets.UTF_8);
    String importJsp = Files.readString(IMPORT_PARTIAL_JSP, StandardCharsets.UTF_8);

    assertThat(manager)
            .contains("/eform/partials/upload${param.scheduleNav eq '1' ? '?scheduleNav=1' : ''}")
            .contains("/eform/partials/import${param.scheduleNav eq '1' ? '?scheduleNav=1' : ''}");

    for (String partial : List.of(upload, importJsp)) {
        assertThat(partial)
                .contains("<c:if test=\"${param.scheduleNav eq '1'}\">")
                .contains("<input type=\"hidden\" name=\"scheduleNav\" value=\"1\"")
                .contains("/administration?show=Forms${param.scheduleNav eq '1' ? '&scheduleNav=1' : ''}")
                .doesNotContain("/administration?show=Forms&scheduleNav=1\"");
    }
}
```

- [ ] **Step 3: Run the focused tests and verify RED**

Run:

```bash
mvn -q -Dtest=AdministrationNavigationRegressionTest,EFormJspMigrationRegressionTest test
```

Expected: both new tests fail because the entry links, iframe sources, hidden fields, and conditional redirects do not yet propagate `scheduleNav=1`.

---

### Task 2: Propagate `scheduleNav=1` through Upload and Import

**Files:**
- Modify: `src/main/webapp/WEB-INF/jsp/administration/index.jsp`
- Modify: `src/main/webapp/WEB-INF/jsp/administration/leftNav.jspf`
- Modify: `src/main/webapp/WEB-INF/jsp/eform/efmformmanager.jsp`
- Modify: `src/main/webapp/WEB-INF/jsp/eform/partials/upload.jsp`
- Modify: `src/main/webapp/WEB-INF/jsp/eform/partials/import.jsp`
- Test: `src/test/java/io/github/carlos_emr/carlos/web/AdministrationNavigationRegressionTest.java`
- Test: `src/test/java/io/github/carlos_emr/carlos/eform/EFormJspMigrationRegressionTest.java`

**Interfaces:**
- Consumes: request parameter `scheduleNav`, treated as enabled only when `param.scheduleNav eq '1'`.
- Produces: conditional manager and iframe URLs, hidden multipart fields, and top-window return URLs.

- [ ] **Step 1: Update both Administration Manage eForms links**

Use the same conditional expression in the quick-access card and left navigation:

```jsp
href="${ctx}/eform/efmformmanager${param.scheduleNav eq '1' ? '?scheduleNav=1' : ''}"
```

- [ ] **Step 2: Pass the flag into both embedded manager iframes**

Update the iframe sources in `efmformmanager.jsp`:

```jsp
src="<%=request.getContextPath()%>/eform/partials/upload${param.scheduleNav eq '1' ? '?scheduleNav=1' : ''}"
```

```jsp
src="<%=request.getContextPath()%>/eform/partials/import${param.scheduleNav eq '1' ? '?scheduleNav=1' : ''}"
```

- [ ] **Step 3: Preserve the flag in both multipart POSTs**

Place this field inside both forms:

```jsp
<c:if test="${param.scheduleNav eq '1'}">
    <input type="hidden" name="scheduleNav" value="1"/>
</c:if>
```

- [ ] **Step 4: Make both successful top-window redirects conditional**

Replace each unconditional redirect with:

```javascript
window.top.location.href = "<%=request.getContextPath()%>/administration?show=Forms${param.scheduleNav eq '1' ? '&scheduleNav=1' : ''}";
```

- [ ] **Step 5: Run focused tests and verify GREEN**

Run:

```bash
mvn -q -Dtest=AdministrationNavigationRegressionTest,EFormJspMigrationRegressionTest test
```

Expected: PASS with no test failures.

- [ ] **Step 6: Run JSP/build validation**

Run:

```bash
mvn -q -DskipTests package -Pjspc
git diff --check
```

Expected: Maven exits 0 and `git diff --check` emits no output.

- [ ] **Step 7: Commit the tested production change**

```bash
git add src/main/webapp/WEB-INF/jsp/administration/index.jsp \
  src/main/webapp/WEB-INF/jsp/administration/leftNav.jspf \
  src/main/webapp/WEB-INF/jsp/eform/efmformmanager.jsp \
  src/main/webapp/WEB-INF/jsp/eform/partials/upload.jsp \
  src/main/webapp/WEB-INF/jsp/eform/partials/import.jsp \
  src/test/java/io/github/carlos_emr/carlos/web/AdministrationNavigationRegressionTest.java \
  src/test/java/io/github/carlos_emr/carlos/eform/EFormJspMigrationRegressionTest.java
git commit -m "fix: preserve admin navigation after eform upload"
```

---

### Task 3: Verify both completion paths in Playwright with screenshots

**Files:**
- Create: `scripts/eform-admin-schedule-navigation-playwright-checks.js`
- Modify: `package.json`

**Interfaces:**
- Consumes: `BASE_URL`, `CHROME_PATH`, `TEST_USER`, `TEST_PASSWORD`, `TEST_PIN`, and `EFORM_ADMIN_NAV_SCREENSHOT_DIR` environment variables.
- Produces: a nonzero exit on navigation loss and PNG screenshots named `eform-admin-nav-before-upload.png`, `eform-admin-nav-after-upload.png`, and `eform-admin-nav-after-import.png`.

- [ ] **Step 1: Create a focused live browser check**

Implement these helpers in the new script:

```javascript
async function login(context) {
  const page = await context.newPage();
  await page.goto(appUrl('/'), { waitUntil: 'domcontentloaded' });
  await page.locator('#username').fill(testUser);
  await page.locator('#password').fill(testPassword);
  if (await page.locator('#pin').count()) {
    await page.locator('#pin').fill(testPin);
  }
  await Promise.all([
    page.waitForURL(/providercontrol|appointment/i, { timeout: 30000 }),
    page.locator('input[type="submit"], button[type="submit"]').first().click(),
  ]);
  return page;
}

async function openFocusedManager(page) {
  await page.goto(appUrl('/administration?scheduleNav=1'), { waitUntil: 'domcontentloaded' });
  await page.locator('a.defaultForms').first().click();
  await page.frameLocator('#uploadFrame').locator('form[action$="/eform/uploadHtml"]').waitFor();
}
async function assertFocusedAdministration(page, label) {
  const url = new URL(page.url());
  assert(url.pathname.endsWith('/administration'), `${label}: unexpected path ${url.pathname}`);
  assert(url.searchParams.get('show') === 'Forms', `${label}: Forms section was not restored`);
  assert(url.searchParams.get('scheduleNav') === '1', `${label}: scheduleNav was lost`);
  assert(await page.locator('#firstTable #navlist').count(), `${label}: schedule navigation is absent`);
}
```

Create a deterministic temporary HTML eForm whose name includes `Date.now()`. Enter the Upload iframe, populate `formName`, `formSubject`, and `formHtml`, then submit while waiting for the top page to return to Administration. Capture the before/after screenshots with `buildArtifactPath(...)` and call `assertFocusedAdministration(...)`.

Export the uploaded row through `/eform/manageEForm?method=exportEForm&fid=...`, save the downloaded ZIP under a validated `/tmp` directory, delete the temporary eForm using the existing POST-only `/eform/delEForm` pattern, return to the focused manager, upload the saved ZIP through the Import iframe, and assert/capture the second successful redirect. Delete the imported temporary eForm in `finally` when its fid is available and remove local temporary files.

Record page errors, console errors, and HTTP responses of status 400 or higher. Fail if any unexpected browser issue occurs.

- [ ] **Step 2: Register the package command**

Add:

```json
"test:eform-admin-schedule-nav-playwright": "node scripts/eform-admin-schedule-navigation-playwright-checks.js"
```

- [ ] **Step 3: Rebuild and restart the local PR app**

Run:

```bash
make install --skip-checks
```

If the command-scoped Tomcat process does not persist, run `catalina.sh jpda run` in a persistent terminal session and wait for `Server startup` before testing.

- [ ] **Step 4: Run Playwright and inspect screenshots**

Run:

```bash
NODE_PATH=/workspace/node_modules \
CHROME_PATH=/root/.cache/ms-playwright/chromium-1223/chrome-linux64/chrome \
EFORM_ADMIN_NAV_SCREENSHOT_DIR=/tmp/pr3286-eform-admin-nav \
node scripts/eform-admin-schedule-navigation-playwright-checks.js
```

Expected: PASS; the top-level URL retains `show=Forms&scheduleNav=1` after both operations, `#firstTable #navlist` remains present, and all three screenshots exist and show the schedule navigation.

- [ ] **Step 5: Commit the browser regression**

```bash
git add scripts/eform-admin-schedule-navigation-playwright-checks.js package.json
git commit -m "test: cover eform admin navigation round trips"
```

---

### Task 4: Final verification

**Files:**
- Verify only; no additional files expected.

**Interfaces:**
- Consumes: completed Tasks 1–3 and the running local CARLOS app.
- Produces: fresh test/build/browser evidence and a clean scoped diff.

- [ ] **Step 1: Run all focused automated checks**

```bash
mvn -q -Dtest=AdministrationNavigationRegressionTest,EFormJspMigrationRegressionTest test
mvn -q -DskipTests package -Pjspc
NODE_PATH=/workspace/node_modules \
CHROME_PATH=/root/.cache/ms-playwright/chromium-1223/chrome-linux64/chrome \
EFORM_ADMIN_NAV_SCREENSHOT_DIR=/tmp/pr3286-eform-admin-nav \
node scripts/eform-admin-schedule-navigation-playwright-checks.js
git diff --check HEAD~2..HEAD
```

Expected: every command exits 0, Playwright reports both round trips preserved the shell, and all requested screenshots are present.

- [ ] **Step 2: Review final scope**

```bash
git status --short --branch
git diff --stat a643b739d3f69f8f4068594cd0bb1063e2d08ad1..HEAD
git log --oneline -4
```

Expected: only the approved design/plan, five JSP propagation points, focused Java regressions, the Playwright script, and its package command differ from PR 3286.
