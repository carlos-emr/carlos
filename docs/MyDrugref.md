# MyDrugref Integration Documentation

## Overview

MyDrugref is an external drug reference and clinical decision support service that provides comprehensive medication information, drug interaction checks, clinical guidelines, and treatment recommendations to CARLOS EMR. This service enhances patient safety by providing real-time drug information at the point of care.

## Features and Capabilities

### 1. Drug Interaction Checking
- Real-time drug-drug interaction analysis
- Multi-drug interaction detection for complex medication regimens
- Severity levels for interactions (minor, moderate, major)
- Clinical significance ratings

### 2. Drug Warnings and Alerts
- **Age-based warnings**: Pediatric and geriatric considerations
- **Condition-based contraindications**: Pregnancy, lactation, renal/hepatic impairment
- **Allergy cross-reactivity alerts**
- **Black box warnings and serious adverse effects**

### 3. Clinical Guidelines
- Evidence-based prescribing guidelines
- Dosing recommendations by patient demographics
- Treatment protocols for specific conditions
- Best practice recommendations

### 4. Drug Bulletins and Updates
- Medication safety notices
- New drug information
- Regulatory updates and recalls
- Formulary changes

### 5. Treatment Recommendations
- Therapeutic alternatives
- Cost-effective options
- First-line vs. second-line treatments
- Combination therapy suggestions

## Technical Architecture

### Communication Protocol
MyDrugref uses **XML-RPC** (XML Remote Procedure Call) for communication:
- Lightweight remote procedure call protocol
- Uses HTTP as transport and XML for encoding
- Synchronous request-response pattern

### Integration Points in CARLOS EMR

#### 1. Prescription Module (`oscarRx`)
- **Location**: `/src/main/webapp/oscarRx/`
- **Key JSP Files**:
  - `WarningDisplayMyD.jsp` - Displays drug warnings
  - `InteractionDisplayMyD.jsp` - Shows drug interactions
  - `TreatmentMyD.jsp` - Treatment recommendations
  - `BulletinDisplayMyD.jsp` - Drug bulletins
  - `PriceDisplayMyD.jsp` - Pricing information

#### 2. Decision Support Service
- **Main Class**: `org.oscarehr.decisionSupport.service.DSServiceMyDrugref`
- **Responsibilities**:
  - Fetches clinical guidelines from MyDrugref
  - Validates guideline XML against schema
  - Stores guidelines locally for offline access
  - Maps guidelines to providers

#### 3. Drug Reference Action Handler
- **Class**: `oscar.oscarRx.pageUtil.RxMyDrugrefInfoAction`
- **Methods**:
  - `callOAuthService()` - Main interface for MyDrugref calls (uses XML-RPC)
  - Handles procedure name mapping between OpenO and MyDrugref

## API Procedures

### Available XML-RPC Procedures

| OpenO Procedure Name | MyDrugref XML-RPC Name | Purpose |
|---------------------|------------------------|---------|
| `atcfetch/getWarnings` | `Fetch` with `warnings_byATC` | Get drug warnings by ATC code |
| `atcfetch/getBulletins` | `Fetch` with `bulletins_byATC` | Get drug bulletins by ATC code |
| `atcfetch/getInteractions` | `Fetch` with `interactions_byATC` | Get drug interactions by ATC code |
| `guidelines/getGuidelineIds` | `GetGuidelineIds` | Retrieve available guideline IDs |
| `guidelines/getGuidelines` | `GetGuidelines` | Fetch full guideline content |

### Data Flow

1. **Provider initiates action** (e.g., prescribing medication)
2. **OpenO collects drug information** (ATC codes, drug IDs)
3. **Request sent to MyDrugref** via XML-RPC
4. **MyDrugref processes and returns data**
5. **OpenO validates and displays information**
6. **Provider makes informed clinical decision**

## Configuration

### Required Properties

#### System-wide Configuration
```properties
# MyDrugref server endpoint
MY_DRUGREF_URL=<mydrugref_server_url>

# Connection timeout (milliseconds)
mydrugref.timeout=10000
```

#### Provider-specific Configuration
Each provider needs a MyDrugref ID stored in the `property` table:
- **Property Name**: `mydrugref_id`
- **Property Value**: Provider's MyDrugref identifier
- Managed through User Properties

### Setup Steps

1. **Obtain MyDrugref Account**
   - Contact MyDrugref service provider
   - Receive provider-specific credentials

2. **Configure CARLOS EMR**
   - Set `MY_DRUGREF_URL` in system properties
   - Add provider IDs to user properties

3. **Test Connection**
   - Attempt to fetch guidelines
   - Verify drug interaction checks work

## Data Validation

### Guideline XML Schema
Guidelines received from MyDrugref are validated against `dsGuideline.xsd`:
- **Location**: `/src/main/resources/dsGuideline.xsd`
- **Purpose**: Ensures guideline structure integrity
- **Validation occurs**: Before storing guidelines locally

### Schema Structure
The XSD validates:
- **Conditions**: age, sex, diagnosis codes, drugs, notes
- **Consequences**: warnings, strength recommendations, consent requirements
- **Operators**: Logical conditions for guideline application

## Error Handling

### Connection Failures
- **Timeout**: Default 10 seconds, configurable
- **Fallback**: Uses cached guidelines if available
- **Logging**: Errors logged to application logs

### Invalid Data
- **XML Validation**: Rejects malformed guidelines
- **Status Tracking**: Failed guidelines marked with status 'F'
- **User Notification**: Appropriate messages displayed

## Caching and Performance

### Local Storage
- **Guidelines cached locally** in database
- **Version tracking** to detect updates
- **Provider-specific mappings** maintained

### Update Mechanism
1. Checks for new guideline versions
2. Fetches only updated guidelines
3. Validates before storing
4. Maintains audit trail

## Security Considerations

### Authentication
- Provider-specific MyDrugref IDs
- Stored securely in database
- Transmitted over HTTPS (when configured)

### Data Privacy
- Patient data not sent to MyDrugref
- Only drug codes and guideline requests transmitted
- Results associated with provider, not patient

## Troubleshooting

### Common Issues

#### No Drug Information Displayed
- **Check**: MyDrugref URL configuration
- **Verify**: Provider has valid MyDrugref ID
- **Test**: Network connectivity to MyDrugref server

#### Guidelines Not Updating
- **Review**: Error logs for validation failures
- **Check**: XML schema accessibility
- **Verify**: Database write permissions

#### Timeout Errors
- **Adjust**: Timeout setting in configuration
- **Check**: Network latency
- **Verify**: MyDrugref server status

### Log Locations
- **Application logs**: Check for MyDrugref-related errors
- **Classes to monitor**:
  - `DSServiceMyDrugref`
  - `RxMyDrugrefInfoAction`

## Maintenance

### Regular Tasks
1. **Monitor guideline updates** - Check for new versions
2. **Review error logs** - Identify connection issues
3. **Validate cached data** - Ensure integrity
4. **Update provider mappings** - As staff changes

### Database Tables
- **DSGuideline**: Stores guideline content
- **DSGuidelineProviderMapping**: Provider-guideline associations
- **property**: Provider MyDrugref IDs

## Future Considerations

### Potential Enhancements
- Implement connection pooling for better performance
- Add support for additional drug reference services
- Enhance caching strategies
- Implement failover to alternative services

### Migration Path
If migrating to a different drug reference service:
1. Implement new service adapter
2. Map procedure calls to new API
3. Update validation schemas if needed
4. Migrate provider credentials
5. Test thoroughly before switching

## Support and Resources

### Internal Resources
- Configuration files: System properties
- Schema documentation: `/docs/dsGuideline.md`
- Source code: `/src/main/java/org/oscarehr/decisionSupport/`

### External Dependencies
- XML-RPC library (Apache XML-RPC)
- MyDrugref service availability
- Network connectivity

---

*Last Updated: August 2025*
*Document Version: 1.0*