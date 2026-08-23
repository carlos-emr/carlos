# CARLOS EMR — Custom Semgrep Rules

This directory contains custom [Semgrep](https://semgrep.dev/) rules that
recognize project-specific sanitizers. Most are taint-mode replacements for
built-in rules; the JSP scriptlet rule is a supplemental generic-mode rule
because Semgrep OSS does not support taint-mode with generic JSP matching.

## Why Custom Rules?

Semgrep's built-in taint rules flag data flowing from HTTP request parameters to
logging calls (CRLF injection) or file operations (path traversal) without
recognizing that the data has been sanitized by project utilities like
`LogSanitizer.sanitize()`. Custom taint-mode rules declare these utilities as
`pattern-sanitizers`, allowing Semgrep to correctly model the data flow.

## Rules

| File | Replaces | Sanitizer Recognized | False Positives Resolved |
|------|----------|---------------------|------------------------:|
| `crlf-injection-logs-carlos.yml` | 3 built-in CRLF log injection rules | `LogSanitizer.sanitize()`, `Encode.forJava(...)` | ~128 |
| `jsp-scriptlet-xss-carlos.yml` | Supplements the built-in JSP scriptlet XSS rule | `<carlos:encode>`, `${carlos:forXxx(...)}`, `SafeEncode.forXxx(...)`, `Encode.forXxx(...)`, `URLEncoder.encode(...)` | direct request-output FPs |

## Built-in Rules to Disable in Semgrep Cloud

When a custom rule replaces built-in rules, the built-in rules **must** be
disabled in the Semgrep Cloud policy dashboard for your organization
(`https://semgrep.dev/orgs/<org>/policies`)
to avoid duplicate alerts:

### CRLF Injection in Logs (`crlf-injection-logs-carlos.yml`)

- `java.servlets.security.crlf-injection-logs-deepsemgrep.crlf-injection-logs-deepsemgrep`
- `java.servlets.security.crlf-injection-logs.crlf-injection-logs`
- `java.lang.security.audit.crlf-injection-logs.crlf-injection-logs`

### JSP Scriptlet XSS (`jsp-scriptlet-xss-carlos.yml`)

Do **not** disable `java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss` while this
rule is generic-mode. The CARLOS rule recognizes common encoded direct-output
patterns and includes supplemental request-variable matching, but the built-in
rule remains responsible for broader JSP data-flow coverage.

## Running Locally

```bash
# Validate rule syntax
semgrep --config .semgrep/ --validate

# Scan the codebase with custom rules only
semgrep --config .semgrep/ src/main/java/

# Scan a specific file
semgrep --config .semgrep/crlf-injection-logs-carlos.yml src/main/java/path/to/File.java

# Scan JSPs with the CARLOS scriptlet XSS replacement rule
semgrep --config .semgrep/jsp-scriptlet-xss-carlos.yml src/main/webapp/
```

## CI Integration

The `semgrep ci` command (in `.github/workflows/semgrep.yml`) runs rules from the
Semgrep Cloud policy. `semgrep ci` does not support `--config`, so local CARLOS
rules that should run in GitHub Actions must be invoked with a separate
`semgrep scan --config ...` step and uploaded as their own SARIF file.

For `jsp-scriptlet-xss-carlos.yml`, the workflow keeps the built-in Semgrep
Cloud rule enabled and uploads the local supplemental CARLOS scan as
`semgrep-carlos.sarif`.

`nosemgrep` suppressions are honored by Semgrep CI, but Semgrep still writes
suppressed results into SARIF with `result.suppressions`. GitHub Code Scanning
creates PR annotations from uploaded SARIF results and does not treat those
Semgrep suppressions as dismissals, so the workflow runs
`scripts/filter_suppressed_sarif.py semgrep.sarif` before uploading the Semgrep
Cloud SARIF. This keeps Semgrep Cloud as the triage source of truth while keeping
GitHub Code Scanning focused on actionable unsuppressed findings.

## Handling False Positives

1. Prefer fixing the code or adding the missing sanitizer model to a CARLOS rule.
2. If a built-in Semgrep rule is fully replaced by a CARLOS sanitizer-aware rule,
   disable only that exact built-in rule in the Semgrep Cloud policy and document
   it above.
3. If the built-in rule still provides broader coverage, keep it enabled and use
   narrow rule-specific `nosemgrep` comments only at already-safe findings.
4. Do not use blanket file ignores, broad rule disables, or bare `nosemgrep`
   comments unless a rule-specific suppression is impossible and the rationale is
   documented in the surrounding code or PR.

## Maintenance

- **Adding a new sanitizer**: For taint-mode rules, add a `- pattern: NewSanitizer.method(...)`
  entry under `pattern-sanitizers`. For generic-mode rules, add a matching
  `pattern-not-regex` exclusion and keep the built-in rule enabled unless local
  tests prove equivalent flow coverage.
- **Adding a new rule**: Create a new `.yml` file in this directory following the
  existing naming convention (`<vuln-type>-carlos.yml`). Document the replaced
  built-in rules in this README and disable them in Semgrep Cloud.
- **Validating changes**: Always run `semgrep --config .semgrep/ --validate`
  after editing rule files.
