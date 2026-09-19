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

Installed-DEB execution results and any defects discovered during the run are
recorded in the coverage PR. Source syntax and manifest validation alone are not
considered proof that these live workflows pass.
