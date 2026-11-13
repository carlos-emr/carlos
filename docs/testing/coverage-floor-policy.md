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

The `dd7321db79ee` PR #6102 merged-source refresh uses Debian 13 and Temurin
25.0.3. Its complete H2 run records 35,947 actual cases: 35,736 passes,
211 explicit routed/prerequisite skips, and zero failures or errors. The
source-bound baseline now covers 727 files and the same six nonexecutable
exclusions, retaining higher historical metrics for 56 files and all required
ZIP refusal lines. The 80 unreviewed zero floors remain explicit, with no new
waivers. Source freshness does not qualify the required native-lane union.

The baseline records the fresh JaCoCo XML hash and a test-report inventory
hash: SHA-256 of compact, key-sorted JSON mapping each Surefire XML filename to
the SHA-256 of its unchanged bytes. Counts use the repository's Surefire reader,
which validates nested-container header collisions and counts every testcase.

The PR #6102 review refresh binds the unchanged production tree at `d3234545d1849d582caa2ebd89cbfe94c8c6b6b7`
(and test checkpoint `b91796b3d29b930bf82444eb91de0072a1f512f8`) to an Ubuntu 26.04 /
OpenJDK 25.0.5.0.3 run: 35,964 actual cases, 35,758 passes, 206 exact registered
routed/prerequisite skips, and zero failures/errors. All five permission cases pass
separately. Same-class refusal and compatibility runs pass 367 and 132 cases without
skips; their report inventories are recorded as additional runs, not unique additions
to the full-suite count. All 6,301 compiled production classes match the source-built
WAR. Every one of the 27 Java paths changed against PR #6102's release base meets its
retained floors. Thresholds for those paths only rise; the other historical floors,
required refusal lines and 80 unreviewed zero floors remain. The wider main/release
comparison still has 190 unmet assertions pending the required native-lane union and
review. This refresh establishes source freshness and measured PR coverage, not full
release qualification, and introduces no waiver or lower threshold.

The `d950b07166cc5e5c475762f8db0998f23fbe57a5` follow-up removes numeric-parser
backtracking while retaining the declared numeric grammar. A fresh complete Ubuntu
26.04 / OpenJDK 25.0.5.0.3 H2 execution passes 35,784 cases with 206 exact registered
routed/prerequisite skips (35,990 total), zero failures/errors, followed by all five
required permission cases passing separately against the same compiled classes.
The checkpoint replaces the earlier supplemental-run inventories with this complete
run and its permission lane; those overlapping runs are not added to the total.
All 6,301 production class hashes match the clean source-built WAR. All 27 PR Java
paths meet their retained floors without lowering a threshold or adding a waiver.
The wider comparison still has 190 unmet assertions; complete release native-union
qualification and the existing zero-floor review requirements remain outstanding.

The regex-free follow-up uses the same production Java tree as `277e8dc36a002ff90ba60ed048f2e0a3722e2c99`
(`46d0eea64b355073d44ab1584a6b4634bbb381bf`). Its independent Ubuntu 26.04 /
OpenJDK 25.0.5.0.3 execution records 36,020 cases: 35,814 passes, 206 exact
registered routed/prerequisite skips, and zero failures/errors. All five required
permission cases pass separately. The actual validation commit is recorded in
`baselineTestRunCommit`; its production and test sources match the qualified
source/test trees. Unicode unit letters, ASCII-only whitespace/digit behavior and
a trailing grouping comma have maintained regression coverage. All 6,301 tested
production classes match the clean source-built WAR. All 27 PR Java paths meet
the retained floors; no threshold, required refusal line or waiver is weakened.
The wider comparison retains its 190 unmet assertions and 80 unreviewed zero
floors pending complete release native-union qualification.

The PR #6103 review refresh at `b1bc12cefdd` records a complete Ubuntu 26.04 /
OpenJDK 25.0.5 H2 run: 35,938 test cases, 212 explicitly routed/prerequisite
skips, zero failures and zero errors. Its source-bound baseline has 716 floors
and retains 56 higher historical measurements plus every required refusal line.
The native form-table guard passes both MariaDB cases separately without skips.
The 80 existing unreviewed zero floors remain refused; no waiver or lower
threshold is introduced. This refresh establishes source freshness, and does
not replace the full same-class native-lane union required for release coverage.

The earlier PR #6103 Java source tree `292b72cb296206e802acea052628cf4f53a9d258`
qualifies 35,952 cases with zero failures/errors and 212 registered skips.
One full-suite worker was terminated by shared-host memory pressure; all 347
classes assigned to it were retaken (4,563 cases), including the interrupted
Rourke class. The resulting 2,646 reports match the complete case inventory.
All 6,293 production class hashes and compiled test classes stayed identical
across the full run, retake and separate five-case permission lane. Both native
form-history cases pass separately without skips. The coverage report combines
the surviving workers with the complete worker retake from those same classes.
Its 717 source-bound floors retain 56 higher historical measurements and every
required refusal line. The 80 existing unreviewed zero floors remain refused;
there is no new waiver or lowered threshold.

The final PR #6103 Java source tree `b78f9b0c2e6de829d54f3fde8b96ea17f622e13f`
adds positive coverage for normalized consultation months, clearing a saved period
and retaining unrelated properties. A fresh complete three-worker H2 execution
passes all 35,954 cases with zero failures/errors and 212 registered skips; all
2,646 reports match the complete case inventory. The separate five-case permission
and two-case physical MariaDB lanes pass without skips. All 6,293 production class
hashes remain identical across those executions and the source-built WAR/Debian
payload; compiled test class hashes remain identical across the Java lanes.
All twelve selected ON/BC installed browser checks
pass on Ubuntu 26.04; the installed payload and restored descriptor match that WAR.
The refreshed 717 source-bound floors retain 56 higher historical measurements and
every required refusal line. The changed manager persistence lines, instructions
and branches now measure 100% in this run. The 80 existing unreviewed zero floors
remain refused; this adds no waiver or lowered threshold and does not replace the
complete same-source native-lane coverage union required for broader release coverage.

PR #6102 merges release PR #6103 at `5a85f66af4de862c1abd7791107cd5b9ac57d569`
without changing either parent's production Java blobs. The merged Java source tree
is `bfbc7ef7d080eccc9eb5b551ee8c506b205d09e6`. A fresh Ubuntu 26.04/OpenJDK
25.0.5.0.3 full H2 run executes 36,080 cases with zero failures/errors and 207
registered skips. Four test families then receive 33 additional regression cases
for stale issue identities/editor state, expired sessions, historical page bounds,
missing REST settings and independently encoded CPP issue fields. Their complete
132-case final retake passes without skips; replacing only those four reports
produces the verified complete 36,113-case inventory (35,906 passes, 207 registered
skips). All 6,303 production class hashes remain identical across the full run,
retakes, separate five-case permission lane, two-case physical MariaDB form-history
lane and the rebuilt WAR. Test classes outside the four retaken families remain
byte-identical. Both additional native lanes pass without skips.

All 43 combined production Java paths (27 PR paths against the updated release)
meet retained line, instruction and branch floors. The merged checkpoint retains
all 734 source floors, six nonexecutable exclusions, each parent's higher
thresholds and every required refusal line. Source/tree/comparison, coverage XML
and final report-inventory hashes are refreshed for the merged source. The wider
release comparison still refuses 190 coverage checks, including the existing 80
unreviewed zero floors; no waiver or threshold is weakened, and this checkpoint
does not replace the complete same-source native-lane union for broader release
qualification. Native Node 22.22.1 passes all 7,911 cases without skips,
cancellations or source mutation; Python 3.14 passes 405 CI and 30 coverage-tooling
tests.

PR #6167 merges `main` into `release/2026.08` and adds the corrections from the
promotion review. Its Java source tree is `4c1558034df08979c048aa9a3cac7f40fb5a2fe5`. A fresh Ubuntu
26.04/OpenJDK 25.0.5.0.3 full H2 run at `c15dfae068ec120d25303eb27ec1782deb3fac91` executes
36,870 cases in one Surefire run with zero failures/errors and 217 registered
skips. This baseline has no retakes and no additional lane: execution data,
coverage XML and the report inventory all come from that single run.

The refresh binds 801 source floors and eight nonexecutable exclusions to that
source and report. The two new exclusions are documentation-only package-info
files changed in this comparison: `documentManager/htmlpdf/support`
(`XhtmlDomUnitTest` exercises the rules it documents) and `admin/service`
(`SecurityRecordUpdateServiceUnitTest`). The floors were refreshed twice in this
PR. The first refresh added 55 sources new to the comparison and raised 74
metrics; the second, after the last product changes, added 15 and raised 4.
Higher historical metrics and every required refusal line are retained, no
waiver is added and no threshold is lowered. Freshness passes.

Coverage acceptance does not, and this checkpoint does not claim it. Applied to
this single H2 run, the floor checker refuses 207 checks: the 77 unreviewed zero
floors, and 130 retained line, instruction or branch floors on 61 files, in a run
that includes no native lane. The complete same-source native-lane union remains
required for release qualification. Native Node 22.23.4 passes all 7,964 cases
without skips, cancellations or source mutation; Python 3 passes the 30
coverage-tooling tests, and 398 of the 405 CI tests: the other seven compare the
shipped `carlos-ctl` pin (1.3.3, from `main`) with the published identity recorded
for 1.3.2 and cannot pass until 1.3.3 is published and that record is updated.
