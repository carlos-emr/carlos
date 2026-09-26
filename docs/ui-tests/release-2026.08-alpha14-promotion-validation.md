# Release 2026.08.0-alpha14 promotion validation

Review baseline: `release/2026.08` at `ba7b1822e1` (the head the promotion PR #3928 was cut
from) compared with tag `2026.08.0-alpha13`, on 2026-09-25, plus the review-fixes branch
`claude/release-2026.08-alpha14-review-fixes` (PR #3929) that carries the repairs below.
The promotion PR is refreshed from `release/2026.08` once #3929 lands.

## Confirmed findings and repairs

| Finding | Evidence | Repair and regression coverage |
| --- | --- | --- |
| Health Tracker rejects a malformed blood pressure with the literal key `error.bloodPressure` | `HealthTrackerMeasurementPersister` emitted a key that exists in no bundle; Struts `getText` returns the key itself | Use the existing `errors.bloodPressure` key (present in en/es/fr/pl/pt_BR) |
| Health Tracker prevention popover prints `Entered by: null` | `HealthTrackerPage.jspf` wrapped `String.valueOf(hdata.get("provider_name"))`, which `SafeEncode` does not coalesce, and `PreventionData` leaves the name null for provider `-1` | Coalesce through `StringUtils.noNull` as the measurement rows already do |
| Measurement delete endpoint accepted GET | `EctDeleteData2Action` (the Health Tracker's delete target, also posted to by the history pages) had no HTTP-method gate; CSRFGuard protects POST/PUT/DELETE/PATCH only, so a link or image in a logged-in session could tombstone and delete a measurement | Refuse non-POST with 405 before any lookup; unit test drives a GET and verifies no DAO interaction; registered in the mutator GET-rejection contract manifest |
| Two annotation-viewer messages were hard-coded English | `documentAnnotate.js` page-load failure and unconfirmed-save status bypassed `cfg.i18n` | New keys `faxAnnotateViewer.status.pageLoadFailed` and `faxAnnotateViewer.alert.saveUnconfirmed` in all five bundles, exposed through the JSP `i18n` block |
| Annotation viewer locked Save after a failure that never left the browser | The single `.catch` marked the save uncertain for every failure, including a font or CSRF-token bootstrap failure before any request, disabling both Save buttons until a reload that discards the marks | The body is built first and the save counts as sent from the moment `fetch()` starts, because `fetch()` can reject after the server accepted the POST. Only a failure before that keeps Save enabled. The `annotate-document` check covers both sides |
| eForm Image Library delete returned an unmapped `error` result | `DelImage2Action` returned `ERROR` for a blank name, a path-validation failure or an IO failure, but `struts-eform.xml` maps only `success`, so the operator got a Struts "No result defined" page | Report 400/500 on the response and return `NONE`, per the direct-response contract |
| Incoming-document page actions ran on GET and for read-only users | `incomingDocs.jsp` called `IncomingDocUtil.doPagesAction` (rotate, delete page, delete PDF, extract) for any request with `pdfAction`. The page is reached through the `_edoc` read gate and CSRFGuard does not check GET, so a link could change a queued fax, and a read-only user could do so with a CSRF-valid POST. Found by the existing `incoming-pdf-extraction` check, which expected 405 | GET with `pdfAction` answers 405 with `Allow: POST`. A page action also needs `_edoc` write, the right `addIncomingDocument` requires, and answers 403 otherwise. The check now also lowers the test login's `_edoc` grants to read for one CSRF-valid POST and requires 403 with the file unchanged |
| Incoming-document page operations could overwrite another queued document | Rotate, rotate-all, delete-page and extract wrote their working copy to a fixed `"T" + name` file in the queue directory, and extraction wrote `<name>E<pages>.pdf` with a plain `FileOutputStream`, so a queued document with either name was silently replaced. A failed extraction also left the source read-only. Present since before alpha13; found when the `incoming-pdf-extraction` check first got past its GET step | A unique `.tmp` scratch file per operation (hidden from the `*.pdf` listing; one older than an hour is an orphan of a process killed mid-rewrite and is swept when the queue is listed or the next operation runs), `CREATE_NEW` for the extract with a visible error on a name clash, clean-up that never removes a file the call did not create, the source's own permissions restored on failure and kept on the replacement (read-only is set only once setup has succeeded, and a read-only queue entry is neither restored nor replaced as a writable one), a deleted page filed in the recycle directory before the queue document is replaced, under a name no older entry holds, and removed again if the replacement fails, and one move instead of delete-then-rename for rotate. Seven new `IncomingDocUtilUnitTest` cases; the browser check's collision, cancel, bounds, extract, rotate and delete steps pass on the packaged install |
| Annotation viewer left late pages blank after a jump to the end | Unloaded pages were 200px placeholders, so each page that loaded pushed the ones after it out of the lazy loader's window with no scroll event to bring them back; the first paint also requested nine renders for two visible pages. Present since before alpha13; found by the `annotate-document` check's multipage step | Placeholders reserve a letter-shaped box at the current zoom's render width, and the loader re-runs after each image loads. The first paint now requests two renders and the last page of a ten-page fax loads after `End` |
| A repaired install kept a stale restart veto | A configure that holds the EMR back writes `/run/carlos-emr/.start-vetoed`, and neither `carlos-ctl finish-install` path cleared it after a successful repair, so a later DrugRef-only transaction could still decline to restart a stopped EMR | Clear the veto after the hand-run start succeeds and once the boot repair has queued the start; keep it when either fails |
| The legacy renderer could keep running through the renderer consolidation | The move off the pre-alpha14 names stopped `carlos-emr-chromedriver.service` only once its unit file was gone. A bare `dpkg -i --auto-deconfigure` leaves the unit file and driver in place, so the old home was deleted under a live driver that still held the port | Stop the old unit whenever it is active. If it is still active after the stop, keep its home, leave the new render browser down rather than contend for the port, and print how to finish with `dpkg-reconfigure`. The upgrade test confirms the old unit inactive, the new one active and the old home gone |
| Packaging metadata | `debian/control` accepted debhelper 13.6, but before 13.14.1 `dh_installsystemd` does not scan `usr/lib/systemd/system`, where the render browser unit is staged, so an older toolchain would build a package that never enables the renderer. The `postrm` purge note omitted the retained `carlos-render` account, and `install-deb.md` listed `openjdk-25-jre-headless` under `universe` instead of `main` | Raise the floor to `debhelper (>= 13.14.1~)` with the reason recorded beside it, name the account, and correct the component |
| Email senders threw an unchecked exception on a NULL configuration row | `readTree(null)` in the SMTP, local SMTP and SendGrid senders raised `IllegalArgumentException`; only `IOException` was caught, so a NULL `emailConfig.configDetails` (allowed by the schema and preserved by V1.0.23.1) escaped the `EmailSendingException` contract | Guard the JSON before parsing and raise the checked exception the callers handle |

Two repairs made during this review were withdrawn after the packaged install contradicted
them, and neither ships:

- Changing `getBooleanProperty("health_tracker", "true")` to `"false"` inverted the switch. The
  second argument is the value to match, not a default, so the shipped `health_tracker=false`
  showed the Health Tracker entry. `echart-navbar-modules` caught it. The original call already
  leaves the tracker off when the property is absent, and a comment now records that.
- Restoring `tabindex` 6 and 16-25 in `ChartNotes.jsp` undid a deliberate change. The header
  localisation commit moved the template search and Save to natural keyboard order, and
  `encounter-header-i18n` asserts it. The note textarea (7), the encounter time fields (11-14)
  and the issue autocomplete (100) still carry positive values. Converting them is follow-up
  work for the next train.

## Review scope

Four review passes covered the 46 commits since `2026.08.0-alpha13`: Debian packaging (renderer
consolidation, transitional package, restart trigger, postinst/postrm, units, carlos-ctl);
the Ocean toolbar, Health Tracker, consult-specialist, `rosterEnrolledTo` and email-config
changes with their migrations; the browser-facing JS/JSP changes (annotations, appointment
typeahead, chart header localisation, inbox acknowledge, HRM sign-off, eForm/RTL editor, ported
upstream fixes, document extraction); and the server-side hardening (MCEDT/WSS4J series,
PathNet upload, HRM statement retirement, Java 25, dependency updates, Semgrep rules).

No published Flyway migration differs from alpha13. `V1.0.23.1` is numbered below `V1.0.29`
and `V1.0.30` (against the migration README's own rule) but sits above alpha13's high-water
mark (`on/V1.0.23`, `common/V1.0.22`), so a tagged alpha13 -> alpha14 upgrade applies all
three in order; only a development database migrated in the two-hour window between #3782
and #3816 on `release/2026.08` can be out of order, and that state is repaired with
`flyway repair` and a re-migrate. Do not repeat the pattern.

Items reviewed and left unchanged, to be verified against a live system rather than in code:
the MCEDT `EncryptedKey` bound of 20 per response (MCEDT limits a download request to a
handful of resources; a multi-file download against the ministry gateway is the only proof);
CXF spill files after large downloads; runtime JasperReports compilation on JDK 25 (Rourke and
label printing); PDFBox 3.0.8 rejecting OpenType CFF2 fonts in incoming documents; the RTL
editor's save gate on a clinic-customised `form_html` that never calls `Start()`.

## New browser checks

- `ocean-display-settings`: Administration > eChart Display Settings round trip. Saves the
  Ocean switch off and on through the panel, asserts the `SystemPreferences` row and the
  `OceanSetting` singleton, proves the encounter drops and restores `#ocean_placeholder`, and
  that a GET carrying the save intent answers 405 without changing the preference. Restores
  the pre-existing rows in its cleanup.
- `hrm-retired-statement`: the retired HRM confidentiality-statement operations answer 410 on
  GET and on a POST carrying the session's CSRF token (so it reaches the action), the report listing served by the same action still answers, and the
  Administration panel no longer links the removed page.
- `clinic-demo-name`: Flyway `V1.0.29` is recorded as applied, no clinic row still carries the
  upstream placeholder name, and Administration > Clinic shows the stored name exactly.

## Package build and install

The three packages were built with `dpkg-buildpackage -us -uc -b` in an Ubuntu 26.04 (resolute)
container with OpenJDK 25, Maven 3.9 and debhelper 13.31. The version was stamped the way the
release workflow stamps it: `pom.xml` at `2026.08.0-alpha14` with SCM tag `2026.08.0-alpha14`, and
a single changelog stanza `2026.08.0~alpha14`. The build was repeated after each repair batch;
the last build is from the review-fixes branch head.

| Package | Architecture |
| --- | --- |
| `carlos-emr_2026.08.0~alpha14` | amd64 (EMR, bundled Chromium renderer, carlos-ctl) |
| `carlos-emr-drugref_2026.08.0~alpha14` | all |
| `carlos-emr-eform-renderer_2026.08.0~alpha14` | all (empty transitional package) |

Lintian reports no errors. It reports four warnings: three `debian-changelog-line-too-long` from
the local validation stamp (the release workflow writes a shorter line), and one
`possible-bashism-in-maintainer-script` that points at Python source inside a postinst heredoc,
which is a false positive.

Install on a fresh Ubuntu 26.04 systemd container, following
[`deb-install-validation.md`](deb-install-validation.md):

- `apt-get install` of the three local packages with the preseeded answers completed, and
  `carlos-ctl check` reported "All checks passed" (front door, HSTS, WAF blocking, DrugRef
  lookup, 26 Flyway migrations, renderer, TLS).
- The mandatory first-login password reset through the browser succeeded.
- A same-version reinstall of each rebuilt package set ran the `carlos-emr-restart` trigger once
  at the end of the transaction, redeployed the application a single time, left
  `NRestarts=0`, and passed `carlos-ctl check` again.
- The About page and `carlos-build.properties` report `2026.08.0-alpha14 (carlos-emr-deb
  2026.08.0~alpha14)`.

Environment notes: the container needs `fonts-liberation` for the annotation check's late-font
scenarios (the check now says so). The Docker host has no IPv6 stack; carlos-ctl emits the
`[::]` listeners only when the kernel has one, and the front door came up on IPv4 alone.

## Browser suite

`scripts/run-playwright-suite.js` ran all 150 manifest entries through the packaged front door
(`https://127.0.0.1/carlos`), twice. Neither run restarted the application.

| Run | Passed | Failed | Skipped |
| --- | --- | --- | --- |
| First build | 131 | 11 | 8 |
| After the repairs | 130 | 12 | 8 |

Every failure was re-run on its own and classified:

- **Product defects, fixed here:** `incoming-pdf-extraction` (GET mutations, read-only users,
  and the queue overwrite behind it), `echart-navbar-modules` (the inverted Health Tracker switch
  this review had introduced), and `encounter-header-i18n` (the withdrawn `tabindex` restore).
- **Check defects, fixed here:** `document-upload`, `rh-form-workflow` (navbar overlay, reload
  beacon, form-entry timing and the STRICT `sql_mode` probe), `eform-admin` (a JSP scriptlet in
  its fixture), `ocean-display-settings` (a stale page), and `annotate-document` (outside the
  manifest; it now names its font prerequisite).
- **Environment or fixture, not a defect:**
  - `demographic-edit-update` needs `DEMOGRAPHIC_EDIT_SEARCH` and
    `DEMOGRAPHIC_EDIT_DEMOGRAPHIC_NO`, and passes with them.
  - `rx-fax-reprint-represcribe` and `about-licence` failed because the run was given the build
    tag in the wrong format. Both pass with the About page's actual tag.
  - `hrm-window` needs `CHROME_BIN`.
  - `patient-messenger-context`, `echart-note-editor`, `csrf-xhr-token` and
    `consultation-signature-fallback` hit a note lock left by an earlier check whose browser
    closed without its unload beacon. The chart then showed the "edit this note in another
    window" prompt. All four pass or skip on their own.
  - `surface-audit:edoc-surface` passed on re-run.
  - `o19-migrated-smoke` needs an imported OSCAR 19 fixture.
- **Pre-existing, recorded, not changed:**
  - `episode-lifecycle`: the seeded `doctor` role holds `o` on `_newCasemgmt.episode`, the same
    as in alpha13, so the Episode module is hidden.
  - `inboxhub-filters`: earlier checks acknowledged the newest version of two lab chains
    (170, 172) while older versions (169, 44) stayed filed. The labs query collapses each
    chain to its newest version within the result set it is building. "All" therefore shows
    170 and 172, and "Filed" shows 169 and 44, so the filters no longer partition "All". The
    count query does not collapse versions, which is why the total reads 11 against three
    rows. The same code shipped in alpha13; it is worth an issue, but it is not a release
    regression.

The eight skips need fixtures the packaged demo data does not carry: long documents, the eForm
corpus, referral data, BC billing and inactive drugs.

Custom Playwright work beyond the manifest:

- **Probes:** navbar overlay geometry, note-lock release on navigate and close, the annotation
  viewer's lazy loading and placeholder layout, and the Health Tracker entry's source.
- **New checks:** `ocean-display-settings`, `hrm-retired-statement` and `clinic-demo-name`.
- **New steps:** `incoming-pdf-extraction` gained its read-only step, and `annotate-document`
  gained its pre-send and post-send save-failure steps.

## Upgrade path

The published `2026.08.0~alpha13` packages were checked against their release `.sha256`
files and installed on a fresh Ubuntu 26.04 systemd container. The first-login reset was
completed in the browser, and `scripts/deb-upgrade-baseline.sh` captured the state. The final
alpha14 packages were then installed over them with `apt-get install --no-remove`, and
`scripts/deb-upgrade-verify.sh` compared the result. The run was made twice: once with the
packages the full suite ran on, and once with the final packages built from the branch head.
Both passed.

- **Migrations:** Flyway went from 23 to 26 applied, 0 failed. Exactly `1.0.23.1`, `1.0.29`
  and `1.0.30` were added, and no earlier history row disappeared.
- **Preserved:** the operator's password hash and reset flag, `carlos-emr.env`,
  `carlos.properties`, `backup.env`, the TLS certificate, province, time zone and database
  name, the row counts of every clinical table sampled, and every stored document file.
- **Build tag:** moved from `2026.08.0-alpha13` to `2026.08.0-alpha14 (carlos-emr-deb
  2026.08.0~alpha14)`.
- **Renderer consolidation:** `render-browser.env` moved to `renderer.env`, and the old
  `/var/lib/carlos-emr/render` home was removed. `carlos-emr-chromedriver.service` is
  inactive and `carlos-emr-render-browser.service` is active.
- **Runtime and health:** the JVM is OpenJDK 25, `carlos-ctl check` passes, and
  `NRestarts=0`.
- **Browser checks after the upgrade:** `login`, `application-health`, `clinic-demo-name`,
  `ocean-display-settings`, `hrm-retired-statement`, `health-tracker`,
  `incoming-pdf-extraction` (all nine steps) and `echart-navbar-modules` all pass.

Two more runs, with the final packages, tried to force the case where the legacy driver refuses
to stop: a `RefuseManualStop=yes` drop-in on `carlos-emr-chromedriver.service` before upgrading,
once through `apt-get install` of all three packages and once through `dpkg -i
--auto-deconfigure` of `carlos-emr` alone. In both, the alpha13 renderer package's own
maintainer script stopped the driver while it was being upgraded or deconfigured, before the new
postinst ran. Both ended with the old unit inactive, the old home removed, the new render
browser active and `carlos-ctl check` passing. The `dpkg-reconfigure` recovery also ran cleanly.
The kept-home branch therefore guards a case these paths do not reach;
`scripts/debian-build.test.js` pins it. The `--auto-deconfigure` run leaves the old renderer and
DrugRef packages to be upgraded separately, as `install-deb.md` describes.

The final packages were also reinstalled over the test host. `incoming-pdf-extraction`,
`application-health`, `ocean-display-settings`, `echart-navbar-modules`,
`encounter-header-i18n`, `health-tracker`, `hrm-retired-statement` and `document-upload` pass,
and no scratch file is left in the incoming-document tree.

The upgrade log carries one expected line, "Failed to stop carlos-emr-render-browser.service:
Unit … not loaded." It comes from debhelper's stop-on-upgrade snippet for a unit that alpha13
did not have under its new name; the snippet ends in `|| true`. The Ubuntu `nginx` package also
fails its own first start on a host without IPv6, because its default site listens on `[::]`.
The alpha13 and alpha14 installs both continue past it, and the CARLOS front door comes up.
