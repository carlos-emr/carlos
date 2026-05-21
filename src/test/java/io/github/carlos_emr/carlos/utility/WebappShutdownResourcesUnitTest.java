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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link WebappShutdownResources}.
 * The @Isolated annotation keeps the DriverManager mutation test from running beside
 * connection-opening tests that depend on JVM-global driver registrations.
 *
 * @since 2026-05-19
 */
@Tag("unit")
@DisplayName("WebappShutdownResources")
@Isolated
public class WebappShutdownResourcesUnitTest {

    @Test
    void shouldDeregisterJdbcDriver_whenLoadedByWebappClassLoader() throws Exception {
        java.util.List<Driver> existingDrivers = Collections.list(DriverManager.getDrivers());
        URL testClassesUrl = WebappShutdownResourcesUnitTest.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI()
                .toURL();
        URL mainClassesUrl = WebappShutdownResources.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI()
                .toURL();

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
                deregisterDriver.invoke(null, driver);
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
    void shouldTreatChildClassLoaderResource_asWebappOwned() {
        ClassLoader webappClassLoader = getClass().getClassLoader();
        ClassLoader scopedChildLoader = new ClassLoader(webappClassLoader) {
        };

        assertThat(WebappShutdownResources.isOwnedByWebappClassLoader(scopedChildLoader, webappClassLoader)).isTrue();
    }

    @Test
    void shouldLeaveParentClassLoaderResource_registeredForSharedContainerUse() {
        ClassLoader parentClassLoader = getClass().getClassLoader();
        ClassLoader testWebappLoader = new ClassLoader(parentClassLoader) {
        };

        assertThat(WebappShutdownResources.isOwnedByWebappClassLoader(parentClassLoader, testWebappLoader)).isFalse();
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

        private static final class TestDriver implements Driver {

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
