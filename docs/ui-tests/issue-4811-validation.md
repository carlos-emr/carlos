# Issue #4811: session and access follow-ups

This change retains the CAISI features while fixing the isolation and access defects.
The broader product decision in #4795 remains separate. Patient-specific requests must
name their patient; opening another chart cannot supply that identity.

| Issue item | Disposition |
| --- | --- |
| 1 | Remove the unnamed Rx read fallback. Read and write requests require an explicit patient. |
| 2 | Stop writing the login-wide encounter bean; chart notes use the named patient's immutable entry metadata. |
| 3 | Capture the selected CAISI program for each patient at chart entry. Subsequent chart operations use that captured program. |
| 4–7 | Remove unused session writes and move message navigation and note-editor flags into the request. |
| 8 | Make inbox filtering request-scoped and carry the filter through navigation links. |
| 9, 21, 22 | Retain at most 100 patients' disposable appointment, encounter and saved-note metadata per category. Unsaved clinical drafts are not evicted. |
| 10, 32 | Keep each compose window's attachment key in its URL, including reply/forward reloads. |
| 11 | Retain the intentional provider-wide appointment cut/copy clipboard. |
| 12 | The transfer chooser no longer changes the authenticated messenger identity from `val1`. |
| 13 | Add per-patient module privilege and patient-record access checks to registry and note-list entry points. |
| 14 | Require the local sender or a local recipient for saved attachment views/downloads, including configured clinic location IDs. |
| 15, 17, 24 | Already addressed by #4775: attachment POST/source checks, PMM selection state, and copied-note UUID handling. Retain those fixes and their regressions. |
| 16 | An appointment whose patient was deleted still renders using its appointment name. |
| 18 | Consume the form's `filter_providers` field and the legacy editor handoff without leaving stale filters. |
| 19 | Bind provider/group values in report queries. |
| 20 | Require POST in both immunization configuration mutators themselves. |
| 23 | Require POST and patient write access for CPP saves; derive the saved CPP identity from the authorized patient. |
| 25 | Run PMM initialization after authentication and before Struts action execution. |
| 26 | Bundle the historical transfer definition as a classpath resource. Check access to its clinical sections before rendering and encode displayed data. |
| 27 | Carry the resolved message ID through the position-navigation redirect; return 404 when no result exists. |
| 28 | Restore the dynamically included legacy chart fragments under `WEB-INF`, with current routes and encoding. |
| 29 | Return an empty issue search for an absent/invalid program selection. |
| 30 | Guard chart-close lock release until the editor and note field exist. |
| 31 | The release already removes the incorrect custom-drug charset conversion; retain and rerun `rx-drug-name-quotes`. |
| 33 | Already addressed by #4954: billing refusals survive the redirect on the bill's own bean. |
| 34 | Remove the three unlinked direct-render gates that bypass their chart owner action. |

## Regression coverage

Java coverage exercises explicit Rx identity, per-patient chart programs and encounter
metadata, bounded metadata retention, CPP request/access/identity guards, registry
access, sender/recipient attachment access, and direct POST contracts.

`chart-message-followups` drives the legacy chart owner, independent inbox searches,
and attachment denial for a provider who is neither sender nor recipient.
`session-state-two-window-isolation` also checks reply/forward reloads and the
packaged document-transfer chooser. `echart-two-patient-isolation` checks that the
retired direct-render routes return 404 without changing either chart.

Package validation uses an Ubuntu 26.04 systemd container, the pinned DrugRef revision
and checksum-verified Chromium snapshot. The unreleased `carlos-ctl` 1.2.0 pin is
built from the repository's documented fallback revision and given a local test
version satisfying the package dependency. No CLI release is published by this work.
