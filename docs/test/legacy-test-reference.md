# Legacy Test Framework Reference

> **HISTORICAL DOCUMENT.** The JUnit 4 suite described here has been removed. All tests
> now live in the single JUnit Jupiter suite under `src/test/`. Kept for pre-migration
> context only — do not follow its guidance for new work. See
> [`README.md`](README.md) for the current framework.

## Overview

Before the migration, CARLOS carried two test suites side by side: the inherited JUnit 4
suite (with some JUnit 3 `junit.framework` usage) under `src/test/`, and the modern JUnit 5
suite that started life under `src/test-modern/`. The JUnit 4 suite has since been retired
and `src/test-modern/` was collapsed into `src/test/`, so everything below describes a
state that no longer exists in the repository. Recover the old sources from git history if
you need them.

**Status at retirement**: removed; no JUnit 4 tests remain
**Framework**: JUnit 4 (with some JUnit 3)
**Test Count**: ~374 test files
**Location**: `src/test/` (the same tree the JUnit 5 suite now owns)

## Legacy Test Structure

The retired suite was organized by module rather than by test type:

```
src/test/
├── java/
│   ├── io/github/carlos_emr/carlos/
│   │   ├── commn/
│   │   │   ├── dao/              # DAO tests (largest collection)
│   │   │   │   └── utils/        # Test utilities
│   │   │   │       ├── EntityDataGenerator.java
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

The legacy tests used JUnit 4 annotations and assertions:

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

`io.github.carlos_emr.carlos.commn.dao.DaoTestFixtures` was the shared base class. It
provided:

- Database connection setup via `@BeforeClass` static initialization
- Spring context initialization from `applicationContextTest.xml`
- `LoggedInInfo` for authentication context
- A mix of JUnit 3 (`junit.framework`) and JUnit 4 (`@BeforeClass`) imports

Its modern replacement is the `CarlosTestBase` family (see [`README.md`](README.md)).

#### Test Utilities

`io.github.carlos_emr.carlos.commn.dao.utils` held the shared helpers:

- **EntityDataGenerator**: created test entities with valid data
- **DataUtils**: common data manipulation utilities
- **AuthUtils**: authentication/authorization test helpers
- **ConfigUtils**: test configuration management (loaded `over_ride_config.properties`)

These four helpers survived the migration unchanged and still live at
`src/test/java/io/github/carlos_emr/carlos/commn/dao/utils/`. The current JUnit 5 suite
imports `io.github.carlos_emr.carlos.commn.dao.utils.*` extensively, so they are live,
reusable test infrastructure rather than legacy leftovers; only the JUnit 4 tests that
originally consumed them are gone.

## How the Legacy Suite Ran

A plain `mvn test` ran both suites, modern tests first and then the JUnit 4 tests, and the
build failed if either had failures. The legacy portion took roughly 5-15 minutes because it
needed a real MariaDB/MySQL instance rather than the in-memory H2 database the modern suite
uses. Individual classes were selected with `-Dtest=AllergyDaoTest` or package/glob patterns
such as `-Dtest=*DaoTest`.

Today `mvn test` runs only the JUnit 5 suite; see [`README.md`](README.md) for the current
commands and tag-based filtering.

## Test Categories

### DAO Tests (~200 files)

The largest category, testing data access objects:

- Located in `*/dao/` directories
- Tested database operations (CRUD)
- Used real database connections (not in-memory)
- Extended `DaoTestFixtures`

### Web/Controller Tests

Tested Struts actions and the web layer:

- Located in `*/web/` directories
- Tested request/response handling
- Often mocked service layers

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

## Known Issues That Motivated the Migration

1. **Database Dependencies**
   - Tests required an actual database instance
   - Slower execution compared to in-memory tests
   - Potential for test pollution

2. **Spring Context**
   - Full Spring context loading for each test
   - Longer startup times
   - Memory intensive

3. **Test Isolation**
   - Some tests did not properly clean up
   - Order-dependent test failures were possible
   - Database state persisted between tests

### Tests That Were Excluded

Several legacy tests were compiled but excluded from regular Maven runs through the Surefire
plugin configuration:

- **HinValidatorTest** - Health Insurance Number validation
- **MCEDT Tests** (`**/*EDTTest.java`) - Medical Claims Electronic Data Transfer
- **AR2005 Tests** (`**/AR2005*.java`) - Annual Report 2005 related
- **OntarioMDSpec4DataTest** - Ontario MD specification tests
- **E2E Tests** (`org/oscarehr/e2e/**/*.java`) - End-to-end tests

These exclusions were retired together with the suite.

## Comparison with the Modern Suite

| Aspect | Legacy Tests (JUnit 4, removed) | Modern Tests (JUnit 5, current) |
|--------|----------------------------------|----------------------------------|
| **Framework** | JUnit 4 (with some JUnit 3) | JUnit 5 (Jupiter) |
| **Location** | `src/test/` | `src/test/` |
| **Database** | Real MariaDB/MySQL | H2 in-memory |
| **Execution Speed** | 5-15 minutes | Seconds for unit tests |
| **Assertions** | JUnit assert methods | AssertJ fluent |
| **Organization** | Package/class hierarchy | @Nested classes + files |
| **Naming** | testMethodName() | shouldAction_whenCondition() |
| **Spring Context** | Full applicationContextTest.xml | Optimized test contexts |
| **Base Classes** | DaoTestFixtures | CarlosTestBase family |

## Working With Tests Today

- New tests use the JUnit 5 framework and conventions: start with
  [`modern-test-framework-guide.md`](modern-test-framework-guide.md), then
  [`test-writing-guide.md`](test-writing-guide.md) for context configuration patterns.
- Do not add JUnit 4 annotations, `DaoTestFixtures`, or `junit.framework` imports; the
  dependencies and base classes are gone.
- Test logs are still written to `target/surefire-reports/`.

---

*Last Updated: September 2026*
*Version: 2.0*
*Status: Historical (JUnit 4 suite removed)*
