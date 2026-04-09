# CARLOS EMR — Custom Semgrep Rules

This directory contains custom [Semgrep](https://semgrep.dev/) taint-mode rules
that recognize project-specific sanitizers. Without these rules, Semgrep's
built-in rules produce false positives because they cannot trace data flow
through custom sanitizer methods.

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

## Built-in Rules to Disable in Semgrep Cloud

When a custom rule replaces built-in rules, the built-in rules **must** be
disabled in the Semgrep Cloud policy dashboard for your organization
(`https://semgrep.dev/orgs/<org>/policies`)
to avoid duplicate alerts:

### CRLF Injection in Logs (`crlf-injection-logs-carlos.yml`)

- `java.servlets.security.crlf-injection-logs-deepsemgrep.crlf-injection-logs-deepsemgrep`
- `java.servlets.security.crlf-injection-logs.crlf-injection-logs`
- `java.lang.security.audit.crlf-injection-logs.crlf-injection-logs`

## Running Locally

```bash
# Validate rule syntax
semgrep --config .semgrep/ --validate

# Scan the codebase with custom rules only
semgrep --config .semgrep/ src/main/java/

# Scan a specific file
semgrep --config .semgrep/crlf-injection-logs-carlos.yml src/main/java/path/to/File.java
```

## CI Integration

The `semgrep ci` command (in `.github/workflows/semgrep.yml`) runs rules from the
Semgrep Cloud policy. To include these local rules in CI scans, either:

1. **Add to Semgrep Cloud**: Upload rules via the Semgrep Cloud dashboard
   (Policies → Add Rule → Custom Rule), or
2. **Append local config**: Add `--config .semgrep/` to the `semgrep ci` command
   in the workflow file.

## Maintenance

- **Adding a new sanitizer**: Add a `- pattern: NewSanitizer.method(...)` entry
  under `pattern-sanitizers` in the relevant rule file.
- **Adding a new rule**: Create a new `.yml` file in this directory following the
  existing naming convention (`<vuln-type>-carlos.yml`). Document the replaced
  built-in rules in this README and disable them in Semgrep Cloud.
- **Validating changes**: Always run `semgrep --config .semgrep/ --validate`
  after editing rule files.
