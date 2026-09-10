# myCarlos synthetic MVP status

- **Milestone:** local encrypted filing cabinet using synthetic data
- **Production status:** not approved for PHI, a patient pilot, or distribution
- **Supported evaluation targets:** Windows, macOS, Android, and iOS
- **Deferred target:** Linux

This checklist describes the work currently present on the staging branch. A checked item means the
behavior is implemented and covered at development depth; it does not replace independent security,
privacy, accessibility, or clinical review.

## Implemented

- [x] Shared responsive React/TypeScript interface in a Tauri v2 shell.
- [x] Native PDF picker with paths and bytes kept out of the React renderer.
- [x] PDF-filtered imports bounded to 100 open files per transaction, with constant-memory chunked
      encryption and no arbitrary per-file size cap.
- [x] XChaCha20-Poly1305 encrypted chunked objects and encrypted metadata manifests.
- [x] Argon2id passphrase wrapper, independent object keys, and keyed duplicate detection.
- [x] Atomic import, redundant manifest repair, atomic filesystem-path export, restart/unlock, and
      error-path cleanup.
- [x] Multiple patient profiles, nested folders, search, sorting, bulk moves, and drag-and-drop.
- [x] Manual, background, and 15-minute inactivity locking.
- [x] Passphrase change and typed-confirmation whole-vault reset.
- [x] Confirmed individual deletion from the live vault with both manifest slots rewritten without
      the wrapped object key and ciphertext removed between the two durable commits.
- [x] Frontend, Rust, responsive-browser, and cross-platform debug-build CI.

## Required before calling the synthetic MVP reviewed

- [ ] Replace the stale PR title/body with the implemented scope and current test evidence.
- [ ] Obtain application-owner and independent security review of the vault and threat model.
- [ ] Run the native lifecycle checklist on representative physical target devices.
- [ ] Add crash/power-loss injection at every manifest and object persistence boundary.
- [ ] Test low-disk, filesystem permissions, corrupted slots/objects, and OS backup/restore.
- [ ] Benchmark Argon2id on the oldest supported device class.
- [ ] Inspect logs, crash artifacts, app-switcher snapshots, and backups for plaintext canaries.
- [ ] Decide whether passphrase-only recovery and permanent loss are acceptable product behavior.

## Required before a patient pilot

- [ ] Design and test an isolated hostile-PDF viewer with no vault or network capability.
- [ ] Define encrypted backup/sync, device enrollment, rollback protection, and deletion tombstones.
- [ ] Define and implement signed CARLOS/portal provenance and recipient binding.
- [ ] Complete accessibility, privacy, PHIPA/PIPEDA, and clinical-safety review.
- [ ] Add signing, notarization, app-store packaging, updater security, and release operations.
- [ ] Resolve all high/critical shipped-runtime findings; Linux remains prohibited while its
      documented `glib` advisory is unresolved.
