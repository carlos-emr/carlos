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

A zero line floor on executable changed lines fails as
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

The `e2b2c4979a2f50e2a3fb90a163ef60e133f2c980` H2 refresh has 34,526 actual
test cases, no failures/errors, and 148 explicit prerequisite/routed skips.
It records 467 source-file floors and six package-annotation exclusions,
retaining higher pre-existing floors for 11 files and all four required ZIP
refusal lines. Twenty-nine zero floors remain unreviewed. H2 alone also misses
seven line, six instruction and ten branch floors. These are explicit failures;
the required native-lane union and reviewed dispositions remain outstanding.
This refresh is not a passing release coverage receipt.
