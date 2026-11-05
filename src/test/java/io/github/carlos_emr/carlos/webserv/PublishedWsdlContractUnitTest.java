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
package io.github.carlos_emr.carlos.webserv;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.wsdl.Definition;
import javax.wsdl.factory.WSDLFactory;
import io.github.carlos_emr.carlos.utility.XmlUtils;
import org.apache.cxf.bus.extension.ExtensionManagerBus;
import org.apache.cxf.jaxws.support.JaxWsServiceFactoryBean;
import org.apache.cxf.service.Service;
import org.apache.cxf.wsdl11.ServiceWSDLBuilder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the published SOAP contracts against silent drift.
 *
 * <p>Every endpoint in {@code spring_ws.xml} serves a hand-captured WSDL from
 * {@code wsdl/published} (#5476), so {@code ?wsdl} no longer follows the implementor. This test
 * regenerates each service model with CXF from the implementor class and compares the parts of the
 * contract a client depends on: the operations, the global elements, and for every named complex
 * type the ordered child elements with their cardinality and type. Provider-specific schema
 * metadata (final-type flags, empty sequences, default attributes, {@code minOccurs} of primitive
 * fields) is deliberately not compared; see {@code docs/jaxb-provider-compatibility.md}.
 *
 * <p>When this fails after an intentional change, recapture the published WSDL from a running
 * instance and update its compatibility evidence together, as the document describes.
 */
@Tag("unit")
@Tag("xml")
class PublishedWsdlContractUnitTest {
    private static final String XSD = "http://www.w3.org/2001/XMLSchema";
    private static final String WSDL = "http://schemas.xmlsoap.org/wsdl/";
    private static final Pattern ENDPOINT = Pattern.compile(
            "implementor=\"#(\\w+)\"\\s+address=\"/(\\w+)\"\\s+wsdlLocation=\"classpath:(wsdl/published/\\w+\\.wsdl)\"");

    static Stream<Arguments> endpoints() throws Exception {
        String config;
        try (InputStream input = PublishedWsdlContractUnitTest.class.getResourceAsStream("/spring_ws.xml")) {
            assertThat(input).as("spring_ws.xml on the test class path").isNotNull();
            config = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<Arguments> endpoints = new ArrayList<>();
        Matcher matcher = ENDPOINT.matcher(config);
        while (matcher.find()) {
            String implementor = matcher.group(1);
            String className = PublishedWsdlContractUnitTest.class.getPackageName() + "."
                    + Character.toUpperCase(implementor.charAt(0)) + implementor.substring(1);
            endpoints.add(Arguments.of(matcher.group(2), Class.forName(className), matcher.group(3)));
        }
        assertThat(endpoints).as("endpoints declared with a published WSDL").hasSize(14);
        return endpoints.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void shouldMatchImplementorContract_forPublishedWsdl(String address, Class<?> implementor, String published) throws Exception {
        Document generated = generate(implementor);
        Document frozen;
        try (InputStream input = getClass().getResourceAsStream("/" + published)) {
            assertThat(input).as(published).isNotNull();
            var factory = XmlUtils.createSecureDocumentBuilderFactory();
            factory.setNamespaceAware(true);
            frozen = factory.newDocumentBuilder().parse(input);
        }
        assertThat(contract(generated)).as("contract of " + implementor.getSimpleName() + " vs " + published)
                .containsExactlyInAnyOrderEntriesOf(contract(frozen));
    }

    private static Document generate(Class<?> implementor) throws Exception {
        ExtensionManagerBus bus = new ExtensionManagerBus();
        try {
            JaxWsServiceFactoryBean factory = new JaxWsServiceFactoryBean();
            factory.setBus(bus);
            factory.setServiceClass(implementor);
            Service service = factory.create();
            Definition definition = new ServiceWSDLBuilder(bus, service.getServiceInfos().get(0)).build();
            return WSDLFactory.newInstance().newWSDLWriter().getDocument(definition);
        } finally {
            bus.shutdown(true);
        }
    }

    /** Operation names, global element declarations and named complex type structures. */
    private static Map<String, String> contract(Document wsdl) {
        Map<String, String> contract = new TreeMap<>();
        TreeSet<String> operations = new TreeSet<>();
        for (Element operation : elements(wsdl, WSDL, "operation")) {
            if (operation.getParentNode() instanceof Element parent && "portType".equals(parent.getLocalName())) {
                operations.add(operation.getAttribute("name"));
            }
        }
        contract.put("operations", operations.toString());
        for (Element element : elements(wsdl, XSD, "element")) {
            if (element.getParentNode() instanceof Element parent && "schema".equals(parent.getLocalName())) {
                contract.put("element " + element.getAttribute("name"), localType(element.getAttribute("type")));
            }
        }
        for (Element type : elements(wsdl, XSD, "complexType")) {
            if (!type.hasAttribute("name")) continue;
            List<String> children = new ArrayList<>();
            String base = null;
            for (Element element : descendants(type, XSD, "element")) {
                // minOccurs is provider metadata: MOXy declares a primitive such as DocumentTransfer's
                // char status required, Glassfish declares the adapter-mapped field optional, and both
                // always write it. Cardinality (single value or list) is still compared.
                children.add(element.getAttribute("name") + "[" + occurs(element, "maxOccurs", "1") + "]:"
                        + localType(element.getAttribute("type")));
            }
            for (Element extension : descendants(type, XSD, "extension")) {
                base = localType(extension.getAttribute("base"));
            }
            contract.put("complexType " + type.getAttribute("name"), (base == null ? "" : "extends " + base + " ") + children);
        }
        return contract;
    }

    private static String occurs(Element element, String attribute, String defaultValue) {
        return element.hasAttribute(attribute) ? element.getAttribute(attribute) : defaultValue;
    }

    /** Types compare by local name: the prefixes differ between the generated and frozen documents. */
    private static String localType(String qualified) {
        int colon = qualified.indexOf(':');
        return colon < 0 ? qualified : qualified.substring(colon + 1);
    }

    private static List<Element> elements(Document document, String namespace, String localName) {
        return toList(document.getElementsByTagNameNS(namespace, localName));
    }

    private static List<Element> descendants(Element parent, String namespace, String localName) {
        return toList(parent.getElementsByTagNameNS(namespace, localName));
    }

    private static List<Element> toList(NodeList nodes) {
        List<Element> result = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element) result.add(element);
        }
        return result;
    }
}
