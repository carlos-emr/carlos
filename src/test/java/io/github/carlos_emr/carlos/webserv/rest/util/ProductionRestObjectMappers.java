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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider;
import io.github.carlos_emr.carlos.utility.XmlUtils;
import jakarta.ws.rs.core.MediaType;
import org.springframework.beans.factory.config.MethodInvokingFactoryBean;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.ClassPathResource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * The JSON {@link ObjectMapper}s the two deployed REST surfaces use, built from their own Spring
 * XML rather than copied into a test.
 *
 * <p>Only the JSON provider, its mapper, and whatever configures that mapper are loaded, so the
 * CXF servers, DAOs and services in the same files are never created. "Whatever configures it"
 * matters on the OAuth surface: {@code SmartDateModule} is registered by an anonymous
 * {@link MethodInvokingFactoryBean} that calls {@code registerModule} on the mapper. A loader that
 * kept only the beans with ids would return a mapper without the module, and would then pass a
 * date-shape test that the deployed mapper fails (issue #3877).
 *
 * <p>The mappers are built once per JVM and are thread-safe once configured, as Jackson documents.
 *
 * @since 2026-10-19
 */
public final class ProductionRestObjectMappers {

    private static volatile ObjectMapper oauthSurface;
    private static volatile ObjectMapper sessionSurface;

    private ProductionRestObjectMappers() {
    }

    /**
     * The OAuth {@code /ws/services} mapper (applicationContextREST.xml), with
     * {@code SmartDateModule}. This is the surface third-party integrations call.
     *
     * @return the mapper {@code oauthJsonProvider} hands to CXF
     */
    public static ObjectMapper oauthSurface() {
        ObjectMapper mapper = oauthSurface;
        if (mapper == null) {
            synchronized (ProductionRestObjectMappers.class) {
                mapper = oauthSurface;
                if (mapper == null) {
                    mapper = load("applicationContextREST.xml", "oauthJacksonObjectMapper", "oauthJsonProvider");
                    oauthSurface = mapper;
                }
            }
        }
        return mapper;
    }

    /**
     * The session {@code /ws/rs} mapper (spring_ws.xml), which does not register
     * {@code SmartDateModule} and so writes unannotated dates as epoch milliseconds.
     *
     * @return the mapper {@code jsonProvider} hands to CXF
     */
    public static ObjectMapper sessionSurface() {
        ObjectMapper mapper = sessionSurface;
        if (mapper == null) {
            synchronized (ProductionRestObjectMappers.class) {
                mapper = sessionSurface;
                if (mapper == null) {
                    mapper = load("spring_ws.xml", "jacksonObjectMapper", "jsonProvider");
                    sessionSurface = mapper;
                }
            }
        }
        return mapper;
    }

    private static ObjectMapper load(String resourceName, String mapperId, String providerId) {
        ClassPathResource resource = new ClassPathResource(resourceName);
        Document document = parse(resource);
        Element root = document.getDocumentElement();
        retainMapperGraph(root, mapperId, providerId);

        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        new XmlBeanDefinitionReader(factory).registerBeanDefinitions(document, resource);
        // Instantiating every retained singleton runs the MethodInvokingFactoryBean, which is what
        // registers SmartDateModule on the OAuth mapper.
        factory.preInstantiateSingletons();
        JacksonJsonProvider provider = factory.getBean(providerId, JacksonJsonProvider.class);
        ObjectMapper mapper = provider.locateMapper(Object.class, MediaType.APPLICATION_JSON_TYPE);
        if (mapper != factory.getBean(mapperId, ObjectMapper.class)) {
            throw new IllegalStateException(providerId + " in " + resourceName + " does not use " + mapperId);
        }
        // The factory holds nothing the mapper needs after configuration.
        factory.destroySingletons();
        return mapper;
    }

    private static Document parse(ClassPathResource resource) {
        try (InputStream stream = resource.getInputStream()) {
            var parser = XmlUtils.createSecureDocumentBuilderFactory();
            parser.setNamespaceAware(true);
            return parser.newDocumentBuilder().parse(stream);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + resource.getPath(), e);
        } catch (ParserConfigurationException | SAXException e) {
            throw new IllegalStateException("cannot parse " + resource.getPath(), e);
        }
    }

    /**
     * Removes every top-level element except the provider, the mapper, the
     * {@link MethodInvokingFactoryBean}s that target the mapper, and the beans those reference
     * (transitively), e.g. the {@code smartDateModule} bean the registration passes in.
     */
    private static void retainMapperGraph(Element root, String mapperId, String providerId) {
        Set<String> wanted = new HashSet<>(Set.of(mapperId, providerId));
        Set<Element> kept = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>(wanted);
        while (!pending.isEmpty()) {
            String id = pending.pop();
            for (Node child = root.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof Element element && isBean(element)
                        && (id.equals(element.getAttribute("id")) || configures(element, id))
                        && kept.add(element)) {
                    for (String ref : referencedBeanIds(element)) {
                        if (wanted.add(ref)) {
                            pending.push(ref);
                        }
                    }
                }
            }
        }
        for (Node child = root.getFirstChild(); child != null; ) {
            Node next = child.getNextSibling();
            if (!(child instanceof Element element && kept.contains(element))) {
                root.removeChild(child);
            }
            child = next;
        }
    }

    private static boolean isBean(Element element) {
        return "bean".equals(element.getLocalName());
    }

    /**
     * A MethodInvokingFactoryBean whose targetObject is the given bean, written either as
     * {@code <property name="targetObject" ref="id"/>} or with a nested {@code <ref bean="id"/>}.
     */
    private static boolean configures(Element bean, String id) {
        if (!MethodInvokingFactoryBean.class.getName().equals(bean.getAttribute("class"))) {
            return false;
        }
        for (Node child = bean.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element property && "property".equals(property.getLocalName())
                    && "targetObject".equals(property.getAttribute("name"))
                    && (id.equals(property.getAttribute("ref")) || referencedBeanIds(property).contains(id))) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> referencedBeanIds(Element element) {
        Set<String> ids = new HashSet<>();
        Deque<Element> stack = new ArrayDeque<>();
        stack.push(element);
        while (!stack.isEmpty()) {
            Element current = stack.pop();
            if (!current.getAttribute("ref").isEmpty()) {
                ids.add(current.getAttribute("ref"));
            }
            if ("ref".equals(current.getLocalName()) && !current.getAttribute("bean").isEmpty()) {
                ids.add(current.getAttribute("bean"));
            }
            for (Node child = current.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof Element nested) {
                    stack.push(nested);
                }
            }
        }
        return ids;
    }
}
