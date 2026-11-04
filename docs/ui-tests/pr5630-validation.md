# Rich Text Letter template autofill validation (#5625)

PR #5630 targets `release/2026.08`. All application data used here is synthetic.

The parser now expands client mappings into server-backed keys, deduplicates those
keys, and runs composite store handlers in dependency order after the response.
Self mappings such as `sex` retain their response-value handler. Fully cached
templates avoid a request; a lookup that flushes the cache includes every needed
placeholder. Genuine unknown keys still reach the lookup endpoint and raise the
existing warning.

`ageGender` is defined in the database-stored Rich Text Letter form HTML (see
`database/mysql/updates/update-2026-03-22-rtl-2026.3.0-modernize.sql`), so it is a
client mapping even though its definition is outside `editControl2.js`.

## Build and install

CARLOS and DrugRef were compiled with Maven and OpenJDK 25 inside an Ubuntu
26.04.1 LTS container. `dpkg-buildpackage -us -uc -b` (with `-nc` for the cached
retry) produced the complete `2026.09.0~snapshot27` CARLOS and DrugRef packages.
DrugRef was built from the verified local checkout of pin
`0d240f07ee5c32464060dd7eecd98f317b6b3a08`. The Chromium revision and both SHA-256
checks in `debian/chromium.pin` were unchanged; the download used
`https://storage.googleapis.com/chromium-browser-snapshots/Linux_x64`.

The pinned carlos-ctl 1.3.0 source was built as a Debian package in the same
Ubuntu container. Its tag retains a development changelog, so the validation
checkout was stamped 1.3.0 to satisfy the application's declared dependency.
Its package build ran 1,944 tests, including 186 existing skips.

The three packages were installed in a separate Ubuntu 26.04 systemd container
with Ontario demonstration data, using the repository's Debian validation
workflow with the prepared image and read-only session CA mounts. The build
image's Ubuntu Tomcat service package was removed before installation because
`carlos-emr` supplies its own Tomcat service and conflicts with that package.
`carlos-ctl check` passed, the forced first-login reset completed through nginx,
and the fixture setup completed.

## Automated checks

- Parser/cache regression tests: 10 passed. Running these same tests against the
  original parser fails three assertions: nested composites, deduplicated keys,
  and cached placeholders surviving a flushing lookup.
- Focused Java regressions: 117 passed, with no failures, errors, or skips. This
  includes the AP lookup, renderer composition, template, deployment, print and
  editor-asset checks. The required `release/editControl2.js` mirror was
  synchronized after the asset equality assertion identified it.
- Isolated native Chromium editor regression: passed. Its year-boundary fixture
  now stays before midnight under both Vancouver's former winter offset and its
  permanent daylight-time rule.
- Complete locked Node regression suite: 7,067 passed, zero failures or skips,
  and one existing TODO (7,068 total).
- Packaged Playwright checks through nginx `:443`: `eform-rtl-print-pdf` 44/44,
  `eform-rtl-signature-stamp` 9/9, and `eform-apcache-renderer` passed. This
  includes all three shipped templates and `MissedAppointment.rtl`. Every
  composed field was filled, no false banner appeared, and the observed lookup
  requests contained no composite names. The renderer's deliberate missing-key
  refusal still worked. The suite reported no database residue.

## Negative control and server log

Substituting the original editor into the installed authoritative bundled
resource at
`/usr/share/carlos-emr/webapp/carlos/WEB-INF/eform-assets/editControl2.js`
makes the same browser check fail: 36/44 assertions pass. All four templates
raise the false banner and send composite names to the server. The three shipped
templates produce the reported nine unconfigured-key errors; the custom fixture
adds one `letterhead` error. The log audit leaves these real failures UNKNOWN.
The fixed resource is restored automatically after the negative control.

The log baseline distinguishes only the exact synthetic
`renderer_missing_ap_<13-digit timestamp>` probe, with a numeric form id, from other errors
at the same JSP log site. The timestamp is removed from its signature. Unit
checks verify that real composite names, arbitrary unknown keys, suffix lookalikes
and other errors at that site remain unexplained. The recorded packaged probe
signature is in `scripts/fixtures/server-log-signatures-2026.08.txt`.

The browser harness waits for template image requests to finish before closing
an editor, avoiding a client-disconnect ERROR during signature-image streaming.

After restoring the fixed bundled bytes, all three packaged browser checks passed
again (44/44, 9/9 and the renderer bridge), with zero failed or skipped checks
and no database residue. The final server-log audit passed with exactly two
known renderer-probe events and zero unexplained signatures. `carlos-ctl check`
passed again. The installed bundled asset, deployed image copy, canonical source
and release mirror share SHA-256
`afe92aa4b13de1589dbecee7350aafbae84bebe93eebd05b92b95f4fbfecb0f2`.
