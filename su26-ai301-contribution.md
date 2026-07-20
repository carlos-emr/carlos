# Contribution 2: #2623

**Contribution Number:** 2
**Student:** Hana Ahmed
**Issue:** https://github.com/carlos-emr/carlos/issues/2623
**Status:** Phase III Complete

---

## Why I Chose This Issue

I already worked on a previous contribution issue for this project and enjoyed it, so I was glad to find another security-focused issue that was interesting and challenging to work on.

---

## Understanding the Issue

### Problem Description

This EMR app has a feature that shows a patient's clinical measurement chart (e.g., blood pressure over time). To load it, you pass in a patient ID (`demographicNo`). The problem: the servlet checks "are you logged in" but never checks "are you allowed to see this specific patient." Any logged-in user — receptionist, random staff, whoever — can swap the ID number in the request and pull up someone else's medical chart. No per-patient permission check at all. This is a classic IDOR (insecure direct object reference) vulnerability.

### Expected Behavior

When a user requests a patient's measurement chart via `ScatterPlotChartServlet`, the system should verify — for every request, not just at login — that the requesting user has explicit privilege to view that specific patient's clinical data. If the user lacks that privilege, the servlet should reject the request with a `403 Forbidden` response and not render or return any chart data.

### Current Behavior

The servlet only confirms that the user is authenticated (via `LoginFilter`) — it never checks whether the user is *authorized* to view the specific patient referenced by the `demographicNo` parameter. Any logged-in user can substitute any other patient's `demographicNo` into the request and successfully retrieve that patient's measurement chart, regardless of role or clinical relationship.

### Affected Components

- `ScatterPlotChartServlet` — the vulnerable file; missing the `SecurityInfoManager.hasPrivilege()` check present in comparable servlets/actions.
- `SecurityInfoManager` — the existing privilege-checking utility that should be invoked here but wasn't.
- `MeasurementData2Action` (measurements/web) — a sibling endpoint identified during solution research that handles the same kind of demographicNo-scoped measurement data and already follows the correct pattern; used as the reference implementation for this fix.

---

## Reproduction Process

### Environment Setup

Initial setup took longer than expected since I didn't have Docker installed and this project's recommended dev environment is a `.devcontainer` setup. I ended up not completing a full local devcontainer build within the time I had for this phase (see Testing Strategy for what that means for this submission).

### Steps to Reproduce

1. Cloned the repository and forked it to my own GitHub account.
2. Located the vulnerable file and traced how `demographicNo` flows from request parameter through to `generateResult()` and the DAO layer with no authorization check in between.
3. Searched the codebase for the established `hasPrivilege()` pattern used elsewhere, to confirm this servlet was missing something other comparable code already does.

### Reproduction Evidence

I did not run the application locally to observe the vulnerability live (see note in Environment Setup and Testing Strategy). Reproduction here was done by static code review: reading `ScatterPlotChartServlet.service()` and confirming no `hasPrivilege()` call exists anywhere in the request path, compared against sibling files that do have this check.

---

## Solution Approach

### Analysis

The root cause is that `ScatterPlotChartServlet.service()` resolves `demographicNo` from either a request parameter or a session-bound `EctSessionBean`, and trusts the request parameter unconditionally whenever it's present — it never validates that the currently authenticated user is authorized to view the specific patient referenced by that ID. The value flows directly into `generateResult()`, which queries `MeasurementDao` and returns real patient data with no intervening permission check. Unlike other parts of the codebase, this servlet never calls `SecurityInfoManager.hasPrivilege()`, which is the established pattern for enforcing per-resource access control elsewhere in the project.

### Proposed Solution

Add a check immediately after `demographicNo` is resolved and before any chart-generation logic runs, using the same `SpringUtils.getBean(...)` dependency-lookup style already used in this file for `MeasurementDao` and `MeasurementTypeDao`. If the check fails, the servlet returns `403 Forbidden` immediately and skips chart generation entirely.

### Implementation Plan

Using UMPIRE framework (adapted):

**Understand:** `ScatterPlotChartServlet` renders a patient's measurement chart based on a client-supplied `demographicNo`, without ever verifying that the requesting, authenticated user is authorized to view that specific patient's data.

**Match:** Grepped the codebase for existing `hasPrivilege(..., "_demographic", ...)` usage rather than guessing at the pattern. Found `MeasurementData2Action.java` (measurements/web), a sibling endpoint handling the same kind of demographicNo-scoped measurement data, which checks both `_measurement` and `_demographic` privilege, scoped to the specific `demographicNo` (not `null`). Used this as the direct reference implementation.

**Plan:**
1. In `ScatterPlotChartServlet.service()`, after `demographicNo` is finalized, call `SecurityInfoManager.hasPrivilege()` for both `_measurement` and `_demographic`, scoped to `demographicNo`.
2. If either check fails, call `httpServletResponse.sendError(HttpServletResponse.SC_FORBIDDEN)` and `return` immediately.
3. Confirmed corrected import paths (`io.github.carlos_emr.carlos.managers.SecurityInfoManager`, `io.github.carlos_emr.carlos.utility.LoggedInInfo`) by grepping existing usages rather than guessing.

**Implement:** Branch `fix/2623-scatterplot-privilege-check`, commit `097a320903`, PR [#3199](https://github.com/carlos-emr/carlos/pull/3199).

**Review:** Change is scoped to the single file the maintainer asked for; no Struts2 migration bundled in, per maintainer's review comment on the issue. Commit is DCO-signed (`-s`).

**Evaluate:** Verified via static/code-level review and an automated Sourcery review on the open PR (see Testing Strategy). Did not verify via local manual testing in this phase — flagged honestly below rather than claimed.

---

## Testing Strategy

**What was actually done in this phase:**

- **Static/code review:** Confirmed the fix follows the exact pattern used in `MeasurementData2Action.java`, including scoping both `_measurement` and `_demographic` checks to the specific `demographicNo` rather than `null`.
- **Automated review (Sourcery bot on the PR):** Confirmed both privilege checks are present and correctly scoped, and that the PR satisfies the linked issue's two stated objectives (privilege check added; `403` returned on failure).

**What was NOT done in this phase, and why:**

- No local manual testing was performed — I was not able to get the `.devcontainer` environment fully running in time to load the app and observe a live `403` vs. successful chart render. This is a real gap, not something I want to claim happened when it didn't.
- No automated unit test was added, and no existing servlet/security test file was found for this specific class to mirror.

### Unit Tests

I have not added unit tests in this phase.

- [ ] Test case 1: mock `SecurityInfoManager.hasPrivilege()` to return `false` for `_measurement` and/or `_demographic`; assert `403` is returned and no chart is generated.
- [ ] Test case 2: mock both checks to return `true`; assert the chart still renders for both `BP` and standard scatter plot `type` values.
- [ ] Test case 3: assert `demographicNo` is passed correctly as the scoping argument to `hasPrivilege()`, not `null`.

### Integration Tests

- [ ] Integration scenario 1: end-to-end request as an unprivileged user, confirm `403` at the HTTP layer.
- [ ] Integration scenario 2: end-to-end request as a privileged user for their own patient, confirm chart image is returned.

### Manual Testing

I have not manually tested this locally in this phase — no local environment was running. This is planned for Phase IV once the devcontainer is set up.

---

## Implementation Notes

Fixed the missing authorization check in `ScatterPlotChartServlet` (issue #2623), located at
`src/main/java/io/github/carlos_emr/carlos/encounter/oscarMeasurements/pageUtil/ScatterPlotChartServlet.java`.

The servlet's `service()` method read a `demographicNo` request parameter (falling back to the session's `EctSessionBean` if absent) and immediately proceeded to fetch and render that patient's clinical measurements (blood pressure, vitals) as a JPEG chart. The only gate was `LoginFilter`'s session check — there was no call to `SecurityInfoManager.hasPrivilege()`, so any authenticated user could substitute an arbitrary `demographicNo` and view another patient's measurement chart.

Per maintainer review on the issue, this fix stays narrowly scoped to `ScatterPlotChartServlet` — the larger Struts2 migration suggested in the original issue was intentionally left out of scope.

The fix adds a privilege check in `service()`, after `demographicNo` is parsed (since the check needs that value) and before any chart generation or data lookup runs:

- Retrieves `LoggedInInfo` from the session via `LoggedInInfo.getLoggedInInfoFromSession(request)`.
- Calls `SecurityInfoManager.hasPrivilege()` for **both** `_measurement` and `_demographic` read privilege, scoped to the requested `demographicNo`.
- Returns `403 Forbidden` and exits immediately if either check fails.

**On `_demographic`:** per the maintainer's request, I grepped the codebase for existing `hasPrivilege(..., "_demographic", ...)` usage rather than guessing. `MeasurementData2Action.java` (measurements/web) checks both `_measurement` and `_demographic`, scoped to the specific `demographicNo` rather than passing `null`. This fix mirrors that pattern, which also closes a gap in an earlier draft of this fix: the first version only confirmed general measurement privilege, not that the user is authorized for this specific patient. Scoping both checks to `demographicNo` fixes that.

### Week 5 Progress

Studied the issue, identified the vulnerable file and root cause, wrote and committed the fix, opened the PR against `develop`, and received an automated Sourcery review confirming the fix meets both stated objectives of the linked issue.

### Code Changes

- Branch: [`fix/2623-scatterplot-privilege-check`](https://github.com/Hanaahmed12/carlos/tree/fix/2623-scatterplot-privilege-check)
- Commit: `097a320903` — "Fix #2623: add hasPrivilege() check to ScatterPlotChartServlet"
- PR: [#3199](https://github.com/carlos-emr/carlos/pull/3199)

---

## Pull Request

**PR Link:** https://github.com/carlos-emr/carlos/pull/3199

**PR Description:** Adds a patient-scoped authorization check to `ScatterPlotChartServlet` so that measurement charts are only accessible to users with `_measurement` and `_demographic` read privileges for the requested `demographicNo`, returning `403 Forbidden` otherwise. Scoped narrowly to this one file per maintainer guidance; Struts2 migration intentionally out of scope.

**Maintainer Feedback:**
- Maintainer comment on issue #2623 (prior to PR): requested the fix target `develop`, stay scoped to `ScatterPlotChartServlet` only, flag rather than silently decide on `_demographic`, add test coverage or clear manual verification notes if no test pattern exists, and DCO-sign commits.
- Addressed by: grepping for the `_demographic` pattern and documenting the finding in the PR description, keeping the change to one file, and signing the commit with `-s`. Manual verification notes were not fully completed this phase (see Testing Strategy) — flagged rather than fabricated.
- Automated (Sourcery) review on the PR: confirmed both privilege checks are present and correctly scoped, and that both linked-issue objectives are satisfied.

**Status:** Awaiting maintainer review.

---

## Learnings & Reflections

### Technical Skills Gained

Learned to verify assumptions against the actual codebase instead of guessing package paths or privilege patterns — grepping for existing `hasPrivilege()` usage turned up a directly comparable file (`MeasurementData2Action.java`) that meaningfully changed the fix (patient-scoped checks instead of a general privilege check). Also practiced Git workflow steps I hadn't done end-to-end before: branching off a commit made directly on `develop`, adding a second remote for my fork, and pushing a feature branch for a cross-fork PR.

### Challenges Overcome

Getting Maven and Docker set up on Windows took longer than expected, which ate into the time I had for local testing. I worked around the compile-verification step by relying on the repo's CI to catch anything I couldn't verify locally, and was upfront in this document about not having done live manual testing rather than claiming I had.

### What I'd Do Differently Next Time

Set up the `.devcontainer` environment first, before writing any code, so I'm not scrambling to test at the end. I'd also grep for existing patterns (like the `_demographic` check) earlier in the process instead of after already drafting a first version of the fix.

---

## Resources Used

- Claude (AI assistant) 