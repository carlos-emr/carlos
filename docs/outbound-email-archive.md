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
  It does not hide the eDoc from the ordinary document browser or erase its bytes.
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
rollback. `OscarLog` is a secondary activity index and is delivered asynchronously.

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
