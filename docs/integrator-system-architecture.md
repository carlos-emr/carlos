# Oscar Integrator System Architecture

## Overview

The Oscar Integrator is a comprehensive **inter-EMR** data sharing system that allows multiple **separate Oscar EMR installations** to share patient data securely across a healthcare network. This is distinct from Oscar's internal multi-site management system.

### Critical Terminology Distinction

**"Sites" in Integrator Context** = Separate Oscar EMR installations connected via integrator
**"Sites" in Oscar Core** = Physical clinic locations within a single Oscar installation

These are completely different systems with different purposes and data models.

## System Components

### 1. Core Configuration

#### Global Properties
```properties
# Master kill switch for entire integrator system
INTEGRATOR_ENABLED=false|true

# Optimization for single-facility installations  
INTEGRATOR_LIMIT_TO_FACILITY=true|false

# Background task configuration
INTEGRATOR_UPDATE_PERIOD=300000  # milliseconds
INTEGRATOR_FORCE_FULL=no|yes

# Local data storage
INTEGRATOR_LOCAL_STORE=yes|no
FORCED_ROSTER_INTEGRATOR_LOCAL_STORE=yes

# Web service configuration
INTEGRATOR_COMPRESSION_ENABLED=true|false
INTEGRATOR_LOGGING_ENABLED=true|false
INTEGRATOR_MAX_FILE_SIZE=10485760

# Authentication
INTEGRATOR_USER=-1  # Provider number for system operations
```

#### Per-Facility Configuration
Each facility can connect to different integrator servers with individual settings stored in the `Facility` table:

```sql
-- Key integrator fields in Facility table
integratorEnabled tinyint(1)       -- Whether facility participates
integratorUrl varchar(255)         -- Integrator server URL
integratorUser varchar(255)        -- Authentication username  
integratorPassword varchar(255)    -- Authentication password
enableIntegratedReferrals tinyint(1)
enableHealthNumberRegistry tinyint(1)
```

### 2. Architecture Overview: Two Separate Multi-Location Systems

#### System 1: Oscar Core Site Management (Intra-EMR)
**Purpose**: Manage multiple physical clinic locations within a **single Oscar installation**

```sql
-- Site table for clinic locations within one Oscar
CREATE TABLE site (
    site_id int primary key auto_increment,
    name varchar(255),           -- "Downtown Clinic", "Suburban Branch"
    short_name varchar(255),
    address varchar(255),
    phone varchar(255),
    bg_color varchar(255),       -- UI customization per clinic
    site_url varchar(100),
    -- ... other clinic info
);

-- Provider assignment to clinic sites
CREATE TABLE providersite (
    provider_no varchar(6),
    site_id int,
    PRIMARY KEY (provider_no, site_id)
);
```

**Use Cases**: 
- Multi-location scheduling within one organization
- Provider assignment to specific clinic sites
- Billing site identification
- Per-location branding and configuration

#### System 2: Integrator Facility Management (Inter-EMR)
**Purpose**: Connect multiple **separate Oscar installations** for data sharing

The integrator follows a **per-facility configuration model** where each Oscar installation ("integrator site") can:
- Connect to different integrator server instances
- Use different authentication credentials  
- Enable/disable participation independently

Example integrator network setup:
- **Hospital A Oscar**: `integratorUrl = "https://integrator-west.health.org"`
- **Clinic B Oscar**: `integratorUrl = "https://integrator-east.health.org"`  
- **Practice C Oscar**: `integratorEnabled = false` (not participating)

#### Key Architectural Differences

| Aspect | Site System (Intra-EMR) | Integrator System (Inter-EMR) |
|--------|-------------------------|-------------------------------|
| **Scope** | Single Oscar installation | Multiple Oscar installations |
| **Data Model** | `Site` + `ProviderSite` | `Facility` + integrator fields |
| **Purpose** | Clinic location management | Cross-EMR data sharing |
| **Configuration** | Local clinic information | Web service URLs + credentials |
| **Provider Relations** | `providersite` table | `provider_facility` table |
| **Data Sharing** | Internal database | SOAP web services |
| **Authentication** | Local Oscar users | Integrator-specific credentials |

### 3. Integrator-Specific Classes and Components

**Note**: These are for the integrator system only, not the internal Site system.

#### Integrator Manager Classes
- **`CaisiIntegratorManager`**: Primary integration manager, handles web service connections to remote Oscar installations
- **`CaisiIntegratorUpdateTask`**: Background task for data synchronization between Oscar installations
- **`IntegratorLocalStoreUpdateJob`**: Manages local data caching from remote Oscar sites
- **`IntegratorFileLogUpdateJob`**: Handles file-based data exchange between Oscar installations
- **`MessengerIntegratorManager`**: Manages messaging integration across Oscar installations

#### Site System Classes (Separate from Integrator)
- **`Site`**: Local clinic location model (not used by integrator)
- **`ProviderSite`**: Provider-to-clinic assignments within single Oscar installation
- **`SitesManage2Action`**: Admin interface for managing clinic sites
- **`BillingSiteIdPrep`**: Billing site identification for multi-clinic Oscar installations

#### Web Service Integration
```java
// URL construction pattern
private static URL buildURL(Facility facility, String servicePoint) {
    return new URL(facility.getIntegratorUrl() + '/' + servicePoint + "?wsdl");
}

// Authentication setup
cxfClient.getOutInterceptors().add(
    new AuthenticationOutWSS4JInterceptorForIntegrator(
        facility.getIntegratorUser(), 
        facility.getIntegratorPassword(), 
        providerNo
    )
);
```

#### Data Models
- **`IntegratorConsent`**: Patient consent for data sharing
- **`IntegratorControl`**: Control settings per facility
- **`IntegratorProgress`**: Synchronization progress tracking
- **`IntegratorFileLog`**: File exchange logging
- **`CachedFacility`**: Remote facility information cache

### 4. Web Service APIs

The integrator communicates with remote servers via SOAP web services:

#### Key Service Endpoints
- **FacilityService**: `facility.getIntegratorUrl() + '/FacilityService?wsdl'`
- **DemographicService**: For patient demographic sharing
- **CaseManagementService**: For clinical notes sharing
- **PreventionService**: For immunization/prevention data
- **PrescriptionService**: For medication sharing
- **LabService**: For laboratory results sharing

#### Cached Data Types
- `CachedDemographic`: Remote patient demographics
- `CachedDemographicNote`: Remote clinical notes
- `CachedDemographicPrevention`: Remote prevention data
- `CachedFacility`: Remote facility information
- `CachedProgram`: Remote program data
- `CachedProvider`: Remote provider information

### 5. Data Flow Architecture

#### Outbound Data Push
1. **Background Task**: `CaisiIntegratorUpdateTask` runs periodically
2. **Data Collection**: Gathers local changes since last sync
3. **Web Service Call**: Pushes data to integrator server
4. **Progress Tracking**: Updates `IntegratorProgress` records

#### Inbound Data Pull
1. **Feature Detection**: Check `facility.isIntegratorEnabled()`
2. **Service Request**: Call remote web services for data
3. **Data Caching**: Store results in local cache (`basicDataCache`, `segmentedDataCache`)
4. **UI Integration**: Display remote data alongside local data

### 6. Feature Integration Points

#### Clinical Modules That Use Integrator (Inter-EMR)
- **Demographics**: Remote patient search and data viewing from other Oscar installations
- **Case Management**: View clinical notes from other Oscar EMR systems
- **Prescriptions**: Show medications prescribed at other Oscar installations
- **Laboratory**: Display lab results from other Oscar systems
- **Prevention**: Show immunizations from other Oscar installations
- **Messaging**: Cross-EMR provider communication
- **Documents**: Share documents between Oscar installations

#### Clinical Modules That Use Sites (Intra-EMR)
- **Scheduling**: Filter appointments by clinic location
- **Provider Management**: Assign providers to specific clinic sites
- **Billing**: Identify billing site for claims processing
- **Reports**: Generate reports by clinic location
- **UI Customization**: Different colors/branding per clinic site

#### UI Integration Pattern
```java
// Common pattern throughout Oscar codebase
if (loggedInInfo.getCurrentFacility().isIntegratorEnabled()) {
    // Show integrator features (remote data, extra columns, etc.)
    List<RemoteData> remoteData = integratorManager.getRemoteData(...);
} else {
    // Standard local-only functionality
}
```

### 7. Database Schema

#### Site System Tables (Intra-EMR)
```sql
-- Clinic locations within single Oscar installation
CREATE TABLE site (
    site_id int primary key auto_increment,
    name varchar(255),              -- "Main Campus", "Downtown Clinic"
    short_name varchar(255),        -- "MAIN", "DT"
    phone varchar(255),
    fax varchar(255),
    address varchar(255),
    city varchar(255),
    province varchar(255),
    postal varchar(255),
    bg_color varchar(255),          -- UI customization
    site_url varchar(100),
    provider_id_from int,           -- Provider ID range for this site
    provider_id_to int,
    status tinyint(1),
    site_logo_id int
);

-- Provider assignment to clinic sites
CREATE TABLE providersite (
    provider_no varchar(6),
    site_id int,
    PRIMARY KEY (provider_no, site_id)
);
```

#### Integrator System Tables (Inter-EMR)
```sql
-- Consent management
CREATE TABLE integrator_consent (
    id int primary key auto_increment,
    facility_id int not null,
    demographic_no int not null,
    consent_given tinyint(1),
    -- ... other fields
);

-- Control settings
CREATE TABLE integrator_control (
    id int primary key auto_increment,
    facility_id int not null,
    base_url varchar(255),
    -- ... other fields
);

-- Progress tracking
CREATE TABLE integrator_progress (
    id int primary key auto_increment,
    facility_id int not null,
    last_update_date datetime,
    -- ... other fields
);

-- File exchange logging
CREATE TABLE integrator_file_log (
    id int primary key auto_increment,
    facility_id int not null,
    filename varchar(255),
    -- ... other fields
);
```

#### Foreign Key Dependencies

**Site System Dependencies**:
- `provider` table via `providersite` - Provider assignment to clinic locations
- Independent of integrator system

**Integrator System Dependencies**:
- `Facility(id)` - Core facility configuration for integrator connections
- `demographic` - Patient consent and data mapping for cross-EMR sharing
- `provider` - Authentication and authorization for integrator access
- `provider_facility` - Provider access to integrator-enabled facilities

### 8. Security and Authentication

#### WS-Security Implementation
- Uses WS-Security headers for SOAP authentication
- Per-facility credentials: `integratorUser` and `integratorPassword`
- Provider context passed for authorization: `loggedInProviderNo`

#### Patient Consent
- `IntegratorConsent` table tracks patient consent per facility
- Consent required before sharing patient data
- Complex consent workflows supported via `IntegratorConsentComplexExitInterview`

### 9. Operational Aspects

#### Background Jobs
- **Startup**: Jobs only start if `INTEGRATOR_ENABLED=true`
- **Scheduling**: Uses Quartz scheduler for periodic tasks
- **Monitoring**: Progress tracked in `IntegratorProgress` table

#### Error Handling
- Connection failures set `INTEGRATOR_OFFLINE` session flag
- Fallback managers handle service unavailability
- Comprehensive logging when `INTEGRATOR_LOGGING_ENABLED=true`

#### Performance Optimization
- Two-tier caching: `basicDataCache` and `segmentedDataCache`
- Cache TTL: 1 hour (configurable)
- Optional facility limitation via `INTEGRATOR_LIMIT_TO_FACILITY`

### 10. Configuration Examples

#### Example 1: Single-Location Practice (No Sites, No Integrator)
```properties
INTEGRATOR_ENABLED=false
```
```sql
-- No site or facility configuration needed
-- Uses default Oscar setup
```

#### Example 2: Multi-Clinic Practice (Sites Only, No Integrator)
```properties
INTEGRATOR_ENABLED=false
```
```sql
-- Configure clinic locations
INSERT INTO site (name, short_name, address, bg_color, status) VALUES
('Main Campus', 'MAIN', '123 Medical Way', '#0066CC', 1),
('Downtown Clinic', 'DT', '456 City Street', '#CC6600', 1);

-- Assign providers to clinics
INSERT INTO providersite (provider_no, site_id) VALUES
('12345', 1),  -- Provider at Main Campus
('67890', 2);  -- Provider at Downtown Clinic
```

#### Example 3: Single-Location with Integrator
```properties
INTEGRATOR_ENABLED=true
INTEGRATOR_LIMIT_TO_FACILITY=true
```
```sql
-- Configure facility for integrator
UPDATE Facility SET 
    integratorEnabled=1,
    integratorUrl='https://integrator.hospital.org:8443/integrator',
    integratorUser='oscar_user',
    integratorPassword='encrypted_password'
WHERE id=1;
```

#### Example 4: Multi-Clinic with Integrator (Both Systems)
```properties
INTEGRATOR_ENABLED=true
INTEGRATOR_LIMIT_TO_FACILITY=false
```
```sql
-- Configure local clinic sites
INSERT INTO site (name, short_name, bg_color, status) VALUES
('North Campus', 'NORTH', '#0066CC', 1),
('South Campus', 'SOUTH', '#CC6600', 1);

-- Configure facility for integrator connection
UPDATE Facility SET 
    integratorEnabled=1,
    integratorUrl='https://regional-integrator.health.org:8443/integrator',
    integratorUser='hospital_system_user',
    integratorPassword='encrypted_password'
WHERE id=1;

-- Assign providers to both local sites AND integrator facility
INSERT INTO providersite (provider_no, site_id) VALUES ('12345', 1);
INSERT INTO provider_facility (provider_no, facility_id) VALUES ('12345', 1);
```

#### Example 5: Multi-Integrator Network Configuration
```properties
INTEGRATOR_ENABLED=true
INTEGRATOR_LIMIT_TO_FACILITY=false
```
```sql
-- Multiple Oscar installations connecting to different integrator networks
-- This would be configured on separate Oscar installations

-- Oscar Installation A (Hospital System)
UPDATE Facility SET 
    integratorEnabled=1,
    integratorUrl='https://hospital-integrator.health.org:8443/integrator',
    integratorUser='hospital_user',
    integratorPassword='hospital_password'
WHERE id=1;

-- Oscar Installation B (Clinic Network) - separate database
UPDATE Facility SET 
    integratorEnabled=1,
    integratorUrl='https://clinic-integrator.health.org:8443/integrator', 
    integratorUser='clinic_user',
    integratorPassword='clinic_password'
WHERE id=1;
```

### 11. Dependencies and Requirements

#### Critical Dependencies for Integrator System
- **Facility Model**: **Cannot be removed** - stores per-facility integrator configuration
- **Provider-Facility Relationships**: Required for multi-EMR provider access via `provider_facility` table
- **Web Services**: SOAP-based communication with integrator servers
- **Authentication**: WS-Security integration with facility credentials

#### Independent Systems (Can Coexist)
- **Site System**: Completely independent from integrator, uses `site` and `providersite` tables
- **Facility System**: Required for integrator, can also be used for local multi-location management
- **Both Systems**: Can run simultaneously - sites for local clinic management, facilities for integrator

#### Architecture Decision Points
Organizations have several configuration options:

1. **Single Location, No Integration**: Neither sites nor integrator needed
2. **Multiple Clinics, No Integration**: Use Site system only
3. **Single Location, With Integration**: Use Facility system for integrator only
4. **Multiple Clinics, With Integration**: Use both Site system (local) + Facility system (integrator)
5. **Facility-Only Multi-Location**: Use Facility system for both local management and integrator (PMmodule pattern)

#### Optional Components
- **PMmodule Integration**: Uses Facility model for shelter/program management (separate from integrator)
- **Consent Management**: Required only if patient consent tracking needed
- **File Exchange**: Alternative to real-time web service integration

### 12. Troubleshooting and Administration

#### Admin Pages
- `/admin/integratorStatus.jsp`: View integrator system status
- `/admin/integratorPushStatus.jsp`: Monitor data push progress  
- `/admin/setIntegratorProperties.jsp`: Configure integrator settings
- `/admin/viewIntegratedCommunity.jsp`: View participating facilities

#### Common Issues
1. **Connection Failures**: Check `integratorUrl` and network connectivity
2. **Authentication Errors**: Verify `integratorUser` and `integratorPassword`
3. **No Remote Data**: Ensure `integratorEnabled=true` for facility
4. **Background Task Not Running**: Verify `INTEGRATOR_ENABLED=true` in properties

#### Monitoring
- Check `IntegratorProgress` table for last successful sync times
- Review `IntegratorFileLog` for file exchange issues
- Monitor application logs when `INTEGRATOR_LOGGING_ENABLED=true`

## Conclusion

The Oscar Integrator is a sophisticated **inter-EMR** data sharing system that enables multiple separate Oscar installations to share patient data. It is architecturally distinct from Oscar's internal Site management system:

### Key Takeaways

1. **Two Separate Multi-Location Systems**:
   - **Site System**: Manages multiple clinic locations within one Oscar installation
   - **Integrator System**: Connects multiple separate Oscar installations for data sharing

2. **Different Data Models**:
   - Sites use `site` and `providersite` tables
   - Integrator uses `Facility` table with integrator-specific fields

3. **Independent Operation**:
   - Site system works without integrator
   - Integrator system works without sites
   - Both can coexist in the same Oscar installation

4. **Facility Model Dependency**:
   - **Cannot remove Facility model** if integrator is needed
   - Facility stores integrator URLs, credentials, and configuration
   - Each Oscar installation ("integrator site") needs facility configuration

5. **Configuration Flexibility**:
   - Small practices: Neither system needed
   - Multi-clinic organizations: Site system for local management
   - Health networks: Integrator system for data sharing
   - Complex organizations: Both systems simultaneously

6. **Disabling Integration**:
   - Set `INTEGRATOR_ENABLED=false` to completely disable integrator
   - Facility model still available for PMmodule or local multi-location use
   - Site system remains available for clinic location management

The integrator requires careful configuration and maintenance but provides powerful cross-EMR data sharing capabilities when properly implemented.