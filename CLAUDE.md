# OpenO EMR - Healthcare Electronic Medical Records System

> **⚠️ DEVCONTAINER ENVIRONMENT NOTICE**
>
> The `.claude/settings.json` in this repository grants **extensive pre-approved permissions**
> optimized for **isolated devcontainer development only**. These settings assume:
> - Sandboxed Docker environment with no external network access to production systems
> - Development database with synthetic/test data (no real PHI)
> - Disposable environment that can be safely reset
>
> **DO NOT** use these defaults in shared servers, production environments, or any system
> with access to real patient data. Review and restrict permissions in `.claude/settings.json`
> if running outside an isolated devcontainer.

**PROJECT IDENTITY**: Always refer to this system as "OpenO EMR" or "OpenO" - NOT "OSCAR EMR" or "OSCAR McMaster"

## Core Context

**Domain**: Canadian healthcare EMR system with multi-jurisdictional compliance (BC, ON, generic)
**Stack**: Java 21, Spring 5.3.39, Hibernate 5.x, Maven 3, Tomcat 9.0.97, MariaDB/MySQL
**Regulatory**: HIPAA/PIPEDA compliance REQUIRED - PHI protection is CRITICAL

## Essential Commands

```bash
# Development Workflow
make clean                    # Clean project and remove deployed app
make install                  # Build and deploy without tests
make install --run-tests      # Build, test, and deploy (all tests)
make install --run-modern-tests     # Build and run only modern tests (JUnit 5)
make install --run-legacy-tests     # Build and run only legacy tests (JUnit 4)
make install --run-unit-tests       # Build and run only modern unit tests
make install --run-integration-tests # Build and run only modern integration tests
server start/stop/restart     # Tomcat management
server log                    # Tail application logs

# Database & Environment
db-connect                   # Connect to MariaDB as root
debug-on / debug-off         # Toggle DEBUG/INFO logging levels

gh pr create                 # GitHub pull request creation
```

## Critical Security Requirements

**MANDATORY for all code changes:**
- Use `Encode.forHtml()`, `Encode.forJavaScript()` for ALL user inputs
- Parameterized queries ONLY - never string concatenation
- ALL actions MUST include `SecurityInfoManager.hasPrivilege()` checks
- PHI (Patient Health Information) must NEVER be logged or exposed
- **Use `PathValidationUtils` for ALL file path operations** (see below)

### PathValidationUtils - File Path Security

**ALWAYS use PathValidationUtils** (`ca.openosp.openo.utility.PathValidationUtils`) for file operations involving user input. It prevents path traversal attacks consistently across the codebase.

**Key Methods:**
```java
// For user-provided filenames (sanitizes and validates)
File safeFile = PathValidationUtils.validatePath(userFilename, allowedDir);

// For validating existing file paths
PathValidationUtils.validateExistingPath(file, allowedDir);

// For validating uploaded files from Struts2/Tomcat
PathValidationUtils.validateUpload(uploadedFile);

// For complete upload validation (source + destination)
File dest = PathValidationUtils.validateUpload(sourceFile, filename, destDir);

// For checking if file is in allowed temp directory
if (PathValidationUtils.isInAllowedTempDirectory(file)) { ... }
```

**Migration from old patterns:**
```java
// OLD (inconsistent, error-prone)
if (!file.getCanonicalPath().startsWith(baseDir.getCanonicalPath() + File.separator)) {
    throw new SecurityException("Invalid path");
}

// NEW (consistent, robust)
PathValidationUtils.validateExistingPath(file, baseDir);
```

**Full documentation**: `docs/path-validation-utils.md`

## Package Structure (2025 Migration)

**CRITICAL**: Use NEW namespace `ca.openosp.openo.*` for ALL code
- **Old**: `org.oscarehr.*`, `oscar.*` → **New**: `ca.openosp.openo.*`
- **Note**: May encounter old names in comments/documentation; git history shows "renamed" files
- **DAO Classes**: `ca.openosp.openo.commn.dao.*` (note: "commn" not "common")
- **Forms DAOs**: `ca.openosp.openo.commn.dao.forms.*`
- **Models**: `ca.openosp.openo.commn.model.*`
- **Exception**: `ProviderDao` at `ca.openosp.openo.dao.ProviderDao`
- **Test Utilities**: Remain at `org.oscarehr.common.dao.*` for backward compatibility

## Struts2 Migration Pattern ("2Action")

**CRITICAL PATTERN**: All new Struts2 actions use `*2Action.java` naming convention

### 2Action Structure Template:
```java
public class Example2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() {
        // MANDATORY security check
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_object", "r", null)) {
            throw new SecurityException("missing required sec object");
        }
        // Business logic
        return "success";
    }
}
```

### 2Action Categories:
1. **Simple Execute**: Single `execute()` method (e.g., `AddTickler2Action`)
2. **Method-Based**: Route via `method` parameter (e.g., `CaseloadContent2Action`)
3. **Inheritance-Based**: Extend `EctDisplayAction` for encounter components

## Healthcare Domain Context

**Core Medical Modules**:
- **PMmodule/**: Program management and case management
- **billing/**: Province-specific billing (BC, ON) with diagnostic codes
- **prescription/**: Drug management with ATC codes, interaction checking
- **lab/**: HL7 lab results, OLIS integration (Ontario)
- **prevention/**: Immunization tracking with provincial schedules
- **demographic/**: Patient data with HIN (Health Insurance Number) management

## Development Workflow

**DevContainer Environment**:
- Docker-based development with debugging on port 8000
- Custom terminal with tool reminders on bash startup

**Build & Deploy Cycle**:
1. `make clean` → `make install --run-tests` → `server log`
2. For quick iterations: `make install` (skips tests)
3. Debug logging: `debug-on` → `server restart` → `debug-off`

## Modern Test Framework (JUnit 5)
**Key Features**:
- **Parallel Structure**: `src/test-modern/` separate from legacy `src/test/`
- **Zero Impact**: Legacy tests unchanged, both suites run automatically
- **Modern Stack**: JUnit 5, AssertJ, H2 in-memory database, BDD naming
- **Spring Integration**: Full Spring context with transaction support
- **Multi-File Architecture**: Component-first naming (`TicklerDao*Test`) for scalability
- **Documentation**: Complete guide at `docs/test/modern-test-framework-complete.md`

### Test Organization Standards

Tests use hierarchical tagging for filtering:
- **Required Tags**: `@Tag("integration")`, `@Tag("dao")` (test type & layer)
- **CRUD Tags**: `@Tag("create")`, `@Tag("read")`, `@Tag("update")`, `@Tag("delete")`
- **Extended Tags**: `@Tag("query")`, `@Tag("search")`, `@Tag("filter")`, `@Tag("aggregate")`

**Running Tagged Tests:**
```bash
mvn test -Dgroups="unit"           # Unit tests only
mvn test -Dgroups="integration"    # Integration tests only
mvn test -Dgroups="create,update"  # Specific operations
```

### BDD Test Naming Convention

Modern tests use BDD (Behavior-Driven Development) naming for clarity:

**Patterns**:
1. `should<Action>_when<Condition>` - Testing behavior/requirements (camelCase, ONE underscore)
2. `<methodName>_<scenario>_<expectedOutcome>` - Testing specific methods
3. `should<ExpectedBehavior>` - Simple assertions

**Examples**:
```java
void shouldReturnTickler_whenValidIdProvided()
void findActiveByDemographicNo_multipleStatuses_returnsOnlyActive()
void shouldLoadSpringContext()
```

**Benefits**: Self-documenting, clear failure messages, searchable

### Test Context Configuration

The codebase has legacy patterns (SpringUtils static access, mixed Hibernate/JPA, circular dependencies) that require specific test setup. See **[Test Writing Guide](docs/test/test-writing-guide.md)** for detailed configuration patterns.

**Key points**: Extend `OpenOTestBase` (handles SpringUtils), define beans manually in test context, explicitly list entities in persistence.xml.

**Writing Tests - CRITICAL**:
When asked to write tests, you MUST:
1. **First examine the actual interface/class** being tested
2. **Only test methods that actually exist** in the codebase
3. **Never invent or assume method names** - verify they exist
4. **Extend OpenOTestBase** for Spring context handling
5. **Use @PersistenceContext(unitName = "testPersistenceUnit")** for EntityManager

## Code Quality Standards

**Security (CodeQL Integration)**:
- OWASP Encoder for all JSP outputs
- Parameterized SQL queries (never concatenation)
- File upload filename validation
- CodeQL security scanning must pass

**Spring Integration Pattern**:
```java
private SomeManager someManager = SpringUtils.getBean(SomeManager.class);
```

**Documentation Standards**:
- **JavaDoc Required**: All public classes and methods MUST have comprehensive JavaDoc
- **No @author Tags**: Do NOT add @author annotations (misleading after Bitbucket→GitHub migration)
- **@since Tags**: Use git history to determine accurate dates: `git log --follow --format="%ai" <file> | tail -1`
- **Parameter Documentation**: Include specific data types in @param tags (e.g., `@param id String the unique identifier`)
- **Return Documentation**: Specify exact return types in @return tags (e.g., `@return List<Provider> list of healthcare providers`)
- **Exception Documentation**: Document all thrown exceptions with @throws tags
- **Deprecation**: Use @deprecated with migration guidance to newer APIs
- **JSP Documentation**: Add comprehensive JSP comment blocks after copyright headers with purpose, features, parameters, and @since
- **Inline Comments**: Add comments for complex logic on separate lines (not same line as code)

## Healthcare Integration Standards

**Standards & Protocols**:
- **HL7 v2/v3**: Full message processing with MSH, PID, OBX, ORC, OBR segment handlers
- **FHIR R4**: HAPI FHIR 5.4.0 with resource filters and healthcare provider context
- **SNOMED CT**: Clinical terminology with core dataset loading
- **ICD-9/ICD-10**: Diagnosis coding systems fully integrated
- **ATC Codes**: Anatomical Therapeutic Chemical classification for medications
- **DICOM**: Medical imaging support for diagnostic images

**Provincial Healthcare Systems**:
- **OLIS**: Ontario Labs Information System integration
- **Teleplan**: BC MSP billing system with specialized upload/download
- **MCEDT**: Medical Certificate Electronic Data Transfer
- **DrugRef**: Drug reference database integration

**Medical Forms Integration**:
- **Rourke Growth Charts**: Multiple versions (2006, 2009, 2017, 2020) for pediatric care
- **BCAR Forms**: British Columbia Antenatal Record for pregnancy care
- **Mental Health Assessments**: Standardized clinical assessment forms
- **Laboratory Requisitions**: Province-specific lab ordering forms

## Technology Stack Details

### Core Technologies
- **Java 21** with modern language features and JAXB compatibility
- **Spring Framework 5.3.39**: IoC container, MVC, AOP, Security, transaction management
- **Hibernate 5.6.15**: ORM framework with custom MySQL dialect (`OscarMySQL5Dialect`)
- **Maven 3**: Build management with 200+ healthcare-specific dependencies
- **Apache Tomcat 9.0.97**: Web application server with debugging enabled
- **MariaDB/MySQL**: Database with custom connection tracking (`OscarTrackingBasicDataSource`)

### Web Technologies
- **Struts 2.5.33**: Modern actions (2Action pattern) coexisting with legacy Struts 1.x
- **Apache CXF 3.5.10**: Web services framework for healthcare integrations
- **JSP/JSTL**: View layer with extensive medical form templates
- **Bootstrap 5.3.0**: Modern UI framework loaded from CDN for responsive design
- **JavaScript/CSS/jQuery**: Frontend with healthcare-specific UI components
- **Vanilla JavaScript**: Progressively replacing jQuery dependencies where possible

### Security Libraries
- **OWASP CSRF Guard**: CSRF protection with healthcare exclusions
- **OWASP Encoder**: Output encoding for XSS prevention
- **BCrypt**: Password hashing for provider authentication
- **Bouncy Castle**: Cryptographic functions for PHI protection

### Spring Configuration Architecture
Multiple modular application contexts:
- `applicationContext.xml` - Core Spring configuration
- `applicationContextREST.xml` - REST APIs with OAuth 1.0a
- `applicationContextOLIS.xml` - Ontario Labs Information System
- `applicationContextHRM.xml` - Hospital Report Manager
- `applicationContextCaisi.xml` - CAISI community integration
- `applicationContextFax.xml`, `applicationContextJobs.xml` - Specialized modules

## REST API & Web Services

### OAuth 1.0a Authentication (Migration in Progress)
- **Current Migration**: CXF OAuth2 → ScribeJava OAuth1.0a
- **New Classes**: `OscarOAuthDataProvider`, `OAuth1Executor`, `OAuth1Utils`
- **Healthcare Context**: Provider-specific credentials with facility integration
- **Services Migrated**: ProviderService, ConsentService with enhanced error handling

### Core API Services (25+ endpoints)
- **DemographicService**: Patient demographics with HIN management
- **ScheduleService**: Appointment scheduling with reason codes and billing types
- **PrescriptionService**: Medication management with ATC codes and interaction checking
- **LabService**: Laboratory results with HL7 integration and OLIS connectivity
- **PreventionService**: Immunization tracking with provincial schedules
- **ConsultationWebService**: Referral management and specialist communication
- **DocumentService**: Medical document management with privacy statement injection

### SOAP Web Services
- **CXF-based**: Healthcare system integration with WS-Security
- **Inter-EMR**: Data sharing via Integrator system across multiple OSCAR installations
- **Provincial Billing**: Direct integration with Teleplan (BC MSP) and other systems

### Active Code Cleanup (2025)
- **Modules Removed**: MyDrugRef, BORN integration, HealthSafety, legacy email notifications

### Code Maintenance Approach
- **Active cleanup**: Project aggressively removes unused code and dependencies
- **Recently removed**: MyDrugRef, BORN integration, HealthSafety, legacy email notifications
- **Assumption**: Don't assume legacy features still exist - check current codebase
- **Philosophy**: Reduce attack surface by removing unused functionality

### Development Environment Context
- **DevContainer primary**: Development done in Docker containers with debugging enabled
- **Debug port**: 8000 for remote debugging
- **Database**: Hibernate schema validation disabled - manual migration control
- **Logging**: Enhanced logging in development environment with `debug-on`/`debug-off` aliases
- **Custom Terminal**: Welcome message displays all available tools and shortcuts on bash startup

### DevContainer Custom Scripts
Located in `/scripts` directory within the container (copied from `.devcontainer/development/scripts/`):
- `make lock` - Update Maven dependency lock file
- **Build Process**: Stops Tomcat → Builds WAR → Creates symlink → Starts Tomcat
- **Configuration**: Auto-creates `over_ride_config.properties` from template
- **Parallel builds**: Uses `-T 1C` for faster Maven builds
- **Deployment**: Handles versioned WAR directories with symlinks to `/usr/local/tomcat/webapps/oscar`

## Architecture Patterns

### Layered Architecture
- **Web Layer**: Controllers (Actions) handle HTTP requests
- **Service Layer**: Business logic and workflow orchestration
- **DAO Layer**: Data access objects for database operations
- **Model Layer**: Domain entities and value objects

### Spring Configuration
- Extensive use of Spring IoC container
- Transaction management with Spring AOP
- Security configuration with Spring Security
- Multiple application contexts for different modules

### Legacy Integration & Struts2 Migration Pattern ("2Action") - CRITICAL PATTERN

#### **Migration Strategy Overview**
OpenO EMR uses a unique incremental migration approach from Struts 1.x to Struts 2.x using a "2Action" naming convention that allows both frameworks to coexist during the transition period.

#### **2Action Naming Convention & Structure**
- **Naming Pattern**: All migrated Struts2 actions follow `*2Action.java` naming (e.g., `AddTickler2Action`, `DisplayDashboard2Action`, `Login2Action`)
- **Class Structure**:
  ```java
  public class Example2Action extends ActionSupport {
      HttpServletRequest request = ServletActionContext.getRequest();
      HttpServletResponse response = ServletActionContext.getResponse();

      private SomeManager someManager = SpringUtils.getBean(SomeManager.class);

      public String execute() {
          // Security check pattern
          if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_object", "r", null)) {
              throw new SecurityException("missing required sec object");
          }
          // Business logic
          return "success";
      }
  }
  ```

#### **Struts2 Action Categories**

**1. Simple Execute Actions**
- Single `execute()` method handling all logic
- Examples: `AddTickler2Action`, `EditTickler2Action`
- Return simple result strings like "success", "close", "error"

**2. Method-Based Actions**
- Use `method` parameter to route to different methods within the action
- Pattern: `String mtd = request.getParameter("method");`
- Examples: `CaseloadContent2Action` (noteSearch/search methods), `SystemMessage2Action`
- Allows multiple related operations in one action class

**3. Inheritance-Based Actions**
- Extend specialized base classes like `EctDisplayAction`
- Examples: `EctDisplayMeasurements2Action`, `EctDisplayRx2Action`
- Inherit common functionality while implementing specific `getInfo()` methods
- Used for encounter display components in left navbar

#### **Integration Patterns**

**Request/Response Access**
```java
HttpServletRequest request = ServletActionContext.getRequest();
HttpServletResponse response = ServletActionContext.getResponse();
```
- Direct servlet API access maintained for compatibility
- No dependency on Struts2 action properties

**Spring Integration**
```java
private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
private TicklerManager ticklerManager = SpringUtils.getBean(TicklerManager.class);
```
- Spring dependency injection via `SpringUtils.getBean()`
- Maintains loose coupling with Spring container
- No need for Struts2-Spring plugin complexity

**Security Pattern (Required)**
```java
if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_objectname", "r", null)) {
    throw new SecurityException("missing required sec object");
}
```
- Every 2Action MUST include security validation
- Uses healthcare-specific role-based access control
- Throws SecurityException for unauthorized access

#### **Configuration Approach**

**Struts.xml Mapping**
```xml
<action name="login" class="ca.openosp.openo.login.Login2Action">
    <result name="provider" type="redirect">/provider/providercontrol.jsp</result>
    <result name="failure">/logout.jsp</result>
</action>
```
- Maintains `.do` extension for backward compatibility
- Spring object factory integration: `<constant name="struts.objectFactory" value="spring"/>`
- Mixed namespace support for gradual migration

**URL Compatibility**
- Legacy URLs ending in `.do` continue to work
- No changes required to existing JSP forms and links
- Seamless user experience during migration

#### **Best Practices for 2Action Development**
**1. Security First**
- Always include security privilege checks
- Use appropriate security objects for healthcare data
- Log security violations appropriately

**2. Error Handling**
- Use context-appropriate OWASP encoding when outputting user data:
  - `Encode.forHtml()` - HTML body content
  - `Encode.forHtmlAttribute()` - HTML attribute values
  - `Encode.forJavaScript()` - JavaScript string contexts
  - `Encode.forJavaScriptAttribute()` - JS in HTML attributes
  - `Encode.forCssString()` - CSS string values
  - `Encode.forUri()` / `Encode.forUriComponent()` - URL paths/parameters
- Implement proper exception handling
- Return appropriate result strings

**3. Spring Integration**
- Use `SpringUtils.getBean()` for dependency injection
- Leverage existing Spring-managed services
- Maintain transactional boundaries

**4. Healthcare Context**
- Include audit logging for patient data access
- Follow PHI protection patterns
- Use healthcare-specific validation

This migration pattern allows OpenO EMR to modernize incrementally while maintaining system stability and regulatory compliance throughout the transition process.

## File Patterns

### Key File Types
- `**/*DAO.java` - Database access objects
- `**/*Action.java` - Struts/Spring MVC controllers
- `**/web/**` - Web layer components
- `**/model/**` - Entity/domain models
- `**/service/**` - Business logic services
- `database/mysql/**` - Schema migrations and SQL scripts
- `src/main/webapp/**` - Web resources (JSP, CSS, JS)

### Configuration Files
- **Struts Configuration**:
  - `struts.xml` - Struts2 configuration with `.do` extension and Spring integration
  - Mixed Struts 1.x and 2.x action mappings
- **Database Configuration**:
  - Custom MySQL dialect: `OscarMySQL5Dialect`
  - Connection tracking: `OscarTrackingBasicDataSource`
  - Legacy MySQL compatibility settings
- **Security Configuration**:
  - `web.xml` - Complex filter chain with OWASP CSRF protection
  - Privacy statement filters and audit logging
  - Multi-factor authentication and SAML 2.0 support
- `pom.xml` - Maven with 200+ healthcare-specific dependencies

## Development Environment

### Docker Setup
- Development environment runs in Docker containers
- Tomcat container with Java 21 and debugging enabled
- MariaDB database container
- Maven repository caching for faster builds
- Port 8080 for web application, 3306 for database

### IDE Configuration
- VS Code with Java extension pack
- Remote development in Docker container
- Debugging support on port 8000

## Database Schema Patterns

### Core Healthcare Tables
- **demographic**: 50+ fields including HIN, rostering status, multiple addresses
- **allergies**: Drug/non-drug allergies with severity, reaction tracking, regional identifiers
- **appointment**: Scheduling with reason codes, billing types, status tracking
- **casemgmt_note**: Clinical notes with encryption support and issue-based organization
- **prevention**: Immunization/prevention tracking with configurable schedules
- **drugs**: Prescription management with ATC codes, interaction checking, renal dosing
- **measurementType**: Vital signs and clinical measurements with flowsheet integration
- **billing**: Province-specific billing with diagnostic codes and claims processing

### Audit and Compliance Patterns
- Every table includes `lastUpdateUser`, `lastUpdateDate` for audit trails
- Complex healthcare schema with 50+ fields in `demographic` table
- Comprehensive logging of all patient data access via `UserActivityFilter`
- Privacy-compliant data handling with PHI filtering throughout application
- Multi-jurisdictional support with province-specific configurations

## Database Schema & Migration System

**Database**: MariaDB/MySQL with comprehensive healthcare schema dating back to 2006
**Migration Pattern**: Date-based SQL scripts (`update-YYYY-MM-DD-description.sql`)

### Core Database Files (`database/mysql/`)
```bash
# Initial Schema Setup
oscarinit.sql          # Core database schema
oscarinit_2025.sql     # Current 2025 schema version
oscardata.sql          # Initial reference data
oscarinit_bc.sql       # British Columbia specific
oscarinit_on.sql       # Ontario specific

# Medical Coding Systems
icd9.sql / icd10.sql   # Diagnosis codes (ICD-9/ICD-10)
measurementMapData.sql # Clinical measurements mapping
SnomedCore/           # SNOMED CT clinical terminology
olis/                 # Ontario Labs Information System

# Provincial Healthcare Data
bc_billingServiceCodes.sql     # BC medical service codes
bc_pharmacies.sql              # BC pharmacy directory
firstNationCommunities_lu_list.sql # First Nations communities
```

**Development Database**:
- Container: `db-connect` alias → MariaDB as root user
- Port 3306 with health checks, 2G memory limit
- Seeded with medical forms (Rourke charts, BCAR) and reference data

---

## Issue Management & Automation

### Automated Issue Lifecycle
When a PR is merged that references an issue (using keywords like `fixes #123`, `closes #456`, `resolves #789`):
1. **Automatic notification** - Issue reporter is @mentioned with request to verify the fix
2. **Status tracking** - Issue gets `status: pending-verification` label
3. **Verification response** - If reporter comments "verified" or "fixed", issue auto-closes with `status: verified-fixed`
4. **Failed fix detection** - If reporter says "not fixed" or "still broken", adds `status: fix-failed` label

**Automated Status Labels:**
- `status: pending-verification` - Fix has been merged, awaiting reporter confirmation
- `status: verified-fixed` - Reporter confirmed the fix works
- `status: fix-failed` - Reporter confirmed the fix doesn't work

### Issue Labeling Standards

**Every issue should have at minimum:**
1. **Type label** (required) - What kind of issue is it?
   - `type: bug` - Something isn't working as expected
   - `type: feature` - New feature or request
   - `type: documentation` - Documentation improvements
   - `type: maintenance` - Code refactoring, dependency updates
   - `type: regression` - Something that used to work but is broken
   - `type: security` - Security related issue
   - `type: test` - Test improvements or additions

2. **Priority label** (required) - How urgent is it?
   - `priority: critical` - Must be fixed ASAP (production broken)
   - `priority: high` - Stalls work on the project or its dependents
   - `priority: medium` - Not blocking but should be addressed soon
   - `priority: low` - Nice to have but not urgent

3. **Status label** (as needed) - Current state
   - `status: needs-triage` - Needs maintainer review
   - `status: confirmed` - Issue has been reproduced

4. **Additional labels** (optional):
   - **Special labels**: `blocker`, `good first issue`, `help wanted`, `dependencies`

## Commit Standards & Quick Reference

**Commit Format**: [Conventional Commits](https://www.conventionalcommits.org/) - `feat:`, `fix:`, `chore:`, `update:`

**Key Files**:
- `CLAUDE.md` - AI context (this file)
- `pom.xml` - 200+ healthcare Maven dependencies
- `database/mysql/` - 19+ years of healthcare schema evolution (2006-2025)
- `.devcontainer/` - Docker development with AI tools

**Critical Patterns**:
- **Project Name**: "OpenO EMR" (NOT "OSCAR EMR")
- **Security**: `SecurityInfoManager.hasPrivilege()` + OWASP encoding required
- **Actions**: `*2Action.java` pattern for Struts2 migration
- **Packages**: `ca.openosp.openo.*` (new) vs `org.oscarehr.*` (legacy)
- **Database**: Date-based migrations, audit trails (`lastUpdateUser`, `lastUpdateDate`)

---

## Claude Workflow Guidelines

**Context**: Claude operates both as a GitHub Actions workflow (triggered by @claude mentions) and directly via Claude Code CLI. These guidelines apply to both contexts.

### Task Handling
1. **Simple Questions/Reviews**: Answer directly in comments, reference specific files and line numbers
2. **Straightforward Changes** (1-3 files): Create feature branch, implement, create PR
3. **Complex Changes**: Ask clarifying questions first, create implementation plan, proceed after approval

### Branch Protection
- **Protected Branches**: `develop`, `main`, `experimental` - direct commits prohibited
- **All changes** must go through pull requests with review
- Claude creates feature branches: `claude/issue-<number>-<timestamp>`

### Security Checklist (Every Code Change)
- [ ] Context-appropriate OWASP encoding for user inputs (see Error Handling section)
- [ ] Parameterized SQL queries (never concatenation)
- [ ] `SecurityInfoManager.hasPrivilege()` checks in all actions
- [ ] `PathValidationUtils` for file operations
- [ ] No PHI in logs or error messages

### PR Requirements
- ✅ Target `develop` branch (not `main`)
- ✅ Include tests for new functionality
- ✅ Reference related issues (`fixes #123`)
- ✅ Add "Generated with Claude Code" signature
- ✅ Ensure CI checks pass before requesting review

### When Blocked
If Claude encounters issues it cannot resolve:
- Document the problem clearly in comment
- Explain what was attempted and why it failed
- Provide specific error messages
- Ask for guidance on preferred resolution

---

## AI-Assisted Development

### Claude Code Capabilities

Claude Code is integrated into this repository with the following capabilities:

**Automated Actions (on @claude mention or PR events):**
- Post PR review comments with inline code annotations
- Create and update issues with proper labels
- Create feature branches and push code changes
- Create pull requests automatically (via `gh pr create`)
- Access CI/CD status and logs for debugging
- **Note**: @claude triggers are restricted to repository OWNER, MEMBER, or COLLABORATOR only. CONTRIBUTOR, FIRST_TIME_CONTRIBUTOR, and FIRST_TIMER are excluded for security.

**Tool Permissions:**
- GitHub CLI access with tiered permissions:
  - **Allowed**: `gh pr create/view/list/diff/checks`, `gh issue view/list/comment`, `gh run view/list/watch`, `gh repo view`
  - **Requires confirmation**: `gh pr close`, `gh issue create/edit/close`, `gh label`, `gh run rerun`
  - **Blocked**: `gh pr merge`, `gh repo create/delete/fork`, `gh secret`, `gh api` write methods
- Git operations (status, branch, checkout, add, commit, push, pull, fetch, log, diff)
- File read/write within the repository, subject to the following boundaries:
  - Scope: Only files inside the checked-out OpenO EMR repository workspace; no access to paths outside the repo.
  - Protected directories: Claude must not modify Git metadata or CI/CD definitions (e.g., `.git/`, `.github/`, `.github/workflows/`), database seeds/migrations (e.g., `database/`), secrets or credential stores, or other sensitive directories. These protections **must be enforced via explicit write-deny rules** in `.claude/settings.json` (for example: `Write(path:.git/**)`, `Write(path:.github/workflows/**)`, `Write(path:database/**)`).
  - File size: Intended for source files, configuration, and documentation. Very large files (such as database dumps, large binaries, or media assets) may be rejected by the tools and should not be created or edited by Claude.
  - File types: Read/write is primarily for text-based project assets (Java, XML, YAML, JSON, JSP, Markdown, shell scripts, etc.). Claude should not generate or alter compiled artifacts, installers, or opaque binary formats.
  - Deny rules: All file write operations remain subject to (a) the destructive-operation deny list and (b) explicit path-based write restrictions configured in `.claude/settings.json`. At minimum, `.claude/settings.json` **must** include write-deny entries for:
    - `Write(path:.git/**)`
    - `Write(path:.github/**)`
    - `Write(path:.github/workflows/**)`
    - `Write(path:database/**)`
    - and any additional secrets/credential directories defined by the deployment environment. If there is any conflict, the deny rules take precedence and the operation must not be performed.
- Web search and documentation lookup
- Playwright MCP tools for UI testing
- See `.claude/settings.json` for complete permission configuration

**Three-Tier Permission Model:**
The `.claude/settings.json` file defines three permission categories:
- **ALLOW**: Commands execute immediately without user intervention (core workflow operations)
- **ASK**: Commands require explicit user confirmation before execution (reversible but potentially disruptive operations)
- **DENY**: Commands are blocked entirely and cannot be executed (destructive or dangerous operations)

Commands in the ASK tier include:
- `gh pr close`, `gh issue create/edit/close`, `gh label` - visible repository actions
- `gh run rerun` - CI resource usage
- `git reset --soft/--mixed` - recoverable history changes
- `git stash drop` - potential data loss (single stash entry)

**Safety Guardrails:**
- **Repository scoped** - Operations run within the checked-out `openo-beta/Open-O` repository context
- Branch protection rules prevent direct pushes to `develop`, `main`, `experimental`
- All PRs require human review before merge
- Destructive operations are blocked:
  - File deletion: `rm -rf`, `rm -fr`, `rm -r`, `rm --recursive`
  - Force push: `git push --force/-f`, `git push origin --force/-f`, `git push * --force/-f`, `git push --force-with-lease`, `git push origin --force-with-lease`, `git push origin * --force-with-lease`
  - History rewriting: `git commit --amend`, `git filter-branch`, `git filter-repo`, `git reflog expire`, `git gc --prune`
  - Hook bypass: `git commit --no-verify`, `git push --no-verify`
  - Destructive git: `git rebase`, `git reset --hard`, `git clean` (note: `git reset --soft/--mixed` require confirmation, see ASK tier above)
  - System: `sudo`
- GitHub API write methods blocked (`-X DELETE/POST/PUT/PATCH`, `--method DELETE/POST/PUT/PATCH`)
- Repository management operations (`gh repo create/delete/fork`) are blocked
- Sensitive repository APIs blocked: `settings`, `collaborators`, `hooks`, `keys`, `invitations`, `branches/*/protection`
- Remote branch deletion (`git push origin --delete`) is blocked
- Remote manipulation (`git remote add/set-url`) is blocked
- Workflow modification (`gh workflow enable/disable`) is blocked
- Credential manipulation (`gh auth`) is blocked
- PHI protection enforced via OWASP encoding, parameterized queries, and `SecurityInfoManager` (see Critical Security Requirements)

**Enforcement Mechanism:**
The safety guardrails above are enforced through Claude Code's permission system configured in `.claude/settings.json`:
- **Deny rules take precedence** - Commands matching deny patterns are blocked before execution, regardless of allow rules
- **Pattern matching** - Uses glob-style wildcards (`*`) to match command variations (e.g., `git push --force *` blocks `git push --force origin main`)
- **Layered defense** - Multiple patterns cover flag ordering variations (e.g., `--force` before or after remote/branch)
- **Case sensitivity** - Separate patterns for case variants (e.g., `rm -rf` and `rm -Rf` both blocked)
- **No bypass via equals syntax** - Patterns like `--force-with-lease=*` block the `=refname` variant

Note: These are client-side controls. Repository-level branch protection rules provide server-side enforcement for protected branches.

### Interacting with Claude

**On Pull Requests:**
- `@claude review` - Comprehensive code review with security focus
- `@claude fix the lint errors` - Apply automated fixes
- `@claude explain this change` - Get explanation of PR changes

**On Issues:**
- `@claude investigate this bug` - Research and provide analysis
- `@claude implement this feature` - Create implementation PR
- `@claude add labels` - Categorize with appropriate labels

**Automated Triggers:**
- New PRs automatically receive code review
- Issues trigger Claude response when opened or assigned by authorized users (OWNER/MEMBER/COLLABORATOR), if they contain `@claude` in title or body

---

## Key Code References & Further Information

### Essential Configuration Files
```bash
# Spring Configuration Examples
src/main/resources/applicationContext.xml           # Core Spring setup patterns
src/main/resources/applicationContextREST.xml      # OAuth 1.0a implementation examples
src/main/webapp/WEB-INF/web.xml                   # Security filter chain configuration

# Struts Configuration
src/main/webapp/WEB-INF/classes/struts.xml        # 2Action mapping examples
src/main/java/ca/openosp/openo/*/web/*2Action.java # 2Action implementation patterns

# Database Configuration
src/main/resources/OscarDatabaseBase.xml           # Hibernate configuration
database/mysql/oscarinit_2025.sql                 # Current database schema
database/mysql/updates/update-2025-*.sql          # Recent migration patterns
```

### Security Implementation Examples
```bash
# Security Patterns (READ THESE FIRST)
src/main/java/ca/openosp/openo/managers/SecurityInfoManager.java    # Authorization patterns
src/main/java/ca/openosp/openo/utility/LoggedInInfo.java           # Session management
src/main/webapp/WEB-INF/classes/oscar/oscarSecurity/                # Security filter examples

# OWASP Integration Examples
src/main/webapp/*/*.jsp                            # Look for Encode.forHtml() usage patterns
src/main/java/ca/openosp/openo/*/web/*2Action.java # Security check implementations

# CSRF Protection Implementation
src/main/webapp/WEB-INF/Owasp.CsrfGuard.properties # CSRF Guard configuration
src/main/webapp/WEB-INF/csrfguard.js               # Client-side token injection
src/main/java/ca/openosp/openo/app/CSRFPreservingFilter.java    # Custom CSRF filter
src/main/java/ca/openosp/openo/app/CsrfJavaScriptInjectionFilter.java # JS injection

```

### Healthcare Domain Examples
```bash
# Medical Data Patterns
src/main/java/ca/openosp/openo/commn/model/Demographic.java        # Patient data model
src/main/java/ca/openosp/openo/commn/model/Allergies.java          # Medical allergy model
src/main/java/ca/openosp/openo/commn/dao/DemographicDao.java       # Healthcare DAO patterns

# Provincial Healthcare Integration
src/main/java/ca/openosp/openo/billing/CA/BC/                      # BC-specific billing
src/main/java/ca/openosp/openo/billing/CA/ON/                      # Ontario-specific billing
src/main/java/ca/openosp/openo/olis/                               # Ontario Labs integration

# HL7 & FHIR Examples
src/main/java/ca/openosp/openo/hl7/                                # HL7 message handling
src/main/java/ca/openosp/openo/fhir/                               # FHIR implementation patterns
```

### 2Action Migration Examples
```bash
# Study These 2Action Implementations
src/main/java/ca/openosp/openo/tickler/pageUtil/AddTickler2Action.java      # Simple execute pattern
src/main/java/ca/openosp/openo/caseload/CaseloadContent2Action.java         # Method-based routing
src/main/java/ca/openosp/openo/encounter/pageUtil/EctDisplay*2Action.java
# Base Classes for 2Actions
src/main/java/ca/openosp/openo/encounter/pageUtil/EctDisplayAction.java
```

### Spring Integration Patterns
```bash
# Spring Utility Usage Examples
src/main/java/ca/openosp/openo/utility/SpringUtils.java           # Spring bean access patterns
src/main/java/ca/openosp/openo/managers/*Manager.java             # Service layer examples
src/main/java/ca/openosp/openo/commn/dao/*Dao.java               # DAO injection patterns
```

### Database Schema References
```bash
# Database Structure Examples
database/mysql/oscardata.sql                      # Reference data examples
database/mysql/caisi/initcaisi.sql               # Community integration schema
database/mysql/olis/olisinit.sql                 # Provincial lab integration schema
database/mysql/SnomedCore/snomedinit.sql         # Medical terminology integration
```

### Testing Patterns
```bash
# Modern Test Framework (JUnit 5) - ACTIVE AND RECOMMENDED
src/test-modern/java/ca/openosp/openo/            # Modern JUnit 5 tests
src/test-modern/resources/                        # Modern test configurations
docs/test/modern-test-framework-complete.md            # Complete test framework documentation

# Legacy Test Examples (JUnit 4) - for reference only
src/test/java/ca/openosp/openo/                   # Legacy test structure
src/test/resources/over_ride_config.properties    # Test configuration template
```

#### Modern Test Framework - Critical Guidelines
**IMPORTANT**: When writing tests, ALWAYS:
1. **Examine the actual code first** - Read the DAO/Manager interfaces to see what methods actually exist
2. **Test real methods only** - Never make up methods that don't exist in the codebase
3. **Use actual method signatures** - Match the exact parameters and return types
4. **Extend OpenOTestBase** - Handles SpringUtils anti-pattern and Spring context
5. **Follow BDD naming strictly**: `should<Action>_when<Condition>` (camelCase, ONE underscore)
6. **Check DAO interfaces** - Look at `*Dao.java` files to see available methods before writing tests

For detailed examples and test development workflow, see **[Test Writing Guide](docs/test/test-writing-guide.md)**.

**Test Execution Commands:**
```bash
# Run all modern tests
mvn test                          # Runs modern tests first, then legacy

# Run all integration tests for a DAO component
mvn test -Dtest=TicklerDao*IntegrationTest  # All TicklerDao integration tests

# Run specific operation tests
mvn test -Dtest=TicklerDaoFindIntegrationTest      # Just find operations
mvn test -Dtest=TicklerDaoWriteIntegrationTest     # Just write operations

# Run by test type (using tags)
mvn test -Dgroups="unit"                # Fast unit tests only
mvn test -Dgroups="integration"         # Integration tests only

# Run tests by tags
mvn test -Dgroups="tickler,read"        # All read operations for tickler
mvn test -Dgroups="create,update"       # All create and update operations
mvn test -Dgroups="aggregate"           # All aggregation operations

# Build with tests
make install --run-tests          # Includes modern tests automatically
```

**Parallel Execution for Multi-File Tests:**
```bash
mvn test -T 4C                    # 4 threads per CPU core for parallel execution
```

### Development Environment References
```bash
# DevContainer Configuration
.devcontainer/devcontainer.json                   # VS Code dev environment setup
.devcontainer/development/Dockerfile              # Container build configuration
.devcontainer/development/scripts/make            # Build and deploy automation
.devcontainer/development/scripts/server          # Tomcat management automation
.devcontainer/development/config/bashrc           # Terminal customization
```

### Documentation & Architecture
```bash
# Project Documentation
docs/Password_System.md                           # Security architecture details
docs/struts-actions-detailed.md                   # Action mapping documentation
pom.xml                                            # Complete dependency list with versions
README.md                                          # Project setup and overview
```

### When You Need Help Understanding:
- **Security Patterns**: Check `SecurityInfoManager.java` and existing 2Action implementations
- **Database Access**: Look at DAO implementations in `commn.dao` package
- **Healthcare Standards**: Examine `hl7/` and `fhir/` packages for integration patterns
- **Provincial Variations**: Study `billing/CA/BC/` vs `billing/CA/ON/` implementations
- **Spring Configuration**: Reference the multiple `applicationContext*.xml` files
- **2Action Migration**: Compare legacy Action classes with their 2Action equivalents