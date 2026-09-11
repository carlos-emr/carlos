# myCarlos patient-pilot product decisions

- **Status:** Approved product direction; implementation and independent review remain open
- **Decision date:** 2026-09-10
- **Target:** Patient-pilot readiness on Windows, macOS, Android, and iOS
- **Current code:** Synthetic-data evaluation only; these decisions do not authorize PHI use

This record separates the agreed patient-pilot behavior from the smaller local-vault slice already
implemented in this draft PR. An approved decision is not evidence that its controls have been
built, tested, or independently reviewed.

## Identity, keys, and recovery

- Use the existing CARLOS patient-portal identity with MFA or passkeys. Email and password alone
  are insufficient.
- The portal authenticates the account but cannot decrypt the patient's vault. MFA must never be
  treated as a vault-recovery secret.
- Generate a high-entropy patient-held recovery key. Present a one-time printable/downloadable
  recovery kit and require the patient to complete a verification challenge during setup.
- A new device receives vault keys only through approval by an unlocked enrolled device or entry of
  the recovery key. The portal transports encrypted key material only.
- Permit recovery-key rotation. Rotation invalidates the old key for the current vault, but cannot
  revoke credentials embedded in independently copied historical backups.
- Permit biometric convenience unlock through device-bound Keychain/Secure Enclave or Android
  Keystore material. The passphrase or recovery key remains the fallback.
- Require a passphrase of at least 15 characters, permit paste and password managers, reject known
  compromised choices, and do not impose arbitrary composition rules or scheduled expiration.
- On a lost-device report, revoke synchronization access and rotate key-sharing material. Do not
  promise remote erasure of a device that remains offline or already holds an accessible copy.

## Storage, backup, and deletion

- The supported recovery mechanism is a portable, authenticated, end-to-end encrypted backup.
  Exclude the vault from OS cloud backup where the platform permits, avoiding inconsistent policy
  and Android's small Auto Backup quota.
- Exporting a backup must never create a plaintext intermediate. Restore must authenticate the
  complete backup before changing the live vault and must detect incompatible, corrupt, and stale
  generations.
- Imported file size has no arbitrary fixed cap. Keep the 100-file transaction limit and add a
  free-space reserve, cancellation, operation timeouts, and viewer page/image/resource limits.
- Deletion is immediate with no undo: remove the live key and ciphertext, synchronize a tombstone,
  and purge server ciphertext. Retain a privacy-minimized opaque tombstone only as needed to prevent
  an old device from resurrecting the record.
- Deletion cannot erase plaintext exports, recipient copies, or portable backups already copied by
  the patient. The product must say this directly. Deleting from myCarlos also does not delete the
  clinic's source medical record in CARLOS.
- Account closure requires strong reauthentication and typed confirmation, then revokes enrolled
  devices and purges synchronized ciphertext and key envelopes immediately. Offline devices,
  portable backups, recipient copies, and clinical source records remain outside that guarantee.

## Documents, provenance, and viewing

- A document selected by the patient is labelled **Patient imported · Unverified**. The label is a
  non-colour trust indicator and must not be inferred from its filename or source text.
- A verified CARLOS document must use a signed package binding the document hash, stable document
  ID, intended recipient, issuer, timestamp, and version. Signature validity is distinct from the
  medical correctness of the content.
- Render PDFs in a sandboxed, capability-free helper. It receives one authorized decrypted
  document, has no vault keys or network access, and returns constrained rendered pages. Do not
  render an untrusted PDF in the privileged application webview.
- Conceal decrypted content in background and app-switcher states. Deliberate screenshots remain
  allowed because blocking is inconsistent across the four targets; warn that captures and exports
  leave vault protection.

## Portal synchronization and sharing

- Build full CARLOS portal integration, while keeping patient-imported content and sensitive
  metadata end-to-end encrypted from the portal operator.
- The service may observe the minimum routing data needed to operate: account and device IDs,
  sender, recipient, timestamp, approximate size, and opaque object/package identifiers. Titles,
  document types, providers, dates, profiles, folders, and content remain encrypted.
- Synchronization preserves an append-only revision history. A later signed revision supersedes an
  earlier version; last-write-wins must not silently overwrite history.
- Viewing is available offline. Import, organization, deletion, enrollment, synchronization, and
  sharing require confirmed portal connectivity. This intentionally narrows the current prototype,
  whose local mutations presently work offline.
- Patient-imported documents synchronize as ciphertext the CARLOS service cannot decrypt.
- Care-team access requires an explicit per-document share. Sharing delivers an encrypted,
  immutable document version to the intended recipient. Revocation prevents future retrieval but
  cannot claw back a copy the recipient already opened or exported.
- The first pilot supports only the patient's own portal identity. A local profile is an organizing
  boundary, not caregiver authorization or delegated access.

## Operations and patient experience

- Collect no telemetry. Keep diagnostics local and redacted; any future support bundle is exported
  only through an explicit patient-controlled action.
- Maintain an encrypted, patient-visible, append-only history of imports, shares, deletions,
  devices, and security-setting changes. Sensitive audit details remain encrypted from the server.
- Use mobile app-store updates and a signed desktop updater. Enforce a minimum safe version for
  sensitive operations, with a short grace period except when an actively exploited critical issue
  requires immediate action.
- Default automatic locking to five minutes and allow a range of one to fifteen minutes. Conceal
  content immediately when backgrounded.

## Consequences and open implementation gates

These decisions require new protocols and substantial code beyond this PR: recovery-key envelopes,
portable backup/restore, enrolled-device key exchange and revocation, encrypted synchronization,
tombstones, signed CARLOS packages, explicit sharing, encrypted audit history, a sandboxed renderer,
configurable locking, platform privacy controls, release signing, and secure updates.

Physical-device lifecycle tests, real full-disk and power-loss tests, Argon2id benchmarks,
accessibility review, privacy/regulatory and clinical-safety approval, and independent security
review remain mandatory. Linux remains unsupported until its documented runtime advisory is
resolved and reviewed.
