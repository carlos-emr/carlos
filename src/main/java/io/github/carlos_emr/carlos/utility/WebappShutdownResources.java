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
    private static final int MAX_CLASS_LOADER_ANCESTRY_DEPTH = 64;

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
            if (!isOwnedByWebappClassLoader(driver.getClass().getClassLoader(), classLoader)) {
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
     * Determines whether a resource class loader belongs to the stopping webapp
     * loader. Child loaders are included because libraries may create scoped
     * class loaders beneath the webapp loader; parent loaders are excluded to avoid
     * deregistering Tomcat/common drivers shared with other applications.
     *
     * @param resourceClassLoader ClassLoader that loaded a shutdown-sensitive resource
     * @param webappClassLoader The stopping web application ClassLoader
     * @return True when the resource should be treated as CARLOS webapp-owned
     */
    static boolean isOwnedByWebappClassLoader(ClassLoader resourceClassLoader, ClassLoader webappClassLoader) {
        // Bootstrap or missing loaders are not webapp-owned JDBC resources.
        if (resourceClassLoader == null || webappClassLoader == null) {
            return false;
        }
        // Direct match covers the standard Tomcat webapp class-loader case.
        if (resourceClassLoader == webappClassLoader) {
            return true;
        }
        // Nested library loaders below the webapp are owned by the stopping webapp.
        return isAncestor(webappClassLoader, resourceClassLoader);
    }

    private static boolean isAncestor(ClassLoader possibleAncestor, ClassLoader classLoader) {
        // Shutdown checks a small DriverManager snapshot; avoid caching class-loader
        // relationships so this cleanup path never retains loaders after redeploy.
        // Normal servlet containers have shallow hierarchies; cap the walk to avoid
        // pathological custom loader chains during shutdown.
        ClassLoader current = classLoader.getParent();
        int depth = 0;
        while (current != null && depth < MAX_CLASS_LOADER_ANCESTRY_DEPTH) {
            if (current == possibleAncestor) {
                return true;
            }
            current = current.getParent();
            depth++;
        }
        return false;
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
