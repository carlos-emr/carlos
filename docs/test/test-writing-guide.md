# OpenO EMR Test Writing Guide

This guide contains detailed patterns and configurations for writing tests in the OpenO EMR codebase. For a quick reference, see the summary in [CLAUDE.md](/CLAUDE.md#modern-test-framework-junit-5).

## Critical Dependency Resolution Patterns

The OpenO codebase has several legacy patterns that require specific handling in tests. Understanding these patterns is essential for writing working tests.

### SpringUtils Anti-Pattern Resolution

The codebase uses static `SpringUtils.getBean()` throughout. Modern tests handle this via reflection in `OpenOTestBase`:

```java
@BeforeEach
void setUpSpringUtils() throws Exception {
    // CRITICAL: Field is "beanFactory" not "applicationContext"
    Field contextField = SpringUtils.class.getDeclaredField("beanFactory");
    contextField.setAccessible(true);
    contextField.set(null, applicationContext);
}
```

**Why this matters**: Without this setup, any code that calls `SpringUtils.getBean()` will fail because the static field won't be initialized with your test's Spring context.

### Mixed Hibernate/JPA Configuration

**Challenge**: The legacy codebase uses both `.hbm.xml` files AND `@Entity` annotations for different entities.

**Solution**: Dual configuration in test context - configure both Hibernate SessionFactory and JPA EntityManagerFactory:

```xml
<!-- Hibernate for XML mappings -->
<bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
    <property name="mappingResources">
        <list>
            <value>ca/openosp/openo/commn/model/Provider.hbm.xml</value>
            <value>ca/openosp/openo/commn/model/Demographic.hbm.xml</value>
        </list>
    </property>
</bean>

<!-- JPA for annotations -->
<bean id="entityManagerFactory" class="org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean">
    <property name="persistenceUnitName" value="testPersistenceUnit" />
</bean>
```

### Manual Bean Definitions Required

**Issue**: DAOs use SpringUtils in constructors causing circular dependencies during component scanning.

**Solution**: Define beans manually in test context, not via component scanning:

```xml
<!-- Manual DAO definitions to avoid SpringUtils initialization issues -->
<bean id="ticklerDao" class="ca.openosp.openo.commn.dao.TicklerDaoImpl" autowire="byName" />
<bean id="oscarLogDao" class="ca.openosp.openo.commn.dao.OscarLogDaoImpl" autowire="byName" />
```

**When to add new beans**: If you're testing a new DAO that hasn't been tested before, you'll need to add its bean definition to the test context XML file.

### Entity Discovery Pattern

**Problem**: Entities discovered at runtime via scanning cause missing dependencies (e.g., `lst_gender` table) because the test database doesn't have all tables.

**Solution**: Explicitly list only the entities you need in `persistence.xml`, disable scanning:

```xml
<persistence-unit name="testPersistenceUnit">
    <class>ca.openosp.openo.commn.model.Tickler</class>
    <class>ca.openosp.openo.commn.model.OscarLog</class>
    <!-- Explicitly list each entity -->
    <exclude-unlisted-classes>true</exclude-unlisted-classes>
</persistence-unit>
```

**When to add new entities**: If your test needs an entity that isn't listed, add it to the persistence unit. Be prepared to also add any dependent entities and their corresponding database tables to the H2 schema.

### Security Mock Pattern

**Issue**: All operations in the codebase require SecurityInfoManager privilege checks, which will fail in tests without proper setup.

**Solution**: Use MockSecurityInfoManager that always returns true for privilege checks:

```xml
<bean id="securityInfoManager" class="ca.openosp.openo.test.mocks.MockSecurityInfoManager" />
```

---

## Test Development Workflow

When writing tests, follow this workflow to ensure you're testing actual methods that exist in the codebase.

### Step 1: Examine the Actual Interface

**CRITICAL**: Before writing any test, read the DAO/Manager interface to see what methods actually exist:

```java
// First, check the actual DAO interface:
// src/main/java/ca/openosp/openo/commn/dao/TicklerDao.java
public interface TicklerDao extends AbstractDao<Tickler> {
    public Tickler find(Integer id);  // <-- Real method to test
    public List<Tickler> findActiveByDemographicNo(Integer demoNo); // <-- Real method
    // ... other actual methods
}
```

### Step 2: Write BDD-Style Tests for ACTUAL Methods

```java
@Test
@DisplayName("should return tickler when valid ID is provided")
void shouldReturnTickler_whenValidIdProvided() {
    // Given
    Tickler saved = createAndSaveTickler();

    // When
    Tickler found = ticklerDao.find(saved.getId()); // Testing real method

    // Then
    assertThat(found).isNotNull();
    assertThat(found).isEqualTo(saved);
}
```

### Step 3: Add Negative Test Cases

Always include tests for edge cases and error conditions:
- Null inputs
- Invalid IDs
- Empty results
- Boundary conditions

---

## BDD Test Naming Convention (Detailed)

### Method Naming Patterns

1. **`should<Action>_when<Condition>`** - Testing behavior/requirements
   - Use camelCase
   - Exactly ONE underscore separating action from condition
   - Example: `shouldReturnTickler_whenValidIdProvided()`

2. **`<methodName>_<scenario>_<expectedOutcome>`** - Testing specific methods
   - Example: `findActiveByDemographicNo_multipleStatuses_returnsOnlyActive()`

3. **`should<ExpectedBehavior>`** - Simple assertions without conditions
   - Example: `shouldLoadSpringContext()`

### @DisplayName Convention

- Always starts with lowercase "should"
- Natural language description
- Example: `@DisplayName("should return tickler when valid ID is provided")`

### What NOT to Do

- NO "test" prefix (e.g., ~~`testFindTickler()`~~)
- NO test numbers (e.g., ~~`test1_findTickler()`~~)
- NO multiple underscores (e.g., ~~`should_return_tickler_when_valid()`~~)

### BDD Test Structure Quick Reference

```java
// ✅ CORRECT BDD Test Structure
@Test
@DisplayName("should perform expected behavior when condition is met")  // lowercase "should"
void shouldPerformExpectedBehavior_whenConditionIsMet() {  // camelCase with ONE underscore
    // Given - set up test data
    TestData data = createTestData();

    // When - perform the action being tested
    Result result = systemUnderTest.performAction(data);

    // Then - verify the expected outcome
    assertThat(result).isNotNull();
    assertThat(result.getValue()).isEqualTo(expected);
}
```

---

## Test Base Class

All modern tests should extend `OpenOTestBase`:

```java
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = {"classpath:applicationContext-test.xml"})
@Transactional
public abstract class OpenOTestBase {

    @Autowired
    protected ApplicationContext applicationContext;

    @PersistenceContext(unitName = "testPersistenceUnit")
    protected EntityManager entityManager;

    @BeforeEach
    void setUpSpringUtils() throws Exception {
        Field contextField = SpringUtils.class.getDeclaredField("beanFactory");
        contextField.setAccessible(true);
        contextField.set(null, applicationContext);
    }
}
```

### Why Extend OpenOTestBase?

1. **SpringUtils handling**: Automatically sets up the static SpringUtils field
2. **Spring context**: Provides autowired ApplicationContext
3. **EntityManager**: Provides properly configured EntityManager
4. **Transaction support**: Tests are transactional and roll back automatically

---

## Test Tagging System

### Required Tags

Every test class should have at minimum:
- Test type: `@Tag("integration")` or `@Tag("unit")`
- Layer: `@Tag("dao")`, `@Tag("service")`, `@Tag("controller")`

### CRUD Operation Tags

- `@Tag("create")` - Tests that create/persist entities
- `@Tag("read")` - Tests that query/find entities
- `@Tag("update")` - Tests that modify existing entities
- `@Tag("delete")` - Tests that remove entities

### Extended Tags

- `@Tag("query")` - Complex query tests
- `@Tag("search")` - Search functionality tests
- `@Tag("filter")` - Filtering tests
- `@Tag("aggregate")` - Aggregation/count tests

### Example Test Class with Tags

```java
@Tag("integration")
@Tag("dao")
@Tag("tickler")
class TicklerDaoFindIntegrationTest extends OpenOTestBase {

    @Autowired
    private TicklerDao ticklerDao;

    @Test
    @Tag("read")
    @DisplayName("should return tickler when valid ID is provided")
    void shouldReturnTickler_whenValidIdProvided() {
        // test implementation
    }

    @Test
    @Tag("read")
    @Tag("filter")
    @DisplayName("should return only active ticklers for demographic")
    void shouldReturnOnlyActiveTicklers_whenFilteringByDemographic() {
        // test implementation
    }
}
```

---

## Common Issues and Solutions

### "Table not found" Errors

**Cause**: Entity references a table not in the H2 test schema.

**Solution**: Either:
1. Add the table to the H2 schema initialization script
2. Or exclude the entity from the persistence unit if not needed for your test

### "Bean not found" Errors

**Cause**: DAO or service not defined in test context.

**Solution**: Add manual bean definition to test context XML.

### "SpringUtils returned null" Errors

**Cause**: Test not extending OpenOTestBase, or @BeforeEach not running.

**Solution**: Ensure your test class extends OpenOTestBase.

### Tests Pass Individually but Fail Together

**Cause**: Shared state between tests, often from SpringUtils static field.

**Solution**: Ensure OpenOTestBase's @BeforeEach runs for every test. Consider test isolation.

---

## File Locations

- **Modern tests**: `src/test-modern/java/ca/openosp/openo/`
- **Test resources**: `src/test-modern/resources/`
- **Test context**: `src/test-modern/resources/applicationContext-test.xml`
- **H2 schema**: `src/test-modern/resources/schema.sql`
- **Persistence config**: `src/test-modern/resources/META-INF/persistence.xml`

---

## Related Documentation

- [Modern Test Framework Complete Guide](modern-test-framework-complete.md)
- [CLAUDE.md Test Section](/CLAUDE.md#modern-test-framework-junit-5)
