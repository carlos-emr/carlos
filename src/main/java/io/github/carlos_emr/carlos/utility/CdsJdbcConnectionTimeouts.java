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

import com.mysql.cj.conf.ConnectionUrl;
import com.mysql.cj.conf.ConnectionUrlParser;
import java.sql.SQLException;
import java.util.Properties;
import org.apache.commons.dbcp2.ConnectionFactory;
import org.apache.commons.dbcp2.DriverConnectionFactory;

/** Per-factory network policy for controlled CDS connections; ordinary default-off pools are unchanged. */
public final class CdsJdbcConnectionTimeouts {
    public static final int DEFAULT_CONNECT_MILLIS=5000;
    public static final int DEFAULT_SOCKET_MILLIS=60000;
    public static final int MAX_CONNECT_MILLIS=30000;
    public static final int MAX_SOCKET_MILLIS=300000;

    private CdsJdbcConnectionTimeouts() { }

    /** A private advisory owner must survive its run budget plus a bounded network-unwind interval. */
    public static void retainOwnerForDuration(java.sql.Connection connection, long maximumMillis) throws SQLException {
        if (maximumMillis < 1) throw invalid();
        long seconds = (maximumMillis - 1) / 1000 + 1 + MAX_SOCKET_MILLIS / 1000;
        if (seconds > 31536000) throw invalid(); // MariaDB's maximum supported session idle interval.
        try (var change = connection.prepareStatement("SET SESSION wait_timeout=?")) {
            change.setQueryTimeout(3); change.setLong(1, seconds); change.execute();
        }
        try (var query = connection.prepareStatement("SELECT @@SESSION.wait_timeout")) {
            query.setQueryTimeout(3);
            try (var rows = query.executeQuery()) {
                if (!rows.next() || rows.getLong(1) < seconds || rows.wasNull() || rows.next()) throw invalid();
            }
        }
    }

    /** Clone the resolved driver, URL and credential properties; never mutate shared pool configuration. */
    public static ConnectionFactory bounded(ConnectionFactory factory) throws SQLException {
        if (!(factory instanceof DriverConnectionFactory configured)) throw invalid();
        Properties copy=(Properties) configured.getProperties().clone();
        try {
            // Resolve the actual shipped driver's query/property/per-host merge semantics.
            // Missing values alone receive bounded defaults.
            var parsed=ConnectionUrlParser.parseConnectionString(configured.getConnectionString());
            if (ConnectionUrl.Type.fromValue(parsed.getScheme(), parsed.getHosts().size())!=ConnectionUrl.Type.SINGLE_CONNECTION) {
                throw singleHostRequired();
            }
            var resolved=ConnectionUrl.getConnectionUrlInstance(configured.getConnectionString(), copy);
            if (resolved.getType()!=ConnectionUrl.Type.SINGLE_CONNECTION) throw singleHostRequired();
            Properties effective=resolved.getMainHost().exposeAsProperties();
            copy.setProperty("connectTimeout", bounded(effective, "connectTimeout", DEFAULT_CONNECT_MILLIS, MAX_CONNECT_MILLIS));
            copy.setProperty("socketTimeout", bounded(effective, "socketTimeout", DEFAULT_SOCKET_MILLIS, MAX_SOCKET_MILLIS));
            return new DriverConnectionFactory(configured.getDriver(), configured.getConnectionString(), copy);
        } catch (RuntimeException malformed) {
            // URL/property contents and credentials must not enter ordinary framework logs.
            throw invalid();
        }
    }

    private static String bounded(Properties effective, String key, int fallback, int maximum) throws SQLException {
        String configured=effective.getProperty(key);
        if (configured==null) return Integer.toString(fallback);
        final int value;
        try { value=Integer.parseInt(configured); }
        catch (NumberFormatException malformed) { throw invalid(); }
        if (value<1 || value>maximum) throw invalid();
        return Integer.toString(value);
    }

    private static SQLException singleHostRequired() { return new SQLException("CDS_JDBC_SINGLE_HOST_REQUIRED", "08001"); }

    private static SQLException invalid() { return new SQLException("CDS_JDBC_TIMEOUT_CONFIGURATION_INVALID", "08001"); }
}
