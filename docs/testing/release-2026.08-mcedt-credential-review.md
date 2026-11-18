# MCEDT credential review follow-up

The final review revision stores ciphertext in `mcedt_credential`, separate from
opaque legacy `property` values. Migration `V1.0.59.51` seeds its single record
before application traffic and adds a non-unique index on `property.name`.
The primary key and check constraint protect the stable lock target, including
concurrent first-ever password saves across application instances. LONGTEXT
retains the encrypted representation of the former 4,000-character capacity,
including supplementary Unicode. Larger submitted passwords are refused before
storage changes.

Reads and writes lock the singleton. A save or a first read that can migrate a
legacy value also takes a locking read of legacy rows in ID order. After
migration, ordinary reads discover any obsolete rows without a locking range
scan and delete them by ID and property name. A first read treats every legacy
value as plaintext, including a literal `{ENC}` prefix. The first legacy row
supplies the credential and the transaction removes every legacy duplicate.
Already migrated ciphertext is authenticated before use. Missing or blank
credentials keep the configured fallback. Change Password records the provider
and timestamp when it replaces the encrypted value and removes legacy rows.
The seed records the migration actor, and lazy legacy conversion records the
system actor. The failure page and application warning use fixed messages.

All 34 selected Java 25 cases pass: eight unit controls and 26 real H2 transaction
cases. The same 26 transaction cases pass on a disposable MariaDB 11.8.8 server
with 32 KiB pages. Each case owns a UUID database; cleanup never deletes a
credential from a shared fixture or application database. Lock assertions first
observe the competing operation in the database, then check it remains pending
until the writer commits. Both first-save serialization and a concurrent legacy
writer are covered. A held credential transaction also permits an unrelated
preference update to commit before its credential locks are released.

The first physical runs exposed a real MariaDB `HY000/1020` failure after the
legacy writer committed. An ordinary encrypted-value SELECT had opened a
repeatable-read snapshot before the legacy locking read. That intermediate
implementation used current locking reads for both values. The failed physical
reports are retained. Its corrected 23-case retake passed with zero failures,
errors and skips.
The actual migration also passes independently: EXPLAIN selects
`idx_property_name`, LONGTEXT is reported, and inserting a second singleton ID
is rejected by its constraint. All test databases and the owned server are
retired. All 10,755 original installed application files and configuration are
independently retaken exact.

- Final H2/unit JUnit SHA-256: `6bdc0873d667fa62fb1592606f807cb06ed90cc5c0c024711874e9c3a5655106`.
- Final MariaDB JUnit SHA-256: `550dc3b0894da582ecf904b64ac3cc90c5c54220face78f2fe0fc01a1e85c30b`.
- Actual migration SHA-256: `2d4f0eec60513259fd8e54a38354abd8fd14575eeed1b3b18ec2f41ce5264b95`.
- Fresh source-bound coverage evidence SHA-256: `3e028a7aca10e02c6742d370616d8a15062dbde9fa4fd41aa402e136718ed3f8`.

The new credential service and changed action lines have 100% line, instruction
and branch coverage. Delegate floors rise to 90% / 91.2% / 100%; its existing malformed-keystore-URL
diagnostic and the required-JDK-algorithm-unavailable diagnostic remain uncovered. The retired service's
historical evidence is preserved; its floor is replaced by complete evidence
for the renamed service.

The public client factory checks the committed credential before reusing a cached
client. Its private cache retains only a SHA-256 credential fingerprint alongside
the client; it does not log or expose the fingerprint. Real H2 and MariaDB tests
prove that both this service's committed rotation and a committed update from
another instance replace the cached client and its WS-Security password. A rolled
back rotation preserves the existing client, and unchanged credentials reuse it.
Explicit refresh still rebuilds clients for certificate/configuration changes.
Reinstating the former cache-once lookup behavior makes all three rotation
regressions fail; its report is retained (SHA-256
`fd6cb500c7c20ba51b2dfe5f44fd54ab9ed1ad9f13087c63be1fb92218f9c8c3`).
No Ministry calls are made by these client-construction tests.

The earlier three-workflow installed component proof in the original document
belongs to the pre-review property-based format. It does not qualify this revised
format. The revised native checks now preserve the encrypted record in a
server-side owned backup, verify both password changes without reading a
credential, exercise the visible storage-failure alert and restore exact state.
Their rebuilt-DEB qualification is recorded below. No Ministry transport or
authentication is exercised.

Upgrade while application instances are stopped, as the packaged service upgrade
does, and upgrade every instance before resuming MCEDT use. Older code reads only
plaintext properties and cannot consume ciphertext. Retain the installation
encryption key with its credential backups and use compatible code and schema
when restoring them.

After every instance is upgraded, complete a credential read in the application
or save the current password through Change Password. The schema upgrade seeds
the singleton. The first successful application read or save removes the
legacy plaintext. Verify cleanup with this count, which should return zero.

```sql
SELECT COUNT(*) FROM property WHERE name = 'mcedt_account_password';
```

Backups taken before cleanup retain the bytes captured at the time.

The complete post-review Node retake passes all 8,111 cases across 570 files
on Node 22.23.5 with zero failures, skips, cancellations or TODOs, and every
test-source fingerprint unchanged. The generated inventory is byte-identical
to the tracked inventory. The original one-case failure (missing migration
merge-order registration) is retained; the ledger is corrected and all five
migration-order controls pass. Final Node report SHA-256:
`a622abac48d99d12fa96845233e30296f8984a8437f73aa06268ed47661b5a01`.

The first combined DEB build stopped at the schema-contract guard, before any
DEB was produced. Both ON and BC reference schemas were then migrated from
empty databases and validated on MariaDB 11.8.8 with 32 KiB pages. Their exported
contracts add only the singleton table and property-name index: every prior
physical definition is unchanged. The generated contract hashes, CDS catalogue
and its descriptor binding are refreshed together. The new global credential
table has an explicit OPERATIONAL ruling and is excluded from every patient
export category. All 10 catalogue/resource cases pass against the four exact
updated resources ahead of the unchanged production classes; JUnit SHA-256:
`bd9e7e3cd3d3417ba368f27eaedd4d29a9b48b47fe93f5bd85185933feeb15e6`.
The initial reference-startup attempt failed before migration because its socket
readiness probe observed the temporary initialization server; the corrected
retake waits for the actual TCP listener. Both owned reference schemas/server
are retired. The rebuilt snapshot below qualifies those resource definitions.

The storage code, JSP and revised workflows are qualified in rebuilt Ubuntu
26.04 DEBs at private snapshot `e37f4d2b6f7de741691ebb0452804a198cc517c6`. All
10,757 installed application files match the DEB manifest. All 1,845 installed
JVM executions pass without production overrides, including 26 H2 and 26 physical
MariaDB credential cases; all 36 native workflows pass across five profiles on
locked Chromium 154.0.8025.0. Both MCEDT variants verify encrypted replacement,
no password echo, visible storage-failure feedback and exact credential restoration.

The retained strict audit exit 1 contains 19 recognized events and seven exactly
bound intentional faults, leaving zero unexplained events. Three are the MCEDT
invalid-delete probes; two additional fixed WARN messages belong to the passing
visible storage-failure steps. No global ignore rule is added. Properties are
byte-restored, health passes, and all installed file hashes are retaken afterward.
This qualifies that immutable snapshot and does not claim Ministry authentication.

The later schema-adoption revision (`97103bdf6a`) makes table/index creation and
singleton seeding idempotent without replacing an existing encrypted value. Both
ON and BC chains are freshly migrated and validated again on MariaDB 11.8.8/32K;
the generated contracts exactly match this revision and every physical definition
is unchanged. Repeating the exact migration twice preserves an owned opaque test
value, one singleton row and one name index. Its SQL SHA-256 is
`cdb00ab07687698e9a6d323d799058a5dfd447eda4486f19f381b6f35039ef37` and its Flyway
checksum is `-1910160356`. The earlier qualified package used the prior unmerged
migration checksum; this revised SQL/resource revision needs the next DEB batch.
The owned reference schemas/server are retired. No application database repair
or live-clinic repair is claimed by this reference proof.

## Ordinary reads leave preference inserts unblocked

First-read migration and saves retain their current locking reads. Once ciphertext
exists, obsolete legacy rows are found without a locking range scan; a missing
legacy key therefore cannot gap-lock an unrelated preference insert. Obsolete
rows are deleted only while their ID and password-property name still match, so
an old writer renaming its row into a regular preference cannot lose that row.

MariaDB's snapshot-isolation conflict requires current-row deletion when an old
writer commits after the nonlocking read. `SET STATEMENT` disables that conflict
policy for the one guarded DELETE; it preserves the same connection's setting,
keeps the caller's transaction, and borrows no second connection. Other databases
use the same portable guarded DELETE without the MariaDB prefix. Credential
values never appear in these statements or failure causes.

The merged final tests comprise 39 distinct cases: 39 H2/consumer-unit executions and
31 actual MariaDB 11.8.8/32K transaction executions, all passing with zero skips.
The intentional database-lane repetition totals 70 executions, not 70 distinct
testcases. Changed-line, instruction and branch coverage remain 100% across the
two raw, unchanged reports. Those reports' SHA-256 values are
`d4142b95b3fd3f6cfcc20fb08050b8fd7a6fdc6872a79a4feea7a82ef4b3c1ba`
and `40973d183987f7ecbc040c1585ab4cb7ca0c98d73f8e5e1ccae4e88b1a3127a5`.

The insert controls keep the credential reader's transaction open and require a
new `provider.language` row to commit before releasing it. Concurrent legacy-value
changes, renaming, same-connection isolation restoration, first-save serialization,
first-read migration, encryption failures and rollback are covered. The owned
physical server is retired; the application database is untouched. Rebuilt-DEB
and installed browser qualification of this follow-up remain pending.

Both concurrent repair histories and regression sets are retained. The original
locking implementation fails two of the final 30 transaction controls on physical
MariaDB (JUnit `949c907a0dcb54d0964806dc91dace9f9000af0a569184699f052132e56858c7`).
The intermediate presence-probe implementation at `098e6e5a4e` still produces
MariaDB error 1020 after a legacy writer commits (one of 31 cases; JUnit
`70fd8abae44b4d6d908d2d7c252ba1a5ea98a6579e98ae70e2f2e53fe544ee58`).
The final guarded cleanup passes all 31. Its merged source-bound coverage evidence
SHA-256 is `f56e7ba0300bd281435e5ba9d649a7b9f5fed5737fb05aca5486cd8eb86f6617`.

## Credential audit and concurrent cache publication

Fresh Ontario and BC Flyway runs on MariaDB 11.8.8 confirmed the audited seed,
and replay preserved the existing ciphertext, actor and timestamp. Only
`lastUpdateUser` and `lastUpdateDate` were added to the physical definitions.
The source catalogue retains the operational classification for this table.
The verified migration SHA-256 is
`96dd5081cfd99e4953276b2f801b08a372672c4fa4d7ea96c79e154996aa7c31`.

The focused Java 25 run passed 45 H2 and unit cases. A separate run passed all
31 physical transaction cases on MariaDB plus the same 14 unit cases.
Lock tests use InnoDB's wait records to match the exact writer and contender.
The cache tests exercise an older credential observation overtaken by a newer
publication, for both lookup and explicit refresh. The action tests verify
credential-free failure logging and preserve the committed outcome when a
concurrent logout ends the session.

The credential integration JUnit hashes are
`c4510d66c5a70657502b3bbb812b89772d85e277eb7999a9fdc3c6440cf3f926` for H2 and
`cbe556f2659e1152da6928171cdb0371b03eac83bdbc1615dc428e52d0cb08b4` for MariaDB.
The mailbox workflow requires `EXCLUSIVE=1` before setup, verifies both
credential locations and restores the audit fields with the encrypted row.
Rebuilt package and installed browser qualification for this revision remain
pending.
