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
package io.github.carlos_emr.carlos.webserv.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Guards the CARLOS REST API surface registration against drift between the two
 * CXF JAX-RS servers that publish the {@code io.github.carlos_emr.carlos.webserv.rest}
 * data services:
 *
 * <ul>
 *   <li>the session surface {@code /ws/rs} (the {@code jaxrServer} server in
 *       {@code spring_ws.xml}), and</li>
 *   <li>the OAuth surface {@code /ws/services} (the {@code restServices} server in
 *       {@code applicationContextREST.xml}).</li>
 * </ul>
 *
 * <p>Issue #2961: {@code DocumentService} was registered only on the OAuth surface,
 * so the same document operation 404'd on the session surface. These tests assert
 * the document service is reachable on both surfaces and, more generally, that the
 * two surfaces stay consistent (aside from the OAuth-only status endpoint).
 *
 * @since 2026-06-25
 */
@DisplayName("REST service surface registration Tests")
@Tag("unit")
@Tag("rest")
class RestServiceSurfaceRegistrationUnitTest {

    private static final String SESSION_CONFIG = "spring_ws.xml";
    private static final String OAUTH_CONFIG = "applicationContextREST.xml";

    /** CXF JAX-RS server addresses that publish the REST data services on each surface. */
    private static final String SESSION_SERVER_ADDRESS = "/rs";
    private static final String OAUTH_SERVER_ADDRESS = "/services";

    private static final String REST_PACKAGE_PREFIX = "io.github.carlos_emr.carlos.webserv.rest.";
    private static final String DOCUMENT_SERVICE = REST_PACKAGE_PREFIX + "DocumentService";

    /** OAuth-status endpoint is intentionally OAuth-surface only. */
    private static final String OAUTH_ONLY_SERVICE =
            "io.github.carlos_emr.carlos.webserv.oauth.OAuthStatusService";

    /** The session {@code /ws/rs} surface must publish DocumentService (the gap fixed by #2961). */
    @Test
    @DisplayName("should register DocumentService on the session surface (/ws/rs)")
    void shouldRegisterDocumentService_onSessionSurface() throws Exception {
        assertThat(serviceBeanClasses(SESSION_CONFIG, SESSION_SERVER_ADDRESS))
                .as("DocumentService must be reachable on the session /ws/rs surface, "
                        + "consistent with the OAuth /ws/services surface (issue #2961)")
                .contains(DOCUMENT_SERVICE);
    }

    /** The OAuth {@code /ws/services} surface must keep publishing DocumentService. */
    @Test
    @DisplayName("should register DocumentService on the OAuth surface (/ws/services)")
    void shouldRegisterDocumentService_onOAuthSurface() throws Exception {
        assertThat(serviceBeanClasses(OAUTH_CONFIG, OAUTH_SERVER_ADDRESS))
                .as("DocumentService must remain reachable on the OAuth /ws/services surface")
                .contains(DOCUMENT_SERVICE);
    }

    /** The session and OAuth surfaces must expose the identical set of rest-package data services. */
    @Test
    @DisplayName("should expose the same rest-package data services on both surfaces")
    void shouldExposeSameRestServices_onBothSurfaces() throws Exception {
        Set<String> session = restPackageServices(
                serviceBeanClasses(SESSION_CONFIG, SESSION_SERVER_ADDRESS));
        Set<String> oauth = restPackageServices(
                serviceBeanClasses(OAUTH_CONFIG, OAUTH_SERVER_ADDRESS));

        assertThat(session)
                .as("every rest-package data service on the OAuth surface must also be "
                        + "registered on the session surface")
                .containsAll(oauth);
        assertThat(oauth)
                .as("every rest-package data service on the session surface must also be "
                        + "registered on the OAuth surface")
                .containsAll(session);
    }

    /** Keep only beans in the rest data-service package (drops the OAuth-only status endpoint). */
    private static Set<String> restPackageServices(Set<String> all) {
        Set<String> out = new LinkedHashSet<>(all);
        out.removeIf(c -> !c.startsWith(REST_PACKAGE_PREFIX));
        out.remove(OAUTH_ONLY_SERVICE);
        return out;
    }

    /**
     * Collects the service classes registered under the {@code <jaxrs:serviceBeans>} of the
     * single {@code <jaxrs:server>} with the given {@code address} in the config. Both inline
     * {@code <bean class="...">} registrations and {@code <ref bean="id"/>} references (resolved
     * to the referenced top-level bean's {@code class}) are counted, so the parity check sees the
     * real registrations regardless of which form a service uses.
     */
    private static Set<String> serviceBeanClasses(String configPath, String serverAddress) throws Exception {
        Document doc = parse(configPath);
        Map<String, String> beanClassById = beanClassesById(doc);

        Element server = serverByAddress(doc, serverAddress);
        assertThat(server)
                .as("config %s must declare a <jaxrs:server> at address %s", configPath, serverAddress)
                .isNotNull();

        Set<String> classes = new LinkedHashSet<>();
        NodeList serviceBeansList = server.getElementsByTagName("jaxrs:serviceBeans");
        for (int i = 0; i < serviceBeansList.getLength(); i++) {
            Element serviceBeans = (Element) serviceBeansList.item(i);

            NodeList beans = serviceBeans.getElementsByTagName("bean");
            for (int j = 0; j < beans.getLength(); j++) {
                String clazz = ((Element) beans.item(j)).getAttribute("class");
                if (clazz != null && !clazz.isBlank()) {
                    classes.add(clazz.trim());
                }
            }

            NodeList refs = serviceBeans.getElementsByTagName("ref");
            for (int j = 0; j < refs.getLength(); j++) {
                String refId = ((Element) refs.item(j)).getAttribute("bean");
                String clazz = beanClassById.get(refId == null ? "" : refId.trim());
                if (clazz != null && !clazz.isBlank()) {
                    classes.add(clazz.trim());
                }
            }
        }
        return classes;
    }

    /** Finds the single {@code <jaxrs:server>} element whose {@code address} matches, or null. */
    private static Element serverByAddress(Document doc, String serverAddress) {
        NodeList servers = doc.getElementsByTagName("jaxrs:server");
        for (int i = 0; i < servers.getLength(); i++) {
            Element server = (Element) servers.item(i);
            if (serverAddress.equals(server.getAttribute("address"))) {
                return server;
            }
        }
        return null;
    }

    /** Maps every top-level {@code <bean id="..." class="...">} id to its class for ref resolution. */
    private static Map<String, String> beanClassesById(Document doc) {
        Map<String, String> byId = new HashMap<>();
        NodeList beans = doc.getElementsByTagName("bean");
        for (int i = 0; i < beans.getLength(); i++) {
            Element bean = (Element) beans.item(i);
            String id = bean.getAttribute("id");
            String clazz = bean.getAttribute("class");
            if (id != null && !id.isBlank() && clazz != null && !clazz.isBlank()) {
                byId.put(id.trim(), clazz.trim());
            }
        }
        return byId;
    }

    /** Parses a classpath config resource with a hardened, namespace-unaware DOM parser. */
    private static Document parse(String configPath) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setValidating(false);
        dbf.setNamespaceAware(false);
        // Defense-in-depth XML hardening — the inputs are trusted local config
        // files, but pinning secure-processing + disabling external entities
        // keeps the test robust across JAXP implementations.
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        db.setEntityResolver((publicId, systemId) ->
                new InputSource(new java.io.StringReader("")));

        try (InputStream in = RestServiceSurfaceRegistrationUnitTest.class.getClassLoader()
                .getResourceAsStream(configPath)) {
            if (in == null) {
                throw new java.io.FileNotFoundException("Resource not found on classpath: " + configPath);
            }
            return db.parse(in);
        }
    }
}
