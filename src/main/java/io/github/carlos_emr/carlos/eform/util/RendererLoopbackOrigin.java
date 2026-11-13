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
package io.github.carlos_emr.carlos.eform.util;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Where a render with no HTTP request (the CDS export worker, a scheduled job) reaches this
 * application over loopback.
 *
 * <p>A request-driven render takes the local port and context path from the request it is
 * serving. A request-less render has neither, and used to fall back to
 * {@code http://127.0.0.1:8080/<project_home>}, which is wrong on the packaged install (Tomcat
 * listens on 127.0.0.1:18080). The port is now read from the servlet container's own
 * configuration: the one plain-HTTP, non-AJP Tomcat connector that 127.0.0.1 can reach, as Tomcat
 * publishes it over JMX. Nothing here is ever taken from a client: the host is always
 * {@code 127.0.0.1}, the port comes from the server's connector and the context path from the
 * deployed application. When no single connector qualifies (none, several, TLS only, bound to a
 * non-loopback address) the caller keeps the legacy default, and {@code eform_pdf_browser_base_url}
 * still overrides every derivation.
 *
 * @since 2026-11-13
 */
final class RendererLoopbackOrigin {
    private RendererLoopbackOrigin() {}

    /**
     * The port of the single plain-HTTP connector reachable on 127.0.0.1, or {@code -1} when no
     * connector, or more than one distinct port, qualifies.
     *
     * @param server the MBean server Tomcat registers its connectors in; {@code null} gives -1
     */
    // IMPROPER_UNICODE: upper-casing the connector's literal protocol name to recognise AJP is a
    // case-insensitive protocol keyword compare, not user identity folding.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive match of the connector protocol keyword AJP; not user identity folding")
    static int connectorPort(MBeanServer server) {
        if (server == null) return -1;
        Set<ObjectName> names;
        try {
            names = server.queryNames(new ObjectName("*:type=Connector,*"), null);
        } catch (JMException | RuntimeException unavailable) {
            return -1;
        }
        var ports = new TreeSet<Integer>();
        for (ObjectName name : names) {
            try {
                Object protocol = attribute(server, name, "protocol");
                // AJP is not HTTP: a browser cannot speak to it.
                if (protocol != null
                        && String.valueOf(protocol).toUpperCase(Locale.ROOT).contains("AJP")) continue;
                Object scheme = attribute(server, name, "scheme");
                // A connector that terminates TLS, or claims to (secure=true behind a proxy), is
                // not used: the loopback hop is plain HTTP, and guessing would fail every render.
                if (scheme != null && !"http".equals(String.valueOf(scheme))) continue;
                if (Boolean.TRUE.equals(attribute(server, name, "secure"))) continue;
                if (!reachableFromLoopback(name.getKeyProperty("address"))) continue;
                int port = number(attribute(server, name, "localPort"));
                if (port < 1) port = number(attribute(server, name, "portWithOffset"));
                if (port < 1) port = number(attribute(server, name, "port"));
                if (port >= 1 && port <= 65_535) ports.add(port);
            } catch (JMException | RuntimeException skipped) {
                // An unreadable connector is not a candidate; the others still decide.
            }
        }
        return ports.size() == 1 ? ports.first() : -1;
    }

    /**
     * True when a connector bound to {@code rawAddress} accepts a connection to 127.0.0.1: no
     * address (every interface), a wildcard address, or 127.0.0.1 itself. Tomcat names a bound
     * connector by its {@code InetAddress}, quoted, so the value may read {@code "/127.0.0.1"} or
     * {@code "localhost/127.0.0.1"}.
     */
    // IMPROPER_UNICODE: lower-casing the connector's literal bind address canonicalises a host label
    // for comparison with fixed loopback and wildcard addresses; not user identity folding.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "Locale.ROOT lower-casing of the connector bind address for comparison with fixed loopback/wildcard addresses; not user identity folding")
    static boolean reachableFromLoopback(String rawAddress) {
        if (rawAddress == null || rawAddress.isBlank()) return true;
        String address = rawAddress.strip();
        if (address.length() >= 2 && address.startsWith("\"") && address.endsWith("\"")) {
            try {
                address = ObjectName.unquote(address);
            } catch (IllegalArgumentException malformed) {
                return false;
            }
        }
        int slash = address.lastIndexOf('/');
        if (slash >= 0) address = address.substring(slash + 1);
        if (address.isBlank()) return true;
        String host = address.strip().toLowerCase(Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
        // The render always dials 127.0.0.1: a connector on every interface or on 127.0.0.1 itself
        // answers it; one bound only to another loopback address (127.0.0.2, ::1) does not.
        return Set.of("0.0.0.0", "::", "0:0:0:0:0:0:0:0", "127.0.0.1", "localhost").contains(host);
    }

    private static Object attribute(MBeanServer server, ObjectName name, String attribute)
            throws JMException {
        try {
            return server.getAttribute(name, attribute);
        } catch (javax.management.AttributeNotFoundException absent) {
            return null;
        }
    }

    private static int number(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value == null) return -1;
        try {
            return Integer.parseInt(String.valueOf(value).strip());
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }
}
