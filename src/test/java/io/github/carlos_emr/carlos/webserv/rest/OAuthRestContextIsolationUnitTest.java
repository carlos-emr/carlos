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
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider;
import io.github.carlos_emr.carlos.utility.XmlUtils;
import jakarta.ws.rs.core.MediaType;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.ClassPathResource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Guards the coexistence of the session REST context ({@code spring_ws.xml}, {@code /ws/rs}) and the
 * OAuth REST context ({@code applicationContextREST.xml}, {@code /ws/oauth} and {@code /ws/services})
 * in one Spring root context.
 *
 * <p>Issue #3446 made {@code OscarSpringContextLoader} load the OAuth file on every deployment, after
 * {@code spring_ws.xml}. Spring allows bean-definition overriding, so any bean id the two files
 * shared was silently replaced by the OAuth file's definition. Before the fix the OAuth file declared
 * {@code jaxb}, {@code jacksonObjectMapper} and {@code jsonProvider} again, and loading it switched the
 * session surface's JSON dates from epoch milliseconds to {@code yyyy-MM-dd} strings. A deployed
 * {@code /ws/rs/demographics/{id}} showed this for {@code patientStatusDate} and {@code dateJoined}.
 *
 * @since 2026-10-08
 */
@DisplayName("OAuth REST context isolation Tests")
@Tag("unit")
@Tag("rest")
class OAuthRestContextIsolationUnitTest {

    private static final String SESSION_CONFIG = "spring_ws.xml";
    private static final String OAUTH_CONFIG = "applicationContextREST.xml";

    /** The session surface's JSON provider bean, referenced by {@code jaxrServer} at {@code /rs}. */
    private static final String SESSION_JSON_PROVIDER = "jsonProvider";

    /** Bean classes that make up a surface's JSON provider graph (mapper, provider, date module). */
    private static final Set<String> JSON_GRAPH_CLASSES = Set.of(
            "com.fasterxml.jackson.databind.ObjectMapper",
            "com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider",
            "io.github.carlos_emr.carlos.webserv.rest.util.SmartDateModule",
            "org.springframework.beans.factory.config.MethodInvokingFactoryBean");

    @Test
    @DisplayName("should register the OAuth context after the session context without overriding any bean")
    void shouldNotOverrideSessionBeans_whenOAuthContextLoadsAfterSessionContext() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(factory);
        // Same order as the deployed root context: applicationContext.xml imports spring_ws.xml,
        // and OscarSpringContextLoader reads applicationContextREST.xml next. Only definitions
        // are registered; nothing is instantiated, so no database or servlet container is needed.
        reader.loadBeanDefinitions(new ClassPathResource(SESSION_CONFIG));

        factory.setAllowBeanDefinitionOverriding(false);
        assertThatCode(() -> reader.loadBeanDefinitions(new ClassPathResource(OAUTH_CONFIG)))
                .as("%s must not redefine a bean that %s (or a file it imports) already defines: "
                        + "the later definition would silently replace the session /ws/rs bean",
                        OAUTH_CONFIG, SESSION_CONFIG)
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should keep epoch-millisecond dates on /ws/rs and smart dates on /ws/services")
    void shouldKeepEachSurfaceDateFormat_whenBothContextsShareOneFactory() throws Exception {
        Document session = parse(SESSION_CONFIG);
        Document oauth = parse(OAUTH_CONFIG);
        String oauthJsonProvider = jsonProviderOf(oauth, "/services");

        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(factory);
        try {
            // Production order and production overriding rules (overriding allowed).
            reader.registerBeanDefinitions(keepJsonGraph(session), new ClassPathResource(SESSION_CONFIG));
            reader.registerBeanDefinitions(keepJsonGraph(oauth), new ClassPathResource(OAUTH_CONFIG));
            // Runs the MethodInvokingFactoryBean that registers SmartDateModule, as the root context does.
            factory.preInstantiateSingletons();

            Date midnight = new GregorianCalendar(2023, Calendar.JULY, 25).getTime();

            JsonNode sessionDate = serialize(factory, SESSION_JSON_PROVIDER, midnight);
            assertThat(sessionDate.isNumber())
                    .as("/ws/rs must keep serializing dates as epoch milliseconds, got %s", sessionDate)
                    .isTrue();
            assertThat(sessionDate.longValue()).isEqualTo(midnight.getTime());

            JsonNode oauthDate = serialize(factory, oauthJsonProvider, midnight);
            assertThat(oauthDate.isTextual())
                    .as("/ws/services must keep SmartDateModule's date-only strings, got %s", oauthDate)
                    .isTrue();
            assertThat(oauthDate.textValue()).isEqualTo("2023-07-25");
        } finally {
            factory.destroySingletons();
        }
    }

    private static JsonNode serialize(DefaultListableBeanFactory factory, String providerId, Date date)
            throws Exception {
        JacksonJsonProvider provider = factory.getBean(providerId, JacksonJsonProvider.class);
        ObjectMapper mapper = provider.locateMapper(Map.class, MediaType.APPLICATION_JSON_TYPE);
        Map<String, Object> payload = new HashMap<>();
        payload.put("dateJoined", date);
        return mapper.readTree(mapper.writeValueAsString(payload)).get("dateJoined");
    }

    /**
     * Returns the id of the {@code JacksonJsonProvider} bean the {@code <jaxrs:server>} at the given
     * address references, resolved through the file's top-level bean classes.
     */
    private static String jsonProviderOf(Document doc, String serverAddress) {
        Map<String, String> classById = new HashMap<>();
        NodeList beans = doc.getElementsByTagNameNS("*", "bean");
        for (int i = 0; i < beans.getLength(); i++) {
            Element bean = (Element) beans.item(i);
            if (bean.getParentNode() == doc.getDocumentElement() && !bean.getAttribute("id").isEmpty()) {
                classById.put(bean.getAttribute("id"), bean.getAttribute("class"));
            }
        }
        NodeList servers = doc.getElementsByTagNameNS("*", "server");
        for (int i = 0; i < servers.getLength(); i++) {
            Element server = (Element) servers.item(i);
            if (!serverAddress.equals(server.getAttribute("address"))) {
                continue;
            }
            NodeList refs = server.getElementsByTagNameNS("*", "ref");
            for (int j = 0; j < refs.getLength(); j++) {
                String id = ((Element) refs.item(j)).getAttribute("bean");
                if (JacksonJsonProvider.class.getName().equals(classById.get(id))) {
                    return id;
                }
            }
        }
        throw new AssertionError("no JacksonJsonProvider is referenced by the server at " + serverAddress);
    }

    /** Removes every top-level element except the beans of the JSON provider graph. */
    private static Document keepJsonGraph(Document doc) {
        Element root = doc.getDocumentElement();
        for (Node child = root.getFirstChild(); child != null; ) {
            Node next = child.getNextSibling();
            boolean keep = child instanceof Element element
                    && "bean".equals(element.getLocalName())
                    && JSON_GRAPH_CLASSES.contains(element.getAttribute("class"));
            if (!keep) {
                root.removeChild(child);
            }
            child = next;
        }
        return doc;
    }

    private static Document parse(String resourceName) throws Exception {
        var parser = XmlUtils.createSecureDocumentBuilderFactory();
        parser.setNamespaceAware(true);
        try (InputStream stream = new ClassPathResource(resourceName).getInputStream()) {
            return parser.newDocumentBuilder().parse(stream);
        }
    }
}
