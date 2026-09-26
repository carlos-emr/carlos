# BC billing simulation / Teleplan report HTML encoding (#3950)

`billingSim.jsp` prints the report HTML that `genSimulation.jsp` collects from `ExtractBean.getHtmlCode()` without encoding it, because the value is a whole table. Teleplan submissions render the same way through `TeleplanFileWriter`. The rows were built by string concatenation from patient and claim records. Unencoded values included:

- patient name
- PHN
- fee code, amounts and dx codes
- the provider billing number, in the header and footer
- the billing number placed inside the `onClick="openBrWindow('…')"` URL

A patient or WCB worker name containing markup was therefore stored XSS for every billing or admin user who opened the simulation or report.

The fix makes `HtmlTeleplanHelper`, which already encoded its own MSP rows, the single BC row builder:

| Builder | Before | After |
|---|---|---|
| `bc/MSP/ExtractBean` `htmlLine` / header / footer | raw concatenation | delegates to `HtmlTeleplanHelper.htmlLine` / `htmlContentHeaderGen` / `htmlFooter` |
| `bc/MSP/WcbSb` `getHtmlLine` / `validate()` row | raw, `billing_no` in the `onClick` URL | `wcbHtmlLine` / `wcbCorrectionErrorRow` |
| `bc/Teleplan/WCBTeleplanSubmission` `getHtmlLine` / `validate()` row | raw | `htmlLine` / `adjustBillErrorRow` |
| legacy `billings/MSP/ExtractBean` header, first row, continuation row, footer | raw | `htmlContentHeaderGen` / `htmlLine` / `continuationLine` / `htmlFooter` |

The helper uses one encoding contract for every row:

- Cell text goes through `SafeEncode.forHtmlContent`.
- The id inside the popup URL is URI-component encoded first, then JavaScript-attribute encoded, so `&`, `#`, `'` and `"` cannot add parameters or leave the string or the attribute.

The correction page and parameter name are compile-time constants, never request data. The Ontario equivalents (`OhipClaimFileService`, `OhipClaimExtractService`) already followed this pattern.

Reference: openo-beta/Open-O#2438 (LiamStanziani) made the same server-side encoding change in its billing report generators. It is ported here into CARLOS's shared helper rather than patched line by line.

The browser check also found a pre-existing page bug. `billingSim.jsp` `checkData()` read `document.forms[0].provider`, but the select is named `providers`, so every "Create Report" submit threw a `TypeError`.

## Coverage

**Java unit tests** (`@Tag("unit")`):

- `ExtractBeanHtmlUnitTest` (BC): `<script>` in the name; markup in PHN, fee code and amount; a JavaScript-string breakout billing number; table shape; provider number in the header.
- `WcbSbHtmlUnitTest`: WCB row encoding and the correction-link id; the validation error row and the empty result.
- `WCBTeleplanSubmissionHtmlUnitTest`: row encoding and the validation row.
- `HtmlTeleplanHelperUnitTest`: WCB row, correction error rows, continuation row, and footer with a string count.
- `billings/MSP/ExtractBeanHtmlUnitTest` (legacy): drives `dbQuery()` in dry-run mode over a mocked `dbExtract` and asserts the header, first row, continuation row and footer are encoded.

**Changed-line coverage** from `python3 scripts/coverage/changed_line_audit.py target/site/jacoco/jacoco.xml origin/release/2026.08 HEAD`:

- 46 of 47 changed executable Java lines covered (97.9%).
- No changed file left uncovered.
- All 1,581 tests in the `billings` package pass.

**Browser check** `billing-bc-simulation-encoding` (manifest tier `core`, provinces `BC`, `npm run test:billing-bc-simulation-encoding-playwright`):

1. Creates a FAKE provider with a unique OHIP number through Admin ▸ Add Provider. It must go through the app: `ProviderDao.getActiveProviders()` is cached for 5 minutes, and only an app-side save evicts it.
2. Seeds one MSP `billing`/`billingmaster` claim with markup in the patient name, PHN and fee code.
3. Submits the real simulation form and asserts:
   - the three values render as literal text in the claim row;
   - no element was parsed from them and no injected handler ran;
   - the adjustment link keeps its `billingmaster_no=NNNNNNN` shape;
   - the form submit raised no page error.
4. Verifies the simulation stayed a dry run: claim statuses stay `O` and no `log_teleplantx` rows are written.
5. Removes every owned row.

On an Ontario install the check reports SKIP.

## Verification (2026-09-26)

**Packages.** Built from this branch with `dpkg-buildpackage` in `ubuntu:26.04`, with `CARLOS_WAR` prebuilt and `SKIP_DRUGREF=1 SKIP_EFORM_RENDERER=1`. Lintian reported no errors.

**Install.** Installed in a fresh `ubuntu:26.04` systemd container, preseeded as BC with demonstration data. `carlos-ctl finish-install` was needed only because that container has no IPv6 (see [deb-install-validation.md](deb-install-validation.md)). Afterwards `carlos-ctl check` reported all checks passed.

**Browser checks** (`run-playwright-suite.js --province BC`):

- **New check:** `billing-bc-simulation-encoding` passed all four steps.
- **Neighbouring BC check:** `billing-bc-associations` passed.
- **Smoke tier:** run with `login` skipped, because that check needs `TEST_PASSWORD_HASH` seeding. Nine checks passed, including `appointment-lifecycle` and `mutator-get-rejection-live`, which were rerun after adding `poppler-utils` and the source tree. `drug-search` and `eform-render` failed only because DrugRef and the bundled eForm Chromium were deliberately left out of this dev build.

**Negative control.** With the pre-fix `bc/MSP/ExtractBean.class` swapped into the installed webapp, the browser check fails: the payload is parsed into an `<img>` and the name cell renders empty. The owned fixtures were still removed after the failure.

No migration or schema change is involved.
