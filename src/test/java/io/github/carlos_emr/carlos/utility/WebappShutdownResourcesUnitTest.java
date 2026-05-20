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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Collections;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThatNoException;
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
class WebappShutdownResourcesUnitTest {

    @Test
    void shouldDeregisterJdbcDriver_whenLoadedByWebappClassLoader() throws Exception {
        java.util.List<Driver> existingDrivers = Collections.list(DriverManager.getDrivers());
        ClassLoader webappClassLoader = new ClassLoader(getClass().getClassLoader()) {
        };
        Driver driver = newTestDriver(webappClassLoader);
        DriverManager.registerDriver(driver);

        try {
            int deregistered = WebappShutdownResources.deregisterJdbcDrivers(webappClassLoader);

            assertThat(deregistered).isEqualTo(1);
            assertThat(Collections.list(DriverManager.getDrivers())).doesNotContain(driver);
        } finally {
            DriverManager.deregisterDriver(driver);
            java.util.List<Driver> currentDrivers = Collections.list(DriverManager.getDrivers());
            for (Driver existingDriver : existingDrivers) {
                if (!currentDrivers.contains(existingDriver)) {
                    DriverManager.registerDriver(existingDriver);
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

    @Test
    void shouldNoOp_whenMySqlCleanupThreadClassMissing() {
        assertThatNoException().isThrownBy(
                () -> WebappShutdownResources.shutdownAbandonedConnectionCleanupThread("missing.mysql.CleanupThread"));
    }

    private static Driver newTestDriver(ClassLoader webappClassLoader) {
        InvocationHandler handler = new TestDriverInvocationHandler();
        return (Driver) Proxy.newProxyInstance(webappClassLoader, new Class<?>[]{Driver.class}, handler);
    }

    private static final class TestDriverInvocationHandler implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return switch (method.getName()) {
                case "connect" -> null;
                case "acceptsURL", "jdbcCompliant" -> false;
                case "getPropertyInfo" -> new DriverPropertyInfo[0];
                case "getMajorVersion" -> 1;
                case "getMinorVersion" -> 0;
                case "getParentLogger" -> throw new SQLFeatureNotSupportedException("not supported");
                case "toString" -> TestDriverInvocationHandler.class.getSimpleName();
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException("Unexpected method: " + method.getName());
            };
        }
    }
}
