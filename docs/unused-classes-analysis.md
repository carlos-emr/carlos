# CARLOS EMR - Unused Classes Analysis

**Date**: 2026-02-16
**Branch**: `claude/identify-unused-classes-Zxx1H`
**Total Unused Classes Found**: 232
**Total Classes in Codebase**: 4,068
**Percentage Unused**: ~5.7%

## Methodology

Classes were identified as unused by checking whether their simple class name appears
in **any other file** across the entire `src/main/` directory tree, including:

- Java source files (`.java`)
- XML configuration files (`.xml`) - Spring, Struts, Hibernate, web.xml
- JSP pages (`.jsp`)
- Properties files (`.properties`)
- TLD files (`.tld`)

**False-positive filtering applied**:
- `*Impl` classes were checked against their interface counterpart; if the interface
  is referenced elsewhere (indicating the Impl is used via Spring DI), they were
  excluded from this report.
- `package-info.java` files were excluded (metadata-only).

**Limitations**:
- Reflection-based usage (`Class.forName("...")`) is not detected by this analysis.
- CXF/JAXB auto-discovery via annotations (`@WebService`, `@XmlType`) may use classes
  without explicit Java references.
- Hibernate HBM XML mappings with `<class>` elements may reference classes indirectly.
- Some classes may be entry points invoked via URL patterns, scheduled jobs config,
  or external systems.
- Short/generic class names (e.g., `DAO`, `Pager`) may produce false negatives in
  substring-based search; these were caught via secondary fully-qualified name checks.

---

## Summary by Category

| Category | Count | Risk Level | Notes |
|----------|-------|------------|-------|
| Form Records (`form/`) | 52 | Low | Legacy form data holders, likely replaced by eforms |
| Encounter Data Records | 10 | Low | Legacy encounter record classes |
| DAO Implementations (`commn/dao/`) | 26 | Medium | Interfaces unused too; safe to remove |
| Web Services (`webserv/`) | 26 | Medium | REST/SOAP endpoints and DTOs |
| CAISI Integrator | 12 | Low | Related to archived CAISI integration |
| PMmodule | 13 | Low | Legacy program management classes |
| Billing | 9 | Medium | Province-specific billing code |
| Integration | 7 | Medium | External system integrations |
| Hospital Report Manager | 4 | Low | HRM-related unused classes |
| Utility/Infrastructure | 13 | Low | Utility classes, config, helpers |
| Login/Security | 5 | Medium | Auth-related unused code |
| Other (misc) | 55 | Varies | Various domain-specific unused classes |

---

## Detailed Findings

### 1. Form Record Classes (52 classes) - LOW RISK

These are legacy form data record holder classes in the `form/` package. They appear
to have been superseded by the eforms system and newer form implementations.

**Recommendation**: Safe to remove. These are simple data record classes with no
external dependencies.

```
src/main/java/io/github/carlos_emr/carlos/form/Frm2MinWalkRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmARRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmAdfRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmAdfV2Record.java
src/main/java/io/github/carlos_emr/carlos/form/FrmAnnualRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmAnnualV2Record.java
src/main/java/io/github/carlos_emr/carlos/form/FrmBCAR2007Record.java
src/main/java/io/github/carlos_emr/carlos/form/FrmBCAR2012Record.java
src/main/java/io/github/carlos_emr/carlos/form/FrmBCARRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmBCBirthSumMo2008Record.java
src/main/java/io/github/carlos_emr/carlos/form/FrmBCBrithSumMoRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmBCClientChartChecklistRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmBCHPRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmBCNewBorn2008Record.java
src/main/java/io/github/carlos_emr/carlos/form/FrmBCNewBornRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmCESDRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmCaregiverRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmCostQuestionnaireRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmCounselingRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmCounsellorAssessmentRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmDischargeSummaryRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmFallsRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmGripStrengthRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmGrowth0_36Record.java
src/main/java/io/github/carlos_emr/carlos/form/FrmGrowthChartRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmHomeFallsRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmImmunAllergyRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmIntakeHxRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmIntakeInfoRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmInternetAccessRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmInvoiceRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmLateLifeFDIDisabilityRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmLateLifeFDIFunctionRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmMMSERecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmONARRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmOvulationRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmPalliativeCareRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmPeriMenopausalRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmPolicyRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmPositionHazardRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmReceptionAssessmentRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmRhImmuneGlobulinRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmSF36CaregiverRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmSF36Record.java
src/main/java/io/github/carlos_emr/carlos/form/FrmSatisfactionScaleRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmSelfAdministeredRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmSelfAssessmentRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmSelfEfficacyRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmSelfManagementRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmTreatmentPrefRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmType2DiabeteRecord.java
src/main/java/io/github/carlos_emr/carlos/form/FrmchfRecord.java
src/main/java/io/github/carlos_emr/carlos/form/graphic/FrmPdfGraphicRourke.java
src/main/java/io/github/carlos_emr/carlos/form/model/FormBooleanValuePK.java
```

### 2. Encounter Data Records (10 classes) - LOW RISK

Legacy encounter record classes that are no longer referenced.

```
src/main/java/io/github/carlos_emr/carlos/encounter/data/EctARRecord.java
src/main/java/io/github/carlos_emr/carlos/encounter/data/EctAlphaRecord.java
src/main/java/io/github/carlos_emr/carlos/encounter/data/EctAnnualRecord.java
src/main/java/io/github/carlos_emr/carlos/encounter/data/EctMMSERecord.java
src/main/java/io/github/carlos_emr/carlos/encounter/data/EctMentalHealthRecord.java
src/main/java/io/github/carlos_emr/carlos/encounter/data/EctPalliativeCareRecord.java
src/main/java/io/github/carlos_emr/carlos/encounter/data/EctPeriMenopausalRecord.java
src/main/java/io/github/carlos_emr/carlos/encounter/data/EctProgram_improved.java
src/main/java/io/github/carlos_emr/carlos/encounter/data/EctRourkeRecord.java
src/main/java/io/github/carlos_emr/carlos/encounter/data/EctType2DiabetesRecord.java
```

### 3. DAO Implementations (26 classes) - MEDIUM RISK

These `DaoImpl` classes implement DAO interfaces that are **also unused** (the
interface itself has no references outside its own file and the Impl file). This
means the entire DAO+Impl pair is dead code.

**Recommendation**: Remove in pairs (interface + implementation). Verify no Spring XML
bean definitions reference them before deletion.

```
src/main/java/io/github/carlos_emr/carlos/commn/dao/BatchBillingDaoImpl.java
src/main/java/io/github/carlos_emr/carlos/commn/dao/CSSStylesDaoImpl.java
src/main/java/io/github/carlos_emr/carlos/commn/dao/CaisiFormDaoImpl.java
src/main/java/io/github/carlos_emr/carlos/commn/dao/CaisiFormDataDaoImpl.java
src/main/java/io/github/carlos_emr/carlos/commn/dao/CaisiFormDataTmpSaveDaoImpl.java
src/main/java/io/github/carlos_emr/carlos/commn/dao/CaisiFormInstanceDaoImpl.java
src/main/java/io/github/carlos_emr/carlos/commn/dao/CaisiFormInstanceTmpSaveDaoImpl.java
src/main/java/io/github/carlos_emr/carlos/commn/dao/CaisiFormQuestionDaoImpl.java
src/main/java/io/github/carlos_emr/carlos/commn/dao/ConsultationRequestMergedDemographicDaoImpl.java
src/main/java/io/github/carlos_emr/carlos/commn/dao/DocumentResultsMergedDemographicDaoImpl.java
src/main/java/io/github/carlos_emr/carlos/commn/dao/DrugMergedDemographicDaoImpl.java
src/main/java/io/github/carlos_emr/carlos/commn/dao/DxAssociationDaoImpl.java
src/main/java/io/github/carlos_emr/carlos/commn/dao/GroupNoteLinkDaoImpl.java
src/main/java/io/github/carlos_emr/carlos/commn/dao/IntegratorConsentComplexExitInterviewDaoImpl.java
src/main/java/io/github/carlos_emr/carlos/commn/dao/MdsZCLDaoImpl.java
src/main/java/io/github/carlos_emr/carlos/commn/dao/MdsZCTDaoImpl.java
src/main/java/io/github/carlos_emr/carlos/commn/dao/MdsZFRDaoImpl.java
src/main/java/io/github/carlos_emr/carlos/commn/dao/OscarAnnotationDaoImpl.java
src/main/java/io/github/carlos_emr/carlos/commn/dao/OscarCodeDaoImpl.java
src/main/java/io/github/carlos_emr/carlos/commn/dao/OscarMsgTypeDaoImpl.java
src/main/java/io/github/carlos_emr/carlos/commn/dao/PrescribeDaoImpl.java
src/main/java/io/github/carlos_emr/carlos/commn/dao/PreventionMergedDemographicDaoImpl.java
src/main/java/io/github/carlos_emr/carlos/commn/dao/ProgramAccessRolesDaoImpl.java
src/main/java/io/github/carlos_emr/carlos/commn/dao/ReadLabDaoImpl.java
src/main/java/io/github/carlos_emr/carlos/commn/dao/RecycleBinBillingDaoImpl.java
src/main/java/io/github/carlos_emr/carlos/commn/dao/RemoteDataLogDaoImpl.java
```

### 4. Web Services and REST DTOs (26 classes) - MEDIUM RISK

Unused REST endpoints, SOAP web services, data transfer objects, and conversion utilities.

**Note**: Some of these may be auto-discovered by CXF via `@WebService` annotations.
Verify CXF configuration before removing SOAP service classes.

```
src/main/java/io/github/carlos_emr/carlos/webserv/AllergyWs.java
src/main/java/io/github/carlos_emr/carlos/webserv/BookingWs.java
src/main/java/io/github/carlos_emr/carlos/webserv/DocumentWs.java
src/main/java/io/github/carlos_emr/carlos/webserv/MeasurementWs.java
src/main/java/io/github/carlos_emr/carlos/webserv/PrescriptionWs.java
src/main/java/io/github/carlos_emr/carlos/webserv/PreventionWs.java
src/main/java/io/github/carlos_emr/carlos/webserv/rest/conversion/ProviderSettingsConverter.java
src/main/java/io/github/carlos_emr/carlos/webserv/rest/conversion/QuickLinkConverter.java
src/main/java/io/github/carlos_emr/carlos/webserv/rest/conversion/summary/AllergiesSummary.java
src/main/java/io/github/carlos_emr/carlos/webserv/rest/conversion/summary/DecisionSupportSummary.java
src/main/java/io/github/carlos_emr/carlos/webserv/rest/conversion/summary/FormsSummary.java
src/main/java/io/github/carlos_emr/carlos/webserv/rest/conversion/summary/LabsDocsSummary.java
src/main/java/io/github/carlos_emr/carlos/webserv/rest/conversion/summary/OngoingConcernDxRegSummary.java
src/main/java/io/github/carlos_emr/carlos/webserv/rest/conversion/summary/PreventionsSummary.java
src/main/java/io/github/carlos_emr/carlos/webserv/rest/conversion/summary/RxSummary.java
src/main/java/io/github/carlos_emr/carlos/webserv/rest/to/DemographicMergeResponse.java
src/main/java/io/github/carlos_emr/carlos/webserv/rest/to/DrugDSResponse.java
src/main/java/io/github/carlos_emr/carlos/webserv/rest/to/PharmacyResponse.java
src/main/java/io/github/carlos_emr/carlos/webserv/rest/to/PrimitiveResponseWrapper.java
src/main/java/io/github/carlos_emr/carlos/webserv/rest/to/ProgramDomainResponse.java
src/main/java/io/github/carlos_emr/carlos/webserv/rest/to/model/DemographicContactTo1.java
src/main/java/io/github/carlos_emr/carlos/webserv/rest/to/model/DemographicSearchResults.java
src/main/java/io/github/carlos_emr/carlos/webserv/rest/to/model/DocumentCategory.java
src/main/java/io/github/carlos_emr/carlos/webserv/rest/to/model/NotificationTo1.java
src/main/java/io/github/carlos_emr/carlos/webserv/rest/to/model/ProviderSearchResults.java
src/main/java/io/github/carlos_emr/carlos/webserv/rest/util/WebServiceLoggingAdvice.java
```

### 5. CAISI Integrator (12 classes) - LOW RISK

Related to the archived CAISI integration system. Per CLAUDE.md, CAISI integrator
architecture is being phased out.

```
src/main/java/io/github/carlos_emr/carlos/PMmodule/caisi_integrator/ByteWrapper.java
src/main/java/io/github/carlos_emr/carlos/PMmodule/caisi_integrator/IntegratorLocalStoreUpdateJob.java
src/main/java/io/github/carlos_emr/carlos/caisi/CaisiUtil.java
src/main/java/io/github/carlos_emr/carlos/caisi/OscarMenuExtension.java
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/dao/CachedDemographicImage.java
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/dao/EventLog.java
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/dao/HomelessPopulationReport.java
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/dao/MatchingCachedDemographicScore.java
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/ws/DemographicWs_DemographicWsPort_Client.java
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/ws/FacilityWs_FacilityWsPort_Client.java
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/ws/HnrWs_HnrWsPort_Client.java
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/ws/ProgramWs_ProgramWsPort_Client.java
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/ws/ProviderWs_ProviderWsPort_Client.java
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/ws/ReferralWs_ReferralWsPort_Client.java
```

### 6. PMmodule (13 classes) - LOW RISK

Legacy program management classes including old web actions, form beans, and
service classes.

```
src/main/java/io/github/carlos_emr/carlos/PMmodule/service/IntakeManager.java
src/main/java/io/github/carlos_emr/carlos/PMmodule/utility/MigrateStaffAssignments.java
src/main/java/io/github/carlos_emr/carlos/PMmodule/web/CdsForm4Action.java
src/main/java/io/github/carlos_emr/carlos/PMmodule/web/ClientManagerAction.java
src/main/java/io/github/carlos_emr/carlos/PMmodule/web/ManageConsentAction.java
src/main/java/io/github/carlos_emr/carlos/PMmodule/web/ManageHnrClientAction.java
src/main/java/io/github/carlos_emr/carlos/PMmodule/web/PopulationReportUIBean.java
src/main/java/io/github/carlos_emr/carlos/PMmodule/web/RatingView.java
src/main/java/io/github/carlos_emr/carlos/PMmodule/web/formbean/DefaultRoleAccessFormBean.java
src/main/java/io/github/carlos_emr/carlos/PMmodule/web/forms/IntegratorPausedException.java
src/main/java/io/github/carlos_emr/carlos/PMmodule/web/reports/custom/CustomReportDataSource.java
```

### 7. Billing (9 classes) - MEDIUM RISK

Province-specific billing classes and third-party integration code.

**Note**: Billing code is critical healthcare infrastructure. Extra care recommended.

```
src/main/java/io/github/carlos_emr/carlos/billing/CA/ON/model/BillingONCHeader2.java
src/main/java/io/github/carlos_emr/carlos/billing/Clinicaid/util/ClinicaidCommunication.java
src/main/java/io/github/carlos_emr/carlos/billings/ca/bc/Teleplan/TeleplanMessagesDAO.java
src/main/java/io/github/carlos_emr/carlos/billings/ca/bc/pageUtil/SupServiceCodeAssocActionForm.java
src/main/java/io/github/carlos_emr/carlos/billings/ca/bc/pageUtil/methadonebilling/MethadoneBillingBCHandler.java
src/main/java/io/github/carlos_emr/carlos/billings/ca/on/data/BillingClaimHeader2Data.java
src/main/java/io/github/carlos_emr/carlos/billings/ca/on/data/BillingStatusData.java
src/main/java/io/github/carlos_emr/BillingDataServlet.java
src/main/java/io/github/carlos_emr/carlos/entities/Billcenter.java
src/main/java/io/github/carlos_emr/carlos/entities/Billingdetail.java
src/main/java/io/github/carlos_emr/carlos/entities/Insclaim.java
```

### 8. Integration (7 classes) - MEDIUM RISK

External system integration classes including DHIR, EBS, FHIR, and OBEC.

```
src/main/java/io/github/carlos_emr/carlos/integration/dashboard/OutcomesDashboardMetricSenderJob.java
src/main/java/io/github/carlos_emr/carlos/integration/dhir/DHIRUtils.java
src/main/java/io/github/carlos_emr/carlos/integration/ebs/client/ng/RawXmlLoggingInInterceptor.java
src/main/java/io/github/carlos_emr/carlos/integration/ebs/client/ng/WSS4JInNonValidatingActionInterceptor.java
src/main/java/io/github/carlos_emr/carlos/integration/fhir/model/PatientContact.java
src/main/java/io/github/carlos_emr/carlos/integration/fhir/model/RelatedPerson.java
src/main/java/io/github/carlos_emr/carlos/integration/mchcv/OBECRunner.java
```

### 9. Hospital Report Manager (4 classes) - LOW RISK

```
src/main/java/io/github/carlos_emr/carlos/hospitalReportManager/v2018/HRMAction.java
src/main/java/io/github/carlos_emr/carlos/hospitalReportManager/xsd/AuditFormat.java
src/main/java/io/github/carlos_emr/carlos/hospitalReportManager/xsd/MedicalSurgicalFlag.java
src/main/java/io/github/carlos_emr/carlos/hospitalReportManager/xsd/PreferredMethodOfContact.java
```

### 10. Utility and Infrastructure (11 classes) - LOW RISK

```
src/main/java/io/github/carlos_emr/carlos/utility/EnumNameComparator.java
src/main/java/io/github/carlos_emr/carlos/utility/HinValidator.java
src/main/java/io/github/carlos_emr/carlos/utility/SpringPropertyConfigurer.java
src/main/java/io/github/carlos_emr/carlos/util/DAO.java
src/main/java/io/github/carlos_emr/carlos/util/FileHolder.java
src/main/java/io/github/carlos_emr/carlos/util/Pager.java
src/main/java/io/github/carlos_emr/carlos/util/PagerDef.java
src/main/java/io/github/carlos_emr/carlos/util/plugin/OscarDbPropertiesListener.java
src/main/java/io/github/carlos_emr/carlos/commons/LookupTagValue.java
src/main/java/io/github/carlos_emr/carlos/db/OscarHibernateProperties.java
src/main/java/io/github/carlos_emr/carlos/commn/Dangerous.java
src/main/java/io/github/carlos_emr/carlos/commn/merge/MergedDemographicInterceptor.java
src/main/java/io/github/carlos_emr/carlos/commn/service/ContactManager.java
```

### 11. Login and Security (5 classes) - MEDIUM RISK

**Note**: Security-related code changes should be carefully reviewed.

```
src/main/java/io/github/carlos_emr/carlos/app/OAuth1Utils.java
src/main/java/io/github/carlos_emr/carlos/login/AuthResult.java
src/main/java/io/github/carlos_emr/carlos/login/GenericOAuth10aApi.java
src/main/java/io/github/carlos_emr/carlos/login/ValidateMFA2Action.java
src/main/java/io/github/carlos_emr/carlos/sec/CookieSecurity.java
src/main/java/io/github/carlos_emr/carlos/sec/token/StJoesTokenManager.java
```

### 12. Encounter Actions and Utils (7 classes) - LOW RISK

```
src/main/java/io/github/carlos_emr/carlos/encounter/oscarConsultationRequest/pageUtil/ConsultationAttachForms.java
src/main/java/io/github/carlos_emr/carlos/encounter/oscarConsultationRequest/pageUtil/EctConsultationFormRequestPrintPdf.java
src/main/java/io/github/carlos_emr/carlos/encounter/oscarMeasurements/pageUtil/EctValidationParameter.java
src/main/java/io/github/carlos_emr/carlos/encounter/pageUtil/EctDisplayAppointmentHistoryAction.java
src/main/java/io/github/carlos_emr/carlos/encounter/pageUtil/EctDisplayIssuesAction.java
src/main/java/io/github/carlos_emr/carlos/encounter/pageUtil/EctDisplayPhotosAction.java
src/main/java/io/github/carlos_emr/carlos/encounter/pageUtil/EctDisplayResolvedIssuesAction.java
```

### 13. Ontario Health (2 classes) - LOW RISK

Legacy Ontario health integration implementations. The `HCValidation` interface is
still used, but `HCValidationImpl1` specifically is never referenced.

```
src/main/java/ca/ontario/health/hcv/HCValidationImpl1.java
src/main/java/ca/ontario/health/edt/EDTDelegateImpl.java
```

**Note on `EDTDelegateImpl`**: The `EDTDelegate` interface is referenced by MCEDT
integration code. However `EDTDelegateImpl` itself is never instantiated - check
if `DelegateFactory` creates it via reflection before removing.

### 14. Remaining Miscellaneous (26 classes)

```
src/main/java/io/github/carlos_emr/carlos/ar2005/impl/ARRecordDocumentImpl.java
src/main/java/io/github/carlos_emr/carlos/ar2005/impl/ARRecordSetDocumentImpl.java
src/main/java/io/github/carlos_emr/carlos/casemgmt/service/impl/DefaultNoteService.java
src/main/java/io/github/carlos_emr/carlos/casemgmt/web/ProviderAccessRight.java
src/main/java/io/github/carlos_emr/carlos/commn/model/MsgDemoMapPK.java
src/main/java/io/github/carlos_emr/carlos/commn/model/inbox/OscarInboxQueryParameters.java
src/main/java/io/github/carlos_emr/carlos/consultations/ConsultationData.java
src/main/java/io/github/carlos_emr/carlos/dashboard/admin/ManageDashboard2Action.java
src/main/java/io/github/carlos_emr/carlos/decisionSupport/web/TestActionW2Action.java
src/main/java/io/github/carlos_emr/carlos/demographic/pageUtil/HRMCreateFile.java
src/main/java/io/github/carlos_emr/carlos/documentManager/ExternalEDocConverter.java
src/main/java/io/github/carlos_emr/carlos/documentManager/data/DocumentUpload2Form.java
src/main/java/io/github/carlos_emr/carlos/jobs/OscarMsgReviewSender.java
src/main/java/io/github/carlos_emr/carlos/jobs/OscarOnCallClinic.java
src/main/java/io/github/carlos_emr/carlos/lab/ca/bc/PathNet/PathNetController.java
src/main/java/io/github/carlos_emr/carlos/lab/ca/bc/PathNet/PathNetInfo.java
src/main/java/io/github/carlos_emr/carlos/lab/ca/on/LabResultImport.java
src/main/java/io/github/carlos_emr/carlos/listeners/MyDemographicEventListener.java
src/main/java/io/github/carlos_emr/carlos/managers/DrugLookUpManager.java
src/main/java/io/github/carlos_emr/carlos/managers/WaitListManager.java
src/main/java/io/github/carlos_emr/carlos/match/MatchManagerScheduler.java
src/main/java/io/github/carlos_emr/carlos/messenger/pageUtil/MsgCreateMessageBean.java
src/main/java/io/github/carlos_emr/carlos/model/DefaultCustomFilter.java
src/main/java/io/github/carlos_emr/carlos/prescript/data/RxAllergyData.java
src/main/java/io/github/carlos_emr/carlos/prescript/util/ShowAllSorter.java
src/main/java/io/github/carlos_emr/carlos/prescript/util/TimingOutCallback.java
src/main/java/io/github/carlos_emr/carlos/report/data/VisitReportData.java
src/main/java/io/github/carlos_emr/carlos/report/pageUtil/RptDrugRecord.java
src/main/java/io/github/carlos_emr/carlos/services/ProviderManagerTickler.java
src/main/java/io/github/carlos_emr/carlos/tickler/AutoTickler.java
src/main/java/io/github/carlos_emr/carlos/ticklers/service/TicklerService.java
src/main/java/io/github/carlos_emr/carlos/web/AppointmentProviderAdminDayUIBean.java
src/main/java/io/github/carlos_emr/carlos/web/Cds4FunctionCode.java
src/main/java/io/github/carlos_emr/carlos/web/CdsManualLineEntry.java
src/main/java/io/github/carlos_emr/carlos/wl/WaitListService_Service.java
src/main/java/io/github/carlos_emr/carlos/wl/prepared/runtime/AbstractPreparedTickler.java
src/main/java/io/github/carlos_emr/carlos/wl/prepared/seaton/consultation/ConsultationsConfigBean.java
src/main/java/io/github/carlos_emr/carlos/wl/prepared/seaton/consultation/NotifyConsultationBean.java
src/main/java/io/github/carlos_emr/carlos/wl/prepared/seaton/consultation/ProcessConsultationBean.java
src/main/java/io/github/carlos_emr/carlos/www/admin/UserSearchFormBean.java
```

---

## Recommended Removal Priority

### Phase 1: Safe to Remove Immediately (LOW RISK - 75 classes)
- All 52 `Frm*Record` form classes
- All 10 `Ect*Record` encounter data classes
- `Dangerous.java` (annotation marker, unused)
- `LookupTagValue.java`
- `FileHolder.java`, `PagerDef.java`
- `OscarMenuExtension.java`, `CaisiUtil.java`
- `ByteWrapper.java`
- `EnumNameComparator.java`
- `UserSearchFormBean.java`
- `FormBooleanValuePK.java`
- `FrmPdfGraphicRourke.java`

### Phase 2: Remove After Verification (MEDIUM RISK - 100+ classes)
- 26 DAO implementations (verify interfaces are also unused)
- 26 web service classes (verify no CXF auto-discovery)
- CAISI integrator classes
- PMmodule legacy classes
- Billing-related unused classes

### Phase 3: Requires Careful Review (HIGHER RISK - ~50 classes)
- Login/security classes (`OAuth1Utils`, `ValidateMFA2Action`, `CookieSecurity`)
- Integration classes (EBS, FHIR, DHIR)
- Billing province-specific code
- `EDTDelegateImpl` (check `DelegateFactory` for reflection usage)

---

## Notes for Reviewers

1. **Reflection Usage**: Some classes may be loaded via `Class.forName()` or Spring
   property-based class names. Search for string literals matching class names before
   removing any class marked as "Higher Risk".

2. **JAXB/CXF Auto-discovery**: Classes annotated with `@XmlType`, `@WebService`, or
   registered in CXF endpoint configurations may be used without explicit Java imports.

3. **Scheduled Jobs**: Classes implementing `OscarRunnable` or `Runnable` may be
   configured in the database `schedule_template_code` table rather than in XML.

4. **Test References**: This analysis only checked `src/main/`. Some classes may be
   referenced in test code (`src/test/`, `src/test-modern/`). Removing unused production
   classes is still valid even if tests reference them - the tests should be removed too.

Generated with Claude Code
