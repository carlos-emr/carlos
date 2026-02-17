# CAISI Integrator System Architecture (Archived)

> **Status**: DEPRECATED / PENDING REMOVAL
>
> This document archives the complete architecture and design of the CAISI Integrator
> subsystem as it existed in the CARLOS EMR codebase prior to removal. It is intended
> as a historical reference for developers who encounter legacy references, database
> tables, or code comments mentioning the integrator.
>
> **Date of analysis**: February 2026
> **Decision**: Remove integrator code to reduce attack surface and maintenance burden.
> The integrator server no longer exists and the code has been dead for years.

---

## Table of Contents

1. [Overview](#1-overview)
2. [What the Integrator Did](#2-what-the-integrator-did)
3. [Architecture](#3-architecture)
4. [Data Flow](#4-data-flow)
5. [Package Structure](#5-package-structure)
6. [Spring Configuration](#6-spring-configuration)
7. [Web Service Layer](#7-web-service-layer)
8. [Data Access Layer](#8-data-access-layer)
9. [Manager Layer](#9-manager-layer)
10. [Cached Data Model (WS DTOs)](#10-cached-data-model-ws-dtos)
11. [Cached Data Model (JPA Entities)](#11-cached-data-model-jpa-entities)
12. [Database Schema](#12-database-schema)
13. [External Coupling Points](#13-external-coupling-points)
14. [Struts Action Mappings](#14-struts-action-mappings)
15. [JSP Views](#15-jsp-views)
16. [Scheduled Jobs](#16-scheduled-jobs)
17. [Security Architecture](#17-security-architecture)
18. [Data Completeness Analysis](#18-data-completeness-analysis)
19. [Known Limitations](#19-known-limitations)
20. [Removal Rationale](#20-removal-rationale)

---

## 1. Overview

The CAISI (Community Access to Integrated Services Initiative) Integrator was a SOAP-based
inter-EMR data sharing system that allowed multiple OSCAR/CARLOS installations to exchange
patient data through a central integrator server. It was originally developed for the
Toronto Community Health Centre network to coordinate care for patients (particularly
homeless and at-risk populations) who visited multiple community health centres.

**Key Facts:**
- Protocol: SOAP 1.1 with WS-Security (WSS4J) authentication
- Server: A separate Java application (not part of this codebase) that acted as a hub
- Client: The code in this codebase acted as a spoke connecting to that hub
- Data model: Triple-representation (WS DTO, JPA cache entity, domain model)
- Sync pattern: Periodic background jobs pulling from server, on-demand pushes
- Feature flag: `Facility.isIntegratorEnabled()` (defaults to `false`)
- Last meaningful development: ~2016 based on update SQL scripts

**Heritage:** The integrator was part of the broader CAISI initiative, which also included
the PMModule (Program Management Module) for case management of clients in shelters,
community health centres, and mental health programs. The PMModule is NOT part of the
integrator and remains in active use.

---

## 2. What the Integrator Did

### Use Case

A patient visits Community Health Centre A and gets medications prescribed. The same
patient later visits Community Health Centre B. With the integrator enabled:

1. Centre A pushes the patient's data to the central integrator server
2. The integrator server stores a canonical copy
3. Centre B's background job pulls data from the integrator server
4. Centre B's clinician sees the medications from Centre A in the patient's chart

### Functional Capabilities

1. **Demographic Linking**: Match patients across facilities by HIN, name, DOB
2. **Clinical Data Sharing**: Medications, allergies, preventions, lab results, notes, issues
3. **Consent Management**: Digital consent with complex exit interviews
4. **Inter-Facility Messaging**: Provider-to-provider communication
5. **Referrals**: Referral tracking between programs across facilities
6. **Document Sharing**: Clinical document content exchange
7. **Appointment Visibility**: View appointments at other facilities
8. **HNR (Homeless and Newcomer Registry)**: Population-level reporting

### What It Did NOT Do

- Real-time synchronization (batch/periodic only)
- Bidirectional conflict resolution
- Structured lab results (only HL7 blobs)
- Complete billing data (only billing items, no payment tracking)
- Consultation/referral lifecycle (only basic referrals)
- Audit trail preservation (lastUpdateUser/Date fields dropped)

---

## 3. Architecture

### High-Level Topology

```
+-------------------+       SOAP/HTTPS       +--------------------+
|  CARLOS EMR       | <-------------------> |  Integrator Server  |
|  (this codebase)  |                        |  (external, gone)   |
|                   |       SOAP/HTTPS       |                    |
|  Facility A       | <-------------------> |                    |
+-------------------+                        +--------------------+
                                                      ^
+-------------------+       SOAP/HTTPS               |
|  OSCAR EMR        | <------------------------------+
|  Facility B       |
+-------------------+
```

### Internal Architecture (This Codebase)

```
+-------------------------------------------------------------------+
|                        Web Layer (JSP/Actions)                     |
|  EctDisplayAllergy2Action, CaseManagementView2Action, etc.        |
|  Check: facility.isIntegratorEnabled()                            |
+-------------------------------------------------------------------+
        |                                |
        v                                v
+-------------------+     +----------------------------------+
|  Normal DAO/      |     |  Integrator Manager Layer        |
|  Manager Path     |     |  CaisiIntegratorManager          |
|  (local data)     |     |  IntegratorFallBackManager       |
+-------------------+     |  RemotePreventionHelper          |
                          |  RemoteDrugAllergyHelper         |
                          +----------------------------------+
                                    |              |
                          (online)  |              | (offline/error)
                                    v              v
                          +----------------+  +---------------------+
                          | WS Client      |  | FallBack Storage    |
                          | DemographicWs  |  | RemoteIntegrated    |
                          | FacilityWs     |  | DataCopy (XML blob) |
                          | ProviderWs     |  +---------------------+
                          | ProgramWs      |
                          | ReferralWs     |
                          | HnrWs          |
                          +----------------+
                                    |
                              SOAP/HTTPS
                                    |
                                    v
                          +------------------+
                          | Integrator Server|
                          | (GONE)           |
                          +------------------+
```

### Triple Data Representation

Every synced data type existed in three forms:

| Layer | Example | Annotations | Purpose |
|-------|---------|-------------|---------|
| `caisi_integrator/ws/CachedDemographicAllergy` | JAXB DTO | `@XmlType`, `@XmlElement` | SOAP wire format, generated from WSDLs |
| `caisi_integrator/dao/CachedDemographicAllergy` | JPA Entity | `@Entity`, `@EmbeddedId` | Local database cache |
| `commn/model/Allergy` | Domain Model | JPA/Hibernate | Actual EMR patient data |

The WS layer was **generated from the integrator server's WSDLs** via JAXB code generation.
The DAO layer provided a local cache for offline/fallback access. No automated mapping
existed between any of these three layers.

---

## 4. Data Flow

### Outbound (Push to Integrator)

```
1. CaisiIntegratorUpdateTask (scheduled job) runs periodically
2. Reads local patient data from domain models (Demographic, Drug, Allergy, etc.)
3. Converts to WS transfer objects (DemographicTransfer, etc.)
4. Calls integrator server via SOAP WS (DemographicWs.setDemographic(), etc.)
5. Tracks push progress in IntegratorProgress/IntegratorProgressItem tables
```

### Inbound (Pull from Integrator)

```
1. IntegratorLocalStoreUpdateJob (scheduled job) runs periodically
2. Calls integrator server via SOAP WS (DemographicWs.getLinkedCachedDemographic*())
3. Receives CachedDemographic* WS transfer objects
4. Serializes WS objects as XML blobs into RemoteIntegratedDataCopy table
5. Also populates some cached DAO entities for direct querying
```

### Display (Reading Remote Data)

```
1. Action class (e.g., EctDisplayAllergy2Action) checks facility.isIntegratorEnabled()
2. If enabled, tries CaisiIntegratorManager.getDemographicWs().getLinkedCachedDemographic*()
3. If server is offline, falls back to IntegratorFallBackManager.getRemote*()
4. FallBackManager reads XML blobs from RemoteIntegratedDataCopy, unmarshals via JAXB
5. Remote data is merged with local data for display
```

### Consent Flow

```
1. Patient signs digital consent form (IntegratorConsent table)
2. Complex exit interview questions stored (IntegratorConsentComplexExitInterview)
3. Consent state pushed to integrator server via FacilityWs
4. Remote facilities can check consent before displaying shared data
```

---

## 5. Package Structure

### Core Integrator Packages

```
io.github.carlos_emr.carlos.caisi_integrator/
    dao/                    # 45+ JPA cached entity models + composite PKs
        CachedDemographic.java
        CachedDemographicAllergy.java
        CachedDemographicDrug.java
        CachedDemographicNote.java
        CachedDemographicPrevention.java
        CachedAppointment.java
        CachedAdmission.java
        CachedBillingOnItem.java
        CachedFacility.java
        CachedProgram.java
        CachedProvider.java
        CachedMeasurement.java
        CachedMeasurementExt.java
        CachedMeasurementMap.java
        CachedMeasurementType.java
        CachedDemographicConsent.java
        CachedDemographicDocument.java
        CachedDemographicDocumentContents.java
        CachedDemographicForm.java
        CachedDemographicHL7LabResult.java
        CachedDemographicLabResult.java
        CachedDemographicIssue.java
        CachedDemographicImage.java
        CachedDxresearch.java
        CachedEformData.java
        CachedEformValue.java
        DemographicLink.java
        DemographicPushDate.java
        EventLog.java
        Facility.java             # Integrator facility reference (NOT commn/model/Facility)
        HomelessPopulationReport.java
        ImportLog.java
        IssueGroup.java
        MatchingCachedDemographicScore.java
        NoteIssue.java
        ProviderCommunication.java
        Referral.java
        SiteUser.java
        SystemProperties.java
        AbstractModel.java        # Base class for cached entities
        FacilityIdIntegerCompositePk.java       # (facilityId, intId)
        FacilityIdStringCompositePk.java        # (facilityId, stringId)
        FacilityIdDemographicIssueCompositePk.java
        FacilityIdLabResultCompositePk.java
        CachedDemographicNoteCompositePk.java

    ws/                     # 290+ JAXB-generated SOAP client stubs
        # Service interfaces (6)
        DemographicWs.java / DemographicWsService.java
        FacilityWs.java / FacilityWsService.java
        ProviderWs.java / ProviderWsService.java
        ProgramWs.java / ProgramWsService.java
        ReferralWs.java / ReferralWsService.java
        HnrWs.java / HnrWsService.java

        # Client stubs (6)
        DemographicWs_DemographicWsPort_Client.java
        FacilityWs_FacilityWsPort_Client.java
        ProviderWs_ProviderWsPort_Client.java
        ProgramWs_ProgramWsPort_Client.java
        ReferralWs_ReferralWsPort_Client.java
        HnrWs_HnrWsPort_Client.java

        # Transfer objects (mirrors of dao/ Cached* classes + request/response wrappers)
        CachedDemographic.java, CachedDemographicAllergy.java, ... (all Cached* types)
        DemographicTransfer.java, ProviderTransfer.java
        FacilityConsentPair.java, SetConsentTransfer.java, GetConsentTransfer.java
        ProviderCommunicationTransfer.java
        MatchingDemographicParameters.java, MatchingDemographicTransferScore.java
        Role.java, Gender.java, ConsentState.java, CodeType.java

        # Operation wrappers (Get*/Set*/Link*/etc.)
        GetCachedDemographic*.java, SetCachedDemographic*.java
        GetLinkedCachedDemographic*.java
        GetDirectlyLinkedDemographicsByDemographicId.java
        LinkDemographics.java, UnLinkDemographics.java
        MakeReferral.java, RemoveReferral.java
        GetImportLogsSince.java, CompleteImportLog.java, ErrorImportLog.java

        # Exception classes
        ConnectException_Exception.java
        DuplicateHinExceptionException.java
        InvalidHinExceptionException.java

    util/                   # 8 utility classes
        CodeType.java
        ConfigXmlUtils.java
        EncryptionUtils.java
        ImageIoUtils.java
        MiscUtils.java
        Named.java
        Role.java
        XmlUtils.java

io.github.carlos_emr.carlos.PMmodule.caisi_integrator/
    CaisiIntegratorManager.java          # 887 lines - main orchestrator
    IntegratorFallBackManager.java       # 798 lines - offline/fallback storage
    CaisiIntegratorUpdateTask.java       # 2,757 lines - scheduled sync job
    IntegratorLocalStoreUpdateJob.java   # 192 lines - background update trigger
    IntegratorFileLogUpdateJob.java      # 107 lines - file-based logging
    ConformanceTestHelper.java           # 332 lines - testing utilities
    IntegratorRoleUtils.java             # 66 lines - role-based access
    RemoteDrugAllergyHelper.java         # 159 lines - remote allergy/drug handling
    RemotePreventionHelper.java          # 131 lines - remote prevention handling
    AuthenticationOutWSS4JInterceptorForIntegrator.java  # 194 lines - WS-Security
    IntegratorFileHeader.java            # 192 lines - file format header
    IntegratorFileFooter.java            # 86 lines - file format footer
    ByteWrapper.java                     # 76 lines - byte array utilities
    DeleteCachedDemographicIssuesWrapper.java     # 106 lines
    DeleteCachedDemographicPreventionsWrapper.java # 105 lines
    ProgramDeleteIdWrapper.java          # 49 lines

```

### NOT Part of the Integrator (General CAISI/PMModule)

```
io.github.carlos_emr.carlos.caisi/
    CaisiUtil.java                       # Generic query-string utility (NOT integrator)
    IsModuleLoadTag.java                 # Generic JSP module-load tag (NOT integrator)
    OscarMenuExtension.java             # Generic menu extension interface (NOT integrator)
```

### Supporting Classes in Common Packages

```
io.github.carlos_emr.carlos.commn.model/
    RemoteIntegratedDataCopy.java        # JPA entity for XML blob fallback storage
    FacilityDemographicPrimaryKey.java   # Composite PK for facility demographics
    FacilityMessage.java                 # Inter-facility messaging
    IntegratorProgress.java              # Push progress tracking
    IntegratorProgressItem.java          # Push progress line items
    Facility.java                        # Contains integrator fields (integratorEnabled,
                                         #   integratorUrl, integratorUser, integratorPassword,
                                         #   enableIntegratedReferrals)

io.github.carlos_emr.carlos.commn.dao/
    RemoteIntegratedDataCopyDao.java     # Interface
    RemoteIntegratedDataCopyDaoImpl.java # Implementation
    FacilityMessageDao.java              # Interface
    FacilityMessageDaoImpl.java          # Implementation
    IntegratorConsentDao.java            # Consent data access
    IntegratorConsentDaoImpl.java        # Implementation
    IntegratorControlDao.java            # Integrator control/config
    IntegratorControlDaoImpl.java        # Implementation
    IntegratorProgressDao.java           # Push progress tracking
    IntegratorProgressDaoImpl.java       # Implementation
    IntegratorProgressItemDao.java       # Push progress line items
    IntegratorProgressItemDaoImpl.java   # Implementation

io.github.carlos_emr.carlos.commn.web/
    IntegratorPush2Action.java           # Admin UI for push operations

io.github.carlos_emr.carlos.utility/
    ObjectMarshalUtil.java               # JAXB marshaling for WS objects

io.github.carlos_emr.carlos.casemgmt.web/
    NoteDisplayIntegrator.java           # Implements NoteDisplay for remote notes

io.github.carlos_emr.carlos.managers/
    IntegratorPushManager.java           # Push operation management
    MessengerIntegratorManager.java      # Inter-facility messaging bridge
```

---

## 6. Spring Configuration

### applicationContextCaisi.xml

**Location**: `src/main/resources/applicationContextCaisi.xml`
**Loaded by**: `web.xml` via `classpath:applicationContext*.xml` glob pattern

**CRITICAL NOTE**: Despite the name, this file contains **ZERO integrator-specific beans**.
All beans defined here are PMModule/core functionality. The file must be left intact
during integrator removal — it does not need to be modified at all.

#### Beans (ALL are non-integrator, ALL must be preserved)

| Bean ID | Class | Purpose |
|---------|-------|---------|
| `measurementTemplateFlowSheet` | `MeasurementTemplateFlowSheetConfig` | Configures measurement flowsheets (diabetes, hypertension, HIV, etc.) |
| `formsDAO` | `FormsDAOImpl` | CAISI forms data access |
| `populationReportDao` | `PopulationReportDaoImpl` | Population reporting |
| `issueAdminManager` | `IssueAdminManager` | Issue administration |
| `oscarSecurityManager` | `OscarSecurityManagerImpl` | Security management |
| `formsManagerCaisi` | `FormsManagerImpl` | Forms management |
| `populationReportManager` | `PopulationReportManager` | Population reports |
| `scheduledErProgramDischargeTask` | `ErProgramDischargeTask` | Scheduled ER discharge |
| `scheduledAnonymousClientDischargeTask` | `AnonymousClientDischargeTask` | Scheduled anonymous client discharge |

#### Component Scans (MUST PRESERVE)

```xml
<context:component-scan base-package="io.github.carlos_emr.carlos.PMmodule.dao" />
<context:component-scan base-package="io.github.carlos_emr.carlos.PMmodule.service" />
<context:component-scan base-package="io.github.carlos_emr.carlos.PMmodule.web.admin" />
```

These component scans register PMModule beans that are NOT part of the integrator.

---

## 7. Web Service Layer

### Service Interfaces

The integrator exposed 6 SOAP web service interfaces:

| Interface | Purpose | Key Operations |
|-----------|---------|----------------|
| `DemographicWs` | Patient data sync | `getLinkedCachedDemographic*()`, `setDemographic()`, `linkDemographics()` |
| `FacilityWs` | Facility management | `getConsentState()`, `setConsentState()`, `getFacilities()` |
| `ProviderWs` | Provider info | `getCachedProviders()`, `getProvider()` |
| `ProgramWs` | Program definitions | `getCachedPrograms()`, `getProgram()` |
| `ReferralWs` | Referral tracking | `makeReferral()`, `removeReferral()`, `getLinkedReferrals()` |
| `HnrWs` | Homeless/Newcomer Registry | HNR-specific population data |

### Authentication

WS-Security via `AuthenticationOutWSS4JInterceptorForIntegrator`:
- Username/password from `Facility.integratorUser` / `Facility.integratorPassword`
- WSS4J interceptor attached to CXF client conduit
- Credentials stored in facility table (potential security concern - cleartext password)

### Client Factory Pattern

```java
// CaisiIntegratorManager.java
public static DemographicWs getDemographicWs(LoggedInInfo loggedInInfo, Facility facility) {
    // Creates CXF client proxy with WS-Security interceptor
    // Connects to facility.getIntegratorUrl() + "/DemographicService"
}
```

---

## 8. Data Access Layer

### Composite Primary Keys

All cached entities used composite primary keys combining facility ID with entity ID:

```java
@Embeddable
public class FacilityIdIntegerCompositePk implements Serializable {
    private Integer integratorFacilityId;  // Which remote facility
    private Integer caisiItemId;           // ID at that remote facility
}
```

This allowed the same `demographicId=5` to exist from multiple remote facilities without
collision.

### Fallback Storage

`RemoteIntegratedDataCopy` stored WS DTOs as serialized XML blobs:

```java
@Entity
public class RemoteIntegratedDataCopy {
    private Integer id;
    private Integer demographicNo;        // Local demographic
    private String dataType;              // "ALLERGY", "DRUG", "NOTE", etc.
    private String data;                  // XML blob (JAXB-serialized WS object)
    private String signature;             // Data integrity check
    private Integer facilityId;           // Source facility
    private String providerNo;            // Source provider
    private Date lastUpdateDate;
    private boolean archived;
}
```

`ObjectMarshalUtil` handled JAXB marshaling/unmarshaling for this blob storage.

---

## 9. Manager Layer

### CaisiIntegratorManager (887 lines)

The central orchestrator. Static factory methods for WS clients:

```java
public class CaisiIntegratorManager {
    // WS client factories
    public static DemographicWs getDemographicWs(LoggedInInfo, Facility)
    public static FacilityWs getFacilityWs(LoggedInInfo, Facility)
    public static ProviderWs getProviderWs(LoggedInInfo, Facility)
    public static ProgramWs getProgramWs(LoggedInInfo, Facility)
    public static ReferralWs getReferralWs(LoggedInInfo, Facility)

    // State management
    public static boolean isIntegratorOffline(HttpSession)
    public static void checkForConnectionError(HttpSession, Exception)

    // Data access helpers
    public static CachedFacility getRemoteFacility(LoggedInInfo, Facility, int facilityId)
    public static List<CachedDemographicPrevention> getLinkedPreventions(LoggedInInfo, int demoId)
    // ... many more convenience methods
}
```

### IntegratorFallBackManager (798 lines)

Local cache read/write when integrator server is unreachable:

```java
public class IntegratorFallBackManager {
    // Save methods (write XML blobs to RemoteIntegratedDataCopy)
    public static void saveLinkNotes(LoggedInInfo, int demographicNo)
    public static void saveRemoteForms(LoggedInInfo, int demographicNo)
    public static void saveDemographicIssues(LoggedInInfo, int demographicNo)
    public static void saveDemographicPreventions(LoggedInInfo, int demographicNo)
    public static void saveDemographicDrugs(LoggedInInfo, int demographicNo)
    // ... more save methods

    // Read methods (unmarshal XML blobs for display)
    public static List<CachedDemographicAllergy> getRemoteAllergies(LoggedInInfo, int demoId)
    public static List<CachedDemographicDrug> getRemoteDrugs(LoggedInInfo, int demoId)
    public static List<CachedDemographicPrevention> getRemotePreventions(LoggedInInfo, int demoId)
    // ... more read methods
}
```

### CaisiIntegratorUpdateTask (2,757 lines)

The largest class - the scheduled background sync job:

```java
public class CaisiIntegratorUpdateTask implements Runnable {
    // Runs periodically to:
    // 1. Push local demographic data to integrator server
    // 2. Push consent states
    // 3. Push provider communication
    // 4. Update local cache from integrator server
    // 5. Track push progress
}
```

---

## 10. Cached Data Model (WS DTOs)

These JAXB-annotated classes represent the SOAP wire format. They were generated from the
integrator server's WSDLs and define what data the integrator could exchange.

### Field Comparison: WS DTO vs Domain Model

| Data Type | WS DTO Fields | Domain Model Fields | Coverage |
|-----------|---------------|---------------------|----------|
| Demographics | ~30 | ~50 | 60% |
| Medications (Drug) | 37 | 50+ | 74% |
| Allergies | 17 | 30+ | 57% |
| Measurements | 9 | 11 | 82% |
| Preventions | 9 | 12+ | 75% |
| Clinical Notes | 11 | 20+ | 55% |
| Clinical Issues | 7 | 15+ | 47% |
| Dx Research | 7 | 9 | 78% |
| Appointments | 16 | 30+ | 53% |
| Admissions | 7 | Complex | ~30% |
| Billing Items | 12 | 20+ | 60% |
| Documents | 19 | 30+ | 63% |
| Forms | 6 | 10+ | 60% |
| Lab Results | 4 (blob) | 40+ | 10% |
| Providers | 5 | 20+ | 25% |

### Key Fields Lost in Transmission

**Audit trail fields** (missing on most types):
- `lastUpdateUser` - WHO made changes (PIPEDA compliance gap)
- `lastUpdateDate` - WHEN changes were made

**Medication-specific losses**:
- `writtenDate`, `outsideProviderName`, `outsideProviderOhip`
- `hideFromDrugProfile`, `customNote`, `nonAuthoritative`
- `pickupDateTime`, `eTreatmentType`, `rxStatus`, `special_instruction`

**Measurement-specific losses**:
- `appointmentNo` - breaks encounter context linkage

**Prevention-specific losses**:
- `snomedId` - SNOMED CT coding lost
- `preventionExts` (OneToMany relationship) - extended data lost

---

## 11. Cached Data Model (JPA Entities)

The `caisi_integrator/dao/` package contains JPA entities that mirror the WS DTOs but are
annotated for database persistence. These used `@EmbeddedId` with composite primary keys
combining facility ID and entity ID.

The fallback storage path (`IntegratorFallBackManager`) actually bypassed these entities
and serialized the WS DTOs directly as XML blobs into `RemoteIntegratedDataCopy`. This
made the JPA entities partially redundant for the fallback use case, though they were
used for some direct queries.

---

## 12. Database Schema

### CAISI Schema Files

**Location**: `database/mysql/caisi/`

| File | Purpose | Tables |
|------|---------|--------|
| `initcaisi.sql` | Core CAISI schema | 120+ tables |
| `initcaisidata.sql` | Reference data | Seed data |
| `cookierevolverinit.sql` | Cookie/session module | Cookie tables |
| `init_cds_form_4_options.sql` | CDS form options | Form option tables |
| `init_functional_centres.sql` | Functional centres | Centre tables |
| `init_ocan_form_*.sql` | OCAN assessment forms | Assessment tables |
| `init_ticklerplus.sql` | Tickler+ features | Tickler extension tables |
| `populate_issue_icd*.sql` | ICD diagnosis codes | Diagnosis mapping tables |
| `reportrunnerview.sql` | Report runner views | Report view definitions |
| `updates/` | 157 patch files (2007-2011) | Schema evolution |

### Integrator-Specific Tables

These tables are ONLY used by the integrator:

```sql
-- Consent management
IntegratorConsent                    -- Digital consent records
IntegratorConsentComplexExitInterview -- Complex exit interview data
IntegratorConsentShareDataMap        -- Consent-to-data sharing mappings
IntegratorControl                    -- Integrator control/configuration

-- Data caching
cached_demographic                   -- Remote patient demographics
cached_demographic_allergy           -- Remote allergies
cached_demographic_appointment       -- Remote appointments
cached_demographic_drug              -- Remote medications
cached_demographic_form              -- Remote clinical forms
cached_demographic_issue             -- Remote clinical issues
cached_demographic_note              -- Remote clinical notes
cached_demographic_prevention        -- Remote immunizations
cached_facility                      -- Remote facility definitions
cached_program                       -- Remote program definitions
cached_provider                      -- Remote provider information
cached_measurement*                  -- Remote measurements + types + maps + extensions

-- Linking and communication
ClientLink                           -- Cross-facility patient linking
DemographicLink                      -- Demographic linking
DigitalSignature                     -- Digital signatures for consent
EventLog                            -- Integrator event logging
facility_message                     -- Inter-facility messages
HnrDataValidation                    -- HNR data validation
HomelessPopulationReport             -- HNR population reports
ImportLog                            -- Data import tracking
ProviderCommunication                -- Inter-provider messaging
Referral                            -- Cross-facility referrals
SiteUser                            -- Integrator site user accounts
SystemProperties                     -- Integrator system configuration
remote_integrated_data_copy          -- XML blob fallback storage

-- Push tracking
integrator_progress                  -- Push operation progress
integrator_progress_item             -- Push operation line items
```

### Tables in Main Schema

```sql
-- In oscarinit.sql / updates
remote_integrated_data_copy          -- Fallback XML blob storage
provider_facility                    -- Provider-to-facility mapping
```

### CAISI Tables That Are NOT Integrator-Specific

**IMPORTANT**: Many tables in `initcaisi.sql` are for the PMModule (Program Management),
case management, and clinical forms. These include:

- `admission`, `client_referral`, `program*` - Program management
- `casemgmt_*` - Case management
- `caisi_form*` - Clinical forms
- Mental health assessment form tables
- CDS form tables

These tables are NOT part of the integrator and should NOT be removed.

---

## 13. External Coupling Points

### Critical Coupling: Java Files Outside caisi_integrator Importing Integrator Classes

**58 Java files** in non-integrator packages import integrator classes. These are grouped
by risk level:

#### High Risk (Core Business Logic)

| File | Integrator Usage | Impact |
|------|-----------------|--------|
| `managers/DemographicManagerImpl.java` | Remote demographic fetching, consent | Core patient search |
| `managers/DemographicManager.java` (interface) | `getRemoteDemographic()`, `copyRemoteDemographic()` | Public API |
| `casemgmt/service/CaseManagementManagerImpl.java` | Remote notes, drugs, issues | Case management |
| `casemgmt/service/impl/DefaultNoteService.java` | Remote note integration | Note display |
| `casemgmt/web/CaseManagementView2Action.java` | Remote issue/note display | Encounter view |
| `casemgmt/web/NoteDisplayIntegrator.java` | Entire class is integrator-only | Note rendering |
| `prevention/PreventionData.java` | Remote prevention display (10+ imports) | Immunization tracking |
| `prescript/data/RxPatientData.java` | Remote drug/allergy data | Prescription display |

#### Medium Risk (Encounter Display Actions)

| File | Integrator Usage |
|------|-----------------|
| `encounter/pageUtil/EctDisplayAllergy2Action.java` | Remote allergy display with fallback |
| `encounter/pageUtil/EctDisplayRx2Action.java` | Remote medication display with fallback |
| `encounter/pageUtil/EctDisplayIssues2Action.java` | Remote issue display |
| `encounter/pageUtil/EctDisplayIssuesAction.java` | Legacy version of above |
| `encounter/pageUtil/EctDisplayResolvedIssues2Action.java` | Remote resolved issues |
| `encounter/pageUtil/EctDisplayResolvedIssuesAction.java` | Legacy version |
| `encounter/pageUtil/EctDisplayPrevention2Action.java` | Remote prevention data |
| `encounter/pageUtil/EctDisplayLabAction2.java` | Remote lab results |
| `encounter/pageUtil/EctDisplayLabAction22Action.java` | Remote lab results (Struts2) |
| `encounter/pageUtil/EctDisplayDocs2Action.java` | Remote document display |
| `encounter/pageUtil/EctDisplayMeasurements2Action.java` | Remote measurements |
| `encounter/data/EctFormData.java` | Remote form data |
| `encounter/oscarMeasurements/bean/EctMeasurementsDataBeanHandler.java` | Remote measurements |
| `encounter/oscarMeasurements/pageUtil/EctSetupDisplayHistory2Action.java` | Remote history |
| `encounter/oscarMeasurements/pageUtil/EctSetupHistoryIndex2Action.java` | Remote history |

#### Lower Risk (Utility/Admin)

| File | Integrator Usage |
|------|-----------------|
| `managers/MessengerIntegratorManager.java` | Entire class is integrator messaging |
| `managers/IntegratorPushManager.java` | Push operation management |
| `commn/web/IntegratorPush2Action.java` | Admin push UI |
| `documentManager/EDocUtil.java` | Remote document utility |
| `documentManager/actions/ManageDocument2Action.java` | Remote document management |
| `documentManager/actions/AddEditDocument2Action.java` | ConformanceTestHelper |
| `web/DemographicSearchHelper.java` | Remote demographic search |
| `rx/StaticScriptBean.java` | Remote drug display |
| `prescript/pageUtil/AllergyHelperBean.java` | Remote allergy display |
| `prescription/util/DrugrefUtil.java` | Drug reference with integrator |
| `form/FrmLabReq07Record.java` | Lab req with facility lookup |
| `form/FrmLabReq10Record.java` | Lab req with facility lookup |
| `lab/ca/all/web/LabDisplayHelper.java` | Remote lab display |
| `lab/ca/on/CommonLabResultData.java` | Ontario lab integration |
| `messenger/pageUtil/ImportDemographic2Action.java` | Import with integrator |
| `messenger/pageUtil/MsgViewMessage2Action.java` | Message view with integrator |
| `webserv/rest/DemographicService.java` | REST API demographic merge |
| `webserv/rest/PatientDetailStatusService.java` | Integrator status in REST |
| `utility/ContextStartupListener.java` | Startup initialization |

### Coupling Pattern

Nearly all external coupling follows the same pattern:

```java
if (loggedInInfo.getCurrentFacility().isIntegratorEnabled()) {
    try {
        if (!CaisiIntegratorManager.isIntegratorOffline(loggedInInfo.getSession())) {
            remoteData = CaisiIntegratorManager.getDemographicWs(loggedInInfo,
                loggedInInfo.getCurrentFacility()).getLinkedCachedDemographic*(...);
        }
    } catch (Exception e) {
        CaisiIntegratorManager.checkForConnectionError(loggedInInfo.getSession(), e);
    }

    if (CaisiIntegratorManager.isIntegratorOffline(loggedInInfo.getSession())) {
        remoteData = IntegratorFallBackManager.getRemote*(...);
    }
}
```

Since `Facility.integratorEnabled` defaults to `false`, all these code paths are dead code
in practice. The removal strategy is to delete the entire `if` block.

### Facility Model Fields

`commn/model/Facility.java` contains these integrator-related fields:

```java
private boolean integratorEnabled = false;
private String integratorUrl = null;
private String integratorUser = null;
private String integratorPassword = null;
private boolean enableIntegratedReferrals = true;
```

These map to database columns in the `Facility` table. They should be removed from the
model (and eventually the database columns dropped).

---

## 14. Struts Action Mappings

### Integrator-Specific Actions

From `struts.xml`:

```xml
<!-- Direct integrator action -->
<action name="integrator/IntegratorPush"
        class="io.github.carlos_emr.carlos.commn.web.IntegratorPush2Action"/>
```

### Actions Using caisicore/ JSPs

**NOTE**: These actions are NOT integrator-specific. They are admin/PMModule features that
happen to use JSPs in the `/caisicore/` directory:

```xml
<action name="SystemMessage" class="...www.SystemMessage2Action">
    <result name="view">/caisicore/SystemMessage.jsp</result>
    <result name="list">/caisicore/SystemMessageList.jsp</result>
    <result name="edit">/caisicore/SystemMessageForm.jsp</result>
</action>

<action name="DefaultEncounterIssue" class="...www.DefaultEncounterIssue2Action">
    <result name="list">/caisicore/DefaultEncounterIssueList.jsp</result>
    <result name="edit">/caisicore/DefaultEncounterIssueForm.jsp</result>
    <result name="editRemove">/caisicore/DefaultEncounterIssueFormRemove.jsp</result>
</action>

<action name="FacilityMessage" class="...www.OrganizationMessage2Action">
    <result name="view">/caisicore/FacilityMessage.jsp</result>
    <result name="list">/caisicore/FacilityMessageList.jsp</result>
    <result name="edit">/caisicore/FacilityMessageForm.jsp</result>
</action>

<action name="issueAdmin" class="...www.IssueAdmin2Action">
    <result name="list">/caisicore/issueAdminList.jsp</result>
    <result name="edit">/caisicore/issueAdminForm.jsp</result>
</action>
```

These actions (SystemMessage, DefaultEncounterIssue, FacilityMessage/OrganizationMessage,
IssueAdmin) live in the `www` package, not in `caisi_integrator`. Their JSPs should be
relocated from `caisicore/` to an appropriate location, but the actions themselves are
NOT integrator code.

### Login Flow Reference

```xml
<action name="login" class="...Login2Action">
    <result name="caisiPMM">/PMmodule/ProviderInfo.do</result>
</action>
```

The `caisiPMM` result routes to the PMModule provider info page when `default_pmm` is
enabled. This is a PMModule feature, NOT integrator-specific.

---

## 15. JSP Views

### Integrator-Specific Admin Pages

| JSP | Purpose |
|-----|---------|
| `admin/integratorStatus.jsp` | Integrator connection status dashboard |
| `admin/integratorPushStatus.jsp` | Push operation status |
| `admin/viewIntegratedCommunity.jsp` | View integrated community facilities |
| `admin/setIntegratorProperties.jsp` | Provider integrator preferences |
| `oscarPrevention/display_remote_prevention.jsp` | Remote prevention display |
| `appointment/copyRemoteDemographic.jsp` | Copy remote demographic from integrator |
| `demographic/copyLinkedDemographicInfoAction.jsp` | Copy linked demographic |
| `demographic/DiffRemoteDemographics.jsp` | Compare local vs remote demographics |

### JSPs With Integrator Conditional Blocks

These 20+ JSPs contain `isIntegratorEnabled()` checks with conditional blocks that should
be removed:

- `demographic/demographicsearchresults.jsp` - Remote search results
- `demographic/demographicsearch2apptresults.jsp` - Appointment search
- `demographic/demographicappthistory.jsp` - Appointment history
- `demographic/demographiccontrol.jsp` - Demographic control panel
- `demographic/demographiceditdemographic.jsp` - Edit demographic
- `demographic/followUp.jsp` - Follow-up with remote
- `casemgmt/newEncounterHeader.jsp` - Encounter header
- `casemgmt/prescriptions.jsp` - Prescription view
- `oscarRx/SearchDrug.jsp` - Drug search
- `oscarRx/ListDrugs.jsp` - Drug list
- `oscarRx/StaticScript.jsp` - RX JavaScript
- `lab/CA/ALL/labDisplay.jsp` - Lab display
- `lab/CA/ON/labValues.jsp` - Ontario lab values
- `form/formlabreq07.jsp` - Lab requisition
- And more...

### caisicore/ Directory

The `/webapp/caisicore/` directory contains 14 JSPs. These are used by admin actions
(SystemMessage, DefaultEncounterIssue, FacilityMessage, IssueAdmin) that are NOT
integrator-specific. During removal, these JSPs should be relocated to `/webapp/admin/`
and the struts.xml result paths updated.

---

## 16. Scheduled Jobs

### IntegratorLocalStoreUpdateJob

- **Trigger**: Configured via `oscar_mcmaster.properties` / runtime config
- **Purpose**: Periodically pulls data from integrator server
- **Delegates to**: `CaisiIntegratorUpdateTask`

### CaisiIntegratorUpdateTask

- **Size**: 2,757 lines (largest integrator class)
- **Purpose**: Full data synchronization cycle
- **Operations**:
  1. Push demographics to integrator
  2. Push medications, allergies, preventions
  3. Push clinical notes and issues
  4. Push consent states
  5. Push provider communications
  6. Pull cached data from integrator
  7. Track progress in IntegratorProgress tables

### IntegratorFileLogUpdateJob

- **Purpose**: File-based logging of integrator operations
- **Writes**: IntegratorFileHeader + IntegratorFileFooter format

### Non-Integrator Scheduled Jobs (in applicationContextCaisi.xml)

- `scheduledErProgramDischargeTask` - ER program discharge (runs every 5 minutes)
- `scheduledAnonymousClientDischargeTask` - Anonymous client discharge (runs hourly)

These are PMModule jobs, NOT integrator jobs, and MUST be preserved.

---

## 17. Security Architecture

### WS-Security

`AuthenticationOutWSS4JInterceptorForIntegrator`:
- Attaches WSS4J username/password token to outgoing SOAP requests
- Credentials from `Facility.integratorUser` / `Facility.integratorPassword`
- Applied to CXF client conduit via interceptor chain

### Consent Model

- `IntegratorConsent` - stores patient consent for data sharing
- `IntegratorConsentComplexExitInterview` - complex exit interview questions
- `ConsentState` enum: `GIVEN`, `REVOKED`, `NOT_SET`
- Digital signatures via `DigitalSignature` table

### Security Concerns

- Integrator password stored in Facility table (potential cleartext)
- WS-Security over HTTPS only (no mutual TLS)
- Consent model allows cross-facility data access
- No audit trail on remote data access (PIPEDA gap)

---

## 18. Data Completeness Analysis

### Data Types Synced (10 types, partial coverage)

| Type | WS DTO Fields | Domain Fields | Coverage | Key Losses |
|------|---------------|---------------|----------|------------|
| Demographics | ~30 | ~50 | 60% | Address details, rostering |
| Medications | 37 | 50+ | 74% | Written date, outside provider, privacy flags |
| Allergies | 17 | 30+ | 57% | Archived status, audit trail |
| Measurements | 9 | 11 | 82% | Appointment context |
| Preventions | 9 | 12+ | 75% | SNOMED coding, extended attributes |
| Notes | 11 | 20+ | 55% | Encryption, issue relationships |
| Issues | 7 | 15+ | 47% | Clinical metadata |
| Appointments | 16 | 30+ | 53% | Billing, creator, urgency |
| Documents | 19 | 30+ | 63% | Review workflow |
| Lab Results | 4 (blob) | 40+ | 10% | Almost everything |

### Data Types NOT Synced At All

| Missing Type | Models in EMR | Impact |
|-------------|---------------|--------|
| Consultations/Referrals | 8 models | No referral coordination |
| Lab Orders & Routing | 15 models | No structured lab data |
| Billing (beyond items) | 14 models | No payment tracking |
| Document Workflow | 6 models | No review/approval state |
| Case Management Issues | 5 models | Only notes, not structured diagnoses |
| Provider details | 15+ fields | Minimal provider info (5 fields) |

### Unimplemented Stubs

In `IntegratorLocalStoreUpdateJob.java`:

```java
// These don't exist
// IntegratorFallBackManager.saveMeasurements(demographicNo);     // Not being displayed yet
// IntegratorFallBackManager.saveDxresearchs(demographicNo);      // Not being displayed yet
// IntegratorFallBackManager.saveBillingItems(demographicNo);     // Not being displayed yet
// IntegratorFallBackManager.saveEforms(demographicNo);           // Not being displayed yet
```

---

## 19. Known Limitations

1. **No real-time sync** - Batch-only via scheduled jobs
2. **No conflict resolution** - Last-write-wins semantics
3. **No bidirectional workflow** - One-way push/pull only
4. **Incomplete data model** - ~20% of clinical data types, ~60% of fields
5. **No audit trail preservation** - `lastUpdateUser`/`lastUpdateDate` dropped
6. **Triple object duplication** - Maintenance burden, no conversion layer
7. **Fragile offline mode** - XML blob storage, no structured fallback queries
8. **No structured lab sync** - HL7 blobs only, no discrete results
9. **No billing integration** - Items only, no payment/correction lifecycle
10. **Server dependency** - External integrator server required (now gone)

---

## 20. Removal Rationale

### Why Remove

1. **Dead code**: The integrator server no longer exists. `integratorEnabled` defaults to
   `false`. All integrator code paths are unreachable in practice.

2. **Security surface reduction**: 350+ classes, 290+ generated SOAP stubs, WS-Security
   configuration, and credential storage represent unnecessary attack surface.

3. **Maintenance burden**: Triple data representation (WS DTO, JPA entity, domain model)
   with no conversion layer creates confusion for developers.

4. **Build impact**: Hundreds of classes that compile, get scanned by CodeQL, and
   increase build times with zero value.

5. **Incomplete by design**: Even when operational, the integrator synced only ~20% of
   clinical data types with ~60% field coverage. A modern replacement would use FHIR R4
   (HAPI FHIR 5.4.0 is already in the dependency tree).

### What to Preserve

- **PMModule** (Program Management) - active, not integrator-specific
- **Non-integrator beans** in `applicationContextCaisi.xml` (measurement flowsheets,
  security manager, forms DAO, population reports, scheduled tasks, component scans)
- **caisicore/ JSPs** used by admin actions (relocate to `/admin/`)
- **Login flow** `caisiPMM` result (PMModule feature)
- **CAISI database tables** for program management, case management, clinical forms

---

## Appendix A: File Count Summary

| Category | Count | Location |
|----------|-------|----------|
| WS client stubs (JAXB generated) | ~290 | `caisi_integrator/ws/` |
| Cached entity DAOs | ~45 | `caisi_integrator/dao/` |
| Utility classes | 8 | `caisi_integrator/util/` |
| Manager classes | 16 | `PMmodule/caisi_integrator/` |
| Common model/DAO | ~12 | `commn/model/`, `commn/dao/` |
| Support classes | ~5 | `caisi/`, `commn/web/`, `casemgmt/web/` |
| **Total integrator-only files** | **~376** | |
| External Java files with imports | 58 | Various packages |
| JSP files with integrator code | 52+ | `webapp/` |
| Integrator-specific JSPs | 8 | `webapp/admin/` |
| Test files | 7 | `src/test/` |
| Database SQL files | 168 | `database/mysql/caisi/` |

## Appendix B: Properties Keys

### oscar_mcmaster.properties / oscar.properties

```properties
# Integrator configuration
INTEGRATOR_UPDATE_PERIOD=           # Sync interval
INTEGRATOR_LOCAL_STORE_DAYS=        # How long to keep cached data
```

### Localization Keys (oscarResources_*.properties)

```properties
provider.integratorPreferences.preferences=Integrator Preferences
provider.integratorPreferences.chooseDataSets=Choose Data Sets
oscarEncounter.integrator.NA=Integrator not available
oscarEncounter.integrator.outOfSync=Community not synced
admin.admin.caisi=CAISI
admin.admin.integratorPush=Integrator Push Manager
web.record.details.integrator=Integrator
provider.pref.caisi.*=CAISI preference settings
```

## Appendix C: Hibernate Mapping Files

The integrator entities use JPA annotations (not HBM XML files) for persistence. However,
the `Facility.hbm.xml` includes the integrator fields (`integratorEnabled`,
`integratorUrl`, `integratorUser`, `integratorPassword`). These column mappings must be
removed from the HBM file when the `Facility.java` model fields are removed.
