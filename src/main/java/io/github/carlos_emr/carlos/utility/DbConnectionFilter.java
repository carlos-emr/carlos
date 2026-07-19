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

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.sql.DataSource;

import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.carlos.util.SqlUtils;

public class DbConnectionFilter implements javax.servlet.Filter {
    private static final Logger logger = MiscUtils.getLogger();

    private static ThreadLocal<Connection> dbConnection = new ThreadLocal<Connection>();

    /**
     * deprecated we should stop using raw jdbc connections. Don't write new code using raw jdbc, use JPA and native queries instead.
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
        try {
            Connection c = dbConnection.get();
            SqlUtils.closeResources(c, null, null);
            dbConnection.remove();
        } catch (Exception e) {
            logger.error("Error closing db connection.", e);
        }
    }

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

    public void destroy() {
        // can't think of anything to do right now.
    }
}
