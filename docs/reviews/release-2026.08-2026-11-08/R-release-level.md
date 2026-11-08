# R: Release-level findings (coordinator)

## R-1: beta7 was tagged on main without any release/2026.08 work; release branch still claims beta7-SNAPSHOT
- Severity: High
- Category: process
- Confidence: Confirmed
- Evidence: tag 2026.08.0-beta7 -> d40d2939 (main) = beta6 (e53e6cb5) + #5739 only. `git merge-base --is-ancestor 2026.08.0-beta7 origin/release/2026.08` -> no.
  release/2026.08 pom.xml:7 `<version>2026.08.0-beta7-SNAPSHOT</version>`, scm tag HEAD. 134 release commits (2,550 files) are in no tag.
- Problem: docs/release-process.md: "After publishing a release from main, back-merge the tagged main commit into its maintenance branch before advancing that branch to its next snapshot." Not done. The branch names a version that is already published with different content.
- Correction: back-merge tag 2026.08.0-beta7 into release/2026.08 (`--no-ff`, signed-off). Only conflict: pom.xml version + scm tag (lines 7 and 22) — resolve to `2026.08.0-beta8-SNAPSHOT` / `HEAD`. Auto-merged: debian/control carlos-ctl floor 1.3.1 -> 1.3.2, debian/carlos-ctl.pin, debian/changelog (adds 2026.09.0~snapshot29), debian/rules, O19 manifests/generator, scripts/debian-build.test.js, plus #5739's migration V1.0.59.33, schema contracts, flyway-merge-order, carlos_report_grants.sql row, OscarDownload. The next promotion must be beta8 (never reuse beta7).

## R-2: #5739 fix landed directly on main, not via release/2026.08
- Severity: Medium
- Category: process
- Confidence: Confirmed
- Evidence: d40d2939 "fix(o19): audit historical billing filenames and prepare beta7 (#5739)" — application fix + Flyway V1.0.59.33 + O19 manifests, merged to main.
- Problem: release-process.md: fixes start from and target the oldest affected release/YYYY.MM; only release-preparation PRs target main. release/2026.08 currently lacks the fix and its (now published, checksum-frozen) migration.
- Correction: covered by the R-1 back-merge; then forward-merge to develop. Confirm develop gets V1.0.59.33 without renumbering conflicts.

## R-3: Release qualification gate not met (release says so itself)
- Severity: High
- Category: process
- Confidence: Confirmed
- Evidence: docs/testing/release-2026.08-implementation-status.md: "No release-ready verdict has been produced; preserved qualification attempts remain incomplete." and "Final reconciliation correctly reports releaseReady=false." Issues #5700 and #5720 open.
- Correction: finish #5700/#5720 qualification (installed-package, native MariaDB, browser lanes) on the final release head before promotion.

## R-4: CI evidence
- PR #5800 head ac88fbee == release head tree 9004536d (identical). Checks green: build, tests, jspc, MariaDB DAO integration tests, CodeQL, Semgrep, SpotBugs, PMD, Sonar, WAF, Node/Python script regressions, release-verification tooling.
- "DCO Sign-Off" = failure (two runs) on PR #5800 yet merged. (S26a to explain.)
- Push builds on release/2026.08 do not run the Maven test workflow (maven-project.yml runs on pull_request/workflow_call only); evidence for the head relies on the PR run.
