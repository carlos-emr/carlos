# CARLOS EMR Modern Test Framework

## Author: yingbull
## Date: September 15, 2025

## Overview

This directory contains the modern test framework for CARLOS EMR, built with JUnit 5, Spring Test, and modern Java 21 features. The framework is designed to coexist with legacy tests while providing a migration path to modern testing practices.

## Table of Contents
- [Architecture](#architecture)
- [Key Features](#key-features)
- [Test Organization](#test-organization)
- [Running Tests](#running-tests)
- [Writing New Tests](#writing-new-tests)
- [Best Practices](#best-practices)
- [Migration Guide](#migration-guide)

## Architecture

### Directory Structure
```
src/test-modern/
├── java/
│   └── io/github/carlos_emr/carlos/
│       ├── test/base/          # Base test classes
│       │   ├── CarlosTestBase.java
│       │   ├── CarlosDaoTestBase.java
│       │   └── CarlosWebTestBase.java
│       └── tickler/             # Tickler module tests
│           ├── dao/             # DAO layer tests
│           ├── manager/         # Service layer tests
│           └── web/             # Web layer tests
└── resources/
    ├── test-applicationContext.xml
    ├── test-applicationContext-dao.xml
    ├── test-applicationContext-security.xml
    └── test.properties
```

### Test Layers

#### 1. **Base Test Classes**
- **CarlosTestBase**: Foundation for all tests, handles SpringUtils anti-pattern
- **CarlosDaoTestBase**: Database-specific testing with transaction management
- **CarlosWebTestBase**: Web layer testing with Struts2 ActionContext support

#### 2. **Test Categories**
- **DAO Tests**: Database operations, queries, and persistence
- **Manager Tests**: Business logic and service layer validation
- **Web Tests**: Struts2 actions, security, and request handling

## Key Features

### SpringUtils Anti-Pattern Handling
The production code uses `SpringUtils.getBean()` instead of proper dependency injection. Our test framework properly initializes SpringUtils with the test context, ensuring legacy code works correctly during testing.

```java
// Production code pattern (anti-pattern)
TicklerDao dao = SpringUtils.getBean(TicklerDao.class);

// Test validates this works correctly
verifyDaoSpringUtilsIntegration(TicklerDao.class);
```

### Modern Testing Features

#### JUnit 5 Capabilities
- **@DisplayName**: Human-readable test names
- **@ParameterizedTest**: Data-driven testing
- **@Nested**: Organized test groups
- **@Order**: Controlled test execution order
- **@Timeout**: Test time limits

#### Assertion Libraries
- **AssertJ**: Fluent assertions for better readability
- **Mockito 5**: Java 21-compatible mocking
- **Hamcrest**: Matcher-based assertions

#### Database Testing
- **H2 In-Memory**: Fast unit tests
- **Testcontainers**: Real MariaDB for integration tests
- **@Transactional/@Rollback**: Automatic cleanup

## Test Organization

### Tickler Module Test Suite

#### TicklerDaoTest
**Coverage**: Data access layer operations
- CRUD operations
- Complex queries and filters
- Pagination
- Batch operations
- Transaction handling

#### TicklerManagerTest
**Coverage**: Business logic and workflows
- Tickler creation and validation
- Status transitions
- Priority management
- Batch updates
- Overdue task identification

#### AddTickler2ActionTest
**Coverage**: Web layer and security
- Security privilege enforcement
- Request parameter handling
- Validation
- Session management
- Error handling

## Running Tests

### Maven Profiles

```bash
# Run legacy tests only (default)
mvn test

# Run modern tests only
mvn test -Pmodern-tests

# Run both test suites sequentially
mvn test -Pall-tests

# Run specific test class
mvn test -Pmodern-tests -Dtest=TicklerDaoTest

# Run specific test method
mvn test -Pmodern-tests -Dtest=TicklerDaoTest#testCreateAndRetrieve

# Run tests with specific tags
mvn test -Pmodern-tests -Dgroups="unit"
mvn test -Pmodern-tests -DexcludedGroups="slow,integration"
```

### Test Execution Options

```bash
# With coverage report
mvn test -Pmodern-tests jacoco:report

# With debug output
mvn test -Pmodern-tests -Dtest.logging.level=DEBUG

# With specific database
mvn test -Pmodern-tests -Dtest.db.url=jdbc:mariadb://localhost:3306/testdb
```

## Writing New Tests

### DAO Test Template

```java
/**
 * Test suite for {@link YourDao} data access operations.
 *
 * @author yingbull
 * @since 2025-09-15
 */
@DisplayName("YourDao Integration Tests")
class YourDaoTest extends CarlosDaoTestBase {

    private YourDao dao;

    @BeforeEach
    void setUp() {
        // Get DAO using SpringUtils pattern
        dao = SpringUtils.getBean(YourDao.class);

        // Verify SpringUtils integration
        verifyDaoSpringUtilsIntegration(YourDao.class);
    }

    @Test
    @DisplayName("Should perform specific operation")
    void testSpecificOperation() {
        // Given - test setup

        // When - operation execution

        // Then - assertions
    }
}
```

### Manager Test Template

```java
/**
 * Unit test suite for {@link YourManager} business logic.
 *
 * @author yingbull
 * @since 2025-09-15
 */
@DisplayName("YourManager Unit Tests")
class YourManagerTest extends CarlosTestBase {

    @Mock
    private YourDao mockDao;

    @InjectMocks
    private YourManager manager;

    @Test
    @DisplayName("Should enforce business rule")
    void testBusinessRule() {
        // Given - mock setup
        when(mockDao.find(any())).thenReturn(testData);

        // When - business operation

        // Then - verify behavior
        verify(mockDao).persist(any());
    }
}
```

### Web Action Test Template

```java
/**
 * Integration test suite for {@link Your2Action} Struts2 action.
 *
 * @author yingbull
 * @since 2025-09-15
 */
@DisplayName("Your2Action Web Tests")
class Your2ActionTest extends CarlosWebTestBase {

    private Your2Action action;

    @BeforeEach
    void setUp() {
        action = new Your2Action();
    }

    @Test
    @DisplayName("Should enforce security")
    void testSecurity() {
        // Given - deny privilege
        denyPrivilege("_object", "w");

        // When/Then - should throw
        assertThatThrownBy(() -> executeAction(action))
            .isInstanceOf(SecurityException.class);
    }
}
```

## Best Practices

### Documentation Standards

1. **Class-Level JavaDoc**
   - Purpose and scope
   - Test coverage summary table
   - Configuration notes
   - @author and @since tags

2. **Method-Level JavaDoc**
   - What is being tested
   - Business scenario/user story
   - Special conditions

3. **Inline Comments**
   - Complex logic explanation
   - Important business rules
   - Workarounds for known issues

### Test Design Principles

1. **AAA Pattern**
   ```java
   @Test
   void testExample() {
       // Arrange (Given)
       Entity entity = createTestEntity();

       // Act (When)
       Result result = service.process(entity);

       // Assert (Then)
       assertThat(result).isNotNull();
   }
   ```

2. **Test Isolation**
   - Each test runs independently
   - No shared mutable state
   - Transaction rollback by default

3. **Meaningful Names**
   - Use @DisplayName for clarity
   - Method names describe what is tested
   - Include expected outcome

4. **Data Factories**
   - Helper methods for test data creation
   - Consistent test data patterns
   - Avoid magic numbers/strings

5. **Partial Mock Implementation**
   - When creating mock implementations of interfaces with many methods
   - Only implement the methods needed for your specific tests
   - Document that it's a partial implementation
   - This keeps tests focused and maintainable

### Performance Considerations

1. **Use H2 for unit tests** - Fast, in-memory
2. **Testcontainers for integration** - Real database behavior
3. **Batch tests with flush()** - Optimize bulk operations
4. **@Tag("slow") for long tests** - Exclude from regular builds

## Migration Guide

### Phase 1: Setup
- [x] Create modern test framework structure
- [x] Implement base test classes
- [x] Handle SpringUtils anti-pattern
- [x] Create example tests

### Phase 2: Migration
- [ ] Identify critical test gaps
- [ ] Write new tests using modern framework
- [ ] Gradually migrate legacy tests
- [ ] Maintain test coverage metrics

### Phase 3: Completion
- [ ] Deprecate legacy test framework
- [ ] Remove JUnit 4 dependencies
- [ ] Update CI/CD pipelines
- [ ] Document lessons learned

### Migration Checklist

When migrating a legacy test:

1. **Understand the test**
   - What does it test?
   - What are the assertions?
   - Are there hidden dependencies?

2. **Choose appropriate base class**
   - CarlosTestBase for general tests
   - CarlosDaoTestBase for database tests
   - CarlosWebTestBase for web layer tests

3. **Update imports**
   ```java
   // Old
   import org.junit.Test;
   import static junit.framework.Assert.*;

   // New
   import org.junit.jupiter.api.Test;
   import static org.assertj.core.api.Assertions.*;
   ```

4. **Convert annotations**
   ```java
   // Old
   @Before
   public void setUp() {}

   // New
   @BeforeEach
   void setUp() {}
   ```

5. **Improve assertions**
   ```java
   // Old
   assertEquals(expected, actual);

   // New
   assertThat(actual).isEqualTo(expected);
   ```

6. **Add documentation**
   - JavaDoc with @author and @since
   - Test coverage summary
   - Business context

## Troubleshooting

### Common Issues

#### SpringUtils Not Initialized
**Problem**: `NullPointerException` when using SpringUtils.getBean()
**Solution**: Ensure base class properly initializes SpringUtils
```java
verifySpringUtilsIntegration(YourBean.class);
```

#### Transaction Not Rolling Back
**Problem**: Test data persists between tests
**Solution**: Ensure @Transactional and @Rollback annotations are present

#### Mock Not Working
**Problem**: Real bean is used instead of mock
**Solution**: Use `replaceSpringUtilsBean()` method
```java
replaceSpringUtilsBean(YourManager.class, mockManager);
```

#### Struts2 Action Context Missing
**Problem**: ServletActionContext returns null
**Solution**: Extend CarlosWebTestBase and use `executeAction()`

## Support

For questions or issues:
1. Check this documentation
2. Review example tests in `/tickler/`
3. Consult the base class JavaDoc
4. Contact the development team

## Current Implementation Status

### Completed Components

#### Framework Infrastructure ✅
- Base test classes with SpringUtils anti-pattern handling
- Spring test context configuration
- Database test support (H2 and Testcontainers ready)
- Struts2 ActionContext mocking
- Maven profiles for parallel test execution

#### Tickler Module Tests ✅
- **TicklerDaoTest**: 12 test methods covering CRUD, search, pagination, batch operations
- **TicklerManagerTest**: 10 test methods covering business logic, validation, workflow
- **AddTickler2ActionTest**: 13 test methods covering security, validation, request handling

#### Documentation ✅
- Comprehensive JavaDoc with @author and @since tags
- Test coverage summary tables in class-level documentation
- Inline comments explaining complex logic
- This README with complete usage instructions

### Known Limitations

1. **MockTicklerManager**: Partial implementation - only includes methods used in tests
2. **Test Data**: Currently uses hardcoded test provider/demographic IDs
3. **Integration Tests**: Framework ready but no Testcontainers tests implemented yet
4. **Coverage**: Only Tickler module has tests - other modules need implementation

### Next Steps

1. Add tests for other critical modules (Demographic, Appointment, Billing)
2. Implement Testcontainers for true integration testing
3. Add performance benchmarking tests
4. Create test data builders for complex entities
5. Set up coverage reporting with JaCoCo

## Contributing

When contributing new tests:
1. Follow the established patterns
2. Include comprehensive JavaDoc with @author and @since
3. Maintain test coverage above 80%
4. Ensure all tests pass locally
5. Update this documentation as needed

---

*Last Updated: September 15, 2025*
*Author: yingbull*