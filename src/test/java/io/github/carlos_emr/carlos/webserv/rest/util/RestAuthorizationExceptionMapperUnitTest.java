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
package io.github.carlos_emr.carlos.webserv.rest.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
import io.github.carlos_emr.carlos.test.logging.LogCapture;

/**
 * The REST surfaces answer a privilege refusal with HTTP 403 (#2798): the mappers turn the
 * {@code SecurityException} / {@code AccessDeniedException} a guard throws into an empty 403, log
 * only the security object, and are registered on both CXF servers.
 *
 * @since 2026-10-08
 */
@Tag("unit")
@Tag("rest")
@DisplayName("REST authorization exception mappers")
class RestAuthorizationExceptionMapperUnitTest {

    @Test
    @DisplayName("should answer a missing-object SecurityException with an empty 403 and log the object")
    void shouldAnswer403_forMissingSecObjectSecurityException() {
        try (LogCapture log = LogCapture.forLogger(SecurityExceptionMapper.class)) {
            Response response = new SecurityExceptionMapper()
                    .toResponse(new SecurityException("missing required sec object (_newCasemgmt.DxRegistry)"));

            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.hasEntity()).isFalse();
            assertThat(log.messages()).containsExactly(
                    "REST request refused: missing required sec object (_newCasemgmt.DxRegistry)");
        }
    }

    @Test
    @DisplayName("should not log a SecurityException message outside the sec-object convention")
    void shouldOmitMessage_forUnconventionalSecurityException() {
        try (LogCapture log = LogCapture.forLogger(SecurityExceptionMapper.class)) {
            Response response = new SecurityExceptionMapper()
                    .toResponse(new SecurityException("access to demographic 12345 is locked"));

            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(log.messages()).containsExactly("REST request refused by a SecurityException");
            assertThat(String.join("\n", log.messages())).doesNotContain("12345");
        }
    }

    @Test
    @DisplayName("should answer AccessDeniedException with an empty 403 and log object and action, not the subject")
    void shouldAnswer403_forAccessDeniedException() {
        try (LogCapture log = LogCapture.forLogger(AccessDeniedExceptionMapper.class)) {
            Response response = new AccessDeniedExceptionMapper()
                    .toResponse(new AccessDeniedException("_rx", "w", 4321));

            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.hasEntity()).isFalse();
            assertThat(log.messages()).containsExactly("REST request refused: missing w privilege on _rx");
            assertThat(String.join("\n", log.messages())).doesNotContain("4321");
        }
    }

    @ParameterizedTest(name = "{0} server {1}")
    @CsvSource({"spring_ws.xml, /rs", "applicationContextREST.xml, /services"})
    @DisplayName("should register both mappers on each REST surface")
    void shouldRegisterBothMappers_onEachRestSurface(String config, String address) throws Exception {
        assertThat(providerClasses(config, address)).contains(
                SecurityExceptionMapper.class.getName(), AccessDeniedExceptionMapper.class.getName());
    }

    /** Bean classes declared inside the {@code jaxrs:providers} of the server at {@code address}. */
    private static List<String> providerClasses(String config, String address) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document document;
        try (InputStream in = RestAuthorizationExceptionMapperUnitTest.class.getClassLoader().getResourceAsStream(config)) {
            assertThat(in).as("%s on the test classpath", config).isNotNull();
            document = factory.newDocumentBuilder().parse(in);
        }
        NodeList servers = document.getElementsByTagName("jaxrs:server");
        for (int i = 0; i < servers.getLength(); i++) {
            Element server = (Element) servers.item(i);
            if (!address.equals(server.getAttribute("address"))) {
                continue;
            }
            List<String> classes = new ArrayList<>();
            NodeList providers = server.getElementsByTagName("jaxrs:providers");
            for (int p = 0; p < providers.getLength(); p++) {
                NodeList beans = ((Element) providers.item(p)).getElementsByTagName("bean");
                for (int b = 0; b < beans.getLength(); b++) {
                    classes.add(((Element) beans.item(b)).getAttribute("class"));
                }
            }
            return classes;
        }
        throw new AssertionError("no jaxrs:server with address " + address + " in " + config);
    }
}
