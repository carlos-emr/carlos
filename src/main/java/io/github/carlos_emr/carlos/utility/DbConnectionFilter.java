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

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import javax.sql.DataSource;

import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.carlos.util.SqlUtils;

/**
 * Servlet filter that manages thread-local JDBC connections and ensures all
 * database resources are released after each request.
 *
 * <p>On each request, the filter chain executes normally and then the {@code finally}
 * block calls {@link #releaseAllThreadDbResources()} to close any thread-local
 * JDBC connections, Hibernate sessions, and tracked data source connections.
 *
 * @deprecated New code should use JPA with {@code EntityManager} and Spring-managed
 *             transactions instead of raw JDBC connections via this filter.
 * @since 2026-03-17
 */
public class DbConnectionFilter implements jakarta.servlet.Filter {
    private static final Logger logger = MiscUtils.getLogger();

    private static ThreadLocal<Connection> dbConnection = new ThreadLocal<Connection>();

    /**
     * Returns the thread-local JDBC connection, creating one if necessary.
     *
     * @return Connection the thread-local database connection
     * @throws SQLException if a connection cannot be obtained
     * @deprecated Use JPA with {@code EntityManager} and native queries instead of raw JDBC.
     */
    @Deprecated
    public static Connection getThreadLocalDbConnection() throws SQLException {
        Connection c = dbConnection.get();
        if (c == null || c.isClosed()) {
            c = getDbConnection();
            dbConnection.set(c);
        }

        return (c);
    }

    /**
     * Initializes the filter and logs startup.
     *
     * @param filterConfig FilterConfig the filter configuration
     * @throws ServletException if initialization fails
     */
    public void init(FilterConfig filterConfig) throws ServletException {
        logger.info("Starting Filter : " + getClass().getSimpleName());
    }

    /**
     * Executes the filter chain and releases all thread-local database resources afterward.
     *
     * @param tmpRequest  ServletRequest the servlet request
     * @param tmpResponse ServletResponse the servlet response
     * @param chain       FilterChain the filter chain
     * @throws IOException      if an I/O error occurs
     * @throws ServletException if a servlet error occurs
     */
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

    /**
     * Closes and removes the thread-local JDBC connection, if one exists.
     */
    public static void releaseThreadLocalDbConnection() {
        try {
            Connection c = dbConnection.get();
            SqlUtils.closeResources(c, null, null);
            dbConnection.remove();
        } catch (Exception e) {
            logger.error("Error closing db connection.", e);
        }
    }

    /**
     * Releases all thread-local database resources: the raw JDBC connection,
     * Hibernate sessions, and tracked data source connections.
     */
    public static void releaseAllThreadDbResources() {
        releaseThreadLocalDbConnection();
        SpringHibernateLocalSessionFactoryBean.releaseThreadSessions();
        OscarTrackingBasicDataSource.releaseThreadConnections();
    }

    /**
     * This method should only be called by DbConnectionFilter internally, everyone else should use getThreadLocalDbConnection to obtain a connection.
     */
    private static Connection getDbConnection() throws SQLException {

        Connection c = ((DataSource) SpringUtils.getBean(DataSource.class)).getConnection();
        c.setAutoCommit(true);
        return (c);
    }

    /**
     * Called when the filter is taken out of service. No cleanup is required.
     */
    public void destroy() {
        // can't think of anything to do right now.
    }
}
