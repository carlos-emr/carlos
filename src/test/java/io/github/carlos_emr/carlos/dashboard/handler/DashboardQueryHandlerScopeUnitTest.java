/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.dashboard.handler;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.dashboard.query.Parameter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * The dashboard query handlers keep one request's parameters, query and rows in fields (#3947).
 * As singletons, two users running an indicator, drilldown or export at once could overwrite each
 * other's state and see each other's patient rows. Each lookup must therefore get its own instance.
 *
 * <p>The bean definitions are read from the real {@code applicationContext.xml} (only these three
 * elements, so no database or other bean is needed).</p>
 */
@Tag("unit")
@Tag("dashboard")
@DisplayName("Dashboard query handler scope")
class DashboardQueryHandlerScopeUnitTest {

    private static final List<String> HANDLERS =
            List.of("drilldownQueryHandler", "indicatorQueryHandler", "exportQueryHandler");

    /** applicationContext.xml with every element removed except the three handler beans. */
    private static Document handlerBeans() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Document document;
        try (InputStream input = DashboardQueryHandlerScopeUnitTest.class.getResourceAsStream("/applicationContext.xml")) {
            assertThat(input).isNotNull();
            document = factory.newDocumentBuilder().parse(input);
        }
        Element root = document.getDocumentElement();
        for (Node child = root.getFirstChild(); child != null; ) {
            Node next = child.getNextSibling();
            if (!(child instanceof Element element) || !HANDLERS.contains(element.getAttribute("id"))) {
                root.removeChild(child);
            }
            child = next;
        }
        return document;
    }

    private static DefaultListableBeanFactory handlerFactory() throws Exception {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        new XmlBeanDefinitionReader(factory).registerBeanDefinitions(handlerBeans(), null);
        return factory;
    }

    @Test
    void shouldDeclareEveryHandlerPerLookup_inTheApplicationContext() throws Exception {
        DefaultListableBeanFactory factory = handlerFactory();
        assertThat(factory.getBeanDefinitionNames()).containsExactlyInAnyOrderElementsOf(HANDLERS);
        for (String id : HANDLERS) {
            assertThat(factory.getBeanDefinition(id).isPrototype())
                    .as("%s must be prototype-scoped: it holds one request's state", id).isTrue();
        }
    }

    @Test
    void shouldGiveEachRequestItsOwnState_whenTwoLookupsOverlap() throws Exception {
        DefaultListableBeanFactory factory = handlerFactory();
        for (String id : HANDLERS) {
            // Two requests in flight at once, as two users opening the dashboard together.
            AbstractQueryHandler first = factory.getBean(id, AbstractQueryHandler.class);
            AbstractQueryHandler second = factory.getBean(id, AbstractQueryHandler.class);
            assertThat(second).as(id).isNotSameAs(first);

            List<Parameter> firstRequestParameters = new ArrayList<>();
            first.setParameters(firstRequestParameters);
            assertThat(second.getParameters()).as("%s: the second request must not see the first's parameters", id).isNull();
            assertThat(first.getParameters()).as(id).isSameAs(firstRequestParameters);
        }
    }

    @Test
    void shouldNotInjectAHandlerIntoAnotherBean_inAnySpringFile() throws IOException {
        // A prototype injected into a singleton is created once and shared again.
        Pattern reference = Pattern.compile("ref\\s*=\\s*\"(" + String.join("|", HANDLERS) + ")\"");
        List<Path> springFiles;
        try (Stream<Path> resources = Files.list(Path.of("src", "main", "resources"));
             Stream<Path> webInf = Files.list(Path.of("src", "main", "webapp", "WEB-INF"))) {
            springFiles = Stream.concat(resources, webInf).filter(path -> path.toString().endsWith(".xml")).toList();
        }
        assertThat(springFiles).isNotEmpty();
        for (Path file : springFiles) {
            assertThat(reference.matcher(Files.readString(file, StandardCharsets.UTF_8)).find())
                    .as("%s injects a dashboard query handler", file).isFalse();
        }
    }
}
