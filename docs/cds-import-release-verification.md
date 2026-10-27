# CDS import release verification

Target: `release/2026.08`. Application: [CARLOS #5347](https://github.com/carlos-emr/carlos/pull/5347).
Companions: [carlos-ctl #15](https://github.com/carlos-emr/carlos-ctl/pull/15) and
[carlos-podman #22](https://github.com/carlos-emr/carlos-podman/pull/22).

This document identifies the verified build foundation and the acceptance criteria
for the installed and resource gates. The application PR's [verification record](https://github.com/carlos-emr/carlos/pull/5347#issuecomment-6451727760)
records their executed results, exact artifacts and current CI/review outcomes.
A criterion below is not, by itself, a claim that a particular execution passed.

The delivery covers import work associated with #3973, #3946, #3959, #3968 and
#3979. Export work is outside this delivery. The design, contract decisions and
implementation sequence are in the [implementation plan](cds-import-implementation-plan.md).

## Preserved foundation and final candidate

The preserved intermediate WAR SHA256 is `9135fb29e22b43378961651e92ce8b4a2c758c0dc2430e610ee6fbd8878d16f5`, built from production source
`33e32f870edabd2feb0068458e75641ed4b83915`. It contains 5916 production classes, 433 production
JARs and 74 migration SQL files. Every packaged class equals the compiler output
used for the foundation checks. Every packaged migration equals its source;
every dependency JAR equals the verified runtime classpath. The archive excludes
H2, JUnit, test bootstrap classes, workload validators and session-expiry agents.
The packaged API-2 browser script equals its checked source.

That intermediate's ruleset fingerprint is `32a733b334ec4c8fc2f1e435a123b34b9c946a82984b4e44835abe654e910adb` in its tested compiler output
and exact WAR. These preserved results do not accept its successor. The final
WAR, source head, fingerprint and executed results are identified in the
linked PR verification record. Each installed runtime must independently attest
its selected candidate's fingerprint,
time zone and locale categories before acceptance. The native test deployment
uses America/Toronto and en-CA in the default, FORMAT and DISPLAY categories.
A run's command/configuration ruleset digest is a separate composite value;
it must not be overwritten to match a raw runtime fingerprint.

| Preserved intermediate foundation | Executed evidence |
| --- | --- |
| Affected application regression | 1,461 cases in all 124 required suites; zero failures, errors or skips. Required XML reports reconcile their counters to individual cases. |
| Browser state contracts | 133 affected Node cases; zero failures or skips. Covers known accepted jobs when status reads fail, truthful job presentation and one-time navigation without polling scrolls. |
| Physical database contracts | 73 cases in all 12 required suites against a fresh owned MariaDB; zero skips. Includes native storage, interrupted preparation/recovery, unknown acknowledgments, review acceptance, provider byte identity and physical coordinator controls. |
| HRM maintenance safety | Eleven physical cases include actual pool abandonment while an independent ordinary lease continues blocking maintenance; atomic publication, rollback and unknown-commit handling remain distinct. |
| Ownership control cost | Twelve forced control reads issue exactly twelve SELECTs, with no ordinary-pool borrows or retained InnoDB transaction. Actual cancellation, epoch/admission changes, malformed control values, physical connection loss and competing ownership are checked. |
| Active attempt budgets | Bounded reads check the local attempt budget; the shared owner probe coalesces only database reads. Clinical callbacks use a local budget without owner SQL or nested transactions. Known rollback, prior commits and unknown acknowledgment retain distinct outcomes. |
| Specification coverage | Pinned inventories classify 407 leaf/attribute paths, 45 choices with 130 arms, and 150 compositors. Explicit dispositions, actual-parser fixtures and fail-closed crosswalk checks detect unclassified or changed contracts. |
| Forward migration | The retained 67-to-68-history upgrade proof preserves the complete application-data dump, prior history/checksums and import controls. Repeated migration has zero pending changes. Published migrations are not edited. |
| Host command pairing | Source pairing: 1,674 passed, 73 conditional database skips and 218 subtests. The pinned runtime files are unchanged except for the permitted CARLOS data overlay. Physical installed gates below provide separate non-skipping evidence. |
| Container companion | 824 unit cases passed with one pre-existing skip, plus 406 hermetic end-to-end checks, lint, typing, provisioning, migration and launcher checks. |

These scopes overlap and must not be added into a purported disjoint test total.
Conditional companion unit skips do not replace the required physical database
and installed-engine gates. The PR records the separate full application CI
results, including their conditional skips.

The corrected final candidate requires a fresh build and all 127 explicitly
selected regression suites, all 12 physical native suites and the seven
permanent browser Node suites. Exact case counts must be reconciled from the
fresh reports; earlier counts above are historical. The added coverage includes
coordinator cleanup, reporting after clinical admission is released, optional
demographic projections, actual CLI pool state/rollback/replacement, and native
date/time storage. Every installed gate below must bind the final artifact
before acceptance. A preliminary compile or focused test pass does not satisfy
these final gates.

## Installed and browser acceptance criteria

Every executed gate must bind the exact WAR, installed companion, immutable
source, registered namespace/profile, command, operator and clinical scope before
first acceptance. Accepted source bytes, ruleset/options hashes, checkpoints and
control history are retained. An uncertain acceptance is reconciled through its
original submission identity; it is not replaced by a fresh submission.

| Gate | Required proof |
| --- | --- |
| Installed host | Genuine installed command: preview, ordinary import, verified no-op, full categories, laboratory offset, alphanumeric provider, directory/ZIP, submission lookup, protected report hashes, course copies and explicit refusal controls. |
| Interrupted host | SIGTERM after a native commit, then a PID-fenced hard kill after a further commit on the same resumed run. Final resume reconciles exactly one patient, 256 notes and 514 checkpoints, complete reports and verified release. |
| Installed container | Actual service user, 512 MiB heap, 768 MiB cgroup, one CPU, read-only root/source/configuration and no capabilities. Same accepted run survives container replacement; separate ordinary/full-category/no-op cases reconcile native data and all four reports. |
| Retained browser recovery | Native authentication and authorized recovery remain usable under a retained maintenance window. A stopped proven-unwritten attempt is handled through supported controls; original source and history remain intact. A sealed incomplete cancellation remains incomplete rather than being relabelled clean. |
| Normal browser and corpus | Real upload/acceptance, preview then execute from the same immutable upload, exact retries, no-op, ZIP and ordinary-doctor refusal. Native category/source reconciliation and all four protected report HTTP bodies must match the selected physical files. |
| Web job experience | Separate upload and processing states; a canonical owned run bookmark; readable phases and counts with truthful unknown totals. Preview, warnings, review, incomplete work, report failure, cleanup and maintenance are distinct. Technical JSON is supplementary. |
| Browser interruption | Real proxy timeout after acceptance, tab closure and expiration of the selected native HTTP session. Reauthentication returns to the same accepted run without resubmission. The 514-checkpoint result and separate approved synthetic-course copies reconcile completely. |
| Browser compatibility | Genuine cached historical JavaScript against the new API and new JavaScript against the genuine historical API fail visibly before mutation. Ordinary multipart upload remains functional. Owner/nonowner/anonymous/logout report checks cover all four files. |
| Large browser ingress | More than 64 MiB through actual Nginx/ModSecurity using declared and streamed HTTP framing, bounded chunks, exact payload/journal hashes, disconnect/resume, CSRF/oversize controls and measured server memory. This upload-only gate does not claim a 20 GiB browser clinical import. |
| Final review | Current-head application/companion CI, valid review findings, base compatibility, DCO sign-offs and the PR verification record are checked before readiness. |

The user may leave the page after the server accepts the job. Closing the page
during transfer requires resuming that upload. Upload bytes reaching 100%, HTTP
acceptance, clinical work counts and complete report/cleanup/release are separate
observations. Source verification can continue while clinical item counts remain
unchanged; the UI must not invent a worker heartbeat or an ETA.

## Permanent resource contracts

The CDS native and installed resource tests remain in CI. The resource matrix
retains all five profiles: `scaling`, `aggregate20`, `zip64`, `disk-reserve` and
`disk-enospc`. Direct Java helpers and proof fixtures are included in workflow
path filters. Neither memory/query budgets nor failure assertions are relaxed
to obtain a green result.

The profiles enforce a 512 MiB JVM heap, a 1 GiB process-RSS budget, a 1,536 MiB
cgroup and two CPUs. These differ from the separately tested 768 MiB deployment
container. Kernel/process peaks and cgroup OOM events are measured independently;
heap size alone is not evidence of total memory use.

The scaling matrix increases patient counts, clinical occurrences and payload
sizes. Complete 8, 32 and 64 MiB inline workloads are validated by the actual
production parser under a separate 64 MiB heap before submission. The actual
20 GiB and ZIP64 profiles reconcile physical native bytes, distinct digests,
all four reports, checkpoint counts, zero holds and maintenance release.

| Earlier measured CI workload | Result and timing |
| --- | --- |
| 20 GiB, source `0c5491cd7a` | 320 patients, 320 distinct PDFs, 21,474,984,320 native bytes and 1,280 checkpoints. Import 849.489 seconds; independent verification 99.560 seconds; total 949.049 seconds. Process RSS 543,014,912 bytes; zero OOM events. All native/report/release checks passed. |
| Earlier 20 GiB, source `211f4beb78` | Same workload shape: import 577.856 seconds plus verification 60.660 seconds. The newer run includes additional database instrumentation; no controlled comparison attributes the timing difference. |
| Single chart, source `0c5491cd7a` | 2,048 notes and 2,048 laboratory results, 8,194 checkpoints; 1,559.24-second import and 561,696,768-byte process RSS. Native, report, release and query-budget checks passed. |
| ZIP64, source `0c5491cd7a` | One 5,368,716,133-byte native PDF, four checkpoints and exact native/report/release reconciliation. The 7,244/2,000 query-budget failure exposed repeated transactional control polling; the candidate refactors that polling and must meet the unchanged budget. |

These are artifact-specific historical measurements, not final-candidate timing
promises. They exclude transfer from the operator's computer and test setup.
Structured clinical work can have a different cost from large attachments.
Current-candidate resource outcomes are recorded in the PR verification record.

A query-budget failure remains a failure. The scaling collector may continue
only after all clinical, file, report, hold, release, RSS and OOM proofs pass;
it retains each failed budget and exits nonzero overall. A product failure,
unknown outcome, held chart, missing proof or resource violation stops the gate.

Disk tests use the real service UID and verified ancestry/traversal before first
submission. Reserve refusal and actual traced ENOSPC are distinct cases. The
ENOSPC fixture's one-byte reserve is the minimum valid configuration, frozen
before submission; the production reserve default remains unchanged. Failed
attempts retain bounded protected evidence and require native ledger/source
reconciliation before their disposable runtime is retired.

## Recovery and release boundaries

The maintenance owner retains the barrier by default after failure. Cancellation
and delivery of a failed run's reports do not prove that ordinary clinical access
has reopened. A later successful verified owner can reopen the barrier when no
chart holds remain. A historical completed run's release receipt describes that
run; it is not a live statement about a later failed attempt's global window.
Installed validation includes an explicit unchanged-source no-op after known
refusal controls, with zero new clinical mutations and actual reopening proof.
No SQL override clears the barrier.

Failure evidence excludes exception messages, SQL, source values and physical
paths. Authorized operators can inspect bounded exception types, SQL state/vendor
codes and code locations, alongside the event, error, item and completion files.
A failed or uncertain report publication cannot become clean completion. Prior
selected report generations remain immutable until a verified replacement is
selected.

Cooperative deadlines do not promise interruption of a blocked operating-system
filesystem call. The [JDBC timeout contract](cds-import-jdbc-timeouts.md), process
isolation, durable fencing and supported recovery address their separate failure
boundaries; no timer blindly retries clinical work.

## Release admission

The compatible host command is carlos-ctl 1.3.0, source
`7a93c482224f7bb8319002492d0bae9ab4d72c97`. The application dependency floor and
release pin require it. The development package is 1.3.0~dev1; publishing the
compatible 1.3.0 release asset remains a maintainer prerequisite after merge.
The container companion is `ed907600768ccfec3f5b07a92a2b5243aaaaa632`,
version 2.0.0-beta3. No release tag is created by this work.

Mutation admission remains default-off. Before enabling a deployment, follow the
[operator guide](cds-import-operator-guide.md),
[maintenance admission contract](cds-maintenance-admission.md) and implementation
plan, including compatible readers, validated migrations, backup/recovery
preparation and published companion artifacts. PR verification does not itself
enable production imports or certify OntarioMD conformance.
