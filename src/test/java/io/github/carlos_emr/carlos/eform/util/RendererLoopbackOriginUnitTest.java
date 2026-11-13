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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A render with no HTTP request reaches the application at 127.0.0.1 on the port of Tomcat's own
 * plain-HTTP connector, never at a host or port a client supplied.
 */
@Tag("unit")
@Tag("eform")
class RendererLoopbackOriginUnitTest {
    private MBeanServer server;

    @BeforeEach
    void createServer() {
        server = MBeanServerFactory.newMBeanServer();
    }

    /** A connector MBean answering with Tomcat's lower-case attribute names. */
    private record Connector(Map<String, Object> attributes) implements DynamicMBean {
        @Override
        public Object getAttribute(String name) throws AttributeNotFoundException {
            if (!attributes.containsKey(name)) throw new AttributeNotFoundException(name);
            return attributes.get(name);
        }

        @Override
        public void setAttribute(Attribute attribute) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AttributeList getAttributes(String[] names) {
            return new AttributeList();
        }

        @Override
        public AttributeList setAttributes(AttributeList list) {
            return new AttributeList();
        }

        @Override
        public Object invoke(String action, Object[] parameters, String[] signature) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MBeanInfo getMBeanInfo() {
            return new MBeanInfo(Connector.class.getName(), "test connector", null, null, null, null);
        }
    }

    private void connector(String name, Map<String, Object> attributes) throws Exception {
        server.registerMBean(new Connector(attributes), new ObjectName(name));
    }

    @Test
    void shouldUseThePackagedLoopbackConnector_forARenderWithoutARequest() throws Exception {
        connector(
                "Catalina:type=Connector,port=18080,address=\"/127.0.0.1\"",
                Map.of("protocol", "HTTP/1.1", "scheme", "http", "secure", false, "localPort", 18080));

        int port = RendererLoopbackOrigin.connectorPort(server);

        assertThat(port).isEqualTo(18080);
        assertThat(EFormBrowserPdfService.buildRequestlessBaseUrl(port, "/carlos", "ignored"))
                .isEqualTo("http://127.0.0.1:18080/carlos");
    }

    @Test
    void shouldIgnoreAjpTlsAndForeignAddressConnectors_whenChoosingThePort() throws Exception {
        connector(
                "Catalina:type=Connector,port=8009",
                Map.of("protocol", "AJP/1.3", "scheme", "http", "secure", false, "localPort", 8009));
        connector(
                "Catalina:type=Connector,port=8443",
                Map.of("protocol", "HTTP/1.1", "scheme", "https", "secure", true, "localPort", 8443));
        connector(
                "Catalina:type=Connector,port=9090,address=\"/10.0.0.5\"",
                Map.of("protocol", "HTTP/1.1", "scheme", "http", "secure", false, "localPort", 9090));
        connector(
                "Catalina:type=Connector,port=8080",
                Map.of("protocol", "HTTP/1.1", "scheme", "http", "secure", false, "localPort", 8080));

        assertThat(RendererLoopbackOrigin.connectorPort(server)).isEqualTo(8080);
    }

    @Test
    void shouldKeepTheLegacyDefault_whenTwoPlainConnectorsCouldServeTheRender() throws Exception {
        connector(
                "Catalina:type=Connector,port=8080",
                Map.of("protocol", "HTTP/1.1", "scheme", "http", "secure", false, "localPort", 8080));
        connector(
                "Catalina:type=Connector,port=18080",
                Map.of("protocol", "HTTP/1.1", "scheme", "http", "secure", false, "localPort", 18080));

        int port = RendererLoopbackOrigin.connectorPort(server);

        assertThat(port).isEqualTo(-1);
        assertThat(EFormBrowserPdfService.buildRequestlessBaseUrl(port, "/carlos", "carlos"))
                .isEqualTo("http://127.0.0.1:8080/carlos");
    }

    @Test
    void shouldFallBackToProjectHome_whenNoServletContextIsKnown() {
        assertThat(EFormBrowserPdfService.buildRequestlessBaseUrl(18080, null, "/carlos/"))
                .isEqualTo("http://127.0.0.1:18080/carlos");
        assertThat(EFormBrowserPdfService.buildRequestlessBaseUrl(80, "", null))
                .isEqualTo("http://127.0.0.1");
        assertThat(RendererLoopbackOrigin.connectorPort(null)).isEqualTo(-1);
    }

    @Test
    void shouldAcceptOnlyAddressesThat127001Reaches_forBoundConnectors() {
        assertThat(RendererLoopbackOrigin.reachableFromLoopback(null)).isTrue();
        assertThat(RendererLoopbackOrigin.reachableFromLoopback("\"/0.0.0.0\"")).isTrue();
        assertThat(RendererLoopbackOrigin.reachableFromLoopback("\"localhost/127.0.0.1\"")).isTrue();
        assertThat(RendererLoopbackOrigin.reachableFromLoopback("\"/::\"")).isTrue();
        assertThat(RendererLoopbackOrigin.reachableFromLoopback("\"/::1\"")).isFalse();
        assertThat(RendererLoopbackOrigin.reachableFromLoopback("\"/127.0.0.2\"")).isFalse();
        assertThat(RendererLoopbackOrigin.reachableFromLoopback("\"clinic.example/192.0.2.7\""))
                .isFalse();
    }
}
