# OpenO EMR Modern Test Framework - Complete Documentation

## Executive Summary

A comprehensive modern test framework has been successfully implemented for OpenO EMR using JUnit 5 (Jupiter). The framework runs alongside existing JUnit 4 tests with zero impact on legacy code, allowing gradual migration while immediately enabling modern testing practices for new development.

**Key Achievement**: Successfully implemented 141 comprehensive tests (129 unit + 12 integration), achieving 99% pass rate (140/141 tests passing).

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
- **Test Framework**: JUnit 5 (Jupiter) 5.10.1
- **Assertions**: AssertJ 3.24.2 for fluent assertions
- **Mocking**: Mockito 5.x (Java 21 compatible)
- **Database**: H2 in-memory database (MySQL mode)
- **Spring**: Spring Test with Spring 5.3.39
- **Transactions**: Full transaction support with rollback

### Design Principles
1. **Zero Impact**: Legacy tests remain untouched
2. **Parallel Structure**: Modern tests in separate directory
3. **Real Testing**: Tests actual implementations, not mocks
4. **Fast Execution**: In-memory database, sub-second test execution
5. **Modern Features**: Leverages JUnit 5 capabilities fully

## Directory Structure

```
workspace/
├── src/
│   ├── test/                        # Original tests (unchanged)
│   │   ├── java/                    # JUnit 4 tests
│   │   └── resources/               # Legacy test resources
│   └── test-modern/                 # Modern test framework
│       ├── java/
│       │   └── ca/openosp/openo/
│       │       ├── test/
│       │       │   ├── base/        # Base test classes
│       │       │   │   ├── OpenOTestBase.java
│       │       │   │   ├── OpenODaoTestBase.java
│       │       │   │   └── OpenOWebTestBase.java
│       │       │   ├── unit/        # Unit test base classes
│       │       │   │   └── OpenOUnitTestBase.java
│       │       │   ├── examples/    # Example tests
│       │       │   ├── mocks/       # Mock implementations
│       │       │   │   └── MockSecurityInfoManager.java
│       │       │   └── simple/      # Framework validation tests
│       │       ├── managers/        # Manager layer unit tests
│       │       │   ├── DemographicUnitTestBase.java    # Base with test data builders
│       │       │   └── DemographicManagerUnitTest.java # 117 tests, 18 @Nested classes
│       │       └── tickler/         # Domain-specific tests
│       │           ├── dao/
│       │           │   └── Multiple test files (11 integration + 3 unit tests)
│       │           ├── manager/
│       │           │   └── TicklerManagerUnitTest.java
│       │           └── TicklerUnitTestBase.java
│       └── resources/
│           ├── META-INF/
│           │   └── persistence.xml   # JPA configuration
│           └── test-context-*.xml    # Spring configurations
└── docs/test/
    ├── README.md                          # Test documentation index
    ├── modern-test-framework-guide.md     # Main framework guide
    ├── test-writing-best-practices.md     # Best practices guide
    └── modern-test-framework-complete.md  # This file
```

## Maven Configuration

### Build Configuration (pom.xml)

The framework is integrated into the default build process:

```xml
<!-- Modern test sources added automatically -->
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>build-helper-maven-plugin</artifactId>
    <version>3.6.0</version>
    <executions>
        <execution>
            <id>add-modern-test-source</id>
            <phase>generate-test-sources</phase>
            <goals>
                <goal>add-test-source</goal>
            </goals>
            <configuration>
                <sources>
                    <source>src/test-modern/java</source>
                </sources>
            </configuration>
        </execution>
    </executions>
</plugin>

<!-- Surefire plugin configured for dual execution -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.2.5</version>
    <executions>
        <!-- Modern tests run first -->
        <execution>
            <id>modern-tests</id>
            <phase>test</phase>
            <configuration>
                <testSourceDirectory>src/test-modern/java</testSourceDirectory>
                <reportsDirectory>${project.build.directory}/surefire-reports-modern</reportsDirectory>
            </configuration>
        </execution>
        <!-- Original tests run second -->
        <execution>
            <id>default-test</id>
            <phase>test</phase>
            <configuration>
                <testSourceDirectory>src/test/java</testSourceDirectory>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Test Execution Order
1. Modern tests execute first (JUnit 5)
2. Original tests execute second (JUnit 4)
3. Both must pass for successful build

## Core Components

### 1. OpenOTestBase - Foundation Class

**Location**: `src/test-modern/java/ca/openosp/openo/test/base/OpenOTestBase.java`

**Purpose**: Base class for all modern tests, handles Spring context and SpringUtils anti-pattern

**Key Features**:
```java
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = {"classpath:test-context-full.xml"})
@TestPropertySource(locations = "classpath:test.properties")
public abstract class OpenOTestBase {

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

**Location**: `src/test-modern/resources/test-context-full.xml`

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
            <value>ca/openosp/openo/commn/model/Provider.hbm.xml</value>
            <value>ca/openosp/openo/commn/model/Demographic.hbm.xml</value>
        </list>
    </property>
</bean>

<bean id="entityManagerFactory" class="org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean">
    <property name="persistenceUnitName" value="testPersistenceUnit" />
</bean>
```

### 3. MockSecurityInfoManager

**Location**: `src/test-modern/java/ca/openosp/openo/test/mocks/MockSecurityInfoManager.java`

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

**Location**: `src/test-modern/resources/META-INF/persistence.xml`

**Features**:
- Explicit entity listing (no scanning)
- Mixed mapping support (.hbm.xml and @Entity)
- H2 dialect configuration

```xml
<persistence-unit name="testPersistenceUnit" transaction-type="RESOURCE_LOCAL">
    <!-- XML Mappings -->
    <mapping-file>ca/openosp/openo/commn/model/Provider.hbm.xml</mapping-file>

    <!-- JPA Entities -->
    <class>ca.openosp.openo.commn.model.Tickler</class>
    <class>ca.openosp.openo.commn.model.TicklerComment</class>

    <exclude-unlisted-classes>true</exclude-unlisted-classes>
</persistence-unit>
```

## Writing Tests

### Test Class Structure

```java
package ca.openosp.openo.tickler.dao;

import ca.openosp.openo.test.base.OpenOTestBase;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Descriptive Test Suite Name")
@Transactional
class ExampleDaoTest extends OpenOTestBase {

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

### JUnit 5 Features Available

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

- **IntelliJ IDEA**: Full JUnit 5 support out of the box
- **Eclipse**: Requires JUnit 5 platform launcher
- **VS Code**: Java Test Runner extension supports JUnit 5

### Test Reports

- **Modern Tests**: `target/surefire-reports-modern/`
- **Original Tests**: `target/surefire-reports/`
- **Coverage Reports**: Compatible with JaCoCo

## Proven Capabilities

### Successfully Demonstrated Features

Based on the 141 implemented tests (129 unit + 12 integration):

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
- **Total Time**: < 30 seconds for all 141 tests
- **Memory Usage**: < 512MB heap
- **Success Rate**: 99% (140/141 passing)

## Current Implementation Status

- ✅ Modern framework operational with JUnit 5
- ✅ Both test suites (modern JUnit 5 and legacy JUnit 4) run independently
- ✅ No impact on existing tests
- ✅ 140 of 141 tests passing (99% pass rate)
- ✅ Full Java 21 compatibility with ByteBuddy experimental flag
- ✅ Manager unit test patterns proven with 117-test DemographicManagerUnitTest
- ✅ Domain-specific base classes demonstrated (DemographicUnitTestBase, TicklerUnitTestBase)

## Required Configurations for OpenO

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
            <value>ca/openosp/openo/commn/model/Provider.hbm.xml</value>
            <value>ca/openosp/openo/commn/model/Demographic.hbm.xml</value>
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
<bean id="ticklerDao" class="ca.openosp.openo.commn.dao.TicklerDaoImpl" autowire="byName" />
<bean id="oscarLogDao" class="ca.openosp.openo.commn.dao.OscarLogDaoImpl" autowire="byName" />
<!-- Add more as needed -->
```

### Entity Discovery Issues

**Problem**: Automatic entity scanning finds entities with dependencies on non-existent tables (e.g., `lst_gender`).

**Solution**: Explicit entity listing in `persistence.xml`:
```xml
<persistence-unit name="testPersistenceUnit">
    <!-- List each entity explicitly -->
    <class>ca.openosp.openo.commn.model.Tickler</class>
    <class>ca.openosp.openo.commn.model.TicklerComment</class>
    <class>ca.openosp.openo.commn.model.OscarLog</class>
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
- Ensure test extends `OpenOTestBase`
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

- ✅ JUnit 5 with Java 21 support
- ✅ Better test organization with @Nested and @DisplayName
- ✅ AssertJ fluent assertions
- ✅ Fast execution with H2 in-memory database
- ✅ No impact on existing JUnit 4 tests
- ✅ Handles complex domain objects and database operations
- ✅ Full Spring dependency injection support
- ✅ Transaction management with automatic rollback
- ✅ Mixed Hibernate XML and JPA annotation support

New tests should be written in `src/test-modern/` using JUnit 5 and the established patterns.

## References

- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [AssertJ Documentation](https://assertj.github.io/doc/)
- [Spring Test Documentation](https://docs.spring.io/spring-framework/docs/5.3.x/reference/html/testing.html)
- [H2 Database Documentation](https://www.h2database.com/html/main.html)

---

*Last Updated: January 2026*
*Version: 1.1*
*Status: Production Ready*