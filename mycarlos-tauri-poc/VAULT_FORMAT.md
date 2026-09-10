# Durable vault format v1

- **Status:** Implemented for synthetic-data development; security review required
- **Identifier:** `ca.carlos.mycarlos`
- **Storage root:** Tauri application-data directory, `vault-v1/`
- **Implemented recovery:** the patient passphrase; there is no vendor key or recovery code
- **Approved patient-pilot recovery:** a patient-held recovery key; not implemented in format v1

This records decisions D-01, D-02, D-03, and D-05 for the current local-only vertical slice. It is
an implementation description, not approval to store PHI.

## Key hierarchy

Vault creation generates a random 256-bit master key. Argon2id derives a wrapping key from the
patient passphrase using a unique 128-bit salt, 64 MiB memory, three iterations, and four lanes.
The passphrase-derived key wraps only the master key with XChaCha20-Poly1305. HKDF-SHA-256 derives
separate manifest, object-key-wrapping, and keyed-fingerprint keys from the master key. Each record
has an independent random 256-bit content key, itself authenticated and wrapped by the object-wrap
key. Changing the passphrase therefore rewraps the master key without rewriting every object.

The parameters and algorithm identifiers are stored in the non-secret header. They need target
device benchmarking and independent cryptographic review before release. Raw keys never cross IPC;
Rust zeroizes passphrase request strings and long-lived secret-key buffers where the libraries make
that practical.

## Files and transactions

```text
vault-v1/
  header.json          non-secret format, KDF configuration, wrapped master key
  manifest-0.bin       authenticated encrypted metadata slot
  manifest-1.bin       authenticated encrypted metadata slot
  objects/<uuid>.mcobj authenticated encrypted record stream
  staging/<job-id>/    incomplete imports, removed after failure or next unlock
```

The encrypted manifest contains profiles, nested folders, folder assignments, immutable imported
filenames, sizes, timestamps, unverified-source labels, per-record fingerprints, opaque object
names, and wrapped content keys. Mutations write the same logical state into two consecutive
generations using a cross-platform atomic replacement primitive. Unlock authenticates both slots,
selects the highest valid generation whose objects exist, rewrites that state into the other slot
to repair redundancy, removes incomplete staging jobs, and only then removes ciphertext objects
not present in the repaired state.

Imports stream arbitrary files in 1 MiB chunks. XChaCha20-Poly1305 authenticates every chunk with
the vault ID, record ID, chunk index, and final-chunk marker as associated data. Files are encrypted
and verified in a per-job staging directory; a batch becomes visible only after every non-duplicate
input succeeds and the next manifest generation is durable. Duplicate fingerprints are keyed and
scoped to a patient profile. A failed batch leaves the prior manifest authoritative. The native
picker is filtered to PDFs and batches are limited to 100 files so the picker bridge does not hold
an unbounded number of open handles in one transaction. Individual file size is not capped;
constant-memory chunking keeps large medical files possible, while available storage and future
vault-quota policy remain separate concerns. The filter does not validate PDF structure or make a
future renderer safe.

Exports stream authenticated plaintext only into a destination explicitly chosen with the native
save dialog. Filesystem-path destinations are written to a same-directory temporary file and
atomically replaced only after authentication and syncing succeeds. Content-provider destinations,
where atomic rename is unavailable, receive a second streaming pass only after a complete
authentication pass. The UI warns that the exported copy is outside vault protection.

Individual deletion commits a manifest without the record into one slot, unlinks the ciphertext,
then commits the same state at a newer generation into the other slot. Both live manifests
therefore omit the wrapped per-object key after a successful operation. If unlinking fails, the
second commit still makes the object an undecryptable orphan and unlock cleanup retries its
removal. This local cryptographic-erasure
property does not remove previously exported plaintext or old copies held by filesystem snapshots,
device backups, or a future synchronization service. Those systems require explicit tombstones and
retention rules. No viewer, synchronization, analytics, or CARLOS provenance is implemented.

## Backup and restore behavior

On Apple platforms the vault is deliberately stored in the application data/Application Support
area so normal device backups can carry its ciphertext. A restored vault still requires the
patient passphrase. No plaintext cache is created or marked for backup. Android cloud backup is not
promised and the generated Android application manifest is configured in CI with
`android:allowBackup="false"`; Android users need a future explicit encrypted export/restore flow.

Losing the passphrase means losing access. The only fallback is a typed-confirmation whole-vault
reset, which permanently removes all local profiles and records.

The patient-pilot target in [`PRODUCT_DECISIONS.md`](PRODUCT_DECISIONS.md) replaces this limitation
with a patient-held recovery kit and portable authenticated encrypted backups. That target is not
implemented by format v1. OS cloud backup is to be excluded where the platform permits once the
portable backup flow exists.

## Known limits before release

- Rollback across an externally restored pair of otherwise valid manifest slots is not detected.
- Individual deletion has no backup/synchronization tombstone or verified secure-erasure guarantee
  for storage media, snapshots, exported plaintext, or copies outside the live vault.
- Crash-injection, power-loss, low-disk, physical-device backup/restore, and filesystem-permission
  matrices remain release-gate tests. Abrupt subprocess termination at the application-controlled
  chunk, staging, rename, manifest, and deletion boundaries is automated, along with deterministic
  `NoSpace` failures around object and metadata commits. True power-cut and genuinely full
  filesystem behavior inside platform primitives still require target-device testing.
- Argon2id settings require performance measurements on the oldest supported device class.
- The format has not received independent cryptographic or privacy review and has no migration
  implementation beyond rejecting unsupported versions.
- An application extraction into the dedicated myCarlos repository is required before release.
