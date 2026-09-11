# Architecture decision: Tauri v2 application shell

- **Status:** Accepted for the next development phase
- **Date:** 2026-09-08
- **Scope:** myCarlos desktop and mobile application framework

## Decision

Use **Tauri v2 with a shared React/TypeScript interface** as the application shell for
myCarlos. The initial supported platforms are Windows, macOS, Android, and iOS.

Linux is explicitly deferred while the Tauri Linux dependency graph contains the documented
`glib` vulnerability. Linux compatibility may continue to be built in CI so the dependency and
upstream resolution remain visible, but those artifacts are evaluation evidence and must not be
distributed as supported releases.

This replaces Electron plus Capacitor as the active implementation direction. It does not approve
the current proof of concept for production use and does not establish that the application is safe
to store personal health information.

The proof of concept in this CARLOS repository remains a **DO NOT MERGE** draft. It is retained as
framework-selection evidence until a dedicated myCarlos repository is created. Development
will continue in that repository; this PR should then be closed with a link to the new location
rather than merged into CARLOS.

## Basis for the decision

The proof of concept demonstrated, at evaluation depth:

- one responsive React/TypeScript interface across browser, Linux desktop, Android, and iOS;
- successful Linux, Android, and unsigned iOS simulator debug builds in hosted CI;
- a narrow typed TypeScript-to-Rust command boundary;
- a native PDF picker with narrowly scoped Tauri capabilities;
- working session-only record-library interactions; and
- frontend unit, responsive browser, and Rust command tests.

This is enough evidence to choose the shell and avoid maintaining parallel Tauri and
Electron/Capacitor implementations. It is not evidence for the security or clinical suitability of
the future product.

## Constraints carried into the new repository

- Keep platform access behind narrow interfaces; React components must not call unrestricted
  native APIs directly.
- Grant only the Tauri capabilities needed for a specific workflow.
- Use synthetic records until the security, privacy, and clinical-safety controls permit otherwise.
- Do not include Linux in release packaging, distribution, support claims, or production readiness
  until the `glib` advisory documented in the README is resolved and the resulting dependency graph
  passes security review.
- Complete physical Android and iOS device testing, including file selection, application
  lifecycle, accessibility, rotation, text scaling, and reduced motion.
- Treat encrypted storage, key handling and recovery, secure deletion, backup and synchronization,
  safe PDF rendering, signing, and updating as unproven work requiring separate design and review.

## Local-vault milestone implemented on the staging branch

The staging PR now builds a security-focused local-vault vertical slice using synthetic files:

1. import a PDF through the native picker;
2. encrypt the document and its metadata before durable storage;
3. close, restart, and unlock the application;
4. export an authenticated plaintext copy only after an explicit warning, using atomic replacement
   when the destination is a filesystem path;
5. organize records across profiles and nested folders without exposing metadata while locked; and
6. permanently delete an individual record by durably removing it from one manifest, unlinking its
   ciphertext, and removing its wrapped object key from the redundant manifest.

The implemented format and key/recovery decisions are recorded in
[`VAULT_FORMAT.md`](VAULT_FORMAT.md). This is implementation-complete for the scoped slice, not
security-gate complete: independent review, crash injection, backup/restore and physical-device
matrices, signing, and release controls remain. The chosen scope still defers secure document
viewing and deletion propagation to backups or synchronized devices. Cloud synchronization and
CARLOS integration follow only after the local vault lifecycle passes review.

## Approved patient-pilot direction

The subsequent product decisions are recorded in
[`PRODUCT_DECISIONS.md`](PRODUCT_DECISIONS.md). The target is a four-platform patient pilot using
the existing portal identity with MFA/passkeys, patient-held recovery, portable encrypted backups,
an isolated PDF renderer, full E2EE portal synchronization, signed CARLOS provenance, explicit
per-document sharing, immediate deletion, minimal server-visible metadata, and signed/enforced
updates. The current local-vault implementation demonstrates only a prerequisite slice of that
design and must not be described as implementing those patient-pilot capabilities.

## Superseded direction

Electron plus Capacitor remains useful historical design analysis, but it is no longer the active
implementation direction for myCarlos. Reconsidering it requires new evidence and a new
architecture decision.
