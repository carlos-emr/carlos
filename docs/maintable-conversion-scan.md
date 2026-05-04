# MainTable Legacy Layout — Conversion Scan Report

> Generated: 2026-04-13
> Related issue: Plan for CARLOS template roll out
> Reference: `docs/oscar-to-carlos-bootstrap-conversion.md`

---

## Summary

| Metric | Count |
|--------|-------|
| JSP files with `class="MainTable"` or `id="MainTable"` | 108 |
| JSP files with `MainTableTopRow` or `MainTableBottomRow` | 124 |
| JSP files with **both** patterns (conversion candidates) | 106 |
| Files with row classes but no MainTable class/id | 18 |

---

## Conversion Candidates (sorted by LOC ascending)

Files that contain **both** a `<table class="MainTable">` / `id="MainTable"` **and**
`MainTableTopRow` / `MainTableBottomRow` row classes.

| # | File | LOC | Size | Tables | Status |
|---|------|-----|------|--------|--------|
| 1 | `provider/providerColourErr.jsp` | 64 | 4 KB | 1 | ✅ Converted |
| 2 | `provider/providerFaxErr.jsp` | 64 | 4 KB | 1 | ✅ Converted |
| 3 | `encounter/TimeOut.jsp` | 80 | 4 KB | 2 | ✅ Converted |
| 4 | `provider/setEDocBrowserInMasterFile.jsp` | 88 | 8 KB | 1 | |
| 5 | `provider/setEDocBrowserInDocumentReport.jsp` | 89 | 8 KB | 1 | |
| 6 | `encounter/oscarConsultationRequest/nothingtoPrint.jsp` | 92 | 4 KB | 2 | ✅ Converted |
| 7 | `provider/setDisplayDocumentAs.jsp` | 93 | 8 KB | 1 | |
| 8 | `provider/setDashboardPrefs.jsp` | 94 | 8 KB | 1 | |
| 9 | `provider/setGenRxDefaultQuantityProperty.jsp` | 94 | 8 KB | 1 | |
| 10 | `encounter/License.jsp` | 95 | 4 KB | 2 | ✅ Converted |
| 11 | `provider/setQuickChartSize.jsp` | 96 | 8 KB | 1 | |
| 12 | `WEB-INF/jsp/provider/providerSignature.jsp` | 97 | 8 KB | 2 | |
| 13 | `provider/setPreventionPrefs.jsp` | 98 | 8 KB | 1 | |
| 14 | `provider/setAppointmentCardPrefs.jsp` | 102 | 8 KB | 1 | |
| 15 | `provider/setEncounterWindowSize.jsp` | 102 | 8 KB | 1 | |
| 16 | `provider/setPatientNameLength.jsp` | 102 | 8 KB | 1 | |
| 17 | `WEB-INF/jsp/oscarReport/RptByExamplesFavorite.jsp` | 105 | 8 KB | 3 | |
| 18 | `billing/CA/BC/manageSVCDXAssoc.jsp` | 109 | 8 KB | 2 | |
| 19 | `admin/sitesAdmin.jsp` | 114 | 8 KB | 2 | |
| 20 | `provider/providerFax.jsp` | 121 | 8 KB | 1 | |
| 21 | `provider/providerPhone.jsp` | 123 | 8 KB | 1 | |
| 22 | `lab/CA/BC/LabUpload.jsp` | 125 | 8 KB | 2 | |
| 23 | `provider/providerColourPicker.jsp` | 130 | 8 KB | 1 | |
| 24 | `encounter/oscarMeasurements/DisplayMeasurementStyleSheet.jsp` | 131 | 8 KB | 5 | |
| 25 | `provider/setShowPatientDOB.jsp` | 131 | 8 KB | 1 | |
| 26 | `provider/setCppSingleLine.jsp` | 133 | 8 KB | 1 | |
| 27 | `WEB-INF/jsp/encounter/oscarMeasurements/AddMeasurementStyleSheet.jsp` | 135 | 8 KB | 5 | |
| 28 | `provider/setGenRxPageSizeProperty.jsp` | 135 | 8 KB | 1 | |
| 29 | `provider/setNoteStaleDate.jsp` | 135 | 8 KB | 1 | |
| 30 | `provider/setGenRxProfileViewProperty.jsp` | 136 | 8 KB | 1 | |
| 31 | `provider/setLabAckComment.jsp` | 136 | 8 KB | 1 | |
| 32 | `encounter/oscarMeasurements/EditMeasurementGroupStyle.jsp` | 137 | 8 KB | 5 | |
| 33 | `provider/editSignature.jsp` | 137 | 8 KB | 2 | |
| 34 | `encounter/oscarMeasurements/DefineNewMeasurementGroup.jsp` | 139 | 8 KB | 5 | |
| 35 | `waitinglist/DisplayPatientWaitingList.jsp` | 142 | 8 KB | 5 | |
| 36 | `provider/setLabRecallPrefs.jsp` | 145 | 8 KB | 2 | |
| 37 | `messenger/SentMessage.jsp` | 148 | 8 KB | 2 | |
| 38 | `encounter/oscarMeasurements/AddMeasurementGroup.jsp` | 150 | 8 KB | 4 | |
| 39 | `encounter/oscarMeasurements/SelectMeasurementGroup.jsp` | 150 | 8 KB | 5 | |
| 40 | `oscarReport/oscarMeasurements/SelectCDMReport.jsp` | 152 | 8 KB | 4 | |
| 41 | `encounter/oscarMeasurements/DisplayMeasurementTypes.jsp` | 155 | 8 KB | 4 | |
| 42 | `encounter/oscarMeasurements/EditMeasurementGroup.jsp` | 156 | 8 KB | 4 | |
| 43 | `oscarReport/oscarMeasurements/CDMReport.jsp` | 157 | 8 KB | 5 | |
| 44 | `billing/CA/BC/billingAddCode.jsp` | 158 | 8 KB | 4 | |
| 45 | `WEB-INF/jsp/encounter/oscarMeasurements/AddMeasuringInstruction.jsp` | 159 | 8 KB | 5 | |
| 46 | `encounter/immunization/ScheduleConfig.jsp` | 171 | 8 KB | 3 | |
| 47 | `admin/sitesAdminDetail.jsp` | 172 | 8 KB | 3 | |
| 48 | `provider/setGenProperty.jsp` | 173 | 8 KB | 1 | |
| 49 | `billing/CA/BC/billingCodeAdjust.jsp` | 175 | 8 KB | 5 | |
| 50 | `WEB-INF/jsp/encounter/immunization/config/AdministrateImmunizationSets.jsp` | 177 | 12 KB | 4 | |
| 51 | `WEB-INF/jsp/encounter/oscarMeasurements/AddMeasurementType.jsp` | 185 | 12 KB | 5 | |
| 52 | `WEB-INF/jsp/messenger/ViewPDFAttachment.jsp` | 185 | 8 KB | 6 | |
| 53 | `provider/setTicklerPreferences.jsp` | 186 | 8 KB | 1 | |
| 54 | `hospitalReportManager/displayHRMDocList.jsp` | 187 | 12 KB | 3 | |
| 55 | `provider/setDocDefaultQueue.jsp` | 191 | 12 KB | 1 | |
| 56 | `WEB-INF/jsp/admin/uploadEntryText.jsp` | 215 | 12 KB | 2 | |
| 57 | `billing/CA/BC/billingEditCode.jsp` | 216 | 12 KB | 3 | |
| 58 | `eform/efmpatientformlistsingle.jsp` | 217 | 12 KB | 3 | |
| 59 | `provider/providerAddress.jsp` | 221 | 8 KB | 1 | |
| 60 | `demographic/displayHealthCareTeam.jsp` | 237 | 12 KB | 3 | |
| 61 | `provider/clients.jsp` | 238 | 12 KB | 4 | |
| 62 | `episode/episodeForm.jsp` | 241 | 12 KB | 3 | |
| 63 | `admin/keygen/createKey.jsp` | 247 | 12 KB | 3 | |
| 64 | `admin/billingreferralAdmin.jsp` | 251 | 12 KB | 3 | |
| 65 | `oscarReport/oscarMeasurements/InitializePatientsInAbnormalRangeCDMReport.jsp` | 255 | 16 KB | 7 | |
| 66 | `oscarReport/oscarMeasurements/InitializeFrequencyOfRelevantTestsCDMReport.jsp` | 261 | 16 KB | 7 | |
| 67 | `lab/CumulativeLabValues.jsp` | 269 | 12 KB | 2 | |
| 68 | `oscarWorkflow/WorkFlowList.jsp` | 273 | 12 KB | 3 | |
| 69 | `oscarReport/oscarMeasurements/InitializePatientsMetGuidelineCDMReport.jsp` | 276 | 16 KB | 8 | |
| 70 | `demographic/AddAlternateContact.jsp` | 285 | 16 KB | 3 | |
| 71 | `provider/providerPrinter.jsp` | 286 | 16 KB | 2 | |
| 72 | `demographic/EnrollmentHistory.jsp` | 298 | 16 KB | 5 | |
| 73 | `demographic/procontactSearch.jsp` | 319 | 16 KB | 4 | |
| 74 | `report/GenerateLetters.jsp` | 321 | 16 KB | 3 | |
| 75 | `oscarPrevention/AddPreventionDataDisambiguate.jsp` | 326 | 12 KB | 2 | |
| 76 | `encounter/ViewAttachment.jsp` | 337 | 16 KB | 6 | |
| 77 | `waitinglist/DisplayWaitingList.jsp` | 338 | 20 KB | 5 | |
| 78 | `lab/CumulativeLabValues2.jsp` | 342 | 16 KB | 3 | |
| 79 | `WEB-INF/jsp/messenger/DisplayDemographicMessages.jsp` | 349 | 20 KB | 7 | |
| 80 | `provider/cpp_preferences.jsp` | 349 | 20 KB | 2 | |
| 81 | `encounter/calculators/SimpleCalculator.jsp` | 352 | 16 KB | 5 | |
| 82 | `messenger/ViewAttachment.jsp` | 390 | 16 KB | 5 | |
| 83 | `demographic/ManageContacts.jsp` | 395 | 20 KB | 2 | |
| 84 | `lab/CumulativeLabValues3.jsp` | 395 | 20 KB | 3 | |
| 85 | `encounter/immunization/ScheduleEdit.jsp` | 405 | 20 KB | 5 | |
| 86 | `messenger/Transfer/SelectItems.jsp` | 408 | 16 KB | 6 | |
| 87 | `encounter/calculators/GeneralCalculators.jsp` | 412 | 20 KB | 8 | |
| 88 | `encounter/oscarMeasurements/Measurements.jsp` | 418 | 24 KB | 7 | |
| 89 | `demographic/demographicappthistory.jsp` | 444 | 24 KB | 3 | |
| 90 | `lab/DemographicLab.jsp` | 446 | 28 KB | 3 | |
| 91 | `billing/CA/BC/teleplan/ManageBillingCodes.jsp` | 453 | 16 KB | 3 | |
| 92 | `encounter/immunization/Schedule.jsp` | 484 | 24 KB | 6 | |
| 93 | `demographic/manageHealthCareTeam.jsp` | 515 | 24 KB | 5 | |
| 94 | `oscarPrevention/dhirSubmission.jsp` | 522 | 20 KB | 2 | |
| 95 | `messenger/generatePreviewPDF.jsp` | 525 | 24 KB | 6 | |
| 96 | `encounter/calculators/OsteoporoticFracture.jsp` | 555 | 28 KB | 6 | |
| 97 | `messenger/DisplayMessages.jsp` | 595 | 32 KB | 3 | |
| 98 | `encounter/oscarMeasurements/AddMeasurementData.jsp` | 699 | 28 KB | 2 | |
| 99 | `messenger/ViewMessage.jsp` | 719 | 32 KB | 10 | |
| 100 | `WEB-INF/jsp/report/ClinicalReports.jsp` | 930 | 44 KB | 3 | |
| 101 | `oscarPrevention/review.jsp` | 1064 | 44 KB | 10 | |
| 102 | `encounter/calculators/CoronaryArteryDiseaseRiskPrediction.jsp` | 1111 | 52 KB | 7 | |
| 103 | `WEB-INF/jsp/demographic/edit.jsp` | 1478 | 88 KB | 8 | |
| 104 | `oscarPrevention/AddPreventionData.jsp` | 1565 | 100 KB | 2 | |
| 105 | `oscarPrevention/index.jsp` | 1631 | 88 KB | 5 | |
| 106 | `demographic/demographiceditdemographic.jsp` | 5139 | 436 KB | 14 | |

All paths are relative to `src/main/webapp/`.

---

## Additional Files (MainTableTopRow/BottomRow only, no MainTable class/id)

These 18 files use the row-class pattern but not the `MainTable` class. They may use a
different outer table structure and should be reviewed separately.

| File | Notes |
|------|-------|
| `WEB-INF/jsp/oscarReport/demographicSetEdit.jsp` | |
| `billing/CA/BC/billingPreferences.jsp` | |
| `encounter/oscarMeasurements/addMeasurementMap.jsp` | |
| `encounter/oscarMeasurements/addMeasurementMap2.jsp` | |
| `encounter/oscarMeasurements/newMeasurementMap.jsp` | |
| `encounter/oscarMeasurements/remapMeasurementMap.jsp` | |
| `encounter/oscarMeasurements/removeMeasurementMap.jsp` | |
| `encounter/oscarMeasurements/viewMeasurementMap.jsp` | |
| `lab/CA/ALL/labDisplay.jsp` | |
| `lab/CA/ALL/labDisplayAjax.jsp` | |
| `lab/CA/BC/labDisplay.jsp` | |
| `lab/CA/ON/CMLDisplay.jsp` | |
| `lab/CA/ON/labValues.jsp` | |
| `lab/CA/ON/labValuesGraph.jsp` | |
| `oscarMDS/Index.jsp` | |
| `oscarMDS/Page.jsp` | |
| `oscarMDS/Search.jsp` | |
| `oscarMDS/SegmentDisplay.jsp` | |

---

## Conversion Strategy

**Batch 1 (this PR):** Files ≤ 95 LOC with 1–2 tables — pure layout, minimal logic.
These are the safest candidates for automated conversion.

| File | Why it is safe |
|------|---------------|
| `provider/providerColourErr.jsp` | Static error message, no forms, fully i18n |
| `provider/providerFaxErr.jsp` | Static error message, no forms, fully i18n |
| `encounter/TimeOut.jsp` | Auto-close popup, minimal JS |
| `encounter/oscarConsultationRequest/nothingtoPrint.jsp` | Static message, auto-redirect |
| `encounter/License.jsp` | Static license text, no forms |

**Future batches** should work through the remaining `provider/set*.jsp` preference pages
(rows 4–16), which all share a near-identical template, making bulk conversion efficient.

---

## Related Documents

- `docs/oscar-to-carlos-bootstrap-conversion.md` — conversion rules and class mapping
- `docs/CARLOS_bootstrap_layout_Example.html` — reference Bootstrap 5 template
- `docs/OSCAR_standard_layout_Example.html` — annotated legacy OSCAR layout
- `docs/JSP-REFACTORING-GUIDE.md` — detailed JSP refactoring process
