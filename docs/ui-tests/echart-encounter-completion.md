# Encounter completion acceptance ledger

Implementation branch: `fix/echart-encounter-completion`, based on
`release/2026.08`, currently merged through `38cfc10c0ae72c66ebe501703de462f59249da1f`.
The broader 17-ticket effort remains in progress. No ticket is claimed closed by this
ledger. Early verification entries are historical; the latest reviewed qualification
is recorded at the end of this document.

| Ticket | Implementation / outstanding evidence |
| --- | --- |
| #5834 | Optional hidden modules skip presentation refresh; focused JS passes; installed browser coverage outstanding. |
| #5750 | A new Save/Sign attempt reconciles an uncertain clinical issue update before proceeding, preserves selections and never revives a dropped intent. Focused recovery tests pass; installed concurrency qualification remains outstanding. |
| #3423 | Lease, ownership, draft and rollout work outstanding. |
| #5992 | Reason-specific conflict handling and test correction outstanding. |
| #5703 | Baseline browser reproduction and selection round-trip outstanding. |
| #5903 | Literal template closures and encoded full names; rendered-JSP producer, autocomplete, POST and textarea round trips pass in ON/BC. |
| #4588 | Checklist toggle, fresh mutation authorization and resolved/unresolved persistence/history round trips verified. |
| #5844 | CPP issue transport URI-encodes each component before the outer context encoder; rendered-JSP/consumer round trips preserve delimiters, Unicode, percent escapes and literal markup in ON/BC. |
| #5993 | Full module responses replace module wrappers; both issue modules refresh; focused JS passes. |
| #5376 | Visible Quick/Full Chart failure/retry and note-switch persistence verified; broader navigation matrix outstanding. |
| #5958 | Registry membership validated at the DAO SQL boundary; native grouped history and unknown-table compatibility pass. Installed query-count trace remains outstanding. |
| #6011 | Shared calendar-month validation, fallback and canonical preference storage verified by action/manager tests; broader preference browser matrix outstanding. |
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

The full-chart regression changes encounter type through the visible SelectBox
combo and clicks the modern Quick/Full Chart button. On the upgraded Ontario package this
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
template is removed. The live control now uses a native button, keyboard activation and
an announced pressed state kept consistent by individual and bulk queue operations.
An acknowledgement resets that control to the new revision's queue state while retaining
an explicitly selected historical revision. The installed recovery regression verifies
mouse selection, Space-key deselection, state announcements and the unchanged old queue.
The Java findings are corrected with direct validated-index diagnosis updates, removal
of redundant conditions, unused catch bindings, and narrower test assertions/callables.

Selecting Print before changing the encounter type exposed a SelectBox positioning
defect: its dropdown used document coordinates inside a positioned editor, placing
the choices outside the viewport. SelectBox now measures its own coordinate origin
before placing the choices below the input. Three production-code regressions cover
document scrolling and positioned/scrolled editors; the two editor cases fail against
the prior code. A real Chromium reproduction moves the dropdown from 1,200 pixels
below its input to the correct edge and selects the visible option. The installed
recovery workflow retains the Print-first interaction to exercise this layout.

The final accessibility follow-up replaces the image's button role with a native
`type="button"` control. It retains the revision ID and decorative printer icon,
provides built-in Enter/Space activation, and does not submit the clinical form.
Queue helpers and Clear reset both legacy image controls and the new button's icon
and pressed state; opening an already queued note reflects its current selection.
Editor cleanup removes the native control as well as old images.
The installed recovery workflow verifies both keyboard activation keys after the
acknowledgement renames the control.

Chart replacement passes the current editor's note identity to the edit action.
After acquiring a different replacement editor, the action retires only the prior
provider/patient/note/session lock. A conflicting replacement returns HTTP 409 and
leaves the prior editor available; ordinary edit routes and independent new notes
for different clinicians keep their existing behavior. Malformed identities reject
before mutations, and storage failures propagate to chart-load recovery. The Java
regressions cover these boundaries and acquisition-before-retirement ordering; the
installed recovery check verifies that successful retry leaves no stale saved-note
lock and can close and reopen the chart cleanly.

## Remaining legacy assets

`encounter/includes/*.jspf` and `encounter/js/encounter.js` are no longer served
by `Index2.jsp`. Their remaining source-contract tests preserve historical
encoding and revision contracts while complete legacy retirement is tracked
under #5599. They are not evidence of a reachable legacy editor. The modern
Quick/Full Chart button now exposes the chart recovery workflow; sort/filter
failures retain the rendered chart and show a load-specific error.

Consultation warning periods are calendar months. The schedule alert now
applies the configured offset once (previously twice); explicit settings may
therefore flag referrals sooner. Blank retains the default, malformed stored
values use the reader fallback, and invalid submissions reject the entire
preference save before any setting is changed.
Clearing a previously saved period explicitly persists the canonical property's
null reset, restoring the one-month default. Valid month normalization and that
reset have positive manager regressions; unrelated nullable properties retain
their existing persistence behavior.

## Reviewed qualification — 10 November 2026

The reviewed fixes preserve pending issue selections and encounter type/date/time
through status, Assign, Delete, CPP and Save reconciliation. Both diagnosis replacement
routes authorize the proposed role before persistence. The visible chart toggle retains
the editor after an acknowledged save and failed refresh, updates its revision and Print
identity, and retires the prior session-owned lock after acquiring a replacement.

- Java: 35,954 cases, 212 registered routed/prerequisite skips, zero failures/errors.
  A fresh complete three-worker run succeeds in one execution; all 2,646 reports
  match the complete case inventory, including both positive preference cases.
  All compiled production/test class hashes match across the qualification lanes.
  The five permission-sensitive cases pass separately without skips.
- Physical MariaDB: both grouped-history and unknown-table boundary cases pass without
  skips. Other illegal-argument failures remain visible rather than becoming empty history.
- Node: 7,889 passing cases, no skips; complete case/source inventory refreshed.
  Python: all 390 CI-tooling cases and 30 coverage-tool cases pass.
- WAR/JSP: clean build, 997 JSPs plus eight dispatched fragments compiled, four taglib
  contract cases pass, zero dead directives.
- Complete Ubuntu 26.04 packages `2026.09.0~snapshot29+pr6103.11` include pinned Chromium
  and carlos-ctl 1.3.2. All 7,503 packaged class/JSP/JavaScript entries match the source
  WAR, including the same 6,293 production classes used for Java qualification.
- ON and BC upgrades pass all deployment health checks. Their 1,081 durable encounter
  rows remain byte-identical through upgrade and browser qualification. All six selected
  issue/editor native checks qualify in each province with strict residue audits and
  descriptor restoration.
  All 7,503 installed class/JSP/JavaScript entries and the restored deployment descriptor
  match the source WAR in each province. The editor workflow uses the native template
  dropdown, waits for CPP reconciliation before fixture teardown, accepts the normal CPP
  redirect and checks the stored/reopened item.
  Template names retain quotes, ampersands, plus signs, Unicode and literal percent escapes;
  CPP labels also retain semicolons and markup-like text without interpreting it as HTML.

The source-bound coverage refresh retains higher historical floors and every required
refusal line. Broader release native-union coverage and the remaining ticket acceptance
work above are separate from this reviewed PR scope.
