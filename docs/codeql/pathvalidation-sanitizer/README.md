# CodeQL sanitizer model for `PathValidationUtils`

A CodeQL customization that teaches GitHub code scanning that
`io.github.carlos_emr.carlos.utility.PathValidationUtils`'s containment-validating
helpers return safe, contained paths — clearing the `java/path-injection` false
positives this PR's centralization produces (e.g. alert **#26023** on
`DocumentUpload2Action:301`, and every sibling sink where a `validate*` result is
later used in a filesystem call).

## Why a QL barrier, not a Models-as-Data (MaD) model

`PathValidationUtils` is **in the analysed source**. CodeQL reads each helper's body,
propagates taint through `new File(allowedDir, name)`, and does **not** recognise the
custom `validateWithinDirectory(...)` canonical-containment check
(`fileCanonical.startsWith(baseCanonical + File.separator)`, `PathValidationUtils.java:875-878`)
as a barrier. A MaD `neutral`/`summary` data extension only overrides models for
**external** (library) methods — it does **not** suppress body-based flow for
in-source code. A QL **barrier** (`PathInjectionSanitizer`) does, which is why this
ships as `Customizations.qll` rather than a `.model.yml`.

Only genuine containment / component validators are modelled. `resolveTrustedPath`,
`resolveConfiguredDirectory`, and `resolveConfiguredFile` are **excluded on purpose** —
their Javadoc says they only canonicalise a trusted value and are *"not a security
boundary"*, so sanitizing them could mask real findings.

## Files

| File | Role |
|------|------|
| `Customizations.qll` | The sanitizer (`PathValidationUtilsSanitizer extends PathInjectionSanitizer`). The `Customizations.qll` filename is the convention the standard library picks up. |
| `qlpack.yml` | Declares the library pack `carlos-emr/carlos-codeql-customizations` (depends on `codeql/java-all`). |

> Move this pack to its intended home before publishing — e.g. `.github/codeql/customizations/`
> or a top-level `codeql/` directory. It is parked under `docs/` only because that was the
> writable location available when it was drafted.

## Integrating it (this repo uses CodeQL **default setup**)

`.github/codeql/codeql-config.yml` exists and default setup is in use (no
`codeql-action/init` workflow). **Default setup cannot run local `.qll` files** — it can
only load **published** CodeQL packs referenced under `packs:`. So pick one:

### Option A — publish the pack, reference it from the config (recommended for default setup)
1. From this pack directory, with the CodeQL CLI:
   ```bash
   codeql pack install
   codeql pack publish        # publishes carlos-emr/carlos-codeql-customizations to GHCR
   ```
2. Add it to `.github/codeql/codeql-config.yml`:
   ```yaml
   packs:
     - carlos-emr/carlos-codeql-customizations
   ```
3. Re-run code scanning and confirm `java/path-injection` alert #26023 (and siblings) clear.

### Option B — switch to advanced setup
Add a `.github/workflows/codeql.yml` using `github/codeql-action/init@v3` with
`config-file: ./.github/codeql/codeql-config.yml` and either `--additional-packs`
pointing at this pack directory or a `packs:` reference to the published pack, then
`github/codeql-action/analyze@v3`.

## Caveats / validation
- I cannot commit this into `.github/**` or publish packs (those are write-denied /
  outside my access) — hence the draft under `docs/` plus these steps for someone with
  repo/registry access.
- **Validate in CI**: a customization that doesn't compile is silently ignored by some
  runners, so confirm the target alert actually disappears after the pack is wired in.
- **CodeQL version**: `Customizations.qll` uses `MethodCall`; on CodeQL CLI &lt; 2.16
  rename it to `MethodAccess`.
- Until the pack is live, the handful of current FPs can be dismissed in the Security
  tab as *false positive* (the interim approach already in use on this PR).
