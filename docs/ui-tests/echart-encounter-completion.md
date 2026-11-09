# Encounter completion acceptance ledger

Implementation branch: `fix/echart-encounter-completion`, based on
`release/2026.08` at `9004536dc8314d5f50542b48905ca3ca4099a916`.
This is work in progress. No ticket is claimed closed by this ledger.

| Ticket | Implementation / outstanding evidence |
| --- | --- |
| #5834 | Optional hidden modules skip presentation refresh; focused JS passes; installed browser coverage outstanding. |
| #5750 | Save-coordinator failure recovery outstanding. |
| #3423 | Lease, ownership, draft and rollout work outstanding. |
| #5992 | Reason-specific conflict handling and test correction outstanding. |
| #5703 | Baseline browser reproduction and selection round-trip outstanding. |
| #5903 | Literal template closures and encoded full names; focused dispatch test passes; browser round-trip outstanding. |
| #4588 | Checklist toggle exposed; permissions and browser persistence checks outstanding. |
| #5844 | CPP issue transport URI-encodes each component before the outer context encoder; round-trip coverage outstanding. |
| #5993 | Full module responses replace module wrappers; both issue modules refresh; focused JS passes. |
| #5376 | Baseline navigation trace and correction outstanding. |
| #5958 | Registry membership is validated once at the DAO SQL boundary; redundant facade queries removed. Fifteen focused form/DAO tests pass; installed query-count evidence outstanding. |
| #6011 | Shared month validation, malformed-value fallback, preference writers and units label implemented; action/browser validation outstanding. |
| #5901 | Notes/module errors rendered as text; focused JS passes; remaining updater inventory and browser controls outstanding. |
| #5599 | Legacy editor body removed; full route/caller/configuration/import inventory outstanding. |
| #5931 | CAISI entry always redirects to modern chart; retired POST recovery outstanding. |
| #5933 | Bounded DAO paging and corrected links; focused DAO/action tests pass; native/browser tests outstanding. |
| #5856 | Modern Messenger PDF implementation outstanding. |

## Recorded verification

- Pinned baseline: JDK 25 Maven WAR package passed, tests skipped.
- Initial panel/dispatch selection: 30 Node tests passed, no skips.
- Initial preference/history selection: 18 Java tests passed, no failures/errors/skips.
- These focused results do not establish final application or package qualification.
- Before/after screenshots, installed Ubuntu 26.04 package tests, ON/BC coverage,
  native concurrency tests, full suites, final-head CI and CodeRabbit review remain outstanding.

## Required preservation contracts

Modern entry routes, patient/program/role/facility access, immutable revisions,
CPP extensions and selections, draft ownership, appointment/billing handoffs,
historical-only records, external encryption keys and durable rollback settings
must survive. A rejected or unknown save cannot silently discard text or resume
signing; failed attachment generation cannot replace valid staged attachments.

## Subsequent initial-stage verification

- Full Node suite: 7,703 passed, 4 skipped, 0 failed (7,707 reported tests).
- Corrected panel/dispatch/order/isolation/i18n selection: 77 passed, no skips.
- WAR and JSP compilation passed after the first implementation commit.
- Form registry/history selection: 15 Java tests passed with no failures/errors/skips.
- Pinned DrugRef `93775a0` verify passed: 309 tests reported, 6 skipped.
- Signed-off commits are pushed to draft PR #6103, targeting release/2026.08.
- CodeRabbit was explicitly requested but rate limited; it reported the next
  included review available about 36 minutes after the initial request.
- Complete renderer download is blocked by HTTP proxy CONNECT 403 for
  `commondatastorage.googleapis.com`. Domain access was requested from the user.
- Development baseline Debian packages were built in Ubuntu 26.04 with
  `SKIP_EFORM_RENDERER=1` solely to investigate ordinary chart screens. They
  are not complete release packages or final acceptance evidence.
- Full-package installation, before/after screenshots and final-head CI remain
  outstanding. Do not mark this draft ready or close the 17 issues based on
  these initial-stage results.
