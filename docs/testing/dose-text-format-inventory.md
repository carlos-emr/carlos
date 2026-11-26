# Dose, strength and quantity text inventory

Issue [#6185](https://github.com/carlos-emr/carlos/issues/6185). Target: `release/2026.08`.

ISMP Canada's *Do Not Use* list bans two numeric forms in medication text: a trailing zero
("1.0 mg" is read as 10 mg) and a missing leading zero (".5 mg" is read as 5 mg). This inventory
lists every place where CARLOS turns a dose, strength or quantity **number** into text that a
person reads or another system receives, and what each one printed. A number that is changed on
the way to text (rounded, split, truncated) is listed too, because the reader gets a different
number.

Each row was read in the source. Paths are under `src/main/java/io/github/carlos_emr/carlos/`
or `src/main/webapp/WEB-INF/jsp/`. The "Was" column gives the text for the stored values
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
| `withPlainNumbers(String)` | For comparison only: text with each decimal number in it written plainly, so "250.0mg" and "250mg", or "1.0E7unit" and "10000000unit", compare equal. |

Do not format such a number at the call site. `DoseTextUnitTest` covers 1.0, 0.5, 0.0625, 500.0,
1.0E7, the ranges, negative zero, non-finite values, a non-English default locale and oversized
exponents.

## Defects fixed

| Kind | Where the text is produced | Was | Reaches |
| --- | --- | --- | --- |
| Dose | `prescript/util/RxUtil.java:325` `FloatToString`, three-decimal HALF_EVEN rounding | `1`, `0.5`, **`0.062`**, `500`, `10000000`; 0.0004 printed `0` | Every row marked "via `FloatToString`" below |
| Dose | `commn/model/Drug.java:361` `getDosageDisplay` (via `FloatToString`) | as above | Rx record, `rx/DisplayRxRecord.jsp:287` |
| Dose | `prescript/data/RxPrescriptionData.java:1624` `getDosageDisplay`, `:1670` `getRxDisplay`, `:1748` `getFullFrequency` (via `FloatToString`) | as above | BC health passport forms `form/formbchppg1.jsp:97-98`, `formbchppg2.jsp:97-98` (saved with the form); VT form `form/pageUtil/FrmSetupForm2Action.java:313`; Rx page drug list `rx/SearchDrug3.jsp:1038` |
| Dose | `prescript/data/RxPrescriptionData.java:1333`, `:1345` `getTakeMinString`/`getTakeMaxString` (via `FloatToString`) | as above | Staging card Min/Max, `rx/prescribe.jsp:115-116`, `:403-404`; legacy editor hidden fields `rx/WriteScript.jsp:944-945`, `:1220-1221`. **Both post the text back as the dose**, so 0.0625 was stored as 0.062 on the next save |
| Dose | `rx/EditFavorites2.jsp:329`, `:332` printed the float | **`1.0`**, `0.5`, `0.0625`, **`500.0`**, **`1.0E7`** | Edit Favorites "Take" inputs |
| Dose | `rx/CopyFavorites2.jsp:191` printed the double with EL | **`1.0`**, `0.5`, `0.0625`, **`500.0`**, **`1.0E7`** | Copy Favorites "Take:" |
| Dose | `form/pharmaForms/formBPMH/business/BpmhForm2Handler.java:504-505`: the float dose was never copied into the form's text field | **blank**: "Take TAB BID" | BPMH "How I take it" on screen and in the PDF, when the instruction text has no direction line. A range is now written "1 to 3" (`form/pharmaForms/formBPMH/bean/BpmhDrug.java:135`); 0 and the legacy editor's -1 are "no dose" |
| Dose | `prevention/review.jsp:1047` printed `BigDecimal.toString()` | **`1.0`**, `0.5`, `0.0625`, **`500.0`**, **`1.0E+7`** | DHIR submission review "Dose Qty" |
| Strength | `prescript/data/RxDrugData.java:246` copied DrugRef's float text | **`1.0`**, `0.5`, `0.0625`, **`500.0`**, **`1.0E7`** | Drug Information `rx/DrugInfo.jsp:82`; staged generic name and dosage (`prescript/pageUtil/RxWriteScript2Action.java:996`, `RxChooseDrug2Action.java:167-168`), which are **stored** in `drugs.GN`/`drugs.dosage` and then shown on the staging card, the Rx record, the BPMH form and the prescription HL7 (`commn/hl7/v2/OmpO09Builder.java:155`) |
| Strength | `webserv/rest/conversion/DrugConverterImpl.java:117`, `:425`, `:435` concatenated the `Float` | **`1.0 mg`**, `0.5 mg`, `0.0625 mg`, **`500.0 mg`**, **`1.0E7 mg`**; "nullmg" or "nullnull" for a part not sent | **Stored** in `drugs.dosage` by REST `/rx/new`, `/rx/update`, `/rx/prescribe` |
| Strength | `demographic/cdsexport/service/CdsExportDrugReferenceLookupService.java:131` rewrote a numeric strength only | text **`250.0`** passed through | CDS 5.2 export `Strength`. DrugRef sends text, so this was the live path |
| Quantity | `prescript/util/RxUtil.java:481`, `:501` split a quantity typed with a unit at its first run of digits and deleted those digits | "2.5 mL" gave **`Qty:2 .5 mL`**; "0.5 mL" gave `Qty:0 .5 mL`; ".5 mL" gave `Qty:5 . mL`; "1 bottle (120 mL)" gave `Qty:1 bottle (20 mL)` | **Stored** in `drugs.quantity`, `drugs.unitName` and the instruction text; printed and faxed prescription |
| Quantity | `prescript/pageUtil/RxWriteScript2Action.java:1210`, `:1686` stored a quantity typed without a unit as typed | "30.0" gave **`Qty:30.0`**; ".5" gave **`Qty:.5`** | As above |
| Quantity | `prescript/util/RxUtil.java:601` `findDuration`, same split | a typed "2.5" gave the unit `.5` and no duration | Staging card; `Qty:2.5 .5` in the instruction text |
| Quantity | `prescript/util/RxUtil.java:3009` carried over the first run of digits of the previous quantity | previous "2.5" gave the default **`2`** | Default quantity of a new prescription for the same drug |
| Quantity | `webserv/rest/conversion/DrugConverterImpl.java:251`, `FavoriteConverterImpl.java:161` parsed the stored text with `Integer.parseInt` | "2.5" or "100 mL" failed the whole read | REST drug list and favourites. A quantity the `Integer` field cannot carry is now left out (`commn/model/Drug.java:350`); the instruction text still states it, and an update that states no quantity keeps the stored one (`managers/RxManagerImpl.java:298`) |

Fixed earlier under the same issue: the legacy CDS 4 export `Dosage`
(`demographic/pageUtil/DemographicExportAction42Action.java:4539`, #6326) and the CDS 5.2 export
dose and numeric strength (#5706). The CDS 5.2 formatter now uses `DoseText`. The legacy CDS 4
export retains the formatter tested in #6326.

### What storing the strength without its zero touches

New prescriptions store "250mg" where "250.0mg" was stored; existing rows are not rewritten.
Code that compares the stored generic name or dosage text therefore meets both forms:

| Where | Effect | Handling |
| --- | --- | --- |
| `prescript/data/UniqueMedicationList.java:137` keys the E-Chart, consult, chart print and REST medication list on the generic name and dosage text | A renewal written after the upgrade would have been listed beside the prescription it renews | Fixed: the two texts are compared through `DoseText.withPlainNumbers` |
| `prescript/pageUtil/RxWriteScript2Action.java:996` left a strength out of the generic name when the text built so far contained it anywhere | "5mg" is inside "12.5mg", so the second ingredient of a combination lost its strength | Fixed: only the ingredient's own name is checked, at a number boundary (`:411`). This also restores a second ingredient whose strength equals the first's, which was always dropped |
| `webserv/rest/conversion/DrugConverterImpl.java:284` read the REST `strength` back from stored text with a pattern that needed two digits and a unit, and took everything up to the next number as the unit | "5 mg" had no strength and "5 mg / 20 mg" reported 20 with the unit "mg / "; unnoticed while every strength was stored as "5.0 mg" | Fixed: a strength is read only when the text starts with it, and whole (`DoseText.numberEnd`), with or without a fraction, a unit or the exponent this service once stored ("1.0E7 unit" reads ten million), with the unit that follows it after any blanks. "5 mg / 20 mg" reads 5 and "mg"; "2.5 mg/mL" reads 2.5 and "mg/mL"; "2.5" reads 2.5 and no unit. When another number follows the leading one before any unit, whatever stands between them ("1,000 mg", "1 000 mg", "1/2 mg", "1 / 2 mg", "5/20 mg", "5 - 10 mg", "1:1000", "10^9 CFU", or one and a half written with a fraction character), the leading digits are not the amount: nothing is reported, and no later number is reported in its place. Text that starts with a unit and no number ("mg/5mL", stored for a unit sent without a strength) is reported as that unit, whole, and never as the number inside it |
| `prescript/util/RxUtil.java:3209` `checkLastPrescribed` (via `commn/dao/DrugDaoImpl.java:657`) matches a later active prescription by brand and generic name text | The "discontinued before" notice can show although a later active prescription, written before the upgrade, exists | Not changed. The lookup also requires `archived_reason = ''`, which prescriptions saved since #5698 store as NULL, so it already misses them. See "Not fixed here" |
| `commn/dao/FavoriteDaoImpl.java:112` matches an existing favourite on every field, generic name and dosage included | A favourite saved again from a new prescription is not recognised as the old one | Not changed; a second favourite is saved |

## Checked, no defect

| Kind | Where | Why it is correct |
| --- | --- | --- |
| Dose | `prescript/pageUtil/RxWriteScript2Action.java` puts the float in the card's JSON (`hm.put("takeMin", …)`); `rx/SearchDrug3.jsp:2769-2770`, `:2838-2839` print it | JavaScript prints the number as `1`, `0.5`, `0.0625`, `500`, `10000000` |
| Dose | eForm tags `druglist_generic`, `druglist_line` (`resources/oscar/eform/apconfig.xml:1480-1492`); document transfer "Take" (`resources/DocXferConfig.xml:27`) | MariaDB writes the FLOAT column in `CONCAT` as `1`, `0.5`, `0.0625`, `500`, `10000000`, to six significant digits (a third is `0.333333`); checked against the packaged MariaDB 11.8 |
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
| A do-not-use form the prescriber types into free text is shown as typed: the instruction line, the inline Min/Max until the card is redrawn, the prevention dose | `rx/prescribe.jsp`, `rx/SearchDrug3.jsp:1458-1486`, `prevention/AddPreventionData.jsp:1050` |
| DHIR immunization dose: a decimal comma or grouped digits are misread ("0,5 mL" becomes 0 with the unit ",5 mL"), a dose with no number is sent as 0, and the FHIR decimal is built from a double | `integration/fhir/model/Immunization.java:393-403` |
| REST: a drug has one `strength`, so a combination is reported as its first ingredient. An edit to the same drug that echoes or omits that strength retains the stored dosage. The drug lookup casts DrugRef's strength to `Double` although DrugRef sends text. The quantity is an `Integer`, so a decimal quantity is omitted | `prescript/util/DrugStrength.java`; `managers/RxManagerImpl.java`; `managers/DrugLookUpManager.java:208`; `DrugTo1.quantity`, `FavoriteTo1.quantity` |
| DrugRef's XML-RPC serializer has no float branch, so a strength is sent as the text "250.0" | `carlos-emr/drugref2026`, `util/XmlRpcUtils.java` |
| The "discontinued before" check matches by brand and generic name text and by `archived_reason = ''`; favourites are matched on every text field | `prescript/util/RxUtil.java:3209`, `commn/dao/DrugDaoImpl.java:657`, `commn/dao/FavoriteDaoImpl.java:112` |
| The instruction parser reads a quantity with `Integer.parseInt` when it derives a duration from it | `prescript/util/RxUtil.java:1287` |
| Quantity text with grouped digits, a fraction or a range is still split at its first number ("1,000 mL" gives `Qty:1 ,000 mL`) | `prescript/util/RxUtil.java:481-520` |
| Unused code that still has the old habit | `demographic/pageUtil/DemographicExportHelper.java:209-211`, `util/UtilMisc.java:224`, `integration/fhir/model/Immunization.java:407`, and `prescript/data/RxDrugData.java:828` `getStrengths` and `:862` `getStrengthsLists` (deprecated, no callers; they join DrugRef's strength text to its unit as sent) |
| Out of scope, measurement text: range messages print bounds with `Double.toString` ("0.0") | `encounter/oscarMeasurements/pageUtil/EctMeasurements2Action.java:221-222`, `encounter/oscarMeasurements/healthtracker/HealthTrackerMeasurementPersister.java:198` |

### Stored values

Stored values are out of scope for #6185, and are not rewritten:

- A prescription saved before this change keeps "250.0mg" in its generic name and dosage.
- A dose that a save had already rounded stays rounded.
- A quantity that was split stays split, and cannot be repaired by rule. The old split deleted
  every copy of the first run of digits, so "2.5 mL" and "2.25 mL" were both stored as the
  quantity "2" with the unit ".5 mL". Re-prescribing such a row copies it as it is. Rows to
  review have a `unitName` that starts with a full stop:
  `SELECT drugid, demographic_no, quantity, unitName FROM drugs WHERE unitName LIKE '.%';`

## Behaviour changes to expect

- A dose finer than a thousandth is shown in full: 0.0625 where "0.062" was shown.
- A dose with no finite decimal form is shown to the float's precision: a third of a tablet is
  "0.33333334" where "0.333" was shown. The instruction text ("Take 1/3 tab") is unchanged.
- New prescriptions stage and store "250mg" and "amoxicillin 250mg capsule" where "250.0mg" was
  stored. The instruction parser divides by either shape
  (`RxUtilInstrucParserUnitTest.shouldDivideByProductStrength_whenStrengthHasNoTrailingZero`).
- A typed quantity is stored as a plain decimal, with or without a unit: ".5 mL" is saved as
  `Qty:0.5 mL`, "30.0 mL" as `Qty:30 mL` and "30.0" as `Qty:30`.
- The generic name of a combination product names every ingredient's strength.
- The BPMH form shows the structured dose, and a range as "1 to 3".
- REST: a stored quantity that is not a whole number is omitted from `quantity` instead of
  failing the read, and is kept by an update that states no quantity. `strength` is reported for
  a one-digit strength and for a strength stored without a unit, and `strengthUnit` for a
  combination is the first ingredient's unit ("mg", where "mg / " was reported).
- Rolling back to an earlier build keeps what this build saved. Its REST drug list fails on a
  quantity that is not a whole number, as it already did for one typed without a unit; this
  build saves "2.5" for a quantity typed as "2.5 mL", where the earlier build saved "2".

## Tests

| Test | Covers |
| --- | --- |
| `DoseTextUnitTest` | The formatter |
| `RxUtilFloatToStringUnitTest`, `RxPrescriptionDataDoseDisplayUnitTest` | Dose text on prescriptions and Rx records, and that it reads back to the stored dose |
| `RxUtilQuantityTextUnitTest`, `RxUtilInstrucParserUnitTest` (`findDuration`), `RxPrescriptionDataNullRepeatUnitTest`, `RxWriteScript2ActionCardCorrectionUnitTest` | Typed quantity with and without a unit, derived duration, carried-over quantity |
| `RxDrugDataComponentStrengthUnitTest`, `RxWriteScript2ActionCreateNewRxUnitTest`, `CdsExportDrugReferenceLookupServiceUnitTest` | DrugRef strength as text and as a number; staged generic name and dosage |
| `RxUniquePrescriptionsUnitTest` | A renewal is listed once across the old and new strength text |
| `DrugConverterUnitTest`, `FavoriteConverterQuantityUnitTest`, `RxManagerUnitTest` | REST strength text, strength read-back, stored quantity, update |
| `BpmhForm2HandlerUnitTest` | BPMH structured dose |
| `RxDoseTextJspRegressionUnitTest` | No page prints `takeMin`/`takeMax` as a raw float |
| `scripts/rx-dose-text-playwright-checks.js` | The Rx page, the saved prescription, the Rx record and the favourites editor in a browser |
