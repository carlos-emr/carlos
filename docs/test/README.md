# CARLOS EMR Test Documentation

## Overview

This directory contains the documentation for the CARLOS EMR test framework: a single
JUnit Jupiter suite under `src/test/`. The framework was introduced alongside the legacy
JUnit 4 tests in a parallel `src/test-modern/` tree; that migration is finished — the
JUnit 4 suite has been removed and `src/test-modern/` was collapsed into `src/test/`.

## Current Status

- **Framework**: ✅ Production Ready
- **Layout**: one Jupiter suite under `src/test/`; no legacy suite to keep in sync
- **Java 25 Support**: ✅ Fully compatible with ByteBuddy experimental flag
  (`-Dnet.bytebuddy.experimental=true`, set in the surefire `argLine`)

Run `mvn test` for a current pass/fail count rather than trusting a number recorded here.

## Quick Start Guide

If you're new to the test framework, start here:

1. **[Modern Test Framework Guide](modern-test-framework-guide.md)** - Complete guide to using the framework
2. **[Test Writing Best Practices](test-writing-best-practices.md)** - How to write effective tests

## Documentation Index

### Essential Guides

| Document | Purpose | When to Read |
|----------|---------|--------------|
| [Modern Test Framework Guide](modern-test-framework-guide.md) | Main guide for using the test framework | **Start here** - When writing any tests |
| [Test Writing Best Practices](test-writing-best-practices.md) | Comprehensive best practices and patterns | Before writing your first test |
| [Implementation Summary](modern-test-implementation-summary.md) | High-level overview of what was implemented | Understanding the framework setup |

### Technical Documentation

| Document | Purpose | When to Read |
|----------|---------|--------------|
| [Framework Complete Documentation](modern-test-framework-complete.md) | Detailed technical implementation | Deep technical understanding needed |
| [Legacy Test Reference](legacy-test-reference.md) | **Historical** — describes the removed JUnit 4 suite | Reading pre-migration history only |

## Test Framework Architecture

```
src/test/
├── java/io/github/carlos_emr/carlos/
│   ├── test/
│   │   ├── base/              # Base test classes (CarlosTestBase, ...)
│   │   ├── unit/              # Unit test infrastructure (CarlosUnitTestBase)
│   │   ├── builders/          # Test data builders
│   │   ├── logging/           # LogCapture, for Log4j2 assertions
│   │   ├── mocks/             # Mock implementations
│   │   └── support/           # Shared test support
│   ├── managers/              # Manager layer unit tests
│   │   ├── DemographicUnitTestBase.java      # Base class with test data builders
│   │   └── DemographicManagerUnitTest.java   # 117 tests in 18 @Nested classes
│   └── <domain>/              # Per-domain tests, mirroring the main tree
│       └── tickler/
│           ├── dao/           # DAO tests (integration + unit)
│           └── manager/       # Tickler Manager tests
└── resources/                 # Spring contexts, test.properties, log4j2.xml
```

## Running Tests

### Using Make Script (Recommended)
```bash
# Run all tests
make install --run-tests

# Run only unit tests (fast, no database)
make install --run-unit-tests

# Run only integration tests
make install --run-integration-tests
```

Those three are the only test modes the script accepts; see
`.devcontainer/development/scripts/make`.

### Using Maven Directly
```bash
# Run all tests
mvn test

# Run specific test types
mvn test -Dgroups="unit"          # Unit tests only
mvn test -Dgroups="integration"   # Integration tests only
mvn test -Dtest=TicklerDao*       # Specific test pattern
```

## Key Features

### 1. Single Suite
- All tests live in `src/test/java`, run by one surefire execution
- Surefire's `<includes>` is an **allowlist**: a class whose name and package match
  none of its patterns is silently never run. Name new tests `*UnitTest` /
  `*IntegrationTest`, or put them under a `test/` package.

### 2. SpringUtils Anti-Pattern Handling
- **Integration Tests**: Automatic SpringUtils configuration
- **Unit Tests**: MockedStatic support for isolation

### 3. BDD Naming Convention
```java
// Clear, self-documenting test names with ONE underscore separator
// Preposition after underscore (_when, _by, _for, _with, _to, _from) should read naturally
void shouldReturnActiveTicklers_whenDemographicNumberProvided()  // _when for conditions
void shouldReturnSpecialists_byServiceName()                      // _by for lookups
void shouldPersistMeasurement_withBloodPressureData()             // _with for parameters
void shouldReturnTrue_forOMedsCppCode()                           // _for for inputs
void shouldConvertExtensionList_toMapKeyedByExtKey()              // _to for transformations
```

### 4. Comprehensive Tagging
```java
@Tag("integration")  // Test type
@Tag("dao")          // Layer
@Tag("read")         // Operation
@Tag("filter")       // Extended operation
```

### 5. Multi-File Scalability
Tests are organized by operation type for scalability:
- `TicklerDaoFindIntegrationTest` - Find operations
- `TicklerDaoQueryIntegrationTest` - Query operations
- `TicklerDaoAggregateIntegrationTest` - Aggregation operations
- `TicklerDaoWriteIntegrationTest` - Write operations

## Writing Your First Test

### Integration Test Example

```java
@DisplayName("My Component Integration Tests")
@Tag("integration")
public class MyComponentIntegrationTest extends CarlosTestBase {

    @Test
    @DisplayName("should perform expected action when condition is met")
    void shouldPerformExpectedAction_whenConditionMet() {
        // Given - setup
        Entity entity = createTestEntity();

        // When - execute
        Result result = component.process(entity);

        // Then - verify
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
    }
}
```

### Unit Test Example

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("My Manager Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("manager")
public class MyManagerUnitTest extends CarlosUnitTestBase {

    @Mock private SomeDao mockDao;
    @Mock private AnotherDao mockAnotherDao;

    private MyManagerImpl manager;
    private MockedStatic<LogAction> logActionMock;

    @BeforeEach
    void setUp() {
        // Register mocks for SpringUtils BEFORE static class mocking
        registerMock(SomeDao.class, mockDao);
        registerMock(OscarLogDao.class, createAndRegisterMock(OscarLogDao.class));

        // Mock static classes
        logActionMock = mockStatic(LogAction.class);

        // Create manager and inject dependencies via reflection
        manager = new MyManagerImpl();
        injectDependency(manager, "someDao", mockDao);
    }

    @AfterEach
    void tearDown() {
        if (logActionMock != null) logActionMock.close();
    }

    @Nested
    @DisplayName("Core Operations")
    class CoreOperationsTests {
        @Test
        @DisplayName("should return entity when valid ID provided")
        void shouldReturnEntity_whenValidIdProvided() {
            when(mockDao.find(1)).thenReturn(new Entity());
            Entity result = manager.getEntity(loggedInInfo, 1);
            assertThat(result).isNotNull();
        }
    }
}
```

## Common Issues and Solutions

### Issue: SpringUtils.getBean() returns null
**Solution**: Ensure test extends `CarlosTestBase` and Spring context is configured

### Issue: ByteBuddy Java 25 compatibility error
**Solution**: Verify `-Dnet.bytebuddy.experimental=true` is in Maven configuration

### Issue: Static initialization failures
**Solution**: Mock dependencies before creating static mocks (see unit testing guide)

### Issue: Test not discovered by Maven
**Solution**: Check it matches a surefire `<include>` pattern in `pom.xml` — the list is an
allowlist, so a non-matching class is skipped with no error. `*UnitTest` and
`*IntegrationTest` always match.

## Contributing

When adding new test documentation:
1. Follow the existing structure and naming conventions
2. Update this README with any new guides
3. Ensure examples are from actual working code
4. Include both positive and negative test cases

## Support

For questions or issues:
1. Check the [Test Writing Best Practices](test-writing-best-practices.md)
2. Review existing test implementations in `src/test/`
3. Consult the main project documentation in `/workspace/CLAUDE.md`

---

*Last Updated: September 2026*
*Version: 1.2*
*Status: Production Ready*
