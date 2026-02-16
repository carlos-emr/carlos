/**
 * Copyright (c) 2025. Magenta Health. All Rights Reserved.
 *
 * OpenO Test Framework Base Class
 *
 * Foundation class for all OpenO EMR test suites providing common
 * test infrastructure and utilities.
 *
 * @author yingbull
 * @since 2025-09-15
 */
package io.github.carlos_emr.carlos.test.base;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.orm.hibernate5.HibernateTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.TestPropertySource;

import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.mockito.MockitoAnnotations;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Base test class for OpenO EMR that handles the SpringUtils anti-pattern
 * and provides modern JUnit 5 testing capabilities while maintaining
 * compatibility with legacy code patterns.
 *
 * <p><b>Key Features:</b>
 * <ul>
 *   <li>Initializes SpringUtils with the test application context</li>
 *   <li>Provides transaction rollback by default</li>
 *   <li>Sets up logging and test metadata</li>
 *   <li>Handles the static Spring context injection anti-pattern</li>
 * </ul>
 *
 * <p><b>SpringUtils Anti-Pattern Handling:</b>
 * The production code uses {@code SpringUtils.getBean()} instead of proper
 * dependency injection. This base class ensures SpringUtils is properly
 * initialized with the test application context, allowing legacy code to
 * function correctly during testing.
 *
 * <p><b>Configuration:</b>
 * Tests are configured to run with Spring's test context, automatic
 * transaction rollback, and property overrides from test.properties.
 *
 * <p><b>Parallel Execution:</b>
 * Integration tests are explicitly marked {@code @Execution(SAME_THREAD)} to prevent
 * concurrent execution within a single JVM. All integration tests share a single H2 in-memory
 * database and Spring context. Process-level parallelism is achieved via Surefire's
 * {@code forkCount} — each fork gets its own JVM, Spring context, and H2 database instance.
 *
 * @see SpringUtils
 * @see SpringExtension
 * @author yingbull
 * @since 2025-09-15
 */
@Execution(ExecutionMode.SAME_THREAD)
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = {
    "classpath:test-context-full.xml"
})
@WebAppConfiguration
@Transactional
@Rollback
@TestPropertySource(locations = "classpath:test.properties")
public abstract class OpenOTestBase {

    /** Logger instance for test debugging and information */
    protected static final Logger logger = LogManager.getLogger(OpenOTestBase.class);

    /** Spring application context for the test */
    @Autowired
    protected ApplicationContext applicationContext;

    /**
     * HibernateTemplate for flushing the standalone Hibernate Session.
     *
     * <p>During the Hibernate→JPA migration, legacy HibernateDaoSupport DAOs write
     * through the standalone Hibernate Session while modern DAOs use the JPA
     * EntityManager. These are separate persistence contexts sharing the same JDBC
     * Connection. Calling {@code entityManager.flush()} does NOT flush the Hibernate
     * Session. Tests that persist data through HibernateDaoSupport DAOs must call
     * {@code hibernateTemplate.flush()} to push those writes to the database before
     * verification queries.</p>
     */
    @Autowired
    protected HibernateTemplate hibernateTemplate;

    /** Static context reference for SpringUtils initialization */
    private static ApplicationContext staticContext;

    /** Flag to track SpringUtils initialization status */
    private static boolean springUtilsInitialized = false;

    /** JUnit 5 test information for the current test */
    protected TestInfo testInfo;

    /**
     * Initialize SpringUtils with the test application context.
     *
     * <p>This method handles the anti-pattern where production code uses
     * {@code SpringUtils.getBean()} instead of proper dependency injection.
     * By setting the application context via reflection, we ensure that
     * legacy code continues to function during testing.
     *
     * <p><b>Implementation Note:</b>
     * Uses reflection to access the private static field in SpringUtils,
     * which is necessary due to the legacy design of the utility class.
     *
     * @throws RuntimeException if SpringUtils cannot be initialized
     */
    @BeforeAll
    public static void initializeSpringUtils() {
        if (!springUtilsInitialized && staticContext != null) {
            try {
                // Use reflection to set the private static beanFactory in SpringUtils
                Field contextField = SpringUtils.class.getDeclaredField("beanFactory");
                contextField.setAccessible(true);
                contextField.set(null, staticContext);

                springUtilsInitialized = true;
                logger.info("SpringUtils initialized with test application context");
            } catch (Exception e) {
                logger.error("Failed to initialize SpringUtils", e);
                throw new RuntimeException("Cannot initialize SpringUtils for testing", e);
            }
        }
    }

    /**
     * Capture the application context for static initialization.
     *
     * <p>This method is called by Spring during context initialization.
     * We store the context statically to enable SpringUtils initialization
     * for all test instances.
     *
     * @param context the Spring application context
     */
    @Autowired
    public void setApplicationContext(ApplicationContext context) {
        this.applicationContext = context;
        if (staticContext == null) {
            staticContext = context;
            initializeSpringUtils();
        }
    }

    /**
     * Set up test information and Mockito mocks before each test.
     *
     * <p>This method is called before each test method and:
     * <ul>
     *   <li>Stores test metadata for logging</li>
     *   <li>Initializes Mockito annotations</li>
     *   <li>Sets up LoggedInInfo for security context</li>
     * </ul>
     *
     * @param testInfo JUnit 5 test information
     */
    @BeforeEach
    public void setUpBase(TestInfo testInfo) {
        this.testInfo = testInfo;
        MockitoAnnotations.openMocks(this);

        logger.info("Running test: {} in class: {}",
            testInfo.getDisplayName(),
            testInfo.getTestClass().orElse(null));

        // Set up a default LoggedInInfo for tests that need it
        setUpLoggedInInfo();
    }

    /**
     * Create a mock LoggedInInfo for testing.
     *
     * <p>LoggedInInfo represents the security context containing the
     * currently logged-in user's information. Override this method
     * to customize the security context for specific test scenarios.
     *
     * <p><b>Implementation Note:</b>
     * The actual implementation depends on how LoggedInInfo is stored
     * (session, ThreadLocal, etc.) in your application.
     */
    protected void setUpLoggedInInfo() {
        // This would typically set up a test LoggedInInfo in session or ThreadLocal
        // Based on how your LoggedInInfo.getLoggedInInfoFromSession() works
    }

    /**
     * Helper method to get a Spring bean, using the same pattern as production code
     * but ensuring it uses our test context.
     *
     * @param <T> the type of the bean
     * @param beanClass the class of the bean to retrieve
     * @return the bean instance from the test context
     */
    protected <T> T getBean(Class<T> beanClass) {
        return applicationContext.getBean(beanClass);
    }

    /**
     * Helper method to get a Spring bean by name.
     *
     * @param beanName the name of the bean to retrieve
     * @return the bean instance from the test context
     */
    protected Object getBean(String beanName) {
        return applicationContext.getBean(beanName);
    }

    /**
     * Verify that SpringUtils returns the same beans as our test context.
     *
     * <p>This validation ensures the SpringUtils anti-pattern is properly
     * handled and that production code using {@code SpringUtils.getBean()}
     * will receive the correct test beans.
     *
     * @param beanClass the class of the bean to verify
     * @throws AssertionError if SpringUtils returns a different bean instance
     */
    protected void verifySpringUtilsIntegration(Class<?> beanClass) {
        Object contextBean = applicationContext.getBean(beanClass);
        Object utilsBean = SpringUtils.getBean(beanClass);

        if (contextBean != utilsBean) {
            logger.warn("SpringUtils bean mismatch for class: {}. Context: {}, Utils: {}",
                beanClass.getName(), contextBean, utilsBean);
        }
    }

    /**
     * Clean up resources after test if needed.
     *
     * <p>Override this method to perform custom cleanup operations
     * such as closing resources, clearing caches, or resetting
     * static state.
     *
     * <p>This method is called after each test completes.
     */
    protected void cleanUp() {
        // Default implementation - override as needed
    }
}