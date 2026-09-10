# myCarlos Tauri evaluation

> **Synthetic-data development only — do not use real patient files.** The native app now includes
> an encrypted durable-vault vertical slice, but it has not passed the security, privacy, signing,
> physical-device, or release gates required for PHI. It is not connected to CARLOS EMR.

This directory began as the framework-selection proof of concept for the patient-held record proposed in
[`carlos-emr/carlos#3474`](https://github.com/carlos-emr/carlos/issues/3474). It uses one responsive
React/TypeScript web UI with a narrow Rust boundary and Tauri's native document picker. The branch
now also contains a synthetic-data local-vault MVP slice; it remains evaluation code rather than a
patient pilot or production application.

**Tauri v2 is the selected application shell for the next development phase.** This is a framework
decision, not production approval. [`ARCHITECTURE_DECISION.md`](ARCHITECTURE_DECISION.md) records the
decision, its limits, and the handoff to a future dedicated myCarlos repository. This PR must
remain unmerged in CARLOS until that repository exists, and should then be closed with a link to it.

[`THREAT_MODEL.md`](THREAT_MODEL.md) defines the assets, trust boundaries, credible threats,
required controls, and the Secure Vault v0.1 acceptance gate. [`VAULT_FORMAT.md`](VAULT_FORMAT.md)
records the implemented local format and the decisions and release-gate work that remain.
[`MVP_STATUS.md`](MVP_STATUS.md) separates the implemented synthetic-data milestone from the open
security, device, integration, and release gates.

Use [`EVALUATION.md`](EVALUATION.md) to reproduce the evaluation evidence and record remaining
platform findings. The UI
follows the filing-cabinet visual direction proposed in
[`carlos-emr/carlos#3479`](https://github.com/carlos-emr/carlos/pull/3479), while retaining an
always-visible evaluation warning so screenshots and test sessions cannot be mistaken for evidence
of production readiness.

## Initial platform scope

The planned initial supported platforms are **Windows, macOS, Android, and iOS**. Linux is deferred
until the `glib` advisory below is resolved and the updated dependency graph passes security review.
The Linux CI job is retained only as a compatibility monitor; its debug package is unsupported
evaluation evidence and must not be distributed to patients.

## What it demonstrates

- The same responsive, mock-aligned filing-cabinet screen in a browser, desktop webview, Android
  webview, and iOS webview.
- A durable native filing cabinet aligned with the discussion mocks in PR #3479: patient-profile
  switching, nested folder navigation, location search, list/grid views, sorting, bulk moves, and
  drag-and-drop document/folder moves, plus plain-language document details and save-copy actions.
- A browser evaluation demo with record-kind filters, Recent, Starred, Trash, Restore, and
  session-only sample folders. Those concepts remain deliberately separate from durable storage
  until their data model and retention rules are implemented.
- A synthetic document preview and connected Recent → Starred → Trash → Restore workflow. Actions
  update all affected library sections in memory until refresh, close, or reset.
- Browser-only Security & backup and Health data concept screens, alongside a native Security
  screen for the implemented vault lock, passphrase, profile, and reset operations.
- A typed `runtime_info` command crossing from TypeScript to Rust.
- Native multi-file import and explicit export dialogs owned by Rust; filesystem paths and file
  bytes are never accepted from or returned to React.
- PDF-filtered imports are limited to 100 files per batch to bound simultaneously open handles and
  one transaction's duration. Individual file size is not capped: encryption remains
  constant-memory by streaming 1 MiB chunks, whose plaintext buffers are zeroized on success and
  error paths.
- A passphrase-unlocked, XChaCha20-Poly1305 encrypted local vault with an Argon2id key wrapper,
  encrypted metadata, chunked files, per-object keys, atomic manifest generations, and keyed
  duplicate detection.
- Multiple patient profiles, nested folders, multiple folder assignments, manual/background/
  15-minute inactivity locking, passphrase change, and typed-confirmation whole-vault reset. A
  background lock requested by a native picker is completed immediately after that active
  import/export operation, avoiding a mid-operation lock race.
- Confirmed individual record deletion updates one durable manifest, unlinks the encrypted object,
  and then updates the redundant manifest. Both live slots omit the wrapped per-object key when the
  operation succeeds. Old external backups and future synchronized copies remain outside that
  local deletion guarantee.
- Abrupt-termination tests exercise recovery after chunk writes, staging, object rename, redundant
  manifest commits, and each deletion boundary. Corruption tests cover bit flips, truncation,
  ciphertext swapping, one-slot recovery, and two-slot fail-closed behavior.
- A generated 101 MiB input checks bounded 1 MiB read requests without allocating the whole source;
  recursive canary and Unix mode tests inspect application-controlled storage while locked.
- A collapsible evaluation panel and reset control that removes session-only metadata.
- Frontend unit tests, browser viewport tests, Rust tests, and unsigned debug builds in CI.

It deliberately does **not** implement an in-app document viewer, accounts, synchronization,
Android cloud backup, CARLOS integration, verified provenance, HealthKit/Health Connect, release
signing, or app-store packaging. Deletion does not yet propagate tombstones to backups or other
devices. Apple OS backup may carry the encrypted app-data vault, but restore still requires the
patient passphrase. A successful build and test run is not evidence that the app is ready to hold
PHI.

## Responsive UI evidence

The Playwright smoke test captures the same route at desktop and Pixel 7 viewports:

| Desktop | Phone |
| --- | --- |
| ![Desktop library screen](screenshots/library-desktop.png) | <img src="screenshots/library-phone.png" alt="Phone library screen" width="280"> |

## Browser and desktop development

Prerequisites follow the [Tauri v2 setup guide](https://v2.tauri.app/start/prerequisites/): Node
22.14 or newer, the pinned Rust 1.98.0 toolchain, and the operating system's Tauri webview/build
packages.

```bash
npm ci
npm run dev             # browser preview
npm run tauri dev       # desktop application
```

The browser preview intentionally remains the non-persistent synthetic demo. Durable-vault screens
and commands are available only inside the native Tauri runtime.

## Fake manual-test data

Generate the deterministic development pack before starting the native app:

```bash
npm run dev:data
npm run tauri dev
```

This creates five valid PDFs and `FAKE_MANIFEST.json` in `dev-data/`. Every filename, patient
profile, folder, provider, and document title starts with `FAKE`. Choose a unique throwaway
passphrase when creating the test vault; no credential is stored in the fixtures. The manifest
maps each PDF to a profile and folder. Import
`FAKE_Avery_Patient_Bloodwork.pdf` first, then its `_DUPLICATE` copy into the same profile to verify
duplicate detection. The two files are byte-for-byte identical. The generated files contain no
patient information and must not be edited to include any.

## Android and iOS

Install the platform prerequisites described by Tauri, then initialize and run the generated shell:

```bash
npm run tauri android init
# CI then sets android:allowBackup="false" in the generated manifest.
npm run tauri android dev

# macOS/Xcode only
npm run tauri ios init
npm run tauri ios dev
```

The generated `src-tauri/gen/android` and `src-tauri/gen/apple` directories are build products of
the pinned Tauri CLI rather than reviewed application source, so CI regenerates them on clean
runners. Android debug builds run on Linux; the unsigned iOS simulator build runs on macOS.

## Known evaluation findings

- The current Tauri v2 Linux dependency graph resolves `glib` 0.18.5. GitHub's dependency review
  flags [GHSA-wrw7-89jp-8q8g](https://github.com/advisories/GHSA-wrw7-89jp-8q8g), which is patched
  only in `glib` 0.20.0. Linux therefore remains outside the initial supported platform set. The
  target-unaware dependency review has an exception restricted to this unmerged POC branch. That
  exception is not a resolution and must not be generalized or copied to the dedicated app repo;
  Linux can be reconsidered only after the dependency is patched and reviewed.
- Hosted CI produced a 48 MB Linux debug `.deb`, a 131 MB Android debug APK, and a 92 MB unsigned
  iOS simulator `.app`. These unoptimized artifacts are useful feasibility evidence, not release
  size estimates.

## Checks

```bash
npm run check
npm test
npm run build
npx playwright install chromium
npm run test:e2e

cargo fmt --manifest-path src-tauri/Cargo.toml -- --check
CARGO_BUILD_JOBS=1 cargo clippy --manifest-path src-tauri/Cargo.toml --all-targets -- -D warnings
CARGO_BUILD_JOBS=1 cargo test --manifest-path src-tauri/Cargo.toml -- --test-threads=1
```

The single Cargo build job and test thread are the low-memory defaults for a shared development
container. They trade speed for predictable memory use and do not affect the produced application.

## Native boundary

The durable UI sends passphrases, opaque record/profile/folder IDs, and sanitized names through
typed commands. Native pickers and Rust-owned file handles keep paths and file contents out of the
renderer. The filesystem plugin is not granted to the main webview; it is used only from Rust.
Errors are converted to fixed patient-safe messages and are not logged to the browser console.
