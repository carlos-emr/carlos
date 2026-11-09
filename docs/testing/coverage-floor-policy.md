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
