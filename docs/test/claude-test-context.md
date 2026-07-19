# Modern Test Framework - Claude Context

> This file is automatically injected by hooks when working on test files.
> For complete documentation, see test-writing-best-practices.md.

===============================================================================
CRITICAL: Before writing tests, READ the actual DAO/Manager interface!
Never invent methods - verify they exist in the codebase first.
===============================================================================

## BDD NAMING CONVENTION

### Method Naming (Choose ONE style consistently)

**Option 1: Pure camelCase (RECOMMENDED for Java)**
   - Pattern: `should<ExpectedBehavior>When<Condition>()`
   - Examples:
     - `shouldReturnTicklerWhenValidIdProvided()`
     - `shouldThrowExceptionWhenTicklerNotFound()`
     - `shouldLoadSpringContext()`  (no condition needed)

**Option 2: Snake_case (common in Ruby/Python BDD, valid for Java)**
   - Pattern: `should_<expected_behavior>_when_<condition>()`
   - Examples:
     - `should_return_tickler_when_valid_id_provided()`
     - `should_throw_exception_when_tickler_not_found()`
     - `should_load_spring_context()`

**AVOID**: Mixed camelCase with underscores (e.g., `shouldReturnTickler_whenValid`)
**AVOID**: Traditional test naming (e.g., `testFindById()`, `findById_validId_returnsTickler()`)

### @DisplayName (describes behavior from user perspective)
   @DisplayName("should return tickler when valid ID is provided")
   @DisplayName("should throw exception when tickler not found")
   @DisplayName("should load Spring context successfully")

===============================================================================

## REQUIRED STRUCTURE

```java
@Tag("integration")  // Test type: integration, unit
@Tag("dao")          // Layer: dao, manager, service, controller
@Tag("read")         // CRUD: create, read, update, delete
@DisplayName("Component Tests")
public class ComponentTest extends OpenOTestBase {  // MUST be public!

    @PersistenceContext(unitName = "testPersistenceUnit")
    private EntityManager entityManager;

    @Test
    @DisplayName("should perform action when condition is met")
    void shouldPerformActionWhenConditionMet() {  // Pure camelCase
        // Given - set up test data
        Entity entity = createTestEntity();

        // When - perform the action
        Result result = componentUnderTest.doSomething(entity);

        // Then - verify outcome with AssertJ
        assertThat(result).isNotNull();
        assertThat(result.getValue()).isEqualTo(expected);
    }
}
```

===============================================================================

## TAG HIERARCHY

Level 1 (Test Type):   @Tag("integration"), @Tag("unit"), @Tag("slow"), @Tag("fast")
Level 2 (Layer):       @Tag("dao"), @Tag("manager"), @Tag("service"), @Tag("controller")
Level 3 (CRUD):        @Tag("create"), @Tag("read"), @Tag("update"), @Tag("delete")
Level 4 (Extended):    @Tag("query"), @Tag("search"), @Tag("filter"), @Tag("aggregate")

===============================================================================

## TEST DATA MANAGEMENT

```java
protected Entity createTestEntity() {
    Entity entity = new Entity();
    entity.setField("deterministic value");  // NOT new Date() or random
    return entity;
}

protected Entity createAndPersist() {
    Entity entity = createTestEntity();
    entityManager.persist(entity);
    entityManager.flush();
    return entity;
}
```

===============================================================================

## ASSERTIONS (Use AssertJ)

```java
// Good - fluent AssertJ
assertThat(result).isNotNull();
assertThat(result.getStatus()).isEqualTo("ACTIVE");
assertThat(resultList)
    .hasSize(3)
    .extracting(Entity::getStatus)
    .containsOnly("ACTIVE");

// Avoid - JUnit basic assertions
assertNotNull(result);  // Less readable
assertEquals("ACTIVE", result.getStatus());  // Argument order confusion
```

===============================================================================

## COMMON PITFALLS

- Non-public test class      -> Maven Surefire won't discover it
- Testing invented methods   -> Read actual interface first!
- Random/time-dependent data -> Use deterministic values
- Testing implementation     -> Test behavior, not internal methods
- Too many things in one test -> One behavior per test

===============================================================================

## CHECKLIST BEFORE COMMITTING

[ ] Method name follows BDD pattern (pure camelCase or snake_case, consistently)
[ ] @DisplayName describes behavior from user perspective
[ ] Appropriate @Tag annotations (min: type + layer)
[ ] Extends OpenOTestBase (or appropriate base)
[ ] Given-When-Then structure with comments
[ ] Uses AssertJ assertions
[ ] Deterministic test data (no random/time values)
[ ] Test passes consistently
[ ] Test fails when expected behavior is broken

===============================================================================

## UNIT TESTING WITH MOCKS (SpringUtils Anti-Pattern)

```java
public class ManagerUnitTest extends OpenOUnitTestBase {  // Note: OpenOUnitTestBase for unit tests
    @Mock private SomeDao mockDao;
    private MockedStatic<LogAction> logActionMock;

    @BeforeEach
    void setUp() {
        registerMock(SomeDao.class, mockDao);           // 1. Register dependency mocks first
        logActionMock = mockStatic(LogAction.class);     // 2. Then create static mocks
    }

    @AfterEach
    void tearDown() {
        if (logActionMock != null) logActionMock.close();
    }
}
```

===============================================================================

Full documentation: docs/test/test-writing-best-practices.md
