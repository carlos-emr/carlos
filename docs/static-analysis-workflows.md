# Static Analysis Workflows — PMD, SpotBugs, Find Security Bugs

> **Added**: April 2026
> **Workflows**: `pmd.yml`, `spotbugs.yml`
> **Configuration**: `.github/pmd/carlos-ruleset.xml`, `.github/spotbugs/spotbugs-exclude.xml`

## Overview

CARLOS EMR uses three complementary static analysis tools in CI, each catching a different
class of defect:

| Tool | Analyzes | Finds | Workflow |
|------|----------|-------|----------|
| **PMD** | Source code (`.java`) | Code patterns, complexity, dead code, style | `pmd.yml` |
| **SpotBugs** | Compiled bytecode (`.class`) | Null deref, resource leaks, concurrency bugs | `spotbugs.yml` |
| **Find Security Bugs** | Compiled bytecode (`.class`) | SQL injection, XSS, XXE, crypto, deserialization | `spotbugs.yml` |

All findings are uploaded as SARIF to the **GitHub Security tab** under **Code scanning alerts**
and appear as inline PR annotations.

These tools complement the existing SonarCloud, Semgrep, and CodeQL workflows — each tool has
different detection strengths and together they provide defense-in-depth coverage appropriate
for a healthcare application handling PHI.

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
| Pull request | Targeting `develop`, `main`, or `experimental`; only when `.java` files change |
| Push | To `develop`; only when `.java` files change |
| Schedule | Weekly (Monday 5:00 UTC) |
| Manual | `workflow_dispatch` |

### How it runs

PMD does not need compiled code. The workflow uses the official `pmd/pmd-github-action@v2`
action, which downloads PMD and runs it directly against `src/main/java` — no dev container
or Maven build required.

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
| `SE_BAD_FIELD` / `SE_NO_SERIALVERSIONID` on `*Action` classes | Struts actions extend `ActionSupport` (Serializable) but are never serialized |
| `ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD` on `*2Action` | Intentional `SpringUtils.getBean()` pattern |
| `URF_UNREAD_FIELD` on model classes | Fields set by Hibernate reflection |
| HL7 / WS-Security vendor packages | Third-party code, not actionable |
| `RV_RETURN_VALUE_OF_PUTIFABSENT_IGNORED` | Common in cache patterns, not a bug |

**Maven profile**: `spotbugs` (defined in `pom.xml`)

- SpotBugs Maven Plugin: 4.9.3.0
- SpotBugs Engine: 4.9.3
- Find Security Bugs: 1.13.0
- Effort: `Max` (deepest analysis)
- Threshold: `Low` (report everything, filter via exclude file)

### Triggers

| Event | Condition |
|-------|-----------|
| Pull request | Targeting `develop`, `main`, or `experimental`; only when `.java` or `pom.xml` changes |
| Push | To `develop`; only when `.java` or `pom.xml` changes |
| Schedule | Weekly (Monday 5:30 UTC) |
| Manual | `workflow_dispatch` |

### How it runs

SpotBugs needs compiled `.class` files, so the workflow uses the dev container (same as
`maven-project.yml`):

1. Pull or build the `carlos-tomcat-dev` container
2. Restore Maven dependency cache
3. Run `mvn compile spotbugs:spotbugs -Pspotbugs,skip-dependency-lock`
4. Convert the SpotBugs XML report (`target/spotbugs-result.xml`) to SARIF format
5. Upload SARIF to GitHub Security tab

The XML-to-SARIF conversion uses an inline Python script (no third-party action dependency).

---

## Viewing Results

### GitHub Security Tab

All findings from both workflows appear under **Security > Code scanning alerts**. Each tool
uploads to a separate SARIF category (`pmd` and `spotbugs`) so alerts can be filtered by tool.

### Pull Request Annotations

PMD findings appear as inline annotations on the PR diff via `pmd-github-action`.
SpotBugs findings appear via the SARIF upload (GitHub renders them as code scanning alerts on
the PR).

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

# Run SpotBugs analysis
mvn spotbugs:spotbugs -Pspotbugs,skip-dependency-lock -DskipTests

# View results
# XML report: target/spotbugs-result.xml

# Optional: open HTML report in browser
mvn spotbugs:gui -Pspotbugs,skip-dependency-lock
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

To suppress a finding on a single method, add `@SuppressFBWarnings(value = "BUG_PATTERN",
justification = "reason")` to the method. Use this sparingly and always include a justification.

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
