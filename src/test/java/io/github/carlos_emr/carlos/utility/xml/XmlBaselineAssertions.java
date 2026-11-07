/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.utility.xml;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.utility.XmlUtils;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import java.io.StringReader;
import java.io.StringWriter;
import javax.xml.namespace.QName;
import javax.xml.transform.stream.StreamSource;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import static org.assertj.core.api.Assertions.assertThat;

/** Compares new JAXB output to the independently captured, committed MOXy oracle. */
public final class XmlBaselineAssertions {
    private XmlBaselineAssertions() { }

    public static <T> T assertBaselineRoundTrip(Class<T> type, String sample) throws Exception {
        String expected = baseline(type, sample);
        var context = JAXBContext.newInstance(type);
        T value = context.createUnmarshaller().unmarshal(new StreamSource(new StringReader(expected)), type).getValue();
        assertBaselineWrite(type, value, sample);
        return value;
    }

    public static <T> T readBaseline(Class<T> type, String sample) throws Exception {
        return JAXBContext.newInstance(type).createUnmarshaller().unmarshal(
                new StreamSource(new StringReader(baseline(type, sample))), type).getValue();
    }

    public static <T> void assertBaselineWrite(Class<T> type, T value, String sample) throws Exception {
        String expected = baseline(type, sample);
        var context = JAXBContext.newInstance(type);
        var writer = new StringWriter();
        context.createMarshaller().marshal(new JAXBElement<>(new QName("urn:carlos:jaxb-compatibility", "value"), type, value), writer);
        var factory = XmlUtils.createSecureDocumentBuilderFactory();
        factory.setNamespaceAware(true);
        var wanted = factory.newDocumentBuilder().parse(new InputSource(new StringReader(expected)));
        var actual = factory.newDocumentBuilder().parse(new InputSource(new StringReader(writer.toString())));
        assertThat(tree(actual.getDocumentElement())).isEqualTo(tree(wanted.getDocumentElement()));
    }

    private static String baseline(Class<?> type, String sample) throws Exception {
        try (var input = type.getResourceAsStream("/xml/jaxb-moxy-baseline.json")) {
            return new ObjectMapper().readTree(input).get(type.getName()).get(sample).asText();
        }
    }

    private static String tree(Node node) {
        if (node.getNodeType() == Node.TEXT_NODE) return node.getNodeValue();
        var result = new StringBuilder("{").append(node.getNamespaceURI()).append('}').append(node.getLocalName()).append('[');
        var attributes = node.getAttributes();
        if (attributes != null) {
            var sorted = new java.util.TreeMap<String, String>();
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attribute = attributes.item(i);
                if (!"http://www.w3.org/2000/xmlns/".equals(attribute.getNamespaceURI())) {
                    sorted.put("{" + attribute.getNamespaceURI() + "}" + attribute.getLocalName(), attribute.getNodeValue());
                }
            }
            result.append(sorted);
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) result.append(tree(child));
        return result.append(']').toString();
    }
}
