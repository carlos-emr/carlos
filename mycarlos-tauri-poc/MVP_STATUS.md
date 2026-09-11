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
- [x] Approved 15-character passphrase minimum without arbitrary composition rules, plus local
      common-password/pattern/profile-name screening. A production breach corpus and independent
      threshold review remain patient-pilot gates.
- [x] Atomic import, redundant manifest repair, atomic filesystem-path export, restart/unlock, and
      error-path cleanup.
- [x] Multiple patient profiles, nested folders, search, sorting, bulk moves, and drag-and-drop.
- [x] Manual/background locking, immediate background visual concealment, and persisted
      one-to-fifteen-minute inactivity configuration with the approved five-minute default.
- [x] Passphrase change and typed-confirmation whole-vault reset.
- [x] Confirmed individual deletion from the live vault with both manifest slots rewritten without
      the wrapped object key and ciphertext removed between the two durable commits.
- [x] Abrupt-process-termination recovery matrix after object chunk writes, staging, object rename,
      both manifest commits, and each deletion boundary.
- [x] Deterministic `NoSpace` failure injection during object writing and before/after durable
      manifest/deletion boundaries, verifying restart never exposes a half-committed record.
- [x] Corrupt/truncated/swapped object tests, corrupt-one-slot recovery, corrupt-both-slots
      fail-closed behavior, and preservation of ciphertext when metadata recovery is impossible.
- [x] A generated 101 MiB input verifies that reader requests stay bounded to one 1 MiB chunk.
- [x] Recursive plaintext-canary inspection and Unix `0700` directory/`0600` file assertions for
      application-controlled vault storage.
- [x] Frontend, Rust, responsive-browser, and cross-platform debug-build CI.
- [x] Native imports reject an oversized selection before opening file handles and reject local
      links/special files; metadata reads are bounded and authenticated manifests are structurally
      validated before their object names drive filesystem operations.
- [x] Production CSP excludes development WebSocket access, freezes the JavaScript prototype, and
      grants the main webview no unused Tauri core command permissions.
- [x] Property-based malformed-input coverage for header, manifest, object, and IPC envelopes;
      unsupported versions, same-generation manifest divergence, and generation overflow fail
      closed. Structured semantic manifest mutations, recovery-candidate selection, exact chunk
      boundaries and a preserved malformed-object regression corpus are also covered, and a future
      migration protocol is recorded.
- [x] Map every OWASP MASVS v2.1.0 control to current evidence and explicit gaps in
      [`MASVS_MAPPING.md`](MASVS_MAPPING.md). This engineering map is not an independent assessment.
- [x] Generate reproducible CycloneDX npm/Cargo dependency inventories in CI, keep Actions commit
      pinned, and run a high/critical production npm vulnerability gate.
- [x] Define content-provider export failure behavior: provider destinations may retain a partial
      readable copy and the returned patient-safe error requires deleting it before retrying.
- [x] Record the evaluation's empty telemetry allow-list, incident handling, supported-version
      boundary, future support-bundle constraints and release-supply-chain gates in
      [`SECURITY_OPERATIONS.md`](SECURITY_OPERATIONS.md).
- [x] Add WCAG A/AA automation across the browser library, Security, Health and document-dialog
      states at desktop/phone sizes; fix the detected secondary-text contrast failures. Add a
      hostile-filename regression proving metadata renders as text without off-origin requests.

## Required before calling the synthetic MVP reviewed

- [x] Replace the stale PR title/body with the implemented scope and current test evidence.
- [ ] Obtain application-owner and independent security review of the vault and threat model.
- [ ] Run the native lifecycle checklist on representative physical target devices.
- [ ] Run true power-cut/filesystem crash testing around the atomic replacement primitive on every
      supported filesystem and physical target; subprocess termination coverage is implemented.
- [ ] Reproduce genuine full-filesystem behavior and test OS backup/restore. Deterministic
      `NoSpace` injection, Unix permission modes, and the local corruption matrix are automated;
      platform filesystem and policy inspection remains.
- [ ] Benchmark Argon2id on the oldest supported device class. A repeatable release-mode harness and
      result template are in [`ARGON2_BENCHMARK.md`](ARGON2_BENCHMARK.md); physical results remain.
- [ ] Inspect platform logs, crash artifacts, app-switcher snapshots, and backups for plaintext
      canaries. Recursive application-storage canary inspection is automated.
- [x] State the current evaluation's passphrase-only permanent-loss behavior and the limits of
      readable exports/deletion directly in the UI. The selected recovery kit and backup remain open.

## Required before a patient pilot

- [x] Decide the patient-pilot product behavior for identity, recovery, backup, deletion, viewing,
      E2EE portal synchronization, sharing, privacy, and updates; see
      [`PRODUCT_DECISIONS.md`](PRODUCT_DECISIONS.md). Implementation and review remain open.
- [ ] Design and test an isolated hostile-PDF viewer with no vault or network capability.
- [ ] Implement the approved portable encrypted backup, E2EE synchronization, device enrollment,
      rollback protection, immediate deletion, and resurrection-prevention tombstones.
- [ ] Implement signed CARLOS/portal provenance, recipient binding, and explicit encrypted sharing.
- [ ] Complete accessibility, privacy, PHIPA/PIPEDA, and clinical-safety review.
- [ ] Add signing, notarization, app-store packaging, updater security, and release operations.
- [ ] Resolve all high/critical shipped-runtime findings; Linux remains prohibited while its
      documented `glib` advisory is unresolved.
