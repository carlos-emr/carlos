# Unused Classes Report - CARLOS EMR

**Date**: 2026-02-16
**Scope**: All Java source files under `src/main/java/`
**Verified unused count**: 208 classes

## Methodology

Classes were identified as unused through a multi-layered analysis:

1. **Java source cross-reference**: Each public/package-private class was checked for references from other `.java` files (excluding self-references and test files)
2. **Struts XML configuration**: Checked `struts.xml`, `struts-config.xml` for action/form mappings
3. **Spring XML configuration**: Checked all `applicationContext*.xml`, `spring_ws.xml`, `cxf.xml` for bean definitions and endpoint declarations
4. **Hibernate/JPA configuration**: Checked all `*.hbm.xml`, `persistence.xml`, `ehcache.xml`
5. **JSP scriptlet references**: Searched all `*.jsp` and `*.jspf` files for class imports and usage
6. **Spring component scanning**: Cross-referenced `@Component`/`@Service`/`@Repository` annotations with component-scan base packages, then checked for consumers of their interfaces
7. **Reflection and dynamic lookup**: Searched for `Class.forName()`, `SpringUtils.getBean()` string-based lookups, and `@Aspect` AOP pointcuts
8. **Scheduler/job configurations**: Checked `applicationContextJobs.xml` and properties files
9. **Web service deployment**: Checked JAXWS endpoint declarations in `spring_ws.xml`

### False Positives Caught by Framework Analysis

22 classes initially flagged as unused were rescued by the framework-level checks:

| Class | Reason Not Unused |
|-------|-------------------|
| `AppointmentProviderAdminDayUIBean` | Used in 2 JSP files via scriptlet import |
| `CaisiUtil` | Used in JSP file (`infirmaryviewprogramlist.jspf`) |
| `VisitReportData` | Used in JSP file (`oscarReportVisit_vr.jspf`) |
| `BatchBillingDaoImpl` | `@Repository` - interface consumed by `BatchBill2Action` |
| `CSSStylesDaoImpl` | `@Repository` - interface consumed by `ManageCSS2Action` |
| `DefaultNoteService` | `@Component` - sole impl of `NoteService` (4 consumers) |
| `DrugLookUpManager` | `@Service` - sole impl of `DrugLookUp` (3 consumers) |
| `MyDemographicEventListener` | `@Component` - Spring ApplicationListener for demographic events |
| `WebServiceLoggingAdvice` | `@Aspect` + `@Component` - AOP pointcut on REST services |
| `AllergyWs` | `@WebService` - JAXWS endpoint in `spring_ws.xml` |
| `BookingWs` | `@WebService` - JAXWS endpoint in `spring_ws.xml` |
| `DocumentWs` | `@WebService` - JAXWS endpoint in `spring_ws.xml` |
| `MeasurementWs` | `@WebService` - JAXWS endpoint in `spring_ws.xml` |
| `PrescriptionWs` | `@WebService` - JAXWS endpoint in `spring_ws.xml` |
| `PreventionWs` | `@WebService` - JAXWS endpoint in `spring_ws.xml` |
| `AllergiesSummary` | `@Component` - string-based bean lookup from `RecordUxService` |
| `DecisionSupportSummary` | `@Component` - string-based bean lookup from `RecordUxService` |
| `FormsSummary` | `@Component` - string-based bean lookup from `RecordUxService` |
| `LabsDocsSummary` | `@Component` - string-based bean lookup from `RecordUxService` |
| `OngoingConcernDxRegSummary` | `@Component` - string-based bean lookup from `RecordUxService` |
| `PreventionsSummary` | `@Component` - string-based bean lookup from `RecordUxService` |
| `RxSummary` | `@Component` - string-based bean lookup from `RecordUxService` |

---

## Summary by Category

| Category | Count | Risk Level | Notes |
|----------|-------|------------|-------|
| Form Records (`Frm*Record`) | 53 | Low | Legacy medical form data classes |
| Form Utilities | 1 | Low | `FormBooleanValuePK`, `FrmPdfGraphicRourke` |
| Encounter Data Records (`Ect*Record`) | 10 | Low | Legacy encounter form data |
| DAO Implementations (no consumers) | 24 | Medium | `@Repository` beans with zero interface consumers |
| CAISI Integrator | 12 | Low | Integrator WS clients and data objects |
| PMmodule | 11 | Low | Legacy program management classes |
| REST Transfer Objects | 12 | Low | Unused REST DTOs and converters |
| Billing | 7 | Low | Legacy billing data/handler classes |
| Jobs/Schedulers | 6 | Low | Unconfigured job classes |
| Other (misc utilities, actions, etc.) | 72 | Low-Medium | Mixed bag of utilities and legacy code |

---

## Detailed Unused Class List

### 1. Form Records (54 classes) - LOW RISK

Legacy medical form data record classes. None are referenced from any Java source, JSP, or configuration file.

| Class | File |
|-------|------|
| `Frm2MinWalkRecord` | `src/main/java/io/github/carlos_emr/carlos/form/Frm2MinWalkRecord.java` |
| `FrmARRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmARRecord.java` |
| `FrmAdfRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmAdfRecord.java` |
| `FrmAdfV2Record` | `src/main/java/io/github/carlos_emr/carlos/form/FrmAdfV2Record.java` |
| `FrmAnnualRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmAnnualRecord.java` |
| `FrmAnnualV2Record` | `src/main/java/io/github/carlos_emr/carlos/form/FrmAnnualV2Record.java` |
| `FrmBCAR2007Record` | `src/main/java/io/github/carlos_emr/carlos/form/FrmBCAR2007Record.java` |
| `FrmBCAR2012Record` | `src/main/java/io/github/carlos_emr/carlos/form/FrmBCAR2012Record.java` |
| `FrmBCARRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmBCARRecord.java` |
| `FrmBCBirthSumMo2008Record` | `src/main/java/io/github/carlos_emr/carlos/form/FrmBCBirthSumMo2008Record.java` |
| `FrmBCBrithSumMoRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmBCBrithSumMoRecord.java` |
| `FrmBCClientChartChecklistRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmBCClientChartChecklistRecord.java` |
| `FrmBCHPRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmBCHPRecord.java` |
| `FrmBCNewBorn2008Record` | `src/main/java/io/github/carlos_emr/carlos/form/FrmBCNewBorn2008Record.java` |
| `FrmBCNewBornRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmBCNewBornRecord.java` |
| `FrmCESDRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmCESDRecord.java` |
| `FrmCaregiverRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmCaregiverRecord.java` |
| `FrmCostQuestionnaireRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmCostQuestionnaireRecord.java` |
| `FrmCounselingRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmCounselingRecord.java` |
| `FrmCounsellorAssessmentRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmCounsellorAssessmentRecord.java` |
| `FrmDischargeSummaryRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmDischargeSummaryRecord.java` |
| `FrmFallsRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmFallsRecord.java` |
| `FrmGripStrengthRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmGripStrengthRecord.java` |
| `FrmGrowth0_36Record` | `src/main/java/io/github/carlos_emr/carlos/form/FrmGrowth0_36Record.java` |
| `FrmGrowthChartRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmGrowthChartRecord.java` |
| `FrmHomeFallsRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmHomeFallsRecord.java` |
| `FrmImmunAllergyRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmImmunAllergyRecord.java` |
| `FrmIntakeHxRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmIntakeHxRecord.java` |
| `FrmIntakeInfoRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmIntakeInfoRecord.java` |
| `FrmInternetAccessRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmInternetAccessRecord.java` |
| `FrmInvoiceRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmInvoiceRecord.java` |
| `FrmLateLifeFDIDisabilityRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmLateLifeFDIDisabilityRecord.java` |
| `FrmLateLifeFDIFunctionRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmLateLifeFDIFunctionRecord.java` |
| `FrmMMSERecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmMMSERecord.java` |
| `FrmONARRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmONARRecord.java` |
| `FrmOvulationRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmOvulationRecord.java` |
| `FrmPalliativeCareRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmPalliativeCareRecord.java` |
| `FrmPeriMenopausalRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmPeriMenopausalRecord.java` |
| `FrmPolicyRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmPolicyRecord.java` |
| `FrmPositionHazardRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmPositionHazardRecord.java` |
| `FrmReceptionAssessmentRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmReceptionAssessmentRecord.java` |
| `FrmRhImmuneGlobulinRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmRhImmuneGlobulinRecord.java` |
| `FrmSF36CaregiverRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmSF36CaregiverRecord.java` |
| `FrmSF36Record` | `src/main/java/io/github/carlos_emr/carlos/form/FrmSF36Record.java` |
| `FrmSatisfactionScaleRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmSatisfactionScaleRecord.java` |
| `FrmSelfAdministeredRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmSelfAdministeredRecord.java` |
| `FrmSelfAssessmentRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmSelfAssessmentRecord.java` |
| `FrmSelfEfficacyRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmSelfEfficacyRecord.java` |
| `FrmSelfManagementRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmSelfManagementRecord.java` |
| `FrmTreatmentPrefRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmTreatmentPrefRecord.java` |
| `FrmType2DiabeteRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmType2DiabeteRecord.java` |
| `FrmchfRecord` | `src/main/java/io/github/carlos_emr/carlos/form/FrmchfRecord.java` |
| `FrmPdfGraphicRourke` | `src/main/java/io/github/carlos_emr/carlos/form/graphic/FrmPdfGraphicRourke.java` |
| `FormBooleanValuePK` | `src/main/java/io/github/carlos_emr/carlos/form/model/FormBooleanValuePK.java` |

### 2. Encounter Data Records (10 classes) - LOW RISK

Legacy encounter form data record classes with no references anywhere.

| Class | File |
|-------|------|
| `EctARRecord` | `src/main/java/io/github/carlos_emr/carlos/encounter/data/EctARRecord.java` |
| `EctAlphaRecord` | `src/main/java/io/github/carlos_emr/carlos/encounter/data/EctAlphaRecord.java` |
| `EctAnnualRecord` | `src/main/java/io/github/carlos_emr/carlos/encounter/data/EctAnnualRecord.java` |
| `EctMMSERecord` | `src/main/java/io/github/carlos_emr/carlos/encounter/data/EctMMSERecord.java` |
| `EctMentalHealthRecord` | `src/main/java/io/github/carlos_emr/carlos/encounter/data/EctMentalHealthRecord.java` |
| `EctPalliativeCareRecord` | `src/main/java/io/github/carlos_emr/carlos/encounter/data/EctPalliativeCareRecord.java` |
| `EctPeriMenopausalRecord` | `src/main/java/io/github/carlos_emr/carlos/encounter/data/EctPeriMenopausalRecord.java` |
| `EctProgram_improved` | `src/main/java/io/github/carlos_emr/carlos/encounter/data/EctProgram_improved.java` |
| `EctRourkeRecord` | `src/main/java/io/github/carlos_emr/carlos/encounter/data/EctRourkeRecord.java` |
| `EctType2DiabetesRecord` | `src/main/java/io/github/carlos_emr/carlos/encounter/data/EctType2DiabetesRecord.java` |

### 3. DAO Implementations with No Consumers (24 classes) - MEDIUM RISK

These are `@Repository`-annotated Spring beans. Spring will instantiate them at startup, but no code ever injects or looks up their interfaces. Removing these should also remove their corresponding interface files.

| Class | File |
|-------|------|
| `CaisiFormDaoImpl` | `src/main/java/io/github/carlos_emr/carlos/commn/dao/CaisiFormDaoImpl.java` |
| `CaisiFormDataDaoImpl` | `src/main/java/io/github/carlos_emr/carlos/commn/dao/CaisiFormDataDaoImpl.java` |
| `CaisiFormDataTmpSaveDaoImpl` | `src/main/java/io/github/carlos_emr/carlos/commn/dao/CaisiFormDataTmpSaveDaoImpl.java` |
| `CaisiFormInstanceDaoImpl` | `src/main/java/io/github/carlos_emr/carlos/commn/dao/CaisiFormInstanceDaoImpl.java` |
| `CaisiFormInstanceTmpSaveDaoImpl` | `src/main/java/io/github/carlos_emr/carlos/commn/dao/CaisiFormInstanceTmpSaveDaoImpl.java` |
| `CaisiFormQuestionDaoImpl` | `src/main/java/io/github/carlos_emr/carlos/commn/dao/CaisiFormQuestionDaoImpl.java` |
| `ConsultationRequestMergedDemographicDaoImpl` | `src/main/java/io/github/carlos_emr/carlos/commn/dao/ConsultationRequestMergedDemographicDaoImpl.java` |
| `DocumentResultsMergedDemographicDaoImpl` | `src/main/java/io/github/carlos_emr/carlos/commn/dao/DocumentResultsMergedDemographicDaoImpl.java` |
| `DrugMergedDemographicDaoImpl` | `src/main/java/io/github/carlos_emr/carlos/commn/dao/DrugMergedDemographicDaoImpl.java` |
| `DxAssociationDaoImpl` | `src/main/java/io/github/carlos_emr/carlos/commn/dao/DxAssociationDaoImpl.java` |
| `GroupNoteLinkDaoImpl` | `src/main/java/io/github/carlos_emr/carlos/commn/dao/GroupNoteLinkDaoImpl.java` |
| `IntegratorConsentComplexExitInterviewDaoImpl` | `src/main/java/io/github/carlos_emr/carlos/commn/dao/IntegratorConsentComplexExitInterviewDaoImpl.java` |
| `MdsZCLDaoImpl` | `src/main/java/io/github/carlos_emr/carlos/commn/dao/MdsZCLDaoImpl.java` |
| `MdsZCTDaoImpl` | `src/main/java/io/github/carlos_emr/carlos/commn/dao/MdsZCTDaoImpl.java` |
| `MdsZFRDaoImpl` | `src/main/java/io/github/carlos_emr/carlos/commn/dao/MdsZFRDaoImpl.java` |
| `OscarAnnotationDaoImpl` | `src/main/java/io/github/carlos_emr/carlos/commn/dao/OscarAnnotationDaoImpl.java` |
| `OscarCodeDaoImpl` | `src/main/java/io/github/carlos_emr/carlos/commn/dao/OscarCodeDaoImpl.java` |
| `OscarMsgTypeDaoImpl` | `src/main/java/io/github/carlos_emr/carlos/commn/dao/OscarMsgTypeDaoImpl.java` |
| `PrescribeDaoImpl` | `src/main/java/io/github/carlos_emr/carlos/commn/dao/PrescribeDaoImpl.java` |
| `PreventionMergedDemographicDaoImpl` | `src/main/java/io/github/carlos_emr/carlos/commn/dao/PreventionMergedDemographicDaoImpl.java` |
| `ProgramAccessRolesDaoImpl` | `src/main/java/io/github/carlos_emr/carlos/commn/dao/ProgramAccessRolesDaoImpl.java` |
| `ReadLabDaoImpl` | `src/main/java/io/github/carlos_emr/carlos/commn/dao/ReadLabDaoImpl.java` |
| `RecycleBinBillingDaoImpl` | `src/main/java/io/github/carlos_emr/carlos/commn/dao/RecycleBinBillingDaoImpl.java` |
| `RemoteDataLogDaoImpl` | `src/main/java/io/github/carlos_emr/carlos/commn/dao/RemoteDataLogDaoImpl.java` |

### 4. CAISI Integrator Classes (12 classes) - LOW RISK

Web service client stubs and data objects for the CAISI integrator system. No consumers found.

| Class | File |
|-------|------|
| `ByteWrapper` | `src/main/java/io/github/carlos_emr/carlos/PMmodule/caisi_integrator/ByteWrapper.java` |
| `IntegratorLocalStoreUpdateJob` | `src/main/java/io/github/carlos_emr/carlos/PMmodule/caisi_integrator/IntegratorLocalStoreUpdateJob.java` |
| `CachedDemographicImage` | `src/main/java/io/github/carlos_emr/carlos/caisi_integrator/dao/CachedDemographicImage.java` |
| `EventLog` | `src/main/java/io/github/carlos_emr/carlos/caisi_integrator/dao/EventLog.java` |
| `HomelessPopulationReport` | `src/main/java/io/github/carlos_emr/carlos/caisi_integrator/dao/HomelessPopulationReport.java` |
| `MatchingCachedDemographicScore` | `src/main/java/io/github/carlos_emr/carlos/caisi_integrator/dao/MatchingCachedDemographicScore.java` |
| `DemographicWs_DemographicWsPort_Client` | `src/main/java/io/github/carlos_emr/carlos/caisi_integrator/ws/DemographicWs_DemographicWsPort_Client.java` |
| `FacilityWs_FacilityWsPort_Client` | `src/main/java/io/github/carlos_emr/carlos/caisi_integrator/ws/FacilityWs_FacilityWsPort_Client.java` |
| `HnrWs_HnrWsPort_Client` | `src/main/java/io/github/carlos_emr/carlos/caisi_integrator/ws/HnrWs_HnrWsPort_Client.java` |
| `ProgramWs_ProgramWsPort_Client` | `src/main/java/io/github/carlos_emr/carlos/caisi_integrator/ws/ProgramWs_ProgramWsPort_Client.java` |
| `ProviderWs_ProviderWsPort_Client` | `src/main/java/io/github/carlos_emr/carlos/caisi_integrator/ws/ProviderWs_ProviderWsPort_Client.java` |
| `ReferralWs_ReferralWsPort_Client` | `src/main/java/io/github/carlos_emr/carlos/caisi_integrator/ws/ReferralWs_ReferralWsPort_Client.java` |

### 5. PMmodule Classes (11 classes) - LOW RISK

Legacy program management actions, form beans, and utilities.

| Class | File |
|-------|------|
| `IntakeManager` | `src/main/java/io/github/carlos_emr/carlos/PMmodule/service/IntakeManager.java` |
| `MigrateStaffAssignments` | `src/main/java/io/github/carlos_emr/carlos/PMmodule/utility/MigrateStaffAssignments.java` |
| `CdsForm4Action` | `src/main/java/io/github/carlos_emr/carlos/PMmodule/web/CdsForm4Action.java` |
| `ClientManagerAction` | `src/main/java/io/github/carlos_emr/carlos/PMmodule/web/ClientManagerAction.java` |
| `ManageConsentAction` | `src/main/java/io/github/carlos_emr/carlos/PMmodule/web/ManageConsentAction.java` |
| `ManageHnrClientAction` | `src/main/java/io/github/carlos_emr/carlos/PMmodule/web/ManageHnrClientAction.java` |
| `PopulationReportUIBean` | `src/main/java/io/github/carlos_emr/carlos/PMmodule/web/PopulationReportUIBean.java` |
| `RatingView` | `src/main/java/io/github/carlos_emr/carlos/PMmodule/web/RatingView.java` |
| `DefaultRoleAccessFormBean` | `src/main/java/io/github/carlos_emr/carlos/PMmodule/web/formbean/DefaultRoleAccessFormBean.java` |
| `IntegratorPausedException` | `src/main/java/io/github/carlos_emr/carlos/PMmodule/web/forms/IntegratorPausedException.java` |
| `CustomReportDataSource` | `src/main/java/io/github/carlos_emr/carlos/PMmodule/web/reports/custom/CustomReportDataSource.java` |

### 6. REST Web Service Classes (12 classes) - LOW RISK

Unused REST DTOs, converters, and response objects.

| Class | File |
|-------|------|
| `ProviderSettingsConverter` | `src/main/java/io/github/carlos_emr/carlos/webserv/rest/conversion/ProviderSettingsConverter.java` |
| `QuickLinkConverter` | `src/main/java/io/github/carlos_emr/carlos/webserv/rest/conversion/QuickLinkConverter.java` |
| `DemographicMergeResponse` | `src/main/java/io/github/carlos_emr/carlos/webserv/rest/to/DemographicMergeResponse.java` |
| `DrugDSResponse` | `src/main/java/io/github/carlos_emr/carlos/webserv/rest/to/DrugDSResponse.java` |
| `PharmacyResponse` | `src/main/java/io/github/carlos_emr/carlos/webserv/rest/to/PharmacyResponse.java` |
| `PrimitiveResponseWrapper` | `src/main/java/io/github/carlos_emr/carlos/webserv/rest/to/PrimitiveResponseWrapper.java` |
| `ProgramDomainResponse` | `src/main/java/io/github/carlos_emr/carlos/webserv/rest/to/ProgramDomainResponse.java` |
| `DemographicContactTo1` | `src/main/java/io/github/carlos_emr/carlos/webserv/rest/to/model/DemographicContactTo1.java` |
| `DemographicSearchResults` | `src/main/java/io/github/carlos_emr/carlos/webserv/rest/to/model/DemographicSearchResults.java` |
| `DocumentCategory` | `src/main/java/io/github/carlos_emr/carlos/webserv/rest/to/model/DocumentCategory.java` |
| `NotificationTo1` | `src/main/java/io/github/carlos_emr/carlos/webserv/rest/to/model/NotificationTo1.java` |
| `ProviderSearchResults` | `src/main/java/io/github/carlos_emr/carlos/webserv/rest/to/model/ProviderSearchResults.java` |

### 7. Billing Classes (7 classes) - LOW RISK

Legacy billing data classes and handlers.

| Class | File |
|-------|------|
| `BillingONCHeader2` | `src/main/java/io/github/carlos_emr/carlos/billing/CA/ON/model/BillingONCHeader2.java` |
| `ClinicaidCommunication` | `src/main/java/io/github/carlos_emr/carlos/billing/Clinicaid/util/ClinicaidCommunication.java` |
| `TeleplanMessagesDAO` | `src/main/java/io/github/carlos_emr/carlos/billings/ca/bc/Teleplan/TeleplanMessagesDAO.java` |
| `SupServiceCodeAssocActionForm` | `src/main/java/io/github/carlos_emr/carlos/billings/ca/bc/pageUtil/SupServiceCodeAssocActionForm.java` |
| `MethadoneBillingBCHandler` | `src/main/java/io/github/carlos_emr/carlos/billings/ca/bc/pageUtil/methadonebilling/MethadoneBillingBCHandler.java` |
| `BillingClaimHeader2Data` | `src/main/java/io/github/carlos_emr/carlos/billings/ca/on/data/BillingClaimHeader2Data.java` |
| `BillingStatusData` | `src/main/java/io/github/carlos_emr/carlos/billings/ca/on/data/BillingStatusData.java` |

### 8. Jobs and Schedulers (6 classes) - LOW RISK

Job classes not configured in any scheduler or Spring job context.

| Class | File |
|-------|------|
| `AuditLogPurgeJob` | `src/main/java/io/github/carlos_emr/carlos/admin/job/AuditLogPurgeJob.java` |
| `OutcomesDashboardMetricSenderJob` | `src/main/java/io/github/carlos_emr/carlos/integration/dashboard/OutcomesDashboardMetricSenderJob.java` |
| `OscarMsgReviewSender` | `src/main/java/io/github/carlos_emr/carlos/jobs/OscarMsgReviewSender.java` |
| `OscarOnCallClinic` | `src/main/java/io/github/carlos_emr/carlos/jobs/OscarOnCallClinic.java` |
| `MatchManagerScheduler` | `src/main/java/io/github/carlos_emr/carlos/match/MatchManagerScheduler.java` |
| `AutoTickler` | `src/main/java/io/github/carlos_emr/carlos/tickler/AutoTickler.java` |

### 9. Other Unused Classes (72 classes) - LOW-MEDIUM RISK

| Class | File |
|-------|------|
| `HCValidationImpl1` | `src/main/java/ca/ontario/health/hcv/HCValidationImpl1.java` |
| `BillingDataServlet` | `src/main/java/io/github/carlos_emr/BillingDataServlet.java` |
| `MigrateProfessionalContactsHelper` | `src/main/java/io/github/carlos_emr/carlos/admin/web/MigrateProfessionalContactsHelper.java` |
| `OAuth1Utils` | `src/main/java/io/github/carlos_emr/carlos/app/OAuth1Utils.java` |
| `ARRecordDocumentImpl` | `src/main/java/io/github/carlos_emr/carlos/ar2005/impl/ARRecordDocumentImpl.java` |
| `ARRecordSetDocumentImpl` | `src/main/java/io/github/carlos_emr/carlos/ar2005/impl/ARRecordSetDocumentImpl.java` |
| `OscarMenuExtension` | `src/main/java/io/github/carlos_emr/carlos/caisi/OscarMenuExtension.java` |
| `ProviderAccessRight` | `src/main/java/io/github/carlos_emr/carlos/casemgmt/web/ProviderAccessRight.java` |
| `Dangerous` | `src/main/java/io/github/carlos_emr/carlos/commn/Dangerous.java` |
| `MergedDemographicInterceptor` | `src/main/java/io/github/carlos_emr/carlos/commn/merge/MergedDemographicInterceptor.java` |
| `MsgDemoMapPK` | `src/main/java/io/github/carlos_emr/carlos/commn/model/MsgDemoMapPK.java` |
| `OscarInboxQueryParameters` | `src/main/java/io/github/carlos_emr/carlos/commn/model/inbox/OscarInboxQueryParameters.java` |
| `ContactManager` | `src/main/java/io/github/carlos_emr/carlos/commn/service/ContactManager.java` |
| `LookupTagValue` | `src/main/java/io/github/carlos_emr/carlos/commons/LookupTagValue.java` |
| `ConsultationData` | `src/main/java/io/github/carlos_emr/carlos/consultations/ConsultationData.java` |
| `ManageDashboard2Action` | `src/main/java/io/github/carlos_emr/carlos/dashboard/admin/ManageDashboard2Action.java` |
| `OscarHibernateProperties` | `src/main/java/io/github/carlos_emr/carlos/db/OscarHibernateProperties.java` |
| `TestActionW2Action` | `src/main/java/io/github/carlos_emr/carlos/decisionSupport/web/TestActionW2Action.java` |
| `HRMCreateFile` | `src/main/java/io/github/carlos_emr/carlos/demographic/pageUtil/HRMCreateFile.java` |
| `ExternalEDocConverter` | `src/main/java/io/github/carlos_emr/carlos/documentManager/ExternalEDocConverter.java` |
| `DocumentUpload2Form` | `src/main/java/io/github/carlos_emr/carlos/documentManager/data/DocumentUpload2Form.java` |
| `ConsultationAttachForms` | `src/main/java/io/github/carlos_emr/carlos/encounter/oscarConsultationRequest/pageUtil/ConsultationAttachForms.java` |
| `EctConsultationFormRequestPrintPdf` | `src/main/java/io/github/carlos_emr/carlos/encounter/oscarConsultationRequest/pageUtil/EctConsultationFormRequestPrintPdf.java` |
| `EctValidationParameter` | `src/main/java/io/github/carlos_emr/carlos/encounter/oscarMeasurements/pageUtil/EctValidationParameter.java` |
| `EctDisplayAppointmentHistoryAction` | `src/main/java/io/github/carlos_emr/carlos/encounter/pageUtil/EctDisplayAppointmentHistoryAction.java` |
| `EctDisplayIssuesAction` | `src/main/java/io/github/carlos_emr/carlos/encounter/pageUtil/EctDisplayIssuesAction.java` |
| `EctDisplayPhotosAction` | `src/main/java/io/github/carlos_emr/carlos/encounter/pageUtil/EctDisplayPhotosAction.java` |
| `EctDisplayResolvedIssuesAction` | `src/main/java/io/github/carlos_emr/carlos/encounter/pageUtil/EctDisplayResolvedIssuesAction.java` |
| `Billcenter` | `src/main/java/io/github/carlos_emr/carlos/entities/Billcenter.java` |
| `Billingdetail` | `src/main/java/io/github/carlos_emr/carlos/entities/Billingdetail.java` |
| `Insclaim` | `src/main/java/io/github/carlos_emr/carlos/entities/Insclaim.java` |
| `HRMAction` | `src/main/java/io/github/carlos_emr/carlos/hospitalReportManager/v2018/HRMAction.java` |
| `AuditFormat` | `src/main/java/io/github/carlos_emr/carlos/hospitalReportManager/xsd/AuditFormat.java` |
| `MedicalSurgicalFlag` | `src/main/java/io/github/carlos_emr/carlos/hospitalReportManager/xsd/MedicalSurgicalFlag.java` |
| `PreferredMethodOfContact` | `src/main/java/io/github/carlos_emr/carlos/hospitalReportManager/xsd/PreferredMethodOfContact.java` |
| `DHIRUtils` | `src/main/java/io/github/carlos_emr/carlos/integration/dhir/DHIRUtils.java` |
| `RawXmlLoggingInInterceptor` | `src/main/java/io/github/carlos_emr/carlos/integration/ebs/client/ng/RawXmlLoggingInInterceptor.java` |
| `WSS4JInNonValidatingActionInterceptor` | `src/main/java/io/github/carlos_emr/carlos/integration/ebs/client/ng/WSS4JInNonValidatingActionInterceptor.java` |
| `PatientContact` | `src/main/java/io/github/carlos_emr/carlos/integration/fhir/model/PatientContact.java` |
| `RelatedPerson` | `src/main/java/io/github/carlos_emr/carlos/integration/fhir/model/RelatedPerson.java` |
| `OBECRunner` | `src/main/java/io/github/carlos_emr/carlos/integration/mchcv/OBECRunner.java` |
| `PathNetController` | `src/main/java/io/github/carlos_emr/carlos/lab/ca/bc/PathNet/PathNetController.java` |
| `PathNetInfo` | `src/main/java/io/github/carlos_emr/carlos/lab/ca/bc/PathNet/PathNetInfo.java` |
| `LabResultImport` | `src/main/java/io/github/carlos_emr/carlos/lab/ca/on/LabResultImport.java` |
| `AuthResult` | `src/main/java/io/github/carlos_emr/carlos/login/AuthResult.java` |
| `GenericOAuth10aApi` | `src/main/java/io/github/carlos_emr/carlos/login/GenericOAuth10aApi.java` |
| `ValidateMFA2Action` | `src/main/java/io/github/carlos_emr/carlos/login/ValidateMFA2Action.java` |
| `WaitListManager` | `src/main/java/io/github/carlos_emr/carlos/managers/WaitListManager.java` |
| `MsgCreateMessageBean` | `src/main/java/io/github/carlos_emr/carlos/messenger/pageUtil/MsgCreateMessageBean.java` |
| `DefaultCustomFilter` | `src/main/java/io/github/carlos_emr/carlos/model/DefaultCustomFilter.java` |
| `RxAllergyData` | `src/main/java/io/github/carlos_emr/carlos/prescript/data/RxAllergyData.java` |
| `ShowAllSorter` | `src/main/java/io/github/carlos_emr/carlos/prescript/util/ShowAllSorter.java` |
| `TimingOutCallback` | `src/main/java/io/github/carlos_emr/carlos/prescript/util/TimingOutCallback.java` |
| `RptDrugRecord` | `src/main/java/io/github/carlos_emr/carlos/report/pageUtil/RptDrugRecord.java` |
| `CookieSecurity` | `src/main/java/io/github/carlos_emr/carlos/sec/CookieSecurity.java` |
| `StJoesTokenManager` | `src/main/java/io/github/carlos_emr/carlos/sec/token/StJoesTokenManager.java` |
| `ProviderManagerTickler` | `src/main/java/io/github/carlos_emr/carlos/services/ProviderManagerTickler.java` |
| `TicklerService` | `src/main/java/io/github/carlos_emr/carlos/ticklers/service/TicklerService.java` |
| `FileHolder` | `src/main/java/io/github/carlos_emr/carlos/util/FileHolder.java` |
| `PagerDef` | `src/main/java/io/github/carlos_emr/carlos/util/PagerDef.java` |
| `OscarDbPropertiesListener` | `src/main/java/io/github/carlos_emr/carlos/util/plugin/OscarDbPropertiesListener.java` |
| `EnumNameComparator` | `src/main/java/io/github/carlos_emr/carlos/utility/EnumNameComparator.java` |
| `HinValidator` | `src/main/java/io/github/carlos_emr/carlos/utility/HinValidator.java` |
| `SpringPropertyConfigurer` | `src/main/java/io/github/carlos_emr/carlos/utility/SpringPropertyConfigurer.java` |
| `Cds4FunctionCode` | `src/main/java/io/github/carlos_emr/carlos/web/Cds4FunctionCode.java` |
| `CdsManualLineEntry` | `src/main/java/io/github/carlos_emr/carlos/web/CdsManualLineEntry.java` |
| `WaitListService_Service` | `src/main/java/io/github/carlos_emr/carlos/wl/WaitListService_Service.java` |
| `AbstractPreparedTickler` | `src/main/java/io/github/carlos_emr/carlos/wl/prepared/runtime/AbstractPreparedTickler.java` |
| `ConsultationsConfigBean` | `src/main/java/io/github/carlos_emr/carlos/wl/prepared/seaton/consultation/ConsultationsConfigBean.java` |
| `NotifyConsultationBean` | `src/main/java/io/github/carlos_emr/carlos/wl/prepared/seaton/consultation/NotifyConsultationBean.java` |
| `ProcessConsultationBean` | `src/main/java/io/github/carlos_emr/carlos/wl/prepared/seaton/consultation/ProcessConsultationBean.java` |
| `UserSearchFormBean` | `src/main/java/io/github/carlos_emr/carlos/www/admin/UserSearchFormBean.java` |

---

## Recommended Removal Order

For safe, incremental removal:

1. **Phase 1 - Lowest risk** (64 classes): Form Records + Encounter Data Records
   - Self-contained data classes with no dependencies
   - Zero risk of runtime impact

2. **Phase 2 - Low risk** (37 classes): CAISI Integrator + PMmodule + REST DTOs
   - Isolated subsystem classes
   - No framework wiring

3. **Phase 3 - Low risk** (13 classes): Billing + Jobs/Schedulers
   - Unconfigured jobs, legacy billing data classes

4. **Phase 4 - Medium risk** (24 classes): DAO Implementations
   - Remove both `*DaoImpl` and their corresponding interfaces
   - Spring will no longer instantiate these beans
   - Build after each removal to catch compile errors

5. **Phase 5 - Remaining** (70 classes): Other utilities, actions, helpers
   - Remove in small batches, build-test after each batch

---

## Verification Checklist

Before removing any class, verify:

- [ ] No references in Java source files (excluding self and tests)
- [ ] No references in Struts XML configuration
- [ ] No references in Spring XML configuration (applicationContext*.xml, spring_ws.xml)
- [ ] No references in Hibernate mapping files (*.hbm.xml)
- [ ] No references in JSP/JSPF files
- [ ] No `@Component`/`@Service`/`@Repository` with active consumers
- [ ] No reflection-based loading (Class.forName, string-based bean lookup)
- [ ] No AOP pointcut interception
- [ ] No Spring event listener registration
- [ ] Build succeeds after removal (`make install`)

Generated with Claude Code

https://claude.ai/code/session_01WqpqNAcrKKYk48no5CuCit
