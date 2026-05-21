/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Collections;
import java.util.Properties;
import java.util.logging.Logger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.MockedStatic;

import io.github.carlos_emr.carlos.drools.DroolsShutdownResources;
import io.github.carlos_emr.carlos.log.LogAction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Unit tests for {@link WebappShutdownResources}.
 * The @Isolated annotation keeps the DriverManager mutation test from running beside
 * connection-opening tests that depend on JVM-global driver registrations.
 */
@Tag("unit")
@DisplayName("WebappShutdownResources")
@Isolated
public class WebappShutdownResourcesUnitTest {

    @Test
    void shouldDeregisterJdbcDriver_whenLoadedByWebappClassLoader() throws Exception {
        java.util.List<Driver> existingDrivers = Collections.list(DriverManager.getDrivers());
        URL testClassesUrl = testClassesUrl();
        URL mainClassesUrl = mainClassesUrl();

        try (ChildFirstTestClassLoader webappClassLoader = new ChildFirstTestClassLoader(
                testClassesUrl, mainClassesUrl, getClass().getClassLoader())) {
            Class<?> helperClass = webappClassLoader.loadClass(
                    WebappShutdownResourcesUnitTest.class.getName() + "$DriverRegistrationHelper");
            Method registerDriver = helperClass.getMethod("registerDriver");
            Method deregisterDriver = helperClass.getMethod("deregisterDriver", Driver.class);
            Method deregisterWebappDrivers = helperClass.getMethod("deregisterWebappDrivers", ClassLoader.class);
            Method isDriverRegistered = helperClass.getMethod("isDriverRegistered", Driver.class);
            Driver driver = (Driver) registerDriver.invoke(null);

            try {
                int deregistered = (Integer) deregisterWebappDrivers.invoke(null, webappClassLoader);

                assertThat(deregistered).isEqualTo(1);
                assertThat((Boolean) isDriverRegistered.invoke(null, driver)).isFalse();
            } finally {
                if ((Boolean) isDriverRegistered.invoke(null, driver)) {
                    deregisterDriver.invoke(null, driver);
                }
                java.util.List<Driver> currentDrivers = Collections.list(DriverManager.getDrivers());
                for (Driver existingDriver : existingDrivers) {
                    if (!currentDrivers.contains(existingDriver)) {
                        DriverManager.registerDriver(existingDriver);
                    }
                }
            }
        }
    }

    @Test
    void shouldDeregisterJdbcDriver_whenLoadedByChildClassLoader() throws Exception {
        java.util.List<Driver> existingDrivers = Collections.list(DriverManager.getDrivers());
        URL testClassesUrl = testClassesUrl();
        URL mainClassesUrl = mainClassesUrl();

        try (URLClassLoader parentWebappClassLoader = new URLClassLoader(new URL[0], getClass().getClassLoader());
             ChildFirstTestClassLoader childClassLoader = new ChildFirstTestClassLoader(
                     testClassesUrl, mainClassesUrl, parentWebappClassLoader)) {
            Class<?> helperClass = childClassLoader.loadClass(
                    WebappShutdownResourcesUnitTest.class.getName() + "$DriverRegistrationHelper");
            Method registerDriver = helperClass.getMethod("registerDriver");
            Method deregisterDriver = helperClass.getMethod("deregisterDriver", Driver.class);
            Method deregisterWebappDrivers = helperClass.getMethod("deregisterWebappDrivers", ClassLoader.class);
            Method isDriverRegistered = helperClass.getMethod("isDriverRegistered", Driver.class);
            Driver driver = (Driver) registerDriver.invoke(null);

            try {
                int deregistered = (Integer) deregisterWebappDrivers.invoke(null, parentWebappClassLoader);

                assertThat(deregistered).isEqualTo(1);
                assertThat((Boolean) isDriverRegistered.invoke(null, driver)).isFalse();
            } finally {
                if ((Boolean) isDriverRegistered.invoke(null, driver)) {
                    deregisterDriver.invoke(null, driver);
                }
                java.util.List<Driver> currentDrivers = Collections.list(DriverManager.getDrivers());
                for (Driver existingDriver : existingDrivers) {
                    if (!currentDrivers.contains(existingDriver)) {
                        DriverManager.registerDriver(existingDriver);
                    }
                }
            }
        }
    }

    @Test
    void shouldIgnoreJdbcDriver_whenLoadedBySharedParentClassLoader() throws Exception {
        try (URLClassLoader sharedParentClassLoader = new URLClassLoader(new URL[0], getClass().getClassLoader());
             URLClassLoader webappClassLoader = new URLClassLoader(new URL[0], sharedParentClassLoader)) {
            assertThat(WebappShutdownResources.isWebappOwnedDriver(sharedParentClassLoader, webappClassLoader))
                    .isFalse();
        }
    }

    @Test
    void shouldPreserveJdbcDriver_whenLoadedBySharedParentClassLoader() throws Exception {
        java.util.List<Driver> existingDrivers = Collections.list(DriverManager.getDrivers());
        URL testClassesUrl = testClassesUrl();
        URL mainClassesUrl = mainClassesUrl();

        try (ChildFirstTestClassLoader sharedParentClassLoader = new ChildFirstTestClassLoader(
                     testClassesUrl, mainClassesUrl, getClass().getClassLoader());
             ChildFirstTestClassLoader webappClassLoader = new ChildFirstTestClassLoader(
                     testClassesUrl, mainClassesUrl, sharedParentClassLoader)) {
            Class<?> parentHelperClass = sharedParentClassLoader.loadClass(
                    WebappShutdownResourcesUnitTest.class.getName() + "$DriverRegistrationHelper");
            Class<?> webappHelperClass = webappClassLoader.loadClass(
                    WebappShutdownResourcesUnitTest.class.getName() + "$DriverRegistrationHelper");
            Method registerParentDriver = parentHelperClass.getMethod("registerDriver");
            Method deregisterParentDriver = parentHelperClass.getMethod("deregisterDriver", Driver.class);
            Method isParentDriverRegistered = parentHelperClass.getMethod("isDriverRegistered", Driver.class);
            Method registerWebappDriver = webappHelperClass.getMethod("registerDriver");
            Method deregisterWebappDriver = webappHelperClass.getMethod("deregisterDriver", Driver.class);
            Method deregisterWebappDrivers = webappHelperClass.getMethod("deregisterWebappDrivers", ClassLoader.class);
            Method isWebappDriverRegistered = webappHelperClass.getMethod("isDriverRegistered", Driver.class);
            Driver parentDriver = (Driver) registerParentDriver.invoke(null);
            Driver webappDriver = (Driver) registerWebappDriver.invoke(null);

            try {
                int deregistered = (Integer) deregisterWebappDrivers.invoke(null, webappClassLoader);

                assertThat(deregistered).isEqualTo(1);
                assertThat((Boolean) isParentDriverRegistered.invoke(null, parentDriver)).isTrue();
                assertThat((Boolean) isWebappDriverRegistered.invoke(null, webappDriver)).isFalse();
            } finally {
                if ((Boolean) isWebappDriverRegistered.invoke(null, webappDriver)) {
                    deregisterWebappDriver.invoke(null, webappDriver);
                }
                if ((Boolean) isParentDriverRegistered.invoke(null, parentDriver)) {
                    deregisterParentDriver.invoke(null, parentDriver);
                }
                java.util.List<Driver> currentDrivers = Collections.list(DriverManager.getDrivers());
                for (Driver existingDriver : existingDrivers) {
                    if (!currentDrivers.contains(existingDriver)) {
                        DriverManager.registerDriver(existingDriver);
                    }
                }
            }
        }
    }

    @Test
    void shouldRunAllShutdownSteps_whenEarlierStepThrows() {
        ClassLoader unrelatedClassLoader = new ClassLoader(null) {
        };

        try (MockedStatic<DbConnectionFilter> dbConnections = mockStatic(DbConnectionFilter.class);
             MockedStatic<OscarTrackingBasicDataSource> tracking = mockStatic(OscarTrackingBasicDataSource.class);
             MockedStatic<LogAction> logAction = mockStatic(LogAction.class);
             MockedStatic<DroolsShutdownResources> droolsShutdown = mockStatic(DroolsShutdownResources.class);
             MockedStatic<QueueCache> queueCache = mockStatic(QueueCache.class)) {
            dbConnections.when(DbConnectionFilter::releaseAllKnownDbResources)
                    .thenThrow(new AssertionError("db cleanup failed"));

            WebappShutdownResources.ShutdownReport report = WebappShutdownResources.releaseForContext(unrelatedClassLoader);

            assertThat(report.results()).hasSize(7);
            assertThat(report.results().get(0).successful()).isFalse();
            assertThat(report.failureCount()).isEqualTo(1);
            tracking.verify(OscarTrackingBasicDataSource::clearTrackingState);
            logAction.verify(LogAction::shutdownExecutorService);
            droolsShutdown.verify(DroolsShutdownResources::shutdownExecutors);
            queueCache.verify(QueueCache::shutdownSharedTimer);
        }
    }

    private static URL testClassesUrl() throws Exception {
        return WebappShutdownResourcesUnitTest.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI()
                .toURL();
    }

    private static URL mainClassesUrl() throws Exception {
        return WebappShutdownResources.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI()
                .toURL();
    }

    public static final class DriverRegistrationHelper {

        private DriverRegistrationHelper() {
        }

        public static Driver registerDriver() throws SQLException {
            Driver driver = new TestDriver();
            DriverManager.registerDriver(driver);
            return driver;
        }

        public static void deregisterDriver(Driver driver) throws SQLException {
            DriverManager.deregisterDriver(driver);
        }

        public static int deregisterWebappDrivers(ClassLoader webappClassLoader) {
            return WebappShutdownResources.deregisterJdbcDrivers(webappClassLoader);
        }

        public static boolean isDriverRegistered(Driver driver) {
            return Collections.list(DriverManager.getDrivers()).contains(driver);
        }

        public static final class TestDriver implements Driver {

            @Override
            public Connection connect(String url, Properties info) {
                return null;
            }

            @Override
            public boolean acceptsURL(String url) {
                return false;
            }

            @Override
            public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
                return new DriverPropertyInfo[0];
            }

            @Override
            public int getMajorVersion() {
                return 1;
            }

            @Override
            public int getMinorVersion() {
                return 0;
            }

            @Override
            public boolean jdbcCompliant() {
                return false;
            }

            @Override
            public Logger getParentLogger() throws SQLFeatureNotSupportedException {
                throw new SQLFeatureNotSupportedException("not supported");
            }
        }
    }

    private static final class ChildFirstTestClassLoader extends URLClassLoader {
        private static final String CHILD_FIRST_CLASS_PREFIX =
                WebappShutdownResourcesUnitTest.class.getName() + "$DriverRegistrationHelper";
        private static final String WEBAPP_SHUTDOWN_RESOURCES_CLASS =
                WebappShutdownResources.class.getName();

        private ChildFirstTestClassLoader(URL testClassesUrl, URL mainClassesUrl, ClassLoader parent) {
            super(new URL[]{testClassesUrl, mainClassesUrl}, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith(CHILD_FIRST_CLASS_PREFIX)
                    || WEBAPP_SHUTDOWN_RESOURCES_CLASS.equals(name)) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loadedClass = findLoadedClass(name);
                    if (loadedClass == null) {
                        loadedClass = findClass(name);
                    }
                    if (resolve) {
                        resolveClass(loadedClass);
                    }
                    return loadedClass;
                }
            }
            return super.loadClass(name, resolve);
        }
    }
}
