# OpenO Resources Directory Overview

## What is src/main/resources?

The `src/main/resources` directory is a critical component of the OpenO application that contains all non-Java resources required for the application to function. In a Maven project structure, this directory holds files that need to be available on the classpath at runtime but are not Java source code.

## Purpose and Importance

### Key Functions:
1. **Configuration Management** - Houses Spring contexts, properties files, and application settings
2. **Data Storage** - Contains medical forms, templates, schemas, and reference data
3. **Legacy Support** - Maintains historical versions for backward compatibility
4. **Provincial Customization** - Supports different Canadian provincial requirements (BC, ON)
5. **Clinical Decision Support** - Stores rules, flowsheets, and clinical guidelines

### Why It's Critical:
- **Runtime Dependencies** - Many files are loaded dynamically by name at runtime
- **Spring Context Loading** - Core application beans and configurations
- **Form Generation** - PDF templates and printing configurations for medical forms
- **Data Integrity** - Historical patient records require original form versions
- **Regulatory Compliance** - Medical standards and provincial requirements

## Directory Structure Summary

```
src/main/resources/
├── Spring Configuration (*.xml)          # Application contexts and bean definitions
├── Properties Files (*.properties)       # Configuration and message bundles
├── /oscar/                               # Main application resources
│   ├── /form/                           # Medical forms (140+ print configs, PDFs)
│   ├── /oscarRx/                        # Prescription module and ODB formulary
│   ├── /oscarEncounter/                 # Clinical encounters and flowsheets
│   ├── /oscarBilling/                   # Billing reports and templates
│   └── [other modules]                  # Module-specific resources
├── /com/quatro/model/                   # Hibernate mappings for security
├── /org/oscarehr/                       # Core module resources
├── /hapi_libs/fork/                     # Legacy HL7 libraries (DO NOT UPGRADE)
├── /META-INF/                           # JPA and container configuration
└── Various schemas and data files       # XSD, JSON, XML data files
```

## Key Characteristics

### Dynamic Loading Pattern
Resources are often loaded at runtime based on:
- Form names passed as HTTP parameters
- Provincial configuration (BC vs ON)
- Lab type configurations
- User language preferences

### Version Management
The directory maintains multiple versions of the same resource because:
- Medical forms evolve (Rourke 2006 → 2009 → 2017 → 2020)
- Historical patient data must be printable with original versions
- Different provinces update at different times
- Regulatory requirements mandate specific versions

### Technology Mix
- **Legacy**: Text-based print configurations, properties files
- **Modern**: Jasper Reports (JRXML), Drools rules, FHIR filters
- **Hybrid**: System supports both simultaneously for transition

## Critical Files That Cannot Be Removed

1. **All form versions** (even from 2006) - Required for historical records
2. **Legacy HAPI libraries** - Required for specific lab interfaces
3. **Spring configuration files** - Core application structure
4. **billing_invoice_email.properties** - Required for startup even if unused
5. **META-INF/persistence.xml** - Required by JPA configuration

## Size and Scope

- **Total Files**: ~500+ files
- **Total Directories**: ~75 directories
- **Critical for**: Form generation, billing, prescriptions, lab results, clinical decision support
- **Provincial Support**: Ontario (ON) and British Columbia (BC) specific resources

## Detailed Documentation

For comprehensive documentation of every file and directory:
- **Full Documentation**: [`/src/main/resources/README.md`](../src/main/resources/README.md)
- **Form Resources**: [`/src/main/resources/README-form-resources.md`](../src/main/resources/README-form-resources.md)

## Maintenance Warnings

⚠️ **DO NOT DELETE** files without careful analysis:
- Files are often loaded dynamically by name
- Missing files can cause runtime or startup failures
- Historical versions are required for data integrity
- Some "unused" files are required for startup (e.g., billing_invoice_email.properties)

⚠️ **DO NOT UPGRADE** legacy libraries without testing:
- HAPI 0.5.1 is specifically required for certain lab interfaces
- Libraries are dynamically loaded to avoid conflicts

## Related Documentation

- [Legacy HAPI Dynamic Loading](legacy-hapi-dynamic-loading.md)
- [Text Files Analysis](txt-files-analysis.md)
- [Build Configuration](../pom.xml)
- [Web Application Configuration](../src/main/webapp/WEB-INF/web.xml)