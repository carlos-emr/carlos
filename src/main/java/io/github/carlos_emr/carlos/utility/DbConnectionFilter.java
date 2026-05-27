/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.utility;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import javax.sql.DataSource;

import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.util.SqlUtils;

public class DbConnectionFilter implements jakarta.servlet.Filter {
    private static final Logger logger = MiscUtils.getLogger();

    private static ThreadLocal<AtomicReference<Connection>> dbConnection = new ThreadLocal<AtomicReference<Connection>>();
    private static final Set<AtomicReference<Connection>> trackedConnectionHolders = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<AtomicReference<Connection>, Boolean>()));
    private static final Set<Connection> trackedConnections = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<Connection, Boolean>()));

    /**
     * @deprecated Raw JDBC connections obtained here bypass Spring's transaction
     * management and run with {@code autoCommit=true}. Use JPA {@link jakarta.persistence.EntityManager}
     * and {@link jakarta.persistence.EntityManager#createNativeQuery(String)} within an
     * {@code @Transactional} context instead. Scheduled for removal once remaining
     * non-DAO callers migrate.
     */
    @Deprecated(forRemoval = true)
    public static Connection getThreadLocalDbConnection() throws SQLException {
        AtomicReference<Connection> holder = dbConnection.get();
        if (holder == null) {
            holder = new AtomicReference<Connection>();
            dbConnection.set(holder);
            trackedConnectionHolders.add(holder);
        }

        Connection c = holder.get();
        if (c == null || c.isClosed()) {
            if (c != null) {
                trackedConnections.remove(c);
            }
            c = getDbConnection();
            holder.set(c);
            trackedConnections.add(c);
        }

        return (c);
    }

    public void init(FilterConfig filterConfig) throws ServletException {
        logger.info("Starting Filter : " + getClass().getSimpleName());
    }

    public void doFilter(ServletRequest tmpRequest, ServletResponse tmpResponse, FilterChain chain) throws IOException, ServletException {
        try {
            chain.doFilter(tmpRequest, tmpResponse);
        } catch (Exception e) {
            logger.error("Unexpected error.", e);
            throw (new ServletException(e));
        } finally {
            releaseAllThreadDbResources();
        }
    }

    public static void releaseThreadLocalDbConnection() {
        AtomicReference<Connection> holder = dbConnection.get();
        try {
            Connection c = holder == null ? null : holder.getAndSet(null);
            SqlUtils.closeResources(c, null, null);
            if (c != null) {
                trackedConnections.remove(c);
            }
        } catch (Exception e) {
            logger.error("Error closing db connection.", e);
        } finally {
            if (holder != null) {
                trackedConnectionHolders.remove(holder);
            }
            dbConnection.remove();
        }
    }

    public static void releaseAllThreadDbResources() {
        releaseThreadResource("legacy thread-local database connection", DbConnectionFilter::releaseThreadLocalDbConnection);
        releaseThreadResource("legacy JDBC query resources", LegacyJdbcQuery::releaseThreadResources);
        releaseThreadResource("tracking data source connections", OscarTrackingBasicDataSource::releaseThreadConnections);
    }

    private static void releaseThreadResource(String resourceName, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException e) {
            logger.error("Error releasing {}", resourceName, e);
        }
    }

    /**
     * Releases all JDBC resources known to the legacy thread-local connection
     * tracker. This shutdown-only API first clears the current thread's resources,
     * then closes and removes every tracked raw connection. It is idempotent and
     * must not be used during normal request handling.
     *
     * @since 2026-05-21
     */
    public static void releaseAllKnownDbResources() {
        releaseAllThreadDbResources();
        AtomicReference<Connection>[] holders;
        synchronized (trackedConnectionHolders) {
            holders = trackedConnectionHolders.toArray(new AtomicReference[0]);
        }
        for (AtomicReference<Connection> holder : holders) {
            Connection c = holder.getAndSet(null);
            try {
                SqlUtils.closeResources(c, null, null);
            } finally {
                if (c != null) {
                    trackedConnections.remove(c);
                }
                trackedConnectionHolders.remove(holder);
            }
        }

        Connection[] connections;
        synchronized (trackedConnections) {
            connections = trackedConnections.toArray(new Connection[0]);
        }
        for (Connection c : connections) {
            try {
                SqlUtils.closeResources(c, null, null);
            } finally {
                trackedConnections.remove(c);
            }
        }
    }

    /**
     * This method should only be called by DbConnectionFilter internally, everyone else should use getThreadLocalDbConnection to obtain a connection.
     */
    private static Connection getDbConnection() throws SQLException {

        Connection c = ((DataSource) SpringUtils.getBean(DataSource.class)).getConnection();
        c.setAutoCommit(true);
        return (c);
    }

    public void destroy() {
        // can't think of anything to do right now.
    }
}
