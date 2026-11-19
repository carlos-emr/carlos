# Day-sheet rendered appointment selection (#5956)

New-mode preview and printing remain read-only. The explicit acknowledgment POST
now carries the displayed appointment IDs and edit versions. A fresh query still
applies report scope; only matching submitted versions reach the existing service,
which rechecks each appointment under its database lock and archives/updates in
one transaction. Appointments booked or edited after preview remain unchanged.
The result reports acknowledged and skipped counts. Repeated submissions cannot
acquire newly booked appointments or create duplicate archives.

The submitted count must exactly match the unique parsed IDs. Missing, malformed,
duplicate, truncated or more-than-10,000-row selections return application HTTP
400 before any mutation. Operation/filter fields precede the selected pairs in
the form, so parameter-limit truncation cannot turn the acknowledgment into a
successful filter request. Reduce the report scope if a selection exceeds the
server request limits. Legacy unversioned timestamps remain supported. Report
and appointment-write authorization and CSRF checks retain the separate #5963 fix.

## Real installed reproduction

On Ubuntu 26.04 installed build 5c3dc515543c646cbdbdada2a12c610292c1d6a0, Chromium 154.0.8025.0
opens the report through Schedule > Report. The new-mode preview shows two owned
appointments without changing their rows or archives. Before submitting its
actual form, the probe books a third appointment and edits a previewed row.
The old JSP acknowledges all three: the newly booked and edited appointments
both become T and each gains an archive, despite not representing the preview.
The intended negative assertion fails. Raw JUnit SHA-256:
`4b3b56c6a764f6fbeec1b0c4e5ae91b84dcf34df6ed57c19a1fd108e10de39d7`.
The actual browser/SQL/residue guards remain active; no residue remains,
properties/restarts are unchanged and service health passes. The probe uses a
separate exact script beside the immutable installed source, with no production
class substitution. Its metadata wrapper is retained with the before evidence.

## Source validation

All 24 selection cases pass, including unchanged/stale/new IDs, fresh order,
null legacy timestamps, negative epoch values, duplicates, missing/mismatched
counts, malformed/overflow values and the exact size boundary. JUnit SHA-256:
`c978f6a460d2de472ed60a2f68347d0582760c981b50463281da4f054d60fde2`. The new Main helper has 100% changed-line, instruction
and branch coverage. Source-bound evidence SHA-256:
`da6bd92d05d33dca04dbbdb7c32e92aa91080d4ed69a16e8f379423f0f20f5e1`.

The actual transactional service/DAOs pass all five cases on MariaDB 11.8.8 with
32 KiB pages, including native projection, archive rollback, selection after
booking/editing and repeated acknowledgment. Physical JUnit SHA-256:
`8fdb0dbe906d2b32a1f6ccdb09e5b7804376ace4c715d8e513714ddcfbb0ed44`. The owned schema/server are retired. H2 passes its
four applicable cases and skips the pre-existing MariaDB-only projection case;
its five-discovered-case report is `1763b4b36b58e1a81991bb9f95a3a1dc7703c6690f3231967775e058a54ddeec`. No H2 claim substitutes
for the complete physical run.

The BC Flyway CI lane already selects the service suite; its verifier now
requires five cases and the new exact transactional case identity. All 26 verifier
controls pass. All 70 browser manifest/runner controls pass. The native core check
requires the repaired installed behavior, exact acknowledged/skipped counts,
application 400 without mutation for an incomplete selection, and safe replay.
Its installed after run is pending the next combined DEBs. Earlier fixture-only
commands lacking sparse-checkout sources/docs are retained as harness failures,
and are not product regression evidence.
