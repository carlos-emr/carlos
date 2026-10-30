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

/**
 * Request-end safety net for legacy JDBC: after the request finishes, successfully or not, it
 * releases the thread's legacy connection ({@link #getThreadLocalDbConnection()}), any
 * {@link LegacyJdbcQuery} resources a caller left open and every connection still tracked by
 * {@link OscarTrackingBasicDataSource} for the thread.
 *
 * <p>The cleanup only works where the filter is mapped (issue #5305). {@code web.xml} maps it as
 * the first application filter, for the REQUEST dispatcher only, so it encloses every other
 * application filter, the Struts action, the JSP the action forwards to and any servlet, and
 * runs exactly once per request after all of them return. Struts executes an action without
 * calling the rest of the chain, so a mapping after {@code struts2-execute} never runs for
 * actions. A FORWARD, INCLUDE or ERROR mapping would run the cleanup inside a forward, while the
 * code that forwarded can still be using its resources.</p>
 *
 * <p>The tracked-connection cleanup closes connections outright, so it must run after any
 * transaction on the thread has finished: no filter inside this one may hold a transaction open
 * around {@code chain.doFilter}. {@link LegacyJdbcQuery} connections are released through
 * Spring's {@code DataSourceUtils}, which leaves a connection that an active transaction owns
 * to that transaction.</p>
 */
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
            if (ClientAbort.isClientAbort(e)) {
                // The browser went away mid-response (#4438). Not a server error: rethrow it unwrapped
                // and unlogged. ResponseSanitizationFilter, outermost in web.xml, handles it and logs it
                // once (DEBUG after commit, WARN before). With response.sanitization.enabled=false that
                // filter passes it through, and Tomcat logs the abort itself as before #4438.
                if (e instanceof IOException ioe) {
                    throw ioe;
                }
                if (e instanceof ServletException se) {
                    throw se;
                }
                if (e instanceof RuntimeException re) {
                    throw re;
                }
            }
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
