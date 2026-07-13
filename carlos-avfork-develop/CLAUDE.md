# CARLOS EMR - Healthcare Electronic Medical Records System

> **⚠️ DEVCONTAINER ENVIRONMENT NOTICE**
>
> The `.claude/settings.json` in this repository grants **extensive pre-approved permissions**
> optimized for **isolated devcontainer development only**. These settings assume:
> - Sandboxed Docker environment with no external network access to production systems
> - Development database with synthetic/test data (no real PHI)
> - Disposable environment that can be safely reset
>
> **DO NOT** use these defaults in shared servers, production environments, or any system
> with access to real patient data. Review and restrict permissions in `.claude/settings.json`
> if running outside an isolated devcontainer.

**PROJECT IDENTITY**: Always refer to this system as "CARLOS EMR" or "CARLOS" in all user-facing content

## Project Origin & Naming Conventions

**CARLOS** (Clinical Assisting Recording Ledger Open Source) is a fork of the OpenO EMR project, maintained at `github.com/carlos-emr/carlos`.

**Project Lineage:**
- **CARLOS** ← forked from **OpenO EMR** ← forked from **OSCAR McMaster**
- **No affiliation** with OpenOSP organization or McMaster University
- Repository: `github.com/carlos-emr/carlos`

**Critical Naming Distinctions:**
- **Display Name**: Use "CARLOS" or "CARLOS EMR" in all user-facing content (UI, documentation, README files, issue templates, workflows)
- **Code Namespaces**: Package names remain as `io.github.carlos_emr.carlos.*` for backward compatibility - DO NOT change these internal paths
- **Legacy References**: You may encounter "OpenO", "Open-O", "OpenOSP", or "OSCAR" in code comments, JavaDoc, and git history - these refer to the upstream project heritage, NOT organizational affiliations

**When to Use Which Name:**
- ✅ **Use "CARLOS EMR"**: README files, documentation headers, GitHub templates, workflow descriptions, user-facing messages
- ✅ **Keep as-is**: Java packages (`io.github.carlos_emr.carlos.*`), internal class references, database schema names, Spring bean names
- ⚠️ **Update carefully**: Comments referencing project name (use "CARLOS"), but preserve technical accuracy for upstream references

## Core Context

**Domain**: Canadian healthcare EMR system with multi-jurisdictional compliance (BC, ON, generic)
**Stack**: Java 21, Spring 7.0.6, Struts 7.1.1, Hibernate 7.2.7, Maven 3, Tomcat 11.0, MariaDB/MySQL
**Regulatory**: HIPAA/PIPEDA compliance REQUIRED - PHI protection is CRITICAL


## Fax Provider Feature Context (AI + Dev)

- Provider-specific fax transport is now selected by `FaxConfig.providerType` (`MIDDLEWARE` or `SRFAX`).
- Admin configuration path is the existing UI: **Administration > Faxes > Configure Fax**.
- Fax configuration requires `_admin.fax` write rights; scheduler controls use `_admin.fax.restart`.
- SRFax duplicate prevention policy is unread/read flag based (unread-only pull + mark-as-read), not remote delete.
- See `docs/fax-provider-configuration-and-ux.md` for implementation and operational details.

## Essential Commands

```bash
# Development Workflow
make clean                    # Clean project and remove deployed app
make install                  # Build and deploy without tests
make install --run-tests      # Build, test, and deploy (all tests)
make install --run-unit-tests       # Build and run only unit tests
make install --run-integration-tests # Build and run only integration tests
server start/stop/restart     # Tomcat management
server log                    # Tail application logs

# Database & Environment
db-connect                   # Connect to MariaDB as root
debug-on / debug-off         # Toggle DEBUG/INFO logging levels

gh pr create                 # GitHub pull request creation
```

## Critical Security Requirements

**MANDATORY for all code changes:**
- OWASP Encoder for ALL user inputs (see [OWASP Encoding](#owasp-encoding--xss-prevention) below)
- Parameterized queries ONLY - never string concatenation
- ALL actions MUST include `SecurityInfoManager.hasPrivilege()` checks
- SecurityException messages for failed security-object privilege checks MUST use
  paren form: `missing required sec object (_objectname)` (not colon form).
- PHI (Patient Health Information) must NEVER be logged or exposed
- **Use `PathValidationUtils` for ALL file path operations** (see below)

### Documentation-Only Changes

When asked for a documentation-only pass, change only comments, Javadocs, Markdown, or other
non-executable documentation text. Do not hide behavior changes in comment sweeps: no route
mappings, constants, selectors, assertions, imports, SQL, config values, or executable statements.
Inline comments are expected when they help a future maintainer understand intent, context, or
risk. Senior-maintainer comments should explain security boundaries, legacy constraints,
invariants, and why a surprising pattern exists; avoid line-by-line narration that restates the
code.

For login/password-reset work, keep documentation aligned with these invariants:
- Server-side validation is authoritative; browser password policy checks are only user feedback.
- Forced-reset credential material stays out of the HTTP session and is referenced by an opaque,
  short-lived cache token.
- Retryable reset validation errors may keep the token live; terminal password changes consume it.
- `scripts/login-playwright-checks.js` is the reference browser/direct-POST regression check for
  login, failed login, forced reset, CSRF rejection, and provider schedule rendering. It requires
  exactly these environment variables: `TEST_PASSWORD`, `TEST_PIN`, `MYSQL_PASSWORD`, and
  `TEST_PASSWORD_HASH`. The script uses them to seed a known-good dev password before mutating the
  test user's security row. Run it with
  `npm run test:login-playwright` against a disposable local/dev database; it is a manual reference
  check unless the active CI job has already started Tomcat and the dev database.
  Devcontainer-only defaults are `TEST_USER=carlosdoc`, `TEST_PASSWORD=carlos2026`, and
  `TEST_PIN=2026`; these are not production defaults. Use the dev database password from
  `.devcontainer/development/config/shared/local.env` and the seeded `carlosdoc` hash from
  `database/mysql/oscardata.sql` for `TEST_PASSWORD_HASH`.

**What counts as PHI vs. internal identifiers:**
- **PHI** (treat as sensitive): HIN/health card number, patient name, DOB, address, phone, diagnosis text, clinical notes, lab values, medication details — anything that identifies a real person or their care.
- **PHI-correlating operational identifiers**: `demographic_no` / `demoNo`, appointment IDs, billing IDs, provider numbers, claim/WCB IDs, and internal surrogate keys. These are not clinical text, but they can join directly back to patient or billing records inside CARLOS. Log them only when necessary for operations, sanitize them with `LogSafe`, avoid pairing them with clinical context, and never place them in browser-visible exception messages unless the endpoint explicitly requires that identifier for an authorized user workflow.

### Response-Rewriting Filter Safety

Response-rewriting filters are security controls and UI-critical infrastructure. Recent fixes showed that broad buffering, stale `Content-Length` replay, and blanket wrapper changes can silently break login pages, static assets, and first-render post-login screens. Keep fixes targeted by route/status/content type, preserve binary/static passthrough, and add regression coverage before changing buffer limits, dispatcher mappings, `Content-Length` handling, or writer/output-stream fallback behavior. After touching `ResponseSanitizationFilter`, `CsrfGuardScriptInjectionFilter`, `LogoutBroadcastFilter`, or `LoginFilter`, run the focused unit tests and the Playwright login/reset script against Tomcat.

### OWASP Encoding — XSS Prevention

CARLOS provides null-safe wrappers around OWASP Encoder. **Use the CARLOS wrappers for all new code.**

**Why the CARLOS wrapper?** `Encode.forHtmlContent(null)` returns the literal 4-character string `"null"` — `<e:forHtmlContent value='<%= x %>'/>` renders the word `null` in every cell where `x` is a nullable DB field. JSTL `<c:out>` rendered null as empty; the mass `<c:out>` → OWASP migration silently broke this. `<carlos:encode>`, `${carlos:forXxx(...)}`, and `SafeEncode.forXxx(...)` coalesce null to empty before delegating to OWASP.

**CI enforces this.** `scripts/lint/check-encoder-null-safety.sh` runs on every PR and fails if new code uses `<e:forXxx>`, `${e:forXxx(...)}`, or `<%= Encode.forXxx(...) %>`.

**Taglib declaration** (once per JSP that uses the tag or EL functions):
```jsp
<%@ taglib uri="carlos" prefix="carlos" %>
```

**Quick-Reference — All Encoding Contexts:**

| Context | JSP Tag (preferred) | EL Function | Java / Scriptlet |
|---------|--------------------|-------------|------------------|
| HTML body | `<carlos:encode value="${v}"/>` | `${carlos:forHtmlContent(v)}` | `SafeEncode.forHtmlContent(v)` |
| HTML attribute | `<carlos:encode value="${v}" context="htmlAttribute"/>` | `${carlos:forHtmlAttribute(v)}` | `SafeEncode.forHtmlAttribute(v)` |
| JavaScript string | `<carlos:encode value="${v}" context="javaScript"/>` | `${carlos:forJavaScript(v)}` | `SafeEncode.forJavaScript(v)` |
| JS in HTML attr | `<carlos:encode value="${v}" context="javaScriptAttribute"/>` | `${carlos:forJavaScriptAttribute(v)}` | `SafeEncode.forJavaScriptAttribute(v)` |
| CSS string | `<carlos:encode value="${v}" context="cssString"/>` | `${carlos:forCssString(v)}` | `SafeEncode.forCssString(v)` |
| URL path | `<carlos:encode value="${v}" context="uri"/>` | `${carlos:forUri(v)}` | `SafeEncode.forUri(v)` |
| URL parameter | `<carlos:encode value="${v}" context="uriComponent"/>` | `${carlos:forUriComponent(v)}` | `SafeEncode.forUriComponent(v)` |

`<carlos:encode>` supports the full OWASP context set. Default `context="html"` (forHtmlContent). See `src/main/webapp/WEB-INF/carlos-tag.tld` for the complete list.

**When to use which:**
- **JSP tag** (preferred): `<carlos:encode value="${v}"/>` — declarative, null-safe, XSS-safe, context-selectable.
- **EL function** (inline in attribute strings, URL components, JSON): `href="?id=${carlos:forUriComponent(v)}"` — use where a tag element can't fit.
- **Java code / scriptlets**: `SafeEncode.forHtmlContent(v)` — drop-in replacement for `Encode.forHtmlContent(v)` with null-safety.

**Legacy forms — DO NOT use in new code:**
- `<e:forHtmlContent>` and friends (renders null as literal `"null"`; silently drops if the taglib isn't declared).
- `${e:forXxx(...)}` EL functions (same null bug).
- `Encode.forXxx(...)` static calls (same null bug).
- `<c:out>` and `fn:escapeXml()` (only basic XML entity escaping; not context-aware).

The null-safe wrappers live in:
- `src/main/java/io/github/carlos_emr/carlos/utility/SafeEncode.java`
- `src/main/java/io/github/carlos_emr/carlos/utility/tld/CarlosEncodeTag.java`
- `src/main/webapp/WEB-INF/carlos-tag.tld`

Unit tests: `src/test/java/io/github/carlos_emr/carlos/utility/SafeEncodeUnitTest.java` and
`src/test/java/io/github/carlos_emr/carlos/utility/tld/CarlosEncodeTagUnitTest.java`.

A migration codemod script is kept at `scripts/migrate-to-carlos-encode.py` for rewriting any future `<e:...>` / `${e:...}` / `Encode.*` drift.

### CSRF Token Bootstrapping on AJAX JSPs

CSRFGuard's client script only injects the hidden `<input name="CSRF-TOKEN">` into a `<form>` whose `action` attribute is a real URL and whose method is non-GET. Pages that read the token via `document.querySelector('input[name="CSRF-TOKEN"]')` from AJAX (fetch/XHR) POSTs **will silently fail** if the page has no such form — the token comes out empty, the request is rejected with an HTML error page, and `response.json()` throws into a catch block the user never sees.

**Rule:** any JSP that does AJAX POSTs reading `input[name="CSRF-TOKEN"]` must satisfy ONE of:
- **(a)** contain at least one `<form action="<real URL>" method="post">` on the rendered page, OR
- **(b)** include the canonical bootstrap fragment:

  ```jsp
  <%@ include file="/WEB-INF/jspf/csrf-token.jspf" %>
  ```

  Place the include inside `<body>`. It pulls in `share/javascript/csrfTokenFetch.js`, adds a hidden CSRF-TOKEN input, and calls `fetchCsrfToken(contextPath)` on DOMContentLoaded.

Don't rely on the "empty placeholder form" anti-pattern `<form id="csrfForm" style="display:none;"></form>` — CSRFGuard skips action-less forms, so the input never gets populated. Note also how CSRFGuard 4.5 (configured with `org.owasp.csrfguard.Ajax=true`, see `Owasp.CsrfGuard.properties`) validates tokens:

- **AJAX / XHR requests** carrying `X-Requested-With: XMLHttpRequest` are validated via the `CSRF-TOKEN` request header. XHRs hijacked by CSRFGuard's injected client script get this header set automatically; `fetch()` calls are **not** hijacked and must set `CSRF-TOKEN` explicitly (e.g. reading it from the hidden `input[name="CSRF-TOKEN"]`).
- **Classic form POSTs** (non-AJAX) are validated via the `CSRF-TOKEN` form-body parameter injected by CSRFGuard into the `<form>`.

Header validation takes precedence over body-parameter validation when both are present.

Reference implementations: `src/main/webapp/WEB-INF/jsp/lab/CA/ALL/labDisplay.jsp:564,939` and `src/main/webapp/WEB-INF/jsp/documentManager/showDocument.jsp:919,1169`.

### PathValidationUtils - File Path Security

**ALWAYS use PathValidationUtils** (`io.github.carlos_emr.carlos.utility.PathValidationUtils`) for file operations involving user input. It prevents path traversal attacks consistently across the codebase.

**Key Methods:**
```java
// For user-provided filenames where basename stripping is acceptable
File safeFile = PathValidationUtils.validatePath(userFilename, allowedDir);

// For one directory or filename segment that must be preserved exactly
String safeComponent = PathValidationUtils.validatePathComponent(rawComponent, "componentName");

// For validating existing or assembled file paths
PathValidationUtils.validateExistingPath(file, allowedDir);

// For validating uploaded files from Struts2/Tomcat
PathValidationUtils.validateUpload(uploadedFile);

// For complete upload validation (source + destination)
File dest = PathValidationUtils.validateUpload(sourceFile, filename, destDir);

// For checking if file is in allowed temp directory
if (PathValidationUtils.isInAllowedTempDirectory(file)) { ... }
```

**Migration from old patterns:**
```java
// OLD (inconsistent, error-prone)
if (!file.getCanonicalPath().startsWith(baseDir.getCanonicalPath() + File.separator)) {
    throw new SecurityException("Invalid path");
}

// NEW (consistent, robust)
PathValidationUtils.validateExistingPath(file, baseDir);
```

**Component validation rule:** use `validatePathComponent()` when request data becomes a directory
or filename segment that must not be normalized (queue ids, existing server filenames, document
subdirectories). Do not use `validatePath()` as a boolean guard and then build the real `File` from
the original request value; validate the component, use the returned value, then call
`validateExistingPath()` or `validatePath()` on the final filesystem target.

**Full documentation**: `docs/path-validation-utils.md`

## Package Structure (2025 Migration)

**CRITICAL**: Use NEW namespace `io.github.carlos_emr.carlos.*` for ALL code
- **Old**: `org.oscarehr.*`, `oscar.*` → **New**: `io.github.carlos_emr.carlos.*`
- **Note**: May encounter old names in comments/documentation; git history shows "renamed" files
- **DAO Classes**: `io.github.carlos_emr.carlos.commn.dao.*` (note: "commn" not "common")
- **Forms DAOs**: `io.github.carlos_emr.carlos.commn.dao.forms.*`
- **Models**: `io.github.carlos_emr.carlos.commn.model.*`
- **Exception**: `ProviderDao` at `io.github.carlos_emr.carlos.dao.ProviderDao`
- **Test Utilities**: Remain at `org.oscarehr.common.dao.*` for backward compatibility

## Struts2 Migration Pattern ("2Action")

**CRITICAL PATTERN**: All new Struts2 actions use `*2Action.java` naming convention

### 2Action Structure Template:
```java
public class Example2Action extends ActionSupport {
    private final SecurityInfoManager securityInfoManager;

    public Example2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        // MANDATORY security check
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_object", "r", null)) {
            throw new SecurityException("missing required sec object");
        }
        // Business logic
        return "success";
    }
}
```

### 2Action Categories:
1. **Simple Execute**: Single `execute()` method (e.g., `AddTickler2Action`)
2. **Method-Based**: Route via `method` parameter (e.g., `SystemMessage2Action`)
3. **Inheritance-Based**: Extend `EctDisplayAction` for encounter components

### GET/HEAD Rejection Contract (mutator 2Actions)

Any `*2Action` that performs a mutation MUST reject `GET`/`HEAD` before any
side-effect fires (DAO persist, manager call, event publish, file write).
The aggregated contract test that pins this for the in-scope slices is:

`src/test/java/io/github/carlos_emr/carlos/app/contract/MutatorActionGetRejectionContractTest.java`

**When you add a new mutator 2Action**, the contract test's discovery scan
will fail the build until you register the class in one of three lists at
the top of that file:

- `unconditionalMutators()` — the action rejects GET regardless of request
  params. The parameterized test drives GET against it directly.
- `CONDITIONAL_MUTATORS` — the action rejects GET only when a
  mutation-intent param is present (e.g. `submit=Save`, `statement=...`).
  You must also add a focused `*2ActionTest` in the same slice that drives
  a GET with mutation intent and asserts 405 or `SecurityException` plus
  `verifyNoInteractions(...)` on the mutation dependency.
- `NON_MUTATOR_GATES` — a read-scope gate that permits GET and only 405s
  on truly unsupported methods like DELETE/PUT.

**When a new slice is migrated**, extend `IN_SCOPE_PACKAGE_PREFIXES` with
the slice's package prefix only after the existing guarded actions in that
slice have been audited and classified in the contract manifest. For a
legacy-heavy slice where one mutator is migrated first, add that specific
class to `IN_SCOPE_EXPLICIT_CLASSES` and file/track the broader slice-audit
work instead of sweeping unreviewed legacy actions into the manifest.

### Struts 7.1.1 Notes

`*2Action` files extend `org.apache.struts2.ActionSupport` (the Struts 7
package location). The deprecated `com.opensymphony.xwork2.*` packages
from Struts 6.x were migrated to `org.apache.struts2.*` as part of the
Jakarta EE migration. A handful of gate / view actions extend
specialized base classes; treat the Struts 7 `ActionSupport` as the
default for new code.

**Migration History**:
- Struts 2.5.33 → 6.8.0 (January 2026, PR #88) — security fix for CVE-2025-64775
- Struts 6.8.0 → 7.1.1 (March 2026) — Jakarta EE namespace migration, `com.opensymphony.xwork2.*` → `org.apache.struts2.*`
- Caffeine 3.2.3 cache dependency required by Struts for internal caching

### Direct Response Actions

Actions that write directly to the servlet response, especially PDF, CSV,
XLS, ZIP, image, and JSON responses via `response.getOutputStream()` or
`response.getWriter()`, must terminate Struts processing with explicit
`return NONE` after the response is written. Do not return `"success"`,
another named result, or a bare `null` after streaming bytes. Named results
will resolve to JSP forwards, and in CARLOS' Struts 7 direct-response paths a
bare `null` is not reliable enough to prevent HTML error/page content from
being written into binary downloads.

Prefer separate action routes/classes for downloads and streamed responses
instead of mixing page navigation and direct response writes in one
method-dispatch action. If a legacy mixed action must remain, keep named
results only for paths that have not written to the response yet, such as
validation or authorization errors.

For PDFs and other generated binary responses, buffer output before setting
response headers where practical, validate that generated bytes are the
expected content type (for PDFs, `%PDF`), and only then write to the servlet
response. Any non-critical side effects such as "printed" comments or audit
annotations must either happen before response headers/body are touched or be
caught and logged after the binary write. An uncaught post-write exception can
let Struts replace the download with an HTML error page.

Direct-response actions must also own their error response. If generation
fails before bytes are written, call `sendError(...)` or write a deliberate
error response and return `NONE`; do not return an unmapped `failure`/`error`
result. PR #2043 showed this surfaces in CARLOS as `CARLOS Error: 0` because
`errorpage.jsp` has no real HTTP status after Struts result resolution fails.

Track broader cleanup of legacy mixed direct-response actions in
GitHub issue #2064.

## Layer Names — class naming policy

**Suffix == role + lifecycle.** Pick the most specific verb-suffix that fits; fall back to `*Service` only when nothing more specific applies. **Never** combine two role-suffixes (no `*LoaderService`, `*ServiceManager`).

Sanctioned suffixes for new code:
`*Action`, `*ViewModelAssembler`, `*ViewModel`, `*Loader`, `*Resolver`, `*Composer`, `*Validator`, `*Persister`, `*Calculator`, `*Parser`, `*ImportService`, `*Service`, `*Dao`, `*Dto`, `*Command`. Static-utility classes use a domain noun with no suffix.

Forbidden in new code: `*Prep`, `*Manager`, `*Helper`, `*Utils`, compound suffixes except the sanctioned `*ImportService` file-import workflow suffix. DAOs may not inject other DAOs (cross-DAO orchestration goes in a `*Service`).

Full policy + decision rules + retired-suffix migration guidance: **`docs/architecture/layer-names.md`**.

## Healthcare Domain Context

**Core Medical Modules**:
- **PMmodule/**: Program management and case management
- **billing/**: Province-specific billing (BC, ON) with diagnostic codes. Ontario-specific architecture, layer conventions, fetch policies, and refactor history are documented in **[`docs/billing-ontario-module.md`](docs/billing-ontario-module.md)** — read this before extending `billings/ca/on/**`.
- **prescription/**: Drug management with ATC codes, interaction checking
- **lab/**: HL7 lab results
- **prevention/**: Immunization tracking with provincial schedules
- **demographic/**: Patient data with HIN (Health Insurance Number) management

## Development Workflow

**DevContainer Environment**:
- Docker-based development with debugging on port 8000
- Custom terminal with tool reminders on bash startup

**Build & Deploy Cycle**:
1. `make clean` → `make install --run-tests` → `server log`
2. For quick iterations: `make install` (skips tests)
3. Debug logging: `debug-on` → `server restart` → `debug-off`

## Test Framework (JUnit 5)
**Key Features**:
- **Single Structure**: All tests live under `src/test/`. The legacy JUnit 4 suite has been removed; the historical `src/test-modern/` directory has been collapsed into `src/test/`.
- **Modern Stack**: JUnit 5, AssertJ, H2 in-memory database, BDD naming
- **Spring Integration**: Full Spring context with transaction support
- **Multi-File Architecture**: Component-first naming (`TicklerDao*Test`) for scalability
- **Documentation**: Complete guide at `docs/test/modern-test-framework-complete.md`
- **Context Guide**: `docs/test/claude-test-context.md` (auto-injected by hooks when working on tests)
- **Unit Test Support**: `CarlosUnitTestBase` for mocked tests without database
- **Manager Testing**: @Nested classes for organizing 100+ tests per manager (see `DemographicManagerUnitTest`)

### Test Organization Standards

Tests use hierarchical tagging for filtering:
- **Required Tags**: `@Tag("integration")`, `@Tag("dao")` (test type & layer)
- **CRUD Tags**: `@Tag("create")`, `@Tag("read")`, `@Tag("update")`, `@Tag("delete")`
- **Extended Tags**: `@Tag("query")`, `@Tag("search")`, `@Tag("filter")`, `@Tag("aggregate")`

**Running Tagged Tests:**
```bash
mvn test -Dgroups="unit"           # Unit tests only
mvn test -Dgroups="integration"    # Integration tests only
mvn test -Dgroups="create,update"  # Specific operations
```

### BDD Test Naming Convention

Tests use BDD (Behavior-Driven Development) naming for clarity. Use one project-wide Java style:

**Pattern: `should<Action>_<prepositionOrContext><Condition>()`**
```java
void shouldReturnTickler_whenValidIdProvided()
void shouldThrowException_whenTicklerNotFound()
void shouldReturnSpecialists_byServiceName()
void shouldPersistMeasurement_withBloodPressureData()
void shouldConvertExtensionList_toMapKeyedByExtKey()
void shouldReturnTrue_forOMedsCppCode()
```

**Rules**: exactly ONE underscore separator, camelCase throughout, `should` prefix required, and the segment after the underscore starts lowercase.
The preposition/context after the underscore (`when`, `by`, `for`, `with`, `to`, `from`, etc.) should be whichever reads most naturally for the test scenario.
There is no zero-underscore exception for "simple" tests; use a short context such as `_forDefaultCase`, `_forTypeContract`, or `_withValidInput`.
Do not use pure camelCase (`shouldReturnTicklerWhenValidIdProvided`), snake_case (`should_return_tickler_when_valid_id_provided`), or multi-underscore names (`shouldReturnFoo_whenBar_andBaz`).

**Benefits**: Self-documenting, clear failure messages, searchable

### Test Context Configuration

The codebase has legacy patterns (SpringUtils static access, mixed Hibernate/JPA, circular dependencies) that require specific test setup. See **[Test Writing Guide](docs/test/test-writing-guide.md)** for detailed configuration patterns.
**Key points**: Extend `CarlosTestBase` (handles SpringUtils), define beans manually in test context, explicitly list entities in persistence.xml.

**Writing Tests - CRITICAL**:
When asked to write tests, you MUST:
1. **First examine the actual interface/class** being tested
2. **Only test methods that actually exist** in the codebase
3. **Never invent or assume method names** - verify they exist
4. **Choose the right base class**:
   - `CarlosTestBase` - Integration tests with Spring context and database
   - `CarlosUnitTestBase` - Unit tests with mocked SpringUtils (no database)
   - Domain-specific bases like `DemographicUnitTestBase` - Unit tests with test data builders
5. **Use @PersistenceContext(unitName = "testPersistenceUnit")** for EntityManager (integration tests only)
6. **For Manager unit tests**: Register SpringUtils mocks BEFORE creating static mocks (LogAction, etc.)

### Integration Test Pitfalls (Hibernate/H2)

The test infrastructure uses **dual persistence contexts** (JPA EntityManager + Hibernate Session) sharing one JDBC connection via `TransactionAwareDataSourceProxy`. This creates several non-obvious pitfalls:

**1. Flush Context Mismatch**
DAOs extending `HibernateDaoSupport` write through the Hibernate Session. `entityManager.flush()` only flushes the JPA context — it will NOT flush Hibernate writes. Always use `hibernateTemplate.flush()` (available via `CarlosTestBase`) when testing `HibernateDaoSupport`-based DAOs.
```java
// WRONG - won't flush HibernateDaoSupport DAO writes:
entityManager.flush();

// CORRECT - flushes the Hibernate Session:
hibernateTemplate.flush();
```

**2. H2/MySQL BOOLEAN Incompatibility**
H2 uses actual `BOOLEAN` type while MySQL uses `TINYINT(1)`. HQL comparisons like `locked<>'1'` work in MySQL (comparing TINYINT with string) but fail in H2. Fix production HQL to use proper boolean comparisons: `cmn.locked = false` instead of `cmn.locked != '1'`. This is both more correct and cross-database compatible.

**3. HQL LIKE Queries Need Explicit Wildcards**
DAO methods using HQL `LIKE` do not auto-add `%` wildcards. Tests must include them:
```java
// WRONG - will only match exact string:
dao.searchNotes("111", "diabetes");

// CORRECT - wraps with wildcards for partial matching:
dao.searchNotes("111", "%diabetes%");
```
Note: This is standard SQL behavior, NOT a production bug. Callers provide wildcards from the UI layer.

**4. DAO Methods May Override Test Data**
Some DAO `save*()` methods override fields like `update_date` with `new Date()`. When testing date-based queries, re-set the date after saving:
```java
caseManagementIssueDAO.saveIssue(cmi);  // Overwrites update_date with now()
cmi.setUpdate_date(desiredDate);         // Re-set to test-specific date
hibernateTemplate.flush();               // Persist the corrected date
```
Always check the DAO implementation before assuming test data is persisted as-is.

**5. SpringUtils Identity Across Multiple Contexts**
When running the full test suite, classes with `@TestPropertySource` create separate Spring contexts. `SpringUtils.getBean()` may return instances from a different context than `@Autowired` injection. Do NOT assert instance identity (`isSameAs`/`isEqualTo`). Instead assert type:
```java
// WRONG - fails across multiple Spring contexts:
assertThat(springUtilsDao).isSameAs(autowiredDao);

// CORRECT - verifies correct type regardless of context:
assertThat(springUtilsDao).isInstanceOf(autowiredDao.getClass());
```

**6. Read DAO Method Semantics Carefully**
DAO method names can be misleading. For example, `getProviders(boolean active)` returns providers filtered by that status — `getProviders(false)` returns INACTIVE providers, not ALL providers. Always read the DAO implementation before writing test assertions.

## Code Quality Standards

**Security (CodeQL Integration)**:
- Null-safe CARLOS encoder for all JSP outputs — prefer `<carlos:encode>` tag or `${carlos:forXxx()}` EL functions (see [OWASP Encoding](#owasp-encoding--xss-prevention))
- Parameterized SQL queries (never concatenation)
- File upload filename validation
- CodeQL security scanning must pass

**Static analysis (Semgrep, SpotBugs + Find Security Bugs)**: these scanners run alongside
CodeQL/PMD and upload SARIF to the Security tab (see `docs/static-analysis-workflows.md`).
Keep real-defect detectors on and fix real flows first. Known false positives should be handled
with the scanner's narrowest supported suppression: SpotBugs uses `.github/spotbugs/spotbugs-exclude.xml`
or per-site `@SuppressFBWarnings`; Semgrep uses CARLOS sanitizer-aware rules in `.semgrep/` and
rule-specific `nosemgrep` comments only when the code is already encoded/sanitized and the rule
cannot model that sanitizer. Semgrep CI filters suppressed SARIF results before GitHub Code Scanning
upload, so `nosemgrep` suppressions must remain specific and justified; do not blanket-disable
Semgrep Pro or broad rule groups to clear PR noise. The `IMPROPER_UNICODE` detector is
*informational* — it flags `equalsIgnoreCase`/`toLowerCase`/`Normalizer` case folding **regardless
of `Locale`** (so it cannot be cleared by adding `Locale.ROOT`), and almost all hits are intended
case-insensitive domain comparisons. It is suppressed **per-site** with
`@SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = ...)` **plus a mandatory adjacent
`//` comment** stating the same reason. New `@SuppressFBWarnings` of any pattern must follow this
annotation-plus-inline-comment convention. Genuinely trust-path case folds are tracked for
locale-safe hardening in issue #2496 (CVE-2024-38827 class).

**Spring Integration Pattern**:
```java
private final SomeManager someManager;

public Example2Action(SomeManager someManager) {
    this.someManager = someManager;
}
```

**Documentation Standards**:
- **JavaDoc Required**: Public entrypoint classes and non-obvious public/protected methods should have contract-level JavaDoc. Carrier DTOs, trivial records, getters/setters, and tiny private helpers do not need mechanical method-by-method JavaDoc.
- **No @author Tags**: Do NOT add @author annotations (misleading after Bitbucket→GitHub migration)
- **@since Tags**: Use git history to determine accurate dates: `git log --follow --format="%ai" <file> | tail -1`
- **Parameter Documentation**: Use `@param` tags when the method contract is not obvious from the signature or when legacy field semantics matter
- **Return Documentation**: Use `@return` tags when callers need help understanding shape, nullability, or compatibility expectations
- **Exception Documentation**: Document thrown exceptions when failure modes are part of the caller contract
- **Deprecation**: Use @deprecated with migration guidance to newer APIs
- **Legacy DTOs**: Keep fields private and document compatibility behavior at the class level, especially fixed-width file shapes and money normalization. Avoid mechanical JavaDoc on trivial bean accessors.
- **JSP Documentation**: Add comprehensive JSP comment blocks after copyright headers with purpose, features, parameters, and @since
- **Package-Level Docs**: Use `package-info.java` to document ownership boundaries and conventions for `assembler`, `command`, `dto`, and `support` packages
- **Inline Comments**: Comment the why: legacy compatibility, security/compliance constraints, file-format offsets, transaction boundaries, and non-obvious control flow. Do not narrate obvious assignments or getters/setters.

**Copyright Header Standards**:
- **New Files**: Use CARLOS project header (see `docs/copyright-header-carlos.md`)
- **Modified Files**: Preserve all existing headers; optionally add modification notice for functional changes
- **Never Remove**: Existing copyright notices - this violates GPL
- **Never Change GPL Version**: Preserve GPL2, GPL2+, GPL3, etc. exactly as-is in each file
- **Attribution**: See `NOTICE.md` for full project attribution and heritage documentation

## Healthcare Integration Standards

**Billing Identifier Abbreviations**:
- Java class/member names use Java-style acronym casing: `On`, `Ohip`, `Ra`, `Moh`, `Inr`, `Gst`, `Mri`, `Edt`, and `Obec`.
- `Diag` is the only accepted non-official short form in billing identifiers.
- Keep regular domain words spelled out: `ThirdParty`, `Specialist`, `Report`, `Payment`, and `Address`.
- Do not introduce legacy compressed names such as `3rd`, `Dig`, `Db`, `Obj`, `Hlp`, `Bean`, or `Handler`.
- Official prose, UI labels, external protocols, and Ministry/OHIP document names may keep uppercase acronyms like `OHIP`, `RA`, `MOH`, `INR`, and `GST`.

**Standards & Protocols**:
- **HL7 v2/v3**: Full message processing with MSH, PID, OBX, ORC, OBR segment handlers
- **FHIR R4**: HAPI FHIR 6.10.5 with resource filters and healthcare provider context
- **SNOMED CT**: Clinical terminology with core dataset loading
- **ICD-9/ICD-10**: Diagnosis coding systems fully integrated
- **ATC Codes**: Anatomical Therapeutic Chemical classification for medications
- **DICOM**: Medical imaging support for diagnostic images

**Provincial Healthcare Systems**:
- **Teleplan**: BC MSP billing system with specialized upload/download
- **MCEDT**: Medical Claims Electronic Data Transfer
- **DrugRef**: Drug reference database integration

**Medical Forms Integration**:
- **Rourke Growth Charts**: Multiple versions (2006, 2009, 2017, 2020) for pediatric care
- **BCAR Forms**: British Columbia Antenatal Record for pregnancy care
- **Mental Health Assessments**: Standardized clinical assessment forms
- **Laboratory Requisitions**: Province-specific lab ordering forms

## Drools Decision Support System

**Version**: Drools 10.1.0 (KIE API, executable model), upgraded from 7.74.1.Final for Jakarta EE compatibility
**Documentation**: Full architecture, DRL file reference, and known bugs in `docs/drools-decision-support-system.md`

**Key Classes**:
- `DroolsHelper` — compiles DRL to `KieBase` via standard KIE API (`KieServices`/`KieFileSystem`/`KieBuilder`)
- `RuleBaseFactory` — thread-safe `QueueCache` of compiled `KieBase` objects (24h TTL, SHA-256 keyed)
- `DroolsCompilationException` — checked exception for DRL compilation failures
- `RuleBaseCreator` — generates DRL from `DSCondition` objects, compiles and caches
- `TargetColour` / `Recommendation` — generate DRL from flowsheet XML for color indicators and clinical reminders
- `WorkFlowDS` — wraps `KieBase` for workflow rule execution (e.g., Rh pregnancy management)

**Test Coverage**: Tests under `src/test/` tagged `@Tag("drools")`. Run with `make install --run-unit-tests` or `mvn test -Dgroups="drools"`. See `docs/drools-decision-support-system.md#test-coverage` for details.

## Technology Stack Details

### Core Technologies
- **Java 21** with modern language features and Jakarta XML Binding
- **Spring Framework 7.0.6**: IoC container, MVC, AOP, Security, transaction management (Jakarta EE 11)
- **Spring Security 7.0.4**: Crypto module for password hashing
- **Hibernate 7.2.7**: ORM framework with custom MySQL dialect (`OscarMySQL5Dialect`)
- **Maven 3**: Build management with 200+ healthcare-specific dependencies
- **Apache Tomcat 11.0**: Web application server with debugging enabled (Jakarta EE 11)
- **MariaDB/MySQL**: Database with custom connection tracking (`OscarTrackingBasicDataSource`)

### Web Technologies
- **Struts 7.1.1**: Modern actions (2Action pattern) using `org.apache.struts2.ActionSupport`
  - Upgraded from 6.8.0 (March 2026) - Jakarta EE namespace migration
  - `*2Action` classes migrated from `com.opensymphony.xwork2.*` to `org.apache.struts2.*`
  - Requires Caffeine 3.2.3 cache dependency for internal caching
- **Apache CXF 4.1.5**: Web services framework for healthcare integrations (Jakarta EE 10, upgrade to 4.2.x pending Jackson 3 migration)
- **JSP/JSTL**: View layer with extensive medical form templates
- **Bootstrap 5.3.0**: Modern UI framework loaded from CDN for responsive design
- **JavaScript/CSS/jQuery**: Frontend with healthcare-specific UI components
- **Vanilla JavaScript**: Progressively replacing jQuery dependencies where possible

### Security Libraries
- **OWASP CSRFGuard 4.5 (Jakarta edition)**: CSRF protection with auto-injected tokens (see `docs/csrf-protection-architecture.md`)
- **OWASP Encoder** (`encoder` 1.4.0): `Encode.*` static methods for Java code and JSP scriptlets
- **OWASP Encoder JSP** (`encoder-jakarta-jsp` 1.4.0): underlying tag library behind the CARLOS null-safe wrapper (TLD URI `owasp.encoder.jakarta.advanced`). Do not reference `<e:forXxx>` / `${e:forXxx()}` directly in new code — use `<carlos:encode>` / `${carlos:forXxx()}`.
- **BCrypt**: Password hashing for provider authentication
- **Bouncy Castle**: Cryptographic functions for PHI protection

### Spring Configuration Architecture
Multiple modular application contexts:
- `applicationContext.xml` - Core Spring configuration
- `applicationContextREST.xml` - REST APIs with OAuth 1.0a
- `applicationContextHRM.xml` - Hospital Report Manager
- `applicationContextCaisi.xml` - CAISI community integration
- `applicationContextFax.xml`, `applicationContextJobs.xml` - Specialized modules

## REST API & Web Services

### OAuth 1.0a Authentication (Migration in Progress)
- **Current Migration**: CXF OAuth2 → ScribeJava OAuth1.0a
- **New Classes**: `OscarOAuthDataProvider`, `OAuth1Executor`, `OAuth1Utils`
- **Healthcare Context**: Provider-specific credentials with facility integration
- **Services Migrated**: ProviderService, ConsentService with enhanced error handling

### Core API Services (25+ endpoints)
- **DemographicService**: Patient demographics with HIN management
- **ScheduleService**: Appointment scheduling with reason codes and billing types
- **PrescriptionService**: Medication management with ATC codes and interaction checking
- **LabService**: Laboratory results with HL7 integration
- **PreventionService**: Immunization tracking with provincial schedules
- **ConsultationWebService**: Referral management and specialist communication
- **DocumentService**: Medical document management with privacy statement injection

### SOAP Web Services
- **CXF-based**: Healthcare system integration with WS-Security
- **Inter-EMR**: Data sharing via Integrator system across multiple OSCAR installations
- **Provincial Billing**: Direct integration with Teleplan (BC MSP) and other systems

### Active Code Cleanup (2025)
- **Modules Removed**: MyDrugRef, BORN integration, HealthSafety, legacy email notifications

### Code Maintenance Approach
- **Active cleanup**: Project aggressively removes unused code and dependencies
- **Recently removed**: MyDrugRef, BORN integration, HealthSafety, legacy email notifications
- **Assumption**: Don't assume legacy features still exist - check current codebase
- **Philosophy**: Reduce attack surface by removing unused functionality

### Development Environment Context
- **DevContainer primary**: Development done in Docker containers with debugging enabled
- **Debug port**: 8000 for remote debugging
- **Database**: Hibernate schema validation disabled - manual migration control
- **Logging**: Enhanced logging in development environment with `debug-on`/`debug-off` aliases
- **Custom Terminal**: Welcome message displays all available tools and shortcuts on bash startup

### DevContainer Custom Scripts
Located in `/scripts` directory within the container (copied from `.devcontainer/development/scripts/`):
- `make lock` - Update Maven dependency lock file
- **Build Process**: Stops Tomcat → Builds WAR → Creates symlink → Starts Tomcat
- **Configuration**: Auto-creates `over_ride_config.properties` from template
- **Parallel builds**: Uses `-T 1C` for faster Maven builds
- **Deployment**: Handles versioned WAR directories with symlinks to `/usr/local/tomcat/webapps/carlos`

## Architecture Patterns

### Layered Architecture
- **Web Layer**: Controllers (Actions) handle HTTP requests
- **Service Layer**: Business logic and workflow orchestration
- **DAO Layer**: Data access objects for database operations
- **Model Layer**: Domain entities and value objects

### Spring Configuration
- Extensive use of Spring IoC container
- Transaction management with Spring AOP
- Security configuration with Spring Security
- Multiple application contexts for different modules

### Legacy Integration & Struts2 Migration Pattern ("2Action") - CRITICAL PATTERN

#### **Migration Strategy Overview**
CARLOS EMR uses a unique incremental migration approach from Struts 1.x to Struts 2.x using a "2Action" naming convention that allows both frameworks to coexist during the transition period.

#### **2Action Naming Convention & Structure**
- **Naming Pattern**: All migrated Struts2 actions follow `*2Action.java` naming (e.g., `AddTickler2Action`, `DisplayDashboard2Action`, `Login2Action`)
- **Class Structure**:
  ```java
  public class Example2Action extends ActionSupport {
      private final SecurityInfoManager securityInfoManager;
      private final SomeManager someManager;

      public Example2Action(SecurityInfoManager securityInfoManager, SomeManager someManager) {
          this.securityInfoManager = securityInfoManager;
          this.someManager = someManager;
      }

      public String execute() {
          HttpServletRequest request = ServletActionContext.getRequest();
          HttpServletResponse response = ServletActionContext.getResponse();
          // Security check pattern
          if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_object", "r", null)) {
              throw new SecurityException("missing required sec object");
          }
          // Business logic
          return "success";
      }
  }
  ```

#### **Struts2 Action Categories**

**1. Simple Execute Actions**
- Single `execute()` method handling all logic
- Examples: `AddTickler2Action`, `EditTickler2Action`
- Return simple result strings like "success", "close", "error"

**2. Method-Based Actions**
- Use `method` parameter to route to different methods within the action
- Pattern: `String mtd = request.getParameter("method");`
- Examples: `SystemMessage2Action` (view/edit methods)
- Allows multiple related operations in one action class

**3. Inheritance-Based Actions**
- Extend specialized base classes like `EctDisplayAction`
- Examples: `EctDisplayMeasurements2Action`, `EctDisplayRx2Action`
- Inherit common functionality while implementing specific `getInfo()` methods
- Used for encounter display components in left navbar

#### **Integration Patterns**

**Request/Response Access**
```java
HttpServletRequest request = ServletActionContext.getRequest();
HttpServletResponse response = ServletActionContext.getResponse();
```
- Direct servlet API access maintained for compatibility
- No dependency on Struts2 action properties

**Spring Integration**
```java
private final SecurityInfoManager securityInfoManager;
private final TicklerManager ticklerManager;

public Some2Action(SecurityInfoManager securityInfoManager, TicklerManager ticklerManager) {
    this.securityInfoManager = securityInfoManager;
    this.ticklerManager = ticklerManager;
}
```
- Prefer constructor injection for new actions and services.
- Legacy Struts-created routers that require a no-arg constructor may call `SpringUtils.getBean()` inside that constructor and expose a package-private test constructor.
- Do not add new `field = SpringUtils.getBean(...)` shims.

**Security Pattern (Required)**
```java
if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_objectname", "r", null)) {
    throw new SecurityException("missing required sec object");
}
```
- Every 2Action MUST include security validation
- Uses healthcare-specific role-based access control
- Throws SecurityException for unauthorized access

#### **Configuration Approach**

**Struts.xml Mapping**
```xml
<action name="login" class="io.github.carlos_emr.carlos.login.Login2Action">
    <result name="provider" type="redirect">/provider/providercontrol</result>
    <result name="failure">/WEB-INF/jsp/login/logout.jsp</result>
</action>
```
- Uses extensionless action routes
- Spring object factory integration: `<constant name="struts.objectFactory" value="spring"/>`
- View JSPs live under `/WEB-INF/jsp/**`

**URL Compatibility**
- New code must use extensionless Struts routes
- New view pages should not be exposed as public JSP entrypoints
- Caller updates must target actions, not JSP files

#### **Best Practices for 2Action Development**
**1. Security First**
- Always include security privilege checks
- Use appropriate security objects for healthcare data
- Log security violations appropriately

**2. Error Handling**
- Use context-appropriate CARLOS null-safe encoding when outputting user data:
  - `SafeEncode.forHtmlContent()` / `${carlos:forHtmlContent()}` / `<carlos:encode value="..."/>` - HTML body content
  - `SafeEncode.forHtmlAttribute()` / `${carlos:forHtmlAttribute()}` / `<carlos:encode value="..." context="htmlAttribute"/>` - HTML attribute values
  - `SafeEncode.forJavaScript()` / `${carlos:forJavaScript()}` / `<carlos:encode value="..." context="javaScript"/>` - JavaScript string contexts
  - `SafeEncode.forJavaScriptAttribute()` / `${carlos:forJavaScriptAttribute()}` - JS in HTML attributes
  - `SafeEncode.forCssString()` / `${carlos:forCssString()}` - CSS string values
  - `SafeEncode.forUri()` / `${carlos:forUri()}` and `SafeEncode.forUriComponent()` / `${carlos:forUriComponent()}` - URL paths/parameters
- In JSP views, prefer `<carlos:encode>` tag; use `${carlos:forXxx()}` EL functions for inline use inside attribute strings or URLs (see [OWASP Encoding](#owasp-encoding--xss-prevention))
- Implement proper exception handling
- Return appropriate result strings

**3. Spring Integration**
- Prefer constructor injection; reserve `SpringUtils.getBean()` for legacy compatibility shims that cannot be constructor-wired yet.
- Leverage existing Spring-managed services
- Maintain transactional boundaries

**4. View / Endpoint Design**
- Put new JSP views under `src/main/webapp/WEB-INF/jsp/**`
- Add new page entrypoints as Struts actions in the correct `struts-*.xml` file
- Use extensionless routes such as `/login`, `/form/setupSelect`, `/eform/efmshowform_data`
- Point action results at internal `WEB-INF` JSPs or other extensionless actions, not public JSP paths
- For view-only pages, prefer a small gate action that enforces security and then forwards internally
- When migrating a former public `index.jsp`, decide whether the old clean section-root URL
  (for example `/administration/`) was part of the user-facing contract. Check nav links,
  popup targets, generated menu URLs, and relative links, not just explicit `index.jsp`
  references.
- If that clean section-root URL was part of the contract, preserve it with a small
  explicit compatibility mapping. Do not make `/section/index` the new preferred public URL,
  and do not use a generic catch-all `append /index` rewrite.

**5. Healthcare Context**
- Include audit logging for patient data access
- Follow PHI protection patterns
- Use healthcare-specific validation

This migration pattern allows CARLOS EMR to modernize incrementally while maintaining system stability and regulatory compliance throughout the transition process.

## File Patterns

### Key File Types
- `**/*DAO.java` - Database access objects
- `**/*Action.java` - Struts/Spring MVC controllers
- `**/web/**` - Web layer components
- `**/model/**` - Entity/domain models
- `**/service/**` - Business logic services
- `database/mysql/**` - Schema migrations and SQL scripts
- `src/main/webapp/**` - Web resources (JSP, CSS, JS)

### Configuration Files
- **Struts Configuration** (modular split):
  - `struts.xml` - Parent config with global constants and `<include>` directives for 17 module files
  - `struts-{admin,billing,clinical,demographic,document,eform,encounter,form,integration,lab,login,messenger,pmmodule,prescription,provider,report,scheduling}.xml` - Domain-specific action mappings
  - Each module file declares its own uniquely-named package (e.g., `name="billing"`) with `namespace="/"` and `extends="struts-default"`
  - New actions should be added to the appropriate domain-specific module file, not to `struts.xml`
  - Canonical action routes are extensionless (`struts.action.extension=""`)
  - Static assets are excluded from Struts by `struts.action.excludePattern`
- **Database Configuration**:
  - Custom MySQL dialect: `OscarMySQL5Dialect`
  - Connection tracking: `OscarTrackingBasicDataSource`
  - Legacy MySQL compatibility settings
- **Security Configuration**:
  - `web.xml` - Filter chain with CSRFGuard 4.5 CSRF protection (see `docs/csrf-protection-architecture.md`)
  - Privacy statement filters and audit logging
  - Multi-factor authentication and SAML 2.0 support
- `pom.xml` - Maven with 200+ healthcare-specific dependencies

## Development Environment

### Docker Setup
- Development environment runs in Docker containers
- Tomcat container with Java 21 and debugging enabled
- MariaDB database container
- Maven repository caching for faster builds
- Port 8080 for web application, 3306 for database

### IDE Configuration
- VS Code with Java extension pack
- Remote development in Docker container
- Debugging support on port 8000

## Database Schema Patterns

### Core Healthcare Tables
- **demographic**: 50+ fields including HIN, rostering status, multiple addresses
- **allergies**: Drug/non-drug allergies with severity, reaction tracking, regional identifiers
- **appointment**: Scheduling with reason codes, billing types, status tracking
- **casemgmt_note**: Clinical notes with encryption support and issue-based organization
- **prevention**: Immunization/prevention tracking with configurable schedules
- **drugs**: Prescription management with ATC codes, interaction checking, renal dosing
- **measurementType**: Vital signs and clinical measurements with flowsheet integration
- **billing**: Province-specific billing with diagnostic codes and claims processing

### Audit and Compliance Patterns
- Every table includes `lastUpdateUser`, `lastUpdateDate` for audit trails
- Complex healthcare schema with 50+ fields in `demographic` table
- Comprehensive logging of all patient data access via `UserActivityFilter`
- Privacy-compliant data handling with PHI filtering throughout application
- Multi-jurisdictional support with province-specific configurations

## Database Schema & Migration System

**Database**: MariaDB/MySQL with comprehensive healthcare schema dating back to 2006
**Migration Pattern**: Date-based SQL scripts (`update-YYYY-MM-DD-description.sql`)

### Core Database Files (`database/mysql/`)
```bash
# Initial Schema Setup
oscarinit.sql          # Core database schema
oscarinit_2025.sql     # Current 2025 schema version
oscardata.sql          # Initial reference data
oscarinit_bc.sql       # British Columbia specific
oscarinit_on.sql       # Ontario specific

# Medical Coding Systems
icd9.sql / icd10.sql   # Diagnosis codes (ICD-9/ICD-10)
measurementMapData.sql # Clinical measurements mapping
SnomedCore/           # SNOMED CT clinical terminology
olis/                 # Ontario Labs Information System

# Provincial Healthcare Data
bc_billingServiceCodes.sql     # BC medical service codes
bc_pharmacies.sql              # BC pharmacy directory
firstNationCommunities_lu_list.sql # First Nations communities
```

**Development Database**:
- Container: `db-connect` alias → MariaDB as root user
- Port 3306 with health checks, 2G memory limit
- Seeded with medical forms (Rourke charts, BCAR) and reference data
- **Default login**: username `carlosdoc`, password `carlos2026`, PIN `2026`

---

## Issue Management & Automation

### Automated Issue Lifecycle
When a PR is merged that references an issue (using keywords like `fixes #123`, `closes #456`, `resolves #789`):
1. **Automatic notification** - Issue reporter is @mentioned with request to verify the fix
2. **Status tracking** - Issue gets `status: pending-verification` label
3. **Verification response** - If reporter comments "verified" or "fixed", issue auto-closes with `status: verified-fixed`
4. **Failed fix detection** - If reporter says "not fixed" or "still broken", adds `status: fix-failed` label

**Automated Status Labels:**
- `status: pending-verification` - Fix has been merged, awaiting reporter confirmation
- `status: verified-fixed` - Reporter confirmed the fix works
- `status: fix-failed` - Reporter confirmed the fix doesn't work

### Issue Classification System

CARLOS uses a three-layer classification system: **Issue Types** (org-level, single-select), **Project Board Fields** (per-item metadata), and **Labels** (stackable cross-cutting attributes).

#### Issue Types (Organization-Level)

Issue types are configured at the GitHub organization level (`carlos-emr`) and provide single-select categorization. Every issue must have exactly one type.

| Type | Color | Description |
|------|-------|-------------|
| Bug | Purple | Something isn't working as expected |
| Feature | Blue | New feature or enhancement request |
| Security | Red | Security vulnerabilities, hardening, or audit findings |
| Documentation | Purple | Documentation improvements or additions |
| Maintenance | Yellow | Code refactoring, dependency updates, migrations |
| Test | Green | Test coverage improvements or additions |
| Performance | Orange | Performance optimization or profiling |
| Research | Gray | Code reviews, investigations, and exploratory analysis |

**Note**: "Task" is NOT an issue type - it is a value in the project board's Kind field. Research covers code reviews (`Review:` prefix), investigations (`Investigate:` prefix), and discussions.

#### Project Board Fields

Project Board #1 (`carlos-emr development`) has three custom fields:

**Status** (single-select, 12 options):
- Needs Triage → Backlog → Todo → Blocked → In Progress → In Review → QA → Staging → Done → Won't Do → Duplicate → On Hold

**Priority** (single-select, 4 levels):
- `1-Critical` - Active exploit vectors (SQL injection, XSS with patient data exposure, authentication bypass)
- `2-High` - Security vulnerabilities needing fix, critical migration blockers, authentication bypasses
- `3-Medium` - Security hardening, UI modernization, feature work, migrations, code reviews of medium-risk areas
- `4-Low` - Nice-to-have features, low-risk reviews, minor chores, investigations, cosmetic improvements

**Kind** (single-select, 2 options):
- `Epic` - Large initiative spanning multiple issues
- `Task` - Individual work item (default for all issues)

**Priority guidance**: Authorization issues within the circle-of-care model (shared provider access patterns) are Medium unless they involve exploitable authentication bypass. Be conservative - when in doubt, classify lower.

#### Labels (Stackable Attributes Only)

Labels are reserved for cross-cutting attributes that can apply alongside any issue type. Do NOT use labels for categorization that duplicates issue types or priority.

**Current labels:**
- `blocker` - Blocks other work from proceeding
- `discussion` - Needs team discussion before action
- `duplicate` - Duplicate of another issue
- `good first issue` - Suitable for new contributors
- `help wanted` - Extra attention needed from community
- `hibernate-6-prep` - Related to Hibernate 6 migration preparation
- `wontfix` - Will not be addressed

**Automated status labels** (set by workflows):
- `status: pending-verification` - Fix merged, awaiting reporter confirmation
- `status: verified-fixed` - Reporter confirmed the fix works
- `status: fix-failed` - Reporter confirmed the fix doesn't work

## Commit Standards & Quick Reference

**Commit Format**: [Conventional Commits](https://www.conventionalcommits.org/) - `feat:`, `fix:`, `chore:`, `update:`

**Key Files**:
- `CLAUDE.md` - AI context (this file)
- `pom.xml` - 200+ healthcare Maven dependencies
- `database/mysql/` - 19+ years of healthcare schema evolution (2006-2025)
- `.devcontainer/` - Docker development with AI tools

**Critical Patterns**:
- **Project Name**: "CARLOS EMR" or "CARLOS" in all user-facing content
- **Security**: `SecurityInfoManager.hasPrivilege()` + OWASP encoding required
- **Actions**: `*2Action.java` pattern for Struts2 migration
- **Packages**: `io.github.carlos_emr.carlos.*` (new) vs `org.oscarehr.*` (legacy)
- **Database**: Date-based migrations, audit trails (`lastUpdateUser`, `lastUpdateDate`)

---

## Claude Workflow Guidelines

**Context**: Claude operates both as a GitHub Actions workflow (triggered by @claude mentions) and directly via Claude Code CLI. These guidelines apply to both contexts.

### Task Handling
1. **Simple Questions/Reviews**: Answer directly in comments, reference specific files and line numbers
2. **Straightforward Changes** (1-3 files): Create feature branch, implement, create PR
3. **Complex Changes**: Ask clarifying questions first, create implementation plan, proceed after approval

### Branch Protection
- **Protected Branches**: `develop`, `main`, `experimental` - direct commits prohibited
- **All changes** must go through pull requests with review
- Claude creates feature branches: `claude/issue-<number>-<timestamp>`

### Security Checklist (Every Code Change)
- [ ] Context-appropriate OWASP encoding for user inputs (see [OWASP Encoding](#owasp-encoding--xss-prevention))
- [ ] Parameterized SQL queries (never concatenation)
- [ ] `SecurityInfoManager.hasPrivilege()` checks in all actions
- [ ] `PathValidationUtils` for file operations
- [ ] No PHI in logs or error messages

### PR Requirements
- ✅ Target `develop` branch (not `main`)
- ✅ Include tests for new functionality
- ✅ Reference related issues (`fixes #123`)
- ✅ Add "Generated with Claude Code" signature
- ✅ Ensure CI checks pass before requesting review

### When Blocked
If Claude encounters issues it cannot resolve:
- Document the problem clearly in comment
- Explain what was attempted and why it failed
- Provide specific error messages
- Ask for guidance on preferred resolution

---

## AI-Assisted Development

### Claude Code Capabilities

Claude Code is integrated into this repository with the following capabilities:

**Automated Actions (on @claude mention or PR events):**
- Post PR review comments with inline code annotations
- Create and update issues with proper labels
- Create feature branches and push code changes
- Create pull requests automatically (via `gh pr create`)
- Access CI/CD status and logs for debugging
- **Note**: @claude triggers are restricted to repository OWNER, MEMBER, or COLLABORATOR only. CONTRIBUTOR, FIRST_TIME_CONTRIBUTOR, and FIRST_TIMER are excluded for security.

**Tool Permissions:**
- GitHub CLI access with tiered permissions:
  - **Allowed**: `gh pr create/view/list/diff/checks`, `gh issue view/list/comment`, `gh run view/list/watch`, `gh repo view`
  - **Requires confirmation**: `gh pr close`, `gh issue create/edit/close`, `gh label`, `gh run rerun`
  - **Blocked**: `gh pr merge`, `gh repo create/delete/fork`, `gh secret`, `gh api` write methods
- Git operations (status, branch, checkout, add, commit, push, pull, fetch, log, diff)
- File read/write within the repository, subject to the following boundaries:
  - Scope: Only files inside the checked-out CARLOS EMR repository workspace; no access to paths outside the repo.
  - Protected directories: Claude must not modify Git metadata or CI/CD definitions (e.g., `.git/`, `.github/`, `.github/workflows/`), database seeds/migrations (e.g., `database/`), secrets or credential stores, or other sensitive directories. These protections **must be enforced via explicit write-deny rules** in `.claude/settings.json` (for example: `Write(path:.git/**)`, `Write(path:.github/workflows/**)`, `Write(path:database/**)`).
  - File size: Intended for source files, configuration, and documentation. Very large files (such as database dumps, large binaries, or media assets) may be rejected by the tools and should not be created or edited by Claude.
  - File types: Read/write is primarily for text-based project assets (Java, XML, YAML, JSON, JSP, Markdown, shell scripts, etc.). Claude should not generate or alter compiled artifacts, installers, or opaque binary formats.
  - Deny rules: All file write operations remain subject to (a) the destructive-operation deny list and (b) explicit path-based write restrictions configured in `.claude/settings.json`. At minimum, `.claude/settings.json` **must** include write-deny entries for:
    - `Write(path:.git/**)`
    - `Write(path:.github/**)`
    - `Write(path:.github/workflows/**)`
    - `Write(path:database/**)`
    - and any additional secrets/credential directories defined by the deployment environment. If there is any conflict, the deny rules take precedence and the operation must not be performed.
- Web search and documentation lookup
- Playwright MCP tools for UI testing
- See `.claude/settings.json` for complete permission configuration

**Three-Tier Permission Model:**
The `.claude/settings.json` file defines three permission categories:
- **ALLOW**: Commands execute immediately without user intervention (core workflow operations)
- **ASK**: Commands require explicit user confirmation before execution (reversible but potentially disruptive operations)
- **DENY**: Commands are blocked entirely and cannot be executed (destructive or dangerous operations)

Commands in the ASK tier include:
- `gh pr close`, `gh issue create/edit/close`, `gh label` - visible repository actions
- `gh run rerun` - CI resource usage
- `git reset --soft/--mixed` - recoverable history changes
- `git stash drop` - potential data loss (single stash entry)

**Safety Guardrails:**
- **Repository scoped** - Operations run within the checked-out `carlos-emr/carlos` repository context
- Branch protection rules prevent direct pushes to `develop`, `main`, `experimental`
- All PRs require human review before merge
- Destructive operations are blocked:
  - File deletion: `rm -rf`, `rm -fr`, `rm -r`, `rm --recursive`
  - Force push: `git push --force/-f`, `git push origin --force/-f`, `git push * --force/-f`, `git push --force-with-lease`, `git push origin --force-with-lease`, `git push origin * --force-with-lease`
  - History rewriting: `git commit --amend`, `git filter-branch`, `git filter-repo`, `git reflog expire`, `git gc --prune`
  - Hook bypass: `git commit --no-verify`, `git push --no-verify`
  - Destructive git: `git rebase`, `git reset --hard`, `git clean` (note: `git reset --soft/--mixed` require confirmation, see ASK tier above)
  - System: `sudo`
- GitHub API write methods blocked (`-X DELETE/POST/PUT/PATCH`, `--method DELETE/POST/PUT/PATCH`)
- Repository management operations (`gh repo create/delete/fork`) are blocked
- Sensitive repository APIs blocked: `settings`, `collaborators`, `hooks`, `keys`, `invitations`, `branches/*/protection`
- Remote branch deletion (`git push origin --delete`) is blocked
- Remote manipulation (`git remote add/set-url`) is blocked
- Workflow modification (`gh workflow enable/disable`) is blocked
- Credential manipulation (`gh auth`) is blocked
- PHI protection enforced via OWASP encoding, parameterized queries, and `SecurityInfoManager` (see Critical Security Requirements)

**Enforcement Mechanism:**
The safety guardrails above are enforced through Claude Code's permission system configured in `.claude/settings.json`:
- **Deny rules take precedence** - Commands matching deny patterns are blocked before execution, regardless of allow rules
- **Pattern matching** - Uses glob-style wildcards (`*`) to match command variations (e.g., `git push --force *` blocks `git push --force origin main`)
- **Layered defense** - Multiple patterns cover flag ordering variations (e.g., `--force` before or after remote/branch)
- **Case sensitivity** - Separate patterns for case variants (e.g., `rm -rf` and `rm -Rf` both blocked)
- **No bypass via equals syntax** - Patterns like `--force-with-lease=*` block the `=refname` variant

Note: These are client-side controls. Repository-level branch protection rules provide server-side enforcement for protected branches.

### Interacting with Claude

**On Pull Requests:**
- `@claude review` - Comprehensive code review with security focus
- `@claude fix the lint errors` - Apply automated fixes
- `@claude explain this change` - Get explanation of PR changes

**On Issues:**
- `@claude investigate this bug` - Research and provide analysis
- `@claude implement this feature` - Create implementation PR
- `@claude add labels` - Categorize with appropriate labels

**Automated Triggers:**
- New PRs automatically receive code review
- Issues trigger Claude response when opened or assigned by authorized users (OWNER/MEMBER/COLLABORATOR), if they contain `@claude` in title or body

---

## Key Code References & Further Information

### Essential Configuration Files
```bash
# Spring Configuration Examples
src/main/resources/applicationContext.xml           # Core Spring setup patterns
src/main/resources/applicationContextREST.xml      # OAuth 1.0a implementation examples
src/main/webapp/WEB-INF/web.xml                   # Security filter chain configuration

# Struts Configuration (modular — 17 domain-specific files included from parent)
src/main/webapp/WEB-INF/classes/struts.xml        # Parent config: constants + <include> directives
src/main/webapp/WEB-INF/classes/struts-*.xml      # Domain-specific action mappings (admin, billing, etc.)
src/main/java/io/github/carlos_emr/carlos/*/web/*2Action.java # 2Action implementation patterns

# Database Configuration
src/main/resources/OscarDatabaseBase.xml           # Hibernate configuration
database/mysql/oscarinit_2025.sql                 # Current database schema
database/mysql/updates/update-2025-*.sql          # Recent migration patterns
```

### Security Implementation Examples
```bash
# Security Patterns (READ THESE FIRST)
src/main/java/io/github/carlos_emr/carlos/managers/SecurityInfoManager.java    # Authorization patterns
src/main/java/io/github/carlos_emr/carlos/utility/LoggedInInfo.java           # Session management
src/main/webapp/WEB-INF/classes/oscar/oscarSecurity/                # Security filter examples

# OWASP Integration Examples
src/main/webapp/*/*.jsp                            # Look for Encode.forHtml() usage patterns
src/main/java/io/github/carlos_emr/carlos/*/web/*2Action.java # Security check implementations

# CSRF Protection (CSRFGuard 4.5) — see docs/csrf-protection-architecture.md
src/main/webapp/WEB-INF/Owasp.CsrfGuard.properties # CSRFGuard configuration
src/main/java/io/github/carlos_emr/carlos/app/CarlosCsrfGuardFilter.java          # CSRF token validation filter
src/main/java/io/github/carlos_emr/carlos/app/CsrfGuardScriptInjectionFilter.java # Auto-injects csrfguard script tag
src/main/java/io/github/carlos_emr/carlos/app/MultiReadHttpServletRequest.java     # Multipart request dual-read wrapper

```

### Healthcare Domain Examples
```bash
# Medical Data Patterns
src/main/java/io/github/carlos_emr/carlos/commn/model/Demographic.java        # Patient data model
src/main/java/io/github/carlos_emr/carlos/commn/model/Allergies.java          # Medical allergy model
src/main/java/io/github/carlos_emr/carlos/commn/dao/DemographicDao.java       # Healthcare DAO patterns

# Provincial Healthcare Integration
src/main/java/io/github/carlos_emr/carlos/billing/CA/BC/                      # BC-specific billing
src/main/java/io/github/carlos_emr/carlos/billing/CA/ON/                      # Ontario-specific billing
docs/billing-ontario-module.md                                                 # Ontario billing architecture, fetch policy, recipe
src/main/java/io/github/carlos_emr/carlos/olis/                               # Ontario Labs integration

# HL7 & FHIR Examples
src/main/java/io/github/carlos_emr/carlos/hl7/                                # HL7 message handling
src/main/java/io/github/carlos_emr/carlos/fhir/                               # FHIR implementation patterns
```

### 2Action Migration Examples
```bash
# Study These 2Action Implementations
src/main/java/io/github/carlos_emr/carlos/tickler/pageUtil/AddTickler2Action.java      # Simple execute pattern
src/main/java/io/github/carlos_emr/carlos/admin/SystemMessage2Action.java              # Method-based routing
src/main/java/io/github/carlos_emr/carlos/encounter/pageUtil/EctDisplay*2Action.java
# Base Classes for 2Actions
src/main/java/io/github/carlos_emr/carlos/encounter/pageUtil/EctDisplayAction.java
```

### Spring Integration Patterns
```bash
# Spring Utility Usage Examples
src/main/java/io/github/carlos_emr/carlos/utility/SpringUtils.java           # Spring bean access patterns
src/main/java/io/github/carlos_emr/carlos/managers/*Manager.java             # Service layer examples
src/main/java/io/github/carlos_emr/carlos/commn/dao/*Dao.java               # DAO injection patterns
```

### Database Schema References
```bash
# Database Structure Examples
database/mysql/oscardata.sql                      # Reference data examples
database/mysql/caisi/initcaisi.sql               # Community integration schema
database/mysql/olis/olisinit.sql                 # Provincial lab integration schema
database/mysql/SnomedCore/snomedinit.sql         # Medical terminology integration
```

### Testing Patterns
```bash
# Test Framework (JUnit 5)
src/test/java/io/github/carlos_emr/carlos/            # All tests live here
src/test/java/io/github/carlos_emr/carlos/managers/   # Manager unit tests (DemographicManagerUnitTest)
src/test/java/io/github/carlos_emr/carlos/test/unit/  # Unit test base classes (CarlosUnitTestBase)
src/test/resources/                                   # Test configurations
src/test/resources/over_ride_config.properties        # Test configuration template
docs/test/modern-test-framework-complete.md           # Complete test framework documentation
docs/test/test-writing-guide.md                       # Test writing patterns and static mocking
```

#### Test Framework - Critical Guidelines
**IMPORTANT**: When writing tests, ALWAYS:
1. **Examine the actual code first** - Read the DAO/Manager interfaces to see what methods actually exist
2. **Test real methods only** - Never make up methods that don't exist in the codebase
3. **Use actual method signatures** - Match the exact parameters and return types
4. **Choose the right base class**:
   - Integration tests: Extend `CarlosTestBase` (Spring context + H2 database)
   - Unit tests: Extend `CarlosUnitTestBase` (mocked SpringUtils, no database)
   - Domain unit tests: Extend domain-specific bases like `DemographicUnitTestBase`
5. **Follow BDD naming strictly**: `should<Action>_<prepositionOrContext><Condition>` (camelCase, exactly ONE underscore, suffix starts lowercase; e.g. `_when`, `_by`, `_for`, `_with`). Do not use zero-underscore or multi-underscore variants.
6. **Check DAO interfaces** - Look at `*Dao.java` files to see available methods before writing tests
7. **For Manager unit tests with static classes** (LogAction, etc.):
   - Register SpringUtils mocks FIRST, THEN create static mocks
   - Close static mocks in @AfterEach to prevent test pollution
   - Use @Nested classes with JavaDoc to organize large test suites
8. **For Log4j2 assertions**: use `io.github.carlos_emr.carlos.test.logging.LogCapture`
   from `src/test/java/io/github/carlos_emr/carlos/test/logging/LogCapture.java`.
   Do not define local `AbstractAppender`, `CapturingAppender`, or per-test
   logger-config copies. `LogCapture` scopes capture to the exact logger under
   test, stores immutable events, and cleans up safely for parallel Surefire.

Example of proper test development workflow:
```java
// 1. First, check the actual DAO interface:
// src/main/java/io/github/carlos_emr/carlos/commn/dao/TicklerDao.java
public interface TicklerDao extends AbstractDao<Tickler> {
    public Tickler find(Integer id);  // <-- Real method to test
    public List<Tickler> findActiveByDemographicNo(Integer demoNo); // <-- Real method
    // ... other actual methods
}

// 2. Then write BDD-style tests for these ACTUAL methods:
@Test
@DisplayName("should return tickler when valid ID is provided")
void shouldReturnTickler_whenValidIdProvided() {
    // Given
    Tickler saved = createAndSaveTickler();

    // When
    Tickler found = ticklerDao.find(saved.getId()); // Testing real method

    // Then
    assertThat(found).isNotNull();
    assertThat(found).isEqualTo(saved);
}

// 3. Add negative test cases for edge cases and error conditions
```

For detailed examples and test development workflow, see **[Test Writing Guide](docs/test/test-writing-guide.md)**.

**Test Execution Commands:**
```bash
# Run all tests
mvn test

# Run all integration tests for a DAO component
mvn test -Dtest=TicklerDao*IntegrationTest  # All TicklerDao integration tests

# Run specific operation tests
mvn test -Dtest=TicklerDaoFindIntegrationTest      # Just find operations
mvn test -Dtest=TicklerDaoWriteIntegrationTest     # Just write operations

# Run Manager unit tests
mvn test -Dtest=DemographicManagerUnitTest         # All 117 Demographic manager tests
mvn test -Dtest=*ManagerUnitTest                   # All manager unit tests

# Run by test type (using tags)
mvn test -Dgroups="unit"                # Fast unit tests only
mvn test -Dgroups="integration"         # Integration tests only
mvn test -Dgroups="manager"             # All manager layer tests

# Run tests by tags
mvn test -Dgroups="tickler,read"        # All read operations for tickler
mvn test -Dgroups="demographic,security" # Demographic security tests
mvn test -Dgroups="create,update"       # All create and update operations

# Build with tests
make install --run-tests          # All tests
make install --run-unit-tests     # Only unit tests (fast, no database)
```

### Development Environment References
```bash
# DevContainer Configuration
.devcontainer/devcontainer.json                   # VS Code dev environment setup
.devcontainer/development/Dockerfile              # Container build configuration
.devcontainer/development/scripts/make            # Build and deploy automation
.devcontainer/development/scripts/server          # Tomcat management automation
.devcontainer/development/config/bashrc           # Terminal customization
```

### Documentation & Architecture
```bash
# Project Documentation
docs/Password_System.md                           # Security architecture details
docs/struts-actions-detailed.md                   # Action mapping documentation
docs/struts-web-endpoints.md                      # Current Struts route + WEB-INF JSP guidance
pom.xml                                            # Complete dependency list with versions
README.md                                          # Project setup and overview
```

### When You Need Help Understanding:
- **Security Patterns**: Check `SecurityInfoManager.java` and existing 2Action implementations
- **Database Access**: Look at DAO implementations in `commn.dao` package
- **Healthcare Standards**: Examine `hl7/` and `fhir/` packages for integration patterns
- **Provincial Variations**: Study `billing/CA/BC/` vs `billing/CA/ON/` implementations. The Ontario module has a dedicated architecture doc — see [`docs/billing-ontario-module.md`](docs/billing-ontario-module.md) for layer conventions, entity domain methods, fetch policy (LAZY + JOIN FETCH companions), service/loader/persister suffix grammar, and the new-feature recipe.
- **Spring Configuration**: Reference the multiple `applicationContext*.xml` files
- **2Action Migration**: Compare legacy Action classes with their 2Action equivalents
- **New JSP-backed pages**: Follow `docs/struts-web-endpoints.md`
