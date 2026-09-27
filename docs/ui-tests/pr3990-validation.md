# PR #3990 installed validation

Validated against `release/2026.08` at `25a867f07b9bda663aaa70aab8e5de225605f999`.
Fixes #3955, #4010, #4011 and #4012; includes the release branch repair for #2319.

The note editors preserve typed line breaks, including supplementary Unicode.
Save/Sign & Save wait for both issue refreshes; the classic editor loads its existing
note and retains patient/provider scope. Legacy recovery renders note content literally.
Program/admission NULL flags retain the model defaults without schema changes.
Caisi dynamic includes are compiled during the build, and pooled tags cannot retain
another provider's view setting.

## Automated checks

- Clean complete Java run: 13,350 tests, zero failures/errors, 51 skips.
- Final Caisi follow-up: five focused Java tests pass, including two new pooled-tag
  tests and three discharge-task/configuration tests. These ran after the full suite.
- Final Node script suite: 1,046 tests pass, no skips.
- JSP build: all 984 pages compile, including both previously excluded dynamic Caisi
  includes. WAR and Javadoc packaging pass.
- Built all three DEBs as `2026.08.0~alpha16~pr3990.6` with one packaging worker.
  Compared 6,679 classes/web resources/migrations with both the DEB and installed
  payload. Removed retired fragment files from WAR staging before final packaging.

## Ubuntu 26.04 VM

Installed through apt in `carlos-val`; `carlos-ctl check` passed. Browser checks used
packaged Chromium through nginx and ModSecurity at `/carlos`.

Final package checks:

- Five modern/classic steps pass: initial editor; delayed fast Save; delayed new-note
  Sign & Save; signed-note edit; exact classic-note content and form scope.
- Legacy check passes all six textareas, hard-wrap negative controls, literal recovery
  text with an HTML-shaped payload, and exactly one Caisi program selector. Strict
  recording reports no HTTP, JavaScript, console, dialog or failed-request errors.
- Legacy settings were temporary: `AbandonOldChart=false`, `ModuleNames=Caisi`,
  `caisi=true`. The original properties were restored byte-for-byte afterward.

Eight neighboring workflows passed on the preceding package before the final Caisi
selector/tag-only follow-up: clinical-freetext, document-upload, echart,
echart-navbar-modules, echart-new-patient-notes, echart-note-sign-bill, echart-print,
and echart-note-editor. Navbar coverage opened 11 links and skipped 13 links by its
existing policy. The echart smoke check needed `ECHART_NOTES_POLL_TIMEOUT_MS=240000`
for the existing patient's 2,201 eForms: it exhausted 125 pages, cleared the loading
indicator, saved/archived Social History, and autosaved/cleaned the note draft. The
90-second default timed out before that dataset was exhausted; assertions were unchanged.

Owned fixtures were removed, including the seven consultation requests left by the
clinical-freetext check (identified by its exact run stamp). Existing patient records
were retained. The VM was stopped for every compile/package run; CPU/memory were
limited during browser validation. Build hashes and complete logs are retained on the
development machine in `work/pr3985-4000-evidence/`.
