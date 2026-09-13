# Application findings found while building Playwright coverage

A running list of **defects and dead code in CARLOS itself** that surfaced while
writing the coverage plan ([playwright-coverage-plan-2026.08.md](playwright-coverage-plan-2026.08.md))
and the checks that implement it. It is not a list of test-suite problems — those
belong in the plan — and it is not a duplicate of
[alpha-11-tester-coverage.md](alpha-11-tester-coverage.md), whose observations 1–22
were found by an earlier pass and are tracked there.

**The rule this list follows: nothing goes on it unverified.** Each entry records
how it was checked, so a reader can re-check it. §3 records candidates that were
investigated and turned out **not** to be defects, so nobody spends the time again.

Status values: `open` (verified, no issue filed), `issue-filed`, `fixed`,
`needs-live-check` (verified statically, wants confirmation against a running
deployment).

**All nine findings below are filed as [issue #3665](https://github.com/carlos-emr/carlos/issues/3665)**,
one ticket covering the whole pass. A finding keeps `needs-live-check` where that
is still true of it — being filed does not make a source-search result a
confirmed one.

---

## 1. Unreachable routes — no UI entry

These are Struts routes with no link anywhere in the webapp. Per the suite's own
rule a route with no UI entry gets no check, so each of these is a finding about
the route rather than a gap in coverage: either it is dead and should be removed
under the cleanup policy in `CLAUDE.md`, or its entry point was lost and users
have silently had a feature taken away.

| # | Route | Evidence | Status |
|---|---|---|---|
| 1 | `report/ViewGenerateLetters`, and the letters / envelopes / spreadsheet generation behind it (`report/GenerateLetters`, `GenerateEnvelopes`, `GenerateSpreadsheet`) | `grep -rl` across `src/main/webapp` and `src/main/java` for `.jsp`/`.jspf`/`.js`/`.java`/`.xml` returns **0** references outside the JSP itself and `struts-report.xml`. `report/GenerateLetters.jsp` exists and references `ViewManageLetters`, but nothing links to the page that would start the flow. | `needs-live-check` |
| 2 | `admin/ViewDbConnection` | **0** references outside its own JSP and the Struts config. Not linked from the Administration panel or the `/administration` shell left nav. | `needs-live-check` |
| 3 | `billing/CA/ON/ImportOnRA` | **0** UI callers. `ImportOnRa2Action` exists and is mapped, but the Billing Reconciliation page reads the MOH files directory instead of posting here, so the route is service-only. Either it is dead, or the reconciliation page is meant to use it. | `needs-live-check` |
| 4 | `encounter/immunization/config/*` (the immunization **set** configuration pages, e.g. `ViewImmunizationSetDisplay`) | **0** references. `CreateImmunizationSetInit.jsp` posts to `CreateInitImmunization`, but nothing links to `CreateImmunizationSetInit.jsp` itself, so the whole set-configuration area has no way in. | `needs-live-check` |
| 5 | `provider/ViewProviderEncounterHistory` | Referenced only as a `dboperation` dispatch-table entry in `providercontrol.jsp` (`{"encounterhistory", "/provider/ViewProviderEncounterHistory"}`), and `dboperation=encounterhistory` is referenced only by `providerencounterhistory.jsp` itself. Nothing sets it, so the dispatch entry is never taken. | `needs-live-check` |

All five are filed as #3665.

**Why `needs-live-check` rather than `open`:** all five were verified by source
search, which cannot see a link built at runtime from a database row or a
property. A pass against a running deployment should confirm each before any of
them is removed. That confirmation is cheap once the surface checks run: a route
with no UI entry never appears in their catalogues.

## 2. Known defects with no issue filed

Both are tolerated by `scripts/lib/console-baseline.json`, which means the whole
suite is currently blind to that class of error. Each baseline entry is supposed
to name the issue that removes it; these two can only name a docs paragraph.

| # | Defect | Where | Status |
|---|---|---|---|
| 6 | The consultation form requests `providerSignatureImage?providerNo=…` unconditionally, so it 404s and logs a console error for every provider without a stored signature. Remedy: make the request conditional on the provider having a stored signature | alpha-11 observation 6; tolerated by `scripts/lib/console-baseline.json`; filed as #3665 | `issue-filed` |
| 7 | The eChart note editor throws a `TypeError` from `getActiveText()` on **every keystroke** (`js/newCaseManagementView.js.jsp` writes to a `keyword` element the current layout no longer renders). Until it is fixed, no check can type into a chart note and assert a clean console | alpha-11 observation 15; tolerated by `scripts/lib/console-baseline.json`; filed as #3665 | `issue-filed` |

Both are now filed as #3665, and both console-baseline entries cite it. Delete
the entry in the same change that fixes the defect, or the suite stays blind to
the next occurrence.

## 2a. Clinical calculators answer confidently on input they cannot use

Both chart calculators read the age box as text and feed it straight into a
chain of `<=` comparisons. Neither validates it, and neither refuses: they print
a ten-year probability either way. A wrong number here does not look like a bug
— the page renders, no console error appears, and the figure is plausible — but
it is the figure a prescribing decision is made on.

| # | Defect | Where | Status |
|---|---|---|---|
| 8 | **A blank age is treated as 50.** `calculate()` reads `document.calCorArDi.age.value` as a string and tests `age <= 54` first; `"" <= 54` coerces to `0 <= 54`, so an empty box silently selects the youngest age band and prints its probability. Reproduce: open the calculator, leave Age empty, pick any T-score, press Calculate — it reports the 50-year-old figure. Remedy: refuse a non-numeric or out-of-range age instead of computing one | `src/main/webapp/WEB-INF/jsp/encounter/calculators/OsteoporoticFracture.jsp:203-221` (`var age = ...value` then the `age <= 54` ladder); the same shape at `CoronaryArteryDiseaseRiskPrediction.jsp:230-242` | `open` |
| 9 | **A non-numeric age selects the OLDEST band.** Every comparison against `NaN` is false, so the ladder falls through to its final `else` and sets `ageGroup = 8` — the 85-and-over row. A typo in the age box therefore produces the highest-risk answer on the table with no indication anything went wrong. The coronary calculator has the mirror-image fault: its `ageGroup` is a page-level variable (`var ageGroup = 0` outside the function), so a `NaN` age leaves it at **the value the previous calculation set**, while `ageFactor` does fall back to 0 — an answer assembled from two different patients' ages. Remedy: the same validation as finding 8 | `OsteoporoticFracture.jsp:218-219` (the unguarded final `else`); `CoronaryArteryDiseaseRiskPrediction.jsp:54` (the page-level `ageGroup`) and `:232-243` (the ladder with no final `else`); filed as #3665 | `issue-filed` |

Verified by reading the source and by evaluating the same comparison ladder
directly: `"" → band 1`, `"abc" → band 8`, `"54" → band 1`, `"55" → band 2`.

`scripts/clinical-calculators-playwright-checks.js` deliberately asserts only
valid input. Pinning either behaviour as expected would make it permanent; when
it is fixed, the assertion belongs in that check.

Finding 7 is the more serious of the two: it is on the single most-used screen in
the product, it fires continuously while a clinician types, and it is the reason
a baseline entry has to exist at all.

## 3. Investigated and **not** defects

Recorded so the same candidates are not re-investigated.

| Candidate | Why it is not a defect |
|---|---|
| "64 of 136 `admin.admin.*` labels are blank in `oscarResources_en.properties`" | My own search was wrong, not the bundle. The keys are written with spaces around the separator (`admin.admin.mergeRec = Merge Patient Records`), so `grep "^key="` missed them while Java's properties parser reads them correctly. All the labels resolve. |
| "The Administration panel renders the CAISI heading twice" | `admin.jsp` renders two `<h3>CAISI</h3>` blocks, but they are the two branches of one `oscarSec` check on `_admin.caisi` (`reverse="false"` and `reverse="true"`). They are mutually exclusive at render time; exactly one appears. |
| "`consultationServices` rows ship inactive on Ontario, so the service picker is empty" | Real, but already found (alpha-11 observation 21) and already **fixed** on `release/2026.08` by `V1.0.23__activate_legacy_consultation_services.sql`. |

---

## How this list is meant to be used

1. A finding here is **not** a reason to weaken a check. The suite's rule is
   report, don't encode: a check that pins current broken behaviour as expected
   makes the bug permanent.
2. When an issue is filed, set the Status cell to `issue-filed` with the issue
   number, and update the console-baseline entry's `issue` field to the same
   number so the burn-down is traceable from either direction. Filing does not
   upgrade a `needs-live-check` finding: those stay as they are until a live pass
   confirms them.
3. When a fix lands, delete the console-baseline entry in the same change —
   otherwise the suite stays blind to the next occurrence.
4. **This file is enforced, not remembered.** `scripts/app-findings-log.test.js`
   runs in CI and fails the build if a finding has no evidence or an unknown
   status, if ids are not unique and consecutive, or — the point of it — if a
   console-baseline entry cites a finding that is not recorded here. The suite
   may not tolerate a defect that nobody wrote down.

## Adding a finding

Keep the same shape as the rows above: the next id, what the defect is, the
evidence (a command, a file and line, or the check that caught it — enough for a
reader to re-check it), and a status. If a browser check had to tolerate the
defect to keep running, add the console-baseline entry in the same change and
point its `issue` field at this finding by number.
