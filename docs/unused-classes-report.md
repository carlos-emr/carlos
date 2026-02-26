# Unused Classes Report - CARLOS EMR

**Date**: 2026-02-16
**Scope**: All Java source files under `src/main/java/`
**Verified unused count**: 149 classes

## Methodology

Classes were identified as unused through a comprehensive multi-layered analysis:

### Layer 1: Java Source Cross-Reference
Each public/package-private class was checked for import statements and type references from other `.java` files (excluding self-references and test files).

### Layer 2: Framework Configuration Files
- **Struts XML**: `struts.xml`, `struts-config.xml` for action/form mappings
- **Spring XML**: All `applicationContext*.xml`, `spring_ws.xml`, `spring_managers.xml`, `spring_jpa.xml`, `cxf.xml`
- **Hibernate/JPA**: All `*.hbm.xml`, `persistence.xml`, `ehcache.xml`, `OscarDatabaseBase.xml`
- **Web deployment**: `web.xml` for servlets, filters, listeners
- **All XML files**: 196 XML files searched with FQCN variants (new, old, oscar.* namespaces)

### Layer 3: Non-Java File References
- **JSP/JSPF**: 1,364 files searched for scriptlet imports and useBean directives
- **JavaScript**: 453 `.js` and `.Js.jsp` files
- **Properties/Config**: 34 `.properties` files, all `.txt` config files
- **JSON/YAML**: 61 `.json` files, all `.yml`/`.yaml` files
- **SQL**: 11,000+ `.sql` files for database-driven class loading references
- **Other**: `.html`, `.css`, `.tld`, `.tag`, `.wsdl`, `.xsd`, `.xsl`, `.sh` files

### Layer 4: Dynamic Loading Patterns
- `Class.forName()` across all Java source (found `FrmRecordFactory`, `FrmGraphicFactory`, `OscarJobUtils`)
- `SpringUtils.getBean()` with string arguments and type-based injection
- `request.getAttribute/setAttribute`, `session.getAttribute/setAttribute`
- URL-parameter-driven factory patterns (`Frm` + `which` + `Record`)
- Database-driven class loading (`OscarJobType` table via `OscarJobUtils`)
- Config-file-driven class loading (`__className` property in `.txt` files)

### Layer 5: Spring Component Scan + Annotation Analysis
- Cross-referenced `@Component`/`@Service`/`@Repository` annotations with `component-scan` base packages
- For each scanned class, traced whether its interface has any active consumers
- Checked inheritance chains (e.g., `MergedDemographic*DaoImpl` extending parent DAOs that are the sole `@Repository` bean)
- Checked `@Entity`, `@Embeddable`, `@MappedSuperclass` against active persistence contexts
- Checked `@XmlRootElement`/`@XmlType` against active JAXB contexts

### Layer 6: Runtime Lifecycle Annotations
Checked all 230 candidate classes for 30+ annotation patterns:
`@PostConstruct`, `@PreDestroy`, `@Scheduled`, `@Async`, `@EventListener`, `@TransactionalEventListener`, `@Aspect`, `@Around`/`@Before`/`@After`, `@Controller`/`@RestController`, `@RequestMapping`, `@WebServlet`, `@WebFilter`, `@WebListener`, `@ServerEndpoint`, `@EntityListeners`, `@Interceptor`, `@ManagedBean`, `@Singleton`, `@Startup`, `@MessageDriven`, `@Provider`, plus `implements ApplicationListener`, `InitializingBean`, `DisposableBean`, `BeanPostProcessor`, `BeanFactoryPostProcessor`, `HandlerInterceptor`, `Filter`

---

## False Positives Caught (81 classes rescued across 3 rounds)

### Round 1: Framework Configuration (22 classes)

| Class | Detection Method |
|-------|-----------------|
| `AppointmentProviderAdminDayUIBean` | JSP scriptlet import in 2 files |
| `CaisiUtil` | JSP scriptlet import (`infirmaryviewprogramlist.jspf`) |
| `VisitReportData` | JSP scriptlet import (`oscarReportVisit_vr.jspf`) |
| `BatchBillingDaoImpl` | `@Repository` - interface consumed by `BatchBill2Action` |
| `CSSStylesDaoImpl` | `@Repository` - interface consumed by `ManageCSS2Action` |
| `DefaultNoteService` | `@Component` - sole impl of `NoteService` (4 consumers) |
| `DrugLookUpManager` | `@Service` - sole impl of `DrugLookUp` (3 consumers) |
| `MyDemographicEventListener` | `@Component` implementing `ApplicationListener` for demographic events |
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

### Round 2: Dynamic Loading Patterns (55 classes)

**52 `Frm*Record` classes** - loaded via `FrmRecordFactory.java:41-45`:
```java
String fullName = "io.github.carlos_emr.carlos.form.Frm" + which + "Record";
Class classDefinition = Class.forName(fullName);
```
The `which` parameter comes from URL/form parameters, making all `Frm*Record` classes potentially reachable at runtime.

| Class | Dynamic Loading Mechanism |
|-------|--------------------------|
| `Frm2MinWalkRecord` through `FrmchfRecord` (52 classes) | `FrmRecordFactory` URL-parameter-driven `Class.forName()` |
| `FrmPdfGraphicRourke` | Config-driven `Class.forName()` via `__className` in 16 GrowthChart `.txt` files, loaded by `FrmGraphicFactory.create()` |
| `OscarMsgReviewSender` | Database-driven `Class.forName()` via `OscarJobType` table, loaded by `OscarJobUtils.java:122` |
| `OscarOnCallClinic` | Database-driven `Class.forName()` via `OscarJobType` table, loaded by `OscarJobUtils.java:122` |

### Round 3: Annotation-Based Inheritance (4 classes)

These `*MergedDemographicDaoImpl` classes extend parent DAOs that have NO `@Repository` annotation. The child class is the **sole Spring bean** providing the parent's DAO interface, making them active at runtime via `SpringUtils.getBean(XxxDao.class)`.

| Class | `@Repository` Name | Interface Provided | Active Consumers |
|-------|--------------------|--------------------|-----------------|
| `ConsultationRequestMergedDemographicDaoImpl` | `consultationRequestDao` | `ConsultationRequestDao` | `EFormUtil`, `ConsultationAttach`, `EctConsultationFormRequest2Action` |
| `DocumentResultsMergedDemographicDaoImpl` | `documentResultsDao` | `DocumentResultsDao` | `CommonLabResultData`, `InboxManagerImpl` |
| `DrugMergedDemographicDaoImpl` | `drugDao` | `DrugDao` | `CaisiIntegratorUpdateTask`, `CaseManagementManagerImpl` |
| `PreventionMergedDemographicDaoImpl` | `preventionDaoImpl` | `PreventionDao` | `CaisiIntegratorUpdateTask`, `OscarChartPrinter`, `CihiExport2Action`, `PreventionData`, `AddPrevention2Action` |

---

## Summary by Category

| Category | Count | Risk Level | Notes |
|----------|-------|------------|-------|
| Encounter Data Records (`Ect*Record`) | 10 | Low | No dynamic factory, no references anywhere |
| DAO Implementations (zombie beans) | 20 | Medium | `@Repository` with zero interface consumers |
| CAISI Integrator | 12 | Low | WS clients and data objects |
| PMmodule | 11 | Low | Legacy program management |
| REST Transfer Objects | 12 | Low | Unused DTOs and converters |
| Billing | 7 | Low | Legacy billing data/handler classes |
| Jobs/Schedulers | 4 | Low | Unconfigured job classes |
| Other (misc utilities, actions, etc.) | 73 | Low-Medium | Mixed bag of utilities and legacy code |
| **TOTAL** | **149** | | |

---

## Detailed Unused Class List

### 1. Encounter Data Records (10 classes) - LOW RISK

Legacy encounter form data record classes. Confirmed NO dynamic factory exists for `Ect*Record` (unlike `Frm*Record` which has `FrmRecordFactory`).

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

### 2. DAO Implementations - Zombie Beans (20 classes) - MEDIUM RISK

These are `@Repository`-annotated Spring beans with zero interface consumers. Spring instantiates them at startup, but nothing injects or uses them. When removing, also remove the corresponding interface.

| Class | File |
|-------|------|
| `CaisiFormDaoImpl` | `src/main/java/io/github/carlos_emr/carlos/commn/dao/CaisiFormDaoImpl.java` |
| `CaisiFormDataDaoImpl` | `src/main/java/io/github/carlos_emr/carlos/commn/dao/CaisiFormDataDaoImpl.java` |
| `CaisiFormDataTmpSaveDaoImpl` | `src/main/java/io/github/carlos_emr/carlos/commn/dao/CaisiFormDataTmpSaveDaoImpl.java` |
| `CaisiFormInstanceDaoImpl` | `src/main/java/io/github/carlos_emr/carlos/commn/dao/CaisiFormInstanceDaoImpl.java` |
| `CaisiFormInstanceTmpSaveDaoImpl` | `src/main/java/io/github/carlos_emr/carlos/commn/dao/CaisiFormInstanceTmpSaveDaoImpl.java` |
| `CaisiFormQuestionDaoImpl` | `src/main/java/io/github/carlos_emr/carlos/commn/dao/CaisiFormQuestionDaoImpl.java` |
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
| `ProgramAccessRolesDaoImpl` | `src/main/java/io/github/carlos_emr/carlos/commn/dao/ProgramAccessRolesDaoImpl.java` |
| `ReadLabDaoImpl` | `src/main/java/io/github/carlos_emr/carlos/commn/dao/ReadLabDaoImpl.java` |
| `RecycleBinBillingDaoImpl` | `src/main/java/io/github/carlos_emr/carlos/commn/dao/RecycleBinBillingDaoImpl.java` |
| `RemoteDataLogDaoImpl` | `src/main/java/io/github/carlos_emr/carlos/commn/dao/RemoteDataLogDaoImpl.java` |

### 3. CAISI Integrator (12 classes) - LOW RISK

Web service client stubs and data objects for the CAISI integrator system.

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

### 4. PMmodule (11 classes) - LOW RISK

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

### 5. REST Transfer Objects (12 classes) - LOW RISK

Unused REST DTOs, converters, and response objects. Not referenced by any active endpoint.

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

### 6. Billing (7 classes) - LOW RISK

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

### 7. Jobs and Schedulers (3 classes) - LOW RISK

Job classes not configured in any scheduler, Spring job context, or database `OscarJobType` table.

| Class | File |
|-------|------|
| `AuditLogPurgeJob` | `src/main/java/io/github/carlos_emr/carlos/admin/job/AuditLogPurgeJob.java` |
| `MatchManagerScheduler` | `src/main/java/io/github/carlos_emr/carlos/match/MatchManagerScheduler.java` |
| `AutoTickler` | `src/main/java/io/github/carlos_emr/carlos/tickler/AutoTickler.java` |

### 8. Form Utilities (1 class) - LOW RISK

| Class | File | Notes |
|-------|------|-------|
| `FormBooleanValuePK` | `src/main/java/io/github/carlos_emr/carlos/form/model/FormBooleanValuePK.java` | `@Embeddable` but not used by any active `@Entity` |

### 9. Other Unused Classes (66 classes) - LOW-MEDIUM RISK

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

## Annotations Present But Inactive

These annotations were found on unused classes but do NOT make them active:

| Annotation | Classes | Why Still Unused |
|-----------|---------|------------------|
| `@Entity` | `BillingONCHeader2`, `CachedDemographicImage`, `EventLog`, `HomelessPopulationReport` | Not in `persistence.xml` or HBM, not registered in any ORM context |
| `@Embeddable` | `MsgDemoMapPK`, `FormBooleanValuePK` | Not referenced by any active `@Entity` |
| `@XmlRootElement` | 9 REST transfer objects | Not used by any active web service endpoint |
| `@XmlType` | `AuditFormat`, `MedicalSurgicalFlag`, `PreferredMethodOfContact` | HRM XSD types, not in active JAXB context |
| `@Repository` | 20 DAO implementations | Scanned by Spring but zero interface consumers (zombie beans) |
| `@Service` | `WaitListManager` | Scanned by Spring but never injected |
| `@Component` | `TicklerService` | Scanned by Spring but never injected |
| `@WebService` | `HCValidationImpl1` | Not deployed in any CXF/JAXWS endpoint config |
| `@WebServiceClient` | `WaitListService_Service` | Generated client class, never used |
| `implements Serializable` | 11 classes | Passive interface, no active deserialization paths |
| `implements MethodInterceptor` | `MergedDemographicInterceptor` | Not registered in any Spring AOP config |

---

## Recommended Removal Order

1. **Phase 1 - Lowest risk** (11 classes): Encounter Data Records + FormBooleanValuePK
   - Self-contained data classes with no dependencies or annotations
   - Zero risk of runtime impact

2. **Phase 2 - Low risk** (46 classes): CAISI Integrator + PMmodule + REST DTOs + Billing
   - Isolated subsystem classes with no framework wiring

3. **Phase 3 - Low risk** (4 classes): Jobs/Schedulers
   - Not configured in any job system

4. **Phase 4 - Medium risk** (20 classes): Zombie DAO Implementations
   - Remove both `*DaoImpl` and corresponding interfaces
   - Spring will no longer instantiate these beans
   - Build after each removal to catch compile errors

5. **Phase 5 - Remaining** (68 classes): Other utilities, actions, helpers
   - Remove in small batches, build-test after each batch

---

## Verification Checklist

Before removing any class, verify:

- [ ] No references in Java source files (excluding self and tests)
- [ ] No references in Struts XML configuration
- [ ] No references in Spring XML configuration (applicationContext*.xml, spring_ws.xml)
- [ ] No references in Hibernate mapping files (*.hbm.xml) or persistence.xml
- [ ] No references in JSP/JSPF files (scriptlet imports, useBean directives)
- [ ] No `@Component`/`@Service`/`@Repository` with active interface consumers
- [ ] No `@Repository` subclass providing a parent's DAO interface to active callers
- [ ] No reflection-based loading (Class.forName, FrmRecordFactory, FrmGraphicFactory, OscarJobUtils)
- [ ] No database-driven class loading (OscarJobType table)
- [ ] No config-file-driven class loading (__className properties)
- [ ] No string-based Spring bean lookup (SpringUtils.getBean("name"))
- [ ] No AOP pointcut interception (@Aspect)
- [ ] No Spring event listener registration (ApplicationListener, @EventListener)
- [ ] No lifecycle annotations (@PostConstruct, @Scheduled, @Async)
- [ ] Build succeeds after removal (`make install`)

---

## Dynamic Loading Mechanisms (reference for future audits)

These are the `Class.forName()` patterns discovered during analysis. Any future class additions matching these patterns will be loaded at runtime even without explicit Java references:

| Factory | Pattern | Location |
|---------|---------|----------|
| `FrmRecordFactory` | `"io.github.carlos_emr.carlos.form.Frm" + which + "Record"` | `FrmRecordFactory.java:41-45` |
| `FrmGraphicFactory` | `Class.forName(className)` from `__className` config | `FrmGraphicFactory.java:44` |
| `OscarJobUtils` | `Class.forName(oscarJobType.getClassName())` from DB | `OscarJobUtils.java:122` |
| `FrmPDFServlet` | `Class.forName("...form.pdfservlet." + param)` | `FrmPDFServlet.java` |
| `DSGuidelineDrools` | `Class.forName(dsp.getStrClass())` from decision support | `DSGuidelineDrools.java` |

Generated with Claude Code

https://claude.ai/code/session_01WqpqNAcrKKYk48no5CuCit
