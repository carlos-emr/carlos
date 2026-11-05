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

import org.mariadb.jdbc.Configuration;
import org.mariadb.jdbc.export.HaMode;
import java.sql.SQLException;
import java.util.Properties;
import org.apache.commons.dbcp2.ConnectionFactory;
import org.apache.commons.dbcp2.DriverConnectionFactory;

/** Per-factory network policy for controlled CDS connections; ordinary default-off pools are unchanged. */
public final class CdsJdbcConnectionTimeouts {
    private static final String CONNECT_TIMEOUT = "connectTimeout";
    private static final String SOCKET_TIMEOUT = "socketTimeout";
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
        Properties original=configured.getProperties();
        Properties copy=new Properties();
        for (String name : original.stringPropertyNames()) copy.setProperty(name, original.getProperty(name));
        String url=configured.getConnectionString();
        if (url==null || !(url.startsWith("jdbc:mysql://") || url.startsWith("jdbc:mariadb://"))) {
            throw singleHostRequired();
        }
        try {
            if (hasDnsSrv(url, copy)) throw singleHostRequired();
            // MariaDB does not interpret MySQL's per-host timeout options. For the
            // only supported topology (one host), retain their highest precedence.
            Properties hostTimeouts=new Properties();
            url=stripHostTimeouts(url, hostTimeouts);
            var settings=MariaDbJdbcCompatibility.settings(url, copy);
            Properties effective=(Properties) settings.properties().clone();
            // parse() mutates its Properties argument. The shared compatibility
            // layer has already decoded and merged URL and explicit properties.
            Configuration resolved=Configuration.parse(settings.url(), (Properties) effective.clone());
            if (resolved==null || resolved.haMode()!=HaMode.NONE || resolved.addresses().size()!=1) {
                throw singleHostRequired();
            }
            effective.putAll(hostTimeouts);
            String connect=bounded(effective, CONNECT_TIMEOUT, DEFAULT_CONNECT_MILLIS, MAX_CONNECT_MILLIS);
            String socket=bounded(effective, SOCKET_TIMEOUT, DEFAULT_SOCKET_MILLIS, MAX_SOCKET_MILLIS);
            copy.keySet().removeIf(key -> MariaDbJdbcCompatibility.asciiEquals(CONNECT_TIMEOUT, key.toString())
                    || MariaDbJdbcCompatibility.asciiEquals(SOCKET_TIMEOUT, key.toString()));
            copy.setProperty(CONNECT_TIMEOUT, connect);
            copy.setProperty(SOCKET_TIMEOUT, socket);
            // Both native and compatibility drivers must observe the finite values.
            url=MariaDbJdbcCompatibility.normalizeUrl(url);
            url+=(url.contains("?") ? "&" : "?")+"connectTimeout="+connect+"&socketTimeout="+socket;
            return new DriverConnectionFactory(configured.getDriver(), url, copy);
        } catch (SQLException malformed) {
            if ("CDS_JDBC_SINGLE_HOST_REQUIRED".equals(malformed.getMessage())) throw singleHostRequired();
            throw invalid();
        } catch (RuntimeException _) {
            // URL/property contents and credentials must not enter ordinary framework logs.
            throw invalid();
        }
    }

    /** Retain the single host's timeout overrides before passing its descriptor to MariaDB. */
    private static String stripHostTimeouts(String url, Properties hostTimeouts) throws SQLException {
        int authorityStart=url.indexOf("//")+2;
        int authorityEnd=url.indexOf('/', authorityStart);
        int queryStart=url.indexOf('?', authorityStart);
        if (authorityEnd<0 || queryStart>=0 && queryStart<authorityEnd) authorityEnd=queryStart;
        if (authorityEnd<0) authorityEnd=url.length();
        String authority=url.substring(authorityStart, authorityEnd);
        if (authority.contains(",")) throw singleHostRequired();
        var descriptor=java.util.regex.Pattern.compile("\\((connectTimeout|socketTimeout)=([^()]*)\\)")
                .matcher(authority);
        StringBuilder stripped=new StringBuilder();
        while (descriptor.find()) {
            hostTimeouts.setProperty(descriptor.group(1), descriptor.group(2));
            descriptor.appendReplacement(stripped, "");
        }
        descriptor.appendTail(stripped);
        return url.substring(0, authorityStart)+stripped+url.substring(authorityEnd);
    }

    private static boolean hasDnsSrv(String url, Properties properties) {
        for (String key : properties.stringPropertyNames()) {
            if (MariaDbJdbcCompatibility.asciiEquals("dnsSrv", key) && Boolean.parseBoolean(properties.getProperty(key))) return true;
        }
        int query=url.indexOf('?');
        if (query>=0) {
            for (String parameter : url.substring(query+1).split("&")) {
                String[] pair=parameter.split("=", 2);
                if (pair.length==2 && MariaDbJdbcCompatibility.asciiEquals("dnsSrv", java.net.URLDecoder.decode(pair[0], java.nio.charset.StandardCharsets.UTF_8))
                        && Boolean.parseBoolean(java.net.URLDecoder.decode(pair[1], java.nio.charset.StandardCharsets.UTF_8))) return true;
            }
        }
        return false;
    }

    private static String bounded(Properties effective, String key, int fallback, int maximum) throws SQLException {
        String configured=effective.getProperty(key);
        if (configured==null) return Integer.toString(fallback);
        final int value;
        try { value=Integer.parseInt(configured); }
        catch (NumberFormatException _) { throw invalid(); }
        if (value<1 || value>maximum) throw invalid();
        return Integer.toString(value);
    }

    private static SQLException singleHostRequired() { return new SQLException("CDS_JDBC_SINGLE_HOST_REQUIRED", "08001"); }

    private static SQLException invalid() { return new SQLException("CDS_JDBC_TIMEOUT_CONFIGURATION_INVALID", "08001"); }
}
