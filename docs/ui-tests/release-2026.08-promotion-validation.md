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

Conversion definitions: [NIST SP 811 Appendix B.8](https://www.nist.gov/pml/special-publication-811/nist-guide-si-appendix-b-conversion-factors/nist-guide-si-appendix-b8).

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
  --junit release-regressions.xml
```

The registry and calculator checks own synthetic patient records; pharmacy search
owns two pharmacy rows. Cleanup runs after failures too. The administration check
opens reports and attempts only an invalid submission. Pharmacy search is an
authenticated endpoint check after UI login; the other three follow UI controls.
The suite manifest contains 124 checks after these additions. Listing a check
does not mean its deployment-specific prerequisites have been satisfied.

## Corrected-package verification in progress

- Full corrected Java `clean package`: 12,412 tests, zero failures/errors, 51 skips.
- Python: 1,587 passed. Node: 694 passed, including all 16 calculator cases.
- Matched main, DrugRef and renderer `validation3` packages built and installed;
  all three report `ii`. Clinical counts, administrator credential records and
  the initial credential file are unchanged. Installed deployment checks and
  Flyway validation pass.
- ICHPPC lifecycle, pharmacy search, and administration report navigation passed
  against `validation3`. The calculator numerical checks passed, then its
  invalid-input check exposed the focus-clearing defect above; the final repair
  still requires a rebuilt-package browser pass.
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

## Resource controls and remaining validation

Java builds run only while the VM is stopped, with a 6 GiB build cgroup, no
cgroup swap, one test fork, and a host guard reserving at least 10 GiB available
memory. Baseline Java build minimum host availability was 16.02 GiB.

The initial 6 GiB VM browser run reached its guest reserve; the guard paused the
suite and terminated the browser. This is an incomplete suite run, not a pass.
The VM was stopped and configured for 8 GiB for the next pass. Host and guest
memory monitors remain active. No compilation overlaps the running VM.

Rebuilt-package validation, the complete browser result inventory, and final
repair-test results will be added after that pass. Tests requiring additional
fixtures or external services must be recorded as unverified/skipped rather
than counted as passes.

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
not billing row payloads; it requires classification separately from the real
password-refusal leak fixed here. Path-containment and hash-comparison scanner
findings are under review; no global scanner suppression has been added.

Promotion DCO flags five historical commits without trailers and its reporting
step also receives HTTP 403. New repair commits carry DCO sign-off. This report
makes no assertion that the promotion's existing DCO/security gates are green.
