# CARLOS EMR: Migration Plan — MySQL to Medplum FHIR R4 Backend

> **Status**: Phase 0 — Foundation  
> **Created**: 2026-04-02  
> **Branch**: `claude/migrate-medplum-fhir-BQpfc`

## Executive Summary

This document describes the phased migration of CARLOS EMR from a MySQL/Hibernate
data store to a self-hosted [Medplum](https://www.medplum.com/) FHIR R4 server as
the primary backend for clinical data. The migration preserves MySQL for
non-clinical infrastructure (security, audit, configuration) while moving ~85
clinical entities to standards-based FHIR resources.

### Why Medplum

- **Open source** (Apache 2.0), self-hostable, no vendor lock-in
- **Full FHIR R4 compliance** with SMART on FHIR support
- **Built-in subscriptions** for event-driven workflows (lab routing, inbox)
- **TypeScript/Node.js server** with PostgreSQL + Redis backing store
- **Canadian healthcare compatible** — supports custom profiles and extensions
- Replaces 373 custom DAOs with a standards-based API

### What Changes, What Stays

| Layer | Before | After |
|-------|--------|-------|
| Clinical data store | MySQL + Hibernate | Medplum FHIR R4 |
| Clinical data access | 373 custom DAOs | FHIR REST API via HAPI client |
| Security/auth | MySQL (Sec* tables) | MySQL (unchanged) |
| Audit logs | MySQL (OscarLog) | MySQL (unchanged) |
| App config | MySQL (Property, UserProperty) | MySQL (unchanged) |
| Program management | MySQL (PMmodule) | MySQL (unchanged) |
| Billing infrastructure | MySQL | MySQL (with FHIR Claim export) |
| FHIR version | DSTU3 (34 files) | R4 |

---

## Architecture

### Current State

```
┌──────────────────────────────┐
│         CARLOS EMR           │
│                              │
│  JSP/Struts → Manager → DAO │
│                         │    │
│                    Hibernate  │
│                         │    │
└─────────────────────────┼────┘
                          │
                    ┌─────▼─────┐
                    │  MariaDB  │
                    └───────────┘
```

### Target State

```
┌────────────────────────────────────────────────┐
│                  CARLOS EMR                     │
│                                                 │
│  JSP/Struts → Manager → Repository (interface)  │
│                              │            │     │
│                    ┌─────────┘            │     │
│                    │                      │     │
│              FhirRepository         JpaRepository│
│              (clinical)            (non-clinical)│
│                    │                      │     │
└────────────────────┼──────────────────────┼─────┘
                     │                      │
              ┌──────▼───────┐       ┌──────▼─────┐
              │   Medplum    │       │  MariaDB   │
              │   Server     │       │            │
              │  ┌────────┐  │       └────────────┘
              │  │Postgres │  │
              │  │Redis    │  │
              └──────────────┘
```

### DevContainer Layout

```yaml
services:
  carlos:          # Tomcat 11 (existing) — port 8080
  db:              # MariaDB (existing) — port 3306
  drugref:         # DrugRef (existing) — port 8180
  medplum-server:  # Medplum FHIR server — port 8103
  medplum-postgres:# PostgreSQL for Medplum — port 5432
  medplum-redis:   # Redis for Medplum — port 6379
```

---

## Entity Classification

### Tier 1 — Direct FHIR Mapping (~60 entities)

These entities have natural FHIR R4 resource equivalents.

#### Patient Domain
| CARLOS Entity | FHIR R4 Resource | Fields | Notes |
|---|---|---|---|
| Demographic | Patient | 84 | HIN as identifier slice, gender/pronoun extensions |
| DemographicExt | Patient (extensions) | 8 | PHU, ethnicity, consent, employment |
| DemographicContact | Patient.contact | 18 | Emergency contacts, SDM, care team |
| DemographicCust | Patient (extensions) | 6 | Nurse, resident, midwife, alerts |
| DemographicMerged | Patient (link) | — | Patient merge history |

#### Provider Domain
| CARLOS Entity | FHIR R4 Resource | Notes |
|---|---|---|
| Provider | Practitioner | License types (CPSO, CNORN, OCP, CMO) |
| ProfessionalSpecialist | Practitioner | External specialists |
| Clinic | Organization | Name, address, phone, fax |
| ClinicLocation | Location | Sub-locations |

#### Medications
| CARLOS Entity | FHIR R4 Resource | Fields | Notes |
|---|---|---|---|
| Drug | MedicationRequest | 73 | ATC code, dosage, frequency, duration, refills, route |
| Prescription | MedicationRequest (group) | 9 | Script metadata, digital signature |
| DrugDispensing | MedicationDispense | 11 | Quantity, dispensing provider |
| DrugReason | MedicationRequest.reasonCode | 10 | ICD-9/10 indications |
| DrugProduct | Medication | 7 | Lot number, expiry, product code |

#### Allergies
| CARLOS Entity | FHIR R4 Resource | Fields | Notes |
|---|---|---|---|
| Allergy | AllergyIntolerance | 31 | Severity (1-3), onset, ATC, drug/non-drug |

#### Immunizations
| CARLOS Entity | FHIR R4 Resource | Fields | Notes |
|---|---|---|---|
| Prevention | Immunization | 16+ ext | SNOMED code, lot, route, dose, refusal tracking |
| PreventionExt | Immunization (extensions) | 5 | Lot, route, dose, manufacturer |

#### Lab Results
| CARLOS Entity | FHIR R4 Resource | Notes |
|---|---|---|
| Lab | DiagnosticReport | Accession, lab name, ordering provider |
| LabTest | Observation | Code, value, units, reference range, abnormal flag |
| LabTestResults | Observation | Result lines, abnormal indicators |
| LabPatientPhysicianInfo | DiagnosticReport (metadata) | Accession, ordering physician, status |
| Hl7TextInfo | DiagnosticReport (metadata) | Result status, discipline, priority |
| MdsOBR | DiagnosticReport (OBR) | Order/result header |
| MdsOBX | Observation (OBX) | Result value, abnormal flags |
| PatientLabRouting | DiagnosticReport.subject | Links labs to patients |

#### Measurements / Vitals
| CARLOS Entity | FHIR R4 Resource | Notes |
|---|---|---|
| Measurement | Observation | BP, WT, HT, HR, TEMP — immutable after creation |
| MeasurementsExt | Observation (extensions) | Units, reference ranges, flags |
| MeasurementType | ObservationDefinition | Type code, display name |

#### Documents
| CARLOS Entity | FHIR R4 Resource | Notes |
|---|---|---|
| Document | DocumentReference | Type, class, content date, abnormal flag |
| DocumentReview | DocumentReference (extension) | Reviewer, review date |
| DocumentStorage | DocumentReference.content | Binary content (MEDIUMBLOB) |

#### Consultations / Referrals
| CARLOS Entity | FHIR R4 Resource | Notes |
|---|---|---|
| ConsultationRequest | ServiceRequest | 30+ fields: reason, urgency, clinical info |
| ConsultationRequestExt | ServiceRequest (extensions) | Key-value extended attributes |
| ConsultationResponse | DiagnosticReport / Communication | Examination, impression, plan |
| ConsultationServices | HealthcareService | Service types with specialist lists |
| ConsultDocs / ConsultResponseDoc | DocumentReference | Attached documents |

#### Clinical Notes
| CARLOS Entity | FHIR R4 Resource | Notes |
|---|---|---|
| CaseManagementNote | Encounter + ClinicalImpression | Clinical notes, signatures, encryption |
| CaseManagementNoteExt | Encounter (extensions) | Extended note properties |
| CaseManagementNoteLink | Encounter (references) | Links to issues/conditions |
| CaseManagementIssue | Condition | Clinical issues/problems |

#### Clinical Workflow (Ticklers, Messages, Inbox)
| CARLOS Entity | FHIR R4 Resource | Notes |
|---|---|---|
| Tickler | Task | Patient, provider, priority, status, service date |
| TicklerComment | Task.note | Provider comments/audit trail |
| TicklerUpdate | Task (provenance) | Status change history |
| TicklerCategory | Task.code | Classification (Lab Follow-up, Referral) |
| TicklerLink | Task.focus | Links to labs, documents, encounters |
| MessageTbl | Communication | Subject, message, sender, recipient |
| MessageList | Communication (status) | Read/new/sent/deleted per provider |
| InboxItem | Task + DiagnosticReport ref | Lab results awaiting review |
| ProviderInboxItem | Task | Lab routing to providers, ack status |
| IncomingLabRules | Subscription | Auto-forwarding rules |

#### eForms
| CARLOS Entity | FHIR R4 Resource | Notes |
|---|---|---|
| EFormData | QuestionnaireResponse | Patient-linked completed form data |
| EFormValue | QuestionnaireResponse.item | Individual field values |
| Flowsheet | Questionnaire | Flowsheet templates |

#### Appointments
| CARLOS Entity | FHIR R4 Resource | Notes |
|---|---|---|
| Appointment | Appointment | Date, provider, patient, reason, status |
| AppointmentArchive | Appointment | Historical appointments |

### Tier 2 — Canadian Healthcare Extensions (~25 entities)

These map to FHIR with Canadian-specific profiles and extensions.

| CARLOS Entity | FHIR R4 Resource | Notes |
|---|---|---|
| BillingONCHeader1 | Claim | Ontario OHIP submission |
| BillingONItem | Claim.item | Service code, fee, diagnosis |
| BillingONPayment | ClaimResponse | Payment/refund amounts |
| BillingOnItemPayment | ClaimResponse.item | Line-level payment |
| Billing | Claim | General claim record |
| BillingDetail | Claim.item | Service code, diagnostic code |
| TeleplanS21 | ClaimResponse | BC MSP remittance header |
| TeleplanS00 | ClaimResponse.item | BC MSP service detail |
| TeleplanS22 | ClaimResponse.total | BC provider batch summary |
| TeleplanS23 | ClaimResponse.adjudication | BC adjustment/reversal |
| BillingPrivateTransactions | PaymentNotice | Private pay records |
| BillingService | ChargeItemDefinition | Fee schedule master |

### Tier 3 — Stays in MySQL (~140 entities)

| Category | Count | Examples |
|---|---|---|
| Security & Auth | ~15 | Security, SecRole, SecPrivilege, SecObjectName, SecurityToken |
| Program Management | ~15 | Program, ProgramTeam, ProgramProvider, ProgramAccess |
| System Configuration | ~10 | Property, UserProperty, SystemMessage, ScheduleTemplate |
| Audit & Logging | ~15 | OscarLog, HashAudit, EmailLog, FaxClientLog |
| Billing Infrastructure | ~10 | BillingONExt, BillingONEAReport, BillingONHeader (batch) |
| EForm Templates | ~5 | EForm (template definition), EFormGroup |
| Decision Support | ~9 | DSGuideline, DSCondition, DSConsequence |
| HRM | ~9 | HRMDocument, HRMCategory, HRMDocumentComment |
| Reporting | ~11 | Report, ReportConfig, ReportTemplate |
| Other infrastructure | ~40+ | Lookups, DTOs, display helpers, composite keys |

### Shared / Reference (~100 entities)

REST transfer objects (101 `*To1` classes in `webserv/rest/to/`), lookup tables,
and enums that serve both stores.

---

## Phased Implementation

### Phase 0 — Foundation (current phase)

**Goal**: Set up infrastructure with zero production impact.

**Deliverables**:
1. Self-hosted Medplum in devcontainer (server + PostgreSQL + Redis)
2. HAPI FHIR R4 client dependency (upgrade from DSTU3)
3. `MedplumFhirClient` wrapper service for auth, CRUD, search
4. `ClinicalRepository<T>` abstraction interfaces
5. This migration plan document

**Risk**: None — all existing behavior unchanged.

### Phase 1 — Dual-Write Core Entities

**Goal**: Write clinical data to both MySQL and Medplum simultaneously.

**Entities** (5):
- Patient ← Demographic
- Practitioner ← Provider
- Immunization ← Prevention
- AllergyIntolerance ← Allergy
- Organization ← Clinic

**Approach**:
1. Implement `FhirRepository` for each entity (FHIR R4 CRUD via Medplum)
2. Implement `DualWriteRepository` that delegates to both MySQL DAO and FhirRepository
3. Wire managers to use DualWriteRepository
4. Reads still come from MySQL

**Validation**: Reconciliation job compares MySQL and Medplum state nightly.

**Risk**: Low — MySQL remains source of truth for all reads.

### Phase 2 — Read Switch (Core Entities)

**Goal**: Flip reads to Medplum for Phase 1 entities.

**Approach**:
1. Implement FHIR search query equivalents for DAO finder methods
2. Add feature flag per entity: `fhir.read.patient=true`
3. Switch reads to Medplum with MySQL fallback
4. Monitor latency and correctness

**Risk**: Medium — first time Medplum is in the read path.

### Phase 3 — Expand Entity Coverage

**Goal**: Migrate next tier of entities using the same dual-write → read-switch cycle.

**Entities** (~20):
- Appointment → FHIR Appointment
- Drug/Prescription → MedicationRequest + MedicationDispense
- Lab results → DiagnosticReport + Observation
- Measurements → Observation
- Documents → DocumentReference
- CaseManagementNote → Encounter + ClinicalImpression
- ConsultationRequest → ServiceRequest
- Tickler → Task
- Messages → Communication
- Inbox → Task

**Each entity follows**: implement mapping → dual-write → validate → read-switch.

**Risk**: Medium — more complex mappings, larger blast radius.

### Phase 4 — Canadian Healthcare Profiles

**Goal**: Migrate billing and provincial data with FHIR Canadian profiles.

**Entities** (~25):
- Ontario OHIP billing → Claim / ClaimResponse
- BC Teleplan billing → Claim / ClaimResponse
- Provincial identifiers → Patient.identifier slices
- OLIS/DHIR integrations → point at Medplum

**Risk**: High — provincial billing has deep MySQL coupling and no clean FHIR standard.

### Phase 5 — MySQL Retirement (per entity)

**Goal**: Drop dual-write; Medplum becomes source of truth.

**Approach**:
1. Disable MySQL writes for migrated entities (one at a time)
2. MySQL becomes read-only archive
3. Eventually drop MySQL tables for fully migrated entities
4. Non-FHIR entities remain in MySQL permanently

**Risk**: High — point of no return per entity.

---

## DSTU3 → R4 Migration

The existing 34 FHIR files in `integration/fhir/` use HAPI FHIR DSTU3.
Medplum requires R4. Key changes:

| Aspect | DSTU3 | R4 |
|---|---|---|
| FhirContext | `FhirContext.forDstu3()` | `FhirContext.forR4()` |
| Package | `org.hl7.fhir.dstu3.model` | `org.hl7.fhir.r4.model` |
| Immunization.status | `completed` | `completed` (same) |
| Immunization.notGiven | boolean field | `status = not-done` |
| Patient.animal | removed in R4 | N/A |
| Consent | draft resource | normative |

**Approach**: Create new R4 model classes alongside DSTU3 (don't break DHIR
integration during transition). Deprecate DSTU3 classes after DHIR is migrated.

---

## DevContainer Infrastructure

### New Containers

```yaml
# Medplum PostgreSQL (backing store for Medplum server)
medplum-postgres:
  image: postgres:16
  environment:
    POSTGRES_DB: medplum
    POSTGRES_USER: medplum
    POSTGRES_PASSWORD: medplum
  ports:
    - "5432:5432"
  volumes:
    - medplum-postgres-data:/var/lib/postgresql/data

# Medplum Redis (job queue and cache)
medplum-redis:
  image: redis:7-alpine
  ports:
    - "6379:6379"

# Medplum FHIR Server
medplum-server:
  image: medplum/medplum-server:latest
  ports:
    - "8103:8103"
  depends_on:
    medplum-postgres:
      condition: service_healthy
    medplum-redis:
      condition: service_started
  environment:
    MEDPLUM_PORT: 8103
    MEDPLUM_DATABASE_HOST: medplum-postgres
    MEDPLUM_DATABASE_PORT: 5432
    MEDPLUM_DATABASE_DBNAME: medplum
    MEDPLUM_DATABASE_USERNAME: medplum
    MEDPLUM_DATABASE_PASSWORD: medplum
    MEDPLUM_REDIS_HOST: medplum-redis
    MEDPLUM_REDIS_PORT: 6379
```

### Resource Requirements

| Container | Memory | CPU | Disk |
|---|---|---|---|
| medplum-server | 512M | 1 | minimal |
| medplum-postgres | 512M | 1 | 1G+ (grows) |
| medplum-redis | 128M | 0.5 | minimal |
| **Total new** | **~1.2G** | **2.5** | **~1G** |

---

## Repository Abstraction Layer

The key enabler for gradual migration. Managers call repository interfaces
instead of DAOs directly.

```java
/**
 * Generic clinical data repository that abstracts the backing store.
 * Implementations can target MySQL (via existing DAOs), Medplum FHIR,
 * or both (dual-write).
 */
public interface ClinicalRepository<T, ID> {

    Optional<T> findById(ID id);

    List<T> findAll(int offset, int limit);

    T save(T entity);

    void delete(ID id);

    long count();
}
```

Each migrated entity gets three implementations:
1. `JpaClinicalRepository` — delegates to existing DAO (current behavior)
2. `FhirClinicalRepository` — delegates to Medplum via HAPI FHIR client
3. `DualWriteClinicalRepository` — writes to both, reads from configurable source

---

## Key Decisions

### 1. FHIR Version: R4
Medplum requires R4. The existing DSTU3 code (34 files for DHIR/Ontario) will be
migrated to R4 as part of Phase 0/1.

### 2. Self-Hosted Medplum
Running in devcontainer alongside existing services. No cloud dependency.
PIPEDA/data residency concerns eliminated.

### 3. What Stays in MySQL
Security, audit, program management, app config, billing internals, and ~140
other non-clinical entities. These are poor FHIR fits and forcing them would
add complexity without benefit.

### 4. Dual-Write Before Read-Switch
Every entity goes through dual-write validation before reads are switched.
This catches mapping bugs before they affect users.

### 5. Feature Flags Per Entity
Each entity's read source is controlled by a property flag, allowing
per-entity rollback without code changes.

### 6. Repository Abstraction
Managers are decoupled from storage implementation. This is the critical
enabler — without it, migrating means rewriting 99 managers.

---

## Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Medplum performance under load | Slow clinical reads | Benchmark in Phase 2 before expanding |
| FHIR mapping data loss | Clinical data gaps | Reconciliation jobs compare both stores |
| DSTU3→R4 breaks DHIR | Ontario immunization reporting fails | Parallel R4 classes, don't remove DSTU3 until DHIR migrated |
| DevContainer resource pressure | Slow builds, OOM | Memory limits on Medplum containers |
| Incomplete FHIR mapping for Canadian data | Billing features break | Keep billing in MySQL, export to FHIR only |
| Manager→DAO coupling too deep | Abstraction layer leaks | Start with cleanest entities (Patient, Practitioner) |

---

## Success Metrics

| Phase | Metric | Target |
|---|---|---|
| 0 | Medplum server running in devcontainer | Health check passes |
| 0 | HAPI FHIR R4 client can CRUD a Patient | Integration test green |
| 1 | 5 entities dual-writing to Medplum | Zero reconciliation errors for 7 days |
| 2 | Reads from Medplum for 5 entities | Latency < 2x MySQL, zero data errors |
| 3 | 20+ entities on Medplum | All existing tests pass |
| 5 | MySQL tables dropped for migrated entities | Clean shutdown |
