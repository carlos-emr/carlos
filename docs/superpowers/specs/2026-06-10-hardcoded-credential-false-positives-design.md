# Design: Suppress Hardcoded-Credential False Positives

**Date:** 2026-06-10
**Status:** Approved

## Problem

GitHub Code Scanning shows 14 open `HARD_CODE_PASSWORD` / `HardCodedCryptoKey` /
`secrets:S8215` / `java:S2068` alerts. Every one is a false positive — scanners
misidentifying empty-string field clears, WS-Security protocol-type constants, a
UI mask sentinel, a BCrypt timing-equalization decoy hash, a policy comparison
string, and a cipher algorithm name.

Leaving them open creates auditor confusion ("was this reviewed?") and buries
real future findings in noise.

## Goals

1. Close all 14 alerts in GitHub Code Scanning.
2. Self-document each suppression so future reviewers understand why it exists.
3. Add a structural section to `spotbugs-exclude.xml` so recurring structural
   patterns do not re-accumulate as the codebase grows.

## Non-Goals

- Fix any real credentials (none were found).
- Change any runtime behavior.
- Suppress any finding that is not a confirmed false positive.

## Alert Inventory and Disposition

### Structural patterns → `spotbugs-exclude.xml`

These patterns will always fire for a given method because of what the code *is*,
not because of a bug. They belong in the XML so the pattern is explained once and
applies to the whole structural class.

| Bug pattern | Class | Method | Why it fires |
|---|---|---|---|
| `HARD_CODE_PASSWORD` | `EmailManager` | `sanitizeEmailFields` | `setPassword("")` — clearing a field to empty string |
| `HARD_CODE_PASSWORD` | `AuthenticationInWSS4JInterceptor` | `initialize` | `"PasswordText"` is a WS-Security protocol-type constant |
| `HARD_CODE_PASSWORD` | `AuthenticationOutWSS4JInterceptor` | `<init>` | Same WS-Security constant |
| `HARD_CODE_PASSWORD` | `EdtClientBuilder` | `newWSSOutInterceptorConfiguration` | Same WS-Security constant |
| `HARD_CODE_PASSWORD` | `ConfigureFax2Action` | `isPasswordUnchanged` | `PASSWORD_MASK_SENTINEL = "**********"` — UI mask sentinel comparison |

All five entries go in a new `HARD_CODE_PASSWORD FALSE POSITIVES` section with an
explanatory header comment describing the three structural trigger patterns so the
next developer who encounters the same pattern has context before reaching for a
new suppression.

### Reviewed per-site findings → `@SuppressFBWarnings` + inline comment

These need call-site review context that is specific to the code's design intent.

| Alert | File | Lines | Mechanism |
|---|---|---|---|
| `HARD_CODE_PASSWORD` (SpotBugs) + `java:S2068` + `secrets:S8215` (SonarCloud) | `LoginCheckLoginBean.java` | 125–126, 349 | `@SuppressFBWarnings` on the field constant + `// NOSONAR` on lines 125-126; `@SuppressFBWarnings` on `missingUserDummySecurity()` |
| `HARD_CODE_PASSWORD` | `SecurityManager.java` | 94 | `@SuppressFBWarnings` on `checkPasswordAgainstPrevious` |

`LoginCheckLoginBean.java` justification: `MISSING_USER_DUMMY_PASSWORD_HASH` is a
pre-computed BCrypt hash of a random decoy password used solely to equalize
timing of the missing-user authentication path (prevents user-enumeration via
timing). It has no meaningful plaintext. This is an intentional security pattern.

`SecurityManager.java` justification: The string `"0"` on line 94 is a policy
threshold sentinel (zero past passwords to check), not a credential.

### PMD inline suppression

| Alert | File | Line | Mechanism |
|---|---|---|---|
| `HardCodedCryptoKey` | `LabUpload2Action.java` | 236 | `// NOPMD HardCodedCryptoKey` inline + adjacent comment |

Justification: `Cipher.getInstance("RSA/ECB/PKCS1Padding")` is an algorithm
name string, not a key. No cryptographic key material is hardcoded. The padding
choice is a pre-existing protocol constraint; see adjacent comment in the source.

## Suppression Conventions (matches existing codebase)

- **SpotBugs XML**: method-scoped `<Match>` elements; a prose comment above each
  entry or section explains the rationale.
- **`@SuppressFBWarnings`**: annotation on the method (or field for field-level
  findings); mandatory adjacent `//` comment per CLAUDE.md.
- **SonarCloud**: `// NOSONAR rule-id — short justification` at end of line.
- **PMD**: `// NOPMD RuleId — justification` inline.

## Files Changed

- `.github/spotbugs/spotbugs-exclude.xml` — new section with 5 entries
- `src/main/java/io/github/carlos_emr/carlos/login/LoginCheckLoginBean.java` — field + method annotations + NOSONAR
- `src/main/java/io/github/carlos_emr/carlos/managers/SecurityManager.java` — method annotation
- `src/main/java/io/github/carlos_emr/carlos/lab/ca/all/pageUtil/LabUpload2Action.java` — inline NOPMD

## Testing

No behavior change. Verification: run `make install --run-unit-tests` to confirm
no compilation or test regressions. The SpotBugs and Semgrep runs are CI-only;
locally confirm the changed files compile clean.
