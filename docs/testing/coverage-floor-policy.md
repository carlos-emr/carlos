# Candidate Java coverage floors

The baseline covers the Java comparison against `currentMain` in the release
verification plan. Source freshness and coverage acceptance are separate checks.
CI checks freshness on PRs targeting `release/2026.08` and release-branch pushes.
It does not approve a coverage waiver or establish release readiness.

The baseline records the Java source tree, complete comparison fingerprint,
each source blob, measured report hash, baseline commit and actual test-case
counts. Test/document-only commits can retain that baseline. Changed Java
sources, a different comparison base, missing/stale floors, dirty Java files or
a baseline from another source tree fail freshness. A failed invocation removes
any previous result file before validation, so an old success cannot survive.
Squash/rebase merges may rewrite or discard the historical baseline commit.
Freshness therefore requires the exact source tree, comparison and per-file
blobs rather than commit ancestry. When the historical commit is available,
its source tree is also checked; its availability is recorded explicitly.
An absent commit does not waive any source identity or coverage requirement.

`baselineSourceTree` retains the measured tree if the original commit object
becomes unavailable; when that object is present, its tree must also match.

Run freshness independently:

```sh
python3 scripts/coverage/check-coverage-floors.py --freshness-only \
  --floors scripts/coverage/release-coverage-floors.json \
  --base <reviewed-currentMain-commit> --output /private/run/freshness.json
```

Coverage acceptance still requires the complete, passing, same-class Java-lane
union produced by `scripts/ci/run-coverage-ratchet.py`. Its invocation of the
floor checker validates freshness before applying line, instruction, branch and
required-refusal assertions. Freshness output explicitly says
`coverageEvaluated: false`; it cannot replace that union result.

A zero line floor on changed executable lines fails as
`unreviewed-zero-floor`, even if a later run happens to cover those lines. A
waiver requires a reason, a named reviewer, a concrete PR review/discussion
permalink and the exact source blob reviewed. An issue link, boolean, blank
explanation or waiver for another blob is insufficient. Review metadata must
refer to an actual approval; filling those fields does not create approval.
Removed, no-longer-zero and nonexecutable waiver entries are rejected. No
waivers were added by this baseline refresh.

Regeneration requires a fresh passing run for the candidate's production and
test sources, its JaCoCo XML, the complete Git comparison, and review of every
changed floor. Keep higher existing metric floors and required refusal lines;
do not lower them to obtain a pass. Enumerate every changed source, retaining
only the documented nonexecutable exclusions. Record current source blobs and
report/test evidence hashes. Never carry a waiver to changed source bytes.

The `0f47111d0e0` merged-source H2 refresh has 34,768 actual test cases,
no failures/errors, and 167 explicit prerequisite/routed skips. It records
508 source-file floors and six package-annotation exclusions, retaining higher
pre-existing floors for 19 files and all four required ZIP refusal lines.
Thirty-two zero floors remain unreviewed. H2 alone also misses 14 line,
13 instruction and 14 branch floors across the full comparison with currentMain.
The Java files changed by PR #6100 meet their retained floors in this report.
These wider comparison gaps are explicit failures;
the required native-lane union and reviewed dispositions remain outstanding.
This refresh is not a passing release coverage receipt.

The `3192f601a58a` refresh includes release `1335e1b28ab4` and the inbox
recovery follow-up. The complete H2 execution records 35,695 cases: 35,479
executed passes and 216 explicit prerequisite/routed skips, with zero failures
or errors. Its source-bound metadata records 673 floors and the same six
nonexecutable exclusions. Higher historical metrics for 47 files and all four
ZIP refusal lines are retained. The full H2 comparison still refuses 77
unreviewed zero floors and misses 33 line, 41 instruction and 29 branch floors.
No waiver or lower threshold is added; the complete native-lane union remains
required. This metadata establishes freshness and does not qualify release
coverage.

The `4159843ab31c` merged-release H2 baseline records 35,804 cases (216 explicit
routed/prerequisite skips), zero failures/errors and 704 source-bound floors.
It retains six nonexecutable exclusions, 54 higher historical metrics and all
ZIP refusal lines, including higher concurrent measurements. There are no
zero-coverage waivers. Seventy-nine unreviewed zero floors and unmet higher
metrics remain refused until the complete same-source native union and review
requirements pass; source freshness alone does not qualify coverage.

The `0c95d9e7cfd1` checkpoint also includes release `ef69eea17a4c` and
preserves both concurrent inbox selection guards and the upload, consent and
OAuth failure-path regressions. Its fresh Ubuntu 26.04/JDK 25 H2 run records
35,817 cases: 35,606 passes, 211 explicit routed/prerequisite skips and zero
failures/errors. The exact five-case permission lane passes separately without skips;
all 6,289 production class hashes remain identical across these executions.
That checkpoint binds 704 floors and six nonexecutable exclusions to that
source and report. Higher historical metrics for 50 files and all four ZIP
refusal lines are retained. Every Java file changed by PR #6100 meets its
retained floors. The full comparison still refuses 79 unreviewed zero floors
and misses 34 line, 42 instruction and 31 branch floors. These failures remain
open; this refresh adds no waiver and does not qualify full release coverage.

The `1980fc9b3321` checkpoint integrates release `0f73961b3804` (#6104).
Its clean Ubuntu 26.04/JDK 25 H2 run passes 35,683 cases with 211 explicitly
registered routed/prerequisite skips (35,894 total), zero failures/errors, and
all five permission cases pass separately without skips. All 6,291 compiled
production classes remain byte-identical across the focused, permission and
full H2 executions and the source-built WAR/Debian payload. The fresh baseline
binds 710 floors and six nonexecutable exclusions to this source and report,
preserving higher historical metrics for 52 files and all four ZIP refusal
lines. All 15 Java paths changed relative to the current PR base meet their
retained floors. The wider currentMain comparison still refuses 80 unreviewed
zero floors and misses 36 line, 44 instruction and 32 branch assertions.
These are retained failures; no waiver or lower threshold is introduced.
The complete same-source native-lane coverage union remains required.

The CDS release integration measures 38,311 Java cases at `57834fb7e141`
(37,935 passes, 376 explicit routed/prerequisite skips, no failures or errors).
Its coverage baseline also includes the fresh MariaDB export run at
`fea89675daa9`: 2,304 cases across 185 suites, with no skips, failures or errors.
All 7,000 production class files were compared individually and are byte-identical
between those runs; the baseline records that manifest and both execution hashes.
These overlapping executions are separate evidence, not 40,615 unique tests.

The regenerated baseline binds 1,263 changed Java paths and nine nonexecutable
package-documentation exclusions. The three new exclusions contain documentation
only and name their concrete contract tests. Historical floors follow the two
schema-parser classes moved into the shared CDS contract package. All 137 higher
historical metric assertions and the required ZIP refusal lines remain intact.
No zero-coverage waiver is added. The source-freshness check passes, while 95
unreviewed zero floors and any unmet higher assertions remain refused by the
coverage gate. This is not full release-coverage qualification.

Test counts use the existing strict `junit_evidence.read_surefire` reader. It
counts every distinct container/case and records the known Surefire 3.5.6 nested
method-name header collision; other inconsistent counters, duplicate cases,
failures and reruns remain refusals. The report-manifest hash uses sorted
name/SHA-256 entries encoded as compact canonical JSON plus a final newline.

The `3528ac5aea96` CDS checkpoint records 38,436 distinct H2 cases across
2,856 suites, including 386 explicit routed/prerequisite skips and no failures
or errors. Its three passing MariaDB CDS executions record 2,416 export cases
across 198 suites, 1,189 import cases across 94 suites and 392 shared cases
across 30 suites, each without skips, failures or errors. All 7,047 production
class files are individually byte-identical between the H2 and native builds.
The baseline records the four execution hashes, the merged JaCoCo report and
the production-class manifest. These executions overlap and are not summed as
unique tests.

This refresh binds 1,292 changed Java paths and the same nine nonexecutable
exclusions. It preserves 191 higher historical metric assertions and all
required refusal lines. Source freshness passes. The current measurements leave
92 executable files without line coverage; retained historical floors leave 88
unreviewed zero floors. Those and unmet historical assertions remain subject to
the coverage gate.
No waiver or lower threshold is introduced. This CDS evidence does not replace
the complete release-lane union required for release coverage acceptance.

The `d074b11dd9a4` checkpoint includes the literal metadata projections, explicit
CLI prescription DAO binding and exact routes for the additional native cases.
Its complete H2 run records 38,437 cases across 2,856 suites, with 387 explicitly
routed/prerequisite skips and no failures or errors. Export passes 2,417 cases
across 198 suites, import 1,189 across 94 and shared 392 across 30, each without
skips or failures. The five permission-sensitive cases also pass as an actual
unprivileged user at `c49ad841853f`, whose Java source and test trees are identical.
All 7,047 compiled production classes match individually across these builds.

The measured union records all five execution hashes. It retains the same
1,292 floors and nine exclusions, all required refusal lines and 190 higher
historical metric assertions. Source freshness passes; 92 files without measured
line coverage, 88 unreviewed zero floors and unmet historical assertions remain
explicit. These overlapping executions are not unique-test sums, and this
checkpoint adds no waiver or lower threshold.
