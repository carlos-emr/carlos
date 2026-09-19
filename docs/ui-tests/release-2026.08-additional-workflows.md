# Additional release 2026.08 workflow coverage

These core-suite checks use the shared live harness and an isolated synthetic
login. Set the usual BASE_URL, login, MySQL and Chromium environment variables.
Run each npm command individually or select its name with run-playwright-suite.
No real pharmacy, patient or provider preference should be used as a fixture.

| Check / npm suffix | User task | Persistence and cleanup |
| --- | --- | --- |
| `provider-quick-links-playwright` | Schedule → Preferences: reject empty fields; add a named URL containing literal `&` and percent escapes; close/reopen; remove; reopen again | Exact URL and label round-trip. Removes only the marker-owned link and verifies all original quick-link rows remain unchanged. |
| `pharmacy-editor-workflow-playwright` | Master Record → Prescriptions → Pharmacy: validate required name, add pharmacy, edit with confirmation, reopen notes/address, link/unlink/relink for a patient, deactivate with confirmation | Exact field checks, active association checks, reload after deletion. Owns its patient and pharmacy; refuses to delete a pharmacy unexpectedly linked to another patient. Requires `_rx` and `_rx.editPharmacy` write access. |

The scripts use UI controls for positive actions, database reads for independent
persistence checks, and narrowly owned cleanup. Expected validation/confirmation
dialogs are asserted; other browser/network failures remain fatal. They do not
send prescriptions or faxes, visit the example.invalid quick-link destination,
or claim appointment-screen token-expansion coverage.

Installed-DEB validation: both workflows pass on validation12 (Ubuntu 26.04,
8 GiB). The pharmacy editor was repeated three times after correcting a test
race that clicked Close while Bootstrap was still opening the modal. The check
now observes the real DOM transition before closing and waits for the modal to
be hidden; it does not invoke application handlers or use a fixed sleep.

The quick-link check exposed #3771: Add returned HTTP 403 because its dynamic
form lacked a CSRF token. It passes with the separate fix #3772. Other findings
from this promotion review are scratchpad integrity (#3767, fix #3768) and
pharmacy filtering (#3769, fix #3770). These test and fix PRs target
release/2026.08 independently; use their combined validation tree until merged.

The integration build passed 12,458 Java tests (zero failures/errors, 51 skips),
771 Node tests and 1,587 packaging/CLI tests. Suite-manifest tests passed all
18 cases. Code review follow-ups and the broad installed suite are tracked in
#3773; focused live passes alone do not certify every application workflow.
