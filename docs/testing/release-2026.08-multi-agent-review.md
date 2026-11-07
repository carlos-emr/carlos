# Release 2026.08 multi-agent review

## Comparison and outcome boundary

The review starts from release commit
`94c75c772aa0e32c962c1346d9977ed17bc0ce4f` and published main
`e53e6cb555125a942bbb28e52097972a24c88ad1` (beta6): 121 commits and
2,076 changed paths. Fixes target `release/2026.08`; its snapshot version,
SCM metadata and published migrations are preserved.

This is a risk-based code review with executable regression checks. It does not
replace the full qualification matrix or declare the entire release error-free.
The existing [implementation status](release-2026.08-implementation-status.md)
and [compatibility comparison](release-2026.08-main-release-comparison.md)
retain unresolved acceptance and client-review requirements.

## Cycle 1: independent review and reproduction

Six reviewers separately examined persistence/reporting/billing, clinical
transactions, security and dependency compatibility, JSP/JavaScript workflows,
release/packaging/evidence tooling, and browser-harness ownership/refusal logic.
They compared changed behavior with main and inspected callers and existing tests.

Validated findings:

| Finding | Origin and effect | Correction |
| --- | --- | --- |
| Patient birthday XML becomes date-only | Provider migration regression: `Demographic.birthDay` declares `xs:dateTime`, while the global MOXy compatibility adapter drops its time and offset. Direct and nested patient responses differ from packaged main. | A property-specific adapter restores midnight and the calendar's offset; legacy input and JSON contracts remain supported. |
| SOAP/attachment diagnostics expose content | Pre-existing production defect. HCV and MCEDT callers enable logging when their skip settings are absent, although the configuration bean alone defaults false. | Replace inbound/outbound payload loggers with fixed transport events and validated numeric HTTP status; leave streams and lazy attachments untouched. |
| Proxy 405 can qualify as authorization refusal | Pre-existing evidence defect in the changed shared harness. An authenticated heartbeat does not prove a separate unmarked method refusal came from CARLOS. | Require the application's provenance marker for 405, including HEAD; retain legitimate application-owned refusals. |

Evidence and controls:

- The clean release WAR builds successfully on JDK 25. The independently downloaded
  main Debian package matches its committed SHA-256 pin.
- The original compiled privacy oracle reports `soapPayloadLeaked=true`. New
  diagnostic tests inspect console and Log4j output and assert stream identity,
  unread bytes, lazy-attachment noninteraction and failure ownership.
- The two augmented authorization regressions fail against the original classifier;
  all 44 focused transport tests pass after the fix.
- All 7,368 Node tests pass after installing the release lockfile and providing the
  published CLI 1.3.1 checkout. The earlier run's two stale-node_modules failures
  are retained separately; no lockfile was relaxed.
- All 327 Python evidence tests pass with the pinned parser and real `xmllint`.
  JDBC packaging checks pass 9/9, modern-chart checks 8/8 and host-conflict checks
  63/63. Initial missing-tool failures remain separate from these results.
- Frontend checks pass 125 focused Node cases, 11 job-type browser cases, dashboard
  desktop/mobile and HRM fixtures. RTL, toolbar/fax and calendar Chromium fixtures
  also pass. These are isolated fixtures, not installed application qualification.
- Focused Java execution exposed a wildcard compilation error and an incomplete
  synthetic JSON fixture in the new tests; both were corrected. The birthday suite
  subsequently passes all nine cases. Final combined execution is recorded below.

No additional defect was validated in the reviewed typed-query parity, report
limits/stream cancellation, BC billing transaction boundaries, prescription atomic
saves, note locks, BCAR saves, appointment updates, demographic merges or JSP
workflow subset. Native MariaDB and installed/province-specific acceptance remain
separate requirements.

## Review-comment reconciliation

The review inspected inline threads on merged PRs #5690, #5695, #5688, #5560,
#5526, #5495, #5701, #5698, #5631, #5605, #5467 and #5676. All inspected
threads are resolved except #5688's rolling-deployment concern, which explicitly
awaits a maintainer decision. Its existing response describes the supported
single-host replacement deployment. This review does not silently resolve that
security-labelled deployment-policy decision.

## Remaining release qualification

Ordinary CI success cannot replace the required main/release XML/API dispositions,
reviewed path ownership, installed ON/BC lifecycle/render/export checks or external
EBS/SRFax acceptance. Those existing blockers remain visible; historical failed
receipts are not rewritten as passing results for these fixes.
