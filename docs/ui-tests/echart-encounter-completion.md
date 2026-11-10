# Encounter completion acceptance ledger

Implementation branch: `fix/echart-encounter-completion`, based on
`release/2026.08` at `9004536dc8314d5f50542b48905ca3ca4099a916`.
This is work in progress. No ticket is claimed closed by this ledger.

| Ticket | Implementation / outstanding evidence |
| --- | --- |
| #5834 | Optional hidden modules skip presentation refresh; focused JS passes; installed browser coverage outstanding. |
| #5750 | A new Save/Sign attempt reconciles an uncertain clinical issue update before proceeding, preserves selections and never revives a dropped intent. Focused recovery tests pass; installed concurrency qualification remains outstanding. |
| #3423 | Lease, ownership, draft and rollout work outstanding. |
| #5992 | Reason-specific conflict handling and test correction outstanding. |
| #5703 | Baseline browser reproduction and selection round-trip outstanding. |
| #5903 | Literal template closures and encoded full names; focused dispatch test passes; browser round-trip outstanding. |
| #4588 | Checklist toggle exposed; permissions and browser persistence checks outstanding. |
| #5844 | CPP issue transport URI-encodes each component before the outer context encoder; round-trip coverage outstanding. |
| #5993 | Full module responses replace module wrappers; both issue modules refresh; focused JS passes. |
| #5376 | Baseline navigation trace and correction outstanding. |
| #5958 | Registry membership is validated once at the DAO SQL boundary; redundant facade queries removed. Fifteen focused form/DAO tests pass; installed query-count evidence outstanding. |
| #6011 | Shared month validation, malformed-value fallback, preference writers and units label implemented; action/browser validation outstanding. |
| #5901 | Notes/module errors rendered as text; focused JS passes; remaining updater inventory and browser controls outstanding. |
| #5599 | Legacy editor body removed; full route/caller/configuration/import inventory outstanding. |
| #5931 | CAISI entry always redirects to modern chart; retired POST recovery outstanding. |
| #5933 | Bounded DAO paging and corrected links; focused DAO/action tests pass; native/browser tests outstanding. |
| #5856 | Modern Messenger PDF implementation outstanding. |

## Recorded verification

- Pinned baseline: JDK 25 Maven WAR package passed, tests skipped.
- Initial panel/dispatch selection: 30 Node tests passed, no skips.
- Initial preference/history selection: 18 Java tests passed, no failures/errors/skips.
- These focused results do not establish final application or package qualification.
- Before/after screenshots, installed Ubuntu 26.04 package tests, ON/BC coverage,
  native concurrency tests, full suites, final-head CI and CodeRabbit review remain outstanding.

## Required preservation contracts

Modern entry routes, patient/program/role/facility access, immutable revisions,
CPP extensions and selections, draft ownership, appointment/billing handoffs,
historical-only records, external encryption keys and durable rollback settings
must survive. A rejected or unknown save cannot silently discard text or resume
signing; failed attachment generation cannot replace valid staged attachments.

## Subsequent initial-stage verification

- Full Node suite: 7,703 passed, 4 skipped, 0 failed (7,707 reported tests).
- Corrected panel/dispatch/order/isolation/i18n selection: 77 passed, no skips.
- WAR and JSP compilation passed after the first implementation commit.
- Form registry/history selection: 15 Java tests passed with no failures/errors/skips.
- Pinned DrugRef `93775a0` verify passed: 309 tests reported, 6 skipped.
- Signed-off commits are pushed to draft PR #6103, targeting release/2026.08.
- CodeRabbit was explicitly requested but rate limited; it reported the next
  included review available about 36 minutes after the initial request.
- Complete renderer download is blocked by HTTP proxy CONNECT 403 for
  `commondatastorage.googleapis.com`. Domain access was requested from the user.
- Development baseline Debian packages were built in Ubuntu 26.04 with
  `SKIP_EFORM_RENDERER=1` solely to investigate ordinary chart screens. They
  are not complete release packages or final acceptance evidence.
- Full-package installation, before/after screenshots and final-head CI remain
  outstanding. Do not mark this draft ready or close the 17 issues based on
  these initial-stage results.

## PR review repair — 9 November 2026

Merged `release/2026.08` at `1335e1b28ab493daf634dfed4d2ba093a9131f82`,
preserving both sets of localization keys. The following review findings are repaired:

- Resolved and unresolved checklist changes mark the note dirty, including after
  fragment replacement. All mutation controls respect the rendered issue permission.
- Issue status changes, deletion and both diagnosis replacement routes re-read the
  selected patient issue and check its current program/role write grant before changing
  the session model or calling persistence. Read-only issues expose no mutation links.
- Optional sidebar failures no longer invalidate confirmed clinical issue fields or
  cancel a queued note save. After an uncertain clinical update, a new Save/Sign attempt
  reconciles clinical fields with the server while preserving current issue selections;
  it never resurrects the intent dropped by the failed operation.
- Quick/Full Chart navigation saves dirty notes before loading, retains the live editor
  while the request runs, and rejects responses without the expected patient marker.
  Failure restores controls and leaves the existing chart available for retry.
- Preference validation displays an escaped error and retains submitted non-secret
  values. Warning periods are normalized and the schedule consultation tag uses the
  same safe parser, applying the month offset once.
- Removed three unused legacy JSP taglibs and the duplicate resolved-issues action.
  Updated obsolete Java regressions while retaining historical revision/patient checks.
  The CPP encoding test executes the actual production text encoder.

Validation in an Ubuntu 26.04.1 container with Ubuntu OpenJDK 25.0.5:

- 78 focused JavaScript tests passed without skips.
- 88 focused Java tests passed without failures, errors or skips, including historical
  DAO integration, per-issue denial, preference rejection and schedule-alert coverage.
- WAR/JSP compilation passed; JSP directive audit reports zero dead directives.
- Pinned DrugRef `93775a045670d5d6b90238ce1dbc551d174ed788` verified successfully:
  309 tests reported, six existing skips, no failures or errors.
- Chromium revision 1685886 and chromedriver were downloaded from the supported
  `storage.googleapis.com` endpoint and verified against `debian/chromium.pin`.
  carlos-ctl 1.3.1 was downloaded and its published SHA-256 verified.

These results qualify the repaired paths, not the entire 17-ticket completion effort.
The lock lease/draft lifecycle, Messenger modern encounter PDF, complete legacy
retirement, installed navigation/concurrency matrix, package lifecycle qualification
and final-head CI remain outstanding. This ledger does not authorize issue closure.

### Additional authorization and installed-package verification

- Extended the fresh permission boundary to Delete and both Change Diagnosis routes.
  Missing replacement diagnoses return HTTP 400 without changing the selected issue.
- 304 adjacent Java tests passed without failures, errors or skips: 34 issue authorization
  cases, 60 observation/duration cases, 202 real transaction cases, and eight preference/tag
  cases. The transaction selection retains stored associations, rollback and draft checks.
- Native Chromium 154 fixtures passed with actual checkbox handlers after DOM replacement,
  Save-on-switch serialization, failed full-chart loading, control restoration and retry.
- Full CARLOS and DrugRef Debian packages, including the pinned Chromium renderer, were
  built in Ubuntu 26.04.1. A fresh Ontario systemd-container install with carlos-ctl 1.3.1
  passed every `carlos-ctl check` and the installed `echart` smoke check (one pass, no skips).
  The last authorization extension still requires installation/qualification of its build.
- Full Node regressions: 7,803 passed, four existing skips, zero failures. Final-head CI and
  the remaining installed encounter workflow selection are still running.

### Installed checklist regression and release integration

The native persistence regression now changes only an existing unresolved or resolved
checkbox, accepts the normal save confirmation, switches notes and reopens the saved
revision. It verifies database membership, exact clinical text, and the prior revision's
unchanged associations. Expected confirmations use the harness's single dialog handler.
The resolve check locates the control by its actual ARIA target; its previous search
excluded every control inside `noteIssues`, including the new checklist toggle.
The obsolete finding-227 expected-failure pin is removed.

A fresh AJAX checklist derives its open state from its own button, so an expanded earlier
editor cannot swallow the first click. The prototype's unnecessary CPP-only field write
was removed: encounter note saves require the dirty lifecycle, not the CPP save flag.
Native candidate checks and final packaged verification are recorded in the PR discussion.

Release integration was refreshed to `ef69eea17a4cc3fb288b26327471fce1e20dcd8f`
after #6099 landed; both prescribing and consultation translations were retained.

### Full-chart failure after an acknowledged save

The compact save used before Quick/Full Chart navigation now requests an editable
acknowledgement. Ordinary note switching keeps its existing view conversion. The
chart-specific response retains encounter date/type/duration and assignment controls,
updates the textarea and form to the saved revision ID, and resets the dirty baseline.
A failed chart request restores autosave; retrying the chart does not create another
revision. The installed editor workflow now injects HTTP 503 after the real compact
save and checks text, controls, saved identity and duplicate-free retry.

The final full-chart regression changes encounter type through the visible SelectBox
combo and invokes the production refresh entry point: the current layout no longer
renders the historical Quick/Full Chart link. On the upgraded Ontario package this
passes the real compact save, injected HTTP 503, editable-control and text preservation,
new revision identity, unchanged prior encounter type, and duplicate-free chart retry.
The strict residue audit passes. Only the single deliberately injected HTTP response
and its matching browser console diagnostic are consumed; other failures remain strict.
The Ubuntu 26.04 package upgrade (snapshot28 to snapshot29, carlos-ctl 1.3.2) also passes
all deployment health checks and preserves the encounter-table snapshot exactly.

Optional sidebar failures use their own localized presentation message. They no
longer display the clinical-refusal message while a confirmed queued save proceeds.
The regression checks both the distinct feedback and continued Save/Sign behavior.

The retained editor's Print callback now derives the revision from its current DOM
ID. A compact-save acknowledgement renames that control; retaining a captured prior
ID broke printing after a failed chart refresh. The regression first reproduced the
old-ID dispatch, and the installed recovery check now toggles Print, verifies that the
committed revision enters the print queue, and restores the original selection.

Sonar's new-code gate identified an obsolete commented Print template as a duplicate
HTML ID, and flagged keyboard access on the retained editor's Print image. The dead
template is removed. The live control now has a button role, keyboard activation and
an announced pressed state kept consistent by individual and bulk queue operations.
An acknowledgement resets that control to the new revision's queue state while retaining
an explicitly selected historical revision. The installed recovery regression verifies
mouse selection, Space-key deselection, state announcements and the unchanged old queue.
The Java findings are corrected with direct validated-index diagnosis updates, removal
of redundant conditions, unused catch bindings, and narrower test assertions/callables.
