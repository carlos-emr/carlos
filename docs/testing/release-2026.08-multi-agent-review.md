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

## Cycle 2: cross-review, state isolation and evidence completeness

Reviewers exchanged areas: security examined the scoped birthday adapter and
legacy-reader corpus, clinical/release reviewers examined transport diagnostics,
frontend examined authorization provenance, and persistence reviewed stream/DAO
boundaries. This produced additional validated corrections:

- Both globally registered attachment resolvers retained the first response's
  collection in an instance field. Santuario calls the retained instance directly;
  its current SPI does not honor the legacy `engineIsThreadSafe` method. The fix
  reads the current thread's attachment context for every resolution. Independent
  original/fixed probes reproduce and remove contamination across responses and
  after cleanup. Regression tests prime the old state before concurrent requests.
- Resolver logs formatted content IDs, arbitrary attachment objects and provider
  exceptions. Fixed messages retain explicit resolution failures and context
  metadata without formatting those private values. Signature consumers retain
  ownership of unread streams.
- The independent privacy oracle captured only stdout, so a real Log4j leak could
  pass. Its revised schema requires preserved streams/attachments, both diagnostic
  directions and detection of a real nonadditive Log4j negative control. Only
  boolean observations leave memory. Existing historical schema-1 results remain
  historical, rather than being silently promoted.
- The strict Node manifest missed the latest release's prescription-test changes.
  A complete Node 22.23.4/npm 10.9.9 run passes all 7,368 cases. The reviewed
  manifest update changes exactly five source hashes: four prescription files
  from the existing release commit, plus the authorization regression. All case
  rows for the other 512 files remain unchanged. The original manifest rejects
  this run; the updated exact identities/multiplicities accept it.
- Expected birthday output is adjusted only for serialization. Both legacy XML
  reader paths continue consuming the unchanged frozen MOXy input.

## Cycle 3: final adversarial review

Independent reviewers checked the complete fixes, public signatures, caller
defaults, diagnostic provenance, test sensitivity, exact Node inventory and
strict result-contract assertions. The final resolver review reproduced a legal
Content-ID containing literal `+` being decoded as a space and falsely refused.
Both resolvers now preserve literal plus before one percent-decoding pass;
tests cover literal `+`, encoded `%2B`, encoded space `%20`, and malformed escapes.

The birthday annotation changes a reviewed CDS source fingerprint. Review confirms
CDS import uses date components/LocalDate and export builds its XMLBeans document
from the existing DOB data; neither path uses this JAXB property adapter. The
single affected reviewed hash and canonical generated crosswalk are refreshed.
All 25 crosswalk/refusal cases pass, with the 407 field and 130 choice-arm
dispositions unchanged. Required new Python case identities and resolver test
bindings are added rather than reducing any gate.

Final local JVM/native, binary-comparison and remote CI results are reconciled
in the pull request. Full release acceptance remains subject to the requirements
below; passing the focused corrections does not waive unreviewed compatibility
differences or incomplete installed/vendor qualification.

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
