/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Properties;
import java.util.logging.Logger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link WebappShutdownResources}.
 *
 * @since 2026-05-19
 */
@Tag("unit")
@DisplayName("WebappShutdownResources")
class WebappShutdownResourcesUnitTest {

    @Test
    void shouldDeregisterJdbcDriver_whenLoadedByWebappClassLoader() throws Exception {
        java.util.List<Driver> existingDrivers = Collections.list(DriverManager.getDrivers());
        Driver driver = new TestDriver();
        DriverManager.registerDriver(driver);

        try {
            int deregistered = WebappShutdownResources.deregisterJdbcDrivers(getClass().getClassLoader());

            assertThat(deregistered).isGreaterThanOrEqualTo(1);
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
        public Logger getParentLogger() throws SQLException {
            throw new SQLException("not supported");
        }
    }
}
