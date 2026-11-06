# Form record JPA migration — issue #5693

Inventory of `release/2026.08` at `710fb1ac7c3fa75528359e71e8fac0d590b9e36f`. Rechecked 2026-11-05; counts describe this source tree, not the historical source-ticket inventory.

There are **62 concrete form record implementations**, plus `FrmRecord`, `FrmRecordFactory`, `FrmRecordHelp`, form actions, `FrmData`, and the Rourke model helpers. The historical list is not the current implementation list. An absent historical class is not evidence of a completed migration.

## Historical names

| Historical implementation | Current exact class |
| --- | --- |
| `FrmALSRecord` | Absent; no migration inferred |
| `FrmANERecord` | Absent; no migration inferred |
| `FrmAntenatalRecord` | Absent; no migration inferred |
| `FrmCDMRecord` | Absent; no migration inferred |
| `FrmCML1Record` | Absent; no migration inferred |
| `FrmCONSRecord` | Absent; no migration inferred |
| `FrmCPP14Record` | Absent; no migration inferred |
| `FrmCdmDiabetesRecord` | Absent; no migration inferred |
| `FrmCdmIhdRecord` | Absent; no migration inferred |
| `FrmCdmSmokerRecord` | Absent; no migration inferred |
| `FrmCdmYRecord` | Absent; no migration inferred |
| `FrmD5Record` | Absent; no migration inferred |
| `FrmDMRecord` | Absent; no migration inferred |
| `FrmDPRecord` | Absent; no migration inferred |
| `FrmEpisodeRecord` | Absent; no migration inferred |
| `FrmFURecord` | Absent; no migration inferred |
| `FrmGrowth0_36Record` | Migrated in this slice to `Growth0To36FormService` and `Growth0To36FormDao`; legacy class removed |
| `FrmHPRecord` | Absent; no migration inferred |
| `FrmHaitiRecord` | Absent; no migration inferred |
| `FrmINRecord` | Absent; no migration inferred |
| `FrmINTRecord` | Absent; no migration inferred |
| `FrmINT2Record` | Absent; no migration inferred |
| `FrmKitchenerRecord` | Absent; no migration inferred |
| `FrmMHRecord` | Absent; no migration inferred |
| `FrmMHPRecord` | Absent; no migration inferred |
| `FrmMSRecord` | Absent; no migration inferred |
| `FrmMSPRecord` | Absent; no migration inferred |
| `FrmMentalHealthRecord` | [FrmMentalHealthRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmMentalHealthRecord.java) |
| `FrmOBRecord` | Absent; no migration inferred |
| `FrmONARRecord` | Absent; no migration inferred |
| `FrmONMDRecord` | Absent; no migration inferred |
| `FrmONMD2Record` | Absent; no migration inferred |
| `FrmONMD3Record` | Absent; no migration inferred |
| `FrmPCRecord` | Absent; no migration inferred |
| `FrmPRRecord` | Absent; no migration inferred |
| `FrmRSRecord` | Absent; no migration inferred |
| `FrmRourkeRecord` | [FrmRourkeRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmRourkeRecord.java) |
| `FrmRourke09Record` | Absent; no migration inferred |
| `FrmRourke17Record` | Absent; no migration inferred |
| `FrmRourke20Record` | Absent; no migration inferred |
| `FrmSPRecord` | Absent; no migration inferred |
| `FrmSTRecord` | Absent; no migration inferred |
| `FrmSOAPRecord` | Absent; no migration inferred |
| `FrmSkinRecord` | Absent; no migration inferred |
| `FrmWHRecord` | Absent; no migration inferred |
| `FrmXTRecord` | Absent; no migration inferred |

Current `Rourke2009`, `Rourke2017`, and `Rourke2020` classes have different names from historical `Rourke09`, `Rourke17`, and `Rourke20`. ONAR/CDM classes are absent from this branch; do not recreate their retired workflows solely to satisfy a historical count.

## Current implementations

Every suffix below is accepted by `FrmRecordFactory`. Common callers are `Frm2Action` (save, autosave, graph and print), `FrmPDFServlet`, and the corresponding JSP through the factory. Form table names below are extracted from the implementation, including helper-based persistence; demographic/provider/measurement reads are listed as related tables where present.

| Record | Tables referenced | Persistence path | Write boundary | Direct class callers outside the implementation |
| --- | --- | --- | --- | --- |
| [Frm2MinWalkRecord](../src/main/java/io/github/carlos_emr/carlos/form/Frm2MinWalkRecord.java) | `demographic`, `form2MinWalk`, `rehabStudy2004` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmAdfV2Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmAdfV2Record.java) | `demographic`, `formAdfV2` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmAlphaRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmAlphaRecord.java) | `formAlpha` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | `src/main/java/io/github/carlos_emr/carlos/form/Frm2Action.java` |
| [FrmAnnualRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmAnnualRecord.java) | `formAnnual` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmAnnualV2Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmAnnualV2Record.java) | `formAnnualV2` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmBCAR2007Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmBCAR2007Record.java) | `demographic`, `formBCAR2007` | FrmRecordHelp, DemographicExtDao | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmBCAR2012Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmBCAR2012Record.java) | `formBCAR2012` | FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmBCAR2020Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmBCAR2020Record.java) | `demographic`, `formBCAR2020`, `formBCAR2020Data`, `formBCAR2020Text`, `provider` | FrmRecordHelp, FormBCAR2020Dao, FormBCAR2020DataDao, FormBCAR2020TextDao | BCAR2020FormSaveService (proxied) | `src/main/java/io/github/carlos_emr/carlos/form/BCAR2020FormSaveService.java`<br>`src/main/java/io/github/carlos_emr/carlos/form/FrmBCAR20202Action.java`<br>`src/main/webapp/WEB-INF/jsp/form/formBCAR2020Attachments.jsp`<br>`src/main/webapp/WEB-INF/jsp/form/formBCAR2020pg1.jsp`<br>`src/main/webapp/WEB-INF/jsp/form/formBCAR2020pg2.jsp`<br>`src/main/webapp/WEB-INF/jsp/form/formBCAR2020pg3.jsp`<br>`src/main/webapp/WEB-INF/jsp/form/formBCAR2020pg4.jsp`<br>`src/main/webapp/WEB-INF/jsp/form/formBCAR2020pg5.jsp` |
| [FrmBCARRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmBCARRecord.java) | `formBCAR` | FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmBCBirthSumMo2008Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmBCBirthSumMo2008Record.java) | `formBCBirthSumMo2008` | FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmBCBrithSumMoRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmBCBrithSumMoRecord.java) | `formBCBirthSumMo` | FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmBCClientChartChecklistRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmBCClientChartChecklistRecord.java) | `clinic`, `demographic`, `formBCClientChartChecklist` | direct LegacyJdbcQuery, FrmRecordHelp, ClinicDAO | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmBCHPRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmBCHPRecord.java) | `demographic`, `formBCHP`, `provider` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmBCINRRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmBCINRRecord.java) | `demographic`, `form`, `formBCINR`, `hl7_obr`, `hl7_obx`, `patientLabRouting` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | `src/main/webapp/WEB-INF/jsp/form/formbcinr.jsp` |
| [FrmBCNewBorn2008Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmBCNewBorn2008Record.java) | `formBCNewBorn2008` | FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmBCNewBornRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmBCNewBornRecord.java) | `formBCNewBorn` | FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmCESDRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmCESDRecord.java) | `demographic`, `formCESD`, `rehabStudy2004` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmCaregiverRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmCaregiverRecord.java) | `demographic`, `formCaregiver`, `rehabStudy2004` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmConsultantRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmConsultantRecord.java) | `clinic`, `demographic`, `formConsult`, `professionalSpecialists`, `provider` | direct LegacyJdbcQuery, FrmRecordHelp, ClinicDAO, ProfessionalSpecialistDao | No service transaction in record; helper/DAO owns writes | `src/main/webapp/WEB-INF/jsp/form/formConsultant.jsp` |
| [FrmCostQuestionnaireRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmCostQuestionnaireRecord.java) | `demographic`, `formCostQuestionnaire`, `rehabStudy2004` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmCounselingRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmCounselingRecord.java) | `clinic`, `demographic`, `formCounseling` | FrmRecordHelp, ClinicDAO | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmCounsellorAssessmentRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmCounsellorAssessmentRecord.java) |  | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmDischargeSummaryRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmDischargeSummaryRecord.java) | `admission`, `allergies`, `casemgmt_issue`, `demographic`, `drugs`, `formDischargeSummary`, `issue`, `program`, `provider` | direct LegacyJdbcQuery, FrmRecordHelp, AllergyDao | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmFallsRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmFallsRecord.java) | `demographic`, `formFalls`, `rehabStudy2004` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmGripStrengthRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmGripStrengthRecord.java) | `demographic`, `formGripStrength`, `rehabStudy2004` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmGrowth0_36Record](https://github.com/carlos-emr/carlos/blob/710fb1ac7c3fa75528359e71e8fac0d590b9e36f/src/main/java/io/github/carlos_emr/carlos/form/FrmGrowth0_36Record.java) (removed in this slice) | `demographic`, `formGrowth0_36` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmGrowthChartRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmGrowthChartRecord.java) | `demographic`, `formGrowthChart` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmHomeFallsRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmHomeFallsRecord.java) | `demographic`, `formHomeFalls`, `rehabStudy2004` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmImmunAllergyRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmImmunAllergyRecord.java) | `demographic`, `formImmunAllergy` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmIntakeInfoRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmIntakeInfoRecord.java) | `demographic`, `formIntakeInfo`, `rehabStudy2004` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmInternetAccessRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmInternetAccessRecord.java) | `demographic`, `formInternetAccess`, `rehabStudy2004` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmInvoiceRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmInvoiceRecord.java) | `demographic` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmLabReq07Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmLabReq07Record.java) | `clinic`, `demographic`, `formLabReq07`, `provider` | direct LegacyJdbcQuery, FrmRecordHelp, ClinicDAO | No service transaction in record; helper/DAO owns writes | `src/main/java/io/github/carlos_emr/carlos/commn/web/Pregnancy2Action.java`<br>`src/main/java/io/github/carlos_emr/carlos/form/Frm2Action.java`<br>`src/main/webapp/WEB-INF/jsp/form/formlabreq07.jsp` |
| [FrmLabReq10Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmLabReq10Record.java) | `clinic`, `demographic`, `formLabReq10`, `provider` | FrmRecordHelp, ClinicDAO, ProviderDao | No service transaction in record; helper/DAO owns writes | `src/main/java/io/github/carlos_emr/carlos/commn/web/Pregnancy2Action.java`<br>`src/main/webapp/WEB-INF/jsp/form/formlabreq10.jsp` |
| [FrmLabReqRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmLabReqRecord.java) | `clinic`, `formLabReq`, `provider` | direct LegacyJdbcQuery, FrmRecordHelp, ClinicDAO | No service transaction in record; helper/DAO owns writes | `src/main/webapp/WEB-INF/jsp/form/formlabreq.jsp` |
| [FrmLateLifeFDIDisabilityRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmLateLifeFDIDisabilityRecord.java) | `demographic`, `formLateLifeFDIDisability`, `rehabStudy2004` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmLateLifeFDIFunctionRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmLateLifeFDIFunctionRecord.java) | `demographic`, `formLateLifeFDIFunction`, `rehabStudy2004` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmMMSERecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmMMSERecord.java) | `formMMSE` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmMentalHealthForm14Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmMentalHealthForm14Record.java) | `demographic`, `formMentalHealthForm14`, `provider` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | `src/main/webapp/WEB-INF/jsp/form/formMentalHealthForm14.jsp` |
| [FrmMentalHealthForm1Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmMentalHealthForm1Record.java) | `demographic`, `formMentalHealthForm1`, `provider` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | `src/main/webapp/WEB-INF/jsp/form/formMentalHealthForm1.jsp` |
| [FrmMentalHealthForm42Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmMentalHealthForm42Record.java) | `demographic`, `formMentalHealthForm42`, `provider` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | `src/main/webapp/WEB-INF/jsp/form/formMentalHealthForm42.jsp` |
| [FrmMentalHealthRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmMentalHealthRecord.java) | `demographic`, `formMentalHealth` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | `src/main/webapp/WEB-INF/jsp/form/formmhassessment.jsp`<br>`src/main/webapp/WEB-INF/jsp/form/formmhoutcome.jsp`<br>`src/main/webapp/WEB-INF/jsp/form/formmhreferral.jsp` |
| [FrmPalliativeCareRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmPalliativeCareRecord.java) | `formPalliativeCare` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmPeriMenopausalRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmPeriMenopausalRecord.java) | `formPeriMenopausal` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmPolicyRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmPolicyRecord.java) | `demographic`, `formNoShowPolicy` | FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmPositionHazardRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmPositionHazardRecord.java) | `clinic`, `demographic`, `formPositionHazard` | FrmRecordHelp, ClinicDAO | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmReceptionAssessmentRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmReceptionAssessmentRecord.java) | `formreceptionassessment` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmRhImmuneGlobulinRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmRhImmuneGlobulinRecord.java) | `demographic`, `formRhImmuneGlobulin` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmRourke2006Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmRourke2006Record.java) | `demographic`, `formRourke2006` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | `src/main/webapp/WEB-INF/jsp/form/formrourke2006p1.jsp`<br>`src/main/webapp/WEB-INF/jsp/form/formrourke2006p2.jsp`<br>`src/main/webapp/WEB-INF/jsp/form/formrourke2006p3.jsp`<br>`src/main/webapp/WEB-INF/jsp/form/formrourke2006p4.jsp` |
| [FrmRourke2009Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmRourke2009Record.java) | `demographic`, `formGrowth0_36`, `formRourke2009`, `measurements` | FrmRecordHelp, MeasurementDao, Rourke2009DAO | No service transaction in record; helper/DAO owns writes | `src/main/webapp/WEB-INF/jsp/form/formrourke2009p1.jsp`<br>`src/main/webapp/WEB-INF/jsp/form/formrourke2009p2.jsp`<br>`src/main/webapp/WEB-INF/jsp/form/formrourke2009p3.jsp`<br>`src/main/webapp/WEB-INF/jsp/form/formrourke2009p4.jsp` |
| [FrmRourke2017Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmRourke2017Record.java) | `demographic`, `formGrowth0_36`, `formRourke2017`, `form_boolean_value`, `measurements`, `report` | FrmRecordHelp, MeasurementDao, Rourke2017Dao | No service transaction in record; helper/DAO owns writes | `src/main/webapp/WEB-INF/jsp/form/formrourke2017p1.jsp`<br>`src/main/webapp/WEB-INF/jsp/form/formrourke2017p2.jsp`<br>`src/main/webapp/WEB-INF/jsp/form/formrourke2017p3.jsp`<br>`src/main/webapp/WEB-INF/jsp/form/formrourke2017p4.jsp` |
| [FrmRourke2020Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmRourke2020Record.java) | `demographic`, `formGrowth0_36`, `formRourke2020`, `form_boolean_value`, `measurements` | FrmRecordHelp, MeasurementDao, Rourke2020Dao | No service transaction in record; helper/DAO owns writes | `src/main/webapp/WEB-INF/jsp/form/formRourke2020p1.jsp`<br>`src/main/webapp/WEB-INF/jsp/form/formRourke2020p2.jsp`<br>`src/main/webapp/WEB-INF/jsp/form/formRourke2020p3.jsp`<br>`src/main/webapp/WEB-INF/jsp/form/formRourke2020p4.jsp` |
| [FrmRourkeRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmRourkeRecord.java) | `demographic`, `formRourke` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | `src/main/webapp/WEB-INF/jsp/form/formrourke1.jsp`<br>`src/main/webapp/WEB-INF/jsp/form/formrourke2.jsp`<br>`src/main/webapp/WEB-INF/jsp/form/formrourke3.jsp`<br>`src/main/webapp/WEB-INF/jsp/form/formrourkep1.jsp`<br>`src/main/webapp/WEB-INF/jsp/form/formrourkep2.jsp`<br>`src/main/webapp/WEB-INF/jsp/form/formrourkep3.jsp`<br>`src/main/webapp/WEB-INF/jsp/form/graphHeadCirc.jsp`<br>`src/main/webapp/WEB-INF/jsp/form/graphLengthWeight.jsp` |
| [FrmSF36CaregiverRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmSF36CaregiverRecord.java) | `demographic`, `formSF36Caregiver`, `rehabStudy2004` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmSF36Record](../src/main/java/io/github/carlos_emr/carlos/form/FrmSF36Record.java) | `demographic`, `formSF36`, `rehabStudy2004` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmSatisfactionScaleRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmSatisfactionScaleRecord.java) | `demographic`, `formSatisfactionScale`, `rehabStudy2004` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmSelfAdministeredRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmSelfAdministeredRecord.java) | `demographic`, `formSelfAdministered`, `rehabStudy2004` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmSelfAssessmentRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmSelfAssessmentRecord.java) | `demographic`, `formSelfAssessment` | FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmSelfEfficacyRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmSelfEfficacyRecord.java) | `demographic`, `formSelfEfficacy`, `rehabStudy2004` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmSelfManagementRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmSelfManagementRecord.java) | `demographic`, `formSelfManagement`, `rehabStudy2004` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmTreatmentPrefRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmTreatmentPrefRecord.java) | `demographic`, `formTreatmentPref`, `rehabStudy2004` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |
| [FrmchfRecord](../src/main/java/io/github/carlos_emr/carlos/form/FrmchfRecord.java) | `formchf` | direct LegacyJdbcQuery, FrmRecordHelp | No service transaction in record; helper/DAO owns writes | Factory/JSP dispatch |

## Shared persistence and transaction boundaries

- `FrmRecordHelp`: metadata-driven SELECT, updatable ResultSet INSERT/UPDATE, `LAST_INSERT_ID()` on the same session, print projections, demographic ID queries, and optional XML archives. Missing strings become SQL NULL on save and empty strings on normal form load; dates and timestamps have different formatting. Boolean load and print values differ. This helper must remain until all its callers have migrated.
- `Frm2Action`: instantiates records through the allowlisted factory; overwrites `provider_no` from the session, saves before logging and navigation; multi-page merges are assembled before save. Preserve audit identifiers, autosave and print forwarding.
- `FrmForm2Action` / `FrmSetupForm2Action`: configured XML measurement forms use `FrmRecordHelp`; the save action has a TransactionTemplate around form and measurement writes. This is a separate dynamic-schema path that must not lose its shared connection participation.
- `FrmData`: latest-record and shortcut queries; callers include encounter shortcuts. Its registered-table validation and ordering must be preserved.
- `FormRourke2017` / `FormRourke2020`: model helpers still issue LegacyJdbcQuery demographic projections. Rourke headers and form_boolean_value writes span multiple tables; existing entities alone do not prove atomic saves.
- `BCAR2020FormSaveService`: already commits header, normal fields and large text atomically through Spring. Preserve its independent batch/rollback regression coverage.
- `EctFormData`: registered-table history/list/group queries and the built-in `formGrowth0_36` exception. Migrate the Growth history callers to its typed DAO before dropping that exception; coordinate generic encounter query work with #5547.
- `JDBCUtil`: legacy XML export/import is a second Growth built-in-table consumer. Its archive/import contract is distinct from EctFormData validation and must remain compatible.
- `FormRecord` / `HtmlRecord` in the document/report infrastructure are outside this form-record slice; shared JDBC lifecycle cleanup remains required by other modules.

## Migration order and verification

1. Growth 0–36m: map the physical table, typed DAO, proxied transactional save, factory adapter, patient-scoped reads, optional XML archive, encounter history and graph/export callers. Delete its legacy implementation only after all callers use the replacement.
2. Other single-table forms: retain every column, generated ID, null/string/date/checkbox representation, version-insert semantics and auxiliary demographic defaults.
3. Rourke/BCAR and other multi-table forms: place all writes under a proxy-reached service transaction and force a failure after an earlier write; verify rollback through a fresh independent connection.
4. Migrate remaining setup/shortcut/model helper paths, then remove FrmRecordHelp persistence and the remaining legacy record implementations. Do not delete JDBC lifecycle utilities used outside this package.

For each slice run H2 and physical MariaDB tests against generated and Flyway schemas (ON and BC where applicable), build and install Debian packages on Ubuntu 26.04, and run the relevant synthetic Playwright form save/load/print/graph suite. Update #2249 and #5693 with completed and remaining work; do not close the umbrella from a partial slice.

## Completed Growth 0–36m slice

`Growth0To36Form` maps all 131 physical columns, with twenty embedded visit slots. `Growth0To36FormDao` owns patient-bound entity/header/group queries. `Growth0To36FormService` authorizes form and demographic access and owns the proxied append-version transaction; provider identity comes from the authenticated actor. `Growth0To36FormAdapter` retains factory/print dispatch, and the legacy class is deleted.

`EctFormData` history/grouping and all Growth branches of `FrmData` use the typed DAO. `EctFormData.INTERNAL_FORM_TABLES` is removed; generic registered-table queries remain for #5547. Rourke graph and eForm growth reads reach the new adapter through the existing factory name. Optional XML copies retain their existing field names and encoding and run only after commit. The independent XML import allowlist in `JDBCUtil` remains required.

Remaining: **61 record implementations** and the shared/setup/Rourke model paths above. This PR does not close #5693 or #2249. Physical transaction coverage is required in generated, ON Flyway and BC Flyway CI lanes; tests check all visit slots, generated IDs, null/date/string behavior, patient isolation, rollback after a flushed insert and failure of a later database write, and XML publication after commit.
