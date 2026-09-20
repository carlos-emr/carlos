# CARLOS EMR Modern Test Framework - Complete Documentation

## Executive Summary

CARLOS EMR has a single modern test suite built on JUnit Jupiter, living entirely under
`src/test/`. It was introduced alongside the legacy JUnit 4 tests in a parallel
`src/test-modern/` tree; that migration is finished — the JUnit 4 suite has been removed and
`src/test-modern/` was collapsed into `src/test/`. There is now one tree, one runner and one
surefire execution.

This document is the deep reference: base classes, Spring/Hibernate wiring, and the
non-obvious pitfalls of the dual persistence context. For the short version, see
[`test-writing-guide.md`](test-writing-guide.md).

## Table of Contents
1. [Architecture Overview](#architecture-overview)
2. [Directory Structure](#directory-structure)
3. [Maven Configuration](#maven-configuration)
4. [Core Components](#core-components)
5. [Writing Tests](#writing-tests)
6. [Running Tests](#running-tests)
7. [Proven Capabilities](#proven-capabilities)
8. [Current Implementation Status](#current-implementation-status)
9. [Troubleshooting](#troubleshooting)

## Architecture Overview

### Technology Stack
- **Test Framework**: JUnit 6 (Jupiter) 6.1.3
- **Assertions**: AssertJ 3.27.7 for fluent assertions
- **Mocking**: Mockito 5.23.0 (Java 25 compatible)
- **Database**: H2 in-memory database (MySQL mode)
- **Spring**: Spring Test with Spring 7.0.8
- **Transactions**: Full transaction support with rollback

### Design Principles
1. **Real Testing**: Tests actual implementations, not mocks
2. **Fast Execution**: In-memory database, sub-second test execution
3. **Modern Features**: Leverages JUnit Jupiter capabilities fully
4. **One Tree**: All tests live under `src/test/` — no parallel suite to keep in sync

## Directory Structure

```
workspace/
└── src/
    └── test/
        ├── java/
        │   └── io/github/carlos_emr/carlos/
        │       ├── test/
        │       │   ├── base/        # Base test classes
        │       │   │   ├── CarlosTestBase.java
        │       │   │   ├── CarlosDaoTestBase.java
        │       │   │   └── CarlosWebTestBase.java
        │       │   ├── unit/        # Unit test base classes
        │       │   │   └── CarlosUnitTestBase.java
        │       │   ├── builders/    # Test data builders
        │       │   ├── logging/     # LogCapture, for Log4j2 assertions
        │       │   ├── mocks/       # Mock implementations
        │       │   │   └── MockSecurityInfoManager.java
        │       │   ├── support/     # Shared test support
        │       │   ├── examples/    # Example tests
        │       │   └── simple/      # Framework validation tests
        │       ├── managers/        # Manager layer unit tests
        │       │   ├── DemographicUnitTestBase.java    # Base with test data builders
        │       │   └── DemographicManagerUnitTest.java # 117 tests, 18 @Nested classes
        │       └── <domain>/        # Per-domain tests, mirroring the main tree
        │           └── tickler/
        │               ├── dao/     # Multiple test files (integration + unit)
        │               ├── manager/
        │               │   └── TicklerManagerUnitTest.java
        │               └── TicklerUnitTestBase.java
        └── resources/
            ├── test-context-*.xml           # Spring contexts (full, complete, mock-security)
            ├── test-applicationContext*.xml # Narrower / legacy contexts
            ├── test.properties              # @TestPropertySource values
            └── log4j2.xml                   # Test logging config
```

Docs live beside this file:

```
docs/test/
├── README.md                          # Test documentation index
├── modern-test-framework-guide.md     # Main framework guide
├── test-writing-guide.md              # Patterns and static mocking
├── claude-test-context.md             # Context guide (auto-injected by hooks)
└── modern-test-framework-complete.md  # This file
```

## Maven Configuration

### Build Configuration (pom.xml)

There is one surefire execution over the single `src/test/java` tree — no
`build-helper` source injection and no second test source directory. The parts
that matter when a test misbehaves:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.5.5</version>
    <configuration>
        <!-- Each fork is a separate JVM with its own H2 mem:testdb, so forks
             are isolated and safe to run in parallel. Classes stay sequential
             WITHIN a fork: some Spring integration tests share context caches
             or fixed seed data. -->
        <forkCount>${test.forkCount}</forkCount>
        <reuseForks>true</reuseForks>
        <perCoreThreadCount>false</perCoreThreadCount>
        <includes>
            <include>**/test/**/*Test.java</include>
            <include>**/*IntegrationTest.java</include>
            <include>**/*UnitTest.java</include>
            <!-- ...plus per-slice and legacy-name includes; see pom.xml -->
        </includes>
        <argLine>
           @{argLine}                          <!-- carries the JaCoCo agent -->
           -Xmx2048m
           -Dnet.bytebuddy.experimental=true   <!-- Mockito on a new JDK -->
           --add-opens java.base/java.lang=ALL-UNNAMED
           --add-opens java.base/java.util=ALL-UNNAMED
           -Djdk.attach.allowAttachSelf=true
           -XX:+EnableDynamicAgentLoading
           -Djava.awt.headless=true
        </argLine>
        <groups>${groups}</groups>          <!-- -Dgroups="unit" etc. -->
    </configuration>
</plugin>
```

Note that the `<includes>` list is an allowlist, not a convention: a test class
whose name and package match none of those patterns is silently never run. Name
new tests `*UnitTest` / `*IntegrationTest`, or put them under a `test/` package.

## Core Components

### 1. CarlosTestBase - Foundation Class

**Location**: `src/test/java/io/github/carlos_emr/carlos/test/base/CarlosTestBase.java`

**Purpose**: Base class for all modern tests, handles Spring context and SpringUtils anti-pattern

**Key Features**:
```java
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = {"classpath:test-context-full.xml"})
@TestPropertySource(locations = "classpath:test.properties")
public abstract class CarlosTestBase {

    @BeforeEach
    void setUpSpringUtils() throws Exception {
        // Handles SpringUtils static bean factory injection
        Field contextField = SpringUtils.class.getDeclaredField("beanFactory");
        contextField.setAccessible(true);
        contextField.set(null, applicationContext);
    }
}
```

### 2. Spring Test Configuration

**Location**: `src/test/resources/test-context-full.xml`

**Key Configurations**:
- H2 in-memory database with MySQL compatibility mode
- Hibernate SessionFactory for XML mappings
- JPA EntityManagerFactory for annotations
- Manually defined beans (avoids circular dependencies)
- Mock SecurityInfoManager

```xml
<!-- H2 Database Configuration -->
<bean id="dataSource" class="org.apache.commons.dbcp2.BasicDataSource">
    <property name="driverClassName" value="org.h2.Driver" />
    <property name="url" value="jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1" />
    <property name="username" value="sa" />
    <property name="password" value="" />
</bean>

<!-- Mixed Hibernate/JPA Support -->
<bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
    <property name="mappingResources">
        <list>
            <value>io/github/carlos_emr/carlos/commn/model/Provider.hbm.xml</value>
            <value>io/github/carlos_emr/carlos/commn/model/Demographic.hbm.xml</value>
        </list>
    </property>
</bean>

<bean id="entityManagerFactory" class="org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean">
    <property name="persistenceUnitName" value="testPersistenceUnit" />
</bean>
```

### 3. MockSecurityInfoManager

**Location**: `src/test/java/io/github/carlos_emr/carlos/test/mocks/MockSecurityInfoManager.java`

**Purpose**: Bypasses security checks in test environment

```java
public class MockSecurityInfoManager implements SecurityInfoManager {
    @Override
    public boolean hasPrivilege(LoggedInInfo loggedInInfo, String objectName,
                                String privilege, int demographicNo) {
        return true; // Always grant access in tests
    }
}
```

### 4. JPA Persistence Configuration

There is **no test `persistence.xml`**. The test contexts build the
`EntityManagerFactory` in Spring instead, so the set of entities a test sees is
decided by `packagesToScan` in whichever context it loads — not by a
`<class>` list.

**Location**: `src/test/resources/test-context-full.xml` (the context
`CarlosTestBase` loads)

```xml
<bean id="entityManagerFactory" primary="true"
      class="org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean">
    <property name="dataSource" ref="dataSource" />
    <property name="persistenceProvider">
        <bean class="org.hibernate.jpa.HibernatePersistenceProvider" />
    </property>
    <!-- Annotated entities are discovered from here; there is no <class> list -->
    <property name="packagesToScan">
        <list><value>io.github.carlos_emr.carlos</value></list>
    </property>
    <property name="jpaVendorAdapter">
        <bean class="org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter">
            <property name="database" value="H2" />
            <property name="generateDdl" value="true" />
        </bean>
    </property>
</bean>
```

Narrower contexts (`test-context-complete.xml`,
`test-applicationContext-working.xml`) scan a shortlist of model packages and
name the unit `testPersistenceUnit` — which is the name to use when injecting
the `EntityManager`:

```java
@PersistenceContext(unitName = "testPersistenceUnit")
private EntityManager entityManager;
```

An entity in a package the loaded context does not scan fails at runtime as
"Unknown entity", not at startup. If a test sees that, widen `packagesToScan`
in the context it uses rather than adding a mapping file.

## Writing Tests

### Test Class Structure

```java
package io.github.carlos_emr.carlos.tickler.dao;

import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Descriptive Test Suite Name")
@Transactional
class ExampleDaoTest extends CarlosTestBase {

    @Autowired
    private TicklerDao ticklerDao;

    @PersistenceContext(unitName = "testPersistenceUnit")
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        // Test data setup
    }

    @Test
    @DisplayName("Should perform specific action")
    void testSpecificAction() {
        // Given
        Tickler tickler = createTestTickler();

        // When
        ticklerDao.persist(tickler);

        // Then
        assertThat(tickler.getId()).isNotNull();
    }
}
```

### Best Practices

1. **Use @DisplayName** for readable test descriptions
2. **Follow Given-When-Then** pattern
3. **Use AssertJ** for fluent assertions
4. **Leverage @Transactional** for automatic rollback
5. **Create helper methods** for test data
6. **Test real implementations**, not mocks

### JUnit Jupiter Features Available

- **Parameterized Tests**: `@ParameterizedTest`, `@ValueSource`
- **Nested Tests**: `@Nested` for grouping related tests
- **Conditional Execution**: `@EnabledIf`, `@DisabledOnOs`
- **Repeated Tests**: `@RepeatedTest`
- **Dynamic Tests**: `@TestFactory`
- **Better Assertions**: `assertAll()`, `assertThrows()`

## Running Tests

### Command Line Execution

```bash
# Run all tests (modern + original)
mvn test

# Run only modern tests (skip legacy)
mvn test -DskipTests=false -DskipModernTests=false -Dtest="**/*Test,**/*Tests"

# Run specific modern test class
mvn test -Dtest=TicklerDaoMethodTest

# Run specific test method
mvn test -Dtest=TicklerDaoMethodTest#testFindById

# Skip modern tests
mvn test -DskipModernTests=true

# Using make script (includes modern tests by default)
make install --run-tests
```

### IDE Integration

- **IntelliJ IDEA**: Full JUnit Jupiter support out of the box
- **Eclipse**: Requires the JUnit Platform launcher
- **VS Code**: Java Test Runner extension supports JUnit Jupiter

### Test Reports

- **Surefire**: `target/surefire-reports/`
- **Coverage Reports**: JaCoCo (surefire's `@{argLine}` carries the agent)

## Proven Capabilities

### Successfully Demonstrated Features

Exercised across the suite:

#### ✅ Database Operations
- CRUD operations (Create, Read, Update, Delete)
- Complex queries with multiple criteria
- Pagination support
- Count operations
- Date range queries

#### ✅ Spring Integration
- Dependency injection with `@Autowired`
- Transaction management
- Multiple bean definitions
- Context loading without errors

#### ✅ Hibernate/JPA Features
- Mixed XML and annotation mappings
- Entity relationships (OneToMany, ManyToOne)
- Lazy/Eager fetching
- Custom queries
- Entity lifecycle management

#### ✅ Test Framework Features
- Fast execution (< 4 seconds for all tests)
- Isolated test transactions
- Reproducible results
- Clear failure messages
- Parallel test capability

### Performance Metrics

From actual test execution:
- **Setup Time**: ~2 seconds for Spring context
- **Test Execution**: < 300ms per test average
- **Memory Usage**: 2048MB heap per fork (`-Xmx2048m` in the surefire `argLine`)

Wall-clock time scales with `test.forkCount`; run the suite to get a current figure rather
than trusting a number recorded here.

## Current Implementation Status

- ✅ Single JUnit Jupiter suite under `src/test/`; the legacy JUnit 4 suite is gone
- ✅ Full Java 25 compatibility with the ByteBuddy experimental flag
- ✅ Manager unit test patterns proven with 117-test DemographicManagerUnitTest
- ✅ Domain-specific base classes demonstrated (DemographicUnitTestBase, TicklerUnitTestBase)

## Required Configurations for CARLOS

### SpringUtils Configuration

The codebase uses `SpringUtils.getBean()` static calls. Tests must configure this properly:
```java
@BeforeEach
void setUpSpringUtils() throws Exception {
    // CRITICAL: Field is "beanFactory" not "applicationContext"
    Field contextField = SpringUtils.class.getDeclaredField("beanFactory");
    contextField.setAccessible(true);
    contextField.set(null, applicationContext);
}
```

### Mixed Hibernate/JPA Configuration

The codebase uses both Hibernate XML mappings (`.hbm.xml` files) and JPA annotations (`@Entity` classes). Tests require dual configuration:
```xml
<!-- SessionFactory for XML mappings -->
<bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
    <property name="mappingResources">
        <list>
            <value>io/github/carlos_emr/carlos/commn/model/Provider.hbm.xml</value>
            <value>io/github/carlos_emr/carlos/commn/model/Demographic.hbm.xml</value>
        </list>
    </property>
</bean>

<!-- EntityManagerFactory for JPA -->
<bean id="entityManagerFactory" class="org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean">
    <property name="persistenceUnitName" value="testPersistenceUnit" />
</bean>
```

### Circular Dependency Prevention

**Problem**: DAOs initialize SpringUtils in static blocks, causing circular dependencies during component scanning.

**Solution**: Manual bean definitions instead of component scanning:
```xml
<!-- Define each DAO manually -->
<bean id="ticklerDao" class="io.github.carlos_emr.carlos.commn.dao.TicklerDaoImpl" autowire="byName" />
<bean id="oscarLogDao" class="io.github.carlos_emr.carlos.commn.dao.OscarLogDaoImpl" autowire="byName" />
<!-- Add more as needed -->
```

### Entity Discovery Issues

**Problem**: Automatic entity scanning finds entities with dependencies on non-existent tables (e.g., `lst_gender`).

**Solution**: Explicit entity listing in `persistence.xml`:
```xml
<persistence-unit name="testPersistenceUnit">
    <!-- List each entity explicitly -->
    <class>io.github.carlos_emr.carlos.commn.model.Tickler</class>
    <class>io.github.carlos_emr.carlos.commn.model.TicklerComment</class>
    <class>io.github.carlos_emr.carlos.commn.model.OscarLog</class>
    <!-- Prevent scanning -->
    <exclude-unlisted-classes>true</exclude-unlisted-classes>
</persistence-unit>
```

### Security Configuration for Testing

All operations require `SecurityInfoManager.hasPrivilege()` checks. Tests use a mock implementation that always grants access:
```java
public class MockSecurityInfoManager implements SecurityInfoManager {
    @Override
    public boolean hasPrivilege(LoggedInInfo loggedInInfo,
                                String objectName, String privilege,
                                int demographicNo) {
        return true; // Always grant in tests
    }
}
```

## Troubleshooting

### Common Issues and Solutions

#### Issue: "No qualifying bean of type 'EntityManagerFactory'"
**Solution**: Add `@PersistenceContext(unitName = "testPersistenceUnit")`

#### Issue: Missing table errors (e.g., "lst_gender")
**Cause**: Eager fetching triggers lookup table access
**Solution**:
- Create missing tables in test schema
- Use lazy fetching
- Mock problematic relationships
- Add entity to exclude list if not needed

#### Issue: SpringUtils not initialized
**Solution**:
- Ensure test extends `CarlosTestBase`
- Check that field name is `beanFactory` not `applicationContext`

#### Issue: "Unknown entity" errors
**Solution**:
- Add entity class to persistence.xml
- Check if related DAO needs manual bean definition
- Verify entity package name matches new structure

#### Issue: Security check failures
**Solution**: Verify `MockSecurityInfoManager` is configured in test context

#### Issue: Transaction not rolling back
**Solution**: Add `@Transactional` to test class

### Debug Tips

1. **Enable SQL logging**: Set `hibernate.show_sql=true`
2. **Check Spring context**: Add `@Test void contextLoads() {}`
3. **Verify test data**: Use `entityManager.flush()` after persist
4. **Clear cache**: Use `entityManager.clear()` before assertions

## Advanced Features

### Custom Test Configurations

Create specialized contexts for different test scenarios:

```xml
<!-- test-context-minimal.xml -->
<beans>
    <!-- Minimal beans for unit tests -->
</beans>

<!-- test-context-integration.xml -->
<beans>
    <!-- Full integration test setup -->
</beans>
```

### Test Data Builders

```java
public class TicklerTestDataBuilder {
    private Tickler tickler = new Tickler();

    public TicklerTestDataBuilder withDemographic(Integer id) {
        tickler.setDemographicNo(id);
        return this;
    }

    public Tickler build() {
        return tickler;
    }
}
```

### Performance Testing

```java
@Test
@Timeout(value = 2, unit = TimeUnit.SECONDS)
void performanceTest() {
    // Test must complete within 2 seconds
}
```

## Current Capabilities

The modern test framework is fully operational with:

- ✅ JUnit 6 with Java 25 support
- ✅ Better test organization with @Nested and @DisplayName
- ✅ AssertJ fluent assertions
- ✅ Fast execution with H2 in-memory database
- ✅ Handles complex domain objects and database operations
- ✅ Full Spring dependency injection support
- ✅ Transaction management with automatic rollback
- ✅ Mixed Hibernate XML and JPA annotation support

New tests belong in `src/test/`, using JUnit Jupiter and the established patterns.

## References

- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/) (Jupiter programming model; still the reference for JUnit 6)
- [AssertJ Documentation](https://assertj.github.io/doc/)
- [Spring Test Documentation](https://docs.spring.io/spring-framework/reference/testing.html)
- [H2 Database Documentation](https://www.h2database.com/html/main.html)

---

*Last Updated: September 2026*
*Version: 1.2*
*Status: Production Ready*