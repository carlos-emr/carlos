# MedSeek/ClinicalConnect Integration Analysis

**Analysis Date**: 2026-01-28
**Issue**: #2167
**Parent Issue**: #2166 (Local Repo JAR Dependencies Audit)

## Executive Summary

**Recommendation**: **DO NOT REMOVE** - The MedSeek ClinicalConnect integration is actively implemented and functional. Removal would break existing functionality for installations using this integration.

## Background

OpenO EMR includes two MedSeek integration libraries in `local_repo/`:
- `SSOClinicalConnect` (version 20171101, from November 2017)
- `PatientService` (version 20161213, from December 2016)

These dependencies support integration with MedSeek ClinicalConnect, a healthcare information exchange platform. MedSeek was acquired by Optum (UnitedHealth Group) in 2018.

## Code Usage Analysis

### Core Integration Classes

#### 1. ClinicalConnectSSO.java
**Location**: `src/main/java/ca/openosp/openo/integration/clinicalconnect/ClinicalConnectSSO.java`
**Lines of Code**: 147
**Purpose**: Implements SSO authentication with MedSeek ClinicalConnect

**Key Functionality**:
- Creates WS-Security enabled SOAP clients for VmoService and PatientService
- Implements `getLaunchURL()` method for SSO redirect with patient context
- Handles patient token lookup via Health Card Number (HCN)
- Searches across multiple facilities to locate patient records
- Uses username token authentication with WS-Security headers

**Dependencies Used**:
```java
import com.medseek.clinical.service.*;
import com.medseek.clinical.service.GetDefaultLaunchUrl.Props;
```

#### 2. ClinicalConnectUtil.java
**Location**: `src/main/java/ca/openosp/openo/webserv/rest/util/ClinicalConnectUtil.java`
**Lines of Code**: 212
**Purpose**: Manages service configuration and credentials

**Key Functionality**:
- Stores and retrieves service credentials from `userproperty` table
- Implements AES-128 encryption for sensitive credentials
- Provides `isReady()` check for integration availability
- Generates and stores encryption keys in `DOCUMENT_DIR/SSOClinicalConnect/SecretKey`

**Database Storage**:
- `CLINICALCONNECT_SERVICE_USERNAME` (encrypted)
- `CLINICALCONNECT_SERVICE_PASSWORD` (encrypted)
- `CLINICALCONNECT_SERVICE_LOCATION` (URL endpoint)
- `CLINICALCONNECT_ID` (per-provider username)
- `CLINICALCONNECT_TYPE` (per-provider auth type)

### REST API Integration Points

#### PersonaService.java (Line 255)
```java
if (ClinicalConnectUtil.isReady(provider.getProviderNo())) {
    moreMenuList.add(idCounter++, bundle.getString("navbar.menu.clinicalconnect"),
                     null, "../commons/ClinicalConnectRedirect.jsp", "clinicalconnect");
}
```
**Purpose**: Conditionally adds ClinicalConnect menu item to navbar "More" dropdown when service is configured.

#### RecordUxService.java (Lines 216-219)
```java
if (securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null)) {
    if (ClinicalConnectUtil.isReady(loggedInInfo.getLoggedInProviderNo())) {
        String url = ClinicalConnectUtil.getLaunchURL(loggedInInfo, demographicNo.toString());
        morelist.add(new MenuItemTo1(idCounter++, "Launch ClinicalConnect", url));
    }
}
```
**Purpose**: Adds "Launch ClinicalConnect" option to patient record "More" menu, passing patient context.

#### ResourceService.java (Lines 127-134)
```java
@GET
@Path("/clinicalconnect")
@Produces("text/plain")
public String launchClinicalConnect(@Context HttpServletRequest request) {
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    if (ClinicalConnectUtil.isReady(loggedInInfo.getLoggedInProviderNo()))
        return ClinicalConnectUtil.getLaunchURL(loggedInInfo, null);
    else
        return null;
}
```
**Purpose**: Dedicated REST endpoint for ClinicalConnect SSO launch.

### User Interface Components

#### Admin Configuration Page
**Location**: `src/main/webapp/admin/clinicalconnect.jsp`
**Security**: Requires `_admin` or `_admin.misc` privileges
**Purpose**: Administrative interface for configuring ClinicalConnect service credentials

**Form Fields**:
- Service Username (password field, encrypted storage)
- Service Password (password field, encrypted storage)
- Service Location (text field, URL endpoint)

#### Redirect Handler
**Location**: `src/main/webapp/common/ClinicalConnectRedirect.jsp`
**Purpose**: Handles SSO redirect to ClinicalConnect or displays "NO ACCESS TO CLINICAL CONNECT" message

## Dependencies Status

### SSOClinicalConnect
- **Group**: `com.medseek.clinical.service`
- **Artifact**: `SSOClinicalConnect`
- **Version**: `20171101` (November 1, 2017)
- **Age**: 8 years, 3 months
- **Location**: `local_repo/com/medseek/clinical/service/SSOClinicalConnect/20171101/`
- **Files**:
  - `SSOClinicalConnect-20171101.jar` (compiled classes)
  - `SSOClinicalConnect-20171101.pom` (Maven metadata)

### PatientService
- **Group**: `com.medseek`
- **Artifact**: `PatientService`
- **Version**: `20161213` (December 13, 2016)
- **Age**: 9 years, 1 month
- **Location**: `local_repo/com/medseek/PatientService/20161213/`
- **Files**:
  - `PatientService-20161213.jar` (compiled classes)
  - `PatientService-20161213.pom` (Maven metadata)

## Integration Features

### Functionality Provided
- ✅ Single Sign-On (SSO) authentication to MedSeek ClinicalConnect
- ✅ Patient context passing via Health Card Number (HCN)
- ✅ Multi-facility patient record lookup
- ✅ Secure credential storage with AES-128 encryption
- ✅ Provider-specific configuration support
- ✅ Menu integration (navbar and patient record menu)
- ✅ Administrative configuration interface
- ✅ WS-Security SOAP client implementation

### Configuration Requirements
For the integration to be active, administrators must configure:
1. Service Username (ClinicalConnect service account)
2. Service Password (ClinicalConnect service account password)
3. Service Location (ClinicalConnect SOAP endpoint URL)
4. Per-provider ClinicalConnect username mappings
5. Per-provider authentication types

If not configured, integration features are hidden from the UI.

## Security Considerations

### Current Implementation Strengths
- ✅ Credentials encrypted with AES-128
- ✅ Encryption keys stored outside database (`DOCUMENT_DIR/SSOClinicalConnect/SecretKey`)
- ✅ WS-Security with username token authentication
- ✅ Integration conditionally enabled (must be explicitly configured)
- ✅ Security privilege checks before accessing integration

### Security Concerns
- ⚠️ Dependencies are 8-9 years old with no updates
- ⚠️ Not available in Maven Central (no automated security scanning)
- ⚠️ Unknown vulnerability status (no CVE scanning possible)
- ⚠️ Unclear if MedSeek/Optum still maintains these services
- ⚠️ No visible security patches or maintenance updates
- ⚠️ SOAP/WS-Security is older technology (modern integrations use REST/OAuth)

## MedSeek/Optum Context

### Company History
- **2018**: MedSeek acquired by Optum (UnitedHealth Group)
- **Post-acquisition**: Services likely rebranded or consolidated
- **Current Status**: Unknown if ClinicalConnect services still operational under Optum

### Integration Viability Questions
1. Are ClinicalConnect SOAP services still operational?
2. Has Optum provided migration path to newer APIs?
3. Are there updated Java client libraries available?
4. What is the roadmap for legacy MedSeek integrations?

## Removal Impact Assessment

### If Dependencies Are Removed

**Broken Functionality**:
- ❌ SSO to ClinicalConnect completely non-functional
- ❌ Patient context passing to external system lost
- ❌ Menu items will error if clicked (code references removed classes)
- ❌ Admin configuration page will throw ClassNotFoundException

**Code Changes Required**:
1. Remove `ClinicalConnectSSO.java` (147 lines)
2. Remove `ClinicalConnectUtil.java` (212 lines)
3. Remove references in `PersonaService.java`
4. Remove references in `RecordUxService.java`
5. Remove references in `ResourceService.java`
6. Remove `/admin/clinicalconnect.jsp`
7. Remove `/common/ClinicalConnectRedirect.jsp`
8. Remove dependencies from `pom.xml` (lines 1220-1231)
9. Remove JAR files from `local_repo/com/medseek/`

**Affected Users**:
- Any OpenO installation currently configured with ClinicalConnect credentials
- Healthcare organizations with active ClinicalConnect accounts
- Providers relying on integrated patient lookup across facilities

### If Dependencies Are Kept

**Benefits**:
- ✅ Maintains existing functionality
- ✅ No impact on current users
- ✅ Preserves integration option for installations that need it
- ✅ No code changes required

**Ongoing Concerns**:
- ⚠️ Continues use of outdated dependencies
- ⚠️ Requires manual security monitoring
- ⚠️ Unknown vulnerability exposure
- ⚠️ Technical debt accumulation

## Recommendations

### Primary Recommendation: Keep with Documentation

**Keep the integration** with the following actions:

1. **Document Legacy Status**
   - Add warning to admin configuration page: "ClinicalConnect uses legacy libraries last updated in 2017. Consult Optum for current integration options."
   - Update `CLAUDE.md` to document this as legacy integration
   - Add entry to technical debt tracking

2. **Add Monitoring**
   - Create internal security monitoring task for these dependencies
   - Document known limitations and age in release notes
   - Add to quarterly security review checklist

3. **Contact Optum**
   - Reach out to Optum technical support to inquire about:
     - Current status of ClinicalConnect services
     - Availability of updated Java client libraries
     - Recommended migration path for legacy integrations
     - Roadmap for MedSeek-acquired services

4. **Deprecation Planning**
   - If Optum confirms services are deprecated:
     - Add deprecation notice to UI (12-month sunset timeline)
     - Notify installations with active configurations
     - Plan removal for next major version

5. **Alternative Integration**
   - If Optum provides replacement services:
     - Implement new integration alongside legacy
     - Provide migration guide for affected installations
     - Deprecate old integration after transition period

### Alternative: Conditional Removal

Only remove if ALL of the following conditions are met:
- [ ] Confirmed no OpenO installations are using ClinicalConnect integration
- [ ] Optum confirms ClinicalConnect services are permanently discontinued
- [ ] No alternative integration path is available or needed
- [ ] Community consensus supports removal

## Testing Strategy

### If Keeping (Current Recommendation)
- ✅ No changes needed, existing code continues to function
- Document integration status in release notes
- Add to manual security review process

### If Updating with New Libraries
- Test SSO authentication flow with new libraries
- Verify patient context passing still works
- Test multi-facility patient lookup
- Ensure backward compatibility with stored credentials
- Validate WS-Security authentication

### If Removing
- Remove dependencies from `pom.xml`
- Verify build succeeds without MedSeek libraries
- Test application startup (no ClassNotFoundException)
- Verify UI gracefully handles missing integration
- Test affected REST endpoints return appropriate errors
- Ensure no configuration errors for installations without ClinicalConnect

## Conclusion

The MedSeek ClinicalConnect integration is **actively used and functional** in OpenO EMR. While the dependencies are outdated (8-9 years old), they provide important integration functionality for installations that need to connect with ClinicalConnect services.

**Recommendation**: **Keep the integration** but document its legacy status, contact Optum for updated libraries or migration guidance, and plan for future deprecation if replacement services become available.

**Next Steps**:
1. Mark issue #2167 as "Keep - Legacy Integration"
2. Add documentation to `CLAUDE.md` about ClinicalConnect being legacy
3. Create task to contact Optum regarding ClinicalConnect service status
4. Add to technical debt tracking with quarterly review
5. Close this investigation with findings documented

## References

- Parent Issue: #2166 (Local Repo JAR Dependencies Audit)
- MedSeek Acquisition: https://www.optum.com/ (2018)
- Maven Central Search: https://central.sonatype.com/ (no MedSeek libraries found)

## Appendix: File Inventory

### Java Classes (2 files)
```
src/main/java/ca/openosp/openo/integration/clinicalconnect/ClinicalConnectSSO.java (147 lines)
src/main/java/ca/openosp/openo/webserv/rest/util/ClinicalConnectUtil.java (212 lines)
```

### REST Services (3 references)
```
src/main/java/ca/openosp/openo/webserv/rest/PersonaService.java (line 255)
src/main/java/ca/openosp/openo/webserv/rest/RecordUxService.java (lines 216-219)
src/main/java/ca/openosp/openo/webserv/rest/ResourceService.java (lines 127-134)
```

### JSP Pages (2 files)
```
src/main/webapp/admin/clinicalconnect.jsp (100 lines)
src/main/webapp/common/ClinicalConnectRedirect.jsp (37 lines)
```

### Maven Dependencies (2 entries)
```
pom.xml lines 1220-1224: SSOClinicalConnect
pom.xml lines 1227-1231: PatientService
```

### Local Repository JARs (4 files)
```
local_repo/com/medseek/clinical/service/SSOClinicalConnect/20171101/SSOClinicalConnect-20171101.jar
local_repo/com/medseek/clinical/service/SSOClinicalConnect/20171101/SSOClinicalConnect-20171101.pom
local_repo/com/medseek/PatientService/20161213/PatientService-20161213.jar
local_repo/com/medseek/PatientService/20161213/PatientService-20161213.pom
```

### Database Storage (5 properties)
```
UserProperty.CLINICALCONNECT_SERVICE_USERNAME (encrypted)
UserProperty.CLINICALCONNECT_SERVICE_PASSWORD (encrypted)
UserProperty.CLINICALCONNECT_SERVICE_LOCATION (plain text URL)
UserProperty.CLINICALCONNECT_ID (per-provider, plain text)
UserProperty.CLINICALCONNECT_TYPE (per-provider, plain text)
```
