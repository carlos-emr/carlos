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

The `f69b87ad0ff0` refresh is PR #6113 (#5969, #5968), merged with release
`38cfc10c0ae7` (#6100). It changes the source blobs of
`MariaDbCompatibilityDriver.java` and `MariaDbJdbcCompatibility.java` only.
Every other floor and exclusion is unchanged. Its evidence is CI run
42762907938 ("Check Carlos EMR", `tests` job) for that head. The PR merge ref
it checked out has the same tree as `f69b87ad0ff0`. The H2 run records 35,937
cases with 218 explicit routed/prerequisite skips and zero failures or errors.
`baselineReportSha256` is the SHA-256 of that run's
`target/site/jacoco/jacoco.xml` (artifact `java-coverage-42762907938-1`).
`baselineTestReportsSha256` is the SHA-256 over the 2,649
`TEST-*.xml` reports of artifact `java-test-reports`, taken in sorted filename
order as one `<name>\0<sha256 hex of file>\n` line per report.
The driver's changed-line floors rise from 3.29/2.47/1.25 to 48.02/47.62/40.97
(lines/instructions/branches). That is because `MariaDbCompatibilityDriverNumberUnitTest` now runs the
#5968 conversions in the H2 lane. Previously only the physical-MariaDB test
exercised them. `MariaDbJdbcCompatibility.java` rises from 95.36/96.24/88.34 to
97.77/98.14/90.45. No floor was lowered and no waiver was added. Both changed
files meet their floors in this report. The wider currentMain comparison in
this run still refuses 80 unreviewed zero floors and misses 37 line,
45 instruction and 33 branch assertions, all in other files. The same misses
appear in the CI run of `0e86503` without the new tests. That run also
missed all three of the driver's previous floors, which the new tests cover.
These remain open release failures; the complete same-source native-lane union is
still required.

The `2255c1b31db3` review refresh fixes TINYINT(1) object reads for negative
values other than -1 and adds the corresponding numeric-truth boundary cases.
Its clean Ubuntu 26.04/JDK 25 H2 execution records 36,078 actual test cases,
213 explicitly routed/prerequisite skips and zero failures/errors. Counts use
the individual `testcase` elements: Surefire's nested-container header counters
collapse some equal method names. The release evidence parser accepts every
retained identity and skip route across the 2,643 reports. Their digest uses
the same sorted filename/NUL/file-SHA-256 protocol described above.
All 6,292 production class hashes match the separate 35-case physical MariaDB
execution. The driver's floors rise to 51.72/51.34/45.40 percent
(lines/instructions/branches); its source blob and the baseline's source,
comparison and report hashes are refreshed. Every other file floor and all
refusal lines are retained. No floor is lowered and no waiver is added; the
comparison-wide qualification requirements and 80 unreviewed zero floors from
the preceding baseline remain.

The `2766d0fffb89` checkpoint also retains SQL NULL for supported primitive-class
`getObject` requests, while primitive getters still return zero plus `wasNull()`.
Its clean Ubuntu 26.04/JDK 25 H2 run passes 36,087 actual cases with the same
213 explicitly registered skips, zero failures/errors and 2,643 reports. All
6,292 production class hashes match the separate 60-case adapter/physical
MariaDB execution and the source-built packaged WAR. The original Connector/J
9.6.0 jar agrees on all 80 additional typed-NULL observations in both protocols.
The driver's floors rise again to 54.08/53.09/48.79 percent; only that row and
the source/report provenance change. Other floors, refusal lines and the
comparison-wide qualification requirements remain intact, with no lower floor
or new waiver.

The `6b679afc3659` checkpoint extends the same TINYINT(1) truth rule to
`transformedBitIsBoolean=true`. The seven boundary/NULL unit cases exercise
both BIT and BOOLEAN metadata, and the physical cases exercise all four
`tinyInt1isBit`/`transformedBitIsBoolean` combinations in both protocols,
including the integer-object controls. The original Connector/J 9.6.0 oracle
agrees throughout that configuration matrix. Its clean Ubuntu 26.04/JDK 25
H2 execution again passes 36,087 actual cases, with 213 explicitly registered
skips, zero failures/errors and 2,643 reports accepted by the release evidence
parser. All 6,292 production class hashes match the separate 60-case
adapter/physical MariaDB execution and the source-built packaged WAR. The
driver's floors rise to 54.20/53.32/49.00 percent; its source blob and the
source/report provenance are refreshed. Every other floor and refusal line
is retained, including the comparison-wide qualification requirements and
80 unreviewed zero floors. No floor is lowered and no waiver is added.
