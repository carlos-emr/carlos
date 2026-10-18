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
package io.github.carlos_emr.carlos.webserv.oauth;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import jakarta.ws.rs.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every service published on {@code /ws/services} has a scope decision (#4419).
 *
 * <p>Reads the {@code restServices} server's service beans from {@code applicationContextREST.xml}, the file
 * that actually publishes them, takes each class's {@link Path} root, and asks {@link OAuthScopes} what a
 * call under that root requires. {@link OAuthScopes#UNMAPPED_ENDPOINT} for any of them fails the build: under
 * enforcement such a service would answer 403 to every token, so whoever adds one must map it or exempt it.
 */
@DisplayName("OAuthScopes covers every published /ws/services root")
@Tag("unit")
@Tag("security")
class OAuthScopesServiceMapUnitTest {

    private static final String JAXRS_NS = "http://cxf.apache.org/jaxrs";
    private static final String BEANS_NS = "http://www.springframework.org/schema/beans";

    @Test
    @DisplayName("should map or explicitly exempt the root of every published service")
    void shouldHaveScopeDecision_forEveryPublishedService() throws Exception {
        List<String> classes = publishedServiceClasses();
        assertThat(classes).as("restServices service beans").hasSizeGreaterThan(30);

        List<String> undecided = new ArrayList<>();
        for (String className : classes) {
            // Do not initialise: a few services have static state that expects a running context.
            Class<?> type = Class.forName(className, false, getClass().getClassLoader());
            Path path = type.getAnnotation(Path.class);
            assertThat(path).as("@Path on %s", className).isNotNull();
            String servicePath = "/services/" + path.value().replaceAll("^/+", "") + "/probe";
            for (String method : List.of("GET", "POST")) {
                if (OAuthScopes.UNMAPPED_ENDPOINT.equals(OAuthScopes.requiredScope(method, servicePath))) {
                    undecided.add(className + " (" + path.value() + ")");
                }
            }
        }
        assertThat(undecided).as("services with no scope decision in OAuthScopes").isEmpty();
    }

    @Test
    @DisplayName("should strip exactly the extension mappings the /services server declares")
    void shouldMatchExtensionMappings_ofServicesServer() throws Exception {
        // CXF strips a mapped extension before routing; if the server gained a mapping the resolver does
        // not know, "/demographics/1.csv" would route to /demographics/1 but resolve to a different domain.
        Element server = servicesServer();
        Element mappings = (Element) server.getElementsByTagNameNS(JAXRS_NS, "extensionMappings").item(0);
        assertThat(mappings).as("extensionMappings on the /services server").isNotNull();
        NodeList entries = mappings.getElementsByTagNameNS(BEANS_NS, "entry");
        List<String> declared = new ArrayList<>();
        for (int i = 0; i < entries.getLength(); i++) {
            declared.add("." + ((Element) entries.item(i)).getAttribute("key"));
        }
        assertThat(declared).containsExactlyInAnyOrderElementsOf(OAuthScopes.EXTENSION_MAPPING_SUFFIXES);
    }

    private static List<String> publishedServiceClasses() throws Exception {
        Element serviceBeans = (Element) servicesServer().getElementsByTagNameNS(JAXRS_NS, "serviceBeans").item(0);
        NodeList beans = serviceBeans.getElementsByTagNameNS(BEANS_NS, "bean");
        List<String> classes = new ArrayList<>();
        for (int j = 0; j < beans.getLength(); j++) {
            classes.add(((Element) beans.item(j)).getAttribute("class"));
        }
        return classes;
    }

    private static Element servicesServer() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc;
        try (InputStream in = OAuthScopesServiceMapUnitTest.class.getResourceAsStream("/applicationContextREST.xml")) {
            assertThat(in).as("applicationContextREST.xml on the classpath").isNotNull();
            doc = builder.parse(in);
        }
        NodeList servers = doc.getElementsByTagNameNS(JAXRS_NS, "server");
        for (int i = 0; i < servers.getLength(); i++) {
            Element server = (Element) servers.item(i);
            if ("/services".equals(server.getAttribute("address"))) {
                return server;
            }
        }
        throw new AssertionError("no jaxrs:server with address /services in applicationContextREST.xml");
    }
}
