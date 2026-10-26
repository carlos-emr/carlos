# Outbound email archive foundation

This service stores a finalized outbound email artifact as a patient eDoc, with its
SHA-256 hash and byte count. New artifacts are encrypted at rest with the archive's own
keyring; see [Encryption at rest](#encryption-at-rest-3448). It provides archive
creation, legal-hold transitions, and logical retirement with a retained tombstone. SMTP sends through `EmailManager`
(including the LOCAL provider) now archive the finalized RFC 822 message before
transport. SendGrid/API sends archive the finalized JSON request body before transport.
There is no new archive user interface.

## Installation and upgrade

Use the normal CARLOS deployment and Flyway migration procedure. The shared
`V1.0.69` migration prepares the reference tables; `V1.0.70` creates the four archive
tables and supplies the default admin eDoc deletion grant. Both Ontario and BC use
these migrations. Follow the backup and maintenance-window instructions in the
V1.0.69 header when upgrading legacy MyISAM tables.

After deployment, confirm Flyway validation succeeds and history records successful
V1.0.69 and V1.0.70 migrations. Do not bypass a failed migration to start the app.
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

The focused Java suite is `*OutboundEmailArchive*Test` (including the #3448 envelope,
keyring parser and keyring startup tests), `DocumentManagerImplFilenameValidationUnitTest`,
and `SecurityObjectSeedContractUnitTest`.
The ORM tests use H2; the DB schema workflow separately migrates real MariaDB for
Ontario and BC. `python3 scripts/verify-outbound-email-archive.py --port <test-port>`
checks real constraints, defaults, grants, and retry behavior on uniquely named,
disposable schemas. Use a test server account with schema creation/removal rights;
pass its password through `MYSQL_PWD`. The workflow also runs with case-folded table
names. The reference-engine regression is `scripts/verify-outbound-archive-engines.py`.

## Archive reads and protected document workflows

Use the Spring-managed `OutboundEmailArchiveService` for reads. Both metadata and
artifact access require `_edoc r` and access to the archive's patient. The artifact
method locks and refreshes the archive row, refuses retired records, decrypts an
encrypted artifact only after those checks pass, and verifies the plaintext size and
SHA-256 before returning any bytes. Its per-call limit is 50 MiB of plaintext. Metadata
reads initialize the document and demographic; other entity associations remain lazy.
This is a Java service API, not a new download endpoint or archive browser.

Read audits commit independently of the caller's transaction. An audit failure
prevents a successful read from returning data. For a failed integrity check, the
original `IOException` remains primary and an audit-write failure is attached as a
suppressed exception; the application also logs the archive ID. A rollback by an
outer caller cannot discard a successfully recorded event. `INTEGRITY_FAILURE`
means a verified recorded-size or SHA-256 mismatch; `READ_FAILURE` covers unavailable
files, invalid metadata/paths, read limits, and other I/O failures. A read-limit
refusal by itself is not evidence that the stored bytes are corrupt. Encrypted artifacts
add `DECRYPTION_FAILURE` and `KEY_UNAVAILABLE`; see
[Encryption at rest](#encryption-at-rest-3448).

Ordinary eDoc previews, edits, deletes, refile, split/combine, attachment selectors,
and synchronization listings protect archive artifacts and linked attachment eDocs,
including duplicate-filename aliases. Direct requests fail explicitly; listings omit
protected entries. Existing consultation/eForm links are retained when a replacement
submission omits a protected attachment. Unchanged ordinary attachments remain active;
only omitted ordinary attachments are marked deleted. Document metadata updates and inbox unlink
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
an incident. SMTP and SendGrid capture are described below.


## SMTP capture and delivery outcomes

Use the normal email compose/send workflow. SMTP now requires the sender's existing
`_email w` permission plus `_edoc w` and access to the patient's record. Missing archive
authority or archive storage failure prevents transport. Keep the document directory
writable and include archive files and database rows in the same backup procedure. Back
up the archive keyring with the server configuration: without it the archive files
cannot be read (see [Encryption at rest](#encryption-at-rest-3448)).

The sender validates configuration, prepares the MIME message and private attachment
snapshots, and commits the archive before opening SMTP transport. It suspends a caller's
database transaction while sending, so a later caller rollback cannot erase the archive
of a message already handed to SMTP. Always call the Spring-managed `EmailManager`.
The total finalized MIME message is limited to 50 MiB; attachment encoding adds overhead.
Oversized preparation fails before archive creation or transport. SMTP snapshots require
a filesystem supporting POSIX owner-only permissions; unsupported filesystems fail
before sensitive snapshot contents are written.

The archived `.eml` contains the attachment bytes actually prepared for sending, even
if their source files subsequently change. Attachment metadata records provenance and
hash/size; it does not link or lock the working source eDoc. Private temporary snapshots
are cleaned up after preparation failure, archive failure, and transport completion.
A cleanup failure is logged without replacing the delivery result.

An archive means capture succeeded, not that the recipient received the email. A failure
known to occur before acceptance keeps its archive and records FAILED. That includes a mail
server refusing the recipient address: check the patient's address before resending. Ambiguous transport
failures, including a lost SMTP acknowledgement or possible partial delivery, leave PENDING
and return an unconfirmed outcome: check the mail server and outbox before retrying.
An accepted send stays accepted even if its status or chart-note update fails; the
response flags the bookkeeping problem for follow-up. Never resend merely to repair a
status or chart note. Archive failures and SMTP failures use distinct safe diagnostic
categories, without storing or logging raw server error text or credentials.


## SendGrid capture and delivery outcomes

SendGrid uses the same archive-before-send workflow and permissions as SMTP. The JSON artifact, labeled
`outbound-email-<log-id>-sendgrid.json`, is the exact UTF-8 request body submitted to the provider,
including encoded attachment snapshots. API credentials are sent only in the authorization
header and are excluded from the artifact. Source documents remain editable.

Preparation limits the serialized JSON to 50 MiB, including base64 and JSON escaping.
Attachment reads are bounded before encoding. A provider can impose a smaller limit;
archive preparation alone does not guarantee provider acceptance. Oversized requests fail
before archive creation or transmission. Endpoint validation requires HTTPS, pins DNS,
rejects redirects and keeps the existing connection/response timeouts.

Only HTTP 202 counts as provider acceptance. Other HTTP responses are reported as rejection;
the safe diagnostic includes the status code. HTTP 401/403 calls for checking credentials
and provider permissions; HTTP 429 calls for checking provider rate limits. Do not retry
immediately. The HTTP client performs no automatic retries. A lost response is unconfirmed:
check provider activity and the outbox before deciding whether another send is needed.
Acceptance means the provider queued the request, not that the recipient received it.

Application callers must use the Spring-managed `EmailManager`. Direct `EmailSender.send()`
is refused because it cannot create the required archive. New transports must implement
`OutboundEmailTransport`, including artifact preparation, attachment metadata and cleanup;
there is no unarchived fallback for unsupported configurations.


## Encryption at rest (#3448)

Every artifact archived since #3448 is encrypted by CARLOS before it reaches the
document store. The eDoc file under `DOCUMENT_DIR` holds an encrypted envelope, not
the email: a copy of the document store cannot be read without the archive keyring.
Only the authorized read path described above decrypts it.

### How it works

- **Cipher.** AES-256-GCM with a fresh random 96-bit nonce for every artifact and a
  128-bit tag. It does not use `EncryptionUtils` or `encryption.util.secret.key`: the
  archive has its own key domain in its own file.
- **Integrity order.** The SHA-256 and byte size on the archive row are those of the
  *plaintext* that was sent. The plaintext is hashed, the ciphertext is stored, and a
  read authorizes first, then authenticates and decrypts, then checks the plaintext
  size and SHA-256. No bytes are returned unless every step passes, and no plaintext
  temporary file is written. The deletion tombstone keeps the plaintext hash too.
- **Key id in the stored bytes, not in the database.** No column records whether an
  artifact is encrypted or which key sealed it. The envelope header says so:

  | Offset | Bytes | Field |
  |---|---|---|
  | 0 | 8 | Marker `89 43 45 41 0D 0A 1A 0A` |
  | 8 | 1 | Format version, `1` |
  | 9 | 1 | Algorithm, `1` = AES-256-GCM |
  | 10 | 4 | Key id, big-endian |
  | 14 | 12 | Nonce |
  | 26 | n | Ciphertext, the same length as the plaintext |
  | 26+n | 16 | GCM tag |

  An unknown version or algorithm is refused, never guessed. An encrypted file is
  exactly 42 bytes longer than the recorded plaintext size.
- **What the tag protects.** The whole header, plus the archive row's `EmailLog` id,
  patient, plaintext size, plaintext SHA-256 and content type. Changing any header
  byte, editing those row values, or copying the ciphertext to another archive row
  fails authentication. The archive row id itself is not bound, because the file is
  written through the eDoc store before the row exists; two rows could only swap
  ciphertext undetected if they recorded the same plaintext.
- **Scope.** The encrypted artifact is the exact message or request that was sent,
  attachments included. A separately linked attachment eDoc (optional, and not used
  by the SMTP or SendGrid capture) is an ordinary document and is not encrypted.
- **Artifacts from before #3448 stay plaintext** and remain readable. They are
  recognised by the absence of the marker (an RFC 822 message or a JSON document
  cannot start with byte `0x89`). They are not converted; see
  [Not done yet](#not-done-yet).

### Deployment prerequisite: encrypted volumes

Application-level encryption does not replace disk encryption. Before the archive
holds real patient email, put the document store (`DOCUMENT_DIR`), the database data
directory, the archive keyring and the backup repository on encrypted volumes
(LUKS/dm-crypt, or the cloud provider's volume encryption), and keep the owner-only
permissions CARLOS and the Debian package set. Disk encryption covers a stolen disk
or volume snapshot; the archive keyring additionally covers a copy of the document
store restored somewhere else without the server configuration. The email subject,
recipients and other `EmailLog` columns are ordinary database content and are covered
by disk encryption only.

### The archive keyring

CARLOS reads the keyring from, in order:

1. the environment variable `CARLOS_OUTBOUND_EMAIL_ARCHIVE_KEYRING_FILE`;
2. the property `email.archive.keyring.file`;
3. `<context>-outbound-email-archive.keyring` in the home directory of the user
   CARLOS runs as, next to the `<context>.properties` file: for the usual `carlos`
   context, `carlos-outbound-email-archive.keyring`. The context name keeps two CARLOS
   webapps in one Tomcat from sharing a keyring.

The Debian package sets the environment variable in `carlos-emr.service` to
`/etc/carlos-emr/archive-keyring/outbound-email-archive.keyring`. That directory is
`0700 carlos:carlos` and is the only place under `/etc` the service may write.

On a fresh install CARLOS creates the keyring on first start, owner-only (`0600`),
and logs a WARN asking for it to be backed up. Creation never replaces an existing
file: if another process created the keyring in the meantime, CARLOS loads that one.
The file is plain text:

```text
format=1
current=2
key.1=<Base64 of 32 random bytes>
key.2=<Base64 of 32 random bytes>
```

`current` names the key that encrypts new artifacts. Every other key is kept for
older artifacts. Never delete or change a `key.N` line, and never reuse a number
for different key material.

**Permissions.** At every start, a keyring that users other than its owner can read is
restricted to `0600`, with a WARN advising a rotation in case someone already read it.
CARLOS also warns when users other than its owner can write to the keyring's directory,
since they could replace the file. Both are warnings, not refusals: a mounted secret may
be owned by another account in a directory CARLOS cannot change.

**Backups.** The keyring MUST be backed up with the server configuration, and a copy
kept off the server; without it every encrypted archive is permanently unreadable.
On a Debian install it is inside `/etc/carlos-emr`, so the nightly `carlos-emr-backup`
`files` snapshot already carries it together with `carlos.properties`. Other
deployments must add it to their configuration backup themselves. Restore it
**before** starting CARLOS. After every key rotation, back it up again: artifacts
sealed with the new key need the new file.

**Restore drill.** On a Debian install the weekly `carlos-emr-backup verify` drill
fails, and alerts, when the restored database holds archived emails but the newest
`files` snapshot has no keyring. For a full drill, restore the configuration,
including the keyring, the database and the document store (`DOCUMENT_DIR`) to a
scratch host and start CARLOS. A clean start shows the keyring was restored and parses.
Look for the INFO line
`Outbound email archive keyring <path> opened archived email N (key K), the most recent
encrypted one it could open.`: it proves the keyring opens archive N. Compare N with the
newest archive id in the restored database; a gap means newer archives were not tried.
A WARN that recent archived emails could not be tried means the document store is
missing or incomplete, even when an older archive opened. Without either line, and
without an ERROR or WARN about the keyring, no encrypted archive was there to try. It does not prove every historical artifact
decrypts; until an archive read entry point exists (#3222), that needs a developer to
call `readArchivedArtifact` for a sample.

### Startup

| Situation | What CARLOS does |
|---|---|
| Keyring present and valid | Loads it; logs `Loaded the outbound email archive keyring from <path>: current key N, keys [..].` It decrypts, in memory, the newest encrypted archive (of the 20 most recent rows) whose key id the keyring holds, trying older ones until one opens, and logs an INFO naming the archive and key. If newer archives under keys it holds failed first, it logs an ERROR (damaged files, or a second keyring with the same ids, such as another server's), unless they include the current key id or a newer one and the archive that opened uses another key: that is refused (next row). A file that is missing, unreadable, the wrong size or over 50 MiB, a damaged header, a row with incomplete metadata, or a cryptography provider fault is skipped with a WARN; a database read failure is a WARN too. None says anything about the keys. When nothing among the 20 could be tried (every encrypted one uses a key the keyring lacks, or their files are missing or damaged), older rows are searched, header by header, for archives sealed with a key it holds, and tried the same way. If the newest encrypted archive uses a key the keyring lacks, it also logs an ERROR suggesting an out-of-date copy was restored (a WARN when that key is older than the current key and an archive opened, as after an acknowledged loss), but still starts. |
| Keyring present, holds the archives' key ids, but archives under its current key id (or a newer one) do not open | Refuses to start when a failing archive uses the current key id or a newer one, even if an archive under an older key opens: it is a different keyring with the same ids (one created on a fresh start, or by another server sharing the document store), or the files were damaged or altered, and new archives would be sealed with a second, different key under an id already in use. With `acknowledge_loss` set **and** `rotate_to` above the failing key ids and every key id the keyring holds, it starts with an ERROR and rotates; `acknowledge_loss` alone is refused. If that rotation is itself refused because some archive files cannot be read, move the keyring file aside and set `acknowledge_loss` alone: a new keyring is created at a random high key id. When every failing key is older than the current key (lost keys rotated past), it starts with an ERROR. |
| Keyring present but unreadable or malformed | Refuses to start, always. It never replaces an existing keyring file. |
| Keyring missing, no archived artifact encrypted | Creates it (a fresh install, or an upgrade from plaintext-only archives). |
| Keyring missing, encrypted artifacts found | Refuses to start with one ERROR naming the fix. |
| Keyring missing, a stored file could not be checked, or the database could not be read | Refuses to start: encrypted artifacts cannot be ruled out. |

When the keyring is missing, the check reads only the database's archive rows and the
first bytes of each stored file, newest first, and stops at the first encrypted one.
(The trial open of a present keyring reads and decrypts whole recent files, in memory.
It writes no read-audit row, since no person reads the artifact, and keeps and logs
nothing it decrypted.) A missing
`outboundEmailArchive` table counts as a fresh install. The refusal reads, for
example:

```text
Outbound email archive keyring /etc/carlos-emr/archive-keyring/outbound-email-archive.keyring
is missing, but archived emails in the database are encrypted with it (2 archived emails;
encrypted artifacts found). Refusing to start: a new keyring cannot decrypt them. Fix:
restore the keyring file from backup to <path> (or point email.archive.keyring.file or
CARLOS_OUTBOUND_EMAIL_ARCHIVE_KEYRING_FILE at it), then restart. Only if the keyring is
lost for good: set email.archive.keyring.acknowledge_loss=true and restart. CARLOS then
creates a new keyring, and every archived email encrypted with the old one stays unreadable.
```

**If the keyring is lost:**

1. Restore it from backup to the path in the message, owned by the CARLOS user with
   mode `0600`, and restart.
2. Only if it cannot be recovered, set `email.archive.keyring.acknowledge_loss=true`
   (`yes` and `on` also work; the same style as `encryption.util.secret.key.acknowledge_loss`,
   #4098, pending) and restart. CARLOS creates a new keyring and logs one ERROR. Its
   first key avoids every key id found in a surviving archive, so if the old keyring
   turns up later the `key.N` lines that sealed those archives can be copied back into
   the new file (a lost key that sealed nothing still on disk may share an id with a new
   one; it is not needed). When every archived file could
   be read, the first key is one above the highest key id found. When the check was
   incomplete (the database or some files could not be read), the lost ids are not all
   known, so the first key gets a random id between 16777216 and 1073741823 and a WARN
   says so. Reading an archive sealed with a lost key fails with an audited
   `keyUnavailable`.
3. Back up the new keyring, then remove `email.archive.keyring.acknowledge_loss`.
   It applies on every start while it is set, with a WARN each time, so it must not be
   left in place.

Logs never contain key material, file content or archive content: they carry the
path, key ids, archive ids, counts, property names and exception class names.

### Rotating the archive key

Rotation makes a new key encrypt new artifacts. Old keys are kept permanently, so
every existing artifact stays readable. It does **not** re-encrypt existing
artifacts, so it does not protect artifacts already sealed with a compromised key.

1. Back up the current keyring file.
2. Set `email.archive.keyring.rotate_to=N`, where N is the current key id plus one,
   and restart. Before generating key N, CARLOS reads every archived file's header
   once. If an archive already uses key N or higher and the keyring lacks that key,
   the keyring is an out-of-date copy: CARLOS refuses to start rather than create a
   second, different key N, and names the fix (restore the newest keyring). Missing,
   unreadable or malformed archive headers also stop rotation until every key id
   can be checked. Otherwise it generates key N, makes it current, keeps every other key, writes the file
   atomically (owner-only) and logs
   `Rotated the outbound email archive keyring at <path>: key N now encrypts new archived emails, ...`.
3. Back up the new keyring file straight away.
4. Remove the setting. Left in place it does nothing: once key N is current, a later
   restart does not rotate again. A lower number than the current key is ignored with
   a WARN; a value that is not a positive whole number stops startup.

If CARLOS cannot write the file (for example a read-only secret mount), rotate by
hand instead: generate a key with `openssl rand -base64 32`, add it as a new
`key.N=` line with an unused N, set `current=N`, and restart. CARLOS checks the file
strictly: a duplicate, an unknown line, a key that is not 32 bytes, or a `current`
that names no key stops startup and leaves the file untouched.

Automatic rotation uses a stable sibling `.lock` file and reloads the keyring while
holding an exclusive filesystem lock. The directory must be writable, and the
filesystem must support locks shared by every process using it. Leave that lock
file in place. Ordinary reads of a mounted keyring do not need a writable lock file.

For multiple CARLOS servers sharing one document store, create the keyring once, on
the first server, and copy it to every other server before that server first starts:
two servers starting without a keyring would each create a different key 1. Stop sends
on every server before rotating, then restart all servers with the updated keyring
before resuming sends. Running servers keep their
startup keyring in memory and cannot read a newly added key until restarted. Manual
keyring edits must also happen while every server is stopped.

**Compared with the patient portal (#3207 / #3220, carlos-emr/carlos-portal).** Taken
from the portal: AES-256-GCM with a random nonce per record, a keyring of retained
keys plus an active key id selecting the key for new writes, reads selecting the key
by each record's stored key id, associated data binding the record's context
(patient, type, key id, record context), and "never reuse a key id for different key
material". Not taken: the portal keeps its keyring as a JSON environment variable plus
an `*_ACTIVE_KEY_ID` variable and stores key id, nonce and algorithm in database
columns; CARLOS uses its own file (which it can create and rotate) and a versioned
header inside the stored bytes, with no new column. The portal also derives its AES
keys with HKDF from operator-supplied secrets; the archive keyring holds random 256-bit
keys directly. The portal ships a batch re-encryption command
(`rotate-unlock-secrets`) and retires old keys once nothing references them; the
archive has no re-encryption job yet, so its old keys are never retired.

### Read failures

Encrypted reads add two audit events to those listed above:

- `readArchivedArtifact.decryptionFailure`: the envelope failed authentication, is
  malformed or of an unsupported version, or the cryptography provider failed. Either the stored file or the row's
  recorded values changed, or the key under that id is not the key that sealed it
  (the wrong keyring restored). Stop using the artifact; compare the file and the
  keyring with backups. Do not edit the recorded hash or size to silence it.
- `readArchivedArtifact.keyUnavailable`: the key id in the envelope is not in the
  keyring. Restore the keyring version that holds that key.

The application log line is
`Outbound email archive artifact read failure archiveId=<id> failureType=<type> event=<event>`.
A successful read of a pre-#3448 plaintext artifact logs a WARN,
`Outbound email archive artifact archiveId=<id> is stored unencrypted (archived before #3448); ...`,
so the plaintext still on disk stays visible.
A plaintext size or SHA-256 mismatch after a successful decryption is still
`integrityFailure`.

### Capacity and growth (operational note)

Retention is permanent, so the archive only grows. Every send stores the complete
RFC 822 message or SendGrid request, with every attachment, up to 50 MiB each.
Encryption adds 42 bytes per artifact, but encrypted bytes do not compress or
de-duplicate: the same PDF sent to many patients would de-duplicate in the restic
repository as plaintext, and encrypted it costs its full size every time.

- **Estimate:** emails per day x average artifact size x 365 per year, plus the
  backup copies your retention keeps. For example, 200 emails a day at 300 KiB is
  about 21 GiB a year before backups.
- **Measure:** `SELECT YEAR(archivedAt), COUNT(*), SUM(byteSize) FROM
  outboundEmailArchive GROUP BY YEAR(archivedAt);` gives plaintext bytes per year
  (stored size is 42 bytes more per row).
- **Watch:** alert on the `DOCUMENT_DIR` volume and the backup repository at, say,
  70% and 85% full, and review growth each quarter.
- **Tiering:** not implemented. Archive files are kept in `DOCUMENT_DIR` with the other
  documents; moving older ones to cheaper storage needs its own design before the
  archive carries production volume.

### Not done yet

- **Converting pre-#3448 plaintext artifacts.** They stay plaintext and readable. A
  restartable, batched re-encryption job (and a production cutoff after which no
  plaintext archive remains) is a follow-up.
- **Re-encryption on rotation and retiring old keys.** Keys are kept forever.
- **Metrics** for plaintext artifacts remaining, decryption failures, unknown key ids
  and rotation progress. Today these are only visible in the audit log.
- **A KMS or HSM.** Keys live in a file protected by file permissions.
- **Streaming (#3211).** A write or read holds the plaintext and the ciphertext in
  memory at once, bounded by the 50 MiB limit.
- **Refusing an out-of-date keyring at startup.** A copy that lacks the newest
  archive's key is reported at startup (an ERROR, or a WARN when that key is older
  than the current key and an archive opened), not refused. (A keyring holding
  different key material under the same ids is refused; see Startup.)
- **Rotation through the admin job framework.** Rotation is a startup property
  (`email.archive.keyring.rotate_to`) applied at the next start, not a batched,
  restartable admin job, and it does not re-encrypt anything.
- **An explicit end to plaintext reads.** Pre-#3448 plaintext artifacts are read
  without decryption for as long as they exist; each such read logs a WARN naming the
  archive id. There is no switch that refuses them yet.
