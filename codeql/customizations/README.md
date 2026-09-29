# CodeQL sanitizer model for `PathValidationUtils`

A CodeQL customization that teaches GitHub code scanning that
`io.github.carlos_emr.carlos.utility.PathValidationUtils`'s containment-validating
helpers return safe, contained paths — clearing the `java/path-injection` false
positives the path-validation centralization produces (e.g. alert **#26023** on
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

## Files (this directory)

| File | Role |
|------|------|
| `Customizations.qll` | The sanitizer (`PathValidationUtilsSanitizer extends PathInjectionSanitizer`). The `Customizations.qll` filename is the convention the standard library picks up. |
| `qlpack.yml` | Declares the library pack `carlos-emr/carlos-codeql-customizations` (depends on `codeql/java-all`). |

## Activating it (this repo uses CodeQL **default setup**)

Default setup cannot run a local `.qll`, so publish this pack and reference it by name.

1. **Publish** (needs the CodeQL CLI and a token with `write:packages`):
   ```bash
   gh extension install github/gh-codeql      # one-time
   cd codeql/customizations
   gh codeql pack install
   gh codeql pack publish                     # -> ghcr.io/carlos-emr/carlos-codeql-customizations
   ```
2. **Make it pullable by the scanner**: Org → Packages → `carlos-codeql-customizations`
   → Package settings → set visibility **Internal** (or link it to the `carlos` repo).
3. **Reference it** in `.github/codeql/codeql-config.yml`:
   ```yaml
   packs:
     - carlos-emr/carlos-codeql-customizations
   ```
4. **Validate**: push to a branch → code scanning re-runs → confirm alert #26023 (and
   any sibling `PathValidationUtils` path-injection FPs) are gone.

If they do **not** clear, switch to advanced setup and add the pack with
`--additional-packs ./codeql/customizations` in the `github/codeql-action/init` step —
the `Customizations.qll` auto-import is CodeQL-version-sensitive, so validate on a branch.

## Notes
- `Customizations.qll` uses `MethodCall`; on CodeQL CLI &lt; 2.16 rename it to `MethodAccess`.
- Until the pack is live, dismiss the few current FPs in the Security tab as *false positive*.
