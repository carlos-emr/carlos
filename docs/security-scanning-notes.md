# Security scanning — suppression notes

This document explains how false positives from CodeQL, SonarCloud, and Snyk Code are handled in CARLOS EMR. It exists so a future reviewer can verify a suppression in one hop: read the note, follow the reference to the sanitizer, check the canary test is green.

## Overarching principle

**Every suppression is an in-repo artifact**, not a platform-metadata dismissal. Suppressions travel with the code, show up in PR review, and future scanner re-runs reproduce the same state without out-of-band actions. No per-alert UI "dismiss as false positive" is used in this codebase.

## Canary tests (regression tripwire)

The in-repo suppressions trust two sanitizers:

- `io.github.carlos_emr.carlos.utility.PathValidationUtils` — path-traversal defence (`validatePath`, `validateExistingPath`, `validateUpload`, `isInAllowedTempDirectory`).
- `io.github.carlos_emr.carlos.utility.LogSanitizer` — log-injection defence (`sanitize(String)`, `sanitize(String, int)`, `sanitizeObject(Object)`).

Both have unit-test coverage that exercises the traversal / injection paths directly:

- `src/test/java/io/github/carlos_emr/carlos/utility/PathValidationUtilsTest.java`
- `src/test/java/io/github/carlos_emr/carlos/utility/LogSanitizerUnitTest.java`

If either sanitizer is ever weakened, the unit tests must fail. Every downstream suppression is only as trustworthy as these tests staying green. Both files are flagged for security review; any PR that modifies them must be reviewed with the canaries in mind.

## Per-tool suppression mechanism

### CodeQL — model pack

Location: `.github/codeql/extensions/carlos-java-models/`

Models `PathValidationUtils` methods as `summaryModel` entries (safe argument → return value, user-controlled argument omitted), and `LogSanitizer` / guard methods as `neutralModel` entries. CodeQL's dataflow engine cuts taint at each sanitizer boundary.

Wired into `.github/codeql/codeql-config.yml` via the `packs:` key. If default-setup silently ignores local pack references, the repo must convert to advanced-setup with a dedicated workflow file — see "Open" below.

**Precision property**: the model entries name specific method signatures. A new file sink that does not pass through one of these methods still raises a fresh CodeQL alert. Adding new wrappers requires editing `models/path-validation-sanitizers.yml`.

### SonarCloud — `@SuppressWarnings("java:S5145")` on specific methods

Used for log-injection findings where the logged value goes through `LogSanitizer.sanitize(...)` but SonarCloud does not recognise the custom sanitizer.

- Applied **per method**, never at class level. A new log statement in a sibling method without `LogSanitizer` still alerts.
- Annotation carries a short comment naming the reason: `// all logged user input goes through LogSanitizer.sanitize()`.

Some existing code uses the inline `// NOSONAR javasecurity:S5145 — sanitized with LogSanitizer` form instead; both are valid. For new code, prefer the method-level annotation — it is explicit about which rule it suppresses (`// NOSONAR` alone suppresses all Sonar rules on that line).

Not every log-injection false positive has been annotated yet. The remaining unresolved S5145 alerts sit in the following files and are tracked as residual:

- `FrmPDFServlet.java`, `FrmRecordFactory.java`
- `ImageRenderingServlet.java`
- `EctDisplayAction.java`
- `EventService.java`
- `BillingCorrectionPrep.java`
- `EFormPDFServlet.java`
- JSPs (`select_facility.jsp`, `forwardname.jsp`, `forwardshortcutname.jsp`) — JSPs cannot carry `@SuppressWarnings`; these should be refactored so the logging happens in an annotated Java helper.

Follow-up PRs should add `@SuppressWarnings("java:S5145")` per method as they are touched.

### Snyk Code — `.snyk exclude.code` for dev-only paths

Location: `.snyk` at repo root.

Scoped to developer-only scripts that are not deployed in the WAR:

- `scripts/**`
- `.claude/hooks/**`
- `database/mysql/importCasemgmt.java`, `database/mysql/importCPP.java`
- `release/**`
- `.devcontainer/**`

**Scope discipline**: this file must not contain patterns matching `src/main/**`. Production-code Snyk Code findings are resolved by code refactor or left as a documented residual (see below) — not by `.snyk` exclude.

### Snyk Open Source — `.snyk ignore:` block

Not currently used. When needed, use the documented `ignore:` block keyed by `SNYK-XXX` vulnerability ID with a `reason:` and `expires:` date.

## Residual Snyk Code `java/PT` alerts on production code

A residual set of Snyk Code path-injection alerts on production code (`src/main/**`) is accepted as known noise. They cannot be cleared by:

- `.snyk exclude` (scoped to dev-only paths by policy above).
- Inline comments (`// deepcode ignore java/PT: …` is a deprecated legacy feature; Snyk strips it from results).
- Per-alert UI dismissal (not used — see overarching principle).

Options when one of these alerts is encountered:

1. **Refactor the caller** to use `PathValidationUtils` in a shape Snyk Code's dataflow engine recognises. Snyk Code respects `FilenameUtils.getName()` + fixed-directory `new File(baseDir, name)`; adding a local variable pass-through sometimes breaks its over-approximation.
2. **Leave it.** CodeQL + SonarCloud still cover the same defect class, so Snyk Code becomes a lower-signal third opinion on these files.
3. **Escalate to Snyk.** Enterprise customers can register custom sanitizers at org level through Snyk support.

No action is taken in this repo. If a given file's alerts become too noisy to review, open an issue titled `Refactor <file> for Snyk Code dataflow` and handle case-by-case.

## How to add a new suppression

1. Confirm the finding is genuinely safe. Trace the dataflow by hand; do not trust the scanner's first-guess sanitizer list.
2. Identify the sanitizer that makes it safe. If it's `PathValidationUtils` or `LogSanitizer`, confirm the canary tests still cover the property you rely on.
3. Pick the narrowest in-repo suppression mechanism:
   - CodeQL: add a model entry for the specific method signature in `path-validation-sanitizers.yml`.
   - SonarCloud: `@SuppressWarnings("java:S<number>")` on the single method (or `// NOSONAR java:S<number> — <reason>` on the specific line).
   - Snyk Code: refactor first; `.snyk exclude` only for non-deployed paths.
4. Do not disable a rule at the project/profile level. Do not use class-level `@SuppressWarnings`. Do not dismiss via the tool's UI.
5. Reference this file in the PR description so reviewers know where to look.
