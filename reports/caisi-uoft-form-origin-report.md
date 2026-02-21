# CAISI / UofT origin check for requested form classes

Method used:
- Checked each requested class file for the copyright/org header.
- Marked a form as **CAISI/UofT-origin likely** only when the header explicitly points to:
  `Centre for Research on Inner City Health, St. Michael's Hospital, Toronto`.
- Marked all `Department of Family Medicine, McMaster University` headers as **not CAISI/UofT** (OSCAR/McMaster legacy origin).
- If a file path from the request does not exist exactly, noted it as missing/typo.

## Likely CAISI / UofT-origin forms (explicit Toronto/CRICH header)
- `src/main/java/io/github/carlos_emr/carlos/form/FrmCounsellorAssessmentRecord.java`
- `src/main/java/io/github/carlos_emr/carlos/form/FrmDischargeSummaryRecord.java`
- `src/main/java/io/github/carlos_emr/carlos/form/FrmReceptionAssessmentRecord.java`

## Not CAISI / UofT by header (mostly McMaster legacy)
- `Frm2MinWalkRecord`
- `FrmARRecord`
- `FrmAnnualV2Record`
- `FrmBCAR2007Record`
- `FrmBCAR2012Record`
- `FrmBCARRecord`
- `FrmBCBirthSumMo2008Record`
- `FrmBCBrithSumMoRecord`
- `FrmBCClientChartChecklistRecord`
- `FrmBCHPRecord`
- `FrmBCNewBorn2008Record`
- `FrmBCNewBornRecord`
- `FrmCESDRecord`
- `FrmCaregiverRecord`
- `FrmCostQuestionnaireRecord`
- `FrmCounselingRecord`
- `FrmFallsRecord`
- `FrmGripStrengthRecord`
- `FrmGrowthChartRecord`
- `FrmHomeFallsRecord`
- `FrmImmunAllergyRecord`
- `FrmIntakeHxRecord`
- `FrmIntakeInfoRecord`
- `FrmInternetAccessRecord`
- `FrmLateLifeFDIDisabilityRecord`
- `FrmLateLifeFDIFunctionRecord`
- `FrmMMSERecord`
- `FrmONARRecord`
- `FrmOvulationRecord`
- `FrmPalliativeCareRecord`
- `FrmPeriMenopausalRecord`
- `FrmPolicyRecord`
- `FrmPositionHazardRecord`
- `FrmRhImmuneGlobulinRecord`
- `FrmSF36CaregiverRecord`
- `FrmSF36Record`
- `FrmSatisfactionScaleRecord`
- `FrmSelfAdministeredRecord`
- `FrmSelfAssessmentRecord`
- `FrmSelfEfficacyRecord`
- `FrmSelfManagementRecord`
- `FrmTreatmentPrefRecord`
- `FrmType2DiabeteRecord`
- `FrmPdfGraphicAR` (McMaster header)

## Other origins / special cases
- `FrmGrowth0_36Record` -> OpenSoft System header, not CAISI/UofT.
- `FrmInvoiceRecord` -> OSCAR Team header, not CAISI/UofT.
- `FrmchfRecord` -> Peter Hutten-Czapski header, not CAISI/UofT.
- `FormBooleanValuePK` -> WELL EMR Group Inc. (newer CARLOS code), not CAISI/UofT.

## Requested paths that do not exist as written
- `src/main/java/io/github/carlos_emr/carlos/form/FrmAdfRecord.Focord.java` (likely typo; existing file is `FrmAdfRecord.java`)
- `src/main/java/io/github/carlos_emr/carlos/form/graphic/FrmPdfGraphicRo?` (likely `FrmPdfGraphicRourke.java`)

## Notes on your extra questions
- `FrmSF36Record` / `FrmSF36CaregiverRecord`: both are McMaster-header forms, **not CAISI/UofT by file header evidence**.
- `FrmType2DiabeteRecord`: McMaster-header form. If you want “better flowsheet” lineage, that should be traced in measurement flowsheet resources rather than this form-record class.
