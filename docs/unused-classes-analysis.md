# CARLOS EMR - Unused Classes Analysis

**Date**: 2026-02-16
**Branch**: `claude/identify-unused-classes-Zxx1H`
**Total Unused Classes Found**: 262 (explicitly unreferenced) + ~230 JAXB stubs (see Section 17)
**Total Classes in Codebase**: 4,068
**Percentage Unused**: ~12.1% including JAXB stubs, ~6.4% excluding them

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
| Dead Entity Duplicates (`entities/`) | 12 | Low | Legacy POJOs duplicating `commn.model` + isolated pairs |
| Dead Model Classes (`commn/model/`) | 1 | Low | Unreferenced JPA entity (Pronoun) |
| DAO Implementations (`commn/dao/`) | 26 | Medium | Interfaces unused too; safe to remove |
| Registered-but-Unused Beans | 10 | Medium | Spring XML beans never consumed (incl. DAO pairs) |
| Dead-Chain Model Classes | 11 | Medium | Models only referenced by their unused DAOs |
| Web Services (`webserv/`) | 26 | Medium | REST/SOAP endpoints and DTOs |
| CAISI Integrator (explicit) | 14 | Low | Related to archived CAISI integration |
| CAISI Integrator JAXB stubs | ~230 | Low | Auto-generated WS stubs (see Section 17) |
| PMmodule | 11 | Low | Legacy program management classes |
| Billing | 8+3 | Medium | Province-specific billing (3 cross-ref with entities) |
| Integration | 13 | Medium | External system integrations (incl. dashboard models) |
| Hospital Report Manager | 3 | Low | HRM XSD enums (HRMAction.java is misnamed, not dead) |
| Utility/Infrastructure | 13 | Low | Utility classes, config, helpers |
| Login/Security | 6 | Medium | Auth-related unused code |
| Other (misc) | 39 | Varies | Various domain-specific unused classes |

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

### 3. Dead Entity Classes (`entities/` package) (12 classes) - LOW RISK

The `entities/` package contains legacy POJOs that predate the `commn.model` JPA entities.
Many are duplicates of actively-used classes elsewhere. The package is ~37.5% dead code.

**Duplicate entities** (plain POJOs superseded by JPA-annotated `commn.model` equivalents):

| Class | entities/ File | Active Equivalent |
|-------|---------------|-------------------|
| `EChart` | `entities/EChart.java` | `commn.model.EChart` (6 imports) |
| `Ichppccode` | `entities/Ichppccode.java` | `commn.model.Ichppccode` (2 imports) |
| `Immunizations` | `entities/Immunizations.java` | `commn.model.Immunizations` (3 imports) |
| `LabTest` | `entities/LabTest.java` | `commn.model.LabTest` (5 imports) |
| `Prescription` | `entities/Prescription.java` | `commn.model.Prescription` (13 imports) |

**Legacy POJOs with zero references anywhere:**
```
src/main/java/io/github/carlos_emr/carlos/entities/LabData.java
src/main/java/io/github/carlos_emr/carlos/entities/LabRequest.java
src/main/java/io/github/carlos_emr/carlos/entities/LoincCodes.java
src/main/java/io/github/carlos_emr/carlos/entities/Insclaim.java
```

**Note**: `LabData` (JSP "LabData" matches are JavaScript variable names), `LabRequest`
(callers use `commn.model.LabRequestReportLink`), `LoincCodes` (1353 lines, massive
but completely orphaned), and `Insclaim` (732 lines, legacy billing entity never integrated).

**Mutually-isolated pair** (only reference each other, nothing else uses either):
```
src/main/java/io/github/carlos_emr/carlos/entities/ClinicalFactor.java
src/main/java/io/github/carlos_emr/carlos/entities/Condition.java
```

**Note**: `Billcenter`, `Billingdetail`, and `Insclaim` from this package are also listed
in Section 7 (Billing) since they relate to billing functionality.

### 4. Dead Model Classes (`commn/model/`) (1 class) - LOW RISK

**`Pronoun`** - Unreferenced JPA entity mapped to `lst_pronoun` table:
```
src/main/java/io/github/carlos_emr/carlos/commn/model/enumerator/Pronoun.java
```

The entity and its `@NamedQuery` are never queried or loaded. JSP files reference
"Pronouns" as an HTML form label string, not this Java class. The pronoun field on
demographics uses raw string input, not this entity.

### 5. DAO Implementations (26 classes) - MEDIUM RISK

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

### 5b. Registered-but-Unused Spring Beans (10 classes) - MEDIUM RISK

These classes are registered as Spring beans (via `applicationContext.xml` or `@Repository`/
`@Component` annotations) but are **never consumed** - no Java code, JSP, or XML
configuration references the bean ID or the class/interface.

**Recommendation**: Remove from both Spring XML config and source code. The Spring XML
bean definitions should be removed first to confirm no runtime errors.

**Service/Bean registered in XML but never consumed:**
```
src/main/java/io/github/carlos_emr/carlos/casemgmt/service/MeasurementPrint.java
src/main/java/io/github/carlos_emr/carlos/billings/ca/bc/data/PrivateBillTransactionsDAO.java
```

**DAO interface + Impl pairs registered in XML but never consumed:**
```
src/main/java/io/github/carlos_emr/carlos/commn/dao/AllergyMergedDemographicDao.java
src/main/java/io/github/carlos_emr/carlos/commn/dao/AllergyMergedDemographicDaoImpl.java
src/main/java/io/github/carlos_emr/carlos/commn/dao/DocumentMergeDemographicDAO.java
src/main/java/io/github/carlos_emr/carlos/commn/dao/DocumentMergeDemographicDAOImpl.java
src/main/java/io/github/carlos_emr/carlos/commn/dao/InboxResultsRepository.java
src/main/java/io/github/carlos_emr/carlos/commn/dao/InboxResultsRepositoryImpl.java
```

**Note**: `MeasurementPrint` is registered as bean `extPrintMeasurements` in
`applicationContext.xml` but the bean ID is never referenced anywhere.
`PrivateBillTransactionsDAO` is similarly registered but never injected.
The three DAO pairs are registered in `applicationContext.xml`/`spring_jpa.xml`
(some explicitly excluded from component scanning) and never consumed.

### 5c. Dead Code Chain: Model Classes Only Referenced by Unused DAOs (11 classes)

These model classes are **only** referenced by their corresponding DAO (listed in
Sections 5 or 5b), which is itself unused. Removing the DAO without removing the
model would leave an orphaned class; both should be removed together.

| Model Class | File | Only Referenced By (Unused DAO) |
|-------------|------|---------------------------------|
| `CaisiForm` | `commn/model/CaisiForm.java` | `CaisiFormDao` |
| `IntegratorConsentComplexExitInterview` | `commn/model/IntegratorConsentComplexExitInterview.java` | `IntegratorConsentComplexExitInterviewDao` |
| `MdsZCL` | `commn/model/MdsZCL.java` | `MdsZCLDao` |
| `MdsZCT` | `commn/model/MdsZCT.java` | `MdsZCTDao` |
| `MdsZFR` | `commn/model/MdsZFR.java` | `MdsZFRDao` |
| `OscarAnnotation` | `commn/model/OscarAnnotation.java` | `OscarAnnotationDao` |
| `ProgramAccessRoles` | `commn/model/ProgramAccessRoles.java` | `ProgramAccessRolesDao` |
| `ReadLab` | `commn/model/ReadLab.java` | `ReadLabDao` |
| `RecycleBinBilling` | `commn/model/RecycleBinBilling.java` | `RecycleBinBillingDao` |
| `RemoteDataLog` | `commn/model/RemoteDataLog.java` | `RemoteDataLogDao` |
| `BillingPrivateTransactions` | `billing/CA/BC/model/BillingPrivateTransactions.java` | `PrivateBillTransactionsDAO` |

**Notable dead subsystems**:
- **CAISI Forms**: All 6 CaisiForm*Dao pairs + `CaisiForm` model are completely dead
- **MergedDemographic DAOs**: All 5 `*MergedDemographic*` pairs (Allergy, Consultation,
  Document, Drug, Prevention) - demographic merge feature appears never completed
- **MDS (Minimum Data Set)**: All 3 MdsZ*Dao pairs + models - RAI data no longer used

### 5d. False Positives Identified and Excluded

The following classes were initially flagged as unused but are confirmed active:

- **`DrugLookUpManager`**: Implements the `DrugLookUp` interface and is annotated
  `@Service`. It IS the bean injected when callers request `DrugLookUp` via Spring DI
  (used by `DrugConverterImpl`, `FavoriteConverterImpl`, `RxLookupService`).

### 6. Web Services and REST DTOs (26 classes) - MEDIUM RISK

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

### 7. CAISI Integrator - Explicit Unused (14 classes) - LOW RISK

Related to the archived CAISI integration system. Per CLAUDE.md, CAISI integrator
architecture is being phased out.

**DAO classes with no external references:**
```
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/dao/CachedDemographicImage.java
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/dao/EventLog.java
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/dao/HomelessPopulationReport.java
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/dao/MatchingCachedDemographicScore.java
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/dao/ImportLog.java
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/dao/ProviderCommunication.java
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/dao/SystemProperties.java
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/dao/Referral.java
```

**Generated WS test clients (zero external references):**
```
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/ws/DemographicWs_DemographicWsPort_Client.java
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/ws/FacilityWs_FacilityWsPort_Client.java
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/ws/HnrWs_HnrWsPort_Client.java
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/ws/ProgramWs_ProgramWsPort_Client.java
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/ws/ProviderWs_ProviderWsPort_Client.java
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/ws/ReferralWs_ReferralWsPort_Client.java
```

**Other CAISI-related unused:**
```
src/main/java/io/github/carlos_emr/carlos/PMmodule/caisi_integrator/ByteWrapper.java
src/main/java/io/github/carlos_emr/carlos/PMmodule/caisi_integrator/IntegratorLocalStoreUpdateJob.java
src/main/java/io/github/carlos_emr/carlos/caisi/CaisiUtil.java
src/main/java/io/github/carlos_emr/carlos/caisi/OscarMenuExtension.java
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/util/ImageIoUtils.java
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/util/XmlUtils.java
src/main/java/io/github/carlos_emr/carlos/caisi_integrator/util/ConfigXmlUtils.java
```

### 8. PMmodule (11 classes) - LOW RISK

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

### 9. Billing (8 + 3 cross-ref classes) - MEDIUM RISK

Province-specific billing classes and third-party integration code.

**Note**: Billing code is critical healthcare infrastructure. Extra care recommended.
The last 3 files are `entities/` package classes also listed in Section 3.

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

### 10. Integration (6 classes) - MEDIUM RISK

Dashboard model classes used only for JSON serialization to an external outcomes
dashboard service. All 6 remaining classes live under `integration/dashboard/model/`.

**Dashboard model classes** (only used by `OutcomesDashboardUtils` for JSON
serialization to an external dashboard service - if that service is decommissioned,
all are dead):
```
src/main/java/io/github/carlos_emr/carlos/integration/dashboard/model/Clinic.java
src/main/java/io/github/carlos_emr/carlos/integration/dashboard/model/MetricData.java
src/main/java/io/github/carlos_emr/carlos/integration/dashboard/model/MetricOwner.java
src/main/java/io/github/carlos_emr/carlos/integration/dashboard/model/MetricSet.java
src/main/java/io/github/carlos_emr/carlos/integration/dashboard/model/Name.java
src/main/java/io/github/carlos_emr/carlos/integration/dashboard/model/User.java
```

### 11. Hospital Report Manager (3 classes) - LOW RISK

```
src/main/java/io/github/carlos_emr/carlos/hospitalReportManager/xsd/AuditFormat.java
src/main/java/io/github/carlos_emr/carlos/hospitalReportManager/xsd/MedicalSurgicalFlag.java
src/main/java/io/github/carlos_emr/carlos/hospitalReportManager/xsd/PreferredMethodOfContact.java
```

**Note**: `HRMAction.java` was initially flagged but is a **false positive** - the file
is misnamed and actually defines `class ColumnInfo` (not `HRMAction`), which IS used
by `HRM2Action.java`. The file should be renamed to `ColumnInfo.java` but is not dead code.

### 12. Utility and Infrastructure (13 classes) - LOW RISK

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

### 13. Login and Security (6 classes) - MEDIUM RISK

**Note**: Security-related code changes should be carefully reviewed.

```
src/main/java/io/github/carlos_emr/carlos/app/OAuth1Utils.java
src/main/java/io/github/carlos_emr/carlos/login/AuthResult.java
src/main/java/io/github/carlos_emr/carlos/login/GenericOAuth10aApi.java
src/main/java/io/github/carlos_emr/carlos/login/ValidateMFA2Action.java
src/main/java/io/github/carlos_emr/carlos/sec/CookieSecurity.java
src/main/java/io/github/carlos_emr/carlos/sec/token/StJoesTokenManager.java
```

### 14. Encounter Actions and Utils (8 classes) - LOW RISK

The `EctDisplay*Action.java` classes are superseded Struts 1.x actions that have
been replaced by `EctDisplay*2Action.java` equivalents wired in struts.xml.

```
src/main/java/io/github/carlos_emr/carlos/encounter/oscarConsultationRequest/pageUtil/ConsultationAttachForms.java
src/main/java/io/github/carlos_emr/carlos/encounter/oscarConsultationRequest/pageUtil/EctConsultationFormRequestPrintPdf.java
src/main/java/io/github/carlos_emr/carlos/encounter/oscarMeasurements/pageUtil/EctValidationParameter.java
src/main/java/io/github/carlos_emr/carlos/encounter/pageUtil/EctDisplayAppointmentHistoryAction.java
src/main/java/io/github/carlos_emr/carlos/encounter/pageUtil/EctDisplayDxAction.java
src/main/java/io/github/carlos_emr/carlos/encounter/pageUtil/EctDisplayIssuesAction.java
src/main/java/io/github/carlos_emr/carlos/encounter/pageUtil/EctDisplayPhotosAction.java
src/main/java/io/github/carlos_emr/carlos/encounter/pageUtil/EctDisplayResolvedIssuesAction.java
```

### 15. Ontario Health (2 classes) - LOW RISK

Legacy Ontario health integration implementations. The `HCValidation` interface is
still used, but `HCValidationImpl1` specifically is never referenced.

```
src/main/java/ca/ontario/health/hcv/HCValidationImpl1.java
src/main/java/ca/ontario/health/edt/EDTDelegateImpl.java
```

**Note on `EDTDelegateImpl`**: The `EDTDelegate` interface is referenced by MCEDT
integration code. However `EDTDelegateImpl` itself is never instantiated - check
if `DelegateFactory` creates it via reflection before removing.

### 16. Remaining Miscellaneous (39 classes)

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
src/main/java/io/github/carlos_emr/carlos/managers/WaitListManager.java
src/main/java/io/github/carlos_emr/carlos/match/MatchManagerScheduler.java
src/main/java/io/github/carlos_emr/carlos/messenger/pageUtil/MsgCreateMessageBean.java
src/main/java/io/github/carlos_emr/carlos/model/DefaultCustomFilter.java
src/main/java/io/github/carlos_emr/carlos/prescript/data/RxAllergyData.java
src/main/java/io/github/carlos_emr/carlos/prescript/util/ShowAllSorter.java
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

### 17. CAISI Integrator JAXB-Generated Stubs (~230 classes) - LOW RISK

The `caisi_integrator/ws/` package contains **~242 files**, of which **~230 are
auto-generated JAXB web service stubs** (Request/Response pairs, data transfer
objects). These were generated from the CAISI integrator server's WSDL.

Breakdown of the ~230 JAXB stubs:
- **~84 `*Response.java`** - JAXB response wrappers
- **~79 `Get*.java`** - JAXB request wrappers
- **~49 `Set*.java`** - JAXB request wrappers
- **~6 `Delete*.java`** - JAXB request wrappers
- **~12 misc** - Other generated data types

**Still active** (~12 files in `caisi_integrator/ws/`):
- 6 WS service interfaces: `DemographicWs`, `FacilityWs`, `HnrWs`, `ProgramWs`,
  `ProviderWs`, `ReferralWs`
- 6 WS service factory classes: `*WsService.java`
- Transfer objects used by `CaisiIntegratorManager`

**Important caveat**: While these JAXB stubs have no explicit Java imports outside
the package, the JAXB runtime uses them via annotation-based auto-discovery during
SOAP message deserialization. Removing them requires confirming that the CAISI
integrator SOAP communication can function without them, or that CAISI integration
is fully decommissioned.

Full listing not included due to volume (~230 files). To see all files:
```bash
find src/main/java/io/github/carlos_emr/carlos/caisi_integrator/ws/ \
  -name "*Response.java" -o -name "Get*.java" -o -name "Set*.java" \
  -o -name "Delete*.java" | wc -l
```

### Confirmed NOT Dead (Packages Verified Active)

The following packages were verified as fully active and should NOT be removed:
- **`webserv/`** (309 files) - All wired in Spring config (`spring_ws.xml`,
  `applicationContextREST.xml`, component scanning)
- **`decisionSupport/`** (19 files) - Heavily cross-referenced from login, flowsheets,
  prevention, billing, and REST API
- **`drools/`** (1 file) - Used by decision support and measurement flowsheets
- **`mds/`** (17 files) - All actions mapped in struts.xml, used by lab display
- **`match/`** (9 files) - Used by PMmodule vacancy/waitlist management
- **`workflow/`** (8 files) - Used by Rh Immunoglobulin form (narrow but active)
- **`contactRegistry/`** (1 file) - Mapped in struts.xml
- **`scratch/`** (3 files) - Mapped in struts.xml with JSP

### Removed Module Remnant Status

| Module | Status | Remnants |
|--------|--------|----------|
| MyDrugRef | Properly removed | Minor: 2 JS string replacements in `SearchDrug3.jsp` |
| BORN Integration | Properly removed | None (grep hits are medical terms, not module refs) |
| HealthSafety | Properly removed | Only a database column name in HBM mapping |
| ERx (External Prescriber) | Properly removed | None |

---

## Recommended Removal Priority

### Phase 1: Safe to Remove Immediately (LOW RISK - ~95 classes)
- All 52 `Frm*Record` form classes + `FrmPdfGraphicRourke` + `FormBooleanValuePK`
- All 10 `Ect*Record` encounter data classes
- 12 dead `entities/` package classes (5 duplicates, 4 orphaned POJOs, 2 isolated pair,
  `Insclaim` - note: `Billcenter`/`Billingdetail` counted under billing too)
- `Pronoun.java` (unreferenced JPA entity in `commn/model/enumerator/`)
- `Dangerous.java` (annotation marker, unused)
- `LookupTagValue.java`, `DAO.java`, `Pager.java`
- `FileHolder.java`, `PagerDef.java`
- `OscarMenuExtension.java`, `CaisiUtil.java`
- `ByteWrapper.java`, `EnumNameComparator.java`
- `UserSearchFormBean.java`
- 6 CAISI `*_*Port_Client.java` generated test stubs

### Phase 2: Remove After Verification (MEDIUM RISK - ~151 classes)
- 26 DAO implementations + 11 dead-chain model classes (remove DAO+model together)
- 10 registered-but-unused Spring beans (remove XML config + source together)
- 26 web service REST/SOAP classes (verify no CXF auto-discovery)
- CAISI integrator DAO + util classes (8+3 classes)
- PMmodule legacy classes (13 classes)
- Billing-related unused classes (9 classes)
- Dashboard model classes (6 classes - verify external service status)

### Phase 3: Requires Careful Review (HIGHER RISK - ~50 classes)
- Login/security classes (`OAuth1Utils`, `ValidateMFA2Action`, `CookieSecurity`)
- Integration classes (EBS, FHIR, DHIR)
- Billing province-specific code
- `EDTDelegateImpl` (check `DelegateFactory` for reflection usage)

### Phase 4: CAISI JAXB Stubs (~230 classes) - Requires Architecture Decision
- ~230 auto-generated JAXB stubs in `caisi_integrator/ws/`
- Removal depends on whether CAISI integrator SOAP communication is still needed
- If CAISI integration is fully decommissioned per the phased removal plan
  (PR #398), these can be removed as a batch with the rest of the `caisi_integrator/`
  package

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
