# Outbound email archive foundation

This service stores a finalized outbound email artifact as a patient eDoc, with its
SHA-256 hash and byte count. It provides archive creation, legal-hold transitions,
and logical retirement with a retained tombstone. This PR does **not** connect the
SMTP or SendGrid sending flows or add an archive user interface. Installing it does
not start archiving outgoing email automatically.

## Installation and upgrade

Use the normal CARLOS deployment and Flyway migration procedure. The shared
`V1.0.27` migration prepares the reference tables; `V1.0.28` creates the four archive
tables and supplies the default admin eDoc deletion grant. Both Ontario and BC use
these migrations. Follow the backup and maintenance-window instructions in the
V1.0.27 header when upgrading legacy MyISAM tables.

After deployment, confirm Flyway validation succeeds and history records successful
V1.0.27 and V1.0.28 migrations. Do not bypass a failed migration to start the app.
Earlier development versions of these archive tables require a rebuild of the
**disposable development database**, or an explicit schema reconciliation that
preserves its data. `flyway repair` adjusts history; it does not update old columns,
constraints, or tables to match the current SQL.

The `_admin.edocdelete` grant also affects the existing eDoc undelete action: an
administrator receiving the new grant can undelete documents created by other
providers. Existing clinic-specific grants or denials are preserved, so a customized
installation may still deny archive retirement. Check both this privilege and access
to the patient's record when investigating an authorization failure.

## Service behavior and limits

- Creation requires eDoc write authority and access to the patient linked to the
  persisted email log. Call the Spring-managed service so its transaction applies.
- Every new archive starts under legal hold. An authorized administrator must release
  the hold with a reason before retirement. Release, reapplication, and retirement
  record the responsible provider and a timestamp.
- `recordControlledDeletion` retires the archive logically. It retains its eDoc row,
  file, attachment metadata, and a tombstone containing the artifact hash and size.
  Ordinary document listings hide it, and direct ordinary-document operations refuse
  it. Retirement does not erase its bytes.
- Attachment entries describe the finalized message's attachments. For a linked
  eDoc, the service reloads its stored filename, checks read authority and patient
  ownership, and verifies both hash and size against its stored bytes before creating
  the archive. A mismatch or missing/unreadable file aborts creation. Use a persisted
  eDoc containing the finalized attachment (including any encryption/transformation).
  These reads use a bounded buffer and do not create separate attachment eDocs.
  Metadata-only external entries have no eDoc link; their provenance remains caller
  asserted. Never use `sourceDocumentId` as an authorized document lookup.
- The append-only audit entities prevent ordinary JPA update/removal. This is not
  filesystem immutability or protection against direct administrative SQL changes.

## Failure investigation

A transaction rollback removes the newly written artifact. If cleanup fails, the
application logs `Orphaned outbound email archive artifact left in DOCUMENT_DIR`
with its generated filename. Reconcile the file against the database before removing
it. An unknown transaction outcome deliberately retains the file; check whether the
archive committed before deciding what to do.

Concurrent hold/retirement requests can be rejected as transaction conflicts. Reload
current state and recheck the requested action in a fresh transaction. MariaDB's
snapshot isolation can reject a locking read after another transaction changed the
row; this fails closed rather than acting on the old hold state.

The archive, legal-hold events, and tombstones are the durable audit records stored
in the same transaction as the state change. Failure to persist them propagates for
rollback. For those state changes, `OscarLog` is a secondary activity index delivered
asynchronously. Archive reads instead commit their `OscarLog` access evidence
synchronously in a separate transaction before returning metadata or bytes.

If `Outbound email archive committed but audit logging failed` appears, use the
logged archive ID to reconcile the durable archive, legal-hold event, or tombstone
with the audit log. Do not retry the completed state change merely to recreate its
secondary audit entry.

## Developer validation

The focused Java suite is `*OutboundEmailArchive*Test`,
`DocumentManagerImplFilenameValidationUnitTest`, and `SecurityObjectSeedContractUnitTest`.
The ORM tests use H2; the DB schema workflow separately migrates real MariaDB for
Ontario and BC. `python3 scripts/verify-outbound-email-archive.py --port <test-port>`
checks real constraints, defaults, grants, and retry behavior on uniquely named,
disposable schemas. Use a test server account with schema creation/removal rights;
pass its password through `MYSQL_PWD`. The workflow also runs with case-folded table
names. The reference-engine regression is `scripts/verify-outbound-archive-engines.py`.

## Archive reads and protected document workflows

Use the Spring-managed `OutboundEmailArchiveService` for reads. Both metadata and
artifact access require `_edoc r` and access to the archive's patient. The artifact
method locks and refreshes the archive row, refuses retired records, and verifies
size and SHA-256 before returning any bytes. Its per-call limit is 50 MiB. Metadata
reads initialize the document and demographic; other entity associations remain lazy.
This is a Java service API, not a new download endpoint or archive browser.

Read audits commit independently of the caller's transaction. An audit failure
prevents a successful read from returning data. For a failed integrity check, the
original `IOException` remains primary and an audit-write failure is attached as a
suppressed exception; the application also logs the archive ID. A rollback by an
outer caller cannot discard a successfully recorded integrity event.

Ordinary eDoc previews, edits, deletes, refile, split/combine, attachment selectors,
and synchronization listings protect archive artifacts and linked attachment eDocs,
including duplicate-filename aliases. Direct requests fail explicitly; listings omit
protected entries. Existing consultation/eForm links are retained when a replacement
submission omits a protected attachment. Document metadata updates and inbox unlink
requests require POST.

A linked attachment becomes protected too. Pass a dedicated persisted copy of the
finalized attachment to archive creation; do not link a working clinical document
that must remain editable or selectable. The service does not create these copies,
and ordinary document access (including reuse through that interface) is refused once
linked. Metadata-only external attachments have no stored file protection.

For a refusal, use an authorized archive integration; changing eDoc status or releasing
a legal hold does not enable ordinary document access. If no archive integration is
installed, ask the application administrator rather than altering records directly.
For integrity failures, stop using the artifact and reconcile the archive ID, recorded
size/hash, stored file, and backups. Do not replace the recorded hash to silence an
error. Preserve both the integrity error and any suppressed audit failure when reporting
an incident. This PR does not wire archive creation into SMTP or SendGrid delivery.
