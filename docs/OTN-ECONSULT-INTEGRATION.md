# OTN eConsult Integration Documentation

> **Document Version**: 1.0
> **Last Updated**: 2026-01-22
> **Status**: Assessment Complete

## Table of Contents

1. [Overview](#overview)
2. [OTN eConsult vs OCEAN - Key Differences](#otn-econsult-vs-ocean---key-differences)
3. [OTN eConsult Platform Technical Information](#otn-econsult-platform-technical-information)
4. [Current OpenO Implementation](#current-openo-implementation)
5. [Comparison with Other Forks](#comparison-with-other-forks)
6. [Delta Assessment](#delta-assessment)
7. [Potential Recommended Enhancements](#potential-recommended-enhancements)
8. [References](#references)

---

## Overview

### What is OTN eConsult?

OTN eConsult (Ontario Telemedicine Network eConsult) is a provincial digital health service provided by Ontario Health that enables primary care providers (physicians and nurse practitioners) to obtain timely specialist advice electronically. The service is hosted on the OTNhub platform and is accessed using ONE ID credentials.

### Key Characteristics

- **Government-operated**: Part of Ontario Health (formerly eHealth Ontario)
- **Provincial scope**: Available to all Ontario healthcare providers with ONE ID
- **Specialist advice**: Allows PCPs to consult specialists without patient referral
- **OTNhub-based**: Accessed through the OTNhub web portal
- **ONE ID authentication**: Uses Ontario Health's federated identity system

### Current Status (2026)

**Important**: As of February 28, 2025, EMR-integrated access to eConsult via direct launch was deactivated. Providers must now:
- Use ONE ID credentials to log into OTNhub directly
- Access eConsult functions through the OTNhub web interface
- Or use Ocean integration (where available) to launch OTN eConsult

---

## OTN eConsult vs OCEAN - Key Differences

Understanding the distinction between these two systems is critical:

| Aspect | OTN eConsult | OCEAN (CognisantMD) |
|--------|-------------|---------------------|
| **Operator** | Ontario Health (Government) | CognisantMD (Private Company) |
| **Primary Purpose** | Specialist advice for PCPs | Full consultation referrals with attachments |
| **Patient Referral** | Often eliminates need for referral | Full referral workflow |
| **Authentication** | ONE ID (Provincial) | OCEAN-specific credentials |
| **Platform** | OTNhub.ca | Ocean Platform |
| **Attachment Handling** | Limited (via eConsult form) | Full document/lab/eForm attachments |
| **Response Type** | Text-based specialist advice | Full consultation response |
| **EMR Integration** | Contextual launch, document import | Toolbar integration, REST APIs |
| **Billing** | Specific eConsult billing codes | Standard consultation billing |

### Integration Relationship

Ocean and OTN eConsult can work together:
- Ocean can launch OTN eConsult from the EMR via the Healthmap
- Patient demographics can be pre-populated from Ocean to OTN eConsult
- Follow-up and correspondence must be completed on OTNhub
- Ocean provides the "sender-initiated" workflow to OTN eConsult

**Note**: Ontario eConsults sent through Ocean are separate and unique from Ocean eConsults, and do not offer the same features.

---

## OTN eConsult Platform Technical Information

### ONE ID Authentication

ONE ID is Ontario Health's federated identity management system for healthcare providers.

#### Requirements
- ONE ID account from Ontario Health
- OTNhub Membership (belonging to an OTNhub member organization)
- Validated credentials for OTNhub access

#### Federation Standards
- **Protocol**: OASIS SAML version 2 specifications
- **Alternative**: OpenID Connect (OIDC) supported
- **Custom Attributes**: Additional attributes for eHealth Ontario's single sign-on model
- **Security**: Identity Providers must complete required testing including security testing

#### SAML Integration Requirements
- Identity Providers must implement either OIDC or SAML Token Service
- Must meet Identity Providers Standard
- Service providers must meet SAML Standards or OIDC Standards
- Federation Standards require protection of PHI and access controls

### HL7 FHIR Implementation Guide

Ontario has published implementation guides for eReferral/eConsult integration:

| Version | Status | Features |
|---------|--------|----------|
| **v0.10.0** | Stable | Basic eReferral workflows |
| **v0.11.1** | Beta | OTN eConsult support added |

#### Integration Patterns Supported
1. **SMART on FHIR**: RMS launched in clinician's Point of Service system
2. **Direct Messaging**: Two RMSs exchange referral messages via FHIR APIs
3. **RESTful FHIR API**: For systems conforming to RESTful FHIR API requirements

### Contextual Launch

Through Contextual Launch, EMR services can:
- Select "OTN eConsult" from the EMR interface
- Login to ONE ID services when prompted
- Launch OTN eConsult with a draft case already in progress
- Pull patient health information (name, DOB, health card #) from EMR

### Currently Integrated EMRs (Official)

As of 2026, official eConsult EMR integration is available through:
- QHR Technologies' Accuro EMR
- Avaros EMR
- YES EMR
- YMS EMR
- TELUS PS Suite (via Ocean)
- TELUS CHR (via Ocean)
- OSCAR/OSCAR Pro (via Ocean contextual launch)

---

## Current OpenO Implementation

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                 OpenO OTN eConsult Integration                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────┐                                           │
│  │   User Session   │                                           │
│  │  (ONE ID Auth)   │                                           │
│  └────────┬─────────┘                                           │
│           │                                                      │
│           ▼                                                      │
│  ┌──────────────────────────────────────────┐                   │
│  │       SSOLogin2Action (SAML2)            │                   │
│  │  - econsultLogin() method                │                   │
│  │  - ONE ID token validation               │                   │
│  │  - HMAC signature verification           │                   │
│  │  - Provider account linking              │                   │
│  └────────┬─────────────────────────────────┘                   │
│           │                                                      │
│           ▼                                                      │
│  ┌──────────────────────────────────────────┐                   │
│  │        EConsult2Action                   │                   │
│  │  - frontend() - Launch eConsult UI       │                   │
│  │  - backend() - API calls                 │                   │
│  │  - login() - SAML2 redirect              │                   │
│  └────────┬─────────────────────────────────┘                   │
│           │                                                      │
│           ▼                                                      │
│  ┌──────────────────────────────────────────┐                   │
│  │   ConsultationWebService REST API        │                   │
│  │   POST /consults/importEconsult          │                   │
│  │   - Import eConsult document to chart    │                   │
│  └────────┬─────────────────────────────────┘                   │
│           │                                                      │
│           ▼                                                      │
│  ┌──────────────────────────────────────────┐                   │
│  │       OtnEconsult Model                  │                   │
│  │  - contentType, demographicNo            │                   │
│  │  - fileName, contents, importDate        │                   │
│  │  - consultId, docDescription             │                   │
│  └──────────────────────────────────────────┘                   │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### File Inventory

#### Java Classes

| File | Path | Purpose |
|------|------|---------|
| `SSOLogin2Action.java` | `ca.openosp.openo.login` | SAML2 SSO login handling with ONE ID support |
| `EConsult2Action.java` | `ca.openosp.openo.encounter.oscarConsultationRequest.pageUtil` | eConsult frontend/backend redirect handler |
| `SsoAuthenticationManager.java` | `ca.openosp.openo.managers` | SAML2 authentication settings builder |
| `OtnEconsult.java` | `ca.openosp.openo.webserv.rest.to.model` | OTN eConsult data transfer object |
| `OtnEconsultConverter.java` | `ca.openosp.openo.webserv.rest.conversion` | Converter for OTN eConsult data |
| `EctDisplayEconsult2Action.java` | `ca.openosp.openo.encounter.pageUtil` | Encounter display action for eConsult |
| `ConsultationWebService.java` | `ca.openosp.openo.webserv.rest` | REST endpoint for eConsult import |
| `ConsultationManagerImpl.java` | `ca.openosp.openo.managers` | Business logic for eConsult import |
| `Security.java` | `ca.openosp.openo.commn.model` | Security model with ONE ID fields |

#### Security Model Fields

The `Security` model includes ONE ID authentication fields:

```java
// ONE ID fields in Security.java
private String oneIdKey;      // ONE ID unique identifier
private String oneIdEmail;    // ONE ID email address
private String delagateOneIdEmail;  // Delegate's ONE ID email
```

#### Configuration Properties

```properties
# oscar.properties - eConsult configuration
frontendEconsultUrl=https://econsult.otnhub.ca
backendEconsultUrl=https://api.econsult.otnhub.ca
econsultLoginTimeout=300
oneid.encryptionKey=<encryption-key>
```

### Key Methods

#### SSOLogin2Action.java

```java
/**
 * Handles ONE ID authentication callback for eConsult login.
 * Validates HMAC signature, decrypts token, and links provider account.
 */
public String econsultLogin() throws IOException {
    // Validates HMAC signature
    // Decrypts ONE ID token
    // Validates session timeout
    // Links ONE ID to provider account (first login)
    // Sets session attributes: oneIdEmail, oneid_token, delegateOneIdEmail
}

/**
 * SAML2 SSO login handler.
 * Processes SAML response and creates authenticated session.
 */
public String ssoLogin() {
    // Builds SAML2 settings
    // Processes SAML response
    // Validates authentication
    // Creates new session with provider data
}
```

#### EConsult2Action.java

```java
/**
 * Builds eConsult frontend redirect URL with ONE ID credentials.
 * Validates task parameter for security.
 */
public String frontend() {
    // Validates ONE ID token in session
    // Validates task parameter (prevents URL injection)
    // Builds redirect URL with oneid_email parameter
    // Adds patient_id for demographic context
}

/**
 * Creates SAML2 login redirect to backend eConsult service.
 */
public String login() {
    // Builds return URL for OSCAR
    // Constructs SAML2/login endpoint URL
    // Includes oscarReturnURL and loginStart timestamp
}
```

#### ConsultationWebService.java

```java
/**
 * Imports OTN eConsult document to patient chart.
 * POST /consults/importEconsult
 */
@POST
@Path("/importEconsult")
public GenericRESTResponse importEconsult(OtnEconsult data) {
    // Validates demographic number
    // Calls consultationManager.importEconsult()
    // Returns success/failure response
}
```

### REST API Endpoint

```
POST /ws/rs/consults/importEconsult
Content-Type: application/json

Request Body:
{
  "demographicNo": 12345,
  "fileName": "eConsult_Response_2026-01-22.pdf",
  "contents": "<base64-encoded-pdf>",
  "contentType": "application/pdf",
  "consultId": 67890,
  "docDescription": "OTN eConsult Consultation"
}

Response:
{
  "success": true,
  "message": "File eConsult_Response_2026-01-22.pdf imported."
}
```

### Security Features

1. **HMAC Signature Verification**
   - Token signed with HmacSHA256
   - Signature verified: `sha256_HMAC(oneIdKey + oneIdEmail + encryptedOneIdToken + timestamp)`

2. **AES Token Encryption**
   - ONE ID token encrypted with AES/CBC/PKCS5PADDING
   - IV passed with encrypted data (format: `encrypted:iv`)

3. **Session Timeout**
   - Configurable timeout via `econsultLoginTimeout` property
   - Validates timestamp to prevent replay attacks

4. **URL Injection Prevention**
   - Task parameter validated with regex pattern
   - Path traversal and protocol injection blocked

---

## Comparison with Other Forks

### Feature Matrix

| Feature | Oscar 19 | OscarPro | OpenO |
|---------|----------|----------|-------|
| **Core OTN eConsult** |
| ONE ID SSO Login | Yes | Yes | Yes (Struts2) |
| EConsult Action | Yes (Struts1) | Yes (Struts1) | Yes (Struts2) |
| OtnEconsult Model | Yes | No | Yes |
| Import eConsult Endpoint | Yes | Yes | Yes |
| Encounter Display | Yes | Yes | Yes (Struts2) |
| **ONE ID Management** |
| Security.oneIdKey | Yes | Yes | Yes |
| Security.oneIdEmail | Yes | Yes | Yes |
| Security.delagateOneIdEmail | Yes | Yes | Yes |
| **OscarPro-Exclusive Features** |
| OneIdSession Entity | No | Yes | No |
| OneIdViewlet Entity | No | Yes | No |
| OneIdGatewayData | No | Yes | No |
| OneIdFilter | No | Yes | No |
| OneIdSessionDao | No | Yes | No |
| OneIdViewletDao | No | Yes | No |
| JWT Token Handling | No | Yes | No |
| Toolbar URL Extraction | No | Yes | No |
| SystemPreferences.oneId* | No | Yes | No |
| **OpenO-Specific Features** |
| Struts2 Migration | No | No | Yes |
| URL Injection Prevention | Partial | Partial | Yes |
| Enhanced Security Validation | Basic | Basic | Enhanced |

### Key File Comparison

| File Type | Oscar 19 | OscarPro | OpenO |
|-----------|----------|----------|-------|
| SSO Login Action | `SSOLoginAction.java` | `SSOLoginAction.java` | `SSOLogin2Action.java` |
| EConsult Action | `EConsultAction.java` | `EConsultAction.java` | `EConsult2Action.java` |
| OtnEconsult Model | `OtnEconsult.java` | Not present | `OtnEconsult.java` |
| OtnEconsult Converter | `OtnEconsultConverter.java` | Not present | `OtnEconsultConverter.java` |
| OneId Session | Not present | `OneIdSession.java` | Not present |
| OneId Viewlet | Not present | `OneIdViewlet.java` | Not present |
| OneId Filter | Not present | `OneIdFilter.java` | Not present |
| OneId Gateway Data | Not present | `OneIdGatewayData.java` | Not present |
| OneId Utils | Not present | `OneIDTokenUtils.java`, `OneIDUtil.java` | Not present |

### OscarPro-Specific ONE ID Features

OscarPro has significantly expanded ONE ID integration:

#### OneIdSession Entity
```java
@Entity
public class OneIdSession extends AbstractModel<Object> {
    private String providerNo;      // Primary key
    private String accessToken;     // JWT access token
    private String refreshToken;    // Token refresh
    private String idToken;         // Identity token
    private String subject;         // ONE ID subject
    private String email;           // Provider email
    private String serviceEntitlements;  // Authorized services
    private String hubTopic;        // Hub topic subscription
    private String uaoUpi;          // UAO identifier
    private String uaoName;         // UAO name
    private String authorizationId; // Authorization ID
    private String toolbar;         // JSON toolbar config (base64)
    private long timestamp;         // Token timestamp
    private Date lastKeptActive;    // Session keep-alive
    private boolean sso;            // SSO flag

    // JWT token expiration handling
    public boolean isExpired() { ... }
    public String getUrlFromToolbar(String key) { ... }
}
```

#### OneIdViewlet Entity
```java
@Entity
public class OneIdViewlet extends AbstractModel<Object> {
    private Integer id;
    private String name;            // Viewlet name
    private String keyValue;        // Configuration key
    private String updatedBy;       // Last modified by
    private Date updateTime;        // Last modified time
    private boolean showInEchart;   // Display in eChart
    private boolean deleted;        // Soft delete flag
}
```

---

## Delta Assessment

### Features OpenO Has That Others Don't

| Feature | Value | Assessment |
|---------|-------|------------|
| Struts2 Migration | Modern framework, better maintainability | Aligns with OpenO direction - **retain** |
| Enhanced URL Validation | Prevents injection attacks | Security improvement - **retain** |
| Input Sanitization | Comprehensive parameter validation | Better security posture - **retain** |

### Features OscarPro Has That OpenO Doesn't

| Feature | Value | Assessment | Effort |
|---------|-------|------------|--------|
| `OneIdSession` | Full JWT session management | Could improve token handling | Medium |
| `OneIdViewlet` | Configurable eChart viewlets | UI flexibility | Low |
| `OneIdGatewayData` | JWT token parsing and management | Token data processing | Medium |
| `OneIdFilter` | Request-level ONE ID validation | Enhanced security | Medium |
| `OneIdSessionDao` | Session persistence | Session recovery | Low |
| `OneIdViewletDao` | Viewlet configuration storage | UI customization | Low |
| JWT Token Handling | Automatic expiration checking | Better UX | Medium |
| Toolbar JSON Parsing | Dynamic toolbar configuration | Extensibility | Low |
| SystemPreferences.oneId* | Centralized ONE ID settings | Configuration management | Low |

### Features Oscar 19 Has That OpenO Doesn't

| Feature | Value | Assessment |
|---------|-------|------------|
| `OtnEconsultConverter` | Same as OpenO | Already present |
| Struts1 Actions | Legacy framework | OpenO has Struts2 equivalent |

### Assessment Summary

1. **OpenO Strengths**: Modern Struts2 framework, enhanced security validation
2. **OscarPro Strengths**: Comprehensive ONE ID session management with JWT handling
3. **Gap**: OpenO lacks OscarPro's advanced ONE ID session persistence and viewlet configuration

---

## Potential Recommended Enhancements

The following are potential enhancements that could be considered if enhanced OTN eConsult functionality is desired.

### Potential Enhancement 1: ONE ID Session Management (Medium Effort)

**Purpose**: Could provide persistent ONE ID session tracking with JWT token management, reducing re-authentication needs.

#### 1.1 Create OneIdSession Entity

**File**: `src/main/java/ca/openosp/openo/integration/OneIdSession.java`

```java
package ca.openosp.openo.integration;

import ca.openosp.openo.commn.model.AbstractModel;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;

import javax.persistence.*;
import java.util.Calendar;
import java.util.Date;

/**
 * Entity for storing ONE ID session data with JWT token management.
 *
 * @since 2026-01-22
 */
@Entity
@Table(name = "one_id_session")
public class OneIdSession extends AbstractModel<String> {

    @Id
    @Column(name = "provider_no")
    private String providerNo;

    @Column(name = "access_token", columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "refresh_token")
    private String refreshToken;

    @Column(name = "id_token", columnDefinition = "TEXT")
    private String idToken;

    @Column(name = "subject")
    private String subject;

    @Column(name = "email")
    private String email;

    @Column(name = "service_entitlements", columnDefinition = "TEXT")
    private String serviceEntitlements;

    @Column(name = "timestamp")
    private long timestamp;

    @Column(name = "last_kept_active")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastKeptActive;

    @Column(name = "sso")
    private boolean sso;

    private static final int MILLISECONDS = 1000;

    @Override
    public String getId() {
        return providerNo;
    }

    public boolean isExpired() {
        if (this.accessToken == null) {
            return true;
        }
        try {
            DecodedJWT jwt = JWT.decode(this.accessToken);
            Date expiresAt = jwt.getExpiresAt();
            if (expiresAt == null) {
                return true;
            }
            Calendar future = Calendar.getInstance();
            future.add(Calendar.SECOND, 15); // 15 second buffer
            return expiresAt.before(future.getTime());
        } catch (Exception e) {
            return true;
        }
    }

    // Getters and setters...
}
```

#### 1.2 Database Migration

**File**: `database/mysql/updates/update-2026-XX-XX-one-id-session.sql`

```sql
-- Create ONE ID session table for persistent session management
CREATE TABLE IF NOT EXISTS one_id_session (
    provider_no VARCHAR(6) PRIMARY KEY,
    access_token TEXT,
    refresh_token VARCHAR(500),
    id_token TEXT,
    subject VARCHAR(255),
    email VARCHAR(255),
    service_entitlements TEXT,
    timestamp BIGINT,
    last_kept_active DATETIME,
    sso BOOLEAN DEFAULT FALSE,
    INDEX idx_one_id_session_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### Potential Enhancement 2: ONE ID Viewlet Configuration (Low Effort)

**Purpose**: Could allow administrators to configure which ONE ID services appear in the eChart interface.

#### 2.1 Create OneIdViewlet Entity

**File**: `src/main/java/ca/openosp/openo/integration/OneIdViewlet.java`

```java
package ca.openosp.openo.integration;

import ca.openosp.openo.commn.model.AbstractModel;

import javax.persistence.*;
import java.util.Date;

/**
 * Entity for configuring ONE ID viewlets in the eChart.
 *
 * @since 2026-01-22
 */
@Entity
@Table(name = "one_id_viewlet")
public class OneIdViewlet extends AbstractModel<Integer> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name")
    private String name;

    @Column(name = "key_value")
    private String keyValue;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "update_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updateTime;

    @Column(name = "show_in_echart")
    private boolean showInEchart;

    @Column(name = "deleted")
    private boolean deleted;

    @Override
    public Integer getId() {
        return id;
    }

    // Getters and setters...
}
```

#### 2.2 Database Migration

**File**: `database/mysql/updates/update-2026-XX-XX-one-id-viewlet.sql`

```sql
-- Create ONE ID viewlet configuration table
CREATE TABLE IF NOT EXISTS one_id_viewlet (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100),
    key_value VARCHAR(255),
    updated_by VARCHAR(6),
    update_time DATETIME,
    show_in_echart BOOLEAN DEFAULT TRUE,
    deleted BOOLEAN DEFAULT FALSE,
    INDEX idx_one_id_viewlet_key (key_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Insert default viewlets
INSERT INTO one_id_viewlet (name, key_value, show_in_echart, deleted) VALUES
('eConsult', 'econsult', TRUE, FALSE),
('Clinical Connect', 'clinicalconnect', TRUE, FALSE),
('DHIR', 'dhir', TRUE, FALSE);
```

### Potential Enhancement 3: ONE ID Request Filter (Medium Effort)

**Purpose**: Could provide request-level ONE ID session validation for enhanced security.

**Note**: This would require careful consideration of performance impact and session management complexity.

### Implementation Checklist (If Proceeding)

#### Phase 1: Session Management

- [ ] Create `OneIdSession.java` entity
- [ ] Create database migration
- [ ] Create `OneIdSessionDao` interface and implementation
- [ ] Update `SSOLogin2Action` to persist sessions
- [ ] Add session expiration checking
- [ ] Update `EConsult2Action` to use session data
- [ ] Add to `persistence.xml`

#### Phase 2: Viewlet Configuration

- [ ] Create `OneIdViewlet.java` entity
- [ ] Create database migration
- [ ] Create `OneIdViewletDao` interface and implementation
- [ ] Create admin UI for viewlet management
- [ ] Update eChart to use viewlet configuration
- [ ] Add to `persistence.xml`

### Files to Create (If Implementing)

| File | Type | Phase |
|------|------|-------|
| `OneIdSession.java` | JPA Entity | 1 |
| `OneIdSessionDao.java` | DAO Interface | 1 |
| `OneIdSessionDaoImpl.java` | DAO Implementation | 1 |
| `OneIdViewlet.java` | JPA Entity | 2 |
| `OneIdViewletDao.java` | DAO Interface | 2 |
| `OneIdViewletDaoImpl.java` | DAO Implementation | 2 |
| `update-2026-XX-XX-one-id-session.sql` | Migration | 1 |
| `update-2026-XX-XX-one-id-viewlet.sql` | Migration | 2 |

---

## References

### Official Ontario Health Documentation
- [OTNhub Portal](https://otnhub.ca/)
- [ONE ID Account](https://ehealthontario.on.ca/en/health-care-professionals/one-id)
- [ONE ID Application Federation](https://ehealthontario.on.ca/en/health-care-professionals/one-id-application-federation)
- [ONE ID Federation Standards](https://ehealthontario.on.ca/en/health-care-professionals/one-id-federation)
- [Ontario eReferral HL7 FHIR Implementation Guide](https://ehealthontario.on.ca/en/standards/ontario-ereferral-implementation-guide-fhir-overview)

### OntarioMD Resources
- [eConsult Deployment and EMR Integration](https://www.ontariomd.ca/products-and-services/econsult-deployment-and-emr-integration)
- [eConsult FAQ](https://www.ontariomd.ca/products-and-services/econsult-deployment-and-emr-integration/faqs)

### Ocean Integration
- [Ocean eConsults in Ontario](https://support.cognisantmd.com/hc/en-us/sections/5467451390477-Ocean-eConsults-in-Ontario)
- [How to use Ocean to connect to OTN eConsult](https://support.cognisantmd.com/hc/en-us/articles/8259921421965-How-do-I-use-Ocean-to-connect-to-the-Ontario-eConsult-Service-on-the-OTNhub)
- [Ontario eServices Program - eConsult Fact Sheet](https://support.cognisantmd.com/hc/en-us/articles/4404853100685-Ontario-eServices-Program-eConsult-Fact-Sheet-for-Primary-Care-Providers)

### Regional Resources
- [eConsult Ontario](https://econsultontario.ca/)
- [SEAMO Ocean to OTN Integration](https://www.seamo.ca/programs-resources/digital-health/econsult/ocean-ontario-econsult-integration)
- [eReferral Ontario East OTN Integration](https://www.ereferralontarioeast.ca/otn-integration)

### Source Repositories
- OscarPro Repository: `bitbucket.org/oscaremr/oscarpro`
- Oscar 19 Repository: `bitbucket.org/oscaremr/oscar`
- OpenO EMR Repository: `github.com/openo-beta/Open-O`

---

## Document History

| Version | Date       | Author                                        | Changes                                                              |
|---------|------------|-----------------------------------------------|----------------------------------------------------------------------|
| 1.0     | 2026-01-22 | Michael Yingbull (Co-authored by Claude Code) | Initial assessment and documentation with platform technical details |
