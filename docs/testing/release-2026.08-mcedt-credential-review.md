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

All 29 selected Java 25 cases pass: six unit controls and 23 real H2 transaction
cases. The same 23 transaction cases pass on a disposable MariaDB 11.8.8 server
with 32 KiB pages. Each case owns a UUID database; cleanup never deletes a
credential from a shared fixture or application database. Lock assertions first
observe the competing operation in the database, then check it remains pending
until the writer commits. Both first-save serialization and a concurrent legacy
writer are covered.

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

- Final H2/unit JUnit SHA-256: `a5cebe208ca5e826c02cef8db4ddab2f4e742cce4079723aa61bec5cc0ab83cb`.
- Final MariaDB JUnit SHA-256: `b3791ee2989be46a54fb996f02fdb779afd7baf70dd9e4709e427f60082a58eb`.
- Actual migration SHA-256: `2d4f0eec60513259fd8e54a38354abd8fd14575eeed1b3b18ec2f41ce5264b95`.
- Fresh source-bound coverage evidence SHA-256: `43b5b1e96e775e41c805eb7c78fb6f48daaaa74a4ca4aa1eac68ef849d0fb6ca`.

The new credential service and changed action lines have 100% line, instruction
and branch coverage. Delegate floors remain 66.67% / 64.29% / 100%; its existing
malformed-keystore-URL diagnostic is still uncovered. The retired service's
historical evidence is preserved; its floor is replaced by complete evidence
for the renamed service.

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
