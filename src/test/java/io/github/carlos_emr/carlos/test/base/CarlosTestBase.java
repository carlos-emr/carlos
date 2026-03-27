/**
 * Copyright (c) 2025. Magenta Health. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * Now maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 *
 * Modifications by CARLOS Contributors, 2026.
 */
package io.github.carlos_emr.carlos.test.base;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import io.github.carlos_emr.carlos.test.base.HibernateTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.TestPropertySource;

import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.mockito.MockitoAnnotations;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.lang.reflect.Field;

/**
 * Base test class for CARLOS EMR that handles the SpringUtils anti-pattern
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
 * @see SpringUtils
 * @see SpringExtension
 * @since 2025-09-19
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = {
    "classpath:test-context-full.xml"
})
@WebAppConfiguration
@Transactional
@Rollback
@TestPropertySource(locations = "classpath:test.properties")
public abstract class CarlosTestBase {

    /** Logger instance for test debugging and information */
    protected static final Logger logger = LogManager.getLogger(CarlosTestBase.class);

    /** Spring application context for the test */
    @Autowired
    protected ApplicationContext applicationContext;

    /**
     * HibernateTemplate for flushing the standalone Hibernate Session.
     *
     * <p>CARLOS EMR uses a permanent mixed-persistence architecture: legacy
     * {@code HibernateDaoSupport} DAOs write through the standalone Hibernate
     * Session while modern DAOs use the JPA {@code EntityManager}. These are
     * separate persistence contexts sharing the same JDBC Connection.
     * Calling {@code entityManager.flush()} does NOT flush the Hibernate Session.
     * Tests that persist data through {@code HibernateDaoSupport} DAOs must call
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
     * Captures the application context injected by Spring's {@code @Autowired}
     * setter injection and initialises the static {@code SpringUtils} bean factory.
     *
     * <p>Called by Spring's dependency-injection mechanism during test context
     * initialization. The {@code @BeforeAll} guard in {@link #initializeSpringUtils()}
     * is a safety net: if {@code setApplicationContext} has already fired on a prior
     * test-class instance in the same JVM run, {@code staticContext} will already be
     * non-null and {@code @BeforeAll} can complete initialization without waiting for
     * setter injection on the new instance.</p>
     *
     * @param context the Spring application context injected by Spring
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
     * No-op hook for subclasses that need a test {@code LoggedInInfo} in the
     * security context. Override this method when the code under test calls
     * {@code LoggedInInfo.getLoggedInInfoFromSession(request)}.
     */
    protected void setUpLoggedInInfo() {
        // No-op by default — override in subclasses that require a security context.
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