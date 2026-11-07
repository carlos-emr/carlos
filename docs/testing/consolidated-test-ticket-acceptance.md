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
| [#5177](https://github.com/carlos-emr/carlos/issues/5177) | Consolidated validation-harness findings and packaged retakes | Outstanding original source acceptance and complete ON/BC repetitions remain pending. |
| [#5700](https://github.com/carlos-emr/carlos/issues/5700) | Test: Release 2026.08 full verification coverage plan (Java, Node, Playwright) | Existing merged tooling retained. Scope ownership, compatibility decisions, installed execution and vendor prerequisites remain open. |
| [#5707](https://github.com/carlos-emr/carlos/issues/5707) | Test: cover successful key-pair creation through the administration UI | Native creation/download/signature/reopen/duplicate lifecycle passes on the installed ON package with a temporary synthetic primary prerequisite, whose removal restores its original absence; strict missing-prerequisite failure retained. BC/final qualification pending. |
| [#5708](https://github.com/carlos-emr/carlos/issues/5708) | Test: cover multi-recipient lab-forwarding rule updates and selective removal | Installed ON two-recipient/reopen/repeat-save, three-upload routing, cancellation, selective removal and admin isolation pass. Status is source-wide; per-recipient status is unavailable. Original method/CSRF coverage retained. |
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
| [#5779](https://github.com/carlos-emr/carlos/issues/5779) | Test: #5548 inbox counts are only partly exercised on physical MariaDB | Seven cases pass on both physical ON and BC Flyway schemas with zero skips at 239cd330e1a; final expanded normal/abnormal assertions and final-head retakes pending. |
| [#5793](https://github.com/carlos-emr/carlos/issues/5793) | Test: Release adds 11 expected-failure pins, including security and audit checks | All release-added pins reconciled to open ownership below; retired 335 distinguished. No release waiver or product fix claimed. |
| [#5794](https://github.com/carlos-emr/carlos/issues/5794) | Test: Front-door check for sanitized error bodies was removed while ResponseSanitizationFilter was rewritten | Installed ON REQUEST exception, /ws and observed ERROR-dispatch 500 sanitization pass; exact descriptor restoration and fixture removal verified. BC/final qualification pending. |
| [#5795](https://github.com/carlos-emr/carlos/issues/5795) | Test: Missing browser-check prerequisites now SKIP instead of failing, including the CDS import gate | Packaged runner enforces strict release/residue checking and fails on report loss; packaged runs pending. |
| [#5796](https://github.com/carlos-emr/carlos/issues/5796) | Test: Stored partial-precision allergy start dates are no longer covered (allergy-partial-start-date removed) | Installed ON year/year-month display and both reaction-only amendments pass with clean residue audit. BC/final qualification pending. |
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

The adapter adds temporary exact fault mappings and a numeric 500 error-page mapping to the installed `web.xml`, tests
through the normal HTTPS front door, restores its original bytes, removes the
owned class and restarts the service. It refuses existing fixture directories or
linked descriptors. Concurrent descriptor changes fail and are retained for
explicit recovery; they are never silently overwritten. These routes bypass the
CXF servlet to exercise the installed filter mappings; separate application/CXF
coverage remains necessary. No fixture is packaged. Direct execution without the
fixture is an explicit prerequisite skip and cannot pass strict qualification.

Three local review rounds are required: scope and coverage; correctness, failures and
cleanup; then final source, installed evidence, CI and review-thread reconciliation.
Round 1 (scope/failure review): found and corrected missing NOT NULL fixture
fields, native family-floor/routing drift, the fault adapter prerequisite in
packaged/lifecycle suite entry points, report-copy failures previously ignored,
and concurrent descriptor changes that must be retained rather than overwritten.
Focused checks pass; remaining rounds require actual native/installed results.

Round 2 (correctness and sensitivity): require all three new inbox case identities
on each native lane, with unrelated replacement controls under normal and
optimized Python. Corrected test discovery for those controls, added missing
JDK/parser runtime prerequisites, and made descriptor publication atomic while
preserving package ownership/mode/timestamps. The key-creation check runs
independently of the original negative GET pin. A real ON/BC MariaDB run passes
7 cases per province; the exact permission regression passes unprivileged.
CodeRabbit received the full-review request but declined it due to its account
rate limit; no successful CodeRabbit review is claimed.

Round 3 (deployment-path review, still awaiting final execution): distinguish
REQUEST exception handling from actual Tomcat ERROR dispatch. A test-only
`sendError` path now renders a tainted error page through the installed ERROR
mapping, with an independent dispatch witness and missing-mapping negative
control. The HTML servlet uses a JSP-suffixed exact mapping to pass the real
Struts exclusion contract. APT index failures are fatal in both image/setup
steps. Final installed retakes, green final-head CI and CodeRabbit remain open.

Installed ON evidence at harness head `458c284d9fd0` uses the locally built
`carlos-emr_2026.09.0~snapshot28_amd64.deb` (SHA-256
`428f5a7f525359fc8a15f80bb84bf04d402aa7d83dbc43ba382e306ff78ecf28`)
from source `239cd330e1a6`, the matching pinned DrugRef package and CLI 1.3.1,
on Ubuntu 26.04.1 with Java 25 and Playwright Chromium 149. No production bytes
changed in the intervening harness commits. Error sanitization and both stored
allergy precision amendments pass. Key creation first reports a strict
prerequisite skip; after separately staging an owned synthetic primary key,
creation/download/independent signature/reopen/duplicate checks pass. Its
prerequisite is removed with exact ownership verification, restoring the
original absence. All three checks report clean residue audits. These are
focused evidence, not a full qualification receipt or issue-closure claim.

The first full CI H2 evidence contains 34,077 cases, no failures, and 139 skips.
Four additional exact root-sensitive methods were discovered by the new gate.
They join startup refusal in a dedicated five-case lane; all five pass
unprivileged on Ubuntu 26.04 without skips. The gate rejects missing, substituted,
skipped and failed required methods, and sanitized artifacts now preserve the
separate permission and legacy reports. CodeRabbit accepted a full-review
request after its rate-limit window; review completion remains pending.

## Expected-failure ownership (#5793)

The current target has 26 pins. The issue's earlier count of 27 includes finding
335, whose fix and pin removal are already on this branch. The following are
tracking links, with no release waiver. Every remaining known failure is fatal
under `--strict-release`; later assertions behind a pinned failure remain
unreached. Tracker state was checked on 7 November 2026.

| Release-added finding | Check | Open defect ownership / disposition |
| --- | --- | --- |
| 329 | `appointment-email-option` | [#4788, explicit 329 task](https://github.com/carlos-emr/carlos/issues/4788#issuecomment-6713494284); related implementation #4472 does not repair the existing checkbox by itself. |
| 330 | `admin-messages` | [#4788, explicit 330 task](https://github.com/carlos-emr/carlos/issues/4788#issuecomment-6713494284), with retention/retirement #5383. |
| 331 | `admin-issue-editor` | [#4788, explicit 331 task](https://github.com/carlos-emr/carlos/issues/4788#issuecomment-6713494284), with retention #5383 and method/CSRF #3059. |
| 332 | `chart-informed-consent` | [#4788, explicit 332 task](https://github.com/carlos-emr/carlos/issues/4788#issuecomment-6713494284). |
| 335 | `echart-forms-list-paging` | Fixed by #5691/#5684; no current expected-failure pin. Installed qualification is still part of the full release plan. |
| 337 | `dialog-confirmations-tickler` | [#5683](https://github.com/carlos-emr/carlos/issues/5683). |
| 342 | `authz-read-admin-objects` | [#4223](https://github.com/carlos-emr/carlos/issues/4223). |
| 343 | `authz-read-print-export` | [#4223](https://github.com/carlos-emr/carlos/issues/4223); closed #5406 is historical evidence, not a live fix tracker. |
| 344 | `audit-log-locked-patient` | [#4172](https://github.com/carlos-emr/carlos/issues/4172). |
| 347 | `login-mfa-no-pin` | [#4788, explicit 347 task](https://github.com/carlos-emr/carlos/issues/4788#issuecomment-6713494284). |
| 348 | `document-clinical-followup` | [#5685](https://github.com/carlos-emr/carlos/issues/5685). |

The older security/authorization pins retain these fix owners: API-key GET
creation (163) → #3059; chart note and billing authorization (198, 200), document
download authorization (202) → #4223; missing chart-read audit (141) → #4172.
The stored MCEDT credential (189) and output-encoding findings (118, 122, 123,
148) remain open findings in the browser-defect umbrella #4788 and its source
acceptance, rather than being silently treated as accepted release behavior.

Round 3 retake and external review: CodeRabbit's completed review found seven
inline items and one output-drain race outside the diff. Corrections distinguish
missing keys from malformed counts, correlate allergy reload with the save
response, cover all legacy/refusal branches, assert adapter wiring, require
specific negative-control failure reasons and document fixture behavior. The
shutdown subprocess now drains stdout/stderr through `close`; a descendant
writing a leak after parent exit independently proves the failure remains seen.
The fixture's creation date is verified from its first Git commit.

The additional #5708 check passes on the installed ON package: exactly two
recipients, repeat-save without duplicates, source-wide New → Filed, three fresh
CML uploads, unchanged earlier routes, cancelled removal, selective archival and
Administration source isolation. The existing single-recipient and negative
checks remain registered. Failed development attempts and their exact recovery
are retained privately; the final run and the allergy retake have clean residue
audits. The application offers a source-wide status, so a per-recipient status
update cannot be claimed. Four legacy compatibility cases also pass without
skips on Ubuntu 26.04 with the pinned historical libraries.

The installed `--include-warn` log audit still fails on nine signatures: missing
property defaults, Drools build/evaluation warnings, Hibernate mapping/dialect
warnings and the deliberately malformed satellite input's controlled-400 log.
They are not waived or hidden. Only exact disposable #5794 routes and the exact
fixture stack are explained, with independent controls proving ordinary routes,
malformed tokens and production servlet exceptions still fail the log audit.
Full release readiness remains blocked by these warnings and the other
uncompleted acceptance rows above.
