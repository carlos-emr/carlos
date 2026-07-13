---
description: Generate modern JUnit 5 tests for DAOs and Managers with infrastructure expansion
allowed-tools: Read, Write, Edit, Glob, Grep, Bash, Task
---

# /generate-tests - CARLOS EMR Test Generation Skill

Generate comprehensive JUnit 5 tests for DAO or Manager classes, including test infrastructure expansion when testing entities for the first time.

**Complements**: The `inject-test-context.py` hook auto-injects `docs/test/claude-test-context.md` when editing test files. This skill provides the full workflow including infrastructure changes.

---

## 1. Critical Pre-Generation Checklist

Before writing ANY test code:

1. **Read the actual interface/class** - Extract real method signatures. NEVER invent methods.
2. **Check if the entity is already in test infrastructure** - Look in `persistence.xml` and `test-context-full.xml`.
3. **Determine DAO type** - Is it `AbstractDaoImpl` (JPA/EntityManager) or `HibernateDaoSupport` (SessionFactory)?
4. **Identify entity mapping type** - Is it `@Entity` annotation or `.hbm.xml` file?

```
# Quick checks to run:
# 1. Read the DAO interface
Read: src/main/java/io/github/carlos_emr/carlos/commn/dao/<Name>Dao.java

# 2. Check if entity is in persistence.xml
Grep: "<Name>" in src/test-modern/resources/META-INF/persistence.xml

# 3. Check if DAO bean exists in test context
Grep: "<Name>" in src/test-modern/resources/test-context-full.xml

# 4. Determine DAO type
Grep: "extends AbstractDaoImpl\|extends HibernateDaoSupport" in <DaoImpl>.java

# 5. Determine entity mapping
Grep: "@Entity" in <Model>.java
Glob: **/<ModelName>.hbm.xml
```

---

## 2. Decision Framework

### Integration vs Unit Test

| Choose Integration When | Choose Unit When |
|------------------------|------------------|
| Testing DAO layer directly | Testing Manager/Service business logic |
| Need real database queries | Dependencies can be mocked |
| Testing Hibernate mappings | Testing conditional logic |
| Testing query correctness | Testing security checks |
| Testing transaction behavior | Fast feedback needed |

### Base Class Selection

| Base Class | Use When | Provides |
|------------|----------|----------|
| `OpenOTestBase` | Integration tests with Spring + DB | SpringUtils, EntityManager, transactions |
| `OpenOUnitTestBase` | Unit tests with mocked SpringUtils | `registerMock()`, `springUtilsMock` |
| Domain-specific (e.g., `DemographicUnitTestBase`) | Domain unit tests | Test data builders + mocks |

### Single vs Multi-File

- **Single file with @Nested**: Under 50 tests, same component, shared setup
- **Multiple files**: Over 50 tests, different contexts (unit vs integration), different setup needs

---

## 3. Test Generation Workflow

### Step 1: Examine the Interface

Read the DAO/Manager interface and extract every public method signature:

```java
// Example: Read TicklerDao.java and list methods
public interface TicklerDao extends AbstractDao<Tickler> {
    Tickler find(Integer id);
    List<Tickler> findActiveByDemographicNo(Integer demoNo);
    // ... extract ALL methods
}
```

Record: method name, parameters, return type, any JavaDoc hints about behavior.

### Step 2: Check Test Infrastructure

Check if the entity and its dependencies are already registered:

**File: `src/test-modern/resources/META-INF/persistence.xml`**
- `<class>` entries for `@Entity` annotated classes
- `<mapping-file>` entries for `.hbm.xml` mapped classes

**File: `src/test-modern/resources/test-context-full.xml`**
- DAO bean definition (`<bean id="myDao" class="...Impl" autowire="byName" />`)
- `annotatedClasses` in sessionFactory bean (for `@Entity` classes used by HibernateDaoSupport DAOs)
- `mappingResources` in both entityManagerFactory and sessionFactory (for `.hbm.xml` entities)

**File: `src/test-modern/resources/test-lookup-tables.sql`**
- Lookup tables referenced by HBM formulas or entity relationships

If the entity is NOT registered, proceed to **Section 5: Infrastructure Expansion** before writing tests.

### Step 3: Generate Test Class Structure

#### Integration Test Template

```java
package io.github.carlos_emr.carlos.<domain>.dao;

import io.github.carlos_emr.carlos.test.base.OpenOTestBase;
import io.github.carlos_emr.carlos.commn.model.<Entity>;
import io.github.carlos_emr.carlos.commn.dao.<Entity>Dao;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link <Entity>Dao} implementation.
 *
 * <p>Tests verify data access operations against an H2 in-memory database
 * with the full Spring context.</p>
 *
 * @since <use git log date>
 * @see <Entity>Dao
 * @see <Entity>
 */
@DisplayName("<Entity> DAO Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("<domain>")
@Transactional
public class <Entity>DaoIntegrationTest extends OpenOTestBase {

    @Autowired
    private <Entity>Dao dao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    // Helper methods
    private <Entity> createTest<Entity>() {
        <Entity> entity = new <Entity>();
        // Set required fields with deterministic values
        return entity;
    }

    private <Entity> createAndPersist() {
        <Entity> entity = createTest<Entity>();
        entityManager.persist(entity);
        entityManager.flush();
        return entity;
    }
}
```

#### Unit Test Template (Manager)

```java
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.test.unit.OpenOUnitTestBase;
import io.github.carlos_emr.carlos.commn.dao.<Entity>Dao;
import io.github.carlos_emr.carlos.managers.<Entity>Manager;
import io.github.carlos_emr.carlos.managers.<Entity>ManagerImpl;
import io.github.carlos_emr.carlos.util.LogAction;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link <Entity>Manager} implementation.
 *
 * @since <use git log date>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("<Entity>Manager Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("manager")
public class <Entity>ManagerUnitTest extends OpenOUnitTestBase {

    @Mock private <Entity>Dao mockDao;
    private MockedStatic<LogAction> logActionMock;
    private <Entity>ManagerImpl manager;

    @BeforeEach
    void setUp() {
        // 1. FIRST: Register SpringUtils mocks
        registerMock(<Entity>Dao.class, mockDao);
        registerMock(OscarLogDao.class, createAndRegisterMock(OscarLogDao.class));

        // 2. SECOND: Mock static classes
        logActionMock = mockStatic(LogAction.class);

        // 3. Create manager and inject dependencies via reflection
        manager = new <Entity>ManagerImpl();
        injectDependency(manager, "dao", mockDao);
    }

    @AfterEach
    void tearDown() {
        if (logActionMock != null) logActionMock.close();
    }

    protected void injectDependency(Object target, String fieldName, Object dependency) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, dependency);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject " + fieldName, e);
        }
    }
}
```

### Step 4: Generate Test Methods

Use BDD naming: `shouldDoSomethingWhenCondition()` (pure camelCase, no underscores)

```java
@Test
@Tag("read")
@DisplayName("should return entity when valid ID is provided")
void shouldReturnEntityWhenValidIdProvided() {
    // Given
    Entity saved = createAndPersist();

    // When
    Entity found = dao.find(saved.getId());

    // Then
    assertThat(found).isNotNull();
    assertThat(found.getId()).isEqualTo(saved.getId());
}

@Test
@Tag("read")
@DisplayName("should return null when ID does not exist")
void shouldReturnNullWhenIdDoesNotExist() {
    // When
    Entity found = dao.find(-999);

    // Then
    assertThat(found).isNull();
}

@Test
@Tag("create")
@DisplayName("should persist new entity successfully")
void shouldPersistNewEntitySuccessfully() {
    // Given
    Entity entity = createTestEntity();

    // When
    dao.persist(entity);
    entityManager.flush();

    // Then
    assertThat(entity.getId()).isNotNull();
}
```

### Step 5: Apply Tags

Every test class needs at minimum:
- **Level 1 (Type)**: `@Tag("integration")` or `@Tag("unit")`
- **Level 2 (Layer)**: `@Tag("dao")`, `@Tag("manager")`, `@Tag("service")`
- **Level 3 (CRUD)**: `@Tag("create")`, `@Tag("read")`, `@Tag("update")`, `@Tag("delete")` on individual methods
- **Level 4 (Extended)**: `@Tag("query")`, `@Tag("search")`, `@Tag("filter")`, `@Tag("aggregate")` as needed

---

## 4. Hibernate/JPA Duality

The codebase has TWO DAO patterns that persist until Hibernate 6 migration:

### Type A: AbstractDaoImpl (JPA EntityManager)

```java
// Uses EntityManager internally, @Entity annotations on model
public class TicklerDaoImpl extends AbstractDaoImpl<Tickler> implements TicklerDao {
    // Uses this.entityManager for queries
}
```

**Test infrastructure needs**: Add `<class>` to persistence.xml, add DAO bean to test-context-full.xml

### Type B: HibernateDaoSupport (Hibernate SessionFactory)

```java
// Uses SessionFactory/HibernateTemplate, often .hbm.xml mappings
public class DemographicDaoImpl extends HibernateDaoSupport implements DemographicDao {
    // Uses getHibernateTemplate() or getSession() for queries
}
```

**Test infrastructure needs**: Add `.hbm.xml` to persistence.xml AND to both `entityManagerFactory.mappingResources` and `sessionFactory.mappingResources` in test-context-full.xml. If model uses `@Entity`, also add to `sessionFactory.annotatedClasses`. DAO bean needs explicit `<property name="sessionFactory" ref="sessionFactory" />`.

### How to Identify

```bash
# Check which pattern a DAO uses:
Grep: "extends AbstractDaoImpl" in <DaoImpl>.java    # Type A
Grep: "extends HibernateDaoSupport" in <DaoImpl>.java # Type B
```

---

## 5. Infrastructure Expansion Guide

When testing an entity for the FIRST TIME, you must register it in the test infrastructure.

### 5a. Add Entity to persistence.xml

**File**: `src/test-modern/resources/META-INF/persistence.xml`

For `@Entity` annotated classes:
```xml
<class>io.github.carlos_emr.carlos.commn.model.MyEntity</class>
```

For `.hbm.xml` mapped classes:
```xml
<mapping-file>io/github/carlos_emr/carlos/commn/model/MyEntity.hbm.xml</mapping-file>
```

### 5b. Add DAO Bean to test-context-full.xml

**File**: `src/test-modern/resources/test-context-full.xml`

For Type A (AbstractDaoImpl) DAOs:
```xml
<bean id="myEntityDao" class="io.github.carlos_emr.carlos.commn.dao.MyEntityDaoImpl" autowire="byName" />
```

For Type B (HibernateDaoSupport) DAOs:
```xml
<bean id="myEntityDao" class="io.github.carlos_emr.carlos.commn.dao.MyEntityDaoImpl" autowire="byName">
    <property name="sessionFactory" ref="sessionFactory" />
</bean>
```

### 5c. Add to sessionFactory annotatedClasses (if @Entity)

If the model uses `@Entity` AND any HibernateDaoSupport DAO needs it, add to the `sessionFactory` bean's `annotatedClasses` list:

```xml
<bean id="sessionFactory" ...>
    <property name="annotatedClasses">
        <list>
            <!-- existing entries -->
            <value>io.github.carlos_emr.carlos.commn.model.MyEntity</value>
        </list>
    </property>
</bean>
```

### 5d. Add HBM mappings to both factories (if .hbm.xml)

For `.hbm.xml` entities, add the mapping to BOTH:
- `entityManagerFactory.mappingResources`
- `sessionFactory.mappingResources`

### 5e. Add Lookup Table Data (if needed)

If the entity has HBM `<formula>` elements or foreign keys to lookup tables that don't have entity classes, add CREATE TABLE + seed data to:

**File**: `src/test-modern/resources/test-lookup-tables.sql`

```sql
CREATE TABLE IF NOT EXISTS my_lookup_table (
    code varchar(10) NOT NULL PRIMARY KEY,
    description varchar(100)
);

MERGE INTO my_lookup_table (code, description) KEY(code) VALUES
('A', 'Active'),
('I', 'Inactive');
```

### 5f. Verify Schema Creation

After infrastructure changes, run the tests to verify H2 creates all needed tables:
```bash
mvn test -Dtest=<YourNewTest> 2>&1 | grep -i "table.*not found\|unknown entity\|could not resolve"
```

---

## 6. Test Execution & Verification

### Running Tests

```bash
# Run specific test class
mvn test -Dtest=MyEntityDaoIntegrationTest

# Run all tests for a component
mvn test -Dtest=MyEntity*Test

# Run by tag
mvn test -Dgroups="integration,dao"

# Run all modern tests
make install --run-modern-tests

# Run only unit tests (fast)
make install --run-unit-tests

# Run all tests (modern + legacy)
make install --run-tests
```

### Verification Checklist

After generating tests, verify:
- [ ] All tests pass: `mvn test -Dtest=<TestClass>`
- [ ] Test class is `public` (Maven Surefire requirement)
- [ ] `@PersistenceContext(unitName = "entityManagerFactory")` used (not "testPersistenceUnit")
- [ ] BDD naming: `shouldDoSomething...()` with `@DisplayName`
- [ ] Tags present: type + layer minimum
- [ ] Given/When/Then structure with comments
- [ ] AssertJ assertions (not JUnit assertEquals)
- [ ] Deterministic test data (no `new Date()` or random values)
- [ ] Negative/edge cases included (null, empty, invalid)

---

## 7. Common Pitfalls & Solutions

| Pitfall | Symptom | Fix |
|---------|---------|-----|
| Non-public test class | Tests not discovered by Surefire | Add `public` modifier |
| Wrong persistence unit name | "No EntityManager" error | Use `unitName = "entityManagerFactory"` |
| Missing entity in persistence.xml | "Unknown entity" error | Add `<class>` or `<mapping-file>` entry |
| Missing DAO bean | "No qualifying bean" error | Add `<bean>` to test-context-full.xml |
| HibernateDaoSupport without sessionFactory | "No SessionFactory set" error | Add `<property name="sessionFactory" ref="sessionFactory" />` |
| Missing lookup table | "Table not found" error | Add CREATE TABLE to test-lookup-tables.sql |
| Static mock order wrong | NullPointerException in static init | Register SpringUtils mocks BEFORE `mockStatic()` |
| Static mocks not closed | Test pollution, flaky failures | Close in `@AfterEach` in reverse order |
| Testing invented methods | Compile error or wrong behavior | Read actual interface FIRST |
| Non-deterministic data | Flaky tests | Use fixed dates, sequential IDs |

---

## 8. Reference Documentation

For deep dives beyond this workflow:

- **Complete framework guide**: `docs/test/modern-test-framework-complete.md`
- **Test writing patterns**: `docs/test/test-writing-guide.md`
- **BDD naming & tagging**: `docs/test/test-writing-best-practices.md`
- **Auto-injected context**: `docs/test/claude-test-context.md`
- **Test infrastructure files**:
  - `src/test-modern/resources/META-INF/persistence.xml`
  - `src/test-modern/resources/test-context-full.xml`
  - `src/test-modern/resources/test-lookup-tables.sql`
  - `src/test-modern/resources/schema.sql`
- **Test base classes**:
  - `src/test-modern/java/io/github/carlos_emr/carlos/test/base/OpenOTestBase.java`
  - `src/test-modern/java/io/github/carlos_emr/carlos/test/unit/OpenOUnitTestBase.java`
