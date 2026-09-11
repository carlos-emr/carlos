# myCarlos Tauri evaluation guide

This build began as a framework evaluation and now includes a synthetic-data local-vault MVP slice.
It is not a pilot, beta, clinical system, or foundation that should be promoted directly to
production.

## Decision outcome

Tauri v2 has been selected as the myCarlos application shell for the next development phase.
See [`ARCHITECTURE_DECISION.md`](ARCHITECTURE_DECISION.md) for the decision and constraints.
See [`THREAT_MODEL.md`](THREAT_MODEL.md) for the security boundaries and Secure Vault v0.1 gate.

The initial supported platforms are Windows, macOS, Android, and iOS. Linux is deferred until the
documented `glib` advisory is resolved and the updated dependency graph passes security review. The
Linux CI job remains only as an unsupported compatibility monitor.

The current PR remains **DO NOT MERGE**. It stays in CARLOS as evaluation evidence until a dedicated
myCarlos repository is created, after which the PR should be closed with a link to the new
repository. The remaining tasks below are promotion and production-readiness gates, not a reason to
maintain a parallel Electron/Capacitor implementation.

## Safety boundary

- Use synthetic PDF files only. Never enter or select real patient information.
- The app has no accounts, API, analytics, or clinical integration. Native builds include a
  synthetic-data-only encrypted persistence slice; the browser evaluation remains session-only.
- The browser demo never opens or copies a chosen PDF; it keeps basename metadata in memory only.
- In a native build, Rust owns the selected file handle and streams the file directly into encrypted
  storage. The React renderer receives only opaque IDs and sanitized metadata.
- Saving a copy creates plaintext at the explicitly selected destination. Permanent deletion covers
  the live local vault only; it cannot recall previously exported files or old external backups.
- Debug packages are unsigned evaluation artifacts and must not be distributed to patients.

## Evaluation tasks

Run the responsive concept tasks in the browser, desktop shell, Android emulator/device, and iOS
simulator/device where available:

1. Confirm the evaluation warning is visible without scrolling.
2. Review the synthetic record library at desktop and phone widths.
3. Choose a synthetic PDF with the **New** button and confirm only its filename appears with the
   `session only` label.
4. Reset the session and confirm the imported filename disappears.
5. Open **Evaluation details** and confirm it reports a Rust command in a Tauri build and
   `Browser preview` in Vite.
6. Open a document preview, add it to Starred, and confirm opening it places it at the top of Recent.
7. Move that document to Trash, confirm it disappears from the library, then restore it and confirm
   it returns to My records. Check that the 30-day countdown remains visible on phone.
8. Review the Security & backup controls and confirm every real security capability is labelled as
   a concept or session-only demonstration.
9. Connect one Health data demo and confirm only synthetic readings appear and no operating-system
   health permission is requested.
10. Check keyboard navigation, screen-reader labels, text scaling, rotation, and reduced motion.

In a native build, additionally:

1. Generate the fake development pack with `npm run dev:data`.
2. Create a vault with a unique throwaway passphrase and import only the generated PDFs.
3. Confirm imports are encrypted, survive lock/restart/unlock, and remain in the selected profile
   and folder.
4. Confirm a 101-file batch is rejected before import starts. Import a representative file larger
   than 100 MB while monitoring memory and confirm memory remains bounded by chunk size rather than
   total file size.
5. Export over an existing synthetic file and confirm a deliberately corrupted record leaves the
   existing destination unchanged.
6. Permanently delete one record, restart, unlock, and confirm it remains absent.
7. Background the app during import/export and confirm the UI conceals immediately, the native
   operation is cancelled at its next I/O boundary, and the vault reaches the locked state.
8. On Android, use a test document provider that fails after accepting some bytes. Confirm the app
   says the destination may contain a partial readable copy, then inspect and delete that
   destination before retrying. A provider-backed destination is not an atomic export.

The Rust suite automates abrupt process termination after an encrypted chunk write, completed
staging, object rename, each redundant manifest write, and each deletion boundary. It also covers
bit-flipped, truncated, and swapped ciphertext; structured semantic manifest mutations;
authenticated recovery-candidate selection; exact chunk-size boundaries; a preserved malformed
object corpus; redundant-header repair; one corrupt manifest slot; two corrupt slots; bounded reads
for a generated 101 MiB source; cancellation boundaries; recursive plaintext-canary inspection;
and Unix storage modes. Deterministic `NoSpace`
injection also covers failure during object output and on both sides of the first durable
manifest/deletion commit. These tests are repeatable development
evidence, not a substitute for a genuinely full filesystem, physical power-cut, device-backup, or
app-switcher testing.

## Decision questions

Record evidence for these questions rather than treating a successful build as approval:

| Area | Question | Evidence in this build |
| --- | --- | --- |
| Shared UI | Can one responsive React interface serve the target form factors? | Yes, at POC depth |
| Native bridge | Can the UI call a narrow, typed Rust command? | Yes |
| File chooser | Does the platform picker work consistently? | Implemented; real devices still required |
| Accessibility | Is the experience usable with target assistive technology? | Automated WCAG A/AA checks pass across browser and durable-vault states; modal focus and keyboard folder movement are implemented; manual testing required |
| Secure vault | Can records be encrypted, recovered, backed up, and deleted safely? | Local encrypted import/export and live-vault deletion implemented; independent review, device restore, and backup tombstones remain |
| Mobile APIs | Do biometrics, notifications, deep links, and background work meet requirements? | Not evaluated |
| Operations | Can the app be signed, observed safely, updated, and supported? | No-egress/incident/support-bundle baseline and dependency SBOM CI recorded; signing, updating, ownership and drills remain |
| Dependency risk | Are all initial-target dependency graphs acceptable? | Linux is deferred; the remaining target graphs still require production review |

## Exit criteria

The framework evaluation supports continuing with Tauri in a dedicated repository. Before a
production decision, the deeper spike must establish that:

- browser, Windows, macOS, Android, and iOS checks pass on representative devices;
- accessibility findings and platform differences are recorded;
- required native capabilities and missing plugins are listed;
- artifact sizes, build times, and developer setup friction are recorded; and
- no release or support surface includes Linux while its dependency advisory remains unresolved.

Before adding Linux to the supported platform set, its dependency advisory must be resolved and the
updated package must pass the same security, device, signing, and release-readiness gates.

It cannot support a production decision until a separate security and data-lifecycle spike proves
encrypted local storage, key handling and recovery, authentication, secure deletion, backup/sync,
privacy-safe telemetry, and release signing.
