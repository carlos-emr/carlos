# Static Analysis Workflows — Semgrep, PMD, SpotBugs, Find Security Bugs

> **Added**: April 2026
> **Workflows**: `semgrep.yml`, `pmd.yml`, `spotbugs.yml`
> **Configuration**: `.semgrep/`, `.github/pmd/carlos-ruleset.xml`, `.github/spotbugs/spotbugs-exclude.xml`

## Overview

CARLOS EMR uses complementary static analysis tools in CI, each catching a different
class of defect:

| Tool | Analyzes | Finds | Workflow |
|------|----------|-------|----------|
| **Semgrep** | Source code and generic text patterns | Injection, XSS, path traversal, policy rules | `semgrep.yml` |
| **PMD** | Source code (`.java`) | Code patterns, complexity, dead code, style | `pmd.yml` |
| **SpotBugs** | Compiled bytecode (`.class`) | Null deref, resource leaks, concurrency bugs | `spotbugs.yml` |
| **Find Security Bugs** | Compiled bytecode (`.class`) | SQLi, XSS, XXE, crypto, deserialization | `spotbugs.yml` |

All findings are uploaded as SARIF to the **GitHub Security tab** under **Code scanning alerts**
and appear as inline PR annotations.

These tools complement the existing SonarCloud and CodeQL workflows — each tool has different
detection strengths and together they provide defense-in-depth coverage appropriate for a
healthcare application handling PHI.

---

## Semgrep

### What it detects

Semgrep runs two scans in `.github/workflows/semgrep.yml`:

- `semgrep ci --sarif --output semgrep.sarif` runs the Semgrep Cloud policy, including Semgrep Pro rules when `SEMGREP_APP_TOKEN` is configured.
- `semgrep scan --config .semgrep/ --sarif --output semgrep-carlos.sarif` runs every CARLOS sanitizer-aware rule under `.semgrep/` — JSP scriptlet XSS, CRLF log injection, and path traversal — recognizing project encoders and `PathValidationUtils`/`LogSafe` sanitizers. See `.semgrep/README.md` for the full rule list and the built-in Semgrep Cloud rules each one replaces.

### False-positive handling

Use the narrowest control that preserves useful coverage:

1. Fix real flows first, especially request data reaching HTML, filesystem, SQL, log, or response sinks.
2. Add sanitizer-aware CARLOS rules under `.semgrep/` when a project utility is consistently safe but a built-in rule cannot model it.
3. Disable exact built-in rules in Semgrep Cloud only when a CARLOS rule fully replaces their coverage. `.semgrep/README.md` lists the rules intended for policy disablement.
4. For isolated already-safe findings from still-useful built-in rules, use rule-specific `nosemgrep: <rule-id>` comments at the finding site.

Semgrep honors `nosemgrep` by treating those findings as ignored, but still includes them in SARIF with `result.suppressions`. GitHub Code Scanning creates PR alerts from uploaded SARIF results, so the workflow runs `scripts/filter_suppressed_sarif.py` separately on both `semgrep.sarif` and `semgrep-carlos.sarif` before their uploads. This removes only explicitly suppressed results; unsuppressed Cloud and CARLOS findings still appear in Code Scanning. Both filters also run when an earlier scan fails but produces a report.

Do not use broad `.semgrepignore` entries, blanket rule disables, or bare `nosemgrep` comments to clear PR noise unless a narrower option is impossible and the rationale is documented.

## Reviewed 2026.08 exceptions

The October 2026 review covered 111 alert IDs: 105 false positives and six
intentional regression-fixture exceptions. Source changes are matched to
`release/2026.08`; the original alerts were reported on `main`. These are counts
of reviewed alerts, including duplicate reports at the same source location,
not a promise about the result count of a future scan.

The 92 Semgrep reports use exact-rule comments at the reported expression, or
remove misleading SQL examples from comments. Multiline test SQL templates
place their suppression comments **inside JavaScript interpolations**, so the
comments do not become SQL or alter the fixture data. The existing SARIF filter
removes only results marked suppressed by Semgrep. No file or rule is globally
excluded.

The 14 SpotBugs reports are addressed by method-scoped, justified annotations
for domain comparisons and the renderer header rejection gate, by naming the
blank PNG byte array as image data, and by comparing the same SHA-256 lab-content
digests with `MessageDigest.isEqual`. The note-save annotation belongs on
`noteSave`, not its appointment-binding helper. These changes preserve file
validation, authorization, logging, fixture assertions and exception handling.

The six deliberate fixtures are the ECB receiver tests (27421, 27422), MD5
protocol tests (27427, 27428, 27440), and the 0755-to-0700 directory regression
(27443). Their exceptions do not apply to production cryptography or permissions.

## CodeQL exceptions

This repository uses GitHub's CodeQL default setup. Source comments such as
`# codeql[...]` or `// codeql[...]` do **not** suppress its findings. Keep the
analysis enabled and use GitHub's
[per-alert dismissal with a recorded reason](https://docs.github.com/en/code-security/code-scanning/managing-code-scanning-alerts/resolving-code-scanning-alerts)
for the following reviewed exceptions. The source comments document the
reasoning; the dismissal is GitHub state, not a directive activated by merging
this file. Do not exclude the entire test module or turn off these queries.

| Alert | Disposition | Evidence and scope |
| --- | --- | --- |
| [27453](https://github.com/carlos-emr/carlos/security/code-scanning/27453) | False positive | `UploadTemplates2Action` gets the file from `UploadedFilesAware`; its setter is unannotated and Struts 7.1.1 requires parameter annotations. The read uses the canonical `validateUpload` result. This does not justify treating the one-argument helper as a general sanitizer for client-selected paths. |
| [27430](https://github.com/carlos-emr/carlos/security/code-scanning/27430) | False positive | `TempEnv.write_conf` writes placeholder credentials and generated test keys in a temporary fixture tree, not live secrets. |
| [27431](https://github.com/carlos-emr/carlos/security/code-scanning/27431) | False positive | The sessionless OSCAR regression removes those placeholder credentials from its temporary configuration. |
| [27421](https://github.com/carlos-emr/carlos/security/code-scanning/27421) | Used in tests | The envelope regression intentionally reproduces the legacy receiver's ECB decryption using generated keys and synthetic messages. |
| [27422](https://github.com/carlos-emr/carlos/security/code-scanning/27422) | Used in tests | The fake receiver intentionally implements the same legacy ECB protocol; production crypto findings remain in scope. |

Re-review an exception when its input source or security boundary changes;
reopen the corresponding CodeQL alert if its justification no longer holds.
Source suppressions take effect only on branches that contain them and have
been rescanned; release fixes must follow the normal forward-merge/promotion
process to affect `main`.

---

## PMD

### What it detects

PMD operates on Java source code and uses pattern-matching rules to find:

- **Error-prone patterns**: null checks after dereference, empty catch/if/try blocks, switch
  fallthrough, broken equals/hashCode
- **Security issues**: full `category/java/security.xml` ruleset
- **Resource leaks**: unclosed streams, connections, and other `Closeable` resources
- **Concurrency bugs**: double-checked locking, non-thread-safe singletons
- **Dead code**: unused fields, methods, and local variables
- **Complexity**: cognitive complexity above 30 (relaxed threshold for legacy code)

### What it intentionally skips

Style and naming rules are excluded to avoid noise on legacy code. Covered by Checkstyle instead.

### Configuration

**Ruleset**: `.github/pmd/carlos-ruleset.xml`

The ruleset cherry-picks individual rules rather than including full categories, giving precise
control over what gets flagged. To add or remove rules, edit the ruleset file — each rule
includes a comment explaining its category.

**PMD version**: 7.13.0

### Triggers

| Event | Condition |
|-------|-----------|
| Pull request | Targeting `develop`, `main`, `experimental`, or `release/*`; only when `.java` files change |
| Push | To `develop` or `release/*`; only when `.java` files change |
| Schedule | Weekly (Monday 5:00 UTC) |
| Manual | `workflow_dispatch` |

### How it runs

PMD does not need compiled code. The workflow downloads the pinned PMD CLI release (cached
between runs) and runs it directly against `src/main/java` and `src/test/java` — no third-party
action, dev container, or Maven build required.

---

## SpotBugs + Find Security Bugs

### What it detects

SpotBugs operates on compiled Java bytecode, which lets it perform data-flow analysis that
source-only tools cannot:

**SpotBugs core detectors:**
- Null pointer dereferences (interprocedural analysis)
- Resource leaks (streams, connections not closed on all paths)
- Concurrency bugs (inconsistent synchronization, shared mutable state)
- Infinite recursive loops
- Integer overflow and lossy casts
- Incorrect `equals()`/`compareTo()` implementations

**Find Security Bugs (150+ detectors):**
- SQL injection (JDBC, Hibernate HQL, JPA, Spring JDBC)
- XSS (reflected, stored, DOM-based)
- XXE (XML External Entity injection)
- Path traversal and file disclosure
- Insecure cryptography (weak ciphers, hardcoded keys, predictable RNG)
- Deserialization of untrusted data
- LDAP injection
- HTTP response splitting
- Unvalidated redirects
- Server-side request forgery (SSRF)
- Trust boundary violations

### Configuration

**Exclude filter**: `.github/spotbugs/spotbugs-exclude.xml`

Suppresses known false positives:

| Exclusion | Reason |
|-----------|--------|
| Test classes | Not production code |
| `SE_BAD_FIELD` / `SE_NO_SERIALVERSIONID` on `*Action` classes | Struts actions are not serialized |
| `ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD` on `*2Action` | Intentional `SpringUtils.getBean()` pattern |
| `URF_UNREAD_FIELD` on model classes | Fields set by Hibernate reflection |
| HL7 / WS-Security vendor packages | Third-party code, not actionable |
| `RV_RETURN_VALUE_OF_PUTIFABSENT_IGNORED` | Common in cache patterns, not a bug |

> **`IMPROPER_UNICODE` is suppressed differently — per-site, not via this filter.** It is an
> *informational* case-folding detector (`equalsIgnoreCase` / `toLowerCase` / `toUpperCase` /
> `Normalizer.normalize` / `toASCIIString` / `IDN.toASCII`) that fires on the **call itself,
> regardless of `Locale`** — so it cannot be cleared by editing code (adding `Locale.ROOT` does
> not help). Across this legacy EMR the overwhelming majority are intended case-insensitive
> domain comparisons (status/flag/enum/MIME codes). They are dispositioned with per-site
> `@SuppressFBWarnings` annotations carrying a justification, **plus an adjacent `//` comment** —
> see [SpotBugs exclusions](#spotbugs) below.

The incoming-document capacity page uses a method-local `XSS_SERVLET`
suppression because its two dynamic values already use OWASP encoding for
HTML text and quoted attributes. Its regression parses hostile context-path
markup and verifies that it remains one inert script URL, without injected
elements or event handlers. Preview reloads require exact `GET`; case-folded
or mutating method names receive no preview reload script. The separate incoming
page-edit waiting response is emitted only before mutation admission and preserves
the original POST fields, CSRF token and source revision. An uncertain result or
a revision conflict never uses that automatic retry path.

**Maven profile**: `spotbugs` (defined in `pom.xml`)

- SpotBugs Maven Plugin: 4.10.2.0 — held here deliberately; 4.10.4.x requires Maven >= 3.8.9,
  which the `carlos-tomcat-dev` image cannot supply from apt on noble. See the rationale comment
  on the plugin declaration in `pom.xml`.
- SpotBugs Engine: 4.10.2
- Find Security Bugs: 1.14.0
- `spotbugs-annotations` 4.10.2 (`provided` scope, in `pom.xml`) — supplies
  `edu.umd.cs.findbugs.annotations.SuppressFBWarnings` for per-site suppression
- Effort: `Max` (deepest analysis)
- Threshold: `Low` (report everything, filter via exclude file)
- Analyzer heap: `2048` MB via `spotbugs.maxHeapMb` in the Maven profile
  (override per-run with `-Dspotbugs.maxHeapMb=<MB>`)

### Triggers

| Event | Condition |
|-------|-----------|
| Pull request | Targeting `develop`, `main`, `experimental`, or `release/*`; only when `.java` or `pom.xml` changes |
| Push | To `develop` or `release/*`; only when `.java` or `pom.xml` changes |
| Schedule | Weekly (Monday 5:30 UTC) |
| Manual | `workflow_dispatch` |

### How it runs

SpotBugs needs compiled `.class` files, so the workflow uses the dev container (same as
`maven-project.yml`):

1. Start the `carlos-tomcat-dev` container through the `.github/actions/dev-container`
   composite action (pulled from ghcr.io, or built locally when it is not in the registry),
   which also restores the shared Maven dependency cache
2. Run `mvn -DskipTests -Pspotbugs,skip-dependency-lock compile spotbugs:spotbugs`
3. Convert the SpotBugs XML report (`target/spotbugs-result.xml`) to SARIF with
   `scripts/ci/spotbugs-xml-to-sarif.py`
4. Upload SARIF to GitHub Security tab

The XML-to-SARIF conversion is a plain Python script (no third-party action dependency); run it
locally with `REPORT=target/spotbugs-result.xml python3 scripts/ci/spotbugs-xml-to-sarif.py`
(it needs `defusedxml`: `python3 -m pip install -r scripts/ci/requirements.txt`).

---

## Viewing Results

### GitHub Security Tab

All findings from both workflows appear under **Security > Code scanning alerts**. Each tool
uploads to a separate SARIF category (`pmd` and `spotbugs`) so alerts can be filtered by tool.

### Pull Request Annotations

PMD and SpotBugs findings appear via their SARIF uploads (GitHub renders them as code scanning
alerts on the PR).

### Job Summary

Both workflows write a summary table to the GitHub Actions job summary page showing finding
counts by severity.

---

## Running Locally

### PMD

PMD can be run locally by downloading the standalone distribution:

```bash
# Download PMD 7.13.0
wget https://github.com/pmd/pmd/releases/download/pmd_releases%2F7.13.0/pmd-dist-7.13.0-bin.zip
unzip pmd-dist-7.13.0-bin.zip

# Run against source
pmd-bin-7.13.0/bin/pmd check \
  -d src/main/java \
  -R .github/pmd/carlos-ruleset.xml \
  -f text
```

### SpotBugs + Find Security Bugs

In the devcontainer, compile first then run SpotBugs:

```bash
# Build classes (no tests)
make install

# Run SpotBugs analysis (matches CI: skip-dependency-lock is already validated
# in the build job, so it is not re-checked here)
mvn -q -DskipTests -Pspotbugs,skip-dependency-lock spotbugs:spotbugs

# View results
# XML report: target/spotbugs-result.xml

# Optional: override the analyzer heap (in MB) if a local branch needs more
mvn -q -DskipTests -Pspotbugs,skip-dependency-lock -Dspotbugs.maxHeapMb=3072 spotbugs:spotbugs

# Optional: open HTML report in browser
mvn -Pspotbugs,skip-dependency-lock spotbugs:gui
```

---

## Adding New Exclusions

### PMD

Edit `.github/pmd/carlos-ruleset.xml`. To exclude a rule entirely, remove its `<rule ref="..."/>`
line. To adjust a threshold, add a `<properties>` block (see `CognitiveComplexity` for an
example).

### SpotBugs

Edit `.github/spotbugs/spotbugs-exclude.xml`. Use `<Match>` elements with `<Bug>`, `<Class>`,
`<Package>`, or `<Source>` matchers. See the
[SpotBugs filter documentation](https://spotbugs.readthedocs.io/en/stable/filter.html).

#### Path traversal findings

Treat `PATH_TRAVERSAL_IN` and related Find Security Bugs alerts as valid until the exact value used
at the filesystem sink is proven safe. Prefer fixing the flow with `PathValidationUtils` before
adding suppression: use `validatePathComponent()` for request-controlled path segments that must be
preserved exactly, use the returned values, and validate the assembled `File` with
`validateExistingPath()` before read/write/delete operations. Do not suppress an alert just because
`validatePath()` was called on the same raw request value if the original value is later used to
construct the real path.

#### Per-site suppression with `@SuppressFBWarnings`

To suppress a finding on a single declaration, add
`@SuppressFBWarnings(value = "BUG_PATTERN", justification = "reason")` to the method/constructor/
field/type (`edu.umd.cs.findbugs.annotations.SuppressFBWarnings`, from the `spotbugs-annotations`
`provided` dependency). Use it sparingly and **always** include a justification.

**Convention (required):** every `@SuppressFBWarnings` site must also carry an adjacent `//`
comment stating the same reason in human-readable form — the annotation attribute and the comment
must agree. This keeps the rationale visible at the call site for reviewers and future
maintainers, not only in the annotation metadata. Example:

```java
// FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value
// (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
@SuppressFBWarnings(value = "IMPROPER_UNICODE",
    justification = "case-insensitive comparison of an internal/domain value " +
            "(status/flag/enum/MIME/code); not a security or authorization decision")
public String resolveStatus(String code) { ... }
```

##### `IMPROPER_UNICODE` (case folding)

`IMPROPER_UNICODE` fires on `equalsIgnoreCase` / `toLowerCase` / `toUpperCase` (when the method
also does a string comparison or `switch`) / `Normalizer.normalize` / `toASCIIString` /
`IDN.toASCII`, **regardless of any `Locale` argument** — it is an audit marker, not a defect, and
cannot be cleared by editing. The codebase suppresses it per-site with two justification flavors:

- **Benign** (the overwhelming majority): *"case-insensitive comparison of an internal/domain
  value (status/flag/enum/MIME/code); not a security or authorization decision."*
- **Trust-path** (case folding that guards a security decision — OAuth scheme/host/Content-Type,
  file-extension allowlists, host/scheme checks): *"case-fold in a trust path; locale-safe
  hardening tracked in #2496."* These few sites are tracked for real `Locale.ROOT` /
  `Normalizer`-based hardening in **issue #2496** (CVE-2024-38827 class).

These annotations were applied in bulk by `scripts/lint/annotate-improper-unicode.py`, which
re-derives the flagged sites from source (tree-sitter) and is idempotent — re-run it after large
merges to annotate any new drift.

##### `UNVALIDATED_REDIRECT` (open redirect)

Find Security Bugs flags any `response.sendRedirect(...)` whose argument is data-flow reachable
from request input. The overwhelming majority of CARLOS sites are false positives because the
redirect target is a **fixed same-origin path** — `request.getContextPath() + "<literal route>"` —
with request-derived data appended only as URL-encoded **query parameters**, which cannot change
the host or scheme. The standard justification is:

> *"redirect target is a same-origin application path or validated internal path, not an
> attacker-controlled external URL."*

**Guard — do not paste this string onto a redirect whose *destination* is request-influenced.**
It is only accurate when the scheme+host+path are contextPath-anchored or a server-side allowlist
value. If a request parameter can become the redirect *target* itself (a `nextPage` / `returnURL` /
`chain` style value), the finding is **real** — gate it through
`io.github.carlos_emr.carlos.utility.RedirectValidationUtils.isValidRelativeRedirect(...)` (which
rejects absolute, protocol-relative, backslash/`%5c`, encoded control chars, and `..` traversal)
**before** suppressing, and write a site-specific justification naming the validator.

##### `BEAN_PROPERTY_INJECTION` (mass assignment)

Fires on `BeanUtils.copyProperties(...)`. The CARLOS sites are false positives because they use the
Spring **bean-to-bean** form `copyProperties(source, target)` — and its overload
`copyProperties(source, target, ignoreProperties)`, where the third argument is a *hardcoded* list
of property names to **skip**, not a source of request input. In both forms the property names come
from the compiled JavaBean descriptors of the fixed source/target model/DTO types, so no
request-controlled property name reaches the sink (see e.g. the legitimate 3-arg suppressions in
`OscarJobService` and `FacilityTransfer`). The standard justification states exactly that.

The distinguishing factor is whether the **property name** is request-derived, not the argument
count. **Do not** reuse this rationale for Apache Commons `BeanUtils.populate(bean, map)` /
`BeanUtilsBean.setProperty`, or any sink where the property name itself comes from request data —
those are genuine mass-assignment sinks.
For generated-JSP sinks the suppression lives in `.github/spotbugs/spotbugs-exclude.xml` keyed to
the generated `_jspService` class name — note that such entries silently stop matching if the JSP is
renamed or migrated to a `2Action`, so prefer an in-source `@SuppressFBWarnings` once the sink moves
to Java.

---

## Relationship to Other Analysis Tools

| Tool | Type | Scope | Primary Strength |
|------|------|-------|------------------|
| **PMD** | Source SAST | Java source | Pattern matching, complexity, dead code |
| **SpotBugs** | Bytecode SAST | Compiled classes | Data-flow analysis, null safety, resources |
| **Find Security Bugs** | Bytecode SAST | Compiled classes | Security-specific detectors (150+) |
| **SonarCloud** | Multi-language SAST | Java + JS + JSP | Holistic quality gate, technical debt tracking |
| **Semgrep** | Source SAST | Multi-language | Custom rules, taint analysis |
| **CodeQL** | Semantic SAST | Java + JS | Deep semantic queries, variant analysis |
| **Checkstyle** | Linter | Java source | Code formatting and naming conventions |
