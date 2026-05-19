/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collections;

import org.apache.logging.log4j.Logger;

import com.mysql.cj.jdbc.AbandonedConnectionCleanupThread;

/**
 * Releases process-wide resources that JDBC drivers and CARLOS utility caches
 * otherwise keep pinned to the stopped web application class loader.
 *
 * @since 2026-05-19
 */
public final class WebappShutdownResources {

    private static final Logger logger = MiscUtils.getLogger();

    private WebappShutdownResources() {
    }

    /**
     * Releases shutdown-sensitive resources for the supplied web application class loader.
     *
     * @param webappClassLoader class loader associated with the stopping CARLOS webapp
     */
    public static void releaseForContext(ClassLoader webappClassLoader) {
        DbConnectionFilter.releaseAllThreadDbResources();
        OscarTrackingBasicDataSource.clearTrackingState();
        QueueCache.shutdownSharedTimer();
        shutdownMySqlAbandonedConnectionCleanupThread();
        deregisterJdbcDrivers(webappClassLoader);
    }

    /**
     * Deregisters JDBC drivers loaded by the stopping webapp class loader only.
     * Drivers loaded by Tomcat or another parent loader are left registered because
     * they may be shared with other applications. Package-private visibility keeps
     * the lifecycle helper scoped to the utility package while allowing focused
     * tests to exercise the class-loader filtering contract directly.
     *
     * @param webappClassLoader class loader whose drivers should be deregistered
     * @return number of JDBC drivers successfully deregistered
     */
    static int deregisterJdbcDrivers(ClassLoader webappClassLoader) {
        ClassLoader classLoader = webappClassLoader != null
                ? webappClassLoader
                : WebappShutdownResources.class.getClassLoader();
        int deregistered = 0;

        for (Driver driver : Collections.list(DriverManager.getDrivers())) {
            if (driver.getClass().getClassLoader() != classLoader) {
                continue;
            }

            try {
                DriverManager.deregisterDriver(driver);
                deregistered++;
                logger.info("Deregistered JDBC driver {}", driver.getClass().getName());
            } catch (SQLException e) {
                logger.warn("Unable to deregister JDBC driver {}", driver.getClass().getName(), e);
            }
        }

        return deregistered;
    }

    /**
     * Stops MySQL Connector/J's abandoned-connection cleanup thread during webapp
     * shutdown. A stopped cleanup thread is expected on repeated shutdown paths, so
     * that state is logged at debug level; unexpected runtime failures are warnings
     * because shutdown should continue best-effort. Package-private visibility keeps
     * direct use inside the utility package and supports focused lifecycle tests.
     */
    static void shutdownMySqlAbandonedConnectionCleanupThread() {
        try {
            AbandonedConnectionCleanupThread.checkedShutdown();
        } catch (IllegalStateException e) {
            logger.debug("MySQL abandoned connection cleanup thread was already stopped", e);
        } catch (RuntimeException e) {
            logger.warn("Unable to stop MySQL abandoned connection cleanup thread", e);
        }
    }
}
