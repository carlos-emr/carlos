/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 *
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.util;

import org.junit.jupiter.api.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link UtilXML} XML document creation and serialization.
 *
 * @since 2026-03-31
 */
@DisplayName("UtilXML Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("utility")
class UtilXMLUnitTest {

    @Nested
    @DisplayName("newDocument")
    class NewDocument {

        @Test
        @DisplayName("should create non-null Document")
        void shouldCreateNonNullDocument() {
            Document doc = UtilXML.newDocument();
            assertThat(doc).isNotNull();
        }
    }

    @Nested
    @DisplayName("addNode")
    class AddNode {

        @Test
        @DisplayName("should add element to document root")
        void shouldAddElement_toDocumentRoot() {
            Document doc = UtilXML.newDocument();
            Element root = UtilXML.addNode(doc, "root");
            assertThat(root).isNotNull();
            assertThat(root.getTagName()).isEqualTo("root");
            assertThat(doc.getDocumentElement()).isSameAs(root);
        }

        @Test
        @DisplayName("should add child element with text value")
        void shouldAddChildElement_withTextValue() {
            Document doc = UtilXML.newDocument();
            Element root = UtilXML.addNode(doc, "root");
            Element child = UtilXML.addNode(root, "name", "John");
            assertThat(child.getTagName()).isEqualTo("name");
            assertThat(child.getTextContent()).isEqualTo("John");
        }

        @Test
        @DisplayName("should add child element without value")
        void shouldAddChildElement_withoutValue() {
            Document doc = UtilXML.newDocument();
            Element root = UtilXML.addNode(doc, "root");
            Element child = UtilXML.addNode(root, "empty");
            assertThat(child.getTagName()).isEqualTo("empty");
            assertThat(child.getTextContent()).isEmpty();
        }

        @Test
        @DisplayName("should add multiple children")
        void shouldAddMultipleChildren() {
            Document doc = UtilXML.newDocument();
            Element root = UtilXML.addNode(doc, "root");
            UtilXML.addNode(root, "a", "1");
            UtilXML.addNode(root, "b", "2");
            UtilXML.addNode(root, "c", "3");
            NodeList children = root.getChildNodes();
            assertThat(children.getLength()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("toXML")
    class ToXML {

        @Test
        @DisplayName("should serialize document to XML string")
        void shouldSerialize_toXmlString() {
            Document doc = UtilXML.newDocument();
            Element root = UtilXML.addNode(doc, "patient");
            UtilXML.addNode(root, "name", "Jane Doe");
            UtilXML.addNode(root, "age", "45");

            String xml = UtilXML.toXML(doc);

            assertThat(xml).contains("<patient>");
            assertThat(xml).contains("<name>Jane Doe</name>");
            assertThat(xml).contains("<age>45</age>");
        }

        @Test
        @DisplayName("should produce well-formed XML")
        void shouldProduceWellFormedXml() {
            Document doc = UtilXML.newDocument();
            UtilXML.addNode(doc, "root");
            String xml = UtilXML.toXML(doc);
            assertThat(xml).contains("<?xml");
            assertThat(xml).contains("<root/>");
        }
    }

    @Nested
    @DisplayName("parseXMLDocument")
    class ParseXMLDocument {

        @Test
        @DisplayName("should parse XML string to Document")
        void shouldParseXmlString() {
            Document doc = UtilXML.parseXML("<root><child>value</child></root>");
            assertThat(doc).isNotNull();
            assertThat(doc.getDocumentElement().getTagName()).isEqualTo("root");
        }

        @Test
        @DisplayName("should return null for null input")
        void shouldReturnNull_forNull() {
            Document doc = UtilXML.parseXML(null);
            assertThat(doc).isNull();
        }
    }

    @Nested
    @DisplayName("round-trip")
    class RoundTrip {

        @Test
        @DisplayName("should survive create/serialize/parse round-trip")
        void shouldSurviveRoundTrip() {
            Document doc = UtilXML.newDocument();
            Element root = UtilXML.addNode(doc, "data");
            UtilXML.addNode(root, "field", "hello");

            String xml = UtilXML.toXML(doc);
            Document parsed = UtilXML.parseXML(xml);

            assertThat(parsed).isNotNull();
            assertThat(parsed.getDocumentElement().getTagName()).isEqualTo("data");
            assertThat(parsed.getElementsByTagName("field").item(0).getTextContent()).isEqualTo("hello");
        }
    }
}
