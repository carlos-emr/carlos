# Issue 4133 fix and validation

[Issue 4133](https://github.com/carlos-emr/carlos/issues/4133): the packaged nginx +
ModSecurity front door answered three legitimate workflows with 403 — Query By Example,
Report by Template ▸ Edit Template, and Messenger chart-PDF attachments (preview and attach,
including the patient-information PDF). Recorded as finding 72 in the 2026.08 coverage work
(PR #4126).

## What changed

| Workflow | Fix | Why this shape |
|---|---|---|
| Query By Example (`oscarReport/RptByExample`, `ARGS:sql`) and its favourites (`RptByExamplesFavorite`, `ARGS:query` / `ARGS:newQuery`) | Exclusions **1400** and **1401** unhook those arguments from `attack-sqli` only, on POST to those path-anchored routes only. The favourites picker no longer posts the stored SQL a second time (`selectedRecentSearch` had no reader). | The input *is* SQL, so the SQLi family cannot tell a report from an attack on it. The trade-off is explicit: the exclusion also stops the WAF inspecting malicious SQL in these arguments, so the application validator is the control, not a second layer. What may run is decided by the application: `QueryByExampleSqlValidator` (one SELECT over the application schema, no sensitive tables, no locking/`INTO`/unlisted functions) and a read-only, row- and time-limited, audited execution. A favourite is stored text that only ever runs through the same path. |
| Report by Template editor (`addEditTemplatesAction`, `ARGS:xmltext`) | Exclusion **1402** unhooks `xmltext` from `attack-sqli` and `attack-xss`, on POST with `action=add|edit` only (phase-2 chain, as 1142). New `ReportTemplateSqlValidator`: every `<query>` statement (each part of a sequenced template) and every `<param-query>` must pass the run-time report SELECT rule **at save**; a stored `<param-query>` is re-checked before it runs. add/edit/delete and the file upload are POST-only and require `_report` write (delete had no write check). The editor encodes the stored template in its textarea and the reflected hidden fields, and re-shows the author's text after a refused save (an upload too). Because `xmltext` is no longer XSS-inspected, every page that lists a template's title or description (template home page, template list, result page links) now encodes it. The unused `updateTemplateXml`/`loadInReports` pair (which deleted every template) is removed. | The document is markup carrying SQL. Before, an `UPDATE`/`DELETE` was accepted at upload and only refused when the report ran, and a `<param-query>` ran unchecked as soon as the template was opened. |
| Messenger ▸ Attach Patient (`messenger/Doc2PDF`) | **No exclusion.** The chooser now posts the patient and item keys (`demographic`, `encounter`, `prescriptions`); `MsgPdfAttachmentResolver` maps each to a fixed route and a server-computed title; `MsgAttachPDF2Action` checks `_msg` write, patient access and the item's own module read, includes the route in the same request as a GET carrying only the route's parameters, and converts the captured page. All ticked items attach in one POST. `demographicpdflabel.jsp` encodes every patient field (finding L61). `Doc2PDF` strips HTML comments, which may legally contain `--` but break the XHTML parse (the drug-profile page has one, so the prescriptions item was always stored as a failed render). | Converting browser-posted HTML let any `_msg` writer store arbitrary markup as a "chart PDF", and the WAF correctly refused the posted page. Rendering from ids removes both problems. |

`ReportAuthoringWafExclusionRegressionTest` pins rules 1400–1402 (route, POST, phase, exact
tag/argument pairs, the `add|edit` key, and that `Doc2PDF`/`srcText` have no exclusion).

## Live validation (2026-10-01)

**Packages.** `carlos-emr_2026.09.0~snapshot26_amd64.deb` and
`carlos-emr-drugref_2026.09.0~snapshot26_all.deb` built with `dpkg-buildpackage -us -uc -b`
inside an `ubuntu:26.04` container from this branch (`release/2026.08` + the fix), DrugRef at
the `debian/drugref.pin` revision, the pinned Chromium, and `carlos-ctl` 1.1.1 from
`debian/carlos-ctl.pin`. `lintian --fail-on error`: no errors.

**Install.** Ubuntu 26.04 systemd container (`--privileged`, host network, cgroup2 mounted
for a cgroup-v1 host, as section 2 of [deb-install-validation.md](deb-install-validation.md)
describes), the section 3 preseed (Ontario, self-signed TLS, `reset-seed-admin=true`, demo
data). The first nginx start failed on the stock `[::]:80` site (no IPv6 in the container, the
documented artefact) and the package's retry started it. `carlos-ctl check`: **all checks
passed** (35 Flyway migrations, WAF blocking the probe SQLi, live DrugRef lookup). The
first-login reset ran once through `drugref-update-playwright-checks.js`.

**Before/after on the WAF.** With rules 1400–1402 removed from the installed
`REQUEST-900-EXCLUSION-RULES-BEFORE-CRS.conf` and nginx reloaded, every one of these POSTs
through `https://127.0.0.1/carlos` was answered **403**; with them restored, the legitimate
ones answer **200** and the probes stay **403**:

| Request | Without 1400–1402 | With |
|---|---|---|
| QBE `select demographic_no from demographic limit 1` | 403 | 200 |
| QBE with `>`, `<`, `<>`, `LIKE`, `concat`, `UNION`, `JOIN … GROUP BY … HAVING`, `date_format`/`date_sub` | 403 | 200 |
| Favourite save (`query`) and editor open (`newQuery`) | 403 | 200 |
| Template add with `<param>` + `<param-query>`; template add with `>=`/`<` and `UNION` | 403 | 200 |
| QBE with an injection payload in another argument (`selectedRecentSearch`) | 403 | **403** |
| QBE SQL on a GET | 403 | **403** |
| Template `action=delete` with `<script>` in `xmltext` | 403 | **403** |

**Browser checks** (`EXPECT_FRONT_DOOR=true`, run from the host with Playwright's Chromium over the container's shared network, MariaDB asserted),
on a fresh install of the final build:

| Check | Result |
|---|---|
| `report-query-by-example-front-door` (new) | PASS — runs real queries and shows the owned patient; history row written; an `UPDATE` reaches the app and is refused (patient unchanged, nothing recorded); favourite saved from View Query History and run again from the picker without `selectedRecentSearch`; other-argument injection and GET SQL are 403 |
| `report-by-template-editor-front-door` (new) | PASS — parameterised template saved from the textarea; Edit ▸ Done stores the change and the configuration page fills the `<param-query>` list; a `DELETE` `<param-query>` is refused at save with the typed XML kept and the row unchanged; a stored `</textarea><b …>` stays text in the editor; a stored title/description carrying markup is listed as text on the template home page and list (fails against the previous `homePage.jsp`); add/edit/delete GET are 405 and the template survives; markup on `action=delete` is 403 |
| `messenger-pdf-attachments-server-render` (new) | PASS — chooser has no hidden frame and offers keys only; Preview posts `previewItem=demographic` (no page HTML) and returns a PDF with name, city, postal code and the address `… O'Neil & <Fixture> Lane` reproduced literally; forged `srcText` does not reach the PDF; unknown key is 400; Attach makes **one** request for both ticked items and shows the compose indicator; with an encounter record the encounter item renders that record; the sent message stores two `OK` PDFs with server titles, linked to the patient |
| `mutator-get-rejection-live` | PASS — 144 probes across 72 routes, now including `uploadTemplates` |
| `messenger`, `pr-hardening`, `anonymous-access-refused`, `clinical-freetext` | PASS |

The first live runs found two defects in the first cut of the Messenger change, both fixed on
the branch before the final build: the include kept the outer POST, so the encounter print's
GET-only gate answered 405; and the drug-profile page's `--` comment broke the XHTML parse.

**Automated suites.** `mvn test -Dgroups=unit` (JDK 25, Ubuntu 26.04 container): 13,992 tests;
the 31 that failed in a bare container (missing `libharfbuzz`/fonts, no `node`, non-UTF-8
locale) were re-run in a container with those present and all 280 tests in those classes pass.
`npm run test:scripts`: 2,276 pass, 4 skipped. `debian/assets/tests`: 45 OK. Pinned
`carlos-ctl` 1.1.1 suite with `CARLOS_SRC` on this tree: 1,468 OK. Lint: encoder null-safety,
BDD naming, SecurityException message convention and Struts DTD all pass.

## Interaction with PR #4126

PR #4126's `messenger-attachments` and `demographic-relations-pdf-labels` checks drive the old
hidden-frame flow (they wait for the item page to load into `srcFrame` and read `srcText` from
the POST), and its `report-by-template` check expects a write statement to be *stored* and only
refused when the report runs. Those expectations describe the behaviour this fix removes; they
need to follow the new contract (item keys; refusal at save) when #4126 lands.
