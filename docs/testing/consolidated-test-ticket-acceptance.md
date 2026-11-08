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
| [#5707](https://github.com/carlos-emr/carlos/issues/5707) | Test: cover successful key-pair creation through the administration UI | Native creation/download/signature/reopen/duplicate lifecycle passes on the installed ON package with a temporary synthetic primary prerequisite, whose removal restores its original absence; strict missing-prerequisite failure retained. Both ON and BC focused installed retakes pass; final full qualification remains pending. |
| [#5708](https://github.com/carlos-emr/carlos/issues/5708) | Test: cover multi-recipient lab-forwarding rule updates and selective removal | Installed ON two-recipient/reopen/repeat-save, three-upload routing, cancellation, selective removal and admin isolation pass. Status is source-wide; per-recipient status is unavailable. Original method/CSRF coverage retained. |
| [#5709](https://github.com/carlos-emr/carlos/issues/5709) | Test: broaden lookup-list CRUD coverage and verify consumer/list isolation | Actual list/consumer/operation inventory documented. ON/BC three-list native adds, duplicate/empty behavior, exact ordering/identity, all consumer save/reopen and selective isolation pass. Full removal fails correctly on provider inactive options (#5816); GET still returns 200 and changes order (existing finding 58/#3059). Independent native cache restoration and exact SQL cleanup pass after both failures. PHU is hidden in ON and absent in BC; no positive edit/reorder UI exists. |
| [#5715](https://github.com/carlos-emr/carlos/issues/5715) | Test: verify clinical-record preservation through populated patient merge and unmerge | Implementation and installed acceptance pending. |
| [#5716](https://github.com/carlos-emr/carlos/issues/5716) | Test: verify measurement graph dates, values and plotted positions | Installed ON/BC five-point irregular-date geometry, independent rendered-axis calibration, three-pixel tolerance, native selected/multi-delete, clinical isolation and four sensitivity controls pass. Empty history hides Plot; single-point PNG output is observed, with its invisible point/indistinguishable ticks separately tracked as #5814. No date-range controls exist. |
| [#5717](https://github.com/carlos-emr/carlos/issues/5717) | Test: validate growth-chart point placement and reference curves independently | Implemented; [growth inventory](growth-clinical-geometry-inventory.md). Both sexes pass installed ON/BC coordinates, exact WHO chart/curve/unit/age-axis pixels, independent median relationships, source multiplicity, saved-history/control preservation and owned cleanup. Final full qualification remains pending. |
| [#5718](https://github.com/carlos-emr/carlos/issues/5718) | Test: broaden decision-support guideline condition and operator coverage | 41 independently specified rules reach all 205 ON chart/list/detail observations, including expanded warnings, native diagnosis resolution, alternating patients, provider scope and unchanged clinical snapshots. Exactly 40 mismatches remain: sex all/notall across four surfaces on five visits (#5817). Other selected operator cases and zero-residue cleanup pass; no pin. Inventory records unsupported/dynamic-context families and missing actual-value readers. |
| [#5719](https://github.com/carlos-emr/carlos/issues/5719) | Test: cover Ontario RA re-import, settlement replay and subsequent adjustments | Implemented; [replay inventory](billing-on-ra-replay-inventory.md). Installed ON original/duplicate import, protected settlement replay and subsequent signed -5.00 recovery/settlement/reimport pass. Exact original/rejected/control financial state and net balance remain correct. SQL NULL/foreign ownership and captured-header drift refuse cleanup; unacknowledged writes retain fixtures. Final full qualification remains pending. |
| [#5724](https://github.com/carlos-emr/carlos/issues/5724) | Test: cover OAuth access-token expiry and deletion of a client with issued tokens | Native consent, exact expiry-second semantics, client deletion and independent token/session controls pass on installed ON and BC with no clinical writes or residue. |
| [#5725](https://github.com/carlos-emr/carlos/issues/5725) | Test: verify Report-by-Template results and exports remain isolated across tabs | Installed ON/BC four-tab, shared-login native runs, exact CSV/XLS schemas/values/types, empty results, Back beacons and tab closure pass with clean residue. List parameters used; separate empty-text-parameter defect tracked as #5813. |
| [#5726](https://github.com/carlos-emr/carlos/issues/5726) | Test: ingest original, corrected and out-of-order HL7 results for one accession | Native original/correction/older CML ingestion, original acknowledgment, per-version routing/review, explicit/history/clinical-chart reads, independent control and exact replay implemented. ON/BC reach all stages and fail on chart-current ranking (#5819) and stale active Measurements (#5820); cleanup is clean. Stronger row-set/read-isolation retakes reach completion on both provinces; one failed BC navigation attempt is retained, with the settled-handoff retake completing without unexpected diagnostics. |
| [#5727](https://github.com/carlos-emr/carlos/issues/5727) | Test: cover First Nations field save, edit, clear and community-selection round trips | Native lifecycle implemented; installed ON enabled-profile attempt fails on the real undefined focus handler and reopened First Nation flag (#5815). Later steps remain unreached. Owned fixtures are removed and the temporary properties/lookup profile is restored exactly; no pin or waiver. |
| [#5728](https://github.com/carlos-emr/carlos/issues/5728) | Test: verify exactly-once Messenger delivery to overlapping groups and direct recipients | Native ON/BC two overlapping groups plus a direct recipient produce exactly one delivery each; membership edits, a legacy duplicate and a removed-member control pass. Exclusive membership coordination and exact cleanup are required. |
| [#5729](https://github.com/carlos-emr/carlos/issues/5729) | Test: verify Messenger read and archive state is independent for each recipient | Native ON/BC read, mark-new, archive and restore transitions preserve other recipients, shared content, attachments, patient links and Sent access. |
| [#5730](https://github.com/carlos-emr/carlos/issues/5730) | Test: expand document-upload coverage across supported formats and malformed content | Fresh ON/BC format/byte/metadata/routing/native output, exact multipart boundaries, draft/retry and safe malformed-preview fallback all reached. Fails only on valid mixed-case PDF page count (#5818); exact cleanup and independent zero-residue audits pass in both provinces. |
| [#5731](https://github.com/carlos-emr/carlos/issues/5731) | Test: preserve scanned and mixed-geometry PDFs through incoming-document edits | Implemented; [mixed-PDF inventory](incoming-mixed-pdf-inventory.md). Native ON/BC image-only/mixed previews, selected extraction, single/all rotation, selected deletion, exact page/crop/pixel/annotation preservation, recycle output and source/extracted reopen pass. Both original and new checks pass together; independent audits confirm zero owned queue/recycle/cache files. Final full qualification remains pending. |
| [#5732](https://github.com/carlos-emr/carlos/issues/5732) | Test: verify shared contact edits and patient-specific relationship isolation | Installed ON/BC native creation, two-patient linking, shared-detail editing, independent flags, cancelled edits/removal and selective removal pass; unrelated control and cleanup verified. |
| [#5733](https://github.com/carlos-emr/carlos/issues/5733) | Test: verify complete lab-report content across PDF page breaks | Native ON/BC eight-page PDFs preserve all 60 ordered clinical rows and their comment tokens, numeric columns, flags, patient identity, footers and margins. Extracted coordinates and actual raster ink are checked; omission, duplication and blank-raster controls reject. Source and unrelated-report controls pass. |
| [#5734](https://github.com/carlos-emr/carlos/issues/5734) | Test: preserve multiple same-severity Rx interaction partners when one is removed | Installed ON/BC native current/staged moderate partners, exact JSON/marker/tooltip identities, staged removal/re-add, current-medication discontinuation/staged re-add, threshold 1/2/3/4 and patient overrides all pass. DrugRef pairs are read-only and independently validated; metformin is a noninteracting control, with no unexpected prescription writes and clean owned cleanup. |
| [#5735](https://github.com/carlos-emr/carlos/issues/5735) | Test: cover Schedule of Benefits imports that update existing codes and multiple rows | Installed ON native multi-row preview/apply, malformed-file refusal/retry, abandonment, GET/CSRF refusals, exact appended periods, historical claim preservation, duplicate upload and code-admin reopening pass. Error feedback is file-level; per-line diagnostics are absent. |
| [#5736](https://github.com/carlos-emr/carlos/issues/5736) | Test: cover BC payment amount boundaries, overpayments and supported refunds | Installed BC refusals, exact-cent partial/overpayment, receipt/list/accounting reconciliation and control-invoice preservation pass. No native refund control exists; negative amounts are refused. |
| [#5737](https://github.com/carlos-emr/carlos/issues/5737) | Test: verify appointment-slot lock expiry and recovery after an abandoned browser | Implemented; [expiry inventory](appointment-slot-expiry-inventory.md). All native stages pass on snapshot31 in ON/BC; exact configuration restoration and post-run audits follow below. |
| [#5738](https://github.com/carlos-emr/carlos/issues/5738) | Test: cover abandoned appointment cut/paste and stale clipboard recovery | Implemented; [recovery inventory](appointment-cut-recovery-inventory.md). All native stages pass on snapshot31 in ON/BC; the observed archive-only interrupted-Cut outcome remains explicit, without inventing automatic undo. |
| [#5771](https://github.com/carlos-emr/carlos/issues/5771) | Test: Tomcat shutdown leak smoke check doesn't match MariaDB Connector/J thread names | Matcher and delayed-output CLI controls pass; actual installed ON stop/start log contains zero target leak warnings. Full lifecycle repetitions remain pending. |
| [#5779](https://github.com/carlos-emr/carlos/issues/5779) | Test: #5548 inbox counts are only partly exercised on physical MariaDB | Seven cases pass on both physical ON and BC Flyway schemas with zero skips at 239cd330e1a. CI at fb0401df0c1 passes both native lanes including expanded normal/abnormal assertions; final-head evidence still required after further edits. |
| [#5793](https://github.com/carlos-emr/carlos/issues/5793) | Test: Release adds 11 expected-failure pins, including security and audit checks | All release-added pins reconciled to open ownership below; retired 335 distinguished. No release waiver or product fix claimed. |
| [#5794](https://github.com/carlos-emr/carlos/issues/5794) | Test: Front-door check for sanitized error bodies was removed while ResponseSanitizationFilter was rewritten | Installed ON REQUEST exception, /ws and observed ERROR-dispatch 500 sanitization pass; exact descriptor restoration and fixture removal verified. Both ON and BC focused installed retakes pass; final full qualification remains pending. |
| [#5795](https://github.com/carlos-emr/carlos/issues/5795) | Test: Missing browser-check prerequisites now SKIP instead of failing, including the CDS import gate | Packaged runner enforces strict release/residue checking and fails on report loss; packaged runs pending. |
| [#5796](https://github.com/carlos-emr/carlos/issues/5796) | Test: Stored partial-precision allergy start dates are no longer covered (allergy-partial-start-date removed) | Installed ON year/year-month display and both reaction-only amendments pass with clean residue audit. Both ON and BC focused installed retakes pass; final full qualification remains pending. |
| [#5797](https://github.com/carlos-emr/carlos/issues/5797) | Test: Two new tests never run in CI, and the H2 test job doesn't fail on unexpected skips | Dedicated permission and legacy-library CI lanes plus exact-result checks implemented; full CI/native acceptance pending. |
| [#5801](https://github.com/carlos-emr/carlos/issues/5801) | Test: cover visual eForm editor checkboxes, radio groups, images and signatures | Newly identified in the 8 November open-ticket refresh; implementation and installed acceptance pending. |
| [#5802](https://github.com/carlos-emr/carlos/issues/5802) | Test: verify session loss while clinical editors contain unsaved work | Implemented; [session inventory](session-clinical-recovery-inventory.md). Both ON and BC strict-release suites pass all six stages and exact cleanup. Real refusal, no unauthorized writes, native heartbeat closure, same-account D1 recovery, same-browser other-account/storage isolation and conservative request supervision are covered. |
| [#5803](https://github.com/carlos-emr/carlos/issues/5803) | Test: preserve qualified and nonnumeric laboratory results across views and PDF output | Newly identified in the 8 November open-ticket refresh; implementation and installed acceptance pending. |
| [#5804](https://github.com/carlos-emr/carlos/issues/5804) | test(playwright): cover end-to-end lab recall message delivery to delegates | Implemented; [delivery inventory](lab-recall-delivery-inventory.md). Actual send, delegate reads, assigned task/lab navigation and both existing regressions pass on snapshot31 in ON/BC. |
| [#5805](https://github.com/carlos-emr/carlos/issues/5805) | test(playwright): cover Ontario payments allocated across multiple invoice items | Implemented; [allocation inventory](billing-on-multi-item-payments-inventory.md). All three native stages pass on ON: two distinct partial allocations, complete one item while another remains partial, then finish. Exact item/payment/header/audit amounts, reopened line balances, payment details/history and displayed/printed totals reconcile; the full control snapshot is unchanged. Existing single-item regression also passes. |
| [#5806](https://github.com/carlos-emr/carlos/issues/5806) | test(playwright): cover OAuth 1.0a registered web callbacks and denial | Native ON/BC exact and plain-query registered callbacks complete actual consent/303 receiver delivery, verifier exchange and signed provider/patient/scope checks. Native Deny, denied exchange and host/scheme/lookalike-path application refusals pass. Encoded-query initiation fails on #5823 and its later stages remain explicitly unreached; independent cases continue. Owned token/client/nonce/patient/receiver cleanup passes, with no new waiver pin. |
| [#5807](https://github.com/carlos-emr/carlos/issues/5807) | test(playwright): cover email consent states and changes after composition opens | Implemented; [consent inventory](email-consent-transitions-inventory.md). Six native deliveries pass on ON/BC across fresh Unknown/Opt-Out/Opt-In, second-context changes with open drafts, preserved pending body, current warning/snapshot contract and restored Opt-In. Both existing email/consent regressions pass. Sender IDs avoid all legacy log references; ten controls and exact legacy preservation/cleanup pass. |
| [#5808](https://github.com/carlos-emr/carlos/issues/5808) | test(playwright): round-trip image-backed eForms through ZIP export and import | Implemented; [ZIP inventory](eform-image-zip-roundtrip-inventory.md). Actual exported HTML/properties/two image assets match exactly, source rows/files are absent before native reimport, and chart save/reopen plus PDF text/geometry/colour regions pass in ON/BC. Both existing export/navigation regressions pass; exact owned cleanup and unrelated resource preservation pass. |
| [#5809](https://github.com/carlos-emr/carlos/issues/5809) | test(playwright): verify eForm page exclusion in deployed PDF output | Native ON/BC clean, form-wrapped and body-direct layouts each pass new and saved-instance PDF output, actual renderer receipts, independently checked page/text/geometry/ink and strict clinical bounds. Native interstitial approval preserves its exact saved revision without an extra save. Exact SQL/download/server-PDF cleanup and refusal controls pass; final full qualification remains pending. |
| [#5810](https://github.com/carlos-emr/carlos/issues/5810) | test(playwright): cover stale tickler selections during filtering and pagination | Native ON/BC preselected search/status/assignee/page changes, held real list responses, inert pending controls, Clear All, out-of-order completion, exact Complete/Delete POST IDs and task/history/control isolation all pass. Barrier delivery/disposal/route-removal failures are independently exercised. Exact owned cleanup passes; final full qualification remains pending. |
| [#5811](https://github.com/carlos-emr/carlos/issues/5811) | test(playwright): cover Query-by-Example result limits, timeout and recovery | Native ON/BC 999/1000/1001-row boundaries, aggregate encoded and single streamed-cell character caps, genuine reporting-account timeout observed in MariaDB, explicit timeout guidance/cancellation, same-composer recovery and exact history/private INFO audit receipts pass. Twenty controls (18 rejection and two positive) and existing QBE regressions pass; owned cleanup is clean. Temporary logging is restored; latest-target full qualification remains pending. |
| [#5812](https://github.com/carlos-emr/carlos/issues/5812) | test(playwright): verify MRP tickler preference routes newly created tasks | Implemented; [routing inventory](tickler-mrp-routing-inventory.md). Native ON/BC reach all independent active-MRP, recipient/patient, late association, manual assignment and recovery cases. Four comparisons fail on draft loss (#5824) and arbitrary missing/inactive-MRP fallback (#5825); no unrelated recipient is submitted and no waiver is added. Exact cleanup and cache retirement pass. |

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

The current target has 25 pins after the legitimate retirement of finding 348. The issue's earlier count of 27 includes finding
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
| 348 | `document-clinical-followup` | Fixed on the latest target; no current expected-failure pin. Historical owner: [#5685](https://github.com/carlos-emr/carlos/issues/5685). |

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

Final focused installed retakes at harness content `1894471c154` (and the same
locked driver as `927f33ee3b2`) pass 4/4 ON and 3/3 BC, zero failures/skips, with
clean residue. Both wrappers verify exact descriptor restoration and fixture
removal; both synthetic primary keys are removed, restoring their original
absence. The ON actual installed stop/start log is scanned by the leak detector
with zero target or other-application leak warnings.

A direct root capability-drop execution exposed the purge test's username-only
assumption. It now checks actual directory readability after removing mode bits,
with permission restoration in `finally`. All five exact methods pass both
unprivileged and with root's DAC bypass capabilities removed. The root reports
pass the H2 gate alongside the 34,077-case CI report. A full qualification claim
still requires final-head CI and all outstanding register entries.

All seven initial CodeRabbit inline threads were individually answered and
resolved after their corrections and validation; the separate output-drain
issue was corrected too. CodeRabbit's second full review completed on
2026-11-07 at 22:52 UTC against fb0401df0c1 and raised twelve comments.
The corrections and their completed validation are recorded below; review
threads are reconciled after publication. No third external review completion
or final review clearance is claimed.

Additional independent lifecycle coverage (#5724, #5732, #5736) passes through
the same installed package front doors. The ON batch passes 2/2 and BC passes
3/3, with zero failures/skips and clean residue. OAuth tokens are obtained using
real consent; the finite token is tested within its exact expiry second and
again immediately afterward. A separately authorized active client and an
ordinary browser session remain usable across expiry and native deletion.
Captured client IDs keep cleanup effective after the application deletes a
client, including its owned request/access tokens and nonces.

The contact lifecycle creates the directory record natively and keeps patient
A/B in separate real sessions to avoid the application's reused named windows.
Both link rows are snapshotted independently; shared edits preserve both,
patient-specific flag edits preserve B, abandoned directory/relationship/removal
edits preserve storage, and removing A preserves B and the directory. An
unrelated contact/patient control remains byte-for-byte unchanged. Native
self-closing controls are awaited with their actual close and opener reload.

The BC amount lifecycle retains the original positive and method checks, adds
a tokenless POST refusal, and verifies zero, negative, malformed, over-width and
sub-cent refusals leave no financial rows. A corrected 10.01 partial payment
and 20.00 overpayment produce exact 30.01 received and -7.01 credit on a 23.00
invoice, with paid-private status and the selected payment method. The reopened
receipt, header-labelled Invoice List columns and accounting CSV agree. The
refund checkbox is absent on this target, so no refund UI flow is claimed.
The original partial/exact-balance check separately passes after the extension.
All amounts are checked with decimal SQL or integer cents, not floating-point
comparison. Development failures remain recorded; none was converted to a pin
or a passing incomplete result.


The next review correction batch requires the exact fully qualified inbox class
and empty container, preserves independent skip routes, parses each report once,
and applies the same report-directory/identity checks to historical oracles.
The package fault adapter now has 1,860 seconds for its two bounded restarts,
compilation, counters, browser suite and restoration. Partial OAuth/contact
setup is reconciled by exact ownership, and cleanup continues across independent
fixtures while aggregating failures. A changed owner still prevents deletion.
Forwarding provider IDs remain registered as intents before INSERT; a missing
row is harmless during cleanup, and other owned providers are still attempted.

Ubuntu 26.04 / Node 22.22.1 now passes all 7,379 cases with zero failures,
skips, cancellations or TODOs. The release Node inventory is regenerated from
that complete execution, including all 518 test files and the leak CLI case.
The initial Ubuntu attempt lacked jq/Poppler and failed; its report is retained,
the missing distribution packages were installed, and the complete run was
repeated. All 349 Python helper cases pass in the Ubuntu environment. The new
cleanup controls cover a lost INSERT-ID response, changed ownership, independent
cleanup and an uncreated secondary patient.

The ON review retake passes OAuth, shared contacts and native key generation
(3/3, no skip/residue); the synthetic primary key is removed afterward.
The refactored default key workflow reaches its retained finding-163 GET-refusal
pin after its preceding steps pass; this remains a known failure, not a release
pass. Both installed provinces pass the four-tab Report by Template workflow.
The normal Back beacons are observed without interception; only the exact
unloading ping's ERR_ABORTED may be consumed. An empty result offers no export,
and does not replace the populated instances' table or downloaded data.
The separate lone-empty-text-parameter upload defect is tracked in
[#5813](https://github.com/carlos-emr/carlos/issues/5813), with the failed native
attempts retained. No repair of that product defect is claimed here.

All thirteen CI workflows pass at fb0401df0c1, including the Rourke font/cache
matrix. The downloaded actual CI reports also pass the revised H2/permission
and four-case historical gate: 34,077 H2 cases, zero failures, 139 exactly routed
skips, five actual passing permission cases and four actual passing historical
comparisons. The native summary records 3,079 generated-schema, 230 ON Flyway
and 64 BC Flyway cases, all passing without skips, including all seven inbox
cases in each Flyway lane. Further commits still require their own green CI.

The final ON forwarding/SOB retake also passes 2/2 with clean residue. The
forwarding source-wide status limitation remains explicit. Malformed mixed SOB
files offer no partial changes, and a corrected native retry preserves both
previous fee periods and an existing claim; same-schedule reupload creates no
new effective period. Fee cleanup continues independently across code owners.

The target advanced to b6fa82558f7 and was merged at d67651993b1. Its CI H2
execution has 34,123 cases, no failures and 143 skips. Four newly introduced
physical-type cases lacked H2 routing declarations; the strict gate correctly
refused them. They are now individually routed to BC Flyway, whose actual CI
summary confirms all four pass. The native gate additionally requires their
exact class/method identities and empty containers; four same-count replacement
controls prove unrelated cases cannot substitute. The revised gate accepts the
actual H2/permission artifacts, and all 350 Python helper tests pass on Ubuntu.
This correction still requires green CI on its own published commit.

A fresh d67651993b1 WAR and matching DrugRef were packaged as the local
snapshot29 Debian set and installed in both Ubuntu 26.04 provinces. The version
bump is confined to the packaging checkout. Application package SHA-256 is
ec40d8d8e803832de98df86735ae3c22966b99e57b208ec76e17f80ac6839af5;
DrugRef package SHA-256 is
51351fdff4e27b1fe21066d4489c40024e1f17a0205d52582a4fe24c199558d5.
Both installed package checks and dpkg verification pass. Nineteen focused
browser retakes pass (10 ON, 9 BC), with no skips or residue, including the
new Messenger and eight-page lab-PDF workflows. These focused runs do not
replace the outstanding complete qualification and acceptance rows.

The new measurement oracle independently reads the actual printed date/value
ticks and rendered red series, rather than reusing the application's dataset or
range formulas. All five deliberately non-collinear observations and the four
remaining after native deletion match within three native PNG pixels in both
provinces. Missing, swapped, shifted and foreign-observation controls reject.
The history supplies `in kg`; the graph's labels are Weight, Days and Wt, without
a separate printed kg unit. Native empty and single-output transitions are
observed. A single sample has no point glyph and repeats rounded value ticks;
that product limitation is reported as [#5814](https://github.com/carlos-emr/carlos/issues/5814),
not claimed repaired or waived here.

Chromium CDP corrupts the main-frame PNG bytes when the server declares a UTF-8
charset, despite displaying the image correctly. The capture helper intercepts
only the exact native UI graph request for the owned patient/type, fetches that
one request losslessly with redirects disabled, and fulfills it with identical
bytes/headers. It does not replay a guessed URL or re-encode the image. The
original PNG/content-type assertion is retained and passes on installed ON.
Nine controls cover exact bytes, ambiguous identity, failures, missing/duplicate
navigation and handler removal. The Docker validation image includes pinned
pngjs and distribution Tesseract/English data.

The complete Ubuntu 26.04 / Node 22.22.1 execution for this batch passes all
7,398 cases in 520 files, with no failures, skips, cancellations, TODOs or test
source changes. Its reviewed Node inventory is regenerated from that report.

The additional lookup lifecycle passes four native positive phases on both
installed provinces, then independently reports both the inactive-provider-option
defect [#5816](https://github.com/carlos-emr/carlos/issues/5816) and existing GET
mutation finding 58/#3059 (HTTP 200 with changed stored order). Both failures
remain fatal, without new pins. Cleanup separately adds/removes an owned cache
sentinel after SQL teardown and reopens every selected consumer to prove no
marker remains; this does not count as successful normal removal. Full unrelated
lookup rows and configuration are restored. The ON `phu` list exists but its
consumer is hidden by `phu.hide=true`; BC has no `phu` list. These limitations
and all native UI operations are in `lookup-list-consumer-inventory.md`.

The enabled First Nations ON profile uses two owned synthetic communities and
the actual installed module. Its first native round trip reaches correct stored
values, then fails on the reopened First Nation flag; the strict recorder also
captures the undefined `toggleFirstNationFields` focus handler. These product
failures are tracked in [#5815](https://github.com/carlos-emr/carlos/issues/5815).
Later lifecycle steps remain unreached. All owned demographic/control/support
rows are removed. The app is stopped, the exact original properties and lookup
rows are verified/restored, and the original profile is started again, clearing
temporary lookup caches. No successful full-profile qualification is claimed.

CodeRabbit's third requested full review completed on 2026-11-08 at 00:48 UTC
against 9b8de7c95fe. Its one inline finding correctly identified omitted shared
fixture declarations. The OAuth clients, Messenger groups/members/providers,
BC payment/forwarding providers, forwarding rules, report templates, benefit
codes and key-manager entries now declare their clinic-wide mutations and
exclusivity wording. All 68 manifest checks pass after the correction. The
review's docstring advisory is distinct from required CI and is not claimed as
a passed qualification check. Final scope, new-code review and CI reconciliation
remain necessary as work continues.

The decision-support final retake reaches 41 rules over A/B/A, A after native
diagnosis resolution, then B again (205 rule/patient observations). The actual
six-row chart expander is used. Forty fatal mismatches are confined to sex
all/notall on four surfaces per visit, tracked as #5817. All other selected
operators, source transitions, provider scope and clinical snapshots agree;
owned guideline, patient and provider counts return to zero. Quoted DSL note
literals and exact list-row identity were corrected during development; those
earlier failed attempts are retained. `decision-support-operator-inventory.md`
records implementation limits and the missing notes/billing actual-text reader.

The multi-partner Rx retakes pass in both installed provinces. Ciprofloxacin's
two moderate partners (current phenytoin and staged warfarin) remain distinct
through selective removal and re-addition. Native discontinuation removes only
the current source; a staged re-add restores both. Exact authoritative effect,
evidence/comment and partner identities survive all applicable thresholds and
patient/provider overrides. Only an owned clinician's preferences change, and
the DrugRef catalog is not modified. Source fixtures and cleanup are verified.

Current target refresh: merged `d9f0a9551784` into `52b5b9c80752` and retained
both the upstream growth requirements and this PR's exact inbox/physical-type
identities. All 20 focused MariaDB evidence-verifier tests pass after conflict
resolution. A complete qualified Ubuntu 26.04/Node 22.22.1 run passes **7,405
cases in 522 files**, with zero failures/skips/cancellations/TODO and unchanged
test source digests. The committed inventory is regenerated from that result.
The Java 25 build compiles 4,677 production and 2,620 test sources and packages
successfully; this compile/package command intentionally skips test execution.
Fresh Debian snapshot30 uses source `52b5b9c80752ea3d3732ee9e92bb98c6a3cd69ed`:
application SHA-256 `bdc240d0e9581c57290e76634a5ab3e62322e3a295236863d9851009fd2b8e32`,
DrugRef SHA-256 `bfa728a4a9c1223ce1032f19004858aefdb981753f619f1339162493adab1996`.
Installed upgrade/health and changed-library browser retakes remain pending.

The 8 November open-ticket refresh also identifies twelve additional pure test
coverage requests, #5801–#5812, created after this PR's initial inventory. They
are included above as pending work in the same PR; the original batch cannot be
presented as “all open test tickets.” No implementation, execution or closure is
claimed for these new rows.

Both fresh snapshot30 installed upgrades and `carlos-ctl check` pass on ON and
BC with `NRestarts=0`. `dpkg -V` is clean after restoring the two exact packaged
README files omitted by the base image's documentation-exclusion policy; the
policy itself was not changed. ON measurement geometry, lab-PDF pagination,
report-tab isolation, Rx partner semantics and Messenger recipient isolation
all pass against the updated target. The report harness now decodes the
upstream migration's exact `oscarReport.xlsx` OOXML output and retains every
cell-value/type and tab-isolation assertion. Historical XLS receipts above
belong to snapshot29. The new BC document matrix reaches every planned case,
fails only on #5818, acknowledges only the two precisely asserted malformed
render refusals with visible fallback, and verifies zero owned residue. Final
ON and remaining BC retakes are pending.

The ON snapshot30 document-matrix repetition reaches every format, native output,
exact request-size boundary, draft/retry and precisely asserted malformed render
fallback too. It fails only on the same #5818 mixed-case page count; no unexpected
browser diagnostics or cleanup failure occurs. The new renderer preserves the
single-point limitation #5814: the native 1,000×600 PNG has only 16 red legend
pixels at y=588, with no red observation glyph in the plot. No fix/closure is
claimed for that existing product finding.

Additional full review (round 4) completed on 2026-11-08 at 02:12 UTC with eleven
inline findings (review `5816092162`). Corrections strengthen exact allergy-row
cardinality; explicit physical-suite binding; secondary-patient audit draining;
alert-scoped empty-upload feedback and failure-before-skip classification;
early forwarding/consultation prerequisites; independent forwarding cleanup
with provider retention; unconfirmed-versus-confirmed BC provider insertion;
Contact mutation/exclusivity metadata; and preservation of primary plus shutdown
errors. Focused controls cover late/persistent audits, lost insert acknowledgment,
changed ownership, independent cleanup failures, missing prerequisites after
prior findings, and real subprocess health/shutdown failures. All 21 native
report-evidence tests and 7 shutdown tests pass. ON allergy, shared-contact and
forwarding retakes pass; BC document retake reaches all cases and fails only on
#5818. Final qualified Node, remaining retakes, commit and individual review-thread
replies/resolutions are still pending.

Round-4 verification now includes a complete qualified Ubuntu 26.04/Node 22.22.1
run: **7,416/7,416 cases in 522 files**, zero failures/skips/cancellations/TODO,
unchanged test-source digests, with the committed inventory regenerated from
that report. The full Ubuntu Python CI suite passes **354 tests**. All thirteen
GitHub workflows also pass at prior committed head `3f2373bc9a1`; this does not
qualify the still-uncommitted review corrections or replace final-head CI.
The stronger HL7 Inbox row-set, unrelated-patient exclusion and clinical/source
read-isolation assertions reach completion on ON and BC, failing only the four
#5819/#5820 comparisons. One BC repetition encountered local asset-loading errors
during schedule return and remains a failure; the settled-handoff retake reaches
all stages with no unexpected browser diagnostics and clean teardown. ON lookup
preflight and all positive lifecycle phases pass; its two known product failures
remain fatal, with exact cache/SQL restoration verified. Both environment-only
prerequisites also report SKIP before database credentials are required, rather
than misclassifying absence as a database failure.

The remaining round-4 installed retakes are complete: BC payment-boundary
workflow passes, ON lookup reaches all positive phases and retains both known
product failures with exact native cache/SQL restoration, and the tightened
ON/BC document matrix fails only on #5818. Independent post-run audits show
zero marker-owned patients/documents/providers in the checked containers. The
settled BC HL7 handoff reaches original review, all version/history/chart reads,
control isolation, exact replay and teardown, with only the four #5819/#5820
comparisons failing. These failures remain release blockers, not waiver pins.
Review-thread replies/resolutions and CI for the next committed head follow
this verified source checkpoint.

Published checkpoint `2f91cab8f35` has now passed **all thirteen GitHub workflows**.
All 31 CodeRabbit inline threads from the four completed full reviews were
individually reconciled. A later Semgrep inline finding identified a dynamic
HL7 link regex; the next checkpoint replaces it with fixed, exact parameter
parsing rather than suppressing the finding. Its reply/resolution remains pending
publication and validation. The PR description reflects the committed checkpoint;
it continues to declare pending coverage and product failures explicitly.

Additional native coverage for #5809 and #5810 passes all reached stages on both
installed snapshot30 provinces. The eForm check retains the standalone check and
uses six actual deployed PDFs, not local browser printing. Initial fixture
attempts exposed content-extent sizing, native named-window reuse, save-as-new-
revision semantics, and the required informed interstitial approval. Those attempts
remain failed/unreached evidence, not product findings or successful runs. The
qualified fixture has a visible full-page extent and asserts the actual topology,
exact revision/approval behavior, independent geometry and visible ink. It also
recovers generated PDFs by marker title after writes drain. The earlier unmarked
development PDFs were separately identified by exact synthetic title/content,
creation window and immutable hashes, then retired: 14 ON and 11 BC, with zero
renderer files remaining at that recovery checkpoint. No unrelated file was
removed. See [the deployed eForm inventory](eform-deployed-page-exclusion-inventory.md).

The stale tickler check holds real successful server responses without fabricated
rows, covers four result-set transitions and late old responses, and verifies
exact selected POST IDs and one status-history record per intended mutation.
The first installed attempt reached all four race phases but failed on a harness
NULL representation assumption; the corrected repetition completes both provinces
with no unexpected browser diagnostics and clean teardown. The new eForm, renderer
file-ownership and response-barrier controls pass **22 focused tests**. A complete
Ubuntu Node checkpoint passed **7,432 cases in 524 files** before the six extra
renderer-file refusal controls were added; the new full 525-file run is pending.
Sixteen other requested workflow additions, umbrella acceptance, native database
scope, vendor prerequisites and full repeated final-head qualification remain open.

The next full qualified Ubuntu 26.04/Node 22.22.1 execution passes **7,438/7,438
cases in 525 files**, zero failures/skips/cancellations/TODO and unchanged test
source digests; the inventory is regenerated from that complete report. All 68
manifest controls and the 22 focused new controls pass. The fixed HL7 parameter
parser retakes reach all stages on both provinces, including exact replay and
teardown; only the same four #5819/#5820 product comparisons fail, with no new
browser diagnostic or cleanup failure. No dynamic-regex suppression was added.

Final fixture review also strengthens numeric allocation across legacy fdid
references (including numeric text), refuses changed tickler ownership before
cleanup, checks unexpected eForm children on the owned patient, and verifies that
late tickler AJAX processing has actually completed before inspecting the DOM.
The latter observes the native xhr event and the following browser task; response
headers alone are insufficient evidence of a completed stale draw. The installed
retake and temporary INFO-logging restoration follow this source checkpoint.

The settled stale-tickler retake completes both provinces after the native xhr
handler and following task have run. Independent SQL/files audits show zero owned
patients, tasks, eForms or renderer PDFs; package byte verification is clean and
`NRestarts=0`. Both temporary INFO overrides are removed, their process environments
are verified restored, and installed health checks pass after restart. Checkpoint
`78c0617d3f4` publishes this work. All 32 then-existing review threads (31 CodeRabbit
plus the Semgrep finding) are resolved. A fifth full CodeRabbit review was
requested at 03:36 UTC and accepted; completion remains pending.

The OAuth web-callback addition reaches native exact/plain-query redirects,
provider/scope-bound access, Deny and all three callback-boundary refusals on ON
and BC. The encoded query variant reproducibly returns 500 (#5823); it remains
fatal with later stages explicitly unreached. Ten focused receiver, collision,
redaction and independent RFC-signing controls pass. A 7,442-case/526-file Ubuntu
Node checkpoint passed before the additional collision control and target merge;
its full replacement is pending. See [the callback inventory](oauth-web-callback-inventory.md).

The latest release target `e0601ced004c38040e6ef293e4b3f24fc43826d3`
(Inbox document description/type, #5689) is merged as `41efe5a113eee492f169b3704666dd91330ed80b`.
Its navigation fixtures, stored-document type constant and legitimate retirement
of finding 348 are preserved. New full Node/CI and installed-package qualification
must bind to that target; existing installed snapshot30 evidence above remains
bound to source `52b5b9c80752ea3d3732ee9e92bb98c6a3cd69ed` through target
`d9f0a9551784`. No newer package receipt is claimed. Fifteen other workflow
requests, native database scope, umbrella/vendor acceptance and full repeated
qualification remain open.

The replacement qualified Ubuntu Node execution after the target merge passes
**7,461/7,461 cases in 526 files**, with zero failures/skips/cancellations/TODO
and unchanged test-source digests; its inventory is regenerated from that report.
The added namespace-collision control proves an existing OAuth client is neither
adopted nor deleted. Final ON/BC callback retakes reach all independent cases and
clean teardown, with only #5823's encoded-query initiation failing. Their exact
source digest is retained; the refused encoding variant's later stages remain
unreached. QBE audit testing next uses a separately owned temporary INFO override;
its restoration and full latest-target installed qualification remain pending.

The fifth requested CodeRabbit full review finished on 2026-11-08 at 03:52 UTC
for `78c0617d3f4` with no new inline findings. All 32 then-existing threads remain
resolved. Its docstring advisory is still inconclusive (26.77% against an 80%
threshold), with unsupported/file-limit skips; it is not claimed as a passed
check or as a full independent review of later scope. Final documentation and
scope review remain required.

#5811's installed ON/BC retakes pass all seven budget/timeout/recovery stages,
including a 600,000-character streamed value across reader chunks and genuine
15-second database execution observed under `carlos_report`. Audit receipts are
bound to the application class, INFO severity, exact provider/hash/length,
outcome and visible row count; foreign, substituted or duplicate events cannot
stand in. Both existing QBE validation/escaping/history/favourite workflows also
pass. Twenty table/audit controls (18 rejection and two positive) pass. Full Ubuntu Python
CI after the target merge passes **354 tests**. The complete qualified Ubuntu
Node run passes **7,481/7,481 cases in 527 files**, zero failures/skips/cancellations/
TODO and unchanged test-source digests; the inventory is regenerated from it.
See [the QBE budget inventory](query-by-example-budgets-inventory.md).

The application WAR compiles/packages successfully from latest target
`e0601ced004c38040e6ef293e4b3f24fc43826d3` (4,677 production and 2,621 test sources;
Java execution is skipped by this build command). A local snapshot31 Debian pair
is being assembled with the complete renderer and pinned DrugRef input. No newer
installed-package result is claimed until installation and retakes complete.
Fourteen other requested workflow additions plus umbrella/native database/vendor
and repeated full qualification remain open.

Snapshot31 packages are built from source `e0601ced004c38040e6ef293e4b3f24fc43826d3`:
`carlos-emr_2026.09.0~snapshot31_amd64.deb` SHA-256
`2b04bbe07c3ea46fd59f6c90aaeffd7c221054ff211207419a2e3691df526951` and
`carlos-emr-drugref_2026.09.0~snapshot31_all.deb` SHA-256
`e0b45d149824e3b5065cadeb21ec30fe7790d2003f0433b1ce462330a3bf4254`.
Both include the complete pinned renderer/DrugRef inputs; no release component is
skipped. Installation and new package-qualified retakes follow. Independent
post-QBE audits show zero owned patients, extension/history rows or active
reporting queries in both containers. All 68 manifest controls pass too.

Snapshot31 upgrades complete in both Ubuntu 26.04 provincial containers. Exact
configuration hashes are preserved; installed health, package byte verification
and `NRestarts=0` pass. Temporary QBE INFO overrides are absent from the running
process environments. These are installation checks, not a claim of full native
acceptance on the new package.

The CodeQL diagnostic-assertion finding is addressed in `7be3aed6c4c` using an
exact expected redacted message; all five callback controls pass. All 33 current
inline threads are resolved. A sixth CodeRabbit full review requested at 04:46
UTC stopped when that fix changed the head; it is not counted as complete. Five
full reviews remain complete, and a fresh final-scope review is still required.

The new appointment recovery profile and workflows are being qualified. Initial
lifecycle-freeze attempts did not stop the owner's heartbeat, and the independent
unchanged-heartbeat assertion correctly failed. Those failed/unreached receipts
are retained. The replacement observes a real DevTools JavaScript pause and
uses rendered duration controls for native refreshes rather than relying on
background-tab timer cadence. Seven expiry-oracle controls and the affected
manifest/profile controls pass (87 total focused cases); successful final native
outcomes remain required.

The replacement Ubuntu Node execution passes **7,488/7,488 cases in 528
files**, with no failures, skips, cancellations or TODOs and unchanged test-file
digests. Its case inventory is regenerated. All 68 manifest controls pass; the
seven expiry-oracle and five OAuth callback controls also pass.

The final appointment workflows pass every native stage in both snapshot31
containers: abandoned overlap expires after about 20.6 seconds, the live control
keeps its identity and renews, the stale owner cannot steal/release the successor,
and the non-admin successor books the correct patient/slot. A monitor left by
form submission is retired by the supported native expiry, not an invented
immediate-release contract. Cut recovery passes exact archive/audit, cancellation,
invalid duration/no write, corrected single retry, repeated Paste without
autosave, browser interruption and Copy snapshots after edit/delete. The
interrupted Cut remains explicitly incomplete with its archive; no recovery
policy or automatic undo is invented. Source SHA-256 values are
`cc8296b59678a92d2a3037d09c7f12cb86010a7e2c8d402b3185438708f17d8a`
(expiry) and `827f92c20843937757491c1079a92ae54f104e8e0019b8b2f744dd67207fd5d9`
(cut recovery). Both profile supervisors finish successfully, restore exact original property
bytes, and pass installed health/package verification with `NRestarts=0`.
Independent post-run audits find zero owned patients, providers, appointments,
archives, groups or monitor names. Twelve further requested workflow implementations, plus umbrella,
native-database/vendor and repeated full acceptance, remain open.

#5804's final native delivery workflow and both affected existing regressions
(`lab-recall-delegate`, `lab-linked-tickler`) pass together in ON and BC: three
passes each, no failures or skips. The delegate reads/reopens the actual sent
message and opens the correctly assigned task and exact lab; the control receives
nothing, and reads do not resend or duplicate clinical work. Fresh-sender and
MRP-default explicit-assignee cases both pass after native registration refreshes
the SQL fixture providers' application cache. Earlier cache/navigation fixture
failures remain retained as failed development evidence, not product findings.

The final source SHA-256 values are
`c257c40cdad790db3ed740f03a5a0fb98ac3fa967335e74a4c8ec29d48ead846`
(existing recall), `f2380ad2b3c681050bbb24099fee2127d94b6bc4c294e93fc865ab03d99b0833`
(delivery extension), and
`0f5efcda8eed026a062ab9e33a1d0beb88ead40a5bb100ac4e212689ea83d48d`
(profile helper). All match the retake sources. The qualified Ubuntu Node run
passes **7,497/7,497 cases in 529 files**, no failures/skips/cancellations/TODO
and unchanged test-file digests; the inventory is regenerated. Nine lab ownership
controls and all 68 manifest controls pass. The manifest caught a second
`runLast` flag; it was removed, preserving the existing unique destructive probe
ordering rather than weakening that invariant.

Both post-cleanup cache restarts complete. Independent audits show exact original
configuration hashes, passing health/package checks, `NRestarts=0`, and zero
owned patient/provider/provider-archive/message/task/lab metadata rows. The
Messenger coordinator returns to its empty baseline. Eleven further workflow
implementations plus umbrella, native-database/vendor and repeated full acceptance
remain open; no full-release pass is claimed.

The sixth complete CodeRabbit full review finishes at 06:13 UTC on
`aa1f1cf20fc4`. Its five new findings are verified and corrected: exact expiry
boundary controls in both modes; deterministic MariaDB report substitution;
OAuth client and shared Messenger mutation/exclusivity metadata; and startup
wait termination/interval cleanup. Both appointment workflows and OAuth enforce
the disposable/exclusive prerequisite before fixture writes. The latter remains
fatal only on #5823 in fresh ON/BC retakes; every other independent callback case
is reached and cleanup passes. Both appointment workflows pass again in each
province, with exact profile restoration. New source SHA-256 values are
`49323aeefd80734f81820a3addc95984ccd43309e8d6cf84264370a166068fe6`
(expiry), `e5b74f2b5f92fef40f4ceb977e8b8546be4fd6e5c65859760042d63a8e162a72`
(cut) and `79a0c94ee83b9fc2338a4229d9fa4e21b378c46438570c3ca35edbf8105cf7e8`
(OAuth). The new boundary controls reject equality at the expiry threshold, and
the shutdown controls prove signal exit, original launch-error propagation and
absence of polling after timeout. Review replies/resolution follow publication.

#5812 reaches all independent cases on both snapshot31 provinces; exactly four
semantic comparisons fail on #5824/#5825, with clean browser diagnostics. The
source digest is `8e99ffccaf2aff29f3462319d934cc41c4a57a69036516f26c10f0dbd24df459`.
The known read-only `formRourke2009` reference is supported under Aria or MyISAM;
owned writes still require InnoDB, and other nontransactional tables are refused.
Four real engine/refusal comparisons pass on each installed 16 KiB MariaDB and
the separate 32 KiB server. Each uses a private nonce-owned database, preserves
all control/legacy rows and verifies its database is removed.

The complete qualified Ubuntu Node run passes **7,504/7,504 cases in 529 files**,
with no failures/skips/cancellations/TODO and unchanged test-file digests. The
case inventory is regenerated from that exact report. All 354 Ubuntu Python
cases, 68 manifest cases and 24 focused expiry/shutdown/OAuth controls pass.
Independent final ON/BC audits confirm exact original properties, passing health
and package checks, `NRestarts=0`, zero owned patient/provider/archive/message/task/
lab rows, and the empty Messenger coordinator baseline. All 13 CI workflows pass
at the prior published `aa1f1cf20fc4`; CI for the next publication is still required.
Ten workflow implementations plus umbrella, native-database/vendor and repeated
full qualification remain pending; this remains a draft with no closure claim.

The image-backed eForm ZIP round trip passes on ON and BC alongside both existing
export/navigation regressions: three passes, no failures/skips in each province.
The native import receives the actual downloaded export after exact removal of
the source template and both assets. Stored HTML/asset bytes, chart-entered
values, saved-instance images and actual PDF text/geometry/colour regions agree
with independent expectations. Thirteen omission/corruption/render controls pass.
Source SHA-256 is `51bb899df48877ebeff664c064ffe9099b7e5f350e7c2d530f2b4946237b1ee8`.

Email consent passes all six delivery cases in each province. Current configured
behavior is explicit: non-opt-in entry requires a Yes acknowledgment; composition
snapshots consent and the current send does not recheck it. A second context
natively opts out/clears consent while the composer remains open. Validation
preserves the pending body, which the corrected native submission sends unchanged.
New composers reflect the change, and restored Opt-In sends once. Ten sender-ID
controls prevent adoption of orphan legacy references. Exceptional recovery of
the deliberately refused collision run removes only exact owned rows and restores
the original absent parent; all 184 preexisting email logs retain complete row
digests in each province, including after the successful final regression retakes.
No normal test disables constraints.

The Ontario multi-item workflow passes all three allocations, exact item/payment/
header/extension/transaction relationships, reopened balances, payment history and
details, and actual displayed/printed totals. Source SHA-256 is `224e2e161f7d823de5963aa4fbc42d6fcb3867b096305f7ca6433e869a4fce08`.
The existing single-item invoice workflow also passes completely, including the
previously reported payer-handler case, so its stale manifest note is corrected.
Both billing workflows and all three email/consent workflows pass together: five
passes, no failures/skips. BC's three email/consent workflows also pass together.
Development wrapper/identifier/label failures remain recorded as failed attempts;
the original retained physician is recovered by its exact ownership ledger, and
its retained patient is removed only after zero clinical/support/merge references.
Independent final audits and final-source qualification follow this checkpoint.

A further qualification review finds that the Node gate's initial file selection
could omit tests added during execution, despite unchanged fingerprints for the
original files. Two real CLI controls reproduce that false pass before the fix.
The full-release entry point now compares final discovery with its initial
selection; added failing or empty files make sourceChanged fatal. The unchanged
selection still qualifies, and explicit subset execution retains its existing API.
All 30 focused Node controls pass. The earlier 7,517-case/530-file and
7,527-case/531-file reports cover their exact selections only. A later run whose
test source changed during execution correctly reports incomplete and is retained
as such; it is not used as final qualification. The complete stable-source
replacement is running. All 13 CI workflows pass at `c05b5083f75`; CI for the next
publication remains required. Seven workflow implementations plus umbrella,
native-database/vendor and repeated full acceptance remain open.

The complete stable-source replacement passes **7,531/7,531 Node cases in 532
files**, with zero failures/skips/cancellations/TODO and sourceChanged=false.
Its exact case inventory is regenerated. The 30 focused controls pass again.
Final local cleanup review additionally checks captured sender/template IDs even
when their marker fields change, preserves unrelated NULL-name templates in the
full snapshot, and rejects NULL ownership fields through explicit COALESCE.
A real MariaDB comparison demonstrates that the earlier NOT expression yields
unknown for NULL, whereas the corrected predicate refuses it.

The final guard-qualified image/PDF and payment retakes pass all native stages;
ON reports three passes and BC two passes with zero failures/skips. A final
standalone email retake on each province also passes all six delivery cases after
the last identity-guard adjustment. Current source SHA-256 values are:

- `eform-image-zip-roundtrip-playwright-checks.js`: `c0b27df634a386490cb10171e1034b89a566bda195af4bb1c35a7e61a2772f8c`.

- `email-consent-transitions-playwright-checks.js`: `093dbbf995afece5586ffd8a20ec8bb3d2f0e362af4a10036d504e0ee11622a1`.

- `billing-on-multi-item-payments-playwright-checks.js`: `224e2e161f7d823de5963aa4fbc42d6fcb3867b096305f7ca6433e869a4fce08`.

Independent publication audits show exact original configuration hashes, passing
installed health/package checks, NRestarts=0, zero owned patient/provider/archive/
message/task/lab/eForm/email rows, and the empty Messenger coordinator baseline
in both provinces. All 184 original email-log row digests still match in each
database. The source release target remains e0601ced004c. Seven workflows plus
umbrella/native-database/vendor and repeated full qualification remain open.
No new expected-failure pin or full-release pass is introduced.

## RA replay and seventh-review reconciliation

The workspace is recovered. The documentation-only `2b0d556dc46` checkpoint
passes all 13 CI workflows. The target remains `e0601ced004c`; both provincial
containers still use the actual snapshot31 application/DrugRef Debian packages
on Ubuntu 26.04.1, Java 25, MariaDB 11.8.6 and locked Chromium 149.0.7827.55.

#5719 now exercises original RA reimport, replay of the actual protected
settlement POST, and a subsequent signed -5.00 recovery with zero new invoicing,
its settlement and reimport. Complete original/rejected/control financial
snapshots, source details/files, report totals and the independent net balance
are checked; private payment tables are not confused with OHIP remittance state.
Captured IDs remain guarded when filenames change; real row-query controls
reject NULL/foreign claim/provider ownership. Unacknowledged dispatched writes
retain fixtures, while failed session/CSRF preflights do not falsely mark a write.

The six outstanding code findings from CodeRabbit review 5817084151 are
addressed: literal payment parsers, deferred repeated SIGTERM/SIGINT handling
through descriptor/class restoration and the final restart, installed/effective
Java-properties notice parsing, an exact English OCR-data preflight and a
deterministic virtual-clock late-audit control. The properties parser agrees
with actual JDK 25 Properties.load(InputStream) on five independent vectors.
Both installed error-adapter runs pass and report exact descriptor restoration,
test-class removal and a final service restart. Lab pagination and measurement
geometry retakes pass in ON/BC; all three payment stages pass in ON.

The complete replacement Node run passes **7,568/7,568 cases in 536 files**, with
zero failures/skips/cancellations/TODO and unchanged test-source digests. The
inventory is regenerated from that exact report. All **356 Ubuntu Python tests**
pass, including real repeated cancellation signals during setup/restoration/
restart and setup-copy failure. All 43 final focused RA/payment/probe controls
pass after the last navigation change.

An installed RA repetition fails correctly on aborted script/style requests;
another fails on aborted fetch/font requests. Financial assertions alone do not
qualify either attempt. The harness now waits for new iframe navigation, complete
upload documents and summary/report assets before submission/closure. No browser
diagnostic is filtered or waived. Final repeated native results and independent
publication audits accompany this source checkpoint.

Six workflow implementations remain: #5715, #5717, #5731, #5801, #5802 and #5803.
SMS-feature availability, umbrella/native-database/vendor acceptance and repeated
full qualification remain open. Existing product findings stay fatal; no new
expected-failure pin or full-release pass is introduced.

The final navigation version passes **two consecutive installed repetitions** of
both `billing-on-ra-import` and `billing-on-ra-replay`, with zero failures/skips
or unexpected browser diagnostics. Final replay source SHA-256 is
`38caa81f437e33c73e5b9098cb99bbf033cc3d329ab0984f701083fcec68100d`;
the shared import source is
`f11cf801dcb2818ac9b530e2d3876eea85741cd45047a824ce1e515333ae5c36`.
Independent ON/BC publication audits pass original configuration hashes,
installed health and package verification, NRestarts=0, zero owned patient/
provider/archive/message/task/lab rows and the empty Messenger coordinator.
All 184 original email-log rows retain every column digest in both provinces.

## Mixed incoming PDFs and eighth-review reconciliation

#5731 now has a native scanned/mixed-PDF workflow and an independent raster/
geometry/annotation oracle. The original extraction/collision/cancellation/
permission/stale/capacity/filing check remains intact. Both pass together on
installed ON and BC. The independent audit initially finds an old deleted-page
recycle omission despite passing browser steps; that attempt is not qualified.
After the teardown correction, the final paired runs and independent audits pass
with zero owned queue, recycle or cache files. Original configuration hashes,
package verification, installed health, NRestarts=0, all 184 legacy email-log row
digests and the empty Messenger coordinator baseline remain intact.

Native PNG bytes are captured losslessly from the exact UI request. Cache writes
are drained; unknown responses retain fixtures, changed bytes refuse deletion,
and preexisting cache entries remain untouched. Twenty-eight focused incoming
PDF/transport/finalization controls pass. The first complete Node attempt fails
on the required exclusive-run declaration for the new check; that declaration is
corrected rather than bypassing the manifest constraint.

CodeRabbit full review 5817664583 completes with fourteen inline findings and one
outside the diff. The fixes preserve the primary lookup failure on an uncertain
write, bound OAuth timing misses without hiding data/authentication/server errors,
await dependent financial cleanup and all save branches, observe Back requests
until closure, reuse the guarded native provider-profile save, select the
application's consecutive confidentiality-statement version, support an explicitly
absent standalone override, improve asset diagnostics, share the engine policy,
match rejection causes, resolve linked temporary roots and select a deterministic
JUnit report. QBE evidence now correctly states eighteen rejection controls and
two positive controls. Publication and individual thread reconciliation follow
successful native retakes.

The complete stable-source replacement passes **7,595/7,595 Node cases in 540
files**, with zero failures/skips/cancellations/TODO and sourceChanged=false.
Its exact case inventory is regenerated. All **356 Ubuntu Python tests** pass.
The 124 focused Node checks pass; six renderer-fixture checks also pass with a
symlinked TMPDIR. All thirteen CI workflows pass at published baseline
`e9b964e855d`; the next head requires fresh CI.

Five workflow implementations remain: #5715, #5717, #5801, #5802 and #5803.
SMS-feature availability and umbrella/native-database/vendor/repeated full
qualification remain open. Existing product comparisons remain fatal, with no
new expected-failure pin or issue-closure claim.

The eighth-review native retakes pass five normal checks on ON and four on BC,
plus `appointment-cut-recovery` under its real `appointment-lock-expiry` profile
on each province. This includes exact-second OAuth expiry, default lab notice,
the shared guarded profile save/real recall delivery, native image ZIP reimport,
all three payment stages on ON and Back/Paste with zero submitted saves. Both
temporary appointment configurations are restored exactly and all post-audits
pass. A separate ON disposable profile activates confidentiality statement v2:
all sixty clinical results and the actual new notice pass across eight PDF pages;
the original configuration is restored byte-for-byte and the service restarted.
Its final health/package/configuration/fixture/legacy-email audit also passes.

### Ninth full review reconciliation

The ninth full CodeRabbit review of `55b39ac685af` raised three valid findings.
Preview capacity cleanup now preserves its original assertion together with route
removal and upstream forwarding failures, and drains forwarding even when removal
fails. Two independent fault controls exercise these combined failures. The
mixed-PDF manifest names all three preference rows for the independent residue
audit. Query-by-Example now requires the shared `CARLOS_DISPOSABLE_VM=true`
convention; four subprocess controls prove that `true` reaches its next prerequisite
and `1`, `false` and an empty value refuse before database/browser mutation.

The immutable review-fix qualification checkout passes **7,601/7,601 Node cases
in 540 files**, with no failed, skipped, cancelled or TODO case and no changed
test source. The installed ON original extraction and mixed-PDF pair passes again
through Chromium 149.0.7827.55 against the same snapshot-31 Debian packages.
All 13 CI workflows pass on the preceding `55b39ac685af` checkpoint; the next
commit requires its own CI result. The five unimplemented workflows and original
full/repeated/vendor acceptance obligations above remain pending.


### Growth and populated-editor session-loss checkpoint

Two further workflow implementations address #5717 and #5802. The complete stable
Ubuntu 26.04 Node run passes **7,645/7,645 cases in 543 files**, with zero failed,
skipped, cancelled or TODO cases and no changed test source. The exact case
inventory is regenerated from that report. The original Rourke/Growth defaults,
original finding selectors and existing heartbeat check remain registered.

Growth runs both sexes with fixed birth dates, irregular and boundary inputs,
repeated module observations and an unrelated plausible control. The independent
WHO reference oracle verifies every actual vector point, the existing
duplicate-source representation and explicit median relationships. It compares
more than 330,000 reference pixels per first page, plus the older-child page,
with zero unexplained pixel differences; the worst reached point error is
1.14 points against a 2-point tolerance. Both installed provincial runs pass.
The BC suite entry completes in 263.3 seconds with zero failures/skips.
Saved Rourke/Growth histories and all four module observations stay unchanged
through graph/print reads; the original native setup's acknowledged history
count is preserved rather than assuming one row.

The session workflow establishes native saved note/eForm baselines and an exact
acknowledged private D1, holds every native owned autosave before typing N2,
and accounts for an earlier D1 request explicitly. The saved eForm's genuine
Save request is bound to its actual instance and action identity. A separate
context logs out, after which actual application-origin refusals preserve
all clinical snapshots. The release's visible generic 403 error is asserted
truthfully. Native heartbeat closure, original-context same-account recovery,
other-account DOM/session/local/IndexedDB isolation and exact D1 persistence
are required. The final strict-release suites complete all six stages and cleanup in
227.6 seconds on ON and 220.8 seconds on BC, with no failure or skip. All saved,
draft and pending clinical values and the patient marker are excluded from both
raw/visible unauthenticated response content and login/error pages.

Request supervision refuses cancelled clients before dispatch, drains real
upstream operations, preserves primary diagnostics and keeps a private durable
journal for uncertain writes. It resumes supervision for authenticated recovery
without leaving a write-aborting route active. Independent real-HTTP/fault
controls cover selective release, phase handoff, cancellation, ownership, lost
acknowledgement, journal failure and closed-page delivery. Two conservative
parent/journal receipts left by earlier failed harness attempts were reconciled
only after proving every request was aborted before dispatch and every owned
clinical child was already absent; those attempts are not qualification evidence.

Independent final ON/BC audits pass configuration/package integrity, health,
NRestarts=0, zero owned clinical/account fixtures, the empty Messenger coordinator
and every original column digest in all 184 legacy email-log rows. All 13 CI
workflows pass on the preceding published `09b6395b881` checkpoint. The new
checkpoint still requires its own CI and CodeRabbit review. Three workflow
implementations remain: #5715 populated merge/unmerge, #5801 visual eForm widgets
and #5803 qualified/qualitative labs; original umbrella/vendor/native-database
and repeated full qualification obligations remain open.
