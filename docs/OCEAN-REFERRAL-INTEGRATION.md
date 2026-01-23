# OCEAN Referral Integration Documentation

> **Document Version**: 1.0
> **Last Updated**: 2026-01-22
> **Status**: Assessment Complete

## Table of Contents

1. [Overview](#overview)
2. [OCEAN Platform Technical Information](#ocean-platform-technical-information)
3. [Current OpenO Implementation](#current-openo-implementation)
4. [Comparison with Other Forks](#comparison-with-other-forks)
5. [Delta Assessment](#delta-assessment)
6. [Potential Recommended Enhancements](#potential-recommended-enhancements)
7. [Implementation Specifications](#implementation-specifications)
8. [Incoming OCEAN Consultations](#incoming-ocean-consultations)
9. [References](#references)

---

## Overview

### What is OCEAN?

OCEAN (Online Care Enhancement and Navigation) is a healthcare integration platform provided by CognisantMD that enables electronic referrals and consultations between healthcare providers in Canada. It is distinct from:

- **eReferral**: General doctor-to-doctor referral letters/requests (internal EMR feature)
- **CAISI Integrator**: Inter-facility referrals between OSCAR/OpenO EMR instances

### OCEAN Integration Purpose

OCEAN integration allows OpenO EMR users to:
- Send full consultations with clinical attachments to specialists via the OCEAN network
- Attach documents, labs, eForms, and HRM records to outgoing consultations
- Retrieve attachment data for transmission to OCEAN's external servers
- Track referral status via the Ocean toolbar

### Current Limitation

**No OSCAR-based EMR fork (Oscar 19, OscarPro, or OpenO) currently supports automated incoming OCEAN consultations.** Specialists receiving consultations via OCEAN must manually enter responses in their EMR or use the OCEAN web portal.

---

## OCEAN Platform Technical Information

### Service Overview

OCEAN is a product of CognisantMD, providing eReferral and eConsult services across Canada with strong adoption in Ontario. The platform facilitates electronic referrals between primary care providers and specialists through integration with various EMR systems.

### API Standards

OCEAN offers two sets of APIs for eReferral integration:

| API Type | Status | Recommended For |
|----------|--------|-----------------|
| **HL7 FHIR APIs** | Active, Standards-based | New integrations |
| **Open APIs** | Deprecated (as of Nov 2022) | Legacy only |

**FHIR API Versions:**
- **v0.10.0**: Based on Ontario HL7 FHIR eReferral Implementation Guide
- **v0.11.1**: Newer version with OTN eConsult support (recommended for new integrators)

### Authentication & Security

- **OAuth2-based authentication** with site-specific Client ID and secret
- **Shared Encryption Key (SEK)** required for site configuration
- Standard 401/403 response codes for authorization errors
- IP whitelist for incoming referral notifications (Production: ocean.cognisantmd.com)

### Supported EMRs (Official)

As of 2026, the following EMRs have official OCEAN integration support:
- TELUS PS Suite
- TELUS CHR
- OSCAR Pro (Well Health)
- Accuro/QHR

### Integration Capabilities

The OCEAN integration enables:
1. **Outbound eReferrals** - Send consultations with attachments to specialists
2. **Status Updates** - Receive referral status changes (accepted, booked, cancelled)
3. **Patient Demographics** - Update patient core demographics in OCEAN
4. **Referral Communication** - Add instructions and attachments to referrals
5. **Auto-import** - Receive eReferral records and attachments as PDFs via Cloud Connect

### Cloud Connect Features

Ocean Cloud Connect provides:
- Automatic import of Website Form submissions
- File attachments from Patient Authenticated Website Form submissions
- eReferral/eConsult records with file attachments (PDF format)
- Automatic patient chart creation for new patients (configurable)

### Attachment Limitations

**Unsupported file types in OSCAR Pro/OCEAN integration:**
- `.tiff`, `.tif` (TIFF images)
- `.mp3`, `.mp4` (audio/video)
- `.txt` (plain text)

**Supported attachment sources in OSCAR Pro:**
- Documents
- HRMs (Hospital Report Manager records)
- Labs
- eForms
- eDocs
- Smart Encounter Forms

### Provincial Integration

**Ontario:**
- Integration with OTN eConsult via v0.11.1 FHIR APIs
- Part of Ontario eServices Program
- Integration with OntarioMD's HRM for consultation reports

### Key Support Resources

- [API Integrations Hub](https://support.cognisantmd.com/hc/en-us/sections/360006922672-API-Integrations)
- [HL7 FHIR eReferral Integration Setup Guide](https://support.cognisantmd.com/hc/en-us/articles/360058125332-HL7-FHIR-eReferral-Integration-Setup-Guide)
- [HL7 FHIR eReferral API Implementation Guidance](https://support.cognisantmd.com/hc/en-us/articles/360060603172-HL7-FHIR-eReferral-API-Implementation-Guidance)
- [OSCAR & Ocean Support](https://support.cognisantmd.com/hc/en-us/sections/115000889632-OSCAR-Ocean)
- [Ocean API Integration Updates](https://support.cognisantmd.com/hc/en-us/sections/10479650547213-Ocean-API-Integration-Updates)

---

## Current OpenO Implementation

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    OpenO OCEAN Integration                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────┐    ┌──────────────────┐                   │
│  │ ConsultationForm │───▶│  ERefer2Action   │                   │
│  │   Request.jsp    │    │   (Struts2)      │                   │
│  └──────────────────┘    └────────┬─────────┘                   │
│                                   │                              │
│                    ┌──────────────┴──────────────┐              │
│                    ▼                             ▼              │
│         ┌──────────────────┐          ┌──────────────────┐     │
│         │ attachOcean      │          │ editOcean        │     │
│         │ EReferralConsult │          │ EReferralConsult │     │
│         └────────┬─────────┘          └────────┬─────────┘     │
│                  │                              │                │
│                  ▼                              ▼                │
│         ┌──────────────────────────────────────────────┐       │
│         │           EReferAttachment (DB)              │       │
│         │  - id, demographic_no, created, archived     │       │
│         └──────────────────────────────────────────────┘       │
│                              │                                  │
│                              ▼                                  │
│         ┌──────────────────────────────────────────────┐       │
│         │         EReferAttachmentData (DB)            │       │
│         │  - erefer_attachment_id, lab_id, lab_type    │       │
│         └──────────────────────────────────────────────┘       │
│                              │                                  │
│                              ▼                                  │
│         ┌──────────────────────────────────────────────┐       │
│         │    ConsultationManager.getEReferAttachments  │       │
│         │         (Renders attachments to PDF)         │       │
│         └──────────────────────────────────────────────┘       │
│                              │                                  │
│                              ▼                                  │
│         ┌──────────────────────────────────────────────┐       │
│         │     ConsultationWebService (REST API)        │       │
│         │     GET /ws/rs/consults/getEReferAttachments │       │
│         └──────────────────────────────────────────────┘       │
│                              │                                  │
│                              ▼                                  │
│                    ┌─────────────────┐                         │
│                    │  OCEAN Network  │                         │
│                    │   (External)    │                         │
│                    └─────────────────┘                         │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### File Inventory

#### Java Classes

| File | Path | Purpose |
|------|------|---------|
| `ERefer2Action.java` | `ca.openosp.openo.encounter.oceanEReferal.pageUtil` | Struts2 action handling OCEAN attachment requests |
| `OceanEReferralAttachmentUtil.java` | `ca.openosp.openo.encounter.oceanEReferal.pageUtil` | Utility for attachment lifecycle management |
| `EReferAttachment.java` | `ca.openosp.openo.commn.model` | JPA entity for attachment records |
| `EReferAttachmentData.java` | `ca.openosp.openo.commn.model` | JPA entity for individual attachment items |
| `EReferAttachmentDataCompositeKey.java` | `ca.openosp.openo.commn.model` | Composite key for attachment data |
| `EReferAttachmentDao.java` | `ca.openosp.openo.commn.dao` | DAO interface |
| `EReferAttachmentDaoImpl.java` | `ca.openosp.openo.commn.dao` | DAO implementation |
| `EReferAttachmentDataDao.java` | `ca.openosp.openo.commn.dao` | Attachment data DAO interface |
| `EReferAttachmentDataDaoImpl.java` | `ca.openosp.openo.commn.dao` | Attachment data DAO implementation |
| `ConsultationManagerImpl.java` | `ca.openosp.openo.managers` | Contains `getEReferAttachments()` method |
| `ConsultationWebService.java` | `ca.openosp.openo.webserv.rest` | REST endpoint for attachment retrieval |

#### JavaScript Files

| File | Path | Purpose |
|------|------|---------|
| `conreq.js` | `src/main/webapp/js/custom/ocean/` | Consultation request OCEAN toolbar integration |
| `cme.js` | `src/main/webapp/js/custom/ocean/` | Encounter view OCEAN toolbar integration |
| `global.js` | `src/main/webapp/js/custom/ocean/` | Placeholder (intentionally blank) |

#### Database Tables

| Table | Purpose |
|-------|---------|
| `erefer_attachment` | Stores attachment session records |
| `erefer_attachment_data` | Stores individual document references |

### Database Schema

```sql
-- Current OpenO Schema (from oscarinit.sql)
CREATE TABLE IF NOT EXISTS erefer_attachment (
    id INT PRIMARY KEY AUTO_INCREMENT,
    demographic_no INT,
    created DATETIME,
    archived BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS erefer_attachment_data (
    erefer_attachment_id INT,
    lab_id INT,
    lab_type VARCHAR(20),
    PRIMARY KEY(erefer_attachment_id, lab_id, lab_type)
);
```

### Supported Attachment Types

| Type Code | Document Type | Supported |
|-----------|---------------|-----------|
| `D` | Documents (uploaded files) | Yes |
| `L` | Lab Results | Yes |
| `E` | eForms | Yes |
| `H` | HRM (Hospital Report Manager) | Yes |
| `F` | Clinical Forms | No (PDF generation issue) |

### Key Methods

#### ERefer2Action.java

```java
// Creates new attachment record for OCEAN consultation
public void attachOceanEReferralConsult() {
    // Parses document string (e.g., "D1|L2|E3|H4")
    // Creates EReferAttachment with EReferAttachmentData items
    // Persists to database
    // Returns attachment ID
}

// Updates attachments on existing consultation request
public void editOceanEReferralConsult() {
    // Parses document string
    // Uses DocumentAttachmentManager to attach to consultation
    // Separates by type: docs, labs, eforms, hrms
}
```

#### ConsultationManagerImpl.java

```java
// Retrieves and renders attachments as PDFs for OCEAN transmission
public List<ConsultationAttachment> getEReferAttachments(
    LoggedInInfo loggedInInfo,
    HttpServletRequest request,
    HttpServletResponse response,
    Integer demographicNo
) throws PDFGenerationException {
    // Gets attachment created within last hour
    // Renders each attachment to PDF
    // Archives attachment after retrieval (one-time use)
    // Returns list of ConsultationAttachment with file data
}
```

### REST API Endpoint

```
GET /ws/rs/consults/getEReferAttachments?demographicNo={id}

Response: JSON array of ConsultationAttachment objects
[
  {
    "id": 123,
    "attachmentType": "D",
    "fileName": "Doc_001.pdf",
    "data": "<base64-encoded-pdf>"
  }
]
```

### Configuration

OCEAN host URL is configured via property:
```properties
ocean_host=https://ocean.cognisantmd.com
```

This property is read in JSP and passed to JavaScript via script attribute:
```jsp
<script src="conreq.js" ocean-host="<%=Encode.forUriComponent(props.getProperty("ocean_host"))%>"></script>
```

### OpenO-Specific Enhancements

OpenO has the following enhancements not present in Oscar 19:

1. **Edit Existing Referral Attachments**
   - `editOceanEReferralConsult()` method allows updating attachments on existing consultations
   - Uses `DocumentAttachmentManager` for proper attachment handling

2. **Form Attachment Warning**
   ```javascript
   // conreq.js - User-friendly warning with workaround guidance
   if (isFormAttached) {
       const canProceed = confirm(
           "Forms cannot be attached to Ocean referrals.\n\n" +
           "Workaround:\n" +
           "Create a PDF of the form and upload it to the patient's chart as a document. " +
           "Then you can attach that document with the referral.\n\n" +
           "To proceed without attaching Form, please press 'OK'"
       );
   }
   ```

3. **Struts2 Migration**
   - Uses `ERefer2Action` (Struts2) instead of `EReferAction` (Struts1)
   - Modern action pattern with method-based routing

---

## Comparison with Other Forks

### Feature Matrix

| Feature | Oscar 19 | OscarPro | OpenO |
|---------|----------|----------|-------|
| **Core OCEAN Integration** |
| OCEAN toolbar loading | Yes | Yes | Yes |
| Attach documents to consultation | Yes | Yes | Yes |
| Retrieve attachments as PDF | Yes | Yes | Yes |
| Archive after retrieval | Yes | Yes | Yes |
| **Enhanced Features** |
| Edit existing attachments | No | No | Yes |
| Form attachment warning | No | No | Yes |
| Struts2 action | No | No | Yes |
| DocumentAttachmentManager | No | No | Yes |
| **OscarPro-Only Features** |
| OceanWorkflowTypeEnum | No | Yes | No |
| OceanSetting table | No | Yes | No |
| OceanService REST API | No | Yes | No |
| EReferAttachment.type column | No | Yes | No |
| EReferAttachmentManagerData | No | Yes | No |
| OceanApiAttachment DTO | No | Yes | No |
| Perinatal form attachments | No | Yes | No |
| Dual attachment format | No | Yes | No |
| **Incoming OCEAN** |
| Automated incoming consultations | No | No | No |
| Incoming webhook/API | No | No | No |

### JavaScript File Comparison

| File | Oscar 19 | OscarPro | OpenO |
|------|----------|----------|-------|
| `conreq.js` | Basic | Basic | **Enhanced** |
| `cme.js` | Yes | Yes | Yes |
| `global.js` | Empty | No | Empty |
| `main.js` | ConReport | KAI Bar | No |
| `master.js` | Empty | Empty | No |
| `adddemo.js` | Yes | Yes | No |
| `monthview.js` | No | Yes | No |

### Database Schema Comparison

| Table/Column | Oscar 19 | OscarPro | OpenO |
|--------------|----------|----------|-------|
| `erefer_attachment` | Yes | Yes | Yes |
| `erefer_attachment.type` | No | Yes | No |
| `erefer_attachment_data` | Yes | Yes | Yes |
| `erefer_attachment_manager_data` | No | Yes | No |
| `OceanSetting` | No | Yes | No |

---

## Delta Assessment

### Features OpenO Has That Others Don't

| Feature | Value | Assessment |
|---------|-------|------------|
| `editOceanEReferralConsult()` | Allows updating attachments on existing consultations | Valuable functionality - **retain** |
| Form attachment warning | User-friendly UX with workaround instructions | Better user experience - **retain** |
| Struts2 migration | Modern framework, maintainable | Aligns with OpenO direction - **retain** |
| DocumentAttachmentManager integration | Proper attachment handling | Better architecture - **retain** |

### Features OscarPro Has That OpenO Doesn't

| Feature | Value | Assessment | Effort |
|---------|-------|------------|--------|
| `OceanWorkflowTypeEnum` | Supports EREFERRAL, MESSENGER, AM workflows | Could future-proof integration | Low |
| `OceanSetting` table | Proper configuration storage | Could improve over properties file | Low |
| `OceanService` REST API | Dedicated /ocean/* endpoints | Could provide cleaner API separation | Medium |
| `EReferAttachment.type` column | Differentiates workflow types | Would be required for workflow support | Low |
| `EReferAttachmentManagerData` | Modern JSON attachment format | Only if attachment manager needed | Medium |
| `OceanApiAttachment` DTO | Structured response object | ConsultationAttachment works adequately | Low |
| Perinatal form attachments | BC-specific form support | Only if BC requires | Medium |

### Features Oscar 19 Has That OpenO Doesn't

| Feature | Value | Assessment |
|---------|-------|------------|
| `main.js` (ConReport link) | Adds consultation report link to nav | Minor UI enhancement - optional |
| `adddemo.js` | Demo form field adjustments | UI preference - not needed |

---

## Potential Recommended Enhancements

The following are potential enhancements that could be considered if additional OCEAN functionality is desired. These are not required for current functionality but may be valuable for future expansion.

### Potential Enhancement 1: Workflow Type Support (Low Effort)

**Purpose**: Could enable support for multiple OCEAN workflows (eReferral, Messenger, Attachment Manager) if needed in the future.

#### 1.1 Create OceanWorkflowTypeEnum

**File**: `src/main/java/ca/openosp/openo/commn/model/enumerator/OceanWorkflowTypeEnum.java`

```java
package ca.openosp.openo.commn.model.enumerator;

/**
 * Enum representing OCEAN workflow types.
 *
 * @since 2026-01-22
 */
public enum OceanWorkflowTypeEnum {
    /**
     * Standard eReferral workflow for sending consultations to specialists.
     */
    EREFERRAL,

    /**
     * OCEAN Messenger workflow for secure messaging.
     */
    MESSENGER,

    /**
     * Attachment Manager workflow for document management.
     */
    AM
}
```

#### 1.2 Add Type Column to EReferAttachment

**Database Migration**: `database/mysql/updates/update-2026-XX-XX-ocean-workflow-type.sql`

```sql
-- Add workflow type column to erefer_attachment table
ALTER TABLE erefer_attachment
ADD COLUMN type VARCHAR(255) DEFAULT NULL;

-- Create index for type-based queries
CREATE INDEX idx_erefer_attachment_type ON erefer_attachment(type);
```

**Model Update**: `EReferAttachment.java`

```java
// Add field
@Column(name = "type")
private String type;

// Add constructor
public EReferAttachment(Integer demographicNo, OceanWorkflowTypeEnum type) {
    this.demographicNo = demographicNo;
    this.created = new Date();
    this.type = type != null ? type.name() : null;
}

// Add getter/setter
public String getType() {
    return type;
}

public void setType(String type) {
    this.type = type;
}
```

### Potential Enhancement 2: OCEAN Settings Storage (Low Effort)

**Purpose**: Could provide more flexible configuration storage compared to properties files, if dynamic configuration is needed.

#### 2.1 Create OceanSetting Entity

**File**: `src/main/java/ca/openosp/openo/commn/model/OceanSetting.java`

```java
package ca.openosp.openo.commn.model;

import javax.persistence.*;

/**
 * Entity for storing OCEAN integration settings.
 * Settings are stored as JSON text for flexibility.
 *
 * @since 2026-01-22
 */
@Entity
@Table(name = "OceanSetting")
public class OceanSetting extends AbstractModel<Integer> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "settings", columnDefinition = "TEXT")
    private String settings;

    public OceanSetting() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getSettings() { return settings; }
    public void setSettings(String settings) { this.settings = settings; }
}
```

#### 2.2 Database Migration

**File**: `database/mysql/updates/update-2026-XX-XX-ocean-settings.sql`

```sql
-- Create OceanSetting table for OCEAN configuration storage
CREATE TABLE IF NOT EXISTS OceanSetting (
    id INT PRIMARY KEY AUTO_INCREMENT,
    settings TEXT DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Insert default settings (empty JSON object)
INSERT INTO OceanSetting (settings) VALUES ('{}');
```

#### 2.3 Create DAO

**File**: `src/main/java/ca/openosp/openo/commn/dao/OceanSettingDao.java`

```java
package ca.openosp.openo.commn.dao;

import ca.openosp.openo.commn.model.OceanSetting;

public interface OceanSettingDao extends AbstractDao<OceanSetting> {
    OceanSetting getSettings();
}
```

**File**: `src/main/java/ca/openosp/openo/commn/dao/OceanSettingDaoImpl.java`

```java
package ca.openosp.openo.commn.dao;

import ca.openosp.openo.commn.model.OceanSetting;
import org.springframework.stereotype.Repository;
import javax.persistence.Query;
import java.util.List;

@Repository
public class OceanSettingDaoImpl extends AbstractDaoImpl<OceanSetting> implements OceanSettingDao {

    public OceanSettingDaoImpl() {
        super(OceanSetting.class);
    }

    @Override
    public OceanSetting getSettings() {
        Query query = entityManager.createQuery("SELECT o FROM OceanSetting o ORDER BY o.id ASC");
        query.setMaxResults(1);
        List<OceanSetting> results = query.getResultList();
        return results.isEmpty() ? null : results.get(0);
    }
}
```

### Potential Enhancement 3: Dedicated OCEAN REST Service (Medium Effort)

**Purpose**: Could provide cleaner API separation with dedicated /ocean/* endpoints, if multiple OCEAN features are added.

#### 3.1 Create OceanService

**File**: `src/main/java/ca/openosp/openo/webserv/rest/OceanService.java`

```java
package ca.openosp.openo.webserv.rest;

import ca.openosp.openo.commn.dao.OceanSettingDao;
import ca.openosp.openo.commn.model.OceanSetting;
import ca.openosp.openo.commn.model.enumerator.OceanWorkflowTypeEnum;
import ca.openosp.openo.managers.ConsultationManager;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.webserv.rest.to.model.ConsultationAttachment;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

/**
 * REST service for OCEAN integration endpoints.
 * Provides dedicated API for OCEAN-specific operations.
 *
 * @since 2026-01-22
 */
@Path("/ocean")
public class OceanService extends AbstractServiceImpl {

    private final ConsultationManager consultationManager =
        SpringUtils.getBean(ConsultationManager.class);
    private final OceanSettingDao oceanSettingDao =
        SpringUtils.getBean(OceanSettingDao.class);
    private final SecurityInfoManager securityInfoManager =
        SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Retrieves OCEAN attachments for a demographic by workflow type.
     *
     * @param demographicNo The patient demographic number
     * @param type The OCEAN workflow type (EREFERRAL, MESSENGER, AM)
     * @param request HTTP request
     * @param response HTTP response
     * @return List of attachments as PDF data
     */
    @GET
    @Path("/attachments")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAttachments(
            @QueryParam("demographicNo") Integer demographicNo,
            @QueryParam("type") @DefaultValue("EREFERRAL") String type,
            @Context HttpServletRequest request,
            @Context HttpServletResponse response) {

        LoggedInInfo loggedInInfo = getLoggedInInfo();

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_con", "r", demographicNo)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        try {
            OceanWorkflowTypeEnum workflowType = OceanWorkflowTypeEnum.valueOf(type);
            List<ConsultationAttachment> attachments =
                consultationManager.getEReferAttachments(
                    loggedInInfo, request, response, demographicNo);
            return Response.ok(attachments).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Invalid workflow type: " + type).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("Error retrieving attachments").build();
        }
    }

    /**
     * Retrieves OCEAN settings.
     *
     * @return OCEAN settings JSON
     */
    @GET
    @Path("/getSettings")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSettings() {
        LoggedInInfo loggedInInfo = getLoggedInInfo();

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "r", null)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        OceanSetting settings = oceanSettingDao.getSettings();
        if (settings == null) {
            return Response.ok("{}").build();
        }
        return Response.ok(settings.getSettings()).build();
    }

    /**
     * Saves OCEAN settings.
     *
     * @param settings JSON settings string
     * @return Success response
     */
    @POST
    @Path("/saveSettings")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response saveSettings(String settings) {
        LoggedInInfo loggedInInfo = getLoggedInInfo();

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)) {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        try {
            OceanSetting oceanSetting = oceanSettingDao.getSettings();
            if (oceanSetting == null) {
                oceanSetting = new OceanSetting();
            }
            oceanSetting.setSettings(settings);
            oceanSettingDao.merge(oceanSetting);
            return Response.ok().entity("{\"success\": true}").build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("{\"success\": false, \"error\": \"" + e.getMessage() + "\"}").build();
        }
    }
}
```

#### 3.2 Register in applicationContextREST.xml

Add to `src/main/resources/applicationContextREST.xml`:

```xml
<jaxrs:server id="oceanService" address="/ocean">
    <jaxrs:serviceBeans>
        <ref bean="oceanServiceBean"/>
    </jaxrs:serviceBeans>
</jaxrs:server>

<bean id="oceanServiceBean" class="ca.openosp.openo.webserv.rest.OceanService"/>
```

---

## Implementation Specifications

### Implementation Checklist (If Proceeding)

#### Phase 1: Workflow Type Support

- [ ] Create `OceanWorkflowTypeEnum.java`
- [ ] Create database migration for `type` column
- [ ] Update `EReferAttachment.java` model
- [ ] Update `EReferAttachmentDao` with type-based queries
- [ ] Update `ERefer2Action` to set workflow type
- [ ] Update unit tests
- [ ] Test with OCEAN sandbox environment

#### Phase 2: Settings Storage

- [ ] Create `OceanSetting.java` entity
- [ ] Create database migration for `OceanSetting` table
- [ ] Create `OceanSettingDao` interface and implementation
- [ ] Add to `persistence.xml` entity list
- [ ] Create admin UI for settings management (optional)
- [ ] Migrate existing property-based configuration

#### Phase 3: Dedicated REST Service

- [ ] Create `OceanService.java`
- [ ] Register in `applicationContextREST.xml`
- [ ] Add security privilege checks
- [ ] Update JavaScript to use new endpoints (optional)
- [ ] Create API documentation
- [ ] Test all endpoints

### Files to Create (If Implementing)

| File | Type | Phase |
|------|------|-------|
| `OceanWorkflowTypeEnum.java` | Java Enum | 1 |
| `OceanSetting.java` | JPA Entity | 2 |
| `OceanSettingDao.java` | DAO Interface | 2 |
| `OceanSettingDaoImpl.java` | DAO Implementation | 2 |
| `OceanService.java` | REST Service | 3 |
| `update-2026-XX-XX-ocean-workflow-type.sql` | Migration | 1 |
| `update-2026-XX-XX-ocean-settings.sql` | Migration | 2 |

### Files to Modify (If Implementing)

| File | Change | Phase |
|------|--------|-------|
| `EReferAttachment.java` | Add `type` field | 1 |
| `EReferAttachmentDao.java` | Add type-based query methods | 1 |
| `EReferAttachmentDaoImpl.java` | Implement type-based queries | 1 |
| `ERefer2Action.java` | Set workflow type on create | 1 |
| `ConsultationManagerImpl.java` | Filter by workflow type | 3 |
| `persistence.xml` | Add new entities | 2 |
| `applicationContextREST.xml` | Register OceanService | 3 |

---

## Incoming OCEAN Consultations

### Current State

**No OSCAR-based EMR fork supports automated incoming OCEAN consultations.**

Current workflow for specialists receiving OCEAN consultations:
1. Specialist receives notification from OCEAN (email/web portal)
2. Specialist logs into OCEAN web portal to view consultation
3. Specialist manually creates `ConsultationResponse` in their EMR
4. No automatic patient matching or document import

Note: OSCAR Pro with Cloud Connect can auto-create patient charts and import referral documents when receiving eReferrals, but this requires manual acceptance in OCEAN first and is not a fully automated incoming workflow.

### Gap Analysis

To implement fully automated incoming OCEAN consultation support, OpenO would require:

| Component | Description | Effort |
|-----------|-------------|--------|
| Webhook Endpoint | Receive incoming consultations from OCEAN | High |
| Patient Matching | Match by HIN or create new demographic | High |
| Document Import | Import attached documents to patient chart | Medium |
| ConsultationResponse Creation | Auto-create response record | Medium |
| Notification System | Alert specialist of incoming consultation | Medium |
| OCEAN API Client | Acknowledge receipt, update status | High |
| Admin Configuration | OCEAN API credentials, settings | Low |

### Architectural Considerations

```
┌─────────────────────────────────────────────────────────────────┐
│              Proposed Incoming OCEAN Architecture               │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────────┐                                            │
│  │  OCEAN Network  │                                            │
│  │   (External)    │                                            │
│  └────────┬────────┘                                            │
│           │ Webhook POST                                         │
│           ▼                                                      │
│  ┌─────────────────────────────────────────┐                    │
│  │  OceanIncomingService (New REST API)    │                    │
│  │  POST /ocean/incoming                   │                    │
│  └────────┬────────────────────────────────┘                    │
│           │                                                      │
│           ▼                                                      │
│  ┌─────────────────────────────────────────┐                    │
│  │  Patient Matching Service               │                    │
│  │  - Match by HIN                         │                    │
│  │  - Create if not exists (optional)      │                    │
│  └────────┬────────────────────────────────┘                    │
│           │                                                      │
│           ▼                                                      │
│  ┌─────────────────────────────────────────┐                    │
│  │  Document Import Service                │                    │
│  │  - Import PDFs to patient chart         │                    │
│  │  - Link to ConsultationResponse         │                    │
│  └────────┬────────────────────────────────┘                    │
│           │                                                      │
│           ▼                                                      │
│  ┌─────────────────────────────────────────┐                    │
│  │  Notification Service                   │                    │
│  │  - Create inbox item for specialist     │                    │
│  │  - Optional email notification          │                    │
│  └────────┬────────────────────────────────┘                    │
│           │                                                      │
│           ▼                                                      │
│  ┌─────────────────────────────────────────┐                    │
│  │  OCEAN API Client                       │                    │
│  │  - Send acknowledgment                  │                    │
│  │  - Update consultation status           │                    │
│  └─────────────────────────────────────────┘                    │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Prerequisites for Implementation

1. **OCEAN API Documentation** - Obtain HL7 FHIR API documentation from CognisantMD
2. **OCEAN Partnership** - May require partnership agreement with CognisantMD
3. **OAuth2 Credentials** - Site-specific Client ID and secret required
4. **Shared Encryption Key (SEK)** - Required for secure communication
5. **Webhook Security** - OCEAN authentication mechanism and IP whitelist configuration
6. **Patient Consent** - Privacy considerations for auto-import
7. **Testing Environment** - OCEAN sandbox access (test.cognisantmd.com)

### Assessment

Incoming OCEAN consultation support would be a significant development effort requiring:
- External partnership with CognisantMD
- Substantial new code development
- Privacy and security considerations
- Multi-province compliance review
- HL7 FHIR v0.10.0 or v0.11.1 API implementation

**This should be considered a separate project rather than a port from another fork, as no existing fork has this functionality implemented.**

---

## References

### Official OCEAN Documentation
- [OCEAN by CognisantMD](https://www.cognisantmd.com/ocean/)
- [API Integrations Hub](https://support.cognisantmd.com/hc/en-us/sections/360006922672-API-Integrations)
- [HL7 FHIR eReferral Integration Setup Guide](https://support.cognisantmd.com/hc/en-us/articles/360058125332-HL7-FHIR-eReferral-Integration-Setup-Guide)
- [HL7 FHIR eReferral API Implementation Guidance](https://support.cognisantmd.com/hc/en-us/articles/360060603172-HL7-FHIR-eReferral-API-Implementation-Guidance)
- [OSCAR & Ocean Support Section](https://support.cognisantmd.com/hc/en-us/sections/115000889632-OSCAR-Ocean)

### Source Repositories
- OscarPro Repository: `bitbucket.org/oscaremr/oscarpro`
- Oscar 19 Repository: `bitbucket.org/oscaremr/oscar`
- OpenO EMR Repository: `github.com/openo-beta/Open-O`

### Community Resources
- [Oscar Galaxy - OCEAN Toolbar](https://oscargalaxy.org/knowledge-base/ocean-toolbar/)
- [World EMR - OCEAN Toolbar](https://worldemr.org/knowledge-base/ocean-toolbar/)

---

## Document History

| Version | Date       | Author                                        | Changes                                                                        |
|---------|------------|-----------------------------------------------|--------------------------------------------------------------------------------|
| 1.0     | 2026-01-22 | Michael Yingbull (Co-authored by Claude Code) | Initial assessment and documentation with OCEAN platform technical information |
