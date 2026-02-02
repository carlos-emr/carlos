# OpenO EMR Test Writing Best Practices

## BDD Naming Convention

### Method Naming Pattern
All test methods follow BDD (Behavior-Driven Development) naming for self-documenting tests:

```java
// Pattern 1: should<ExpectedBehavior>_when<Condition> (PREFERRED - camelCase with ONE underscore)
@Test
void shouldReturnActiveTicklers_whenDemographicNumberProvided() { }

// Pattern 2: <methodName>_<scenario>_<expectedOutcome>
@Test
void findById_validId_returnsTickler() { }

// Pattern 3: should<ExpectedBehavior> (simple cases without conditions)
@Test
void shouldLoadSpringContext() { }
```

**IMPORTANT**: Use camelCase with exactly ONE underscore separating action from condition. Do NOT use multiple underscores like `should_return_tickler_when_valid()`.

### @DisplayName Best Practices

```java
@Test
@DisplayName("should return tickler when valid ID is provided")  // lowercase 'should'
void should_returnTickler_when_validIdProvided() { }
```

**Key Points:**
- Start with lowercase "should" - BDD convention
- Use natural language that reads like a sentence
- Match the method intent without redundancy
- Focus on behavior, not implementation

### Benefits of BDD Naming

1. **Self-Documenting**: Method names explain the test without reading implementation
2. **Better Failure Messages**: `FAILED: should_returnOnlyActiveTicklers_when_searchingByDemographic` immediately tells you what behavior is broken
3. **Living Documentation**: Tests serve as executable specifications
4. **Searchable**: Consistent pattern makes tests easy to find

## Test Organization

### Multi-File Structure for Scalability

The modern test framework uses a multi-file structure designed to scale to 50+ tests per component:

```
tickler/dao/
├── TicklerDaoBaseIntegrationTest.java      # Shared setup for all DAO integration tests
├── TicklerDaoFindIntegrationTest.java      # Find operations (5 tests, room for 15+)
├── TicklerDaoQueryIntegrationTest.java     # Query operations (3 tests, room for 20+)
├── TicklerDaoAggregateIntegrationTest.java # Aggregation ops (3 tests, room for 15+)
├── TicklerDaoWriteIntegrationTest.java     # Write operations (1 test, room for 30+)
└── TicklerDaoUnitTest.java                 # Unit tests with mocked dependencies
```

### Critical Requirements

**Test classes MUST be declared `public`** for Maven Surefire to discover them:
```java
public class TicklerDaoFindIntegrationTest extends TicklerDaoBaseIntegrationTest { // ✅ Correct
class TicklerDaoFindIntegrationTest extends TicklerDaoBaseIntegrationTest { // ❌ Won't run
```

### Single File vs Multiple Files

#### Keep Tests in ONE File When:
- Testing the same class/component (e.g., TicklerDao)
- Under 50 total tests
- Tests share setup/teardown logic
- Tests share helper methods
- Logical groupings are clear with @Nested

#### Split into MULTIPLE Files When:
- Over 50 tests for the same component
- Different test contexts (unit vs integration)
- Different setups needed (mock vs real database)
- Performance concerns (some tests are very slow)
- Testing different layers (DAO vs Service vs Controller)

### Using @Nested Classes

Organize related tests within a single file using @Nested:

```java
@DisplayName("Tickler DAO Tests")
class TicklerDaoMethodTest extends OpenOTestBase {

    @Nested
    @DisplayName("Find Operations")
    class FindOperations {
        @Test
        void should_findById() { }

        @Test
        void should_findActiveByDemographic() { }
    }

    @Nested
    @DisplayName("Query and Filter Operations")
    class QueryAndFilterOperations {
        @Test
        void should_applyCustomFilter() { }

        @Test
        void should_paginateResults() { }
    }

    @Nested
    @DisplayName("Aggregation Operations")
    class AggregationOperations {
        @Test
        void should_countActiveTicklers() { }

        @Test
        void should_groupByProvider() { }
    }

    @Nested
    @DisplayName("Write Operations")
    class WriteOperations {
        @Test
        void should_persistNewTickler() { }

        @Test
        void should_updateExistingTickler() { }
    }
}
```

## Test Tagging Standards

### Hierarchical Tag Structure

```java
@Tag("integration")  // Level 1: Test type
@Tag("dao")          // Level 2: Layer
@Tag("read")         // Level 3: CRUD operation
@Tag("filter")       // Level 4: Extended operation (optional)
class TicklerDaoMethodTest extends OpenOTestBase {
```

### Standard Tag Definitions

#### Level 1: Test Type
- `@Tag("unit")` - Unit tests with mocked dependencies
- `@Tag("integration")` - Integration tests with real dependencies
- `@Tag("database")` - Tests requiring database access
- `@Tag("e2e")` - End-to-end tests
- `@Tag("slow")` - Tests taking > 5 seconds
- `@Tag("fast")` - Tests taking < 1 second

#### Level 2: Layer
- `@Tag("dao")` - Data Access Object layer
- `@Tag("manager")` - Manager/Service layer tests
- `@Tag("service")` - Service/Business layer
- `@Tag("controller")` - Controller/Web layer
- `@Tag("api")` - API tests
- `@Tag("tickler")` - Domain-specific tag for tickler-related tests

#### Level 3: CRUD Operations
- `@Tag("create")` - Create/Insert operations (INSERT)
- `@Tag("read")` - Read/Select operations (SELECT)
- `@Tag("update")` - Update/Modify operations (UPDATE)
- `@Tag("delete")` - Delete/Remove operations (DELETE)

#### Level 4: Extended Operations
- `@Tag("query")` - Complex queries beyond simple reads
- `@Tag("search")` - Search operations with criteria
- `@Tag("filter")` - Filtering operations
- `@Tag("aggregate")` - Aggregation operations (COUNT, SUM, AVG)
- `@Tag("batch")` - Batch operations
- `@Tag("transaction")` - Transaction-specific tests

### Tag Usage Examples

```bash
# Run all read operations
mvn test -Dgroups="read"

# Run create and update tests
mvn test -Dgroups="create,update"

# Run all DAO integration tests
mvn test -Dgroups="integration,dao"

# Exclude slow tests
mvn test -DexcludedGroups="slow"

# Run only fast unit tests
mvn test -Dgroups="unit,fast"
```

## Test Data Management

### Test Data Creation Pattern

```java
public abstract class TicklerTestBase extends OpenOTestBase {

    protected Tickler createTestTickler() {
        Tickler tickler = new Tickler();
        tickler.setDemographicNo(1001);
        tickler.setMessage("Test tickler message");
        tickler.setStatus(Tickler.STATUS_ACTIVE);
        tickler.setPriority(Tickler.PRIORITY_NORMAL);
        tickler.setTaskAssignedTo("999998");
        tickler.setServiceDate(new Date());
        tickler.setCreator("999999");
        tickler.setUpdateDate(new Timestamp(System.currentTimeMillis()));
        return tickler;
    }

    protected Tickler createAndPersistTickler() {
        Tickler tickler = createTestTickler();
        entityManager.persist(tickler);
        entityManager.flush();
        return tickler;
    }

    protected List<Tickler> createMultipleTicklers(int count) {
        List<Tickler> ticklers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Tickler tickler = createTestTickler();
            tickler.setMessage("Test message " + i);
            ticklers.add(createAndPersistTickler());
        }
        return ticklers;
    }
}
```

### Test Isolation

```java
@Test
@Transactional
@Rollback  // Ensures database changes are rolled back after test
void should_isolateTestData() {
    // All database changes in this test are automatically rolled back
}
```

## Assertion Best Practices

### Use AssertJ Fluent Assertions

```java
// Good - AssertJ fluent assertions
assertThat(result).isNotNull();
assertThat(result.getStatus()).isEqualTo("ACTIVE");
assertThat(resultList)
    .hasSize(3)
    .extracting(Tickler::getStatus)
    .containsOnly("ACTIVE");

// Avoid - JUnit basic assertions
assertNotNull(result);
assertEquals("ACTIVE", result.getStatus());
```

### Comprehensive Assertions

```java
@Test
void should_returnCompleteTickler_when_foundById() {
    // Given
    Tickler original = createAndPersistTickler();

    // When
    Tickler found = ticklerDao.find(original.getId());

    // Then - Assert all important fields
    assertThat(found).isNotNull();
    assertThat(found.getId()).isEqualTo(original.getId());
    assertThat(found.getDemographicNo()).isEqualTo(original.getDemographicNo());
    assertThat(found.getMessage()).isEqualTo(original.getMessage());
    assertThat(found.getStatus()).isEqualTo(original.getStatus());
    assertThat(found.getPriority()).isEqualTo(original.getPriority());
}
```

## Manager Unit Testing Patterns

### Overview

Manager (service layer) unit tests verify business logic without database access. They use mocked DAOs and require special handling for:
- Multiple dependencies (often 10-20 mocks)
- Static class mocking (LogAction, DemographicContactCreator)
- Reflection-based dependency injection
- Security manager mocking

### Complete Manager Unit Test Structure

```java
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MyManager Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("manager")
public class MyManagerUnitTest extends MyDomainUnitTestBase {

    // Mock all dependencies
    @Mock private SomeDao mockSomeDao;
    @Mock private AnotherDao mockAnotherDao;
    // ... more mocks as needed

    private MyManagerImpl manager;
    private MockedStatic<LogAction> logActionMock;
    private MockedStatic<SomeStaticHelper> helperMock;

    @BeforeEach
    void setUp() {
        // 1. Register SpringUtils mocks FIRST (before static mocking)
        registerMock(SomeDao.class, mockSomeDao);
        registerMock(OscarLogDao.class, createAndRegisterMock(OscarLogDao.class));

        // 2. Mock static classes SECOND
        logActionMock = mockStatic(LogAction.class);
        helperMock = mockStatic(SomeStaticHelper.class);

        // 3. Configure default security behavior
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any()))
            .thenReturn(true);

        // 4. Create manager and inject dependencies via reflection
        manager = new MyManagerImpl();
        injectDependency(manager, "someDao", mockSomeDao);
        injectDependency(manager, "securityInfoManager", mockSecurityInfoManager);
    }

    @AfterEach
    void tearDown() {
        // CRITICAL: Close all static mocks to prevent test pollution
        if (helperMock != null) helperMock.close();
        if (logActionMock != null) logActionMock.close();
    }

    /**
     * Tests for security and privilege checking.
     */
    @Nested
    @DisplayName("Security Tests")
    @Tag("security")
    class SecurityTests {
        @Test
        @DisplayName("should throw exception when privilege denied")
        void shouldThrowException_whenPrivilegeDenied() {
            reset(mockSecurityInfoManager);
            when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any()))
                .thenReturn(false);

            assertThatThrownBy(() -> manager.doSomething(loggedInInfo, 1))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("missing required sec object");
        }
    }
}
```

### Reflection-Based Dependency Injection

When managers don't have setters for dependencies, use reflection:

```java
protected void injectDependency(Object target, String fieldName, Object dependency) {
    try {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, dependency);
    } catch (Exception e) {
        throw new RuntimeException("Failed to inject " + fieldName, e);
    }
}
```

### Domain-Specific Base Classes

Create a base class for each domain with test data builders:

```java
@Tag("unit")
@Tag("fast")
@Tag("demographic")
public abstract class DemographicUnitTestBase extends OpenOUnitTestBase {

    protected SecurityInfoManager mockSecurityInfoManager;
    protected LoggedInInfo mockLoggedInInfo;
    protected Facility mockFacility;

    protected static final Integer TEST_DEMO_NO = 12345;
    protected static final String TEST_PROVIDER = "999990";

    @BeforeEach
    void setUpDemographicMocks() {
        mockSecurityInfoManager = Mockito.mock(SecurityInfoManager.class);
        mockLoggedInInfo = Mockito.mock(LoggedInInfo.class);
        mockFacility = Mockito.mock(Facility.class);

        Mockito.lenient().when(mockLoggedInInfo.getCurrentFacility()).thenReturn(mockFacility);
        Mockito.lenient().when(mockFacility.isIntegratorEnabled()).thenReturn(false);

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
    }

    protected Demographic createTestDemographic() {
        Demographic demographic = new Demographic();
        demographic.setDemographicNo(TEST_DEMO_NO);
        demographic.setFirstName("John");
        demographic.setLastName("Doe");
        // ... set other fields
        return demographic;
    }
}
```

### @Nested Class Documentation

All @Nested test classes should have comprehensive JavaDoc:

```java
/**
 * Tests for demographic search functionality.
 *
 * <p>These tests cover various search operations including:</p>
 * <ul>
 *   <li>Search by name (partial matching)</li>
 *   <li>Search by health card number (HIN)</li>
 *   <li>Search by multiple attributes</li>
 * </ul>
 */
@Nested
@DisplayName("Search Operations")
@Tag("search")
class SearchOperationsTests {
    // tests...
}
```

## Unit Testing with SpringUtils

### Handling the SpringUtils Anti-Pattern

For unit tests that encounter SpringUtils.getBean() calls:

```java
// Note: TicklerUnitTestBase is in ca.openosp.openo.tickler package
public class TicklerManagerUnitTest extends TicklerUnitTestBase {

    @Mock
    private TicklerDao mockTicklerDao;

    @Mock
    private OscarLogDao mockOscarLogDao;

    private MockedStatic<LogAction> logActionMock;

    @BeforeEach
    void setUp() {
        // Register mocks for SpringUtils
        registerMock(TicklerDao.class, mockTicklerDao);

        // Handle static initialization dependencies
        registerMock(OscarLogDao.class, mockOscarLogDao);

        // Mock static classes that use SpringUtils
        logActionMock = mockStatic(LogAction.class);
    }

    @AfterEach
    void tearDown() {
        if (logActionMock != null) {
            logActionMock.close();
        }
    }
}
```

### Static Initialization Order

When mocking classes with static initializers that call SpringUtils:

1. Register dependency mocks first
2. Then create the static mock
3. Clean up in @AfterEach

```java
// CORRECT ORDER
registerMock(OscarLogDao.class, mockOscarLogDao);  // First
logActionMock = mockStatic(LogAction.class);       // Second

// WRONG ORDER - Will fail
logActionMock = mockStatic(LogAction.class);       // Fails - OscarLogDao not mocked
registerMock(OscarLogDao.class, mockOscarLogDao);
```

## Integration Testing Best Practices

### Database Testing with H2

```java
@Test
@Transactional
void should_handleConcurrentUpdates() {
    // Given
    Tickler tickler = createAndPersistTickler();

    // When - Simulate concurrent updates
    Tickler tickler1 = ticklerDao.find(tickler.getId());
    Tickler tickler2 = ticklerDao.find(tickler.getId());

    tickler1.setMessage("Updated by user 1");
    tickler2.setMessage("Updated by user 2");

    ticklerDao.merge(tickler1);
    entityManager.flush();

    ticklerDao.merge(tickler2);
    entityManager.flush();

    // Then - Last update wins
    Tickler result = ticklerDao.find(tickler.getId());
    assertThat(result.getMessage()).isEqualTo("Updated by user 2");
}
```

### Testing with Spring Context

```java
@Test
void should_autowireAllRequiredBeans() {
    // Verify critical beans are available
    assertThat(SpringUtils.getBean(TicklerDao.class)).isNotNull();
    assertThat(SpringUtils.getBean(SecurityInfoManager.class)).isNotNull();
    assertThat(SpringUtils.getBean(EntityManager.class)).isNotNull();
}
```

## Performance Testing

### Marking Slow Tests

```java
@Test
@Tag("slow")
@DisplayName("should handle large dataset efficiently")
void should_processLargeDataset() {
    // Test with 10,000+ records
    List<Tickler> ticklers = createMultipleTicklers(10000);

    long startTime = System.currentTimeMillis();
    List<Tickler> results = ticklerDao.findActiveByDemographicNo(1001);
    long duration = System.currentTimeMillis() - startTime;

    assertThat(duration).isLessThan(1000); // Should complete within 1 second
}
```

## Test Documentation

### Class-Level Documentation

```java
/**
 * Integration tests for TicklerDao implementation.
 *
 * <p>These tests verify the data access layer functionality for Tickler entities,
 * including CRUD operations, complex queries, and aggregation functions.</p>
 *
 * <p><b>Test Coverage:</b></p>
 * <ul>
 *   <li>Basic CRUD operations (Create, Read, Update, Delete)</li>
 *   <li>Complex search queries with multiple criteria</li>
 *   <li>Aggregation and counting operations</li>
 *   <li>Pagination and filtering</li>
 * </ul>
 *
 * @since 2025-01-17
 * @see TicklerDao
 * @see Tickler
 */
@DisplayName("Tickler DAO Integration Tests")
@Tag("integration")
@Tag("dao")
public class TicklerDaoIntegrationTest extends OpenODaoTestBase {
```

### Method-Level Documentation

For complex test logic, add explanatory comments:

```java
@Test
void should_handleComplexFilterCriteria() {
    // Given - Create ticklers with various states
    createTicklerWithStatus("ACTIVE");
    createTicklerWithStatus("COMPLETED");
    createTicklerWithStatus("DELETED");

    // When - Apply filter for active ticklers only
    CustomFilter filter = new CustomFilter();
    filter.setStatus("ACTIVE");
    List<Tickler> results = ticklerDao.getTicklers(filter);

    // Then - Only active ticklers returned
    assertThat(results)
        .hasSize(1)
        .allMatch(t -> "ACTIVE".equals(t.getStatus()));
}
```

## Common Pitfalls to Avoid

### ❌ Don't Test Implementation Details

```java
// BAD - Testing internal implementation
@Test
void should_callSpecificInternalMethod() {
    verify(mockDao, times(1)).internalHelperMethod();
}

// GOOD - Testing behavior
@Test
void should_returnActiveTicklers() {
    List<Tickler> results = dao.findActive();
    assertThat(results).allMatch(t -> "ACTIVE".equals(t.getStatus()));
}
```

### ❌ Don't Create Overly Complex Tests

```java
// BAD - Too many things tested at once
@Test
void testEverything() {
    // Tests create, update, delete, search all in one
}

// GOOD - Focused tests
@Test
void should_createTickler() { }

@Test
void should_updateTickler() { }

@Test
void should_deleteTickler() { }
```

### ❌ Don't Use Random/Time-Dependent Data

```java
// BAD - Non-deterministic
tickler.setServiceDate(new Date()); // Current time changes

// GOOD - Deterministic
tickler.setServiceDate(parseDate("2025-01-17"));
```

## Checklist for New Tests

Before committing a test, ensure:

- [ ] Test method name follows BDD pattern
- [ ] @DisplayName provides clear description
- [ ] Appropriate @Tag annotations applied
- [ ] Test is in correct package/directory
- [ ] Uses appropriate base class
- [ ] Follows Given-When-Then structure
- [ ] Uses AssertJ assertions
- [ ] No hardcoded values (use constants/helpers)
- [ ] Test is repeatable and deterministic
- [ ] Test data is cleaned up (via @Transactional or explicit cleanup)
- [ ] Test passes consistently
- [ ] Test fails when expected behavior is broken

## Examples from Actual Implementation

### Complete Test Example

```java
@Test
@Tag("read")
@Tag("filter")
@DisplayName("should return only active ticklers when multiple statuses exist")
void should_returnOnlyActiveTicklers_when_searchingByDemographic() {
    // Given - Create ticklers with different statuses
    Tickler activeTickler = createTicklerWithStatus("ACTIVE", demographicNo);
    Tickler completedTickler = createTicklerWithStatus("COMPLETED", demographicNo);
    Tickler deletedTickler = createTicklerWithStatus("DELETED", demographicNo);

    // When - Search for active ticklers only
    List<Tickler> results = ticklerDao.findActiveByDemographicNo(demographicNo);

    // Then - Only active tickler is returned
    assertThat(results).hasSize(1);
    assertThat(results.get(0).getId()).isEqualTo(activeTickler.getId());
    assertThat(results.get(0).getStatus()).isEqualTo(Tickler.STATUS_ACTIVE);
}
```

This example demonstrates:
- Clear BDD naming
- Proper tagging
- Given-When-Then structure
- Comprehensive assertions
- Test isolation
- Focused testing of one behavior