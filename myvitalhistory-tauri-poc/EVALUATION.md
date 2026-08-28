# MyVitalHistory Tauri evaluation guide

This build exists only to answer whether Tauri is a credible cross-platform shell for a future
patient-held records product. It is not a pilot, beta, clinical system, or foundation that should
be promoted directly to production.

## Safety boundary

- Use synthetic PDF files only. Never enter or select real patient information.
- The app has no accounts, API, analytics, clinical integration, or persistence.
- A chosen PDF is not opened, copied, uploaded, or rendered. Only its basename is displayed in
  memory until the app is refreshed, closed, or reset.
- Debug packages are unsigned evaluation artifacts and must not be distributed to patients.

## Evaluation tasks

Run the same tasks in the browser, desktop shell, Android emulator/device, and iOS
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

## Decision questions

Record evidence for these questions rather than treating a successful build as approval:

| Area | Question | Evidence in this build |
| --- | --- | --- |
| Shared UI | Can one responsive React interface serve the target form factors? | Yes, at POC depth |
| Native bridge | Can the UI call a narrow, typed Rust command? | Yes |
| File chooser | Does the platform picker work consistently? | Implemented; real devices still required |
| Accessibility | Is the experience usable with target assistive technology? | Automated semantics only; manual testing required |
| Secure vault | Can records be encrypted, recovered, backed up, and deleted safely? | Not evaluated |
| Mobile APIs | Do biometrics, notifications, deep links, and background work meet requirements? | Not evaluated |
| Operations | Can the app be signed, observed safely, updated, and supported? | Not evaluated |
| Dependency risk | Are all target dependency graphs acceptable? | No; the documented Linux `glib` advisory remains open |

## Exit criteria

This evaluation can support a decision to run a deeper spike when:

- browser, desktop, Android, and iOS checks pass on representative devices;
- accessibility findings and platform differences are recorded;
- required native capabilities and missing plugins are listed;
- artifact sizes, build times, and developer setup friction are recorded; and
- the Linux dependency advisory has an accepted resolution or Linux is removed from the intended
  product scope.

It cannot support a production decision until a separate security and data-lifecycle spike proves
encrypted local storage, key handling and recovery, authentication, secure deletion, backup/sync,
privacy-safe telemetry, and release signing.
