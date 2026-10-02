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
| 23 | Anonymous-route audit sent malformed JavaScript-encoded URLs | Reclassified as a test defect in #3684: decode JavaScript literal escapes such as `\x26` before probing. The corrected live audit refuses all 122 routes with authentication code unchanged; valid fresh-session requests redirect to login. The original observation did not establish data disclosure. | `fixed` |
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

## Issue 3682 follow-up

The [focused PR and validation ledger](issue-3682-resolution-validation.md) records
follow-up fixes, live results, negative controls and pending checks for the
historical observations above. In particular, finding 23 was malformed audit URL
generation, not an authentication-code defect: 122 correctly generated anonymous
routes refuse access. The original PDF Envelope 404 did not reproduce; all six
offered PDFs pass byte validation. The latest administration retest identified an
OHIP fragment's extra GET handler and repeated AJAX headers as the causes of the
remaining report failures. Final package validation remains explicitly pending
in the ledger; source changes alone do not establish a live fix.

## 5. Found while resolving #3665 (September 2026)

| # | Defect | Evidence | Status |
|---|---|---|---|
| 39 | The four calculator pages link their stylesheet at the context root (`/encounterStyles.css`), where nothing is served, so every calculator opens with a 404 for its stylesheet, a "Refused to apply style ... MIME type ('text/html')" console error, and unstyled tables | Surfaced the moment `clinical-calculators-playwright-checks.js` could reach the pages by clicking (§2a): the strict recorder failed on the request and the console error for all three calculators it opens. The file is `encounter/encounterStyles.css`, which is how the calculators index itself links it. Fixed on those four pages; `encounter/immunization/Schedule.jsp`, `ScheduleEdit.jsp` and `messenger/Transfer/SelectItems.jsp` carry the same wrong path and are left for their own checks | `fixed` |
| 40 | Twenty Administration panel items are broken on the packaged install, none of them touched by the #3665 change | `admin-index-links-playwright-checks.js` on the post-fix package (101 items opened): **Age-Sex Report** answers 405 (the panel posts a hidden form, `DbReportAgeSex2Action` is POST-only, and the audit's click reaches it as a GET — a check-versus-page disagreement to settle in the check); **Visit Report** and **Overnight Batch** throw `$(...).validate is not a function` (the jQuery Validation plugin is not loaded on those pages); **Patient List by Appointment Time** throws `Identifier 'reportForm' has already been declared` (a script is injected twice); **Document Description Template** aborts a fetch and **Messages** an image request (see finding 22). Recorded, not fixed here | `open` |

## 6. Reported by phc007 against the packaged install (September 2026)

One report, three separate defects on the encounter chart header. They are
recorded apart because only the first is the one the wording points at: "the
header is not i18n" was true of the Java-rendered half only, which is why the
page around it looked translated.

| # | Defect | Evidence | Status |
|---|---|---|---|
| 41 | The chart header's identity block (Sex, DOB, Age, Next Appt., MRP, and the pronoun/gender/phone/email captions) renders in the SERVER's language whatever the browser asks for, while the `<fmt:message>` labels beside it follow the browser — a half-translated header | `Demographic#getStandardIdentificationHtml` read `LocaleContextHolder.getLocale()`, and CARLOS installs no Spring `LocaleResolver` on the Struts/JSP request path (`grep -rn "LocaleResolver" src/main` returns nothing, and `web.xml` has no `RequestContextFilter`), so that is the JVM default. Reproduced on the packaged install: a `fr-CA` browser and an `en-CA` browser on the same server returned byte-identical identity labels (`Sex`, `DOB`, `Age`, `Next Appt.`, `MRP`) while the same header's calculators link read `calculatrices` in the French one. Fixed: the locale is now an argument, resolved per request by `LocaleUtils.resolveBundleLocale`. After the fix the same two browsers return `Sexe`/`DDN`/`Âge`/`Prochain rendez-vous` and `Sex`/`DOB`/`Age`/`Next Appt.` respectively. Live: `encounter-header-i18n-playwright-checks.js` | `fixed` |
| 42 | The chart header offered TWO links to the clinical calculators, labelled identically, differing only in passing `sex`/`age` in the query string instead of `demo` | Both anchors were in `newEncounterHeader.jsp`; the packaged install rendered `calcCount: 2` with `calcTexts: ["calculators", "calculators"]`. The `demo=` form survives, because `calculators.jsp` resolves sex and age from the record and `admin-fragment-navigation.test.js` already asserts patient attributes stay out of navigation URLs. Live: `encounter-header-i18n-playwright-checks.js` asserts exactly one, and `clinical-calculators-playwright-checks.js` still reaches the calculators by clicking it | `fixed` |
| 43 | The note-template search legend, its input placeholder and the template-shortcut overlay placeholder were literal English in the markup and in an inline script, so no translation could reach them | `<legend>Template Search</legend>` and `placeholder="template name"` in `ChartNotes.jsp`; `searchInput.placeholder = 'Search templates\u2026'` in `newEncounterLayout.js.jsp`. Reproduced on the packaged install: a `fr-CA` browser read `Template Search` / `template name`. Fixed with `encounter.templateSearch.*` keys in all five shipped bundles. Following the translation checklist, the new non-English entries are explicitly marked English placeholders pending verified translations. Live: `encounter-header-i18n-playwright-checks.js`; static: `EncounterChartHeaderI18nUnitTest` fails if the literals come back | `fixed` |

## 7. Alpha15 tester report: chart header English on first open, correct after F5 (September 2026)

| # | Defect | Evidence | Status |
|---|---|---|---|
| 44 | Reported against `2026.08.0-alpha15`: the fixed chart header (findings 41-43) is in the browser's language, except on "the most important initial load", which renders in English; an F5 of the same chart then renders it correctly | **Not reproduced.** An alpha15-equivalent `carlos-emr` package (this tree at the promotion commit, built with `CARLOS_WAR` from a JDK 25 build, `SKIP_DRUGREF=1 SKIP_EFORM_RENDERER=1`) was installed on an Ubuntu 26.04 systemd container and driven through the front door (`https://127.0.0.1/carlos`). The FIRST open of a chart in a fresh session was read and then re-read after `page.reload()`: identity labels, calculators control and template-search text were identical, and French, in every variant: Chromium 141 and Firefox 150; the Master Record E-Chart control and the schedule's `E` link (`encounter/IncomingEncounter` → `casemgmt/ViewForward` → `CaseManagementEntry?method=setUpMainEncounter`, the popup URL an F5 replays); popup and `encounter_open_in_tab=yes`; `Accept-Language` of `fr-CA,fr;q=0.9,en-US;q=0.8,en;q=0.7`, `fr-CA`, `fr` and `fr-CA,fr;q=0.9`. The shipped `encounter-header-i18n-playwright-checks.js` also passed on that install. Reading the request path found no state that differs between a first open and a reload of the same URL: `LocaleUtils.resolveBundleLocale` and JSTL both read only `Accept-Language` per request (no session, cookie or JVM-default input; the Struts `i18n` interceptor and `Dispatcher` only set the response locale), the packaged Tomcat launcher clears compiled JSPs on every start, and nginx sends `Cache-Control: no-store` on every page. English on a French browser therefore needs a request whose `Accept-Language` reached the server English-first or absent (absent would also render `???key???` for the page's own JSTL text), which no browser here produced. What changed so the next report is diagnosable: the chart page's `<html lang>` and `#header-top-row lang` now state the language the server negotiated for that very render, `LocaleUtils` logs the negotiated locale with the raw `Accept-Language` at DEBUG, and the browser check now asserts first open == reload and reads those attributes. To close this, the reporter's screenshot should be taken with DevTools open on the chart's `Accept-Language` request header, and the `lang` attributes read from the DOM of the English render | `open` |

## 8. Found while porting the eChart soft-wrap fix, #3955 (September 2026)

Initially reproduced on the packaged release while porting #3955. The follow-up in
#3990 fixes the save lifecycle and classic editor defects and validates the existing
document-upload repair. See [installed validation](pr3990-validation.md).

| # | Defect | Evidence | Status |
|---|---|---|---|
| 45 | Leaving a saved note incorrectly reports unsaved changes | #4010: observation input and calendar now use the same padded-hour format without trailing whitespace. The installed browser saves and opens another note with strict dialog recording. | `fixed` |
| 46 | Fast Save or Sign & Save races the editor's issue refresh | #4010: both issue fragments complete before a queued save; failures prevent incomplete saves. Callback tests cover failures/recovery and editor changes; installed checks hold each request and prove zero early saves and one eventual save. | `fixed` |
| 47 | Classic entry form opens an existing note with an empty editor | #4010: render the populated patient-scoped form bean and preserve form identity/patient/provider values. The browser compares exact loaded text with the stored synthetic note before modifying the editor. | `fixed` |
| 48 | A sibling element intercepts document-upload's navbar click | The current release base includes the repair. The existing installed document-upload check passed upload, chart navigation, forwarding and empty-file refusal. | `fixed` |
| 49 | Schema-valid NULL program/admission flags crash chart hydration | #4011: explicit converters preserve the primitive APIs and existing defaults for seven nullable flags. DAO regressions and the installed browser use nullable program/admission fixtures. | `fixed` |
| 50 | Caisi scheduler includes fail compilation and pooled view state leaks across providers | #4012: compile both dynamic includes as JSPs, encode values, handle empty program state, render one selector, and keep view state local to each tag use. Discharge jobs use injection and one recurring schedule. Five focused Java tests and strict installed Caisi login/legacy workflow pass. | `fixed` |

## 9. Found while validating Provider Linking Rules (#3971, September 2026)

Both findings come from the packaged Ubuntu 26.04 install described in
[deb-install-validation.md](deb-install-validation.md#provider-linking-rules-validation-2026-09-26).
Each was then reproduced against the unmodified `release/2026.08` code, so neither was
introduced by the #3971 change.

| # | Defect | Evidence | Status |
|---|---|---|---|
| 51 | Assigning a provider to an unclaimed HRM report, unlinking an HRM report from its patient, and re-linking it all fail with "Error encountered" and roll back | `mutateReport()` locks the report with `HRMDocumentDao.findForUpdate`, which loads the eager, unidirectional `matchedProviders` / `matchedDemographics` collections. The handlers then `EntityManager.remove()` rows from those collections, so the flush throws `TransientPropertyValueException ... HRMDocument.matchedProviders` (or `matchedDemographics`). Reproduced on the package through the new check's HRM step, and on unmodified `release/2026.08` by `HRMModifyTransactionIntegrationTest.shouldClaimUnclaimedReport_whenProviderIsAssigned`. Fixed with bulk deletes (`HRMDocumentToProviderDao.deleteByHrmDocumentIdAndProviderNo`, `HRMDocumentToDemographicDao.deleteByHrmDocumentId`); four integration tests pin claim, MRP routing, unlink and re-link. Live: `provider-linking-rules-playwright-checks.js` unlinks through the viewer's (remove) link and assigns through its autocomplete | `fixed` |
| 52 | Inbox review-status filter "Filed" returns a lab the unfiltered list does not | Live `inboxhub-filters` on the demo dataset, run after `lab-acknowledge` and `inbox-preview-acknowledge`: `Filed returned row HL7:44, which the unfiltered list does not contain`. Demo lab 44 is routed to provider 999998 with status `F`. The same failure reproduces with the unmodified `release/2026.08` WAR exploded over the same install, so it is not a Provider Linking Rules effect. It is the lab-side counterpart of finding 20 (an HRM row visible under All but under no status). Latest release fix `4d4f29b7d3d` preserves source row identity instead of collapsing accessions. Verified by the installed PR #4000 Ubuntu 26.04 `inboxhub-filters` run on 2026-09-27: type and New/Acknowledged/Filed partitions all pass. | `fixed` |

## 10. Found while expanding workflow coverage on the packaged install (October 2026)

Every row below was confirmed against `carlos-emr 2026.09.0~snapshot26` built from
`release/2026.08` and installed into an Ubuntu 26.04 container behind the packaged
nginx + ModSecurity front door, unless its status says `needs-live-check`. The check
named in the evidence asserts the correct behaviour, so it fails until the defect is
fixed, except where the evidence says *reported, not asserted*: that check observed the
defect and logs it but does not fail on it, so it can pass while the finding stays open.
None of them encodes the broken behaviour. The full record, with the routes
each check covers and the routes found to have no UI entry, is
[release-2026.08-workflow-coverage-expansion.md](release-2026.08-workflow-coverage-expansion.md).

| # | Defect | Evidence | Status |
|---|---|---|---|
| 53 | A GET to `admin/UpdateDemographicProvider` performs the bulk MRP/nurse/midwife/resident reassignment; the only privilege checked is `_admin.misc` read | `admin-update-demographic-provider`: a tokenless GET answered 200 and reassigned all three owned patients (the scriptlet in `updatedemographicprovider.jsp` runs on any method) | `open` |
| 54 | `report/ViewReportonbilledvisitprovider` (Reports ▸ PHCP provider settings) rewrites `secUserRole` with only `_report` read, including on a tokenless GET | `report-daysheet-labs`: `GET ?buttonUpdate=Update&providerId=X&nameX=doctor` replaced the owned provider's roles; Update on an unchanged admin row would demote it to doctor | `open` |
| 55 | `admin/FixRolesOnNotes` accepts GET `action=run`, reaching an unscoped `UPDATE casemgmt_note SET reporter_caisi_role=?`; the button is labelled "Run Report" and has no confirmation | `admin-role-management`: the GET probe reached the update (it 500s only because the probe sent a non-numeric role; `NumberFormatException` in the log) | `open` |
| 56 | `report/DemographicReport` writes on GET: "Save Query" stores a clinic-wide saved query and "Run Query And Save to Patient Set" stores set rows | `demographic-report-favourites` (asserted) and `patient-set-cohort` (reported, not asserted): each GET answered 200 and wrote one row; the action is not a mutator to `HttpMethodGuardFilter` and CSRFGuard does not cover GET | `open` |
| 57 | `setTicklerPreferences?method=saveTicklerTaskAssignee` writes on GET (`ProviderProperty2Action`, which also serves the `setProviderStaleDate` saves, has no POST guard) | `tickler-preferences` (run with `TICKLER_PREFS_DIRECT=true`): the GET answered 200 and stored the preference | `open` |
| 58 | `lookupListManagerAction` add/remove/order accept GET | `admin-lookup-lists`: `GET ?method=order&lookupListItemId=…` changed the stored display order | `open` |
| 59 | `web/dashboard/display/BulkPatientAction?method=addToDiseaseRegistry` writes a `dxresearch` row and a notification on GET | `dashboard-display`: the GET answered 200 and wrote the row (`addToDiseaseRegistry` is not in the filter's mutator list) | `open` |
| 60 | HRM list, display, print and download check only `_hrm` read, never patient-level access | `hrm-report-print-download`: a patient locked to another provider (correctly refused by the eDoc report) still had reports listed by `ViewDocList`, printed by `PrintHRMReport` and downloaded by `HRMDownloadFile` | `open` |
| 61 | Preferences ▸ Change Password enforces no password policy on the server; only `js/checkPassword.js.jsp` does | `password-change-preferences`: `ab1` POSTed with a valid CSRF token and the current password replaced the hash | `open` |
| 62 | Ticking Enable MFA on a security record silently clears the PIN and both PIN-lock flags; after MFA is later disabled the account signs in with the password alone | `login-mfa` (asserted in its last step, comparing a SHA2 digest of the PIN): `pin`/`b_LocalLockSet`/`b_RemoteLockSet` went from set/1/1 to NULL/0/0 (the disabled PIN inputs are not submitted and `securityupdate.jsp` treats that as a change) | `open` |
| 63 | Stored XSS in Jobs Management: the job name is concatenated into the list HTML unescaped | `admin-jobs`: a job named with `<b>bold</b>` rendered as an element | `open` |
| 64 | `demographicpdflabel.jsp` writes name, address, city and HIN unencoded, and that HTML is then posted for PDF conversion | `demographic-relations-pdf-labels` probe: an address containing `<Fixture>` lost the text in the rendered page | `open` |
| 65 | Drug-drug interaction warnings never appear in the Rx module | `rx-interactions-renal-luc`: staging ciprofloxacin and theophylline (DrugRef significance 3, major) shows no marker and the page never requests `rx/ViewInteractionDisplay` or `ViewUpdateInteractingDrugs`; every `updateCurrentInteractions()` call in `SearchDrug3.jsp` is commented out. The warning-level preference is stored but never read | `open` |
| 66 | Editing an E-Chart note, signed or not, overwrites the same `casemgmt_note` row instead of adding a revision; Note Revision History therefore shows only the current text | `note-browser-documents`: three saves kept one `note_id`; only the `history` text column retained earlier text | `open` |
| 67 | Several deletes throw `DetachedObjectException` because the entity is loaded by one DAO call and removed by another (Hibernate 7 rejects removing a detached instance): Insert a Template ▸ Delete, Security Records ▸ Delete Record, Query By Example ▸ delete favourite, Clear Photo | `encounter-templates` (`ProviderTemplate2Action:109`, silent), `security-record-admin` (`SecurityDelete2Action:125`, "Failed to delete security entry"), `report-query-by-example` (`RptByExamplesFavorite2Action:163`, 500; reported, not asserted), `patient-photo-upload` (`ClientImageDAOImpl.deleteClientImage:83`, 500) | `open` |
| 68 | Pages that build a `<form>` in JavaScript and submit it carry no CSRF token, so the POST is refused 403: Billing History ▸ Unbill, Report by Template ▸ Delete, eForm Restore (patient and independent), Billing Reconciliation ▸ Report/Summary/Settle, Messenger ▸ Link to Patient | `billing-on-correction-delete`, `report-by-template`, `eform-deleted-restore`, `billing-on-ra-import`, `messenger-demographic-link`; each POST logged `CSRF violation … Required Token is missing from the Request` | `open` |
| 69 | Forms rendered inside the `/administration` shell panels lose their CSRF token: eForm Groups remove/delete and Patient Independent eForms delete are refused 403 | `eform-groups`, `eform-deleted-restore` (`CarlosCsrfGuardFilter` 403 on `eforms/removeFromGroup`, `eforms/delGroup`, `eform/removeEForm`) | `open` |
| 70 | Encounter forms: after the first Save, a second Save, Print or Save and Exit is refused 403 because the redisplayed page has no CSRFGuard script or token | `clinical-forms-save-reopen` (Mental Health Form 1, Palliative Care) | `open` |
| 71 | Administration ▸ Unlock Account answers 500 whenever any login is tracked, so an administrator cannot unlock an account exactly when one is locked | `account-lockout-unlock`: `NonUniqueDiscoveredSqlAliasException [provider_no]` at `SecurityDaoImpl.findByProviderSite:136` (`select * … join providersite`) | `open` |
| 72 | The packaged WAF blocks three workflows: Query By Example run (CRS 942100 on `ARGS:sql`), Report by Template edit of a template with `<param>` (941160 on `ARGS:xmltext`) and the Messenger attachment preview/attach (`messenger/Doc2PDF` posts rendered page HTML; 932100/932105/932130/932140/934100/941100/941110/941140/941180 → 949110) | `report-query-by-example` (reported, not asserted), `report-by-template`, `demographic-relations-pdf-labels`, `messenger-attachments`; rule ids from `/var/log/nginx/error.log` | `open` |
| 73 | `HttpMethodGuardFilter` answers 405 to the GET form-openers of Customize Measurements (Add Measurement Type, Add Measuring Instruction, Add/Remove/Remap Measurement Mapping, the post-save group page), and the mapping Search buttons POST to GET-only View routes | Live run on the packaged install: `measurement-type-group-admin`, `measurement-map-admin` | `open` |
| 74 | Gate actions that only accept GET receive POSTs from their own pages and answer 405: Custom Print ▸ Preview (`ViewTemplateFlowSheetPrint`), the Note Browser controls (`casemgmt/ViewNoteBrowser`), eForm Groups add/add-to-group (success result forwards the POST to `efmmanageformgroups`) | `flowsheet-patient-customization`, `note-browser-documents`, `eform-groups` | `open` |
| 75 | `<rewrite:reWrite>` builds URLs from `request.getRequestURI()`, which is the `/WEB-INF/jsp/…` path after a gate forward, so the generated links 404: E-Chart ▸ Preventions ▸ Print and eDoc ▸ Combine PDF (17 uses are affected) | `prevention-admin` (404 on `/carlos/WEB-INF/jsp/prevention/printPrevention`), `document-refile-combine` | `open` |
| 76 | JSPs with JavaScript identifiers split across lines, so the whole page script fails to parse: the Note Browser and the Document Browser | `note-browser-documents` (`noteBrowser.jsp` lines 243/408/429/434), `document-refile-combine` (`documentBrowser.jsp` 404-405) | `open` |
| 77 | The site-wide `Cross-Origin-Opener-Policy: same-origin` cuts `window.opener` for popups opened from the chart, breaking opener contracts: the consultation confirmation never closes, and the eForm Add list throws a TypeError on unload | `consultation-edit-status` (reported, not asserted), `eform-groups` (`efmformslistadd.jsp` / `efmpatientformlistdeleted.jsp` `updateAjax()`) | `open` |
| 78 | Regression from the null-safe encoder migration: two JSPs compare encoded output with the string `"null"`, which is now always unequal; the Messenger patient-search popup writes empty values into the message and closes itself on load | `messenger-demographic-link`, `messenger-attachments` (`msgSearchDemo.jsp:219`; the same comparison at `ViewMessage.jsp:615`) | `open` |
| 79 | Master Record waiting list is read-only exactly when the feature is on (`DEMOGRAPHIC_WAITING_LIST=true`, the packaged default), and the waiting-list management page has no navigation entry | `waiting-list` (`edit.jsp` sets `wLReadonly` when the property is true; `add-form-clinical.jsp` has the correct logic) | `open` |
| 80 | Administration ▸ Schedule Management ▸ Search/Edit/Delete Groups ▸ Delete always answers 500 (`adminnewgroup.jsp` parses `CSRF-TOKEN` as a group member); the provider-side Save/Delete leaves a blank page | `my-groups`, reported, not asserted (`StringIndexOutOfBoundsException` at `adminnewgroup_jsp:230`) | `open` |
| 81 | Merge Patient Records accepts a record id that does not exist and writes an orphan `demographic_merged` row and chart privilege | `demographic-merge` probe, reported, not asserted: POST with a missing id answered success | `open` |
| 82 | Patient-set saves duplicate membership | `patient-set-cohort` probe, reported, not asserted: saving the same patient to the same set twice wrote two `demographicSets` rows | `open` |
| 83 | Ontario Lab Forwarding Rules (Administration ▸ Labs/Inbox) cannot load or save any provider's rules: the change handler is bound to `#providers-selection` while the select is `#provider-selection` | Live run on the packaged install: `lab-forwarding-rules` | `open` |
| 84 | Billing Correction ▸ Payer search: picking an address throws a syntax error (the handler is JavaScript-encoded twice) | Live run on the packaged install: `billing-on-invoice-third-party` | `open` |
| 85 | Payment Received omits payments dated on the End Date (`paymentdate < end` against `<=` for invoices), so today's payments never appear with the default range | Live run on the packaged install: `billing-on-payment-status` | `open` |
| 86 | The claims error report upload stores its rows and then answers 500 (`billingEAreport.jsp:63` reads a bean property the parser does not have) | `billing-on-ra-import` (`JspPropertyNotFoundException claimsErrorReportBeanVector`) | `open` |
| 87 | CDM reports: "frequency of relevant tests" never prints a line (no `setLessThan`), "patients seen" shows the last demographic number instead of the count, and invalid-date errors are lost on redirect | Live run on the packaged install: `report-cdm` | `open` |
| 88 | Decision support: the guideline list answers 500 (`${guideline.status == 'A'}` on a `char`), and the detail page evaluates every condition with the first condition's cached rule | Live run on the packaged install: `decision-support-guidelines` | `open` |
| 89 | Dx Registry: choosing a named quick list in the chart sidebar answers 500, Edit Associations returns JSON followed by the whole JSP, the association CSV upload stores nothing silently, and the registry report's quick-list add adds nothing | Live run on the packaged install: `dx-registry-quicklist`, `dx-registry-status-update` | `open` |
| 90 | Health Tracker Custom Print omits patient and provider customisations; the measurement group page hides the last value of readings with no measuring instruction | `flowsheet-patient-customization`, `measurement-group-entry` | `open` |
| 91 | Old-immunization schedule del/restore do nothing (forms nested inside another form), and a template created in the UI loses its name (`setName` posted, `name` bound) | Live run on the packaged install: `immunization-schedule-config` | `open` |
| 92 | Tickler preferences: the Preferences link opens the stale-note-date page, the preference page throws a TypeError, reopen does not show the stored provider, and "Default" stores the literal `default` | Live run on the packaged install: `tickler-preferences` | `open` |
| 93 | `ticklerDemoMain.jsp` answers 500 for any patient who has a tickler (`LazyInitializationException` on `Tickler.comments`) | Live run on the packaged install: `workflow-tickler-suggested-text` | `open` |
| 94 | Jobs Management: every job-name click raises a syntax error (`javascript:void();`) and the schedule dialog never shows the stored cron, so Save resets the job to every minute | Live run on the packaged install: `admin-jobs` | `open` |
| 95 | Key Manager buttons post to `/admin/…` without the context path (404) | Live run on the packaged install: `admin-api-keygen` | `open` |
| 96 | Manage Emails ▸ Resolve shows RESOLVED but never updates the log (`setResolved` is not dispatched); the shell Help ignores the saved Help Link | Live run on the packaged install: `admin-email-config`, `admin-sites-clinic-numbers` | `open` |
| 97 | Audit Log Purge never shows its form; the all-providers Security Log Report omits failed logins and other-site providers for `_site_access_privacy` holders | Live run on the packaged install: `admin-audit-log`, reported, not asserted | `open` |
| 98 | Dashboard drill-down: Assign Tickler 404s on a doubled context path, Add To Disease Registry posts to an undefined href (500), and Drill Down 403s for users without `_dashboardChgUser` | Live run on the packaged install: `dashboard-display` | `open` |
| 99 | Document Edit throws `ReferenceError: validDate`; Lab Row Display throws `ReferenceError: scanDOM`; View Mapping throws `ReferenceError: stripe`; the Rx Qty field throws `ReferenceError: Insertion` | `document-refile-combine`, `lab-manual-entry-cumulative`, `measurement-map-admin`, `rx-interactions-renal-luc` | `open` |
| 100 | Pages link stylesheets the webapp does not ship (`admin/bcArStyle.css`, `/styles.css`, `/encounterStyles.css`, `decision/annualreview/antenatalrecord.css`, `${request.contextPath}`-relative facility CSS) | `security-record-admin`, `login-mfa`, `measurement-type-group-admin`, `immunization-schedule-config`, `antenatal-annual-review-planner` | `open` |
| 101 | Assign Role/Rights to Object throws a TypeError on every role pick; the day-sheet Administration link is shown to plain doctors and answers 403 | Live run on the packaged install: `admin-role-management` | `open` |
| 102 | Schedule Setting's date popup preselects the first template instead of the day's, and Template Code edit truncates at an apostrophe; Holiday and Template Code Setting are hidden from the seeded admin by `_site_access_privacy` | Live run on the packaged install: `schedule-admin-settings` | `open` |
| 103 | Encounter forms: Vascular Tracker 404s (`SetupForm.do` unmapped), Annual "Print Page" 404s, the MMSE image 404s, and Discharge Summary / Mental Health Form 1 saves throw TypeErrors | Live run on the packaged install: `clinical-forms-save-reopen` | `open` |
| 104 | `demographic/ValidateSwipeCard` answers 500 on a malformed track, and as a GET form it writes the full card track (HIN, name, birth date) into the access log | Live run on the packaged install: `patient-swipe-card-search` | `open` |
| 105 | Demographic Import reports "Imported Successfully" for a file whose only patient was refused as a duplicate, and leaves uploaded CDS files and event logs in the temp directory indefinitely | Live run on the packaged install: `demographic-cds-import` | `open` |
| 106 | The Lab and patient-photo upload paths give no feedback on refusal; the Rx "Drug Info" link sends the drug name to an off-host plain-HTTP site | `patient-photo-upload` (refused non-image re-renders silently), `rx-interactions-renal-luc` (`RxDrugInfo2Action` redirects to `http://resource.oscarmcmaster.org/…`) | `open` |
| 107 | Mutators found by code reading to accept GET, not probed live because a probe would perform the change: `EmailSend2Action`, `HandleMessages` delete, the consultation config actions (`AddService`, `DelService`, `EnableConRequestResponse`, `UpdateInstitutionDepartment`, `UpdateServiceSpecialists`), seven eForm group/remove actions, `ForwardingRules2Action`, `UnlinkDemographic2Action`, `ClientImage?method=deleteImage`, `IssueAdmin2Action`, `SystemMessage?method=save` | Source reading during the coverage pass; none is registered in `MutatorActionGetRejectionContractUnitTest` | `needs-live-check` |
| 108 | Code-read authorization gaps: `FacilityManager2Action` writes before any privilege check; `ManageEmails2Action` has none; `ViewDisplayRxRecord2Action` and `CombinePDF2Action` check no patient access; `BillingSettings2Action` and the consultation config writers need only read | Source reading during the coverage pass (`FacilityManager2Action`, `ManageEmails2Action`, `ViewDisplayRxRecord2Action`, `CombinePDF2Action`, `BillingSettings2Action`); live validation remains pending | `needs-live-check` |
| 109 | Code-read data-integrity risk: Modify Measurement Style ▸ OK changes the style row's primary key, so the merge overwrites another group's style | `EctEditMeasurementStyle2Action.changeCSS` calls `m.setId(styleSheet)`; not driven, to protect the demo data | `needs-live-check` |
| 110 | The Ontario billing report renders the request-header map, including the session cookie, into every column header, which defeats HttpOnly; every report cell is also empty | `billing-on-reports-inr-eoy`: `billingONNewReport.jsp:187/195` loop on `var="header"`, which the EL implicit object `header` shadows | `open` |
| 111 | End Year Statement ▸ Print PDF answers 500 (`UnrecognizedPropertyException "isForPrompting"` in `end_year_statement_report.jrxml`); INR Batch Billing's patient link opens a POST-only action by GET (405); Upload MOH File ▸ L report renders blank (`ES.xsl` 404) | Live run on the packaged install: `billing-on-reports-inr-eoy` | `open` |
| 112 | Manage Billing Form cannot add, retype or delete a form: Add's form has no `action` (no CSRF token injected) and Change/Delete post a runtime-built form; dx Search ▸ Update is refused because CSRFGuard's client throws on result inputs named with numeric codes | `billing-on-admin-config` (403 on `DbManageBillingformAdd`/`Billtype`/`Delete` and `BillingDigUpdate`; `TypeError` at `/carlos/csrfguard:371`) | `open` |
| 113 | Billing code Search ▸ update raises `SyntaxError: Illegal return statement`, Confirm from the correction page never attaches the code, and Manage Code Styles drops a hand-typed colour | Live run on the packaged install: `billing-on-admin-config`, `billing-on-gst-css-benefit` | `open` |
| 114 | Administration ▸ Billing Settings (Ontario) ▸ Save, with no options shown, writes NULL rows for the three BC-only `property` keys and the two invoice `SystemPreferences` keys, and would overwrite existing values with NULL | Live run on the packaged install: `admin-sites-clinic-numbers` compares the property and SystemPreferences rows before and after the Save and asserts the difference in its last step | `open` |
| 115 | Billing History ▸ Unbill decides whether to refuse only from the client-posted `billCode`: `BillingDeleteNoAppt2Action` never reads `billing_on_cheader1.status`, so a `_billing` writer posting `billCode=O` for a bill in status B (submitted to OHIP) or S would mark it deleted | Source reading of `BillingDeleteNoAppt2Action`; `billing-on-correction-delete` proves only the honest refusal (a status-B bill posted with its stored code is refused). The forged-code post was not sent, to protect the demo data | `needs-live-check` |
| 116 | Customize Measurements ▸ Delete style sheet removes only `measurementGroupStyle` rows; an unused style sheet's `measurementCSSLocation` row and uploaded file stay, and the copy-without-replace upload then refuses the same name | Source reading of `EctDeleteMeasurementStyleSheet2Action` and `EctAddMeasurementStyleSheet2Action` (`Files.copy` without `REPLACE_EXISTING`); `measurement-type-group-admin` asserts the correct behaviour, but on 2026.08 it stops earlier at the documented 405 | `needs-live-check` |

## 11. Found by the stored-markup (xss-poison) sweep (October 2026)

Each row was confirmed live on the same packaged install as §10 by an `xss-poison-*` check
(see [release-2026.08-workflow-coverage-expansion.md](release-2026.08-workflow-coverage-expansion.md#stored-markup-xss-poison-sweep)),
then traced to the line that prints the value. The fixture text is inert (`<i data-xp="N">`, quotes,
a backslash, `&amp;`, `</script data-xp>`), INSERTed so the WAF does not refuse it, as imported or
legacy data would arrive. A finding here means the stored value became an element in the page or
broke its script; none of the payloads can run code, so these are encoding defects, not demonstrated
exploits.

| # | Defect | Evidence | Status |
|---|---|---|---|
| 117 | `<oscar:nameage>` prints the patient's name unencoded; 19 JSPs use it, among them the eDoc patient document list, the Disease Registry, Manage Contacts and the Msg inbox | `DemographicNameAgeTag.java:66` (`out.print(nameage)`); live: `xss-poison-documents-inbox`, `xss-poison-master-record`, `xss-poison-echart`, `xss-poison-messenger-consult` (`documentReport.jsp:475`, `dxResearch.jsp:247`, `ManageContacts.jsp:362`, `DisplayMessages.jsp:610`) | `open` |
| 118 | Master Record view and Edit form print stored patient and provider values unencoded: official/spoken language, contact role, cytology number, phone extensions, cell, email, health card type, the alert, and the doctor/nurse/midwife select labels | `xss-poison-master-record` (`demographic/edit-view.jsp:295,310,416,508,530,734,740,746,794,810`; `edit-form-clinical.jsp:260,277,294`) | `open` |
| 119 | The E-Chart left navbar prints item titles raw, and the Rx, Tickler and eForm modules build those titles from stored text without encoding; the Rx drug list prints drug instructions raw | `xss-poison-echart` (`encounter/LeftNavBarDisplay.jsp:338`; `EctDisplayRx2Action.java:112,123`, `EctDisplayTickler2Action.java:96,104`, `EctDisplayEForm2Action.java:123`; `rx/ListDrugs.jsp:326`) | `open` |
| 120 | The consultation request form writes the saved letterhead name into a script string unescaped, so a name with a double quote is a SyntaxError that stops the form's script | `xss-poison-messenger-consult` (`ConsultationFormRequest.jsp:3525`, `switchProvider("${pageScope.consultUtil.letterheadName}")`) | `open` |
| 121 | eForm administration prints stored names unencoded: role names in the upload form's select, patient-independent instance names and subjects; the group Delete confirmation re-parses the attribute-encoded group name as HTML | `xss-poison-eform`, `xss-poison-admin-users-billing` (`eform/partials/upload.jsp:127`; `efmmanageindependent.jsp:149,151`; `efmmanageformgroups.jsp:148` read back by `efmFooter.jspf:70` with `.html()`) | `open` |
| 122 | Administration lists print stored names unencoded: group members, document types, report-template titles and descriptions, and the provider/role/quick-list selects of Age-Sex, Disease Registry, Add a Group, Access Control, Manage Faxes, Demographic Export and Fix notes with invalid role | `xss-poison-admin-detail`, `xss-poison-admin-reports-system` (`admindisplaymygroup.jsp:117`, `displayDocumentDescriptionTemplate.jsp:291`, `reportByTemplate/homePage.jsp:129,131`, `oscarReportAgeSex.jsp:192`, `oscarReportDxReg.jsp:168,247`, `adminnewgroup.jsp:177`, `groupnoacl.jsp:162,180`, `manageFaxes.jsp:371`, `demographicExport.jsp:483`, `fixRolesOnNotes.jsp:93`); Jobs Management is finding 63 | `open` |
| 123 | The schedule month view's provider select prints the (truncated) provider name unencoded | `xss-poison-schedule` (`provider/appointmentprovideradminmonth.jsp:592,619`): the cut-off payload became an element inside the option | `open` |

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
