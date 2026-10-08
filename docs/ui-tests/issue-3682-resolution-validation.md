# Issue 3682 fixes and validation

This follow-up addresses the remaining application and test findings from
[issue 3682](https://github.com/carlos-emr/carlos/issues/3682). The earlier
[test expansion](release-2026.08-workflow-validation.md) is historical evidence;
the focused PRs below carry the follow-up fixes. No PR is merged by this validation.

## Final installed-package validation (2026-10-08)

Every PR listed below has since merged, #3694 included: its signature migration landed as
`common/V1.0.52__enforce_provider_signature_identity.sql`, so the `V1.0.23.1` collision noted
in the next paragraph no longer applies. Two packages were validated: the first was built from
the PR base `17c363e3` (the full pass below, which found the defects), the second from the
fix commit (the rebuilt-package retest; built as `3bb4e716`, whose tree is identical to
`f35304f5` after the commits were re-signed). The PR's later commits change only the
browser checks, their Node tests and documentation. Those checks ran from the mounted
checkout, so every run used the check code as it stands on the branch, not a packaged copy.

**Build and install.** `release/2026.08` at `17c363e3` was packaged with `dpkg-buildpackage`
in an `ubuntu:26.04` container: `carlos-emr` and `carlos-emr-drugref` 2026.09.0~snapshot26,
DrugRef from its pin, plus `carlos-ctl` 1.1.1 from its pin. The packages were installed with
the runbook's preseed (Ontario, self-signed TLS, demo data, secure seed-admin reset) into a
systemd `ubuntu:26.04` container. Install completed and `carlos-ctl check` reported every
line OK, including WAF blocking, live DrugRef and 43 successful Flyway migrations (`1.0.52`
among them). The mandatory first-login reset ran first, and every check went through `:443`
with `EXPECT_FRONT_DOOR=true`.

**3682 findings re-checked on the installed package.** These checks passed:
`anonymous-access-refused`, `demographic-labels`, `demographic-label-content`,
`export-content-patient-labels`, `demographic-relations-pdf-labels`, `inboxhub-filters`,
`scratchpad-workflow`, `surface-audit:scratch-surface`, `contact-editor`,
`provider-preferences`, `gap-provider-preferences-save-all`, `provider-signature-contact`,
`clinical-calculators`, `echart-navbar-modules`, `lot-number-search`, `measurement-history`,
`prevention-lifecycle`, `admin-report-validation`, `surface-audit:preferences-surface`,
`surface-audit:inbox-surface`, `page-health-master-record`, `page-health-echart-navbar`,
`page-health-preferences-referrals`, both `page-health-admin-panel` halves, and
`admin-index-links`. That sweep opened 107 Administration items and skipped 2 by policy;
Age-Sex, Visit, Overnight, the patient list and lot search were all clean. The three
originally reported anonymous routes answer 302 to `/logoutPage`, and `/favicon.ico`
answers 302 to the application icon.

**Failures and what they were.**

| Check | Cause | Resolution in this PR |
|---|---|---|
| `get-reject-contact-directory` | **Application defect.** A GET replay of `demographic/Contact?method=saveContact` answered 200 and renamed the owned directory contact. | Every remaining `Contact2Action` write is POST-only. `saveProContact`, which wrote with no privilege check at all, now requires `_demographic` write access, like `saveContact`. The check now also saves a professional contact through the UI and replays that save. Rebuilt package: both saves answer 405 to GET/HEAD and the rows are unchanged. The six writers with no UI caller are tracked in #4402. |
| `contact-lifecycle` | Test defect. #3985 renamed the search-result pick button from `demographic_no` to `pick_demographic`; the check still used the old name. | Locator updated, plus a Node contract test that ties the locator to the JSP. All seven steps pass. |
| `episode-lifecycle` | Fixture gap plus two stale locators. The demo `doctor` role holds `o` on `_newCasemgmt.episode`, so the module is hidden and the check timed out. With a temporary grant, the check then clicked the centre of a title that the start date overlaps, and an `<h3>` whose handler moved onto its link in `1c3d1433`. | The check now SKIPs and names the missing grant. It clicks the visible start of the title and the heading link. With the grant (fixture documented in the runbook, restored to `o` afterwards) all five steps pass. |
| `master-record-tabs` | One `net::ERR_ABORTED` on Client Lab Label, during a concurrent debugging run. | Not reproduced in five isolated runs. `demographic-labels`, which validates that PDF's bytes strictly, passes. Not claimed as a fix. |

`anonymous-access-refused` now pins `messenger/DisplayMessages`, `encounter/IncomingConsultation`
and `documentManager/ViewDocumentReport`, so they are probed even when the catalogue does not
reach them. A pinned route that answers 404 fails the run.

**Rebuilt-package retest.** After the fix, the package was rebuilt from the fix commit (`3bb4e716`, tree-identical to `f35304f5`), which carries the only application change in this PR. The installed
`Contact2Action.class` hash matched the build, and `carlos-ctl check` passed. On it,
`get-reject-contact-directory`, `contact-lifecycle`, `contact-editor`,
`provider-signature-contact`, `anonymous-access-refused`, `master-record-tabs` and
`demographic-labels` all pass. `episode-lifecycle` SKIPs without the grant and passes with it.
The doctor role's episode privileges were verified restored to `o,o`. After the review
follow-up `fc5837a9` (pinned-flag and role-semantics changes in the checks only),
`anonymous-access-refused` passed again and `episode-lifecycle` SKIPped as expected without
the grant; the with-grant episode run predates that follow-up.

**Automated tests.** `Contact2ActionUnitTest` has 71 cases, including 16 GET/HEAD dispatch cases
and the denied `saveProContact` POST. The new denied-POST case failed before the fix with an NPE,
because no privilege check existed. Together with `MutatorActionGetRejectionContractUnitTest`,
`DemographicContactCreatorUnitTest` and the contact DAO integration tests, that is 156 tests,
all passing. The Node script suite has 2,817 tests: 2,813 pass, 4 skip, none fail.

**Status after the 2026-09-24 release merge.** #3684–#3691, #3699 and DrugRef
[#14](https://github.com/carlos-emr/drugref2026/pull/14) are merged, so this branch
now carries every application fix its workflows exercise except #3694. #3694 is
still open, and its `V1.0.23.1` signature migration now collides with
`common/V1.0.23.1__widen_email_config.sql`, which #3782 added to release/2026.08;
#3694 needs a new, unused version before it can merge. The deployment-order notes
below describe the plan as written on September 17 and should be read with that
collision in mind. The workflows in this PR do not depend on the #3694 migration:
`provider-preferences` owns and restores the test provider's `providerExt` rows.

All CARLOS branches start at release/2026.08 `71d4528eddd9e10e6b8027fd83ccd59831c89e34`
and target that release. Develop was fetched only to check migration allocation.

| PR | Scope | Validation |
|---|---|---|
| [3684](https://github.com/carlos-emr/carlos/pull/3684) | Decode JavaScript URL literals in the route audit | Original anonymous-route finding was malformed URL generation; 122 correctly generated anonymous routes refuse access without an authentication-code change |
| [3685](https://github.com/carlos-emr/carlos/pull/3685) | Jasper-compatible labels and explicit PDF failures | Earlier installed package passes all six offered PDF checks; current patient-access/null-preference/fallback follow-ups pass 72 action and ten PDF/template cases, with installed-package retest pending |
| [3686](https://github.com/carlos-emr/carlos/pull/3686) | HRM status filtering and matching counts | Live All/New/Acknowledged/Filed partition and count checks pass; legacy A/F are aliases, not disjoint buckets |
| [3687](https://github.com/carlos-emr/carlos/pull/3687) | Episode validation/access and scratchpad ownership | Earlier installed package passes five episode lifecycle steps and four access checks; latest session/dispatch/existence fixes pass 37 scratchpad and 18 episode cases in CI, installed retest pending |
| [3688](https://github.com/carlos-emr/carlos/pull/3688) | Internal contact picker, reciprocal identity, preflight validation and stale recent patients | 53 contact-action cases and five recent-patient regressions pass in CI; final package retest pending |
| [3689](https://github.com/carlos-emr/carlos/pull/3689) | Printer/signature/document-description preferences | Final package retest pending, including both printer-feature settings |
| [3690](https://github.com/carlos-emr/carlos/pull/3690) | Explicit inactive-drug status and visible lookup failure | Actual inactive DIN returns its stored calendar date; malformed response shows warning and recovery, without saving a prescription |
| [3691](https://github.com/carlos-emr/carlos/pull/3691) | Admin/chart navigation, calculator entry/styles, Row Display and lot search | Chart module audit and repeated lot search pass; 101-item administration sweep exposed OHIP handler and duplicated AJAX-header failures; both corrected, final package retest pending |
| [3693](https://github.com/carlos-emr/carlos/pull/3693) | Shared live workflows and this evidence | 146 named checks / 137 scripts after the 2026-09-24 release merge (114 / 105 when #3699 merged); 945 Node script regressions pass. The release already carried an `inactive-drug-status` check from #3766, so the two were merged into one manifest entry: the release's configured-fixture lookup and four malformed-response cases, plus this PR's real Rx search-and-select step |
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
Version 1.0.23.1 followed this release's 1.0.23 and did not collide with develop's
1.0.24–1.0.28 when written; it now collides with #3782's email-configuration
migration (see the status note at the top). Promotion into an already-upgraded develop database needs explicit
upgrade planning; do not rename a published migration or enable out-of-order
execution implicitly.

The shared workflow PR intentionally tests behavior supplied by the focused
application PRs. Validate the combined release candidate before shipping it.

## September 17 review follow-ups

The audit decoder now handles JavaScript braced Unicode escapes, including astral
characters and explicit failure for invalid code points. Four new regression cases
bring its unit file to 68 cases; the complete Node suite has 695 passing cases.

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

HRM head `ca33639e03` passes [12,149 Java tests and JSP compilation](https://github.com/carlos-emr/carlos/actions/runs/35227086272)
with no failures/errors and 48 skips, including ten HRM query/count cases. All 22
Node filter regressions pass. Direct null status now selects unsigned HRM rows
in both result and count paths; empty status remains All. The matched/unmatched
count queries bind an integer sign-off value, with explicit null and binding
regressions. CodeRabbit accepted both full-review fixes and both threads are
resolved. The earlier installed status-filter checks passed; this later null-status
follow-up still requires installed validation.

Access head `2c9e4613cf` passes [12,186 Java tests and JSP compilation](https://github.com/carlos-emr/carlos/actions/runs/35187990768)
with no failures/errors and 48 skips, including 37 scratchpad and 18 episode cases.
Scratchpad dispatch rejects unsupported operations and verbs, requires a session
provider before request processing, and rejects missing text while preserving an
intentional empty-string save. Episode requests using only an ID return the same
404 for missing and inaccessible records. CodeRabbit accepted these changes; their
installed-package retest is pending.

Contacts head `a52418d5a1` passes [12,169 Java tests and JSP compilation](https://github.com/carlos-emr/carlos/actions/runs/35191083977)
with no failures/errors and 48 skips, including 53 contact-action cases and five
recent-patient regressions. Recent-patient filtering excludes soft-deleted `DE`
records before pagination while retaining legacy SQL NULL statuses and audit
history. The two new database regressions cover both cases. CodeRabbit accepted
these changes; installed contact/reciprocal/low-privilege retesting is pending.

Preferences head `d9f45d121a` passes [12,147 Java tests and JSP compilation](https://github.com/carlos-emr/carlos/actions/runs/35202354902)
with no failures/errors and 48 skips, including eight preference action/data cases.
A successful overlapping background read can no longer clear a failed
document-description write warning. The concurrency test fails against the original
helper and passes after the fix; all five request/error checks and all 695 combined
Node cases pass. Full-review follow-ups correct three locales' Unicode escapes,
close the HTML document, document the JSP contracts and cover denied signature
POSTs without DAO access. CodeRabbit accepts all seven fixes and withdrew its
additional CSRF question after verifying the existing request filter's ordering,
configuration and fail-closed path. The latest installed-package preference
workflows remain pending.

Inactive-drug endpoint head `32f70e542a` passes [12,144 Java tests and JSP compilation](https://github.com/carlos-emr/carlos/actions/runs/35233828973)
with no failures/errors and 48 skips, including five endpoint cases. Denied `_rx`
read access returns HTTP 403 before any DrugRef call, and the JSON response returns
`NONE` to prevent further rendering. JSON tests cover content type and hostile
identifier round-tripping; the method-local SpotBugs suppression documents Jackson
serialization and the JSON-only response. The UI also rejects impossible calendar
dates using month/day bounds and Gregorian leap-year rules without converting them
to browser-local instants. Ten new negative cases fail against the prior JSP; all
26 inactive-status Node cases pass after the fix, including valid month ends and
century leap-year boundaries. CodeRabbit accepted the full-review fix and its
thread is resolved. Installation of these endpoint/UI changes with DrugRef
`1941e142` remains pending.

Navigation head `6c75d801a2` passes [12,139 Java tests and JSP compilation](https://github.com/carlos-emr/carlos/actions/runs/35209972118)
with no failures/errors and 48 skips. The new header calculator link passes the
originating chart reference to the existing patient-authorized server lookup,
keeping age and sex out of both header URLs while preserving prefill. The executable
popup/fallback regression fails against the old header and passes after the fix.
Existing legacy child-calculator URL contracts are unchanged. All twelve touched
JSPs now document their purpose and request contracts. CodeRabbit accepted the
fixes and the final lab failure-handling documentation; all review threads are
resolved. Latest installed administration/calculator validation remains pending.

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
