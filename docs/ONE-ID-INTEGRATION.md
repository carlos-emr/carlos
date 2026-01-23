# ONE ID Integration Documentation

> **Document Version**: 1.0
> **Last Updated**: 2026-01-22
> **Status**: Assessment Complete

## Table of Contents

1. [Overview](#overview)
2. [ONE ID Platform Technical Information](#one-id-platform-technical-information)
3. [Supported Ontario Health Services](#supported-ontario-health-services)
4. [Current OpenO Implementation](#current-openo-implementation)
5. [Comparison with Other Forks](#comparison-with-other-forks)
6. [Delta Assessment](#delta-assessment)
7. [Potential Recommended Enhancements](#potential-recommended-enhancements)
8. [References](#references)

---

## Overview

### What is ONE ID?

ONE ID is Ontario Health's (formerly eHealth Ontario) digital identity and authentication system that allows healthcare professionals to securely access electronic health care applications with a single username and password. It establishes a digital identity and recognizes professional credentials, ensuring only authorized users access healthcare services.

### Key Characteristics

- **Provincial Standard**: Ontario's official digital identity system for healthcare
- **Single Sign-On (SSO)**: Access multiple applications with one credential
- **Federation Support**: Organizations can integrate their existing credentials
- **Professional Verification**: Links to professional regulatory body credentials (e.g., CPSO, CNO)
- **Privacy Compliant**: Meets stringent privacy, security, and legal requirements
- **155,000+ Users**: Large existing user base across 6,000+ Ontario healthcare organizations
- **Ministry Recognized**: Recognized as a secure identity solution by the Ministry of Health and Long-Term Care

### ONE ID vs OSCAR Authentication

| Aspect | ONE ID | OSCAR Local Auth |
|--------|--------|------------------|
| **Scope** | Provincial (Ontario-wide) | Local (per installation) |
| **Identity Verification** | Professional credentials verified | Self-managed |
| **Service Access** | Provincial digital health services | Local EMR only |
| **Session Management** | Federated across services | Single application |
| **Credential Management** | Centralized | Per-installation |

---

## ONE ID Platform Technical Information

### Authentication Protocols

ONE ID supports two authentication protocols for federation:

#### SAML 2.0

- **Standard**: OASIS SAML version 2 specifications
- **Custom Attributes**: Additional attributes for eHealth Ontario's SSO model
- **Bindings**: HTTP-POST for assertions, HTTP-Redirect for requests/logout
- **Specification**: [ONE ID SAML Interface Spec v1.4](https://ehealthontario.on.ca/files/public/support/Standards/ONEID_SAML_Interface_Spec_v1.4.pdf)

**SAML NameID Formats Supported**:
| Format | URN | Use Case |
|--------|-----|----------|
| Persistent | `urn:oasis:names:tc:SAML:2.0:nameid-format:persistent` | Unique identifier across sessions |
| Email | `urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress` | Email-based identification |
| Unspecified | `urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified` | Default format |

**SAML Assertion Attributes**:
| Attribute | Description |
|-----------|-------------|
| `NameID` | Unique user identifier (ONE ID key) |
| `Email` | User's email address |
| `FirstName` | User's legal first name |
| `LastName` | User's legal last name |
| `ProfessionalDesignation` | Professional title (MD, RN, etc.) |
| `RegulatoryCollege` | Licensing body (CPSO, CNO, etc.) |
| `RegistrationNumber` | Professional license number |
| `ServiceEntitlements` | Authorized services list |
| `OrganizationName` | User's organization |
| `SessionIndex` | SAML session identifier |

#### OpenID Connect (OIDC)

- **Standard**: OAuth 2.0 + OpenID Connect
- **PKCE Support**: Proof Key for Code Exchange (RFC 7636) for confidential and public clients
- **Token Types**: Access tokens, ID tokens, and refresh tokens (confidential clients only)
- **Specification**: [ONE ID OAuth2/OpenID Specification v1.5](https://ehealthontario.on.ca/files/public/support/Standards/ONEIDOAuth2OpenIDSpecification.pdf)

### Identity Provider Requirements

Organizations joining the ONE ID Federation as Identity Providers must:

1. Implement either OIDC or SAML Token Service
2. Meet the Identity Providers Standard
3. Complete required testing including security testing
4. Meet Federation Standards for identity proofing and authentication
5. Sign required agreements (Identity Services Schedule, Delivery Channel Services Schedule)
6. Complete Privacy & Security Assessments and remediate gaps

### Identity Assurance Levels

ONE ID implements identity assurance levels based on the [ONE ID Identity Assurance Standard](https://ehealthontario.on.ca/files/public/support/ONE_ID/Registration_Community/one_id_identity_assurance_level_standard.pdf):

| Level | Description | Use Case |
|-------|-------------|----------|
| **Level 1** | Basic identity verification | Administrative access |
| **Level 2** | Enhanced verification with professional credentials | Clinical access to health data |
| **Level 3** | Strong identity proofing with in-person verification | Access to highly sensitive PHI |

The Local Registration Authority (LRA) validates identity, verifies the level of assurance, and creates the ONE ID account.

### Registration Process

#### CPSO Member Self-Registration

Physicians registered with the College of Physicians and Surgeons of Ontario (CPSO) can self-register:

1. **Navigate to CPSO Portal**: Log in using existing CPSO credentials
2. **Initiate Registration**: Click "Sign Up for ONE ID" in the ONE ID registration section
3. **Verify Pre-populated Data**: Legal name, gender, date of birth from CPSO records (cannot be changed)
4. **Provide Contact Information**: Email address, phone number, language preference
5. **Set Challenge Questions**: Select and answer five (5) security questions
6. **Confirm Regulatory Information**: Verify CPSO Registration Number
7. **Complete Registration**: Account created within minutes

#### Other Healthcare Providers

Non-CPSO healthcare providers must be sponsored by an organization:

- **Organization-based**: Contact your ONE ID Local Registration Authority (LRA)
- **Private Practice**: Submit Private Practice Sign Up form to Ontario Health
- **Support Staff**: Sponsored through organization's LRA

**Support Contacts**:
- eHealth Ontario Service Desk: 1-866-250-1554 (24/7)
- Email: servicedesk@ehealthontario.on.ca
- CPSO Physician Advisory Service: 416-967-2606 or 1-800-268-7096 Ext. 606

### Under Authority Of (UAO)

The UAO concept is critical for PHIPA compliance:

- **Definition**: The Health Information Custodian (HIC) under whose authority the user is accessing health data
- **Purpose**: Meets PHIPA requirements for tracking access to personal health information
- **Multi-UAO**: Users may be authorized by multiple HICs; must select one for each transaction
- **Selection**: Client system is responsible for UAO selection; ONE ID OIDC Service facilitates the process
- **Auditing**: ONE ID OIDC Service audits the UAO selected for each transaction
- **Storage**: If ONE ID handles authorization for a service, it stores UAO(s) for each user

### Token Structure

#### Access Token Claims
```json
{
  "iat": 1706000000,
  "exp": 1706003600,
  "expires_in": 3600,
  "sub": "user@oneid.ca",
  "scope": "openid user/Patient.read user/Immunization.read",
  "azp": "client_application_id",
  "aud": "https://fhir.ehealthontario.ca"
}
```

#### ID Token Contents
```json
{
  "sub": "unique-user-identifier",
  "name": "Dr. Jane Smith",
  "email": "jane.smith@clinic.ca",
  "professional_designation": "MD",
  "regulatory_college": "CPSO",
  "registration_number": "123456",
  "service_entitlements": ["DHIR", "DHDR", "ClinicalViewer"],
  "organization": "Example Health Clinic",
  "organization_id": "org-12345",
  "uao_upi": "uao-identifier",
  "iat": 1706000000,
  "exp": 1706003600
}
```

#### Toolbar Configuration (Base64-encoded JSON)
```json
{
  "cms_url": "https://cms.ehealthontario.ca",
  "pcoi_url": "https://pcoi.ehealthontario.ca",
  "FHIR_iss": "https://fhir.ehealthontario.ca",
  "dhir_url": "https://dhir.ehealthontario.ca",
  "dhdr_url": "https://dhdr.ehealthontario.ca"
}
```

#### Authorization Response (Full Token Set)
```json
{
  "access_token": "<JWT>",
  "refresh_token": "<JWT>",
  "id_token": "<JWT>",
  "toolbar": "<Base64-encoded JSON>",
  "authzid": "authorization-identifier",
  "hub.topic": "hub-topic-subscription",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

### OAuth 2.0 Scopes

| Scope | Description |
|-------|-------------|
| `openid` | Base scope for authentication |
| `user/Patient.read` | Read patient demographics |
| `user/Immunization.read` | Read immunization records (DHIR) |
| `user/Immunization.write` | Write immunization records (DHIR) |
| `user/MedicationDispense.read` | Read medication dispenses (DHDR) |
| `user/Consent.write` | Write patient consent (PCOI) |
| `user/Context.read` | Read context session |
| `user/Context.write` | Write context session |
| `toolbar` | Access toolbar configuration |
| `azs` | Authorization service |

### FHIR Profiles Supported

| Profile | Description |
|---------|-------------|
| `ca-on-dhdr-profile-MedicationDispense` | Ontario DHDR medication dispense |
| `ca-on-consent-pcoi-profile-Consent` | Ontario PCOI consent |
| `ca-on-dhir-profile-Immunization` | Ontario DHIR immunization |
| `ca-on-dhir-profile-Patient` | Ontario DHIR patient |

---

## Supported Ontario Health Services

ONE ID provides access to the following provincial digital health services:

### ConnectingOntario ClinicalViewer

- **Purpose**: Secure, web-based portal providing real-time access to digital health records
- **Access**: Requires ONE ID + organization membership + privacy training
- **Onboarding**: 3-6 weeks end-to-end process
- **Training Required**: Privacy and Security Training, Medications Portlet Training

**Data Available**:
| Data Type | Source |
|-----------|--------|
| Laboratory Results | OLIS (3+ billion results from 23 labs) |
| Dispensed Medications | DHDR (including Narcotic Monitoring System) |
| Immunizations | DHIR (including COVaxON COVID-19 data) |
| Hospital Visits | Hospital Information Systems |
| Home Care Services | LHIN Home and Community Care |
| Mental Health Information | Mental Health reporting systems |
| Diagnostic Imaging | DI-CS (reports and images) |

**Contextual Launch**: Supports seamless launch with patient context from EMRs, PointClickCare (LTCHs), and other health information systems.

### ClinicalConnect

- **Purpose**: Regional clinical viewer for South West Ontario (Hamilton/Niagara region)
- **Access**: ONE ID + practice in South West Ontario region
- **Integration**: Available through ONE ID Application Federation
- **Features**: Desktop & mobile access, customizable settings, audit logging
- **Launch**: Contextual launch from primary care EMRs with patient context

### OTNhub (Ontario Telemedicine Network)

- **Purpose**: Virtual care platform for patient communication and specialist consultation
- **Services**: eConsult, video visits, secure messaging, peer consultation
- **Access**: ONE ID credentials + OTNhub membership application
- **Portal**: [otnhub.ca](https://otnhub.ca/)

### OLIS (Ontario Laboratories Information System)

- **Purpose**: Provincial repository for laboratory results
- **Data Types**: Lab results, microbiology, blood bank, pathology
- **Scale**: 23 contributing laboratories, 3+ billion results
- **Access**: Through ConnectingOntario, ClinicalConnect, or direct EMR integration

### DHIR (Digital Health Immunization Repository)

- **Purpose**: Provincial immunization records repository
- **Access**: Through clinical viewers, EMRs, or health information systems
- **Operations**: Read and write immunization records
- **COVID-19**: Includes COVaxON vaccination data in near real-time
- **FHIR Profile**: `ca-on-dhir-profile-Immunization`

### DHDR (Digital Health Drug Repository)

- **Purpose**: Provincial medication dispense records
- **Data Types**: Publicly-funded drugs, pharmacy services, monitored drugs (NMS)
- **Access**: Through clinical viewers or EMR integration
- **Operations**: Read medication dispenses
- **FHIR Profile**: `ca-on-dhdr-profile-MedicationDispense`

### PCOI (Provincial Consent of Information)

- **Purpose**: Patient consent management for health information sharing
- **Operations**: Read and write patient consent directives
- **FHIR Profile**: `ca-on-consent-pcoi-profile-Consent`

### ONE Mail

- **Purpose**: Secure healthcare communication between providers
- **Access**: ONE ID credentials
- **Features**: Encrypted messaging, document sharing

### EMR Integration Initiative

OntarioMD partners with EMR vendors to integrate certified EMRs directly with:
- **DHDR**: Direct medication dispense access
- **DHIR**: Direct immunization record access and submission
- **OLIS**: Direct laboratory results access

---

## Current OpenO Implementation

### Architecture Overview

```
+------------------------------------------------------------------------+
|                     OpenO ONE ID Integration                            |
+------------------------------------------------------------------------+
|                                                                         |
|  +------------------+     +------------------+     +------------------+ |
|  |  ONE ID Button   |     |  SAML2 Request   |     |   IDP Login      | |
|  |  (Login Page)    | --> |  (Auth Redirect) | --> |   (ONE ID)       | |
|  +------------------+     +------------------+     +------------------+ |
|                                                               |         |
|                                                               v         |
|  +------------------+     +------------------+     +------------------+ |
|  | Provider Session |     | Account Linking  | <-- | SAML2 Response   | |
|  |   (Established)  | <-- |  (First Login)   |     |   (Validated)    | |
|  +------------------+     +------------------+     +------------------+ |
|           |                                                             |
|           v                                                             |
|  +------------------+     +------------------+     +------------------+ |
|  | ClinicalConnect  |     |   OTN eConsult   |     |   DHIR/DHDR      | |
|  |     Viewer       |     |     Access       |     |    Access        | |
|  +------------------+     +------------------+     +------------------+ |
|                                                                         |
+------------------------------------------------------------------------+
```

### File Inventory

#### Core Authentication Classes

| File | Path | Purpose |
|------|------|---------|
| `SSOLogin2Action.java` | `ca.openosp.openo.login` | SAML2 SSO login/logout handling |
| `SsoAuthenticationManager.java` | `ca.openosp.openo.managers` | SAML2 settings builder and session creation |
| `SSOUtility.java` | `ca.openosp.openo.utility` | SSO URL builders and property getters |
| `LoginCheckLogin.java` | `ca.openosp.openo.login` | ONE ID key authentication lookup |
| `LoginFilter.java` | `ca.openosp.openo.sec` | Session validation filter |

#### Integration Classes

| File | Path | Purpose |
|------|------|---------|
| `ClinicalConnectViewer2Action.java` | `ca.openosp.openo.integration.clinicalconnect` | ClinicalConnect EHR viewer launch |
| `EConsult2Action.java` | `ca.openosp.openo.encounter.oscarConsultationRequest.pageUtil` | OTN eConsult launch |
| `EctDisplayEHR2Action.java` | `ca.openosp.openo.encounter.pageUtil` | EHR display in encounter |
| `EctDisplayEconsult2Action.java` | `ca.openosp.openo.encounter.pageUtil` | eConsult display in encounter |

#### Ontario Health Data Repository Integration

| File | Path | Purpose |
|------|------|---------|
| `DHIRSubmissionManager.java` | `ca.openosp.openo.managers` | DHIR immunization submission |
| `DHIRUtils.java` | `ca.openosp.openo.integration.dhir` | DHIR utility functions |
| `DHIRSubmissionLog.java` | `ca.openosp.openo.commn.model` | DHIR submission audit log |
| `OLISSearch2Action.java` | `ca.openosp.openo.olis` | OLIS laboratory search |
| `OLISResults2Action.java` | `ca.openosp.openo.olis` | OLIS results display |
| `OLISPoller.java` | `ca.openosp.openo.olis` | OLIS polling for new results |
| `OLISSchedulerJob.java` | `ca.openosp.openo.olis` | OLIS scheduled polling job |
| `OLISHL7Handler.java` | `ca.openosp.openo.lab.ca.all.parsers` | OLIS HL7 message parsing |
| `DHIR.java` | `ca.openosp.openo.integration.fhir.api` | DHIR FHIR API interface |

#### Data Model Classes

| File | Path | Purpose |
|------|------|---------|
| `Security.java` | `ca.openosp.openo.commn.model` | Security model with ONE ID fields |
| `SecurityDao.java` | `ca.openosp.openo.commn.dao` | Security data access |
| `SecurityDaoImpl.java` | `ca.openosp.openo.commn.dao` | Security DAO implementation |

#### Configuration Files

| File | Purpose |
|------|---------|
| `onelogin.saml.properties` | SAML2 SP/IDP configuration template |
| `oscar_mcmaster.properties` | ONE ID enable flag and eConsult URLs |

### Security Model Fields

The `Security` model includes ONE ID authentication fields:

```java
// ONE ID fields in Security.java
@Column(name = "oneIdKey")
private String oneIdKey = "";      // ONE ID unique identifier (NameId)

@Column(name = "oneIdEmail")
private String oneIdEmail = "";    // ONE ID email address

@Column(name = "delegateOneIdEmail")
private String delagateOneIdEmail = "";  // Delegate's ONE ID email (note: typo in field name)
```

### Configuration Properties

#### oscar.properties - ONE ID Configuration

```properties
# Enable ONE ID login button
oneid.enabled=false

# ONE ID encryption key for token handling
oneid.encryptionKey=<encryption-key>

# eConsult backend/frontend URLs (uses ONE ID auth)
backendEconsultUrl=https://api.econsult.otnhub.ca
frontendEconsultUrl=https://econsult.otnhub.ca
econsultLoginTimeout=600
```

#### oscar.properties - SSO Federation Settings

```properties
# SSO Federation Server Links for SAML2 Authentication
sso.enabled=true
sso.entity.id=https://idp.ehealthontario.ca
sso.url.login=https://idp.ehealthontario.ca/sso/login
sso.url.logout=https://idp.ehealthontario.ca/sso/logout
sso.entity.metadata=https://idp.ehealthontario.ca/metadata
sso.idp.x509cert=<IDP-certificate>
sso.sp.entity.id=https://your-oscar-instance.ca
sso.encryptionKey=<encryption-key>
```

#### onelogin.saml.properties - SAML2 Configuration

```properties
# Service Provider Settings
onelogin.saml2.sp.entityid=
onelogin.saml2.sp.assertion_consumer_service.url=
onelogin.saml2.sp.assertion_consumer_service.binding=urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST
onelogin.saml2.sp.single_logout_service.url=
onelogin.saml2.sp.single_logout_service.binding=urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect
onelogin.saml2.sp.nameidformat=urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified

# Identity Provider Settings
onelogin.saml2.idp.entityid=
onelogin.saml2.idp.single_sign_on_service.url=
onelogin.saml2.idp.single_sign_on_service.binding=urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect
onelogin.saml2.idp.single_logout_service.url=
onelogin.saml2.idp.single_logout_service.binding=urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect
onelogin.saml2.idp.x509cert=

# Security Settings
onelogin.saml2.security.signature_algorithm=http://www.w3.org/2001/04/xmldsig-more#rsa-sha256
onelogin.saml2.security.digest_algorithm=http://www.w3.org/2001/04/xmlenc#sha256
onelogin.saml2.security.reject_deprecated_alg=true
```

### Key Methods

#### SSOLogin2Action.java

```java
/**
 * Handles SAML2 SSO login response.
 * Validates authentication, creates session, sets attributes.
 */
public String ssoLogin() {
    // Build SAML2 settings from properties
    // Process SAML response from IDP
    // Validate authentication
    // Create new session with provider data
    // Set session attributes: oneIdEmail, oneid_token
}

/**
 * Handles ONE ID callback for eConsult login.
 * Validates HMAC signature, decrypts token, links accounts.
 */
public String econsultLogin() {
    // Validate HMAC signature: sha256_HMAC(oneIdKey + oneIdEmail + token + timestamp)
    // Decrypt ONE ID token with AES/CBC/PKCS5PADDING
    // Validate session timeout
    // Link ONE ID to provider account (first login)
    // Set session attributes
}

/**
 * Redirects to IDP for authentication.
 */
public String authenticationRedirect() {
    // Build SAML2 settings
    // Create Auth request
    // Redirect to IDP login URL
}

/**
 * Handles SAML2 Single Logout.
 */
public String ssoLogout() {
    // Process Single Logout Request/Response
    // Invalidate session
}
```

#### SsoAuthenticationManager.java

```java
/**
 * Builds SAML2 settings for authentication request.
 */
public Saml2Settings buildAuthenticationRequestSettings(String user_email, String context) {
    // Load base properties from onelogin.saml.properties
    // Set SP assertion consumer service URL
    // Set SP single logout service URL
    // Set IDP entity ID, login/logout URLs, certificate from oscar.properties
    // Build and return Saml2Settings
}

/**
 * Validates SSO login and creates session data.
 */
public Map<String, Object> checkSSOLogin(Auth auth) {
    // Extract attributes from SAML response
    // Get NameId (ONE ID identifier)
    // Validate provider exists with ONE ID key
    // Create session data with provider information
    // Set ONE ID email and token in session
}
```

#### ClinicalConnectViewer2Action.java

```java
/**
 * Launches ClinicalConnect EHR viewer with patient context.
 */
public String launch() {
    // Validate ONE ID token in session
    // Build SAML2 login URL to backend eConsult
    // Get context session ID
    // Set patient context (HIN, demographics)
    // Redirect to ClinicalConnect viewer
}

/**
 * Launches ClinicalConnect without patient context.
 */
public String launchNonPatientContext() {
    // Validate ONE ID token
    // Get context session ID
    // Redirect to ClinicalConnect viewer
}
```

### Session Attributes

| Attribute | Description | Source |
|-----------|-------------|--------|
| `oneIdEmail` | ONE ID email address | SAML NameId or callback parameter |
| `oneid_token` | ONE ID access token | Decrypted from callback |
| `delegateOneIdEmail` | Delegate's ONE ID email | Security record |
| `user` | Provider number | Security record |
| `userfirstname` | Provider first name | Provider record |
| `userlastname` | Provider last name | Provider record |
| `userrole` | Provider role | Security record |
| `nameId` | SAML NameId | SAML response |
| `sessionIndex` | SAML session index | SAML response |
| `attributes` | SAML attributes map | SAML response |

### Security Features

1. **HMAC Signature Verification**
   - Algorithm: HmacSHA256
   - Format: `sha256_HMAC(oneIdKey + oneIdEmail + encryptedOneIdToken + timestamp)`
   - Prevents token tampering

2. **AES Token Encryption**
   - Algorithm: AES/CBC/PKCS5PADDING
   - Format: `encrypted:iv` (Base64-encoded)
   - Protects token in transit

3. **Session Timeout Validation**
   - Configurable via `econsultLoginTimeout` property
   - Validates timestamp to prevent replay attacks

4. **Account Linking Security**
   - First-time login links ONE ID to existing provider account
   - Prevents overwriting existing ONE ID associations
   - Logs all account linking events

5. **SAML Security Options**
   - SHA-256 signature algorithm (deprecated algorithms rejected)
   - Optional request/response signing
   - Optional assertion encryption

### Database Schema

```sql
-- Security table ONE ID fields (added in update-2016-08-30.sql)
ALTER TABLE security ADD COLUMN oneIdKey VARCHAR(255);
ALTER TABLE security ADD COLUMN oneIdEmail VARCHAR(255);
ALTER TABLE security ADD COLUMN delegateOneIdEmail VARCHAR(255);
```

### Contextual Launch Flow

The contextual launch allows users to access provincial services with patient context from the EMR:

```
+---------------+     +---------------+     +---------------+     +---------------+
|    OpenO EMR  |     |  ONE ID IDP   |     | Clinical      |     | Context       |
|  (User clicks | --> | (Authenticate | --> | Viewer        | --> | Manager       |
|   EHR button) |     |   via SAML)   |     | (Launch URL)  |     | (Set Patient) |
+---------------+     +---------------+     +---------------+     +---------------+
                                                    |
                                                    v
                                            +---------------+
                                            | Patient Data  |
                                            | (DHIR, DHDR,  |
                                            |  OLIS, etc.)  |
                                            +---------------+
```

**Contextual Launch Parameters**:
| Parameter | Description |
|-----------|-------------|
| `oneid_token` | ONE ID access token from session |
| `contextSessionId` | Context Manager session ID |
| `patientHIN` | Patient Health Insurance Number |
| `patientDOB` | Patient Date of Birth |
| `patientGender` | Patient Gender |
| `providerUPI` | Provider Unique Person Identifier (UAO) |

**Context Manager API**:
- **Set Context**: `PUT /context/{sessionId}` with patient identifiers
- **Get Context**: `GET /context/{sessionId}` to retrieve current patient
- **Clear Context**: `DELETE /context/{sessionId}` to end patient context

---

## Comparison with Other Forks

### Feature Matrix

| Feature | Oscar 19 | OscarPro | OpenO |
|---------|----------|----------|-------|
| **Core ONE ID Authentication** |
| SAML2 SSO Login | Yes | Yes | Yes (Struts2) |
| ONE ID Button (Login Page) | Yes | Yes | Yes |
| Account Linking | Yes | Yes | Yes |
| HMAC Signature Verification | Yes | Yes | Yes |
| AES Token Decryption | Yes | Yes | Yes |
| Session Timeout Validation | Yes | Yes | Yes |
| **Security Model Fields** |
| Security.oneIdKey | Yes | Yes | Yes |
| Security.oneIdEmail | Yes | Yes | Yes |
| Security.delagateOneIdEmail | Yes | Yes | Yes |
| **Integration Features** |
| ClinicalConnect Viewer | Yes | Yes | Yes (Struts2) |
| OTN eConsult Launch | Yes | Yes | Yes (Struts2) |
| EHR Display in Encounter | Yes | Yes | Yes (Struts2) |
| **OscarPro-Exclusive Features** |
| OneIdSession Entity | No | Yes | No |
| OneIdViewlet Entity | No | Yes | No |
| OneIdFilter | No | Yes | No |
| OneIdSessionDao | No | Yes | No |
| OneIdViewletDao | No | Yes | No |
| OneIDUtil | No | Yes | No |
| SystemPreferences.oneId* | No | Yes | No |
| Session Near-Expiry Check | No | Yes | No |
| **Shared Oscar 19 & OscarPro Features (Not in OpenO)** |
| OneIdGatewayData | Yes | Yes | No |
| OneIDTokenUtils | Yes | Yes | No |
| JWT Token Handling | Yes | Yes | No |
| Toolbar URL Extraction | Yes | Yes | No |
| **OpenO-Specific Features** |
| Struts2 Migration | No | No | Yes |
| Enhanced Error Handling | Basic | Basic | Enhanced |

### Key File Comparison

| File Type | Oscar 19 | OscarPro | OpenO |
|-----------|----------|----------|-------|
| SSO Login Action | `SSOLoginAction.java` | `SSOLoginAction.java` | `SSOLogin2Action.java` |
| SSO Auth Manager | `SsoAuthenticationManager.java` | `SsoAuthenticationManager.java` | `SsoAuthenticationManager.java` |
| Clinical Connect | `ClinicalConnectViewerAction.java` | `ClinicalConnectViewerAction.java` | `ClinicalConnectViewer2Action.java` |
| EConsult Action | `EConsultAction.java` | `EConsultAction.java` | `EConsult2Action.java` |
| OneId Session | Not present | `OneIdSession.java` | Not present |
| OneId Viewlet | Not present | `OneIdViewlet.java` | Not present |
| OneId Gateway Data | `OneIdGatewayData.java` | `OneIdGatewayData.java` | Not present |
| OneId Filter | Not present | `OneIdFilter.java` | Not present |
| OneID Token Utils | `OneIDTokenUtils.java` | `OneIDTokenUtils.java` | Not present |
| OneID Util | Not present | `OneIDUtil.java` | Not present |

### OscarPro-Specific ONE ID Features

OscarPro includes additional ONE ID integration components:

#### OneIdSession Entity
```java
@Entity
public class OneIdSession extends AbstractModel<Object> {
    private String providerNo;          // Primary key
    private String accessToken;         // JWT access token
    private String refreshToken;        // Token refresh
    private String idToken;             // Identity token
    private String subject;             // ONE ID subject
    private String email;               // Provider email
    private String serviceEntitlements; // Authorized services
    private String hubTopic;            // Hub topic subscription
    private String uaoUpi;              // UAO identifier
    private String uaoName;             // UAO name
    private String authorizationId;     // Authorization ID
    private String toolbar;             // JSON toolbar config (base64)
    private long timestamp;             // Token timestamp
    private Date lastKeptActive;        // Session keep-alive
    private boolean sso;                // SSO flag

    public boolean isExpired() { ... }
    public String getUrlFromToolbar(String key) { ... }
}
```

#### OneIdGatewayData
```java
public class OneIdGatewayData implements Serializable {
    private String accessTokenStr;
    private String refreshTokenStr;
    private String idTokenStr;
    private String hubTopic;
    private String authorizationId;
    private String uao;
    private String uaoFriendlyName;
    private String cmsUrl;
    private String pcoiUrl;
    private String fhirIss;
    private String scope;
    private String _profile;

    // Full OAuth scopes
    public static String[] fullScope = {
        "openid", "user/MedicationDispense.read", "toolbar",
        "user/Context.read", "user/Context.write", "user/Consent.write",
        "user/Immunization.read", "user/Immunization.write",
        "user/Patient.read", "azs"
    };

    public boolean isAccessTokenExpired() { ... }
    public boolean isRefreshTokenExpired() { ... }
    public void processToolBar(String toolbarStr) { ... }
}
```

#### OneIDUtil
```java
public class OneIDUtil {
    public static String getLoginRedirectUrl(ServletRequest request) { ... }
    public static String getLoginMessage(ServletRequest request) { ... }

    /**
     * Checks if session is within 3 minutes of expiry.
     * Uses oneid.idp_session_refresh_period system preference (default: 55 minutes).
     */
    public static boolean isSessionNearExpiry(Date lastKeptActive) { ... }
}
```

#### OneIdFilter (Request-Level Validation)
```java
public class OneIdFilter implements Filter {
    private final OneIdSessionDao oneIdSessionDao;
    private final OneIdViewletDao oneIdViewletDao;

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        // Get logged-in user
        // Retrieve OneIdSession from database
        // Check if session data changed (UAO, lastKeptActive, accessToken)
        // Rebuild OneIdGatewayData if needed
        // Set gateway data in HTTP session with:
        //   - Access/Refresh/ID tokens
        //   - UAO (Under Authority Of) information
        //   - CMS URL, PCOI URL, FHIR ISS from toolbar
        //   - Session keep-alive timestamp
        // Continue filter chain
    }
}
```

#### OneIDTokenUtils
```java
public class OneIDTokenUtils {
    // URL encoding utility for token parameters
    public static String urlEncode(String toEncode) { ... }

    // JWT token debugging
    public static void debugToken(DecodedJWT token) { ... }

    // Extract claims from JWT
    public static Map<String, Claim> getClaims(String token) { ... }
}
```

---

## Delta Assessment

### Features OpenO Has That Others Don't

| Feature | Description | Function |
|---------|-------------|----------|
| Struts2 Migration | Modern Struts2 framework | Aligns with OpenO architecture direction |
| Enhanced Error Handling | More detailed error messages | Additional debugging information |

### Features OscarPro Exclusively Has (Not in Oscar 19 or OpenO)

| Feature | Description | Function | Effort |
|---------|-------------|----------|--------|
| `OneIdSession` | Persistent session tracking | Session recovery capability | Medium |
| `OneIdViewlet` | Configurable eChart viewlets | UI customization | Low |
| `OneIdFilter` | Request-level validation | Additional request filtering | Medium |
| `OneIdSessionDao` | Session persistence | Database session storage | Low |
| `OneIdViewletDao` | Viewlet storage | Configuration persistence | Low |
| `OneIDUtil` | Login utilities | Session expiry checking | Low |
| Session Near-Expiry | Proactive re-auth | Pre-expiry notification | Low |
| SystemPreferences.oneId* | Centralized settings | Admin configuration | Low |

### Shared Oscar 19 & OscarPro Features (Not in OpenO)

| Feature | Description | Function | Effort |
|---------|-------------|----------|--------|
| `OneIdGatewayData` | JWT token parsing/management | OIDC token handling | Medium |
| `OneIDTokenUtils` | Token utilities | Token helper functions | Low |
| JWT Token Handling | Automatic expiration | Token lifecycle management | Medium |
| Toolbar URL Extraction | CMS/PCOI URL parsing | Service URL extraction | Low |

### Assessment Summary

1. **OpenO**: Modern Struts2 framework, enhanced error handling
2. **Oscar 19 + OscarPro Shared**: JWT token parsing (`OneIdGatewayData`, `OneIDTokenUtils`) for OIDC support
3. **OscarPro Additional Features**: Session persistence, request-level validation, proactive session management
4. **Difference**: OpenO does not include the JWT token handling classes that Oscar 19 and OscarPro have, nor OscarPro's additional session management features

---

## Potential Recommended Enhancements

The following are potential enhancements that could be considered if enhanced ONE ID functionality is desired.

### Potential Enhancement 1: ONE ID Gateway Data (Medium Effort)

**Purpose**: Could provide comprehensive JWT token parsing and management for full OIDC support.

#### 1.1 Create OneIdGatewayData Class

**File**: `src/main/java/ca/openosp/openo/integration/oneid/OneIdGatewayData.java`

```java
package ca.openosp.openo.integration.oneid;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import net.sf.json.JSONObject;
import org.apache.commons.codec.binary.Base64;

import java.io.Serializable;
import java.time.Instant;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

/**
 * Manages ONE ID gateway data including JWT tokens and toolbar configuration.
 *
 * @since 2026-01-22
 */
public class OneIdGatewayData implements Serializable {

    private String accessTokenStr;
    private String refreshTokenStr;
    private String idTokenStr;
    private String hubTopic;
    private String authorizationId;
    private JSONObject endPointToolbar;
    private DecodedJWT accessToken;
    private DecodedJWT refreshToken;
    private DecodedJWT idToken;
    private String uniqueSessionId;

    public static final String[] FULL_SCOPE = {
        "openid", "user/MedicationDispense.read", "toolbar",
        "user/Context.read", "user/Context.write", "user/Consent.write",
        "user/Immunization.read", "user/Immunization.write",
        "user/Patient.read", "azs"
    };

    public OneIdGatewayData() {
        this.uniqueSessionId = UUID.randomUUID().toString();
    }

    public void processOneIdString(String oneIdString) {
        if (oneIdString != null) {
            JSONObject tokens = JSONObject.fromObject(oneIdString);
            accessTokenStr = tokens.optString("access_token");
            refreshTokenStr = tokens.optString("refresh_token");
            idTokenStr = tokens.optString("id_token");
            setAuthorizationId(tokens.optString("authzid"));
            processAccessToken(accessTokenStr);
            processRefreshToken(refreshTokenStr);
            processIdToken(idTokenStr);
            processToolBar(tokens.getString("toolbar"));
            if (tokens.containsKey("hub.topic")) {
                hubTopic = tokens.getString("hub.topic");
            }
        }
    }

    public boolean isAccessTokenExpired() {
        if (accessToken == null) return true;
        long iat = accessToken.getClaim("iat").asLong();
        int expiresIn = accessToken.getClaim("expires_in").asInt();
        Date date = Date.from(Instant.ofEpochSecond(iat));
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.SECOND, expiresIn);
        Calendar future = Calendar.getInstance();
        future.add(Calendar.SECOND, 15);
        return cal.getTime().before(future.getTime());
    }

    // Additional methods...
}
```

### Potential Enhancement 2: ONE ID Session Persistence (Medium Effort)

**Purpose**: Could enable persistent session tracking with automatic expiration handling.

#### 2.1 Create OneIdSession Entity

**File**: `src/main/java/ca/openosp/openo/integration/oneid/OneIdSession.java`

```java
package ca.openosp.openo.integration.oneid;

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

    @Column(name = "hub_topic")
    private String hubTopic;

    @Column(name = "uao_upi")
    private String uaoUpi;

    @Column(name = "uao_name")
    private String uaoName;

    @Column(name = "authorization_id")
    private String authorizationId;

    @Column(name = "toolbar", columnDefinition = "TEXT")
    private String toolbar;

    @Column(name = "timestamp")
    private long timestamp;

    @Column(name = "last_kept_active")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastKeptActive;

    @Column(name = "sso")
    private boolean sso;

    @Override
    public String getId() {
        return providerNo;
    }

    public boolean isExpired() {
        if (this.accessToken == null) return true;
        try {
            DecodedJWT jwt = JWT.decode(this.accessToken);
            Date expiresAt = jwt.getExpiresAt();
            if (expiresAt == null) return true;
            Calendar future = Calendar.getInstance();
            future.add(Calendar.SECOND, 15);
            return expiresAt.before(future.getTime());
        } catch (Exception e) {
            return true;
        }
    }

    // Getters and setters...
}
```

#### 2.2 Database Migration

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
    hub_topic VARCHAR(255),
    uao_upi VARCHAR(255),
    uao_name VARCHAR(255),
    authorization_id VARCHAR(255),
    toolbar TEXT,
    timestamp BIGINT,
    last_kept_active DATETIME,
    sso BOOLEAN DEFAULT FALSE,
    INDEX idx_one_id_session_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### Potential Enhancement 3: ONE ID Utility Functions (Low Effort)

**Purpose**: Could provide utility functions for session expiry checking and login handling.

**File**: `src/main/java/ca/openosp/openo/utility/OneIDUtil.java`

```java
package ca.openosp.openo.utility;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Utility functions for ONE ID integration.
 *
 * @since 2026-01-22
 */
public class OneIDUtil {

    private static final int DEFAULT_SESSION_REFRESH_PERIOD = 55; // minutes

    /**
     * Checks if session is within 3 minutes of expiry.
     */
    public static boolean isSessionNearExpiry(Date lastKeptActive, int refreshPeriodMinutes) {
        if (lastKeptActive == null) return true;
        Date currentDate = new Date();
        Date refreshTime = new Date(
            lastKeptActive.getTime() + TimeUnit.MINUTES.toMillis(refreshPeriodMinutes)
        );
        return currentDate.getTime() > (refreshTime.getTime() - TimeUnit.MINUTES.toMillis(3));
    }
}
```

### Implementation Checklist (If Proceeding)

#### Phase 1: Gateway Data

- [ ] Create `OneIdGatewayData.java` class
- [ ] Add JWT token parsing methods
- [ ] Add toolbar configuration parsing
- [ ] Add token expiration checking
- [ ] Update `SSOLogin2Action` to use gateway data
- [ ] Add unit tests

#### Phase 2: Session Persistence

- [ ] Create `OneIdSession.java` entity
- [ ] Create database migration
- [ ] Create `OneIdSessionDao` interface and implementation
- [ ] Update `SSOLogin2Action` to persist sessions
- [ ] Add session recovery on login
- [ ] Add to `persistence.xml`

#### Phase 3: Utility Functions

- [ ] Create `OneIDUtil.java` utility class
- [ ] Add session expiry checking
- [ ] Update login page to show expiry warnings
- [ ] Add unit tests

### Files to Create (If Implementing)

| File | Type | Phase |
|------|------|-------|
| `OneIdGatewayData.java` | Utility Class | 1 |
| `OneIdSession.java` | JPA Entity | 2 |
| `OneIdSessionDao.java` | DAO Interface | 2 |
| `OneIdSessionDaoImpl.java` | DAO Implementation | 2 |
| `OneIDUtil.java` | Utility Class | 3 |
| `update-2026-XX-XX-one-id-session.sql` | Migration | 2 |

---

## References

### Official Ontario Health Documentation

- [ONE ID Account](https://ehealthontario.on.ca/en/health-care-professionals/one-id) - Main ONE ID information page
- [ONE ID Federation](https://ehealthontario.on.ca/en/health-care-professionals/one-id-federation) - Federation overview
- [ONE ID Application Federation](https://ehealthontario.on.ca/en/health-care-professionals/one-id-application-federation) - Application integration
- [ONE ID OpenID Connect Specification](https://ehealthontario.on.ca/en/standards/one-id-openid-connect-specification) - OIDC technical details
- [ONE ID SAML Interface Specification v1.4](https://ehealthontario.on.ca/files/public/support/Standards/ONEID_SAML_Interface_Spec_v1.4.pdf) - SAML technical specification
- [ONE ID OAuth2/OpenID Specification v1.5](https://ehealthontario.on.ca/files/public/support/Standards/ONEIDOAuth2OpenIDSpecification.pdf) - OAuth2/OIDC specification
- [ONE ID Identity Assurance Standard](https://ehealthontario.on.ca/files/public/support/ONE_ID/Registration_Community/one_id_identity_assurance_level_standard.pdf) - Identity assurance levels
- [ONE ID Policy v2.1](https://ehealthontario.on.ca/files/public/support/ONE_ID/Registration_Community/one_id_policy.pdf) - Governance policy
- [ONE ID LRA Procedures Manual](https://ehealthontario.on.ca/files/public/support/one_id_lra_procedures_manual.pdf) - Local Registration Authority guide
- [ONE ID LRA User Guide](https://ehealthontario.on.ca/files/public/support/ONE_ID/Registration_Community/one_id_local_registration_authority_user_guide.pdf) - LRA user documentation

### Registration Guides

- [ONE ID CPSO Registration Guide](https://ehealthontario.on.ca/files/public/support/ONE_ID/Registration_Community/one_id_cpso_registration_guide.pdf) - CPSO member self-registration
- [ONE ID CPSO Registration Guide (Cancer Care Ontario)](https://www.cancercareontario.ca/sites/ccocancercare/files/assets/ONE-ID-CPSO-Registration-Guide-for-New-Users-EN.pdf) - Alternative registration guide

### Ontario Health Services

- [ConnectingOntario ClinicalViewer](https://ehealthontario.on.ca/en/health-care-professionals/connectingontario) - Provincial clinical viewer
- [ClinicalConnect](https://ehealthontario.on.ca/en/health-care-professionals/clinicalconnect) - Regional clinical viewer (SW Ontario)
- [OTNhub](https://otnhub.ca/) - Telemedicine network portal
- [ONE ID FAQ](https://ehealthontario.on.ca/en/faqs/one-id) - Frequently asked questions
- [ONE ID and CPSO FAQ](https://ehealthontario.on.ca/en/faqs/health-care-professionals/one-id) - CPSO-specific FAQ
- [ConnectingOntario FAQ](https://ehealthontario.on.ca/en/faqs/connectingontario) - ClinicalViewer FAQ
- [OTNhub ONE ID FAQ](https://dropbox.otn.ca/pcvc/otn-selfserv-faq-oneid.pdf) - OTN-specific ONE ID FAQ
- [OntarioMD DHDR/DHIR Integration](https://www.ontariomd.ca/ontariomd-dhdr-dhir-emr-integration-vendors) - EMR integration initiative

### Integration Support

- **Architecture Team**: architecture@ehealthontario.on.ca
- **Service Desk** (24/7): 1-866-250-1554 or servicedesk@ehealthontario.on.ca
- **CPSO Physician Advisory**: 416-967-2606 or 1-800-268-7096 Ext. 606
- [ONE ID Product Sheet](https://ehealthontario.on.ca/files/public/support/ONE_ID_ProductSheet.pdf) - Product overview
- [ONE ID Support Portal](https://ehealthontario.on.ca/en/support/one-id) - Support resources

### Source Repositories

- **OscarPro Repository**: `bitbucket.org/oscaremr/oscarpro`
- **Oscar 19 Repository**: `bitbucket.org/oscaremr/oscar`
- **OpenO EMR Repository**: `github.com/open-osp/Open-O`

---

## Document History

| Version | Date       | Author                                        | Changes                                                                       |
|---------|------------|-----------------------------------------------|-------------------------------------------------------------------------------|
| 1.0     | 2026-01-22 | Michael Yingbull (Co-authored by Claude Code) | Initial assessment and documentation with platform technical details and code |
