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

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.mysql.cj.jdbc.AbandonedConnectionCleanupThread;
import io.github.carlos_emr.carlos.drools.DroolsShutdownResources;
import io.github.carlos_emr.carlos.log.LogAction;
import org.apache.logging.log4j.Logger;

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
     * The call order is significant: servlet-thread DB state and datasource tracking are released before
     * async log workers, Drools executors, cache timers, MySQL cleanup, and finally webapp-owned
     * JDBC driver deregistration.
     *
     * @param webappClassLoader class loader associated with the stopping CARLOS webapp
     * @return per-step shutdown report for audit logging
     */
    public static ShutdownReport releaseForContext(ClassLoader webappClassLoader) {
        List<ShutdownStepResult> results = new ArrayList<>();
        runStep(results, ShutdownStep.DB_THREAD_RESOURCES, () -> {
            DbConnectionFilter.releaseAllKnownDbResources();
            return 0;
        });
        runStep(results, ShutdownStep.TRACKING_DATA_SOURCE, () -> {
            OscarTrackingBasicDataSource.clearTrackingState();
            return 0;
        });
        runStep(results, ShutdownStep.LOG_ACTION_EXECUTOR, () -> {
            LogAction.shutdownExecutorService();
            return 0;
        });
        runStep(results, ShutdownStep.DROOLS_EXECUTORS, () -> DroolsShutdownResources.shutdownExecutors());
        runStep(results, ShutdownStep.QUEUE_CACHE_TIMER, () -> {
            QueueCache.shutdownSharedTimer();
            return 0;
        });
        runStep(results, ShutdownStep.MYSQL_CLEANUP_THREAD, () -> {
            shutdownMySqlAbandonedConnectionCleanupThread();
            return 0;
        });
        runStep(results, ShutdownStep.JDBC_DRIVERS, () -> deregisterJdbcDrivers(webappClassLoader));
        return new ShutdownReport(results);
    }

    private static void runStep(List<ShutdownStepResult> results, ShutdownStep step, ShutdownOperation operation) {
        try {
            results.add(ShutdownStepResult.success(step, operation.run()));
        } catch (Throwable t) {
            logger.warn("Shutdown step {} failed", step, t);
            results.add(ShutdownStepResult.failure(step, t));
        }
    }

    @FunctionalInterface
    private interface ShutdownOperation {
        int run() throws Throwable;
    }

    public enum ShutdownStep {
        DB_THREAD_RESOURCES,
        TRACKING_DATA_SOURCE,
        LOG_ACTION_EXECUTOR,
        DROOLS_EXECUTORS,
        QUEUE_CACHE_TIMER,
        MYSQL_CLEANUP_THREAD,
        JDBC_DRIVERS
    }

    public record ShutdownStepResult(ShutdownStep step, boolean successful, int count, Throwable failure) {
        static ShutdownStepResult success(ShutdownStep step, int count) {
            return new ShutdownStepResult(step, true, count, null);
        }

        static ShutdownStepResult failure(ShutdownStep step, Throwable failure) {
            return new ShutdownStepResult(step, false, 0, failure);
        }
    }

    public static final class ShutdownReport {
        private final List<ShutdownStepResult> results;

        private ShutdownReport(List<ShutdownStepResult> results) {
            this.results = List.copyOf(results);
        }

        public List<ShutdownStepResult> results() {
            return results;
        }

        public boolean successful() {
            return results.stream().allMatch(ShutdownStepResult::successful);
        }

        public long failureCount() {
            return results.stream().filter(result -> !result.successful()).count();
        }

        public int deregisteredDriverCount() {
            return results.stream()
                    .filter(result -> result.step() == ShutdownStep.JDBC_DRIVERS)
                    .mapToInt(ShutdownStepResult::count)
                    .sum();
        }
    }

    /**
     * Deregisters JDBC drivers owned by the stopping webapp class loader hierarchy.
     * Drivers loaded by Tomcat or another shared parent loader are left registered because
     * they may be shared with other applications.
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
            if (!isWebappOwnedDriver(driver.getClass().getClassLoader(), classLoader)) {
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
     * Determines whether a JDBC driver should be treated as owned by the stopping
     * webapp. Direct webapp drivers and drivers loaded by child class loaders are
     * owned. When the servlet container passes a wrapper child loader, drivers loaded
     * by the same loader as this shutdown utility are also owned if that loader is an
     * ancestor of the supplied webapp loader. This method is package-visible so tests
     * can pin the class loader hierarchy rules without mutating DriverManager state.
     *
     * @param driverClassLoader class loader that loaded the JDBC driver class
     * @param webappClassLoader class loader associated with the stopping webapp
     * @return true when the driver belongs to the webapp class loader hierarchy
     */
    static boolean isWebappOwnedDriver(ClassLoader driverClassLoader, ClassLoader webappClassLoader) {
        if (driverClassLoader == null) {
            return false;
        }
        if (isSameOrChildClassLoader(driverClassLoader, webappClassLoader)) {
            return true;
        }

        ClassLoader shutdownResourcesClassLoader = WebappShutdownResources.class.getClassLoader();
        return driverClassLoader == shutdownResourcesClassLoader
                && isSameOrChildClassLoader(webappClassLoader, shutdownResourcesClassLoader);
    }

    /**
     * Walks a class loader's parent chain looking for an expected ancestor.
     *
     * @param candidate class loader to inspect
     * @param expectedAncestor class loader expected to appear in the parent chain
     * @return true when candidate is the same class loader as expectedAncestor or a descendant of it
     */
    private static boolean isSameOrChildClassLoader(ClassLoader candidate, ClassLoader expectedAncestor) {
        for (ClassLoader current = candidate; current != null; current = current.getParent()) {
            if (current == expectedAncestor) {
                return true;
            }
        }
        return false;
    }

    /**
     * Stops MySQL Connector/J's abandoned-connection cleanup thread during webapp
     * shutdown. A stopped cleanup thread is expected on repeated shutdown paths, so
     * that state is logged at debug level; unexpected runtime failures are warnings
     * because shutdown should continue best-effort.
     */
    static void shutdownMySqlAbandonedConnectionCleanupThread() {
        try {
            AbandonedConnectionCleanupThread.checkedShutdown();
        } catch (NoClassDefFoundError e) {
            logger.debug("MySQL cleanup thread class is not available on the classpath", e);
        } catch (IllegalStateException e) {
            logger.debug("MySQL cleanup thread was already stopped", e);
        } catch (RuntimeException e) {
            logger.warn("Unable to stop MySQL cleanup thread", e);
        }
    }
}
