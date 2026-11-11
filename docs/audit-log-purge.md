# Audit log purge and recovery

The administrator's Audit Log Purge form requires `_admin.auditLogPurge` write
permission and a CSRF-protected POST. Opening the form is read-only. Dates are
strict `yyyy-MM-dd` calendar dates. Purge includes the entire selected day and
preserves the following midnight. `log.purge.minDays` retains its default of
3650 days; missing/future dates and negative or malformed retention settings refuse the operation.

`log.purge.mysqldump` selects an executable, without shell arguments. Set
`log.purge.outputdir` to an existing private backup location; otherwise
`DOCUMENT_DIR` is used. The service creates a private directory per attempt,
with mode 0700 and files mode 0600. Treat these files as clinical audit data.
The database name, active host/port, credentials and TLS policy come from the
application's live datasource. A plain `db_name` no longer affects Spring bean
construction. Credentials travel in a temporary client options file, never
command arguments or application logs; that file is removed as the operation
unwinds. If removal fails, the operation reports failure and logs its protected
path so an operator can remove it.

The operation holds a database-wide named purge lock. It captures the highest
existing ID, then locks up to 1000 eligible rows at a time. It dumps that exact
ID set, verifies every ID appears exactly once, flushes the SQL file and its
SHA-256/ID manifest to durable storage, and only then deletes and commits the
same locked rows. A random server lock in the dump predicate proves the dump
read the same database server. New records beyond the initial ID bound remain
for a later run. Writers to selected rows can wait while a cohort is backed up.

The installed MariaDB or MySQL dump client must support the options emitted by
this service. TCP and local Unix sockets are supported. Required TLS includes
a dump-session cipher check, so an older client's opportunistic TLS fallback
cannot authorize deletion. Java-only socket factories, key/trust stores and
cipher-suite lists, and CA-only verification with MariaDB clients, refuse the
operation before deletion when their policy cannot be faithfully translated.
Use a supported PEM CA configuration rather than weakening TLS. Dump processes
have a bounded execution time; an unsuccessful or incomplete dump never permits
deleting its cohort. The public manager method and general-purpose DAO purge
signature remain available.

If a run reports failure, some earlier cohorts may already have committed.
Preparation failures name the setting stage and confirm that no rows were deleted.
Later failures record the confirmed deletion count and retained backup path,
without raw client/SQL errors or record values. The form reports failure even
when an exception has no cause. A secondary rollback, process-reaping or
credential-cleanup error does not replace the original failure. Session cleanup
failures trigger connection invalidation/abort attempts and an explicit message
to inspect datasource health before retrying. Inspect the private SQL, manifest and stderr
files before retrying; `.partial` files are not certified backups. Verify the
manifest hash, test restoration in a separate database, and reconcile IDs with
the live log. Completed backups are retained on both success and failure. A
retry selects only rows still present and never overwrites a previous attempt.
Back up and retain these artifacts according to the clinic's audit obligations.
