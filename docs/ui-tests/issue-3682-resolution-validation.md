# Issue 3682 fixes and validation

This follow-up addresses the remaining application and test findings from
[issue 3682](https://github.com/carlos-emr/carlos/issues/3682). The earlier
[test expansion](release-2026.08-workflow-validation.md) is historical evidence;
the focused PRs below carry the follow-up fixes. No PR is merged by this validation.

All CARLOS branches start at release/2026.08 `71d4528eddd9e10e6b8027fd83ccd59831c89e34`
and target that release. Develop was fetched only to check migration allocation.

| PR | Scope | Validation |
|---|---|---|
| [3684](https://github.com/carlos-emr/carlos/pull/3684) | Decode JavaScript URL literals in the route audit | Original anonymous-route finding was malformed URL generation; 122 correctly generated anonymous routes refuse access without an authentication-code change |
| [3685](https://github.com/carlos-emr/carlos/pull/3685) | Jasper-compatible labels and explicit PDF failures | Earlier installed package passes all six offered PDF checks; current patient-access/null-preference/fallback follow-ups pass 72 action and ten PDF/template cases, with installed-package retest pending |
| [3686](https://github.com/carlos-emr/carlos/pull/3686) | HRM status filtering and matching counts | Live All/New/Acknowledged/Filed partition and count checks pass; legacy A/F are aliases, not disjoint buckets |
| [3687](https://github.com/carlos-emr/carlos/pull/3687) | Episode validation/access and scratchpad ownership | Five episode lifecycle steps and four cross-patient/provider/owner checks pass |
| [3688](https://github.com/carlos-emr/carlos/pull/3688) | Internal contact picker, reciprocal identity, preflight validation and stale recent patients | Final package retest pending |
| [3689](https://github.com/carlos-emr/carlos/pull/3689) | Printer/signature/document-description preferences | Final package retest pending, including both printer-feature settings |
| [3690](https://github.com/carlos-emr/carlos/pull/3690) | Explicit inactive-drug status and visible lookup failure | Actual inactive DIN returns its stored calendar date; malformed response shows warning and recovery, without saving a prescription |
| [3691](https://github.com/carlos-emr/carlos/pull/3691) | Admin/chart navigation, calculator entry/styles, Row Display and lot search | Chart module audit and repeated lot search pass; 101-item administration sweep exposed OHIP handler and duplicated AJAX-header failures; both corrected, final package retest pending |
| [3693](https://github.com/carlos-emr/carlos/pull/3693) | Shared live workflows and this evidence | 112 named checks / 103 scripts registered; 677 Node regressions pass |
| [3694](https://github.com/carlos-emr/carlos/pull/3694) | Signature identity migration | Nine real MariaDB cases and both province fresh/adopted Flyway CI jobs pass, including preservation of duplicate unassigned rows; application upgrade pending |
| [DrugRef 14](https://github.com/carlos-emr/drugref2026/pull/14) | JDBC calendar dates and explicit lookup faults | 69 tests and WAR pass at 1941e142; earlier installed-DEB calendar-date lookup passes, latest query-failure follow-up installation pending |

## Scope and interpretation

PDF Envelope's original 404 did not reproduce in the valid packaged UI flow; it
returns a complete PDF. No envelope-specific application fix is claimed.
The anonymous-access finding was a test defect, with no demonstrated disclosure.
Calculator valid-input scenarios are covered; the separate invalid-input issues
already tracked in the findings log remain outside this aggregate's scope.
The original blank lot-search observation did not reproduce; the new test now
uses the actual administration iframe and visible input, with both searches checked.

Validation uses an isolated Ubuntu 26.04 VM with 6 GiB RAM and a 2 GiB Java heap.
Host builds run only with the VM stopped. Browsers run sequentially under host and
guest memory guards. Settings are snapshot/restored; owned patient/child fixtures
are deleted and checked independently while audit history remains intact.
Episode permissions are explicitly enabled for the disposable test provider and
restored afterward. No low-privilege VM exploit claim substitutes for the Java
negative authorization cases.

## Deployment order and database policy

Review the signature write-path repair and schema migration together. Stop every
application node before the packaged migration, as required by the upgrade guide.
The migration removes only byte-identical signature duplicates for assigned providers
and rejects conflicting values before changing source data; it never selects an arbitrary
signature. Every NULL-provider row is preserved, including identical rows and NULL
signatures. Follow the migration README for backup and conflict recovery.
Version 1.0.23.1 follows this release's 1.0.23 and does not collide with develop's
1.0.24–1.0.28. Promotion into an already-upgraded develop database needs explicit
upgrade planning; do not rename a published migration or enable out-of-order
execution implicitly.

The shared workflow PR intentionally tests behavior supplied by the focused
application PRs. Validate the combined release candidate before shipping it.

## September 17 review follow-ups

The audit decoder now handles JavaScript braced Unicode escapes, including astral
characters and explicit failure for invalid code points. Four new regression cases
bring its unit file to 68 cases; the complete Node suite has 677 passing cases.

Printing head `7c139eebd1` passes [12,221 Java tests and JSP compilation](https://github.com/carlos-emr/carlos/actions/runs/35178032145)
with no failures/errors and 48 skips. This includes 72 action cases and ten
PDF/template cases. Patient-specific denials and numeric aliases are checked before
report data is read; denied callers remain denied even with malformed identifiers.
Null printer preferences and unavailable template overrides are covered. CodeRabbit
accepted the fixes. These follow-ups have not yet been installed in the validation VM.

Signature migration head `a384cbbde2` preserves every unassigned NULL-provider row.
The new mixed regression fails against the original SQL and passes after the fix;
all nine cases pass on disposable MariaDB 11.8.6 and in [both province CI jobs](https://github.com/carlos-emr/carlos/actions/runs/35181060081).
Both fresh and adopted databases migrate and validate through 1.0.23.1. The local
socket-only test server peaked at 70 MiB under a 256 MiB limit and was removed after
testing; this did not start the VM or compile Java.

DrugRef head `1941e142` passes 69 tests and WAR packaging. Query errors now propagate
instead of being cached as an empty inactive-drug list or a missing DIN; expanded
category 19 searches also check inactive status. Its full CodeRabbit review reports
no actionable findings. The CARLOS pin is updated in #3690. The earlier installed
calendar-date result does not substitute for an installed test of these later changes.

## Latest retest and environment blocker

The 101-item administration sweep completed in 584.8 seconds, with two explicit
policy skips. Nine error observations came from three screens: Age-Sex (HTTP 405)
and Visit Report/Overnight Batch (missing jQuery validation). Loading the OHIP
simulation fragment installed an extra GET handler on all persistent shell links,
overriding Age-Sex's POST. The executable regression fails on the original fragment
and passes after its removal. The eForm requests actually carry
`X-Requested-With: XMLHttpRequest, XMLHttpRequest`; the previous exact-value guard
therefore reloaded jQuery and discarded its plugins. Both fragment guards now
recognize comma-separated markers without changing security filter negotiation.

LXD's internal database timed out during VM shutdown. QEMU exited, but the daemon
continues returning stale state and transaction timeouts. Host service recovery
requires interactive authentication, so the next local build/package/VM pass is
pending. No build was started during this uncertain shutdown state. The contact and printing follow-ups pass their PR CI suites. The combined release
candidate has not yet been rebuilt or installed; the earlier 12,205-test integrated
build predates the latest changes.
