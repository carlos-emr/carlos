# Legacy Test Framework Reference

## Overview

This document describes the existing legacy test suite that continues to run alongside the modern JUnit 5 tests. These tests remain functional and provide critical coverage while new development uses the modern framework.

**Status**: Active and maintained
**Framework**: JUnit 4
**Test Count**: ~374 test files
**Location**: `/workspace/src/test/`

## Legacy Test Structure

```
src/test/
├── java/
│   ├── ca/openosp/openo/
│   │   ├── commn/
│   │   │   ├── dao/              # DAO tests (largest collection)
│   │   │   │   └── utils/        # Test utilities
│   │   │   │       ├── EntityDataGenerator.java
│   │   │   │       ├── SchemaUtils.java
│   │   │   │       ├── DataUtils.java
│   │   │   │       ├── AuthUtils.java
│   │   │   │       └── ConfigUtils.java
│   │   │   └── model/            # Model tests
│   │   ├── PMmodule/             # Program Management module tests
│   │   │   ├── dao/
│   │   │   └── web/
│   │   ├── billing/
│   │   │   └── CA/
│   │   │       ├── BC/           # British Columbia billing tests
│   │   │       └── ON/           # Ontario billing tests
│   │   ├── casemgmt/             # Case management tests
│   │   ├── dashboard/            # Dashboard tests
│   │   ├── decisionSupport/      # Decision support tests
│   │   ├── hl7/                  # HL7 integration tests
│   │   ├── measurement/          # Clinical measurements tests
│   │   ├── prevention/           # Prevention module tests
│   │   └── util/                 # Utility tests
│   └── tests/                    # Miscellaneous tests
└── resources/
    ├── applicationContextTest.xml      # Main Spring test context
    ├── spring_hibernate_test.xml       # Hibernate configuration
    ├── spring_jpa_test.xml             # JPA configuration
    ├── over_ride_config.properties     # Database and app configuration
    ├── log4j.xml                       # Logging configuration
    ├── demographicsAndProviders.xml    # Test data fixtures
    ├── labs/                           # Lab test data
    ├── e2e/                            # End-to-end test resources
    └── [various test data files]       # .zip, .sql, template files

```

## Test Framework Details

### JUnit 4 Configuration

The legacy tests use JUnit 4 with the following patterns:

```java
import org.junit.Test;
import org.junit.Before;
import static org.junit.Assert.*;

public class SomeDaoTest extends DaoTestFixtures {

    @Before
    public void setUp() throws Exception {
        // Test setup
    }

    @Test
    public void testFindMethod() {
        // Test implementation
    }
}
```

### Base Test Classes

#### DaoTestFixtures
Located at `ca.openosp.openo.commn.dao.DaoTestFixtures`, this base class provides:
- Database connection setup via `@BeforeClass` static initialization
- Spring context initialization from `applicationContextTest.xml`
- Database schema utilities through `SchemaUtils`
- LoggedInInfo for authentication context
- Note: Uses mix of JUnit 3 (junit.framework) and JUnit 4 (@BeforeClass) imports

#### Test Utilities

Located in `ca.openosp.openo.commn.dao.utils`:

- **EntityDataGenerator**: Creates test entities with valid data
- **SchemaUtils**: Database schema operations for tests (table creation, cleanup)
- **DataUtils**: Common data manipulation utilities
- **AuthUtils**: Authentication/authorization test helpers
- **ConfigUtils**: Test configuration management (loads over_ride_config.properties)

## Running Legacy Tests

### Run All Legacy Tests
```bash
# Default Maven test execution runs both modern and legacy
mvn test

# Note: Legacy tests run AFTER modern tests complete
# Expected time: 5-15 minutes depending on database and system performance
```

### Run Specific Legacy Tests
```bash
# Run a specific test class
mvn test -Dtest=AllergyDaoTest

# Run all tests in a package
mvn test -Dtest=ca.openosp.openo.commn.dao.*

# Run tests matching a pattern
mvn test -Dtest=*DaoTest
```

## Test Categories

### DAO Tests (~200 files)
The largest category, testing data access objects:
- Located in `*/dao/` directories
- Test database operations (CRUD)
- Use real database connections (not in-memory)
- Extend `DaoTestFixtures`

### Web/Controller Tests
Testing Struts actions and web layer:
- Located in `*/web/` directories
- Test request/response handling
- Often mock service layers

### Module-Specific Tests

#### PMmodule (Program Management)
- Program, Vacancy, Waitlist management
- Criteria and selection options
- Security role tests

#### Billing Tests
Province-specific billing functionality:
- **BC**: Teleplan integration, MSP billing
- **ON**: OHIP billing, claims processing

#### Clinical Module Tests
- **casemgmt**: Case management workflow
- **prevention**: Immunization tracking
- **measurement**: Vital signs, lab values
- **hl7**: Message parsing and processing

## Known Issues and Limitations

### Current Challenges

1. **Database Dependencies**
   - Tests require actual database instance
   - Slower execution compared to in-memory tests
   - Potential for test pollution

2. **Spring Context**
   - Full Spring context loading for each test
   - Longer startup times
   - Memory intensive

3. **Test Isolation**
   - Some tests may not properly clean up
   - Order-dependent test failures possible
   - Database state persistence between tests

### Excluded Tests

The following tests are excluded from regular Maven test runs via Surefire plugin configuration:

- **HinValidatorTest** - Health Insurance Number validation
- **MCEDT Tests** (`**/*EDTTest.java`) - Medical Certificate Electronic Data Transfer
- **AR2005 Tests** (`**/AR2005*.java`) - Annual Report 2005 related
- **OntarioMDSpec4DataTest** - Ontario MD specification tests
- **ONAREnhancedBornConnectorTest** - BORN integration tests
- **E2E Tests** (`org/oscarehr/e2e/**/*.java`) - End-to-end tests

These tests are compiled but not executed during normal `mvn test` runs. They can be run explicitly when needed.
See `/workspace/docs/Testing_Exclusion_of_MCEDT_and_HinValidator_tests.md` for full details.

## Maintenance Guidelines

### When Working with Legacy Tests

1. **Don't Migrate Unless Necessary**
   - Keep working tests as-is
   - Only migrate when tests need significant changes

2. **Follow Existing Patterns**
   - Use JUnit 4 annotations
   - Extend appropriate base classes
   - Use established utilities

3. **Database Considerations**
   - Always clean up test data
   - Use transactions where possible
   - Be aware of shared database state

## Comparison with Modern Tests

| Aspect | Legacy Tests (JUnit 4) | Modern Tests (JUnit 5) |
|--------|------------------------|------------------------|
| **Framework** | JUnit 4 (with some JUnit 3) | JUnit 5 (Jupiter) |
| **Location** | `/src/test/` | `/src/test-modern/` |
| **Test Count** | ~374 test files | 23 tests |
| **Database** | Real MariaDB/MySQL | H2 in-memory |
| **Execution Speed** | 5-15 minutes | < 4 seconds |
| **Assertions** | JUnit assert methods | AssertJ fluent |
| **Organization** | Package/class hierarchy | @Nested classes + files |
| **Naming** | testMethodName() | should_action_when_condition() |
| **Spring Context** | Full applicationContextTest.xml | Optimized test contexts |
| **Base Classes** | DaoTestFixtures | OpenOTestBase family |

## Test Execution in CI/CD

Both test suites run automatically:
1. Modern tests execute first (JUnit 5)
2. Legacy tests execute second (JUnit 4)
3. Build fails if either suite has failures

## Future Considerations

While the legacy tests continue to provide value:
- New tests should use the modern framework
- Legacy tests remain until explicitly migrated
- No timeline for forced migration
- Both frameworks coexist indefinitely

## Support

For legacy test issues:
1. Check test logs in `target/surefire-reports/`
2. Verify database connectivity
3. Review Spring context configuration
4. Consult existing similar tests for patterns

---

*Last Updated: January 2026*
*Version: 1.0*
*Status: Active*