# MCEDT credential review follow-up

The final review revision stores ciphertext in `mcedt_credential`, separate from
opaque legacy `property` values. Migration `V1.0.59.51` seeds its single record
before application traffic and adds a non-unique index on `property.name`.
The primary key and check constraint protect the stable lock target, including
concurrent first-ever password saves across application instances. LONGTEXT
retains the encrypted representation of the former 4,000-character capacity,
including supplementary Unicode. Larger submitted passwords are refused before
storage changes.

Reads and writes acquire the singleton database lock, then lock legacy rows in
ID order. A first read treats every legacy value as plaintext, including a
literal `{ENC}` prefix. The first legacy row remains the effective credential;
all legacy duplicates are removed in the same transaction. Already migrated
ciphertext is authenticated and decrypted; corruption fails closed. Missing or
blank credentials keep the configured fallback. Change Password replaces the
encrypted record and removes every old plaintext row atomically. Failures expose
only static errors. The page now shows an explicit failure alert, and the action
emits a fixed credential-free warning. The Struts constructor resolves its
dependency and delegates to a package-private injection constructor.

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
repeatable-read snapshot before the legacy locking read. The final implementation
uses current locking reads for both values. The failed physical reports are
retained; the corrected 23-case retake passes with no failures/errors/skips.
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
Their rebuilt-DEB qualification is still pending. No Ministry transport or
authentication is exercised.

Upgrade while application instances are stopped, as the packaged service upgrade
does, and upgrade every instance before resuming MCEDT use. Older code reads only
plaintext properties and cannot consume ciphertext; mixed old/new MCEDT writers
are unsupported. Keeping plaintext available to those writers would defeat the
storage repair. Retain the installation encryption key with its existing
credential backups and use compatible code/schema when restoring them.

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
are retired. A fresh WAR/DEB qualification is required for these resources.
