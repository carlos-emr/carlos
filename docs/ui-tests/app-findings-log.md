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

**Findings 1–9 are filed as [issue #3665](https://github.com/carlos-emr/carlos/issues/3665)**,
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
| 6 | The consultation form requests `providerSignatureImage?providerNo=…` unconditionally, so it 404s and logs a console error for every provider without a stored signature | alpha-11 observation 6. Fixed on `release/2026.08` by the alpha12 regression sweep: `ProviderSignatureImage2Action` answers **204** for a provider without a stamp (absence is a normal state), the `<img>` fires `onerror`, and the form falls back to the signature pad. Live: `scripts/consultation-signature-fallback-playwright-checks.js` opens the form from the chart, probes the current provider and then the other providers the form offers until one's stamp request answers 204 (it skips only when every one has a stored stamp), and sees the pad shown, `newSignature=true`, and a clean console with **no** baseline. The console-baseline entry is deleted. | `fixed` |
| 7 | The eChart note editor throws a `TypeError` from `getActiveText()` on every click into the note (`js/newCaseManagementView.js.jsp` writes to a `keyword` element the current layout no longer renders) | alpha-11 observation 15. Fixed on `release/2026.08` by the alpha12 regression sweep: `getActiveText()` returns when `$("keyword")` is absent. Live: `scripts/echart-note-editor-playwright-checks.js` clicks into the note, types, and asserts a clean console with **no** baseline. The console-baseline entry is deleted, so a check can type into a chart note again. | `fixed` |

Both console-baseline entries are gone. The suite is no longer blind to either
class of error, and the two checks above are the regression for them.

## 2a. Clinical calculators answer confidently on input they cannot use

Both chart calculators read the age box as text and feed it straight into a
chain of `<=` comparisons. Neither validates it, and neither refuses: they print
a ten-year probability either way. A wrong number here does not look like a bug
— the page renders, no console error appears, and the figure is plausible — but
it is the figure a prescribing decision is made on.

| # | Defect | Where | Status |
|---|---|---|---|
| 8 | **A blank age is treated as 50.** `calculate()` reads `document.calCorArDi.age.value` as a string and tests `age <= 54` first; `"" <= 54` coerces to `0 <= 54`, so an empty box silently selects the youngest age band and prints its probability. Reproduce: open the calculator, leave Age empty, pick any T-score, press Calculate — it reports the 50-year-old figure | Reproduced live on the pre-fix package (blank age printed the 50-year-old figure with the first band highlighted). Fixed: both `calculate()` functions parse the box through `share/javascript/clinicalCalculatorAge.js` (a whole number inside the page's own table range: 50–120 for the fracture table, whose first row is 50; 20–79 for the coronary tables, whose cholesterol and smoking bands run 20–39 to 70–79) and refuse anything else with a message in the prediction box and nothing computed. `scripts/clinical-calculators-playwright-checks.js` now asserts every refusal on both calculators and that a valid age computes again afterwards; `scripts/clinical-calculators.test.js` pins the parser, the guard in each JSP, and the message in every bundle | `fixed` |
| 9 | **A non-numeric age selects the OLDEST band.** Every comparison against `NaN` is false, so the ladder falls through to its final `else` and sets `ageGroup = 8` — the 85-and-over row. A typo in the age box therefore produces the highest-risk answer on the table with no indication anything went wrong. The coronary calculator has the mirror-image fault: its `ageGroup` is a page-level variable (`var ageGroup = 0` outside the function), so a `NaN` age leaves it at **the value the previous calculation set**, while `ageFactor` does fall back to 0 — an answer assembled from two different patients' ages | Reproduced live on the pre-fix package (`abc` printed the 85-and-over figure). Fixed with finding 8 by the same guard (`clinicalCalculatorAge.js`); the coronary `ageGroup` is now local to `calculate()`, and the browser check proves a refusal changes nothing by computing the same total for the same patient before and after it. Found on the way: the default chart layout (`newEncounterLayout`) had **no control that reached the calculators at all** — only the older `encounterLayout`'s navigation column offered one — so `newEncounterHeader.jsp` now carries the same popup link the `Index2` layout has | `fixed` |

Verified by reading the source and by evaluating the same comparison ladder
directly: `"" → band 1`, `"abc" → band 8`, `"54" → band 1`, `"55" → band 2`.

`scripts/clinical-calculators-playwright-checks.js` asserted only valid input
while these were open (pinning the behaviour as expected would have made it
permanent); the invalid-input assertions now live there.

## 2b. Pages that POST over AJAX with no CSRF token to send

CLAUDE.md's CSRF bootstrapping rule: CSRFGuard's client script injects the hidden
`CSRF-TOKEN` input only into a `<form>` with a real action and a non-GET method.
A page that reads that input from an AJAX POST and has neither such a form nor the
`/WEB-INF/jspf/csrf-token.jspf` include sends an **empty** token. The request comes
back as an HTML error page, `response.json()` throws into a catch block, and the
user is shown nothing.

| # | Finding | Evidence | Status |
|---|---|---|---|
| 10 | **Six pages POST through the shared AJAX helper with nothing to populate the token.** `share/javascript/carlos-ajax.js` reads `input[name="CSRF-TOKEN"]` on the caller's behalf, and these six (`documentsInQueues.jsp`, `CumulativeLabValues.jsp`, `newEncounterLayout.jsp`, and the fragments `ChartNotesAjax.jsp`, `labDisplayAjax.jsp`, `js/newCaseManagementView.js.jsp` those pages host) carry neither a qualifying form nor the `csrf-token.jspf` include, so the audit concluded every one of those POSTs is sent with an empty token and rejected | **Not an application defect; the defect was in the audit's model.** The hidden input is not the token's only carrier: `CarlosAjax` sends with `XMLHttpRequest`, never `fetch()`, precisely so that CSRFGuard's own script (which `CsrfGuardScriptInjectionFilter` adds to every HTML response) injects the `CSRF-TOKEN` and `X-Requested-With` headers into every send, and CSRFGuard validates the header before the body (`docs/csrf-protection-architecture.md`; the helper's own comment at `carlos-ajax.js:285`). Live: `scripts/csrf-xhr-token-playwright-checks.js` reaches Pending Docs, the chart and the Labs Row Display by clicking, POSTs through each page's own `CarlosAjax.request()`, reads the headers off the wire, and sees the token header, a 200 and the JSON body on every one — on Pending Docs and Row Display with **no** hidden input present at all. The static audit no longer judges pages whose only sends go through the helper, `csrf-bootstrap-baseline.json` is deleted, and `csrf-bootstrap-audit.test.js` pins the fact the exclusion rests on (the helper uses `XMLHttpRequest`) so the audit widens again the day that changes. One send on the chart is outside that exclusion: `onClosing()` in `js/newCaseManagementView.js.jsp` releases the note lock on `pagehide` with `navigator.sendBeacon()`, carrying `CarlosAjax.getCsrfToken()` in the body, and no header can rescue a beacon. The audit names that shape (helper accessor + beacon) so the script is judged, and it is satisfied by the chart's `<form id="frmIssueNotes" action="" method="post">`: CSRFGuard's `isValidUrl("")` is true (`csrfguard.js`, the empty string has no scheme and is a local resource), so the hidden input is injected and populated — the audit's earlier requirement of a non-blank action was stricter than CSRFGuard, and the browser check now asserts the chart's input is populated | `fixed` |

**How this was missed, and then mis-called.** The audit's applicability test first
required the token read and the AJAX send to appear in the page's *own* source, so
pages that delegate to `CarlosAjax` were never looked at. Widening it to the
helper found six "violations" — and reported them without checking what the
helper does with the token, which is send it in a header CSRFGuard's script
sets. The browser check is the half that settles such a question; the static
half now says why it does not judge those pages, and the test suite fails if
the reason stops being true.

---

## 3. Investigated and **not** defects

Recorded so the same candidates are not re-investigated.

| Candidate | Why it is not a defect |
|---|---|
| "64 of 136 `admin.admin.*` labels are blank in `oscarResources_en.properties`" | My own search was wrong, not the bundle. The keys are written with spaces around the separator (`admin.admin.mergeRec = Merge Patient Records`), so `grep "^key="` missed them while Java's properties parser reads them correctly. All the labels resolve. |
| "The Administration panel renders the CAISI heading twice" | `admin.jsp` renders two `<h3>CAISI</h3>` blocks, but they are the two branches of one `oscarSec` check on `_admin.caisi` (`reverse="false"` and `reverse="true"`). They are mutually exclusive at render time; exactly one appears. |
| "`consultationServices` rows ship inactive on Ontario, so the service picker is empty" | Real, but already found (alpha-11 observation 21) and already **fixed** on `release/2026.08` by `V1.0.23__activate_legacy_consultation_services.sql`. |
| "Six pages POST through `CarlosAjax` with an empty CSRF token" (finding 10) | The helper sends with `XMLHttpRequest`, which CSRFGuard's injected script equips with the `CSRF-TOKEN` header on every send; the hidden input the helper also reads is a second copy. Measured on a packaged install: every such POST carried the header and was answered 200 with JSON, on pages with no hidden input at all. A bootstrap include would have been added to six pages that do not need one. |

---

## 4. Packaged release VM validation (September 2026)

These findings are tracked together in [issue #3682](https://github.com/carlos-emr/carlos/issues/3682).
The [validation record](release-2026.08-workflow-validation.md) distinguishes
application defects from test defects and missing fixtures, and records retests.

| # | Defect | Evidence | Status |
|---|---|---|---|
| 11 | SOAP interceptor by-type autowiring initializes unrelated request actions during startup | `AuthenticationInterceptorWiringUnitTest` reproduces the original Spring `UnsatisfiedDependencyException`; release VM starts after removing that autowiring from `spring_ws.xml`. | `issue-filed` |
| 12 | Demographic PDF label templates are incompatible with JasperReports 7 | Fixed in #3685 with updated bundled templates and buffered PDF generation. All six offered Print / Labels destinations pass the packaged VM browser test; optional sexual-health template rendering is unit-tested. The earlier envelope 404 did not reproduce through its unchanged route or UI control. | `fixed` |
| 13 | Fresh-demo Messenger administration/compose fails on NULL clinic locations | Live `messenger`, `messenger-inbox-actions` and surface audit return HTTP 500; three demo rows contain NULL and Hibernate cannot hydrate the primitive `GroupMembers.clinicLocationNo`. Regression fails on the original mapping. | `issue-filed` |
| 14 | Contact search cannot select a result; quoted names also corrupt its JSON handoff | Live `contact-lifecycle` records `Invalid or unexpected token` on clicking the result. `contactSearch.jsp` JavaScript-encodes a complete handler instead of HTML-encoding its attribute, then concatenates JSON. Executing the original serializer with a quoted name raises `SyntaxError`. | `issue-filed` |
| 15 | Measurement history omits Plot for populated numeric data | Live `measurement-history` renders both owned WT rows but no Plot control. `DisplayHistory.jsp` tests `data.canPlot` after the `c:forEach` variable has left scope. | `issue-filed` |
| 16 | Fresh-install prevention pages request an absent optional catalogue and log HTTP 404 | Live `prevention-lifecycle` captures `eform/displayImage?imagefile=vaccine-brands.json` returning 404 before the bundled fallback. A Java regression requires a bundled response when no clinic override exists and preserves override precedence. | `issue-filed` |
| 17 | Episode editor has validation/authorization gaps requiring a focused follow-up | Source review: `episodeForm.jsp` compares status with `Completed`, but the option is `Complete`; `Episode2Action.edit()` loads an episode without the privilege check present in `list()`. No low-privilege live exploit is claimed by this validation pass. | `needs-live-check` |


| 18 | New contact associations submit a blank integer ID and fail Save | Live `contact-lifecycle` selects a result but Save returns 500; `Contact2Action.saveManage()` parses the blank ID. Both contact fragments now initialize new IDs to zero; tracked in #3682. | `issue-filed` |
| 19 | Native-document and calendar popups request a missing host favicon | Captured browser request to `/favicon.ico` returns 404 while `/carlos/images/favicon.ico` exists. Exact nginx redirect added; tracked in #3682. | `issue-filed` |
| 20 | Inbox review-status filters lose an HRM result | Live `inboxhub-filters`: `HRM:17` appears under All but none of New/Acknowledged/Filed after real UI form submissions; tracked in #3682. | `issue-filed` |
| 21 | Chart Row Display has no CSRF input for its AJAX POST | **Not an application defect; the same model error as finding 10.** Live `echart-navbar-modules` DOM/source audit saw Row Display (`CumulativeLabValues.jsp`) read a missing `input[name="CSRF-TOKEN"]`, but its only POSTs go through `CarlosAjax.request()`, which sends with `XMLHttpRequest`, so CSRFGuard's injected script supplies the `CSRF-TOKEN` header and the hidden input is never the carrier. Live `scripts/csrf-xhr-token-playwright-checks.js` reaches Row Display from the chart's Labs menu, POSTs through the page's own helper and reads the token header, a 200 and the JSON body off the wire with no hidden input on the page. Nothing to fix; the entry under #3682 is superseded by this row. | `fixed` |
| 22 | Provider preferences contain broken destinations | Live preferences surface: Edit Text Signature returns 500; Set Default Printer throws a null `messageHandler` assignment. An additional Document Description Template aborted request needs further classification; tracked in #3682. | `issue-filed` |
| 23 | Three anonymous routes return success status with no content | Live `anonymous-access`: DisplayMessages, IncomingConsultation and ViewDocumentReport return HTTP 200 and zero bytes. No data disclosure is established; tracked in #3682. | `issue-filed` |
| 24 | Scratchpad version operations lack the owner comparison used by save | Source review of `Scratch2Action.showVersion()` and `delete()` versus ordinary save; cross-provider behavior has not been live-validated. Tracked in #3682. | `needs-live-check` |

| 25 | Contact deletion fails for both new and persisted associations | Follow-up review of `Contact2Action.removeContact()`: zero-valued unsaved IDs reach `find(0)`, and casting `ArrayList.toArray()` to `String[]` throws before persisted deletions. Both corrected; added Java regressions and unsaved personal/professional UI steps. Final live retest pending; #3682. | `issue-filed` |
| 26 | Contact removal lacked write permission and association ownership checks | Review confirmed `removeContact()` checked read access and lacked association ownership validation. Added write permission, required patient context, POST-only save/removal, and validation of every selected owner before any deletion. Added negative Java regressions; packaged live validation pending; #3682. | `needs-live-check` |

| 27 | Contact saves can reassign another patient's association or move a reciprocal row | Review of `saveManage`: existing IDs were loaded without ownership validation and reused for reverse links. Both categories/removals now prevalidate ownership; reciprocal writes require both patients' permission and a distinct reverse row. 30 focused Java cases pass; #3682. | `issue-filed` |
| 28 | Professional contact consent and active status silently ignore selections | `saveManage` read `contact_` parameters for professional rows. Now uses `procontact_`; regression with opposing personal values passes and UI round-trip is added; #3682. | `issue-filed` |
| 29 | Contact control IDs collide between personal and professional rows | Personal consent/active fields used the professional ID prefix. Corrected prefixes and accessible labels; live workflow checks uniqueness; #3682. | `issue-filed` |
| 30 | Measurement Plot handler did not encode the type query parameter | Review found request-derived type embedded directly in the Plot JavaScript string. Now uses URI-component then JavaScript-attribute encoding; #3682. | `issue-filed` |

| 31 | Client Lab Label silently returns an empty PDF response | Fixed in #3685: errors return an explicit HTTP 500 before PDF output, and optional program joins retain patients without a program assignment. Live UI printing passes for that case; automated tests cover malformed/missing templates, database failure and empty reports. | `fixed` |

| 32 | Reciprocal contact access checks over-restrict ordinary saves and miss omitted form types | Follow-up review and negative Java cases reproduce unnecessary target-patient denial and an omitted/`01` type bypass. The action now plans authorized reverse writes before mutations; #3682. | `issue-filed` |
| 33 | Reverse contacts acquire the wrong type and unrequested SDM/emergency flags | An omitted type created a provider association; empty non-null flag parameters enabled both flags. Corrected explicit type and null flag parameters; Java regressions and an existing-relationship UI scenario check these fields; #3682. | `issue-filed` |
| 34 | Internal patient contact search is unavailable | Live UI probe: Manage Contacts → Add Contact → Internal → Search displays “Demographic search is currently unavailable” instead of opening search; fixture cleanup passes. Source `ManageContacts.jsp` confirms the unconditional return. Open; #3682. | `issue-filed` |
| 35 | Reciprocal lookup can confuse different contact ID namespaces | `DemographicContactDaoImpl.find(int,int)` filters numeric IDs and deletion, but not category/type; a coincident directory/provider ID can look like a reverse patient relationship. Source confirmed; collision not VM-reproduced; #3682. | `needs-live-check` |

| 36 | Malformed contact-save numbers are parsed before authorization | `Contact2Action.saveManage()` parses `demographic_no` and `contact_num` before its privilege check; malformed values throw `NumberFormatException`. Present in the release base; no mutation precedes authorization. Source confirmed; deployed response not VM-reproduced; #3682. | `needs-live-check` |

| 37 | Crafted contact type changes can reclassify existing relationships | Existing rows now retain persisted types in reciprocal planning and persistence. Five regressions fail before the fix; all 30 contact cases pass afterward. Old installed package fails the owned-request tampering probe; the rebuilt DEB passes normal and twice-tampered saves, with cleanup verified; #3682. | `issue-filed` |
| 38 | Existing contact category can be changed by submitting the row in the opposite list | `validateContactSaves` validates patient ownership but not the stored personal/professional category; `linkContactToDemographic` assigns the submitted list's category. Source-patient write permission is required; this is a classification-consistency candidate, not a demonstrated authorization bypass. No normal UI path or VM reproduction was established; #3682. | `needs-live-check` |

## 5. Found while resolving #3665 (September 2026)

| # | Defect | Evidence | Status |
|---|---|---|---|
| 39 | The four calculator pages link their stylesheet at the context root (`/encounterStyles.css`), where nothing is served, so every calculator opens with a 404 for its stylesheet, a "Refused to apply style ... MIME type ('text/html')" console error, and unstyled tables | Surfaced the moment `clinical-calculators-playwright-checks.js` could reach the pages by clicking (§2a): the strict recorder failed on the request and the console error for all three calculators it opens. The file is `encounter/encounterStyles.css`, which is how the calculators index itself links it. Fixed on those four pages; `encounter/immunization/Schedule.jsp`, `ScheduleEdit.jsp` and `messenger/Transfer/SelectItems.jsp` carry the same wrong path and are left for their own checks | `fixed` |
| 40 | Twenty Administration panel items are broken on the packaged install, none of them touched by the #3665 change | `admin-index-links-playwright-checks.js` on the post-fix package (101 items opened): **Age-Sex Report** answers 405 (the panel posts a hidden form, `DbReportAgeSex2Action` is POST-only, and the audit's click reaches it as a GET — a check-versus-page disagreement to settle in the check); **Visit Report** and **Overnight Batch** throw `$(...).validate is not a function` (the jQuery Validation plugin is not loaded on those pages); **Patient List by Appointment Time** throws `Identifier 'reportForm' has already been declared` (a script is injected twice); **Document Description Template** aborts a fetch and **Messages** an image request (see finding 22). Recorded, not fixed here | `open` |

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
