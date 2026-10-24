# Issue #4811: session and access follow-ups

This change retains the CAISI features while fixing the isolation and access defects.
The broader product decision in #4795 remains separate. Patient-specific requests must
name their patient; opening another chart cannot supply that identity.

| Issue item | Disposition |
| --- | --- |
| 1 | Remove the unnamed Rx read fallback. Read and write requests require an explicit patient. |
| 2 | Stop writing the login-wide encounter bean; chart notes use the named patient's immutable entry metadata. |
| 3 | Bind the selected CAISI program to the originating chart window using a session-authenticated context. Subsequent chart, autosave and Rx operations carry that context, including two programs for the same patient. Ordinary chart entry retains the appointment provider's default; an explicit program must be available to the authenticated provider. The chart context supports the full non-negative Integer range used by its DAOs. |
| 4–7 | Remove unused session writes and move message navigation and note-editor flags into the request. |
| 8 | Make mailbox search and sort request-scoped; carry both through pagination, message detail, Inbox/Sent return links, and message actions. |
| 9, 21, 22 | Retain at most 100 patients' disposable appointment, encounter and saved-note metadata per category. Unsaved clinical drafts are not evicted. |
| 10, 32 | Keep each compose window's attachment key in its URL. Reply, Reply All and Forward reloads also retain their source message and read-only operation, reconstructing the quote without placing message text in the URL. |
| 11 | Retain the intentional provider-wide appointment cut/copy clipboard. |
| 12 | The transfer chooser no longer changes the authenticated messenger identity from `val1`. A first-use transfer initializes messaging from the authenticated provider and retains its selected attachment. |
| 13 | Add per-patient module privilege and patient-record access checks to registry and note-list entry points. |
| 14 | Require local message participation or an explicitly authorized linked patient chart for saved message reads, quotes and attachment downloads. Shared chart reads show only the selected patient; message deletion and patient-link changes require local participation. Match legacy and configured local location IDs without mutating remote recipient rows. |
| 15, 17, 24 | Already addressed by #4775: attachment POST/source checks, PMM selection state, and copied-note UUID handling. Retain those fixes and their regressions. |
| 16 | An appointment whose patient was deleted still renders using its appointment name. |
| 18 | Consume the form's `filter_providers` field and the legacy editor handoff without leaving stale filters. |
| 19 | Bind provider/group values in report queries, including unfiltered queries with empty parameter arrays. |
| 20 | Require POST in both immunization configuration mutators themselves. |
| 23 | Require POST and patient write access for CPP saves; derive the saved CPP identity from the authorized patient. |
| 25 | Run PMM initialization after authentication and before Struts action execution. Use the core role DAO so note attachment also works without the optional CAISI context. |
| 26 | Bundle the transfer definition as a classpath resource, excluding the retired `formAR` table. Check access to its clinical sections before rendering, encode displayed data, and load the packaged stylesheet. |
| 27 | Carry the resolved message ID through the position-navigation redirect; return 404 when no result exists. |
| 28 | Restore the dynamically included legacy chart fragments under `WEB-INF`, with current routes and encoding. |
| 29 | Return an empty issue search for an absent/invalid program selection. Dated draft restoration also returns no draft when patient/program context is missing or invalid. Issue searches accept the full positive Long range used by the program access DAOs, without an arbitrary nine-digit limit. |
| 30 | Guard chart-close lock release until the editor and note field exist. |
| 31 | The release already removes the incorrect custom-drug charset conversion; retain and rerun `rx-drug-name-quotes`. |
| 33 | Already addressed by #4954: billing refusals survive the redirect on the bill's own bean. |
| 34 | Remove the three unlinked direct-render gates that bypass their chart owner action. |

## Regression coverage

Java coverage exercises explicit Rx identity, per-patient chart programs and encounter
metadata, bounded metadata retention, CPP request/access/identity guards, registry
access, sender/recipient attachment access, and direct POST contracts.

The verification follow-up also covers signed chart-context forgery, cross-login and
cross-patient rejection, same-patient program selection, shared-chart messaging reads,
unrelated-patient denials, all-or-nothing unlink authorization, and local-versus-remote
message state changes. Real database tests execute the packaged document-transfer
queries and exercise draft replacement rollback. Failed clinical column reads abort
document transfer; a failed autosave reports failure and preserves the previous draft.
Stateless chart contexts do not accumulate per-window session records or expire an
unsaved window when the disposable legacy patient cache is bounded.

`chart-message-followups` drives the legacy chart owner, history/reminder saves without
losing fields from other tabs, orphan appointments, independent mailbox searches and
sorting, Inbox/Sent return navigation, message-position redirects, and attachment denial
for a provider who is neither sender nor recipient. It also checks a fresh-login transfer,
the unfiltered report index, and canonical chart patient fields for both parameter aliases.
`session-state-two-window-isolation` also checks Reply, Reply All and Forward reloads
(subject, quoted text, patient and attachments) and the packaged document-transfer
chooser. `echart-two-patient-isolation` checks that the
retired direct-render routes return 404 without changing either chart.
`rx-print-profile` additionally opens two programs for one owned patient and verifies
that Print & Paste consumes only its originating program's draft, preserves the other
draft, and reopens the originating chart context. The chart/message follow-up also
checks shared-chart message detail, quote reloads and actual PDF bytes, plus unrelated
message and unlinked-patient refusals.

Package validation uses an Ubuntu 26.04 systemd container, the pinned DrugRef revision
and checksum-verified Chromium snapshot. The unreleased `carlos-ctl` 1.2.0 pin is
built from the repository's documented fallback revision and given a local test
version satisfying the package dependency. No CLI release is published by this work.

## Original validation results (Ubuntu 26.04, `ce0a12203f9`)

- Full Java unit/integration run after review fixes: 25,547 tests, zero failures/errors, seven skips.
  The reply/forward reload and mailbox-navigation checks passed all 33
  focused tests, including three new reload cases. The CodeRabbit follow-up suite
  passed 48 tests covering program identifier boundaries, empty report parameters,
  missing draft context, first-use transfer identity, canonical JSP patient keys
  and the authorized missing-PDF path.
  All 992 JSPs compile.
- Full script regression run: 4,475 passed, zero failures, four skipped and one TODO.
- Pinned DrugRef build: 74 tests passed. The documented `carlos-ctl` fallback build:
  1,597 passed and 78 skipped.
- CARLOS and DrugRef debs were built, installed and upgraded in a disposable Ubuntu
  26.04 systemd container. `carlos-ctl check` passed, including live application and
  DrugRef requests, database migrations, TLS, WAF and least-privilege reporting.
  The final local package version is `2026.08.0~beta1+issue4811test9` with
  `carlos-ctl` `1.2.0+issue4811test1`.
- CAISI legacy-editor isolation and multi-site Rx previews both passed with their
  optional modules enabled temporarily. Both settings were restored afterwards;
  fixture residue audits passed. Browser validation uses Chromium 154.0.8025.0.

## Review and broader audit limits

CodeRabbit's full review identified seven inline findings and one missing-PDF fixture
outside the diff. All were validated and addressed, with regressions for the behavior
changes. The unused retired-navigation message fixtures were removed and the new
attachment-access helper received its creation-date `@since` tag. The incremental
review also led to explicit query/draft contracts and program identifier range fixes.
Boundary regressions cover ten-digit IDs, Integer/Long maxima, zero sentinels,
negative IDs, missing IDs and overflow.

Six broader authorization workflows did not pass. Five reproduced failures documented
in the existing suite manifest: chart-note writes through GET/HEAD, patient-lock read
bypasses, note-history patient checks, document descriptions exposed without `_edoc`,
and registry authorization refusals rendered as HTTP 500. The print/export sweep was
inconclusive because its prevention-print positive control failed before the sweep.
These results are recorded separately from the 16 passing workflows; they are not
claimed as fixed by this issue. The OAuth audit passed with its documented prerequisite
enabled temporarily, and that setting was restored.
