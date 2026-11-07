# Consolidated test-ticket acceptance

Target: `release/2026.08`. This is the working acceptance register for the single
follow-up PR. No ticket closes merely because it appears here or because unit
tests pass. The source issue remains the authoritative full acceptance contract.
Product defects must remain visible; expected failures, skipped prerequisites,
missing cases, failed cleanup and incomplete receipts cannot become release passes.

## Ticket inventory

| Ticket | Scope | Status |
| --- | --- | --- |
| [#4102](https://github.com/carlos-emr/carlos/issues/4102) | test: run the locking and race tests against a real MariaDB in CI | Implementation and installed acceptance pending. |
| [#4492](https://github.com/carlos-emr/carlos/issues/4492) | test(scripts): Playwright coverage plan, upgrade-path check, remaining Ontario checks, configuration profiles and hardening (plan Tasks 17 and 35–48) | Implementation and installed acceptance pending. |
| [#5700](https://github.com/carlos-emr/carlos/issues/5700) | Test: Release 2026.08 full verification coverage plan (Java, Node, Playwright) | Existing merged tooling retained. Scope ownership, compatibility decisions, installed execution and vendor prerequisites remain open. |
| [#5707](https://github.com/carlos-emr/carlos/issues/5707) | Test: cover successful key-pair creation through the administration UI | Implementation and installed acceptance pending. |
| [#5708](https://github.com/carlos-emr/carlos/issues/5708) | Test: cover multi-recipient lab-forwarding rule updates and selective removal | Implementation and installed acceptance pending. |
| [#5709](https://github.com/carlos-emr/carlos/issues/5709) | Test: broaden lookup-list CRUD coverage and verify consumer/list isolation | Implementation and installed acceptance pending. |
| [#5715](https://github.com/carlos-emr/carlos/issues/5715) | Test: verify clinical-record preservation through populated patient merge and unmerge | Implementation and installed acceptance pending. |
| [#5716](https://github.com/carlos-emr/carlos/issues/5716) | Test: verify measurement graph dates, values and plotted positions | Implementation and installed acceptance pending. |
| [#5717](https://github.com/carlos-emr/carlos/issues/5717) | Test: validate growth-chart point placement and reference curves independently | Implementation and installed acceptance pending. |
| [#5718](https://github.com/carlos-emr/carlos/issues/5718) | Test: broaden decision-support guideline condition and operator coverage | Implementation and installed acceptance pending. |
| [#5719](https://github.com/carlos-emr/carlos/issues/5719) | Test: cover Ontario RA re-import, settlement replay and subsequent adjustments | Implementation and installed acceptance pending. |
| [#5724](https://github.com/carlos-emr/carlos/issues/5724) | Test: cover OAuth access-token expiry and deletion of a client with issued tokens | Implementation and installed acceptance pending. |
| [#5725](https://github.com/carlos-emr/carlos/issues/5725) | Test: verify Report-by-Template results and exports remain isolated across tabs | Implementation and installed acceptance pending. |
| [#5726](https://github.com/carlos-emr/carlos/issues/5726) | Test: ingest original, corrected and out-of-order HL7 results for one accession | Implementation and installed acceptance pending. |
| [#5727](https://github.com/carlos-emr/carlos/issues/5727) | Test: cover First Nations field save, edit, clear and community-selection round trips | Implementation and installed acceptance pending. |
| [#5728](https://github.com/carlos-emr/carlos/issues/5728) | Test: verify exactly-once Messenger delivery to overlapping groups and direct recipients | Implementation and installed acceptance pending. |
| [#5729](https://github.com/carlos-emr/carlos/issues/5729) | Test: verify Messenger read and archive state is independent for each recipient | Implementation and installed acceptance pending. |
| [#5730](https://github.com/carlos-emr/carlos/issues/5730) | Test: expand document-upload coverage across supported formats and malformed content | Implementation and installed acceptance pending. |
| [#5731](https://github.com/carlos-emr/carlos/issues/5731) | Test: preserve scanned and mixed-geometry PDFs through incoming-document edits | Implementation and installed acceptance pending. |
| [#5732](https://github.com/carlos-emr/carlos/issues/5732) | Test: verify shared contact edits and patient-specific relationship isolation | Implementation and installed acceptance pending. |
| [#5733](https://github.com/carlos-emr/carlos/issues/5733) | Test: verify complete lab-report content across PDF page breaks | Implementation and installed acceptance pending. |
| [#5734](https://github.com/carlos-emr/carlos/issues/5734) | Test: preserve multiple same-severity Rx interaction partners when one is removed | Implementation and installed acceptance pending. |
| [#5735](https://github.com/carlos-emr/carlos/issues/5735) | Test: cover Schedule of Benefits imports that update existing codes and multiple rows | Implementation and installed acceptance pending. |
| [#5736](https://github.com/carlos-emr/carlos/issues/5736) | Test: cover BC payment amount boundaries, overpayments and supported refunds | Implementation and installed acceptance pending. |
| [#5737](https://github.com/carlos-emr/carlos/issues/5737) | Test: verify appointment-slot lock expiry and recovery after an abandoned browser | Implementation and installed acceptance pending. |
| [#5738](https://github.com/carlos-emr/carlos/issues/5738) | Test: cover abandoned appointment cut/paste and stale clipboard recovery | Implementation and installed acceptance pending. |
| [#5771](https://github.com/carlos-emr/carlos/issues/5771) | Test: Tomcat shutdown leak smoke check doesn't match MariaDB Connector/J thread names | Matcher and independent warning controls implemented; actual installed shutdown retake pending. |
| [#5779](https://github.com/carlos-emr/carlos/issues/5779) | Test: #5548 inbox counts are only partly exercised on physical MariaDB | Native lab/document/HRM count cases added on ON/BC lanes; physical-MariaDB runs pending. |
| [#5793](https://github.com/carlos-emr/carlos/issues/5793) | Test: Release adds 11 expected-failure pins, including security and audit checks | Implementation and installed acceptance pending. |
| [#5794](https://github.com/carlos-emr/carlos/issues/5794) | Test: Front-door check for sanitized error bodies was removed while ResponseSanitizationFilter was rewritten | Temporary installed fault-route adapter and sensitive output assertions added; installed front-door qualification pending. |
| [#5795](https://github.com/carlos-emr/carlos/issues/5795) | Test: Missing browser-check prerequisites now SKIP instead of failing, including the CDS import gate | Packaged runner enforces strict release/residue checking and fails on report loss; packaged runs pending. |
| [#5796](https://github.com/carlos-emr/carlos/issues/5796) | Test: Stored partial-precision allergy start dates are no longer covered (allergy-partial-start-date removed) | Stored year/year-month display and reaction-only amendment restored; installed acceptance pending. |
| [#5797](https://github.com/carlos-emr/carlos/issues/5797) | Test: Two new tests never run in CI, and the H2 test job doesn't fail on unexpected skips | Dedicated permission and legacy-library CI lanes plus exact-result checks implemented; full CI/native acceptance pending. |

## Execution and review

Use a disposable Ubuntu 26.04 container, Java 25, physical MariaDB and the locked
Node/browser dependencies. Install the actual Debian package set for browser and
lifecycle acceptance and bind results to exact source/package identities. Raw
logs and synthetic browser artifacts remain in private execution storage.

For #5794, after provisioning a disposable systemd package container and loading
its normal suite environment, run:

```sh
CARLOS_DISPOSABLE_HOST=true python3 scripts/ci/run-installed-error-sanitization.py
```

The adapter adds temporary exact fault mappings to the installed `web.xml`, tests
through the normal HTTPS front door, restores its original bytes, removes the
owned class and restarts the service. It refuses existing fixture directories or
linked descriptors. Concurrent descriptor changes fail and are retained for
explicit recovery; they are never silently overwritten. These routes bypass the
CXF servlet to exercise the installed filter mappings; separate application/CXF
coverage remains necessary. No fixture is packaged. Direct execution without the
fixture is an explicit prerequisite skip and cannot pass strict qualification.

Three review rounds are required: scope and coverage; correctness, failures and
cleanup; then final source, installed evidence, CI and review-thread reconciliation.
Round 1 (scope/failure review): found and corrected missing NOT NULL fixture
fields, native family-floor/routing drift, the fault adapter prerequisite in
packaged/lifecycle suite entry points, report-copy failures previously ignored,
and concurrent descriptor changes that must be retained rather than overwritten.
Focused checks pass; remaining rounds require actual native/installed results.
