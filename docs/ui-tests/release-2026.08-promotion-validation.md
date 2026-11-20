# Release 2026.08 promotion validation

Review baseline: `c181142689` on `release/2026.08`, compared with `origin/main`
(alpha12), 2026-09-18. This report accompanies a separate repair PR targeting
`release/2026.08`; the promotion PR is #3762.

## Confirmed findings

| Finding | Evidence | Repair and regression coverage |
| --- | --- | --- |
| General Conversions fails to initialize and its About link does nothing | Installed-package Playwright reports `Generic is not defined`, two syntax errors, and `popupStart is not defined` | Repair script initialization and popup handler; test the actual Chart → Calculators → General Conversions → About/Licence journey |
| General Conversions has inaccurate unit factors and loses zero/small values | Nautical-mile and long-ton factors are incorrect; U.S. liquid-volume factors also disagree with unit definitions. The formatter truncates scientific notation and the calculation treats zero as missing | Use exact unit definitions, significant-digit formatting, visible invalid-input feedback, and numerical browser assertions. Legacy `.htm` bookmarks forward to the maintained calculator |
| Legacy pharmacy search loses names containing `!` | Authenticated installed-package search for an owned `FAKE-PW…!` pharmacy returns no match | Restore the existing wildcard-search contract; retain the separate fax picker's literal escaping. New DAO integration and authenticated Playwright endpoint regressions |
| Report validation depends on administration navigation order | Full admin audit opens 102 items; Visit Report and Overnight Batch produce six JavaScript error signals. eForm fragments reload jQuery and discard the shell's validation plugins | Restore the shell's jQuery instance before the next fragment; new browser workflow visits eForms before both reports and checks invalid submission is blocked |
| Migration verification tools can echo a password prefix | An attached argument such as `-pSECRET=tail` is split at `=` before being printed in the refusal | Return only constant option names from both argument checks; regression cases cover attached passwords containing `=` and long/short options |
| Calculator validation clears its own error and the entered value | The browser's `reportValidity()` focuses the invalid field; the legacy focus handler immediately clears the form | Select the source unit on input edits, preserve focused results, and test correction after a visible validation error |
| Legacy referral with a NULL booking flag returns HTTP 500 | Dedicated installed-package browser regression reproduces Hibernate hydration failure; the published SQL column permits NULL | Nullable internal model field with the existing boolean API; DAO verifies reading does not rewrite NULL, and Playwright verifies the saved referral opens |
| Bouncy Castle dependency has open security advisories | Release pins 1.84; Dependabot alerts #195/#196 include a critical name-constraints bypass | Upgrade to 1.86 and refresh its dependency lock; validate the full build and installed cryptographic workflows |
| Correcting a temperature through the opposite field leaves a stale error | Entering an invalid Fahrenheit value, then 20 Celsius, produces 68 Fahrenheit but retains `Enter a finite number` on that now-valid result | Clear the destination's old validity before replacing its value; unit and browser regressions cover correction through the opposite field |

Conversion definitions: [NIST SP 811 Appendix B.8](https://www.nist.gov/pml/special-publication-811/nist-guide-si-appendix-b-conversion-factors/nist-guide-si-appendix-b8).

Bouncy Castle release: [upstream 1.86 announcement](https://www.bouncycastle.org/resources/new-release-bouncy-castle-java-1-86/).

## Review scope

The review follows the changed application paths, their callers, tests and package
configuration. It covers login/session handling; recurrence transactions and
legacy-series matching; contact, episode and scratchpad ownership; document
annotation, upload, forwarding and fax boundaries; response error handling;
APCache lookup compatibility; clinical calculator input; printing; and installation
recovery. Generated OSCAR 19 manifests are checked by the manifest integrity and
SQL-generation tests, with inspection of import staging, resume ledgers, backup
ordering, restricted database accounts, archive containment, value parity and
role reconciliation. This does not certify a real clinic migration or an external
fax/lab integration.

No published Flyway migration differs from alpha12, and no normalized version
collision exists in either common + ON or common + BC. The release does not add
a schema migration. Configuration changes retain existing clinic values during
the package upgrade; no replacement of the clinic configuration was required.
The nullable booking repair also regenerates ON/BC import manifests from pinned
OSCAR source `a7900d569d3faf741993e5e1da8c14021bbefede`. Both map families advance
to `o19map-3`, so the existing resume guards refuse an unfinished import staged
under the older conversion rules. No Flyway SQL is edited. A partially imported
clinic must follow the existing rollback/restart instructions; this change does
not waive that check.

## Running the added browser checks

Use the isolated demo deployment and environment setup in
[the DEB validation runbook](deb-install-validation.md). The shared harness needs
`BASE_URL`, `TEST_USER`, `TEST_PASSWORD`, `TEST_PIN`, and database connection
settings; use `CHROME_PATH` for the packaged Chromium binary. Keep credentials
out of command arguments and logs. Then run from the checkout:

```sh
node scripts/run-playwright-suite.js \
  --only registry-ichppc-lifecycle --only about-licence \
  --only pharmacy-search --only admin-report-validation \
  --only consultation-null-booking \
  --junit release-regressions.xml
```

The registry, calculator and nullable-referral checks own synthetic patient
records; pharmacy search owns two pharmacy rows. Cleanup runs after failures too.
The administration check opens reports and attempts only an invalid submission.
Pharmacy search checks an authenticated endpoint after UI login. The nullable
referral check opens an existing saved-referral URL; it requires reference rows
in `consultationServices` and `professionalSpecialists`, supplied by the demo
dataset. The other three checks follow UI controls.
The suite manifest contains 125 checks after these additions. Listing a check
does not mean its deployment-specific prerequisites have been satisfied.

## First corrected-package verification

- Full corrected Java `clean package`: 12,413 tests, zero failures/errors, 51 skips.
  The dependency integrity check also passes with all three Bouncy Castle
  artifacts locked to 1.86.
- Python: 1,587 passed. Node: 700 passed, including 19 calculator cases and
  three prescription-fixture parsing cases.
- Matched main, DrugRef and renderer `validation5` packages built from application
  revision `d7b8d2ee48` and installed. All three report `ii`. Clinical counts,
  administrator credential records and
  the initial credential file are unchanged. Installed deployment checks and
  Flyway validation pass.
- ICHPPC lifecycle, pharmacy search, administration report navigation, calculator
  numerical/validation controls, About/Licence popups, and nullable referral
  rendering pass against the corrected installed application. Viewing the
  nullable referral preserves its clinical reason and leaves the database NULL
  unchanged. Legacy bookmark fragments also pass against the final package.
  The optional booking checkbox is tested when the deployment enables
  it; the DAO regression also verifies the public boolean getter and later save.
- Post-dependency-upgrade browser reruns pass prescription signing, all three Rx
  fax checks, both consultation-signature checks, document annotation, APCache
  eForm rendering and lab PDF footers. A missing-stamp negative control confirms
  the fax test records the specific missing-signature/disabled-button failure
  and cleans its prescription, drug, signature and fax-configuration fixtures.
- Fresh ON and BC scratch schemas, using the package's `utf8mb4_general_ci`
  collation, passed installed migration and demo loading. A second demo load left
  a digest of all database row data unchanged. Flyway validation passed; both
  owned schemas and their temporary grants were removed afterwards.

Validation setup findings: upgrading only the main `validation2` DEB removed
the two companions because they require the exact main-package version. This
was corrected by installing all three matching `validation3` DEBs with
`apt-get --no-remove`; the install guide now documents both requirements.
The resulting missing-browser failures are not application regressions. The
first scratch-schema attempt used `utf8mb4_unicode_ci` and hit a collation
conflict; that was a test setup mismatch with the package's provisioner, not a
failure of the supported fresh install. The calculator menu intentionally closes
its opener; the browser helper now supports that behavior while still awaiting
and checking the new page.

## Extended VM verification

The first complete smoke/core/front-door pass ran 116 checks: 96 passed, 18
failed, and 2 were skipped (referrals and workflow modules disabled). The raw
failure count includes absent test prerequisites and shared demo state; it is
not a count of confirmed application defects.

The [per-check result inventory](release-2026.08-browser-results.csv) accounts
for all 125 manifest entries: 121 passed across the broad run and targeted
follow-ups, two disabled-module checks were skipped, and two extended checks
were not run. Passing checks can still contain the excluded subcases listed
below. The separate annotation script is additional to this manifest.

All 18 initial failures subsequently passed targeted reruns. This is evidence
across the broad run and its reruns, not a claim that the initial run was green:

| Initial failing check(s) | Resolution and passing rerun |
| --- | --- |
| Consultation signature and signature submit | Supply an owned complete referral and synthetic provider stamp; both passed again after the crypto upgrade |
| Demographic edit, chart navigation and note editor | Own isolated synthetic patients; avoid pre-existing draft/lock state |
| Note sign/bill, patient Messenger, consultation signature fallback and CSRF XHR | Separate owned patient for each scenario; all persisted/response assertions passed |
| Patient-list export | Supply the documented appointment fixture profile; remove owned appointments afterwards |
| Prescription signature | Supply a verified unsigned demo prescription; cleanup enabled; passed again after the crypto upgrade |
| Three Rx fax checks | Supply the configured document directory, enabled fax feature and owned signature stamp; all passed again after the crypto upgrade |
| eDoc surface audit | Targeted repeat passed; initial aborted navigation was not reproduced |
| Episode lifecycle | Temporarily enable the documented test permission denied by the demo doctor role |
| Contact lifecycle | Correct the test selector to exclude the hidden input; full create/edit/archive lifecycle passed |
| Login | Supply the private current password hash required by the reset fixture |

Targeted runs with separately owned patients passed demographic editing (five
fields plus audit records), eChart navigation, note save/sign/billing, patient
Messenger sorting, manual consultation-signature fallback, draft autosave, and
CSRF header enforcement. Reusing a chart across browser sessions left note locks
and draft recovery dialogs; dismissing those dialogs produced the apparent blank
pages. Existing demo drafts were not deleted. Each targeted scenario received a
fresh patient and removed only its own support rows.

The contact lifecycle check selected both a visible search input and a hidden
input with the same name. Its selectors now identify the text inputs. The demo
doctor role explicitly denies episode access, and faxing is disabled in the VM
configuration; those checks require their documented permissions/configuration.
Consultation, prescription, login-reset and patient-list checks also need their
explicit fixtures. These requirements are not bypassed or counted as passes.

The separate document annotation script passed server-rendered pages, actual
annotation saves, Unicode text and unsupported-glyph rejection, stale-source
refusal, text-layer retry, in-flight save protection, lost-response warnings,
failed-page-image refusal, CSP enforcement, and protected fax-preview cancellation.
An owned two-page PDF remained byte-identical. Six filed copies were verified;
all owned document files, rows and the patient were removed, retaining audit logs.

The OSCAR 19 SQL semantics oracle passed against the VM's MariaDB 11.8. It tested
seed precedence, twin preservation, ID/FK remapping, charset repair, row-value
parity, archived columns, collation compatibility, packet-size-independent
digests, and dump/restore corruption controls. Its uniquely named scratch schemas
were removed and the global packet setting restored. This is synthetic SQL
validation, not validation of a real clinic's complete source dataset.

The separate DDL parser oracle checked all 1,337 CREATE and 2,225 ALTER statements
from the pinned OSCAR source. Its parse agreed with MariaDB for all 1,969
comparable statements. The server refused the other 1,593 probes; these are
uncompared, not passes. No probe was unbuildable, and no scratch schema remained.

## Baseline verification

- Full Java `clean package`: 12,411 tests, zero failures/errors, 51 skips.
- Python: 1,585 tests passed. Node: 678 tests passed.
- Built main, DrugRef, and eForm-renderer DEBs, version
  `2026.08.0~alpha13~validation1`. DrugRef uses the pinned source revision;
  Chromium archive checksums were verified before packaging.
- Upgraded the existing alpha12 Ubuntu 26.04 VM using all three DEBs.
  Preserved clinical record counts, administrator credential records, and the
  initial credential file. Flyway validates; no incomplete-install marker.
- Installed `carlos-ctl check` passed all deployment checks, including HTTPS,
  WAF blocking, DrugRef lookup, renderer service, database, and service ownership.
- New ICHPPC browser lifecycle passed search, add, reopen, resolve, cancel delete,
  and confirmed archival deletion. The test owns and removes its synthetic patient.
- Full browser baseline began through nginx HTTPS. The admin failures above and
  pharmacy-search failure are confirmed against the built package. A first About
  test had an incorrect menu selector; that test error was corrected before the
  application errors were recorded.

## Resource controls and validation limits

Java builds run only while the VM is stopped, with a 6 GiB build cgroup, no
cgroup swap, one test fork, and a host guard reserving at least 10 GiB available
memory. Baseline Java build minimum host availability was 16.02 GiB; the final
full Java build's minimum was 11.26 GiB. Swap remained unused.

The initial 6 GiB VM browser run reached its guest reserve; the guard paused the
suite and terminated the browser. This is an incomplete suite run, not a pass.
The VM was stopped and configured for 8 GiB for the next pass. Host and guest
memory monitors remain active. No compilation overlaps the running VM.

The referrals and workflow modules are disabled in the standard deployment, so their
surface checks remain skipped. Individual checks also report unavailable
subcases: an unbilled Bill link, the Sexual Health label, selective chart/phone
search fixtures, the library's Signature trick eForm, a clinic RTL template,
an opt-in eForm fax-preview case, and Inbox partitioning on an empty dataset.
These subcases are not covered by a check's overall passing result. The separate
annotation document fax-preview prepare/read/cancel path was exercised.

The three standalone eForm browser checks also pass. The DrugRef update page
passes live status and intercepted rollback/transport-failure reporting; no
DrugRef database rebuild was triggered. The next-appointment check also passed
with `workflow_enhance` temporarily enabled, verifying the real schedule widget
before, during and after its owned appointment fixture. Original properties and
episode permissions were restored; the two owned synthetic provider stamps
were removed, and installed deployment checks passed again.

External fax delivery, external lab sender interoperability, a real clinic's
OSCAR import and a third-party eForm corpus are not certified. Fresh ON/BC schema
and repeat-demo-load checks used owned scratch databases; the actual VM package
installation was an upgrade from alpha12, not a second fresh operating-system
installation.

## Promotion checks requiring separate assessment

The installed pinned DrugRef build still throws `UnsupportedOperationException`
when serializing `java.sql.Date`; CARLOS's inactive-date lookup then returns an
empty object. The ordinary drug-search browser check passes despite this failure.
This confirms the already tracked
[DrugRef issue #13](https://github.com/carlos-emr/drugref2026/issues/13).
[CARLOS PR #3690](https://github.com/carlos-emr/carlos/pull/3690) and
[DrugRef PR #14](https://github.com/carlos-emr/drugref2026/pull/14) contain the
existing repairs but are not merged into the validated release. Their fixes and
integration validation remain a promotion dependency; this PR does not duplicate
those open changes or claim the current inactive-date path is correct.

Promotion #3762 reports legacy lab AES encryption findings. Switching the
algorithm unilaterally would break the external sender's protocol; this repair
must not silently change that contract. The source also documents the external
RSA padding constraint. These findings are not evidence that the release
introduced the encryption code.

The CodeQL `util.py` alert traces to manifest-selected billing table identifiers,
not billing row payloads. Inspection of the path-containment and hash-comparison
alerts did not establish an unsafe release change. Their promotion-gate
classification remains separate from this repair. No global scanner suppression
has been added; the new calculator unit test has one rule-specific annotation
for executing a checked-in fixture in a mock VM, with no external input or HTML
output.

Promotion DCO flags five historical commits without trailers and its reporting
step also receives HTTP 403. New repair commits carry DCO sign-off. This report
makes no assertion that the promotion's existing DCO/security gates are green.

## Initial repair review

CodeRabbit completed a full follow-up review of `d7b8d2ee48` and reported no
actionable comments. Earlier valid comments were fixed and their regression
checks passed; all review threads were answered and resolved. The original report
and result inventory commit (`79a94fe05e`) added only documentation after that
reviewed and VM-tested application revision. New commits carry DCO sign-off.

Evidence logs, package SHA-256 records, memory samples and protected VM backups
are retained by the validation operator. Raw deployment logs and database
backups are not committed to the public repository.

## Second review

The second review reproduced the temperature-recovery issue above with the
actual JSP's JavaScript. The added regression failed against the previous code
(19 passed, one failed), then the full Node suite passed all 701 cases after the
fix. The live calculator check now also corrects an invalid Celsius entry by
entering Fahrenheit and requires both the expected value and cleared validity.
That assertion also failed for the specific stale-validity reason against the
previous installed `validation5` package. All three `validation6` DEBs were then
built from `86a1ba377b`, installed with data/credential preservation checks,
and passed deployment health and Flyway validation. All five added live browser
checks passed, including the new recovery assertion. Builds ran only with the
VM stopped; minimum host memory availability was 14.47 GiB for the WAR build and
12.15 GiB for package assembly. The VM was stopped after validation.

CodeRabbit's full review of `86a1ba377b` requested documentation of the nullable
booking getter's contract. Its JavaDoc now explicitly states that persisted NULL
returns false; the implementation is unchanged. Final CI and review outcomes
are recorded on [repair PR #3764](https://github.com/carlos-emr/carlos/pull/3764).
