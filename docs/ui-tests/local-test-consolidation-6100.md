# Local test consolidation for release/2026.08

PR #6100 consolidates the 32 requested issues. This is an implementation and
qualification ledger, not a declaration that all 32 acceptance criteria pass.
The existing release verification matrix remains authoritative. Historical
passes, a registered script, and a successful unit test do not establish an
installed workflow pass. Leave tickets open until their complete acceptance
has current evidence.

The complete `67495a962dc0` candidate matched 6,206 production-class,
changed-JSP and translated-resource entries through its WAR, Debian payload
and both local installations. ON/BC installed CI at `a040ff73b291`
independently rebuilt the packages and passed all seven checks, including
audit reads, native lab versions and qualified results. The stronger local
lab matrix also passed on both provinces with a historical zero completeness
counter and a same-time late final. Failed/interrupted runs remain retained;
no expectation was waived.

The clinical follow-up at `e2b2c4979a2f` retains the authorized note ID for
auditing and explicitly refuses a missing consent type before a DAO query,
while preserving inactive-type queries. Its complete shipping source build
and lintian passed, all 6,206 checked package entries matched, and 34,526 actual
H2 cases passed with 148 explicit prerequisite/routed skips. All 6,192 tested
production classes match that package. The coverage/document follow-up retains
this Java source tree. Final-head CI qualifies its own rebuilt artifacts;
historical evidence never substitutes for that qualification.

## Scope and disposition

| Issue | Implementation and remaining acceptance |
| --- | --- |
| #4102 | Actual develop SMS/consent locking implementations are absent from this branch. Keep branch-inapplicable races open; do not substitute unrelated locks or synthetic production tables. See `test-only-release-issues.md`. |
| #5718 | Corrected the all-sex operator and added matching/nonmatching multi-value and alias controls. The current ON installed 41-rule chart/list/detail, patient-isolation and native-fact-update retake passed. |
| #5812 | Retain native MRP routing assertions. The composer now carries its draft through patient search, refuses an unselected assignee in both site modes, and offers an explicit blank choice for an unavailable preferred provider (#5824/#5825). Current default-profile ON/BC retakes passed; the dedicated multisite MRP variant is enrolled in the required profile lane. It drives both clinic filtering and the visible Change control. |
| #5806 | Retain actual signed callback/deny boundaries. Callback verification/storage now preserve internal percent escapes and invalid signatures map to 401 (#5823); real-parser signature controls cover header, form and query transports. All 262 selected OAuth/eForm/CSRF/report regression cases and the current ON/BC callback, Deny, receiver and token-isolation retakes passed. |
| #5801 | Explicit empty eForm values clear saved markup and PDF defaults; missing parameters remain distinct. Existing nonblank-only value rows and chart-template substitutions are preserved. The current ON/BC native visual editor, checkbox/radio, image, signature, saved-clear and actual PDF retakes passed. |
| #5709 | Retain lookup CRUD and consumer isolation. Provider add/edit forms omit inactive choices; edit explicitly preserves and labels its current retired value. The consumer test checks both that preservation and the new-provider boundary. Lookup add/remove/reorder now require POST and have direct/dispatcher GET/HEAD and authorized POST controls; current ON/BC lookup lifecycle and consumer-isolation retakes passed. |
| #5708 | The current ON native multi-recipient/routing retake passed with no residue. The offered New/Filed status is source-wide; no per-recipient control is invented. This check is registered for ON. |
| #5730 | PDF suffix matching now handles upper/mixed case; action tests inspect stored page count and MIME. Current ON/BC native eDoc/Inbox format, suffix-case, opaque/malformed-storage, retry and exact multipart-limit retakes passed; opaque storage is not represented as content validation. |
| #5803 | Qualified bounds are excluded from exact numeric graph points. Accepted measurement literals preserve source spelling. The explicit #5828 contract retains the shipped filter: final-status Pending remains unfiled, with visible unsupported-history and grid-scope explanations in all five language bundles. Original reports/PDFs retain Pending exactly. ON/BC installed CI and local retakes passed; final-head CI retains all report, graph, tooltip and PDF assertions. |
| #5727 | Corrected request-scoped First Nations selected/original values and removed a missing focus handler; separate required First Nations round-trip and encoding profiles enable the module. Both profiles passed on current ON/BC packages. Two owned three-digit communities enabled round-trip setup; complete lookup bytes were restored and the application cache was restarted. |
| #5726 | CML F/C observations both count as complete; clinical observation date and correction status precede the source-ID tie break. Existing correction metadata is interpreted from the stored source without rewriting it. Historical deliveries no longer replace active measurements. ON/BC native retakes passed for original/correction/older/same-time-final ingestion, a deliberately simulated owned pre-fix completeness counter, independent review state, immutable raw sources and exact duplicate replay. Final-head installed CI includes this complete matrix. |
| #5794 | Only the error check provisions temporary fault routes; exact descriptor restoration is required before its supervisor exits. Current ON/BC packaged retakes passed with exact descriptor restoration, fault-class removal and no residue. Broader development/lifecycle filter qualification remains required. |
| #6025 | Growth persistence tests compare all five submitted headers, with H2 and physical MariaDB executions. |
| #6093 | A real physical-table grouped history test covers independent MAX projections, historical ordering, the EctFormData caller and other-patient isolation. Required in generated and ON/BC Flyway native lanes. |
| #5946 | DAO tests cover the non-Ontario LIKE branch and Spire's one-row length ordering, with distinct provider fixtures. |
| #5700 | Keep all 40 workstreams and all local acceptance gates. No full-release readiness claim while any local gate or product blocker remains. |
| #4492 | Keep Tasks 17 and 35–48, profile/locale/time-zone, signal, application-finding and upgrade requirements; full current runtime qualification is outstanding. |
| #5177 | Keep H1–H46 and transferred acceptance, including full repetitions and independent residue checks. Helper passes do not replace installed executions. |
| #5487 | Keep current installed and development response-filter, authentication/reset and error-path lifecycle retakes. |
| #5852 | BC billing edit/delete checks use the actual link roles; dependency-table fixtures exercise anchor markup. Both current BC association and private-code workflows passed their real Edit/Delete links, with no residue. |
| #5881 | General suites/lifecycle clients run with the shipped error mapping; only the error check is supervised by the fault adapter. It cannot wrap arbitrary suite commands. |
| #5883 | The 20 retained scripts now write shared structured outcomes after their existing cleanup. A manifest regression checks reachability. BC PathNet has its own required profile; O19 has a required explicit imported-database/authentication lane. Neither is silently dropped. |
| #5884 | Every non-dry deployment run requires CARLOS_DISPOSABLE_HOST=true, including loopback. Pure standalone fixtures remain independent of deployment state. |
| #5914 | The shipping Debian build checks nested WAR jars for exactly the GlassFish JAXB factory, rejects extra/MOXy providers and malformed/ambiguous archives. |
| #5973 | Native DAO URLs use the packaged ROUND/old-alias conversion options through the compatibility driver. |
| #5977 | Source-contract tests pin both the DrugRef lock goal/path and absence of the lock-skip flag. The new PR package job executes the shipping source-build pipeline. |
| #6048 | Jasper compiles all eight dynamically dispatched JSP fragments in addition to the ordinary JSP execution; an exposed source-level incompatibility was repaired. |
| #6062 | Source/blob-bound candidate floors are regenerated; CI detects stale Java sources, comparison bases and missing/stale floors. Twenty-seven policy tests cover refusal paths and stale-result removal. Executable zero floors require an actual named, source-bound review permalink. Higher old floors and all required ZIP refusal lines are retained. No waiver was added: H2 still refuses 29 unreviewed zero floors and unmet higher metrics; the complete native-lane union remains required. See `docs/testing/coverage-floor-policy.md`. |
| #6069 | Java runs on main/release pushes; PR CI now builds both complete Debian packages and tests ON/BC installs. The final head must pass these CI lanes. On restricted AppArmor kernels, the disposable container exposes securityfs before installation so the package can load its dedicated Chromium userns profile; the kernel restriction and renderer sandbox remain enabled. Installed CI also exercises a real eForm PDF. |
| #6070 | Node, packaging Python and O19 generator CI use strict outcome runners. Native ON/BC references are provisioned for required generator cases. The pinned CLI's separate upstream suite retains its own contract. |
| #6072 | Error-page detection recognizes colon-bearing HTTP status and the sanitizer heading/reference signature; failed body reads propagate. Controls cover legitimate reference prose. |
| #6076 | Finding 141 passed on ON/BC and its pin is retired. Fixes for 206–208 add patient/note revision-read auditing, exactly one patient/provider-identified print event before the first PDF byte, and structured patient IDs on consent/merge manager events. Audit failures withhold clinical output. Native ON/BC audit retakes and actual H2 group-note audit rows pass. The authorized note ID is retained instead of reparsing a nullable parameter; changing-request and audit-refusal controls pass. All assertions remain fatal without a pin. |

## Qualification accounting

The merged-source H2 retake at `c50603fb641` completed 34,495 cases with no failures/errors and
143 explicitly routed skips. The OAuth callback changes also passed all
262 selected regression cases, including six independently signed transport and
refusal cases. The generated-schema MariaDB lane passed 3,128 cases without skips, and all
177 selected lookup-guard/mutator/render cases pass. Native MRP fixture cleanup
now reserves and removes owned provider billing-centre rows; the failing run is
retained and its three synthetic orphan rows were recovered without changing
unrelated rows. The strict Node inventory now has 7,742 passing cases in 547 files. Rebuilt
ON/BC package retakes for the lookup guard and visible MRP guidance passed
without residue. The required multisite lane now includes a dedicated MRP
variant, which requires the real clinic selector and drives the native Change
control for manual reassignment. Both ON and BC passed, including profile restoration.
The complete `b8f89793c5d7` package candidate matched 6,197 production-class/changed-JSP
entries through the WAR, Debian payload and both installations. Later commits change
test/CI infrastructure, rename an unused OAuth exception parameter, and repair
CML versioning, literal views and audit events. The subsequent complete
`67495a962dc0` candidate and `a040ff73b291` ON/BC installed CI qualify those
clinical paths; `e2b2c4979a2f` adds the final nullable-input guards and full
passing Java retake. Final-head build/install CI qualifies its own rebuilt
artifacts separately. Earlier ON/BC document, OAuth and eForm
widget/PDF bundles passed with no skips or residue; the ON guideline and forwarding
retakes and BC billing selector retakes also passed. These focused passes do not
substitute for the remaining full profiles, repetitions and lifecycle acceptance.

The generator's old 289 count included twelve duplicate executions of
`TestOverlayRulingsReachTheManifest`: unittest also discovered the `_base` loop
variable as a class alias. Removing that alias leaves 277 unique cases. The
strict runner rejects duplicate identities, skips and every non-PASS outcome.
The matrix floor now names those 277 unique cases; no unique test was removed.
The native socket's private parent stays mode 0700; its mysql-owned child is
traversable so the unprivileged CI runner can execute the real tests.

Error qualification has a 300-second browser bound plus compilation, counters
and two bounded service restarts. Its 1600-second manifest budget includes that
setup/restoration, which the old suite-wide wrapper placed outside the per-check
budget. The smoke ceiling therefore includes the additional 1300 seconds.
Failure or cancellation must still return nonzero after restoration.

## External exclusion

The user's external-systems exclusion covers Ministry staging
`ebs-packaged-staging` and the four SRFax `manual-fax-*` vendor gates already
identified in the matrix. Their absence never becomes PASS. Local checks with
`kind: external`, local transport/privacy checks, package lifecycle, backups,
restart/key persistence, crash recovery, load and production-view coverage stay
in scope. This local scope cannot establish full vendor-qualified release
readiness.

## Additional local qualification and remaining gates

The `f8e2771b0e6` full local Java retake again executed 34,526 cases with no
test failures/errors and 148 explicit root/prerequisite skips. Its formal
receipt correctly remained FAIL: the release matrix registered only 143 skip
routes, while CI injected the other five permission destinations dynamically.
The correction registers a real unprivileged permission lane, requires its
exact five cases and same-class JaCoCo counters, and makes CI refuse a missing
registered route. No skip is promoted to a pass. All 40 workstreams remain;
the additional lane increases the matrix to 91 checks. The original failed
formal receipt is retained, and qualification must be retaken on the correction.
The permission helper also compiles before dropping DAC bypass, because the
source-bound verifier deliberately clears compiled classes before every Java
lane. Its actual five-case execution and counters cannot qualify a stale build.

The complete `ca2bd8bf4836` source-built candidate passed lintian and matched
all 6,206 checked payload entries in both installed provinces. Its 6,192
production classes are byte-identical to the full `e2b2c4979a2f` Java retake.
Each province passed all seven installed Playwright checks and all nine actual
same-candidate APT reinstall assertions, including complete clinical-cell,
document, encryption-key and application-byte preservation. Private transition
and recovery evidence is retained. This is equal-version reinstall evidence;
it does not establish published-predecessor upgrade acceptance.

At `ca2bd8bf4836`, all nineteen Actions workflows completed: eighteen passed,
including source-built Debian/ON/BC installs, Sonar and all five CDS resource
profiles. The Java workflow passed its full tests, permission, compatibility,
timezone and font retakes, but failed the newly added coverage-artifact step:
`make install --run-tests` packages before running tests, so the lifecycle's
report goal precedes execution data on a clean runner. The follow-up explicitly
runs `jacoco:report` after successful tests and requires both XML and execution
data inside that same container before publishing them. The failed workflow
remains failed; its replacement must pass independently. The retained sanitized
reports contain 34,526 H2 cases (148 routed/prerequisite skips), five passing
permission cases and four passing legacy compatibility cases.

The complete `67495a962dc0` installation passed all 16 backup-core assertions
on both ON and BC: actual restic backup/drill, SQL/file/config/key restoration,
application read denial, encryption/canary checks, missing-repository refusal,
owned cleanup and private recovery escrow. These core passes do not qualify
retention, interruption or terminal purge, and remain bound to their candidate.

The explicit scope audit still has 2,573 unowned production paths; published
beta5/beta6 migration bytes remain identical. The independent binary-client
comparison against pinned beta6 retained 132 unreviewed observations: one
baseline client rejection, 103 public-API findings in classes untouched by this
PR, 26 changed wire values and two unqualified baseline outputs. These are
release-wide compatibility decisions, not a passing compatibility receipt.
Do not restore unsafe legacy SQL APIs or approve those changes implicitly.
Full profiles/repetitions, current package lifecycle, crash/load and production
view coverage remain distinct acceptance. The matrix refuses full release
readiness while any of these local gates is incomplete.

## Final review retake

At `3d892ab89c46`, all nineteen Actions workflows, the automatic DCO check
and PR-label checks passed. Downloaded CI artifact hashes matched GitHub's
digests: 34,526 H2 cases (148 explicit routed/prerequisite skips), five passing
permission cases, four passing legacy compatibility cases, and native counts
3,128/260/118. Both main and permission JaCoCo XML/execution pairs were present.
The separate local same-class union completed all ten required Java executions
but refused acceptance: 29 unreviewed zero floors and three line, four
instruction and eight branch misses. No floor was lowered and no waiver added.

The subsequent complete CodeRabbit review identified additional corrections:
required Python/Node outcome retention; isolated permission-test working paths;
specific JAXB and mount-refusal diagnostics; supervisor signal diagnostics;
coverage freshness through squash/rebase merges; exact CML version de-duplication;
accurate localized history notices; and the task warning/test-name conventions.
Freshness still requires the exact Java tree, comparison and every source blob;
unavailability of a historical commit is explicitly reported.

Incomplete related CML sources now have a separate display-only resolver. The
authorized selected report remains readable with a translated warning and
disabled controls. Mutation and measurement resolution retain the strict API;
query failures still propagate. Unit cases require the selected-only warning,
normal version authorization, exact de-duplication and continued mutation
refusal. The native browser workflow adds a temporary, owned unreadable-source
probe with exact restoration and independent source/clinical/control snapshots.
These production corrections require fresh source/package/browser qualification;
the earlier `ca2bd8bf4836` installed-payload identity does not cover changed Java
or JSP bytes. Historical failed and partial retakes remain retained separately.

The release branch subsequently advanced to `6eddfa9bd1ec` (#6097). The merge
retains its BC history migration, billing repairs, explicit native zero-date
policy controls and 29-case legacy-export family, alongside this PR's grouped
history case, old-alias JDBC behavior, ROUND default and exact five permission
routes. Before that integration, `b0ef15c990c1` passed the full 34,527-case H2
run, five permissions, both 118-case timezone profiles, native 3,128/262/118,
four browser/PDF cases and four legacy compatibility cases. Those nine Java
executions remain bound to that earlier source; they do not qualify the newly
merged billing production bytes. The merged source needs its own CI report,
coverage-baseline refresh, source-built packages and installed browser retakes.

The next release update, `1062fe6a02f4` (#6101), advances tickler drafts,
recipients, dates and cancellation handling. Its complete draft transport and
real-signal note-dialog harness are retained. Conflict resolution also retains
the unique visible clinic Change guard, explicit multisite prerequisite,
billing-centre fixture ownership, document-format navigation restoration and
translated unavailable-recipient warning. The repeated-draft regression now
executes the production transport function and verifies every transported field.
All 115 focused fixture, cancellation, draft and validation cases pass together.
