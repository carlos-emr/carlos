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
        String expected = withPublishedMainContracts(type, baseline(type, sample));
        var context = JAXBContext.newInstance(type);
        var writer = new StringWriter();
        context.createMarshaller().marshal(new JAXBElement<>(new QName("urn:carlos:jaxb-compatibility", "value"), type, value), writer);
        var factory = XmlUtils.createSecureDocumentBuilderFactory();
        factory.setNamespaceAware(true);
        var wanted = factory.newDocumentBuilder().parse(new InputSource(new StringReader(expected)));
        var actual = factory.newDocumentBuilder().parse(new InputSource(new StringReader(writer.toString())));
        assertThat(tree(actual.getDocumentElement())).isEqualTo(tree(wanted.getDocumentElement()));
    }

    /**
     * Keeps the frozen MOXy corpus as legacy input while pinning the published main birthday output.
     * Main e53e6cb5551 selected Glassfish and wrote xs:dateTime here; MOXy's date-only output was
     * never a valid replacement for that schema contract. The independent main/release findings
     * retain the exact observation. Other calendar/date values remain byte-for-byte expectations.
     */
    public static String withPublishedMainBirthday(String legacyXml) {
        return legacyXml.replace("<birthDay>1980-07-15</birthDay>",
                "<birthDay>1980-07-15T00:00:00-04:00</birthDay>");
    }

    /**
     * Applies only the corrections independently validated against beta6 schemas in #5721.
     * The captured MOXy bytes remain unchanged as reader input. Character mappings, fractional
     * seconds and other unresolved compatibility differences are not normalized. The JSON-only edit
     * token is excluded from XML: independently published beta6 schemas contain no such element.
     */
    public static String withPublishedMainContracts(Class<?> type, String legacyXml) throws Exception {
        String expected = withPublishedMainBirthday(legacyXml);
        if (java.util.Set.of("AppointmentTo1", "SchedulingResponse").contains(type.getSimpleName())) {
            expected = expected.replace("<editVersion>7</editVersion>", "");
        }
        java.util.List<String> dateTimes = switch (type.getSimpleName()) {
            case "AppointmentTransfer", "AppointmentArchiveTransfer" -> java.util.List.of(
                    "appointmentStartDateTime", "appointmentEndDateTime", "createDateTime", "updateDateTime");
            case "CalendarScheduleCodePairTransfer", "DayWorkScheduleTransfer" -> java.util.List.of("date");
            case "LookupCodeValue", "NavbarResponse", "ProgramProviderTo1", "ProgramTo1", "TicklerTo1" -> java.util.List.of("lastUpdateDate");
            default -> java.util.List.of();
        };
        for (String property : dateTimes) {
                expected = expected.replace("<" + property + ">2026-07-15T13:14:15</" + property + ">",
                        "<" + property + ">2026-07-15T13:14:15-04:00</" + property + ">");
        }
        if (!java.util.Set.of("FilterDefinitionTransfer", "AppointmentExtTo").contains(type.getSimpleName())) return expected;
        var factory = XmlUtils.createSecureDocumentBuilderFactory();
        factory.setNamespaceAware(true);
        var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(expected)));
        var root = document.getDocumentElement();
        if (type == io.github.carlos_emr.carlos.webserv.rest.to.model.FilterDefinitionTransfer.class
                && root.getElementsByTagName("params").getLength() == 0) {
            root.appendChild(document.createElement("params"));
        }
        if (type == io.github.carlos_emr.carlos.webserv.rest.to.model.AppointmentExtTo.class) {
            var field = root.getElementsByTagName("hPhoneExt").item(0);
            var status = root.getElementsByTagName("status").item(0);
            if (field != null && status != null) root.insertBefore(field, status.getNextSibling());
        }
        var output = new StringWriter();
        XmlUtils.createSecureTransformerFactory().newTransformer().transform(
                new javax.xml.transform.dom.DOMSource(document), new javax.xml.transform.stream.StreamResult(output));
        return output.toString();
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
