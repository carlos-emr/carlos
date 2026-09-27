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

The final Java run passed 13,580 tests (zero failures/errors; 51 existing skips), with
1,103/1,330 changed executable Java lines covered. All 1,051 Node tests passed, including
five regressions for toolbar timing and default/explicit template subject synchronization.
All 984 JSPs compiled, WAR/Javadoc and all three DEBs built, and encoding, BDD, i18n and
security lints passed. Java regressions cover source collisions, permissions, real Spring
bean wiring, Hibernate query-time flush, atomic tickler rollback, nullable consultation
rendering, and exact subject text for templates without a control.

Ubuntu 26.04 iteration 6 passed 6,700 payload and installed-file comparisons and health checks.
The final four-check browser run passed health, consultation source rows, tickler attachments
and eForm attachments with zero failures/skips. The eForm check preserves a subject containing
quotes, ampersands and angle brackets through reopen and the PDF's saved revision; the merged
PDF was 782,877 bytes. Iteration 4 also passed the neighboring and CAISI scenarios below;
these paths were unchanged by the final subject JavaScript fixes.

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

Both final installed migrations (V1.0.37 and V1.0.38) succeeded. The final isolated migration
regression passed again. After all final browser checks, cleanup verified every original
attachment row fingerprint, removed only this PR's test schema, and stopped the VM. No fixture
rows or configuration changes were retained.


## Follow-up review and installed validation

Preserved and reviewed the six additional remote commits through `a557517429`.
All twelve CodeRabbit findings were checked, including case-normalized migration
sources, legacy event construction in all three subject assets, atomic toolbar selection,
patient-scoped cleanup, permission diagnostics, retained restricted attachments,
multiple lab-routing ownership rows, REST patient lookup, and BDD names.

Own review found that looking up the stored response patient after conversion was too
late: conversion could already overwrite it. Both consultation converters now reject
patient reassignment before changing managed clinical fields (#4045). Responses retain
their stored patient when the demographic object is omitted; missing records or required
metadata produce explicit validation failures. The managers' audit messages no longer
dereference absent records. Duplicate response selections are compared once per source
and ID, preserving distinct HL7/MDS rows. Request updates preserve omitted/null attachment
fields and detach an explicit empty selection. A private presence flag retains the DTO's
legacy empty-list getter default without exposing an extra JSON property.

Shared follow-up fixes from #3994 preserve stable priority values with translated labels
(#4043), avoid logging successfully entered suggested text as an error (#4044), enforce
exact POST methods with Allow headers, retain available program choices without a current
selection, and remove all links owned by test ticklers while retaining excluded notes.

Validation:

- Full Java suite: **13,613 tests**, zero failures/errors, **51 existing skips**. The final
  focused manager/REST run passed **103 tests**, including real Jackson deserialization of
  omitted, null and empty selections. The installed omission test first reproduced the
  DTO-default defect against iteration 7, then passed against iteration 8.
- Full Node suite: **1,060 passed**. Behavioral tests cover all three subject assets with
  and without the Event constructor, atomic toolbar rejection, and scoped fixture cleanup.
  BDD naming (275 files), encoder and security-message checks passed.
- **984 JSPs**, WAR and Javadocs built. All three DEBs
  `2026.08.0~alpha16~pr3996.8` installed on Ubuntu 26.04; **6,700 packaged and installed
  files** matched the tested build, and application health checks passed.
- Eight installed consultation REST scenarios passed: patient-change refusal, omitted
  response patient, source collision/deduplication, repeated-save row retention, invalid
  lab-source transaction rollback, missing metadata, request field-presence semantics,
  and deleted response refusal. Owned fixtures were removed.
- Consultation lab rows, interactive request creation, tickler attachments, eForm saved
  selections/subject and merged PDF (782,878 bytes), and normal/French tickler validation
  passed. CAISI attachment and validation/French scenarios also passed. Neighboring tickler
  CRUD, note dialog and patient list checks and all four rendered program-selector cases passed.
- Isolated MariaDB checks passed mixed-case V1.0.37 source normalization, ownership,
  metadata and rerun behavior; all three V1.0.38 attachment stores; and the actual shipped
  demo inserts against the widened schema.

The original configuration was restored byte-for-byte. All **327 original note-link rows**
and every original attachment-table row retained their exact fingerprints. Fixtures and
selector probe were removed, the temporary attachment schema was restored, final health
and installed-file verification passed, and the VM was stopped with zero automatic restarts.
