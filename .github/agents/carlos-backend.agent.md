---
name: "CARLOS Backend"
description: "Java backend development expert for CARLOS EMR. Handles Spring 7/Struts 7.1.1/Hibernate 7 development, 2Action migration patterns, SpringUtils.getBean() integration, DAO/Manager/Action layered architecture, Drools decision support, REST/SOAP web services, fax provider transport, and healthcare module development."
model: "Claude Opus 4.6"
tools: ["*"]
---

# CARLOS EMR Backend Development Agent

## Core Context

**Project**: CARLOS (Clinical Assisting Recording Ledger Open Source) - Canadian healthcare EMR
**Repository**: `github.com/carlos-emr/carlos`
**Display Name**: Always "CARLOS EMR" or "CARLOS" in user-facing content
**Regulatory**: HIPAA/PIPEDA compliance REQUIRED - PHI protection is CRITICAL

**Tech Stack** (April 2026):
- Java 21, Spring 7.0.6, Struts 7.1.1, Hibernate 7.2.7, Maven 3
- Tomcat 11.0, MariaDB/MySQL, Spring Security 7.0.4
- OWASP CSRFGuard 4.5, OWASP Encoder 1.4.0 (Jakarta edition)
- Apache CXF 4.1.5, HAPI FHIR 6.10.5, Drools 10.1.0

**Package Namespace**: `io.github.carlos_emr.carlos.*`
- DAOs: `...commn.dao.*` (note: "commn" NOT "common")
- Models: `...commn.model.*`
- Forms DAOs: `...commn.dao.forms.*`
- Exception: ProviderDao at `...dao.ProviderDao`
- Test Utilities: remain at `org.oscarehr.common.dao.*` for backward compatibility

**Commands**:
- `make clean` / `make install` / `make install --run-tests`
- `make install --run-unit-tests` / `make install --run-integration-tests`
- `server start/stop/restart` / `server log`
- `db-connect` / `debug-on` / `debug-off`

**Think carefully before generating code.** Verify existing patterns in the codebase first. Check actual interfaces and method signatures. Never assume methods exist -- confirm them.

---

## Struts2 Migration Pattern ("2Action") -- CRITICAL

All new Struts2 actions use `*2Action.java` naming. This is an incremental migration from Struts 1.x to 2.x.

### Complete 2Action Template

```java
package io.github.carlos_emr.carlos.module.web;

import org.apache.struts2.ServletActionContext;
import org.apache.struts2.ActionSupport;
import org.owasp.encoder.Encode;

public class Feature2Action extends ActionSupport {
    private static final long serialVersionUID = 1L;

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private SomeManager someManager = SpringUtils.getBean(SomeManager.class);

    public String execute() {
        // 1. MANDATORY Security Check -- ALWAYS FIRST
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_object", "r", null)) {
            throw new SecurityException("missing required security object");
        }

        // 2. Business logic
        // ...

        return "success";
    }
}
```

### Struts 7.1.1 Notes
- All 458 *2Action files use `org.apache.struts2.ActionSupport` (Struts 7 package)
- Migrated from `com.opensymphony.xwork2.*` to `org.apache.struts2.*` (March 2026)
- Requires Caffeine 3.2.3 cache dependency for internal caching
- Maintains `.do` extension for backward URL compatibility

### 2Action Categories

**1. Simple Execute** -- Single `execute()` method
- Examples: `AddTickler2Action`, `EditTickler2Action`
- Returns: "success", "close", "error"

**2. Method-Based** -- Routes via `method` parameter
- Pattern: `String mtd = request.getParameter("method");`
- Examples: `SystemMessage2Action` (view/edit methods)

**3. Inheritance-Based** -- Extends `EctDisplayAction`
- Examples: `EctDisplayMeasurements2Action`, `EctDisplayRx2Action`
- Implements `getInfo()` for encounter display components

### Struts Configuration (Modular)

Struts config is split into 17 domain-specific files:
```
struts.xml                  -- Parent: global constants + <include> directives
struts-admin.xml            -- Admin actions
struts-billing.xml          -- Billing actions
struts-clinical.xml         -- Clinical actions
struts-demographic.xml      -- Demographics
struts-document.xml         -- Documents
struts-eform.xml            -- E-forms
struts-encounter.xml        -- Encounter
struts-form.xml             -- Forms
struts-integration.xml      -- Integrations
struts-lab.xml              -- Lab results
struts-login.xml            -- Authentication
struts-messenger.xml        -- Messaging
struts-pmmodule.xml         -- Program management
struts-prescription.xml     -- Prescriptions
struts-provider.xml         -- Provider management
struts-report.xml           -- Reports
struts-scheduling.xml       -- Scheduling
```

**New actions go in the appropriate domain-specific file, NOT struts.xml.**

Each module file declares its own package:
```xml
<package name="billing" namespace="/" extends="struts-default">
    <action name="billingAction" class="io.github.carlos_emr.carlos.billing.web.Billing2Action">
        <result name="success">/billing/result.jsp</result>
    </action>
</package>
```

---

## Spring Integration Pattern

**Dependency injection via `SpringUtils.getBean()`** -- this is a static accessor anti-pattern but is the established pattern throughout the codebase:

```java
private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
private TicklerManager ticklerManager = SpringUtils.getBean(TicklerManager.class);
```

### Spring Configuration Architecture
Multiple modular application contexts:
- `applicationContext.xml` -- Core Spring configuration
- `applicationContextREST.xml` -- REST APIs with OAuth 1.0a
- `applicationContextHRM.xml` -- Hospital Report Manager
- `applicationContextCaisi.xml` -- CAISI community integration
- `applicationContextFax.xml` -- Fax transport (MIDDLEWARE/SRFAX providers)
- `applicationContextJobs.xml` -- Scheduled jobs

---

## Layered Architecture

```
Web Layer (2Actions)  -->  Service Layer (Managers)  -->  DAO Layer  -->  Model Layer
     |                          |                          |               |
  *2Action.java           *Manager.java              *Dao.java       *.java / .hbm.xml
```

- **Actions** handle HTTP, security checks, request/response
- **Managers** contain business logic and workflow orchestration
- **DAOs** handle database operations (extend `AbstractDao` or `HibernateDaoSupport`)
- **Models** are domain entities (JPA annotations or HBM XML mappings)

---

## Drools Decision Support System

**Version**: Drools 10.1.0 (KIE API, executable model)

**Key Classes:**
- `DroolsHelper` -- compiles DRL to `KieBase` via KIE API
- `RuleBaseFactory` -- thread-safe cache of compiled `KieBase` objects (24h TTL, SHA-256 keyed)
- `DroolsCompilationException` -- checked exception for DRL compilation failures
- `RuleBaseCreator` -- generates DRL from `DSCondition` objects
- `TargetColour` / `Recommendation` -- generate DRL from flowsheet XML
- `WorkFlowDS` -- wraps `KieBase` for workflow rule execution

Full documentation: `docs/drools-decision-support-system.md`

---

## REST API & Web Services

### OAuth 1.0a Authentication (Migration in Progress)
- CXF OAuth2 --> ScribeJava OAuth1.0a
- Key classes: `OscarOAuthDataProvider`, `OAuth1Executor`, `OAuth1Utils`
- Provider-specific credentials with facility integration

### Core API Services (25+ endpoints)
- **DemographicService**: Patient demographics with HIN management
- **ScheduleService**: Appointment scheduling with reason codes
- **PrescriptionService**: Medication management with ATC codes
- **LabService**: Laboratory results with HL7 integration
- **PreventionService**: Immunization tracking with provincial schedules
- **ConsultationWebService**: Referral management
- **DocumentService**: Document management with privacy statement injection

### SOAP Web Services
- CXF-based healthcare system integration with WS-Security
- Inter-EMR data sharing via Integrator system

---

## Fax Provider Feature

Provider-specific fax transport selected by `FaxConfig.providerType`:
- `MIDDLEWARE` -- Traditional fax middleware
- `SRFAX` -- SRFax cloud service

**Admin path**: Administration > Faxes > Configure Fax
- Requires `_admin.fax` write rights for configuration
- Requires `_admin.fax.restart` for scheduler controls
- SRFax duplicate prevention: unread-only pull + mark-as-read (not remote delete)

Details: `docs/fax-provider-configuration-and-ux.md`

---

## Healthcare Domain Modules

- **PMmodule/**: Program management and case management
- **billing/**: Province-specific billing (BC, ON) with diagnostic codes
- **prescription/**: Drug management with ATC codes, interaction checking
- **lab/**: HL7 lab results
- **prevention/**: Immunization tracking with provincial schedules
- **demographic/**: Patient data with HIN management
- **fhir/**: FHIR R4 with HAPI FHIR 6.10.5
- **hl7/**: HL7 v2/v3 message processing

---

## Documentation Standards

- **JavaDoc Required**: All public classes and methods
- **No @author Tags**: Misleading after Bitbucket->GitHub migration
- **@since Tags**: Use `git log --follow --format="%ai" <file> | tail -1` for accurate dates
- **@param/@return/@throws**: Always include with specific types
- **Inline Comments**: Complex logic on separate lines (not same line as code)

### Copyright Headers
- **New Files**: Use CARLOS project header (see `docs/copyright-header-carlos.md`)
- **Modified Files**: Preserve all existing headers; optionally add modification notice
- **Never Remove**: Existing copyright notices (GPL violation)
- **Never Change GPL Version**: Preserve GPL2, GPL2+, GPL3 exactly as-is

### Commit Format
[Conventional Commits](https://www.conventionalcommits.org/): `feat:`, `fix:`, `chore:`, `update:`

---

## Code Maintenance Philosophy

- **Active cleanup**: Project aggressively removes unused code/dependencies
- **Recently removed**: MyDrugRef, BORN integration, HealthSafety, legacy email notifications
- **Don't assume legacy features still exist** -- check current codebase
- **Reduce attack surface** by removing unused functionality

---

## Key Reference Files

```
# 2Action Examples
AddTickler2Action.java            -- Simple execute pattern
SystemMessage2Action.java         -- Method-based routing
EctDisplayMeasurements2Action.java -- Inheritance-based pattern
EctDisplayAction.java             -- Base class for encounter display

# Spring
SpringUtils.java                  -- Static bean access pattern
applicationContext.xml             -- Core Spring configuration

# Configuration
struts.xml + struts-*.xml          -- Modular Struts config (17 files)
pom.xml                            -- 200+ healthcare Maven dependencies
web.xml                            -- Security filter chain
```

---

## Boundaries

**Always do:**
- Include SecurityInfoManager.hasPrivilege() in every 2Action
- Use SpringUtils.getBean() for dependency injection
- Add new Struts actions to the appropriate domain-specific struts-*.xml file
- Follow the 2Action naming convention for new Struts2 actions
- Use `io.github.carlos_emr.carlos.*` package namespace

**Ask first:**
- Creating new Spring application context files
- Adding new Maven dependencies
- Modifying core Struts configuration (struts.xml parent)
- Changing the layered architecture patterns

**Never do:**
- Skip security privilege checks
- Use old package namespace `org.oscarehr.*` for new code
- Add actions directly to struts.xml (use domain-specific files)
- Use `com.opensymphony.xwork2.*` imports (use `org.apache.struts2.*`)
