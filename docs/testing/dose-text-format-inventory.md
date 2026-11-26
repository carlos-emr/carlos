# Dose, strength and quantity text inventory

Issue [#6185](https://github.com/carlos-emr/carlos/issues/6185). Target: `release/2026.08`.

ISMP Canada's *Do Not Use* list bans two numeric forms in medication text: a trailing zero
("1.0 mg" is read as 10 mg) and a missing leading zero (".5 mg" is read as 5 mg). This inventory
lists every place where CARLOS turns a dose, strength or quantity **number** into text that a
person reads or another system receives, and what each one printed. A number that is changed on
the way to text (rounded, split, truncated) is listed too, because the reader gets a different
number.

Each row was read in the source. The "Was" column gives the text for the stored values
1, 0.5, 0.0625, 500 and 10 000 000, in that order.

## The shared formatter

`io.github.carlos_emr.carlos.utility.DoseText` writes the exact plain decimal: no trailing zero,
always a leading zero, no exponent, no digit grouping, a dot whatever the server's locale is, and
no rounding. For the five values above it writes `1`, `0.5`, `0.0625`, `500`, `10000000`.

| Method | Use |
| --- | --- |
| `of(float)` | A dose held as the `drugs.takemin`/`takemax` float. Shortest decimal that reads back to the same float. |
| `of(double)` | A strength or quantity held as a double. |
| `of(BigDecimal)` | An exact decimal. |
| `ofNumericText(String)` | A number that arrives as text (DrugRef's "250.0", a typed ".5"). Text that is not one number is returned unchanged. |
| `range(float, float)` | "1-2", or the single dose when both bounds are equal. |

Do not format such a number at the call site. `DoseTextUnitTest` covers 1.0, 0.5, 0.0625, 500.0,
1.0E7, the ranges, negative zero, non-finite values, a non-English default locale and oversized
exponents.

## Defects fixed

| Kind | Where the text is produced | Was | Reaches |
| --- | --- | --- | --- |
| Dose | `prescript/util/RxUtil.java:325` `FloatToString`, three-decimal HALF_EVEN rounding | `1`, `0.5`, **`0.062`**, `500`, `10000000`; 0.0004 printed `0` | Every row marked "via `FloatToString`" below |
| Dose | `commn/model/Drug.java:338` `getDosageDisplay` (via `FloatToString`) | as above | Rx record, `rx/DisplayRxRecord.jsp:287` |
| Dose | `prescript/data/RxPrescriptionData.java:1623` `getDosageDisplay`, `:1675` `getRxDisplay`, `:1757` `getFullFrequency` (via `FloatToString`) | as above | BC health passport forms `form/formbchppg1.jsp:97-98`, `formbchppg2.jsp:97-98` (saved with the form); VT form `form/pageUtil/FrmSetupForm2Action.java:313`; Rx page drug list `rx/SearchDrug3.jsp:1038` |
| Dose | `prescript/data/RxPrescriptionData.java:1332`, `:1344` `getTakeMinString`/`getTakeMaxString` (via `FloatToString`) | as above | Staging card Min/Max, `rx/prescribe.jsp:115-116`, `:403-404`; legacy editor hidden fields `rx/WriteScript.jsp:944-945`, `:1220-1221`. **Both post the text back as the dose**, so 0.0625 was stored as 0.062 on the next save |
| Dose | `rx/EditFavorites2.jsp:329`, `:332` printed the float | **`1.0`**, `0.5`, `0.0625`, **`500.0`**, **`1.0E7`** | Edit Favorites "Take" inputs |
| Dose | `rx/CopyFavorites2.jsp:191` printed the double with EL | **`1.0`**, `0.5`, `0.0625`, **`500.0`**, **`1.0E7`** | Copy Favorites "Take:" |
| Dose | `form/pharmaForms/formBPMH/business/BpmhForm2Handler.java:503-504`: the float dose was never copied into the form's text field | **blank**: "Take TAB BID" | BPMH "How I take it" on screen and in the PDF, when the instruction text has no direction line |
| Dose | `prevention/review.jsp:1047` printed `BigDecimal.toString()` | **`1.0`**, `0.5`, `0.0625`, **`500.0`**, **`1.0E+7`** | DHIR submission review "Dose Qty" |
| Strength | `prescript/data/RxDrugData.java:242` copied DrugRef's float text | **`1.0`**, `0.5`, `0.0625`, **`500.0`**, **`1.0E7`** | Drug Information `rx/DrugInfo.jsp:82`; staged generic name and dosage (`prescript/pageUtil/RxWriteScript2Action.java`, `RxChooseDrug2Action.java:167-168`), which are **stored** in `drugs.GN`/`drugs.dosage` and then shown on the staging card, the Rx record, the BPMH form and the prescription HL7 (`commn/hl7/v2/OmpO09Builder.java:155`) |
| Strength | `webserv/rest/conversion/DrugConverterImpl.java:99`, `:368`, `:378` concatenated the `Float` | **`1.0 mg`**, `0.5 mg`, `0.0625 mg`, **`500.0 mg`**, **`1.0E7 mg`**; "nullmg" or "nullnull" for a part not sent | **Stored** in `drugs.dosage` by REST `/rx/new`, `/rx/update`, `/rx/prescribe` |
| Strength | `demographic/cdsexport/service/CdsExportDrugReferenceLookupService.java:126` rewrote a numeric strength only | text **`250.0`** passed through | CDS 5.2 export `Strength`. DrugRef sends text, so this was the live path |
| Quantity | `prescript/util/RxUtil.java:481`, `:501` split a quantity typed with a unit at its first run of digits and deleted those digits | "2.5 mL" gave **`Qty:2 .5 mL`**; "0.5 mL" gave `Qty:0 .5 mL`; ".5 mL" gave `Qty:5 . mL`; "1 bottle (120 mL)" gave `Qty:1 bottle (20 mL)` | **Stored** in `drugs.quantity`, `drugs.unitName` and the instruction text; printed and faxed prescription |
| Quantity | `prescript/util/RxUtil.java:601` `findDuration`, same split | a typed "2.5" gave the unit `.5` and no duration | Staging card; `Qty:2.5 .5` in the instruction text |
| Quantity | `prescript/util/RxUtil.java:3009` carried over the first run of digits of the previous quantity | previous "2.5" gave the default **`2`** | Default quantity of a new prescription for the same drug |
| Quantity | `webserv/rest/conversion/DrugConverterImpl.java:229`, `FavoriteConverterImpl.java:160` parsed the stored text with `Integer.parseInt` | "2.5" or "100 mL" failed the whole read | REST drug list and favourites. A quantity the `Integer` field cannot carry is now left out; the instruction text still states it |

Fixed earlier under the same issue: the legacy CDS 4 export `Dosage`
(`demographic/pageUtil/DemographicExportAction42Action.java:4535`, #6326) and the CDS 5.2 export
dose and numeric strength (#5706). Both now call `DoseText` instead of a private copy.

## Checked, no defect

| Kind | Where | Why it is correct |
| --- | --- | --- |
| Dose | `prescript/pageUtil/RxWriteScript2Action.java:1146-1147`, `:1225-1226` put the float in JSON; `rx/SearchDrug3.jsp:2769-2770`, `:2838-2839` print it | JavaScript prints the number as `1`, `0.5`, `0.0625`, `500`, `10000000` |
| Dose | eForm tags `druglist_generic`, `druglist_line` (`resources/oscar/eform/apconfig.xml:1480-1492`); document transfer "Take" (`resources/DocXferConfig.xml:27`) | MariaDB writes the FLOAT column in `CONCAT` as `1`, `0.5`, `0.0625`, `500`, `10000000`; confirmed against the packaged MariaDB |
| Dose, quantity | The instruction text (`drugs.special`, `getFullOutLine()`): prescription preview, print, PDF and fax (`rx/Preview2.jsp:709-724`, `form/pdfservlet/FrmCustomedPDFServlet.java`), drug profile and lists, encounter Rx box, CPP, consult request, chart print (`casemgmt/service/CaseManagementPrintPdf.java`, `casemgmt/print/OscarChartPrinter.java`, `commn/service/PdfRecordPrinter.java`), REST summary, eForm tags `current_rx`/`today_rx` | Text the prescriber typed, shown as written. CARLOS adds only the `Qty:`/`Repeats:` suffix |
| Quantity | Calculated quantity, `prescript/util/RxUtil.java` (`Integer.toString(ceilingUnits())`); derived duration (`setScale(0, FLOOR).toPlainString()`) | Whole numbers |
| Dose, strength | REST and SOAP number fields (`DrugTo1`, `FavoriteTo1`, `DrugSearchTo1`, `DrugTransfer`) | Serialized as numbers; the server builds no text |
| Dose | Prevention dose (`prevention/pageUtil/AddPrevention2Action.java`, `PreventionData.java`, `PreventionPrintPdf.java`, eForm prevention tags) | Stored and shown as typed |
| Dose, strength | CDS import (`demographic/cdsimport/service/CdsMedicationPersister.java`) | Source text kept verbatim; the float is set only when it is exact |
| Rate | `rx/RenalDosing.jsp:255-269` | Already `stripTrailingZeros().toPlainString()` |

## Not fixed here

These were found during the review and are not number-to-text formatting. Each needs its own
change.

| Finding | Where |
| --- | --- |
| Legacy single-drug editor: rebuilding the instruction text keeps the dot of a dose typed without a leading zero, so "Take .5 Tabs" changed to 2 becomes "Take .2 Tabs"; the auto-quantity rounds a binary float up (0.2 x 3 x 10 gives 7) | `rx/WriteScript.jsp:351-411`, `:269-313` |
| A do-not-use form the prescriber types is shown as typed: the instruction line, a quantity typed without a unit ("30.0", ".5"), the inline Min/Max until the card is redrawn, the prevention dose | `rx/prescribe.jsp`, `rx/SearchDrug3.jsp:1458-1486`, `prevention/AddPreventionData.jsp:1050` |
| DHIR immunization dose: a decimal comma or grouped digits are misread ("0,5 mL" becomes 0 with the unit ",5 mL"), a dose with no number is sent as 0, and the FHIR decimal is built from a double | `integration/fhir/model/Immunization.java:393-403` |
| REST strength read from stored text needs two digits, so "1 mg" has no strength and "5 mg / 12.5 mg" reports 12.5 | `webserv/rest/conversion/DrugConverterImpl.java:236-250` |
| REST drug lookup casts DrugRef's strength to `Double`; DrugRef sends text | `managers/DrugLookUpManager.java:208` |
| DrugRef's XML-RPC serializer has no float branch, so a strength is sent as the text "250.0" | `carlos-emr/drugref2026`, `util/XmlRpcUtils.java` |
| A decimal quantity typed without a unit meets whole-number code: the parser's `Integer.parseInt`, and the REST `Integer` quantity | `prescript/util/RxUtil.java:1287`; `DrugTo1.quantity`, `FavoriteTo1.quantity` |
| Quantity text with grouped digits, a fraction or a range is still split at its first number ("1,000 mL" gives `Qty:1 ,000 mL`) | `prescript/util/RxUtil.java:481-520` |
| Unused code that still has the old habit | `demographic/pageUtil/DemographicExportHelper.java:209-211`, `util/UtilMisc.java:224`, `integration/fhir/model/Immunization.java:407` |
| Out of scope, measurement text: range messages print bounds with `Double.toString` ("0.0") | `encounter/oscarMeasurements/pageUtil/EctMeasurements2Action.java:221-222`, `encounter/oscarMeasurements/healthtracker/HealthTrackerMeasurementPersister.java:198` |

Stored values are out of scope for #6185. Prescriptions saved before this change keep the text
they were saved with ("250.0mg", `Qty:2 .5 mL`), and a dose that was already rounded by a save
stays rounded.

## Behaviour changes to expect

- A dose finer than a thousandth is shown in full: 0.0625 where "0.062" was shown.
- A dose with no finite decimal form is shown to the float's precision: a third of a tablet is
  "0.33333334" where "0.333" was shown. The instruction text ("Take 1/3 tab") is unchanged.
- New prescriptions stage and store "250mg" and "amoxicillin 250mg capsule" where "250.0mg" was
  stored. The instruction parser divides by either shape
  (`RxUtilInstrucParserUnitTest.shouldDivideByProductStrength_whenStrengthHasNoTrailingZero`).
- A quantity typed with a unit keeps its decimal amount, written with a leading zero and no
  trailing zero: ".5 mL" is saved as `Qty:0.5 mL` and "30.0 mL" as `Qty:30 mL`.

## Tests

| Test | Covers |
| --- | --- |
| `DoseTextUnitTest` | The formatter |
| `RxUtilFloatToStringUnitTest`, `RxPrescriptionDataDoseDisplayUnitTest` | Dose text on prescriptions and Rx records, and that it reads back to the stored dose |
| `RxUtilQuantityTextUnitTest`, `RxUtilInstrucParserUnitTest` (`findDuration`), `RxPrescriptionDataNullRepeatUnitTest` | Quantity typed with a unit, derived duration, carried-over quantity |
| `RxDrugDataComponentStrengthUnitTest`, `CdsExportDrugReferenceLookupServiceUnitTest` | DrugRef strength as text and as a number |
| `DrugConverterUnitTest`, `FavoriteConverterQuantityUnitTest` | REST strength text and stored quantity |
| `BpmhForm2HandlerUnitTest` | BPMH structured dose |
| `RxDoseTextJspRegressionUnitTest` | No page prints `takeMin`/`takeMax` as a raw float |
| `scripts/rx-dose-text-playwright-checks.js` | The Rx page, the saved prescription and the favourites editor in a browser |
