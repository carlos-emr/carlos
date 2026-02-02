# OpenO EMR Modern Test Framework Guide

## Overview

OpenO EMR uses a dual test framework approach with JUnit 5 (modern) tests running alongside legacy JUnit 4 tests. This guide covers the current state of the modern test framework and how to develop tests.

**Status**: ✅ Production Ready

## Current Test Results

### Unit Tests
- **129 of 129 passing** - All unit tests pass
  - `DemographicManagerUnitTest` - 117 tests ✅ (18 @Nested classes covering 66 methods)
  - `TicklerManagerUnitTest` - 9 tests ✅ (5 validation + 4 business operations)
  - `TicklerDaoUnitTest` - 3 tests ✅

### Integration Tests
- **11 of 12 passing** - TicklerDao integration tests
- Tests are split across multiple files by operation type:
  - `TicklerDaoFindIntegrationTest` - 5 tests ✅
  - `TicklerDaoQueryIntegrationTest` - 3 tests ✅
  - `TicklerDaoAggregateIntegrationTest` - 3 tests ✅
  - `TicklerDaoWriteIntegrationTest` - 1 test ❌ (lst_gender table dependency)

## Quick Start

### Running Tests

```bash
# Run all tests (modern + legacy)
mvn test
make install --run-tests

# Run specific modern test
mvn test -Dtest=TicklerDaoIntegrationTest
```

### Writing Your First Test

```java
package ca.openosp.openo.your.domain;

import ca.openosp.openo.test.base.OpenOTestBase;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Your Component Integration Tests")
@Tag("integration")
public class YourComponentIntegrationTest extends OpenOTestBase {

    @Test
    @DisplayName("should perform expected behavior when condition met")
    void should_performExpectedBehavior_when_conditionMet() {
        // Given
        Entity entity = createTestEntity();

        // When
        Result result = yourService.process(entity);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
    }
}
```

## Architecture

### Directory Structure

```
src/test-modern/
├── java/ca/openosp/openo/
│   ├── test/
│   │   ├── base/              # Base test classes
│   │   │   ├── OpenOTestBase.java
│   │   │   ├── OpenODaoTestBase.java
│   │   │   └── OpenOWebTestBase.java
│   │   ├── mocks/             # Mock implementations
│   │   │   └── MockSecurityInfoManager.java
│   │   ├── unit/              # Unit test infrastructure
│   │   │   └── OpenOUnitTestBase.java
│   │   ├── examples/          # Example tests
│   │   └── simple/            # Framework validation tests
│   └── tickler/               # Domain-specific tests (example)
│       ├── dao/               # DAO tests
│       │   ├── archive/      # Original single-file tests (preserved for reference)
│       │   ├── TicklerDaoBaseIntegrationTest.java
│       │   ├── TicklerDaoFindIntegrationTest.java
│       │   ├── TicklerDaoQueryIntegrationTest.java
│       │   ├── TicklerDaoAggregateIntegrationTest.java
│       │   ├── TicklerDaoWriteIntegrationTest.java
│       │   └── TicklerDaoUnitTest.java
│       ├── manager/           # Manager/Service tests
│       │   ├── SimpleTicklerManagerTest.java
│       │   └── TicklerManagerUnitTest.java
│       └── TicklerUnitTestBase.java
└── resources/
    ├── META-INF/
    │   └── persistence.xml    # JPA configuration
    └── test-context-*.xml     # Spring contexts
```

### Test Types

#### Integration Tests
- Extend `OpenOTestBase` or `OpenODaoTestBase`
- Use real Spring context with H2 in-memory database
- Test actual component interactions
- File naming: `*IntegrationTest.java`

#### Unit Tests
- Extend `OpenOUnitTestBase` or domain-specific unit base
- Mock SpringUtils and dependencies
- Test logic in isolation
- File naming: `*UnitTest.java`

## Key Components

### Base Classes

#### OpenOTestBase
Base class for all integration tests. Provides:
- Spring context initialization
- SpringUtils anti-pattern handling
- Transaction management
- Common test utilities

```java
public abstract class OpenOTestBase {
    @Autowired
    protected ApplicationContext applicationContext;

    @PersistenceContext(unitName = "testPersistenceUnit")
    protected EntityManager entityManager;

    @BeforeEach
    void setUpSpringUtils() {
        // Handles SpringUtils static initialization
    }
}
```

#### OpenOUnitTestBase
Base class for unit tests with mocked dependencies:
- MockedStatic for SpringUtils
- Mock registry management
- No Spring context required

```java
public abstract class OpenOUnitTestBase {
    protected MockedStatic<SpringUtils> springUtilsMock;
    protected Map<Class<?>, Object> mockedBeans = new HashMap<>();

    protected void registerMock(Class<?> clazz, Object mock) {
        mockedBeans.put(clazz, mock);
    }
}
```

### Configuration

#### Maven Configuration (pom.xml)
```xml
<!-- JUnit 5 with Java 21 support -->
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.10.1</version>
    <scope>test</scope>
</dependency>

<!-- Surefire configuration for ByteBuddy Java 21 compatibility -->
<plugin>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <argLine>
            -Dnet.bytebuddy.experimental=true
            --add-opens java.base/java.lang=ALL-UNNAMED
        </argLine>
    </configuration>
</plugin>
```

#### Spring Test Context
Located in `src/test-modern/resources/`:
- `test-context-dao.xml` - DAO and database configuration
- `test-context-persistence.xml` - JPA/Hibernate setup
- `test-context-security.xml` - Mock security configuration

## Best Practices

### Test Naming Convention

Use BDD-style naming for clarity:

```java
// Pattern 1: should_expectedBehavior_when_condition
@Test
void should_returnActiveTicklers_when_demographicNumberProvided() { }

// Pattern 2: methodName_scenario_expectedOutcome
@Test
void findById_validId_returnsTickler() { }

// Pattern 3: shouldExpectedBehavior (simple cases)
@Test
void shouldLoadSpringContext() { }
```

### Test Organization

1. **Use @Nested classes** for logical grouping:
```java
@Nested
@DisplayName("Validation Logic")
class ValidationLogic {
    @Test
    void should_validateRequiredFields() { }
}

@Nested
@DisplayName("Business Operations")
class BusinessOperations {
    @Test
    void should_persistValidEntity() { }
}
```

2. **Use @Tag annotations** for test categorization:
```java
@Tag("unit")        // Fast, isolated tests
@Tag("integration") // Database/Spring context tests
@Tag("slow")        // Long-running tests
```

3. **Create domain-specific base classes** for shared setup:
```java
public abstract class TicklerTestBase extends OpenOTestBase {
    protected Tickler createTestTickler() {
        // Common test data creation
    }
}
```

### Database Testing

#### Using H2 In-Memory Database
- Configured with MySQL compatibility mode
- Schema auto-created from entities
- Transactions roll back after each test
- No cleanup required

```java
@Test
@Transactional
@Rollback
void should_persistAndRetrieveEntity() {
    // Database changes are automatically rolled back
}
```

### Handling SpringUtils Anti-Pattern

The codebase uses static `SpringUtils.getBean()` calls. The framework handles this:

#### For Integration Tests
- OpenOTestBase automatically configures SpringUtils
- Real Spring beans are available

#### For Unit Tests
- OpenOUnitTestBase provides MockedStatic
- Register mocks before use:

```java
@BeforeEach
void setUp() {
    mockDao = mock(TicklerDao.class);
    registerMock(TicklerDao.class, mockDao);
}
```

## Common Patterns

### Testing DAOs

```java
public class TicklerDaoIntegrationTest extends OpenODaoTestBase {

    private TicklerDao ticklerDao;

    @BeforeEach
    void setUp() {
        ticklerDao = SpringUtils.getBean(TicklerDao.class);
    }

    @Test
    void should_findActiveTicklers() {
        // Given - setup test data
        Tickler tickler = createAndPersistTickler();

        // When - execute DAO method
        List<Tickler> results = ticklerDao.findActive();

        // Then - verify results
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(tickler.getId());
    }
}
```

### Testing Managers/Services

```java
public class TicklerManagerUnitTest extends TicklerUnitTestBase {

    @Mock
    private TicklerDao mockDao;

    private TicklerManager manager;

    @BeforeEach
    void setUp() {
        registerMock(TicklerDao.class, mockDao);
        manager = new TicklerManagerImpl();
    }

    @Test
    void should_validateBeforePersisting() {
        // Test business logic with mocked dependencies
    }
}
```

## Troubleshooting

### Common Issues

#### SpringUtils.getBean() returns null
- Ensure test extends OpenOTestBase
- Check Spring context configuration
- Verify bean is defined in test context

#### ByteBuddy Java 21 errors
- Verify `-Dnet.bytebuddy.experimental=true` in Maven config
- Update to Mockito 5.8.0 or later

#### Static initialization failures
- Mock dependencies before creating static mocks
- Example: Mock OscarLogDao before mocking LogAction

#### Test not running
- Check naming convention (*Test.java)
- Verify @Test annotation present
- Ensure test is in src/test-modern/java

### Debug Commands

```bash
# Run with full stack traces
mvn test -X

# Run specific test with debug output
mvn test -Dtest=YourTest -Dmaven.surefire.debug

# Check test compilation
mvn test-compile
```

## Migration from Legacy Tests

### When to Migrate
- When adding new features
- When fixing bugs in tested code
- During refactoring efforts

### How to Migrate
1. Create new test in src/test-modern/
2. Extend appropriate base class
3. Convert assertions to AssertJ
4. Add @DisplayName annotations
5. Keep legacy test until confident
6. Remove legacy test when ready

### What Not to Migrate
- Working tests with good coverage
- Tests tightly coupled to legacy frameworks
- Tests scheduled for removal

## Resources

### Documentation
- `/workspace/docs/modern-test-framework-guide.md` - This guide
- `/workspace/docs/test-writing-best-practices.md` - Detailed best practices
- `/workspace/CLAUDE.md` - Project context including test section

### Examples
- `ca.openosp.openo.tickler.dao.*` - DAO integration tests
- `ca.openosp.openo.tickler.manager.*` - Service unit tests
- `ca.openosp.openo.test.base.*` - Framework base classes

### Tools
- JUnit 5: https://junit.org/junit5/docs/current/user-guide/
- AssertJ: https://assertj.github.io/doc/
- Mockito: https://javadoc.io/doc/org.mockito/mockito-core/latest/