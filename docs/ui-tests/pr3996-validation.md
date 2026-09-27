# PR #3996 validation

Issue #3984 adds patient-scoped tickler attachments. Review also repairs existing lab-source
collisions in consultations, responses and eForms (#4024), shared attachment ownership checks
(#4025), nullable consultation rendering (#4026), and saved eForm subject loss (#4027).
Shared tickler validation, atomic edits, POST-only writes, CAISI rendering and fixture cleanup
include the fixes tracked in #4020–#4022 and #4012.

## Review decisions

- Carry lab source and ID together through picker, persistence, REST, reload and preview.
  Infer a legacy bare ID only when the parent patient's routing identifies exactly one source.
  Ambiguous or missing legacy attachments remain visible as unavailable and removable; PDF
  generation refuses them instead of silently substituting a different report. Non-HL7 PDF
  rendering remains unsupported and fails explicitly.
- Validate patient ownership and type-specific read permission before shared attachment writes.
  Keep unchanged restricted selections, reject unauthorized changes, lock the parent and active
  attachment rows, and roll back clinical edits with attachment validation failures.
- Preserve tickler comments and history atomically; use persist-only cascades for new children
  because permission queries can flush the persistence context before the manager saves them.
- Keep stored nullable consultation fields unchanged while displaying blank controls.
- Supply the persisted eForm subject only when the template lacks a subject control. Jsoup
  serializes its raw text once, preserving quotes, ampersands and angle brackets.
- Deploy #3986's V1.0.36 before this PR's V1.0.37 tickler and V1.0.38 lab-source migrations.
  The unused V1.0.35 gap is intentional. Both attachment migrations are unreleased; numbering
  either below V1.0.36 would leave it unapplied on upgraded installations without outOfOrder.

## Validation performed

The previous complete Java run passed 13,577 tests (51 existing skips); all 1,046 Node tests
passed. Added regressions cover source collisions, permissions, real Spring bean wiring,
Hibernate query-time flush, atomic tickler rollback, nullable consultation rendering and eForm
subject text. Final package and installed results are recorded below after the last build.

Ubuntu 26.04 DEBs for iteration 4 passed 6,696 payload and installed-file comparisons, health
checks and these browser scenarios (no skipped scenarios in their final runs):

- Normal configuration: consultation lab-source rows; all five tickler attachment types,
  detach/revive, duplicate selection, failed/unlisted pickers, foreign ownership, source and GET
  rejection, refiled lab removal, and atomic failure; application health; nullable consultation
  controls and PDF; consultation creation; eForm attachment persistence and merged PDF;
  lab macro; tickler CRUD, note dialog, patient list, and validation messages.
- CAISI configuration: tickler attachments, note dialog and validation messages.
- The isolated MariaDB migration regression covers all three legacy attachment stores,
  unique/ambiguous/missing/foreign/unsupported sources, duplicate routing, deleted rows,
  metadata preservation, confirmed selections, and reruns.

The test VM is stopped for compilation. Configuration is restored byte-for-byte after CAISI
checks. Attachment schema cleanup first compares every original row fingerprint and refuses
DDL if any fixture remains or original row changed. The PDF test now marks its saved revision
independently of the subject to keep cleanup safe even when subject preservation regresses.
